# Ars 'n Spells (v1.2.0)

Ars 'n Spells bridges Ars Nouveau and Iron's Spells 'n Spellbooks for Minecraft 1.20.1 (Forge). It unifies mana behavior, scaling, and progression while keeping each mod playable on its own.

**NEW in v1.2.0:** Full support for Covenant of the Seven curios! Ring of Virtue and Blasphemy curios now provide mana discounts, and the Cursed Ring properly consumes Life Points (LP) for spell costs with configurable death penalties.

## Requirements

*   Minecraft 1.20.1
*   Forge 47.2.0+
*   Ars Nouveau 4.12.7+ (required)
*   Iron's Spells 'n Spellbooks 3.15.x (optional but recommended)
*   Covenant of the Seven (Sanctified Legacy) 2.2.5+ (optional - for curio discounts and LP costs)
*   Blood Magic 3.3.5+ (optional - required for Cursed Ring LP consumption)

If Iron's is not installed, Ars 'n Spells falls back to native Ars behavior. Curio features require Covenant of the Seven and Blood Magic to be installed.

## Features

### Covenant of the Seven Integration (NEW in v1.2.0)

**Ring of Virtue & Blasphemy Curio Discounts:**
- **Ring of the Seven Virtues**: Provides 20% mana cost reduction for all Ars Nouveau spells (configurable)
- **Blasphemy Curios**: Provide 15% base mana discount + 10% bonus for matching spell schools (configurable)
  - All 13 Blasphemy variants supported (Fire, Ice, Lightning, Holy, Ender, Blood, Evocation, Nature, Eldritch, Aqua, Geo, Wind, Dormant)
  - School matching: Fire Blasphemy + Fire spell = 25% total discount
  - Stacking: Ring of Virtue + Matching Blasphemy = up to 40% total discount
- Fully configurable discount percentages and stacking behavior

**Cursed Ring LP Consumption:**
- **Ars Nouveau spells** consume Life Points (LP) from Blood Magic instead of mana when wearing the Ring of Seven Curses
- **Iron's Spellbooks spells** consume LP with improved messaging and death prevention
- **Configurable LP formulas** with 15 options for complete control:
  - Separate multipliers for Ars spell tiers (Tier 1/2/3)
  - Separate multipliers for Iron's spell levels and rarities (Common/Uncommon/Rare/Epic/Legendary)
  - Adjustable base conversion rates and minimum costs
- **Death Penalty System**:
  - Safe Mode (default): Spell cancelled, 1 heart damage, you survive
  - Death Mode: Spell casts, you die instantly
  - Configurable via `death_on_insufficient_lp` setting
- **Clear Messages**: Shows "Consumed XXX LP" or "Insufficient LP - Spell Cancelled"
- **Blasphemy Discounts**: Apply to LP costs (85% reduction for matching schools)

### Mana unification modes

Configured by `mana_unification_mode` in `config/ars_n_spells-common.toml`:

*   `iss_primary` (default): Iron's mana is the single source of truth. Ars mana reads and drains Iron's mana.
*   `ars_primary`: Ars mana is the single source of truth. Iron's spells drain Ars mana.
*   `hybrid`: Shared pool with Iron's as the primary reader, Ars as secondary for sync.
*   `separate`: Separate pools. Cross-mod spell casts require dual costs.
*   `disabled`: No mana integration.

### Gear perks and enchantments

Ars and Iron's gear can grant mana bonuses. This mod routes those bonuses to the active mana source:

*   `iss_primary` / `hybrid`: Ars gear perks are applied to Iron's attributes (MAX\_MANA and MANA\_REGEN).
*   `ars_primary`: Iron's gear perks are applied to Ars mana calculations.
*   `separate`: Each mod's gear affects its own pool only (normal behavior).

Bonuses are sourced from:

