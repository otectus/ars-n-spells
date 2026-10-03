# Changelog - Ars 'n' Spells

All notable changes to this project will be documented in this file.

## [3.3.6] - 2026-10-03

### Fixed

- An Ars Nouveau spell bound into an Iron's spellbook can now be cast by right-clicking an Iron's staff. The hotkey cast worked, but the staff cast failed with "Cross-cast failed: the book carrying this spell (wheel slot N) could not be found" and the server logged "no carried spellbook holds a sidecar entry". Iron's starts a staff cast with the staff itself as the casting item and the staff's hand as the equipment slot, while the cast source still says the spell came from the equipped spellbook; ANS treated the staff as the only possible carrier and refused. The carrier is now read from the cast source and the slot: a spellbook cast resolves against the equipped book and nothing else, a cast from a held container (an imbued weapon, a scroll) resolves against that item, and a book without the entry is still refused rather than redirected to another book that shares the wheel slot. Verified against Iron's 1.20.1-3.15.0 and 1.20.1-3.16.3.
- A payment made while the Iron's pool sits above its `max_mana` ceiling now moves exactly the spell's price. Iron's clamps every mana write to the ceiling, so such a cast lost the whole surplus. The 3.3.5 build published on CurseForge went further and refused the cast outright (`CEILING_INCONSISTENT`, "Cast stopped. Check your resources; details are in the server log."), which a player saw as every Iron's spell failing at full mana; the Modrinth 3.3.5 build paid but let the clamp take the surplus. The ceiling can sit below the pool for a few ticks after a max-mana modifier goes away, until Iron's next regeneration tick applies it; that tick, not the payment, now does the clamping. The first such payment per player and server run is logged with the ceiling's modifiers, so a pack author can see which bonus was missing.

### Added

- GameTests for the staff path: a bound Ars spell cast by right-clicking a staff, in creative and funded survival; the hotkey path with a staff held; two bound books sharing a wheel slot; a stale binding next to a valid book; a book that is only held; and a native Iron's spell cast from a staff, with its payment and cooldown.

## [3.3.5] - 2026-09-27

### Fixed

- Iron's spells no longer stop with "Cast stopped. Check your resources; details are in the server log." when another mod checks mana inside Iron's own mana payment. 3.3.4 took the price as soon as Iron's final cost event ended, before Iron's own mana check and write. A mod that checks affordability at that point, such as Animus on NeoForge 1.21.1, saw the pool already debited and cancelled the cast; ANS refunded it and logged `Effect boundary not reached`. Any spell costing more than half the remaining pool failed. ANS now takes the price where Iron's writes its own mana debit and replaces that write, so such a check sees the pool as Iron's would, and a top-up made there pays for the cast. The cost event then states what ANS will take from the pool Iron's reads: the converted amount in `ars_primary`, the Iron's share of a dual-cost split, and 0 for bound Ars spells and LP-paid casts. No Forge 1.20.1 mod is known to act at that point; the change keeps both builds' payment code the same.
- A cast that another mod cancels before its effect now costs nothing and is not reported as a payment failure: no warning and no "Cast stopped" message. Iron's own follow-through runs as it would without ANS, and debug mode logs `stage=vetoed_before_effect`.
- The startup self-check now names each handler it needs and also covers the payment and cast-ticker mixins. Before, any ANS method on `AbstractSpell` passed, so a failed payment mixin would not have been reported.
- Channelled Iron's spells no longer stop with a payment failure when mana runs low. 3.3.4 turned off Iron's own end-of-channel check, so a channel such as BielGG's Crystal Barrage (250 mana per pulse) ran into a pulse the caster could not pay and stopped with "Cast stopped. Check your resources; details are in the server log." ANS now checks after every paid pulse, at that pulse's final price and in the pool that paid it. When the balance cannot cover another pulse, the channel finishes as Iron's native final pulse, with cooldown, scroll consumption and completion. Final-price modifiers such as BielGG's Thorn Ring are honoured; Iron's own check only ever saw the base cost.
- `ars_primary`: Iron's mana regeneration can no longer lower the Ars pool. Iron's regeneration reads and writes the Ars balance in this mode but clamped it to Iron's mirrored `max_mana`, which is rounded down to a whole number and can briefly lag the real Ars maximum. That clamp removed mana without any cast. It is the only such path found and the most likely cause of the reported dip while scrolling the hotbar, although the report itself has not been reproduced. Regeneration may now only raise the balance.
- The shared-pool ceiling is no longer computed before an equipment change has taken effect. Forge fires the change event before vanilla swaps the item's attribute modifiers, so ANS now reconciles at the end of the player's tick instead. Delayed ceiling updates are merged per player, so an older maximum can no longer be applied after a newer one.
- Ars spells cast from Iron's spell wheel counted as successful even when Ars refused them, for example for lack of mana, because Ars's caster reports success either way. Success now comes from Ars's own resolver. A refused cast no longer grants the first cross-cast advancement.
- A Cursed Ring LP shortage on an Iron's cast is now reported as LP, not as Iron's mana.
- A resource shortage now says what was needed and what was available, for example "Not enough Iron's mana for Crystal Barrage: 250 needed, 180 available.", instead of the generic message. Shortages are ordinary gameplay and are no longer logged as warnings; debug mode records one line per failed cast with the price and resource. Other payment faults still warn, now including the price. These diagnostic lines identify the player by a pseudonymous token instead of their UUID.
- Config migration: only a config file without a schema stamp is checked for being newly generated, so a stamped 3.3.0–3.3.2 file can no longer be mistaken for a new one. Files from 3.3.3 and 3.3.4 receive only the new 3.3.5 step.

### Added

- `inscribed_ars_default_cooldown_ticks` (Cross-Cast Inscription, 0–12000 ticks, 20 ticks = 1 s): Iron's cooldown after an Ars spell bound into an Iron's spellbook casts successfully from Iron's spell wheel. It uses Iron's own cooldown system, so Iron's cooldown reduction, the wheel's cooldown display and saving across relogs all apply. A failed, refused or unpaid cast starts no cooldown. Newly generated configs use 40 ticks (2 s); configs from earlier versions migrate to 0, which keeps the previous behaviour. Changes take effect on the next cast after the config reloads. The setting is also on the in-game settings screen. Books that reuse the same proxy slot share its cooldown. Ordinary Ars casts, ordinary Iron's spells and Iron's spells inscribed into Ars items are unaffected, and the optional category cooldowns remain a separate check.
- Debug mode logs both mana pools, the Iron's ceiling, the selected slot and the held item whenever equipment reconciliation changes something (at most ten lines per second per player). It also logs each regeneration write that was refused because it would have lowered the Ars pool.
- New GameTests cover: channels that run out of mana in all five modes; a SWORD channel with a halved final cost; a delayed SWORD cast charged once at its final price; `ars_primary` hotbar cycling with Iron's regeneration; removal of a real maximum-mana bonus; and the inscribed cooldown after success, failure, zero, cooldown reduction, shared slots and category cooldowns.
- New GameTests reproduce the NeoForge Scorch report with a GameTest-only mixin at the call Animus uses: an affordability check in all five modes at conversion rates 0.5, 1 and 3, a top-up of an underfunded cast, a cancel before the effect, and a channel under the check. Only `runGameTestServer` sets `-Dans.gametest.castProbe=true`, which applies that mixin.
- `-PwithIrons316RuntimeGameTests` runs the Iron's-loaded GameTests against Iron's Spellbooks 1.20.1-3.16.3 (with irons_lib 1.20.1-2.1.0) instead of the 3.15.0 build pin.
- `tools/pack_presets.py` accepts config schema 3 as well as 2; before this change it would have refused every 3.3.5 config. `docs/3.3.5/config-reference.md` lists every key of this release.

