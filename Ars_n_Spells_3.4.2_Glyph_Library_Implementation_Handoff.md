# Ars 'n Spells 3.4.2: Imported Glyph Visibility and the Iron's Glyph Library

Implementation handoff for a coding agent  
Repository: https://github.com/otectus/ars-n-spells  
Prepared: November 18, 2026  
Target release: 3.4.2

## 1. Assignment and finished experience

Implement a complete solution to imported Iron's glyph clutter in the Ars Nouveau spellbook.

The player report supplied with this request describes learning glyphs through Ars and addon scrolls, then finding the book crowded with dormant imported glyphs after 3.4.0. The requested escape hatch is a config setting that hides imported Iron's glyphs. Provide that setting, and improve the default experience for players who keep the feature enabled.

Ship these three display modes:

1. **Organized**, the new default. Ordinary Ars and Ars addon glyphs remain in the normal palette. Imported Iron's glyphs move into an **Iron's Glyph Library** bookmark inside the same editor. The library shows one entry per original Iron's spell, with its learned levels inside.
2. **Individual levels**, an opt-in familiar layout. Available imported glyphs appear as separate level entries in the normal palette.
3. **Hidden**, the requested opt-out. Imported glyphs disappear from supported spellbook browsing, search results, acquisition lists, and library shortcuts. The setting is local to the player and does not require operator permission.

Dormant learned glyphs are omitted from normal browsing in both visible modes. They remain inspectable in a deliberately opened, grouped **Dormant** library view with useful explanations. Nothing is unlearned, unregistered, reassigned, or removed from a saved spell.

The organizing principle is **one spell to browse, exact levels to select**. This is a presentation change around the existing glyph identities, not a replacement of the glyph or casting system.

All requirements below are part of this feature unless expressly marked outside scope. Implement and validate the feature on Forge 1.20.1 and NeoForge 1.21.1 using their actual branch APIs. Do not claim loader parity based on matching documentation alone.

## 2. Inspected baseline and evidence boundaries

### 2.1 Repository snapshots

| Build | Inspected branch and commit | Version and development pins observed |
| --- | --- | --- |
| Forge | `main` at `6c26c544291ec3123eb1f5e97136d62d4d801b41` | ANS `3.4.1`; Minecraft `1.20.1`; Forge `47.4.10`; Java `17`; official Minecraft mappings; Ars Nouveau `4.12.7`; Iron's compile pin `1.20.1-3.15.0`, with a `3.16.3` test profile. |
| NeoForge | `port/neoforge-1.21.1` at `c234f19145d4380a849597f7e390fd535cc45e4f` | ANS `3.4.0` in this branch's properties; Minecraft `1.21.1`; NeoForge `21.1.248`; Java `21`; Parchment `2024.11.17`; Ars Nouveau `5.13.1.1400`; Iron's `1.21.1-3.16.3`. |

Sources: [S1], [S2].

The main branch's 3.4.1 documents discuss both loaders, but the inspected NeoForge branch still contains materially different 3.4.0-era implementations. Rebase this feature onto the actual release work before coding. Preserve newer changes if either branch has advanced. Do not transplant Forge classes into NeoForge or assume the NeoForge branch already has main's catalog retention, live-state publication, settings service, or test infrastructure.

This feature does not require upgrading Minecraft, Ars, Iron's, Java, or the loaders. Use the existing dependency policy and record the exact dependency artifacts used for validation.

### 2.2 What the source establishes

| Observation | Implementation consequence |
| --- | --- |
| Imported glyphs are permanent identities for an original spell ID plus a base level. Forge registers them even when Iron's is absent. [S3] | Never make registration conditional on a local display preference. |
| Forge's current `ImportedIronEffect.shouldShowInSpellBook()` already returns `isEnabled()`, which uses `GlyphAvailability`. [S4], [S5] | A new availability predicate alone is not a complete fix. Keep the existing mechanical checks and implement view policy plus live refresh. |
| Ars 4.12.7 and 5.13.1 build editor candidate lists from known glyphs and `shouldShowInSpellBook()`. These lists are stored on the screen. [S6], [S7] | Rebuild the presentation when knowledge, behavior, or preferences change, including while the editor is already open. |
| Ars 4.12.7's learn-all command stores registry glyphs without an availability filter. The capability itself accepts glyphs without checking availability. [S8] | Knowledge can legitimately contain dormant entries. Presentation must tolerate large, mixed known sets. |
| Main's 3.4.1 changelog describes 111 base spells and 1,110 registered level identities, including reserved levels. [S9] | Do not create one default row per registered identity. Do not present reserved-but-unlearned levels as a completion checklist. |
| `ImportedGlyphKnowledge` adjusts the maximum-mana and regeneration contribution using actual learned glyphs. [S10] | Filtering a capability getter or deleting learned variants would affect gameplay. Filter copies for display only. |
| Forge already publishes live behavior and mapping snapshots together, with a generation counter. [S11] | Reuse accepted state and its lifecycle; do not invent a competing client availability cache. |
| Main's config handlers currently distinguish events by mod ID, and the existing settings screen edits server-owned settings. [S12], [S13] | Adding a CLIENT config requires exact spec/type guards and a separate personal-settings route. |
| NeoForge uses `ImportedGlyphRegistration`, `CatalogEntry`, `VariantBehavior`, and `ImportedGlyphSyncPayload` instead of Forge's corresponding classes. [S14], [S15] | Keep a shared behavioral contract with loader-specific adapters. |
| The modern NeoForge Ars editor uses `GlyphFormatter` and a backing `spell` list. Forge has both `GuiSpellBook` and `InfinityGuiSpellBook`. [S6], [S7], [S16] | Test all supported editor variants; a single screen-class injection is insufficient. |

### 2.3 What is not established

The supplied quotation could not be matched to a numbered issue in the repository issue results inspected. Treat it as user-supplied feedback; do not attach an invented issue number to commits or the changelog.

The report does not identify the exact scroll item, addon, loader, dependency versions, or a reproduction world. No Minecraft client was run during preparation of this document. Therefore:

- Do not state that a particular addon caused the problem.
- Do not state that ordinary Ars scrolls always teach imported glyphs.
- Do not claim that the current Forge source lacks dormant filtering.
- Treat stale screen contents, addon acquisition paths, and released-version differences as reproduction cases, not confirmed causes.
- The design must work for any legitimate knowledge source, including imported glyph items, commands, and addons.

The source review was targeted at registration, knowledge, availability, configuration, synchronization, editor integration, and relevant tests and documentation. It was not an exhaustive audit of unrelated casting, mana, or workstation code.

## 3. Non-negotiable compatibility rules

1. **Keep every existing spell-part ID and item ID.** Forge uses `ars_n_spells:iron/<namespace>/<spell path>/level_<n>` for the part and item. NeoForge uses that part ID but `ars_n_spells:glyph_iron/<namespace>/<spell path>/level_<n>` for the item. Preserve the branch's encoding.
2. **Keep the entire learned set.** Never call `setKnownGlyphs`, `unlockGlyph`, or a forget/remove operation to implement a display option, favorite, archive, or migration.
3. **Keep the exact spell recipe.** Opening a screen, changing mode, applying a filter, or receiving a server update must not remove or substitute a recipe element.
4. **Keep gameplay availability server-authoritative.** Local hiding cannot change `isEnabled()`, tier, cost, cooldown, learning prerequisites, press availability, cast admission, or the feature flag on the server.
5. **Do not replace variants with a new universal glyph.** A group is a UI object. Selecting level III inserts the already registered level-III part.
6. **Do not grant lower levels automatically.** Knowing level V does not mean the player knows I–IV. Preserve gaps.
7. **Do not imply that a saved glyph will cast just because it is visible.** Dormant, incompatible, or over-tier glyphs must retain accurate explanations.
8. **Do not gate all ANS content by namespace.** Imported Iron's glyphs are a specific registered type/identity. Native Ars addon glyphs, ANS proxy spells, rituals, and unrelated future ANS glyphs are not targets.
9. **Do not change unrelated interfaces globally.** This setting governs supported spellbook browsing. Inventory item tooltips, physical items, JEI, creative inventories, the Glyph Press, and the Iron's native spell wheel keep their own behavior.
10. **Keep dedicated-server loading safe.** Client screens, client config readers, and Minecraft client classes must not be reached from common glyph registration or server validation.

