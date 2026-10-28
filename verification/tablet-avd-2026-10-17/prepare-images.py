from pathlib import Path
from PIL import Image, ImageDraw, ImageOps

source = Path('/tmp/marisuki-avd-run/images')
target = Path('/tmp/marisuki-avd-run/ready')
target.mkdir(parents=True, exist_ok=True)
names = [
    'piattos', 'nova', 'chippy', 'oreo', 'coca_cola', 'milo', 'nescafe',
    'nutella', 'del_monte', 'datu_puti', 'lucky_me', 'purefoods', 'heinz_beans',
]
thumbs = []
for index, name in enumerate(names, start=1):
    paths = list(source.glob(f'{name}.*'))
    if not paths:
        raise FileNotFoundError(f'Missing image for {name}')
    image = ImageOps.exif_transpose(Image.open(paths[0])).convert('RGB')
    image = ImageOps.contain(image, (560, 560), Image.Resampling.LANCZOS)
    canvas = Image.new('RGB', (600, 600), 'white')
    canvas.paste(image, ((600 - image.width) // 2, (600 - image.height) // 2))
    out = target / f'{index:02d}-{name}.jpg'
    canvas.save(out, 'JPEG', quality=90, optimize=True)
    print(f'{index:02d} {name:12} {image.width}x{image.height} -> {out.stat().st_size} bytes')
    thumbs.append((name, canvas.copy()))

sheet = Image.new('RGB', (600 * 4, 640 * 4), '#eef5f1')
draw = ImageDraw.Draw(sheet)
for i, (name, image) in enumerate(thumbs):
    x, y = (i % 4) * 600, (i // 4) * 640
    sheet.paste(image, (x, y))
    draw.text((x + 18, y + 606), f'{i + 1:02d}  {name.replace("_", " ")}', fill='#13352d')
sheet.save('/tmp/marisuki-avd-run/contact-sheet.jpg', quality=88, optimize=True)
