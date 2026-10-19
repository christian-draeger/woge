#!/usr/bin/env python3
"""Informational closed-loop comparison. Never used as a noisy PR throughput gate."""
import argparse
import concurrent.futures
import http.client
import hashlib
import json
import math
import os
import pathlib
import platform
import re
import resource
import socket
import subprocess
import time
import uuid

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--samples", type=int, default=300)
parser.add_argument("--concurrency", type=int, default=4)
parser.add_argument("--rounds", type=int, default=3)
parser.add_argument("--browser-bytes", action="store_true", help="Also record actual Chromium resource entries using the pinned Docker image")
parser.add_argument("--browser-journeys", action="store_true", help="Run identical native/enhanced consumer journeys after measurements")
parser.add_argument("--container-image", help="Run each server in this locally built image with 2 CPUs and 768 MiB")
parser.add_argument("--skrape-probe", action="store_true", help="Verify published skrape parser assertions on each host")
args = parser.parse_args()
assert args.samples > 0 and args.concurrency > 0 and args.rounds > 0
root = pathlib.Path(__file__).resolve().parent
evidence = root / "build/evidence"
evidence.mkdir(parents=True, exist_ok=True)
result = {"platform": platform.platform(), "machine": platform.machine(), "processor": platform.processor(),
          "java": subprocess.run([os.environ.get("JAVA_HOME", "") + "/bin/java", "-version"], capture_output=True, text=True).stderr,
          "limits": "No CPU/container isolation; JAVA_OPTS=-Xms128m -Xmx512m -XX:+UseG1GC", "load": "closed loop, one keep-alive connection per worker; no coordinated-omission correction",
          "samples": args.samples, "concurrency": args.concurrency, "rounds": args.rounds, "runs": []}
result["sourceRevision"] = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
result["candidateWorkingTree"] = True
sources = list((root / "src").rglob("*")) + list((root.parents[1] / "examples/todo-spring-boot/src/main/kotlin").rglob("*.kt"))
result["sourceSha256"] = {str(p.relative_to(root.parents[1])): hashlib.sha256(p.read_bytes()).hexdigest()
                          for p in sorted(sources) if p.is_file()}
result["versions"] = {"woge": "0.1.0-SNAPSHOT (local candidate)", "kotlin": "2.4.10", "ksp": "2.3.12", "springBoot": "4.1.1", "ktor": "3.5.2", "thymeleaf": "3.1.5.RELEASE", "kotlinxHtml": "0.12.0", "htmx": "2.0.11", "playwright": "1.63.0"}
if args.container_image:
    metadata = json.loads(subprocess.check_output(["docker", "image", "inspect", args.container_image], text=True))[0]
    result["container"] = {"imageId": metadata["Id"], "architecture": metadata["Architecture"], "cpus": 2, "memoryBytes": 768 * 1024 * 1024}
    result["limits"] = "Each server: 2 CPUs, 768 MiB container; JAVA_OPTS=-Xms128m -Xmx512m -XX:+UseG1GC"

def percentile(values, fraction):
    return sorted(values)[max(0, math.ceil(len(values) * fraction) - 1)]

def request(port, method, path, body=None, headers=None):
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=10)
    try:
        connection.request(method, path, body, headers or {})
        response = connection.getresponse()
        return response.status, dict(response.getheaders()), response.read()
    finally:
        connection.close()

