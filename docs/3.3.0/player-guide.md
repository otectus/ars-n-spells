# Ars 'n' Spells 3.3.0 player guide

This guide describes the implemented 3.3.0 behavior. [Release acceptance](school-inscription-evidence.md) separates implementation from runtime scenarios still awaiting evidence. Install the build for your loader: Forge 1.20.1 and NeoForge 1.21.1 are separate artifacts and use different Minecraft save formats.

The Ars Worn Notebook includes an Ars 'n' Spells chapter with the Loom, transcription, binding, uninscription, resource rules, and live recipe pages. Iron-dependent entries are gated by Patchouli's mod-presence flag.

## Start with a spell you already know

Ars Nouveau is required. Install the matching Iron's Spells 'n Spellbooks version to export Ars spells to Iron scrolls or bind them into Iron spellbooks. Extra glyph mods are optional; their presence does not guarantee that every glyph has been exercised in every carrier. The client Compatibility page and `/ans diagnose` report detected versions.

Build and select a valid Ars spell in an Ars spellbook. Craft a Spell Loom with one gold ingot, two lapis lazuli, one book, and three obsidian: gold above the book, lapis on either side, obsidian across the bottom. Put the spellbook in the source slot and a blank **Iron's scroll** in the target slot.

Choose a name, background, and foreground icon, inspect the preview, then inscribe. The main screen shows source/target consumption and output capacity. Details shows the primary school and server mapping digest. Five local cosmetic presets save only the name, icon, and background; Apply changes the preview and Save current replaces the selected preset. One scroll appears in the output. Your reusable book or focus stays in the source slot with its spell and other data intact. An Ars spell parchment is disposable and costs one unit. Clear the output before making another scroll. A rejected operation moves no items.

A scroll with a native Iron spell or an existing ANS inscription is already filled. Ordinary inscription refuses it. The explicit Convert action replaces the spell on one target scroll; inspect that choice before using it. Extra target units remain unchanged. The loom accepts readable Ars sources and actual Iron scroll targets; arbitrary items cannot be inserted through its menu or automation.

Automation inserts sources from above, scroll targets from a horizontal side, and extracts output from below. Inputs cannot be extracted through those sided handlers. Breaking the loom drops its current contents once.

## Use, bind, and remove an inscription

An exported ANS scroll is a carrier of the original Ars spell. Use the carrier to cast. For a generic carrier with several entries, sneak-use cycles the ANS selection. An Iron spellbook uses Iron's own spell selection controls and wheel. ANS introduces no required new keybinding; use the keys configured in Minecraft's Controls screen for the upstream mods.

To bind a scroll into an Iron spellbook, prepare the Spellbook Binding ritual. Place exactly one exported carrier and one compatible Iron book near the brazier, insert the tablet, and activate the brazier with an empty main hand. On success one carrier scroll is consumed and one Ars entry is added to the book. Existing native Iron spells, upgrades, custom names, and unrelated item data stay on the book. The spell's chosen name and icon travel with it.

The default proxy pool allows eight distinct Ars entries on a book. A server can set a smaller cap or forbid new binds. A cap of `-1` still has the eight-entry pool limit. ANS appends native wheel slots rather than replacing native spells. The same serialized Ars spell cannot be bound twice to the same book. A duplicate or full-book refusal preserves both inputs.

From 3.3.5, a server can put a bound Ars entry on an Iron's cooldown after it casts successfully from the wheel. `inscribed_ars_default_cooldown_ticks` sets the length in ticks (20 ticks = 1 second). A new config uses 40 ticks. A world updated from 3.3.4 or earlier keeps 0, which means no cooldown. It is Iron's own cooldown, so the wheel shows it and Iron's cooldown reduction shortens it. A cast that fails, is refused or cannot be paid starts no cooldown. Each wheel slot is a shared proxy spell, so two books that bind different Ars spells to the same slot number share that slot's cooldown. Casting the same spell from an Ars book is not affected.

Spell Uninscription removes all ANS entries and their native proxy slots from one item. It preserves unrelated native spells and item metadata. For books bound by 3.3.0, empty appended tail slots are reduced back to the recorded original capacity. A later native spell placed above that original capacity is retained. An older book without a recorded baseline retains any surplus empty native slots because their original ownership cannot be established safely. An anvil/custom name is retained.

The Spell Transcription ritual also copies spells between supported source and target forms. Keep exactly one readable source entity and one suitable empty target entity within three blocks; extra candidates are refused instead of selecting an arbitrary item. Books and focuses are reusable. A filled native Iron scroll is a valid consumable source, and one source unit produces one target unit. A filled item is never implicitly overwritten as a target. See the [recipe reference](recipe-reference.md) for exact tablet ingredients and Source costs. Ingredients used to **craft a tablet** are distinct from the reusable source item used later by the ritual.

## Understand the resource bill

The server's mode determines the paying pool. In `iss_primary` and `hybrid`, the native Iron mana pool is authoritative. In `ars_primary`, the native Ars pool is authoritative. In `separate`, native spells use their own pool and cross-casts may have two configured payment legs. With unification disabled, spells use their native pool. If Iron's is absent, the effective route falls back to native Ars behavior.

