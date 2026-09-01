#!/usr/bin/env python3
"""Generate the 16x16 ritual-tablet item textures for Ars 'n' Spells.

These tablets sit on an Ars Nouveau brazier alongside Ars's own tablets, so they
share that family's reading: a chamfered grey stone plaque carrying one coloured
glyph. The art here is drawn from scratch rather than copied out of the Ars
Nouveau jar, and re-running this script reproduces the shipped PNGs exactly.

Usage:  python tools/gen_ritual_tablets.py [--preview]
"""
from __future__ import annotations

import argparse
import os
import random

from PIL import Image

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(
    REPO_ROOT, "src", "main", "resources", "assets", "ars_n_spells", "textures", "item"
)

SIZE = 16

# Tablet silhouette, as an inclusive x-range per row. Rows 0 and 15 stay empty so
# the slab reads as a plaque rather than filling the whole icon cell.
SILHOUETTE = {
    1: (3, 12),
    2: (2, 13),
    3: (1, 14),
    4: (1, 14),
    5: (1, 14),
    6: (1, 14),
    7: (1, 14),
    8: (1, 14),
    9: (1, 14),
    10: (1, 14),
    11: (1, 14),
    12: (1, 14),
    13: (2, 13),
    14: (3, 12),
}

# Grey ramp for the slab: dark top/left edge, light bottom/right bevel, mottled face.
STONE_EDGE = (68, 66, 72)
STONE_DARK = (96, 94, 102)
STONE_MID = (124, 122, 130)
STONE_FACE = (140, 138, 146)
STONE_LIGHT = (162, 160, 168)

# Chamfer pixels tinted with the ritual's hue, so a tablet is identifiable at
# inventory scale before its glyph is legible.
NOTCHES = ((4, 1), (7, 1), (11, 1), (4, 14), (8, 14), (11, 14))


class Palette:
    def __init__(self, dark, main, light):
        self.D = dark
        self.M = main
        self.L = light


PALETTES = {
    "spell_transcription": Palette((74, 48, 110), (124, 84, 176), (190, 162, 232)),
    "spellbook_binding": Palette((118, 74, 24), (196, 138, 46), (242, 202, 112)),
    "spell_uninscription": Palette((104, 34, 36), (176, 60, 58), (230, 128, 116)),
    "mana_infusion": Palette((24, 88, 108), (54, 154, 178), (140, 220, 236)),
    "mana_well": Palette((30, 50, 112), (56, 92, 180), (132, 168, 238)),
}

# Glyph maps. '.' keeps the stone; D / M / L pick the ritual palette. Row count
# and width are validated before anything is drawn.
GLYPHS = {
    # A quill writing across an unrolled scroll: transcription copies a spell
    # from one carrier onto another.
    "spell_transcription": [
        "................",
        "................",
        "................",
        "............LL..",
        "...........LML..",
        "..........LM....",
        ".........LM.....",
        "..DDDDDDDDDD....",
        "..DDLLLLLLDD....",
        "..DDLMMMMLDD....",
        "..DDLLLLLLDD....",
        "..DDDDDDDDDD....",
        "................",
        "................",
        "................",
        "................",
    ],
    # A closed book with a binding strap drawn across it: binding fixes a carried
    # spell onto a spell book.
    "spellbook_binding": [
        "................",
        "................",
        "................",
        "................",
        "....DDDDDDD.....",
        "...DDMMMMMDD....",
        "...DDMMMMMMD....",
        "..LLLLLLLLLLL...",
        "..DDDDDDDDDDD...",
        "...DDMMMMMMD....",
        "...DDMMMMMDD....",
        "....DDDDDDD.....",
        "................",
        "................",
        "................",
        "................",
    ],
    # The same scroll, struck through: uninscription strips an inscription back
    # off an item.
    "spell_uninscription": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "..DDDDDDDDDD....",
        "..DMMMMMMDDD....",
        "..DMLLLLDDMD....",
        "..DMLLLDDLMD....",
        "..DMLLDDLLMD....",
        "..DMMDDMMMMD....",
        "..DDDDDDDDDD....",
        "................",
        "................",
        "................",
        "................",
    ],
    # A mana droplet falling into an open vessel.
    "mana_infusion": [
        "................",
        "................",
        "................",
        "................",
        ".......L........",
        "......LML.......",
        "......LML.......",
        ".......M........",
        "..DDDDDDDDDD....",
        "..DMLLLLLLMD....",
        "..DMMMMMMMMD....",
        "...DDDDDDDD.....",
        "................",
        "................",
        "................",
        "................",
    ],
    # A ringed well with water rippling inside it.
    "mana_well": [
        "................",
        "................",
        "................",
        "................",
        "....DDDDDDDD....",
        "....DMMMMMMD....",
        "...DMLLMMLLMD...",
        "...DMMLLLLMMD...",
        "...DMLLMMLLMD...",
        "...DMMLLLLMMD...",
        "....DMMMMMMD....",
        "....DDDDDDDD....",
        "................",
        "................",
        "................",
        "................",
    ],
}


