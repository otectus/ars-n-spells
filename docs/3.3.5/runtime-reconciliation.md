# 3.3.5 runtime reconciliation

This note ties the reported failures to the build that produced them. It records what the evidence shows and what it does not. Test commands and results are in [testing.md](testing.md).

## Which build wrote the reported line

The report quoted one `[CastPayment]` line. It gave spell `bielgg_spells:crystal_barrage/1`, `source=SWORD`, `step=2`, `stage=interrupted_after_effect`, `paid=false`, `reason=INSUFFICIENT_RESOURCE`, `failureUnit=IRONS_MANA` and `remaining=[]`.

| Source | What it contains |
| --- | --- |
| Brief baseline `d805b45` (3.3.3) | `NativePayment.Result` has six components: `id, paid, debited, refunded, reason, failureUnit`. No source file logs `[CastPayment]`. `git log --all -S interrupted_after_effect` finds no commit. |
| Uncommitted 3.3.4 work on top of `d805b45` | `casting/IronsCastLifecycle.java` logs `[CastPayment] attempt={} step={} player={} spell={}/{} source={} stage={} payment={} exception={}`. Its stages are `aborted_before_effect`, `failed_during_effect` and `interrupted_after_effect`. `NativePayment.Result` has nine components, ending `remaining, unknown, movements`. |
| Local build `build/libs/ars_n_spells-3.3.4.jar` | `mods.toml` version `3.3.4`, built 2026-09-14, SHA-256 `3103f169f92a7d66f79a20c3f4e082e02a38b93034c80d7e9df4053f080b35f6`. `javap` shows the same format string and the same nine-component `Result`. |

The line therefore came from ANS 3.3.4: this build, or one made from the same source. It cannot have come from 3.3.3. The reporter's jar was not supplied, so its hash was not compared.

The report did not include the Minecraft, Forge, Ars Nouveau, Iron's, BielGG's, T.O Magic or Roaring versions. The mana mode, the server config, the mod list and the full log were also missing. No pack config could be preserved, so every test uses a generated config.

## Reading the 3.3.4 line

| Field | Meaning in 3.3.4 |
| --- | --- |
| `step=2` | The second paid invocation of one cast. For a channelled spell, the second pulse. |
| `stage=interrupted_after_effect` | An earlier step of the same cast had already produced its effect. |
| `paid=false`, `reason=INSUFFICIENT_RESOURCE` | The check before any debit refused this step, so nothing was taken for it. |
| `failureUnit=IRONS_MANA` | Either the refused leg was Iron's mana, or the caster wore a Cursed Ring and ran short of LP. 3.3.4 labelled LP shortages on Iron's casts with the plan's Iron's-mana origin. 3.3.5 labels them `LP`. |
| `remaining=[]` | Refunds still owed after a failed settlement. The list is empty because nothing was debited. It does not mean the step was free. |
| (absent) | 3.3.4 logged neither the price nor the balance. 3.3.5 adds `quote=` and the needed and available amounts. |

## Why Crystal Barrage stopped

BielGG's Spells Addon `1.5-patchwork` was inspected from its jar: Modrinth version `xGlPe98h`, published 2026-09-11, SHA-256 `6fa4dc388423911de350f37c25348f7ad5de7014502669dfdb59ee4aa15db740`. Its class `roaring/spells/CrystalBarrageSpell` registers `bielgg_spells:crystal_barrage` as `CastType.CONTINUOUS`, with a base cost of 250 mana and a cast time of 100 ticks. Iron's charges a channel on every pulse.

Iron's own cast ticker (`MagicManager`, in 1.20.1-3.15.0 and 3.16.3) ends a channel early when the source consumes mana and `mana - baseCost × 2 < 0`. The current pulse then becomes the last one: Iron's applies the cooldown, consumes a scroll and completes the cast. With 250 to 499 mana, Crystal Barrage pays one pulse and ends normally.

In the 3.3.4 jar, `MixinIronsCastTicker.arsnspells$deferChannelAffordability` always returns `false` (`iconst_0; ireturn`). That switched Iron's forecast off for every channel. A channel that ran low went on into a pulse the caster could not pay. The pre-debit check refused that pulse after the previous pulse's effect. The result is exactly `step=2 … interrupted_after_effect … INSUFFICIENT_RESOURCE`, followed by the generic "Cast stopped. Check your resources; details are in the server log." message.

This was an ANS regression. It did not depend on BielGG's code. In 3.3.4, any mana-consuming channelled Iron's spell that ran out of mana mid-channel stopped the same way, whichever mod registered it.

