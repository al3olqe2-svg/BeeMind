"""Rebuild OFL-licensed bilingual fonts: Manrope Latin + Tajawal Arabic.
Requires fonttools. Source upstream assets and OFL notices live under licenses/fonts.
The merged family is renamed BeeMind Text to respect Tajawal's reserved name.
"""
from pathlib import Path
from tempfile import TemporaryDirectory
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools import subset
from fontTools.merge import Merger

root = Path(__file__).resolve().parents[1]
sources = root / 'licenses/fonts'
for weight, style in [(400, 'regular'), (700, 'bold')]:
    with TemporaryDirectory() as tmp:
        latin = instantiateVariableFont(TTFont(sources / 'Manrope.ttf'), {'wght': weight}, inplace=False)
        arabic = TTFont(sources / f'Tajawal-{style}.ttf')
        # Give each script one source, including shared punctuation from Manrope.
        options = subset.Options()
        sub = subset.Subsetter(options=options)
        sub.populate(unicodes=[c for c in arabic.getBestCmap() if c >= 0x600])
        sub.subset(arabic)
        # Fonts use different units-per-em; normalize before merging.
        from fontTools.ttLib.scaleUpem import scale_upem
        scale_upem(arabic, latin['head'].unitsPerEm)
        paths = [str(Path(tmp) / f'{i}.ttf') for i in range(2)]
        for font, path in zip([latin, arabic], paths):
            if 'MATH' in font: del font['MATH']
            font.save(path)
        merged = Merger().merge(paths)
        for name_id, value in [(1, 'BeeMind Text'), (2, style.title()), (4, f'BeeMind Text {style.title()}'), (6, f'BeeMindText-{style.title()}'), (16, 'BeeMind Text'), (17, style.title())]:
            merged['name'].setName(value, name_id, 3, 1, 0x409)
        merged.save(root / f'app/src/main/res/font/beemind_{style}.ttf')
