# Ars 'n' Spells (v2.6.0)

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

**Changing the mode:** in singleplayer, open Mods → Ars 'n' Spells → Config and click the **Mana Mode** row to cycle it (applies on *Done*). Server operators can run `/ans mode set <mode>`. Either path — or editing the config file directly — takes effect **without a restart** as of 2.0.1. Confirm the active mode with `/ans mode`.

Conversion rates (`conversion_rate_ars_to_iron`, `conversion_rate_iron_to_ars`) and dual-cost percentages are configurable.

### Gear perks and enchantments

Ars and Iron's gear bonuses are routed to the active mana source:

- **iss_primary / hybrid** -- Ars gear perks apply to Iron's attributes.
- **ars_primary** -- Iron's gear perks apply to Ars calculations.
- **separate** -- Each mod's gear affects its own pool only.

Bonuses come from attribute modifiers, `IManaEquipment` implementations, mana-related enchantments, and Curios items.

Worn **curios** (rings, amulets, belts) have their attribute modifiers read the same way armor and weapons do, so mana stats applied by **Apotheosis / Apothic Curios** affixes and sockets — and by other curio mana gear like Magical Jewelry and Jewelcraft — feed the unified pool too. No hard dependency on Apotheosis is required; the read is generic and gated by the `read_curio_attribute_modifiers` config (default on). Apothic Attributes' combat stats (crit, armor pierce, fire/cold damage) are not mana or spell-power and are not bridged.

### Spell scaling

Ars spell potency scales with Iron's spell power attributes. The base `SPELL_POWER` attribute applies to every Ars cast; if the first glyph indicates an element (fire, ice, lightning, holy, ender, blood, evocation, nature, eldritch), the matching Iron's elemental spell-power attribute layers on additively. Affinity (per-school) and resonance (mana fullness) further shape the multiplier. The final scalar is clamped to `spell_power_cap` (default 3.0).

Implementation: scaling activates on each Ars `SpellCastEvent` and is applied within a 60-tick window to spell-flavored damage from the casting player. Iron's must be installed for the scaling path to fire — without Iron's, Ars spells use their native damage values.

### Resonance

Optional resonance tracks mana percentage and boosts Iron's spell damage when mana is above a configurable threshold (default 95%).

### Cooldowns

A unified cooldown system groups spells into four categories (OFFENSIVE, DEFENSIVE, UTILITY, MOVEMENT) and locks out *all* spells in that category — across both mods — while a cooldown is active. **Cooldowns are global per category, by design**: an Ars OFFENSIVE cast and an Iron's OFFENSIVE cast intentionally collide on the same slot. Earlier versions exposed a `modNamespace` parameter that suggested per-mod isolation; it never actually affected the storage and was removed in 1.9.0. Disabled by default.

### Progression and affinity

Casting builds **per-school progression** (cast counts persist) and **per-school affinity** (0–100 levels, recently used schools level up). Both systems work in **both directions** as of 1.9.0: Ars and Iron's casts each contribute to the same shared school maps, and progression-derived bonuses feed back into both Ars (via spell scaling) and Iron's (via the `<school>_spell_power` attribute) damage.

**Affinity decay** is opt-in via `enable_affinity_decay` (default `false` in fresh configs; pre-existing configs keep their previous value). When enabled, each player ticks every `affinity_decay_interval_ticks` (default 1200 = 60 s) and loses a fraction of every non-zero affinity level prorated from `affinity_decay_rate` (default 0.01 per Minecraft day).

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

Config file (per-world, server-authoritative): `<world>/serverconfig/ars_n_spells-server.toml` — singleplayer: `.minecraft/saves/<World>/serverconfig/`; dedicated server: `<server>/world/serverconfig/`. (Changed from `config/ars_n_spells-common.toml` in 2.0.0 when the config became `ModConfig.Type.SERVER`; the old global file is ignored.)

### Master toggles

