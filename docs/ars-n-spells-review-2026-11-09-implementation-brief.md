# Ars 'n Spells review and implementation brief

Reviewed 9 November 2026. This is an implementation handoff, not a record of fixes made by this review.

The immediate priorities are casting lifecycle correctness, reliable unbinding, coherent configuration changes, and completing addon support. Do not restart the earlier 3.4.1 implementation: substantial fixes are already present in both working trees. Preserve them, reproduce the remaining failures, and implement the work below against the current source.

## 1. Scope, baselines, and evidence

Two local projects were inspected:

| Prefix used below | Checkout | Baseline commit | Current working version |
| --- | --- | --- | --- |
| **F** | `/home/otectus/Projects/ars-n-spells` | `0da9de067fac60db06f18ba407b266261c65db47` | Uncommitted 3.4.1, Forge 1.20.1 |
| **N** | `/home/otectus/Projects/1.21.1 Ports/ars-n-spells_1.21.1` | `c234f19145d4380a849597f7e390fd535cc45e4f` | Uncommitted 3.4.1, NeoForge 1.21.1 |

Both commits are 3.4.0 baselines; **the findings refer to the working trees, including their untracked source**, not just those commits. The existing `ars-n-spells-3.4.1-improvement-instructions.md` predates much of that work. Treat its historical findings as leads to recheck, not as an unchanged backlog. No production source was changed during this review.

`F:` and `N:` Java paths below are relative to `src/main/java/com/otectus/arsnspells/` in the corresponding checkout. Other paths are relative to that checkout's root. Line numbers are review-time anchors; method names remain useful after edits. Selected files are linked in the source index.

The inspection covered repository and build discovery, both imported-glyph execution designs, payment boundaries, catalog generation and persistence, live configuration, Press planning and storage, uninscription and native-table interception, command/config UI behavior, synchronization, JEI, progression, and the existing test evidence. It was not a line-by-line proof of every class or every mod combination. Arbitrary addon behavior cannot be certified from a catalog entry or a successful compilation.

Evidence labels:

- **Confirmed source defect:** a concrete problematic path is present in current source; this does not imply an in-game reproduction was run.
- **Reproduced tool defect:** exercised directly with temporary fixtures during this review.
- **Incomplete feature:** behavior or coverage needed to fulfill the reported use case is absent or deliberately limited.
- **Verification gap:** plausible failure or compatibility risk requiring a targeted reproduction before selecting a fix.
- **Already addressed:** current code contains a relevant correction; retain and validate it.

Priorities: **P1** means correctness, data preservation, authority, or a central reported workflow; **P2** means other functional inconsistency or reliability work. No confirmed P0 server-compromise finding is claimed.

### Actual build targets

- F: Java 17, official Minecraft 1.20.1 mappings, Forge 47.4.10, Ars Nouveau 4.12.7. Default Iron's compile dependency is 1.20.1-3.15.0; the `withIrons316RuntimeGameTests` profile switches compilation and runtime to 1.20.1-3.16.3. Do not repeat the stale properties comment claiming that profile still compiles against 3.15.0.
- N: Java 21, Minecraft 1.21.1, NeoForge 21.1.248, Parchment 2024.11.17, Ars Nouveau 5.13.1.1400, Iron's 1.21.1-3.16.3.
- Read each actual `build.gradle` before invoking profiles. Preserve separate loader APIs and Forge NBT versus NeoForge data components.
- Neither checkout contained a project `AGENTS.md` or `CLAUDE.md` discovered by the review. Follow the user's global agreement and the Minecraft skill; recheck for newly added project instructions when implementation begins.

## 2. The four reports: current disposition

| Feedback | What current evidence establishes | Work required |
| --- | --- | --- |
| Magma Bomb keeps draining mana while its ground effect exists | F catalogs it as a **LONG projectile spell**, not a continuous spell. Its native 3.16.3 implementation creates `FireBomb`, which creates `FireField`. The inspected spell/entity methods do not contain a repeating mana debit. ANS can legitimately bill repeated Ars deliveries and continuous pulses, but that does not establish the reported ground-effect drain's cause. No full-lifetime Magma Bomb regression exists in the inspected tests. | Reproduce and trace the complete effect lifetime; distinguish a native persistent effect from repeated imported spell executions. See CAST-01. Do not claim this report fixed from a generic Heal test. |
| Addon spells and global maximum-level configuration are not converted | Current 3.4.1 reads live maximums/schools and supports startup addon manifests. The bundled catalog still covers only 111 base Iron's spells. Its usual registered envelope ends at 10; higher base glyph identities need an expanded catalog and restart. Real Iron's addons were not exercised in the recorded 3.4.1 runs. | Finish usable addon packages/adapters and level-range/tool contracts. Keep the fixes already present. See CATALOG-01/02 and SAVE-01. |
| Ars spells cannot be unbound through Iron's table; ritual appears broken | Table extraction is deliberately suppressed for proxies to avoid generating an unusable scroll without the Ars payload. The old ritual classification problem is fixed in current code. The ritual removes **all ANS inscriptions** on one item, not one selected spell. Some player-facing text incorrectly says “remove one.” | Preserve the classification fix; provide safe selected-entry recovery and accurate destructive-ritual wording. See UNBIND-01. |
| Switching mana mode requires cheats | Both command trees put `/ans mode set` behind permission level 2. Both config screens allow the integrated-server host to mutate settings without that permission check. This is a confirmed inconsistency between entry points, not a requirement of mana routing itself. | Allow the single-player owner to change world configuration without cheats, retain multiplayer authority, and centralize safe application. See CONFIG-01. |

## 3. Preserve the corrections already present

These should not be implemented again or removed while fixing adjacent behavior:

1. Live Iron's maximum, enabled state, school, learning requirement, and rarity snapshots; pending/not-synced states; dormant identity preservation.
2. All 777 released 3.4.0 base identities, plus the current 1,110 registered identities and append-only ledger. Preserve the different released item IDs on each loader.
3. Startup catalogs from mod files and `config/ars_n_spells/glyph_catalogs/`, bounded optional-source loading, and the catalog-package tool.
4. Live maximum enforcement at root admission, one native level-modification evaluation per admitted occurrence, and NeoForge event-adjusted channel levels.
5. Native free-recast admission and release of unspent reservations.
6. Operation-specific ritual classification: `UNINSCRIPTION` checks ANS inscriptions before interpreting a mixed book as a native spell source. Both loaders have actual brazier lifecycle tests for mixed books and stray items.
7. Zero-catalyst recipes, Forge custom catalyst insertion, Forge job-term revalidation, startup readiness waiting, and Source refund handling.
8. Direction-restricted packets, menu/session/revision checks, request throttling, server-derived casting targets, and existing native Touch repairs.

The 3.4.1 docs explicitly retain some design choices: a final cast-event discount cannot rescue imported root admission that could not afford the initial reservation; Forge and NeoForge have different augment limits and execution mechanisms; levels beyond the registered envelope require a restart. These are not newly discovered defects. Where this brief calls for better compatibility, update the contract and tests together instead of silently changing those choices.

## 4. Prioritized work list