## 4. Configuration contract

### 4.1 A single, unambiguous local mode

Create a loader-appropriate CLIENT config at `config/ars_n_spells-client.toml`. Add this setting:

~~~toml
[spellbook]
    # How imported Iron's glyphs appear in supported Ars spellbook browsers.
    # grouped: a separate library with one entry per spell and a level chooser.
    # flat: individual available levels in the ordinary glyph palette.
    # hidden: hide imported glyph browsing on this client.
    # Does not erase knowledge, alter saved spells, or disable casting.
    iron_glyph_display_mode = "grouped"
~~~

Allowed values are exactly `grouped`, `flat`, and `hidden`. Use a validated string or equivalent serializer that actually accepts these lowercase values on both loaders. Invalid values fall back to `grouped` with one useful config warning, not a crash or a silent switch to hiding.

Do not introduce a second boolean that conflicts with this enum. The explicit opt-out example in the guide must be:

~~~toml
[spellbook]
    iron_glyph_display_mode = "hidden"
~~~

Use these user-facing labels:

| Stored value | Setting label |
| --- | --- |
| `grouped` | Organized |
| `flat` | Individual levels |
| `hidden` | Hidden |

Setting title: **Imported Iron's glyphs**.

Description: **Choose how converted Iron's spells appear in your Ars spellbook. Hiding them keeps your learned glyphs and saved spells.**

### 4.2 Distinguish hiding from disabling the feature

The existing server setting stays authoritative:

~~~toml
["Imported Iron's Glyphs"]
    enable_iron_glyphs = false
~~~

Document the difference explicitly:

| Intent | Setting | Effect |
| --- | --- | --- |
| “I do not want these entries in my book.” | Client `iron_glyph_display_mode = "hidden"` | Hides imported browsing for that player. Existing valid saved spells can still cast. |
| “I want the imported-glyph gameplay feature disabled in this world.” | Server `enable_iron_glyphs = false` | Uses the existing server refusal/dormancy behavior for learning, casting, and press conversion. |
| “I want the feature, without pages of level duplicates.” | Client `iron_glyph_display_mode = "grouped"` | Separate grouped library; normal Ars palette remains clear. |
| “I prefer the former individual-level palette.” | Client `iron_glyph_display_mode = "flat"` | Available imported variants appear individually. |

Do not add a server “force hidden” policy for 3.4.2. A local preference is sufficient for the requested opt-out, and the existing server switch already controls gameplay.

### 4.3 In-game access

Add **Personal display** access from the ANS config screen. It must work:

- At the title screen, before any server config has loaded.
- In single-player, including worlds without cheats.
- For a non-operator on a dedicated server.
- For a LAN guest whose world-settings controls are read-only.

Use a separate child settings screen or clearly separate section with its own local Apply/Cancel draft. Do not send personal display changes in `SettingsApplyPacket`, include them in `AnsSettings.SETTINGS`, or require `ClientSettings.current()` to be non-null.

Add **Display settings…** inside the library. Return to the same editor draft after applying or cancelling. Reset restores `grouped` but does not clear favorites.

Applying the setting refreshes the current editor without reconnecting, restarting, rewriting the held item, or closing an in-progress spell draft.

### 4.4 Exact visibility behavior

| Surface | Organized | Individual levels | Hidden |
| --- | --- | --- | --- |
| Ordinary Ars palette | Original non-imported candidates only | Original candidates plus individually displayed, available imported candidates | Original non-imported candidates only |
| Ordinary palette search | Original results; optional matching-library link described below | Original results plus matching available variants | Original non-imported results only; no imported result count |
| Iron's Glyph Library bookmark | Present when useful | Present for grouped inspection, favorites, and dormant explanations | Absent |
| Library Learned view | One entry per known original spell, subject to filters | Same grouped inspection view | Inaccessible |
| Library Dormant view | Explicit, grouped, inspection-only | Explicit, grouped, inspection-only | Inaccessible |
| Supported acquisition/unlock browser | No imported level wall; preserve real acquisition routes as described in section 10 | Preserve actual supported recipes; omit unavailable imported entries | Omit imported entries |
| Current recipe and saved spell slots | Exact stored glyphs remain represented | Exact stored glyphs remain represented | Exact stored glyphs remain represented |
| Item tooltips and non-spellbook interfaces | Existing behavior | Existing behavior | Existing behavior |

“Hidden” means fully hidden from browsing and discovery lists. **It does not conceal ingredients already present in the spell being edited.** An editor must show what that spell actually contains. Keep those recipe chips and their warnings, even in Hidden mode; otherwise the player could unknowingly save or cast a different-looking spell. Explain this one exception in the setting tooltip.

Do not show repeated “you have hidden glyphs” notices, imported favorites, archive counts, or a promotional bookmark in Hidden mode. The setting remains reachable through Personal display.

## 5. Default interaction: the Iron's Glyph Library

### 5.1 Preserve the normal editor

Attach one recognizable bookmark labeled **Iron's glyphs** to the existing Ars editor. Match the book's visual language and reuse ANS/Ars icon assets with a readable fallback.

Opening it reveals a panel within the current editor session. It does not replace the book item, save the draft, or rebuild the screen from the original stored recipe. Keep the composition row visible where the layout permits.

The ordinary form, effect, and augment interface remains available on closing the panel. In Organized mode, all ordinary Ars and Ars addon glyphs retain their previous eligibility, ordering, tooltips, and behavior.

Bookmark availability:

- Hide it in Hidden mode.
- Otherwise show it if there are known imported glyphs, including dormant ones.
- Also allow a quiet introductory bookmark when the feature is available but the player has not learned an imported glyph.
- If the feature is unavailable and the player has no imported knowledge, omit the bookmark.
- Initial introduction is inline help, not a modal or chat message.

A player with 40 spells learned at six levels each should browse 40 library entries rather than 240 separate imported candidates. The ordinary palette should contain zero of those 240 entries in Organized mode. This is a conditional example, not a claim that all installed spells have six levels.

### 5.2 Library contents

Provide three views:

| View | Contents |
| --- | --- |
| **Learned** | One entry per original spell with at least one learned, currently available variant. |
| **Favorites** | A filtered subset of Learned, chosen explicitly by the player. |
| **Dormant** | Known unavailable variants, grouped by original spell. Read-only inspection with reasons. |

Inside each spell entry show:

- The original spell's localized name and icon.
- The original source mod, resolved from the original Iron's spell ID.
- Its current school when authoritative live information is available.
- A compact summary such as **3 learned levels**.
- A favorite control with a text tooltip and narration.
- In the detail panel, the exact learned levels and their individual states.

Group by the full original spell `ResourceLocation`. Never group by translated name, icon, school, registry order, `GlyphFamily`, or the ANS namespace.

In the existing Forge code, `GlyphFamily` describes an execution/targeting adapter. It is not the UI spell grouping key. For Forge use `definition().spell()`; for the inspected NeoForge implementation use `key().ironSpellId()`.

Two addons with a spell named “Firebolt” must produce two entries, identified by their original namespace and path. If duplicate display names remain ambiguous, show the source mod next to the name and the complete ID in the tooltip.

### 5.3 Choosing a level

Click a spell entry to inspect its learned levels. Provide an explicit **Add <spell name> <level>** action.

- Exactly one eligible learned level: offer a direct Add control on the row as a shortcut.
- Several eligible learned levels: open the detail panel and choose a level.
- Sort levels numerically, never by Roman numeral text.
- With no previous selection in this editor session, preselect the lowest eligible learned level. The Add label must name it.
- Remember the player's selected level for that spell for the current editor session.
- Do not silently switch to the highest level when another one is learned.
- If the selected level becomes invalid, disable Add, retain the explanation, and require a new explicit selection. Do not substitute another level on the click.
- Adding one level does not remove other levels from the player's knowledge.
- The same spell may still be used more than once, at different levels, where the existing validator permits it. A grouped entry is not a new per-spell uniqueness rule.

