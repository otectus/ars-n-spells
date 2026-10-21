# 3.4.0 testing record (Forge 1.20.1)

Everything below was executed on 2026-10-20 against this checkout. Nothing here is carried over
from an earlier release; 3.3.x evidence stays in its own folders.

## Checkout and toolchain

- Branch `main`, HEAD `2d4cb64` (3.3.6) plus the uncommitted 3.3.7 work and all 3.4.0 changes
  (not committed: no commit or push was requested).
- `gradle.properties` `mod_version=3.4.0`; network protocol `8`.
- Java 17 (OpenJDK, `/usr/lib/jvm/java-17-openjdk`), Gradle wrapper with ForgeGradle 6, official
  mappings, all runs `--offline` through `/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh`.

| Dependency | Version (source) |
| --- | --- |
| Minecraft / Forge | 1.20.1 / 47.4.10 |
| Ars Nouveau | 4.12.7 (CurseForge file 6688854) |
| Iron's Spells 'n Spellbooks | 1.20.1-3.15.0 (file 7402504, compile and default runtime); 1.20.1-3.16.3 (file 8680180) with irons_lib 1.20.1-2.1.0 and GeckoLib 4.8.3 |
| Curios | 5.14.1+1.20.1 |
| JEI | 15.49.0.187 (compile-only API) |
| Ars Elemental | 0.6.8.0 (file 8399870) |
| Ars Zero | 2.0.2 (local `libs/` jar) |
| Too Many Glyphs | 2.3.2 (file 4813803) |

## Results

Logs are kept locally (git-ignored) under `logs/3.4.0/`; run directories are `run-340-*`. The
GameTest runs below used the final code.

