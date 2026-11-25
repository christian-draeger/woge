#!/usr/bin/env python3
"""Parity-gated, independently packaged Spring WebFlux Todo comparison."""

import argparse
import contextlib
import hashlib
import http.client
import json
from pathlib import Path
import platform
import runpy
import subprocess
import time
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
PACKAGED = runpy.run_path(str(ROOT / "spikes/todo-next/study.py"))
KINDS = ("woge", "kte", "datastar")
PROJECTS = {
    "woge": "examples/todo-spring-boot",
    "kte": "spikes/todo-kte-htmx",
    "datastar": "spikes/todo-datastar",
}
JARS = {"woge": "woge-todo-0.1.0.jar", "kte": "todo-kte-htmx.jar", "datastar": "todo-datastar.jar"}


def command(*args):
    return PACKAGED["command"](*args)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def archive(path):
    result = PACKAGED["artifact"](path)
    with zipfile.ZipFile(path) as jar:
        result["closure"] = {
            entry.filename.removeprefix("BOOT-INF/lib/"): {
                "bytes": entry.file_size,
                "sha256": hashlib.sha256(jar.read(entry)).hexdigest(),
            }
            for entry in jar.infolist()
            if entry.filename.startswith("BOOT-INF/lib/") and entry.filename.endswith(".jar")
        }
        result["ownClassAndResourceBytes"] = sum(
            entry.file_size for entry in jar.infolist() if entry.filename.startswith("BOOT-INF/classes/")
        )
    return result


def build(kind, args, log):
    invoke = ["./gradlew", "-p", PROJECTS[kind], "--offline", "--no-build-cache",
              "--no-configuration-cache", "--console=plain", "clean", "bootJar"]
    if kind == "woge":
        invoke += [f"-PwogeVersion={args.woge_version}", f"-PwogeRepository={args.woge_repository.resolve()}"]
    start = time.monotonic()
    result = subprocess.run(invoke, cwd=ROOT, capture_output=True, text=True, timeout=180)
    elapsed = (time.monotonic() - start) * 1000
    text = result.stdout + result.stderr
    log.write_text(text.replace(str(ROOT), "<checkout>").replace(str(Path.home()), "<home>"))
    return {"contender": kind, "ms": elapsed, "exit": result.returncode}


def round_order(index):
    return KINDS[index:] + KINDS[:index]


def browser(args, origin, kind, log):
    result = subprocess.run([args.node, str(HERE / "verify.mjs"), origin, kind],
                            cwd=ROOT, capture_output=True, text=True, timeout=70)
    log.write_text(result.stdout + result.stderr)
    if result.returncode != 0:
        raise RuntimeError(f"{kind} browser gate failed; see {log.name}")
    return json.loads(result.stdout)


