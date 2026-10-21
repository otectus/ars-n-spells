## 3.4.0: imported Iron's glyphs and the Glyph Press

There is one configuration file, the per-world server config
`<world>/serverconfig/ars_n_spells-server.toml` (Forge `ModConfig.Type.SERVER`, synced to
clients). 3.4.0 adds a section to it, `Imported Iron's Glyphs`; it adds no second file. Per-spell
behaviour lives in datapacks (below), not in hundreds of TOML keys.

### Settings and defaults

| Key | Default | Range | Effect |
| --- | --- | --- | --- |
| `enable_iron_glyphs` | `true` | | Imported glyphs can be learned, cast and pressed. `false` makes every one dormant. |
| `enable_glyph_press` | `true` | | The Press may start new jobs. `false` cancels running jobs with inputs and Source kept. |
| `iron_glyph_mana_bonus_policy` | `per_spell` | `per_spell`, `native_per_variant` | How learned imported glyphs count toward Ars's per-glyph mana bonus: all levels of one spell as one glyph, or every level as a glyph. |
| `max_imported_effects_per_spell` | `4` | 1-8 | Imported glyphs one Ars spell may contain (editor and cast time). |
| `max_foreign_emissions_per_cast` | `16` | 1-64 | Iron's executions one Ars cast may produce, counting splits and repeats; each is priced. |
| `max_active_glyph_sessions_per_player` | `8` | 1-32 | Casts with imported glyphs still in flight per player. |
| `glyph_press_source_capacity` | `4000` | 1000-100000 | A Press's Source buffer. A conversion costing more is reported as unavailable. |
| `glyph_press_source_scan_interval_ticks` | `20` | 5-200 | Ticks between a working Press's requests to nearby jars. Idle presses never ask. |
| `glyph_press_source_radius` | `4` | 1-8 | How far a working Press looks for jars (loaded positions only, Ars's own lookup). |

The hard ceilings in the ranges are code constants. Existing settings still apply to imported
glyphs: the cross-cast cost multiplier, conversion rates, dual-cost shares, mana mode,
discounts, the cooldown system and school mappings. No duplicate toggle was added for any of
them.

### When changes take effect

- All nine keys are read where they are used (each cast, request, tick or mana calculation), so
  editing the file or using the in-game config screen applies them without a restart.
- Turning `enable_iron_glyphs` off makes imported glyphs dormant: learned glyphs stay known,
  spells and items that contain them are kept, and a cast containing one is refused with an
  explanation before anything is paid. Turning it on again restores them; nothing has to be
  relearned or re-pressed.
- Turning `enable_glyph_press` off cancels running jobs (inputs unlocked, reserved Source back in
  the buffer) and refuses new ones. Every item in a Press stays recoverable.

### Datapack behaviour layer (`data/<namespace>/ans_iron_glyphs/**.json`)

Reloaded with `/reload`. Each file has `"schema_version": 1` and a `"type"`:

- `ars_n_spells:press_tiers`: Source cost, catalyst tag and count, and duration per conversion
  tier. ANS ships `data/ars_n_spells/ans_iron_glyphs/press_tiers.json` with the defaults
  (tier I: 500 Source, 1 gem, 100 ticks; II: 1,500, 2, 140; III: 3,000, 4, 200).
- `ars_n_spells:spell` (the default type): one spell's `enabled` flag and per-level `ars_tier`,
  `source_cost`, `duration_ticks` and `catalyst`. An optional `adapter` must name a known adapter
  and match the catalogued one: adapters belong to the startup catalog.
- `ars_n_spells:policy`: `allow_spells`, `deny_spells`, `allow_schools`, `deny_schools`.

Precedence:

1. Iron's own state wins over everything: a spell Iron's disables, or a level Iron's no longer
   allows, is unavailable whatever a datapack says. An allowlist cannot enable it.
2. An explicit deny (spell or school) wins over every allow.
3. When any allow list is present, only what it names is allowed.
4. A per-spell `"enabled": false` disables that spell.
5. For the same spell, the file from the higher pack in the pack list wins. Two files for the
   same spell in one pack are an error. Files at the same path follow normal datapack
   replacement (the top pack's copy is the one read).

A file can only describe glyphs the startup catalog registered. Naming an unknown spell, a level
beyond the catalog, an unknown adapter, an out-of-range number or a malformed file is an error.
A reload whose files contain any error is rejected as a whole and the previous valid behaviour
stays in force. On the first load after a server starts there is no previous behaviour, so the
valid files apply and every spell named by a broken file fails closed (unavailable) until fixed.
`/ans glyph mappings` lists the winning file and pack for each override and the errors of the
last rejected load. Running Press jobs whose price or availability a reload changed are
cancelled cleanly; unrelated jobs continue. Clients receive the effective behaviour on login
and after every reload.

### Not configurable here

The glyph ids and the spell-to-adapter assignment are fixed by the startup catalog
(`ars_n_spells/iron_glyph_catalog.json` inside the jar); see `iron-glyph-coverage.md`.
