# Ars 'n' Spells (NeoForge 1.21.1)

Ars 'n' Spells bridges **Ars Nouveau** and **Iron's Spells 'n Spellbooks** for Minecraft 1.21.1 on **NeoForge**. It unifies mana, scaling, and progression while keeping each mod playable on its own.

3.4.0 fixes Ars spells with **Touch** cast from scrolls and other inscribed items: they now strike the block or creature you aim at. It also adds the **Glyph Press**, which turns a filled Iron's scroll into an Ars glyph for that spell at that level. You learn the glyph like any other and build it into your own Ars spells. Client and server both need 3.4.0 (network protocol 9). See [CHANGELOG.md](CHANGELOG.md) and [docs/3.4.0](docs/3.4.0/glyph-press.md).

3.3.5 ends channelled Iron's spells cleanly on the last pulse the caster can pay for, instead of stopping them with a payment error, and stops Iron's regeneration from lowering the Ars pool in `ars_primary`. Shortage messages now name the resource, the amount needed and the amount available. A new setting, `inscribed_ars_default_cooldown_ticks`, gives Ars spells cast from Iron's spell wheel a native cooldown. This build is tested with Ars Affinity 1.1.1. See [CHANGELOG.md](CHANGELOG.md) and [docs/3.3.5](docs/3.3.5/testing.md).

3.3.4 ports the Forge casting/payment repair and closes the supported-feature differences recorded in the [parity review](docs/3.3.4/parity-review.md). Both client and server require this build (network protocol 7). New configurations use Forge’s 95% resonance threshold and primary-school damage policy; existing saved values remain in effect.

3.3.0 includes the economic, school/inscription, network, equipment and icon/background audit implementation. See the [full audit closure ledger](docs/3.3.0-audit-status.md) for fresh validation and open acceptance gates. Compilation or an installed dependency alone is not a runtime support certification.

Pinned development targets: **Ars Nouveau 5.13.1.1400**, **Iron's Spells 1.21.1-3.16.3**, **NeoForge 21.1.248**, **Java 21**. Other accepted versions in the loader metadata require their own runtime evidence.

## Requirements

| Mod | Version | Required |
| --- | --- | --- |
| Minecraft (NeoForge) | 1.21.1 / 21.1.0+ (built against 21.1.248) | Yes |
| Java | 21 | Yes |
| Ars Nouveau | 5.13+ (built against 5.13.1.1400) | Yes |
| Iron's Spells 'n Spellbooks | 1.21.1-3.16.3 up to, not including, 4.0.0 (built against 3.16.3) | No |
| Ars Affinity | 1.1.1 tested (needs Ars Elemental) | No |

If Iron's Spellbooks is not installed, Ars 'n' Spells falls back to native Ars behavior. The mod will not load on Forge or on Minecraft versions other than 1.21.1.

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

Conversion rates (`conversion_rate_ars_to_iron`, `conversion_rate_iron_to_ars`) and dual-cost percentages are configurable.

### Gear perks and enchantments

Ars and Iron's gear bonuses are routed to the active mana source:

- **iss_primary / hybrid** — Ars gear perks apply to Iron's attributes.
- **ars_primary** — Iron's gear perks apply to Ars calculations.
- **separate** — Each mod's gear affects its own pool only.

In `iss_primary`, Iron's pool is the only pool, so Iron's `MAX_MANA` is sized to hold what either mod grants: the larger of Ars's own maximum (base pool, glyph bonus, book tier, perks and gear) and Iron's own maximum plus the Ars gear bonus (the max-mana contribution of worn equipment and, with `read_curio_attribute_modifiers` on, curios), each Ars amount times `conversion_rate_ars_to_iron`. The larger, not the sum, so Ars gear is never counted twice. The Ars gear regen, times the same rate, is added to Iron's `MANA_REGEN`. In `hybrid` and `ars_primary`, Iron's `MAX_MANA` is instead raised to cover Ars's real maximum (the shared-pool ceiling), because Iron's clamps every mana write to that attribute; `hybrid` also mirrors the Ars gear regen. With `respect_armor_bonuses` off the `iss_primary` additions and the gear bonuses are dropped, but the `hybrid` ceiling stays. The enchantment and curio toggles decide what is transferred: in `iss_primary` they keep what they cover out of the Ars maximum as well as the gear bonus, and they never lower the shared-pool ceiling of the other modes. Both loaders apply exactly these rules.

