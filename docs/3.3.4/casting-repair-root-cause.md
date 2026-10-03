# Casting repair — 3.3.4

The minimal supported Forge and NeoForge installations reproduce a failure matching the reported “windup, no effect” symptom. They do not establish which condition affected the reporter's unknown modpack.

## Reproduction and bytecode evidence

Work started from refreshed `origin/main` at `d805b45fb629d9ae30b97ccc6b3487438c103942` and `origin/port/neoforge-1.21.1` at `41fac17065c381104b17fdaab307d89ba21b49ab`. Neither refreshed head differed from the supplied brief. Original untracked reference files were preserved; implementation and tests used isolated worktrees.

The supplied arithmetic probe was rerun without modifying its source. From native Ars balance `32768.001`, a legitimate one-mana debit ends at `32767.001`. Narrowing the two observations to float makes the measured debit `0.998046875`, outside the old cost-based `0.001` tolerance. Both original production branches, with only the new native GameTest fixtures added, reject the actual one-mana cast and produce no healing. The patched versions heal and retain the double balance `32767.001`. `65536.002` is also covered by the accounting regression; `1000` is the normal-balance control.

Five required runtime fixtures fail on each original branch: precision, discounted ordinary-book initiation, duplicate native mana writes, first-channel-failure cooldown, and mixing pricing modes during a cast. These are independent confirmed bugs, not five proved causes in the player's installation. The original recast regression passes and remains a preservation test.

The release JAR metadata and bytecode were inspected directly. Forge CurseMaven file `7402504` declares Iron's `1.20.1-3.15.0`; it must not be described as 3.16.x based on a repository comment. NeoForge resolves Iron's `1.21.1-3.16.3`. Their relevant order is final cost event → native mana write → synchronization → `onCast` → recast/cooldown handling. Native continuous cancellation can consume a scroll even when cooldown is disabled. Cancelling only `castSpell` does not stop the surrounding ticker's completion/consumption code.

## Repair

- Transactional Ars reads and writes use the supported double API. Float-backed Iron's payments require the exact representable post-subtraction balance, sufficient funds, and an observed positive debit. Sub-resolution nonzero costs are explicitly refused, not rounded into free casts.
- Admission remains provisional until every final-cost listener has run. Native non-mana validation still executes and its failure is returned unchanged. Pricing and ownership are latched before event dispatch; cost conversion occurs once.
- Each debit records protected before/after observations even when the adapter returns false or throws. A failed later leg compensates known earlier debits. Partial credits retain only the unpaid remainder; unknown movement is quarantined. Reentrant callbacks cannot commit an unfinished observation or refund twice.
- Payment remains reserved until entry into the native effect. A failure before that point can compensate it; an exception after native healing or other world changes keeps its payment. Only the native write actually replaced by ANS is suppressed; the native synchronization remains.
- Cast identity includes the player, MagicData, spell/level, source/carrier, latched rules, and per-channel effect sequence. Pricing changes reject before effects. A repeated synchronization of identical settings may advance a generation without changing the price and does not cancel the cast.
- A first-effect abort uses native completion/reset and finish notification without the native interruption handler's cooldown or scroll removal. After earlier paid channel effects, native interruption semantics remain. The enclosing ticker stops after an aborted invocation, avoiding a second completion or consumption.
- Category cooldowns follow an actual native cooldown event. They wait for a native recast sequence to end and do not arise solely from a failed first payment. The ticker's base-price anticipation no longer overrides authoritative final-event pulse pricing.
- The validation invocation is wrapped with `try/finally` semantics through MixinExtras. No instruction-level mana redirect or translated-error-to-SUCCESS rescue was introduced. Required payment, effect, validation, and caller hooks have explicit match counts. Optional integrations remain gated when absent.

Main retains its cursed-ring LP, native-fallback/refuse/legacy-open choices, death option, and Covenant-owned virtue aura behavior. LP factors are latched and use final event cost; native recasts and other established exemptions remain. NeoForge retains its simpler native-mana scope; this hotfix does not add Covenant integration to the port.

## Recovery and diagnostics

Unresolved obligations remain in the server ledger and retry known remaining credits at a 20-tick cadence after the attempt TTL. Offline players do not count as refunded. Each attempt retains bounded diagnostic receipts. New paid reservations are bounded; unknown movements never trigger a guessed credit. Warnings identify attempt, step, player, spell/source, lifecycle stage and settlement reason. Existing debug mode adds bounded successful-boundary traces. Players receive one concise actionbar message for a failed session.

On orderly shutdown, unresolved obligations are written atomically to the world's `data/ars_n_spells-payment-recovery.json` (schema 1, at most 4096 rows/4 MiB on read). A corrupt or unsupported journal is preserved and blocks ANS payment rather than being treated as empty. A partially parsed journal is not retried. Known native credits can resume after restart. Alternative-resource receipts that cannot safely reconstruct the original health/network/aura source are retained as unknown for review. This is orderly-restart recovery, not a transaction log guaranteeing recovery from power loss at every instruction.

Review unknown obligations from the retained receipts and native resource state before adjusting a stopped server's journal. Do not infer a refund from the requested price or restore an entire old balance.

## Findings during validation

The integrated client exposed repeated unchanged configuration synchronization; comparing pricing semantics fixed the false cancellation while retaining mode-change rejection. A copied NeoForge QA save also required an explicit survival transition, and the dedicated fixture needed regeneration disabled and login initialization completed before comparing exact server/client mana. These are recorded test setup corrections, not evidence of free casts in production.

A historical survival Ars-proxy fixture funded only Iron's through an accessor whose server-player link was not guaranteed initialized. In an Ars-primary Covenant profile it could leave the real payer unfunded. The fixture now uses an isolated player and funds both native pools explicitly. The full Covenant suite passes after this correction.

## Scope limits

The original player's loader, spell, configuration, and pack are unknown. No claim of a unique player-environment cause or universal compatibility is made. One Mana Bar and Mana and Artifice were not available as a verified matching test installation; the overwrite compatibility rationale is preserved, but that combination is not newly certified. Other addon profiles and an exhaustive per-spell target/prerequisite matrix were not run for this hotfix. Native validation is preserved, with adventure/cooldown/insufficient-resource checks directly exercised. Full dimension-transfer, death/respawn, and arbitrary third-party early-injection permutations remain outside the runtime evidence; cleanup hooks and contract fault tests cover the implemented boundaries.