3.3.5 checks after each paid pulse, at that pulse's final price and in the pool that paid it. If the balance cannot pay another pulse, the pulse just paid becomes the final pulse and Iron's native completion runs. Iron's forecast stays off only for casts whose latched payment plan charges resources. Exempt casts keep it: creative mode, recasts, inscribed Ars proxies and Virtue Ring aura casts. Channels paid in LP through a Cursed Ring are still settled pulse by pulse. They stop at the first pulse the LP balance cannot cover, with a message that names LP, unless `death_on_insufficient_lp` is on.

BielGG's Thorn Ring changes the price. `equipment/WinterEquipmentEvents.halveManaCost` sets the `SpellOnCastEvent` cost to half the original cost when the caster wears the ring, a Curios item from the jar's `roaring/ShadowMagicRegistry`. Iron's forecast reads the base cost, so without ANS a Thorn Ring channel ended while halved pulses were still affordable. 3.3.5 checks the halved price.

Evidence, using Iron's own spells because BielGG's could not be run:

- `ironsLoaded_channelEndsOnLastAffordablePulse` uses `irons_spellbooks:fire_breath`, a channelled spell. In all five mana modes, a caster funded for one, two or three pulses plus half a pulse pays exactly that many pulses. Every run ends with no payment failure and one exhausted-channel completion. The native cooldown starts, the cast state clears and no reservation stays open.
- `ironsLoaded_swordChannelUsesFinalCostModifier` casts from a SWORD source with a `SpellOnCastEvent` listener that halves the cost, as the Thorn Ring does. It pays three halved pulses with no payment failure and starts the native cooldown.
- A mutation check disabled the 3.3.5 exhaustion check, which restores the 3.3.4 behaviour. The loaded suite then failed exactly these two tests, 2 of 135 required: "channel must not end in a payment failure iss_primary x1" and "sword channel must not end in a payment failure". The restored source passes both.

## The other BielGG's spells

The jar's `compat/OptionalSpellDependencies` lists 16 spells under the optional `roaring` mod. It lists `the_final_breath` and `death_sorrows` under `cataclysm_spellbooks` together with `traveloptics` (T.O Magic). The table comes from each class's bytecode. Costs are base mana costs at level 1, and cast times are in ticks.

| Spell (`bielgg_spells:`) | Cast type | Base cost | Cast time |
| --- | --- | --- | --- |
| `crystal_barrage` | CONTINUOUS | 250 | 100 |
| `cutting_vortex` | CONTINUOUS | 120 | 100 |
| `dark_fan` | CONTINUOUS | 100 | 100 |
| `prism_rain` | CONTINUOUS | 50 | 60 |
| `star_shard` | CONTINUOUS | 25 | 60 |
| `ultra_penumbra` | CONTINUOUS | 10 | 400 |
| `beam_of_fear` | LONG | 750 | 60 |
| `roaring` | LONG | 700 | 100 |
| `summon_knight` | LONG | 650 | 160 |
| `puppeting` | LONG | 450 | 40 |
| `summon_shade` | LONG | 200 | 60 |
| `void_slash` | LONG | 50 | 20 |
| `glint` | INSTANT | 500 | 0 |
| `shadow_control` | INSTANT | 250 | 0 |
| `rapid_cut` | INSTANT | 60 | 10 |
| `knife_dance` | INSTANT | 50 | 10 |
| `the_final_breath` (T.O Magic set) | LONG | 1000 | 200 |
| `death_sorrows` (T.O Magic set) | LONG | 750 | 20 |

Only the six channelled spells charge more than once per cast, so only they could stop after an effect with `interrupted_after_effect`. LONG and INSTANT spells charge once, before their effect. A shortage refuses them before anything happens. 3.3.4 showed the generic message for that. 3.3.5 names the resource, the amount needed and the amount available.

"Roaring" in the report could mean the `roaring` spell set or the single spell `bielgg_spells:roaring`. Without a spell ID from the reporter, no further attribution is made. T.O Magic's own spells were not inspected.

## What this does not establish

- None of BielGG's spells ran. Its `mods.toml` makes L_Ender's Cataclysm 3.31 or later and Lionfish API 3.0 or later mandatory, and the test profiles do not include them. The fixtures above use Iron's own spells and a cost listener that mimics the Thorn Ring.
- The reporter's pack, mode and config are unknown. Whether they wore a Cursed Ring is unknown, which matters for reading `failureUnit`.
- Nobody observed the in-game messages in a graphical client.

## Hotbar mana dip in `ars_primary`

The report is verbal: no balances, no log, no versions. The brief listed five possible explanations. Here is what 3.3.5 established for each.

