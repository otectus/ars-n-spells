# Ars 'n' Spells: a missed projectile locks its Iron's glyph

Handoff for a coding agent. Repo `github.com/otectus/ars-n-spells`, branch `main` (Forge 1.20.1, mod version 3.4.1, unreleased), commit `6c26c54`. All paths are relative to `src/main/java/com/otectus/arsnspells/` unless they start with a top-level folder.

**Status of this document.** The diagnosis comes from reading the source at that commit, plus Ars Nouveau's 1.20 source for the projectile. Nothing was compiled or run: the code blocks below are sketches to adapt, and step 3 asks you to reproduce the bug before changing anything.

## 1. The report

A player wrote:

> when i miss a spell with an iron glyph inside and it flies off i can't use it anymore

That is: an Ars spell with the Projectile form and an imported Iron's glyph, cast in survival, misses and flies into open air. Further casts of that spell are refused.

## 2. Root cause

The refusal is deliberate code that lasts far longer than intended when the projectile hits nothing.

1. Admission reserves the Iron's price of each imported glyph when the Ars cast starts (`glyph/GlyphAdmission.java:184`). The reservation is redeemed when the glyph resolves at a target.
2. Admission refuses any cast containing an Iron's spell that an earlier cast of the same player still holds unredeemed (`glyph/GlyphAdmission.java:130`, via `GlyphSessions.spellPending` at `glyph/GlyphSessions.java:148` and `GlyphCastSession.pendingCooldownFor` at `glyph/GlyphCastSession.java:143`). The player sees `message.ars_n_spells.glyph.refused.pending`: "%s is still in flight from an earlier cast." The refusal cancels the whole Ars cast (`casting/ArsCastPayments.java:145`).
3. The hold ends only when the session closes. For a projectile cast that is when the projectile leaves the world (`GlyphSessions.onEntityLeave`, line 255) or when the session's 60-second lifetime runs out (`LIFETIME_TICKS = 1200`, line 53; checked once a second in `onServerTick`, line 349).
4. A projectile that hits nothing does not leave the world. Ars removes it only after `max_lifespan` seconds, default 60 (`EntityProjectileSpell.tick()` and `MethodProjectile.getProjectileLifespan()` in Ars Nouveau).

So one miss into open air blocks every spell containing that Iron's spell for about 60 seconds, and holds the reserved mana for the same time. A projectile that hits a block is fine: it resolves, is removed, and the session closes at once.

Two things explain why this was never caught:

- GameTest players are creative by default (`gametest/CrossCastGameTests.java:345`). A creative cast is free, reserves nothing, and so is never "pending". The lock exists only for a paying caster.
- No GameTest fires a projectile that misses, and none asserts the `refused.pending` path.

The lock exists for a real reason: Iron's cooldown starts when the effect runs (`compat/irons/glyph/IronsGlyphInvoker.java:116`), so without it a player could launch several projectiles before the first lands and get several effects for one cooldown. The fix has to keep that protection.

## 3. Reproduce first

In a survival world with Ars Nouveau, Iron's Spellbooks and this mod:

1. Make an Ars spell: Projectile, then any instant imported glyph (Firebolt I, for example). Cast it at the sky.
2. Cast it again. Expected on current `main`: red action-bar text "… is still in flight from an earlier cast", and `/ans glyph last` shows an admission-stage record `refused message.ars_n_spells.glyph.refused.pending`.
3. Wait about a minute. The spell works again.

If step 2 shows a different refusal, or the spell never comes back, stop and report what you see instead of applying the fix below. The diagnosis would be wrong or incomplete.

## 4. The fix

### 4.1 Behaviour to reach

- A cast is never refused because an earlier cast of the same Iron's spell is still travelling or delayed. When the new cast is admitted, what the earlier cast still holds for that spell is handed back. The earlier delivery stays live: if it does arrive, it pays at that moment like any later execution, or is refused for a shortage.
- Iron's cooldown is enforced where the effect would run. An execution that arrives while the spell is on a cooldown this cast did not start is refused before any effect, with the existing message `message.ars_n_spells.glyph.refused.cooldown`, and gives back anything it holds.
- A charged or channelled child still blocks a second cast of its spell, as today. Iron's has one cast slot.
- Within one cast nothing changes: a split, pierce, linger, wall, or a second occurrence of the same glyph in the recipe still runs even though the first execution started the cooldown.

