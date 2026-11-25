#!/usr/bin/env python3
"""Collect bounded TCP text metadata for the isolated WebFlux loopback TCK."""
import argparse
from datetime import datetime
import json
import os
from pathlib import Path
import platform
import pwd
import re
import runpy
import secrets
import shutil
import signal
import subprocess
import threading
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "build/multipart-loopback"
PARSER = Path(__file__).with_name("multipart-loopback.py")
PACKET = runpy.run_path(str(PARSER))["PACKET"]
REPORTS = {
    project: ROOT / f"adapters/{project}/build/test-results/test/TEST-{suite}.xml"
    for project, suite in (
        ("woge-spring-webflux", "dev.woge.spring.webflux.WogeWebFluxAdapterTckTest"),
        ("woge-spring-mvc", "dev.woge.spring.mvc.WogeSpringMvcAdapterTckTest"),
        ("woge-ktor", "dev.woge.ktor.WogeKtorAdapterTckTest"),
    )
}
WEBFLUX = "woge-spring-webflux"
TCK_METHOD = "passes the shared server adapter contract"
ATTEMPTS = 3
ATTEMPT_SECONDS = 75
PACKET_LIMIT = 10_000
FILTER = (
    "ip and tcp and src host 127.0.0.1 and dst host 127.0.0.1 "
    "and src portrange 32768-65535 and dst portrange 32768-65535"
)
SUDO = ("sudo", "-n")
COUNTER = re.compile(r"\d+ packets (?:captured|received by filter|dropped by kernel)")


def packet_metadata(line, fixture_port=None):
    """Re-emit only the numeric TCP fields consumed by multipart-loopback.py."""
    packet = PACKET.fullmatch(line.strip())
    if packet is None or not re.fullmatch(r"[FSRPAUEWCe.]+", packet[4]):
        return None
    ports = {int(packet[2]), int(packet[3])}
    if fixture_port is not None and fixture_port not in ports:
        return None
    if not all(port == fixture_port or 32768 <= port <= 65535 for port in ports):
        return None
    fields = re.findall(r"\b(?:seq|ack) [\d:]+", packet[5])
    prefix = f"{packet[1]} IP 127.0.0.1.{packet[2]} > 127.0.0.1.{packet[3]}: Flags [{packet[4]}], "
    return prefix + ", ".join([*fields, f"length {packet[6]}"])


def command(args, **kwargs):
    return subprocess.run(args, cwd=ROOT, check=True, text=True, **kwargs)


def namespace_processes(namespace):
    return command((*SUDO, "ip", "netns", "pids", namespace), capture_output=True, timeout=5).stdout.split()


def signal_namespace_processes(pids, operation):
    if pids:
        subprocess.run(
            (*SUDO, "kill", f"-{operation}", *pids),
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=5, check=False,
        )


def clean_namespace(namespace):
    signal_namespace_processes(namespace_processes(namespace), "TERM")
    time.sleep(0.2)
    signal_namespace_processes(namespace_processes(namespace), "KILL")
    command((*SUDO, "ip", "netns", "delete", namespace), timeout=5)


