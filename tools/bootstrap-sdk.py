#!/usr/bin/env python3
"""Fetch only the reviewed SDK archive, then verify every source file before use.

Default network source is a pinned codeload.github.com HTTPS commit archive.
--verify is offline and is required by settings.gradle for the default cache.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import tarfile
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
MAX_ARCHIVE = 10 * 1024 * 1024
MAX_CONTENT = 50 * 1024 * 1024


def manifest(path: Path) -> dict:
    value = json.loads(path.read_text())
    if not isinstance(value, dict) or set(value) != {"repository", "commit", "version", "sha256"}:
        raise ValueError("SDK manifest requires exact repository/commit/version/sha256 fields.")
    if not all(isinstance(item, str) for item in value.values()):
        raise ValueError("SDK manifest values must be strings.")
    if value["repository"] != "Lunixi-Payment/lunixi-java" or value["version"] != "0.2.0":
        raise ValueError("Unexpected SDK repository/version.")
    if not re.fullmatch(r"[a-f0-9]{40}", value["commit"]) or not re.fullmatch(r"[a-f0-9]{64}", value["sha256"]):
        raise ValueError("SDK manifest needs a reviewed immutable commit and SHA256.")
    return value


def contents(data: bytes, pin: dict) -> dict[str, bytes]:
    if len(data) > MAX_ARCHIVE or hashlib.sha256(data).hexdigest() != pin["sha256"]:
        raise ValueError("SDK archive checksum mismatch.")
    expected_root = "lunixi-java-" + pin["commit"]
    files: dict[str, bytes] = {}
    total = 0
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as archive:
        members = archive.getmembers()
        if len(members) > 10000:
            raise ValueError("SDK archive contains too many entries.")
        for member in members:
            path = PurePosixPath(member.name)
            if path.is_absolute() or ".." in path.parts or "\\" in member.name or not path.parts or path.parts[0] != expected_root:
                raise ValueError("Unsafe SDK archive path.")
            if not member.isdir() and not member.isfile():
                raise ValueError("SDK archive links/devices are forbidden.")
            relative = PurePosixPath(*path.parts[1:])
            if member.isdir():
                continue
            if str(relative) == "." or str(relative) in files or member.size < 0:
                raise ValueError("Invalid/duplicate SDK archive entry.")
            total += member.size
            if total > MAX_CONTENT:
                raise ValueError("SDK archive expands beyond the configured bound.")
            handle = archive.extractfile(member)
            if handle is None:
                raise ValueError("Unreadable SDK archive entry.")
            content = handle.read(member.size + 1)
            if len(content) != member.size:
                raise ValueError("Incomplete SDK archive entry.")
            files[str(relative)] = content
    properties = files.get("gradle.properties", b"").decode("utf-8")
    if "lunixiVersion=0.2.0" not in properties.splitlines() or "settings.gradle" not in files:
        raise ValueError("SDK archive does not contain the expected build/version.")
    return files


class NoRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError("SDK source redirects are forbidden.")


def download(pin: dict) -> bytes:
    url = "https://codeload.github.com/" + pin["repository"] + "/tar.gz/" + pin["commit"]
    request = urllib.request.Request(url, headers={"User-Agent": "lunixi-spring-source-bootstrap/0.1"})
    with urllib.request.build_opener(NoRedirects()).open(request, timeout=30) as response:
        data = response.read(MAX_ARCHIVE + 1)
    if len(data) > MAX_ARCHIVE:
        raise ValueError("SDK download exceeds the configured bound.")
    return data


@contextmanager
def cache_lock(cache: Path):
    cache.mkdir(mode=0o700, parents=True, exist_ok=True)
    if cache.is_symlink():
        raise ValueError("SDK cache cannot be a symlink.")
    lock = cache / ".bootstrap.lock"
    if lock.is_symlink():
        raise ValueError("SDK lock cannot be a symlink.")
    with lock.open("a+b") as handle:
        if os.name == "nt":
            import msvcrt
            handle.write(b"0"); handle.flush(); handle.seek(0)
            msvcrt.locking(handle.fileno(), msvcrt.LK_LOCK, 1)
        else:
            import fcntl
            fcntl.flock(handle.fileno(), fcntl.LOCK_EX)
        try:
            yield
        finally:
            if os.name == "nt":
                handle.seek(0); msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(handle.fileno(), fcntl.LOCK_UN)


def validate_tree(target: Path, files: dict[str, bytes]) -> None:
    if target.is_symlink() or not target.is_dir():
        raise ValueError("Verified SDK cache is missing.")
    actual = set()
    for base, directories, names in os.walk(target, followlinks=False):
        current = Path(base)
        # Gradle-generated caches/output are not SDK source; exclude only root dirs.
        if current == target:
            directories[:] = [d for d in directories if d not in {"build", ".gradle"}]
        for name in directories + names:
            if (current / name).is_symlink():
                raise ValueError("SDK cache source contains a symlink.")
        for name in names:
            file = current / name
            relative = file.relative_to(target).as_posix()
            if relative not in files or not file.is_file() or file.stat().st_size != len(files[relative]) or file.read_bytes() != files[relative]:
                raise ValueError("SDK cache source differs from the reviewed archive.")
            actual.add(relative)
    if actual != set(files):
        raise ValueError("SDK cache source is incomplete.")


def bootstrap(root: Path, verify: bool = False) -> Path:
    pin = manifest(root / "tools/sdk-source.json")
    cache = root / ".sdk-cache"
    archive = cache / (pin["commit"] + ".tar.gz")
    target = cache / "lunixi-java"
    with cache_lock(cache):
        if archive.is_symlink():
            raise ValueError("SDK archive cannot be a symlink.")
        if archive.is_file():
            if archive.stat().st_size > MAX_ARCHIVE:
                raise ValueError("Cached SDK archive exceeds the configured bound.")
            data = archive.read_bytes()
        elif verify:
            raise ValueError("Run python3 tools/bootstrap-sdk.py before the default build.")
        else:
            data = download(pin)
        files = contents(data, pin)
        if target.exists() or target.is_symlink():
            validate_tree(target, files)
        elif verify:
            raise ValueError("Verified SDK source is missing; run bootstrap first.")
        else:
            with tempfile.TemporaryDirectory(prefix=".extract-", dir=cache) as directory:
                stage = Path(directory) / "source"
                stage.mkdir()
                for relative, content in files.items():
                    file = stage / relative
                    file.parent.mkdir(parents=True, exist_ok=True)
                    file.write_bytes(content)
                validate_tree(stage, files)
                # Source becomes visible in one rename, only after complete verification.
                os.replace(stage, target)
        if not archive.exists():
            with tempfile.NamedTemporaryFile(prefix=".download-", dir=cache, delete=False) as handle:
                temporary = Path(handle.name)
                handle.write(data); handle.flush(); os.fsync(handle.fileno())
            try:
                os.replace(temporary, archive)
            finally:
                temporary.unlink(missing_ok=True)
    return target


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify", action="store_true", help="offline verification only; never downloads")
    arguments = parser.parse_args()
    try:
        bootstrap(ROOT, arguments.verify)
    except (OSError, ValueError, tarfile.TarError) as error:
        raise SystemExit("SDK source validation failed: " + str(error)) from None
    print("Reviewed SDK source checksum and complete cache contents verified.")
