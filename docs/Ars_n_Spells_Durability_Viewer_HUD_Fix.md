# Ars 'n Spells: Fix the Durability Viewer HUD Compatibility Regression

## Objective

Act as a senior Minecraft Forge 1.20.1 engineer. Investigate and fix the reported compatibility regression in `otectus/ars-n-spells`: adding Ars 'n Spells 3.3.2 causes Durability Viewer's HUD to disappear from a profile where it previously worked with Classic Bar.

Repository: `https://github.com/otectus/ars-n-spells`

This is an implementation task. Establish the cause, make the smallest reliable correction, add regression coverage, and document the results. Do not stop at a speculative explanation or a successful build. Do not perform an unrelated HUD rewrite, upgrade dependencies as a substitute for fixing the reported versions, or change the release version without authorization.

## 1. Preserve the reported environment

| Component | Reported version or context |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.10 |
| Launcher | CurseForge Launcher, isolated profile instance |
| Ars 'n Spells | `ars_n_spells-3.3.2.jar` |
| Durability Viewer | `DurabilityViewer-1.20.1-1.0.1.jar` |
| Ars Nouveau | `ars_nouveau-1.20.1-4.12.7-all.jar` |
| Iron's Spells 'n Spellbooks | `irons_spellbooks-1.20.1-3.16.3.jar` |
| Classic Bar | 1.20.1-6 |
| Jade | Present; exact version was not supplied |

The reporter says Durability Viewer rendered correctly alongside Classic Bar and disappeared after ANS was added. Treat this as the symptom to investigate, not proof that Classic Bar, Jade, a particular event handler, or a particular graphics-state change caused it.

Use the exact versions above and their required dependencies. Record any substituted or unavailable component. Do not describe a recreated minimal profile as the reporter's complete custom mod list. Retrieve the actual Jade version from available profile metadata or logs; otherwise record the tested version and this limitation.

## 2. Evidence-based starting points

These observations come from source inspection, not a reproduced gameplay session. Reconfirm them against the actual checkout and released jars before making changes.

### ANS source baseline is not yet matched to the reported jar

The inspected `main` snapshot was commit `2578bad5d820fc2de9f38c26c92833a0d977c441`. Its `gradle.properties` declares Minecraft 1.20.1, Forge 47.4.10, and `mod_version=3.3.0`; `build.gradle` uses Java 17. This does not establish that the snapshot corresponds to the reported 3.3.2 binary. Resolve that discrepancy rather than assuming the filename, branch, and build metadata describe identical code. [S1, S2]

### The inspected mana-overlay cancellation is already namespace-scoped

`src/main/java/com/otectus/arsnspells/client/ManaBarController.java` subscribes to `RenderGuiOverlayEvent.Pre` at `HIGHEST` priority. Its matchers require the `ars_nouveau` or `irons_spellbooks` namespace and a path containing `mana`. It also has ring-related hiding logic that runs independently of the mana-unification master toggle. This file does not, by itself, demonstrate cancellation of every HUD element. Replacing substring matching with exact IDs may be useful hardening, but is not proof of a fix for this report. [S3]

### Identify the correct Durability Viewer implementation

The exact filename is listed under cnlimiter's **Durability Viewer[Forge/NeoForge]**, CurseForge project `1225695`, file `6332731`. Its linked source is `Nova-Committee/DurabilityViewer`, with a `forge/1.20.1` branch. Do not substitute another Durability Viewer fork or a Fabric build. [S4, S5]

At inspected source commit `a42bce8fbc6fcbae4544b9e90fe8bf3234b9e3f0`, `DurabilityViewerForgeClient.java` renders its main display from `RenderGuiOverlayEvent.Post` for `VanillaGuiOverlay.HOTBAR`, and performs status-effect-related handling on the `POTION_ICONS` Post event. It checks `GuiItemDurability.visible` and provides a show/hide key mapping defaulting to H. Confirm these details against the actual 1.0.1 jar. [S6]

### Missing Post callbacks are a distinct failure mode

Forge's 1.20.1 `RenderGuiOverlayEvent` contract states that canceling an overlay's Pre event suppresses both that overlay and its corresponding Post event. Consequently, trace the vanilla hotbar and potion-icon events; do not assume Durability Viewer has a separately registered overlay ID. This mechanism is relevant to investigate, not an established diagnosis. [S7]

### Existing diagnostics need care

ANS `client/OverlayDiagnostics.java` logs unique overlay IDs through a plain Pre-event subscriber and does not opt into receiving canceled events. Inspect its registration and event ordering before trusting its output as a complete account of what was attempted, canceled, or drawn. [S8]

