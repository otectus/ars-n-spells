# 3.3.0 school and inscription implementation evidence

This is the scoped evidence map for V15–V20, V22 and the school portions of V25. It documents implemented source and fixtures. It is not a replacement for the release ledger's executed test logs.

| Audit area | Implementation | Verification entry points |
|---|---|---|
| V15 glyph roles / metadata | Typed non-payload guard precedes overrides; declared metadata before heuristic; captured namespaced memberships | `SchoolResolverTest`, `SchoolMappingLoaderTest`, `SpellAnalysis*Test` |
| V16 custom schools | `SchoolKeys`, registry-backed `IronsSchoolAttributes`, namespaced scaling/affinity/progression; unresolved data preserved | pure namespace/migration tests plus `SchoolInscriptionNativeGameTests`; real third-party SchoolType runtime still requires its own profile |
| V17 datapacks / sync | schema1/2 deterministic merge, priority/mod gates, bounded sizes, immutable snapshots, provenance/digest, S2C login/reload, client logout clear | `SchoolMappingLoaderTest`; actual multiplayer reload acceptance required |
| V18 source conservation | shared `InscriptionPlanner` and outcome, read-only source discovery, real native scroll blankness | `InscriptionSourcePolicyTest`, `InscriptionOutcomeSingleUnitTest`, Loom native GameTests |
| V19 loom container | exact inputs, shared plan for preview/action, explicit convert, output refusal, sided capability contract, copy-and-clear drops | six `SpellLoomInscriptionGameTests`; client picker and survival automation sessions tracked separately |
| V20 binding / teardown | work-copy bind transaction, native write refusal, known schema stamps, ordered proxy removal and recorded capacity restoration, complete ANS metadata teardown | binding/allocation/teardown tests plus `SchoolInscriptionNativeGameTests` real-book round-trip; old-capacity runtime acceptance |
| V22 ritual source behavior | exactly-one candidate discovery, one-unit split, reusable preservation, output-spawn success before input consumption | ritual lifecycle/tablet tests and shared planner; external canceled-spawn event fixture remains additional acceptance |
| V25 diagnostics | `/ans diagnose`, `/ans schools`, `/ans journal`, `/ans removal_report` read-only subscriber in both loaders | compilation plus manual dedicated-server command acceptance required |

The Forge and NeoForge implementations intentionally use their native API boundaries: root NBT and Forge capabilities versus NeoForge data components, attachments, `RegistryFriendlyByteBuf` and capability registration. The shared planner, mapping loader, keys, and deterministic mapping policy contain no loader transaction behavior.

New focused tests include deterministic mapping provenance/digest across shuffled input order, custom namespace collisions, malformed/mod-gated isolation, prevention of filter promotion, post-publication immutability, oversized snapshot rejection, native filled-scroll source permission, empty reusable-source refusal, and NeoForge defensive payload copying and unresolved legacy affinity round-trip.

Release acceptance must retain separate labels for source implemented, unit tested, native runtime tested, client visually tested, and optional integration skipped. At the time this document was authored, root coordination owned the final build/runtime logs; no pass count is invented here. Regenerate configuration and recipe references after the final source changes. Client icon assets and native-carrier selection evidence are covered by their dedicated manifest and the root release status.

The Worn Notebook integration adds five translated entries using page types inspected from the pinned Ars 4/5 jars: text, crafting, and Ars apparatus recipe. Iron-dependent entries use Patchouli's `mod:irons_spellbooks` flag. Resource JSON/local recipe references are checked; notebook navigation and text layout remain a client visual acceptance step.

Neo component records now archive unrecognized extension fields defensively, including on wire round-trip and known list mutations. Their saved field names remain unchanged; protocol 5 carries the added archival envelope. Golden tests preserve unknown entry/list fields and future Forge schema stamps. This preserves extensions to a decodable known shape; it is not a decoder for an arbitrary incompatible future Minecraft/component format.

The Loom now exposes planned per-action source/target consumption, output count/capacity, and a Details inspector from synchronized slots plus the server mapping snapshot. Five local cosmetic presets persist only name/background/icon in `config/ars_n_spells-loom-presets.json`; writes are explicit and atomic, bounded reads reject malformed/future files without overwriting them. These controls do not execute inscription or change gameplay rules.

`/ans journal view` captures up to64 own-player rows server-side and opens a standalone paged journal through a strictly S2C bounded snapshot. It includes configured caps, actual owned progression modifiers, native attribute identities, full school keys, and mapping digest. New codec tests cover custom identities and rejecting count overflow before row allocation. Neo screens avoid Screen.render's implicit blur over custom opaque panels; Neo Loom delegates its single background/event pass to AbstractContainerScreen.

The read-only `/ans inspect` command uses authoritative held inventory and `QuoteService` with captured rules/player maxima. It exposes selection, school, native base and resource units plus balance and generations. It deliberately does not dispatch native cost events or select alternative payments, so its label excludes their final adjustments. Native Iron's slot 1 and ANS selection are explicit; the command does not claim to follow the native wheel. Source extraction uses copies and validates ANS schema/payload before decoding. This adds inspectability without beginning a cast transaction.