class Capture:
    def __init__(self, namespace, destination, fixture_port=None):
        self.fixture_port = fixture_port
        self.ready = threading.Event()
        self.metadata_count = 0
        self.pid = None
        if namespace is not None:
            prefix = (*SUDO, "ip", "netns", "exec", namespace)
            packet_filter = FILTER
        else:
            if fixture_port != 21078:
                raise ValueError("The hosted capture is restricted to the synthetic WebFlux listener")
            prefix = (*SUDO, "sh", "-c", 'echo "capture-pid=$$" >&2; exec "$@"', "capture")
            packet_filter = (
                "ip and tcp and src host 127.0.0.1 and dst host 127.0.0.1 "
                f"and ((src port {fixture_port} and dst portrange 32768-65535) "
                f"or (dst port {fixture_port} and src portrange 32768-65535))"
            )
        self.process = subprocess.Popen(
            (*prefix, "tcpdump",
             "-p", "-i", "lo", "-c", str(PACKET_LIMIT), "--immediate-mode", "-l",
             "-s", "96", "-nn", "-tt", "-S", packet_filter),
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, errors="replace",
        )
        self.threads = (
            threading.Thread(target=self.metadata, args=(destination / "loopback.txt",), daemon=True),
            threading.Thread(target=self.counters, args=(destination / "capture.log",), daemon=True),
        )
        for thread in self.threads:
            thread.start()

    def metadata(self, path):
        with self.process.stdout as stream, path.open("w") as output:
            for line in stream:
                if (metadata := packet_metadata(line, self.fixture_port)) is not None:
                    output.write(metadata + "\n")
                    self.metadata_count += 1

    def counters(self, path):
        with self.process.stderr as stream, path.open("w") as output:
            for line in stream:
                text = line.strip()
                if self.fixture_port is not None and re.fullmatch(r"capture-pid=\d+", text):
                    self.pid = text.split("=")[1]
                if re.match(r"^(?:tcpdump: )?listening on lo,", text):
                    output.write("listening on lo\n")
                    self.ready.set()
                if COUNTER.fullmatch(text):
                    output.write(text + "\n")

    def wait_ready(self, namespace):
        if not self.ready.wait(timeout=5):
            raise RuntimeError("tcpdump did not confirm loopback capture")
        if namespace is None:
            if not self.pid or Path(f"/proc/{self.pid}/comm").read_text().strip() != "tcpdump":
                raise RuntimeError("The confirmed fixture capture process is not tcpdump")
            return
        pids = namespace_processes(namespace)
        if len(pids) != 1 or Path(f"/proc/{pids[0]}/comm").read_text().strip() != "tcpdump":
            raise RuntimeError("Expected only tcpdump in the new namespace before running tests")
        self.pid = pids[0]

    def finish(self, namespace):
        if namespace is None:
            if self.process.poll() is None and self.pid:
                signal_namespace_processes([self.pid], "INT")
        else:
            pids = namespace_processes(namespace)
            workers = [pid for pid in pids if pid != self.pid]
            signal_namespace_processes(workers, "TERM")
            if workers:
                time.sleep(0.2)
                signal_namespace_processes([pid for pid in namespace_processes(namespace) if pid != self.pid], "KILL")
            if self.pid in pids:
                signal_namespace_processes([self.pid], "INT")
        try:
            self.process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            raise RuntimeError("tcpdump did not finish after interrupt") from None
        for thread in self.threads:
            thread.join(timeout=2)
            if thread.is_alive():
                raise RuntimeError("tcpdump output did not drain")


def gradle_command(namespace, projects):
    account = pwd.getpwuid(os.getuid())
    tasks = [argument for project in projects for argument in (
        f":{project}:test", "--tests", f"*{TCK_METHOD}", "--rerun",
    )]
    return (
        *SUDO, "ip", "netns", "exec", namespace,
        *SUDO, "-H", "-u", f"#{account.pw_uid}", "env", "-i",
        f"HOME={account.pw_dir}", f"PATH={os.environ['PATH']}",
        f"JAVA_HOME={os.environ['JAVA_HOME']}",
        f"GRADLE_USER_HOME={os.environ.get('GRADLE_USER_HOME', str(Path(account.pw_dir) / '.gradle'))}",
        "WOGE_TCK_UPLOAD_DIAGNOSTICS=true", "WOGE_TCK_UPLOAD_TRANSPORT_DIAGNOSTICS=true",
        str(ROOT / "gradlew"), "--offline", "--no-daemon", "--no-build-cache", "--console=plain",
        "--parallel", "--max-workers=3", *tasks,
    )


def host_windows(destination, projects):
    windows = {}
    for project in projects:
        report = destination / REPORTS[project].name
        if not report.is_file():
            continue
        suite = ET.parse(report).getroot()
        cases = [case for case in suite.findall("testcase") if TCK_METHOD in case.get("name", "")]
        if len(cases) == 1 and cases[0].find("skipped") is None:
            start = datetime.fromisoformat(suite.attrib["timestamp"]).timestamp()
            windows[project] = {"start": start, "end": start + float(suite.attrib["time"])}
    return windows


def overlapping_webflux_peers(windows):
    if WEBFLUX not in windows:
        return []
    webflux = windows[WEBFLUX]
    return [project for project, peer in windows.items() if project != WEBFLUX
            and max(webflux["start"], peer["start"]) < min(webflux["end"], peer["end"])]


