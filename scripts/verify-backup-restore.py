#!/usr/bin/env python3
"""Exercise account/session recovery on a disposable compact stack, retaining evidence."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import time
from urllib.request import Request, urlopen
import uuid

ROOT = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--env-file', required=True, type=Path)
parser.add_argument('--source-project', required=True)
parser.add_argument('--source-port', type=int, default=18091)
parser.add_argument('--target-port', type=int, default=18090)
args = parser.parse_args()
if not args.source_project.startswith('xiyouji-backup-'):
    parser.error('Only disposable xiyouji-backup-* source projects are accepted')
if not all(1024 <= port <= 65535 for port in [args.source_port, args.target_port]):
    parser.error('Ports must be in 1024..65535')
if args.source_port == args.target_port:
    parser.error('Source and target ports must differ')

token = ''
def api(base, path, body=None):
    headers = {'Content-Type': 'application/json', 'X-Idempotency-Key': str(uuid.uuid4())}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = Request(base + path, data=None if body is None else json.dumps(body).encode(), headers=headers)
    with urlopen(req, timeout=30) as response:
        return json.load(response)

source = 'http://localhost:' + str(args.source_port)
target = 'http://localhost:' + str(args.target_port)
account = 'restore_' + uuid.uuid4().hex[:8]
password = uuid.uuid4().hex
auth = api(source, '/api/auth/register', {'account': account, 'username': account, 'password': password})
token = auth['token']
game = api(source, '/api/game/new', {'characterClass': 'SUN_WUKONG'})
session_id = game['sessionId']
before = api(source, '/api/game/state/' + session_id)
directory = ROOT / 'backups' / ('drill-' + uuid.uuid4().hex[:8])
project = 'xiyouji-restore-' + uuid.uuid4().hex[:8]
cmd = [sys.executable, str(ROOT / 'scripts/backup_restore.py')]
common = ['--env-file', str(args.env_file.resolve()), '--directory', str(directory)]
subprocess.run(cmd + ['backup', '--project', args.source_project] + common, check=True)
restore = cmd + ['restore', '--project', project, '--port', str(args.target_port)] + common
subprocess.run(restore, check=True)
assert api(target, '/api/game/state/' + session_id) == before, 'Restored session differs'
login = api(target, '/api/auth/login', {'account': account, 'username': account, 'password': password})
assert login['account'] == account
subprocess.run(['docker', 'restart', project + '-redis-1'], check=True)
time.sleep(3)
assert api(target, '/api/game/state/' + session_id) == before, 'Redis restart lost session'
assert api(source, '/api/game/state/' + session_id) == before, 'Source was changed'
refused = subprocess.run(restore, capture_output=True)
assert refused.returncode != 0 and b'already exists' in refused.stderr
result = {'backup_directory': str(directory), 'restore_project': project,
          'account_login': 'PASS', 'session_exact_match': 'PASS', 'redis_restart_persistence': 'PASS',
          'source_unchanged': 'PASS', 'existing_target_refused': 'PASS'}
(directory / 'verification.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
print(json.dumps(result, indent=2))
