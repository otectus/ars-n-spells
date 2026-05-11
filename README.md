# Ars 'n' Spells (v1.9.0, NeoForge 1.21.1)

Ars 'n' Spells bridges **Ars Nouveau** and **Iron's Spells 'n Spellbooks** for Minecraft 1.21.1 on **NeoForge**. It unifies mana, scaling, and progression while keeping each mod playable on its own.

> **Port status (1.9.0 on NeoForge 1.21.1).** This release ports the Forge 1.20.1 1.9.0 codebase to NeoForge 1.21.1: loader rewrite, vanilla-payload networking, NeoForge attachments for player state, and a `DataComponent`-backed cross-cast inscription store. Several gameplay re-attach points against the new Ars Nouveau 5.11.1 / Iron's Spells 3.15.6 internals are still being worked through (see [`TODO(Phase 11)`](#known-work-in-progress) markers in the source). The previous **Covenant of the Seven / Sanctified Legacy** integration (Cursed Ring, Virtue Ring, Blasphemy curios, LP / aura systems) is **removed**: no NeoForge 1.21.1 distribution of that addon exists at the time of writing. A direct **Curios** integration is planned to replace its curio-feature footprint.

## Requirements

| Mod | Version | Required |
| --- | --- | --- |
| Minecraft (NeoForge) | 1.21.1 / 21.1.84+ | Yes |
| Java | 21 | Yes |
| Ars Nouveau | 5.11.1+ | Yes |
| Iron's Spells 'n Spellbooks | 1.21.1-3.15.6+ | No |

If Iron's Spellbooks is not installed, Ars 'n' Spells falls back to native Ars behavior. The mod will not load on Forge or on Minecraft versions other than 1.21.1.

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

- **iss_primary / hybrid** — Ars gear perks apply to Iron's attributes.
- **ars_primary** — Iron's gear perks apply to Ars calculations.
- **separate** — Each mod's gear affects its own pool only.

### Spell scaling

Ars spell potency scales with Iron's spell power attributes. The base `SPELL_POWER` attribute applies to every Ars cast; if the first glyph indicates an element (fire, ice, lightning, holy, ender, blood, evocation, nature, eldritch), the matching Iron's elemental spell-power attribute layers on additively. Affinity (per-school) and resonance (mana fullness) further shape the multiplier. The final scalar is clamped to `spell_power_cap` (default 3.0).

Implementation: scaling activates on each Ars `SpellCastEvent` and is applied within a 60-tick window to spell-flavored damage from the casting player. Iron's must be installed for the scaling path to fire — without Iron's, Ars spells use their native damage values.

### Resonance

Optional resonance tracks mana percentage and boosts Iron's spell damage when mana is above a configurable threshold (default 95%).

### Cooldowns

A unified cooldown system groups spells into four categories (OFFENSIVE, DEFENSIVE, UTILITY, MOVEMENT) and locks out *all* spells in that category — across both mods — while a cooldown is active. **Cooldowns are global per category, by design**: an Ars OFFENSIVE cast and an Iron's OFFENSIVE cast intentionally collide on the same slot. Disabled by default.

### Progression and affinity

Casting builds **per-school progression** (cast counts persist) and **per-school affinity** (0–100 levels, recently used schools level up). Both systems work in **both directions**: Ars and Iron's casts each contribute to the same shared school maps, and progression-derived bonuses feed back into both Ars (via spell scaling) and Iron's (via the `<school>_spell_power` attribute) damage.

**Affinity decay** is opt-in via `enable_affinity_decay` (default `false` in fresh configs). When enabled, each player ticks every `affinity_decay_interval_ticks` (default 1200 = 60 s) and loses a fraction of every non-zero affinity level prorated from `affinity_decay_rate` (default 0.01 per Minecraft day).

### Cross-mod spell casting

Cross-casting lets any item store a spell from the *other* mod and cast it on right-click. Inscription is a two-tablet ritual flow backed by datapack recipes, so pack authors can retune ingredients without touching code.

**1. Craft the Spell Transcription tablet.**
Combine a novice Ars spellbook (reagent) with an Iron's spellbook, an archwood log, and a source gem block on the Enchanting Apparatus. Costs 2000 source. Recipe lives at [`data/ars_n_spells/recipes/apparatus/spell_transcription.json`](src/main/resources/data/ars_n_spells/recipes/apparatus/spell_transcription.json). The recipe is gated with `neoforge:conditions` `mod_loaded irons_spellbooks`, so it only loads when Iron's Spells is installed.

