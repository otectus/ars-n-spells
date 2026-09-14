# Ars 'n' Spells 3.3.0 pack-author reference

Use the reference generated in the **same loader checkout** as the jar. [Configuration](config-reference.md) lists every declared key, default, bounds/validator arguments, runtime symbol references, and direct test references. Its adjacent JSON is machine readable and includes the source SHA-256. [Recipes](recipe-reference.md) includes all six shipped recipe documents, including conditional wrappers and apparatus Source costs. Rebuild these references with `tools/generate_release_references.py` after changing configuration or recipes.

## Version and support scope

The audit targets Forge Minecraft 1.20.1 / Java 17 / Forge 47.4.10 / Ars Nouveau 4.12.7 / Iron's runtime artifact 7402504 (3.15.0), and NeoForge Minecraft 1.21.1 / Java 21 / NeoForge 21.1.248 / Ars Nouveau 5.13.1.1400 / Iron's 3.16.3. These identify pinned environments, not a claim that every optional-mod/client/multiplayer scenario has run. See [acceptance evidence](school-inscription-evidence.md) and the release ledger for actual runs.

Covenant of the Seven integration belongs to the Forge branch; its mod id is `covenant_of_the_seven`, with Enigmatic Legacy integration gated separately. It is not a NeoForge feature. Ars Elemental, Ars Zero, Too Many Glyphs, and NeoForge Ars Elemancy are optional. Runtime detection, registry resolution, pure contract coverage, native GameTests, and client screenshots are different evidence levels. Do not label a detected mod as fully certified merely because the game started.

## School mapping datapacks

Place JSON in `data/<namespace>/ans_glyph_schools/<file>.json`. Schema 1 preserves the old scalar shape:

```json
{
  "glyphs": { "ars_elemental:glyph_life_link": "blood" },
  "ars_schools": { "water": "ice" }
}
```

Schema 2 supports ordered school membership, explicit roles, a per-file priority, and mod prerequisites:

```json
{
  "schema_version": 2,
  "priority": 20,
  "requires_mods": ["ars_elemental"],
  "glyphs": {
    "ars_elemental:glyph_life_link": {
      "roles": ["payload"],
      "schools": ["irons_spellbooks:blood"]
    }
  },
  "ars_schools": {
    "necromancy": ["irons_spellbooks:eldritch"]
  }
}
```

The file identity is its full resource ID without the directory/suffix supplied by the reload listener. Files are ordered by ascending priority then lexicographic resource ID. Later valid entries win; duplicate diagnostics identify the previous provenance. A file whose `requires_mods` is not satisfied is ignored. Malformed entries are isolated; other valid entries still load. Unsupported schema versions are ignored with diagnostics.

Supported roles are `payload`, `filter`, `augment`, `cast_method`, and `control`. An explicit role list without `payload` yields generic membership. This does not override the actual Java type: a true filter, augment, or cast method cannot become a payload by setting `roles` in JSON. No new spell or glyph is registered by a mapping file.

Known short built-in keys normalize to `irons_spellbooks:<school>`; `generic` becomes `ars_n_spells:generic`. Unknown short keys are rejected from datapacks. Valid namespaced custom keys are retained even if currently unresolved. Attribute binding requires an actual registered Iron `SchoolType` and its real power/resistance attribute. ANS does not guess an attribute ID from the school's path and does not create a phantom school. A namespaced custom school with path `fire` never aliases built-in fire.

Glyph registry IDs are limited to 256 characters, membership to 1–16 schools, and each map to 4,096 entries. Each section also has a 32,768-character budget covering keys, memberships, and provenance; over-budget entries are diagnosed and omitted. File IDs are limited to 256 characters. The budget protects the synchronized snapshot size.

Resolution prefers an exact glyph mapping, then declared Ars school metadata with Ars-school overrides, then a small legacy path heuristic. Membership order is preserved. The first payload supplies the primary school for progression/affinity. NeoForge supports its existing multi-school scaling strategy (`primary`, `max`, or `average`); refer to its own configuration declaration for spelling/defaults. Legacy enum classification remains for older cooldown/category policies, while elemental attribute application and persisted school identity use namespaced keys.

On login and `/reload`, the server sends its immutable merged map, provenance, and digest to clients. A client disconnect clears the prior server snapshot. The payload is server-to-client only; a client cannot nominate school semantics. `/ans diagnose` and `/ans schools` expose the current digest. Icon/resource-pack customization changes visuals and does not authorize gameplay school overrides.

## Resource rules and migration

`flat_legacy` multiplies only cross-pool payment legs by the configured directional conversion rate. The two direction rates are independent and need not be reciprocals. `equal_percent` instead converts using the current native maxima (`destination maximum / origin maximum`), with positive finite maxima required. Native casts do not acquire cross-cast overhead. Cross-casts receive the configured multiplier once.

Example before final per-leg rounding: an Ars-origin cross-cast priced at 100 Ars mana, multiplier 1.25, and Ars-to-Iron rate 2 costs 250 Iron mana in `iss_primary` or `hybrid`, and 125 Ars mana in `ars_primary`. In `separate` with equal normalized shares, it costs 62.5 Ars plus 125 Iron. With unification disabled it costs 125 Ars. Under `equal_percent`, an Ars maximum of 200 and Iron maximum of 500 changes that conversion factor to 2.5. Equipment and alternative-payment modifiers still participate in the runtime quote.

