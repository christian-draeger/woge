#!/usr/bin/env python3
"""Build and verify isolated Todo variants. Timing is outside this study."""

import argparse
from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
import io
import json
from pathlib import Path
import re
import socket
import subprocess
import tarfile
import time

from cases import CASES

ROOT = Path(__file__).resolve().parents[2]
SOURCE = "cabaea870919fc7a1dfca0e3664643982ade2207"
PROJECTS = {
    "woge": Path("examples/todo-spring-boot"),
    "kte": Path("spikes/todo-kte-htmx"),
    "datastar": Path("spikes/todo-datastar"),
}
ARTIFACTS = {"woge": "woge-todo-0.1.0.jar", "kte": "todo-kte-htmx.jar", "datastar": "todo-datastar.jar"}


def stage(output):
    tracked = subprocess.check_output(["git", "archive", SOURCE, *(str(path) for path in PROJECTS.values())], cwd=ROOT)
    for app in PROJECTS:
        with tarfile.open(fileobj=io.BytesIO(tracked)) as tree:
            tree.extractall(output / app, filter="data")
    todo = output / "woge" / PROJECTS["woge"] / "src/main/kotlin/example/todo/Todos.kt"
    text = todo.read_text()
    marker = "internal val todos = InMemoryTodoRepository()\n"
    if text.count(marker) != 1:
        raise RuntimeError("Expected one Woge bootstrap marker")
    todo.write_text(text.replace(marker, '''internal val todos = InMemoryTodoRepository().apply {
    repeat(10) { add("Todo <${it + 1}> & Kotlin") }
}
'''))


@contextmanager
def edit(project, edits):
    originals = {}
    locations = []
    try:
        for name, old, new, count in edits:
            path = project / name
            current = path.read_text()
            if current.count(old) != count:
                raise RuntimeError(f"Unexpected source marker count in {name}: {old!r}")
            originals.setdefault(path, current)
            line = current.count("\n", 0, current.index(old)) + 1
            locations.append({"path": name, "line": line, "before": old, "after": new})
            path.write_text(current.replace(old, new))
        yield locations
    finally:
        for path, original in originals.items():
            path.write_text(original)


def build(project, app, repository, log):
    args = [str(ROOT / "gradlew"), "-p", str(project), "--offline", "--console=plain", "--no-build-cache",
            "--no-configuration-cache", "--max-workers=2", "-Pkotlin.compiler.execution.strategy=in-process",
            "-Dorg.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=768m", "clean", "bootJar"]
    if app == "woge":
        args.extend([f"-PwogeRepository={repository}", "-PwogeVersion=0.1.0-SNAPSHOT"])
    with log.open("w") as buffer:
        outcome = subprocess.run(args, cwd=ROOT, stdout=buffer, stderr=subprocess.STDOUT, timeout=150)
    return {"command": args, "exitCode": outcome.returncode, "log": log.name}


def detect_build(log):
    output = log.read_text()
    if re.search(r":(?:kspKotlin|generateJte) FAILED", output):
        return "generator"
    if re.search(r":compileKotlin FAILED", output) and re.search(r"(?m)^e: .+", output):
        if "generated/" in output and "spikes/todo-kte-htmx" in output:
            return "generated-kte-kotlin"
        return "kotlin"
    return "build-unresolved"


@contextmanager
def serve(project, app, log):
    with socket.socket() as server:
        server.bind(("127.0.0.1", 0))
        port = server.getsockname()[1]
    java = ["java", "-jar", str(project / "build/libs" / ARTIFACTS[app]), f"--server.port={port}",
            "--server.address=127.0.0.1"]
    with log.open("w") as output:
        running = subprocess.Popen(java, stdout=output, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 20
            while time.monotonic() < deadline:
                if running.poll() is not None:
                    raise RuntimeError("Application exited before listening")
                try:
                    with socket.create_connection(("127.0.0.1", port), timeout=0.25):
                        break
                except OSError:
                    time.sleep(0.05)
            else:
                raise RuntimeError("Application startup timeout")
            yield f"http://127.0.0.1:{port}"
        finally:
            if running.poll() is None:
                running.terminate()
                try:
                    running.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    running.kill()
                    running.wait()


def run_app(app, output, repository, selected):
    project = output / "workspaces" / app / PROJECTS[app]
    logs = output / app
    logs.mkdir()
    results = []
    for case in selected:
        check, mutations = CASES[case]
        build_log = logs / f"{case}.build.log"
        result = {"case": case, "assertion": check}
        try:
            with edit(project, mutations[app]) as edits:
                result["edits"] = edits
                result["build"] = build(project, app, repository, build_log)
                if result["build"]["exitCode"]:
                    result["firstDetector"] = detect_build(build_log)
                else:
                    with serve(project, app, logs / f"{case}.server.log") as origin:
                        probe = subprocess.run(["node", str(Path(__file__).with_name("verify.mjs")), origin, check],
                                               capture_output=True, text=True, timeout=45)
                    (logs / f"{case}.browser.log").write_text(probe.stdout + probe.stderr)
                    if probe.returncode:
                        result["firstDetector"] = "browser-unresolved"
                    else:
                        result["browser"] = json.loads(probe.stdout)
                        failed = [name for name, item in result["browser"]["results"].items() if not item["passed"]]
                        result["firstDetector"] = "browser" if failed else "none"
        except (RuntimeError, OSError, subprocess.SubprocessError, ValueError) as problem:
            result.update(firstDetector="unresolved", error=str(problem))
        results.append(result)
        (logs / "results.json").write_text(json.dumps(results, indent=2) + "\n")
        print(app, case, result["firstDetector"], flush=True)
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--app", choices=PROJECTS, action="append")
    parser.add_argument("--case", choices=CASES, action="append")
    args = parser.parse_args()
    apps = args.app or list(PROJECTS)
    selected = args.case or list(CASES)
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    stage(output / "workspaces")
    with ThreadPoolExecutor(max_workers=len(apps)) as pool:
        jobs = {app: pool.submit(run_app, app, output, args.repository.resolve(), selected) for app in apps}
        results = {app: job.result() for app, job in jobs.items()}
    payload = {"source": SOURCE, "results": results}
    (output / "results.json").write_text(json.dumps(payload, indent=2) + "\n")
    for rows in results.values():
        for row in rows:
            actual = row["firstDetector"]
            if row["case"] in ("unchanged", "rename-private"):
                if actual != "none":
                    raise SystemExit("A clean control failed. Check the retained evidence.")
            elif actual not in {"kotlin", "generator", "generated-kte-kotlin", "browser"}:
                raise SystemExit("An edit was unresolved or undetected. Check the retained evidence.")


if __name__ == "__main__":
    main()
