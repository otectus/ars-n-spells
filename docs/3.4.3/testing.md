# 3.4.3 test evidence

3.4.3 fixes the report "when i miss a spell with an iron glyph inside and it flies off i can't use it
anymore" (`ars-n-spells-missed-projectile-fix.md` in the Forge checkout). It also derives the
session lifetime from Ars's projectile lifespan, with a message for deliveries that arrive after
their cast expired. The owner chose that optional item for this release. The rules are in
[docs/3.4.1/casting-contract.md](../3.4.1/casting-contract.md). The earlier evidence is in
[docs/3.4.2/testing.md](../3.4.2/testing.md), kept as it was.

Recorded 2026-11-26 from both checkouts, uncommitted: Forge on top of `6c26c54`, NeoForge
(`ars-n-spells_1.21.1`) on top of `c234f19`. Counts come from each run's own output. Every GameTest log
listed below passed `tools/verify_gametest_log.py` with the counts and mod identities given. Each run
used a new run directory (`run-343-<profile>`).

## The reproduction, before the fix (Forge)

The report reproduced on the unmodified 3.4.2 code, in GameTests with a paying survival player (a real
`ServerPlayer`, `TestPlayers`) casting Projectile and Firebolt I from an Ars spell book at the sky.
The run was Iron's 3.15.0, `run-343-before2-irons315`, and its lines are in
`logs/3.4.3/before/repro-irons315.txt` (git-ignored).

- The second cast was refused at admission: `/ans glyph last`'s record was
  `refused message.ars_n_spells.glyph.refused.pending`.
- Tried once a second, the cast was refused every time while the first projectile flew (ages 0 to
  1,200). It was admitted at +1,220 ticks, once Ars had removed the projectile at age 1,201.
- `ironsLoaded_projectileMiss_theSpellCanBeCastAgainAtOnce` failed on that code with the same record.

The first attempt (`run-343-before-irons315`) cast from the platform. Its projectile hit something
28 ticks after launch, and Firebolt's cooldown then refused every retry. A test player's Iron's
cooldowns never tick down. The GameTest world is generated terrain with a random seed per run
directory, and GameTest clears only about 20 blocks above each structure. The tests that need a
projectile that hits nothing therefore park it, still, inside their own cleared volume.

## The new tests detect what they cover

The fix was disabled part by part in place, and each run was in the Iron's 3.15.0 profile. Afterwards
the changed files were restored and their SHA-256 checked against copies taken before
(`logs/3.4.3/simulated/` in each checkout).

| Run | Disabled | Required tests that failed |
|---|---|---|
| Forge `run-343-simulated-s1` | admission as before (an unredeemed hold refuses, nothing is released), and the fixed 60-second lifetime without the expired message | `projectileMiss_theSpellCanBeCastAgainAtOnce`, `twoFlightsOfOneSpell_onlyTheFirstLandingRuns` (the second cast was refused), `lateDelivery_afterItsSessionExpired_saysSo`, `longProjectileLifespan_aLateLandingStillRuns`; also `ironsLoaded_uninscription_removesOnlyAnsInscriptionsFromARealBoundBook`, see below |
| Forge `run-343-simulated-s2` | the cooldown check at the effect boundary | `twoFlightsOfOneSpell_onlyTheFirstLandingRuns` (two effects applied), `cooldownStartedInFlight_refusesTheLanding` (the landing ran on the cooldown) |
| NeoForge `run-343-simulated-old` | admission as before (a prepaid hold or a running charge refuses, nothing is released, every open session counts), the fixed 90-second lifetime without the expired message, and the per-occurrence cooldown exemption | `projectileMiss_...`, `twoFlightsOfOneSpell_...`, `sameGlyphTwiceInOneCast_bothRun` (the second occurrence was refused with `ui.irons_spellbooks.cast_error_cooldown`), `lateDelivery_...` (`not_admitted`), `longProjectileLifespan_...` |

`sameGlyphTwiceInOneCast_bothRun` and `chargingChild_stillRefusesItsSpell` pass on the old Forge
behaviour by design: they guard what must not change. On NeoForge the first of them shows a defect
that the per-cast exemption fixes.

## Forge