Quotes capture their rule generation and paying units before reserve. Multi-leg payment reserves the complete bundle or refuses it; partial withdrawals are refunded. Server lifecycle validation and replay/carrier guards are part of the casting boundary. Debug logs and `/ans diagnose` expose identities and generations without dumping raw spell payloads. The read-only command does not produce a complete per-glyph combat simulator.

Config schema 2 backs up an existing file to `<filename>.pre-3.3.0.bak` before migration. For an older Source Jar multiplier `m` and scan interval `i`, the migrated multiplier is `m × 20 / i`, preserving the old average income. Subsequent interval changes control discovery only. Migration also persists the resolved payment-open failure policy. Existing configurations retain their explicit/legacy behavior; new configurations use the current default. Inspect the migration log and generated configuration reference for the policy that actually applies.

Restart after changing loader, content mods, registries, or native resources. Use the loader's normal configuration reload lifecycle for supported live numeric/feature changes and `/reload` for datapack mappings/tags. A registered config key alone does not prove all GUI controls or hot-reload paths are covered. Branch-only and compatibility keys remain visible in the generated inventory.

## Carriers, recipes, and automation

The Loom only accepts readable Ars sources and real Iron scroll targets. Reusable books/focuses are preserved; disposable sources and targets consume one unit. Explicit conversion is the only Loom path that replaces a filled target. A server packet cannot bypass the menu's current block entity, distance, input, or output checks. Duplicate exports and occupied output slots are refused before mutation.

The transcription planner is shared between loader adapters. It distinguishes reusable books/focuses, consumable scrolls, blank targets, and filled scrolls. A filled native scroll is allowed as a source; it is refused as an implicit target. Ritual discovery requires exactly one source and target candidate. A failed output spawn does not consume the discovered input stacks.

The `ars_n_spells:irons_spell_books` item tag extends the book eligibility contract. A tagged item must actually tolerate Iron's native spell-container writes; failed native writes reject the entire binding transaction. Native ANS proxy capacity is recorded when first extended in 3.3.0. Unbinding shrinks empty added tail slots only, preserving later native spells. Old capacity without a baseline is retained. `max_ars_cross_spells_per_irons_spellbook=-1` still has the eight-proxy upper bound.

The block automation contract is top insert source, horizontal insert target, bottom extract output. Unsided access permits either valid input and output extraction, with input extraction denied. Do not assume automation can insert arbitrary stack types because the block owns an item handler.

Override apparatus recipes in the loader's correct directory: Forge uses `recipes`, NeoForge 1.21 uses `recipe`. Keep conditions appropriate to optional dependencies. The generated recipe reference is the authority for actual installed file content; item tags can be changed by datapacks. Preserve server-side legality checks when changing costs or discoverability in JEI/EMI.

Source Jar proximity checks `ars_n_spells:source_jars`. Empty tagged jars qualify and are not drained. Only loaded chunks are scanned; missing chunks do not count as a cached positive. Tag reload, movement, dimension, interval expiry, logout, and server stop invalidate relevant cached observations. The configured bonus is active-pool mana per second and does not stack per jar. Mana Well does stack per ritual and includes all intersecting players. Measure any pack-specific farm or overlapping-ritual balance in its actual server profile.

## Save compatibility and removal

Forge stores ANS carrier root-NBT sidecars; NeoForge uses registered data components with codecs and stream codecs. Known old keys are read and normalized. School progression migrates built-in short aliases to full identities using the maximum when duplicate aliases already exist, avoiding double-count inflation. Unknown school IDs remain saved and unresolved. NeoForge archives unmappable legacy affinity category keys rather than silently converting them to a different elemental school.

Compound spell payloads are copied at ownership boundaries. NeoForge entry/list codecs retain unknown extension fields in defensive archival compounds and synchronize them with protocol 5; known list edits preserve those extensions. Arbitrarily incompatible future component shapes still require their matching version. Unsupported future inscription schema versions cannot be appended to, restamped to an older version, or cast by this build. Explicit uninscription is an intentional user removal operation, not an implicit migration. Cosmetic changes do not alter the spell payload's school.

No general world importer is provided between Forge and NeoForge. Removing upstream content mods can remove item, entity, or dimension registry entries independently of ANS. `/ans removal_report` is a dry run for the calling player's ANS inventory entries plus registered non-vanilla dimension-type keys; it does not scan the entire world or delete dormant data. Back up saves and use upstream removal instructions.

## Acceptance evidence for a pack

Run the relevant unit contracts, dedicated-server GameTests, and every optional runtime profile you claim. A prerequisite skip is not a pass for that integration. Keep the exact mod list/versions, branch, jar hashes, logs, test counts, and mapping/config digests. Exercise two players with identical proxy pool IDs on different books; repeat with main hand, offhand, and Curios selection. Check Loom conversion, failed actions, stack remainders, hoppers, block break/reload, old saves, logout/rejoin, `/reload`, and removal/reinstall. Capture native wheel/HUD/tooltip and picker screenshots at the intended GUI scales. Server tests cannot certify native client rendering or third-party recipe-browser layouts.

## Adapter kits and reviewed presets

Use the [addon adapter kit](addon-adapter-kit.md) for exact version/role/school fixtures and conditional datapack generation. The [four server-config presets](pack-presets.md) provide a dry-run diff and require an explicit apply command; cosmetic client presets do not change server economics.
