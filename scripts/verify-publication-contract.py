#!/usr/bin/env python3
"""Inspect actual local Maven output, not build-file strings."""

import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
from zipfile import ZipFile

NS = {"m": "http://maven.apache.org/POM/4.0.0"}
PLUGIN_IDS = (
    "dev.woge.application", "dev.woge.components", "dev.woge.spring-boot",
    "dev.woge.tailwind", "dev.woge.vite", "dev.woge.kotlin-js-islands",
)


def artifact_base(directory, module, version):
    if version.endswith("-SNAPSHOT"):
        metadata = ET.parse(directory / "maven-metadata.xml").getroot()
        for entry in metadata.findall("versioning/snapshotVersions/snapshotVersion"):
            if entry.findtext("extension") == "pom":
                return f"{module}-{entry.findtext('value')}"
        raise AssertionError(f"No snapshot POM metadata for {module}")
    return f"{module}-{version}"


def pom_metadata(pom, group, artifact, version):
    root = ET.parse(pom).getroot()
    for key, value in (("groupId", group), ("artifactId", artifact), ("version", version)):
        assert root.findtext("m:" + key, namespaces=NS) == value, (pom, key)
    for key in ("name", "description", "url", "scm/url", "scm/connection", "scm/developerConnection"):
        assert root.findtext("/".join("m:" + part for part in key.split("/")), namespaces=NS), (pom, key)
    assert root.findtext("m:licenses/m:license/m:name", namespaces=NS) == "Apache License, Version 2.0"
    assert root.findtext("m:developers/m:developer/m:id", namespaces=NS) == "christian-draeger"
    return root


def verify(repository, group, version, modules):
    for module in modules:
        directory = repository / group.replace(".", "/") / module / version
        base = artifact_base(directory, module, version)
        root = pom_metadata(directory / (base + ".pom"), group, module, version)
        production = not module.startswith("woge-dev-") and module != "woge-gradle-plugin"
        for dependency in root.findall("m:dependencies/m:dependency", NS):
            dep_group = dependency.findtext("m:groupId", namespaces=NS)
            artifact = dependency.findtext("m:artifactId", namespaces=NS)
            if dep_group == group:
                assert artifact in modules, (module, "unpublished dependency", artifact)
                assert dependency.findtext("m:version", namespaces=NS) == version, (module, artifact)
            if production:
                assert not artifact.startswith("woge-dev-") and artifact != "spring-boot-devtools", (module, artifact)
        metadata = json.loads((directory / (base + ".module")).read_text())
        assert all(metadata["component"][key] == value for key, value in
                   (("group", group), ("module", module), ("version", version))), module
        assert {"sourcesElements", "javadocElements"} <= {v["name"] for v in metadata["variants"]}
        for variant in metadata["variants"]:
            for dependency in variant.get("dependencies", []):
                if dependency["group"] == group:
                    assert dependency["module"] in modules
                    assert dependency["version"]["requires"] == version
                if production:
                    assert not dependency["module"].startswith("woge-dev-")
                    assert dependency["module"] != "spring-boot-devtools"
        with ZipFile(directory / (base + "-sources.jar")) as jar:
            if module == "woge-spring-boot-starter":
                assert "api(project" in jar.read("build-definition/build.gradle.kts").decode()
            else:
                assert any(name.endswith((".kt", ".java", ".js")) for name in jar.namelist()), module
        with ZipFile(directory / (base + "-javadoc.jar")) as jar:
            assert len(jar.read("docs/guides/quickstart-spring-boot.md")) > 1000, module
            assert "Apache License" in jar.read("LICENSE").decode(), module
        with ZipFile(directory / (base + ".jar")) as jar:
            assert "META-INF/LICENSE" in jar.namelist(), module
            if production:
                assert not any(name.startswith(("dev/woge/dev/", "org/springframework/boot/devtools/"))
                               for name in jar.namelist()), module
            if module == "woge-fallback-client-assets":
                assert "static/assets/woge/index.js" in jar.namelist()
                assert json.loads(jar.read("META-INF/woge/fallback-client/package.json"))["version"] == version
            if module == "woge-gradle-plugin":
                assert f"version={version}" in jar.read("dev/woge/gradle/woge-gradle-plugin.properties").decode()
                assert any(name.endswith("/LICENSE.txt") and "/registry/" in name for name in jar.namelist())
            if module == "woge-core":
                assert "MIT" in jar.read("META-INF/licenses/webref-provenance.md").decode()
                assert len(jar.read("META-INF/licenses/LICENSE.webref")) > 500
    for plugin in PLUGIN_IDS:
        artifact = plugin + ".gradle.plugin"
        directory = repository / plugin.replace(".", "/") / artifact / version
        root = pom_metadata(directory / (artifact_base(directory, artifact, version) + ".pom"), plugin, artifact, version)
        dependency = root.find("m:dependencies/m:dependency", NS)
        assert dependency is not None, plugin
        assert dependency.findtext("m:groupId", namespaces=NS) == group
        assert dependency.findtext("m:artifactId", namespaces=NS) == "woge-gradle-plugin"
        assert dependency.findtext("m:version", namespaces=NS) == version
    print(f"Publication contract passed: {len(modules)} modules, {len(PLUGIN_IDS)} plugin markers.")


if __name__ == "__main__":
    verify(Path(sys.argv[1]), sys.argv[2], sys.argv[3], set(sys.argv[4].split(",")))
