# Touch casting regression (3.4.0, NeoForge 1.21.1)

## The report

An Ars spell whose form is **Touch**, carried on a scroll, did nothing when cast. The report did not say which kind of scroll. 3.4.0 tested every carrier route separately (see "Evidence").

## Reproduction

The failing test came first. `TouchCastGameTests` was written before the fix, and a temporary trace line in `CrossCastingHandler.castArsSpell` (removed afterwards) logged which resolver entry each carrier cast used. The Iron's-loaded GameTest run `run-340-touch-repro` failed exactly the three Touch carrier tests that existed then. The excerpt is in `evidence/touch-repro-before-fix.txt`:

```
castArsSpell player=touch_harm form=ars_nouveau:glyph_touch resolverEntry=onCast accepted=false
genericcarrier_touchharm_hurtsthelivingtarget failed ... health 10.0 -> 10.0
castArsSpell player=touch_generic form=ars_nouveau:glyph_touch resolverEntry=onCast accepted=false
genericcarrier_touchbreak_breakstheblockinfront failed ... Expected Air, got Dirt
castArsSpell player=touch_scroll form=ars_nouveau:glyph_touch resolverEntry=onCast accepted=false
ironsloaded_exportedscroll_touchbreak_breakstheblockinfront failed ... Expected Air, got Dirt
3 required tests failed
```

In the same run, Self and Projectile carrier casts (`form=ars_nouveau:glyph_self`, `glyph_projectile`) were accepted and resolved. The native Ars spell book control, Touch → Break, broke its block.

Of the possible failure stages (no request, request refused, wrong resolver entry, event veto, effect without a target), the trace and the controls place this one at the **wrong resolver entry**. The request reached the server, was admitted, and was dispatched to the one resolver entry Touch cannot use.

## Cause

- For every carrier cast, `CrossCastingHandler.castArsSpell` built a `SpellResolver` and called `resolver.onCast(stack, level)`.
- In Ars Nouveau 5.13.1, `MethodTouch.onCast` always returns `FAILURE`. A Touch spell resolves only through `onCastOnBlock` or `onCastOnEntity`.
- The cast request (`CrossCastRequestPayload`) carried no target or aim, so the server had nothing to dispatch on.

Every Touch spell on a Spell Loom export, a generic inscribed item, or a bound Iron's book therefore failed wherever the player aimed. It showed no message and charged nothing. Ars's own spell book did not fail, because `AbstractCaster.castSpell` picks a target and calls the block or entity entry itself.

## Fix

**One target-aware dispatch.** `spell/ArsCastDispatcher` decides the resolver entry for every carrier cast, the same way Ars's own spell book does:

| Cast | Resolver entry |
| --- | --- |
| A living entity is targeted | `onCastOnEntity(stack, entity, hand)` with the real caster, tool and hand |
| A block is targeted, or Sensitive with no block in reach | `onCastOnBlock(new UseOnContext(player, hand, hit))` |
| Otherwise | `onCast(stack, level)`, unchanged for Self, Projectile and addon forms |
| Touch with no valid target | refused before the resolver: "Nothing in reach to touch.", nothing charged, no cooldown |

