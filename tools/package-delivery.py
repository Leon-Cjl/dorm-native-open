"""Build an allowlisted source archive and audit only local delivery files. No network or device I/O."""
from pathlib import Path
import base64
import hashlib
import json
import os
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
dist = root / 'dist'
android = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root / 'android/AndroidManifest.xml').getroot()
version = manifest.get(android + 'versionName')
assert re.fullmatch(r'\d+\.\d+\.\d+', version)
build = json.loads((dist / 'build-result.json').read_text(encoding='utf-8-sig'))
apk = Path(build['apk'])
personal = bool(build.get('personalBuild'))
source_zip = dist / f'dorm-native-open-{version}-source.zip'
envelope = json.loads((root / 'private/本人宿舍授权.dormkey').read_text(encoding='utf-8-sig'))
migration = json.loads(base64.b64decode(envelope['payload']))
needles = [str(v).encode() for v in [migration.get('staffID'), migration.get('MAC'), migration.get('roomNo'), migration.get('sn'), envelope['signature']] if v and len(str(v)) >= 8]

def audit(name, data):
    if any(needle in data for needle in needles):
        raise RuntimeError('Private value found in delivery member: ' + name)
    if re.search(rb'(?m)^-----BEGIN (?:RSA )?PRIVATE KEY-----\r?$', data):
        raise RuntimeError('Private key found in delivery member: ' + name)

members = [root / f for f in ['README.md', 'build.ps1', '.gitignore', 'android/AndroidManifest.xml']]
for directory in ['android/src', 'android/res', 'android/assets', 'tests', 'tools', 'onboarding', 'docs', 'branding']:
    members.extend(p for p in (root / directory).rglob('*') if p.is_file() and '__pycache__' not in p.parts and p.suffix != '.pyc')
with zipfile.ZipFile(source_zip, 'w', zipfile.ZIP_DEFLATED) as z:
    for p in sorted(members):
        p.resolve().relative_to(root.resolve())
        name = p.relative_to(root).as_posix()
        if any(part in {'private', 'build', 'signing'} for part in Path(name).parts) or p.suffix in {'.keystore', '.dormkey', '.dormrequest', '.pem'} or p.name == 'observed-session.json':
            raise RuntimeError('Non-allowlisted source path')
        data = p.read_bytes()
        audit(name, data)
        z.writestr(name, data)
with zipfile.ZipFile(apk) as z:
    assert ('assets/owner.dormkey' in z.namelist()) == personal
    for filename in ['index.html', 'gongda.css', 'door.css', 'door.js']:
        assert z.read('assets/ui/' + filename) == (root / 'android/assets/ui' / filename).read_bytes()
    for name in z.namelist():
        if name == 'assets/owner.dormkey':
            assert z.read(name) == (root / 'private/本人宿舍授权.dormkey').read_bytes()
        else:
            audit(name, z.read(name))

app = manifest.find('application')
assert app.get(android + 'name') == '.NativeApplication'
for name in ['.AutoActivity', '.AutoService']:
    tag = 'activity' if name.endswith('Activity') else 'service'
    node = next(n for n in app.findall(tag) if n.get(android + 'name') == name)
    assert node.get(android + 'exported') == 'false'
assert not app.findall('receiver')
permissions = [n.get(android + 'name') for n in manifest.findall('uses-permission')]
assert not any('ACCESS_' in p and 'LOCATION' in p for p in permissions)
assert not any('NFC' in p or 'RECEIVE_BOOT_COMPLETED' in p for p in permissions)

jdk = Path('D:/APPs/Android Studio/jbr')
signer = Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/build-tools/36.0.0/lib/apksigner.jar'
def cert(path):
    r = subprocess.run([str(jdk / 'bin/java.exe'), '-jar', str(signer), 'verify', '--print-certs', str(path)], capture_output=True)
    if r.returncode:
        raise RuntimeError('Signature verification failed')
    match = re.search(rb'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)', r.stdout)
    if not match:
        raise RuntimeError('No signing certificate returned')
    return match.group(1).decode()
