# Imported-glyph identity and levels (3.4.1)

This covers both builds: Forge 1.20.1 (Iron's 3.15.0 and 3.16.3) and NeoForge 1.21.1 (Iron's
1.21.1-3.16.3). Where the two differ, the difference is named. Casting, prices and the four cast
levels are in [casting-contract.md](casting-contract.md). Data-pack files are in
[mapping-schema.md](mapping-schema.md), and addon catalogs are in [catalog-packages.md](catalog-packages.md).

## What an identity is

An imported glyph is one Iron's spell at one base level. Its Ars spell-part id is
`ars_n_spells:iron/<spell namespace>/<spell path>/level_<n>`. Spell books, saved spells and
known-glyph lists store this id. The glyph item id differs per build, and neither is renamed for
parity:

| Build | Glyph item id |
|---|---|
| Forge | the part id itself, `ars_n_spells:iron/...` |
| NeoForge | `ars_n_spells:glyph_iron/<namespace>/<path>/level_<n>` |

The identity is the spell and the base level, nothing else. Behaviour is decided at runtime and is
not part of the identity: whether the glyph is enabled, Iron's live maximum, its tier, its school
and its price.

## The registered envelope

Every catalogued spell registers base levels from its **minimum** (Iron's minimum level for the
spell when it was catalogued, 1 for every Iron's spell) up to its **registered maximum**: Iron's
default maximum level, or 10 if that is higher. The base catalog covers 111 Iron's spells. That
is 777 glyphs at Iron's default maximums (all of 3.4.0's ids) and 1,110 registered ids. The 333
added levels are **reserved**. They exist so that a server raising Iron's maximum for a spell, up
to 10, needs no new catalog and no new ids.