The Spell Loom path (`CrossCastingHandler`) and the native wheel and staff path (`ArsCrossProxySpell`, which passes the hand Iron's cast from) both use the dispatcher.

**The server picks the target.** `spell/target/CastTargetResolver` ray-traces from the player:

- reach is `0.5 + block_interaction_range` for blocks, and Ars's 25-unit entity trace for entities;
- Sensitive uses a fluid-inclusive pick, as in Ars;
- a target in another dimension, an unloaded chunk or outside the world border is refused.

**The client's aim is only a hint.** `CrossCastRequestPayload` now carries a `CastTargetHint`: the aim plus the block or entity the client claims to see. The server checks the claim:

- a claim within reach and tolerance is confirmed;
- otherwise the target is rebuilt from the client's aim, at most 15° from the server's view;
- otherwise the hint is stale and the server's own pick decides.

A claim is never trusted as a hit. A forged entity or block claim cannot reach something the server would not pick. Protection, adventure-mode and friendly-fire checks remain Ars's own, because the effect resolves through Ars's resolver.

**One execution authority per click.** The client's `RightClickItem` handler captures the hint and sends the request. For an inscribed carrier, `RightClickBlock` suppresses the carrier item's own use-on action, so one click is one cast. The block's own interaction keeps precedence, as in vanilla and Ars: a crafting table still opens. Sneak-right-click cycles a carrier's inscriptions and never casts. The client sends one request per click, and the request-id guard, carrier fingerprint and burst limit refuse duplicates and replays.

Nothing else changed:

- carriers stay reusable;
- native Iron's scrolls keep their own consumption rule;
- payment still goes through `ArsCastPayments`;
- old carriers need no rebuilding, because no item data changed.

The network protocol moved from 8 to 9, because the request gained the hint.

**Found while verifying (also fixed in 3.4.0).** `MixinSpellResolverPreCast`'s return hook named its targets `"onCastOnBlock"` without a descriptor. Mixin binds a bare name to one overload only, and it bound `onCastOnBlock(BlockHitResult)`. The `UseOnContext` overload, which Ars's own player casting and the dispatcher use, never ran ANS's per-cast cleanup when it returned; a tick-end sweep released the hold instead. The hook now lists all four resolver entries with full descriptors and `require = 4`.

## Deliberate differences from Ars's own spell book

- **Occlusion.** Ars's spell book can pick an entity through a block, because its entity trace ignores blocks. ANS does not: if a block is nearer than the entity, the block is the target. The `touchObstructedEntity_isNotHurt` scenario covers this.
- **Sneak on block entities.** Ars's spell book Touches a block entity only while sneaking. ANS uses sneak to cycle a carrier's inscriptions, so the rule is not mirrored. A block with its own interaction (a chest, a crafting table) still takes the click first. A block entity without one is touched without sneaking.

## Evidence

`TouchCastGameTests`, 28 scenarios. Each checks the world effect (a broken block, a hurt mob, a projectile in flight), not a `true` return:

| Group | Scenarios |
| --- | --- |
| Carriers | `ironsLoaded_exportedScroll_touchBreak_breaksTheBlockInFront`, `ironsLoaded_exportedScroll_touchHarm_hurtsTheLivingTarget`, `genericCarrier_touchBreak_breaksTheBlockInFront`, `genericCarrier_touchBreak_withoutHint_usesTheServerPick`, `genericCarrier_touchHarm_hurtsTheLivingTarget` |
| Controls | `control_nativeArsSpellbook_touchBreak_breaksTheBlock`, `control_selfHeal_stillResolvesOnTheCaster`, `control_projectileHarm_stillLaunchesAProjectile` |
| Targeting | `touchMiss_isRefusedWithoutCastOrPayment`, `touchOutOfReach_doesNotBreakTheBlock`, `touchObstructedEntity_isNotHurt`, `touchSensitive_resolvesInAir_whereAPlainTouchIsRefused` |
| Iron's routes | `ironsLoaded_wheelHotkey_touchBreak_breaksTheBlock`, `ironsLoaded_staffRightClick_touchHarm_hurtsTheTarget`, `ironsLoaded_curiosBook_touchBreak_breaksTheBlock`, `ironsLoaded_wheelTouchMiss_startsNoProxyCooldown` |
| Input authority | `eventOverlap_entityClick_castsExactlyOnce`, `eventOverlap_blockClick_castsExactlyOnce_andSuppressesTheCarrierUseOn`, `duplicateRequestId_castsOnce`, `freshIdSpam_isBoundedByTheBurst`, `staleFingerprint_isRefused`, `forgedEntityClaim_cannotHitAnUnreachableEntity`, `forgedBlockClaim_cannotBreakAnUnseenBlock`, `wrongDimensionClaim_isRefused`, `unloadedTargetClaim_isNotTouched`, `handMismatch_isRefused`, `deadSender_isRefused`, `offhandCarrier_neverSendsASecondCast` |

`CastTargetHintTest` (JUnit) covers the hint's wire format and its handling of malformed claims.

The real-client check on a dedicated server is in `testing.md` ("Client on a dedicated server"). It used actual right-clicks: Touch → Break from a Spell Loom export broke the aimed block, and Touch → Harm hurt the aimed pig.
