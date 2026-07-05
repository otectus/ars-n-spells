# Testing Guide — Ars 'n' Spells 3.0.1

This guide covers manual verification scenarios for the cross-cast pipeline
(introduced in 2.0.0), the native-wheel/Spell Loom export systems (3.0.0),
and the 3.0.x hardening fixes. It supersedes the pre-1.8 testing notes.

For an at-a-glance description of features and configuration, start with the
[README](README.md). For the change history, see [CHANGELOG.md](CHANGELOG.md).

## Prerequisites

| Mod | Version | Required for tests below |
| --- | --- | --- |
| Minecraft (Forge) | 1.20.1 / 47.4.0+ | All tests |
| Ars Nouveau | 4.12.7 | All tests |
| Iron's Spells 'n Spellbooks | 3.15.x | "Ars + Iron's" tests, scaling, progression, scrolls, cross-cast |
| Covenant of the Seven (Sanctified Legacy) | Any | Cursed/Virtue Ring tests |
| Blood Magic | Any | LP source `BLOOD_MAGIC_*` tests |

Drop the built jar (`build/libs/ars_n_spells-<version>.jar`) into the
instance's `mods/` folder alongside the dependencies. **The network protocol
version is strict; clients and servers must run matching mod versions.**

Apotheosis + Apothic Curios are optional — install them only for the
"Curio attribute bridge" scenario below.

To enable verbose log output during testing, set in `<world>/serverconfig/ars_n_spells-server.toml`:

```toml
debug_mode = true
```

This adds `[Cooldown]`, `[CurioDiscount]`, `[CrossCastTrace]`, and similar log
prefixes to most event paths.

## 2.0.0 cross-cast pipeline matrix

This matrix mirrors the 2.0.0 audit's "Testing and Validation Strategy"
table. Every cell here must pass before a release candidate is signed off.

To follow a single cast end-to-end, set `debug_mode=true` and grep
`logs/latest.log` for `attempt=<uuid>` — the trace utility emits one line per
pipeline stage with the same UUID:

```
[CrossCastTrace] attempt=<uuid> player=<name> side=C stage=request_sent  …
[CrossCastTrace] attempt=<uuid> player=<name> side=S stage=request_received …
[CrossCastTrace] attempt=<uuid> player=<name> side=S stage=descriptor_validated …
[CrossCastTrace] attempt=<uuid> player=<name> side=S stage=resource_check approved=true
[CrossCastTrace] attempt=<uuid> player=<name> side=S stage=upstream_cast_enter runtime=…
[CrossCastTrace] attempt=<uuid> player=<name> side=S stage=upstream_cast_exit success=true
```

The client and server attempt UUIDs are different (one generated each side);
the server logs both with `clientAttempt=…` in the `request_received` line so
correlation across sides is one grep.

### Matrix to run

| Scenario | Mode | Expected | Where to look |
| --- | --- | --- | --- |
| Inscribed Ars spell, neutral target item | `iss_primary` | Cast succeeds, Iron's mana decreases by `cost × multiplier` | Full trace; one `resource_spend` line |
| Inscribed Ars spell | `ars_primary` | Cast succeeds, Ars mana pays | Full trace; one `ars_cost_applied` line |
| Inscribed Ars spell | `hybrid` | Cast succeeds, both pools sync | Full trace |
| Inscribed Ars spell | `separate` | Cast succeeds, both pools pay split | Trace shows `ars_cost_applied` + `resource_spend` |
| Inscribed Ars spell | `disabled` | **Cast still succeeds** (regression fix), Ars native pool pays multiplied cost | Full trace; no secondary spend |
| Inscribed Iron spell | every mode | Same matrix, applied to Iron's | `iron_cost_applied` line |
| Dedicated server, Ars-origin cross-cast | any | Server logs `request_received` and `upstream_cast_enter` | Server log only |
| Dedicated server, Iron-origin cross-cast | any | Same | Server log only |
| Malformed NBT (edit one field) | any | Translated denial message; one `descriptor_rejected` line | Action-bar message; trace WARN |
| Death + respawn | any | Cross-cast still works immediately after respawn | Affinity/cooldown/resonance HUD reflects server state |
| Nether transition | any | Same | Same |
| Relog | any | Same | Same |
| Sneak-right-click cycle | any | Index advances on the item, HUD message shows `n/total` | `cycle_applied` trace line |