| Check | Command | Result |
|---|---|---|
| Unit tests and packaging | `gradlew-quiet.sh <checkout> build --offline` | PASS; 1,108 unit tests, 0 failures, 0 errors, 0 skipped (`GlyphSessionLifetimeTest` is new); `build/libs/ars_n_spells-3.4.3.jar` and `-slim.jar`, `META-INF/mods.toml` version 3.4.3 |
| Tool tests | `python3 -m unittest discover -s tools -p 'test_*.py'` | 65 tests, OK |
| Shared contracts | `python3 tools/contract_parity.py`, and with `--other <NeoForge checkout>` | PASS, 33 sources and fixtures; 1 and 2 checkouts |
| Assets and language | `python3 .mcmod-tools/check_mod.py <checkout>` | 0 errors, 1 warning, 5 notes, the findings recorded for 3.4.2 |
| GameTests, Iron's absent (CI) | `runGameTestServer --offline -PgametestRunDir=run-343-absent` | all 379 required passed; 1 executed, 315 not exercised; every optional mod absent |
| GameTests, Iron's 3.15.0 | `... -PwithIronsRuntimeGameTests` (`run-343-irons315-final`) | all 379 passed; 266 executed, 50 not exercised |
| GameTests, Iron's 3.15.0 and Ars Elemental (CI) | `... -PwithIronsRuntimeGameTests -PwithArsElemental` | all 379 passed; 275 executed, 41 not exercised |
| GameTests, Iron's 3.15.0 and the Covenant stack | `... -PwithIronsRuntimeGameTests -PwithCovenantRuntimeGameTests` (lock in `libs/covenant-3.3.0`) | all 379 passed; 279 executed, 37 not exercised |
| GameTests, Iron's 3.16.3, every addon and Covenant | `... -PwithIronsRuntimeGameTests -PwithIrons316RuntimeGameTests -PwithArsElemental -PwithTooManyGlyphs -PwithWindSpellbooks -PwithCatalogPackages=wind_spellbooks,irons_envelope_15 -PwithArsZero -PwithCovenantRuntimeGameTests` | all 379 passed; 315 executed, 1 not exercised (`save01_experiment`) |

The 7 new required GameTests (372 before) are in `ImportedGlyphGameTests`. Each is Iron's-gated
and cast by a paying survival player, so every profile with Iron's executes 7 more and the
Iron's-absent profile skips 7 more. CI pins these counts.

## NeoForge

| Check | Command | Result |
|---|---|---|
| Unit tests and packaging | `gradlew-quiet.sh <checkout> build --offline` | PASS; 1,092 unit tests, 0 failures, 0 errors, 0 skipped (`GlyphSessionLifetimeTest` is new); `build/libs/ars_n_spells-3.4.3.jar`, `META-INF/neoforge.mods.toml` version 3.4.3 |
| Tool tests | `python3 -m unittest discover -s tools -p 'test_*.py'` | 59 tests, OK |
| Shared contracts | `python3 tools/contract_parity.py --other <Forge checkout>` | PASS, 33 sources and fixtures, 2 checkouts |
| GameTests, Iron's absent (CI) | `runGameTestServer --offline -PgametestRunDir=run-343-absent` | all 500 required passed; 1 executed, 427 not exercised; Iron's, Ars Elemental, Ars Zero and Ars Elemancy absent |
| GameTests, Iron's 3.16.3 and the Ars addons (CI) | `... -PwithIronsRuntimeGameTests -PwithArsElemental -PwithArsZero -PwithArsElemancy` | all 500 passed; 408 executed, 20 not exercised |
| GameTests, Wind's Spellbooks | `... -PwithIronsRuntimeGameTests -PwithWindSpellbooks -PwithCatalogPackages=wind_spellbooks` | all 500 passed; 395 executed, 33 not exercised |
| GameTests, levels XI-XV | `... -PwithIronsRuntimeGameTests -PwithCatalogPackages=irons_envelope_15` | all 500 passed; 391 executed, 37 not exercised |

The 7 new required GameTests (493 before) have the Forge names. One existing test changed:
`ironsLoaded_glyphBoundsRefuseWithoutCharging` filled the one-session limit by casting the charging
Slow again. That second cast is now refused as pending first, so the test casts a different spell.

## Loader parity

