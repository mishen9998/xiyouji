#!/usr/bin/env python3
"""Cold application backup and non-destructive restore for the compact Compose stack.

Requires Python 3.9+ and Docker Compose v2. Secrets remain in the external env file.
Restore only targets a NEW xiyouji-restore-* project, never overwrites an existing stack.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import tempfile
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parent.parent


def run(args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)


def output(args):
    return subprocess.check_output(args, text=True).strip()


def checksum(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['backup', 'restore'])
    parser.add_argument('--project', required=True)
    parser.add_argument('--env-file', required=True, type=Path)
    parser.add_argument('--directory', required=True, type=Path)
    parser.add_argument('--port', type=int, default=18090, help='Restore HTTP loopback port')
    args = parser.parse_args()
    if not re.fullmatch(r'[a-z0-9][a-z0-9_-]*', args.project):
        parser.error('Invalid Compose project name')
    if not args.env_file.is_file():
        parser.error('Env file does not exist')
    directory = args.directory.resolve()
    compose = ['docker', 'compose', '-p', args.project, '--env-file',
               str(args.env_file.resolve()), '-f', str(ROOT / 'docker-compose-cloud.yml')]

    def cid(service):
        value = output(compose + ['ps', '-aq', service])
        if not value or '\n' in value:
            raise RuntimeError('Expected exactly one container for ' + service)
        return value

    if args.action == 'backup':
        # All writers must be the app service of this compact stack.
        app = cid('app')
        if output(['docker', 'inspect', '-f', '{{.State.Running}}', app]) != 'true':
            raise RuntimeError('Source app must be running before backup')
        image_id = output(['docker', 'inspect', '-f', '{{.Image}}', app])
        image_ref = output(['docker', 'inspect', '-f', '{{.Config.Image}}', app])
        directory.mkdir(parents=True, exist_ok=False, mode=0o700)
        # Stop the only application writer while BOTH stores are snapshotted.
        # start is in finally, including when stop or a dump fails.
        try:
            run(compose + ['stop', '-t', '60', 'app'])
            with (directory / 'mysql.sql').open('wb') as stream:
                run(compose + ['exec', '-T', 'mysql', 'sh', '-c',
                    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot '
                    '--single-transaction --routines --events --triggers '
                    '--set-gtid-purged=OFF --no-tablespaces --databases xiyouji'], stdout=stream)
            if output(compose + ['exec', '-T', 'redis', 'redis-cli', 'SAVE']) != 'OK':
                raise RuntimeError('Redis snapshot failed')
            run(['docker', 'cp', cid('redis') + ':/data/dump.rdb', str(directory / 'dump.rdb')])
            manifest = {
                'format': 1, 'created_at': datetime.now(timezone.utc).isoformat(),
                'project': args.project, 'app_image_id': image_id, 'app_image_ref': image_ref,
                'sha256': {name: checksum(directory / name) for name in ['mysql.sql', 'dump.rdb']},
            }
            (directory / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
            for path in directory.iterdir():
                path.chmod(0o600)
        finally:
            run(compose + ['start', 'app'])
        # Do not recreate from env defaults: the running container may use overrides.
        for _ in range(180):
            health = output(['docker', 'inspect', '-f', '{{.State.Health.Status}}', app])
            if health == 'healthy':
                break
            if health == 'unhealthy':
                raise RuntimeError('Backup saved, but source app is unhealthy after restart')
            time.sleep(2)
        else:
            raise RuntimeError('Backup saved, but source app recovery timed out')
        print('Backup complete:', directory)
        print('Copy this directory to encrypted off-host storage; keep env secrets separately.')
        return

    if not args.project.startswith('xiyouji-restore-') or not 1024 <= args.port <= 65535:
        parser.error('Restore requires a NEW xiyouji-restore-* project and port 1024..65535')
    manifest = json.loads((directory / 'manifest.json').read_text(encoding='utf-8'))
    if manifest.get('format') != 1:
        raise RuntimeError('Unsupported manifest format')
    for name in ['mysql.sql', 'dump.rdb']:
        if checksum(directory / name) != manifest['sha256'][name]:
            raise RuntimeError('Backup checksum mismatch: ' + name)
    # Refuse even an existing stopped project or orphaned data volumes/networks.
    for resource in ['container', 'volume', 'network']:
        if output(['docker', resource, 'ls', '-q', '--filter',
                   'label=com.docker.compose.project=' + args.project]):
            raise RuntimeError('Restore target already exists: ' + args.project)
    for suffix in ['mysql_data', 'redis_data']:
        if subprocess.run(['docker', 'volume', 'inspect', args.project + '_' + suffix],
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
            raise RuntimeError('Target volume already exists')
    # Resolve the original exact image. It must be loaded/pulled on the target first.
    image_id = manifest['app_image_id']
    if not re.fullmatch(r'sha256:[0-9a-f]{64}', image_id):
        raise RuntimeError('Invalid image ID')
    run(['docker', 'image', 'inspect', image_id], stdout=subprocess.DEVNULL)
    os.environ.update(APP_IMAGE=image_id, HTTP_BIND='127.0.0.1', HTTP_PORT=str(args.port))
    # Redis with appendonly=yes does NOT load a standalone dump.rdb on fresh startup.
    # First load RDB with AOF disabled, then generate AOF from the recovered dataset.
    with tempfile.TemporaryDirectory(prefix='xiyouji-restore-') as temp:
        override = Path(temp) / 'redis-bootstrap.json'
        override.write_text(json.dumps({'services': {'redis': {'command': [
            'redis-server', '--appendonly', 'no', '--maxmemory', '128mb',
            '--maxmemory-policy', 'noeviction']}}}), encoding='utf-8')
        bootstrap = compose + ['-f', str(override)]
        run(bootstrap + ['create', '--no-build', 'redis'])
        run(['docker', 'cp', str(directory / 'dump.rdb'), cid('redis') + ':/data/dump.rdb'])
        run(bootstrap + ['up', '-d', '--no-build', '--wait', '--wait-timeout', '180', 'mysql', 'redis'])
        response = output(compose + ['exec', '-T', 'redis', 'redis-cli', 'CONFIG', 'SET', 'appendonly', 'yes'])
        if response != 'OK':
            raise RuntimeError('Could not enable Redis AOF: ' + response)
        for _ in range(90):
            info = output(compose + ['exec', '-T', 'redis', 'redis-cli', 'INFO', 'persistence'])
            fields = dict(line.split(':', 1) for line in info.splitlines() if ':' in line)
            if fields.get('aof_rewrite_in_progress') == '0' and fields.get('aof_rewrite_scheduled') == '0':
                if fields.get('aof_last_bgrewrite_status') != 'ok':
                    raise RuntimeError('Redis AOF rewrite failed')
                break
            time.sleep(2)
        else:
            raise RuntimeError('Redis AOF rewrite timed out')
    # Recreate with the normal appendonly=yes command and prove AOF loads on startup.
    run(compose + ['up', '-d', '--no-build', '--wait', '--wait-timeout', '180', 'mysql', 'redis'])
    with (directory / 'mysql.sql').open('rb') as stream:
        run(compose + ['exec', '-T', 'mysql', 'sh', '-c',
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'], stdin=stream)
    run(compose + ['up', '-d', '--no-build', '--wait', '--wait-timeout', '360', 'app'])
    print('Restored into NEW project:', args.project)
    print('Verify accounts, game state and restart persistence at http://127.0.0.1:' + str(args.port))
    print('Source stack was not modified. No data is automatically deleted on failure.')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError, OSError, ValueError, KeyError) as error:
        print('ERROR:', error, file=sys.stderr)
        sys.exit(1)
