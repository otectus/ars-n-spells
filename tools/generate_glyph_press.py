#!/usr/bin/env python3
"""Rebuild the Glyph Press and imported-glyph assets for Ars 'n' Spells 3.4.0.

Writes, deterministically (seeded noise, sorted output):
  models/block/glyph_press.json, glyph_press_working.json   cuboid model, idle and working
  blockstates/glyph_press.json                               facing x working
  models/item/glyph_press.json, blank_glyph_plate.json
  textures/block/glyph_press_{stone,wood,plate,plate_active}.png (+ .mcmeta for the animation)
  textures/item/blank_glyph_plate.png
  textures/item/imported_glyph/{plate,rune,level_1..level_10}.png

The Press is a companion to the Spell Loom: it reuses the Loom's brass, crystal and weave
materials for its trim, and adds its own dark stone, archwood beams and rune plate. The working
model differs only in the rune plate (animated, full-bright) and the stylus crystal (full-bright).
The block's collision/selection boxes in GlyphPressBlock follow the solid parts listed in SHAPE.

Run from any directory: python3 tools/generate_glyph_press.py
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/ars_n_spells"
TEXTURES = ASSETS / "textures"

# Shared with the Loom's materials so the two workstations read as a set.
WOOD = [(67, 44, 26), (73, 48, 28), (88, 59, 34), (99, 66, 37), (101, 68, 39), (103, 69, 39), (112, 77, 45)]
STONE = [(36, 35, 44), (42, 41, 51), (47, 46, 57), (52, 51, 63), (57, 56, 69), (63, 62, 76), (72, 71, 86)]
SLATE = [(58, 60, 74), (64, 66, 81), (70, 72, 88), (77, 79, 95), (84, 86, 103)]
GOLD = [(132, 98, 38), (169, 131, 57), (194, 155, 72), (222, 183, 92), (246, 214, 128)]
LAVENDER = [(84, 49, 136), (107, 68, 165), (144, 102, 200), (194, 153, 229), (232, 214, 250)]
SMOOTH = [(112, 112, 120), (128, 128, 136), (146, 146, 154), (160, 160, 168), (176, 176, 184)]
OUTLINE = (28, 24, 34)


def save(image, relative):
    path = TEXTURES / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)
    return path


def noise_fill(image, palette, rng, weights=None, box=(0, 0, 16, 16)):
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            image.putpixel((x, y), rng.choices(palette, weights=weights)[0] + (255,))


# ---- Block textures --------------------------------------------------------------------------

def stone():
    """Dressed dark stone: two courses of blocks, offset seams, a lit top-left edge on each block."""
    rng = random.Random(3401)
    image = Image.new("RGBA", (16, 16))
    noise_fill(image, STONE[2:6], rng, weights=[2, 4, 4, 2])
    for y in (7, 15):
        for x in range(16):
            image.putpixel((x, y), STONE[0] + (255,))
    for x, (top, bottom) in ((4, (0, 7)), (12, (8, 15))):
        for y in range(top, bottom):
            image.putpixel((x, y), STONE[0] + (255,))
    for (bx0, bx1, by) in ((0, 4, 0), (5, 16, 0), (0, 12, 8), (13, 16, 8)):
        for x in range(bx0, bx1):
            image.putpixel((x, by), STONE[6] + (255,))
    for (bx, by0, by1) in ((5, 0, 7), (0, 8, 15), (13, 8, 15)):
        for y in range(by0, by1):
            image.putpixel((bx, y), STONE[5] + (255,))
    return save(image, "block/glyph_press_stone.png")


def wood():
    """Archwood beam: vertical grain streaks and one small knot."""
    rng = random.Random(3402)
    image = Image.new("RGBA", (16, 16))
    noise_fill(image, WOOD[3:6], rng)
    for x in range(16):
        if rng.random() < 0.45:
            shade = WOOD[rng.choice((0, 1, 2))]
            start, length = rng.randrange(16), rng.randrange(4, 12)
            for y in range(start, start + length):
                image.putpixel((x, y % 16), shade + (255,))
    for x in (0, 15):
        for y in range(16):
            image.putpixel((x, y), WOOD[1] + (255,))
    for dx, dy, c in ((10, 5, 0), (11, 5, 1), (10, 6, 1), (11, 6, 0), (9, 5, 2), (12, 6, 2)):
        image.putpixel((dx, dy), WOOD[c] + (255,))
    for y in range(16):
        image.putpixel((1, y), WOOD[6] + (255,))
    return save(image, "block/glyph_press_wood.png")


RUNE = [
    # The plate face is sampled at uv 3..13, so the rune is drawn inside that 10x10 window.
    "..........",
    "....##....",
    "...#..#...",
    "..#.##.#..",
    ".#.#..#.#.",
    ".#.#..#.#.",
    "..#.##.#..",
    "...#..#...",
    "....##....",
    "..........",
]


def plate_frame(rng, rune_color, engraved=False):
    image = Image.new("RGBA", (16, 16))
    noise_fill(image, SLATE[1:4], rng)
    for i in range(3, 13):
        for j in (3, 12):
            image.putpixel((i, j), GOLD[1] + (255,))
            image.putpixel((j, i), GOLD[1] + (255,))
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        image.putpixel((x, y), GOLD[3] + (255,))
    cut = {(3 + col, 3 + row) for row, line in enumerate(RUNE) for col, cell in enumerate(line) if cell == "#"}
    for (x, y) in cut:
        image.putpixel((x, y), rune_color + (255,))
        # An engraved cut catches light on its lower-right lip.
        if engraved and (x + 1, y + 1) not in cut and x + 1 < 12 and y + 1 < 12:
            image.putpixel((x + 1, y + 1), SLATE[4] + (255,))
    return image


def plate():
    """The idle rune plate: the rune is cut into the slate, dark and quiet."""
    return save(plate_frame(random.Random(3403), STONE[1], engraved=True), "block/glyph_press_plate.png")


def plate_active():
    """Working: the cut rune fills with Source light and pulses (8 frames, interpolated)."""
    frames = []
    for frame in range(8):
        pulse = 0.5 + 0.5 * math.sin(frame / 8 * 2 * math.pi)
        low, high = LAVENDER[2], LAVENDER[4]
        color = tuple(round(low[i] + (high[i] - low[i]) * pulse) for i in range(3))
        frames.append(plate_frame(random.Random(3403), color))
    sheet = Image.new("RGBA", (16, 16 * len(frames)))
    for i, frame in enumerate(frames):
        sheet.paste(frame, (0, 16 * i))
    path = save(sheet, "block/glyph_press_plate_active.png")
    path.with_suffix(".png.mcmeta").write_text(json.dumps(
        {"animation": {"frametime": 3, "interpolate": True}}, indent=2) + "\n")
    return path


# ---- Item textures ---------------------------------------------------------------------------

def tablet(rng, fill, rim, box=(2, 2, 14, 14)):
    """A rounded tablet with a dark outline and a one-pixel rim, on a transparent background."""
    x0, y0, x1, y1 = box
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(y0, y1):
        for x in range(x0, x1):
            corner = (x in (x0, x1 - 1)) and (y in (y0, y1 - 1))
            if corner:
                continue
            edge = x in (x0, x1 - 1) or y in (y0, y1 - 1)
            if edge:
                image.putpixel((x, y), OUTLINE + (255,))
            elif x in (x0 + 1, x1 - 2) or y in (y0 + 1, y1 - 2):
                image.putpixel((x, y), rng.choice(rim) + (255,))
            else:
                image.putpixel((x, y), rng.choice(fill) + (255,))
    return image


def blank_glyph_plate():
    """Smooth stone with a gold inlay and an amethyst bead: what its recipe is made of."""
    rng = random.Random(3404)
    image = tablet(rng, SMOOTH[1:4], GOLD[1:4], box=(2, 2, 14, 14))
    for y in range(4, 12):
        image.putpixel((4, y), SMOOTH[4] + (255,))
    for x in range(4, 12):
        image.putpixel((x, 4), SMOOTH[4] + (255,))
    for (x, y, c) in ((7, 7, 2), (8, 7, 3), (7, 8, 1), (8, 8, 2)):
        image.putpixel((x, y), LAVENDER[c] + (255,))
    image.putpixel((9, 6), LAVENDER[4] + (255,))
    return save(image, "item/blank_glyph_plate.png")


def glyph_plate():
    """The imported glyph's plate. Only its two-pixel border shows around the inset spell icon."""
    rng = random.Random(3405)
    image = tablet(rng, STONE[2:5], GOLD[2:4], box=(0, 0, 16, 16))
    return save(image, "item/imported_glyph/plate.png")


