# Ars 'n Spells 3.4.0 — investigation and improvement instructions

**Investigated:** 30 October 2026  
**Repository:** <https://github.com/otectus/ars-n-spells>  
**Purpose:** A prioritized implementation handoff covering functionality, compatibility, optimization, and verification. This document proposes work; it does not represent changes already made to the mod.

## 1. Findings that should drive the next release

The supplied feedback identifies two real architectural limitations. The built-in catalogs contain **111 Iron's spells and 777 spell/level variants**. A spell absent from the catalog has no registered Ars glyph identity. A configured level above a spell's catalog maximum likewise has no identity, even when Iron's accepts the scroll. Separately, amplification and some other behavior continue to use catalog defaults instead of the effective server configuration.

**Implement a persistent identity catalog plus a server-authoritative behavior snapshot.** Keep spell/level IDs stable, obtain live behavior through Iron's APIs, and supply explicit addon adapters. Updating only the Glyph Press's maximum-level check will not solve the problem: learning, spell serialization, augments, casting, recipes, and client displays must agree.

The two 3.4.0 implementations are materially different. NeoForge already discovers third-party catalog manifests and synchronizes a richer behavior snapshot. Forge needs those capabilities adapted to its architecture. Neither implementation should be described as having universal addon support, and neither should receive a wholesale rewrite that discards existing fixes.

Other actionable findings include the uninscription classification order, zero-catalyst recipe rejection, Forge's custom-catalyst insertion restriction, reload inconsistencies, and NeoForge's missing native level-bonus application and discarded channel event-level changes. Treat those as separate, reviewable changes.

### Evidence and scope

| Label | Meaning |
| --- | --- |
| **Confirmed** | The cited source contains the stated behavior or limitation. This does not imply that a Minecraft reproduction was executed during this review. |
| **Risk** | The source exposes a plausible failure path; prove the player-visible result with an integration test before claiming a reproduced bug. |
| **Enhancement** | A proposed capability or optimization, rather than a defect in promised behavior. |
| **Historical evidence** | A test result recorded by the repository maintainers, not rerun during this investigation. |

Reviewed both release source trees, changelogs, prior audit documentation, configuration and casting contracts, imported glyphs, the Press, rituals, network synchronization, compatibility boundaries, tests, and public issues. Compared the relevant upstream Iron's configuration and spell APIs. Locally reran the Python tooling suites: **33 tests passed on Forge and 27 on NeoForge**. No Minecraft client/server, Gradle Java build, or Java GameTest was run for this review. No addon-specific runtime compatibility is certified here. The feedback does not identify the affected addon spell IDs.

### Pinned baselines

| Track | Reviewed source | Dependency baseline |
| --- | --- | --- |
| Forge 1.20.1 | `main`, commit `0da9de067fac60db06f18ba407b266261c65db47` | Java 17; Forge 47.4.10; Ars Nouveau 4.12.7; Iron's 3.15.0 default compile/runtime profile and 3.16.3 alternate profile |
| NeoForge 1.21.1 | `port/neoforge-1.21.1`, commit `c234f19145d4380a849597f7e390fd535cc45e4f` | Java 21; NeoForge 21.1.248; Ars Nouveau 5.13.1.1400; Iron's 1.21.1-3.16.3 |

Use these commits to reproduce findings. Before implementation, compare them with the current branch and mark any intervening fix as already satisfied. Do not conflate Forge and NeoForge network protocol numbers or assume their item registries are interchangeable. See the [Forge release evidence][F-testing] and [NeoForge release evidence][N-testing].

## 2. Preserve the complete 3.4.0 baseline

Turn the following into regression constraints rather than proposing them as missing features:

- **Touch repair:** preserve server-derived target validation across native books, exported scrolls, bound books, wheel/hotkey/staff routes, and generic carriers. Preserve Sensitive water targeting and correct Self/Projectile behavior. A miss must not spend mana, train progression, or start a successful-cast cooldown.
- **Imported glyphs:** retain real Ars spell parts and physical glyph items, exact scroll-level identity, learning and tier restrictions, native effects, shared native cooldown/recast state, charged casts, continuous casts, and explicit refusal of unsupported caster routes.
- **Glyph Press:** preserve exact consumption of one filled native scroll and one blank plate, Source reservation, catalyst costs, input locks, cancellation, persistence, output blocking, server action validation, and sided automation. The default tiers are 1/2/4 gems, 500/1,500/3,000 Source, and 100/140/200 ticks, with a 4,000-Source default capacity. Automation stages inputs and removes output; player initiation is an existing policy, not a missing feature.
- **Mana and equipment:** retain all five modes (`iss_primary`, `ars_primary`, `hybrid`, `separate`, `disabled`), asymmetric conversion rates, transactional payment, refunds of unspent reservations, and equipment deduplication. In `iss_primary`, preserve the 3.3.7 work included in 3.4.0: the ceiling is the larger applicable Ars-derived or Iron's-plus-Ars-equipment capacity, not their sum.
- **School choice:** preserve the mechanical school selected in the Spell Loom across carriers, projectiles, delayed effects, native table/ritual/command binding, and saved spells. Cosmetic backgrounds remain separate. Deduplication includes mechanical school and excludes purely cosmetic changes.
- **Existing integrations:** retain progression, affinity/resonance, Source synergy, Curios handling, optional-mod classloading guards, the established inscription workflow, and loader-specific integrations such as the Forge Covenant/Blood Magic work where supported.
- **Already implemented hardening:** retain replay protection, request fingerprints, server authorization, target checks, narrow compatibility boundaries, and the completed earlier audit fixes. Reproduce an old issue against 3.4.0 before reopening its implementation work.

The NeoForge test record already includes broader imported-spell execution, dedicated-server restart, two-client, JEI, and editor checks. Forge's record explicitly has narrower coverage in several of those areas. Port behavior and tests where applicable; do not describe NeoForge's completed work as absent. [Sources: Forge testing][F-testing], [NeoForge testing][N-testing], [earlier audit status][F-audit].

## 3. Implementation order

`P0` means a data-preservation prerequisite; `P1` means a functional or compatibility correction; `P2` means maintainability, diagnostics, or measured performance work; `P3` means an optional product expansion.

| Work package | Priority | Applies to | Classification |
| --- | --- | --- | --- |
| A. Stable identities and addon catalogs | P0/P1 | Both; build on NeoForge's existing manifests | Confirmed limitation; persistence risk |
| B. Effective configuration and level semantics | P1 | Both | Confirmed |
| C. Native casting lifecycle, level events, payments | P1 | Both; different defects by loader | Confirmed plus integration risks |
| D. Glyph Press correctness and recovery | P1 | Both; several Forge-only findings | Confirmed plus recovery risks |
| E. Uninscription and carrier preservation | P1 | Both | Confirmed classification path |
| F. Reload, schemas, and client consistency | P1/P2 | Both | Confirmed divergence |
| G. Broader compatibility and gameplay contracts | P1/P2 | Both | Integration work |
| H. Performance and resource bounds | P2 | Both | Confirmed hot paths; impact to measure |
| I. Diagnostics, presentation, documentation | P2 | Both | Enhancement and documentation corrections |
| J. CI, runtime coverage, release gates | P1/P2 | Both | Verification work |
| K. Optional gameplay extensions | P3 | Both | Enhancement |