### Compatibility

- Tested: Minecraft 1.20.1, Forge 47.4.10, Ars Nouveau 4.12.7, Iron's Spellbooks 1.20.1-3.15.0 and 1.20.1-3.16.3. BielGG's Spells Addon 1.5-patchwork was inspected from its jar but not run, because it also requires L_Ender's Cataclysm and Lionfish API. Details and the remaining limits are in `docs/3.3.5/`.
- Animus 1.20.1-3.0.35 was inspected from its jar: it works through Iron's cast check and cost events and does not act inside Iron's mana payment. It was not run. The Iron's GameTests also passed with Covenant of the Seven 2.2.6-hotfix loaded.
- Ars Affinity has no Forge 1.20.1 release and is not supported on this build. It is covered by the NeoForge 1.21.1 build.

### Forge / NeoForge parity

The Forge 1.20.1 and NeoForge 1.21.1 builds now have the same features, configuration, commands, datapack tags and translations, except for integrations whose mod has no build for the other Minecraft version (Covenant of the Seven and Too Many Glyphs exist only for 1.20.1; Ars Affinity and Ars Elemancy only for 1.21.1). `docs/3.3.5/parity-review.md` lists every remaining difference and why. This build gains:

- **Cross-mod combat stats in both directions.** Iron's spell power already scaled Ars spell damage. The Ars Spell Damage Bonus perk now also adds to Iron's spell damage as a flat bonus, which `spell_power_cap` does not limit. **This changes damage for players with that perk**; set `enable_ars_damage_for_irons_damage = false` to keep the 3.3.4 behaviour. New Spell Scaling keys: `enable_cross_mod_combat_stats`, `enable_irons_power_for_ars_damage`, `enable_ars_damage_for_irons_damage` (all on by default and independent of the mana mode) and `multi_school_power_policy` (`primary` by default, as before; `max` or `average` let a multi-school spell scale with another of its schools; affinity and progression still credit the first school). `/ans debug combat` shows the settings, both mods' spell-damage attributes and the last scaled hit in each direction.
- **Tagged-curio discount.** Each worn curio in `#ars_n_spells:curio_spell_discount` reduces Ars and Iron's cast costs by `virtue_ring_discount` (0.20), and all of them together by at most `max_total_curio_discount` (0.50). A cast that cost mana never becomes free. With Covenant installed, the Blasphemy discount still applies after it. The shipped tag lists Iron's school focus items, which are not normally wearable; packs add their own curios. `enable_curio_discounts` covers both discounts.
- **`#ars_n_spells:cross_cast_blacklist`.** Ars Zero's five multi-phase glyphs only work inside a Spell Staff. A spell using them is now refused by the Spell Loom, `/ans export_to_irons_scroll`, Spell Transcription and every cross-cast, with a message naming the glyphs.
- `/ans info` also lists the player's affinity levels and the number of registered Iron's schools. Its raw Iron's mana line is translatable, and the ring-bypass flag appears only with Covenant installed.
- Server stop now also clears staged scroll costs, cast-validation scopes, in-flight cross-casts, combat diagnostics and log throttles.
- The "Cross spell selected" message is translatable, and cross-cast refusals are shown in red.
- Config comments match the NeoForge build. The `mana_unification_mode` comment no longer claims a restart is needed, because mode changes apply live.
- The debug-only startup lock check looks at `ars_n_spells-server.toml`, not the pre-2.0.1 `-common.toml`.
- New GameTests: combat bridge (11), `ParityBehaviourGameTests` (Source Jar synergy, gear bonus per mode, potion mirroring, tagged curios), uninscription teardown, the Curios attribute toggle, Ars Zero profile, resolver-hook and blacklist checks, proxy pool reuse and the carrier reconciler. `tools/verify_loader_parity.py` now ships here too and checks both directions, including GameTest coverage.

## [3.3.4] - 2026-09-13

### Fixed

- Fixed Iron's book casts finishing their windup without producing an effect when Ars mana had a fractional balance near a float precision boundary.
- Apply the final spell cost once, including discounts and free casts, and avoid a second native mana write after ANS pays.
- Failed first channel pulses no longer consume a scroll or start a payment-failure cooldown. Channels that already produced effects keep native interruption rules.
- Optional category cooldowns now start with native cooldowns, including the end of recast sequences.
- Preserve observed refunds after partial or throwing resource operations. Unresolved credits survive orderly server restarts and cannot be paid twice.
- Clean up casting validation on cancellation and exceptions, preserve native restrictions, and reject stale cast identities or changed pricing before effects.

### Packaging

- Bundle MixinExtras for the invocation wrappers. No configuration migration is required; use the bundled Forge release JAR.
- Detailed reproduction evidence and tested versions are in `docs/3.3.4/casting-repair-testing.md`.

## [3.3.3] - 2026-09-12

### Added

- New item `ars_n_spells:blank_scroll` ("Blank Scroll"), a Spell Loom substrate for servers
  that don't want to hunt down a bare `irons_spellbooks:scroll` (`registry/ModItemsRegistry.java`).
  Crafted shapeless from an Ars Nouveau blank parchment and a source gem
  (`data/ars_n_spells/recipes/blank_scroll.json`), unlocked by a matching recipe advancement,
  and listed in the ANS creative tab and JEI alongside the mod's other items.
- Iron's chest loot can now drop the Blank Scroll when Iron's Spellbooks is installed:
  `loot/BlankScrollLootModifier.java`, registered via `registry/ModLootModifiersRegistry.java`
  and wired into `data/forge/loot_modifiers/global_loot_modifiers.json`. Four tiers under
  `data/ars_n_spells/loot_modifiers/` cover stronghold libraries (15%), ancient city and
  woodland mansion chests (10%), common dungeon/mineshaft/stronghold chests (5%), and a wide
  set of Iron's own structure chests (6%). The Java gate — not a datapack condition — is what
  keeps the modifier inert without Iron's, so it's always registered but never rolls. Weights
  are datapack-overridable; there is no config key.
- Iron's native Inscription Table now binds ANS carrier scrolls into Iron's spellbooks
  directly, as a third bind route alongside the Spellbook Binding ritual and
  `/ans bind_scroll_to_irons_book`. Previously the table only refused carriers with a
  "can't read this scroll" message. The server-side router lives in
  `mixin/irons/MixinInscriptionTableMenu.java` (`clickMenuButton` injection), which classifies
  the scroll via `spell/irons/IronsInscriptionPolicy.java` and, for a well-formed carrier,
  hands off to `spell/irons/IronsTableBindHandler.java`. The handler fires Iron's own
  `InscribeSpellEvent` (so other mods still see the inscription and can veto it), binds through
  the shared `spell/IronsSpellbookBinder.java`, consumes exactly one scroll on success, and
  grants the `bind_spell` advancement.

### Changed

- Redesigned the Spell Loom as an ornate arcane workstation with carved walnut legs,
  brass fittings, crystal finials, a suspended Source bobbin, and individual spell-weaving
  threads above an inlaid inscription motif. Six Minecraft-style 16x16 pixel-art materials, a shaped
  outline/collision box, and an updated inventory presentation replace the old cube.
- The Spell Loom's target slot now accepts the new Blank Scroll in addition to a blank Iron's
  scroll (`inscription/LoomInscription.java`, `isTarget`). The output is unchanged: a real
  `irons_spellbooks:scroll` carrier built by
  `spell/ArsSpellExportUtil.createIronsScrollCarrier`. Converting an already-filled Iron's
  scroll still works exactly as before.
