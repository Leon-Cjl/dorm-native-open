"""Allowlisted public self-service bundle; excludes all personal credentials and prebuilt APKs."""
from pathlib import Path
import base64
import hashlib
import json
import re
import zipfile

root = Path(__file__).resolve().parents[1]
dist = root / 'dist'
guide = dist / '自助签发详细教程-0.10.0'
beginner = dist / '自助签发小白教程-0.10.0'
target = dist / '宿舍快开-自助签发工具包-0.10.0.zip'
tests = json.loads((dist / 'self-service-tests.json').read_text(encoding='utf-8'))
assert tests['ok']
for name, digest in tests['sources'].items():
    assert hashlib.sha256((root / name).read_bytes()).hexdigest() == digest

files = [root / 'build.ps1', root / '.gitignore', root / 'android/AndroidManifest.xml']
for name in ['android/src', 'android/res', 'android/assets']:
    files.extend(p for p in (root / name).rglob('*') if p.is_file())
files.extend(root / name for name in ['tools/issue-dormkey.cjs', 'onboarding/session-export.cjs',
    'onboarding/observation.example.json', 'tests/ProtocolTests.java', 'tests/AutomationTests.java'])
files.extend(p for p in (root / 'self-service').iterdir() if p.is_file() and p.suffix in {'.cjs', '.ps1', '.md', '.txt'})

# Personal values used only for a local absence check; never printed or packaged.
envelope = json.loads((root / 'private/本人宿舍授权.dormkey').read_text(encoding='utf-8-sig'))
seed = json.loads(base64.b64decode(envelope['payload']))
needles = [str(x).encode() for x in [seed.get('staffID'), seed.get('roomNo'), seed.get('MAC'), envelope['payload'], envelope['signature']] if x and len(str(x)) >= 8]
def audit(name, data):
    if any(v in data for v in needles):
        raise RuntimeError('Private value in public member: ' + name)
    if re.search(rb'(?m)^-----BEGIN (?:RSA )?PRIVATE KEY-----\r?$', data):
        raise RuntimeError('Private key in public member: ' + name)

with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as z:
    for p in sorted(files):
        name = p.relative_to(root).as_posix()
        assert not any(s in {'private', 'build', 'dist', 'signing'} for s in p.relative_to(root).parts)
        assert p.suffix.lower() not in {'.pem', '.keystore', '.dormkey', '.dormrequest', '.apk'}
        data = p.read_bytes()
        audit(name, data)
        z.writestr('程序模板/' + name, data)
    for filename in ['自助签发与导入详细教程.docx', '自助签发与导入详细教程.pdf', '自助签发与导入详细教程.txt']:
        data = (guide / filename).read_bytes()
        audit(filename, data)
        z.writestr('AI专用参考/' + filename, data)
    for filename in ['00-小白照着做.docx', '00-小白照着做.pdf', '00-小白照着做.txt']:
        data = (beginner / filename).read_bytes()
        audit(filename, data)
        z.writestr(filename, data)
    for filename, archive_name in [('给AI的完整指令.txt', '01-复制给AI.txt'), ('发给提问者.txt', '发给提问者.txt')]:
        data = (root / 'self-service' / filename).read_bytes()
        audit(filename, data)
        z.writestr(archive_name, data)
    z.writestr('本人截图/', b'')
    z.writestr('给手机/', b'')
    z.writestr('先看这里.txt', '完全不会编程也先看“00-小白照着做.pdf”。\n\n你只负责：解压、打开电脑AI、自己登录和保存两张页面截图、最后手机安装。\n\n把“01-复制给AI.txt”交给能执行本机任务的电脑AI；“程序模板”与“AI专用参考”都交给AI处理，普通使用者不输入命令、不处理密钥。完成后“给手机”文件夹会出现配套安装包和本人文件。\n\n这次要用AI新做的“宿舍快开·自助版”，原先0.10.0普通安装包不能直接导入你的自签文件。全程不用找发布者签发。\n\n第一次仍需Windows电脑和相应环境；缺软件时由AI先说明占用空间和安装位置，再由你决定。本包不自动安装工具，没有纯手机一键生成或托管签名服务。\n\n本机已完成生成、编译和验签；独立自助版尚未在另一名使用者手机和实体门上验证。\n')

with zipfile.ZipFile(target) as z:
    for name in z.namelist():
        assert not name.lower().endswith(('.apk', '.pem', '.keystore', '.dormkey', '.dormrequest'))
        audit(name, z.read(name))
        if name.endswith('.docx'):
            import io
            with zipfile.ZipFile(io.BytesIO(z.read(name))) as word:
                for member in word.namelist():
                    audit(name + '/' + member, word.read(member))
    entries = len(z.namelist())

report = {'ok': True, 'archive': str(target), 'bytes': target.stat().st_size,
          'sha256': hashlib.sha256(target.read_bytes()).hexdigest(), 'entries': entries,
          'privatePublisherKeyIncluded': False, 'personalAuthorizationIncluded': False,
          'prebuiltApkIncluded': False, 'privateValuesAbsent': True,
          'selfServiceAssertions': tests['assertions'], 'publisherNeededForSelfIssuance': False,
          'originalPublicApkCompatibleWithSelfKeys': False, 'beginnerGuidePages': 4, 'aiReferencePages': 6,
          'phoneImportTest': 'NOT RUN', 'newUserPhysicalDoorTest': 'NOT RUN'}
(dist / 'self-service-package-checks.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(report, ensure_ascii=False, indent=2))