Ship the small independent fixes early. Do the identity and configuration design before advertising broader addon coverage or arbitrary configured maximum levels.

## 4. A — Stable identities and comprehensive addon support

### A1. Separate permanent identity from mutable behavior

**Confirmed:** Forge reads a bundled catalog. NeoForge discovers `ans_glyph_catalog/*.json` from loaded mod JARs. Both ultimately register a finite set of Ars parts and items before normal world datapack reloads. The shipped entries cover the base Iron's namespace. [Forge catalog][F-catalog], [NeoForge catalog][N-catalog], [NeoForge discovery][N-manifests].

1. Define a shared catalog contract containing the native namespaced spell ID, an explicit supported/reserved base-level range, stable glyph identity, adapter identity/version, and provenance. Keep behavior such as enablement, live maximum, tier, school, and price outside identity.
2. Preserve the existing spell-part encoding `ars_n_spells:iron/<namespace>/<path>/level_<n>`. Preserve each loader's existing item IDs: Forge uses the same identity for its item; NeoForge uses `ars_n_spells:glyph_iron/<namespace>/<path>/level_<n>`. Do not rename either as a parity cleanup. [Forge key][F-key], [NeoForge key][N-key].
3. Bring manifest discovery or an equivalent early registration extension point to Forge. Reuse NeoForge's existing implementation as a starting point, with explicit loader adaptations and tests.
4. Provide a pack-author catalog generator that consumes a **loaded registry inventory**, preserves published IDs, and produces a distributable early-load catalog/compatibility package. Extend existing tools rather than adding a second unrelated inventory format.
5. Investigate whether the supported loader lifecycle permits reading all native spell entries before the relevant Ars/item registration closes. Prove this with an ordering test. If it does not, use the early-load catalog package. Never promise runtime discovery that depends on an already-frozen registry.
6. Keep server datapacks responsible for behavior overrides, not registering new item identities on `/reload`. A single generic item with a level in item NBT is not an adequate substitute: Ars serializes learned spell parts by ID, so that level would not survive ordinary spell composition.

**Acceptance:** Two addons may use the same spell path in different namespaces without collision. A glyph survives learning, a saved recipe, export/import, restart, and a behavior reload with the same native spell and base level. A client with a different required identity catalog receives an intelligible mismatch error before using the world.

### A2. Retain identities when support changes

**Confirmed:** NeoForge's catalog registers keys only when `entry.supported()` is true. Changing a previously supported entry to `unsupported` therefore removes its identities. Removing the only JAR containing an addon manifest also removes that catalog contribution. Catalog-cap overflow can truncate later entries. These are persistence hazards; world damage was not reproduced in this review. [NeoForge catalog][N-catalog].

1. Maintain an append-only inventory of all released identities. A retired or disabled adapter must make its glyph **dormant**, not unregister it.
2. Introduce explicit tombstone entries that retain the part/item but refuse use with a useful explanation. Distinguish identity registration from execution support.
3. Retain addon identities in an ANS compatibility bundle or persistent pack catalog that remains installed when the gameplay addon is removed. A manifest residing only in the removed addon cannot provide that guarantee.
4. Validate the entire identity catalog before registration. Fail clearly on collisions, invalid required manifests, or cap overflow instead of silently booting with a shortened previously published identity set. An optional, genuinely new malformed contribution can be handled separately if it cannot erase published identities.
5. Document restart and client-distribution requirements. Correct any adapter-authoring instructions that suggest `unsupported` already preserves registered IDs.

**Acceptance:** Disable the feature, retire an adapter, remove/reinstall an addon while retaining its identity package, change manifest order, and load an old world. Known glyphs remain representable; inactive ones cannot cast; no ID is silently reassigned. Validate a catalog over the cap before world loading.

### A3. Support addons through tested execution contracts

Do not equate “extends `AbstractSpell`” with “safe under every Ars cast method.” Native spells may require a caster attachment, special cast data, a held item, recast state, entities with ownership, channel setup, or a custom resource.

1. Define an adapter capability contract for allowed delivery methods, target kind, native lifecycle, charged/continuous/recast behavior, resource handling, and compatibility versions. A conservative generic adapter may handle a proven subset; other spells need explicit adapters.
2. Preserve namespaced schools and spell IDs. Do not classify by translated name, namespace alone, or school alone. School does not establish targeting semantics.
3. Use conservative discovery: report every native spell, register eligible catalog identities, and mark unsupported execution with an exact reason. Do not produce a learnable glyph that predictably fails without explaining the limitation.
4. Publish coverage per **spell and cast route**, not just per mod: `registered`, `convertible`, `learnable`, `executable`, `recast tested`, `channel tested`, and known restrictions.
5. Start with representative addon families: simple projectiles, target effects, areas, summons, teleports, buffs, charges, channels, and recasts. Cataclysm: Spellbooks and Alshanex's Familiars are reasonable real-addon candidates; choose versions compatible with the exact loader and Iron's build. Their project pages establish candidates, not certification: [Cataclysm source][addon-cataclysm], [Cataclysm 1.21.1 source][addon-cataclysm-neo], [Alshanex project][addon-familiars].
6. Add a small test addon containing difficult lifecycle cases. This gives repeatable API coverage even when third-party releases change or lack source.

**Acceptance:** The loaded registry inventory accounts for every non-proxy native spell as supported, intentionally excluded, or unsupported with a reason. Test actual effects, targets, resource changes, and ownership; successful registration alone is insufficient.

## 5. B — Respect global and per-spell configuration everywhere

### B1. Use the effective Iron's API, after configuration is ready

**Confirmed:** In the inspected upstream APIs, `AbstractSpell.getMaxLevel()` reads `SpellConfigManager`, and `getSchoolType()` also reads effective configuration. The configuration manager applies global values where a spell parameter remains at its default, alongside per-spell configuration and config events. Reading static defaults or independently parsing the global file duplicates upstream policy and misses parts of that behavior. [Forge upstream spell][U-F-spell], [Forge upstream configuration][U-F-config], [NeoForge upstream spell][U-N-spell], [NeoForge upstream configuration][U-N-config].

1. Read effective maximum, enabled state, school, rarity, mana pricing, cooldowns, and learning requirements through the native runtime API or a version-specific bridge.
2. Build an immutable, versioned behavior snapshot once upstream configuration is authoritative. Treat pre-initialization fallback values as **not ready**, not as a permanent one-level spell definition.
3. Rebuild on the actual upstream lifecycle/config reload hooks and relevant ANS/tag reloads. Establish event ordering explicitly; queue a rebuild on the server thread when necessary. Do not race a file watcher against native config synchronization.
4. Apply the snapshot to the Press planner, item tooltips, learning, spell-editor validity, augment limits, cast admission, execution, school policy, diagnostics, and JEI.
5. Preserve Iron's as the authority for its configuration precedence. Test global-only, explicit per-spell override, datapack override, and configuration-event modification with the pinned native versions.

