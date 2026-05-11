# Changelog - Ars 'n' Spells

All notable changes to this project will be documented in this file.

## [1.9.0] — NeoForge 1.21.1 port (2026-05-10, in progress)

Loader, Minecraft, and language version bump from **Forge 1.20.1 / Java 17** to **NeoForge 1.21.1 / Java 21**, against Ars Nouveau 5.11.1 and Iron's Spells 'n Spellbooks 1.21.1-3.15.6. The `mod_version` stays at 1.9.0 — this is the same release, ported, not a feature bump.

The port is staged. Phases 0, 1, 4, and 5 (round-trip test) are in this changeset. Phases 2 (mixin re-verification), 3 (22 `TODO(Phase 11)` gameplay re-attach markers), 6 (full Curios integration), and 7 (verification) are tracked work-in-progress; see [README §Known work in progress](README.md#known-work-in-progress).

### Build, metadata, language

- ForgeGradle replaced with [`net.neoforged.moddev`](build.gradle) 2.0.78. Java toolchain `17` → `21`. Mixin Gradle plugin removed (moddev wires mixin natively). New named runs: `runClient`, `runServer`, `runGameTestServer`, `runData`. Parchment mappings `2024.11.17` for 1.21.1 wired in.
- `gradle.properties` pins `minecraft_version=1.21.1`, `neoforge_version=21.1.84`, Ars Nouveau Curse file `7417840` (5.11.1), Iron's Curse file `7907341` (1.21.1-3.15.6). The optional `sanctified_*` properties exist but stay blank — see Removed below.
- `META-INF/mods.toml` deleted; replaced by [`META-INF/neoforge.mods.toml`](src/main/resources/META-INF/neoforge.mods.toml). Declares NeoForge required, Minecraft `[1.21.1,1.22)`, Ars Nouveau `[5.0.0,)` required, Iron's `[1.21.1-3.0.0,)` optional.
- `pack.mcmeta` updated to `pack_format` 34 (1.21.1 data + resource pack format); legacy `forge:resource_pack_format` / `forge:data_pack_format` keys removed.
- `ars_n_spells.mixins.json` `compatibilityLevel` `JAVA_17` → `JAVA_21`. The `refmap` key is gone — `moddev` handles remapping.

### Loader / bootstrap

- [`ArsNSpells`](src/main/java/com/otectus/arsnspells/ArsNSpells.java) main class no longer uses `FMLJavaModLoadingContext.get()`/`ModLoadingContext.get()` static lookups; the constructor takes `(IEventBus modBus, ModContainer container)` and `container.registerConfig(...)` replaces the old loading-context call. `MinecraftForge.EVENT_BUS` → `NeoForge.EVENT_BUS`. The previous `AttachCapabilitiesEvent`/`RegisterCapabilitiesEvent` listeners are gone (no longer needed; see attachments below).
- Client bootstrap (`ArsNSpellsClient`, `ManaBarController`, `OverlayDiagnostics`) is currently a stub set — overlay registration moves from Forge's `RegisterGuiOverlaysEvent` to NeoForge's `RegisterGuiLayersEvent`. Pending Phase 3.
- Commands moved to `net.neoforged.neoforge.event.RegisterCommandsEvent` with no semantic change.

### Networking

- Forge `SimpleChannel` / `NetworkRegistry` / `NetworkDirection` / `DistExecutor` entirely removed. The three sync packets are replaced by record-based payloads:
  - [`AffinitySyncPayload`](src/main/java/com/otectus/arsnspells/network/AffinitySyncPayload.java)
  - [`CooldownSyncPayload`](src/main/java/com/otectus/arsnspells/network/CooldownSyncPayload.java)
  - [`ResonanceSyncPayload`](src/main/java/com/otectus/arsnspells/network/ResonanceSyncPayload.java)
- Each implements `CustomPacketPayload`, declares a static `Type<>`, a `StreamCodec`, and a `handleOnClient` whose client-only inner class is annotated `@OnlyIn(Dist.CLIENT)` for classload safety on dedicated servers.
- [`PacketHandler`](src/main/java/com/otectus/arsnspells/network/PacketHandler.java) now registers via `RegisterPayloadHandlersEvent` and sends via `PacketDistributor.sendToPlayer`. The Resonance payload is still gated by `ModList.isLoaded("irons_spellbooks")` at registration time, but every payload type lives in its own `ResourceLocation` so server / client mod-set skew no longer corrupts a shared channel.
- `ClientAffinityPacketHandler` deleted — its work moved into `AffinitySyncPayload.ClientHandler`.
- Deleted: `AffinitySyncPacket`, `CooldownSyncPacket`, `ResonanceSyncPacket`.

### Player state — attachments

- [`AttachmentTypes`](src/main/java/com/otectus/arsnspells/data/AttachmentTypes.java) registers three NeoForge attachments: `AFFINITY` (`copyOnDeath`), `PROGRESSION` (`copyOnDeath`), and `COOLDOWN` (intentionally no `copyOnDeath` — cooldowns reset on respawn). All three serialize via a Codec on the data class.
- Deleted: `ModCapabilityProvider`, the `bridge_data` capability `ResourceLocation`, and the `PlayerEvent.Clone` capability copy logic that wrapped them. The data classes themselves ([`AffinityData`](src/main/java/com/otectus/arsnspells/data/AffinityData.java), [`CooldownData`](src/main/java/com/otectus/arsnspells/data/CooldownData.java), [`ProgressionData`](src/main/java/com/otectus/arsnspells/data/ProgressionData.java)) are now plain POJOs.

### Item state — data components

- Cross-cast inscription storage moves from root NBT keys (`arsnspells:cross_spells` list, `arsnspells:cross_spell_index` int) to a single `DataComponentType<CrossModSpellList>` registered as `ars_n_spells:cross_spells` ([`ModDataComponents`](src/main/java/com/otectus/arsnspells/spell/ModDataComponents.java)).
- New record types: [`CrossModSpell`](src/main/java/com/otectus/arsnspells/spell/CrossModSpell.java) (one entry: spell id, level, type, optional Ars CompoundTag, optional Iron's cast source) and [`CrossModSpellList`](src/main/java/com/otectus/arsnspells/spell/CrossModSpellList.java) (immutable list + selected index, with `Codec` and `StreamCodec`). [`CrossModSpellComponents`](src/main/java/com/otectus/arsnspells/spell/CrossModSpellComponents.java) is the helper façade — all `getOrCreateTag` / `setTag(null)` callsites are gone.
- **Breaking persistence change.** Items inscribed under the previous Forge 1.20.1 build will not migrate automatically; their root-NBT entries are silently ignored by the new component reader. The mod is in-development; a migrator is not in scope for this port.
- Deleted: `CrossCastNbt`.

### Resource locations and registries

- Zero `new ResourceLocation(...)` callsites remain. All call paths use `ResourceLocation.fromNamespaceAndPath(...)` (two-arg) or `ResourceLocation.parse(...)` (one-arg).
- `ForgeRegistries` callsites replaced with `BuiltInRegistries` for `ITEM`, `ENCHANTMENT`, `MOB_EFFECT`, `ATTRIBUTE`, `BLOCK` lookups. `RegistryObject<Item>` → `DeferredHolder<Item, ?>` via `DeferredRegister.Items`.
- `AttributeModifier` now takes a `ResourceLocation` as its id (no more `UUID` constructor); the cross-mod progression modifier is rekeyed to `ars_n_spells:cross_mod_school_progression`.

### Mixins

- [`ars_n_spells.mixins.json`](src/main/resources/ars_n_spells.mixins.json): `MixinSanctifiedAbstractSpell` entry removed (see Removed below). Compatibility level `JAVA_21`. The 9 remaining mixins (5 Ars, 4 Iron's) carry `TODO(Phase 11)` markers for target re-verification against the actual 1.21.1 dependency jars — none have been confirmed yet.
- [`ArsNSpellsMixinPlugin`](src/main/java/com/otectus/arsnspells/mixin/ArsNSpellsMixinPlugin.java): Sanctified clause removed. The Iron's-classpath probe remains and gates the 4 Iron's-side mixins.
- Known Phase 2 hazards: `ManaCap` (6 methods), `SpellResolver.expendMana`/`canCast`, `ManaCapEvents.playerOnTick`, `MagicData.getMana/setMana/addMana`, `AbstractSpell.getSpellPower`/`canBeCastedBy`, `Scroll.use`, and `ManaBarOverlay.render` — the last one almost certainly needs a signature rewrite because Forge's `ForgeGui` parameter doesn't exist on NeoForge 1.21.1 (it's `LayeredDraw.Layer` / `GuiLayer`).

### 1.9.0 stabilization handlers, NeoForge port

The five P0 fixes and five finished-system handlers from the Forge 1.20.1 1.9.0 release were re-applied on top of the port skeleton:

- [`AffinityDecayHandler`](src/main/java/com/otectus/arsnspells/events/AffinityDecayHandler.java) — uses NeoForge `PlayerTickEvent.Post`; reads/writes affinity via `player.getData(AttachmentTypes.AFFINITY.get())`; syncs via `AffinitySyncPayload`.
- [`AffinitySyncOnLoginHandler`](src/main/java/com/otectus/arsnspells/events/AffinitySyncOnLoginHandler.java) — NeoForge `PlayerEvent.PlayerLoggedInEvent`, same attachment + payload flow.
- [`ArsSpellScalingHandler`](src/main/java/com/otectus/arsnspells/events/ArsSpellScalingHandler.java) — `LivingHurtEvent` → `LivingDamageEvent.Pre`; uses `event.getNewDamage` / `setNewDamage`.
- [`IronsAffinityHandler`](src/main/java/com/otectus/arsnspells/events/IronsAffinityHandler.java), [`IronsProgressionHandler`](src/main/java/com/otectus/arsnspells/events/IronsProgressionHandler.java) — Iron's `SpellOnCastEvent` listeners; attachment-backed.
- [`ProgressionAttributes`](src/main/java/com/otectus/arsnspells/progression/ProgressionAttributes.java) — `ForgeRegistries.ATTRIBUTES.getValue(...)` replaced with `BuiltInRegistries.ATTRIBUTE.getHolder(...)`; `AttributeModifier` constructed with `ResourceLocation` id.
- New config key: `affinity_decay_interval_ticks` (default 1200, range 20–24000) — preserved on the port.

The bootstrap registrations of these handlers (and their `ModList.isLoaded("irons_spellbooks")` gating) survive on `NeoForge.EVENT_BUS`.

### Resources

- Recipe tag namespace: `forge:logs/archwood` → `c:logs/archwood` (NeoForge common-tag namespace) in both `spell_transcription.json` and `spell_uninscription.json`.
- `spell_transcription.json` is gated with `neoforge:conditions` `mod_loaded irons_spellbooks` so an Ars-only install no longer attempts to load an Iron's-dependent tablet recipe.
- Lang file: keys for `commands.ans.info.cursed_ring`, `commands.ans.info.virtue_ring`, `message.ars_n_spells.lp.*`, `message.ars_n_spells.aura.*`, `message.ars_n_spells.ring_conflict`, and `message.ars_n_spells.lp.insufficient_need` are removed (the features are gone — see below).

### Tests

- The two Forge-era test classes (`CrossCastNbtRoundTripTest`, `InscriptionInputsPredicateTest`) were deleted on the port branch. The first is replaced by [`CrossModSpellListRoundTripTest`](src/test/java/com/otectus/arsnspells/spell/CrossModSpellListRoundTripTest.java) — 7 Bootstrap-free Codec round-trip cases against `CrossModSpellList`. The second is deferred to Phase 3, since `InscriptionInputs.readSource` is itself stubbed pending the Ars / Iron's parchment-source API re-attach.
- Unit tests run via `./gradlew test` and remain Bootstrap-free.

### Removed (Branch B — Sanctified Legacy / Covenant of the Seven absent on NeoForge 1.21.1)

The CurseForge listing for Sanctified Legacy / Covenant of the Seven shipped its most recent file (`sanctified_legacy-2.2.5.jar`, January 2026) still targeting Minecraft 1.20.1 / Forge. No NeoForge 1.21.1 distribution exists at the time of the port. Per the approved port plan (Branch B), the entire Sanctified-coupled feature footprint is **deleted** from the codebase, with a Curios integration planned to replace its curio-feature surface area in Phase 6:

- Deleted classes: `compat/SanctifiedLegacyCompat` (788 lines), `compat/ScrollLPTracker`, `events/CursedRingHandler`, `events/VirtueRingHandler`, `events/LPDeathPrevention`, `events/IronsLPHandler`, `mixin/sanctified/MixinSanctifiedAbstractSpell` (whole `mixin/sanctified/` directory removed).
- Deleted aura system: `aura/AuraCapability`, `aura/AuraCapabilityProvider`, `aura/AuraManager`, `aura/AuraTickHandler`, `aura/IAuraCapability`. Aura was only consumed by the Virtue Ring path — removing the ring removed the only sink.
- Stripped from [`MixinSpellResolverMana`](src/main/java/com/otectus/arsnspells/mixin/ars/MixinSpellResolverMana.java): Cursed Ring / Virtue Ring branches that cancelled `expendMana` when a ring was equipped.
- Stripped from [`ArsNSpellsCommands`](src/main/java/com/otectus/arsnspells/commands/ArsNSpellsCommands.java): `cursed_ring` / `virtue_ring` / `aura` info display in `/ans info`.
- Stripped from [`ArsNSpells`](src/main/java/com/otectus/arsnspells/ArsNSpells.java): `SanctifiedLegacyCompat.init()` / `isAvailable()` calls in the config-loading lifecycle.
- Stripped from lang file: LP / aura / ring-conflict / ring-status keys.
- Dead config keys (`ENABLE_LP_SYSTEM`, `LP_SOURCE_MODE`, `DEATH_ON_INSUFFICIENT_LP`, `SHOW_LP_COST_MESSAGES`, `ARS_LP_*`, `IRONS_LP_*`, `AURA_*`, `VIRTUE_RING_DISCOUNT`, `BLASPHEMY_*`, `HIDE_MANA_BAR_WITH_RING`) remain declared in `AnsConfig` until a cosmetic cleanup pass. No code reads them. They are harmless config-file clutter.

The previous v1.9.0 (Forge 1.20.1) entry below documents the pre-port behavior of these systems and is preserved for historical reference.

---

## [1.9.0] - 2026-05-10

### Bug Fixes (P0 stabilization pass)

- **AffinitySyncPacket no longer crashes dedicated servers.** Pre-1.9.0, [AffinitySyncPacket](src/main/java/com/otectus/arsnspells/network/AffinitySyncPacket.java) imported `net.minecraft.client.Minecraft` directly and was registered unconditionally on the common bus, so the very first cast on a dedicated server attempted to load `Minecraft` server-side and risked a `NoClassDefFoundError`. Client logic moved to a new [`ClientAffinityPacketHandler`](src/main/java/com/otectus/arsnspells/client/ClientAffinityPacketHandler.java) and the packet now wraps the client-side capability mutation in `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, …)` — the same pattern `ResonanceSyncPacket` was already using.
- **Iron's Spellbooks is now actually optional.** [`IronsLPHandler`](src/main/java/com/otectus/arsnspells/events/IronsLPHandler.java) was an unconditional `@Mod.EventBusSubscriber` while importing `io.redspace.ironsspellbooks.api.*` at the file header — Iron's-less servers crashed at classload. The annotation is removed and the handler is now instance-registered behind the existing `ModList.get().isLoaded("irons_spellbooks")` block in [`ArsNSpells`](src/main/java/com/otectus/arsnspells/ArsNSpells.java). [`SpellScalingUtil`](src/main/java/com/otectus/arsnspells/util/SpellScalingUtil.java)'s static initializer (which referenced Iron's `AttributeRegistry.*_SPELL_POWER` slots) is converted to lazy double-checked init so the map only builds when an Iron's-aware caller actually invokes it. New [`IronsCompat`](src/main/java/com/otectus/arsnspells/compat/IronsCompat.java) exposes a cached `isLoaded()` for the cast hot-path.
- **Scroll LP handling is now a real transaction.** [`MixinScrollItem`](src/main/java/com/otectus/arsnspells/mixin/irons/MixinScrollItem.java) used to consume LP at scroll-use time, ignore the consumption return value, and silently leak the death-on-insufficient-LP path because [`LPDeathPrevention`](src/main/java/com/otectus/arsnspells/events/LPDeathPrevention.java) early-returned when the death-mode config was on. The mixin is rewritten as **validate → cast → commit**: HEAD calls `hasEnoughLP`, stages a per-player pending entry via the new [`ScrollLPTracker`](src/main/java/com/otectus/arsnspells/compat/ScrollLPTracker.java) helper, and either cancels (safe mode) or lets the scroll proceed (death mode). RETURN reads the original `use` result and either consumes LP (success), kills the player explicitly (death mode), or no-ops (cast didn't actually consume the action). LP no longer disappears for failed casts, and the death-mode path now does its own enforcement instead of relying on a subsystem that exited early.
- **Cooldown "namespacing" was a lie — now removed.** [`UnifiedCooldownManager`](src/main/java/com/otectus/arsnspells/cooldown/UnifiedCooldownManager.java) accepted a `String modNamespace` argument that suggested per-mod isolation, but the storage was always `Map<CooldownCategory, Long>` and the namespace only ever appeared in debug logs. The parameter is dropped from every public method, both callers ([`CooldownHandler`](src/main/java/com/otectus/arsnspells/events/CooldownHandler.java) and [`IronsCooldownHandler`](src/main/java/com/otectus/arsnspells/events/IronsCooldownHandler.java)) updated, and the README "Cooldowns" section now states clearly that the unified system is global per category — an Ars `OFFENSIVE` cast and an Iron's `OFFENSIVE` cast intentionally collide. NBT and packet wire formats are unchanged, so existing 1.8.9 saves load cleanly with no migration.

### Finished half-wired systems

The pre-1.9.0 README claimed cross-mod progression "and vice versa", "Ars spell potency scales with Iron's spell power attributes", and optional affinity decay. None of those were actually wired. They are now:

- **Iron's-side progression hook.** New [`IronsProgressionHandler`](src/main/java/com/otectus/arsnspells/events/IronsProgressionHandler.java) listens to Iron's `SpellOnCastEvent`, derives the school from the path component of the school's resource location, and calls into the same `ProgressionData.incrementCastCount` + `<school>_spell_power` attribute application that the Ars-side handler uses. The shared logic lives in [`ProgressionAttributes`](src/main/java/com/otectus/arsnspells/progression/ProgressionAttributes.java) so both sides use the same modifier UUID and naming.
- **Iron's-side affinity hook.** New [`IronsAffinityHandler`](src/main/java/com/otectus/arsnspells/events/IronsAffinityHandler.java) mirrors `AffinityHandler` for Iron's casts. The [`AffinityType`](src/main/java/com/otectus/arsnspells/affinity/AffinityType.java) enum gains `HOLY`, `ENDER`, `BLOOD`, `EVOCATION`, `ELDRITCH` so every Iron's stock school maps onto an entry. Adding enum values is forward and backward compatible — pre-1.9.0 NBT loads cleanly with the new entries defaulting to 0.
- **Ars spell scaling actually wired.** New [`ArsSpellScalingHandler`](src/main/java/com/otectus/arsnspells/events/ArsSpellScalingHandler.java) computes `SpellScalingUtil.getMultiplierForCaster` on each Ars `SpellCastEvent`, stages it for the casting player with a 60-tick window, and applies it on `LivingHurtEvent` for spell-flavored damage from that player. Final amount is clamped against `spell_power_cap`. Filter rejects melee/environmental damage so the bonus only flows to actual spell hits.
- **Affinity decay tick handler.** New [`AffinityDecayHandler`](src/main/java/com/otectus/arsnspells/events/AffinityDecayHandler.java) ticks each player every `affinity_decay_interval_ticks` (default 1200 = 60 s) and prorates the existing `affinity_decay_rate` from per-day to per-interval (24000 ticks per Minecraft day). Default for `enable_affinity_decay` is now `false` for new configs to avoid surprising existing players whose 1.8.9 config had it (no-op-ly) on; existing config files retain their previous setting.
- **Login affinity sync.** New [`AffinitySyncOnLoginHandler`](src/main/java/com/otectus/arsnspells/events/AffinitySyncOnLoginHandler.java) fires one `AffinitySyncPacket` per non-zero school when the player joins, so HUD and tooltips reflect persisted state immediately instead of waiting for the next cast. Progression already auto-applies attribute modifiers on login so it doesn't need a packet sweep.
- [`AffinityCalculator`](src/main/java/com/otectus/arsnspells/affinity/AffinityCalculator.java) and [`AffinityBonuses`](src/main/java/com/otectus/arsnspells/affinity/AffinityBonuses.java) — the per-level damage curve now lives in `AffinityCalculator.getDamageBonus` and is consumed by `AffinityBonuses.getAttributeMultiplier`. Same numbers as before; less duplication; no more dead code.

### Configuration

- **New: `affinity_decay_interval_ticks`** (default 1200, range 20–24000) — how often the new decay handler ticks each player.
- **Changed default: `enable_affinity_decay`** is now `false` for fresh configs (was effectively a no-op `true` in 1.8.9). Existing configs preserve whatever value the user already had.

### Internal

- **Mixin-package isolation respected.** The transactional scroll-cost state ([`ScrollLPTracker`](src/main/java/com/otectus/arsnspells/compat/ScrollLPTracker.java)) lives in the `compat` package, not as an inner class of `MixinScrollItem`. Sponge Mixin treats every class inside a mixin package — including a mixin's own inner classes — as off-limits for direct reference, because the mixin class gets merged into its target at load time and stops existing as a standalone class. The first 1.9.0 build paid for that rule with an `IllegalClassLoadError` on `MixinScrollItem$PendingScrollLP`; extracting the holder fixed it without behavior change. Worth remembering for any future mixin that needs shared state.

### Known follow-ups (deferred to 1.10.0+)

- Aura HUD does not auto-update server-side regen — still updates only on next spell cast or login. Aura sync is queued for 1.10.0.
- `SERVER` / `CLIENT` config split. All keys remain on the single `COMMON` config in 1.9.0.
- Datapack registries for spell schools, cooldown categories, progression rules, cross-cast rules.
- Cross-cast NBT re-validation at cast time (server-trust hardening).
- Capability sync on dimension change / respawn (only login is in scope this round).

### Backward compatibility

Strict. AffinityData, ProgressionData, CooldownData, AuraCapability NBT shapes unchanged. Network protocol stays at "1". Inscribed cross-cast items unaffected. No removed config keys, no renamed config keys.

---

## [1.8.9] - 2026-04-25

### New Features
- **Cross-Spell Inscription is now reachable in survival.** The Spell Transcription ritual has been functional in code since v1.8.3, but the ritual tablet that activates it had no acquisition path: Ars Nouveau iterates `RitualRegistry.getRitualMap()` during its own item `RegisterEvent` and produces a `RitualTablet` per ritual, but that loop runs before Ars 'n' Spells's common-setup registration so our ritual was never picked up. The mod now owns its tablet items: a new `ModItemsRegistry.ITEMS` (`DeferredRegister<Item>`) registers the Spell Transcription tablet (when Iron's Spellbooks is loaded) and the new Spell Uninscription tablet (always), and `RitualRegistryHandler` splices them into Ars's `ritualItemMap` at common setup so brazier and JEI lookups by `ResourceLocation` resolve.
- **Apparatus recipe gates the Spell Transcription tablet.** Datapack recipe at [data/ars_n_spells/recipes/apparatus/spell_transcription.json](src/main/resources/data/ars_n_spells/recipes/apparatus/spell_transcription.json): novice Ars spellbook reagent + Iron's spellbook + archwood log + source gem block on pedestals, 2000 source. The recipe loader silently drops the recipe when Iron's isn't installed, matching the tablet-registration gate.
- **Spell Transcription rewritten with strict disambiguation.** Validation now runs to completion before any item mutation. The new `InscriptionInputs` classifier sorts dropped items in the three-block work area into three disjoint buckets (sources, inscribed, blank-target candidates); each violation produces a lang-keyed message naming the offending items and the rule. Distinct paths for empty range, no source, multiple sources (counted and listed), no target, multiple targets (counted and listed), already-inscribed items in range ("uninscribe first"), Ars-rooted target (decision 5 defensive guard against right-click handler shadowing), and source re-parse failure.
- **Spell-to-spell direct copy is intentionally not auto-resolved** -- the user must uninscribe first. Predictable beats clever; a "smart" fallback that picked one direction would silently corrupt the wrong item.
- **New Spell Uninscription ritual.** Mirrors the transcribe flow with stricter intake (no sources or blanks in range, exactly one inscribed item). Strips both the cross-spell list and the cycle index and collapses an empty residual root tag to null, so the result is bit-identical to a fresh blank target and the same item can be re-inscribed cleanly. Iron's-independent: the tablet and ritual register without Iron's loaded so a player can still clean up legacy inscribed items after Iron's is removed. Apparatus recipe at [data/ars_n_spells/recipes/apparatus/spell_uninscription.json](src/main/resources/data/ars_n_spells/recipes/apparatus/spell_uninscription.json): blank parchment reagent + water bucket + source gem + archwood log, 500 source. Cheaper than transcribe by design -- uninscribing is a fix, not a feat.
- **Cross-cast cost multiplier.** New `cross_cast_cost_multiplier` config (default `1.25`, range `0.5`-`5.0`) charges a flat overhead on cross-cast spells. Applies symmetrically to Iron's spells cast from non-Iron's items and Ars spells cast from non-Ars items, exactly once per cast, after base cost calculation and before mana deduction. Routed through `BridgeManager` so it composes with the active mana mode and the SEPARATE-mode dual-cost split. Iron's-side: SEPARATE-mode multiplies the base before splitting; non-SEPARATE multiplies `event.getManaCost()` in the handler and clears the entry. Ars-side: a new `Entry.multiplierApplied` one-shot guard prevents double-application across multiple `SpellCostCalcEvent` fires per resolve. Direct casts (no cross-cast context entry) are never touched.
- **Theming.** Inscribe plays enchantment-glyph particles (`ParticleTypes.ENCHANT`) + the enchantment-table sound; uninscribe plays ash + smoke + a fire-extinguish sound.

### Configuration
- **New: `cross_cast_cost_multiplier`** (default `1.25`, range `0.5`-`5.0`) -- multiplier on cross-cast spell base mana cost.
- **New: `enable_per_cast_reagent`** (default `false`) -- reserved hook for a future per-cast reagent system. No-op today; documented so future work can be gated without re-shuffling config keys.

### Internal
- **`CrossCastNbt`** -- new pure helper extracted from `CrossCastingHandler`. NBT-key constants and the inscribe/clear/has-cross-spells operations live here, with no dependency on Minecraft `Bootstrap`. `CrossCastingHandler` delegates its add and clear methods to the helper; the on-disk shape is unchanged.
- **`InscriptionInputs` / `InscriptionSource` / `RitualFeedback`** -- new files in the rituals package. `InscriptionInputs.classify(...)` is the shared classifier consumed by both transcribe and uninscribe rituals; `RitualFeedback.error/success` centralizes the chat-message styling and nearest-player lookup so the two rituals stay in lockstep.
- **JUnit 5 test harness.** `testImplementation` JUnit 5.10.2 in [build.gradle](build.gradle); `useJUnitPlatform()`. Two test classes:
  - `CrossCastNbtRoundTripTest` (5 cases) -- empty-baseline round-trip, unrelated-NBT preservation, inscribe→uninscribe→inscribe identity, cycle-index strip, multi-inscription bulk clear.
  - `InscriptionInputsPredicateTest` (7 cases) -- empty tag, null tag, empty list shape, inscribed tag, unrelated root NBT, conflicting non-root NBT (must pass per spec), wrong tag type at the cross-spells key.
  - All 12 tests green under `./gradlew test`.

### Documentation
- README's "Cross-mod spell casting" section rewritten end-to-end to walk through the new survival flow: tablet crafting, strict source/target rules, cost multiplier, and the uninscribe ritual.

---

## [1.8.8] - 2026-04-24

### Bug Fixes
- **Cross-system mana regen unit mismatch fixed** -- Iron's `MANA_REGEN` is a percentage-of-pool multiplier (`max × regen × 0.01` mana/sec); Ars Nouveau regen is absolute mana/sec. Three callsites previously wrote a value from one system directly into the other without converting units, so an Ars enchantment like Mana Regen III on wizard armor compounded into hundreds of mana/sec instead of the intended single-digit boost. All cross-system regen translations now route through a new `ManaRegenBridge` that converts via the wearer's current max pool.
  - `ArsManaCalcHandler.onManaRegenCalc` (ARS_PRIMARY mode) -- Iron's gear regen attribute is now converted to mana/sec before being added to the Ars regen event.
  - `EquipmentIntegration.applyArsBonusesToIrons` (ISS_PRIMARY/HYBRID mode) -- Ars-side regen bonuses are now converted to the equivalent Iron's `MANA_REGEN` attribute delta. Max mana modifier is applied first so the regen conversion sees the post-bonus pool.
  - `MixinArsPotionEffects.arsnspells$redirectManaRegenPotions` -- Ars `mana_regen` / `mana_boost` potion effects redirected to Iron's `MANA_REGEN` now go through the bridge.

### Configuration
- **New: `cross_system_regen_conversion`** (default `EQUAL_EFFECT`) -- Conversion strategy. `EQUAL_EFFECT` preserves equivalent mana/sec on both sides at any pool size; `REFERENCE_POOL` uses a fixed pool for predictable conversions; `DISABLED` blocks cross-system regen translation entirely (Mana Regen enchantments only affect their own system).
- **New: `cross_system_regen_multiplier`** (default `1.0`) -- Global dampener for every cross-system regen translation.
- **New: `cross_system_regen_reference_pool`** (default `100.0`) -- Reference pool size for `REFERENCE_POOL` mode.

### Cleanup
- **Tightened enchantment mana detection** -- `EquipmentIntegration.getEnchantmentManaBonus` previously string-matched any enchantment whose description ID contained `"mana"` or `"source"` and granted +50 max mana per level, which silently caught unrelated enchantments (e.g. anything named `mana_steal`, `source_friendly`). Detection is now anchored to the specific Ars Nouveau enchantment IDs `ars_nouveau:mana_regen` and `ars_nouveau:mana_boost` via `ForgeRegistries`. The heuristic remains load-bearing in `ISS_PRIMARY`/`HYBRID` mode where `MixinManaCapability` suppresses Ars's native regen tick, so this is the only path that surfaces those enchantments to the Iron's pool.

---

## [1.8.6] - 2026-04-24

### Bug Fixes
- **Ring of Seven Curses / Virtues now hides the mana bar** -- While either ring is equipped, spells consume LP or Aura instead of mana, so the Iron's Spellbooks and Ars Nouveau mana bars are now hidden on the HUD. New config `hide_mana_bar_with_ring` (default `true`) gates the behavior, and the check runs independently of mana unification so it applies in every mode.
- **Cursed Ring now recognized from both namespaces** -- Detection matches `enigmaticlegacy:cursed_ring` and `covenant_of_the_seven:cursed_ring`; previously only the Enigmatic Legacy variant was honored. Virtue Ring detection uses an equivalent set for defensive future-proofing.
- **Thread-safety fix for LP/Aura pending-cost tracking** -- `CursedRingHandler`, `IronsLPHandler`, and `VirtueRingHandler` now store pending-cost state in `ConcurrentHashMap` instead of `HashMap`. Prevents rare `ConcurrentModificationException` or lost entries when spell events fire on different threads than the tick sweep.
- **Per-player state now evicted on logout** -- Added `PlayerLoggedOutEvent` handlers to all three ring/LP handlers so stale UUID entries are removed immediately instead of waiting for the 5-second TTL sweep. The Cursed handler also clears the curio-state cache on logout.
- **Defensive guards on LP accounting** -- `hasEnoughLP` and `consumeLP` now refuse non-positive costs instead of silently "succeeding" with a free cast. `consumeLP` also clamps post-cast health at 1 HP so floating-point drift cannot violate the reserved buffer.
- **Null-guard on Blood Magic Soul Network lookup** -- `getBloodMagicLP` and `consumeBloodMagicLP` now check for a null Soul Network return value and log a debug message instead of relying on a catch-all `Exception` to mask a potential NPE across Blood Magic version drift.

### Performance
- **Curio scan cached per player** -- Ring and Blasphemy detection (`isWearingCursedRing`, `isWearingVirtueRing`, `hasBothRings`, `hasAnyBlasphemy`, `hasMatchingBlasphemy`, `hasBlasphemyType`, `hasVirtueRing`) previously iterated every curio slot on every spell cast — up to 5–6 scans per Ars cast between the Cursed Ring, Virtue Ring, and Blasphemy-discount paths. A new 20-tick (~1-second) TTL `CurioState` cache in `SanctifiedLegacyCompat` collapses all checks onto a single inventory scan per player per second.
- **Pre-computed ring ResourceLocations** -- Removed per-call `new ResourceLocation(...)` allocations; ring IDs are now static final `Set<ResourceLocation>` fields built once.

### Cleanup
- **Removed dead `hasCurio` helper** -- All call sites moved to the cached state lookup; the generic helper had no remaining callers.

---

## [1.8.3] - 2026-04-14

### New Feature
- **Spell Transcription ritual is now functional** -- Previously a no-op that dropped a blank scroll. The ritual now inscribes a cross-mod spell onto a target item, exposing the previously API-only cross-casting runtime to survival play. Drop a source (filled Ars spell parchment/focus/spellbook, or an Iron's scroll) and a target item near the brazier, activate the ritual, and the target gains the `arsnspells:cross_spells` NBT tag. Right-click the target to cast the inscribed spell; sneak-right-click cycles through multiple inscriptions. Mana costs flow through `BridgeManager` and respect the active unification mode, including `SEPARATE`-mode dual-cost splitting.

### Cleanup
- **Removed dead `ProgressionSyncPacket`** -- The packet was registered on the network channel but never constructed or sent anywhere. Its packet-id slot is freed.
- **Removed deprecated `XpConverter`** -- `@Deprecated` stub that redirected to `SpellAnalysis`; had no remaining callers.

### Bug Fixes
- **`AuraCapability` now tolerates early capability attach** -- Constructor and `loadNBTData` wrap `AnsConfig.AURA_MAX_DEFAULT.get()` in try/catch, defaulting to 100 if the config spec isn't loaded yet (defensive; pre-existing crash had not been observed in practice).

### Documentation
- **Fixed `CLAUDE.md` Ars Nouveau version range drift** -- Now shows `[4.12.7, 4.13)` matching `mods.toml` instead of a misleading `4.12.7+`.

---

## [1.8.2] - 2026-04-08

### Bug Fix
- **Fixed Ars armor still not increasing mana in ARS_PRIMARY mode** -- The v1.8.1 fix only applied item-level armor bonuses to Iron's MAX_MANA attribute, but Ars 4.12.7 applies bonuses via the perk system (player attributes, not item modifiers), so the scanned bonus was often zero. Iron's mana tick then clamped Ars mana to Iron's base max (~200). Now syncs Iron's MAX_MANA to Ars's actual calculated max mana instead of scanning items, with a secondary sync on every `MaxManaCalcEvent` to catch level-ups, perk changes, and glyph learning

---

## [1.8.1] - 2026-03-29

### Bug Fix
- **Fixed Ars Nouveau armor mana bonuses not applying in ARS_PRIMARY mode** -- Iron's internal regen was clamping the shared mana pool to Iron's base max because Ars armor bonuses were cleared from `AttributeRegistry.MAX_MANA` in ARS_PRIMARY. The equipment handler now keeps Iron's max mana attribute aligned with Ars's max in all shared-pool modes (ISS_PRIMARY, HYBRID, and ARS_PRIMARY)

---

## [1.8.0] - 2026-03-27

### Upstream Compatibility Overhaul
This release brings full compatibility with **Ars Nouveau 4.12.7** and **Iron's Spellbooks 3.15.5.1**.

### Build System
- **Replaced local jar dependencies with CurseMaven** -- Clean checkouts now compile without manual jar placement; dependencies resolve automatically from CurseForge
- **Updated Iron's Spellbooks dependency** from 3.15.2 to 3.15.5.1
- **Widened Ars Nouveau version range** from exact `[4.12.7]` to `[4.12.7,4.13)` to accept compatible patch releases

### Critical Fixes
- **Fixed fundamentally wrong spell classification across the entire mod** -- All systems (cooldowns, progression, affinity, LP costing, scaling, discounts) were reading `recipe.get(0)` which returns the cast method (projectile/touch/self), not the actual effect glyph. New central `SpellAnalysis` utility correctly walks recipes, skips cast methods and augments, and identifies the first effect glyph
- **Removed two broken Ars mixins** -- `MixinArsManaHud` (targeted removed `render` method) and `MixinSpellStatsPotency` (targeted removed `getPotency` method) were silently dead against Ars 4.12.7
- **Removed `MixinArsManaRegen`** that cancelled the entire `ManaCapEvents.playerOnTick` handler -- This broke capability state maintenance and sync. Regen suppression in ISS_PRIMARY/HYBRID mode is already handled by `MixinManaCapability.addMana`
- **Fixed all three rituals doing nothing on completion** -- `onFinishing(Player)` and `onRitualFinished(Player)` were dead methods that nothing called; migrated to the current Ars 4.12.7 `onEnd()` lifecycle hook
- **Fixed ritual double-registration on config reload** -- Added guard and moved registration to `commonSetup`

### LP Death Prevention Rework
- **Replaced broad temporary magic immunity with scoped cast transactions** -- Previously, `setLPImmune` blocked ALL magic/sacrifice damage for up to 3 seconds, creating an invulnerability exploit. Now only blocks damage in the same tick as the LP cast
- **Immunity cleared immediately** instead of deferred to next tick
- **Safety timeout reduced** from 3 seconds to 1 second

### Mana Bridge Fixes
- **Fixed BridgeManager stale state** -- `getCurrentMode()` was reading config live but bridges were built once at init, creating impossible mixed states. Mode is now cached at init; changing `mana_unification_mode` requires a restart
- **Fixed RegenSynergyHandler ignoring mana mode** -- Source Jar synergy was always writing to Iron's `MagicData` regardless of mode. Now routes through `BridgeManager` to write to the correct pool
- **Consolidated overlay handling** -- Removed redundant `OverlayRegistrationHandler` and dead `OverlayManager`; `ManaBarController` is now the sole overlay controller
- **Fixed MixinScrollItem mixin warning** -- Switched from `targets` to `value` for the public `Scroll` class

### Spell Scaling Improvements
- **Added `nature` and `eldritch` schools** to `SpellScalingUtil` element map (were missing from Iron's 3.15.x)
- **Wired affinity bonuses into spell scaling** -- High affinity in a school now provides a spell power bonus (0.5% per level, up to 50%)
- **Wired resonance multiplier into spell scaling** -- `ResonanceManager` values now actually affect spell power

### Progression System Overhaul
- **Progression is now a real persistent capability** -- Per-school cast counts stored in `ProgressionData` via NBT, persisted across death and dimension changes
- **Attribute bonuses are now transient** instead of permanent -- Recalculated from stored data on login; no more permanent stat drift
- **Created working `ProgressionSyncPacket`** -- Replaces the old no-op stub that did nothing

### Affinity System Fixes
- **Added `setLevel()` with `[0,100]` clamping** -- Previously only `addLevel` existed with upper-only clamping, allowing desync to negative values
- **Switched to full-state sync** -- Client now receives the absolute level instead of a delta, preventing desync

### ManaWell Ritual Config
- **Added dedicated `mana_well_range`** (default 8) -- Previously hijacked `cooldown_category_duration` divided by 10
- **Added `mana_well_regen_rate`** (default 2.0) -- Previously hardcoded to 2.0f

### Cleanup
- **Gated StartupValidator file I/O behind debug mode** -- Write tests and lock probes only run when `debug_mode` is enabled; mod-presence and Java-version checks remain always-on
- **Deprecated `XpConverter.mapGlyphToSchool()`** -- Use `SpellAnalysis.analyze(spell).dominantSchool()` instead
- **Deprecated `SpellCategorizer.categorizeArsGlyph()`** -- Use `SpellAnalysis.analyze(spell).category()` instead
- **Removed 8 dead code files** -- MixinArsManaHud, MixinSpellStatsPotency, MixinArsManaRegen, OverlayManager, OverlayRegistrationHandler, old ProgressionSyncPacket, ArsSpellCastHandler, IronsSpellCastHandler

### New Config Options
| Option | Default | Description |
|--------|---------|-------------|
| `mana_well_range` | `8` | Radius in blocks for Mana Well ritual effect |
| `mana_well_regen_rate` | `2.0` | Mana per tick granted within Mana Well range |

---

## [1.7.0] - 2026-03-26

### Critical Fixes
- **Fixed SEPARATE mode dual-cost losing mana on partial failure** -- If Iron's consumption fails after Ars succeeds, Ars mana is now rolled back to its pre-consumption value instead of being silently lost
- **Fixed `applySilentHealthLoss` killing the player in safe mode** -- Health floor changed from 0.0 to 1.0, preventing unintended death when LP is insufficient and death penalty is disabled
- **Fixed hardcoded Blasphemy LP minimum ignoring config** -- Blasphemy-discounted LP costs now respect the `ars_lp_minimum_cost` config instead of a hardcoded 100

### High-Priority Fixes
- **Removed `Thread.sleep(100)` from mod loading thread** -- Ritual and compat initialization now deferred to `ModConfigEvent.Loading` instead of blocking the FML work queue
- **Switched pending cost TTL to game ticks** -- VirtueRingHandler and CursedRingHandler no longer use wall-clock time; costs expire after 100 game ticks (5 seconds at 20 TPS) instead of 5000ms, preventing free casts under server lag
- **Fixed double event firing** -- Removed 8 redundant explicit `.register()` calls for handlers already auto-registered via `@Mod.EventBusSubscriber`

### Bug Fixes
- **Fixed MixinManaCapability.setMana API contract violation** -- `setMana()` now returns the requested `amount` instead of Iron's internal mana value
- **Fixed ResonanceManager memory leak** -- Cache now cleans up offline players every 60 seconds and clears all entries on server stop
- **Fixed potion effect detection using fragile string matching** -- Ars potion effects are now detected by registry `ResourceLocation` instead of matching substrings in description keys
- **Fixed SLF4J format string bug** -- `{:.1f}` (Python-style) replaced with proper `String.format("%.1f", value)` in EquipmentIntegration
- **Fixed aura regen float precision drift** -- Accumulator now uses modulo instead of subtraction to prevent IEEE 754 errors over long sessions
- **Fixed cooldown namespace keys being constructed but never used** -- Removed dead `namespacedKey` variable assignments from UnifiedCooldownManager
- **Fixed AffinityData lost on death** -- Added `PlayerEvent.Clone` handler to persist affinity levels across death/respawn
- **Fixed CrossCastContext entries never cleaned up on disconnect** -- Added logout handler to clear stale entries

### Balance Changes
- **Spell scaling changed from multiplicative to additive** -- Iron's base spell power and elemental spell power bonuses are now added instead of multiplied, preventing exponential stacking. New configurable `spell_power_cap` (default 3.0) provides a hard limit
- **Blasphemy discounts now independently configurable per resource type** -- New `blasphemy_lp_discount` and `blasphemy_aura_discount` configs (default 0.85) replace the old hardcoded 85% reduction, separate from the mana discount
- **Source Jar regen synergy buffed** -- New `source_jar_synergy_multiplier` (default 5.0) makes the proximity bonus meaningful (5 mana/sec instead of 1)
- **Ritual of Mana Infusion now configurable** -- New `ritual_mana_infusion_amount` config (default 500) replaces the hardcoded value

### New Features
- **Ring conflict notification** -- Players wearing both Cursed Ring and Virtue Ring now receive a one-time action bar warning that the rings cancel each other
- **New commands:**
  - `/ans debug` -- Toggle debug mode at runtime (op 2)
  - `/ans info <player>` -- Display player's mana, aura, resonance, and ring status (op 2)
  - `/ans mode` -- Show the current mana unification mode

### UX Improvements
- **Expanded language file** from 8 to 28 translation entries
- **Replaced hardcoded formatting codes** -- LP and aura messages now use `Component.translatable()` with `ChatFormatting` constants instead of raw `\u00a7` section signs, enabling proper i18n support

### Code Quality
- **Standardized logging** -- All files now use SLF4J; removed mixed Log4j usage
- **Removed emoji from log messages** -- Replaced with text equivalents for cross-platform console compatibility
- **Removed 11 dead code files** -- AffinityTracker, CooldownManager facade, BridgeHealthCheck, ConfigCache, ModLogger, FeatureManager, 4 unused addon compat classes, and duplicate ProgressionData stub

### Performance
- **Source Jar block scan now cached by position** -- Scans only when player moves >4 blocks from last scan position; stationary players skip all 324-block scans
- **`hasAnyBlasphemy` reduced from 13 inventory scans to 1** -- Single curio slot iteration with `HashSet.contains()` lookup instead of 13 separate `hasCurio()` calls

### New Config Options
| Option | Default | Description |
|--------|---------|-------------|
| `spell_power_cap` | `3.0` | Maximum total spell power multiplier |
| `blasphemy_lp_discount` | `0.85` | LP cost discount from matching Blasphemy |
| `blasphemy_aura_discount` | `0.85` | Aura cost discount from matching Blasphemy |
| `source_jar_synergy_multiplier` | `5.0` | Source Jar proximity regen multiplier |
| `ritual_mana_infusion_amount` | `500.0` | Mana added by Ritual of Mana Infusion |
| `source_jar_cache_move_threshold` | `4.0` | Distance before re-scanning for Source Jars |

---

## [1.6.0] - 2026-03-16

### Fixed - Critical Mana Reset Bug
- **Fixed mana being permanently stuck at 1/100 with no regeneration** in ISS_PRIMARY mode
  - Root cause: ManaCap write intercepts (`setMana`, `addMana`, `removeMana`) were forwarding Ars-internal stale values (typically 0) to Iron's MagicData, overwriting Iron's actual mana every tick
  - Fix: Changed write intercepts to read-only shadow sync — ManaCap now reads Iron's current value without writing back to it; spell consumption still works via the separate `expendMana` path through `BridgeManager`
- **Fixed StackOverflowError crash in ARS_PRIMARY mode**
  - Root cause: `getCurrentMana()` mixin called `ArsNativeBridge.getMana()` which called `cap.getCurrentMana()`, re-entering the mixin in an infinite loop
  - Fix: Added `ThreadLocal` recursion guard and explicit ARS_PRIMARY early-exit (Ars is source of truth in that mode, no bridge needed)
- **Fixed mana potions and armor buffs snapping back immediately**
  - Caused by the same write redirect bug — potion/armor effects set correct mana, then the next Ars-internal `setMana(0)` overwrote it
- **Fixed "both bars empty" when disabling mana unification**
  - Root cause 1: `MixinSpellResolverPreCast` unconditionally overrode Ars's native `canCast()` even in DISABLED mode
  - Root cause 2: `MixinArsManaRegen` only checked mode (cached from startup), not `isUnificationEnabled()`
  - Fix: Added `isUnificationEnabled()` guard to both mixins; native Ars behavior now fully restored when unification is off
- **Fixed config mode changes requiring a full restart**
  - `BridgeManager.getCurrentMode()` now reads directly from config instead of returning a stale cached value
- **Fixed client-side mana display artifacts**
  - ManaCap read intercepts now only run on server side; client uses native values synced by each mod independently

### Changed
- `MixinManaCapability`: Rewrote all 6 method intercepts with proper guards and read-only semantics
- `MixinArsManaRegen`: Added `isUnificationEnabled()` check before suppressing Ars regen
- `MixinSpellResolverPreCast`: Added early return when unification disabled and no Sanctified rings active
- `BridgeManager.getCurrentMode()`: Now always reads from `AnsConfig.getManaMode()` for runtime config responsiveness

### Technical Details
- ManaCap `setMana`/`addMana`/`removeMana` no longer write to Iron's MagicData in any mode
- Spell mana consumption path is unaffected: `MixinSpellResolverMana` → `BridgeManager.consumeManaForMode()` → `IronsBridge.consumeMana()` (bypasses ManaCap entirely)
- `ThreadLocal<Boolean>` recursion guard prevents re-entrant bridge calls in all modes
- All mixin intercepts now check `player.level().isClientSide()` and skip client-side execution

---

## [1.2.0] - 2026-02-02

### Added - Covenant of the Seven Integration

#### Ring of Virtue & Blasphemy Curio Discounts
- **Ring of the Seven Virtues Support**
  - Provides 20% mana cost reduction for all Ars Nouveau spells (configurable)
  - Automatically detected when equipped in curio slot
  - Stacks multiplicatively with Blasphemy discounts (if enabled)

- **Blasphemy Curio Support (All 13 Variants)**
  - Base 15% mana discount for all Ars Nouveau spells
  - Additional 10% bonus when Blasphemy school matches spell school (25% total)
  - Supported variants:
    - Fire, Ice, Lightning, Holy, Ender, Blood, Evocation, Nature, Eldritch, Aqua, Geo, Wind, Dormant
  - School-specific matching with intelligent keyword detection
  - Configurable base discount and matching bonus

- **Discount Stacking System**
  - Multiplicative stacking: Ring of Virtue + Blasphemy = 32-40% total discount
  - Maximum discount with matching school: 40%
  - Configurable stacking behavior (can be disabled)

#### Cursed Ring LP Consumption System
- **Ars Nouveau Spell LP Costs**
  - Spells consume Life Points (LP) from Blood Magic instead of mana
  - Configurable LP formula: `LP = (Mana × Base) × Tier Multiplier`
  - Separate multipliers for Tier 1, 2, and 3 glyphs
  - Minimum LP cost enforcement
  - Spell effects apply correctly (fixed visual-only bug)
  - Blasphemy discounts apply to LP costs (85% for matching schools)

- **Iron's Spellbooks Spell LP Costs**
  - Enhanced integration with Sanctified Legacy's native LP system
  - Configurable LP formula: `LP = (Mana × Base) × (1 + Level × LevelMult) × RarityMult`
  - Separate multipliers for each rarity tier (Common through Legendary)
  - LP cost messages now displayed for Iron's spells
  - Insufficient LP messages shown consistently

- **Death Penalty System**
  - **Safe Mode** (default): Spell cancelled, 1 heart damage, player survives
  - **Death Mode**: Spell casts, player dies instantly
  - Configurable via `death_on_insufficient_lp` setting
  - Triple-layer death prevention:
    - Layer 1: Damage interception (LivingHurtEvent)
    - Layer 2: Damage application (LivingDamageEvent)
    - Layer 3: Death event cancellation (LivingDeathEvent)
  - Handles multiple death events from Blood Magic
  - Intercepts "sacrifice" damage type
  - 2-second window for death prevention

- **LP Cost Messages**
  - Shows "Consumed XXX LP" on successful casts
  - Shows "Insufficient LP - Spell Cancelled" on failures
  - Configurable via `show_lp_cost_messages` setting
  - Consistent messaging for both Ars and Iron's spells

### Added - Configuration Options

#### Curio Discount System (5 new options)
- `enable_curio_discounts` - Master toggle (default: true)
- `virtue_ring_discount` - Ring of Virtue discount percentage (default: 0.2)
- `blasphemy_discount` - Blasphemy base discount (default: 0.15)
- `blasphemy_matching_school_bonus` - Matching school bonus (default: 0.1)
- `allow_discount_stacking` - Enable discount stacking (default: true)

#### Cursed Ring LP System (2 new options)
- `death_on_insufficient_lp` - Death penalty toggle (default: false)
- `show_lp_cost_messages` - Show LP cost messages (default: true)

#### LP Calculation - Ars Nouveau (5 new options)
- `ars_lp_base_multiplier` - Base LP conversion (default: 10.0)
- `ars_lp_tier1_multiplier` - Tier 1 multiplier (default: 1.5)
- `ars_lp_tier2_multiplier` - Tier 2 multiplier (default: 2.0)
- `ars_lp_tier3_multiplier` - Tier 3 multiplier (default: 2.5)
- `ars_lp_minimum_cost` - Minimum LP cost (default: 100)

#### LP Calculation - Iron's Spellbooks (8 new options)
- `irons_lp_base_multiplier` - Base LP conversion (default: 10.0)
- `irons_lp_per_level_multiplier` - Level scaling (default: 0.1)
- `irons_lp_minimum_cost` - Minimum LP cost (default: 100)
- `irons_lp_common_multiplier` - Common rarity (default: 1.0)
- `irons_lp_uncommon_multiplier` - Uncommon rarity (default: 1.5)
- `irons_lp_rare_multiplier` - Rare rarity (default: 2.0)
- `irons_lp_epic_multiplier` - Epic rarity (default: 3.0)
- `irons_lp_legendary_multiplier` - Legendary rarity (default: 5.0)

**Total: 22 new configuration options**

### Added - New Event Handlers
- `CurioDiscountHandler` - Applies Ring of Virtue and Blasphemy mana discounts
- `CursedRingHandler` - Handles Cursed Ring LP consumption for Ars Nouveau spells
- `IronsLPHandler` - Handles LP cost messages for Iron's Spellbooks spells
- `LPDeathPrevention` - Prevents death from insufficient LP in safe mode

### Added - Compatibility Layer Enhancements
- Extended `SanctifiedLegacyCompat` with curio detection methods
- Added `hasVirtueRing()` - Detects Ring of the Seven Virtues
- Added `hasAnyBlasphemy()` - Detects any Blasphemy curio
- Added `hasBlasphemyType()` - Detects specific Blasphemy variant
- Added `getMatchingBlasphemyType()` - Maps spell schools to Blasphemy types
- Added `determineSpellSchool()` - Determines spell school from Ars glyphs
- Added `calculateIronsLPCost()` - Configurable LP formula for Iron's spells
- Improved `calculateLPCost()` - Now uses configurable multipliers
- Fixed curio detection to use CuriosUtil (Ars Nouveau API) instead of reflection
- Fixed Blood Magic API integration (correct method signatures)

### Added - Equipment Integration
- Added `CurioDiscountData` class for caching discount information
- Added `getCurioDiscounts()` method for retrieving cached discount data
- Updated `CachedEquipmentData` to include curio discount information
- Curio discount data cached for 1 second (same as other equipment bonuses)

### Changed
- Updated `ArsNSpells.java` to register new event handlers
- Enhanced logging throughout for better debugging
- Improved error messages and user feedback
- Updated configuration file structure with new sections

### Fixed
- Fixed Cursed Ring detection using CuriosUtil instead of non-existent SuperpositionHandler
- Fixed LP consumption for Ars Nouveau spells (spell effects now apply correctly)
- Fixed death prevention system to handle multiple death events
- Fixed "sacrifice" damage type interception (Blood Magic's LP death penalty)
- Fixed spell cast marker persistence across multiple death events
- Fixed insufficient LP message display for Iron's Spellbooks spells
- Increased death prevention window from 500ms to 2000ms for reliability

### Technical Changes
- Switched from mixin-based to event-based Cursed Ring handling for better compatibility
- Implemented triple-layer death prevention system
- Added spell cast marker tracking with 2-second window
- Enhanced curio detection with detailed logging
- Improved Blood Magic Soul Network integration
- Added comprehensive LP calculation system with configurable formulas

### Documentation
- Added `CURIO_DISCOUNT_IMPLEMENTATION.md` - Technical implementation details
- Added `TESTING_GUIDE.md` - Comprehensive testing procedures
- Added `LP_CALCULATION_GUIDE.txt` - LP formula configuration guide
- Added `COMPLETE_IMPLEMENTATION_SUMMARY.md` - Full feature documentation
- Updated `CF_DESCRIPTION.md` - CurseForge description with new features

---

## [1.1.2] - Previous Version

### Features
- Mana unification modes (ISS_PRIMARY, ARS_PRIMARY, HYBRID, SEPARATE, DISABLED)
- Gear perks and enchantments integration
- Spell scaling with Iron's attributes
- Resonance system for full-mana bonuses
- Unified cooldown system
- Progression and affinity tracking
- Cross-mod spell casting (experimental)

---

## Version Comparison

### v1.1.2 → v1.2.0 Summary
- **+22 configuration options** for curio discounts and LP costs
- **+4 new event handlers** for curio integration
- **+10 new methods** in SanctifiedLegacyCompat
- **+3 new classes** (CurioDiscountHandler, CursedRingHandler, IronsLPHandler, LPDeathPrevention)
- **Full Covenant of the Seven integration** with Ring of Virtue, Blasphemy, and Cursed Ring
- **Comprehensive LP calculation system** with configurable formulas
- **Death prevention system** with safe mode and death mode
- **Enhanced messaging** for better user experience

---

## Migration Guide: v1.1.2 → v1.2.0

### Configuration Changes
Your existing `ars_n_spells-common.toml` will automatically gain new sections:
- `["Curio Discount System"]`
- `["Cursed Ring LP System"]`
- `["LP Calculation - Ars Nouveau"]`
- `["LP Calculation - Iron's Spellbooks"]`
- `["LP Rarity Multipliers - Iron's Spells"]`

All new options have sensible defaults. No action required unless you want to customize.

### Behavior Changes
- **No breaking changes** - All existing features work the same
- **New features are opt-in** - Curio discounts can be disabled
- **Cursed Ring now works** - Previously non-functional, now fully implemented
- **Better messages** - Cleaner, more concise user feedback

### Recommended Settings
For balanced gameplay (default):
```toml
enable_curio_discounts = true
death_on_insufficient_lp = false
show_lp_cost_messages = true
```

For hardcore mode:
```toml
death_on_insufficient_lp = true
ars_lp_base_multiplier = 20.0
irons_lp_legendary_multiplier = 10.0
```

---

## Credits

**Mod Author:** Otectus  
**Integration Support:** Covenant of the Seven (Sanctified Legacy) by llenzzz  
**Dependencies:** Ars Nouveau, Iron's Spells 'n Spellbooks, Blood Magic, Curios API

---

## License

GNU GPLv3

---

## Links

- **CurseForge:** [Ars 'n' Spells](https://www.curseforge.com/minecraft/mc-mods/ars-n-spells)
- **Issues:** Report bugs and request features on CurseForge
- **Discord:** Join for support and updates

---

*Last Updated: April 14, 2026*
