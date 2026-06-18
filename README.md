# Ars 'n' Spells (v2.6.1, NeoForge 1.21.1)

Ars 'n' Spells bridges **Ars Nouveau** and **Iron's Spells 'n Spellbooks** for Minecraft 1.21.1 on **NeoForge**. It unifies mana, scaling, and progression while keeping each mod playable on its own.

> **Status (v2.6.1, NeoForge 1.21.1).** Mana unification, cross-cast inscription, rituals, affinity, progression, resonance, cooldowns, and equipment scaling are all live (the mana mixins were repaired against Ars 5.x / Iron's 3.x in 2.0.1). **2.6.1 restores core parity with the Forge 1.20.1 build:** the in-game config screen, Ars mana-potion mirroring into the unified pool, a mana-only pre-cast validator, and the debug overlay are all functional again (each was stubbed or unported in 2.5.0). **2.5.0** was a correctness release: affinity now tracks *every* Iron's addon school dynamically instead of a hardcoded sixteen, the Curios spell-discount applies on both the Ars and Iron's sides, and two datapack features that silently broke in the 1.20.1 → 1.21 directory flattening (the ritual apparatus recipes and the curio-discount tag) are restored to their correct singular paths. Compile targets are pinned to Ars Nouveau 5.11.1 / Iron's Spells 3.15.6; the mod runs against newer (5.11.7 / 3.16.0) at runtime. The previous **Covenant of the Seven / Sanctified Legacy** integration (Cursed Ring, Virtue Ring, Blasphemy curios, LP / aura systems) remains **removed** (no NeoForge 1.21.1 build of that addon exists); the `#ars_n_spells:curio_spell_discount` item tag is its replacement footprint.

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

### Mana potions

When Iron's is the primary pool (`iss_primary`), Ars mana potions feed the unified pool instead of the now-unread Ars pool: the `ars_nouveau:mana_regen` and `ars_nouveau:mana_boost` effects are mirrored onto Iron's `MANA_REGEN` and `MAX_MANA` attributes (`MixinArsPotionEffects`), so drinking a Potion of Mana actually raises the pool you cast from. Removing the effect reverts the bonus. Requires Iron's installed; no-op in the other modes (Ars handles its own pool natively there).

### Pre-cast validation

`CastingAuthority` performs a mana-only pre-cast check against the mode-correct (bridged) pool, denying a cast with an action-bar message when the unified pool can't afford it — the native per-mod "enough mana?" checks read the wrong pool in primary modes. Creative and zero-cost casts always pass. (The 1.20.1 LP/aura ring branches are deferred along with those systems.)

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

An **in-game config screen** is available from the mod list (**Mods → Ars 'n' Spells → Config**), registered via NeoForge's `IConfigScreenFactory`. It exposes the master toggles, the mana-unification mode (click the row to cycle), and the gear/debug switches. Because the gameplay config is a SERVER config, the Save / Reset controls are active only in singleplayer; on a dedicated server, edit the server `.toml` or use `/ans`.

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

Hiding is handled natively on NeoForge by `ManaBarController`, which cancels the redundant `RenderGuiLayerEvent.Pre` for `ars_nouveau:mana_bar` / `irons_spellbooks:mana_bar` per mode — the Forge-style overlay mixin is not needed.

## Debug overlay

With `debug_mode` enabled, `OverlayDiagnostics` logs every rendered GUI layer id once (via `RenderGuiLayerEvent.Pre`), highlighting mana-related layers — a quick way to discover overlay ids when tuning `ManaBarController`. It is opt-in and registers no per-frame work when off; the client reads `debug_mode` at startup, so toggling it takes effect on the next client launch.

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/ans mana setdefault <value>` | Op 2 | Set the default max mana. |
| `/ans mana getdefault` | — | View the current default max mana. |
| `/ans debug` | Op 2 | Toggle debug mode at runtime. |
| `/ans info <player>` | Op 2 | Show mana, resonance, and the player's per-school affinity (plus the registered Iron's school count). |
| `/ans mode` | — | Show current mana unification mode. |

## Roadmap (deferred past 2.6.1)

The 1.21.1 port is functionally complete; the items below are intentionally deferred, not broken. The mana-unification mixins disabled during the early port were repaired and re-enabled in 2.0.1; the cross-cast / rituals / scaling re-attach work tracked as "Phase 3" is done; and 2.6.1 restored the last stubbed pieces (in-game config screen, Ars mana-potion mirroring, mana-only pre-cast validation, debug overlay). The remaining deferral is the **LP/Cursed-Ring and Aura/Virtue-Ring** systems, which depend on Sanctified Legacy / Covenant of the Seven — no NeoForge 1.21.1 build of those exists yet.

- **Event-first mana bridge.** The bridge routes through two repaired mixins (`MixinManaCapability`, `MixinIronsMagicDataMana`) plus the Ars `SpellResolver` context/cost mixins. Every inject now uses `require = 0` and the mixin plugin probes its target classes (`ManaCap`/`ManaData`/`SpellResolver`), so a dependency point-release fails soft on **method** drift — but a **field** rename would still abort load. Migrating the bridge to Ars/Iron's public events (`MaxManaCalcEvent`, `SpellCostCalcEvent`, `ChangeManaEvent`, …) removes that fragility and is the main deferred item.
- **Compile-target bump.** Pinned to Ars 5.11.1 / Iron's 3.15.6 (build-verified); the target pack runs 5.11.7 / 3.16.0. Moving the compile classpath up is deferred until an in-game validation pass exists.
- **Larger optional-mod integrations** from the compatibility plan (Apotheosis affixes, Ars Elemental focus mapping, familiar/summon synergy, Iron's Restrictions gating, ISS upgrade orbs, DailyBoss) are scoped for a later release. 2.5.0 ships only the data-only scaffolding (`#ars_n_spells:magical_companions` entity tag, the curio-discount tag default) and the light `compat.ModPresence` / `compat.CompatIds` foundation.
- **In-game runtime validation** — the build environment has no Minecraft, so the scenarios in [TESTING_GUIDE.md](TESTING_GUIDE.md) remain to be run manually.

## Building from source

Requires JDK 21.

```bash
./gradlew build
```

Dependencies (Ars Nouveau, Iron's Spellbooks) resolve automatically from CurseMaven (pinned file IDs in [`gradle.properties`](gradle.properties)); no manual jar placement required. The NeoForge `moddev` Gradle plugin handles deobf and run configuration.

Useful Gradle tasks: `runClient`, `runServer`, `runGameTestServer`, `runData`.

Output jar: `build/libs/ars_n_spells-2.6.1.jar`

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the full version history, including the NeoForge 1.21.1 port notes at the top.

## License

GNU GPLv3
