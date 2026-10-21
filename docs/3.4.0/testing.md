# Testing record (3.4.0, NeoForge 1.21.1)

What was run for 3.4.0 on this checkout, with the exact commands, counts and evidence, and what was not run.

## Build under test

| | |
| --- | --- |
| Repository | `ars-n-spells_1.21.1`, branch `port/1.21.1-neoforge` |
| Commit | `087ed3d` ("fix: staff casts of bound Ars spells and payments above the ceiling (3.3.6)"), plus the uncommitted 3.3.7 work and the uncommitted 3.4.0 work. Nothing was committed. |
| Version | `mod_version=3.4.0`; network protocol 9 |
| Minecraft / loader | 1.21.1, NeoForge 21.1.248, Parchment 2024.11.17 |
| Required | Ars Nouveau 5.13.1.1400 |
| Optional | Iron's Spells 'n Spellbooks 1.21.1-3.16.3 (Iron's Lib 1.21.1-2.1.0), Curios 9.3.1+1.21.1, GeckoLib 4.7.5.1, playerAnimator 2.0.4+1.21.1, JEI 19.44.0.403 |
| Addon profiles | Ars Elemental 0.7.10.1 (CurseForge file 8399862), Ars Affinity 1.1.1 (7416588), Ars Zero 2.0.2 (8703997), Ars Elemancy 1.18.3 (8349780) |
| Java | Eclipse Adoptium 21.0.12.1 (toolchain 21) |
| Machine | Linux 7.2 (CachyOS), 24 threads |

Every Gradle command below ran through `/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh <checkout> <task> <properties>`. It runs the repository's Gradle wrapper with that task and those properties, prints PASS or FAIL, and keeps the full Gradle log under `/tmp/gradle-ars-n-spells_1.21.1-<task>-<time>.log`. Tasks and properties are exactly those in the 3.4.0 instructions.

## Results on the final code

Every run below used the same, final source (20 October 2026, 21:15 to 21:25 EDT).

| Check | Command | Result |
| --- | --- | --- |
| Build and JUnit | `gradlew-quiet.sh <checkout> build` | PASS. `:test` ran: 977 tests, 0 failures, 0 errors, 0 skipped. Jar `build/libs/ars_n_spells-3.4.0.jar` (3,200,082 bytes, SHA-256 `8778166fc6362a2dfec40f036d4a11df90ce09f648323f22480605bed298f2a3`) and `ars_n_spells-3.4.0-sources.jar` |
| GameTests, Iron's absent | `runGameTestServer -PgametestRunDir=run-glyph-press-no-irons` | All 407 required tests passed. Optional scenarios: 1 executed, 340 not exercised (314 of them need Iron's). Iron's and every addon reported absent. |
| GameTests, Iron's loaded | `runGameTestServer -PwithIronsRuntimeGameTests -PgametestRunDir=run-glyph-press-irons` | All 407 passed. 315 executed, 26 not exercised (only scenarios for the absent addons). Iron's 1.21.1-3.16.3 present. |
| GameTests, Iron's and Ars Affinity | `runGameTestServer -PwithIronsRuntimeGameTests -PwithArsAffinity -PgametestRunDir=run-glyph-press-affinity` | All 407 passed. 328 executed, 13 not exercised (Ars Zero and Ars Elemancy scenarios). Iron's, Ars Elemental 0.7.10.1 and Ars Affinity 1.1.1 present. |
| GameTests, Iron's and the composition addons | `runGameTestServer -PwithIronsRuntimeGameTests -PwithArsElemental -PwithArsZero -PwithArsElemancy -PgametestRunDir=run-glyph-press-addons` | All 407 passed. 336 executed, 5 not exercised (Ars Affinity scenarios). Iron's, Ars Elemental 0.7.10.1, Ars Zero 2.0.2 and Ars Elemancy 1.18.3 present. |
| GameTest log checks | `python3 tools/verify_gametest_log.py <latest.log> --required 407 --executed E --skipped S --present … --absent …` for each profile | All four "Verified 407 required tests", with the expected mods present or absent |
| Tool tests | `python3 -m unittest discover -s tools -p 'test_*.py'` | 27 tests, OK |
| Loader parity (informational) | `python3 tools/verify_loader_parity.py --forge /home/otectus/Projects/ars-n-spells` (read-only) | Fails, 2,800 differences; see "Open gaps" |
| Client on a dedicated server | below | All checks passed |

