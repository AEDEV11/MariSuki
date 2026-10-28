from pathlib import Path
from urllib.request import Request, urlopen

OUT = Path('/tmp/marisuki-avd-run/images')
OUT.mkdir(parents=True, exist_ok=True)
items = {
    'piattos': 'https://shopsuki.ph/cdn/shop/files/4800016671500_1024x.jpg?v=1753069252',
    'nova': 'https://allmartshop.com/cdn/shop/files/NovaCountryCheddar40g_1.jpg?v=1710087525&width=1946',
    'chippy': 'https://admin.landmark.ph/wp-content/uploads/2024/07/10924.jpg',
    'oreo': 'https://digitalcontent.api.tesco.com/v2/media/ghs/6c258615-0cd2-4f5d-8312-fecceee52e29/113402be-ca1a-4c27-aed8-6376da0d67c5_1556383838.jpeg?h=960&w=960',
    'coca_cola': 'https://www.cocacolaep.com/assets/Uploads/key-statistics/330ML-CAN.png',
    'milo': 'https://www.milo.com.ph/sites/default/files/2025-05/Powder_300g_Front.png',
    'nescafe': 'https://spar2u.lk/cdn/shop/files/12448899NescafeClassicJar100g.png?v=1756958482&width=1066',
    'nutella': 'https://img.ananinja.com/media/bra-public-files/services-admin/files/5f95b230-cc8f-4212-b74b-b9b248e6f36e?q=90&w=3840',
    'del_monte': 'https://alinggemma.com/cdn/shop/files/52.png?v=1754531360&width=416',
    'datu_puti': 'https://zbga.shopsuki.ph/cdn/shop/files/4801668500224_1_1024x.jpg?v=1711440996',
    'lucky_me': 'https://cdn.webshopapp.com/shops/225503/files/492437422/lucky-me-pancit-canton-original-60g.webp',
    'purefoods': 'https://shopgaisano.com/cdn/shop/products/1uMgQe6PeTGiwdMvYST2ZlygaA2nKRTfu_w868-h1010_fd683a1b-51c0-4490-b98d-087ca966bebd_1024x1024.jpg?v=1603779059',
    'heinz_beans': 'https://images.openfoodfacts.org/images/products/500/015/702/4671/front_en.433.400.jpg',
}
extensions = [(b'\xff\xd8\xff', '.jpg'), (b'\x89PNG\r\n\x1a\n', '.png'), (b'RIFF', '.webp')]
for name, url in items.items():
    request = Request(url, headers={'User-Agent': 'Mozilla/5.0 MariSuki tablet demo verification'})
    try:
        with urlopen(request, timeout=25) as response:
            data = response.read(8 * 1024 * 1024 + 1)
        if len(data) > 8 * 1024 * 1024:
            raise ValueError('image is too large')
        ext = next((ext for magic, ext in extensions if data.startswith(magic)), None)
        if data.startswith(b'RIFF') and data[8:12] != b'WEBP':
            ext = None
        if not ext:
            raise ValueError(f'not a supported image ({data[:12]!r})')
        path = OUT / f'{name}{ext}'
        path.write_bytes(data)
        print(f'{name}: {path.name} ({len(data)} bytes)')
    except Exception as error:
        print(f'{name}: FAILED: {error}')