An inscribed cross-cast has the configured overhead, 1.25 by default. Conversion applies only when a payment crosses between Ars and Iron units. Equipment discounts, alternative LP/aura payments, and carrier policies can change the final quote. The server checks and commits payment; a client preview cannot authorize a cast. A refused or stale held-item request must be retried after the item synchronizes.

Channelled Iron's spells are charged on every pulse, at that pulse's final price. From 3.3.5, when your balance cannot pay another pulse, the channel ends after the last pulse you paid for, with the normal cooldown and scroll use. A refused cast names the missing resource, the amount needed and the amount you had, for example "Not enough Iron's mana for Crystal Barrage: 250 needed, 180 available." No mana is taken for a refused pulse or cast.

`/ans diagnose` prints the requested and effective mode, conversion rates, native adapter identity, rule generation, school mapping digest, detected mod versions, and a held-item fingerprint. It does not print the spell's raw payload. A detected addon is not a completed compatibility certification.

## Schools, affinity, and progression

Spell schools come from payload effects. Filters, augments, cast methods, and controls do not gain an element merely because their names contain one. School identity includes its namespace: `custom:fire` and `irons_spellbooks:fire` are different schools. A server datapack can supply explicit mappings, and its semantic mapping snapshot is synchronized to clients.

By default successful school casts add 0.001 power per cast, capped at 0.25. Affinity is stored separately on a 0–100 scale; its current damage bonus is 0.5% per level. Turning a feature off retains saved levels and counts while removing its active contribution. Optional affinity decay is off by default; when enabled it carries fractional decay between checks.

`/ans journal view` opens a paged graphical journal with a captured server snapshot of your levels, counts, applied progression, native attribute bindings, and school mapping digest. Re-run the command to refresh it. `/ans journal` and `/ans schools` list your stored school keys, affinity levels, cast counts, applied progression contribution, and resolved native power attribute. An unresolved custom school remains visible and saved. It does not receive a fabricated attribute or borrow the built-in school with the same path.

## Nearby Source and mana rituals

Source Jar synergy requires Iron's and enabled mana unification. A block in the `ars_n_spells:source_jars` tag qualifies by proximity; the ANS feature does not inspect, require, or consume Source stored inside it. The default horizontal search radius is four blocks, vertically from one below to two above the player. Discovery is cached and checks only loaded chunks. Multiple nearby jars do not multiply this proximity bonus.

The default bonus is five active-pool mana per second at a conversion rate of one. The configured rate is `conversion_rate_ars_to_iron × source_jar_synergy_multiplier` per second. The scan interval controls discovery frequency and no longer changes the income rate. Existing pre-3.3.0 configuration is backed up and migrated to preserve its old average income.

Mana Infusion grants the initiating player 500 active-pool mana by default after the ritual finishes; if that player cannot be resolved, the ritual's nearby-recipient fallback applies. Mana Well grants two active-pool mana per tick to each player whose bounding box intersects its default eight-block area. Wells overlap additively; there is no ANS team-only filter. These amounts are server configurable and are bounded by the pool's available capacity. ANS does not add a separate per-tick Source debit to these grants; the apparatus recipe and upstream ritual lifecycle have their own costs.

## Updating a world and troubleshooting

Keep a world backup before changing Minecraft version, loader, or removing content mods. Updating ANS within a supported loader reads its older known data shapes. New writes are stamped; data from a newer unsupported inscription schema is refused for casting and append operations rather than rewritten. The Forge root-NBT format and NeoForge data-component format are not a general Forge-to-NeoForge world importer.

For a missing icon, first check that the server and client both have the 3.3.0 resources. Saved legacy icon keys have compatible fallbacks; unknown choices use the default visual. For a missing school bonus, use `/ans schools` and inspect whether the native attribute is unresolved. For a refused cast, read the translated reason and run `/ans diagnose` to verify the effective mode and held carrier state.

In `ars_primary`, scrolling the hotbar does not spend mana. Putting away an item that adds maximum mana lowers your maximum, and a balance above the new maximum is capped once. If the bar drops at an unchanged maximum without a cast, ask the server operator to turn on `debug_mode` and reproduce it. `[ManaTrace]` lines in the server log then show both pools, the ceiling, the selected slot and the held item at each change.

Before removing a mod, `/ans removal_report` reports ANS-bearing inventory slots and non-vanilla registered dimension-type keys without changing anything. It scans your inventory only: it does not inspect every chest, unloaded entity, chunk, or dimension, and cannot replace a complete world backup or upstream mod removal procedure.

`/ans inspect` reads your main-hand spell on the server and shows its primary school, native base cost, the configured mana conversion quote, current pool balances, rules generation, and mapping digest. It inspects the ANS selected entry when present; an ordinary Iron's carrier uses slot 1, which can differ from the wheel selection. This is a mana quote before alternative payments and native/addon cost-event adjustments, not a promise of the final bill. It does not cast, reserve resources, post cost events, or alter the held item. Cast feedback reports actual settlement. Unreadable or future-schema inscriptions remain intact.