For learned levels I, III, and VIII, show those three levels. Do not infer II, IV–VII, or any unlearned reserved variants.

The main level list shows available learned levels. A compact **Dormant levels (N)** expander can reveal unavailable learned levels within the same detail panel. Unlearned registered levels stay out of this list. Do not add a hundreds-of-items “collection completion” counter.

### 5.4 Favorites without another wall of glyphs

Favorites are keyed by the original spell ID, not by each level.

- A favorite is a browsing preference, not a learning or server action.
- Favoriting a spell does not choose a higher level.
- The Favorites view still respects current knowledge, availability, search, and book filters.
- A favorite with no currently eligible variants does not become selectable.
- Retain its preference if an addon temporarily disappears or the player joins a different world.
- Hidden mode renders no favorite shortcut.
- Do not automatically favorite everything learned by a bulk unlock.
- Do not inject every favorite back into the normal palette. That would recreate the original problem.

Store favorites locally in `config/ars_n_spells/glyph_browser.json`, with a versioned schema and only spell IDs. Use bounded parsing, duplicate removal, and an atomic replacement write. Preserve a malformed file as a backup before creating an empty valid one.

A suitable schema is:

~~~json
{
  "schema": 1,
  "favorite_spells": [
    "irons_spellbooks:firebolt",
    "irons_spellbooks:heal"
  ]
}
~~~

Set a finite bound of 8,192 unique IDs and a 1 MiB file-size limit. At the limit, refuse a new favorite with a short explanation; do not discard an existing favorite silently. Keep well-formed IDs that are absent from the current catalog. Unknown future schema versions must not be overwritten.

Preferences may be shared across the player's worlds, but visible favorites must always be derived from the current connection's knowledge. Do not persist world/server addresses, knowledge snapshots, availability, or last selected levels.

### 5.5 Search and filters

The library supports:

- Case-insensitive localized spell-name search.
- Full original spell-ID search.
- Original source namespace/mod filtering, including `@namespace` search.
- School filtering based on the current server-derived school.
- **Fits this book**, default on, to omit levels above the current Ars book tier.
- Learned/Favorites/Dormant view selection.

The current spelling of a localized name must never be a persistent identity. Use `Locale.ROOT` for identifier normalization and rebuild localized search text when language resources reload.

Search before pagination. Group matched variants by original spell after determining which actual learned variants match. Searching a level-specific name such as “Firebolt III” should reveal the Firebolt entry with III selected or highlighted when that exact learned level matches. Use exact IDs/level metadata; do not strip numerals from arbitrary spell names.

All counts must name their unit: **spells** for rows, **levels** for variants. Counts follow the active filters. A simple **Reset filters** action restores Learned, all sources, all schools, Fits this book on, and an empty query. It does not change the global display mode or erase favorites.

In Organized mode, the normal Ars search can show one unobtrusive action such as **Search Iron's glyphs (4 spells)** when the same query matches imported knowledge. Activating it carries the query into the library. Do not put individual imported matches back into the native result list. No such action appears in Hidden mode.

Do not use current mana, cooldown, temporary status effects, distance, or target availability as catalog filters. Entries must not jump around as mana regenerates. “Fits this book” means tier compatibility, not a guarantee that the server will accept a cast in every situation.

## 6. Dormancy, loading, and honest explanations

### 6.1 Distinguish different kinds of unavailability

Do not collapse all failure states into “dormant.”

| State | Presentation | Can Add? |
| --- | --- | --- |
| Learned, behavior ready, available, fits the book, compatible with current draft | Normal learned level | Yes, after fresh validation |
| Learned and available, but above book tier | “Requires an Archmage book” or the actual tier; visible when Fits this book is off | No |
| Learned and available, but incompatible with current composition | Specific existing validation reason, such as a Self-only requirement | No |
| Learned but disabled by server/datapack/upstream | Dormant, with the actual reason | No |
| Learned but original mod/spell/catalog support is absent | Dormant, with the actual branch-supported reason | No |
| Learned base level exceeds the live maximum or falls below the live minimum | Dormant; show the live limit | No |
| Waiting for authoritative live state | “Waiting for server spell settings…” | No until ready |
| Not learned in Ars | Omitted from survival Learned/Favorites | No |
| Known only through a favorite from another world | Omitted unless currently learned or legitimately available in a creative editor | No survival bypass |

Book tier and composition compatibility are contextual restrictions, not reasons to change stored knowledge or the server's `GlyphAvailability` result.

Preserve Iron's own learning requirement checks. Ars knowledge obtained through a command or addon must not bypass them. If the existing client API cannot authoritatively establish a per-player prerequisite, do not invent an optimistic “ready to cast” indicator or a new grant. Keep the server checks and communicate the prerequisite without claiming it has been satisfied.

### 6.2 Dormant view

Dormant entries are never inserted into a new recipe from the library.

For each dormant level, show the existing translated explanation and a useful next step where known:

| Reason | Helpful direction |
| --- | --- |
| Feature disabled by server | “Imported glyphs are disabled in this world.” Do not tell a non-operator to change world settings. |
| Iron's or a spell-providing addon absent | Name the original source; explain that learned progress is retained. |
| Above live maximum | Show learned level and allowed maximum. |
| Datapack disabled | Explain that the world's spell rules disable it. |
| Retired/unsupported/newer adapter | Explain the support status without promising a future update. |
| Catalog package missing | Use this reason only where the branch's authoritative model supports it. |
| Not synced/pending | A loading state, not an archive entry or missing-mod diagnosis. |

Reuse existing translation keys where they already describe the situation correctly. A detailed tooltip may include the exact glyph ID for support.

When all known imported glyphs are dormant, Learned shows a concise empty state and **View dormant glyphs**. It should not enumerate every dormant level in the default view.

When a server restores a level or re-enables a spell, it moves back into Learned automatically after the accepted behavior update. No new scroll is required.

### 6.3 Existing spell contents

The composition row and saved spell previews continue to represent their actual glyph sequence.

- A dormant element keeps its identity and warning.
- Hidden mode does not silently remove that element from a preview or recipe.
- No part is replaced with the representative of a UI group.
- A local display toggle must not cancel an already running cast.
- A server feature toggle continues to use existing cast-session cleanup.
- Saving is still subject to the upstream editor's validators. This feature must neither auto-repair an invalid recipe nor bypass an existing refusal.
- Removing an element is an explicit ordinary editor action. Do not provide a mass “clean dormant glyphs from all spells” migration.

## 7. State model and projection rules

Implement a pure presentation model separate from rendering. The names below are proposed new components, not claims that these classes already exist.

Suggested components:

| Proposed component | Responsibility |
| --- | --- |
| `AnsClientConfig` | CLIENT config definition and safe local values. |
| `ImportedGlyphBrowserModel` | Build immutable grouped rows from explicit inputs. |
| `ImportedGlyphBrowserState` | Query, view, filters, focused spell, scroll position, and session level selections. |
| `ImportedGlyphPreferences` | Versioned local favorites persistence. |
| `ImportedGlyphBrowserController` | Observe revisions and update a live editor on the client thread. |
| `ImportedGlyphLibraryPanel` | Render rows, level details, actions, and explanations. |
| `ArsGlyphEditorHost` | Loader/editor-specific bridge to the existing draft and insertion/validation flow. |
| `ImportedGlyphViewAdapter` | Translate each loader's imported definitions and authoritative state into neutral model records. |

Model inputs must include:

- A copy of current known glyph IDs, plus upstream starting-glyph eligibility where applicable.
- A read-only catalog lookup for resolving imported identities.
- The current accepted availability/tier/school view and its readiness.
- The actual editor's creative-book/player eligibility rules.
- Current book tier and enough draft context to determine insertion errors.
- The local display mode and favorites.
- Current filters and localization revision.

Output records should identify every concrete variant by its real spell-part ID and level. A group should expose original spell ID, localized metadata, known variants, available variants, dormant variants, and contextual validation results.

