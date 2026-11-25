#!/usr/bin/env python3
"""Collect bounded TCP text metadata for the isolated WebFlux loopback TCK."""
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

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "build/multipart-loopback"
PARSER = Path(__file__).with_name("multipart-loopback.py")
PACKET = runpy.run_path(str(PARSER))["PACKET"]
SUITE = ROOT / "adapters/woge-spring-webflux/build/test-results/test"
REPORT = SUITE / "TEST-dev.woge.spring.webflux.WogeWebFluxAdapterTckTest.xml"
ATTEMPTS = 3
ATTEMPT_SECONDS = 75
PACKET_LIMIT = 10_000
FILTER = (
    "ip and tcp and src host 127.0.0.1 and dst host 127.0.0.1 "
    "and src portrange 32768-65535 and dst portrange 32768-65535"
)
SUDO = ("sudo", "-n")
COUNTER = re.compile(r"\d+ packets (?:captured|received by filter|dropped by kernel)")


def packet_metadata(line):
    """Re-emit only the numeric TCP fields consumed by multipart-loopback.py."""
    packet = PACKET.fullmatch(line.strip())
    if packet is None or not re.fullmatch(r"[FSRPAUEWCe.]+", packet[4]):
        return None
    if not all(32768 <= int(packet[index]) <= 65535 for index in (2, 3)):
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
    def __init__(self, namespace, destination):
        self.ready = threading.Event()
        self.metadata_count = 0
        self.pid = None
        self.process = subprocess.Popen(
            (*SUDO, "ip", "netns", "exec", namespace, "tcpdump",
             "-p", "-i", "lo", "-c", str(PACKET_LIMIT), "--immediate-mode", "-l",
             "-s", "96", "-nn", "-tt", "-S", FILTER),
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
                if (metadata := packet_metadata(line)) is not None:
                    output.write(metadata + "\n")
                    self.metadata_count += 1

    def counters(self, path):
        with self.process.stderr as stream, path.open("w") as output:
            for line in stream:
                text = line.strip()
                if re.match(r"^(?:tcpdump: )?listening on lo,", text):
                    output.write("listening on lo\n")
                    self.ready.set()
                if COUNTER.fullmatch(text):
                    output.write(text + "\n")

    def wait_ready(self, namespace):
        if not self.ready.wait(timeout=5):
            raise RuntimeError("tcpdump did not confirm loopback capture")
        pids = namespace_processes(namespace)
        if len(pids) != 1 or Path(f"/proc/{pids[0]}/comm").read_text().strip() != "tcpdump":
            raise RuntimeError("Expected only tcpdump in the new namespace before running tests")
        self.pid = pids[0]

    def finish(self, namespace):
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


def gradle_command(namespace):
    account = pwd.getpwuid(os.getuid())
    return (
        *SUDO, "ip", "netns", "exec", namespace,
        *SUDO, "-H", "-u", f"#{account.pw_uid}", "env", "-i",
        f"HOME={account.pw_dir}", f"PATH={os.environ['PATH']}",
        f"JAVA_HOME={os.environ['JAVA_HOME']}",
        f"GRADLE_USER_HOME={os.environ.get('GRADLE_USER_HOME', str(Path(account.pw_dir) / '.gradle'))}",
        "WOGE_TCK_UPLOAD_DIAGNOSTICS=true", "WOGE_TCK_UPLOAD_TRANSPORT_DIAGNOSTICS=true",
        str(ROOT / "gradlew"), "--offline", "--no-daemon", "--no-build-cache", "--console=plain",
        ":woge-spring-webflux:test", "--tests", "*WebFlux passes the shared server adapter contract", "--rerun",
    )


def run_attempt(destination, result):
    namespace = "woge-multipart-" + secrets.token_hex(5)
    capture = None
    created = False
    REPORT.unlink(missing_ok=True)
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
                    gradle_command(namespace), cwd=ROOT, stdout=log, stderr=subprocess.STDOUT,
                    timeout=ATTEMPT_SECONDS, check=False,
                )
                result["testExitCode"] = test.returncode
            except subprocess.TimeoutExpired:
                result.update(testExitCode=124, timedOut=True)
    finally:
        try:
            if REPORT.is_file():
                shutil.copyfile(REPORT, destination / REPORT.name)
        finally:
            try:
                if capture is not None:
                    capture.finish(namespace)
                    result.update(tcpdumpExitCode=capture.process.returncode, metadataRows=capture.metadata_count)
            finally:
                if created or (Path("/run/netns") / namespace).exists():
                    clean_namespace(namespace)

    report = destination / REPORT.name
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
    return result["testExitCode"] or result["tcpdumpExitCode"] or result.get("correlationExitCode", 1)


def main():
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
        "packetLimit": PACKET_LIMIT, "attempts": [],
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
                result["exitCode"] = run_attempt(destination, result)
            except (OSError, RuntimeError, subprocess.SubprocessError, ValueError) as failure:
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
