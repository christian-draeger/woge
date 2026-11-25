#!/usr/bin/env python3
"""Exercise the opt-in PetClinic cluster on loopback with a disposable PostgreSQL container."""

import argparse
import base64
import concurrent.futures
import contextlib
import datetime
import http.client
import http.cookiejar
import io
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile
from html.parser import HTMLParser

from tls_ingress import start_ingress

POSTGRES_IMAGE = "postgres@sha256:d3e1620b530c944afa6e887d22eb899824da68e19c52024bf98f5220c88a65b2"
UPSTREAM = "500158f732419217507c7656904b8e6aa1bcc0d6"
LIVE = "/woge/cluster/owners/1"
NATIVE_VISIT = "/owners/1/pets/1/visits/new"
FOLLOW_UP = LIVE + "/follow-up"
PATCH = "application/vnd.woge.patch-stream; version=1"
USERNAME = "reference"


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def command(*arguments, **options):
    return subprocess.run(arguments, check=True, text=True, capture_output=True, **options).stdout.strip()


def await_value(check, failure, timeout=45):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        value = check()
        if value:
            return value
        time.sleep(0.2)
    raise RuntimeError(failure)


def psql(container, sql):
    return command("docker", "exec", "-i", container, "psql", "-qAt", "-v", "ON_ERROR_STOP=1",
                   "-U", "petclinic", "-d", "petclinic", input=sql, timeout=10)


def start_postgres(cleanup, upstream, jar):
    container = "woge-petclinic-" + secrets.token_hex(6)
    password = secrets.token_urlsafe(24)
    command("docker", "run", "-d", "--rm", "--pull=never", "--name", container,
            "-p", "127.0.0.1::5432", "-e", "POSTGRES_USER=petclinic", "-e", "POSTGRES_DB=petclinic",
            "-e", "POSTGRES_PASSWORD", POSTGRES_IMAGE,
            env={**os.environ, "POSTGRES_PASSWORD": password}, timeout=45)
    cleanup.callback(subprocess.run, ("docker", "rm", "-f", container), capture_output=True, timeout=10)

    def ready():
        return subprocess.run(("docker", "exec", container, "pg_isready", "-q", "-h", "127.0.0.1",
                               "-U", "petclinic", "-d", "petclinic"), timeout=5).returncode == 0

    await_value(ready, "Disposable PostgreSQL did not become ready")
    schema = upstream / "src/main/resources/db/postgres"
    with zipfile.ZipFile(jar) as archive:
        session = next(name for name in archive.namelist() if name.startswith("BOOT-INF/lib/spring-session-jdbc-"))
        with zipfile.ZipFile(io.BytesIO(archive.read(session))) as session_jar:
            session_schema = session_jar.read("org/springframework/session/jdbc/schema-postgresql.sql").decode()
    psql(container, "\n".join([
        (schema / "schema.sql").read_text(), (schema / "data.sql").read_text(), session_schema,
        Path(__file__).with_name("visits.sql").read_text(),
    ]))
    port = command("docker", "port", container, "5432/tcp", timeout=5).rsplit(":", 1)[1]
    return container, port, password


def start_node(cleanup, directory, jar, node, env):
    log = directory / f"{node}.log"
    with log.open("w") as output:
        process = subprocess.Popen([
            str(Path(os.environ["JAVA_HOME"]) / "bin/java") if "JAVA_HOME" in os.environ else "java",
            "-jar", str(jar), "--server.address=127.0.0.1", "--server.port=0",
            "--spring.profiles.active=postgres,woge-cluster", "--spring.sql.init.mode=never",
            "--spring.datasource.hikari.maximum-pool-size=4",
            "--server.servlet.session.tracking-modes=cookie",
        ], stdout=output, stderr=subprocess.STDOUT, env=env)

    def stop():
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)

    cleanup.callback(stop)

    def port():
        require(process.poll() is None, f"Node {node} exited during startup")
        messages = log.read_text(errors="replace")
        match = re.search(r"Tomcat started on port (\d+)", messages)
        return int(match[1]) if match and "Started PetClinicApplication" in messages else None

    return await_value(port, f"Node {node} did not start")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        return None