- The Spellbook Binding ritual, `/ans bind_scroll_to_irons_book`, and the Inscription Table now
  all go through one shared binder, `spell/IronsSpellbookBinder.java` (`bind`, `BindResult`),
  instead of two separate implementations of the same checks (the table route did not exist
  before this release). Proxy pool allocation still
  happens only at bind time, in `spell/IronsBookBindingUtil.appendArsSpellToBook`.
- Scroll classification is centralized in the new `spell/ScrollKind.java` enum (`NATIVE`,
  `ANS_CARRIER`, `ANS_BLANK`, `INVALID`, `OTHER`), used by the table policy, the binder, and the
  tooltip handler so they agree at the edges. An invalid carrier is refused with
  `message.ars_n_spells.bind.invalid_carrier` and is never deleted; a legacy container-less
  carrier is repaired in place by `spell/irons/CarrierReconciler` on the table path as well as
  the ritual and command paths.
- Selecting an Ars proxy entry in the Inscription Table no longer offers anything in the
  extraction slot (`setupResultSlot` injection in `MixinInscriptionTableMenu.java`); Spell
  Uninscription remains the way to remove a bound Ars spell.
- The carrier scroll tooltip now shows the Loom-assigned name and nature plus
  "Use at an Iron's Inscription Table or with the Spellbook Binding ritual."
  (`events/CrossSpellTooltipHandler.java`), and the ritual's own description
  (`ars_n_spells.ritual_desc.spellbook_binding`) now says binding is "one of two routes" instead
  of implying the ritual is the only way. The Spell Loom's scroll-slot hints now name the ANS
  Blank Scroll alongside a blank Iron's scroll, where they previously named only the latter.

### Known limitations

- Ars Nouveau's own structure loot tables are not targeted by the new loot modifiers — no
  confirmed table ids for them were identified for this release.

## [3.3.2] - 2026-09-07

### Fixed

- Iron's contextual mana bar now hides when the displayed mana is full even if
  equipment or shared-pool modifiers give the maximum a fractional value. The
  visibility check uses the same integer precision as Iron's mana packet and HUD,
  preserving held casting items and the Always/Never settings.
- Fixed the shared visibility predicate on both Forge 1.20.1 and NeoForge 1.21.1,
  so the XP bar returns when the contextual mana bar hides at the XP anchor.
- Both loaders use identical mana-visibility rules and regression cases. No
  config, network protocol, or save-format changes from 3.3.1.
- The mana-bar overlay handler now returns before any config read for every overlay that
  is not the Ars Nouveau or Iron's Spellbooks mana bar, and the debug-config read on its
  error path is itself guarded, so a not-yet-loaded server config can no longer make Forge
  skip an unrelated overlay's render.

### Added

- Overlay diagnostics (debug mode) now listen at lowest priority and receive cancelled
  events, logging once per overlay when its Pre arrives already cancelled and once when
  its Post fires — so the log shows whether a third-party HUD (for example one drawing
  from the vanilla hotbar) was suppressed, and by what.

### Testing

- A unit test pins that the mana-bar matchers never match vanilla or third-party overlay
  ids (`minecraft:hotbar`, `minecraft:potion_icons`, `minecraft:effects`,
  `durabilityviewer:*`).

Validation: full builds, unit suites, and ISS-absent/loaded GameTests passed on
both loaders. See [the 3.3.2 verification record](docs/3.3.2-validation.md).

## [3.3.1] - 2026-09-06

### Removed: transaction receipt HUD and its client delivery chain

- The transaction receipt panel — the overlay that rendered every frame for the eight seconds a
  receipt stayed live after a cast, showing what it quoted, paid, or refunded — is deleted
  outright, not just switched off. It was already suppressed while the player was null, the HUD
  was hidden with F1, or a screen was open, but there is no renderer left in the tree to revive:
  `TransactionHud`, `ClientTransactionState`, `TransactionSyncPacket`, `TransactionSnapshotCodec`,
  and `TransactionSync` are all removed, along with the `transaction.ars_n_spells.*` lang keys
  that fed them. There is no config option for the panel.
- `ArsCastPayments` and `IronsCastPayments` no longer report transactions; the server publishes
  no receipt snapshot to clients at all. `TransactionSnapshot` itself still exists — its `Reason`
  enum remains a record component of `NativePayment.Result` for server-side payment bookkeeping —
  but nothing builds or sends a full snapshot off the server anymore.
- **Breaking:** removing `TransactionSyncPacket` shifts `JournalSnapshotPacket` down one packet id,
  so `PacketHandler.PROTOCOL_VERSION` moves from `5` to `6`. A client still on the old jar hard-fails
  at connect instead of misdecoding packets; update client and server together.

## [3.3.0] - Unreleased

3.3.0 updates both Forge 1.20.1 and NeoForge 1.21.1. The complete requirement and validation record is in [the audit closure ledger](docs/3.3.0-audit-status.md). Implementation and runtime verification are tracked separately; this entry does not certify unexecuted release gates.

### Custom spell icons and backgrounds

- Added 274 semantic icon IDs, original 16px and separately authored 32px art, normal/selected/disabled/high-contrast/monochrome variants, and 11 selectable icon frames/backgrounds.
- Added the icon library and Loom icon/frame picker with search, labels and keyboard focus. Stored logical icon IDs retain legacy aliases. Resource reload publishes a replacement registry and checks whether textures exist before choosing a fallback.
- Native Iron's proxy icons resolve from the selected physical carrier; ambiguous carrier selection refuses instead of silently choosing another book. The eight existing proxy registry IDs remain stable.

### Vanilla GUI overhaul

- Replaced purple panels across the Spell Loom, details, icon picker, school journal, compatibility screen and settings with light-grey container bevels, dark labels without shadows, recessed slots and native Minecraft buttons. HUD receipts now use a neutral translucent black background and white text.
- The Loom fits the minimum 320x240 scaled viewport with a 176x224 panel, a vanilla-style recipe arrow and separate output/icon slots. Concise localized status hints link to complete tooltips and details.
- Loom details wrap and scroll using the mouse wheel, scrollbar or keyboard. Settings use native focusable controls with narration and read-only gating; descriptions have full tooltips. Icon selection uses white borders and an inset state, with native hover/focus feedback.
- Forge 1.20.1 and NeoForge 1.21.1 share the presentation with explicit background-rendering adapters so NeoForge's blur stays behind the panels. See [GUI design and validation](docs/3.3.0/gui-overhaul.md).

### Casting and resource accounting

- Native cast contexts own immutable quotes. Repeated price checks do not debit resources; a successful payment occurs at the native expenditure boundary before effects. Canceled casts release uncommitted reservations.
- Quotes contain final paying units. Native spells in separate mode pay only their own pool; reusable cross-casts use normalized dual-cost shares. Directional flat rates apply across a resource boundary. Equal-percentage conversion is a separate policy using captured native maxima.
- Restored public Ars mana set/add/remove behavior while limiting native regeneration suppression to the native regeneration tick. Native pool adapters remain available when sharing is disabled.
- Native Iron's long casts and full-cost scrolls retain their initiation price and pay before effects; accepting scroll use is only initiation. Generic reusable carriers use trusted native book semantics and native effective spell levels.
- Forge LP/aura payment is associated with the initiating cast instead of an unrelated per-player queue. Missing or insufficient alternate resources follow the configured refusal, native-fallback or legacy-open policy. Full Covenant-stack runtime acceptance remains recorded separately.
- Cross-cast packets validate the current hand, carrier fingerprint, player/menu state and server-selected descriptor. Bounded rate and nonce admission refuse replay and stale swaps.

### Schools, progression, equipment and Source