| Option | Default | Description |
| --- | --- | --- |
| `enable_mana_unification` | `true` | Enables all mana bridging logic. **Note:** as of 2.0.0, this no longer disables cross-cast inscriptions — only the mana sharing between systems. Inscribed items always cast; only how the cost is routed changes. |
| `mana_unification_mode` | `iss_primary` | Which mana pool is authoritative. The `disabled` value (and `enable_mana_unification=false`) means "native upstream pools pay; no sharing, no SEPARATE split, no ARS_PRIMARY conversion." Cross-cast still works in `disabled` mode; the `cross_cast_cost_multiplier` still applies. |
| `enable_resonance_system` | `true` | Full-mana resonance bonuses (Iron's). |
| `enable_cooldown_system` | `false` | Unified cooldown categories. |
| `enable_progression_system` | `true` | Cross-mod progression XP. |
| `enable_affinity_system` | `true` | Spell school affinity tracking. |
| `debug_mode` | `false` | Verbose logging. **Enables `[CrossCastTrace]` end-to-end pipeline tracing in 2.0.0+** — grep `latest.log` for `attempt=<uuid>` to get a single cast's full trace. |

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
| `read_curio_attribute_modifiers` | `true` | Read mana attribute modifiers off worn curios (Apotheosis/Apothic Curios affixes & sockets, Magical Jewelry, etc.) and mirror them across the unified pool. |

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

### v2.6.0 — Apotheosis curio bridge + ring/HUD polish

- **Apotheosis / Apothic Curios mana bridge.** Affixed and socketed curios (rings, amulets, belts) now feed the unified mana pool — the curio scanner reads attribute modifiers the same way the armor path does, via the Curios API. Generic (works for Magical Jewelry, Jewelcraft, etc. too), no hard Apotheosis dependency, gated by `read_curio_attribute_modifiers` (default on). Spell power already flowed through the player's total attribute, so it needed no change.
- **Ring / aura-HUD correctness.** Lock-free `AtomicInteger` aura peak ratchet, a Covenant version-drift probe for the aura-bar HUD mixin, and Virtue-aura handlers that honor `ENABLE_VIRTUE_AURA_SYSTEM`. Removed dead alternate-resource stubs from `CastingAuthority`.
- **Hot-path performance.** Per-tick mana-attribute churn avoided unless the value changed (OPT-008); allocation-free overlay matching by `ResourceLocation` namespace/path (OPT-009); opt-in overlay diagnostics subscriber + sorted `TreeSet` (MED-019/OPT-010); gated Iron's LP debug tracing.
- New unit tests for the peak ratchet, overlay matchers, and Virtue-aura toggle. See [CHANGELOG.md](CHANGELOG.md) for the full breakdown.

### v2.0.0 — Audit-driven major release

Addresses the comprehensive audit at [ars-n-spells-2.0.0.md](ars-n-spells-2.0.0.md). Every High and Medium-High audit hypothesis maps to a closed fix.

- **Cross-cast actually works on dedicated servers again.** Client used to claim success and cancel the use event before the server received any cast trigger, producing silent "input detected, nothing happens" failures. The client now sends a dedicated [`CrossCastRequestPacket`](src/main/java/com/otectus/arsnspells/network/CrossCastRequestPacket.java) (C2S, appended at the tail of `PacketHandler.register()` so existing IDs do not shift). The server is the sole cast authority via the new [`CrossCastingHandler.serverHandleCast`](src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java) entry point.
- **Cross-cast decoupled from mana unification.** `mana_mode=disabled` (and `enable_mana_unification=false`) used to make inscribed items inert. They now cast normally with native upstream pool costs — only mana *sharing* between systems is suppressed in those settings. The `cross_cast_cost_multiplier` still applies in all modes.
- **End-to-end pipeline tracing.** Every cast attempt gets a server-generated UUID at packet receipt, threaded through [`CrossCastContext.Entry`](src/main/java/com/otectus/arsnspells/spell/CrossCastContext.java) and logged by the new [`util/CrossCastTrace`](src/main/java/com/otectus/arsnspells/util/CrossCastTrace.java) at every pipeline stage. Set `debug_mode=true` and grep `latest.log` for `attempt=<uuid>` to follow a single cast through input → request → validate → resource check → upstream cast → exit.
- **Server-side payload validation.** New [`CrossCastValidator`](src/main/java/com/otectus/arsnspells/spell/CrossCastValidator.java) is the single authority for cross-cast payload validity (index range, spell-type resolution, Ars/Iron sub-payload sanity, Iron registry resolution). Rejections produce a translated chat message and a `descriptor_rejected` trace event. 16-case JUnit covers every branch.
- **Capability resync on transitions.** New [`CapabilityResyncHandler`](src/main/java/com/otectus/arsnspells/events/CapabilityResyncHandler.java) replays Affinity, Cooldown, and Resonance state on login, respawn, and dimension change. (Aura already had this from 1.10.0 via `AuraCapabilityProvider`.) Players no longer see stale HUDs after death or Nether transition. The previous `AffinitySyncOnLoginHandler` is retired; its job is subsumed.
- **Authoritative cost calculator.** New [`CrossCastCostResolver`](src/main/java/com/otectus/arsnspells/spell/CrossCastCostResolver.java) captures the mode × multiplier × ring state matrix in one place. For 2.0.0 it's a calculator (existing cost-mutation sites still own choreography); full site delegation lands in 2.0.1.
- **Phase 3 hardening.** Sealed [`SpellDescriptor`](src/main/java/com/otectus/arsnspells/spell/SpellDescriptor.java) with `ArsSerializedSpellDescriptor` and `IronsRegistrySpellDescriptor`; [`CastContext`](src/main/java/com/otectus/arsnspells/spell/CastContext.java) record; GameTest scaffold; [`.github/workflows/ci.yml`](.github/workflows/ci.yml) for build + test + advisory GameTest jobs. Existing 1.8.9+ inscribed items round-trip unchanged.
- **Breaking change:** clients older than 2.0.0 cannot cross-cast against a 2.0.0 server (and vice versa) because of the new packet ID. Use matching client/server versions.
- **Backward compatibility:** save format, mod config keys, and mixin injection points all unchanged. See [CHANGELOG.md](CHANGELOG.md) for the full breakdown.

### v1.9.0
- **Stabilization pass: five P0 fixes for serious-modpack readiness.**
  - **AffinitySyncPacket** no longer crashes dedicated servers — client logic moved to a new `ClientAffinityPacketHandler` and the packet wraps the capability mutation in `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, …)`. Pre-1.9.0 the packet imported `net.minecraft.client.Minecraft` directly while being registered unconditionally on the common bus.
  - **Iron's Spellbooks is actually optional now.** `IronsLPHandler` was an unconditional `@Mod.EventBusSubscriber` while importing Iron's APIs at the file header; the annotation is gone and the handler is instance-registered behind the existing `ModList.get().isLoaded("irons_spellbooks")` block. `SpellScalingUtil`'s static initializer (which referenced Iron's `AttributeRegistry.*_SPELL_POWER` slots) is converted to lazy double-checked init. New `IronsCompat` exposes a cached `isLoaded()` for hot paths.
  - **Scroll LP handling is now a real transaction.** `MixinScrollItem` rewritten as **validate → cast → commit**: HEAD stages a per-player pending entry and either cancels (safe mode) or lets the scroll proceed (death mode); RETURN reads the original `use` result and either consumes LP (success), kills the player explicitly (death mode), or no-ops (cast didn't actually consume the action). LP no longer disappears for failed casts.
  - **Cooldown "namespacing" was a lie — now removed.** The `String modNamespace` parameter on `UnifiedCooldownManager` was never part of the storage key; only the debug log saw it. Parameter dropped from every public method, both callers updated. Cooldowns are now documented as **global per category** by design — an Ars OFFENSIVE cast and an Iron's OFFENSIVE cast intentionally collide. NBT and packet wire formats unchanged.
- **Half-wired systems finished.**
  - **Iron's-side progression hook** (`IronsProgressionHandler`) listens to Iron's `SpellOnCastEvent` and feeds the same `ProgressionData` map + `<school>_spell_power` attribute application that Ars-side already does. The shared logic lives in a new `ProgressionAttributes` helper.
  - **Iron's-side affinity hook** (`IronsAffinityHandler`) mirrors `AffinityHandler` for Iron's casts. `AffinityType` gains `HOLY`, `ENDER`, `BLOOD`, `EVOCATION`, `ELDRITCH` so every Iron's stock school maps onto an entry. Adding enum values is forward and backward compatible.
  - **Ars spell scaling actually wired** — new `ArsSpellScalingHandler` computes `SpellScalingUtil.getMultiplierForCaster` on each Ars `SpellCastEvent`, stages it for the casting player with a 60-tick window, and applies it on `LivingHurtEvent` for spell-flavored damage from that player. Final amount is clamped against `spell_power_cap`. Filter rejects melee/environmental damage so the bonus only flows to actual spell hits.
  - **Affinity decay tick handler** (`AffinityDecayHandler`) ticks each player every `affinity_decay_interval_ticks` (default 1200 = 60 s) and prorates `affinity_decay_rate` from per-day to per-interval. Default for `enable_affinity_decay` is now `false` for fresh configs to avoid surprising existing players whose 1.8.9 config had it (no-op-ly) on; existing config files retain their previous setting.
  - **Login affinity sync** (`AffinitySyncOnLoginHandler`) fires one `AffinitySyncPacket` per non-zero school when the player joins, so HUD reflects persisted state immediately instead of waiting for the next cast. *(Retired in 2.0.0; superseded by `CapabilityResyncHandler` which adds respawn + dimension sync.)*
- **Configuration:** new `affinity_decay_interval_ticks` (default 1200, range 20–24000); changed default for `enable_affinity_decay` to `false` for fresh installs.
- **Backward compatibility:** strict. AffinityData, ProgressionData, CooldownData, AuraCapability NBT shapes unchanged. Network protocol stays at "1". Inscribed cross-cast items unaffected. No removed config keys, no renamed config keys. Existing 1.8.9 saves load cleanly.
- See [CHANGELOG.md](CHANGELOG.md) for the full list and follow-up notes.

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

Output jar: `build/libs/ars_n_spells-1.9.0.jar`

## License

GNU GPLv3
