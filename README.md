# Ars 'n' Spells (v3.0.2)

Ars 'n' Spells bridges **Ars Nouveau** and **Iron's Spells 'n Spellbooks** for Minecraft 1.20.1 (Forge). It rests on three pillars: **mana unification** (five configurable modes for how the two pools interact), **cross-mod scaling and progression** (Iron's spell-power attributes scale Ars spells; both mods feed shared school progression and affinity), and **cross-casting** (inscribe spells from either mod onto arbitrary items, or export Ars spells onto real Iron's scrolls and spellbooks and cast them from Iron's native spell wheel). Optional integration with **Covenant of the Seven** (Sanctified Legacy) adds LP and aura-based casting through the Ring of Seven Curses and Ring of Seven Virtues.

## Requirements

| Mod | Version | Required |
| --- | --- | --- |
| Minecraft (Forge) | 1.20.1 / 47.4.0+ | Yes |
| Ars Nouveau | 4.12.7 – 4.12.x (`[4.12.7,4.13)`) | Yes |
| Iron's Spells 'n Spellbooks | 3.15.0 – 3.x (`[3.15.0,4.0.0)`) | No |
| Covenant of the Seven (Sanctified Legacy) | 2.2.6 recommended¹ | No |
| Blood Magic | Any | No |
| Apotheosis / Apothic Curios | Any | No² |
| Curios API | Any | Included via Ars |

If Iron's Spellbooks is not installed, Ars 'n' Spells falls back to native Ars behavior; all Iron's-dependent handlers, mixins, items, and recipes are gated on Iron's presence.

¹ The Covenant aura-bar HUD mixin is verified against Covenant 2.2.6; other versions still work, but the client logs a warning if the overlay bytecode has drifted.
² No hard dependency — mana stats on curios are read generically through the Curios API (see [Gear perks and enchantments](#gear-perks-and-enchantments)).

## Features

### Mana unification

Five modes are available via the `mana_unification_mode` config:

| Mode | Behavior |
| --- | --- |
| `iss_primary` (default) | Iron's mana is the single source of truth. Ars reads and drains Iron's pool. |
| `ars_primary` | Ars mana is authoritative. Iron's spells drain Ars mana. |
| `hybrid` | Shared bidirectional pool. A config option (`hybrid_mana_bar`) controls which HUD bar is displayed. |
| `separate` | Independent pools. Cross-mod casts split costs between both pools. |
| `disabled` | No mana integration; each mod uses its own pool. |

**Changing the mode:** in singleplayer, open Mods → Ars 'n' Spells → Config and click the **Mana Mode** row to cycle it (applies on *Done*). Server operators can run `/ans mode set <mode>`. Either path — or editing the config file directly — takes effect **without a restart** as of 2.0.1. Confirm the active mode with `/ans mode`.

Conversion rates (`conversion_rate_ars_to_iron`, `conversion_rate_iron_to_ars`) and dual-cost percentages are configurable.

### Cross-system mana regen bridge

Iron's `MANA_REGEN` is a percentage-of-pool multiplier; Ars regen is absolute mana/sec. All cross-system regen translations route through a conversion bridge so cross-mod Mana Regen enchantments and gear don't compound into absurd values. The strategy is configurable via `cross_system_regen_conversion` (`EQUAL_EFFECT` default, `REFERENCE_POOL`, or `DISABLED`), with `cross_system_regen_multiplier` and `cross_system_regen_reference_pool` as tuning knobs.

### Gear perks and enchantments

Ars and Iron's gear bonuses are routed to the active mana source:

- **iss_primary / hybrid** -- Ars gear perks apply to Iron's attributes.
- **ars_primary** -- Iron's gear perks apply to Ars calculations.
- **separate** -- Each mod's gear affects its own pool only.

Bonuses come from attribute modifiers, `IManaEquipment` implementations, mana-related enchantments, and Curios items.

Worn **curios** (rings, amulets, belts) have their attribute modifiers read the same way armor and weapons do, so mana stats applied by **Apotheosis / Apothic Curios** affixes and sockets — and by other curio mana gear like Magical Jewelry and Jewelcraft — feed the unified pool too. No hard dependency on Apotheosis is required; the read is generic and gated by the `read_curio_attribute_modifiers` config (default on). Apothic Attributes' combat stats (crit, armor pierce, fire/cold damage) are not mana or spell-power and are not bridged.

### Spell scaling

Ars spell potency scales with Iron's spell power attributes. The base `SPELL_POWER` attribute applies to every Ars cast; if the first glyph indicates an element (fire, ice, lightning, holy, ender, blood, evocation, nature, eldritch), the matching Iron's elemental spell-power attribute layers on additively. Affinity (per-school) and resonance (mana fullness) further shape the multiplier. The final scalar is clamped to `spell_power_cap` (default 3.0).

Implementation: scaling activates on each Ars `SpellCastEvent` and is applied within a 60-tick window to spell-flavored damage from the casting player. Iron's must be installed for the scaling path to fire — without Iron's, Ars spells use their native damage values.

### Resonance

Optional resonance tracks mana percentage and boosts spell damage when mana is above a configurable threshold (default 95%). Both directions can be toggled independently (`enable_ars_resonance`, `enable_irons_resonance`), with configurable strength, lingering duration after dropping below the threshold, and a damage-multiplier cap.

### Source Jar synergy

Standing near Ars Nouveau **Source Jars** passively boosts mana regeneration, multiplied by `source_jar_synergy_multiplier` (default 5.0). The scan never loads or waits on chunks, is position-cached (`source_jar_cache_move_threshold`), and as of 3.0.1 has a supported kill switch (`enable_source_jar_synergy`) plus scan tuning (`source_jar_scan_interval_ticks`, `source_jar_scan_radius`).

### Cooldowns

A unified cooldown system groups spells into four categories (OFFENSIVE, DEFENSIVE, UTILITY, MOVEMENT) and locks out *all* spells in that category — across both mods — while a cooldown is active. **Cooldowns are global per category, by design**: an Ars OFFENSIVE cast and an Iron's OFFENSIVE cast intentionally collide on the same slot. Earlier versions exposed a `modNamespace` parameter that suggested per-mod isolation; it never actually affected the storage and was removed in 1.9.0. Disabled by default.

### Progression and affinity

Casting builds **per-school progression** (cast counts persist) and **per-school affinity** (0–100 levels, recently used schools level up). Both systems work in **both directions** as of 1.9.0: Ars and Iron's casts each contribute to the same shared school maps, and progression-derived bonuses feed back into both Ars (via spell scaling) and Iron's (via the `<school>_spell_power` attribute) damage.

**Affinity decay** is opt-in via `enable_affinity_decay` (default `false` in fresh configs; pre-existing configs keep their previous value). When enabled, each player ticks every `affinity_decay_interval_ticks` (default 1200 = 60 s) and loses a fraction of every non-zero affinity level prorated from `affinity_decay_rate` (default 0.01 per Minecraft day).

### Cross-mod spell casting

Cross-casting lets any item store a spell from the *other* mod and cast it on right-click. Inscription is a two-tablet ritual flow inscribe/uninscribe pair backed by datapack recipes, so pack authors can retune ingredients without touching code.

**1. Craft the Spell Transcription tablet.**
Combine a novice Ars spellbook (reagent) with a tiered Iron's spellbook (any tier — the [`ars_n_spells:irons_spell_books`](src/main/resources/data/ars_n_spells/tags/items/irons_spell_books.json) item tag, pack-overridable), an archwood log, and a source gem block on the Enchanting Apparatus. Costs 2000 source. Recipe lives at [data/ars_n_spells/recipes/apparatus/spell_transcription.json](src/main/resources/data/ars_n_spells/recipes/apparatus/spell_transcription.json).

**2. Run the Spell Transcription ritual.**
Place the tablet on a Ritual Brazier, then drop two items within three blocks:
- exactly one **source** -- a filled Ars Nouveau spell parchment, focus, or spellbook, or an Iron's Spellbooks scroll
- exactly one blank **target** item

Strict disambiguation: more than one of either category fails the ritual with a chat message naming what it saw. Items already carrying an Ars Nouveau spell at NBT root are rejected as targets (they would let Ars's own right-click handler shadow the cross-cast). Items already carrying a cross-cast inscription are rejected too -- uninscribe first.

Activate the ritual; on completion the source is consumed and the target gains the `arsnspells:cross_spells` NBT list. Enchantment-glyph particles and the enchantment-table sound mark the inscribe.

**3. Cast the inscribed spell.**
Right-click the target. Sneak-right-click cycles between multiple inscriptions on the same item. Mana costs flow through `BridgeManager` and respect the active unification mode; in SEPARATE mode the dual-cost split (`dual_cost_ars_percentage` / `dual_cost_iss_percentage`) and conversion rates determine how much each pool pays per cast.

Cross-cast spells pay an overhead set by `cross_cast_cost_multiplier` (default `1.25`, range `0.5`-`5.0`). The multiplier applies to both Iron's spells cast from non-Iron's items and Ars spells cast from non-Ars items, once per cast, before mana deduction.

**4. Strip an inscription.**
Craft the Spell Uninscription tablet on the Enchanting Apparatus from a blank parchment (reagent), a water bucket, a source gem, and an archwood log -- 500 source. Drop one inscribed item within three blocks of the brazier with no other items in range. Ash and smoke particles plus a fire-extinguish sound mark the strip; the result is bit-identical to a fresh blank target so the same item can be re-inscribed cleanly. The uninscribe ritual is Iron's-independent and remains useful for cleanup even if Iron's Spellbooks is later removed.

---

## Export Ars spells onto Iron's scrolls and spellbooks (3.0.0)

New in 3.0.0: design an Ars Nouveau spell at the **Spell Loom**, export it onto a real **Iron's scroll**, bind it into a real **Iron's spellbook**, and then **select and cast it from Iron's own native spell wheel** — while it still runs through Ars 'n' Spells' cross-cast pipeline (mana, multiplier, scaling, cooldown). The Ars spell's data rides as an Ars 'n' Spells sidecar (`arsnspells:cross_spells`) on the real Iron item — Iron's per-slot format has no room for it — and a registered proxy spell slot is added to the book's native container so the entry shows in the wheel. The book's own Iron's spells are never overwritten. This leg requires Iron's Spellbooks to be installed.

**1. Export an Ars spell to an Iron's scroll — at the Spell Loom or by command.**
Build the spell with Ars Nouveau's normal tools (Scribe's Table / spellbook), then craft a **Spell Loom** (`G` gold / `L` lapis / `B` book / `O` obsidian on a crafting table). Right-click it, drop the Ars source (spell parchment, focus, or Ars spellbook) into the source slot and a **blank Iron's scroll** into the scroll slot, type a **name**, pick a **nature**, choose a rudimentary **icon**, and press **Inscribe** — the inscribed scroll appears in the output slot. (The op-only `/ans export_to_irons_scroll` still works for a quick, metadata-free export.)

**2. Bind the scroll into an Iron's spellbook.**
Hold the exported scroll and an Iron's spellbook and run `/ans bind_scroll_to_irons_book`, or run the **Spellbook Binding** ritual (its tablet is crafted on the Enchanting Apparatus for 2500 source and only registers when Iron's is loaded; recipe at [data/ars_n_spells/recipes/apparatus/spellbook_binding.json](src/main/resources/data/ars_n_spells/recipes/apparatus/spellbook_binding.json)) with the scroll and spellbook in range of the brazier. The scroll is consumed and the Ars entry is appended to the book — including any name/nature/icon chosen at the Spell Loom. Binding deduplicates by the serialized spell payload, so re-binding the same spell is rejected. Set `allow_ars_spells_in_irons_spellbooks=false` to disable binding, or `max_ars_cross_spells_per_irons_spellbook` to cap how many Ars spells a book may hold (`-1` = no cap, bounded by the native-wheel pool size of 8).

**3. Cast from the spellbook — through Iron's native spell wheel.**
Each bound Ars spell appears as its **own entry in Iron's native spell-selection wheel**, with the name and icon you chose. Select it like any Iron's spell and right-click the spellbook to cast — it runs the real Ars spell through Ars 'n' Spells' server-authoritative cross-cast pipeline (mana, the `cross_cast_cost_multiplier`, scaling, cooldown). Your book's native Iron's spells are untouched and cast exactly as before. Under the hood, each entry occupies one of a small pool of registered proxy spells (`ars_cross_1..8`); the real Ars data lives in the book's `arsnspells:cross_spells` sidecar, since Iron's own per-slot data has no room for it. Up to **8** Ars spells per book show in the wheel. (Generic inscribed items that aren't Iron's spellbooks still cast via right-click / sneak-cycle as before.)

---

## Covenant of the Seven integration

When Covenant of the Seven (Sanctified Legacy) is installed, two ring systems become available. (The Cursed Ring is also recognized from Enigmatic Legacy: both `covenant_of_the_seven:cursed_ring` and `enigmaticlegacy:cursed_ring` are detected.)

### Ring of Seven Curses (LP costs)

Wearing the Cursed Ring converts mana costs to **Life Points**. LP can be sourced from Blood Magic's Soul Network or player health. The whole system is gated by `enable_lp_system` (default on) — as of 3.0.1 every LP participant, including scroll casts and the Covenant-bypass path, honors this master toggle.

- **LP source modes** (`lp_source_mode`):
  - `BLOOD_MAGIC_PRIORITY` (default) -- Try Blood Magic first, fall back to health.
  - `BLOOD_MAGIC_ONLY` -- Blood Magic only; fails if BM is not installed.
  - `HEALTH_ONLY` -- Always use health (100 LP = 5 hearts).
- **Insufficient LP behavior** (`death_on_insufficient_lp`):
  - `false` (default) -- Spell is cancelled with 1 heart of damage.
  - `true` -- Spell casts but the player dies.
- LP costs scale with configurable base multipliers, per-tier multipliers (Ars glyph tiers), per-level and rarity multipliers (Iron's spells), and a minimum cost floor. Delayed-resolution spells stage costs in a per-player FIFO queue, so rapid back-to-back casts can't overwrite each other's pending cost.

### Ring of Seven Virtues (aura costs)

Wearing the Virtue Ring converts **Ars** mana costs to **Covenant aura**. The aura pool itself — maximum, regeneration, persistence, and HUD bar — is owned entirely by Covenant of the Seven; Ars 'n' Spells only maps the mana cost onto it. Iron's spells under the Virtue Ring are handled natively by Covenant.

- Gated by `enable_virtue_aura_system` (default on).
- The aura cost is `Ars mana cost × ars_virtue_aura_multiplier` (default 1.0, range 0.1–10).
- Insufficient aura cancels the spell with an action bar message.

**Ring conflict:** wearing both rings at once cancels both effects — costs fall back to normal mana and the player is notified.

### Blasphemy curios (school discounts)

Thirteen Blasphemy curios provide a base 15% mana discount (`blasphemy_discount`), plus an extra 10% when the spell school matches the curio's element (`blasphemy_matching_school_bonus`). Discounts stack multiplicatively with ring costs. A matching Blasphemy also discounts LP costs via `blasphemy_lp_discount` (default 0.85 = pay only 15%).

---

## Scroll cost enforcement

Iron's Spellbooks scrolls respect resource costs. The `scroll_cost_mode` config controls behavior:

| Mode | Behavior |
| --- | --- |
| `full` (default) | Scrolls consume mana and LP/aura like normal casting. |
| `lp_only` | Scrolls are mana-free but LP is still consumed for Cursed Ring wearers. |
| `free` | No resource cost for scrolls (LP from Cursed Ring still applies). |

---

## Commands

Root command: `/ans`. Mutating subcommands require permission level 2; read-only ones are unrestricted.

| Command | Permission | Description |
| --- | --- | --- |
| `/ans mode` | -- | Show the current mana unification mode |
| `/ans mode set <mode>` | Op 2 | Switch the mana unification mode live (no restart) |
| `/ans mana getdefault` | -- | View the current default max mana fallback |
| `/ans mana setdefault <1–100000>` | Op 2 | Set the default max mana fallback (persisted to config) |
| `/ans info <player>` | Op 2 | Diagnostics: mana, aura, resonance, and ring status (plus raw Iron's mana when Iron's is loaded) |
| `/ans debug` | Op 2 | Toggle debug logging at runtime |
| `/ans aura` | -- | Show your own current Covenant aura |
| `/ans export_to_irons_scroll` | Op 2 | Export the held Ars spell onto a real Iron's scroll (requires Iron's; the Spell Loom is the survival path) |
| `/ans bind_scroll_to_irons_book` | Op 2 | Bind a held exported scroll into a held Iron's spellbook (requires Iron's) |

---

## Configuration

Config file (per-world, server-authoritative, auto-synced to clients): `<world>/serverconfig/ars_n_spells-server.toml` — singleplayer: `.minecraft/saves/<World>/serverconfig/`; dedicated server: `<server>/world/serverconfig/`. (Changed from `config/ars_n_spells-common.toml` in 2.0.0 when the config became `ModConfig.Type.SERVER`; the old global file is ignored.) On multiplayer clients the in-game config screen is read-only ("Read-only: server-managed config").

> **Migration note:** the 3.0.1 audit removed a number of config keys that no code ever read (hybrid sync rate, mana overflow, per-glyph/per-school bonus tables, several resonance/cooldown caps, progression/affinity multipliers, discount stacking, per-cast reagent, mana sync/caching). If your TOML still contains them they are silently ignored. Every key below is live.

**Defaults at a glance** — enabled out of the box: mana unification (`iss_primary`), resonance, progression, affinity, LP system (Cursed Ring), virtue aura (Virtue Ring), curio discounts, Source Jar synergy, cross-cast inscription. Disabled out of the box: unified cooldowns, affinity decay, `death_on_insufficient_lp`, debug mode.

### Master toggles

| Option | Default | Description |
| --- | --- | --- |
| `mana_unification_mode` | `iss_primary` | Which mana pool is authoritative (`iss_primary`/`ars_primary`/`hybrid`/`separate`/`disabled`). The `disabled` value (and `enable_mana_unification=false`) means "native upstream pools pay; no sharing, no SEPARATE split, no ARS_PRIMARY conversion." Cross-cast still works in `disabled` mode; the `cross_cast_cost_multiplier` still applies. Changeable live via `/ans mode set` or the config screen. |
| `enable_mana_unification` | `true` | Enables all mana bridging logic. As of 2.0.0 this no longer disables cross-cast inscriptions — only the mana sharing between systems. |
| `enable_resonance_system` | `true` | Full-mana resonance bonuses. |
| `enable_cooldown_system` | `false` | Unified cooldown categories. |
| `enable_progression_system` | `true` | Cross-mod progression XP. |
| `enable_affinity_system` | `true` | Spell school affinity tracking. |
| `debug_mode` | `false` | Verbose logging. Enables `[CrossCastTrace]` end-to-end pipeline tracing — grep `latest.log` for `attempt=<uuid>` to get a single cast's full trace. |

### Mana unification

| Option | Default | Range | Description |
| --- | --- | --- | --- |
| `conversion_rate_ars_to_iron` | `1.0` | 0.01–10 | Multiplier for Ars costs paid from Iron's pool. |
| `conversion_rate_iron_to_ars` | `1.0` | 0.01–10 | Multiplier for Iron's costs paid from Ars pool. |
| `hybrid_mana_bar` | `irons` | `ars`/`irons` | Which HUD bar to show in `hybrid` mode. |
| `hide_mana_bar_with_ring` | `true` | -- | Hide mana bars while a Cursed or Virtue Ring is equipped. |
| `dual_cost_ars_percentage` | `0.5` | 0–1 | In `separate`, fraction taken from Ars pool. |
| `dual_cost_iss_percentage` | `0.5` | 0–1 | In `separate`, fraction taken from Iron's pool. The split is normalized so both halves always sum to the base cost. |
| `default_max_mana` | `100.0` | 1–100000 | Fallback max mana when the native system returns no value. Settable via `/ans mana setdefault`. |
| `respect_armor_bonuses` | `true` | -- | Route armor mana perks to the active source. |
| `respect_enchantments` | `true` | -- | Include mana enchantment bonuses. |
| `cross_system_regen_conversion` | `EQUAL_EFFECT` | see desc. | Regen unit conversion strategy: `EQUAL_EFFECT`, `REFERENCE_POOL`, or `DISABLED`. |
| `cross_system_regen_multiplier` | `1.0` | 0–100 | Scales converted cross-system regen. |
| `cross_system_regen_reference_pool` | `100.0` | 1–100000 | Reference pool size for the `REFERENCE_POOL` strategy. |

### Resonance system

| Option | Default | Range | Description |
| --- | --- | --- | --- |
| `enable_ars_resonance` | `true` | -- | Resonance bonuses for Ars spells. |
| `enable_irons_resonance` | `true` | -- | Resonance bonuses for Iron's spells. |
| `resonance_strength` | `1.0` | 0–10 | Global multiplier for all resonance bonuses. |
| `resonance_threshold` | `0.95` | 0–1 | Mana fraction required to trigger resonance. |
| `resonance_duration` | `100` | 0–1200 | Ticks resonance lingers after dropping below the threshold. |
| `max_damage_multiplier` | `5.0` | 1–100 | Cap on the resonance damage multiplier. |

### Cooldown system

| Option | Default | Range | Description |
| --- | --- | --- | --- |
| `enable_unified_cooldowns` | `false` | -- | Cross-mod cooldown sharing. |
| `enable_cross_mod_cooldowns` | `false` | -- | Cross-mod cooldown interference (false = each mod independent). |
| `cooldown_category_duration` | `100` | 0–10000 | Base category cooldown in ticks (20 = 1 s). |
| `cross_mod_cooldown_multiplier` | `0.5` | 0–10 | Multiplier applied to cross-mod cooldowns. |

### Progression and affinity

| Option | Default | Range | Description |
| --- | --- | --- | --- |
| `enable_cross_mod_progression` | `true` | -- | Ars casts grant Iron's school progression and vice versa. |
| `progression_bonus_per_cast` | `0.001` | 0–0.1 | Attribute bonus per cast in a school (+0.1%). Transient, so changes rescale everyone immediately. |
| `progression_bonus_cap` | `0.25` | 0–2 | Cap on the per-school progression bonus (+25%, hit after 250 casts at defaults). |
| `enable_affinity_decay` | `false` | -- | Opt-in affinity decay for unused schools. |
| `affinity_decay_rate` | `0.01` | 0–1 | Fraction of current affinity lost per Minecraft day. Decay accrues fractionally and removes a point only once a whole point accumulates (proportional, fixed in 3.0.2). |
| `affinity_decay_interval_ticks` | `1200` | 20–24000 | How often the decay handler ticks each player. |

### Curio discounts

| Option | Default | Range | Description |
| --- | --- | --- | --- |
| `enable_curio_discounts` | `true` | -- | Enable Blasphemy curio discounts. |
| `blasphemy_discount` | `0.15` | 0–1 | Base Blasphemy mana discount (15%). |
| `blasphemy_matching_school_bonus` | `0.10` | 0–1 | Extra discount for a matching school (+10%). |
| `read_curio_attribute_modifiers` | `true` | -- | Read mana attribute modifiers off worn curios (Apotheosis/Apothic Curios affixes & sockets, Magical Jewelry, etc.) and mirror them across the unified pool. |

### LP system (Cursed Ring)

| Option | Default | Range | Description |
| --- | --- | --- | --- |
| `enable_lp_system` | `true` | -- | Master toggle for the Cursed Ring LP system (honored by all LP paths, including scrolls, as of 3.0.1). |
| `lp_source_mode` | `BLOOD_MAGIC_PRIORITY` | see desc. | LP source order: `BLOOD_MAGIC_PRIORITY`, `BLOOD_MAGIC_ONLY`, or `HEALTH_ONLY`. |
| `death_on_insufficient_lp` | `false` | -- | Kill the player instead of cancelling the spell. |
| `show_lp_cost_messages` | `true` | -- | Action bar LP notifications. |
| `ars_lp_base_multiplier` | `1.0` | 0.1–100 | Base LP multiplier for Ars spells (mana × this = base LP). |
| `ars_lp_tier1_multiplier` | `1.5` | 0.1–10 | LP multiplier for Tier 1 glyphs. |
| `ars_lp_tier2_multiplier` | `2.0` | 0.1–10 | LP multiplier for Tier 2 glyphs. |
| `ars_lp_tier3_multiplier` | `2.5` | 0.1–10 | LP multiplier for Tier 3 glyphs. |
| `ars_lp_minimum_cost` | `10` | 1–10000 | Minimum LP per Ars cast (10 LP = 1 health). |
| `irons_lp_base_multiplier` | `0.5` | 0.1–100 | Base LP multiplier for Iron's spells. |
| `irons_lp_per_level_multiplier` | `0.1` | 0–10 | Additional LP per Iron's spell level (+10%/level). |
| `irons_lp_minimum_cost` | `10` | 1–10000 | Minimum LP per Iron's cast. |
| `irons_lp_common_multiplier` | `1.0` | 0.1–100 | LP multiplier for COMMON rarity Iron's spells. |
| `irons_lp_uncommon_multiplier` | `1.5` | 0.1–100 | LP multiplier for UNCOMMON rarity. |
| `irons_lp_rare_multiplier` | `2.0` | 0.1–100 | LP multiplier for RARE rarity. |
| `irons_lp_epic_multiplier` | `3.0` | 0.1–100 | LP multiplier for EPIC rarity. |
| `irons_lp_legendary_multiplier` | `5.0` | 0.1–100 | LP multiplier for LEGENDARY rarity. |
| `blasphemy_lp_discount` | `0.85` | 0–1 | LP discount when the matching Blasphemy curio is equipped (0.85 = pay only 15%). |

### Aura system (Virtue Ring)

The aura pool (max, regen, persistence, HUD) belongs to Covenant of the Seven; these keys only control how Ars mana costs map onto it.

| Option | Default | Range | Description |
| --- | --- | --- | --- |
| `enable_virtue_aura_system` | `true` | -- | Master toggle for the Virtue Ring aura-cost path. |
| `ars_virtue_aura_multiplier` | `1.0` | 0.1–10 | Multiplier applied to the Ars mana cost to derive the Covenant-aura cost. |
| `aura_failure_mode` | `open` | `open`/`closed` | What aura checks do when the Covenant/Nature's Aura reflection bridge is degraded (e.g. an untested Covenant update): `open` allows casts without an aura check (effectively free while degraded); `closed` blocks Virtue Ring casts until the bridge works. Either way the first degraded decision logs at WARN. |

### Scroll costs and spell scaling

| Option | Default | Range | Description |
| --- | --- | --- | --- |
| `scroll_cost_mode` | `full` | see desc. | Scroll resource cost mode (`full`/`lp_only`/`free`). |
| `spell_power_cap` | `3.0` | 1–10 | Maximum total spell power multiplier from Iron's attributes. |

### Source Jar synergy

| Option | Default | Range | Description |
| --- | --- | --- | --- |
| `enable_source_jar_synergy` | `true` | -- | Master kill switch for the proximity regen synergy. |
| `source_jar_scan_interval_ticks` | `20` | 1–200 | Ticks between proximity checks per player. |
| `source_jar_scan_radius` | `4` | 1–8 | Horizontal scan radius in blocks. |
| `source_jar_synergy_multiplier` | `5.0` | 0.1–100 | Multiplier for the proximity regen bonus. |
| `source_jar_cache_move_threshold` | `4.0` | 1–32 | Distance a player must move before re-scanning. |

### Cross-cast inscription and spellbook binding

| Option | Default | Range | Description |
| --- | --- | --- | --- |
| `cross_cast_cost_multiplier` | `1.25` | 0.5–5 | Overhead multiplier on spells cast from inscribed items, applied once per cast in every mode. |
| `allow_ars_spells_in_irons_spellbooks` | `true` | -- | Allow binding exported Ars spells into Iron's spellbooks. |
| `max_ars_cross_spells_per_irons_spellbook` | `-1` | -1–64 | Cap on Ars spells per Iron's spellbook (`-1` = no cap; hard-bounded by the proxy pool size of 8). |

### Rituals

These keys configure the Ritual of Mana Infusion and Ritual of the Mana Well. Both rituals are registered (Iron's-gated) but **currently have no craftable tablet or recipe, so they are not obtainable in survival**.

| Option | Default | Range | Description |
| --- | --- | --- | --- |
| `ritual_mana_infusion_amount` | `500.0` | 1–10000 | Mana added by the Ritual of Mana Infusion. |
| `mana_well_range` | `8` | 1–64 | Radius in blocks for the Mana Well effect. |
| `mana_well_regen_rate` | `2.0` | 0.1–100 | Mana per tick granted to players within Mana Well range. |

---

## For pack makers: datapack tags

All cross-mod item/block detection is tag-driven and datapack-extensible. Shipped defaults use `"required": false` entries, so every tag loads cleanly even when the referenced mod is absent. Extend a tag by shipping your own `data/ars_n_spells/tags/...` file (merged by default), or set `"replace": true` to restrict it.

| Tag | Type | Default contents | Controls |
| --- | --- | --- | --- |
| `ars_n_spells:irons_spell_books` | item | all 16 tiered Iron's spellbooks | Which books the Spell Transcription / Spellbook Binding ritual pedestals accept. |
| `ars_n_spells:cursed_rings` | item | Covenant + Enigmatic Legacy `cursed_ring` | Which rings trigger the LP-cost path. |
| `ars_n_spells:virtue_rings` | item | Covenant `virtue_ring` | Which rings trigger the aura-cost path. |
| `ars_n_spells:blasphemy_curios` | item | Covenant's 13 blasphemies | Which curios grant school discounts. School matching is by item path `<school>_blasphemy` (any namespace), so name custom entries accordingly (e.g. `mypack:fire_blasphemy`). |
| `ars_n_spells:source_jars` | block | Ars `source_jar`, `creative_source_jar` | Which blocks count for Source Jar regen synergy. |

Key economy knobs live in `ars_n_spells-server.toml`: LP multipliers (`ars_lp_*`, `irons_lp_*`, rarity ladder), `aura_failure_mode` (block vs. free casts when the Covenant bridge is degraded), `cross_cast_cost_multiplier`, and the master toggles listed above.

## Mana bars

The mod hides redundant mana bars based on mode:

- **iss_primary**: Hides Ars mana bar.
- **ars_primary**: Hides Iron's mana bar.
- **hybrid**: Shows the bar selected by `hybrid_mana_bar`.
- **separate / disabled**: Both bars may show.

While a Cursed or Virtue Ring is equipped, both mana bars are hidden (`hide_mana_bar_with_ring`, default on); the Covenant aura bar takes over for Virtue Ring wearers.

## Changelog

Full version history lives in [CHANGELOG.md](CHANGELOG.md). Recent highlights:

- **3.0.2 — Second audit pass: decay fix, pack-maker tags, advancements.** Affinity decay now follows its documented proportional curve (it previously drained a flat point per interval, ~20x too fast); ring/Blasphemy/Source Jar detection moved to datapack-extensible tags (`ars_n_spells:cursed_rings`, `virtue_rings`, `blasphemy_curios`, `source_jars`); a four-step advancement chain guides the Spell Loom workflow; new `aura_failure_mode` lets servers block (instead of free-allow) Virtue Ring casts when the Covenant bridge is degraded, and untested Covenant versions now announce themselves in chat; the progression bonus curve is config-driven (`progression_bonus_per_cast`/`progression_bonus_cap`); spell-school classification uses an explicit glyph map (Firework is no longer "fire school"); the Spell Loom screen got the high-contrast readability treatment with region tooltips; `mods.toml` gained issue-tracker/homepage/update-checker metadata. See [CHANGELOG.md](CHANGELOG.md).
- **3.0.1 — Full-codebase audit remediation.** The Spell Transcription and Spellbook Binding tablets are craftable again when Iron's is installed (recipes now use the pack-overridable [`ars_n_spells:irons_spell_books`](src/main/resources/data/ars_n_spells/tags/items/irons_spell_books.json) tag instead of a nonexistent item id); `enable_lp_system` is honored by every LP path (closing a double-penalty and a scrolls-charge-LP-when-disabled inconsistency); `mana_unification_mode = "disabled"` is respected consistently by equipment bridging, Source Jar synergy, and mana-bar hiding; config screen readability rewrite with a proper multiplayer read-only mode; Source Jar synergy kill switch and scan tuning; fixed a startup crash on installs without Iron's; pruned zero-reader config keys; assorted dead-code cleanup. See [AUDIT_FINDINGS.md](AUDIT_FINDINGS.md).
- **3.0.0 — Ars → scroll → spellbook export.** The Spell Loom workstation, Spellbook Binding ritual, and `/ans export_to_irons_scroll` / `/ans bind_scroll_to_irons_book` commands carry an Ars spell onto real Iron's items, cast through Iron's native spell wheel via a registered proxy-spell pool. Also fixed the pending-cost race (per-player FIFO queues) and added Iron-loaded GameTests.
- **2.6.x — Apotheosis curio mana bridge, mana-bridge correctness fixes, ring/HUD polish, and the Source Jar chunk-load deadlock fix.**
- **2.0.x — Audit-driven major release.** Server-authoritative cross-casting (dedicated servers work again), cross-cast decoupled from mana unification, end-to-end pipeline tracing, capability resync on login/respawn/dimension change, live mana-mode switching, config moved to `ModConfig.Type.SERVER`.

## Troubleshooting

- **Ars mana not changing in iss_primary**: Ensure Iron's is installed (3.15.x+). Check logs for mixin failures.
- **Spell Transcription / Spellbook Binding tablet uncraftable**: Fixed in 3.0.1 — update the mod. The recipes accept any tiered Iron's spell book via the `ars_n_spells:irons_spell_books` item tag, which datapacks can override.
- **Gear perks not affecting mana**: Confirm `respect_armor_bonuses=true` and the correct mode. In `separate`, perks are not cross-applied.
- **Double mana bars**: Verify your mode and check for overlay conflicts from other UI mods.
- **"Insufficient LP" despite enough hearts**: Ensure `lp_source_mode` is not `BLOOD_MAGIC_ONLY` without Blood Magic installed. Default is `BLOOD_MAGIC_PRIORITY`.
- **Scrolls casting for free**: Verify `scroll_cost_mode` is set to `full` (default).
- **Aura bar looks wrong with a newer Covenant version**: The aura-bar HUD mixin is verified against Covenant 2.2.6; the client logs a warning when the overlay has drifted. Gameplay is unaffected.

## Building from source

1. Install JDK 17.
2. Run:

```bash
./gradlew build
```

Dependencies (Ars Nouveau, Iron's Spellbooks) resolve automatically from CurseMaven; no manual jar placement required.

Output jar: `build/libs/ars_n_spells-3.0.2.jar` (version tracks `mod_version` in `gradle.properties`).

To run the Iron-loaded GameTest scenarios (CYCLE, export→bind→coexist round-trips), use the opt-in profile: `./gradlew runGameTestServer -PwithIronsRuntimeGameTests`.

## License

GNU GPLv3