class Page(HTMLParser):
    def __init__(self, body):
        super().__init__()
        self.csrf = None
        self.fields = {}
        self.region = {}
        self.render_epoch = None
        self.render_region = None
        self.feed(body.decode())

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if tag == "input" and "name" in values:
            self.fields[values["name"]] = values.get("value", "")
            if values["name"] == "_csrf":
                self.csrf = values["value"]
        if values.get("id") == "clinic-live-visits":
            self.region = values
        if tag == "meta" and values.get("name") == "woge-page-epoch":
            self.render_epoch = values["content"]
        if "data-woge-region" in values and self.render_region is None:
            self.render_region = values["data-woge-region"]

    @property
    def live_path(self):
        return self.region["data-woge-live-url"] + "?" + urllib.parse.urlencode({
            "_woge_live": self.region["data-woge-live-epoch"],
            "_woge_protocol_version": "1",
        })

    def refresh_path(self, revision, interaction):
        return self.region["data-woge-live-url"] + "?" + urllib.parse.urlencode({
            "_woge_refresh": "1",
            "_woge_epoch": self.region["data-woge-live-epoch"],
            "_woge_target": self.region["data-woge-region"],
            "_woge_revision": revision,
            "_woge_interaction": interaction,
        })


def request(browser, port, path, status, fields=None, headers=None):
    query = urllib.request.Request(
        f"http://127.0.0.1:{port}{path}",
        data=urllib.parse.urlencode(fields).encode() if fields is not None else None,
        headers={"Accept": "text/html", "Accept-Language": "en", **(headers or {})},
    )
    try:
        response = browser.open(query, timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        destination = urllib.parse.urlsplit(response.headers.get("Location", "")).path.split(";")[0]
        require(response.status == status, f"{query.get_method()} {path.split('?')[0]}: "
                                          f"expected {status}, got {response.status} ({destination})")
        return response.headers, response.read(512 * 1024)


@contextlib.contextmanager
def event_stream(cookies, port, path, last_id=None):
    url = f"http://127.0.0.1:{port}{path}"
    query = urllib.request.Request(url)
    cookies.add_cookie_header(query)
    headers = {"Accept": "text/event-stream", "Cookie": query.get_header("Cookie")}
    if last_id is not None:
        headers["Last-Event-ID"] = last_id
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
    try:
        connection.request("GET", path, headers=headers)
        with connection.getresponse() as response:
            require(response.status == 200 and response.headers.get_content_type() == "text/event-stream",
                    f"Node on port {port} did not accept the authorized SSE request: {response.status}")
            yield response
    finally:
        connection.close()


def next_event(stream, name, region):
    fields = {}
    deadline = time.monotonic() + 5
    while time.monotonic() < deadline:
        line = stream.readline(1024).decode()
        require(line, "SSE stream closed")
        if line == "\n":
            if "event" in fields:
                require(fields.get("event") == name and fields.get("data") == region,
                        f"SSE did not return the expected {name} for its single declared target")
                return fields["id"]
            fields.clear()
        elif ": " in line:
            key, value = line.rstrip("\n").split(": ", 1)
            fields[key] = value
    raise RuntimeError(f"SSE did not produce {name} within the local test budget")


def post_follow_up(port, cookie, fields, discard_response=False):
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=10)
    try:
        connection.request("POST", FOLLOW_UP, body=urllib.parse.urlencode(fields), headers={
            "Content-Type": "application/x-www-form-urlencoded", "Cookie": cookie,
        })
        if not discard_response:
            with connection.getresponse() as response:
                response.read(128 * 1024)
                if response.status == 303:
                    require(response.headers.get("Location") == FOLLOW_UP, "Follow-up POST did not redirect to a safe GET")
                return response.status
    finally:
        connection.close()


