# 3.4.1 test evidence

Recorded 2026-10-30 from both checkouts, uncommitted, on top of 3.4.0:

- Forge: `ars-n-spells` at `0da9de0`.
- NeoForge: `ars-n-spells_1.21.1` at `c234f19`.

Every count below was read from the run's own output. A Gradle exit code is never taken as the
result: GameTest logs were checked with `tools/verify_gametest_log.py`, which requires the exact
required count, the scenario report and each mod's present or absent profile line. Performance is
not measured here.

| | Forge 1.20.1 | NeoForge 1.21.1 |
|---|---|---|
| Loader | Forge 47.4.10, Java 17.0.20.1 | NeoForge 21.1.248, Java 21.0.12.1 |
| Ars Nouveau | 4.12.7 | 5.13.1.1400 |
| Iron's Spells 'n Spellbooks | 1.20.1-3.15.0 and 1.20.1-3.16.3, one profile each | 1.21.1-3.16.3 |
| `mod_version` | 3.4.1 | 3.4.1 |

`gradlew-quiet.sh` is the maintainer's wrapper around `./gradlew <task>`. It prints PASS or FAIL and
keeps the full Gradle log; the command it runs is `./gradlew <task> --offline <flags>`.

## Forge

| Check | Command | Result |
|---|---|---|
| Tool tests | `python3 -m unittest discover -s tools -p 'test_*.py'` | 51 tests, OK |
| Unit tests | `./gradlew test --offline` | 1,017 tests, 0 failures, 0 errors, 0 skipped |
| GameTests, Iron's absent | `./gradlew runGameTestServer --offline -PgametestRunDir=run-341-absent` | all 283 required passed; scenarios 1 executed, 224 not exercised; Iron's, Ars Elemental, Ars Zero, Too Many Glyphs and Covenant absent |
| GameTests, Iron's 3.15.0 | `./gradlew runGameTestServer --offline -PwithIronsRuntimeGameTests -PgametestRunDir=run-341-irons315` | all 283 required passed; 203 executed, 22 not exercised; Iron's `1.20.1-3.15.0` present |
| GameTests, Iron's 3.16.3 | `./gradlew runGameTestServer --offline -PwithIrons316RuntimeGameTests -PgametestRunDir=run-341-irons316` | all 283 required passed; 203 executed, 22 not exercised; Iron's `1.20.1-3.16.3` present |
| Log verification | `python3 tools/verify_gametest_log.py <log> --required 283 --executed N --skipped M --present/--absent ...` for each of the three logs | all three verified |
| Build | `./gradlew build --offline` | PASS: `build/libs/ars_n_spells-3.4.1.jar`, `build/libs/ars_n_spells-3.4.1-slim.jar` |
| Packaged resources | `unzip -l build/libs/ars_n_spells-3.4.1.jar` | `META-INF/mods.toml` version 3.4.1; the catalog, the ledger and the compat mixin config are present |
| Assets and language | `python3 .mcmod-tools/check_mod.py <checkout>` | 0 errors, 1 warning, 5 notes (see below) |

Logs: `logs/3.4.1/final/` (`unit.txt`, `gt-*.txt`, `gt-*-latest.log` and `build.txt`; git-ignored).

The 3.4.0 record had 267 required GameTests, and the first 3.4.1 run had 282. The 283 now include
16 tests in `Glyph341GameTests` for the 3.4.1 contracts, as well as the updated
`ImportedGlyphGameTests` and `GlyphPressGameTests`. Each new Iron's-gated test adds one executed
scenario in the Iron's profiles and one unexercised scenario in the absent profile.

## NeoForge