**2. Run the Spell Transcription ritual.**
Place the tablet on a Ritual Brazier, then drop two items within three blocks:
- exactly one **source** — a filled Ars Nouveau spell parchment, focus, or spellbook, or an Iron's Spellbooks scroll
- exactly one blank **target** item

Strict disambiguation: more than one of either category fails the ritual with a chat message naming what it saw. Items already carrying a cross-cast inscription are rejected too — uninscribe first.

On completion the source is consumed and the target gains a `CrossModSpellList` data component. Enchantment-glyph particles and the enchantment-table sound mark the inscribe.

**3. Cast the inscribed spell.**
Right-click the target. Sneak-right-click cycles between multiple inscriptions on the same item. Mana costs flow through `BridgeManager` and respect the active unification mode; in SEPARATE mode the dual-cost split (`dual_cost_ars_percentage` / `dual_cost_iss_percentage`) and conversion rates determine how much each pool pays per cast.

Cross-cast spells pay an overhead set by `cross_cast_cost_multiplier` (default `1.25`, range `0.5`–`5.0`). The multiplier applies to both Iron's spells cast from non-Iron's items and Ars spells cast from non-Ars items, once per cast, before mana deduction.

**4. Strip an inscription.**
Craft the Spell Uninscription tablet on the Enchanting Apparatus from a blank parchment (reagent), a water bucket, a source gem, and an archwood log — 500 source. Drop one inscribed item within three blocks of the brazier with no other items in range. Ash and smoke particles plus a fire-extinguish sound mark the strip; the result is bit-identical to a fresh blank target so the same item can be re-inscribed cleanly. The uninscribe ritual is Iron's-independent and remains useful for cleanup even if Iron's Spellbooks is later removed.

### Cross-cast storage (1.21.1)

On NeoForge 1.21.1 the cross-cast inscription is stored as a `DataComponentType<CrossModSpellList>` registered under `ars_n_spells:cross_spells` ([`ModDataComponents`](src/main/java/com/otectus/arsnspells/spell/ModDataComponents.java)). The component carries an immutable list of inscribed entries plus the currently-selected index for sneak-cycling, with both a JSON `Codec` and a `StreamCodec` for network sync. The Forge-era root-NBT keys (`arsnspells:cross_spells`, `arsnspells:cross_spell_index`) are gone; items inscribed under the previous Forge build will not migrate automatically.

### Player state (1.21.1)

Affinity, cooldown, and progression are stored as NeoForge entity attachments ([`AttachmentTypes`](src/main/java/com/otectus/arsnspells/data/AttachmentTypes.java)). Affinity and progression carry `copyOnDeath`; cooldown intentionally resets on respawn. The Forge capability provider that previously wrapped these has been removed.

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
| `enable_affinity_decay` | `false` | Periodic affinity decay (opt-in). |
| `affinity_decay_interval_ticks` | `1200` | Ticks between decay handler runs (range 20–24000). |
| `affinity_decay_rate` | `0.01` | Fraction of each non-zero affinity lost per Minecraft day. |
| `debug_mode` | `false` | Verbose logging. |

### Mana conversion

| Option | Default | Description |
| --- | --- | --- |
| `conversion_rate_ars_to_iron` | `1.0` | Multiplier for Ars costs paid from Iron's pool. |
| `conversion_rate_iron_to_ars` | `1.0` | Multiplier for Iron's costs paid from Ars pool. |
| `dual_cost_ars_percentage` | `0.5` | In `separate`, fraction taken from Ars pool. |
| `dual_cost_iss_percentage` | `0.5` | In `separate`, fraction taken from Iron's pool. |
| `hybrid_mana_bar` | `irons` | Which HUD bar to show in `hybrid` mode (`ars` or `irons`). |

### Spell scaling

| Option | Default | Description |
| --- | --- | --- |
| `spell_power_cap` | `3.0` | Maximum total spell power multiplier from Iron's attributes. |
| `source_jar_synergy_multiplier` | `5.0` | Multiplier for Source Jar proximity regen bonus. |
| `ritual_mana_infusion_amount` | `500.0` | Mana added by Ritual of Mana Infusion. |

