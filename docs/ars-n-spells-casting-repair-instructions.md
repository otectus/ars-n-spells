# Ars ’n Spells — Iron’s Casting Failure Repair Instructions

## Assignment

Act as a senior Minecraft mod engineer. Investigate, reproduce, and correct the reported casting failure in **both** `main` and `port/neoforge-1.21.1`, preserving each branch’s existing integrations and gameplay policies. Implement the smallest coherent fixes that address the failure and the related accounting, cancellation, cooldown, and lifecycle problems described below. This is a repair task, not authorization for an unrelated rewrite.

Player report:

> Hi! I've run into some issue with your mod. It seems whenever I run it, it makes me unable to cast spells, at least via Iron's spellbook. I go through the animations and all the windup but then nothing happens. Sometimes the spells cooldown activates, sometimes it doesn't.

**Success means an actual server-side spell effect, the correct resource debit, and the correct cooldown and item outcome. Animation, windup, a successful build, or a displayed cooldown is not proof of successful casting.**

Do not “fix” this by always returning success, making mana effectively unlimited, disabling payment verification, deleting native prerequisites, or disabling integration by default.

## 1. Evidence, scope, and starting points

### Reviewed source snapshots

| Branch | Reviewed commit | Declared ANS version | Development baseline |
|---|---|---|---|
| `main` | `d805b45fb629d9ae30b97ccc6b3487438c103942` | `3.3.3` | Minecraft 1.20.1, Forge 47.4.10, Java 17; Ars Nouveau 4.12.7 indicated by the pinned dependency and its comment |
| `port/neoforge-1.21.1` | `41fac17065c381104b17fdaab307d89ba21b49ab` | `3.3.3` | Minecraft 1.21.1, NeoForge 21.1.248, Java 21 toolchain; Ars Nouveau 5.13.1.1400; Iron’s `1.21.1-3.16.3` |

Sources: [S01], [S02]. Main pins Iron’s through CurseMaven file ID `7402504`; resolve and inspect the artifact rather than inferring its version from a stale source comment. The main payment mixin still describes “3.15.0” ordering, while the port describes “1.21.1-3.16.3.” [S03], [S04]

Upstream Iron’s source was inspected at `e4056af90302d37eb1739f5ff05020b020e6e252`; its properties declare `1.21.1-3.16.3`. Source version strings do not prove byte-for-byte equivalence with a downloaded release JAR. Verify the actual runtime bytecode on each loader. [S05]

**Evidence limit:** this investigation inspected repository source and ran an isolated IEEE-754 arithmetic probe. It did not launch Minecraft, apply a production patch, or run the repository’s Gradle/GameTest suites. The player’s installed ANS version, loader, configuration, spell IDs, and mod list were not provided. Do not claim one unique cause has been reproduced in that player’s environment.

Read the current checkout’s repository instructions, `MODMAP.md`, build files, testing documentation, relevant change history, and existing tests first. Refresh the branch heads and record any differences from these snapshots. Preserve uncommitted work. Use separate worktrees or equivalent isolated checkouts; do not reset, stash, or overwrite unrelated changes automatically.

### Important branch difference

The payment logic is **not identical across loaders**. Main’s `IronsCastPayments` includes Sanctified Legacy/LP, virtue-aura exemptions, insufficient-LP behavior, and alternative-payment failure policies. The inspected port has a simpler native-mana plan. Preserve these intentional differences; do not replace main’s handler wholesale with the port’s implementation or silently promise missing port integrations. [S06], [S07]

## 2. What the source establishes

### F1 — Confirmed late failure path and lost diagnostics

Both `MixinIronsCastPayment` implementations inject after the native `SpellOnCastEvent` dispatch, before the native payment/effect body proceeds. When `IronsCastPayments.commit(...)` returns false, the mixin invokes `Utils.serverSideCancelCast(player)` and cancels `castSpell`. A spell can therefore complete its windup yet never reach `onCast`. [S03], [S04]

`NativePayment.settle(...)` rejects insufficient resources and observed debits that differ from the quoted amount. It returns a result containing an ID, reason, failure unit, debited legs, and refunded legs. The callers discard that information and return false. This hides the distinction between ordinary insufficient mana, a redirected or cancelled debit, a clamp, and accounting error. [S06]–[S09]

**Conclusion:** this is a concrete failure path matching the report, not proof of which payment condition the player encountered.

### F2 — Confirmed cancellation side effects explain cooldown variation

