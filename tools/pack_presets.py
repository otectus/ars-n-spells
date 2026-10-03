"""Preview or explicitly apply a reviewed 3.3.0 server-config preset (Python 3.11+)."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile
import tomllib

# Schema 3 (3.3.5) only added inscribed_ars_default_cooldown_ticks; every preset key is unchanged.
SUPPORTED_SCHEMAS = (2, 3)

PRESETS = {
    "legacy": {
        "description": "Classic shared Iron pool, flat exchange rates, and the shipped cross-cast premium. Existing rate, Source and saved progress values remain unchanged.",
        "values": {"mana_unification_mode": "iss_primary", "conversion_policy": "flat_legacy",
                   "cross_cast_cost_multiplier": 1.25, "dual_cost_ars_percentage": .5,
                   "dual_cost_iss_percentage": .5},
        "forge": {"payment_open_failure_policy": "legacy_open"},
    },
    "conservative": {
        "description": "Separate native pools, higher cross-cast premium, and reduced Source income; unresolved alternate payment refuses the cast.",
        "values": {"mana_unification_mode": "separate", "conversion_policy": "flat_legacy",
                   "cross_cast_cost_multiplier": 1.5, "dual_cost_ars_percentage": .5,
                   "dual_cost_iss_percentage": .5, "source_jar_synergy_multiplier": 1.0},
        "forge": {"payment_open_failure_policy": "refuse"},
    },
    "cooperative": {
        "description": "Shared hybrid pool with no cross-cast premium and the standard Source income; unresolved alternate payment falls back to a fully affordable native quote.",
        "values": {"mana_unification_mode": "hybrid", "conversion_policy": "flat_legacy",
                   "cross_cast_cost_multiplier": 1.0, "dual_cost_ars_percentage": .5,
                   "dual_cost_iss_percentage": .5, "source_jar_synergy_multiplier": 5.0},
        "forge": {"payment_open_failure_policy": "native_fallback"},
    },
    "expert": {
        "description": "Separate pools with percentage-of-native-maximum exchange, double cross-cast premium and low Source income; unresolved alternate payment refuses the cast.",
        "values": {"mana_unification_mode": "separate", "conversion_policy": "equal_percent",
                   "cross_cast_cost_multiplier": 2.0, "dual_cost_ars_percentage": .5,
                   "dual_cost_iss_percentage": .5, "source_jar_synergy_multiplier": .5},
        "forge": {"payment_open_failure_policy": "refuse"},
    },
}


def leaves(data: dict, path: tuple = ()) -> dict:
    result = {}
    for key, value in data.items():
        if isinstance(value, dict):
            nested = leaves(value, (*path, key))
            if result.keys() & nested.keys():
                raise ValueError("Ambiguous duplicate config key; use an unmodified generated 3.3.0 config")
            result.update(nested)
        else:
            if key in result:
                raise ValueError(f"Ambiguous duplicate key: {key}")
            result[key] = ((*path, key), value)
    return result


def preview(text: str, preset: str, loader: str) -> tuple[str, list[dict]]:
    if len(text.encode("utf-8")) > 2_000_000:
        raise ValueError("Config exceeds 2 MB; no data changed")
    values = leaves(tomllib.loads(text))
    if values.get("config_schema_version", (None, 0))[1] not in SUPPORTED_SCHEMAS:
        raise ValueError("Load/migrate this config with 3.3.0 or later first; only schemas 2 and 3 are supported")
    selected = {"enable_mana_unification": True, **PRESETS[preset]["values"], **(PRESETS[preset]["forge"] if loader == "forge" else {})}
    missing = selected.keys() - values.keys()
    if missing:
        raise ValueError("Missing generated keys; no data changed: " + ", ".join(sorted(missing)))
    changes = []
    output = text
    for key, after in selected.items():
        path, before = values[key]
        if before == after:
            continue
        # Only the documented scalar assignment is changed. Preserve all other text,
        # comments, unknown keys, loader-specific settings and saved schema values.
        pattern = re.compile(r"^(\s*" + re.escape(key) + r"\s*=\s*)([^\r\n#]*?)(\s*(?:#[^\r\n]*)?)$", re.MULTILINE)
        replacement = json.dumps(after, ensure_ascii=False)
        output, count = pattern.subn(lambda match: match[1] + replacement + match[3], output)
        if count != 1:
            raise ValueError(f"Unsupported/ambiguous TOML assignment for {key}; no data changed")
        changes.append({"key": ".".join(path), "before": before, "after": after})
    # Do not trust a text edit without parsing and checking its complete semantic diff.
    changed_values = leaves(tomllib.loads(output))
    for key, item in values.items():
        expected = selected.get(key, item[1])
        if changed_values.get(key, (None, None))[1] != expected:
            raise ValueError(f"Unexpected rewrite for {key}; no data changed")
    return output, changes


def apply(path: Path, original: bytes, updated: str) -> Path:
    if path.read_bytes() != original:
        raise ValueError("Config changed after preview; no data changed. Preview again")
    digest = hashlib.sha256(original).hexdigest()[:16]
    backup = path.with_name(path.name + f".ans-preset-{digest}.bak")
    if backup.exists() and backup.read_bytes() != original:
        raise ValueError("Backup collision; no data changed")
    if not backup.exists():
        with backup.open("xb") as target:
            target.write(original)
    descriptor, temporary = tempfile.mkstemp(prefix=".ans-preset-", suffix=".tmp", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="") as target:
            target.write(updated)
            target.flush()
            os.fsync(target.fileno())
        if path.read_bytes() != original:
            raise ValueError("Config changed before replacement; backup retained, config untouched")
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    return backup


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--list", action="store_true")
    parser.add_argument("--preset", choices=PRESETS)
    parser.add_argument("--loader", choices=("forge", "neoforge"))
    parser.add_argument("--config", type=Path)
    parser.add_argument("--apply", action="store_true", help="Explicitly back up and replace the file; stop the server first")
    args = parser.parse_args()
    if args.list:
        print(json.dumps(PRESETS, indent=2)); return
    if not (args.preset and args.loader and args.config):
        parser.error("--preset, --loader and --config are required; default operation is preview")
    try:
        original = args.config.read_bytes()
        updated, changes = preview(original.decode("utf-8"), args.preset, args.loader)
        result = {"preset": args.preset, "loader": args.loader, "description": PRESETS[args.preset]["description"],
                  "operation": "apply" if args.apply else "preview", "changes": changes}
        if args.apply and changes:
            result["backup"] = str(apply(args.config, original, updated))
        print(json.dumps(result, indent=2))
    except (OSError, ValueError) as error:
        parser.exit(1, f"Refused: {error}\n")


if __name__ == "__main__":
    main()