def race_follow_up(cookie, operations):
    ready = threading.Barrier(len(operations))

    def send(operation):
        ready.wait(timeout=5)
        return post_follow_up(operation[0], cookie, operation[1])

    with concurrent.futures.ThreadPoolExecutor(max_workers=len(operations)) as pool:
        return list(pool.map(send, operations))


def exercise_follow_up(container, browser, cookie, a, b, date):
    def form(port):
        return Page(request(browser, port, FOLLOW_UP, 200)[1]).fields

    def state_matches(port, revision, date_value):
        view = form(port)
        return view["expectedRevision"] == str(revision) and view["followUpOn"] == date_value

    same = form(a)
    same["followUpOn"] = date.isoformat()
    require(same["expectedRevision"] == "0", "Follow-up fixture must start at revision zero")
    without_csrf = {name: value for name, value in same.items() if name != "_csrf"}
    require(post_follow_up(b, cookie, without_csrf) == 403, "Follow-up must enforce Spring CSRF")
    require(race_follow_up(cookie, [(a, same), (b, same)]) == [303, 303],
            "Duplicate submissions to both nodes must share one durable result")
    require(state_matches(b, 1, same["followUpOn"]), "Duplicate requests advanced the revision more than once")
    changed_replay = {**same, "followUpOn": (date + datetime.timedelta(days=1)).isoformat()}
    require(post_follow_up(b, cookie, changed_replay) == 409, "A used identity accepted different command data")

    competitors = [form(a), form(b)]
    for offset, fields in enumerate(competitors, start=1):
        fields["followUpOn"] = (date + datetime.timedelta(days=offset)).isoformat()
    outcomes = race_follow_up(cookie, list(zip((a, b), competitors)))
    require(sorted(outcomes) == [303, 409], f"Competing writes at the same revision returned {outcomes}")
    winner = competitors[outcomes.index(303)]
    stale = competitors[outcomes.index(409)]
    require(state_matches(a, 2, winner["followUpOn"]), "A stale request overwrote the winning date")
    require(post_follow_up(b, cookie, same) == 303 and state_matches(a, 2, winner["followUpOn"]),
            "Replaying an earlier receipt overwrote a newer date")

    lost = form(a)
    lost["followUpOn"] = (date + datetime.timedelta(days=3)).isoformat()
    post_follow_up(a, cookie, lost, discard_response=True)
    await_value(lambda: state_matches(b, 3, lost["followUpOn"]),
                "Safe GET on B did not observe the write whose response was dropped", timeout=5)
    require(post_follow_up(b, cookie, lost) == 303, "Deliberate retry on B did not resolve the stored receipt")
    require(state_matches(a, 3, lost["followUpOn"]), "Retry after a lost response produced another mutation")
    require(psql(container, "SELECT count(*) FROM clinic_follow_up_receipt;") == "3",
            "Expected one durable receipt per committed revision")
    stale_id = uuid.UUID(stale["requestId"])
    require(psql(container, f"SELECT count(*) FROM clinic_follow_up_receipt WHERE request_id = '{stale_id}';") == "0",
            "The stale command did not roll back its reservation")
    print("Follow-up writes: one commit for simultaneous duplicate IDs, one winner at a shared revision, "
          "safe peer GET before retry after a discarded response")
    return lost


