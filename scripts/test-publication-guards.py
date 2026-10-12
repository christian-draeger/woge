#!/usr/bin/env python3
"""Offline tests for release authorization and coordinate safety."""

import importlib.util
import io
import os
from pathlib import Path
import unittest
from unittest.mock import patch
from urllib.error import HTTPError, URLError


def load(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


publication = load("verify-github-publication")
commit = load("verify-publication-commit")


class PublicationGuardsTest(unittest.TestCase):
    def test_channels_are_explicit_and_match_tasks(self):
        for version, channel, requested in (
            ("0.1.0", "", ""), ("0.1.0", "snapshot", ""), ("0.1.0-SNAPSHOT", "release", ""),
            ("0.1.0-SNAPSHOT", "snapshot", "release"), ("0.1.0", "release", "release,snapshot"),
            ("../../bad", "release", ""), ("0.1.0\n", "release", ""),
        ):
            with self.subTest(version=version, channel=channel):
                with self.assertRaises(ValueError):
                    publication.validate_channel(version, channel, requested)
        publication.validate_channel("0.1.0-SNAPSHOT", "snapshot", "snapshot")
        publication.validate_channel("0.1.0", "release", "release")
        publication.validate_channel("0.1.0-beta.1", "release")

    def test_missing_credentials_fail_without_printing_values(self):
        with patch.dict(os.environ, {"ORG_GRADLE_PROJECT_githubPackagesUsername": "private-user"}, clear=True):
            with self.assertRaisesRegex(ValueError, "githubPackagesPassword") as error:
                publication.credentials()
        self.assertNotIn("private-user", str(error.exception))

    def test_release_probes_fail_closed(self):
        repository = Path("build/scaffold-maven-repository")
        pom = repository / "dev/woge/woge-core/0.1.0/woge-core-0.1.0.pom"
        with patch.object(Path, "glob", return_value=[pom]):
            for status in (401, 403, 429, 500):
                def forbidden(request, timeout, status=status):
                    raise HTTPError(request.full_url, status, "ignored", {}, io.BytesIO())
                with self.subTest(status=status), self.assertRaisesRegex(ValueError, f"HTTP {status}"):
                    publication.check_unused(repository, "0.1.0", "not-a-token", forbidden)

            def missing(request, timeout):
                raise HTTPError(request.full_url, 404, "ignored", {}, io.BytesIO())
            publication.check_unused(repository, "0.1.0", "not-a-token", missing)

            def unavailable(request, timeout):
                raise URLError("ignored")
            with self.assertRaisesRegex(ValueError, "unavailable"):
                publication.check_unused(repository, "0.1.0", "not-a-token", unavailable)

            class Existing:
                def __enter__(self):
                    return self
                def __exit__(self, *args):
                    pass
            with self.assertRaisesRegex(ValueError, "already exists"):
                publication.check_unused(repository, "0.1.0", "not-a-token", lambda *a, **k: Existing())
            jar = pom.with_suffix(".jar")
            with patch.object(Path, "glob", return_value=[pom, jar]):
                def partial(request, timeout):
                    if request.full_url.endswith(".jar"):
                        return Existing()
                    return missing(request, timeout)
                with self.assertRaisesRegex(ValueError, "already exists"):
                    publication.check_unused(repository, "0.1.0", "not-a-token", partial)

    def test_checks_require_every_build_job_and_success(self):
        checks = [{"name": name, "status": "completed", "conclusion": "success"}
                  for name in commit.REQUIRED_CHECKS]
        commit.validate_checks(checks)
        with self.assertRaises(ValueError):
            commit.validate_checks(checks[:-1])
        for status, conclusion in (("in_progress", None), ("completed", "failure"), ("completed", "skipped")):
            with self.subTest(status=status, conclusion=conclusion), self.assertRaises(ValueError):
                commit.validate_checks(checks + [{"name": "extra", "status": status, "conclusion": conclusion}])
        commit.validate_checks(checks + [{"name": "Publication gate", "status": "in_progress", "conclusion": None}])

    def test_workflow_is_dispatch_only_and_write_is_job_scoped(self):
        workflow = Path(".github/workflows/publish-packages.yml").read_text()
        self.assertIn("workflow_dispatch:", workflow)
        self.assertNotIn("pull_request:", workflow)
        self.assertNotIn("\n  push:", workflow)
        gate, publish = workflow.split("\n  publish:")
        self.assertNotIn("packages: write", gate)
        self.assertIn("packages: write", publish)
        self.assertIn("persist-credentials: false", publish)
        self.assertIn('if: github.ref == \'refs/heads/main\'', gate)


if __name__ == "__main__":
    unittest.main()
