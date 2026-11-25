#!/usr/bin/env python3
"""Matched Todo D1 quality gates and timings in opt-in deployments."""

import argparse
import contextlib
import html
import http.client
import json
from pathlib import Path
import platform
import re
import socket
import struct
import subprocess
import time

from study import KINDS, PACKAGED, ROOT, archive, command, round_order

HERE = Path(__file__).resolve().parent
PATH = "/todo-study/deferred"
CONTENT_TYPES = {
    "woge": "application/vnd.woge.patch-stream; version=1",
    "kte": "text/html",
    "datastar": "text/event-stream",
}
SHELL = "Loading Todo summary…".encode()
COMPLETE = b"10 todos on the board."
FAILED = b"Todo summary unavailable."


def collection(connection):
    status, content = PACKAGED["request"](connection, f"{PATH}/observations")
    if status != 200:
        raise RuntimeError(f"Observations returned {status}")
    return tuple(map(int, content.split()))


def await_counter(connection, slot, target, deadline):
    while time.monotonic() < deadline:
        counters = collection(connection)
        if counters[slot] == target:
            return counters
        time.sleep(0.005)
    raise RuntimeError(f"Expected observation {target} in slot {slot}, got {collection(connection)}")


def cancel_operation(kind, port, connection, shell):
    if kind == "woge":
        match = re.search(rb'data-woge-patch-url="([^"]+)"', shell)
        if match is None:
            raise RuntimeError("The Woge shell omitted its deferred request URL")
        path = html.unescape(match[1].decode())
    else:
        path = f"{PATH}/summary"
    before = collection(connection)
    with socket.create_connection(("127.0.0.1", port), timeout=2) as wire:
        wire.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, struct.pack("ii", 1, 0))
        wire.sendall(f"GET {path} HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\n"
                     f"Accept: {CONTENT_TYPES[kind]}\r\nConnection: close\r\n\r\n".encode("ascii"))
        started = await_counter(connection, 0, before[0] + 1, time.monotonic() + 0.25)
    after = await_counter(connection, 1, before[1] + 1, time.monotonic() + 2)
    return {"before": before, "started": started, "afterReset": after}


def http_checks(kind, port, origin):
    with contextlib.closing(http.client.HTTPConnection("127.0.0.1", port, timeout=5)) as conn:
        PACKAGED["prepare"](kind, conn, origin)
        began = time.monotonic()
        status, shell = PACKAGED["request"](conn, PATH)
        shell_ms = (time.monotonic() - began) * 1000
        if status != 200 or SHELL not in shell or COMPLETE in shell:
            raise RuntimeError(f"Expected the useful loading document, received HTTP {status}")
        if b"Open the complete Todo overview" not in shell or b"Open the Todo board" not in shell:
            raise RuntimeError("The shell omitted ordinary navigation and completion links")
        aborted = cancel_operation(kind, port, conn, shell)
        began = time.monotonic()
        status, complete = PACKAGED["request"](conn, f"{PATH}?complete=true")
        full_ms = (time.monotonic() - began) * 1000
        if status != 200 or COMPLETE not in complete or SHELL in complete or full_ms < 300:
            raise RuntimeError(f"The complete native document failed its content or delay check (HTTP {status})")
        status, failed = PACKAGED["request"](conn, f"{PATH}?complete=true&fail=true")
        if status != 503 or FAILED not in failed:
            raise RuntimeError(f"The native failure did not return a useful HTTP 503 (HTTP {status})")
        return {"shellHttpMs": shell_ms, "completeDocumentHttpMs": full_ms,
                "shellBytes": len(shell), "completeDocumentBytes": len(complete), "socketCancellation": aborted}


def run(kind, path, args, output, label):
    record = {"contender": kind, "phase": label}
    try:
        with PACKAGED["running"](kind, path, output / f"{label}-{kind}.server.log",
                                 application_args=("--spring.profiles.active=todo-study-deferred",)) as (_, port, _):
            origin = f"http://127.0.0.1:{port}"
            record["http"] = http_checks(kind, port, origin)
            result = subprocess.run([args.node, str(HERE / "verify-deferred.mjs"), origin],
                                    cwd=ROOT, text=True, capture_output=True, timeout=35)
            (output / f"{label}-{kind}.browser.log").write_text(result.stdout + result.stderr)
            if result.returncode != 0:
                raise RuntimeError("Chromium D1 verification failed; see browser log")
            record["browser"] = json.loads(result.stdout)
        record["status"] = "passed"
    except (AssertionError, OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        record.update(status="failed", error=str(error) or type(error).__name__)
    return record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for kind in KINDS:
        parser.add_argument(f"--{kind}", type=Path, required=True, help="Independently built D1 executable JAR")
    parser.add_argument("--node", default="node")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    paths = {kind: getattr(args, kind).resolve() for kind in KINDS}
    evidence = {
        "schemaVersion": 1,
        "sourceCommit": command("git", "-C", str(ROOT), "rev-parse", "HEAD"),
        "scenario": "D1 / one region, 360 ms controlled server wait, ten active escaped-title seed rows",
        "environment": {
            "host": platform.platform(),
            "docker": command("docker", "version", "--format", "{{.Server.Version}}"),
            "jvmImage": PACKAGED["JAVA"],
            "jvmImageId": command("docker", "image", "inspect", PACKAGED["JAVA"], "--format", "{{.Id}}"),
            "runtimeArchitecture": "linux/arm64",
            "node": command(args.node, "--version"),
            "cpuLimit": PACKAGED["CPU"],
            "memoryBytes": PACKAGED["MEMORY"],
            "browser": "fresh host Chromium contexts",
        },
        "artifacts": {kind: archive(path) for kind, path in paths.items()},
        "plan": {
            "threshold": "100 ms difference in browser completion for this 360 ms fixture",
            "parity": "useful shell first, matched native path, canceled in-flight server read, safe failure and fresh journey",
            "order": "all preflights, then one browser and HTTP journey per contender in each of three rotated rounds",
        },
        "preflight": [],
        "attempts": [],
    }

    def retain(records, result):
        records.append(result)
        print(result["phase"], result["contender"], result["status"], result.get("error", ""), flush=True)
        (output / "results.json").write_text(json.dumps(evidence, indent=2) + "\n")

    for kind in KINDS:
        retain(evidence["preflight"], run(kind, paths[kind], args, output, "preflight"))
    if any(result["status"] != "passed" for result in evidence["preflight"]):
        raise RuntimeError("A contender did not pass D1 preflight. No comparative timings collected.")

    for index in range(PACKAGED["ROUNDS"]):
        for order, kind in enumerate(round_order(index), 1):
            result = run(kind, paths[kind], args, output, f"round-{index + 1}")
            retain(evidence["attempts"], {"round": index + 1, "order": order, **result})
    if any(result["status"] != "passed" for result in evidence["attempts"]):
        raise RuntimeError("A measured D1 round failed. Inspect retained outcomes before comparing.")


if __name__ == "__main__":
    main()
