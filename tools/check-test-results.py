"""Require actual library acceptance, including the isolated native MariaDB test."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
reports = [ET.parse(p).getroot() for p in (root / "build/test-results/test").glob("TEST-*.xml")]
counts = {key: sum(int(report.attrib[key]) for report in reports) for key in ("tests", "failures", "errors", "skipped")}
if counts["tests"] < 10 or any(counts[key] for key in ("failures", "errors", "skipped")):
    raise SystemExit("Full native library acceptance required: " + str(counts))
if not any("MariaDbLedgerTest" in report.attrib.get("name", "") for report in reports):
    raise SystemExit("Actual MariaDB native ledger test did not execute.")
print("Starter native library acceptance: " + str(counts))