A GameTest run counts optional-mod scenarios separately from the required tests. A scenario whose mod is absent passes without exercising anything, so each profile above also states how many scenarios actually ran. In the Iron's-loaded profiles, nothing that needs Iron's was skipped.

## What the automated tests cover

The groups are those of the 3.4.0 instructions (section 11.1). GameTests check world effects: a broken block, a hurt mob, a changed pool, an item in a slot. They do not stop at a `true` return.

| Group | Where |
| --- | --- |
| Touch regression | `TouchCastGameTests`, 28 scenarios (listed in `touch-regression.md`); `CastTargetHintTest` (JUnit) |
| Input authority | `TouchCastGameTests` (event overlap, duplicate id, fresh-id spam, stale fingerprint, forged entity and block claims, wrong dimension, unloaded target, hand mismatch, dead sender, offhand carrier); `GlyphPressGameTests#ironsLoaded_press_replayedAndStaleRequestsDoNothing` |
| Conversion | `GlyphPressGameTests`: `convertsTheExactSpellAndLevel`; `refusesEveryInvalidInputUnchanged` (a blank scroll, `none`, a Spell Loom export, an ANS proxy scroll, two spells, levels 0 and 11, a spell Iron's config disables, an unlearned Eldritch spell, no plate, too few catalysts, a book in the scroll slot; each refused by the plan and by Inscribe, every slot unchanged); `waitsForAndPullsSourceFromAJar`; `blockedOutputPausesThenFinishes` (full output). `GlyphPressPlanTest` (JUnit) covers 18 of the plan's reasons, including no adapter, an unsupported spell, an uncatalogued level, a malformed scroll and a cost above the Source capacity. The dormancy reasons (glyphs off, disabled by a data pack, above the server's maximum level) come from the behaviour snapshot (`GlyphMappingsTest`, `disablingGlyphsMakesThemDormantAndReenablingRestores`), and "busy" from a running job (`twoViewersOneLockedJob`) |
| Machine lifecycle | `GlyphPressGameTests`: `twoViewersOneLockedJob`, `cancelReturnsEverything`, `resumesASavedJobOnce`, `waitsForIronsConfigInsteadOfCancelling`, `breakingDropsInputsOnce`, `explosionDropsInputsOnce`, `blockedOutputPausesThenFinishes`, `recipeChangeCancelsTheJob`, `disablingCancelsSafely`, `sourceCapabilityReceivesButNeverProvides`; `GlyphPressJobTest` (JUnit) |
| Glyph identity | `ImportedGlyphIdentityGameTests` (all 777 variants registered as parts under `iron/...` and items under `glyph_iron/...`, the Spell codec round trip, dormancy, learning consumes once, a form that cannot reach the target refused); `ImportedGlyphGameTests#ironsLoaded_bookTierGatesImportedGlyphs`; `ImportedGlyphKeyTest`, `ImportedGlyphCatalogTest` (JUnit) |
| Adapter behaviour | `ImportedGlyphGameTests`: one family test per adapter (self, projectile, entity, position, block, teleport, summon, channel), charge interruption, Eldritch learning, recast stages, native pre-cast veto, last affordable pulse, following Ars effects after a charge, augments (Amplify/Dampen, Accelerate/Decelerate, Extend Time/Duration Down). `ImportedGlyphSmokeGameTests`: every one of the 111 supported spells at its lowest level, and each adapter family at its highest |
| Caster routes | `nonPlayerCaster_isRefusedBeforeEffects`, `turretCaster_isRefusedBeforeEffects`, `projectileImportedEffect_keepsItsSessionAfterItemSwitch` |
| Cost and cooldown | `ImportedGlyphPaymentGameTests`: all five mana modes, equal-percent with unequal maxima, atomic shortage, scroll-free mode and Iron's creative rule, the native cost hook applied once, emission bounds, unsupported augment refused before cost; `selfHeal_paysIronsLegAndStartsNativeCooldownBySpellId`, `recastSpell_paysOnlyTheFirstUse`, `projectileSplit_paysEveryExecution`, `projectileMiss_releasesTheGlyphHoldButKeepsTheArsLeg` |
| Nested casting | `exportedScrollCarryingAGlyph_runsOncePaysEachLegOnce`, `boundBookWheelCarryingAGlyph_keepsTheParentCastIntact`, `glyphDoesNotCancelAnUnrelatedNativeCast` |
| Scaling and training | `glyphTrainsOnceAndFiresEachNativeEventOnce`, `glyphDamageEqualsTheNativeCast`; Ars Affinity: `ArsAffinityInteropGameTests#affinityAndIronsLoaded_importedGlyphAddsNoAffinityProgressOfItsOwn` (Affinity profile) |
| Optional and migration | The Iron's-absent profile (`ironsAbsent_pressAndGlyphsStayRegisteredAndDormant`, identity tests); the existing 3.3.x carrier, Loom and binding suites, passing in every profile |
| Knowledge bonus | `levelVariantsOfOneSpellCountOnceForTheManaBonus`, `learningConsumesOnceAndOnlyWhenAvailable` |
| Sessions | `expiredSession_closesAndReturnsItsHold`, `chargeInterruptedByDamage_releasesItsReservation` (no open session afterwards), the channel and projectile tests (session closed after the last delivery) |

Pure JUnit tests also cover the id encoding and its collision resistance, catalog validation, mapping precedence and fail-closed rules, the pending Iron's config state, Press planning and job persistence, and the charge, launch and pulse arithmetic (`GlyphArithmeticTest`).

## Touch regression reproduction

The reported case was reproduced before the fix, through the real resolver dispatch, in the Iron's-loaded GameTest run `run-340-touch-repro`: the three Touch carrier tests that existed then failed with `resolverEntry=onCast accepted=false`. The excerpt is `evidence/touch-repro-before-fix.txt`, and `touch-regression.md` has the cause, the fix and the 28 scenarios that now pass.

## Client on a dedicated server

These checks used the real client input path against a dedicated server, with Iron's loaded (`-PwithIronsRuntimeGameTests`). The QA classes `GlyphQaServer` and `GlyphQaClient` are inert unless their Gradle profile sets them on. The client acts as a player would. The use key goes through `KeyMapping.click`, and Ars's open-book key through `KeyboardHandler.keyPress`, which fires the key event Ars listens to. Clicks in Ars's spell book editor go through the screen's mouse handling. Slot clicks go through the multiplayer game mode, as container clicks do, and the Press screen's buttons through their own press action. The server judges world state: block states, entity health, slot contents, knowledge, saved spells. Every verdict is an `ANS_GLYPH_QA` log line, and any failure stops the run.

Commands (server on `127.0.0.1:25577`, offline mode, a flat world the server floors itself):

```
gradlew-quiet.sh <checkout> runServer -PgametestRunDir=run-340-qa-server -PwithGlyphQaServer -PglyphQaPhase=1 -PwithIronsRuntimeGameTests
gradlew-quiet.sh <checkout> runClient -PgametestRunDir=run-340-qa-client -PwithGlyphQaClient -PglyphQaClientPhase=1 -PglyphQaAddress=127.0.0.1:25577 -PwithIronsRuntimeGameTests
gradlew-quiet.sh <checkout> runServer -PgametestRunDir=run-340-qa-server -PwithGlyphQaServer -PglyphQaPhase=2 -PwithIronsRuntimeGameTests
gradlew-quiet.sh <checkout> runClient -PgametestRunDir=run-340-qa-client -PwithGlyphQaClient -PglyphQaClientPhase=2 -PglyphQaAddress=127.0.0.1:25577 -PwithIronsRuntimeGameTests
gradlew-quiet.sh <checkout> runServer -PgametestRunDir=run-340-qa-server -PwithGlyphQaServer -PglyphQaPhase=4 -PwithIronsRuntimeGameTests
gradlew-quiet.sh <checkout> runClient -PgametestRunDir=run-340-qa-client  -PwithGlyphQaClient -PglyphQaClientPhase=4 -PglyphQaRole=A -PclientUser=QaA -PglyphQaAddress=127.0.0.1:25577 -PwithIronsRuntimeGameTests
gradlew-quiet.sh <checkout> runClient -PgametestRunDir=run-340-qa-client2 -PwithGlyphQaClient -PglyphQaClientPhase=4 -PglyphQaRole=B -PclientUser=QaB -PglyphQaAddress=127.0.0.1:25577 -PwithIronsRuntimeGameTests
```

The phase 2 client starts only after the phase 2 server logs `restart_waiting`. A small shell runner sequenced these. It retried a client whose mod loading crashed before the game started; see "Open gaps".

### Survival journey and restart (phases 1 and 2, 20 October 2026, 21:15 to 21:17)

All passed; the client and server of both phases exited 0. Logs: `server-phase1.log`, `client-phase1.log`, `server-phase2.log`, `client-phase2.log`.

| Verdict | What was checked |
| --- | --- |
| `touch_block` | A Spell Loom export of Touch → Break, right-clicked at a dirt block, broke that block. |
| `touch_entity` | A Spell Loom export of Touch → Harm, right-clicked at a pig, hurt it (10.0 → 3.93). |
| `press_inputs`, `keyboard` | Shift-click routed the scroll, plates and gems into their slots, and the server reported Ready. Tab focuses the screen's buttons. |
| `press_running`, `press` | Inscribe Glyph started the job, both gauges followed it, the Press consumed the scroll, and the player took the Glyph of Firebolt I. |
| `learn` | Using the glyph learned it on the server and consumed it. |
| `viewer` | JEI opened the Glyph Press category. |
| `glyph_cast` | Touch → the learned Glyph of Firebolt I, cast from an Ars spell book at the pig, struck it (3.93 → 0). |
| `press_tier3_ready`, `press_tier3` | A Firebolt X scroll (tier III), topped up by shift-click to 7 gems, read Ready. The job used exactly one plate, 4 Source Gems and 3,000 Source (jar and buffer 10,000 → 7,000), and made one Glyph of Firebolt X. |
| `press_refusal` | A Spell Loom export in the scroll slot read "This scroll contains an exported Ars spell", Inscribe stayed off, and nothing was consumed. |
| `learn_tier3` | Using the Glyph of Firebolt X learned it. |
| `editor_tier_gate` | Ars's spell book editor, opened with its own key on a novice book, listed the Glyph of Firebolt X and refused it: "Firebolt X is too powerful for your current spell book." |
| `editor_compose` | Clicking Touch, Glyph of Firebolt I and Amplify, then Create, saved that spell to the book (the server read it back). |
| `augmented_cast` | The composed spell ran Firebolt at effective level 2 on a pig (100 → 92.18). |
| `nested_cast` | A Spell Loom export (an Iron's scroll) carrying Touch → Glyph of Firebolt I ran the imported glyph at level 1 (92.18 → 84.96). |
| `editor_full_catalog` | A creative spell book's editor opened with all 862 spell parts (777 imported glyphs); typing "Firebolt" into its search showed the 10 Firebolt glyphs. |
| `restart_saved` (phase 1) | A second Press with a Firebolt II job was saved at progress 30 of 100 when the server stopped. |
| `restart_loaded` (phase 2) | After the restart, with nobody online, the job was back at 49 of 100 with its 500 Source reservation. Iron's config was pending. |
| `restart_waiting` | The finished job waited at 100 of 100 for 60 ticks: not cancelled, no glyph. |
| `restart_completed` | When the client logged in, Iron's config loaded and the job made exactly one Glyph of Firebolt II; nothing stayed reserved. |
| `restart_knowledge` | The player still knew both Firebolt glyphs, and the book's selected spell was still Touch → Glyph of Firebolt I → Amplify. |
| `restart_cast` | That book still cast Firebolt at effective level 2 (82.96 → 75.13). |

Screenshots of this run, cropped to the screen, are in `evidence/screenshots/`: `press-ready.png` (GUI scale 4), `press-help.png`, `press-tier3-ready.png`, `press-refuses-export.png`, `editor-composed.png` and `editor-search-firebolt.png`.

### Two clients at one Press (phase 4, 20 October 2026, 21:17 to 21:19)

Two dev clients, `QaA` and `QaB`, each in its own game directory, joined the same dedicated server. All passed; both clients and the server exited 0.

| Verdict | What was checked |
| --- | --- |
| `duo_b_sees_inputs` | B's open menu showed the scroll, plate and catalyst A had shift-clicked in. |
| `duo_a_ready`, `duo_a_running` | With B watching, A clicked Inscribe twice in the same tick; the Press showed one running job. |
| `duo_b_locked` | While the job ran, B's shift-click and pickup on the locked scroll changed nothing, and Inscribe was off for B. |
| `duo_job` (server) | Exactly one job ran (one operation id). The scroll never left the Press, and neither player held it while the job ran. The job used exactly the scroll, one plate and the catalyst, and made one glyph. |
| `duo_output` (server), `duo_b_took_output`, `duo_a_done` | B took the single glyph (A 0, B 1); A's open menu followed. Nothing was duplicated. |

The clients' screenshots are not reproduced here: a capture shows the last frame rendered, which can lag the menu state the verdict just checked.

### Found by the real-client checks, and fixed

The dedicated-server runs found three Press bugs that the GameTests had not. They need a real dedicated server, the vanilla container click path, or a look at the actual screen. Each is fixed, the first two with a regression test.

- **Restart before the first login.** Iron's builds its spell config at the first data pack sync: on a dedicated server, the first login or `/reload`. Until then its getters answer with fallbacks (maximum level 1, every spell enabled, Common). A Press job that finished in that window was judged on those fallbacks: a Firebolt II job read as "level above the server's maximum" and was cancelled. The first phase 2 runs reproduced it: with nobody online, the saved Firebolt II job ended without a glyph. The cause is in Iron's source: `SpellConfigManager` builds its config in `onDatapackSync`, and `MAX_LEVEL` falls back to 1. Now the glyph behaviour is marked pending until Iron's config is built; it uses Iron's defaults as catalogued, and a finished job waits until the real config has loaded. Tests: `GlyphMappingsTest#anUnbuiltIronsConfigUsesCatalogDefaultsAndMarksTheSnapshotPending` and `GlyphPressGameTests#ironsLoaded_press_waitsForIronsConfigInsteadOfCancelling`.
- **Topping up a slot by shift-click.** Vanilla container merging grows a non-empty stack in place and calls only `Slot.setChanged()`, which never reached the Press's inventory. Open menus kept a stale plan: with 7 Source Gems in the slot, the menu still reported "Requires tier III catalyst materials: 4 Source Gems". The Press was also not marked for saving. The Press's menu slots now report in-place changes. Test: `GlyphPressGameTests#ironsLoaded_press_shiftClickMergeReplansTheOpenMenu`. With the fix disabled, exactly that test fails (`the in-place merge reached the Press`, run `run-340-merge-before`).
- **Help text overflowing its panel.** The **?** panel was a fixed box, and its four paragraphs ran past it over the buttons, the inventory and below the screen (seen in the GUI-scale-4 capture). The help is now a scrollable, clipped panel covering the Press body, with a scrollbar, mouse wheel and keyboard scrolling (`press-help.png`).

## Performance (section 11.4)

| Measure | Result | Where |
| --- | --- | --- |
| 100 idle Presses for 200 ticks | 0.97 ms in total, 48 ns per Press-tick; no Source scans and no planning | `GlyphPressGameTests#ironsLoaded_press_idleCostsNothingAndScansAreBounded` (`ANS-PERF` line) |
| 5 Presses waiting for Source that never comes, 200 ticks | 50 scans (once per 20-tick interval each), within the bound of 55 | same test |
| Chunk forcing | The Press never forces chunks. Apart from the inert QA harness, which forces one chunk to stand in for a chunk loader, nothing in the mod calls `setChunkForced`. | source |
| Catalog startup | 111 spells and 777 glyph variants registered in 12 to 24 ms (four runs); catalog hash `084c6fa50079` | server log, `[ImportedGlyphs]` lines |
| Spell book editor with every spell part | 862 parts (777 imported glyphs): opened in 10.7 ms, typing "Firebolt" into the search took 15.8 ms, 117 frames per second while open | `GlyphQaClient` (`ANS-PERF editor_full_catalog`) |
| Sessions return to zero | An expired session, a charge interrupted by damage, a finished channel and a projectile's last delivery each leave no open session for the caster | `ImportedGlyphGameTests` |

The idle measurement times the Press's own server tick for 20,000 Press-ticks. A world without Presses spends nothing on them, so this is the whole added cost. The 100 idle Presses made no Source scans and no plans; the waiting Presses scanned once per interval (20 ticks), within the test's bound of 55.

## Open gaps

What the 3.4.0 instructions ask for that was not done, or was done only in part:

- **People.** Every client check drove real dev clients through Minecraft's own input handling. No person played.
- **Narration.** This machine has no speech library (`flite`), so narration was not heard. Keyboard focus (Tab) was checked.
- **Native Iron's casts with real input.** The Iron's spell wheel, staff and Curios-book routes are covered by GameTests through Iron's own cast entry points, not by real keypresses in a client.
- **Animations, sounds and HUD.** Cast-time and pulse animations, sound frequency, and the cooldown and mana displays were not judged in a client.
- **Viewers, resource packs, other languages.** The recipe-viewer-absent path, resource-pack overrides, the missing-icon fallback, long names in other languages, and very narrow windows were not exercised in a client. Only `en_us` exists. JEI is always present in this dev runtime, through Ars.
- **Pocket Dimension and Sacrifice.** Their successful paths were not exercised: the GameTest server has no pocket-dimension level, and fake players cannot own summons. Their refusal paths were.
- **Production jar.** The client and dedicated-server checks ran the development runtime. The built jar was not booted in a separate production server.
- **Loader parity.** `tools/verify_loader_parity.py --forge /home/otectus/Projects/ars-n-spells` fails with 2,800 differences. The Forge 3.4.0 work is a separate, in-progress implementation, so its resources, translations, GameTests, configuration bounds and assets differ. Notably, its glyph item ids are `ars_n_spells:iron/...`, where this build follows the instructions' proposal (part `iron/...`, item `glyph_iron/...`). Forge parity is not claimed.
- **Dev-runtime flake.** 4 of about 28 dev-client launches on 20 October failed during mod loading with Patchouli's `ServiceConfigurationError: ... NeoForgeXplatImpl not a subtype`, before the game started. The runner retried those launches. The other client crash reports from that day are the QA harness's own failed checks while it was being built (for example "no QA pig in view" and the dirt timing check). None is an ANS or game crash.

## Glyph Press GUI and model refinement (2026-10-20)

This pass preserves the earlier uncommitted 3.3.7/3.4.0 work. Registry names, glyph identities,
network formats, recipes, machine behaviour and version declarations are unchanged. No commit,
push, publication or installation into a player instance was performed.

### UI changes

- A shared arrow silhouette supplies both the grey background and the purple fill. Each scanline
  is bounded by the arrow outline and the progress column; progress reveals the shaft and then
  the right-pointing head. No rectangular overlay or scissor state is involved.
- Both loaders use a 232×232 layout with matching slot positions, centred player inventory,
  a dashed result preview, a full-width Source gauge and a single visible action (Inscribe/Cancel).
- Status remains visible above the details. Cost, tier/knowledge and result appear first in the
  wrapped details panel. Help uses that same clipped panel, supports wheel/scrollbar/keyboard
  navigation, and resets to its beginning when toggled. The panel fits three complete text rows.
- Empty slots have translated hints. Spell/school and status text ellipsize with full hover text;
  title/inventory labels are bounded. Disabled actions explain their refusal. Icon-presence caches
  now belong to the screen and clear on initialization instead of persisting across every screen.
- NeoForge's former help overlay is now inline, leaving inventory and controls unobscured.
  Its JEI category already uses the native animated recipe arrow and a bounded text widget;
  it was assessed and retained, then opened successfully by the real-client harness.

### Files changed in this pass

Paths are relative to this checkout. The saved baseline hashes and `changes.patch` in the local
log directory distinguish this pass from the extensive work that was already present.

- `docs/3.4.0/testing.md`
- `src/main/java/com/otectus/arsnspells/client/GlyphQaClient.java`
- `src/main/java/com/otectus/arsnspells/client/screen/ArrowProgress.java`
- `src/main/java/com/otectus/arsnspells/client/screen/GlyphPressScreen.java`
- `src/main/java/com/otectus/arsnspells/client/screen/ScrollableTextPanel.java`
- `src/main/java/com/otectus/arsnspells/client/screen/VanillaGui.java`
- `src/main/java/com/otectus/arsnspells/menu/GlyphPressMenu.java`
- `src/main/resources/assets/ars_n_spells/lang/en_us.json`
- `src/main/resources/assets/ars_n_spells/models/block/glyph_press.json`
- `src/main/resources/assets/ars_n_spells/models/block/glyph_press_working.json`
- `src/test/java/com/otectus/arsnspells/client/screen/ArrowProgressTest.java`
- `tools/generate_glyph_press.py`

### Verification

Gradle commands below use `/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh <this checkout>`
with `GRADLEW_QUIET_LOG_DIR=logs/3.4.0/gui-refinement`. Full logs and local screenshots are in
that directory; it is git-ignored. `verification.json` records artifact sizes/hashes and JUnit counts.

| Check | Command / evidence | Result |
| --- | --- | --- |
| Build and JUnit | `build --offline`; `gradle-ars-n-spells_1.21.1-build-20261020-214951.log` | PASS: 980 tests, 0 failures/errors/skips; includes 3 arrow regression tests. |
| GameTests | `runGameTestServer --offline -PwithIronsRuntimeGameTests -PgametestRunDir=run-340-gui-tests`; `gradle-ars-n-spells_1.21.1-runGameTestServer-20261020-213637.log` | All 407 required tests passed; 315 optional scenarios executed, 26 addon scenarios skipped. |
| GameTest evidence guard | `python3 tools/verify_gametest_log.py run-340-gui-tests/logs/latest.log --required 407 --executed 315 --skipped 26 --present irons_spellbooks --absent ars_elemental --absent ars_affinity --absent ars_zero --absent ars_elemancy` | PASS, actual profile and completion verified. |
| Dedicated QA server | `runServer --offline -PwithGlyphQaServer -PglyphQaPhase=1 -PwithIronsRuntimeGameTests -PgametestRunDir=run-340-gui-qa-server-confirm`; `gradle-ars-n-spells_1.21.1-runServer-20261020-215009.log` | PASS, full phase-1 journey and saved restart job; exit 0. |
| Live client | `runClient --offline -PwithGlyphQaClient -PglyphQaClientPhase=1 -PglyphQaAddress=127.0.0.1:25577 -PwithIronsRuntimeGameTests -PgametestRunDir=run-340-gui-qa-client`; `gradle-ars-n-spells_1.21.1-runClient-20261020-215151.log` | PASS: Press conversion, tier III, refusal, help Home/End, keyboard focus, JEI, learning/casting; effective scales 1 and 2; exit 0. |
| Working-model render passes | QA `model_layers` verdict | 122 unculled body quads in the solid pass, one rune quad in the translucent pass (the base underside is the separately culled 123rd body face). |
| Python tool suite | `python3 -m unittest discover -s tools -p 'test_*.py'` | 27 tests, OK. |
| Rendered arrow | `arrow-pixels.txt`, `screenshots/working.png` | All 330 logical pixels match the arrow silhouette at 18/22 filled columns, including the partly filled head; no purple pixels outside the shape. |
| Asset audit | `asset-audit.json`, `texture-uv-audit.json` | Valid dimensions/animation metadata, UVs in range, nonzero rendered face area, literal Press translation keys present. |
| Generator reproducibility | Model-only generation into a temporary directory | Shipped JSON matches exactly (Forge 5 files / NeoForge 4); PNGs were not regenerated. |

The real viewports were **941×1122 at scale 1** and **471×561 at scale 2**. The window manager
constrained the physical window; Minecraft clamped requests for higher scales. Screenshot names
containing `gui4` are historical requested-scale labels, not proof of effective scale 4. New capture
logs record the effective scale explicitly. The 232×232 menu and all slots fit 320×240 by coordinate
inspection; that minimum viewport was not exercised in a live client.

The first disposable-client attempt failed before reaching the Press: a fresh player's starter
notebook satisfied the harness's "slot 0 is nonempty" check, and its use opened Patchouli. The harness
now waits for the actual exported QA scroll and seeded Press/materials. Both subsequent full journeys
passed. This was a QA readiness bug; casting code was not changed. The first failure is retained in
`gradle-ars-n-spells_1.21.1-runClient-20261020-213834.log`. The build has three existing NeoForge API
deprecation warnings in `ArsNSpellsClient.java`, which this pass does not modify.

### Model and texture assessment

- **Geometry:** 24 body cuboids retained; idle drops from **144 to 123 faces**, working from
  **146 to 124**. Completely buried faces and the rune's unnecessary underside were removed; the
  base underside receives `cullface: down`. The rune's zero-thickness plane is intentional and its
  remaining up face has positive area. Rotated crystals and partially visible faces are retained.
- **Render passes:** idle uses the solid pass. The working model uses NeoForge's composite loader,
  reusing the idle body in the solid pass and drawing only the animated rune translucently. This
  avoids sorting/blending the entire opaque machine while preserving the rune's soft alpha halo.
- **Materials/UVs:** 16×16 frames, 5–11 colours, an eight-frame 16×128 animated rune. Several small
  parts sample small UV regions (crystal 10/256, scroll 48/256); repacking them would change appearance
  and create tiny sprites for negligible savings. PNGs, UVs, palettes and animation timing remain.
- **Mipmapping:** both live clients created the block atlas at mip level **4** with no mip-level
  downgrade warning. No small 8×1-style atlas sprite was introduced. Retaining 16×16 frames is
  intentional, including for transparent or sparsely sampled materials.
- **Deliberately unchanged:** the two existing model silhouettes, proportions, art direction,
  texture formats and animation style. No degenerate rendered faces were found after the cleanup.
  These are rendering-cost/geometry improvements, not a measured FPS claim.

### Remaining human review / limits

- Screenshots were inspected and arrow pixels checked mechanically. No human playtest or artistic
  approval is claimed. Compare local `screenshots/ready.png`, `working.png`, `help-end.png` and
  `scale1.png` for the final presentation.
- Minimum/narrow windows, effective GUI scales 3+, long translations, custom fonts, resource-pack
  overrides, and heard narration remain unverified. Only `en_us` translations ship in these trees.
- This pass did not repeat the optional-addon/absent-Iron's matrix, two-player or restart phases,
  and did not boot the packaged JAR in a separate production installation. Earlier evidence above
  remains separate from this UI/model pass.
- The Forge-only `check_mod.py` helper rejects NeoForge APIs by design; NeoForge used its asset-completeness JUnit checks, the resource audit and actual model/atlas loading instead.

Built artifact: `build/libs/ars_n_spells-3.4.0.jar`, 3,201,688 bytes, SHA-256
`bcb379623c06696d75161ebac445beb64ee254088cdc3c52f49f048c26ce4191`.
