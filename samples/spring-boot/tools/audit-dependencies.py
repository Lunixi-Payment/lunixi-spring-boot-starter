#!/usr/bin/env python3
"""Audit exact resolved Maven coordinates with the public OSV querybatch API; no credentials sent."""
import json
from pathlib import Path
from urllib.request import Request, urlopen

root = Path(__file__).resolve().parents[1]
inventory = json.loads((root / 'build/reports/dependency-inventory.json').read_text())
coordinates = sorted({(x['group'], x['module'], x['version']) for values in inventory.values() for x in values})
findings = []
for offset in range(0, len(coordinates), 40):
    chunk = coordinates[offset:offset + 40]
    queries = [{'package': {'ecosystem': 'Maven', 'name': group + ':' + module}, 'version': version} for group, module, version in chunk]
    request = Request('https://api.osv.dev/v1/querybatch', data=json.dumps({'queries': queries}).encode(), headers={'Content-Type': 'application/json'}, method='POST')
    with urlopen(request, timeout=30) as response:
        result = json.load(response)
    if len(result.get('results', [])) != len(chunk):
        raise RuntimeError('Incomplete vulnerability audit response')
    for coordinate, record in zip(chunk, result['results']):
        for item in record.get('vulns', []):
            findings.append({'coordinate': ':'.join(coordinate), 'id': item['id']})
report = {'coordinatesChecked': len(coordinates), 'runtimeComponents': len(inventory['runtimeClasspath']), 'testComponents': len(inventory['testRuntimeClasspath']), 'findings': findings, 'source': 'https://api.osv.dev/v1/querybatch'}
(root / 'build/reports/dependency-audit.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report))
raise SystemExit(1 if findings else 0)