## 3. Establish a safe, matching baseline

Read repository instructions, including any applicable `AGENTS.md`, before editing. Inspect the current branch, commit, working-tree changes, build configuration, mixin configuration, changelog, and existing tests. Preserve all existing local changes and newer fixes, including unrelated scroll or Spell Loom work.

Useful initial commands:

```sh
git status --short
git branch --show-current
git rev-parse HEAD
git log -n 12 --oneline
```

Locate the 3.3.2 release source or inspect the published jar's metadata and relevant classes. When source-to-binary correspondence is uncertain, compare the relevant bytecode rather than treating the current branch as the released implementation. Keep a released-binary reproduction separate from the current-checkout baseline.

Use a disposable test profile or a backed-up copy, never a production world or the reporter's only profile. Confirm the launched game directory and loaded mod list. Ensure each run contains only one ANS implementation: do not load a released ANS jar alongside ANS classes supplied by a development run.

Record the jar filenames, hashes where practical, actual loaded versions, Java version, client settings, and relevant configuration values. Test copied existing configs and clean configs separately. Do not delete or overwrite the player's settings to make a failure disappear.

## 4. Reproduce and minimize the failure

Define the base profile as Forge 47.4.10, the reported Ars Nouveau and Iron's versions, required dependencies, and the exact Durability Viewer jar.

Run these comparisons with otherwise identical settings:

| Profile | Without ANS | With released ANS 3.3.2 | With patched ANS |
| --- | --- | --- | --- |
| Base profile | Record actual result | Record actual result | Must remain functional |
| Base + Classic Bar 1.20.1-6 | Record actual result | Record actual result | Must remain functional |
| Base + Jade | Record actual result | Record actual result | Must remain functional |
| Base + Classic Bar + Jade | Record actual result | Record actual result | Must remain functional |

Also record the unmodified current-checkout result when its code differs from 3.3.2. A newer checkout that already works still requires identification of the relevant change and regression coverage; do not reintroduce or rewrite a working implementation unnecessarily.

Equip damaged vanilla armor, hold a damaged tool, and include an offhand item so the display has observable content. Confirm that the HUD is enabled and Durability Viewer's visibility toggle is on. Capture comparable screenshots or video, logs, equipment, display settings, and ANS configuration for each meaningful comparison.

The published Classic Bar 1.20.1-6 file is CurseForge project `317642`, file `8250607`. Optional development profiles may use these verified coordinates, but keep them opt-in and out of shipped dependencies: [S4, S9]

```text
curse.maven:durability-viewer-forge-neoforge-1225695:6332731
curse.maven:classic-bars-317642:8250607
```

The inspected ANS build keeps Iron's compile-only by default and adds its runtime jar plus PlayerAnimator under `-PwithIronsRuntimeGameTests`. Verify the current build still behaves this way and that the resolved runtime jar is the reported 3.16.3. A development run without Iron's does not reproduce this report. [S2]

## 5. Trace the actual failure before selecting a fix

Start with these ANS locations, following their current equivalents when code has moved:

```text
src/main/java/com/otectus/arsnspells/client/ManaBarController.java
src/main/java/com/otectus/arsnspells/client/OverlayDiagnostics.java
src/main/java/com/otectus/arsnspells/client/ArsNSpellsClient.java
src/main/java/com/otectus/arsnspells/bridge/BridgeManager.java
src/main/java/com/otectus/arsnspells/config/AnsConfig.java
src/main/java/com/otectus/arsnspells/mixin/
src/main/resources/ars_n_spells.mixins.json
src/main/resources/ars_n_spells.compat.mixins.json
```

Search the actual 3.3.2 implementation and current code for overlay registration, `RenderGuiEvent`, `RenderGuiOverlayEvent`, `VanillaGuiOverlay`, `setCanceled`, cancellable GUI mixins, key mappings, `GuiGraphics`, `RenderSystem`, pose operations, scissor operations, buffer handling, and render-order changes. Inspect relevant shared helpers and generated/shipped mixin configuration, not just files with HUD in their names.

Use narrowly scoped, opt-in diagnostics or debugger breakpoints to answer the following:

**Are Durability Viewer's required events emitted?** Trace HOTBAR and POTION_ICONS Pre/Post callbacks, their order, and cancellation state. Observe canceled Pre events with `receiveCanceled = true` where needed. An observer at LOWEST priority is useful but does not identify the canceling listener or necessarily run after every same-priority listener. Instrument the actual ANS cancellation sites or inspect event dispatch when attributing responsibility.

