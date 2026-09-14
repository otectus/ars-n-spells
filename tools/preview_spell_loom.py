#!/usr/bin/env python3
"""Orthographic review of the shipped Loom JSON and PNGs; requires Pillow/numpy.

This is an offline model preview, not a Minecraft screenshot. It uses vanilla
face UVs, nearest-neighbor texture sampling and a depth buffer, without world AO.
"""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/ars_n_spells"
MODEL = json.loads((ASSETS / "models/block/spell_loom.json").read_text())
TEXTURES = {
    key: np.asarray(Image.open(ASSETS / ("textures/" + value.split(":")[1] + ".png")).convert("RGB"))
    for key, value in MODEL["textures"].items() if not value.startswith("#")
}


def render(size, camera, scale):
    camera = np.array(camera, dtype=float)
    camera /= np.linalg.norm(camera)
    right = np.cross([0, 1, 0], camera)
    right /= np.linalg.norm(right)
    up = np.cross(camera, right)
    basis = np.array([right, -up, camera])
    canvas = np.full((size, size, 3), [232, 228, 218], dtype=np.uint8)
    depth = np.full((size, size), -np.inf)
    for element in MODEL["elements"]:
        x0, y0, z0 = element["from"]
        x1, y1, z1 = element["to"]
        # Top-left, top-right, bottom-left vertices in each face's UV orientation.
        faces = {
            "up": ([[x0,y1,z0], [x1,y1,z0], [x0,y1,z1]], [0,1,0], 1),
            "down": ([[x0,y0,z1], [x1,y0,z1], [x0,y0,z0]], [0,-1,0], .5),
            "north": ([[x1,y1,z0], [x0,y1,z0], [x1,y0,z0]], [0,0,-1], .8),
            "south": ([[x0,y1,z1], [x1,y1,z1], [x0,y0,z1]], [0,0,1], .8),
            "west": ([[x0,y1,z0], [x0,y1,z1], [x0,y0,z0]], [-1,0,0], .6),
            "east": ([[x1,y1,z1], [x1,y1,z0], [x1,y0,z1]], [1,0,0], .6),
        }
        rotation = np.eye(3)
        origin = np.zeros(3)
        if "rotation" in element:
            spec = element["rotation"]
            if spec["axis"] != "y" or spec.get("rescale", False):
                raise ValueError("Preview supports the Loom's unscaled Y rotations only")
            a = math.radians(spec["angle"])
            rotation = np.array([[math.cos(a),0,math.sin(a)], [0,1,0], [-math.sin(a),0,math.cos(a)]])
            origin = np.array(spec["origin"])
        for side, face in element["faces"].items():
            vertices, normal, shade = faces[side]
            if np.dot(rotation @ normal, camera) <= 0:
                continue
            points = (np.array(vertices) - origin) @ rotation.T + origin
            projected = (points - [8, 8, 8]) @ basis.T
            projected[:, :2] = projected[:, :2] * scale + size / 2
            p, q, r = projected
            fourth = q + r - p
            coords = np.array([p, q, r, fourth])
            low = np.maximum(0, np.floor(coords[:, :2].min(axis=0)).astype(int))
            high = np.minimum(size - 1, np.ceil(coords[:, :2].max(axis=0)).astype(int))
            if np.any(high < low):
                continue
            xx, yy = np.meshgrid(np.arange(low[0], high[0]+1), np.arange(low[1], high[1]+1))
            mapping = np.column_stack((q[:2]-p[:2], r[:2]-p[:2]))
            ab = np.stack([xx+.5-p[0], yy+.5-p[1]], axis=-1) @ np.linalg.inv(mapping).T
            a, b = ab[:,:,0], ab[:,:,1]
            z = p[2] + a*(q[2]-p[2]) + b*(r[2]-p[2])
            visible = (a>=0) & (a<=1) & (b>=0) & (b<=1) & (z>depth[yy,xx])
            u0, v0, u1, v1 = face["uv"]
            if face.get("rotation", 0):
                raise ValueError("Rotated UVs require updating this preview")
            texture = TEXTURES[face["texture"][1:]]
            u = np.clip(((u0+a*(u1-u0))/16*texture.shape[1]).astype(int), 0, texture.shape[1]-1)
            v = np.clip(((v0+b*(v1-v0))/16*texture.shape[0]).astype(int), 0, texture.shape[0]-1)
            if element.get("shade", True) is False:
                shade = 1
            color = (texture[v,u] * shade).astype(np.uint8)
            canvas[yy[visible],xx[visible]] = color[visible]
            depth[yy[visible],xx[visible]] = z[visible]
    return Image.fromarray(canvas)


def main():
    output = ROOT / "docs/3.3.3/spell-loom-preview.png"
    sheet = Image.new("RGB", (1200, 850), (232, 228, 218))
    sheet.paste(render(800, [1, .85, -1.3], 30), (0, 40))
    sheet.paste(render(390, [-1, .55, 1.25], 15), (810, 50))
    sheet.paste(render(390, [0, 1, -.01], 20), (810, 450))
    draw = ImageDraw.Draw(sheet)
    draw.text((28, 20), "SPELL LOOM / 3.3.3 / MODEL & TEXTURE REVIEW", fill=(62, 48, 65), font_size=22)
    draw.text((835, 60), "REVERSE VIEW", fill=(62, 48, 65), font_size=15)
    draw.text((835, 455), "TOP / WEAVING DETAIL", fill=(62, 48, 65), font_size=15)
    sizes = sorted({f"{texture.shape[1]}x{texture.shape[0]}" for texture in TEXTURES.values()})
    draw.text((28, 815), f"Shipped JSON + {', '.join(sizes)} materials. Offline preview; Minecraft lighting may differ.", fill=(62, 48, 65), font_size=16)
    output.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(output)
    print(output.relative_to(ROOT))


if __name__ == "__main__":
    main()
