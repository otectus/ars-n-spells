# Ars 'n' Spells — 1.21.1 NeoForge port to 3.2.0 parity

**Date:** 2026-08-25 · **Branch:** `port/1.21.1-neoforge` · **Baseline:** `20953dd`
("Release Ars 'n' Spells 3.0.2 — 3.0.x parity port") · **Target:** the Forge 1.20.1 3.2.0
working tree at `C:\Projects\ars-n-spells`.

## 0. Verdict

The NeoForge build is at feature parity with Forge 3.2.0 with **one documented exception**:
the Covenant of the Seven LP/aura subsystem, which has no 1.21.1 dependency to compile or run
against (§4).

Verified green: `compileJava`, `test` (**22 classes / 140 tests, 0 failures**),
`runGameTestServer` on the Iron's-less profile (**9/9 required tests passed**, zero mixin
failures, zero datapack parse errors), and `runServer` (world generates, Ars Nouveau loads,
rituals and payloads register, server config written with 72 keys across 18 sections).

**Not yet exercised: a running client.** Everything below was validated headlessly. The
client-only surface — the config screen, the Spell Loom screen, the mana-bar controller, the
wheel-icon mixin, tooltips — needs a human at `runClient`; see §5.

## 1. What the baseline actually was

The branch was labelled 3.0.2 but was further behind than that implies: **108 Java files
against the Forge tree's 195.** It had never received several 3.0.x systems, let alone 3.1.0
or 3.2.0.

A second trap: **Forge 3.2.0 was never committed.** `main` is at 3.1.0 and every 3.2.0 change
lives only in that repo's working tree. Porting from a tag would have silently dropped the
creative tab, the shared-pool ceiling, the tablet artwork and the mana-ritual tablets. (The
same failure mode already cost this project 3.0.3, which shipped to CurseForge and was
reconstructed by decompiling the jar.)

## 2. What the baseline had already solved — and was kept

These were done well and were **not** re-derived from the Forge sources:

| Migration | Files |
|---|---|
| ItemStack NBT → DataComponents | `ModDataComponents`, `CrossModSpell`, `CrossModSpellComponents`, `CrossModSpellList` |
| Forge capabilities → attachments | `AttachmentTypes` |
| SimpleChannel → payloads | the four `*Payload` classes |
| Ars 5.x `ISpellCaster` removal | `CasterContext`, `SafeCasterContext` |
| Dynamic school discovery | `SchoolKeys`, `SchoolIndex`, string-keyed `AffinityData` |

The last one is a **deliberate divergence kept in place**: 1.20.1 keyed affinity on a
16-value enum, this branch keys it on full school ids so addon schools are tracked. That is a
superset. Only the *resolution* step was replaced (§3), not the storage.

## 3. Defects found and fixed

Ordered by severity. Every one was present in the baseline.

### 3.1 Hybrid mode destroyed mana on every cast

`applyArsBonusesToIrons` pushed only `PerkAttributes.MAX_MANA` into Iron's `max_mana` — the
gear/perk slice, not the base pool or book tier. `MagicData.setMana` clamps **every** write
down to that attribute, so a ceiling below the current pool does not cap mana, it deletes it.
Fixed with `SharedPoolCeiling` + `arsRealMaxMana` + `ensureSharedPoolCeiling`, plus a
self-audit in `IronsBridge.consumeMana`.

### 3.2 A bound spell could silently become a different spell

Ars 5.x changed this failure mode and made it worse. On 1.20.1, `Spell.fromTag` *skipped* a
glyph whose mod was gone. In 5.x `AbstractSpellPart.CODEC` is
`ResourceLocation.CODEC.xmap(GlyphRegistry::getSpellPartOrDefault, …)` and
`getSpellPartOrDefault` returns **`EffectBreak.INSTANCE`** — so the recipe keeps its length
and the missing glyph is substituted with Break. Removing an addon turns a bound fire spell
into one that breaks blocks. `ArsSpellIntegrity` was rewritten for the new serialized shape
(`recipe` is a list of id strings, not `size` + `part0..N`) and gated at the cast path, the
binding ritual and the bind command.

### 3.3 The Inscription Table crashed, including on dedicated servers

Iron's dereferences `ISpellContainer.get` unguarded in `InscriptionTableScreen.onInscription`,
`InscriptionTableMenu.clickMenuButton` and `doInscription`. ANS carriers were built with no
native container. Fixed on both sides via `IronsInscriptionPolicy` + `IronsScrollFactory` +
`CarrierReconciler`.

### 3.4 Cross-casting was client-authoritative

The interact handler resolved and cast on both sides from the client's own stack and index.
Replaced with the `cross_cast_request` payload; the server re-reads the held stack. Protocol
`"2"` → `"3"`.

### 3.5 A spellbook in the Curios slot cast nothing

`ArsCrossProxySpell.onCast` read only `MagicData.getPlayerCastingItem()` — empty for that slot
— and returned silently. Fallback chain and both failure messages restored.

