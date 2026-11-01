import pathlib

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "tools" / "raw" / "fonts"
OUT = ROOT / "app" / "src" / "main" / "res" / "font"

UNICODES = (
    list(range(0x20, 0x7F)) + list(range(0xA0, 0x100))
    + [0x2013, 0x2014, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2026, 0x20B9, 0x2192, 0x2248]
)


def shrink(name, axes=None):
    font = TTFont(SRC / name)
    opts = subset.Options()
    opts.layout_features = ["kern", "liga", "calt", "tnum", "pnum", "case"]
    opts.name_IDs = ["*"]
    opts.notdef_outline = True
    opts.drop_tables += ["DSIG"]
    s = subset.Subsetter(opts)
    s.populate(unicodes=UNICODES)
    s.subset(font)
    if axes:
        font = instancer.instantiateVariableFont(font, axes)
    out = OUT / name
    font.save(out)
    print(f"{name}: {(SRC / name).stat().st_size // 1024} KB -> {out.stat().st_size // 1024} KB")


shrink("plex_sans.ttf", axes={"wdth": 100, "wght": (400, 600)})
shrink("plex_mono_regular.ttf")
shrink("plex_mono_medium.ttf")
