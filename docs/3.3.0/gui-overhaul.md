# 3.3.0 GUI overhaul

The mod-owned GUI uses Minecraft's light container palette: `#C6C6C6` panels,
white highlights, dark bevels, `#8B8B8B` recessed slots and `#404040` labels without
text shadows. Native buttons and edit boxes provide the Minecraft version's
normal, hovered, focused and disabled rendering, click sounds and narration.
Spell artwork keeps its semantic colors. The HUD uses white shadowed text over
translucent black, as appropriate for text displayed over the world.

Covered surfaces: Spell Loom, Loom Details/presets, icon library/picker, school
journal, compatibility, configuration, transaction HUD and the developer icon
review screen. Minecraft and other mods retain ownership of their own screens.

## Layout and interaction

- The Loom is 176x224 GUI pixels, fitting inside Minecraft's minimum 320x240
  scaled viewport. Slot positions remain owned by each loader's menu; the screen
  draws the same 18px recessed slots beneath the item hitboxes. The output and
  cosmetic preview have separate spacing. The hotbar retains vanilla spacing.
- Concise translated Loom status hints occupy two reserved lines. Full status,
  consumption and capacity remain available in a wrapped tooltip and Details.
- Details has a wrapped, clipped scroll area. Wheel, scrollbar drag, arrow keys,
  Page Up/Down and Home/End provide access to long school names and mapping
  digests. Inspection continues to reflect synchronized inventory changes.
- Preset actions retain their behavior. Apply is disabled for an empty slot;
  long preset summaries have ellipses and full tooltips.
- Icon cells use native button rendering. The selected cell is inset with a
  white outline and a corner marker; narration announces selection. Search
  preserves its caret when results rebuild. Toolbar focus survives rebuilding.
- Settings controls are native buttons, including keyboard activation and
  narration. Server-owned settings stay disabled outside an integrated server.
  Wheel, scrollbar drag and Page Up/Down scroll the rows. Full descriptions are
  available as tooltips; the footer reserves enough width for every control.
- The NeoForge panel base renders the native backdrop before drawing the panel
  and suppresses the second background call from `Screen.render`. The container
  screen retains NeoForge's native background lifecycle. Settings preserve an
  opaque backdrop to avoid the previous third-party blur regression.

## Maintenance and validation

`tools/port_gui.py` synchronizes an explicit allowlist of GUI files into the local
NeoForge worktree and applies the 1.21 API adaptations. It updates menu geometry
in place instead of replacing loader-specific inventory handling.
`tools/port_icon_assets.py` finishes by invoking this GUI adapter.

Build each loader with `gradlew test build --offline` using its cached toolchain.
The opt-in `-PwithIconClientSmoke` client run captures the picker at scales 1–4,
search results, compatibility, icon states, read-only settings (including a
scrolled page), a two-page journal fixture and empty search results.

The `-PwithWorldUiSmoke -PwithIronsRuntimeGameTests` run requires an isolated
copied `ANS Icon QA` save in the selected `-PgametestRunDir`. It captures the real
Loom at GUI scale 4 (320x240 viewport), details before/after keyboard scrolling,
picker search/return, a successful inscription and the native spell wheel.
Assertions check preservation of the typed name and the server-produced output.

## Results — September 6, 2026

- Forge: 841 unit tests passed. NeoForge: 832 unit tests passed. Both loaders
  compile and package as 3.3.0, including the new GUI classes and translations.
- Both final library runs completed all 12 captures. Logged viewports were
  1280x960, 640x480, 427x320 and 320x240 at GUI scales 1, 2, 3 and 4.
  Read-only settings, Page Down, journal pagination and empty search results
  completed without errors after fixing the title-screen config read.
- Both initial world runs completed inscription, preservation of the typed
  name, details scrolling, picker return and native wheel checks. Later captures
  verified the Loom at 320x240 on both loaders. Later repeat world runs were not
  counted as clean passes: one encountered a changed screen during scripted
  navigation, and another stopped on Patchouli's startup service-loader error
  (`NeoForgeXplatImpl not a subtype`). Neither failure was hidden by Gradle's
  process exit status. The completed library runs and earlier world passes are
  the runtime evidence for this GUI change.
- Build logs are in each checkout's `build/gui-library-verified.log` and
  `build/gui-package.log`. Completed world-run evidence is in
  `build/gui-world-client.log`; subsequent viewport captures are recorded
  separately in `build/gui-world-verified.log`.

Original client screenshots (no mockups):

| Surface | Forge | NeoForge |
| --- | --- | --- |
| Spell Loom | [Preview](gui-previews/forge/loom.png) | [Preview](gui-previews/neoforge/loom.png) |
| Scrolled details | [Preview](gui-previews/forge/details.png) | [Preview](gui-previews/neoforge/details.png) |
| Icon library | [Preview](gui-previews/forge/icons.png) | [Preview](gui-previews/neoforge/icons.png) |
| Settings | [Preview](gui-previews/forge/settings.png) | [Preview](gui-previews/neoforge/settings.png) |
| School journal | [Preview](gui-previews/forge/journal.png) | [Preview](gui-previews/neoforge/journal.png) |