FALLBACK_RUNE = [
    "................",
    "................",
    ".......##.......",
    "......#..#......",
    ".....#.##.#.....",
    "....#.#..#.#....",
    "...#.#....#.#...",
    "...#.#.##.#.#...",
    "...#.#.##.#.#...",
    "...#.#....#.#...",
    "....#.#..#.#....",
    ".....#.##.#.....",
    "......#..#......",
    ".......##.......",
    "................",
    "................",
]


def rune():
    """ANS's own fallback face for a glyph whose spell icon is unavailable."""
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, line in enumerate(FALLBACK_RUNE):
        for x, cell in enumerate(line):
            if cell == "#":
                image.putpixel((x, y), LAVENDER[3] + (255,))
                if y + 1 < 16 and FALLBACK_RUNE[y + 1][x] != "#" and image.getpixel((x, y + 1))[3] == 0:
                    image.putpixel((x, y + 1), LAVENDER[0] + (255,))
    return save(image, "item/imported_glyph/rune.png")


DIGITS = {
    "0": ["###", "#.#", "#.#", "#.#", "###"],
    "1": [".#.", "##.", ".#.", ".#.", "###"],
    "2": ["###", "..#", "###", "#..", "###"],
    "3": ["###", "..#", "###", "..#", "###"],
    "4": ["#.#", "#.#", "###", "..#", "..#"],
    "5": ["###", "#..", "###", "..#", "###"],
    "6": ["###", "#..", "###", "#.#", "###"],
    "7": ["###", "..#", "..#", "..#", "..#"],
    "8": ["###", "#.#", "###", "#.#", "###"],
    "9": ["###", "#.#", "###", "..#", "###"],
}


