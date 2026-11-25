#!/usr/bin/env python3
"""Scope-bound, parity-gated deployment samples for the packaged Todo applications."""

import argparse
import contextlib
import hashlib
import http.client
import json
from pathlib import Path
import platform
import re
import secrets
import subprocess
import time
from urllib.parse import urlencode
import zipfile

NODE = "node@sha256:873f586f3794f52281bd039bf02c048a0be08fde619338f9941bad1eb6b34530"
JAVA = "eclipse-temurin@sha256:4a541e56af0ae9a1ff20cdbf43e400864542116b0e7804b37f51be4c574f2057"
MEMORY = 768 * 1024 * 1024
CPU = 2
WARMUP = 15
SAMPLES = 60
ROUNDS = 3
ROOT = Path(__file__).resolve().parents[2]


def command(*args, timeout=30):
    return subprocess.run(args, check=True, text=True, capture_output=True, timeout=timeout).stdout.strip()


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def artifact(path):
    files = [path] if path.is_file() else sorted(file for file in path.rglob("*") if file.is_file())
    require(files, f"Empty application artifact: {path}")
    digest = hashlib.sha256()
    for file in files:
        if path.is_dir():
            digest.update(str(file.relative_to(path)).encode() + b"\0")
        digest.update(hashlib.sha256(file.read_bytes()).digest())
    result = {
        "bytes": sum(file.stat().st_size for file in files),
        "files": len(files),
        "digestOfFileHashes": digest.hexdigest(),
    }
    if path.is_file():
        with zipfile.ZipFile(path) as jar:
            result["nestedRuntimeJars"] = sorted(
                entry.removeprefix("BOOT-INF/lib/") for entry in jar.namelist()
                if entry.startswith("BOOT-INF/lib/") and entry.endswith(".jar")
            )
        result["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
    return result


def request(connection, path="/", method="GET", body=None, headers=None):
    connection.request(method, path, body=body, headers={"Accept-Encoding": "identity", **(headers or {})})
    with connection.getresponse() as response:
        return response.status, response.read(512 * 1024)


@contextlib.contextmanager
def running(kind, path, logs, matched_draft):
    name = "woge-todo-study-" + secrets.token_hex(6)
    next_app = kind == "next"
    image = NODE if next_app else JAVA
    mount = "/study" if next_app else "/study.jar"
    launch = ["node", "/study/server.js"] if next_app else [
        "java", "-jar", "/study.jar", "--server.address=0.0.0.0", "--server.port=8080",
    ]
    args = [
        "docker", "run", "-d", "--pull=never", "--platform", "linux/arm64",
        "--name", name, "--cpus", str(CPU), "--memory", str(MEMORY),
        "--read-only", "--tmpfs", "/tmp:rw,nosuid,size=64m", "-p", "127.0.0.1::8080",
        "-e", "HOSTNAME=0.0.0.0", "-e", "PORT=8080", "-e", "NEXT_TELEMETRY_DISABLED=1",
        "--mount", f"type=bind,source={path},target={mount},readonly",
    ]
    if next_app:
        args.extend(["-w", "/study"])
    started = time.monotonic()
    command(*args, image, *launch)
    try:
        port = int(command("docker", "port", name, "8080/tcp").rsplit(":", 1)[1])
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            with contextlib.closing(http.client.HTTPConnection("127.0.0.1", port, timeout=2)) as connection:
                try:
                    status, body = request(connection)
                    ready = correct_board(status, body, all_rows=True) if matched_draft else (
                        status == 200 and b"remaining" in body
                    )
                    if ready:
                        break
                except (OSError, http.client.HTTPException):
                    pass
            time.sleep(0.05)
        else:
            raise RuntimeError(f"{kind} did not return a ready HTML page")
        startup_ms = (time.monotonic() - started) * 1000
        actual = json.loads(command("docker", "inspect", name))[0]
        limits = actual["HostConfig"]
        require(limits["NanoCpus"] == CPU * 1_000_000_000 and limits["Memory"] == MEMORY,
                "Docker did not apply the requested CPU/memory limits")
        require(command("docker", "exec", name, "uname", "-m") == "aarch64", "Expected Linux arm64")
        yield name, port, startup_ms
    finally:
        subprocess.run(["docker", "stop", "--time", "6", name], capture_output=True, timeout=10)
        output = subprocess.run(["docker", "logs", name], capture_output=True, text=True, timeout=8)
        logs.write_text(output.stdout + output.stderr)
        subprocess.run(["docker", "rm", "-f", name], capture_output=True, timeout=8)


def prepare(kind, connection, origin):
    if kind == "woge":
        for index in range(1, 11):
            body = urlencode({"title": f"Todo <{index}> & Kotlin", "filter": "all"})
            status, _ = request(connection, "/woge-actions/add-todo", "POST", body, {
                "Content-Type": "application/x-www-form-urlencoded", "Origin": origin,
            })
            require(status == 303, f"Woge seed returned {status}")


def correct_board(status, body, all_rows=False):
    rendered = re.sub(rb"<!--.*?-->", b"", body)
    row_count = 10 if all_rows else 1
    return (status == 200 and b"10 remaining" in rendered and b"10 total" in rendered
            and all(f"Todo &lt;{index}&gt; &amp; Kotlin".encode() in rendered
                    for index in range(1, row_count + 1)))


def browser(origin, log, matched_draft):
    args = ["node", str(Path(__file__).with_name("verify.mjs")), origin]
    if matched_draft:
        args.append("matched-draft")
    result = subprocess.run(args, cwd=ROOT, capture_output=True, text=True, timeout=90)
    log.write_text(result.stdout + result.stderr)
    require(result.returncode == 0, f"Browser parity failed. See {log.name}")
    return json.loads(result.stdout)


def run(kind, path, output, label, measure, matched_draft):
    result = {"contender": kind, "phase": label, "httpSamples": []}
    try:
        with running(kind, path, output / f"{label}-{kind}.server.log", matched_draft) as (name, port, startup):
            result["startupUsefulHtmlMs" if matched_draft else "startupBeforeSeedingMs"] = startup
            origin = f"http://127.0.0.1:{port}"
            with contextlib.closing(http.client.HTTPConnection("127.0.0.1", port, timeout=5)) as connection:
                if not matched_draft:
                    began = time.monotonic()
                    prepare(kind, connection, origin)
                    result["seedPreparationMs"] = (time.monotonic() - began) * 1000
                require(correct_board(*request(connection), all_rows=matched_draft),
                        f"{kind} initial board did not pass parity")
                if measure:
                    for index in range(WARMUP + SAMPLES):
                        began = time.monotonic()
                        status, body = request(connection)
                        elapsed = (time.monotonic() - began) * 1000
                        correct = correct_board(status, body, all_rows=matched_draft)
                        result["httpSamples"].append({
                            "phase": "warmup" if index < WARMUP else "sample",
                            "elapsedMs": elapsed, "status": status, "bodyBytes": len(body), "correct": correct,
                        })
                        require(correct, f"Initial board HTTP sample {index + 1} failed with {status}")
            result["browser"] = browser(origin, output / f"{label}-{kind}.browser.log", matched_draft)
            rss = command("docker", "exec", name, "grep", "^VmRSS:", "/proc/1/status")
            result["rssAfterBrowserKiB"] = int(rss.split()[1])
        result["status"] = "passed"
    except (RuntimeError, OSError, subprocess.SubprocessError, ValueError) as error:
        result.update(status="failed", error=str(error))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--next", type=Path, required=True, help="Linux arm64 Next.js standalone directory")
    parser.add_argument("--kte", type=Path, required=True, help="Independent KTE/htmx executable JAR")
    parser.add_argument("--woge", type=Path, help="Maintained standalone WebFlux Todo executable JAR")
    parser.add_argument("--output", type=Path, required=True, help="New directory for raw evidence")
    parser.add_argument("--matched-draft", action="store_true", help="Require all three preseeded contenders")
    args = parser.parse_args()
    if args.matched_draft and not args.woge:
        parser.error("--matched-draft requires the preseeded --woge JAR")
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    entries = {name: getattr(args, name).resolve() for name in ("next", "kte", "woge") if getattr(args, name)}
    evidence = {
        "schemaVersion": 1, "sourceCommit": command("git", "-C", str(ROOT), "rev-parse", "HEAD"),
        "host": platform.platform(), "docker": command("docker", "version", "--format", "{{.Server.Version}}"),
        "images": {"next": NODE, "jvm": JAVA},
        "environment": {"architecture": "linux/arm64", "cpus": CPU, "memoryBytes": MEMORY,
                        "browser": "host Chromium, fresh contexts", "fileCache": "unchanged OS cache"},
        "artifacts": {kind: artifact(path) for kind, path in entries.items()},
        "plan": {"warmup": WARMUP, "samples": SAMPLES, "rounds": ROUNDS, "concurrency": 1,
                 "request": "GET / with Accept-Encoding: identity, 10 Todo rows"},
        "preflight": [], "attempts": [],
    }

    if args.matched_draft:
        evidence["schemaVersion"] = 2
        evidence["plan"].update(mode="pending-submission-draft", requireAllContenders=True)

    def retain():
        (output / "results.json").write_text(json.dumps(evidence, indent=2) + "\n")

    for kind, path in entries.items():
        result = run(kind, path, output, "preflight", measure=False, matched_draft=args.matched_draft)
        evidence["preflight"].append(result)
        print(kind, result["status"], result.get("error", ""), flush=True)
        retain()

    passed = [result["contender"] for result in evidence["preflight"] if result["status"] == "passed"]
    if args.matched_draft and len(passed) != len(entries):
        raise RuntimeError("Matched parity failed. No three-contender timing performed.")
    if "next" not in passed or not any(kind in passed for kind in ("woge", "kte")):
        raise RuntimeError("No Next.js/JVM pair passed the parity gate. No comparative timing performed.")
    for round_index in range(ROUNDS):
        rotated = passed[round_index % len(passed):] + passed[:round_index % len(passed)]
        for order, kind in enumerate(rotated):
            result = run(kind, entries[kind], output, f"round-{round_index + 1}",
                         measure=True, matched_draft=args.matched_draft)
            evidence["attempts"].append({"round": round_index + 1, "order": order + 1, **result})
            print(round_index + 1, kind, result["status"], result.get("error", ""), flush=True)
            retain()

    if any(result["status"] != "passed" for result in evidence["attempts"]):
        raise RuntimeError("A measured round failed. Inspect retained outcomes before comparing.")
    print(output / "results.json")


if __name__ == "__main__":
    main()
