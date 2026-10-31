"""Scan all clean public source, including the native sample; prove detection stays on."""
from pathlib import Path
import base64
import json
import secrets
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix="lunixi-starter-source-") as directory:
    export = Path(directory) / "source"
    shutil.copytree(root, export, ignore=shutil.ignore_patterns("build", ".gradle", ".git", ".sdk-cache", ".starter-cache", "__pycache__"))
    def scan():
        report = Path(directory) / "report.json"
        result = subprocess.run(["gitleaks", "dir", str(export), "--no-banner", "--redact", "--config", str(export / ".gitleaks.toml"), "--report-format", "json", "--report-path", str(report)], capture_output=True, text=True, timeout=60, check=False)
        return result.returncode, json.loads(report.read_text()) if report.exists() else []
    code, findings = scan()
    if code != 0 or findings:
        raise SystemExit("Full clean source scan failed; inspect redacted Gitleaks diagnostics.")
    fixture = export / "src/test/java/com/lunixi/spring/AutoConfigurationTest.java"
    with fixture.open("a") as handle:
        handle.write('\nString api_key = "' + secrets.token_hex(24) + '";\n')
        der = bytes.fromhex("302e020100300506032b657004220420") + secrets.token_bytes(32)
        armour = "-----BEGIN " + "PRIVATE KEY-----\n" + base64.b64encode(der).decode() + "\n-----END " + "PRIVATE KEY-----\n"
        handle.write(armour)
    code, findings = scan()
    detected = {finding["RuleID"] for finding in findings if finding["File"].endswith("AutoConfigurationTest.java")}
    if code != 1 or not {"generic-api-key", "private-key"}.issubset(detected):
        raise SystemExit("Source scan API/private-key negative controls were not rejected.")
print("Full clean Starter/sample source scan passed; added API/private-key negative controls rejected.")
