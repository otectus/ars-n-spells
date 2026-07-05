# Ars 'n Spells — Architecture Map (v3.0.1 audit)

**Target**: Forge 1.20.1, Java 17, Gradle 8.4 (ForgeGradle `[6.0,6.2)`, official mappings 1.20.1, SpongePowered mixin 0.7.+).
**Mod id**: `ars_n_spells`, package `com.otectus.arsnspells`.
**Dependencies**: Ars Nouveau `[4.12.7,4.13)` (required, compile pin: CurseMaven file 6688854), Iron's Spells 'n Spellbooks `[1.20.1-3.15.0,1.20.1-4.0.0)` (**optional**, `mandatory=false`, compileOnly pin: file 7402504). Optional soft integrations via reflection/probes: Covenant of the Seven ("Sanctified Legacy"), Enigmatic Legacy (Cursed Ring), Blood Magic (Soul Network LP), Nature's Aura (aura chunks), Apotheosis/Curios.

The mod is a **bridge**: unified/converted mana pools, cross-casting Ars spells from Iron's items (and vice versa), export of Ars spells onto Iron's scrolls/spellbooks with native spell-wheel integration, shared cooldowns/affinity/progression/resonance, and LP/aura casting-cost integrations.

---

## 1. Entrypoints & lifecycle