The second point also closes a hole that exists today. Admission is the only place the cooldown is checked, so these already produce two effects for one cooldown:

- Launch the Ars projectile, then cast the same spell natively from an Iron's spellbook before it lands.
- Any occurrence with no reservation is never "pending", so several can be in flight together: Covenant's aura route (`GlyphRoute.AURA_NATIVE`), the LP death-penalty option, and any free quote.

### 4.2 Change 1: admission hands back the earlier hold

`glyph/GlyphCastSession.java`

- Replace `pendingCooldownFor(ResourceLocation)` with a check that looks only at children:

```java
/** A charged or channelled child of this cast is running or waiting for {@code spell}. */
boolean childPendingFor(ResourceLocation spell) {
    for (Occurrence occurrence : occurrences.values())
        if (occurrence.childPending && occurrence.definition.spell().equals(spell)) return true;
    return false;
}
```

- Add the set that change 2 reads:

```java
/** Iron's spells this cast has begun executing: a cooldown on one of them is this cast's own. */
final java.util.Set<ResourceLocation> started = new java.util.HashSet<>();
```

`glyph/GlyphSessions.java`

- Replace `spellPending` with `childPending(UUID player, ResourceLocation spell)` built on `childPendingFor`.
- Add the release. It must return the money exactly the way `close` does (line 283 onward), so the ledger and the payment timeline stay consistent:

```java
/**
 * A newer cast of {@code spell} is being admitted: hand back what earlier casts still hold for it.
 * Their deliveries stay live. One that still arrives settles its price at the effect boundary like
 * any later execution, and meets the cooldown check there.
 */
static void releaseHoldsFor(ServerPlayer player, ResourceLocation spell) {
    for (GlyphCastSession session : forPlayer(player.getUUID())) {
        for (GlyphCastSession.Occurrence occurrence : session.occurrences.values()) {
            if (occurrence.childPending || !occurrence.holding() || !occurrence.definition.spell().equals(spell)) continue;
            CastAttempt reservation = occurrence.reservation;
            if (reservation != null && !reservation.state().isTerminal())
                CastLedger.cancel(reservation, CastLedger.forPlayer(player));
            if (occurrence.lpLeg != null) EmissionPayment.releaseLp(occurrence, session.owner, player, player.getServer());
            occurrence.redeemed = true;
        }
    }
}
```

Setting `redeemed` is what makes this safe. `EmissionPayment.firstReservation()` (line 306) then returns null, and `payMana` / `payLp` take their existing "later execution" branch and charge fresh at the effect boundary (`EmissionPayment.java:184-203`). No new payment path is needed.

`glyph/GlyphAdmission.java`

- Line 130: refuse with `refused.pending` only when `GlyphSessions.childPending(...)` is true.
- After the per-glyph loop and before the session-count check at line 134, call `GlyphSessions.releaseHoldsFor(serverPlayer, definition.spell())` for each found glyph. It goes before the count check because a session that holds nothing no longer counts in `activeFor`, and before the reservation loop so a low-mana caster gets the missed cast's mana back in time to pay for the new one.
- If the admission is refused after that point (session count, shortage), the earlier holds stay released. That is acceptable: those deliveries pay on arrival.

### 4.3 Change 2: cooldown at the effect boundary

`glyph/GlyphInvocations.java`, in `resolve`, after the target check and before `session.budget().tryConsume()` (line 76):

```java
// Admission no longer keeps a second cast of this spell out while an earlier one is unresolved,
// so the cooldown is enforced here, where the effect would run.
IronGlyphBridge bridge = IronGlyphBridges.get();
ResourceLocation spellId = definition.spell();
if (!session.started.contains(spellId) && !bridge.hasRecast(player, spellId)
        && !bridge.creativeNoCooldown(player) && bridge.onCooldown(player, spellId)) {
    GlyphSessions.payment(player, session, occurrence).releaseIfUnpaid();
    GlyphSessions.record(player, new GlyphSessions.CastRecord(spellId, definition.level(), occurrence.augmentedLevel,
        occurrence.nativeLevel, -1, "nothing", "refused message.ars_n_spells.glyph.refused.cooldown", "execution",
        player.level().getGameTime()));
    refuse(player, definition, "message.ars_n_spells.glyph.refused.cooldown");
    return;
}
```

