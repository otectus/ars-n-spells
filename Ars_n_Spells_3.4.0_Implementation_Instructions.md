# Ars ’n Spells 3.4.0 — Touch Casting Repair and the Glyph Press

**Coding-agent implementation brief**  
**Repository:** https://github.com/otectus/ars-n-spells  
**Primary release target:** Minecraft 1.21.1, NeoForge, Java 21  
**Prepared:** October 20, 2026

## 1. Mission and definition of the update

Implement a substantial 3.4.0 release with two connected outcomes:

1. Repair the reported failure when an Ars spell containing **Touch** is carried on a scroll. Fix the underlying interaction and resolver dispatch so the same spell also behaves correctly through supported inscribed items, Iron’s spellbooks, and casting controls.
2. Introduce the **Glyph Press**, a new survival workstation that converts a genuine Iron’s Spells ’n Spellbooks scroll into a **real, learnable Ars Nouveau effect glyph** representing that spell at that scroll’s level.

The intended progression is:

**Find or craft an Iron’s scroll → press its magic into a glyph → learn the glyph → compose it with Ars forms, compatible effects, and augments.**

The Spell Loom continues to export complete Ars spells into Iron’s carriers. The Glyph Press imports individual Iron’s spells into Ars composition. Together they make the integration genuinely bidirectional.

Do the implementation, assets, configuration, documentation, migrations, and verification. Do not stop at a design, an instant-cast proof of concept, a renamed scroll, or a glyph item that cannot actually be learned and selected in the Ars spell editor. Do not publish a release or claim runtime acceptance from compilation alone.

### Scope decisions

- NeoForge 1.21.1 is the required implementation and acceptance target because it is the platform in the report.
- Work from the current `port/neoforge-1.21.1` branch or its established successor after checking repository instructions. The default `main` branch is the Forge 1.20.1 line; do not accidentally implement this solely there.
- Keep reusable contracts suitable for the Forge line, but do not merge NeoForge APIs into it or claim a tested Forge 3.4.0 port. If the actual checkout already specifies a dual-loader release, implement and validate each separately and record parity.
- Preserve existing mana modes, Spell Loom functions, native Iron’s casting, inscriptions, school mappings, and optional-Iron’s behavior.
- Conversion should cover the pinned base Iron’s catalog’s ordinary player scroll spells. Classify every spell. Addon spells need explicit compatible adapters or a truthful unsupported result, not a blanket compatibility claim.
- New names, classes, schemas, tuning values, and IDs below are **proposed implementation contracts**, not claims that those APIs already exist.

## 2. Source baseline and what the inspection established

The reviewed NeoForge commit was `087ed3d172bfe2e0c8351d25debc7737b2be118f`. Its `gradle.properties` declares:

