# Reviewed pack-author presets

`tools/pack_presets.py` provides four server-config proposals. Every operation defaults to an exact before/after preview. It does not change saves, cast counts, item payloads or dependency versions. Cosmetic client presets are a separate feature.

| Preset | Pool and cross-cast policy | Source income | Forge missing alternate payment |
| --- | --- | --- | --- |
| legacy | Iron primary, flat rates, 1.25× premium | Existing setting retained | Explicit legacy-open |
| conservative | Separate pools, flat rates, 1.5× premium | Multiplier 1 per second | Refuse |
| cooperative | Hybrid shared pool, flat rates, 1× premium | Multiplier 5 per second | Native fallback if fully affordable |
| expert | Separate pools, percentage of native maxima, 2× premium | Multiplier 0.5 per second | Refuse |

All presets enable mana integration and use an equal normalized cross-cast split. They retain both directional exchange rates and all settings outside their listed changes. Source income is the chosen multiplier times the configured Ars-to-Iron rate per server second. Native casts in separate mode still pay only their own pool. NeoForge has no alternate LP/aura adapter, so its presets do not write Forge payment settings. Legacy-open explicitly allows an unresolved alternate payment to proceed; this compatibility choice appears in the diff and is never silently selected by another preset.

First let 3.3.0 or later migrate the server configuration: the tool accepts schema 2 and the schema 3 written by 3.3.5, and refuses anything else. Stop the server, then preview its actual generated config:

```bash
python tools/pack_presets.py --list
python tools/pack_presets.py --loader forge --preset conservative --config world/serverconfig/ars_n_spells-server.toml
```

Review the JSON diff. To apply that proposal, run the same command with the explicit `--apply` flag:

```bash
python tools/pack_presets.py --loader forge --preset conservative --config world/serverconfig/ars_n_spells-server.toml --apply
```

The tool preserves comments, unknown keys and all unrelated values. It parses the resulting complete TOML, verifies the semantic diff, rejects missing/ambiguous keys and unsupported schemas, checks the file has not changed concurrently, backs up the exact original bytes to a content-addressed `.bak`, and atomically replaces the config. Running the same preset twice makes no further changes. Restart through the loader's normal lifecycle so the server applies and synchronizes the new rules.

These are explicit starting values for pack design, not measured balance recommendations. Review the resource quote, progression caps, overlapping Mana Wells and optional resource behavior in the actual server profile before adopting a preset. `python -m unittest discover -s tools -p test_pack_presets.py` verifies all loader/preset combinations, idempotence, preservation, backup, stale-file refusal and unsupported schemas.
