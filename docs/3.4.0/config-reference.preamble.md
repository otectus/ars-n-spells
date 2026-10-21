## Where the configuration lives

All gameplay settings are one **server** config:

- `<world>/serverconfig/ars_n_spells-server.toml` for each world;
- `defaultconfigs/ars_n_spells-server.toml` as the template for new worlds.

The server sends it to clients when they log in. Older releases used a global `config/ars_n_spells-common.toml`, which is no longer read; older notes that name it mean the server file. 3.4.0 adds no second file.

## 3.4.0 settings (section "Imported Iron's Glyphs")

| Key | Default | Bounds | Meaning |
| --- | --- | --- | --- |
| `enable_iron_glyphs` | `true` | | Whether imported glyphs can be learned and cast. Off makes every imported glyph dormant; ids stay registered, and books and known-glyph lists keep them. |
| `enable_glyph_press` | `true` | | Whether Glyph Presses start new jobs. Off cancels running jobs with inputs and Source returned; items in a Press stay retrievable. |
| `iron_glyph_mana_bonus_policy` | `per_spell` | `per_spell` or `native_per_variant` | Ars raises max mana and regeneration per known glyph. `per_spell` counts the level variants of one Iron's spell once; `native_per_variant` counts every variant, as Ars would. |
| `max_imported_effects_per_spell` | `4` | 1 to 8 (hard cap 8) | Imported glyphs allowed in one Ars spell; the spell book editor rejects more. |
| `max_foreign_emissions_per_cast` | `16` | 1 to 64 (hard cap 64) | Imported-effect executions one root cast may make, counting split, repeated and delayed deliveries. Further executions are refused and not charged. |
| `max_active_glyph_sessions_per_player` | `8` | 1 to 32 (hard cap 32) | Casts carrying imported glyphs that one player may have running at once (charges, channels, projectiles in flight). |
| `glyph_press_source_capacity` | `4000` | 500 to 100,000 | Source one Press stores, including a running job's reservation. A recipe that needs more Source than this cannot start ("Needs N Source, more than this Press can hold"). |
| `glyph_press_source_scan_interval_ticks` | `20` | 5 to 200 | How often a Press waiting for Source pulls from nearby jars. Idle Presses never scan. |
| `glyph_press_source_radius` | `4` | 1 to 8 | How far a waiting Press looks for loaded Source Jars (Ars's own source lookup, loaded chunks only). |

**Reload behaviour.** A change to any of these, by file edit, the config screen or `/ans`, applies immediately. The server rebuilds the effective glyph behaviour and sends it to every connected client. Casts already admitted keep the cost rules they were admitted under, and a Press job whose terms changed is cancelled cleanly.

**Fixed bounds.** One root cast with imported glyphs is tracked for at most **1,800 ticks (90 s)**: its charges, channels and projectiles in flight. The longest native casts in Iron's 1.21.1-3.16.3, at default attributes, fit well inside it: Greater Heal's 120-tick charge (about 188 ticks with two Decelerate) and Telekinesis's 220-tick channel (280 with three Extend Time). When the bound is reached, unspent reservations are released. A projectile still flying then resolves its imported glyph as not admitted: no effect, no cost. Effects that already happened, such as summons and buffs, are not affected.

## Settings 3.4.0 reuses

Imported glyphs add no second copy of an existing setting:

- `mana_unification_mode`, `conversion_policy`, `conversion_rate_ars_to_iron` and `conversion_rate_iron_to_ars`, and the dual-cost shares route each imported effect's Iron's cost exactly as for any cross-cast. `cross_cast_cost_multiplier` applies once per imported leg.
- The cooldown system and native cooldowns: an imported glyph starts its Iron's spell's own cooldown, keyed by spell id.
- Discounts that act through Iron's `SpellOnCastEvent` apply once to the imported leg.
- School mappings and affinity, and `scroll_cost_mode`, which never makes a learned glyph free.

Iron's own server config also applies:

- a spell disabled there makes its glyph dormant;
- a spell's maximum level there makes higher glyph levels dormant;
- `creativeMana` and `creativeCooldowns` decide whether creative players pay and wait.

Iron's builds its spell config when the first player joins or on `/reload`. Until then, ANS uses Iron's defaults as catalogued (enabled state, maximum level, rarity), and a Press job that reaches its end waits until the real config has loaded.

## Data-pack layer and precedence

Per-spell availability and Press tuning live in data packs (`data/<namespace>/ans_iron_glyphs/`), not here. Precedence, validation, reload rules and examples are in `adapter-authoring.md`. In short:

- An explicit deny wins.
- Level overrides beat defaults, which beat the built-in tier defaults.
- An invalid reload keeps the previous mappings.
- At first start, a rejected file's spells fail closed.

## Dormant glyphs

A dormant glyph keeps its id, its place in spell books and in what players know, and its stacks. It does nothing until the reason goes away, and there is nothing to relearn or re-press. The reasons, as tooltips and `/ans glyph inspect` show them:

- imported glyphs turned off on this server;
- Iron's Spells 'n Spellbooks is not installed;
- the spell is not installed;
- the spell is disabled by the server;
- disabled by a data pack;
- the level is above the spell's maximum on this server;
- the spell has no glyph support;
- the client is waiting for the server's glyph settings.