### Quick smoke test (5 minutes)

1. Boot dedicated server with Ars + Iron's + this jar.
2. `<world>/serverconfig/ars_n_spells-server.toml`: `debug_mode = true`.
3. Inscribe one Iron's `fireball` onto an Ars novice spellbook (or any neutral
   stack) via the Spell Transcription ritual.
4. Hold the inscribed item, right-click. Confirm:
   - The Iron's fireball actually fires (entity spawns, deals damage).
   - `latest.log` has the full `[CrossCastTrace]` sequence with a single
     `attempt=<uuid>` across all six stages.
5. Set `mana_unification_mode = disabled` and reload. Right-click again.
   Confirm the cast still works (regression fix vs 1.10.0 behavior).
6. Manually corrupt the NBT (edit `arsnspells:cross_spells[0].spell_id` to
   `irons_spellbooks:phantom_spell`). Right-click. Confirm an action-bar chat
   message in red and a single `descriptor_rejected` trace line.

## 3.0.0 — Ars spells in Iron's spellbooks (native wheel + Spell Loom)

Requires Ars Nouveau + Iron's Spellbooks + Ars 'n' Spells. Run on a **dedicated
server** for the authority checks.

### W1 — Spell Loom export → bind → native-wheel cast
1. Author an Ars spell (Scribe's Table) onto a spell parchment.
2. Craft and place a **Spell Loom** (`/give` `ars_n_spells:spell_loom` or the
   recipe: gold / lapis / book / obsidian). Right-click it.
3. Put the parchment in the source slot and a **blank `irons_spellbooks:scroll`**
   in the scroll slot. Type a name, cycle a nature and an icon, press **Inscribe**.
   Pass: an inscribed scroll appears in the output; source + one scroll consumed.
4. Bind the scroll onto an Iron's spellbook that already holds a native Iron's
   spell — drop both near a Spellbook Binding ritual brazier, or
   `/ans bind_scroll_to_irons_book` holding scroll + book.
   Pass: success message; the scroll is consumed.
5. Hold the spellbook, open Iron's **spell wheel**.
   Pass: the Ars spell shows as its own entry with your name + nature icon,
   **alongside** the book's native Iron's spell (which is unchanged).
6. Select the Ars entry, right-click to cast.
   Pass: the Ars spell resolves; with `debug_mode=true` a single
   `upstream_cast_enter runtime=ARS_PROXY` trace appears and mana is deducted once.
7. Select the native Iron's spell, right-click.
   Pass: native Iron's spell casts normally — no ANS interference.

### W2 — Mana-mode matrix for a wheel-cast Ars spell
Repeat W1 step 6 under each `mana_unification_mode`: `iss_primary`,
`ars_primary`, `hybrid`, `separate`, `disabled`. In every mode the cost is taken
**once** and the `cross_cast_cost_multiplier` is applied **once** (compare the
deducted amount to the same Ars spell cast from an Ars focus × the multiplier).
In `separate`, confirm the dual-cost split with no double-drain and no free cast.
With insufficient mana, the cast fails and the **wrong** resource is not consumed.

### W3 — Cap, dedup, and disable
- Bind 8 distinct Ars spells to one book → all show in the wheel. A 9th fails
  with the `book_full` message. Set `max_ars_cross_spells_per_irons_spellbook=3`
  → the 4th fails.
- Re-binding an identical spell payload is rejected as a duplicate.
- Set `allow_ars_spells_in_irons_spellbooks=false` → binding is rejected with the
  `disabled` message; spells already bound keep working.

### W4 — Iron's absent (no crash)
With Iron's **not** installed: the server boots clean, the Spell Loom still
places/opens, and pressing Inscribe shows the `irons_missing` message instead of
crashing. (Automated: `CrossCastGameTests` / `ArsIronsExportGameTests` self-skip
the Iron-loaded paths; run `./gradlew runGameTestServer` with and without
`-PwithIronsRuntimeGameTests`.)

## P0 regression scenarios (1.9.0 stabilization pass)

These scenarios exercise the five critical fixes called out in the 1.9.0
changelog. If any of them fails, do not ship.

### S1 — Dedicated server boot, Ars only (no Iron's installed)

Validates **P0-1** (packet sidedness) and **P0-2** (Iron's classload safety).

1. Run a dedicated server with this jar and Ars Nouveau, **without** Iron's
   Spellbooks on the classpath.
2. Boot to the title.

Pass: server reaches "Done (...)! For help, type 'help'" with no
`NoClassDefFoundError`, no `ClassNotFoundException`, and no warnings about
`io.redspace.ironsspellbooks.*` symbols. Joining a player and casting any
Ars spell does not crash.

Failure modes to watch for: `IronsLPHandler` failing to load (would mean the
de-auto-subscribe didn't take), `SpellScalingUtil` static-init crash (would
mean the lazy-init refactor isn't being respected by some caller).

### S2 — AffinitySyncPacket on Ars + Iron's

Validates **P0-1**.

1. Server with Ars + Iron's. Single-player or dedicated server, both behave the same.
2. Cast any Ars spell whose dominant school maps to an `AffinityType`
   (e.g., a Projectile + Ignite Ars spell → FIRE).
3. Observe the affinity HUD / state on the casting client.

Pass: affinity level for that school increments by 1 and the client reflects
the new value within one tick. No `Wrong dist` warning in the server log.

### S3 — Scroll cast, sufficient LP (Cursed Ring)

Validates **P0-3** happy path.

1. Equip Cursed Ring (Sanctified Legacy). Ensure player health is >5 hearts
   so health-source LP is plentiful, OR have Blood Magic Soul Network filled.
2. Cast an Iron's spell from a scroll whose mana cost is non-zero.

Pass: the scroll consumes the action; LP is consumed exactly once at the
amount shown in the action-bar message. Player does not take residual damage.

### S4 — Scroll cast, insufficient LP, safe mode

Validates **P0-3** safe-mode branch.

1. Set `death_on_insufficient_lp = false` in config.
2. Drain LP below the calculated cost (e.g., set health to 1 heart with a
   high-cost scroll spell).
3. Cast the scroll.

Pass: scroll cast is cancelled; **no LP consumed**; player takes a small
silent health loss (2 HP) as the safety nudge; action-bar shows
"Insufficient LP - Scroll Cancelled".

### S5 — Scroll cast, insufficient LP, death mode

Validates **P0-3** death-mode branch — the path that pre-1.9.0 had no enforcer.

1. Set `death_on_insufficient_lp = true` in config.
2. Drain LP below the calculated cost.
3. Cast the scroll.

Pass: scroll cast resolves once (you see the spell effect); player dies once
(`DEATH: Insufficient LP (N LP required)` action-bar message); no double
hurt, no "spell cast for free with no consequence" outcome. Pre-1.9.0 the
spell would proceed and the player would survive — that is the bug.

### S6 — Cooldown global-per-category

Validates **P0-4** intentional cross-mod blocking.

1. Set `enable_cooldown_system = true` and `enable_cross_mod_cooldowns = true`.
2. Cast an Ars `OFFENSIVE` spell (e.g., Projectile + Ignite).
3. Within the cooldown window (default ~20 ticks; check
   `cooldown_category_duration`), cast an Iron's `OFFENSIVE` spell.

Pass: the second cast is blocked. This is the intended behavior in 1.9.0 —
cooldowns are global per category and do **not** isolate by mod.

## Finished-system scenarios (1.9.0)

### S7 — Iron's-side progression hook

Validates **T5a**.

1. Ars + Iron's. Note the player's `irons_spellbooks:fire_spell_power` value
   via `/attribute @s irons_spellbooks:fire_spell_power get`.
2. Cast an Iron's fire spell several times.
3. Re-check the attribute value.

Pass: the value increases via a transient additive modifier named
"Cross-Mod School Progression". After 100 casts the bonus is roughly +0.10
(0.1% per cast, capped at +25%).

### S8 — Iron's-side affinity hook

Validates **T5b**.

1. Ars + Iron's.
2. Cast an Iron's fire spell.
3. Inspect AffinityData (in absence of UI: read the player NBT or use a debug
   command).

Pass: `AffinityLevels.FIRE` increments by 1 and an `AffinitySyncPacket` is
sent to the casting player.

### S9 — Ars spell scaling actually applied

Validates **T5c**.

1. Ars + Iron's. Stand near a damageable target dummy.
2. Cast an Ars projectile damage spell. Note the damage dealt.
3. Increase `irons_spellbooks:spell_power` on the player (via
   `/attribute @s irons_spellbooks:spell_power base set 2.0`).
4. Cast the same spell.

Pass: the second cast deals roughly twice the damage, capped by
`spell_power_cap` (default 3.0).

### S10 — Affinity decay (opt-in)

Validates **T5d**.

1. Set `enable_affinity_decay = true` and `affinity_decay_interval_ticks = 100`
   (faster cycle for testing).
2. Cast a spell to build, e.g., FIRE affinity to level 50.
3. Stop casting and wait several decay intervals (~25 s at the test setting).

Pass: affinity level decreases at each interval; client receives an
`AffinitySyncPacket` per affected school. With `affinity_decay_rate = 0.01`
and the test interval, level 50 loses 1 per cycle (floor of `50 * 0.01 *
(100/24000) ≈ 0.0021`, but the implementation floors to a minimum of 1 to
ensure progress).

### S11 — Login sync for affinity

Validates **T5e**.

1. Build affinity in any school to level > 0.
2. Disconnect and reconnect to the server (or relog into the world).

Pass: HUD/state shows the persisted level immediately on join, without
requiring another cast to trigger a sync.

## Sanctified Legacy / curio scenarios

These exercise the existing pre-1.9.0 paths; they are unchanged in 1.9.0 but
listed here because the old testing guide content was the only doc covering
them and it was wrong about the Virtue Ring model.

### S12 — Cursed Ring (LP costs, Ars spell)

1. Equip Cursed Ring. Pick `lp_source_mode`.
2. Cast an Ars spell with non-zero mana cost.

Pass: mana cost is converted to LP; LP is deducted from health (or Blood
Magic Soul Network depending on mode). Pre-cast validation prevents casting
if LP is insufficient (safe mode) or proceeds with death (death mode).

### S13 — Virtue Ring (aura costs)

1. Equip Virtue Ring (Ring of the Seven Virtues).
2. Cast an Ars spell with non-zero mana cost.

Pass: mana cost is converted to **aura** (not a flat discount — that was the
pre-1.5 model and is no longer how Virtue Ring works). Aura regenerates over
time per `aura_regen_rate`; insufficient aura cancels the cast with an
action-bar message per `show_aura_messages`.

### S14 — Blasphemy curio discounts

1. Equip a Blasphemy curio (e.g., `fire_blasphemy`).
2. Cast a non-fire spell, then a fire spell.

Pass: both casts get the base 15% mana discount (`blasphemy_discount`); the
fire cast gets an additional 10% from the matching-school bonus
(`blasphemy_matching_school_bonus`). Ring wearers do not also receive the
Blasphemy discount: the Cursed/Virtue rings convert the cost to LP/aura
before the discount handler runs. (The old `allow_discount_stacking` option
was removed in 3.0.0 — it was never read by the code.)

### S15 — Curio attribute bridge (Apotheosis / Apothic Curios) — 2.6.0

Requires Apotheosis + Apothic Curios installed, plus Iron's Spellbooks.

1. Obtain a ring (or other curio) with a mana affix or a socketed mana gem —
   one that rolls Ars `max_mana` or Iron's `max_mana`. (`/apoth gem ...` /
   `/apoth affix ...`, or roll one from loot.)
2. With `read_curio_attribute_modifiers = true` (default), note your max mana,
   then equip the ring. Check `/ans info` or the mana bar.

Pass:
- In `iss_primary` / `hybrid`, an **Ars** `max_mana` curio affix raises the
  **Iron's** max mana via the bridge (not only Ars's pool).
- In `ars_primary`, an **Iron's** `max_mana` curio affix raises the **Ars**
  max mana.
- Total with the ring on = base + affix counted **once per pool** (no
  double-count).
- Set `read_curio_attribute_modifiers = false` and relog: the cross-mirror
  disappears; native single-system behavior remains.
- Launch without Apotheosis: no errors; `IManaEquipment` / Ars-enchantment
  curio mana still works as before.

## Inscription / cross-cast scenarios

These haven't changed in 1.9.0 but are still load-bearing for survival
gameplay. See [README §Cross-mod spell casting](README.md) for the full flow.

### S20 — Strict disambiguation on the brazier

1. Drop two filled Ars spell parchments (two sources) within three blocks of
   the Spell Transcription brazier.
2. Activate the ritual.

Pass: ritual fails with a chat message naming both items and explaining that
exactly one source + one blank target are required. Pre-1.8.9 this would
either silently pick one or corrupt an item.

### S21 — Spell Uninscription returns a bit-identical blank

1. Inscribe a cross-cast spell onto a target item. Note its NBT.
2. Run the Spell Uninscription ritual on the inscribed item.
3. Compare the resulting NBT to a fresh blank target item.

Pass: NBT is bit-identical (no residual root tags). The same item can be
re-inscribed cleanly.

### S22 — Ars → scroll → spellbook export/bind (3.0.0, requires Iron's)

1. Hold an item carrying an Ars spell (filled spell parchment, inscribed
   focus, or Ars spellbook) and run `/ans export_to_irons_scroll`.

   Pass: a real `irons_spellbooks:scroll` appears carrying the Ars spell. Its
   tooltip lists the embedded Ars spell (cross-spell tooltip), and it is a
   genuine Iron item (stacks/behaves like one).

2. With the exported scroll in one hand and a real Iron's spellbook in the
   other, run `/ans bind_scroll_to_irons_book` (or run the **Spellbook Binding**
   ritual with the scroll + spellbook in range of the brazier).

   Pass: the scroll is consumed and the Ars entry is appended to the spellbook.
   The spellbook tooltip now shows the embedded Ars spell, active index, and
   cast/cycle hints. Any pre-existing Iron's spells on the book (`ISB_Spells`)
   are untouched.

3. Right-click the spellbook to cast the bound Ars spell; sneak-right-click to
   cycle if more than one is bound.

   Pass: the Ars spell casts through ANS. Re-binding the same Ars spell is
   rejected as a duplicate (dedup keys off the spell payload, not the shared
   placeholder id).

These map to the automated coverage in `CrossCastGameTests` (run
`./gradlew runGameTestServer -PwithIronsRuntimeGameTests` with Iron's on the
GameTest classpath) and `ArsIronsExportGameTests` (Iron-less default run).

## 3.0.1 scenarios

### S18 — Config screen readability & read-only mode

Steps: open Mods → Ars 'n Spells → Config (a) from the title screen, (b) from
the in-world pause menu in singleplayer, (c) while connected to a dedicated
server. Repeat at GUI scales 1, 2, 3, and Auto. Optionally install a client
blur mod (e.g. "Blur") for (b).

Pass:
- The screen draws its own near-opaque dark background and a bordered content
  panel — text is crisp on light and busy backgrounds alike, and blur mods
  have no effect on legibility (the old "frosted glass" report).
- Rows have background stripes with hover highlight; ON/OFF and Mana Mode
  render as bordered buttons, and only clicks inside the drawn button react.
- Long descriptions truncate with "..." and show the full text as a tooltip
  on hover.
- Singleplayer: toggles flip, Done saves, and changes apply live.
- Dedicated server: all controls render greyed with no hover feedback, a
  "Read-only: server-managed config" note is shown, clicking ON/OFF and Mana
  Mode does nothing, Reset Toggles is disabled, and the footer button reads
  "Close". (Regression: boolean rows used to toggle the client's config
  mirror ungated.)

### S19 — Source Jar synergy must not force-load chunks (+ kill switch/tuning)

Steps: Iron's installed, mana unification on, mana partially drained, standing
next to a filled Source Jar.

1. Defaults: the regen bonus ticks about once per second.
2. Set `enable_source_jar_synergy = false` (server TOML) and rejoin: no bonus,
   and no scan activity in the debug summary. This is the recommended
   workaround for servers affected by the pre-2.6.2 chunk-load deadlock.
3. Set `source_jar_scan_interval_ticks = 100`: bonus cadence drops to ~5 s.
4. Set `source_jar_scan_radius = 1` and stand 3+ blocks away: no bonus.
5. Set `debug_mode = true`: a rate-limited
   `[ANS] SourceJar synergy: N scans, M skipped (unloaded chunks), K jar hits`
   summary appears at most once per minute — never per-block spam.

### Dedicated server chunk-border stress test (ANS-CRIT-005 regression)

On a dedicated server with several players if possible: teleport repeatedly to
ungenerated coordinates (`/tp @s 100000 ~ 100000`, then further), fly at high
speed across fresh chunk borders (Elytra + speed or `/effect`), relog at an
ungenerated border, and cross dimensions — all while mana unification and
Source Jar synergy are enabled.

Pass: the server never deadlocks, hangs, or logs watchdog stalls while chunks
stream in; with `debug_mode = true` the "skipped (unloaded chunks)" counter
increments during the stress instead. Synergy resumes within a second once the
chunks around the player finish loading (skipped scans are retried, never
cached).

## Troubleshooting

- **Server crashes during boot with `NoClassDefFoundError` for an Iron's class
  on an Ars-only install**: a class was missed by the classload-safety pass.
  Grep the stack trace for `io.redspace.ironsspellbooks` and ensure the
  origin class is registered behind `IronsCompat.isLoaded()` or
  `ModList.get().isLoaded("irons_spellbooks")`.
- **Affinity HUD shows zero after relog with non-zero NBT**: the
  `AffinitySyncOnLoginHandler` didn't fire. Check that
  `enable_affinity_system = true` and that the player capability is being
  attached (look for `bridge_data` capability resource location in any debug
  capability dump).
- **Cooldowns blocking spells from the "wrong" mod**: this is intentional in
  1.9.0. Cooldowns are global per category by design; see README §Cooldowns.
- **Ars spell damage not scaling with Iron's spell power**: ensure Iron's is
  installed (the scaling handler is gated on Iron's), `enable_resonance_system`
  and `enable_affinity_system` are at their default values, and the spell's
  damage source contains "magic" or "ars_nouveau" — the onFire/inFire match
  was removed in 3.0.x (it let lava ticks inherit the spell-power multiplier);
  the filter rejects melee/environmental damage from the same player.

## Reporting issues

Include in the bug report:
1. Mod versions (Ars Nouveau, Iron's Spellbooks, Sanctified Legacy / Covenant
   of the Seven, Blood Magic if installed) and Forge version.
2. The relevant `<world>/serverconfig/ars_n_spells-server.toml` keys.
3. `logs/latest.log` excerpts. With `debug_mode = true` enabled, look for log
   lines prefixed `[Cooldown]`, `[CurioDiscount]`, `[Affinity]`, or messages
   from `MixinScrollItem`, `IronsLPHandler`, `LPDeathPrevention`.
4. The exact reproduction steps tied to one of the scenarios above where
   possible.