*   Attribute modifiers on equipment (Ars perk attributes and Iron's mana attributes).
*   Ars `IManaEquipment` implementations.
*   Mana-related enchantments (heuristic name match).
*   Curios items, if Curios is present (reads worn items).

### Spell scaling

*   Ars spell potency scales with Iron's spell power attributes.
*   Elemental spell power attributes are applied when the first glyph indicates an element.

### Resonance (Iron's only)

Optional resonance tracks current mana percentage and boosts Iron's spell damage. It is computed server-side and synced to clients.

### Cooldowns

A unified cooldown system prevents cross-mod spell spam. Spells are grouped into categories and lock out similar spells across mods.

### Progression and affinity

*   Ars spell casts can grant Iron's school attribute progression.
*   Affinity tracks spell school usage and syncs to client.

### Cross-mod spell casting (experimental)

Items can store cross-mod spell NBT and attempt to cast via a common adapter. Current adapter logic is placeholder (cost calculation and logging only); full spell execution is not yet integrated.

## Configuration

Config file: `config/ars_n_spells-common.toml`

### New Configuration Options (v1.2.0)

**Curio Discount System (5 options):**
- `enable_curio_discounts` - Master toggle for Ring of Virtue and Blasphemy discounts
- `virtue_ring_discount` - Mana discount from Ring of Virtue (default: 0.2 = 20%)
- `blasphemy_discount` - Base mana discount from Blasphemy curios (default: 0.15 = 15%)
- `blasphemy_matching_school_bonus` - Additional discount for matching schools (default: 0.1 = 10%)
- `allow_discount_stacking` - Allow Ring of Virtue and Blasphemy to stack multiplicatively

**Cursed Ring LP System (2 options):**
- `death_on_insufficient_lp` - If true: spell casts but you die; if false: spell cancelled, 1 heart damage (default: false)
- `show_lp_cost_messages` - Show LP cost messages in action bar (default: true)

**LP Calculation - Ars Nouveau (5 options):**
- `ars_lp_base_multiplier` - Base LP conversion rate (default: 10.0)
- `ars_lp_tier1_multiplier` - Tier 1 glyph multiplier (default: 1.5)
- `ars_lp_tier2_multiplier` - Tier 2 glyph multiplier (default: 2.0)
- `ars_lp_tier3_multiplier` - Tier 3 glyph multiplier (default: 2.5)
- `ars_lp_minimum_cost` - Minimum LP cost per spell (default: 100)

**LP Calculation - Iron's Spellbooks (8 options):**
- `irons_lp_base_multiplier` - Base LP conversion rate (default: 10.0)
- `irons_lp_per_level_multiplier` - Additional LP per spell level (default: 0.1 = 10%)
- `irons_lp_minimum_cost` - Minimum LP cost per spell (default: 100)
- `irons_lp_common_multiplier` - Common rarity multiplier (default: 1.0)
- `irons_lp_uncommon_multiplier` - Uncommon rarity multiplier (default: 1.5)
- `irons_lp_rare_multiplier` - Rare rarity multiplier (default: 2.0)
- `irons_lp_epic_multiplier` - Epic rarity multiplier (default: 3.0)
- `irons_lp_legendary_multiplier` - Legendary rarity multiplier (default: 5.0)

**Total: 22 new configuration options for complete control over curio discounts and LP costs!**

### Core Configuration Options

| Option                      |Default     |Notes                                                                                                       |
| --------------------------- |----------- |----------------------------------------------------------------------------------------------------------- |
| <code>enable_mana_unification</code> |<code>true</code> |Enables all mana bridging logic; when <code>false</code>, each mod uses its native mana only.               |
| <code>mana_unification_mode</code> |<code>iss_primary</code> |Chooses which mana pool is authoritative and how pools sync or split.                                       |
| <code>conversion_rate_ars_to_iron</code> |<code>1.0</code> |Multiplier applied when Ars costs are paid from Iron's pool (higher = more expensive Ars casts).            |
| <code>conversion_rate_iron_to_ars</code> |<code>1.0</code> |Multiplier applied when Iron's costs/bonuses are paid from Ars pool (higher = more expensive Iron's casts). |
| <code>dual_cost_ars_percentage</code> |<code>0.5</code> |In <code>separate</code>, percent of cost taken from Ars pool when cross‑casting.                           |
| <code>dual_cost_iss_percentage</code> |<code>0.5</code> |In <code>separate</code>, percent of cost taken from Iron's pool when cross‑casting.                        |
| <code>respect_armor_bonuses</code> |<code>true</code> |When enabled, applies mana‑related armor perks to the active mana source.                                   |
| <code>respect_enchantments</code> |<code>true</code> |When enabled, mana‑related enchantments add mana/regen bonuses.                                             |
| <code>enable_resonance_system</code> |<code>true</code> |Enables full‑mana resonance bonuses (Iron's only).                                                          |
| <code>enable_cooldown_system</code> |<code>true</code> |Enables unified cooldown categories across both mods.                                                       |
| <code>enable_progression_system</code> |<code>true</code> |Enables Ars casts to grant Iron's school progression bonuses.                                               |
| <code>enable_affinity_system</code> |<code>true</code> |Enables affinity/attunement tracking for spell schools.                                                     |
| <code>debug_mode</code>     |<code>false</code> |Enables verbose debug logging for troubleshooting.                                                          |

Changes are safest with a client/server restart.

## Mana bars

The mod hides redundant mana bars based on mode:

*   `iss_primary`: hides Ars mana bar
*   `ars_primary` / `hybrid`: hides Iron's mana bar
*   `separate` / `disabled`: both bars may show

## Troubleshooting

**Ars mana not changing in `iss_primary`:**
*   Ensure Iron's is installed and compatible (3.15.x).
*   Check logs for mixin failures or missing attributes.

**Gear perks not affecting mana:**
*   Confirm `respect_armor_bonuses=true` and the correct mode.
*   In `separate`, perks are not cross-applied by design.

**Double mana bars:**
*   Verify your mode and look for overlay conflicts from other UI mods.

**Curio discounts not working:**
*   Ensure `enable_curio_discounts=true` in config
*   Verify Covenant of the Seven mod is installed
*   Check that curios are equipped in curio slots (not inventory)
*   Enable `debug_mode=true` to see discount calculations in logs

**Cursed Ring not consuming LP:**
*   Ensure both Covenant of the Seven and Blood Magic are installed
*   Verify you have a Blood Magic altar and are bound to it
*   Check that you have LP in your Soul Network (use Divination Sigil)
*   Ensure `enable_mana_unification=true` in config
*   Check logs for "Sanctified Legacy compatibility enabled" message

**Insta-death with Cursed Ring:**
*   Set `death_on_insufficient_lp=false` for safe mode (default)
*   Safe mode cancels spell and applies 1 heart damage instead of death
*   Death mode (`true`) allows spell to cast but kills you instantly

**LP costs too high/low:**
*   Adjust LP multipliers in config (15 options available)
*   For cheaper costs: reduce `ars_lp_base_multiplier` and `irons_lp_base_multiplier`
*   For more expensive costs: increase tier/rarity multipliers
*   See `LP_CALCULATION_GUIDE.txt` for detailed formula explanations

**Reporting Problems:** When reporting a problem with the mod, please detail what the exact issue is along with the steps necessary to recreate the problem so I can correct it. Enable `debug_mode=true` and include relevant log sections.

Want to see more ports, more mods and more updates? Consider donating!

Or check out some of my other mods:

[![](https://media.forgecdn.net/avatars/thumbnails/1649/279/64/64/639052531189080257.png)](https://www.curseforge.com/minecraft/mc-mods/combat-toggle)