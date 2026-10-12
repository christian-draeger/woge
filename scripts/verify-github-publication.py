#!/usr/bin/env python3
"""Fail closed before uploads; never print credentials or authenticated URLs."""

import argparse
import base64
import os
from pathlib import Path
import re
import sys
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


def validate_channel(version, channel, requested=""):
    if channel not in ("snapshot", "release"):
        raise ValueError("Select -PwogePublicationChannel=snapshot or release explicitly.")
    if requested and requested != channel:
        raise ValueError("The publication task and wogePublicationChannel must match.")
    pattern = r"\d+\.\d+\.\d+(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?"
    if not re.fullmatch(pattern, version):
        raise ValueError("Use a semantic version, optionally with a prerelease suffix.")
    if version.endswith("-SNAPSHOT") != (channel == "snapshot"):
        raise ValueError("Snapshots require -SNAPSHOT; releases must not have that suffix.")


def credentials():
    names = ("githubPackagesUsername", "githubPackagesPassword")
    values = [os.environ.get("ORG_GRADLE_PROJECT_" + name, "") for name in names]
    if not all(values):
        raise ValueError(
            "Set ORG_GRADLE_PROJECT_githubPackagesUsername to your GitHub username and "
            "ORG_GRADLE_PROJECT_githubPackagesPassword to a token with write:packages "
            "(and access to this repository). Never put them in repository files."
        )
    return base64.b64encode(":".join(values).encode()).decode()


def check_unused(repository, version, authorization, opener=urlopen):
    artifacts = sorted(path for path in repository.glob(f"**/{version}/*")
                       if path.suffix in (".pom", ".jar", ".module"))
    if not any(path.suffix == ".pom" for path in artifacts):
        raise ValueError("No local publication POMs found; build the local publications first.")
    # Also catch incomplete uploads whose JAR exists but POM has not reached the registry.
    for artifact in artifacts:
        url = "https://maven.pkg.github.com/christian-draeger/woge/" + artifact.relative_to(repository).as_posix()
        request = Request(url, headers={"Authorization": "Basic " + authorization})
        try:
            with opener(request, timeout=30):
                raise ValueError(f"Release coordinate already exists: {artifact.parent.parent.name}:{version}.")
        except HTTPError as error:
            error.close()
            if error.code != 404:
                raise ValueError(f"Cannot verify unused release coordinates (HTTP {error.code}).") from None
        except URLError:
            raise ValueError("Cannot verify unused release coordinates (registry unavailable).") from None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--group", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--channel", required=True)
    parser.add_argument("--requested-channel", default="")
    args = parser.parse_args()
    validate_channel(args.version, args.channel, args.requested_channel)
    if args.group != "dev.woge":
        raise ValueError("GitHub publication requires the canonical dev.woge group.")
    authorization = credentials()
    if args.channel == "release":
        check_unused(args.repository, args.version, authorization)
    print(f"GitHub Packages {args.channel} preflight passed for {args.version}.")


if __name__ == "__main__":
    try:
        main()
    except ValueError as error:
        sys.exit(str(error))
