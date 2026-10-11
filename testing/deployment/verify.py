#!/usr/bin/env python3
"""Real container evidence, not an in-process JVM test. Uses only Python's standard library."""

import base64
import concurrent.futures
import hashlib
import http.client
import io
import json
import os
from pathlib import Path
import re
import secrets
import signal
import struct
import subprocess
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[2]
CONTEXT = ROOT / "build/production-deployment"
REPORT = ROOT / "build/issue61"
MEDIA = "application/vnd.woge.patch-stream; version=1"


def docker(*args, timeout=60, environment=None):
    result = subprocess.run(
        ["docker", *args], check=True, capture_output=True, text=True, timeout=timeout, env=environment,
    )
    return (result.stdout + (result.stderr if args[0] == "logs" else "")).strip()


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        return None


def request(origin, path, headers=None, body=None):
    req = urllib.request.Request(origin + path, data=body, headers=headers or {})
    try:
        response = urllib.request.build_opener(NoRedirect).open(req, timeout=5)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, response.headers, response.read()


def eventually(check, seconds=30):
    deadline = time.monotonic() + seconds
    last = None
    while time.monotonic() < deadline:
        try:
            value = check()
            if value:
                return value
        except (urllib.error.URLError, ConnectionError, TimeoutError) as error:
            last = error
        threading.Event().wait(0.1)
    raise AssertionError(f"Readiness deadline exceeded; last connection error: {last}")


def origin(name, port):
    binding = docker("port", name, f"{port}/tcp")
    assert binding.startswith("127.0.0.1:")
    return "http://" + binding


def baseline_frames(data):
    """Narrow old v1 Replace/Complete reader, locked against the supported historical golden."""
    assert data[:5] == b"WOGE\x01"
    offset, frames = 5, []
    while offset < len(data):
        kind, content_size, metadata_size, payload_size = struct.unpack_from(">BBII", data, offset)
        assert kind in (1, 2), "Old client cannot accept a new frame kind"
        assert 0 < content_size <= 255 and metadata_size <= 65536 and payload_size <= 8388608
        offset += 10
        content = data[offset:offset + content_size]
        offset += content_size
        metadata = json.loads(data[offset:offset + metadata_size])
        offset += metadata_size
        payload = data[offset:offset + payload_size].decode("utf-8")
        offset += payload_size
        assert offset <= len(data)
        assert content == (
            b"text/html; charset=utf-8" if kind == 1 else b"application/json; charset=utf-8"
        )
        if kind == 1:
            assert metadata["protocolVersion"] == 1 and metadata["operation"] == "replace"
        else:
            assert not payload and offset == len(data)
        frames.append((kind, metadata, payload))
    assert frames[-1][0] == 2
    assert frames[-1][1]["patches"] == len(frames) - 1
    return frames


def read_stream(stream):
    try:
        return stream.read(), True
    except http.client.IncompleteRead as error:
        # A host may terminate its idle SSE transport on shutdown. Record, never call it completion.
        return error.partial, False


def extract_assets(archive, destination):
    with zipfile.ZipFile(archive) as jar:
        for item in jar.infolist():
            name = item.filename.removeprefix("BOOT-INF/classes/")
            if name.startswith("static/assets/") and not item.is_dir():
                relative = Path(name.removeprefix("static/assets/"))
                assert ".." not in relative.parts and not relative.is_absolute()
                target = destination / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(jar.read(item))
            elif item.filename.startswith("BOOT-INF/lib/") and item.filename.endswith(".jar"):
                extract_assets(io.BytesIO(jar.read(item)), destination)