| ID | Priority | Status | Main area |
| --- | --- | --- | --- |
| CAST-01 | P1 | Report unresolved; verification gap | Full-lifetime mana accounting for persistent effects |
| CAST-02 | P1 | Confirmed source defects | Forge continuation cancellation and completion |
| CAST-03 | P1 | Confirmed missing revalidation; runtime consequences to test | Delayed/charged/channel execution after policy or target changes |
| CAST-04 | P1 | Confirmed source defects and lifecycle gaps | NeoForge exception cleanup, cooldowns, native setup |
| CAST-05 | P1 | Confirmed source defect | NeoForge pending-charge emission budget |
| PAYMENT-01 | P1 | Confirmed divergent paths; compatibility incomplete | Alternate-resource imported payments |
| UNBIND-01 | P1 | Incomplete feature; confirmed documentation mismatch | Selected spell extraction and safe removal |
| CONFIG-01 | P1/P2 | Confirmed authority/UI inconsistency | Cheat-free owner configuration and atomic application |
| CATALOG-01 | P1 | Incomplete feature | Actual addon coverage and supported level envelopes |
| CATALOG-02 | P2 | Reproduced tool defects; parser gaps | Catalog promotion, minimum levels, schemas, assets |
| SAVE-01 | P1 | Confirmed log-only safeguard; data-loss risk | Missing addon identity recovery |
| PROGRESSION-01 | P2 | Confirmed source defect | Rewards before payment succeeds |
| SYNC-01 | P2 | Publication gap and confirmed bound mismatch | Coherent client behavior snapshots |
| VIEW-01 | P2 | Confirmed cache invalidation gap; retention risk | JEI reload and connection lifecycle |
| DOCS-01 / QA-01 | P1 for completion | Incomplete evidence and inconsistent claims | User instructions and behavioral validation |

## 5. Casting and payment work

### CAST-01 — Reproduce and eliminate post-cast billing of persistent native effects

**Evidence:** F `compat/irons/glyph/IronsGlyphInvoker.java:158` starts charged spells through Iron's native initiation; `glyph/EmissionPayment.java:62` settles individual imported emissions; N `glyph/runtime/GlyphRuntime.java:111` creates charged continuations. Neither of those structures proves whether the report occurs on a particular recipe or addon combination. The inspected 3.16.3 bytecode identifies Magma Bomb as LONG and shows the `FireBomb` → `FireField` chain. Evidence files from this review are listed in section 12.

**Required accounting contract:** a successful imported Magma Bomb launch incurs its applicable Ars parent cost and one native spell execution cost. Its projectile impact, floor damage, affected targets, visual ticks, and eventual entity removal must not reopen or settle that execution again. A native continuous spell may charge its actual native pulses. A separate Ars resolver invocation caused by an intentional repeating form is a new emission under the documented budget. A native recast follows native free-recast semantics. Do not implement “one payment per spell ID/player forever”; that would make separate casts and channels free.

Implementation instructions:

1. Add a focused runtime regression before changing payment code. Test the released 3.4.0 behavior and the current working build with identical saved recipe, mode, rates, equipment, and upstream versions where feasible.
2. Record a bounded timeline containing root session, occurrence, emission, native cast attempt/pulse, debit unit and amount, event-final level, and effect entity creation/removal. Correlate events with identities rather than relying on an action-bar mana display. Reuse `ManaTrace`, `CrossCastTrace`, and transaction records; keep normal logging quiet.
3. Record both raw native mana pools and routed/displayed values. Disable natural regeneration and unrelated equipment effects for the baseline, then repeat with those enabled. A ceiling clamp, a UI resync, and a new debit need distinct evidence.
4. Start with plain Self → Magma Bomb and Touch/Projectile → Magma Bomb. Then use the reported recipe if available, including Burst, Split, Delay, lingering/repeating forms or addon forms. Count actual bombs/fields, not merely resolver callbacks.
5. Trace through launch, impact, empty floor ticks, several targets entering/leaving the field, the last damage tick, entity removal, and at least 40 ticks afterward. Do the equivalent test for one other persistent native spell and one true continuous spell.
6. Repair the specific duplicate settlement, stale cast slot, replayed continuation, or pool synchronization path exposed by the trace. Put idempotency at the cast/emission boundary. Persistent native entities should not carry a live billing callback unless the native spell's explicit lifecycle requires one.
7. Preserve already-created persistent effects when bookkeeping closes. Ending a billing session must not automatically delete a legitimate fire field whose natural lifetime continues.

**Acceptance:** exactly one imported debit for a single Magma Bomb execution; no additional debits across the full ground-effect lifetime; correct channel pulse totals; two deliberate casts charge twice; cancellation before emission refunds unspent holds; no refund after an effect starts. Exercise every mana mode, unequal conversion rates, creative free casts, shortage, recast, both loaders, and supported alternate-resource routes. If the report cannot be reproduced, leave it explicitly unresolved with the tested matrix and instrumentation—do not invent a root cause.

### CAST-02 — Make Forge continuations cancellable until resumption and close completed sessions

**Confirmed source defects:** F `glyph/GlyphContinuation.java:54` sets `finished = true` before submitting its resume task. `cancel()` at line 69 then cannot cancel that queued work. The callback checks a removed target entity, but not whether the owner/session died, logged out, changed dimension, expired, or was disabled. `GlyphChildren.finish()` at line 76 skips `maybeClose()` on successful completion. When the charged glyph is the last part, `suspend()` created no hold, so `resume()` schedules nothing and the session survives until timeout. This contradicts the eager-completion contract.

Implementation instructions:

1. Model at least held, queued, resumed, and cancelled states. Queueing must not be terminal. Keep a cancellation/generation token that the callback rechecks on the server thread immediately before resuming.
2. Explicitly own pending-work accounting while the resume is queued. Acquire the continuation's pending reference before clearing `childPending`, then release it exactly once after resumption or cancellation.
3. If there are no remaining recipe parts, close/evaluate completion immediately after the child ends. Do not retain an idle session for 1,200 ticks.
4. Validate session ownership, openness, caster liveness, connection, dimension, target validity, and relevant policy before invoking the remaining recipe. Invalid work must stop the context, release unspent reservations, and never run a trailing ordinary Ars effect.
5. Audit `server.tell(new TickTask(...))`: a task's tick number is not, by itself, a guaranteed next-tick delay. Use a documented tick queue or prove the timing in a runtime test. Avoid running continuation code inside native cast cleanup.
6. Make every close/finish/cancel path idempotent. Restrict native cancellation to the actual ANS-owned cast slot and attempt, not merely a matching spell ID.

**Acceptance:** repeated charged casts with the glyph last return sessions, children, carriers, delays, and reservations to baseline immediately. Queue a tail effect, then kill/disconnect/transfer the caster or disable glyphs before dispatch; the tail never executes. Test cancellation twice, target removal, a subsequent native cast, and charged → charged recipe tails.

### CAST-03 — Revalidate execution after delay, policy changes, and target changes

**Evidence:** F admission checks the augmented level against the live maximum (`glyph/GlyphAdmission.java:123`), but `glyph/GlyphInvocations.java:62` checks availability of the base definition and then uses the frozen augmented/native levels. Lowering a maximum below an already-admitted augmented level while keeping the base glyph valid is not rechecked there. F `IronsGlyphInvoker.begin()` also starts a deferred child without repeating the full glyph-policy decision. On N, `GlyphRuntime.completeCharge():179` rechecks native refusal and an entity target but not the current glyph mapping, enabled setting, or maximum. Channel ticks similarly lack the complete policy check. N `GlyphBehavior.onConfigChanged():168` republishes state without closing disabled active sessions.

N's initial `GlyphRuntime.resolve()` also lacks F's explicit world/dimension, loaded-position, world-border, and removed-target checks. Its `targetRefusal()` primarily checks hit *kind*. This is an observable difference worth correcting at the shared validity boundary; claim-mod bypass is not asserted as reproduced.

Implementation instructions:

