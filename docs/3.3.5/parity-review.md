# Forge 1.20.1 / NeoForge 1.21.1 parity (3.3.5)

Reviewed on 2026-09-29 against both working trees at 3.3.5: the Forge 1.20.1 checkout
(`main`) and the NeoForge 1.21.1 checkout (`port/neoforge-1.21.1`). This file is identical in
both checkouts. It supersedes the 3.3.4 review, which compared only the Forge-to-NeoForge
direction and left the NeoForge-only features out of scope.

## Scope

The two builds now ship the same features, configuration keys (with the same defaults, bounds
and comments), commands, datapack tags, translations and GameTest coverage. What remains
different falls into two classes, and `parity-inventory.json` lists every instance.

**Integrations whose mod has no build for the other Minecraft version.**

| Mod | Exists for | Not present on the other build |
| --- | --- | --- |
| Covenant of the Seven | 1.20.1 only | Cursed Ring LP, Virtue Ring aura and Blasphemy discounts: their 25 config keys, `/ans aura`, the ring-bypass line of `/ans info`, the ring and Blasphemy tags, 13 translations, three Covenant mixins and one GameTest. |
| Too Many Glyphs | 1.20.1 only | Two addon GameTests and the mixed-addon recipe test. |
| Ars Affinity | 1.21.1 only | The four interoperability GameTests. ANS has no Affinity integration code on either build. |
| Ars Elemancy | 1.21.1 only | Three addon GameTests. |

**Loader-native mechanics that implement the same behaviour.** Capabilities and stack NBT on
Forge, data attachments and data components on NeoForge; `SimpleChannel` packets versus
payloads (each loader keeps its own protocol number, 6 and 7); `mods.toml` versus
`neoforge.mods.toml`; the renamed data folders (`recipes`/`recipe` and so on); `UUID` versus
`ResourceLocation` attribute-modifier identities, both mapped from the shared
`AnsModifierIds` keys; Ars 4's `IManaEquipment` fallback, which Ars 5 removed; Iron's 1.21.1
loot tables (`catacombs/crypt_loot` no longer exists, and NeoForge also injects
`dead_king_vault`); the NeoForge placement of `config_schema_version` and `conversion_policy`,
which is kept so existing files keep their values; and GameTests whose subject is one loader's
storage (pre-3.0 NBT carriers, component round trips). The Forge-only
`-PwithIrons316RuntimeGameTests` profile exists because the Forge build compiles against Iron's
3.15.0; the NeoForge build compiles against 3.16.3.

## Changes on the NeoForge build

- **Source Jar synergy was never registered** on the event bus, so it did nothing. It now
  runs, as on Forge.
- **`iss_primary` gear bonus.** Iron's max mana was raised to Ars's whole real maximum. It now
  receives the Ars gear bonus times `conversion_rate_ars_to_iron`. `hybrid` and `ars_primary`
  keep the shared-pool ceiling, and `hybrid` keeps it with `respect_armor_bonuses` off. The
  enchantment and curio toggles no longer lower the ceiling.
- **Ars potions.** Ars gear regen mirroring counts only worn equipment and curios. The Ars Mana
  Regen potion is mirrored separately in `iss_primary` (`PotionContributions`, 0.5 mana/sec per
  level) and removed on a mode change.
- `SharedPoolCeiling` gained the amplification-aware overloads. `ArsManaCalcHandler` folds
  Iron's gear only on the server and clamps at zero.
- `AnsModifierIdentities` and `AnsFeatureCleanup` implement the shared `ModifierKeyMapper` and
  `FeatureCleanup` contracts. `ModeChangeCleanup` removes legacy identities and logs clamps.
- `UnifiedCooldownManager`'s clear methods sync the client. A vetoed cast releases an alternative reservation.
  Cycling a single-spell item reports "1/1". `ArsCastPayments.canAfford`, `CastingAuthority`
  and `parseConversionPolicy` match Forge. Rituals register in Forge's order.
- The casting smoke harness (`-PwithCastingClientSmoke`, `-PwithCastingServerSmoke`,
  `-PcastingSmokeAddress`) and `runServer -PgametestRunDir`.

## Changes on the Forge build

- **Cross-mod combat bridge in both directions.** The Ars Spell Damage Bonus perk now adds to
  Iron's spell damage. `multi_school_power_policy`, the three combat toggles, `/ans debug combat`
  and `CombatDebugState` were added, and multi-school resolution (`SchoolResolver.resolveAll`,
  `SpellAnalysis.schools()`/`allSchoolKeys()`) supports them. `ArsDamageBridge` replaces
  `ArsSpellScalingHandler`.
- **Tagged-curio discount** for Ars and Iron's casts (`#ars_n_spells:curio_spell_discount`,
  `virtue_ring_discount`, `max_total_curio_discount`), applied before Covenant's Blasphemy
  discount.
- **`#ars_n_spells:cross_cast_blacklist`**, enforced at the Spell Loom, `/ans export_to_irons_scroll`,
  Spell Transcription and every cross-cast. The `ans_glyph_schools/ars_zero.json` data file was also
  added; it produces the same schools as the built-in table.
- `/ans info` gained the affinity and Iron's-school lines. `StateEvictionHandler` clears
  per-player state at server stop. The "spell selected" message is translatable, and cross-cast
  refusals are shown in red. The startup lock check names the current config file.
