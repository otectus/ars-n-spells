# 3.4.0 Touch casting repair (Forge 1.20.1)

## Report

A Touch spell inscribed on an Ars 'n' Spells carrier (an exported Iron's scroll, a generic
carrier, or a bound Iron's book cast from the wheel or hotkey) did not behave like the same
spell cast from a native Ars spell book: casts aimed at a block were reported as failures,
misses trained the spell's school, and a wheel cast that hit was treated as a failed cast.

## Reproduction

The GameTests in `TouchRegressionGameTests` cast real spells through the real entry points
(item use, the cross-cast request handler, Iron's native wheel/hotkey path) at a dirt block, a
pig, water (Sensitive) and air, and assert world effects: the block broken, the mob hurt or
slowed, the water frozen, the caster healed, the projectile launched, mana and school training
observed on the player. Each test also logs `[ANS-TouchEvidence] ... effect_applied=...
reported_resolved=...` so a cast whose effect happened can be told apart from one ANS reported.

Before the repair, against Ars Nouveau 4.12.7 and Iron's 1.20.1-3.16.3
(`logs/3.4.0/touch-baseline-before-repair-3316.log`, run dir `run-340-touch-before-3316`):

```
12 required tests failed :(
[ANS-TouchEvidence] generic Touch->Break at a block: effect_applied=true reported_resolved=false
```

The failures were the block-targeted generic, exported-scroll and Sensitive cases, the Self and
Projectile casts aimed at a block, the paid-once case, the three "a miss trains nothing" cases,
the stale/forged-hint case and the resolver-overload coverage case.

## Cause (Forge build)

Three defects combined. The NeoForge port's root cause (an `onCast`-only dispatch) does not
apply to this build; these are the Forge-specific ones.

1. **Resolver return hook bound to too few overloads.** ANS finishes a cast's bookkeeping
   (settling the reserved payment, recording the result the carrier reports, cancelling or
   keeping a wheel cooldown) at the return of Ars's resolver entry. The 3.3.x hook named its
   targets without descriptors (`"onCast", "onCastOnEntity", "onCastOnBlock"`), and Mixin binds
   a bare name to only one overload, so `onCastOnBlock(UseOnContext)`, the block entry a native
   book uses, was never hooked. A cast entering there resolved its effect while ANS saw no
   return and reported it as failed. On the wheel path that failure cancelled the native
   cooldown.
2. **Carriers did not pick targets the way a native book does.** Generic carriers and the
   Iron's proxy handed Ars a spell without the target Ars itself would have picked, so the
   block or entity entry was not always chosen.
3. **Training credited before the form resolved.** School affinity and progression listened
   to Ars's `SpellCastEvent`, which Ars posts before the form runs. A Touch spell that hit
   nothing still trained its school.

## Repair

- **One target-aware dispatch.** `ArsCastDispatcher` is the single place an ANS carrier hands
  an Ars spell to the resolver. The server makes its own pick with Ars 4.12.7's casting reach
  (`SpellUtil.rayTrace` with the caster's block reach and Ars's margin, Sensitive's fluid
  picking, Ars's own living-entity rule) and selects the resolver entry exactly as a native
  book does: entity, block (`onCastOnBlock(UseOnContext)`), or the generic `onCast`. A block
  entity that owns right-click defers to the block. A Touch spell with nothing in reach is
  refused before the resolver, so nothing is posted, reserved, paid or trained.
- **Client aim is a hint, never authority.** Cross-cast requests carry the client's crosshair
  pick (`TargetHint`, fixed-size on the wire). The server compares it with its own pick and
  records it as agreed or stale; a stale or forged hint never redirects a cast. Network
  protocol bumped `7 → 8`.
- **All four resolver entries are hooked.** `MixinSpellResolverPreCast` injects at the return
  of `onCast`, `onCastOnEntity`, and both `onCastOnBlock` overloads with `require = 4`, so a
  missing overload fails mod loading instead of silently skipping bookkeeping.
- **Training moved to the spend.** Affinity and progression credit at the resolver's mana
  expenditure, which only a cast that actually resolved reaches.
- The Iron's proxy (`ArsCrossProxySpell`) passes the real casting item (staff or book) and the
  `NATIVE_WHEEL` origin, so wheel, hotkey and staff casts use the same dispatch.

Old carriers benefit in place: no carrier data changed, so existing scrolls and bound books
work without being rebuilt.

## Evidence after the repair

Same profile and fixtures (`logs/3.4.0/touch-after-repair-3316.log`, run dir
`run-340-touch-after-3316`):

```
All 222 required tests passed :)
[ANS-TouchEvidence] generic Touch->Break at a block: effect_applied=true reported_resolved=true
```

The Touch suite stays in every later full run; see `testing.md` for the final counts.

## Covered scenarios

Native-book controls; generic carrier at a block and at an entity; Sensitive at water; Self
heal and Projectile aimed at a block; a miss trains and spends nothing; paid exactly once;
out of reach; stale and forged hints; duplicate request ids; vanilla item-use overlap; offhand
and dead senders; a stale carrier fingerprint; exported scroll at a block and an entity; bound
book via hotkey and staff; a wheel miss starts no cooldown; and the resolver hook's coverage of
both block overloads.

## Not covered by automation

Real mouse/keyboard input on a live client and a dedicated server with a remote client were not
exercised; the GameTests drive the same server entry points the input packets reach.