### B2. Correct both downward and upward maximum changes

**Confirmed:** Forge's imported effect/admission and NeoForge's imported effect/`GlyphSessions.effectiveLevel` use catalog maximums for augmentation. Both Press planners reject levels beyond the catalog range. NeoForge's live snapshot already marks base glyphs above the server maximum dormant, but that does not correct every augmented cast. [Forge effect][F-effect], [Forge admission][F-admission], [Forge planner][F-plan], [NeoForge effect][N-effect], [NeoForge sessions][N-sessions], [NeoForge planner][N-plan], [NeoForge behavior][N-snapshot].

Use three distinct quantities:

| Quantity | Authority | Required behavior |
| --- | --- | --- |
| Registered base-level envelope | Startup identity catalog | Finite, stable, shared by required clients; only expands through a compatible catalog update |
| Effective allowed base/augmented level | Live server configuration plus ANS augmentation policy | Determines which existing glyphs and augment combinations are valid now |
| Final native cast level | Native affinity/equipment and cast-level events | May legitimately exceed scroll maximum under native rules; do not blindly clamp it back down |

1. For a reduced maximum, retain higher-level IDs but make them dormant. Recompute Amplify limits and validate the effective pre-native-bonus level at cast time. A low-level glyph must not bypass the new maximum through Amplify.
2. For a raised maximum inside a pre-registered envelope, enable those levels without changing IDs. The Press must output exactly the input scroll level, with current tier and cost.
3. For a raised maximum outside the envelope, report the missing range and a concrete catalog-update/restart action. Do not silently downcast or invent a new registry entry during gameplay.
4. Choose and document practical per-spell reservation envelopes. Existing limits are 99 per identity, 4,096 total variants on Forge, and 8,192 on NeoForge. Registering 99 levels for all 111 base spells would already exceed both total-variant caps. Increasing a single constant is not a complete design.
5. If levels above 99 are supported, update ID parsing, schema validation, sync, display, generation, and tests together while preserving every old ID. Otherwise reject them explicitly and document the boundary.
6. Resolve loader differences in augmentation deliberately. NeoForge currently caps Amplify at two and exposes additional timing augments; Forge's policy differs. Document the intended common behavior or preserve versioned exceptions instead of silently changing existing spells.

**Acceptance:** Test a default ten-level spell with effective maximums 1, 5, 10, and 15; a spell originally limited to one level; a per-spell exception to a global setting; and a requested maximum beyond supported bounds. Test learning, saved books, Press output, Amplify/Dampen, JEI, reload, restart, and two clients. Also test native gear/event bonuses above the allowed scroll level so the fix does not suppress legitimate native scaling.

### B3. Resolve schools and learning from live metadata

**Confirmed:** Forge's imported school resolution and policy matching use catalog school metadata. NeoForge's imported effect stores the catalog school and derives Ars school membership at construction; its live spell record does not carry a school. Native school configuration can therefore diverge from policy, progression, presentation, or discounts. [Forge school resolver][F-school], [Forge mappings][F-mappings], [NeoForge effect][N-effect], [NeoForge behavior][N-snapshot].

1. Include the effective namespaced school and learning requirements in behavior resolution.
2. Use that same school for allow/deny rules, progression, cost modifiers, targeting colors, and displayed explanations. Keep the separately chosen mechanical school for exported Ars spells governed by its existing contract.
3. Where Ars caches school membership on registered parts, determine the supported invalidation/query hook. Do not mutate global registered objects inconsistently between server and client.
4. Gate learning and execution against the live native requirement, including a shared book held by a player who has not learned the native spell. Do not skip the live check because the catalog originally said learning was unnecessary.

**Acceptance:** Change a spell's school and learning requirement through supported upstream mechanisms; reload; verify policy, editor, Press, casting, and progression agree. Test a custom addon namespace with the same school path as a base school.

## 6. C — Native casting, events, and resource accounting

### C1. Apply native level modification exactly where intended

**Confirmed:** The inspected NeoForge glyph execution path uses the request's level and `SpellOnCastEvent`, but does not call native `getLevelFor`, which applies affinity bonuses and `ModifySpellLevelEvent`. Forge calls `getLevelFor` during admission and again during invocation, creating opportunities for repeated event evaluation and mismatched quotes. [NeoForge bridge][N-bridge], [Forge admission][F-admission], [Forge invoker][F-invoker], [upstream spell API][U-F-spell].

1. Introduce an explicit per-execution level result: base glyph level, Ars-adjusted level, native-modified level, and final cast-event level.
2. Apply native level bonuses in NeoForge. Avoid calling side-effectful modification events multiple times merely to render a preview or repeat a quote.
3. Specify what is frozen at admission and what is recomputed at delayed execution. Revalidate changes in equipment or context according to that contract, without spending twice or quietly using a different effect level from the charged level.
4. Cover the native school/affinity event path with a test listener that counts calls and modifies levels.

**Acceptance:** Native and imported execution receive equivalent intended bonuses; event count matches the documented lifecycle; cost, damage/effect, cooldown context, and client packet refer to the same committed level.

### C2. Preserve event-modified levels during NeoForge channels

**Confirmed:** `IronGlyphChannel.onCastEventCost()` returns only the modified mana cost. `runPulse()` uses `request.effectiveLevel()`, discarding `SpellOnCastEvent.getSpellLevel()`. [NeoForge channel][N-channel].

Return a structured pulse result containing final cost and level, and use it consistently for payment, native pulse execution, client feedback, and progression. Determine whether the native spell freezes its level for a channel or permits per-pulse changes, then mirror that contract. Test both first and later pulses, interruption, shortage, and the final pulse.

### C3. Make admission and final payment compatible

**Confirmed:** Imported admission initially reserves native quoted mana before the eventual `SpellOnCastEvent` adjustment. A later discount can therefore be irrelevant if admission already refused the cast. NeoForge execution recognizes an existing recast and skips its payment, while admission still quotes ordinary spell cost; test that mismatch at zero mana. Forge explicitly handles free recasts during admission. [Forge admission][F-admission], [NeoForge sessions][N-sessions], [NeoForge bridge][N-bridge].

1. Write down the imported-cast monetary contract, including the Ars parent leg, each imported occurrence, channels, recasts, and alternate resources.
2. Resolve event-modified price at a lifecycle point where its context is valid. Do not post a side-effectful cast event twice as a speculative discount probe.
3. Preserve atomic rejection for insufficient funds and do not run effects before the final charge is secured. If full atomic admission and native event timing conflict, make that design decision explicit and test the chosen behavior.
4. Ensure native free recasts are admitted without demanding the original spell price. Still charge any independently applicable Ars parent cost exactly once.
5. Reconcile reservations against final cost: refund unused reservation, obtain an additional authorized amount if permitted, or abort before effects. Refund only unspent funds after cancellation.
6. Check alternate resource integrations against the actual imported path. A mixin into native `castSpell` may not run when ANS directly invokes `onCast`; a mana-only imported quote is not proof of Covenant/Blood Magic or another addon's resource compatibility.