- School classification uses payload roles and declared metadata, with namespaced custom-school identity. Deterministic mapping overlays expose provenance and synchronize replacement snapshots to clients.
- Unified Neo progression modifier identity and cleanup of historical IDs preserve accumulated cast counts. Live cleanup removes owned contributions before recomputing and synchronizing affected state.
- Shared mana ceilings use native attribute contributions without repeatedly amplifying owned modifiers. Curios and enchanted-equipment integration use actual attribute operations and bounded regeneration conversion.
- Resonance uses its configured threshold, linger duration and strength. Source Jar proximity caches expire when stationary and invalidate on dimension/tag/config changes; discovery cadence no longer determines mana income.

### Inscription and diagnostics

- Loom planning reads reusable sources without consuming them, refuses already-filled targets, and preserves inventory when output is occupied. Transcription converts one target unit and returns the remainder.
- Both loaders apply the same source/target insertion and output extraction policy. Unbinding removes owned proxies/metadata while retaining native spells and upgrades. Future schemas are refused without rewriting the saved item.
- Added read-only school, progression, compatibility/diagnostic and removal-report tools. See the player and pack-author guides for command syntax and limits.

### Migration and validation

- Network protocol is now 5; update clients and server together. Config schema 2 migrates prior Source income with a backup and preserves explicit payment policy. Item/component schema stamps and schema-v2 school mappings retain legacy reads and unknown identities.
- Both loader branches have blocking dependency-absent and loaded GameTest CI, isolated run directories, explicit runtime identities, and exact completion/executed/skipped log validation. Shared domain code and golden fixtures have a checked SHA-256 manifest.
- Validation includes native resolver/resource/effect tests, non-unit conversion rates, replay admission, inventory conservation, and negative completion-log fixtures. Fresh run results and remaining client, multiplayer, performance and optional-version gates are listed in the ledger.

## [3.2.4] - 2026-09-02

### Performance: server-side hot paths trimmed

- The **Mana Well** ritual scanned every entity section within its range (configurable up to
  64 blocks) on every world tick for as long as the brazier stayed lit. It now walks the level's
  player list and tests each player against the same box, so an idle well costs almost nothing.
- Several per-tick handlers (Source Jar regen synergy, resonance sync, equipment integration,
  potion regen bridge) asked Forge's `ModList` whether Iron's Spellbooks was loaded on every
  player tick, before their own throttle gates. That lookup streams the whole mod list. They now
  use the mod's cached answer, and the regen handler checks phase and side before anything else.
- The shared-pool mana ceiling removed and re-added Iron's `MAX_MANA` modifier on every Ars
  mana recalculation, which happens every regen interval per player. Because that attribute is
  client-synced, every recalculation broadcast an attribute packet to the player and everyone
  tracking them even when nothing changed. The sync now returns early when the ceiling is
  already correct.
- Resonance was pushed to the client every 40 ticks per player regardless of whether it had
  changed. It is now sent only when the value moves; login and respawn resyncs are unchanged.

No gameplay behaviour changes. No config, packet, or save-format changes.

## [3.2.3] - 2026-09-01

### Fixed: the ritual brazier never finished an Ars 'n' Spells ritual, so four rituals did nothing

- Placing a **Spellbook Binding**, **Spell Transcription**, **Spell Uninscription** or
  **Mana Infusion** tablet on a Ritual Brazier and lighting it did nothing at all: no items
  consumed, no result, no error message, and the brazier stayed lit indefinitely. Every one of
  these rituals had been inert since 3.0.0.
- Ars Nouveau only runs a ritual's payload once the ritual marks itself finished - the brazier
  calls `onEnd()` exclusively when `RitualContext.isDone` is set, and the only thing that sets it
  is a ritual calling `setFinished()` from its own `tick()`. All four of these rituals shipped
  with an empty `tick()`, so they burned forever and never reached their payload. They now run
  for about three seconds and then complete. Ritual tablets are not returned, matching Ars's own
  rituals.
- This went unnoticed because both the documentation and the automated tests reached for
  `/ans bind_scroll_to_irons_book` instead, which was never affected. New GameTests now drive a
  real brazier through the full light-burn-complete lifecycle, and assert that every one-shot
  ritual actually finishes.

### Fixed: ritual failure messages were silently dropped if you walked away

- A ritual's chat feedback went to the nearest player within 8 blocks *at the moment it
  completed*. Since a ritual now visibly burns for a few seconds, a player who lit it and stepped
  away got no message at all - success or failure. Rituals now remember who lit them (persisted
  across chunk unloads and restarts) and report to that player wherever they are, falling back to
  proximity only for redstone-triggered rituals.
- The binding ritual's advancement used a 16-block radius while its messages used 8, so a player
  twelve blocks out silently earned the advancement while being told nothing. Both now resolve the
  same player.

### Fixed: `/ans bind_scroll_to_irons_book` ignored the binding kill switch

- Setting `allow_ars_spells_in_irons_spellbooks=false` blocked the ritual but not the command, so
  the feature stayed available to operators on servers that had switched it off. The command now
  refuses too.
- The ritual also checked that setting only *after* validating the dropped items, so on a server
  with binding disabled you were told "unexpected item(s) in range" or "drop a carrier scroll and
  a spell book" - troubleshooting advice for a ritual that was never going to run. It now says the
  feature is disabled straight away.

### Changed: Spellbook Binding documentation

- The in-game tablet description had not been updated since before 3.0.0. It pointed at Spell
  Transcription for making carrier scrolls (the Spell Loom has been the survival path since 3.0.0)
  and claimed bound spells cast with right-click and sneak-right-click (they cast from Iron's
  native spell wheel). Rewritten, and it now states the "nothing else in range" rule the ritual
  actually enforces.
- The README, mod page and testing guide now say that the ritual reads *dropped items* rather than
  your inventory, give the three-block radius, note that any other item in range aborts the run,
  and explain that you light a brazier by right-clicking it with an empty hand. They also warn that
  Ars Nouveau ships its own ritual named literally **Binding** - a different ritual that makes
  Bound Scripts for familiars and ignores scrolls - which is easy to reach for by mistake.

## [3.2.2] - 2026-08-28

### Fixed: Iron's loot chests could contain fake "Ars 'n' Spells" spell scrolls

- Exploration chests, Mystery Scroll Pouches, magic-mob drops and wandering traders could hand
  you a spell scroll named after an Ars Nouveau spell whose tooltip read as a raw translation
  key (`spell.ars_n_spells.ars_cross_1.guide` and similar). These were the mod's eight internal
  `ars_cross_*` proxy spells - the hidden slots that let an Ars spell appear in Iron's native
  spell wheel - and they are meaningless outside a spellbook that carries the matching spell.
  Using one did nothing.
- Ars 'n' Spells does not modify any loot table, and never did. Iron's Spellbooks picks a random
  spell out of its own registry, and a spell is eligible unless it opts out with `allowLooting`.
  The proxies set `allowCrafting=false`, which keeps them out of the Scroll Forge, but Iron's
  loot never looks at that flag. They now opt out of looting explicitly, so no new one can be
  generated - by loot, by a wandering trade, or when a looted ring is imbued.
- They were also the most likely thing to be rolled, not the least: the proxies declare Common
  rarity, which carries the heaviest weight in Iron's random-spell table.
- A stray scroll already in your world is repaired the first time you right-click it: it becomes
  a blank Iron's scroll instead of a permanent dud, and the cast is refused rather than charged.
  Unopened chests are unaffected - loot is rolled when a chest is first opened, so they were
  never wrong to begin with.
- The internal warning logged when a proxy fires with no spellbook behind it is now rate-limited
  per player. Clicking a dud scroll repeatedly could otherwise flood a server log.
- Added the eight missing spell-description translations, so a proxy that is still visible
  anywhere shows readable text rather than a raw key.

