# Ars 'n' Spells — full audit: stability, optimization, functionality

**Date:** 2026-08-25 · **Branch:** `port/1.21.1-neoforge` · **Scope:** the whole mod, not the
port diff · **Reference:** the Forge 1.20.1 3.2.0 working tree at `C:\Projects\ars-n-spells`

Companion to [`PORT_AUDIT_1.21.1.md`](PORT_AUDIT_1.21.1.md), which covers the port to parity.
This document covers what the port itself got wrong, and what both lines get wrong.

---

## 0. Verdict

**32 confirmed defects, all fixed.** Six commits.

The dominant finding is a class, not an incident: **the port carried the 1.20.1 line's
behaviour across but dropped its guards.** Twenty-one of the thirty-two are regressions
against fixes the Forge line documents by ticket id — `ANS-CRIT-003`, `ANS-CRIT-004`,
`ANS-HIGH-004`, `ANS-MED-005`, `ANS-MED-010`, `ANS-OPT-006`, `ANS-OPT-008`, audit `E5`, and
others. Each was solved once upstream and reintroduced here.

The most serious were:

| | Defect | Class |
|---|---|---|
| **P0** | Uninscription left orphan wheel entries that cast nothing | regression |
| **P0** | Spell Loom's Inscribe button overlapped nine inventory slots and blocked clicks | regression |
| **P1** | Every spell under 50 mana was free at the config's rate floor | regression |
| **P1** | A failed mana consume double-charged the cast | regression |
| **P1** | A malformed save field silently erased every school's affinity | inherited |
| **P1** | Ars mana-regen potions counted twice | port-added |
| **P1** | Four per-player maps never evicted a key | regression |

Verified green: `build` (**26 classes / 187 tests, 0 failures**) and `runGameTestServer` on
the Iron's-less profile (**12/12 required tests passed**, zero mixin failures). Every fix that
could carry a regression test does, and the ones marked *fail-before verified* below were
demonstrated failing against the pre-fix sources and passing after — in that order, not
asserted.

**Still not exercised: a running client.** §6 lists what needs a human at `runClient`.

---

## 1. Method

Four passes: three deep agent passes (stability, performance, client-surface functionality)
and my own.

The highest-yield technique on a port is not generic code review. The 1.20.1 original carries
dozens of *documented* fixes tagged `ANS-CRIT-###` / `ANS-HIGH-###` / `ANS-MED-###` /
`OPT-###` / `F#` / `D#`. **Checking whether each survived the port** finds bugs that were
already solved once and silently reintroduced — the highest-value class of finding, and one
that reading the new code on its own terms will never surface, because the new code looks
perfectly reasonable. It is only wrong relative to something it does not mention.

Every agent finding was verified against the shipped dependency bytecode or the 1.20.1 source
before anything was changed. Two did not survive that check and are in §5.

---

## 2. Findings and fixes

Ranked by severity. **R** = regression against a documented upstream fix. **I** = inherited,
the 1.20.1 line has it too. **P** = introduced by the port with no upstream counterpart.

### 2.1 Uninscription left orphan wheel entries — P0 · R

`SpellUninscriptionRitual` called `CrossModSpellComponents.clear(stack)` and nothing else.

The proxy pool ids — the record of which of Iron's native wheel slots belong to this mod —
live *inside* that component. Clearing it first destroys the only thing that says which slots
to remove, so the wheel keeps showing selectable entries that resolve to nothing. It also left
this mod's `export_mode` marker behind, so the result was not the never-inscribed item the
ritual promises.

**Fix:** ported `IronsBookBindingUtil.removeAllArsEntries` — native slots first, then the
sidecar, then the marker. Upstream's own comment states the ordering requirement.

**Acceptance:** `UninscribeTeardownGameTests` (3). *Fail-before verified:* against the bare
`clear()` the round trip reports `the export marker survived uninscription` and
`uninscribed item still differs from a blank one: {ars_n_spells:export_mode=>...}`.

### 2.2 The Spell Loom's Inscribe button covered the inventory — P0 · R

