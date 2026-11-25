#!/usr/bin/env python3
"""Stage the tracked Woge Todo source and seed the matched deployment fixture."""

import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parents[2]
EXAMPLE = Path("examples/todo-spring-boot")
TODO = Path("src/main/kotlin/example/todo/Todos.kt")
EMPTY = 'internal val todos = InMemoryTodoRepository()\n'
SEEDED = '''internal val todos = InMemoryTodoRepository().apply {
    repeat(10) { add("Todo <${it + 1}> & Kotlin") }
}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path, help="New workspace directory for the packaging copy")
    parser.add_argument("--source", default="HEAD", help="Git commit containing the example")
    args = parser.parse_args()
    source = subprocess.check_output(["git", "rev-parse", "--verify", f"{args.source}^{{commit}}"],
                                     cwd=ROOT, text=True).strip()
    archive = subprocess.check_output(["git", "archive", source, str(EXAMPLE)], cwd=ROOT)
    args.output.mkdir(parents=True, exist_ok=False)
    with tarfile.open(fileobj=io.BytesIO(archive)) as tracked:
        tracked.extractall(args.output, filter="data")
    todo = args.output / EXAMPLE / TODO
    original = todo.read_text()
    if original.count(EMPTY) != 1:
        raise RuntimeError("The tracked Todo bootstrap changed. Review the staging step before measuring.")
    seeded = original.replace(EMPTY, SEEDED)
    todo.write_text(seeded)
    manifest = {
        "sourceCommit": source,
        "sourceFile": str(EXAMPLE / TODO),
        "originalSha256": hashlib.sha256(original.encode()).hexdigest(),
        "seededSha256": hashlib.sha256(seeded.encode()).hexdigest(),
    }
    (args.output / "woge-seed.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    main()