### Mana potions

When Iron's is the primary pool (`iss_primary`), Ars mana potions feed the unified pool instead of the now-unread Ars pool: each level of `ars_nouveau:mana_regen` adds 0.5 mana/sec (times `conversion_rate_ars_to_iron`, converted through the regen bridge) to Iron's `MANA_REGEN`, refreshed every tick. The potion's own modifier on Ars's regen attribute is not gear, so it is mirrored once. Leaving `iss_primary` removes the mirrored modifier. Requires Iron's; no-op in the other modes (Ars handles its own pool natively there).

### Pre-cast validation

Affordability is checked by the payment layer against the mode-correct pool and the final price: an Ars cast through the resolver's own mana check, an Iron's cast when Iron's writes its mana debit. An Iron's shortage is refused with an action-bar message naming the resource, the spell, the price and the balance; an Ars shortage shows Ars's own not-enough-mana message. Creative casts always pass. The Forge build checks the same way.

### Spell scaling

Cross-mod spell damage scales bidirectionally when Iron's Spellbooks is installed:

**Iron's → Ars:** Ars spell damage multiplies by `globalSpellPower + (schoolSpellPower - 1.0)`, where `globalSpellPower` is Iron's generic `SPELL_POWER` and `schoolSpellPower` is the matching elemental attribute for the spell's resolved school(s). When a spell resolves to multiple schools (dual-element addon glyphs, compound-school recipes), the configured `multi_school_power_policy` (`primary` [default], `max`, or `average`) decides how those schools combine. The result is then multiplied by affinity (per-school) and resonance (mana fullness) bonuses. The final scalar is clamped to `spell_power_cap` (default 3.0).

**Ars → Iron's:** Iron's spell damage adds the caster's Ars `SPELL_DAMAGE_BONUS` perk value as a flat addition. This value is picked up automatically from any source — Ars armor threads, curios with Spell Damage modifiers, potions, or other mods. Plain Ars armor with no offensive perk grants no Iron's damage bonus. The perk addition is **not** subject to the `spell_power_cap`.

**Multi-school policy split:** damage scaling uses all schools the policy resolves; affinity and progression credit the primary (first-resolved) school only. This means a dual-element spell scales with the caster's better element and trains exactly one affinity track.

