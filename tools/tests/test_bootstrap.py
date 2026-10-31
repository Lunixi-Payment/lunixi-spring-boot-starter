"""Offline safety and atomic-cache regressions for public SDK source bootstrap."""
import concurrent.futures
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("bootstrap_sdk", Path(__file__).resolve().parents[1] / "bootstrap-sdk.py")
bootstrap = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bootstrap)
COMMIT = "b" * 40


def archive(extra=None):
    output = io.BytesIO()
    with tarfile.open(fileobj=output, mode="w:gz") as handle:
        files = {"settings.gradle": b"rootProject.name='lunixi-java-sdk'", "gradle.properties": b"lunixiVersion=0.2.1\n", "src/example.java": b"// synthetic public source\n"}
        for name, content in files.items():
            item = tarfile.TarInfo("lunixi-java-" + COMMIT + "/" + name)
            item.size = len(content)
            handle.addfile(item, io.BytesIO(content))
        if extra:
            handle.addfile(extra, io.BytesIO(b"x") if extra.isfile() else None)
    return output.getvalue()


class BootstrapTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / "tools").mkdir()
        self.data = archive()
        self.pin(self.data)

    def pin(self, data):
        value = {"repository": "Lunixi-Payment/lunixi-java", "commit": COMMIT, "version": "0.2.1", "sha256": hashlib.sha256(data).hexdigest()}
        (self.root / "tools/sdk-source.json").write_text(json.dumps(value))
        return value

    def install(self):
        with patch.object(bootstrap, "download", return_value=self.data) as download:
            target = bootstrap.bootstrap(self.root)
            self.assertEqual(download.call_count, 1)
        return target

    def test_valid_archive_is_cached_and_offline_verification_never_downloads(self):
        target = self.install()
        with patch.object(bootstrap, "download", side_effect=AssertionError("Network is forbidden")):
            self.assertEqual(bootstrap.bootstrap(self.root, True), target)
        self.assertEqual((target / "src/example.java").read_bytes(), b"// synthetic public source\n")

    def test_checksum_mismatch_leaves_no_install(self):
        with patch.object(bootstrap, "download", return_value=self.data + b"tampered"):
            with self.assertRaises(ValueError): bootstrap.bootstrap(self.root)
        self.assertFalse((self.root / ".sdk-cache/lunixi-java").exists())

    def test_missing_offline_cache_does_not_fetch(self):
        with patch.object(bootstrap, "download", side_effect=AssertionError("Network is forbidden")):
            with self.assertRaises(ValueError): bootstrap.bootstrap(self.root, True)

    def test_changed_missing_or_extra_source_files_are_rejected(self):
        target = self.install()
        file = target / "src/example.java"
        original = file.read_bytes()
        file.write_bytes(b"tampered")
        with self.assertRaises(ValueError): bootstrap.bootstrap(self.root, True)
        file.unlink()
        with self.assertRaises(ValueError): bootstrap.bootstrap(self.root, True)
        file.write_bytes(original)
        (target / "unexpected.gradle").write_text("tampered")
        with self.assertRaises(ValueError): bootstrap.bootstrap(self.root, True)

    def test_generated_gradle_output_is_allowed_but_source_symlinks_are_not(self):
        target = self.install()
        (target / "build").mkdir(); (target / "build/generated.class").write_bytes(b"generated")
        bootstrap.bootstrap(self.root, True)
        file = target / "src/example.java"; file.unlink(); file.symlink_to(target / "settings.gradle")
        with self.assertRaises(ValueError): bootstrap.bootstrap(self.root, True)

    def test_traversal_absolute_and_link_entries_are_rejected(self):
        for name, kind in [("lunixi-java-" + COMMIT + "/../outside", tarfile.REGTYPE), ("/absolute", tarfile.REGTYPE), ("lunixi-java-" + COMMIT + "/linked", tarfile.SYMTYPE)]:
            item = tarfile.TarInfo(name); item.type = kind; item.size = 1 if kind == tarfile.REGTYPE else 0
            item.linkname = "/outside"
            data = archive(item)
            with self.assertRaises(ValueError): bootstrap.contents(data, self.pin(data))

    def test_duplicate_entries_are_rejected(self):
        item = tarfile.TarInfo("lunixi-java-" + COMMIT + "/src/example.java"); item.size = 1
        data = archive(item)
        with self.assertRaises(ValueError): bootstrap.contents(data, self.pin(data))

    def test_parallel_bootstraps_share_one_atomic_complete_cache(self):
        with patch.object(bootstrap, "download", return_value=self.data) as download:
            with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
                targets = list(pool.map(lambda _: bootstrap.bootstrap(self.root), range(3)))
            self.assertEqual(download.call_count, 1)
        self.assertEqual(len(set(targets)), 1)
        bootstrap.bootstrap(self.root, True)

    def test_cache_and_lock_symlinks_fail_closed(self):
        cache = self.root / ".sdk-cache"; cache.symlink_to(self.root / "tools", target_is_directory=True)
        with self.assertRaises(ValueError): bootstrap.bootstrap(self.root, True)
        cache.unlink(); cache.mkdir(); (cache / ".bootstrap.lock").symlink_to(self.root / "tools/sdk-source.json")
        with self.assertRaises(ValueError): bootstrap.bootstrap(self.root, True)

    def test_manifest_rejects_unreviewed_sources_and_placeholder_pins(self):
        for change in [{"repository": "attacker/sdk"}, {"version": "0.1.0"}, {"commit": "main"}, {"sha256": "pending"}]:
            pin = self.pin(self.data); pin.update(change)
            (self.root / "tools/sdk-source.json").write_text(json.dumps(pin))
            with self.assertRaises(ValueError): bootstrap.bootstrap(self.root, True)


if __name__ == "__main__": unittest.main()
