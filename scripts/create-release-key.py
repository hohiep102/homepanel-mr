#!/usr/bin/env python3
"""Generate HomePanel signing material locally and a private local backup."""
from pathlib import Path
import argparse, datetime, json, os, secrets, shutil, subprocess
root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--edition', choices=('community', 'store'), default='store')
edition = parser.parse_args().edition
private = root / '.private-signing'
private.mkdir(mode=0o700, exist_ok=True)
key = private / ('homepanel.p12' if edition == 'store' else 'homepanel-community.p12')
config = private / ('signing.json' if edition == 'store' else 'community-signing.json')
if key.exists() or config.exists():
    raise SystemExit('Signing material already exists; not replacing it.')
password = secrets.token_urlsafe(36)
env = dict(os.environ, HOMEPANEL_KEY_PASSWORD=password)
keytool = str(Path(os.environ['JAVA_HOME'])/'bin/keytool') if os.environ.get('JAVA_HOME') else shutil.which('keytool')
if not keytool:
    raise SystemExit('Install JDK 17 and set JAVA_HOME, or add keytool to PATH.')
subprocess.run([keytool,'-genkeypair','-keystore',str(key),'-storetype','PKCS12','-storepass:env','HOMEPANEL_KEY_PASSWORD','-keypass:env','HOMEPANEL_KEY_PASSWORD','-alias','homepanel','-keyalg','RSA','-keysize','4096','-sigalg','SHA256withRSA','-validity','10000','-dname',f'CN=HomePanel MR {edition.title()}, OU=Application signing'], env=env, check=True, capture_output=True)
config.write_text(json.dumps({'storeFile':str(key),'storePassword':password,'keyAlias':'homepanel','keyPassword':password},indent=2))
for f in (key,config): f.chmod(0o600)
backup = Path.home()/'.local/share/homepanel-mr/signing-backups'/f'{edition}-{datetime.datetime.now():%Y-%m-%d-%H%M%S}'
backup.mkdir(parents=True, mode=0o700, exist_ok=False)
for f in (key,config): shutil.copy2(f,backup/f.name)
print(f'HomePanel {edition} release key and private local backup created. Passwords are not printed.')
