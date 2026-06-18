# Testing Guide — Ars 'n' Spells 2.6.1 (NeoForge 1.21.1)

This guide covers manual verification scenarios for Ars 'n' Spells **on NeoForge 1.21.1**. It supersedes the pre-port Forge 1.20.1 testing notes (which documented the Sanctified Legacy / Covenant of the Seven ring systems — those integrations are removed in this release; see [README §Removed](README.md#removed-in-the-neoforge-1211-port) and the [CHANGELOG](CHANGELOG.md) for context).

For an at-a-glance description of features and configuration, start with the [README](README.md). For the change history, see [CHANGELOG.md](CHANGELOG.md).

> **Status.** The gameplay systems are live (mana unification, cross-cast, rituals, affinity, progression, resonance, cooldowns, equipment scaling). The build environment runs no Minecraft, so every scenario below is a **manual in-game** check that has not yet been run for 2.6.1. **2.6.1 priorities:** the restored config screen (V11), Ars mana-potion mirroring (V12), the mana-only pre-cast check (V13), and the debug overlay (V14). Carried-over priorities: per-school affinity now covers Iron's addon schools (cast one spell from each addon school → `/ans info` shows a track under its full id); a 2.0.x save migrates its affinity (elemental tracks keep their counts, legacy category buckets are dropped); the ritual apparatus recipes are craftable again and the curio-discount tag applies on both the Ars and Iron's sides.

## Prerequisites

| Mod | Version | Required for tests below |
| --- | --- | --- |
| Minecraft (NeoForge) | 1.21.1 / 21.1.84+ | All tests |
| Java | 21 | All tests (build + runtime) |
| Ars Nouveau | 5.11.1+ | All tests |
| Iron's Spells 'n Spellbooks | 1.21.1-3.15.6+ | "Ars + Iron's" tests, scaling, progression |

The previous Forge 1.20.1 testing guide also referenced Covenant of the Seven (Sanctified Legacy) and Blood Magic. Both prerequisites are gone — see [CHANGELOG §Removed](CHANGELOG.md).

Drop `build/libs/ars_n_spells-2.6.1.jar` into the instance's `mods/` folder alongside the dependencies. The Gradle dev environment exposes `runClient`, `runServer`, `runGameTestServer`, and `runData` tasks.

To enable verbose log output during testing, set in `config/ars_n_spells-common.toml`:

```toml
debug_mode = true
```

This adds `[Cooldown]`, `[Affinity]`, and similar log prefixes to most event paths.

## Verification gates (G1–G7)

Run these gradle tasks against the worktree before any manual scenario. Each gate must pass.

| Gate | Command | Pass criterion |
| --- | --- | --- |
| **G1 — Build** | `./gradlew --refresh-dependencies clean build` | `BUILD SUCCESSFUL`, 0 compile errors, no `mods.toml` references. |
| **G2 — JUnit** | `./gradlew test` | All test classes pass. `CrossModSpellListRoundTripTest` is in place; `InscriptionInputsPredicateTest` is deferred to Phase 3. |
| **G3 — Datagen** | `./gradlew runData` | Task exits 0; `src/generated/resources/` exists. |
| **G4 — Mixin apply (Ars only)** | `./gradlew runClient` without Iron's | The 3 Ars-only mixins apply (`MixinManaCapability`, `MixinSpellResolverMana`, `MixinSpellResolverContext`); `MixinArsPotionEffects` and the Iron's mixins skip silently (they need Iron's); no `Mixin apply failed`. |
| **G5 — Mixin apply (Ars + Iron's)** | `./gradlew runClient` with both pinned | All 6 active mixins apply (the 3 Ars mixins + `MixinArsPotionEffects` + `MixinIronsSpellDamage` + `MixinIronsMagicDataMana`). |
| **G6 — Dedicated server (Ars only)** | `./gradlew runServer` without Iron's | Server reaches `Done (…)! For help, type 'help'`, 0 stack traces, 0 `NoClassDefFoundError` for `io.redspace.*`. |
| **G7 — Dedicated server (Ars + Iron's)** | `./gradlew runServer` with Iron's pinned | Same; payload registration log line visible for `affinity_sync`, `cooldown_sync`, `resonance_sync`. |

Gates G4 / G5 exercise the live mixin set; a failure means a target drifted in the pinned Ars/Iron's build (all injects use `require = 0`, so drift skips rather than crashes).

## V1 — V10 manual scenarios

These scenarios exercise gameplay equivalence to the Forge 1.20.1 build. Each is documented with a Phase-gate so you know whether to expect it to pass against the current commit.

### V1 — Boot integrity (both world configurations) — gated on G6 / G7

1. Run a dedicated server with Ars Nouveau only on NeoForge 1.21.1. Reach `Done`.
2. Repeat with Ars Nouveau + Iron's Spells 'n Spellbooks both installed.

Pass: both boots complete with 0 stack traces, 0 mixin apply failures, 0 `NoClassDefFoundError` for `io.redspace.*` symbols in the Ars-only run. Joining a player and casting any Ars spell does not crash.

Failure modes to watch for: a missed Iron's-gating call (would mean an Iron's import classloaded on the Ars-only server), `SpellScalingUtil` lazy-init crash, mixin target-not-found errors.

### V2 — Cross-cast Ars → Iron's — gated on Phase 3

1. Brazier-craft the Spell Transcription tablet with Iron's installed.
2. Inscribe Iron's `irons_spellbooks:fireball` onto an Ars parchment via the Transcription ritual.
3. Equip the inscribed parchment and right-click to cast.

Pass: Iron's fireball entity spawns; Iron's mana is consumed in HYBRID mode; Ars-side progression increments by 1.

Currently blocked on Phase 3 — [`CrossCastingHandler`](src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java) needs Ars `Spell.CODEC` serialization, `SpellCaster.castSpell`, `SpellCostCalcEvent`, and the `PlayerInteractEvent.RightClickItem` intercept re-wired against the new APIs.

### V3 — Cross-cast Iron's → Ars — gated on Phase 3

1. Inscribe an Ars `lightning + amplify` glyph chain onto an Iron's spellbook slot via the Transcription ritual.
2. Cast from the spellbook in ARS_PRIMARY mode.

Pass: Ars effect resolves; Ars mana is consumed. Same Phase 3 dependency as V2.

### V4 — Affinity decay + login sync round-trip — should pass after Phase 2

1. Build FIRE affinity to level 50 by casting fire spells. Set `affinity_decay_interval_ticks = 100` (faster cycle for testing) and `enable_affinity_decay = true`.
2. Log out, log back in. Observe HUD reflects the persisted level immediately (Login sync via [`AffinitySyncOnLoginHandler`](src/main/java/com/otectus/arsnspells/events/AffinitySyncOnLoginHandler.java)).
3. Wait several decay intervals (~25 s at the test setting); relog again.

Pass: decayed value persists. [`AffinityDecayHandler`](src/main/java/com/otectus/arsnspells/events/AffinityDecayHandler.java) is wired and uses `PlayerTickEvent.Post`, the attachment, and the `AffinitySyncPayload`. The original 1.9.0 decay math (per-day rate prorated to per-interval) is preserved.

### V5 — *(reserved; was Cursed Ring scroll under load)*

Removed. The Cursed Ring / scroll-LP feature path is deleted as part of the Sanctified Legacy removal. No replacement scenario.

### V6 — Resonance multiplier — gated on Phase 3

1. Cast an Iron's spell.
2. `/attribute @s irons_spellbooks:spell_power get`.
3. Confirm the value is base × resonance modifier; confirm the resonance payload syncs within ≤1 tick.

[`ResonanceEvents`](src/main/java/com/otectus/arsnspells/events/ResonanceEvents.java) and [`RegenSynergyHandler`](src/main/java/com/otectus/arsnspells/events/RegenSynergyHandler.java) are wired (re-attached in 2.0.1); resonance syncs on login and once per second, and Source-Jar proximity regen feeds the unified pool.

### V7 — Cooldown global-per-category — gated on Phase 3

1. Set `enable_cooldown_system = true` and `enable_cross_mod_cooldowns = true`.
2. Cast an Ars `OFFENSIVE` spell (e.g., Projectile + Ignite).
3. Within the cooldown window, cast an Iron's `OFFENSIVE` spell.

Pass: the second cast is blocked. By design — see [README §Cooldowns](README.md#cooldowns) for the global-per-category rationale.

Gated on Phase 3 — [`CooldownHandler`](src/main/java/com/otectus/arsnspells/events/CooldownHandler.java) and [`IronsCooldownHandler`](src/main/java/com/otectus/arsnspells/events/IronsCooldownHandler.java) need their cast-event hooks re-attached.

### V8 — Death cycle preserving progression — should pass after Phase 2

1. Build progression to a known value with a few Ars casts. Note `irons_spellbooks:fire_spell_power` attribute.
2. `/kill`. Respawn.
3. Re-check the attribute and the affinity level.

Pass: attribute modifier and affinity level are preserved. Probes `AttachmentType.Builder.copyOnDeath` on `AFFINITY` and `PROGRESSION` ([`AttachmentTypes`](src/main/java/com/otectus/arsnspells/data/AttachmentTypes.java)). Cooldown intentionally resets (no `copyOnDeath`).

### V9 — Config hot-reload — should pass now

1. Toggle `enable_cross_mod_cooldowns` in `config/ars_n_spells-common.toml`.
2. `/reload`.

Pass: no server crash; the new config value takes effect without restart.

### V10 — World save/load integrity — should pass after Phase 3

1. With non-zero affinity, non-zero progression, and an inscribed parchment in a chest, save the world.
2. Stop the server, restart, reload.

Pass: affinity and progression persist; the chest item's `CrossModSpellList` data component is intact and right-clicking the item still casts (Phase 3 dependency for the cast itself; the component persistence should already work).

## V11 – V14 — 2.6.1 feature checks

### V11 — In-game config screen — should pass now

1. In singleplayer, open **Mods → Ars 'n' Spells → Config**.
2. Toggle a master switch and click the **Mana Mode** row to cycle it; click **Done**.
3. Run `/ans mode` and inspect `serverconfig/ars_n_spells-server.toml`.

Pass: the screen opens, the Mana Mode row cycles through the five modes, and on **Done** the value is saved and applied live (`BridgeManager.refreshMode`) so `/ans mode` reflects the change. In multiplayer the Save/Reset controls are disabled and the button reads **Close**. Registered via `IConfigScreenFactory` in [`ArsNSpellsClient`](src/main/java/com/otectus/arsnspells/client/ArsNSpellsClient.java); screen body in [`ConfigScreenFactory`](src/main/java/com/otectus/arsnspells/config/ConfigScreenFactory.java).

### V12 — Ars mana potions feed the unified pool — should pass now (Iron's required)

1. Set `iss_primary`. Note Iron's attributes: `/attribute @s irons_spellbooks:max_mana base get` (and `irons_spellbooks:mana_regen`).
2. `/effect give @s ars_nouveau:mana_boost 999 1` (and/or `ars_nouveau:mana_regen`).
3. Re-read the attributes; then `/effect clear @s` and read again.

Pass: the Iron's `MAX_MANA` (from `mana_boost`) and `MANA_REGEN` (from `mana_regen`) values rise while the effect is active and revert when cleared — the Ars potion now raises the pool you cast from. [`MixinArsPotionEffects`](src/main/java/com/otectus/arsnspells/mixin/ars/MixinArsPotionEffects.java) targets `ManaCapEvents.playerOnTick(PlayerTickEvent.Pre)`; watch the log for **no** "mixin apply failed" on that target.

### V13 — Pre-cast mana validation — should pass now

1. Set `iss_primary`. Drain the unified mana below a spell's cost.
2. Attempt to cast an Ars spell whose cost exceeds the remaining pool.
3. Refill mana and cast again.

Pass: the under-funded cast is denied with a "Not Enough Mana" action-bar message; the refilled cast succeeds. Validation reads the bridged pool ([`CastingAuthority`](src/main/java/com/otectus/arsnspells/casting/CastingAuthority.java) + `MixinManaCapability`), not the per-mod native check. Creative mode and zero-cost spells always pass.

### V14 — Debug overlay diagnostics — client check

1. Set `debug_mode = true` and (re)launch the client; join a world.
2. Watch `logs/latest.log`.

Pass: [`OverlayDiagnostics`](src/main/java/com/otectus/arsnspells/client/OverlayDiagnostics.java) logs each GUI layer id once as `[OVERLAY] <id>`, flagging mana-related layers; `ars_nouveau:mana_bar` and `irons_spellbooks:mana_bar` appear (also confirms `ManaBarController`'s layer-id assumptions). With `debug_mode = false` there is no per-frame overlay logging. The client reads `debug_mode` at startup, so toggling needs a client relaunch.

## Inscription / cross-cast scenarios

### S15 — Strict disambiguation on the brazier — gated on Phase 3

1. Drop two filled Ars spell parchments (two sources) within three blocks of the Spell Transcription brazier.
2. Activate the ritual.

Pass: ritual fails with a chat message naming both items. Pre-1.8.9 this would either silently pick one or corrupt an item.

The ritual classifier ([`InscriptionInputs`](src/main/java/com/otectus/arsnspells/rituals/InscriptionInputs.java)) currently classifies everything as a blank target because `readSource` is a Phase 3 stub. The ritual will short-circuit on "no source" instead of producing the disambiguation message. Once Phase 3 resolves `readSource` against Ars 5.x `SpellCaster` / Iron's 3.15.6 `ISpellContainer`, this scenario becomes verifiable.

### S16 — Spell Uninscription returns a bit-identical blank — should pass now

1. Manually set the `ars_n_spells:cross_spells` data component on a target item (`/data modify entity @s SelectedItem.components` or programmatic test).
2. Run the Spell Uninscription ritual.
3. Compare the resulting item's component map to a fresh blank target.

Pass: the `ars_n_spells:cross_spells` component is gone; component map equality holds. The new [`CrossModSpellComponents.clear`](src/main/java/com/otectus/arsnspells/spell/CrossModSpellComponents.java) call removes the component entirely (rather than leaving an empty list), matching the Forge-era `tag.isEmpty() ⇒ setTag(null)` collapse.

## Removed scenarios

The following scenarios from the Forge 1.20.1 testing guide are no longer applicable:

- **S3** (Scroll cast, sufficient LP — Cursed Ring) — Cursed Ring is gone.
- **S4** (Scroll cast, insufficient LP, safe mode) — Cursed Ring LP system is gone.
- **S5** (Scroll cast, insufficient LP, death mode) — same.
- **S12** (Cursed Ring LP costs on an Ars spell) — Cursed Ring is gone.
- **S13** (Virtue Ring aura costs) — Virtue Ring + aura system are gone.
- **S14** (Blasphemy curio discounts) — Blasphemy curios are gone.

If a future Curios integration in Phase 6 reintroduces curio-driven discounts, equivalent scenarios will be added here.

## Troubleshooting

- **Server crashes during boot with `NoClassDefFoundError` for an Iron's class on an Ars-only install**: a class was missed by the classload-safety pass. Grep the stack trace for `io.redspace.ironsspellbooks` and ensure the origin class is registered behind `IronsCompat.isLoaded()` or `ModList.get().isLoaded("irons_spellbooks")`.
- **Affinity HUD shows zero after relog with non-zero attachment data**: the `AffinitySyncOnLoginHandler` didn't fire. Check that `enable_affinity_system = true` and that the player attachment is being serialized (`/data get entity @s` should show the `ars_n_spells:affinity` attachment).
- **Cooldowns blocking spells from the "wrong" mod**: this is intentional. Cooldowns are global per category by design; see [README §Cooldowns](README.md#cooldowns).
- **Cast nothing-happens on inscribed item**: confirm the item actually carries an inscription (`/data get entity @s` should show `ars_n_spells:cross_spells`), that the source mod of the inscribed spell is installed, and that you are not mid cross-cast cooldown. [`CrossCastingHandler`](src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java) handles the right-click cast, sneak-cycle between inscriptions, and the cross-cast cost multiplier; a malformed inscription shows a red rejection message rather than silently no-casting.
- **Recipes failing to load with `Unknown tag c:logs/archwood`**: confirm Ars Nouveau 5.11.1+ is installed — it ships the `c:` namespace common tag set. If Ars's tag still uses `forge:logs/archwood` on your specific build, fall back to `forge:logs/archwood` temporarily.
- **Two mana bars showing**: bar hiding is done natively by [`ManaBarController`](src/main/java/com/otectus/arsnspells/client/ManaBarController.java) (it cancels `RenderGuiLayerEvent.Pre` per mode), not by a mixin on Iron's overlay. In `separate` / `disabled` both bars show by design; otherwise confirm the layer ids `ars_nouveau:mana_bar` / `irons_spellbooks:mana_bar` are what render (enable `debug_mode` and read the `[OVERLAY]` log lines from V14).

## Reporting issues

Include in the bug report:

1. NeoForge version (must be 1.21.1 / 21.1.84+), Minecraft 1.21.1.
2. Ars Nouveau and Iron's Spells 'n Spellbooks versions.
3. The relevant `config/ars_n_spells-common.toml` keys.
4. `logs/latest.log` excerpts. With `debug_mode = true` enabled, look for log lines prefixed `[Cooldown]`, `[Affinity]`, or messages from the mixin / payload register lifecycle.
5. The exact reproduction steps tied to one of the scenarios above where possible.
6. If the issue may be Phase 3-related, mention which `TODO(Phase 11)` marker (if any) is in the suspected code path.
