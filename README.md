# Ars 'n' Spells (v1.8.8)

Ars 'n' Spells bridges **Ars Nouveau** and **Iron's Spells 'n Spellbooks** for Minecraft 1.20.1 (Forge). It unifies mana, scaling, and progression while keeping each mod playable on its own. Optional integration with **Covenant of the Seven** (Sanctified Legacy) adds LP and aura-based casting through the Ring of Seven Curses and Ring of Seven Virtues.

## Requirements

| Mod | Version | Required |
| --- | --- | --- |
| Minecraft (Forge) | 1.20.1 / 47.2.0+ | Yes |
| Ars Nouveau | 4.12.7 | Yes |
| Iron's Spells 'n Spellbooks | 3.15.x | No |
| Covenant of the Seven | Any | No |
| Blood Magic | Any | No |
| Curios API | Any | Included via Ars |

If Iron's Spellbooks is not installed, Ars 'n' Spells falls back to native Ars behavior.

## Features

### Mana unification

Five modes are available via the `mana_unification_mode` config:

| Mode | Behavior |
| --- | --- |
| `iss_primary` (default) | Iron's mana is the single source of truth. Ars reads and drains Iron's pool. |
| `ars_primary` | Ars mana is authoritative. Iron's spells drain Ars mana. |
| `hybrid` | Shared bidirectional pool. A config option (`hybrid_mana_bar`) controls which HUD bar is displayed. |
| `separate` | Independent pools. Cross-mod casts split costs between both pools. |
| `disabled` | No mana integration; each mod uses its own pool. |

Conversion rates (`conversion_rate_ars_to_iron`, `conversion_rate_iron_to_ars`) and dual-cost percentages are configurable.

### Gear perks and enchantments

Ars and Iron's gear bonuses are routed to the active mana source:

- **iss_primary / hybrid** -- Ars gear perks apply to Iron's attributes.
- **ars_primary** -- Iron's gear perks apply to Ars calculations.
- **separate** -- Each mod's gear affects its own pool only.

Bonuses come from attribute modifiers, `IManaEquipment` implementations, mana-related enchantments, and Curios items.

### Spell scaling

Ars spell potency scales with Iron's spell power attributes. Elemental spell power is applied when the first glyph indicates an element.

### Resonance

Optional resonance tracks mana percentage and boosts Iron's spell damage when mana is above a configurable threshold (default 95%).

### Cooldowns

A unified cooldown system groups spells into categories and locks out similar spells across mods. Disabled by default.

### Progression and affinity

Ars spell casts can grant Iron's school progression and vice versa. Affinity tracks spell school usage over time, with optional decay.

### Cross-mod spell casting

Cross-casting lets any item store a spell from the *other* mod and cast it on right-click. The inscription is applied in survival via the **Spell Transcription** ritual:

1. Build the Spell Transcription ritual (`ars_n_spells:spell_transcription`).
2. Drop a **source** within ~3 blocks of the brazier: a filled Ars Nouveau spell parchment, spellbook, or focus, **or** an Iron's Spellbooks scroll.
3. Drop a **target** item in the same area -- any item can receive the inscription.
4. Activate the ritual. On completion, the source is consumed and the target gains the `arsnspells:cross_spells` NBT tag.
5. **Right-click** the target to cast the inscribed spell. **Sneak-right-click** cycles if you stack multiple inscriptions.

Mana costs flow through `BridgeManager` and respect the active unification mode. In `separate` mode, the dual-cost split (`dual_cost_ars_percentage` / `dual_cost_iss_percentage`) and conversion rates determine how much each pool pays per cast.

---

## Covenant of the Seven integration

When Covenant of the Seven (Sanctified Legacy) is installed, two ring systems become available.

### Ring of Seven Curses (LP costs)

Wearing the Cursed Ring converts mana costs to **Life Points**. LP can be sourced from Blood Magic's Soul Network or player health.

- **LP source modes** (`lp_source_mode`):
  - `BLOOD_MAGIC_PRIORITY` (default) -- Try Blood Magic first, fall back to health.
  - `BLOOD_MAGIC_ONLY` -- Blood Magic only; fails if BM is not installed.
  - `HEALTH_ONLY` -- Always use health (100 LP = 5 hearts).
- **Insufficient LP behavior** (`death_on_insufficient_lp`):
  - `false` (default) -- Spell is cancelled with 1 heart of damage.
  - `true` -- Spell casts but the player dies.
- LP costs scale with configurable base and tier multipliers, with a minimum cost floor.

### Ring of Seven Virtues (aura costs)

Wearing the Virtue Ring converts mana costs to **aura**, a custom resource that regenerates over time.

