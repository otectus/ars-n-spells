# Ars 'n' Spells

**Ars 'n' Spells** bridges [Ars Nouveau](https://www.curseforge.com/minecraft/mc-mods/ars-nouveau) and [Iron's Spells 'n Spellbooks](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks) into one seamless magic experience. Unify your mana, share progression between spell systems, and scale your power across both mods — all fully configurable.

Works with **Minecraft 1.20.1** on **Forge**.

---

## Requirements

| Mod | Required? |
|-----|-----------|
| **Ars Nouveau** (4.12.7+) | Yes |
| **Iron's Spells 'n Spellbooks** (3.15.x) | No — falls back to native Ars behavior if absent |
| **Covenant of the Seven** (Sanctified Legacy) | No — enables LP and aura ring systems |
| **Blood Magic** | No — optional LP source for Ring of Seven Curses |

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

Conversion rates between the two mana systems are fully configurable.

The mod automatically hides the redundant mana bar so your HUD stays clean.

---

## Gear & Spell Integration

- **Gear perks cross over** — Ars armor bonuses apply to Iron's mana pool (and vice versa), depending on your active mode.
- **Spell scaling** — Ars spell potency scales with Iron's spell power attributes using additive stacking (not multiplicative). Elemental bonuses are applied based on your first glyph. A configurable `spell_power_cap` (default 3.0) prevents extreme values.
- **Enchantments** — Mana-related enchantments from both mods are respected regardless of which pool is active.

---

## Resonance

When your mana is nearly full (above 95% by default), you gain a **Resonance bonus** that boosts Iron's spell damage. A reward for mana-efficient play.

---

## Source Jar Synergy

Standing near Ars Nouveau Source Jars passively boosts Iron's mana regeneration. The bonus is configurable via `source_jar_synergy_multiplier` (default 5.0). The scan is position-cached for performance — it only re-scans when you move.

---

## Cross-Mod Progression & Affinity

- **Progression sharing** — Casting Ars spells grants Iron's school progression, and vice versa.
- **Affinity tracking** — The mod tracks your spell school usage over time, with optional decay for unused schools.

---

## Cross-Spell Inscription

Cast spells from *either* mod using items from the *other* mod through a tablet-driven inscribe / uninscribe ritual flow. Both ritual tablets are crafted at the **Enchanting Apparatus** and are fully datapack-defined, so pack authors can retune ingredients without code.

### Spell Transcription — bind a foreign spell onto an item

1. **Craft the Spell Transcription tablet.** Reagent: a novice Ars Nouveau spellbook. Pedestals: an Iron's Spellbooks spellbook, an archwood log, and a source gem block. Costs 2000 source.
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

LP costs scale with configurable base multipliers and per-tier scaling. Blasphemy LP discounts are independently configurable via `blasphemy_lp_discount` (default 85%).

### Ring of Seven Virtues — Aura Casting

Wearing the Virtue Ring converts mana costs into **Aura**, a custom resource that regenerates over time (default: 10/sec, 1000 max pool). Aura persists through death and dimension changes. If you run dry, the spell simply fails. Blasphemy aura discounts are independently configurable via `blasphemy_aura_discount` (default 85%).

**Ring Conflict:** Wearing both rings at the same time cancels both effects — you'll use normal mana instead, and receive a warning notification.

### Blasphemy Curios — School Discounts

Thirteen Blasphemy curio variants (Fire, Ice, Lightning, Holy, Ender, Blood, Evocation, Nature, Eldritch, Aqua, Geo, Wind, Dormant) each provide:

- **15% base mana discount** on all spells
- **+10% bonus** when the spell's school matches the curio's element (25% total)

Discounts stack multiplicatively with ring costs. A fully kitted setup can reach up to **40% off**.

---

## Scroll Cost Enforcement

Iron's Spellbooks scrolls now respect your resource costs:

| Mode | Behavior |
|------|----------|
| **Full** *(default)* | Scrolls consume mana and LP/aura like normal casting. |
| **LP Only** | Scrolls are mana-free, but LP is still consumed for Cursed Ring wearers. |
| **Free** | No resource cost for scrolls (LP from Cursed Ring still applies). |

---

## Configuration

Everything is configurable via `config/ars_n_spells-common.toml`. Major options include:

- **Master toggles** for mana unification, resonance, cooldowns, progression, and affinity
- **Mana conversion rates** and dual-cost split percentages
- **LP system** — source mode, death penalty, base/tier multipliers, minimum costs
- **Aura system** — max pool, regen rate, tier multipliers
- **Curio discounts** — base discount, matching bonus, stacking behavior
- **Blasphemy ring discounts** — separate LP and aura discount rates
- **Spell scaling** — power cap for Iron's attribute stacking
- **Source Jar synergy** — proximity regen multiplier
- **Rituals** — configurable Mana Infusion amount
- **Scroll cost mode**
- **Performance** — Source Jar cache move threshold
- **Debug mode** for troubleshooting

All options ship with balanced defaults — install and play, or fine-tune to your liking.

---

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/ans mana setdefault <value>` | Op 2 | Set the default max mana |
| `/ans mana getdefault` | -- | View the current default max mana |
| `/ans debug` | Op 2 | Toggle debug mode at runtime |
| `/ans info <player>` | Op 2 | Show mana, aura, resonance, and ring status |
| `/ans mode` | -- | Show current mana unification mode |

---

## FAQ / Troubleshooting

**Q: Ars mana isn't changing in ISS Primary mode.**
A: Make sure Iron's Spells 'n Spellbooks (3.15.x) is installed. Check logs for mixin load failures.

**Q: I see two mana bars.**
A: Verify your mana mode is set correctly. Check for overlay conflicts from other UI mods.

**Q: "Insufficient LP" even though I have enough hearts.**
A: Make sure `lp_source_mode` isn't set to `BLOOD_MAGIC_ONLY` without Blood Magic installed. The default (`BLOOD_MAGIC_PRIORITY`) falls back to health.

**Q: Scrolls are casting for free.**
A: Check that `scroll_cost_mode` is set to `full` (the default).

---

## License

GNU GPLv3
