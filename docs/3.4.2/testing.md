# 3.4.2 test evidence

The glyph library has its own client, server and compatibility results. [Glyph library verification](glyph-browser-testing.md)

3.4.2 is the remediation of the 2026-11-17 audit (`Ars_n_Spells_Audit_Remediation_Handoff.md`,
findings ANS-001 to ANS-015). It also carries every 3.4.1 change, since 3.4.1 was never published.
The 3.4.1 evidence it builds on is in [docs/3.4.1/testing.md](../3.4.1/testing.md). Those dated
records are kept as they were.

The runs below were made under `mod_version=3.4.1`, before the version bump. The bump changed only
the version string. After it, the build and unit tests were run again, and so was the Iron's-absent
GameTest profile; see the end of this file.

## 2026-11-18: the audit remediation runs

Recorded 2026-11-18 from the Forge checkout only, uncommitted, on top of `6c26c54`, after the
changes for `Ars_n_Spells_Audit_Remediation_Handoff.md` (findings ANS-001 to ANS-015). The NeoForge
checkout was not changed or run. The rules of the 3.4.1 record apply: counts come from each run's own
output, and every GameTest log passed `tools/verify_gametest_log.py` with the counts and mod
identities given below. Each profile ran in a new run directory (`run-audit-after-<profile>`).

| Check | Command | Result |
|---|---|---|
| Tool tests | `python3 -m unittest discover -s tools -p 'test_*.py'` | 65 tests, OK |
| Icon generation and assets | `python3 tools/generate_icons.py --check`; `python3 tools/verify_icons.py` | verified; PASS |
| Shared contracts | `python3 tools/contract_parity.py` | PASS, 33 sources and fixtures |
| Build with unit tests | `gradlew-quiet.sh <checkout> build` | PASS; 1,082 unit tests, 0 failures, 0 errors, 0 skipped (the Covenant JAR was present in `libs/`, so `CovenantJarSurfaceTest` ran) |
| GameTests, Iron's absent | `runGameTestServer -PgametestRunDir=run-audit-after-absent` | all 372 required passed; 1 executed, 308 not exercised; Iron's, Ars Elemental, Ars Zero, Too Many Glyphs and Covenant absent |
| GameTests, Iron's 3.15.0 | `... -PwithIronsRuntimeGameTests` | all 372 passed; 259 executed, 50 not exercised |
| GameTests, Iron's 3.15.0 and Ars Elemental (the CI profile) | `... -PwithIronsRuntimeGameTests -PwithArsElemental` | all 372 passed; 268 executed, 41 not exercised |
| GameTests, Iron's 3.15.0 and the Covenant stack | `... -PwithIronsRuntimeGameTests -PwithCovenantRuntimeGameTests` (lock in `libs/covenant-3.3.0`) | all 372 passed; 272 executed, 37 not exercised (rerun as `run-audit-final-covenant` after the last test change: the same) |
| GameTests, addons | `... -PwithIronsRuntimeGameTests -PwithArsElemental -PwithTooManyGlyphs -PwithWindSpellbooks -PwithCatalogPackages=wind_spellbooks` | all 372 passed; 280 executed, 29 not exercised |
| GameTests, Ars Zero and levels XI-XV | the addons flags plus `-PwithArsZero` and `irons_envelope_15` | all 372 passed; 295 executed, 14 not exercised |
| GameTests, Iron's 3.16.3, every addon and Covenant | `... -PwithIrons316RuntimeGameTests` plus every flag above | all 372 passed; 308 executed, 1 not exercised (`save01_experiment`); rerun as `run-audit-final-all-316` after the last test change: the same |
| Packaged JAR on a production server | `logs/3.4.1/review-1109/tools/boot_server.sh` on Forge 47.4.10, profiles `absent`, `irons315` and `irons316`, evidence in `logs/audit-remediation-2026-11-18/boot/` | each reached Done and stopped with exit 0; 111 and 119 native spells accounted for. ERROR lines are Iron's own recipe and loot-table data (3 and 2), as in the 2026-11-10 record |
| Assets and language | `python3 .mcmod-tools/check_mod.py <checkout>` | 0 errors, 1 warning, 5 notes, the same findings as recorded above |

The 28 new required GameTests (344 before) cover ANS-001 (7, Covenant-gated), ANS-002 (4), ANS-004
(2), ANS-005 (1, Ars Elemental-gated), ANS-006 (1), ANS-010 (3), ANS-011 (8), ANS-012 (1) and
ANS-014 (1). The positive imported-glyph fixtures now use real `ServerPlayer`s with a test connection
(`TestPlayers`) instead of Forge FakePlayers, which imported glyphs now refuse.

## Before and after

