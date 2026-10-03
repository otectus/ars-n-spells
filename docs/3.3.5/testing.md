# 3.3.5 testing (NeoForge 1.21.1)

The runs under "Final results" were made on 2026-09-27. The payment-boundary fix of 2026-09-28 changed the source afterwards. Its section below records the runs made after it, which replace the rows they repeat. The channel payment fix is shared with the Forge build. The Forge checkout's `docs/3.3.5/runtime-reconciliation.md` explains that failure, which came from a Forge pack.

## Payment-boundary fix (2026-09-28)

A server running ANS 3.3.4 logged eleven `[CastPayment]` warnings for one player casting `irons_spellbooks:scorch/9` from a spellbook. The server ran NeoForge 21.1.250, Iron's 1.21.1-3.16.3, Ars Nouveau 5.13.1 and 412 mod files. The mode was `iss_primary`, and then `hybrid` after an operator changed it. Every line read `stage=aborted_before_effect`, `reason=NATIVE_FAILURE`, movements 100 → 10 → 100, `exception=java.lang.IllegalStateException: Effect boundary not reached`. The spell never fired.

### Cause

Iron's `AbstractSpell.castSpell` posts the final `SpellOnCastEvent`, then runs its own mana block (`MagicData.getMana()`, then `setMana`), then `onCast`. The same order holds in every Iron's build checked: 1.21.1-3.16.3, 1.20.1-3.15.0 and 1.20.1-3.16.3, which the checkouts build and test against, and the cached 1.20.1-3.15.4 and 3.16.1 jars. Each has exactly one of those calls in `castSpell`. ANS 3.3.4, and 3.3.5 until this fix, took the price right after the event and only suppressed Iron's later write.

Animus for NeoForge 1.21.1 (`animusnv-1.21.1-5.2.13`, in the reported pack) injects at that `getMana()` call (`AbstractSpellMixin.animus$payEV`, a required mixin config). When the pool holds less than the event's cost, it tries to pay the difference from the player's NeoVitae blood-magic network. When it cannot, it cancels `castSpell`. With 100 mana and a cost of 90, ANS took 90 and Animus saw 10. Animus cancelled the cast, and ANS refunded the 90 and reported the missing effect as a fault. Any spell costing more than half the remaining pool failed this way; cheaper spells passed. With blood casting enabled, Animus would instead have drained essence for mana ANS had already taken. 3.3.3 did not show the failure, because it set the event's cost to 0 after paying.

### Change

- ANS prices the cast after the event (`IronsCastPayments.price`) and takes the payment at Iron's `setMana` call, which the payment replaces (`MixinIronsCastPayment.arsnspells$nativeWrite`). A scroll in `full` cost mode, which Iron's never debits, still pays right after the event.
- After pricing, the event's cost states what ANS will take from the pool `MagicData.getMana()` reads, rounded down. That is the converted amount in `ars_primary`, the Iron's share of a dual-cost split, and 0 for exempt casts such as bound Ars spells. A check inside Iron's mana block therefore compares like with like.
- A cast another mod ends before its effect is that cast's native outcome. ANS returns anything held marks any such refund `NATIVE_VETO`, and counts the veto. It shows no message and logs no warning; debug mode logs `stage=vetoed_before_effect`. Iron's own completion runs as it would without ANS.
- If another mod skips Iron's mana write, the cast is free by that mod's decision and ANS takes nothing. Debug mode traces it as `native_write_skipped`.
- The startup self-check names the handlers each target needs, and now covers the payment and cast-ticker mixins. Before, any ANS method on `AbstractSpell` passed, so a failed payment mixin would not have been reported.
- The payment hook reads Iron's locals by type with MixinExtras `@Local`, instead of capturing the whole local variable table.

### Reproduction and tests

`mixin/gametest/MixinCastProbe` injects at the same `getMana()` call as Animus. It exists for GameTests only. `ArsNSpellsMixinPlugin` applies it only when the JVM property `ans.gametest.castProbe` is `true`, which `build.gradle` sets for `runGameTestServer` alone. A unit test checks that gate. Four new tests in `NativeCastPaymentGameTests` arm the probe for their own player:

| Test | What it shows |
| --- | --- |
| `ironsLoaded_thirdPartyAffordabilityCheckSeesUndebitedPool` | An Animus-style check, in all five modes at conversion rates 0.5, 1 and 3. A native heal funded at 1.2 times its price resolves, pays once and starts the native cooldown, with no failure or veto. |
| `ironsLoaded_thirdPartyTopUpBeforeNativeWriteIsHonoured` | A mod that raises the pool to the cost inside Iron's mana block funds a heal started with half its price, in all five modes. |
| `ironsLoaded_thirdPartyVetoBeforeEffectRefundsSilently` | An unconditional cancel: no heal, no charge, no cooldown and no payment failure. One veto is recorded and the cast state is cleared. |
| `ironsLoaded_channelPulsesPayAtNativeWrite` | `fire_breath` under the affordability check, funded for three and a half pulses. Three pulses are paid and the channel ends as Iron's final pulse, with no failure. |

