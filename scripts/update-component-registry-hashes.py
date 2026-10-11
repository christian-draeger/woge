#!/usr/bin/env python3
"""Refresh source hashes after editing and versioning a registry recipe."""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "registry"
index = json.loads((root / "index.json").read_text())
for item in index["items"]:
    sources = {}
    for variant in item["variants"].values():
        for file in variant["files"] + variant["tests"]:
            source = root / file["source"]
            if not source.resolve().is_relative_to(root.resolve()):
                raise ValueError(f"Source escapes the registry: {source}")
            file["sha256"] = hashlib.sha256(source.read_bytes()).hexdigest()
            sources[file["source"]] = file["sha256"]
    revision = "".join(f"{path} {digest}\n" for path, digest in sorted(sources.items()))
    item["provenance"]["revisionKind"] = "source-tree-sha256"
    item["provenance"]["revision"] = hashlib.sha256(revision.encode()).hexdigest()
(root / "index.json").write_text(json.dumps(index, indent=2) + "\n")
