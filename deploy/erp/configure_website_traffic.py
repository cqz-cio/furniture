"""Enable opt-in website analytics in the canonical server env, without exporting secrets.

Run as the deployment owner on the server; the next regular CD copies this env.
Existing keys are reused. Rotation must use the dedicated HMAC rotation process.
"""
import argparse
import datetime
import json
import os
from pathlib import Path
import secrets
import tempfile
from zoneinfo import ZoneInfo


def configure(source, tenant, enabled_from, generate=secrets.token_urlsafe):
    if not str(tenant).isdigit() or int(tenant) < 1:
        raise ValueError('Invalid tenant')
    datetime.date.fromisoformat(enabled_from)
    lines = source.splitlines()
    values = {}
    for line in lines:
        if line and not line.lstrip().startswith('#') and '=' in line:
            key, value = line.split('=', 1)
            if key in values:
                raise ValueError('Duplicate environment setting')
            values[key] = value
    # Reject alternative bindings rather than silently overriding another operator's config.
    if any(key.startswith('YUDAO_STATISTICS') for key in values):
        raise ValueError('Inspect existing statistics environment bindings first')
    config = json.loads(values.get('SPRING_APPLICATION_JSON', '{}'))
    if not isinstance(config, dict) or any('.' in key for key in config):
        raise ValueError('Expected nested Spring JSON configuration')
    stats = config.setdefault('yudao', {}).setdefault('statistics', {})
    behavior = stats.setdefault('behavior', {})
    website = stats.setdefault('website', {})
    allowed = {'enabled', 'consent-required', 'enabled-tenant-ids', 'hmac-tenants',
               'per-ip-per-minute', 'per-visitor-per-minute', 'per-tenant-per-minute',
               'global-per-minute', 'consent-per-ip-per-minute', 'consent-evidence-lifetime-days',
               'consent-record-retention-days', 'consent-policy-version'}
    if not set(behavior).issubset(allowed) or not set(website).issubset({'enabled-from'}):
        raise ValueError('Inspect existing statistics property naming first')
    tenant = str(tenant)
    hmac = behavior.setdefault('hmac-tenants', {})
    entry = hmac.get(tenant)
    additions = {}
    if entry is None:
        key_ref = 'WEBSITE_' + tenant + '_HMAC_V1'
        consent_ref = 'WEBSITE_' + tenant + '_CONSENT_KEY'
        entry = {'active-version': 1, 'active-key-ref': key_ref, 'consent-evidence-key-ref': consent_ref}
        hmac[tenant] = entry
        for name in (key_ref, consent_ref):
            if name not in values:
                additions[name] = generate(48)
    for field in ('active-key-ref', 'consent-evidence-key-ref'):
        ref = entry.get(field)
        if not ref or len(additions.get(ref, values.get(ref, ''))) < 32:
            raise ValueError('Existing analytics key cannot be resolved; no keys were changed')
    behavior['enabled'] = True
    behavior['consent-required'] = True
    ids = behavior.setdefault('enabled-tenant-ids', [])
    if int(tenant) not in ids:
        ids.append(int(tenant))
    website.setdefault('enabled-from', {}).setdefault(tenant, enabled_from)
    additions['SPRING_APPLICATION_JSON'] = json.dumps(config, separators=(',', ':'))
    output = [line for line in lines if line.split('=', 1)[0] not in additions]
    output.extend(key + '=' + value for key, value in additions.items())
    return '\n'.join(output) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', required=True)
    parser.add_argument('--tenant', required=True, type=int)
    args = parser.parse_args()
    root = Path(args.root).resolve()
    if str(root) not in ('/opt/oakved-deploy/test', '/opt/oakved-deploy/production'):
        raise ValueError('Expected a canonical ERP deployment root')
    if os.geteuid() != 0:
        raise ValueError('Run as root to protect the generated secrets')
    state = json.loads((root / 'state.json').read_text())
    if state.get('in_progress'):
        raise ValueError('A deployment is in progress')
    path = root / 'config/backend.env'
    if path.is_symlink():
        raise ValueError('Refusing an environment symlink')
    source = path.read_text()
    today = datetime.datetime.now(ZoneInfo('Asia/Shanghai')).date().isoformat()
    updated = configure(source, args.tenant, today)
    if updated == source:
        print(json.dumps({'status': 'unchanged', 'tenant': args.tenant}))
        return
    backup_dir = root / 'backups/website-config'
    backup_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
    backup_dir.chmod(0o700)
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    backup = backup_dir / ('backend.env.' + stamp + '.' + secrets.token_hex(4))
    fd = os.open(backup, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as stream:
        stream.write(source)
    fd, temporary = tempfile.mkstemp(dir=path.parent, prefix='.website-env-')
    try:
        with os.fdopen(fd, 'w') as stream:
            stream.write(updated)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        path.chmod(0o600)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    print(json.dumps({'status': 'configured-for-next-cd', 'tenant': args.tenant, 'backup': str(backup)}))


if __name__ == '__main__':
    main()