**Acceptance:** Test base cost 100, event cost 50, and only 60 available; an event increasing cost; zero-cost native recast; multiple imported effects; each mana mode with unequal rates; custom currency; a charge cancelled before emission; and a channel exhausting funds between pulses. Compare complete pool deltas and native resource deltas, not just a successful return value.

### C4. Audit the full native lifecycle before expanding adapters

**Confirmed structure; addon-specific consequences require testing:** Forge's instant path and NeoForge's execute path manually invoke selected native checks/events/effects instead of running the entire native initiation path. Native `onServerPreCast`, casting-state initialization, cleanup, or `canBeCastedBy` semantics must not be presumed equivalent. NeoForge channels do call `onServerPreCast`; this is not a blanket missing-hook finding. [Forge invoker][F-invoker], [NeoForge bridge][N-bridge], [NeoForge channel][N-channel].

1. Compare each adapter path with the pinned native lifecycle: eligibility, conditions, pre-event, setup, cast data, level/cost event, tick, effect, recast, cooldown, client event, completion, and cancellation.
2. Prefer supported upstream extension points. Where delegation would conflict with the Ars parent cast slot, use a well-defined child execution context with equivalent hooks.
3. Verify whether an addon reads `MagicData` passed to it or retrieves the player's real data directly. A detached child slot only isolates the former; native cast-slot delegation has different contention risks.
4. Put temporary frame/target/cast-data state behind guaranteed `finally` cleanup and reentrancy-safe scopes. Never cancel an unrelated native cast to free a slot for an imported effect.
5. Test exceptions before payment, after payment, and after an effect has begun. An exception after emission must not grant both an effect and a full refund.
6. On death, logout, dimension transfer, unload, feature disable, and session expiry, terminate owned continuations and release their unspent reservations. Check that clearing session bookkeeping also terminates the associated native child cast when one exists.

**Acceptance:** A test addon that requires setup data casts correctly; an addon reading real player state behaves as documented; native cast contention is refused safely; no child cast, reservation, temporary target, or thread-local frame survives cancellation.

## 7. D — Glyph Press correctness, reloads, and persistence

### D1. Accept genuinely zero-catalyst recipes — both loaders

**Confirmed:** Both planners allow a zero catalyst count in data but still require membership in the catalyst tag. An empty slot fails that predicate. [Forge planner][F-plan], [NeoForge planner][N-plan].

Only require a matching catalyst item when the required count is positive. Consumption, preview, JEI, and completion must also handle zero. Do not require a dummy item that is never consumed.

**Acceptance:** Zero-count recipe plus empty slot succeeds; positive count plus empty/wrong item fails without consumption; zero-count jobs survive reload and restart.

### D2. Admit configured custom catalysts — Forge

**Confirmed:** Forge's planner understands per-recipe catalyst tags, but `GlyphPressBlockEntity.accepts` calls `isCatalyst(stack, null)`, restricting insertion to the default tag. NeoForge already builds a catalyst-tag union in its behavior layer. [Forge Press][F-press], [Forge planner][F-plan], [NeoForge behavior][N-snapshot].

Make manual insertion, shift-click, and all sided item handlers accept the valid configured catalyst set. Use the same generation as the planner. Keep extraction possible for obsolete inputs after reload rather than trapping an item in an unusable slot.

**Acceptance:** A custom-tag-only item works through normal clicks, shift-click, and hoppers; simulation is non-mutating; running jobs retain their existing input locks.

### D3. Revalidate full job terms, once per relevant generation — especially Forge

**Confirmed:** Forge stores the mapping digest at job creation. After a digest change that leaves a job valid, it does not advance that stored digest, so subsequent ticks repeatedly replan. Its `stillPlanned` comparison checks cost, duration, and count but not every term; the mapping digest also does not represent every upstream live change. Completion can use old reserved/consumption terms after a live rarity change. [Forge Press][F-press].

1. Give a job an immutable plan fingerprint covering glyph identity, tier, Source cost, duration, catalyst tag/count, and relevant eligibility policy.
2. Compare against a combined behavior generation that includes live Iron's metadata and tags, not only ANS mapping JSON.
3. On a generation change, revalidate once. If terms are unchanged, acknowledge the new generation. If they changed, use the documented cancellation/refund policy; do not silently charge a new price or complete at an obsolete price.
4. Repeat a final authoritative validity check immediately before the one completion mutation.
5. Retain NeoForge's more complete term/snapshot behavior and expand tests rather than replacing it with Forge's simpler implementation.

**Acceptance:** Unrelated reload does not repeatedly replan or cancel the job. Changing catalyst tag, tier, rarity-derived cost, live maximum, enabled state, or school policy produces the documented result with exact item/Source accounting. Two viewers see the same outcome.

### D4. Wait for authoritative configuration on restored jobs

**Risk on Forge; already addressed explicitly in NeoForge:** Forge revalidates a restored job immediately against current planning data. NeoForge's recorded dedicated-server work includes waiting for Iron's configuration before completing a restored job. [Forge Press][F-press], [NeoForge testing][N-testing].

Port the readiness contract to Forge. Preserve input locks and reserved Source while configuration is pending. Separate “not ready” from “disabled” or “invalid level.” Test a server restart with no player online, followed by first login and synchronization; a temporary fallback value must not destroy a valid job.

### D5. Preserve the Source reservation invariant

**Confirmed accounting structure; exercise the external refill path:** Forge's `canAcceptSource()` considers its free buffer, not buffer plus reserved Source, and cancellation clamps the refund to capacity. Refill during a reservation or a reduced capacity can therefore discard a refund. Malformed-job recovery can clear the job without recovering a known reserved amount. [Forge Press][F-press].

1. Model free and reserved Source separately with an explicit total-capacity invariant. Reject or limit incoming transfers based on total held Source where the integration permits them.
2. Define capacity-reduction behavior that preserves already held value without allowing it to be spent twice; for example, retain a bounded over-capacity refund balance until drained.
3. Persist a versioned reservation ledger. Recover only validated, known amounts on malformed/old job data; never mint Source from arbitrary corrupt fields.
4. Verify normal cancellation, changed mappings, destruction, explosion, chunk unload, save/reload, and repeated load all release or consume a reservation exactly once.
5. Respect the actual Ars Source API on each loader. Forge's jar polling and NeoForge's capability/relay integration are not interchangeable. Add relay support to Forge only through an appropriate supported integration and tests.

**Acceptance:** Inventory and Source conservation hold across refill-then-cancel, capacity reduction, invalid saved job, repeated reload, full output, and block removal. Existing idle-Press no-polling behavior remains intact.

## 8. E — Repair uninscription and preserve carrier contents

