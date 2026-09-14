# Audit validation record

Date: 2026-09-05. This record describes tests actually executed, separately from future acceptance tests in the specification.

## Source identity

- Forge `main`: `2578bad5d820fc2de9f38c26c92833a0d977c441`, ANS 3.3.0.
- NeoForge `port/neoforge-1.21.1`: `eb6d3af883b5386785e7e4d2793871f11f3830ce`, ANS 3.2.5.

Both matched GitHub branch metadata during the audit. The Neo branch and gameplay runs used extracted source archives in an isolated temporary directory. The Forge fresh unit run used the local checkout, whose tracked source matched the pinned commit. Existing `.claude/` and `MODMAP.md` were left untouched. No tracked mod source was patched for these runs.

Java 17 was Eclipse Adoptium 17.0.20.8. Java 21 came from the existing Gradle Adoptium toolchain. Gradle dependency resolution ran offline against the existing user cache; this does not prove a clean online dependency bootstrap on another machine. Exact inspected dependency hashes are in [dependency-evidence.csv](dependency-evidence.csv).

## Commands and outcomes

Commands below omit host-specific `JAVA_HOME` and `GRADLE_USER_HOME` assignments. Windows used `gradlew.bat`; Unix equivalents use `./gradlew`. Each loaded/absent configuration should have its own world/config directory in future CI.

| Target | Command | Observed result |
|---|---|---|
| Forge compile/unit baseline | `gradlew.bat compileJava test --offline --no-daemon` | Successful; cached tasks initially up to date |
| Forge fresh unit execution | `gradlew.bat test --rerun-tasks --offline --no-daemon` | Successful; 272 tests / 64 suites / 0 failures / 0 errors / 0 skipped; compile rerun |
| Neo compile/unit | `gradlew.bat compileJava test --offline --no-daemon` | Successful; 312 tests / 57 suites / 0 failures / 0 errors / 0 skipped |
| Forge loaded | `gradlew.bat runGameTestServer -PwithIronsRuntimeGameTests -PwithArsElemental --offline --no-daemon` | 74 tests completed; one required failure |
| Neo loaded | `gradlew.bat runGameTestServer -PwithIronsRuntimeGameTests -PwithArsElemental -PwithArsElemancy -PwithArsZero --offline --no-daemon` | 71 tests completed; four required failures |
| Neo absent | `gradlew.bat runGameTestServer --offline --no-daemon` | All 71 required tests passed; optional paths self-skip where applicable |
| Forge absent, reused loaded world | `gradlew.bat runGameTestServer --offline --no-daemon` | Failed world decoding; no completed suite despite BUILD SUCCESSFUL |
| Forge absent, fresh world | `gradlew.bat runGameTestServer --offline --no-daemon` | 74 tests completed; one required failure |

The reused Forge world was preserved under an audit backup name before the fresh-world retry. No user gameplay world was touched.

## Runtime evidence and attribution

### Forge loaded

[Full log](evidence/forge-loaded-gametest.log), lines 1802–1806:

```text
bindingritual_bindsdroppedscrollontodroppedbook failed!
the binding ritual must append the carrier's Ars entry to the book, found 0 entries
74 GAME TESTS COMPLETE
1 required tests failed
```

Source shows a happy-path ritual and a test holding the global binding flag false running in the same asynchronous batch. This is a strong suspected test-isolation cause, not a runtime proof of the precise cause. It was not patched/retested during this audit. The implementation agent must isolate and rerun it before deciding whether a production ritual bug remains.

The same log includes upstream Iron’s invalid recipe/loot references (`minecraft:wind_charge`, `irons_spellbooks:expulsion_ring`). Their namespaces and resource paths identify them as upstream resources. Do not count them as ANS-owned recipe defects without additional evidence.

### Neo loaded

[Full log](evidence/neo-loaded-gametest.log), lines 165–167, 237, 245, 248–249:

```text
ironsloaded_aboundbook_isnotaproxyonlystack failed
bindthenunbind_leavesnotrace failed
ironsloaded_validcarrier_isrejectedfromnativetable failed
bindingritual_bindsdroppedscrollontodroppedbook failed
71 GAME TESTS COMPLETE
4 required tests failed
```

