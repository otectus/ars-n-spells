# Ars 'n Spells — Audit Findings (v3.0.1)

**Scope**: full repository at commit `5d50eac` (Release 3.0.1). Method: three parallel deep sweeps (architecture, prior-audit reconciliation, build/resources/dist-safety) plus jar-level verification against the actual dependency artifacts (Ars Nouveau 4.12.7 / CurseMaven 6688854, Iron's Spellbooks / CurseMaven 7402504) and the most recent Iron's-loaded gametest run log.

## Part A — Prior audit (AUDIT.md, v2.0.0) reconciliation

Every CRITICAL and HIGH finding from the 147-finding v2.0.0 audit was re-verified against the current code. **Result: all 31 are resolved** — none carried into this findings list.

| IDs | Status | Note |
|---|---|---|
| ANS-CRIT-001…004 | FIXED | Mixin gating (`ArsNSpellsMixinPlugin:74-82`), SEPARATE pre-consume (`CrossCastingHandler:414-442`), compensating rollback (`BridgeManager:324-336`), cost-event priority HIGHEST (`CrossCastingHandler:372`). |
| ANS-CRIT-005, ANS-HIGH-028…030 (post-2.0.0) | FIXED | Source-Jar chunk guard + kill switch, LP invulnerability tick scope, mode refresh on config load, SEPARATE refund. |
| ANS-HIGH-001, 002, 004, 006…017, 019…027 | FIXED | Verified individually at file:line during reconciliation. |
| ANS-HIGH-003, 005, 018 | NO LONGER APPLICABLE | `CasterContext` ThreadLocal and the parallel aura subsystem (`AuraSyncPacket`, `IronsAuraHandler`, …) were deleted outright; aura is owned by Covenant's HUD. |

Note: AUDIT.md's own tail note that ANS-CRIT-002/004 "were not re-deep-verified" is stale — both are confirmed fixed.

Prior MEDIUM/LOW items still open at 3.0.1 are re-registered below as F4–F10/F12 with fresh evidence; ~30 of 41 MEDIUMs and most LOW/OPT items were confirmed fixed or deleted.

## Part B — Current findings

### F1 — HIGH — Apparatus recipes reference an unregistered item; both headline rituals uncraftable when Iron's is loaded
**Files**: `src/main/resources/data/ars_n_spells/recipes/apparatus/spell_transcription.json`, `spellbook_binding.json`
**Evidence**: both list pedestal ingredient `{"item": "irons_spellbooks:spell_book"}`. Iron's `ItemRegistry.class` (jar 7402504) registers only tiered books — `wimpy/copper/iron/gold/diamond/netherite/blaze/dragonskin/druidic/evoker/ice/rotten/villager/necronomicon/cursed_doll/legendary_spell_book` — no plain `spell_book` (the jar's lang key/model for it are leftovers). Confirmed at runtime: `run/logs/latest.log` (Iron's-loaded gametest run) logs `JsonSyntaxException: Unknown item 'irons_spellbooks:spell_book'` for both recipes.
**Root cause**: recipe authored against an assumed generic item id; the `forge:conditional` wrapper only gates on mod-loaded, not item-exists, so the recipe body still parses (and fails) when Iron's is present.
**Impact**: Spell Transcription and Spellbook Binding ritual tablets cannot be crafted in exactly the configuration they exist for. (The command/Spell-Loom paths still work, which is why gametests pass.)
**Fix (implemented)**: ship item tag `ars_n_spells:irons_spell_books` with all 16 tiered books as **optional** entries (`"required": false` — the tag file loads even when Iron's is absent), and reference `{"tag": "ars_n_spells:irons_spell_books"}` in both recipes. Tag over a single item: matches the "any Iron's spell book" intent and the Java-side matching in `IronsBookBindingUtil.isIronsSpellBook`. Guarded by `ApparatusRecipeIngredientTest`.

### F2 — LOW — `pack.mcmeta` declares the 1.19.4 data-pack format
**File**: `src/main/resources/pack.mcmeta` — `"forge:data_pack_format": 12`; 1.20.1 uses 15. Forge still loaded the data, but the declaration is wrong and could trip pack-format validators.
**Fix (implemented)**: 12 → 15; asserted by `ApparatusRecipeIngredientTest`.

### F3 — MEDIUM — Client-typed class in a common package
**File**: `src/main/java/com/otectus/arsnspells/config/ConfigScreenFactory.java` — imports `net.minecraft.client.gui.*`, extends `Screen`, but lives in the common `config` package. Currently safe purely by call-site discipline (only `ArsNSpellsClient` references it); any future common-code reference would `NoClassDefFoundError` a dedicated server.
**Fix (implemented)**: moved to `client/screen/ConfigScreenFactory.java` alongside `SpellLoomScreen`; test path literals updated.

### F4 — MEDIUM — Dead config keys (ANS-MED-031 residue)
**File**: `config/AnsConfig.java`. `PROGRESSION_XP_MULTIPLIER`, `MAX_PROGRESSION_LEVEL`, `AFFINITY_BONUS_MULTIPLIER`, `MAX_AFFINITY_BONUS` were already **removed** in 3.0.1 (commit 601f6a6); only "(NEEDS VERIFY)" tombstone comments remained. Verification is now done (full test + gametest suites pass post-removal; curves live in `ProgressionData.getBonusForSchool` = `min(0.25, casts*0.001)` and `AffinityCalculator` = 0.5%/level).
**Fix (implemented)**: comments finalized. Wiring configurable curves back is **deferred** — it would re-introduce keys deleted in a released version (a feature, not an audit fix).

### F5 — MEDIUM — Two ways to disable mana unification, precedence implicit at call sites
**Files**: `config/AnsConfig.java`, `bridge/BridgeManager.java`, ~7 call sites. `ENABLE_MANA_UNIFICATION=false` and `MANA_UNIFICATION_MODE="disabled"` overlap. Precedence **is** implemented (`AnsConfig.getManaMode()` forces DISABLED when the master toggle is off) and the config comment states it, but several sites read the raw toggle directly, re-encoding the rule.
**Fix (implemented, minimal)**: mode-dependent call sites routed through the existing `BridgeManager.isUnificationEnabled()`; javadoc names it the single source of truth. Both keys kept — removing one is a breaking config change with no functional gain.

### F6 — VERIFIED NOT A BUG — Ritual tablet splice vs `/reload` (was ANS-MED-022)
**File**: `rituals/RitualRegistryHandler.java`. Concern: the one-shot manual splice into Ars `RitualRegistry.getRitualItemMap()` could be lost if Ars rebuilds the map on datapack reload.
**Verification (jar disassembly, Ars 4.12.7 / 6688854)**: `ritualItemMap` is a `private static ConcurrentHashMap` initialized once in `<clinit>`; no `clear()`, rebuild, or reassignment exists anywhere in Ars; every caller is read-only except the one-time population in Ars' item RegisterEvent; nothing subscribes to `AddReloadListenerEvent`/`RecipesUpdatedEvent` for it. `/reload` reloads datapacks, not static class state. The one-shot `registered` boolean is correct.
**Action (implemented)**: verification recorded in the class javadoc; finding closed.

### F7 — MEDIUM (accepted risk) — Plain `HashMap` in capability data classes
**Files**: `data/AffinityData.java`, `CooldownData.java`, `ProgressionData.java`. The server-main-thread-only invariant is documented in the classes and holds at every mutation site (all are server-thread event handlers). Swapping to `ConcurrentHashMap` would mask, not fix, compound read-modify-write races if the invariant were ever broken. **Disposition: leave documented; no change.**

### F8 — MEDIUM (deferred) — Spell-school classification by path substring
**File**: `util/SpellAnalysis.java:128-133` (`path.contains("fire")` etc., with a known `light && !lightning` patch). Replacing with an exact-id/tag-driven map is the right long-term fix but **changes classification for modded glyphs, shifting affinity/progression data in existing worlds**. Deferred to a feature release behind a data-driven mapping.

### F9 — LOW — Hardcoded English scroll messages
**File**: `mixin/irons/MixinScrollItem.java:111,195,213` — `Component.literal` strings; the matching translation keys already existed in `en_us.json:78-81` (`message.ars_n_spells.lp.consumed/.death/.scroll_cancelled`).
**Fix (implemented)**: converted to `Component.translatable(...).withStyle(...)`, mirroring `CursedRingHandler`; guarded by a no-literal structural test.

### F10 — LOW — Dead public API: `IronsLPHandler.storePendingScrollLP`
**File**: `events/IronsLPHandler.java:287`. Zero callers repo-wide (main + test); its javadoc claiming "called by MixinScrollItem" is stale — the mixin uses `compat/ScrollLPTracker.stage()`.
**Fix (implemented)**: method deleted (noted in CHANGELOG in case any third-party pack invoked it reflectively).

### F11 — LOW — Stale documentation
`TESTING_GUIDE.md` mixed 3.0.0/2.0.0/1.9.0 references; `ars-n-spells-mana-fixes.md` described the pending-cost race and aura HUD as open/current although both were fixed or deleted; two more untracked planning docs (`ars-n-spells-2.0.0.md`, `ars-n-spells-update.md`) had drifted from reality (old forge version, old Iron's range).
**Fix (implemented, per user decision)**: guide updated to 3.0.1; the three untracked planning docs deleted.

### F12 — OPT — Redundant mixin: `MixinIronsManaBarOverlay`
**Files**: `mixin/irons/MixinIronsManaBarOverlay.java`, `client/ManaBarController.java`.
**Verification (Iron's jar 7402504)**: Iron's registers `ManaBarOverlay` exclusively via `RegisterGuiOverlaysEvent` (`OverlayRegistry`); ForgeGui fires cancelable `RenderGuiOverlayEvent.Pre` for id `irons_spellbooks:mana_overlay`; `ManaBarController.onRenderOverlay` (@HIGHEST) cancels it under conditions identical to the mixin's HEAD cancel, and the event cancel runs **before** `render` is ever invoked — the mixin can never observably fire. It was `require=0` (allowed to vanish silently) and guarded a call path that has no callers.
**Fix (implemented)**: mixin deleted; `ars_n_spells.mixins.json`, `ArsNSpellsMixinPlugin` gating, and `ArsNSpellsMixinPluginGatingTest` updated. `ManaBarController` remains the single hiding mechanism.

### F13 — MEDIUM — LP cost participants gated on inconsistent config toggles (found during the F5 site review)
**Files**: `mixin/sanctified/MixinSanctifiedAbstractSpell.java`, `mixin/irons/MixinScrollItem.java`.
**Evidence**: the LP system's handlers (`CursedRingHandler`, `IronsLPHandler`, `MixinSpellResolverMana`) all gate on `ENABLE_LP_SYSTEM` ("Master toggle for the Cursed Ring LP system. When disabled, spells use normal mana even with Cursed Ring equipped"). But `MixinSanctifiedAbstractSpell` bypassed Covenant's native LP/death check based on `ENABLE_MANA_UNIFICATION` — the wrong knob — and `MixinScrollItem`'s Cursed-Ring scroll LP path had no LP-system gate at all.
**Impact**: with unification **off** + LP **on**, ANS charged LP while Covenant's native check still ran — the double-penalty/instant-death interaction the mixin exists to prevent. With unification **on** + LP **off**, Covenant's native handling was bypassed with nobody charging LP (free casts). With LP **off**, scrolls kept charging LP while spells didn't, contradicting the documented toggle behavior.
**Root cause**: the bypass predates the LP system's dedicated master toggle and was never re-keyed; the scroll path's "always applies" comment referred to `scroll_cost_mode`, not the system toggle.
**Fix (implemented)**: both sites now gate on `ENABLE_LP_SYSTEM` (default `true` — packs on default configs see no change).

## Part C — Verified non-issues (checked, no defect)

- `arsnspells.crosscast.invalid.*` lang prefix: `CrossCastValidator` emits exactly the 8 keys present in `en_us.json:91-98` (the underscore-less namespace is consistent on both sides).
- `ars_n_spells.mixins.json`: all 13 mixins exist on disk, all on-disk mixins are listed, gating matches (pre-F12).
- `mods.toml` version ranges vs `gradle.properties` pins: consistent (Forge `[47,)`/47.4.0; Ars `[4.12.7,4.13)`/6688854; Iron's `[3.15.0,4.0.0)`/7402504).
- Spell icon textures (8 `icon_*`, 8 `nature_*`, `ars_cross_default.png`) pair 1:1 with lang keys and the `CrossCastNbt` whitelists.
- Networking: all 5 packets direction-guarded and payload-validated; protocol/id stability preserved by unconditional registration.
- Dist safety: no client classes reachable from common/server paths (post-F3 the last smell is gone); S2C handlers use the `DistExecutor` double-lambda pattern correctly.
