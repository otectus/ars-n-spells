# Ars 'n' Spells (v3.2.2, NeoForge 1.21.1)

Ars 'n' Spells bridges **Ars Nouveau** and **Iron's Spells 'n Spellbooks** for Minecraft 1.21.1 on **NeoForge**. It unifies mana, scaling, and progression while keeping each mod playable on its own.

> **Status (v3.2.2, NeoForge 1.21.1).** Feature parity with the Forge 1.20.1 **3.2.2** line,
> with one documented exception (Covenant of the Seven — see *Known gaps* below).
>
> This release closes the gap from the 3.0.2 port. Beyond the 3.0.x feature set it adds the
> **3.0.3** fixes (the proxy book-resolution fallback chain, so a spellbook in the Curios slot
> casts instead of silently doing nothing; proxy double-billing short-circuits), the **3.1.0**
> fixes (the Inscription Table crash guard on both client and server; payload-integrity checks
> before anything is consumed; the school-classification rebuild on Ars metadata plus datapack
> overrides; `ISpellbook`-based spellbook detection; JEI/EMI proxy hiding), and the **3.2.0**
> fixes (the mod's own creative tab; Mana Infusion and the Mana Well finally obtainable; the
> shared-pool ceiling that stopped a single hybrid-mode cast draining the whole mana pool; the
> tooltip crash guard), the **3.2.1** parity pass (the `canBeCastedBy` injection reworked so a
> mod that replaces that method can no longer make mod loading fatal; Iron's mixins moved to a
> non-required config), and the **3.2.2** fix (the `ars_cross_*` proxy spells no longer appear
> in Iron's random-spell loot).
>
> Compile targets: **Ars Nouveau 5.13.1.1400**, **Iron's Spells 1.21.1-3.16.3**,
> **NeoForge 21.1.248**.

## Requirements

| Mod | Version | Required |
| --- | --- | --- |
| Minecraft (NeoForge) | 1.21.1 / 21.1.0+ (built against 21.1.248) | Yes |
| Java | 21 | Yes |
| Ars Nouveau | 5.13+ (built against 5.13.1.1400) | Yes |
| Iron's Spells 'n Spellbooks | 1.21.1-3.15.0+ (built against 3.16.3) | No |

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

When Iron's is the primary pool (`iss_primary`), Ars mana potions feed the unified pool instead of the now-unread Ars pool. Ars 5.x expresses the `ars_nouveau:mana_regen` effect as a modifier on `PerkAttributes.MANA_REGEN_BONUS`, and `EquipmentIntegration` mirrors that aggregate onto Iron's `MANA_REGEN` on a 1 Hz refresh, so drinking a Potion of Mana raises the pool you cast from and removing the effect reverts the bonus - with no separate potion code to double-count it. Requires Iron's installed; no-op in the other modes (Ars handles its own pool natively there).

### Pre-cast validation

`CastingAuthority` performs a mana-only pre-cast check against the mode-correct (bridged) pool, denying a cast with an action-bar message when the unified pool can't afford it — the native per-mod "enough mana?" checks read the wrong pool in primary modes. Creative and zero-cost casts always pass. (The 1.20.1 LP/aura ring branches are deferred along with those systems.)

### Spell scaling

Cross-mod spell damage scales bidirectionally when Iron's Spellbooks is installed:

**Iron's → Ars:** Ars spell damage multiplies by `globalSpellPower + (schoolSpellPower - 1.0)`, where `globalSpellPower` is Iron's generic `SPELL_POWER` and `schoolSpellPower` is the matching elemental attribute for the spell's resolved school(s). When a spell resolves to multiple schools (dual-element addon glyphs, compound-school recipes), the configured `multi_school_power_policy` (`primary`, `max` [default], or `average`) decides how those schools combine. The result is then multiplied by affinity (per-school) and resonance (mana fullness) bonuses. The final scalar is clamped to `spell_power_cap` (default 3.0).

**Ars → Iron's:** Iron's spell damage adds the caster's Ars `SPELL_DAMAGE_BONUS` perk value as a flat addition. This value is picked up automatically from any source — Ars armor threads, curios with Spell Damage modifiers, potions, or other mods. Plain Ars armor with no offensive perk grants no Iron's damage bonus. The perk addition is **not** subject to the `spell_power_cap`.

**Multi-school policy split:** damage scaling uses all schools the policy resolves; affinity and progression credit the primary (first-resolved) school only. This means a dual-element spell scales with the caster's better element and trains exactly one affinity track.

Implementation: both directions subscribe directly to spell-damage events (`SpellDamageEvent.Pre` from Ars Nouveau and `SpellDamageEvent` from Iron's) with no time window or damage-string guessing. Iron's must be installed for cross-mod scaling to fire — without Iron's, Ars spells use their native damage values, and Iron's applies no Ars perk bonus. Cross-mod combat scaling is independent of the mana-unification mode and remains active even when mana unification is `DISABLED`.

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

Light the brazier by right-clicking it with an **empty main hand** (right-clicking while holding something tries to feed that item to the brazier instead). The ritual burns for about three seconds; on completion the source is consumed and the target gains a `CrossModSpellList` data component. Enchantment-glyph particles and the enchantment-table sound mark the inscribe.

**3. Cast the inscribed spell.**
Right-click the target. Sneak-right-click cycles between multiple inscriptions on the same item. Mana costs flow through `BridgeManager` and respect the active unification mode; in SEPARATE mode the dual-cost split (`dual_cost_ars_percentage` / `dual_cost_iss_percentage`) and conversion rates determine how much each pool pays per cast.

Cross-cast spells pay an overhead set by `cross_cast_cost_multiplier` (default `1.25`, range `0.5`–`5.0`). The multiplier applies to both Iron's spells cast from non-Iron's items and Ars spells cast from non-Ars items, once per cast, before mana deduction.

**4. Strip an inscription.**
Craft the Spell Uninscription tablet on the Enchanting Apparatus from a blank parchment (reagent), a water bucket, a source gem, and an archwood log — 500 source. Drop one inscribed item within three blocks of the brazier with no other items in range. Ash and smoke particles plus a fire-extinguish sound mark the strip; the result is bit-identical to a fresh blank target so the same item can be re-inscribed cleanly. The uninscribe ritual is Iron's-independent and remains useful for cleanup even if Iron's Spellbooks is later removed.

### Ars spells in Iron's native spell wheel (3.0.x)

Beyond the generic right-click cross-cast, an Ars spell can be bound into a real Iron's spellbook where it appears as **its own entry in Iron's spell-selection wheel**, with a player-chosen name, nature, and icon, cast through Iron's native flow:

**1. Make a carrier scroll.** Craft the **Spell Loom** (gold ingot over lapis-book-lapis over three obsidian). Insert an Ars spell source (filled parchment, focus, or spellbook) and a blank Iron's scroll, type a display name, cycle a nature and icon, and press *Inscribe*. The output is a real `irons_spellbooks:scroll` carrying the Ars spell as an ANS component sidecar. (`/ans export_to_irons_scroll` is the admin shortcut.)

**2. Bind it onto a spellbook.** Craft the **Spellbook Binding** tablet on the Enchanting Apparatus (novice spellbook reagent; any Iron's spellbook, an Iron's scroll, a source gem block, and an archwood log on pedestals — 2500 source). The ritual reads **dropped item entities, not inventories**: place the tablet on a Ritual Brazier, throw (`Q`) exactly one carrier scroll and one Iron's spellbook on the ground within **three blocks** of the brazier, then light it by right-clicking the brazier with an **empty main hand**. It burns for about three seconds. Anything else lying inside that radius aborts the run with `unexpected item(s) in range` rather than risk binding the wrong stack. One scroll is consumed; the book gains the Ars spell. (`/ans bind_scroll_to_irons_book` binds the two items you hold, one per hand, and refuses when `allow_ars_spells_in_irons_spellbooks` is off.)

> **This is not Ars Nouveau's "Binding" ritual.** Ars ships `ars_nouveau:ritual_binding`, whose display name is exactly **Binding**; it converts nearby mobs into Bound Scripts for familiars and ignores dropped scrolls entirely. The tablet you want is **Spellbook Binding** (`ars_n_spells:spellbook_binding`, shown as *Ritual of Spellbook Binding* on the brazier). Lighting the wrong one looks identical to the feature being broken, because neither ritual says anything about the other.

**3. Cast from the wheel.** The bound spell shows in Iron's spell wheel as its own entry (name/icon from the loom; defaults otherwise). Casting delegates to the ANS cross-cast pipeline, so the cost multiplier, unification mode, and SEPARATE dual-cost split all apply — exactly once (the wheel entry itself is a zero-cost proxy spell).

Mechanically, each book can hold up to **8** bound Ars spells: they are driven by a finite pool of registered proxy spells (`ars_n_spells:ars_cross_1..8`), because Iron's wheel merges entries by spell id. Proxy slots are added into grown container capacity, so the player's real Iron's spells are never evicted. Binding is gated by `allow_ars_spells_in_irons_spellbooks` and capped by `max_ars_cross_spells_per_irons_spellbook`.

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
| `enable_cross_mod_combat_stats` | `true` | Master switch for Iron's → Ars and Ars → Iron's spell damage scaling. Active even when mana unification is `disabled`. |
| `enable_irons_power_for_ars_damage` | `true` | Iron's spell power multiplies Ars spell damage. |
| `enable_ars_damage_for_irons_damage` | `true` | Ars Spell Damage Bonus adds to Iron's spell damage. |
| `spell_power_cap` | `3.0` | Maximum total spell power multiplier from Iron's attributes. Caps multipliers only; does not apply to the Ars flat bonus. |
| `multi_school_power_policy` | `max` | How multiple matching schools combine: `primary` (first-resolved only), `max` (strongest single value), `average` (mean of all). |
| `source_jar_synergy_multiplier` | `5.0` | Multiplier for Source Jar proximity regen bonus. |
| `ritual_mana_infusion_amount` | `500.0` | Mana added by Ritual of Mana Infusion. |

### Cross-cast

| Option | Default | Description |
| --- | --- | --- |
| `cross_cast_cost_multiplier` | `1.25` | Overhead applied to cross-cast spell base mana cost. |
| `allow_ars_spells_in_irons_spellbooks` | `true` | Allow binding Ars spells into Iron's spellbooks / spell wheel. |
| `max_ars_cross_spells_per_irons_spellbook` | `-1` | Per-book cap on bound Ars spells (-1 = no cap; hard-bounded by the 8-slot proxy pool). |

### Source Jar synergy

| Option | Default | Description |
| --- | --- | --- |
| `enable_source_jar_synergy` | `true` | Kill switch for the proximity-regen scan (off = zero per-tick cost). |
| `source_jar_scan_interval_ticks` | `20` | Ticks between proximity checks per player (1–200). |
| `source_jar_scan_radius` | `4` | Horizontal scan radius in blocks (1–8; the scan never loads chunks). |

### Covenant of the Seven integration (removed in 3.2.1)

Covenant of the Seven has no 1.21.1 or NeoForge release, so the Cursed Ring LP subsystem, Virtue Ring aura, Blasphemy curios, and all associated config keys were removed in the 3.2.1 parity pass. If Covenant returns to NeoForge, those keys and their hooks will be re-added alongside the integration.

`virtue_ring_discount` and `max_total_curio_discount` are kept and live — they were repurposed as the generic `#ars_n_spells:curio_spell_discount` per-curio discount and its stacking cap, extensible through datapack tags.

`scroll_cost_mode` is live: `full` charges scroll casts through the unified pool (Iron's scrolls never deduct mana natively), and `free` removes the cost entirely. The `lp_only` value is inert and behaves as `free`.

The never-read keys the 1.20.1 audit flagged (ANS-MED-044) also stay deleted: the glyph/school bonus sections, the resonance caps, category cooldowns, and the dead performance keys.

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

## Roadmap (deferred past 3.2.2)

The 1.21.1 port is functionally complete; the items below are intentionally deferred, not broken. The mana-unification mixins disabled during the early port were repaired and re-enabled in 2.0.1; the cross-cast / rituals / scaling re-attach work tracked as "Phase 3" is done; and 2.6.1 restored the last stubbed pieces (in-game config screen, Ars mana-potion mirroring, mana-only pre-cast validation, debug overlay). The remaining deferral is the **LP/Cursed-Ring and Aura/Virtue-Ring** systems, which depend on Sanctified Legacy / Covenant of the Seven — no NeoForge 1.21.1 build of those exists yet.

- **Event-first mana bridge.** The bridge routes through two repaired mixins (`MixinManaCapability`, `MixinIronsMagicDataMana`) plus the Ars `SpellResolver` context/cost mixins. Every inject now uses `require = 0` and the mixin plugin probes its target classes (`ManaCap`/`ManaData`/`SpellResolver`), so a dependency point-release fails soft on **method** drift — but a **field** rename would still abort load. Migrating the bridge to Ars/Iron's public events (`MaxManaCalcEvent`, `SpellCostCalcEvent`, `ChangeManaEvent`, …) removes that fragility and is the main deferred item.
- **Larger optional-mod integrations** (Apotheosis affixes, Ars Elemental focus mapping, familiar/summon synergy, Iron's Restrictions gating, ISS upgrade orbs, DailyBoss) from the compatibility plan are scoped for a later release. Ars Elemental, Ars Zero, and Ars Elemancy are now verified optional addons with working profiles and GameTests; deeper per-item feature integrations remain deferred.
- **In-game runtime validation** — the build environment has no Minecraft, so the scenarios in [TESTING_GUIDE.md](TESTING_GUIDE.md) remain to be run manually.

## Building from source

Requires JDK 21.

```bash
./gradlew build
```

Dependencies (Ars Nouveau, Iron's Spellbooks) resolve automatically from CurseMaven (pinned file IDs in [`gradle.properties`](gradle.properties)); no manual jar placement required. The NeoForge `moddev` Gradle plugin handles deobf and run configuration.

Useful Gradle tasks: `runClient`, `runServer`, `runGameTestServer`, `runData`.

Output jar: `build/libs/ars_n_spells-3.2.2.jar` (version tracks `mod_version` in `gradle.properties`)

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the full version history, including the NeoForge 1.21.1 port notes at the top.

## License

GNU GPLv3