Implementation: both directions subscribe directly to spell-damage events (`SpellDamageEvent.Pre` from Ars Nouveau and `SpellDamageEvent` from Iron's) with no time window or damage-string guessing. Iron's must be installed for cross-mod scaling to fire — without Iron's, Ars spells use their native damage values, and Iron's applies no Ars perk bonus. Cross-mod combat scaling is independent of the mana-unification mode and remains active even when mana unification is `DISABLED`.

### Resonance

Optional resonance tracks mana percentage and boosts Iron's spell damage when mana is above a configurable threshold (default 95%).

### Cooldowns

A unified cooldown system groups spells into four categories (OFFENSIVE, DEFENSIVE, UTILITY, MOVEMENT) and locks out *all* spells in that category — across both mods — while a cooldown is active. **Cooldowns are global per category, by design**: an Ars OFFENSIVE cast and an Iron's OFFENSIVE cast intentionally collide on the same slot. Disabled by default.

**Inscribed Ars spell cooldown (3.3.5).** An Ars spell bound into an Iron's spellbook can put its wheel slot on an Iron's cooldown after each successful cast. Set the length with `inscribed_ars_default_cooldown_ticks` (20 ticks = 1 s); new configs default to 40 ticks, and configs from 3.3.4 or earlier migrate to 0, which is no cooldown as before. The cooldown uses Iron's own system, so Iron's cooldown reduction, the wheel's cooldown display and saving across relogs work as for any Iron's spell. A cast that fails, is refused or cannot be paid starts no cooldown. Each wheel slot is a shared proxy spell (`ars_n_spells:ars_cross_N`), so two books that bind different Ars spells to the same slot number share that slot's cooldown. Ordinary Ars casts, ordinary Iron's spells and Iron's spells inscribed into Ars items are unaffected. If category cooldowns are on, both checks apply to these casts.

### Progression and affinity

Casting builds **per-school progression** (cast counts persist) and **per-school affinity** (0–100 levels, recently used schools level up). Both systems work in **both directions**: Ars and Iron's casts each contribute to the same shared school maps, and progression-derived bonuses feed back into both Ars (via spell scaling) and Iron's (via the `<school>_spell_power` attribute) damage.

**Affinity decay** is opt-in via `enable_affinity_decay` (default `false` in fresh configs). When enabled, each player ticks every `affinity_decay_interval_ticks` (default 1200 = 60 s) and loses a fraction of every non-zero affinity level prorated from `affinity_decay_rate` (default 0.01 per Minecraft day).

### Cross-mod spell casting

Cross-casting lets any item store a spell from the *other* mod and cast it on right-click. Inscription is a two-tablet ritual flow backed by datapack recipes, so pack authors can retune ingredients without touching code.

**1. Craft the Spell Transcription tablet.**
Combine a novice Ars spellbook (reagent) with an Iron's spellbook, an archwood log, and a source gem block on the Enchanting Apparatus. Costs 2000 source. Recipe lives at [`data/ars_n_spells/recipes/apparatus/spell_transcription.json`](src/main/resources/data/ars_n_spells/recipes/apparatus/spell_transcription.json). The recipe is gated with `neoforge:conditions` `mod_loaded irons_spellbooks`, so it only loads when Iron's Spells is installed.

**2. Run the Spell Transcription ritual.**
Place the tablet on a Ritual Brazier, then drop two items within three blocks:
- exactly one **source** — a filled Ars Nouveau spell parchment, focus, or spellbook, or an Iron's Spellbooks scroll
- exactly one blank **target** item

Strict disambiguation: more than one of either category fails the ritual with a chat message naming what it saw. Items already carrying a cross-cast inscription are rejected too — uninscribe first.

Light the brazier by right-clicking it with an **empty main hand** (right-clicking while holding something tries to feed that item to the brazier instead). The ritual burns for about three seconds; on completion the source is consumed and the target gains a `CrossModSpellList` data component. Enchantment-glyph particles and the enchantment-table sound mark the inscribe.

**3. Cast the inscribed spell.**
Right-click the target. Sneak-right-click cycles between multiple inscriptions on the same item. Mana costs flow through `BridgeManager` and respect the active unification mode; in SEPARATE mode the dual-cost split (`dual_cost_ars_percentage` / `dual_cost_iss_percentage`) and conversion rates determine how much each pool pays per cast.

Cross-cast spells pay an overhead set by `cross_cast_cost_multiplier` (default `1.25`, range `0.5`–`5.0`). The multiplier applies to both Iron's spells cast from non-Iron's items and Ars spells cast from non-Ars items, once per cast, before mana deduction.

**4. Strip an inscription.**
Craft the Spell Uninscription tablet on the Enchanting Apparatus from a blank parchment (reagent), a water bucket, a source gem, and an archwood log — 500 source. Drop one inscribed item within three blocks of the brazier with no other items in range. Ash and smoke particles plus a fire-extinguish sound mark the strip; the result is bit-identical to a fresh blank target so the same item can be re-inscribed cleanly. The uninscribe ritual is Iron's-independent and remains useful for cleanup even if Iron's Spellbooks is later removed.

### Ars spells in Iron's native spell wheel (3.0.x)

Beyond the generic right-click cross-cast, an Ars spell can be bound into a real Iron's spellbook where it appears as **its own entry in Iron's spell-selection wheel**, with a player-chosen name, icon, background and school, cast through Iron's native flow:

**1. Make a carrier scroll.** Craft the **Spell Loom** (gold ingot over lapis-book-lapis over three obsidian). Insert an Ars spell source (filled parchment, focus, or spellbook) and a blank Iron's scroll, type a display name, set the spell's **School** (Automatic by default; see [Choosing a spell school](#choosing-a-spell-school-337)), optionally use **Choose icon…** for the cosmetic icon and background frame, and press *Inscribe*. The output is a real `irons_spellbooks:scroll` carrying the Ars spell as an ANS component sidecar. (`/ans export_to_irons_scroll [school]` is the admin shortcut.)

**2. Bind it onto a spellbook.** Craft the **Spellbook Binding** tablet on the Enchanting Apparatus (novice spellbook reagent; any Iron's spellbook, an Iron's scroll, a source gem block, and an archwood log on pedestals — 2500 source). The ritual reads **dropped item entities, not inventories**: place the tablet on a Ritual Brazier, throw (`Q`) exactly one carrier scroll and one Iron's spellbook on the ground within **three blocks** of the brazier, then light it by right-clicking the brazier with an **empty main hand**. It burns for about three seconds. Anything else lying inside that radius aborts the run with `unexpected item(s) in range` rather than risk binding the wrong stack. One scroll is consumed; the book gains the Ars spell. (`/ans bind_scroll_to_irons_book` binds the two items you hold, one per hand, and refuses when `allow_ars_spells_in_irons_spellbooks` is off.)

> **This is not Ars Nouveau's "Binding" ritual.** Ars ships `ars_nouveau:ritual_binding`, whose display name is exactly **Binding**; it converts nearby mobs into Bound Scripts for familiars and ignores dropped scrolls entirely. The tablet you want is **Spellbook Binding** (`ars_n_spells:spellbook_binding`, shown as *Ritual of Spellbook Binding* on the brazier). Lighting the wrong one looks identical to the feature being broken, because neither ritual says anything about the other.

**3. Cast from the wheel.** The bound spell shows in Iron's spell wheel as its own entry (name/icon from the loom; defaults otherwise). Casting delegates to the ANS cross-cast pipeline, so the cost multiplier, unification mode, and SEPARATE dual-cost split all apply — exactly once (the wheel entry itself is a zero-cost proxy spell).

Mechanically, each book can hold up to **8** bound Ars spells: they are driven by a finite pool of registered proxy spells (`ars_n_spells:ars_cross_1..8`), because Iron's wheel merges entries by spell id. Proxy slots are added into grown container capacity, so the player's real Iron's spells are never evicted. Binding is gated by `allow_ars_spells_in_irons_spellbooks` and capped by `max_ars_cross_spells_per_irons_spellbook`. Every route keeps the scroll's name, background, icon and school. A spell the book already holds with the same glyphs **and the same school** is refused as a duplicate, even when its name, colour, sound, background or icon differ; the same glyphs with a different school (or Automatic next to a chosen school) bind as a separate entry with its own wheel slot.

#### Choosing a spell school (3.3.7)

A custom spell's **school** is the school Ars 'n' Spells uses for that spell's mechanics. It is separate from the **background** frame picked under **Choose icon…**: the background (stored as the entry's `nature`) has always been cosmetic, and still is.

- **Automatic (from glyphs)** — the default, and what every scroll made before 3.3.7 uses. The glyphs decide the school or schools as before, and a spell with several glyph schools keeps its `multi_school_power_policy` scaling.
- **A chosen school** — exactly one school replaces the glyph schools for spell-power scaling (that school's Iron's spell-power attribute), affinity and progression. The glyph schools add nothing on top. The glyphs themselves never change: same effects, same damage types, same cooldown category.

The picker offers every installed Iron's school that has a spell-power attribute — the nine built-in schools plus any addon schools, which the server checks against Iron's school registry. Native Ars Nouveau casts and ordinary Iron's scrolls are unaffected.

**Inscribe a scroll with a chosen school**

1. Open the Spell Loom. Put the Ars source in the source slot and a blank Iron's scroll in the target slot.
2. Press the **School** button. While it is Automatic it reads `School: Auto (…)`, listing the glyph schools Automatic would use.
3. In the **Spell school** picker, choose a school (**Next**/**Back** page through them) or **Automatic (from glyphs)**, then press **Done**. The button now reads, for example, `School: Ice`; hover it, or open **Details**, to preview what the choice does.
4. Type a name, optionally use **Choose icon…**, and press **Inscribe**. The scroll's tooltip reads `School: Ice (chosen)`.

**Change the school of a finished scroll** without rebuilding its spell:

1. Put the carrier scroll in the Loom's target slot. The source slot can stay empty.
2. Use the **School** button to pick the new school, or Automatic.
3. Press **Apply**. Only the school changes: the spell, name, background and icon stay, nothing is consumed, and the scroll stays in the target slot.

Operators can do the same to a held item with `/ans school set <school>` or `/ans school set auto`, adding an entry number for a spellbook that holds several Ars spells. `/ans school list` lists the schools the server accepts, and `/ans school show` lists each Ars entry on the held item with its school.

**Where the school goes.** The choice is stored on the spell entry (the optional `school` field of the `ars_n_spells:cross_spells` component), so it survives saving and reloading, copying, inventory moves and every binding route (Iron's Inscription Table, Spellbook Binding ritual, `/ans bind_scroll_to_irons_book`). It applies to casts from the scroll itself and from Iron's spell wheel. A delayed effect or a projectile still in flight keeps the school of the cast that launched it, whatever its caster casts or holds afterwards.

**Server rules.** The server checks every request. A school that is unknown, malformed or not installed is refused, and the refused operation moves and consumes nothing. Setting `allow_spell_school_override = false` limits the Loom and the commands to Automatic; scrolls that already carry a chosen school keep it stored but cast as Automatic until the setting is turned back on. If the addon that supplied a chosen school is removed, the spell casts as Automatic and its tooltip says so; the choice returns if the addon does.

### Touch spells on scrolls (3.4.0)

An Ars spell with Touch on a Spell Loom scroll, an inscribed item, or a bound Iron's spell book now behaves as it does in an Ars spell book. Right-clicking strikes the block or creature in front of you, within normal reach. With nothing in reach, the cast is refused: no mana, no cooldown. The server picks the target itself; the client's aim is only a hint. Self, Projectile and other forms keep their usual behaviour. Existing scrolls and books work without being remade. Details: [docs/3.4.0/touch-regression.md](docs/3.4.0/touch-regression.md).

### Glyph Press and imported Iron's glyphs (3.4.0)

The **Glyph Press** turns a filled Iron's scroll into a learnable Ars glyph for that spell at the scroll's level, for example a Firebolt III scroll into a **Glyph of Firebolt III**. Each conversion uses the scroll, a **Blank Glyph Plate**, Source Gems, and Source from nearby Source Jars or a Source Relay. Learn the glyph by using it, then add it to any Ars spell:

- **Self** for buffs on you;
- **Touch** or **Projectile** for creatures, blocks or points;
- Amplify and Dampen change the effective level, Accelerate and Decelerate the charge time or projectile speed, Extend Time and Duration Down a channel's length.

The Iron's spell keeps its own rules: cast time, channel pulses, cooldown, recasts, Eldritch learning and events. Each imported effect is charged once, in your mana mode.

All 111 ordinary player spells in Iron's 3.16.3 have glyphs, 777 in all; [docs/3.4.0/iron-glyph-coverage.md](docs/3.4.0/iron-glyph-coverage.md) lists them. Without Iron's, pressed glyphs stay as dormant ANS glyphs and never turn into a different glyph. Guide: [docs/3.4.0/glyph-press.md](docs/3.4.0/glyph-press.md). Data-pack tuning and addon spells: [docs/3.4.0/adapter-authoring.md](docs/3.4.0/adapter-authoring.md).

### Cross-cast storage (1.21.1)

On NeoForge 1.21.1 the cross-cast inscription is stored as a `DataComponentType<CrossModSpellList>` registered under `ars_n_spells:cross_spells` ([`ModDataComponents`](src/main/java/com/otectus/arsnspells/spell/ModDataComponents.java)). The component carries an immutable list of inscribed entries plus the currently-selected index for sneak-cycling, with both a JSON `Codec` and a `StreamCodec` for network sync. The Forge-era root-NBT keys (`arsnspells:cross_spells`, `arsnspells:cross_spell_index`) are gone; items inscribed under the previous Forge build will not migrate automatically.

### Player state (1.21.1)

Affinity, cooldown, and progression are stored as NeoForge entity attachments ([`AttachmentTypes`](src/main/java/com/otectus/arsnspells/data/AttachmentTypes.java)). Affinity and progression carry `copyOnDeath`; cooldown intentionally resets on respawn. The Forge capability provider that previously wrapped these has been removed.

---

## Configuration

Config file: `<world>/serverconfig/ars_n_spells-server.toml` (a server config, sent to clients on login). `defaultconfigs/ars_n_spells-server.toml` seeds new worlds. The older global `config/ars_n_spells-common.toml` is no longer read. Every key, with its default and bounds: [docs/3.4.0/config-reference.md](docs/3.4.0/config-reference.md).

An **in-game config screen** is available from the mod list (**Mods → Ars 'n' Spells → Config**), registered via NeoForge's `IConfigScreenFactory`. It exposes the master toggles, the mana-unification mode (click the row to cycle), and the gear/debug switches. Because the gameplay config is a SERVER config, the Save / Reset controls are active only in singleplayer; on a dedicated server, edit the server `.toml` or use `/ans`.

### Master toggles

| Option | Default | Description |
| --- | --- | --- |
| `enable_mana_unification` | `true` | Enables all mana bridging logic. |
| `mana_unification_mode` | `iss_primary` | Which mana pool is authoritative. |
| `enable_resonance_system` | `true` | Full-mana resonance bonuses (Iron's). |
| `enable_cooldown_system` | `false` | Unified cooldown categories. |
| `enable_progression_system` | `true` | Cross-mod progression XP. |
| `enable_affinity_system` | `true` | Spell school affinity tracking. |
| `enable_affinity_decay` | `false` | Periodic affinity decay (opt-in). |
| `affinity_decay_interval_ticks` | `1200` | Ticks between decay handler runs (range 20–24000). |
| `affinity_decay_rate` | `0.01` | Fraction of each non-zero affinity lost per Minecraft day. |
| `debug_mode` | `false` | Verbose logging. |

### Mana conversion

| Option | Default | Description |
| --- | --- | --- |
| `conversion_rate_ars_to_iron` | `1.0` | Multiplier for Ars costs paid from Iron's pool. |
| `conversion_rate_iron_to_ars` | `1.0` | Multiplier for Iron's costs paid from Ars pool. |
| `dual_cost_ars_percentage` | `0.5` | In `separate`, fraction taken from Ars pool. |
| `dual_cost_iss_percentage` | `0.5` | In `separate`, fraction taken from Iron's pool. |
| `hybrid_mana_bar` | `irons` | Which HUD bar to show in `hybrid` mode (`ars` or `irons`). |

### Spell scaling

| Option | Default | Description |
| --- | --- | --- |
| `enable_cross_mod_combat_stats` | `true` | Master switch for Iron's → Ars and Ars → Iron's spell damage scaling. Active even when mana unification is `disabled`. |
| `enable_irons_power_for_ars_damage` | `true` | Iron's spell power multiplies Ars spell damage. |
| `enable_ars_damage_for_irons_damage` | `true` | Ars Spell Damage Bonus adds to Iron's spell damage. |
| `spell_power_cap` | `3.0` | Maximum total spell power multiplier from Iron's attributes. Caps multipliers only; does not apply to the Ars flat bonus. |
| `multi_school_power_policy` | `primary` | How multiple matching schools combine: `primary` (first-resolved only), `max` (strongest single value), `average` (mean of all). |
| `source_jar_synergy_multiplier` | `5.0` | Multiplier for Source Jar proximity regen bonus. |
| `ritual_mana_infusion_amount` | `500.0` | Mana added by Ritual of Mana Infusion. |

### Cross-cast

| Option | Default | Description |
| --- | --- | --- |
| `cross_cast_cost_multiplier` | `1.25` | Overhead applied to cross-cast spell base mana cost. |
| `allow_ars_spells_in_irons_spellbooks` | `true` | Allow binding Ars spells into Iron's spellbooks / spell wheel. |
| `max_ars_cross_spells_per_irons_spellbook` | `-1` | Per-book cap on bound Ars spells (-1 = no cap; hard-bounded by the 8-slot proxy pool). |
| `allow_spell_school_override` | `true` | Allow a chosen school on custom Ars spells (Spell Loom **School** button, `/ans school set`, `/ans export_to_irons_scroll <school>`). `false` limits all three to Automatic; spells that already carry a chosen school keep it stored but cast as Automatic. See [Choosing a spell school](#choosing-a-spell-school-337). |
| `inscribed_ars_default_cooldown_ticks` | `40` (new configs), `0` (migrated from ≤3.3.4) | Iron's cooldown, 0–12000 ticks, started on an Iron's-wheel slot after its bound Ars spell casts successfully. `0` = none. Applies from the next cast after a config reload, and Iron's cooldown reduction applies. Iron's per-spell config files are not read for the `ars_cross_N` proxies. Also on the in-game settings screen. |

### Imported Iron's glyphs and the Glyph Press (3.4.0)

| Option | Default | Description |
| --- | --- | --- |
| `enable_iron_glyphs` | `true` | Imported glyphs can be learned and cast. Off makes them dormant; they stay in books and in what players know. |
| `enable_glyph_press` | `true` | Glyph Presses start new jobs. Off cancels running jobs and returns their inputs. |
| `iron_glyph_mana_bonus_policy` | `per_spell` | `per_spell` counts the levels of one Iron's spell as one glyph for Ars's glyph mana bonus; `native_per_variant` counts each. |
| `max_imported_effects_per_spell` | `4` | Imported glyphs allowed in one Ars spell (1–8). |
| `max_foreign_emissions_per_cast` | `16` | Imported-effect executions one cast may make, splits and repeats included (1–64). |
| `max_active_glyph_sessions_per_player` | `8` | Casts with imported glyphs one player may have running at once (1–32). |
| `glyph_press_source_capacity` | `4000` | Source a Press stores (500–100000). |
| `glyph_press_source_scan_interval_ticks` | `20` | How often a Press waiting for Source pulls from nearby jars (5–200). |
| `glyph_press_source_radius` | `4` | How far it looks for loaded Source Jars (1–8). |

### Source Jar synergy

| Option | Default | Description |
| --- | --- | --- |
| `enable_source_jar_synergy` | `true` | Kill switch for the proximity-regen scan (off = zero per-tick cost). |
| `source_jar_scan_interval_ticks` | `20` | Ticks between proximity checks per player (1–200). |
| `source_jar_scan_radius` | `4` | Horizontal scan radius in blocks (1–8; the scan never loads chunks). |

### Covenant of the Seven integration (removed in 3.2.1)

This NeoForge adapter does not include the Cursed Ring LP subsystem, Virtue Ring aura or Blasphemy integration from the Forge build. Its configuration therefore exposes mana-only payment behavior.

`virtue_ring_discount` and `max_total_curio_discount` are kept and live — they were repurposed as the generic `#ars_n_spells:curio_spell_discount` per-curio discount and its stacking cap, extensible through datapack tags.

`scroll_cost_mode` is live: `full` charges scroll casts through the unified pool (Iron's scrolls never deduct mana natively), and `free` removes the cost entirely. The `lp_only` value is inert and behaves as `free`.

The never-read keys the 1.20.1 audit flagged (ANS-MED-044) also stay deleted: the glyph/school bonus sections, the resonance caps, category cooldowns, and the dead performance keys.

---

## For pack makers: datapack tags

All cross-mod item/block detection is tag-driven and datapack-extensible. Shipped defaults use `"required": false` entries, so every tag loads cleanly even when the referenced mod is absent. Extend a tag by shipping your own `data/ars_n_spells/tags/...` file (merged by default), or set `"replace": true` to restrict it. The Forge 1.20.1 build ships the same tags plus the Covenant of the Seven ring and Blasphemy tags.

| Tag | Type | Default contents | Controls |
| --- | --- | --- | --- |
| `ars_n_spells:irons_spell_books` | item | all tiered Iron's spellbooks | An **additive** second source for binding-target detection; the primary test is Iron's own spellbook type. |
| `ars_n_spells:source_jars` | block | Ars `source_jar`, `creative_source_jar` | Which blocks count for Source Jar regen synergy. |
| `ars_n_spells:curio_spell_discount` | item | `#irons_spellbooks:school_focus` | Worn curios that grant the per-curio mana discount (`virtue_ring_discount`, capped by `max_total_curio_discount`). |
| `ars_n_spells:cross_cast_blacklist` | item | Ars Zero's temporal context form and anchor/select/sustain/discard effects | Glyphs that only work in their own caster: refused by the Spell Loom, `/ans export_to_irons_scroll`, Spell Transcription and every cross-cast. |

## Mana bars

The mod hides redundant mana bars based on mode:

- **iss_primary**: Hides Ars mana bar.
- **ars_primary**: Hides Iron's mana bar.
- **hybrid**: Shows the bar selected by `hybrid_mana_bar`.
- **separate / disabled**: Both bars may show.

Hiding is handled natively on NeoForge by `ManaBarController`, which cancels the redundant `RenderGuiLayerEvent.Pre` for `ars_nouveau:mana_bar` / `irons_spellbooks:mana_bar` per mode — the Forge-style overlay mixin is not needed.

## Debug overlay

With `debug_mode` enabled, `OverlayDiagnostics` logs every rendered GUI layer id once (via `RenderGuiLayerEvent.Pre`), highlighting mana-related layers — a quick way to discover overlay ids when tuning `ManaBarController`. It is opt-in and registers no per-frame work when off; the client reads `debug_mode` at startup, so toggling it takes effect on the next client launch.

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/ans mana setdefault <value>` | Op 2 | Set the default max mana. |
| `/ans mana getdefault` | — | View the current default max mana. |
| `/ans debug` | Op 2 | Toggle debug mode at runtime. |
| `/ans debug combat` | Op 2 | Cross-mod combat diagnostics: settings, spell-damage attributes and the last scaled hit in each direction. |
| `/ans export_to_irons_scroll [school]` | Op 2 | Export the held Ars spell onto a real Iron's scroll (requires Iron's; the Spell Loom is the survival path). The optional school is checked like a Loom choice; omit it for Automatic. |
| `/ans school list` | — | List the spell schools this server accepts for custom spells. |
| `/ans school show` | — | Show the school of each Ars spell on the held item. |
| `/ans school set <school>\|auto [entry]` | Op 2 | Change the school of an Ars spell on the held item without rebuilding it; the entry number is needed when the item holds several. |
| `/ans bind_scroll_to_irons_book` | Op 2 | Bind a held exported scroll into a held Iron's spellbook (requires Iron's). |
| `/ans info <player>` | Op 2 | Show mana, resonance, and the player's per-school affinity (plus the registered Iron's school count). |
| `/ans mode` | — | Show current mana unification mode. |
| `/ans glyph inspect` | — | Show the held imported glyph, or the imported glyphs in the held spell book's current spell: spell, level, adapter, target, tier, Press terms, augments and whether it is dormant. |
| `/ans glyph coverage` | Op 2 | Imported-glyph catalog, adapter coverage, dormancy counts, and data-pack mapping winners and errors. |
| `/ans glyph sessions [player]` | — (another player: Op 2) | Running casts with imported glyphs and their latest outcomes. |

## Compatibility and remaining acceptance

NeoForge has mana-only payment adapters. The Forge Covenant LP/aura integration is not included in this build. Optional addon support is scoped to the exact profiles and scenarios recorded in the audit ledger.

**Ars Affinity.** ANS 3.3.5 has no Ars Affinity integration code and needs none for the behaviour tested with Ars Affinity 1.1.1 (CurseForge file 7416588) and the Ars Elemental it requires, with and without Iron's. An Ars cast adds Affinity progress exactly once, including an Ars spell cast from Iron's spell wheel, because that cast runs through Ars's own resolver. A native Iron's spell adds no glyph progress. Mana Tap restores the pool the current mana mode pays from, in all five modes. ANS's school affinity and Ars Affinity's perk tree are separate systems with separate saves. Other perks, persistence across relog, death and dimension changes, and the two mods' screens and overlays were not tested. There is no Forge 1.20.1 build of Ars Affinity.

The pinned runtime hooks use native API and bytecode boundaries. Critical payment hooks require their injection targets; an arbitrary upstream version change cannot be described as automatically safe. Real-client gameplay, two-player latency, complete viewer/addon combinations and performance thresholds remain explicit release acceptance work.

Missing optional glyphs are preserved as dormant descriptors and refused before native decoding changes their meaning. Removing a mod from a saved world may also remove native registry entries, blocks, entities or dimensions. A fresh optional-absent boot and a dormant-item roundtrip do not guarantee that upstream world removal is supported; `/ans removal_report` is a read-only aid.

## Building from source

Requires JDK 21.

```bash
./gradlew build
```

Dependencies (Ars Nouveau, Iron's Spellbooks) resolve automatically from CurseMaven (pinned file IDs in [`gradle.properties`](gradle.properties)); no manual jar placement required. The NeoForge `moddev` Gradle plugin handles deobf and run configuration.

Useful Gradle tasks: `runClient`, `runServer`, `runGameTestServer`, `runData`. `runGameTestServer -PwithIronsRuntimeGameTests -PwithArsAffinity` adds Ars Affinity 1.1.1 and Ars Elemental to the GameTest runtime. The 3.4.0 commands and results are in [docs/3.4.0/testing.md](docs/3.4.0/testing.md).

Output jar: `build/libs/ars_n_spells-<mod_version>.jar`, where `<mod_version>` comes from `gradle.properties`.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the full version history, including the NeoForge 1.21.1 port notes at the top.

## License

GNU GPLv3
