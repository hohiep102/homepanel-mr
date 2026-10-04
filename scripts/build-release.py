#!/usr/bin/env python3
"""Build signed editions without passing signing secrets in command arguments."""
from pathlib import Path
import argparse, json, os, subprocess

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--edition', choices=('community', 'store', 'all'), default='all')
args, tasks = parser.parse_known_args()
editions = ['community', 'store'] if args.edition == 'all' else [args.edition]
env = dict(os.environ)
if not env.get('JAVA_HOME'):
    java_home = Path('/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home')
    if java_home.exists(): env['JAVA_HOME'] = str(java_home)
for edition in editions:
    config = root / '.private-signing' / ('signing.json' if edition == 'store' else 'community-signing.json')
    if not config.exists():
        raise SystemExit(f'Missing {edition} signing key. Run scripts/create-release-key.py --edition {edition} first.')
    data = json.loads(config.read_text())
    prefix = 'HOMEPANEL_RELEASE' if edition == 'store' else 'HOMEPANEL_COMMUNITY'
    for suffix, field in [('STORE','storeFile'),('PASSWORD','storePassword'),('ALIAS','keyAlias'),('KEY_PASSWORD','keyPassword')]:
        env[f'{prefix}_{suffix}'] = data[field]
if not tasks:
    tasks = [f':app:{task}{edition.title()}Release{tail}' for edition in editions
             for task, tail in [('assemble',''), ('lint',''), ('test','UnitTest')]]
raise SystemExit(subprocess.call(['./gradlew', *tasks, '--console=plain'], cwd=root, env=env))
