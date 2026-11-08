"""Generate the public first-authorization guide. Uses installed python-docx and LibreOffice only."""
from pathlib import Path
from html import escape
import subprocess
from docx import Document
from docx.shared import Cm, Pt, RGBColor
from docx.oxml import OxmlElement
from docx.oxml.ns import qn

root = Path(__file__).resolve().parents[1]
out = root / 'dist' / '发布包-0.10.0'
out.mkdir(parents=True, exist_ok=True)
pages = [('取得文件，三步开始用', [
    ('1　自己登录', '在电脑微信打开“公寓智锁”，登录本人的账号。准备好“我的”和“我的房间”页面，AI需要从中读取学校全称、本人学号和完整房间号。不要把微信密码或验证码交给工具。'),
    ('2　把工具包交给 AI', '把发布包内“AI首次授权导出工具包”整个文件夹及“给AI的指令.txt”交给能操作本机电脑的 AI。让它实际读取页面、处理资料。不能操作电脑的 AI，需要你本人提供对应官方页面截图。'),
    ('3　收到文件，选中即绑定', 'AI整理的资料交给维护者后，其工具会自动签出你的 .dormkey。把返回文件保存到手机，安装 public.apk，打开“导入我的宿舍”并选中文件，即自动绑定；已有宿舍时从“设置 → 添加账号或宿舍”进入。保持网络开启，不再需要“核对并切换”。'),
    ('哪些步骤已经省掉', '无需人工逐项核对，也不用复制摘要确认。资料格式、签名、有效期和当前房间权限都由程序自动校验。.dormrequest 是 AI 处理的待签发资料，不能直接导入；目前仍需维护者返回签名文件，尚不是完全自助登录。'),
    ('日常开门', '打开蓝牙、网络，允许“附近设备”和通知。打开开门页会主动预连接，退到后台也继续尝试；连上后保持。看到“可以快速开门”即可点“开门”。门锁响应后立即推门，测试门锁的开门窗口由用户反馈约5秒。手动开门成功后，本次预连接自动结束。'),
    ('停止与恢复', '首页和运行通知都可停止预连接，需要时点“开启预连接”。系统终止进程、关闭蓝牙或收回权限可能中断。连不上时先退出官方开锁页面、触摸唤醒门锁，再记录提示；设置里可复制诊断信息。'),
    ('发布与文件保管', '对外只发送 public.apk 和公开工具包。个人 APK、本人 .dormkey、页面证据及签名私钥单独保留。导出脚本只依赖已有 Node.js，不安装 npm 依赖、不常驻或开机自启。'),
    ('验证进展', '已从维护者本人当前官方会话实际取得并签出新文件，通过 App 使用的签名校验代码；另一名新用户首次导入尚未验证。用户已确认预连接后的手动开门“几乎立即”。自动接近仍为试验功能，NFC发卡与门锁硬件设置未接入。'),
])]
doc = Document()
section = doc.sections[0]
section.page_width, section.page_height = Cm(21), Cm(29.7)
section.top_margin, section.bottom_margin = Cm(1.65), Cm(1.65)
section.left_margin = section.right_margin = Cm(1.85)
normal = doc.styles['Normal']
normal.font.name = '微软雅黑'
normal._element.rPr.rFonts.set(qn('w:eastAsia'), '微软雅黑')
normal.font.size = Pt(10)
normal.paragraph_format.line_spacing = 1.18
normal.paragraph_format.space_after = Pt(5)
for style_name, size in [('Title', 23), ('Heading 2', 11)]:
    style = doc.styles[style_name]
    style.font.name = '微软雅黑'
    style._element.rPr.rFonts.set(qn('w:eastAsia'), '微软雅黑')
    style.font.size = Pt(size)
    style.font.color.rgb = RGBColor.from_string('245BDB')
    style.paragraph_format.space_before = Pt(7)
    style.paragraph_format.space_after = Pt(4)
    style.paragraph_format.keep_with_next = True
html_pages, plain = [], []
for index, (title, sections) in enumerate(pages):
    if index:
        doc.add_page_break()
    doc.add_paragraph(title, 'Title')
    doc.add_paragraph('宿舍快开 0.10.0 · AI 辅助取得授权与导入', 'Subtitle')
    plain.append(title)
    parts = ['<section class="page"><h1>' + escape(title) + '</h1><p class="subtitle">宿舍快开 0.10.0</p>']
    for heading, body in sections:
        doc.add_paragraph(heading, 'Heading 2')
        doc.add_paragraph(body)
        plain.extend([heading, body, ''])
        parts.append('<h2>' + escape(heading) + '</h2><p>' + escape(body) + '</p>')
    html_pages.append(''.join(parts) + '</section>')
footer = section.footer.paragraphs[0]
footer.alignment = 2
footer.add_run('宿舍快开 · ')
field = OxmlElement('w:fldSimple')
field.set(qn('w:instr'), 'PAGE')
footer._p.append(field)
filename = out / '导入自己的宿舍.docx'
doc.save(filename)
(root / 'docs/导入教程.txt').write_text('\n'.join(plain), encoding='utf-8')
(out / '导入自己的宿舍.html').write_text('<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>取得本人授权与导入</title><style>body{font:16px/1.7 system-ui;color:#20242b;background:#f2f5f8;margin:0}.page{max-width:800px;margin:24px auto;background:white;padding:36px;box-sizing:border-box}h1{color:#245bdb;font-size:28px}h2{font-size:18px;margin:20px 0 5px}p{margin:5px 0}.subtitle{color:#647084}@media print{.page{page-break-after:always;margin:0;padding:0}body{background:white}}</style>' + ''.join(html_pages) + '</html>', encoding='utf-8')
profile = (root / 'build/tutorial-lo-final').as_uri()
cmd = [r'C:\Program Files\LibreOffice\program\soffice.exe', '-env:UserInstallation=' + profile, '--headless', '--convert-to', 'pdf', '--outdir', str(out), str(filename)]
subprocess.run(cmd, check=True, timeout=55, capture_output=True)
print('Created public DOCX/HTML/PDF acquisition and import guide')
