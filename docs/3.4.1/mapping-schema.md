# Imported-glyph mapping files: the canonical schema (3.4.1)

Data packs tune imported Iron's glyphs with JSON files in `data/<namespace>/ans_iron_glyphs/`, at
any depth. `/reload` applies them. Since 3.4.1, both builds read one canonical schema with the same
rules, and 3.4.0 files of either build are still accepted.

A data pack changes behaviour only. It cannot register a glyph, add a level or invent an adapter.
Those are identity, which comes from catalogs at startup (see [identity-and-levels.md](identity-and-levels.md)).

## Rules for every file

- `schema_version` is required: `2`, or `1` for a 3.4.0 file.
- `type` chooses the document; it defaults to `ars_n_spells:spell`.
- An unknown field is an error. It is never silently ignored. Keys starting with `_` are comments.
- Numbers must be JSON numbers and whole, within their bounds. `10.5`, `"60"`, `true` and `1e12`
  for a cost are all errors. `2.0` is read as 2. Nothing is truncated or clamped.
- Every error names the file, its pack and the field. On Forge, for example:
  `my_pack:bolt [file/my_pack]: levels.1.source_cost: expected a whole number, got 10.5`.

## `ars_n_spells:spell`: one spell

```json
{
  "schema_version": 2,
  "type": "ars_n_spells:spell",
  "spell": "irons_spellbooks:firebolt",
  "enabled": true,
  "adapter": "ars_n_spells:self",
  "defaults": { "duration_ticks": 120, "catalyst": { "tag": "c:gems/diamond", "count": 2 } },
  "levels": {
    "1": { "enabled": false },
    "5": { "ars_tier": 3, "source_cost": 5000 }
  }
}
```

| Field | Value |
|---|---|
| `spell` | required; a catalogued Iron's spell id |
| `enabled` | `false` makes every level of the spell dormant ("disabled by a data pack") |
| `adapter` | optional; the spell's catalogued adapter (a no-op confirmation), or `ars_n_spells:self` to restrict the spell to Self casts. Anything else is an error: an adapter is identity and cannot change |
| `defaults` | a level override applied to every level |
| `levels` | level number (`"1"` to `"99"`, no leading zero) to a level override; only registered levels |

A **level override** has these fields, all optional:

| Field | Bounds | Meaning |
|---|---|---|
| `enabled` | `true` / `false` | this level's availability; overrides `defaults.enabled` |
| `ars_tier` | 1 to 3 | the glyph's Ars tier, which also picks its Press tier |
| `source_cost` | 0 to 100,000 | Source for one Press conversion |
| `duration_ticks` | 1 to 72,000 | conversion time (72,000 ticks is one hour; Forge 3.4.0 allowed only 12,000) |
| `catalyst.tag` | item tag id | the catalyst item tag |
| `catalyst.count` | 0 to 64 | how many catalysts; `0` means none, and an empty slot is accepted |

How a value is resolved. A spell-wide `enabled: false` disables every level. Otherwise a level's
own `enabled` decides, and if it has none, `defaults.enabled` does. For Press terms and the tier,
the level's value is used first, then `defaults`, then the tier's terms. The tier is the override,
or else the glyph's tier from Iron's live rarity for that level: Common is I, Uncommon and Rare
are II, Epic and Legendary are III.

## `ars_n_spells:press_tiers`: the three Press tiers

```json
{
  "schema_version": 2,
  "type": "ars_n_spells:press_tiers",
  "tiers": {
    "1": { "source_cost": 500, "duration_ticks": 100, "catalyst": { "tag": "ars_n_spells:glyph_press_catalysts", "count": 1 } },
    "3": { "source_cost": 2500, "duration_ticks": 200, "catalyst": { "count": 0 } }
  }
}
```

Each listed tier needs `source_cost`, `duration_ticks` and `catalyst`. Use `"count": 0` for no
catalyst. The tag defaults to `ars_n_spells:glyph_press_catalysts` (Source gems). An unlisted tier
keeps the shipped terms: tier I is 1 gem, 500 Source and 100 ticks; tier II is 2 gems, 1,500
Source and 140 ticks; tier III is 4 gems, 3,000 Source and 200 ticks. Every catalyst tag named by
any tier or override may be placed in the Press's catalyst slot, by hand, shift-click or hopper.

## `ars_n_spells:policy`: allow and deny lists

```json
{
  "schema_version": 2,
  "type": "ars_n_spells:policy",
  "deny_schools": ["irons_spellbooks:blood"],
  "allow_spells": ["irons_spellbooks:heal"]
}
```

The fields are `allow_spells`, `deny_spells`, `allow_schools` and `deny_schools`, each a list of
ids. The lists of every policy file are merged.

- **Deny wins.** A spell is refused if it is denied, or if its live school is denied.
- If any allow list is non-empty, only allowed spells and spells of allowed schools are available.
- Schools are matched against the spell's **live** school, the one Iron's configuration gives it on
  this server, not the school it was catalogued with.
- An unknown spell id in an allow or deny list is an error.

A 3.4.0 NeoForge policy file with `"type": "policy"` is accepted only with `"schema_version": 1`.

## Precedence and conflicts

- **Different priority.** A file from a higher-priority pack (later in `/datapack list`) replaces a
  lower pack's file for the same spell. The winner is reported.
- **Same priority.** Two files for one spell in the same pack, or at the same priority, conflict.
  This is reported and the spell **fails closed**: dormant, "disabled by a data pack", until the
  conflict is fixed. Two different declarations of one Press tier at the same priority are reported
  and the shipped terms stay in force; identical declarations agree.
- **Invalid file.** A spell named by an invalid file also fails closed. The error names it.

## Reloads

Every file is read into one candidate, which is validated as a whole. A valid candidate is
published atomically to the server and every client: Press, tooltips, editor, JEI and casts all
switch together. An **invalid** candidate is rejected, the last valid mappings stay in force, and
every error is logged. At a server's first load there is nothing to keep, so the candidate is used
with its broken spells failing closed.

## Checking files before shipping them

- In game: `/ans glyph validate` (operator) checks the loaded data packs without applying anything.
- Offline: `python3 tools/validate_glyph_mappings.py [--catalog CATALOG.json ...] [--json] PACK...`.
  A PACK is a data pack directory or zip, lowest priority first. `--catalog` adds an addon's
  catalog. The exit status is 0 when valid, 1 when invalid, and 2 when the input cannot be used. The
  same tool is available as `tools/generate_iron_glyph_catalog.py validate-mappings ...`.

The game's two implementations (`GlyphMappingSnapshot` on Forge, `GlyphMappings` on NeoForge) and
the offline validator all run the same fixtures, `src/test/resources/ans_shared_fixtures/mappings`.
`tools/verify_loader_parity.py` checks that the fixtures and the validator are identical in both
checkouts.
