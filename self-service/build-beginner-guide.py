"""Button-by-button novice guide. Technical commands remain in the separate AI prompt/reference."""
from pathlib import Path
import subprocess
from docx import Document
from docx.shared import Cm, Pt, RGBColor
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.opc.constants import RELATIONSHIP_TYPE as RT

root = Path(__file__).resolve().parents[1]
out = root / 'dist/自助签发小白教程-0.10.0'
out.mkdir(parents=True, exist_ok=True)
PAGES = [
('完全不会编程，就从这里开始', [
('先准备这三样', '一台Windows电脑、一部你自己的安卓手机，以及平时能正常打开自己宿舍门的“公寓智锁”账号。你不用懂签名、密钥或命令，这些由电脑AI处理。只有手机、没有能运行本机任务的电脑AI，暂时做不了这条流程。'),
('先记住一件事', '这次要安装AI给你新做的“宿舍快开·自助版”。之前发布的普通安装包，不能直接使用你自己生成的宿舍文件。不要在旧版里反复导入、改文件名。'),
('第1步　下载并解压', '① 把发布者给你的“宿舍快开-自助签发工具包-0.10.0.zip”下载到电脑。\n② 打开电脑的“下载”文件夹，找到这个压缩包。\n③ 在压缩包上点鼠标右键，选“全部解压”，再点“解压”。\n④ 打开解压后的文件夹。看到“00-小白照着做.pdf”“01-复制给AI.txt”“程序模板”“本人截图”，就找对了。不要在压缩包预览窗口里运行。'),
('第2步　打开一个电脑AI助手', '你已经有能读取本机文件、运行本机命令的AI助手，可以继续下一页。没有的话，可按TRAE官方桌面端入门页下载Windows版并按提示安装、登录；这是一个可选示例，不要求你必须用它。普通聊天网页不等于能操作你的电脑。'),
('link', ('TRAE官方桌面端入门：点这里打开', 'https://docs.trae.cn/ide_get-started-with-trae')),
('安装前看清楚', '缺少开发工具时，AI会先告诉你要装什么、装到哪个盘、占多少空间、是否常驻或自启，再由你决定。首次环境下载可能达到GB级，不是这个小压缩包本身的大小。没有实际运行能力的AI无法靠聊天完成，不要把“给了代码”当成“做完了”。'),
]),
('把任务交给AI，你只提供自己的截图', [
('第3步　让AI打开刚才那个文件夹', '以TRAE桌面端为例，点“打开文件夹”，选刚刚解压出来的整个文件夹。不是选.zip文件，也不只是随便选一个脚本。然后在AI对话框粘贴下方这段话并发送。其他电脑AI也可用同一段话，前提是已允许它访问该文件夹并执行本机任务。'),
('copy', '我完全不会编程。请先读取当前文件夹里的“01-复制给AI.txt”，按里面的步骤帮我实际完成。请先检查你能否读取本机文件并执行Windows命令；不能就直接说不能。需要我点哪里，请明确告诉我按钮名称。我只负责本人登录、保存截图、同意必要安装和最后手机安装，不要让我输入代码，也不用找发布者签发。'),
('第4步　自己登录公寓智锁', '在电脑微信打开你平时使用的“公寓智锁”，自己登录本人的账号。先打开“我的”页面，让学校全称和本人学号显示出来。再打开“我的房间”页面，确认完整房间号能看见。密码、短信验证码由你自己输入，不发给脚本。'),
('第5步　把这两页各保存一张图', '① 停在“我的”页，按键盘上的Windows标志键＋Shift＋S，拖动鼠标框出小程序窗口。\n② 点右下角截图通知，打开截图后按Ctrl＋S。保存位置选择解压文件夹里的“本人截图”，文件名填“账号.png”。\n③ 回到“我的房间”页，重复一次，保存为“房间.png”。\n④ 把这两张图拖进AI对话框，并发：“图片已保存在本人截图，请继续处理。”\n截图只要自己的小程序页面，不要框进聊天窗口或别人的开门记录。'),
('link', ('示例AI的图片发送方法：点这里查看', 'https://docs.trae.cn/ide_chat')),
('不知道下一步点什么时', '直接对AI说：“我不会操作，请按我现在的界面，一步一步告诉我点哪里。”如果截图通知没出现，打开Windows开始菜单，搜索“截图工具”，在里面截图后保存到上述文件夹即可。'),
]),
('等AI做好，再把两个文件放到手机', [
('第6步　让AI继续做完', 'AI会自动检查环境、读取你的页面资料、生成并打包你自己的应用。遇到执行本次任务的“允许/运行”提示，按当前AI工具的提示处理。缺软件时先看它说明的空间和安装位置；不会操作就让它一步一步带你点。你不需要读命令，也不需要自己签名。'),
('看到这两个文件才算文件准备好', 'AI完成后，应告诉你打开“给手机”文件夹。里面应有：\n① 宿舍快开-自助版.apk——手机安装包。\n② 本人宿舍授权.dormkey——你自己的宿舍文件。\n只给了文字、代码或“.dormrequest”，还没有做到这一步。直接回复AI：“请继续实际做完，把这两个结果文件放到给手机文件夹。”'),
('第7步　用数据线复制到手机', '① 用数据线连接电脑和手机，解锁手机。\n② 手机出现USB用途提示时，选择“传输文件”；若默认仅充电，点通知中的USB选项更改用途。这里只传文件，不需要USB调试。\n③ 电脑按Windows标志键＋E，打开“此电脑”，双击你的手机，再打开“内部存储”和“Download/下载”。\n④ 在电脑“给手机”文件夹选中上述两个文件，按Ctrl＋C；切回手机的Download文件夹，按Ctrl＋V。\n没有可用数据线时，可让AI按你实际设备教你传文件，不要把本人宿舍文件公开发群。'),
('第8步　在手机安装', '手机打开“文件管理”，进入“下载”，点“宿舍快开-自助版.apk”，按手机提示安装。若提示当前文件来源未获安装权限，只按系统提示处理这个来源；其他不明安全提示可截图问AI，不要关闭整机安全防护。安装完打开名称带“自助版”的应用。'),
('别打开错应用', '你可以同时装着旧版和自助版。本教程使用的是AI给你新做的“宿舍快开·自助版”，不是先前下载的普通版。你的宿舍文件要和这次AI做的安装包配套使用。'),
]),
('选中文件，然后由你本人试一次开门', [
('第9步　选中自己的宿舍文件', '打开自助版，点“导入我的宿舍”。系统出现文件选择界面后，进入“下载”，选“本人宿舍授权.dormkey”，等待自动绑定。不用再找人签名，也不用点“核对并切换”。如果提示签名不匹配，先确认打开的是你自己生成的自助版、文件也来自同一个“给手机”文件夹。'),
('第10步　到自己的门旁试一次', '① 保持手机蓝牙和网络开启，系统询问“附近设备”“通知”权限时按需要允许。\n② 若以前用过旧版，先点旧版运行通知里的“停止预连接”；同时退出官方小程序的开锁页面，避免抢连。没有这些运行项就跳过。\n③ 打开自助版，等首页提示“可以快速开门”。\n④ 由你本人点“开门”。门锁响应后及时推门，实体门打开才算这一步成功。不要让AI代你点开门。'),
('以后还要做这些吗？', '首次设置完成后，日常只需打开自助版并点开门。预连接会提前尝试，手动开门成功后结束本次预连接。换手机、换宿舍或更新时，把原来的解压文件夹和生成的自助目录留着，交给你的AI继续处理，不用找发布者签发。'),
('遇到错误，就复制这句话给AI', '“我不会编程。请根据实际报错修复并继续原来的任务，保留我已经生成的自助目录，不要重新生成密钥，也不要让我抄命令。最后仍然把配套安装包和本人文件放到给手机文件夹。”'),
('你不需要阅读哪些文件', '“程序模板”里的代码和“AI专用参考”里的技术教程都交给AI。普通使用者只照这份教程点按钮、自己登录、保存两张图、安装两个结果文件即可。'),
('当前验证情况', '这套工具已在本机完成生成、编译和签名校验。独立自助版尚未在另一名使用者的手机和实体门上验证，文件做好不等于已开门。自动接近仍为试验功能。示例AI的界面操作按官方文档说明，未声称在该AI中完整实测本流程。'),
])]