The regression tests for the three findings that needed reproduction or confirmation first ran
against the unfixed code (`run-audit-before-covenant`, Iron's 3.15.0 and Covenant):

- ANS-001: the delayed imported Heal still healed for free (health 15) after the aura toggle was
  turned off or a Cursed Ring was added. A mana-admitted Heal ran after Virtue was equipped, and a
  charged Fireball released after its ring came off. The ring-removal case itself is the audit's
  own reproduction; in that first run it was masked by a test that changed the aura toggle
  concurrently. Since then every aura-reading test has had its own batch. To show the final tests
  detect the defect, the two new checks (the execution-time route authorization and the Covenant
  receipt) were disabled temporarily and the 3.15.0+Covenant and 3.16.3+everything profiles were run
  (`run-audit-simulated-unfixed`). All six refusal tests then failed, including ring removal (health
  15 and 17.5) and projectile delivery (an applied Chain Lightning). The checks were then restored
  byte for byte, and the final runs below passed.
- ANS-011, measured on the native schedulers: at the default settings the old conversion delivered
  exactly the native amount (both loops double their nominal value, so the factors cancel). Away from
  them it did not. At Ars interval 7 the bridged attribute delivered 140 of 200, at interval 3 60 of
  66.7, and at Iron's multiplier 0.5 and 2 it delivered 20 and 80 of 40. With Iron's regeneration
  disabled it still produced a nonzero attribute. After the fix every scenario delivered the native
  amount exactly (`ANS_REGEN_MEASURE` lines in the Gradle console).
- ANS-012: a client menu built from the opening buffer without the block entity had 36 slots
  against the server's 39.

## What this evidence does not cover

- No game client was launched. These remain unverified: the Spell Loom screen's refusal feedback
  and retry (ANS-009; its decision logic is unit-tested in `LoomFeedbackTest`), a remote client's
  mana bars following a reload (ANS-007/008; the snapshot and the HUD rule are unit-tested), and the
  integrated-server ring cache under a real client (ANS-003; unit-tested in `SidedStateCacheTest`).
- The ANS-012 test applies real menu content and slot packets to a menu built without its block
  entity. It does not show that normal network timing can reach that state.
- A ring that is only in `#ars_n_spells:virtue_rings` is refused for imported glyphs at admission.
  No GameTest adds a custom item to that tag, so this branch is covered by source and by the
  bytecode surface test of Covenant's own ring check, not by a run.
- Iron's container refuses a second wheel slot for the same proxy, so the reconciler's duplicate
  pass could not be given a duplicate to remove. It was later removed (see below).
- Nothing was committed, published, uploaded, merged or released.

## 2026-11-18: after the version bump to 3.4.2

`mod_version` was changed to 3.4.2 in `gradle.properties` and nothing else in the code. The build was
then repeated with `--offline`: ForgeGradle's online environment check hung on an SSL handshake to the
Forge Maven, a network problem on the build machine rather than a build failure.

| Check | Command | Result |
|---|---|---|
| Build with unit tests | `gradlew-quiet.sh <checkout> build --offline` | PASS; 1,082 unit tests, 0 failures, 0 errors, 0 skipped; `build/libs/ars_n_spells-3.4.2.jar` and `-slim.jar`, `META-INF/mods.toml` version 3.4.2, MixinExtras jar-in-jar present; both catalog packages built |
| GameTests, Iron's absent | `runGameTestServer --offline -PgametestRunDir=run-342-absent` | all 372 required passed; 1 executed, 308 not exercised; every optional mod absent |
| Packaged JAR on a production server | `boot_server.sh` as above, with the 3.4.2 JAR, profiles `absent` and `irons316`, evidence in `logs/audit-remediation-2026-11-18/boot-3.4.2/` | both reached Done and stopped with exit 0; 111 and 119 native spells accounted for; Iron's own data logged 2 ERROR lines on `irons316` and none on `absent` |

The Iron's-loaded GameTest profiles were not run again after the bump; their results above are from
the same code under the old version string. `update.json` still names 3.4.0 as the latest and
recommended release, because nothing has been published.

## 2026-11-18: the NeoForge port and the final runs

The remediation was then ported to the NeoForge 1.21.1 checkout (`ars-n-spells_1.21.1`, uncommitted,
on top of `c234f19`), and that checkout was also bumped to 3.4.2. ANS-001, ANS-003 and ANS-008 concern
Covenant of the Seven and the Cursed and Virtue Rings, which have no 1.21.1 build, so they do not
apply there. ANS-006 was already correct on NeoForge: an End return copies every attachment, and a
death only those declared `copyOnDeath`. Its GameTest was ported to prove it. The other findings were ported with GameTests of
the same names. The ANS-011 tests measured NeoForge's real schedulers: Ars Nouveau 5 regenerates once
per eligible tick (its handler listens to `PlayerTickEvent.Pre`), not twice as Ars 4.12.7 does on
Forge, so `ManaRegenBridge.ARS_CALLS_PER_ELIGIBLE_TICK` is 1 there and 2 on Forge.

Both checkouts converged further where the code allows. They now share `AttemptLedger` (one
contract manifest), the imported-glyph config constants and the Glyph Press capacity bounds
(1000-100000), the Glyph Press sounds, the Spell Loom feedback text and `SchoolResolverTest`. On
Forge, `CarrierReconciler` now walks the distinct proxy pool ids once, so the duplicate pass is
gone, and the ANS-002 loader test is named `ironsLoaded_caster_loaderFakePlayerIsRefusedBeforeEffectAndPayment`
on both loaders.

### NeoForge

| Check | Command | Result |
|---|---|---|
| Build with unit tests | `gradlew-quiet.sh <checkout> compileJava test --offline`, then `build --offline` | PASS; 1,076 unit tests, 0 failures, 0 errors, 0 skipped; `build/libs/ars_n_spells-3.4.2.jar` |
| Tool tests | `python3 -m unittest discover -s tools -p 'test_*.py'` | 59 tests, OK |
| Icon generation and assets | `python3 tools/generate_icons.py --check`; `python3 tools/verify_icons.py` | verified; PASS |
| Shared contracts | `python3 tools/contract_parity.py --other <Forge checkout>`, from each checkout | PASS, 33 sources and fixtures, 2 checkouts; the two manifests are identical |
| GameTests, Iron's absent | `runGameTestServer --offline -PgametestRunDir=run-audit-neo-absent` | all 493 required passed; 1 executed, 420 not exercised; Iron's, Ars Elemental, Ars Zero and Ars Elemancy absent |
| GameTests, Iron's 3.16.3 and the Ars addons (the CI profile) | `... -PwithIronsRuntimeGameTests -PwithArsElemental -PwithArsZero -PwithArsElemancy` | all 493 passed; 401 executed, 20 not exercised |
| GameTests, Wind's Spellbooks | `... -PwithIronsRuntimeGameTests -PwithWindSpellbooks -PwithCatalogPackages=wind_spellbooks` | all 493 passed; 388 executed, 33 not exercised |
| GameTests, levels XI-XV | `... -PwithIronsRuntimeGameTests -PwithCatalogPackages=irons_envelope_15` | all 493 passed; 384 executed, 37 not exercised |
| Packaged JAR on a production server | `boot_server.sh neoforge absent` and `neoforge irons` on NeoForge 21.1.248, evidence in `logs/audit-remediation-2026-11-18/boot/neoforge-*` (Forge checkout) | both reached Done and stopped with exit 0; 111 and 119 native spells accounted for; no ERROR lines |

Every GameTest log passed `tools/verify_gametest_log.py` with the counts and identities above. The
21 new required tests (472 before) are the Forge tests for ANS-002 (4), ANS-004 (2), ANS-005 (1,
Ars Elemental-gated), ANS-006 (1), ANS-010 (3), ANS-011 (8), ANS-012 (1) and ANS-014 (1). NeoForge's
CI now checks these counts; before, it asserted a stale total of 104.

### Forge, after the convergence changes

| Check | Command | Result |
|---|---|---|
| Unit tests | `gradlew-quiet.sh <checkout> compileJava test --offline` | PASS; 1,091 unit tests, 0 failures, 0 errors, 0 skipped (`SchoolResolverTest` gained NeoForge's multi-school cases) |
| GameTests, Iron's absent | `runGameTestServer --offline -PgametestRunDir=run-342-absent` (rerun in the directory of the post-bump run, replacing its logs) | all 372 required passed; 1 executed, 308 not exercised |
| GameTests, Iron's 3.15.0 | `... -PwithIronsRuntimeGameTests` (`run-342-irons315`) | all 372 passed; 259 executed, 50 not exercised, as before |
| GameTests, Iron's 3.16.3, every addon and Covenant | every flag of the full profile above (`run-342-all-316`) | all 372 passed; 308 executed, 1 not exercised, as before |

### Loader parity

| Check | Command | Result |
|---|---|---|
| Shared behaviour | `python3 tools/verify_loader_parity.py --neoforge <NeoForge checkout> --section behaviour`, and `--forge <Forge checkout>` from the other checkout | PASS from both checkouts: 63 shared fixture cases in 67 identical files, 13 identical tool modules, and 1,110 catalog identities and 1,110 ledger ids that agree |
| Full parity audit | `python3 tools/verify_loader_parity.py --neoforge <NeoForge checkout>`, and `--forge` from the other checkout | FAIL, as recorded since 3.4.0: shared behaviour PASS, 170 reviewed platform exceptions, 489 source-level differences |

The 21 audit GameTests left the tool's one-sided list, since they now run on both loaders. The 489
remaining differences all belong to two features that 3.4.0 built separately on each loader. About
430 are imported glyphs and the Glyph Press: their runtime, screens and messages (the translation
differences), item rendering (a custom geometry loader on Forge, layered models with a dormant
override on NeoForge), the Press's block art, its book pages (Patchouli on Forge, Ars Nouveau 5's own
documentation on NeoForge) and their GameTests. About 60 are Touch targeting and its GameTests.
Converging them means rewriting one loader's implementation, which this remediation did not do.