def run_attempt(destination, result, projects):
    namespace = "woge-multipart-" + secrets.token_hex(5)
    capture = None
    created = False
    for project in projects:
        REPORTS[project].unlink(missing_ok=True)
    try:
        command((*SUDO, "ip", "netns", "add", namespace), timeout=5)
        created = True
        command((*SUDO, "ip", "-n", namespace, "link", "set", "lo", "up"), timeout=5)
        links = command((*SUDO, "ip", "-n", namespace, "-j", "link", "show"), capture_output=True, timeout=5)
        result["interfaces"] = [link["ifname"] for link in json.loads(links.stdout)]
        if result["interfaces"] != ["lo"]:
            raise RuntimeError("Expected a dedicated namespace with only the loopback interface")

        capture = Capture(namespace, destination)
        capture.wait_ready(namespace)
        with (destination / "gradle.log").open("w") as log:
            try:
                test = subprocess.run(
                    gradle_command(namespace, projects), cwd=ROOT, stdout=log, stderr=subprocess.STDOUT,
                    timeout=ATTEMPT_SECONDS, check=False,
                )
                result["testExitCode"] = test.returncode
            except subprocess.TimeoutExpired:
                result.update(testExitCode=124, timedOut=True)
    finally:
        try:
            for project in projects:
                report = REPORTS[project]
                if report.is_file():
                    shutil.copyfile(report, destination / report.name)
        finally:
            try:
                if capture is not None:
                    capture.finish(namespace)
                    result.update(tcpdumpExitCode=capture.process.returncode, metadataRows=capture.metadata_count)
            finally:
                if created or (Path("/run/netns") / namespace).exists():
                    clean_namespace(namespace)

    report = destination / REPORTS[WEBFLUX].name
    if report.is_file():
        with (destination / "correlation.txt").open("w") as correlation:
            parsed = subprocess.run(
                ("python3", str(PARSER), str(report), str(destination / "loopback.txt"),
                 str(destination / "capture.log")),
                stdout=correlation, stderr=subprocess.STDOUT, timeout=5, check=False,
            )
            result["correlationExitCode"] = parsed.returncode
        counters = (destination / "capture.log").read_text()
        captured = re.search(r"^(\d+) packets captured$", counters, re.MULTILINE)
        if captured and int(captured[1]) != result["metadataRows"]:
            result["evidenceError"] = "Capture contained rows outside the allowed TCP metadata format"
            result["correlationExitCode"] = 1
    else:
        result["evidenceError"] = "Missing fresh WebFlux TCK XML"
    if len(projects) > 1:
        windows = host_windows(destination, projects)
        peers = overlapping_webflux_peers(windows)
        result.update(testWindows=windows, overlappingWebFluxPeers=peers)
        if len(peers) != len(projects) - 1:
            result["overlapError"] = "Expected both other host contracts to overlap the WebFlux contract"
            return 1
    return result["testExitCode"] or result["tcpdumpExitCode"] or result.get("correlationExitCode", 1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--parallel-hosts", action="store_true", help="Run the MVC and Ktor contracts alongside WebFlux")
    projects = tuple(REPORTS) if parser.parse_args().parallel_hosts else (WEBFLUX,)
    if platform.system() != "Linux" or os.getuid() == 0:
        raise SystemExit("Use a non-root Linux runner with passwordless sudo")
    if "JAVA_HOME" not in os.environ:
        raise SystemExit("Set JAVA_HOME to the JDK used to prepare the test runtime")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    summary_path = OUTPUT / "summary.json"
    if summary_path.exists() or list(OUTPUT.glob("attempt-*")):
        raise SystemExit("Existing loopback evidence is preserved. Start from a fresh output directory.")
    summary = {
        "machine": platform.machine(), "attemptSeconds": ATTEMPT_SECONDS,
        "packetLimit": PACKET_LIMIT, "projects": projects, "attempts": [],
    }
    try:
        command((*SUDO, "true"), timeout=5)
        for index in range(1, ATTEMPTS + 1):
            destination = OUTPUT / f"attempt-{index:02}"
            destination.mkdir()
            result = {"attempt": index}
            summary["attempts"].append(result)
            started = time.monotonic()
            try:
                result["exitCode"] = run_attempt(destination, result, projects)
            except (ET.ParseError, KeyError, OSError, RuntimeError, subprocess.SubprocessError, ValueError) as failure:
                result.update(exitCode=1, evidenceError=str(failure))
            result["seconds"] = round(time.monotonic() - started, 3)
            print(json.dumps(result), flush=True)
            if result["exitCode"]:
                print(f"Stopped on first failure: {destination.relative_to(ROOT)}", flush=True)
                return result["exitCode"]
        print("Loopback control passed; the intermittent failure did not recur here.", flush=True)
        return 0
    finally:
        summary_path.write_text(json.dumps(summary, indent=2) + "\n")


def interrupted(_signal, _frame):
    raise KeyboardInterrupt


if __name__ == "__main__":
    signal.signal(signal.SIGTERM, interrupted)
    raise SystemExit(main())