### 3.6 Proxy casts were billed twice

`isArsCrossProxyId` had no counterpart here, so every `ars_cross_*` wheel cast was charged
Iron's affinity, cooldown, progression **and** the cross-cast multiplier, filed under the
placeholder's Ender school.

### 3.7 Affinity decayed ~20× faster than documented

`Math.max(1, floor(level * perInterval))` stripped a flat point per interval.
`DecayAccumulator` carries the residual, re-keyed to school ids and persisted as an optional
`AffinityData` codec field.

### 3.8 School classification was the bare substring heuristic

No datapack override, no read of Ars' own `spellSchools` metadata, Firework classified as
fire. Also: `AbstractFilter extends AbstractEffect`, so filter glyphs were claimed as the
first effect — `Projectile → Sensitive → Ignite` was classified by *Sensitive*.

### 3.9 Smaller, but real

- The cross-cast multiplier did not apply when unification was off (it applies in every mode).
- ARS_PRIMARY used a flat conversion rate, so a 50-mana Iron's spell cost half the Ars pool.
- Both mana rituals wrote straight into Iron's `MagicData` — dead on an Ars-only install.
- Mana Infusion and Mana Well had no tablet item and no ritual-map splice: unobtainable.
- Spellbook detection matched `"spell_book"` in the registry path; the
  `ars_n_spells:irons_spell_books` tag shipped in the 1.20.1 F1 fix had never been read.
- Source Jar detection matched `"source_jar"` in the registry path.
- `scroll_cost_mode=full` validated a cost and charged nothing.
- The tooltip handler caught only `Exception`, so a linkage `Error` crashed the client.
- Proxies had no `allowCrafting=false`, so they appeared in Iron's Scroll Forge.
- `/ans bind_scroll_to_irons_book` reported "failed" for a full book.
- **`gradle-wrapper.jar` was never committed** — `.gitignore`'s blanket `*.jar` swallowed it,
  so a fresh clone had no working wrapper. `run-server/`/`run-data/` were also unignored.

## 4. The one parity gap: Covenant of the Seven

Covenant has **no 1.21.1 release** — CurseForge lists 1.20.1/Forge only, newest file March
2026, no public source repository. The subsystem it powers (Cursed Ring LP, Virtue Ring aura,
13 Blasphemy curios, the aura HUD rewiring, Blood Magic and Nature's Aura reflection bridges)
therefore cannot be compiled or tested.

The previous porting attempt deleted it (`72ee2a0`). This port instead preserves the 1.20.1
sources under `src/covenant-disabled/` — not a source set, so nothing is compiled or shipped —
with a README describing what porting each file would take. The ~24 config keys stay declared
and generated, marked `INERT`, so an existing server TOML round-trips unchanged, and the three
places the subsystem hooks in are marked in place with what belongs there and why the ordering
matters.

**Consequences while blocked:** `scroll_cost_mode=lp_only` behaves as `free`; ring and
blasphemy keys have no effect; `/ans aura` reports nothing useful;
`hide_mana_bar_with_ring` never triggers.

**Also blocked:** Too Many Glyphs has no 1.21.1 build, so the `-PwithTooManyGlyphs` GameTest
profile has no counterpart. Ars Elemental's profile is ported (`-PwithArsElemental`).

## 5. Residual risk — what a human still needs to check

Everything below is client-side and cannot be asserted headlessly. This is the same gap the
MCA: Quests port audit flagged, and both of that port's P1 bugs were client rendering
regressions invisible to unit tests.

1. `./gradlew runClient` with Ars + Iron's. Walk: craft the Spell Loom → transcribe a spell →
   bind to a spellbook → cast from Iron's wheel with the custom name and icon → uninscribe.
2. All six items appear in the `ars_n_spells:general` tab **with real textures** (the 3.2.0
   checkerboard bug), and none appear in Ars Nouveau's tab.
3. Both mana ritual tablets resolve on a brazier.
4. Hovering a Spell Loom carrier does not crash.
5. Put a carrier scroll in Iron's Inscription Table: it must be refused with a message, not
   crash, and the scroll must not be consumed.
6. The config screen opens from the mod list and is legible; controls are read-only on a
   dedicated server.
7. The mana bar shows the right pool per mode.
8. Join a dedicated server from the dev client and confirm the cross-cast flow works over the
   wire.

## 6. Verification commands

```bash
./gradlew build
```

```bash
./gradlew runGameTestServer
```

The GameTest job's exit code proves nothing — `runGameTestServer` reports `BUILD SUCCESSFUL`
even when the server crashes at boot or collects zero tests. Assert on the log:

```bash
grep "required tests passed" run/logs/latest.log
```

`rm -rf run/world` between profile switches: an Iron's-loaded run leaves an
`irons_spellbooks:pocket_dimension` reference the Iron's-less run cannot load.
