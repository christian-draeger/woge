#!/usr/bin/env python3
"""Run, verify, measure or build a local image for a packaged reference host."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import socket
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
BROWSER = ROOT / "client/woge-fallback-client"
HOSTS = ("spring-webflux", "spring-mvc", "ktor")
PROJECTS = tuple(
    f"{engine}-{mode}"
    for engine in ("chromium", "firefox", "webkit")
    for mode in ("enhanced", "no-javascript")
)
BROWSER_TIMEOUT_MS = 300_000
IMAGE_DOCKERFILE = ROOT / "testing/deployment/Dockerfile"
IMAGE_BASE = "eclipse-temurin:21-jre@sha256:cff19e6215689161eb6162c11b86b0c60ddf802164f2eaf48d570f8fb79a36c5"


def version():
    return next(
        line.split("=", 1)[1]
        for line in (ROOT / "gradle.properties").read_text().splitlines()
        if line.startswith("wogeVersion=")
    )


def package(host, woge_version, java):
    name = f"woge-reference-{host}"
    output = ROOT / "examples/reference-application" / host / "build"
    if host == "ktor":
        distribution = output / "install" / name
        return f":{name}:installDist", distribution, [str(distribution / "bin" / name)]
    jar = output / "libs" / f"{name}-{woge_version}.jar"
    return f":{name}:bootJar", jar, [java, "-jar", str(jar)]


def free_port():
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


def output(*args):
    return subprocess.check_output(args, cwd=ROOT, stderr=subprocess.STDOUT, text=True).strip()


def artifact_hashes(path):
    files = [path] if path.is_file() else sorted(path.rglob("*"))
    hashes = {}
    for file in files:
        if file.is_file():
            checksum = hashlib.sha256()
            with file.open("rb") as stream:
                for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                    checksum.update(chunk)
            hashes[str(file.relative_to(ROOT))] = checksum.hexdigest()
    if not hashes:
        raise RuntimeError(f"No packaged artifact found at {path}.")
    return hashes


def build_image(host, woge_version, package_path, build):
    directory = ROOT / "build/reference-demo"
    directory.mkdir(parents=True, exist_ok=True)
    evidence = Path(tempfile.mkdtemp(prefix=f"{host}-image-", dir=directory))
    print(f"Evidence directory {evidence}", flush=True)
    revision = output("git", "rev-parse", "HEAD")
    dirty = bool(output("git", "status", "--porcelain"))
    qualifier = "-dirty" if dirty else ""
    tag = f"woge-reference-{host}:{revision[:12]}{qualifier}"
    report = {
        "schemaVersion": 1,
        "kind": "source-container",
        "host": host,
        "sourceCommit": revision,
        "sourceDirty": dirty,
        "wogeVersion": woge_version,
        "baseImage": IMAGE_BASE,
        "tag": tag,
        "status": "failed",
    }
    try:
        with tempfile.TemporaryDirectory(prefix="woge-reference-context-") as work:
            context = Path(work)
            payload = context / host
            image_id = context / "image-id"
            with (evidence / "build.log").open("w") as log:
                subprocess.run(build, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True)
                report["artifactSha256"] = artifact_hashes(package_path)
                report["recipeSha256"] = artifact_hashes(IMAGE_DOCKERFILE)
                if package_path.is_file():
                    payload.mkdir()
                    shutil.copy2(package_path, payload / "server.jar")
                else:
                    shutil.copytree(package_path, payload)
                command = [
                    "docker", "build", "--iidfile", str(image_id),
                    "--file", str(IMAGE_DOCKERFILE),
                    "--build-arg", f"HOST={host}", "--build-arg", f"BASE_IMAGE={IMAGE_BASE}",
                    "--label", f"org.opencontainers.image.revision={revision}",
                    "--label", f"org.opencontainers.image.version={woge_version}",
                    "--label", f"dev.woge.source.dirty={str(dirty).lower()}",
                    "--tag", tag, str(context),
                ]
                subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=300)
            report["imageId"] = image_id.read_text().strip()
        report["platform"] = output("docker", "image", "inspect", "--format", "{{.Os}}/{{.Architecture}}", report["imageId"])
        report["status"] = "passed"
    except (OSError, RuntimeError, subprocess.SubprocessError) as error:
        report["error"] = str(error)
    finally:
        (evidence / "manifest.json").write_text(json.dumps(report, indent=2) + "\n")
    if report["status"] != "passed":
        print(f"Image build failed. See {evidence / 'build.log'}.")
        return 1
    print(f"Built {tag} ({report['imageId']})")
    return 0


def verify(args, port, woge_version, java, package_path, build):
    directory = ROOT / "build/reference-demo"
    directory.mkdir(parents=True, exist_ok=True)
    evidence = Path(tempfile.mkdtemp(prefix=f"{args.host}-", dir=directory))
    print(f"Evidence directory {evidence}", flush=True)
    lock = json.loads((BROWSER / "package-lock.json").read_text())
    report = {
        "schemaVersion": 1,
        "kind": "source-browser-budget" if args.mode == "measure" else "source-correctness",
        "sourceCommit": output("git", "rev-parse", "HEAD"),
        "sourceDirty": bool(output("git", "status", "--porcelain")),
        "wogeVersion": woge_version,
        "host": args.host,
        "origin": f"http://127.0.0.1:{port}",
        "platform": platform.platform(),
        "java": output(java, "-version"),
        "node": output("node", "--version"),
        "playwright": lock["packages"]["node_modules/@playwright/test"]["version"],
        "browserProjects": args.project or list(PROJECTS),
        "dataset": "shared project and task-board fixtures, reset by a new server process",
        "instrumentation": "browser contract trace on failure",
        "browserTimeoutMs": BROWSER_TIMEOUT_MS,
        "status": "failed",
    }
    environment = {
        **os.environ,
        "WOGE_REFERENCE_HOST": args.host,
        "WOGE_REFERENCE_PORT": str(port),
        "WOGE_REFERENCE_PACKAGED": "1",
        "WOGE_REFERENCE_REPORT_DIR": str(evidence),
    }
    browser = ["npm", "run", "test:reference", "--", "--global-timeout", str(BROWSER_TIMEOUT_MS)]
    budget_case = "real adapter production budgets"
    command = [*browser, "--grep-invert", budget_case]
    for project in args.project:
        command += ["--project", project]
    try:
        with (evidence / "build.log").open("w") as log:
            subprocess.run(
                ["node", "scripts/build-reference-theme.mjs"], cwd=BROWSER,
                stdout=log, stderr=subprocess.STDOUT, check=True,
            )
            subprocess.run(build, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True)
        report["artifactSha256"] = artifact_hashes(package_path)
        with (evidence / "browser.log").open("w") as log:
            result = subprocess.run(command, cwd=BROWSER, env=environment, stdout=log, stderr=subprocess.STDOUT)
        report["browserExitCode"] = result.returncode
        if result.returncode != 0:
            raise RuntimeError("The shared browser journeys failed. See browser.log.")
        if args.mode == "measure":
            measurement = evidence / "measurement"
            measurement.mkdir()
            report["measurementSamples"] = "measurement/browser-budget.json"
            with (measurement / "browser.log").open("w") as log:
                subprocess.run(
                    [*browser, "--grep", budget_case, "--project", "chromium-enhanced"],
                    cwd=BROWSER, env={**environment, "WOGE_REFERENCE_REPORT_DIR": str(measurement)},
                    stdout=log, stderr=subprocess.STDOUT, check=True,
                )
        report["status"] = "passed"
    except (OSError, RuntimeError, subprocess.SubprocessError) as error:
        report["error"] = str(error)
    except KeyboardInterrupt:
        report["status"] = "cancelled"
    finally:
        (evidence / "manifest.json").write_text(json.dumps(report, indent=2) + "\n")
    print(report.get("error", f"Browser verification {report['status']}."))
    return 0 if report["status"] == "passed" else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("run", "verify", "measure", "serve", "image"))
    parser.add_argument("host", choices=HOSTS)
    parser.add_argument("--port", type=int)
    parser.add_argument("--project", action="append", choices=PROJECTS, default=[])
    args = parser.parse_args()
    if args.port is not None and not 1 <= args.port <= 65535:
        parser.error("--port must be between 1 and 65535")
    if args.port is not None and args.mode == "image":
        parser.error("--port only applies to a running reference host")
    checks = args.mode in ("verify", "measure")
    if args.project and not checks:
        parser.error("--project applies to verify and measure")
    port = args.port or (free_port() if checks else 8080)
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin/java") if java_home else "java"
    woge_version = version()
    task, package_path, launch = package(args.host, woge_version, java)
    build = [str(ROOT / "gradlew"), task, f"-PwogeVersion={woge_version}", "--console=plain"]
    if args.mode == "image":
        return build_image(args.host, woge_version, package_path, build)
    if checks:
        return verify(args, port, woge_version, java, package_path, build)
    if args.mode == "run":
        subprocess.run(build, cwd=ROOT, check=True)
        print(f"Open http://127.0.0.1:{port}/projects/woge", flush=True)
    environment = {
        **os.environ,
        "PORT": str(port),
        "SERVER_PORT": str(port),
        "SERVER_ADDRESS": "127.0.0.1",
        "WOGE_REFERENCE_BIND_ADDRESS": "127.0.0.1",
        "WOGE_DEPLOYMENT": "false",
    }
    os.execvpe(launch[0], launch, environment)


if __name__ == "__main__":
    raise SystemExit(main())
