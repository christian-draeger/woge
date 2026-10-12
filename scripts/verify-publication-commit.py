#!/usr/bin/env python3
"""Require the current main commit and successful checks before publication."""

import json
import os
import re
import sys
from urllib.request import Request, urlopen

REPOSITORY = "christian-draeger/woge"
REQUIRED_CHECKS = {"check", "production-deployment", "reference-browser-smoke", "example-browser-smoke"}
PUBLICATION_CHECKS = {"Publication gate", "Publish packages"}


def validate_checks(checks):
    checks = [check for check in checks if check["name"] not in PUBLICATION_CHECKS]
    if not REQUIRED_CHECKS <= {check["name"] for check in checks}:
        raise ValueError("The complete Build workflow must have checks on the selected main commit.")
    if any(check["status"] != "completed" or check["conclusion"] != "success" for check in checks):
        raise ValueError("Every non-publication check must be completed and successful before publishing.")


def main():
    sha = sys.argv[1]
    if not re.fullmatch("[0-9a-f]{40}", sha):
        raise ValueError("Select an exact 40-character commit SHA.")
    if os.environ.get("GITHUB_REF") != "refs/heads/main":
        raise ValueError("Run this workflow from main, never a pull request or another branch.")
    token = os.environ.get("GH_TOKEN", "")
    if not token:
        raise ValueError("The read-only verification job requires its GitHub token.")

    def get(path):
        request = Request(
            f"https://api.github.com/repos/{REPOSITORY}/{path}",
            headers={"Authorization": "Bearer " + token, "Accept": "application/vnd.github+json"},
        )
        with urlopen(request, timeout=30) as response:
            return json.load(response)

    if get("git/ref/heads/main")["object"]["sha"] != sha:
        raise ValueError("Select the current main HEAD; main has moved or the SHA is not main.")
    checks = []
    page = 1
    while True:
        result = get(f"commits/{sha}/check-runs?filter=latest&per_page=100&page={page}")["check_runs"]
        checks.extend(result)
        if len(result) < 100:
            break
        page += 1
    validate_checks(checks)
    statuses = get(f"commits/{sha}/status")
    if statuses["statuses"] and statuses["state"] != "success":
        raise ValueError("Commit status contexts must also be successful.")
    print(f"Verified current main commit {sha} with all checks green.")


if __name__ == "__main__":
    try:
        main()
    except ValueError as error:
        sys.exit(str(error))