| Component | Reviewed pin |
| --- | --- |
| Ars ’n Spells | `3.3.6` |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.248` |
| Java toolchain | `21` |
| Ars Nouveau | `5.13.1.1400` |
| Iron’s Spells | `1.21.1-3.16.3` |

Re-read these files in the implementation checkout. Preserve any fixes made after the reviewed commit, including mana and staff-casting fixes. Do not downgrade dependencies or remove intervening changes to match this document. Reconcile the README’s dependency statements with the actual metadata; the reviewed README still admits an older Iron’s floor than `gradle.properties` does. [S1]

### Source-backed findings

| Area | Observed behavior | Consequence for this work |
| --- | --- | --- |
| Cross-cast dispatch | `CrossCastingHandler.castArsSpell` builds a `SpellResolver` and invokes `resolver.onCast(stack, level)` without a block/entity dispatch. Its item handler listens to `RightClickItem`. | Audit both how interaction intent arrives and which resolver method executes. [S2] |
| Request contents | `CrossCastRequestPayload` carries hand, action, index, attempt ID, and carrier fingerprint; no target or interaction context. | A target-aware path needs validated interaction context or a server-computed pick, with duplicate prevention. [S3] |
| Ars Touch | In the upstream 5.13.1 version commit, `MethodTouch.onCast` returns failure; its block/entity entry points resolve the effect. | This is a strong source-level explanation for the complaint, not a completed in-game reproduction. [S4] |
| Resolver behavior | Ars exposes separate generic, block, and entity entry points, each wrapping validation, events, resolution, and expenditure. | Dispatch through the appropriate resolver API rather than calling effects directly. [S5] |
| Spell identity | Ars serializes each spell part by its resource ID. Unknown IDs can fall back to `EffectBreak`. | A level stored only on the physical glyph item will not survive normal spell composition. Keep identities stable and prevent dormant imports from changing meaning. [S6] |
| Existing casting infrastructure | ANS has `QuoteService`, `CostRules`, `CastLedger`, `ArsCastPayments`, and `IronsCastLifecycle`. | Extend these systems with explicit child-cast contracts; do not add unrelated mana subtraction handlers. [S7] |
| Existing workstation | The Loom has a persistent block inventory, directional automation, menu, planning/apply logic, and requests bound to session/revision state. | Reuse its patterns and visual conventions while keeping the new conversion operation separate. [S8] |

The upstream Ars source inspected at `112920ff774831f204031da75b4c4e73d3765157` is its 5.13.1 version commit. It is not proof that every byte matches the `.1400` Maven artifact. Verify the actual resolved dependency source or bytecode before choosing mixin descriptors. Iron’s source at `e4056af90302d37eb1739f5ff05020b020e6e252` declares 1.21.1-3.16.3. [S9]

No game client, server, or mod build was executed while preparing this brief. The implementation agent must produce the reproduction and verification evidence.

## 3. Phase A — reproduce and properly repair Touch

### 3.1 Establish the actual failing carrier

The report does not identify the scroll’s internal representation. Exercise these independently:

1. Native Ars spellbook/appropriate native Ars carrier, as a control.
2. A real Iron’s scroll exported by the Spell Loom with an Ars sidecar.
3. The equivalent generic ANS-inscribed item.
4. The same exported spell bound into an Iron’s spellbook and selected in its native wheel.
5. Supported right-click, hotkey, staff, main-hand, and Curios-book entry points.

Use `Touch → Break` against a breakable block and `Touch → Harm` against a valid living target. Add `Self → Heal` and `Projectile → Harm` controls. Then exercise Touch with Sensitive against air/fluids using the resolved Ars version’s native behavior.

Record exact mod versions, mana mode, selected spell, carrier components, input route, target, actual result, resource deltas, and cooldown deltas. Use the existing trace/diagnostic infrastructure with bounded output. Distinguish “no request,” “request refused,” “wrong resolver entry,” “event veto,” and “effect had no applicable target.”

Add a failing integration test for the reproduced defect before repairing it. A test that directly invokes a Touch effect with a fabricated hit does not verify scroll interaction routing.

### 3.2 Introduce one target-aware Ars dispatch boundary

Create a small service, suggested name `ArsCastDispatcher`, used by generic carriers and `ArsCrossProxySpell`.

Its inputs should describe the actual caster, caster tool, hand, validated Ars spell/context, attempt identity, and interaction origin. Use an immutable target description with `AIR`, `BLOCK`, or `ENTITY` semantics, dimension, position/hit face where relevant, and a bounded entity reference. Distinguish direct block/entity interaction from a wheel/hotkey cast.

The server is authoritative:

- For direct interaction, retain native hit semantics, then validate the claimed block/entity, hand, reach, line of sight, world border, world/dimension, and loaded state.
- For wheel/hotkey casting, compute the target on the server using the actual Ars casting/reach rules. Match entity-versus-block occlusion and Sensitive’s behavior. Do not give Touch a projectile’s range or a hard-coded extended reach.
- Client aim hints may assist a bounded reconstruction; they are never authority to hit an arbitrary entity or coordinate. A stale hint fails or triggers a fresh validated pick; it never becomes an unchecked hit.
- Use native permission/protection/event checks. Do not bypass adventure restrictions, invulnerability, spectator restrictions, friendly-fire rules, or protection integrations by switching to direct damage/block changes.
- A genuine miss returns an appropriate failure with no successful-cast reward or cooldown. Sensitive’s legitimate native air-target behavior is not a miss workaround.

Dispatch the selected cast form correctly:

| Cast semantics | Required behavior |
| --- | --- |
| Touch with valid block target | Use the suitable `SpellResolver.onCastOnBlock` overload; retain hand/context where needed. |
| Touch with valid entity target | Use `SpellResolver.onCastOnEntity` with the real caster/tool/hand. |
| Touch without a native-valid target | Refuse cleanly and release any uncommitted reservation. |
| Self, Projectile, and other forms | Preserve that form’s native dispatch and semantics; do not force them into Touch’s path. |
| Addon form requiring special caster context | Follow the existing compatibility/blacklist contract and explain rejection. |

Inspect native spellbook and cast-method behavior before generalizing. Do not switch every spell to block casting merely because the player is looking at a block. Do not call `onResolveEffect` directly as the bug fix: that can bypass normal admission, cost, cancellation, and form logic.

### 3.3 Cover all interaction events without double casting

Audit `RightClickItem`, block use, entity interaction, and entity-interact-at handling on NeoForge 1.21.1. A right-click against a block or entity may not reach the air-use event at all.

Choose a single execution authority. Client events may emit a validated intent and suppress the conflicting local prediction; native server callbacks must not independently cast the same action. Reuse `NetworkRequestGuard` and carrier fingerprints, and add the necessary target/input discriminator. Give the entity and entity-interact-at pair a deliberate deduplication policy.

Preserve native Iron’s spellbook routing. Its wheel/staff paths delegate to the dispatcher through their resolved carrier; they must not also be hijacked by the generic item hook. Preserve the distinction between the physical casting tool and the book that owns the sidecar.

Specify interaction precedence deliberately: a handled inscribed carrier should follow the native Ars casting convention for target interaction; ordinary uninscribed items retain their normal behavior. Cycling inscriptions must not simultaneously cast or open a block menu. Keep the existing supported-hand policy unless expanded intentionally and tested; unsupported offhand use must never produce a second cast.

Do not change consumability as part of this repair. Establish the current contract for each carrier: a reusable ANS carrier remains reusable, and a native consumable Iron’s scroll still follows its native consumption rule. **The Glyph Press consumes its input scroll during conversion; that is a separate operation.**

### 3.4 Resource and result correctness

Route through the existing `ArsCastPayments` lifecycle. It already covers resolver block/entity returns, but verify all exact overload injections against the resolved jar.

- Validation/cancellation/miss before effects: no committed payment, scroll loss, cooldown, affinity, progression, or advancement.
- Successful immediate resolution: one resource charge and one success notification per intended cast.
- Accepted delayed delivery is not proof that a later effect succeeded. Keep these outcomes distinct in traces and child-cast accounting.
- Exceptions must release uncommitted reservations; they must not refund damage or other world effects that already happened.

Correct misleading comments encountered in the touched path. For example, the inspected generic Iron’s carrier now uses reusable spellbook semantics despite older comments mentioning scroll semantics. The current Iron’s `attemptInitiateCast` boolean argument is named `triggerCooldown`; it is not a generic “consume mana” toggle. Verify signatures before reusing them. [S2, S9]

## 4. Phase B — define real imported glyphs

### 4.1 Stable identity: spell and level are part of the glyph

Use a distinct registered Ars effect definition for each supported **Iron’s spell resource ID + base scroll level**. Share implementation classes and adapter logic; do not handwrite one Java class per level.

Proposed identity examples:

```text
Effect: ars_n_spells:iron/irons_spellbooks/firebolt/level_3
Item:   ars_n_spells:glyph_iron/irons_spellbooks/firebolt/level_3
Name:   Glyph of Firebolt III
```

Include the complete source namespace/path using a collision-free encoding. Freeze and test the encoding before shipping. Never use translated names, registry iteration indices, school names, or the existing `ars_cross_1..8` proxy pool as glyph identity.

Why this design: native Ars spell parts serialize by ID. Putting `spellId` and `level` only in an item component would lose those values when the player learns or places the part into a normal spell recipe. Do not implement a generic “Iron Spell” part whose singleton is mutated whenever a player selects a different scroll. [S6]

The physical output should use the actual Ars `Glyph` learning contract, or a narrowly extended compatible implementation:

- Learning succeeds server-side, syncs native knowledge, and consumes one glyph only when newly learned.
- Known glyphs are recognized and are not consumed again. Conversion may still produce a spare/tradeable glyph; the Press warns that the interacting player already knows it.
- The learned effect appears in the normal Ars editor, respects book tier and spell validation, and retains its ID through saving, copying, parchment export, logout, death, and client/server synchronization.
- Never grant a paid scroll’s other levels automatically. A level III scroll yields level III, not level I and not every level through the maximum.
- Legitimate native equipment level bonuses may still apply at cast time according to Iron’s rules. Show base and effective level distinctly; do not let an augment permanently unlock a higher-level glyph.

### 4.2 Registration before registry freeze

Build a versioned, deterministic catalog for the pinned base mod, with spell IDs, base level variants, adapter keys, and stable glyph/item IDs. Register ANS-owned items and spell parts during the proper startup lifecycle. Resolve optional Iron’s runtime objects only behind the optional-mod bridge.

Do not enumerate a late-populated Iron’s registry during item registration and assume event ordering will always cooperate. Generate the shipped base catalog from a validated development runtime/source inventory, commit it, and compare it against the loaded registry in verification. Explicit addon adapter modules may contribute their own startup manifests.

Do not register new items or spell parts during `/reload`, workstation use, player login, or world load. Server settings control **availability**, never whether a saved ID exists. Keep registered base definitions present as dormant ANS definitions if Iron’s is absent or a spell is disabled; this protects native Ars recipes from unknown-ID fallback.

Server/client catalog disagreement is a clear compatibility error. Hash the startup catalog and the effective behavior snapshot separately. Sort all generation inputs, cap catalog and payload sizes, and do not persist live `AbstractSpell` instances in item data.

### 4.3 Levels, tiers, and knowledge balance

Default tier mapping should use Iron’s effective rarity for the actual spell level, with explicit overrides for unusually powerful utility/movement/summoning spells:

| Iron’s rarity | Default Ars book tier | Conversion tier |
| --- | --- | --- |
| Common | I | I |
| Uncommon, Rare | II | II |
| Epic, Legendary | III | III |

Validate enum/API names on the target dependency. Never determine rarity by a translated tooltip or level alone. Respect Iron’s spell learning prerequisites and server disables both at conversion and at use; a scroll does not become a way to bypass native progression. Native Ars glyph learning and spellbook tier rules must also remain intact.

**Account for native glyph-count mana bonuses.** The inspected Ars glyph learning code raises its glyph-bonus count as knowledge grows. Registering many level variants can otherwise multiply the mana benefit of learning one spell family. [S10]

Default ANS contribution policy: variants of one imported Iron’s spell contribute at most one normal glyph’s worth of the knowledge-based mana bonus. Keep every variant genuinely learned in Ars knowledge; normalize only the ANS variant contribution at the correct mana-calculation boundary. Do not overwrite all native glyph knowledge or repeatedly subtract from mutable maxima. Expose an explicit pack option for native per-variant behavior, and test this policy against equipment changes, relogs, all mana modes, and Ars Affinity.

### 4.4 Catalog coverage is a deliverable

Produce a generated coverage report with one row per loaded base Iron’s spell:

`spell ID | levels | school | cast type | adapter | supported forms | augments | prerequisites | status/reason | test evidence`

The release target is all ordinary player scroll spells in the pinned base catalog. Instant projectile, target, self, area, movement, summon, long-cast, continuous, and recast families all require attention. Do not call a tiny allowlist “full conversion.”

Explicit exclusions are appropriate for `none`, internal/debug/NPC-only spells, ANS proxy spells, and entries without a valid supported scroll representation. A genuinely unsupported base player spell is an open release gap with a specific technical explanation; do not silently mark it supported or hide a broad unsupported category.

Unknown addon spells remain unconsumed with a clear “No glyph adapter for this spell” status. Publish an adapter extension contract and an example addon mapping. A datapack may choose among implemented adapters and tune them; arbitrary code execution or arbitrary spell behavior cannot be invented by JSON.

## 5. Phase C — make Iron’s effects compose correctly in Ars

### 5.1 Adapter contract

Introduce a loader-independent descriptor and an Iron’s-isolated runtime bridge. Suggested concepts:

```java
// Conceptual interfaces; adapt to real APIs rather than copying as compilable code.
record ImportedGlyphKey(ResourceLocation ironSpellId, int baseLevel) {}
record GlyphInvocation(UUID rootCastId, UUID invocationId,
                       ImportedGlyphKey glyph, CastTarget target,
                       InvocationOrigin origin, int effectIndex,
                       int generation) {}

