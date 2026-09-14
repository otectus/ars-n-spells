# Original spell icon library for 3.3.0

The library contains all **274 base designs** from the September 5 audit, plus **11 background choices** (transparent, arcane, fire, ice, lightning, nature, holy, blood, ender, evocation, eldritch). A background is cosmetic and never changes damage, affinity, progression or spell cost.

Open the Spell Loom, choose **Choose icon…**, search by name or family, select a symbol and frame, then choose **Done**. The preview and Iron’s native wheel use the same composition. The Mods configuration screen also has an **Icon library** browser and a **Compatibility** panel. All picker controls use localized labels, tooltips, keyboard focus and vanilla narration.

The 16px artwork uses original pixel-grid geometry, one-pixel transparent margins, dark contours and restrained upper-left highlights. All five requested states ship: normal, selected, disabled, high contrast and monochrome. Selected uses corner marks; disabled uses a slash. Nothing flashes or animates. The larger sheets below show actual-size samples on gray and dark surfaces alongside enlarged inspection samples.

- [Spells](spell.png)
- [Schools](school.png)
- [Elements](element.png)
- [Resources](resource.png)
- [Status](status.png)
- [Compatibility](compat.png)
- [Controls](ui.png)
- [Rituals](ritual.png)
- [Carriers](carrier.png)
- [Background compositions](backgrounds.png)

The optional packs in `resourcepacks/` provide HD32, high contrast and monochrome artwork. Put a pack in the game instance’s resourcepacks folder and enable it through Minecraft. HD32 is rasterized independently from the authored geometry, with its own contour and engraving treatment. The native wheel adapter preserves its 16px sampling contract when composing pack textures. High contrast and monochrome packs apply globally; their picker buttons are explicitly previews.

The versioned manifest is `assets/ars_n_spells/icon_manifest.json`. Every new canonical/state texture and legacy replacement records its author, source, GPL-3.0-only license, permitted transformations and SHA-256 hashes. Artwork was authored for this project with Codex assistance. No upstream mod texture, logo, decompiled source, photographic asset or proprietary-art derivative is used. Existing registered proxy IDs remain unchanged, and old symbol/nature keys remain valid aliases.

`tools/icon_art.py` is the maintained original shape source. `tools/generate_icons.py` deterministically emits PNGs, packs, catalog, labels and sheets. `tools/verify_icons.py` checks dimensions, alpha margins, required states, provenance, aliases, hashes, localization and fallback graph. `--check` on the generator detects output drift. `tools/icon_catalog.csv` preserves every planned logical ID, priority and placement independently of the historical audit directory.

The resource resolver validates resource presence and decodability, falls back through school/generic icons and finally creates an in-memory outlined rune if the pack removes all artwork. Reload releases old textures and clears a bounded composition cache. Native tooltip rendering uses the actual hovered stack; context-free lookups refuse ambiguous carriers instead of choosing the wrong book.

All 16px family sheets have been inspected during implementation. Automated resource/provenance checks pass. Real-client captures and final loader validation are recorded in the release ledger; these source and sheet checks do not certify every resource pack, translation, color-vision condition or native viewer combination.
