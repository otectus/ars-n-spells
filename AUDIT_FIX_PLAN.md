# Ars 'n Spells — Audit Fix Plan (v3.0.1)

Prioritized remediation for the findings in `AUDIT_FINDINGS.md`. One commit per item; every phase gate runs
`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew compileJava test --no-daemon`.
Nothing in this plan adds, removes, or renames a config key or changes world data. The two behavior-adjacent changes are F1 (uncraftable → craftable, the intended behavior) and F12 (render-path consolidation, visually identical).

## Phase 1 — Runtime-visible defects (fix first)

| Item | Change | Risk | Guard |
|---|---|---|---|
| F1 | New `data/ars_n_spells/tags/items/irons_spell_books.json` (16 tiered books, every entry `"required": false` so the tag loads Iron's-less); both apparatus recipes' pedestal `{"item": "irons_spellbooks:spell_book"}` → `{"tag": "ars_n_spells:irons_spell_books"}` | Low (data-only) | New `ApparatusRecipeIngredientTest`; runtime log grep in Phase 5 |
| F2 | `pack.mcmeta` `forge:data_pack_format` 12 → 15 | Trivial | Same test |

## Phase 2 — Dead code & mechanical cleanups

| Item | Change | Risk |
|---|---|---|
| F9 | `MixinScrollItem` 3 × `Component.literal` → `Component.translatable` (keys pre-existing) | Low — identical English output |
| F10 | Delete dead `IronsLPHandler.storePendingScrollLP` (0 callers) | Low — changelog note for hypothetical reflective callers |
| F4 | Finalize ANS-MED-031 tombstone comments in `AnsConfig` (removal already shipped in 3.0.1) | None (comments) |
| F6 | Record the verified `/reload`-safety of the ritual splice in `RitualRegistryHandler` javadoc | None (comments) |

## Phase 3 — Config semantics & client hygiene

| Item | Change | Risk |
|---|---|---|
| F5 | Route direct `ENABLE_MANA_UNIFICATION.get()` reads guarding mode-dependent logic through `BridgeManager.isUnificationEnabled()` (`RegenSynergyHandler`, `EquipmentHandler` ×3, `EquipmentIntegration`, `MixinSanctifiedAbstractSpell`, `ManaBarController`); document precedence on the helper. Keep both config keys. | Low-med — behavior identical for every reachable state (each site already required toggle && consulted mode); each site reviewed at implementation |
| F3 | Move `config/ConfigScreenFactory.java` → `client/screen/`; update `ArsNSpellsClient` import + 3 test source-path literals; move the two ConfigScreenFactory tests to the matching package | Low — mechanical; class only classloaded from client init |

## Phase 4 — Mixin deletion

| Item | Change | Risk |
|---|---|---|
| F12 | Delete `MixinIronsManaBarOverlay`; update `ars_n_spells.mixins.json`, `ArsNSpellsMixinPlugin`, `ArsNSpellsMixinPluginGatingTest` | Med-low — redundancy proven against Iron's 7402504; mixin was `require=0` (already allowed to no-op); `ManaBarController` covers all 1.20.1 Iron's builds (overlay registered via `RegisterGuiOverlaysEvent` throughout) |

## Phase 5 — Verification

1. `./gradlew clean compileJava test` — full JUnit suite including new tests.
2. `./gradlew runGameTestServer` — Iron's-less: proves mixin-plugin gating post-deletion and that the optional tag loads without Iron's (no tag errors).
3. `./gradlew runGameTestServer -PwithIronsRuntimeGameTests` — must remain 11/11; then grep `run/logs/latest.log`: **zero recipe parse errors for `ars_n_spells:apparatus/*`** (concrete proof of F1).
4. Write `TESTING_REPORT.md`: commands, results, log excerpts, and the explicit manual-verification list (client GUI checks, dedicated-server multiplayer, Covenant/Blood Magic/Nature's Aura-loaded runs, in-game ritual craft with two different book tiers, `/reload` + tablet use).

## Phase 6 — Documentation

- Update `TESTING_GUIDE.md` to 3.0.1 (remove 3.0.0/2.0.0/1.9.0 drift).
- Delete the three stale untracked planning docs (`ars-n-spells-2.0.0.md`, `ars-n-spells-mana-fixes.md`, `ars-n-spells-update.md`) — user-approved.
- CHANGELOG "Unreleased" section; **no version bump** (user decision — release cut later).

## Deferred (documented, intentionally not changed)

- **F7** — capability `HashMap`s: documented server-thread invariant is correct; `ConcurrentHashMap` would mask compound races.
- **F8** — `SpellAnalysis` substring school matching: a data-driven map is the right fix but shifts affinity/progression classification in existing worlds; defer to a feature release.
- **F4 (wiring)** — configurable progression/affinity curves would re-introduce config keys deleted in released 3.0.1; feature work, not remediation.
- Architectural fragilities (Covenant bytecode pin, reflection fail-open, SEPARATE-mode distributed accounting, proxy-registry classload discipline): documented in `AUDIT_ARCHITECTURE.md` appendix; rewrites would exceed audit scope and risk regressions in heavily-tested paths.