- **Aura pool**: Configurable max (default 1000) and regen rate (default 0.5/tick = 10/sec).
- **Cost formula**: `aura = max(minimum, manaCost * baseMultiplier * tierMultiplier)`
- Aura persists across death and dimension changes.
- Insufficient aura cancels the spell with an action bar message.

### Blasphemy curios (school discounts)

Thirteen Blasphemy curios provide a base 15% mana discount, plus an extra 10% when the spell school matches the curio's element. Discounts stack multiplicatively with ring costs when enabled.

---

## Scroll cost enforcement

Iron's Spellbooks scrolls now respect resource costs. The `scroll_cost_mode` config controls behavior:

| Mode | Behavior |
| --- | --- |
| `full` (default) | Scrolls consume mana and LP/aura like normal casting. |
| `lp_only` | Scrolls are mana-free but LP is still consumed for Cursed Ring wearers. |
| `free` | No resource cost for scrolls (LP from Cursed Ring still applies). |

---

## Configuration

Config file: `config/ars_n_spells-common.toml`

### Master toggles

| Option | Default | Description |
| --- | --- | --- |
| `enable_mana_unification` | `true` | Enables all mana bridging logic. |
| `mana_unification_mode` | `iss_primary` | Which mana pool is authoritative. |
| `enable_resonance_system` | `true` | Full-mana resonance bonuses (Iron's). |
| `enable_cooldown_system` | `false` | Unified cooldown categories. |
| `enable_progression_system` | `true` | Cross-mod progression XP. |
| `enable_affinity_system` | `true` | Spell school affinity tracking. |
| `debug_mode` | `false` | Verbose logging. |

### Mana conversion

| Option | Default | Description |
| --- | --- | --- |
| `conversion_rate_ars_to_iron` | `1.0` | Multiplier for Ars costs paid from Iron's pool. |
| `conversion_rate_iron_to_ars` | `1.0` | Multiplier for Iron's costs paid from Ars pool. |
| `dual_cost_ars_percentage` | `0.5` | In `separate`, fraction taken from Ars pool. |
| `dual_cost_iss_percentage` | `0.5` | In `separate`, fraction taken from Iron's pool. |
| `hybrid_mana_bar` | `irons` | Which HUD bar to show in `hybrid` mode (`ars` or `irons`). |

### LP system (Cursed Ring)

| Option | Default | Description |
| --- | --- | --- |
| `lp_source_mode` | `BLOOD_MAGIC_PRIORITY` | LP source fallback order. |
| `death_on_insufficient_lp` | `false` | Kill player instead of cancelling spell. |
| `show_lp_cost_messages` | `true` | Action bar LP notifications. |
| `ars_lp_base_multiplier` | `1.0` | Base LP multiplier for Ars spells. |
| `irons_lp_base_multiplier` | `0.5` | Base LP multiplier for Iron's spells. |
| `ars_lp_minimum_cost` | `10` | Minimum LP per Ars cast. |
| `irons_lp_minimum_cost` | `10` | Minimum LP per Iron's cast. |

### Aura system (Virtue Ring)

| Option | Default | Description |
| --- | --- | --- |
| `aura_max_default` | `1000` | Starting max aura. |
| `aura_regen_rate` | `0.5` | Aura regenerated per tick (10/sec at 20 TPS). |
| `aura_base_multiplier` | `1.0` | Base aura cost multiplier. |
| `aura_tier1_multiplier` | `1.0` | Tier 1 spell aura multiplier. |
| `aura_tier2_multiplier` | `1.5` | Tier 2 spell aura multiplier. |
| `aura_tier3_multiplier` | `2.0` | Tier 3 spell aura multiplier. |
| `aura_minimum_cost` | `5` | Minimum aura per cast. |
| `show_aura_messages` | `true` | Action bar aura notifications. |

### Scroll costs

| Option | Default | Description |
| --- | --- | --- |
| `scroll_cost_mode` | `full` | Scroll resource cost mode (`full`/`lp_only`/`free`). |

### Curio discounts

| Option | Default | Description |
| --- | --- | --- |
| `enable_curio_discounts` | `true` | Enable Blasphemy curio discounts. |
| `blasphemy_discount` | `0.15` | Base Blasphemy discount (15%). |
| `blasphemy_matching_school_bonus` | `0.10` | Extra discount for matching school (+10%). |
| `allow_discount_stacking` | `true` | Allow discounts to stack with ring costs. |

### Spell scaling

| Option | Default | Description |
| --- | --- | --- |
| `spell_power_cap` | `3.0` | Maximum total spell power multiplier from Iron's attributes. |
| `blasphemy_lp_discount` | `0.85` | LP cost discount from matching Blasphemy curio (0.85 = 85%). |
| `blasphemy_aura_discount` | `0.85` | Aura cost discount from matching Blasphemy curio (0.85 = 85%). |
| `source_jar_synergy_multiplier` | `5.0` | Multiplier for Source Jar proximity regen bonus. |
| `ritual_mana_infusion_amount` | `500.0` | Mana added by Ritual of Mana Infusion. |

### Other

| Option | Default | Description |
| --- | --- | --- |
| `respect_armor_bonuses` | `true` | Route armor mana perks to active source. |
| `respect_enchantments` | `true` | Include mana enchantment bonuses. |

---

## Mana bars

The mod hides redundant mana bars based on mode:

- **iss_primary**: Hides Ars mana bar.
- **ars_primary**: Hides Iron's mana bar.
- **hybrid**: Shows the bar selected by `hybrid_mana_bar`.
- **separate / disabled**: Both bars may show.

## Changelog

### v1.8.8
- **Cross-system mana regen unit mismatch fixed** -- Iron's `MANA_REGEN` is a percentage-of-pool multiplier; Ars regen is absolute mana/sec. Three callsites previously wrote a value from one system directly into the other without converting units, so an Ars Mana Regen III enchantment on wizard armor could compound into hundreds of mana/sec. All cross-system regen translations now route through a new `ManaRegenBridge` that converts via the wearer's current max pool. Three new config knobs (`cross_system_regen_conversion`, `cross_system_regen_multiplier`, `cross_system_regen_reference_pool`) control the conversion strategy. Also tightened the enchantment-detection heuristic in `EquipmentIntegration` from a broad `description.contains("mana")` string match to specific Ars enchantment IDs, eliminating spurious +50 max-mana grants from unrelated enchantments. See [CHANGELOG.md](CHANGELOG.md) for details.

### v1.8.6
- **Cursed/Virtue Ring polish and LP hardening** -- Mana bar is now hidden while either ring is equipped (new `hide_mana_bar_with_ring` config, default on). Cursed Ring detection now recognizes both `enigmaticlegacy:cursed_ring` and `covenant_of_the_seven:cursed_ring`. Ring and Blasphemy curio detection is cached per-player so a single spell cast no longer triggers 5+ curio-inventory scans. LP pending-cost maps upgraded to `ConcurrentHashMap` with `PlayerLoggedOutEvent` eviction in all three handlers. Added defensive guards against non-positive LP costs, null Blood Magic Soul Networks, and floating-point health drift below the 1-HP buffer. See [CHANGELOG.md](CHANGELOG.md) for details.

### v1.8.3
- **Spell Transcription ritual is now functional** -- The previously no-op ritual now actually inscribes a cross-mod spell onto a target item, exposing the cross-casting runtime to survival play. Removed the dead `ProgressionSyncPacket` and deprecated `XpConverter`, and added a defensive guard in `AuraCapability` for early capability attach. See [CHANGELOG.md](CHANGELOG.md) for details.

### v1.8.0 -- v1.8.2
- Upstream compatibility overhaul for Ars Nouveau 4.12.7 and Iron's Spellbooks 3.15.5.1: central `SpellAnalysis` utility corrects glyph classification across all systems, rituals migrated to the current `onEnd()` lifecycle, LP death prevention rewritten as scoped cast transactions, overlay controllers consolidated, and Ars armor mana bonuses fixed in `ARS_PRIMARY` mode. See [CHANGELOG.md](CHANGELOG.md) for the full list.

### v1.7.0
- Major quality and stability release: fixed SEPARATE mode mana loss, safe mode killing players, pending cost TTL under server lag, double event firing, and more. Added additive spell scaling with configurable cap, ring conflict notifications, new `/ans` commands, expanded translations, and performance optimizations. See [CHANGELOG.md](CHANGELOG.md) for full details.

## Troubleshooting

- **Ars mana not changing in iss_primary**: Ensure Iron's is installed (3.15.x). Check logs for mixin failures.
- **Gear perks not affecting mana**: Confirm `respect_armor_bonuses=true` and the correct mode. In `separate`, perks are not cross-applied.
- **Double mana bars**: Verify your mode and check for overlay conflicts from other UI mods.
- **"Insufficient LP" despite enough hearts**: Ensure `lp_source_mode` is not `BLOOD_MAGIC_ONLY` without Blood Magic installed. Default is `BLOOD_MAGIC_PRIORITY`.
- **Scrolls casting for free**: Verify `scroll_cost_mode` is set to `full` (default).

## Building from source

1. Install JDK 17.
2. Run:

```bash
./gradlew build
```

Dependencies (Ars Nouveau, Iron's Spellbooks) resolve automatically from CurseMaven; no manual jar placement required.

Output jar: `build/libs/ars_n_spells-1.8.8.jar`

## License

GNU GPLv3