**Does Durability Viewer's callback execute?** Record entry, its visibility predicate, whether its vanilla-overlay identity comparison succeeds, and the reason for any early return. A missing custom Durability Viewer overlay in an ID list is not evidence that registration failed, given the inspected implementation's vanilla Post hooks.

**Does its renderer execute but produce no visible output?** Compare the graphics state and transformation at the actual draw point in working and failing runs. Inspect pose translation/scale/Z, shader color and alpha, blending, depth state, scissor clipping, viewport, buffer flush boundaries, screen coordinates, and later overdraw. First determine whether ANS or an upstream renderer that ANS changes can affect that state; do not invent an ANS drawing routine absent from the code.

**Was the HUD toggled off or configured off-screen?** Check H-key conflicts, configuration loading, GUI scale, offsets, and visibility transitions. Distinguish an accidental toggle or profile-setting change from a rendering defect. Do not remove Durability Viewer's user-controlled toggle or rewrite its configuration as a compatibility patch.

**Does a particular ANS policy activate the defect?** Compare unification disabled, each supported primary mode, hybrid with each supported selected bar, and relevant optional ring states when those integrations are available. Read the actual enum and defaults rather than inventing configuration keys.

Use one-variable-at-a-time diagnostic changes in the disposable environment. Disabling the entire mod or all mana behavior can help localize the problem, but cannot be the final fix.

Before implementing the correction, write a brief causal explanation: the responsible code path, the triggering conditions, the observable event/state change, why that hides the durability display, and the evidence that distinguishes this explanation from alternatives.

## 6. Implement the smallest reliable correction

### Preserve unrelated overlays and event behavior

ANS mana visibility policy must not cancel the whole GUI or unrelated vanilla overlays. It must not interfere with Durability Viewer, Classic Bar, Jade, the hotbar, potion effects, armor, food, air, boss bars, or other unrelated HUD content.

If cancellation is responsible, correct the responsible predicate or interception point. Match verified mana overlay IDs where exact matching is appropriate, using the actual dependency versions' registration code or runtime IDs. Do not guess an ID from a Java class name. Keep unknown or unrelated overlays unchanged, and never use `setCanceled(false)` to override another mod's decision.

Preserve the existing primary/hybrid mana-bar selection behavior and optional ring rules. In particular, do not accidentally move ring-related logic behind a master-toggle return when its existing intended behavior is independent of that toggle.

If an upstream overlay combines mana with other content, avoid suppressing the entire combined renderer merely to hide its mana component. Choose a narrower, verified hook and explain why an ordinary overlay event cannot solve that specific case.

### Repair graphics-state ownership only when evidence supports it

If a state leak is responsible, fix it where the state is changed. Balance owned pose and scissor operations, including early-return paths. Use scoped cleanup where appropriate and respect the caller's documented state contract.

Do not assume a pose push/pop restores global render state. Do not add an unconditional end-of-frame reset, clear buffers globally, flush indiscriminately, or force arbitrary render-state defaults before unrelated mods draw. Such changes can hide the immediate symptom while creating new incompatibilities.

### Keep compatibility optional and contained

Prefer fixing ANS's own behavior over patching third-party code. Do not unregister another mod's listeners, manually invoke its renderer a second time, synthesize duplicate Post events, force its visibility flag, or require users to remove Classic Bar or Jade.

Only add a targeted third-party compatibility hook when the trace proves it necessary. Gate it to the relevant mod and verified implementation, isolate client-only classes, and test both its presence and absence. Do not introduce hard runtime dependencies on these HUD mods or bundle their classes.

Diagnostics must be disabled by default, observational, bounded, and inexpensive when off. Avoid per-frame log spam, repeated reflection, and permanent graphics-state polling. Label a generic canceled-event observation separately from an ANS-owned cancellation.

## 7. Add regression coverage and verify in game

### Automated coverage

Extend the existing tests rather than adding a parallel test framework. Cover the corrected policy and the actual failing path as closely as the available harness permits.

For overlay-selection changes, test verified target IDs, unrelated namespaces, vanilla HOTBAR and POTION_ICONS, unknown IDs, misleading mana-like names, all supported unification selections, and optional ring-policy combinations. Assert that unrelated events are left untouched and preexisting cancellations are not reversed. Tests of a helper predicate alone do not establish that the event handler uses it correctly.

Where feasible, add a client test probe that observes the actual vanilla Post callbacks and downstream rendering. Add regression protection against whichever mechanism caused the defect: lost callbacks, incorrect visibility transitions, identity changes, bad placement, or state leakage. Do not claim a source-text grep or a headless server GameTest proves pixels were rendered.