doc = Document()
sec = doc.sections[0]
sec.page_width, sec.page_height = Cm(21), Cm(29.7)
sec.top_margin = sec.bottom_margin = Cm(1.65)
sec.left_margin = sec.right_margin = Cm(1.8)
style = doc.styles['Normal']
style.font.name = '微软雅黑'
style._element.rPr.rFonts.set(qn('w:eastAsia'), '微软雅黑')
style.font.size = Pt(10.5)
style.paragraph_format.line_spacing = 1.15
style.paragraph_format.space_after = Pt(6)
for name, size in [('Title', 21), ('Heading 2', 11.5)]:
    s = doc.styles[name]
    s.font.name = '微软雅黑'
    s._element.rPr.rFonts.set(qn('w:eastAsia'), '微软雅黑')
    s.font.size = Pt(size)
    s.font.color.rgb = RGBColor.from_string('245BDB')
    s.paragraph_format.keep_with_next = True
    s.paragraph_format.space_before = Pt(7)
    s.paragraph_format.space_after = Pt(4)
text_lines = []
for i, (title, blocks) in enumerate(PAGES):
    if i:
        doc.add_page_break()
    doc.add_paragraph(title, 'Title')
    doc.add_paragraph(f'宿舍快开 · 小白照着做  |  {i+1} / {len(PAGES)}', 'Subtitle')
    text_lines.extend([title, ''])
    for head, body in blocks:
        if head == 'link':
            label, url = body
            p = doc.add_paragraph()
            h = OxmlElement('w:hyperlink')
            h.set(qn('r:id'), p.part.relate_to(url, RT.HYPERLINK, is_external=True))
            rr = OxmlElement('w:r')
            prop = OxmlElement('w:rPr')
            color = OxmlElement('w:color')
            color.set(qn('w:val'), '245BDB')
            prop.append(color)
            rr.append(prop)
            t = OxmlElement('w:t')
            t.text = label
            rr.append(t)
            h.append(rr)
            p._p.append(h)
            text_lines.append(label + '：' + url)
        elif head == 'copy':
            p = doc.add_paragraph(body)
            shade = OxmlElement('w:shd')
            shade.set(qn('w:fill'), 'ECF3FF')
            p._p.get_or_add_pPr().append(shade)
            p.paragraph_format.keep_together = True
            text_lines.extend(['【复制这段给AI】', body, ''])
        else:
            doc.add_paragraph(head, 'Heading 2')
            doc.add_paragraph(body)
            text_lines.extend([head, body, ''])
foot = sec.footer.paragraphs[0]
foot.alignment = 2
foot.add_run('只照着点，不用写代码  |  ')
field = OxmlElement('w:fldSimple')
field.set(qn('w:instr'), 'PAGE')
foot._p.append(field)
filename = out / '00-小白照着做.docx'
doc.save(filename)
(out / '00-小白照着做.txt').write_text('\n'.join(text_lines), encoding='utf-8')
profile = (root / 'build/lo-beginner-guide').as_uri()
subprocess.run([r'C:\Program Files\LibreOffice\program\soffice.exe', '-env:UserInstallation=' + profile,
                '--headless', '--convert-to', 'pdf', '--outdir', str(out), str(filename)],
               check=True, capture_output=True, timeout=55)
print('Created button-by-button DOCX/PDF/TXT novice guide')
