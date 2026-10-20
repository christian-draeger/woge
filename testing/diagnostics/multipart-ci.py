#!/usr/bin/env python3
"""Bounded #278 investigation. Run after the clean root gate; stop at the first failure."""

import json
import os
from pathlib import Path
import platform
import shutil
import signal
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "build/multipart-diagnostics"
PROJECTS = ("woge-spring-mvc", "woge-spring-webflux", "woge-ktor")
ATTEMPTS_PER_MODE = 3
BUDGET_SECONDS = 360
ATTEMPT_SECONDS = 180


def retain_reports(destination):
    for project in PROJECTS:
        source = ROOT / "adapters" / project / "build"
        for relative in ("reports/tests/test", "test-results/test"):
            if (source / relative).exists():
                shutil.copytree(source / relative, destination / project / relative)


def verify_executed(destination):
    for project in PROJECTS:
        reports = list((destination / project / "test-results/test").glob("TEST-*AdapterTckTest.xml"))
        if len(reports) != 1:
            return f"Missing fresh TCK XML for {project}"
        suite = ET.parse(reports[0]).getroot()
        cases = [case for case in suite.findall("testcase")
                 if "passes the shared server adapter contract" in case.get("name", "")]
        if len(cases) != 1 or any(cases[0].find(tag) is not None for tag in ("skipped", "failure", "error")):
            return f"Shared TCK was not executed successfully for {project}"
        output = "\n".join(node.text or "" for node in suite.iter("system-out"))
        if "case-6]" not in output or "client.response.headers status=413" not in output:
            return f"Missing multipart limit timeline for {project}"
        if project == "woge-spring-webflux" and "netty.request connection=" not in output:
            return "Missing WebFlux server-side timeline"
    return None


def investigate():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    metadata = {
        "platform": platform.platform(),
        "machine": platform.machine(),
        "java": subprocess.check_output(["java", "-version"], stderr=subprocess.STDOUT, text=True),
        "revision": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "attemptsPerMode": ATTEMPTS_PER_MODE,
        "budgetSeconds": BUDGET_SECONDS,
        "results": [],
    }
    deadline = time.monotonic() + BUDGET_SECONDS
    environment = dict(os.environ, WOGE_TCK_UPLOAD_DIAGNOSTICS="true",
                       WOGE_STREAM_PROXY_SCRIPT=str(ROOT / "testing/proxies/stream-probe.py"))
    try:
        for mode in ("parallel", "serial"):
            for number in range(1, ATTEMPTS_PER_MODE + 1):
                attempt = OUTPUT / f"{mode}-{number:02}"
                attempt.mkdir()
                if time.monotonic() >= deadline:
                    metadata["budgetExhausted"] = True
                    print("Investigation budget exhausted; remaining attempts were not executed.", flush=True)
                    return 124
                # Remove old reports so an interrupted attempt cannot retain a previous pass as its evidence.
                for project in PROJECTS:
                    for relative in ("reports/tests/test", "test-results/test"):
                        shutil.rmtree(ROOT / "adapters" / project / "build" / relative, ignore_errors=True)
                command = ["./gradlew"]
                for project in PROJECTS:
                    command += [f":{project}:test", "--tests", "*AdapterTckTest", "--rerun"]
                command += ["--no-build-cache", "--console=plain", "--stacktrace",
                            "--parallel" if mode == "parallel" else "--no-parallel"]
                started = time.monotonic()
                remaining = deadline - started
                print(f"Multipart investigation {mode} attempt {number}/{ATTEMPTS_PER_MODE}", flush=True)
                result = {"mode": mode, "attempt": number, "command": command}
                metadata["results"].append(result)
                with (attempt / "gradle.log").open("w") as log:
                    process = subprocess.Popen(command, cwd=ROOT, env=environment, stdout=log,
                                               stderr=subprocess.STDOUT, start_new_session=True)
                    try:
                        result["exitCode"] = process.wait(timeout=max(0, min(ATTEMPT_SECONDS, remaining)))
                    except subprocess.TimeoutExpired:
                        # Cancel the client; the CI job also bounds the reusable daemon/worker lifecycle.
                        os.killpg(process.pid, signal.SIGTERM)
                        try:
                            process.wait(timeout=5)
                        except subprocess.TimeoutExpired:
                            os.killpg(process.pid, signal.SIGKILL)
                            process.wait()
                        result["exitCode"] = 124
                        result["timedOut"] = True
                result["seconds"] = round(time.monotonic() - started, 3)
                retain_reports(attempt)
                if not result["exitCode"]:
                    problem = verify_executed(attempt)
                    if problem:
                        result["evidenceError"] = problem
                        result["exitCode"] = 1
                if result["exitCode"]:
                    print(f"Stopped on first failure: {attempt.relative_to(ROOT)}", flush=True)
                    return result["exitCode"]
        print("Bounded investigation passed; original timeout remains not reproduced.", flush=True)
        return 0
    finally:
        (OUTPUT / "summary.json").write_text(json.dumps(metadata, indent=2) + "\n")


if __name__ == "__main__":
    sys.exit(investigate())