In the inspected Iron’s source, the one-argument `Utils.serverSideCancelCast(player)` requests cooldown when the current spell is `CONTINUOUS`. Other cast types do not request cooldown through that default. The downstream cancellation handler applies that cooldown when casting is active. [S10], [S11]

The same handler removes a continuous-cast scroll when the source is `SCROLL`, independently of its `triggerCooldown` argument. It then invokes `onServerCastComplete(..., true)`. ANS currently uses this general interruption path even when payment fails before the first successful effect. [S04], [S11]

Consequences to reproduce: an unsuccessful first channel pulse can incur cooldown; a full-cost continuous scroll can be consumed without an effect. Simply changing the helper call to `serverSideCancelCast(player, false)` does **not** by itself solve scroll consumption.

Do not globally eliminate cooldown on interrupted channels: a channel that already produced paid effects may legitimately incur it. Distinguish first-effect payment rejection from interruption after successful output.

### F3 — Confirmed exception-safety gap during reservation

`AttemptLedger.reserve(...)` debits each leg into a local list and only publishes the reservations to `CastAttempt` after the entire loop. There is no surrounding compensation guard. If the second debit throws, the first debit has already happened but is absent from the attempt’s recorded reservation. `NativePayment.settle(...)` does not repair that exception path. [S08], [S12]

`CastLedger.BridgeResourceAccess.debit(...)` also returns zero immediately if `consumeMana(...)` returns false; it does not perform its after-read in that branch. A bridge that mutates and then reports failure can therefore hide an actual debit. Iron’s bridge catches `Throwable`, so inspect failures after an upstream mutation, not just failures before one. [S13], [S14]

These are source-level accounting weaknesses. Establish their reachability with fault-injected adapters and real integration tests; do not describe a specific third-party mod as responsible without evidence.

### F4 — Confirmed refund-accounting weakness

`AttemptLedger.settle(...)` marks a reservation released before issuing credits, then forgets the attempt after the credits return. Partial credits are reported but do not preserve an unresolved refund obligation. A throwing credit can also leave the attempt marked released, preventing a straightforward retry. [S12]

Distinguish “release requested” from “all observed debits have been compensated.” A cancelled spell must not be reported fully refunded when a clamp, unavailable player, integration veto, or exception prevented credit.

### F5 — Confirmed precision-sensitive false rejection, demonstrated in an isolated model

`ArsNativeBridge.getMana(...)` narrows Ars’s current mana to `float`. The ledger subtracts those narrowed before/after readings. `NativePayment` compares the result against the double quote using:

```java
Math.max(0.001, Math.ulp((float) leg.amount()) * 2)
```

The tolerance depends on the **cost**, not the precision of the observed **balance**. [S08], [S13], [S15]

An isolated model of these conversions demonstrates:

```text
Actual double-backed Ars balance: 32768.001
Quoted debit:                     1
Actual debit:                     1
Before read narrowed to float:    32768.0
After read narrowed to float:     32767.001953125
Measured debit:                   0.998046875
Existing tolerance:               0.001
Result:                           rejected, despite the correct debit
```

The probe also rejects a correct one-mana debit at `65536.002`; a `1000 → 980` control passes. These are high-balance edge cases, not evidence that the player had those balances. They establish that the current comparison is not generally reliable.

Do not repair this with a large arbitrary tolerance: that could accept a genuinely cancelled debit or free cast.

### F6 — Confirmed clamp/refund hazard; pack-specific trigger remains unproven

Iron’s `MagicData.setMana(...)` posts `ChangeManaEvent` and clamps stored mana against `MAX_MANA`; the clamp can still run after event cancellation. ANS’s Iron’s adapter deducts through this API, while its hot-path ceiling repair currently runs only in `HYBRID`. [S14], [S16], [S17]

When a stale or inconsistent ceiling exists, observed “payment” can include unrelated surplus destruction, and a refund through the same capped API may not restore it. For example, an unresolved current balance of 1500 under a 1000 ceiling can lose 500 on a nominal ten-mana debit, then reject the cast; a capped refund cannot recover the surplus. This is an illustrative inconsistent-state scenario, not a reproduced default installation.

Investigate mode transitions, transient modifiers, equipment changes, login/respawn, and recalculation ordering. Respect intentional native caps; do not raise maxima to arbitrary balances simply to make a transaction pass.

### F7 — Confirmed lifecycle/identity limitations; reproduce consequential failures