certificate = cert(apk)
assert certificate == cert(dist / 'dorm-native-open-0.2.0-test.apk')
assert certificate == cert(dist / 'dorm-native-open-0.3.0-test.apk')
protocol = json.loads((dist / 'core-tests.json').read_text(encoding='utf-8-sig'))
auto = json.loads((dist / 'automation-tests.json').read_text(encoding='utf-8-sig'))
ble = json.loads((dist / 'ble-transport-tests.json').read_text(encoding='utf-8-sig'))
access = json.loads((dist / 'access-selection-tests.json').read_text(encoding='utf-8-sig'))
port = json.loads((dist / 'port-and-import-checks.json').read_text(encoding='utf-8-sig'))
assert protocol['ok'] and auto['ok'] and port['ok'] and ble['ok'] and access['ok']
assert access['nativeApiSha256'] == hashlib.sha256((root / 'android/src/cn/dormnative/app/NativeApi.java').read_bytes()).hexdigest()
assert port['apkSha256'] == hashlib.sha256(apk.read_bytes()).hexdigest()
profiles = json.loads((dist / 'profile-tests.json').read_text(encoding='utf-8-sig'))
ui = json.loads((dist / 'ui-tests.json').read_text(encoding='utf-8-sig'))
preparation = json.loads((dist / 'preparation-tests.json').read_text(encoding='utf-8-sig'))
parallel = json.loads((dist / 'parallel-api-tests.json').read_text(encoding='utf-8-sig'))
assert profiles['ok'] and ui['ok'] and preparation['ok'] and parallel['ok']
onboarding = json.loads((dist / 'onboarding-tests.json').read_text(encoding='utf-8-sig'))
assert onboarding['ok']
for name, digest in onboarding['sources'].items():
    assert digest == hashlib.sha256((root / name).read_bytes()).hexdigest()
assert parallel['nativeApiSha256'] == hashlib.sha256((root / 'android/src/cn/dormnative/app/NativeApi.java').read_bytes()).hexdigest()
for name, digest in profiles['sources'].items():
    assert digest == hashlib.sha256((root / 'android/src/cn/dormnative/app' / name).read_bytes()).hexdigest()
result = {
    'apk': str(apk), 'apkBytes': apk.stat().st_size,
    'apkSha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
    'sameSigningCertificateAs020': True, 'sameSigningCertificateAs030': True,
    'signingCertificateSha256': certificate,
    'authorizationFilePresent': True, 'authorizationRegenerated': False,
    'sourceArchive': str(source_zip), 'sourceEntries': len(members),
    'sourceZipBytes': source_zip.stat().st_size,
    'sourceSha256': hashlib.sha256(source_zip.read_bytes()).hexdigest(),
    'sourcePrivateFilesExcluded': True, 'personalAuthorizationEmbedded': personal,
    'privateValuesOnlyInExpectedApkAsset': personal, 'sourcePrivateValuesAbsent': True,
    'autoComponentsNotExported': True, 'bootReceiverAbsent': True,
    'protocolAssertions': protocol['passed'], 'automationAssertions': auto['assertions'],
    'bleCallbackSimulationAssertions': ble['assertions'],
    'accessSelectionAssertions': access['assertions'],
    'preparationAssertions': preparation['assertions'], 'parallelApiAssertions': parallel['assertions'],
    'profileStorageAssertions': profiles['assertions'], 'uiAssertions': ui['assertions'],
    'onboardingAssertions': onboarding['assertions'],
    'profileAndroidKeystoreRuntime': 'NOT RUN', 'realMultiAccountSwitch': 'NOT RUN',
    'oldAccessBugReproduced': access['oldBugReproduced'],
    'offlineVendorComparisons': port['originalModuleOfflineComparisons'],
    'deviceRuntime': 'See device-checks.json for exact installed/tested artifact', 'screenOffRuntime': 'NOT RUN',
    'realDoorUnlock': 'User confirmed earlier 0.10.0 manual warm connection opened almost immediately; no measured milliseconds; proximity unverified',
    'firstAuthorizationExport': 'Own current official PC session collected, queried and signed; production MigrationTrust accepted offline; second user NOT RUN',
    'hardwareSettings': 'NOT IMPLEMENTED',
    'projectMiB': round(sum(p.stat().st_size for p in root.rglob('*') if p.is_file()) / 1048576, 2)
}
(dist / 'delivery-checks.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(result, ensure_ascii=False, indent=2))
