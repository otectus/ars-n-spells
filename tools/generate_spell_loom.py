#!/usr/bin/env python3
"""Rebuild the Spell Loom's vanilla cuboid model (no runtime renderer needed).

The six PNG materials are authored separately; this only generates geometry/UVs.
Run from any directory: python3 tools/generate_spell_loom.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / "src/main/resources/assets/ars_n_spells/models/block/spell_loom.json"
ELEMENTS = []


def box(name, start, end, material="bottom", *, faces=None, rotation=None, shade=True):
    x0, y0, z0 = start
    x1, y1, z1 = end
    # Vanilla's face orientation; structural materials keep a consistent texel scale.
    uv = {
        "down": [x0, 16-z1, x1, 16-z0], "up": [x0, z0, x1, z1],
        "north": [16-x1, 16-y1, 16-x0, 16-y0],
        "south": [x0, 16-y1, x1, 16-y0],
        "west": [z0, 16-y1, z1, 16-y0],
        "east": [16-z1, 16-y1, 16-z0, 16-y0],
    }
    element = {"name": name, "from": start, "to": end,
               "faces": {side: {"uv": coords, "texture": "#" + material}
                         for side, coords in uv.items()}}
    if faces:
        element["faces"].update(faces)
    if rotation:
        element["rotation"] = rotation
    if not shade:
        element["shade"] = False
    ELEMENTS.append(element)


def face(material, uv=(0, 0, 16, 16)):
    return {"texture": "#" + material, "uv": list(uv)}


def build():
    ELEMENTS.clear()
    # Four carved legs, stepped shoes, collar bands and crystal-tipped loom posts.
    for x in (2.75, 13.25):
        for z in (2.75, 13.25):
            suffix = f"{x:g}_{z:g}"
            box("foot_" + suffix, [x-1.5, 0, z-1.5], [x+1.5, 1, z+1.5])
            box("brass_shoe_" + suffix, [x-1.3, 1, z-1.3], [x+1.3, 1.5, z+1.3], "brass")
            box("carved_leg_" + suffix, [x-1, 1.5, z-1], [x+1, 8.5, z+1])
            box("leg_collar_" + suffix, [x-1.15, 6.75, z-1.15], [x+1.15, 7.25, z+1.15], "brass")
            box("capital_" + suffix, [x-1.4, 8.5, z-1.4], [x+1.4, 9, z+1.4])
            box("post_socket_" + suffix, [x-.9, 11, z-.9], [x+.9, 11.5, z+.9], "brass")
            box("loom_post_" + suffix, [x-.55, 11.5, z-.55], [x+.55, 14.5, z+.55])
            box("post_cap_" + suffix, [x-.8, 14.5, z-.8], [x+.8, 15, z+.8], "brass")
            for tier, (width, low, high) in enumerate(((.4, 15, 15.2), (.6, 15.2, 15.6), (.4, 15.6, 15.85), (.2, 15.85, 16))):
                box(f"crystal_finial_{suffix}_{tier}", [x-width, low, z-width], [x+width, high, z+width], "crystal",
                    rotation={"origin": [x, 15.5, z], "axis": "y", "angle": 45}, shade=False)

    # Low cross stretchers and a suspended Source bobbin visible between the legs.
    box("lower_stretcher_x", [3.75, 2.5, 7.5], [12.25, 3.25, 8.5])
    box("lower_stretcher_z", [7.5, 2.5, 3.75], [8.5, 3.25, 12.25])
    for z in (2.75, 13.25):
        box(f"lower_rail_{z}", [3.75, 2.5, z-.4], [12.25, 3.25, z+.4])
    for x in (2.75, 13.25):
        box(f"lower_side_rail_{x}", [x-.4, 2.5, 3.75], [x+.4, 3.25, 12.25])
    box("bobbin_axle", [7.65, 3.25, 7.65], [8.35, 9, 8.35], "brass")
    for y in (3.5, 7.5):
        box(f"bobbin_flange_{y}", [6.4, y, 6.4], [9.6, y+.5, 9.6], "brass")
    box("source_bobbin", [6.9, 4, 6.9], [9.1, 7.5, 9.1], "crystal",
        rotation={"origin": [8, 5.75, 8], "axis": "y", "angle": 45}, shade=False)

    # Stepped corners give the tabletop an octagonal outline. UVs stay continuous
    # across its three top faces, so the diamond inlay is never repeated or stretched.
    box("table_center", [1, 9, 2], [15, 11, 14],
        faces={"up": face("top", [1, 2, 15, 14])})
    box("table_north", [2, 9, 1], [14, 11, 2],
        faces={"up": face("top", [2, 1, 14, 2])})
    box("table_south", [2, 9, 14], [14, 11, 15],
        faces={"up": face("top", [2, 14, 14, 15])})
    for z, side in ((1, "north"), (14.5, "south")):
        for y in (8.875, 10.875):
            box(f"rim_{side}_{y}", [2, y, z], [14, y+.25, z+.5], "brass")
        box(f"apron_{side}", [4.2, 7.25, z+.2], [11.8, 9, z+.5], "side",
            faces={side: face("side", [0, 6, 16, 10])})
        box(f"escutcheon_{side}", [6.75, 6.5, z+.1], [9.25, 9, z+.6], "brass",
            faces={side: face("side", [4, 4, 12, 12])})
    for x, side in ((1, "west"), (14.5, "east")):
        for y in (8.875, 10.875):
            box(f"rim_{side}_{y}", [x, y, 2], [x+.5, y+.25, 14], "brass")
        box(f"apron_{side}", [x+.2, 7.25, 4.2], [x+.5, 9, 11.8], "side",
            faces={side: face("side", [0, 6, 16, 10])})
        box(f"escutcheon_{side}", [x+.1, 6.5, 6.75], [x+.6, 9, 9.25], "brass",
            faces={side: face("side", [4, 4, 12, 12])})

    # Raised rails and individual warp/weft strands leave the inscription diamond
    # visible through the entire mesh, including its center.
    for z in (2.75, 13.25):
        box(f"weaving_rail_{z}", [3.3, 13.25, z-.3], [12.7, 13.85, z+.3], "brass")
    for x in (2.75, 13.25):
        box(f"weaving_side_rail_{x}", [x-.3, 13.25, 3.3], [x+.3, 13.85, 12.7], "brass")
    for p in (5, 6.5, 8, 9.5, 11):
        box(f"warp_{p}", [p-.0625, 13.48, 3.05], [p+.0625, 13.605, 12.95], "crystal", shade=False)
        box(f"weft_{p}", [3.05, 13.625, p-.0625], [12.95, 13.75, p+.0625], "brass")

    # These are inset faces: intentionally no cullface, including the underside.
    # Adjacent opaque blocks must not erase a recessed leg, apron or weaving rail.
    model = {
        "parent": "minecraft:block/block", "ambientocclusion": True,
        "textures": {key: "ars_n_spells:block/spell_loom_" + key
                     for key in ("top", "side", "bottom", "brass", "crystal", "weave")},
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [.68, .68, .68]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [.3, .3, .3]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [.6, .6, .6]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [.375, .375, .375]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [.4, .4, .4]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [.4, .4, .4]},
        },
        "elements": ELEMENTS,
    }
    model["textures"]["particle"] = "#bottom"
    MODEL.write_text(json.dumps(model, indent=2) + "\n")
    print(f"Wrote {len(ELEMENTS)} cuboids to {MODEL.relative_to(ROOT)}")


if __name__ == "__main__":
    build()
