# Casting repair validation — 3.3.4

All runs used disposable directories under `/tmp`; no player world was used. Runtime launches used the Gradle mapped development environment; packaged JAR metadata, class versions, and bundled dependencies were inspected separately. Dates and timestamps in retained logs are local to the test host (2026-09-13). This report distinguishes pure accounting checks, transformed server tests, and actual clients.

## Versions and hashes

| Component | Forge | NeoForge |
|---|---|---|
| Minecraft | 1.20.1 | 1.21.1 |
| Loader | Forge 47.4.10 | NeoForge 21.1.248 |
| Java runtime/compiler | Arch OpenJDK 17.0.19 | Adoptium 21.0.12.1+1-LTS |
| Ars Nouveau | 4.12.7, file 6688854 | 5.13.1.1400 |
| Iron's | 1.20.1-3.15.0, file 7402504 | 1.21.1-3.16.3 |
| MixinExtras bundled | 0.5.4 Forge | 0.5.3 NeoForge |

`release-dependencies.json` records the original Ars/Iron's release JAR SHA-256 values and embedded metadata. `forge-runtime-inventory.json` and `neoforge-runtime-inventory.json` record all resolved runtime artifacts, including mapped development artifacts (111 and 112 entries). Those inventories are not claims that every library is a separate loaded mod. `covenant-profile.json` records the exact six additional release JARs and hashes: Covenant 2.2.6-hotfix, Enigmatic Legacy 2.30.1, Blood Magic 3.3.3-45, Nature's Aura 39.4, Caelus 3.1.0+1.20, and Patchouli 1.20.1-81-FORGE.

## Results

| Check | Forge | NeoForge |
|---|---|---|
| Original production + new native regressions | 5 required failures | 5 required failures |
| Final complete JUnit suite | 866 discovered, 860 executed, 6 skipped; 0 failures/errors | 864 executed; 0 failures/errors |
| Iron's-loaded GameTest server | 123 required green; gated instrumentation executed 74 / skipped 19 | 111 required green; gated instrumentation executed 61 / skipped 13 |
| Iron's-absent GameTest server | 123 required green; gated instrumentation executed 1 / skipped 92 | 111 required green; gated instrumentation executed 1 / skipped 73 |
| Covenant-loaded GameTest server | 123 required green; gated instrumentation executed 75 / skipped 18 | Not applicable: integration not present on port |
| Integrated server + actual client | PASS: heal, server mana 970, client HUD mana 970, native cooldown, cast cleared | PASS: same assertions |
| Dedicated server + actual client | PASS on 127.0.0.1:25574 | PASS on 127.0.0.1:25575 |
| Shared-contract parity | 33 canonical contract sources/fixtures match both checkouts | Same |

A green required-test total includes intentional absent-mod skips. The `ANS-GAMETEST` counters instrument gated tests, not the complete number of assertions or all test methods. Only the loaded profile proves Iron's execution. The explicit runtime profile checks its requested mod environment; absent operation is reported separately.

The 14-method payment-boundary unit suite covers double and float precision, exact/slightly insufficient/zero funds, split payments, partial/excess/vetoed debits, mutation followed by false/exception, second-leg failure, full/partial/throwing/offline refunds, unknown movement, restored remaining obligations, duplicate/late/reentrant callbacks, validation errors after mutation, bounded retry diagnostics, and alternative-resource compensation. Existing alternative-policy, quote, bridge, cooldown, fixture, and lexical validation tests also pass.

Native runtime fixtures use an ordinary equipped Iron's book with its native selection/initiation/ticker path, across disabled/separate/ars_primary/iss_primary/hybrid and final event prices zero, one, and above base price. Healing and exact pool deltas are asserted, not just animation. Existing fixtures cover native Ars healing and cross-cast price conversions. Fire Breath creates an actual projectile before a later interrupted pulse; first failure creates none. Book/scroll consumption and native/category cooldowns are checked for first versus later failure. Flaming Barrage creates native projectiles, pays its initial cast once, preserves exempt native recasts, and delays both cooldowns until sequence end. Native mana veto, duplicate write, ceiling drift, adventure/cooldown restrictions, mode changes, unchanged refresh, event reset/throw/reentry, and a controlled native Heal override throwing after world mutation are directly asserted.

The Covenant fixture equips actual ring items in Curios, casts the native heal spell, checks exact Soul Network LP or ambient aura movement, toggles each alternative path off to verify native mana payment, and checks cooldown and empty reservation state. The existing refuse/native-fallback/legacy-open and alternative fault behaviors have contract coverage; this does not claim an exhaustive runtime permutation of every ring policy.

