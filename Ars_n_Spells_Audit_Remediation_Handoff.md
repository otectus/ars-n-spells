# Ars 'n' Spells: audit and remediation handoff

Audit date: **17 November 2026**. Repository: [otectus/ars-n-spells](https://github.com/otectus/ars-n-spells). Audited branch: `main`. Exact commit: **`6c26c544291ec3123eb1f5e97136d62d4d801b41`**. All repository locations and quoted line numbers refer to this commit. This is a remediation specification; no tracked repository file was changed.

**Result: 15 findings, comprising 13 Confirmed and 2 Suspected. No Critical issue was established.** The highest-severity finding is a reproduced imported-glyph payment bypass. Passing baseline tests do not invalidate the uncovered edge cases.

**Coverage qualification:** all 3,398 tracked files were inventoried and byte-read; all 344 production Java files (42,589 lines, excluding the GameTest package) were opened for source review. Resources received structural validation, and the runtime/test profiles below were executed. This is **not a claim that every test, historical document, image, or runtime combination received an exhaustive semantic review**. Section 6 distinguishes complete source reads, partial reads, automated checks, and unexecuted verification. Do not treat a structural pass or a green test suite as proof that every behavior is correct.

## 1. Project summary

### Audited behavior and architecture

The current README, 3.4.1 contracts, metadata, and source describe a Forge bridge between Ars Nouveau and Iron's Spells 'n Spellbooks:

- Five mana modes (`iss_primary`, `ars_primary`, `hybrid`, `separate`, `disabled`), configurable directional conversion, final-cost payment, alternate LP/aura resources, cancellation, and refund accounting.
- Cross-mod damage scaling, school choice, school affinity/progression, resonance, optional category cooldowns, equipment/Curios/enchantment contributions, potion handling, and nearby Source Jar regeneration.
- Ars spells serialized into ANS sidecars on carriers, exported to Iron's scrolls, bound into Iron's books through the Loom/ritual/command/native Inscription Table routes, and cast through native wheel/hotkey/staff paths. Uninscription must preserve unrelated contents and future data.
- Imported Iron's spells represented by stable Ars spell parts and glyph items. The base catalog has 111 spells, 777 default enabled spell/level variants, and 1,110 registered identities with levels reserved through 10. Optional catalog packages can expand that envelope or add supported addon identities. A datapack reload changes behavior, not frozen registry identity.
- Glyph Press conversion from a native filled scroll, blank plate, catalyst, and reserved Source; server-controlled jobs with locks, persistence, cancellation, output blocking, and automation that stages/removes items.
- Live server settings, versioned mapping/behavior snapshots, diagnostics, JEI integration, tooltips, journals, and optional-mod fallback. Imported glyphs explicitly require real player casts; unsupported fake/non-player routes are refused.

`ArsNSpells` registers content and event handlers. `bridge/`, `casting/`, and mixins reconcile native mana and cast lifecycles; `glyph/` manages catalog identities, behavior snapshots, admission, reservations, sessions, and imported executions through `compat/irons/glyph/`. `spell/` manages descriptors, saved sidecars, proxy slots, export/binding, and cross-cast execution. `block/`, `menu/`, `network/`, and `client/` implement the workstations and their synchronized UI. Capabilities under `data/` persist affinity/progression/cooldowns, while cast/payment ledgers handle short-lived and recoverable payment state. Optional integrations use isolated classes, mixin selection, and versioned runtime profiles.

The mod's claims are the audit standard; historical planning documents are not treated as proof that proposed features shipped. The separate NeoForge branch was not audited here.

### Versions and dependency declarations

Primary sources: `gradle.properties:5-65`, `build.gradle:1-10,20-21,126-209`, `settings.gradle:1-10`, `gradle/wrapper/gradle-wrapper.properties`, `src/main/resources/META-INF/mods.toml:1-78`, and `gradle/covenant-profile.gradle:1-53`.

| Component | Exact development pin / observed identity | Role and published range |
| --- | --- | --- |
| ANS | 3.4.1; changelog marks it Unreleased | `mod_id=ars_n_spells`; update metadata still naming stable 3.4.0 is intentional |
| Minecraft | 1.20.1 | Required `[1.20.1]` |
| Forge / JavaFML | 47.4.10 | Development pin; published Forge/loader range `[47,)` |
| Java | Toolchain 17; audit used Temurin 17.0.21+9 | Required by the build |
| Mappings | Mojang official, 1.20.1 | `mappings channel: 'official'` |
| Gradle wrapper | 8.4 | Same version used for the audit builds |
| ForgeGradle | Declared `[6.0,6.2)`; resolved 6.0.54 | Dynamic range, not an exact reproducibility pin |
| MixinGradle | Declared `0.7.+`; resolved 0.7.38 | Dynamic range |
| Mixin annotation processor | 0.8.5 | Build-time processor |
| MixinExtras common / Forge | 0.5.4 | Implementation/processor; Forge runtime jar-in-jar, range `[0.5.4,)` |
| Ars Nouveau | 4.12.7; CurseForge project 401955, file 6688854 | Required `[4.12.7,4.13)` |
| Iron's Spellbooks | 1.20.1-3.15.0; project 855414, file 7402504 | Compile-only by default; optional runtime `[1.20.1-3.15.0,1.20.1-4.0.0)` |
| Alternate Iron's profile | 1.20.1-3.16.3; project 855414, file 8680180 | Opt-in compile **and** runtime profile |
| irons_lib | 1.20.1-2.1.0 | Alternate Iron's runtime dependency |
| Curios | 5.14.1+1.20.1 | Compile API and development runtime; required transitively by Ars |
| GeckoLib | 4.7.1.3 default; 4.8.3 with Iron's 3.16.3 | Compile/runtime for upstream types and native integrations |
| PlayerAnimator | 1.0.2-rc1+1.20 | Iron's runtime profile dependency |
| JEI common/Forge API | 15.49.0.187 | Optional client integration, compile-only |
| Ars Elemental | 0.6.8.0; project 561470, file 8399870 | Opt-in runtime; metadata optional `[0.6.8.0,)` |
| Too Many Glyphs | 2.3.2; project 560595, file 4813803 | Opt-in runtime; no hard dependency |
| Ars Zero | 1.20.1-2.0.2HOTFIX artifact; project 1377482, file 8721552 | Opt-in local `libs/` profile; optional metadata `[2.0.2,)` |
| TerraBlender | 1.20.1-3.0.1.10 | Ars Zero runtime dependency |
| Wind's Spellbooks | 1.20.1-1.0.0; Modrinth version Rnn55z6s | Opt-in runtime with the `wind_spellbooks` catalog package |
| Covenant of the Seven | 2.2.6-hotfix; project 1368461, file 7821584 | Opt-in complete local stack; optional metadata `[2.2.6,)` |
| Blood Magic | 3.3.3-45; project 224791, file 5290993 | Covenant stack / reflective LP integration |
| Enigmatic Legacy | 2.30.1; project 336184, file 5600004 | Covenant stack / ring integration |
| Nature's Aura | 39.4; project 306626, file 4963590 | Covenant stack / aura integration |
| Caelus | 3.1.0+1.20; project 308989, file 4581481 | Covenant stack dependency |
| Patchouli | 1.20.1-81-FORGE; project 306770, file 4636277 | Covenant stack dependency |
| JUnit Jupiter / launcher | 5.10.2 / 1.10.2 | Unit tests |
| ASM / ASM tree | 9.5 / 9.5 | Dependency bytecode tests |
| Python / Pillow tooling | CI requests Python 3.12; Pillow is unpinned | Audit observed Pillow 13.0.0; see ANS-013 |

These are the repository's direct pins and the explicitly provisioned optional integration stack, not an assertion that every transitive JVM library is independently pinned. The broader published mod ranges were not exhaustively tested. No access-transformer file is present in this tree.

### Dependency verification

APIs were checked against the actual pinned artifacts, using source archives where available and CFR 0.152 / `javap` for exact released class signatures and implementation. A default GitHub branch was not substituted for an installed version. The live transformed Iron's `AbstractSpell` was also exported and inspected with Covenant mixins applied.

| Dependency surface | Inspection and runtime evidence | Remaining limit |
| --- | --- | --- |
| Ars Nouveau 4.12.7 | Spell resolver/caster/context, mana capability/events, glyph serialization/schools, rituals, items and Source interactions compared with ANS hooks; loaded in all GameTest profiles | Every native glyph combination was not executed |
| Iron's 3.15.0 | `AbstractSpell`, `SpellRegistry`, `MagicData`, `MagicManager`, native events, container/slot/binding behavior, target helpers and selected spell implementations inspected; loaded GameTests and payment probe | Catalog registration coverage is broader than spell-effect runtime coverage |
| Iron's 3.16.3 | Real alternate artifact compiled and loaded with required libraries; all combined-profile required GameTests passed | Not every admitted future 3.x release or spell route is certified |
| Curios 5.14.1 | Actual inventory/slot/equipped-curio surfaces inspected; equipment/ring tests executed | Client/server timing race in ANS-003 needs a graphical fixture |
| Covenant 2.2.6-hotfix | Native ring event handlers and transformed Iron's methods inspected; full required stack loaded; normal payment tests and ANS-001 reproduction executed | Custom tagged Virtue item branch not separately reproduced |
| Blood Magic 3.3.3-45 | Actual `NetworkHelper.getSoulNetwork(UUID)`, SoulNetwork essence read/write/syphon signatures verified; LP route tests loaded | Exhaustive upstream Soul Network failure behavior not audited |
| Enigmatic Legacy 2.30.1 | Actual `SuperpositionHandler.hasCurio(LivingEntity, Item)` and `getCurioStack` surfaces verified; native ring tests loaded | Other Enigmatic features are outside ANS integration scope |
| Nature's Aura 39.4 | Actual `IAuraChunk` get/drain/store signatures and Covenant's use inspected; aura route test executed | World-wide aura economy is not audited |
| Ars Elemental 0.6.8.0 | Actual glyph/school implementations inspected, including Conjure Terrain's two schools; addon GameTests executed | No per-glyph full playthrough |
| Ars Zero 2.0.2HOTFIX | Actual artifact/source extracted, metadata and multi-phase/staff integration compared; addon profile executed | Staff-only features intentionally remain blacklisted for cross-casting |
| Too Many Glyphs 2.3.2 | Actual artifact/source extracted and glyph integration checked; addon tests executed | No claim of all possible mixed-addon recipe behavior |
| Wind's Spellbooks 1.20.1-1.0.0 | Actual artifact/source extracted, catalog/adapter surfaces checked, addon/catalog tests executed | No universal support claim for unrelated Iron's addons |
| JEI 15.49.0.187 | Actual API source/signatures checked, including recipe lookup/hide/reload surfaces; compile succeeds | Full JEI client runtime and repeated-reload memory checks not run |
| Forge 47.4.10 | Actual event, FakePlayer, config synchronization and lifecycle implementation checked | Older Forge builds admitted by `[47,)` not run |
| GeckoLib, PlayerAnimator, irons_lib, TerraBlender, Patchouli, Caelus | Resolved metadata/types and required runtime loading verified | ANS does not directly implement most of these APIs; their internals were not exhaustively audited |

The complete Covenant profile used an audit-local lock generated from the six actual artifacts. Caelus 3.1.0+1.20 downloaded from CurseForge had a different hash from the repository's historical profile record; a separately obtained Maven artifact differed in manifest bytes while its classes matched. The historical Caelus hash was not reproduced. This is disclosed as a provenance limit, **not** reported as a confirmed broken dependency declaration.

### Executed validation and sound paths

The following are observations from this audit, not copied historical claims. Each GameTest result was checked for the explicit completion line and optional-test counters; `BUILD SUCCESSFUL` alone was not used as proof of gameplay execution.

| Run | Required-test result | Instrumented optional scenarios executed / skipped |
| --- | --- | --- |
| Default JUnit suite | 1,061 test cases; 0 failures, 0 errors, 6 skipped | 1,055 executed |
| Covenant bytecode surface tests, explicit follow-up | All 6 passed; 0 skipped | The six initially skipped tests rerun with the exact Covenant JAR at their expected ignored `libs/` path |
| Iron's absent | All 344 required GameTests passed | 1 / 282 |
| Iron's 3.15.0 | All 344 required GameTests passed | 241 / 42 |
| Iron's 3.15.0 + complete Covenant stack | All 344 required GameTests passed | 247 / 36 |
| Iron's 3.15.0 + Ars Elemental + Too Many Glyphs + Wind + Wind catalog | All 344 required GameTests passed | 261 / 22 |
| Iron's 3.15.0 + preceding addons + Ars Zero + level-15 envelope catalog | All 344 required GameTests passed | 276 / 7 |
| Iron's 3.16.3 + all preceding addons/catalogs + complete Covenant stack | All 344 required GameTests passed | 282 / 1; only `save01_experiment` skipped |
| External delayed-Virtue reproduction with Covenant | All 345 required GameTests passed, including the added probe that asserts the defect exists | Baseline 247 / 36; probe outside optional counter |
| Python tooling suite | 58 tests passed | No failure |
| Icon verifier | Passed | Structural/provenance checks; not every image visually reviewed |
| Icon generator `--check` | **Failed** | Compression-byte difference, ANS-013 |
| Shared contract manifest | Passed for 33 sources | This is not a cross-branch NeoForge audit |
| Catalog package generation/checks | Base envelope: 111 spells / 1,665 IDs; Wind: 7 spells / 70 IDs | Bounded package generation and identity checks |

Most positive gameplay fixtures use Forge FakePlayers. They run actual server registries, events, item use, effects, and resource changes, but are not real remote-client sessions; ANS-002 explains the specific consequence of that distinction.

The default suite's six skips were all `CovenantJarSurfaceTest` because its expected local JAR path was initially absent. After placing the inspected 2.2.6-hotfix JAR there, `test --tests com.otectus.arsnspells.compat.CovenantJarSurfaceTest --rerun-tasks` passed all six. The baseline total and the focused follow-up are reported separately rather than implying one unfiltered run with different results.

The reviewed core is sound in several important tested areas: dependency-absent dedicated-server boot; registrations and critical mixin application in the tested stacks; bounded packet admission with menu/session/revision checks; stable catalog identity and strict mapping numeric validation; supported native and imported payment/refund regression cases; Press item/Source conservation cases; native table extraction and uninscription regression cases; and explicit modifier cleanup in tested mode changes. The JSON/PNG/archive scans found no malformed tracked JSON, corrupt tracked PNG, archive CRC error, or unresolved local model reference. These statements are bounded to the checks performed, not a blanket certification.

The production `build` task also passed. Its installable `ars_n_spells-3.4.1.jar` has expanded 3.4.1 metadata, the mixin resources, and `mixinextras-forge-0.5.4.jar` plus its jar-in-jar metadata. The slim artifact intentionally lacks that bundled runtime. No external audit-probe class was packaged. A separate runtime boot from the packaged release JAR was not performed; the GameTests used the development source set.

Some optional dependencies logged their own recipe/reward-download warnings, and a shutdown config-watcher exception appeared after successful completion. No ANS cause was established for those messages; they are not padded into this findings list. Saved-world removal of Iron's can also encounter its native pocket-dimension registry dependency; clean absent-profile boot does not prove arbitrary existing worlds survive removal.
## 2. Findings overview

| ID | Title | Severity | Category | Status | Primary file |
| --- | --- | --- | --- | --- | --- |
| ANS-001 | Delayed imported glyphs can execute without payment after the Virtue Ring is removed | High | Cross-mod integration / payment lifecycle | Confirmed | `src/main/java/com/otectus/arsnspells/glyph/EmissionPayment.java` |
| ANS-002 | The real-player-only imported-glyph boundary accepts ordinary Forge FakePlayers | Medium | Admission / automation policy / test coverage | Confirmed | `src/main/java/com/otectus/arsnspells/glyph/GlyphAdmission.java` |
| ANS-003 | Ring state is shared across logical sides and retained after players leave | Medium | State isolation / lifecycle / performance | Confirmed | `src/main/java/com/otectus/arsnspells/compat/SanctifiedLegacyCompat.java` |
| ANS-004 | HYBRID mode drops Ars potion regeneration instead of transferring it | Medium | Mana integration / potion effects | Confirmed | `src/main/java/com/otectus/arsnspells/equipment/PotionContributions.java` |
| ANS-005 | Primary spell school differs between progression and curio discounts | Medium | Logic / duplicated school resolution | Confirmed | `src/main/java/com/otectus/arsnspells/util/SchoolResolver.java` |
| ANS-006 | Returning from the End clears cooldowns even though the player did not die | Medium | Persistence / player lifecycle | Confirmed | `src/main/java/com/otectus/arsnspells/data/ModCapabilityProvider.java` |
| ANS-007 | Live server config reloads leave remote HUD policy stale | Medium | Networking / config synchronization | Confirmed | `src/main/java/com/otectus/arsnspells/config/AnsSettings.java` |
| ANS-008 | Virtue Ring hides mana bars even when aura casting is disabled | Medium | Client presentation / config | Confirmed | `src/main/java/com/otectus/arsnspells/client/ManaBarController.java` |
| ANS-009 | Spell Loom receives server refusals but normally displays its local Ready result | Medium | Networking / UI feedback | Confirmed | `src/main/java/com/otectus/arsnspells/client/screen/SpellLoomScreen.java` |
| ANS-010 | Carrier reconciliation skips books whose entire sidecar is missing | Medium | Persistence / migration / inscription | Confirmed | `src/main/java/com/otectus/arsnspells/spell/irons/CarrierReconciler.java` |
| ANS-011 | Regeneration conversion assumes units that do not match the pinned native schedulers | Medium | Mana arithmetic / dependency behavior | Suspected | `src/main/java/com/otectus/arsnspells/bridge/ManaRegenBridge.java` |
| ANS-012 | The Spell Loom client can construct a different slot layout when its block entity is absent | Medium | Networking / menu synchronization | Suspected | `src/main/java/com/otectus/arsnspells/menu/SpellLoomMenu.java` |
| ANS-013 | The required icon generation check depends on an unpinned PNG encoder | Medium | Build / reproducibility | Confirmed | `tools/generate_icons.py` |
| ANS-014 | Removed Iron’s spell IDs pass availability checks through NoneSpell fallback | Low | Cross-mod API contract / missing registry entries | Confirmed | `src/main/java/com/otectus/arsnspells/spell/CrossCastValidator.java` |
| ANS-015 | Current player/config documentation contradicts implemented behavior | Low | Documentation / configuration consistency | Confirmed | `README.md` |

## 3. Findings in detail

No Critical finding was established. Findings below are ordered High, Medium, then Low. Confirmed means the cited code or reproduced result establishes the defect; it does not claim a graphical reproduction unless explicitly stated.

### ANS-001: Delayed imported glyphs can execute without payment after the Virtue Ring is removed

**Severity:** High  
**Category:** Cross-mod integration / payment lifecycle  
**Status:** Confirmed

**Location and quoted evidence**

`src/main/java/com/otectus/arsnspells/glyph/GlyphRoute.java:31-39`

```java
    /** The route native casting would use for {@code player} now. */
    public static GlyphRoute decide(ServerPlayer player) {
        try {
            if (SanctifiedLegacyCompat.isVirtueAuraCostPathActive(player)) return AURA_NATIVE;
            if (SanctifiedLegacyCompat.isCursedRingCostPathActive(player)) return LP;
        } catch (RuntimeException | LinkageError unavailable) {
            // An unreadable ring state is no ring: the mana route, as native casting falls back.
        }
        return MANA;
```

`src/main/java/com/otectus/arsnspells/glyph/EmissionPayment.java:165-181`

```java
    public boolean payAtEffect(double finalIronsCost, int finalLevel, boolean free) {
        if (paid) return true;
        if (free) {
            releaseHeld();
            markPaid(null, "free");
            return true;
        }
        return switch (occurrence.route) {
            case AURA_NATIVE -> {
                // Covenant took the aura at the pre-cast event; ANS takes nothing, as for a native cast.
                releaseHeld();
                markPaid(null, "aura (Covenant)");
                yield true;
            }
            case LP -> payLp(finalIronsCost, finalLevel);
            case MANA -> payMana(finalIronsCost);
        };
```

`src/main/java/com/otectus/arsnspells/compat/irons/glyph/IronsGlyphInvoker.java:81-92`

```java
            data.setAdditionalCastData(null);
            if (!spell.checkPreCastConditions(level, castLevel, player, data))
                return IronGlyphBridge.InvocationOutcome.rejected("message.ars_n_spells.glyph.refused.no_valid_target");
            if (MinecraftForge.EVENT_BUS.post(new SpellPreCastEvent(player, spell.getSpellId(), castLevel, spell.getSchoolType(), CastSource.SPELLBOOK)))
                return IronGlyphBridge.InvocationOutcome.rejected("message.ars_n_spells.glyph.refused.vetoed");
            boolean recast = data.getPlayerRecasts().hasRecastForSpell(spell.getSpellId());
            SpellOnCastEvent event = new SpellOnCastEvent(player, spell.getSpellId(), castLevel, spell.getManaCost(castLevel),
                spell.getSchoolType(), CastSource.SPELLBOOK);
            MinecraftForge.EVENT_BUS.post(event);
            boolean free = recast || (player.isCreative() && !ServerConfigs.CREATIVE_MANA_COST.get());
            if (!invocation.payment().payAtEffect(event.getManaCost(), event.getSpellLevel(), free)) {
                var why = invocation.payment().refusal();
```

Pinned dependency evidence: **Covenant of the Seven 2.2.6-hotfix**, `net/llenzzz/covenant_of_the_seven/events/ISSSpellEvents.java:147-158`. Line numbers below are from the CFR 0.152 extraction of that exact artifact, not an upstream Git revision.

```java
    public static void virtueRingCastingEvent(SpellPreCastEvent event) {
        if (event.getCastSource().toString().equals("SCROLL")) {
            return;
        }
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player2 = (ServerPlayer)player;
        if (!SuperpositionHandler.hasCurio((LivingEntity)player2, (Item)((Item)ModItems.VIRTUE_RING.get())) || SuperpositionHandler.hasCurio((LivingEntity)player2, (Item)EnigmaticItems.CURSED_RING)) {
            return;
        }
```

**What is wrong**

The occurrence captures `AURA_NATIVE` at root admission. At execution, `payAtEffect` assumes that posting an Iron's pre-cast event necessarily collected Covenant aura. That assumption is false after equipment or policy changes.

The inspected Covenant **2.2.6-hotfix** artifact, class `net/llenzzz/covenant_of_the_seven/events/ISSSpellEvents.class`, method `virtueRingCastingEvent(SpellPreCastEvent)`, returns unless the caster currently wears Covenant's specific Virtue Ring and lacks Enigmatic Legacy's Cursed Ring. Its condition is equivalent to:

```java
if (!SuperpositionHandler.hasCurio(player, ModItems.VIRTUE_RING.get())
        || SuperpositionHandler.hasCurio(player, EnigmaticItems.CURSED_RING)) return;
```

ANS's `mixin/sanctified/MixinCovenantSpellEvents.java:42-49` additionally suppresses that handler when the aura toggle is off. Neither situation changes the occurrence's captured route. The imported effect then runs after `markPaid(null, "aura (Covenant)")` without an aura debit.

**Reproduced, not inferred:** an external GameTest source directory was added through a temporary Gradle init script, without changing tracked repository files. The test used the existing `GlyphRouteGameTests` configuration fixture, a survival fixture player, and the actual Ars item-use path. It cast `Self → Delay → imported Heal I` with the Virtue Ring equipped and zero Iron's mana, removed the ring immediately, cleared its equipment cache, then waited for resolution. The log recorded:

```text
AUDIT_AURA_SWAP health=15.0 auraBefore=999990 auraAfter=999990 mana=0.0
All 345 required tests passed :)
```

Health began at 10. LP was also asserted unchanged. The probe's assertions demonstrate the defect; they must be inverted into a regression test for the correction. The fixture is a Forge `FakePlayer`, as are the repository's positive casting fixtures; no remote-client playthrough is claimed.

There is also a provider mismatch for custom items added to `#ars_n_spells:virtue_rings`: ANS's tag scan can select this route, while Covenant's native handler checks its specific registered item. That branch is source-proven but was not separately exercised with a custom ring.

**Why it matters**

Players can arrange delayed imported effects that provide healing or other effects without paying the imported spell's required resource. Repeated/projectile executions have the same missing authorization boundary. Merely retaining the admission route is insufficient when the external provider rechecks current equipment.

**The fix**

1. Add an explicit execution-time authorization step for the captured alternative-resource route before `IronsGlyphInvoker.runInstant` posts events or begins an effect. Make charged releases and channel pulses use the same rule.
2. For `AURA_NATIVE`, require that the installed provider will actually service this execution under the captured route: correct native ring eligibility, enabled policy, usable provider, and appropriate cast source. If that authorization no longer holds, refuse the imported execution before its effect and release any unspent reservation. Do not silently mark it paid or reprice it into mana/LP.
3. Make native Covenant hooks aware of the imported execution's captured route. Equipping Virtue after a `MANA`/`LP` admission must not cause an unexpected aura debit plus a zeroed mana event. Retain ordinary native Iron's behavior outside an imported-execution scope; close that scope in `finally`.
4. For tag-added Virtue items, either implement an owned aura payment adapter that returns an actual payment result or refuse the unsupported native delegation with a translated reason. A tag membership is not evidence that Covenant charged anything.
5. Replace the unconditional aura branch with an explicit authorization/payment receipt. Keep the existing prohibition on refunds after an effect has started. Preserve separate accounting of the already executed Ars root and the imported child.

**Acceptance criteria**

- Reproduce the exact delayed-Heal sequence above. After ring removal, the imported Heal must not execute for free; health remains 10 for that child, and there is a clear refusal.
- Repeat with aura disabled after admission, a Cursed Ring added, projectile delivery, repeated emissions, a charged release, and a channel pulse.
- Equip Virtue after a mana/LP admission: no silent route change, double debit, or zero-cost child.
- Normal Virtue casts still debit aura exactly once; normal Cursed Ring and mana casts still debit their own route once. Verify custom tagged rings explicitly.
- No leaked session, alternative payment leg, or execution scope remains after a refusal.

**Dependencies on other findings:** ANS-003 supplies trustworthy equipment cache state. ANS-002 changes the positive GameTest fixture strategy; keep the reproduction using an admitted real-player fixture after that change.

### ANS-002: The real-player-only imported-glyph boundary accepts ordinary Forge FakePlayers

**Severity:** Medium  
**Category:** Admission / automation policy / test coverage  
**Status:** Confirmed

**Location and quoted evidence**

`src/main/java/com/otectus/arsnspells/glyph/GlyphAdmission.java:101-107`

```java
    public static boolean admit(Player player, SpellContext context) {
        List<Found> found = importedIn(context.getSpell());
        if (found.isEmpty()) return true;
        if (!(player instanceof ServerPlayer serverPlayer) || player instanceof com.hollingsworth.arsnouveau.api.ANFakePlayer
                || !(context.getCaster() instanceof PlayerCaster)) {
            return false;
        }
```

`src/main/java/com/otectus/arsnspells/glyph/GlyphInvocations.java:37-45`

```java
    static void resolve(ImportedIronEffect effect, HitResult hit, Level world, LivingEntity shooter, SpellStats stats,
                        SpellContext context, SpellResolver resolver) {
        if (world.isClientSide()) return;
        ImportedGlyphDefinition definition = effect.definition();
        if (!(shooter instanceof ServerPlayer player) || shooter instanceof com.hollingsworth.arsnouveau.api.ANFakePlayer
                || player.isRemoved() || !player.isAlive()) {
            // A turret, rune or other non-player caster: no session, no payment, no free cast.
            trace(null, definition, "refused_non_player_caster");
            return;
```

`src/main/java/com/otectus/arsnspells/gametest/CrossCastGameTests.java:331-345`

```java
    static ServerPlayer scenarioPlayer(GameTestHelper helper, String scenario) {
        String name = scenario.length() > 16 ? scenario.substring(0, 16) : scenario;
        return emptyHandedPlayer(helper,
            new GameProfile(UUID.nameUUIDFromBytes(("ans_gametest/" + scenario).getBytes(StandardCharsets.UTF_8)), name));
    }

    private static ServerPlayer emptyHandedPlayer(GameTestHelper helper, GameProfile profile) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), profile);
        player.moveTo(helper.absoluteVec(new Vec3(1.0, 2.0, 1.0)));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        // Hurt, so a resolved heal is observable as a health increase.
        player.setHealth(10.0f);
        // Permissive defaults so a cast can only be refused for the reason under test.
        player.setGameMode(GameType.CREATIVE);
```

`docs/3.4.1/casting-contract.md:7-13`

```markdown
## Who can cast

Only a real player casting through a player caster, from any route that resolves an Ars spell:
a spell book, Ars staffs and scrolls, and ANS carriers (exported Iron's scrolls, inscribed items
and bound Iron's books). Turrets, runes, fake players and other non-player casters never run
imported glyphs.

```

**What is wrong**

The contract excludes fake players, but both boundaries exclude only Ars's `ANFakePlayer`. Forge's separate `net.minecraftforge.common.util.FakePlayer` extends `ServerPlayer` and passes these tests when wrapped in `PlayerCaster`.

This is already exercised by the successful suite: `TouchRegressionGameTests.aimingPlayer` obtains `CrossCastGameTests.scenarioPlayer`, whose factory returns a Forge FakePlayer; `ImportedGlyphGameTests` then executes imported effects through item use. The external ANS-001 probe also completed an imported Heal using that fixture. This is not a hypothetical inheritance concern.

**Why it matters**

Other mods' automation can reach a path documented as reserved for real players. The current positive tests also conceal the missing rejection by relying on the same forbidden caster category.

**The fix**

Centralize the caster predicate and reject both Forge `FakePlayer` and Ars `ANFakePlayer`, in addition to the existing server-player, caster-wrapper, lifecycle, and session-owner checks. Use the predicate at root admission and again at delayed execution. Do not weaken it because existing positive tests use FakePlayer.

Change positive imported-glyph fixtures to a real `ServerPlayer` test fixture with an appropriate test connection. Keep fixture construction test-scoped and do not add a production admission bypass for tests. Add a separate ordinary Forge FakePlayer rejection case. Do not broadly ban every `ServerPlayer` subclass; real server implementations may subclass it.

**Acceptance criteria**

- Ordinary Forge FakePlayer, Ars ANFakePlayer, turret, rune, and non-player caster attempts are refused before imported effects and imported payment.
- Real players still cast from Ars books, ANS carriers, and bound Iron's books.
- Positive GameTests continue observing actual effects and payments; they must not turn green through an early skip after the new guard.
- A session cannot be transferred from an admitted player to a fake caster.

**Dependencies on other findings:** None.

### ANS-003: Ring state is shared across logical sides and retained after players leave

**Severity:** Medium  
**Category:** State isolation / lifecycle / performance  
**Status:** Confirmed

**Location and quoted evidence**

`src/main/java/com/otectus/arsnspells/compat/SanctifiedLegacyCompat.java:49-57`

```java
    // Curio-state cache
    //
    // Scanning the entire curio inventory runs once per spell cast per player
    // for ring/blasphemy detection. We scan once, cache the result for a short
    // TTL, and serve subsequent queries from cache. Entries are evicted on
    // player logout (see clearCacheFor / CursedRingHandler listener).
    // ------------------------------------------------------------------
    private static final int CURIO_CACHE_TTL_TICKS = 20; // 1 second at 20 TPS
    private static final ConcurrentHashMap<UUID, CurioState> CURIO_STATE_CACHE = new ConcurrentHashMap<>();
```

`src/main/java/com/otectus/arsnspells/compat/SanctifiedLegacyCompat.java:786-801`

```java
    private static CurioState getState(Player player) {
        if (player == null) {
            return EMPTY_STATE;
        }
        UUID id = player.getUUID();
        // ANS-HIGH-011: server-global gameTime (monotonic per world) instead of the
        // per-player tickCount, for consistency with the ring handlers. The now >=
        // cachedAtTick guard still cheaply rejects any non-monotonic edge (e.g. /time set).
        long now = player.level().getGameTime();
        CurioState cached = CURIO_STATE_CACHE.get(id);
        if (cached != null && now - cached.cachedAtTick < CURIO_CACHE_TTL_TICKS && now >= cached.cachedAtTick) {
            return cached;
        }
        CurioState fresh = scanCurios(player, now);
        CURIO_STATE_CACHE.put(id, fresh);
        return fresh;
```

`src/main/java/com/otectus/arsnspells/events/StateEvictionHandler.java:51-60`

```java
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player == null) {
            return;
        }
        ScrollLPTracker.clear(player.getUUID());
        ResonanceManager.clear(player);
        CrossCastContext.clear(player);
        CombatDebugState.clear(player.getUUID());
```

`src/main/java/com/otectus/arsnspells/events/StateEvictionHandler.java:78-90`

```java
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Integrated server: the JVM survives world exit, so these statics carry into the next
        // world unless they are drained here.
        sweepCounter = 0;
        ScrollLPTracker.clearAll();
        com.otectus.arsnspells.spell.CastValidationScope.clearAll();
        ResonanceManager.clearAll();
        CrossCastContext.clearAll();
        CombatDebugState.clearAll();
        LogThrottle.clearAll();
        com.otectus.arsnspells.bridge.ManaTrace.clearAll();
    }
```

**What is wrong**

A single static cache is keyed only by UUID. Client HUD checks and server payment checks call the same `getState`. In an integrated server, the local client player and the authoritative server player have the same UUID but independent, asynchronously synchronized inventories. A client scan can therefore populate the cache used by a server cost decision, and a server scan can populate the client's HUD decision. The 20-tick timestamp check does not identify the entity or logical side.

The source comment also claims logout eviction that is not wired. Repository-wide production references to `clearCacheFor` lead to equipment invalidation; `StateEvictionHandler` does not clear this cache on logout or server stop. TTL only rejects an old value when that UUID is queried again; it never removes absent players' entries.

**Why it matters**

Integrated-server casts or HUD visibility can use the wrong copy of ring state for up to the cache interval. A long-running server retains a cache record for every queried UUID, and a reused integrated-server JVM can carry those records into another world. The cache collision and retention are confirmed from the implementation; a visible integrated-client race was not manually reproduced.

**The fix**

Partition the cache by logical side and player lifecycle, or cache by weak player identity with properly synchronized access. Prefer separate bounded client/server UUID maps with explicit generation/identity checks; never let a client result satisfy a server authorization query.

Expose explicit per-player and per-world clear operations. Wire server logout, clone/replacement, and server stopping, plus client disconnect/world replacement. Keep equipment-change invalidation, and ensure it applies to the correct side. Consider reading authoritative equipment directly at the critical payment boundary instead of accepting a stale TTL result. Avoid retaining `Level`/`Player` strongly in static entries.

**Acceptance criteria**

- Query server and client representations with the same UUID and deliberately different ring inventories; each gets its own result.
- Populate the client cache after server invalidation but before the next server query; the server still sees its own inventory.
- Equip/unequip, death/clone, reconnect, dimension change, and switching single-player worlds leave no stale ring policy.
- After many distinct logins/logouts, cache size returns to the online-player bound; server stop and client disconnect empty their respective maps.

**Dependencies on other findings:** None.

### ANS-004: HYBRID mode drops Ars potion regeneration instead of transferring it

**Severity:** Medium  
**Category:** Mana integration / potion effects  
**Status:** Confirmed

**Location and quoted evidence**

`src/main/java/com/otectus/arsnspells/equipment/PotionContributions.java:51-67`

```java
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        boolean redirecting = BridgeManager.isUnificationEnabled()
            && mode != null && mode.isIssPrimary();

        // V14: removal is NOT gated on the redirect being on. This used to return above,
        // before either helper ran, so switching away from ISS_PRIMARY (or disabling
        // unification) left the last potion modifier sitting on the Iron's attributes with
        // nothing left running that could take it off. The redirect is conditional; the
        // cleanup is not.
        if (!redirecting) {
            arsnspells$clearPotionModifiers(player);
            return;
        }

        // Check for Ars potion effects and apply them to Iron's mana system
        arsnspells$redirectManaRegenPotions(player);
        arsnspells$redirectMaxManaPotions(player);
```

`src/main/java/com/otectus/arsnspells/mixin/ars/MixinManaCapability.java:224-232`

```java
        if (ArsRegenTickScope.isInRegenTickFor(player.getUUID())
                && BridgeManager.ironsOwnsSharedPool()) {
            // Injection one of the V06 split: Ars's own regeneration tick, and only that
            // caller, is suppressed - Iron's already regenerates this pool.
            // MixinManaRegenTick is what names the scope. Report the unchanged balance,
            // which is exactly what a zero-sized regen tick would have returned.
            double unchanged = (double) BridgeManager.getBridge().getMana(player);
            cir.setReturnValue(unchanged);
            return;
```

`src/main/java/com/otectus/arsnspells/equipment/EquipmentIntegration.java:359-368`

```java
        ManaBonus ars = new ManaBonus(fallbackMax + contribution(player, PerkAttributes.MAX_MANA.get(), contributions, excluded),
            fallbackRegen + contribution(player, PerkAttributes.MANA_REGEN_BONUS.get(), contributions, excluded));
        ManaBonus irons = IronsCompat.isLoaded() ? new ManaBonus(
            contribution(player, AttributeRegistry.MAX_MANA.get(), contributions, excluded),
            contribution(player, AttributeRegistry.MANA_REGEN.get(), contributions, excluded)) : ManaBonus.ZERO;
        double withheld = withheldArsMax.isEmpty() ? 0.0 : AttributeContribution.equipmentDelta(
            player.getAttribute(PerkAttributes.MAX_MANA.get()), withheldArsMax.values(), ansOwned);
        CachedEquipmentData computed = new CachedEquipmentData(ars, irons, calculateCurioDiscountsInternal(player),
            withheld, now);
        equipmentCache.put(player.getUUID(), computed);
```

**What is wrong**

The shared pool is Iron's-owned in both ISS_PRIMARY and HYBRID, so native Ars regeneration is suppressed in both. `PotionContributions.reconcile` installs the potion regeneration modifier only in ISS_PRIMARY and actively removes it in HYBRID. The equipment transfer is not an alternative potion path: its inputs are equipped item/Curios attribute modifiers and `IManaEquipment`, not the active potion effects. Thus the native Ars regeneration benefit has no paying-pool destination in HYBRID.

**Why it matters**

An Ars mana-regeneration potion has no regeneration benefit in HYBRID, and changing from ISS_PRIMARY to HYBRID removes the previously applied benefit. This contradicts the unified-pool behavior expected of active mana effects.

**The fix**

Use shared-pool ownership to decide whether the **regeneration** potion contribution must be transferred. Do not simply broaden the entire current `redirecting` branch: HYBRID's max-mana calculation already incorporates Ars's native maximum, including its potion contribution, so blindly adding the max-mana potion modifier could double count it.

Split regeneration and capacity reconciliation. Regeneration should follow `BridgeManager.ironsOwnsSharedPool()` and the established conversion policy. Capacity should respect the mode's existing native-ceiling composition. Keep unconditional cleanup for every modifier that ceases to apply, and preserve the existing bounded modifier identities.

**Acceptance criteria**

- In survival with an uncapped pool and all unrelated bonuses disabled, measure the delta over a fixed number of ticks with and without Ars mana-regeneration potion in HYBRID; the configured bonus must be present exactly once.
- Repeat ISS_PRIMARY, ARS_PRIMARY, SEPARATE, and DISABLED as controls.
- Switch modes while a potion is active, then let it expire: no stale modifier and no extra max-mana contribution in HYBRID.
- Keep the documented 0.5-per-level potion tuning unless the author separately changes balance; this finding concerns the missing HYBRID route.

**Dependencies on other findings:** Coordinate numeric conversion validation with ANS-011; the missing HYBRID route is independently confirmed.

### ANS-005: Primary spell school differs between progression and curio discounts

**Severity:** Medium  
**Category:** Logic / duplicated school resolution  
**Status:** Confirmed

**Location and quoted evidence**

`src/main/java/com/otectus/arsnspells/util/SchoolResolver.java:137-145`

```java
    public static SpellSchoolId resolve(@Nullable String registryId,
                                        @Nullable List<String> arsSchoolIds) {
        SpellSchoolId best = SpellSchoolId.GENERIC;
        for (SpellSchoolId candidate : resolveAll(registryId, arsSchoolIds)) {
            if (candidate.ordinal() < best.ordinal()) {
                best = candidate;
            }
        }
        return best;
```

`src/main/java/com/otectus/arsnspells/util/SchoolResolver.java:224-235`

```java
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        if (declared != null) for (String raw : declared) {
            if (raw == null) continue;
            List<String> overlay = snapshot.arsSchoolKeys(raw);
            if (overlay != null) result.addAll(overlay);
            else {
                SpellSchoolId builtin = ARS_SCHOOL_TO_CANONICAL.get(raw.toLowerCase(Locale.ROOT));
                if (builtin != null) result.add(SchoolKeys.normalize(builtin.id()));
                // Untranslated Ars schools are a different vocabulary, not Iron's school IDs.
            }
        }
        return result.isEmpty() ? List.of(SchoolKeys.normalize(heuristic(path).id())) : List.copyOf(result);
```

`src/main/java/com/otectus/arsnspells/util/SpellAnalysis.java:124-134`

```java
        /** The derived school id: "fire", "ice", "holy", etc., or "generic" if unknown. */
        public String dominantSchool() { return dominantSchool; }

        /** Enum-typed companion to {@link #dominantSchool()}; prefer this at new call sites. */
        public SpellSchoolId school() { return SpellSchoolId.fromId(dominantSchool); }

        /** Ordered namespaced memberships of the actual payload, including custom schools. */
        public List<String> schoolKeys() { return schoolKeys; }

        public String schoolKey() { return schoolKeys().get(0); }

```

`src/main/java/com/otectus/arsnspells/events/AffinityHandler.java:33-40`

```java
            // The cast's own context decides: an explicit school stamped by the cross-cast
            // replaces the glyph school, and a native cast carries none.
            String key = SpellAnalysis.analyze(spell, context).schoolKey();
            if (!com.otectus.arsnspells.util.SchoolKeys.GENERIC.equals(key)
                && com.otectus.arsnspells.util.SchoolKeys.builtin(key).isGeneric()) {
                player.getCapability(AffinityData.AFFINITY_DATA).ifPresent(data -> {
                    data.addLevel(key, 1);
                    PacketHandler.sendToClient(new AffinitySyncPacket(key, data.getLevel(key)), player);
```

`src/main/java/com/otectus/arsnspells/events/CursedRingHandler.java:22-26`

```java
        var analysis = SpellAnalysis.analyze(event.context.getSpell(), event.context);
        int cost = SanctifiedLegacyCompat.calculateLPCost(event.currentCost, analysis.firstEffect());
        double discount = SanctifiedLegacyCompat.getBlasphemyLPMultiplier(player, analysis.dominantSchool());
        if (discount < 1) cost = (int) Math.max(AnsConfig.ARS_LP_MINIMUM_COST.get(), Math.round(cost * discount));
        ArsCastPayments.alternative(event.context, ResourceUnit.LP, cost);
```

**What is wrong**

`resolve` chooses the built-in enum with the lowest ordinal, while `resolveKeys` preserves the declared/overlay membership order. `SpellAnalysis.Result` stores both answers. Affinity/progression and current scaling read `schoolKey()`, while `CurioDiscountHandler` and the Cursed Ring discount read `dominantSchool()`.

Concrete deterministic input: `SchoolResolver.resolve("audit:payload", List.of("conjuration", "earth"))` selects NATURE, whereas `resolveKeys` returns evocation first and nature second. Ars Elemental 0.6.8.0's actual `EffectConjureTerrain.getSchools()` declares CONJURATION and ELEMENTAL_EARTH, so this is a real integrated school combination, although the upstream set's iteration order should not be assumed. An ordered datapack overlay can make the mismatch deterministic in a game fixture.

**Why it matters**

A spell can train one school and receive its primary damage/progression bonus from it, but use another school for matching-school mana or LP discounts. Reordering otherwise identical metadata can change only some subsystems, making journal information and equipment behavior disagree.

**The fix**

Make the ordered namespaced school result the canonical analysis result, with one explicit primary. Derive the legacy built-in `dominantSchool()`/`school()` projection from that primary instead of independently rerunning enum-min selection. Update the old resolver javadoc and tests that promise a different tie-break.

For Automatic mode, use the existing `schoolKeys().get(0)` policy consistently so current namespaced affinity/progression data do not move unexpectedly. If metadata order needs normalization, do that once at the metadata boundary; preserve explicit datapack order where it defines priority. Explicit chosen school must continue replacing all inferred memberships. Do not erase or redistribute saved affinity tracks. Unknown/custom schools may legitimately have no built-in Blasphemy bonus; do not map them to an arbitrary built-in school.

**Acceptance criteria**

- Cover both orders of the conjuration/earth combination, an explicit ordered overlay, and the live Ars Elemental glyph.
- For each case, journal primary, affinity credit, progression credit, primary scaling, mana discount, and LP discount agree on the same primary.
- MAX/other multi-school damage aggregation still sees the complete membership set.
- Explicit school override remains authoritative and existing saved school data remain readable.

**Dependencies on other findings:** None.

### ANS-006: Returning from the End clears cooldowns even though the player did not die

**Severity:** Medium  
**Category:** Persistence / player lifecycle  
**Status:** Confirmed

**Location and quoted evidence**

`src/main/java/com/otectus/arsnspells/data/ModCapabilityProvider.java:64-91`

```java
    /**
     * Persist affinity and progression data across death/dimension change.
     * Cooldown data resets on death (clone hook does not copy it) but IS persisted
     * across server save/restart via {@link #serializeNBT()}.
     *
     * <p>ANS-HIGH-008: registered at HIGHEST priority so capability copy completes
     * BEFORE any third-party {@code PlayerEvent.Clone} handler at default priority
     * reads our caps. Without this, a HIGHEST-priority reader from another mod
     * would see the freshly-default state.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        event.getOriginal().getCapability(AffinityData.AFFINITY_DATA).ifPresent(oldData -> {
            event.getEntity().getCapability(AffinityData.AFFINITY_DATA).ifPresent(newData -> {
                CompoundTag tag = new CompoundTag();
                oldData.saveToNBT(tag);
                newData.loadFromNBT(tag);
            });
        });
        event.getOriginal().getCapability(ProgressionData.PROGRESSION_DATA).ifPresent(oldData -> {
            event.getEntity().getCapability(ProgressionData.PROGRESSION_DATA).ifPresent(newData -> {
                CompoundTag tag = new CompoundTag();
                oldData.saveToNBT(tag);
                newData.loadFromNBT(tag);
            });
        });
        event.getOriginal().invalidateCaps();
```

**What is wrong**

The clone handler copies affinity and progression but never cooldowns and never checks `event.isWasDeath()`. Forge 47.4.10's actual `net/minecraftforge/event/entity/player/PlayerEvent.java:211-241` documents that `Clone` fires on death **or travel from the End to the Overworld** and exposes `isWasDeath()` to distinguish them. The mod explicitly intends cooldown reset on death, not every clone.

**Why it matters**

The End exit becomes a non-death reset of ANS category cooldowns. Persistence through save/restart does not repair the empty capability created during this clone.

**The fix**

Within the existing revived-capability scope, copy `CooldownData.COOLDOWN_CAP` only when `!event.isWasDeath()`. Use its existing `save(CompoundTag)` and `load(CompoundTag)` methods so the `BridgeCooldowns` schema stays unchanged. Keep death reset as documented. Put original-capability invalidation in `finally` so a failed copy cannot leave revived capabilities active. Let the normal post-clone/respawn synchronization path publish the copied state where applicable.

Add this inside the revived-capability scope, alongside the existing affinity/progression copies:

```java
if (!event.isWasDeath()) {
    event.getOriginal().getCapability(CooldownData.COOLDOWN_CAP).ifPresent(oldData ->
        event.getEntity().getCapability(CooldownData.COOLDOWN_CAP).ifPresent(newData -> {
            CompoundTag saved = new CompoundTag();
            oldData.save(saved);
            newData.load(saved);
        }));
}
```

The surrounding handler must still invalidate the original capabilities in `finally`.

**Acceptance criteria**

- Set a future ANS cooldown end, trigger a non-death Clone/End return, and verify the same remaining cooldown under the current game-time clock.
- A death Clone still starts with no ANS category cooldowns.
- Affinity/progression continue copying in both cases; old saves without `BridgeCooldowns` remain valid.
- Save/restart and ordinary dimension travel preserve their existing behavior.

**Dependencies on other findings:** None.

### ANS-007: Live server config reloads leave remote HUD policy stale

**Severity:** Medium  
**Category:** Networking / config synchronization  
**Status:** Confirmed

**Location and quoted evidence**

`src/main/java/com/otectus/arsnspells/config/AnsSettings.java:27-38`

```java
    public static final List<Setting> SETTINGS = List.of(
        new Setting("enable_mana_unification", Kind.BOOLEAN),
        new Setting(MODE, Kind.MODE),
        new Setting("enable_resonance_system", Kind.BOOLEAN),
        new Setting("enable_cooldown_system", Kind.BOOLEAN),
        new Setting("enable_progression_system", Kind.BOOLEAN),
        new Setting("enable_affinity_system", Kind.BOOLEAN),
        new Setting("respect_armor_bonuses", Kind.BOOLEAN),
        new Setting("respect_enchantments", Kind.BOOLEAN),
        new Setting("enable_source_jar_synergy", Kind.BOOLEAN),
        new Setting("inscribed_ars_default_cooldown_ticks", Kind.COOLDOWN_TICKS),
        new Setting("debug_mode", Kind.BOOLEAN));
```

`src/main/java/com/otectus/arsnspells/config/AnsSettings.java:91-95`

```java
    public static Map<String, String> current() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Setting setting : SETTINGS) out.put(setting.key(), String.valueOf(value(setting.key()).get()));
        return out;
    }
```

`src/main/java/com/otectus/arsnspells/config/AnsSettingsService.java:138-143`

```java
    /** A config file edit or reload: the values are already loaded; make everything else follow and tell clients. */
    public static void onFileReloaded() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.execute(() -> broadcast(server));
    }
```

`src/main/java/com/otectus/arsnspells/client/ClientSettings.java:28-40`

```java
    public static void accept(AnsSettings.Snapshot snapshot) {
        current = snapshot;
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.hasSingleplayerServer()) BridgeManager.applyServerMode(snapshot.effectiveMode());
        if (!snapshot.resultKey().isEmpty() && minecraft.player != null) {
            boolean failed = snapshot.resultKey().endsWith("not_allowed") || snapshot.resultKey().endsWith("invalid_value")
                || snapshot.resultKey().endsWith("unknown_key") || snapshot.resultKey().endsWith("not_saved");
            minecraft.player.displayClientMessage(Component.translatable(snapshot.resultKey(),
                    snapshot.values().getOrDefault(AnsSettings.MODE, ""), snapshot.effectiveMode(), snapshot.resultDetail())
                .withStyle(failed ? ChatFormatting.RED : ChatFormatting.GREEN), false);
        }
        if (minecraft.screen instanceof com.otectus.arsnspells.client.screen.ConfigScreenFactory.ArsNSpellsConfigScreen screen)
            screen.onSettings(snapshot);
```

`src/main/java/com/otectus/arsnspells/client/ManaBarController.java:87-89`

```java
            // Determine which bar to show in hybrid mode
            boolean hybridShowIrons = mode == ManaUnificationMode.HYBRID
                && "irons".equalsIgnoreCase(AnsConfig.HYBRID_MANA_BAR.get());
```

**What is wrong**

The reload broadcast contains only the eleven editable settings. It does not carry `hybrid_mana_bar`, `hide_mana_bar_with_ring`, `enable_lp_system`, or the Virtue/aura policy. The client stores the received values for its settings screen and applies only the effective mana mode to runtime routing. `ManaBarController` still reads those omitted values directly from the client's Forge SERVER config.

Forge 47.4.10's actual `net/minecraftforge/network/ConfigSync.java` sends SERVER configs through the login handshake. Its `ConfigFileTypeHandler.ConfigWatcher.run()` reloads the server copy and fires the local reloading event; it does not broadcast a new config file to already-connected clients. ANS's own broadcast therefore needs to carry every live client policy it changes. Example: connect in Hybrid with the Ars bar selected, edit the server's `hybrid_mana_bar` to `irons`, and reload. Server settings and new connections use the new value; an existing remote client's bar-selection code retains the login value.

**Why it matters**

Players can see the wrong mana bar or keep a hidden bar after a resource-policy change until they reconnect. This is a source-confirmed missing synchronization path; a two-client graphical reproduction was not run.

**The fix**

Create a bounded, immutable client-policy snapshot containing all SERVER settings actually read by client presentation, including the four policy areas above. Keep server edit authorization separate from which values clients may receive: a display-only setting need not become remotely editable. Send this snapshot on login and every successful file/UI/command reload. Change `ManaBarController` and other affected client consumers to read the snapshot, with a safe initial-login fallback; clear it on disconnect. Do not mutate shared `AnsConfig` values from the client on an integrated server. Update the packet schema/protocol and paired client/server expectations if the wire layout changes.

**Acceptance criteria**

- With two real remote clients connected, change Hybrid bar selection and every ring HUD/resource toggle on the server; both update without reconnecting.
- Repeat through each supported configuration route; a rejected or failed config write changes neither server behavior nor the client snapshot.
- A new login receives the same policy as existing players; disconnecting and connecting to another server leaves no old policy.
- Integrated-server client receipt does not overwrite authoritative config or cause a reload loop.

**Dependencies on other findings:** None.

### ANS-008: Virtue Ring hides mana bars even when aura casting is disabled

**Severity:** Medium  
**Category:** Client presentation / config  
**Status:** Confirmed

**Location and quoted evidence**

`src/main/java/com/otectus/arsnspells/client/ManaBarController.java:55-68`

```java
            // Hide mana bars when the Cursed/Virtue Ring is equipped — spells consume
            // LP or Aura in that state, so showing a mana bar is misleading. This runs
            // independently of mana unification so it still applies when unification is off.
            if (AnsConfig.HIDE_MANA_BAR_WITH_RING.get()
                && SanctifiedLegacyCompat.isAvailable()) {
                LocalPlayer localPlayer = Minecraft.getInstance().player;
                if (localPlayer != null) {
                    boolean cursed = AnsConfig.ENABLE_LP_SYSTEM.get()
                        && SanctifiedLegacyCompat.isWearingCursedRing(localPlayer);
                    boolean virtue = SanctifiedLegacyCompat.isWearingVirtueRing(localPlayer);
                    if (cursed || virtue) {
                        event.setCanceled(true);
                        return;
                    }
```

`src/main/java/com/otectus/arsnspells/compat/SanctifiedLegacyCompat.java:757-760`

```java
    public static boolean isVirtueAuraCostPathActive(Player player) {
        return isAvailable()
            && AnsConfig.ENABLE_VIRTUE_AURA_SYSTEM.get()
            && isWearingVirtueRing(player);
```

**What is wrong**

The Cursed Ring branch checks `ENABLE_LP_SYSTEM`, but the Virtue Ring branch checks only whether the item is worn. `SanctifiedLegacyCompat.isVirtueAuraCostPathActive()` includes the missing policy check; the native Covenant-event mixin also disables aura interception when the setting is off. Therefore wearing a Virtue Ring with aura integration disabled still hides both mana overlays, although casts use mana. This is independent of ANS-007: it occurs even when the client received the correct config at login.

**Why it matters**

A supported configuration removes the player's mana display while mana remains the resource being spent.

**The fix**

Make HUD suppression depend on the active resource route, not equipment presence alone. Use a client-safe policy predicate that applies the same enablement and Cursed/Virtue priority rules as server routing. Include the Virtue/aura enable flag and the LP flag. Retain the explicit `hide_mana_bar_with_ring` preference. When implementing ANS-007, source these policy values from the synchronized client snapshot rather than an independently stale config.

**Acceptance criteria**

- With hide-with-ring on, wearing Virtue and aura integration off leaves the appropriate mana bar visible in every mana mode.
- Turning aura integration on hides mana only when the actual alternate-resource route is active.
- Check Cursed only, Virtue only, both rings, neither ring, both integration toggles off, and hide-with-ring off.
- Unrelated overlays, XP placement, and native Always/Never visibility settings keep their existing behavior.

**Dependencies on other findings:** Coordinate with ANS-007 for live updates; the initial-login logic fix can be made independently.

### ANS-009: Spell Loom receives server refusals but normally displays its local Ready result

**Severity:** Medium  
**Category:** Networking / UI feedback  
**Status:** Confirmed

**Location and quoted evidence**

`src/main/java/com/otectus/arsnspells/client/screen/SpellLoomScreen.java:344-352`

```java
    public static void acceptResult(String reasonCode, boolean preview, com.otectus.arsnspells.network.LoomRequestContext request) {
        Screen open = Minecraft.getInstance().screen;
        if (open instanceof SpellLoomScreen loom && Minecraft.getInstance().player != null
            && Minecraft.getInstance().player.containerMenu == loom.menu && request.equals(loom.latestRequest)
            && request.matches(loom.menu.containerId, loom.menu.sessionId(),
                com.otectus.arsnspells.network.LoomInventoryRevision.of(loom.menu), com.otectus.arsnspells.util.SchoolMappings.get().digest())) {
            loom.serverReason = reasonCode == null ? InscriptionPlan.REASON_OK : reasonCode;
            loom.serverReasonIsPreview = preview;
            loom.updateInscribeState();
```

`src/main/java/com/otectus/arsnspells/client/screen/SpellLoomScreen.java:434-458`

```java
    private String currentReasonCode() {
        SpellLoomBlockEntity be = this.menu.getBlockEntity();
        if (be == null || this.menu.slots.size() < SpellLoomBlockEntity.SLOT_COUNT) {
            // Desynced menu: nothing local to plan against. A reply to a finished attempt says
            // nothing about what the slots hold now, so only a preview answer is reusable and
            // anything else keeps the button disabled, as it was before.
            return serverReasonIsPreview ? serverReason : LoomInscription.REASON_CARRIER_FAILED;
        }
        if (!this.menu.getSlot(SpellLoomBlockEntity.SLOT_OUTPUT).getItem().isEmpty()) {
            return LoomInscription.REASON_OUTPUT_OCCUPIED;
        }
        if (!IronsCompat.isLoaded()) {
            return LoomInscription.REASON_IRONS_MISSING;
        }
        if (this.menu.getSlot(SpellLoomBlockEntity.SLOT_SOURCE).getItem().isEmpty()) {
            return LoomInscription.REASON_NO_ARS_SPELL;
        }
        String plan = LoomInscription.plan(be).reasonCode();
        // A school this server cannot use stops Inscribe and Convert alike, as the server will.
        if (InscriptionPlan.REASON_OK.equals(plan) || InscriptionPlan.REASON_NOT_BLANK.equals(plan)
                || InscriptionPlan.REASON_TARGET_NOT_EMPTY.equals(plan)) {
            String schoolReason = LoomInscription.schoolReason(schoolChoice);
            if (schoolReason != null) return schoolReason;
        }
        return plan;
```

`src/main/java/com/otectus/arsnspells/client/screen/SpellLoomScreen.java:378-385`

```java
        // The requested school is part of what the server judges, so a new choice asks again.
        String fingerprint = com.otectus.arsnspells.network.LoomInventoryRevision.of(menu)
            + ":" + com.otectus.arsnspells.util.SchoolMappings.get().digest() + ":" + schoolChoice;
        if (fingerprint.equals(previewedInputs)) {
            return;
        }
        previewedInputs = fingerprint;
        sendAction(SpellLoomExportPacket.ACTION_PREVIEW);
```

**What is wrong**

`acceptResult` correctly validates the current menu/session/revision and stores the reply. However, `currentReasonCode` uses that reply only when the client block entity is absent. On the normal path it recomputes a local plan and returns it even when the server has just refused the same attempt. `updateInscribeState` and the rendered status use this method. A server-only refusal such as request admission/rate limiting is not part of the local planner, so the screen continues to say Ready and enables the action. The Apply School controls likewise compute their own reason. The preview fingerprint excludes the name/cosmetic draft, so not every server-validated draft change requests a fresh preview.

**Why it matters**

A player sees a button apparently do nothing and receives misleading readiness/tooltip feedback after a valid server refusal. The server's inventory validation remains authoritative and prevents the forbidden mutation; this finding is about a broken feedback chain, not an inventory exploit.

**The fix**

Store the last accepted response with its action, request identity, and complete relevant input/draft fingerprint. Render a matching server failure as the action's result, including refusals for Inscribe, Convert, and Apply School. Invalidate stale outcomes when relevant input changes; keep the existing session/revision rejection of replies for old menus. Include every server-validated draft field in preview invalidation, or explicitly validate those fields locally and send a new preview when they change. Treat temporary admission refusals as retryable: do not permanently disable the action because no slot changed. A timed retry or explicit Retry may request a new authoritative preview. Keep local planning as immediate provisional feedback, with matching server outcomes taking precedence.

**Acceptance criteria**

- Force a server REJECTED response while the local plan is OK; show its localized reason and a usable retry path.
- Verify refusal feedback for all three actions and name/school changes without slot changes.
- Out-of-order responses, a reopened menu, changed inventory, and mapping reloads never replace the current draft's state.
- A successful action and subsequent inventory sync do not leave an old refusal or an old success displayed for a different plan.
- Request previews remain bounded; no per-frame or uncontrolled per-tick network loop is introduced.

**Dependencies on other findings:** None.

### ANS-010: Carrier reconciliation skips books whose entire sidecar is missing

**Severity:** Medium  
**Category:** Persistence / migration / inscription  
**Status:** Confirmed

**Location and quoted evidence**

`src/main/java/com/otectus/arsnspells/spell/irons/CarrierReconciler.java:62-73`

```java
    public static Outcome reconcile(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag() || !IronsCompat.isLoaded()) {
            return Outcome.UNCHANGED;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !CrossCastNbt.hasCrossModSpells(tag)) {
            return Outcome.UNCHANGED;
        }

        if (CrossCastNbt.schemaVersion(stack.getTag()) > CrossCastNbt.SCHEMA_VERSION) return Outcome.UNCHANGED;
        boolean containerRepaired = repairCarrierContainer(stack);
        boolean orphansRemoved = removeOrphanProxySlots(stack);
```

`src/main/java/com/otectus/arsnspells/spell/irons/CarrierReconciler.java:115-135`

```java
    /**
     * Drop native proxy slots whose sidecar entry no longer exists.
     *
     * <p>Only pool ids ANS owns are considered, and only those absent from the sidecar, so a
     * genuine Iron's spell can never be removed.
     */
    private static boolean removeOrphanProxySlots(ItemStack stack) {
        if (!IronsBookBindingUtil.isIronsSpellBook(stack)) {
            return false;
        }
        Set<Integer> live = CrossCastNbt.usedProxyPoolIds(stack.getTag());
        boolean removedAny = false;
        for (int poolId = 1; poolId <= CrossCastNbt.PROXY_POOL_SIZE; poolId++) {
            if (live.contains(poolId)) {
                continue;
            }
            if (IronsProxySlotWriter.removeProxySlot(stack, poolId)) {
                removedAny = true;
                LOGGER.debug("Removed orphan ars_cross_{} proxy slot with no sidecar entry", poolId);
            }
        }
```

**What is wrong**

The routine promises to remove owned native proxy slots with no corresponding sidecar entry, but returns before that pass whenever `hasCrossModSpells(tag)` is false. The most complete orphan case, an Iron's book with ANS native proxies and no readable cross-spell entries, therefore cannot be repaired by the normal reconciler. `IronsBookBindingUtil` invokes this reconciler before binding, so this is not an unwired utility. Manual uninscription has a separate cleanup route and can repair some such books; do not remove that working path or describe the book as permanently unrecoverable.

**Why it matters**

A migrated/damaged book can retain unusable ANS wheel entries and occupied capacity when opened or rebound. The advertised automatic repair does nothing for the case with zero remaining sidecar entries.

**The fix**

Separate scroll-sidecar repair from native-book orphan cleanup. For a recognized Iron's spellbook with a supported schema, inspect ANS-owned native proxy IDs even when the sidecar list is absent or empty. Compute the live pool IDs from readable entries and remove only owned proxies not represented there. Preserve native spells, upgrades, unrelated NBT, and future-schema items. Distinguish an absent/empty supported sidecar from a future or undecodable format whose contents must not be guessed. Reconcile all owned duplicates consistently, and retain the existing capacity-restoration rules rather than indiscriminately shrinking the book.

**Acceptance criteria**

- Construct a book containing a native spell and an ANS proxy, remove its cross-spell sidecar, then run reconciliation: remove the orphan, preserve the native spell and upgrades, and report the repair.
- Repeat with an empty sidecar and with one surviving entry plus a different orphan.
- Current valid books are unchanged and reconciliation is idempotent.
- Future schema and undecodable payloads are preserved/refused conservatively, with no silent data loss.
- Legacy carrier-scroll container repair and manual uninscription continue passing.

**Dependencies on other findings:** None.

### ANS-011: Regeneration conversion assumes units that do not match the pinned native schedulers

**Severity:** Medium  
**Category:** Mana arithmetic / dependency behavior  
**Status:** Suspected

**Location and quoted evidence**

`src/main/java/com/otectus/arsnspells/bridge/ManaRegenBridge.java:25-31`

```java
    /**
     * Iron's Spellbooks effective regen formula (per second):
     * {@code regen_per_sec ≈ MAX_MANA * MANA_REGEN_attr * 0.01}.
     * Verified against irons-spells-n-spellbooks 1.20.1 and the user-observed
     * baseline of {@code MANA_REGEN=1, MAX_MANA=1000 -> 10 mana/sec}.
     */
    public static final double IRONS_REGEN_PER_SECOND_FACTOR = 0.01;
```

`src/main/java/com/otectus/arsnspells/bridge/ManaRegenBridge.java:64-79`

```java
    public static double arsToIronsRegen(double arsAbsPerSec, double ironsMaxMana) {
        if (arsAbsPerSec == 0.0 || ironsMaxMana <= 0.0) {
            return 0.0;
        }
        return arsAbsPerSec / (ironsMaxMana * IRONS_REGEN_PER_SECOND_FACTOR);
    }

    /**
     * Convert an Iron's MANA_REGEN attribute value into its equivalent absolute
     * regen rate ({@code mana/sec}) at a given current max mana pool.
     */
    public static double ironsToArsRegen(double ironsRegenAttr, double ironsMaxMana) {
        if (ironsRegenAttr == 0.0 || ironsMaxMana <= 0.0) {
            return 0.0;
        }
        return ironsRegenAttr * ironsMaxMana * IRONS_REGEN_PER_SECOND_FACTOR;
```

`src/main/java/com/otectus/arsnspells/bridge/ManaRegenBridge.java:90-100`

```java
    public static double convertArsToIrons(double arsAbsPerSec, Player player) {
        if (arsAbsPerSec == 0.0) return 0.0;
        ConversionMode mode = getConversionMode();
        if (mode == ConversionMode.DISABLED) return 0.0;

        double maxMana = (mode == ConversionMode.REFERENCE_POOL)
            ? AnsConfig.CROSS_SYSTEM_REGEN_REFERENCE_POOL.get()
            : getCurrentIronsMaxMana(player);

        double base = arsToIronsRegen(arsAbsPerSec, maxMana);
        return base * AnsConfig.CROSS_SYSTEM_REGEN_MULTIPLIER.get();
```

`src/main/java/com/otectus/arsnspells/bridge/ManaRegenBridge.java:111-121`

```java
    public static double convertIronsToArs(double ironsRegenAttr, Player player) {
        if (ironsRegenAttr == 0.0) return 0.0;
        ConversionMode mode = getConversionMode();
        if (mode == ConversionMode.DISABLED) return 0.0;

        double maxMana = (mode == ConversionMode.REFERENCE_POOL)
            ? AnsConfig.CROSS_SYSTEM_REGEN_REFERENCE_POOL.get()
            : getCurrentIronsMaxMana(player);

        double base = ironsToArsRegen(ironsRegenAttr, maxMana);
        return base * AnsConfig.CROSS_SYSTEM_REGEN_MULTIPLIER.get();
```

Pinned dependency evidence: **Iron's Spellbooks 1.20.1-3.15.0**, `io/redspace/ironsspellbooks/capabilities/magic/MagicManager.java:45-61`. Line numbers below are from the CFR 0.152 extraction of that exact artifact, not an upstream Git revision.

```java
    public static final int MANA_REGEN_TICKS = 10;
    public static final int CONTINUOUS_CAST_TICK_INTERVAL = 10;

    public boolean regenPlayerMana(ServerPlayer serverPlayer, MagicData playerMagicData) {
        int playerMaxMana = (int)serverPlayer.m_21133_((Attribute)AttributeRegistry.MAX_MANA.get());
        float mana = playerMagicData.getMana();
        if (mana != (float)playerMaxMana) {
            float playerManaRegenMultiplier = (float)serverPlayer.m_21133_((Attribute)AttributeRegistry.MANA_REGEN.get());
            float increment = (float)playerMaxMana * playerManaRegenMultiplier * 0.01f * ((Double)ServerConfigs.MANA_REGEN_MULTIPLIER.get()).floatValue();
            playerMagicData.setMana(Mth.m_14036_((float)(playerMagicData.getMana() + increment), (float)0.0f, (float)playerMaxMana));
            return true;
        }
        return false;
    }

    public void tick(Level level) {
        boolean doManaRegen = level.m_7654_().m_129921_() % 10 == 0;
```

**What is wrong**

The helper labels `0.01 * max * attribute` as Iron's regeneration **per second**, and never incorporates Iron's `ServerConfigs.MANA_REGEN_MULTIPLIER`. In the inspected Iron's 3.15.0 artifact, `io/redspace/ironsspellbooks/capabilities/magic/MagicManager.java:45-61` uses that 0.01 formula **per regeneration call**, multiplied by the server setting, with calls scheduled every 10 ticks. At 20 TPS that is two calls per second.

This is not sufficient evidence to prescribe simply changing 0.01 to 0.02. Ars Nouveau 4.12.7's `com/hollingsworth/arsnouveau/common/event/ManaCapEvents.playerOnTick` has no START/END phase filter, divides its nominal rate by `MEAN_TPS / REGEN_INTERVAL`, and can run twice per eligible player tick. The two native scheduling factors may cancel in some default configurations. Existing arithmetic tests can pass by sharing the helper's assumption; they do not establish wall-clock equivalence.

**To confirm:** with native regeneration enabled and a pool safely below its cap, measure the added mana over exactly 20 server ticks for a known native regen bonus and the corresponding bridged bonus. Run both directions, then repeat at Iron's native multiplier 0.5, 1, and 2 and at non-default Ars regen intervals. Record both event phases, native scheduler calls, and actual resource deltas. A mismatch beyond rounding confirms the behavioral defect. Until that measurement, the wrong unit description is certain but the net gameplay magnitude is not.

**Why it matters**

Pack-specific regeneration can be under- or over-valued, particularly when Iron's native regen multiplier differs from its default. A blind constant adjustment could also introduce a new default-mode regression.

**The fix**

First add the measured integration comparison described above. Then define one explicit conversion model based on actual native scheduler cadence, event units, pool conversion rate, and native regen configuration. Separate nominal Ars event units from real mana per second. Pass or read the native settings from an Iron's-isolated adapter; keep the dependency-absent path safe. Account for zero/disabled regeneration without division by zero or non-finite modifiers. If the current default arithmetic is experimentally correct because both native loops double nominal values, preserve that result and document the cancellation accurately. Update all callers, unit tests, and config comments against the measured model, not a guessed factor.

**Acceptance criteria**

- A 20-tick measured native-versus-bridged bonus comparison passes in each direction at non-unit pool conversion rates.
- Repeat native Iron's multiplier 0.5/1/2, a disabled native multiplier if supported, Ars interval changes, and both equal-effect and reference-pool policies.
- Changing native config while online recomputes owned modifiers without accumulation.
- No full-pool clamping masks a bad rate in the tests; record actual delta and ceiling headroom.
- Do not mark this finding fixed solely because a helper round-trip returns its input.

**Dependencies on other findings:** Coordinate potion measurements with ANS-004 and retain its separate capacity/regen ownership rules.

### ANS-012: The Spell Loom client can construct a different slot layout when its block entity is absent

**Severity:** Medium  
**Category:** Networking / menu synchronization  
**Status:** Suspected

**Location and quoted evidence**

`src/main/java/com/otectus/arsnspells/menu/SpellLoomMenu.java:50-66`

```java
    public SpellLoomMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, resolve(inv, buf.readBlockPos()), buf.readUUID());
    }

    public SpellLoomMenu(int id, Inventory inv, SpellLoomBlockEntity be) {
        this(id, inv, be, java.util.UUID.randomUUID());
    }

    public SpellLoomMenu(int id, Inventory inv, SpellLoomBlockEntity be, java.util.UUID sessionId) {
        super(ModMenus.SPELL_LOOM.get(), id);
        this.sessionId = sessionId;
        this.blockEntity = be;
        this.access = be == null
            ? ContainerLevelAccess.NULL
            : ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());

        if (be != null) {
```

`src/main/java/com/otectus/arsnspells/menu/SpellLoomMenu.java:86-102`

```java
            addSlot(new SlotItemHandler(be.getItems(), SpellLoomBlockEntity.SLOT_OUTPUT,
                SLOT_OUTPUT_X, RECIPE_ROW_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }

        addPlayerInventory(inv);
    }

    private static SpellLoomBlockEntity resolve(Inventory inv, BlockPos pos) {
        if (inv.player.level().getBlockEntity(pos) instanceof SpellLoomBlockEntity loom) {
            return loom;
        }
        return null;
```

`src/main/java/com/otectus/arsnspells/menu/SpellLoomMenu.java:127-132`

```java
        // Audit E5 (defensive): if the client-side BE lookup failed, no working
        // slots were added and this.slots holds only the 36 player slots â€” the
        // index math below would then misclassify player slots 0-2 as loom slots.
        // Unreachable on the authoritative server (createMenu always passes a BE).
        if (this.slots.size() < SpellLoomBlockEntity.SLOT_COUNT + 36) {
            return result;
```

`src/main/java/com/otectus/arsnspells/menu/GlyphPressMenu.java:70-81`

```java
        boolean server = press != null && press.getLevel() != null && !press.getLevel().isClientSide();
        // Only the server's press is authoritative. A client always works on its own copy of the
        // slots, filled by the server's sync, so its slot layout never depends on whether the
        // block entity happened to arrive before the menu did.
        this.press = server ? press : null;
        this.access = server ? ContainerLevelAccess.create(press.getLevel(), press.getBlockPos()) : ContainerLevelAccess.NULL;
        this.data = server ? new ServerData() : new SimpleContainerData(D_COUNT);
        IItemHandler handler = server ? press.getItems() : new ItemStackHandler(GlyphPressBlockEntity.SLOT_COUNT);
        addSlot(new PressSlot(handler, GlyphPressBlockEntity.SLOT_SCROLL, SCROLL_X, SCROLL_Y));
        addSlot(new PressSlot(handler, GlyphPressBlockEntity.SLOT_PLATE, PLATE_X, PLATE_Y));
        addSlot(new PressSlot(handler, GlyphPressBlockEntity.SLOT_CATALYST, CATALYST_X, CATALYST_Y));
        addSlot(new PressSlot(handler, GlyphPressBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y));
```

**What is wrong**

The server constructs three Loom slots followed by 36 player slots. The client resolves the block entity from its current level; if resolution returns null, it constructs only the 36 player slots. The quick-move guard avoids one bad local index calculation but cannot change the slot numbering in the server's synchronization packets. `GlyphPressMenu` already uses a client-side dummy handler to maintain an identical layout regardless of block-entity arrival.

**To confirm:** create the client menu from its real opening buffer before the Loom block entity is present, then deliver the normal server menu-content packet and an incremental slot update. Observe whether the vanilla content application throws, shifts inventory entries, or closes the screen. A delayed-chunk/open-menu network test would establish whether normal multiplayer packet timing can reach this state. That graphical/network reproduction was not executed, so this is not labeled a confirmed production crash.

**Why it matters**

If the supported null-block-entity branch is reached, server slot 0 describes the Loom source while client slot 0 describes player inventory. The resulting packet layout mismatch can corrupt the displayed inventory or fail content application.

**The fix**

Always create the same three workstation slots and 36 player slots on both sides. Use the real block-entity handler only for the authoritative server; give the client a three-slot `ItemStackHandler`, filled by normal menu synchronization, as the Press already does. Decouple client preview planning from direct block-entity inventory access so it plans from the synchronized menu snapshot, or keep actions safely unavailable until a coherent snapshot arrives. Keep server `stillValid`, request identity, and inventory checks authoritative. Do not solve the problem by silently interpreting the first three player slots as work slots.

**Acceptance criteria**

- Client menu construction with a null block entity still produces exactly 39 slots in server order.
- Full-content and incremental synchronization work before and after the block entity arrives.
- Normal and shift-click transfers conserve items; a removed/out-of-range server Loom closes/refuses safely.
- Preview and server-result feedback work with the dummy client inventory and never mutate the server speculatively.

**Dependencies on other findings:** Coordinate with ANS-009 because client planning and result handling must use the same synchronized inventory.

### ANS-013: The required icon generation check depends on an unpinned PNG encoder

**Severity:** Medium  
**Category:** Build / reproducibility  
**Status:** Confirmed

**Location and quoted evidence**

`.github/workflows/ci.yml:38-46`

```yaml
      - uses: actions/setup-python@v5
        with:
          python-version: '3.12'
      - run: python3 -m pip install Pillow
      - name: Verify tooling and icon assets
        run: |
          python3 -m unittest discover -s tools -p "test_*.py"
          python3 tools/generate_icons.py --check
          python3 tools/verify_icons.py
```

`tools/generate_icons.py:24-39`

```python
def png(im):
    out = io.BytesIO(); im.save(out, format='PNG', optimize=False, compress_level=9)
    return out.getvalue()

def generated_asset_matches(path, content):
    if not path.exists():
        return False
    existing = path.read_bytes()
    if existing == content:
        return True
    if path.suffix != '.zip':
        return False
    try:
        with zipfile.ZipFile(io.BytesIO(existing)) as old, zipfile.ZipFile(io.BytesIO(content)) as new:
            return (old.namelist() == new.namelist()
                    and all(old.read(name) == new.read(name) for name in new.namelist()))
```

`tools/generate_icons.py:123-127`

```python
        for size, target in ((16,artifacts),(32,hd)):
            for state in STATES:
                path=row['resource_path'] if state=='normal' else PREFIX+f'textures/gui/icons/v2/variants/{state}/{family}/{name}.png'
                data=png(render(family,name,size,state)); target[path]=data
                hashes[f'{size}/{state}']=hashlib.sha256(data).hexdigest()
```

`tools/generate_icons.py:238-244`

```python
    mismatches=[]
    for path,content in outputs.items():
        if check:
            if not generated_asset_matches(path, content): mismatches.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(content)
    if mismatches: raise SystemExit('Generated files differ: '+', '.join(mismatches[:20]))
```

**What is wrong**

CI installs unconstrained Pillow, while the generator records SHA-256 hashes of encoded PNG bytes and compares archive entries byte-for-byte. A compatible encoder can produce identical pixels with different compressed bytes.

**Reproduced:** using Pillow 13.0.0, `python tools/generate_icons.py --check` exits nonzero with:

```text
Generated files differ: src/main/resources/assets/ars_n_spells/icon_manifest.json, resourcepacks/ars-n-spells-icons-hd32.zip
```

Generation into an external scratch copy showed 450 differing HD32 PNG entries whose decoded RGBA pixels were all identical; the additional changed ZIP member was provenance. The ordinary icon verifier passed. This is an encoder/provenance reproducibility problem, not evidence that the icon artwork is wrong. The audit did not inspect a live GitHub Actions run, so it does not claim a particular hosted run is currently red.

**Why it matters**

A clean supported environment can fail a blocking release check without a source or artwork change. Different developers can also produce noisy manifest/archive changes that obscure real art changes.

**The fix**

Choose and document a reproducibility contract. For byte-exact artifacts, pin the complete known-good authoring toolchain (Pillow and relevant PNG compression/runtime dependencies), record its versions, and run generation plus verification in that controlled environment. Alternatively, define a canonical decoded-pixel hash and metadata schema for semantic art verification while separately preserving provenance of published encoded files. Migrate the manifest/verifier deliberately if choosing pixel hashes. Do not merely delete the CI check, accept arbitrary mismatches, or regenerate hundreds of art files as an unexplained fix. Update `.github/workflows/ci.yml`, the generator, and its setup instructions together.

**Acceptance criteria**

- Two clean environments following the documented setup produce the same accepted output.
- The required Python 3.12 CI job completes generation and verification successfully.
- A genuine one-pixel change still fails verification until intentionally regenerated.
- Zip metadata differences cannot conceal changed entries, and a compression-only difference is handled according to the documented contract.
- Published source/provenance and the visual pixel content remain intact.

**Dependencies on other findings:** None.

### ANS-014: Removed Iron’s spell IDs pass availability checks through NoneSpell fallback

**Severity:** Low  
**Category:** Cross-mod API contract / missing registry entries  
**Status:** Confirmed

**Location and quoted evidence**

`src/main/java/com/otectus/arsnspells/spell/CrossCastValidator.java:139-145`

```java
    /**
     * Isolated to avoid loading Iron's classes when the mod isn't present.
     * Only called after a {@code ModList.isLoaded} check.
     */
    private static boolean ironSpellResolvable(ResourceLocation spellId) {
        return io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(spellId) != null;
    }
```

`src/main/java/com/otectus/arsnspells/spell/IronsRegistrySpellDescriptor.java:58-72`

```java
    @Override
    public Object resolve() {
        if (!ModList.get().isLoaded("irons_spellbooks") || spellId == null) {
            return null;
        }
        return io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(spellId);
    }

    @Override
    public CrossSpellType systemType() {
        return CrossSpellType.IRONS_SPELLBOOKS;
    }

    private static boolean ironResolvable(ResourceLocation id) {
        return io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(id) != null;
```

`src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java:390-399`

```java
        io.redspace.ironsspellbooks.api.spells.AbstractSpell spell =
            io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(spellId);

        if (spell == null) {
            LOGGER.warn("Cross-mod Iron spell not found: {}", spellId);
            return false;
        }

        int storedLevel = spellData.getInt(TAG_SPELL_LEVEL);
        if (storedLevel < 1 || storedLevel > spell.getMaxLevel()) return false;
```

Pinned dependency evidence: **Iron's Spellbooks 1.20.1-3.15.0**, `io/redspace/ironsspellbooks/api/registry/SpellRegistry.java:276-282`. Line numbers below are from the CFR 0.152 extraction of that exact artifact, not an upstream Git revision.

```java
    public static AbstractSpell getSpell(ResourceLocation resourceLocation) {
        AbstractSpell spell = (AbstractSpell)REGISTRY.get().getValue(resourceLocation);
        if (spell == null) {
            return noneSpell;
        }
        return spell;
    }
```

**What is wrong**

These paths treat a non-null result from `SpellRegistry.getSpell(id)` as proof that the requested spell exists. At the pinned Iron's 3.15.0 version, `io/redspace/ironsspellbooks/api/registry/SpellRegistry.java:276-282` returns its singleton `noneSpell` for an unknown ID. Thus `ironSpellResolvable` and `ironResolvable` return true for removed addon spell IDs, and `resolve()` returns a different spell from the requested one. The actual cast's null guard has the same mismatch. `commands/IronsHeldSpellInspection.java` already demonstrates the needed explicit NoneSpell exclusion.

**Why it matters**

Stored entries for a removed or renamed addon spell appear resolvable and proceed deeper into casting instead of receiving the intended unavailable-spell refusal. A later check or NoneSpell behavior may turn this into a no-op; no crash or resource loss was reproduced, so neither is claimed.

**The fix**

Add an Iron's-isolated exact lookup helper that verifies the registry contains the requested ID and excludes the `noneSpell` sentinel. Use it consistently for validation, descriptor availability/resolution, and the final cross-cast lookup. Compare the resolved registry identity to the requested ID if necessary. Return a localized unavailable-spell result before any payment, cooldown, effect, or progression credit. Preserve the original saved ID/payload so reinstalling the addon can restore the spell; do not silently rewrite it to the none ID or erase the entry.

The core exact-lookup rule can be implemented as the following proposed helper inside an optional Iron's-only class:

```java
private static AbstractSpell lookupExact(ResourceLocation id) {
    if (id == null) return null;
    AbstractSpell spell = SpellRegistry.getSpell(id);
    return spell != null && spell != SpellRegistry.none()
        && id.equals(spell.getSpellResource()) ? spell : null;
}
```

Import `AbstractSpell` and `SpellRegistry` from the pinned Iron's API only in that isolated class. Do not expose this class through a common static initializer when Iron's is absent.

**Acceptance criteria**

- A well-formed stored entry for `audit_missing:spell` is unavailable and cannot cast or spend resources.
- The native none sentinel itself cannot become a cross-cast spell.
- Reinstalling/registering the original ID restores availability without rewriting the carrier.
- Valid native and addon spells retain their exact ID and effective-level handling.
- Iron's-absent startup and descriptor inspection remain safe.

**Dependencies on other findings:** None.

### ANS-015: Current player/config documentation contradicts implemented behavior

**Severity:** Low  
**Category:** Documentation / configuration consistency  
**Status:** Confirmed

**Location and quoted evidence**

`README.md:233-236`

```markdown
- **Insufficient LP behavior** (`death_on_insufficient_lp`):
  - `false` (default) -- Spell is cancelled with 1 heart of damage.
  - `true` -- Spell casts but the player dies.
- LP costs scale with configurable base multipliers, per-tier multipliers (Ars glyph tiers), per-level and rarity multipliers (Iron's spells), and a minimum cost floor. Delayed-resolution spells stage costs in a per-player FIFO queue, so rapid back-to-back casts can't overwrite each other's pending cost.
```

`src/main/java/com/otectus/arsnspells/config/AnsConfig.java:666-671`

```java
        DEATH_ON_INSUFFICIENT_LP = BUILDER
            .comment(
                "If enabled: Spell casts but player dies when LP is insufficient",
                "If disabled: Spell is cancelled and player takes minor damage (1 heart)"
            )
            .define("death_on_insufficient_lp", false);
```

`README.md:425-425`

```markdown
| `source_jar_synergy_multiplier` | `5.0` | 0.1–100 | Multiplier for the proximity regen bonus. |
```

`src/main/java/com/otectus/arsnspells/config/AnsConfig.java:929-934`

```java
        SOURCE_JAR_SYNERGY_MULTIPLIER = BUILDER
            .comment("Multiplier for Source Jar proximity regen bonus.",
                     "Higher values = stronger regen when standing near Source Jars.",
                     "Final bonus = CONVERSION_RATE_ARS_TO_IRON * this value per second.",
                     "To disable the feature use enable_source_jar_synergy = false, not a zero multiplier.")
            .defineInRange("source_jar_synergy_multiplier", 5.0, 0.01, 2000.0);
```

`README.md:458-458`

```markdown
Mana Infusion grants its amount once, to the nearest player, on completion. Mana Well regenerates mana for every player in range on every ritual tick, which means one radius-`mana_well_range` player query per tick for the ritual's duration — in line with what Ars Nouveau's own area rituals do, but worth lowering the range on a busy server. Both pay into whichever pool the active mana unification mode treats as primary.
```

`src/main/java/com/otectus/arsnspells/rituals/ManaInfusionRitual.java:26-35`

```java
        // Was its own nearest-player-within-8 search. Now shared with the feedback path, so the
        // mana goes to the player who lit the brazier rather than to whoever happens to be closest
        // when it finishes -- the ritual burns for a few seconds, which is long enough for those
        // to be different people on a busy server.
        Player player = recipient();
        if (player == null) {
            return;
        }
        BridgeManager.getBridge().addMana(player,
            AnsConfig.RITUAL_MANA_INFUSION_AMOUNT.get().floatValue());
```

`README.md:603-603`

```markdown
*(Covenant of the Seven has no dedicated runtime profile; surface compatibility is verified by `CovenantJarSurfaceTest`, which reads the jar bytecode and skips when absent.)*
```

`gradle/covenant-profile.gradle:5-12`

```groovy
if (project.hasProperty('withCovenantRuntimeGameTests')) {
    if (!project.hasProperty('withIronsRuntimeGameTests')) {
        throw new GradleException('Covenant requires -PwithIronsRuntimeGameTests as well')
    }
    def directory = project.file(project.findProperty('covenantRuntimeDir') ?: 'libs/covenant-3.3.0').canonicalFile
    def lock = new File(directory, 'covenant-profile.json')
    if (!lock.isFile() || lock.length() > 262144) {
        throw new GradleException('Prepare the complete local dependency directory first: python tools/covenant_profile.py "' + directory + '" --write-lock. See docs/3.3.0/integration-matrix.md')
```

`src/main/java/com/otectus/arsnspells/casting/AlternativePayment.java:182-187`

```java
        // Check before taking. An insufficient balance denies before initiation, with no debit
        // at all - that is what makes "insufficient LP costs the player nothing" true.
        double available = access.current(playerId, unit);
        if (available + SHORTFALL_TOLERANCE < amount && policy != PaymentOpenFailurePolicy.LEGACY_OPEN) {
            LOGGER.debug("[ANS] {} leg denied before initiation: need {}, have {}", unit, amount, available);
            return new Result(Outcome.DENIED, null);
```

**What is wrong**

Several currently presented instructions are stale:

- README and generated config comments promise a one-heart punishment when `death_on_insufficient_lp=false`. `casting/AlternativePayment.java` refuses insufficient payment without that punishment; ordinary insufficient-payment tests intentionally require no debit. Do not reintroduce damage merely to match old prose.
- README gives `source_jar_synergy_multiplier` bounds 0.1–100, while `AnsConfig` accepts 0.01–2000. The enable-toggle comment at `AnsConfig.java:915` also still says a 0.1 minimum.
- README says Mana Infusion rewards the nearest player. Its implementation now calls the shared initiator-aware recipient logic, specifically to avoid rewarding a closer bystander.
- README says there is no Covenant runtime profile. `gradle/covenant-profile.gradle` implements the complete opt-in profile, which was successfully run in this audit.
- The current test-profile introduction still advertises 966 JUnit tests; this checkout produces 1061 total test cases (six skipped). Historical test records may retain their original dated counts, but current instructions should not present an older count as this checkout's suite.

The delayed-payment FIFO wording in the same README paragraph also conflicts with the current cast/occurrence-owned payment architecture and should be updated while editing it.

**Why it matters**

Players and pack authors configure the wrong expectations, misunderstand who receives ritual mana, or miss a real integration test route. Incorrect generated comments propagate into every new server config.

**The fix**

Update `README.md` and the current `AnsConfig` comments to the implemented policies: ordinary insufficient LP refuses without the claimed fixed damage; document the separately configured fatal/legacy alternatives accurately; use actual config bounds; explain initiator targeting and the real legacy/fallback behavior of `AnsRitual.recipient`; document the existing Covenant profile and its local hash-lock preparation. Link current 3.4.1 test instructions and derive suite counts from a dated test result, or omit brittle counts from general instructions. Replace FIFO terminology with the actual cast-owned payment lifecycle. Preserve dated historical reports as historical evidence rather than rewriting their claims to match this audit. Regenerate any current config reference that is intended to mirror `AnsConfig`.

**Acceptance criteria**

- Every corrected default/bound matches the executable config declaration.
- Insufficient-LP behavior described to players matches the existing payment tests and the corrected imported-glyph path.
- Two-player ritual verification awards the recorded initiator according to `AnsRitual.recipient` and documents its fallback separately.
- Covenant commands in current docs run from a prepared complete dependency directory and validate actual loaded versions.
- Historical reports are clearly dated, and current documentation does not imply unexecuted graphical/multiplayer tests were performed.

**Dependencies on other findings:** None.

## 4. Recommended fix order

1. **Establish the review baseline and usable checks.** Work from the audited SHA or explicitly map later changes to these findings. Preserve the current unit/GameTest results and exact dependency identities. Resolve ANS-013 early if its icon check blocks CI; it is independent of gameplay work. Do not commit the external reproduction probe unchanged as a passing regression: it deliberately asserts the exploit exists.
2. **Correct caster fixtures and ring-state ownership (ANS-002, ANS-003), then payment authorization (ANS-001).** These changes touch imported admission/execution, `SanctifiedLegacyCompat`, lifecycle eviction, and casting fixtures. First ensure positive tests use an admitted player representation and server equipment queries are trustworthy. Then enforce the captured alternate-resource route at effect time and make the delayed-ring regression fail before the fix and pass after it. Keep the native Covenant event path isolated from imported route scopes.
3. **Unify school identity and preserve clone state (ANS-005, ANS-006).** These are independent from the payment patch. Do not migrate saved school identities or change death-reset policy accidentally. Run cross-school cost/scaling/progression tests and both death/non-death clone cases.
4. **Measure regeneration, then repair potion ownership (ANS-011, ANS-004).** Resolve the suspected unit model with native scheduler measurements before changing constants. Fix the independently confirmed missing HYBRID potion regeneration route while keeping max-mana composition separate.
5. **Publish coherent client policy (ANS-007, ANS-008).** Implement the snapshot and client consumers together, apply the Virtue enablement rule, and verify real remote-client reloads. Batch any wire change into one documented protocol update.
6. **Repair Loom feedback and slot symmetry (ANS-009, ANS-012).** Prove or dismiss the suspected absent-block-entity failure. If confirmed, install the dummy client handler and make planning use synchronized menu data. Then verify server refusal precedence, retries, and stale-response rejection against that layout.
7. **Repair carrier availability/reconciliation (ANS-010, ANS-014).** Share exact Iron's spell resolution and handle missing sidecars conservatively. Exercise binding, native extraction, ritual uninscription, save/reload, addon removal, native-spell preservation, and future-schema refusal together.
8. **Refresh current documentation (ANS-015) and release evidence.** Incorporate final confirmed behavior and the measured regeneration decision. Preserve historical records. Run all affected tests plus the complete baseline profiles once the changes are assembled; run the real client checks needed for the UI/sync fixes and boot the packaged JAR. Do not accept an optional-profile pass in which the intended scenarios silently skipped.

### Reproducible baseline commands

Use Java 17 and Gradle 8.4. The audit used a separately provisioned Gradle 8.4 executable with the repository's unmodified build scripts because this execution environment needed explicit dependency proxy/trust settings; those environment settings are not part of the proposed mod fix. On an ordinary configured checkout, the wrapper commands below are the corresponding entry points.

```bash
python3 -m unittest discover -s tools -p 'test_*.py'
python3 tools/generate_icons.py --check
python3 tools/verify_icons.py
python3 tools/contract_parity.py
./gradlew test build
./gradlew runGameTestServer -PgametestRunDir=run-audit-absent
./gradlew runGameTestServer -PwithIronsRuntimeGameTests -PgametestRunDir=run-audit-irons
./gradlew runGameTestServer -PwithIronsRuntimeGameTests -PwithArsElemental -PwithTooManyGlyphs -PwithWindSpellbooks -PwithCatalogPackages=wind_spellbooks -PgametestRunDir=run-audit-addons
```

For Ars Zero, place the verified `ars_zero-1.20.1-2.0.2HOTFIX.jar` in the ignored `libs/` directory. For Covenant, place the six versioned artifacts listed in section 1 in a separate directory and run the repository's `tools/covenant_profile.py <directory> --write-lock` to inspect/hash them. Set `COVENANT_PROFILE_DIR` to that prepared directory. Do not copy a historical lock over different bytes or bypass hash validation.

```bash
./gradlew runGameTestServer -PwithIronsRuntimeGameTests -PwithCovenantRuntimeGameTests -PcovenantRuntimeDir="$COVENANT_PROFILE_DIR" -PgametestRunDir=run-audit-covenant
./gradlew runGameTestServer -PwithIronsRuntimeGameTests -PwithArsElemental -PwithArsZero -PwithTooManyGlyphs -PwithWindSpellbooks -PwithCatalogPackages=wind_spellbooks,irons_envelope_15 -PgametestRunDir=run-audit-zero-envelope
./gradlew runGameTestServer -PwithIronsRuntimeGameTests -PwithIrons316RuntimeGameTests -PwithArsElemental -PwithArsZero -PwithTooManyGlyphs -PwithWindSpellbooks -PwithCatalogPackages=wind_spellbooks,irons_envelope_15 -PwithCovenantRuntimeGameTests -PcovenantRuntimeDir="$COVENANT_PROFILE_DIR" -PgametestRunDir=run-audit-all-316
```

Preserve a separate disposable world per incompatible profile. Use `tools/verify_gametest_log.py` with the expected required/executed/skipped counts and actual mod identities. The 344 required-test count is the baseline, not a permanent magic number after adding regressions. `save01Phase` is a separate multi-run experiment, not covered by these default passes.

### Exact ANS-001 reproduction fixture

The following code was compiled from an external source directory through a temporary Gradle init script. It uses existing package-private test helpers, the real item-use path, a native imported Heal, and the actual Covenant stack. It establishes a server-side defect; it does not substitute for a remote-client test. Keep it outside the release source set, or convert it into a proper regression with opposite assertions and the ANS-002 fixture correction.

External init script, with the absolute directory replaced by your own external probe directory:

```groovy
allprojects {
    afterEvaluate {
        sourceSets.main.java.srcDir('/absolute/path/to/audit-probes')
    }
}
```

Place the Java class below at `com/otectus/arsnspells/gametest/AuditAuraRouteProbe.java` inside that directory. Run the Covenant profile with `-I /absolute/path/to/audit-probes.gradle` and a fresh run directory. The checked baseline emitted `AUDIT_AURA_SWAP health=15.0 auraBefore=999990 auraAfter=999990 mana=0.0`.

```java
package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectDelay;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.*;

@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class AuditAuraRouteProbe {
    @BeforeBatch(batch = "audit_aura_route")
    public static void before(ServerLevel level) { GlyphRouteGameTests.before(level); }
    @AfterBatch(batch = "audit_aura_route")
    public static void after(ServerLevel level) { GlyphRouteGameTests.after(level); }

    @GameTest(template = "platform", batch = "audit_aura_route", timeoutTicks = 200)
    public static void delayedHealAfterRemovingVirtueRingIsFree(GameTestHelper helper) {
        var player = TouchRegressionGameTests.aimingPlayer(helper, "audit_aura_swap");
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(10.0f);
        TouchRegressionGameTests.fundArs(player);
        IronsGlyphTestSupport.clearCooldowns(player);
        var curios = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve().orElseThrow();
        var slot = curios.getCurios().get("ring");
        slot.getStacks().setStackInSlot(0, new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation("covenant_of_the_seven:virtue_ring"))));
        SanctifiedLegacyCompat.clearCacheFor(player.getUUID());
        helper.assertTrue(SanctifiedLegacyCompat.isWearingVirtueRing(player), "ring is initially worn");
        IronsProxyCastDriver.setIronsMana(player, 0.0f);
        ImportedGlyphGameTests.cast(helper, player, new Spell(MethodSelf.INSTANCE, EffectDelay.INSTANCE, ImportedGlyphGameTests.glyph("irons_spellbooks:heal", 1)));
        helper.assertTrue(player.getHealth() == 10.0f, "heal is delayed");
        slot.getStacks().setStackInSlot(0, ItemStack.EMPTY);
        SanctifiedLegacyCompat.clearCacheFor(player.getUUID());
        int auraBefore = SanctifiedLegacyCompat.getCovenantAura(player);
        int lpBefore = SanctifiedLegacyCompat.getBloodMagicLP(player);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(player.getHealth() > 10.0f, "heal runs after ring removal"))
            .thenExecute(() -> {
                int auraAfter = SanctifiedLegacyCompat.getCovenantAura(player);
                System.out.println("AUDIT_AURA_SWAP health=" + player.getHealth() + " auraBefore=" + auraBefore + " auraAfter=" + auraAfter + " mana=" + IronsProxyCastDriver.ironsMana(player));
                helper.assertTrue(auraAfter >= auraBefore, "no aura was consumed");
                helper.assertTrue(SanctifiedLegacyCompat.getBloodMagicLP(player) == lpBefore, "no LP consumed");
                helper.assertTrue(IronsProxyCastDriver.ironsMana(player) == 0.0f, "no mana consumed");
            }).thenSucceed();
    }
}
```

## 5. Open questions

These are product-policy choices or measurements, not reasons to postpone the confirmed fixes. The recommended defaults below make the implementation actionable without another design interview.

| Question | Recommended implementation default | When author input would change the fix |
| --- | --- | --- |
| What happens when alternate-resource equipment changes after a delayed cast is admitted? | Preserve the admitted route and refuse an unauthorized future emission before its effect; release only unspent funds. Never silently charge a new resource. | Supporting intentional route migration would need a separate pricing/authorization design and tests. |
| Should a custom item in the Virtue tag work as a full aura provider? | Do not mark native delegation paid unless Covenant will actually handle it. Refuse with a clear reason until an owned adapter can charge it correctly. | Full custom-ring support requires explicitly implementing that adapter; tag membership alone is insufficient. |
| What defines Automatic primary school for multi-school spells? | Use the current ordered namespaced `schoolKey()` primary consistently across all consumers; preserve saved tracks and explicit override behavior. | A new priority scheme would be a balance/migration change rather than a consistency fix. |
| Should fake-player automation be supported? | Follow the published real-player-only contract and reject Forge/Ars fake players. | If support is desired, design explicit ownership, permissions, resources, and lifetime rules instead of removing a guard. |
| What does equal-effect regeneration mean under native multiplier/interval changes? | Match measured native behavior under the same supported settings; preserve current balance where measurements show it is correct. | A deliberately normalized balance policy must be named/documented separately from native equivalence. |
| Should cooldowns reset on death? | Keep the documented death reset; preserve them on non-death Clone/End return. | Persisting death cooldowns would be a separate gameplay change. |

No issue is inferred solely from a TODO, a historical finding, a broad version range, or a test that was not run. Suspected findings ANS-011 and ANS-012 must be confirmed or explicitly dismissed with the stated observations before making behavioral changes based on them.
## 6. Coverage log

### Scope, method, and gaps

The inventory is the complete `git ls-files` list at the audited commit: **3,398 tracked files**. All were read as bytes and hashed. Text was decoded; 1,282 `.json` files plus two `.mcmeta` files were parsed with duplicate-key rejection; 1,488 PNGs were decoded; all three ZIPs and the tracked wrapper JAR passed CRC/member checks. Source read ranges were recorded while files were opened. Build outputs, dependency downloads, generated external probes, and disposable run worlds are audit intermediates rather than tracked project files.

**Manual semantic coverage is incomplete outside production code.** All 344 non-GameTest production Java files were opened in full. All Python tools/scripts were also opened by the end of the review. The unit suite and GameTest profiles were executed, but many test-source files were only byte-read/compiled/executed and were not fully inspected line by line. Numerous historical design/audit documents and generated reference tables were decoded but not fully semantically reviewed. The per-file list records these limits explicitly; no conclusion about unread content is fabricated.

The following further verification was not completed:

- A real client connected to a dedicated server, two-client reloads, integrated-server inventory races, menu packet delay/reordering, keyboard/narration/accessibility, and the JEI/EMI/Durability Viewer UI matrix.
- Visual review of every icon variant, model render, animation, screenshot, and resource-pack combination. Binary validity and pixel equality checks are not visual acceptance.
- The multi-process `save01Phase` identity-package removal/reinstall experiment; arbitrary existing-world removal of Iron's or other gameplay mods; cross-Minecraft-version conversion; hard-crash recovery under every partial-save point.
- Per-spell execution of all 111 native Iron's spells, every registered/reserved level, all addon combinations, every channel/recast/projectile method, or all third-party protection/claim handlers. The catalog's registration tests and representative effect tests do not imply this coverage.
- Every dependency version admitted by the metadata ranges, every third-party mod named in historical docs, and a complete audit of upstream dependencies' internals. Actual direct integration surfaces were checked at the versions listed in section 1.
- A separate launch from the packaged release JAR, long-duration memory/leak/load profiling, hostile-network fuzzing, and old Forge versions below the tested 47.4.10.
- Full semantic re-audit of the other NeoForge branch; no parity claim is inferred from running the local contract manifest.

The reasons are distinct: graphical/multiplayer and long-running experiments were not executed; historical docs and many test sources did not receive a complete semantic pass within this audit; binary files require format/visual review instead of pretending their bytes are source text. No tracked file was inaccessible. This log makes the remaining work explicit rather than labeling the audit universally exhaustive.

### Feature trace ledger

| End-to-end chain | Reviewed links | Conclusion within checked scope |
| --- | --- | --- |
| Native Ars / Iron's mana casting | Registration → event/mixin quote → native expenditure boundary → ledger → effect → completion/refund | Baseline profiles passed; alternate imported route defect ANS-001 and school mismatch ANS-005 remain |
| Imported glyph lifecycle | Catalog → part/item registration → learning/live behavior → player admission → reservation/session → delayed/native child → payment/effect → cooldown/cleanup | Representative tests passed; ANS-001/002/003 need correction; not every spell executed |
| Glyph Press | Registered block/item/menu → native-scroll inspection → plan → Source reservation → persisted job → completion/cancel → menu/JEI snapshots | Existing headless job/identity/consumption tests passed; remote UI and long-running scale not verified |
| Loom/export/binding/extraction | Block/menu → request identity → source/target plan → carrier/sidecar → proxy pool/native table → saved book → cross-cast / uninscription | Existing native table and preservation tests passed; ANS-009/010/012/014 remain |
| Schools and progression | Metadata/overlays → analysis → cost/scaling → successful-cast credit → capability/save/clone → client journal | Namespaced and legacy primary disagree, ANS-005; no other proven progression defect in inspected paths |
| Cooldowns | Successful cast → category end time → capability serialization → clone/reload → admission | Non-death Clone copy omission, ANS-006; death reset kept intentionally |
| Equipment and regen | Equipment/Curios/potion change → owned modifiers → native/shared maxima/regen → mode reload/cleanup → synchronized attributes/HUD | ANS-003/004/007/008; unit model ANS-011 requires measurement |
| Rituals | Registration/tablet recipe → brazier start → persisted initiator/progress → completion → inventory/mana/feedback | Headless ritual tests passed; current docs misdescribe recipient, ANS-015 |
| Reload and networking | Forge/server config → refresh → bounded ANS packets → accepted generation → screen/HUD | Existing request validation sound in tested cases; incomplete client policy and refusal handling ANS-007/009 |
| Build/resources | Exact dependency resolution → compile/tests → resources/refmap → jar-in-jar/reobfuscation → artifact inspection | Build/package passed; image generation check fails under observed Pillow, ANS-013 |

### Coverage status key

| Code | Meaning |
| --- | --- |
| R | Entire text opened for source/content review; this does not mean every branch was runtime-tested. |
| P | Only the recorded line ranges were opened for semantic review; all bytes were still read/decoded. |
| D | Entire JSON/mcmeta parsed and structurally checked. Some core resources were additionally inspected, but this code does not claim every generated entry was independently semantically audited. |
| B | Binary format decoded/validated as stated; no claim of complete visual or binary implementation review. |
| S | Entire file byte-read/decoded; full semantic inspection was not completed. These are explicit coverage gaps. |

Every tracked file appears once below. Paths are repository-relative. R/P line ranges refer to the audited commit. The source snippets in findings are exact, independently validated slices from those files.

Status totals: **R: 407**, **P: 7**, **D: 1284**, **B: 1492**, **S: 208**.

#### Pinned artifact hashes used for dependency evidence

These identify the actual original JAR bytes used for inspection/loading, before ForgeGradle remapping.

| Artifact | SHA-256 |
| --- | --- |
| `ars-4.12.7.jar` | `1f1debc282a0c379c1141f2840ea294eede6f6b544c589663f40bbe17b59a1af` |
| `irons-3.15.0.jar` | `92c046383b4960c655f840d8846732a481edcf7c5ed89028b3d7b2cc2910b224` |
| `curios-5.14.1.jar` | `6d77ae8ad532fdf303390f404b0081b3b4ac4f61e7f1f4b4d4a9077e132dae4f` |
| `ars_zero-1.20.1-2.0.2HOTFIX.jar` | `813236ce9294bab1be69503e299d671e6b7aaab9f2027687e0f67631a2df99f4` |
| `irons-spells-n-spellbooks-855414-8680180.jar` | `54b5aaa52887c38f570fbd820288171234d8541c17a5e953f9271189fdd480fb` |
| `ars-elemental-561470-8399870.jar` | `2172db18ab7a4f43da49a2bb245b3d90f1c6cd8f7ce7905870dbc60e3f5ad40b` |
| `too-many-glyphs-560595-4813803.jar` | `8746ce72e1c4e9eac28dc40a26fffa56189efdcbb38ffa752f0fc3d7fadbb33e` |
| `winds-spellbooks-Rnn55z6s.jar` | `5b8f10cf207332395e6f6b971735f8e8575ed5d4f6ca515726ff035d76cee676` |
| `EnigmaticLegacy-2.30.1.jar` | `cf8488350ccb03fdc5e1f3508a30137234b6dc85ea258b9ee37ceaa485f34024` |
| `NaturesAura-39.4.jar` | `df1c4dffce5d02695031c674e6fd7b34b8a67cece88400c48e942bb27b8fff6d` |
| `Patchouli-1.20.1-81-FORGE.jar` | `979c6466c5a1c0151e8c9791a08bf5d2a42c2ec4c7fa8c4b9a34bb071b175753` |
| `bloodmagic-1.20.1-3.3.3-45.jar` | `87e2cf59e61875360d5a69417c510a631404bf0a32338f5e6ad820bcb9c86f75` |
| `caelus-forge-3.1.0+1.20.jar` | `e7d9c0bd0be90331c9d77d2b7f8df9054df88ea663f95addbbaae543d24fb92a` |
| `covenant_of_the_seven-2.2.6-hotfix.jar` | `07de8088ad8235226d4256d9137b3c0985597227b785c3434c650f7b866cd411` |

#### Per-file coverage

| File | Status | Review/validation extent |
| --- | --- | --- |
| `.github/workflows/ci.yml` | R | Text opened in full, lines 1-142 |
| `.gitignore` | R | Text opened in full, lines 1-45 |
| `AUDIT.md` | R | Text opened in full, lines 1-1549 |
| `AUDIT_ARCHITECTURE.md` | R | Text opened in full, lines 1-122 |
| `AUDIT_FINDINGS.md` | R | Text opened in full, lines 1-87 |
| `AUDIT_FIX_PLAN.md` | R | Text opened in full, lines 1-54 |
| `Ars_n_Spells_3.4.0_Implementation_Instructions.md` | P | Opened lines 1-105; remaining ranges not semantically reviewed |
| `CHANGELOG.md` | P | Opened lines 1-400; remaining ranges not semantically reviewed |
| `CURSEFORGE_DESCRIPTION.md` | S | All bytes read and UTF-8 decoded (284 lines); semantic read not completed |
| `LICENSE` | S | All bytes read and UTF-8 decoded (674 lines); semantic read not completed |
| `MODMAP.md` | S | All bytes read and UTF-8 decoded (170 lines); semantic read not completed |
| `NEXT_MAJOR_UPDATE_PLAN.md` | S | All bytes read and UTF-8 decoded (748 lines); semantic read not completed |
| `README.md` | R | Text opened in full, lines 1-613 |
| `TESTING_GUIDE.md` | S | All bytes read and UTF-8 decoded (750 lines); semantic read not completed |
| `TESTING_REPORT.md` | S | All bytes read and UTF-8 decoded (56 lines); semantic read not completed |
| `ans-3.0.0-update-phase-1.md` | S | All bytes read and UTF-8 decoded (433 lines); semantic read not completed |
| `ars-n-spells-3.4.1-improvement-instructions.md` | R | Text opened in full, lines 1-510 |
| `build.gradle` | R | Text opened in full, lines 1-307 |
| `catalogs/irons_envelope_15/adapters.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `catalogs/irons_envelope_15/inventory.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `catalogs/irons_envelope_15/package.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `catalogs/wind_spellbooks/adapters.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `catalogs/wind_spellbooks/inventory.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `catalogs/wind_spellbooks/package.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `contract-manifest.txt` | S | All bytes read and UTF-8 decoded (36 lines); semantic read not completed |
| `docs/3.3.0-audit-status.md` | S | All bytes read and UTF-8 decoded (94 lines); semantic read not completed |
| `docs/3.3.0-icons/README.md` | S | All bytes read and UTF-8 decoded (28 lines); semantic read not completed |
| `docs/3.3.0-icons/backgrounds.png` | B | PNG decoded 660x150 RGBA; no complete visual/runtime review |
| `docs/3.3.0-icons/carrier.png` | B | PNG decoded 880x278 RGB; no complete visual/runtime review |
| `docs/3.3.0-icons/compat.png` | B | PNG decoded 880x442 RGB; no complete visual/runtime review |
| `docs/3.3.0-icons/element.png` | B | PNG decoded 880x360 RGB; no complete visual/runtime review |
| `docs/3.3.0-icons/resource.png` | B | PNG decoded 880x442 RGB; no complete visual/runtime review |
| `docs/3.3.0-icons/ritual.png` | B | PNG decoded 880x114 RGB; no complete visual/runtime review |
| `docs/3.3.0-icons/school.png` | B | PNG decoded 880x278 RGB; no complete visual/runtime review |
| `docs/3.3.0-icons/spell.png` | B | PNG decoded 880x1836 RGB; no complete visual/runtime review |
| `docs/3.3.0-icons/status.png` | B | PNG decoded 880x442 RGB; no complete visual/runtime review |
| `docs/3.3.0-icons/ui.png` | B | PNG decoded 880x934 RGB; no complete visual/runtime review |
| `docs/3.3.0/addon-adapter-kit.md` | S | All bytes read and UTF-8 decoded (35 lines); semantic read not completed |
| `docs/3.3.0/config-reference.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `docs/3.3.0/config-reference.md` | S | All bytes read and UTF-8 decoded (786 lines); semantic read not completed |
| `docs/3.3.0/gui-overhaul.md` | S | All bytes read and UTF-8 decoded (86 lines); semantic read not completed |
| `docs/3.3.0/gui-previews/forge/details.png` | B | PNG decoded 1920x1009 RGBA; no complete visual/runtime review |
| `docs/3.3.0/gui-previews/forge/icons.png` | B | PNG decoded 1280x960 RGBA; no complete visual/runtime review |
| `docs/3.3.0/gui-previews/forge/journal.png` | B | PNG decoded 1280x960 RGBA; no complete visual/runtime review |
| `docs/3.3.0/gui-previews/forge/loom.png` | B | PNG decoded 1280x960 RGBA; no complete visual/runtime review |
| `docs/3.3.0/gui-previews/forge/settings.png` | B | PNG decoded 1280x960 RGBA; no complete visual/runtime review |
| `docs/3.3.0/gui-previews/neoforge/details.png` | B | PNG decoded 1280x960 RGBA; no complete visual/runtime review |
| `docs/3.3.0/gui-previews/neoforge/icons.png` | B | PNG decoded 1280x960 RGBA; no complete visual/runtime review |
| `docs/3.3.0/gui-previews/neoforge/journal.png` | B | PNG decoded 1280x960 RGBA; no complete visual/runtime review |
| `docs/3.3.0/gui-previews/neoforge/loom.png` | B | PNG decoded 1280x960 RGBA; no complete visual/runtime review |
| `docs/3.3.0/gui-previews/neoforge/settings.png` | B | PNG decoded 1280x960 RGBA; no complete visual/runtime review |
| `docs/3.3.0/pack-author-reference.md` | S | All bytes read and UTF-8 decoded (97 lines); semantic read not completed |
| `docs/3.3.0/pack-presets.md` | S | All bytes read and UTF-8 decoded (29 lines); semantic read not completed |
| `docs/3.3.0/player-guide.md` | S | All bytes read and UTF-8 decoded (69 lines); semantic read not completed |
| `docs/3.3.0/recipe-reference.md` | S | All bytes read and UTF-8 decoded (241 lines); semantic read not completed |
| `docs/3.3.0/school-inscription-evidence.md` | S | All bytes read and UTF-8 decoded (30 lines); semantic read not completed |
| `docs/3.3.2-validation.md` | S | All bytes read and UTF-8 decoded (76 lines); semantic read not completed |
| `docs/3.3.3/spell-loom-diamond-repair-prompt.txt` | S | All bytes read and UTF-8 decoded (19 lines); semantic read not completed |
| `docs/3.3.3/spell-loom-materials.png` | B | PNG decoded 1536x1024 RGB; no complete visual/runtime review |
| `docs/3.3.3/spell-loom-preview.png` | B | PNG decoded 1200x850 RGB; no complete visual/runtime review |
| `docs/3.3.3/spell-loom-top-repaired-source.png` | B | PNG decoded 1254x1254 RGB; no complete visual/runtime review |
| `docs/3.3.3/spell-loom.md` | S | All bytes read and UTF-8 decoded (90 lines); semantic read not completed |
| `docs/3.3.4/casting-repair-root-cause.md` | S | All bytes read and UTF-8 decoded (44 lines); semantic read not completed |
| `docs/3.3.4/casting-repair-testing.md` | S | All bytes read and UTF-8 decoded (75 lines); semantic read not completed |
| `docs/3.3.4/covenant-profile.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `docs/3.3.4/evidence-index.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `docs/3.3.4/forge-runtime-inventory.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `docs/3.3.4/neoforge-runtime-inventory.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `docs/3.3.4/payment-boundary-probe-results.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `docs/3.3.4/release-dependencies.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `docs/3.3.5/config-reference.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `docs/3.3.5/config-reference.md` | S | All bytes read and UTF-8 decoded (856 lines); semantic read not completed |
| `docs/3.3.5/parity-inventory.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `docs/3.3.5/parity-review.md` | S | All bytes read and UTF-8 decoded (148 lines); semantic read not completed |
| `docs/3.3.5/recipe-reference.md` | S | All bytes read and UTF-8 decoded (260 lines); semantic read not completed |
| `docs/3.3.5/runtime-reconciliation.md` | S | All bytes read and UTF-8 decoded (129 lines); semantic read not completed |
| `docs/3.3.5/testing.md` | S | All bytes read and UTF-8 decoded (118 lines); semantic read not completed |
| `docs/3.4.0/adapter-authoring.md` | S | All bytes read and UTF-8 decoded (135 lines); semantic read not completed |
| `docs/3.4.0/config-reference-notes.md` | S | All bytes read and UTF-8 decoded (74 lines); semantic read not completed |
| `docs/3.4.0/config-reference.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `docs/3.4.0/config-reference.md` | S | All bytes read and UTF-8 decoded (1031 lines); semantic read not completed |
| `docs/3.4.0/glyph-press-preview.png` | B | PNG decoded 1200x850 RGB; no complete visual/runtime review |
| `docs/3.4.0/glyph-press.md` | S | All bytes read and UTF-8 decoded (152 lines); semantic read not completed |
| `docs/3.4.0/inventory/irons-1.20.1-3.15.0.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `docs/3.4.0/inventory/irons-1.20.1-3.16.3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `docs/3.4.0/iron-glyph-coverage.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `docs/3.4.0/iron-glyph-coverage.md` | S | All bytes read and UTF-8 decoded (159 lines); semantic read not completed |
| `docs/3.4.0/press-review/08-press-ready.png` | B | PNG decoded 941x1122 RGBA; no complete visual/runtime review |
| `docs/3.4.0/press-review/09-press-help.png` | B | PNG decoded 941x1122 RGBA; no complete visual/runtime review |
| `docs/3.4.0/press-review/10-press-engraving.png` | B | PNG decoded 941x1122 RGBA; no complete visual/runtime review |
| `docs/3.4.0/press-review/11-press-working-world.png` | B | PNG decoded 941x1122 RGBA; no complete visual/runtime review |
| `docs/3.4.0/press-review/12-press-done.png` | B | PNG decoded 941x1122 RGBA; no complete visual/runtime review |
| `docs/3.4.0/press-review/13-glyph-hotbar.png` | B | PNG decoded 941x1122 RGBA; no complete visual/runtime review |
| `docs/3.4.0/recipe-reference.md` | S | All bytes read and UTF-8 decoded (322 lines); semantic read not completed |
| `docs/3.4.0/showcase/01-workshop.png` | B | PNG decoded 1920x1080 RGB; no complete visual/runtime review |
| `docs/3.4.0/showcase/02-glyph-press.png` | B | PNG decoded 1920x1080 RGB; no complete visual/runtime review |
| `docs/3.4.0/showcase/03-engraving-in-world.png` | B | PNG decoded 1920x1080 RGB; no complete visual/runtime review |
| `docs/3.4.0/showcase/04-working-front.png` | B | PNG decoded 1920x1080 RGB; no complete visual/runtime review |
| `docs/3.4.0/showcase/05-under-the-archwood.png` | B | PNG decoded 1920x1080 RGB; no complete visual/runtime review |
| `docs/3.4.0/showcase/06-gui-ready-tier-1.png` | B | PNG decoded 1920x1080 RGB; no complete visual/runtime review |
| `docs/3.4.0/showcase/07-gui-ready-tier-3.png` | B | PNG decoded 1920x1080 RGB; no complete visual/runtime review |
| `docs/3.4.0/showcase/08-gui-engraving.png` | B | PNG decoded 1920x1080 RGB; no complete visual/runtime review |
| `docs/3.4.0/showcase/09-gui-finished-glyph.png` | B | PNG decoded 1920x1080 RGB; no complete visual/runtime review |
| `docs/3.4.0/showcase/10-gui-help.png` | B | PNG decoded 1920x1080 RGB; no complete visual/runtime review |
| `docs/3.4.0/showcase/alternates/close-idle.png` | B | PNG decoded 1920x1080 RGB; no complete visual/runtime review |
| `docs/3.4.0/showcase/alternates/front-idle.png` | B | PNG decoded 1920x1080 RGB; no complete visual/runtime review |
| `docs/3.4.0/showcase/alternates/glyph-press-jars.png` | B | PNG decoded 1920x1080 RGB; no complete visual/runtime review |
| `docs/3.4.0/showcase/alternates/gui-finished-no-tooltip.png` | B | PNG decoded 1920x1080 RGB; no complete visual/runtime review |
| `docs/3.4.0/showcase/alternates/working-hero.png` | B | PNG decoded 1920x1080 RGB; no complete visual/runtime review |
| `docs/3.4.0/testing.md` | S | All bytes read and UTF-8 decoded (222 lines); semantic read not completed |
| `docs/3.4.0/touch-regression.md` | S | All bytes read and UTF-8 decoded (99 lines); semantic read not completed |
| `docs/3.4.1/casting-contract.md` | R | Text opened in full, lines 1-137 |
| `docs/3.4.1/catalog-packages.md` | R | Text opened in full, lines 1-200 |
| `docs/3.4.1/identity-and-levels.md` | R | Text opened in full, lines 1-206 |
| `docs/3.4.1/iron-glyph-coverage.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `docs/3.4.1/iron-glyph-coverage.md` | R | Text opened in full, lines 1-162 |
| `docs/3.4.1/mapping-schema.md` | R | Text opened in full, lines 1-132 |
| `docs/3.4.1/testing.md` | R | Text opened in full, lines 1-249 |
| `docs/Ars-n-Spells-3.3.5-implementation-brief.md` | S | All bytes read and UTF-8 decoded (117 lines); semantic read not completed |
| `docs/Ars_n_Spells_Durability_Viewer_HUD_Fix.md` | S | All bytes read and UTF-8 decoded (211 lines); semantic read not completed |
| `docs/ars-n-spells-casting-repair-instructions.md` | S | All bytes read and UTF-8 decoded (325 lines); semantic read not completed |
| `docs/ars-n-spells-review-2026-11-09-implementation-brief.md` | S | All bytes read and UTF-8 decoded (463 lines); semantic read not completed |
| `docs/audit-2026-09-05/IMPLEMENTATION_SPEC.md` | S | All bytes read and UTF-8 decoded (704 lines); semantic read not completed |
| `docs/audit-2026-09-05/IMPLEMENTATION_SPEC.template.md` | S | All bytes read and UTF-8 decoded (704 lines); semantic read not completed |
| `docs/audit-2026-09-05/build_audit_artifacts.py` | R | Text opened in full, lines 1-229 |
| `docs/audit-2026-09-05/builtin-school-mappings.csv` | S | All bytes read and UTF-8 decoded (46 lines); semantic read not completed |
| `docs/audit-2026-09-05/current-assets.csv` | S | All bytes read and UTF-8 decoded (67 lines); semantic read not completed |
| `docs/audit-2026-09-05/current-icon-contact-sheet.png` | B | PNG decoded 880x664 RGB; no complete visual/runtime review |
| `docs/audit-2026-09-05/dependency-evidence.csv` | S | All bytes read and UTF-8 decoded (8 lines); semantic read not completed |
| `docs/audit-2026-09-05/planned-icons.csv` | S | All bytes read and UTF-8 decoded (275 lines); semantic read not completed |
| `docs/audit-2026-09-05/source-inventory.csv` | S | All bytes read and UTF-8 decoded (539 lines); semantic read not completed |
| `docs/audit-2026-09-05/static-resource-checks.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `docs/audit-2026-09-05/validation.md` | S | All bytes read and UTF-8 decoded (106 lines); semantic read not completed |
| `docs/payment_boundary_probe.py` | R | Text opened in full, lines 1-62 |
| `examples/addon-adapter/ars-elemental.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `gradle.properties` | R | Text opened in full, lines 1-65 |
| `gradle/covenant-profile.gradle` | R | Text opened in full, lines 1-53 |
| `gradle/wrapper/gradle-wrapper.jar` | B | Archive CRC checked 56 entries; member JSON/PNG decoded where present; no full binary-code review |
| `gradle/wrapper/gradle-wrapper.properties` | R | Text opened in full, lines 1-7 |
| `gradlew` | S | All bytes read and UTF-8 decoded (108 lines); semantic read not completed |
| `gradlew.bat` | S | All bytes read and UTF-8 decoded (83 lines); semantic read not completed |
| `project-icon-128.png` | B | PNG decoded 128x128 RGBA; no complete visual/runtime review |
| `project-icon-32-512.png` | B | PNG decoded 512x512 RGBA; no complete visual/runtime review |
| `project-icon-32.png` | B | PNG decoded 32x32 RGBA; no complete visual/runtime review |
| `project-icon-64.png` | B | PNG decoded 64x64 RGBA; no complete visual/runtime review |
| `project-icon.png` | B | PNG decoded 512x512 RGBA; no complete visual/runtime review |
| `resourcepacks/ars-n-spells-icons-hd32.zip` | B | Archive CRC checked 1383 entries; member JSON/PNG decoded where present; no full binary-code review |
| `resourcepacks/ars-n-spells-icons-high-contrast.zip` | B | Archive CRC checked 1410 entries; member JSON/PNG decoded where present; no full binary-code review |
| `resourcepacks/ars-n-spells-icons-monochrome.zip` | B | Archive CRC checked 1410 entries; member JSON/PNG decoded where present; no full binary-code review |
| `settings.gradle` | R | Text opened in full, lines 1-10 |
| `src/main/java/com/otectus/arsnspells/ArsNSpells.java` | R | Text opened in full, lines 1-463 |
| `src/main/java/com/otectus/arsnspells/affinity/AffinityBonuses.java` | R | Text opened in full, lines 1-22 |
| `src/main/java/com/otectus/arsnspells/affinity/AffinityCalculator.java` | R | Text opened in full, lines 1-22 |
| `src/main/java/com/otectus/arsnspells/affinity/AffinityType.java` | R | Text opened in full, lines 1-58 |
| `src/main/java/com/otectus/arsnspells/augmentation/ResonanceManager.java` | R | Text opened in full, lines 1-111 |
| `src/main/java/com/otectus/arsnspells/augmentation/ResonanceState.java` | R | Text opened in full, lines 1-23 |
| `src/main/java/com/otectus/arsnspells/block/GlyphPressBlock.java` | R | Text opened in full, lines 1-171 |
| `src/main/java/com/otectus/arsnspells/block/GlyphPressBlockEntity.java` | R | Text opened in full, lines 1-821 |
| `src/main/java/com/otectus/arsnspells/block/SpellLoomBlock.java` | R | Text opened in full, lines 1-101 |
| `src/main/java/com/otectus/arsnspells/block/SpellLoomBlockEntity.java` | R | Text opened in full, lines 1-150 |
| `src/main/java/com/otectus/arsnspells/bridge/AnsFeatureCleanup.java` | R | Text opened in full, lines 1-143 |
| `src/main/java/com/otectus/arsnspells/bridge/AnsModifierIdentities.java` | R | Text opened in full, lines 1-120 |
| `src/main/java/com/otectus/arsnspells/bridge/ArsNativeBridge.java` | R | Text opened in full, lines 1-65 |
| `src/main/java/com/otectus/arsnspells/bridge/ArsRegenTickScope.java` | R | Text opened in full, lines 1-55 |
| `src/main/java/com/otectus/arsnspells/bridge/BridgeManager.java` | R | Text opened in full, lines 1-493 |
| `src/main/java/com/otectus/arsnspells/bridge/IManaBridge.java` | R | Text opened in full, lines 1-39 |
| `src/main/java/com/otectus/arsnspells/bridge/IronsBridge.java` | R | Text opened in full, lines 1-217 |
| `src/main/java/com/otectus/arsnspells/bridge/IronsRegenScope.java` | R | Text opened in full, lines 1-41 |
| `src/main/java/com/otectus/arsnspells/bridge/ManaAccessDirection.java` | R | Text opened in full, lines 1-4 |
| `src/main/java/com/otectus/arsnspells/bridge/ManaOwnerBinding.java` | R | Text opened in full, lines 1-8 |
| `src/main/java/com/otectus/arsnspells/bridge/ManaRegenBridge.java` | R | Text opened in full, lines 1-142 |
| `src/main/java/com/otectus/arsnspells/bridge/ManaTrace.java` | R | Text opened in full, lines 1-133 |
| `src/main/java/com/otectus/arsnspells/bridge/ModeChangeCleanup.java` | R | Text opened in full, lines 1-142 |
| `src/main/java/com/otectus/arsnspells/bridge/NativeManaAccess.java` | R | Text opened in full, lines 1-51 |
| `src/main/java/com/otectus/arsnspells/bridge/NativeManaSync.java` | R | Text opened in full, lines 1-21 |
| `src/main/java/com/otectus/arsnspells/bridge/SharedPoolCeiling.java` | R | Text opened in full, lines 1-143 |
| `src/main/java/com/otectus/arsnspells/casting/AlternativePayment.java` | R | Text opened in full, lines 1-311 |
| `src/main/java/com/otectus/arsnspells/casting/ArsCastPayments.java` | R | Text opened in full, lines 1-221 |
| `src/main/java/com/otectus/arsnspells/casting/CastLedger.java` | R | Text opened in full, lines 1-331 |
| `src/main/java/com/otectus/arsnspells/casting/CastingAuthority.java` | R | Text opened in full, lines 1-40 |
| `src/main/java/com/otectus/arsnspells/casting/IronsCastLifecycle.java` | R | Text opened in full, lines 1-450 |
| `src/main/java/com/otectus/arsnspells/casting/IronsCastPayments.java` | R | Text opened in full, lines 1-303 |
| `src/main/java/com/otectus/arsnspells/casting/NativePayment.java` | R | Text opened in full, lines 1-100 |
| `src/main/java/com/otectus/arsnspells/casting/PaymentMessages.java` | R | Text opened in full, lines 1-15 |
| `src/main/java/com/otectus/arsnspells/casting/PaymentRecovery.java` | R | Text opened in full, lines 1-86 |
| `src/main/java/com/otectus/arsnspells/casting/PaymentTimeline.java` | R | Text opened in full, lines 1-133 |
| `src/main/java/com/otectus/arsnspells/casting/QuoteService.java` | R | Text opened in full, lines 1-124 |
| `src/main/java/com/otectus/arsnspells/casting/RingPaymentLegs.java` | R | Text opened in full, lines 1-114 |
| `src/main/java/com/otectus/arsnspells/casting/TransactionSnapshot.java` | R | Text opened in full, lines 1-33 |
| `src/main/java/com/otectus/arsnspells/client/ArsNSpellsClient.java` | R | Text opened in full, lines 1-149 |
| `src/main/java/com/otectus/arsnspells/client/CastingClientSmoke.java` | R | Text opened in full, lines 1-66 |
| `src/main/java/com/otectus/arsnspells/client/ClientAffinityPacketHandler.java` | R | Text opened in full, lines 1-40 |
| `src/main/java/com/otectus/arsnspells/client/ClientAuraPeakTracker.java` | R | Text opened in full, lines 1-107 |
| `src/main/java/com/otectus/arsnspells/client/ClientSchoolSnapshotLifecycle.java` | R | Text opened in full, lines 1-25 |
| `src/main/java/com/otectus/arsnspells/client/ClientSettings.java` | R | Text opened in full, lines 1-46 |
| `src/main/java/com/otectus/arsnspells/client/CovenantCompatNotice.java` | R | Text opened in full, lines 1-39 |
| `src/main/java/com/otectus/arsnspells/client/CrossCastClientHints.java` | R | Text opened in full, lines 1-20 |
| `src/main/java/com/otectus/arsnspells/client/GlyphClientSync.java` | R | Text opened in full, lines 1-91 |
| `src/main/java/com/otectus/arsnspells/client/ImportedGlyphClientTooltip.java` | R | Text opened in full, lines 1-24 |
| `src/main/java/com/otectus/arsnspells/client/ManaBarController.java` | R | Text opened in full, lines 1-142 |
| `src/main/java/com/otectus/arsnspells/client/ManaBarVisibility.java` | R | Text opened in full, lines 1-16 |
| `src/main/java/com/otectus/arsnspells/client/OverlayDiagnostics.java` | R | Text opened in full, lines 1-140 |
| `src/main/java/com/otectus/arsnspells/client/WorldUiSmoke.java` | R | Text opened in full, lines 1-410 |
| `src/main/java/com/otectus/arsnspells/client/glyph/ImportedGlyphFallbackContent.java` | R | Text opened in full, lines 1-104 |
| `src/main/java/com/otectus/arsnspells/client/glyph/ImportedGlyphFallbackPack.java` | R | Text opened in full, lines 1-187 |
| `src/main/java/com/otectus/arsnspells/client/icons/CarrierRenderContext.java` | R | Text opened in full, lines 1-17 |
| `src/main/java/com/otectus/arsnspells/client/icons/IconClientSmoke.java` | R | Text opened in full, lines 1-111 |
| `src/main/java/com/otectus/arsnspells/client/icons/IconReviewScreen.java` | R | Text opened in full, lines 1-28 |
| `src/main/java/com/otectus/arsnspells/client/icons/SpellIconRegistry.java` | R | Text opened in full, lines 1-129 |
| `src/main/java/com/otectus/arsnspells/client/jei/GlyphPressCategory.java` | R | Text opened in full, lines 1-87 |
| `src/main/java/com/otectus/arsnspells/client/model/ImportedGlyphGeometry.java` | R | Text opened in full, lines 1-102 |
| `src/main/java/com/otectus/arsnspells/client/screen/ArrowProgress.java` | R | Text opened in full, lines 1-22 |
| `src/main/java/com/otectus/arsnspells/client/screen/CompatibilityScreen.java` | R | Text opened in full, lines 1-66 |
| `src/main/java/com/otectus/arsnspells/client/screen/ConfigScreenFactory.java` | R | Text opened in full, lines 1-309 |
| `src/main/java/com/otectus/arsnspells/client/screen/GlyphPressScreen.java` | R | Text opened in full, lines 1-465 |
| `src/main/java/com/otectus/arsnspells/client/screen/LoomCosmeticPresets.java` | R | Text opened in full, lines 1-61 |
| `src/main/java/com/otectus/arsnspells/client/screen/SchoolJournalScreen.java` | R | Text opened in full, lines 1-82 |
| `src/main/java/com/otectus/arsnspells/client/screen/ScrollableTextPanel.java` | R | Text opened in full, lines 1-107 |
| `src/main/java/com/otectus/arsnspells/client/screen/SpellIconPickerScreen.java` | R | Text opened in full, lines 1-149 |
| `src/main/java/com/otectus/arsnspells/client/screen/SpellLoomDetailsScreen.java` | R | Text opened in full, lines 1-72 |
| `src/main/java/com/otectus/arsnspells/client/screen/SpellLoomScreen.java` | R | Text opened in full, lines 1-584 |
| `src/main/java/com/otectus/arsnspells/client/screen/SpellSchoolPickerScreen.java` | R | Text opened in full, lines 1-134 |
| `src/main/java/com/otectus/arsnspells/client/screen/VanillaGui.java` | R | Text opened in full, lines 1-69 |
| `src/main/java/com/otectus/arsnspells/client/screen/VanillaPanelScreen.java` | R | Text opened in full, lines 1-16 |
| `src/main/java/com/otectus/arsnspells/combat/ArsDamageBridge.java` | R | Text opened in full, lines 1-87 |
| `src/main/java/com/otectus/arsnspells/combat/CombatDebugState.java` | R | Text opened in full, lines 1-180 |
| `src/main/java/com/otectus/arsnspells/combat/IronsAttributeReport.java` | R | Text opened in full, lines 1-76 |
| `src/main/java/com/otectus/arsnspells/combat/IronsDamageBridge.java` | R | Text opened in full, lines 1-98 |
| `src/main/java/com/otectus/arsnspells/commands/ArsNSpellsCommands.java` | R | Text opened in full, lines 1-771 |
| `src/main/java/com/otectus/arsnspells/commands/AuditDiagnosticsCommands.java` | R | Text opened in full, lines 1-152 |
| `src/main/java/com/otectus/arsnspells/commands/GlyphDiagnosticsCommands.java` | R | Text opened in full, lines 1-242 |
| `src/main/java/com/otectus/arsnspells/commands/GlyphReportCommands.java` | R | Text opened in full, lines 1-221 |
| `src/main/java/com/otectus/arsnspells/commands/HeldSpellInspection.java` | R | Text opened in full, lines 1-57 |
| `src/main/java/com/otectus/arsnspells/commands/IronsHeldSpellInspection.java` | R | Text opened in full, lines 1-19 |
| `src/main/java/com/otectus/arsnspells/compat/AlternativeResourceAccess.java` | R | Text opened in full, lines 1-250 |
| `src/main/java/com/otectus/arsnspells/compat/BlasphemySchools.java` | R | Text opened in full, lines 1-18 |
| `src/main/java/com/otectus/arsnspells/compat/CompatIds.java` | R | Text opened in full, lines 1-33 |
| `src/main/java/com/otectus/arsnspells/compat/IronsCompat.java` | R | Text opened in full, lines 1-32 |
| `src/main/java/com/otectus/arsnspells/compat/IronsSchoolAttributes.java` | R | Text opened in full, lines 1-68 |
| `src/main/java/com/otectus/arsnspells/compat/LpSourcePolicy.java` | R | Text opened in full, lines 1-21 |
| `src/main/java/com/otectus/arsnspells/compat/ModPresence.java` | R | Text opened in full, lines 1-36 |
| `src/main/java/com/otectus/arsnspells/compat/SanctifiedLegacyCompat.java` | R | Text opened in full, lines 1-1301 |
| `src/main/java/com/otectus/arsnspells/compat/ScrollLPTracker.java` | R | Text opened in full, lines 1-105 |
| `src/main/java/com/otectus/arsnspells/compat/curios/CuriosAccess.java` | R | Text opened in full, lines 1-100 |
| `src/main/java/com/otectus/arsnspells/compat/curios/IronsCurioDiscountHandler.java` | R | Text opened in full, lines 1-68 |
| `src/main/java/com/otectus/arsnspells/compat/irons/glyph/IronGlyphBridge.java` | R | Text opened in full, lines 1-136 |
| `src/main/java/com/otectus/arsnspells/compat/irons/glyph/IronGlyphBridges.java` | R | Text opened in full, lines 1-61 |
| `src/main/java/com/otectus/arsnspells/compat/irons/glyph/IronTargetingScope.java` | R | Text opened in full, lines 1-193 |
| `src/main/java/com/otectus/arsnspells/compat/irons/glyph/IronsGlyphChildHooks.java` | R | Text opened in full, lines 1-60 |
| `src/main/java/com/otectus/arsnspells/compat/irons/glyph/IronsGlyphInvoker.java` | R | Text opened in full, lines 1-342 |
| `src/main/java/com/otectus/arsnspells/compat/irons/glyph/IronsGlyphRuntime.java` | R | Text opened in full, lines 1-210 |
| `src/main/java/com/otectus/arsnspells/compat/irons/glyph/IronsSpellInventory.java` | R | Text opened in full, lines 1-81 |
| `src/main/java/com/otectus/arsnspells/compat/irons_spells/SchoolIndex.java` | R | Text opened in full, lines 1-53 |
| `src/main/java/com/otectus/arsnspells/compat/jei/ArsNSpellsJeiPlugin.java` | R | Text opened in full, lines 1-124 |
| `src/main/java/com/otectus/arsnspells/compat/jei/GlyphPressJeiRecipe.java` | R | Text opened in full, lines 1-100 |
| `src/main/java/com/otectus/arsnspells/compat/jei/IronsProxyRecipeHider.java` | R | Text opened in full, lines 1-90 |
| `src/main/java/com/otectus/arsnspells/config/AnsConfig.java` | R | Text opened in full, lines 1-1488 |
| `src/main/java/com/otectus/arsnspells/config/AnsSettings.java` | R | Text opened in full, lines 1-149 |
| `src/main/java/com/otectus/arsnspells/config/AnsSettingsService.java` | R | Text opened in full, lines 1-144 |
| `src/main/java/com/otectus/arsnspells/config/ConfigAuthority.java` | R | Text opened in full, lines 1-58 |
| `src/main/java/com/otectus/arsnspells/config/InscribedCooldownPresets.java` | R | Text opened in full, lines 1-27 |
| `src/main/java/com/otectus/arsnspells/config/ManaUnificationMode.java` | R | Text opened in full, lines 1-142 |
| `src/main/java/com/otectus/arsnspells/config/MultiSchoolPowerPolicy.java` | R | Text opened in full, lines 1-70 |
| `src/main/java/com/otectus/arsnspells/contract/AnsModifierIds.java` | R | Text opened in full, lines 1-94 |
| `src/main/java/com/otectus/arsnspells/contract/AttemptLedger.java` | R | Text opened in full, lines 1-265 |
| `src/main/java/com/otectus/arsnspells/contract/AttemptState.java` | R | Text opened in full, lines 1-68 |
| `src/main/java/com/otectus/arsnspells/contract/BillingRoute.java` | R | Text opened in full, lines 1-41 |
| `src/main/java/com/otectus/arsnspells/contract/CarrierPolicy.java` | R | Text opened in full, lines 1-19 |
| `src/main/java/com/otectus/arsnspells/contract/CastAttempt.java` | R | Text opened in full, lines 1-208 |
| `src/main/java/com/otectus/arsnspells/contract/CompatibilityStatus.java` | R | Text opened in full, lines 1-59 |
| `src/main/java/com/otectus/arsnspells/contract/ConversionKind.java` | R | Text opened in full, lines 1-26 |
| `src/main/java/com/otectus/arsnspells/contract/CostQuote.java` | R | Text opened in full, lines 1-66 |
| `src/main/java/com/otectus/arsnspells/contract/CostRules.java` | R | Text opened in full, lines 1-161 |
| `src/main/java/com/otectus/arsnspells/contract/FeatureCleanup.java` | R | Text opened in full, lines 1-25 |
| `src/main/java/com/otectus/arsnspells/contract/InscriptionPlan.java` | R | Text opened in full, lines 1-59 |
| `src/main/java/com/otectus/arsnspells/contract/InscriptionPlanner.java` | R | Text opened in full, lines 1-53 |
| `src/main/java/com/otectus/arsnspells/contract/InscriptionSourceKind.java` | R | Text opened in full, lines 1-28 |
| `src/main/java/com/otectus/arsnspells/contract/InscriptionView.java` | R | Text opened in full, lines 1-29 |
| `src/main/java/com/otectus/arsnspells/contract/ModeRoutingSnapshot.java` | R | Text opened in full, lines 1-135 |
| `src/main/java/com/otectus/arsnspells/contract/ModifierKeyMapper.java` | R | Text opened in full, lines 1-26 |
| `src/main/java/com/otectus/arsnspells/contract/PaymentOpenFailurePolicy.java` | R | Text opened in full, lines 1-22 |
| `src/main/java/com/otectus/arsnspells/contract/QuoteModifier.java` | R | Text opened in full, lines 1-45 |
| `src/main/java/com/otectus/arsnspells/contract/QuotePolicy.java` | R | Text opened in full, lines 1-22 |
| `src/main/java/com/otectus/arsnspells/contract/RequestAdmission.java` | R | Text opened in full, lines 1-40 |
| `src/main/java/com/otectus/arsnspells/contract/ResourceAccess.java` | R | Text opened in full, lines 1-64 |
| `src/main/java/com/otectus/arsnspells/contract/ResourceAmount.java` | R | Text opened in full, lines 1-43 |
| `src/main/java/com/otectus/arsnspells/contract/ResourceMovement.java` | R | Text opened in full, lines 1-29 |
| `src/main/java/com/otectus/arsnspells/contract/ResourceUnit.java` | R | Text opened in full, lines 1-21 |
| `src/main/java/com/otectus/arsnspells/contract/RoundingRule.java` | R | Text opened in full, lines 1-37 |
| `src/main/java/com/otectus/arsnspells/contract/StandardQuotePolicy.java` | R | Text opened in full, lines 1-63 |
| `src/main/java/com/otectus/arsnspells/contract/package-info.java` | R | Text opened in full, lines 1-26 |
| `src/main/java/com/otectus/arsnspells/cooldown/CooldownCategory.java` | R | Text opened in full, lines 1-12 |
| `src/main/java/com/otectus/arsnspells/cooldown/CooldownTracker.java` | R | Text opened in full, lines 1-21 |
| `src/main/java/com/otectus/arsnspells/cooldown/SpellCategorizer.java` | R | Text opened in full, lines 1-31 |
| `src/main/java/com/otectus/arsnspells/cooldown/UnifiedCooldownManager.java` | R | Text opened in full, lines 1-205 |
| `src/main/java/com/otectus/arsnspells/data/AffinityData.java` | R | Text opened in full, lines 1-87 |
| `src/main/java/com/otectus/arsnspells/data/CooldownData.java` | R | Text opened in full, lines 1-53 |
| `src/main/java/com/otectus/arsnspells/data/DecayAccumulator.java` | R | Text opened in full, lines 1-78 |
| `src/main/java/com/otectus/arsnspells/data/GlyphSchoolReloadListener.java` | R | Text opened in full, lines 1-35 |
| `src/main/java/com/otectus/arsnspells/data/ImportedGlyphReloadListener.java` | R | Text opened in full, lines 1-104 |
| `src/main/java/com/otectus/arsnspells/data/ModCapabilityProvider.java` | R | Text opened in full, lines 1-93 |
| `src/main/java/com/otectus/arsnspells/data/ProgressionData.java` | R | Text opened in full, lines 1-70 |
| `src/main/java/com/otectus/arsnspells/data/SchoolMappingLoader.java` | R | Text opened in full, lines 1-118 |
| `src/main/java/com/otectus/arsnspells/equipment/AttributeContribution.java` | R | Text opened in full, lines 1-54 |
| `src/main/java/com/otectus/arsnspells/equipment/EquipmentIntegration.java` | R | Text opened in full, lines 1-533 |
| `src/main/java/com/otectus/arsnspells/equipment/PotionContributions.java` | R | Text opened in full, lines 1-220 |
| `src/main/java/com/otectus/arsnspells/events/AffinityDecayHandler.java` | R | Text opened in full, lines 1-82 |
| `src/main/java/com/otectus/arsnspells/events/AffinityHandler.java` | R | Text opened in full, lines 1-67 |
| `src/main/java/com/otectus/arsnspells/events/ArsManaCalcHandler.java` | R | Text opened in full, lines 1-164 |
| `src/main/java/com/otectus/arsnspells/events/CapabilityResyncHandler.java` | R | Text opened in full, lines 1-107 |
| `src/main/java/com/otectus/arsnspells/events/CooldownHandler.java` | R | Text opened in full, lines 1-40 |
| `src/main/java/com/otectus/arsnspells/events/CrossSpellTooltipHandler.java` | R | Text opened in full, lines 1-245 |
| `src/main/java/com/otectus/arsnspells/events/CurioDiscountHandler.java` | R | Text opened in full, lines 1-238 |
| `src/main/java/com/otectus/arsnspells/events/CursedRingHandler.java` | R | Text opened in full, lines 1-34 |
| `src/main/java/com/otectus/arsnspells/events/EquipmentHandler.java` | R | Text opened in full, lines 1-223 |
| `src/main/java/com/otectus/arsnspells/events/ImportedGlyphSyncHandler.java` | R | Text opened in full, lines 1-28 |
| `src/main/java/com/otectus/arsnspells/events/IronsAffinityHandler.java` | R | Text opened in full, lines 1-63 |
| `src/main/java/com/otectus/arsnspells/events/IronsCooldownHandler.java` | R | Text opened in full, lines 1-94 |
| `src/main/java/com/otectus/arsnspells/events/IronsLPHandler.java` | R | Text opened in full, lines 1-4 |
| `src/main/java/com/otectus/arsnspells/events/IronsProgressionHandler.java` | R | Text opened in full, lines 1-64 |
| `src/main/java/com/otectus/arsnspells/events/LPDeathPrevention.java` | R | Text opened in full, lines 1-208 |
| `src/main/java/com/otectus/arsnspells/events/ProgressionHandler.java` | R | Text opened in full, lines 1-101 |
| `src/main/java/com/otectus/arsnspells/events/RegenSynergyHandler.java` | R | Text opened in full, lines 1-198 |
| `src/main/java/com/otectus/arsnspells/events/ResonanceEvents.java` | R | Text opened in full, lines 1-73 |
| `src/main/java/com/otectus/arsnspells/events/SchoolMappingSyncHandler.java` | R | Text opened in full, lines 1-20 |
| `src/main/java/com/otectus/arsnspells/events/StateEvictionHandler.java` | R | Text opened in full, lines 1-91 |
| `src/main/java/com/otectus/arsnspells/events/VirtueRingHandler.java` | R | Text opened in full, lines 1-29 |
| `src/main/java/com/otectus/arsnspells/gametest/AddonCompatGameTests.java` | S | All bytes read and UTF-8 decoded (688 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/AffinitySnapshotGameTests.java` | S | All bytes read and UTF-8 decoded (27 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/ArsIronsExportGameTests.java` | S | All bytes read and UTF-8 decoded (377 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/CastLifecycleGameTests.java` | S | All bytes read and UTF-8 decoded (270 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/CastLifetimeGameTests.java` | S | All bytes read and UTF-8 decoded (319 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/CastLifetimeIronsSupport.java` | S | All bytes read and UTF-8 decoded (62 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/CastPolicyGameTests.java` | S | All bytes read and UTF-8 decoded (391 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/CastProbe.java` | S | All bytes read and UTF-8 decoded (69 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/CastingSmokeServer.java` | S | All bytes read and UTF-8 decoded (70 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/CombatBridgeGameTests.java` | S | All bytes read and UTF-8 decoded (641 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/CreativeTabGameTests.java` | S | All bytes read and UTF-8 decoded (230 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/CrossCastGameTests.java` | P | Opened lines 315-362; remaining ranges not semantically reviewed |
| `src/main/java/com/otectus/arsnspells/gametest/CrossSpellTooltipGameTests.java` | S | All bytes read and UTF-8 decoded (241 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/EquipmentParityGameTests.java` | S | All bytes read and UTF-8 decoded (70 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/GameTestEnvironment.java` | S | All bytes read and UTF-8 decoded (33 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/Glyph341GameTests.java` | S | All bytes read and UTF-8 decoded (656 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/Glyph341IronsSupport.java` | S | All bytes read and UTF-8 decoded (190 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/GlyphCatalogInventoryGameTests.java` | S | All bytes read and UTF-8 decoded (34 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/GlyphPressGameTests.java` | S | All bytes read and UTF-8 decoded (635 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/GlyphRouteGameTests.java` | R | Text opened in full, lines 1-267 |
| `src/main/java/com/otectus/arsnspells/gametest/IdentityRecoveryGameTests.java` | S | All bytes read and UTF-8 decoded (271 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/ImportedGlyphGameTests.java` | P | Opened lines 580-657; remaining ranges not semantically reviewed |
| `src/main/java/com/otectus/arsnspells/gametest/ImportedGlyphPaymentGameTests.java` | S | All bytes read and UTF-8 decoded (161 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/InscribedCooldownGameTests.java` | S | All bytes read and UTF-8 decoded (232 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/IronsBindEquivalenceGameTests.java` | S | All bytes read and UTF-8 decoded (148 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/IronsGlyphTestSupport.java` | S | All bytes read and UTF-8 decoded (189 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/IronsInscriptionTableGameTests.java` | S | All bytes read and UTF-8 decoded (524 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/IronsLoomFixtures.java` | S | All bytes read and UTF-8 decoded (110 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/IronsProxyCastDriver.java` | R | Text opened in full, lines 1-415 |
| `src/main/java/com/otectus/arsnspells/gametest/IronsSpellDamageSupport.java` | S | All bytes read and UTF-8 decoded (60 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/IronsTableDriver.java` | S | All bytes read and UTF-8 decoded (191 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/LevelEnvelopeGameTests.java` | S | All bytes read and UTF-8 decoded (205 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/ManaCapacityGameTests.java` | S | All bytes read and UTF-8 decoded (738 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/ManaStabilityGameTests.java` | S | All bytes read and UTF-8 decoded (249 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/ModeChangeCleanupGameTests.java` | S | All bytes read and UTF-8 decoded (160 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/NativeCastPaymentGameTests.java` | P | Opened lines 710-790; remaining ranges not semantically reviewed |
| `src/main/java/com/otectus/arsnspells/gametest/NetworkAdmissionGameTests.java` | S | All bytes read and UTF-8 decoded (74 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/OptionalModGate.java` | S | All bytes read and UTF-8 decoded (64 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/ParityBehaviourGameTests.java` | P | Opened lines 1-145; remaining ranges not semantically reviewed |
| `src/main/java/com/otectus/arsnspells/gametest/ProfileIdentityGameTests.java` | S | All bytes read and UTF-8 decoded (113 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/ProxyCarrierGameTests.java` | S | All bytes read and UTF-8 decoded (35 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/RitualLifecycleGameTests.java` | S | All bytes read and UTF-8 decoded (297 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/RitualTabletGameTests.java` | S | All bytes read and UTF-8 decoded (127 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/ScenarioReport.java` | S | All bytes read and UTF-8 decoded (85 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/SchoolInscriptionNativeGameTests.java` | S | All bytes read and UTF-8 decoded (22 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/SettingsGameTests.java` | S | All bytes read and UTF-8 decoded (128 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/SharedPoolManaGameTests.java` | S | All bytes read and UTF-8 decoded (419 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/SpellDamageAttributionGameTests.java` | S | All bytes read and UTF-8 decoded (183 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/SpellLoomInscriptionGameTests.java` | S | All bytes read and UTF-8 decoded (364 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/SpellSchoolGameTests.java` | S | All bytes read and UTF-8 decoded (806 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/StaffCastGameTests.java` | S | All bytes read and UTF-8 decoded (186 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/TableExtractionGameTests.java` | R | Text opened in full, lines 1-256 |
| `src/main/java/com/otectus/arsnspells/gametest/TestConfig.java` | S | All bytes read and UTF-8 decoded (80 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/TouchRegressionGameTests.java` | S | All bytes read and UTF-8 decoded (560 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/UninscribeTeardownGameTests.java` | S | All bytes read and UTF-8 decoded (90 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/gametest/WindSpellbooksGameTests.java` | S | All bytes read and UTF-8 decoded (251 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/glyph/CatalogJson.java` | R | Text opened in full, lines 1-166 |
| `src/main/java/com/otectus/arsnspells/glyph/CatalogSources.java` | R | Text opened in full, lines 1-99 |
| `src/main/java/com/otectus/arsnspells/glyph/EmissionPayment.java` | R | Text opened in full, lines 1-327 |
| `src/main/java/com/otectus/arsnspells/glyph/GlyphAdmission.java` | R | Text opened in full, lines 1-235 |
| `src/main/java/com/otectus/arsnspells/glyph/GlyphAvailability.java` | R | Text opened in full, lines 1-133 |
| `src/main/java/com/otectus/arsnspells/glyph/GlyphCastSession.java` | R | Text opened in full, lines 1-150 |
| `src/main/java/com/otectus/arsnspells/glyph/GlyphChildren.java` | R | Text opened in full, lines 1-180 |
| `src/main/java/com/otectus/arsnspells/glyph/GlyphContinuation.java` | R | Text opened in full, lines 1-155 |
| `src/main/java/com/otectus/arsnspells/glyph/GlyphExecutionPolicy.java` | R | Text opened in full, lines 1-129 |
| `src/main/java/com/otectus/arsnspells/glyph/GlyphFamily.java` | R | Text opened in full, lines 1-84 |
| `src/main/java/com/otectus/arsnspells/glyph/GlyphIdentityRecord.java` | R | Text opened in full, lines 1-295 |
| `src/main/java/com/otectus/arsnspells/glyph/GlyphInvocation.java` | R | Text opened in full, lines 1-35 |
| `src/main/java/com/otectus/arsnspells/glyph/GlyphInvocationBudget.java` | R | Text opened in full, lines 1-35 |
| `src/main/java/com/otectus/arsnspells/glyph/GlyphInvocations.java` | R | Text opened in full, lines 1-148 |
| `src/main/java/com/otectus/arsnspells/glyph/GlyphRoute.java` | R | Text opened in full, lines 1-41 |
| `src/main/java/com/otectus/arsnspells/glyph/GlyphSessions.java` | R | Text opened in full, lines 1-391 |
| `src/main/java/com/otectus/arsnspells/glyph/GlyphSpawnObserver.java` | R | Text opened in full, lines 1-22 |
| `src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphCatalog.java` | R | Text opened in full, lines 1-590 |
| `src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphCompositionValidator.java` | R | Text opened in full, lines 1-60 |
| `src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphDefinition.java` | R | Text opened in full, lines 1-136 |
| `src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphItem.java` | R | Text opened in full, lines 1-79 |
| `src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphKey.java` | R | Text opened in full, lines 1-81 |
| `src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphKnowledge.java` | R | Text opened in full, lines 1-91 |
| `src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphRegistrar.java` | R | Text opened in full, lines 1-68 |
| `src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphRuntime.java` | R | Text opened in full, lines 1-18 |
| `src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphTooltip.java` | R | Text opened in full, lines 1-41 |
| `src/main/java/com/otectus/arsnspells/glyph/ImportedIronEffect.java` | R | Text opened in full, lines 1-159 |
| `src/main/java/com/otectus/arsnspells/glyph/RegistrationOrderProbe.java` | R | Text opened in full, lines 1-57 |
| `src/main/java/com/otectus/arsnspells/glyph/RetainedCatalogs.java` | R | Text opened in full, lines 1-237 |
| `src/main/java/com/otectus/arsnspells/glyph/behavior/GlyphBehavior.java` | R | Text opened in full, lines 1-267 |
| `src/main/java/com/otectus/arsnspells/glyph/behavior/GlyphLiveSnapshot.java` | R | Text opened in full, lines 1-274 |
| `src/main/java/com/otectus/arsnspells/glyph/behavior/LiveSpellFacts.java` | R | Text opened in full, lines 1-38 |
| `src/main/java/com/otectus/arsnspells/glyph/behavior/PublicationStage.java` | R | Text opened in full, lines 1-84 |
| `src/main/java/com/otectus/arsnspells/glyph/recipe/GlyphMappingSnapshot.java` | R | Text opened in full, lines 1-496 |
| `src/main/java/com/otectus/arsnspells/glyph/recipe/GlyphPressPlan.java` | R | Text opened in full, lines 1-84 |
| `src/main/java/com/otectus/arsnspells/glyph/recipe/GlyphPressPlanner.java` | R | Text opened in full, lines 1-163 |
| `src/main/java/com/otectus/arsnspells/glyph/recipe/MappingJson.java` | R | Text opened in full, lines 1-163 |
| `src/main/java/com/otectus/arsnspells/glyph/recipe/ViewerRecipeSet.java` | R | Text opened in full, lines 1-79 |
| `src/main/java/com/otectus/arsnspells/icons/IconCatalog.java` | R | Text opened in full, lines 1-312 |
| `src/main/java/com/otectus/arsnspells/inscription/InscriptionClassifier.java` | R | Text opened in full, lines 1-168 |
| `src/main/java/com/otectus/arsnspells/inscription/InscriptionOutcome.java` | R | Text opened in full, lines 1-68 |
| `src/main/java/com/otectus/arsnspells/inscription/LoomInscription.java` | R | Text opened in full, lines 1-289 |
| `src/main/java/com/otectus/arsnspells/inscription/StackInscriptionView.java` | R | Text opened in full, lines 1-42 |
| `src/main/java/com/otectus/arsnspells/loot/BlankScrollLootModifier.java` | R | Text opened in full, lines 1-78 |
| `src/main/java/com/otectus/arsnspells/menu/GlyphPressMenu.java` | R | Text opened in full, lines 1-234 |
| `src/main/java/com/otectus/arsnspells/menu/SpellLoomMenu.java` | R | Text opened in full, lines 1-167 |
| `src/main/java/com/otectus/arsnspells/mixin/ArsNSpellsMixinPlugin.java` | R | Text opened in full, lines 1-152 |
| `src/main/java/com/otectus/arsnspells/mixin/ars/MixinArsPotionEffects.java` | R | Text opened in full, lines 1-17 |
| `src/main/java/com/otectus/arsnspells/mixin/ars/MixinAugmentCostForImportedGlyph.java` | R | Text opened in full, lines 1-23 |
| `src/main/java/com/otectus/arsnspells/mixin/ars/MixinManaCapAttacher.java` | R | Text opened in full, lines 1-26 |
| `src/main/java/com/otectus/arsnspells/mixin/ars/MixinManaCapability.java` | R | Text opened in full, lines 1-304 |
| `src/main/java/com/otectus/arsnspells/mixin/ars/MixinManaRegenTick.java` | R | Text opened in full, lines 1-57 |
| `src/main/java/com/otectus/arsnspells/mixin/ars/MixinSpellResolverMana.java` | R | Text opened in full, lines 1-27 |
| `src/main/java/com/otectus/arsnspells/mixin/ars/MixinSpellResolverPreCast.java` | R | Text opened in full, lines 1-67 |
| `src/main/java/com/otectus/arsnspells/mixin/ars/MixinStandardSpellValidator.java` | R | Text opened in full, lines 1-35 |
| `src/main/java/com/otectus/arsnspells/mixin/covenant/MixinResourceBarOverlay.java` | R | Text opened in full, lines 1-91 |
| `src/main/java/com/otectus/arsnspells/mixin/gametest/MixinCastProbe.java` | S | All bytes read and UTF-8 decoded (32 lines); semantic read not completed |
| `src/main/java/com/otectus/arsnspells/mixin/irons/MagicDataAccessor.java` | R | Text opened in full, lines 1-19 |
| `src/main/java/com/otectus/arsnspells/mixin/irons/MixinAbstractSpellArsIcon.java` | R | Text opened in full, lines 1-80 |
| `src/main/java/com/otectus/arsnspells/mixin/irons/MixinInscriptionResultSlot.java` | R | Text opened in full, lines 1-69 |
| `src/main/java/com/otectus/arsnspells/mixin/irons/MixinInscriptionTableMenu.java` | R | Text opened in full, lines 1-170 |
| `src/main/java/com/otectus/arsnspells/mixin/irons/MixinInscriptionTableScreen.java` | R | Text opened in full, lines 1-95 |
| `src/main/java/com/otectus/arsnspells/mixin/irons/MixinIronsAimHelpers.java` | R | Text opened in full, lines 1-51 |
| `src/main/java/com/otectus/arsnspells/mixin/irons/MixinIronsCastPayment.java` | R | Text opened in full, lines 1-73 |
| `src/main/java/com/otectus/arsnspells/mixin/irons/MixinIronsCastTicker.java` | R | Text opened in full, lines 1-45 |
| `src/main/java/com/otectus/arsnspells/mixin/irons/MixinIronsCastValidation.java` | R | Text opened in full, lines 1-23 |
| `src/main/java/com/otectus/arsnspells/mixin/irons/MixinIronsMagicDataMana.java` | R | Text opened in full, lines 1-147 |
| `src/main/java/com/otectus/arsnspells/mixin/irons/MixinIronsManaBarVisibility.java` | R | Text opened in full, lines 1-38 |
| `src/main/java/com/otectus/arsnspells/mixin/irons/MixinIronsManaRegen.java` | R | Text opened in full, lines 1-26 |
| `src/main/java/com/otectus/arsnspells/mixin/irons/MixinIronsRaycastBuilder.java` | R | Text opened in full, lines 1-32 |
| `src/main/java/com/otectus/arsnspells/mixin/irons/MixinIronsSpellDamage.java` | R | Text opened in full, lines 1-47 |
| `src/main/java/com/otectus/arsnspells/mixin/irons/MixinItemStackIconContext.java` | R | Text opened in full, lines 1-23 |
| `src/main/java/com/otectus/arsnspells/mixin/irons/MixinScrollItem.java` | R | Text opened in full, lines 1-96 |
| `src/main/java/com/otectus/arsnspells/mixin/sanctified/MixinCovenantSpellEvents.java` | R | Text opened in full, lines 1-68 |
| `src/main/java/com/otectus/arsnspells/mixin/sanctified/MixinSanctifiedAbstractSpell.java` | R | Text opened in full, lines 1-99 |
| `src/main/java/com/otectus/arsnspells/network/AffinitySyncPacket.java` | R | Text opened in full, lines 1-58 |
| `src/main/java/com/otectus/arsnspells/network/CarrierFingerprint.java` | R | Text opened in full, lines 1-60 |
| `src/main/java/com/otectus/arsnspells/network/CooldownSyncPacket.java` | R | Text opened in full, lines 1-56 |
| `src/main/java/com/otectus/arsnspells/network/CrossCastRequestPacket.java` | R | Text opened in full, lines 1-142 |
| `src/main/java/com/otectus/arsnspells/network/GlyphCatalogHandshakePacket.java` | R | Text opened in full, lines 1-39 |
| `src/main/java/com/otectus/arsnspells/network/GlyphLiveSyncPacket.java` | R | Text opened in full, lines 1-72 |
| `src/main/java/com/otectus/arsnspells/network/GlyphMappingSyncPacket.java` | R | Text opened in full, lines 1-185 |
| `src/main/java/com/otectus/arsnspells/network/GlyphPressActionPacket.java` | R | Text opened in full, lines 1-32 |
| `src/main/java/com/otectus/arsnspells/network/GlyphPressRequestHandler.java` | R | Text opened in full, lines 1-34 |
| `src/main/java/com/otectus/arsnspells/network/GlyphPressResultPacket.java` | R | Text opened in full, lines 1-28 |
| `src/main/java/com/otectus/arsnspells/network/GlyphPressRevision.java` | R | Text opened in full, lines 1-39 |
| `src/main/java/com/otectus/arsnspells/network/GlyphPublication.java` | R | Text opened in full, lines 1-70 |
| `src/main/java/com/otectus/arsnspells/network/JournalSnapshotPacket.java` | R | Text opened in full, lines 1-20 |
| `src/main/java/com/otectus/arsnspells/network/LoomInventoryRevision.java` | R | Text opened in full, lines 1-48 |
| `src/main/java/com/otectus/arsnspells/network/LoomRequestContext.java` | R | Text opened in full, lines 1-24 |
| `src/main/java/com/otectus/arsnspells/network/LoomRequestHandler.java` | R | Text opened in full, lines 1-55 |
| `src/main/java/com/otectus/arsnspells/network/NetworkRequestGuard.java` | R | Text opened in full, lines 1-36 |
| `src/main/java/com/otectus/arsnspells/network/PacketHandler.java` | R | Text opened in full, lines 1-117 |
| `src/main/java/com/otectus/arsnspells/network/ResonanceSyncPacket.java` | R | Text opened in full, lines 1-46 |
| `src/main/java/com/otectus/arsnspells/network/SchoolJournalSnapshot.java` | R | Text opened in full, lines 1-40 |
| `src/main/java/com/otectus/arsnspells/network/SchoolMappingsSyncPacket.java` | R | Text opened in full, lines 1-63 |
| `src/main/java/com/otectus/arsnspells/network/SettingsApplyPacket.java` | R | Text opened in full, lines 1-40 |
| `src/main/java/com/otectus/arsnspells/network/SettingsSyncPacket.java` | R | Text opened in full, lines 1-33 |
| `src/main/java/com/otectus/arsnspells/network/SpellLoomExportPacket.java` | R | Text opened in full, lines 1-43 |
| `src/main/java/com/otectus/arsnspells/network/SpellLoomResultPacket.java` | R | Text opened in full, lines 1-21 |
| `src/main/java/com/otectus/arsnspells/progression/ProgressionAttributes.java` | R | Text opened in full, lines 1-57 |
| `src/main/java/com/otectus/arsnspells/registry/ModBlockEntities.java` | R | Text opened in full, lines 1-30 |
| `src/main/java/com/otectus/arsnspells/registry/ModBlocksRegistry.java` | R | Text opened in full, lines 1-59 |
| `src/main/java/com/otectus/arsnspells/registry/ModCreativeTabs.java` | R | Text opened in full, lines 1-156 |
| `src/main/java/com/otectus/arsnspells/registry/ModItemsRegistry.java` | R | Text opened in full, lines 1-133 |
| `src/main/java/com/otectus/arsnspells/registry/ModLootModifiersRegistry.java` | R | Text opened in full, lines 1-28 |
| `src/main/java/com/otectus/arsnspells/registry/ModMenus.java` | R | Text opened in full, lines 1-29 |
| `src/main/java/com/otectus/arsnspells/registry/ModSounds.java` | R | Text opened in full, lines 1-25 |
| `src/main/java/com/otectus/arsnspells/registry/ModTags.java` | R | Text opened in full, lines 1-66 |
| `src/main/java/com/otectus/arsnspells/rituals/AnsRitual.java` | R | Text opened in full, lines 1-117 |
| `src/main/java/com/otectus/arsnspells/rituals/InscriptionInputs.java` | R | Text opened in full, lines 1-176 |
| `src/main/java/com/otectus/arsnspells/rituals/InscriptionSource.java` | R | Text opened in full, lines 1-36 |
| `src/main/java/com/otectus/arsnspells/rituals/IronsInscriptionReader.java` | R | Text opened in full, lines 1-76 |
| `src/main/java/com/otectus/arsnspells/rituals/ManaInfusionRitual.java` | R | Text opened in full, lines 1-42 |
| `src/main/java/com/otectus/arsnspells/rituals/ManaWellRitual.java` | R | Text opened in full, lines 1-41 |
| `src/main/java/com/otectus/arsnspells/rituals/RitualFeedback.java` | R | Text opened in full, lines 1-85 |
| `src/main/java/com/otectus/arsnspells/rituals/RitualRegistryHandler.java` | R | Text opened in full, lines 1-76 |
| `src/main/java/com/otectus/arsnspells/rituals/SpellTranscriptionRitual.java` | R | Text opened in full, lines 1-237 |
| `src/main/java/com/otectus/arsnspells/rituals/SpellUninscriptionRitual.java` | R | Text opened in full, lines 1-137 |
| `src/main/java/com/otectus/arsnspells/rituals/SpellbookBindingInputs.java` | R | Text opened in full, lines 1-62 |
| `src/main/java/com/otectus/arsnspells/rituals/SpellbookBindingRitual.java` | R | Text opened in full, lines 1-210 |
| `src/main/java/com/otectus/arsnspells/spell/ArsCastDispatcher.java` | R | Text opened in full, lines 1-186 |
| `src/main/java/com/otectus/arsnspells/spell/ArsSerializedSpellDescriptor.java` | R | Text opened in full, lines 1-66 |
| `src/main/java/com/otectus/arsnspells/spell/ArsSpellExportUtil.java` | R | Text opened in full, lines 1-194 |
| `src/main/java/com/otectus/arsnspells/spell/CastValidationScope.java` | R | Text opened in full, lines 1-226 |
| `src/main/java/com/otectus/arsnspells/spell/CrossCastContext.java` | R | Text opened in full, lines 1-201 |
| `src/main/java/com/otectus/arsnspells/spell/CrossCastIronsHandler.java` | R | Text opened in full, lines 1-4 |
| `src/main/java/com/otectus/arsnspells/spell/CrossCastNbt.java` | R | Text opened in full, lines 1-418 |
| `src/main/java/com/otectus/arsnspells/spell/CrossCastValidator.java` | R | Text opened in full, lines 1-146 |
| `src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java` | R | Text opened in full, lines 1-580 |
| `src/main/java/com/otectus/arsnspells/spell/CrossSpellType.java` | R | Text opened in full, lines 1-6 |
| `src/main/java/com/otectus/arsnspells/spell/IronsBookBindingUtil.java` | R | Text opened in full, lines 1-408 |
| `src/main/java/com/otectus/arsnspells/spell/IronsRegistrySpellDescriptor.java` | R | Text opened in full, lines 1-82 |
| `src/main/java/com/otectus/arsnspells/spell/IronsSpellbookBinder.java` | R | Text opened in full, lines 1-178 |
| `src/main/java/com/otectus/arsnspells/spell/ScrollKind.java` | R | Text opened in full, lines 1-94 |
| `src/main/java/com/otectus/arsnspells/spell/SpellDescriptor.java` | R | Text opened in full, lines 1-84 |
| `src/main/java/com/otectus/arsnspells/spell/SpellSchoolChoice.java` | R | Text opened in full, lines 1-202 |
| `src/main/java/com/otectus/arsnspells/spell/SpellSchoolNames.java` | R | Text opened in full, lines 1-73 |
| `src/main/java/com/otectus/arsnspells/spell/irons/ArsCrossProxyHiding.java` | R | Text opened in full, lines 1-118 |
| `src/main/java/com/otectus/arsnspells/spell/irons/ArsCrossProxyRegistry.java` | R | Text opened in full, lines 1-117 |
| `src/main/java/com/otectus/arsnspells/spell/irons/ArsCrossProxySpell.java` | R | Text opened in full, lines 1-271 |
| `src/main/java/com/otectus/arsnspells/spell/irons/CarrierReconciler.java` | R | Text opened in full, lines 1-138 |
| `src/main/java/com/otectus/arsnspells/spell/irons/IronsInscriptionPolicy.java` | R | Text opened in full, lines 1-98 |
| `src/main/java/com/otectus/arsnspells/spell/irons/IronsProxySlotWriter.java` | R | Text opened in full, lines 1-111 |
| `src/main/java/com/otectus/arsnspells/spell/irons/IronsScrollFactory.java` | R | Text opened in full, lines 1-144 |
| `src/main/java/com/otectus/arsnspells/spell/irons/IronsTableBindHandler.java` | R | Text opened in full, lines 1-149 |
| `src/main/java/com/otectus/arsnspells/spell/irons/IronsTableExtraction.java` | R | Text opened in full, lines 1-173 |
| `src/main/java/com/otectus/arsnspells/spell/irons/ProxyCarrierResolver.java` | R | Text opened in full, lines 1-79 |
| `src/main/java/com/otectus/arsnspells/spell/target/CastTargetKind.java` | R | Text opened in full, lines 1-15 |
| `src/main/java/com/otectus/arsnspells/spell/target/InteractionOrigin.java` | R | Text opened in full, lines 1-25 |
| `src/main/java/com/otectus/arsnspells/spell/target/TargetHint.java` | R | Text opened in full, lines 1-78 |
| `src/main/java/com/otectus/arsnspells/spell/target/ValidatedCastTarget.java` | R | Text opened in full, lines 1-75 |
| `src/main/java/com/otectus/arsnspells/util/AdvancementUtil.java` | R | Text opened in full, lines 1-41 |
| `src/main/java/com/otectus/arsnspells/util/ArsSpellIntegrity.java` | R | Text opened in full, lines 1-162 |
| `src/main/java/com/otectus/arsnspells/util/ChunkScanUtil.java` | R | Text opened in full, lines 1-49 |
| `src/main/java/com/otectus/arsnspells/util/CrossCastTrace.java` | R | Text opened in full, lines 1-91 |
| `src/main/java/com/otectus/arsnspells/util/LogPrivacy.java` | R | Text opened in full, lines 1-30 |
| `src/main/java/com/otectus/arsnspells/util/LogThrottle.java` | R | Text opened in full, lines 1-61 |
| `src/main/java/com/otectus/arsnspells/util/ManaUtil.java` | R | Text opened in full, lines 1-16 |
| `src/main/java/com/otectus/arsnspells/util/PayloadBudget.java` | R | Text opened in full, lines 1-69 |
| `src/main/java/com/otectus/arsnspells/util/SchoolKeys.java` | R | Text opened in full, lines 1-32 |
| `src/main/java/com/otectus/arsnspells/util/SchoolMappings.java` | R | Text opened in full, lines 1-230 |
| `src/main/java/com/otectus/arsnspells/util/SchoolResolver.java` | R | Text opened in full, lines 1-368 |
| `src/main/java/com/otectus/arsnspells/util/SourceSynergyPolicy.java` | R | Text opened in full, lines 1-23 |
| `src/main/java/com/otectus/arsnspells/util/SpellAnalysis.java` | R | Text opened in full, lines 1-303 |
| `src/main/java/com/otectus/arsnspells/util/SpellScalingUtil.java` | R | Text opened in full, lines 1-244 |
| `src/main/java/com/otectus/arsnspells/util/SpellSchoolId.java` | R | Text opened in full, lines 1-67 |
| `src/main/java/com/otectus/arsnspells/util/StartupValidator.java` | R | Text opened in full, lines 1-177 |
| `src/main/resources/META-INF/mods.toml` | R | Text opened in full, lines 1-78 |
| `src/main/resources/ars_n_spells.compat.mixins.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/ars_n_spells.mixins.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/ars_n_spells/glyph_identity_ledger.txt` | S | All bytes read and UTF-8 decoded (1113 lines); semantic read not completed |
| `src/main/resources/ars_n_spells/iron_glyph_catalog.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/blockstates/glyph_press.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/blockstates/spell_loom.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/icon_manifest.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/lang/en_us.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/block/glyph_press.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/block/glyph_press_working.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/block/spell_loom.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/blank_glyph_plate.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/blank_scroll.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/glyph_press.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/abyssal_shroud/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/abyssal_shroud/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/abyssal_shroud/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/abyssal_shroud/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/abyssal_shroud/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/abyssal_shroud/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/abyssal_shroud/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/abyssal_shroud/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/abyssal_shroud/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/abyssal_shroud/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/acid_orb/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/acid_orb/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/acid_orb/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/acid_orb/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/acid_orb/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/acid_orb/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/acid_orb/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/acid_orb/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/acid_orb/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/acid_orb/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/acupuncture/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/acupuncture/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/acupuncture/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/acupuncture/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/acupuncture/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/acupuncture/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/acupuncture/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/acupuncture/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/acupuncture/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/acupuncture/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/angel_wing/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/angel_wing/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/angel_wing/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/angel_wing/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/angel_wing/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/angel_wing/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/angel_wing/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/angel_wing/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/angel_wing/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/angel_wing/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/arcane_shackle/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/arcane_shackle/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/arcane_shackle/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/arcane_shackle/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/arcane_shackle/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/arcane_shackle/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/arcane_shackle/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/arcane_shackle/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/arcane_shackle/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/arcane_shackle/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/arrow_volley/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/arrow_volley/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/arrow_volley/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/arrow_volley/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/arrow_volley/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/arrow_volley/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/arrow_volley/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/arrow_volley/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/arrow_volley/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/arrow_volley/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ascension/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ascension/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ascension/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ascension/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ascension/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ascension/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ascension/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ascension/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ascension/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ascension/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ball_lightning/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ball_lightning/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ball_lightning/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ball_lightning/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ball_lightning/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ball_lightning/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ball_lightning/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ball_lightning/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ball_lightning/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ball_lightning/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/black_hole/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/black_hole/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/black_hole/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/black_hole/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/black_hole/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/black_hole/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/black_hole/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/black_hole/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/black_hole/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/black_hole/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blaze_storm/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blaze_storm/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blaze_storm/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blaze_storm/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blaze_storm/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blaze_storm/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blaze_storm/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blaze_storm/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blaze_storm/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blaze_storm/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blessing_of_life/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blessing_of_life/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blessing_of_life/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blessing_of_life/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blessing_of_life/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blessing_of_life/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blessing_of_life/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blessing_of_life/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blessing_of_life/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blessing_of_life/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blight/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blight/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blight/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blight/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blight/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blight/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blight/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blight/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blight/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blight/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blizzard/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blizzard/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blizzard/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blizzard/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blizzard/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blizzard/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blizzard/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blizzard/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blizzard/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blizzard/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_needles/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_needles/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_needles/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_needles/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_needles/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_needles/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_needles/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_needles/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_needles/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_needles/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_slash/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_slash/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_slash/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_slash/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_slash/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_slash/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_slash/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_slash/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_slash/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_slash/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_step/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_step/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_step/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_step/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_step/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_step/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_step/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_step/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_step/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/blood_step/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/burning_dash/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/burning_dash/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/burning_dash/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/burning_dash/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/burning_dash/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/burning_dash/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/burning_dash/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/burning_dash/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/burning_dash/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/burning_dash/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/chain_creeper/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/chain_creeper/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/chain_creeper/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/chain_creeper/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/chain_creeper/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/chain_creeper/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/chain_creeper/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/chain_creeper/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/chain_creeper/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/chain_creeper/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/chain_lightning/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/chain_lightning/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/chain_lightning/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/chain_lightning/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/chain_lightning/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/chain_lightning/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/chain_lightning/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/chain_lightning/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/chain_lightning/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/chain_lightning/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/charge/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/charge/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/charge/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/charge/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/charge/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/charge/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/charge/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/charge/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/charge/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/charge/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cleanse/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cleanse/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cleanse/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cleanse/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cleanse/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cleanse/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cleanse/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cleanse/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cleanse/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cleanse/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cloud_of_regeneration/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cloud_of_regeneration/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cloud_of_regeneration/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cloud_of_regeneration/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cloud_of_regeneration/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cloud_of_regeneration/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cloud_of_regeneration/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cloud_of_regeneration/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cloud_of_regeneration/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cloud_of_regeneration/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cone_of_cold/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cone_of_cold/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cone_of_cold/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cone_of_cold/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cone_of_cold/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cone_of_cold/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cone_of_cold/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cone_of_cold/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cone_of_cold/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/cone_of_cold/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/counterspell/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/counterspell/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/counterspell/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/counterspell/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/counterspell/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/counterspell/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/counterspell/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/counterspell/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/counterspell/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/counterspell/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/devour/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/devour/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/devour/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/devour/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/devour/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/devour/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/devour/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/devour/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/devour/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/devour/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/divine_smite/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/divine_smite/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/divine_smite/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/divine_smite/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/divine_smite/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/divine_smite/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/divine_smite/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/divine_smite/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/divine_smite/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/divine_smite/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/dragon_breath/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/dragon_breath/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/dragon_breath/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/dragon_breath/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/dragon_breath/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/dragon_breath/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/dragon_breath/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/dragon_breath/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/dragon_breath/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/dragon_breath/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/earthquake/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/earthquake/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/earthquake/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/earthquake/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/earthquake/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/earthquake/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/earthquake/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/earthquake/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/earthquake/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/earthquake/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/echoing_strikes/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/echoing_strikes/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/echoing_strikes/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/echoing_strikes/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/echoing_strikes/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/echoing_strikes/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/echoing_strikes/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/echoing_strikes/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/echoing_strikes/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/echoing_strikes/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/eldritch_blast/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/eldritch_blast/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/eldritch_blast/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/eldritch_blast/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/eldritch_blast/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/eldritch_blast/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/eldritch_blast/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/eldritch_blast/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/eldritch_blast/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/eldritch_blast/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/electrocute/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/electrocute/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/electrocute/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/electrocute/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/electrocute/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/electrocute/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/electrocute/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/electrocute/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/electrocute/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/electrocute/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/evasion/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/evasion/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/evasion/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/evasion/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/evasion/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/evasion/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/evasion/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/evasion/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/evasion/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/evasion/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_strike/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_strike/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_strike/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_strike/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_strike/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_strike/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_strike/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_strike/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_strike/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_strike/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_swirl/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_swirl/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_swirl/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_swirl/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_swirl/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_swirl/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_swirl/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_swirl/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_swirl/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_swirl/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_ward/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_ward/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_ward/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_ward/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_ward/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_ward/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_ward/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_ward/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_ward/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fang_ward/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fire_arrow/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fire_arrow/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fire_arrow/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fire_arrow/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fire_arrow/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fire_arrow/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fire_arrow/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fire_arrow/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fire_arrow/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fire_arrow/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fire_breath/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fire_breath/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fire_breath/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fire_breath/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fire_breath/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fire_breath/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fire_breath/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fire_breath/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fire_breath/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fire_breath/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fireball/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fireball/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fireball/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fireball/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fireball/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fireball/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fireball/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fireball/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fireball/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fireball/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firebolt/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firebolt/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firebolt/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firebolt/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firebolt/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firebolt/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firebolt/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firebolt/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firebolt/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firebolt/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firecracker/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firecracker/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firecracker/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firecracker/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firecracker/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firecracker/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firecracker/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firecracker/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firecracker/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firecracker/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firefly_swarm/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firefly_swarm/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firefly_swarm/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firefly_swarm/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firefly_swarm/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firefly_swarm/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firefly_swarm/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firefly_swarm/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firefly_swarm/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/firefly_swarm/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/flaming_barrage/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/flaming_barrage/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/flaming_barrage/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/flaming_barrage/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/flaming_barrage/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/flaming_barrage/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/flaming_barrage/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/flaming_barrage/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/flaming_barrage/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/flaming_barrage/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/flaming_strike/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/flaming_strike/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/flaming_strike/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/flaming_strike/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/flaming_strike/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/flaming_strike/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/flaming_strike/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/flaming_strike/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/flaming_strike/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/flaming_strike/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fortify/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fortify/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fortify/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fortify/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fortify/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fortify/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fortify/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fortify/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fortify/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/fortify/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frost_step/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frost_step/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frost_step/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frost_step/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frost_step/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frost_step/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frost_step/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frost_step/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frost_step/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frost_step/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frostbite/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frostbite/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frostbite/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frostbite/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frostbite/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frostbite/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frostbite/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frostbite/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frostbite/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frostbite/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frostwave/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frostwave/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frostwave/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frostwave/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frostwave/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frostwave/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frostwave/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frostwave/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frostwave/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/frostwave/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gluttony/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gluttony/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gluttony/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gluttony/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gluttony/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gluttony/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gluttony/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gluttony/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gluttony/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gluttony/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gravity_fissure/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gravity_fissure/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gravity_fissure/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gravity_fissure/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gravity_fissure/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gravity_fissure/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gravity_fissure/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gravity_fissure/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gravity_fissure/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gravity_fissure/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/greater_heal/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/greater_heal/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/greater_heal/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/greater_heal/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/greater_heal/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/greater_heal/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/greater_heal/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/greater_heal/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/greater_heal/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/greater_heal/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/guiding_bolt/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/guiding_bolt/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/guiding_bolt/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/guiding_bolt/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/guiding_bolt/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/guiding_bolt/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/guiding_bolt/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/guiding_bolt/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/guiding_bolt/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/guiding_bolt/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gust/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gust/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gust/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gust/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gust/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gust/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gust/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gust/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gust/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/gust/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/haste/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/haste/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/haste/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/haste/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/haste/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/haste/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/haste/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/haste/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/haste/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/haste/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heal/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heal/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heal/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heal/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heal/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heal/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heal/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heal/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heal/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heal/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/healing_circle/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/healing_circle/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/healing_circle/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/healing_circle/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/healing_circle/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/healing_circle/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/healing_circle/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/healing_circle/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/healing_circle/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/healing_circle/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heartstop/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heartstop/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heartstop/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heartstop/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heartstop/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heartstop/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heartstop/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heartstop/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heartstop/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heartstop/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heat_surge/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heat_surge/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heat_surge/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heat_surge/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heat_surge/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heat_surge/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heat_surge/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heat_surge/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heat_surge/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/heat_surge/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_block/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_block/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_block/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_block/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_block/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_block/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_block/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_block/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_block/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_block/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_spikes/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_spikes/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_spikes/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_spikes/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_spikes/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_spikes/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_spikes/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_spikes/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_spikes/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_spikes/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_tomb/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_tomb/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_tomb/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_tomb/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_tomb/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_tomb/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_tomb/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_tomb/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_tomb/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ice_tomb/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/icicle/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/icicle/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/icicle/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/icicle/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/icicle/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/icicle/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/icicle/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/icicle/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/icicle/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/icicle/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/invisibility/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/invisibility/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/invisibility/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/invisibility/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/invisibility/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/invisibility/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/invisibility/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/invisibility/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/invisibility/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/invisibility/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lightning_bolt/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lightning_bolt/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lightning_bolt/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lightning_bolt/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lightning_bolt/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lightning_bolt/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lightning_bolt/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lightning_bolt/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lightning_bolt/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lightning_bolt/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lightning_lance/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lightning_lance/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lightning_lance/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lightning_lance/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lightning_lance/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lightning_lance/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lightning_lance/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lightning_lance/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lightning_lance/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lightning_lance/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lob_creeper/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lob_creeper/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lob_creeper/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lob_creeper/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lob_creeper/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lob_creeper/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lob_creeper/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lob_creeper/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lob_creeper/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/lob_creeper/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magic_arrow/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magic_arrow/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magic_arrow/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magic_arrow/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magic_arrow/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magic_arrow/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magic_arrow/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magic_arrow/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magic_arrow/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magic_arrow/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magic_missile/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magic_missile/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magic_missile/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magic_missile/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magic_missile/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magic_missile/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magic_missile/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magic_missile/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magic_missile/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magic_missile/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magma_bomb/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magma_bomb/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magma_bomb/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magma_bomb/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magma_bomb/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magma_bomb/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magma_bomb/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magma_bomb/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magma_bomb/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/magma_bomb/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/oakskin/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/oakskin/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/oakskin/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/oakskin/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/oakskin/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/oakskin/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/oakskin/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/oakskin/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/oakskin/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/oakskin/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/planar_sight/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/planar_sight/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/planar_sight/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/planar_sight/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/planar_sight/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/planar_sight/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/planar_sight/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/planar_sight/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/planar_sight/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/planar_sight/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/pocket_dimension/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/pocket_dimension/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/pocket_dimension/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/pocket_dimension/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/pocket_dimension/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/pocket_dimension/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/pocket_dimension/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/pocket_dimension/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/pocket_dimension/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/pocket_dimension/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_arrow/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_arrow/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_arrow/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_arrow/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_arrow/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_arrow/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_arrow/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_arrow/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_arrow/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_arrow/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_breath/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_breath/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_breath/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_breath/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_breath/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_breath/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_breath/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_breath/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_breath/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_breath/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_splash/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_splash/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_splash/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_splash/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_splash/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_splash/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_splash/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_splash/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_splash/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/poison_splash/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/portal/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/portal/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/portal/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/portal/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/portal/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/portal/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/portal/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/portal/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/portal/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/portal/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/raise_dead/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/raise_dead/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/raise_dead/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/raise_dead/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/raise_dead/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/raise_dead/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/raise_dead/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/raise_dead/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/raise_dead/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/raise_dead/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/raise_hell/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/raise_hell/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/raise_hell/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/raise_hell/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/raise_hell/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/raise_hell/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/raise_hell/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/raise_hell/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/raise_hell/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/raise_hell/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ray_of_frost/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ray_of_frost/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ray_of_frost/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ray_of_frost/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ray_of_frost/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ray_of_frost/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ray_of_frost/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ray_of_frost/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ray_of_frost/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ray_of_frost/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ray_of_siphoning/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ray_of_siphoning/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ray_of_siphoning/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ray_of_siphoning/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ray_of_siphoning/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ray_of_siphoning/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ray_of_siphoning/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ray_of_siphoning/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ray_of_siphoning/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/ray_of_siphoning/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/recall/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/recall/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/recall/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/recall/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/recall/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/recall/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/recall/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/recall/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/recall/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/recall/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/root/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/root/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/root/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/root/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/root/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/root/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/root/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/root/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/root/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/root/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sacrifice/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sacrifice/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sacrifice/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sacrifice/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sacrifice/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sacrifice/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sacrifice/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sacrifice/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sacrifice/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sacrifice/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/scapegoat/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/scapegoat/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/scapegoat/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/scapegoat/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/scapegoat/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/scapegoat/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/scapegoat/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/scapegoat/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/scapegoat/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/scapegoat/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/scorch/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/scorch/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/scorch/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/scorch/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/scorch/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/scorch/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/scorch/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/scorch/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/scorch/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/scorch/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sculk_tentacles/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sculk_tentacles/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sculk_tentacles/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sculk_tentacles/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sculk_tentacles/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sculk_tentacles/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sculk_tentacles/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sculk_tentacles/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sculk_tentacles/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sculk_tentacles/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shadow_slash/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shadow_slash/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shadow_slash/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shadow_slash/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shadow_slash/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shadow_slash/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shadow_slash/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shadow_slash/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shadow_slash/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shadow_slash/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shield/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shield/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shield/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shield/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shield/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shield/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shield/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shield/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shield/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shield/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shockwave/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shockwave/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shockwave/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shockwave/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shockwave/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shockwave/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shockwave/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shockwave/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shockwave/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/shockwave/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/slow/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/slow/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/slow/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/slow/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/slow/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/slow/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/slow/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/slow/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/slow/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/slow/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/snowball/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/snowball/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/snowball/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/snowball/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/snowball/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/snowball/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/snowball/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/snowball/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/snowball/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/snowball/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sonic_boom/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sonic_boom/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sonic_boom/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sonic_boom/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sonic_boom/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sonic_boom/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sonic_boom/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sonic_boom/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sonic_boom/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sonic_boom/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/spectral_hammer/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/spectral_hammer/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/spectral_hammer/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/spectral_hammer/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/spectral_hammer/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/spectral_hammer/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/spectral_hammer/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/spectral_hammer/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/spectral_hammer/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/spectral_hammer/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/spider_aspect/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/spider_aspect/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/spider_aspect/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/spider_aspect/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/spider_aspect/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/spider_aspect/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/spider_aspect/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/spider_aspect/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/spider_aspect/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/spider_aspect/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/starfall/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/starfall/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/starfall/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/starfall/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/starfall/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/starfall/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/starfall/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/starfall/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/starfall/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/starfall/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/stomp/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/stomp/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/stomp/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/stomp/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/stomp/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/stomp/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/stomp/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/stomp/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/stomp/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/stomp/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_ender_chest/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_ender_chest/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_ender_chest/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_ender_chest/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_ender_chest/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_ender_chest/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_ender_chest/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_ender_chest/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_ender_chest/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_ender_chest/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_horse/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_horse/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_horse/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_horse/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_horse/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_horse/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_horse/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_horse/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_horse/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_horse/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_polar_bear/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_polar_bear/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_polar_bear/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_polar_bear/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_polar_bear/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_polar_bear/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_polar_bear/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_polar_bear/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_polar_bear/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_polar_bear/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_swords/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_swords/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_swords/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_swords/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_swords/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_swords/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_swords/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_swords/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_swords/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_swords/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_vex/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_vex/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_vex/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_vex/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_vex/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_vex/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_vex/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_vex/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_vex/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/summon_vex/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sunbeam/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sunbeam/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sunbeam/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sunbeam/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sunbeam/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sunbeam/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sunbeam/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sunbeam/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sunbeam/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/sunbeam/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/telekinesis/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/telekinesis/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/telekinesis/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/telekinesis/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/telekinesis/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/telekinesis/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/telekinesis/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/telekinesis/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/telekinesis/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/telekinesis/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/teleport/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/teleport/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/teleport/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/teleport/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/teleport/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/teleport/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/teleport/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/teleport/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/teleport/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/teleport/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/throw/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/throw/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/throw/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/throw/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/throw/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/throw/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/throw/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/throw/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/throw/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/throw/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/thunderstorm/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/thunderstorm/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/thunderstorm/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/thunderstorm/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/thunderstorm/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/thunderstorm/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/thunderstorm/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/thunderstorm/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/thunderstorm/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/thunderstorm/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/touch_dig/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/touch_dig/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/touch_dig/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/touch_dig/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/touch_dig/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/touch_dig/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/touch_dig/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/touch_dig/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/touch_dig/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/touch_dig/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/volt_strike/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/volt_strike/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/volt_strike/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/volt_strike/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/volt_strike/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/volt_strike/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/volt_strike/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/volt_strike/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/volt_strike/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/volt_strike/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wall_of_fire/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wall_of_fire/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wall_of_fire/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wall_of_fire/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wall_of_fire/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wall_of_fire/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wall_of_fire/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wall_of_fire/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wall_of_fire/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wall_of_fire/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wisp/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wisp/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wisp/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wisp/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wisp/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wisp/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wisp/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wisp/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wisp/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wisp/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wither_skull/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wither_skull/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wither_skull/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wither_skull/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wither_skull/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wither_skull/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wither_skull/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wither_skull/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wither_skull/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wither_skull/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wololo/level_1.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wololo/level_10.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wololo/level_2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wololo/level_3.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wololo/level_4.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wololo/level_5.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wololo/level_6.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wololo/level_7.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wololo/level_8.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/iron/irons_spellbooks/wololo/level_9.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/mana_infusion.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/mana_well.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/spell_loom.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/spell_transcription.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/spell_uninscription.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/models/item/spellbook_binding.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/sounds.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/textures/block/glyph_press_plate.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/block/glyph_press_plate_active.png` | B | PNG decoded 16x128 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/block/glyph_press_plate_active.png.mcmeta` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_n_spells/textures/block/glyph_press_stone.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/block/glyph_press_wood.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/block/spell_loom_bottom.png` | B | PNG decoded 16x16 RGB; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/block/spell_loom_brass.png` | B | PNG decoded 16x16 RGB; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/block/spell_loom_crystal.png` | B | PNG decoded 16x16 RGB; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/block/spell_loom_side.png` | B | PNG decoded 16x16 RGB; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/block/spell_loom_top.png` | B | PNG decoded 16x16 RGB; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/block/spell_loom_weave.png` | B | PNG decoded 16x16 RGB; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/spell/ars_cross_default.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/spell/icon_bolt.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/spell/icon_drop.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/spell/icon_eye.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/spell/icon_flame.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/spell/icon_leaf.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/spell/icon_moon.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/spell/icon_spark.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/spell/icon_star.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/spell/nature_arcane.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/spell/nature_blood.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/spell/nature_eldritch.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/spell/nature_ender.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/spell/nature_evocation.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/spell/nature_fire.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/spell/nature_holy.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/spell/nature_ice.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/spell/nature_lightning.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/spell/nature_nature.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/background/arcane.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/background/blood.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/background/eldritch.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/background/ender.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/background/evocation.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/background/fire.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/background/holy.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/background/ice.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/background/lightning.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/background/nature.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/background/none.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/carrier/book_ars.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/carrier/book_irons.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/carrier/book_mixed.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/carrier/focus.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/carrier/parchment.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/carrier/scroll_blank.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/carrier/scroll_inscribed.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/carrier/slot_conflict.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/carrier/slot_cross.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/carrier/slot_empty.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/carrier/slot_locked.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/carrier/slot_native.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/compat/absent.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/compat/addon_unknown.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/compat/api_verified.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/compat/available.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/compat/client_only.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/compat/conflict.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/compat/data_migrated.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/compat/degraded.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/compat/desynchronized.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/compat/disabled.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/compat/fallback.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/compat/migration_needed.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/compat/mismatch.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/compat/optional.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/compat/required.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/compat/resource_missing.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/compat/runtime_verified.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/compat/server_only.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/compat/synchronized.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/compat/unsupported.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/compat/untested.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/element/abjuration.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/element/air.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/element/arcane.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/element/conjuration.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/element/earth.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/element/fire.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/element/force.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/element/frost.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/element/light.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/element/manipulation.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/element/necromancy.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/element/poison.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/element/shadow.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/element/time.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/element/void.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/element/water.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/area.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/aura.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/conversion.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/cooldown.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/duration.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/efficiency.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/health.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/lp.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/mana_ars.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/mana_capacity.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/mana_cost.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/mana_irons.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/mana_refund.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/mana_regeneration.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/mana_reserve.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/mana_shared.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/mana_split.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/power.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/range.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/resistance.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/source.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/tier_one.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/tier_three.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/resource/tier_two.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ritual/mana_infusion.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ritual/mana_well.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ritual/spell_transcription.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ritual/spell_uninscription.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ritual/spellbook_binding.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/school/blood.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/school/eldritch.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/school/ender.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/school/evocation.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/school/fire.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/school/generic.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/school/holy.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/school/ice.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/school/lightning.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/school/nature.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/school/unknown.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/absorb.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/amplify.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/area_expand.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/banish.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/barrier.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/beam.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/blink.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/blizzard.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/blood_lance.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/blood_pact.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/break_block.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/burst.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/carry.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/chain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/charm.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/cleanse.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/collect.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/combustion.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/command_minion.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/cone.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/contingency.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/counterspell.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/craft.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/curse.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/cut.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/dampen.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/dash.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/detect.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/dispel.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/drain_life.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/earthquake.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/exchange.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/extend.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/extract.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/fireball.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/flame_jet.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/flight.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/freeze.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/frost_bolt.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/gravity_pull.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/gravity_push.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/grow.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/hail.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/harvest.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/haste.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/heal.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/hex.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/ignite.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/interact.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/invisibility.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/lance.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/leap.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/levitate.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/lifelink.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/light.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/lightning_bolt.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/mana_drain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/mana_restore.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/mana_transfer.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/meteor.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/missile.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/nova.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/orbit.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/phantom_grasp.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/phase.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/place_block.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/poison_cloud.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/portal.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/projectile.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/pulse.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/rain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/ray.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/recall.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/reflect.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/regenerate.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/reveal.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/ring.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/root_snare.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/rune.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/sacrifice.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/sanctuary.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/sand_blast.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/shard.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/shock_chain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/silence.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/slow.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/smelt.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/source_transfer.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/split.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/static_field.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/stone_spike.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/summon_familiar.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/summon_guardian.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/summon_undead.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/summon_weapon.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/teleport.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/terror.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/thorn_burst.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/thunderstorm.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/time_delay.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/toxic_spore.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/transmute.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/trap.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/vine_grasp.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/volley.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/wall.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/ward.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/wave.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/spell/wither.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/affinity_decay.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/affinity_gain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/cast_failed.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/cast_pending.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/cooldown_personal.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/cooldown_shared.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/insufficient_aura.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/insufficient_lp.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/insufficient_mana.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/interrupted.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/mana_boost.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/mana_regen.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/mastery.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/overdrawn.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/progression_gain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/resonance_depleted.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/resonance_lingering.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/resonance_ready.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/silenced.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/source_empty.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/source_proximity.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/spell_blacklisted.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/spell_bound.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/spell_missing.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/status/spell_unbound.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/back.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/bind.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/cancel.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/close.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/collapse.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/compare.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/confirm.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/copy.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/down.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/erase.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/error.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/expand.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/export.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/favorite.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/filter.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/forward.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/gamepad.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/grid.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/hand_main.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/hand_off.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/help.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/import.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/info.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/inscribe.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/inspect.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/inventory.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/keyboard.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/link.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/list.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/lock.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/mouse_right.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/next_spell.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/output.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/paste.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/preview.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/previous_spell.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/refresh.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/reload.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/reset.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/search.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/settings.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/sort.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/success.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/swap.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/transcribe.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/unbind.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/unfavorite.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/unlink.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/unlock.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/up.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/ui/warning.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/carrier/book_ars.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/carrier/book_irons.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/carrier/book_mixed.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/carrier/focus.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/carrier/parchment.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/carrier/scroll_blank.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/carrier/scroll_inscribed.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/carrier/slot_conflict.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/carrier/slot_cross.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/carrier/slot_empty.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/carrier/slot_locked.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/carrier/slot_native.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/compat/absent.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/compat/addon_unknown.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/compat/api_verified.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/compat/available.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/compat/client_only.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/compat/conflict.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/compat/data_migrated.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/compat/degraded.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/compat/desynchronized.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/compat/disabled.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/compat/fallback.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/compat/migration_needed.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/compat/mismatch.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/compat/optional.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/compat/required.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/compat/resource_missing.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/compat/runtime_verified.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/compat/server_only.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/compat/synchronized.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/compat/unsupported.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/compat/untested.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/element/abjuration.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/element/air.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/element/arcane.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/element/conjuration.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/element/earth.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/element/fire.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/element/force.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/element/frost.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/element/light.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/element/manipulation.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/element/necromancy.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/element/poison.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/element/shadow.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/element/time.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/element/void.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/element/water.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/area.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/aura.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/conversion.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/cooldown.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/duration.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/efficiency.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/health.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/lp.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/mana_ars.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/mana_capacity.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/mana_cost.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/mana_irons.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/mana_refund.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/mana_regeneration.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/mana_reserve.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/mana_shared.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/mana_split.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/power.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/range.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/resistance.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/source.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/tier_one.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/tier_three.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/resource/tier_two.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ritual/mana_infusion.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ritual/mana_well.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ritual/spell_transcription.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ritual/spell_uninscription.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ritual/spellbook_binding.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/school/blood.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/school/eldritch.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/school/ender.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/school/evocation.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/school/fire.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/school/generic.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/school/holy.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/school/ice.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/school/lightning.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/school/nature.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/school/unknown.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/absorb.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/amplify.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/area_expand.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/banish.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/barrier.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/beam.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/blink.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/blizzard.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/blood_lance.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/blood_pact.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/break_block.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/burst.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/carry.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/chain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/charm.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/cleanse.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/collect.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/combustion.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/command_minion.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/cone.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/contingency.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/counterspell.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/craft.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/curse.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/cut.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/dampen.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/dash.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/detect.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/dispel.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/drain_life.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/earthquake.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/exchange.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/extend.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/extract.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/fireball.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/flame_jet.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/flight.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/freeze.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/frost_bolt.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/gravity_pull.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/gravity_push.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/grow.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/hail.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/harvest.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/haste.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/heal.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/hex.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/ignite.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/interact.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/invisibility.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/lance.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/leap.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/levitate.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/lifelink.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/light.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/lightning_bolt.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/mana_drain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/mana_restore.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/mana_transfer.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/meteor.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/missile.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/nova.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/orbit.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/phantom_grasp.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/phase.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/place_block.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/poison_cloud.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/portal.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/projectile.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/pulse.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/rain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/ray.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/recall.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/reflect.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/regenerate.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/reveal.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/ring.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/root_snare.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/rune.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/sacrifice.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/sanctuary.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/sand_blast.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/shard.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/shock_chain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/silence.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/slow.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/smelt.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/source_transfer.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/split.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/static_field.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/stone_spike.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/summon_familiar.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/summon_guardian.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/summon_undead.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/summon_weapon.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/teleport.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/terror.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/thorn_burst.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/thunderstorm.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/time_delay.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/toxic_spore.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/transmute.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/trap.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/vine_grasp.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/volley.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/wall.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/ward.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/wave.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/spell/wither.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/affinity_decay.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/affinity_gain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/cast_failed.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/cast_pending.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/cooldown_personal.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/cooldown_shared.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/insufficient_aura.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/insufficient_lp.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/insufficient_mana.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/interrupted.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/mana_boost.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/mana_regen.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/mastery.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/overdrawn.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/progression_gain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/resonance_depleted.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/resonance_lingering.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/resonance_ready.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/silenced.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/source_empty.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/source_proximity.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/spell_blacklisted.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/spell_bound.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/spell_missing.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/status/spell_unbound.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/back.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/bind.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/cancel.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/close.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/collapse.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/compare.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/confirm.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/copy.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/down.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/erase.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/error.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/expand.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/export.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/favorite.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/filter.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/forward.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/gamepad.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/grid.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/hand_main.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/hand_off.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/help.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/import.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/info.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/inscribe.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/inspect.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/inventory.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/keyboard.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/link.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/list.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/lock.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/mouse_right.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/next_spell.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/output.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/paste.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/preview.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/previous_spell.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/refresh.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/reload.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/reset.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/search.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/settings.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/sort.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/success.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/swap.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/transcribe.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/unbind.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/unfavorite.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/unlink.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/unlock.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/up.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/disabled/ui/warning.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/carrier/book_ars.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/carrier/book_irons.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/carrier/book_mixed.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/carrier/focus.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/carrier/parchment.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/carrier/scroll_blank.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/carrier/scroll_inscribed.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/carrier/slot_conflict.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/carrier/slot_cross.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/carrier/slot_empty.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/carrier/slot_locked.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/carrier/slot_native.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/compat/absent.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/compat/addon_unknown.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/compat/api_verified.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/compat/available.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/compat/client_only.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/compat/conflict.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/compat/data_migrated.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/compat/degraded.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/compat/desynchronized.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/compat/disabled.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/compat/fallback.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/compat/migration_needed.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/compat/mismatch.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/compat/optional.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/compat/required.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/compat/resource_missing.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/compat/runtime_verified.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/compat/server_only.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/compat/synchronized.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/compat/unsupported.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/compat/untested.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/element/abjuration.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/element/air.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/element/arcane.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/element/conjuration.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/element/earth.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/element/fire.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/element/force.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/element/frost.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/element/light.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/element/manipulation.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/element/necromancy.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/element/poison.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/element/shadow.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/element/time.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/element/void.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/element/water.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/area.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/aura.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/conversion.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/cooldown.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/duration.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/efficiency.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/health.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/lp.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/mana_ars.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/mana_capacity.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/mana_cost.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/mana_irons.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/mana_refund.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/mana_regeneration.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/mana_reserve.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/mana_shared.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/mana_split.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/power.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/range.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/resistance.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/source.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/tier_one.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/tier_three.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/resource/tier_two.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ritual/mana_infusion.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ritual/mana_well.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ritual/spell_transcription.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ritual/spell_uninscription.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ritual/spellbook_binding.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/school/blood.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/school/eldritch.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/school/ender.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/school/evocation.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/school/fire.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/school/generic.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/school/holy.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/school/ice.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/school/lightning.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/school/nature.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/school/unknown.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/absorb.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/amplify.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/area_expand.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/banish.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/barrier.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/beam.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/blink.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/blizzard.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/blood_lance.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/blood_pact.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/break_block.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/burst.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/carry.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/chain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/charm.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/cleanse.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/collect.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/combustion.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/command_minion.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/cone.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/contingency.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/counterspell.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/craft.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/curse.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/cut.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/dampen.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/dash.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/detect.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/dispel.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/drain_life.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/earthquake.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/exchange.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/extend.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/extract.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/fireball.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/flame_jet.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/flight.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/freeze.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/frost_bolt.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/gravity_pull.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/gravity_push.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/grow.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/hail.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/harvest.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/haste.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/heal.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/hex.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/ignite.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/interact.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/invisibility.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/lance.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/leap.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/levitate.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/lifelink.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/light.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/lightning_bolt.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/mana_drain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/mana_restore.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/mana_transfer.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/meteor.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/missile.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/nova.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/orbit.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/phantom_grasp.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/phase.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/place_block.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/poison_cloud.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/portal.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/projectile.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/pulse.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/rain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/ray.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/recall.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/reflect.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/regenerate.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/reveal.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/ring.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/root_snare.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/rune.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/sacrifice.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/sanctuary.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/sand_blast.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/shard.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/shock_chain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/silence.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/slow.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/smelt.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/source_transfer.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/split.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/static_field.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/stone_spike.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/summon_familiar.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/summon_guardian.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/summon_undead.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/summon_weapon.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/teleport.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/terror.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/thorn_burst.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/thunderstorm.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/time_delay.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/toxic_spore.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/transmute.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/trap.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/vine_grasp.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/volley.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/wall.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/ward.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/wave.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/spell/wither.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/affinity_decay.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/affinity_gain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/cast_failed.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/cast_pending.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/cooldown_personal.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/cooldown_shared.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/insufficient_aura.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/insufficient_lp.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/insufficient_mana.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/interrupted.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/mana_boost.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/mana_regen.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/mastery.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/overdrawn.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/progression_gain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/resonance_depleted.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/resonance_lingering.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/resonance_ready.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/silenced.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/source_empty.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/source_proximity.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/spell_blacklisted.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/spell_bound.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/spell_missing.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/status/spell_unbound.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/back.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/bind.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/cancel.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/close.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/collapse.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/compare.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/confirm.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/copy.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/down.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/erase.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/error.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/expand.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/export.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/favorite.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/filter.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/forward.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/gamepad.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/grid.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/hand_main.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/hand_off.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/help.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/import.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/info.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/inscribe.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/inspect.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/inventory.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/keyboard.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/link.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/list.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/lock.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/mouse_right.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/next_spell.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/output.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/paste.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/preview.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/previous_spell.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/refresh.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/reload.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/reset.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/search.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/settings.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/sort.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/success.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/swap.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/transcribe.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/unbind.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/unfavorite.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/unlink.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/unlock.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/up.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/high_contrast/ui/warning.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/carrier/book_ars.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/carrier/book_irons.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/carrier/book_mixed.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/carrier/focus.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/carrier/parchment.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/carrier/scroll_blank.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/carrier/scroll_inscribed.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/carrier/slot_conflict.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/carrier/slot_cross.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/carrier/slot_empty.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/carrier/slot_locked.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/carrier/slot_native.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/compat/absent.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/compat/addon_unknown.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/compat/api_verified.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/compat/available.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/compat/client_only.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/compat/conflict.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/compat/data_migrated.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/compat/degraded.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/compat/desynchronized.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/compat/disabled.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/compat/fallback.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/compat/migration_needed.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/compat/mismatch.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/compat/optional.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/compat/required.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/compat/resource_missing.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/compat/runtime_verified.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/compat/server_only.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/compat/synchronized.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/compat/unsupported.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/compat/untested.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/element/abjuration.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/element/air.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/element/arcane.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/element/conjuration.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/element/earth.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/element/fire.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/element/force.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/element/frost.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/element/light.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/element/manipulation.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/element/necromancy.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/element/poison.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/element/shadow.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/element/time.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/element/void.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/element/water.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/area.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/aura.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/conversion.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/cooldown.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/duration.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/efficiency.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/health.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/lp.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/mana_ars.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/mana_capacity.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/mana_cost.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/mana_irons.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/mana_refund.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/mana_regeneration.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/mana_reserve.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/mana_shared.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/mana_split.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/power.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/range.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/resistance.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/source.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/tier_one.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/tier_three.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/resource/tier_two.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ritual/mana_infusion.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ritual/mana_well.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ritual/spell_transcription.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ritual/spell_uninscription.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ritual/spellbook_binding.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/school/blood.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/school/eldritch.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/school/ender.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/school/evocation.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/school/fire.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/school/generic.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/school/holy.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/school/ice.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/school/lightning.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/school/nature.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/school/unknown.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/absorb.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/amplify.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/area_expand.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/banish.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/barrier.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/beam.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/blink.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/blizzard.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/blood_lance.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/blood_pact.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/break_block.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/burst.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/carry.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/chain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/charm.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/cleanse.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/collect.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/combustion.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/command_minion.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/cone.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/contingency.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/counterspell.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/craft.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/curse.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/cut.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/dampen.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/dash.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/detect.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/dispel.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/drain_life.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/earthquake.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/exchange.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/extend.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/extract.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/fireball.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/flame_jet.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/flight.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/freeze.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/frost_bolt.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/gravity_pull.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/gravity_push.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/grow.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/hail.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/harvest.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/haste.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/heal.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/hex.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/ignite.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/interact.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/invisibility.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/lance.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/leap.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/levitate.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/lifelink.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/light.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/lightning_bolt.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/mana_drain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/mana_restore.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/mana_transfer.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/meteor.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/missile.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/nova.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/orbit.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/phantom_grasp.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/phase.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/place_block.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/poison_cloud.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/portal.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/projectile.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/pulse.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/rain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/ray.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/recall.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/reflect.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/regenerate.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/reveal.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/ring.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/root_snare.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/rune.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/sacrifice.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/sanctuary.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/sand_blast.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/shard.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/shock_chain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/silence.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/slow.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/smelt.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/source_transfer.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/split.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/static_field.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/stone_spike.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/summon_familiar.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/summon_guardian.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/summon_undead.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/summon_weapon.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/teleport.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/terror.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/thorn_burst.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/thunderstorm.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/time_delay.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/toxic_spore.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/transmute.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/trap.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/vine_grasp.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/volley.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/wall.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/ward.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/wave.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/spell/wither.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/affinity_decay.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/affinity_gain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/cast_failed.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/cast_pending.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/cooldown_personal.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/cooldown_shared.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/insufficient_aura.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/insufficient_lp.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/insufficient_mana.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/interrupted.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/mana_boost.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/mana_regen.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/mastery.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/overdrawn.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/progression_gain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/resonance_depleted.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/resonance_lingering.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/resonance_ready.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/silenced.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/source_empty.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/source_proximity.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/spell_blacklisted.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/spell_bound.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/spell_missing.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/status/spell_unbound.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/back.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/bind.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/cancel.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/close.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/collapse.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/compare.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/confirm.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/copy.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/down.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/erase.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/error.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/expand.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/export.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/favorite.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/filter.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/forward.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/gamepad.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/grid.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/hand_main.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/hand_off.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/help.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/import.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/info.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/inscribe.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/inspect.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/inventory.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/keyboard.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/link.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/list.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/lock.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/mouse_right.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/next_spell.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/output.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/paste.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/preview.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/previous_spell.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/refresh.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/reload.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/reset.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/search.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/settings.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/sort.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/success.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/swap.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/transcribe.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/unbind.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/unfavorite.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/unlink.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/unlock.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/up.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/monochrome/ui/warning.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/carrier/book_ars.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/carrier/book_irons.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/carrier/book_mixed.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/carrier/focus.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/carrier/parchment.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/carrier/scroll_blank.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/carrier/scroll_inscribed.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/carrier/slot_conflict.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/carrier/slot_cross.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/carrier/slot_empty.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/carrier/slot_locked.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/carrier/slot_native.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/compat/absent.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/compat/addon_unknown.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/compat/api_verified.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/compat/available.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/compat/client_only.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/compat/conflict.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/compat/data_migrated.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/compat/degraded.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/compat/desynchronized.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/compat/disabled.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/compat/fallback.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/compat/migration_needed.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/compat/mismatch.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/compat/optional.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/compat/required.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/compat/resource_missing.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/compat/runtime_verified.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/compat/server_only.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/compat/synchronized.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/compat/unsupported.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/compat/untested.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/element/abjuration.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/element/air.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/element/arcane.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/element/conjuration.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/element/earth.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/element/fire.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/element/force.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/element/frost.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/element/light.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/element/manipulation.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/element/necromancy.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/element/poison.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/element/shadow.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/element/time.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/element/void.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/element/water.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/area.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/aura.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/conversion.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/cooldown.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/duration.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/efficiency.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/health.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/lp.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/mana_ars.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/mana_capacity.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/mana_cost.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/mana_irons.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/mana_refund.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/mana_regeneration.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/mana_reserve.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/mana_shared.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/mana_split.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/power.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/range.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/resistance.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/source.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/tier_one.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/tier_three.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/resource/tier_two.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ritual/mana_infusion.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ritual/mana_well.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ritual/spell_transcription.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ritual/spell_uninscription.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ritual/spellbook_binding.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/school/blood.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/school/eldritch.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/school/ender.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/school/evocation.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/school/fire.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/school/generic.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/school/holy.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/school/ice.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/school/lightning.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/school/nature.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/school/unknown.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/absorb.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/amplify.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/area_expand.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/banish.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/barrier.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/beam.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/blink.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/blizzard.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/blood_lance.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/blood_pact.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/break_block.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/burst.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/carry.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/chain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/charm.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/cleanse.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/collect.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/combustion.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/command_minion.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/cone.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/contingency.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/counterspell.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/craft.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/curse.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/cut.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/dampen.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/dash.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/detect.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/dispel.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/drain_life.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/earthquake.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/exchange.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/extend.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/extract.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/fireball.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/flame_jet.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/flight.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/freeze.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/frost_bolt.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/gravity_pull.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/gravity_push.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/grow.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/hail.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/harvest.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/haste.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/heal.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/hex.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/ignite.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/interact.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/invisibility.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/lance.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/leap.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/levitate.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/lifelink.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/light.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/lightning_bolt.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/mana_drain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/mana_restore.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/mana_transfer.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/meteor.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/missile.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/nova.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/orbit.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/phantom_grasp.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/phase.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/place_block.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/poison_cloud.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/portal.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/projectile.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/pulse.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/rain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/ray.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/recall.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/reflect.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/regenerate.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/reveal.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/ring.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/root_snare.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/rune.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/sacrifice.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/sanctuary.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/sand_blast.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/shard.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/shock_chain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/silence.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/slow.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/smelt.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/source_transfer.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/split.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/static_field.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/stone_spike.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/summon_familiar.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/summon_guardian.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/summon_undead.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/summon_weapon.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/teleport.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/terror.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/thorn_burst.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/thunderstorm.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/time_delay.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/toxic_spore.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/transmute.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/trap.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/vine_grasp.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/volley.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/wall.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/ward.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/wave.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/spell/wither.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/affinity_decay.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/affinity_gain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/cast_failed.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/cast_pending.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/cooldown_personal.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/cooldown_shared.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/insufficient_aura.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/insufficient_lp.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/insufficient_mana.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/interrupted.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/mana_boost.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/mana_regen.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/mastery.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/overdrawn.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/progression_gain.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/resonance_depleted.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/resonance_lingering.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/resonance_ready.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/silenced.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/source_empty.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/source_proximity.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/spell_blacklisted.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/spell_bound.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/spell_missing.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/status/spell_unbound.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/back.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/bind.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/cancel.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/close.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/collapse.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/compare.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/confirm.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/copy.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/down.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/erase.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/error.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/expand.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/export.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/favorite.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/filter.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/forward.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/gamepad.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/grid.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/hand_main.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/hand_off.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/help.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/import.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/info.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/inscribe.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/inspect.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/inventory.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/keyboard.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/link.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/list.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/lock.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/mouse_right.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/next_spell.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/output.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/paste.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/preview.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/previous_spell.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/refresh.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/reload.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/reset.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/search.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/settings.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/sort.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/success.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/swap.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/transcribe.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/unbind.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/unfavorite.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/unlink.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/unlock.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/up.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/icons/v2/variants/selected/ui/warning.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/spell_icons/ars_cross_1.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/spell_icons/ars_cross_2.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/spell_icons/ars_cross_3.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/spell_icons/ars_cross_4.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/spell_icons/ars_cross_5.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/spell_icons/ars_cross_6.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/spell_icons/ars_cross_7.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/gui/spell_icons/ars_cross_8.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/item/blank_glyph_plate.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/item/blank_scroll.png` | B | PNG decoded 16x16 P; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/item/imported_glyph/level_1.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/item/imported_glyph/level_10.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/item/imported_glyph/level_2.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/item/imported_glyph/level_3.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/item/imported_glyph/level_4.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/item/imported_glyph/level_5.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/item/imported_glyph/level_6.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/item/imported_glyph/level_7.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/item/imported_glyph/level_8.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/item/imported_glyph/level_9.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/item/imported_glyph/plate.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/item/imported_glyph/rune.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/item/mana_infusion.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/item/mana_well.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/item/spell_transcription.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/item/spell_uninscription.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_n_spells/textures/item/spellbook_binding.png` | B | PNG decoded 16x16 RGBA; no complete visual/runtime review |
| `src/main/resources/assets/ars_nouveau/patchouli_books/worn_notebook/en_us/categories/ars_n_spells.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_nouveau/patchouli_books/worn_notebook/en_us/entries/ars_n_spells/binding.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_nouveau/patchouli_books/worn_notebook/en_us/entries/ars_n_spells/glyph_press.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_nouveau/patchouli_books/worn_notebook/en_us/entries/ars_n_spells/imported_glyphs.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_nouveau/patchouli_books/worn_notebook/en_us/entries/ars_n_spells/loom.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_nouveau/patchouli_books/worn_notebook/en_us/entries/ars_n_spells/rules.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_nouveau/patchouli_books/worn_notebook/en_us/entries/ars_n_spells/transcription.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/ars_nouveau/patchouli_books/worn_notebook/en_us/entries/ars_n_spells/unbind.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/assets/minecraft/atlases/blocks.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/advancements/bind_spell.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/advancements/craft_spell_loom.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/advancements/first_cross_cast.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/advancements/recipes/misc/blank_glyph_plate.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/advancements/recipes/misc/blank_scroll.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/advancements/recipes/misc/glyph_press.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/advancements/recipes/misc/spell_loom.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/advancements/transcribe_spell.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/ans_glyph_schools/ars_zero.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/ans_iron_glyphs/press_tiers.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/loot_modifiers/blank_scroll_common.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/loot_modifiers/blank_scroll_irons.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/loot_modifiers/blank_scroll_library.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/loot_modifiers/blank_scroll_treasure.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/loot_tables/blocks/glyph_press.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/loot_tables/blocks/spell_loom.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/recipes/apparatus/mana_infusion.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/recipes/apparatus/mana_well.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/recipes/apparatus/spell_transcription.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/recipes/apparatus/spell_uninscription.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/recipes/apparatus/spellbook_binding.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/recipes/blank_glyph_plate.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/recipes/blank_scroll.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/recipes/glyph_press.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/recipes/spell_loom.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/tags/blocks/source_jars.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/tags/items/blasphemy/aqua.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/tags/items/blasphemy/blood.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/tags/items/blasphemy/eldritch.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/tags/items/blasphemy/ender.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/tags/items/blasphemy/evocation.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/tags/items/blasphemy/fire.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/tags/items/blasphemy/geo.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/tags/items/blasphemy/holy.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/tags/items/blasphemy/ice.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/tags/items/blasphemy/lightning.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/tags/items/blasphemy/nature.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/tags/items/blasphemy/wind.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/tags/items/blasphemy_curios.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/tags/items/cross_cast_blacklist.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/tags/items/curio_spell_discount.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/tags/items/cursed_rings.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/tags/items/glyph_press_catalysts.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/tags/items/irons_spell_books.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/ars_n_spells/tags/items/virtue_rings.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/forge/loot_modifiers/global_loot_modifiers.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/data/minecraft/tags/blocks/mineable/pickaxe.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/main/resources/pack.mcmeta` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/java/com/otectus/arsnspells/TestPaths.java` | R | Text opened in full, lines 1-33 |
| `src/test/java/com/otectus/arsnspells/VersionRangeTest.java` | R | Text opened in full, lines 1-43 |
| `src/test/java/com/otectus/arsnspells/augmentation/ResonanceManagerClampTest.java` | R | Text opened in full, lines 1-80 |
| `src/test/java/com/otectus/arsnspells/augmentation/ResonanceStateTest.java` | R | Text opened in full, lines 1-26 |
| `src/test/java/com/otectus/arsnspells/bridge/AnsModifierIdentitiesTest.java` | R | Text opened in full, lines 1-86 |
| `src/test/java/com/otectus/arsnspells/bridge/BridgeManagerFallbackTest.java` | R | Text opened in full, lines 1-39 |
| `src/test/java/com/otectus/arsnspells/bridge/BridgeManagerModeTest.java` | R | Text opened in full, lines 1-73 |
| `src/test/java/com/otectus/arsnspells/bridge/BridgeManagerRollbackContractTest.java` | R | Text opened in full, lines 1-150 |
| `src/test/java/com/otectus/arsnspells/bridge/ClampingBridgeConsumeExactnessTest.java` | P | Opened lines 1-72; remaining ranges not semantically reviewed |
| `src/test/java/com/otectus/arsnspells/bridge/ConversionUnitParityTest.java` | S | All bytes read and UTF-8 decoded (173 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/bridge/IManaBridgeAddManaContractTest.java` | S | All bytes read and UTF-8 decoded (165 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/bridge/IronsRegenScopeTest.java` | S | All bytes read and UTF-8 decoded (46 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/bridge/ManaMutationRoutingTest.java` | S | All bytes read and UTF-8 decoded (154 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/bridge/ManaRegenBridgeTest.java` | S | All bytes read and UTF-8 decoded (36 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/bridge/ModeRoutingSnapshotTest.java` | S | All bytes read and UTF-8 decoded (175 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/bridge/SharedPoolCeilingAmplificationTest.java` | S | All bytes read and UTF-8 decoded (65 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/bridge/SharedPoolCeilingIssPrimaryTest.java` | S | All bytes read and UTF-8 decoded (100 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/bridge/SharedPoolCeilingTest.java` | S | All bytes read and UTF-8 decoded (93 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/casting/AlternativePaymentTest.java` | S | All bytes read and UTF-8 decoded (298 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/casting/AttemptLedgerIntegrationTest.java` | S | All bytes read and UTF-8 decoded (170 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/casting/NativePaymentBoundaryTest.java` | S | All bytes read and UTF-8 decoded (273 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/casting/PaymentMessagesTest.java` | S | All bytes read and UTF-8 decoded (17 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/casting/TransactionSnapshotTest.java` | S | All bytes read and UTF-8 decoded (21 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/client/ClientAuraPeakTrackerTest.java` | S | All bytes read and UTF-8 decoded (48 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/client/ManaBarControllerOverlayMatchTest.java` | S | All bytes read and UTF-8 decoded (62 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/client/ManaBarVisibilityTest.java` | S | All bytes read and UTF-8 decoded (49 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/client/glyph/ImportedGlyphFallbackContentTest.java` | S | All bytes read and UTF-8 decoded (122 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/client/icons/SpellIconCompositionTest.java` | S | All bytes read and UTF-8 decoded (19 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/client/screen/ArrowProgressTest.java` | S | All bytes read and UTF-8 decoded (51 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/client/screen/ConfigScreenFactoryGateTest.java` | S | All bytes read and UTF-8 decoded (30 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/client/screen/ConfigScreenFactoryLegibilityTest.java` | S | All bytes read and UTF-8 decoded (70 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/combat/CombatDebugStateTest.java` | S | All bytes read and UTF-8 decoded (137 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/commands/AnsModeCommandTest.java` | S | All bytes read and UTF-8 decoded (53 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/compat/AuraFailureModeTest.java` | S | All bytes read and UTF-8 decoded (83 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/compat/BlasphemySchoolsTest.java` | S | All bytes read and UTF-8 decoded (14 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/compat/CovenantJarSurfaceTest.java` | R | Text opened in full, lines 1-205 |
| `src/test/java/com/otectus/arsnspells/compat/CurioTagDetectionTest.java` | S | All bytes read and UTF-8 decoded (74 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/compat/LpSourcePolicyTest.java` | S | All bytes read and UTF-8 decoded (21 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/compat/ScrollLPTrackerClearTest.java` | S | All bytes read and UTF-8 decoded (39 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/compat/ScrollLPTrackerFifoTest.java` | S | All bytes read and UTF-8 decoded (104 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/compat/TagDrivenDetectionTest.java` | S | All bytes read and UTF-8 decoded (75 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/config/AnsConfigStructureTest.java` | S | All bytes read and UTF-8 decoded (142 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/config/ConfigAuthorityTest.java` | S | All bytes read and UTF-8 decoded (44 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/config/ConfigSchemaMigrationTest.java` | S | All bytes read and UTF-8 decoded (131 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/config/InscribedCooldownMigrationTest.java` | S | All bytes read and UTF-8 decoded (60 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/config/SpellTranscriptionRecipeConditionalTest.java` | S | All bytes read and UTF-8 decoded (35 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/contract/ContractFixtureTest.java` | S | All bytes read and UTF-8 decoded (313 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/contract/RequestAdmissionTest.java` | S | All bytes read and UTF-8 decoded (57 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/data/AffinityDecayResidualTest.java` | S | All bytes read and UTF-8 decoded (121 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/data/CapabilityDataVersionTest.java` | S | All bytes read and UTF-8 decoded (47 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/data/SchoolMappingLoaderTest.java` | S | All bytes read and UTF-8 decoded (83 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/equipment/AttributeContributionTest.java` | S | All bytes read and UTF-8 decoded (37 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/equipment/EquipmentIntegrationConcurrencyTest.java` | S | All bytes read and UTF-8 decoded (47 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/events/AffinityHandlerCatchNarrowTest.java` | S | All bytes read and UTF-8 decoded (28 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/events/CapabilityProviderPriorityTest.java` | S | All bytes read and UTF-8 decoded (44 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/events/LPDeathPreventionDeathTickScopeTest.java` | S | All bytes read and UTF-8 decoded (40 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/events/LPDeathPreventionLogoutTest.java` | S | All bytes read and UTF-8 decoded (56 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/events/ProgressionHandlerRespawnHookTest.java` | S | All bytes read and UTF-8 decoded (44 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/events/RegenSynergyHandlerChunkGuardTest.java` | S | All bytes read and UTF-8 decoded (41 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/events/RegenSynergyHandlerConcurrencyTest.java` | S | All bytes read and UTF-8 decoded (26 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/events/RegenSynergyHandlerConfigGateTest.java` | S | All bytes read and UTF-8 decoded (67 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/glyph/IdentityPersistenceTest.java` | S | All bytes read and UTF-8 decoded (183 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/glyph/ImportedGlyphCatalogTest.java` | S | All bytes read and UTF-8 decoded (184 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/glyph/ImportedGlyphKeyTest.java` | S | All bytes read and UTF-8 decoded (63 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/glyph/ImportedGlyphKnowledgeTest.java` | S | All bytes read and UTF-8 decoded (34 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/glyph/SharedCatalogFixturesTest.java` | S | All bytes read and UTF-8 decoded (107 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/glyph/behavior/GlyphLiveDecisionTest.java` | S | All bytes read and UTF-8 decoded (171 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/glyph/behavior/SharedLevelFixturesTest.java` | S | All bytes read and UTF-8 decoded (88 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/glyph/behavior/SnapshotPublicationTest.java` | S | All bytes read and UTF-8 decoded (118 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/glyph/recipe/GlyphMappingSnapshotTest.java` | S | All bytes read and UTF-8 decoded (175 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/glyph/recipe/SharedMappingFixturesTest.java` | S | All bytes read and UTF-8 decoded (107 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/icons/IconCatalogTest.java` | S | All bytes read and UTF-8 decoded (30 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/inscription/InscriptionOutcomeSingleUnitTest.java` | S | All bytes read and UTF-8 decoded (91 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/inscription/InscriptionSourcePolicyTest.java` | S | All bytes read and UTF-8 decoded (28 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/mixin/ArsNSpellsMixinPluginGatingTest.java` | S | All bytes read and UTF-8 decoded (170 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/mixin/ArsNSpellsMixinPluginSanctifiedGatingTest.java` | S | All bytes read and UTF-8 decoded (80 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/mixin/MixinConfigIntegrityTest.java` | S | All bytes read and UTF-8 decoded (208 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/mixin/MixinInjectionPointImmunityTest.java` | S | All bytes read and UTF-8 decoded (172 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/mixin/MixinManaCapabilityPerUuidGuardTest.java` | S | All bytes read and UTF-8 decoded (73 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/mixin/MixinSpellResolverContextDeletedTest.java` | S | All bytes read and UTF-8 decoded (72 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/network/CrossCastRequestPacketTest.java` | S | All bytes read and UTF-8 decoded (92 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/network/PacketHandlerDirectionGuardsTest.java` | S | All bytes read and UTF-8 decoded (76 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/network/ResonanceSyncPacketBoundsTest.java` | S | All bytes read and UTF-8 decoded (71 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/network/SchoolJournalSnapshotTest.java` | S | All bytes read and UTF-8 decoded (29 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/network/SpellLoomExportPacketTest.java` | S | All bytes read and UTF-8 decoded (57 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/registry/CreativeTabWiringTest.java` | S | All bytes read and UTF-8 decoded (112 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/registry/ItemAssetCompletenessTest.java` | S | All bytes read and UTF-8 decoded (126 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/rituals/ApparatusRecipeIngredientTest.java` | S | All bytes read and UTF-8 decoded (76 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/rituals/InscriptionInputsIronsSplitTest.java` | S | All bytes read and UTF-8 decoded (58 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/rituals/InscriptionInputsPredicateTest.java` | S | All bytes read and UTF-8 decoded (100 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/rituals/ManaRitualBridgeRouteTest.java` | S | All bytes read and UTF-8 decoded (43 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/rituals/SpellbookBindingPredicateTest.java` | S | All bytes read and UTF-8 decoded (72 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/spell/ArsExportDeduplicationTest.java` | S | All bytes read and UTF-8 decoded (49 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/spell/ArsToIronsCarrierNbtTest.java` | S | All bytes read and UTF-8 decoded (60 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/spell/CastValidationScopeTest.java` | S | All bytes read and UTF-8 decoded (172 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/spell/CostQuoteRepeatabilityTest.java` | S | All bytes read and UTF-8 decoded (52 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/spell/CrossCastContextNoPrepaymentTest.java` | S | All bytes read and UTF-8 decoded (73 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/spell/CrossCastNbtRoundTripTest.java` | S | All bytes read and UTF-8 decoded (132 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/spell/CrossCastProxyAllocationTest.java` | S | All bytes read and UTF-8 decoded (110 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/spell/CrossCastValidatorTest.java` | S | All bytes read and UTF-8 decoded (212 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/spell/IronsBookBindingNbtTest.java` | S | All bytes read and UTF-8 decoded (124 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/spell/ScrollKindTest.java` | S | All bytes read and UTF-8 decoded (83 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/spell/SpellSchoolChoiceTest.java` | S | All bytes read and UTF-8 decoded (95 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/spell/SpellSchoolStorageTest.java` | S | All bytes read and UTF-8 decoded (145 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/util/ArsSpellIntegrityTest.java` | S | All bytes read and UTF-8 decoded (52 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/util/ChunkScanUtilTest.java` | S | All bytes read and UTF-8 decoded (70 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/util/LogPrivacyTest.java` | S | All bytes read and UTF-8 decoded (27 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/util/PayloadBudgetTest.java` | S | All bytes read and UTF-8 decoded (41 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/util/SchoolResolverAddonTest.java` | S | All bytes read and UTF-8 decoded (128 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/util/SchoolResolverTest.java` | S | All bytes read and UTF-8 decoded (175 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/util/SourceSynergyPolicyTest.java` | S | All bytes read and UTF-8 decoded (35 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/util/SpellAnalysisExplicitSchoolTest.java` | S | All bytes read and UTF-8 decoded (99 lines); semantic read not completed |
| `src/test/java/com/otectus/arsnspells/util/SpellScalingFormulaTest.java` | S | All bytes read and UTF-8 decoded (140 lines); semantic read not completed |
| `src/test/resources/ans-contract-fixtures/attempts.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans-contract-fixtures/inscription.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans-contract-fixtures/quotes.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans-contract-fixtures/routing.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalog.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/a_boolean_string_is_rejected.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/a_duplicate_spell_in_a_source_is_rejected.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/a_fractional_level_is_rejected.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/a_lowered_minimum_extends_the_range_downward.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/a_minimum_above_one_registers_from_the_minimum.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/a_mismatched_rarity_list_is_rejected.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/a_missing_required_field_is_rejected.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/a_newer_adapter_contract_registers_dormant.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/a_numeric_string_is_rejected.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/a_placeholder_never_replaces_a_published_contract.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/a_raised_minimum_never_drops_lower_identities.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/a_supported_placeholder_is_rejected.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/a_variant_cap_overflow_rejects_the_source.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/an_invalid_cast_type_is_rejected.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/an_invalid_id_is_rejected.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/an_invalid_rarity_is_rejected.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/an_out_of_range_adapter_version_is_rejected.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/an_overflowing_number_is_rejected.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/an_unknown_field_is_rejected.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/broken_required_catalog_fails_startup.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/equal_paths_in_two_namespaces_are_two_spells.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/future_schema_source_is_rejected.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/index.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/malformed_optional_source_is_rejected_whole.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/missing_published_identity_fails_startup.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/non_strict_json_is_rejected.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/optional_source_adds_a_new_spell.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/optional_source_cannot_change_an_adapter.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/optional_source_cannot_shrink_an_envelope.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/optional_source_extends_the_registered_maximum.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/placeholder_cannot_be_promoted_by_another_catalog.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/placeholder_is_promoted_by_a_later_version_of_its_catalog.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/required_catalog_registers_the_whole_envelope.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/required_catalog_spells_are_never_given_another_contract.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/retired_and_unsupported_spells_keep_their_identities.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/tool_package_promotion_loads_on_forge.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/catalogs/tool_package_promotion_loads_on_neoforge.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/levels/index.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/levels/lowered_maximum_makes_higher_levels_dormant.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/levels/maximum_beyond_the_envelope_reports_the_live_value.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/levels/pack_disable_applies_within_the_live_maximum.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/levels/policy_follows_the_live_school.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/levels/raised_maximum_wakes_a_reserved_level.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/levels/retired_identity_stays_dormant_whatever_irons_reports.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/levels/the_live_maximum_is_checked_before_pack_rules.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/levels/unregistered_spell_is_missing.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/levels/upstream_disabled_spell_is_dormant_at_every_level.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/mappings/custom_catalyst_tag_joins_the_union.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/mappings/deny_beats_allow.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/mappings/duration_above_the_shared_maximum_is_refused.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/mappings/duration_at_the_shared_maximum_is_valid.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/mappings/fractional_number_is_not_truncated.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/mappings/higher_pack_overrides_lower.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/mappings/huge_number_is_out_of_range.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/mappings/index.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/mappings/legacy_forge_spell_file.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/mappings/legacy_neoforge_policy_alias.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/mappings/level_beyond_registered_is_refused.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/mappings/press_tiers_v2.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/mappings/same_priority_duplicates_fail_closed.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/mappings/self_restriction.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/mappings/string_number_is_a_type_error.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/mappings/unknown_field_is_an_error_with_its_path.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/mappings/unknown_schema_is_rejected.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/mappings/v2_level_overrides_and_defaults.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/ans_shared_fixtures/mappings/zero_catalyst_is_valid.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `src/test/resources/gameteststructures/platform.snbt` | S | All bytes read and UTF-8 decoded (111 lines); semantic read not completed |
| `src/test/resources/gameteststructures/press_hall.snbt` | S | All bytes read and UTF-8 decoded (443 lines); semantic read not completed |
| `src/test/resources/released_identities_3.4.0.txt` | S | All bytes read and UTF-8 decoded (777 lines); semantic read not completed |
| `tools/add_icon_translations.py` | R | Text opened in full, lines 1-54 |
| `tools/addon_adapter.py` | R | Text opened in full, lines 1-106 |
| `tools/build_catalog_packages.py` | R | Text opened in full, lines 1-73 |
| `tools/contract_parity.py` | R | Text opened in full, lines 1-63 |
| `tools/covenant_profile.py` | R | Text opened in full, lines 1-88 |
| `tools/gen_ritual_tablets.py` | R | Text opened in full, lines 1-271 |
| `tools/generate_glyph_press.py` | R | Text opened in full, lines 1-415 |
| `tools/generate_icons.py` | R | Text opened in full, lines 1-276 |
| `tools/generate_iron_glyph_catalog.py` | R | Text opened in full, lines 1-377 |
| `tools/generate_release_references.py` | R | Text opened in full, lines 1-68 |
| `tools/generate_spell_loom.py` | R | Text opened in full, lines 1-128 |
| `tools/glyph_catalog_pack.py` | R | Text opened in full, lines 1-555 |
| `tools/icon_art.py` | R | Text opened in full, lines 1-302 |
| `tools/icon_catalog.csv` | S | All bytes read and UTF-8 decoded (275 lines); semantic read not completed |
| `tools/icon_labels.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `tools/iron_glyph_adapters.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
| `tools/pack_presets.py` | R | Text opened in full, lines 1-146 |
| `tools/port_gui.py` | R | Text opened in full, lines 1-74 |
| `tools/port_icon_assets.py` | R | Text opened in full, lines 1-61 |
| `tools/preview_glyph_press.py` | R | Text opened in full, lines 1-53 |
| `tools/preview_spell_loom.py` | R | Text opened in full, lines 1-105 |
| `tools/test_addon_adapter.py` | R | Text opened in full, lines 1-58 |
| `tools/test_covenant_profile.py` | R | Text opened in full, lines 1-68 |
| `tools/test_glyph_catalog_pack.py` | R | Text opened in full, lines 1-267 |
| `tools/test_pack_presets.py` | R | Text opened in full, lines 1-92 |
| `tools/test_validate_glyph_mappings.py` | R | Text opened in full, lines 1-182 |
| `tools/test_verify_gametest_log.py` | R | Text opened in full, lines 1-46 |
| `tools/test_verify_loader_parity.py` | R | Text opened in full, lines 1-72 |
| `tools/validate_glyph_mappings.py` | R | Text opened in full, lines 1-554 |
| `tools/verify_gametest_log.py` | R | Text opened in full, lines 1-70 |
| `tools/verify_icons.py` | R | Text opened in full, lines 1-49 |
| `tools/verify_loader_parity.py` | R | Text opened in full, lines 1-488 |
| `update.json` | D | Parsed in full; duplicate keys rejected; selected cross-reference/schema checks, not per-file semantic certification |
