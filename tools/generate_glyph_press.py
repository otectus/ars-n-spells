#!/usr/bin/env python3
"""Author the Glyph Press and imported-glyph assets (3.4.0).

Writes, from scratch and deterministically:
  * the Press's cuboid models (idle and working), blockstate and item model;
  * its original 16x16 materials (stone, archwood, brass, rune plate, animated rune, scroll, crystal);
  * the blank glyph plate item texture and model;
  * the imported-glyph item art: frame, dormant frame, fallback icon and level numerals I..X;
  * one item model per catalogued spell level, plus one dormant model per level.

The imported-glyph item models layer the spell's icon under an ANS frame. The icon sprite
``ars_n_spells:item/iron_glyph_icon/<namespace>/<spell>`` is supplied at atlas build time by the
``ars_n_spells:iron_glyph_icons`` sprite source, which uses Iron's own spell icon when its
resource exists and the ANS fallback icon otherwise, so no model ever names a missing sprite.

Run from any directory: python3 -I tools/generate_glyph_press.py
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/ars_n_spells"
CATALOG = ROOT / "src/main/resources/ans_glyph_catalog/irons_spellbooks.json"
ELEMENTS = []

# ---- Palettes (original; tuned to sit beside Ars Nouveau's archwood and Source colours) ----
STONE = [(31, 29, 38), (38, 36, 46), (45, 43, 54), (52, 50, 62), (60, 57, 70), (68, 65, 79)]
ARCHWOOD = [(70, 66, 80), (82, 78, 92), (98, 92, 106), (112, 105, 119), (126, 118, 131), (139, 130, 141)]
BRASS = [(96, 66, 26), (128, 92, 38), (160, 121, 50), (190, 150, 66), (214, 178, 88), (236, 206, 122)]
PLATE = [(118, 116, 128), (136, 134, 146), (152, 150, 161), (168, 166, 176), (183, 181, 190), (198, 196, 204)]
PARCHMENT = [(150, 124, 78), (176, 151, 101), (198, 176, 127), (214, 194, 147), (228, 211, 166), (239, 226, 189)]
SOURCE = [(76, 14, 104), (98, 24, 124), (126, 62, 190), (156, 98, 220), (190, 128, 236), (236, 170, 246)]
RUNE = (182, 140, 255)
OUTLINE = (34, 26, 20)
GOLD = (255, 214, 96)


def pick(palette, value):
    """Map 0..1 to a palette entry."""
    return palette[max(0, min(len(palette) - 1, int(value * len(palette))))]


def canvas(size=16):
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def put(im, x, y, rgb, alpha=255):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), tuple(rgb) + (alpha,))


def noise_fill(im, palette, seed, base=0.5, spread=0.35, area=None):
    rnd = random.Random(seed)
    x0, y0, x1, y1 = area or (0, 0, im.width, im.height)
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(im, x, y, pick(palette, base + (rnd.random() - 0.5) * 2 * spread))


def bevel(im, palette, x0, y0, x1, y1):
    """Lighter top/left edge, darker bottom/right edge, like vanilla machined blocks."""
    for x in range(x0, x1):
        put(im, x, y0, palette[-1])
        put(im, x, y1 - 1, palette[0])
    for y in range(y0, y1):
        put(im, x0, y, palette[-2])
        put(im, x1 - 1, y, palette[0])


def save(im, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)


# ---- Block materials ----

def stone():
    im = canvas()
    noise_fill(im, STONE, 101, 0.45, 0.3)
    for x in range(16):  # carved course line
        put(im, x, 7, STONE[0])
        put(im, x, 8, STONE[-1] if x % 5 else STONE[1])
    for y in (0, 8):
        for x in ((3, 11) if y == 0 else (7,)):
            put(im, x, y + 3, STONE[0])
            put(im, x, y + 4, STONE[0])
    bevel(im, STONE, 0, 0, 16, 16)
    return im


def stone_top():
    im = canvas()
    noise_fill(im, STONE, 102, 0.5, 0.28)
    bevel(im, STONE, 0, 0, 16, 16)
    bevel(im, STONE, 2, 2, 14, 14)
    for i in range(4, 12):  # faint inlaid diamond, the Press's mark
        d = abs(i - 7.5)
        put(im, int(7.5 - (3.5 - d)), i, BRASS[2])
        put(im, int(8.5 + (3.5 - d)), i, BRASS[2])
    return im


def archwood():
    im = canvas()
    rnd = random.Random(103)
    for x in range(16):
        band = 0.35 + 0.3 * math.sin(x * 1.3) + (rnd.random() - 0.5) * 0.2
        for y in range(16):
            put(im, x, y, pick(ARCHWOOD, band + (rnd.random() - 0.5) * 0.18))
    for x in (4, 11):  # plank seams
        for y in range(16):
            put(im, x, y, ARCHWOOD[0])
    put(im, 7, 5, ARCHWOOD[0]); put(im, 8, 5, ARCHWOOD[1]); put(im, 7, 6, ARCHWOOD[1])  # a knot
    return im


def brass():
    im = canvas()
    rnd = random.Random(104)
    for y in range(16):
        row = 0.55 + 0.25 * math.sin(y * 0.9)
        for x in range(16):
            put(im, x, y, pick(BRASS, row + (rnd.random() - 0.5) * 0.25))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):  # rivets
        put(im, x, y, BRASS[-1]); put(im, x + 1, y + 1, BRASS[0])
    bevel(im, BRASS, 0, 0, 16, 16)
    return im


def rune_plate():
    im = canvas()
    noise_fill(im, PLATE, 105, 0.55, 0.22)
    bevel(im, PLATE, 0, 0, 16, 16)
    for x, y in rune_points():  # faint idle engraving
        put(im, x, y, PLATE[1])
    return im


def rune_points():
    """A ring with a vertical stroke and two ticks: the engraved mark on the plate."""
    pts = set()
    for a in range(0, 360, 8):
        r = 5.0
        pts.add((int(round(7.5 + r * math.cos(math.radians(a)))), int(round(7.5 + r * math.sin(math.radians(a))))))
    for y in range(4, 12):
        pts.add((7, y)); pts.add((8, y))
    for i in range(3):
        pts.add((5 - i, 6 + i)); pts.add((10 + i, 6 + i))
    return sorted(pts)


def rune_animated(frames=8):
    """A vertical strip of frames; the glow breathes in and out (see the .mcmeta)."""
    strip = Image.new("RGBA", (16, 16 * frames), (0, 0, 0, 0))
    for f in range(frames):
        glow = 0.45 + 0.55 * (0.5 + 0.5 * math.sin(2 * math.pi * f / frames))
        for x, y in rune_points():
            core = tuple(min(255, int(c * (0.75 + 0.25 * glow))) for c in RUNE)
            put(strip, x, y + 16 * f, core, int(150 + 105 * glow))
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if (nx, ny) not in rune_points() and 0 <= nx < 16 and 0 <= ny < 16:
                    if strip.getpixel((nx, ny + 16 * f))[3] == 0:
                        put(strip, nx, ny + 16 * f, RUNE, int(60 * glow))
    return strip


def scroll():
    im = canvas()
    noise_fill(im, PARCHMENT, 106, 0.6, 0.2)
    for x in range(16):  # rolled ends
        for y in (0, 1, 14, 15):
            put(im, x, y, PARCHMENT[1] if y in (1, 14) else PARCHMENT[0])
    for y in range(2, 14, 3):  # writing lines
        for x in range(3, 13):
            if (x + y) % 4:
                put(im, x, y, PARCHMENT[0])
    for y in range(16):  # ribbon
        put(im, 11, y, (150, 34, 40)); put(im, 12, y, (118, 22, 30))
    return im


def crystal():
    im = canvas()
    for y in range(16):
        for x in range(16):
            facet = ((x // 4) + (y // 4)) % 3
            put(im, x, y, SOURCE[1 + facet + (1 if (x + y) % 7 == 0 else 0)])
    for i in range(16):
        put(im, i, i, SOURCE[-1])
    return im


# ---- Items ----

def blank_plate():
    im = canvas()
    for y in range(3, 14):
        for x in range(2, 14):
            corner = (x in (2, 13)) and (y in (3, 13))
            if not corner:
                put(im, x, y, pick(PLATE, 0.35 + 0.4 * (13 - y) / 10))
    for x in range(3, 13):  # gold rim
        put(im, x, 3, BRASS[4]); put(im, x, 13, BRASS[1])
    for y in range(4, 13):
        put(im, 2, y, BRASS[3]); put(im, 13, y, BRASS[1])
    put(im, 7, 8, SOURCE[4]); put(im, 8, 8, SOURCE[3]); put(im, 7, 7, SOURCE[5]); put(im, 8, 7, SOURCE[4])
    for x in range(1, 15):  # outline
        put(im, x, 2, OUTLINE); put(im, x, 14, OUTLINE)
    for y in range(3, 14):
        put(im, 1, y, OUTLINE); put(im, 14, y, OUTLINE)
    return im


def glyph_frame(dormant=False):
    im = canvas()
    rim = [(90, 90, 96), (120, 120, 126), (150, 150, 156)] if dormant else [BRASS[1], BRASS[3], BRASS[5]]
    for i in range(16):
        put(im, i, 0, OUTLINE); put(im, i, 15, OUTLINE); put(im, 0, i, OUTLINE); put(im, 15, i, OUTLINE)
    for i in range(1, 15):
        put(im, i, 1, rim[2]); put(im, 1, i, rim[1]); put(im, i, 14, rim[0]); put(im, 14, i, rim[0])
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        put(im, x, y, rim[2])
    if dormant:
        for i in range(3, 13):  # a cold crack across the face
            put(im, i, 15 - i, (60, 60, 66), 200)
            put(im, i + 1, 15 - i, (150, 150, 158), 120)
    return im


def fallback_icon():
    im = canvas()
    for y in range(16):
        for x in range(16):
            put(im, x, y, pick(SOURCE, 0.15 + 0.25 * (y / 15)))
    for x, y in rune_points():
        put(im, x, y, (236, 226, 255))
    return im


FONT = {
    "I": ["X", "X", "X", "X", "X"],
    "V": ["X.X", "X.X", "X.X", "X.X", ".X."],
    "X": ["X.X", "X.X", ".X.", "X.X", "X.X"],
}


def roman(n):
    out = ""
    for value, sym in ((10, "X"), (9, "IX"), (5, "V"), (4, "IV"), (1, "I")):
        while n >= value:
            out += sym
            n -= value
    return out


def level_marker(n):
    """Roman numerals in the bottom-right corner, gold with a dark outline."""
    im = canvas()
    text = roman(n)
    width = sum(len(FONT[c][0]) for c in text) + len(text) - 1
    x0, y0 = 14 - width, 9
    pixels = []
    x = x0
    for c in text:
        glyph = FONT[c]
        for gy, row in enumerate(glyph):
            for gx, cell in enumerate(row):
                if cell == "X":
                    pixels.append((x + gx, y0 + gy))
        x += len(glyph[0]) + 1
    for px, py in pixels:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                put(im, px + dx, py + dy, OUTLINE)
    for px, py in pixels:
        put(im, px, py, GOLD)
    return im


# ---- Press geometry (modelled facing north; the blockstate rotates it) ----

def box(name, start, end, material, *, faces=None, rotation=None, shade=True, extra=None):
    x0, y0, z0 = start
    x1, y1, z1 = end
    uv = {
        "down": [x0, 16 - z1, x1, 16 - z0], "up": [x0, z0, x1, z1],
        "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
        "west": [z0, 16 - y1, z1, 16 - y0], "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
    }
    element = {"name": name, "from": start, "to": end,
               "faces": {side: {"uv": coords, "texture": "#" + material} for side, coords in uv.items()}}
    if faces is not None:
        element["faces"] = faces
    if rotation:
        element["rotation"] = rotation
    if not shade:
        element["shade"] = False
    if extra:
        element.update(extra)
    ELEMENTS.append(element)


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


def press_elements(working):
    ELEMENTS.clear()
    box("base", [1, 0, 1], [15, 3, 15], "stone",
        faces=None)
    ELEMENTS[-1]["faces"]["up"]["texture"] = "#top"
    box("base_lip", [1.5, 3, 1.5], [14.5, 4, 14.5], "stone")
    ELEMENTS[-1]["faces"]["up"]["texture"] = "#top"
    for x in (2, 12):
        for z in (2, 12):
            box(f"post_{x}_{z}", [x, 4, z], [x + 2, 12, z + 2], "archwood")
            box(f"post_foot_{x}_{z}", [x - 0.25, 4, z - 0.25], [x + 2.25, 4.75, z + 2.25], "brass")
    box("bed", [3.5, 4, 3.5], [12.5, 6, 12.5], "stone")
    box("cradle_left", [4, 6, 9.5], [5, 8.5, 12.5], "archwood")
    box("cradle_right", [11, 6, 9.5], [12, 8.5, 12.5], "archwood")
    box("scroll", [4.5, 6.5, 10], [11.5, 8, 12], "scroll")
    box("scroll_seal", [7.25, 6.5, 9.75], [8.75, 8.1, 10.25], "brass")
    box("plate", [4.5, 6, 3.5], [11.5, 6.75, 8.5], "plate")
    for z, side in ((2, "north"), (13, "south")):
        box(f"frame_{side}", [2, 12, z], [14, 13, z + 1], "brass")
    for x, side in ((2, "west"), (13, "east")):
        box(f"frame_{side}", [x, 12, 3], [x + 1, 13, 13], "brass")
    box("stylus_arm", [7.5, 12, 3], [8.5, 12.75, 8], "brass")
    box("stylus", [7.65, 7.5, 5.65], [8.35, 12, 6.35], "brass")
    box("crystal", [7, 13.25, 7], [9, 15.25, 9], "crystal",
        rotation={"origin": [8, 14.25, 8], "axis": "y", "angle": 45}, shade=False)
    box("crystal_cap", [7.4, 15.25, 7.4], [8.6, 16, 8.6], "crystal",
        rotation={"origin": [8, 15.6, 8], "axis": "y", "angle": 45}, shade=False)
    if working:
        # The engraved rune, lifted a hair above the plate, breathing and fullbright while it works.
        ELEMENTS.append({
            "name": "rune_glow", "from": [4.75, 6.8, 3.75], "to": [11.25, 6.8, 8.25], "shade": False,
            "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#rune"}},
            "neoforge_data": {"block_light": 15, "sky_light": 15, "ambient_occlusion": False},
        })
    return trim_hidden_faces(list(ELEMENTS))


DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [.68, .68, .68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [.3, .3, .3]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [.6, .6, .6]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [.375, .375, .375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [.4, .4, .4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [.4, .4, .4]},
}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n")


def press_models():
    textures = {key: "ars_n_spells:block/glyph_press_" + key
                for key in ("stone", "top", "archwood", "brass", "plate", "scroll", "crystal")}
    textures["particle"] = "#stone"
    idle = {"parent": "minecraft:block/block", "ambientocclusion": True, "render_type": "minecraft:solid",
            "textures": dict(textures), "display": DISPLAY, "elements": press_elements(False)}
    # Keep the opaque body in the solid pass; only the breathing rune needs alpha blending.
    rune = next(e for e in press_elements(True) if e["name"] == "rune_glow")
    working = {"parent": "minecraft:block/block", "loader": "neoforge:composite",
               "textures": {"particle": textures["stone"]}, "display": DISPLAY,
               "children": {
                   "body": {"parent": "ars_n_spells:block/glyph_press", "render_type": "minecraft:solid"},
                   "rune": {"parent": "minecraft:block/block", "render_type": "minecraft:translucent",
                            "textures": {"rune": "ars_n_spells:block/glyph_press_rune"}, "elements": [rune]},
               }}
    write_json(ASSETS / "models/block/glyph_press.json", idle)
    write_json(ASSETS / "models/block/glyph_press_working.json", working)
    write_json(ASSETS / "models/item/glyph_press.json", {"parent": "ars_n_spells:block/glyph_press"})
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for working_state in ("false", "true"):
            model = "ars_n_spells:block/glyph_press" + ("_working" if working_state == "true" else "")
            entry = {"model": model}
            if y:
                entry["y"] = y
            variants[f"facing={facing},working={working_state}"] = entry
    write_json(ASSETS / "blockstates/glyph_press.json", {"variants": variants})
    return len(idle["elements"]), len(idle["elements"]) + 1


def glyph_models():
    catalog = json.loads(CATALOG.read_text())
    entries = catalog.get("spells") or catalog.get("entries")
    count = 0
    top = 1
    for entry in entries:
        if entry.get("status") != "supported":
            continue
        namespace, path = entry["spell"].split(":", 1)
        for level in range(entry["min_level"], entry["max_level"] + 1):
            top = max(top, level)
            model = {
                "parent": "minecraft:item/generated",
                "textures": {
                    "layer0": f"ars_n_spells:item/iron_glyph_icon/{namespace}/{path}",
                    "layer1": "ars_n_spells:item/iron_glyph/frame",
                    "layer2": f"ars_n_spells:item/iron_glyph/level_{level}",
                },
                "overrides": [
                    {"predicate": {"ars_n_spells:dormant": 1}, "model": f"ars_n_spells:item/glyph_iron_dormant/level_{level}"}
                ],
            }
            write_json(ASSETS / f"models/item/glyph_iron/{namespace}/{path}/level_{level}.json", model)
            count += 1
    for level in range(1, top + 1):
        write_json(ASSETS / f"models/item/glyph_iron_dormant/level_{level}.json", {
            "parent": "minecraft:item/generated",
            "textures": {
                "layer0": "ars_n_spells:item/iron_glyph/fallback_icon",
                "layer1": "ars_n_spells:item/iron_glyph/frame_dormant",
                "layer2": f"ars_n_spells:item/iron_glyph/level_{level}",
            },
        })
    return count, top


def textures(top_level):
    block = ASSETS / "textures/block"
    item = ASSETS / "textures/item"
    save(stone(), block / "glyph_press_stone.png")
    save(stone_top(), block / "glyph_press_top.png")
    save(archwood(), block / "glyph_press_archwood.png")
    save(brass(), block / "glyph_press_brass.png")
    save(rune_plate(), block / "glyph_press_plate.png")
    save(rune_animated(), block / "glyph_press_rune.png")
    (block / "glyph_press_rune.png.mcmeta").write_text(json.dumps({"animation": {"frametime": 3, "interpolate": True}}, indent=2) + "\n")
    save(scroll(), block / "glyph_press_scroll.png")
    save(crystal(), block / "glyph_press_crystal.png")
    save(blank_plate(), item / "blank_glyph_plate.png")
    # Layer sprites live in a subfolder: they are parts of the generated glyph models, not item textures.
    layers = item / "iron_glyph"
    save(glyph_frame(False), layers / "frame.png")
    save(glyph_frame(True), layers / "frame_dormant.png")
    save(fallback_icon(), layers / "fallback_icon.png")
    for level in range(1, top_level + 1):
        save(level_marker(level), layers / f"level_{level}.png")
    write_json(ASSETS / "models/item/blank_glyph_plate.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": "ars_n_spells:item/blank_glyph_plate"}})


def main():
    idle, working = press_models()
    variants, top = glyph_models()
    textures(top)
    print(f"press: {idle} idle / {working} working cuboids; glyph item models: {variants}; level markers: 1..{top}")


if __name__ == "__main__":
    main()