def exercise(container, a, b, password):
    cookies = http.cookiejar.CookieJar()
    browser = urllib.request.build_opener(
        NoRedirect(), urllib.request.ProxyHandler({}), urllib.request.HTTPCookieProcessor(cookies),
    )
    anonymous = urllib.request.build_opener(NoRedirect(), urllib.request.ProxyHandler({}))
    login = Page(request(browser, a, "/login", 200)[1])
    request(browser, a, "/login", 302, fields={
        "username": USERNAME, "password": password, "_csrf": login.csrf,
    })
    page = Page(request(browser, a, LIVE, 200)[1])
    request(browser, b, LIVE, 200)
    request(anonymous, a, LIVE, 302)
    request(anonymous, b, page.live_path, 302)
    request(anonymous, b, page.refresh_path(0, 1), 302, headers={"Accept": PATCH})
    request(browser, b, "/woge/cluster/owners/999999", 404)

    listen_count = psql(container, "SELECT count(*) FROM pg_stat_activity "
                                  "WHERE datname = current_database() AND query = 'LISTEN woge_petclinic_visits';")
    require(listen_count == "2", f"Expected one PostgreSQL listener per node, found {listen_count}")
    date = datetime.date.today() + datetime.timedelta(days=7)
    csrf = Page(request(browser, a, NATIVE_VISIT, 200)[1]).csrf
    request(browser, b, NATIVE_VISIT, 403, fields={"date": date.isoformat(), "description": "Rejected visit"})
    require(psql(container, "SELECT count(*) FROM visits WHERE description = 'Rejected visit';") == "0",
            "A request without CSRF unexpectedly saved a visit")
    description = "Native visit committed on node B"
    region = page.region["data-woge-region"]
    with event_stream(cookies, a, page.live_path) as stream:
        next_event(stream, "invalidate", region)
        began = time.monotonic()
        request(browser, b, NATIVE_VISIT, 302,
                fields={"date": date.isoformat(), "description": description, "_csrf": csrf})
        last_id = next_event(stream, "invalidate", region)
        notified = time.monotonic()
        headers, patch = request(browser, b, page.refresh_path(0, 1), 200, headers={"Accept": PATCH})
        require(headers.get_content_type() == "application/vnd.woge.patch-stream"
                and description.encode() in patch and region.encode() in patch,
                "Node B did not refresh the latest visit into the render target issued by node A")
        refreshed = time.monotonic()
        print(f"Native write via B → SSE on A: {(notified - began) * 1000:.1f} ms")
        print(f"Native write via B → patch via B using A identity: {(refreshed - began) * 1000:.1f} ms")

    external = "Visit committed directly in PostgreSQL while disconnected"
    psql(container, f"INSERT INTO visits (pet_id, visit_date, description) VALUES (1, '{date}', '{external}');")
    with event_stream(cookies, b, page.live_path, last_id) as stream:
        next_event(stream, "resync", region)
        _, patch = request(browser, b, page.refresh_path(1, 2), 200, headers={"Accept": PATCH})
        require(description.encode() in patch and external.encode() in patch,
                "Reconnect on node B did not recover authoritative rows after disconnect")
    require(external.encode() in request(browser, a, LIVE, 200)[1], "Native HTML did not recover the latest data")
    recovered = "Visit committed during listener recovery"
    with event_stream(cookies, a, page.live_path) as stream:
        next_event(stream, "invalidate", region)
        began = time.monotonic()
        terminated = psql(container, "SELECT pg_terminate_backend(pid, 1000) FROM pg_stat_activity "
                                     "WHERE application_name = 'woge-petclinic-a' "
                                     "AND query = 'LISTEN woge_petclinic_visits';")
        require(terminated == "t", "Failed to disconnect the dedicated listener on node A")
        psql(container, f"INSERT INTO visits (pet_id, visit_date, description) VALUES (1, '{date}', '{recovered}');")
        next_event(stream, "invalidate", region)
        _, patch = request(browser, a, page.refresh_path(2, 3), 200, headers={"Accept": PATCH})
        require(recovered.encode() in patch, "Node A did not recover the visit after its database listener restarted")
        print(f"Database listener disconnect → recovery patch: {(time.monotonic() - began) * 1000:.1f} ms")

    identity = urllib.request.Request(f"http://127.0.0.1:{a}{FOLLOW_UP}")
    cookies.add_cookie_header(identity)
    cookie = identity.get_header("Cookie")
    follow_up_receipt = exercise_follow_up(container, browser, cookie, a, b, date)
    psql(container, "DELETE FROM clinic_live_owner_access WHERE username = 'reference' AND owner_id = 1;")
    request(browser, b, FOLLOW_UP, 404)
    require(post_follow_up(b, cookie, follow_up_receipt) == 404,
            "A previous successful receipt bypassed the revoked owner grant")
    request(browser, a, LIVE, 404)
    request(browser, b, page.live_path, 404)
    request(browser, b, page.refresh_path(3, 4), 404, headers={"Accept": PATCH})
    psql(container, "INSERT INTO clinic_live_owner_access (username, owner_id) VALUES ('reference', 1);")
    request(browser, a, LIVE, 200)
    saved = urllib.request.Request(f"http://127.0.0.1:{a}{LIVE}")
    cookies.add_cookie_header(saved)
    session = {"Cookie": saved.get_header("Cookie")}
    request(anonymous, a, LIVE, 200, headers=session)
    request(browser, b, "/logout", 302, fields={"_csrf": csrf})
    request(anonymous, a, LIVE, 302, headers=session)
    request(anonymous, a, page.live_path, 302, headers=session)
    request(anonymous, a, page.refresh_path(3, 4), 302, headers={**session, "Accept": PATCH})
    login = Page(request(browser, b, "/login", 200)[1])
    request(browser, b, "/login", 302, fields={
        "username": USERNAME, "password": password, "_csrf": login.csrf,
    })
    saved = urllib.request.Request(f"http://127.0.0.1:{a}{LIVE}")
    cookies.add_cookie_header(saved)
    session = {"Cookie": saved.get_header("Cookie")}
    request(anonymous, a, LIVE, 200, headers=session)
    expired = psql(container, "UPDATE spring_session SET last_access_time = 0, expiry_time = 0 "
                              "WHERE principal_name = 'reference' RETURNING 1;")
    require(expired == "1", "Expected a single recorded session to expire")
    request(anonymous, b, page.live_path, 302, headers=session)
    request(anonymous, a, page.refresh_path(3, 4), 302, headers={**session, "Accept": PATCH})

    print("Two PostgreSQL listeners; shared session and CSRF; anonymous and missing owner denial; "
          "external write recovered on peer reconnect; owner grant revocation, logout and session expiry enforced")


