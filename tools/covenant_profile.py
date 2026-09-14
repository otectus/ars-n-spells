#!/usr/bin/env python3
"""Inspect a bounded local Forge dependency set; write a hash lock only when complete.

This prepares a runtime profile, not gameplay evidence. FML enforces the recorded
upstream version ranges during startup. It never downloads or rewrites jars.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import tomllib
import zipfile

REQUIRED = {"covenant_of_the_seven", "bloodmagic", "enigmaticlegacy", "naturesaura", "caelus", "patchouli"}
PROVIDED = {"forge", "minecraft", "ars_nouveau", "irons_spellbooks", "curios", "geckolib", "playeranimator"}
MAX_JAR = 128 * 1024 * 1024


def inspect(directory: Path) -> dict:
    directory = directory.resolve(strict=True)
    jars = sorted(directory.glob("*.jar"))
    if not 1 <= len(jars) <= 16:
        raise ValueError("Supply 1..16 local jars, including Covenant, Blood Magic, Enigmatic Legacy, Nature's Aura, Caelus and Patchouli")
    if sum(p.stat().st_size for p in jars) > 512 * 1024 * 1024:
        raise ValueError("Dependency directory exceeds 512 MiB")
    records, versions, dependencies = [], {}, []
    for path in jars:
        if path.resolve().parent != directory or not re.fullmatch(r"[A-Za-z0-9_.+\-]+\.jar", path.name):
            raise ValueError("Jars must be direct children with ordinary artifact filenames")
        if not 0 < path.stat().st_size <= MAX_JAR:
            raise ValueError("Jar exceeds 128 MiB")
        with zipfile.ZipFile(path) as archive:
            info = archive.getinfo("META-INF/mods.toml")
            if info.file_size > 1024 * 1024:
                raise ValueError("Jar metadata exceeds 1 MiB")
            metadata = tomllib.loads(archive.read(info).decode("utf-8"))
            if metadata.get("modLoader") != "javafml":
                raise ValueError("Expected a Forge javafml mod: " + path.name)
            declared = {}
            for mod in metadata.get("mods", []):
                mod_id, version = mod["modId"], mod["version"]
                if version == "${file.jarVersion}":
                    manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
                    match = re.search(r"(?m)^Implementation-Version:\s*([^\r\n]+)", manifest)
                    version = match.group(1).strip() if match else ""
                if not re.fullmatch(r"[a-z][a-z0-9_]{1,63}", mod_id) or not isinstance(version, str) or not version or len(version) > 128 or "${" in version:
                    raise ValueError("Unresolved mod identity in " + path.name)
                if mod_id in versions or mod_id in PROVIDED:
                    raise ValueError("Duplicate/base dependency in local directory: " + mod_id)
                versions[mod_id] = version
                declared[mod_id] = version
            if not declared:
                raise ValueError("Jar has no declared mods")
            for owner, rows in metadata.get("dependencies", {}).items():
                for row in rows:
                    if row.get("mandatory", False):
                        dependencies.append({"owner": owner, "mod": row["modId"], "range": row.get("versionRange", "")})
            with path.open("rb") as source:
                digest = hashlib.file_digest(source, "sha256").hexdigest()
            records.append({"filename": path.name, "sha256": digest,
                            "bytes": path.stat().st_size, "mods": declared})
    missing = REQUIRED - versions.keys()
    missing |= {d["mod"] for d in dependencies} - versions.keys() - PROVIDED
    if missing:
        raise ValueError("Missing required local dependencies: " + ", ".join(sorted(missing)))
    return {"schema": 1, "loader": "forge", "minecraft": "1.20.1", "status": "prepared_runtime_unverified",
            "jars": records, "versions": versions, "required_dependencies": dependencies,
            "provided_by_gradle": sorted(PROVIDED)}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--write-lock", action="store_true", help="Explicitly replace covenant-profile.json after complete inspection")
    args = parser.parse_args()
    try:
        result = inspect(args.directory)
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(2, str(error) + "\n")
    encoded = json.dumps(result, indent=2) + "\n"
    if args.write_lock:
        (args.directory / "covenant-profile.json").write_text(encoded, encoding="utf-8")
    print(encoded)


if __name__ == "__main__":
    main()