### Cross-cast

| Option | Default | Description |
| --- | --- | --- |
| `cross_cast_cost_multiplier` | `1.25` | Overhead applied to cross-cast spell base mana cost. |
| `enable_per_cast_reagent` | `false` | Reserved hook for a future per-cast reagent system. |

### Removed in the NeoForge 1.21.1 port

The following config sections from the Forge 1.20.1 build are no longer surfaced (the underlying features are gone with Sanctified Legacy / Covenant of the Seven removal): `enable_lp_system`, `lp_source_mode`, `death_on_insufficient_lp`, `show_lp_cost_messages`, `ars_lp_*`, `irons_lp_*`, `aura_*`, `virtue_ring_discount`, `blasphemy_*`, `hide_mana_bar_with_ring`, `scroll_cost_mode`. Dead config keys may still appear in autogenerated config files until a follow-up cleanup pass; they have no effect on gameplay.

---

## Mana bars

The mod hides redundant mana bars based on mode:

- **iss_primary**: Hides Ars mana bar.
- **ars_primary**: Hides Iron's mana bar.
- **hybrid**: Shows the bar selected by `hybrid_mana_bar`.
- **separate / disabled**: Both bars may show.

The mana-bar overlay registration moves from Forge's `RegisterGuiOverlaysEvent` to NeoForge's `RegisterGuiLayersEvent`. The mixin and overlay code is currently stubbed pending Phase 3 (Ars 5.x / Iron's 3.15.6 overlay re-targeting).

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/ans mana setdefault <value>` | Op 2 | Set the default max mana. |
| `/ans mana getdefault` | — | View the current default max mana. |
| `/ans debug` | Op 2 | Toggle debug mode at runtime. |
| `/ans info <player>` | Op 2 | Show mana and resonance (ring/aura info removed in 1.21.1). |
| `/ans mode` | — | Show current mana unification mode. |

## Known work in progress

The NeoForge 1.21.1 port is staged. Phase 0 (build / metadata / loader / networking / attachments / data components / Sanctified deletion / 1.9.0 stabilization NeoForge port), Phase 1 (Sanctified upstream decision), Phase 4 (recipe tag + conditions), and Phase 5 (data-component round-trip test) are complete. Pending phases:

- **Phase 2** — re-verify all 9 mixins against the pinned Ars Nouveau 5.11.1 and Iron's Spells 3.15.6 jars. Particular hazards: `ManaCap` method rename, `SpellResolver.expendMana`/`canCast` signatures, `MagicData` mana methods, `ManaBarOverlay.render` (the Forge `ForgeGui` it referenced does not exist on NeoForge 1.21.1).
- **Phase 3** — 22 files carry `TODO(Phase 11)` markers for gameplay re-attach: `CrossCastingHandler` (cast invocation, `SpellCostCalcEvent`, right-click intercept), bridge layer (`ArsNativeBridge`, `ManaUtil`), several event handlers (`ArsManaCalcHandler`, `ResonanceEvents`, `RegenSynergyHandler`, `CurioDiscountHandler`, `EquipmentHandler`, `CastingAuthority`), `EquipmentIntegration`, `SpellScalingUtil`, `SpellAnalysis`, client overlay handlers, and the scroll-use mixin.
- **Phase 6** — full Curios 1.21.1 integration. Will replace the Sanctified curio-feature footprint with a generic Curios bridge gated by `ModList.isLoaded("curios")`.
- **Phase 7** — verification gates (`./gradlew build` / `test` / `runClient` / `runServer` / `runGameTestServer`) and the V1–V10 manual scenarios from [TESTING_GUIDE.md](TESTING_GUIDE.md).

## Building from source

Requires JDK 21.

```bash
./gradlew build
```

Dependencies (Ars Nouveau, Iron's Spellbooks) resolve automatically from CurseMaven (pinned file IDs in [`gradle.properties`](gradle.properties)); no manual jar placement required. The NeoForge `moddev` Gradle plugin handles deobf and run configuration.

Useful Gradle tasks: `runClient`, `runServer`, `runGameTestServer`, `runData`.

Output jar: `build/libs/ars_n_spells-1.9.0.jar`

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the full version history, including the NeoForge 1.21.1 port notes at the top.

## License

GNU GPLv3
