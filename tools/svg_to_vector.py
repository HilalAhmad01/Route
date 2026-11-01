import pathlib
import re
import sys
import xml.etree.ElementTree as ET


def to_path(el):
    tag = el.tag.split('}')[-1]
    a = el.attrib
    if tag == 'path':
        return a['d']
    if tag == 'circle':
        cx, cy, r = (float(a[k]) for k in ('cx', 'cy', 'r'))
        return f"M{cx - r},{cy}a{r},{r} 0 1,0 {2 * r},0a{r},{r} 0 1,0 {-2 * r},0"
    if tag == 'line':
        return f"M{a['x1']},{a['y1']}L{a['x2']},{a['y2']}"
    if tag in ('polyline', 'polygon'):
        pts = re.split(r'[\s,]+', a['points'].strip())
        xy = [f"{pts[i]},{pts[i + 1]}" for i in range(0, len(pts), 2)]
        return 'M' + 'L'.join(xy) + ('Z' if tag == 'polygon' else '')
    if tag == 'rect':
        x, y, w, h = (float(a.get(k, 0)) for k in ('x', 'y', 'width', 'height'))
        r = float(a.get('rx', 0))
        if r:
            return (f"M{x + r},{y}h{w - 2 * r}a{r},{r} 0 0 1 {r},{r}v{h - 2 * r}a{r},{r} 0 0 1 {-r},{r}"
                    f"h{-(w - 2 * r)}a{r},{r} 0 0 1 {-r},{-r}v{-(h - 2 * r)}a{r},{r} 0 0 1 {r},{-r}z")
        return f"M{x},{y}h{w}v{h}h{-w}z"
    return None


def main(src, dst):
    for svg in sorted(pathlib.Path(src).glob('*.svg')):
        root = ET.fromstring(svg.read_text())
        paths = [p for p in (to_path(e) for e in root.iter()) if p]
        body = '\n'.join(
            f'    <path android:pathData="{p}" android:strokeColor="#FFFFFFFF" android:strokeWidth="1.5"'
            f' android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000"/>'
            for p in paths)
        name = 'ic_' + svg.stem.replace('-', '_')
        out = pathlib.Path(dst) / f'{name}.xml'
        out.write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n'
            f'{body}\n</vector>\n')
        print(name)


if __name__ == '__main__':
    main(*sys.argv[1:])