The projection order is:

1. Resolve the current connection and editor context.
2. Copy upstream knowledge/candidate data; never mutate it.
3. Classify imported entries using the registered imported type or validated imported ID lookup.
4. Preserve non-imported candidates according to upstream rules.
5. If mode is Hidden, emit no imported browsing entries or counts.
6. In Organized mode, exclude imported variants from the ordinary palette and build library rows.
7. In Individual levels mode, retain available imported variants individually in the ordinary palette.
8. For the library, evaluate known variants against the accepted snapshot, then apply view/filter/search rules.
9. Group by full original spell ID; sort rows deterministically by localized name with ID as a tie-breaker.
10. Sort levels numerically and paginate the resulting rows.
11. Revalidate the exact selected part immediately before insertion.

Do not use `unlockedSpells` from an already-filtered native screen as the only source for the library archive. Those lists can already omit dormant glyphs. Read a copy of the actual current knowledge for this purpose.

Do not patch `getKnownGlyphs()` globally to return a shorter collection. Other consumers use it for serialization, learning, mana, and progression.

For creative books, reproduce upstream eligibility rather than pretending every registered glyph has been learned. Label the library as creative browsing where necessary. Creative availability may expand the candidate set, but neither Creative nor an operator status bypasses server-disabled glyphs through this UI.

## 8. Safe integration into the actual editors

### 8.1 Forge 1.20.1

The inspected Ars 4.12.7 sources contain two independent editors:

- `com.hollingsworth.arsnouveau.client.gui.book.GuiSpellBook`.
- `com.hollingsworth.arsnouveau.client.gui.book.InfinityGuiSpellBook`.

Their constructors build `unlockedSpells` and `displayedGlyphs`. They have separate layout, search, validation, paging, and insertion methods. Cover both.

Use narrow client-side integrations at candidate-list construction/rebuild and the editor-owned panel lifecycle. Prefer accessors/invokers and small targeted injections over copying an entire upstream screen.

Relevant source seams include:

- Constructor candidate collection.
- `onSearchChanged`.
- `resetPageState` and layout/pagination.
- `onGlyphClick`.
- Candidate validation and the current recipe/crafting cells.
- Resize/reinitialization and screen close.

Do not hide buttons only after pagination. The filtered candidates must drive page counts and layout, or the player will still have empty pages and incorrect arrows.

### 8.2 NeoForge 1.21.1

At the inspected Ars 5.13.1 source commit, `GuiSpellBook` extends `SpellSlottedScreen` and uses `GlyphFormatter` for page construction/layout. It also has:

- `unlockedSpells` and `displayedGlyphs`.
- A backing `spell` list.
- `spellWindowOffset` and bonus glyph slots.
- `onBookstackUpdated`.
- Separate normal and paste validators.
- `onGlyphClick`, which updates both crafting cells and the backing spell.

A Forge-style “set the button's spell part” operation is insufficient here. Respect the backing list, current window, insertion position, and existing validation.

The inspected 5.13.1 and current 5.13.5 versions of `GuiSpellBook` and `AbstractSpellPart` have identical contents in the fetched sources. Still verify against the exact resolved jars used in the build; source compatibility is not proof that a production mixin applied.

Do not target the stale upstream `1.21.x` branch as if it were the release source for 5.13.1. The versioned source examined for this handoff is commit `112920ff774831f204031da75b4c4e73d3765157`.

### 8.3 Insertion must use a concrete part and real validation

The library is a selector, not a new spell writer.

A proposed host interface can expose operations equivalent to:

~~~text
snapshotEditorContext()
validateInsertion(concreteGlyphId)
insertValidatedPart(concreteGlyphId)
refreshCandidateProjection()
preserveAndRestoreViewState()
~~~

These are design operations, not upstream API signatures.

Before Add:

1. Confirm the current editor session still owns the same valid book/hand context.
2. Confirm the chosen real imported part still exists.
3. Re-read current knowledge/creative eligibility.
4. Require authoritative-ready and available behavior.
5. Validate book tier, form restrictions, imported-effect count, augment rules, and the exact insertion position with the existing editor validator.
6. Insert exactly that registered part using the host's normal mutation path.
7. Update validation, recipe preview, and cost presentation using upstream behavior.
8. Keep the normal explicit Save/Create operation. Add does not save automatically.

Upstream `onGlyphClick` trusts a `GlyphButton` whose `validationErrors` have already been populated. Do not fabricate a button with an empty error list and call that method as a validation bypass. Populate/execute the same checks through a narrow host helper first.

Do not mutate the real recipe temporarily merely to compute library eligibility. Validate a copied candidate recipe, including the correct prefix/insertion context.

### 8.4 Preserve draft state

Opening/closing the panel and changing display mode must preserve:

- Current spell slot and name.
- Exact draft recipe, including gaps and temporarily invalid elements.
- Selected insertion position or visible glyph window.
- Book hand, tier, and bonus slots.
- Ordinary search text and page.
- Library search, filters, scroll position, and focused row.

Avoid a naïve child screen that reopens a new `GuiSpellBook` and reloads the saved item contents. Prefer a panel owned by the existing editor, or implement explicit draft retention before any unavoidable screen reinitialization.

Escape closes the innermost level popover, then the library panel, then follows the ordinary editor behavior. Clicking the dimmed background must not activate a recipe button behind the panel.

### 8.5 Mixin and addon compatibility

Keep client mixins in a client-only configuration section. Do not add GUI targets to a common mixin list.

Use the existing mixin plugin's non-classloading resource probes when checking optional target presence. Do not call `Class.forName` to inspect upstream targets during plugin initialization.

For supported upstream versions, verify that required hooks apply. An optional hook that silently fails must not remove imported glyphs from the native palette and leave no working library access.

Integrate through `ArsGlyphEditorHost` where third-party editors can participate. For an unsupported replacement editor:

- Do not transform arbitrary screen lists by guessing field names.
- Do not suppress imports for Organized mode unless a working insertion host/library has been installed.
- Preserve its native behavior, and report the unsupported integration once in diagnostics.
- Hidden mode must still be honored on every officially supported editor; an unknown replacement is an explicit compatibility limitation, not a silently claimed success.

Do not add a new general UI library dependency for this feature.

## 9. Config registration and lifecycle changes

### 9.1 Fix event routing before adding the new spec

In the inspected Forge `ArsNSpells`, `onConfigLoading` and `onConfigReloading` currently return early only for a different mod ID. Adding a second ANS config without tightening these handlers would let CLIENT config events enter server migration, bridge refresh, and publication paths.

Change these handlers to check the exact server spec and config type before executing server logic. Apply the same discipline to all ANS config-loading, reloading, and unloading listeners on both branches.

The client diagnostics handlers in Forge's `ArsNSpellsClient` also currently route by mod ID and read `AnsConfig.DEBUG_MODE.get()`. Ensure a new CLIENT config load at the title screen cannot make them read an unloaded SERVER spec.

Required behavior:

| Event | Permitted work |
| --- | --- |
| ANS SERVER spec load/reload | Existing server migration, mana routing, availability publication, and world-settings synchronization. |
| New ANS CLIENT spec load/reload | Validate local mode, schedule browser refresh on the client thread, and update personal-settings UI. |
| ANS SERVER spec unload | Existing connection/world cleanup; no overwrite of personal preferences. |
| Unrelated mod/spec event | No action. |

A client display edit must not trigger server schema migration, change the remembered server-config path, rebuild mana bridges, broadcast settings, or close glyph sessions.

### 9.2 Register without unsafe class loading

The config-spec class may be common-loadable if it contains no client-only imports. Register the CLIENT spec using the appropriate loader mechanism. Keep screens, filesystem preference UI, and client event handling on the physical client.

Common `ImportedIronEffect.isEnabled()` and `GlyphAvailability` remain entirely independent of the display mode. Prefer screen-owned filtering over introducing a client-class reference into `shouldShowInSpellBook()`.