The native-book-visibility and native-table-policy fixtures construct vanilla `Items.BOOK` while asserting native Iron’s book/scroll behavior. The cleanup test also uses a vanilla book and exposes native-component residue from that synthetic setup; verify the intended generic helper contract separately before classifying it as fixture-only or a production cleanup defect. Do not weaken native carrier detection to make the two mismatched fixtures pass. Binding has the same shared-global-config concern as Forge.

### Forge absent, fresh world

[Full log](evidence/forge-fallback-fresh-gametest.log), lines 240–244:

```text
everyoneshotritual_actuallyfinishes failed! io.redspace.ironsspellbooks.api.magic.MagicData
74 GAME TESTS COMPLETE
1 required tests failed
```

`RitualLifecycleGameTests.everyOneShotRitual_actuallyFinishes()` calls `CrossCastGameTests.emptyHandedPlayer()`, which unconditionally calls `IronsProxyCastDriver.setIronsMana/equipSpellbook/resetCastingState`. This is an optionality defect in test setup. The server itself boots and runs the suite. General gameplay with all optional mods absent still needs real-client verification.

### Neo absent

[Full log](evidence/neo-fallback-gametest.log) records all 71 required tests passed. Tests returning `helper.succeed()` when a mod is missing are not exercised compatibility scenarios. Record explicit skipped counts in the revised harness.

### Forge existing-world removal

[Full log](evidence/forge-removal-existing-world.log), near lines 2344–2365, reports a missing `irons_spellbooks:pocket_dimension_type` while decoding world settings. Gradle nevertheless prints BUILD SUCCESSFUL. This demonstrates why absence startup, saved-world removal, and process exit code must be separate checks. It does not establish an ANS dimension bug.

## Upstream inspection

Selected classes from cached dependency jars were inspected with Vineflower 1.10.1 and `javap`. Decompiled sources were kept in the temporary audit area and are not redistributed here. Reproduction requires the exact jars identified by hashes, not an assumed latest upstream source tree.

| Inspected contract | Evidence used |
|---|---|
| Ars 4.12.7 `SpellResolver` | Fresh `SpellCostCalcEvent` in each `getResolveCost`; both canCast and expendMana call it; onCast result and event ordering |
| Ars 5.13.1.1400 resolver/mana utilities | Neo spell context/codec and native mana/max/regen paths |
| Ars `SpellDamageEvent.Pre` | Exists in both inspected Ars versions and carries contextual damage information; permits a real Forge backport surface |
| Iron’s `CastSource` | SCROLL consumes no mana and does not respect book/sword cooldown; actual enum behavior inspected on both jars |
| Iron’s `AbstractSpell` | Initiation, pre-cast checks, on-cast event, native mana debit conditional on source, actual onCast, recast and cooldown flow |
| Iron’s `MagicData` | Native state/max/clamping and casting-item access relevant to bridge/proxy logic |
| Ars mana/perk utilities | Native base/tier/glyph regen contribution can appear in the live attribute aggregate |
| Elemental/Elemancy selected classes | School/filter/effect metadata examined for version-specific analysis; not a claim of exhaustive execution of every addon spell |

No benchmark, graphical UI/session, JEI/EMI runtime client test, complete Covenant gameplay pack, or cross-Minecraft world migration was executed. Those limitations are release gates, not inferred passes.

## Artifact verification

`build_audit_artifacts.py` validates immutable source-reference paths and line bounds, emits per-file hashes, inventories PNG headers, compares asset identities, creates the inspection contact sheet, and generates the proposed icon manifest. The supplied source snapshots are inputs; the generator does not fetch or modify mod sources.

All 33 existing PNGs are 16×16 and byte-identical between loaders. The proposed manifest has 274 distinct base IDs, with 107 P1 and 167 P2 entries. Proposed assets have not been created; their state/size variants are implementation requirements.

The static resource scanner parsed 28 JSON files per loader with zero syntax failures and found no missing ANS key among its direct literal `Component.translatable` matches. Forge has 165 English keys, Neo 184. Dynamic key construction, registry validity, hardcoded English, native resource schemas and recipe runtime are outside that limited check. [builtin-school-mappings.csv](builtin-school-mappings.csv) enumerates explicit maps only; real effective school resolution also uses metadata, heuristics and datapacks.
