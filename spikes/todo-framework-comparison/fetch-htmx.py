#!/usr/bin/env python3
import hashlib
import pathlib
import urllib.request
with urllib.request.urlopen("https://cdn.jsdelivr.net/npm/htmx.org@2.0.11/dist/htmx.min.js") as response:
    data = response.read()
assert hashlib.sha256(data).hexdigest() == "d6fdc75f204e6bdefa99b69bf1e6d4ac69b8a364f77929f45c13476b4000f717"
target = pathlib.Path(__file__).resolve().parent / "build/generated-resources/static/htmx.min.js"
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(data)