## [3.2.1] - 2026-08-25

### Fixed: a mixin conflict that stopped an entire modpack from loading

- Installing Ars 'n' Spells alongside **One Mana Bar** made the game fail to start, taking
  **twelve mods down with it** — Iron's Spellbooks itself, ISS: Magic From The East, Construct's
  Casting, Grimoire of Gaia Spells, Fallen Gems & Affixes, Iron's Spell's Delight, Additional
  Attributes, Interlace SpellWeaves, Farmers Spell, Apprentice's Codex, Iron's Botany, and ANS.
- The cause was ours. We adjusted the mana value Iron's reads inside
  `AbstractSpell.canBeCastedBy` with a `@Redirect` aimed at one specific instruction in that
  method. One Mana Bar replaces the whole method, so the instruction was gone — and Mixin
  treats that as a fatal error rather than skipping the injection. `require = 0` does not help:
  the check runs before the requirement count is ever consulted.
- The adjustment now uses only `HEAD` and `RETURN` injection points, which Mixin permits into a
  replaced method by design. The ring bypass and ARS_PRIMARY mana conversion behave exactly as
  before, and they now keep working even when another mod rewrites that method.
- A second, independent safeguard covers the case where a replacing mod does not read mana the
  way Iron's does: if a Ring of Seven Curses/Virtues wearer is refused a cast for insufficient
  mana, that refusal is overturned. In the normal path this never triggers.

### Fixed: mixin failures can no longer abort mod loading for the whole pack

- Mixins targeting Iron's Spellbooks and Covenant of the Seven — both **optional**
  dependencies — moved into a separate, non-required mixin config. A conflict there now logs a
  warning and skips that one mixin instead of aborting startup for every mod in the chain.
- Mixins targeting Ars Nouveau stay required. Ars is a hard dependency, so a failure there
  means the mod is genuinely broken and should fail loudly rather than half-work.
- The startup self-check was reporting `OK` unconditionally: it verified our mixin classes were
  loadable, which they always are, rather than whether they had actually applied. It now
  inspects the Iron's classes themselves and names any degraded feature in one greppable log
  line. It also runs later in startup, so it no longer forces Iron's classes to load early —
  which could break other mods' mixins.

### Fixed: Iron's scrolls have been casting completely free

- **This one is a nerf, and it will be noticeable.** `MixinScrollItem` targeted `use` without
  requesting name remapping, but in a released jar that method is renamed to `m_7203_`. The
  injection therefore matched nothing and failed silently, in **every released build**. Scrolls
  have been casting with no mana, no LP and no aura cost the entire time.
- Scrolls now cost what `scroll_cost_mode` says they cost. The default is unchanged (`full` —
  the same cost as casting the spell normally); set it to `lp_only` or `free` if you prefer the
  old behaviour.
- The build now fails if any remapped injection stops producing a mapping, so this class of
  silent breakage cannot ship again.

## [3.2.0] - 2026-08-21

### Added: a dedicated creative tab

- Every Ars 'n' Spells item now lives in one **Ars 'n' Spells** creative tab, keyed by the
  Spell Loom. Previously the mod owned no tab and its items borrowed other mods': the Spell
  Loom was pushed into vanilla **Functional Blocks**, and the five ritual tablets surfaced
  inside the **Ars Nouveau** tab.
- The tablets were never placed there deliberately. ANS has to splice its tablets into Ars
  Nouveau's ritual item map so Ritual Braziers and JEI can resolve a tablet back to its ritual,
  and Ars's tab generator enumerates that same map. The splice is untouched — braziers and JEI
  work exactly as before — but the tablets no longer appear in Ars's tab.
- **Where things moved:** the Spell Loom is no longer in Functional Blocks, and the Spell
  Transcription, Spellbook Binding, Spell Uninscription, Mana Infusion and Mana Well tablets
  are no longer in the Ars Nouveau tab. All six are in the new tab, and all six remain findable
  in the creative **Search** tab and in JEI/EMI.
- The tab is not gated on Iron's Spellbooks. Without Iron's it holds the Spell Loom and the
  Spell Uninscription tablet; the four Iron's-gated tablets are absent, as they already were.

### Added: the Mana Infusion and Mana Well rituals are obtainable

- Both have existed as registered rituals since 1.x with **no tablet item whatsoever**, so
  neither could ever be placed on a Ritual Brazier — two documented, configurable features
  that no player could reach. The README said as much and left them pending a keep-or-remove
  decision; they are now kept and finished.
- Each gets a tablet item, artwork, lang entries, and an Enchanting Apparatus recipe. Mana
  Infusion: blank parchment reagent, plus a source gem block, a source gem, an Iron's arcane
  essence and an archwood log, 1500 source. Mana Well: the same minus the source gem, plus a
  water bucket, 2000 source. Both are Iron's-gated, like the rituals themselves.
- Behaviour is unchanged from what the config keys always described: Mana Infusion grants
  `ritual_mana_infusion_amount` once to the nearest player on completion; Mana Well regenerates
  `mana_well_regen_rate` per tick to everyone inside `mana_well_range` for its duration. Both
  pay into whichever pool the active mana unification mode treats as primary.

### Fixed: one Ars spell drained the whole mana pool on hybrid mode

- Iron's `MagicData.setMana` clamps **every** write down to the player's `max_mana` attribute,
  and `addMana` is just `setMana(mana + delta)` (verified identical in Iron's 3.15.0 and
  3.16.3). A ceiling below the current pool therefore does not cap mana — it deletes the
  difference on the next write, whatever that write happens to be.
- In HYBRID, ANS never put Ars's real max into that attribute. Only the *gear-derived* slice
  was carried across, so a spell book tier or glyph bonus raised the pool the player was shown
  without raising the ceiling that governs writes, and the first cast — of any cost — collapsed
  the pool. `CHANGELOG.md` for 3.0.x claimed this was handled "in all shared-pool modes"; only
  `ARS_PRIMARY` actually did it.
- The shared pool now has a single ceiling, driven to `max(Ars max, Iron's own max)`. The
  shortfall is measured against Iron's max with the ANS modifier removed, so Iron's own gear
  and upgrade orbs are neither voided nor double-counted. `respect_armor_bonuses` no longer
  suppresses it in HYBRID: that toggle governs bonuses, and this is the difference between
  limiting mana and destroying it.
- The ceiling modifier is transient, so it was silently lost whenever the `ServerPlayer` was
  rebuilt. Dimension changes now re-apply it (every other lifecycle handler in the mod already
  listened for that event; this one did not), and a cheap check immediately before any
  deduction re-applies it if it has drifted — so a cast can never destroy mana even if some
  other recompute lagged.
- `IronsBridge.consumeMana` now verifies its own arithmetic and logs once at WARN if a
  deduction ever loses more than its cost, naming the ceiling. It previously had no way to
  tell a correct deduction from a wipe.
- The Ars-side cost was validated with `(float)(cost × rate)` and charged with
  `(int) Math.round(cost × rate)`. Both now go through one helper, so the amount charged is
  the amount checked — and at the config's 0.01 rate floor, spells under 50 mana are no longer
  rounded down to free.

### Fixed: ritual tablets rendered as the missing-texture checkerboard

- The three ritual tablets have shipped with **no item model and no texture at all** since the
  1.20.1 line was reconstructed at 3.0.0: `models/item/` held only the Spell Loom, and
  `textures/item/` did not exist. All three are craftable, so players could obtain items that
  render as purple-and-black in the inventory and on the brazier. Nothing failed at build time
  and nothing logged an error.
- Five 16×16 tablet textures are now shipped, drawn to sit alongside Ars Nouveau's own tablets
  (a chamfered grey plaque carrying one coloured glyph) and generated reproducibly by
  `tools/gen_ritual_tablets.py`.