`IronsCastPayments` stores a plan by `MagicData` and validates reuse primarily by spell ID. The plan lacks a distinct cast-attempt token, stored source/level identity, and per-channel-effect sequence. `isCrossCast` reads the stored plan; a fallback plan constructed only within `commit` is not stored for that later lookup. [S06], [S07]

Test same-spell consecutive casts, re-entrant integrations, direct casting API entry points, recasts, source changes, cancellations, and configuration changes. These limitations warrant targeted regression tests; they do not alone prove that ordinary sequential native casts currently reuse stale plans.

### F8 — Validation scope and cooldown timing require hardening without breaking compatibility

`MixinIronsCastValidation` opens a synthetic-mana scope at HEAD, closes it at RETURN, and may convert a mana-failure result into success after ANS affordability checks. The scope’s own documentation explicitly acknowledges exceptional/early exits and uses an identity check plus a 50 ms timeout as mitigation. It is single-slot rather than nested. Do not misreport it as having no leak guards. [S18], [S19]

The timeout is not a lexical cleanup guarantee. Test an exception or another mod’s cancelling HEAD injection followed immediately by a read of the same `MagicData`. Do not let synthetic validation mana reach persistence, payments, or sync.

Separately, the payment mixin applies ANS category cooldown immediately after payment acceptance, before native `onCast`; this call does not receive `triggerCooldown`, and can run repeatedly for a channel. Native Iron’s effects, recast updates, and native cooldown processing follow their own ordering. Review and test the intended category policy rather than assuming “payment accepted” means “effect succeeded.” [S04], [S20], [S21]

## 3. Reproduce and instrument before changing semantics

Obtain the installed JAR names/hashes, loader, Java version, server and client versions, complete mod list, relevant server/world configuration, and server/client logs. Record concrete spell IDs and levels, source item, game mode, equipment/curios, native resource values, native maxima, and selected ANS mode.

Start with an **ordinary, unmodified Iron’s spellbook containing a native Iron’s spell**, not an exported Ars proxy. Use the same known-valid target and position for all comparisons. Establish:

1. Iron’s plus required dependencies, without ANS.
2. Ars Nouveau and Iron’s with required dependencies, without ANS.
3. The same installation with ANS and a new test world/default server configuration.
4. ANS in each mana mode, then the player’s actual configuration and pack.

Use a disposable fixture world. Never delete or replace a user’s saves/configuration as part of an automated test command. Recheck the resolved runtime classpath: compile-only Iron’s is not an Iron’s-loaded runtime test.

Add server-side, bounded diagnostics at initiation, the post-cost-event payment boundary, each debit/credit, effect entry, and terminal cleanup. Include:

```text
attempt ID, channel effect index, player UUID, spell ID/level, source/carrier
mode + configuration generation, pricing route, creative/recast/proxy exemptions
base cost, final event cost, quote legs, native maxima
before/requested/after/observed values for every debit and refund
payment reason, whether effect execution began, cleanup reason
native cooldown change, ANS category cooldown change, item consumption
```

Make verbose traces opt-in and rate-limited. Preserve useful failure reasons in ordinary server logs; show a concise localized action-bar failure message once per failed attempt. Do not resurrect a transaction-receipt GUI or expose private per-player state globally.

Classify the observed failure by stage: native validation, target conditions, precast-event cancellation, payment refusal, exception, missing hook, effect execution failure, or client-only presentation. Capture the **first** failure, not a later cooldown rejection caused by the first failure.

## 4. Implement a precise payment/effect boundary

### 4.1 Validate the actual mixin contract

Inspect the resolved Iron’s `AbstractSpell.castSpell` bytecode on both loaders, including its event dispatch descriptor, local-variable layout, payment call, `onCast`, recast bookkeeping, and every caller that continues after a cancelled invocation.

The inspected hooks use `LocalCapture.CAPTURE_FAILHARD` and `require = 1`. Keep critical payment hooks mandatory. Do not downgrade to `require = 0` to hide a failed injection. A failure to apply a required mixin is a different diagnosis from a successfully running hook that rejects settlement. [S03], [S04]

Verify one logical invocation of ANS’s payment hook per native effect boundary, including addon spells. Use transformed-class exports/debugging in a disposable development run when needed. Do not introduce a whole-method overwrite just to simplify the patch.

### 4.2 Return structured outcomes, not only a boolean