The grouped library must explicitly require ready authoritative behavior before allowing insertion, even if a provisional pre-sync availability calculation would otherwise appear permissive.

### 9.3 No unnecessary new network protocol

This feature can use existing synchronized knowledge and glyph behavior. Display mode, favorites, filters, and panel state need no packets and must not enter catalog/behavior identity hashes.

Do not change packet layouts solely to carry personal UI preferences. If another 3.4.2 change already changes the protocol, retain that release decision. Do not promise 3.4.1-to-3.4.2 network compatibility without testing the final complete release.

## 10. All relevant browsing surfaces

### 10.1 Candidate lists and acquisition views

Audit the actual UI lists rather than assuming every Ars screen honors the same visibility method.

The inspected Forge `GlyphUnlockMenu` enumerates registered parts filtered by `shouldShowInUnlock()`. The modern NeoForge `GlyphUnlockMenu` starts from the recipe manager's glyph recipes. Consequently, overriding one common method does not cover both paths. [S17]

Implement display filtering on each supported presentation path:

- Hidden: omit imported entries and imported-only controls.
- Organized: avoid a registered-level catalog wall. Provide the library/help route for imported glyphs.
- Individual levels: preserve real available acquisition entries where the current pack actually defines them.
- An unavailable imported glyph must not look craftable/unlockable.
- Preserve a legitimate datapack-added glyph recipe; do not remove it from the recipe manager or the server because the local view is grouped.
- Do not invent Scribe's Table recipes for glyphs obtained through the Glyph Press.

If an acquisition screen needs to expose imported recipes in Organized mode, group those real recipes under an imported-spells section using the same original-spell key, then present the concrete recipe after a level is chosen. Acquisition groups are separate from learned groups: they may contain unlearned levels with an actual recipe. They must never be treated as learned candidates in the spell editor.

This feature does not intercept the server's learning transaction or consume, refund, or replace a scroll.

### 10.2 What remains outside the display setting

Do not use this setting to:

- Hide physical inventory stacks or make their tooltip misleading.
- Change Glyph Press inputs, outputs, or recipes.
- Hide normal Iron's spells from Iron's own wheel or books.
- Filter JEI globally.
- Alter recipe synchronization, loot, creative registry contents, documentation registries, or server commands.
- Remove learned glyphs from exports, parchment, copied spells, bound books, or serialized capabilities.

The native documentation book is a reference catalog, not the editable known-glyph palette. Add help explaining the new browser, but do not replace or destructively prune generated documentation pages as part of 3.4.2. Any later documentation-catalog organization should be a separate change.

### 10.3 Arbitrary acquisition sources

A player may arrive with hundreds of known imported glyphs learned before upgrading, granted by an addon, or granted by an operator. On the first 3.4.2 open:

- Organize the current set immediately.
- Preserve the exact known IDs.
- Do not require the user to relearn, export, clear, or “repair” the book.
- Do not replay one notification per learned glyph.
- Do not assume the order in which glyphs were learned.

Do not introduce a “forget imported glyphs” command or a player-data cleanup migration. Those actions would confuse display management with loss of progression.

## 11. Refresh, connection isolation, and performance

### 11.1 Rebuild on relevant change

The current upstream editors cache candidates. Refresh the projection when any of these inputs changes:

| Change | Refresh needed |
| --- | --- |
| Local display mode applied or config file reloaded | Native candidates, library visibility, page count, and focus. |
| Current player's known glyph set changes | Learned groups, levels, favorites eligibility, and counts. |
| Accepted live behavior/mapping generation changes | Availability, tier, school, archive membership, and insertion errors. |
| Book tier, active book, bonus slots, or editor context changes | Eligibility and insertion context. |
| Recipe changes | Contextual errors for visible rows and selected level. |
| Language/resource pack reload | Names, search keys, sort order, icons, and text layout. |
| Connect/disconnect/player replacement | Drop session-derived state and observe the new player/connection. |
| Window size or GUI scale changes | Layout only where possible, preserving draft and selection. |

On Forge, observe `GlyphBehavior.generation()` from the client side. Respect `GlyphClientSync`'s staged mapping/live publication; do not rebuild between the halves as if they were a valid new pair.

On the inspected NeoForge branch, use the accepted `GlyphBehaviorSnapshot` and its generation/readiness through `GlyphBehavior.current()`. If 3.4.1 work changes that path, adapt to the final publication model instead of retaining a second older one.

Do not take over `GlyphBehavior.setViewerRefresh` for the spellbook. It is a single callback used by recipe-viewer integration. A generation observer, or a deliberate multi-subscriber change that preserves existing subscribers, avoids breaking JEI.

### 11.2 Knowledge changes without invasive hooks

Take a normalized set of real glyph IDs when the screen opens. While a supported editor or library is open, compare with a new snapshot every 10 client ticks, and immediately before Add. Only rebuild when the sets actually differ.

Use set equality, not collection identity, list order, or a lossy hash alone. Upstream/addon code can mutate a capability in place. Ten ticks is a bounded compatibility fallback, not work to perform when all relevant screens are closed.

If a narrow, verified knowledge-sync notification is available in the final branch, use it for immediate refresh as well. Do not patch every capability mutation or depend on an unverified upstream event name.

For state changes accepted on the current client, process UI refresh on the next client tick. For the knowledge fallback, refresh within 10 ticks. Never allow an Add action based on the earlier cached eligibility alone.

### 11.3 Safe refresh behavior

Rebuild only model lists and affected widgets. Preserve the draft and panel state.

- If a focused row remains, keep focus on that original spell ID.
- If it disappears, focus the next valid row or an empty-state control.
- Clamp the page/scroll position before slicing a list.
- If a mode removes the whole panel, restore focus to a valid ordinary-editor control.
- If server data is pending, keep the recipe visible and disable imported insertion with a loading explanation.
- On disconnect, release the screen/controller and cached knowledge. Never display the previous server's glyph state on the next connection.

Do not rebuild or write preferences every render frame.

### 11.4 Bounded work

The model should build in O(V + G log G) for V known/eligible variants and G spell groups, apart from explicitly bounded text operations. Paginate or virtualize widgets so screen creation is proportional to visible rows, not the whole registry.

Cache localized names and static metadata until their inputs change. Do not ask Iron's for expensive live spell computations per frame. Use the already accepted behavior model.

Validate only visible candidate rows and the selected detail level when the draft changes; validate the chosen concrete variant again on Add. Do not perform full recipe validation for every registered level on every frame.

For testing, exercise the actual catalog cap supported by each final branch. Main's inspected documentation describes 4,096 glyphs on Forge and a larger NeoForge 3.4.1 contract; that is not proof the inspected older NeoForge branch already implements the same cap. Derive the stress fixture from the final branch's accepted limits.

Measure model rebuilds and widget counts. Record the machine and fixture size for timing measurements instead of claiming a universal millisecond limit.

## 12. File-oriented implementation map

Paths below are relative to the repository. New paths are proposals; existing paths are grounded in the inspected snapshots.

### 12.1 Forge

| Existing file/area | Required work |
| --- | --- |
| `src/main/java/com/otectus/arsnspells/ArsNSpells.java` | Register CLIENT spec safely; guard server config handlers by type/spec. |
| `client/ArsNSpellsClient.java` under that Java package | Guard diagnostics config listeners; initialize client-only browser integration. |
| `client/screen/ConfigScreenFactory.java` | Add Personal display access independent of server permissions and connection state. |
| `client/GlyphClientSync.java` and `glyph/behavior/GlyphBehavior.java` | Reuse accepted publications and generation; preserve staging and recipe-viewer callbacks. Prefer observing existing generation over editing networking. |
| `glyph/ImportedGlyphRegistrar.java`, `ImportedGlyphDefinition.java`, `ImportedGlyphKey.java` | Read identity/metadata only; keep registration and serialization behavior. |
| `glyph/GlyphAvailability.java` | Consume existing status/tier/readiness; no local display decisions here. |
| `glyph/ImportedGlyphKnowledge.java` | Keep gameplay calculation unchanged; add regression coverage proving display invariance. |
| `src/main/resources/ars_n_spells.mixins.json` | Add client-only editor integrations if using this config; retain existing common mixins. |
| `src/main/resources/ars_n_spells.compat.mixins.json` and mixin plugin | Use only if appropriate to target optional integrations. Do not Iron's-gate cleanup UI that must still handle missing Iron's. |
| `src/main/resources/assets/ars_n_spells/lang/` | Add labels, empty states, help, narration, and translations/fallbacks. |
| `src/test/java/com/otectus/arsnspells/` | Pure-model, config routing, persistence, identity, and relevant bytecode/mixin-contract tests. |
| Existing client smoke and GameTest areas | Extend with real editor and gameplay-invariance cases. |
| `README.md`, `CHANGELOG.md`, release docs | Document mode, defaults, migration, and hiding versus disabling. |

