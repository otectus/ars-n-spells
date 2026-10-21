# Imported glyphs: adapter authoring (3.4.0)

Imported Iron's glyphs have two data layers. Neither runs code.

| Layer | Where | Decides | When it is read |
| --- | --- | --- | --- |
| **Catalog manifest** | `ans_glyph_catalog/<name>.json` inside a **mod jar** | Which Iron's spells exist as glyphs, and at which levels (their registered ids), with each spell's adapter and target | Once, at mod construction, before registries freeze |
| **Mapping** | `data/<namespace>/ans_iron_glyphs/<any path>.json` in a **data pack** | Availability and tuning: enabled levels, Ars tier, Press Source, time and catalyst, Self-only restriction, allow/deny policy | At server start and on every `/reload` |

A data pack can choose among the adapters this build implements, and tune them. It cannot invent a new behaviour, register a new glyph, or turn an unsupported spell into a supported one.

## Built-in adapters

| Adapter | What the glyph does where Ars resolves it | Typical spells |
| --- | --- | --- |
| `ars_n_spells:self` | Runs the spell on the caster. The glyph must be cast with Self. | Heal, Oakskin, Charge |
| `ars_n_spells:projectile` | With Self, launches the spell's own projectile from the caster. Otherwise the native projectile strikes what Ars hit, with its owner, damage and on-hit behaviour. Accelerate and Decelerate change its speed. | Firebolt, Icicle, Acid Orb |
| `ars_n_spells:entity` | Runs the spell on the living entity Ars hit, never on the caster. | Slow, Blight, Chain Lightning |
| `ars_n_spells:position` | Runs the spell at the impact point. | Lightning Bolt, Black Hole, Blizzard |
| `ars_n_spells:block` | Runs the spell on the block Ars hit. | Touch Dig |
| `ars_n_spells:teleport` | Moves only the caster, to a safe, loaded, in-border spot at the target within the spell's own range. | Teleport, Blood Step |
| `ars_n_spells:summon` | Summons for the caster, and moves the summons to a safe spot at the target. Ownership, lifetime and count limits stay Iron's own. | Summon Vex, Raise Dead |
| `ars_n_spells:channel` | Channels from the caster: a pulse every 10 ticks, each paid before it runs. Extend Time and Duration Down change the pulse count. | Cone of Cold, Ray of Siphoning |

Every adapter goes through Iron's own lifecycle:

- `SpellPreCastEvent`, then the spell's own cast conditions, then `SpellOnCastEvent`, then payment, then `onCast`;
- the spell's cooldown, keyed by spell id, and its recast stages;
- learning (Eldritch spells) and adventure-mode rules.

Charged (LONG) spells wait their effective cast time first; Accelerate shortens it and Decelerate lengthens it. Damage interrupts a charge unless Iron's would not interrupt it (a Concentration Amulet, or a damage type in Iron's long-cast-ignore tag).

## Catalog manifest (mod jar)

A mod that adds Iron's spells ships `ans_glyph_catalog/<name>.json` in its jar. A data pack cannot add one, because registry identities must exist before the game finishes loading. The client and the server must load the same manifests: if their catalog hashes differ, the client is disconnected with a message naming both hashes.

```json
{
  "schema_version": 1,
  "spells": [
    {
      "spell": "example_addon:frost_lance",
      "name": "Frost Lance",
      "school": "irons_spellbooks:ice",
      "cast_type": "INSTANT",
      "min_level": 1,
      "max_level": 5,
      "rarity": ["COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY"],
      "default_tier": [1, 2, 2, 3, 3],
      "requires_learning": false,
      "enabled_by_default": true,
      "adapter": "ars_n_spells:projectile",
      "target": "any",
      "final_effect_only": false,
      "status": "supported"
    }
  ]
}
```

| Field | Meaning |
| --- | --- |
| `spell` | The Iron's spell id, as registered in Iron's spell registry |
| `name` | Fallback display name, used when the spell's own translation is missing |
| `school` | The Iron's school id; it maps to Ars schools through ANS's school table |
| `cast_type` | `INSTANT`, `LONG` (charged) or `CONTINUOUS` (channelled) |
| `min_level`, `max_level` | Catalogued levels, 1 to 99. Each level is its own glyph. |
| `rarity`, `default_tier` | One entry per level. Tiers must be 1 to 3. The tier from Iron's live rarity wins when Iron's is loaded. |
| `requires_learning` | The spell needs Iron's own learning (Eldritch spells) |
| `enabled_by_default` | Informational: whether Iron's default config enables the spell |
| `adapter` | One of the built-in adapters above. Anything else rejects the entry. |
| `target` | `self`, `entity`, `entity_or_self`, `block`, `position` or `any`. The spell book editor enforces it against the form. |
| `final_effect_only` | The spell moves the caster away, so it must be the last effect of a spell |
| `status` | `supported`, or `unsupported` (no identities are registered for it) |

