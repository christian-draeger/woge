#!/usr/bin/env python3
"""Correlate limited fixture metadata in the standard three-host nginx Build step."""

import json
import os
import runpy
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).resolve().parent
SUPPORT = runpy.run_path(str(HERE / "multipart-kernel-control.py"))
ROOT = SUPPORT["ROOT"]
OUTPUT = ROOT / "build/multipart-proxy-loopback"
PORT = 21078
PROJECTS = ("woge-spring-mvc", "woge-spring-webflux", "woge-ktor")


def capture_proxy_step():
    if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("RUNNER_OS") != "Linux":
        raise SystemExit("Use the disposable GitHub Actions Linux runner")
    OUTPUT.mkdir(parents=True)
    environment = dict(
        os.environ,
        WOGE_TCK_PROXY_LOOPBACK_CAPTURE="true",
        WOGE_TCK_UPLOAD_TRANSPORT_DIAGNOSTICS="true",
        WOGE_STREAM_PROXY_SCRIPT=str(ROOT / "testing/proxies/stream-probe.py"),
    )
    report = SUPPORT["REPORTS"][SUPPORT["WEBFLUX"]]
    report.unlink(missing_ok=True)
    capture = SUPPORT["Capture"](None, OUTPUT, fixture_port=PORT)
    result = {"capture": "loopback TCP flags, sequence, acknowledgment and length", "fixturePort": PORT}
    try:
        capture.wait_ready(None)
        command = [
            "./gradlew", *[f":{project}:test" for project in PROJECTS],
            "--tests", "*AdapterTckTest", "--console=plain",
        ]
        result["testExitCode"] = subprocess.run(command, cwd=ROOT, env=environment, timeout=140).returncode
    finally:
        capture.finish(None)
        result["metadataRows"] = capture.metadata_count
        result["captureExitCode"] = capture.process.returncode
        try:
            if not report.is_file():
                raise ValueError("No fresh WebFlux adapter TCK XML")
            parser = runpy.run_path(str(HERE / "multipart-loopback.py"))
            with (OUTPUT / "correlation.txt").open("w") as correlation:
                from contextlib import redirect_stdout
                with redirect_stdout(correlation):
                    parser["show_correlation"](
                        parser["read_timeline"](report),
                        (OUTPUT / "loopback.txt").read_text(),
                        (OUTPUT / "capture.log").read_text(),
                    )
            result["correlation"] = "matched"
        except (ET.ParseError, OSError, ValueError) as failure:
            result["correlation"] = str(failure)
        finally:
            (OUTPUT / "summary.json").write_text(json.dumps(result, indent=2) + "\n")
    return result["testExitCode"] or result["captureExitCode"] or int(result["correlation"] != "matched")


if __name__ == "__main__":
    raise SystemExit(capture_proxy_step())