def workload(port, case, initial, measured):
    origin = "http://127.0.0.1:" + str(port)
    headers = {"Origin": origin, "Content-Type": "application/x-www-form-urlencoded"}
    body = "title=+++&filter=all" if case == "validation" else "id=1&filter=all"
    expected = 400 if case == "validation" else 303
    method, path = ("GET", "/") if case == "initial" else ("POST", "/woge-actions/" + ("add-todo" if case == "validation" else "toggle-todo"))
    if case == "enhanced-toggle":
        expected = 200
        if "woge-page-epoch" in initial:
            epoch = re.search(r'name="woge-page-epoch" content="([^"]+)"', initial)[1]
            headers |= {"Accept": "application/vnd.woge.patch-stream; version=1", "Woge-Page-Epoch": epoch, "Woge-Interaction": "1", "Woge-Request-Identity": str(uuid.uuid4())}
        else:
            headers["HX-Request"] = "true"
    if case == "initial":
        body, expected = None, 200
    latencies, errors, sizes = [], [], []
    def worker(count):
        connection = http.client.HTTPConnection("127.0.0.1", port, timeout=10)
        try:
            for _ in range(count):
                start = time.perf_counter_ns()
                connection.request(method, path, body, headers)
                response = connection.getresponse()
                data = response.read()
                latencies.append((time.perf_counter_ns() - start) / 1_000_000)
                sizes.append(len(data))
                if response.status != expected:
                    errors.append({"status": response.status, "body": data[:100].decode(errors="replace")})
        finally:
            connection.close()
    started = time.perf_counter()
    total = args.samples if measured else 100
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        list(pool.map(worker, [total // args.concurrency + (i < total % args.concurrency) for i in range(args.concurrency)]))
    elapsed = time.perf_counter() - started
    return {"case": case, "p50Ms": percentile(latencies, .5), "p95Ms": percentile(latencies, .95),
            "requestsPerSecond": len(latencies) / elapsed, "elapsedSeconds": elapsed, "errors": errors,
            "latenciesMs": latencies, "responseBytes": sorted(set(sizes))}

for round_number in range(args.rounds):
    # Rotate the order to expose rather than hide process warm-up / machine drift.
    modes = ["spring-woge", "spring-thymeleaf", "ktor-woge", "ktor-html"]
    modes = modes[round_number % 4:] + modes[:round_number % 4]
    for mode in modes:
        with socket.socket() as reservation:
            reservation.bind(("127.0.0.1", 0))
            port = reservation.getsockname()[1]
        env = dict(os.environ, PORT=str(port), BENCH_MODE=mode, JAVA_OPTS="-Xms128m -Xmx512m -XX:+UseG1GC")
        log_path = evidence / (mode + "-" + str(round_number) + ".log")
        before = resource.getrusage(resource.RUSAGE_CHILDREN)
        started = time.perf_counter()
        container = "woge-comparison-" + uuid.uuid4().hex[:12] if args.container_image else None
        launch = [str(root / "build/install/todo-framework-comparison/bin/todo-framework-comparison")]
        if container:
            launch = ["docker", "run", "--rm", "--name", container, "--cpus", "2", "--memory", "768m", "--pids-limit", "256", "--read-only", "--tmpfs", "/tmp:rw,nosuid,size=64m", "-p", "127.0.0.1:" + str(port) + ":8080", "-e", "BENCH_MODE=" + mode, "-e", "JAVA_OPTS=" + env["JAVA_OPTS"], args.container_image]
        with log_path.open("w") as log:
            process = subprocess.Popen(launch, env=env, stdout=log, stderr=log)
            try:
                while True:
                    try:
                        status, _, data = request(port, "GET", "/")
                        if status == 200:
                            break
                    except OSError:
                        pass
                    if process.poll() is not None or time.perf_counter() - started > 60:
                        raise RuntimeError("Startup failed: " + str(log_path))
                    time.sleep(.05)
                startup = time.perf_counter() - started
                initial = data.decode()
                if args.skrape_probe:
                    java = str(pathlib.Path(os.environ["JAVA_HOME"]) / "bin/java")
                    libraries = str(root.parent / "consumer-testing/build/install/woge-consumer-testing-spike/lib/*")
                    subprocess.run([java, "-cp", libraries, "experiment.SkrapeProbeKt"], env=dict(os.environ, WOGE_TODO_URL="http://127.0.0.1:" + str(port)), check=True, timeout=30)
                browser_bytes = None
                if args.browser_bytes:
                    probe = subprocess.run(["docker", "run", "--rm", "--mount", "type=bind,src=" + str(root) + ",dst=/work", "-w", "/work", "-e", "BENCH_URL=http://host.docker.internal:" + str(port), "mcr.microsoft.com/playwright:v1.63.0-noble", "node", "browser-bytes.mjs"], check=True, capture_output=True, text=True, timeout=30)
                    browser_bytes = json.loads(probe.stdout)
                    assert not [f for f in browser_bytes["failures"] if f["type"] not in ("fetch", "eventsource")], browser_bytes
                    assert not browser_bytes["httpFailures"] and not browser_bytes["pageErrors"], browser_bytes
                assert "Todo &lt;1&gt; &amp; Kotlin" in initial
                assert request(port, "POST", "/woge-actions/add-todo", "title=denied", {"Origin": "https://other.example", "Content-Type": "application/x-www-form-urlencoded"})[0] == 403
                cases = ["initial", "validation", "native-toggle", "enhanced-toggle"]
                for case in cases:
                    warmup = workload(port, case, initial, False)
                    assert not warmup["errors"], (mode, case, warmup["errors"][:1])
                measured = [workload(port, case, initial, True) for case in cases]
                for value in measured:
                    assert not value["errors"], value["errors"][:1]
                render = json.loads(request(port, "GET", "/bench-render")[2])
                if container:
                    status = subprocess.check_output(["docker", "exec", container, "cat", "/proc/1/status"], text=True)
                    rss = re.search(r"^VmRSS:\s+(\d+)", status, re.MULTILINE)[1]
                else:
                    rss = subprocess.run(["ps", "-o", "rss=", "-p", str(process.pid)], capture_output=True, text=True).stdout.strip()
                entry = {"mode": mode, "round": round_number, "startupSeconds": startup, "rssKiB": int(rss) if rss else None, "render": render, "browserBytes": browser_bytes, "workloads": measured}
                if container:
                    cpu_stat = subprocess.check_output(["docker", "exec", container, "cat", "/sys/fs/cgroup/cpu.stat"], text=True)
                    cpu = str(int(re.search(r"^usage_usec (\d+)", cpu_stat, re.MULTILINE)[1]) / 1_000_000) + " seconds (cgroup)"
                    config = json.loads(subprocess.check_output(["docker", "inspect", container], text=True))[0]["HostConfig"]
                    assert config["NanoCpus"] == 2_000_000_000 and config["Memory"] == 768 * 1024 * 1024
                else:
                    cpu = subprocess.run(["ps", "-o", "time=", "-p", str(process.pid)], capture_output=True, text=True).stdout.strip()
                entry["serverCpuTime"] = cpu
                if args.browser_journeys:
                    label = mode + "-" + str(round_number)
                    worker = "woge-benchmark-browser-" + str(port)
                    try:
                        subprocess.run(["docker", "run", "--rm", "--name", worker, "--mount", "type=bind,src=" + str(root) + ",dst=/work", "--mount", "type=bind,src=" + str(root.parents[1] / "examples/todo-spring-boot/browser-tests") + ",dst=/examples/todo-spring-boot/browser-tests,readonly", "-w", "/work", "-e", "BENCH_URL=http://host.docker.internal:" + str(port), "-e", "BENCH_LABEL=" + label, "mcr.microsoft.com/playwright:v1.63.0-noble", "node", "run-browser.mjs"], check=True, timeout=180)
                    finally:
                        subprocess.run(["docker", "rm", "-f", worker], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                    entry["browserJourneys"] = "browser-" + label + ".json"
                result["runs"].append(entry)
                print(mode, round_number, "startup", round(startup, 3), "p95", [round(x["p95Ms"], 3) for x in measured], flush=True)
            finally:
                process.terminate()
                try:
                    process.wait(timeout=30)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
                if container:
                    subprocess.run(["docker", "rm", "-f", container], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                after = resource.getrusage(resource.RUSAGE_CHILDREN)
                if result["runs"] and result["runs"][-1]["mode"] == mode:
                    result["runs"][-1]["allChildCpuSecondsIncludingProbe"] = (after.ru_utime + after.ru_stime) - (before.ru_utime + before.ru_stime)
                    result["runs"][-1]["exitCode"] = process.returncode
                (evidence / "raw.json").write_text(json.dumps(result, indent=2))
