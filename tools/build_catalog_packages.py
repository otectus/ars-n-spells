#!/usr/bin/env python3
"""Build every imported-glyph catalog package Ars 'n' Spells ships for one loader (3.4.1 review CATALOG-01).

Shared, unchanged, by the Forge 1.20.1 and NeoForge 1.21.1 builds (tools/verify_loader_parity.py checks
that). Each package is a directory catalogs/<name>/ in the loader's tree holding:

  package.json    {"name": ..., "mod_id": ..., "mod_version": ..., "envelope": 10, "namespaces": [...],
                   "inventory": "inventory.json", "adapters": "adapters.json", "description": "..."}
  inventory.json  the native spell inventory exported with the addon loaded (/ans glyph inventory export,
                  or the GameTest dump), trimmed to the package's namespaces
  adapters.json   the adapter assignments, with a reason for every spell (and the reason a spell is left
                  unsupported)

and is built by tools/glyph_catalog_pack.py into <out>/<mod_id>-<mod_version>.jar (a catalog-only mod) and
<out>/<name>.json (the same catalog as a loose config file), validated against the loader's own rules.

Usage: build_catalog_packages.py --loader forge|neoforge --catalogs DIR --out DIR [--only NAME ...]
"""
import argparse
import contextlib
import io
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
import glyph_catalog_pack  # noqa: E402

PACKAGE_FIELDS = {"name", "mod_id", "mod_version", "envelope", "namespaces", "inventory", "adapters", "description"}


def build(loader, catalogs, out, only=None):
    built = []
    for definition in sorted(Path(catalogs).glob("*/package.json")):
        package = glyph_catalog_pack.read_json(definition, "package")
        unknown = set(package) - PACKAGE_FIELDS
        if unknown:
            raise glyph_catalog_pack.PackError(f"{definition}: unknown field(s) {sorted(unknown)}")
        if only and package["name"] not in only:
            continue
        here = definition.parent
        argv = ["--inventory", str(here / package.get("inventory", "inventory.json")),
                "--adapters", str(here / package.get("adapters", "adapters.json")),
                "--name", package["name"], "--out", str(out), "--loader", loader,
                "--envelope", str(package.get("envelope", glyph_catalog_pack.ENVELOPE_FLOOR)),
                "--jar", "--mod-id", package["mod_id"], "--mod-version", package["mod_version"]]
        for namespace in package.get("namespaces", []):
            argv += ["--namespace", namespace]
        with contextlib.redirect_stdout(io.StringIO()) as printed:
            glyph_catalog_pack.main(argv)
        built.append((package["name"], printed.getvalue().strip().replace("\n", "; ")))
    return built


def main(argv):
    parser = argparse.ArgumentParser(prog="build_catalog_packages.py")
    parser.add_argument("--loader", choices=sorted(glyph_catalog_pack.FAMILIES), required=True)
    parser.add_argument("--catalogs", required=True, help="the loader's catalogs/ directory")
    parser.add_argument("--out", required=True)
    parser.add_argument("--only", action="append", help="build only these package names (repeatable)")
    args = parser.parse_args(argv)
    try:
        built = build(args.loader, args.catalogs, Path(args.out), args.only)
    except glyph_catalog_pack.PackError as refused:
        sys.exit(f"refused: {refused}")
    for name, summary in built:
        print(f"{name}: {summary}")
    if args.only and len(built) != len(set(args.only)):
        sys.exit(f"unknown package name(s): {sorted(set(args.only) - {name for name, _ in built})}")


if __name__ == "__main__":
    main(sys.argv[1:])
