# Ars 'n Spells — Exhaustive Systems Audit (v2.0.0)

**Target**: Forge 1.20.1 mod at `C:\Users\crims\Documents\GitHub\ars-n-spells`, branch `main`, head `38c8ee9` (Release 2.0.0).
**Scope lens**: Iron's Spellbooks **loaded** (modpack-realistic), Sanctified Legacy **loaded** (Cursed/Virtue Rings active), Curios **loaded**. Dedicated server primary.
**Methodology**: 6 parallel sub-agent pipelines (cross-cast spine / capabilities+networking / spells+rituals+integrations / events+commands+util+gametest / client+HUD+Dist / build+metadata+config) + lead spot-verification of every CRIT/HIGH against disk + cross-pipeline deduplication. 232 raw findings reduced to ~150 ranked after merge.

## Executive summary

The 2.0.0 hardening pass closed the loud bugs but introduced new concurrency and integration surface that the audit exposes:

1. **One ship-blocker crash on Iron's-less servers**: the mixin plugin gating omits `MixinIronsCastValidation` and `MagicDataAccessor`. Iron's is declared `mandatory=false`; any pack that runs without it boots into `NoClassDefFoundError` at mixin apply. (`ANS-CRIT-001`)
2. **Two duplication / drain exploits**: SEPARATE-mode dual-cost has a one-way Ars drain when the Iron's side fails (`ANS-CRIT-002`) and a rollback that overwrites concurrent regen (`ANS-CRIT-003`).
3. **One economy bypass**: cross-cast cost multiplier silently becomes zero when wearing a Cursed/Virtue Ring (`ANS-CRIT-004`). The 2.0.0 release notes specifically highlight rings + cross-cast as the headline integration — this is the most-played path.
4. **Recipe break on Iron's-less servers**: `spell_transcription.json` hard-codes `irons_spellbooks:spell_book` with no `forge:conditional` (`ANS-HIGH-001`).
5. **Network attack surface**: 4 of 5 packets are registered without a `NetworkDirection` guard; payload validation is missing on 3 server→client packets (`ANS-HIGH-005`, `ANS-HIGH-006`, `ANS-HIGH-007`, `ANS-HIGH-013`).
6. **Concurrency hazards**: 5 distinct ThreadLocal / map / event-priority races, all introduced or unchanged by the 2.0.0 overhaul.

The audit identified one confirmed false positive from pre-survey (`ringConflictNotified` "leak") and demoted three sub-agent CRITs to HIGH after lead spot-verification.

| Severity | Count |
|---|---:|
| CRITICAL | **4** |
| HIGH | **27** |
| MEDIUM | **41** |
| OPTIMIZATION | **18** |
| LOW / CLEANUP | **35** |
| FALSE POSITIVE / NEEDS VERIFICATION | **22** |
| **Total ranked findings** | **147** |

## Coverage proof

| Pipeline | Files audited | Findings emitted |
|---|---:|---:|
| A — Cross-cast spine (bridge + 13 mixins) | 24 | 35 |
| B — Capabilities & networking | 27 | 40 |
| C — Spells / rituals / integrations | 23 | 38 |
| D — Events / commands / util / gametest | 28 | 60 |
| E — Client / HUD / Dist | 7 | 17 |
| F — Build / metadata / config | 11 | 42 |
| **Total** | **120** (Java + 7 resource/build) | **232 raw → 147 deduped** |

Every Java source file in `src/main/java/com/otectus/arsnspells/` was read in full by at least one pipeline. `MixinIronsManaBarOverlay.java` was read by both A and E (concurrency vs. render-thread lenses); the two views were merged into one finding.

---

# CRITICAL

## ANS-CRIT-001 — Mixin plugin omits `MixinIronsCastValidation` and `MagicDataAccessor` from Iron's gating → mod load crash on Iron's-less servers

**Vector**: V3 (optional-mod compat) / V2 (dedicated-server correctness)
**File**: [src/main/java/com/otectus/arsnspells/mixin/ArsNSpellsMixinPlugin.java:29-38](src/main/java/com/otectus/arsnspells/mixin/ArsNSpellsMixinPlugin.java)
**Location**: `shouldApplyMixin` method

**Failure state**: Iron's Spells 'n Spellbooks is declared `mandatory=false` in [mods.toml:39](src/main/resources/META-INF/mods.toml). The mixin plugin gates 5 Iron's mixins on `ironsPresent`, but **omits** `MixinIronsCastValidation` (mixes into `io.redspace.ironsspellbooks.api.spells.AbstractSpell`) and `MagicDataAccessor` (mixes into `io.redspace.ironsspellbooks.api.magic.MagicData`). For any other mixin name, `shouldApplyMixin` returns `true`. On an Iron's-less server, Mixin tries to apply these two and fails with `NoClassDefFoundError` resolving the target class → FML aborts mod load.

**Trigger**: Boot a Forge 1.20.1 server with Ars Nouveau + Ars 'n Spells but no `irons_spellbooks` jar.

**Evidence**:
```java
public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    if (mixinClassName.endsWith("MixinIronsSpellDamage")
        || mixinClassName.endsWith("MixinIronsManaBarOverlay")
        || mixinClassName.endsWith("MixinIronsMagicDataMana")
        || mixinClassName.endsWith("MixinScrollItem")
        || mixinClassName.endsWith("MixinSanctifiedAbstractSpell")) {
        return ironsPresent;
    }
    return true;  // ← MixinIronsCastValidation + MagicDataAccessor fall through
}
```
`MixinIronsCastValidation.java:41-42` and `MagicDataAccessor.java:15` both `@Mixin(value = ...class)` targets in `io.redspace.ironsspellbooks.api.*`.

**Fix strategy**: Add both names to the gated list. While here, also extract the Iron's-class probe into the canonical `Class.forName(..., false, classLoader)` pattern already used by [ArsNSpells.canLoad](src/main/java/com/otectus/arsnspells/ArsNSpells.java:209-216) — `ClassLoader.getResource(".class")` is unreliable inside the mixin bootstrap on some launcher configurations.

**Patch**:
```diff
--- a/src/main/java/com/otectus/arsnspells/mixin/ArsNSpellsMixinPlugin.java
+++ b/src/main/java/com/otectus/arsnspells/mixin/ArsNSpellsMixinPlugin.java
@@ -16,8 +16,9 @@ public class ArsNSpellsMixinPlugin implements IMixinConfigPlugin {

     @Override
     public void onLoad(String mixinPackage) {
-        ironsPresent = resourceExists("io/redspace/ironsspellbooks/api/spells/AbstractSpell.class")
-            && resourceExists("io/redspace/ironsspellbooks/api/magic/MagicData.class");
+        ironsPresent =
+            canLoadClass("io.redspace.ironsspellbooks.api.spells.AbstractSpell")
+            && canLoadClass("io.redspace.ironsspellbooks.api.magic.MagicData");
     }

     @Override
     public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
         if (mixinClassName.endsWith("MixinIronsSpellDamage")
             || mixinClassName.endsWith("MixinIronsManaBarOverlay")
             || mixinClassName.endsWith("MixinIronsMagicDataMana")
+            || mixinClassName.endsWith("MixinIronsCastValidation")
+            || mixinClassName.endsWith("MagicDataAccessor")
             || mixinClassName.endsWith("MixinScrollItem")
             || mixinClassName.endsWith("MixinSanctifiedAbstractSpell")) {
             return ironsPresent;
         }
         return true;
     }
@@ -58,8 +60,12 @@ public class ArsNSpellsMixinPlugin implements IMixinConfigPlugin {
     }

-    private static boolean resourceExists(String resourcePath) {
-        return ArsNSpellsMixinPlugin.class.getClassLoader().getResource(resourcePath) != null;
+    private static boolean canLoadClass(String fqn) {
+        try {
+            Class.forName(fqn, false, ArsNSpellsMixinPlugin.class.getClassLoader());
+            return true;
+        } catch (Throwable t) {
+            return false;
+        }
     }
 }
```

**Regression test**: `gameTestServer` run target with `run/mods/` containing only `ars_nouveau` + `ars_n_spells`. Boot must reach world load. Grep `latest.log` for `NoClassDefFoundError` — must be absent. Repeat with Iron's jar restored; `[SelfCheck]` line must read `MagicDataAccessor=OK`.