### 12.2 NeoForge

| Existing file/area | Required work |
| --- | --- |
| `src/main/java/com/otectus/arsnspells/client/screen/ConfigScreenFactory.java` | Provide the same personal setting without relying on Forge's server-settings implementation. |
| `client/ClientGlyphEvents.java` | Preserve logout cleanup; attach browser lifecycle handling appropriately. |
| `glyph/ImportedGlyphRegistration.java` | Read registered effects; preserve distinct part/item IDs. |
| `glyph/ImportedIronEffect.java` | Read `key()`, `entry()`, and `behavior()`; keep casting availability independent of display. |
| `glyph/behavior/GlyphBehavior.java`, `GlyphBehaviorSnapshot.java`, `VariantBehavior.java` | Adapt accepted availability, tier, readiness, and generation into the UI model. |
| `network/ImportedGlyphSyncPayload.java` | Preserve catalog checks and authoritative publication; no personal-preference payload. |
| Client mixin resources and test infrastructure in the final branch | Implement modern Ars `GuiSpellBook`/`GlyphFormatter` integration and client-only class loading. |

Do not use Forge-only class names in NeoForge pseudocode or tests. Use neutral fixture expectations to keep behavior aligned across implementations.

### 12.3 New release documentation

Add `docs/3.4.2/glyph-browser.md` containing:

- All three modes and the default.
- Exact file path and TOML examples.
- In-game setting location.
- How grouped selection preserves exact levels.
- Favorites, search, filters, and the Dormant view.
- Why hidden glyphs can remain in a saved recipe.
- Why hiding does not disable casting or reduce registered identity counts.
- Known third-party editor limitations, if any.
- Troubleshooting for stale/pending server state.

Add `docs/3.4.2/glyph-browser-testing.md` with the actual tested commits, dependencies, steps, results, screenshots of each mode, and any untested cases.

Update existing relevant config documentation/generation rather than inserting a CLIENT preference into a server-only reference table without its scope.

## 13. Required tests and acceptance matrix

Test observable behavior and data preservation. Source-string assertions alone do not prove the feature works.

### 13.1 Pure model and preference tests

| ID | Fixture or action | Required result |
| --- | --- | --- |
| M01 | Known native and imported glyphs, Organized mode | Native candidates unchanged; imported rows grouped by original spell. |
| M02 | Same spell, levels I/III/VIII only | Exactly those learned variants; no implied intermediate knowledge. |
| M03 | Two original spell IDs sharing the same localized name | Two distinct rows with distinct sources/IDs. |
| M04 | One spell with available and dormant learned levels | Learned row contains available choices; dormant levels appear only on explicit inspection. |
| M05 | Thousands of registered but unlearned variants | No survival Learned rows from registration alone. |
| M06 | All imported knowledge dormant | Default palette remains clear; library empty state provides archive access. |
| M07 | All three modes against the same raw set | Identical source collection before/after; correct visibility. |
| M08 | Hidden with active query and stored favorites | No imported candidates, result link, favorite control, or archive count. |
| M09 | Search plus source/school/book filters | Filter before pagination; row counts use spells, level counts use variants. |
| M10 | Numeric levels 2, 10, 11 | Correct numeric ordering and exact variant selection. |
| M11 | Localized names change | Search index and ordering refresh; IDs/favorites do not change. |
| M12 | Empty list after a filter or server update while on the last page | Valid empty state; no invalid list slice or stuck navigation. |
| M13 | Unsupported/retired/missing package status | Branch-supported reason retained, never classified as available. |
| M14 | Pending or not-synced facts | Loading state and disabled insertion; no premature missing-spell conclusion. |
| M15 | Favorite from another world or missing addon | Preference retained; no unlearned entry appears. |
| M16 | Malformed/oversized/duplicate/future-version preference file | Bounded, non-crashing handling; preserve recoverable original data. |
| M17 | Invalid config mode | Safe grouped fallback and one diagnostic. |
| M18 | Selection becomes invalid between draw and Add | Add refused; no silent fallback to another level. |
| M19 | Shuffle iteration order of identical known sets | Identical grouping and deterministic output. |
| M20 | Local mode differs between two clients with the same catalog | Equal catalog identity digest and unchanged server behavior. |

### 13.2 Real editor tests

| ID | Scenario | Required result |
| --- | --- | --- |
| U01 | Forge standard book, Organized | Correct native palette and usable library. |
| U02 | Forge Infinity editor | Same behavior, including extended recipe windows. |
| U03 | NeoForge modern editor with bonus slots | Correct backing recipe, visible window, insertion, and save. |
| U04 | Save with an imported level, reopen, export/copy through supported native paths | Exact same real glyph ID and recipe order. |
| U05 | Unsaved named draft; open library; change mode; resize; return | Name, slot, sequence, gaps, and other draft state preserved. |
| U06 | Display setting from title screen | Works without a loaded world or SERVER config. |
| U07 | Non-operator on dedicated server and LAN guest | Can change personal display; cannot gain world-setting authority. |
| U08 | CLIENT config load/reload | No server migration, bridge refresh, session closure, or server-settings packet. |
| U09 | Learned set changes while editor open | Updates immediately via a supported event or within 10 client ticks via fallback. |
| U10 | Server disables/enables glyphs while editor open | Correct next-tick accepted-state refresh; draft untouched. |
| U11 | Server changes live maximum, tier override, or school | Correct row/filter/level state after accepted publication. |
| U12 | Current level invalidated during interaction | Explanation shown; exact Add refused. |
| U13 | Book tier too low, wrong form, effect-count limit reached | Add cannot bypass the existing validator. |
| U14 | Panel click overlaps native recipe button | Only the panel handles the click. |
| U15 | Search in Organized/Flat/Hidden | Expected mode-specific behavior without empty pagination pages. |
| U16 | Dormant saved glyph under Hidden mode | Saved composition still accurately represents it and its warning. |
| U17 | Creative book/player | Reproduces upstream eligibility without permanently learning or enabling anything. |
| U18 | GUI scales, narrow window, 720p, and 1080p | Controls fit; long names do not overlap; no inaccessible Add button. |
| U19 | Keyboard, narration, and focus after filtering | Full usable navigation; text communicates state without color alone. |
| U20 | Disconnect, join another server, respawn/player replacement | No old knowledge or availability leaks; favorites filtered correctly. |
| U21 | Iron's absent with retained imported knowledge | No client linkage crash; grouped archive/Hidden behavior still works. |
| U22 | Supported real acquisition recipe in pack | Hiding/grouping changes presentation only; recipe and learning remain intact. |
| U23 | Unsupported replacement editor | Safe diagnosed fallback; no half-installed grouping that loses access. |
| U24 | Main library and JEI open/refresh in one session | Both respond correctly; browser does not steal the viewer callback. |

For input tests, include Tab/Shift-Tab, Enter/Space, arrow navigation where implemented, and Escape layers. A favorite action must have a visible button; a keyboard shortcut is optional convenience, never the only route.

Narrate spell name, source, chosen level, book-tier restriction, dormancy reason, and Add action. Do not rely solely on grayscale, a colored border, or an icon to distinguish unavailable choices.

### 13.3 Gameplay and upgrade invariance