| Check | Command | Result |
|---|---|---|
| Shared behaviour | `python3 tools/verify_loader_parity.py --neoforge <NeoForge checkout> --section behaviour`, and `--forge <Forge checkout>` from the other checkout | PASS from both: 70 shared fixture cases in 75 identical files, 13 identical tool modules, 70 browser labels, 1,110 catalog identities and 1,110 ledger ids that agree |
| Full parity audit | `python3 tools/verify_loader_parity.py --neoforge <NeoForge checkout>`, and `--forge` from the other checkout | FAIL, as recorded since 3.4.0: shared behaviour PASS, 170 reviewed platform exceptions, 491 source-level differences (489 for 3.4.2) |

The two new differences are the new refusal key under each loader's own naming:
`message.ars_n_spells.glyph.refused.expired` on Forge and `ars_n_spells.glyph.refused.expired` on
NeoForge. The new GameTests run on both loaders, so none is listed as one-sided.

## Intermittent failures seen

Two existing tests failed once and passed in every other run. This change touches no code either
test exercises.

- NeoForge `ironsLoaded_regen_defaultSchedulesBridgeEqualEffect`, in the first Iron's run
  (`run-343-irons`): it measured 20.0 delivered for 5.0 expected. It passed in the five NeoForge runs
  after it. NeoForge's GameTests do not stop the watchers on Ars's and Iron's server configs (Forge's
  have since 3.4.2, `TestConfig.NativeWatchers`). That leaves this suite open to the reload race
  recorded for Forge on 2026-11-18. The cause was not confirmed.
- Forge `ironsLoaded_uninscription_removesOnlyAnsInscriptionsFromARealBoundBook`, in
  `run-343-simulated-s1` ("first bind: one Ars entry"). It passed in the ten other Forge runs with
  Iron's, before the fix and after it.

## 2026-11-27: committed, CI and the release JARs

The work was committed and pushed on 2026-11-27: Forge `96fefac` (the uncommitted 3.4.2 work) and
`2f6b7d1` (3.4.3) on `main`, NeoForge `c9d08e5` (the uncommitted 3.4.1 and 3.4.2 work) and `90f1b2d`
(3.4.3) on `port/neoforge-1.21.1`. Each backlog commit was compiled on its own before it was
committed.

GitHub Actions passed every job on both pushes (Forge run 45945096508, NeoForge run 45945100501), and
the log verifier accepted the pinned counts. These are the first green runs on `main` since 3.2.2
(2026-09-01) and the first on `port/neoforge-1.21.1`. On `main`, the 3.2.4 and 3.3.0 runs failed in
their GameTest job. Every Forge run from 3.3.3 to 3.4.1 and every NeoForge run from 3.3.3 to 3.4.0
failed before the GameTests, at the step "Verify tooling and icon assets". At first the runner had no
Pillow. At 3.4.1, with Pillow installed, the regenerated icon files did not match the published ones
byte for byte. 3.4.2 (ANS-013) pins the icon toolchain and compares decoded pixels. The first NeoForge
run (3.3.0) failed to compile.

| CI job | Required passed | Executed | Not exercised |
|---|---:|---:|---:|
| Forge, Iron's absent | 379 | 1 | 315 |
| Forge, Iron's 3.15.0 and Ars Elemental | 379 | 275 | 41 |
| Forge saved-world removal, its loaded preparation (informational) | 379 | 275 | 41 |
| NeoForge, Iron's absent | 500 | 1 | 427 |
| NeoForge, Iron's 3.16.3, Ars Elemental, Ars Zero and Ars Elemancy | 500 | 408 | 20 |

The release JARs were built from those commits with `gradlew-quiet.sh <checkout> build --offline`:
PASS, 1,108 unit tests on Forge and 1,092 on NeoForge, 0 failures, 0 errors. The release commit after
them changes only documents and `update.json`, which no JAR contains.

