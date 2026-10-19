#!/usr/bin/env python3
"""Run the JVM comparison against one task-owned Todo container, then remove it."""
import argparse
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
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parent
name = "woge-testing-" + uuid.uuid4().hex[:12]
worker = name + "-browser"
try:
    subprocess.run(["docker", "run", "-d", "--name", name, "--memory", "512m", "-p", "127.0.0.1::8080", args.app_image], check=True)
    port = subprocess.check_output(["docker", "port", name, "8080/tcp"], text=True).strip().rsplit(":", 1)[1]
    deadline = time.monotonic() + 60
    while True:
        try:
            with urllib.request.urlopen("http://127.0.0.1:" + port + "/actuator/health/readiness", timeout=1) as response:
                if response.status == 200:
                    break
        except OSError:
            pass
        if time.monotonic() >= deadline:
            raise RuntimeError("Todo readiness timed out")
        time.sleep(0.2)
    image = "mcr.microsoft.com/playwright:v1.63.0-noble" if args.node or args.webmcp else args.browser_image
    test_command = (["node", "webmcp.mjs"] if args.webmcp else ["npm", "test"]) if args.node or args.webmcp else ["build/install/woge-consumer-testing-spike/bin/woge-consumer-testing-spike"]
    subprocess.run(["docker", "run", "--rm", "--name", worker, "--network", "container:" + name, "--memory", "2g",
                    "--mount", "type=bind,src=" + str(root) + ",dst=/work", "-w", "/work",
                    "--mount", "type=bind,src=" + str(root.parents[1] / "examples/todo-spring-boot/browser-tests") + ",dst=/examples/todo-spring-boot/browser-tests,readonly",
                    "-e", "WOGE_TODO_URL=http://127.0.0.1:8080", image,
                    *test_command], check=True, timeout=180)
finally:
    subprocess.run(["docker", "rm", "-f", worker], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.run(["docker", "rm", "-f", name], stdout=subprocess.DEVNULL)
