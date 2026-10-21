# The Glyph Press (3.4.0)

The Glyph Press turns a genuine Iron's Spells 'n Spellbooks scroll into a **learnable Ars Nouveau glyph** for that spell at that scroll's level. The glyph is learned with Ars's normal glyph action and composed in an Ars spell book like any other effect.

**Find or craft an Iron's scroll → press it into a glyph → learn the glyph → compose it with Ars forms and augments.**

The Spell Loom exports a whole Ars spell onto an Iron's scroll. The Press goes the other way: it imports one Iron's spell into Ars composition. The two features are separate and do not share data.

## Crafting

| Item | Recipe | Notes |
| --- | --- | --- |
| **Glyph Press** (`ars_n_spells:glyph_press`) | Shaped: gold ingot, Source gem (`#c:gems/source`), gold ingot / archwood planks (`#c:planks/archwood`), lectern, archwood planks / three obsidian | The recipe loads only with Iron's installed. The block itself is registered either way, so a placed Press survives Iron's being removed. |
| **Blank Glyph Plate** (`ars_n_spells:blank_glyph_plate`) | Shapeless: smooth stone, gold nugget, amethyst shard → 2 plates | Always available. |

Both recipes are data files (`data/ars_n_spells/recipe/`), and each has a recipe-book unlock. The Press is mined with a pickaxe and drops itself.

## Using it

Right-click the Press to open it. It has four slots:

| Slot | Takes | Used per glyph |
| --- | --- | --- |
| Top left | One filled Iron's scroll | the scroll |
| Middle left | A Blank Glyph Plate | 1 |
| Bottom left | Catalysts (Source Gems by default, `#ars_n_spells:glyph_press_catalysts`) | depends on the tier |
| Right | The finished glyph | output only |

The screen shows:

- the scroll's spell, level and school, with Iron's own spell icon;
- the glyph it becomes, its Ars tier, and whether you already know it;
- the target restriction (for example "Self only", "Requires a living target", "Channels, paid per pulse") and the augments the glyph supports;
- the catalysts, Source and time it needs.

The status line is the server's answer for you. For example: "Insert a filled Iron's scroll", "This scroll contains an exported Ars spell", "Requires tier II catalyst materials", "Output slot is full", "Spell disabled by the server". Press **Inscribe Glyph** when it reads "Ready". **Cancel** stops a running glyph and gives everything back. The **?** button opens a short help panel.

The ghosted glyph in the brass frame is a **preview**, not an item; only the right-hand slot holds a real result.

### Default terms by tier

The tier comes from Iron's rarity for that spell level: Common is tier I, Uncommon and Rare tier II, Epic and Legendary tier III. A data pack can override every value (see `adapter-authoring.md`).

| Tier | Catalysts | Source | Time |
| --- | --- | --- | --- |
| I | 1 | 500 | 100 ticks (5 s) |
| II | 2 | 1,500 | 140 ticks (7 s) |
| III | 4 | 3,000 | 200 ticks (10 s) |

There is no failure chance, quality roll or experience cost.

### Source

The Press holds up to `glyph_press_source_capacity` Source (default 4,000). It gets Source in two ways:

- **Source Relays** (and any other Ars machine that sends Source) can push into it, through Ars's own source capability. Nothing can draw Source back out.
- **Nearby Source Jars:** while a glyph is waiting for Source, the Press pulls from loaded jars within `glyph_press_source_radius` blocks (default 4), every `glyph_press_source_scan_interval_ticks` (default 20). An idle Press never scans.

Once enough Source is stored, the job reserves its share and runs. A cancelled job's reservation goes back into the Press.

### What can be pressed

Only a native Iron's scroll (`irons_spellbooks:scroll`) holding exactly one spell at a level that spell has. The Press refuses, and consumes nothing for:

- a blank scroll, or one holding Iron's empty `none` spell;
- a Spell Loom export or any other ANS carrier ("This scroll contains an exported Ars spell");
- a scroll with more than one spell, unreadable spell data, or an impossible level;
- a spell with no glyph adapter, or a level outside the catalogued levels (nothing is clamped);
- a spell Iron's has disabled, one a data pack disables, or one above the server's maximum level;
- an Eldritch spell the player has not learned in Iron's;
- books, staffs, imbued gear and addon scroll items, which the slot does not accept.

A level III scroll makes the level III glyph. It never makes level I or every level up to III.

## Learning and casting

Use the finished glyph (right-click), as with any Ars glyph:

- it is learned on the server and consumed only if you did not know it already;
- a glyph you already know is kept, and the Press tells you you know it before you inscribe;
- Eldritch spells also require Iron's own learning, both when you press and when you cast.

The glyph then appears in the Ars spell book editor, subject to the book's tier. A glyph is named after its spell and level, for example "Glyph of Firebolt III", so the editor's search finds it by name.

When a spell resolves the glyph, Iron's own spell runs where Ars says it resolves:

- **Self-only buffs** need Self.
- **Living-target spells** need Touch or Projectile on a creature.
- **Block spells** need a block.
- **Position spells** strike the impact point.
- **Projectile spells** launch from the caster with Self, or strike what Ars hit.
- **Teleports** move only the caster.
- **Summons** appear at the target.
- **Charged spells** wait their cast time before Ars continues.
- **Channels** pulse every half second and are paid per pulse.

Iron's own cooldown, recasts, learning rules and events all apply. Mana is charged for the Ars part of the spell and for each Iron's effect, once each, in the server's mana mode.

## Verified examples

These ran on this checkout (`testing.md` has the commands and results):

- **Firebolt III scroll:** with a plate, 2 Source Gems and 1,500 Source, it makes exactly one Glyph of Firebolt III (`GlyphPressGameTests#ironsLoaded_press_convertsTheExactSpellAndLevel`).
- **Full journey:** a Firebolt I scroll is pressed, the glyph is learned by using it, composed as `Touch → Glyph of Firebolt I` in a spell book, and cast at a pig, which takes the hit (`#ironsLoaded_press_verticalSliceFromScrollToCast`). The same journey ran with real client input on a dedicated server (`GlyphQaClient`), and then went further:
  - a Firebolt X scroll (tier III) used exactly one plate, 4 Source Gems and 3,000 Source;
  - a Spell Loom export in the scroll slot was refused with "This scroll contains an exported Ars spell", and nothing was consumed;
  - in Ars's own spell book editor, the novice book refused the tier III glyph, and `Touch → Glyph of Firebolt I → Amplify` was composed and saved;
  - that spell struck a pig at effective level 2, and a Spell Loom export carrying the glyph struck it again.
- **Two players:** two clients at one Press saw one job. A double click started it once, the other player could not take the locked inputs, and the single glyph went to whoever took it.
- **Restart mid-job:** a Firebolt II job saved mid-way resumes after a server restart and finishes once (`#ironsLoaded_press_resumesASavedJobOnce`). Before Iron's spell settings have loaded, a finished job waits instead of being judged (`#ironsLoaded_press_waitsForIronsConfigInsteadOfCancelling`). On a dedicated server restarted with nobody online, the job resumed, waited at its end, and finished once when a client logged in. The learned glyphs and the composed book survived the restart, and the book still cast.

## Automation and multiplayer

- **Hoppers and pipes:** the top face inserts scrolls, the side faces insert plates and catalysts, and the bottom face extracts the result. An unsided handler may insert any input and extract the result.
- **Locked inputs:** while a glyph is being made, its inputs are locked on every route: the menu, shift-click, hoppers, simulated or not.
- **Manual start only:** jobs start only from a player's Inscribe Glyph. Automation can stage materials and take results, but it cannot start a job, so it cannot bypass a player's learning prerequisites.
- **Shared Press:** two players at one Press see the same job. Each click is a one-time request tied to that player's open menu and the exact inventory they saw. A repeated click, a replayed packet or a stale view does nothing.

## Persistence, breaking and server settings

- **Saving:** the Press saves its slots, Source, job, progress and reservation together, under schema 1. After a restart a valid job resumes. A job that cannot be read is cancelled with its items kept. A finished glyph is never made twice.
- **Before the first login:** Iron's loads its spell settings (enabled spells, maximum levels) when the first player joins or on `/reload`. On a dedicated server that restarts with nobody online, a job that reaches its end in a loaded chunk waits until then. It then finishes, or, if the server's settings no longer allow it, is cancelled with everything returned.
- **Breaking:** breaking or blowing up the Press drops its contents exactly once. A job in progress returns its original inputs, never a glyph, and its Source is lost.
- **`enable_glyph_press` = false:** running jobs are cancelled with everything returned, and no new job starts. Items in the Press stay retrievable.
- **`enable_iron_glyphs` = false:** glyphs go dormant. They stay in spell books and in what players know, and do nothing until it is turned back on.

## Limitations

- Addon spells need a catalog entry naming one of the built-in adapters (`adapter-authoring.md`). Unknown addon spells are refused with "No glyph adapter for this spell".
- **Pocket Dimension** needs Iron's pocket-dimension level. The GameTest server does not create one, so that spell's world effect was not exercised by automation; the refusal path was.
- **Sacrifice** looks up the caster's summon by owner. GameTest fake players cannot own summons, so its successful path was not exercised by automation; Iron's own target filter was.
- **Cloud of Regeneration** is disabled in Iron's default config, so its glyph is dormant by default.
- Glyphs pressed on one server are ANS items. Without Iron's they stay as dormant glyphs; they never turn into another glyph.
- **Ars Affinity** weighs progress by each glyph's Ars mana cost. An imported glyph's price is its Iron's spell's own cost, charged separately, so it adds no Affinity progress of its own, as a native Iron's spell adds none. The Ars glyphs in the same spell progress as usual (`ArsAffinityInteropGameTests#affinityAndIronsLoaded_importedGlyphAddsNoAffinityProgressOfItsOwn`).