Each supported level registers the spell part `ars_n_spells:iron/<namespace>/<path>/level_<n>` and its glyph item `ars_n_spells:glyph_iron/<namespace>/<path>/level_<n>`. For the example, these are `ars_n_spells:iron/example_addon/frost_lance/level_5` and `ars_n_spells:glyph_iron/example_addon/frost_lance/level_5`. Spell books and known-glyph lists store the part id, and inventories store the item id. This encoding is frozen. Never reuse an id for another spell. To retire a spell, set it `unsupported` or disable it in a mapping: either way its saved glyphs stay as dormant ANS parts and never turn into another glyph.

The rules, in full:

- A malformed entry, an unknown adapter, a tier outside 1 to 3, or a manifest with another `schema_version` is reported and registers nothing.
- When two manifests name the same spell, the first in sorted source order wins and the other is reported.
- Identity collisions are reported.
- Limits: 2,048 spells, 8,192 glyph variants, 2 MiB per manifest.

The working example is `examples/iron-glyph-addon-manifest/ans_glyph_catalog/example_addon.json`. `ExampleGlyphFilesTest` parses it and checks its identities.

## Mapping (data pack)

```json
{
  "schema_version": 1,
  "spell": "irons_spellbooks:firebolt",
  "adapter": "ars_n_spells:projectile",
  "enabled": true,
  "defaults": { "source_cost": 800 },
  "levels": {
    "3": { "ars_tier": 2, "source_cost": 1500, "duration_ticks": 140,
           "catalyst": { "tag": "ars_n_spells:glyph_press_catalysts", "count": 2 } },
    "10": { "enabled": false }
  }
}
```

| Field | Bounds | Meaning |
| --- | --- | --- |
| `spell` | must be catalogued and supported | The spell this file tunes |
| `adapter` | the catalogued adapter, or `ars_n_spells:self` | `self` restricts the glyph to Self, the always-safe adapter |
| `enabled` | boolean | Disables every level when `false` |
| `defaults` | an override object | Applies to every level unless a level says otherwise |
| `levels.<n>` | an override object; `<n>` must be a catalogued level | Overrides for one level. A reload cannot add a level. |
| `enabled` (override) | boolean | Disables this level |
| `ars_tier` | 1 to 3 | Ars book tier the glyph requires, and the Press's conversion tier |
| `source_cost` | 0 to 100,000 | Source one conversion uses (the Press refuses a cost above its capacity) |
| `duration_ticks` | 1 to 72,000 | Conversion time |
| `catalyst.tag` | an item tag id | Catalyst ingredient, `ars_n_spells:glyph_press_catalysts` (Source Gems) by default |
| `catalyst.count` | 0 to 64 | Catalysts one conversion uses |

A level's value wins over `defaults`, which wins over the built-in default for the tier (I: 1 gem, 500 Source, 100 ticks; II: 2, 1,500, 140; III: 4, 3,000, 200).

**Policy files** use `"type": "policy"` with `allow_spells`, `deny_spells`, `allow_schools` and `deny_schools` (id lists). Policies from every file are merged.

- An explicit deny always wins.
- When any allow list is non-empty, only allowed spells (by id or school) stay available.
- No pack can enable a spell that Iron's own config disables, or that the server turned off with `enable_iron_glyphs`.

**Precedence.**

- For one file path, the highest-priority pack's copy is used, as for any data-pack file.
- When two files map the same spell, the one with the lowest resource id wins, and the other is reported and ignored.
- `/ans glyph coverage` lists each mapped spell's winning file and pack.

**Validation.** Numbers must be finite and within bounds, spells and levels must be catalogued, and adapters must be allowed. A file that fails is rejected whole.

- At **first load**, the spells named by a rejected file fail closed (dormant, "Disabled by a data pack") and everything else applies.
- On a **reload**, any rejected file rejects the whole new set. The previous mappings stay in force, and `/ans glyph coverage` shows the rejected errors.
- A Press job whose terms changed is cancelled with its inputs and Source returned, rather than finishing under old terms.

The working example data pack is `examples/iron-glyph-mapping/`:

- `firebolt.json` tunes Source and tiers and disables level 10;
- `teleport_self_only.json` restricts Teleport to Self;
- `policy.json` denies Black Hole and the Eldritch school.

`ExampleGlyphFilesTest` loads it against the shipped catalog and checks each effect.

## Diagnostics

- `/ans glyph inspect`: the held glyph, or every imported glyph in the held spell book's current spell. It shows identity, adapter, target, tier, Press terms, dormancy and augments, and the composition errors the spell would get.
- `/ans glyph coverage` (operators): catalog hash and counts, coverage by adapter and status, dormancy counts, mapping winners and errors.
- `/ans glyph sessions [player]`: open imported-glyph casts and the latest outcomes for that player.

`docs/3.4.0/iron-glyph-coverage.md` (with `.json`) is generated from the shipped catalog by `python3 -I tools/generate_iron_glyph_catalog.py <inventory.json>`. The inventory is dumped from a development runtime with `-PglyphCatalogDump=<file>` on `runGameTestServer -PwithIronsRuntimeGameTests`.
