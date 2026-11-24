#!/usr/bin/env python3
"""Sign the verified release publications and write a local Central Portal bundle."""

import argparse
import hashlib
import os
from pathlib import Path
import re
import runpy
import subprocess
import sys
from tempfile import TemporaryDirectory
from zipfile import ZIP_DEFLATED, ZipFile


def publication_paths(group, version, modules, plugin_ids):
    for artifact in sorted(modules):
        directory = Path(*group.split("."), artifact, version)
        for suffix in (".pom", ".module", ".jar", "-sources.jar", "-javadoc.jar"):
            yield directory / f"{artifact}-{version}{suffix}"
    for plugin in sorted(plugin_ids):
        artifact = f"{plugin}.gradle.plugin"
        yield Path(*plugin.split("."), artifact, version, f"{artifact}-{version}.pom")


def bundle(repository, output, paths, fingerprint):
    output.parent.mkdir(parents=True, exist_ok=True)
    with TemporaryDirectory(prefix=".central-", dir=output.parent) as temporary:
        archive = Path(temporary) / "bundle.zip"
        signature = Path(temporary) / "signature.asc"
        with ZipFile(archive, "w", compression=ZIP_DEFLATED) as zipped:
            for relative in paths:
                artifact = repository / relative
                name = relative.as_posix()
                contents = artifact.read_bytes()
                signature.unlink(missing_ok=True)
                subprocess.run(
                    [
                        "gpg", "--batch", "--no-tty", "--armor", "--detach-sign",
                        "--local-user", fingerprint, "--output", str(signature),
                    ],
                    input=contents,
                    check=True,
                )
                zipped.writestr(name, contents)
                zipped.write(signature, f"{name}.asc")
                for algorithm in ("md5", "sha1"):
                    digest = hashlib.new(algorithm, contents, usedforsecurity=False).hexdigest()
                    zipped.writestr(f"{name}.{algorithm}", digest)
        archive.replace(output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repository", type=Path)
    parser.add_argument("group")
    parser.add_argument("version")
    parser.add_argument("modules")
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    scripts = Path(__file__).resolve().parent
    validate_channel = runpy.run_path(str(scripts / "verify-github-publication.py"))["validate_channel"]
    validate_channel(args.version, "release")
    if args.group != "dev.woge":
        raise ValueError("Maven Central bundles require the canonical dev.woge group.")
    fingerprint = os.environ.get("WOGE_CENTRAL_SIGNING_KEY", "")
    if not re.fullmatch(r"[0-9a-fA-F]{40}", fingerprint):
        raise ValueError("Set WOGE_CENTRAL_SIGNING_KEY to the public fingerprint of your local GPG signing key.")
    modules = args.modules.split(",")
    if not all(re.fullmatch(r"woge-[a-z0-9-]+", module) for module in modules):
        raise ValueError("Use the publication modules from the Woge build.")
    plugin_ids = runpy.run_path(str(scripts / "verify-publication-contract.py"))["PLUGIN_IDS"]
    paths = publication_paths(args.group, args.version, modules, plugin_ids)
    bundle(args.repository, args.output, paths, fingerprint)
    print(f"Maven Central bundle prepared: {args.output}")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        sys.exit(str(error))
