# Ars 'n' Spells

**Ars 'n' Spells** bridges [Ars Nouveau](https://www.curseforge.com/minecraft/mc-mods/ars-nouveau) and [Iron's Spells 'n Spellbooks](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks) into one seamless magic experience. Unify your mana, share progression between spell systems, scale your power across both mods — and even weave Ars spells into real Iron's spellbooks, cast straight from Iron's native spell wheel. All fully configurable.

Works with **Minecraft 1.20.1** on **Forge**.

---

## What's new in 3.0.x

- **The Spell Loom** — a new workstation that exports any Ars Nouveau spell onto a real Iron's scroll, with a name, nature, and icon of your choosing.
- **Spellbook Binding** — bind exported scrolls into real Iron's spellbooks; the Ars spell appears as its **own entry in Iron's native spell-selection wheel** (up to 8 per book).
- **3.0.1 hardening** — the Spell Transcription and Spellbook Binding tablets are craftable again with Iron's installed, LP toggles are honored consistently everywhere, the config screen got a full readability rework (with a proper read-only mode on servers), and a startup crash on Iron's-less installs is fixed.
- **3.0.2 polish** — affinity decay now follows its documented gentle curve (it drained ~20x too fast when enabled); an **advancement chain** guides you from crafting the Spell Loom to your first cross-cast; the Loom screen got the high-contrast readability treatment with hover tooltips on every slot; **pack makers** get datapack tags for custom cursed/virtue rings, Blasphemy curios, and Source Jar blocks, plus a new `aura_failure_mode` option to block (instead of free-allow) ring casts if the Covenant integration ever degrades; the mod now announces untested Covenant versions in chat and supports the Forge update checker.

---

## Requirements

| Mod | Required? |
|-----|-----------|
| **Ars Nouveau** (4.12.7+) | Yes |
| **Iron's Spells 'n Spellbooks** (3.15.0 – 3.x) | No — falls back to native Ars behavior if absent |
| **Covenant of the Seven** (Sanctified Legacy) | No — enables LP and aura ring systems (2.2.6 recommended for the aura HUD) |
| **Blood Magic** | No — optional LP source for Ring of Seven Curses |
| **Apotheosis** + **Apothic Curios** | No — lets affix/socket mana stats on curios feed the unified pool |

*(Curios is required by Ars Nouveau, so it is always present.)*

---

## Mana Unification

The core of Ars 'n' Spells: **five configurable modes** that control how the two mana pools interact.

| Mode | How it works |
|------|-------------|
| **ISS Primary** *(default)* | Iron's mana is the single source of truth. Ars reads and drains from Iron's pool. |
| **Ars Primary** | Ars mana is authoritative. Iron's spells drain from Ars mana. |
| **Hybrid** | Shared bidirectional pool. Choose which HUD bar to display. |
| **Separate** | Independent pools. Cross-mod casts split costs between both (configurable split). |
| **Disabled** | No integration — each mod uses its own pool. |

Conversion rates between the two mana systems are fully configurable, and cross-system mana **regen** is unit-converted properly (Iron's percentage regen vs Ars flat regen) so stacked regen gear never spirals out of control.

The mod automatically hides the redundant mana bar so your HUD stays clean.

**Changing the mode** (applied live, no restart): in singleplayer, open Mods → Ars 'n' Spells → Config and click the **Mana Mode** row to cycle it; server operators can run `/ans mode set <mode>`; or edit the config file directly. Confirm the active mode with `/ans mode`.

---

## Gear & Spell Integration

- **Gear perks cross over** — Ars armor bonuses apply to Iron's mana pool (and vice versa), depending on your active mode.
- **Curios & Apotheosis affixes** — Mana stats on worn curios (rings, amulets, belts) cross over too, including those rolled by **Apotheosis / Apothic Curios** affixes and sockets (and by Magical Jewelry, Jewelcraft, and similar). The read is generic — no hard Apotheosis dependency — and can be turned off with the `read_curio_attribute_modifiers` config. (Combat-only stats like crit or armor pierce are not mana/spell-power and are not bridged.)
- **Spell scaling** — Ars spell potency scales with Iron's spell power attributes using additive stacking (not multiplicative). Elemental bonuses are applied based on your first glyph. A configurable `spell_power_cap` (default 3.0) prevents extreme values.
- **Enchantments** — Mana-related enchantments from both mods are respected regardless of which pool is active.

---

## Resonance

When your mana is nearly full (above 95% by default), you gain a **Resonance bonus** that boosts spell damage. A reward for mana-efficient play. Configurable per mod, with adjustable strength, linger duration, and a damage cap.

---

## Source Jar Synergy

Standing near Ars Nouveau Source Jars passively boosts your mana regeneration. The bonus is configurable via `source_jar_synergy_multiplier` (default 5.0), with tunable scan interval and radius plus a dedicated off switch (`enable_source_jar_synergy`). The scan is position-cached for performance and never force-loads chunks.

---

## Cross-Mod Progression & Affinity

- **Progression sharing** — Casting Ars spells grants Iron's school progression, and vice versa.
- **Affinity tracking** — The mod tracks your spell school usage over time, with optional decay for unused schools.

---

## Cross-Spell Inscription

Cast spells from *either* mod using items from the *other* mod through a tablet-driven inscribe / uninscribe ritual flow. Both ritual tablets are crafted at the **Enchanting Apparatus** and are fully datapack-defined, so pack authors can retune ingredients without code.

### Spell Transcription — bind a foreign spell onto an item

1. **Craft the Spell Transcription tablet.** Reagent: a novice Ars Nouveau spellbook. Pedestals: any tiered Iron's spellbook, an archwood log, and a source gem block. Costs 2000 source.
2. **Set up the brazier.** Place the tablet on a Ritual Brazier and drop two items within ~3 blocks:
   - exactly one **source** — a filled Ars Nouveau spell parchment, focus, or spellbook, or an Iron's Spellbooks scroll
   - exactly one blank **target** item to receive the inscription
3. **Activate.** Strict disambiguation: more than one of either category, or any already-inscribed item in range, fails the ritual with a chat message that names the items it saw and the rule. Items that already carry an Ars Nouveau spell at root NBT are rejected as targets so right-click resolution stays unambiguous. On success the source is consumed, the target gains the cross-cast inscription, and a binding theme of enchantment-glyph particles + the enchantment-table sound marks the inscribe.
4. **Cast.** Right-click the target. Sneak-right-click cycles between multiple inscriptions on the same item.

Mana costs flow through the active unification mode and respect your configured conversion rates. In **Separate** mode, costs split between both pools according to your dual-cost percentages. Cross-cast spells pay an overhead set by `cross_cast_cost_multiplier` (default 1.25 = 25% extra) — applied symmetrically to both directions, once per cast, before mana deduction.

### Spell Uninscription — strip an inscription cleanly

If you change your mind, the **Spell Uninscription** ritual returns an inscribed item to a bit-identical fresh-blank state so it can be re-inscribed cleanly.

1. **Craft the Spell Uninscription tablet.** Reagent: a blank parchment. Pedestals: a water bucket, a source gem, and an archwood log. 500 source.
2. **Activate** with exactly one inscribed item in range and nothing else — sources or stray blanks fail the ritual rather than risk stripping the wrong stack.
3. The cross-cast NBT is removed cleanly (spells list, cycle index, and any empty residual tag), with an ash + smoke dissolution theme.

The uninscribe ritual works without Iron's Spellbooks loaded, so legacy inscribed items can still be cleaned up after Iron's is removed.

---

## The Spell Loom — Export Ars Spells into Iron's Spellbooks *(3.0.0)*

Design a spell with Ars Nouveau's tools, weave it onto a **real Iron's scroll** at the Spell Loom, bind it into a **real Iron's spellbook** — and cast it from **Iron's own native spell wheel**, right alongside your Iron's spells. The Ars spell rides as an Ars 'n' Spells sidecar on the real Iron item, so the book's own Iron's spells are never touched. This leg requires Iron's Spellbooks.

1. **Weave.** Craft the **Spell Loom** (gold ingot, 2× lapis, a book, and 3× obsidian on a crafting table) and right-click it. Drop your Ars spell source (parchment, focus, or spellbook) and a **blank Iron's scroll** into the slots, then give your spell a **name**, pick a **nature**, choose an **icon**, and press **Inscribe**. Out comes a real Iron's scroll carrying your spell. *(Ops can also use `/ans export_to_irons_scroll` for a quick, metadata-free export.)*
2. **Bind.** Run the **Spellbook Binding** ritual (its apparatus tablet appears only when Iron's is installed — 2500 source) with the scroll and an Iron's spellbook near the brazier, or hold both and run `/ans bind_scroll_to_irons_book`. The scroll is consumed and the spell is appended to the book with your chosen name and icon; re-binding the same spell is rejected (dedup by spell payload).
3. **Cast.** Your Ars spell shows up as its **own entry in Iron's native spell-selection wheel**. Select it and right-click like any Iron's spell — it casts the real Ars spell through the full cross-cast pipeline (mana mode, cost multiplier, scaling, cooldowns). Up to **8** Ars spells per book, and your native Iron's spells cast exactly as before.

Pack authors: gate the whole feature with `allow_ars_spells_in_irons_spellbooks`, or cap spells per book with `max_ars_cross_spells_per_irons_spellbook`.

*(Generic inscribed items that aren't Iron's spellbooks still cast via right-click, with sneak-right-click to cycle.)*

---

## Unified Cooldowns

An optional system that groups similar spells across both mods into shared cooldown categories. Prevents spell spam by locking out related spells when one is cast. Disabled by default.

---

## Covenant of the Seven Integration

When **Covenant of the Seven** (Sanctified Legacy) is installed, two powerful ring systems unlock.

### Ring of Seven Curses — Life Point Casting

Wearing the Cursed Ring converts all mana costs into **Life Points (LP)**.

- **Blood Magic Priority** *(default)* — Draws LP from Blood Magic's Soul Network first, falls back to player health.
- **Blood Magic Only** — Soul Network only.
- **Health Only** — Always costs health (100 LP = 5 hearts).

If you run out of LP:
- **Safe Mode** *(default)* — Spell is cancelled, you take minor damage (you will never die from this).
- **Death Mode** — The spell casts... but it kills you.

LP costs scale with configurable base multipliers, per-tier scaling for Ars glyphs, and per-level plus rarity scaling for Iron's spells. The whole system has a master toggle (`enable_lp_system`) that every LP path honors — scrolls included.

### Ring of Seven Virtues — Aura Casting

Wearing the Virtue Ring converts your **Ars** mana costs into **Aura** — Covenant of the Seven's own regenerating resource, complete with its own HUD bar. Covenant owns the pool, regen, and persistence; Ars 'n' Spells simply maps the mana cost onto it, scaled by `ars_virtue_aura_multiplier`. Iron's spells under the ring are handled natively by Covenant. If you run dry, the spell simply fails.

**Ring Conflict:** Wearing both rings at the same time cancels both effects — you'll use normal mana instead, and receive a warning notification.

### Blasphemy Curios — School Discounts

Thirteen Blasphemy curio variants (Fire, Ice, Lightning, Holy, Ender, Blood, Evocation, Nature, Eldritch, Aqua, Geo, Wind, Dormant) each provide:

- **15% base mana discount** on all spells
- **+10% bonus** when the spell's school matches the curio's element (25% total)

Discounts stack multiplicatively with ring costs, and a matching Blasphemy also deeply discounts LP costs (`blasphemy_lp_discount`, default 85% off).

---

## Scroll Cost Enforcement

Iron's Spellbooks scrolls respect your resource costs:

| Mode | Behavior |
|------|----------|
| **Full** *(default)* | Scrolls consume mana and LP/aura like normal casting. |
| **LP Only** | Scrolls are mana-free, but LP is still consumed for Cursed Ring wearers. |
| **Free** | No resource cost for scrolls (LP from Cursed Ring still applies). |

---

## Configuration

Everything is configurable via the per-world server config `<world>/serverconfig/ars_n_spells-server.toml` (server-authoritative and auto-synced — on multiplayer the in-game config screen is read-only). Major options include:

- **Master toggles** for mana unification, resonance, cooldowns, progression, affinity, LP, and aura
- **Mana conversion rates**, dual-cost split percentages, and cross-system regen conversion
- **LP system** — source mode, death penalty, base/tier/rarity multipliers, minimum costs
- **Aura system** — master toggle and mana→aura cost multiplier (the pool itself belongs to Covenant)
- **Curio discounts** — base discount, matching-school bonus, Blasphemy LP discount
- **Curio attribute bridge** — `read_curio_attribute_modifiers` (default on): mirror Apotheosis/Apothic Curios affix & socket mana stats across the unified pool
- **Spell scaling** — power cap for Iron's attribute stacking
- **Source Jar synergy** — off switch, regen multiplier, scan interval and radius
- **Cross-casting** — cost multiplier, spellbook-binding toggle, and per-book spell cap
- **Scroll cost mode**
- **Debug mode** with end-to-end cross-cast tracing for troubleshooting

All options ship with balanced defaults — install and play, or fine-tune to your liking.

---

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/ans mode` | -- | Show current mana unification mode |
| `/ans mode set <mode>` | Op 2 | Switch the mana unification mode live (no restart) |
| `/ans mana getdefault` | -- | View the current default max mana |
| `/ans mana setdefault <value>` | Op 2 | Set the default max mana |
| `/ans info <player>` | Op 2 | Show mana, aura, resonance, and ring status |
| `/ans debug` | Op 2 | Toggle debug mode at runtime |
| `/ans aura` | -- | Show your own current aura |
| `/ans export_to_irons_scroll` | Op 2 | Export the held Ars spell onto a real Iron's scroll — the **Spell Loom** is the survival route |
| `/ans bind_scroll_to_irons_book` | Op 2 | Bind a held exported scroll into a held Iron's spellbook |

---

## FAQ / Troubleshooting

**Q: Ars mana isn't changing in ISS Primary mode.**
A: Make sure Iron's Spells 'n Spellbooks (3.15.0+) is installed. Check logs for mixin load failures.

**Q: I can't craft the Spell Transcription or Spellbook Binding tablet.**
A: Fixed in 3.0.1 — update the mod. The recipes accept any tiered Iron's spellbook.

**Q: I see two mana bars.**
A: Verify your mana mode is set correctly. Check for overlay conflicts from other UI mods.

**Q: "Insufficient LP" even though I have enough hearts.**
A: Make sure `lp_source_mode` isn't set to `BLOOD_MAGIC_ONLY` without Blood Magic installed. The default (`BLOOD_MAGIC_PRIORITY`) falls back to health.

**Q: Scrolls are casting for free.**
A: Check that `scroll_cost_mode` is set to `full` (the default).

---

## License

GNU GPLv3