Client smoke tests open only the copied `ANS Casting 334 QA` save or an explicit QA address. The server sets a controlled survival caster and ordinary native book. Dedicated casting starts after login initialization; native regeneration is zero in the disposable QA world so the exact final balance is stable. Client assertions read Iron's `ClientMagicData`, the actual HUD/cooldown cache, and require 20 stable ticks. The `ANS_CAST_SERVER_PASS` and `ANS_CAST_CLIENT_PASS` lines are retained independently.

## Commands

Worktree paths: `/tmp/ans-3.3.4-forge` and `/tmp/ans-3.3.4-neoforge`. `./gradlew` resolves the configured Java 17/21 toolchains. Resolved dependencies were inspected before the final offline runs.

```sh
# Forge clean build and full tests; then final bundled artifact/refund guard check
./gradlew --offline clean test build jarJar reobfJarJar
./gradlew --offline test jarJar reobfJarJar runGameTestServer -PgametestRunDir=/tmp/ans-334-release-forge-absent --quiet
./gradlew --offline runGameTestServer -PwithIronsRuntimeGameTests -PgametestRunDir=/tmp/ans-334-release-forge-loaded --quiet
./gradlew --offline runGameTestServer -PwithIronsRuntimeGameTests -PwithCovenantRuntimeGameTests -PcovenantRuntimeDir=/home/otectus/Projects/ars-n-spells/libs/covenant-3.3.0 -PgametestRunDir=/tmp/ans-334-release-forge-covenant --quiet

# NeoForge clean build, final tests, and independent loaded/absent profiles
./gradlew --offline clean test build
./gradlew --offline test
./gradlew --offline runGameTestServer -PwithIronsRuntimeGameTests -PgametestRunDir=/tmp/ans-334-release-neo-loaded --quiet
./gradlew --offline test --tests com.otectus.arsnspells.casting.NativePaymentBoundaryTest jar runGameTestServer -PgametestRunDir=/tmp/ans-334-release-neo-absent --quiet

# Shared contract manifest, compared before generation, then verified
python3 tools/contract_parity.py --other /tmp/ans-3.3.4-neoforge --write
python3 tools/contract_parity.py --other /tmp/ans-3.3.4-neoforge
```

Actual client commands used `runClient -PwithIronsRuntimeGameTests -PwithCastingClientSmoke -PgametestRunDir=/tmp/ans-334-client-forge` and the equivalent `...client-neoforge`. Dedicated servers used `runServer -PwithIronsRuntimeGameTests -PwithCastingServerSmoke -PgametestRunDir=/tmp/ans-334-dedicated-<loader>`. Matching clients added `-PcastingSmokeAddress=127.0.0.1:25574` (Forge) or `:25575` (NeoForge), using `...client-<loader>-dedicated`. All commands included `--offline --quiet`. The server properties bind loopback and use an offline development login only in those disposable test directories.

Original-baseline checkouts at `/tmp/ans-334-baseline-forge` and `/tmp/ans-334-baseline-neoforge` contained original production sources plus the six initial hotfix runtime fixtures adapted to the old public API. Their loaded `runGameTestServer` commands used `/tmp/ans-334-baseline-runtime-forge` and `/tmp/ans-334-baseline-runtime-neo` respectively. No production fix was backported into those controls.

## Evidence and limits

The accompanying `evidence-index.json` records source log paths, retained archive paths, SHA-256 values, and pass/failure summary lines. Runtime logs and JUnit XML are in the local release evidence archive. Probe output is `payment-boundary-probe-results.json`. Bytecode disassemblies and exact dependency inventories are included in that archive. Intermediate fixture failures are explained in the root-cause report; a successful Gradle process alone was never counted as a casting pass.

The pinned Forge Iron's artifact emitted native resource/recipe errors in the baseline as well as patched runs (including `minecraft:wind_charge` and `set_written_book_pages`). These did not prevent the measured casts and are not attributed to this patch. Development refmap and optional client-resource warnings also remain in raw logs.

No original player pack, verified One Mana Bar/Mana and Artifice combination, full third-party addon matrix, exhaustive native target/unlearned/disabled-spell permutations, or comprehensive death/respawn/dimension runtime sequence was certified. See the root-cause report for the exact evidence boundary and recovery limitations. No version range was broadened, and no public release/tag/upload was made.

Install the bundled Forge `ars_n_spells-3.3.4.jar` (the JarJar/reobfuscated output) or the NeoForge release JAR for its Minecraft version. The explicitly classified Forge slim artifact, when generated for development, does not contain the required wrapper runtime. Release copies use loader/Minecraft suffixes to avoid accidental mixing. Both packaged JARs contain their loader's MixinExtras dependency and declare ANS 3.3.4.
