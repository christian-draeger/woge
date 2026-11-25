#!/usr/bin/env python3
"""Replay the small typed examples with and without semantic checking."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess

HERE = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--maven-cache", type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    next_app = output / "next"
    hilla_app = output / "hilla"
    shutil.copytree(HERE / "next", next_app)
    shutil.copytree(HERE / "hilla", hilla_app)
    log = output / "logs"
    log.mkdir()
    runs = []

    def verify(name, command, project, expected=0, diagnostic=None):
        raw = subprocess.run(command, cwd=project, capture_output=True, text=True, timeout=240)
        messages = raw.stdout + raw.stderr
        (log / f"{name}.log").write_text(messages)
        runs.append({"name": name, "command": command, "exitCode": raw.returncode})
        (log / "results.json").write_text(json.dumps(runs, indent=2) + "\n")
        passed = raw.returncode == 0 if expected == 0 else raw.returncode != 0
        if not passed or diagnostic and diagnostic not in messages:
            raise RuntimeError(f"Unexpected {name} result. See the retained logs.")
        print(name, raw.returncode, flush=True)

    verify("install-next", ["npm", "ci", "--ignore-scripts", "--no-audit", "--no-fund"], next_app)
    tsc_path = next_app / "node_modules/typescript/lib/tsc.js"
    tsc = ["node", str(tsc_path), "--noEmit", "--pretty", "false"]
    verify("typescript-version", ["node", str(tsc_path), "--version"], next_app)
    maven = ["mvn", "-B", "-ntp"]
    if args.maven_cache:
        maven.append(f"-Dmaven.repo.local={args.maven_cache.resolve()}")
    maven.extend(["compile", "vaadin:build-frontend"])

    verify("next-control-tsc", tsc, next_app)
    verify("next-control-runtime", ["node", "runtime.mjs"], next_app)
    next_form = next_app / "form.ts"
    drifted = next_form.read_text().replace("title: string", "title: number")
    next_form.write_text(drifted)
    verify("next-type-drift-tsc", tsc, next_app, expected=1, diagnostic="TS2322")
    verify("next-type-drift-unchecked", ["node", "runtime.mjs"], next_app)
    next_form.write_text(drifted.replace("typeof body.title !== 'string' || body.title.trim() === ''",
                                        "typeof body.title !== 'number' || !Number.isInteger(body.title)"))
    verify("next-coordinated-tsc", tsc, next_app)
    verify("next-coordinated-runtime", ["node", "runtime.mjs", "number"], next_app)

    model = hilla_app / "src/main/java/corpus/EditTodo.java"
    generated = hilla_app / "src/main/frontend/generated/corpus/EditTodo.ts"
    verify("hilla-control-build", maven, hilla_app)
    verify("hilla-control-tsc", tsc, hilla_app)
    shutil.copyfile(generated, log / "hilla-control-model.ts")
    shutil.copyfile(hilla_app / "src/main/frontend/generated/TodoService.ts", log / "hilla-generated-service.ts")
    model.write_text(model.read_text().replace("String title", "Integer title"))
    verify("hilla-type-drift-build", maven, hilla_app, expected=1, diagnostic="TS2322")
    verify("hilla-type-drift-tsc", tsc, hilla_app, expected=1, diagnostic="TS2322")
    shutil.copyfile(generated, log / "hilla-type-drift-model.ts")
    unchecked = """
        const fs = require('node:fs');
        const assert = require('node:assert/strict');
        const ts = require(process.argv[1]);
        const result = ts.transpileModule(fs.readFileSync('src/main/frontend/views/@index.tsx', 'utf8'), {
            fileName: '@index.tsx', reportDiagnostics: true,
            compilerOptions: { jsx: ts.JsxEmit.ReactJSX, target: ts.ScriptTarget.ES2022 },
        });
        assert.equal(result.diagnostics.length, 0);
        assert.ok(result.outputText.includes("String(form.get('title'))"));
        console.log(`TypeScript ${ts.version}: transpilation emitted the stale string call.`);
    """
    verify("hilla-type-drift-unchecked", ["node", "-e", unchecked, str(tsc_path.parent.parent)], hilla_app)
    form = hilla_app / "src/main/frontend/views/@index.tsx"
    form.write_text(form.read_text().replace("String(form.get('title'))", "Number(form.get('title'))"))
    verify("hilla-coordinated-build", maven, hilla_app)
    verify("hilla-coordinated-tsc", tsc, hilla_app)


if __name__ == "__main__":
    main()