- `CompatIds`, `ModPresence`, `CuriosAccess`, `SchoolIndex` and `IronsAttributeReport` were
  added, as on NeoForge.

## Changes on both

- Fifteen config comments differed. Thirteen now match, taking whichever text was more
  accurate. The other two (`enable_curio_discounts` and `scroll_cost_mode`) keep a
  Covenant-specific sentence on the build it applies to.
- Four translation texts were aligned. Each build's translation file now has every key of the
  other, except the Covenant keys.
- New shared tests: `ParityBehaviourGameTests`. Tests ported so that both builds run them:
  combat bridge, mode-change cleanup, shared-pool ledger, named addon glyph schools, proxy
  casts, bind command, carrier and unbinding, ghost scrolls, Curios toggle, uninscription
  teardown, pool reuse, reconciler, `SharedPoolCeilingAmplificationTest`,
  `SchoolResolverAddonTest`, `SpellScalingFormulaTest`, `CombatDebugStateTest`,
  `ManaRegenBridgeTest`, `TagDrivenDetectionTest` and the portable half of
  `ConfigSchemaMigrationTest`.
- `tools/verify_loader_parity.py` ships in both checkouts and runs from either one
  (`--forge` or `--neoforge`). It now checks both directions:
  - config keys, defaults and bounds;
  - resources and recipe semantics;
  - translations, including their text;
  - command literals;
  - GameTest names.

  Every difference must be listed with its reason; a stale entry also fails the check.

## Validation

Commands ran on Linux with Java 17 (Forge) and the Java 21 toolchain (NeoForge), offline
unless noted. Each GameTest profile used a fresh game directory.

| Check | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | --- | --- |
| `build` (unit tests included) | Pass. 904 tests, 0 failures, errors or skips. | Pass. 904 tests, 0 failures, errors or skips. |
| GameTests, Iron's absent | 165/165 required. Optional: 1 run, 129 skipped. | 159/159 required. Optional: 1 run, 121 skipped. |
| GameTests, Iron's loaded | 165/165 (Iron's 1.20.1-3.15.0). Optional: 108 run, 22 skipped. | 159/159 (Iron's 1.21.1-3.16.3). Optional: 97 run, 25 skipped. |
| GameTests, Iron's 1.20.1-3.16.3 | 165/165 on the second run. The first run failed `arsNative_successAndLateVetoHaveExactPoolDeltas` once (see below). | Not applicable. |
| GameTests, addons | 165/165 with Iron's 3.15.0, Ars Elemental 0.6.8.0 and Ars Zero 2.0.2. Optional: 126 run, 4 skipped (Covenant, Too Many Glyphs). Run online, because TerraBlender was not in the offline cache. | 159/159 with Iron's 3.16.3, Ars Elemental 0.7.10.1, Ars Zero 2.0.2, Ars Elemancy 1.18.3 and Ars Affinity 1.1.1. Optional: 122 run, 0 skipped. |
| `contract_parity.py --other` | Pass: 33 sources/fixtures. | Pass: 33 sources/fixtures. |
| `verify_loader_parity.py` | Pass: 60 config fields, 1461 resources, 143 shared GameTests, 99 listed exceptions. | Same result from this checkout. |
| `python3 -m unittest discover -s tools -p 'test_*.py'` | 33 tests pass. | 27 tests pass. |

**Mutation check.** The two NeoForge bugs fixed here were reintroduced one at a time: the synergy
handler unregistered, and the old `iss_primary` ceiling restored. In each case exactly the
matching `ParityBehaviourGameTests` scenario failed ("added 0.0" and "added 6000.0"). The code
was then restored.

**Known harness race.** While tests change config values, Forge's config file watcher
sometimes reloads `ars_n_spells-server.toml` part-way through a save. It logs "Table ... has been
declared twice", and a test that depends on a just-set value can then see a stale one. The
3.3.5 testing notes already record this. It caused the single 3.16.3 failure above, and the
rerun passed.

## Not verified

- Real clients: screen rendering, HUD and tooltips, and two-player sessions.
- The casting smoke harness. It compiles on NeoForge but was not run; it needs the copied QA
  save or a disposable server.
- The Forge Covenant and Too Many Glyphs GameTest profiles. They were not run in this pass.
- The gameplay balance of the NeoForge `iss_primary` gear-bonus change and of the Forge combat
  bridge in real packs.

## Reproduce

```sh
./gradlew --offline build
python3 tools/contract_parity.py --other <other checkout>
python3 tools/verify_loader_parity.py --neoforge <NeoForge checkout>   # from Forge
python3 tools/verify_loader_parity.py --forge <Forge checkout>         # from NeoForge
python3 -m unittest discover -s tools -p 'test_*.py'
./gradlew --offline runGameTestServer -PgametestRunDir=<new dir>
./gradlew --offline runGameTestServer -PwithIronsRuntimeGameTests -PgametestRunDir=<new dir>
# Forge:    -PwithIrons316RuntimeGameTests ; -PwithIronsRuntimeGameTests -PwithArsElemental -PwithArsZero
# NeoForge: -PwithIronsRuntimeGameTests -PwithArsZero -PwithArsElemancy -PwithArsAffinity
```