| Check | Command | Result |
|---|---|---|
| Tool tests | `python3 -m unittest discover -s tools -p 'test_*.py'` | 45 tests, OK |
| Unit tests | `./gradlew test --offline` | 1,022 tests, 0 failures, 0 errors, 0 skipped |
| GameTests, Iron's loaded | `./gradlew runGameTestServer --offline -PwithIronsRuntimeGameTests -PgametestRunDir=run-341-irons` | all 419 required passed; 326 executed, 26 not exercised; Iron's present; Ars Elemental, Ars Zero, Ars Elemancy and Ars Affinity absent |
| GameTests, Iron's absent | `./gradlew runGameTestServer --offline -PgametestRunDir=run-341-absent` | all 419 required passed; 1 executed, 351 not exercised; Iron's and every optional mod absent |
| Log verification | `python3 tools/verify_gametest_log.py <log> --required 419 ...` for both logs | both verified |
| Build | `./gradlew build --offline` | PASS: `build/libs/ars_n_spells-3.4.1.jar`, `build/libs/ars_n_spells-3.4.1-sources.jar` |
| Packaged resources | `unzip -l build/libs/ars_n_spells-3.4.1.jar` | `META-INF/neoforge.mods.toml` version 3.4.1; `ans_glyph_catalog/irons_spellbooks.json` and the ledger are present |
| Assets and language | `python3 .mcmod-tools/check_mod.py <checkout>` | exit 1, false positives only (see below) |

The 3.4.0 record had 407 required GameTests, and the first 3.4.1 run had 417. The 419 add the
live-school test and the stray-item uninscription test, and they strengthen two Press tests.

## Both checkouts

| Check | Command | Result |
|---|---|---|
| Shared behaviour | `python3 tools/verify_loader_parity.py --neoforge <NeoForge checkout> --section behaviour`, and `--forge <Forge checkout>` from the other checkout | PASS from both checkouts: 37 shared fixture cases in 41 identical files, 12 identical tool modules, and 1,110 catalog identities and 1,110 ledger ids that agree |
| Full parity audit | `python3 tools/verify_loader_parity.py --neoforge <NeoForge checkout> --report ...` | FAIL, as recorded since 3.4.0: shared behaviour PASS, 121 reviewed platform exceptions, 483 source-level differences |

The shared fixtures run in each loader's unit tests (`SharedMappingFixturesTest`,
`SharedCatalogFixturesTest`, `SharedLevelFixturesTest`) and in
`tools/test_validate_glyph_mappings.py`:

- mappings: 18 cases;
- catalogs: 10 cases;
- effective levels: 9 cases.

### The remaining parity differences

The 3.4.0 record listed 2,800 differences and did not claim parity: the two builds' imported-glyph
subsystems were written separately. 3.4.1 classifies the differences:

- **Platform exceptions (121, each verified or explained).**
  - Each build's imported-glyph item models are paired one per identity: 1,110 under Forge's
    `models/item/iron/` and 1,110 under NeoForge's `models/item/glyph_iron/`.
  - NeoForge's 1,110 glyph name keys are paired with the identities.
  - The two bundled catalogs are compared by the identities they publish.
  - Each build implements its own adapters, so every spell's adapter pair is listed.
  - The table entries for one-sided GameTests, each with its counterpart or reason.
- **Source-level differences (483).** 466 already existed in 3.4.0: translation keys and Press
  wording that differ by design between the two glyph UIs, one-sided GameTests from 3.4.0, five
  config bounds and some Press textures. 17 are new 3.4.1 translation keys, each following its own
  build's 3.4.0 naming (`message.ars_n_spells.glyph.*` on Forge, `ars_n_spells.glyph.*` on
  NeoForge). Renaming released keys to converge them is left for a later release.

### `check_mod.py` findings

- **Forge.** The warning is a heuristic hit: `ArsNSpellsJeiPlugin.java:84` calls `Minecraft`
  inside a lambda that JEI runs only on the client. NeoForge's JEI plugin has had the same pattern
  since 3.4.0. The five notes are item models with no item of that id. They are unchanged from the
  3.4.0 baseline, which had 0 errors, 0 warnings and the same 5 notes.
- **NeoForge.** The checker targets 1.20.1. Its errors are 1.21 APIs it does not know, and
  `textures/item/iron_glyph_icon/...` sprites that the `ars_n_spells:iron_glyph_icons` atlas source
  builds at runtime from each spell's own icon. There are 1,110 of those, one per model; the 3.4.0
  baseline had 777. The only other new findings are 1.21 APIs in files 3.4.1 added. Apart from line
  numbers, the rest matches the 3.4.0 baseline.

