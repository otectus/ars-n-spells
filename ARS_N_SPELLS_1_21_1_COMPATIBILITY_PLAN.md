# Ars 'n Spells 1.21.1 Compatibility Implementation Plan

**Project:** Ars 'n Spells (`ars_n_spells`) — NeoForge 1.21.1 (21.1.84)
**Source audited:** `C:\Users\crims\Documents\GitHub\1.21.1\ars-n-spells` (gradle `mod_version=2.0.1`)
**Modpack audited:** `Ars 'n Spells (1.21.1) (1)` CurseForge instance — 74 jars, including `ars_n_spells-2.1.0.jar`
**Date:** 2026-06-05

Every claim in this document is derived from direct inspection of the project source tree and the installed jars (class lists, constant-pool dumps, `neoforge.mods.toml` files, and datapack contents). Exact class names, registry IDs, tag paths, and JSON formats quoted below were verified inside the jars listed.

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Current Project Audit](#2-current-project-audit)
3. [Installed Modpack Audit](#3-installed-modpack-audit)
4. [Recommended Compatibility Architecture](#4-recommended-compatibility-architecture)
5. [Feature Proposals by Mod](#5-feature-proposals-by-mod)
6. [Cross-Mod Systems](#6-cross-mod-systems)
7. [Data Generation and Resource Plan](#7-data-generation-and-resource-plan)
8. [Configuration Plan](#8-configuration-plan)
9. [Testing and Verification Plan](#9-testing-and-verification-plan)
10. [Implementation Roadmap](#10-implementation-roadmap)
11. [Risk Register](#11-risk-register)
12. [Final Recommendations](#12-final-recommendations)

---

## 1. Executive Summary

Ars 'n Spells is currently a two-mod bridge (Ars Nouveau ↔ Iron's Spells 'n Spellbooks) in a **staged, partially-inert port state**: the build/loader/network/attachment layer is complete, but the four mana-unification mixins were disabled on 2026-05-10 after `@Shadow` failures against Ars 5.11.x, so all five `mana_unification_mode` settings currently behave like `DISABLED`. The installed pack, however, is a **rich, interconnected magic ecosystem**: 6 Iron's Spellbooks school addons registering 10+ new spell schools, 8 Ars Nouveau addons, the full Apotheosis suite with an existing ISS↔Apotheosis bridge (`irons_apothic`), a familiar framework with ISS spell-behavior tags (`familiarslib`), data-driven jewelry (`irons_jewelry`), spell-learning restrictions (`irons_restrictions`), and boss/loot mods already shipping ISS-aware data.

The single most important strategic finding of this audit: **most of the broken mixin surface can be replaced with public, stable events that both core mods already expose.** Ars Nouveau 5.11.7 ships `MaxManaCalcEvent`, `ManaRegenCalcEvent`, `SpellCostCalcEvent` (+`Pre`/`Post`), and `SpellDamageEvent`; Iron's 3.16.0 ships `ChangeManaEvent`, `SpellOnCastEvent` (with settable `manaCost`), `SpellPreCastEvent`, `SpellDamageEvent`, `ModifySpellLevelEvent`, and `SpellSummonEvent`. An event-first rewrite of the mana bridge eliminates the version-fragility that broke Phase 2, and leaves mixins only for the two narrow cases events cannot cover (intercepting `ManaCap` reads for shared-pool display, and the ISS `canBeCastedBy` mana check).

The second most important finding: **the project's school model is hardcoded while the pack's school registry is dynamic.** `AffinityType` is a fixed 16-value enum, but the pack registers at least 19 ISS schools across 8 namespaces (`cataclysm_spellbooks:abyssal`, `cataclysm_spellbooks:sand`, `iss_magicfromtheeast:{spirit,symmetry,dune}`, `somakespells:aqua`, `ess_requiem:blade`, `gtbcs_geomancy_plus:geo`, `discerning_the_eldritch:{eldritch,ritual}`, `aces_spell_utils:{ritual,hydro,technomancy}`, plus the 9 vanilla ISS schools). Iterating `SchoolRegistry.REGISTRY` instead of the enum makes affinity, progression, and resonance automatically cover every addon school — the highest-leverage single change for pack-wide compatibility.

Recommended implementation strategy, in order:

1. **Repair the core bridge using events instead of mixins** (Ars `MaxManaCalcEvent`/`SpellCostCalcEvent` + ISS `ChangeManaEvent`/`SpellOnCastEvent.setManaCost`), keeping only two surgical mixins.
2. **Dynamize school handling** via `SchoolRegistry.REGISTRY` iteration and a data-driven school→affinity map, instantly supporting all 6 ISS school addons in the pack.
3. **Build the `compat` package architecture** (`com.otectus.arsnspells.compat.<modid>`) with a central `CompatManager` and classloading-safe gating, mirroring the proven `IronsCompat` pattern.
4. **Ship datapack-only wins early**: an Ars-mana ISS upgrade orb type, GLM loot injection of cross-cast tablets/scrolls into ISS and AN structure loot, curio tags, and a DailyBoss config for the Wilden Chimera. These need zero code.
5. **Then layer code integrations by value**: Apotheosis affixes for Ars casting gear (modeled on `irons_apothic`'s verified JSON format), familiar synergy (`familiarslib` spell tags + AN `FamiliarSummonEvent`), Ars Elemental focus↔ISS school mapping, and `irons_restrictions` research-gating of the transcription ritual.

A critical housekeeping item precedes all of this: the shipped `ars_n_spells-2.1.0.jar` in the pack **contains no `data/` folder at all** (its apparatus recipes and curio tag were lost in the build), and contains ~40 classes that do not exist in the 2.0.1 source tree while missing ~25 classes that do. Source and release must be reconciled before any new work ships (§2.7, §11 R1).

---

## 2. Current Project Audit

### 2.1 Build system and dependencies

- NeoForge MDG (`net.neoforged.moddev` 2.0.78), Java 21, Parchment 2024.11.17, NeoForge 21.1.84, `mod_version=2.0.1`.
- Dependencies resolve via CurseMaven: `ars_nouveau` file 7417840 (5.11.1) as `implementation`; `irons_spellbooks` file 7907341 (3.15.6) as `compileOnly`. The pack actually runs **Ars Nouveau 5.11.7** and **Iron's 3.16.0** — the dev classpath lags the runtime targets (see §11 R2).
- `neoforge.mods.toml`: `ars_nouveau` **required** `[5.0.0,)`; `irons_spellbooks` **optional** `[1.21.1-3.0.0,)`. This required/optional split is correct and should be preserved for all new integrations (everything new should be optional).
- Datagen run configuration exists (`--mod ars_n_spells --all`) but **no datagen providers are implemented**; all JSON is hand-authored.
- JUnit test source set is wired (3 test classes: `ManaRegenBridgeTest`, `CrossCastValidatorTest`, `CrossModSpellListRoundTripTest`).

### 2.2 Core systems (source tree, 85 classes in `com.otectus.arsnspells`)

| System | Package | State |
|---|---|---|
| Mana bridge | `bridge` (`IManaBridge`, `ArsNativeBridge`, `IronsBridge`, `BridgeManager`, `ManaRegenBridge`) | Code-complete; **inert** because the mixins that route reads/writes are disabled |
| Unification modes | `config.ManaUnificationMode` | 5 modes: `ISS_PRIMARY`, `ARS_PRIMARY`, `HYBRID`, `SEPARATE`, `DISABLED`; mode matrix + fallback to `ARS_PRIMARY` when Iron's absent |
| Affinity | `affinity` (`AffinityType` 16-value enum, `AffinityData` attachment `copyOnDeath=true`, 0.5%/level damage bonus capped 50%) | Working; **hardcoded school list** |
| Progression | `progression` (per-school cast counts → `*_SPELL_POWER` transient attribute modifiers, id `ars_n_spells:cross_mod_school_progression`) | Working; same hardcoded-school limitation |
| Cooldowns | `cooldown` (`CooldownCategory` {OFFENSIVE, DEFENSIVE, UTILITY, MOVEMENT}, attachment without `copyOnDeath`, unified across both mods) | Working; default-off (`enable_cooldown_system=false`) |
| Resonance | `augmentation.ResonanceManager` (`1.0 + manaPct × strength × 0.2`, applied to ISS spell power via `MixinIronsSpellDamage`) | Working (this mixin is one of the two still enabled) |
| Cross-casting | `spell` (`CrossModSpell(List)` records, `DataComponentType ars_n_spells:cross_spells` with Codec + StreamCodec) | Storage works; **right-click cast invocation stubbed** (Phase 3) |
| Rituals | `rituals` (Spell Transcription, Spell Uninscription, Mana Well, Mana Infusion; `RitualRegistryHandler` splices tablets into AN's ritual map) | Inscribe/uninscribe work; casting blocked on Phase 3 |
| Equipment | `equipment.EquipmentIntegration` (Ars perk bonuses → ISS `max_mana`/`mana_regen` attribute modifiers, ids `ars_n_spells:ars_gear_max_mana` / `ars_gear_mana_regen`) | Working in shared-pool modes |
| Curio discount | `events.CurioDiscountHandler` + item tag `#ars_n_spells:curio_spell_discount` (shipped empty) | Skeleton; tag is the extension point |
| Networking | `network` (3 `CustomPacketPayload`s: Affinity/Cooldown/ResonanceSync; `ResonanceSyncPayload` registration itself gated on Iron's presence) | Working |
| Commands | `/ans mana|debug|info|mode` | Working |
| Config | `AnsConfig`, SERVER type (`<world>/serverconfig/ars_n_spells-server.toml`, auto-synced to clients) | Working; carries ~15 dead keys from the removed Sanctified Legacy integration |

### 2.3 Mixins — the central blocker

`ars_n_spells.mixins.json` (plugin `ArsNSpellsMixinPlugin`, gates Iron's mixins on a classpath probe for `io.redspace.ironsspellbooks.api.spells.AbstractSpell`):

**Enabled and working:**
- `irons.MixinIronsSpellDamage` → `AbstractSpell.getSpellPower` (resonance multiplier) and `@Redirect` of the `canBeCastedBy` mana check (conversion-rate scaling in `ARS_PRIMARY`).

**Disabled 2026-05-10 (shadow failures against Ars 5.11.4):**
- `ars.MixinManaCapability` — shadowed `livingEntity`/`mana`/`maxMana`; **verified actual fields in AN 5.11.7 are `manaData : ManaData` and `entity : LivingEntity`** (mana values moved into the `ManaData` sub-object). This fully explains the failure.
- `ars.MixinSpellResolverMana`, `ars.MixinSpellResolverContext` — shadowed members no longer exist.
- `irons.MixinIronsMagicDataMana` — shadowed `mana`/`serverPlayer` not found in Iron's 3.15.6+.

Consequence: mana unification is inert. Affinity, progression, cooldowns, resonance, rituals, and inscription are unaffected (none route through the disabled mixins).

### 2.4 Event surface currently used

- Ars Nouveau: `SpellCastEvent`, `SpellCostCalcEvent` (cross-cast cost hook).
- Iron's: `SpellOnCastEvent` (cooldowns, affinity, progression, cross-cast cost).
- NeoForge: `PlayerTickEvent.Post`, `PlayerLoggedInEvent`, `LivingDamageEvent.Pre`, `RegisterCommandsEvent`, `RegisterPayloadHandlersEvent`, FML setup/config events.

Notably **unused but available** (verified present in the installed jars — these are the repair path):
- AN: `MaxManaCalcEvent` (settable max), `ManaRegenCalcEvent` (settable regen), `SpellDamageEvent.Pre/Post`, `SummonEvent`, `FamiliarSummonEvent`, `EffectResolveEvent.Pre/Post`, `SpellModifierEvent`, `DispelEvent`.
- ISS: `ChangeManaEvent` (old/new mana, settable), `SpellPreCastEvent` (cancellable gate), `SpellDamageEvent` (settable amount + `SpellDamageSource`), `SpellHealEvent`, `ModifySpellLevelEvent`, `SpellSummonEvent` (settable creature), `SetSummonOwnerEvent`, `CounterSpellEvent`, `InscribeSpellEvent`, `SpellCooldownAddedEvent.Pre/Post`, `ModifyDefaultConfigValuesEvent`.

### 2.5 Optional-dependency handling (the pattern to replicate)

`compat.IronsCompat.isLoaded()` — double-checked-locked cached `ModList.get().isLoaded("irons_spellbooks")`. Iron's-touching event handlers are registered conditionally in the `ArsNSpells` constructor; Iron's-typed code is isolated in dedicated classes (`IronsBridge`, `IronsInscriptionReader`) that are only ever loaded behind the gate; the mixin plugin probes the classpath. This is the correct architecture and §4 generalizes it.

### 2.6 Resources

- 2 apparatus recipes (`spell_transcription` gated on `neoforge:conditions mod_loaded irons_spellbooks`; `spell_uninscription` unconditional).
- 1 item tag `ars_n_spells:curio_spell_discount` (empty, datapack extension point).
- `en_us.json` (28 keys), 2 item models. No advancements, no loot modifiers, no Patchouli/AN-documentation content, no other localization.

### 2.7 Source ↔ release divergence (must fix first)

The pack's `ars_n_spells-2.1.0.jar` is **not a build of this source tree**:

- The jar ships **no `data/` directory** — both apparatus recipes and the curio tag are missing, so the transcription/uninscription rituals are likely unobtainable in the live pack and the curio-discount tag is dead. Its lang file has only 4 keys.
- The jar contains ~40 classes absent from source: an entire `aura` package, `CursedRingHandler`/`VirtueRingHandler`/`IronsLPHandler`/`LPDeathPrevention` (the removed Sanctified-Legacy LP feature set), `compat.AddonModDetector`/`AddonSpellRegistry`/`DynamicSpellDiscovery`, `core.FeatureManager`, `ConfigCache`, 7 extra mixins (`SpellStatsPotency`, `ArsManaRegen`, `ArsPotionEffects`, `ArsManaHud`, `ScrollItem`, `IronsManaBarOverlay`, `MixinSanctifiedAbstractSpell`), and `*SyncPacket` classes.
- The jar is missing ~25 classes that source has: `ManaRegenBridge`, `IronsCompat`, `AttachmentTypes`, `ModItemsRegistry`, the `CrossModSpell*`/`CrossCastValidator`/`ModDataComponents` records, the uninscription ritual and inscription reader, and all the `*SyncPayload` classes.

Either the jar is a stale parallel branch or the source tree is a partial rewrite (version string says 2.0.1 vs jar 2.1.0). **Reconcile before implementing this plan** — every proposal below targets the source tree's architecture (the cleaner of the two), and assumes the lost `data/` resources are restored to the build.

### 2.8 Gaps and architectural constraints

- No datagen; hand-authored JSON only.
- Client classes (`ArsNSpellsClient`, `ManaBarController`, `OverlayDiagnostics`) are stubs; HUD work must use NeoForge `RegisterGuiLayersEvent` and stay out of common classloading.
- `ResonanceManager` holds static client state (multi-server leak risk).
- 22 files carry `TODO(Phase 11)` markers; TESTING_GUIDE.md marks V2–V3, V6–V7, G4–G5, V10 as blocked on Phases 2–3.
- New player state must use NeoForge attachments; new item state must use DataComponents (both patterns already established).
- All new integrations must follow the `IronsCompat` gate + isolated-class pattern; nothing may hard-import an optional mod at file scope of an always-loaded class.

---

## 3. Installed Modpack Audit

74 jars inspected. Grouped by compatibility relevance to Ars 'n Spells.

### 3.1 Core Magic Integrations

| Mod | ID / Version | Why it matters |
|---|---|---|
| Ars Nouveau | `ars_nouveau` 5.11.7 | Required core. Full event API (§2.4), `IManaCap`/`ManaCap`, perk attributes `ars_nouveau:ars_nouveau.perk.max_mana` / `.mana_regen`, dynamic registries (Glyph, Ritual, Familiar, Perk, Documentation), GLM `ars_nouveau:dungeon_loot` |
| Iron's Spells 'n Spellbooks | `irons_spellbooks` 3.16.0 | Optional core. Event API (§2.4), datapack registry `irons_spellbooks/upgrade_orb_type`, GLM `irons_spellbooks:append_loot`, schools/spells as true NeoForge registries, attributes `irons_spellbooks:{max_mana, mana_regen, cooldown_reduction, spell_power, spell_resist, cast_time_reduction, summon_damage, casting_movespeed, <school>_spell_power, <school>_magic_resist}` |
| Cataclysm: Spellbooks | `cataclysm_spellbooks` 1.1.11 | Schools `cataclysm_spellbooks:abyssal`, `:sand`; 36 spells; ships ISS staff/focus/rune tags |
| ISS: Magic From The East | `iss_magicfromtheeast` 1.1.5 | Schools `:spirit`, `:symmetry`, `:dune`; 22 spells; injects irons_jewelry materials + cauldron recipes |
| Somake Spells | `somakespells` 1.0.7 | School `somakespells:aqua`; 61 spells; ships cross-addon upgrade orbs (eldritch/geo/sound/spirit/symmetry…) — precedent for our orb proposal |
| ESS: Requiem | `ess_requiem` 0.1.6 | School `ess_requiem:blade` (spellblade); 44 melee-mage spells; **declares no ISS dep in toml** (fragility flag) |
| GTBC's Geomancy Plus / SpellLib / Spellbooks | `gtbcs_geomancy_plus` 1.1.0, `gtbcs_spell_lib` 1.5.0, `gametechbcs_spellbooks` 3.0.0 | School `gtbcs_geomancy_plus:geo`; 31 spells total; geomancy ships cross-compat data for cataclysm/mowzies/traveloptics |
| Discerning The Eldritch | `discerning_the_eldritch` 1.4.3 | Schools `:eldritch` (own), `:ritual`; 23 spells; irons_jewelry rune; summon/cleanse-immune tags |
| T.O Magic n' Extras | `traveloptics` 4.4.0.1 | 33 boss-tier spells; school `traveloptics:aqua`; hard-requires cataclysm + apothic_attributes |
| Ace's Spell Utils | `aces_spell_utils` 1.2.6.1 | Library: schools `:ritual`, `:hydro`, `:technomancy`; attributes `MANA_STEAL`, `MANA_REND`, magic crit attributes; `AbstractSummonSpell`; imbueable curios/staves |
| Darker Magic (D&D Spellbooks) | `darkermagic` 1.3.3 | 4 sculk summon spells incl. Summon Warden |
| Crystal Chronicles | `crystal_chronicles` 0.0.9 | School `prismatic` + 5 dimensions; early version (0.0.9) |
| Vampire Spells Addon | `vampire_spells_addon` 0.0.6 | Tiny Vampirism↔ISS glue (blood costs for Ray of Siphoning/Devour); uses **reflective event registration** — a pattern reference |
| Spell Engine / Spell Power | `spell_engine` 1.9.9, `spell_power` 1.4.6 | **Separate magic ecosystem (RPG Series), nothing in pack uses it.** Drags `accessories`→`owo`→`forgified-fabric-api`→`cloth-config`. Recommend removal, not integration (§3.8) |

### 3.2 Equipment and Curios Integrations

- **Curios** (`curios` 9.5.1) — the slot system everything here actually uses (ISS registers `spellbook`, `necklace`, `ring` slot data; 20+ pack mods ship `data/curios/tags`). API: `CuriosApi`, `SlotResult`, events `CurioChangeEvent`, `CurioAttributeModifierEvent`, `CurioCanEquipEvent` (**no `CurioEquipEvent` exists** — the 1.20.1-era name must not be assumed).
- **Iron's Jewelry** (`irons_jewelry` 1.6.1.1) — fully data-driven jewelry: `data/<ns>/irons_jewelry/{material,pattern,bonus}`; ISS, MFTE, DTE, and ESS all inject materials. Prime data-only integration target.
- **Accessories** (`accessories` 1.1.0-beta.53) — second slot system, only present as a `spell_engine` dependency. Do not integrate; redundant with Curios.
- **Ars Elemental** (`ars_elemental` 0.7.9.4) — elemental foci as curios + 27 glyphs + 22 `data/ars_nouveau` tags; the natural AN-side school-mapping partner (§5.6).
- **Ars Additions** (`ars_additions` 21.3.0), **Ars Controle** (`ars_controle` 1.6.15), **Ars Elemancy** (`ars_elemancy` 1.17, school-locked armor + bangles) — AN curio/equipment addons; covered by generic curio-tag work, no bespoke code needed.

### 3.3 Combat and Attribute Integrations

- **Apothic Attributes** (`apothic_attributes` 2.9.1) — package `dev.shadowsoffire.apothic_attributes` (renamed from `attributeslib`); `ALObjects$Attributes` adds `crit_chance`, `crit_damage`, `armor_pierce`, `life_steal`, `fire_damage`, `cold_damage`, etc. **No `AttributeChangedValueEvent` in this version.**
- **Apotheosis** (`apotheosis` 8.5.4) + **Apothic Enchanting** (1.5.3) + **Apothic Spawners** (1.3.4) + **Placebo** (9.9.1) — affix/gem/rarity systems, data-driven under `data/<ns>/affixes/**`; 21 enchantments.
- **Iron's Apothic** (`irons_apothic` 2.2.0) — the existing ISS↔Apotheosis bridge: 180 affixes including per-school `attribute.json`/`mana_cost.json` for **25 school IDs across 15 namespaces** (its data is the authoritative school↔namespace cross-reference used in §5.4); custom affix types `irons_apothic:attribute`, `irons_apothic:mana_cost`, `SpellTriggerAffix`, `SpellLevelAffix`; loot categories `irons_apothic:staff`, `irons_apothic:spellbook`. **Our Apotheosis work must complement, never duplicate, this mod.**
- **Iron's Restrictions** (`irons_restrictions` 5.1.1) — config + attachment-driven school research/rarity gating (`SchoolResearchScreen`, `PlayerRarityProvider`); GLMs injecting `irons_restrictions:fragment` ×4 (60%) and `:unfinished_manuscript` (30%) into chest loot via custom condition `irons_restrictions:is_chest_loot`.

### 3.4 Mob, Boss, and NPC Integrations

- **L_Ender's Cataclysm** (`cataclysm` 3.29; lib `lionfishapi` 3.0-beta) — bosses targeted by `cataclysm_spellbooks` and `traveloptics`.
- **DailyBoss** (`pladailyboss` 1.8) + **DailyBoss×ISS** (`dailyboss_irons_spellbooks` 1.0) — data-driven daily bosses at `data/pladailyboss/dailyboss/<ns>/<entity>.json` with keys `phases`, `loot_table`, `nbt`; ISS addon registers `dead_king` and `fire_boss`. Datapack-only AN boss config is trivial (§5.10).
- **Dark Doppelganger** (`darkdoppelganger` 3.3.0) — boss that hard-requires ISS ≥3.15; copies player abilities.
- **Alshanex Familiars** (`alshanex_familiars` 4.0.2) + **FamiliarsLib** (`familiarslib` 1.7.1) — 13 spell-casting pet entities; FamiliarsLib ships **ISS spell-registry tags** (`data/familiarslib/tags/irons_spellbooks/spells/attack/{aoe,close_range,…}`, `buffing/*`, `defense/*`, `movement/*`) and a `familiar_trinket` curio slot. Richest mob-integration target (§5.7).
- **Mowzies Mobs** (1.8.2), **Alex's Caves/Mobs** (2.0.10/1.22.17), **Born in Chaos** (`born_in_chaos_v1` 1.7.5, MCreator), **Deeper & Darker** (`deeperdarker` 1.4.1), **Ice and Fire CE** (`iceandfire` 2.0-beta.15) + **Ice and Fire: Spellbooks** (`ice_and_fire_spellbooks` 2.3.3, dragon-priest gear, no spells) — content sources for loot/affinity hooks, no bespoke APIs needed.
- **Vampirism** (`vampirism` 1.10.11, second modid `teamlapenlib`) — faction/level API `de.teamlapen.vampirism.api`, events `BloodDrinkEvent`, `PlayerFactionEvent`; plus `vampirism_integrations` 1.10.0.
- **Occultism** (`occultism` 1.218.0) — familiar/spirit system, no public event API (only `ItemProcessingJobEvent`); **Ars Ocultas** (`ars_ocultas` 2.4.1) already bridges AN↔Occultism (its `FamiliarDragonHolder extends AbstractFamiliarHolder` is our reference pattern).

### 3.5 Structure, Loot, and Progression Integrations

- ISS ships 8 chest GLMs + 5 entity GLMs + 6 trial-chamber GLMs, all using `neoforge:loot_table_id` conditions that append sub-loot-tables under `data/irons_spellbooks/loot_table/chests/additional_*` (note 1.21 singular `loot_table/`). Its `compat_{generic,good,treasure}_loot_modifier` files target other mods' structure chests — the exact mechanism we should reuse.
- AN ships one GLM (`ars_nouveau:dungeon_loot`) covering 15 vanilla chest tables with common/uncommon/rare rolls.
- `irons_restrictions` and `alshanex_familiars` also inject chest/entity loot (precedents).
- Patchouli 93 (books: AN, ISS, Apotheosis), Modonomicon 1.120.1 (Occultism book). **No JEI/EMI/REI in the pack** — discoverability must go through Patchouli/AN's `DocumentationRegistry` instead (§7.5).

### 3.6 Recipe, Tag, and Data Pack Integrations

- ISS tag surface (verified, partial): item `school_focus` (+ per-school `{fire,ice,…}_focus`), `staff`, `inscribed_rune`, `add_to_lectern_and_bookshelf`, `lootable_focus`; entity `summons`, `village_allies`, `cant_root`, `cant_use_portal`; damage_type `{school}_magic` ×11; mob_effect `cleanse_immune`; plus `data/neoforge/tags/damage_type/is_magic`.
- AN tag surface (partial): entity `familiar`, `an_hostile`, `spell_can_hit`, `magic_find`, `summon_bed`; item `magic_armor/...`, `ritual_loot_blacklist`; damage `is_magic`/`is_physical` (neoforge ns).
- Addon school mods ship ISS tags (`cataclysm_spellbooks`, `iss_magicfromtheeast`, `discerning_the_eldritch`, `darkermagic`, `alshanex_familiars` → `staff`, `school_focus`, `summons`, `cant_use_portal`…), so tag-level integration generalizes across the pack automatically.
- `iss_magicfromtheeast` injects ISS **alchemist cauldron recipes** and **advancements** from its own namespace — proof that ISS systems accept cross-namespace data injection.

### 3.7 Client/UI/Tooltip Integrations

- Cloth Config 15.0.140 present (only required by spell_engine; our config screen should keep using NeoForge's built-in screen via `ConfigScreenFactory`).
- No JEI/EMI: tooltips + Patchouli/AN documentation are the discoverability channels.
- HUD: ISS renders its own mana bar; AN renders its own. The `hybrid_mana_bar` config (`ars`|`irons`) needs client-only handling under `RegisterGuiLayersEvent`.

### 3.8 Low-Relevance, No-Action, or Pack-Issue Mods

- **Libraries (no direct integration):** geckolib 4.8.4, azurelib 3.1.8, player-animation-lib 2.0.4 (`playeranimator` — ISS dep; ISS handles its own cast animations, we never touch it directly), smartbrainlib, citadel, lionfishapi, atlas_api, jupiter, uranus, cobweb, prometheus (Crystal Nest config lib), soul-fire-d, irons_lib, Placebo, Patchouli, modonomicon, owo-lib, forgified-fabric-api, cloth-config.
- **No-action content:** create 6.0.10 + ars_creo/ars_technica (already bridged by their own mods), FarmersDelight + arsdelight (already bridged), occultism + ars_ocultas (already bridged), deeperdarker (bridged by darkermagic), DailyBoss core, soul-fire-d.
- **Pack issues found (report to pack author / fix in instance, not in mod code):**
  1. **Duplicate Citadel** — `citadel-1.21.1-2.7.6.jar` AND `citadel-2.7.0-1.21.1.jar` both declare `modId=citadel`; NeoForge refuses duplicate mod IDs. Remove 2.7.0.
  2. `uranus` declares a **required** dependency on `architectury [0,)` and no architectury jar is present — verify the pack actually launches.
  3. `cataclysmspells_compat.jar` is a datapack-only jar shipping Better Combat `weapon_attributes` for cataclysm_spellbooks; **Better Combat is not installed** — inert, removable.
  4. The `spell_engine`→`spell_power`→`accessories`→`owo`→`forgified-fabric-api`→`cloth-config` chain is dead weight (nothing consumes it) and adds a second trinket system + stray `rpg_series` spells/enchantments.
  5. `somakespells` declares optional dep `familiars_lib` — the real modid is `familiarslib`; its familiar integration may silently never activate.
  6. `ars_additions`, `ars_ocultas`, `pladailyboss`, `dailyboss_irons_spellbooks` declare `minecraft=[1.21,1.21.1)` which nominally excludes 1.21.1 (harmless if NeoForge treats it leniently, but worth noting).
  7. `ars_n_spells-2.1.0.jar` ships no `data/` folder (§2.7).

---

## 4. Recommended Compatibility Architecture

### 4.1 Package layout

```
com.otectus.arsnspells.compat
├── CompatManager.java            // central registry of compat modules
├── ICompatModule.java            // modid(), isEnabled(), init(IEventBus, IEventBus), onConfigReload()
├── CompatIds.java                // string constants for every modid we probe ("curios", "apotheosis", …)
├── ModPresence.java              // generalized IronsCompat: cached ModList.isLoaded per modid
├── ars_nouveau/                  // AN-specific glue beyond the core bridge (events-first rewrite)
│   ├── ArsManaEvents.java        //   MaxManaCalcEvent / ManaRegenCalcEvent / SpellCostCalcEvent hooks
│   ├── ArsDamageEvents.java      //   SpellDamageEvent.Pre (affinity/resonance application, classification)
│   └── ArsSummonCompat.java      //   SummonEvent / FamiliarSummonEvent hooks
├── irons_spells/                 // ISS-specific glue (events-first rewrite)
│   ├── IronsManaEvents.java      //   ChangeManaEvent / SpellOnCastEvent.setManaCost routing
│   ├── IronsDamageEvents.java    //   SpellDamageEvent, SpellHealEvent
│   ├── IronsSummonCompat.java    //   SpellSummonEvent / SetSummonOwnerEvent
│   └── SchoolIndex.java          //   dynamic SchoolRegistry.REGISTRY snapshot + school→affinity map
├── curios/
│   ├── CuriosCompat.java         //   CuriosApi lookups, CurioChangeEvent, CurioAttributeModifierEvent
│   └── CuriosSlots.java          //   slot constants ("spellbook", "ring", "necklace", "familiar_trinket")
├── apotheosis/
│   └── ApothicArsCompat.java     //   code-light: category registration if needed; affixes are data (§5.5)
├── irons_restrictions/
│   └── RestrictionsCompat.java   //   research-gate for transcription ritual (reflection-safe)
├── familiars/
│   └── FamiliarsLibCompat.java   //   familiarslib spell-tag awareness, familiar synergy buffs
├── ars_elemental/
│   └── ElementalSchoolMap.java   //   AE focus ↔ ISS school mapping
└── vampirism/
    └── VampirismCompat.java      //   experimental blood-cost interop (§5.11)
```

(Existing `compat.IronsCompat` stays for source-compatibility but should delegate to `ModPresence`.)

### 4.2 Module contract and gating rules

```java
public interface ICompatModule {
    String modid();                              // the optional mod this module targets
    default boolean shouldLoad() { return ModPresence.isLoaded(modid()); }
    void init(IEventBus modBus, IEventBus gameBus); // called only when shouldLoad() && config-enabled
}
```

- `CompatManager.init()` runs in the `ArsNSpells` constructor after config registration. It iterates a hardcoded list of module **suppliers** (`Supplier<ICompatModule>`), checks `ModPresence.isLoaded(modid)` **and** the per-module config gate (§8) **before** invoking the supplier — so the module class is never classloaded when its target mod is absent. This is the same isolation that already protects `IronsBridge`.
- Rule 1: an always-loaded class (anything reachable from `ArsNSpells`, handlers registered unconditionally, mixin plugin) must never import an optional mod's types. Optional types live only inside `compat/<modid>/` classes.
- Rule 2: client-only code (HUD, screens) goes under `client/` or `compat/<modid>/client/`, registered only behind `Dist.CLIENT` checks (`FMLEnvironment.dist`) — never reachable from common init. This protects dedicated servers.
- Rule 3: prefer **data over code**: tags, GLMs, recipes with `neoforge:conditions mod_loaded`, datapack-registry entries (ISS upgrade orbs, irons_jewelry materials, DailyBoss configs, Apotheosis affixes) load conditionally for free and cannot crash.
- Rule 4: where a target mod's API is too unstable to compile against (e.g., `vampire_spells_addon`-style micro-mods), use `compileOnly` CurseMaven deps if available, otherwise reflection with one-time-warn failure handling (the pattern `vampire_spells_addon` itself uses against ISS).
- Rule 5: every module gets a SERVER-config boolean `compat.<modid>.enabled` (default true) so server owners can switch any integration off without removing jars.

### 4.3 Shared utilities

- `ModPresence` — cached `isLoaded` per modid (double-checked lock, same as `IronsCompat`).
- `SchoolIndex` (in `compat.irons_spells`) — built at `FMLCommonSetupEvent` (after registries freeze): snapshot of `SchoolRegistry.REGISTRY` entries, exposing `allSchools()`, `affinityFor(ResourceLocation schoolId)`, `powerAttribute(school)` / `resistAttribute(school)` via `SchoolType`'s own attribute holders. All affinity/progression/resonance code consumes this instead of the `AffinityType` enum (§6.1).
- `LootIds` — constants for every loot-table ResourceLocation we inject into (ISS catacombs, AN dungeon tables, vanilla tables), kept in one place for auditability.
- Dependency declarations: **no new required deps.** Add optional toml entries (ordering `AFTER`) for `curios`, `apotheosis`, `irons_apothic`, `irons_restrictions`, `familiarslib`, `alshanex_familiars`, `ars_elemental`, `pladailyboss` so load order is deterministic when present. Gradle: add `compileOnly` CurseMaven coordinates for curios, apotheosis/placebo, and ars_elemental as needed per phase; never `implementation` (keeps them out of runtime dev unless testing).

---

## 5. Feature Proposals by Mod

Priorities: **Critical** (parity-blocking), **High** (large value, low-moderate effort), **Medium** (clear value), **Low** (nice-to-have), **Experimental** (prototype behind default-off config).

### 5.1 Ars Nouveau — event-first mana/cost bridge repair — **Critical**

1. **Mod:** Ars Nouveau, `ars_nouveau` 5.11.7 (required dep).
2. **Why:** Mana unification — the mod's headline feature — is inert because the three AN-side mixins were disabled after shadow failures. AN 5.11.x provides public events that cover most of what the mixins did, with no version fragility.
3. **What to integrate (verified API):**
   - `MaxManaCalcEvent` (settable `max`) — in `ISS_PRIMARY`/`HYBRID`, report the unified pool's max into AN's mana system so AN UI/perks see the bridged value.
   - `ManaRegenCalcEvent` (settable `regen`) — route regen through `ManaRegenBridge` conversion (`EQUAL_EFFECT`/`REFERENCE_POOL` modes already implemented).
   - `SpellCostCalcEvent.Pre` (public `currentCost`) — apply conversion-rate and cross-cast multipliers to AN casts; in `SEPARATE` mode, apply the dual-cost split before resolution.
   - `SpellCastEvent` (already used) — after-cast: call `BridgeManager.consumeManaForMode(player, cost, fromArs=true)` for the portion AN's own deduction didn't cover.
   - `ManaCap` writes: keep **one** repaired mixin (`MixinManaCapability`) but re-targeted to the **verified** fields `manaData : com.hollingsworth.arsnouveau.common.capability.ManaData` and `entity : LivingEntity`; intercept `getCurrentMana()/getMaxMana()` reads for display parity in shared-pool modes. Drop `MixinSpellResolverMana`/`MixinSpellResolverContext` entirely — `SpellCostCalcEvent` + `SpellCastEvent` replace them.
4. **Channel:** NeoForge game-bus events (AN events extend NeoForge `Event`); one surgical mixin; no reflection.
5. **Dependencies:** already required; bump dev dependency from CurseMaven file 7417840 (5.11.1) to a 5.11.7 file so compile targets match the pack.
6. **Risks:** double-charging mana (event + bridge both deducting) — mitigate with the existing `ThreadLocal` re-entry guard and integration tests; AN may fire cost events client-side for prediction — guard with `!level.isClientSide`; `ManaData` internals may shift again — the mixin must use `require = 0` and the mixin-plugin probe, with event paths as the functional fallback.
7. **Implementation plan:**
   - `compat/ars_nouveau/ArsManaEvents.java` (new) — the three calc-event handlers.
   - Rewrite `events/ArsManaCalcHandler` to delegate to it; delete dead mixins from `ars_n_spells.mixins.json`; fix `mixin/ars/MixinManaCapability` shadows (`manaData`, `entity`).
   - `bridge/BridgeManager`: no changes to the mode matrix; add `peekUnifiedMax(player)` for the calc events.
8. **Tests:** unit-test conversion math (extend `ManaRegenBridgeTest`); in-game matrix: cast AN spell in each of 5 modes × Iron's present/absent, assert single deduction and HUD agreement; dedicated-server smoke test.
9. **Priority: Critical** — everything else that touches mana sits on this.

### 5.2 Iron's Spells 'n Spellbooks — event-first ISS-side repair — **Critical**

1. **Mod:** `irons_spellbooks` 3.16.0 (optional dep).
2. **Why:** The disabled `MixinIronsMagicDataMana` was the ISS-side write hook. ISS's `ChangeManaEvent` (`getOldMana()/getNewMana()/setNewMana(float)`) and `SpellOnCastEvent` (`getManaCost()/setManaCost(int)`) make it unnecessary.
3. **What to integrate (verified API):**
   - `SpellOnCastEvent.setManaCost(...)` — apply conversion-rate scaling in `ARS_PRIMARY` (replaces the `@Redirect` half of `MixinIronsSpellDamage.canBeCastedBy` over time) and dual-cost split in `SEPARATE`.
   - `ChangeManaEvent` — mirror ISS mana movements into the Ars pool in `ARS_PRIMARY`/`HYBRID`; cancel/adjust drift between pools.
   - `SpellPreCastEvent` (cancellable) — unified-cooldown veto point for ISS casts (today `IronsCooldownHandler` reacts post-hoc on `SpellOnCastEvent`; pre-cast veto is cleaner and refunds nothing).
   - `SpellDamageEvent` (settable amount, exposes `SpellDamageSource`) — replaces the 60-tick-window heuristic in `ArsSpellScalingHandler` for ISS-sourced damage and gives exact school attribution for affinity (§6.2).
   - `SpellCooldownAddedEvent.Pre` — apply cooldown-reduction synergies without touching `MagicData`.
   - Keep `MixinIronsSpellDamage.getSpellPower` (resonance) — it works; annotate with `require = 0` and plan an eventual migration to `ModifySpellLevelEvent`/`SpellDamageEvent`.
4. **Channel:** NeoForge events from `io.redspace.ironsspellbooks.api.events.*`, registered inside `compat/irons_spells/` classes behind `ModPresence.isLoaded("irons_spellbooks")`.
5. **Dependencies:** keep optional toml `[1.21.1-3.0.0,)`; raise the floor to `[1.21.1-3.16.0,)` if `ChangeManaEvent` semantics require it (verify when it was added — if older, keep the floor).
6. **Risks:** `ChangeManaEvent` may fire from our own bridge writes → re-entry guard required; `setManaCost(int)` truncates fractions (conversion rates like 1.25 lose precision — accumulate remainder in an attachment); event firing order vs. AN events on cross-cast needs explicit sequencing tests.
7. **Implementation plan:** `compat/irons_spells/IronsManaEvents.java`, `IronsDamageEvents.java`; refactor `events/IronsCooldownHandler` to `SpellPreCastEvent`; delete `mixin/irons/MixinIronsMagicDataMana` from JSON (already disabled) and the class once parity is confirmed.
8. **Tests:** cast ISS spell in all 5 modes × both directions of conversion rate; mana-steal attribute interaction (`aces_spell_utils:mana_steal` triggers `ChangeManaEvent` — assert no feedback loop); cooldown veto path; dedicated server.
9. **Priority: Critical.**

### 5.3 ISS school-addon family — dynamic school discovery — **High**

1. **Mods:** `cataclysm_spellbooks` 1.1.11, `iss_magicfromtheeast` 1.1.5, `somakespells` 1.0.7, `ess_requiem` 0.1.6, `gtbcs_geomancy_plus` 1.1.0, `gametechbcs_spellbooks` 3.0.0, `discerning_the_eldritch` 1.4.3, `traveloptics` 4.4.0.1, `darkermagic` 1.3.3, `crystal_chronicles` 0.0.9, `aces_spell_utils` 1.2.6.1.
2. **Why:** Affinity/progression/resonance currently key off the 16-value `AffinityType` enum and hardcoded school names. The pack registers (verified registry IDs): `cataclysm_spellbooks:abyssal`, `cataclysm_spellbooks:sand`, `iss_magicfromtheeast:spirit|symmetry|dune`, `somakespells:aqua`, `ess_requiem:blade`, `gtbcs_geomancy_plus:geo`, `discerning_the_eldritch:ritual`, `aces_spell_utils:ritual|hydro|technomancy`, `traveloptics:aqua`, plus crystal_chronicles' prismatic. Casts in these schools currently fall through to a default bucket — a large share of the pack's spells get no affinity/progression benefit.
3. **What to integrate:** `SchoolRegistry.REGISTRY` (a true NeoForge registry, key `SchoolRegistry.SCHOOL_REGISTRY_KEY`) — iterate at common setup; each `SchoolType` carries its own power/resist attribute holders, so per-school progression bonuses need no per-mod knowledge. `SpellOnCastEvent.getSchoolType()` already supplies the school per cast.
4. **Channel:** registry lookups at `FMLCommonSetupEvent` (post-freeze) inside `compat/irons_spells/SchoolIndex.java`; **no dependency on any addon** — they're all reached through the ISS registry. A data-driven override map (`data/ars_n_spells/school_affinity_map.json` via a simple `SimpleJsonResourceReloadListener`, or a NeoForge data map) lets packs assign addon schools to legacy `AffinityType` buckets where desired (e.g., `somakespells:aqua → ICE`-style grouping) — default: every registered school becomes its own affinity track.
5. **Dependencies:** none beyond ISS (optional). Addon jars are never referenced.
6. **Risks:** attachment storage keyed by enum ordinal must migrate to `ResourceLocation` keys (write a one-time NBT migration in `AffinityData`/`ProgressionData` deserialization); unbounded school count inflates sync payloads — send only non-zero entries (already the pattern); `crystal_chronicles` is v0.0.9 and its school may change — registry iteration is immune to this.
7. **Implementation plan:** `SchoolIndex.java` (new); refactor `affinity/AffinityType` to a thin wrapper over `ResourceLocation` with legacy constants; update `AffinityData`, `ProgressionData`, `AffinitySyncPayload` codecs; `IronsAffinityHandler`/`IronsProgressionHandler` consume `SpellOnCastEvent.getSchoolType()` directly.
8. **Tests:** cast one spell from each addon school (creative spellbook) and assert affinity increments under the correct `ResourceLocation`; world save/load round-trip of migrated attachments; sync payload size with 19 schools.
9. **Priority: High** — the single biggest pack-facing win after the Critical bridge repair.

### 5.4 ISS upgrade orbs + irons_jewelry — Ars-attribute datapack entries — **High** (orb) / **Medium** (jewelry)

1. **Mods:** `irons_spellbooks` (upgrade orb registry), `irons_jewelry` 1.6.1.1.
2. **Why:** ISS's upgrade-orb system and Iron's Jewelry's material/bonus system are both **datapack registries that accept arbitrary attributes**. Verified format (`data/irons_spellbooks/irons_spellbooks/upgrade_orb_type/fire_power.json`): `{"amount":0.05,"attribute":"irons_spellbooks:fire_spell_power","containerItem":{"id":"irons_spellbooks:fire_upgrade_orb"},"operation":"add_multiplied_base"}`. AN's perk attributes have verified IDs `ars_nouveau:ars_nouveau.perk.max_mana` and `ars_nouveau:ars_nouveau.perk.mana_regen` (note the unusual doubled path — confirmed in AN's own `enchantment/mana_boost.json`). `somakespells` already ships orbs for other addons' schools — direct precedent.
3. **What to integrate:**
   - **Upgrade orb** `data/ars_n_spells/irons_spellbooks/upgrade_orb_type/ars_mana.json` targeting `ars_nouveau:ars_nouveau.perk.max_mana` (operation `add_multiplied_base`, amount ~0.05), container item: a new `ars_n_spells:source_infused_orb` item (crafted from ISS arcane essence + AN source gem) — lets ISS-style gear upgrading buff the Ars pool. In shared-pool modes the bridge makes this equivalent to ISS mana; in `SEPARATE` it's the only way ISS progression helps the Ars pool.
   - **Iron's Jewelry**: `data/ars_n_spells/irons_jewelry/material/source_gem.json` + a `bonus` granting `ars_nouveau:ars_nouveau.perk.mana_regen` (modeled byte-for-byte on `iss_magicfromtheeast`'s injected materials such as `refined_jade`).
4. **Channel:** pure datapack registry entries + one simple item registration; wrap jsons in `neoforge:conditions` `mod_loaded` for `irons_spellbooks` / `irons_jewelry`.
5. **Dependencies:** none new (data is conditional).
6. **Risks:** balance — max-mana multiplier orbs stack with Apotheosis affixes and AN perks (cap via orb amount, document in CurseForge page); the doubled `irons_spellbooks/irons_spellbooks/` path is easy to get wrong (copy ISS's own files as templates); upgrade-orb datapack registry may validate `containerItem` exists at load — keep the item registration unconditional.
7. **Implementation plan:** `registry/ModItemsRegistry` add `SOURCE_INFUSED_ORB`; resources under `data/ars_n_spells/irons_spellbooks/upgrade_orb_type/` and `data/ars_n_spells/irons_jewelry/{material,bonus}/`; recipe `ars_n_spells:source_infused_orb` (conditional).
8. **Tests:** `/datapack list` clean load with and without ISS/irons_jewelry; apply orb at ISS scroll forge → assert `ars_nouveau:ars_nouveau.perk.max_mana` modifier on gear tooltip; craft jewelry with source gem material.
9. **Priority: High** (orb — datapack-only, zero crash surface); **Medium** (jewelry).

### 5.5 Apotheosis + Iron's Apothic — Ars-side affix pack — **Medium-High**

1. **Mods:** `apotheosis` 8.5.4, `apothic_attributes` 2.9.1, `irons_apothic` 2.2.0.
2. **Why:** `irons_apothic` proves the pattern: it ships 180 data-driven affixes under `data/irons_apothic/affixes/**` giving ISS gear per-school attribute and mana-cost affixes. **Nothing equivalent exists for Ars Nouveau gear** — AN casting tools/armor get no magic-relevant Apotheosis affixes in this pack. Verified affix JSON format: type `apotheosis:attribute` (or irons_apothic's custom types), `attribute`, `operation`, per-rarity `values` keyed `apotheosis:common…mythic`, `categories`, optional `definition{affix_type, exclusive_set, weights}`.
3. **What to integrate:**
   - Affixes targeting `ars_nouveau:ars_nouveau.perk.max_mana`, `…perk.mana_regen`, and `…perk.spell_damage` (verify the spell-damage perk's full ID the same way — via AN's shipped enchantment/perk JSON) on armor categories, plus on AN casting tools if a loot category matches them.
   - A `LootCategory` check: Apotheosis categorizes items via `dev.shadowsoffire.apotheosis.loot.LootCategory`; AN spellbooks/wands likely fall into `none` and need a category definition the way `irons_apothic` defines `irons_apothic:staff` / `irons_apothic:spellbook` via `category/LootCategories` + `rarity_override`. Mirror that: `compat/apotheosis/ApothicArsCompat.java` registering an `ars_n_spells:casting_tool` category for AN spellbooks/wands (code), then data affixes reference it.
   - Do **not** re-implement school affixes — `irons_apothic` already covers all 25 school IDs including every addon school in this pack.
4. **Channel:** mostly datapack (`data/ars_n_spells/affixes/**` — Apotheosis reads affixes from all namespaces, as `irons_apothic` demonstrates) + one small gated code class for the loot category; conditions `mod_loaded apotheosis`.
5. **Dependencies:** optional toml entries for `apotheosis` (AFTER) when present; `compileOnly` Placebo+Apotheosis for the category class.
6. **Risks:** Apotheosis 8.x APIs move quickly (pin `compileOnly` to 8.5.4); affix balance vs. `irons_apothic`'s mythic values (copy its value curves for parity); category registration timing (follow `irons_apothic`'s `category/LootCategories` ordering).
7. **Implementation plan:** `compat/apotheosis/ApothicArsCompat.java`; data under `data/ars_n_spells/affixes/ars/{armor,casting}/…`; `rarity_override` for AN spellbook items if categorized.
8. **Tests:** reforge AN gear at Apotheosis salvaging/reforging stations → Ars affixes appear; absent-Apotheosis launch clean; affix values match per-rarity expectations.
9. **Priority: Medium-High.**

### 5.6 Ars Elemental — focus ↔ school mapping — **Medium**

1. **Mod:** `ars_elemental` 0.7.9.4 (deps: ars_nouveau `[5.11,)`).
2. **Why:** Ars Elemental's elemental foci (curios) define AN-side elemental identity (fire/water/air/earth); ISS defines per-school power via `<school>_spell_power` attributes and per-school focus items tagged `irons_spellbooks:school_focus`. Bridging the two gives a coherent "one element, both systems" experience — e.g., a player wearing AE's fire focus gets a configurable bonus to `irons_spellbooks:fire_spell_power`, and ISS fire-focus holders get bonus fire-affinity gain on AN fire glyphs.
3. **What to integrate:** AE focus detection via Curios `SlotResult` lookups (item registry names under `ars_elemental:`); apply transient attribute modifiers (id `ars_n_spells:elemental_focus_bridge`) mapped fire→fire, water→ice (closest ISS analog; configurable), air→lightning, earth→nature. Reverse direction: `SchoolIndex.affinityFor` boosts when matching ISS focus equipped (tag `irons_spellbooks:{school}_focus`).
4. **Channel:** Curios API lookups + `CurioChangeEvent`; config-driven mapping table (`compat.ars_elemental.focus_map` in TOML, default above); gated module `compat/ars_elemental/ElementalSchoolMap.java`.
5. **Dependencies:** optional toml `ars_elemental` AFTER; `compileOnly` not required (item lookups by `ResourceLocation`, no AE classes touched — fully data/registry-driven, the safest pattern).
6. **Risks:** water→ice mapping is a design opinion (config-overridable); AE item IDs could change (registry lookups fail soft — log once); double-buffing when both foci worn (cap modifier).
7. **Implementation plan:** `ElementalSchoolMap.java`; config block; lang for tooltip line "Resonates with <school>" appended via `ItemTooltipEvent` (client, gated).
8. **Tests:** equip each focus → attribute appears in `/attribute` query; unequip → removed; with AE absent module never loads.
9. **Priority: Medium.**

### 5.7 FamiliarsLib + Alshanex Familiars + AN familiars — summon/familiar synergy — **Medium**

1. **Mods:** `familiarslib` 1.7.1, `alshanex_familiars` 4.0.2, `ars_nouveau` (familiars), `irons_spellbooks` (summons).
2. **Why:** The pack has three companion systems (AN familiars, ISS summons, Alshanex spell-casting pets) that ignore each other. Verified surfaces: AN `FamiliarSummonEvent` (+ `FamiliarRegistry`, `AbstractFamiliarHolder`, entity tag `ars_nouveau:familiar`), ISS `SpellSummonEvent`/`SetSummonOwnerEvent` + entity tag `irons_spellbooks:summons` + attribute `irons_spellbooks:summon_damage`, familiarslib `AbstractSpellCastingPet` + ISS-spell-registry behavior tags (`data/familiarslib/tags/irons_spellbooks/spells/attack/aoe` etc.) + curio slot `familiar_trinket`.
3. **What to integrate:**
   - **Unified summon damage**: in AN `SummonEvent`, apply the owner's `irons_spellbooks:summon_damage` attribute value as a damage modifier to AN summons (attachment-tagged), so ISS summon investment benefits AN summons. Gate: shared-pool modes only, config multiplier.
   - **Familiar affinity resonance**: while an AN familiar is active (`FamiliarSummonEvent`/dispel), grant a small bonus to affinity gain; while an Alshanex pet of a given ISS school is out, grant matching school affinity gain (pet school discoverable via `familiarslib` `SchoolTypeAccessor`-style data — verify exact API in `familiarslib/util/familiars/FamiliarHelper`).
   - **Tag unification**: add AN familiars to a new `#ars_n_spells:magical_companions` entity tag aggregating `#ars_nouveau:familiar`, `#irons_spellbooks:summons`, and Alshanex pet entity types — usable by our own systems and other datapacks.
4. **Channel:** AN/ISS events (no familiarslib classes needed for the first two features if school detection is done via entity-type → school config map); entity-type tags (data, unconditional aggregation via optional tag entries `{"id":"...","required":false}`).
5. **Dependencies:** none hard; optional toml for `familiarslib`/`alshanex_familiars`.
6. **Risks:** summon damage stacking with `irons_apothic` telepathic/summon affixes (modest default 1.0 = off, opt-in); pet-school mapping is per-entity data that may grow stale across Alshanex updates (fail-soft registry lookups).
7. **Implementation plan:** `compat/familiars/FamiliarsLibCompat.java`, `compat/ars_nouveau/ArsSummonCompat.java`, `compat/irons_spells/IronsSummonCompat.java`; tag json `data/ars_n_spells/tags/entity_type/magical_companions.json`.
8. **Tests:** summon AN familiar → affinity-gain bonus visible in `/ans debug`; ISS Raise Dead with summon_damage gear vs AN Summon Vex damage parity check; tag resolves with any subset of the three mods present.
9. **Priority: Medium.**

### 5.8 Iron's Restrictions — research-aware transcription — **Medium**

1. **Mod:** `irons_restrictions` 5.1.1.
2. **Why:** The pack gates ISS spell learning behind school research (config + attachments + `SchoolResearchScreen`). Our Spell Transcription ritual can copy an ISS spell onto an item **bypassing that progression** — a balance hole in this pack specifically.
3. **What to integrate:** before inscribing an ISS spell, ask irons_restrictions whether the nearest/initiating player has learned/unlocked that spell's school+rarity. Verified classes: `util/SchoolUtils`, `player/PlayerRarityProvider`, attachment registry `registries/DataAttachmentRegistry`. No public API package — integrate via reflection-safe wrapper (`RestrictionsCompat.canTranscribe(Player, spellId, level)`) with a config fallback (`compat.irons_restrictions.gate_transcription`, default true; if reflection fails, log once and allow).
4. **Channel:** reflection inside gated module (their internals are obfuscation-free but unstable — reflection keeps us compile-independent).
5. **Dependencies:** optional toml AFTER `irons_restrictions`.
6. **Risks:** their attachment/data layout changes silently → reflection returns "unknown" → fail-open (configurable fail-closed for hardcore servers); multiplayer: check must run server-side in the ritual, not client.
7. **Implementation plan:** `compat/irons_restrictions/RestrictionsCompat.java`; hook in `rituals/SpellTranscriptionRitual` validation step; new lang keys for the denial message.
8. **Tests:** un-researched school → ritual refuses with message; researched → proceeds; mod absent → unaffected.
9. **Priority: Medium** (pack-balance integrity).

### 5.9 Curios — first-class equipment integration — **High**

1. **Mod:** `curios` 9.5.1 (already a transitive presence: AN, ISS, and most addons require it).
2. **Why:** `CurioDiscountHandler` + the empty `#ars_n_spells:curio_spell_discount` tag are skeletal, and the Sanctified-Legacy curio features were removed. Curios is the pack's universal equipment bus: ISS slots `spellbook/necklace/ring`, familiarslib `familiar_trinket`, default slots `back/belt/body/bracelet/charm/curio/hands/head/necklace/ring`.
3. **What to integrate (verified API):**
   - Populate `#ars_n_spells:curio_spell_discount` with sensible defaults (AE foci, ISS rings via optional tag entries) and implement the discount on **both** sides: AN `SpellCostCalcEvent` and ISS `SpellOnCastEvent.setManaCost` (today only the ISS path exists).
   - Use `CuriosApi`/`SlotResult` for equipment scans instead of inventory walks; react to `CurioChangeEvent` (NOT `CurioEquipEvent`, which does not exist in 9.5.1) to invalidate `EquipmentIntegration` caches the moment a curio changes.
   - `CurioAttributeModifierEvent` to let datapacks attach our progression attributes to arbitrary curios.
   - Optional future item: a "Resonance Charm" curio (slot `charm`) that raises the resonance cap — gives the mod a visible in-world artifact.
4. **Channel:** `compileOnly` Curios + gated `compat/curios/CuriosCompat.java` (Curios is technically always present in this pack, but AN can run without it in theory — keep the gate).
5. **Dependencies:** optional toml `curios` AFTER.
6. **Risks:** discount stacking (tag items + AE foci + Apotheosis mana_cost affixes) — clamp total discount (config `max_total_discount`, default 50%); slot scans every cast are hot-path — cache per-player on `CurioChangeEvent`.
7. **Implementation plan:** `compat/curios/CuriosCompat.java`, `CuriosSlots.java`; extend `events/CurioDiscountHandler` to AN side; tag file gains default entries with `"required": false`.
8. **Tests:** equip tagged curio → AN and ISS cast costs both drop; swap curio mid-session → cache invalidates; clamp verified with stacked discounts.
9. **Priority: High.**

### 5.10 DailyBoss — AN boss entry — **Low** (datapack-only)

1. **Mods:** `pladailyboss` 1.8, `dailyboss_irons_spellbooks` 1.0.
2. **Why:** The ISS addon proves the format; AN's Wilden Chimera is the obvious missing magic boss. Verified config format: `data/pladailyboss/dailyboss/<entity_ns>/<entity_path>.json` with `phases`, `loot_table` (array), `nbt`.
3. **What to integrate:** `data/ars_n_spells/... ` — note: the registry path is namespaced by **entity**: ship `data/pladailyboss/dailyboss/ars_nouveau/wilden_boss.json` (verify the chimera's exact entity ID in AN 5.11.7 — `ars_nouveau:wilden_boss`) pointing at AN dungeon loot plus an `ars_n_spells:chests/cross_mod_treasure` table (§7.3). Because this writes into `pladailyboss`'s data namespace, ship it as a **built-in optional datapack** (`pack.mcmeta` feature) or accept that the file is inert when DailyBoss is absent (datapack data for absent mods is simply unused — zero risk).
4. **Channel:** pure data.
5. **Dependencies:** none.
6. **Risks:** none meaningful (inert when absent); chimera NBT phases unknown — start with plain `loot_table` only.
7. **Implementation plan:** one JSON + one loot table.
8. **Tests:** with DailyBoss installed, `/dailyboss` (or its summon flow) offers the chimera and drops the configured loot.
9. **Priority: Low** (trivial effort, fun payoff).

### 5.11 Vampirism + Vampire Spells Addon — blood-caster interop — **Experimental**

1. **Mods:** `vampirism` 1.10.11, `vampire_spells_addon` 0.0.6.
2. **Why:** `vampire_spells_addon` already converts some ISS casts to blood costs for vampires (config keys verified: `bloodCostManaFloor/Ceiling`, `bloodCostRatioMin/Max`, `devourManaMultiplier`, …). Extending the same fiction to AN casts (vampire players pay part of AN spell costs in blood) is thematically coherent for this pack — and ISS has a Blood school + `blood_magic` damage tag, AN has blood-adjacent affinity (`AffinityType.BLOOD` already exists in our enum).
3. **What to integrate (verified API):** Vampirism events `PlayerFactionEvent.FactionLevelChanged` (detect vampire status), `BloodDrinkEvent`; faction handler API under `de.teamlapen.vampirism.api.entity.player.vampire`. Feature: in `AnsConfig`, `compat.vampirism.blood_cost_fraction` (default 0 = off) — when a vampire-faction player casts an AN spell, divert that fraction of cost to blood via Vampirism's blood stats, mirroring the addon's mana-floor/ceiling design. Respect `vampire_spells_addon` if present (skip ISS-side handling — it owns that).
4. **Channel:** gated `compat/vampirism/VampirismCompat.java`; `compileOnly` Vampirism if its CurseMaven artifact is stable, else reflection (the addon itself uses reflection against ISS — precedent).
5. **Dependencies:** optional toml `vampirism` AFTER.
6. **Risks:** double-charging if `vampire_spells_addon` widens scope to AN later (detect its presence and defer); Vampirism API breadth makes compileOnly heavy; balance is speculative — hence default-off Experimental.
7. **Implementation plan:** `VampirismCompat.java`; config block; tooltip line on spellbooks for vampires.
8. **Tests:** vampire player casts AN spell → blood drains per fraction, mana cost reduced; human player unaffected; both gating mods absent → module never loads.
9. **Priority: Experimental.**

### 5.12 Ace's Spell Utils — attribute interop guards — **Low**

1. **Mod:** `aces_spell_utils` 1.2.6.1 (library for cataclysm_spellbooks, DTE, traveloptics).
2. **Why:** Its attributes (`MANA_STEAL`, `MANA_REND`, `SPELL_RES_PENETRATION`, magic crit attributes) mutate ISS mana and damage outside the normal cast loop. With mana unification active, mana-steal must route through the bridge or pools desync.
3. **What to integrate:** nothing proactive — `ChangeManaEvent` (§5.2) already catches attribute-driven mana movement **if** ASU uses `MagicData.setMana` (verify in testing). If it bypasses events, add a low-priority reconciliation tick (compare pools, settle drift) — which is worth having as a safety net regardless.
4. **Channel:** covered by core ISS module + a `PoolReconciler` utility (config-gated, default on in shared-pool modes, runs 1×/s).
5. **Dependencies/Risks/Tests:** none new; test: hit a target with mana-steal weapon in `ISS_PRIMARY` → both HUDs agree afterwards.
6. **Priority: Low** (verification + safety net, not a feature).

### 5.13 Dark Doppelganger, Cataclysm, Ice & Fire, and other content mods — loot/affinity touchpoints — **Low**

- No code integrations warranted: their magic-relevant content is already routed through ISS addons (`cataclysm_spellbooks`, `ice_and_fire_spellbooks`, `traveloptics`) and therefore covered by §5.3's dynamic schools.
- Include their boss/structure loot tables in the cross-mod treasure GLM targets (§7.3): Cataclysm boss chests, Ice & Fire dragon caves, Deeper&Darker ancient-city-analog tables — concrete table IDs to be enumerated from their jars during Phase 4 datagen (the jars ship standard `loot_table/` trees).
- `darkdoppelganger` requires ISS and reads the player's spells; verify our cross-cast data component doesn't leak into the doppelganger's copied loadout (test in Phase 5).
- **Priority: Low.**

### 5.14 Patchouli / AN Documentation — discoverability — **Medium** (see §7.5)

No JEI/EMI exists in this pack; in-game documentation is the only discoverability channel. AN 5.x exposes `DocumentationRegistry` (verified in `api/registry/`) — register entries for the four rituals, the cross-cast tablet flow, the unification modes, and the source-infused orb. Patchouli is present (AN/ISS/Apotheosis books) but AN's native documentation is the lower-maintenance choice since AN is a required dep.

---

## 6. Cross-Mod Systems

### 6.1 Unified school model (spans ISS + all 6 school addons + AN + AE)

One internal school identity: `ResourceLocation`-keyed, sourced from `SchoolIndex` (ISS registry) on the ISS side and from a glyph→school classifier on the AN side (`SpellAnalysis` already walks `Spell.unsafeList()`; extend its mapping table to emit the same ResourceLocation keys, with AE elemental glyphs mapping to the matching ISS school IDs where the config map says so). Affinity, progression, resonance, cooldown categorization, and the Apotheosis/orb data all consume this single model. This is the backbone — implement before any school-facing feature.

### 6.2 Spell damage classification and resist parity

Both mods tag `neoforge:damage_type/is_magic`; ISS additionally ships per-school `damage_type` tags (`fire_magic`, `blood_magic`, …) and per-school `<school>_magic_resist` attributes. System: in AN `SpellDamageEvent.Pre`, classify the spell's school (6.1) and respect the target's matching ISS resist attribute (configurable factor) — making ISS resist gear meaningful against AN casters in PvP/PvE. Inverse: ISS `SpellDamageEvent` already benefits from affinity bonuses. Ship `data/ars_n_spells/tags/damage_type/` entries adding AN damage types into ISS's per-school magic tags where the school mapping is unambiguous (optional tag entries, fail-soft).

### 6.3 Magical loot injection (spans ISS, AN, Apotheosis, content mods)

One GLM suite (§7.3) injecting: cross-cast tablets + ritual components into ISS structure loot (catacombs, evoker fort, pyromancer tower — biome/structure tags verified) and AN dungeon loot; ISS scrolls/inks into AN-relevant loot via ISS's own `additional_*` sub-table pattern; a shared `ars_n_spells:chests/cross_mod_treasure` table referenced by boss configs (§5.10). Use `neoforge:loot_table_id` conditions exactly as ISS's verified `compat_generic_loot_modifier` does.

### 6.4 Mana/source conversion (spans AN source machinery + ISS pool)

`RegenSynergyHandler` (Source-Jar proximity regen) is the seed. Extend: Mana Well ritual draws from nearby AN source jars to refill the unified pool; conversely a future "source condenser" sink converts ISS mana overflow (resonance > threshold) into AN source trickle. All conversions go through `ManaRegenBridge` rates so `EQUAL_EFFECT`/`REFERENCE_POOL` semantics stay consistent. Balance note: source is cheap mid-game in this pack (Create + AN automation via ars_creo/ars_technica) — cap jar-fed regen at a configurable fraction of natural regen.

### 6.5 Summon/companion unification

§5.7's three-system synergy plus the `#ars_n_spells:magical_companions` aggregate tag. Shared rule: companion damage scales from `irons_spellbooks:summon_damage`; companion presence feeds affinity gain. ISS `SetSummonOwnerEvent` lets us attribute kills by ISS summons to the owner for progression credit; AN `SummonEvent.Death` closes the same loop on the AN side (both verified).

### 6.6 Curio-based spell modifiers

`#ars_n_spells:curio_spell_discount` (cost) + planned `#ars_n_spells:curio_affinity_boost` and `#ars_n_spells:curio_resonance_cap` tags — all consumed by one `CuriosCompat` scan with a single per-player cache. Datapack authors integrate any mod's curios with zero code.

### 6.7 Unified progression hooks

Progression cast-counts already span both mods; with 6.1 they span all addon schools. Add advancement triggers (§7.4) on cross-mod milestones (first transcription, first cross-cast, affinity 50/100, all-schools-touched) so pack authors can hang quest systems (FTB Quests etc., if later added) off stable advancement IDs.

---

## 7. Data Generation and Resource Plan

### 7.1 Stand up datagen (currently absent)

The `data` run config exists; add providers under `com.otectus.arsnspells.datagen`:
`AnsItemTagsProvider`, `AnsEntityTagsProvider`, `AnsDamageTagsProvider`, `AnsRecipeProvider`, `AnsGlobalLootModifierProvider`, `AnsAdvancementProvider`, `AnsLanguageProvider`, wired in a `DataGenerators` class on `GatherDataEvent`. Hand-authored JSON (the two apparatus recipes) migrates into providers so conditional logic (`mod_loaded`) is generated consistently — and so the §2.7 "data lost from the jar" failure mode can't recur silently (datagen output is part of the build).

### 7.2 Tags

| Tag | Type | Contents (all optional entries, `required:false`) |
|---|---|---|
| `ars_n_spells:curio_spell_discount` | item | AE foci, ISS rings (defaults; currently empty) |
| `ars_n_spells:curio_affinity_boost` | item | empty default (datapack hook) |
| `ars_n_spells:magical_companions` | entity_type | `#ars_nouveau:familiar`, `#irons_spellbooks:summons`, Alshanex pets |
| `irons_spellbooks:add_to_lectern_and_bookshelf` | item (inject) | our tablets (so ISS lecterns accept them) |
| `irons_spellbooks:<school>_magic` damage tags | damage_type (inject) | matching AN damage types per §6.2 |
| `curios:charm` (or chosen slot) | item (inject) | Resonance Charm when implemented |

### 7.3 Loot (GLMs under `data/ars_n_spells/loot_modifiers/`)

- `iss_structure_tablets.json` — condition `neoforge:loot_table_id` ∈ {`irons_spellbooks:chests/catacombs`(verify exact ids from ISS `loot_table/chests/`), evoker fort, pyromancer tower} → append `ars_n_spells:chests/transcription_components`; wrapped in `mod_loaded irons_spellbooks`.
- `an_dungeon_scrolls.json` — AN dungeon/vanilla tables → `ars_n_spells:chests/iss_scroll_cache` (uses ISS's `RandomizeSpellFunction` if reachable from data — verified loot function exists: `irons_spellbooks:randomize_spell`); `mod_loaded irons_spellbooks`.
- `content_mod_treasure.json` — Cataclysm/Ice&Fire/DeeperDarker boss+structure tables (enumerate in Phase 4) → `ars_n_spells:chests/cross_mod_treasure`.
- Use NeoForge's standard `neoforge:add_loot_table` style (or a minimal custom modifier mirroring ISS's `append_loot`) — prefer vanilla-NeoForge types to avoid owning loot code.

### 7.4 Advancements

`ars_n_spells:root`, `first_transcription`, `first_uninscription`, `first_cross_cast`, `affinity_adept` (50), `affinity_master` (100), `polymath` (cast in ≥6 schools), `unified` (enable a shared-pool mode and cast both systems). Custom criteria triggers where needed (`ProgressionTracker` already counts everything required).

### 7.5 Documentation, lang, tooltips

- AN `DocumentationRegistry` entries for the 4 rituals, tablets, orb, unification modes.
- Tooltips: tablets show inscribed spell + school + cross-cast cost multiplier (via DataComponent, already network-synced); tagged curios show "Spell discount: X%"; AE foci show "Resonates with <ISS school>" when §5.6 active. All behind `ItemTooltipEvent` on client dist.
- Lang: every new key in `en_us.json` via `AnsLanguageProvider`; namespaced `ars_n_spells.compat.<modid>.*` for compat-specific strings.

### 7.6 Restore lost resources

Re-ship (and verify in CI) the two apparatus recipes, the curio tag, and full lang in the built jar — add a Gradle `jar` task assertion or a unit test that opens the built jar and asserts `data/ars_n_spells/recipes/apparatus/spell_transcription.json` exists (§2.7, §11 R1).

---

## 8. Configuration Plan

All SERVER-type (auto-synced), under new `[compat]` section of `AnsConfig`:

```toml
[compat]
  curios_enabled = true
  apotheosis_enabled = true
  irons_restrictions_enabled = true
  familiars_enabled = true
  ars_elemental_enabled = true
  vampirism_enabled = false        # Experimental
  dailyboss_data_enabled = true    # toggles GLM/datapack conditions where applicable

[compat.balance]
  max_total_curio_discount = 0.50
  elemental_focus_power_bonus = 0.10      # +10% school power per matching focus
  summon_damage_bridge_multiplier = 0.0   # 0 = off (opt-in)
  familiar_affinity_gain_bonus = 0.15
  blood_cost_fraction = 0.0               # vampirism, 0 = off
  resist_parity_factor = 0.5              # how strongly ISS resists apply to AN damage
  source_jar_regen_cap = 0.5              # fraction of natural regen

[compat.schools]
  # school -> affinity-bucket overrides; default: each school is its own track
  school_affinity_overrides = []          # e.g. ["somakespells:aqua=ice", "traveloptics:aqua=ice"]
  ars_elemental_focus_map = ["fire=irons_spellbooks:fire", "water=irons_spellbooks:ice", "air=irons_spellbooks:lightning", "earth=irons_spellbooks:nature"]
```

Also: prune the ~15 dead Sanctified-Legacy keys (one release with migration note); keep `debug_mode` gating new compat diagnostics in `/ans debug` (per-module load status, school index dump, pool reconciliation stats). `ConfigScreenFactory` gains a Compat page. Every module checks its flag at registration time **and** at `onConfigReload` (handlers self-disable via a volatile boolean rather than unregistering).

---

## 9. Testing and Verification Plan

### 9.1 Build and static verification

- `./gradlew build test` green; jar-content test asserts `data/` files present (§7.6).
- Mixin audit: only 2 mixins remain (`MixinManaCapability` repaired, `MixinIronsSpellDamage`); `require = 0` + plugin probe on both; run with `-Dmixin.debug.verify=true` once per dependency bump.

### 9.2 Launch matrix (the core gate for every phase)

| Scenario | Assert |
|---|---|
| Client, full pack (74 jars, citadel dedup'd) | clean launch, all compat modules report loaded in `/ans debug` |
| Client, AN only (no ISS, no addons) | clean launch; modules report skipped; rituals degrade per design |
| **Dedicated server**, full pack | clean launch; no client-class loading (watch for `RegisterGuiLayersEvent`/screen classes in stacktraces) |
| Dedicated server, AN only | clean |
| Each optional mod removed one at a time (curios-suite caveat: curios is required by AN deps in practice) | matching module skips, zero errors |

### 9.3 Functional suites

- **Mana**: 5 modes × {AN cast, ISS cast, cross-cast} × {SP, dedicated MP} — single deduction, HUD parity, conversion rates, dual-cost split + rollback, mana-steal reconciliation (§5.12).
- **Multiplayer sync**: second client joins mid-session — affinity/cooldown/resonance payloads arrive (login handlers); spectate ensure no payload spam (packet logging via `debug_mode`).
- **Tooltips**: tablet, orb, discounted curio, AE focus — render on client, absent on dedicated-server logs.
- **Loot**: `/loot give @p loot irons_spellbooks:chests/catacombs` style sampling ×100 for each modified table; verify our appends and that ISS/AN native GLMs still fire (regression).
- **Recipes**: `/recipe give` + JEI-less verification via crafting; conditional recipes absent when gate mod missing (`/reload` after datapack toggle).
- **Spellcasting**: one spell from each of the 19 schools → affinity track increments under correct id; progression attribute modifiers appear in `/attribute`; resonance multiplier visible in damage numbers at full mana.
- **Curio/equipment**: equip/unequip cycles (CurioChangeEvent cache invalidation), discount clamping, attribute cleanup on death/dimension change (`copyOnDeath` semantics).
- **Regression (every release)**: vanilla AN progression (Worn Notebook → Apprentice book) and vanilla ISS progression (scroll → spellbook inscribe) untouched with all compat enabled; `irons_apothic` affixes still roll on ISS gear (we share Apotheosis data space); `darkdoppelganger` boss fight with cross-cast tablets in inventory.

### 9.4 Performance

- Spark/profiler pass on `PlayerTickEvent` handlers (affinity decay, resonance, regen synergy, reconciler) with 10 simulated players; all per-tick work must be O(equipped items) with caches, never O(registry).

---

## 10. Implementation Roadmap

**Phase 1 — Architecture and optional compatibility loader** (foundation)
`CompatManager`/`ICompatModule`/`ModPresence`/`CompatIds`; config `[compat]` section; `/ans debug` module status; datagen scaffolding (§7.1); **source↔release reconciliation (§2.7) and jar-content CI test**; dep bumps (AN 5.11.7, ISS 3.16.0 compileOnly).

**Phase 2 — Critical AN + ISS parity** (§5.1, §5.2)
Event-first mana bridge; repair `MixinManaCapability` shadows; delete dead mixins; cross-cast cost paths on events; full mana test matrix green. *Exit criterion: all 5 unification modes functional on client + dedicated server.*

**Phase 3 — Schools + Curios** (§5.3, §5.9, §6.1)
`SchoolIndex` + dynamic affinity/progression (attachment key migration); Curios first-class module with caching + both-sides discount; AE focus mapping (§5.6).

**Phase 4 — Loot, recipes, tags, progression** (§5.4, §7.2–7.4)
Upgrade orb + jewelry data; GLM suite; advancements; lectern/bookshelf tag injection; DailyBoss chimera config (§5.10).

**Phase 5 — Mob/NPC/boss integrations** (§5.7, §5.13)
Familiar/summon synergy; companion tag; doppelganger regression test; content-mod loot table enumeration completes the GLM list.

**Phase 6 — UI, tooltips, configs, polish** (§5.14, §7.5, §8)
AN documentation entries; tooltips; config screen Compat page; dead-key pruning; `hybrid_mana_bar` HUD via `RegisterGuiLayersEvent`.

**Phase 7 — Testing, balancing, release prep** (§9)
Full matrix; performance pass; balance tuning vs. irons_apothic/Apotheosis stacking; Experimental modules (`vampirism`) behind default-off; CHANGELOG + CurseForge description update; report pack issues (§3.8) to instance owner.

Dependencies: 2 blocks 3–5; 3 blocks 4 (school-keyed data) and 5; 4 and 5 are parallelizable; 6–7 trail.

---

## 11. Risk Register

| # | Risk | Severity | Mitigation |
|---|---|---|---|
| R1 | **Source/release divergence** (§2.7): shipped 2.1.0 jar ≠ 2.0.1 source; jar lacks all `data/`; unknown which tree is canonical | Critical | Reconcile first; adopt source tree as canonical; CI jar-content test; tag releases from this repo only |
| R2 | Dev classpath lags runtime (AN 5.11.1 vs 5.11.7; ISS 3.15.6 vs 3.16.0) | High | Bump CurseMaven file IDs each release cycle; mixin `require=0`; event-first design minimizes exposure |
| R3 | Mixin shadow drift recurs on AN/ISS point releases | High | Only 2 mixins remain; both behind plugin probes with event fallbacks; `-Dmixin.debug.verify` in CI run |
| R4 | Double mana charging / pool desync (events + bridge + third-party attribute effects) | High | ThreadLocal re-entry guards; `PoolReconciler` safety net; mana test matrix |
| R5 | Classloading optional-mod types on absent installs | High | Supplier-gated module loading (§4.2); no optional imports outside `compat/<modid>/`; launch matrix (§9.2) |
| R6 | Client classes on dedicated server | High | Dist-gated client packages; dedicated-server launch in every phase's exit criteria |
| R7 | Attachment school-key migration corrupts player data | Medium | Versioned NBT with legacy-enum fallback parsing; backup note in changelog; round-trip tests |
| R8 | Balance: stacking (orbs + Apotheosis affixes + perks + curio discounts + affinity + resonance) trivializes content | Medium | Clamps (`max_total_curio_discount`, `spell_power_cap` exists), opt-in multipliers default conservative, copy irons_apothic value curves |
| R9 | Multiplayer sync gaps (new school-count payloads, resonance spam) | Medium | Non-zero-only delta sync; payload-size test at 19 schools; login resync handlers (pattern exists) |
| R10 | Reflection targets (`irons_restrictions`, optional `vampirism`) shift | Medium | Fail-open with one-time warn (configurable fail-closed); covered by version-bump test pass |
| R11 | Pack-level launch hazards outside our control (duplicate citadel, uranus→architectury) | Medium | Document in CHANGELOG/CurseForge page; not fixable in mod code |
| R12 | Performance of per-tick handlers at scale | Low-Med | Caches on CurioChangeEvent; 20-tick cadences (existing pattern); profiler gate (§9.4) |
| R13 | `ess_requiem` (no ISS dep declared) or other addons misregister schools late | Low | `SchoolIndex` built at common setup with late-refresh on `/reload`; registry iteration tolerates absence |

---

## 12. Final Recommendations

Implement in this order, and resist the temptation to start with the fun content integrations:

1. **Reconcile source vs. shipped jar and restore the lost `data/` files (R1).** Until this is done, the pack's installed copy of the mod is missing its own recipes, and any plan built on the source tree may be invalidated by whatever produced the 2.1.0 jar.
2. **Rebuild mana unification on events, not mixins (§5.1–5.2).** The verified existence of `MaxManaCalcEvent`/`ManaRegenCalcEvent`/`SpellCostCalcEvent` on the AN side and `ChangeManaEvent`/`SpellOnCastEvent.setManaCost` on the ISS side converts the project's hardest maintenance problem into ordinary event handling, and un-blocks the mod's core promise. Keep exactly two mixins.
3. **Make schools dynamic (§5.3).** One refactor makes affinity/progression/resonance cover all ~19 schools and every future ISS addon with zero per-mod code — the highest compatibility-per-line-of-code in this entire plan, and it's what makes the mod feel native to *this* pack rather than to vanilla AN+ISS.
4. **Ship the datapack-only wins in the same release (§5.4, §5.10, §7.3).** Upgrade orb, loot injection, lectern tags, DailyBoss chimera: high visibility, zero crash surface, and they exercise the conditional-data pipeline that everything later relies on.
5. **Then Curios (§5.9), Apotheosis affixes (§5.5), familiars (§5.7), and the rest by priority.** Each is a self-contained `compat/<modid>` module behind a config flag, so scope can flex without destabilizing the core.

The architecture rule that protects all of it: **optional mods are reached through data, registries, and events wherever possible; through `compileOnly` types only inside supplier-gated `compat/<modid>` classes; and through reflection only when a target has no API at all.** Followed consistently, the mod stays launchable on every subset of this 74-jar pack — and on packs that look nothing like it.