1. Define one execution authorization result consumed by instant effects, delayed effects, charge completion, and channel pulses. Include feature availability, catalog support, current mapping restriction, requested Ars-adjusted level, native enabled/learning requirements, owner/session identity, and target liveness/world validity.
2. Keep the native-modified level frozen according to the existing per-occurrence contract, but refuse an Ars-adjusted level that is no longer allowed. Do not incorrectly cap legitimate native gear/event bonuses at the scroll maximum.
3. Choose and document a reload policy. Recommended: current policy gates any effect that has not started; already-applied effects persist; channels terminate before their next disallowed pulse. Refund only unspent work. Preserve exact payment routing for an accepted execution, or cancel before it starts when the route changes—never silently reprice it into another pool.
4. On feature disable, stop owned continuations and channels promptly on both loaders. Merely hiding glyphs or rejecting new root casts is insufficient.
5. Match the two loaders' target validation. Avoid loading chunks solely to execute a stale cast. Preserve native ownership, PvP/friendly-fire and protection-event behavior; do not bypass a native veto when repositioning spawned entities.
6. Recheck targets before each relevant delayed effect/pulse. If a removed entity is still represented by a saved `EntityHitResult`, reject it safely. Do not execute a spell against an entity in another level.

**Acceptance:** admit base level 1 + Amplify at level 5, lower the maximum to 2 before impact/completion, and assert no imported effect/debit beyond the permitted contract. Repeat with a new deny rule, Self-only rule, learning change, feature disable, unloaded target chunk, border move, dead target, and dimension transfer. Confirm a legitimate native affinity bonus above max still works when the base/augmented level is legal.

### CAST-04 — Complete native lifecycle and exception handling, especially NeoForge

**Confirmed source defects:** N `IronGlyphBridgeImpl.execute():263` pays before `onCast`, but catches a later exception as an ordinary refusal. The exception bypasses cooldown/recast completion and `child.resetCastingState()`, and the caller increments emissions only for `result.applied()`. N channel setup/ticks/pulses lack equivalent failure containment; `pulsedOnce` is assigned after `onCast` returns. N `GlyphContinuation.cancel():136` calls `channel.end(true, false)`, suppressing the cooldown even if paid pulses already happened. Exceptions or cancellation can therefore leave effects/accounting classified incorrectly or avoid a completed-effect cooldown. No production addon exception was injected during this review.

**Lifecycle completeness:** F instant execution manually runs selected hooks. N non-channel charged execution waits in an ANS continuation, then also runs selected hooks. Neither should be advertised as arbitrary-addon native-lifecycle equivalence without checking required initiation, pre-cast, tick, completion and cancellation hooks. N channels do call `onServerPreCast`; do not incorrectly report that hook absent from all N paths. F charged/channel children use the real native cast slot and need their own contention/ownership safeguards.

Implementation instructions:

1. Introduce a structured execution result distinguishing refusal before payment, paid before effect, effect started, completed, cancelled, and failure after effect start. Carry final level, payment receipt and whether cooldown/recast bookkeeping is owed.
2. Mark the effect boundary before calling native `onCast`. A partially emitted effect followed by an exception is not an unpaid refusal. Count it toward the emission budget and retain the committed payment.
3. Put cast-data/slot cleanup in guaranteed cleanup blocks, without destroying data owned by another native cast. Preserve or restore preexisting parent data on F; clean up detached N slots even after a failed condition or exception.
4. Apply the native cooldown/recast policy after an effect starts, including failure and interrupted channels with completed pulses. Do not manufacture a cooldown for a charge cancelled before any effect when native behavior would not do so. Base this decision on actual lifecycle state, not `end(..., false)` at every cancellation.
5. Contain recoverable runtime exceptions from conditions, pre-cast setup, cast events, cast ticks, pulses, delivery, packets, and cleanup. Do not swallow JVM-fatal errors indiscriminately. A failed channel must release its remaining reservation and leave the scheduler usable.
6. Inventory the pinned native lifecycle for every adapter family. Add explicit capabilities/limitations for addons needing setup data, real player `MagicData`, custom cast types, custom resources, or cancellation callbacks. Implement missing hooks in the correct order and target frame; do not fire side-effectful events twice to simulate parity.
7. Ensure an ordinary native spell running concurrently is never cancelled or reclassified as the glyph child simply because its spell ID matches. Test slot identity and reentrancy.

**Acceptance:** synthetic native spells throw before payment, during setup, after spawning an entity, during the first/later channel pulse, and in cleanup. Each has deterministic costs, cooldowns, state cleanup, and one diagnostic. Death, logout, dimension transfer, expiration, and configuration disable after a paid pulse cannot erase the cooldown. Native pre-cast data is available to a test addon that actually requires it.

### CAST-05 — Reserve emission capacity for pending NeoForge charges

**Confirmed source defect:** N `GlyphRuntime.resolve()` checks `session.emissions` at line 82, but its charged branch at line 111 starts a continuation without incrementing that count. The count advances at line 213 only after the effect returns successfully. Several split/burst deliveries can all schedule charges while the counter still says zero. `completeCharge()` does not reserve/recheck the emission limit. Creative/free casts make this particularly easy to exercise.

Implementation instructions:

1. Give an admitted emission a bounded slot/permit before scheduling work. Track pending and consumed capacity separately if useful, but enforce `pending + consumed <= limit`.
2. Release the permit for a refusal/cancellation before effect start; consume it once an effect starts, including partial failure. Native pulses inside one admitted channel need their explicitly defined budget semantics, not accidental double counting.
3. Bound continuation count independently of money and the number of root sessions. A zero-cost spell must still be bounded.
4. Avoid checking a reloadable limit at one stage and an unrelated limit at another without a documented policy. Preserve or deliberately tighten the session's agreed budget on reload.

**Acceptance:** set the budget to one; fan out a charged imported effect to multiple valid targets in a single root cast. At most one pending/started native execution is admitted. Repeat in creative, with zero mana cost, event discounts, cancelled charges, and same-tick completion. Assert the continuation count as well as effects and mana.

### PAYMENT-01 — Unify imported alternate-resource authorization and settlement

**Confirmed path divergence:** F `GlyphAdmission.java:157` and `EmissionPayment.quote():47` reserve ordinary routed mana for every imported occurrence. F charged children later return that hold and use `IronsCastPayments`, whose quote includes LP/virtue rules. Instant imports directly invoke `onCast` and never traverse that same payment wrapper. N `glyph/runtime/GlyphPayments.java:65` also constructs mana-only imported quotes. Having a native LP integration elsewhere does not prove these paths can cast from LP with no mana, or that direct `onCast` honors another mod's alternative currency hooks.

Implementation instructions:

1. Build a common payment authorization model for each supported origin/carrier/resource route. Admission, instant settlement, charge completion, and channel pulses must agree on which resources may pay.
2. Use a single owner for each debit. Native pipeline delegation must consume/transfer the authorization or explicitly release it before a new one; manual native effects must use the same resource policy without invoking a second independent debit.
3. Support existing Covenant/LP/aura integrations only where the actual pinned version can be verified. For an unsupported alternate resource, produce an actionable refusal/compatibility explanation instead of silently spending mana or running for free.
4. Handle changed event-final cost with an exact settlement and rollback before effects. Preserve terminal recovery records for failed compensation; never discard a failed refund result and proceed as though it succeeded.
5. Preserve the existing admission-discount rule unless intentionally redesigning it. The current test `ironsLoaded_payment_discountSettlesExactlyButCannotRescueAdmission` enshrines it. A future change must specify event timing and prove that cast events occur once; speculative duplicate cast events are not an acceptable discount probe.
6. Audit route changes between root admission and delayed child initiation. F `IronsCastPayments.quote():51` reads current rules, whereas the parent session holds an earlier snapshot. Select a consistent snapshot-transfer or cancellation policy; do not silently change the payer after the projectile has left.