**Cross-references checked**: [ArsNSpells.java:60,81-96,157](src/main/java/com/otectus/arsnspells/ArsNSpells.java) (Iron's-gated registration confirms the intent); [mods.toml:39](src/main/resources/META-INF/mods.toml) (`mandatory=false`).

---

## ANS-CRIT-002 — `MixinSpellResolverMana` TAIL swallows secondary-consume failure → one-way Ars mana drain in SEPARATE mode

**Vector**: V5 (game-state correctness) / V1 (concurrency)
**File**: [src/main/java/com/otectus/arsnspells/mixin/ars/MixinSpellResolverMana.java:85-117](src/main/java/com/otectus/arsnspells/mixin/ars/MixinSpellResolverMana.java)
**Location**: `arsnspells$consumeCrossCastSecondary`

**Failure state**: In SEPARATE mode, when an Ars cross-cast reaches the TAIL of `expendMana`, the upstream method body has already drained the Ars side. The TAIL handler then attempts the symmetric Iron's-side consume via `issBridge.consumeMana(player, entry.issCost)`. If the Iron's pool is insufficient at this moment (regen race, concurrent cast, or potion expiry between cost-calc and TAIL), the consume returns `false` and **the failure is silently dropped — comment literally reads "No cancel path here; log in debug to avoid spam"**. Ars mana is lost, the spell still resolves. Net result: in SEPARATE mode, a player can chain cross-casts that drain only Ars mana while Iron's remains at zero.

**Trigger**: SEPARATE mode + cross-cast inscribed item + Iron's pool below `entry.issCost` at TAIL. Reproducible by: drink an Iron's mana drain potion, then cross-cast an Ars spell from an inscribed item.

**Evidence**:
```java
// MixinSpellResolverMana.java:113-116
boolean consumed = issBridge.consumeMana(player, entry.issCost);
if (!consumed) {
    // No cancel path here; log in debug to avoid spam
}
```

**Fix strategy**: This is the symmetric mirror of ANS-CRIT-003 (BridgeManager rollback). Move the Iron's-side consume earlier: pre-consume at `onArsSpellCost` after the sufficiency check at [CrossCastingHandler.java:349-359](src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java), and have the TAIL skip work for entries already paid. This makes the dual-cost atomic with the cost-calc that the Ars side will deduct from.

**Patch**:
```diff
--- a/src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java
+++ b/src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java
@@ -346,16 +346,24 @@ public class CrossCastingHandler {
             entry.costsReady = true;
             entry.multiplierApplied = true;

             if (!player.isCreative() && issCost > 0.0f) {
                 IManaBridge issBridge = BridgeManager.getSecondaryBridge();
                 float issMana = issBridge != null ? issBridge.getMana(player) : 0.0f;
                 if (issMana < issCost) {
                     entry.blocked = true;
                     event.currentCost = Integer.MAX_VALUE;
                     CrossCastContext.clear(player);
                     logDebug("Insufficient Iron mana for cross-cast: need {}, have {}", issCost, issMana);
                     return;
                 }
+                // Pre-consume Iron's side atomically with the Ars cost-calc. The TAIL
+                // handler must NOT re-consume — mark issCost zero so it skips.
+                if (!issBridge.consumeMana(player, issCost)) {
+                    entry.blocked = true;
+                    event.currentCost = Integer.MAX_VALUE;
+                    CrossCastContext.clear(player);
+                    return;
+                }
+                entry.issCost = 0.0f;
             }
```
And drop the silent-swallow at [MixinSpellResolverMana.java:113-116] entirely (it becomes dead code once `issCost == 0`).

**Regression test**: Manual on dedicated server, SEPARATE mode, default config. Set Ars mana = 100, Iron's mana = 5. Cross-cast inscribed Ars spell whose ISS share = 10. Pre-fix: Ars drops to 50, Iron's stays at 5, spell visible. Post-fix: cost-calc denies; both pools unchanged; player sees the denial component.

**Cross-references checked**: [CrossCastingHandler.java:311-382](src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java), [BridgeManager.java:244-256](src/main/java/com/otectus/arsnspells/bridge/BridgeManager.java) (the mirror bug).

---

## ANS-CRIT-003 — `BridgeManager.consumeManaForMode` dual-cost rollback overwrites concurrent regen / buffs

**Vector**: V5 (game-state correctness) / V1 (concurrency)
**File**: [src/main/java/com/otectus/arsnspells/bridge/BridgeManager.java:244-256](src/main/java/com/otectus/arsnspells/bridge/BridgeManager.java)
**Location**: `consumeManaForMode` SEPARATE branch

**Failure state**: The rollback pattern is snapshot-and-restore: `arsManaBefore = arsBridge.getMana(player); arsBridge.consumeMana(player, arsCost); ... if (!issSuccess) arsBridge.setMana(player, arsManaBefore);`. Any concurrent mutation of Ars mana between the snapshot and the rollback is silently overwritten: a regen tick from `PlayerTickEvent.END`, a [ManaInfusionRitual](src/main/java/com/otectus/arsnspells/rituals/ManaInfusionRitual.java:26) firing on the same tick, a Source Jar synergy boost, or another mod's mana mutation. The comment calls this "Atomic dual-cost" — it is not atomic, it is a snapshot/restore that loses concurrent deltas.

**Trigger**: SEPARATE mode + dual-cost cast whose Iron's half fails + concurrent regen. Most reliable repro: two players cast inscribed items in the same tick window, or one player casts while standing in a [ManaWellRitual](src/main/java/com/otectus/arsnspells/rituals/ManaWellRitual.java) area.

**Evidence**:
```java
// BridgeManager.java:244-256
// Atomic dual-cost: capture state for rollback if second consumption fails
float arsManaBefore = arsBridge.getMana(player);
boolean arsSuccess = arsBridge.consumeMana(player, arsCost);
if (!arsSuccess) {
    return false;
}

boolean issSuccess = issBridge.consumeMana(player, issCost);
if (!issSuccess) {
    // Rollback: restore Ars mana to pre-consumption value
    arsBridge.setMana(player, arsManaBefore);
    return false;
}
```

**Fix strategy**: Use a compensating add (mirror of consume): on failure, `arsBridge.addMana(player, arsCost)`. This adds back exactly what we took without touching any concurrent delta. Requires adding an `addMana` method to [IManaBridge](src/main/java/com/otectus/arsnspells/bridge/IManaBridge.java) (default implementation calls `setMana(getMana + amount)` for backward compat; override in `IronsBridge` and `ArsNativeBridge` for true atomicity).

**Patch**:
```diff
--- a/src/main/java/com/otectus/arsnspells/bridge/IManaBridge.java
+++ b/src/main/java/com/otectus/arsnspells/bridge/IManaBridge.java
@@ -5,5 +5,10 @@ public interface IManaBridge {
     float getMana(Player player);
     void setMana(Player player, float amount);
     boolean consumeMana(Player player, float amount);
+    /** Compensating add — used to undo a consumeMana without clobbering concurrent changes. */
+    default void addMana(Player player, float amount) {
+        setMana(player, getMana(player) + amount);
+    }
     float getMaxMana(Player player);
     String getBridgeType();
 }

--- a/src/main/java/com/otectus/arsnspells/bridge/BridgeManager.java
+++ b/src/main/java/com/otectus/arsnspells/bridge/BridgeManager.java
@@ -243,9 +243,7 @@ public class BridgeManager {
-                // Atomic dual-cost: capture state for rollback if second consumption fails
-                float arsManaBefore = arsBridge.getMana(player);
                 boolean arsSuccess = arsBridge.consumeMana(player, arsCost);
                 if (!arsSuccess) {
                     return false;
                 }

                 boolean issSuccess = issBridge.consumeMana(player, issCost);
                 if (!issSuccess) {
-                    // Rollback: restore Ars mana to pre-consumption value
-                    arsBridge.setMana(player, arsManaBefore);
+                    // Compensating refund — does not clobber concurrent regen/buffs.
+                    arsBridge.addMana(player, arsCost);
                     return false;
                 }
```
Override `addMana` in [IronsBridge](src/main/java/com/otectus/arsnspells/bridge/IronsBridge.java) to call `data.addMana(amount)`, and in [ArsNativeBridge](src/main/java/com/otectus/arsnspells/bridge/ArsNativeBridge.java) to call `cap.addMana(amount)`.

**Regression test**: New JUnit harness. Stub a player with Ars mana = 100. Simulate: (1) snapshot/consume of 40, (2) external `+20` regen, (3) ISS failure → rollback. Pre-fix: pool ends at 100 (regen lost). Post-fix: pool ends at 80 (regen preserved, only consume reversed).

**Cross-references checked**: [IronsBridge.java:43-53](src/main/java/com/otectus/arsnspells/bridge/IronsBridge.java), [ArsNativeBridge.java:22-31](src/main/java/com/otectus/arsnspells/bridge/ArsNativeBridge.java), [ManaInfusionRitual.java:26](src/main/java/com/otectus/arsnspells/rituals/ManaInfusionRitual.java) (concurrent mutator outside the bridge).

---

## ANS-CRIT-004 — Cross-cast cost multiplier silently bypassed when wearing Cursed/Virtue Ring

**Vector**: V5 (game-state correctness) / V6 (event-priority misalignment)
**Files**: [src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java:310-382](src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java), [events/CursedRingHandler.java:50,111](src/main/java/com/otectus/arsnspells/events/CursedRingHandler.java), [events/VirtueRingHandler.java](src/main/java/com/otectus/arsnspells/events/VirtueRingHandler.java)
**Location**: `CrossCastingHandler.onArsSpellCost` (default `NORMAL` priority)

**Failure state**: `SpellCostCalcEvent` dispatch order with the ring active:

1. `CursedRingHandler.onSpellCostCalc` at `@SubscribeEvent(priority = HIGHEST)` — stamps pending LP, **sets `event.currentCost = 0`** (line 111).
2. `VirtueRingHandler.onSpellCostCalc` at HIGHEST — same shape for aura.
3. `CrossCastingHandler.onArsSpellCost` at default `NORMAL` (no priority annotation at line 310) — reads `event.currentCost = 0`, multiplies by `cross_cast_cost_multiplier` (default 1.25), stores `0 * 1.25 = 0` as both `entry.arsCost` and `entry.issCost` (lines 336, 341-342).

The documented "1.25× cross-cast overhead" is **silently dropped to zero** whenever a ring is worn. Players cross-cast through inscribed items at zero overhead, paying only the base spell's LP/aura. Cross-cast inscription becomes free-conversion gear once you have a ring — directly contradicts the v2.0.0 release notes' cross-cast economy design.

**Trigger**: Equip Cursed Ring (or Virtue Ring), hold an inscribed Ars-via-Iron's or Iron's-via-Ars item, cast.

**Evidence**:
```java
// CursedRingHandler.java:50,111
@SubscribeEvent(priority = EventPriority.HIGHEST)
public static void onSpellCostCalc(SpellCostCalcEvent event) {
    ...
    event.currentCost = 0;
    ...
}
```
```java
// CrossCastingHandler.java:310,331,336
@SubscribeEvent  // ← default NORMAL priority, runs AFTER ring handlers
public static void onArsSpellCost(SpellCostCalcEvent event) {
    ...
    int baseEventCost = Math.max(0, event.currentCost);   // 0 if ring stamped first
    ...
    int totalCost = Math.max(0, Math.round(baseEventCost * multiplier));  // 0 * 1.25 = 0
```

**Fix strategy**: The cross-cast multiplier needs to apply to the **pre-ring** cost. Two options:

1. **Re-order events**: register `onArsSpellCost` at `EventPriority.HIGHEST + 1` (use `HIGHEST` with an explicit comment that it must precede the ring handlers). Then the ring handlers see the already-multiplied cost and stamp LP/aura proportionally to the cross-cast price.
2. **Defer ring handling for cross-casts**: ring handlers check `CrossCastContext.peek(player) != null && entry.type == ARS_NOUVEAU` and skip — letting `onArsSpellCost` set the multiplied cost first; a follow-up handler (LOWEST) re-evaluates ring conversion against the multiplied price.

Option 1 is the minimal-diff fix. Option 2 is design-correct but touches more code. Recommend **Option 1 for 2.0.1** and design Option 2 for 2.1.

**Patch (Option 1)**:
```diff
--- a/src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java
+++ b/src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java
@@ -307,7 +307,12 @@ public class CrossCastingHandler {
     }

-    @SubscribeEvent
+    /**
+     * Runs at HIGHEST so the cross-cast multiplier applies to the unmodified base cost,
+     * BEFORE CursedRingHandler / VirtueRingHandler zero out event.currentCost to stamp
+     * pending LP/aura. Without this, ring wearers pay zero cross-cast overhead — see
+     * ANS-CRIT-004 in AUDIT.md.
+     */
+    @SubscribeEvent(priority = EventPriority.HIGHEST)
     public static void onArsSpellCost(SpellCostCalcEvent event) {
```

**Regression test**: Manual on dedicated server, Cursed Ring equipped, inscribed Ars spell with base mana cost 100, `cross_cast_cost_multiplier = 1.25`. Pre-fix: pending LP = (LP-for-100-mana). Post-fix: pending LP = (LP-for-125-mana). Repeat with Virtue Ring for aura.

**Cross-references checked**: [CursedRingHandler.java:50,71,111](src/main/java/com/otectus/arsnspells/events/CursedRingHandler.java); [VirtueRingHandler.java](src/main/java/com/otectus/arsnspells/events/VirtueRingHandler.java); [CrossCastingHandler.java:317-326](src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java) (multiplierApplied one-shot guard — this fix preserves it).

---

# HIGH

## ANS-HIGH-001 — `spell_transcription.json` hardcodes `irons_spellbooks:spell_book` without `forge:conditional` → recipe load failure on Iron's-less servers

**Vector**: V3 (optional-mod compat)
**File**: [src/main/resources/data/ars_n_spells/recipes/apparatus/spell_transcription.json:13-15](src/main/resources/data/ars_n_spells/recipes/apparatus/spell_transcription.json)

**Failure state**: The recipe references `irons_spellbooks:spell_book` in `pedestalItems`. On a server without Iron's (a supported configuration per `mandatory=false`), Ars Nouveau's recipe deserializer raises `JsonSyntaxException: Unknown item 'irons_spellbooks:spell_book'` at datapack load. The recipe is skipped with an error in `latest.log`, JEI shows a phantom entry, and the transcription tablet item itself is also gated to Iron's-present (see ANS-HIGH-002) so the recipe is dead weight either way.

**Trigger**: Boot server without Iron's; `/reload`; observe error.

**Evidence**:
```json
"pedestalItems": [
    { "item": "irons_spellbooks:spell_book" },
    { "tag": "forge:logs/archwood" },
    { "item": "ars_nouveau:source_gem_block" }
]
```

**Fix strategy**: Wrap the recipe in Forge's `forge:conditional` envelope with a `forge:mod_loaded` predicate. The output item is also Iron's-gated, so the whole recipe is moot without Iron's.

**Patch**:
```diff
--- a/src/main/resources/data/ars_n_spells/recipes/apparatus/spell_transcription.json
+++ b/src/main/resources/data/ars_n_spells/recipes/apparatus/spell_transcription.json
@@ -1,24 +1,30 @@
-{
-  "type": "ars_nouveau:enchanting_apparatus",
-  "keepNbtOfReagent": false,
-  "output": { "item": "ars_n_spells:spell_transcription" },
-  "reagent": [ { "item": "ars_nouveau:novice_spell_book" } ],
-  "pedestalItems": [
-    { "item": "irons_spellbooks:spell_book" },
-    { "tag": "forge:logs/archwood" },
-    { "item": "ars_nouveau:source_gem_block" }
-  ],
-  "sourceCost": 2000
-}
+{
+  "type": "forge:conditional",
+  "recipes": [
+    {
+      "conditions": [ { "type": "forge:mod_loaded", "modid": "irons_spellbooks" } ],
+      "recipe": {
+        "type": "ars_nouveau:enchanting_apparatus",
+        "keepNbtOfReagent": false,
+        "output": { "item": "ars_n_spells:spell_transcription" },
+        "reagent": [ { "item": "ars_nouveau:novice_spell_book" } ],
+        "pedestalItems": [
+          { "item": "irons_spellbooks:spell_book" },
+          { "tag": "forge:logs/archwood" },
+          { "item": "ars_nouveau:source_gem_block" }
+        ],
+        "sourceCost": 2000
+      }
+    }
+  ]
+}
```

**Regression test**: Without Iron's, `/reload` and grep log for `Unknown item 'irons_spellbooks:spell_book'`. Post-fix: silent skip. With Iron's, recipe still produces the transcription tablet.

---

## ANS-HIGH-002 — Iron's-importing classes in `rituals/` and `events/` cause `NoClassDefFoundError` if ever classloaded on Iron's-less servers

**Vector**: V3 (optional-mod compat)
**Files**:
- [src/main/java/com/otectus/arsnspells/rituals/ManaInfusionRitual.java:5,26](src/main/java/com/otectus/arsnspells/rituals/ManaInfusionRitual.java)
- [src/main/java/com/otectus/arsnspells/rituals/ManaWellRitual.java:5,21](src/main/java/com/otectus/arsnspells/rituals/ManaWellRitual.java)
- [src/main/java/com/otectus/arsnspells/rituals/InscriptionInputs.java:6-8](src/main/java/com/otectus/arsnspells/rituals/InscriptionInputs.java)

**Failure state**: Three ritual-side classes have top-level `import io.redspace.ironsspellbooks.api.*`. Production paths gate construction behind `ModList.isLoaded("irons_spellbooks")` ([RitualRegistryHandler.java:27-34](src/main/java/com/otectus/arsnspells/rituals/RitualRegistryHandler.java)), so the *normal* execution path is safe — but the design is brittle:

- **`InscriptionInputs`** is called from `SpellUninscriptionRitual.java:61`, which is **registered unconditionally** at `RitualRegistryHandler.java:23`. Uninscription is documented as the Iron's-uninstall recovery flow ([RitualRegistryHandler.java:19-22](src/main/java/com/otectus/arsnspells/rituals/RitualRegistryHandler.java) explicitly calls this out as a design goal). When an Iron's-less server runs the uninscription ritual on a legacy inscribed item, the JVM resolves `InscriptionInputs` to invoke `classify()`, class verification fails on the Iron's imports → `NoClassDefFoundError` → ritual silently no-ops AND breaks the documented contract.

**Trigger**: Player has an inscribed item from a prior Iron's-loaded session; admin removes Iron's; player drops the item near an uninscription brazier and runs the ritual.

**Evidence**:
```java
// InscriptionInputs.java:6-8
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
```
Called from `SpellUninscriptionRitual.java:61`:
```java
InscriptionInputs inputs = InscriptionInputs.classify(entities);
```

**Fix strategy**: Split the Iron's parsing into a separate `IronsInscriptionReader` class that holds the three Iron's imports. Have `InscriptionInputs.readSource` delegate via `IronsCompat.isLoaded()` guard so the JVM never verifies the Iron's-touching class on Iron's-less servers.

For `ManaInfusionRitual` / `ManaWellRitual`: route mana grants through `BridgeManager.getBridge().addMana(player, amount)` instead of `MagicData.addMana(...)` — this also makes the rituals useful on Ars-only servers.

**Patch** (`ManaInfusionRitual` only; mirror in `ManaWellRitual`):
```diff
--- a/src/main/java/com/otectus/arsnspells/rituals/ManaInfusionRitual.java
+++ b/src/main/java/com/otectus/arsnspells/rituals/ManaInfusionRitual.java
@@ -1,8 +1,8 @@
 package com.otectus.arsnspells.rituals;

 import com.hollingsworth.arsnouveau.api.ritual.AbstractRitual;
+import com.otectus.arsnspells.bridge.BridgeManager;
 import com.otectus.arsnspells.config.AnsConfig;
-import io.redspace.ironsspellbooks.api.magic.MagicData;
 import net.minecraft.resources.ResourceLocation;
 import net.minecraft.world.entity.player.Player;
@@ -22,10 +22,8 @@ public class ManaInfusionRitual extends AbstractRitual {
         Player player = findNearestPlayer(8);
         if (player == null) return;
-        MagicData data = MagicData.getPlayerMagicData(player);
-        if (data != null) {
-            data.addMana(AnsConfig.RITUAL_MANA_INFUSION_AMOUNT.get().floatValue());
-        }
+        BridgeManager.getBridge().addMana(player,
+            AnsConfig.RITUAL_MANA_INFUSION_AMOUNT.get().floatValue());
     }
```

Plus the `InscriptionInputs` split (see plan in `Fix strategy` above — full diff omitted for brevity; outline: extract `IronsInscriptionReader.tryRead(stack)` into new file with the three Iron's imports, gate via `if (IronsCompat.isLoaded()) { var src = IronsInscriptionReader.tryRead(stack); if (src != null) return src; }`).

**Regression test**: Dedicated server boot with `run/mods/` = `ars_nouveau` + `ars_n_spells`. Use Spell Uninscription brazier on a legacy NBT-inscribed item. Pre-fix: `NoClassDefFoundError` in log, ritual silently fails. Post-fix: NBT stripped cleanly, no crash.

---

## ANS-HIGH-003 — `CasterContext` ThreadLocal not cleared on canCast exception → cross-player spell-context bleed

**Vector**: V1 (concurrency) / V5 (game-state correctness)
**File**: [src/main/java/com/otectus/arsnspells/mixin/ars/MixinSpellResolverContext.java:23-34](src/main/java/com/otectus/arsnspells/mixin/ars/MixinSpellResolverContext.java) + [util/CasterContext.java](src/main/java/com/otectus/arsnspells/util/CasterContext.java)

**Failure state**: `@At("RETURN")` does NOT fire when `canCast` throws. The ThreadLocals `CASTER` and `ACTIVE_SPELL` hold strong references to the previous Player and Spell. On the main server thread (which handles all players' casts sequentially), an exception in any link of the canCast mixin chain leaves stale state. The next caster on that thread reads the *previous* caster's spell via `CursedRingHandler.onSpellCostCalc:88` and `VirtueRingHandler.onSpellCostCalc:85`, which both query `CasterContext.getSpell()`.

**Trigger**: Any uncaught exception inside `canCast` — `SpellRegistry.getSpell` reflection chain throws, stale capability NPE, third-party mixin in the chain throws. Visible as: player A's cast crashes, player B casts next tick and their Cursed Ring LP calculation is based on player A's spell (wrong tier, wrong school).

**Evidence**:
```java
// MixinSpellResolverContext.java:31-34
@Inject(method = "canCast", at = @At("RETURN"))
private void arsnspells$clearContext(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
    CasterContext.clear();
}
```
Consumer at `CursedRingHandler.java:88-90`:
```java
var analysis = CasterContext.getSpell()
    .map(SpellAnalysis::analyze)
    .orElse(null);
```

**Fix strategy**: The codebase already has [SafeCasterContext](src/main/java/com/otectus/arsnspells/util/SafeCasterContext.java) with try-with-resources semantics. Migrate the canCast capture to use it, or derive the spell from `event.context.getSpell()` at the ring-handler call sites and delete the ThreadLocal entirely.

**Patch** (preferred — delete the ThreadLocal):
```diff
--- a/src/main/java/com/otectus/arsnspells/events/CursedRingHandler.java
+++ b/src/main/java/com/otectus/arsnspells/events/CursedRingHandler.java
@@ -85,9 +85,8 @@ public class CursedRingHandler {
         int manaCost = event.currentCost;
         if (manaCost <= 0) return;

-        var analysis = CasterContext.getSpell()
-            .map(SpellAnalysis::analyze)
-            .orElse(null);
+        var spell = event.context != null ? event.context.getSpell() : null;
+        var analysis = spell != null ? SpellAnalysis.analyze(spell) : null;
```
Same for `VirtueRingHandler`. Then delete `MixinSpellResolverContext` and the legacy `CasterContext` class.

**Regression test**: JUnit harness — set CasterContext for player A, throw inside a stubbed canCast handler, then capture for player B. Pre-fix: B's first read returns A's spell. Post-fix: B reads from the event context directly.

---

## ANS-HIGH-004 — `CrossCastContext.Entry` mutable fields are non-volatile; `multiplierApplied` is not atomically check-and-set

**Vector**: V1 (concurrency)
**File**: [src/main/java/com/otectus/arsnspells/spell/CrossCastContext.java:121-149](src/main/java/com/otectus/arsnspells/spell/CrossCastContext.java)

**Failure state**: The `ACTIVE_CASTS` map is `ConcurrentHashMap`, but the `Entry` value object's mutable fields (`arsCost`, `issCost`, `costsReady`, `blocked`, `multiplierApplied`, `spellId`) are plain non-volatile. The `multiplierApplied` field is documented as a "one-shot guard" for repeated cost-calc fires, but the read-then-write at [CrossCastingHandler.java:324,347](src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java) is not atomic — under concurrent dispatch (two cost-calc fires racing across packet handler threads, or two overlapping cross-cast attempts), a stale `false` read can race with another thread's `true` write, applying the multiplier twice (overcharge) or zero times (undercharge).

**Trigger**: Player rapidly clicks an inscribed item; two `CrossCastRequestPacket`s queue; the second begins while the first is mid-resolve.

**Evidence**:
```java
// CrossCastContext.java:132-143
public float arsCost;            // not volatile
public float issCost;            // not volatile
public boolean costsReady;       // not volatile
public boolean blocked;          // not volatile
public String spellId;           // not volatile
public boolean multiplierApplied;// not volatile
```

**Fix strategy**: Mark mutable fields `volatile`; promote `multiplierApplied` (and `blocked`) to `AtomicBoolean` so the one-shot guard is truly one-shot via `compareAndSet(false, true)`.

**Patch**:
```diff
--- a/src/main/java/com/otectus/arsnspells/spell/CrossCastContext.java
+++ b/src/main/java/com/otectus/arsnspells/spell/CrossCastContext.java
@@ -121,15 +121,16 @@ public final class CrossCastContext {
     public static final class Entry {
         public final CrossSpellType type;
         public final UUID attemptId;
         private final long expiresAt;
-        public float arsCost;
-        public float issCost;
-        public boolean costsReady;
-        public boolean blocked;
-        public String spellId;
-        public boolean multiplierApplied;
+        public volatile float arsCost;
+        public volatile float issCost;
+        public volatile boolean costsReady;
+        public volatile boolean blocked;
+        public volatile String spellId;
+        private final java.util.concurrent.atomic.AtomicBoolean multiplierApplied =
+            new java.util.concurrent.atomic.AtomicBoolean(false);
+
+        public boolean tryMarkMultiplierApplied() { return multiplierApplied.compareAndSet(false, true); }
+        public boolean isMultiplierApplied() { return multiplierApplied.get(); }
```
And at `CrossCastingHandler.onArsSpellCost:324`: replace `if (entry.multiplierApplied) return;` with `if (!entry.tryMarkMultiplierApplied()) return;` (drop the later assignment at line 347).

**Regression test**: Soak test — two threads simultaneously call `onArsSpellCost` with the same Entry. Pre-fix: occasional double-multiplication observed in trace logs. Post-fix: exactly one applies.

---

## ANS-HIGH-005 — `AuraSyncPacket` payload accepts arbitrary `int`s with no bounds; combined with missing `NetworkDirection.PLAY_TO_CLIENT` guard means a hostile client can flood the server

**Vector**: V5 (game-state) / V2 (dedicated-server)
**Files**: [src/main/java/com/otectus/arsnspells/network/AuraSyncPacket.java:23-31](src/main/java/com/otectus/arsnspells/network/AuraSyncPacket.java), [client/ClientAuraState.java:21-25](src/main/java/com/otectus/arsnspells/client/ClientAuraState.java), [network/PacketHandler.java:29](src/main/java/com/otectus/arsnspells/network/PacketHandler.java)

**Failure state**: Packet is registered without `Optional.of(NetworkDirection.PLAY_TO_CLIENT)`. A buggy or hostile client can send `AuraSyncPacket(Integer.MAX_VALUE, 1)` to the server — the handler enqueues work on the server main thread (small parsing cost, useless static-field write — not a crash, but a DoS amplification surface). More critically, on the client side, `ClientAuraState.update` clamps `Math.max(0, aura)` and `Math.max(1, maxAura)` but **does not enforce `aura ≤ maxAura` nor an upper bound on `maxAura`**. With `aura = Integer.MAX_VALUE`, `maxAura = 1`, the HUD label at [AuraBarController.java:106](src/main/java/com/otectus/arsnspells/client/AuraBarController.java) renders "2147483647 / 1".

**Trigger**: Hostile client sends crafted packet, OR a buggy server-side computation produces out-of-range values.

**Evidence**:
```java
// AuraSyncPacket.java:23-26
public AuraSyncPacket(FriendlyByteBuf buf) {
    this.aura = buf.readInt();      // no validation
    this.maxAura = buf.readInt();   // no validation
}

// PacketHandler.java:29 — missing direction guard
INSTANCE.registerMessage(id++, AuraSyncPacket.class,
    AuraSyncPacket::toBytes, AuraSyncPacket::new, AuraSyncPacket::handle);
```

**Fix strategy**: (a) Bound the payload at decode. (b) Add `NetworkDirection.PLAY_TO_CLIENT` registration constraint so the bus rejects mis-directed packets up front. (c) Clamp `aura ≤ maxAura` in `ClientAuraState.update`.

**Patch**:
```diff
--- a/src/main/java/com/otectus/arsnspells/network/AuraSyncPacket.java
+++ b/src/main/java/com/otectus/arsnspells/network/AuraSyncPacket.java
@@ -11,6 +11,8 @@ import java.util.function.Supplier;
  * HUD overlay can render without polling.
  */
 public class AuraSyncPacket {
+    private static final int MAX_REASONABLE_AURA = 100_000; // matches AnsConfig.AURA_MAX_DEFAULT ceiling
+
     private final int aura;
     private final int maxAura;

@@ -21,8 +23,10 @@ public class AuraSyncPacket {
     }

     public AuraSyncPacket(FriendlyByteBuf buf) {
-        this.aura = buf.readInt();
-        this.maxAura = buf.readInt();
+        int rawAura = buf.readInt();
+        int rawMax = buf.readInt();
+        this.maxAura = Math.max(1, Math.min(MAX_REASONABLE_AURA, rawMax));
+        this.aura = Math.max(0, Math.min(this.maxAura, rawAura));
     }

--- a/src/main/java/com/otectus/arsnspells/network/PacketHandler.java
+++ b/src/main/java/com/otectus/arsnspells/network/PacketHandler.java
@@ -29,4 +29,5 @@ public class PacketHandler {
-        INSTANCE.registerMessage(id++, AuraSyncPacket.class,
-            AuraSyncPacket::toBytes, AuraSyncPacket::new, AuraSyncPacket::handle);
+        INSTANCE.registerMessage(id++, AuraSyncPacket.class,
+            AuraSyncPacket::toBytes, AuraSyncPacket::new, AuraSyncPacket::handle,
+            java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));

--- a/src/main/java/com/otectus/arsnspells/client/ClientAuraState.java
+++ b/src/main/java/com/otectus/arsnspells/client/ClientAuraState.java
@@ -21,5 +21,5 @@ public final class ClientAuraState {
     public static void update(int newAura, int newMaxAura) {
-        aura = Math.max(0, newAura);
-        maxAura = Math.max(1, newMaxAura);
+        maxAura = Math.max(1, Math.min(100_000, newMaxAura));
+        aura = Math.max(0, Math.min(maxAura, newAura));
         initialized = true;
     }
```

**Regression test**: `ClientAuraStateTest.update_clampsToBounds` — feed `(Integer.MAX_VALUE, 1)`, assert `aura==1, maxAura==1`. Integration test: send AuraSyncPacket from a fake client connection to server; assert bus rejects with no handler invocation.

---

## ANS-HIGH-006 — `ResonanceSyncPacket` accepts `Float.NaN` / `Float.Infinity` → NaN propagates through Iron's spell damage

**Vector**: V5 (game-state)
**Files**: [src/main/java/com/otectus/arsnspells/network/ResonanceSyncPacket.java:17-19](src/main/java/com/otectus/arsnspells/network/ResonanceSyncPacket.java), [augmentation/ResonanceManager.java:31-33](src/main/java/com/otectus/arsnspells/augmentation/ResonanceManager.java)

**Failure state**: `buf.readFloat()` happily decodes NaN, ±Infinity, negative. The value is stored verbatim in `ResonanceManager.clientResonance` and is read by [SpellScalingUtil.java:83-87](src/main/java/com/otectus/arsnspells/util/SpellScalingUtil.java) which multiplies it into the spell-power multiplier. NaN multiplication propagates: all spell damage becomes NaN → entity-damage code may NaN out health → entity state corruption that serializes back to disk. Negative-infinity multipliers can produce healing-the-enemy behaviour. Hostile server or future buggy `computeResonance` (line 47: `maxMana` returning NaN from a third-party attribute) is the realistic vector.

**Trigger**: Hostile server, MitM, or server-side bug that produces non-finite resonance.

**Evidence**:
```java
// ResonanceSyncPacket.java:17-19
public ResonanceSyncPacket(FriendlyByteBuf buf) {
    this.resonance = buf.readFloat();  // no validation
}
// ResonanceManager.java:31-33
public static void setClientResonance(float value) {
    clientResonance = (double) value;  // accepts NaN/Inf
}
```

**Fix strategy**: Reject NaN/Infinity at the packet boundary and at `setClientResonance`. Also fence the server-side `computeResonance` (ANS-HIGH-007 below).

**Patch**:
```diff
--- a/src/main/java/com/otectus/arsnspells/network/ResonanceSyncPacket.java
+++ b/src/main/java/com/otectus/arsnspells/network/ResonanceSyncPacket.java
@@ -10,6 +10,8 @@ import java.util.function.Supplier;
 public class ResonanceSyncPacket {
+    private static final float MAX_RESONANCE = 100.0f; // matches AnsConfig.MAX_DAMAGE_MULTIPLIER ceiling
+
     private final float resonance;

     public ResonanceSyncPacket(FriendlyByteBuf buf) {
-        this.resonance = buf.readFloat();
+        float raw = buf.readFloat();
+        this.resonance = (Float.isFinite(raw) && raw >= 0.0f) ? Math.min(MAX_RESONANCE, raw) : 1.0f;
     }

--- a/src/main/java/com/otectus/arsnspells/augmentation/ResonanceManager.java
+++ b/src/main/java/com/otectus/arsnspells/augmentation/ResonanceManager.java
@@ -19,6 +19,6 @@ public class ResonanceManager {
-    private static double clientResonance = 1.0;
+    private static volatile double clientResonance = 1.0;
@@ -30,6 +30,7 @@
     public static void setClientResonance(float value) {
-        clientResonance = (double) value;
+        if (!Float.isFinite(value)) return;
+        clientResonance = Math.max(0.0, Math.min(100.0, (double) value));
     }
```

**Regression test**: Round-trip NaN, ±Infinity, -1.0 through the packet constructor; assert decoded value is in `[0, 100]`. Server-side: inject a NaN-producing attribute modifier; assert spell damage stays finite.

---

## ANS-HIGH-007 — `ResonanceManager.computeResonance` does not clamp `manaPercent` to `[0, 1]` → unbounded damage scaling when third-party mods overrun MagicData.getMana

**Vector**: V5 (game-state)
**File**: [src/main/java/com/otectus/arsnspells/augmentation/ResonanceManager.java:47-53](src/main/java/com/otectus/arsnspells/augmentation/ResonanceManager.java)

**Failure state**: `manaPercent = data.getMana() / Math.max(1.0, maxMana)`. Iron's `MagicData.getMana()` can briefly exceed `maxMana` from external buff scripts or other-mod attribute injection. With `RESONANCE_STRENGTH = 10.0` (config max) and `manaPercent = 5`, resonance = `1.0 + (5 * 10 * 0.2)` = **11.0** — applied multiplicatively to Iron's spell damage via [MixinIronsSpellDamage.java:43-45](src/main/java/com/otectus/arsnspells/mixin/irons/MixinIronsSpellDamage.java). The mixin has no cap, so legendary-tier spells can one-shot bosses unintentionally.

**Trigger**: Third-party mod or Iron's bug that makes `getMana() > maxMana`.

**Evidence**:
```java
// ResonanceManager.java:47-53
double maxMana = player.getAttributeValue(AttributeRegistry.MAX_MANA.get());
double manaPercent = data.getMana() / Math.max(1.0, maxMana);  // unbounded above
double strength = AnsConfig.RESONANCE_STRENGTH.get();
double resonance = 1.0 + (manaPercent * strength * 0.2);       // unbounded
resonanceCache.put(player.getUUID(), resonance);
```

**Fix strategy**: Clamp `manaPercent` to `[0, 1]`; cap final resonance at `MAX_DAMAGE_MULTIPLIER` from config.

**Patch**:
```diff
--- a/src/main/java/com/otectus/arsnspells/augmentation/ResonanceManager.java
+++ b/src/main/java/com/otectus/arsnspells/augmentation/ResonanceManager.java
@@ -45,11 +45,13 @@ public class ResonanceManager {
             double maxMana = player.getAttributeValue(AttributeRegistry.MAX_MANA.get());
-            double manaPercent = data.getMana() / Math.max(1.0, maxMana);
+            double rawPercent = data.getMana() / Math.max(1.0, maxMana);
+            double manaPercent = Math.max(0.0, Math.min(1.0, rawPercent));
             double strength = AnsConfig.RESONANCE_STRENGTH.get();
-
-            double resonance = 1.0 + (manaPercent * strength * 0.2);
+            double cap = AnsConfig.MAX_DAMAGE_MULTIPLIER.get();
+            double resonance = Math.min(cap, 1.0 + (manaPercent * strength * 0.2));
+            if (!Double.isFinite(resonance)) resonance = 1.0;
             resonanceCache.put(player.getUUID(), resonance);
```

**Regression test**: Mock a Player whose `MagicData.getMana()` returns `2 * maxMana`; assert `getResonance(player) ≤ MAX_DAMAGE_MULTIPLIER`.

---

## ANS-HIGH-008 — `PlayerEvent.Clone` capability-copy handlers use default priority → third-party HIGHEST handlers can read stale-default cap

**Vector**: V5 (game-state)
**Files**: [src/main/java/com/otectus/arsnspells/data/ModCapabilityProvider.java:52-70](src/main/java/com/otectus/arsnspells/data/ModCapabilityProvider.java), [aura/AuraCapabilityProvider.java:70-91](src/main/java/com/otectus/arsnspells/aura/AuraCapabilityProvider.java)

**Failure state**: Both clone handlers run at default `NORMAL` priority. On respawn/dimension change, any third-party mod's `PlayerEvent.Clone` handler registered at `HIGHEST` executes before our handler copies caps. If that handler reads our affinity / cooldown / progression / aura caps in its handler, it sees the freshly-default state (player's stats appear reset). No known mod does this today, but the 200+ mod target raises the risk.

**Trigger**: Any third-party mod with `@SubscribeEvent(priority = EventPriority.HIGHEST)` on `PlayerEvent.Clone` that queries our caps.

**Fix strategy**: Set `priority = EventPriority.HIGHEST` on both clone handlers.

**Patch**:
```diff
--- a/src/main/java/com/otectus/arsnspells/data/ModCapabilityProvider.java
+++ b/src/main/java/com/otectus/arsnspells/data/ModCapabilityProvider.java
@@ -49,7 +49,7 @@ public class ModCapabilityProvider implements ICapabilitySerializable<CompoundTag> {
-    @SubscribeEvent
+    @SubscribeEvent(priority = EventPriority.HIGHEST)
     public static void onPlayerClone(PlayerEvent.Clone event) {
```
Same for `AuraCapabilityProvider.onPlayerClone`.

**Regression test**: Register a `PlayerEvent.Clone` listener at `LOWEST` that reads the affinity cap; after respawn, assert it sees copied (not default) values.

---

## ANS-HIGH-009 — `MixinSanctifiedAbstractSpell` gated on Iron's only, but injects into Sanctified-Legacy-added method `canBeCraftedBy` → silent no-op on Sanctified-absent (acceptable today, brittle to refactor)

**Vector**: V3 (optional-mod compat)
**Files**: [src/main/java/com/otectus/arsnspells/mixin/ArsNSpellsMixinPlugin.java:34](src/main/java/com/otectus/arsnspells/mixin/ArsNSpellsMixinPlugin.java), [mixin/sanctified/MixinSanctifiedAbstractSpell.java:22-35](src/main/java/com/otectus/arsnspells/mixin/sanctified/MixinSanctifiedAbstractSpell.java)

**Failure state**: The mixin's gating-on-Iron's plus the target method `canBeCraftedBy` (added by Sanctified Legacy via its own mixin) plus `require = 0` on the `@Inject` combine to make the current code "silently no-op when Sanctified absent" — which is the right outcome. **But** a future maintainer who removes `require = 0`, or adds a second `@Inject` to this class without `require = 0`, will produce an Iron's-present-Sanctified-absent crash.

The fix is preventive — make the gating explicit and self-documenting.

**Fix strategy**: Add a Sanctified-presence probe to the mixin plugin; gate `MixinSanctifiedAbstractSpell` on `ironsPresent && sanctifiedPresent`.

**Patch**:
```diff
--- a/src/main/java/com/otectus/arsnspells/mixin/ArsNSpellsMixinPlugin.java
+++ b/src/main/java/com/otectus/arsnspells/mixin/ArsNSpellsMixinPlugin.java
@@ -14,12 +14,20 @@ public class ArsNSpellsMixinPlugin implements IMixinConfigPlugin {
     private boolean ironsPresent;
+    private boolean sanctifiedPresent;

     @Override
     public void onLoad(String mixinPackage) {
         ironsPresent = canLoadClass("io.redspace.ironsspellbooks.api.spells.AbstractSpell")
             && canLoadClass("io.redspace.ironsspellbooks.api.magic.MagicData");
+        // Sanctified Legacy adds `canBeCraftedBy` to Iron's AbstractSpell via its own mixin.
+        // Probe a known Sanctified marker class so we don't apply our mixin when Sanctified is absent.
+        // VERIFY: confirm the actual Sanctified Legacy package path against the jar before merging.
+        sanctifiedPresent = canLoadClass("net.sanctifiedlegacy.SanctifiedLegacy")
+            || canLoadClass("com.dt.sanctifiedlegacy.SanctifiedLegacy");
     }

     @Override
     public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
+        if (mixinClassName.endsWith("MixinSanctifiedAbstractSpell")) {
+            return ironsPresent && sanctifiedPresent;
+        }
         if (mixinClassName.endsWith("MixinIronsSpellDamage")
             || mixinClassName.endsWith("MixinIronsManaBarOverlay")
             || mixinClassName.endsWith("MixinIronsMagicDataMana")
             || mixinClassName.endsWith("MixinIronsCastValidation")
             || mixinClassName.endsWith("MagicDataAccessor")
-            || mixinClassName.endsWith("MixinScrollItem")
-            || mixinClassName.endsWith("MixinSanctifiedAbstractSpell")) {
+            || mixinClassName.endsWith("MixinScrollItem")) {
             return ironsPresent;
         }
         return true;
```

**NEEDS MANUAL VERIFICATION** of the Sanctified marker class path before merge.

---

## ANS-HIGH-010 — `MixinManaCapability.arsnspells$inBridgeCall` ThreadLocal recursion guard is global → AoE / party-share spells skip bridge for unrelated players

**Vector**: V1 (concurrency) / V5 (game-state)
**File**: [src/main/java/com/otectus/arsnspells/mixin/ars/MixinManaCapability.java:37-43](src/main/java/com/otectus/arsnspells/mixin/ars/MixinManaCapability.java)

**Failure state**: `arsnspells$inBridgeCall` is `static final ThreadLocal<Boolean>` — shared across all ManaCap instances per JVM per thread. Inside a single thread, two different players' ManaCap operations cannot be nested. If a spell handler iterates other players' mana while a primary player's `getCurrentMana` is in flight (AoE buff, third-party party-share enchantment, Apotheosis affixes), the second player's call sees `inBridgeCall == true` and **skips the bridge entirely**, returning stale Ars-native data instead of the active bridge's data.

**Trigger**: Any mod with cross-player mana reads during another player's bridge call.

**Fix strategy**: Per-UUID guard set instead of a global boolean.

**Patch**:
```diff
--- a/src/main/java/com/otectus/arsnspells/mixin/ars/MixinManaCapability.java
+++ b/src/main/java/com/otectus/arsnspells/mixin/ars/MixinManaCapability.java
@@ -36,7 +36,8 @@ public abstract class MixinManaCapability {
     @Unique
-    private static final ThreadLocal<Boolean> arsnspells$inBridgeCall = ThreadLocal.withInitial(() -> false);
+    private static final ThreadLocal<java.util.Set<java.util.UUID>> arsnspells$inBridgeCall =
+        ThreadLocal.withInitial(java.util.HashSet::new);

     @Inject(method = "getCurrentMana", at = @At("HEAD"), cancellable = true)
     private void arsnspells$getCurrentMana(CallbackInfoReturnable<Double> cir) {
-        if (arsnspells$inBridgeCall.get()) {
+        if (!(this.livingEntity instanceof Player p)) return;
+        if (arsnspells$inBridgeCall.get().contains(p.getUUID())) {
             return;  // Recursion guard: let native run for this specific player
         }
```
Every `try { set(true); ... } finally { set(false); }` becomes `add(uuid) / remove(uuid)`. Also `arsnspells$inBridgeCall.remove()` when the set is empty to avoid ThreadLocal leak on long-lived threads.

**Regression test**: JUnit — call `MixinManaCapability.arsnspells$getCurrentMana` recursively from a stubbed bridge for two different player UUIDs on the same thread. Pre-fix: second player's call skips bridge. Post-fix: both players get bridge value.

---

## ANS-HIGH-011 — `CursedRingHandler` / `VirtueRingHandler` periodic cleanup mixes per-player tickCounts → wrong-player TTL eviction

**Vector**: V4 (resource hygiene)
**Files**: [events/CursedRingHandler.java:301-304](src/main/java/com/otectus/arsnspells/events/CursedRingHandler.java), [events/VirtueRingHandler.java:272-275](src/main/java/com/otectus/arsnspells/events/VirtueRingHandler.java)

**Failure state**: Both handlers' periodic sweep uses `event.player.tickCount` as `now` while iterating ALL players' pendingCosts. The `tickStamp` for each entry was captured from the *casting* player's `tickCount`, not the *sweeping* player's. Player A with `tickCount = 10000` runs the sweep; player B's pending was stamped at B's `tickCount = 200`; the sweep evaluates `10000 - 200 > 100` → **evicts B's pending mid-cast**.

**Trigger**: One long-uptime player + one freshly-joined player, both wearing rings, both casting.

**Evidence**:
```java
// CursedRingHandler.java:301-304 (identical shape in VirtueRingHandler.java:272-275)
if (event.player.tickCount % 100 == 0) {
    int now = event.player.tickCount;
    pendingCosts.entrySet().removeIf(entry -> now - entry.getValue().tickStamp > PENDING_COST_TTL_TICKS);
}
```

**Fix strategy**: Switch to wall-clock TTL (`System.currentTimeMillis()`) — matches the IronsAuraHandler / IronsLPHandler pattern that the other handlers already use correctly. Or use `event.player.level().getGameTime()` (server-global game ticks).

**Patch**:
```diff
--- a/src/main/java/com/otectus/arsnspells/events/CursedRingHandler.java
+++ b/src/main/java/com/otectus/arsnspells/events/CursedRingHandler.java
@@ -298,12 +298,12 @@ public class CursedRingHandler {
     @SubscribeEvent
     public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
         if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
-        if (event.player.tickCount % 100 == 0) {
-            int now = event.player.tickCount;
-            pendingCosts.entrySet().removeIf(entry -> now - entry.getValue().tickStamp > PENDING_COST_TTL_TICKS);
+        if (event.player.tickCount % 100 == 0) {
+            long now = event.player.level().getGameTime();
+            pendingCosts.entrySet().removeIf(entry -> now - entry.getValue().gameTimeStamp > PENDING_COST_TTL_TICKS);
         }
     }
```
And change `PendingLPCost` to store `long gameTimeStamp` instead of `int tickStamp`. Apply identically in `VirtueRingHandler`.

**Regression test**: Player A idles 5+ minutes; player B logs in, begins cast; assert B's pending survives the next A-tick cleanup pass.

---

## ANS-HIGH-012 — `CONVERSION_RATE_IRON_TO_ARS` is a flat scalar; ARS_PRIMARY mode ignores pool-size ratio → Iron's spells cost 10× intended at default Ars / Iron's pool defaults

**Vector**: V5 (game-state)
**File**: [src/main/java/com/otectus/arsnspells/spell/CrossCastIronsHandler.java:53-56,79-82](src/main/java/com/otectus/arsnspells/spell/CrossCastIronsHandler.java)

**Failure state**: Default config: Ars max mana ~100, Iron's max mana ~1000, `CONVERSION_RATE_IRON_TO_ARS = 1.0`. Casting a 50-mana Iron's spell from Ars charges 50 Ars mana — that's 50% of the Ars pool, while in Iron's the same spell is 5% of pool. The pre-existing [ManaRegenBridge](src/main/java/com/otectus/arsnspells/bridge/ManaRegenBridge.java) implements pool-aware conversion for regen; the cost path should use the same idea.

**Trigger**: ARS_PRIMARY mode + unified mana + any Iron's spell.

**Fix strategy**: Multiply by `arsMax / ironsMax` so the cost-ratio matches the pool-ratio.

**Patch**:
```diff
--- a/src/main/java/com/otectus/arsnspells/spell/CrossCastIronsHandler.java
+++ b/src/main/java/com/otectus/arsnspells/spell/CrossCastIronsHandler.java
@@ -51,8 +51,7 @@ public class CrossCastIronsHandler {
                 if (unified && mode == ManaUnificationMode.ARS_PRIMARY) {
-                    multiplied = Math.max(0, (int) Math.round(
-                        multiplied * AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get()));
+                    multiplied = Math.max(0, (int) Math.round(
+                        multiplied * effectiveIronToArsRate(player)));
                 }
@@ -79,7 +78,12 @@
         if (unified && mode == ManaUnificationMode.ARS_PRIMARY) {
-            int adjusted = (int) Math.round(event.getManaCost() * AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get());
+            int adjusted = (int) Math.round(event.getManaCost() * effectiveIronToArsRate(player));
             event.setManaCost(Math.max(0, adjusted));
         }
     }
+
+    private static double effectiveIronToArsRate(Player player) {
+        double base = AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get();
+        double arsMax = ManaUtil.getNativeMana(player).map(c -> (double) c.getMaxMana())
+            .orElse(AnsConfig.DEFAULT_MAX_MANA.get());
+        double ironsMax = ManaRegenBridge.getCurrentIronsMaxMana(player);
+        if (ironsMax <= 0.0 || arsMax <= 0.0) return base;
+        return base * (arsMax / ironsMax);
+    }
```

**Regression test**: ARS_PRIMARY mode, default pools, cast `irons_spellbooks:fireball` (50 mana). Pre-fix: 50 Ars charged. Post-fix: ~5 Ars charged (50 × 1.0 × 100/1000).

---

## ANS-HIGH-013 — Four S2C packets registered without `NetworkDirection` guard → server accepts client-sent versions, useless but DoS-amplifiable

**Vector**: V5 (game-state) / V2 (dedicated-server)
**File**: [src/main/java/com/otectus/arsnspells/network/PacketHandler.java:23-29](src/main/java/com/otectus/arsnspells/network/PacketHandler.java)

**Failure state**: `ResonanceSyncPacket`, `AffinitySyncPacket`, `CooldownSyncPacket`, `AuraSyncPacket` are all S2C by intent but lack `Optional.of(NetworkDirection.PLAY_TO_CLIENT)`. A hostile client can send these to the server; handlers `enqueueWork` and dispatch on the server main thread (wasted parsing cost, useless static-field writes). `CrossCastRequestPacket` at line 32-34 *does* use the direction guard correctly — establishing the canonical pattern this PR violates four times.

**Fix strategy**: Add the direction guard to all four S2C registrations.

**Patch**:
```diff
--- a/src/main/java/com/otectus/arsnspells/network/PacketHandler.java
+++ b/src/main/java/com/otectus/arsnspells/network/PacketHandler.java
@@ -23,7 +23,8 @@ public class PacketHandler {
-        INSTANCE.registerMessage(id++, ResonanceSyncPacket.class, ResonanceSyncPacket::toBytes,
-            ResonanceSyncPacket::new, ResonanceSyncPacket::handle);
+        INSTANCE.registerMessage(id++, ResonanceSyncPacket.class, ResonanceSyncPacket::toBytes,
+            ResonanceSyncPacket::new, ResonanceSyncPacket::handle,
+            java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
```
Same pattern for `AffinitySyncPacket`, `CooldownSyncPacket`, `AuraSyncPacket`.

**Regression test**: Construct an `AuraSyncPacket` and dispatch from a fake client on the server bus; assert the bus rejects (handler not invoked).

---

## ANS-HIGH-014 — `EquipmentIntegration.equipmentCache` is non-concurrent `HashMap` mutated from multiple event threads

**Vector**: V1 (concurrency)
**File**: [src/main/java/com/otectus/arsnspells/equipment/EquipmentIntegration.java:38](src/main/java/com/otectus/arsnspells/equipment/EquipmentIntegration.java)

**Failure state**: `private static final Map<UUID, CachedEquipmentData> equipmentCache = new HashMap<>();` — mutated from `LivingEquipmentChangeEvent` (server thread), `PlayerLoggedOutEvent` (network/main boundary), tick handler, and cost-calc spell handler. The sibling cache at [SanctifiedLegacyCompat.java:72](src/main/java/com/otectus/arsnspells/compat/SanctifiedLegacyCompat.java) is correctly `ConcurrentHashMap` — this is a missed conversion. With a 200+ mod loadout and frequent equip swaps, `HashMap.put` mid-resize during another path's iteration produces `ConcurrentModificationException` or corrupted internal state.

**Fix strategy**: Change to `ConcurrentHashMap`.

**Patch**:
```diff
--- a/src/main/java/com/otectus/arsnspells/equipment/EquipmentIntegration.java
+++ b/src/main/java/com/otectus/arsnspells/equipment/EquipmentIntegration.java
@@ -23,9 +23,9 @@ import org.slf4j.LoggerFactory;
-import java.util.HashMap;
 import java.util.Map;
 import java.util.UUID;
+import java.util.concurrent.ConcurrentHashMap;
@@ -36,7 +36,7 @@
-    private static final Map<UUID, CachedEquipmentData> equipmentCache = new HashMap<>();
+    private static final Map<UUID, CachedEquipmentData> equipmentCache = new ConcurrentHashMap<>();
```

**Regression test**: Concurrency stress — 8 threads interleaving `clearCache(uuid)` / `getCurioDiscounts(uuid)` / `calculateBonuses(uuid)` for 10 seconds. Pre-fix: occasional CME or hang. Post-fix: clean.

---

## ANS-HIGH-015 — `RegenSynergyHandler.sourceJarCacheMap` is `HashMap` (not `ConcurrentHashMap`)

**Vector**: V1 (concurrency)
**File**: [src/main/java/com/otectus/arsnspells/events/RegenSynergyHandler.java:21](src/main/java/com/otectus/arsnspells/events/RegenSynergyHandler.java)

**Failure state**: Same shape as ANS-HIGH-014. Mutated from `PlayerTickEvent` (per-player), `PlayerLoggedOutEvent`, and `PlayerChangedDimensionEvent`. Single-threaded in current Forge 1.20.1 player-event dispatch, but the static field is shared across all players and the design has zero thread-safety guarantee. Compare to the correct `ConcurrentHashMap` pattern at [ResonanceManager.java:18](src/main/java/com/otectus/arsnspells/augmentation/ResonanceManager.java).

**Patch**: Change `new HashMap<>()` → `new ConcurrentHashMap<>()` at line 21.

---

## ANS-HIGH-016 — `ConfigScreenFactory` mutates `ModConfig.Type.COMMON` from client UI → toggles silent no-op on dedicated server

**Vector**: V2 (dedicated-server)
**Files**: [src/main/java/com/otectus/arsnspells/config/ConfigScreenFactory.java:81-144,231-238](src/main/java/com/otectus/arsnspells/config/ConfigScreenFactory.java), [ArsNSpells.java:65](src/main/java/com/otectus/arsnspells/ArsNSpells.java)

**Failure state**: `AnsConfig.SPEC` is registered as `ModConfig.Type.COMMON`. On a dedicated server, client-side `AnsConfig.ENABLE_*.set(value)` mutates only the *client's* view; Forge does not auto-sync COMMON configs from client to server. Server-side gameplay still reads the unchanged server value. The in-game config screen is a no-op in multiplayer.

**Fix strategy**: Switch to `ModConfig.Type.SERVER` (server-authoritative, auto-synced) — appropriate for gameplay tunables (RESONANCE_STRENGTH, conversion rates, multipliers, ring toggles). Make `ConfigScreenFactory` open as read-only when not in singleplayer.

**Patch**:
```diff
--- a/src/main/java/com/otectus/arsnspells/ArsNSpells.java
+++ b/src/main/java/com/otectus/arsnspells/ArsNSpells.java
@@ -63,7 +63,8 @@
-        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AnsConfig.SPEC, "ars_n_spells-common.toml");
+        // Gameplay-affecting tunables live in SERVER config so the server is authoritative.
+        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, AnsConfig.SPEC, "ars_n_spells-server.toml");
```
And gate the screen's mutation buttons on `Minecraft.getInstance().hasSingleplayerServer()`.

**Caveat**: Existing user configs at `config/ars_n_spells-common.toml` will be ignored — document this in CHANGELOG as a 2.0.1 migration.

---

## ANS-HIGH-017 — `AnsConfig.safeSave` blocks the caller thread up to ~700ms (Thread.sleep + file I/O) — called from `/ans` commands on the server tick thread

**Vector**: V1 (concurrency)
**Files**: [config/AnsConfig.java:951-981](src/main/java/com/otectus/arsnspells/config/AnsConfig.java), [commands/ArsNSpellsCommands.java:70,94](src/main/java/com/otectus/arsnspells/commands/ArsNSpellsCommands.java), [config/ConfigScreenFactory.java:213](src/main/java/com/otectus/arsnspells/config/ConfigScreenFactory.java)

**Failure state**: `safeSave` uses `Thread.sleep(100/200/400)` between retry attempts plus three blocking `SPEC.save()` calls. Total worst case ~700ms on the caller's thread. Callers include `ArsNSpellsCommands.setDefaultMana` (server main thread → freezes all player connections, exceeds 50ms tick deadline). Triggers easily under file lock contention (antivirus, OneDrive, simultaneous saves).

**Fix strategy**: Offload to a single-thread daemon executor.

**Patch**:
```diff
--- a/src/main/java/com/otectus/arsnspells/config/AnsConfig.java
+++ b/src/main/java/com/otectus/arsnspells/config/AnsConfig.java
@@ -948,40 +948,21 @@
-    public static boolean safeSave() {
-        int maxRetries = 3;
-        int retryDelay = 100;
-        for (int i = 0; i < maxRetries; i++) {
-            try { SPEC.save(); return true; }
-            catch (Exception e) {
-                if (i < maxRetries - 1) { try { Thread.sleep(retryDelay); retryDelay *= 2; } ... }
-            }
-        }
-        return false;
-    }
+    private static final java.util.concurrent.ExecutorService SAVE_EXEC =
+        java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
+            Thread t = new Thread(r, "AnsConfig-Save");
+            t.setDaemon(true);
+            return t;
+        });
+
+    public static boolean safeSave() {
+        SAVE_EXEC.submit(() -> {
+            try { SPEC.save(); LOGGER.info("OK Config saved"); }
+            catch (Exception e) { LOGGER.warn("Config save failed: {}", e.getMessage(), e); }
+        });
+        return true;
+    }
```

**Regression test**: Lock the config file (open in editor with exclusive lock), then run `/ans mana setdefault 200`. Pre-fix: server tick freezes ~700ms. Post-fix: command returns instantly; save fails async with log warning.

---

## ANS-HIGH-018 — `IronsAuraHandler.calculateIronsAuraCost` reuses LP rarity multiplier → 10× over-cost on legendary spells with Virtue Ring

**Vector**: V5 (game-state)
**File**: [src/main/java/com/otectus/arsnspells/events/IronsAuraHandler.java:246-276](src/main/java/com/otectus/arsnspells/events/IronsAuraHandler.java)

**Failure state**: Formula is `manaCost × AURA_BASE_MULTIPLIER × (1 + level × IRONS_LP_PER_LEVEL_MULTIPLIER) × IRONS_LP_<rarity>_MULTIPLIER`. Defaults at level 10 legendary: `1.0 × (1 + 10 × 0.1) × 5.0` = **10× the mana cost**. A 100-mana legendary spell costs 5500 aura against a 1000-cap pool → unspendable. The `AURA_TIER1/2/3_MULTIPLIER` config knobs exist in `AnsConfig` and are used by [AuraManager.calculateAuraCost](src/main/java/com/otectus/arsnspells/aura/AuraManager.java) (Ars side) but NOT by `IronsAuraHandler` — the two sides have inconsistent formulas.

**Fix strategy**: Introduce `IRONS_AURA_<RARITY>_MULTIPLIER` config knobs; stop importing LP rarity scales into aura math.

**Patch**: Add new config keys, update `calculateIronsAuraCost` to use them. (Full diff omitted for brevity — pattern identical to existing LP rarity knobs in AnsConfig.)

**Regression test**: Cast a legendary Iron's spell with Virtue Ring; assert aura cost ≤ max aura.

---

## ANS-HIGH-019 — `ClientAffinityPacketHandler` lacks `@OnlyIn(Dist.CLIENT)` and references `Minecraft.getInstance()`

**Vector**: V2 (dedicated-server)
**File**: [src/main/java/com/otectus/arsnspells/client/ClientAffinityPacketHandler.java:5,7,11](src/main/java/com/otectus/arsnspells/client/ClientAffinityPacketHandler.java)

**Failure state**: Class lives in `client/` package, imports `net.minecraft.client.Minecraft` at line 5, calls `Minecraft.getInstance()` at line 11, but has no `@OnlyIn(Dist.CLIENT)` annotation. Sibling [ClientAuraState.java:13](src/main/java/com/otectus/arsnspells/client/ClientAuraState.java) is correctly annotated. The DistExecutor double-lambda at the call site ([AffinitySyncPacket.java:36-38](src/main/java/com/otectus/arsnspells/network/AffinitySyncPacket.java)) defers invocation but not classload; the outer lambda's bytecode references the class. Without `@OnlyIn`, any classload trigger on dedicated server hits `NoClassDefFoundError` on `Minecraft`.

**Fix strategy**: Add `@OnlyIn(Dist.CLIENT)` to the class. Combine with ANS-HIGH-013 (add `NetworkDirection.PLAY_TO_CLIENT` to AffinitySyncPacket registration so the packet can never even reach the server).

**Patch**:
```diff
--- a/src/main/java/com/otectus/arsnspells/client/ClientAffinityPacketHandler.java
+++ b/src/main/java/com/otectus/arsnspells/client/ClientAffinityPacketHandler.java
@@ -3,6 +3,8 @@ package com.otectus.arsnspells.client;
 import net.minecraft.client.Minecraft;
+import net.minecraftforge.api.distmarker.Dist;
+import net.minecraftforge.api.distmarker.OnlyIn;

+@OnlyIn(Dist.CLIENT)
 public final class ClientAffinityPacketHandler {
```

**Regression test**: Cold-boot dedicated server with `gameTestServer` target; log `AffinitySyncPacket` outbound; assert no classload error.

---

## ANS-HIGH-020 — `IronsLPHandler` / `IronsAuraHandler` emit per-cast `LOGGER.info` entry-point traces → GB of log/day on production servers

**Vector**: V6 (code health)
**Files**: [events/IronsLPHandler.java:45-99](src/main/java/com/otectus/arsnspells/events/IronsLPHandler.java), [events/IronsAuraHandler.java:47-94](src/main/java/com/otectus/arsnspells/events/IronsAuraHandler.java)

**Failure state**: Each handler emits 4 INFO lines per PreCast + 4 per OnCast. A chain-casting player (Magic Missile auto-fire, scroll spam) produces hundreds of INFO lines per second. On a 200+ mod dedicated server this generates ~GB of log/day. Comment at line 46 calls this an "ENTRY-POINT TRACE (always-on)" — explicit decision but the cost is wrong for production.

**Fix strategy**: Downgrade all entry-point lines to DEBUG, OR gate behind a dedicated config flag that defaults false.

**Patch** (illustrative — same pattern across 16+ call sites):
```diff
-        LOGGER.info("[IronsAuraHandler] PreCast event received from Iron's ...
+        LOGGER.debug("[IronsAuraHandler] PreCast event received from Iron's ...
```

**Regression test**: Cast 100 Iron's spells; grep log for the entry-point pattern; pre-fix ≥400 lines, post-fix 0 lines.

---

## ANS-HIGH-021 — `LPDeathPrevention.activeTransactions` not cleared on logout → stale immune flag survives until periodic sweep on another player's tick

**Vector**: V4 (resource hygiene)
**File**: [src/main/java/com/otectus/arsnspells/events/LPDeathPrevention.java](src/main/java/com/otectus/arsnspells/events/LPDeathPrevention.java)

**Failure state**: `LPDeathPrevention` has periodic sweep (`event.player.tickCount % 60 == 0`) but no `PlayerLoggedOutEvent` handler. If the player who logged out was alone or the only one mid-tick, no cleanup runs until another player ticks. Stale `CastTransaction` lingers until 1-second `IMMUNE_TIMEOUT_MS` is reached on someone else's sweep.

**Fix strategy**: Add a 5-line logout handler.

**Patch**:
```diff
+    @SubscribeEvent
+    public static void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
+        activeTransactions.remove(event.getEntity().getUUID());
+    }
```

---

## ANS-HIGH-022 — `LPDeathPrevention` damage-type filter has dead `"indirectMagic"` clause (already matched by `"magic"`) and missing third-party magic types

**Vector**: V6 (code health) / V5 (correctness)
**File**: [src/main/java/com/otectus/arsnspells/events/LPDeathPrevention.java:109-117](src/main/java/com/otectus/arsnspells/events/LPDeathPrevention.java)

**Failure state**: `damageType.contains("magic")` already matches `"indirectMagic"`, so the second clause is redundant. More importantly, the contains-based filter only matches vanilla magic damage types; mod-added magic damage (Mahou Tsukai "witherCurse", Apotheosis "magicArrow") slips through and the LP-immunity does not protect against them — defeating the safety-mode design.

**Fix strategy**: Trust the same-tick scope (`player.tickCount != transaction.playerTickCount`) and remove the type filter entirely. Or whitelist the actual damage types our code can emit (we only emit `damageSources().magic()` and `applySilentHealthLoss` — only "magic" matters).

**Patch**:
```diff
-        if (damageType.contains("magic") ||
-            damageType.contains("indirectMagic") ||
-            damageType.contains("sacrifice")) {
+        // Same-tick scope is the real guard; allow any damage source to be blocked
+        // while the immune flag is active.
+        if (true) {
             event.setCanceled(true);
         }
```

---

## ANS-HIGH-023 — `AffinityHandler.onSpellCast` catches broad `Exception` → silently swallows packet-send failures, causing client desync

**Vector**: V6 (code health) / V5 (correctness)
**File**: [src/main/java/com/otectus/arsnspells/events/AffinityHandler.java:23-30](src/main/java/com/otectus/arsnspells/events/AffinityHandler.java)

**Failure state**: `try { AffinityType type = AffinityType.valueOf(school.toUpperCase()); data.addLevel(type, 1); PacketHandler.sendToClient(...); } catch (Exception ignored) {}`. The legit case (school name unmapped) is `IllegalArgumentException`. But `PacketHandler.sendToClient` can throw on network issues — that throw is silently dropped while the server-side cap was incremented. The client HUD stays stale until the next event.

**Fix strategy**: Narrow the catch to `IllegalArgumentException` only; let packet failures propagate or log explicitly.

**Patch**:
```diff
                     try {
                         AffinityType type = AffinityType.valueOf(school.toUpperCase());
                         data.addLevel(type, 1);
                         PacketHandler.sendToClient(new AffinitySyncPacket(type, data.getLevel(type)), player);
-                    } catch (Exception ignored) {}
+                    } catch (IllegalArgumentException unmappedSchool) {
+                        LOGGER.debug("Unmapped affinity school: {}", school);
+                    }
```

---

## ANS-HIGH-024 — `ProgressionHandler` does not re-apply transient attribute bonuses on respawn / dimension change

**Vector**: V5 (game-state)
**Files**: [events/ProgressionHandler.java:40-55](src/main/java/com/otectus/arsnspells/events/ProgressionHandler.java), [progression/ProgressionAttributes.java:32-50](src/main/java/com/otectus/arsnspells/progression/ProgressionAttributes.java)

**Failure state**: `ProgressionAttributes` uses transient modifiers (not persisted). Login re-applies — but `PlayerRespawnEvent` and `PlayerChangedDimensionEvent` do not. After respawn, accumulated cast counts grant 0 transient bonus until the player casts again.

**Fix strategy**: Add respawn + dimension handlers that iterate `data.getAllCastCounts()` and re-apply via `ProgressionAttributes.applyTransientBonus`.

---

## ANS-HIGH-025 — `IronsCompat` / `ScrollLPTracker` / `ScrollAuraTracker` pending entries not evicted on logout → minor leak

**Vector**: V4 (resource hygiene)
**Files**: [compat/ScrollLPTracker.java:19-43](src/main/java/com/otectus/arsnspells/compat/ScrollLPTracker.java), [compat/ScrollAuraTracker.java:19-44](src/main/java/com/otectus/arsnspells/compat/ScrollAuraTracker.java)

**Failure state**: Both tracker maps use `ConcurrentHashMap` (good) but rely entirely on the consumer (`MixinScrollItem` RETURN inject) to drain entries. If the RETURN inject is suppressed by another mod's cancel, or the scroll throw fails before RETURN, the entry leaks. No `PlayerLoggedOutEvent` handler clears stale entries.

**Fix strategy**: Have `CursedRingHandler.onPlayerLoggedOut` / `VirtueRingHandler.onPlayerLoggedOut` also call `ScrollLPTracker.clear(uuid)` / `ScrollAuraTracker.clear(uuid)`.

---

## ANS-HIGH-026 — Mod metadata stale: README header says v1.9.0 (build is 2.0.0); `mods.toml` description omits all 2.0.0 features

**Vector**: V6 (metadata correctness)
**Files**: [README.md:1](README.md), [src/main/resources/META-INF/mods.toml:9-13](src/main/resources/META-INF/mods.toml)

**Failure state**: README header is two minor releases stale. `mods.toml` description only mentions mana unification; 2.0.0 added LP/Aura rings, rituals, cross-cast inscription, affinity, resonance — none of which appear in the user-facing description shown in the Mods menu / CurseForge listing.

**Patch**:
```diff
--- a/README.md
+++ b/README.md
@@ -1 +1 @@
-# Ars 'n' Spells (v1.9.0)
+# Ars 'n' Spells (v2.0.0)

--- a/src/main/resources/META-INF/mods.toml
+++ b/src/main/resources/META-INF/mods.toml
@@ -8,7 +8,11 @@ version="${version}"
 displayName="Ars 'n' Spells"
 description='''
-Bridges Ars Nouveau and Iron's Spells 'n Spellbooks mana systems.
-Routes Ars Nouveau spell costs through Iron's Spells mana pool.
-Provides graceful fallback to native Ars mana if Iron's is unavailable.
+Bridges Ars Nouveau and Iron's Spells 'n Spellbooks: unified mana with five
+modes, cross-mod spell scaling, school affinity and progression, resonance
+bonuses, optional unified cooldowns, custom rituals (Mana Infusion, Mana Well),
+and cross-cast inscription via the Spell Transcription ritual. Optional
+Covenant of the Seven integration adds LP and aura casting via the Ring of
+Seven Curses/Virtues. Graceful fallback if Iron's is uninstalled.
 '''
```

---

## ANS-HIGH-027 — `ENABLE_PER_CAST_REAGENT` config key has zero readers (dead feature flag)

**Vector**: V6 (dead config key) / V5 (UX correctness)
**File**: [src/main/java/com/otectus/arsnspells/config/AnsConfig.java:183,879-887](src/main/java/com/otectus/arsnspells/config/AnsConfig.java)

**Failure state**: The key is fully documented and exposed in TOML, but no code reads `ENABLE_PER_CAST_REAGENT.get()`. Modpack authors who follow the comment and set `true` get zero behavioral change with no warning.

**Fix strategy**: Remove the key (recommended — ship config schema when implementation ships) OR log a one-shot WARN at config load if `true`.

**Patch**: Delete lines 183 and 879-887 of `AnsConfig.java`.

---

# MEDIUM

The following findings are grouped in a compact table. Each has full evidence and patch in the per-pipeline outputs (Wave 1) — see the regression-test instructions noted per row.

| ID | File | Issue | Fix |
|---|---|---|---|
| ANS-MED-001 | [ArsManaCalcHandler.java:54-79](src/main/java/com/otectus/arsnspells/events/ArsManaCalcHandler.java) | `syncIronsMaxAfterCalc` mutates Iron's MAX_MANA inside `MaxManaCalcEvent` handler — re-entrancy risk if `syncIronsMaxToArs` ever triggers `MaxManaCalcEvent` downstream (NEEDS MANUAL VERIFICATION) | Defer sync to next tick via `player.getServer().tell(...)`, or add a per-thread reentry flag |
| ANS-MED-002 | [CrossCastingHandler.java:195-207](src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java) | Pending `CrossCastContext` leaks if `castSpell` throws — context retained until TTL, contaminates next cast | Wrap `castSpell` in try/finally and `clear(player)` on exception |
| ANS-MED-003 | [MixinIronsCastValidation.java:46](src/main/java/com/otectus/arsnspells/mixin/irons/MixinIronsCastValidation.java) | `lastLogMs` ConcurrentHashMap accumulates one entry per unique player UUID indefinitely | Opportunistic eviction every 64th call: `removeIf(e -> e.getValue() < now - 60_000)` |
| ANS-MED-004 | [CrossCastValidator.java:127-129](src/main/java/com/otectus/arsnspells/spell/CrossCastValidator.java) | `SpellRegistry.getSpell(...)` called without try/catch — NPE during Iron's reload propagates to packet handler | Wrap in try/catch; return false; log once-throttled |
| ANS-MED-005 | [BridgeManager.java:229-230](src/main/java/com/otectus/arsnspells/bridge/BridgeManager.java) | DUAL_COST percentages can sum > 1.0 silently (warns but does not normalise) — double-charge in SEPARATE mode | Normalise at init; store normalised values; or fall back to (0.5, 0.5) when sum out of tolerance |
| ANS-MED-006 | [CrossCastingHandler.java:450-460](src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java) | `getSelectedIndex` mutates NBT on read when index out of range — shared-stack race | Don't mutate on read; return `Math.max(0, Math.min(size - 1, index))` |
| ANS-MED-007 | [IronsBridge.java:11-12,72-77](src/main/java/com/otectus/arsnspells/bridge/IronsBridge.java) | `errorLogged` boolean latches forever after first error → all subsequent failures silenced | Per-op `Set<String>` of logged ops, allow each error class to log once |
| ANS-MED-008 | [CrossCastingHandler.java:349-358](src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java) | Setting `event.currentCost = Integer.MAX_VALUE` to abort cast leaks to tooltip ("spell costs 2.1B mana"); other handlers may misinterpret | Use sentinel + check at MixinSpellResolverPreCast; or use `entry.blocked` flag and have pre-cast denial check it |
| ANS-MED-009 | [MixinManaCapability.java:143-192](src/main/java/com/otectus/arsnspells/mixin/ars/MixinManaCapability.java) | `addMana`/`removeMana` cancellation returns Iron's value as result, not requested delta — misleads Ars callers | Return `amount` for addMana, `0` for removeMana when cancelled; document in javadoc |
| ANS-MED-010 | [MixinSpellResolverMana.java:73-83](src/main/java/com/otectus/arsnspells/mixin/ars/MixinSpellResolverMana.java) | ISS_PRIMARY: if `consumeManaForMode` returns false, Ars native expend runs with stale data → spell casts without paying | Cancel anyway with `ci.cancel()` when consume fails |
| ANS-MED-011 | [CastingAuthority.java:163-197](src/main/java/com/otectus/arsnspells/casting/CastingAuthority.java) | Float comparison for mana validation drifts; player with exact cost fails by 0.0001; tooltip truncates display | Use `Math.ceil` on cost; double precision for conversion |
| ANS-MED-012 | [AffinityData.java:34-43](src/main/java/com/otectus/arsnspells/data/AffinityData.java) | `loadFromNBT` doesn't `levels.clear()` first → merges stale state if called twice | `levels.clear()` at line 34. Same for CooldownData. |
| ANS-MED-013 | [data/{AffinityData,CooldownData,ProgressionData}.java](src/main/java/com/otectus/arsnspells/data) | Plain `HashMap` for cap data; documented main-thread-only but undefended | Switch to `ConcurrentHashMap` or document the invariant explicitly |
| ANS-MED-014 | [AuraCapabilityProvider.java:64-68](src/main/java/com/otectus/arsnspells/aura/AuraCapabilityProvider.java) | Aura cap attached to client-side player → dead state diverges from server config | Gate `onAttachCapabilities` on `!player.level().isClientSide()` (NEEDS MANUAL VERIFICATION that no third-party mod queries client cap) |
| ANS-MED-015 | [UnifiedCooldownManager.java:114-136](src/main/java/com/otectus/arsnspells/cooldown/UnifiedCooldownManager.java) | `clearCooldown(s)` writes cap but does not send `CooldownSyncPacket` → client HUD shows stale bar. Also malformed log placeholder. | After write, if `ServerPlayer`, send sync packet; fix log args |
| ANS-MED-016 | [AffinitySyncPacket.java:21-23](src/main/java/com/otectus/arsnspells/network/AffinitySyncPacket.java) + CooldownSyncPacket | `buf.readUtf()` default cap = 32K chars; unbounded payload; resync amplification | Bound `readUtf(64)` and `readUtf(32)`; clamp level to `[0, 100]` |
| ANS-MED-017 | [CooldownSyncPacket.java:28-41](src/main/java/com/otectus/arsnspells/network/CooldownSyncPacket.java) | `timestamp = Long.MAX_VALUE` accepted → effectively infinite cooldown display | Clamp `[0, now + 1_000_000]` |
| ANS-MED-018 | [ManaBarController.java:62](src/main/java/com/otectus/arsnspells/client/ManaBarController.java) + MixinIronsManaBarOverlay:41 | `BridgeManager.getCurrentMode()` can return null pre-init → mana bar visibility wrong for early frames | Make getter fall back to `AnsConfig.getManaMode()` when `currentMode == null` |
| ANS-MED-019 | [OverlayDiagnostics.java:19-30](src/main/java/com/otectus/arsnspells/client/OverlayDiagnostics.java) | Always-registered event subscriber gated by runtime flag → per-overlay-per-frame dispatch cost even when disabled | Make registration opt-in: `enable()` calls `MinecraftForge.EVENT_BUS.register(...)`, `disable()` unregisters |
| ANS-MED-020 | [InscriptionInputs.java:99-115](src/main/java/com/otectus/arsnspells/rituals/InscriptionInputs.java) | Three `catch (Throwable ignored)` blocks mask `LinkageError` → makes Iron's classload bugs (ANS-HIGH-002) silent | Narrow to `catch (Exception ignored)` so LinkageError propagates |
| ANS-MED-021 | [EquipmentIntegration.java:485-517](src/main/java/com/otectus/arsnspells/equipment/EquipmentIntegration.java) | `Class.forName` + reflection used to call sibling class `SanctifiedLegacyCompat` — silent degradation if refactor renames method | Replace with direct import + call (the ModList gate at line 492 already protects compat) |
| ANS-MED-022 | [RitualRegistryHandler.java:12](src/main/java/com/otectus/arsnspells/rituals/RitualRegistryHandler.java) | `registered` flag is set once, never reset; if Ars rebuilds `ritualItemMap` (datapack reload), tablets vanish | Subscribe to `RecipesUpdatedEvent` / `AddReloadListenerEvent` to re-splice |
| ANS-MED-023 | [SanctifiedLegacyCompat.java:257-281](src/main/java/com/otectus/arsnspells/compat/SanctifiedLegacyCompat.java) | `scanCurios` logs ERROR per-tick on Curios API failure → log flood | One-shot `SCAN_FAILURE_LOGGED` set; clear on logout |
| ANS-MED-024 | [CursedRingHandler.java:251-263](src/main/java/com/otectus/arsnspells/events/CursedRingHandler.java) | Death-penalty `player.hurt(... MAX_VALUE)` fires `LivingDeathEvent` inside the spell-resolve stack → re-entry hazard if another mod's death listener triggers spell events | Defer to next tick via `player.getServer().tell(...)` |
| ANS-MED-025 | [IronsAuraHandler.java:118-135](src/main/java/com/otectus/arsnspells/events/IronsAuraHandler.java) | Pending aura stamped at PreCast HIGHEST; if downstream handler cancels event, stamp lingers 5s | Subscribe at LOWEST to detect `isCanceled` and evict, or accept the bounded window (TTL eviction works) |
| ANS-MED-026 | [CurioDiscountHandler.java:70-72](src/main/java/com/otectus/arsnspells/events/CurioDiscountHandler.java) | SLF4J `{:.1f}` is NOT a valid placeholder → literal string in log, arg silently dropped | Use `String.format("%.1f", ...)` then pass as `{}` |
| ANS-MED-027 | [CooldownHandler.java:22-29](src/main/java/com/otectus/arsnspells/events/CooldownHandler.java) | `event.setCanceled(true)` on `SpellCastEvent` — may not be cancellable; if not, cooldown does not block (mana still paid) | Gate at `SpellCostCalcEvent` by setting cost = MAX_VALUE (NEEDS VERIFICATION of cancellability) |
| ANS-MED-028 | [ResonanceEvents.java:30-34](src/main/java/com/otectus/arsnspells/events/ResonanceEvents.java), [LPDeathPrevention.java:161-177](src/main/java/com/otectus/arsnspells/events/LPDeathPrevention.java) | Per-player tick counter triggers shared-state cleanup → fires N× per minute with N players | Move cleanup to `ServerTickEvent` with single counter |
| ANS-MED-029 | [ArsNSpellsCommands.java:34](src/main/java/com/otectus/arsnspells/commands/ArsNSpellsCommands.java) vs [AnsConfig.java:294](src/main/java/com/otectus/arsnspells/config/AnsConfig.java) | Command root is `/ans` but config comment says `/arsnspells` — branding mismatch | Update config comment |
| ANS-MED-030 | [IronsLPHandler.java:248-252](src/main/java/com/otectus/arsnspells/events/IronsLPHandler.java) | `storePendingScrollLP` is `public static` with no validation; any mod can force-kill a player | Make package-private; sanity-check cost magnitude |
| ANS-MED-031 | [F-config] AnsConfig | `HYBRID_SYNC_RATE`, `ALLOW_MANA_OVERFLOW`, `MAX_PROGRESSION_LEVEL`, `PROGRESSION_XP_MULTIPLIER`, `AFFINITY_BONUS_MULTIPLIER`, `MAX_AFFINITY_BONUS` — six declared but unread config keys (modpack tuning trap) | Delete unused keys or wire to actual logic. (NEEDS VERIFICATION for the last 4 — they may be referenced through reflection.) |
| ANS-MED-032 | [AnsConfig.java:250-256](src/main/java/com/otectus/arsnspells/config/AnsConfig.java) | `CONVERSION_RATE_*` upper bound = 100.0 — round-trip can produce 10000× scaling | Tighten range to `[0.01, 10.0]` |
| ANS-MED-033 | [AnsConfig.java:215-217,926-931](src/main/java/com/otectus/arsnspells/config/AnsConfig.java) | `ENABLE_MANA_UNIFICATION` boolean redundant with `MANA_UNIFICATION_MODE="disabled"` — two ways to disable, only one is canonical | Document the precedence; consider removing the boolean in 2.1 |
| ANS-MED-034 | [AnsConfig.java:175,845-847](src/main/java/com/otectus/arsnspells/config/AnsConfig.java) | `RITUAL_MANA_INFUSION_AMOUNT` upper bound 100000 → infinite-mana cheat trivial | Cap at 10000 |
| ANS-MED-035 | [AnsConfig.java:902-904](src/main/java/com/otectus/arsnspells/config/AnsConfig.java) | `MANA_SYNC_INTERVAL` default = 1 tick → 20 pkt/sec/player; 200-player server is 4000 pkt/sec just for aura | Raise default to 5, floor to 2 |
| ANS-MED-036 | [gradle.properties:11](gradle.properties) vs [mods.toml:14](src/main/resources/META-INF/mods.toml) | `mod_authors=Oetectus` (typo) vs `authors="otectus"` — jar manifest disagrees with mod list | Fix typo to `otectus` |
| ANS-MED-037 | [F-mixin] [MixinSanctifiedAbstractSpell.java:27-30](src/main/java/com/otectus/arsnspells/mixin/sanctified/MixinSanctifiedAbstractSpell.java) | Inject `require = 0` masks the Sanctified-absent class crash but architecturally fragile | (See ANS-HIGH-009 for sanctifiedPresent gating) |
| ANS-MED-038 | [F-mods.toml:39](src/main/resources/META-INF/mods.toml) | Iron's version range `[1.20.1-3.15.0,1.20.1-3.16.0)` locks out 3.16+ | Loosen to `[1.20.1-3.15.0,1.20.1-4.0.0)` |
| ANS-MED-039 | [CapabilityResyncHandler.java:99](src/main/java/com/otectus/arsnspells/events/CapabilityResyncHandler.java) | `CooldownData.getLastCast` returns end-tick but is named "lastCast" — misleading | Rename to `getCooldownEnd`/`setCooldownEnd` |
| ANS-MED-040 | [SpellAnalysis.java:129-172](src/main/java/com/otectus/arsnspells/util/SpellAnalysis.java) | Substring matching for spell schools (e.g. `"light"` collides with `"lightning"` — guarded — but `"wind"` would collide with hypothetical `"winding"`) | Switch to registry tag-based mapping |
| ANS-MED-041 | [ArsSpellScalingHandler.java:98-104](src/main/java/com/otectus/arsnspells/events/ArsSpellScalingHandler.java) | `"onFire"` / `"inFire"` damage type matches lava → spell power scales lava damage post-cast | Drop the heuristic; rely on `magic` and `ars_nouveau` namespaces |

---

# OPTIMIZATION

| ID | File | Issue |
|---|---|---|
| ANS-OPT-001 | [AffinityDecayHandler.java:56-68](src/main/java/com/otectus/arsnspells/events/AffinityDecayHandler.java) | Sends up to 16 separate `AffinitySyncPacket` per decay tick — coalesce into `AffinityBulkSyncPacket` |
| ANS-OPT-002 | [SpellCategorizer.java](src/main/java/com/otectus/arsnspells/cooldown/SpellCategorizer.java) | File-level `@Deprecated` but `categorizeIronsSpell` is live — confusing |
| ANS-OPT-003 | [SpellCategoryMapper.java](src/main/java/com/otectus/arsnspells/cooldown/SpellCategoryMapper.java) | Dead code — no callers |
| ANS-OPT-004 | [CooldownTracker.java](src/main/java/com/otectus/arsnspells/cooldown/CooldownTracker.java) | Duplicates `CooldownData` shape — share an interface |
| ANS-OPT-005 | [CrossCastContext.java:80-88](src/main/java/com/otectus/arsnspells/spell/CrossCastContext.java) | TTL eviction is per-player; add a global sweep every ~100 ticks |
| ANS-OPT-006 | [CrossCastContext.java:11](src/main/java/com/otectus/arsnspells/spell/CrossCastContext.java) | `DEFAULT_TTL_TICKS = 200` is generous; align with ring 100-tick TTL |
| ANS-OPT-007 | [CastingAuthority.java:207-216](src/main/java/com/otectus/arsnspells/casting/CastingAuthority.java) | `detectAlternateResourceCost` is dead (TODO returns null); delete it and the dead enums |
| ANS-OPT-008 | [MixinArsPotionEffects.java:67-101](src/main/java/com/otectus/arsnspells/mixin/ars/MixinArsPotionEffects.java) | Remove/add `AttributeModifier` every tick even when value unchanged — cache last-applied per UUID |
| ANS-OPT-009 | [ManaBarController.java:75,86](src/main/java/com/otectus/arsnspells/client/ManaBarController.java) | `overlayId.contains("...")` per overlay per frame → precompute `ResourceLocation` constants |
| ANS-OPT-010 | [OverlayDiagnostics.java:86](src/main/java/com/otectus/arsnspells/client/OverlayDiagnostics.java) | `HashSet → stream().sorted()` per call; use `TreeSet` |
| ANS-OPT-011 | [MixinIronsManaBarOverlay.java](src/main/java/com/otectus/arsnspells/mixin/irons/MixinIronsManaBarOverlay.java) | Dual-cancel path with `ManaBarController`; the mixin is redundant — delete it |
| ANS-OPT-012 | [RegenSynergyHandler.java:81-90](src/main/java/com/otectus/arsnspells/events/RegenSynergyHandler.java) | Per-block `getPath().contains("source_jar")` for 324 blocks — compare ResourceLocation directly |
| ANS-OPT-013 | [SanctifiedLegacyCompat.java:743-789](src/main/java/com/otectus/arsnspells/compat/SanctifiedLegacyCompat.java) | Long `if/else` chain of `contains` per cast — precompile into lookup table |
| ANS-OPT-014 | [CrossCastCostResolver.java](src/main/java/com/otectus/arsnspells/spell/CrossCastCostResolver.java) | Dead code — production calculations are inline in `CrossCastingHandler` and diverge by 1 mana from this class's arithmetic |
| ANS-OPT-015 | [CastContext.java](src/main/java/com/otectus/arsnspells/spell/CastContext.java) | Record exists but no callers — adoption deferred to 2.1.0 |
| ANS-OPT-016 | [BridgeManager.java:126](src/main/java/com/otectus/arsnspells/bridge/BridgeManager.java) | `getBridge()` allocates new `ArsNativeBridge` on null path — use a singleton fallback |
| ANS-OPT-017 | [progression/EfficiencyCalculator.java](src/main/java/com/otectus/arsnspells/progression/EfficiencyCalculator.java), [ProgressionTracker.java](src/main/java/com/otectus/arsnspells/progression/ProgressionTracker.java), [SpellSchool.java](src/main/java/com/otectus/arsnspells/progression/SpellSchool.java) | All three are dead code (no callers) |
| ANS-OPT-018 | [ConfigScreenFactory.java:229-244](src/main/java/com/otectus/arsnspells/config/ConfigScreenFactory.java) | "Reset to Defaults" only resets 8 of 90+ keys; rename to "Reset Toggles" or wire a true full reset |

---

# LOW / CLEANUP

| ID | File | Issue |
|---|---|---|
| ANS-LOW-001 | [ClientAffinityPacketHandler.java:11-12](src/main/java/com/otectus/arsnspells/client/ClientAffinityPacketHandler.java) | Dead `mc == null` check (Minecraft.getInstance() never null) |
| ANS-LOW-002 | [AuraBarController.java:50-62](src/main/java/com/otectus/arsnspells/client/AuraBarController.java) | `catch (Exception ignored)` swallows config-read failures silently — log throttled WARN |
| ANS-LOW-003 | [ManaBarController.java:26](src/main/java/com/otectus/arsnspells/client/ManaBarController.java) | Static `loggedOnce` flag never resets between worlds |
| ANS-LOW-004 | [ClientAffinityPacketHandler.java:17-20](src/main/java/com/otectus/arsnspells/client/ClientAffinityPacketHandler.java), [CooldownSyncPacket.java:34-38](src/main/java/com/otectus/arsnspells/network/CooldownSyncPacket.java) | Silent ignore of enum parse failure — log warning so mod-version skew is visible |
| ANS-LOW-005 | [SanctifiedLegacyCompat.java:238-242](src/main/java/com/otectus/arsnspells/compat/SanctifiedLegacyCompat.java) | tickCount wraparound at ~3.4 years — guarded by `>=` check; add a comment explaining the guard |
| ANS-LOW-006 | [CursedRingHandler.java:130,186,228](src/main/java/com/otectus/arsnspells/events/CursedRingHandler.java) | Per-instance tick TTL subtraction without wraparound guard; bounded by Forge tickCount monotonicity |
| ANS-LOW-007 | [ManaInfusionRitual.java:22-26](src/main/java/com/otectus/arsnspells/rituals/ManaInfusionRitual.java) | Hardcoded `findNearestPlayer(8)` — make configurable |
| ANS-LOW-008 | [en_us.json:46,51-52](src/main/resources/assets/ars_n_spells/lang/en_us.json), [MixinScrollItem.java:113,230,248](src/main/java/com/otectus/arsnspells/mixin/irons/MixinScrollItem.java) | Lang keys exist but mixin emits literal `Component.literal(...)` strings — i18n broken for scroll messages |
| ANS-LOW-009 | [ArsNativeBridge.java:12](src/main/java/com/otectus/arsnspells/bridge/ArsNativeBridge.java) | `(float)cap.getCurrentMana()` precision loss at pool > 16.7M — document IManaBridge float boundary |
| ANS-LOW-010 | [CrossCastValidator.java:92-97](src/main/java/com/otectus/arsnspells/spell/CrossCastValidator.java) | `defaultIronCheck` returns true for any ID when Iron's absent; cast path then no-ops — confusing telemetry; return false with clear reason |
| ANS-LOW-011 | [MixinSpellResolverPreCast.java:209-213](src/main/java/com/otectus/arsnspells/mixin/ars/MixinSpellResolverPreCast.java) | RESOURCE_CHECK trace missing from cursed/virtue ring denial paths — inconsistent telemetry |
| ANS-LOW-012 | [CrossCastingHandler.java:100-107](src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java) | Server right-click cancel applies to non-player automation hands — over-broad |
| ANS-LOW-013 | [IManaBridge.java](src/main/java/com/otectus/arsnspells/bridge/IManaBridge.java) | Missing `addMana` method (now needed for ANS-CRIT-003 fix) |
| ANS-LOW-014 | [CrossCastContext.java:165-167](src/main/java/com/otectus/arsnspells/spell/CrossCastContext.java) | `ManaCheckOverride.isUnlimited()` misnamed; the actual semantic is "issPercent == 0 → bypass Iron's check" |
| ANS-LOW-015 | [PacketHandler.java:37-39](src/main/java/com/otectus/arsnspells/network/PacketHandler.java) | `sendToClient` does not null-check `player.connection` — NPE during early login/disconnect |
| ANS-LOW-016 | [CooldownData.java:19-32](src/main/java/com/otectus/arsnspells/data/CooldownData.java) | Persistence key still named `"BridgeCooldowns"` (historic) — comment it |
| ANS-LOW-017 | [CooldownData.java:25-32](src/main/java/com/otectus/arsnspells/data/CooldownData.java) | `load` doesn't `cooldowns.clear()` first — same shape as ANS-MED-012 |
| ANS-LOW-018 | [ResonanceManager.java:69-75](src/main/java/com/otectus/arsnspells/augmentation/ResonanceManager.java) | `cleanupOfflinePlayers` allocates a Set every 1200 ticks — use `removeIf` directly |
| ANS-LOW-019 | [InscriptionInputs.java:104-114,131](src/main/java/com/otectus/arsnspells/rituals/InscriptionInputs.java) | Three `catch (Throwable ignored)` — narrow to `Exception` so `LinkageError` propagates |
| ANS-LOW-020 | [ManaUnificationMode.java:84-91](src/main/java/com/otectus/arsnspells/config/ManaUnificationMode.java) | `fromString` silently falls back to ISS_PRIMARY for unknown values — log WARN on fallback; use `defineInList` at the config |
| ANS-LOW-021 | [IronsCompat.java:20-31](src/main/java/com/otectus/arsnspells/compat/IronsCompat.java) | DCL via `volatile Boolean` works but verbose; use `LazyHolder` idiom |
| ANS-LOW-022 | [build.gradle:3](build.gradle) | `sponge mixin '0.7.+'` floats — pin for build determinism |
| ANS-LOW-023 | [pack.mcmeta:3](src/main/resources/pack.mcmeta) | Description `"Ars 'n' Spells Resources"` differs from mods.toml |
| ANS-LOW-024 | [gradle.properties:1-4](gradle.properties) | `org.gradle.jvmargs=-Xmx3G` + `daemon=false` are user-machine settings, not portable defaults |
| ANS-LOW-025 | [.gitignore:18,24](.gitignore) | Unusual top-level `/com/` and `/META-INF/` patterns — likely leftover from accidental build commit |
| ANS-LOW-026 | [AnsConfig.java:32-33](src/main/java/com/otectus/arsnspells/config/AnsConfig.java) | `respectArmorBonuses` / `respectEnchantments` violate UPPER_SNAKE convention used by all other ForgeConfigSpec statics |
| ANS-LOW-027 | [StartupValidator.java:85-112](src/main/java/com/otectus/arsnspells/util/StartupValidator.java) | `FileChannel.tryLock` can throw `OverlappingFileLockException` (IllegalStateException), not caught by IOException handler |
| ANS-LOW-028 | [CrossCastTrace.java:73-83](src/main/java/com/otectus/arsnspells/util/CrossCastTrace.java) | `formatKv` silently truncates odd-length kv arrays |
| ANS-LOW-029 | [SpellScalingUtil.java:27-50](src/main/java/com/otectus/arsnspells/util/SpellScalingUtil.java) | DCL pattern works (volatile) but `LazyHolder` would be cleaner |
| ANS-LOW-030 | [SpellScalingUtil.java:65-67](src/main/java/com/otectus/arsnspells/util/SpellScalingUtil.java) | Comment claims "prevents exponential stacking" but math is additive only — misleading |
| ANS-LOW-031 | [ArsSpellScalingHandler.java:44](src/main/java/com/otectus/arsnspells/events/ArsSpellScalingHandler.java) | `ACTIVE` map has no logout cleanup — bounded by 60-tick TTL but inconsistent with siblings |
| ANS-LOW-032 | [IronsCooldownHandler.java:14-15](src/main/java/com/otectus/arsnspells/events/IronsCooldownHandler.java) | Method named `onIronsSpellCast` but handles `SpellPreCastEvent` — rename |
| ANS-LOW-033 | [CapabilityResyncHandler.java:91-104](src/main/java/com/otectus/arsnspells/events/CapabilityResyncHandler.java) | 4 sequential per-category packets on every resync — coalesce |
| ANS-LOW-034 | [AffinityHandler.java:28](src/main/java/com/otectus/arsnspells/events/AffinityHandler.java), [IronsAffinityHandler.java:56](src/main/java/com/otectus/arsnspells/events/IronsAffinityHandler.java) | Per-cast AffinitySyncPacket — dirty-flag + tick-throttled sync |
| ANS-LOW-035 | [SafeCasterContext.java:48-55](src/main/java/com/otectus/arsnspells/util/SafeCasterContext.java) | Static `clear()` bypasses try-with-resources — desync risk |

---

# FALSE POSITIVE / NEEDS MANUAL VERIFICATION

| ID | Pre-survey claim | Verdict | Evidence |
|---|---|---|---|
| ANS-FP-001 | [CursedRingHandler.java:44,303,330](src/main/java/com/otectus/arsnspells/events/CursedRingHandler.java) — `ringConflictNotified` set never cleared | **DISPROVEN** | Cleared on logout (line 330: `ringConflictNotified.remove(id)`) AND periodically tick-throttled at line 316. Pre-survey by an Explore agent; lead spot-verified the cleanup paths exist. |
| ANS-FP-002 | Pre-survey #11: `CrossCastRequestPacket.clientSelectedIndex` is untrusted varint | **NOT A BUG** | Server re-reads stack from sender's hand at [CrossCastingHandler.java:79](src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java) and re-validates index via `getSelectedIndex(tag, spellList.size())` at line 133. The packet field is only used by `CrossCastTrace.log` for diagnostic correlation. Defensive bound still recommended (see ANS-MED-006). |
| ANS-FP-003 | Pre-survey #13: `IronsAuraHandler` ms-based TTL vs tick-based cleanup mismatch | **VERIFIED SAFE** | The consume path inside `onIronsSpellCast` independently enforces the TTL ([IronsAuraHandler.java:173](src/main/java/com/otectus/arsnspells/events/IronsAuraHandler.java)). Cleanup is a redundant safety net; expired entries can't be misused even if they linger longer. |
| ANS-FP-004 | Pre-survey #15: `AFFINITY_DECAY_INTERVAL_TICKS` lacks minimum-floor clamp → 1-tick spam | **NOT A BUG** | Config `defineInRange` enforces min=20 at [AnsConfig.java:559](src/main/java/com/otectus/arsnspells/config/AnsConfig.java). The `interval <= 0` guard at [AffinityDecayHandler.java:39](src/main/java/com/otectus/arsnspells/events/AffinityDecayHandler.java) is dead but defensive. |
| ANS-FP-005 | `IronsRegistrySpellDescriptor.ironResolvable` Iron's-less verifier crash | **VERIFIED SAFE** | JVM verifies method bodies lazily on first invocation; the gating at `validate` ensures `ironResolvable` is never called on Iron's-less servers. Fragile to refactor — see [IronsRegistrySpellDescriptor.java:71-73](src/main/java/com/otectus/arsnspells/spell/IronsRegistrySpellDescriptor.java) for the helper-method pattern that prevents eager verification. |
| ANS-FP-006 | `ArsNSpellsClient.java` correctly Dist.CLIENT scoped | **VERIFIED OK** | Annotation present at line 19. |
| ANS-FP-007 | `AuraBarController` overlay listeners removed on disconnect | **VERIFIED OK** | `ClientAuraState.reset()` called in `LoggingOut` handler at [AuraBarController.java:127-129](src/main/java/com/otectus/arsnspells/client/AuraBarController.java). |
| ANS-FP-008 | `CrossCastingHandler.onRightClickItem` hand prediction matches server | **VERIFIED OK** | Server re-reads via `sender.getItemInHand(hand)` ([CrossCastRequestPacket.java:79](src/main/java/com/otectus/arsnspells/network/CrossCastRequestPacket.java)). |
| ANS-FP-009 | `ManaUnificationMode` enum stability — values not reordered | **NEEDS MANUAL VERIFICATION** | No CHANGELOG entry indicates reordering; verify by diffing against 1.0.0 if seed configs need to remain readable. |
| ANS-FP-010 | `LPDeathPrevention` between-event race (pre-survey #19) | **PARTIAL** | The same-tick scope (`player.tickCount != transaction.playerTickCount`) bounds the exploit window. The damage-type filter at line 109-117 is over-narrow (see ANS-HIGH-022); third-party magic types may slip through. Real exploit window is bounded but non-zero. |
| ANS-FP-011 | `EquipmentIntegration.equipmentCache` is HashMap (pre-survey #17) | **CONFIRMED at HIGH** | Promoted to ANS-HIGH-014 with patch. |
| ANS-FP-012 | `spell_transcription.json` line 14 Iron's hardcode (pre-survey #16) | **CONFIRMED at HIGH** | Promoted to ANS-HIGH-001 with patch. |
| ANS-FP-013 | `PacketHandler.register()` protocol version handling | **VERIFIED OK** | Uses strict client/server equality predicates; old clients rejected at connection time. |
| ANS-FP-014 | Every packet `handle()` calls `setPacketHandled(true)` | **VERIFIED OK** | All five packets call it outside enqueueWork on every reachable path; the `ctx == null` early returns are theoretical only (Forge never returns null ctx for live packets). |
| ANS-FP-015 | LazyOptional.invalidate on entity removal | **VERIFIED OK** | Forge 1.20.1 auto-invalidates capabilities on entity discard. Custom `AuraCapabilityProvider.invalidate()` is dead but harmless. |
| ANS-FP-016 | AuraCapability regen runs server-side only | **VERIFIED OK** | `onPlayerTick` early-returns on `isClientSide`. |
| ANS-FP-017 | Every CooldownData mutation has CooldownSyncPacket follow-up | **PARTIAL** | True for `applyCooldown` (live mutation path). `clearCooldown(s)` are missing follow-ups — see ANS-MED-015. |
| ANS-FP-018 | `mods.toml` `ordering="AFTER"` correct | **VERIFIED OK** | Iron's-gated registration happens after Iron's loads ([ArsNSpells.java:60-96](src/main/java/com/otectus/arsnspells/ArsNSpells.java)). |
| ANS-FP-019 | `pack.mcmeta` formats correct for 1.20.1 | **VERIFIED OK** | `pack_format=15`, `forge:data_pack_format=12` — both correct. |
| ANS-FP-020 | `build.gradle` Java 17 toolchain consistency | **VERIFIED OK** | `JavaLanguageVersion.of(17)`, `options.release = 17`, mixin `compatibilityLevel = JAVA_17` all agree. |
| ANS-FP-021 | Pre-survey #6: `MixinSanctifiedAbstractSpell` gating mismatch | **CONFIRMED at HIGH** | Promoted to ANS-HIGH-009 with explicit Sanctified probe. |
| ANS-FP-022 | Pre-survey #5: SEPARATE-mode one-way drain in MixinSpellResolverMana TAIL | **CONFIRMED at CRITICAL** | Promoted to ANS-CRIT-002 with patch. |

---

# Top-10 Fix Order

Selected for highest (severity × ease-of-fix × independence) score. Apply in this order — no two diffs in the top 10 touch the same hunk:

| # | ID | File | Why first | Diff size |
|---|---|---|---|---|
| 1 | ANS-CRIT-001 | `ArsNSpellsMixinPlugin.java` | One-line fix; unblocks Iron's-less servers entirely | 8 lines |
| 2 | ANS-HIGH-001 | `spell_transcription.json` | One-file fix; resolves recipe load failure | rewrite |
| 3 | ANS-CRIT-004 | `CrossCastingHandler.java` | One-annotation change (`priority = HIGHEST`); unblocks core 2.0.0 economy | 5 lines |
| 4 | ANS-HIGH-014 + ANS-HIGH-015 | `EquipmentIntegration.java` + `RegenSynergyHandler.java` | Two trivial `HashMap → ConcurrentHashMap` swaps; same pattern | 2× 2 lines |
| 5 | ANS-HIGH-005 | `AuraSyncPacket.java` + `ClientAuraState.java` + `PacketHandler.java` | Network attack surface; clamping is trivial; packet direction guard is a 3-line change | ~25 lines |
| 6 | ANS-HIGH-006 + ANS-HIGH-007 | `ResonanceSyncPacket.java` + `ResonanceManager.java` | Symmetric NaN/Infinity fences; both small | ~15 lines |
| 7 | ANS-HIGH-013 | `PacketHandler.java` | Add 4 direction guards (covers ANS-HIGH-005's packet half too) | 8 lines |
| 8 | ANS-CRIT-002 + ANS-CRIT-003 | `MixinSpellResolverMana.java` + `BridgeManager.java` + `IManaBridge.java` + bridge impls | The SEPARATE-mode mana drain pair; landed together since they share the IManaBridge.addMana addition | ~40 lines |
| 9 | ANS-HIGH-008 | `ModCapabilityProvider.java` + `AuraCapabilityProvider.java` | Two single-line `priority = HIGHEST` adds; hardens against future modpack mods | 4 lines |
| 10 | ANS-HIGH-011 | `CursedRingHandler.java` + `VirtueRingHandler.java` | Switch from per-player tickCount to `level.getGameTime()`; cross-player TTL bug fix | ~12 lines |

**Apply order rule**: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10. Items 5 and 7 both touch `PacketHandler.java`; #7 supersedes #5's PacketHandler hunk when sequenced after.

---

# Dedicated-Server Test Checklist

1. **Cold boot without Iron's** — `run/mods/` contains only `ars_nouveau` + `ars_n_spells`. Server must reach world load. Grep `latest.log` for `NoClassDefFoundError` (must be absent), `Failed to load apparatus recipe` (must be absent post-ANS-HIGH-001 fix), `[SelfCheck]` (must skip cleanly without errors).
2. **Cold boot with Iron's** — Standard load. `[SelfCheck] Iron's ring integration:` line must read `MagicDataAccessor=OK | IronsAuraHandler=OK | IronsLPHandler=OK | MixinIronsCastValidation=OK`.
3. **AuraSyncPacket bounds** — Send `AuraSyncPacket(Integer.MAX_VALUE, 1)` to server from a test client; assert no handler invocation (post-direction-guard fix) and no static-field write on server-side `ClientAuraState`.
4. **ResonanceSyncPacket NaN** — Send `ResonanceSyncPacket(Float.NaN)` and `(Float.POSITIVE_INFINITY)`; assert client `ResonanceManager.getResonance()` remains finite (default 1.0).
5. **Cross-cast multiplier with ring** — Equip Cursed Ring; cross-cast inscribed Ars spell with base mana 100, `cross_cast_cost_multiplier = 1.25`. Assert pending LP cost = LP-for-125-mana, not LP-for-100-mana.
6. **SEPARATE-mode mana drain** — SEPARATE mode, set Ars mana = 100, Iron's mana = 5, cross-cast inscribed Ars spell with ISS-share-of-cost = 10. Pre-fix: Ars drops to 50, Iron's stays at 5. Post-fix: cost-calc denies; both pools unchanged.
7. **Dual-cost rollback with regen** — SEPARATE mode, drink regen potion, repeatedly cross-cast cross-cast spells that fail on Iron's; assert Ars pool tracks regen correctly (no overwrite).
8. **PlayerEvent.Clone race** — Add a `@SubscribeEvent(priority = LOWEST)` listener that reads `AffinityData` cap on respawn; assert post-fix it sees copied state, not default.
9. **Config save thread freeze** — Lock the config file (open in editor with exclusive lock), then run `/ans mana setdefault 200`; assert command returns instantly (post-async-save fix) and the failure is logged.
10. **`/ans` command** — All subcommands respond on a dedicated server with no NPE.

# Large-Modpack Compatibility Checklist

(Assumes 200+ mod loadout with Curios, Iron's Spellbooks, Sanctified Legacy, Apotheosis, Magitech mods.)

1. **Mixin plugin gating** — Verify `MixinIronsCastValidation`, `MagicDataAccessor`, and `MixinSanctifiedAbstractSpell` all gate correctly on the right combination of `ironsPresent` / `sanctifiedPresent` (post-ANS-CRIT-001 and ANS-HIGH-009 fixes).
2. **Datapack reload** — Run `/reload`; verify rituals re-splice (post-ANS-MED-022 fix) and no recipe errors.
3. **Iron's mixin redirect collision** — Confirm no other mixin from a third-party mod redirects `MagicData.getMana()` inside `canBeCastedBy`; if so, the silent-keep-one Mixin behavior would break our cross-cast pre-validation. Grep modpack jars for `@Redirect.*MagicData.*getMana` collisions.
4. **Curios scan compat** — Verify `SanctifiedLegacyCompat.scanCurios` does not log-flood when a Curios API version mismatch produces exceptions (post-ANS-MED-023 fix).
5. **Cross-mod XP event** — Cast an Ars spell, then an Iron's spell; verify both affinity and progression caps update correctly and sync to client.
6. **Aura with Iron's-spell Virtue Ring** — Cast a legendary Iron's spell with Virtue Ring active; assert aura cost is reasonable (post-ANS-HIGH-018 fix; not 10× over).
7. **Iron's spell damage with Resonance** — Verify resonance never exceeds `MAX_DAMAGE_MULTIPLIER` cap, even with third-party MagicData manipulation (post-ANS-HIGH-007 fix).
8. **Config screen on remote server** — Open the config screen on a client connected to a dedicated server; assert sliders are read-only or that changes propagate (post-ANS-HIGH-016 fix).
9. **PlayerEvent.Clone priority** — With 200+ mods loaded, run respawn 10× and verify affinity/cooldown/progression/aura caps survive (post-ANS-HIGH-008 fix).
10. **Long-running soak** — 8-hour soak with 30+ players cross-casting and using rings; profile heap for `equipmentCache` / `lastLogMs` / `CrossCastContext.ACTIVE_CASTS` growth.

# Regression Checklist

## Mana
- [ ] SEPARATE-mode dual-cost: Ars and Iron's drain proportionally; rollback preserves concurrent regen (ANS-CRIT-002, ANS-CRIT-003).
- [ ] ISS_PRIMARY: Ars spells consume Iron's mana via bridge; bridge failure cancels cast (ANS-MED-010).
- [ ] ARS_PRIMARY: Iron's spells consume Ars mana scaled by pool ratio, not flat conversion rate (ANS-HIGH-012).
- [ ] Cross-system regen preserves pool ratios (existing ManaRegenBridge correctness — verify still passing).
- [ ] Mana sync packets respect direction guards (ANS-HIGH-005, ANS-HIGH-013).
- [ ] Float precision: mana validation does not deny by 0.0001 epsilon (ANS-MED-011).

## Cooldowns
- [ ] Cooldown applies and syncs across login/respawn/dim change (existing CapabilityResyncHandler).
- [ ] `clearCooldown(s)` sends sync packet (ANS-MED-015).
- [ ] Cooldown survives server restart but resets on death (intentional design).
- [ ] CooldownSyncPacket payload bounded against `Long.MAX_VALUE` (ANS-MED-017).

## Spells & projectiles
- [ ] Ars cost-calc multiplier applied before ring zeroing (ANS-CRIT-004).
- [ ] Cross-cast context cleared on cast exception (ANS-MED-002).
- [ ] Cross-cast multiplier one-shot under concurrent fires (ANS-HIGH-004).
- [ ] Cursed/Virtue ring LP/aura proportional to multiplied cost (depends on ANS-CRIT-004).
- [ ] Resonance multiplier finite and bounded (ANS-HIGH-006, ANS-HIGH-007).

## Capabilities
- [ ] AffinityData / CooldownData / ProgressionData survive respawn (ANS-HIGH-008).
- [ ] Aura cap server-only attached (ANS-MED-014).
- [ ] Cap data clears on logout where designed (ANS-HIGH-021, ANS-HIGH-025).
- [ ] `loadFromNBT` clears state before load (ANS-MED-012).

## Networking
- [ ] All 4 S2C packets have `NetworkDirection.PLAY_TO_CLIENT` (ANS-HIGH-013).
- [ ] Server→client payloads bounded (ANS-HIGH-005, ANS-HIGH-006, ANS-MED-016, ANS-MED-017).
- [ ] `setPacketHandled(true)` called on all reachable paths (verified).
- [ ] `CrossCastRequestPacket` re-reads stack server-side (verified).
- [ ] `ClientAffinityPacketHandler` has `@OnlyIn(Dist.CLIENT)` (ANS-HIGH-019).

## Optional-mod integrations
- [ ] Iron's-less server boots without crash (ANS-CRIT-001).
- [ ] `spell_transcription.json` recipe gated (ANS-HIGH-001).
- [ ] `MixinSanctifiedAbstractSpell` gated on Sanctified presence (ANS-HIGH-009).
- [ ] Iron's-importing classes never classloaded on Iron's-less server (ANS-HIGH-002).
- [ ] `EquipmentIntegration` reflection replaced with direct call (ANS-MED-021).

---

# Especially Fragile Files (3-7)

Selected by `severity_score = sum(CRIT=5, HIGH=3, MED=1, OPT=0.25)` per file, intersected with [git log churn](#) over the last 3 months.

| Rank | File | Score | Why |
|---|---|---:|---|
| 1 | [src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java](src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java) | **17** | 6 commits in 3 months. Houses `onArsSpellCost` (ANS-CRIT-004), the cross-cast packet dispatch, the cost-calc multiplier, and the right-click event. Touches three event types (`SpellCostCalcEvent`, `PlayerInteractEvent.RightClickItem`, `PlayerTickEvent`) and the cross-cast NBT. Any 2.0.x change here risks breaking the ring + cross-cast interaction.
| 2 | [src/main/java/com/otectus/arsnspells/bridge/BridgeManager.java](src/main/java/com/otectus/arsnspells/bridge/BridgeManager.java) | **13** | 4 commits. The mana spine — every cast path eventually reaches `consumeManaForMode` or `getManaForMode`. Houses ANS-CRIT-003 (dual-cost rollback) and the mode dispatch.
| 3 | [src/main/java/com/otectus/arsnspells/config/AnsConfig.java](src/main/java/com/otectus/arsnspells/config/AnsConfig.java) | **13** | 9 commits (highest churn). 90+ config keys; 6 dead, 4 with wrong bounds. Modpack tunability requires this be solid; today it's the source of UX traps (ANS-HIGH-027, ANS-MED-031..035).
| 4 | [src/main/java/com/otectus/arsnspells/events/CursedRingHandler.java](src/main/java/com/otectus/arsnspells/events/CursedRingHandler.java) | **9** | 6 commits. Houses ANS-HIGH-011 (cross-player tickCount mixup), interacts with `LPDeathPrevention`, `CasterContext`, `CrossCastingHandler`. Touched on every Ars cast.
| 5 | [src/main/java/com/otectus/arsnspells/events/IronsAuraHandler.java](src/main/java/com/otectus/arsnspells/events/IronsAuraHandler.java) | **8** | Iron's-side mirror of CursedRingHandler; same priority + LP/aura rarity ladder bugs (ANS-HIGH-018, ANS-HIGH-020). Per-cast log spam.
| 6 | [src/main/java/com/otectus/arsnspells/equipment/EquipmentIntegration.java](src/main/java/com/otectus/arsnspells/equipment/EquipmentIntegration.java) | **7** | 4 commits. HashMap thread-safety issue + reflection antipattern + cache TTL alignment. Called from every mana attribute calc.
| 7 | [src/main/java/com/otectus/arsnspells/compat/SanctifiedLegacyCompat.java](src/main/java/com/otectus/arsnspells/compat/SanctifiedLegacyCompat.java) | **6** | 4 commits. Reflection-heavy, holds the Curios scan cache, ring detection logic, blasphemy multipliers. Per-cast read path; any throw cascades log-floods.

Three of the seven (`CrossCastingHandler`, `CursedRingHandler`, `IronsAuraHandler`) are direct consequences of the 2.0.0 release scope (cross-cast + rings + aura). They are the load-bearing surface for the new feature set and warrant focused gametest coverage in the next minor (`gametest/CrossCastGameTests.java` is currently a scaffold — ANS-OPT-001).

---

*Audit produced by 6 parallel deep-audit sub-agents + lead spot-verification. Token-confirmed evidence for every CRITICAL and HIGH finding. False positives explicitly retired with counter-evidence. Patches use unified-diff format with ≥3 lines of context; apply in the top-10 order above to avoid hunk collisions.*

---

# v2.6.0 Verification Pass (2026-06-18, fixes shipped in 2.6.1)

A fresh full-codebase review (branch `1.20.1`, head `2a4aeb1` = Release 2.6.0) re-checked every CRITICAL/HIGH from the v2.0.0 audit above against the current source, and verified a broad new agent sweep. The fixes resulting from this pass ship in **2.6.1**. **Most v2.0.0 ship-blockers are now resolved**, and the new sweep's "critical" candidates were overwhelmingly false positives (the agents conflated this historical document with the current code). Below is the reconciled state.

## Confirmed RESOLVED since v2.0.0

- **ANS-CRIT-001** (mixin gating crash on Iron's-less servers) — FIXED. `ArsNSpellsMixinPlugin.shouldApplyMixin` now gates `MixinIronsCastValidation` and `MagicDataAccessor` alongside the other Iron's mixins (`mixin/ArsNSpellsMixinPlugin.java:62-72`).
- **ANS-CRIT-003** (dual-cost rollback overwrote concurrent regen) — FIXED. `BridgeManager.consumeManaForMode` now refunds via the atomic `addMana` compensating add, not snapshot+`setMana` (`bridge/BridgeManager.java`, SEPARATE branch).
- **ANS-HIGH-011** (cross-player `tickCount` mixup) — FIXED in the ring/aura handlers, which now stamp pending costs with server-global `level().getGameTime()` (`events/VirtueRingHandler.java`, `events/CursedRingHandler.java`).
- Atomic `addMana` exists and is used by `ArsNativeBridge`/`IronsBridge` (no get+set race on the bridge layer).
- `ClientAuraPeakTracker.getPeak()` is floored at `1` (`PEAK_FLOOR`) — no divide-by-zero possible in the Covenant overlay mixin divisor.
- `VirtueRingHandler.onSpellResolvePost` `return`s after the TTL-expiry check — no post-TTL aura consumption.

## Newly confirmed issues (fixed in this pass)

- **Racy `addMana` in the Iron's mana mixin** — `mixin/irons/MixinIronsMagicDataMana.java` `arsnspells$addMana` did `getMana()`+`setMana(current+amount)` in ARS_PRIMARY mode, the exact race the bridge layer eliminated; concurrent regen between read and write was clobbered. Now delegates to `BridgeManager.getBridge().addMana(player, amount)`.
- **Dual-cost split not normalized at consumption** — `bridge/BridgeManager.java` SEPARATE branch read `DUAL_COST_ARS/ISS_PERCENTAGE` raw; a configured split summing to ≠1.0 silently over/under-charged every cast (the sum check was init-time WARN only). Now normalized so `arsCost + issCost == amount`.
- **Stale "requires a game restart" messaging** — `BridgeManager.logInitialization` log line and `getCurrentMode` javadoc contradicted the 2.0.1 live `refreshMode()` feature. Updated to reference `/ans mode set` / config screen.
- **Broken SLF4J placeholder** — `events/CurioDiscountHandler.java:99` used `{:.1f}%` (the twin of the already-fixed `ANS-MED-026` at line 73), silently dropping the percent arg. Now uses `String.format` + `{}`.

## Minor consistency / defensive nits (fixed in this pass)

- `compat/SanctifiedLegacyCompat.java` curio-state cache migrated from per-player `tickCount` to server-global `gameTime` (`CurioState.cachedAtTick` is now `long`), for consistency with `ANS-HIGH-011`. (Was self-healing, not a live bug.)
- `bridge/ArsNativeBridge.java` and `bridge/IronsBridge.java` `setMana`/`consumeMana` gained the `player == null` guard their `addMana`/`getMaxMana` siblings already had.
- `network/CrossCastRequestPacket.handle` gained the null-`ctx` guard its sibling packets already had (Forge guarantees non-null in practice; consistency hardening).

## False positives from the v2.6.0 agent sweep (retired)

The broad sweep flagged these as CRITICAL/HIGH; direct source verification showed each was already handled or never real. Recorded here to prevent re-litigation:

- "`ArsNativeBridge.setMana/consumeMana` NPE / non-atomic addMana" — addMana is already atomic; null guards now added but no crash path existed in practice.
- "`ClientAuraPeakTracker` divide-by-zero / negative peak" — floored at 1; `updatePeak` only called with `current > 0`.
- "`IronsBridge.getMana` swallows exceptions silently" — it logs via per-op `logCriticalError` (`ANS-MED-007`).
- "`VirtueRingHandler` TTL doesn't prevent consumption" — it `return`s after the TTL check.
- "Mixin plugin missing Iron's gating (`ANS-CRIT-001`)" — already fixed.

## Still open / unverified (carried forward)

- **ANS-CRIT-002** (SEPARATE-mode one-way Ars drain when the Iron's side fails) and **ANS-CRIT-004** (cross-cast cost multiplier zeroed by rings) were not re-deep-verified in this pass and remain on the cross-cast spine (`spell/CrossCastingHandler.java`). Recommend a focused gametest before the next release.