def run(kind, path, args, output, label, measure):
    record = {"contender": kind, "phase": label, "httpSamples": []}
    try:
        with PACKAGED["running"](kind, path, output / f"{label}-{kind}.server.log") as (name, port, startup):
            record["startupBeforeSeedingMs"] = startup
            origin = f"http://127.0.0.1:{port}"
            with contextlib.closing(http.client.HTTPConnection("127.0.0.1", port, timeout=5)) as conn:
                began = time.monotonic()
                PACKAGED["prepare"](kind, conn, origin)
                record["seedPreparationMs"] = (time.monotonic() - began) * 1000
                if not PACKAGED["correct_board"](*PACKAGED["request"](conn)):
                    raise RuntimeError(f"{kind}: expected ten seeded Todo rows")
                if measure:
                    warmup, samples = PACKAGED["WARMUP"], PACKAGED["SAMPLES"]
                    for index in range(warmup + samples):
                        started = time.monotonic()
                        status, body = PACKAGED["request"](conn)
                        correct = PACKAGED["correct_board"](status, body)
                        record["httpSamples"].append({
                            "phase": "warmup" if index < warmup else "sample",
                            "elapsedMs": (time.monotonic() - started) * 1000,
                            "status": status, "bytes": len(body), "correct": correct,
                        })
                        if not correct:
                            raise RuntimeError(f"{kind}: HTTP sample {index + 1} did not render the board")
            record["browser"] = browser(args, origin, kind, output / f"{label}-{kind}.browser.log")
            rss = command("docker", "exec", name, "grep", "^VmRSS:", "/proc/1/status")
            record["rssAfterBrowserKiB"] = int(rss.split()[1])
        record["status"] = "passed"
    except (RuntimeError, OSError, ValueError, subprocess.SubprocessError) as error:
        record.update(status="failed", error=str(error))
    return record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--woge-repository", type=Path, required=True, help="Local Maven repository for the pinned Woge artifacts")
    parser.add_argument("--woge-version", default="0.1.0-beta.1")
    parser.add_argument("--node", default="node", help="Node executable that runs the pinned Playwright client")
    parser.add_argument("--output", type=Path, required=True, help="New evidence directory")
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    shared = ROOT / "examples/todo-spring-boot/src/main"
    evidence = {
        "schemaVersion": 1,
        "sourceCommit": command("git", "-C", str(ROOT), "rev-parse", "HEAD"),
        "fixture": {
            "name": "add-only-escaped-active-ten-v1",
            "data": "10 active Todo <n> & Kotlin; completed empty",
            "Todos.kt": sha(shared / "kotlin/example/todo/Todos.kt"),
            "todo.css": sha(shared / "resources/static/todo.css"),
        },
        "wogeVersion": args.woge_version,
        "gradleDistribution": (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text().splitlines()[2:4],
        "environment": {
            "host": platform.platform(),
            "docker": command("docker", "version", "--format", "{{.Server.Version}}"),
            "jvmImage": PACKAGED["JAVA"],
            "jvmImageId": command("docker", "image", "inspect", PACKAGED["JAVA"], "--format", "{{.Id}}"),
            "runtimeJava": command("docker", "run", "--rm", "--pull=never", "--platform", "linux/arm64",
                                   PACKAGED["JAVA"], "java", "--version"),
            "buildJava": command("java", "--version"),
            "node": command(args.node, "--version"),
            "architecture": "linux/arm64", "cpus": PACKAGED["CPU"], "memoryBytes": PACKAGED["MEMORY"],
            "browser": "host Chromium, separate native/enhanced contexts, no added latency or CPU throttle",
            "network": "direct Docker forwarding to localhost, no proxy or compression",
            "observability": "normal startup log only; no profiler, traces or metrics scrape",
        },
        "plan": {
            "rounds": PACKAGED["ROUNDS"], "warmupPerRound": PACKAGED["WARMUP"],
            "samplesPerRound": PACKAGED["SAMPLES"], "concurrency": 1,
            "http": "GET / complete body with identity encoding, seeded ten rows",
            "build": "clean project outputs, warmed dependency caches and Gradle daemons, offline, no build/configuration cache",
            "thresholds": {"httpP95Ms": 5, "interactionMs": 50},
            "comparisonUnit": "whole rotated round, descriptive only",
        },
        "builds": [], "artifacts": {}, "preflight": [], "attempts": [],
    }

    def retain():
        (output / "results.json").write_text(json.dumps(evidence, indent=2) + "\n")

    for round_index in range(PACKAGED["ROUNDS"]):
        for order, kind in enumerate(round_order(round_index), 1):
            label = f"build-{round_index + 1}-{kind}.log"
            result = build(kind, args, output / label)
            evidence["builds"].append({"round": round_index + 1, "order": order, **result})
            retain()
            print(label, result["exit"], flush=True)
            if result["exit"] != 0:
                raise RuntimeError(f"Build failed. See {label}")

    paths = {kind: ROOT / PROJECTS[kind] / "build/libs" / JARS[kind] for kind in KINDS}
    evidence["artifacts"] = {kind: archive(path) for kind, path in paths.items()}
    retain()
    for kind, path in paths.items():
        result = run(kind, path, args, output, "preflight", measure=False)
        evidence["preflight"].append(result)
        print(kind, result["status"], result.get("error", ""), flush=True)
        retain()
    if any(result["status"] != "passed" for result in evidence["preflight"]):
        raise RuntimeError("A contender did not pass the common browser gate; no three-way timing performed.")

    for round_index in range(PACKAGED["ROUNDS"]):
        for order, kind in enumerate(round_order(round_index), 1):
            result = run(kind, paths[kind], args, output, f"round-{round_index + 1}", measure=True)
            evidence["attempts"].append({"round": round_index + 1, "order": order, **result})
            print(round_index + 1, kind, result["status"], result.get("error", ""), flush=True)
            retain()
    if any(result["status"] != "passed" for result in evidence["attempts"]):
        raise RuntimeError("A measured round failed. See results.json before comparing.")


if __name__ == "__main__":
    main()