**Confirmed classification path:** Both `InscriptionInputs.classify` implementations attempt `readSource` before `isInscribed`. A bound native book can be recognized as an Iron's source first, including an ANS proxy in a native slot, and never reach the uninscription bucket. Forge's native reader also lacks NeoForge's empty-container guard. This is a concrete candidate explanation for [issue #14][issue-14]; the issue itself does not identify the release used. [Forge classification][F-inputs], [NeoForge classification][N-inputs], [Forge reader][F-reader], [NeoForge reader][N-reader].

1. Separate classification by operation, or represent both capabilities explicitly. For uninscription, identify a valid ANS sidecar/proxy binding before treating the carrier as a source. Do not blindly reverse global precedence and break transcription.
2. Reject empty/native `none` slots and ANS proxy spells as ordinary native source spells. Define source selection for multi-slot books instead of always taking slot zero.
3. Remove the intended ANS inscriptions and proxy slots while preserving native spell slots, book capacity, enchantments, custom name, components/NBT, and unrelated addon state.
4. Preserve unknown future-format data rather than erasing it during a failed decode. Provide a diagnostic explaining why removal cannot safely proceed.
5. Keep recovery functional when optional Iron's classes are absent, to the extent that the surviving carrier and saved data can still be represented.

**Acceptance:** Test the actual brazier ritual with a real Iron's book bound through the normal workflow, including mixed native/Ars slots, multiple inscriptions, unrelated dropped items, and different item orders. Assert exact before/after contents and one-time ritual consumption. A direct helper test alone will not catch classification failures.

## 9. F — Reloads, schema parity, and client consistency

### F1. Converge mapping semantics without breaking existing packs

**Confirmed:** The loaders use different mapping forms and conflict behavior despite a similar directory/schema surface. Forge supports namespaced policy/press-tier document types; NeoForge supports a different policy form and per-spell defaults/enablement. Their adapter and duplicate-resolution behavior also differs. [Forge mappings][F-mappings], [NeoForge mappings][N-mappings].

1. Publish a canonical, versioned schema with clear defaults, tier terms, per-spell/per-level overrides, adapter restrictions, allow/deny precedence, and duplicate handling.
2. Accept legacy forms through explicit migrations. Diagnose unsupported fields; do not silently ignore a pack author's intended restriction.
3. Honor resource-pack priority deterministically. Distinguish a higher-priority override from conflicting duplicate declarations within one effective priority.
4. Build one validated candidate snapshot and publish it atomically. Invalid reloads should retain the last valid behavior snapshot with actionable errors, rather than expose partially applied policy.
5. Include source pack/file/field in errors and offer a dry-run validation command. Expand existing pack tools to validate the canonical schema on both loaders.

### F2. Validate numbers before narrowing — Forge

**Confirmed:** Several Forge mapping fields call `getAsInt()` before checking finiteness. An integer is already finite, and fractional input may already have been truncated. NeoForge contains stricter bounded-number handling. [Forge mappings][F-mappings].

Validate numeric type, finiteness, integrality, and bounds before conversion. Cover negative, fractional, huge, wrong-type, and malformed values; check sums/products before allocation or arithmetic. Report exact field locations. Preserve intentionally allowed zero values such as catalyst count.

### F3. Make every client consumer use the same accepted generation

1. Synchronize live enabled state, maximum, school, rarity/tier, recipe terms, and dormancy reason needed by clients. Include a catalog identity hash and behavior generation.
2. Clear server-specific caches on disconnect and server switch. Do not show one server's restrictions on another.
3. Require UI actions to be checked against authoritative server state. Preserve existing menu session/revision/fingerprint checks; add catalog/generation validation only where it resolves a real stale-action ambiguity.
4. Refresh open editor validity, tooltips, Press preview, and JEI after accepted snapshot changes. Distinguish pending sync from an unavailable spell.
5. Keep packet decode bounds for counts, strings, levels, and aggregate bytes. A count bound alone does not establish a safe total payload. NeoForge's ordinal-based variant encoding additionally needs exact catalog agreement before applying positions. [Forge sync][F-sync], [NeoForge behavior codec][N-snapshot].

**Confirmed Forge gap:** JEI recipes are initially built from mappings but are not refreshed by the mapping-publication path. NeoForge already has a runtime refresh mechanism. Port that capability, and profile repeated NeoForge reloads before asserting whether hide/add behavior retains obsolete recipe objects. [Forge JEI][F-jei], [NeoForge JEI recipes][N-jei].

**Acceptance:** With two clients and JEI open, change a tier/catalyst/max level and reload. All consumers converge, stale actions are refused cleanly, and repeated reloads do not accumulate duplicate visible recipes or unbounded retained recipe data.

## 10. G — Compatibility and whole-mod gameplay contracts

These are targeted integration tasks, not claims that all existing integrations are broken.

### G1. Keep optional integrations isolated and version-aware