def level_marker(level):
    """The level in light gold with a dark outline, in the bottom-right corner; transparent elsewhere."""
    text = str(level)
    width = 4 * len(text) - 1
    x0, y0 = 15 - width, 10
    lit = set()
    for i, ch in enumerate(text):
        for row, line in enumerate(DIGITS[ch]):
            for col, cell in enumerate(line):
                if cell == "#":
                    lit.add((x0 + 4 * i + col, y0 + row))
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for (x, y) in lit:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                px, py = x + dx, y + dy
                if 0 <= px < 16 and 0 <= py < 16 and (px, py) not in lit:
                    image.putpixel((px, py), OUTLINE + (255,))
    for (x, y) in lit:
        image.putpixel((x, y), GOLD[4] + (255,))
    return save(image, f"item/imported_glyph/level_{level}.png")


# ---- Model -----------------------------------------------------------------------------------

ELEMENTS = []
# Solid parts for GlyphPressBlock's shape, north-facing, in pixels (kept in sync by hand).
SHAPE = []


def box(name, start, end, material, *, faces=None, rotation=None, shade=True, glow=False, solid=False):
    x0, y0, z0 = start
    x1, y1, z1 = end
    uv = {
        "down": [x0, 16 - z1, x1, 16 - z0], "up": [x0, z0, x1, z1],
        "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0],
        "south": [x0, 16 - y1, x1, 16 - y0],
        "west": [z0, 16 - y1, z1, 16 - y0],
        "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
    }
    element = {"name": name, "from": list(start), "to": list(end),
               "faces": {side: {"uv": coords, "texture": "#" + material} for side, coords in uv.items()}}
    if faces:
        element["faces"].update(faces)
    if rotation:
        element["rotation"] = rotation
    if not shade:
        element["shade"] = False
    if glow:
        element["forge_data"] = {"block_light": 15, "sky_light": 15}
    ELEMENTS.append(element)
    if solid:
        SHAPE.append([*start, *end])
    return element


def trim_hidden_faces(elements):
    """Drop faces fully buried in another opaque cuboid; keep partially covered faces intact.

    Rotated parts and the translucent rune are not occluders. Only faces on the block boundary
    receive neighbour culling: applying cullface to inset surfaces would make visible parts vanish.
    """
    solids = [e for e in elements if "rotation" not in e and e["name"] != "rune_glow"
              and all(a < b for a, b in zip(e["from"], e["to"]))]
    directions = {"west": (0, False), "east": (0, True), "down": (1, False),
                  "up": (1, True), "north": (2, False), "south": (2, True)}
    for element in solids:
        for side in list(element["faces"]):
            axis, positive = directions[side]
            plane = element["to" if positive else "from"][axis]
            spans = [i for i in range(3) if i != axis]
            for other in solids:
                if other is element:
                    continue
                crosses = (other["from"][axis] <= plane < other["to"][axis] if positive
                           else other["from"][axis] < plane <= other["to"][axis])
                if crosses and all(other["from"][i] <= element["from"][i]
                                   and other["to"][i] >= element["to"][i] for i in spans):
                    del element["faces"][side]
                    break
            if side in element["faces"] and plane == (16 if positive else 0):
                element["faces"][side]["cullface"] = side
    return elements


