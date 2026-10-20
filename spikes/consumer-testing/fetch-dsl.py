#!/usr/bin/env python3
"""Fetch only the pinned MIT-licensed DSL sources; production has no dependency on this spike."""
import pathlib
import urllib.request

COMMIT = "9adefaed1c1c6c34f356479aa952a8d22eda238e"
FILES = ["LICENSE", "core/src/commonMain/kotlin/codes/draeger/playwright/PlaywrightDsl.kt"]
FILES += ["core/src/jvmMain/kotlin/codes/draeger/playwright/core/" + name + ".kt"
          for name in ("KPlaywright", "BrowserDsl", "PageDsl")]
root = pathlib.Path(__file__).resolve().parent / "build/upstream"
for name in FILES:
    target = root / name
    if not target.exists():
        target.parent.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen("https://raw.githubusercontent.com/christian-draeger/playwright-kotlin/" + COMMIT + "/" + name) as response:
            target.write_bytes(response.read())
