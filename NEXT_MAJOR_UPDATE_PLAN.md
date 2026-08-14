# Ars 'n' Spells — Next Major Update Plan

**Status:** Phase 1 complete (reproduce and document). Phases 2–7 not started.
**Author:** coding agent
**Date:** 2026-08-13
**Working tree at time of writing:** `main` @ `3017bbc` ("Release 3.0.2"), clean, identical to `origin/main`.

---

## 0. Executive summary — read this before writing any code

Phase 1 turned up one finding that changes the shape of the whole project:

> **The published `ars_n_spells-3.0.3.jar` was never committed to this repository.**
> It exists on CurseForge (file `8490162`, uploaded 2026-07-22), it is what the crash
> reporter is running, and it contains **14 changed classes and 3 new classes** relative
> to 3.0.2 — including fixes for two of the five reported defects. There is no `v3.0.3`
> git tag, no `3.0.3` entry in `CHANGELOG.md` or `update.json`, and no GitHub release.

Building the next version from this tree as-is would have **silently reverted every 3.0.3 fix**.
The delta has now been reconstructed into source control and verified against the published
artifact — see [§2.4](#24-decision--resolved-303-reconstructed-into-source-control). Phase 2 can
proceed from a tree that matches what users are running.

Second finding: the crash is **not** version drift. Iron's API surface that ANS depends on is
**binary-identical** between the compile pin (3.15.0) and the crashing runtime (3.16.2) — see
[§6](#6-irons-version-compatibility-evidence). The NPE is a latent ANS bug that reproduces on
every Iron's version in the advertised range, and it is **still unfixed in 3.0.3**.

Third finding, which raises the crash's severity: the reported client NPE is one of **three**
unguarded dereferences of the scroll's spell container. The other two are in
`InscriptionTableMenu` and run on the **server thread** — so on a dedicated server, any player
who puts a legacy ANS scroll in an inscription table and clicks Inscribe crashes the server, not
just their own client. See [§4.1](#41-client-crash--ispellcontainer-is-null--confirmed).

---

## 1. Exact artifact versions and checksums

### 1.1 Artifacts obtained and verified

| Artifact | Source | Size (bytes) | SHA-256 |
|---|---|---:|---|
| `ars_n_spells-3.0.3.jar` | CurseForge file `8490162` | 387,296 | `cc580f834bdbbd58f7180946d87204b177871174d6e43a8cc917298ee13610c3` |
| `ars_n_spells-3.0.2.jar` | CurseForge file `8389140` | 376,457 | `135e786af51555ac4b592b73d5d813ef757ef54523fd857f4e50e9be0378e359` |
| `irons_spellbooks-1.20.1-3.16.2.jar` | CurseForge file `8364933` | 15,554,022 | `b3a2cc3eac6990dd5badb845bb2e2b74ff89539bc54d793045ee1af6c70cd155` |
| `irons_spellbooks-1.20.1-3.15.0.jar` | CurseForge file `7402504` (the compile pin) | 14,149,632 | `92c046383b4960c655f840d8846732a481edcf7c5ed89028b3d7b2cc2910b224` |
| Crash report `fadrgdM` | `https://api.mclo.gs/1/raw/fadrgdM` | 1,052 lines | MD5 `b0e68bbd3425e2bbc99f407c4430c7b0` |

Secondary checksums for 3.0.3: SHA-1 `59f243547e8c12fe153555241450d804c38a0d52`, MD5 `a8fb9948b3b176fbcf317a40b41698ad`.

Download URL pattern used (CurseForge CDN, no API key required):
`https://mediafilez.forgecdn.net/files/<first-4-digits>/<remaining-digits>/<filename>`

### 1.2 Environment in the crash report (verified by grepping the raw log, not summarised)

| Component | Version | Log line |
|---|---|---|
| Minecraft | 1.20.1 | 101 |
| Forge | 47.4.22 | 143 |
| Ars 'n' Spells | **3.0.3** (`ars_n_spells-3.0.3.jar`) | 840 |
| Iron's Spells 'n Spellbooks | 1.20.1-3.16.2 | 687 |
| Ars Nouveau | 4.12.7 (`ars_nouveau-1.20.1-4.12.7-all.jar`) | 818 |
| Ars Elemental | 0.6.8.0 | 819 |
| JEI | 15.49.0.187 | 778 |
| EMI | 1.1.24+1.20.1+forge | 563 |
| Environment | Integrated server (single-player), client crash | 151 |

Too Many Glyphs is **not** present in the crash pack. Ars Elemental 0.6.8.0 **is**, and matches
the version the brief asks us to test against.

> **Correction to an intermediate finding.** An automated summary of the crash log initially
> reported "ars_n_spells 1.0.1" and "ars_nouveau 3.0.3". That was a summarisation error. The raw
> log, grepped directly, gives the table above. Every artifact claim in this document is taken
> from the raw file, and the raw file's own checksum is recorded so the claim is auditable.

### 1.3 Repository vs. published artifact

| | Version | Git tag | Notes |
|---|---|---|---|
| This repository (`main` @ `3017bbc`) | 3.0.2 | `v3.0.2` | `gradle.properties:mod_version=3.0.2` |
| Latest upstream tag | 3.0.2 | `v3.0.2` | `git ls-remote --tags origin` shows only `v3.0.1`, `v3.0.2` |
| GitHub Releases | — | — | `/releases` API returns an empty array |
| **Latest published artifact** | **3.0.3** | **none** | CurseForge, 2026-07-22, 15 days after 3.0.2 |

---

## 2. Release-provenance resolution

### 2.1 What 3.0.3 actually is

3.0.3 is an unversioned hotfix for the **"bound spell casts but does nothing"** report. It was
built from a source tree that is not in this repository. Recovered by decompiling both jars with
CFR 0.152 and diffing:

**New classes (3):**
- `spell/irons/ArsCrossProxySpell$Carrier` — record `(ItemStack book, CompoundTag entry)`
- `gametest/IronsProxyCastDriver`
- `commands/ArsNSpellsCommands$1` (switch-map synthetic)

**Changed classes (14):** `ArsNSpellsCommands`, `IronsAffinityHandler`, `IronsCooldownHandler`,
`IronsLPHandler`, `IronsLPHandler$PendingIronsLP`, `IronsProgressionHandler`,
`CrossCastGameTests`, `MixinAbstractSpellArsIcon`, `SpellbookBindingRitual`,
`CrossCastIronsHandler`, `CrossCastNbt`, `CrossCastingHandler`, `IronsBookBindingUtil`,
`ArsCrossProxySpell`.

**Behavioural delta, by theme:**

1. **Proxy spells excluded from Iron's-side accounting.** New
   `CrossCastNbt.isArsCrossProxyId(String)` (prefix `ars_n_spells:ars_cross_`), called as an
   early return in `IronsAffinityHandler`, `IronsCooldownHandler`, `IronsLPHandler` (×2),
   `IronsProgressionHandler`, and `CrossCastIronsHandler` (×2). Before this, casting a bound Ars
   spell through the native wheel also ran Iron's affinity/cooldown/LP/progression/mana logic
   against a fake ENDER-school zero-cost spell.
2. **Casting-item resolution gained fallbacks.** `ArsCrossProxySpell.onCast` used to give up when
   `MagicData.getPlayerCastingItem()` did not carry the entry. It now tries, in order:
   `getPlayerCastingItem()` → `Utils.getPlayerSpellbookStack(player)` → main hand → offhand.
   **This is the actual "casts but does nothing" fix.**
3. **Binding rolls back on native-write failure.** `IronsBookBindingUtil.appendArsSpellToBook`
   now checks `IronsProxySlotWriter.addProxySlot`'s return value and calls the new
   `CrossCastNbt.removeEntryByProxyPoolId` before returning `FAILED`.
4. **Payload validation before resource spend.** New `IronsBookBindingUtil.isCastableArsPayload`
   (deserialises, requires non-empty recipe and non-null cast method), called by the command and
   the ritual. `CrossCastingHandler` gained a `spell.isValid()` check.
5. **The command grew up.** `/ans bind_scroll_to_irons_book` now reads the whole entry (not just
   `ars_spell`), honours `max_ars_cross_spells_per_irons_spellbook`, forwards
   `custom_name`/`nature`/`icon_symbol`, and switches on `AppendResult` for distinct messages.
6. **Icon lookup checks the equipped spellbook slot** before hands (`MixinAbstractSpellArsIcon`).
7. **Seven new `en_us.json` keys** — all failure diagnostics.

### 2.2 What 3.0.3 did *not* fix

`ArsSpellExportUtil.class` is **byte-identical** between 3.0.2 and 3.0.3
(`4c781e2d6ac0bc4c79829ad53a9f298347fdc5b20bc6ef3b2ffc09767822790b`). The scroll-carrier
constructor is unchanged, so **the inscription-table NPE is live in 3.0.3** — consistent with the
crash report being filed against 3.0.3.

Also still missing in 3.0.3: the command does not gate on
`ALLOW_ARS_SPELLS_IN_IRONS_SPELLBOOKS` (only `SpellbookBindingRitual` does, at
`SpellbookBindingRitual.java:110`).

### 2.3 How much of the brief 3.0.3 already satisfies

| Brief item | 3.0.3 status |
|---|---|
| Phase 2 — return precise results (`ADDED`/`DUPLICATE`/`CAP_REACHED`/…) | Partial: `ADDED`/`DUPLICATE`/`BOOK_FULL`/`FAILED`; no `INVALID_CARRIER`/`NATIVE_WRITE_FAILED`/`DISABLED` |
| Phase 2 — roll back mutation on failure | Done for the native-write path |
| Phase 2 — command honours max cap + name/nature/icon | Done |
| Phase 2 — command honours `allow_ars_spells_in_irons_spellbooks` | **Not done** |
| Phase 1.4 — tracing at every early return in `onCast` | Partial: two WARN + player-facing messages; not all six early returns |
| Phase 2 — scroll carrier gets a valid `ISpellContainer` | **Not done** (this is the crash) |
| Phase 2 — uninscription removes native proxy slots | **Not done** (`removeProxySlot` still has zero callers) |

### 2.4 Decision — RESOLVED: 3.0.3 reconstructed into source control

Option 1 below was chosen and is **done**. The 14-class delta was ported into source (preserving
the repository's comment style and mojmap naming rather than pasting decompiler output), the
version was bumped to 3.0.3, and `CHANGELOG.md` / `update.json` were backfilled.

**Verification.** Rebuilding with `reobfJar` and decompiling the result gives a **class-for-class
match** with the published artifact (no extra, no missing) and **identical public signatures** for
every type. Only five files differ at all, all benign:

| File | Difference |
|---|---|
| `SpellLoomExportPacket` | javac pattern-matching temp name (`patt2323$temp` → `patt2383$temp`) — compiler artifact |
| `CrossCastNbt` | declaration order of the private constructor vs `isArsCrossProxyId` |
| `ArsCrossProxySpell` | one log format string parameterises the sidecar key instead of inlining it; same rendered output |
| `CrossCastGameTests` | identical API — 10 test methods, 6 helpers |
| `IronsProxyCastDriver` | one extracted private helper (`requireProxy`) deduplicating a check the published version repeated |

Checksums will not match byte-for-byte (comments, javac build, the differences above); the
published artifact's checksum in §1.1 remains the reference for what users are running.
JUnit stayed green throughout: 194 tests, 53 suites.

The original three options, for the record:

1. **Recommended — reconstruct 3.0.3 as a commit, tag it, then branch the major update off it.**
   Hand-port the 14+3 class delta into source (it is small and fully characterised above),
   verify by rebuilding and comparing the decompiled output against the published jar, commit as
   `Release 3.0.3 (reconstructed from published artifact)`, tag `v3.0.3`, backfill `CHANGELOG.md`
   and `update.json`. Cost: roughly half a day. Benefit: history is honest, the fixes are
   preserved, and Phase 2 starts from what users are actually running.
2. **Locate the real 3.0.3 tree.** If the author has it on another machine or branch, that is
   strictly better than reconstruction — it keeps comments and intent. Worth one question before
   spending effort on option 1.
3. **Not acceptable — build 3.1.0 from 3.0.2 and re-fix from scratch.** Regresses seven language
   keys and six handler guards that are already field-tested, and would ship a version number
   that goes *backwards* in behaviour.

I have not modified any source pending this decision. The recovered artifacts and full
decompilation are in the session scratchpad:
`…/scratchpad/{ans302.jar,ans303.jar,d302/,d303/}`. **These are session-scoped and will be lost**
— if option 1 or 2 is not started promptly, re-download using the checksums in §1.1.

---

## 3. Reproduction steps

### 3.1 Common minimal instance

Minecraft 1.20.1 · Forge 47.4.22 · Ars Nouveau 4.12.7 · Iron's 3.16.2 · Curios · GeckoLib ·
PlayerAnimator · ANS 3.0.3 (SHA-256 in §1.1). Add JEI 15.49.0.187 and EMI 1.1.24 only for §3.4.
Add Ars Elemental 0.6.8.0 / Too Many Glyphs only for §3.5.

> **Not yet executed.** These procedures are derived from source and bytecode analysis, and the
> crash-report stack trace confirms §3.2 end-to-end. No step below has been run in an actual game
> client — this environment has no Minecraft runtime. Each one is written to be executed and
> checked off by a human or by CI, and §7 lists the automated equivalents that *can* run headless.

### 3.2 Client crash — Iron's inscription table (confirmed by stack trace)

1. Give yourself a Spell Loom; export any Ars spell to a scroll (or run
   `/ans` export equivalent). The result is a real `irons_spellbooks:scroll`.
2. Open an Iron's Inscription Table. Place any Iron's spell book in the book slot and the
   exported ANS scroll in the scroll slot.
3. Press **Inscribe**.

**Expected (broken):** immediate client crash.

```
java.lang.NullPointerException: Cannot invoke
  "io.redspace.ironsspellbooks.api.spells.ISpellContainer.getSpellAtIndex(int)"
  because "scrollContainer" is null
  at ...InscriptionTableScreen.onInscription(InscriptionTableScreen.java:414)
  at ...InscriptionTableScreen.lambda$init$0(InscriptionTableScreen.java:71)
```

Note step 2 requires *no* ANS involvement — the crash is in Iron's client screen, triggered by an
item ANS created. Any pre-existing exported scroll in any player's inventory or in a chest is a
live landmine, which is why §5.2 requires both a root fix and a defensive guard.

### 3.3 Bound spell casts but does nothing

1. Export an Ars spell, bind it to an Iron's spell book (`/ans bind_scroll_to_irons_book`, the
   Spellbook Binding ritual, or the Spell Loom).
2. Equip the book **in the Curios spellbook slot** (not the hand). Select the Ars entry in Iron's
   native wheel. Cast.
3. Relog. Repeat.
4. Repeat the whole sequence with the book in main hand, then offhand.

On 3.0.2 the Curios-slot case is the failure: `MagicData.getPlayerCastingItem()` does not return
the equipped book, `onCast` returns silently. On 3.0.3 the fallback chain should make all four
cases work — **verifying this is the first acceptance test**, because it also tells us whether
3.0.3's fix is complete or merely reduces the failure window.

### 3.4 JEI/EMI proxy pollution

1. Boot with JEI. Search `ars_cross`. Expect 8 ghost scroll entries (`ars_cross_1`…`ars_cross_8`).
2. Repeat with EMI installed (EMI consumes JEI's plugin output).

Mechanism: `ArsCrossProxyRegistry` registers 8 real `AbstractSpell`s
(`ArsCrossProxyRegistry.java:42-47`); Iron's JEI plugin enumerates every enabled registered spell
and synthesises a scroll per spell. The repository currently has **no JEI integration at all**
(`grep -rn jei src/main/java` returns one unrelated comment), so there is nothing to filter with.

### 3.5 Addon round-trip (Too Many Glyphs / Ars Elemental)

For each of: a TMG cast method; a filter/control glyph placed before the real effect; a custom
augment; Ars Elemental Water Grave / Discharge / Envenom / Spike / Spark; and a multi-school
glyph —

1. Author the spell on a spell book, export to scroll, bind to an Iron's book.
2. Relog. Cast from the native wheel. Record: does it resolve, what school did ANS assign, what
   scaling multiplier was applied, what cooldown category.
3. Remove the addon; load the world; attempt to cast the now-orphaned entry. Expect a translated
   "missing glyph/addon" error **before** any mana/LP is spent.

### 3.6 Resource-accounting regressions

- **`scroll_cost_mode=full` skips mana with a Cursed Ring on.** Set `scroll_cost_mode=full`,
  `enable_lp_system=true`, Covenant installed, wear the Cursed Ring, use an Iron's scroll with a
  non-zero mana cost. Observe LP charged and mana **not** charged.
- **Virtue Ring gives free Ars casts with the aura system off.** Set
  `enable_virtue_aura_system=false`, wear the Virtue Ring, cast any Ars spell. Observe no mana
  deducted.

### 3.7 Server performance

Dedicated server, Spark (`/spark profiler --timeout 300`) at 1, 10 and 30 simulated players,
stationary and moving, with the ANS subsystems toggled individually. See §8.

---

## 4. Root causes

Each entry states the evidence class: **Confirmed** = proven by stack trace, bytecode, or a test;
**Derived** = read directly off source with an unambiguous mechanism; **Hypothesis** = needs the
measurement in §3 or §8.

### 4.1 Client crash — `ISpellContainer` is null · **Confirmed**

`ArsSpellExportUtil.createIronsScrollCarrier` builds the carrier with a bare
`new ItemStack(item)` ([`ArsSpellExportUtil.java:87`](src/main/java/com/otectus/arsnspells/spell/ArsSpellExportUtil.java#L87))
and then writes **only** ANS sidecar NBT. It never initialises Iron's native container.

Decompiled from `irons_spellbooks-1.20.1-3.16.2.jar`:

```java
public static ISpellContainer get(ItemStack itemStack) {
    return CodecHelper.getOrElseWithLegacy(
        itemStack, NBT, SpellContainer.CODEC, null,        // ← default is null
        LEGACY_NBT, SpellContainer.LEGACY_CODEC);
}
```

and in `InscriptionTableScreen.onInscription`:

```java
ISpellContainer scrollContainer = ISpellContainer.get(menu.getScrollSlot().getItem());
SpellData scrollSlot = scrollContainer.getSpellAtIndex(0);   // ← unguarded
```

The only guards upstream are `item instanceof SpellBook`, `item instanceof Scroll`, and
`!spellSlots.isEmpty()`. An ANS carrier passes `instanceof Scroll` and fails the null check that
does not exist. **Identical code in 3.15.0** — see §6.

**The crash report shows only half the problem.** Decompiling `InscriptionTableMenu` from 3.16.2
turns up two *more* unguarded dereferences of the scroll's container, both on the **server**:

```java
// InscriptionTableMenu.m_6366_ (clickMenuButton) — server thread
SpellData spellData = ISpellContainer.get(scrollStack).getSpellAtIndex(0);   // ← NPE

// InscriptionTableMenu.doInscription — server thread
ISpellContainer scrollContainer = ISpellContainer.get(scrollItemStack);
SpellData scrollSlot = scrollContainer.getSpellAtIndex(0);                   // ← NPE
```

So the same malformed carrier crashes the **server thread** on a dedicated server, not just the
client. The reporter hit the client path first only because they were in single-player, where the
client screen dereferences before the packet round-trips. This raises the severity from "client
crash" to "remote server crash triggerable by any player holding a legacy ANS scroll", and it is
why the fix must be enforced on both sides.

### 4.2 Bound spell casts but does nothing · **Confirmed** (fixed in 3.0.3, unfixed in this tree)

Two independent defects:

- **Non-atomic binding.** `IronsBookBindingUtil.appendArsSpellToBook`
  ([`IronsBookBindingUtil.java:206-214`](src/main/java/com/otectus/arsnspells/spell/IronsBookBindingUtil.java#L206-L214))
  writes the sidecar entry, calls `IronsProxySlotWriter.addProxySlot(...)` **discarding its
  boolean result**, and unconditionally returns `ADDED`. When the native write fails the book has
  a sidecar entry with no wheel slot — invisible and uncastable, reported as success.
- **Casting-item resolution.** `ArsCrossProxySpell.onCast` reads only
  `playerMagicData.getPlayerCastingItem()`
  ([`ArsCrossProxySpell.java:97-104`](src/main/java/com/otectus/arsnspells/spell/irons/ArsCrossProxySpell.java#L97-L104))
  and returns silently on any miss — a wheel slot that selects and casts nothing.

Both are fixed in the published 3.0.3 (§2.1). The inverse defect is **not** fixed anywhere:
`SpellUninscriptionRitual.java:94` clears the sidecar via `CrossCastNbt.clearCrossModSpells` but
never removes native proxy slots. `IronsProxySlotWriter.removeProxySlot` has **zero callers in
the entire codebase** — verified by grep. Uninscribing therefore leaves an orphan wheel entry
that selects and does nothing, which is the same user-visible symptom arrived at from the other
direction.

### 4.3 JEI ghost items · **Derived**

8 real registered spells × Iron's JEI plugin's "generate a scroll per enabled spell" loop. No ANS
JEI plugin exists to filter them. Disabling the spells globally is not an option — bound books
resolve them by id at cast time.

### 4.4 Addon compatibility is unproven · **Derived**

Three separate problems, and they disagree with each other:

1. **Classification ignores addon metadata.** `SpellAnalysis.deriveSchool`
   ([`SpellAnalysis.java:159-215`](src/main/java/com/otectus/arsnspells/util/SpellAnalysis.java#L159-L215))
   consults a 20-entry hardcoded `ars_nouveau:`-only map, then falls back to substring matching on
   the registry path. Ars Nouveau's `AbstractSpellPart.spellSchools` — which Ars Elemental
   explicitly populates — is never read.
2. **Scaling ignores the classification it just computed.** `SpellScalingUtil.getMultiplierForCaster`
   ([`SpellScalingUtil.java:55-69`](src/main/java/com/otectus/arsnspells/util/SpellScalingUtil.java#L55-L69))
   calls `SpellAnalysis.analyze(spell)`, takes `dominantSchool()` — then throws it away for the
   elemental attribute lookup and re-derives the element with a *different* heuristic
   (`path.contains(key)` over a `HashMap`). Consequences: `glyph_firework` classifies as
   `generic` for affinity but still matches `"fire"` for scaling; the `aqua`, `geo` and `wind`
   schools that `deriveSchool` can return have no `ELEMENT_MAP` entry and silently get no
   elemental bonus at all; and because the map is a `HashMap`, a path containing two element keys
   picks a winner by **iteration order**.
3. **Only the first effect counts.** `analyzeRecipe` takes `firstEffect` and ignores the rest, so
   multi-school and filter-glyph-prefixed recipes are classified by whichever effect happens to
   come first.

### 4.5 `scroll_cost_mode=full` skips mana · **Confirmed by reading**

[`MixinScrollItem.java:126`](src/main/java/com/otectus/arsnspells/mixin/irons/MixinScrollItem.java#L126)
— the Cursed Ring branch ends in `return`, before the mana block at lines 134-150. Every exit
from the LP path (`return` at 108, 120, 126) skips mana staging. Compounding it, `ScrollLPTracker`
holds **one** entry per player and the commit at line 185 checks `pending.manaCost > 0` and
returns — so the data model cannot represent "LP and mana" even if both were staged.

### 4.6 Virtue Ring gives free casts · **Confirmed by reading**

[`MixinSpellResolverMana.java:39`](src/main/java/com/otectus/arsnspells/mixin/ars/MixinSpellResolverMana.java#L39)
cancels `expendMana` whenever `isWearingVirtueRing(player)` is true. The adjacent Cursed Ring
branch (line 35) correctly gates on `ENABLE_LP_SYSTEM`; the Virtue branch has no
`ENABLE_VIRTUE_AURA_SYSTEM` check. With the aura handler off nothing consumes aura and nothing
consumes mana.

### 4.7 FIFO delayed-cast accounting · **Derived**

`CursedRingHandler`, `VirtueRingHandler` and `IronsLPHandler` all keep a per-player `Deque` of
pending costs matched in FIFO order. Ars projectiles resolve when they hit, not when they are
cast, so two casts in flight can resolve out of order and pay each other's cost. Needs a
transaction id threaded through `CrossCastContext`.

### 4.8 Wall-clock time in gameplay transactions · **Confirmed by reading**

`IronsLPHandler` (lines 121, 146, 190, 267) and `LPDeathPrevention` (lines 48, 192) expire
gameplay transactions on `System.currentTimeMillis()`. `CursedRingHandler` and `VirtueRingHandler`
were already migrated to `level().getGameTime()`. Wall-clock expiry misbehaves whenever server
tick rate diverges from real time — lag spikes, `/tick freeze`, single-player pause.

### 4.9 Server-tick hot spots · **Derived; magnitudes are hypotheses pending §8**

| Site | Mechanism |
|---|---|
| `CursedRingHandler.java:349`, `VirtueRingHandler.java:287`, `IronsLPHandler.java:268`, `LPDeathPrevention.java:193` | Each runs `removeIf` over the **entire global map** inside a *per-player* tick. P players × global map of size P = **O(P²)** work per sweep window. |
| Same four sites | Gated on `event.player.tickCount % 100`. `tickCount` is per-player, but every player who joined in the same tick (server restart, post-crash reconnect) shares a phase and sweeps on the same tick. |
| `RegenSynergyHandler.java:90` | Scans a block volume per moving player, cached by a move threshold. No per-level/chunk index. |
| `ResonanceEvents.java:26-32` | Every player, every 40 ticks, unconditionally recomputes **and sends a packet** — even when the value is unchanged. |
| `EquipmentIntegration.java:145,486` | TTL-only cache (`CACHE_DURATION_MS`) over armour + curios + attributes + enchantments. Rescans on expiry regardless of whether equipment changed, and never invalidates when it does. |
| `MixinIronsCastValidation.java:117-128` | Rate-limit map swept inline on the hot path. |

### 4.10 Incomplete features · **Confirmed**

`ManaInfusionRitual` and `ManaWellRitual` are registered but documented as unobtainable
([`README.md:321`](README.md#L321)). `ManaWellRitual.java:21-24` queries an inflated AABB every
tick. `EquipmentIntegration.calculateCurioDiscounts` (line ~500) is a stub returning
`CurioDiscountData.NONE` with a comment saying the real work happens elsewhere.

---

## 5. Planned code ownership and migration strategy

### 5.1 Ownership boundaries

The existing Iron's-isolation discipline is sound and should be preserved: no top-level Iron's
imports outside a small set of gated classes, everything else keyed by `ResourceLocation`. The new
work adds one owner and tightens two existing ones.

| Concern | Owner (new or changed) | Iron's classes? |
|---|---|---|
| **All** carrier/book mutation | `spell.binding.BindingService` *(new)* | No — delegates |
| Native container + proxy slot I/O | `spell.irons.IronsProxySlotWriter` *(extended)* | Yes, gated |
| Scroll carrier construction | `spell.irons.IronsScrollFactory` *(new)* | Yes, gated |
| Inscription-table safety | `mixin.irons.MixinInscriptionTableScreen` *(new, client-only)* | Yes, gated |
| Legacy repair | `spell.binding.CarrierReconciler` *(new)* | No — delegates |
| School resolution | `util.SchoolResolver` *(new)* — sole authority | No |
| JEI filtering | `compat.jei.*` *(new, client-only)* | No |

`BindingService` becomes the single entry point for the Spell Loom, `/ans bind_scroll_to_irons_book`,
`SpellbookBindingRitual`, native-table integration, migration, and uninscription. Its invariant:

> Every ANS proxy slot has exactly one matching sidecar entry, every bound sidecar entry has
> exactly one native proxy slot, and every real Iron's scroll has a valid native spell container.

Result type extends 3.0.3's enum to the brief's full set: `ADDED`, `DUPLICATE`, `CAP_REACHED`
(renaming `BOOK_FULL`), `INVALID_CARRIER`, `NATIVE_WRITE_FAILED`, `DISABLED`.

`SchoolResolver` must be consumed by affinity, progression, scaling, cooldowns, LP cost and UI —
replacing both heuristics in §4.4. Resolution order: `AbstractSpellPart.spellSchools` →
data-driven mapping (`data/ars_n_spells/glyph_schools/*.json`, datapack-extensible, replacing the
hardcoded Java map) → substring heuristic as last resort. Multi-school and filter-glyph behaviour
must be *defined and tested*, not incidental.

### 5.2 The crash fix is two changes, not one

1. **Root:** `createIronsScrollCarrier` initialises a valid native container through Iron's API in
   an Iron's-gated helper, and returns `ItemStack.EMPTY` rather than an invalid real `Scroll` if
   that fails. Iron's own `createScrollContainer(spell, level, stack)` needs an Iron's
   `AbstractSpell` and ANS carriers have no pool id until bind time, so the shape is
   `ISpellContainer.set(stack, ISpellContainer.create(1, false, false))` — an empty 1-slot
   container. **Verified safe:** `SpellContainer.getSpellAtIndex(0)` returns `SpellData.EMPTY`
   (not null) for an unfilled slot, so this clears all three NPE sites.
2. **Defensive:** a mixin that rejects a container-less scroll in the inscription flow. It must
   cover `InscriptionTableScreen.onInscription` (client) **and** `InscriptionTableMenu.m_6366_` /
   `doInscription` (server) — per §4.1 the server sites NPE independently, so a client-only guard
   would leave dedicated servers exposed. This is what protects the scrolls already sitting in
   players' inventories, which the root fix cannot reach.

**Status: both landed and verified.** `IronsScrollFactory` establishes the container before the
carrier is returned (and returns `ItemStack.EMPTY` if it cannot), `IronsInscriptionPolicy` holds
the shared verdict, and `MixinInscriptionTableScreen` / `MixinInscriptionTableMenu` enforce it on
client and server. Four GameTests cover it, including one that performs Iron's exact dereference
(`ISpellContainer.get(s).getSpellAtIndex(0)`) rather than a proxy predicate, and one that asserts
its own setup still reproduces the pre-fix shape so the rejection test cannot pass vacuously.

**The root fix alone is not sufficient, and this decides the native-table policy.** With an empty
container in place the NPEs are gone, but `doInscription` then runs:

```java
SpellData scrollSlot = scrollContainer.getSpellAtIndex(0);           // SpellData.EMPTY
if (mutableBookContainer.addSpellAtIndex(scrollSlot.getSpell(), ...)) {   // → SpellRegistry.none()
    this.getScrollSlot().m_6201_(1);                                 // ← consumes the scroll
```

`SpellData.EMPTY.getSpell()` returns `SpellRegistry.none()`, a real spell object, so the native
table would happily consume an ANS carrier and write a "none" entry into the player's book. That
makes the reject-or-route decision mandatory rather than cosmetic. **Recommendation: reject**
both client- and server-side with a translated message pointing at the Spell Loom workflow, and
treat "route the native table through `BindingService`" as a follow-up once the invariant holds —
rejecting is a few lines and closes the corruption path immediately, whereas routing means
reimplementing Iron's slot selection semantics against a sidecar model.

### 5.3 Migration

- **Item-data schema version.** Add `arsnspells:schema_version` to ANS-owned root NBT. Absent ⇒
  version 0 (everything shipped to date).
- **Lazy reconciliation, never a global sweep.** Repair on the events that already touch the
  stack: inventory tick for held/equipped items only, container open, and bind/cast/uninscribe
  entry points. Repairs: missing native container on a carrier; sidecar entry with no proxy slot;
  proxy slot with no sidecar entry (orphan removal); schema stamp.
- **Uninscription ordering.** Remove native proxy slots *first*, then clear sidecar data, then
  remove ANS-owned export markers (`arsnspells:export_mode`). Must not touch unrelated custom
  names or third-party NBT — `ArsIronsExportGameTests` already asserts this and must stay green.
- **Spellbook detection.** Replace the `path.contains("spell_book") || path.contains("spellbook")`
  substring test at
  [`IronsBookBindingUtil.java:59`](src/main/java/com/otectus/arsnspells/spell/IronsBookBindingUtil.java#L59)
  with an Iron's type check or an item tag (`ars_n_spells:irons_spellbooks`), matching the
  tag-driven approach 3.0.2 already adopted for rings and source jars.

### 5.4 Sequencing

Phase 2 (binding architecture) and Phase 3 (resource accounting) are independent and can proceed
in parallel. Phase 4 (JEI) is self-contained and client-only. Phase 5 (addons) depends on
`SchoolResolver`, which is a Phase 5 deliverable but should land before Phase 6 profiling so the
profile reflects final code. Phase 6 needs a baseline captured *before* any Phase 2–5 change so
the before/after comparison is meaningful — **capture it first** (§8).

---

## 6. Iron's version compatibility evidence

The brief asks whether both 3.15.0 and 3.16.2 can be supported, and to narrow the advertised range
if not. Measured by extracting the `io.redspace.ironsspellbooks.api` package from both jars and
diffing `javap` signatures.

**Whole API package (123 → 127 classes):** 2 removed signatures, 74 added.
- `toJson(Gson)` → `<T> toJson(Gson)` — generics only, erasure-identical, not a binary break.
- `SpellConfigParameter.defaultValue()`: `T` → `Supplier<T>` — a real binary break, on a type ANS
  does not reference.

**The 14 Iron's types ANS actually imports:** the diff is **five added `SpellRegistry` constants**
(`ARCANE_SHACKLE_SPELL`, `GRAVITY_FISSURE`, `FANG_SWIRL_SPELL`, `SCAPEGOAT_SPELL`,
`BLIZZARD_SPELL`) and **nothing else**. Zero removals, zero signature changes.

**Conclusion:** the advertised range `[1.20.1-3.15.0, 1.20.1-4.0.0)` does not need narrowing for
3.16.2, and the compile pin at 3.15.0 is not the cause of any reported defect. `ISpellContainer.get`
returns null on a container-less stack in **both** versions — the crash reproduces on the pinned
version too. Recommendation: keep the range, move the compile pin to 3.16.2 so the build compiles
against what most users run, and add the 3.15.0 GameTest profile required by the acceptance
criteria to catch any future drift.

---

## 7. Baseline correctness measurements

### 7.1 JUnit — measured, green

```
./gradlew test --offline    →  exit 0
suites=53  tests=194  failures=0  errors=0  skipped=0
```

Matches the brief's stated baseline of 194 tests across 53 suites exactly. This is the regression
floor: it must stay green through every phase.

### 7.1b GameTest baseline — measured, both profiles green

Contrary to the assumption in §7.3, the Iron's-loaded GameTest profile turns out to verify far
more than expected without a game client. Both profiles now run clean:

| Profile | Command | Result |
|---|---|---|
| Iron's-absent (default) | `./gradlew runGameTestServer` | `All 21 required tests passed` |
| Iron's-loaded | `./gradlew runGameTestServer -PwithIronsRuntimeGameTests` | `All 21 required tests passed` |

**The Iron's-loaded run genuinely executes the cross-cast machinery** — proven by this line, which
only the real `ArsCrossProxySpell.onCast` path can emit, from a fake player with a book in the
actual Curios spellbook slot:

```
[Server thread/WARN] [c.o.a.spell.irons.ArsCrossProxySpell]: Ars cross proxy
ars_n_spells:ars_cross_1 cast by ans_gametest (source=SPELLBOOK, equipmentSlot=spellbook)
but no carried spellbook holds a sidecar entry for pool 1 …
```

That matters for interpreting the counts: on the Iron's-absent run every Iron's-gated test
self-skips via `helper.succeed()`, so 21/21 there proves boot safety, not behaviour. Only the
Iron's-loaded number is evidence about the cross-cast paths.

> **CI hazard found while doing this.** The two profiles share `run/`, and the Iron's-loaded run
> leaves `irons_spellbooks:pocket_dimension_type` in `run/world`. The next Iron's-absent run then
> dies at world load with `Failed to get element ResourceKey[…pocket_dimension_type]` — and
> **Gradle still reports `BUILD SUCCESSFUL`**. This is precisely why the CI job asserts on the
> "All N required tests passed" log line rather than the exit code (3.0.2, audit E7). Any job
> running both profiles must `rm -rf run/world` between them.

### 7.2 Coverage gaps this baseline hides

The 194 tests are `src/test` JUnit and cannot execute Minecraft runtime code. Only two GameTest
classes exist (`CrossCastGameTests`, `ArsIronsExportGameTests`), and by default `runGameTestServer`
runs them **without Iron's on the classpath** — the Iron's-loaded scenarios self-skip on
`IronsCompat.isLoaded() == false` unless `-PwithIronsRuntimeGameTests` is passed. So the entire
cross-cast integration surface is unverified in the default CI path.

The brief's requirement to stop using source-text substring assertions as proof of runtime
correctness applies here. New behavioural GameTests needed, at minimum:

- Exported scroll has a valid `ISpellContainer`; a legacy container-less carrier does not crash
  the inscription path.
- Bind → relog → cast → deterministic observable Ars effect, for main hand / offhand / Curios
  spellbook slot.
- Native Iron's spells on the same book unchanged after bind and after unbind.
- Bind failure consumes nothing and leaves no half-written NBT.
- Unbind removes both sidecar and native proxy; no selectable no-op remains.
- `scroll_cost_mode=full` charges each configured currency exactly once.
- `enable_virtue_aura_system=false` restores normal mana costs.
- Addon spells (TMG, Ars Elemental) serialize → bind → relog → deserialize → cast.

### 7.3 Performance baseline — not captured

**No profile has been taken.** This environment has no dedicated server, no Spark, and no way to
simulate players. Every magnitude in §4.9 is a mechanism read off source, not a measurement. The
harness in §8 must run before any Phase 6 optimisation, or the "material reduction" acceptance
criterion has no denominator.

---

## 8. Performance measurement plan (Phase 6 prerequisite)

**Harness.** Dedicated server, Spark (`/spark profiler --timeout 300 --thread "Server thread"`),
JFR as cross-check. Populations of 1, 10, 30 simulated players; stationary and moving (moving
matters specifically for `RegenSynergyHandler`). Fixed seed, fixed world, fixed tick budget.

**Matrix.** Default config; then each subsystem isolated — Cursed Ring, Virtue Ring, Iron's LP,
death prevention, resonance, source-jar synergy, equipment/curio bonuses.

**Record per run:** mean and p99 ANS-owned tick time; ANS share of server thread; packet count and
bytes for `ResonanceSyncPacket`; source-jar scan count, skip count and slow-scan warnings (the
counters at `RegenSynergyHandler.java:97,178` already exist); allocation rate.

**Targets (from the brief).** Linear scaling in player count; no synchronised ANS spike above 1 ms;
material reduction in ANS-owned tick CPU. Before/after profiles required for sign-off.

**Planned fixes**, to be confirmed or dropped by the profile:

| Fix | Addresses |
|---|---|
| One server-global cleanup tick, or per-player lazy expiry on access | §4.9 O(P²) sweeps |
| Stagger periodic work by `UUID.hashCode() % interval` | §4.9 synchronised phase |
| Per-level/chunk source-jar index from chunk + block lifecycle events | §4.9 volume scan |
| Recompute/sync resonance only on material change (epsilon compare) | §4.9 unconditional packets |
| Invalidate equipment cache from equipment + Curios change events — the Curios compile dependency now exists (`build.gradle:83`), so the stale "event unavailable" comment is wrong | §4.9 TTL rescan |
| Ring-bypass INFO → debug or rate-limited aggregate | §4.9 hot-path logging |
| Mana Well: lower cadence + scale regen by elapsed ticks — **only if §9 keeps the feature** | §4.10 |

---

## 9. Cohesion decisions to make (Phase 7)

| Item | Recommendation |
|---|---|
| Mana Infusion / Mana Well | **Decide before optimising.** Removing registration and config surface is cheaper than adding tablets, recipes, tests and an optimised tick path for a feature no player can obtain. If kept, they need the full treatment. |
| `calculateCurioDiscounts` stub | Remove, or implement and test. Currently dead code returning `NONE`. |
| Unused armour heuristics | Remove unless deliberately promoted with tests. |
| `ISB_Spells`-only comments and tests | Update to assert against `irons_spellbooks:spell_container`; `ISB_Spells` is the *legacy* key, still read by `CodecHelper.getOrElseWithLegacy` but no longer what Iron's writes. |
| Version coherence | `gradle.properties`, git tag, `CHANGELOG.md`, `update.json` and the published jar must agree — **currently they do not** (§2). Fixing this is part of the §2.4 decision. |

---

## 10. Phase 1 checklist

| Requirement | Status |
|---|---|
| Exact artifact versions and checksums | Done — §1 |
| Reproduction steps for every report | Written — §3. **Not executed**; no game runtime available |
| Root cause or current hypothesis | Done — §4, each labelled Confirmed / Derived / Hypothesis |
| Planned code ownership and migration strategy | Done — §5 |
| Baseline correctness measurements | Done — §7.1, 194/53 green |
| Baseline performance measurements | **Not done** — §7.3; harness specified in §8 |
| Resolve 3.0.3 provenance discrepancy | Done — §2. **Blocking decision at §2.4** |
| Reproduce crash in minimal instance | Root cause proven from bytecode + stack trace; in-game repro pending |
| Capture NBT before/after export, bind, relog, cast, unbind | Pending — requires the running instance |
| Tracing at every early return in `onCast` | Partially shipped in 3.0.3 (§2.3); completion is Phase 2 work |