**Acceptance:** compare native and imported casting with sufficient LP but zero mana, insufficient LP, configured native fallback, compatibility disabled/missing, virtue routing, mixed native/imported recipes, creative, recasts, and all mana modes. Assert every raw resource delta and recovery record. A cast must never require a resource that its final route does not spend.

### PROGRESSION-01 — Credit native Iron's casts only after the accepted effect boundary

**Confirmed source defect:** both `events/IronsProgressionHandler.java:30` and `IronsAffinityHandler.onIronsSpellCast()` credit `SpellOnCastEvent`. F's `MixinIronsCastPayment` explicitly prices after that event and may reject the cast at the native mana write. Thus a native attempt can gain progression/affinity even when the final price cannot be paid and `onCast` is never entered. Imported/proxy guards prevent some double credit but do not fix native rejected casts.

Implementation instructions:

1. Stage training intent from the event, then commit ANS-owned progression/affinity when the accepted native effect begins/completes under the chosen contract. Reuse `IronsCastLifecycle` rather than introducing a player-wide “last cast” flag.
2. Preserve one root-cast credit for exported/imported Ars spells. Preserve intentional native channel/recast progression semantics and document whether the unit is root cast or pulse; do not accidentally multiply training by targets or floor damage.
3. Drop staged intent on price rejection, native veto, interruption before effect, duplicate invocation, or failed identity checks. Ensure state is evicted on logout/server stop.

**Acceptance:** a listener raises the final cost beyond available funds after initiation; health/world effects, progression, affinity, and successful-cast cooldown remain unchanged. Test normal native success, imported success, exported proxy success, continuous pulses, and a native event veto.

## 6. Unbinding and configuration

### UNBIND-01 — Provide selected-entry recovery and make whole-item erasure explicit

**Already addressed:** F `InscriptionInputs.classify(..., UNINSCRIPTION):65`, its N counterpart, and both `Glyph341GameTests`/ritual lifecycle suites address mixed-book classification. Preserve this operation-specific precedence; transcription still has different source semantics.

**Remaining behavior:** F `mixin/irons/MixinInscriptionTableMenu.java:122` clears the native table result for a proxy. N has the corresponding extraction guard. F `SpellUninscriptionRitual.java:109` invokes `IronsBookBindingUtil.removeAllArsEntries():361`, which removes all ANS entries and associated native slots. F `README.md:168` nevertheless directs the player to that ritual “to remove one.” `README.md:135` and the ritual description language key claim a bit-identical fresh blank item, while the code intentionally preserves native spells, custom names, and third-party data. These descriptions can lead to unintended loss of multiple inscriptions.

Implementation instructions:

1. Implement a shared selected-entry extraction/removal service, then route the native table to it. Identify the selected native slot's actual ANS proxy/pool ID and corresponding payload; never assume the native slot index is the sidecar index.
2. Produce a valid ANS carrier scroll containing the full serialized Ars spell, mechanical school selection, names/icons/nature metadata and supported version information. Allocate a fresh proxy identity when it is subsequently bound. A naked `ars_cross_*` native scroll is never a valid extraction product.
3. Keep preview pure. Stage the result and a fingerprint of the book/selected entry. Commit removal exactly once when the result is actually taken, using the pinned native menu's extraction cost/material contract. Audit ordinary click, shift-click, number keys, throw, full inventory, menu close, stale selection, and repeated requests.
4. Mutate copies first; verify native slot removal and payload removal before committing the book and output. If the native container is malformed, a required hook is unavailable, or the data schema is newer, leave the original untouched and explain the refusal. Do not clear the sidecar after a failed native mutation.
5. Preserve other native/Ars spells, component/NBT data, names, enchantments, native locked-slot constraints and legitimate capacity. Shrink only ANS-owned empty tail capacity under the existing baseline rule. Native spells later placed above the old baseline must survive.
6. Keep the ritual as an explicit **erase all ANS inscriptions from this item** operation, unless deliberately adding a selection interface. It must not claim to return a scroll or remove only one entry. The table should offer recovery; the ritual should explain erasure before use through its tablet/notebook text.
7. Add visible table feedback while safe extraction is unavailable or refused. A silently empty result is an inadequate explanation for the reported workflow.
8. Review legacy/orphan repair. `removeAllArsEntries()` discovers proxy IDs through sidecar data; an orphan native proxy without a readable sidecar may not be classified as an inscribed item at all. Provide a bounded repair path for known ANS-owned orphan proxies without deleting real native spells or unknown future-format data.

**Acceptance:** bind at least two different Ars spells into a mixed native book through the ordinary workflow; extract one and cast/rebind the returned scroll; only that entry disappears. Then run the real brazier ritual and verify it removes the remaining ANS entries while preserving every native spell and unrelated property. Include missing payload, duplicate/orphan IDs, empty/native-only books, future schemas, eight-slot boundary, dropped stray items, multiplayer stale requests and restart persistence. Keep the existing complete ritual lifecycle tests rather than replacing them with direct helper calls.

### CONFIG-01 — Make mode changes coherent and available to the single-player owner without cheats

**Evidence:** F command permission is at `commands/ArsNSpellsCommands.java:88`; N has the same level-2 requirement. Both `client/screen/ConfigScreenFactory.java:79` use `hasSingleplayerServer()` for UI mutation permission. The UI's cycle/toggle callbacks directly change live `AnsConfig` values on the render thread; `Done` saves afterward. Closing with Escape can leave in-memory edits without the same apply/save sequence. F `saveConfig():365` only schedules `BridgeManager.refreshMode()`, whereas the F command explicitly also calls `ModeChangeCleanup.reconcileAll()`. N already invokes reconciliation inside `BridgeManager.refreshMode()`; do not report that N cleanup missing.

Implementation instructions:

1. Centralize the authority predicate: dedicated-server console/operators retain permission level 2; the owning local player of an integrated server may change their world configuration regardless of cheats. On a published LAN world, a guest does not become the owner merely because the server is integrated. Verify the exact owner-profile API on each pinned version.
2. Keep world-global mode changes server-authoritative. Do not implement a client-only mode switch or make the command unrestricted for all multiplayer users.
3. Use the same server-side apply service for command, config UI and file reload. Validate the requested mode, publish routing, reconcile modifiers/pools, handle in-flight transactions under the selected policy, refresh dependent snapshots, and synchronize affected clients. Make the operation idempotent under duplicate watcher notifications.
4. Stage UI edits in a local draft. Apply the draft on the server thread through the service; Cancel/Escape discards it. Reset changes the draft. Never change live server configuration from a render-thread button callback.
5. Ensure F GUI changes run the same complete reconciliation as commands. Remove dependency on an eventual filesystem watcher event to make an in-memory mode change correct.
6. Show requested and effective mode, the global scope of the setting, and an explicit reason when editing is unavailable. When Iron's is absent, explain the effective fallback. Expose a clear owner-accessible settings route and make `/ans mode set` consistent with it.
7. Acknowledge applied state separately from asynchronous disk-save success. Audit close-world/reopen behavior so a queued save cannot write a later world's spec accidentally or silently lose the owner's last change. Verify the pinned loader's live config broadcast behavior; add an explicit bounded ANS settings snapshot if existing synchronization does not update connected clients.
8. Localize the configuration screen's hardcoded title, labels, hints, and status messages while touching this screen. Use the same setting names in the UI, command feedback and documentation.

**Acceptance:** a survival owner with cheats disabled can change each mode through command and UI, restart and retain it; a non-op dedicated-server player and a LAN guest cannot; operator/console can. Escape leaves state unchanged. Two clients converge without reconnecting. Change mode while a charge/projectile/channel and a pending refund exist; no double debit, lost hold, stale capacity bonus, or altered unrelated native cast remains. Test every mode transition with equipment and progression modifiers active.

