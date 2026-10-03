# 3.3.5 testing (Forge 1.20.1)

The runs under "Final results" were made on 2026-09-27. The payment-boundary fix of 2026-09-28 changed the source afterwards. Its section below records the runs made after it, which replace the rows they repeat. [runtime-reconciliation.md](runtime-reconciliation.md) explains what each report showed and which build produced it.

## Payment-boundary fix (2026-09-28)

The report came from a NeoForge 1.21.1 server; [runtime-reconciliation.md](runtime-reconciliation.md#scorch-cancelled-after-payment-neoforge-2026-09-28) has the details. The Forge build had the same payment path and received the same change, so the two builds' payment code stays the same.

### Cause

Iron's `AbstractSpell.castSpell` posts the final `SpellOnCastEvent`, then runs its own mana block (`MagicData.getMana()`, then `setMana`), then `onCast`. The same order holds in every Iron's build checked: 1.21.1-3.16.3, 1.20.1-3.15.0 and 1.20.1-3.16.3, which the checkouts build and test against, and the cached 1.20.1-3.15.4 and 3.16.1 jars. Each has exactly one of those calls in `castSpell`. ANS 3.3.4, and 3.3.5 until this fix, took the price right after the event and only suppressed Iron's later write.

Animus for NeoForge 1.21.1 (`animusnv-1.21.1-5.2.13`, in the reported pack) injects at that `getMana()` call (`AbstractSpellMixin.animus$payEV`, a required mixin config). When the pool holds less than the event's cost, it tries to pay the difference from the player's NeoVitae blood-magic network. When it cannot, it cancels `castSpell`. With 100 mana and a cost of 90, ANS took 90 and Animus saw 10. Animus cancelled the cast, and ANS refunded the 90 and reported the missing effect as a fault. Any spell costing more than half the remaining pool failed this way; cheaper spells passed. With blood casting enabled, Animus would instead have drained essence for mana ANS had already taken. 3.3.3 did not show the failure, because it set the event's cost to 0 after paying.

### Change

- ANS prices the cast after the event (`IronsCastPayments.price`) and takes the payment at Iron's `setMana` call, which the payment replaces (`MixinIronsCastPayment.arsnspells$nativeWrite`). A scroll in `full` cost mode, which Iron's never debits, still pays right after the event.
- After pricing, the event's cost states what ANS will take from the pool `MagicData.getMana()` reads, rounded down. That is the converted amount in `ars_primary`, the Iron's share of a dual-cost split, and 0 for exempt casts such as bound Ars spells and LP-paid casts. A check inside Iron's mana block therefore compares like with like.
- A cast another mod ends before its effect is that cast's native outcome. ANS returns anything held, including a reserved LP leg, marks any such refund `NATIVE_VETO`, and counts the veto. It shows no message and logs no warning; debug mode logs `stage=vetoed_before_effect`. Iron's own completion runs as it would without ANS.
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

The failing-before-fix run was made on the NeoForge checkout only. There, all four new tests failed on the unfixed code and passed after the fix.

### Results after the fix

Each GameTest profile ran in a new `-PgametestRunDir` folder.

| Check | Command | Result |
| --- | --- | --- |
| Build and unit tests | `./gradlew build --offline` | Passed. JUnit: 878 tests, 0 failures, 0 errors, 0 skipped. |
| GameTests, Iron's 3.15.0 | `./gradlew runGameTestServer --offline -PwithIronsRuntimeGameTests -PgametestRunDir=<new folder>` | All 139 required tests passed. Optional scenarios: 90 run, 19 not exercised. Iron's reported as `1.20.1-3.15.0`. |
| GameTests, Iron's 3.16.3 | `./gradlew runGameTestServer --offline -PwithIrons316RuntimeGameTests -PgametestRunDir=<new folder>` | All 139 required tests passed on the second run. Optional scenarios: 90 run, 19 not exercised. Iron's reported as `1.20.1-3.16.3`. The self-check line reads `AbstractSpell.payment=OK`, `MagicManager.ticker=OK` and `MagicManager.regenScope=OK`. See the note below about the first run. |
| GameTests, Iron's absent | `./gradlew runGameTestServer --offline -PgametestRunDir=<new folder>` | All 139 required tests passed. Optional scenarios: 1 run, 108 not exercised. |
| GameTests, Iron's 3.15.0 and Covenant | `./gradlew runGameTestServer --offline -PwithIronsRuntimeGameTests -PwithCovenantRuntimeGameTests -PcovenantRuntimeDir=<checkout>/libs/covenant-3.3.0 -PgametestRunDir=<new folder>` | All 139 required tests passed. Optional scenarios: 91 run, 18 not exercised. Covenant of the Seven reported as `2.2.6-hotfix`. |
| GameTest log check | `python3 tools/verify_gametest_log.py <log> --required 139 --executed <n> --skipped <n> --present/--absent <mod>…` | The four passing logs verified. |
| Shared contract | `python3 tools/contract_parity.py --other <NeoForge checkout>` | Passed: 33 contract sources and fixtures, 2 loader checkouts. |
| Tool tests | `python3 -m unittest discover -s tools -p 'test_*.py'` | 29 tests passed. |

The first Iron's 3.16.3 run failed one required test, 1 of 139: `arsNative_successAndLateVetoHaveExactPoolDeltas`, with "Ars successful debit disabled: expected 8910.0, got 8925.0". That test casts an Ars spell and never reaches Iron's `castSpell`. A debit of 75 instead of 90 is the Ars heal's cross-cast price at the shipped multiplier, 1.25, instead of the test's 1.5. The log shows the file watcher reloading `ars_n_spells-server.toml` repeatedly while the server ran. That matches the config race under "Harness notes", but its cause was not proven. The rerun in a new folder passed all 139.

Not re-run after the fix: the two config-migration checks. The fix does not touch the config.

Artifacts, rebuilt on 2026-09-28 and replacing those listed under "Final results": `build/libs/ars_n_spells-3.3.5.jar`, SHA-256 `d6a639768e0afc4ebd26dfce6cf465a7f4260a3d7622861b2e64ad1f06f2d0fe`, and `build/libs/ars_n_spells-3.3.5-slim.jar`, SHA-256 `6daf0b0533c64117b1ad40bd0851295556c0cf2722687494b3f7ebb3f0064863`. The bundled jar declares version `3.3.5` and contains `META-INF/jarjar/mixinextras-forge-0.5.4.jar`. Both list `gametest.MixinCastProbe` in the compat mixin config, which the plugin gate keeps unapplied outside GameTests.

## Environment

| Component | Version |
| --- | --- |
| Minecraft / Forge | 1.20.1 / 47.4.10, Java 17 toolchain |
| Ars Nouveau | 4.12.7 (CurseForge file 6688854) |
| Iron's Spellbooks, build pin | 1.20.1-3.15.0 (file 7402504), GeckoLib 4.7.1.3 |
| Iron's Spellbooks, 3.16 profile | 1.20.1-3.16.3 (file 8680180), irons_lib 1.20.1-2.1.0, GeckoLib 4.8.3 |
| Curios | 5.14.1+1.20.1 |
| MixinExtras | 0.5.4, bundled with jar-in-jar |

## Final results

| Check | Command | Result |
| --- | --- | --- |
| Build and unit tests | `./gradlew build --offline` | Passed. JUnit: 877 tests, 0 failures, 0 errors, 0 skipped. |
| GameTests, Iron's 3.15.0 | `./gradlew runGameTestServer --offline -PwithIronsRuntimeGameTests` | All 135 required tests passed. Optional scenarios: 86 run, 19 not exercised (Ars Elemental 10, Ars Zero 6, Covenant 1, Too Many Glyphs 2). Iron's reported as `1.20.1-3.15.0`. |
| GameTests, Iron's absent | `./gradlew runGameTestServer --offline -PgametestRunDir=<new folder>` | All 135 required tests passed. Optional scenarios: 1 run, 104 not exercised. Iron's reported absent. |
| GameTests, Iron's 3.16.3 | `./gradlew runGameTestServer --offline -PwithIrons316RuntimeGameTests` | All 135 required tests passed. Optional scenarios: 86 run, 19 not exercised. Iron's reported as `1.20.1-3.16.3`. |
| GameTest log check | `python3 tools/verify_gametest_log.py <log> --required 135 --executed <n> --skipped <n> --present/--absent <mod>…` | All three logs verified, including the expected present or absent state of Iron's, Ars Elemental, Ars Zero, Too Many Glyphs and Covenant. |
| Config migration, new file | The Iron's-absent run above, which starts a new world | The server logged "config migration report (schema 0 -> 3)" and "inscribed_ars_default_cooldown_ticks: (absent) -> 40 (freshly generated config: the shipped default)". |
| Config migration, 3.3.3/3.3.4 file | `./gradlew runGameTestServer --offline -PgametestRunDir=<folder>`, with `world/serverconfig/ars_n_spells-server.toml` seeded at `config_schema_version = 2` and without the new key | The server logged "config migration report (schema 2 -> 3)" and "inscribed_ars_default_cooldown_ticks: (absent) -> 0 (config written before 3.3.5: no native cooldown, as before)". The saved file then read `config_schema_version = 3` and `inscribed_ars_default_cooldown_ticks = 0`. All 135 required tests passed. |
| Shared contract | `python3 tools/contract_parity.py --other <NeoForge checkout>` | Passed: 33 contract sources and fixtures, 2 loader checkouts. |
| Loader parity (from the NeoForge checkout) | `python3 tools/verify_loader_parity.py --forge <Forge checkout>` | Passed: 33 contract files, 54 config fields, 1458 resources, 0 errors, both at 3.3.5. |
| Tool tests | `python3 -m unittest discover -s tools -p 'test_*.py'` | 29 tests passed. |

Artifacts: `build/libs/ars_n_spells-3.3.5.jar`, SHA-256 `fd56eaaceeed9970a0e0f204b51007a3ed13603abf6ce7e3d1adde953e9417d4`, and `build/libs/ars_n_spells-3.3.5-slim.jar`, SHA-256 `76e4ca4cc4b57f8126a910f8941b8059b54fcf945231824358271abb8114f6d1`. The bundled jar declares version `3.3.5`, contains `META-INF/jarjar/mixinextras-forge-0.5.4.jar`, and lists `irons.MixinIronsCastTicker` and `irons.MixinIronsManaRegen` in `ars_n_spells.compat.mixins.json`. A file you build yourself will have a different hash.

## New and changed tests

| Area | Tests |
| --- | --- |
| Channel payment (`NativeCastPaymentGameTests`) | `ironsLoaded_channelEndsOnLastAffordablePulse`, `ironsLoaded_swordChannelUsesFinalCostModifier`, `ironsLoaded_swordDelayedCastPaysFinalPriceOnce` |
| `ars_primary` mana (`ManaStabilityGameTests`) | `ironsLoaded_arsPrimaryIronsRegenCannotLowerArsPool`, `ironsLoaded_arsPrimaryHotbarScrollKeepsAuthoritativeBalance`, `ironsLoaded_arsPrimaryRemovedMaxBonusClampsOnce` |
| Inscribed cooldown (`InscribedCooldownGameTests`) | `ironsLoaded_successfulProxyCastStartsConfiguredNativeCooldown`, `ironsLoaded_failedOrUnpaidProxyCastStartsNoCooldown`, `ironsLoaded_zeroCooldownReproducesPreviousBehaviour`, `ironsLoaded_cooldownReductionAttributeShortensProxyCooldown`, `ironsLoaded_reusedProxySlotSharesCooldownAcrossBooks`, `ironsLoaded_categoryCooldownAndProxyCooldownBothGate` |
| Unit tests | `InscribedCooldownMigrationTest`, `IronsRegenScopeTest`, `LogPrivacyTest`, `PaymentMessagesTest`; `ConfigSchemaMigrationTest` now expects schema 3; the mixin gating tests cover `MixinIronsCastTicker` and `MixinIronsManaRegen` |
| Tools | `test_pack_presets.py` accepts schema 3 and refuses 0, 1 and 4 |

## Mutation checks

Two checks confirmed that the new tests catch the problems they target. Each disabled one 3.3.5 change, ran the Iron's 3.15.0 GameTests, then restored the source, confirmed byte-for-byte with `cmp`. The final results above come from the restored source.

| Change disabled | Result |
| --- | --- |
| The post-pulse exhaustion check (`IronsCastLifecycle.afterEffect` returns at once), which restores the 3.3.4 channel behaviour | 2 of 135 failed: "channel must not end in a payment failure iss_primary x1" and "sword channel must not end in a payment failure". |
| The regeneration guard (`IronsRegenScope.suppresses` always false) | 2 of 135 failed: "a lagging Iron's mirror (20) must not clamp the Ars pool (80): got 20.0" and "Iron's regeneration must not be the thing that clamps; got 100.0". The 60-step hotbar test still passed, so it does not reproduce the reported dip on its own. |

## Harness notes

- Run the Iron's-absent profile in its own `-PgametestRunDir`. A world written with Iron's loaded records Iron's dimension type, and a server without Iron's cannot load it. The first absent attempt reused `run/` and stopped at world load, yet Gradle still reported `BUILD SUCCESSFUL`. `tools/verify_gametest_log.py` rejects such a log.
- Forge's server config autosave and file watcher can race when a test sets several values quickly. The watcher can read a half-written file and reset keys to their defaults ("is not correct. Correcting"), which made tests fail at random during development. The new tests change a config value only when it differs (`TestConfig`). This is a harness limitation, not a gameplay path, and it did not occur in the final runs.
- During development, one of four Iron's 3.16.3 runs failed `bindingritual_bindsdroppedscrollontodroppedbook` ("found 0 entries"). The other three runs and the final run passed it. The cause was not identified.

## Not tested

- BielGG's Spells Addon, Roaring and T.O Magic were not run. BielGG's jar requires L_Ender's Cataclysm and Lionfish API. Iron's own `fire_breath` and `fireball`, with cost listeners that mimic BielGG's Thorn Ring, stand in for its spells.
- No graphical client was used. The wheel's cooldown display, the action-bar messages and the HUD mana bar while scrolling were not observed.
- The inscribed cooldown was not tested across logout and relog, with a book held in a Curios slot, or with a cast from the off hand. Persistence comes from Iron's own cooldown storage.
- Death and respawn, dimension changes, dedicated servers with real clients, several players and network latency were not tested.
- Iron's versions other than 1.20.1-3.15.0 and 1.20.1-3.16.3 were not tested.
- Animus was not run on either loader. No dedicated server was started with the production jar, so the GameTest probe's gate was checked by its unit test and by reading the plugin, not by a live production log.