| Artifact | SHA-256 |
|---|---|
| Forge `build/libs/ars_n_spells-3.4.3.jar` | `dfa035dde967bfbe6ec292bbd483a55e12fa27a58e99b21a2893e21cfeed0bd0` |
| Forge `build/libs/ars_n_spells-3.4.3-slim.jar` | `3573cca3a553a150aa2dac6cd83eb2d586423b81dbad78e5319e3ab62b0e816f` |
| Forge `build/catalog-packages/ans_catalog_wind_spellbooks-1.0.0.jar` | `3e5fba0629df8627a54bd8d21a2abcdd788f0b8c8fbc3ab6c59ad70f8e99cb4d` |
| Forge `build/catalog-packages/ans_catalog_irons_envelope_15-1.0.0.jar` | `39e25ca0c02fe5991f76d1f7053fdbe4eda0f397c3308a3768c025f9a7cff236` |
| NeoForge `build/libs/ars_n_spells-3.4.3.jar` | `872c33575f3be24846c5a8f731f0bb79bcbdd39be4a43dce93e32953bb1192c4` |
| NeoForge `build/libs/ars_n_spells-3.4.3-sources.jar` | `132b94713e0efff80aae89fc8a07fe2825ee1cb6d08004804c610df9e6457e71` |
| NeoForge `build/catalog-packages/ans_catalog_wind_spellbooks-1.0.0.jar` | `f09994357f7f2571c884f5a7a99a47b0308c74307c2dc8cd769062d8f305e6d9` |
| NeoForge `build/catalog-packages/ans_catalog_irons_envelope_15-1.0.0.jar` | `2f5921148b7070f64b2add892b1d818ba00ba184c8504cf1ea042df9f6ae947e` |

### The release JARs on production servers

Each JAR was booted on the production dedicated servers of the 3.4.1 review (Forge 47.4.10, NeoForge
21.1.248). Every boot had a fresh world, a fresh `config/ars_n_spells` and a `mods/` holding exactly
the profile's jars. The harness is a copy of `logs/3.4.1/review-1109/tools/boot_server.sh` that reads
the version from `gradle.properties` (`logs/3.4.3/boot/boot_server.sh`, evidence in
`logs/3.4.3/boot/evidence/`, git-ignored). Every boot reached Done, answered `ans glyph report` and
stopped with exit 0. The ANS JAR in each `mods/` had the release SHA-256 above, and no crash report
was written.

| Profile | Mods | Catalog | Native spells accounted for | ERROR lines |
|---|---:|---|---|---|
| Forge, Iron's absent | 4 | 111 spells, 1,110 glyphs | 111: supported 111 | none |
| Forge, Iron's 3.15.0 | 6 | 111 spells, 1,110 glyphs | 119: supported 111, excluded 8 | 3, Iron's own data: the `expulsion_ring` recipe and two loot tables |
| Forge, Iron's 3.16.3 | 7 | 111 spells, 1,110 glyphs | 119: supported 111, excluded 8 | 2, Iron's own loot tables |
| Forge, Iron's 3.16.3 and Wind's Spellbooks with its catalog package | 9 | 118 spells, 1,180 glyphs | 126: supported 116, unsupported 2, excluded 8 | 2, Iron's own loot tables |
| Forge, Iron's 3.16.3 and the levels XI-XV package | 8 | 111 spells, 1,665 glyphs | 119: supported 111, excluded 8 | 2, Iron's own loot tables |
| NeoForge, Iron's absent | 4 | 111 spells, 1,110 glyphs | 111: supported 111 | none |
| NeoForge, Iron's 3.16.3 | 7 | 111 spells, 1,110 glyphs | 119: supported 111, excluded 8 | none |
| NeoForge, Iron's 3.16.3 and Wind's Spellbooks with its catalog package | 9 | 118 spells, 1,180 glyphs | 126: supported 116, unsupported 2, excluded 8 | none |
| NeoForge, Iron's 3.16.3 and the levels XI-XV package | 8 | 111 spells, 1,665 glyphs | 119: supported 111, excluded 8 | none |

The two unsupported Wind's Spellbooks spells are Tornado and Iron Slash, which stay dormant with
their reason. The Forge ERROR lines are the same Iron's data errors as in the earlier boots of these
profiles: on 3.15.0 those of 2026-11-10, on 3.16.3 those of 2026-11-18.

## What this evidence does not cover

- No game client was launched. The reproduction and the fix were exercised on the server through
  GameTests. The brief's own check is still to do: in a real survival client, cast Projectile and an
  imported glyph at the sky twice, and cast it twice at one target.
- Nothing was uploaded to CurseForge or Modrinth, and no GitHub Release was made.