Carry the native or alternative settlement result through `IronsCastPayments.commit` into cancellation and diagnostics. Distinguish insufficient funds, modified/vetoed debit, precision failure, ceiling inconsistency, incompatible adapter, exceptional settlement, and incomplete compensation.

Latch the intended pricing rules for the attempt. Read the **final** `SpellOnCastEvent` cost after all listeners have run. Apply conversion once. Never dispatch the event a second time just to estimate price: listeners can have side effects.

Test cost reductions as well as increases. The current admission quote precedes final cost-event adjustments; do not falsely deny a discounted cast solely because the unmodified price was unaffordable. Use a verified native preflight contract where available, otherwise clearly separate provisional admission from authoritative effect-time payment.

Retain native creative-cost settings, free/paid scroll policy, recast exemptions, delegated Ars-proxy ownership, and main’s alternative-resource integrations. A zero-cost or exempt cast is not equivalent to a failed payment.

### 4.3 Keep settlement and effect execution distinct

Model the lifecycle explicitly, using existing structures where suitable:

```text
INITIATED → VALIDATED → PAYMENT_ACCEPTED → EFFECT_STARTED → EFFECT_FINISHED
                                      ↘ ABORTED_BEFORE_EFFECT
EFFECT_STARTED → INTERRUPTED_AFTER_EFFECT / FAILED_DURING_EFFECT
```

Channel sessions additionally need per-effect payment identity and a count of successful/started effect steps. Distinguish the original paid cast from native recasts.

Prevent duplicate callbacks from charging or refunding the same step twice. A rejected step must not advance effects. An exception after a spell has partially changed the world is not safe evidence that all payment should be refunded; record that outcome and prevent duplication rather than blindly paying the player back.

Suppress only the native debit that ANS has demonstrably replaced. Test the existing `event.setManaCost(0)` strategy carefully: downstream native code can still execute a zero-delta `setMana`, which can fire events or clamp a pool. Preserve required synchronization without triggering an extra payment route or an unrelated destructive write. [S21], [S16]

## 5. Make debits and compensation truthful

Keep resource operations server-authoritative and on the server thread. Preserve `NativeManaAccess`’s player/unit-scoped, nested native bypass so measurements cannot read routed or synthetic validation values. Do not replace actual debits with HUD balances. [S22]

Before taking transaction snapshots, reconcile any ANS-owned ceiling/modifier invariants that this mode actually requires. Snapshot after reconciliation, so maintenance is not miscounted as payment. Reject an unresolved inconsistency with an explicit reason before mutation where possible.

Record each observed debit durably in the attempt **before** touching the next leg. An adapter contract must make its mutation outcome observable, even when it reports failure. Perform a protected after-read on false/exception paths when the API permits it; retain both the original and after-read errors when observation fails.

If a later leg fails, compensate earlier observed debits exactly once. Track remaining credit per leg and distinguish complete, partial, and unknown compensation. Do not mark all refunds complete before any credits execute. Do not forget an outstanding obligation merely because a player is temporarily unavailable. Bound and clean up recovery state deliberately; never invent an unverified credit amount or issue unlimited blind retries.

Respect other mods’ legitimate `ChangeManaEvent` cancellation and resource policies. A vetoed native debit must not become a free spell. Reconcile known alternative-resource ownership using that integration’s established contract, rather than bypassing the event bus for everyone.

Avoid blanket restoration of an old full balance, which can overwrite unrelated legitimate changes. Refund attributable observed debits through the correct resource adapter. Log a capped or vetoed refund as incomplete, not successful. Handle recoverable integration exceptions without broadly swallowing fatal VM errors.

### Numeric policy

For Ars, use double-precision native balances at the transactional boundary where the supported API supplies them; avoid narrowing both observations to float and then pretending their difference is exact.

For float-backed Iron’s, compare against the adapter’s representable expected post-debit balance using a documented precision policy based on the **balance and operation**, not only the quoted amount. Protect affordability and accounting with the same unit conventions.

When a cost is below representable balance resolution, choose an explicit bounded policy: higher-precision authoritative accounting with residual handling, supported range limits, or a clear rejection. Do not declare a zero observed debit successful merely because an expanded epsilon makes it fit. Add NaN, infinity, negative, overflow, zero, and saturation tests at adapter and quote boundaries.

## 6. Separate failed-payment cleanup from interruption

Implement or reuse a dedicated abort-before-effect path that preserves native cleanup and client notification without automatically charging an interruption cooldown or consuming a scroll.