- The eight `ars_cross_*` spell-wheel icons Iron's resolves by convention
  (`textures/gui/spell_icons/ars_cross_<k>.png`) are now shipped as a fallback. The mixin that
  overrides them is declared `require = 0`, so a signature change on Iron's side would have
  silently produced eight checkerboards in the wheel with no error at all.
- A test now walks every registered item and fails the build if its model or texture is
  missing, and a GameTest asserts every ritual has a tablet the brazier can resolve.

### Fixed: hovering a Spell Loom scroll could crash the client

- `CrossSpellTooltipHandler` runs on every hover of an ANS-inscribed item and was the only
  event handler in the mod with no top-level exception guard. A throw there does not degrade to
  a missing tooltip line — it propagates out of `ItemStack.getTooltipLines` into the render loop
  and takes the client down. Its inner guard also caught only `Exception`, which sails past the
  realistic failure in a large modpack: a linkage `Error` from an Ars Nouveau version or
  addon-glyph skew reached through `Spell.fromTag`. It now catches `Throwable`, skips the lines,
  and logs once with the offending item and payload.
- `IronsScrollFactory` treated "the container key is present" as "the container is valid", while
  `ISpellContainer.get` bottoms out in `DataResult.getOrThrow`. A carrier whose container existed
  but did not decode would have been declared healthy and then thrown on every read Iron's made
  of it. Validity is now established by decoding, and the legacy repair path uses the same test.
- `MixinScrollItem`'s `spell == null` guard was unreachable — an empty container yields
  `SpellData.EMPTY`, whose `getSpell()` is a real `SpellRegistry.none()`. Right-clicking an ANS
  carrier therefore ran Iron's whole scroll cost/LP path against `NoneSpell`. It now tests for
  the none spell, the same oversight this release already documented for `doInscription`.
- Note on scope: Iron's own scroll-tooltip path is guarded in both 3.15.0 and 3.16.3, so the
  carrier's container state is not what crashed there. The exact throw could not be reproduced
  from code alone without the reporter's crash log, so the fix hardens every ANS dereference on
  that path and turns any remaining occurrence into a log line plus a reproducible GameTest.

## [3.1.0] - 2026-08-13

### Fixed: Iron's Inscription Table crashed on ANS-exported scrolls

- Exported carriers were built as a bare scroll stack with only ANS sidecar NBT, satisfying Iron's
  `instanceof Scroll` while breaking its unwritten "a scroll has a spell container" invariant.
  `ISpellContainer.get` returns null for such a stack and Iron's dereferences it **unguarded in
  three places** — one on the client (`InscriptionTableScreen.onInscription`, the reported NPE) and
  **two on the server** (`InscriptionTableMenu.clickMenuButton` and `doInscription`). On a
  dedicated server this was a crash any player could trigger with a legacy scroll, not just a
  client-side annoyance. Verified identical in Iron's 3.15.0 and 3.16.2, so it was never version
  drift.
- Carriers now receive a valid empty single-slot container before being handed out, and export
  returns nothing at all rather than an invalid real scroll if that fails.
- A shared guard rejects both a container-less legacy carrier and a well-formed ANS carrier at the
  native table, on client **and** server. The second rejection also prevents a quieter corruption:
  a valid empty container makes `getSpellAtIndex(0)` return `SpellData.EMPTY`, whose `getSpell()`
  is a real `SpellRegistry.none()`, which `doInscription` would have written into the book while
  consuming the scroll.
- Legacy carriers already sitting in chests are repaired in place when something already holds
  them (binding, casting, the inscription guard) — lazily, never by scanning inventories.

### Fixed: unbinding left orphan entries in Iron's spell wheel

- `IronsProxySlotWriter.removeProxySlot` had **zero callers**. Uninscribing cleared the ANS sidecar
  and left the native wheel slot behind: selectable, and casting nothing — the binding bug's mirror
  image. Teardown now removes native slots first, since the pool ids live in the sidecar that was
  being cleared. Custom names and third-party NBT are deliberately left alone.

### Fixed: Virtue Ring made Ars spells free when its system was disabled

- The mixin that cancels mana checked only whether the ring was worn, while the handler that
  consumes aura checked `enable_virtue_aura_system`. With the toggle off, nothing took aura and
  nothing took mana. Both halves now read one predicate, applied consistently across mana
  expenditure, pre-cast validation, and the scroll path.

### Fixed: `ars_cross_*` proxy spells polluted JEI, EMI and the creative menu

- Iron's builds one scroll per enabled spell into its Scrolls creative tab, which is where JEI and
  EMI both source their item lists — so filtering at the tab clears every recipe viewer at once and
  cleans up the creative menu. Proxies additionally declare `allowCrafting=false` (removing a
  nonsense Scroll Forge craft), and an optional client-only JEI plugin hides the Arcane Anvil
  recipes that no flag reaches. The spells stay enabled: disabling them would break every
  already-bound spellbook, which resolves them by id.

### Changed: spell-school classification rebuilt on Ars Nouveau's own metadata

- School is now a closed enum whose every value maps 1:1 onto both an affinity type and an Iron's
  spell-power attribute. The old string heuristic could return `aqua`, `geo` or `wind` — values
  with neither — so those spells silently received no affinity and no elemental scaling.
- Resolution consults an explicit glyph mapping, then `AbstractSpellPart.spellSchools` (which
  vanilla Ars populates in its constructor and Ars Elemental populates explicitly), then a
  substring fallback. Multi-school glyphs resolve deterministically instead of by hash order.
- Scaling now consumes the same school as everything else; it previously re-derived the element
  with a *different* heuristic, so the Firework glyph counted as generic for affinity but matched
  "fire" for scaling.
- Filter glyphs no longer decide a spell's school. `AbstractFilter` extends `AbstractEffect`, so
  `Projectile → Sensitive → Ignite` was being classified by *Sensitive*.
- Addon glyph mappings are datapack-driven via `data/<ns>/ans_glyph_schools/*.json`, so supporting
  a new addon no longer means editing Java.

### Fixed: removing an addon silently changed bound spells instead of failing

- `Spell.fromTag` skips glyphs whose mod is gone and `isValid()` only checks non-emptiness, so an
  exported `Projectile → Ignite → WaterGrave` became `Projectile → Ignite` after uninstalling Ars
  Elemental: shorter, still "valid", still castable, doing something other than what the player
  built — at full price. Payload integrity is now checked against the registry before
  deserialising, at both the bind and cast gates, with a message naming the missing glyphs.

### Changed

- Spell-book detection uses Iron's published `ISpellbook` interface plus the
  `ars_n_spells:irons_spell_books` tag, replacing a registry-path substring test. The tag shipped
  in 3.0.1 but no code had ever consulted it.