| Possibility | Finding |
| --- | --- |
| (a) A held item really changes the maximum | `ironsLoaded_arsPrimaryRemovedMaxBonusClampsOnce`: removing an item that adds 50 maximum mana caps the balance at the new maximum once. Holding it again, and reselecting at an unchanged maximum, takes nothing more. |
| (b) A lower ceiling clamps the balance | In `ars_primary`, Iron's regeneration reads and writes the Ars balance but capped it at Iron's mirrored `max_mana`, an integer copy that can lag the Ars maximum. With the 3.3.5 guard disabled in a mutation check, `ironsLoaded_arsPrimaryIronsRegenCannotLowerArsPool` saw regeneration move the Ars pool from 80 to 20 against a mirror of 20. With the guard, the balance stays at 80, and regeneration still raises a low balance. |
| (c) Only the display changes | Not examined in a graphical client. |
| (d) A cast or item use happens while scrolling | No cast attempt occurs in the hotbar test. Not examined in a client. |
| (e) A stale ceiling update runs after a newer change | Deferred Iron's ceiling updates are now merged per player, so only the latest value applies. The equipment event no longer computes the ceiling before vanilla swaps the item's attribute modifiers, because Forge posts `LivingEquipmentChangeEvent` first. |

`ironsLoaded_arsPrimaryHotbarScrollKeepsAuthoritativeBalance` drives the real player tick through 60 hotbar selections. The hotbar holds an empty hand, an iron sword, the Ars novice spell book, an Iron's spellbook and a stick. Iron's regeneration runs after every step, starting from 80% and from 100% of the maximum. The server balance never falls, and the maximum does not change. This test also passes with the regen guard disabled, so it did not reproduce the report. The dip needs Iron's mirror to sit below the Ars balance, and the test's aligned equipment never produces that.

The cause of the reported dip is therefore not confirmed. 3.3.5 removes the only cast-free debit path found and the two ordering problems that could leave the mirror low. With `debug_mode` on, `[ManaTrace]` lines show both pools, the ceiling, the selected slot and the held item whenever reconciliation changes something, at most ten lines per second per player.

## Scorch cancelled after payment (NeoForge, 2026-09-28)

A NeoForge 1.21.1 server running ANS 3.3.4 logged one warning per attempt while a player cast `irons_spellbooks:scorch/9` from a spellbook. The server ran NeoForge 21.1.250, Iron's 1.21.1-3.16.3 and Ars Nouveau 5.13.1, in `iss_primary` and then `hybrid`. Its log was supplied in full.

| Field | Meaning |
| --- | --- |
| `stage=aborted_before_effect` | The invocation ended before Iron's `onCast`. |
| movements `before=100.0 … after=10.0`, then `before=10.0 … after=100.0` | ANS took the price of 90, then refunded it. |
| `reason=NATIVE_FAILURE`, `exception=… Effect boundary not reached` | `castSpell` returned without reaching the effect and without an ANS refusal. 3.3.4 treated that as a fault. |

Iron's `castSpell` runs its own mana block between the final cost event and `onCast`. ANS 3.3.4 paid before that block. The pack's Animus 5.2.13 injects there: it reads the pool, finds less than the cost, cannot cover the difference with blood magic, and cancels the cast. It saw 10 of the 100, because ANS had already taken 90. So any spell costing more than half the remaining pool failed; cheaper spells passed.

Animus 1.20.1-3.0.35 does not inject there. It redirects the mana read in `canBeCastedBy` and lowers the cost in a lowest-priority `SpellOnCastEvent` listener, which ANS already honoured. No Forge report is known. The Forge build had the same payment path and received the same change.

3.3.5 now pays at Iron's own mana write, which the payment replaces. A cast another mod cancels before its effect is refunded silently. [testing.md](testing.md#payment-boundary-fix-2026-09-28) lists the change, the new tests, and the commands and results.

For players: if a spell that costs more than half your mana never fires and the server log shows `Effect boundary not reached`, this is the cause. It is fixed in 3.3.5.

## Note for players

Channelled spells such as Crystal Barrage stopped with "Cast stopped. Check your resources; details are in the server log." in ANS 3.3.4 when mana ran out mid-channel. That was an ANS bug, fixed in 3.3.5: the channel now ends normally on the last pulse you can pay for. If a cast is still refused, the message names the resource, the amount needed and the amount you had.

For the mana bar dipping while scrolling in `ars_primary`, 3.3.5 fixes the most likely cause, but the dip was not reproduced. If it still happens, please send:

- the exact versions of ANS, Forge, Ars Nouveau, Iron's Spellbooks, BielGG's Spells Addon, Roaring and T.O Magic
- `mana_unification_mode` and the server's `ars_n_spells-server.toml`
- whether you wear a Cursed Ring or a Thorn Ring
- a short recording with current and maximum mana visible
- the `[CastPayment]` and `[ManaTrace]` lines from the server log with `debug_mode = true`. Players appear there as pseudonymous tokens such as `p-1a2b3c4d`, not UUIDs.