After the bridge call returns, mark the spell as started for every outcome except `REJECTED` and `CANCELED`:

```java
if (outcome.status() != IronGlyphBridge.InvocationOutcome.Status.REJECTED
        && outcome.status() != IronGlyphBridge.InvocationOutcome.Status.CANCELED)
    session.started.add(spellId);
```

Points that are easy to get wrong:

- The exemption is per session and per spell id, not per occurrence. A recipe may hold the same glyph twice, and one occurrence can execute many times (split, pierce, linger, wall).
- Do not use `occurrence.emissions` as the "has executed" signal. Line 95 increments it for rejected executions too.
- The recast and creative exemptions mirror admission (`GlyphAdmission.java:127-129`). Keep them identical.
- Put the check in `GlyphInvocations.resolve` only. `GlyphExecutionPolicy.refusal` also runs before each channel pulse and each charged release; a cooldown test there would cut running children short.

### 4.4 Text and documents

- `src/main/resources/assets/ars_n_spells/lang/en_us.json:755`: `refused.pending` now only describes a child. Suggested: "%s is still being cast from an earlier spell."
- `docs/3.4.1/casting-contract.md`: in "Admission", replace the bullet "the spell is not already pending in another of the player's casts" with the new rule (only a charged or channelled child blocks; an earlier unredeemed hold for the same spell is released). Add the effect-boundary cooldown rule under "Money" or "Sessions and termination".
- Javadoc that states the old rule: the class comments of `GlyphAdmission`, `GlyphCastSession` and `GlyphInvocations`.
- `CHANGELOG.md`, section `## [3.4.1] - Unreleased`, under "Fixed". Two entries in the file's existing player-facing style: a missed projectile no longer blocks its spell for a minute; an imported glyph that lands while its spell is on cooldown from another cast does nothing and costs nothing.

## 5. Tests

Add to `gametest/ImportedGlyphGameTests.java`, following `ironsLoaded_survival_importedLegIsChargedOnce` (line 238) for a paying caster: `aimingPlayer`, `setGameMode(GameType.SURVIVAL)`, `fundArs`, `clearCooldowns`. Every test must use a survival player, or it cannot see the bug. Discard stray `EntityProjectileSpell` entities at the end of each test so nothing flies for 60 seconds after it.

| Test | Setup | Asserts |
|---|---|---|
| `ironsLoaded_projectileMiss_theSpellCanBeCastAgainAtOnce` | `lookAtSky`, cast Projectile + instant glyph twice | Second cast admitted (no `refused …pending` record, two projectiles exist). `GlyphSessions.activeFor` is 1. Mana spent is two Ars legs plus one Iron's leg. **Must fail on current `main`.** |
| `ironsLoaded_twoFlightsOfOneSpell_onlyTheFirstLandingRuns` | Pig in front, cast Projectile + Chain Lightning I twice back to back | One `execution` record applied, one refused with `refused.cooldown`. Iron's leg charged once. Spell on cooldown. `activeFor` returns to 0. |
| `ironsLoaded_cooldownStartedInFlight_refusesTheLanding` | Cast at the pig, then put the spell on Iron's cooldown before it lands (add a helper to `IronsGlyphTestSupport`) | No effect on the pig, cooldown refusal recorded, Iron's leg refunded. |
| `ironsLoaded_sameGlyphTwiceInOneCast_bothRun` | Touch, then the same instant glyph twice, at a pig | Two applied executions. Guards the per-session exemption. Passes on current `main`. |
| `ironsLoaded_chargingChild_stillRefusesItsSpell` | Start a long cast as in `ironsLoaded_longFireball_holdsTheRecipeUntilItsNativeCastCompletes` (line 299), cast the spell again while it charges | Refused with `refused.pending`. |

