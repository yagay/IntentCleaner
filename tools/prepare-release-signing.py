#!/usr/bin/env python3
"""Decode the private Actions Secret; export signing paths without logging secrets."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys


def main():
    raw = os.environ.get('ANDROID_SIGNING_JSON', '')
    output = Path(os.environ['GITHUB_OUTPUT'])
    if not raw.strip():
        with output.open('a') as stream:
            stream.write('configured=false\n')
        print('::warning::ANDROID_SIGNING_JSON is missing; only unsigned compilation can be validated.')
        return
    payload = json.loads(raw)
    names = ('keystore_base64', 'store_password', 'key_alias', 'key_password')
    if any(not isinstance(payload.get(k), str) or not payload[k] for k in names):
        raise ValueError('Incomplete signing secret')
    if any('\n' in payload[k] or '\r' in payload[k] for k in names):
        raise ValueError('Signing secret fields must be single-line strings')
    if not re.fullmatch(r'[A-Za-z0-9_.-]+', payload['key_alias']):
        raise ValueError('Unsupported key alias')
    for name in names:
        value = payload[name].replace('%', '%25')
        print('::add-mask::' + value, flush=True)
    key_bytes = base64.b64decode(payload['keystore_base64'], validate=True)
    if not 1024 <= len(key_bytes) <= 32768:
        raise ValueError('Unexpected keystore size')
    target_dir = Path(os.environ['RUNNER_TEMP']) / 'listcleaner-signing'
    target_dir.mkdir(mode=0o700, exist_ok=True)
    target = target_dir / 'release.p12'
    target.write_bytes(key_bytes)
    target.chmod(0o600)
    env = os.environ.copy()
    env['LC_VALIDATION_PASSWORD'] = payload['store_password']
    cert = subprocess.run([
        'keytool', '-exportcert', '-keystore', str(target), '-storetype', 'PKCS12',
        '-alias', payload['key_alias'], '-storepass:env', 'LC_VALIDATION_PASSWORD'
    ], env=env, capture_output=True, check=True).stdout
    expected = Path('signing/release-certificate.sha256').read_text().strip().lower()
    if hashlib.sha256(cert).hexdigest() != expected:
        raise ValueError('Certificate does not match the fixed ListCleaner release key')
    values = {
        'RELEASE_STORE_FILE': str(target), 'RELEASE_STORE_TYPE': 'PKCS12',
        'RELEASE_STORE_PASSWORD': payload['store_password'],
        'RELEASE_KEY_ALIAS': payload['key_alias'],
        'RELEASE_KEY_PASSWORD': payload['key_password'],
    }
    with Path(os.environ['GITHUB_ENV']).open('a') as stream:
        for key, value in values.items():
            stream.write(f'{key}={value}\n')
    with output.open('a') as stream:
        stream.write('configured=true\n')
    print('Fixed Release signing certificate verified.')


if __name__ == '__main__':
    try:
        main()
    except Exception:
        # Avoid embedding passwords, base64, keytool output or JSON in CI logs.
        print('::error::Invalid signing secret or certificate. See docs/RELEASE.md.', file=sys.stderr)
        sys.exit(1)