def build_elements(working):
    ELEMENTS.clear()
    SHAPE.clear()
    # Plinth and worktable.
    box("plinth", [0.5, 0, 0.5], [15.5, 1.5, 15.5], "stone", solid=True)
    box("worktable", [1.5, 1.5, 1.5], [14.5, 4, 14.5], "wood", solid=True)
    box("brass_band_front", [1.25, 3, 1.25], [14.75, 3.5, 1.5], "brass")
    box("brass_band_back", [1.25, 3, 14.5], [14.75, 3.5, 14.75], "brass")
    # The rune plate bed: stone sides, the rune plate on top (uv 3..13 of the plate texture).
    plate_faces = {"up": {"uv": [3, 3, 13, 13], "texture": "#plate"}}
    plate = box("rune_plate", [3, 4, 3], [13, 5.5, 13], "stone", faces=plate_faces, solid=True)
    if working:
        # Only the plate's top face glows; Forge reads emissivity per element.
        plate["forge_data"] = {"block_light": 15, "sky_light": 15}
    # Uprights at the back, a brass collar on each, and the crossbar.
    for x0, x1, side in ((1.5, 3.5, "left"), (12.5, 14.5, "right")):
        box(f"upright_{side}", [x0, 4, 11], [x1, 13, 13], "wood", solid=True)
        box(f"collar_{side}", [x0 - 0.25, 5, 10.75], [x1 + 0.25, 6, 13.25], "brass")
        box(f"collar_top_{side}", [x0 - 0.25, 11.5, 10.75], [x1 + 0.25, 12.25, 13.25], "brass")
    box("crossbar", [1.5, 13, 11], [14.5, 14.5, 13], "wood", solid=True)
    box("crossbar_cap", [1.25, 14.5, 10.75], [14.75, 15, 13.25], "brass")
    # The arm reaching forward over the plate, the stylus rod and its Source crystal tip.
    box("arm", [7, 13.5, 7], [9, 14.5, 11], "brass", solid=True)
    box("stylus", [7.5, 9.5, 7.5], [8.5, 13.5, 8.5], "brass", solid=True)
    tip_rotation = {"origin": [8, 9, 8], "axis": "y", "angle": 45}
    box("crystal_tip", [7.3, 8.5, 7.3], [8.7, 9.5, 8.7], "crystal", rotation=tip_rotation, shade=False, glow=working)
    SHAPE.append([7.3, 8.5, 7.3, 8.7, 9.5, 8.7])
    # The scroll cradle at the front: a wooden trough with brass cheeks and a woven lining.
    box("cradle", [3, 4, 1], [13, 5, 3], "wood", solid=True)
    box("cradle_lining", [4, 5, 1.25], [12, 5.25, 2.75], "weave")
    box("cradle_cheek_left", [3, 5, 1], [4, 7, 3], "brass", solid=True)
    box("cradle_cheek_right", [12, 5, 1], [13, 7, 3], "brass", solid=True)
    return trim_hidden_faces(list(ELEMENTS))


DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [.625, .625, .625]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [.3, .3, .3]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [.6, .6, .6]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [.375, .375, .375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [.4, .4, .4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [.4, .4, .4]},
}


def write_json(relative, body):
    path = ASSETS / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(body, indent=2) + "\n")


def models():
    for working, name in ((False, "glyph_press"), (True, "glyph_press_working")):
        textures = {
            "stone": "ars_n_spells:block/glyph_press_stone",
            "wood": "ars_n_spells:block/glyph_press_wood",
            "plate": "ars_n_spells:block/glyph_press_plate_active" if working else "ars_n_spells:block/glyph_press_plate",
            "brass": "ars_n_spells:block/spell_loom_brass",
            "crystal": "ars_n_spells:block/spell_loom_crystal",
            "weave": "ars_n_spells:block/spell_loom_weave",
            "particle": "ars_n_spells:block/glyph_press_stone",
        }
        write_json(f"models/block/{name}.json", {
            "parent": "minecraft:block/block", "ambientocclusion": True,
            "textures": textures, "display": DISPLAY, "elements": build_elements(working)})
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for working in ("false", "true"):
            model = {"model": "ars_n_spells:block/" + ("glyph_press_working" if working == "true" else "glyph_press")}
            if y:
                model["y"] = y
            variants[f"facing={facing},working={working}"] = model
    write_json("blockstates/glyph_press.json", {"variants": variants})
    write_json("models/item/glyph_press.json", {"parent": "ars_n_spells:block/glyph_press"})
    write_json("models/item/blank_glyph_plate.json", {
        "parent": "minecraft:item/generated", "textures": {"layer0": "ars_n_spells:item/blank_glyph_plate"}})


def main():
    written = [stone(), wood(), plate(), plate_active(), blank_glyph_plate(), glyph_plate(), rune()]
    written += [level_marker(level) for level in range(1, 11)]
    models()
    build_elements(False)
    print(f"Wrote {len(written)} textures and the Press models ({len(ELEMENTS)} cuboids)")
    print("Solid shape boxes (GlyphPressBlock.NORTH_BOXES):")
    for b in SHAPE:
        print("    {" + ", ".join(f"{v:g}" for v in b) + "},")


if __name__ == "__main__":
    main()
