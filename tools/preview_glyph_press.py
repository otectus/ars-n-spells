#!/usr/bin/env python3
"""Orthographic review of the shipped Glyph Press JSON and PNGs (idle and working); requires Pillow/numpy.

Reuses the Spell Loom preview's renderer. An offline model preview, not a Minecraft screenshot:
animated textures show their first frame and there is no world lighting or emissivity.
Writes docs/3.4.0/glyph-press-preview.png.
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import preview_spell_loom as loom  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/ars_n_spells"


def load(name):
    model = json.loads((ASSETS / f"models/block/{name}.json").read_text())
    textures = {}
    for key, value in model["textures"].items():
        if value.startswith("#"):
            continue
        image = Image.open(ASSETS / ("textures/" + value.split(":")[1] + ".png")).convert("RGB")
        textures[key] = np.asarray(image.crop((0, 0, image.width, image.width)))
    return model, textures


def main():
    output = ROOT / "docs/3.4.0/glyph-press-preview.png"
    sheet = Image.new("RGB", (1200, 850), (232, 228, 218))
    loom.MODEL, loom.TEXTURES = load("glyph_press")
    sheet.paste(loom.render(800, [1, .85, -1.3], 30), (0, 40))
    sheet.paste(loom.render(390, [-1, .55, 1.25], 15), (810, 50))
    loom.MODEL, loom.TEXTURES = load("glyph_press_working")
    sheet.paste(loom.render(390, [0, 1, -.01], 20), (810, 450))
    draw = ImageDraw.Draw(sheet)
    draw.text((28, 20), "GLYPH PRESS / 3.4.0 / MODEL & TEXTURE REVIEW", fill=(62, 48, 65), font_size=22)
    draw.text((835, 60), "REVERSE VIEW", fill=(62, 48, 65), font_size=15)
    draw.text((835, 455), "TOP / WORKING RUNE PLATE (FIRST FRAME)", fill=(62, 48, 65), font_size=15)
    draw.text((28, 815), "Shipped JSON + 16x16 materials. Offline preview; Minecraft lighting, emissivity and animation differ.",
              fill=(62, 48, 65), font_size=16)
    output.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(output)
    print(output.relative_to(ROOT))


if __name__ == "__main__":
    main()