Run the first test against unmodified `main` and confirm it fails before making the change.

## 6. Also needs addressing

**CI and tool bookkeeping.** New GameTests break two pinned checks until updated:

- `.github/workflows/ci.yml` pins `--required 344` (lines 99 and 134) and per-profile `executed` / `skipped` counts (matrix at lines 68-77, and line 134). Take the new numbers from the actual run logs. Read `tools/verify_gametest_log.py` for what each number counts rather than assuming.
- `tools/verify_loader_parity.py` requires every Forge-only GameTest to be listed in `FORGE_ONLY_GAMETESTS` (line 63) with a reason, until a NeoForge counterpart exists.
- `docs/3.4.1/testing.md` records test counts per profile. Update it if you rerun the suites.

**The NeoForge build has the same lock, and it is worse there.** On the pushed branch `port/neoforge-1.21.1` (commit `c234f19`, 3.4.0), `glyph/runtime/GlyphSessions.java` refuses through the `COOLDOWN_PENDING` map (around line 219), the session bound is 90 seconds (`MAX_SESSION_TICKS = 1800`), and the session cap counts every open session rather than only those holding something (`forPlayer(...).size() >= maxActiveSessions`, line 204), so each miss also occupies a session slot. Port the same behaviour: only a running charge or channel blocks, an earlier hold is released on re-admission, and the cooldown is checked before the effect. The class layout differs, so treat this as a separate change with its own tests. `main`'s 3.4.1 documents describe NeoForge behaviour newer than that pushed branch, so ask the owner for the current NeoForge checkout before starting.

**Left alone on purpose.**

- `max_active_glyph_sessions_per_player` (default 8) still refuses a cast when eight different Iron's spells are unresolved at once. After this fix repeated casts of one spell no longer accumulate, so the cap does what its description says. Do not change it in this work.
- A missed cast that is never repeated still holds its Iron's mana until the projectile expires. That matches the documented contract ("unspent reservations are released when the session closes").

**Noticed, optional, ask the owner before doing.** The session lifetime is fixed at 60 seconds (`GlyphSessions.LIFETIME_TICKS`), while Ars's projectile lifespan is a server setting with no upper bound. On a server that raises it, a projectile landing after 60 seconds is refused with `refused.no_session` ("it can only run inside a spell you cast yourself"), which misdescribes an expired cast. Options: derive the lifetime from `MethodProjectile.INSTANCE.getProjectileLifespan()` with a ceiling, or give expiry its own message.

## 7. Constraints

- Do not touch `contract/` unless unavoidable. Those files are hashed in `contract-manifest.txt` and checked by `python3 tools/contract_parity.py` in CI. The changes above need nothing there.
- `glyph/` is common code and must not reference Iron's types. Go through `IronGlyphBridge`; `onCooldown`, `hasRecast` and `creativeNoCooldown` already exist on it.
- Money moves only through `CastLedger`, `AlternativePayment` and `EmissionPayment`. Do not add a refund path.
- Keep the scope to what is listed here. No refactors of the session or payment classes.

## 8. Done when

- [ ] The reproduction in section 3 no longer refuses the second cast, in survival, in a real client.
- [ ] Firing twice at one target gives one Iron's effect, one Iron's payment, and "… is on cooldown." for the second landing.
- [ ] The five GameTests in section 5 pass; the first one failed before the change.
- [ ] `./gradlew test` passes.
- [ ] `./gradlew runGameTestServer -PgametestRunDir=run-absent` and `./gradlew runGameTestServer -PwithIronsRuntimeGameTests -PgametestRunDir=run-irons` pass, and `python3 tools/verify_gametest_log.py` accepts both logs with the updated counts.
- [ ] `python3 -m unittest discover -s tools -p 'test_*.py'` and `python3 tools/contract_parity.py` pass.
- [ ] `en_us.json`, `docs/3.4.1/casting-contract.md`, the Javadoc named in 4.4 and `CHANGELOG.md` describe the new rule.
- [ ] Your report says which of these you ran and which you could not, and lists the NeoForge port as open unless you did it.
