#!/usr/bin/env python3
"""Sign a reviewed APK with a private local owner key, outside Gradle/source/cache.

Only the hash-verified JDK and Android apksigner run with access to this directory.
The password file is a convenience for unattended LOCAL signing, not a substitute
for private filesystem permissions and an encrypted offline backup of the key.
"""
import argparse
import hashlib
import os
from pathlib import Path
import secrets
import subprocess

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--apk', type=Path, required=True)
p.add_argument('--sha256', required=True)
p.add_argument('--tools', type=Path, required=True)
p.add_argument('--keydir', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
p.add_argument('--initialize-key', action='store_true')
a = p.parse_args()
a.apk, a.tools, a.keydir, a.output = [x.resolve() for x in (a.apk, a.tools, a.keydir, a.output)]
if hashlib.sha256(a.apk.read_bytes()).hexdigest() != a.sha256:
    p.error('unsigned APK hash mismatch')
if a.output.exists():
    p.error('refusing to overwrite an existing signed artifact')
os.umask(0o077)
a.keydir.mkdir(mode=0o700, parents=True, exist_ok=True)
if a.keydir.stat().st_mode & 0o077:
    p.error('key directory must have mode 0700')
key = a.keydir / 'owner-release.p12'
password = a.keydir / 'password.txt'
initialize = not key.exists()
if initialize:
    if not a.initialize_key or password.exists():
        p.error('explicit --initialize-key required for a fresh, empty key directory')
    with password.open('x') as f:
        f.write(secrets.token_urlsafe(48) + '\n')
elif not password.is_file():
    p.error('owner key exists but password file is missing; never replace the key')
for file in (key, password):
    if file.exists() and (file.is_symlink() or file.stat().st_mode & 0o077):
        p.error('signing files must be ordinary owner-private files')
a.output.parent.mkdir(parents=True, exist_ok=True)
prefix = ['bwrap', '--unshare-all', '--die-with-parent', '--new-session', '--clearenv',
          '--ro-bind', '/usr', '/usr', '--symlink', 'usr/bin', '/bin',
          '--symlink', 'usr/lib', '/lib', '--symlink', 'usr/lib64', '/lib64',
          '--dev', '/dev', '--dir', '/proc', '--tmpfs', '/tmp',
          '--ro-bind', str(a.tools / 'jdk'), '/jdk',
          '--ro-bind', str(a.tools / 'android-sdk/build-tools/36.1.0/lib/apksigner.jar'), '/apksigner.jar',
          '--ro-bind', str(a.apk), '/input.apk', '--bind', str(a.keydir), '/signing',
          '--bind', str(a.output.parent), '/output',
          '--setenv', 'LD_LIBRARY_PATH', '/jdk/lib:/jdk/lib/server',
          '--setenv', 'LANG', 'C.UTF-8', '--setenv', 'PATH', '/jdk/bin:/usr/bin']
if initialize:
    subprocess.run(prefix + ['/jdk/bin/keytool', '-genkeypair', '-keystore', '/signing/owner-release.p12',
        '-storetype', 'PKCS12', '-storepass:file', '/signing/password.txt', '-alias', 'stunmesh',
        '-keyalg', 'RSA', '-keysize', '4096', '-sigalg', 'SHA256withRSA', '-validity', '10000',
        '-dname', 'CN=STUNMESH'], check=True)
key.chmod(0o600)
signer = prefix + ['/jdk/bin/java', '-jar', '/apksigner.jar']
subprocess.run(signer + ['sign', '--ks', '/signing/owner-release.p12', '--ks-key-alias', 'stunmesh',
    '--ks-pass', 'file:/signing/password.txt', '--v1-signing-enabled', 'false',
    '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true', '--v4-signing-enabled', 'false',
    '--out', '/output/' + a.output.name, '/input.apk'], check=True)
subprocess.run(signer + ['verify', '--verbose', '--print-certs', '/output/' + a.output.name], check=True)
print('APK SHA-256:', hashlib.sha256(a.output.read_bytes()).hexdigest())
