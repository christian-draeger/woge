#!/usr/bin/env python3
"""Small standard-library contracts for the opt-in feedback report."""
import importlib.util
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

spec = importlib.util.spec_from_file_location("benchmark", Path(__file__).with_name("benchmark-development-loop.py"))
benchmark = importlib.util.module_from_spec(spec)
spec.loader.exec_module(benchmark)


class DevelopmentLoopBenchmarkTest(unittest.TestCase):
    def test_percentiles_use_nearest_rank(self):
        samples = [{"milliseconds": value} for value in [30, 10, 20]]
        self.assertEqual(20, benchmark.percentile(samples, 0.50))
        self.assertEqual(30, benchmark.percentile(samples, 0.95))

    def test_regression_tolerance_and_fixed_ceiling_both_apply(self):
        policy = {"ceilingsMs": {"css": {"p50Ms": 100, "p95Ms": 120}},
                  "toleranceFraction": 0.5, "noiseAllowanceMs": 2}
        baseline = {"results": {"css": {"p50Ms": 10, "p95Ms": 100}}}
        results = {"css": {"p50Ms": 18, "p95Ms": 121}}
        self.assertEqual(2, len(benchmark.compare_results(results, policy, baseline)))
        self.assertEqual(1, len(benchmark.compare_results(results, policy, baseline, update_baseline=True)))
        results["css"] = {"p50Ms": 17, "p95Ms": 120}
        self.assertEqual([], benchmark.compare_results(results, policy, baseline))

    def test_details_properties_unescape_url_separators(self):
        fixture = MagicMock()
        (fixture / "build/woge-dev/client.properties").read_text.return_value = (
            "details=http\\://127.0.0.1\\:1234/details?token\\=fixture-only\n")
        with patch.object(benchmark.urllib.request, "urlopen") as open_url:
            open_url.return_value.__enter__.return_value.read.return_value = b"Reusing configuration cache."
            self.assertTrue(benchmark.configuration_cache_reused(fixture))
            open_url.assert_called_once_with("http://127.0.0.1:1234/details?token=fixture-only", timeout=5)

    def test_build_failure_is_not_a_successful_sample(self):
        connection = {}
        before = {"phase": "READY", "latestRequestedBuild": 1, "activeServerGeneration": 1}
        failed = {**before, "phase": "BUILD_FAILED", "lastSuccessfulBuild": 1}
        with patch.object(benchmark, "tool", side_effect=[before, failed]), \
                patch.object(benchmark.time, "monotonic", side_effect=[0, 0, 181]), \
                patch.object(benchmark.time, "sleep"), patch.object(benchmark, "read_url") as read:
            with self.assertRaisesRegex(RuntimeError, "Feedback timeout"):
                benchmark.measure(connection, 1234, lambda: None, "/", "new-content")
            read.assert_not_called()


if __name__ == "__main__":
    unittest.main()