For a failed first effect:

- No effect, recast advancement, native cooldown, or ANS category cooldown caused solely by the failed payment.
- No consumable loss solely because the payment failed; respect documented independent native consumption rules when the source has already been legitimately consumed.
- No lingering casting/use state, cast-data leak, or synthetic validation scope.

For a channel interrupted **after** successful paid effects, preserve the appropriate native cooldown/consumption policy and settle only the current failed/uncommitted step. Never refund earlier successful effects. For recasts, preserve the native sequence and its cooldown timing.

Audit the caller after `castSpell` returns: cancelling the callee does not necessarily stop the enclosing ticker or source-item path. Ensure a later caller cannot perform completion/consumption a second time. Handle native `onServerCastComplete` and client finish notifications exactly once.

Do not clear an unrelated existing cooldown to make a failed attempt appear clean. For ANS category cooldowns, define and test when they begin, whether channels refresh them, how recasts behave, and whether `triggerCooldown` is relevant. Prefer native post-effect boundaries where they match the documented category policy; preserve legitimate intentional differences rather than making an unsupported global change.

## 7. Bound attempt identity and validation state

Associate a plan with an explicit logical attempt, owner, `MagicData` identity, spell ID/level, source/carrier identity, and pricing-generation policy. Use a per-effect sequence for channel payment. A fallback plan must carry the same authoritative cross/native classification through cooldown handling and diagnostics; do not reconstruct that classification from a map entry that was never written.

Clean up at native reset, successful finish, cancellation, failure, logout, player replacement/death, server stop, and deliberate mode-transition handling. Verify which hooks already cover each case. Preserve an active long cast’s latched rules or cancel it deterministically; do not silently combine old quote units with new routing assumptions.

Test validation under nested calls, exceptions, another mod’s early cancellation, and a slow validation call. Replace or supplement the current timeout-based containment with a proven exception-safe invocation boundary where compatible. A stack alone is not a solution if its frames can leak.

**Preserve the compatibility rationale already in `CastValidationScope`.** The repository explicitly documents an overwrite interaction with One Mana Bar. Do not casually restore the old instruction-level redirect that this scope replaced. Validate any wrapper or alternative hook against the actual supported runtime combination. Do not expand One Mana Bar support claims beyond verified versions.

Do not manufacture a generic SUCCESS solely by recognizing a translatable “mana failure” string unless all other native restrictions for that exact implementation are known to have executed. Keep adventure-mode restrictions, learned-spell requirements, cooldowns, source restrictions, and target conditions intact. Never use synthetic affordability as permission to bypass those checks.

## 8. Required regression tests

### Contract and fault-injection coverage

Extend existing tests rather than creating a disconnected duplicate payment system. Add tests for exact funds, slightly insufficient funds, zero cost, split payments, final cost modification, a cancelled/partial/excess debit, mutation followed by false/exception, and second-leg failure after the first leg succeeds.

Exercise a full refund, partial credit, throwing credit, unavailable player, duplicate cancellation, late cancellation after commit, and retry/cleanup of an unresolved credit. Assert actual resource values and remaining obligations, not merely a success boolean.

Include the `32768.001`/one-mana numerical case, a normal-balance control, both sides of float exponent boundaries, and a large float-backed balance whose resolution exceeds the cost. The corrected code must not fix false rejection by introducing free casts.

### Runtime matrix

Run the core casting fixtures on **both** loaders with Iron’s actually present. Cover every ANS mana mode: `DISABLED`, `SEPARATE`, `ARS_PRIMARY`, `ISS_PRIMARY`, and `HYBRID`.

| Scenario | Assertions |
|---|---|
| Native Iron’s spell from an ordinary Iron’s book | Actual server-side effect; correct native/selected payment leg; correct cooldown |
| Instant and long casts | One payment/effect; cancellation during windup does not masquerade as success |
| Continuous cast, first payment fails | No effect; clean termination; no payment-failure-only cooldown/scroll loss |
| Continuous cast, later payment fails | Earlier effects remain paid; failed step has no effect; legitimate interruption semantics preserved |
| Recast spell | Correct initial payment, native recast exemptions/counts, and final cooldown timing |
| Native scroll, reusable source, converted Ars proxy | Correct ownership; no duplicate payment, refund, or consumption |
| Final cost listener raises/reduces/zeros cost | Final event price honored once; admission and settlement differences deliberate |
| Mana-change listener cancels/modifies/throws | No free effect; observed accounting and diagnostics; compensation correct |
| Maximum/equipment/mode changes around casting | No stale-ceiling destruction, wrong-unit payment, or old/new policy mixture |
| Logout, death/respawn, dimension change, reload | No stale plan or scope; no orphaned obligation; no duplicate terminal callback |
| Two simultaneous players | No cross-player plan, mana, cooldown, or scope contamination |
| Creative cost/cooldown settings | Native configuration respected rather than blanket free/paid behavior |

