# Catalogs for addon spells (3.4.1)

ANS ships a catalog for Iron's Spells 'n Spellbooks' own spells. Spells added by an Iron's addon need
a catalog of their own, which gives each spell its glyph ids at startup. This page is for pack makers
who build or install one. The rules every catalog follows are in
[identity-and-levels.md](identity-and-levels.md).

ANS 3.4.1 builds two catalog packages of its own alongside the mod (see [Packages ANS ships](#packages-ans-ships)):
Wind's Spellbooks, tested spell by spell ([coverage](#winds-spellbooks)), and an
envelope extension that reserves levels XI-XV for Iron's own spells. For any other addon, the tool
below builds a catalog from your pack's own runtime inventory; you choose and test the adapters.

## 1. Export the native inventory from your pack

Start a server or a single-player world with the pack installed: Iron's, the addon and ANS. Then run:

```
/ans glyph inventory export
```

This writes `ars_n_spells/reports/glyph-inventory.json` in the server directory. It records every
spell in Iron's loaded registry as the running game sees it: id, school, cast type, level range
(from the spell's own minimum level), rarity per level, learning requirement, enabled state and icon.
The NeoForge export also includes each spell's display name; on Forge, pass the addon's `en_us.json`
with `--names`, or names are made from the spell ids. `/ans glyph report` shows which native spells
no catalog covers yet.

## 2. Assign adapters

Write an adapters file that gives each spell the adapter that can cast it. The ids here are
examples:

```json
{
  "spells": {
    "example_addon:frost_lance": { "adapter": "ars_n_spells:projectile", "target": "any", "reason": "native projectile" },
    "example_addon:ice_familiar": { "adapter": "ars_n_spells:summon", "target": "position" },
    "example_addon:blink_dash": { "status": "unsupported", "reason": "computes its own path; no adapter can place it" }
  }
}
```

The adapters differ per build, because each build implements its own:

| Build | Adapters |
|---|---|
| Forge | `caster`, `aimed`, `projectile`, `movement`, `summon` |
| NeoForge | `self`, `projectile`, `entity`, `position`, `block`, `teleport`, `summon`, `channel`, each with a `target` (`self`, `entity`, `entity_or_self`, `block`, `position`, `any`) and an optional `final_effect_only` |

A spell with no assignment, or with only `status` `unsupported` and a `reason`, becomes a
**placeholder**: catalogued `unsupported` with `adapter_version` 0. It is registered and dormant, and
a later version of the same catalog can give it its adapter without new ids (a promotion). An
assignment may also name `adapter_version`; this tool knows contract version 1, the one both builds
implement.

## 3. Build the catalog

```
python3 tools/generate_iron_glyph_catalog.py pack \
    --inventory glyph-inventory.json --adapters adapters.json \
    --name example_addon --out build/catalog \
    --loader forge \
    --namespace example_addon \
    [--names en_us.json] [--previous OLD.json] [--envelope 10] \
    [--jar --mod-id example_addon_glyphs --mod-version 1.0.0]
```

The same tool is `tools/glyph_catalog_pack.py`. It ships unchanged in both builds; pass
`--loader neoforge` for a 1.21.1 catalog. It writes the following files:

- `<name>.json`, the catalog (schema 2).
- `<name>-report.txt`, one line per spell: its status, levels, registered levels, adapter and the reason.
- With `--jar`, `<mod-id>-<version>.jar`. This is a catalog-only mod (`lowcodefml`) that carries the
  catalog, one item model per glyph id and the glyph names. It declares a dependency on
  `ars_n_spells` 3.4.1 or later.

The tool applies these rules, and refuses with a message naming the input and field when one is broken:

- **Inputs are strict JSON.** Numbers must be whole numbers inside their bounds (`2.5`, `"3"`,
  `true` and values beyond the bounds are refused, never truncated or wrapped); flags must be
  `true`/`false`; ids must be namespaced; unknown fields, duplicate keys, a spell listed twice, and
  `NaN` are refused.
- **Levels.** A spell registers base levels from its native minimum up to `--envelope` (default 10,
  at most 99) or its native maximum, whichever is higher; a native maximum above 99 is reported,
  and levels above 99 have no glyph. `rarity[i]` is the rarity of level `min + i`.
- **With `--previous`** (the last published version of the same catalog), identities only grow: a
  spell that left the inventory is kept as `retired`; no registered maximum shrinks; a native
  minimum that rose keeps the lower levels registered (dormant); one that fell adds the levels
  below. A placeholder may be promoted to its first adapter. A published adapter contract may only
  move to a newer version of the same adapter, never to another adapter and never back.
- **The target build's own rules.** Before anything is written, the catalog is validated against
  the chosen loader's catalog rules and limits (Forge 1,024 spells and 4,096 glyphs, NeoForge 2,048
  and 8,192).
- ANS's own proxy spells and Iron's `none` spell are never catalogued.

The package is built in a hidden staging directory inside `--out`, checked (the jar is read back), and
only then published with atomic renames. A refused build removes the staging directory and leaves the
files already in `--out` exactly as they were.

Keep every catalog you publish and pass it as `--previous` next time. Its ids are now part of
players' worlds.

## 4. Install it

Put the catalog in one of these places, on the **server and every client**:

- the catalog-only jar in `mods/`; or
- `<name>.json` in `config/ars_n_spells/glyph_catalogs/`; or
- an addon can ship `ans_glyph_catalog/<name>.json` in its own jar.

Then restart. Identities register at startup; `/reload` cannot add them. A client whose catalogs
differ from the server's is disconnected with both identity digests.

A loose catalog needs no resource pack: an always-on, hidden built-in pack generates an item model and
a name for every identity that no installed file supplies (a catalog-only jar's and ANS's own files
always win). Each glyph shows its spell's icon from the catalogued icon or Iron's standard place, and
ANS's fallback art when the icon is not installed. Level markers exist for I to X; a higher level
shows the plain glyph rather than a missing texture.

At startup each source is accepted whole or rejected whole, with the reason in the log and in
`/ans glyph report`. When two sources declare the same spell:

- their identity ranges merge (the union; a source can extend a spell's levels, never remove one);
- behaviour (the adapter contract) has one owner. A placeholder contributes identities only. Only a
  later version of the **same** catalog (the same `catalog` name) may give a placeholder its first
  adapter, or move an adapter to a newer contract version; the shipped Iron's catalog is never
  re-behaved by another source. Anything else that would change a published adapter rejects the
  source.

A contract version newer than the running build implements registers its identities dormant
("needs a newer Ars 'n' Spells") instead of executing under an older contract.

## 5. Check it

- `/ans glyph report`: no catalog error should be listed for it. Forge also lists each source with
  the spells and glyphs it added. The startup log names any rejected source with the reason.
- `/ans glyph explain <spell> [level]`: each level's status, the live maximum and the school.
- Learn a glyph, cast it with Self and with Touch or Projectile, and press a scroll of it. Check that
  the effect, the price and the cooldown match the native spell.

## Removing the addon later

ANS keeps its own copy of every optional catalog it has accepted, in
`config/ars_n_spells/retained_glyph_catalogs/` (named by catalog and content hash, written once,
never edited). If a package is removed, its retained copy still registers every identity, dormant
("its catalog package is not installed"): glyphs stay in books, inventories, knowledge and Glyph
Press slots, and work again when the package is reinstalled. A retained copy never changes how a
spell is cast. A copy whose content no longer matches the hash in its name is not read, and the
log says so.

If both the package and its retained copy are gone, the world refuses to start, before any level,
chunk or player is loaded, with a report that names each missing spell and levels, the catalog and
version that registered them and the files they were last loaded from. Nothing in the world
changes. Reinstall the package or restore the copy and start again; to drop those glyphs for good,
start once with `-Dars_n_spells.acceptMissingGlyphIdentities=true`.

## Packages ANS ships

Each package is a directory `catalogs/<name>/` in the source tree (`package.json`, the exported
`inventory.json` and the `adapters.json` with a reason per spell). `./gradlew build` builds them into
`build/catalog-packages/`: a catalog-only jar `<mod_id>-<version>.jar` and the same catalog as a loose
`<name>.json`.

| Package | Mod id | For | Contents |
|---|---|---|---|
| `wind_spellbooks` | `ans_catalog_wind_spellbooks` | Wind's Spellbooks (Forge 1.20.1-1.0.0, NeoForge 1.0.1) | 7 spells, I-X; 5 castable, Iron Slash and Tornado dormant ([below](#winds-spellbooks)) |
| `irons_envelope_15` | `ans_catalog_irons_envelope_15` | Iron's own spells | levels XI-XV for every spell of the shipped catalog, as placeholders that merge under the shipped adapters |

### Wind's Spellbooks

The inventory was exported from a server running Wind's Spellbooks with Iron's 3.16.3 (Forge
1.20.1-1.0.0, NeoForge 1.0.1). Every spell is in the `wind_spellbooks:wind` school and registers I-X.
Each verdict is checked by a GameTest in `WindSpellbooksGameTests` (batch `ans_wind_spellbooks`, run
when Wind's Spellbooks is installed), on both builds.

| Spell | Native levels | Forge adapter | NeoForge adapter (target) | Verdict | What the GameTest observes |
|---|---|---|---|---|---|
| Aeropic (`wind_spellbooks:aeropic`) | 1-4 | `caster` | `self` (`self`) | supported | the caster gains Aeropic, and the cast is paid (`aeropicAndTailwindBuffTheCaster`) |
| Tailwind (`wind_spellbooks:tailwind`) | 1-3 | `caster` | `self` (`self`) | supported | the caster gains Tailwind (same test); Amplify stops at the live maximum III and IV is dormant (`glyphsTeachRoundTripAndKeepIronsLimits`) |
| Almighty Push (`wind_spellbooks:almighty_push`) | 1-10 | `caster` | `self` (`self`) | supported | an Almighty Push entity forms on the caster (`almightyPushSpawnsOnTheCaster`) |
| Wind Jump (`wind_spellbooks:wind_jump`) | 1-3 | `caster` | `self` (`self`) | supported | the caster gains Slow Falling (`windJumpLaunchesTheCaster`) |
| Wind Blade (`wind_spellbooks:wind_blade`) | 1-10 | `projectile` | `projectile` (`any`) | supported | a touched pig is wounded (`windBladeHitsTheTouchedTarget`); a Wind Blade III scroll presses into exactly Wind Blade III (`aWindScrollPressesIntoItsExactGlyph`); the glyph teaches and its recipe round-trips |
| Tornado (`wind_spellbooks:tornado`) | 1-5 | none (placeholder) | none (placeholder) | unsupported | dormant: the cast is refused unpaid and no Tornado is built (`tornadoIsDormantOnThisIronsBuild`). The native spell fails on Iron's 3.16.3 (`NoSuchFieldError: fireTransitionTick` in Wind's `TornadoEntity`), on both builds |
| Iron Slash (`wind_spellbooks:iron_slash`) | 1-5 | none (placeholder) | none (placeholder) | unsupported | dormant: refused unpaid, no dash (`ironSlashIsDormantAndRefusesUnpaid`). It computes its own dash path before teleporting, which no adapter can place at what a form hit |

### Levels above X

Iron's maximum level is a live setting; glyph identities are fixed at startup. The shipped catalog
registers I-X for every spell. For a server whose Iron's configuration allows levels up to 15:

1. install `ans_catalog_irons_envelope_15-1.0.0.jar` (or `irons_envelope_15.json` in
   `config/ars_n_spells/glyph_catalogs/`) on the server **and every client**;
2. restart. Every Iron's spell now registers I-XV; I-X are unchanged;
3. Iron's maximum still decides what is usable: a level above it is dormant, and a per-spell maximum
   applies to that spell only. A Firebolt XV scroll presses into Firebolt XV, which teaches, saves
   and casts at level 15 (GameTest `envelope15_aLevelXVScrollPressesTeachesAndCasts`).

For levels above XV, build a larger envelope with the tool (`--envelope N`, at most 99, within the
loader's glyph limit) from your own inventory. A scroll above the registered envelope is refused by
the Press ("no glyph for this level"), never pressed into a lower level.