1. Retain common-code bridges that do not classload Iron's, JEI, or optional addons when absent. Test both dedicated-server and client distributions.
2. Record the actual tested dependency ranges. A broad metadata range is not evidence that every intervening native API revision works.
3. Exercise required mixin targets against each supported dependency profile. A missing critical injection should fail clearly; making every injection optional can hide lost payment or targeting behavior.
4. Prefer stable public APIs over reflection and method-internal injection. Where a mixin is necessary, minimize its scope and document the contract it preserves.
5. Keep compatibility work for an old reported stack separate from a verified 3.4.0 regression. Investigate [issue #12][issue-12] with its exact versions and log; its linked gist was not retrievable during this review. [Issue #11][issue-11] also needs a versioned reproduction.

### G2. Preserve native protection, ownership, and targeting semantics

For each adapter family, test friendly fire, PvP, teams, invulnerability, owner assignment, claim/protection cancellation where integrated, reach, line of sight, unloaded destinations, collision-safe teleporting, and permission to modify blocks. Reuse native checks when possible. Do not teleport the real caster temporarily just to make a native spell appear to originate at an Ars hit point.

For projectile-delivered effects, confirm the intended origin, direction, hit entity, and block face after delays. Test target disappearance and dimension changes between admission and impact. A visually plausible projectile is not sufficient proof of equivalent native targeting.

### G3. Exercise cross-casting in both directions

Build a route matrix for native Ars books, native Iron's books, exported scrolls, generic carriers, staff, wheel, hotkey, offhand, and nested imported glyphs inside exported Ars recipes. Test Self, Touch, Projectile, delayed execution, and chained effects. Preserve explicit refusal for unsupported turret/rune/fake-player/reactive contexts until a separate design supports them safely.

Retain exact sidecar serialization, slot limits, duplicate prevention, selected school, cosmetic metadata, and identity across anvil/crafting operations where those operations are supported. Malformed, excessively large, or future-version spell data should be refused without destructive rewriting.

### G4. Verify mana, equipment, and progression as one system

Use table-driven scenarios across all five modes and unequal conversion rates. Cover armor/Curios changes, modifier replacement, relog, death/clone, dimension transfer, config reload, native affinity bonuses, knowledge bonuses, and progression caps. Assert no repeated additive growth, feedback loop, duplicate regeneration, double payment, or training on a refused cast.

Preserve explicit separation between maximum mana and current mana during capacity changes. Use validated finite values and overflow-safe arithmetic at external boundaries. Earlier mana/armor issues are useful regression inputs, not proof that the current implementation still has their original defects.

## 11. H — Optimization with measurable budgets

Optimize the added-catalog scale after correcting lifecycle and accounting. Record hardware, JVM, dependency set, world, player count, catalog size, and warm-up for every measurement. The repository's idle-Press and NeoForge editor measurements are useful baselines, not universal performance guarantees.

### H1. Bound snapshot construction and cache immutable data

**Confirmed NeoForge hot paths:** `GlyphBehavior.current()` constructs an unsynced snapshot when none exists; repeated pre-sync queries can repeat whole-catalog work. `IronGlyphBridgeImpl.liveSpell()` allocates a rarity list using the effective maximum and enumerates all levels, even though the registered catalog is bounded. `ImportedGlyphCatalog.keys()` constructs/sorts a list on demand. [NeoForge behavior manager][N-behavior], [NeoForge bridge][N-bridge], [NeoForge catalog][N-catalog].

1. Cache one immutable unsynced snapshot per catalog identity.
2. Query rarity only for registered/relevant levels. Store the scalar live maximum separately; never allocate an array proportional to an untrusted or extreme configured maximum.
3. Cache sorted keys and static lookup maps once. Cache per-generation values only when their invalidation source is defined.
4. Avoid rebuilding an unchanged full snapshot for every player login. Reuse the current snapshot and transmit only what the joining player needs; use digest equality to avoid redundant reload work.
5. Coalesce related reload notifications. Do not perform filesystem scans or registry inventory generation in render, tooltip, or per-tick paths.

**Acceptance:** Before-first-sync editor/tooltip queries allocate bounded data; a very large configured maximum does not cause proportional allocation or long loops; repeated joins and unchanged reloads do not reconstruct the same catalog unnecessarily.

### H2. Reduce session scans and bound pending work

**Confirmed:** Forge keeps sessions until its lifetime bound and repeatedly scans a global map to obtain a player's sessions. NeoForge already has eager completion and a pending-spell index, though some owner enumeration remains. [Forge sessions][F-sessions], [NeoForge sessions][N-sessions].

1. Index active sessions by owner and pending native spell where measurements justify it. Reuse NeoForge's completion concept on Forge, preserving delayed/projectile continuations.
2. Remove completed sessions promptly once all tracked work is done. A timeout should be a safety bound, not the normal cleanup path for instant casts.
3. Use bounded expiry processing or an appropriate expiry queue when session volume warrants it. Avoid copying/scanning every session for every glyph occurrence.
4. Add limits and diagnostics for outstanding continuations per player. Ensure limits refuse new work before reserving funds or generating effects.
5. Test long-lived addon charges/channels against the documented session bounds. Make expiry cancel owned work consistently; do not only forget the association.

**Acceptance:** Repeated instant casts return session/reservation counts to baseline; projectile and delayed casts stay alive only as long as necessary; a disconnected player leaves no live resources; stress cost scales with actual pending work.

### H3. Scale the Press, UI, assets, and network

- Retain zero Source polling for idle Presses. Cache plans against inventory and behavior revisions; do not hash/serialize full item data or search nearby Source providers every idle tick.
- Measure **active** Presses waiting for Source and blocked output, not just idle tick calls. Bound source scans and consider backoff after a failed search without making newly available Source feel unresponsive.
- For large catalogs, measure editor opening, search, scrolling, tooltips, creative tabs, JEI registration, login payload, and resource reload. Group/filter dormant variants and provide useful school/mod search where Ars APIs allow it.
- Reuse shared textures and model structures for level variants where supported; keep registry IDs distinct. Avoid generating thousands of duplicate bitmap assets merely to encode a level that can be rendered as text.
- Send revisions/deltas only when state changes where the protocol benefits. Separate dynamic progress updates from static recipe metadata. Preserve current split-field handling for quantities that exceed vanilla menu short widths.
- Track retained memory after repeated reloads, world switches, and disconnects. Profile caches before adding weak references or object pooling; make ownership and clearing explicit.
- Throttle repeated refusal/compatibility logs by reason and relevant identity. Keep full diagnostic detail available on demand without logging per tick.

Use spark/JFR or equivalent profiling in representative worlds. Set project-specific budgets from measured baselines; do not invent a universal acceptable tick time or claim an improvement from a microbenchmark alone.

## 12. I — Diagnostics, UX, and documentation

Extend existing glyph and audit commands rather than introducing a parallel diagnostic subsystem.

1. **Registry-to-catalog report:** list native spell ID, provider/version, catalog source, registered level range, live maximum, adapter, and support status. Show the missing spell/level directly when conversion fails.
2. **Effective configuration explanation:** show catalog default, effective native value, ANS override, resulting tier/cost, readiness state, and behavior generation. Only claim the precise upstream source file when the native API actually exposes it; otherwise label the value as effective, with precedence documented separately.
3. **Cast explanation:** report base/augmented/native/event levels, target family, cooldown/recast state, payment legs, and refusal stage. Redact irrelevant player data from shareable reports and keep collection bounded.
4. **Actionable Press states:** distinguish missing addon catalog, unsupported adapter, disabled native spell, above-live-max level, above-registered-envelope level, not learned, config pending, missing catalyst, insufficient Source, and blocked output. Do not collapse them into a generic “no adapter” message.
5. **Accessible presentation:** verify keyboard navigation, narration/focus, readable disabled-action explanations, small windows, GUI scales, long localized names, and resource-pack icons. Extend existing scripted GUI work; NeoForge has already tested several small-scale and two-client scenarios.
6. **Coverage documentation:** publish exact supported loader/native/addon versions and tested routes. Include examples for global maximum changes and catalog expansion, custom catalysts, behavior reloads, and removing an addon while retaining glyph identities.
7. **Documentation maintenance:** reconcile adapter-retirement guidance with actual registration behavior; distinguish historical test claims from current release evidence; remove stale instructions for UI/features superseded in later 3.x releases.
8. **Issue triage:** prioritize #14 with the real ritual regression. Reproduce #11/#12 against supplied environments. Review older mana/armor issues against their original versions and completed audit work before deciding whether a fix or documentation response is needed. Do not close an issue solely because a newer release exists.

## 13. J — Verification and release instructions

### J1. Add tests that demonstrate behavior, not implementation shape

Use unit tests for parsing, immutable policy calculation, identity encoding, numeric bounds, and ledger invariants. Use GameTests for native registries, effects, resources, blocks, rituals, and actual item behavior. Use real client/server checks for synchronization, editor, JEI, menus, restart readiness, and multiple viewers.

| Test group | Minimum cases | Required observation |
| --- | --- | --- |
| Global/per-spell limits | Lower, unchanged, raised within envelope, raised beyond envelope, per-spell exception | Exact conversion level; correct dormancy/augments; no silent clamp |
| Native level events | Affinity gear and modifying listener | Correct committed level and intended event count |
| Channels/recasts | Event-adjusted pulse, first/last pulse, interruption, zero-mana recast | Matching effect/payment level; exact resource/cooldown behavior |
| Addon inventory | Base and multiple addon namespaces; duplicate paths; custom schools | Every spell accounted for; no ID collision |
| Identity survival | Disable/re-enable; retire adapter; addon removal with retained catalog; old save | Same IDs and saved recipes; dormant execution refused |
| Press terms | Custom tag, zero catalyst, changed tier/tag/count/price, full output | Exact conservation and consistent UI/JEI |
| Press persistence | Restart without players; first login; corrupt/old job; capacity change | No premature invalidation, duplication, or lost validated reservation |
| Rituals | Real bound book; mixed slots; stray items; future data | Correct classification and preservation of unrelated contents |
| Casting routes | Both directions; nested effects; Self/Touch/Projectile; supported carriers | Correct target, one payment, one appropriate progression grant |
| Optional dependencies | Iron's absent/present; JEI absent/present; supported addons | No accidental classloading or missing critical hook |
| Multiplayer | Two clients, stale packet, repeated action, disconnect/switch | Server authority and consistent snapshot generation |
| Scale | Base catalog and large bounded addon catalogs; many pending casts/Presses | Bounded work, memory, payloads, and cleanup |

For native spells, expand beyond one easy example per family. Test at least minimum/maximum supported base levels, learning-restricted spells, nontrivial cast data, and spells with special recast or cancellation behavior. Keep registry coverage and actual execution coverage as different metrics.

### J2. Make the existing test evidence reproducible

The repo records substantial historical coverage. Forge's release record includes 267 required GameTests and later JUnit updates; NeoForge's record includes 407 required GameTests plus broader runtime/client evidence. These counts describe those recorded runs, not tests rerun for this report. Read the complete versioned testing files, including later amendments, rather than copying the first count encountered. [Forge evidence][F-testing], [NeoForge evidence][N-testing].

Run in each appropriate checkout/toolchain:

```bash
python3 -m unittest discover -s tools -p 'test_*.py'
./gradlew test
./gradlew build
./gradlew runGameTestServer -PgametestRunDir=run-audit-absent
./gradlew runGameTestServer -PwithIronsRuntimeGameTests -PgametestRunDir=run-audit-irons
```

Additionally on Forge:

```bash
./gradlew runGameTestServer -PwithIrons316RuntimeGameTests -PgametestRunDir=run-audit-irons316
```

These are implementation-stage commands, not a claim of execution here. Provision the pinned dependencies first; use `--offline` only if dependencies are already cached. Follow each branch's build profile for optional addons, JEI, and scripted client/server runs. Do not depend on a maintainer's private absolute wrapper path.

1. Use a separate disposable run directory per incompatible mod profile. Reusing a world with missing dimensions can make a test startup fail misleadingly.
2. Feed logs to the existing `tools/verify_gametest_log.py`, with expected required/executed/skipped counts and expected present/absent mods for that profile. A zero process exit or `BUILD SUCCESSFUL` alone is insufficient, and all optional tests skipping is not coverage.
3. Fix the documented flaky ritual fixtures by placing drops in valid positions with deterministic range checks. Synchronize config tests with actual application of the config instead of relying on asynchronous timing or rerunning until green.
4. Extend loader-parity tooling with shared behavioral fixtures for catalogs, schemas, effective levels, policies, and plan terms. Review intentional platform exceptions separately from source-level differences.
5. Test packaged production JARs, not just development runs. Check dependency declarations, mixin application, dedicated-server classloading, packaged manifests/resources, and client synchronization.
6. Save release evidence with commit, Java/loader/mod versions, profile, test counts, skip reasons, and reproducible commands. Keep performance results separate from pass/fail correctness evidence.

### J3. Release gates

- [ ] Existing 3.4.0 identities are unchanged; newly retired identities remain registered and dormant.
- [ ] Global/per-spell configuration tests pass across all glyph consumers on both loaders.
- [ ] Every supported addon spell has an explicit coverage status and compatible dependency profile.
- [ ] Native level, cast-event, recast, channel, payment, and cancellation contracts are verified.
- [ ] Zero/custom catalysts, reloads, restart readiness, and Source conservation pass.
- [ ] Real-book uninscription passes without losing unrelated native/addon data.
- [ ] Existing Touch, school-choice, mana-capacity, equipment, progression, and carrier regressions remain green.
- [ ] Two clients agree after reload; JEI/editor/Press show the same accepted behavior.
- [ ] Required runtime profiles actually execute their optional scenarios.
- [ ] Large-catalog and repeated-reload measurements show bounded work and memory.
- [ ] Migration notes explain catalog expansion, restart requirements, and addon removal.

## 14. K — Optional improvements after correctness

Consider these only after the preceding contracts are stable:

- **Advanced automation:** an opt-in batch queue or redstone trigger, with explicit per-job authorization/learning rules, output backpressure, fair Source use, and no accidental free conversion. Preserve current player-start behavior by default.
- **Configurable recipe integration:** a public recipe/adapter API for pack tools, scripting mods, and recipe viewers; document which changes require a restart versus a reload.
- **Supported non-player casters:** design a separate capability/resource/ownership model for turrets, runes, and fake players. Removing the current guard without that design is not support.
- **Catalog usability:** filters by addon, school, level, learned state, and availability; compact level grouping in supported editor surfaces; a preview explaining what an imported glyph will target and cost.
- **Pack migration tooling:** validate manifests and old saved identities, emit a compatibility report, and generate additive catalog updates. Never rewrite a player's learned spells silently.

## 15. Suggested delivery sequence

1. **Correctness patch:** zero catalysts, Forge custom catalysts, uninscription classification, NeoForge channel event levels, and targeted regression tests.
2. **Configuration patch:** effective metadata snapshot, lowered-limit/augment handling, native level bonuses, payment/recast alignment, Forge readiness/reload/JEI corrections.
3. **Identity and addon release:** stable catalog extension contract, tombstones, bounded level envelopes, catalog mismatch checks, tested addon adapters, and migration documentation.
4. **Performance and polish:** measure larger catalogs and active workloads, then implement the justified caching/indexing/UI changes and publish comparable results.

Keep each change reviewable, retain the baseline regressions, and describe unsupported cases accurately. The release is complete when the same spell has a consistent identity, eligibility, price, level, and effect across conversion, learning, composition, execution, reload, and restart.

## Source index

The links used throughout this document point to pinned source files where possible. Public issues are leads, not verified reproductions. Upstream links identify the exact API source inspected; they are not a claim that the upstream repository's default branch matches every supported artifact.

| Source group | Entry points |
| --- | --- |
| ANS Forge 3.4.0 | [Pinned tree](https://github.com/otectus/ars-n-spells/tree/0da9de067fac60db06f18ba407b266261c65db47), [testing record][F-testing], [catalog][F-catalog], [Press][F-press] |
| ANS NeoForge 3.4.0 | [Pinned tree](https://github.com/otectus/ars-n-spells/tree/c234f19145d4380a849597f7e390fd535cc45e4f), [testing record][N-testing], [catalog][N-catalog], [behavior snapshot][N-snapshot] |
| Upstream Iron's API | [Forge spell][U-F-spell] and [config manager][U-F-config]; [NeoForge spell][U-N-spell] and [config manager][U-N-config] |
| Public issue leads | [Uninscription #14][issue-14], [Loom/book crash #11][issue-11], [mixin crash #12][issue-12] |
| Candidate addon sources | [Cataclysm Forge][addon-cataclysm], [Cataclysm 1.21.1][addon-cataclysm-neo], [Alshanex's Familiars][addon-familiars] |

[F-admission]: https://github.com/otectus/ars-n-spells/blob/0da9de067fac60db06f18ba407b266261c65db47/src/main/java/com/otectus/arsnspells/glyph/GlyphAdmission.java
[F-audit]: https://github.com/otectus/ars-n-spells/blob/0da9de067fac60db06f18ba407b266261c65db47/docs/3.3.0-audit-status.md
[F-catalog]: https://github.com/otectus/ars-n-spells/blob/0da9de067fac60db06f18ba407b266261c65db47/src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphCatalog.java
[F-effect]: https://github.com/otectus/ars-n-spells/blob/0da9de067fac60db06f18ba407b266261c65db47/src/main/java/com/otectus/arsnspells/glyph/ImportedIronEffect.java
[F-inputs]: https://github.com/otectus/ars-n-spells/blob/0da9de067fac60db06f18ba407b266261c65db47/src/main/java/com/otectus/arsnspells/rituals/InscriptionInputs.java
[F-invoker]: https://github.com/otectus/ars-n-spells/blob/0da9de067fac60db06f18ba407b266261c65db47/src/main/java/com/otectus/arsnspells/compat/irons/glyph/IronsGlyphInvoker.java
[F-jei]: https://github.com/otectus/ars-n-spells/blob/0da9de067fac60db06f18ba407b266261c65db47/src/main/java/com/otectus/arsnspells/compat/jei/ArsNSpellsJeiPlugin.java
[F-key]: https://github.com/otectus/ars-n-spells/blob/0da9de067fac60db06f18ba407b266261c65db47/src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphKey.java
[F-mappings]: https://github.com/otectus/ars-n-spells/blob/0da9de067fac60db06f18ba407b266261c65db47/src/main/java/com/otectus/arsnspells/glyph/recipe/GlyphMappingSnapshot.java
[F-plan]: https://github.com/otectus/ars-n-spells/blob/0da9de067fac60db06f18ba407b266261c65db47/src/main/java/com/otectus/arsnspells/glyph/recipe/GlyphPressPlanner.java
[F-press]: https://github.com/otectus/ars-n-spells/blob/0da9de067fac60db06f18ba407b266261c65db47/src/main/java/com/otectus/arsnspells/block/GlyphPressBlockEntity.java
[F-reader]: https://github.com/otectus/ars-n-spells/blob/0da9de067fac60db06f18ba407b266261c65db47/src/main/java/com/otectus/arsnspells/rituals/IronsInscriptionReader.java
[F-school]: https://github.com/otectus/ars-n-spells/blob/0da9de067fac60db06f18ba407b266261c65db47/src/main/java/com/otectus/arsnspells/util/SchoolResolver.java
[F-sessions]: https://github.com/otectus/ars-n-spells/blob/0da9de067fac60db06f18ba407b266261c65db47/src/main/java/com/otectus/arsnspells/glyph/GlyphSessions.java
[F-sync]: https://github.com/otectus/ars-n-spells/blob/0da9de067fac60db06f18ba407b266261c65db47/src/main/java/com/otectus/arsnspells/network/GlyphMappingSyncPacket.java
[F-testing]: https://github.com/otectus/ars-n-spells/blob/0da9de067fac60db06f18ba407b266261c65db47/docs/3.4.0/testing.md
[N-behavior]: https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/src/main/java/com/otectus/arsnspells/glyph/behavior/GlyphBehavior.java
[N-bridge]: https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/src/main/java/com/otectus/arsnspells/compat/irons/glyph/IronGlyphBridgeImpl.java
[N-catalog]: https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/src/main/java/com/otectus/arsnspells/glyph/catalog/ImportedGlyphCatalog.java
[N-channel]: https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/src/main/java/com/otectus/arsnspells/compat/irons/glyph/IronGlyphChannel.java
[N-effect]: https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/src/main/java/com/otectus/arsnspells/glyph/ImportedIronEffect.java
[N-inputs]: https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/src/main/java/com/otectus/arsnspells/rituals/InscriptionInputs.java
[N-jei]: https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/src/main/java/com/otectus/arsnspells/glyph/recipe/GlyphPressRecipes.java
[N-key]: https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphKey.java
[N-manifests]: https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/src/main/java/com/otectus/arsnspells/glyph/catalog/CatalogManifests.java
[N-mappings]: https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/src/main/java/com/otectus/arsnspells/glyph/behavior/GlyphMappings.java
[N-plan]: https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/src/main/java/com/otectus/arsnspells/glyph/recipe/GlyphPressPlan.java
[N-reader]: https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/src/main/java/com/otectus/arsnspells/rituals/IronsInscriptionReader.java
[N-sessions]: https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/src/main/java/com/otectus/arsnspells/glyph/runtime/GlyphSessions.java
[N-snapshot]: https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/src/main/java/com/otectus/arsnspells/glyph/behavior/GlyphBehaviorSnapshot.java
[N-testing]: https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/docs/3.4.0/testing.md
[U-F-config]: https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/cae63a6999e24ed3deaa012ecb8c262e0e377816/src/main/java/io/redspace/ironsspellbooks/api/config/SpellConfigManager.java
[U-F-spell]: https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/cae63a6999e24ed3deaa012ecb8c262e0e377816/src/main/java/io/redspace/ironsspellbooks/api/spells/AbstractSpell.java
[U-N-config]: https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/532c98dfa5b219f3927aca9b23d9042c00e767bb/src/main/java/io/redspace/ironsspellbooks/api/config/SpellConfigManager.java
[U-N-spell]: https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/532c98dfa5b219f3927aca9b23d9042c00e767bb/src/main/java/io/redspace/ironsspellbooks/api/spells/AbstractSpell.java
[addon-cataclysm]: https://github.com/AceTheEldritchKing/Cataclysm_Spellbooks
[addon-cataclysm-neo]: https://github.com/AceTheEldritchKing/Cataclysm_Spellbooks_1.21.1
[addon-familiars]: https://www.curseforge.com/minecraft/mc-mods/alshanexs-familiars
[issue-11]: https://github.com/otectus/ars-n-spells/issues/11
[issue-12]: https://github.com/otectus/ars-n-spells/issues/12
[issue-14]: https://github.com/otectus/ars-n-spells/issues/14
