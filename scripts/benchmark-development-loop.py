#!/usr/bin/env python3
"""Opt-in real save-to-HTTP feedback probe; no browser or Node dependency."""
import argparse
import json
import math
import platform
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def rpc(connection, method, params):
    request = urllib.request.Request(
        connection["url"],
        json.dumps({"jsonrpc": "2.0", "id": 1, "method": method, "params": params}).encode(),
        {**connection["headers"], "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=190) as response:
        message = json.load(response)
    if "error" in message:
        raise RuntimeError(message["error"]["message"])
    return message["result"]


def tool(connection, name, arguments=None):
    result = rpc(connection, "tools/call", {"name": name, "arguments": arguments or {}})
    if result.get("isError"):
        raise RuntimeError(f"{name} failed: {result['structuredContent']}")
    return result["structuredContent"]


def read_url(port, path):
    try:
        with urllib.request.urlopen(f"http://127.0.0.1:{port}{path}", timeout=2) as response:
            return response.read().decode()
    except (urllib.error.URLError, TimeoutError):
        return ""


def measure(connection, port, edit, path, expected):
    before = tool(connection, "status")
    start = time.monotonic()
    edit()
    deadline = start + 180
    while time.monotonic() < deadline:
        status = tool(connection, "status")
        if (status["phase"] == "READY"
                and (status["lastSuccessfulBuild"] or 0) > (before["latestRequestedBuild"] or 0)
                and expected in read_url(port, path)):
            return {
                "milliseconds": round((time.monotonic() - start) * 1000, 2),
                "buildId": status["lastSuccessfulBuild"],
                "generationId": status["activeServerGeneration"],
                "previousGenerationId": before["activeServerGeneration"],
            }
        time.sleep(0.1)
    raise RuntimeError(f"Feedback timeout for {path}: {status}")


def percentile(samples, quantile):
    values = sorted(sample["milliseconds"] for sample in samples)
    return values[math.ceil(len(values) * quantile) - 1]


def compare_results(results, policy, baseline, update_baseline=False):
    violations = []
    for kind, result in results.items():
        for metric in ("p50Ms", "p95Ms"):
            limit = policy["ceilingsMs"][kind][metric]
            if not update_baseline:
                limit = min(limit, baseline["results"][kind][metric] * (1 + policy["toleranceFraction"])
                            + policy["noiseAllowanceMs"])
            if result[metric] > limit:
                violations.append(f"{kind} {metric}: {result[metric]} > {limit:.2f} ms")
    return violations


def configuration_cache_reused(fixture):
    properties = (fixture / "build/woge-dev/client.properties").read_text().splitlines()
    details = next(line.split("=", 1)[1].replace("\\:", ":").replace("\\=", "=")
                   for line in properties if line.startswith("details="))
    with urllib.request.urlopen(details, timeout=5) as response:
        return "Reusing configuration cache." in response.read().decode()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("fixture", type=Path)
    parser.add_argument("port", type=int)
    parser.add_argument("report", type=Path)
    parser.add_argument("--update-baseline", action="store_true")
    parser.add_argument("--samples", type=int, help="Measured saves per path; default is the committed policy")
    args = parser.parse_args()
    connection = json.loads((args.fixture / "build/woge-dev/mcp.json").read_text())
    rpc(connection, "initialize", {"protocolVersion": "2025-06-18", "capabilities": {},
                                  "clientInfo": {"name": "woge-feedback-benchmark", "version": "1"}})
    policy = json.loads((ROOT / "testing/performance/development-loop-policy.json").read_text())
    properties = dict(line.split("=", 1) for line in (args.fixture / "gradle.properties").read_text().splitlines()
                      if "=" in line and not line.startswith("#"))
    adapter = properties["wogeSpringAdapter"]
    baseline_path = ROOT / f"testing/performance/development-loop-baseline-{adapter}.json"
    baseline = json.loads(baseline_path.read_text())
    sample_count = args.samples if args.samples is not None else policy["samples"]
    if not 3 <= sample_count <= 1000:
        parser.error("--samples must be between 3 and 1000")
    page = args.fixture / "src/main/kotlin/example/woge/HomePage.kt"
    original = page.read_text()
    results = {}
    for kind in ("css", "frontendAsset", "kotlinImplementation", "structuralGeneration"):
        samples = []
        for iteration in range(sample_count + 1):
            marker = f"benchmark-{kind}-{iteration}"
            if kind == "css":
                target = args.fixture / "src/main/resources/static/styles.css"
                edit = lambda: target.write_text(f"/* {marker} */\nbody {{ color: black; }}\n")
                path = "/styles.css"
            elif kind == "frontendAsset":
                target = args.fixture / "src/main/resources/static/benchmark.js"
                edit = lambda: target.write_text(f'// {marker}\nexport const ready = true;\n')
                path = "/benchmark.js"
            elif kind == "kotlinImplementation":
                target = args.fixture / "shared/src/main/kotlin/example/shared/Heading.kt"
                edit = lambda: target.write_text(
                    f'package example.shared\npublic fun feedbackHeading(): String = "{marker}"\n')
                path = "/"
            else:
                target = args.fixture / "src/main/kotlin/example/woge/BenchmarkRegion.kt"
                def edit():
                    target.write_text(
                        'package example.woge\nimport dev.woge.host.WogeRegion\n'
                        'import dev.woge.html.HtmlWriter\nimport dev.woge.html.p\n'
                        f'@WogeRegion\ninternal fun HtmlWriter.feedback{iteration}(input: String) '
                        '{ p { text(input) } }\n')
                    page.write_text(original.replace(
                        'h1 { text(example.shared.feedbackHeading()) }',
                        'h1 { text(example.shared.feedbackHeading()) }\n'
                        f'                        Feedback{iteration}Region.render(this, "{marker}")'))
                path = "/"
            sample = measure(connection, args.port, edit, path, marker)
            if kind == "css" and sample["generationId"] != sample["previousGenerationId"]:
                raise RuntimeError("CSS edit unexpectedly restarted the application")
            if iteration:
                samples.append(sample)
        results[kind] = {"samples": samples, "p50Ms": percentile(samples, 0.50),
                         "p95Ms": percentile(samples, 0.95)}
    violations = compare_results(results, policy, baseline, args.update_baseline)
    reused = configuration_cache_reused(args.fixture)
    if not reused:
        violations.append("The last incremental build did not reuse the configuration cache")
    report = {"schemaVersion": 1, "host": platform.platform(), "adapter": adapter,
              "launcherJava": subprocess.run(["java", "-version"], capture_output=True, text=True, check=True).stderr.strip(),
              "childJdk": properties["buildJdk"], "jvmTarget": properties["jvmTarget"],
              "springBootVersion": properties["springBootVersion"],
              "configurationCacheReused": reused, "measurement": "save-to-ready-HTTP",
              "samplesPerPath": sample_count, "warmupsPerPath": 1,
              "results": results, "violations": violations}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({kind: {key: value for key, value in result.items() if key != "samples"}
                      for kind, result in results.items()}, indent=2))
    if violations:
        raise SystemExit("\n".join(violations))
    if args.update_baseline:
        baseline_path.write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
