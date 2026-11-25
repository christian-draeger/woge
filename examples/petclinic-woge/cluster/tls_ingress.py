"""Run the isolated PetClinic HTTP/2 ingress with a temporary CA and a pinned nginx image."""

import contextlib
from email import policy
from email.parser import BytesHeaderParser
import os
from pathlib import Path
import platform
import secrets
import select
import socket
import ssl
import subprocess
import time
from urllib.parse import urlencode

NGINX_IMAGE = "nginx@sha256:a8b39bd9cf0f83869a2162827a0caf6137ddf759d50a171451b335cecc87d236"


def create_certificates(directory, mounted):
    ca_key = str(directory / "ca.key")
    ca_cert = str(directory / "ca.pem")
    server_key = str(mounted / "server.key")
    server_request = str(directory / "server.csr")
    extensions = directory / "server.ext"
    extensions.write_text(
        "basicConstraints=critical,CA:FALSE\nextendedKeyUsage=serverAuth\n"
        "keyUsage=critical,digitalSignature,keyEncipherment\nsubjectAltName=IP:127.0.0.1\n"
    )
    commands = [
        ["req", "-x509", "-nodes", "-newkey", "rsa:2048", "-days", "1",
         "-subj", "/CN=Woge local PetClinic test CA", "-out", ca_cert, "-keyout", ca_key,
         "-addext", "basicConstraints=critical,CA:TRUE", "-addext", "keyUsage=critical,keyCertSign,cRLSign"],
        ["req", "-new", "-nodes", "-newkey", "rsa:2048", "-subj", "/CN=127.0.0.1",
         "-out", server_request, "-keyout", server_key],
        ["x509", "-req", "-days", "1", "-in", server_request, "-out", str(mounted / "server.pem"),
         "-CA", ca_cert, "-CAkey", ca_key, "-CAcreateserial", "-extfile", str(extensions)],
    ]
    for arguments in commands:
        subprocess.run(["openssl", *arguments], check=True, capture_output=True, timeout=10)
    return ca_cert


class StreamLines:
    def __init__(self, pipe):
        self.pipe = pipe
        self.buffer = bytearray()

    def readline(self, limit):
        deadline = time.monotonic() + 5
        while b"\n" not in self.buffer:
            remaining = max(0, deadline - time.monotonic())
            if not select.select([self.pipe], [], [], remaining)[0]:
                raise TimeoutError("Timed out waiting for an HTTP/2 SSE frame through nginx")
            chunk = os.read(self.pipe.fileno(), 4096)
            if not chunk:
                raise RuntimeError("curl stopped before the HTTP/2 SSE stream returned the expected frame")
            self.buffer.extend(chunk)
            if len(self.buffer) > 32 * 1024:
                raise RuntimeError("HTTP/2 stream exceeded the test reader buffer")
        length = self.buffer.index(b"\n") + 1
        if length > limit:
            raise RuntimeError("HTTP/2 stream exceeded the test line limit")
        line = bytes(self.buffer[:length])
        del self.buffer[:length]
        return line


