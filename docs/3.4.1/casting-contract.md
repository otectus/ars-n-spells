# Imported-glyph casting contract (3.4.1)

What happens, on both builds, when an Ars spell that contains imported Iron's glyphs is cast:
which level each occurrence casts at, what is paid and when, and how a cast ends. Identity and
the live maximum are described in [identity-and-levels.md](identity-and-levels.md).

## Who can cast

Only a real player casting through a player caster, from any route that resolves an Ars spell:
a spell book, Ars staffs and scrolls, and ANS carriers (exported Iron's scrolls, inscribed items
and bound Iron's books). Turrets, runes, fake players and other non-player casters never run
imported glyphs.

## Admission: before anything is spent

Admission runs at Ars's cancellable cast event. On Forge it runs after ANS reserves the Ars leg
of the cost (`GlyphAdmission`). On NeoForge it runs at high priority (`GlyphSessions`). For every
imported glyph in the spell, in recipe order, admission checks the following:

- the glyph is available, using the live layer's dormancy;
- Iron's live configuration is built. On Forge an unbuilt one refuses with `config_pending`;
- the player has learned the spell in Iron's, where Iron's requires that now (the live rule, which
  follows the spell's school). On NeoForge, learning is checked with Iron's other native conditions
  before each execution;
- the spell is off its Iron's cooldown, or the player holds a native recast of it;
- the spell is not already pending in another of the player's casts;
- the **augmented level** is within Iron's live maximum. It is refused (`above_max`), never clamped;
- a data pack's Self-only restriction (`adapter: ars_n_spells:self`) allows the form. On NeoForge,
  the spell editor's validation refuses the combination, and so does the target check at the hit;
- the configured limits: imported glyphs per spell and active sessions per player.

The cast is admitted with every occurrence funded, or refused with none of them funded. A refusal
tells the caster why and costs nothing.

## Four levels per occurrence

| Level | What it is | When it is fixed |
|---|---|---|
| Base | the glyph's level, part of its identity | at registration |
| Augmented | the base level after Amplify and Dampen | at admission; must not exceed Iron's live maximum |
| Native | Iron's own level for the caster (`getLevelFor`: equipment affinity and `ModifySpellLevelEvent`) | once, at admission, then frozen for the occurrence |
| Final | the level Iron's `SpellOnCastEvent` settles on for one execution | per execution |

The final level is the level the payment, the effect and the client's cast packet all use. Native
bonuses may legitimately take a cast above the scroll maximum. Only the augmented level is
bounded, so lowering Iron's maximum cannot be bypassed with Amplify. Iron's
`ModifySpellLevelEvent` fires once per occurrence, not once per execution
(`ironsLoaded_levels_nativeModifiersApplyOnceAndTheEventLevelIsCommitted`, both builds).

### Augments: an intentional difference

| | Forge | NeoForge |
|---|---|---|
| Amplify | always offered; up to the live maximum (limit = live maximum − base level) | always offered; at most two levels and never past the live maximum (limit = min(2, live maximum − base level)) |
| Dampen | down to the spell's first registered level (Iron's minimum when it was catalogued) | the same |
| Timing | none | Extend Time and Duration Down (up to 3) on channelled spells; Accelerate and Decelerate (up to 2) on charged and projectile spells |

Both builds read the live maximum each time Ars asks for the Amplify limit. Ars caches only which
augments are compatible, and Amplify is always compatible, so raising the maximum takes effect
without a restart.

## Money

1. **Admission** reserves the first execution of every occurrence at Iron's price for its native
   level, all or nothing, under the player's mana mode.
2. **At the effect boundary** Iron's cast event can still change the price. If the event's price
   matches the reservation, the reservation is committed. If it differs, the reservation is
   released and the event's price is charged fresh. If that charge fails, the effect is refused
   before it runs.
3. A discount from the cast event cannot rescue a caster who could not afford the native price.
   Iron's posts its cast event at cast time, and ANS never posts it a second time to probe the
   price (`ironsLoaded_payment_discountSettlesExactlyButCannotRescueAdmission` on Forge;
   `ironsLoaded_nativeCostHookAppliesToTheGlyphLegOnce` on NeoForge).
4. A **native recast** is free and reserves nothing. Forge has worked this way since 3.4.0. NeoForge
   3.4.0 reserved the original price and so refused casters who could not afford a cast that costs
   nothing; fixed in 3.4.1 (`ironsLoaded_recast_isAdmittedWithoutItsOriginalPrice`).
5. Unspent reservations are released when the session closes.

Channelled spells pay per pulse on both builds; see [Loader differences](#loader-differences-kept-as-versioned-exceptions).

## Failure after payment

If Iron's native effect fails after its price was taken, both builds contain the failure. This covers
an exception, and also a linkage error such as a `NoSuchFieldError` thrown by an addon built against
another Iron's version. The execution reports `failed` to the caster and the failure is logged as a
warning. The price is kept, not refunded, and the spell's cooldown (or its recast) still applies, so
a failing effect cannot be retried for free in a loop. A charged spell that fails at its release is
contained in the same way, and its cast's session closes
(`ironsLoaded_failure_aLinkageErrorAfterTheEffectBeganIsContained` and
`ironsLoaded_failure_aLinkageErrorAtAChargesReleaseIsContained`, both builds). A failure before the
effect began is a refusal: nothing is charged.

## Sessions and termination

Each admitted cast has a session that holds its reservations and its pending work. It closes when
the root resolution has returned and nothing is pending. Pending work means a child cast still
charging or channelling, a projectile still carrying the spell's resolver, or an Ars delay still
waiting.

| Event | Effect on the session |
|---|---|
| everything resolved | closes at once: instant casts return to baseline immediately (Forge GameTest `ironsLoaded_sessions_instantCastsReturnToBaselineAtOnce`) |
| timeout | an expired session closes and releases its hold; Forge checks expiry every 20 ticks (NeoForge GameTest `ironsLoaded_expiredSession_closesAndReturnsItsHold`) |
| dimension change, death, logout | every session of that player closes |
| server stop | every session closes |
| `enable_iron_glyphs` turned off on a config reload (Forge) | every session closes |
| session closed while a child cast runs | on Forge, ANS cancels the child cast it started in Iron's cast slot, and never a native cast the player started |

## Loader differences, kept as versioned exceptions

- **Amplify range and timing augments.** See the table above.
- **Charged and channelled spells.** Forge runs a charged (long) or channelled (continuous) imported
  spell in Iron's own cast slot, as a child of the Ars cast: Iron's windup, cast-time modifiers,
  interruption, pulse timing, per-pulse payment and completion apply natively, and the rest of the
  Ars recipe resumes when the native cast completes. NeoForge runs it on a detached casting slot
  that shares the player's recasts and cooldowns but not their casting state. ANS's own charge waits
  the effective cast time. The `channel` adapter ticks the spell and pulses it every 10 ticks,
  paying each pulse before it runs at the level its own cast event settles on
  (`ironsLoaded_channel_pulsesUseTheirEventLevel`).
- **A native cast already in progress.** On both builds an instant imported spell leaves Iron's
  cast slot alone, so it runs during a native cast. On Forge a charged or channelled one needs the
  slot. If the slot holds the native cast that this Ars cast runs inside (an exported spell's
  proxy), the child starts on the tick after that cast ends, waiting at most 40 ticks. Any other
  native cast refuses the child (`slot_busy`) and is not cancelled, and a player runs one child at a
  time (`child_busy`). On NeoForge the detached slot never touches the player's own, so a charge or
  channel runs alongside a native cast.
- **Press revalidation.** Forge revalidates a running Glyph Press job once per behaviour
  generation, so an unrelated `/reload` neither replans nor cancels it. NeoForge re-checks the
  job's terms when it completes.
- **Cast records.** Forge's `/ans glyph last` lists the player's recent imported executions,
  charged and channelled children included. Each line gives the base, augmented, native and cast
  level, what was paid and the outcome. NeoForge's `/ans glyph sessions [player]` lists the
  player's open imported-glyph casts and their last ten outcomes. An applied execution shows the
  committed level, Iron's price as its cast event settled it and the hit, a channel pulse its
  level and cost, and a refusal its reason.

`tools/verify_loader_parity.py` lists each one-sided GameTest with its counterpart or reason.
