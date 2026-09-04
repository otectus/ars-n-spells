# Ars 'n' Spells

**Ars 'n' Spells** bridges [Ars Nouveau](https://www.curseforge.com/minecraft/mc-mods/ars-nouveau) and [Iron's Spells 'n Spellbooks](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks) into one seamless magic experience. Unify your mana, share progression between spell systems, and scale your power across both mods — all fully configurable.

Works with **Minecraft 1.21.1** on **NeoForge**.

> **Version note (3.2.4, NeoForge 1.21.1).** This build is at feature parity with the Forge 1.20.1 **3.2.3** and **3.2.4** lines. Mana unification, cross-cast invocation, Ars spells in Iron's native spell wheel, the Spell Loom, rituals, cooldowns, progression, resonance and equipment scaling are all live. The **Covenant of the Seven** (Sanctified Legacy) integration — Ring of Seven Curses, Ring of Seven Virtues, Blasphemy curios, LP and aura systems — remains **removed**, because Covenant of the Seven has no NeoForge 1.21.1 build. Compile targets: Ars Nouveau 5.13.1.1400, Iron's Spells 1.21.1-3.16.3, NeoForge 21.1.248.

---

## What's new in 3.2.4

**If you were using the rituals and nothing was happening, they work now.** The Spellbook Binding, Spell Transcription, Spell Uninscription, and Mana Infusion rituals were completely broken since version 3.0.0 — you could light the brazier, but it would stay lit forever and never consume the items or produce a result. The rituals now complete properly after burning for about three seconds. Ritual tablets are consumed but not returned, matching how Ars Nouveau's own rituals work.

**Ritual feedback now follows you.** When a ritual completes, the success or failure message used to go to whichever player was closest to the brazier at that moment. If you stepped away while it was burning, you'd get no message at all. Rituals now remember who lit them and send the report to that player, no matter how far away they've wandered — or to the nearest player if the original lighter has logged off.

A third fix closes a loophole: the `/ans bind_scroll_to_irons_book` command now respects the `allow_ars_spells_in_irons_spellbooks=false` config option and says the feature is disabled immediately rather than asking you to provide items it will never accept.

On the server side, the Mana Well ritual no longer scans every entity section within its range on every tick; instead it walks the player list and checks each player's position. The potion regeneration bridge also switched from querying NeoForge's mod list on every call to using a cached answer, so both paths are significantly faster. No gameplay changes outside the ritual fixes above, and no config, packet, or save-format changes.

---

## What's new in 3.2.2

**If you have found strange "Ars Spell" scrolls in loot chests, this is the release that stops them.** Exploration chests, Mystery Scroll Pouches, magic-mob drops and wandering traders could hand you a spell scroll whose tooltip showed a raw line of code rather than a description, and which did nothing at all when used. Those were Ars 'n' Spells' internal proxy spells — the hidden slots that let an Ars Nouveau spell show up in Iron's spell wheel — and they are meaningless outside a spellbook that carries the matching spell.

Ars 'n' Spells does not modify any loot table and never has. Iron's Spellbooks picks a random spell from its own registry, and a spell is eligible unless it opts out. Ours opted out of the Scroll Forge but never out of loot, and because they are Common rarity they were among the *most* likely things to be rolled. They now opt out properly, so no new one can be generated.

A stray scroll already sitting in your world is not stuck there: right-click it once and it turns into a blank Iron's scroll. Unopened chests were never affected, since loot is decided when a chest is first opened. The eight missing spell descriptions have been added too, so nothing shows a raw translation key any more.

---

## What's new in 3.2.1

**If you run One Mana Bar, this is why your game would not start.** Ars 'n' Spells adjusted the mana value Iron's reads when it decides whether you can cast, and did it by targeting one specific instruction inside that check. One Mana Bar replaces that whole method, so the instruction was gone — and Mixin treats that as fatal rather than something to skip. The adjustment now attaches to the start and end of the method instead, which is allowed even when another mod has replaced it.

Also in this release: mixins targeting Iron's Spellbooks — an optional dependency — moved into a separate, non-required layer, so a future conflict there skips one piece with a warning instead of aborting startup for every mod in the chain. Plus fixes for uninscription leaving unusable entries in the spell wheel, the Spell Transcription ritual destroying stacked source scrolls, ARS_PRIMARY deleting mana above Iron's own ceiling, and three ritual tablets rendering with the wrong art.

---

## Requirements

| Mod | Required? |
|-----|-----------|
| **Minecraft (NeoForge)** 1.21.1 / 21.1.84+ | Yes |
| **Java 21** | Yes |
| **Ars Nouveau** 5.13+ (built against 5.13.1.1400) | Yes |
| **Iron's Spells 'n Spellbooks** 1.21.1-3.16.3+ | No — falls back to native Ars behavior if absent |

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
- **Mana potions feed the unified pool** — in ISS Primary, Ars's `mana_regen` and `mana_boost` effects are mirrored onto Iron's mana-regen / max-mana attributes, so Ars mana potions raise the pool you actually cast from.

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

### Spellbook Binding — bind an exported Ars spell into an Iron's spellbook

A two-step ceremony to turn an Ars Nouveau spell into an Iron's native spell: first, export it onto a blank scroll via the Spell Loom or the `/ans export_to_irons_scroll` command. Then use this ritual to bind it into a real Iron's spellbook, where it joins the native spell wheel and casts like any other.

1. **Craft the Spellbook Binding tablet.** Reagent: a novice Ars Nouveau spellbook. Pedestals: an Iron's spellbook, an Iron's scroll, a source gem block, and an archwood log. Costs 2500 source.
2. **Set up the brazier.** Place the tablet on a Ritual Brazier and drop exactly two items within a three-block radius:
   - exactly one **carrier scroll** — an Iron's scroll exported via the Spell Loom or `/ans export_to_irons_scroll`
   - exactly one **target spellbook** — a blank or existing Iron's spellbook
3. **Activate.** Light the brazier by right-clicking it with an empty hand. Any other item in range aborts the ritual with a chat message.
4. **Cast.** The bound spell is now part of the book's collection and casts from Iron's native spell wheel like any other entry.

**Caution:** Ars Nouveau ships its own ritual also named **Binding** — a different ritual that makes Bound Scripts for familiars and ignores scrolls. It is easy to reach for by mistake if you are not looking closely. This ritual uses a three-block radius around the brazier and reads only dropped items.

---

## Unified Cooldowns

An optional system that groups similar spells across both mods into shared cooldown categories (OFFENSIVE, DEFENSIVE, UTILITY, MOVEMENT). When enabled, cooldowns are **global per category by design** — an Ars OFFENSIVE cast and an Iron's OFFENSIVE cast intentionally collide on the same slot. Disabled by default.

---

## Configuration

An **in-game config screen** is available from the mod list (**Mods → Ars 'n' Spells → Config**) for the master toggles, the mana mode (click to cycle), and the gear/debug switches — Save/Reset apply in singleplayer; on a server, edit the `.toml` or use `/ans`. Everything is also configurable via `config/ars_n_spells-common.toml`. Major options include:

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
A: Make sure Iron's Spells 'n Spellbooks (1.21.1-3.16.3+) is installed. Check logs for mixin load failures.

**Q: I see two mana bars.**
A: Verify your mana mode is set correctly, and check for overlay conflicts from other UI mods. Ars 'n' Spells hides the redundant bar per mode via NeoForge's `RenderGuiLayerEvent`; in `separate` and `disabled` modes both bars are shown by design.

**Q: Cross-casting an inscribed item does nothing.**
A: Cross-cast invocation is live — right-click an inscribed item to cast, sneak-right-click to cycle inscriptions. If nothing happens, confirm the item actually carries an inscription (re-run the Transcription ritual) and that the spell's mana cost can be paid from the active pool.

**Q: I found spell scrolls called "Ars Spell" in a loot chest, and their tooltip is a line of code.**
A: Fixed in 3.2.2. Those were internal proxy spells that should never have been eligible for loot. Update the mod and no new one can generate; right-click one you already have and it becomes a blank Iron's scroll.

**Q: The mod requires Sanctified Legacy and I had a setup using LP.**
A: The Sanctified Legacy integration is gone for this version because Sanctified Legacy itself hasn't shipped on NeoForge 1.21.1. Stay on the Forge 1.20.1 1.9.0 release if you need that feature.

---

## License

GNU GPLv3