## 7. Catalogs, configuration ranges, and saved identities

### CATALOG-01 — Turn addon infrastructure into tested spell coverage

**Incomplete feature:** optional catalog discovery and tooling exist, but they do not automatically make uncatalogued spells castable. The shipped catalog is still base Iron's only. Existing 3.4.1 evidence explicitly says no Iron's addon was installed. Raising the global maximum to 15 does not register base-level 11–15 glyphs in a stock envelope ending at 10. This is an acknowledged architecture boundary, not a reason to ignore effective global configuration.

Implementation instructions:

1. Export a loaded native spell inventory for each selected real addon/version and loader. Classify every non-proxy spell as supported, unsupported with a specific execution reason, or intentionally excluded. Start with the addons named by the actual pack/report; Cataclysm Spellbooks and Alshanex's Familiars are examples already named in project evidence, not a certification or a requirement to guess versions.
2. Ship usable catalog packages for the supported combinations, including item models, localization and icon loading. Add adapter code where needed; a manifest that marks every spell `supported` without executing it is not completion.
3. Identify per-addon requirements: custom scroll items, cast types, targeting helpers, native setup data, real versus detached `MagicData`, extra resource systems, summons, recasts, entity lifetimes, and custom school namespaces. Implement explicit optional bridges with version-aware contracts. Keep optional classes out of common/client/server initialization when absent.
4. Continue reading effective metadata through the native API after its config is ready. Do not independently parse global JSON and reimplement precedence over per-spell overrides and native config events.
5. Decide the supported startup level envelope deliberately. At minimum deliver a working, documented path for global maximum 15 and per-spell exceptions, with unchanged identities. An optional catalog extension plus restart is valid if the workflow is complete and its limits are clearly surfaced. Do not claim that `/reload` can add registered glyph items.
6. Keep registered identity range, live allowed range, Ars-adjusted cast level, and native/event-modified cast level separate. A lowered maximum must make existing higher identities dormant; an enlarged envelope must not silently change saved glyphs' spell/level.
7. Budget catalog variants, registry size, models, atlas memory, packet size and startup work together. Current caps differ (F 4,096 variants; N 8,192). Registering 99 levels of 111 spells would exceed both. Do not “fix” support by setting one max constant to an arbitrarily high number.
8. Decide an explicit policy for native minimum levels and addon range changes; implement it consistently with CATALOG-02. Tests must use addon namespaces with equal spell/school paths to catch accidental path-only matching.
9. Document deliberate loader differences that remain, including charged cast scheduling, concurrent native casts, Amplify limits and timing augments. Close behavioral differences relevant to these reports; source-text parity alone is not the objective.

**Acceptance:** global maxima 1, 5, 10 and 15; a spell originally limited to one level; an explicit per-spell override; a maximum beyond the supported envelope; an addon spell with a nonstandard icon/resource/cast lifecycle; native disabled/learning changes; reload and restart. Verify pressing, learning, saved recipes, augments, tooltips, JEI, casting, and two clients. Publish an inventory-based coverage table with effect-level evidence, not only registration assertions.

### CATALOG-02 — Repair catalog tooling, parsing, promotion and resource completeness

There are several related defects to fix as one schema/tool contract:

1. **Reproduced:** `tools/glyph_catalog_pack.py` assigns an unassigned Forge spell the `caster` adapter with `unsupported` status. A later invocation with `--previous` and the correct `projectile` assignment exits because the adapter changed. The same pattern exists with N's `self` default. An inert placeholder can therefore obstruct the documented future-support workflow.
2. **Reproduced:** a valid tool input with `min_level = 2` produces a Forge schema-2 catalog with `levels.min = 2`, but F `glyph/ImportedGlyphCatalog.java:297` rejects any minimum other than 1. The Forge inventory exporter also enumerates rarities from level 1 while the tool slices as if the first entry belonged to the minimum, misaligning such a range.
3. **Confirmed source gap:** both catalog parsers still narrow numeric input using `getAsInt()` before strict validation. Fractional/string/overflowing values can be coerced unlike the stricter mapping schema. F accepts an `adapter_version` without validating the supported range; N validates only a positive lower bound. No compatibility dispatch for future adapter versions is established by those checks.
4. **Resource completeness gap:** loose config manifests register new identities but do not themselves supply item models. Forge's atlas source scans `gui/spell_icons`; a generated model referencing an addon's icon elsewhere needs a matching atlas/resource path. This must be verified with a real addon, not assumed from successful registration.

Implementation instructions:

1. Preserve identity as `(namespaced spell, base level)` while distinguishing never-supported placeholders from a published executable adapter contract. Permit a deliberate versioned transition from unsupported to the correct adapter without renaming identities. Reject arbitrary behavior replacement by unrelated lower-trust sources. Define source ownership, replacement/version rules and migration tests; do not simply remove every adapter conflict check.
2. Support the chosen minimum-level contract in runtime, inventory, fixtures, generator, item registration, rarity indexing, identity ledger and world record. Alternatively, if a loader must remain minimum-1-only, reject unsupported inputs before writing a supposedly usable package and state that limit. The preferred full-support solution handles the native minimum explicitly.
3. Validate JSON types and integral bounded numbers before narrowing. Validate required and unknown fields, supported schema/adapter versions, resource IDs, target/cast-kind enums, list lengths and duplicate IDs. Reject an invalid optional source whole with a source/field explanation; retain required-source startup failure.
4. Validate a generated package against the same schema/limits as its target loader **before** producing final output. Write a complete temporary package then publish it atomically. Add fixtures for unsupported→supported promotion, unchanged IDs, non-1 minima, extreme maxima, fractional/string/overflowing numeric fields, unknown adapter versions, duplicate IDs, and cap overflow.
5. Verify loose-manifest and catalog-JAR install modes independently. Either provide a runtime model/icon fallback for each new identity or require/generate a companion resource pack and explain its installation. Include marker behavior above level 10; never reference absent textures or pretend every higher level has its own visible marker.
6. Preserve all prior identities when retiring an addon or increasing a minimum. Avoid a migration that silently drops the lower part of a range. Recheck the N world identity record, which currently records only the maximum, before adding arbitrary minimum changes.

**Acceptance:** the package tool's output loads on the intended loader with no rejected source or missing model; promotion preserves all spell/item identities and saved recipes; inventory rarity at level N remains the rarity for N; unsupported future adapters do not execute; malformed packages produce precise errors and no partial registration.

### SAVE-01 — Protect worlds when addon catalogs disappear

**Confirmed safeguard limitation:** both `glyph/GlyphIdentityRecord.check()` methods only log missing identities and then allow normal operation. F invokes this during `ServerStartedEvent` and catches failures. The documentation acknowledges Ars can discard unregistered parts on subsequent saves. The record reports damage risk but does not preserve missing spell definitions. No world-loss experiment was performed in this review.

Implementation instructions:

1. Make addon identity installation persistent independently of the addon implementation JAR. A previously accepted identity package must remain available to register dormant glyphs when the native addon is absent.
2. Add a startup/migration check early enough to prevent normal loading/saving from erasing missing identities. Prefer restoring the already-known dormant catalog at the appropriate pre-registration stage; otherwise fail the world start with an actionable missing-package report before affected data is decoded or rewritten. Throwing an exception only after `ServerStartedEvent` is not sufficient proof of safety.
3. Retain unknown future schema/payload data without destructive normalization. Do not invent definitions for unknown native spells or reuse an ID for a different level.
4. Keep identity history append-only. Reports should include source/package information that actually identifies what to reinstall; recording only a spell and maximum cannot support a claim to know its originating package.
5. Exercise migration using a copied fixture world, not a user's live save. Verify learned glyphs, books, exported carriers, inventories, chunks containing Press jobs, and round trips after reinstalling the addon.