- Gameplay transaction expiry (scroll costs, Iron's LP, death prevention) uses server game time
  instead of wall-clock, so a lag spike or a paused world can no longer expire a staged cost while
  its commit is still pending. Cache TTLs and log rate-limiting keep wall-clock deliberately.
- ANS-owned item NBT carries a schema version for future migrations.
- `scroll_cost_mode` documentation corrected: a Cursed Ring wearer pays LP **instead of** mana, as
  in normal casting, never both. The behaviour was already correct; the wording implied otherwise.
- Removed an unreachable curio-discount getter whose cache-miss path returned a stub "no
  discounts", which would have silently mis-answered the first caller to use it.
- Optional GameTest profiles for Ars Elemental 0.6.8.0 and Too Many Glyphs, run in all six
  combinations; addon glyphs are verified to round-trip, resolve schools from declared metadata,
  and survive mixed-addon recipes.

## [3.0.3] - 2026-07-22

> **Provenance note.** 3.0.3 was published to CurseForge (file `8490162`, SHA-256
> `cc580f83…13610c3`) but was never committed or tagged in this repository. This entry and the
> matching source were reconstructed from the published artifact: the jar was decompiled, the
> delta against 3.0.2 was ported back into source, and the rebuild was verified to produce a
> class-for-class match with identical public signatures. See `NEXT_MAJOR_UPDATE_PLAN.md` §2 for
> the full audit. The reconstruction differs from the published jar only in compiler-generated
> temporary names, one method declaration order, one log format string, and one extracted private
> helper in the test driver.

### Fixed: a bound Ars spell could be selected in Iron's wheel and cast nothing

- **The book could not be found.** `ArsCrossProxySpell.onCast` read only
  `MagicData.getPlayerCastingItem()`, which comes back empty for a spellbook worn in the Curios
  spellbook slot — the normal way to carry one. The proxy then returned silently, so the wheel
  entry selected, played no effect, and logged nothing. Resolution now falls back through the
  equipped spellbook, main hand, and offhand, and each candidate must actually carry a sidecar
  entry for that pool id, so a player holding two bound books can never resolve to the wrong one.
- **Binding could half-succeed.** `IronsBookBindingUtil.appendArsSpellToBook` discarded the result
  of the native proxy-slot write and always reported `ADDED`. A failed native write left a sidecar
  entry with no wheel slot: invisible, uncastable, reported as success. The sidecar entry is now
  rolled back (`CrossCastNbt.removeEntryByProxyPoolId`) and the bind returns `FAILED`.
- **Both failure modes are now audible.** Each surfaces a WARN naming the pool id, the resolved
  book, and the entry count, plus a translated action-bar message for the player
  (`arsnspells.crosscast.proxy.book_missing`, `arsnspells.crosscast.proxy.entry_missing`).

### Fixed: casting an Ars spell through Iron's wheel also ran Iron's own accounting

The `ars_cross_*` proxies are zero-cost `ENDER`-school placeholders whose real cost, school and
cooldown belong to the delegated Ars cast. Iron's-side handlers were treating them as genuine
Iron's spells, so a single cast could be billed twice and filed under the wrong school. New
`CrossCastNbt.isArsCrossProxyId` now short-circuits `IronsAffinityHandler`, `IronsCooldownHandler`,
`IronsLPHandler` (pre-cast and on-cast), `IronsProgressionHandler`, and `CrossCastIronsHandler`.

### Fixed: unreadable payloads bound and cast instead of failing

- `IronsBookBindingUtil.isCastableArsPayload` deserializes and requires a non-empty recipe with a
  cast method. The bind command and the Spellbook Binding ritual both check it **before** anything
  is consumed, so a payload written by a different Ars version (or one whose glyph mod was removed)
  is refused with a translated message instead of binding a silent dud.
- `CrossCastingHandler` rejects an invalid deserialized spell before opening the cast context, so
  no resource is spent.
- The ritual's native-write failure now reports against the **book**
  (`…error.bind_failed`) rather than telling players to re-export a scroll that parsed fine.

### Changed

- `/ans bind_scroll_to_irons_book` now reads the whole carrier entry rather than just the spell
  payload: it honours `max_ars_cross_spells_per_irons_spellbook`, forwards the Spell Loom's chosen
  name/nature/icon onto the book, and reports `DUPLICATE` / `BOOK_FULL` / failure distinctly
  instead of collapsing every outcome into one message.
- Iron's spell-wheel icon and name lookup checks the equipped spellbook slot before the hands,
  which is where a bound book actually sits while the wheel is being rendered.
- New GameTests drive real Iron's cast machinery (`IronsProxyCastDriver`) and assert an observable
  world effect rather than NBT shape: Curios-slot cast, survival cast with mana, hand fallback,
  no-op without a sidecar entry, bind-command rejection (with a positive control), and a guard
  asserting Iron's still reports an empty casting item for spellbook casts — the assumption the
  fallback chain exists for.

### Known issue, still open in this release

- Exported scrolls are created without a native Iron's spell container, so putting one in Iron's
  Inscription Table and pressing Inscribe throws an NPE. `ArsSpellExportUtil` is byte-identical
  between 3.0.2 and 3.0.3. Tracked in `NEXT_MAJOR_UPDATE_PLAN.md` §4.1.

## [3.0.2] - 2026-07-07

### Fixed: affinity decay ran ~20x faster than documented (audit D1)

- `AffinityDecayHandler` floored the per-interval proportional amount and clamped it to a minimum of 1, so every non-zero school lost a flat point per interval regardless of level — a maxed school emptied in ~5 in-game days instead of the documented proportional curve. Fractional decay now accrues in a per-school residual ([DecayAccumulator.java](src/main/java/com/otectus/arsnspells/data/DecayAccumulator.java), persisted in the capability NBT, survives relogs, sanitizes corrupt values) and a point is removed only once a whole point accumulates. At defaults a level-100 school now loses one point per ~20 real minutes, slowing proportionally as it drops. Only affects installs with `enable_affinity_decay = true` (default off).

### New: `aura_failure_mode` — control fail-open free casts (audit D2)

- When the Covenant/Nature's Aura reflection bridge is degraded (e.g. an untested Covenant update), aura-cost checks previously always failed OPEN: Virtue Ring casts became free, silently. New server config `aura_failure_mode` under `Virtue Ring`: `open` (default, historical behavior) or `closed` (block Virtue Ring casts until the bridge works). Either way the first degraded decision per session now logs at WARN.

### New: datapack tags replace hardcoded compat IDs (audit F-1/F-2)

- Ring, Blasphemy, and Source Jar detection is now tag-driven: item tags `ars_n_spells:cursed_rings` (Covenant + Enigmatic Legacy rings), `ars_n_spells:virtue_rings`, `ars_n_spells:blasphemy_curios`, and block tag `ars_n_spells:source_jars` (Ars source + creative source jars). All shipped entries are `required: false`. Pack makers can add custom rings/blasphemies/jars without a code change; Blasphemy school matching is now namespace-agnostic (item path `<school>_blasphemy`). The Source Jar scan also got cheaper per block (tag lookup instead of registry-key + substring).

### New: advancement chain for the cross-cast workflow

- Four advancements guide the loom pipeline: **Warp and Weft** (craft the Spell Loom) → **Written in Starlight** (inscribe a scroll at the loom) → **A Foreign Chapter** (bind it into an Iron's spellbook, via ritual or `/ans bind_scroll_to_irons_book`) → **Two Schools, One Voice** (cast an Ars spell through Iron's, sidecar or native wheel).

### Changed

- Untested Covenant of the Seven versions now surface a once-per-session gold chat notice on login (the HUD mixin degrades silently on version drift; the log-only warning was invisible to affected players).
- Progression bonus curve is config-driven: `progression_bonus_per_cast` (default 0.001) and `progression_bonus_cap` (default 0.25) replace the hardcoded values (audit F4).
- Spell-school classification consults an explicit glyph→school map (verified against Ars 4.12.7) before the registry-path substring heuristic; fixes the Firework glyph classifying as fire school via the "fire" substring (audit F8).
- `ResonanceManager` and the Source Jar synergy boost no longer swallow exceptions silently — first failure per session logs at WARN so "feature silently does nothing" regressions are diagnosable (audit D4).
- `mods.toml` gains `issueTrackerURL`, `displayURL`, and `updateJSONURL` (Forge update checker via `update.json` in the repo).
- Player capability NBT now carries an `AnsDataVersion` schema field for future migrations (audit E3).
- CI: the Iron's-less GameTest job is now blocking and verifies the "All N required tests passed" log line instead of trusting the gradle exit code (audit E7).
- `AnsConfig.safeSave()` returns `void` — the old unconditional `true` read as "save succeeded" at call sites (audit D5).

### Spell Loom screen readability + usability rework

- The Spell Loom screen now uses the same high-contrast treatment as the config screen: opaque bordered panel with bevels, shadowed labels, and per-slot chrome, so blur mods/shaders can no longer wash it out. The GUI grew to 176×208 and all slot/widget coordinates now derive from named layout constants in [SpellLoomMenu.java](src/main/java/com/otectus/arsnspells/menu/SpellLoomMenu.java) — a single source of truth shared by menu and screen (the recipe row moved from y=35 to y=40).
- New localized hover tooltips explain each region: source slot, scroll slot, output slot, spell preview, nature picker, icon picker, and the Inscribe button (`screen.ars_n_spells.spell_loom.tooltip.*`). The Inscribe button now mirrors the server-side validation client-side and reports the first blocking problem (including a new "output slot is full" message) instead of failing silently — the server checks remain authoritative and unchanged.
- The spell-name box now ticks properly (cursor blink) and survives window resizes without losing typed text.
- README and CurseForge description updated to match (Spell Loom workflow, Iron's "any tiered spellbook" wording from the 3.0.1 tag fix, requirements table).

### Fixed config screen readability/blur issue

- The in-game config screen now paints its own near-opaque background and a bordered content panel instead of relying on the vanilla translucent dim — client blur mods/shaders (the "frosted glass" look) can no longer make it unreadable. Rows have background stripes with hover highlight, high-contrast shadowed text, and the ON/OFF and Mana Mode controls are drawn as real bordered buttons whose hitboxes exactly match the drawn rect (previously a 20px hitbox floated over a 35px row). Long descriptions truncate with a hover tooltip instead of overflowing. ([ConfigScreenFactory.java](src/main/java/com/otectus/arsnspells/config/ConfigScreenFactory.java))
- **Fixed: boolean toggles were clickable in multiplayer read-only mode** — the click silently mutated only the client's SERVER-config mirror (cycle rows were already gated). All row controls are now gated on `canMutate`, render disabled on dedicated-server clients, and the screen shows "Read-only: server-managed config. Edit the server TOML or use /ans commands."
- "Reset Toggles" now resets `enable_cooldown_system` to its actual TOML default (`false`); it previously set it to `true`.

### Source Jar synergy: kill switch + tuning (follow-up to the ANS-CRIT-005 chunk-load deadlock fix)

- Fixed in 2.6.2/3.0.0 and hardened here: the Source Jar synergy scan could touch (and synchronously load) chunks during player tick in ≤2.6.1, which could contribute to chunk-load deadlocks. The scan now remains guarded by a non-loading `hasChunk` check, and this release adds server-owner controls around it.
- New keys under `Source Jar Synergy`: `enable_source_jar_synergy` (default `true` — the supported off switch; when `false` the periodic scan is skipped entirely), `source_jar_scan_interval_ticks` (default 20, range 1–200), and `source_jar_scan_radius` (default 4, hard cap 8 — at most a 2×2 chunk area). Existing TOMLs are auto-upgraded with defaults; no keys renamed. The multiplier keeps its 0.1 minimum — use the kill switch to disable, not a zero multiplier. The kill switch is also exposed as a toggle in the config screen.
- The chunk-coverage math behind the scan guard moved to [ChunkScanUtil.java](src/main/java/com/otectus/arsnspells/util/ChunkScanUtil.java) with direct unit coverage; skipped scans are still retried and never cached.
- With `debug_mode = true`, a rate-limited counter summary (scans run / skipped due to unloaded chunks / jar hits, plus a slow-scan warning) is logged at most once per minute — counters only, never per-block.

### Fixed: crash at boot on installs without Iron's Spellbooks

- `MixinArsPotionEffects` targets an Ars Nouveau class but its bytecode references Iron's `AttributeRegistry`; on an Iron's-less install the mixin processor failed with `ClassMetadataNotFoundException` while transforming `ManaCapEvents`, which made Ars Nouveau itself fail to load and killed the server during startup. The mixin is now gated on Iron's presence in [ArsNSpellsMixinPlugin.java](src/main/java/com/otectus/arsnspells/mixin/ArsNSpellsMixinPlugin.java) — it only has an effect when Iron's is the primary mana system, so nothing is lost. (Found because the Iron-less GameTest profile could not boot.)

### Full-codebase audit remediation

Remediation from the 3.0.1 full-codebase audit (see [AUDIT_FINDINGS.md](AUDIT_FINDINGS.md), [AUDIT_ARCHITECTURE.md](AUDIT_ARCHITECTURE.md)).

#### Fixed: transcription/binding ritual tablets were uncraftable when Iron's is loaded

- Both apparatus recipes used pedestal ingredient `irons_spellbooks:spell_book`, which is **not a registered item** — Iron's registers only tiered books (`copper_spell_book`, …, `legendary_spell_book`). With Iron's installed, both recipes failed to parse (`JsonSyntaxException: Unknown item`) and the Spell Transcription / Spellbook Binding tablets could not be crafted. The recipes now accept any tiered Iron's spell book via a new shipped item tag [`ars_n_spells:irons_spell_books`](src/main/resources/data/ars_n_spells/tags/items/irons_spell_books.json) (all entries optional, so the tag also loads cleanly on Iron's-less installs). Pack makers can override the tag to restrict tiers.
- `pack.mcmeta` now declares the correct 1.20.1 data-pack format (15; was 12).

#### Fixed: LP cost participants gated on inconsistent config toggles

- `MixinSanctifiedAbstractSpell` bypassed Covenant of the Seven's native LP/death check based on `enable_mana_unification` instead of the LP system's own `enable_lp_system` master toggle: with unification off + LP on, both ANS and Covenant processed the cost (the double-penalty/instant-death interaction the bypass exists to prevent); with unification on + LP off, Covenant was bypassed with nobody charging LP. The scroll LP path in `MixinScrollItem` had no LP-system gate at all, so scrolls kept charging LP with the system disabled. Both now key off `enable_lp_system` (default unchanged: `true`).

#### Changed

- Mode-dependent features (equipment mana bridging, Source Jar synergy, mana-bar hiding) now consistently respect `mana_unification_mode = "disabled"` even when the `enable_mana_unification` master toggle is still `true` — previously each site re-implemented the precedence check and this state behaved inconsistently. `BridgeManager.isUnificationEnabled()` is the documented single source of truth.
- The three scroll LP action-bar messages are now translatable (`message.ars_n_spells.lp.*`); English text unchanged.
- `ConfigScreenFactory` moved from the common `config` package to `client/screen` (it is a client `Screen`; the old location risked dedicated-server classloading if ever referenced from common code).
- Removed the redundant `MixinIronsManaBarOverlay` — `ManaBarController` already cancels Iron's mana-bar overlay via `RenderGuiOverlayEvent.Pre` before the mixin's injection point could ever run. Visual behavior unchanged.
- Removed the dead `IronsLPHandler.storePendingScrollLP` (no callers; scroll costs stage through `ScrollLPTracker`). If any pack invoked it reflectively, that call will now fail to resolve.

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

2.0.0 addresses the comprehensive technical audit (planning doc retired; see AUDIT.md). Every High and Medium-High hypothesis in the audit's "Ranked Root-Cause Hypotheses" maps to a closed fix below. The major-version bump reflects two breaking changes (new C2S packet ID; clients older than 2.0.0 cannot cross-cast against a 2.0.0 server) plus the architectural reorganization of the cross-cast pipeline.

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
