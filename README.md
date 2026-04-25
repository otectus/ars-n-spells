# Ars 'n' Spells (v1.8.9)

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

Cross-casting lets any item store a spell from the *other* mod and cast it on right-click. Inscription is a two-tablet ritual flow inscribe/uninscribe pair backed by datapack recipes, so pack authors can retune ingredients without touching code.

**1. Craft the Spell Transcription tablet.**
Combine a novice Ars spellbook (reagent) with an Iron's spellbook, an archwood log, and a source gem block on the Enchanting Apparatus. Costs 2000 source. Recipe lives at [data/ars_n_spells/recipes/apparatus/spell_transcription.json](src/main/resources/data/ars_n_spells/recipes/apparatus/spell_transcription.json).

**2. Run the Spell Transcription ritual.**
Place the tablet on a Ritual Brazier, then drop two items within three blocks:
- exactly one **source** -- a filled Ars Nouveau spell parchment, focus, or spellbook, or an Iron's Spellbooks scroll
- exactly one blank **target** item

Strict disambiguation: more than one of either category fails the ritual with a chat message naming what it saw. Items already carrying an Ars Nouveau spell at NBT root are rejected as targets (they would let Ars's own right-click handler shadow the cross-cast). Items already carrying a cross-cast inscription are rejected too -- uninscribe first.

Activate the ritual; on completion the source is consumed and the target gains the `arsnspells:cross_spells` NBT list. Enchantment-glyph particles and the enchantment-table sound mark the inscribe.

**3. Cast the inscribed spell.**
Right-click the target. Sneak-right-click cycles between multiple inscriptions on the same item. Mana costs flow through `BridgeManager` and respect the active unification mode; in SEPARATE mode the dual-cost split (`dual_cost_ars_percentage` / `dual_cost_iss_percentage`) and conversion rates determine how much each pool pays per cast.

Cross-cast spells pay an overhead set by `cross_cast_cost_multiplier` (default `1.25`, range `0.5`-`5.0`). The multiplier applies to both Iron's spells cast from non-Iron's items and Ars spells cast from non-Ars items, once per cast, before mana deduction.

**4. Strip an inscription.**
Craft the Spell Uninscription tablet on the Enchanting Apparatus from a blank parchment (reagent), a water bucket, a source gem, and an archwood log -- 500 source. Drop one inscribed item within three blocks of the brazier with no other items in range. Ash and smoke particles plus a fire-extinguish sound mark the strip; the result is bit-identical to a fresh blank target so the same item can be re-inscribed cleanly. The uninscribe ritual is Iron's-independent and remains useful for cleanup even if Iron's Spellbooks is later removed.

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

### v1.8.9
- **Cross-Spell Inscription is now reachable in survival** -- the Spell Transcription ritual has been wired to the world for the first time. An Enchanting Apparatus recipe (Ars novice spellbook + Iron's spellbook + archwood log + source gem block, 2000 source) crafts the ritual tablet that activates a brazier; previously the tablet existed only via `/give` because Ars Nouveau builds tablet items during its own item RegisterEvent and never saw our common-setup ritual. The mod now owns the tablet through a dedicated `DeferredRegister<Item>` and splices it into Ars's `ritualItemMap` at startup. Same flow for the new Spell Uninscription tablet.
- **Spell Transcription rewritten with strict disambiguation** -- exactly one source and exactly one blank target in the brazier's three-block radius. More than one of either category fails with a chat message naming what was found. Items already carrying an Ars Nouveau spell at NBT root are rejected as targets to keep right-click resolution unambiguous; already-inscribed items in range are rejected with a "uninscribe first" hint. Every failure produces a lang-keyed, item-named message; no silent failures.
- **New Spell Uninscription ritual** -- mirrors the inscribe flow with strict intake (exactly one inscribed item, no other items in range). The strip removes both the cross-spell list and the cycle index, and collapses an empty residual root tag to null so the result is bit-identical to a fresh blank target. Iron's-independent: the tablet and ritual register without Iron's loaded so legacy inscribed items can still be cleaned up after Iron's is removed. Apparatus recipe: blank parchment + water bucket + source gem + archwood log, 500 source.
- **Cross-cast cost multiplier** -- new `cross_cast_cost_multiplier` config (default `1.25`, range `0.5`-`5.0`) charges a flat overhead on spells cast from inscribed items. Applies to both Iron's spells from non-Iron's items and Ars spells from non-Ars items, exactly once per cast, before mana deduction. Routed through `BridgeManager` so it composes cleanly with the active mana mode and the SEPARATE-mode dual-cost split. New `enable_per_cast_reagent` config reserved as a future hook (default `false`, no-op today).
- **Theming** -- inscribe plays enchantment-glyph particles + the enchantment-table sound; uninscribe plays ash + smoke + the fire-extinguish sound.
- **Test harness** -- JUnit 5 lands with two test classes: a CompoundTag-level inscribe/uninscribe round-trip (5 cases) and a disambiguation-predicate suite (7 cases). Twelve tests total, all green under `./gradlew test`. The cross-cast NBT manipulation is extracted into a Bootstrap-free `CrossCastNbt` helper so the contract is testable without booting Minecraft. See [CHANGELOG.md](CHANGELOG.md) for details.

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

Output jar: `build/libs/ars_n_spells-1.8.9.jar`

## License

GNU GPLv3
