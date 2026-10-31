"""Inspect actual Java17 Starter JAR/POM/metadata and native test acceptance."""
from pathlib import Path
import re
import struct
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
artifact = "lunixi-spring-boot-starter"
version_match = re.search(r"(?m)^lunixiVersion=(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$", (root / "gradle.properties").read_text())
assert version_match, "Expected an exact release version in gradle.properties"
version = version_match.group(0).split("=", 1)[1]
for suffix in ("", "-sources", "-javadoc"):
    path = root / f"build/libs/{artifact}-{version}{suffix}.jar"
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        assert "META-INF/LICENSE" in names
        assert names and all(not n.startswith("/") and ".." not in Path(n).parts for n in names)
        assert not any(n.endswith((".pem", ".key", ".sqlite3")) or "/test/" in n or ".sdk-cache" in n or "samples/" in n for n in names)
        for name in names:
            if name.endswith("/"): continue
            value = archive.read(name)
            assert not re.search(rb"/(?:Users|home)/[A-Za-z0-9._-]+/|/tmp/[A-Za-z0-9._-]+/", value), name
            if name.endswith(".class"):
                assert name.startswith("com/lunixi/spring/"), name
                assert value[:4] == bytes.fromhex("cafebabe") and struct.unpack(">H", value[6:8])[0] == 61, name
        if not suffix:
            assert "com/lunixi/spring/JdbcLedgerStore.class" in names
            key = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
            assert archive.read(key).decode().strip() == "com.lunixi.spring.LunixiAutoConfiguration"
            assert "META-INF/spring-configuration-metadata.json" in names
pom = ET.parse(root / "build/publications/starter/pom-default.xml").getroot()
ns = {"p": "http://maven.apache.org/POM/4.0.0"}
assert pom.findtext("p:groupId", namespaces=ns) == "com.lunixi"
assert pom.findtext("p:artifactId", namespaces=ns) == artifact
assert pom.findtext("p:version", namespaces=ns) == version
assert pom.findtext("p:scm/p:url", namespaces=ns) == "https://github.com/Lunixi-Payment/lunixi-spring-boot-starter"
dependencies = {(d.findtext("p:groupId", namespaces=ns), d.findtext("p:artifactId", namespaces=ns), d.findtext("p:version", namespaces=ns)) for d in pom.findall("p:dependencies/p:dependency", ns)}
assert ("com.lunixi", "lunixi-java-sdk", "0.2.0") in dependencies
assert not any(group.startswith("org.junit") for group, _, _ in dependencies)
print("Starter class61/JAR boundaries, Boot registration/metadata and SDK0.2 POM dependency passed.")
