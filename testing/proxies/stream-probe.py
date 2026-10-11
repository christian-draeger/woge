#!/usr/bin/env python3
"""HTTP/1.1 stream timing probe; stdlib only. Optional short-lived nginx Docker comparison."""
import argparse
import gzip
import http.client
import json
import platform
import signal
import socket
import subprocess
import time
import uuid
from pathlib import Path
from urllib.parse import urlsplit


def measure(url, encoding, profile, label):
    parsed = urlsplit(url)
    connection_type = http.client.HTTPSConnection if parsed.scheme == "https" else http.client.HTTPConnection
    connection = connection_type(parsed.hostname, parsed.port, timeout=8)
    started = time.monotonic()
    arrivals = []
    payload = bytearray()
    try:
        path = parsed.path + ("?" + parsed.query if parsed.query else "")
        connection.request("GET", path, headers={"Accept-Encoding": encoding})
        response = connection.getresponse()
        if response.status != 200:
            raise AssertionError(f"Unexpected HTTP {response.status}")
        while True:
            chunk = response.read1(65536)
            if not chunk:
                break
            arrivals.append({"ms": round((time.monotonic() - started) * 1000, 1), "bytes": len(chunk)})
            payload.extend(chunk)
        end_ms = round((time.monotonic() - started) * 1000, 1)
        actual_encoding = response.getheader("Content-Encoding", "identity")
        decoded = gzip.decompress(payload) if actual_encoding == "gzip" else payload
        if not arrivals or not all(f"chunk={index}".encode() in decoded for index in range(4)):
            raise AssertionError("Expected four diagnostic chunks")
        result = {
            "host": label, "profile": profile, "protocol": f"HTTP/{response.version // 10}.{response.version % 10}",
            "accept": encoding, "encoding": actual_encoding,
            "transfer": response.getheader("Transfer-Encoding", "none"),
            "firstMs": arrivals[0]["ms"], "endMs": end_ms, "arrivals": arrivals,
        }
        print(json.dumps(result), flush=True)
        return end_ms - arrivals[0]["ms"] >= 800
    finally:
        connection.close()


def proxy_profile(url, label, proxy="nginx", buffered=False):
    parsed = urlsplit(url)
    if parsed.scheme != "http" or parsed.hostname not in ("localhost", "127.0.0.1"):
        raise ValueError("Docker comparison requires a local HTTP fixture")
    with socket.socket() as allocator:
        allocator.bind(("127.0.0.1", 0))
        port = allocator.getsockname()[1]
    linux = platform.system() == "Linux"
    upstream = f"{'127.0.0.1' if linux else 'host.docker.internal'}:{parsed.port}"
    template = Path(__file__).with_name("nginx.conf" if proxy == "nginx" else "Caddyfile").read_text()
    config = template.replace("@PORT@", str(port) if linux else "80").replace("@UPSTREAM@", upstream)
    config = config.replace("{$WOGE_PROXY_PORT:9080}", str(port) if linux else "80")
    config = config.replace("{$WOGE_UPSTREAM:127.0.0.1:8080}", upstream)
    if not linux:
        config = config.replace("listen 127.0.0.1:80", "listen 80").replace("http://127.0.0.1:80", "http://:80")
    if buffered:
        config = config.replace("proxy_buffering off;", "proxy_buffering on;\n            proxy_ignore_headers X-Accel-Buffering;")
        config = config.replace("gzip off;", "gzip on;\n            gzip_min_length 0;\n            gzip_proxied any;")
    name = "woge-stream-" + uuid.uuid4().hex[:12]
    directory = Path.cwd() / "build" / "stream-probe"
    directory.mkdir(parents=True, exist_ok=True)
    config_path = directory / f"{name}.conf"
    config_path.write_text(config)
    network = ["--network", "host"] if linux else ["-p", f"127.0.0.1:{port}:80"]
    target = "/etc/nginx/nginx.conf" if proxy == "nginx" else "/etc/caddy/Caddyfile"
    image = "nginx:1.28-alpine" if proxy == "nginx" else "caddy:2.10-alpine"
    command = ["docker", "run", "--rm", "--name", name, "-d", *network,
               "-v", f"{config_path}:{target}:ro", image]
    try:
        subprocess.run(command, check=True, capture_output=True, timeout=20)
        deadline = time.monotonic() + 5
        while True:
            try:
                connection = http.client.HTTPConnection("127.0.0.1", port, timeout=0.5)
                try:
                    connection.request("HEAD", parsed.path)
                    connection.getresponse().close()
                    break
                finally:
                    connection.close()
            except (OSError, http.client.HTTPException):
                if time.monotonic() >= deadline:
                    raise TimeoutError(f"{proxy} did not become ready")
                time.sleep(0.1)
        profile = "nginx-buffered-gzip-control" if buffered else f"{proxy}-recommended"
        proxied = f"http://127.0.0.1:{port}{parsed.path}"
        for encoding in ("identity", "gzip"):
            streamed = measure(proxied, encoding, profile, label)
            if not buffered and not streamed:
                raise AssertionError(f"{profile} accept={encoding}: buffering detected")
            if buffered and encoding == "gzip" and streamed:
                raise AssertionError("Deliberate gzip buffering control was not detected")
    except Exception:
        subprocess.run(["docker", "logs", name], timeout=5)
        raise
    finally:
        subprocess.run(["docker", "rm", "-f", name], capture_output=True, timeout=10)
        config_path.unlink(missing_ok=True)


def interrupted(signum, frame):
    raise TimeoutError("Stream probe interrupted; releasing proxy container")


def main():
    signal.signal(signal.SIGTERM, interrupted)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("url", help="URL of the four-chunk Woge TCK diagnostic")
    parser.add_argument("--label", default="custom-host", help="host name included in each measurement")
    proxies = parser.add_mutually_exclusive_group()
    proxies.add_argument("--nginx", action="store_true", help="compare recommended and deliberately buffered nginx")
    proxies.add_argument("--caddy", action="store_true", help="compare the recommended Caddy profile")
    args = parser.parse_args()
    for encoding in ("identity", "gzip"):
        if not measure(args.url, encoding, "direct", args.label):
            raise AssertionError("Buffering detected on direct diagnostic stream")
    if args.nginx:
        proxy_profile(args.url, args.label)
        proxy_profile(args.url, args.label, buffered=True)
    if args.caddy:
        proxy_profile(args.url, args.label, proxy="caddy")


if __name__ == "__main__":
    main()