The port hardcoded the container at the vanilla 166px height while keeping the taller layout's
button rows. Inscribe occupies `y+74..92`; the inventory's top row starts at `y+84`.
`AbstractContainerScreen` draws widgets **before** slot items and the hover highlight, so items
and a pale highlight square painted over the button — and the overlapped nine pixels of nine
slots stopped taking clicks.

**Fix:** back to 176×208, with the slot geometry as named constants on the menu that the
screen derives from, so the two cannot drift again.

**Acceptance:** `SpellLoomLayoutTest` (3), reading the *sources* rather than the compiled
constants — a test referencing `static final int`s compares values the compiler already
inlined into the test and can never fail. *Fail-before verified:* `the Inscribe button
(y 74..92) runs into the player inventory (first row at y 84)`.

### 2.3 The Spell Loom lost its entire client-side validation mirror — P1 · R

No `containerTick`, no `updateInscribeState`, no `firstInscribeProblem`. Inscribe was always
enabled, so the only way to learn the output slot was full, the source held no Ars spell, or
Iron's was not installed was to press it and read an action-bar error. Eight lang keys were
dropped with it, including `error.output_full`, which shipped orphaned.

Also gone: `resize()` preserving the typed name (init rebuilds the `EditBox`),
`renderLabels()` (vanilla's `0x404040` on a purple panel), slot bevels, the real wheel icon in
the preview instead of a flat colour, and the working-slot tooltips.

**Fix:** all restored, reusing the server's own lang keys so client and server cannot
disagree. 1.21.1 delta: `EditBox` has no `tick()` any more — the caret blink is time-based.

**Acceptance:** `SpellLoomLayoutTest.everySpellLoomLangKeyTheGuiUsesIsTranslated`.
*Fail-before verified:* found only 4 of the referenced keys present.

### 2.4 Every spell under 50 mana was free at the rate floor — P1 · R

Validation computed `(float)(cost * rate)`; charging computed `(int) Math.round(cost * rate)`.
At `conversion_rate_ars_to_iron`'s 0.01 floor, everything under 50 mana rounded to zero and
was charged nothing after being checked against a non-zero cost.

**Fix:** both paths call `CastingAuthority.effectiveArsCost`.

### 2.5 A failed consume double-charged the cast — P1 · R (`ANS-MED-010`)

`MixinSpellResolverMana` cancelled only on a *successful* consume. On failure it fell through
to Ars's native `expendMana`, which decremented the Ars pool as well.

**Fix:** cancel unconditionally, and trace the spend.

### 2.6 The dual-cost split silently overcharged — P1 · R (`ANS-MED-005`)

`dual_cost_ars_percentage` and `dual_cost_iss_percentage` are independently range-checked
`[0,1]`, so a pair summing to 1.2 is a valid config — the init-time sum check only `WARN`s.
Both spend paths multiplied the raw percentages into the cost, so that config overcharged 20%
on every cast, and an under-1.0 pair undercharged.

**Fix:** one `AnsConfig.dualCostSplit()` that normalises, used by `BridgeManager` and the
cross-cast cost-calc handler alike.

### 2.7 The rollback clobbered concurrent mana — P1 · R (`ANS-CRIT-003`)

The SEPARATE-mode rollback was snapshot-and-restore: `setMana(manaBefore)`. Any regen, buff or
ritual mana that landed between the snapshot and the rollback was erased.

**Fix:** both bridges override `addMana` to delegate to their backing API's atomic add, and
the rollback is a compensating refund. `IManaBridge`'s default is `setMana(getMana() + x)`,
which is exactly the race the ticket was filed against.

### 2.8 A malformed save field erased all affinity — P1 · I

`AffinityData`'s payload fields were strict. One bad entry failed the record; the legacy
fallback failed too; NeoForge's `AttachmentHolder.deserializeAttachments` catches that and
**silently drops the whole attachment**. A garbled decay remainder cost every school's
affinity, with nothing in the log.

**Fix:** both fields lenient (`orElseGet` — `optionalFieldOf` alone covers an *absent* field,
not a *malformed* one), and the decay default is no longer one mutable instance shared by
every decode. `ProgressionData` gained the sanitization it never had: it copied whatever was on
disk straight through, and a negative cast count becomes a negative attribute bonus with no
floor downstream.

**Acceptance:** 3 tolerance tests in `AffinityDataMigrationTest`. *Fail-before verified.*

### 2.9 Ars mana-regen potions counted twice — P1 · P

`MixinArsPotionEffects` mirrored the Ars `mana_regen` / `mana_boost` effects onto Iron's
attributes. Against Ars 5.x both halves are wrong:

- `ars_nouveau:mana_boost` **is not a registered `MobEffect`** — `ModPotions` declares only
  `MANA_REGEN_EFFECT`. The max-mana half could never fire.
- `MANA_REGEN_EFFECT` already applies a modifier to `PerkAttributes.MANA_REGEN_BONUS`, which
  `EquipmentIntegration` reads and mirrors onto Iron's `MANA_REGEN` once per second. The mixin
  added the same potion's regen a **second** time.
- Its `@Inject` sat at the HEAD of `ManaCapEvents.playerOnTick`, ahead of Ars's own
  `ServerPlayer` and interval guards, so it ran at 20 Hz on both logical sides — five times
  Ars's own rate, half of it on the client.

The mod's own changelog already recorded the 1 Hz refresh as *"replacing the old
`MixinArsPotionEffects` with no double-counting"*. The port shipped both.

**Fix:** deleted, with its manifest entry, its plugin gate and its now-dead class probe.

**Acceptance:** `ArsNSpellsMixinPluginGatingTest.mixinArsPotionEffects_staysDeleted`.

### 2.10 Four per-player maps never evicted a key — P1 · R

`ScrollLPTracker.PENDING`, `ResonanceManager.resonanceCache`, `CrossCastContext.ACTIVE_CASTS`
and `ArsSpellScalingHandler.ACTIVE` all expire their *values* on the owning player's next
touch, but nothing removed the *key*. Every player who ever logged in stayed resident until
restart, and a player who logged out mid-cast left a live entry behind.
`ResonanceManager.cleanupOfflinePlayers` and `ScrollLPTracker.clear` both already existed with
**zero callers**.

**Fix:** one `StateEvictionHandler` covering logout, server stop, and the 1200-tick sweep
`ANS-MED-028` specifies. Server stop matters on an integrated server, where the JVM survives
world exit and the statics carry into the next world.

### 2.11 Resonance had no bounds at all — P1 · R (`ANS-HIGH-006/007`)

Six guards, all present upstream, all absent: the `manaPercent` clamp to `[0,1]`, the
`max_damage_multiplier` cap (so the key was dead), the packet `isFinite`/`>=0`/cap, the client
receive clamp, `volatile` on `clientResonance`, and the downstream clamp.

The load-bearing one is the `manaPercent` clamp. `manaPercent` is `mana / max`, and the
shared-pool ceiling work already established that mana above max is reachable; past 1 the
expression is unbounded, and on the Iron's damage path nothing downstream catches it. `NaN`
matters too: `Math.min(NaN, cap)` returns `NaN`, which is precisely why upstream clamps at the
packet.

**Fix:** arithmetic extracted as pure statics (`resonanceFor`, `clampClientResonance`) so it
carries tests without a Minecraft bootstrap and without Iron's on the test classpath.

**Acceptance:** `ResonanceManagerClampTest` (13). *(Fixed in `397ea4c`.)*

### 2.12 Packet input validation was dropped wholesale — P1 · R (`ANS-MED-016/017`)

The `SimpleChannel` → `CustomPacketPayload` migration carried the wire format but not the
hardening. Every payload read unbounded strings — `ByteBufCodecs.STRING_UTF8` defaults to
`Short.MAX_VALUE`, 32 KB allocated per string on demand — and applied no range checks.

**Fix:** bounded string reads and compact-constructor clamps on all four payloads, plus
`CrossModSpellList`'s stream codec, which used an unbounded list and let a peer declare any
element count.

**Acceptance:** `PayloadBoundsTest` (16). *(Mostly fixed in `397ea4c`.)*

### 2.13 Config reads that throw on the render thread — P1 · I

`ModConfigSpec.ConfigValue#get()` throws `IllegalStateException` when its spec is not loaded —
reachable at the main menu and during world transitions, not just pathologically. The
`DEBUG_MODE != null && DEBUG_MODE.get()` idiom used in nine places **is not a guard**: the
field is assigned at class-init, long before the config loads, so the check passes right up to
the `get()` that throws.

The one that mattered most is `ResonanceManager.getResonance`, reached from
`AbstractSpell.getSpellPower` — which the client calls while rendering the spell wheel and the
inscription table. A throw there is a hard crash of the render loop.

**Fix:** `AnsConfig.flag` / `debugEnabled`, and every risky read routed through them.
`AffinityDecayHandler` and `RegenSynergyHandler` additionally read config *before* their
`ServerPlayer` check, on an event that fires for client players too — gates reordered.

### 2.14 `SpellScalingUtil` scaled off the wrong school — P1 · R

It called `SpellAnalysis`, **discarded the answer**, and re-derived the element with a
different substring test. Firework counted as generic for affinity but matched `"fire"` for
scaling; `glyph_ender_inventory` scaled as ender; a path containing two element words resolved
by hash iteration order.

The port's own CHANGELOG claimed this was fixed. It was fixed in `SchoolResolver` and
`SpellAnalysis` and not here.

**Fix:** one map lookup on the school `SpellAnalysis` already resolved.

### 2.15 `onArsSpellCost` lost `EventPriority.HIGHEST` — P1 · R (`ANS-CRIT-004`)

At default priority, any listener that rewrites `event.currentCost` first turns the documented
1.25× cross-cast premium into 0×1.25.

### 2.16 `CrossCastContext.Entry` lost `volatile` and its atomic one-shot — P1 · R (`ANS-HIGH-004`)

The mutable fields were plain, and `multiplierApplied` had reverted from an `AtomicBoolean`
CAS to a read-then-write. The event can fire more than once per resolve (preview vs. actual
deduction), and losing that race applies the cross-cast premium twice. The TTL had also
drifted back to 200 ticks from `ANS-OPT-006`'s 100 — and a stale entry is not inert, because
`peek()` hands it to the cost-calc handler, which then charges the premium on an unrelated
later cast.

### 2.17 Two advancements were unobtainable — P1 · R

`transcribe_spell` and `bind_spell` use `minecraft:impossible` criteria, so they are awarded in
code. Neither had a `grant` call anywhere. Restored at all three sites (the loom payload, the
binding ritual, `/ans bind`).

### 2.18 The wheel never found a Curios-equipped spellbook — P1 · R

`MixinAbstractSpellArsIcon` checked both hands and nothing else — but a bound book normally
sits in the Curios spellbook slot *while its entries are being rendered*, and neither hand
holds it, so the wheel fell back to the default icon and name.

**Fix:** check `Utils.getPlayerSpellbookStack(player)` first. (Verified present in Iron's
3.16.3 at `io.redspace.ironsspellbooks.api.util.Utils`.)

### 2.19 A drifted mixin was undetectable — P1 · I

Three things compound. Every injection carries `require = 0` (deliberately — a drifted target
must not take Ars or Iron's down with it), so drift does not fail the load, it just silently
stops working. `neoforge.mods.toml` declared Iron's `[3.15.0, 4.0.0)` — **eleven releases** —
while the mod mixins into Iron's *non-API* internals (`gui.inscription_table.*`, `item.Scroll`,
`MagicData`'s private `serverPlayer`), and only 3.16.3 was ever verified. And upstream's
`runRingIntegrationSelfCheck` was not ported.

**Fix:** `MixinSelfCheck` verifies at common setup that every targeted method still exists and
that `MagicDataAccessor` actually attached, and the declared floor is raised to the verified
3.16.3. Confirmed in the GameTest run: `[SelfCheck] Mixin targets OK (4 checked, Iron's
absent)`.

### 2.20 The affinity resync was an unbounded packet burst — P2 · P

One packet per tracked school, on login, on respawn **and** on every dimension change. The
1.20.1 line did the same against a fixed 16-value enum, so it was bounded at 16; this port
deliberately re-keyed affinity to full school ids so addon schools are tracked — a real
improvement that turned a bounded burst into an unbounded one.

**Fix:** `AffinityBulkSyncPayload` — one packet, whole map, count and id length bounded.
Protocol `"3"` → `"4"`. `AffinitySyncPayload` stays for the single-school deltas the cast and
decay handlers actually produce.

**Acceptance:** 4 bounds tests in `PayloadBoundsTest`.

### 2.21 `syncIronsMaxToArs` dirtied `MAX_MANA` every second — P2 · R (`ANS-OPT-008`)

It removed and re-added its modifier unconditionally. Removing a modifier marks the attribute
dirty, and the server drains that into a `ClientboundUpdateAttributesPacket` at end of tick —
one per player per second, forever, whether or not anything changed. Its sibling
`applyAttributeModifier` had the guard; this one did not, and `EquipmentHandler`'s comment
claimed otherwise.

**Fix:** decide before touching the modifier map. The check is exact by construction: applying
`modifierAmount()` leaves the attribute at `max(ironsOwnMax, arsMax)`, so with the modifier
present the ceiling is right iff `getValue() == arsMax`, and without it iff `getValue() >=
arsMax`.

### 2.22 The Source Jar scan re-swept on every interval — P2 · I

324 block reads at the default radius, cached against a 4-block move threshold — which a
walking player clears every second, so the cache almost never survived.

**Fix:** remember *where* the jar was. One block read re-validates it while the player is
working near their jars, which is the case the feature exists for. Only ever shortcuts a
positive answer, so it cannot manufacture a false negative.

### 2.23 Resonance recomputed twice as often, ungated, and synced blind — P2 · R

The interval had been halved 40 → 20 ticks, the `ENABLE_RESONANCE_SYSTEM` gate dropped from the
handler, and the sync packet sent unconditionally.

**Fix:** gate restored, interval back to 40, and `computeResonance` now reports whether the
value moved so the sync only fires when it did. Login always syncs — the client starts at
neutral and has nothing to mirror until the first packet.

### 2.24 `CrossCastContext` ticked for client players — P2 · P

On an integrated server both logical sides share the static map, so the client player's tick
evicted the *server* player's in-flight context using client game time.

### 2.25 Five config keys nothing read — P2 · mixed

A key that generates into every server's TOML and is read by nothing tells the owner they have
a knob they do not have.

| Key | Class | Disposition |
|---|---|---|
| `max_damage_multiplier` | R | wired — it is the resonance cap (§2.11) |
| `enable_ars_resonance` | I | wired — gates `SpellScalingUtil` |
| `enable_irons_resonance` | I | wired — gates `MixinIronsSpellDamage` |
| `resonance_threshold` | I | **wired as a gate** — see below |
| `resonance_duration` | I | **wired as a gate** — see below |
| `read_curio_attribute_modifiers` | P | **removed** — the behaviour is unconditional |

**The threshold pair deserves its own note, because my first answer was wrong.**
`resonance_threshold` documents *"mana percentage required to trigger resonance"* and
`resonance_duration` *"how long resonance lasts after dropping below threshold"*. **No such
behaviour has ever existed in either line** — `resonanceFor` scales linearly from 0% mana with
no gate at all. I initially removed both keys as false advertising, on the grounds that
implementing them as written would change resonance on every existing server.

That framed it as a choice between two bad options, and it is not one. **The gate and the
curve are orthogonal.** `resonanceFor` decides *how large* the bonus is; a gate decides
*whether* it is granted. Layering the second on the first costs the curve nothing:

- `ResonanceManager.gateOpen(manaPercent, threshold, ticksSinceAbove, duration)` — the bonus
  applies at or above the threshold, or within `duration` ticks of last having been.
- **The default `threshold` is 0**, not the historical 0.95. Any clamped mana fraction is at or
  above 0, so the gate is permanently open and the mod behaves exactly as it always has. No
  existing server's damage numbers move.
- Set it to 0.95 and resonance becomes the burst window the config text has always described:
  top the pool off, get roughly five seconds of boosted casting.

The linger is what makes a raised threshold playable at all — spending mana to cast
necessarily drops you below the threshold, so without it the bonus would switch off on the
very cast that earned it.

Both keys are now live knobs, the config text is true, and nothing changed for anyone who does
not opt in. The default is load-bearing enough to have its own guard
(`resonanceThreshold_defaultsToZeroSoTheGateIsOpen`): shipping the historical 0.95 would turn a
passive trickle into a burst window on every world that updates — a balance change disguised
as a bug fix.

`read_curio_attribute_modifiers` is different: what it describes is not missing, it is
*unconditional and inseparable*. Curios applies its modifiers to the **player's** attributes,
and `EquipmentIntegration` mirrors the aggregate `PerkAttributes.MAX_MANA` /
`MANA_REGEN_BONUS` — so curio mana gear (Apotheosis affixes, Magical Jewelry, …) already feeds
the bridge, with no per-item scan and no toggle to honour.

**Acceptance:** `AnsConfigStructureTest.everyNonCovenantConfigKeyHasAReader` sweeps every
declared key against every reader. *Fail-before verified:* it reports exactly those five.
The gate itself carries 8 tests in `ResonanceManagerClampTest`, the first of which pins that
threshold 0 leaves the gate open at every mana level and whatever the linger state — the
no-behaviour-change guarantee, asserted rather than assumed.

### 2.26 Smaller confirmed defects

| # | Defect | Class |
|---|---|---|
| a | The binding ritual reported `error.scroll_parse_failed` for a *book* rejection, sending players to re-drop a scroll that was never the problem. `error.bind_failed` shipped orphaned. | R |
| b | `hybrid_mana_bar` accepted any string and resolved it silently — to a *different* bar than the 1.20.1 line picked. Validated at load now. | P |
| c | `OverlayDiagnostics` cleared a bare `TreeSet` off the render thread while the render thread added to it; `diagnosticsEnabled` was non-volatile. (Reachable only because I made the tool reachable in `5d7b5fe`.) | P |
| d | `SpellLoomMenu.quickMoveStack` lost the audit-`E5` desync guard: without it a failed client-side BE lookup leaves only the 36 player slots and the index math misclassifies player slots 0–2 as loom slots. | R |
| e | `UnifiedCooldownManager`'s `modNamespace` parameter never reached the storage key, only the debug log — callers passing `"ars"` and `"irons"` shared one cooldown while the signature promised isolation. Upstream removed it in 1.9.0. | R |
| f | `EfficiencyCalculator`, `ProgressionTracker`, `SpellCategoryMapper`: no callers, no upstream counterpart. `SpellAnalysis`'s own comment says the mapper *"has been removed"*. | P |

---

## 3. Regression tests added

| Test | Count | Guards |
|---|---|---|
| `UninscribeTeardownGameTests` | 3 | §2.1 — *fail-before verified* |
| `SpellLoomLayoutTest` | 3 | §2.2, §2.3 — *fail-before verified* |
| `ResonanceManagerClampTest` | 21 | §2.11, §2.25 |
| `PayloadBoundsTest` | 16 | §2.12, §2.20 |
| `AffinityDataMigrationTest` (added) | 3 | §2.8 — *fail-before verified* |
| `AnsConfigStructureTest` (added) | 2 | §2.25 — dead-key sweep *fail-before verified* |
| `ArsNSpellsMixinPluginGatingTest` (replaced) | 1 | §2.9 |
| `ManaBarControllerOverlayMatchTest` | 4 | the mana-bar P1 (`5d7b5fe`) |

**26 classes / 187 unit tests, 0 failures. 12/12 GameTests.**

Per the agreed scope, only the tests that guard a fix were ported — not the whole 34-class
backlog. That backlog is still open; see §6.

---

## 4. Do NOT "fix" these

Things that look wrong and are deliberate. Each has been mistaken for a bug at least once.

- **The 24 Covenant of the Seven config keys with no reader.** Covenant (LP / aura) has no
  1.21.1 release. The keys stay declared so an existing server's TOML carries over untouched
  and re-enabling the subsystem is a compile-scope change, not a config migration. The dead-key
  sweep in §2.25 exempts them by marker, scoped so a *new* dead key cannot hide in the block.
- **`src/covenant-disabled/` is not a source set.** Same reason. Deliberately excluded from
  compilation, deliberately kept in tree.
- **`require = 0` on every injection.** This is the safe failure mode, not an oversight. The
  invisibility it causes is addressed by `MixinSelfCheck` (§2.19), not by removing it.
- **The plain `HashMap`s under the server-main-thread-only invariant.** Not every per-player
  map here needs to be concurrent; the ones that are, are.
- **`MixinManaCapability`'s client-side bail.** Intentional.
- **`AnsConfig`'s "applies immediately — no restart needed" note.** Accurate: `refreshMode()`
  runs from the config listeners, `/ans mode set`, and the config screen.
- **String-keyed `AffinityData` instead of the 1.20.1 enum.** A deliberate divergence and a
  superset — it is what lets addon schools be tracked at all.

---

## 5. Agent findings that did not survive verification

Recorded because "we checked and it was fine" is worth as much as a fix, and because both
would have been damaging to act on.

**`irons_spell_books.json` is stale.** Claimed `legendary_spell_book` was removed from Iron's
and `illusioner_spell_book` added. Neither is true: `ItemRegistry` in 3.16.3 registers exactly
the sixteen ids the tag lists, `legendary_spell_book` included, and there is **no** reference to
`illusioner` anywhere in the registry. The claim came from a leftover entry in Iron's lang
file. Acting on it would have removed a real book from the tag and added one that does not
exist. **The tag is correct as shipped.**

**`ANS-MED-028` server-tick cleanup is missing.** True as stated, but the recommended fix — a
`ServerTickEvent` subscriber — was folded into `StateEvictionHandler` (§2.10) rather than
added separately, since the same handler already owns the eviction it would drive.

---

## 6. Open, and honestly labelled

Nothing below is a defect I found and declined to fix. These are limits of what this pass
could establish.

**The client surface is still unverified.** Everything here was validated headlessly plus one
earlier `runClient` session that found the mana-bar P1 — which is the whole argument for doing
this: the last time client code here was checked by reasoning alone, the first real run found a
P1 inside a minute.

Written up as **C1–C6** in [`TESTING_GUIDE.md`](TESTING_GUIDE.md), roughly ten minutes with
Iron's installed. It covers the failure classes static analysis categorically cannot reach: the
9-argument `blit` sampling the icon with the wrong texture dimensions, whether a `Tooltip` on an
*inactive* Button renders at all, tooltips clipping at the new 208px height, the `EditBox`
surviving a resize, and whether the Curios wheel-icon lookup fires on its client-only render
path.

**Iron's compatibility is verified at exactly one version.** The declared floor is now 3.16.3,
which is honest rather than optimistic. Widening it again should mean actually testing the
floor — `MixinSelfCheck` will now say so at startup if a target has moved, which is the point.

**The test backlog is still 30+ classes.** `events`, `mixin`, `network` and `cooldown` remain
the thinnest-covered packages in the mod, and they are where defects keep being found. Per the
agreed scope this pass added only the tests that guard the fixes above.

**No `v3.2.0` tag existed.** Created. The project's convention requires `gradle.properties`,
`update.json`, the changelog, the tag and the published jar to agree; everything agreed except
the tag. That is the exact lapse that lost 3.0.3 upstream and forced its recovery by
decompiling the shipped jar.
