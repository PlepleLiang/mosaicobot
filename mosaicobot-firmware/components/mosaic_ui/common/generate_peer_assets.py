#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Build Partner artwork from the canonical Amoo and vector primitives.

amoo_source.png is the unchanged phoenix amoo_body.png embedded in the
2026-10-08 peer-agent-park UI design. Keep its silhouette and proportions.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageOps

HERE = Path(__file__).resolve().parent
ASSETS = HERE / "assets"


def generate():
    avatar = Image.new("RGBA", (140, 140))
    with Image.open(ASSETS / "peer/amoo_source.png") as source:
        body = ImageOps.contain(source.convert("RGBA"), (140, 140), Image.Resampling.LANCZOS)
    avatar.alpha_composite(body, ((140 - body.width) // 2, (140 - body.height) // 2))
    avatar.save(ASSETS / "peer/amoo.png")

    # Two facing speech bubbles: cool Mosaico + warm Amoo, linked as peers.
    scale = 4
    icon = Image.new("RGBA", (118 * scale, 118 * scale))
    draw = ImageDraw.Draw(icon)
    svg = ['<svg xmlns="http://www.w3.org/2000/svg" width="118" height="118" viewBox="0 0 118 118">']

    def rect(x, y, w, h, radius, color):
        draw.rounded_rectangle((x * scale, y * scale, (x + w) * scale - 1, (y + h) * scale - 1),
                               radius=radius * scale, fill=color)
        svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" fill="{color}"/>')

    def polygon(points, color):
        draw.polygon([(x * scale, y * scale) for x, y in points], fill=color)
        svg.append('<polygon points="' + ' '.join(f'{x},{y}' for x, y in points) + f'" fill="{color}"/>')

    rect(0, 0, 118, 118, 30, "#142B3D")
    rect(14, 17, 62, 51, 18, "#6CC8FF")
    polygon([(23, 60), (23, 79), (43, 62)], "#6CC8FF")
    # Dark keyline separates the overlap at launcher size.
    rect(44, 43, 66, 54, 19, "#142B3D")
    polygon([(80, 89), (101, 108), (101, 83)], "#142B3D")
    rect(48, 47, 58, 46, 16, "#FF9A3D")
    polygon([(82, 86), (97, 101), (97, 82)], "#FF9A3D")
    for x in (29, 47):
        rect(x, 32, 7, 17, 3.5, "#142B3D")
    for x in (62, 81):
        rect(x, 60, 8, 18, 4, "#FFF4EA")
    svg.append('</svg>')
    (ASSETS / "icons/partner.svg").write_text('\n'.join(svg) + '\n', encoding="utf-8")
    icon.resize((118, 118), Image.Resampling.LANCZOS).save(ASSETS / "icons/partner.png")


if __name__ == "__main__":
    generate()
