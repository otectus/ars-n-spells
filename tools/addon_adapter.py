"""Validate/build an ANS mapping adapter and compare it to an explicit runtime observation."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re

ID = re.compile(r"[a-z0-9_.-]+:[a-z0-9/._-]+\Z")
ROLES = {"payload", "filter", "augment", "cast_method", "control"}


def bounded_json(path: Path) -> dict:
    if path.stat().st_size > 262_144:
        raise ValueError("Adapter input exceeds 256 KiB")
    result = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(result, dict):
        raise ValueError("Adapter input must be a JSON object")
    return result


def validate(manifest: dict, loader: str) -> dict:
    if manifest.get("schema_version") != 1:
        raise ValueError("Unsupported adapter-kit schema")
    mod = manifest.get("mod_id", "")
    if not re.fullmatch(r"[a-z0-9_]+", mod):
        raise ValueError("Invalid mod_id")
    pins = manifest.get("profiles", {}).get(loader)
    if not isinstance(pins, dict) or not all(isinstance(v, str) and 0 < len(v) <= 128 for v in pins.values()):
        raise ValueError("Exact string version pins are required for this loader")
    if not {mod, "minecraft", "ars_nouveau", "irons_spellbooks"}.issubset(pins):
        raise ValueError("Profile must pin Minecraft, Ars, Iron's and the addon")
    if pins["minecraft"] != ("1.20.1" if loader == "forge" else "1.21.1"):
        raise ValueError("Minecraft version does not match loader adapter")
    fixtures = manifest.get("glyphs")
    if not isinstance(fixtures, dict) or not 1 <= len(fixtures) <= 256:
        raise ValueError("Declare 1..256 exact glyph fixtures")
    output = {}
    for glyph, definition in fixtures.items():
        if len(glyph) > 256 or not ID.fullmatch(glyph) or not glyph.startswith(mod + ":"):
            raise ValueError(f"Invalid addon glyph identity: {glyph}")
        if not isinstance(definition, dict):
            raise ValueError("Glyph fixture must declare roles and schools")
        roles, schools = definition.get("roles"), definition.get("schools")
        if not isinstance(roles, list) or not roles or len(roles) > 5 or any(not isinstance(role, str) or role not in ROLES for role in roles):
            raise ValueError(f"Invalid roles for {glyph}")
        if not isinstance(schools, list) or not 1 <= len(schools) <= 16 or any(
                not isinstance(s, str) or len(s) > 256 or not ID.fullmatch(s) for s in schools):
            raise ValueError(f"Invalid namespaced schools for {glyph}")
        if len(set(schools)) != len(schools):
            raise ValueError(f"Duplicate school membership for {glyph}")
        if "payload" not in roles and schools != ["ars_n_spells:generic"]:
            raise ValueError("Filter/control/augment/cast method fixtures must remain generic")
        output[glyph] = {"roles": roles, "schools": schools}
    return {"schema_version": 2, "priority": 20, "requires_mods": [mod, "irons_spellbooks"], "glyphs": output}


def compare(manifest: dict, loader: str, observed: dict) -> None:
    if not isinstance(observed.get("versions"), dict) or not isinstance(observed.get("glyphs"), dict):
        raise ValueError("Observation must contain versions and glyphs objects")
    expected = validate(manifest, loader)
    for mod, version in manifest["profiles"][loader].items():
        if observed.get("versions", {}).get(mod) != version:
            raise ValueError(f"Runtime version mismatch for {mod}; expected {version}")
    for glyph, definition in expected["glyphs"].items():
        if observed.get("glyphs", {}).get(glyph) != definition:
            raise ValueError(f"Runtime role/school fixture mismatch or missing registry glyph: {glyph}")


def build(manifest: dict, loader: str, target: Path) -> list[Path]:
    mapping = validate(manifest, loader)
    if target.exists() and any(target.iterdir()):
        raise ValueError("Output directory must be empty; existing pack files are never overwritten")
    target.mkdir(parents=True, exist_ok=True)
    metadata = {"pack": {"pack_format": 15 if loader == "forge" else 48,
                         "description": f"ANS {manifest['mod_id']} adapter fixtures"}}
    path = target / "data" / "ars_n_spells" / "ans_glyph_schools" / (manifest["mod_id"] + "_adapter.json")
    path.parent.mkdir(parents=True, exist_ok=True)
    outputs = [(target / "pack.mcmeta", metadata), (path, mapping)]
    for file, data in outputs:
        file.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    return [file for file, _ in outputs]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--loader", choices=("forge", "neoforge"), required=True)
    parser.add_argument("--observed", type=Path, help="Explicit runtime registry/version fixture; never inferred from installation")
    parser.add_argument("--output", type=Path, help="Create a new mapping datapack in an empty directory")
    args = parser.parse_args()
    try:
        manifest = bounded_json(args.manifest)
        mapping = validate(manifest, args.loader)
        if args.observed:
            compare(manifest, args.loader, bounded_json(args.observed))
        paths = build(manifest, args.loader, args.output) if args.output else []
        print(json.dumps({"loader": args.loader, "glyphs": len(mapping["glyphs"]),
            "evidence": "observed_fixture_matches" if args.observed else "schema_valid_runtime_unverified",
            "outputs": [str(path) for path in paths]}, indent=2))
    except (OSError, ValueError) as error:
        parser.exit(1, f"Refused: {error}\n")


if __name__ == "__main__":
    main()