**Acceptance:** remove only the addon while keeping identities: glyphs become dormant and recover on reinstall. Remove/corrupt the identity package: world start is safely refused or identities are restored before loss; a later correct start has identical spell payloads and knowledge. Both outcomes must be demonstrated rather than inferred from an error log.

## 8. Synchronization, recipe viewers, and presentation

### SYNC-01 — Publish one complete accepted behavior generation

**Evidence:** F maps and live facts arrive in separate packets and are published independently by `GlyphClientSync`. Each publication can trigger a viewer refresh; no common publication token ties the two halves together. `GlyphLiveSnapshot.read():172` permits fewer entries than the catalog contains, overwrites duplicate spell entries, and trusts the supplied digest instead of checking the reconstructed content. `GlyphAvailability` can then fall back to catalog defaults for missing facts. These are coherence/validation gaps, not a demonstrated player-to-server exploit: the packets are correctly registered S2C.

**Confirmed bound mismatch:** F's live snapshot supports up to 1,024 spells but its decoder caps the school string table at 256 (`GlyphLiveSnapshot.java:52`). The writer does not enforce that bound. A sufficiently diverse supported addon catalog can be valid on the server yet produce an undecodable client snapshot. Similar byte/count combinations need auditing across both loaders.

Implementation instructions:

1. Publish mappings, live facts and dependent settings as one immutable accepted generation, or stage packet parts under a common server generation and swap only once complete. Include catalog identity and connection identity; discard old/incomplete publications cleanly.
2. Require a complete, unique entry set for a READY snapshot. Validate flags, IDs, counts, value ranges, supported versions and content digest before publishing. Retain the last valid snapshot or an explicit not-synced state on failure; do not silently promote fallback defaults to authoritative facts.
3. Align encode/decode limits and catalog limits. Bound total bytes as well as list counts and strings. Preflight payload size before sending; use a deliberate chunk/assembly protocol or a smaller admissible catalog if necessary. Avoid a catalog that passes startup validation but disconnects every joining client.
4. Invalidate tag-dependent consumers after the applicable tag update. Hashing a tag's *name* does not identify its *members*. Refresh Press preview, insertion rules, recipe viewers and any cached availability from the same accepted state.
5. Verify live ANS setting changes reach existing remote clients, including the mode/feature flags used to derive local availability. Shared single-player statics are not evidence of remote synchronization correctness.

**Acceptance:** two remote clients with editor/Press/JEI open converge after maximum, school, tag, recipe and enablement changes. Outdated menu requests refuse without consumption. Test missing/duplicate snapshot entries, invalid digest, delayed half-publication, disconnect/reconnect to a differently configured server, and maximum-size catalogs with more than 256 distinct schools where supported. Rejected input cannot partially overwrite accepted state.

### VIEW-01 — Correct JEI cache invalidation and lifecycle retention

**Confirmed source defect:** F `GlyphPressJeiRecipe.key():67` includes glyph, tier and terms but not resolved catalyst members. `reuse():72` returns the old object containing old catalyst stacks after a tag-only reload. `ArsNSpellsJeiPlugin.onRuntimeUnavailable():101` does not clear `known` or recipe `registered` flags, which belong to one JEI runtime. This also risks treating a recipe as already registered in a later runtime where it was never added.

**Retention risk:** N `ArsNSpellsJeiPlugin.refresh():74` hides all old recipes and creates/adds a fresh set on every refresh. F caches every distinct set of terms indefinitely. Measure retained recipe objects before claiming a bounded memory design; hiding is not the same as removing an object from JEI's indexes.

Implementation instructions:

1. Include all material recipe inputs in equivalence, including resolved catalyst item/component sets and relevant live learning/presentation fields. On a tag change, update the actual ingredient index, not just tooltip text.
2. Scope recipe registration state to a particular JEI runtime and connection lifecycle. Clear/reinitialize caches when that runtime ends. Do not carry `registered=true` into a new recipe manager.
3. Avoid duplicate additions for unchanged recipes. Use supported viewer replace/remove/unhide operations with a bounded strategy for genuinely changed recipes; profile the pinned JEI implementation where API semantics are uncertain.
4. Coalesce updates so one logical accepted generation produces one refresh. N `GlyphPressRecipes.entries():76` still populates `requiresLearning` from the catalog entry rather than live behavior; use the authoritative effective requirement.
5. Keep category viewing functional with Iron's absent, before sync, and when all spells are dormant. Recipe-viewer failures must not alter server Press behavior.

**Acceptance:** replace the only member of a custom catalyst tag, reload, and verify JEI shows/indexes the replacement and stops offering the old item. Connect to another world/server without restarting the JVM. Repeat 100 unchanged reloads and repeated changed-term cycles; report visible recipe counts, retained objects and memory behavior. No accumulating hidden duplicates or missing recipes after runtime recreation.

## 9. Whole-mod regression boundaries

The concrete findings above do not justify rewriting every integration. Run focused checks for changed paths and preserve these broader contracts:

| System | Required regression checks |
| --- | --- |
| Native Ars and Iron's casting | Plain native casts still obey their own target, cast-time, interruption, cost, recast and cooldown rules; successful and rejected casts train correctly. |
| Cross-cast carriers | Native Ars books, Iron's books, native scrolls, ANS carrier scrolls, wheel/hotkey/staff paths, generic inscribed items, offhand and selected-entry changes; no duplicate client/server activation. |
| Targeting/protection | Self affects the caster, Touch miss is free, Sensitive water behavior survives, projectiles retain ownership, summons are owned correctly, teleports use safe loaded destinations, protection vetoes remain effective. |
| Mana/equipment | All five modes; distinct pool capacities and conversion rates; zero/above-ceiling pools; equipment, Curios, potions and progression modifiers added/removed without compounding. |
| Alternative resources | Supported LP/aura paths, missing/incompatible integrations, exact refunds, insufficient funds, disconnect/reload during a hold; no unbounded compatibility promise. |
| Glyph Press | Zero/custom catalysts, shift-click/hoppers, lock simulation, full output, Source conservation, cancel/reload/restart, changed live rarity/terms, config readiness and block removal. Preserve the current explicit Source-dissipation-on-block-break policy unless intentionally changing it. |
| Spell Loom / inscription | Pure previews, explicit conversion of filled scrolls, full output, future formats, preservation of names/school/cosmetics, server request freshness and output exactly once. |
| Networking | Direction guards, bounded decoding, menu/session/revision ownership, rate limiting, replay, stale container IDs, item swaps, spectators and disconnects. Expand tests only where the new extraction/config/snapshot paths change the surface. |
| Persistence | Death/respawn/dimension transfer, book/capability/component round trips, older saves, missing optional mods, late refund ownership and repeated load/save without erosion. |
| Client and packaging | Dedicated-server class loading, optional Iron's/JEI absent, packaged mixins/resources, icons and language fallback, editor visibility, actual installable JAR boot. |

There was no fresh evidence from this review of a defect in every row. Treat these as acceptance boundaries for the implementation, not fabricated findings. Avoid unrelated formatting, documentation campaigns, or broad refactors.

## 10. DOCS-01 — Reconcile player-facing instructions with implemented behavior

Update documentation as the corresponding work lands; do not advertise proposed behavior as available.

