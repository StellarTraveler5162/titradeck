"""Export editable SVG artwork directly from Android's vector resources (stdlib only)."""
from pathlib import Path
from html import escape
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DRAWABLE = ROOT / "app/src/main/res/drawable"
OUTPUT = ROOT / "docs/branding"
ANDROID = "{http://schemas.android.com/apk/res/android}"


def vector_content(name, prefix, tint=None):
    definitions = []

    def attr(node, key, default=None):
        return node.get(ANDROID + key, default)

    def render(node):
        if node.tag == "path":
            fill = tint or attr(node, "fillColor", "none")
            gradient = node.find(".//gradient")
            if gradient is not None and tint is None:
                gid = f"{prefix}-gradient-{len(definitions)}"
                definitions.append(
                    f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" '
                    f'x1="{attr(gradient, "startX")}" y1="{attr(gradient, "startY")}" '
                    f'x2="{attr(gradient, "endX")}" y2="{attr(gradient, "endY")}">'
                    f'<stop stop-color="{attr(gradient, "startColor")}"/>'
                    f'<stop offset="1" stop-color="{attr(gradient, "endColor")}"/>'
                    '</linearGradient>'
                )
                fill = f"url(#{gid})"
            rule = "evenodd" if attr(node, "fillType") == "evenOdd" else "nonzero"
            return f'<path fill="{fill}" fill-rule="{rule}" d="{attr(node, "pathData")}"/>'
        if node.tag == "group":
            px, py = attr(node, "pivotX", "0"), attr(node, "pivotY", "0")
            sx, sy = attr(node, "scaleX", "1"), attr(node, "scaleY", "1")
            transform = f"translate({px} {py}) scale({sx} {sy}) translate(-{px} -{py})"
            return f'<g transform="{transform}">{"".join(render(child) for child in node)}</g>'
        return "".join(render(child) for child in node)

    body = render(ET.parse(DRAWABLE / name).getroot())
    return "".join(definitions), body


def svg(body, defs="", width=108, height=108, title="TitraDeck"):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
            f'viewBox="0 0 {width} {height}" role="img">'
            f'<title>{escape(title)}</title><defs>{defs}</defs>{body}</svg>')


def label(x, y, value, size=16, color="#50645F", weight=400):
    return (f'<text x="{x}" y="{y}" font-family="Microsoft YaHei, sans-serif" '
            f'font-size="{size}" font-weight="{weight}" fill="{color}">{escape(value)}</text>')


OUTPUT.mkdir(parents=True, exist_ok=True)
brand_defs, brand = vector_content("ic_brand_logo.xml", "brand")
bg_defs, background = vector_content("ic_launcher_background.xml", "background")
fg_defs, foreground = vector_content("ic_launcher_foreground.xml", "foreground")
mono_defs, mono_light = vector_content("ic_launcher_monochrome.xml", "mono", "#174B3D")
_, mono_dark = vector_content("ic_launcher_monochrome.xml", "mono-dark", "#B7EED6")

(OUTPUT / "titradeck-icon.svg").write_text(svg(brand, brand_defs), encoding="utf-8")
(OUTPUT / "titradeck-monochrome.svg").write_text(svg(mono_light, mono_defs), encoding="utf-8")

definitions = brand_defs + bg_defs + fg_defs
definitions += f'<g id="brand">{brand}</g><g id="adaptive">{background}{foreground}</g>'
definitions += '<clipPath id="circle"><circle cx="54" cy="54" r="36"/></clipPath>'
definitions += '<clipPath id="rounded"><rect x="18" y="18" width="72" height="72" rx="18"/></clipPath>'


def badge(x, y, size, shape="rounded", monochrome=None):
    # Launchers crop the 108 dp overscan canvas to the central 72 dp viewport.
    body = '<use href="#adaptive"/>'
    if monochrome:
        light = monochrome == "light"
        color = "#D9F1E4" if light else "#234C40"
        body = f'<rect width="108" height="108" fill="{color}"/>' + (mono_light if light else mono_dark)
    return (f'<svg x="{x}" y="{y}" width="{size}" height="{size}" viewBox="18 18 72 72">'
            f'<g clip-path="url(#{shape})">{body}</g></svg>')


board = '<rect width="1120" height="680" rx="28" fill="#F3F6F2"/>'
board += label(56, 62, "TitraDeck", 30, "#123D32", 700)
board += label(56, 94, "滴定液滴 × 烧瓶 × 计算等号", 16)
board += '<rect x="40" y="126" width="388" height="514" rx="24" fill="#FFFFFF"/>'
board += '<svg x="100" y="164" width="268" height="268" viewBox="0 0 108 108"><use href="#brand"/></svg>'
board += label(80, 486, "化学，有据可算", 24, "#163D32", 700)
board += label(80, 522, "深墨绿 / 薄荷绿 / 琥珀色", 16)
for index, color in enumerate(["#12302E", "#E5FFF6", "#5EE5B4", "#FFC76D"]):
    board += f'<rect x="{80 + index * 64}" y="560" width="48" height="20" rx="10" fill="{color}"/>'
board += label(472, 158, "桌面图标 · 自适应裁切", 17, "#163D32", 600)
board += badge(472, 184, 128) + badge(638, 184, 128, "circle")
board += label(800, 158, "系统主题色图标", 17, "#163D32", 600)
board += badge(800, 184, 128, "circle", "light")
board += badge(954, 184, 128, "circle", "dark")
board += '<rect x="456" y="350" width="624" height="290" rx="24" fill="#FFFFFF"/>'
board += label(480, 389, "应用内 · 不同尺寸", 17, "#163D32", 600)
for x, y, size in [(480, 421, 36), (580, 417, 40), (680, 409, 48), (790, 393, 64)]:
    board += f'<svg x="{x}" y="{y}" width="{size}" height="{size}" viewBox="0 0 108 108"><use href="#brand"/></svg>'
    board += label(x, 487, f"{size} dp", 13)
board += '<rect x="480" y="524" width="568" height="86" rx="18" fill="#F1F5F0"/>'
board += '<svg x="498" y="543" width="48" height="48" viewBox="0 0 108 108"><use href="#brand"/></svg>'
board += label(562, 558, "关于 TitraDeck", 18, "#163D32", 600)
board += label(562, 584, "离线化学工作台", 14)
(OUTPUT / "icon-preview.svg").write_text(svg(board, definitions, 1120, 680, "TitraDeck 图标设计与裁切预览"), encoding="utf-8")
print(f"Exported SVG artwork to {OUTPUT}")
