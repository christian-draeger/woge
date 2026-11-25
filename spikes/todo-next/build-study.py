#!/usr/bin/env python3
"""Build the three matched Todo distributions and retain separate setup and clean-build runs."""

import argparse
import json
from pathlib import Path
import platform
import secrets
import subprocess
import time

from study import CPU, NODE, ROOT, ROUNDS, command

JDK = "eclipse-temurin@sha256:db5cc6341a1d7bc40cdb51e1f0d9824f3bb2cc8107d29da421dc4df7d11f05f1"
BUILD_MEMORY = 3 * 1024 ** 3
CONTENDERS = ("next", "kte", "woge")
GRADLE = [
    "/opt/gradle/bin/gradle", "--no-daemon", "--max-workers=2", "--no-build-cache",
    "--no-configuration-cache", "-Dorg.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=768m",
    "-Pkotlin.compiler.execution.strategy=in-process",
]


def mount(source, target, readonly=False):
    return ["--mount", f"type=bind,source={source},target={target}" + (",readonly" if readonly else "")]


def build_command(kind, args, cache, name, cold):
    docker = [
        "docker", "run", "--rm", "--name", name, "--pull=never", "--platform", "linux/arm64",
        "--cpus", str(CPU), "--memory", str(BUILD_MEMORY),
        *mount(ROOT, "/workspace"), *mount(cache, "/dependency-cache"),
    ]
    if not cold:
        docker.extend(["--network", "none"])
    if kind == "next":
        docker.extend([
            "-w", "/workspace/spikes/todo-next", "-e", "NEXT_TELEMETRY_DISABLED=1",
            "-e", "npm_config_cache=/dependency-cache", NODE,
        ])
        if cold:
            return docker + ["npm", "ci", "--ignore-scripts", "--no-audit", "--no-fund"]
        return docker + ["sh", "-ec", "rm -rf .next && npm run build"]

    project = "/workspace/spikes/todo-kte-htmx"
    flags = []
    docker.extend([*mount(args.gradle, "/opt/gradle", True), "-e", "GRADLE_USER_HOME=/dependency-cache"])
    if kind == "woge":
        docker.extend([*mount(args.woge_stage, "/consumer"), *mount(args.maven_repository, "/maven-repository", True)])
        project = "/consumer/examples/todo-spring-boot"
        flags = ["-PwogeRepository=/maven-repository", "-PwogeVersion=0.1.0-SNAPSHOT"]
    options = ["-I", "/workspace/spikes/todo-next/resolve-study.gradle", "resolveStudyDependencies"] if cold else [
        "--offline", "clean", "bootJar",
    ]
    return docker + ["-w", project, JDK, *GRADLE, *flags, *options]


def sample(kind, args, label, cold):
    cache = args.caches / kind
    name = "woge-todo-build-" + secrets.token_hex(6)
    launch = build_command(kind, args, cache, name, cold)
    log = args.output / f"{label}-{kind}.log"
    started = time.monotonic()
    with log.open("w") as output:
        try:
            outcome = subprocess.run(launch, stdout=output, stderr=subprocess.STDOUT, timeout=300)
            exit_code = outcome.returncode
        except subprocess.TimeoutExpired:
            output.write("\nBuild exceeded the 300 second sample deadline.\n")
            subprocess.run(["docker", "rm", "-f", name], capture_output=True, timeout=15)
            exit_code = 124
    return {"contender": kind, "phase": label, "elapsedMs": (time.monotonic() - started) * 1000,
            "exitCode": exit_code, "command": launch, "log": log.name}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ("woge-stage", "maven-repository", "gradle", "caches", "output"):
        parser.add_argument(f"--{option}", type=lambda argument: Path(argument).resolve(), required=True)
    parser.add_argument("--warm-dependencies-only", action="store_true", help="Repeat builds with populated caches")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    candidates = set(CONTENDERS)
    for kind in CONTENDERS:
        cache = args.caches / kind
        cache.mkdir(parents=True, exist_ok=True)
        populated = any(cache.iterdir())
        if populated != args.warm_dependencies_only:
            parser.error(f"Expected {'populated' if args.warm_dependencies_only else 'empty'} cache {cache}")
    results = {
        "host": platform.platform(), "dockerVersion": command("docker", "version", "--format", "{{.Server.Version}}"),
        "sourceCommit": command("git", "-C", str(ROOT), "rev-parse", "HEAD"),
        "sourceSeed": json.loads((args.woge_stage / "woge-seed.json").read_text()),
        "toolchains": {"nodeImage": NODE, "jdkImage": JDK, "gradle": args.gradle.name},
        "environment": {"architecture": "linux/arm64", "cpus": CPU, "memoryBytes": BUILD_MEMORY,
                        "warmNetwork": "none", "warmOutputCaches": False},
        "coldDependencySetup": [], "warmCleanBuilds": [],
    }

    def retain(section, record):
        results[section].append(record)
        (args.output / "builds.json").write_text(json.dumps(results, indent=2) + "\n")
        print(record["phase"], record["contender"], record["exitCode"], round(record["elapsedMs"]), flush=True)

    if not args.warm_dependencies_only:
        for kind in CONTENDERS:
            record = sample(kind, args, "cold-setup", cold=True)
            retain("coldDependencySetup", record)
            if record["exitCode"]:
                candidates.remove(kind)
    for index in range(ROUNDS):
        queue = CONTENDERS[index:] + CONTENDERS[:index]
        for order, kind in enumerate(queue, 1):
            if kind not in candidates:
                continue
            record = sample(kind, args, f"warm-build-{index + 1}", cold=False)
            retain("warmCleanBuilds", {"round": index + 1, "order": order, **record})

    if candidates != set(CONTENDERS) or any(result["exitCode"] for result in results["warmCleanBuilds"]):
        raise SystemExit("A dependency setup or offline build failed. See the retained build logs.")


if __name__ == "__main__":
    main()