1. Correct the unbinding descriptions immediately: selected extraction versus erasing all ANS inscriptions, scroll recovery versus deletion, preservation of native spells and unrelated data. Remove “bit-identical fresh blank” where it is false.
2. Explain mana-mode ownership and scope consistently in `/ans` help, config screen, README and notebook. Cheats must not be the recommended workaround once CONFIG-01 is implemented.
3. Publish a real addon support matrix: loader, addon/version, native spell IDs, adapter/status, known limitation and tested scenario. Distinguish infrastructure, catalog registration, and proven effects.
4. Explain live maximum versus registered envelope and a complete level-15 expansion/install/restart workflow. Never silently downcast an unsupported scroll or equate “not yet synced” with “spell unsupported.”
5. Review `docs/3.4.1/casting-contract.md`: Forge executes continuous spells through native children even though it has no separate channel adapter family. Wording such as “Channel pulses. NeoForge only” is misleading. Describe actual loader behavior and remaining differences.
6. Repair stale build statements, including the 3.16 compile profile comment, and cross-check `README.md`, loader dependency ranges, coverage docs, generated package guides and notebook keys against the same final implementation.
7. Update diagnostic records for charged/channel work. F initially records `ACCEPTED_PENDING` with no settled price and does not turn that into a complete child-cast receipt. N normal execution records the requested level even when the cast event changes it. `/ans glyph last` must distinguish pending, paid, effect-started and final outcomes, and show the actual committed level/price.
8. Preserve released translation keys where compatibility matters. Add aliases/migrations deliberately; do not rename hundreds of unrelated keys simply to make a parity script green.

## 11. Implementation sequence and completion criteria

### Ordered implementation

1. **Capture the current baseline.** Preserve all dirty work; read existing tests/contract docs and record dependency/runtime profiles. Add targeted failing behavioral tests for confirmed paths and the Magma Bomb reproduction harness.
2. **Repair lifecycle accounting together.** CAST-02 through CAST-05 and PAYMENT-01 share execution boundaries. Establish owned pending work, emission permits, cancellation, effect-start state and receipt semantics before expanding addon execution. Resolve CAST-01 using the evidence, not a guessed blanket suppression.
3. **Fix native training commitment.** Land PROGRESSION-01 against the corrected effect boundary.
4. **Complete unbinding and configuration UX.** UNBIND-01 and CONFIG-01 should use server-owned mutation services with precise outcomes. Preserve normal native table handling for ordinary Iron's spells.
5. **Finish catalog/schema/persistence support.** CATALOG-02 and SAVE-01 precede new production addon catalogs; then CATALOG-01 packages real tested coverage and complete higher-level workflows.
6. **Make clients converge.** SYNC-01 and VIEW-01 consume the final server behavior contract; update affected UI/diagnostics and docs.
7. **Run the warranted runtime matrix.** Reuse unchanged valid checks, investigate failures, then boot packaged artifacts and exercise multiplayer/client workflows. Produce concise final evidence and any honestly unresolved compatibility limits.

### Required new regression scenarios

Tests should observe effects, inventory, exact resource deltas and lifecycle counters. A test that calls only a helper or checks source text is not enough for these reports.

| Scenario | Required observable outcome |
| --- | --- |
| Magma Bomb complete lifetime | One execution debit, persistent damage still works, no post-launch billing |
| Charged glyph last in recipe | Immediate session cleanup after completion |
| Cancel queued Forge tail | No tail effect after session/owner invalidation |
| Max/policy changes mid-flight | Disallowed unstarted work refuses and releases only its unspent resources |
| Feature disable during NeoForge channel | No further pulses; correct cleanup/cooldown |
| Native effect throws after emission | Payment retained, budget consumed, native cleanup/cooldown correct |
| Channel cancellation after pulse | No cooldown bypass or leaked native effect entity |
| Split charged glyph at budget one | No more than one admitted pending execution |
| Alternative resource with zero mana | Supported route casts and bills its intended resource, or explicitly refuses unsupported compatibility |
| Final native price unaffordable | No progression/affinity credit or successful effect |
| Extract one of multiple Ars book entries | One valid returned carrier, all other entries/data intact |
| Ritual erase of a mixed book | Every ANS entry removed; native spells retained; wording accurately describes all-entry erasure |
| Cheats-disabled owner / LAN guest | Owner can change mode; unauthorized guest cannot |
| Escape from edited config screen | No live change or persistence |
| Global max 15 with extension | Exact level-15 identity presses, learns, saves, syncs and casts |
| Unsupported catalog promotion | Correct new adapter without lost or reassigned identities |
| Missing addon catalog | No destructive save; recovery preserves all glyphs |
| Tag-only JEI reload and reconnect | Correct ingredients and bounded registration state |
| Partial/oversized/mismatched snapshot | Safe rejection with no partial publication |

### Definition of done

- Each P1/P2 entry has a disposition: implemented and behaviorally validated, demonstrated already correct, or a precisely documented external blocker. Merely adding an explanation is not closure for a confirmed correctness defect.
- The four user reports each have a reproducible scenario and final evidence, or an explicit unresolved status with the missing reproduction information. Do not declare “all bugs fixed” from the existing broad suite.
- Existing released identities, books, glyph knowledge and unrelated item data survive migration.
- Multiplayer authority and optional-mod boundaries hold on actual dedicated runtimes.
- The installable JAR, not only the development classpath, boots for each claimed profile. No publishing/upload/merge/release action is implied by this review request; those require their own authorization.
- Final implementation delivery lists changed files, exact checks and results, evidence paths, and any remaining work. Do not rewrite historical test records to imply checks were newly run.

## 12. Validation observed during this review

### Commands actually run

| Checkout | Command | Observed result |
| --- | --- | --- |
| F | `/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh /home/otectus/Projects/ars-n-spells test --offline` | PASS; `:test UP-TO-DATE`. Existing XML totals: **1,017 tests, 0 failures/errors/skips**. The tests were not re-executed. |
| N | `/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh '/home/otectus/Projects/1.21.1 Ports/ars-n-spells_1.21.1' test --offline` | PASS; `:test UP-TO-DATE`. Existing XML totals: **1,022 tests, 0 failures/errors/skips**. The tests were not re-executed. |
| F | `python3 -m unittest discover -s tools -p 'test_*.py'` | **51 tests actually ran; OK.** |
| N | `python3 -m unittest discover -s tools -p 'test_*.py'` | **45 tests actually ran; OK.** |
| F | `python3 tools/verify_loader_parity.py --neoforge '/home/otectus/Projects/1.21.1 Ports/ars-n-spells_1.21.1' --section behaviour` | PASS: **37 fixture cases, 41 identical fixture files, 12 identical tool modules, 1,110 catalog and ledger identities agree**. This is shared-fixture parity, not complete runtime parity. |
| F tools | Temporary Python fixtures importing `glyph_catalog_pack.build` | Reproduced unsupported-adapter promotion rejection and generated minimum-level incompatibility described in CATALOG-02. No repository fixtures/source were modified. |
| F dependency | `javap -c -p` on the mapped Iron's 1.20.1-3.16.3 JAR for `MagmaBombSpell`, `FireBomb`, and `FireField` | Confirmed LONG cast type and persistent field creation chain in the inspected bytecode. No full gameplay reproduction. |

Review-time logs:

- `/tmp/gradle-ars-n-spells-test-20261109-133340.log`
- `/tmp/gradle-ars-n-spells_1.21.1-test-20261109-133611.log`
- `/tmp/ans-review-magma-3163.txt`
- `/tmp/ans-review-firebomb-3163.txt`
- `/tmp/ans-review-firefield-3163.txt`

These temporary files are local review evidence and may not survive environment cleanup. Existing historical runtime evidence is in each checkout's `docs/3.4.1/testing.md` and the referenced `logs/3.4.1/final/`. It reports Forge's 283 and NeoForge's 419 required GameTests, including many gated/unexercised optional scenarios. Those runtime runs were **not repeated** for this review.