## What this evidence does not cover

- No client was launched. That leaves untested: two clients converging after a reload, the JEI and
  editor refresh on a client, and accessibility. The sync codecs, digests and refresh hooks are
  covered only by unit tests and by the GameTests' server side.
- The packaged JARs were inspected but not booted in a separate production install.
- No Iron's addon was installed. Cataclysm Spellbooks and Alshanex's Familiars are not certified.
  The addon path is exercised by the shared catalog fixtures and by the pack tool's tests.
- Performance with large catalogs and repeated reloads was not measured.
- The optional improvements (section K of the instructions) were not started.

## 2026-11-10: the review implementation

Recorded 2026-11-10 from both checkouts, still uncommitted, after the changes made for the
2026-11-09 review (`docs/ars-n-spells-review-2026-11-09-implementation-brief.md`). The rules above
still apply: every count was read from the run's own output, and every GameTest log passed
`tools/verify_gametest_log.py`. Its expected counts come from the suite, not from the log. Each
GameTest profile ran in a run directory never used before (`-PgametestRunDir=run-qa-<profile>`),
so every world was new. Evidence is in the Forge checkout, under `logs/3.4.1/review-1109/`
(git-ignored): `qa/` holds the builds, GameTest logs and verifier output, and `boot/evidence/`
the server boots.

| Check | Forge 1.20.1 | NeoForge 1.21.1 |
|---|---|---|
| Tool tests: `python3 -m unittest discover -s tools -p 'test_*.py'` | 58 tests, OK | 52 tests, OK |
| `build --offline` through `gradlew-quiet.sh` (unit tests, catalog packages, JARs) | PASS; 1,061 unit tests, 0 failures, 0 errors, 0 skipped | PASS; 1,063 unit tests, 0 failures, 0 errors, 0 skipped |
| Artifacts | `build/libs/ars_n_spells-3.4.1.jar` and `-slim.jar`; `build/catalog-packages/` | `build/libs/ars_n_spells-3.4.1.jar` and `-sources.jar`; `build/catalog-packages/` |
| Assets and language: `python3 .mcmod-tools/check_mod.py <checkout>` | 0 errors, 1 warning, 5 notes, the same findings as recorded above | exit 1: 1,401 errors, 2 warnings, 5 notes, all from the checker's 1.20.1 rules (see below) |

`build/catalog-packages/` holds `ans_catalog_wind_spellbooks-1.0.0.jar`,
`ans_catalog_irons_envelope_15-1.0.0.jar`, the same two catalogs as loose files, and their reports.

### GameTests

Every profile passed all its required tests: 344 on Forge and 472 on NeoForge. The scenario
columns count optional-mod scenarios, not tests. Each optional mod's scenarios run only when that
mod is installed.

| Build | Profile | Flags | Scenarios executed / not exercised |
|---|---|---|---|
| Forge | Iron's absent | none | 1 / 282 |
| Forge | Iron's `1.20.1-3.15.0` | `-PwithIronsRuntimeGameTests` | 241 / 42 |
| Forge | Iron's `1.20.1-3.16.3` | `-PwithIrons316RuntimeGameTests` | 241 / 42 |
| Forge | Covenant `2.2.6-hotfix` with Iron's 3.15.0 | `-PwithIronsRuntimeGameTests -PwithCovenantRuntimeGameTests` | 247 / 36 |
| Forge | Wind's Spellbooks `1.0.0` with Iron's 3.16.3 | `-PwithIrons316RuntimeGameTests -PwithWindSpellbooks -PwithCatalogPackages=wind_spellbooks` | 250 / 33 |
| Forge | Wind's Spellbooks `1.0.0` with Iron's 3.15.0 | `-PwithIronsRuntimeGameTests -PwithWindSpellbooks -PwithCatalogPackages=wind_spellbooks` | 250 / 33 |
| Forge | Levels XI-XV with Iron's 3.16.3 | `-PwithIrons316RuntimeGameTests -PwithCatalogPackages=irons_envelope_15` | 246 / 37 |
| NeoForge | Iron's absent | none | 1 / 401 |
| NeoForge | Iron's `1.21.1-3.16.3` | `-PwithIronsRuntimeGameTests` | 361 / 41 |
| NeoForge | Wind's Spellbooks `1.0.1` | `-PwithIronsRuntimeGameTests -PwithWindSpellbooks -PwithCatalogPackages=wind_spellbooks` | 370 / 32 |
| NeoForge | Levels XI-XV | `-PwithIronsRuntimeGameTests -PwithCatalogPackages=irons_envelope_15` | 366 / 36 |