class TlsIngress:
    def __init__(self, directory, port, ca_cert, node_ports):
        self.origin = f"https://127.0.0.1:{port}"
        self.port = port
        self.node_ports = node_ports
        self.ca_cert = ca_cert
        self.directory = directory
        self.cookies = directory / "ingress-cookies.txt"
        self.cookies.touch(mode=0o600)

    def wait_for_tls(self):
        context = ssl.create_default_context(cafile=self.ca_cert)
        context.set_alpn_protocols(["h2"])
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            try:
                with socket.create_connection(("127.0.0.1", self.port), timeout=1) as connection:
                    with context.wrap_socket(connection, server_hostname="127.0.0.1") as tls:
                        if tls.selected_alpn_protocol() != "h2":
                            raise RuntimeError("nginx did not negotiate h2 with ALPN")
                        return tls.version()
            except (ConnectionRefusedError, TimeoutError, ssl.SSLEOFError):
                time.sleep(0.2)
        raise RuntimeError("The isolated nginx TLS listener did not become ready")

    def require_untrusted_ca_rejection(self):
        context = ssl.create_default_context()
        with socket.create_connection(("127.0.0.1", self.port), timeout=2) as connection:
            try:
                with context.wrap_socket(connection, server_hostname="127.0.0.1"):
                    raise RuntimeError("The temporary test CA unexpectedly appeared in the system trust store")
            except ssl.SSLCertVerificationError:
                pass

    def request_args(self, node, path, headers, with_session):
        arguments = [
            "curl", "--disable", "--silent", "--show-error", "--http2", "--noproxy", "*",
            "--cacert", self.ca_cert, "--proto", "=https",
            "--header", f"X-Woge-Fixture-Node: {node}", "--header", "Accept-Language: en",
        ]
        if with_session:
            arguments.extend(["--cookie", str(self.cookies)])
        for name, value in headers.items():
            arguments.extend(["--header", f"{name}: {value}"])
        return [*arguments, self.origin + path]

    def verify_headers(self, raw, node, status):
        start, _, rest = raw.partition(b"\n")
        if start.split()[:2] != [b"HTTP/2", str(status).encode()]:
            raise RuntimeError(f"Expected HTTP/2 {status} for upstream {node}, got {start.rstrip().decode()}")
        headers = BytesHeaderParser(policy=policy.HTTP).parsebytes(rest)
        upstream = headers.get("X-Woge-Fixture-Upstream", "")
        if not upstream.endswith(f":{self.node_ports[node]}"):
            raise RuntimeError(f"Expected JVM {node} on port {self.node_ports[node]}, nginx reached {upstream!r}")
        return headers

    def request(self, node, path, status, headers=None, fields=None, with_session=True):
        head = self.directory / "ingress-response-head"
        arguments = self.request_args(node, path, {"Accept": "text/html", **(headers or {})}, with_session)
        arguments.extend(["--max-time", "10", "--dump-header", str(head)])
        if with_session:
            arguments.extend(["--cookie-jar", str(self.cookies)])
        body = None
        if fields is not None:
            arguments.extend(["--data-binary", "@-"])
            body = urlencode(fields).encode()
        response = subprocess.run(arguments, input=body, capture_output=True, check=True, timeout=12)
        return self.verify_headers(head.read_bytes(), node, status), response.stdout

    @contextlib.contextmanager
    def stream(self, node, path):
        arguments = self.request_args(node, path, {"Accept": "text/event-stream"}, with_session=True)
        with subprocess.Popen([*arguments, "--include", "--no-buffer", "--max-time", "20"],
                              stdout=subprocess.PIPE, stderr=subprocess.DEVNULL) as process:
            try:
                stream = StreamLines(process.stdout)
                head = bytearray()
                while True:
                    line = stream.readline(2048)
                    head.extend(line)
                    if line == b"\r\n":
                        break
                    if len(head) > 16 * 1024:
                        raise RuntimeError("HTTP/2 SSE response exceeded the test header limit")
                headers = self.verify_headers(bytes(head), node, 200)
                if headers.get_content_type() != "text/event-stream":
                    raise RuntimeError("HTTP/2 ingress did not serve SSE")
                yield stream, process
            finally:
                if process.poll() is None:
                    process.terminate()
                process.wait(timeout=3)


@contextlib.contextmanager
def start_ingress(directory, node_a, node_b):
    supported = platform.system()
    if supported not in ("Linux", "Darwin"):
        raise RuntimeError("The TLS ingress proof uses Linux Docker or Docker Desktop on macOS")
    with socket.socket() as allocator:
        allocator.bind(("127.0.0.1", 0))
        port = allocator.getsockname()[1]
    mounted = directory / "nginx"
    mounted.mkdir()
    ca_cert = create_certificates(directory, mounted)
    linux = supported == "Linux"
    substitutions = {
        "UPSTREAM_HOST": "127.0.0.1" if linux else "host.docker.internal",
        "NODE_A": str(node_a), "NODE_B": str(node_b),
        "LISTEN": f"127.0.0.1:{port}" if linux else "8443",
        "PUBLIC_PORT": str(port),
    }
    config = Path(__file__).with_name("nginx-tls.conf").read_text()
    for marker, value in substitutions.items():
        config = config.replace(f"@{marker}@", value)
    (mounted / "nginx.conf").write_text(config)
    network = ["--network", "host"] if linux else ["-p", f"127.0.0.1:{port}:8443"]
    name = "woge-petclinic-ingress-" + secrets.token_hex(6)
    arguments = [
        "docker", "run", "-d", "--rm", "--pull=never", "--name", name, *network,
        "--read-only", "--tmpfs", "/var/cache/nginx", "--tmpfs", "/var/run",
        "--mount", f"type=bind,src={mounted.resolve()},dst=/woge-ingress,readonly",
        "--entrypoint", "nginx", NGINX_IMAGE, "-c", "/woge-ingress/nginx.conf", "-g", "daemon off;",
    ]
    subprocess.run(arguments, check=True, capture_output=True, timeout=15)
    try:
        yield TlsIngress(directory, port, ca_cert, {"a": node_a, "b": node_b})
    except Exception:
        subprocess.run(["docker", "logs", name], timeout=5)
        raise
    finally:
        subprocess.run(["docker", "rm", "-f", name], capture_output=True, timeout=10)
