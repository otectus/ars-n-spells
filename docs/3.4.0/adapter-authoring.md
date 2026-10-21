# Authoring imported-glyph adapters (3.4.0, Forge 1.20.1)

An **adapter** tells Ars 'n' Spells how an Iron's spell meets an Ars target when it runs as an
imported glyph. There is no per-spell Java: every spell is assigned one of five adapter
families, and the family decides what Iron's own spell code is handed. This page covers the real
extension points, in the order a contributor or pack author meets them. (The 3.3.0 "addon
adapter kit" is a different thing: it maps Ars addon glyphs to schools.)

## 1. The families

| Family | What the Ars form's target becomes | Forms |
| --- | --- | --- |
| `caster` | Nothing: the spell resolves at the caster exactly as Iron's casts it. | Self |
| `aimed` | Iron's own aim helpers (`Utils.preCastTargetHelper`, `RaycastBuilder`/`raycastForEntity`, `raycastForBlock`, `getTargetBlock`) answer with the Ars target, for aims taken from the caster's eye. | Self, Touch, Projectile, Underfoot |
| `projectile` | The native projectile is moved to just before the target and aimed at it, keeping speed, owner and impact. | same |
| `movement` | Iron's aim helpers answer with the target, so the spell moves the caster there. | same |
| `summon` | The spell's summons are moved to the nearest free spot at the target; Iron's records them as the caster's. | same |

With Self, the aimed family aims at the caster, and projectile, movement and summon keep Iron's
native placement along the caster's aim. Addon forms are refused at composition.

The scope is narrow on purpose (`IronTargetingScope`): only the server thread, only the casting
player, only aims that start at the caster's eye, and only for the duration of one execution.
A chain bounce, a line-of-sight check or a spawn search inside the spell runs natively.

## 2. Assigning a spell: `tools/iron_glyph_adapters.json`

One entry per spell, with the family and the source-backed reason:

```json
"irons_spellbooks:firebolt": {
  "adapter": "projectile",
  "reason": "Launches a FireboltProjectile along the caster's look."
}
```

Choose the family from the spell's own `onCast`: which helper picks its target, whether it
spawns a projectile or summons, whether it moves the caster. A spell that only affects its
caster is `caster`. If none of the five fits, the spell needs a new family (section 5); do not
force it into one that changes what the spell does.

An Iron's addon spell takes the same shape under its own namespace. A hypothetical example (no
addon spells are in the 3.4.0 table):

```json
"example_addon:frost_lance": {
  "adapter": "projectile",
  "reason": "FrostLanceSpell#onCast launches a FrostLanceProjectile along the caster's look."
}
```

Its glyphs would be `ars_n_spells:iron/example_addon/frost_lance/level_<n>`, and its icon is
read from `example_addon:textures/gui/spell_icons/frost_lance.png`, Iron's own convention.

## 3. Generating the catalog: `tools/generate_iron_glyph_catalog.py`

The startup catalog is generated, never hand-edited:

1. Record the loaded registry of each pinned Iron's build. Run the GameTest
   `ironsLoaded_writeLoadedSpellInventory` (class `GlyphCatalogInventoryGameTests`) in that
   build's profile; it writes `ans-iron-spell-inventory.json` in the run directory. Commit it as
   `docs/3.4.0/inventory/irons-<build>.json`.
2. Add an adapter entry for every new spell (the generator stops with
   `adapter table mismatch` if any registered player spell lacks one, or an entry names a spell
   no build registers).
3. Run, with an English Iron's language file for names:

   ```bash
   python3 tools/generate_iron_glyph_catalog.py <iron's assets/irons_spellbooks/lang/en_us.json>
   ```

   It writes `src/main/resources/ars_n_spells/iron_glyph_catalog.json`, the coverage manifest
   and table (`docs/3.4.0/iron-glyph-coverage.{json,md}`), and one item model per level under
   `assets/ars_n_spells/models/item/iron/`. Output is sorted and deterministic.

Rules the generator enforces, because a glyph id is permanent:

- Ids are `ars_n_spells:iron/<spell namespace>/<spell path>/level_<n>`. They are never reused for
  another spell; a retired spell keeps its id as a dormant glyph.
- A spell's school, cast type, level range, rarities and learning requirement must agree across
  the pinned builds; if they differ, the catalog cannot freeze them and generation stops.
- Every level from 1 to the spell's maximum gets a glyph.

The catalog's identity digest is checked at login: a client whose catalog differs from the
server's is disconnected with a readable message instead of decoding spells into the wrong
glyphs. Changing the catalog is therefore a release change on both sides.

GameTests guard the result: `ironsLoaded_catalogCoversEveryLoadedSpellAtEveryLevel` fails if any
player spell in the running Iron's build is missing a glyph at any level, and
`ironsLoaded_everyFamilyAtItsMinimumAndMaximumLevel` presses one spell per family at its minimum
and maximum levels.

## 4. Per-spell behaviour without code: datapacks

Availability, tiers and Glyph Press terms are reloadable data under
`data/<namespace>/ans_iron_glyphs/` (full rules in `config-reference.md`). A working example,
exercised by `ironsLoaded_behaviourReloadInvalidatesOnlyAffectedJobs`:

```json
{
  "schema_version": 1,
  "spell": "irons_spellbooks:firebolt",
  "levels": {
    "1": { "source_cost": 600 }
  }
}
```

This reprices Firebolt I at the Press (the catalyst and time keep the level's tier defaults). A
file may name the spell's adapter, but only to confirm it: an adapter that differs from the
catalog's is an error and the spell fails closed.

## 5. A new family (Java)

When a spell needs a behaviour none of the five provide:

1. Add the family to `glyph/GlyphFamily` with its supported forms and adapter id.
2. Decide its targeting in `compat/irons/glyph/IronTargetingScope.push` (whether Iron's aim
   helpers are redirected and whether spawned entities are captured for delivery).
3. If it delivers spawned entities, extend `IronsGlyphInvoker.deliver`.
4. Add its description key `ars_n_spells.imported_glyph.description.<family>`, its restriction
   key `tooltip.ars_n_spells.glyph.restriction.<family>`, the family to `FAMILIES`, `FORMS` and
   `FAMILY_TEXT` in the generator, and a GameTest that proves the world effect (see
   `ImportedGlyphGameTests` for one test per existing family).

Keep Iron's types inside `compat/irons/glyph/`: common code talks to Iron's only through
`IronGlyphBridge`, so nothing outside that package resolves an Iron's class when Iron's is
absent.

## Not supported in 3.4.0

- Iron's addon spells are not in the shipped catalog. Adding them follows sections 2 and 3 with
  the addon loaded when the inventory is recorded, and ships as a new catalog.
- There is no runtime API for third-party mods to register adapters; the catalog is the
  registry identity and is fixed at startup.
