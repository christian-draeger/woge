#!/usr/bin/env python3
"""Verify a task-owned Todo container behind nginx, optionally with real browsers."""

import argparse
import http.client
import json
import pathlib
import re
import subprocess
import tempfile
import time
import uuid
from urllib.parse import urlencode


def command(*args, **kwargs):
    return subprocess.run(args, check=True, text=True, **kwargs)


def request(port, method, path, body=None, headers=None):
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=10)
    try:
        connection.request(method, path, body=body, headers=headers or {})
        response = connection.getresponse()
        return response.status, dict(response.getheaders()), response.read(1_048_576).decode()
    finally:
        connection.close()


def verify(args):
    root = pathlib.Path(__file__).resolve().parents[1]
    name = "woge-todo-" + uuid.uuid4().hex[:12]
    network, proxy = name + "-network", name + "-proxy"
    evidence = root / "build/todo-container"
    evidence.mkdir(parents=True, exist_ok=True)
    owned = []
    network_created = False
    try:
        command("docker", "network", "create", network, stdout=subprocess.DEVNULL)
        network_created = True
        command(
            "docker", "run", "-d", "--name", name, "--network", network,
            "--read-only", "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m",
            "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
            "--memory", "512m", "-e", "PORT=8080", args.image,
            stdout=subprocess.DEVNULL,
        )
        owned.append(name)
        with tempfile.TemporaryDirectory(prefix="woge-todo-proxy-") as scratch:
            config = pathlib.Path(scratch) / "nginx.conf"
            config.write_text(
                "events {}\nhttp { server { listen 8080; location / { "
                "proxy_pass http://" + name + ":8080; proxy_http_version 1.1; "
                "proxy_buffering off; proxy_cache off; gzip off; "
                "proxy_set_header Host $http_host; } } }\n"
            )
            command(
                "docker", "run", "-d", "--name", proxy, "--network", network,
                "--read-only", "--tmpfs", "/var/cache/nginx", "--tmpfs", "/var/run",
                "--mount", "type=bind,src=" + str(config) + ",dst=/etc/nginx/nginx.conf,readonly",
                "-p", "127.0.0.1::8080", "--entrypoint", "nginx",
                args.proxy_image, "-g", "daemon off;", stdout=subprocess.DEVNULL,
            )
            owned.append(proxy)
            port = int(command("docker", "port", proxy, "8080/tcp", capture_output=True).stdout.strip().rsplit(":", 1)[1])
            deadline = time.monotonic() + 60
            while True:
                try:
                    if request(port, "GET", "/actuator/health/readiness")[0] == 200:
                        break
                except (OSError, http.client.HTTPException):
                    pass
                if time.monotonic() >= deadline:
                    raise RuntimeError("Todo container did not become ready behind nginx")
                time.sleep(0.25)
            image = json.loads(command("docker", "image", "inspect", args.image, capture_output=True).stdout)[0]
            if image["Config"]["User"] not in ("10001", "10001:10001"):
                raise RuntimeError("Todo image must run as its documented non-root user")
            status, _, html = request(port, "GET", "/")
            assert status == 200 and "<h1>Todos</h1>" in html
            origin = "http://127.0.0.1:" + str(port)
            status, _, _ = request(port, "POST", "/woge-actions/add-todo", urlencode({"title": "Container <todo>"}), {
                "Origin": origin, "Content-Type": "application/x-www-form-urlencoded",
            })
            assert status == 303, status
            assert "Container &lt;todo&gt;" in request(port, "GET", "/")[2]
            assert request(port, "POST", "/woge-actions/add-todo", "title=denied", {
                "Origin": "https://other.example", "Content-Type": "application/x-www-form-urlencoded",
            })[0] == 403
            epoch = re.search(r'name="woge-page-epoch" content="([^"]+)"', html)[1]
            connection = http.client.HTTPConnection("127.0.0.1", port, timeout=10)
            try:
                started = time.monotonic()
                connection.request("GET", "/?" + urlencode({"_woge_live": epoch, "_woge_protocol_version": "1"}),
                                   headers={"Accept": "text/event-stream"})
                stream = connection.getresponse()
                assert stream.status == 200
                assert stream.getheader("Content-Type").startswith("text/event-stream")
                first = stream.readline(4096)
                assert first, "SSE must flush a frame without completing the response"
                first_frame_ms = round((time.monotonic() - started) * 1000, 2)
            finally:
                connection.close()
            if args.browser_image:
                # Loopback is a trustworthy browser context without introducing test TLS.
                command(
                    "docker", "run", "--rm", "--network", "container:" + proxy, "--memory", "2g",
                    "--mount", "type=bind,src=" + str(root / "examples/todo-spring-boot") + ",dst=/work",
                    "-w", "/work", "-e", "WOGE_TODO_URL=http://127.0.0.1:8080", "-e", "CI=1",
                    args.browser_image, "npm", "run", "test:browser",
                )
            command("docker", "stop", "--time", "30", name, stdout=subprocess.DEVNULL)
            state = json.loads(command("docker", "inspect", name, capture_output=True).stdout)[0]["State"]
            assert state["ExitCode"] != 137 and not state["OOMKilled"], state
            result = {
                "imageId": image["Id"], "architecture": image["Architecture"],
                "labels": image["Config"].get("Labels", {}), "nonRoot": True, "readOnlyRoot": True,
                "nginx": args.proxy_image, "firstSseFrameMs": first_frame_ms,
                "browserImage": args.browser_image, "exitCode": state["ExitCode"],
            }
            (evidence / "result.json").write_text(json.dumps(result, indent=2))
            print(json.dumps(result))
    finally:
        for container in reversed(owned):
            log = subprocess.run(["docker", "logs", container], capture_output=True, text=True)
            (evidence / (container + ".log")).write_text(log.stdout + log.stderr)
            subprocess.run(["docker", "rm", "-f", container], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if network_created:
            subprocess.run(["docker", "network", "rm", network], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("image", help="Locally built Todo image, for example woge-todo:local")
    parser.add_argument("--proxy-image", default="nginx:1.28-alpine")
    parser.add_argument("--browser-image", help="Optional matching Playwright image; run npm ci first")
    verify(parser.parse_args())
