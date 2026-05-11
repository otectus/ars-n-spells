# Ars 'n' Spells

**Ars 'n' Spells** bridges [Ars Nouveau](https://www.curseforge.com/minecraft/mc-mods/ars-nouveau) and [Iron's Spells 'n Spellbooks](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks) into one seamless magic experience. Unify your mana, share progression between spell systems, and scale your power across both mods — all fully configurable.

Works with **Minecraft 1.21.1** on **NeoForge**.

> **Port note (1.9.0, NeoForge 1.21.1).** This release ports the Forge 1.20.1 1.9.0 codebase to NeoForge 1.21.1 — Java 21, vanilla payload networking, NeoForge entity attachments for player state, and a `DataComponent`-backed cross-cast inscription store. The previous **Covenant of the Seven** (Sanctified Legacy) integration — Ring of Seven Curses, Ring of Seven Virtues, Blasphemy curios, LP and aura systems — has been **removed** because Covenant of the Seven does not have a NeoForge 1.21.1 build. Several gameplay re-attach points against Ars Nouveau 5.11.1 / Iron's Spells 1.21.1-3.15.6 internals are still being worked through; expect the next 1.9.x release(s) to land cross-cast invocation, Iron's-side hooks, and a fresh Curios integration. The core mod (mana unification, attachments, networking, cross-cast inscription store, cooldowns, progression / affinity scaffolding) compiles and runs against NeoForge 1.21.1.

---

## Requirements

| Mod | Required? |
|-----|-----------|
| **Minecraft (NeoForge)** 1.21.1 / 21.1.84+ | Yes |
| **Java 21** | Yes |
| **Ars Nouveau** 5.11.1+ | Yes |
| **Iron's Spells 'n Spellbooks** 1.21.1-3.15.6+ | No — falls back to native Ars behavior if absent |

The mod will not load on Forge or on Minecraft versions other than 1.21.1.

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

The mod hides the redundant mana bar so your HUD stays clean.

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

Standing near Ars Nouveau Source Jars passively boosts Iron's mana regeneration. The bonus is configurable via `source_jar_synergy_multiplier` (default 5.0). The scan is position-cached for performance.

---

## Cross-Mod Progression & Affinity

- **Progression sharing** — Casting Ars spells grants Iron's school progression, and vice versa.
- **Affinity tracking** — The mod tracks your spell school usage over time, with optional decay for unused schools (`affinity_decay_interval_ticks`, default 1200 ticks).

Player state is stored in NeoForge entity attachments. Affinity and progression carry through death/respawn; cooldowns intentionally reset on respawn.

---

## Cross-Spell Inscription

Cast spells from *either* mod using items from the *other* mod through a tablet-driven inscribe / uninscribe ritual flow. Both ritual tablets are crafted at the **Enchanting Apparatus** and are fully datapack-defined, so pack authors can retune ingredients without code.

### Spell Transcription — bind a foreign spell onto an item

1. **Craft the Spell Transcription tablet.** Reagent: a novice Ars Nouveau spellbook. Pedestals: an Iron's Spellbooks spellbook, an archwood log, and a source gem block. Costs 2000 source. The recipe is gated on Iron's Spellbooks being installed.
2. **Set up the brazier.** Place the tablet on a Ritual Brazier and drop two items within ~3 blocks:
   - exactly one **source** — a filled Ars Nouveau spell parchment, focus, or spellbook, or an Iron's Spellbooks scroll
   - exactly one blank **target** item to receive the inscription
3. **Activate.** Strict disambiguation: more than one of either category, or any already-inscribed item in range, fails the ritual with a chat message that names the items it saw and the rule.
4. **Cast.** Right-click the target. Sneak-right-click cycles between multiple inscriptions on the same item.

Mana costs flow through the active unification mode and respect your configured conversion rates. In **Separate** mode, costs split between both pools according to your dual-cost percentages. Cross-cast spells pay an overhead set by `cross_cast_cost_multiplier` (default 1.25 = 25% extra) — applied symmetrically to both directions, once per cast, before mana deduction.

The inscription itself is stored as a vanilla **DataComponent** (`ars_n_spells:cross_spells`) on the item — moving forward on 1.21.1 we use the modern data model instead of root NBT.

### Spell Uninscription — strip an inscription cleanly

If you change your mind, the **Spell Uninscription** ritual returns an inscribed item to a bit-identical fresh-blank state so it can be re-inscribed cleanly.

1. **Craft the Spell Uninscription tablet.** Reagent: a blank parchment. Pedestals: a water bucket, a source gem, and an archwood log. 500 source.
2. **Activate** with exactly one inscribed item in range and nothing else — sources or stray blanks fail the ritual rather than risk stripping the wrong stack.
3. The cross-cast component is removed cleanly, with an ash + smoke dissolution theme.

The uninscribe ritual works without Iron's Spellbooks loaded, so legacy inscribed items can still be cleaned up after Iron's is removed.

---

## Unified Cooldowns

An optional system that groups similar spells across both mods into shared cooldown categories (OFFENSIVE, DEFENSIVE, UTILITY, MOVEMENT). When enabled, cooldowns are **global per category by design** — an Ars OFFENSIVE cast and an Iron's OFFENSIVE cast intentionally collide on the same slot. Disabled by default.

---

## Configuration

Everything is configurable via `config/ars_n_spells-common.toml`. Major options include:

- **Master toggles** for mana unification, resonance, cooldowns, progression, and affinity
- **Mana conversion rates** and dual-cost split percentages
- **Spell scaling** — power cap for Iron's attribute stacking
- **Source Jar synergy** — proximity regen multiplier
- **Affinity decay** — opt-in tick handler with configurable interval
- **Cross-cast** — overhead multiplier on cross-cast spell costs
- **Debug mode** for troubleshooting

All options ship with balanced defaults — install and play, or fine-tune to your liking.

---

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/ans mana setdefault <value>` | Op 2 | Set the default max mana |
| `/ans mana getdefault` | — | View the current default max mana |
| `/ans debug` | Op 2 | Toggle debug mode at runtime |
| `/ans info <player>` | Op 2 | Show mana and resonance |
| `/ans mode` | — | Show current mana unification mode |

---

## Features removed in the NeoForge 1.21.1 port

Because **Covenant of the Seven** (Sanctified Legacy) does not have a NeoForge 1.21.1 distribution, the following addon-coupled features from Forge 1.20.1 are removed in this release:

- **Ring of Seven Curses (LP costs).** LP-based casting from Blood Magic Soul Network or health.
- **Ring of Seven Virtues (Aura costs).** Aura resource and regen system.
- **Blasphemy curios.** School-matched mana discounts.
- **Scroll cost mode** (`scroll_cost_mode`). LP-coupled scroll cost routing.
- **The aura system itself.** Removed because Virtue Ring was the only consumer.

If Covenant of the Seven ships a NeoForge 1.21.1 build, the integration can be revisited. A direct **Curios** integration is planned for an upcoming release to replace the curio-discount feature footprint.

---

## FAQ / Troubleshooting

**Q: Ars mana isn't changing in ISS Primary mode.**
A: Make sure Iron's Spells 'n Spellbooks (1.21.1-3.15.6+) is installed. Check logs for mixin load failures.

**Q: I see two mana bars.**
A: Verify your mana mode is set correctly. Check for overlay conflicts from other UI mods. (The mana-bar overlay re-targeting against NeoForge's `LayeredDraw` is on the work-in-progress list for the next 1.9.x release; until then both bars may show in some configurations.)

**Q: Cross-casting an inscribed item does nothing.**
A: Cross-cast invocation against Ars Nouveau 5.x is currently being re-attached as part of the port. Expect this to land in an upcoming 1.9.x release. Inscription itself works (you can inscribe and uninscribe), but the cast path is the open work.

**Q: The mod requires Sanctified Legacy and I had a setup using LP.**
A: The Sanctified Legacy integration is gone for this version because Sanctified Legacy itself hasn't shipped on NeoForge 1.21.1. Stay on the Forge 1.20.1 1.9.0 release if you need that feature.

---

## License

GNU GPLv3