def verify_host(host):
    prefix = "woge61-" + uuid.uuid4().hex[:12]
    work = REPORT / prefix
    work.mkdir(parents=True)
    assets = work / "assets/releases"
    jars = list((CONTEXT / host).rglob("*.jar"))
    assert jars, f"Missing packaged host {host}"
    for release in ("old", "new"):
        target = assets / release
        target.mkdir(parents=True)
        for jar in jars:
            extract_assets(jar, target)
        assert (target / "woge/index.js").is_file()
        with (target / "notes.css").open("a") as stylesheet:
            stylesheet.write(f"\n/* immutable release: {release} */\n")
    old_asset = (assets / "old/woge/index.js").read_bytes()
    (work / "nginx.conf").write_bytes((ROOT / "testing/deployment/nginx.conf").read_bytes())
    upstream = work / "upstream.conf"
    upstream.write_text(f"proxy_pass http://{prefix}-old:8080;\n")
    image = prefix + ":reference"
    containers = []
    network_created = False
    try:
        docker("build", "--quiet", "--build-arg", f"HOST={host}", "-t", image, str(CONTEXT), timeout=120)
        docker("network", "create", prefix)
        network_created = True
        secret = base64.b64encode(secrets.token_bytes(32)).decode("ascii")
        for release in ("old", "new"):
            name = f"{prefix}-{release}"
            docker(
                "run", "-d", "--name", name, "--network", prefix,
                "--memory", "512m", "--cpus", "2",
                "-p", "127.0.0.1::8080", "-p", "127.0.0.1::8081",
                "-e", "SPRING_PROFILES_ACTIVE=deployment",
                "-e", "WOGE_DEPLOYMENT=true", "-e", f"WOGE_RELEASE={release}",
                "-e", "WOGE_RENDER_IDENTITY_SECRET",
                "-e", "WOGE_LIVE_SUBSCRIPTIONS=1", "-e", "WOGE_LIVE_SESSION_SUBSCRIPTIONS=1",
                "-e", "WOGE_LIVE_LIFETIME_SECONDS=4",
                image, environment={**os.environ, "WOGE_RENDER_IDENTITY_SECRET": secret},
            )
            containers.append(name)
            app = origin(name, 8080)
            management = origin(name, 8081) if host != "ktor" else app
            ready = "/actuator/health/readiness" if host != "ktor" else "/health/ready"
            live = "/actuator/health/liveness" if host != "ktor" else "/health/live"
            def is_ready():
                assert docker("inspect", "--format", "{{.State.Running}}", name) == "true", (
                    f"{host} exited before readiness; see saved container logs"
                )
                return request(management, ready)[0] == 200
            eventually(is_ready)
            assert request(management, live)[0] == 200
            assert request(management, "/actuator/env")[0] == 404
            assert request(management, live)[2] in (
                b'{"status":"UP"}', b"UP",
            ), "Health must not expose application diagnostics"
        proxy = prefix + "-proxy"
        docker(
            "run", "-d", "--name", proxy, "--network", prefix,
            "-p", "127.0.0.1::8080",
            "-v", f"{work / 'nginx.conf'}:/etc/nginx/nginx.conf:ro",
            "-v", f"{upstream}:/etc/nginx/upstream.conf:ro",
            "-v", f"{work}:/srv:ro", "nginx:1.28-alpine",
        )
        containers.append(proxy)
        public = origin(proxy, 8080)
        eventually(lambda: request(public, "/deployment")[0] == 200)
        status, headers, html = request(public, "/deployment")
        assert status == 200 and b"Deployment old" in html
        assert "no-store" in headers["Cache-Control"]
        assert headers.get("Content-Encoding") is None
        (work / "old.html").write_bytes(html)
        epoch = re.search(rb'name="woge-page-epoch" content="([^"]+)"', html)[1].decode()
        target = re.search(rb'data-woge-region="([^"]+)"', html)[1].decode()
        action = re.search(rb'action="([^"]+)"', html)[1].decode()
        asset_path = "/assets/releases/old/woge/index.js"
        asset_status, asset_headers, asset_body = request(public, asset_path)
        assert asset_status == 200 and asset_body == old_asset
        assert "immutable" in asset_headers["Cache-Control"]
        assert request(public, asset_path, {"If-Modified-Since": asset_headers["Last-Modified"]})[0] == 304
        referenced_assets = re.findall(rb'(?:href|src)="(/assets/releases/old/[^"]+)"', html)
        assert len(referenced_assets) == 2
        retained = {}
        for path in referenced_assets:
            name = path.decode()
            status, asset_headers, body = request(public, name)
            assert status == 200 and "immutable" in asset_headers["Cache-Control"]
            retained[name] = body
        assert request(public, "/actuator/health")[0] == 404
        assert request(public, "/health/ready")[0] == 404

        # The old nginx worker keeps this real, idle SSE connection while new requests move away.
        stream = urllib.request.urlopen(public + "/deployment/live/" + epoch, timeout=8)
        with stream, concurrent.futures.ThreadPoolExecutor(max_workers=1) as executor:
            assert stream.status == 200
            assert "no-transform" in stream.headers["Cache-Control"]
            assert stream.headers.get("Content-Encoding") is None
            assert stream.readline().startswith(b"retry:")
            assert stream.readline() == b"\n"
            assert request(public, "/deployment/live/" + epoch)[0] == 503
            assert stream.readline() == b": heartbeat\n", "Idle SSE must flush before shutdown"
            assert stream.readline() == b"\n"
            reader = executor.submit(read_stream, stream)
            upstream.write_text(f"proxy_pass http://{prefix}-new:8080;\n")
            docker("exec", proxy, "nginx", "-t")
            docker("exec", proxy, "nginx", "-s", "reload")
            eventually(lambda: b"Deployment new" in request(public, "/deployment")[2], seconds=5)
            started = time.monotonic()
            docker("kill", "--signal=TERM", prefix + "-old")
            received, completed = reader.result(timeout=8)
            assert completed or host == "ktor", "Spring's reference profile must drain the bounded stream"
            exit_code = docker("wait", prefix + "-old", timeout=10)
            assert exit_code in ("0", "143"), "Graceful JVM SIGTERM must not rely on SIGKILL (137)"
            assert docker("inspect", "--format", "{{.State.OOMKilled}}", prefix + "-old") == "false"
            if host != "ktor":
                assert "Graceful shutdown complete" in docker("logs", prefix + "-old")
            shutdown_seconds = time.monotonic() - started
            assert shutdown_seconds < 10

        # Send exactly the old document's form, epoch and region revisions to the new process.
        action_headers = {
            "Content-Type": "application/x-www-form-urlencoded", "Origin": public, "Accept": MEDIA,
            "Woge-Page-Epoch": epoch, "Woge-Region-Revisions": target + ":0", "Woge-Interaction": "1",
        }
        status, response_headers, wire = request(public, action, action_headers, b"text=From+old+HTML")
        assert status == 200 and response_headers["Content-Type"].startswith(MEDIA.split(";")[0])
        frames = baseline_frames(wire)
        assert len(frames) == 2
        patch = frames[0][1]
        assert patch["epoch"] == epoch and patch["target"] == target
        assert patch["baseRevision"] == 0 and patch["nextRevision"] == 1 and patch["interactionSequence"] == 1
        assert frames[0][2] == "<p>From old HTML</p>"
        (work / "new-action.bin").write_bytes(wire)
        assert request(public, asset_path)[2] == old_asset
        for name, body in retained.items():
            assert request(public, name)[2] == body
        assert b"Deployment new" in request(public, "/deployment")[2]
        # Unsupported versions are rejected before execution; recovery is a GET, never POST replay.
        action_headers["Accept"] = MEDIA.replace("version=1", "version=999")
        status, native_headers, _ = request(public, action, action_headers, b"text=Native")
        assert status == 406 and native_headers["Woge-Protocol-Error"] == "unsupported-version"
        status, native_headers, _ = request(
            public, action, {"Origin": public, "Content-Type": "application/x-www-form-urlencoded"}, b"text=Native",
        )
        assert status == 303 and native_headers["Location"] == "/deployment"
        assert request(public, "/deployment", {"If-None-Match": '"old"'})[0] == 200
        logs = docker("logs", prefix + "-old") + docker("logs", prefix + "-new")
        assert "live.subscription" in logs and "LIVE_SUBSCRIPTIONS" in logs and "rejected" in logs
        assert "From old HTML" not in logs and secret not in logs
        evidence = {
            "host": host, "engine": docker("info", "--format", "{{.ServerVersion}}"),
            "retained_client_sha256": hashlib.sha256(old_asset).hexdigest(),
            "old_html_new_action_v1": True, "asset_retained": True, "private_health": True,
            "live_exhaustion_observed": True, "stream_shutdown": "drained" if completed else "terminated",
            "shutdown_exit": int(exit_code), "shutdown_seconds": shutdown_seconds,
        }
        (REPORT / f"deployment-{host}.json").write_text(json.dumps(evidence, indent=2) + "\n")
        print(json.dumps(evidence), flush=True)
    finally:
        for name in containers:
            result = subprocess.run(
                ["docker", "logs", name], capture_output=True, text=True, timeout=10,
            )
            (work / f"{name}.log").write_text(result.stdout + result.stderr)
            docker("rm", "-f", name)
        if network_created:
            docker("network", "rm", prefix)
        if subprocess.run(["docker", "image", "inspect", image], capture_output=True, timeout=10).returncode == 0:
            docker("image", "rm", image)


def main():
    REPORT.mkdir(parents=True, exist_ok=True)
    try:
        version = docker("info", "--format", "{{.ServerVersion}}", timeout=10)
    except (OSError, subprocess.SubprocessError) as error:
        raise SystemExit(f"BLOCKED: a running Docker engine is required; no container evidence: {error}") from error
    print("Docker engine " + version, flush=True)
    golden = ROOT / "modules/woge-protocol/src/test/resources/fixtures/patch-stream-v1.hex"
    baseline_frames(bytes.fromhex(golden.read_text().strip()))
    docker("pull", "nginx:1.28-alpine", timeout=120)
    docker("pull", "eclipse-temurin:21-jre", timeout=120)
    for host in ("spring-mvc", "spring-webflux", "ktor"):
        verify_host(host)


if __name__ == "__main__":
    def interrupted(signum, frame):
        raise SystemExit(f"Deployment verification interrupted by signal {signum}")

    signal.signal(signal.SIGTERM, interrupted)
    main()
