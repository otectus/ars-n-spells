# Testing Report — 3.0.1 Audit Remediation (2026-07-05)

Environment: Linux, OpenJDK 17.0.19 (`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`; machine default is JDK 25 and must not be used), Gradle 8.4 wrapper, `--no-daemon`.

## Automated verification (all run, all passed)

### 1. Baseline before any change
```
./gradlew compileJava test        → BUILD SUCCESSFUL
```
Established that `main@5d50eac` was green before remediation.

### 2. Per-phase gates
`./gradlew compileJava test` after every phase (recipes/tag, i18n + dead-code removal, gating unification, ConfigScreenFactory move, mixin deletion) — **BUILD SUCCESSFUL each time**.

### 3. Clean full build + unit suite (post-remediation)
```
./gradlew clean compileJava test  → BUILD SUCCESSFUL
```
48 test classes, **174 test cases, 0 failures** (includes the new `ApparatusRecipeIngredientTest` — 3 cases — and `MixinScrollItemLocalizationTest` — 2 cases).

### 4. GameTest server, Iron's-less profile
```
rm -rf run/world && ./gradlew runGameTestServer
```
Result: **"All 11 required tests passed"** (Iron's-dependent bodies self-skip by design). Log verified clean: zero `Parsing error loading recipe`, zero `JsonSyntaxException`, zero tag-load errors — proving the new `ars_n_spells:irons_spell_books` tag (all-optional entries) loads safely without Iron's, and the mixin-plugin gating still boots after the `MixinIronsManaBarOverlay` deletion.

Note: the first Iron's-less attempt crashed on a **stale dev world** (`run/world` had been created by a previous Iron's-loaded run and contained `irons_spellbooks:pocket_dimension`, which cannot deserialize without Iron's). This is a dev-environment artifact, not a mod defect; deleting `run/world` resolved it. Real servers removing Iron's from an existing world will hit the same vanilla-level dimension error — that is Iron's data in the world save, outside this mod's control.

### 5. GameTest server, Iron's-loaded profile
```
./gradlew runGameTestServer -PwithIronsRuntimeGameTests
```
Iron's presence confirmed in-log (`StartupValidator: OK Iron's Spellbooks detected`, `BridgeManager: Iron's Spellbooks Detected: true`). Result: **"All 11 required tests passed"** with the Iron's-dependent bodies executing for real (cross-cast + export/bind suites).

**F1 regression proof**: before the fix, this exact profile logged
`Parsing error loading recipe ars_n_spells:apparatus/spell_transcription — JsonSyntaxException: Unknown item 'irons_spellbooks:spell_book'` (and the same for `spellbook_binding`). After the fix: **zero** recipe parsing errors and zero tag errors in `run/logs/latest.log`.

## Not run here — requires manual verification

These need an interactive client or a real multiplayer topology that this environment cannot drive:

1. **In-game ritual craft (F1 end-to-end)**: craft the Spell Transcription and Spellbook Binding tablets at the enchanting apparatus using two different book tiers on the pedestal (e.g. `copper_spell_book` and `netherite_spell_book`) to observe the tag matching; also confirm JEI shows the tag's book cycle.
2. **Config screen after the F3 move** (`TESTING_GUIDE.md` S18): opens from the mods list, renders correctly at GUI scales 1–3/Auto, read-only on a dedicated server, live mode apply in singleplayer.
3. **Single mana bar without the deleted mixin** (F12): in `ars_primary` and `hybrid` (non-Iron's) modes, exactly one mana bar renders; Iron's bar stays hidden.
4. **F13 toggle matrix** (needs Covenant of the Seven installed): `enable_lp_system=false` → Cursed-Ring scroll casts charge mana (not LP) and Covenant's native handling runs; `enable_lp_system=true, enable_mana_unification=false` → LP is charged exactly once (no Covenant double-penalty/death).
5. **`mana_unification_mode="disabled"` with master toggle on** (F5): equipment mana bridging, Source Jar synergy, and mana-bar hiding are all inactive.
6. **Dedicated-server multiplayer** smoke of cross-cast/export flows (gametests run on an integrated test server; a real dedicated topology with a separate client remains the guide's S1/W1 scenarios).
7. **`/reload` + ritual tablet use** (F6 belt-and-suspenders): after `/reload`, activate a transcription tablet at a brazier — expected to work (verification was static: Ars never rebuilds `ritualItemMap`).
8. Covenant/Blood Magic/Nature's Aura-loaded runs (reflection integrations) — unchanged by this pass but listed in `TESTING_GUIDE.md` S3–S5, S12–S14.

## Known remaining risks

- The Covenant HUD mixin remains bytecode-pinned to Covenant 2.2.6 (documented fragility, unchanged).
- `SanctifiedLegacyCompat` reflection failures still fail open (casts free) by design.
- The `storePendingScrollLP` deletion breaks any third-party **reflective** caller (none found in any source; noted in CHANGELOG).