### `ArsNSpells.java` (`@Mod`)
Constructor order is load-bearing:
1. `StartupValidator.validate()` — required-mod/Java checks, non-fatal.
2. Mod-bus: `commonSetup`, `registerCaps`, `onConfigLoading`, `onConfigReloading`.
3. Item registration **before** `ITEMS.register(bus)`: `ModItemsRegistry.registerCommonItems()` always; `registerIronsDependentItems()` (ritual tablets `spell_transcription`, `spellbook_binding`, …) only when `ModList.get().isLoaded("irons_spellbooks")`.
4. `ModBlocksRegistry`/`ModBlockEntities`/`ModMenus` unconditional (Spell Loom is not Iron's-gated).
5. `ArsCrossProxyRegistry.register(modEventBus)` referenced **by FQN only, behind the Iron's gate** — its static init classloads Iron's `SpellRegistry`.
6. Config registered as **`ModConfig.Type.SERVER`** (`ars_n_spells-server.toml`) — dedicated server authoritative, auto-synced to clients.
7. FORGE-bus instance handlers: unconditional (`CooldownHandler`, `AffinityHandler`, `AffinityDecayHandler`, `ArsNSpellsCommands`) and **Iron's-gated** (`IronsCooldownHandler`, `ProgressionHandler`, `IronsProgressionHandler`, `IronsAffinityHandler`, `ArsSpellScalingHandler`, `ResonanceEvents`, `RegenSynergyHandler`, `CrossCastIronsHandler`, `IronsLPHandler` — these import Iron's API and must never classload Iron's-less).

- `commonSetup`: `BridgeManager.init(event)`; `enqueueWork` → `PacketHandler.register()`, `RitualRegistryHandler.registerRituals()`. Wrapped in try/catch "safe mode".
- `onConfigLoading` (`ModConfigEvent.Loading`): `BridgeManager.refreshMode()` on **every** load (SERVER config isn't available at commonSetup, so `init` runs on defaults first — refresh is the real mode selection). One-shot banner (AtomicBoolean): `SanctifiedLegacyCompat.init()` + `runRingIntegrationSelfCheck()` (reflective probe that `MagicDataAccessor` applied, `IronsLPHandler`/`MixinIronsCastValidation` classloadable).
- `onConfigReloading`: `refreshMode()` again.

### `client/ArsNSpellsClient.java` (`@Mod.EventBusSubscriber` MOD/CLIENT)
`FMLClientSetupEvent`: registers config-screen extension point (`ConfigScreenHandler.ConfigScreenFactory`); `enqueueWork` → `MenuScreens.register(ModMenus.SPELL_LOOM, SpellLoomScreen::new)`; optional `OverlayDiagnostics`; `probeCovenantHudCompat()` warns when Covenant version ≠ `TESTED_COVENANT_VERSION = "2.2.6"`.

---

## 2. Mana bridge (`bridge/`, `config/ManaUnificationMode`)

- **`IManaBridge`** — `getMana/setMana/consumeMana/addMana/getMaxMana`; float exchange (documented precision loss > ~16.7M vs Ars' double); **server-main-thread only**. Default `addMana` is read-modify-write; impls override atomically.
- **`ArsNativeBridge`** — Ars `IManaCap` via `ManaUtil`. **`IronsBridge`** — Iron's `MagicData` + `AttributeRegistry.MAX_MANA`; per-op fail-once logging.
- **`ManaRegenBridge`** — unit conversion: Ars regen is absolute mana/sec, Iron's `MANA_REGEN` is a pool-percentage multiplier (factor 0.01). All cross-system regen math must route here (EQUAL_EFFECT / REFERENCE_POOL / DISABLED strategies).
- **`BridgeManager`** — volatile `activeBridge`/`secondaryBridge`/`currentMode`; `refreshMode()` synchronized, called from config load/reload, `/ans mode set`, and the config screen (marshalled onto the integrated server thread). Iron's-absent fallbacks: ISS_PRIMARY/HYBRID/SEPARATE → ARS_PRIMARY.
  **SEPARATE dual-cost** (`consumeManaForMode`): normalizes `DUAL_COST_ARS/ISS_PERCENTAGE` to sum to the base cost, pre-checks both pools, consumes Ars then Iron's, and on Iron's-leg failure issues a **compensating `addMana` refund** (never snapshot/`setMana`, which would clobber concurrent regen).
  `isUnificationEnabled()` (line ~219) composes `ENABLE_MANA_UNIFICATION` + mode ≠ DISABLED — the precedence source of truth.
- **`ManaUnificationMode`** — `ISS_PRIMARY, ARS_PRIMARY, HYBRID, SEPARATE, DISABLED`; `fromString` falls back to ISS_PRIMARY with a WARN.

## 3. Cross-cast spine (`spell/`, `casting/`)

Client right-click → `CrossCastRequestPacket` (C2S) → server re-reads the actual held stack (client index never trusted) → `CrossCastValidator` → dispatch by `CrossSpellType`.

- **`CrossCastingHandler`** — `onRightClickItem` (MAIN_HAND, requires `arsnspells:cross_spells` NBT; defers to the native proxy flow when the item is an Iron's spell book). `serverHandleCast` handles CAST/CYCLE. `castArsSpell` (public — the proxy spell delegates here) opens `CrossCastContext.beginWithAttempt`, casts via Ars `SpellCaster`, and on failure refunds the pre-paid Iron's share (`entry.issPaid`) through the secondary bridge. `castIronsSpell` pre-computes the SEPARATE split and casts inside `CrossCastContext.withManaCheckOverride`. `onArsSpellCost` (`SpellCostCalcEvent` @ **HIGHEST**, before ring handlers zero costs; `tryMarkMultiplierApplied` CAS because the event fires for preview + actual): SEPARATE mode **pre-consumes the Iron's share atomically**, records `issPaid`, zeros `issCost`, sets the event cost to the Ars share.
- **`CrossCastIronsHandler`** (Iron's-gated) — `SpellOnCastEvent`: consumes the Ars share for cross-cast entries; for normal Iron's casts under ARS_PRIMARY converts currency via `effectiveIronToArsRate` (pool-ratio-aware).
- **`CrossCastContext`** — `ConcurrentHashMap<UUID, Entry>`, TTL 100 ticks, cleanup on player tick/logout; `ThreadLocal<ManaCheckOverride>` consumed by `MixinIronsMagicDataMana.getMana` to scale Iron's sufficiency checks. `Entry` fields volatile; `AtomicBoolean multiplierApplied`.
- **`CrossCastValidator`** — single server-side validation point; returns lang reason keys (`arsnspells.crosscast.invalid.*` — all 8 present in `en_us.json`).
- **`CrossCastNbt`** — pure-NBT layer (unit-tested): entries list, index, proxy fields (`proxy_pool_id`, `custom_name`, `nature`, `icon_symbol`), `PROXY_POOL_SIZE = 8`, whitelists for the 8 natures + 8 icon symbols.
- **`CastingAuthority`** — hard gate invoked from `MixinSpellResolverPreCast.canCast`: creative/zero-cost bypass, Cursed-Ring LP validation (Blasphemy multiplier), else mana validation; also owns Iron's scroll-cost modes (`full`/`lp_only`/`free` — scrolls never natively deduct mana; "full" charges here).

## 4. Iron's-native proxy spell system (`spell/irons/`, Spell Loom)

Ars spells appear as native entries in Iron's spell wheel via a **finite proxy-spell pool (8) + NBT sidecar** on the book (Iron's `SpellData` has no extensible NBT — see memory note).

- **`ArsCrossProxyRegistry`** — `DeferredRegister<AbstractSpell>` registering `ars_cross_1..8`; static init touches Iron's classes → FQN-gated referencing discipline.
- **`ArsCrossProxySpell extends AbstractSpell`** — `getManaCost()==0` (real cost paid by the delegated Ars cast); ENDER school so `requiresLearning=false`; `onCast` finds the sidecar entry by pool id and delegates to `CrossCastingHandler.castArsSpell`. Server-only logic.
- **`IronsProxySlotWriter`** — grows the Iron's `ISpellContainer` capacity for proxy slots (never evicts real spells); removal doesn't shrink (avoids re-indexing). All container API confined here.
- **`ArsSpellExportUtil`** — builds a real Iron's scroll (`irons_spellbooks:scroll`) carrying the Ars spell as a sidecar ("carrier"); no top-level Iron's imports.
- **`IronsBookBindingUtil`** — `appendArsSpellToBook` → dedup / pool-id allocation / meta write / FQN-gated `addProxySlot`; `isIronsSpellBook` matches any `irons_spellbooks` item whose path contains `spell_book`/`spellbook` (tiered books).
- **Spell Loom** — `SpellLoomBlock` (+ blockstate/model/loot/advancement/recipe JSONs), `SpellLoomBlockEntity` (3 slots), `SpellLoomMenu`, `client/screen/SpellLoomScreen`, `SpellLoomExportPacket` (C2S; server re-reads slots, whitelists nature/icon, clamps names, consumes inputs, emits carrier).
- Alternate paths: rituals (§6) or `/ans export_to_irons_scroll` + `/ans bind_scroll_to_irons_book`.

## 5. Mixins (`ars_n_spells.mixins.json`, `ArsNSpellsMixinPlugin`)

Plugin probes presence with **`getResource(".class")`** (deliberately *not* `Class.forName`, which classloads the target and collided with Covenant's own mixin → `MixinTargetAlreadyLoadedException`). Gating: 7 Iron's mixins on `ironsPresent`; `MixinSanctifiedAbstractSpell` on `ironsPresent && sanctifiedPresent`; `MixinResourceBarOverlay` on `sanctifiedPresent`; `MixinArsPotionEffects` on `ironsPresent` (targets an Ars class but references Iron's `AttributeRegistry` in bytecode).

| Mixin | Target | Purpose |
|---|---|---|
| `ars.MixinManaCapability` | Ars `ManaCap` | ISS_PRIMARY/HYBRID: redirect reads to the bridge; writes are read-only sync. Per-player `ThreadLocal<Set<UUID>>` recursion guard. |
| `ars.MixinSpellResolverMana` | `SpellResolver.expendMana` | HEAD: ring bypass + bridge consumption (cancels to avoid double-deduct). TAIL: defensive SEPARATE drain (normally pre-consumed → no-op). |
| `ars.MixinSpellResolverPreCast` | `SpellResolver.canCast` | **Hard gate** → `CastingAuthority.canCastArsSpell` (LP/aura/mana validation), replicates Ars recipe checks. |
| `ars.MixinArsPotionEffects` | Ars `ManaCapEvents.playerOnTick` | ISS_PRIMARY: maps Ars mana potion effects onto Iron's attributes via `ManaRegenBridge`. |
| `irons.MagicDataAccessor` | `MagicData` | `@Accessor serverPlayer` for the cast-validation mixin. |
| `irons.MixinIronsCastValidation` | `AbstractSpell.canBeCastedBy` | **Single owner** of the `MagicData.getMana()` redirect: ring bypass (`Float.MAX_VALUE`), ARS_PRIMARY converted-mana check. |
| `irons.MixinIronsSpellDamage` | `AbstractSpell.getSpellPower` @RETURN | × `ResonanceManager.getResonance`. Version-sensitive (3.15.2 comment). |
| `irons.MixinIronsMagicDataMana` | `MagicData.getMana/setMana/addMana` | ARS_PRIMARY bridge redirect; honors `CrossCastContext.ManaCheckOverride`. |
| `irons.MixinScrollItem` | `Scroll.use` HEAD+RETURN | Transactional scroll cost staging/commit/rollback via `ScrollLPTracker`; scroll cost modes; Cursed-Ring LP. |
| `irons.MixinIronsManaBarOverlay` (client) | `ManaBarOverlay.render` `@Pseudo` | Hide Iron's bar in ARS_PRIMARY/HYBRID. **Audit F12: verified redundant with `ManaBarController` — removed.** |
| `irons.MixinAbstractSpellArsIcon` (client) | `AbstractSpell.getDisplayName/getSpellIconResource` | Proxy spells: substitute sidecar custom name + nature/icon texture (whitelisted paths under `textures/gui/icons/spell/`). |
| `sanctified.MixinSanctifiedAbstractSpell` | `AbstractSpell.canBeCraftedBy` (priority 900) | Bypass Covenant's native LP/death check when ANS owns LP handling. |
| `covenant.MixinResourceBarOverlay` (client) | Covenant `ResourceBarOverlay.render` `@Pseudo`, priority 1500 | Rewire Virtue-Ring aura bar divisor/label to `ClientAuraPeakTracker.getPeak()`. **Bytecode-pinned to Covenant 2.2.6** (`@ModifyConstant 2_000_000`, `@Redirect String.valueOf`). |

## 6. Rituals (`rituals/`) & data

`RitualRegistryHandler.registerRituals()` at commonSetup: unconditional `SpellUninscriptionRitual`; Iron's-gated `ManaInfusionRitual`, `ManaWellRitual`, `SpellTranscriptionRitual`, `SpellbookBindingRitual`. Tablets are **manually spliced** into Ars `RitualRegistry.getRitualItemMap()` (Ars' own population loop runs before late registration). **Verified (jar disassembly of Ars 4.12.7 / file 6688854): `ritualItemMap` is a static ConcurrentHashMap populated once and never rebuilt — the splice survives `/reload`.**

Recipes (`data/ars_n_spells/recipes/`): `spell_loom.json` (vanilla shaped); `apparatus/spell_uninscription.json` (unconditional); `apparatus/spell_transcription.json` + `apparatus/spellbook_binding.json` (`forge:conditional` on `irons_spellbooks`; pedestal ingredient fixed by audit F1 to the shipped tag `ars_n_spells:irons_spell_books`). Helpers: `InscriptionInputs` (source classification; Iron's reads isolated in `IronsInscriptionReader` behind `IronsCompat.isLoaded()`), `SpellbookBindingInputs`, `RitualFeedback`.

## 7. Capabilities, events, networking

- **`ModCapabilityProvider`** (`ars_n_spells:bridge_data` on every `Player`) wraps `AffinityData` (16 school levels, 0–100), `CooldownData` (category → end tick), `ProgressionData` (per-school cast counts; bonus = `min(0.25, casts*0.001)`). `PlayerEvent.Clone` @ HIGHEST copies Affinity + Progression (cooldowns intentionally reset on death). Maps are plain `HashMap` under a documented server-main-thread-only invariant (audit F7: accepted).
- **Networking** (`PacketHandler`, channel `ars_n_spells:main`, protocol "3", fixed sequential ids, all registered unconditionally to keep ids stable): `ResonanceSyncPacket`(0, S2C), `AffinitySyncPacket`(1, S2C), `CooldownSyncPacket`(2, S2C), `CrossCastRequestPacket`(3, C2S), `SpellLoomExportPacket`(4, C2S). All S2C use `NetworkDirection` guards + `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, …)`; payloads clamp/validate (finite floats, bounded strings, bounded timestamps).
- **Events** (`events/`): Ars-side handlers (cooldown/affinity/decay/resync/clone), ring economies (`CursedRingHandler` LP, `VirtueRingHandler` aura — 3-phase cost-calc/verify/consume with per-player FIFO deques and TTL sweeps), `CurioDiscountHandler` (@LOW, after rings), `EquipmentHandler`/`ArsManaCalcHandler` (max-mana/regen bridging with reentrancy-breaking deferred sync), `LPDeathPrevention` (same-tick immunity scope), `CrossSpellTooltipHandler` (client), and the Iron's-gated set (LP, cooldown, affinity, progression, spell scaling, Source-Jar regen synergy with non-loading `hasChunk` guard via `ChunkScanUtil`, resonance compute/sync).

## 8. Client & config

- `ManaBarController` — `RenderGuiOverlayEvent.Pre` @ HIGHEST: cancels Ars/Iron's mana-bar overlays per mode; hides both when a ring is equipped. This is the **only** mana-bar-hiding mechanism after F12 removed the redundant mixin.
- `ClientAuraPeakTracker` — monotonic aura peak feeding the Covenant HUD mixin; reset on disconnect.
- `ClientAffinityPacketHandler` — `@OnlyIn(Dist.CLIENT)` mirror store.
- `client/screen/ConfigScreenFactory` (relocated from `config/` by audit F3) — owned-render config screen; mutation gated on `hasSingleplayerServer()` (SERVER config: read-only on multiplayer clients); mana-mode changes applied live via integrated-server `execute(BridgeManager::refreshMode)`.
- `config/AnsConfig` — SERVER `ForgeConfigSpec`, ~90 keys (master toggles, mana unification + rates + dual-cost split, resonance, cooldowns [default off], progression, affinity + decay, curio discounts, Cursed-Ring LP, Virtue aura, scroll cost mode, spell-power cap, Source-Jar synergy + kill switch, ritual amounts, cross-cast multiplier/book caps). `getManaMode()` enforces master-toggle precedence; `safeSave()` async on a daemon executor.
- Commands (`/ans`): `mana setdefault/getdefault`, `debug`, `info <player>`, `mode show/set`, `aura`, `export_to_irons_scroll`, `bind_scroll_to_irons_book` (mutations perm level 2).

## 9. Tests & build

- 46 JUnit-5 files (`./gradlew test`; 12 need the ForgeGradle-provided MC classpath). Style: many are read-source-file structural tests (e.g. gating lists, config structure).
- 11 gametests (`gametest/`: cross-cast 4, export 7; single `platform.snbt` template staged by `stageGameTestStructures`). Iron's-dependent tests self-skip; run fully with `-PwithIronsRuntimeGameTests` (moves Iron's to runtimeOnly + adds PlayerAnimator).
- CI (`.github/workflows/ci.yml`): JDK 17, `compileJava` + `test`; advisory Iron's-less `runGameTestServer`.
- Local builds **must** set `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` (machine default JDK is newer).

---

## Appendix — Known fragilities (documented, intentionally not "fixed")

1. **Covenant HUD mixin bytecode pin** — `MixinResourceBarOverlay` matches a `2_000_000` constant and a `String.valueOf` call in Covenant 2.2.6 exactly; any Covenant update silently degrades (`require=0`) → client probe warns on version mismatch.
2. **`SanctifiedLegacyCompat` reflection fail-open** — Blood Magic / Covenant / Nature's Aura resolution failures degrade to `hasEnoughLP/hasEnoughCovenantAura == true` (casts become free rather than blocked). Deliberate availability-over-economy tradeoff; logged.
3. **SEPARATE-mode cost accounting is distributed** across `CrossCastingHandler.onArsSpellCost` (pre-consume + `issPaid`), `castIronsSpell` (split precompute), `CrossCastIronsHandler` (Ars-share consume), and `MixinSpellResolverMana` TAIL (defensive drain). Signaling via `issCost=0`/`issPaid` is easy to break in refactors — covered by `CrossCastSeparateRefundTest`, `CrossCastContextAtomicTest`, gametests.
4. **`ArsCrossProxyRegistry` static-init discipline** — safe only while every reference stays behind the Iron's gate (FQN, no import leakage).
5. **ENDER-school learning bypass** for proxy spells couples to Iron's school semantics.
6. **Version-brittle `@Pseudo`/`require=0` mixins** (`MixinIronsSpellDamage.getSpellPower` et al.) degrade silently on Iron's updates; the config-load self-check and client probes are the detection mechanism.
