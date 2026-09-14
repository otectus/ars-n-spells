# Spell Loom visual update — 3.3.3

The Loom uses **16×16** textures with simple spruce-like wood grain, matte
ochre fittings, muted amethyst, a plum tabletop and a complete diamond motif. The
textures were newly generated for this simpler Minecraft aesthetic.

![Offline preview of the shipped model](spell-loom-preview.png)

The 91-cuboid model leaves the raised wire mesh open over the tabletop: the former
`woven_spell` cloth square has been removed, retaining all ten warp/weft strands.
Item transforms, blockstate and cached collision shape are unchanged. Recessed model faces have no
`cullface`, and the block uses `noOcclusion()` for its open frame. The textures
are opaque RGB PNGs. Crystal colors are artwork, not an emissive rendering effect.
No renderer, animation, shader or new game dependency is required.

## Backup

The complete prior Loom, including all six 64×64 textures, the block/item models,
blockstate, block and registry source, geometry/preview tools, source artwork and
design notes, is preserved in:

[spell-loom-64x64-20260912T230829Z.zip](../../backups/spell-loom/spell-loom-64x64-20260912T230829Z.zip)

The archive contains 16 original files with repository-relative paths,
`BACKUP-MANIFEST.json` with their SHA-256 hashes, and restoration notes. Archive
integrity and all hashes were verified before replacement.

## Files and regeneration

- Model: `src/main/resources/assets/ars_n_spells/models/block/spell_loom.json`.
- Textures: `src/main/resources/assets/ars_n_spells/textures/block/spell_loom_{top,side,bottom,brass,crystal,weave}.png`.
- New source material sheet: [spell-loom-materials.png](spell-loom-materials.png).
- Corrected tabletop source: [spell-loom-top-repaired-source.png](spell-loom-top-repaired-source.png).
- Geometry: `python3 tools/generate_spell_loom.py`.
- Preview: `python3 tools/preview_spell_loom.py` (Pillow and NumPy).

The existing item-model parent and blockstate use the same block model and texture
resource locations.

The replacement sheet was generated using the **built-in imagegen tool**. Its six
tiles were extracted and resized with nearest-neighbor sampling to exactly 16×16.
There is no generated-image API dependency in the build or game. The retained
1536×1024 sheet is divided into 512×512 tiles at x=512/1024 and y=512. Tile order is
top, side, bottom / brass, crystal, weave. Convert each crop to RGB, resize to 16×16
using Pillow's `Image.Resampling.NEAREST`, and save as PNG. Re-running the prompt
is not deterministic; use the retained sheet to reproduce the shipped textures.

The tabletop received a subsequent built-in imagegen edit to complete the bottom
tip of its gold diamond at logical pixel (8,12), counted from the upper left.
For `spell_loom_top.png`, resize the entire corrected 1254×1254 source to 16×16 RGB
with nearest-neighbor sampling instead of using the older atlas crop. The other
five PNGs are unchanged. The unused `weave` texture remains available at its
existing resource location, but no face uses it after removal of the cloth square.
The exact built-in imagegen edit prompt is saved in
[spell-loom-diamond-repair-prompt.txt](spell-loom-diamond-repair-prompt.txt).

## Validation

- Verified the prior-version ZIP and all 16 SHA-256 file hashes.
- Checked all six production textures are opaque 16×16 RGB PNGs.
- Checked the model's material references, blockstate and item parent resolve.
- Confirmed that removing `woven_spell` is the only model change in the detail fix;
  all ten threads and all other model properties are unchanged.
- Checked the corrected tabletop has the full symmetric 20-pixel gold diamond,
  with (8,12) the only added gold cell, and the other five PNGs are unchanged.
- Minecraft/Forge's native model parser accepted all 91 elements and 546 faces,
  including all material references and the particle alias.
- `./gradlew --offline jar reobfJar` passed. All nine packaged Loom resource files
  match source; every packaged Loom texture is 16×16. The rebuilt Forge artifact
  is `build/libs/ars_n_spells-3.3.3.jar`.
- The offline preview renders the actual shipped model/textures from front,
  reverse and top views. Minecraft world lighting and live interaction have not
  been visually exercised for this texture revision.

## Texture-generation prompt

```text
Use case: stylized-concept
Asset type: Six vanilla Minecraft 16x16 block textures arranged as a precisely aligned texture atlas.
Create a 1536x1024 image that is EXACTLY an enlarged 48x32 pixel artwork: every logical pixel must be a solid 32x32 square. There are ONLY 48 logical pixels across the whole image and ONLY 32 logical pixels down. Do not put any finer details inside these large squares. This is EXTREMELY COARSE authentic 16x16 Minecraft pixel art, with very simple icon-like motifs and chunky pixel clusters, not high-resolution pixel art. No gradients, anti-aliasing, texture noise or strokes smaller than one logical pixel.
Layout: exactly 3 columns and 2 rows, six edge-to-edge square tiles of 512x512 physical pixels each (16x16 logical pixels). Divider coordinates x=512 and1024, y=512. No margin, gaps, gutters, labels or grid lines. Each tile has a small palette of 4 to 6 solid colors maximum. Opaque.
Purpose: replace elaborate noisy textures on an existing modeled arcane Spell Loom with simpler vanilla Minecraft materials like spruce wood, muted gold, amethyst and wool. The game model already supplies all furniture geometry. Make only FLAT MATERIAL MAPS, no furniture drawing, perspective or 3D render.
TOP LEFT: 16x16 tabletop. Warm spruce-brown outer two-pixel wooden border, very dark desaturated plum inner 12x12 field. A single clean stepped diamond outline in muted pale ochre on the plum field, one logical pixel thick, spanning about 10x10 pixels, small light-lavender 2x2 center. A few plain purple pixels inside. No ornate filigree, circles, lettering or runes.
TOP MIDDLE: 16x16 wooden decorated panel. Warm medium-dark spruce-brown quiet vertical wood in 3 brown shades. Simple thin ochre horizontal belt along logical rows 7 and8, with one small purple diamond at center outlined in dark brown. No border frame or swirls. The belt needs to be clearly visible when only the center four rows are used by the model.
TOP RIGHT: 16x16 seamless vertical spruce wood grain. Three muted brown shades: middle spruce brown, warm dark brown, restrained lighter brown. A handful of long chunky vertical 1-pixel grain streaks with occasional 2-pixel breaks. No plank seams, border or ornament. Readable, quiet, default Minecraft wood.
BOTTOM LEFT: 16x16 seamless matte ochre gold metal. Muted ochre base, 3 closely related ochre shades with a few small pixel clusters. No white, shine, specular highlight, edge outline or polished metallic gradients. Think default Minecraft low-contrast material.
BOTTOM MIDDLE: 16x16 amethyst material in 4 muted lavender/purple shades, not neon blue. Several big simple angular stepped clusters or bands, like vanilla amethyst block, each facet 3 to 6 logical pixels across, limited pale lavender highlights. No sparkling noise.
BOTTOM RIGHT: 16x16 woven plum cloth. Muted dark-plum background with sparse larger checkerlike clusters in a neighboring plum shade to suggest vanilla wool, one clean small pale ochre stepped diamond centered with lavender middle. Simple soft textile, no fine lattice lines.
Critical: the entire sheet is a 48x32 discrete pixel design enlarged uniformly 32 times. All six tiles MUST genuinely look authored with only sixteen by sixteen pixels, no fine-detail texture anywhere. Flat solid-color pixel squares only. Opaque, no letters, numbers, watermark, border around sheet, cast shadows, furniture or photographic texture.
```