Each log's profile lines name the mods and versions it ran with. Absent mods are listed as
absent: Ars Elemental, Ars Zero and Too Many Glyphs on Forge, and Ars Elemental, Ars Zero, Ars
Elemancy and Ars Affinity on NeoForge.

The two earlier runs of this matrix found three defects, all in tests. Each was fixed before this
run, and the earlier logs are kept beside the final ones (`qa/*-run1`, `qa/*-run2`):

- The NeoForge port of the mid-flight deny-rule scenario expected a refusal record. A rule that
  disables a glyph makes Ars itself skip the part (`ImportedIronEffect.isEnabled`), so the runtime
  never sees it: no effect runs and the hold is returned. The test now asserts exactly that, as
  the Forge test does (all three NeoForge Iron's profiles).
- On Forge, in the Wind profile with Iron's 3.16.3, the Self-only phase of the same scenario found
  the pig hurt, although the log shows the Firebolt refused and its hold refunded. The test now
  also asserts that no Firebolt ran, and names the damage source if the health check fails again.
  It passed in both later runs.
- On Forge, in the Wind profile with Iron's 3.16.3, `bindingRitual_bindsDroppedScrollOntoDroppedBook`
  finished with no entry bound. Five ritual tests shared one batch, and a ritual scans a 7x7x7 box
  that overhangs its 5x5 platform, so one test's ritual could reach the next test's items. Each
  ritual test now has a batch of its own, on both builds.

### Packaged JARs on production servers

`logs/3.4.1/review-1109/tools/boot_server.sh` booted the packaged JARs on servers built from the
official installers:
Forge 47.4.10 (SHA-1 checked against the Forge Maven) and NeoForge 21.1.248 (SHA-256 checked against
the NeoForge Maven). For each profile it put only that profile's JARs in `mods/` and started a new
world, then ran `ans glyph report` from the console and stopped the server. The other JARs are the
same artifacts the GameTest profiles use, taken from the Gradle cache.

| Build | Profile | Mods with ANS | Result |
|---|---|---|---|
| Forge, Java 17 | Iron's absent | Ars Nouveau 4.12.7, Curios 5.14.1+1.20.1, GeckoLib 4.7.1.3 | Done; 111 spells, 1,110 glyphs |
| Forge | Iron's 3.15.0 | the same, Iron's 1.20.1-3.15.0, PlayerAnimator 1.0.2-rc1+1.20 | Done; 1,110 glyphs; 119 registry spells accounted for |
| Forge | Iron's 3.16.3 | Ars Nouveau, Curios, GeckoLib 4.8.3, Iron's 1.20.1-3.16.3, PlayerAnimator, irons_lib 1.20.1-2.1.0 | Done; the same |
| Forge | Wind's Spellbooks | the 3.16.3 set, Wind's Spellbooks 1.20.1-1.0.0, `ans_catalog_wind_spellbooks` 1.0.0 | Done; 118 spells, 1,180 glyphs; 116 supported, 2 unsupported |
| Forge | Levels XI-XV | the 3.16.3 set, `ans_catalog_irons_envelope_15` 1.0.0 | Done; 1,665 glyphs |
| NeoForge, Java 21 | Iron's absent | Ars Nouveau 5.13.1.1400, Curios 9.3.1+1.21.1, GeckoLib 4.7.5.1 | Done; 111 spells, 1,110 glyphs |
| NeoForge | Iron's 3.16.3 | the same, Iron's 1.21.1-3.16.3, irons_lib 1.21.1-2.1.0, PlayerAnimator 2.0.4 | Done; 1,110 glyphs; 119 registry spells accounted for |
| NeoForge | Wind's Spellbooks | the Iron's set, Wind's Spellbooks 1.0.1, `ans_catalog_wind_spellbooks` 1.0.0 | Done; 118 spells, 1,180 glyphs; 116 supported, 2 unsupported |
| NeoForge | Levels XI-XV | the Iron's set, `ans_catalog_irons_envelope_15` 1.0.0 | Done; 1,665 glyphs |

Every server stopped on `stop` with exit code 0, and the Wind and envelope servers wrote their
retained catalog copies. None of the NeoForge servers logged an ERROR line. The Forge servers with
Iron's logged two or three ERROR lines each, all about Iron's own data:

- with 3.16.3, the loot tables `chests/catacombs/crypt_loot` and `chests/citadel/citadel_tomes` do
  not parse;
- with 3.15.0, `chests/citadel/citadel_tomes`, `chests/trial_chambers/additional_ominous_vault_loot`
  (it names the item `irons_spellbooks:expulsion_ring`, which that build does not register) and
  the recipe `expulsion_ring` do not parse.

The same lines appear in the development GameTest runs.

### Both checkouts

| Check | Command | Result |
|---|---|---|
| Shared behaviour | `python3 tools/verify_loader_parity.py --neoforge <NeoForge checkout> --section behaviour`, and `--forge <Forge checkout>` from the other checkout | PASS from both checkouts: 63 shared fixture cases in 67 identical files, 13 identical tool modules, and 1,110 catalog identities and 1,110 ledger ids that agree |
| Full parity audit | `python3 tools/verify_loader_parity.py --neoforge <NeoForge checkout> --report ...` | FAIL, as recorded since 3.4.0: shared behaviour PASS, 163 reviewed platform exceptions, 494 source-level differences |

The shared fixtures now hold 18 mapping cases, 36 catalog cases and 9 effective-level cases. The
42 new platform exceptions are this review's one-sided GameTests, each listed with its counterpart
on the other build or the reason it has none. The 11 new source-level differences are translation
keys that follow each build's own naming, and NeoForge's dormant glyph model, which Forge has no
use for.

NeoForge's `check_mod.py` findings come from rules for Forge 1.20.1. They are:

- 1,110 `textures/item/iron_glyph_icon/...` sprites, which the atlas source builds at runtime;
- 283 uses of 1.21 and NeoForge APIs;
- 8 structure findings: the 1.21 folder names, the pack format, the Java version and the NeoForge
  metadata file;
- two warnings that are 1.20.1 heuristics: a loot-table path, and a JEI lambda that only runs on
  the client.

### What this evidence does not cover

- No game client was launched. These were not run:
  - a single-player owner changing the mode without cheats, a LAN guest refused, and two remote
    clients converging after a change;
  - JEI after a tag-only reload, a reconnect, and a hundred reloads.

  The authority rule, the settings service and the sync codecs are covered by unit tests and by
  the server side of the GameTests. The packaged JARs were booted on dedicated servers only.
- The Magma Bomb report was not reproduced. On both builds, the full-lifetime GameTests trace
  every debit through launch, the fire field, the entity's removal and 40 ticks after it, in all
  five mana modes, with Touch, two casts, creative, regeneration on, Poison Splash and Fire
  Breath's pulses. They found one payment per execution and nothing after it. Without the
  reporter's recipe and addons, the report stays open.
- On Iron's 3.16.3, Wind's Spellbooks' Tornado fails natively, and Iron Slash has no adapter. Both
  are dormant, with the reason recorded in the package's adapters file.
- Performance with large catalogs and repeated reloads was not measured.
- Nothing was published, uploaded, merged or released.