With only the probe and these tests added, before the fix, the Iron's-loaded run failed exactly these four tests, 4 of 132 required:

- "an affordable cast was cancelled by a check inside Iron's mana block disabled rate 0.5"
- "cast probe ran 0 times, expected 1 (disabled)": ANS refused the underfunded cast at the event, before the top-up could run
- "another mod's veto is not an ANS payment failure"
- "three affordable pulses paid: expected 2.5, got 7.5": the last affordable pulse was refunded and the channel stopped

That log showed the reported `Effect boundary not reached` warning. After the fix, all 132 passed and the logs contain no such line.

### Results after the fix

Each profile ran in a new `-PgametestRunDir` folder.

| Check | Command | Result |
| --- | --- | --- |
| Build and unit tests | `./gradlew build --offline` | Passed. JUnit: 888 tests, 0 failures, 0 errors, 0 skipped. |
| GameTests, Iron's loaded | `./gradlew runGameTestServer --offline -PwithIronsRuntimeGameTests -PgametestRunDir=run-335-scorch-loaded` | All 132 required tests passed. Optional scenarios: 78 run, 17 not exercised. Iron's reported as `1.21.1-3.16.3`. The self-check line reads `AbstractSpell.payment=OK`, `MagicManager.ticker=OK` and `MagicManager.regenScope=OK`. |
| GameTests, Iron's absent | `./gradlew runGameTestServer --offline -PgametestRunDir=run-335-scorch-absent` | All 132 required tests passed. Optional scenarios: 1 run, 94 not exercised. |
| GameTests, Iron's and Ars Affinity | `./gradlew runGameTestServer --offline -PwithIronsRuntimeGameTests -PwithArsAffinity -PgametestRunDir=run-335-scorch-affinity` | All 132 required tests passed. Optional scenarios: 86 run, 9 not exercised. |
| GameTest log check | `python3 tools/verify_gametest_log.py <log> --required 132 --executed <n> --skipped <n> --present/--absent <mod>…` | All three logs verified. |
| Loader parity | `python3 tools/verify_loader_parity.py --forge <Forge checkout>` | Passed: 33 contract files, 54 config fields, 1458 resources. |
| Tool tests | `python3 -m unittest discover -s tools -p 'test_*.py'` | 27 tests passed. |

Not re-run after the fix: Ars Affinity without Iron's, and the two config-migration checks. The fix touches neither path.

Artifact, rebuilt on 2026-09-28 and replacing the one listed under "Final results": `build/libs/ars_n_spells-3.3.5.jar`, SHA-256 `65b2d786ea3b0f7653cbb453cfc29cb6495ce40abfd38809e3db2e1dd9f4f0af`. It declares version `3.3.5` and bundles `META-INF/jarjar/mixinextras-neoforge-0.5.3.jar`. Its compat mixin config lists `gametest.MixinCastProbe`, which the plugin gate keeps unapplied outside GameTests.

### Other Iron's add-ons in the reported pack

These jars were inspected. None acts inside Iron's mana block.