interface IronGlyphAdapter {
    SupportResult validateComposition(CompositionView spell);
    AdmissionResult validateInvocation(InvocationView invocation);
    NativeCostSpec quote(InvocationView invocation);
    ExecutionHandle start(AuthorizedInvocation invocation);
}
```

The immutable invocation/session must carry the real owner UUID, dimension, caster/tool provenance, school, level, hit position/entity, direction/origin, relevant Ars stats, adapter identity, quote/config generation, and lifecycle state. Resolve entities by bounded references when needed; do not keep worlds or disconnected players alive through a static map.

The imported effect is an Ars `AbstractEffect`. Ars chooses where and when that effect resolves. The adapter translates that context into the specific Iron’s behavior while retaining Iron’s spell identity, native events, attribution, school scaling, conditions, and lifecycle.

### 5.2 Target semantics must be explicit

| Family | Composition contract |
| --- | --- |
| Entity effect | Use the living entity resolved by Ars; preserve original caster ownership, allegiance, PvP checks, and native effect restrictions. |
| Position/area effect | Use the validated impact position and safe placement rules. Do not silently recast at the player’s feet. |
| Projectile-producing spell | An adapter specifies whether the Ars impact becomes its launch origin or a supported direct-impact delivery. Preserve native projectile behavior, owner, collision, and damage; avoid double impact or self-collision. |
| Self-only buff | Require Self unless a deliberately implemented targetable adapter exists. Touch must not silently buff the caster instead of the selected target. |
| Movement/teleport | Move the original caster only. Use an admissible destination, safe placement, loaded-chunk/world-border checks, and native restrictions. Do not teleport a victim merely because Ars hit it. |
| Summon | Use a valid resolved position; ownership, lifetime, count limits, and despawn rules belong to the original caster/native summon behavior. |
| Long/charged | Retain cast duration, cancellation, cast-time modifiers, and completion semantics. |
| Continuous/channelled | Retain pulse timing, bounded duration, interruption, and per-pulse payment. Never run the entire channel in one tick. |
| Recast/multistage | Retain the native stage machine and native recast limits; spell recomposition must not reset or manufacture stages. |

Ship concrete examples and tests after adapters exist: `Self → Iron Heal`, a validated `Touch → Iron Firebolt` adapter, `Projectile → an imported target effect`, and a position-based imported spell. Illustrative names are not evidence of an already-supported combination.

An unconditional call to `attemptInitiateCast` is not a universal target adapter. For example, the inspected Firebolt implementation derives its origin and direction from the caster, while Heal operates on the caster; neither accepts an arbitrary Ars hit simply because ANS has one. [S11]

Prefer native APIs and narrow, adapter-scoped hooks to supply supported context. Do not temporarily teleport/rotate the real player, replace the player with a fake caster, globally alter all native spell targeting, or duplicate whole upstream spell implementations. Reuse upstream runtime classes/assets through their APIs/resource IDs; keep ANS assets original.

### 5.3 Continuations and native casting state

Preserve Ars recipe order. An instantaneous foreign effect completes before the next effect. A charged foreign effect suspends that resolution branch and resumes after successful completion; a continuous effect resumes its continuation after its bounded channel ends. Cancellation cancels the remaining branch and releases only unspent reservations. Previously completed effects remain completed.

Use Ars’s actual context/continuation facilities after inspecting the pinned source; add a narrow ANS mechanism if necessary. Persist a copied cursor/state for deferred work, not the mutable global glyph singleton. Do not restart from glyph zero on completion, and do not replay effects preceding the imported glyph. Following a delayed branch with another glyph must have deterministic, documented targeting.

Treat the native `MagicData` casting slot as a real shared resource. Starting an Iron’s cast when it is already casting can cancel that existing cast. In particular, an Ars spell exported into an Iron’s book can itself contain an imported Iron’s glyph: the outer native proxy is already in a casting lifecycle. A nested `attemptInitiateCast` must not cancel its parent. [S9]

Implement child invocations with explicit parent ownership and scoped lifecycle delegation. An independent conflicting cast is refused without disturbing the first. Multiple instantaneous child effects may execute in order; charged/channelled children require a supported serialized continuation. Do not replace the player’s active casting item or cast data with an unrelated child’s values.

Carry context through delayed projectiles, channels, and recasts explicitly. A temporary thread-local may help a synchronous hook, but must be restored in `finally` and must never be the only store for future ticks.

### 5.4 Augments are supported capabilities, not decorative acceptance

Implement a per-adapter augment matrix and make Ars composition validation enforce it. Follow native placement/stacking rules.

| Augment class | Required policy |
| --- | --- |
| Amplify/Dampen | Modify an explicitly supported potency parameter, with matching cost and bounds. Do not change the learned base-level identity or silently exceed native maximum levels. |
| Duration modifiers | Affect actual effect/channel duration only where supported; update pulse budget and total quoted cost. |
| Area modifiers | Affect a supported area/radius or bounded fan-out, with real cost changes and native protection checks per target. |
| Pierce/Split and repeat delivery | Work only through a verified delivery/fan-out contract. Each additional foreign execution needs authorization and payment. |
| Sensitive | Retains the Ars form’s targeting meaning; it does not make every Iron’s effect valid on air or liquids. |
| Accelerate/Decelerate | Change only adapters with a meaningful verified speed/cast parameter. Do not pretend to accelerate a self buff. |
| Unknown addon augments | Require adapter support; otherwise show the native-style composition error. |

No blanket “all augments supported” return value. Show unsupported combinations before casting where possible. A glyph must do something observably different for every augment it advertises.

### 5.5 Caster types and shared spells

Publish a caster support matrix. Player spellbooks and the existing supported ANS/Iron’s carrier routes are mandatory. Player-owned projectile/delayed contexts retain their original authorized session even after the player changes the held slot; they must not resolve against whatever unrelated item happens to be held later.

Audit spell bows, reactive/imbued items, runes, and turrets as distinct entry points. Never cast as a fake player or treat a missing player mana capability as permission to cast for free. A non-player caster requires an explicit adapter with an appropriate Source/payment and ownership contract; otherwise reject that composition/use clearly before expenditure. Do not advertise those additional caster routes as supported merely because the glyph appears in their editor.

Shared books and copied spells follow native Ars ownership/knowledge/tier rules plus Iron’s own prerequisites. Receiving another player’s powerful spell recipe must not grant learned imported glyphs, native Iron’s research, or unrestricted operator casting. Add a two-player case where one player lacks the relevant permissions/knowledge.

## 6. Phase D — integrate payment, cooldowns, and progression

### 6.1 One typed cost plan

Extend the existing quote and ledger services with a **composite cast plan**. Native Ars delivery/effects and imported Iron’s effects have different origin units; preserve those units until the existing routing policy converts them.

- The Ars leg includes the native form, native effects, and applicable Ars augmentation costs.
- Each imported occurrence contributes its actual Iron’s cost, effective level, supported modifiers, and channel/pulse policy.
- The same imported cost must not be included in the native Ars debit and charged again by Iron’s.
- UI estimates, affordability checks, reservation, and commitment must share the same compiler/quote model. Do not compute the display from an unrelated hard-coded mana cost.
- Retain native discount/modifier hooks without applying either mod’s discount twice. Keep an auditable breakdown of each modifier.
- Apply `cross_cast_cost_multiplier` once to each foreign-cost leg. A nested export/import must not multiply an already-premiumed leg again merely because it crossed another wrapper.
- All participating legs use one `CostRules` generation for admission. Requote unstarted work on a relevant reload; terminate or finish already-authorized work under a documented immutable snapshot. Never check one mode and debit another.

Partition native and foreign cost contributions explicitly at the cost boundary; do not subtract a guessed total from a mutable final cost event after other addons changed it. If Ars requires an integer display cost, provide that plus an ANS breakdown rather than pretending two pools are one integer.

For an ordinary Ars book containing imported glyphs, the expected routing is:

| Mode | Native Ars portion | Imported Iron’s portion |
| --- | --- | --- |
| `iss_primary` | Existing conversion into Iron’s paying pool | Iron’s-origin quote in the Iron’s paying pool |
| `ars_primary` | Ars paying pool | Existing Iron’s-to-Ars conversion |
| `hybrid` | Existing authoritative shared-pool route | Same route; no second deduction from its mirrored display |
| `separate` | Native Ars cost in Ars mana | Existing ANS cross-cast dual-cost split, with directional conversion |
| `disabled` | Native Ars cost in Ars mana | Iron’s cost in Iron’s mana; no mana-pool conversion |

For an exported composite Ars spell, preserve the existing outer-carrier policy for its Ars leg and merge the child legs without duplicating them. Preserve equal-percentage conversion as well as flat rates. Test non-unit rates and asymmetric pool maxima. Do not substitute a custom arithmetic scheme for `StandardQuotePolicy`. [S7]

Glyphs are reusable learned abilities. They must not inherit native **consumable-scroll** free-casting semantics from the scroll used to create them. `scroll_cost_mode=free` must not make a learned glyph free, and a saved `COMMAND`/`MOB` cast source must not bypass normal player constraints.

### 6.2 Reservation and success boundaries

Use a per-root `GlyphCastSession` with a separate child ledger/token for each foreign occurrence. It must coexist with the existing immediate `ArsCastPayments` cleanup: that class’s return/tick cleanup cannot delete the authorization for an effect still travelling in a projectile.

Recommended execution contract:

1. Compile and validate the composition before the root cast. Check knowledge/tier, adapter availability, recursion limits, current cooldown eligibility, and the minimum known resource requirement.
2. Reserve the root Ars portion and the first authorized invocation of each imported occurrence atomically. A shortage in either pool refuses admission before initial world effects.
3. Commit the root Ars portion at its normal successful launch/resolution boundary. Keep foreign reservations associated with bounded child tokens.
4. Before each foreign execution, revalidate the live target, relevant permissions, and native spell conditions. Redeem its reservation immediately before the first corresponding world effect.
5. If the delivery misses or the child is canceled before any foreign effect, release that unused foreign reservation. The successfully launched Ars delivery remains paid. Expose reserved versus available resources consistently in diagnostics/UI.
6. Additional area/split/pierce invocations require additional per-invocation quotes and payment before effects. State the estimate or maximum budget in the spell details. Reject or stop at the configured bound; no unbounded prepaid “one glyph buys unlimited native casts.”
7. Channels pay each pulse through the native lifecycle contract. Admission reserves the required initial amount; later pulses require funds before they execute. Stop cleanly on the last affordable pulse, preserving the existing channel-payment fix.
8. Expired, invalidated, logged-out, dead, unloaded, or canceled sessions release only unused reservations. A returned/duplicated continuation cannot redeem the same token again.

Use explicit outcomes such as `REJECTED`, `ACCEPTED_PENDING`, `EFFECT_APPLIED`, `COMPLETED`, and `CANCELED`. Do not equate `attemptInitiateCast == true` with a completed charged spell.

Bound active sessions, continuation lifetime, pending target references, and fan-out. Suggested safe starting defaults: four imported effect occurrences per recipe, sixteen foreign emissions per root cast, and eight active foreign-bearing root sessions per player. Choose a lifetime bound compatible with the longest supported native cast, publish it, and preserve legitimate long-lived effects after completion. A server setting cannot exceed a tested hard ceiling.

### 6.3 Cooldowns, recasts, and recursion

Native Iron’s cooldown identity is the source **spell ID**, not the glyph item, Ars book slot, scroll level variant, or player-chosen spell name. Switching Firebolt II to Firebolt III or copying a spell into another book must not reset it.

Reserve cooldown eligibility while a cast is pending; commit the cooldown at the native success point. Release a pre-effect cancellation according to native rules. This prevents launching many delayed copies before any one begins its cooldown.

An explicit bounded fan-out batch may share a single occurrence’s native admission, while charging each authorized target execution. Its tokens are valid only for that root/effect occurrence; a separate cast or a repeated glyph occurrence cannot borrow them. Define and test this intentional area-cast behavior. Do not globally disable Iron’s cooldown checks to permit it.

Keep optional ANS category cooldowns and native cooldown reduction working. Suppress duplicate cooldown/event bookkeeping only for the exact delegated invocation. Preserve unrelated native casts and native recast windows.

Never import `ars_n_spells:ars_cross_*` or any foreign spell whose only action recursively delegates to an exported ANS carrier. Reject these at conversion and execution. Track root ID, ancestry, and depth for nested cross-casts; enforce bounded depth and detect cycles. An exported Ars spell containing real imported effects is valid when the supported adapter path handles it safely; it must not recurse through its own carrier descriptor.

### 6.4 Damage, attribution, and training

- Native Iron’s effect damage stays Iron’s-origin damage and keeps its native damage source/school/owner. Passing through Ars delivery must not apply the Ars damage multiplier a second time.
- Preserve legitimate existing cross-mod gear, affinity, and resonance modifiers once. Compare representative glyph casts against native Iron’s casts with equivalent gear and level.
- Healing, summons, knockback, debuffs, and movement need behavioral checks; damage-only tests are insufficient.
- Give ANS progression/affinity at most one intended root-cast credit under its established primary-school policy. Add explicit delegated-event deduplication; do not award full progression at both the Ars wrapper and every Iron’s child/pulse.
- Keep Ars Affinity’s glyph progress and ANS school affinity separate. Verify the relevant addon’s event model instead of emitting duplicate synthetic native events for its benefit.
- Emit native cancellation/condition hooks at the appropriate boundary. Do not cancel a non-cancelable event or treat a post-cast event as preflight.

## 7. Phase E — build the Glyph Press

### 7.1 Identity and art direction

**Block/item ID:** `ars_n_spells:glyph_press`  
**Blank substrate:** `ars_n_spells:blank_glyph_plate`  
**Display name:** Glyph Press

Create a one-block workstation that looks like a companion to the Spell Loom: a dark stone base, archwood supports, restrained brass/gold framing, a scroll cradle, a flat rune plate, and a small suspended source crystal or engraving stylus. The silhouette should read as a magical press at normal gameplay distance.

During work, a small stream of school-colored particles travels from the scroll toward the rune plate; the plate gains a softly pulsing rune. Use a brief engraving sound and a distinct completion chime. An idle Press should be quiet and inexpensive to render.

Use original pixel textures consistent with the mod’s existing vanilla-style assets. Prefer a modest static model with a small optional animated element; do not duplicate the Loom’s geometry wholesale. Cache voxel shapes, support horizontal facing, and align selection/collision with the model. Keep particles/sounds client-side and bounded; server logic must not load renderer classes.

Add blockstate, item/block models, textures, translations, crafting recipe, loot table, pickaxe/mining tags, creative-tab placement, and documentation assets. Preserve the existing Loom’s appearance and recipes.

Suggested shaped crafting recipe, to validate with the current tags/items:

| Slot | Ingredient |
| --- | --- |
| Top left / center / right | Gold ingot / Ars source gem / gold ingot |
| Middle left / center / right | Ars archwood planks / vanilla lectern / Ars archwood planks |
| Bottom row | Three obsidian |

Suggested blank plate recipe: smooth stone + gold nugget + amethyst shard → two blank glyph plates. Use exact registered IDs or verified tags. Put both recipes in datapack-controlled resources; gate the functional Press recipe on Iron’s presence without conditionally removing the saved block ID.

### 7.2 Inventory and survival conversion

Four slots:

| Slot | Purpose | Consumption |
| --- | --- | --- |
| 0 | One genuine filled Iron’s scroll | One scroll per completed operation |
| 1 | Blank glyph plate | One per completed operation |
| 2 | Source-gem catalyst stack | Tier-dependent count |
| 3 | Result glyph | Output only |

The operation reads Iron’s actual `ISpellContainer`/scroll contract behind an optional bridge. Do not accept every item with a spell container: books, weapons, staffs, and ANS sidecar carriers are not scrolls. Native scroll implementations from addons require a deliberate compatible reader.

Require exactly one supported nonempty spell entry and a valid level. Reject blank scrolls, `none`, malformed containers, unexpected multi-spell scrolls, future/unknown schemas, ANS proxy scrolls, and Ars-export sidecars. Do not silently choose slot zero from an ambiguous source or clamp an invalid level into a valuable output. Retain the complete original input stack until successful commitment.

Proposed initial tuning, all data-driven:

| Conversion tier | Source gems | Source energy | Duration |
| --- | --- | --- | --- |
| I | 1 | 500 | 100 ticks / 5 seconds |
| II | 2 | 1,500 | 140 ticks / 7 seconds |
| III | 4 | 3,000 | 200 ticks / 10 seconds |

The Press converts **knowledge**, not another consumable casting scroll. No random failure, destroyed-input lottery, hidden quality roll, or extra player XP fee. Balance power through scroll acquisition, level-specific knowledge, tiers, Source, native casting costs, and cooldowns.

### 7.3 Source supply and machine lifecycle

Use Ars’s supported Source interfaces/utilities for the resolved version. The workstation should accept normal nearby Source supply or relay integration that those APIs support. Verify the actual API before implementing; Source energy is not player mana.

Give the Press an internal Source buffer of at least 4,000 by default. Pull Source only while a valid job needs energy, at a bounded interval and radius, from already-loaded locations. Do not scan a cube every tick for every idle block, load chunks to find jars, or move jar objects into persistent state.

Use a clear state machine: `IDLE → READY → PROCESSING → OUTPUT_READY`, with pause/cancel reasons. Before processing:

1. Verify the source scroll, adapter/level, player prerequisites for a manual job, ingredients, server config, recipe generation, and output capacity.
2. Create an immutable job snapshot with an operation ID and input fingerprint.
3. Reserve its Source from the local buffer and lock the relevant inputs against removal/replacement. Keep the items physically present until completion.
4. Advance progress server-side. The screen is a synchronized view of server state.
5. At completion, revalidate the snapshot and output capacity. Atomically decrement the inputs, consume the Source reservation, insert the exact output, and clear the job in one block-entity mutation.

If output becomes blocked, pause without consuming inputs. An ordinary cancel unlocks the original items and returns the Source reservation to the internal buffer. A recipe/config change invalidating the operation cancels cleanly rather than producing an obsolete result.

Persist inventory, Source, reservation, job identity/fingerprint, progress, and schema version together. On load, resume a valid job or cancel it while preserving its items; never replay a completed output. Respect Minecraft’s normal save guarantees rather than claiming arbitrary cross-file crash atomicity.

Breaking the block drops its contained items exactly once. A processing job yields the original inputs, not a glyph plus inputs; Source dissipates on block destruction unless a deliberately implemented retained-block-state policy says otherwise. Handle explosions, creative breaking, replacement, chunk unload, and server restart. A break/drop path must clear ownership before emitting item entities.

### 7.4 Menu and usability

Use the existing `VanillaGui`/`VanillaPanelScreen` conventions as appropriate. Keep the base screen compact, legible at normal GUI scales, keyboard-navigable, and narratable.

The player should see:

- Scroll spell name, base level, school, and icon.
- Result glyph name, Ars tier, and whether the interacting player already knows it.
- Required/available catalysts and Source, progress, and a single **Inscribe Glyph** action.
- Supported forms and a short summary of compatible augments.
- A concise restriction such as “Self only,” “Requires a living target,” or “Channels for …”.
- A clear reason when conversion is unavailable, with no ingredients consumed.

Keep a result preview visually distinct from a real extractable output. Do not place a preview stack in a real item-handler slot. Useful messages include “Insert a filled Iron’s scroll,” “This scroll contains an exported Ars spell,” “Unknown spell adapter,” “Requires tier II catalyst materials,” “Needs 1,500 Source,” “Output slot is full,” and “Spell disabled by the server.” Localize all messages.

Use the spell’s runtime icon/resource when available, with an original ANS fallback and a small readable level marker. Do not embed client resources in common/server initialization. Show costs as estimates where player gear or deferred fan-out affects them.

Give the Press a short help panel explaining that the output is learned by the normal Ars glyph action. Distinguish **Inscribe Glyph** from the Loom’s export operation and the existing rituals. Provide a compact in-game tutorial and at least one verified spell recipe example.

### 7.5 Automation and concurrent players

Reuse the Loom’s directional capability pattern: top supplies scrolls, horizontal faces supply matching materials, bottom extracts output. Unsided handlers may insert supported inputs and extract output. Lock processing inputs in every route, including GUI shift-click and hopper simulation/execution.

Default to manual initiation. Inventory automation may stage materials and extract results; it must not impersonate a player to bypass native learning prerequisites. If automatic jobs are added, define an explicit owner/prerequisite policy and enable them separately; do not make that an implicit 3.4.0 requirement.

Two players looking at the same Press must see one job. Repeated button presses, replayed packets, stale previews, disconnects, and inventory changes cannot duplicate outputs. Reuse request UUID admission, menu/session identity, validity/distance checks, inventory revision, and effective recipe/catalog digest. The server derives the spell, level, costs, and result entirely from current authoritative inputs.

## 8. Phase F — data, configuration, and integrations

### 8.1 Separate immutable IDs from reloadable behavior

Use a startup catalog for registry identity and a reloadable mapping/recipe layer for enabled status, adapter configuration, tier overrides, catalyst ingredients, Source cost, processing time, forms, augments, and limits.

An illustrative **new ANS schema**, not an existing upstream recipe format:

```json
{
  "schema_version": 1,
  "spell": "irons_spellbooks:firebolt",
  "adapter": "ars_n_spells:projectile",
  "enabled": true,
  "levels": {
    "3": {
      "ars_tier": 2,
      "source_cost": 1500,
      "duration_ticks": 140,
      "catalyst": {
        "tag": "ars_n_spells:glyph_press_catalysts",
        "count": 2
      }
    }
  }
}
```

Choose and document a custom resource directory such as `data/<namespace>/ans_iron_glyphs/`. This example illustrates an explicit override; generate the real defaults from verified rarity and adapter data. Recipe serializer registration and Minecraft’s built-in data directory names must match 1.21.1, not an older Forge example copied from documentation.

Rules:

- Every referenced spell-level glyph must already have a startup identity. A reload cannot manufacture new registered variants. If a server raises a spell’s maximum beyond the supported catalog, report the unsupported levels clearly; do not clamp or remap them.
- Validate finite bounded numbers, known adapters, valid ingredients, tier limits, and duplicate mappings. Detect identifier encoding collisions.
- Define deterministic pack priority/override behavior. Report the winning resource ID and source pack in diagnostics.
- Parse a new immutable snapshot, validate it fully, then publish it and synchronize a bounded client view. Reject an invalid candidate snapshot while retaining the last valid one; at first startup fail affected conversion definitions closed and report useful errors.
- Never silently preserve a partial recipe while using new costs from another generation. Invalidate stale menu previews/jobs safely.
- Respect Iron’s current server spell disables, prerequisite settings, and Ars glyph disables. An ANS allowlist cannot override an upstream disabled spell.
- Support spell/school allowlists and denylists with declared precedence; an explicit deny wins. Resolve schools through the existing school mapping infrastructure rather than hard-coded enum assumptions.

### 8.2 Proposed configuration surface

Use the project’s actual server-config registration/storage conventions. Reconcile existing documentation that calls the file “common” while describing server-owned behavior; do not create a second competing config file.

| Setting | Proposed default | Meaning |
| --- | --- | --- |
| `enable_iron_glyphs` | `true` | Conversion/learning/casting availability; registry identities remain present. |
| `enable_glyph_press` | `true` | New jobs allowed; stored items remain recoverable when off. |
| `iron_glyph_mana_bonus_policy` | `per_spell` | Count imported level variants once per spell family for ANS’s knowledge bonus contribution; optional `native_per_variant`. |
| `max_imported_effects_per_spell` | `4` | Composition limit with a tested hard bound. |
| `max_foreign_emissions_per_cast` | `16` | Bounded fan-out, including repeated deliveries. |
| `max_active_glyph_sessions_per_player` | `8` | Resource/continuation bound. |
| `glyph_press_source_capacity` | `4000` | Internal buffer; validate against enabled recipe costs. |
| `glyph_press_source_scan_interval_ticks` | `20` | Only for active demand, if nearby-source discovery is needed. |
| `glyph_press_source_radius` | `4` | Loaded-only discovery range, bounded and used only if appropriate for the chosen native API. |

Reuse the existing cross-cast multiplier, conversion rates, dual shares, cooldown system, discounts, and school mappings. Do not introduce duplicate global toggles with conflicting meanings. Keep per-spell details in the mapping layer and native spell configs rather than hundreds of ad hoc TOML keys.

Disabling the feature cancels uncommitted jobs/invocations safely, leaves learned glyph IDs and stored stacks intact, and explains that the glyph is dormant. Re-enabling restores operation without relearning or re-inscribing. Saved worlds must not silently lose glyphs because a config changed.

### 8.3 Viewer, guide, and editor integration

- Add a JEI category for Glyph Press conversion using real component-bearing scroll inputs and the correct level-specific glyph outputs. Plain identical scroll icons without their spell data are insufficient.
- Recipe previews, craftability, and the Press must consult the same effective definitions. Reload refreshes the viewer; missing Iron’s disables only Iron’s-dependent entries.
- JEI remains optional. Add other viewers only if already supported by the checkout or through a small explicit adapter; do not add a mandatory viewer dependency.
- Integrate with the actual Ars documentation system used by the resolved 5.13.x artifact. Do not assume an old Patchouli page is the current documentation API.
- Make native editor search practical: include spell name, school, and level in accessible localized labels; respect native known/unlearned filters. Avoid a replacement spell editor.
- Keep missing textures/icons from crashing the UI. Prefer one shared glyph visual family with runtime spell icon references and generated models where appropriate.
- Extend `/ans` diagnostics with held glyph/spell inspection and an operator-readable catalog/adapter coverage report. Add an op-only repair/admin conversion command only if useful; it must call the same planner and cannot be the sole conversion path.

### 8.4 Optional dependency boundaries

Iron’s remains optional. Common registration, saved ANS blocks/items, component codecs, menu types, and dormant glyph definitions must not directly link classes that are absent without Iron’s. Use a small bridge interface and an optional implementation with an absent implementation.

Prefer extending the pattern established by `IronsInscriptionReader`, while making the Press’s stricter scroll validation explicit. Do not reuse its generic source acceptance without checking item kind, every container entry, `none`, and bounds. [S12]

When Iron’s is absent:

- A fresh client and dedicated server still boot with Ars and ANS.
- Existing ANS Press blocks and output glyphs deserialize as ANS objects and show dormant status.
- Learned ANS glyph identities remain registered and do not turn into Break.
- No missing Iron’s class is resolved by common init, a tooltip, a codec, or a menu opening.
- Native Iron’s items/dimensions removed with their owning mod remain subject to upstream save limitations. Do not promise that ANS can restore an entire world after arbitrary mod removal.

## 9. Phase G — persistence and 3.3.x migration

Keep old `cross_spells` component IDs, proxy pool IDs, bound-book associations, player progression, affinity, cooldowns, and Loom inventories readable. The Touch fix should improve old eligible scrolls/books in place; it must not require rebuilding them.

New data needs explicit schemas:

- Startup glyph catalog/version and immutable ID mapping.
- Effective mapping generation/digest for requests and sessions.
- Glyph Press inventory/job/Source state version.
- Any necessary session attachment or persistent cooldown extension.

Do not migrate the existing eight-slot Iron’s proxy pool into the imported-glyph registry. Those proxies represent complete exported Ars spells in native Iron’s books, a different feature.

The physical imported glyph normally needs no arbitrary per-stack spell payload because its stable item/effect identity already names the spell and base level. If auxiliary components are necessary, use bounded immutable records with persistence and stream codecs; reject or preserve future versions according to the project’s existing dormant-data conventions.

Native Ars books can contain these new effect IDs. Registering known ANS definitions as dormant tombstones is mandatory when an adapter is unavailable. Extend pre-decode integrity checks where relevant to exported sidecars. Test native books as well as ANS components; guarding only `CrossModSpellList` cannot protect a normal Ars spellbook. [S6]

If a future release retires an adapter or variant, keep its identity and an explicit unavailable reason. Never reuse an ID for another spell. Do not automatically rewrite unknown future content into an empty spell or allow native unknown-ID fallback to turn it into a block-breaking spell.

Network changes require a deliberate protocol bump from the actual current value, plus matching client/server requirements and a readable mismatch error. Do not hard-code a presumed next number from an old README. Bump mod metadata and release references to **3.4.0** consistently; do not relabel old verification results as 3.4.0 evidence.

## 10. Suggested implementation map

All Java paths below are relative to `src/main/java/com/otectus/arsnspells/`. Suggested new names may be adjusted to fit the repository’s conventions.

| Existing area | Required responsibility |
| --- | --- |
| `spell/CrossCastingHandler.java` | Interaction coverage; delegate Ars casts to a shared target-aware dispatcher. |
| `network/CrossCastRequestPayload.java` | Bounded target/input context, retained request admission/fingerprint checks. |
| `spell/irons/ArsCrossProxySpell.java` | Target-aware delegation from the resolved native carrier; nested-cast ownership. |
| `mixin/ars/MixinSpellResolverPreCast.java`, `MixinSpellResolverMana.java` | Verify immediate admission/expenditure coverage; add scoped composite participation without breaking native spells. |
| `casting/QuoteService.java`, `ArsCastPayments.java`, `IronsCastPayments.java`, `IronsCastLifecycle.java`, `CastLedger.java` | Composite quotes, per-child authorization, proper deferred cleanup, native lifecycle preservation. |
| `spell/CrossCastValidator.java`, `util/ArsSpellIntegrity.java` | Missing/dormant/cycle validation, imported-glyph compatibility. |
| `events/ArsManaCalcHandler.java` and native glyph bonus boundary | Normalize only imported family knowledge contribution; preserve prior mana fixes. |
| Existing school/combat/progression handlers | Single application of scaling and intended training credit. |
| `registry/*`, `network/PacketHandler.java`, `ArsNSpells.java` | New content/menus/payloads/capabilities and optional-mod-safe registration. |
| Existing Loom/menu/request helpers | Extract only genuinely shared mechanics; keep Loom behavior intact. |

Suggested new units:

```text
spell/ArsCastDispatcher
spell/target/ValidatedCastTarget
glyph/ImportedGlyphDefinition
glyph/ImportedGlyphCatalog
glyph/ImportedIronEffect
glyph/GlyphCompositionValidator
glyph/GlyphCastSession
glyph/GlyphInvocationBudget
compat/irons/glyph/IronGlyphAdapter
compat/irons/glyph/… family and spell-specific adapters
glyph/recipe/GlyphPressPlan
glyph/recipe/GlyphPressRecipeManager
block/GlyphPressBlock
block/GlyphPressBlockEntity
menu/GlyphPressMenu
client/screen/GlyphPressScreen
network/GlyphPressActionPayload
network/GlyphPressResultPayload
```

Keep pure planning, identity, budget, and cost logic testable without a client. Keep runtime integration tests responsible for proving actual upstream behavior. Avoid a sprawling universal handler with spell-name substring switches.

## 11. Verification and acceptance gates

### 11.1 Meaningful automated tests

Use existing JUnit and loaded GameTest infrastructure; add tests around observable behavior and failure boundaries.

| Group | Required scenarios |
| --- | --- |
| Touch regression | Scroll → block, scroll → entity, native-book controls, miss, out of reach, obstructed entity, Sensitive, generic carrier, wheel, staff/Curios routing. Prove the target world effect, not merely a `true` return. |
| Input authority | Item/block/entity event overlap, duplicate UUID, fresh-UUID spam rate bound, stale fingerprint, forged target, wrong dimension, unloaded target, hand mismatch, disconnected/dead sender. |
| Conversion | Correct spell and exact level; blank/none/proxy/sidecar/multiple entries rejected; invalid level rejected; upstream disable/prerequisite enforced; full output; insufficient catalyst/Source; input unchanged on rejection. |
| Machine lifecycle | Two viewers, shift-click, hopper simulation/execution, input mutation attempts, cancel, unload/reload, restart, recipe reload, break/explosion, no duplicate result or item loss. |
| Glyph identity | Learn once, duplicate learn does not consume, normal editor availability, tier gating, book/scroll copy, serialization/stream round-trip, level preservation, no singleton data leakage between players. |
| Adapter behavior | Real target/position/ownership per family, preconditions, native event veto, charge interruption, continuous shortage, recast stages, following Ars effects execute once in order. |
| Caster routes | Player delivery retains its owner/session after item switching; shared-book restrictions; reactive/rune/turret routes are explicitly supported or clearly refused, never free fake-player casts. |
| Cost and cooldown | All five modes; unequal rates; equal-percent mode; dual split; discounts; creative policy; exactly-once overhead; atomic shortage; child cancellation; split/area budgets; per-spell cooldown across levels/books. |
| Nested casting | Imported glyph inside an Ars spell exported to an Iron’s scroll/book; active native parent remains intact; no recursive proxy import; no duplicated effects/payments; one foreign child cannot cancel an unrelated native cast. |
| Scaling/training | No doubled damage multiplier, legitimate native events retained, healing/summon ownership, single intended school credit, Ars Affinity progress tested separately. |
| Optional/migration | Iron’s-absent fresh boot; dormant ANS glyph/book data; re-enable adapter; existing 3.3.x scrolls/books/Loom inventories unchanged; missing effect does not become Break. |
| Knowledge bonus | First imported spell versus additional level variants, normal Ars glyphs unaffected, repeated sync/equipment changes/relogs do not inflate or erode maxima. |

Required representative runtime coverage includes at least one verified spell from each supported adapter family and every spell requiring a special adapter. Generate catalog tests so an ordinary player scroll added to the pinned registry cannot be silently omitted. Include each family’s minimum and maximum supported levels plus invalid boundaries.

Pure tests should verify deterministic IDs/collision resistance, recipe generation validation, budget accounting, schema preservation, exact once-only transitions, and non-unit cost arithmetic. Do not replace gameplay evidence with source-code string assertions or mocks that bypass the upstream API being tested.

### 11.2 Execute the actual runtime profiles

The reviewed build contains these profiles; verify names and repository instructions before running them:

```bash
./gradlew build
./gradlew runGameTestServer -PgametestRunDir=run-glyph-press-no-irons
./gradlew runGameTestServer -PwithIronsRuntimeGameTests -PgametestRunDir=run-glyph-press-irons
./gradlew runGameTestServer -PwithIronsRuntimeGameTests -PwithArsAffinity -PgametestRunDir=run-glyph-press-affinity
```

Use isolated run directories. Confirm each report identifies the expected loaded mods and that optional tests actually ran; a skipped Iron’s suite is not success. Use the existing addon profiles for compositions claimed to support Ars Elemental, Ars Zero, or Ars Elemancy. Respect existing blacklists unless a specific context requirement is genuinely implemented.

If the wrapper or environment is broken, repair an authorized repository-local issue or document the exact blocker and run equivalent permitted tooling. Do not claim a runtime pass from a failed dependency download. Do not update all dependencies simply to make the environment easier.

### 11.3 Real client and dedicated multiplayer checks

Perform one complete survival journey: acquire a scroll, craft/place/fuel the Press, convert it, learn the glyph, compose it in a valid Ars book, and cast a verified form/augment combination. Repeat with a high-tier scroll and an unsupported input to confirm explanations.

In a client connected to a dedicated server, verify:

- Touch casting at blocks/entities with actual input events.
- Two users interacting with one Press, inventory edits, and latency/repeated clicks.
- Native Iron’s book/staff/hotkey casts, exported Ars casts, and nested imported effects.
- Cast time/pulse animations, cancellation controls, sound frequency, cooldown display, and mana display.
- GUI scales, long localized names, narrow windows, keyboard navigation, narration, recipe viewer absent/present, resource-pack overrides, and missing-icon fallback.
- Save/restart with a Press mid-job and with learned/selected imported glyphs.

Inspect the finished block and screen in-game. Include review screenshots in repository documentation only when appropriate to the project. An offline model render does not prove in-game collision, lighting, GUI, or interaction correctness.

### 11.4 Performance checks tied to concrete risks

Compare a baseline world against a scene containing 100 idle Presses and a controlled set of active Presses; record tick cost and Source scans. Idle Presses should perform no source discovery or per-tick registry work. Active work must be bounded and must not force chunks.

Measure catalog startup/config count and native editor responsiveness with the full level-variant catalog. Cache icons and definition lookup; do not repeatedly scan the entire Iron’s registry from a tooltip, render tick, or per-effect callback. Check that expired sessions and canceled channels return memory/state counts to baseline.

## 12. Delivery sequence and required repository outputs

Implement in reviewable increments:

1. **Baseline and reproduction:** dependency/source inventory, failing Touch regression, observed input/casting traces.
2. **Touch repair:** shared target-aware dispatch, event coverage, authority checks, runtime proof, old-carrier regression protection.
3. **Catalog and learning:** deterministic spell-level identities, real Ars knowledge/editor round-trip, dormant definitions, glyph-bonus policy.
4. **One complete vertical slice:** real scroll → Press → glyph → native editor → targeted cast, with correct quotes/cooldowns and multiplayer inventory semantics. This is an internal milestone, not the completed major release.
5. **Full adapter coverage:** charged/channelled/recast/movement/summon families, augment matrix, continuations, all ordinary base-scroll coverage accounted for.
6. **Nested composition and integration:** exported composite spells, all mana modes, upstream/addon event compatibility, migration, optional-Iron’s loading.
7. **Player-facing completion:** final model/textures/screen, help, viewer category, translations, configuration reference, performance and real-client acceptance.

Deliver at least:

- Implementation and assets with version **3.4.0** and appropriate network version.
- `docs/3.4.0/touch-regression.md`: reproduction, causal explanation, fix, evidence.
- `docs/3.4.0/glyph-press.md`: crafting, Source, conversion, learning, limitations, verified examples.
- `docs/3.4.0/iron-glyph-coverage.md` or a generated table plus machine-readable manifest.
- `docs/3.4.0/adapter-authoring.md`: real extension APIs and a working example.
- `docs/3.4.0/config-reference.md`: defaults, precedence, reload/restart behavior, dormant state.
- `docs/3.4.0/testing.md`: exact commits/dependencies, executed commands, pass/fail/skip counts, runtime/manual evidence, open gaps.
- Updated README/changelog and existing project maps where present.

The player-facing changelog should explain that Touch scroll casting is repaired, describe the Glyph Press and learned Iron’s glyphs, explain level/tier behavior and meaningful compatibility limits, and state client/server requirements. Keep internal class names out of the player instructions.

### Final completion checklist

- [ ] The reported Touch case is reproduced and fixed through the real input path.
- [ ] Old supported carriers benefit without manual reconstruction.
- [ ] The Press consumes exactly the intended resources and produces the correct learnable glyph.
- [ ] The glyph is a native Ars composition part with stable spell/level identity.
- [ ] Ordinary base Iron’s player scroll spells have implemented coverage; exceptions are specific and justified.
- [ ] Advertised forms and augments have tested, meaningful behavior.
- [ ] Native prerequisites, cast time, channels, cooldowns, recasts, attribution, and protection hooks survive conversion.
- [ ] Mana is correctly quoted/reserved/charged once in every supported mode and nested route.
- [ ] No infinite recursion, free channel/fan-out, cooldown evasion, duplication, or runaway glyph-count mana bonus remains.
- [ ] Optional-mod absence and old 3.3.x data are handled deliberately.
- [ ] Client, dedicated server, multiplayer, assets, documentation, and release metadata are complete.

Do not substitute a green build for any unchecked item. Report remaining blockers precisely; do not describe stubbed adapters, unsupported ordinary scroll families, or unexecuted runtime tests as completed support.

## 13. Source references

These links identify the inspected snapshots. Reconcile them with the implementation checkout and the resolved dependency artifacts. The design requirements above extend beyond these sources.

- **S1 — ANS NeoForge baseline:** [gradle.properties](https://github.com/otectus/ars-n-spells/blob/087ed3d172bfe2e0c8351d25debc7737b2be118f/gradle.properties), [README](https://github.com/otectus/ars-n-spells/blob/087ed3d172bfe2e0c8351d25debc7737b2be118f/README.md), [build.gradle](https://github.com/otectus/ars-n-spells/blob/087ed3d172bfe2e0c8351d25debc7737b2be118f/build.gradle).
- **S2 — Current cross-cast dispatch:** [CrossCastingHandler](https://github.com/otectus/ars-n-spells/blob/087ed3d172bfe2e0c8351d25debc7737b2be118f/src/main/java/com/otectus/arsnspells/spell/CrossCastingHandler.java), [ArsCrossProxySpell](https://github.com/otectus/ars-n-spells/blob/087ed3d172bfe2e0c8351d25debc7737b2be118f/src/main/java/com/otectus/arsnspells/spell/irons/ArsCrossProxySpell.java).
- **S3 — Existing request protocol:** [CrossCastRequestPayload](https://github.com/otectus/ars-n-spells/blob/087ed3d172bfe2e0c8351d25debc7737b2be118f/src/main/java/com/otectus/arsnspells/network/CrossCastRequestPayload.java).
- **S4 — Ars Touch behavior:** [MethodTouch at the 5.13.1 version commit](https://github.com/baileyholl/Ars-Nouveau/blob/112920ff774831f204031da75b4c4e73d3765157/src/main/java/com/hollingsworth/arsnouveau/common/spell/method/MethodTouch.java).
- **S5 — Ars resolution boundaries:** [SpellResolver](https://github.com/baileyholl/Ars-Nouveau/blob/112920ff774831f204031da75b4c4e73d3765157/src/main/java/com/hollingsworth/arsnouveau/api/spell/SpellResolver.java), [SpellBook](https://github.com/baileyholl/Ars-Nouveau/blob/112920ff774831f204031da75b4c4e73d3765157/src/main/java/com/hollingsworth/arsnouveau/common/items/SpellBook.java).
- **S6 — Ars identity/serialization:** [AbstractSpellPart](https://github.com/baileyholl/Ars-Nouveau/blob/112920ff774831f204031da75b4c4e73d3765157/src/main/java/com/hollingsworth/arsnouveau/api/spell/AbstractSpellPart.java), [GlyphRegistry](https://github.com/baileyholl/Ars-Nouveau/blob/112920ff774831f204031da75b4c4e73d3765157/src/main/java/com/hollingsworth/arsnouveau/api/registry/GlyphRegistry.java), [Spell codec](https://github.com/baileyholl/Ars-Nouveau/blob/112920ff774831f204031da75b4c4e73d3765157/src/main/java/com/hollingsworth/arsnouveau/api/spell/Spell.java).
- **S7 — Existing payment ownership:** [QuoteService](https://github.com/otectus/ars-n-spells/blob/087ed3d172bfe2e0c8351d25debc7737b2be118f/src/main/java/com/otectus/arsnspells/casting/QuoteService.java), [StandardQuotePolicy](https://github.com/otectus/ars-n-spells/blob/087ed3d172bfe2e0c8351d25debc7737b2be118f/src/main/java/com/otectus/arsnspells/contract/StandardQuotePolicy.java), [ArsCastPayments](https://github.com/otectus/ars-n-spells/blob/087ed3d172bfe2e0c8351d25debc7737b2be118f/src/main/java/com/otectus/arsnspells/casting/ArsCastPayments.java), [IronsCastLifecycle](https://github.com/otectus/ars-n-spells/blob/087ed3d172bfe2e0c8351d25debc7737b2be118f/src/main/java/com/otectus/arsnspells/casting/IronsCastLifecycle.java).
- **S8 — Workstation precedent:** [SpellLoomBlockEntity](https://github.com/otectus/ars-n-spells/blob/087ed3d172bfe2e0c8351d25debc7737b2be118f/src/main/java/com/otectus/arsnspells/block/SpellLoomBlockEntity.java), [LoomInscription](https://github.com/otectus/ars-n-spells/blob/087ed3d172bfe2e0c8351d25debc7737b2be118f/src/main/java/com/otectus/arsnspells/inscription/LoomInscription.java), [LoomRequestHandler](https://github.com/otectus/ars-n-spells/blob/087ed3d172bfe2e0c8351d25debc7737b2be118f/src/main/java/com/otectus/arsnspells/network/LoomRequestHandler.java), [Loom visual documentation](https://github.com/otectus/ars-n-spells/blob/087ed3d172bfe2e0c8351d25debc7737b2be118f/docs/3.3.3/spell-loom.md).
- **S9 — Iron’s native lifecycle:** [AbstractSpell](https://github.com/iron431/irons-spells-n-spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/api/spells/AbstractSpell.java), [Scroll](https://github.com/iron431/irons-spells-n-spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/item/Scroll.java), [version properties](https://github.com/iron431/irons-spells-n-spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/gradle.properties).
- **S10 — Native learning and glyph bonus:** [Glyph](https://github.com/baileyholl/Ars-Nouveau/blob/112920ff774831f204031da75b4c4e73d3765157/src/main/java/com/hollingsworth/arsnouveau/common/items/Glyph.java).
- **S11 — Examples of spell-specific targeting:** [FireboltSpell](https://github.com/iron431/irons-spells-n-spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/spells/fire/FireboltSpell.java), [HealSpell](https://github.com/iron431/irons-spells-n-spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/spells/holy/HealSpell.java), [TeleportSpell](https://github.com/iron431/irons-spells-n-spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/spells/ender/TeleportSpell.java).
- **S12 — Optional-mod isolation:** [IronsInscriptionReader](https://github.com/otectus/ars-n-spells/blob/087ed3d172bfe2e0c8351d25debc7737b2be118f/src/main/java/com/otectus/arsnspells/rituals/IronsInscriptionReader.java).