Ids must exist before the game finishes loading, and Iron's fills its spell registry after Ars
registers glyph items. The GameTest `ironsLoaded_identity_ironsSpellsAreRegisteredAfterGlyphItems`
shows this on both builds: when the item registry fires, Iron's spell registry holds no spells. So
identities cannot come from Iron's live registry. They come from a catalog read when the mod is
constructed. Raising a spell above its registered maximum needs a catalog that extends the
envelope, then a restart. ANS ships one for levels XI-XV of every Iron's spell
([Levels above X](catalog-packages.md#levels-above-x)). The envelope can be at most 99, the limit of
the id encoding.

A level above Iron's live maximum is **dormant**: registered, kept in every book and inventory,
and refused with an explanation. It wakes as soon as the server raises the maximum to that level.
A level below the spell's live minimum on this server is dormant in the same way.
On Forge, reserved levels are hidden from Ars's creative glyph tab while they are dormant.
NeoForge has kept every ANS item, imported glyphs included, out of Ars's own tabs since 3.4.0;
they remain in creative search.

## Statuses

Each catalog entry has a status. **Every status registers all of its identities.** Only
`supported` glyphs can be cast.

| Status | Meaning | Glyphs |
|---|---|---|
| `supported` | an adapter casts it | available, subject to the live checks |
| `unsupported` | catalogued, but no adapter can cast it yet | registered, always dormant ("unsupported") |
| `retired` | was published, and its spell is gone or withdrawn | registered, always dormant ("retired") |

A published entry is never deleted. To withdraw a spell, mark it `retired`; its saved glyphs stay
dormant and never turn into another effect. This changed in 3.4.1: on NeoForge 3.4.0, an
`unsupported` entry registered no identities.

An `unsupported` entry with `adapter_version` 0 is a **placeholder**. It reserves a spell's ids
before any adapter can cast it, and a later version of the same catalog can promote it to an
adapter without new ids. Each castable entry names the adapter contract version it was written
for (`adapter_version`; 3.4.1 implements version 1). A glyph whose contract is newer than the
running build stays dormant instead of being cast under an older contract.

3.4.1 adds three dormancy reasons, shown wherever a glyph's status is (editor, tooltip, Press,
recipe viewer, cast refusal, `/ans glyph explain`):

| Reason | When |
|---|---|
| below the minimum | the base level is below the spell's live minimum on this server |
| needs a newer Ars 'n' Spells | the catalog's adapter contract is newer than this build implements |
| catalog package not installed | only ANS's retained copy registers the glyph: the package that published it was removed (see [The world identity record](#the-world-identity-record)) |

## The ledger

`src/main/resources/ars_n_spells/glyph_identity_ledger.txt` lists every imported-glyph id ANS has
ever published, 1,110 as of 3.4.1. The file is identical in both builds. It is append-only: a release
may add ids and never remove one. At startup, a catalog that does not register every ledger id
stops the game. The error names how many ids are missing and the first one. The unit test resource
`released_identities_3.4.0.txt` holds the 777 ids 3.4.0 published, and the catalog tests check that
all of them are still registered.

## Where catalogs come from

Catalogs are read once, at mod construction, in this order:

1. ANS's own required catalog: `ars_n_spells/iron_glyph_catalog.json` (Forge) or
   `ans_glyph_catalog/irons_spellbooks.json` in ANS's jar (NeoForge).
2. `ans_glyph_catalog/*.json` in any other mod jar, for example an addon's or a catalog-only jar.
3. `config/ars_n_spells/glyph_catalogs/*.json`.
4. ANS's retained copies, `config/ars_n_spells/retained_glyph_catalogs/*.json`: one per optional
   catalog this installation has accepted, written once and named by catalog and content hash.
   A copy is read for its identities only (every entry `retired`, adapter version 0), so it never
   changes how a spell is cast, and a copy identical to a live source is skipped.

The rules, the same on both builds (the shared fixtures in `ans_shared_fixtures/catalogs` prove
them on each):

- When two sources declare the same spell, its registered levels are the **union** of theirs: a
  source may extend a spell's levels, upward or downward, and can never remove one.
- The adapter contract (adapter, target, final-effect rule and contract version) has one owner. A
  placeholder contributes identities only. Only a later version of the same optional catalog (the
  same `catalog` name) may give a placeholder its first adapter, or move an adapter to a newer
  contract version. Any other source that would change a published contract is rejected.
- An optional source is accepted whole or rejected whole. That covers JSON that is not strict
  (comments, `NaN`, trailing data, duplicate keys), a schema other than 2 or 1, an unknown field, a
  number that is fractional or out of range, an id without a namespace, an invalid entry, a
  refused contract change, or exceeding the caps. A rejected source registers nothing, is logged,
  and leaves the other sources loading.
- A broken required catalog, including one over the caps, and a lost ledger identity stop startup
  with the reason. An optional source that would take the catalog over the caps is rejected. The
  caps are 1,024 spells and 4,096 glyphs on Forge, and 2,048 spells and 8,192 glyphs on NeoForge.
- Catalog schema 2 is current. 3.4.0's schema 1 is still read.

The client and the server must load the same catalogs. The identity digest is compared at login,
and a mismatch disconnects the client with both digests (unchanged from 3.4.0).

## The world identity record

Each world saves the imported-glyph identities it has run with, in saved data
`ars_n_spells_glyph_identities` (schema 2). For each spell it holds the union of every level range
the spell was registered with, and the catalog, version and source files that registered it. The
record only grows, and data that a later build adds is kept as it was read.

Before the server loads any level, chunk or player (`ServerAboutToStartEvent`), the record is
compared with the registered catalog. If an identity it recorded is not registered, the world
refuses to start, because Ars removes unregistered parts from spells, knowledge, inventories and
chunks as it saves them. The refusal, logged and shown as the start error, names each missing
spell and its levels, the catalog and version that registered them and the files they were last
loaded from. Nothing in the world has changed, so reinstalling the catalog package (or restoring
its retained copy) and starting again recovers every glyph. An unreadable record refuses the start
too. To drop the missing glyphs deliberately, start once with
`-Dars_n_spells.acceptMissingGlyphIdentities=true`: the record notes them as abandoned and stops
refusing them, and Ars drops them as those spells and items are next saved.

Removing a package alone does not trigger this: the retained copy keeps its identities registered
and dormant ("catalog package not installed").

## Three level quantities

| Quantity | Decided by | Changes with |
|---|---|---|
| Registered envelope | the catalogs | a new catalog, and a restart |
| Live maximum | Iron's effective configuration (per-spell config, 3.16's `global_config.json`, config events), read through Iron's own API | the server's Iron's config, and `/reload` |
| Cast level | each cast; see [casting-contract.md](casting-contract.md) | augments, equipment, Iron's events |

