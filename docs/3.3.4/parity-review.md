# Forge 1.20.1 / NeoForge 1.21.1 supported-feature parity

Reviewed on 2026-09-14 against the current Forge 3.3.4 working tree. The NeoForge
checkout was previously 3.3.3. This pass ports the missing casting repair and fixes
additional supported-feature differences found by comparing production code,
configuration, commands, resources, tests, and the pinned dependency JARs.

## Scope

The user approved exceptions for integrations whose dependencies do not support
1.21.1. Covenant LP/aura/Blasphemy and Too Many Glyphs therefore remain excluded.
Their upstream file catalogs list no 1.21.1 build:
[Covenant](https://www.curseforge.com/minecraft/mc-mods/covenant-of-the-seven/files/all),
[Too Many Glyphs](https://www.curseforge.com/minecraft/mc-mods/too-many-glyphs/files/all).
The portable alternative-payment engine and its failure tests are shared, but
this does not introduce working Covenant adapters or their configuration into NeoForge.

NeoForge retains native data components, attachments, holder-based attributes,
resource formats and network payload APIs. Existing NeoForge-only options and
Ars Elemancy support remain available. The two existing configuration locations
for schema version and conversion policy are preserved to avoid losing saved
settings; their defaults and bounds match Forge. No cross-version world conversion
is provided.

## Changes and evidence

| Area | Result and verification |
| --- | --- |
| Native casting | Ported final event pricing, suppression of the replaced native mana write, invocation-scoped identity, reentrant/duplicate rejection, carrier and pricing checks, channel interruption, scroll completion and native/category cooldown ownership. Native heal, book, channel and recast tests run against Iron's 1.21.1-3.16.3. |
| Payment failures | Shared double-precision Ars reads, native float rounding, measured partial/false/throwing debits, compensation, unknown movement quarantine, recovery persistence and exception-safe validation. All 33 portable domain sources/fixtures match Forge exactly after line-ending normalization. |
| Equipment | Restored `Curio Discount System.read_curio_attribute_modifiers`. The native Curios event fixture proves enabling/disabling/re-enabling changes the mirrored contribution exactly once. Existing armor, enchantment, pool-ceiling and cleanup paths use NeoForge APIs. |
| Affinity and resonance | Public affinity helper now returns 0.5% per level like Forge. New-config resonance threshold is 0.95 and mixed-school damage defaults to primary-school selection. The main affinity combat path already used 0.5%. Saved choices are preserved. |
| Source and recipes | Shared Source Jar bounds now agree. Seven recipes retain their ingredients, outputs, source costs and optional-mod gates after native format conversion. Mana Infusion/Well and binding/transcription lifecycle tests pass. |
| Loom and presentation | Supported resource inventory matches: models, textures, icon states/backgrounds, mappings, tags, advancements, recipe data and translation coverage. Existing server menu, inscription, native table and carrier tests pass. |
| Loot | Restored blank-scroll injection into `catacombs/armory_loot` and `citadel/citadel_tomes`, both present in the pinned NeoForge Iron's JAR. Forge's `catacombs/crypt_loot` no longer exists upstream; NeoForge's existing `catacombs/dead_king_vault` injection is retained. |
| Requests and synchronization | Carrier fingerprints now include the entire native item serialization rather than ANS data alone. Native custom-data and name changes invalidate stale requests. Protocol 7 requires matching client/server builds. Native attachment/component/payload storage remains intact. |
| Commands | Every supported Forge command literal exists on NeoForge. The excluded `/ans aura` command depends on Covenant. NeoForge's additional combat diagnostics remain. |
| Optional dependencies | Fresh absent, Iron's-only, and combined Iron's + Ars Elemental + Ars Zero + Ars Elemancy profiles complete their required GameTests. Combined profile exercises every optional scenario, with zero skips. |

## Validation

- Clean Gradle build and 876 unit tests: zero failures, errors or skips.
- Iron's absent: 112 required GameTests pass; 1 optional scenario executes and 74 skip for absent dependencies.
- Iron's only: 112 required GameTests pass; 62 optional scenarios execute and 13 skip for absent addons.
- All supported addon profiles: 112 required GameTests pass; 75 optional scenarios execute and none skip.
- Contract equality: 33 sources/fixtures, both checked-in manifests valid.
- Configuration/resource check: 53 shared fields and 1,458 resources; no unclassified mismatch.
- Icon verifier: 274 logical icons, 1,370 state rasters and 11 backgrounds, plus HD32 variants.
- Python tooling suite: 26 tests pass, including negative cases for recipe normalization.

`parity-inventory.json` records the static check and each explicit exception.
`validation.json` records artifact hashes, unit totals, runtime identities and compressed
logs. `config-reference.md`, `config-reference.json` and `recipe-reference.md` describe
this checkout. The source inventories are checks of coverage; gameplay evidence is the
separate unit/native test suite.

The native tests deliberately inject exceptions to prove refusal and recovery behavior;
those expected traces appear in successful logs. The log verifier also checks the explicit
completion counts and actual loaded mod identities, rather than accepting Gradle's exit
code alone.

This pass does not claim new real-client screenshot, two-player latency, arbitrary
third-party modpack or future dependency-version certification. The older broad release
acceptance ledger is not relabeled as fully executed. These limits are distinct from the
approved unavailable-dependency exceptions and from the supported-feature source changes.

## Reproduce

```sh
./gradlew --offline clean build
python3 tools/contract_parity.py --other /path/to/forge-checkout
python3 tools/verify_loader_parity.py --forge /path/to/forge-checkout
python3 -m unittest discover -s tools -p 'test_*.py'
python3 tools/verify_icons.py
./gradlew --offline runGameTestServer -PgametestRunDir=/tmp/ans-parity-absent
./gradlew --offline runGameTestServer -PwithIronsRuntimeGameTests -PgametestRunDir=/tmp/ans-parity-loaded
./gradlew --offline runGameTestServer -PwithIronsRuntimeGameTests -PwithArsZero -PwithArsElemancy -PgametestRunDir=/tmp/ans-parity-addons
```

`withArsZero` and `withArsElemancy` imply the Ars Elemental runtime. Keep the unit suite
in its default absent profile; its deliberate classpath-absence tests must not be run
with the Iron's runtime flag. Use separate game directories for different dependency sets.