| ID | Scenario | Required result |
| --- | --- | --- |
| G01 | Snapshot learned IDs; cycle all display modes; save/reload player | Learned-ID set unchanged. |
| G02 | Snapshot spell recipe IDs and unrelated item data before UI actions | No mutation until an explicit ordinary edit/save. |
| G03 | Maximum mana and regeneration before/after local mode/favorite/filter changes | Identical under the same gameplay settings, including per-spell imported bonus policy. |
| G04 | Valid saved imported spell under Hidden | Same server acceptance, payment, cooldown, and effect as in visible modes. |
| G05 | Server disables imported glyphs, client uses any display mode | Existing authoritative refusal remains effective. |
| G06 | Player knows an Ars imported glyph but lacks an Iron's prerequisite | No new progression bypass. |
| G07 | Registry/identity handshake before and after local mode change | No registry or catalog digest difference. |
| G08 | Existing 3.4.0/3.4.1 knowledge with hundreds of dormant/available levels | Opens cleanly with no clearing/relearning migration. |
| G09 | Missing addon restored on a compatible test setup | Same identities recover; favorites remain associated with the original spell. |
| G10 | Two players select different display modes | Independent UI, identical shared world rules. |

Use a real imported-glyph item for the ordinary acquisition case. Use controlled bulk-known fixtures or an operator grant in a test world for the “many dormant learned glyphs” case. The latter is a deterministic regression fixture, not proof of which scroll caused the player's report.

Do not remove Iron's from a production world to test this. For absence tests use an isolated fixture appropriate to each branch's existing world-loading constraints; the repository already notes unrelated Iron's world-registry limitations in its removal workflow.

### 13.4 Build and runtime verification

Run each loader's existing build and test workflow. For the inspected Forge branch, relevant commands include:

~~~bash
./gradlew compileJava test build --no-daemon --stacktrace

./gradlew runGameTestServer \
  -PgametestRunDir=run-glyph-browser-absent \
  --no-daemon --stacktrace

./gradlew runGameTestServer \
  -PwithIronsRuntimeGameTests \
  -PwithArsElemental \
  -PgametestRunDir=run-glyph-browser-loaded \
  --no-daemon --stacktrace

./gradlew runGameTestServer \
  -PwithIrons316RuntimeGameTests \
  -PgametestRunDir=run-glyph-browser-irons316 \
  --no-daemon --stacktrace
~~~

Use separate run directories for incompatible profiles. Confirm actual executed versus skipped test counts using the repository's log verifier and update the expected counts for added tests. A successful Gradle exit alone is insufficient evidence of GameTest execution.

Run the corresponding NeoForge tasks/profiles defined by its final release branch. Do not assume Forge flags or verification scripts already exist there.

Run actual clients for the editor cases; headless GameTests cannot verify widget layout, native search integration, focus, or draft retention. Test Forge with Ars 4.12.7 and the supported Iron's profiles, and NeoForge with its pinned Ars 5.13.1.1400 plus the current supported 5.13.5 line where dependencies allow. Record which combination was actually exercised.

If both checkouts have the current parity infrastructure, run its comparison with the real other-checkout path, for example:

~~~bash
python3 tools/verify_loader_parity.py \
  --neoforge /absolute/path/to/neoforge-checkout \
  --section behaviour
~~~

Extend parity expectations for the pure browser model/fixtures as appropriate. Do not edit unrelated parity failures merely to make this feature appear complete. Report them separately with their baseline.

For the browser stress case, record distinct spells, learned variants, registered variants, visible widgets, number of rebuilds during idle rendering, and measured rebuild time. Confirm no rebuild or preference-file write occurs each frame.

## 14. Implementation order

1. **Establish the release bases.** Record exact branch heads and dependencies. Preserve already-landed 3.4.1/3.4.2 work. Add reproducible knowledge fixtures for mixed and bulk-known glyph sets.
2. **Add the local mode safely.** Implement CLIENT spec and exact config-event guards. Add Personal display access. Verify title-screen and non-operator behavior before adding the library.
3. **Implement the pure projection.** Classify imported identities, preserve native candidates, group by original spell, and cover gaps, duplicate names, readiness, filters, and favorites with behavioral tests.
4. **Complete Hidden and Individual levels modes.** Verify all supported editor candidates/search/pagination and acquisition paths. Confirm identity and mana invariance.
5. **Integrate Organized mode.** Add the bookmark, panel, level chooser, concrete insertion host, favorites, search, and empty states for each editor implementation.
6. **Add refresh and lifecycle handling.** Preserve drafts during config/snapshot changes, distinguish pending state, and clear connection-derived data on disconnect.
7. **Finish accessibility and compatibility.** Verify input, narration, translations, resizing, missing Iron's, addons, and fallback behavior.
8. **Run final validation and document evidence.** Build both targets, execute meaningful runtime/client tests, update docs and the 3.4.2 changelog, and record any real limitation.

Do not ship a half-working Organized mode as the default. Correct Hidden mode is necessary but does not complete the requested bloat reduction for players who remain opted in.

## 15. Definition of done

The feature is complete when all of the following are demonstrated:

- A player can choose Hidden using a documented config value or personal GUI without server permission.
- Organized is the default for new and existing installations that have no explicit preference.
- An existing crowded learned set becomes manageable immediately, without a destructive migration.
- Imported browsing is grouped by original spell and preserves every separately learned level.
- Native Ars and Ars addon glyph behavior is unchanged.
- Dormant entries stay out of normal browsing and remain explainable on demand.
- Search, filters, pagination, favorites, and empty states work together.
- Add inserts the exact selected registered glyph using existing validation.
- A display preference never changes casting, learning, mana, registries, catalog hashes, or other players.
- Drafts survive panel navigation, settings changes, and refreshes.
- Pending server data is not mislabeled as a permanent failure.
- Supported Forge and NeoForge editor implementations are verified in actual clients.
- Dedicated servers boot with the new feature and with optional Iron's absent.
- Existing settings and recipe-viewer synchronization continue working.
- The release evidence distinguishes passing, failing, and untested cases honestly.

### Outside scope for this feature

Do not expand 3.4.2's glyph-browser work into a new workstation, a replacement casting system, automatic glyph learning, refunded historical scrolls, destructive forgetting, a new rarity model, a new networked collection/progression system, or a rewrite of the whole Ars UI.

Grouping is not expected to reduce registered item count, baked-model count, save size, or network knowledge size. It reduces browsing clutter and the number of visible/validated UI entries. Document that distinction so performance claims remain accurate.

## 16. Player-facing changelog draft

Use only after implementation and validation:

> Added personal display settings for converted Iron's glyphs. Choose Organized, Individual levels, or Hidden without changing your learned glyphs or saved spells.
>
> Organized is the new default. Iron's glyphs have their own library inside the Ars spellbook, with one entry per spell and a chooser for its learned levels. Search by spell or source mod, filter by school and book tier, and favorite the spells you use most.
>
> Dormant glyphs stay out of normal browsing. You can inspect them in the library to see why they are unavailable. The browser refreshes when your learned glyphs or the server's spell settings change.
>
> Existing spells keep their exact glyphs. Hiding imported glyphs changes your browsing view; the existing server setting still controls whether imported glyphs can be used.

## 17. Source references

All repository references below are pinned to the snapshots inspected for this handoff. Class/member names in the implementation map come from those files; names explicitly introduced as proposed components are new design suggestions.