| Mod | Finding |
| --- | --- |
| Better Spellcasting 1.2.0 | No mixins. Weapon-attack casts call `onCast` directly (free, as in Iron's without ANS) or start a normal Iron's cast. |
| Ace's Spell Utils 1.2.7.2 | Mixins on `LivingEntity` and `Player` only. |
| Caster Curios Bonus 1.1.1 | `SpellOnCastEvent` listeners that change the cost; ANS already prices the final cost. |
| Iron's RPG Tweaks 2.2.3, Iron's Lib 2.2.0, Goety Iron 3.1, Goety Twilight 2.1.0, Alex's Caves Spellbooks 1.1.5, Deeper and Darker Spellbooks 1.3.3 | No reference to `castSpell` or its mana block. |

Animus also lowers costs in a `SpellOnCastEvent` listener (Arcane Channeling, never below 1). ANS honours that. For a bound Ars spell that listener raised the cost from 0 to 1, and ANS now states 0 because the proxy never takes Iron's mana.

### Not established

- Animus itself was not run. Its jar was inspected, and the probe reproduces the two behaviours that matter: the affordability check and the top-up.
- The reporter's server was not re-tested. With the rebuilt jar, casting Scorch 9 at 100 mana in `iss_primary` or `hybrid` should fire the spell and leave 10 mana, with no `[CastPayment]` warning.
- No dedicated server was started with the production jar, so the probe gate was checked by its unit test and by reading the plugin, not by a live production log.

## Environment

| Component | Version |
| --- | --- |
| Minecraft / NeoForge | 1.21.1 / 21.1.248, Java 21 |
| Ars Nouveau | 5.13.1.1400 |
| Iron's Spellbooks | 1.21.1-3.16.3, irons_lib 1.21.1-2.1.0 |
| Curios | 9.3.1+1.21.1 |
| Ars Elemental | 0.7.10.1 (CurseForge file 8399862), required by Ars Affinity |
| Ars Affinity | 1.1.1 (CurseForge project 1319260, file 7416588, SHA-256 `d96938c6e246bbcbf82151e0943f14d3b5ec6c466d1ba025a772bfb4e309682f`) |
| MixinExtras | 0.5.3, bundled with jar-in-jar |

## Final results

Each GameTest profile ran in a new `-PgametestRunDir` folder, so every run started a new world.

| Check | Command | Result |
| --- | --- | --- |
| Build and unit tests | `./gradlew build --offline` | Passed. JUnit: 887 tests, 0 failures, 0 errors, 0 skipped. |
| GameTests, Iron's loaded | `./gradlew runGameTestServer --offline -PwithIronsRuntimeGameTests` | All 128 required tests passed. Optional scenarios: 74 run, 17 not exercised (Ars Affinity 4, Ars Elemancy 3, Ars Elemental 4, Ars Zero 6). Iron's reported as `1.21.1-3.16.3`. |
| GameTests, Iron's absent | `./gradlew runGameTestServer --offline` | All 128 required tests passed. Optional scenarios: 1 run, 90 not exercised. |
| GameTests, Iron's and Ars Affinity | `./gradlew runGameTestServer --offline -PwithIronsRuntimeGameTests -PwithArsAffinity` | All 128 required tests passed. Optional scenarios: 82 run, 9 not exercised (Ars Elemancy 3, Ars Zero 6). Ars Affinity reported as `1.1.1`, Ars Elemental as `0.7.10.1`. All four Affinity tests ran. |
| GameTests, Ars Affinity without Iron's | `./gradlew runGameTestServer --offline -PwithArsAffinity` | All 128 required tests passed. Optional scenarios: 7 run, 84 not exercised. The two Affinity tests that need no Iron's ran: native Ars progress and Mana Tap. |
| GameTest log check | `python3 tools/verify_gametest_log.py <log> --required 128 --executed <n> --skipped <n> --present/--absent <mod>…` | All four logs verified, including the expected present or absent state of Iron's, Ars Elemental, Ars Zero, Ars Elemancy and Ars Affinity. |
| Config migration, new file | The Iron's-absent run above | The server logged "ANS config migration: inscribed_ars_default_cooldown_ticks (absent) -> 40 (freshly generated config: the shipped default)" and "ANS config migrated from schema 0 to schema 3." |
| Config migration, 3.3.3/3.3.4 file | `./gradlew runGameTestServer --offline -PgametestRunDir=<folder>`, with `config/ars_n_spells-server.toml` seeded at `config_schema_version = 2` and without the new key | The server logged "inscribed_ars_default_cooldown_ticks (absent) -> 0 (config written before 3.3.5: no native cooldown, as before)" and "ANS config migrated from schema 2 to schema 3." The saved file then read `config_schema_version = 3` and `inscribed_ars_default_cooldown_ticks = 0`. All 128 required tests passed. |
| Loader parity | `python3 tools/verify_loader_parity.py --forge <Forge checkout>` | Passed: 33 contract files, 54 config fields, 1458 resources, 0 errors, both at 3.3.5. |
| Tool tests | `python3 -m unittest discover -s tools -p 'test_*.py'` | 27 tests passed. |

Artifact: `build/libs/ars_n_spells-3.3.5.jar`, SHA-256 `57afb16ebe1da675f21ea695e12568aa4336d94d7d6ab3955b36f98610eb82a5`. It declares version `3.3.5`, contains `META-INF/jarjar/mixinextras-neoforge-0.5.3.jar`, and lists `irons.MixinIronsCastTicker` and `irons.MixinIronsManaRegen` in `ars_n_spells.compat.mixins.json`. A file you build yourself will have a different hash.

## New tests

| Area | Tests |
| --- | --- |
| Channel payment (`NativeCastPaymentGameTests`) | `ironsLoaded_channelEndsOnLastAffordablePulse`, `ironsLoaded_swordChannelUsesFinalCostModifier`, `ironsLoaded_swordDelayedCastPaysFinalPriceOnce` |
| `ars_primary` mana (`ManaStabilityGameTests`) | `ironsLoaded_arsPrimaryIronsRegenCannotLowerArsPool`, `ironsLoaded_arsPrimaryHotbarScrollKeepsAuthoritativeBalance`, `ironsLoaded_arsPrimaryRemovedMaxBonusClampsOnce` |
| Inscribed cooldown (`InscribedCooldownGameTests`) | `ironsLoaded_successfulProxyCastStartsConfiguredNativeCooldown`, `ironsLoaded_failedOrUnpaidProxyCastStartsNoCooldown`, `ironsLoaded_zeroCooldownReproducesPreviousBehaviour`, `ironsLoaded_cooldownReductionAttributeShortensProxyCooldown`, `ironsLoaded_reusedProxySlotSharesCooldownAcrossBooks`, `ironsLoaded_categoryCooldownAndProxyCooldownBothGate` |
| Ars Affinity (`ArsAffinityInteropGameTests`) | `affinityLoaded_nativeArsCastProgressesAffinity`, `affinityLoaded_proxyArsCastProgressesExactlyOnce`, `affinityLoaded_nativeIronsSpellAddsNoGlyphProgress`, `affinityLoaded_manaTapRestoresTheRoutedPoolInEveryMode` |
| Unit tests | `InscribedCooldownMigrationTest`, `IronsRegenScopeTest`, `LogPrivacyTest`, `PaymentMessagesTest`; the mixin gating tests cover `MixinIronsCastTicker` and `MixinIronsManaRegen` |
| Tools | `test_pack_presets.py` accepts schema 3 and refuses 0, 1 and 4 |

The mutation checks recorded in the Forge testing notes were run on Forge only. The channel and regeneration code they exercise is the same on both loaders.

## Ars Affinity

ANS 3.3.5 contains no Ars Affinity code. The tests reach Affinity only by reflection, and each one skips itself when `ars_affinity` is absent. The jar was inspected before testing. Affinity counts progress in its own mixin on Ars's `SpellResolver.onResolveEffect`, and only for a `PlayerCaster`. Its Mana Tap perk restores mana through Ars's `IManaCap`.

| Test | What it shows |
| --- | --- |
| `affinityLoaded_nativeArsCastProgressesAffinity` | A native Ars heal that resolves raises Affinity's recorded progress. |
| `affinityLoaded_proxyArsCastProgressesExactlyOnce` | An Ars spell bound into an Iron's spellbook and cast from Iron's wheel raises progress by exactly as much as the same spell cast natively. ANS delegates to Ars's own resolver, so Affinity counts the cast once, with no bridge. |
| `affinityLoaded_nativeIronsSpellAddsNoGlyphProgress` | A native Iron's heal leaves Affinity's progress unchanged. As a control, an Ars cast by the same player raises it. |
| `affinityLoaded_manaTapRestoresTheRoutedPoolInEveryMode` | The Mana Tap perk is allocated through Affinity's own perk-tree rules, then the test posts Ars's `SpellDamageEvent.Post` for 20 damage. Affinity restores a positive amount. With Iron's loaded, in each of the five mana modes, that amount lands exactly once in the pool the mode makes authoritative for Ars mana. In `iss_primary`, `hybrid` and `ars_primary`, the other pool does not move. Without Iron's, only `disabled` applies and is tested. |

The tests use a plain `ServerPlayer`, not a FakePlayer. Ars wraps a FakePlayer in a `LivingCaster`, which Affinity never tracks. The player is not added to the player list, because Iron's login payloads refuse the GameTest mock connection. NeoForge's own no-op FakePlayer network handler discards the packets that casting sends.

ANS's own school affinity, progression and resonance are separate systems with separate saves, and nothing merges them with Ars Affinity's data.

## Harness notes

- Run the Iron's-absent profiles in their own `-PgametestRunDir`, as above. A world written with Iron's loaded cannot be loaded without it, and Gradle can still report `BUILD SUCCESSFUL` when the server stops at world load. `tools/verify_gametest_log.py` rejects such a log.
- The server config's autosave and file watcher can race when a test sets several values quickly. The new tests change a config value only when it differs (`TestConfig`).
- When NeoForge corrects the seeded 3.3.4-style config, it writes its own backup, `ars_n_spells-server-1.toml.bak`. On a new install, ANS's older schema-2 step also runs and writes `ars_n_spells-server.toml.pre-3.3.0.bak`, logging "Source proximity multiplier 5.0 per scan -> 5.0 per second". The value is unchanged. This predates 3.3.5 and was left as is.

## Not tested

- Ars Affinity perks other than Mana Tap, including spell power, healing amplification and defensive perks. Their numerical stacking with ANS scaling was not measured.
- Ars Affinity and ANS data across relog, death and respawn, and dimension changes. The two mods' screens, overlays and tooltips side by side. Affinity's commands.
- A dedicated server with real clients, several players and network latency.
- No graphical client was used. The wheel's cooldown display, the action-bar messages and the HUD mana bar while scrolling were not observed.
- The inscribed cooldown was not tested across logout and relog, with a book held in a Curios slot, or with a cast from the off hand.
- Iron's versions other than 1.21.1-3.16.3 and Ars Affinity versions other than 1.1.1 were not tested.
