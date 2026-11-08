"""Export a vector-only original doorway mark. No source app artwork is modified."""
from pathlib import Path
import pymupdf as fitz
from PIL import Image, ImageDraw, ImageFont
root=Path(__file__).resolve().parents[1]
out=root/'branding'
out.mkdir(exist_ok=True)
res=root/'android/res'
paths=[
 ('M42 32h21q13 0 13 13v12h-9V45q0-4-4-4H42Z','#245bdb','#245bdb'),
 ('M32 51h9v12q0 4 4 4h21v9H45q-13 0-13-13Z','#20b8ab','#20b8ab')]

def vector(mono=False,background=False,adaptive=False):
    body='<path android:fillColor="#FFFFFF" android:pathData="M0 0h108v108H0Z"/>' if background else ''
    if adaptive: body+='<group android:pivotX="54" android:pivotY="54" android:scaleX=".92" android:scaleY=".92">'
    for d,c1,c2 in paths:
        if mono: body+=f'<path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{d}"/>'
        else: body+=f'<path android:fillColor="{c1}" android:fillType="evenOdd" android:pathData="{d}"/>'
    if adaptive: body+='</group>'
    return f'<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">{body}</vector>\n'
def write(rel,text):
    p=res/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8')
write('drawable/ic_launcher.xml',vector(background=True))
write('drawable/ic_launcher_foreground.xml',vector(adaptive=True))
write('drawable/ic_launcher_monochrome.xml',vector(mono=True,adaptive=True))
write('drawable/ic_launcher_background.xml','<shape xmlns:android="http://schemas.android.com/apk/res/android"><solid android:color="#FFFFFF"/></shape>')
write('drawable/ic_tile.xml','<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#FFFFFFFF" android:pathData="M6,3h12a1,1 0,0 1,1 1v16h-3V6H8v14H5V4a1,1 0,0 1,1 -1M10,8l4,-1v14l-4,1Z"/></vector>')
for name in ['ic_launcher','ic_launcher_round']:
    write(f'mipmap-anydpi/{name}.xml',vector(background=True))
    for api in [26,33]:
        mono='<monochrome android:drawable="@drawable/ic_launcher_monochrome"/>' if api==33 else ''
        write(f'mipmap-anydpi-v{api}/{name}.xml',f'<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@drawable/ic_launcher_background"/><foreground android:drawable="@drawable/ic_launcher_foreground"/>{mono}</adaptive-icon>')
defs=''.join(f'<linearGradient id="g{i}" gradientUnits="userSpaceOnUse" x1="29" y1="24" x2="76" y2="90"><stop stop-color="{c1}"/><stop offset="1" stop-color="{c2}"/></linearGradient>' for i,(d,c1,c2) in enumerate(paths))
def svg(mono=False,adaptive=False):
    body=''.join(f'<path fill="{("#fff" if mono else c1)}" fill-rule="evenodd" d="{d}"/>' for i,(d,c1,c2) in enumerate(paths))
    if adaptive:body='<g transform="translate(4.32 4.32) scale(.92)">'+body+'</g>'
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 108 108"><defs>{defs}</defs>{body}</svg>'
(out/'icon-master.svg').write_text(svg(),encoding='utf-8')
def render(source,size):
    doc=fitz.open(stream=source.encode(),filetype='svg')
    pix=doc[0].get_pixmap(matrix=fitz.Matrix(size/1024,size/1024),alpha=True)
    return Image.frombytes('RGBA',[pix.width,pix.height],pix.samples)
master=render(svg(),1024)
canvas=Image.new('RGBA',(1024,1024),'white');canvas.alpha_composite(master);canvas.convert('RGB').save(out/'app-icon-1024.png')
preview=Image.new('RGB',(1100,500),'#f5f5f7')
draw=ImageDraw.Draw(preview)
font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',20)
draw.text((36,24),'宿舍快开 · 新图标 / 圆形裁切 / 单色主题',font=font,fill='#17171b')
for x,kind in [(36,'square'),(310,'circle'),(584,'mono')]:
    size=240
    tile=Image.new('RGBA',(size,size),(0,0,0,0));d=ImageDraw.Draw(tile)
    if kind=='square':d.rounded_rectangle((0,0,size-1,size-1),radius=56,fill='white');mark=render(svg(),size)
    else:
        d.ellipse((0,0,size-1,size-1),fill='#304976' if kind=='mono' else 'white')
        # Launcher masks central 72/108 viewport; show the actual adaptive safe area.
        large=render(svg(mono=kind=='mono',adaptive=True),360)
        mark=large.crop((60,60,300,300))
    tile.alpha_composite(mark);preview.paste(tile,(x,85),tile)
for x,size in [(890,96),(1000,48),(1008,32)]:
    y=100 if size>32 else 220
    tile=Image.new('RGBA',(size,size),'white');tile.alpha_composite(render(svg(),size));preview.paste(tile,(x,y))
draw.text((36,367),'双折角 · 中央门缝 / 更宽图标留白',font=font,fill='#62626b')
draw.text((36,408),'矢量资源预览，非手机桌面实拍',font=font,fill='#62626b')
preview.save(out/'icon-preview.png')
print('Exported launcher vectors, Android 8/13 adaptive layers and icon previews.')