- **[S1] Forge build baseline:** [gradle.properties](https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/gradle.properties), [build.gradle](https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/build.gradle), and [mods.toml](https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/src/main/resources/META-INF/mods.toml).
- **[S2] NeoForge build baseline:** [gradle.properties](https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/gradle.properties).
- **[S3] Registration and identity:** [ImportedGlyphRegistrar.java, lines 35–69](https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphRegistrar.java#L35-L69) and [ImportedGlyphKey.java](https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphKey.java).
- **[S4] Existing visibility hooks:** [ImportedIronEffect.java, lines 84–105](https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/src/main/java/com/otectus/arsnspells/glyph/ImportedIronEffect.java#L84-L105).
- **[S5] Authoritative availability:** [GlyphAvailability.java](https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/src/main/java/com/otectus/arsnspells/glyph/GlyphAvailability.java), especially `status`, `decide`, `Status`, and `tier`.
- **[S6] Ars 4.12.7 editor:** [GuiSpellBook.java](https://github.com/baileyholl/Ars-Nouveau/blob/2c74064bc753600d9b6600f102f8163e2d6764dd/src/main/java/com/hollingsworth/arsnouveau/client/gui/book/GuiSpellBook.java), especially lines 93–115, 206–327, 420–433, and 630–647. [Version declaration](https://github.com/baileyholl/Ars-Nouveau/blob/2c74064bc753600d9b6600f102f8163e2d6764dd/build.gradle#L32).
- **[S7] Ars 5.13.1 editor:** [GuiSpellBook.java](https://github.com/baileyholl/Ars-Nouveau/blob/112920ff774831f204031da75b4c4e73d3765157/src/main/java/com/hollingsworth/arsnouveau/client/gui/book/GuiSpellBook.java), especially lines 94–125, 235–277, 369–391, and 713–784. [Current 5.13.5 source comparison](https://github.com/baileyholl/Ars-Nouveau/blob/b5e91cd626459f6d9194406711430d382e4d9364/src/main/java/com/hollingsworth/arsnouveau/client/gui/book/GuiSpellBook.java).
- **[S8] Knowledge and bulk grant:** [Ars 4.12.7 ANPlayerDataCap.java](https://github.com/baileyholl/Ars-Nouveau/blob/2c74064bc753600d9b6600f102f8163e2d6764dd/src/main/java/com/hollingsworth/arsnouveau/common/capability/ANPlayerDataCap.java#L27-L43) and [LearnGlyphCommand.java](https://github.com/baileyholl/Ars-Nouveau/blob/2c74064bc753600d9b6600f102f8163e2d6764dd/src/main/java/com/hollingsworth/arsnouveau/common/command/LearnGlyphCommand.java#L43-L60). These establish supported bulk-known states, not the origin of the reported scroll behavior.
- **[S9] Catalog scale and retention contract:** [CHANGELOG.md, 3.4.1 section](https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/CHANGELOG.md#L5-L43) and [identity-and-levels.md](https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/docs/3.4.1/identity-and-levels.md). Documentation about another branch is a contract to verify, not proof that branch implements it.
- **[S10] Knowledge affects mana:** [ImportedGlyphKnowledge.java](https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphKnowledge.java).
- **[S11] Live publication:** [GlyphClientSync.java](https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/src/main/java/com/otectus/arsnspells/client/GlyphClientSync.java) and [GlyphBehavior.java](https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/src/main/java/com/otectus/arsnspells/glyph/behavior/GlyphBehavior.java).
- **[S12] Server config event routing:** [ArsNSpells.java, lines 225–297](https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/src/main/java/com/otectus/arsnspells/ArsNSpells.java#L225-L297), [ArsNSpellsClient.java](https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/src/main/java/com/otectus/arsnspells/client/ArsNSpellsClient.java), and [existing imported feature setting](https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/src/main/java/com/otectus/arsnspells/config/AnsConfig.java#L1046-L1080).
- **[S13] Server-owned settings UI:** [ConfigScreenFactory.java](https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/src/main/java/com/otectus/arsnspells/client/screen/ConfigScreenFactory.java) and [AnsSettings.java](https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/src/main/java/com/otectus/arsnspells/config/AnsSettings.java).
- **[S14] NeoForge identity implementation:** [ImportedGlyphRegistration.java](https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphRegistration.java), [ImportedGlyphKey.java](https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphKey.java), and [ImportedIronEffect.java](https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/src/main/java/com/otectus/arsnspells/glyph/ImportedIronEffect.java).
- **[S15] NeoForge live behavior:** [GlyphBehavior.java](https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/src/main/java/com/otectus/arsnspells/glyph/behavior/GlyphBehavior.java), [VariantBehavior.java](https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/src/main/java/com/otectus/arsnspells/glyph/behavior/VariantBehavior.java), and [ImportedGlyphSyncPayload.java](https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/src/main/java/com/otectus/arsnspells/network/ImportedGlyphSyncPayload.java).
- **[S16] Forge Infinity editor:** [InfinityGuiSpellBook.java](https://github.com/baileyholl/Ars-Nouveau/blob/2c74064bc753600d9b6600f102f8163e2d6764dd/src/main/java/com/hollingsworth/arsnouveau/client/gui/book/InfinityGuiSpellBook.java).
- **[S17] Different acquisition-browser inputs:** [Ars 4.12.7 GlyphUnlockMenu.java](https://github.com/baileyholl/Ars-Nouveau/blob/2c74064bc753600d9b6600f102f8163e2d6764dd/src/main/java/com/hollingsworth/arsnouveau/client/gui/book/GlyphUnlockMenu.java#L72-L80) and [Ars 5.13.1 GlyphUnlockMenu.java](https://github.com/baileyholl/Ars-Nouveau/blob/112920ff774831f204031da75b4c4e73d3765157/src/main/java/com/hollingsworth/arsnouveau/client/gui/book/GlyphUnlockMenu.java#L78-L84).
- **[S18] Existing validation and item learning:** [ImportedGlyphCompositionValidator.java](https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphCompositionValidator.java) and [ImportedGlyphItem.java](https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphItem.java).
- **[S19] Verification infrastructure:** [CI workflow](https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/.github/workflows/ci.yml), [build profiles](https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/build.gradle), and [loader parity tool](https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/tools/verify_loader_parity.py).


[S1]: https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/gradle.properties
[S2]: https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/gradle.properties
[S3]: https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphRegistrar.java#L35-L69
[S4]: https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/src/main/java/com/otectus/arsnspells/glyph/ImportedIronEffect.java#L84-L105
[S5]: https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/src/main/java/com/otectus/arsnspells/glyph/GlyphAvailability.java
[S6]: https://github.com/baileyholl/Ars-Nouveau/blob/2c74064bc753600d9b6600f102f8163e2d6764dd/src/main/java/com/hollingsworth/arsnouveau/client/gui/book/GuiSpellBook.java
[S7]: https://github.com/baileyholl/Ars-Nouveau/blob/112920ff774831f204031da75b4c4e73d3765157/src/main/java/com/hollingsworth/arsnouveau/client/gui/book/GuiSpellBook.java
[S8]: https://github.com/baileyholl/Ars-Nouveau/blob/2c74064bc753600d9b6600f102f8163e2d6764dd/src/main/java/com/hollingsworth/arsnouveau/common/capability/ANPlayerDataCap.java#L27-L43
[S9]: https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/CHANGELOG.md#L5-L43
[S10]: https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphKnowledge.java
[S11]: https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/src/main/java/com/otectus/arsnspells/client/GlyphClientSync.java
[S12]: https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/src/main/java/com/otectus/arsnspells/ArsNSpells.java#L225-L297
[S13]: https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/src/main/java/com/otectus/arsnspells/client/screen/ConfigScreenFactory.java
[S14]: https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphRegistration.java
[S15]: https://github.com/otectus/ars-n-spells/blob/c234f19145d4380a849597f7e390fd535cc45e4f/src/main/java/com/otectus/arsnspells/glyph/behavior/GlyphBehavior.java
[S16]: https://github.com/baileyholl/Ars-Nouveau/blob/2c74064bc753600d9b6600f102f8163e2d6764dd/src/main/java/com/hollingsworth/arsnouveau/client/gui/book/InfinityGuiSpellBook.java
[S17]: https://github.com/baileyholl/Ars-Nouveau/blob/2c74064bc753600d9b6600f102f8163e2d6764dd/src/main/java/com/hollingsworth/arsnouveau/client/gui/book/GlyphUnlockMenu.java#L72-L80
[S18]: https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/src/main/java/com/otectus/arsnspells/glyph/ImportedGlyphCompositionValidator.java
[S19]: https://github.com/otectus/ars-n-spells/blob/6c26c544291ec3123eb1f5e97136d62d4d801b41/.github/workflows/ci.yml