Include legitimate native failures: unlearned spells, invalid/missing targets, adventure restrictions, disabled spells where applicable, existing cooldowns, and insufficient resources. Those must remain failures.

Use spell-specific observable effects: entity/projectile creation, target health/effects, block/world changes, or a dedicated controlled test spell. An `onCast` entry counter helps locate the boundary but does not by itself prove that an actual native spell did something.

Run integrated-server and dedicated-server/client checks. Assert server resource state **and** the client’s eventual displayed state. Test the minimal pack first, then implicated mana/casting integrations separately, then together. In main, additionally cover existing LP/virtue/alternative-resource policies; do not require nonexistent port integrations to pass.

Run an Iron’s-absent boot and Ars-only cast test. That intentionally absent profile cannot substitute for the loaded casting suite. A supposed Iron’s-loaded suite must fail its environment check when Iron’s is missing, not silently skip every relevant test. The repository already uses an opt-in runtime profile for this distinction. [S23]

## 9. Build, parity, and delivery

Use Java 17 for main and Java 21 for the port’s compilation/runtime. Inspect available tasks and the current test guide before running commands. In each isolated worktree, the intended checks include:

```bash
git status --short
git rev-parse HEAD
./gradlew --version
./gradlew tasks --all
./gradlew clean test build
./gradlew runGameTestServer
./gradlew runGameTestServer -PwithIronsRuntimeGameTests
./gradlew runClient -PwithIronsRuntimeGameTests
```

On Windows, use `gradlew.bat`. Resolve any branch-specific run configuration from its build script; record the exact command actually executed. Keep test worlds and global configuration isolated between fixtures. Do not copy a historical “all tests passed” statement from a testing report into the new results.

For changed loader-neutral contracts and fixtures, inspect `tools/contract_parity.py`, its CLI help, and the existing CI parity job. The manifest identifies canonical shared contract files and is generated with that tool. Update it through the existing process only after intentionally matching both branches’ shared changes. Do not regenerate hashes merely to bless accidental divergence. Loader-specific payment handlers require behavioral parity tests even when not covered by the manifest. [S24]

Deliver:

1. Focused fixes and failing-before/passing-after regressions on both branches, preserving loader-specific integrations and unrelated working changes.
2. A root-cause report distinguishing the reproduced player failure, additional confirmed bugs, tested hypotheses that were ruled out, and unresolved environment-specific questions.
3. A testing report with exact versions/hashes, commands, executed test counts, logs, actual effect/payment/cooldown/item outcomes, and explicit unavailable tests.
4. A plain-language changelog describing observable fixes, plus any narrowly required compatibility-range/config migration changes. Do not bump the release version or publish artifacts unless separately authorized.

**Release acceptance:** ordinary native Iron’s book casting works in the minimal supported installation; payment failures are explainable and leave consistent resources/items/casting state; channel/recast cooldown behavior is deliberate; no free casts or duplicate refunds are introduced; Forge, NeoForge, and Ars-only operation are verified independently. When an external pack is unavailable, state that limitation instead of declaring universal compatibility.

## Source index

All references below are immutable source locations. `[Sxx]` references identify inspected evidence, not claims that its tests were executed in this investigation.