### Client verification

Repeat the profile comparisons with the packaged patched jar, not only a development run. Verify durability icons and numbers with damaged/undamaged items, armor and offhand changes, and the visibility toggle off/on. Check that the display remains readable and updates correctly.

Exercise unification disabled, both primary modes, and both hybrid-bar choices. Confirm that mana display selection, spell selection/casting indicators, mana consumption/regeneration, Classic Bar, Jade, hotbar content, and potion effects remain correct. Test full and depleted mana, swapping between casting and ordinary items, GUI scales 1/2/3/Auto where supported, window resizing, resource reload, inventory open/close, and reconnect or world change.

Test supported optional ring behavior only with its required integrations installed; document an unavailable optional environment rather than claiming it passed. Verify ANS without Durability Viewer and a dedicated-server launch without client-only HUD mods to detect accidental dependencies or client-class loading.

### Build and test commands

Confirm available tasks and current project instructions before running these commands. The inspected build has JUnit tests, client/server runs, and GameTests. [S2]

```sh
./gradlew test build
./gradlew runGameTestServer
./gradlew runGameTestServer -PwithIronsRuntimeGameTests
```

Use the appropriate wrapper on the host platform. Run client verification with an opt-in HUD test profile or a disposable launcher profile containing the required jars. Do not run competing clients or servers against the same world/run directory.

Record commands, exit results, test counts, skips, screenshots, and relevant logs. Report dependency-resolution failures, missing display access, unavailable jars, and unexecuted tests explicitly. A clean build is necessary, but does not establish that this visual regression is fixed.

## 8. Deliverables and completion criteria

Deliver a focused patch, regression tests, and a short document such as `docs/HUD_COMPATIBILITY_FIX.md` containing the exact baseline, root cause with file/method references, the implemented correction, reproduction instructions, and an actual pass/fail/not-run verification matrix.

Include a player-facing changelog entry under the project's appropriate unreleased section. Do not change version numbers, publish a release, or commit downloaded third-party jars unless separately authorized. Explain any proposed optional development-profile addition.

The issue is verified fixed only when the original failure is reproduced or otherwise conclusively explained, the patched jar preserves the durability display under the triggering conditions, and the relevant mana/HUD regression checks pass. When runtime verification is blocked, deliver only evidence-supported changes and clearly label the remaining uncertainty. If investigation establishes that a keybinding or configuration state caused the symptom rather than ANS code, document that evidence instead of manufacturing a rendering patch.

## Source references

These are inspection references, not guarantees that a moving branch matches a released jar. Revalidate source-to-binary correspondence during implementation.

- **S1 — ANS properties, inspected commit:** `https://github.com/otectus/ars-n-spells/blob/2578bad5d820fc2de9f38c26c92833a0d977c441/gradle.properties`
- **S2 — ANS build and test/runtime configuration:** `https://github.com/otectus/ars-n-spells/blob/2578bad5d820fc2de9f38c26c92833a0d977c441/build.gradle`
- **S3 — ANS mana overlay handling:** `https://github.com/otectus/ars-n-spells/blob/2578bad5d820fc2de9f38c26c92833a0d977c441/src/main/java/com/otectus/arsnspells/client/ManaBarController.java`
- **S4 — Exact Durability Viewer release:** `https://www.curseforge.com/minecraft/mc-mods/durability-viewer-forge-neoforge/files/6332731`
- **S5 — Durability Viewer project and linked source:** `https://www.curseforge.com/minecraft/mc-mods/durability-viewer-forge-neoforge` and `https://github.com/Nova-Committee/DurabilityViewer`
- **S6 — Durability Viewer Forge callbacks and key mapping:** `https://github.com/Nova-Committee/DurabilityViewer/blob/a42bce8fbc6fcbae4544b9e90fe8bf3234b9e3f0/src/main/java/committee/nova/mods/durabilityviewer/client/DurabilityViewerForgeClient.java`
- **S7 — Forge 1.20.1 overlay-event contract:** `https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/src/main/java/net/minecraftforge/client/event/RenderGuiOverlayEvent.java`
- **S8 — ANS overlay diagnostics:** `https://github.com/otectus/ars-n-spells/blob/2578bad5d820fc2de9f38c26c92833a0d977c441/src/main/java/com/otectus/arsnspells/client/OverlayDiagnostics.java`
- **S9 — Exact Classic Bar release:** `https://www.curseforge.com/minecraft/mc-mods/classic-bars/files/8250607`
