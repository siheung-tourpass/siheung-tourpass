"""Compare the OpenAPI document with the API: python3 backend/tests/python/openapi_check.py [API base URL]."""
import re
import sys
import urllib.request
from pathlib import Path
from api_check import BASE, Client
from http_support import opener, send

ROOT = Path(__file__).resolve().parents[3]
SPEC = ROOT / 'frontend/assets/api-docs/openapi.yaml'
CONTROLLER = ROOT / 'backend/src/main/java/kr/ac/siheung/tourpass/controller/ApiController.java'
STRICT_POLICY = "default-src 'self'; frame-ancestors 'none'; base-uri 'none'"
METHODS = {'get', 'post', 'put', 'patch', 'delete'}
SAMPLES = {'token': 'x', 'type': 'has-theme'}
ACCOUNTS = {'/me/': 'visitor', '/merchant/': 'staff-a', '/admin/': 'admin'}


def spec_operations(text):
    """Read paths and methods by indentation so the check needs no YAML package."""
    body = text.split('\npaths:\n', 1)[1].split('\ncomponents:\n', 1)[0]
    operations, path = {}, None
    for line in body.splitlines():
        if match := re.fullmatch(r'  (/\S*):', line):
            path = match[1]
            operations[path] = set()
        elif (match := re.fullmatch(r'    (\w+):', line)) and match[1] in METHODS:
            operations[path].add(match[1].upper())
    return operations


def check_overview(text, operations):
    """The overview table in info.description states counts, so keep them true."""
    prefixes = {'방문객': '/me/', '담당자': '/merchant/', '관리자': '/admin/'}
    for role, paths, requests in re.findall(r'\| (공개|방문객|담당자|관리자) \| (\d+) \| (\d+) \|', text):
        if role == '공개':
            selected = [path for path in operations if not path.startswith(tuple(prefixes.values()))]
        else:
            selected = [path for path in operations if path.startswith(prefixes[role])]
        assert (int(paths), int(requests)) == (len(selected), sum(len(operations[path]) for path in selected)), role
    total = re.search(r'\*\*(\d+)개 경로, (\d+)개 요청\*\*', text)
    assert (int(total[1]), int(total[2])) == (len(operations), sum(map(len, operations.values()))), total[0]


def normalize(path):
    return re.sub(r'\{[^}]*\}', '{}', path)


def controller_paths(text):
    paths = set()
    for mapping in re.findall(r'@RequestMapping\((?:\{)?("[^)]*?")\}?\)', text):
        for path in re.findall(r'"([^"]*)"', mapping):
            if path == '/**':
                continue
            for expanded in expand(path):
                paths.add(normalize(expanded.rstrip('/') or '/'))
    return paths


def expand(path):
    match = re.search(r'\{\w+:([^}]+)\}', path)
    if not match:
        return [path]
    return [item for choice in match[1].split('|') for item in expand(path[:match.start()] + choice + path[match.end():])]


def check_paths(operations):
    documented = {normalize(path) for path in operations}
    implemented = controller_paths(CONTROLLER.read_text(encoding='utf-8'))
    assert documented == implemented, {'missing from spec': sorted(implemented - documented), 'missing from API': sorted(documented - implemented)}


def check_methods(operations):
    clients = {}
    for path, methods in operations.items():
        login = next((name for prefix, name in ACCOUNTS.items() if path.startswith(prefix)), None)
        client = clients.setdefault(login, Client(login))
        url = re.sub(r'\{(\w+)\}', lambda match: SAMPLES.get(match[1], '1'), path)
        client.request('OPTIONS', url, expected=204)
        allowed = {method.strip() for method in client.headers['Allow'].split(',')} - {'HEAD', 'OPTIONS'}
        assert allowed == methods, (path, 'API', sorted(allowed), 'spec', sorted(methods))


def check_page():
    web = BASE.removesuffix('/api/v1')
    http = opener()
    status, headers, page = send(http, urllib.request.Request(web + '/docs'))
    assert status == 200, status
    assert headers['Content-Security-Policy'] == "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'"
    html = page.decode()
    assets = re.findall(r'(?:src|href)="\.\./\.\./(webjars/[^"]+)"', html)
    assert len(assets) == 1, assets
    for asset in assets:
        status, headers, _ = send(http, urllib.request.Request(web + '/' + asset))
        assert status == 200, asset
        assert 'Set-Cookie' not in headers, asset
    for name in ('init.js', 'openapi.yaml'):
        status, headers, _ = send(http, urllib.request.Request(web + '/assets/api-docs/' + name))
        assert status == 200, name
        assert headers['Content-Security-Policy'] == STRICT_POLICY, name
    for path in ('/', '/assets/css/style.css', '/api/v1/'):
        _, headers, _ = send(http, urllib.request.Request(web + path))
        assert headers['Content-Security-Policy'] == STRICT_POLICY, path
    status, _, _ = send(http, urllib.request.Request(web + '/webjars/scalar__api-reference/none.js'))
    assert status == 404


if __name__ == '__main__':
    operations = spec_operations(SPEC.read_text(encoding='utf-8'))
    check_overview(SPEC.read_text(encoding='utf-8'), operations)
    check_paths(operations)
    check_methods(operations)
    check_page()
    print(f'OpenAPI document matches {len(operations)} paths')
