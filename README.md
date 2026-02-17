# Ars 'n' Spells (v1.2.0)

Ars 'n' Spells bridges Ars Nouveau and Iron's Spells 'n Spellbooks for Minecraft 1.20.1 (Forge). It unifies mana behavior, scaling, and progression while keeping each mod playable on its own.

This README describes the current implementation in this repository (not marketing plans).

## Requirements

- Minecraft 1.20.1
- Forge 47.2.0
- Ars Nouveau 4.12.7 (required)
- Iron's Spells 'n Spellbooks 3.15.x (optional)

If Iron's is not installed, Ars 'n' Spells falls back to native Ars behavior.

## Features

### Mana unification modes

Configured by `mana_unification_mode` in `config/ars_n_spells-common.toml`:

- `iss_primary` (default): Iron's mana is the single source of truth. Ars mana reads and drains Iron's mana.
- `ars_primary`: Ars mana is the single source of truth. Iron's spells drain Ars mana.
- `hybrid`: Shared pool with Iron's as the primary reader, Ars as secondary for sync.
- `separate`: Separate pools. Cross-mod spell casts require dual costs.
- `disabled`: No mana integration.

### Gear perks and enchantments

Ars and Iron's gear can grant mana bonuses. This mod routes those bonuses to the active mana source:

- `iss_primary` / `hybrid`: Ars gear perks are applied to Iron's attributes (MAX_MANA and MANA_REGEN).
- `ars_primary`: Iron's gear perks are applied to Ars mana calculations.
- `separate`: Each mod's gear affects its own pool only (normal behavior).

Bonuses are sourced from:
- Attribute modifiers on equipment (Ars perk attributes and Iron's mana attributes).
- Ars `IManaEquipment` implementations.
- Mana-related enchantments (heuristic name match).
- Curios items, if Curios is present (reads worn items).

### Spell scaling

- Ars spell potency scales with Iron's spell power attributes.
- Elemental spell power attributes are applied when the first glyph indicates an element.

### Resonance (Iron's only)

Optional resonance tracks current mana percentage and boosts Iron's spell damage. It is computed server-side and synced to clients.

### Cooldowns

A unified cooldown system prevents cross-mod spell spam. Spells are grouped into categories and lock out similar spells across mods.

### Progression and affinity

- Ars spell casts can grant Iron's school attribute progression.
- Affinity tracks spell school usage and syncs to client.

### Cross-mod spell casting (experimental)

Items can store cross-mod spell NBT and cast the stored spell on right-click. The list tag is `arsnspells:cross_spells`.

Per-entry NBT:
- `spell_type`: `IRONS_SPELLBOOKS` or `ARS_NOUVEAU`
- Iron spell: `spell_id` (e.g., `irons_spellbooks:fireball`), `spell_level` (int), optional `cast_source`
- Ars spell: `ars_spell` (CompoundTag from `Spell.serialize()`)

Sneak-right-click cycles the selected entry. The selected index is stored in `arsnspells:cross_spell_index`.

Mana costs respect the active mode and conversion rates:
- `iss_primary` / `hybrid`: Ars costs are paid from Iron with `conversion_rate_ars_to_iron`.
- `ars_primary`: Iron costs are paid from Ars with `conversion_rate_iron_to_ars`.
- `separate`: costs split by `dual_cost_*`; the off-pool portion uses the relevant conversion rate.

## Configuration

Config file: `config/ars_n_spells-common.toml`

Common options:

| Option | Default | Notes |
| --- | --- | --- |
| `enable_mana_unification` | `true` | Enables all mana bridging logic; when `false`, each mod uses its native mana only. |
| `mana_unification_mode` | `iss_primary` | Chooses which mana pool is authoritative and how pools sync or split. |
| `conversion_rate_ars_to_iron` | `1.0` | Multiplier applied when Ars costs are paid from Iron's pool (higher = more expensive Ars casts). |
| `conversion_rate_iron_to_ars` | `1.0` | Multiplier applied when Iron's costs/bonuses are paid from Ars pool (higher = more expensive Iron's casts). |
| `dual_cost_ars_percentage` | `0.5` | In `separate`, percent of cost taken from Ars pool when cross-casting. |
| `dual_cost_iss_percentage` | `0.5` | In `separate`, percent of cost taken from Iron's pool when cross-casting. |
| `respect_armor_bonuses` | `true` | When enabled, applies mana-related armor perks to the active mana source. |
| `respect_enchantments` | `true` | When enabled, mana-related enchantments add mana/regen bonuses. |
| `enable_resonance_system` | `true` | Enables full-mana resonance bonuses (Iron's only). |
| `enable_cooldown_system` | `true` | Enables unified cooldown categories across both mods. |
| `enable_progression_system` | `true` | Enables Ars casts to grant Iron's school progression bonuses. |
| `enable_affinity_system` | `true` | Enables affinity/attunement tracking for spell schools. |
| `debug_mode` | `false` | Enables verbose debug logging for troubleshooting. |

Changes are safest with a client/server restart.

## Mana bars

The mod hides redundant mana bars based on mode:

- `iss_primary`: hides Ars mana bar
- `ars_primary` / `hybrid`: hides Iron's mana bar
- `separate` / `disabled`: both bars may show

## Troubleshooting

- Ars mana not changing in `iss_primary`:
  - Ensure Iron's is installed and compatible (3.15.x).
  - Check logs for mixin failures or missing attributes.

- Gear perks not affecting mana:
  - Confirm `respect_armor_bonuses=true` and the correct mode.
  - In `separate`, perks are not cross-applied by design.

- Double mana bars:
  - Verify your mode and look for overlay conflicts from other UI mods.

## Building from source

1. Install JDK 17.
2. Place dependency jars in the repo root (as configured in `build.gradle`).
3. Run:

```powershell
.\\gradlew.bat build
```

Output jar: `build/libs/ars_n_spells-<version>.jar`

## License

GNU GPLv3