No new game client, GameTest server, dedicated server, external modpack, or packaged-JAR install was launched. No Magma Bomb drain, world-loss migration, real-addon execution, or multiplayer UI scenario was reproduced in-game. The brief deliberately keeps those gaps open.

### Proposed implementation validation commands

Use the Minecraft skill's quiet wrapper for Gradle. Run builds serially in a checkout. Use fresh isolated run directories for different mod profiles; never load a world written with Iron's into an absent-mod profile merely to test fallback.

```bash
# Run from the Forge checkout. Changed tests will rerun; use `test --rerun`
# when a genuinely fresh test execution of an unchanged suite is required.
/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh "$PWD" test --offline
python3 -m unittest discover -s tools -p 'test_*.py'

/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh "$PWD" runGameTestServer --offline -PgametestRunDir=run-review-f-absent
/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh "$PWD" runGameTestServer --offline -PwithIronsRuntimeGameTests -PgametestRunDir=run-review-f-315
/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh "$PWD" runGameTestServer --offline -PwithIrons316RuntimeGameTests -PgametestRunDir=run-review-f-316

# Run from the NeoForge checkout.
/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh "$PWD" test --offline
python3 -m unittest discover -s tools -p 'test_*.py'
/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh "$PWD" runGameTestServer --offline -PgametestRunDir=run-review-n-absent
/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh "$PWD" runGameTestServer --offline -PwithIronsRuntimeGameTests -PgametestRunDir=run-review-n-loaded
```

Inspect available runtime flags before adding real addon profiles. Pin exact mod versions/hashes and required dependencies. Optional tests that self-skip do not count as coverage. For every runtime run, invoke `tools/verify_gametest_log.py` with the **actual updated** required/executed/skipped counts and explicit present/absent mods; require its success and inspect unexpected exceptions/mixin failures. A Gradle exit code alone is not a passing Minecraft run.

After source/resource changes, run each checkout's `build`, inspect packaged metadata/mixins/catalogs/models, and boot the produced installable artifact. Use `check_mod.py` for the supported Forge resource checks when registrations/assets/lang change. Its 1.20.1 assumptions are not proof of NeoForge 1.21.1 failures; use actual target-version runtime/resource evidence there. Exercise a dedicated server with two remote clients for mode changes, table extraction, reload convergence and recipe-viewer lifecycle. Keep test worlds disposable and separate from user saves.

## 13. Source index

Links below target the reviewed local files. The F/N prefixes and named methods in the findings provide portable lookup information if the checkout moves.

- [F imported invoker](/home/otectus/Projects/ars-n-spells/src/main/java/com/otectus/arsnspells/compat/irons/glyph/IronsGlyphInvoker.java:72), [F admission](/home/otectus/Projects/ars-n-spells/src/main/java/com/otectus/arsnspells/glyph/GlyphAdmission.java:101), [F emission payment](/home/otectus/Projects/ars-n-spells/src/main/java/com/otectus/arsnspells/glyph/EmissionPayment.java:62).
- [F continuation](/home/otectus/Projects/ars-n-spells/src/main/java/com/otectus/arsnspells/glyph/GlyphContinuation.java:54), [F child completion](/home/otectus/Projects/ars-n-spells/src/main/java/com/otectus/arsnspells/glyph/GlyphChildren.java:76), [F invocation validation](/home/otectus/Projects/ars-n-spells/src/main/java/com/otectus/arsnspells/glyph/GlyphInvocations.java:37).
- [N imported execution](</home/otectus/Projects/1.21.1 Ports/ars-n-spells_1.21.1/src/main/java/com/otectus/arsnspells/compat/irons/glyph/IronGlyphBridgeImpl.java:263>), [N channel](</home/otectus/Projects/1.21.1 Ports/ars-n-spells_1.21.1/src/main/java/com/otectus/arsnspells/compat/irons/glyph/IronGlyphChannel.java:58>), [N runtime](</home/otectus/Projects/1.21.1 Ports/ars-n-spells_1.21.1/src/main/java/com/otectus/arsnspells/glyph/runtime/GlyphRuntime.java:59>), [N continuation](</home/otectus/Projects/1.21.1 Ports/ars-n-spells_1.21.1/src/main/java/com/otectus/arsnspells/glyph/runtime/GlyphContinuation.java:82>).
- [F native payment boundary](/home/otectus/Projects/ars-n-spells/src/main/java/com/otectus/arsnspells/mixin/irons/MixinIronsCastPayment.java:29), [F native progression listener](/home/otectus/Projects/ars-n-spells/src/main/java/com/otectus/arsnspells/events/IronsProgressionHandler.java:30).
- [F table extraction guard](/home/otectus/Projects/ars-n-spells/src/main/java/com/otectus/arsnspells/mixin/irons/MixinInscriptionTableMenu.java:122), [F uninscription ritual](/home/otectus/Projects/ars-n-spells/src/main/java/com/otectus/arsnspells/rituals/SpellUninscriptionRitual.java:47), [F all-entry removal](/home/otectus/Projects/ars-n-spells/src/main/java/com/otectus/arsnspells/spell/IronsBookBindingUtil.java:361).
- [F mode command](/home/otectus/Projects/ars-n-spells/src/main/java/com/otectus/arsnspells/commands/ArsNSpellsCommands.java:88), [F config screen](/home/otectus/Projects/ars-n-spells/src/main/java/com/otectus/arsnspells/client/screen/ConfigScreenFactory.java:79), [N reconciliation inside routing refresh](</home/otectus/Projects/1.21.1 Ports/ars-n-spells_1.21.1/src/main/java/com/otectus/arsnspells/bridge/BridgeManager.java:139>).
- [Catalog package tool](/home/otectus/Projects/ars-n-spells/tools/glyph_catalog_pack.py:84), [F catalog parser](/home/otectus/Projects/ars-n-spells/src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphCatalog.java:280), [N catalog parser](</home/otectus/Projects/1.21.1 Ports/ars-n-spells_1.21.1/src/main/java/com/otectus/arsnspells/glyph/catalog/ImportedGlyphCatalog.java:236>), [F world identity record](/home/otectus/Projects/ars-n-spells/src/main/java/com/otectus/arsnspells/glyph/GlyphIdentityRecord.java:52).
- [F live snapshot decoder](/home/otectus/Projects/ars-n-spells/src/main/java/com/otectus/arsnspells/glyph/behavior/GlyphLiveSnapshot.java:172), [F client publication](/home/otectus/Projects/ars-n-spells/src/main/java/com/otectus/arsnspells/client/GlyphClientSync.java:36), [F recipe cache](/home/otectus/Projects/ars-n-spells/src/main/java/com/otectus/arsnspells/compat/jei/GlyphPressJeiRecipe.java:67), [N recipe refresh](</home/otectus/Projects/1.21.1 Ports/ars-n-spells_1.21.1/src/main/java/com/otectus/arsnspells/compat/jei/ArsNSpellsJeiPlugin.java:74>).
- [Existing 3.4.1 test evidence](/home/otectus/Projects/ars-n-spells/docs/3.4.1/testing.md), [identity contract](/home/otectus/Projects/ars-n-spells/docs/3.4.1/identity-and-levels.md), [casting contract](/home/otectus/Projects/ars-n-spells/docs/3.4.1/casting-contract.md), [package workflow](/home/otectus/Projects/ars-n-spells/docs/3.4.1/catalog-packages.md).

External primary reference: Forge's configuration documentation distinguishes CLIENT, COMMON and SERVER configuration and their synchronization roles. Use that distinction when implementing CONFIG-01; it does not authorize a remote client to mutate the server's config, and it does not by itself prove that a particular runtime save broadcasts every ANS setting. [Forge 1.20.1 configuration documentation](https://docs.minecraftforge.net/en/1.20.1/misc/config/).