def in_slab(x: int, y: int) -> bool:
    span = SILHOUETTE.get(y)
    return span is not None and span[0] <= x <= span[1]


def build_stone() -> Image.Image:
    """The shared slab. Identical pixels for every tablet so the set reads as one
    family; the mottle is seeded so regeneration is deterministic."""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(0x41525300)

    for y in sorted(SILHOUETTE):
        x0, x1 = SILHOUETTE[y]
        for x in range(x0, x1 + 1):
            dark_edge = not in_slab(x - 1, y) or not in_slab(x, y - 1)
            light_edge = not in_slab(x + 1, y) or not in_slab(x, y + 1)
            if dark_edge:
                colour = STONE_EDGE
            elif light_edge:
                colour = STONE_LIGHT
            else:
                colour = rng.choice([STONE_DARK, STONE_MID, STONE_MID, STONE_FACE])
            px[x, y] = (colour[0], colour[1], colour[2], 255)
    return img


def render(name: str) -> Image.Image:
    rows = GLYPHS[name]
    if len(rows) != SIZE:
        raise ValueError("%s: glyph has %d rows, expected %d" % (name, len(rows), SIZE))
    for i, row in enumerate(rows):
        if len(row) != SIZE:
            raise ValueError(
                "%s: glyph row %d is %d wide, expected %d" % (name, i, len(row), SIZE)
            )

    pal = PALETTES[name]
    img = build_stone()
    px = img.load()

    for x, y in NOTCHES:
        px[x, y] = (pal.D[0], pal.D[1], pal.D[2], 255)

    lookup = {"D": pal.D, "M": pal.M, "L": pal.L}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if ch not in lookup:
                raise ValueError("%s: unknown glyph key %r at (%d,%d)" % (name, ch, x, y))
            if not in_slab(x, y):
                raise ValueError("%s: glyph pixel (%d,%d) falls off the tablet" % (name, x, y))
            colour = lookup[ch]
            px[x, y] = (colour[0], colour[1], colour[2], 255)
    return img


def preview(name: str, img: Image.Image) -> None:
    px = img.load()
    print("--- %s ---" % name)
    for y in range(SIZE):
        line = ""
        for x in range(SIZE):
            r, g, b, a = px[x, y]
            if a == 0:
                line += ". "
            elif abs(r - b) < 12 and abs(r - g) < 12:
                line += "%X " % min(15, r // 16)
            else:
                line += "# "
        print(line)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true")
    ap.add_argument("--out", default=OUT_DIR)
    ap.add_argument("--scale", type=int, default=0, help="also write an NxN preview PNG")
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    for name in GLYPHS:
        img = render(name)
        if args.preview:
            preview(name, img)
        path = os.path.join(args.out, name + ".png")
        img.save(path, "PNG", optimize=True)
        if args.scale:
            big = img.resize((SIZE * args.scale, SIZE * args.scale), Image.NEAREST)
            big.save(os.path.join(args.out, name + "_x%d.png" % args.scale), "PNG")
        print("wrote " + path)


if __name__ == "__main__":
    main()
