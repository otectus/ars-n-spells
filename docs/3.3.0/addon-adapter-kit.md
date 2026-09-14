# Addon adapter kit

The kit creates a loader-correct, conditional school-mapping datapack from an explicit addon manifest. It keeps exact tested versions and expected payload/filter roles next to the mapping. It does not introduce reflective hooks, register replacement glyphs, or certify gameplay by detecting an installed jar.

Start with [`examples/addon-adapter/ars-elemental.json`](../../examples/addon-adapter/ars-elemental.json). The sample uses real pinned Ars Elemental IDs: Life Link is an explicit blood payload override; Aquatic Filter remains generic. Change the mod ID, exact loader/version profiles and glyph fixtures for a different addon. Every school must retain its namespace. A custom school needs a real registered Iron's `SchoolType` with its own attribute; declaring a mapping alone cannot create it.

```bash
python tools/addon_adapter.py examples/addon-adapter/ars-elemental.json --loader forge
python tools/addon_adapter.py examples/addon-adapter/ars-elemental.json --loader neoforge --output build/example-neoforge-adapter
```

Generation requires an empty output directory and writes `pack.mcmeta` plus `data/ars_n_spells/ans_glyph_schools/<addon>_adapter.json`. Forge uses data-pack format 15; NeoForge 1.21.1 uses 48. Generated mappings require the addon and Iron's. Schema validation is reported as `schema_valid_runtime_unverified`.

For a real runtime capture, record the exact loaded version strings and the observed registry-backed role/school result in this shape, then pass `--observed observation.json`:

```json
{
  "versions": {
    "minecraft": "1.20.1",
    "ars_nouveau": "4.12.7",
    "irons_spellbooks": "1.20.1-3.15.0",
    "ars_elemental": "0.6.8.0"
  },
  "glyphs": {
    "ars_elemental:glyph_life_link": {"roles": ["payload"], "schools": ["irons_spellbooks:blood"]},
    "ars_elemental:glyph_aquatic_filter": {"roles": ["filter"], "schools": ["ars_n_spells:generic"]}
  }
}
```

This is an example schema, not shipped runtime evidence. Obtain the observation from the actual registry/GameTest profile and `/ans schools`; never copy expected values into an observation and call it a test. A matching observation means that metadata fixture matches, not that native casts, damage, optional removal or client rendering have been certified.

Extend `AddonCompatGameTests` in the relevant loader: gate on the addon with `OptionalModGate`, assert actual glyph registration/type and analysis, serialize/decode a real spell containing those glyphs, and assert final resource/effect behavior on native casts. Use a distinct player per scenario and restore modified configuration/listeners in `finally`. Test mixed payload/filter spells with deliberately unequal school attributes; namespace collisions; missing addon followed by reinstall; and mapping reload. Record exact artifact hashes and executed/skipped counts with the final logs. Add the profile to blocking CI before advertising it as supported.

Run the kit's negative fixtures with `python -m unittest discover -s tools -p test_addon_adapter.py`. They reject version/loader mismatch, missing observed glyphs, namespace/path traversal, filter-to-payload misclassification, unsupported schema and overwriting an existing pack.