The live layer is server-authoritative. It is synced to every client with the catalog's identity
digest and a behaviour digest; NeoForge also sends its behaviour generation. It carries each
spell's enabled state, live maximum, school, learning requirement and per-level rarity. NeoForge
also sends each glyph's dormancy; Forge clients derive it from those facts. Iron's builds its
configuration only on the first data-pack sync (a player joining, or `/reload`). Before that, the
live layer is **pending**: catalog defaults stand in, decisions made on them are provisional, and
nothing final rests on them. On Forge, casts are refused with a message that the server has not
loaded Iron's spell settings yet. On both builds a restored Glyph Press job waits for them instead
of being cancelled (`AWAITING_CONFIG` on Forge). A client that has not received the server's facts
reports "not synced", never "unavailable".

What the live maximum does:

- A glyph whose base level is above it is dormant everywhere: editor, tooltip, Press, JEI and cast.
- A cast that Amplifies past it is refused before anything is paid. It is never clamped.
- A scroll whose level Iron's accepts but no catalog registers is refused by the Press with its own
  reason: no glyph is registered for that level. It is never pressed into a lower level.
- The school that policy, progression, discounts, colours and explanations use is the live school,
  not the catalogued one (GameTest `ironsLoaded_school_liveSchoolDrivesPolicyAndProgression`, both
  builds). On NeoForge, Ars's own per-school part lists, which Ars foci read, keep the catalogued
  school. Ars has no hook to move a registered part between schools consistently on server and
  clients.

## Diagnostics

| Command | Permission | What it shows |
|---|---|---|
| `/ans glyph report` | operator | the catalog and any rejected source with its reason (on Forge, what each source added), the readiness and generation, ids this world used that are missing, status counts, and one row per native spell, so spells no catalog covers stand out. Also written to `ars_n_spells/reports/glyph-catalog-report.txt` in the server directory |
| `/ans glyph explain <spell> [level]` | operator | one spell's catalog entry (status, registered and default maximum), Iron's live maximum, school and learning, its data-pack mapping, and each level's status |
| `/ans glyph validate` | operator | a dry run of the loaded data packs' mapping files: valid, winners, errors with file and field, and spells that fail closed |
| `/ans glyph inventory export` | operator | writes `ars_n_spells/reports/glyph-inventory.json` in the server directory, the input for [catalog-packages.md](catalog-packages.md) |
| `/ans glyph last` | any player (Forge only) | your last imported-glyph casts: base, augmented, native and cast level, price paid, and outcome |
| `/ans glyph sessions [player]` | any player for themselves, operator for another (NeoForge only) | the player's open imported-glyph casts and last ten outcomes: the committed level, Iron's settled price and the hit of each applied execution, and the reason for each refusal |

## Migration

**From 3.4.0.** No action is needed. All 777 ids are unchanged. The 333 reserved ids are new and
dormant until a server raises Iron's maximum. The client and the server both need 3.4.1, because
the network protocol changed (Forge 10, NeoForge 11).

**Catalog expansion** (addon spells, or levels above 10). Install the catalog on the server and on
every client, as a catalog-only jar or in `config/ars_n_spells/glyph_catalogs/`, then restart.
Identities register at startup, so `/reload` is not enough.

**Removing an addon.** Its ids stay registered as long as its catalog is installed, and its glyphs
are dormant ("spell missing"). If the catalog package goes too, ANS's retained copy keeps the ids
registered, dormant ("catalog package not installed"). Either way saved spells keep the glyphs, and
they work again when the addon and its package return. Only when the package and its retained copy
are both gone does the world refuse to start (see [The world identity record](#the-world-identity-record)).

**Lowering Iron's maximum.** Glyphs above the new maximum become dormant. Saved spells keep them,
Amplify cannot bypass the limit, and raising the maximum again restores them.

**Retiring a spell from a catalog.** Change its status to `retired`. Never delete the entry: the
pack tool keeps it for you (see [catalog-packages.md](catalog-packages.md)).