def exercise_tls_ingress(ingress, password):
    tls_version = ingress.wait_for_tls()
    ingress.require_untrusted_ca_rejection()
    login = Page(ingress.request("a", "/login", 200)[1])
    signed_in, _ = ingress.request("a", "/login", 302, fields={
        "username": USERNAME, "password": password, "_csrf": login.csrf,
    })
    require(any(cookie.startswith("SESSION=") and "secure" in cookie.lower().split("; ")
                for cookie in signed_in.get_all("Set-Cookie", [])),
            "Login through nginx did not issue a Secure Spring Session cookie")
    live = Page(ingress.request("a", LIVE, 200)[1])
    ingress.request("b", LIVE, 200)
    ingress.request("b", live.live_path, 302, with_session=False)
    form = Page(ingress.request("a", "/woge" + NATIVE_VISIT, 200)[1])
    fields = {
        "date": (datetime.date.today() + datetime.timedelta(days=7)).isoformat(),
        "description": "Visit booked over the HTTP/2 ingress",
        "_csrf": form.csrf,
    }
    common = {
        "Accept": PATCH, "Woge-Page-Epoch": form.render_epoch,
        "Woge-Interaction": "1", "Woge-Region-Revisions": f"{form.render_region}:0",
        # Incoming forwarding data must never define the public authority.
        "Forwarded": "proto=https;host=foreign.invalid",
        "X-Forwarded-Host": "foreign.invalid",
        "X-Forwarded-Port": "443",
    }
    ingress.request("b", NATIVE_VISIT, 403, headers={
        **common, "Origin": "https://foreign.invalid", "Woge-Request-Identity": str(uuid.uuid4()),
    }, fields={**fields, "description": "Rejected foreign ingress visit"})
    ingress.request("b", NATIVE_VISIT, 403, headers={
        **common, "Origin": ingress.origin, "Woge-Request-Identity": str(uuid.uuid4()),
    }, fields={name: value for name, value in fields.items() if name != "_csrf"})

    began = time.monotonic()
    region = live.region["data-woge-region"]
    with ingress.stream("a", live.live_path) as (stream, process):
        next_event(stream, "invalidate", region)
        first = time.monotonic()
        posted, _ = ingress.request("b", NATIVE_VISIT, 200, headers={
            **common, "Origin": ingress.origin, "Woge-Request-Identity": str(uuid.uuid4()),
        }, fields=fields)
        require(posted.get("Woge-Navigate") == "/owners/1", "Enhanced ingress POST did not navigate to the owner")
        next_event(stream, "invalidate", region)
        changed = time.monotonic()
        require(process.poll() is None, "Both events must flush before the HTTP/2 SSE response finishes")
        headers, patch = ingress.request("b", live.refresh_path(0, 1), 200, headers={"Accept": PATCH})
        require(headers.get_content_type() == "application/vnd.woge.patch-stream"
                and fields["description"].encode() in patch and b"Rejected foreign ingress visit" not in patch,
                "Peer refresh behind nginx did not reconcile the committed visit and denied origin")
    print(f"nginx {tls_version}/HTTP/2: distinct routed nodes, shared Secure session and CSRF, forwarded origin "
          f"enforced; first SSE {(first - began) * 1000:.1f} ms, POST to peer SSE {(changed - first) * 1000:.1f} ms")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path)
    parser.add_argument("upstream", type=Path)
    parser.add_argument("--tls-ingress", action="store_true", help="also verify nginx TLS and HTTP/2 ingress")
    args = parser.parse_args()
    jar, upstream = args.jar.resolve(), args.upstream.resolve()
    require(command("git", "-C", str(upstream), "rev-parse", "HEAD") == UPSTREAM
            and not command("git", "-C", str(upstream), "status", "--porcelain"),
            "Use the clean pinned PetClinic checkout documented in README.md")

    with tempfile.TemporaryDirectory(prefix="woge-petclinic-cluster-") as temp:
        directory = Path(temp)
        try:
            with contextlib.ExitStack() as cleanup:
                container, db_port, db_password = start_postgres(cleanup, upstream, jar)
                password = secrets.token_urlsafe(24)
                env = {**os.environ, "SPRING_DATASOURCE_USERNAME": "petclinic",
                       "SPRING_DATASOURCE_PASSWORD": db_password, "SPRING_SECURITY_USER_NAME": USERNAME,
                       "SPRING_SECURITY_USER_PASSWORD": password, "SPRING_APPLICATION_JSON": json.dumps({
                           "woge": {"render-identity": {"secret": base64.b64encode(secrets.token_bytes(32)).decode()}},
                       })}
                if args.tls_ingress:
                    env["SERVER_FORWARD_HEADERS_STRATEGY"] = "framework"
                a, b = [
                    start_node(cleanup, directory, jar, node, {
                        **env, "SPRING_DATASOURCE_URL":
                            f"jdbc:postgresql://127.0.0.1:{db_port}/petclinic?ApplicationName=woge-petclinic-{node}",
                    })
                    for node in ("a", "b")
                ]
                require(a != b, "Expected two independent loopback listeners")
                exercise(container, a, b, password)
                if args.tls_ingress:
                    with start_ingress(directory, a, b) as ingress:
                        exercise_tls_ingress(ingress, password)
        except Exception:
            for log in sorted(directory.glob("*.log")):
                print(f"\nLast lines from {log.name}:\n" + "\n".join(log.read_text().splitlines()[-28:]))
            raise


if __name__ == "__main__":
    main()