| # | Command (`gradlew-quiet.sh <repo> …`) | Result |
| --- | --- | --- |
| 1 | `build` | PASS. Ran `test` (966 JUnit tests, 0 failures, 0 skipped) and produced `build/libs/ars_n_spells-3.4.0.jar` (3,146,339 bytes, sha256 `d5b1d7bf70bae7df8fdf42f0c6d3da95376669b1724ed0b33863c9b6a9801d9b`) and `ars_n_spells-3.4.0-slim.jar` (2,512,332 bytes, `dff0c7f8dbdd8c51a3c852a6d755f9d17b9ca6b14e1e3eab83b5c2c8c9733f2f`). |
| 2 | `runGameTestServer --offline -PgametestRunDir=run-340-final-noirons` | **All 267 required tests passed.** Profile lines: every optional mod absent. 209 gated scenarios skipped for absent mods (Iron's 187, Ars Elemental 10, Ars Zero 9, Too Many Glyphs 2, Covenant 1); the ungated rest executed, among them glyph registration (777 dormant glyphs), spell round trip, dormancy, the Press store/reload test and the creative-tab tests. Log `gametest-final-noirons.log`. |
| 3 | `runGameTestServer --offline -PwithIronsRuntimeGameTests -PgametestRunDir=run-340-final-3150` | **All 267 required tests passed** (profile: Iron's `1.20.1-3.15.0`; 188 gated scenarios executed). Log `gametest-final-3150.log`. |
| 4 | `runGameTestServer --offline -PwithIrons316RuntimeGameTests -PgametestRunDir=run-340-final-3316` | **All 267 required tests passed** (profile: Iron's `1.20.1-3.16.3`; 188 executed). Log `gametest-final-3316.log`. |
| 5 | `runGameTestServer --offline -PwithIronsRuntimeGameTests -PwithArsElemental -PwithArsZero -PwithTooManyGlyphs -PgametestRunDir=run-340-final-addons` | **All 267 required tests passed** (profile: Iron's 3.15.0, Ars Elemental 0.6.8.0, Ars Zero 2.0.2, Too Many Glyphs 2.3.2 present; 209 executed, only the Covenant scenario skipped). Log `gametest-final-addons.log`. |
| 6 | `runClient --offline -PwithWorldUiSmoke -PwithIrons316RuntimeGameTests -PgametestRunDir=run-340-ui` (copied disposable save `ANS Icon QA`) | `ANS_WORLD_UI_REVIEW_COMPLETE stages=19 … glyph_press=true glyph_items=true`; 19 captures; block atlas created at mip level 4 with no mipmap warning. Log `client-world-ui-smoke.log`; Press captures in `docs/3.4.0/press-review/`. Run before the last client change (the Press screen's fallback rune path, used only when a spell icon is missing), so that path is not covered by it. |
| 7 | `python3 .mcmod-tools/check_mod.py ars-n-spells` | 0 errors, 0 warnings (5 pre-existing notes about unused 3.x tablet models). |

The debug logs of runs 3 and 4 record exactly three imported-glyph refusals, all intended: the
shared Iron's cooldown (Heal II after Heal), the shared-book player who has not learned the spell,
and the charged glyph refused while a native cast holds the slot.

### Touch evidence (before and after the repair)

From the Touch work earlier the same day, Iron's 3.16.3 profile: the pre-repair baseline
(`run-340-touch-before-3316`, `logs/3.4.0/touch-baseline-before-repair-3316.log`) failed 12
required tests and logged `effect_applied=true reported_resolved=false`; after the repair
(`run-340-touch-after-3316`) all 222 required tests passed with
`effect_applied=true reported_resolved=true`. The Touch suite is part of every run above. See
`touch-regression.md`.

### Measurements recorded by the runs

- `[ANS-PressPerf]`: 100 idle Presses for 100 ticks made **0** Source lookups in every Iron's
  run. Their ticker, called directly 20,000 times, averaged 59.8, 51.1 and 160.2 ns per idle Press
  tick in runs 3, 4 and 5 (one machine, not a benchmark). The test is Iron's-gated and does not
  run in run 2.
- `[ANS-GlyphModes]` (Self + imported Heal I, `ars_to_iron` 2.0, `iron_to_ars` 0.5): each pool
  lost exactly its quoted leg, once:

  | Mode | Ars pool | Iron's pool |
  | --- | --- | --- |
  | `iss_primary` | 0 | 58 |
  | `ars_primary` | 29 | 0 |
  | `hybrid` | 0 | 58 |
  | `separate` | 19 | 19 |
  | `disabled` | 10 | 38 |

## What the new tests cover

| Class | Tests | Covers |
| --- | --- | --- |
| `TouchRegressionGameTests` | 22 | Touch at blocks, entities and water (Sensitive) from native books, generic carriers, exported scrolls and bound books (hotkey, staff, wheel); Self and Projectile aimed at blocks; misses spend and train nothing; paid once; out of reach; stale and forged aim hints; duplicate request ids; vanilla item-use overlap; off-hand and dead senders; stale fingerprints; resolver-hook coverage of both block overloads. |
| `ImportedGlyphGameTests` | 26 | All 777 glyphs registered as Ars parts with items; catalog vs loaded registry (every spell, every level); spell round trip; dormancy without Iron's; learning (once, no level leak, Iron's learning requirement); per family: Self Heal (cooldown shared across levels), Touch Slow (charged), Touch Chain Lightning, Touch and Self Firebolt, Projectile Teleport (destination is the landing point, not the look), Touch Summon Vex (Iron's records the caster as owner), Touch Ray of Siphoning (channel, then the recipe resumes); Projectile Chain Lightning (a target effect delivered by a projectile); Touch Lightning Bolt (a position effect at the block); the augment matrix (only Amplify and Dampen, only within range, each changing Iron's cast level); a shared book refused for a player who has not learned the spell in Iron's, at no cost and granting nothing; caster-only refused with Touch; charged-once; training once; knowledge bonus; Fireball holding the recipe; family minimum/maximum levels and invalid boundaries; native cast slot never cancelled; nested in an exported scroll (one Iron's cast event). |
| `ImportedGlyphPaymentGameTests` | 3 | All five mana modes with unequal rates; atomic shortage; refund when a charge is cancelled. |
| `GlyphPressGameTests` | 14 | Store/reload in every profile; the notebook example (Firebolt I, exact consumption); level preserved at tier II and a catalyst shortfall; every refusal kind with nothing moved; Iron's learning and upstream disable; two viewers, replayed, stale and duplicate requests; menu locks (pickup, shift-click, swap, throw); hopper staging, locks on every face (simulated and executed) and output-only extraction; persistence mid-job, cancelled restored job, no replay; break and explosion drop inputs once; Source drawn only while working; 100 idle Presses; behaviour reload cancelling only affected jobs and an allowlist not overriding Iron's disable. |
| `GlyphCatalogInventoryGameTests` | 1 | Writes the loaded-registry inventory the catalog is generated from. |
| `CreativeTabGameTests` | +1 | Imported glyphs are in Ars's glyph tab only with Iron's. |
| Unit (`glyph/*Test`; `PacketHandlerDirectionGuardsTest` updated) | 25 new | Glyph id encoding and collisions; catalog parsing; behaviour layer (defaults, overrides, deny precedence, pack ranks, unknown spells and levels, bounds, digest, adapter validation, client-view round trip through the sync packet, oversized view refused); knowledge-bonus policies; packet direction guards. |

## Intermittent tests

These pre-existing tests failed in some full-suite runs during this work and passed in the next
run of the same profile, with no change to the code they test:

- `bindingRitual_strayItemInRangeAbortsWithoutMutating` (failed in two runs, one on each Iron's
  build, and once earlier in development) and `bindingRitual_bindsDroppedScrollOntoDroppedBook`
  (once, Iron's 3.15.0).
  Their items are spawned inside the floor layer beside the brazier, so Minecraft's push-out
  decides whether an item ends up within the ritual's range. The rituals are unchanged in 3.4.0.
- `ironsLoaded_hotfixRecastPaymentAndCooldown` (`ans_mana_config`) and
  `ironsLoaded_schoolChoice_disabledServerRefusesAndCastsAutomatic` (`ans_school_disabled`):
  each failed once, then passed. Both write the SERVER config, which Forge reloads
  asynchronously.

## Not covered

- **Manual play.** There was no manual survival journey, no dedicated server with a remote
  client, and no two-person session. The client run is a scripted single-player session: real
  menu, real request packets, real rendering.
- **Client presentation.** GUI scales other than 4, narrow windows, keyboard-only navigation and
  narration, long localized names, and resource-pack overrides were not checked by hand.
- **JEI.** JEI is compile-only in this workspace, so the Glyph Press category compiled but was
  not shown in a running client.
- **Caster routes.** Imported glyphs inside an Ars spell bound into an Iron's book (wheel,
  hotkey, staff), and turret, rune and Reactive casts containing them, follow the coded rules in
  `iron-glyph-coverage.md` but have no imported-glyph GameTest.
- **Knowledge bonus.** The `per_spell` policy is tested for learning and recalculation
  stability, not against equipment changes, relogs or each mana mode.
- **Untested Iron's mechanics.** No imported-glyph GameTest exercises Iron's recast stages; they
  run through Iron's own recast bookkeeping. Interruption of a charge by player input (rather
  than a server-side cancel) was not exercised. Continuous-cast shortage mid-channel is covered
  for native casts by the existing suite, not for an imported channel.
- **Performance.** Native spell-editor responsiveness with 777 extra glyphs and catalog startup
  time were not measured.
- **Excluded profiles.** Ars Affinity does not exist for Forge 1.20.1, so that profile is not
  applicable. The Covenant runtime profile was not run.

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
- Forge's existing help was already scrollable and clipped; that behaviour was retained.
  Parameterized server refusal messages now receive their catalyst/capacity arguments.
  JEI uses a horizontal input/arrow/output row, bounded description lines and full hover text.
  Unknown learning state is kept distinct from a known "not learned" state.

### Files changed in this pass

Paths are relative to this checkout. The saved baseline hashes and `changes.patch` in the local
log directory distinguish this pass from the extensive work that was already present.

- `docs/3.4.0/testing.md`
- `src/main/java/com/otectus/arsnspells/client/WorldUiSmoke.java`
- `src/main/java/com/otectus/arsnspells/client/jei/GlyphPressCategory.java`
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
| Build and JUnit | `build --offline`; `gradle-ars-n-spells-build-20261020-215152.log` | PASS: 969 tests, 0 failures/errors/skips; includes 3 arrow regression tests. |
| GameTests | `runGameTestServer --offline -PwithIrons316RuntimeGameTests -PgametestRunDir=run-340-gui-tests`; `gradle-ars-n-spells-runGameTestServer-20261020-214227.log` | All 267 required tests passed; Iron's 1.20.1-3.16.3 loaded. |
| Live client | `runClient --offline -PwithWorldUiSmoke -PwithIrons316RuntimeGameTests -PgametestRunDir=run-340-gui-refinement`; `gradle-ars-n-spells-runClient-20261020-215008.log` | PASS, 24 captures: real menu and request, engraving near 85%, completion, working world model, help Home/End, effective GUI scales 1 and 2. |
| Content checker | `python3 /home/otectus/Projects/.mcmod-tools/check_mod.py /home/otectus/Projects/ars-n-spells` | 0 errors, 0 warnings; 5 pre-existing unused-tablet-model notes. |
| Python tool suite | `python3 -m unittest discover -s tools -p 'test_*.py'` | 33 tests, OK. |
| Rendered arrow | `arrow-pixels.txt`, `screenshots/working.png` | All 330 logical pixels match the arrow silhouette at 18/22 filled columns, including the partly filled head; no purple pixels outside the shape. |
| Asset audit | `asset-audit.json`, `texture-uv-audit.json` | Valid dimensions/animation metadata, UVs in range, nonzero rendered face area, literal Press translation keys present. |
| Generator reproducibility | Model-only generation into a temporary directory | Shipped JSON matches exactly (Forge 5 files / NeoForge 4); PNGs were not regenerated. |

The real viewports were **941×1122 at scale 1** and **471×561 at scale 2**. The window manager
constrained the physical window; Minecraft clamped requests for higher scales. Screenshot names
containing `gui4` are historical requested-scale labels, not proof of effective scale 4. New capture
logs record the effective scale explicitly. The 232×232 menu and all slots fit 320×240 by coordinate
inspection; that minimum viewport was not exercised in a live client.

The final build also preserves the unavailable learning-state label, a small condition added after
the client captures; the captured ready/working paths are unchanged. GameTests preceded the final
presentation-only adjustments; no machine, packet or slot behaviour changed afterwards.

### Model and texture assessment

- **Geometry:** 20 cuboids retained; idle and working models drop from **120 to 105 faces**.
  Only completely buried faces were removed. The plinth underside receives `cullface: down`.
  Inset sides, partially obscured surfaces and the rotated crystal stay intact; culling those against
  neighbouring blocks could erase visible geometry. Collision/selection shapes are unchanged.
- **Materials:** the Press PNGs use 6–10 colours and 16×16 frames; the working plate is an eight-frame
  16×128 strip. Reused Loom brass/crystal/weave are 16×16 RGB materials with denser existing noise.
  Their palette was deliberately retained to keep the two workstations consistent. No PNG changed.
- **UV use:** the plate samples 100/256 texels; the crystal/weave sample only small regions of shared
  Loom textures. Separate smaller sprites would add assets, alter the authored appearance and risk
  reducing atlas mip levels for negligible savings. The existing UVs and material sharing remain.
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
- Forge JEI compiled, but this Forge development profile has no JEI runtime; its category needs a visual check in a JEI-enabled client. NeoForge JEI was exercised in its client journey.

Built artifact: `build/libs/ars_n_spells-3.4.0.jar`, 3,149,293 bytes, SHA-256
`dad75ce4cee2a7dcf059949a9a8b49cdff00909f7c249f09de327ab8381b0a32`.
