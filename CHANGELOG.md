# Changelog - Ars 'n' Spells

All notable changes to this project will be documented in this file.

## [3.0.0] - 2026-07-02

### Export Ars spells onto Iron's scrolls and bind them into spellbooks

The Ars → scroll → spellbook workflow is the headline 3.0.0 feature. An Ars Nouveau spell can now be exported onto a real `irons_spellbooks:scroll` and then bound into a real Iron's spellbook, where it casts through Ars 'n' Spells' existing server-authoritative cross-cast pipeline. The Ars spell is preserved as an opaque ANS sidecar payload (`arsnspells:cross_spells`) on the real Iron item, coexisting untouched with Iron's own `ISB_Spells` container — no lossy translation into Iron's registry slot model.

- **New binding ritual + tablet.** [SpellbookBindingRitual.java](src/main/java/com/otectus/arsnspells/rituals/SpellbookBindingRitual.java) consumes a carrier scroll and appends its Ars entry onto a spellbook in range, with full pre-mutation validation and translated feedback. The tablet is registered in [ModItemsRegistry.java](src/main/java/com/otectus/arsnspells/registry/ModItemsRegistry.java) only when Iron's is loaded; the [recipe](src/main/resources/data/ars_n_spells/recipes/apparatus/spellbook_binding.json) is wrapped in `forge:conditional`.
- **New export/bind utilities.** [ArsSpellExportUtil.java](src/main/java/com/otectus/arsnspells/spell/ArsSpellExportUtil.java) and [IronsBookBindingUtil.java](src/main/java/com/otectus/arsnspells/spell/IronsBookBindingUtil.java) recognize Iron items by registry id (no top-level Iron's imports) and dedup bound spells by their serialized `ars_spell` payload, not the shared placeholder id.
- **New commands.** `/ans export_to_irons_scroll` and `/ans bind_scroll_to_irons_book` (permission level 2) provide a developer/admin path. [ArsNSpellsCommands.java](src/main/java/com/otectus/arsnspells/commands/ArsNSpellsCommands.java).
- **Cross-spell tooltips.** [CrossSpellTooltipHandler.java](src/main/java/com/otectus/arsnspells/events/CrossSpellTooltipHandler.java) surfaces embedded Ars spells, the active index, and cast/cycle hints on Iron scrolls and spellbooks (still used for generic non-spellbook carriers).

### Ars spells in Iron's native spell wheel + the Spell Loom workstation

Bound Ars spells now appear as their own entries in **Iron's native spell-selection wheel** and cast through Iron's native right-click flow (instead of the ANS sidecar right-click), and are authored at a new **Spell Loom** block.

- **Registered proxy-spell pool.** [ArsCrossProxySpell](src/main/java/com/otectus/arsnspells/spell/irons/ArsCrossProxySpell.java) + [ArsCrossProxyRegistry](src/main/java/com/otectus/arsnspells/spell/irons/ArsCrossProxyRegistry.java) register a finite pool (`ars_cross_1..8`) of real Iron's `AbstractSpell`s into `SpellRegistry`. Each occupies one wheel slot; its `onCast` reads the casting book's sidecar entry (matched by pool id) and delegates to `CrossCastingHandler.castArsSpell`, so cost flows once through `onArsSpellCost`/`BridgeManager` (proxy `getManaCost` is 0; no double-charge). Iron's-gated; never classloads without Iron's. These are **real registered spells, not forged ids** — avoiding the lookup/UI-crash risks of fake Iron's spell ids.
- **Native container write.** [IronsProxySlotWriter](src/main/java/com/otectus/arsnspells/spell/irons/IronsProxySlotWriter.java) adds each proxy into *grown* container capacity, so a player's existing Iron's spells are never evicted. [IronsBookBindingUtil.appendArsSpellToBook](src/main/java/com/otectus/arsnspells/spell/IronsBookBindingUtil.java) allocates a distinct pool id (the wheel de-dupes by spell id) and enforces the per-book cap; it returns a typed `AppendResult` (`ADDED`/`DUPLICATE`/`BOOK_FULL`/`FAILED`).
- **Custom name + icon in the wheel.** Client-only [MixinAbstractSpellArsIcon](src/main/java/com/otectus/arsnspells/mixin/irons/MixinAbstractSpellArsIcon.java) substitutes the per-spell name and icon for proxy entries (`require = 0`, so a future Iron's change degrades to a static fallback rather than crashing). The loom's chosen **icon symbol** (spark/flame/leaf/bolt/star/eye/drop/moon — 8 shipped 16×16 icons) takes priority; the nature icon is the fallback. The icon **color** control was dropped pre-release: Iron's `getSpellIconResource` returns a plain texture path with no tint hook, so a color could never render. Both the nature and icon keys are whitelisted server-side in [SpellLoomExportPacket](src/main/java/com/otectus/arsnspells/network/SpellLoomExportPacket.java) so crafted packets can't stamp NBT that resolves to a missing texture.
- **Native right-click preserved.** [CrossCastingHandler.onRightClickItem](src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java) now defers to Iron's native flow for Iron's spellbooks (no more hijack); the ANS right-click/sneak-cycle path remains for generic inscribed items.
- **Spell Loom workstation.** New block + block entity + menu + screen ([block/](src/main/java/com/otectus/arsnspells/block/), [SpellLoomMenu.java](src/main/java/com/otectus/arsnspells/menu/SpellLoomMenu.java), [SpellLoomScreen.java](src/main/java/com/otectus/arsnspells/client/screen/SpellLoomScreen.java)): drop an Ars source + a blank Iron's scroll, set a name/nature/icon, and inscribe a carrier scroll via the server-authoritative [SpellLoomExportPacket](src/main/java/com/otectus/arsnspells/network/SpellLoomExportPacket.java). Craftable; appears in the Functional Blocks creative tab.
- **New config.** `allow_ars_spells_in_irons_spellbooks` (default `true`) and `max_ars_cross_spells_per_irons_spellbook` (default `-1` = no cap, bounded by the pool size 8) under Cross-Cast Inscription.
- **Localised diagnostics.** The previously-raw `arsnspells.crosscast.invalid.*` validator messages are now translated.
- **Limitation / compat.** At most 8 Ars spells per book show in the native wheel (Iron's `SpellData` stores only id/level/locked and the wheel merges by id). Network protocol version bumped `2 → 3`.

### Pending-cost race fix (free-cast / mischarge on rapid casts)

The Virtue Ring (aura), Cursed Ring (LP), and Iron's-LP handlers staged a pending resource cost at cost-calc and consumed it at the deferred resolve. They each used a **single-entry** `Map<UUID, PendingX>`, so back-to-back casts of delayed-resolution spells (e.g. Ars projectiles whose `SpellResolveEvent.Post` fires on impact) overwrote each other's staged cost — the first cast paid the second's price and the second cast went free.

- **Per-player FIFO queue.** [VirtueRingHandler.java](src/main/java/com/otectus/arsnspells/events/VirtueRingHandler.java), [CursedRingHandler.java](src/main/java/com/otectus/arsnspells/events/CursedRingHandler.java), and [IronsLPHandler.java](src/main/java/com/otectus/arsnspells/events/IronsLPHandler.java) now store a `Deque<PendingX>` per player: enqueue at cost-calc, poll FIFO (skipping expired heads) at resolve. No cast goes free and no staged cost is dropped. Empty deques are evicted by the periodic sweep and on logout, not in the hot consume path (which would race a concurrent cost-calc reusing the same deque).

### Critical fix: world-load deadlock (also shipped standalone as 2.6.2)

- **Fixed a server-thread deadlock while loading chunks.** The Source-Jar regen-synergy scan ([RegenSynergyHandler.java](src/main/java/com/otectus/arsnspells/events/RegenSynergyHandler.java)) called `getBlockState` across a 9×4×9 volume around each player with no loaded-chunk check; near an unloaded chunk border (login, teleport, dimension change, chunk streaming) this forced a synchronous chunk load on the server thread, freezing the game inside `ServerChunkCache.getChunkBlocking`. The scan now verifies the (at most 4) covered chunks are loaded first and retries next cycle otherwise, never caching a result from a skipped scan; its Y range is clamped to world build height. (ANS-CRIT-005)

### High-severity fixes

- **Closed a post-cast invulnerability window.** In safe mode (`death_on_insufficient_lp = false`), [LPDeathPrevention](src/main/java/com/otectus/arsnspells/events/LPDeathPrevention.java)'s death-event safety net cancelled ANY lethal magic/sacrifice damage while the LP-immune flag was set — and the flag lingers up to ~3s after every successful Cursed-Ring cast, silently negating unrelated killing blows (PvP spells, hostile casters, Blood Magic effects). The death handler now enforces the same same-tick scope as the hurt handler. (ANS-HIGH-028)
- **Server config values are now applied at world load.** The mana-bridge mode was cached at common setup, before the SERVER-type config file is read, so a non-default `mana_unification_mode` / `enable_mana_unification` in `ars_n_spells-server.toml` was silently ignored until someone ran `/ans mode set`. [BridgeManager](src/main/java/com/otectus/arsnspells/bridge/BridgeManager.java) now re-selects on every config load/reload and initializes defensively (DISABLED until real values exist). The init banner/self-check now logs once per session instead of on every world load. (ANS-HIGH-029)
- **Failed SEPARATE-mode cross-casts no longer drain Iron's mana.** The Iron's share of a dual-cost cross-cast is pre-consumed during the Ars cost-calc (ANS-CRIT-002); if the Ars leg then failed (insufficient Ars mana, downstream cancel), that payment was silently kept. [CrossCastingHandler](src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java) now records the pre-payment on the cast context and refunds it via the secondary bridge on every failed-cast path. (ANS-HIGH-030)

### Medium fixes

- **Scroll `full` cost mode now actually charges mana.** [MixinScrollItem](src/main/java/com/otectus/arsnspells/mixin/irons/MixinScrollItem.java) validated the mana cost and then charged nothing (Iron's scrolls never deduct mana natively). The cost is now staged at HEAD and consumed at RETURN only when Iron's accepts the use, via the new `CastingAuthority.consumeIronsSpellMana` (mirrors the validation conversion exactly). (ANS-MED-043)
- **Scroll LP staging migrated to per-player FIFO.** [ScrollLPTracker](src/main/java/com/otectus/arsnspells/compat/ScrollLPTracker.java) used a single overwriting slot, so a second scroll use before the first's RETURN clobbered the first's staged cost (the same race the ring handlers' deque migration fixed); entries leaked until logout if another mod suppressed the RETURN. Now a per-player deque with a 5s TTL and opportunistic eviction. (ANS-MED-042)
- **Removed ~20 config options that were never read** (silent modpack-tuning traps): the entire *Ars Glyph Bonuses* section (`amplify_damage_bonus`, `extend_time_duration_bonus`, `split_projectile_count`, `pierce_armor_penetration`, `sensitive_crit_bonus`), the entire *Iron's School Bonuses* section (`enable_school_bonuses`, `school_bonus_multiplier`, ten `*_school_*` per-level values), four of the five resonance caps (`max_duration_multiplier`, `max_projectile_split`, `max_chain_chance`, `max_area_multiplier` — only `max_damage_multiplier` is enforced), `enable_category_cooldowns`, `cooldown_reduction_cap`, `allow_discount_stacking`, `mana_sync_interval`, `enable_caching`, `cache_duration`. Setting any of these had no effect; they can return alongside real implementations. (ANS-MED-044)
- **Rituals consume one item, not the whole stack.** [SpellTranscriptionRitual](src/main/java/com/otectus/arsnspells/rituals/SpellTranscriptionRitual.java) and [SpellbookBindingRitual](src/main/java/com/otectus/arsnspells/rituals/SpellbookBindingRitual.java) discarded the entire source/scroll `ItemEntity`; a stacked input (Iron's scrolls stack to 16) lost the extras.

### Hardening / consistency

- **Fixed packet ids.** [PacketHandler](src/main/java/com/otectus/arsnspells/network/PacketHandler.java) registered `ResonanceSyncPacket` only when Iron's was loaded, shifting every later message id with Iron's presence. All packets now register unconditionally with fixed ids (the packet touches no Iron's classes).
- [CooldownSyncPacket](src/main/java/com/otectus/arsnspells/network/CooldownSyncPacket.java) wraps its client-mirror write in the same `DistExecutor` client guard as its sibling S2C packets.
- [MixinArsPotionEffects](src/main/java/com/otectus/arsnspells/mixin/ars/MixinArsPotionEffects.java) resolves its two target `MobEffect`s once instead of reverse-registry-scanning every active effect twice per player tick. (OPT-019)
- [MixinResourceBarOverlay](src/main/java/com/otectus/arsnspells/mixin/covenant/MixinResourceBarOverlay.java)'s label `@Redirect` is pinned to `ordinal = 0` so a future Covenant build can't get stray " / peak" suffixes on other bars.
- Deleted dead `util/SafeCasterContext.java` (zero callers; superseded by reading `event.context` directly).
- The in-game config screen's save toast now says the save is scheduled (the write is async; the log is the source of truth) instead of unconditionally claiming success.
- The Spell Loom recipe ships an unlock advancement (recipe toast on picking up a book); the loom's icon button label is now translatable (`ars_n_spells.spell_loom.icon` + `ars_n_spells.icon.*`).

### Tests

- **Iron-loaded GameTests.** [CrossCastGameTests.java](src/main/java/com/otectus/arsnspells/gametest/CrossCastGameTests.java) replaces the previous unconditional `helper.succeed()` placeholders with a real CYCLE test driven through `CrossCastingHandler.serverHandleCast` and an Iron-loaded export→bind→coexist round-trip on real `irons_spellbooks` items. The round-trip self-skips when Iron's is absent and runs under the new opt-in `-PwithIronsRuntimeGameTests` profile ([build.gradle](build.gradle)).
- New unit tests: chunk-guard source assertions for the deadlock fix, death-handler tick scope, SEPARATE-mode refund bookkeeping, ScrollLPTracker FIFO/mana-cost carriage.

### Dev environment (build-time only, no player impact)

- **Compile classpath corrected to Ars Nouveau 4.12.7.** `ars_nouveau_file` pointed at CurseMaven file 4781441, which is Ars **4.5.0** — the mod compiled against a different Ars API than the `[4.12.7,4.13)` range mods.toml requires. Now pinned to file 6688854 (4.12.7).
- **Forge build target 47.2.0 → 47.4.0**, the minimum Iron's 3.15+ accepts (players have been on 47.4.x all along; `loaderVersion` stays `[47,)`).
- **Runnable dev environment.** GeckoLib + Curios (Ars' runtime graph) are now `runtimeOnly`, and PlayerAnimator rides the `-PwithIronsRuntimeGameTests` profile, so `runClient`/`runGameTestServer` boot without hand-copied jars. GameTest structure templates are staged from `src/test/resources/gameteststructures/` into the run dir by the new `stageGameTestStructures` task (vanilla/Forge ship none — the suite previously crashed at batch setup with "Could not find structure file gameteststructures/platform.snbt").

## [2.6.2] - 2026-07-02

Standalone hotfix for the 2.6.x line (branch `hotfix/2.6.2`), carrying the three critical fixes above for users not yet on 3.0.0: the chunk-load deadlock (ANS-CRIT-005), the post-cast LP invulnerability window (ANS-HIGH-028), and the SERVER-config bridge-mode timing (ANS-HIGH-029).

## [2.6.1] - 2026-06-18

### Mana-bridge correctness fixes

A full-codebase review (reconciled against the historical [AUDIT.md](AUDIT.md)) confirmed most prior findings were already resolved; this release closes the remaining genuine issues.

- **Concurrent regen no longer lost in ARS_PRIMARY mode.** [MixinIronsMagicDataMana.java](src/main/java/com/otectus/arsnspells/mixin/irons/MixinIronsMagicDataMana.java)'s `addMana` redirect did a non-atomic `getMana()` + `setMana(current + amount)`, clobbering any regen/buff that landed between the read and the write. It now delegates to the bridge's atomic `addMana`, matching `ArsNativeBridge`/`IronsBridge`.
- **SEPARATE-mode dual cost is normalized.** [BridgeManager.java](src/main/java/com/otectus/arsnspells/bridge/BridgeManager.java) now scales the configured `dual_cost_ars_percentage` / `dual_cost_iss_percentage` so the two halves always sum to the spell's base cost. Previously a split summing to ≠1.0 silently over- or under-charged every cast (the sum check was an init-time warning only). New contract tests cover the over-one, under-one, and degenerate-zero cases.
- **Stale messaging fixed.** Removed the "changing the mode requires a game restart" log line and `getCurrentMode` javadoc — the mode has been live-changeable via `/ans mode set` and the config screen since 2.0.1.
- **Debug log fix.** [CurioDiscountHandler.java](src/main/java/com/otectus/arsnspells/events/CurioDiscountHandler.java) used an invalid `{:.1f}` SLF4J placeholder in the Blasphemy-discount trace (the twin of the already-fixed cost-calc log), which dropped the percentage argument. Now formatted correctly.

### Hardening / consistency

- [SanctifiedLegacyCompat.java](src/main/java/com/otectus/arsnspells/compat/SanctifiedLegacyCompat.java)'s curio-state cache now stamps with server-global `gameTime` (`long`) instead of per-player `tickCount`, consistent with the ring handlers' ANS-HIGH-011 migration.
- Added missing `player == null` guards to `setMana`/`consumeMana` in [ArsNativeBridge.java](src/main/java/com/otectus/arsnspells/bridge/ArsNativeBridge.java) and [IronsBridge.java](src/main/java/com/otectus/arsnspells/bridge/IronsBridge.java) (their `addMana`/`getMaxMana` siblings already had them).
- Added the missing null-context guard to [CrossCastRequestPacket.java](src/main/java/com/otectus/arsnspells/network/CrossCastRequestPacket.java)'s `handle`, matching its sibling packets.

## [2.6.0] - 2026-06-14

### Apotheosis / Apothic Curios mana bridge

Affixed and socketed **curios** (rings, amulets, belts) now contribute their mana stats to the unified Ars ↔ Iron's pool. The equipment scanner already read attribute modifiers off armor and weapons — so Apotheosis affixes there always worked — but the curio path read only `IManaEquipment` and Ars enchantments, so an affixed/socketed ring's max-mana or mana-regen never reached the cross-mod bridge. A Mana Battery Ring took effect in its own system's pool but was invisible to the other.

- **Curios attribute modifiers are now read and mirrored.** [EquipmentIntegration.java](src/main/java/com/otectus/arsnspells/equipment/EquipmentIntegration.java) enumerates worn curios via the Curios API (`CuriosApi.getCuriosInventory(...).findCurios(...)`) and reads each slot's aggregated attribute modifiers (`CuriosApi.getAttributeModifiers(slotContext, uuid, stack)`), summing Ars `max_mana`/`mana_regen` and Iron's `max_mana`/`mana_regen` through the **same `sumModifiers` helper and ADDITION-only rule the armor path uses**. This routes Apotheosis (with Apothic Curios) affixes & sockets — and any other curio mana gear such as Magical Jewelry or Jewelcraft — through the existing bridge. No double-counting: the curio's own pool already gets the modifier natively; the bridge only mirrors it to the other system, exactly as armor does.
- **No hard Apotheosis dependency.** The read is generic (any mod's curio mana attributes), gated behind the new `read_curio_attribute_modifiers` config (default on) and wrapped so any API surprise degrades to "no bonus" rather than a crash.
- **Spell power needed no change** — it is read from the player's *total* attribute, so affix spell power on armor/weapons/curios already counted. Apothic Attributes' own attributes (crit, armor pierce, fire/cold damage) are combat-only and out of scope for the mana/spell bridge.
- **Build:** [build.gradle](build.gradle) adds a `compileOnly` Curios API dependency (`5.9.1+1.20.1`, the version Ars Nouveau bundles at runtime). Curios is always present at runtime via Ars, so this only puts the slot-aware API on the compile classpath.

### Ring / aura-HUD correctness and hardening

- **Aura-bar peak tracker is now lock-free.** [ClientAuraPeakTracker.java](src/main/java/com/otectus/arsnspells/client/ClientAuraPeakTracker.java) stores the personal peak in an `AtomicInteger` with a monotonic `getAndUpdate` ratchet, so the render-thread read in the HUD mixin composes cleanly with client-tick writes (and stays correct if a sync writer is ever added).
- **Covenant version-drift probe.** [ArsNSpellsClient.java](src/main/java/com/otectus/arsnspells/client/ArsNSpellsClient.java) logs at startup whether Covenant of the Seven is at the version the aura-bar HUD mixin (`MixinResourceBarOverlay`) was bytecode-verified against (2.2.6). The mixin uses `require = 0`, so a mismatch can't crash the client; this makes the silent fallback diagnosable instead.
- **Virtue aura system honors its toggle.** [VirtueRingHandler.java](src/main/java/com/otectus/arsnspells/events/VirtueRingHandler.java) now short-circuits its handlers when `ENABLE_VIRTUE_AURA_SYSTEM` is off.
- **Dead alternate-resource stubs removed.** [CastingAuthority.java](src/main/java/com/otectus/arsnspells/casting/CastingAuthority.java) dropped the never-implemented `detectAlternateResourceCost`/`validateAlternateResource` path (it always returned null/true); Cursed/Virtue ring costs are owned by their dedicated event handlers.

### Performance (render + tick hot paths)

- **OPT-008:** [MixinArsPotionEffects.java](src/main/java/com/otectus/arsnspells/mixin/ars/MixinArsPotionEffects.java) only churns the mana attribute modifiers when the value actually changed, instead of a remove/add every server tick (which forced an attribute recompute).
- **OPT-009:** [ManaBarController.java](src/main/java/com/otectus/arsnspells/client/ManaBarController.java) matches mana overlays on the `ResourceLocation` namespace/path directly (allocation-free `equals`) instead of `toString()` + substring scan every overlay every frame. The matchers are extracted as pure, package-private, unit-testable methods.
- **OPT-010 / MED-019:** [OverlayDiagnostics.java](src/main/java/com/otectus/arsnspells/client/OverlayDiagnostics.java) registers its per-frame render subscriber on the Forge bus only while diagnostics are enabled (zero dispatch cost when off, the default), and uses a `TreeSet` for already-sorted iteration.
- **IronsLP debug trace gated.** [IronsLPHandler.java](src/main/java/com/otectus/arsnspells/events/IronsLPHandler.java) builds its per-cast entry-trace strings only when `LOGGER.isDebugEnabled()`.

### Tests

- New unit coverage for the above: [ClientAuraPeakTrackerTest](src/test/java/com/otectus/arsnspells/client/ClientAuraPeakTrackerTest.java) (monotonic ratchet), [ManaBarControllerOverlayMatchTest](src/test/java/com/otectus/arsnspells/client/ManaBarControllerOverlayMatchTest.java) (overlay matchers), and [VirtueRingAuraToggleTest](src/test/java/com/otectus/arsnspells/config/VirtueRingAuraToggleTest.java) (toggle gating).

## [2.0.1] - 2026-05-29

### Mana unification mode is changeable in-game again

2.0.0 left the mana mode effectively unchangeable: the in-game config screen's "Mana Mode" row was a dead stub (getter `() -> true`, setter `value -> {}`, with a never-implemented `// Mode cycling handled separately` note), `/ans mode` only *printed* the mode, and `MANA_UNIFICATION_MODE.set(...)` was called nowhere. The only path left was hand-editing a TOML — and the docs pointed at the wrong file (see below). 2.0.1 restores a working path, applied **live** (no restart).

- **The config screen "Mana Mode" row now cycles.** [ConfigScreenFactory.java](src/main/java/com/otectus/arsnspells/config/ConfigScreenFactory.java) gains a non-boolean "cycling" `ConfigOption` variant; clicking the Mana Mode row advances `mana_unification_mode` through `iss_primary → ars_primary → hybrid → separate → disabled` and writes it via `AnsConfig.MANA_UNIFICATION_MODE.set`. Gated to singleplayer by the existing `canMutate` (`hasSingleplayerServer()`) check — read-only behavior on dedicated servers is unchanged.
- **New op command `/ans mode set <mode>`.** [ArsNSpellsCommands.java](src/main/java/com/otectus/arsnspells/commands/ArsNSpellsCommands.java) — permission level 2, tab-completes the five mode names, strictly validates input (a typo is reported, not silently coerced to ISS the way `ManaUnificationMode.fromString` would), persists, and echoes the requested *and* now-active mode. `/ans mode` (display only) is unchanged.
- **Changes apply live.** New [`BridgeManager.refreshMode()`](src/main/java/com/otectus/arsnspells/bridge/BridgeManager.java) re-reads the configured mode and re-selects the active/secondary bridges at runtime; the command calls it directly (server thread) and the config screen marshals it onto the integrated server. The three bridge-state fields are now `volatile`. **Caveat:** on a dedicated server a mid-session change applies to server-side gameplay immediately, but a connected client's HUD may reflect the new mode only after reconnecting.
- **Test seam.** New package-private `BridgeManager.testSetMode(...)` — the seam the 2.0.1 `CrossCastCostResolverTest` roadmap item called for — sets the cached mode without constructing bridges, keeping unit tests bootstrap-free.

### Config file location (documenting the 2.0.0 COMMON → SERVER move)

2.0.0 switched the config to `ModConfig.Type.SERVER` ([ArsNSpells.java](src/main/java/com/otectus/arsnspells/ArsNSpells.java), `ANS-HIGH-016`) but deferred documenting it to this release. The live config file is now **per-world**:

- `<world>/serverconfig/ars_n_spells-server.toml` — singleplayer: `.minecraft/saves/<World>/serverconfig/`; dedicated server: `<server>/world/serverconfig/`.
- The old global `config/ars_n_spells-common.toml` is **ignored**. Settings made there before upgrading do not carry over — re-apply them in the new per-world file (or via the config screen / `/ans` commands). This is the most common cause of a mana mode that appears "stuck."

### Documentation

- Corrected the stale `config/ars_n_spells-common.toml` path in [README.md](README.md), [CURSEFORGE_DESCRIPTION.md](CURSEFORGE_DESCRIPTION.md), and [TESTING_GUIDE.md](TESTING_GUIDE.md) to the per-world server-config path.
- Added a "Changing the mode" note to the README mana-unification section (in-game screen / `/ans mode set` / direct file edit; applies live).
- Fixed the stale README title version (`v1.9.0` → `v2.0.1`).

## [2.0.0] - 2026-05-14

### Audit-driven major release

2.0.0 addresses the comprehensive technical audit at [ars-n-spells-2.0.0.md](ars-n-spells-2.0.0.md). Every High and Medium-High hypothesis in the audit's "Ranked Root-Cause Hypotheses" maps to a closed fix below. The major-version bump reflects two breaking changes (new C2S packet ID; clients older than 2.0.0 cannot cross-cast against a 2.0.0 server) plus the architectural reorganization of the cross-cast pipeline.

### Phase 1 — Hotfix (cross-cast actually works again)

- **Client-side cancellation no longer swallows the cast.** [CrossCastingHandler.java](src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java) used to call `handleCrossCast` on both sides, return `true` on the logical client, and then `event.setCanceled(true)` — so on a dedicated server the client claimed success and cancelled the use, while the server never received any cast trigger. Silent "input detected, nothing happens" failure. The client now short-circuits before any cast logic and sends a new `CrossCastRequestPacket` instead; the server is the sole authority over cast execution.
- **Cross-cast decoupled from mana unification.** The previous `BridgeManager.isUnificationEnabled()` guard in [`CrossCastingHandler.onRightClickItem`](src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java), [`CrossCastingHandler.onArsSpellCost`](src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java), and [`CrossCastIronsHandler.onIronsSpellCast`](src/main/java/com/otectus/arsnspells/spell/CrossCastIronsHandler.java) made inscribed items inert whenever `mana_mode=disabled` or `enable_mana_unification=false`. The README documents `disabled` as a *mana sharing* mode, not as a master switch for cross-cast. Settled policy: cross-cast remains available in every mode; the multiplier still applies; in `disabled` mode, native upstream pools pay native costs (no SEPARATE split, no ARS_PRIMARY conversion). The unification check survives as an internal mode-branch flag inside each cost site.

### Phase 2 — Stabilization (multiplayer authoritative + traceable)

- **New C2S [`CrossCastRequestPacket`](src/main/java/com/otectus/arsnspells/network/CrossCastRequestPacket.java).** Carries hand, action (`CAST`/`CYCLE`), client-observed index, and a client-generated attempt UUID. The server handler re-reads the held stack (no client trust), generates its own attempt UUID for trace correlation, and dispatches to the new `CrossCastingHandler.serverHandleCast` entry point.
- **New [`PacketHandler.sendToServer`](src/main/java/com/otectus/arsnspells/network/PacketHandler.java).** Helper for the new C2S direction. `CrossCastRequestPacket` is registered at the *tail* of the packet ID list so pre-existing IDs (Resonance, Affinity, Cooldown, Aura) do not shift.
- **New [`CrossCastValidator`](src/main/java/com/otectus/arsnspells/spell/CrossCastValidator.java).** Single authority for cross-cast payload validity. Checks index range, payload non-emptiness, spell-type resolution (with namespace fallback), Ars `ars_spell` non-empty, Iron's `spell_id` parseability + level ≥ 1 + registry resolution. Rejections produce a translation key the player sees via `displayClientMessage` and a structured `descriptor_rejected` trace event. The four-arg public API is the production path; a package-private overload accepts a synthetic Iron's predicate so unit tests do not need Forge bootstrap. 16-case [`CrossCastValidatorTest`](src/test/java/com/otectus/arsnspells/spell/CrossCastValidatorTest.java) covers every branch.
- **`attemptId` UUID on [`CrossCastContext.Entry`](src/main/java/com/otectus/arsnspells/spell/CrossCastContext.java).** Threaded from the packet handler through `serverHandleCast`, into `CrossCastContext.begin(...)`, and read back at every Ars/Iron mixin and event-handler trace point. Parallel cross-casts no longer alias their logs.
- **New [`util/CrossCastTrace`](src/main/java/com/otectus/arsnspells/util/CrossCastTrace.java).** Structured logger emitting `[CrossCastTrace] attempt=<uuid> player=<name> side=<C|S> stage=<symbolic> k=v …` exactly as specified in the audit's "Debug Instrumentation Plan". Gated on `debug_mode`. Stages: `INPUT_DETECTED`, `REQUEST_SENT`, `REQUEST_RECEIVED`, `DESCRIPTOR_VALIDATED`, `DESCRIPTOR_REJECTED`, `RESOURCE_CHECK`, `RESOURCE_SPEND`, `ARS_COST_APPLIED`, `IRON_COST_APPLIED`, `UPSTREAM_CAST_ENTER`, `UPSTREAM_CAST_EXIT`, `EFFECT_APPLIED`, `CYCLE_APPLIED`. Used from [`CrossCastingHandler`](src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java), [`CrossCastIronsHandler`](src/main/java/com/otectus/arsnspells/spell/CrossCastIronsHandler.java), [`MixinSpellResolverPreCast`](src/main/java/com/otectus/arsnspells/mixin/ars/MixinSpellResolverPreCast.java), and [`MixinSpellResolverMana`](src/main/java/com/otectus/arsnspells/mixin/ars/MixinSpellResolverMana.java).
- **New [`CrossCastCostResolver`](src/main/java/com/otectus/arsnspells/spell/CrossCastCostResolver.java).** Single source of truth for the cross-cast cost algorithm — captures the mode × multiplier × ring state matrix in one place. Returns a `CostBreakdown(primary, secondary, primaryMode, secondaryMode, mode, unified, ringActive, multiplier)` record. Three stages: `ARS_PRECALC`, `IRON_PRECALC`, `IRON_POSTEVENT`. For 2.0.0 this is an authoritative *calculator* — existing cost-mutation sites still own choreography (event mutation, mixin cancels, ring pending-cost stamping); full delegation onto the resolver is tracked for 2.0.1.
- **New [`CapabilityResyncHandler`](src/main/java/com/otectus/arsnspells/events/CapabilityResyncHandler.java).** Single owner of bridge-capability resync across `PlayerLoggedInEvent`, `PlayerRespawnEvent`, and `PlayerChangedDimensionEvent`. Replays Affinity (one packet per non-zero school), Cooldown (one packet per active category), and Resonance (when Iron's is loaded). Aura is *not* duplicated here — [`AuraCapabilityProvider`](src/main/java/com/otectus/arsnspells/aura/AuraCapabilityProvider.java) already covers all three events from 1.10.0. The previous `AffinitySyncOnLoginHandler` is retired; its login coverage is subsumed.

### Phase 3 — Hardening

- **Sealed [`SpellDescriptor`](src/main/java/com/otectus/arsnspells/spell/SpellDescriptor.java) model** with [`ArsSerializedSpellDescriptor`](src/main/java/com/otectus/arsnspells/spell/ArsSerializedSpellDescriptor.java) and [`IronsRegistrySpellDescriptor`](src/main/java/com/otectus/arsnspells/spell/IronsRegistrySpellDescriptor.java). Typed adapter between the two upstream spell models with uniform `validate/serialize/displayName/resolve/systemType/spellId`. On-disk NBT shape is unchanged so pre-2.0.0 inscribed items round-trip cleanly via `SpellDescriptor.parse(CompoundTag)`. Full migration of call sites off raw `CompoundTag` maps onto descriptors is tracked for 2.1.0.
- **New [`CastContext`](src/main/java/com/otectus/arsnspells/spell/CastContext.java) value record.** Threads attempt UUID + player + hand + source stack + descriptor + mode + cost breakdown through the pipeline. Existing sites still pass individual parameters; full migration is tracked for 2.1.0.
- **GameTest scaffold.** [`build.gradle`](build.gradle) gains a `gameTestServer` run target gated on the `ars_n_spells` namespace. [`CrossCastGameTests`](src/main/java/com/otectus/arsnspells/gametest/CrossCastGameTests.java) ships one sanity scenario confirming the scaffold is wired; the full seven-scenario suite from the audit's "Testing and Validation Strategy" lands in 2.0.1 alongside the structure NBT templates each scenario requires.
- **CI workflow.** [`.github/workflows/ci.yml`](.github/workflows/ci.yml) — JDK 17 on Ubuntu, gradle and ForgeGradle caches, `./gradlew compileJava test` as the required-status job, `./gradlew runGameTestServer` as an advisory job (continue-on-error true while the GameTest suite is still landing).

### Breaking changes

- **Packet ID list grew.** `CrossCastRequestPacket` is appended at the tail of `PacketHandler.register()`, so existing packet IDs (Resonance, Affinity, Cooldown, Aura) are unchanged. Clients older than 2.0.0 cannot cross-cast against a 2.0.0 server — they cannot send the new packet. Servers older than 2.0.0 cannot serve 2.0.0 clients — they will reject the new packet ID. Use matching client/server versions.
- **`AffinitySyncOnLoginHandler` removed.** Its login-sync responsibility is fully subsumed by `CapabilityResyncHandler` (which adds respawn + dimension sync). No user-facing behavior change for affinity on login.
- **`mana_mode=disabled` now permits cross-cast.** Previously, setting the mode to disabled (or `enable_mana_unification=false`) made inscribed items inert. They now cast normally with native upstream pool costs; only mana *sharing* is suppressed in disabled mode. Modpacks that intentionally wanted cross-cast disabled by setting `mana_mode=disabled` will see cross-cast become available again — there is no separate `enable_cross_casting` toggle; the feature is now always-on whenever an inscribed item is held.

### Known follow-ups (deferred to 2.0.1 / 2.1.0)

- **2.0.1**: full site delegation onto `CrossCastCostResolver` (current 2.0.0 has the resolver but call sites still own choreography); `CrossCastCostResolverTest` with the mode × ring × stage matrix (needs a `BridgeManager.testSetMode` test-only seam).
- **2.0.1**: the seven cross-cast GameTest scenarios (clean Ars cast, clean Iron cast, malformed NBT rejection, insufficient resources, dimension transition, separate-mode dual cost, ring + cross-cast) — each needs its own structure NBT template.
- **2.1.0**: full migration of `CrossCastingHandler` / `CrossCastValidator` call sites off raw `CompoundTag` maps onto `SpellDescriptor`; `CastContext` threaded through the pipeline.
- **2.1.0**: server/client config split, datapack registries for spell schools / cooldown categories / progression rules / cross-cast rules.

### Backward compatibility

- **Save format unchanged.** AffinityData, ProgressionData, CooldownData, AuraCapability, and `arsnspells:cross_spells` NBT shapes all preserved. Inscribed items from 1.8.9+ load and cast unchanged at 2.0.0.
- **Mod config unchanged.** No keys added, removed, or renamed. The `mana_mode=disabled` semantics change is documented above.
- **Mixin injection points unchanged.** All Ars `SpellResolver` injects keep their `@At` targets and method signatures. Mixin-compatible with Ars 4.12.7+; no Iron's-side mixin changes.

### Ring of Virtues and Ring of Curses — correctness pass

Investigation found five correctness bugs in the Sanctified Legacy / Covenant of the Seven ring integration. Symptoms ranged from "the rings silently do nothing on a C7-only modpack" to "the player pays both mana and aura on the same spell after unequipping the ring mid-cast." All are fixed in 1.10.0.

- **`hasBothRings` AND-gate fix.** [SanctifiedLegacyCompat.java:222](src/main/java/com/otectus/arsnspells/compat/SanctifiedLegacyCompat.java) returned `false` whenever **either** Covenant of the Seven **or** Enigmatic Legacy was missing. But C7 ships both rings — meaning a C7-only modpack with both rings equipped silently dropped through every ring path: `isWearingCursedRing` cancelled out, `isWearingVirtueRing` cancelled out, ring-conflict notification never fired, and the player paid mana while believing aura was being consumed. The guard is now `&&` instead of `||`. `hasMatchingBlasphemy` was also tightened to use `isAvailable()` for consistency.
- **Stale pending-cost can't leak across spells anymore.** [VirtueRingHandler](src/main/java/com/otectus/arsnspells/events/VirtueRingHandler.java) and [CursedRingHandler](src/main/java/com/otectus/arsnspells/events/CursedRingHandler.java) used to consume the pending aura/LP cost purely on UUID lookup at `SpellResolveEvent.Pre`. If another HIGHEST-priority handler cancelled a spell between `SpellCostCalcEvent` and `SpellResolveEvent.Pre`, the pending cost lingered for up to 5 seconds; the next spell — even with the ring unequipped — was charged aura/LP **and** mana ([MixinSpellResolverMana](src/main/java/com/otectus/arsnspells/mixin/ars/MixinSpellResolverMana.java) only cancels mana if you're currently wearing the ring). Both handlers now re-verify `isWearingVirtueRing` / `isWearingCursedRing` at every consumption site and drop the stale entry if state changed.
- **Resolve consumption moved from Pre to Post.** Consuming on `SpellResolveEvent.Pre` meant that if another mod cancelled the cast at Pre after our HIGHEST handler ran, the resource was charged but the spell never executed ("paid but didn't cast"). Aura/LP is now charged on `SpellResolveEvent.Post`, which only fires for resolved (non-cancelled) spells. Validation continues to happen at `canCast` ([MixinSpellResolverPreCast](src/main/java/com/otectus/arsnspells/mixin/ars/MixinSpellResolverPreCast.java)) so impossible casts still get blocked cleanly. Pre is kept as a state-drift gate that drops the pending entry if the ring came off between cost-calc and resolve.
- **Iron's Spellbooks + Virtue Ring path now exists.** Previously, casting any Iron's spell while wearing the Virtue Ring drained Iron's mana — only the Cursed Ring had an Iron's handler. Symmetric coverage with the new [IronsAuraHandler](src/main/java/com/otectus/arsnspells/events/IronsAuraHandler.java): pre-cast validation against the aura pool, mana zeroed at `SpellOnCastEvent`, aura consumed instead. Insufficient aura cancels the cast with an action-bar message (no death penalty — Virtue Ring failure is intentionally non-punitive). Iron's scrolls get the same treatment via a new aura branch in [MixinScrollItem](src/main/java/com/otectus/arsnspells/mixin/irons/MixinScrollItem.java) plus [ScrollAuraTracker](src/main/java/com/otectus/arsnspells/compat/ScrollAuraTracker.java) for the HEAD→RETURN commit pattern that ScrollLPTracker already uses.
- **`AuraCapability` no longer corrupts new players.** [AuraCapability.java](src/main/java/com/otectus/arsnspells/aura/AuraCapability.java) used to read `AURA_MAX_DEFAULT` in its constructor and fall back to **100** on `IllegalStateException`. `AttachCapabilitiesEvent` can fire before `ModConfigEvent.Loading`, so any player created before config load was permanently capped at 100 aura (10% of the configured default of 1000). The capability now defers initialization until first server-side access (so config is guaranteed loaded), and `loadNBTData` runs a one-shot migration: if a saved `maxAura == 100` and the configured default is higher, the player is reset to the configured default and an `ans_v110_migrated` marker is stamped onto the NBT so the heuristic only runs once. Users who legitimately wanted a 100-aura cap should re-apply their config preference after upgrading.

### Aura HUD and sync

The mana bar is hidden while the Virtue Ring is worn (since spells cost aura, not mana), but until now nothing replaced it — the player flew blind. 1.10.0 ships the missing pieces:

- **New** [AuraSyncPacket](src/main/java/com/otectus/arsnspells/network/AuraSyncPacket.java) — server→client sync of `(aura, maxAura)`. Sent on login, dimension change, respawn (post-clone), and whenever the server-side capability marks itself dirty and the `mana_sync_interval` tick window elapses.
- **New** [ClientAuraState](src/main/java/com/otectus/arsnspells/client/ClientAuraState.java) — client-side mirror, reset on disconnect.
- **New** [AuraBarController](src/main/java/com/otectus/arsnspells/client/AuraBarController.java) — `RenderGuiOverlayEvent.Post` overlay drawn just above the hotbar in aqua tint when the local player wears the Virtue Ring. Hidden when `mc.options.hideGui`, when the ring isn't equipped, or when the aura system is disabled.

### Ring swap mid-cast no longer leaks

The previous handler had no signal for Curios slot changes (`LivingEquipmentChangeEvent` does not fire for Curios), so the per-player curio cache could stay stale for up to a second and a stamped pending cost could be consumed against the wrong wearer state. The cleanup now happens at the consumption site instead: both `SpellResolveEvent.Pre` and `SpellResolveEvent.Post` in the ring handlers re-check `isWearingVirtueRing` / `isWearingCursedRing` (with the freshness provided by the 20-tick `SanctifiedLegacyCompat` curio cache) and drop the pending entry when state has changed. A dedicated `CurioChangeEvent` listener that would cut the "ring just equipped → not active yet" latency from ~1 s to instant is deferred — the event class is not on this mod's transitive compile classpath without an extra `compileOnly` dependency declaration.

### Configuration

- **New: `enable_aura_system`** (default `true`) — master toggle mirroring `enable_lp_system`. When false, the Virtue Ring is ignored and spells use normal mana. Useful for modpacks that want LP but not aura.
- **Removed: `virtue_ring_discount`** — the Ring of Virtue stopped being a mana discount in 1.2.0 (it converts mana to aura). The config key has been dead since then; setting it had no effect. Removed entirely in 1.10.0. Existing configs will get an "unknown key" warning on first load and can safely delete the line.
- **Changed default: `aura_minimum_cost`** is now `10` (was `5`) — matches `ars_lp_minimum_cost`. Existing configs preserve whatever value the user set.

### Commands

- **New: `/ans aura`** — non-permissioned subcommand that prints the caller's own aura value. The existing `/ans info <player>` is unchanged (still op-only).

### Known follow-ups (deferred to 1.11.0+)

- `SERVER` / `CLIENT` config split. All keys still live on the single `COMMON` config.
- Datapack registries for spell schools, cooldown categories, progression rules, cross-cast rules.
- Cross-cast NBT re-validation at cast time (server-trust hardening).
- Aura-specific rarity multipliers for Iron's spells (currently shared with the LP rarity knobs).

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