- **S01 — Main properties:** `https://github.com/otectus/ars-n-spells/blob/d805b45fb629d9ae30b97ccc6b3487438c103942/gradle.properties`
- **S02 — Port properties:** `https://github.com/otectus/ars-n-spells/blob/41fac17065c381104b17fdaab307d89ba21b49ab/gradle.properties`
- **S03 — Forge payment mixin:** `https://github.com/otectus/ars-n-spells/blob/d805b45fb629d9ae30b97ccc6b3487438c103942/src/main/java/com/otectus/arsnspells/mixin/irons/MixinIronsCastPayment.java`
- **S04 — NeoForge payment mixin:** `https://github.com/otectus/ars-n-spells/blob/41fac17065c381104b17fdaab307d89ba21b49ab/src/main/java/com/otectus/arsnspells/mixin/irons/MixinIronsCastPayment.java`
- **S05 — Upstream version:** `https://github.com/iron431/irons-spells-n-spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/gradle.properties`
- **S06 — Main payment handler:** `https://github.com/otectus/ars-n-spells/blob/d805b45fb629d9ae30b97ccc6b3487438c103942/src/main/java/com/otectus/arsnspells/casting/IronsCastPayments.java`
- **S07 — Port payment handler:** `https://github.com/otectus/ars-n-spells/blob/41fac17065c381104b17fdaab307d89ba21b49ab/src/main/java/com/otectus/arsnspells/casting/IronsCastPayments.java`
- **S08 — Port native settlement:** `https://github.com/otectus/ars-n-spells/blob/41fac17065c381104b17fdaab307d89ba21b49ab/src/main/java/com/otectus/arsnspells/casting/NativePayment.java`
- **S09 — Main native settlement, identical inspected blob:** `https://github.com/otectus/ars-n-spells/blob/d805b45fb629d9ae30b97ccc6b3487438c103942/src/main/java/com/otectus/arsnspells/casting/NativePayment.java`
- **S10 — Upstream cancellation helper:** `https://github.com/iron431/irons-spells-n-spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/api/util/Utils.java`
- **S11 — Upstream cancellation side effects:** `https://github.com/iron431/irons-spells-n-spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/network/casting/CancelCastPacket.java`
- **S12 — Attempt ledger:** `https://github.com/otectus/ars-n-spells/blob/41fac17065c381104b17fdaab307d89ba21b49ab/src/main/java/com/otectus/arsnspells/contract/AttemptLedger.java`
- **S13 — Loader resource access:** `https://github.com/otectus/ars-n-spells/blob/41fac17065c381104b17fdaab307d89ba21b49ab/src/main/java/com/otectus/arsnspells/casting/CastLedger.java`
- **S14 — Iron’s bridge:** `https://github.com/otectus/ars-n-spells/blob/41fac17065c381104b17fdaab307d89ba21b49ab/src/main/java/com/otectus/arsnspells/bridge/IronsBridge.java`
- **S15 — Ars native bridge:** `https://github.com/otectus/ars-n-spells/blob/41fac17065c381104b17fdaab307d89ba21b49ab/src/main/java/com/otectus/arsnspells/bridge/ArsNativeBridge.java`
- **S16 — Upstream mana writes:** `https://github.com/iron431/irons-spells-n-spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/api/magic/MagicData.java`
- **S17 — Ceiling repair and equipment:** `https://github.com/otectus/ars-n-spells/blob/41fac17065c381104b17fdaab307d89ba21b49ab/src/main/java/com/otectus/arsnspells/equipment/EquipmentIntegration.java`
- **S18 — Validation mixin:** `https://github.com/otectus/ars-n-spells/blob/41fac17065c381104b17fdaab307d89ba21b49ab/src/main/java/com/otectus/arsnspells/mixin/irons/MixinIronsCastValidation.java`
- **S19 — Validation scope and compatibility rationale:** `https://github.com/otectus/ars-n-spells/blob/41fac17065c381104b17fdaab307d89ba21b49ab/src/main/java/com/otectus/arsnspells/spell/CastValidationScope.java`
- **S20 — ANS category cooldowns:** `https://github.com/otectus/ars-n-spells/blob/41fac17065c381104b17fdaab307d89ba21b49ab/src/main/java/com/otectus/arsnspells/events/IronsCooldownHandler.java`
- **S21 — Upstream cast/validation/effect ordering:** `https://github.com/iron431/irons-spells-n-spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/api/spells/AbstractSpell.java`
- **S22 — Scoped native access:** `https://github.com/otectus/ars-n-spells/blob/41fac17065c381104b17fdaab307d89ba21b49ab/src/main/java/com/otectus/arsnspells/bridge/NativeManaAccess.java`
- **S23 — Main runtime profiles:** `https://github.com/otectus/ars-n-spells/blob/d805b45fb629d9ae30b97ccc6b3487438c103942/build.gradle`
- **S24 — Shared-contract manifest:** `https://github.com/otectus/ars-n-spells/blob/41fac17065c381104b17fdaab307d89ba21b49ab/contract-manifest.txt`
