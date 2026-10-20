#!/usr/bin/env python3
"""Run the JVM comparison against one task-owned Todo container, then remove it."""
import argparse
import json
import pathlib
import subprocess
import time
import uuid
import urllib.request

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--app-image", default="woge-todo:roadmap")
parser.add_argument("--browser-image", default="mcr.microsoft.com/playwright/java:v1.63.0-noble")
parser.add_argument("--node", action="store_true", help="Run the unchanged Node Todo journeys on Playwright 1.63.0 instead")
parser.add_argument("--webmcp", action="store_true", help="Probe the opt-in declarative form experiment")
parser.add_argument("--parallel", action="store_true", help="Use independent thread-confined JVM browser workers")
parser.add_argument("--shared-store", action="store_true", help="Explicit multi-user stress mode: parallel workers share the public live store")
parser.add_argument("--failure-probe", action="store_true", help="Capture one deliberate JVM assertion failure")
parser.add_argument("--node-failure", action="store_true", help="Verify Node's report/trace for a deliberate assertion failure")
parser.add_argument("--pointer-probe", action="store_true", help="Compare native and live DOM replacement during pointer activation")
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parent
name = "woge-testing-" + uuid.uuid4().hex[:12]
worker = name + "-browser"
extra_apps = []
try:
    publication = ["-p", "127.0.0.1::8080"]
    if args.parallel and not args.shared_store:
        publication += ["-p", "127.0.0.1::8081", "-p", "127.0.0.1::8082"]
    subprocess.run(["docker", "run", "-d", "--name", name, "--memory", "512m", *publication, args.app_image], check=True)
    if args.parallel and not args.shared_store:
        for index, engine in enumerate(("firefox", "webkit"), 1):
            extra = name + "-" + engine
            subprocess.run(["docker", "run", "-d", "--name", extra, "--network", "container:" + name, "--memory", "512m", "-e", "PORT=" + str(8080 + index), args.app_image], check=True, stdout=subprocess.DEVNULL)
            extra_apps.append(extra)
    port = subprocess.check_output(["docker", "port", name, "8080/tcp"], text=True).strip().rsplit(":", 1)[1]
    deadline = time.monotonic() + 60
    for app_port in range(8080, 8081 + len(extra_apps)):
        published = subprocess.check_output(["docker", "port", name, str(app_port) + "/tcp"], text=True).strip().rsplit(":", 1)[1]
        while True:
            try:
                with urllib.request.urlopen("http://127.0.0.1:" + published + "/actuator/health/readiness", timeout=1) as response:
                    if response.status == 200:
                        break
            except OSError:
                pass
            if time.monotonic() >= deadline:
                raise RuntimeError("Todo readiness timed out")
            time.sleep(0.2)
    image = "mcr.microsoft.com/playwright:v1.63.0-noble" if args.node or args.webmcp or args.node_failure or args.pointer_probe else args.browser_image
    test_command = (["node", "webmcp.mjs"] if args.webmcp else ["npm", "test"]) if args.node or args.webmcp else ["build/install/woge-consumer-testing-spike/bin/woge-consumer-testing-spike"]
    if args.parallel:
        test_command.append("--parallel")
    if args.failure_probe:
        test_command.append("--failure-probe")
    if args.node_failure:
        test_command = ["npx", "playwright", "test", "--config=failure.config.mjs"]
    if args.pointer_probe:
        test_command = ["node", "pointer-probe.mjs"]
    result = subprocess.run(["docker", "run", "--rm", "--name", worker, "--network", "container:" + name, "--memory", "2g",
                    "--mount", "type=bind,src=" + str(root) + ",dst=/work", "-w", "/work",
                    "--mount", "type=bind,src=" + str(root.parents[1] / "examples/todo-spring-boot/browser-tests") + ",dst=/examples/todo-spring-boot/browser-tests,readonly",
                    "-e", "WOGE_TODO_URL=http://127.0.0.1:8080", "-e", "WOGE_TODO_URLS=chromium=http://127.0.0.1:8080,firefox=http://127.0.0.1:" + ("8081" if extra_apps else "8080") + ",webkit=http://127.0.0.1:" + ("8082" if extra_apps else "8080"), image,
                    *test_command], check=not args.node_failure, timeout=180)
    if args.node_failure:
        assert result.returncode == 1, "The Node failure probe must fail exactly as expected"
        assert list((root / "build/expected-failure").rglob("trace.zip")), "Expected a retained failure trace"
        report = json.loads((root / "build/evidence/node-expected-failure.json").read_text())
        assert report["stats"]["unexpected"] == 1 and not report["errors"]
        diagnostic = report["suites"][0]["specs"][0]["tests"][0]["results"][0]["error"]["message"]
        assert "Expected missing button" in diagnostic and "toBeVisible" in diagnostic
finally:
    subprocess.run(["docker", "rm", "-f", worker], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for extra in reversed(extra_apps):
        subprocess.run(["docker", "rm", "-f", extra], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.run(["docker", "rm", "-f", name], stdout=subprocess.DEVNULL)
