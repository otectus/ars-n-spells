#!/usr/bin/env python3
"""Build an imported-glyph catalog for addon spells: what a pack author ships for Ars 'n' Spells.

Shared, unchanged, by the Forge 1.20.1 and NeoForge 1.21.1 builds (tools/verify_loader_parity.py
checks that). It consumes an inventory of the loaded native spell registry exported in the pack
itself (/ans glyph inventory export), an adapter assignment written by the pack author, and
optionally the previous version of the same catalog, whose ids it never drops.

Outputs, in --out (published only once the whole package is built and has passed the target
loader's own schema rules and limits; nothing in --out changes when anything fails):
  <name>.json         the catalog manifest (schema 2), for config/ars_n_spells/glyph_catalogs/ or a jar
  <name>-report.txt   one line per spell: status, levels, adapter, reason
  <mod-id>-<version>.jar   with --jar: a catalog-only mod (lowcodefml) carrying the manifest, the glyph
                      item models and their names. Keep it installed for as long as any world has used
                      these glyphs, even after removing the addon itself: the ids live in it. (Ars 'n'
                      Spells also keeps a copy of every catalog it accepted, and supplies a model for any
                      identity that has none, so a loose manifest in the config folder works on its own.)

Identity rules (docs/3.4.1/identity-and-levels.md, docs/3.4.1/catalog-packages.md):
  * every spell reserves base levels up to --envelope (default 10, at most 99) and from its native
    minimum level; identities only ever grow, so a raised minimum keeps the lower levels registered
    (dormant) and a lowered one adds levels below;
  * a spell that left the inventory stays as "retired";
  * a spell with no adapter assigned is a placeholder: "unsupported" with adapter_version 0, registered
    and dormant, never castable. A later version of the same catalog may give it its first adapter
    contract (a promotion; every id stays). A published contract (adapter_version 1 or more) may only
    move to a newer version of the same adapter, never to another adapter.
Every id is registered whatever its status.

Usage:
  generate_iron_glyph_catalog.py pack --inventory INV.json --adapters ADAPTERS.json --name NAME --out DIR
      [--names LANG.json] [--loader forge|neoforge] [--previous OLD.json] [--envelope N] [--namespace NS ...]
      [--jar --mod-id ID --mod-version V]
"""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import sys
import tempfile
import zipfile

ENVELOPE_FLOOR = 10
MAX_LEVEL = 99
ID_ENCODING = "ars_n_spells:iron/<spell namespace>/<spell path>/level_<n>"
TIER_BY_RARITY = {"COMMON": 1, "UNCOMMON": 2, "RARE": 2, "EPIC": 3, "LEGENDARY": 3}
RARITIES = set(TIER_BY_RARITY)
CAST_TYPES = {"INSTANT", "LONG", "CONTINUOUS"}
STATUSES = {"supported", "unsupported", "retired"}
FAMILIES = {
    "forge": {"caster", "aimed", "projectile", "movement", "summon"},
    "neoforge": {"self", "projectile", "entity", "position", "block", "teleport", "summon", "channel"},
}
PLACEHOLDER_FAMILY = {"forge": "caster", "neoforge": "self"}
TARGETS = {"self", "entity", "entity_or_self", "block", "position", "any"}
# The adapter execution contract both builds implement; 0 is a placeholder that never had one.
ADAPTER_VERSION = 1
PLACEHOLDER_VERSION = 0
MAX_ADAPTER_VERSION = 1000
# Each loader's caps on an assembled catalog (ImportedGlyphCatalog.MAX_SPELLS / MAX_VARIANTS).
LIMITS = {"forge": (1024, 4096), "neoforge": (2048, 8192)}
MAX_SOURCE_BYTES = 2 * 1024 * 1024
ROMAN = ["", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"]
MARKED_LEVELS = 10
NAMESPACE = re.compile(r"[a-z0-9_.-]+\Z")
PATH = re.compile(r"[a-z0-9_./-]+\Z")
ROOT_FIELDS = {"schema_version", "catalog", "catalog_version", "id_encoding", "digest", "provenance", "source", "excluded",
               "irons_builds", "spells", "_comment"}
SPELL_FIELDS = {"spell", "name", "school", "cast_type", "levels", "rarity", "tier", "adapter", "adapter_version", "status", "reason",
                "requires_learning", "enabled_by_default", "irons_versions", "target", "final_effect_only", "icon"}
ASSIGNMENT_FIELDS = {"adapter", "adapter_version", "target", "reason", "status", "final_effect_only"}


class PackError(Exception):
    """The package cannot be built; the message names the input and field."""


def fail(message):
    raise PackError(message)


def loads(text, what):
    """Strict JSON: no NaN or Infinity, no duplicate keys; numbers kept exact for validation."""
    def constant(name):
        raise ValueError(f"{name} is not a JSON number")

    def unique(pairs):
        out = {}
        for key, value in pairs:
            if key in out:
                raise ValueError(f"duplicate key {key!r}")
            out[key] = value
        return out
    try:
        return json.loads(text, parse_constant=constant, object_pairs_hook=unique)
    except ValueError as invalid:
        fail(f"{what}: not valid JSON ({invalid})")


def read_json(path, what):
    try:
        text = Path(path).read_text(encoding="utf-8")
    except OSError as unreadable:
        fail(f"{what} {path}: {unreadable}")
    return loads(text, f"{what} {path}")


def whole(value, what, low, high):
    """A JSON number with no fractional part inside [low, high]; booleans and numeric strings are not numbers."""
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        fail(f"{what} must be a whole number from {low} to {high} (found {value!r})")
    if isinstance(value, float) and not value.is_integer():
        fail(f"{what} must be a whole number from {low} to {high} (found {value!r})")
    number = int(value)
    if number < low or number > high:
        fail(f"{what} must be a whole number from {low} to {high} (found {value!r})")
    return number


def text(value, what, limit=256):
    if not isinstance(value, str) or not value or len(value) > limit:
        fail(f"{what} must be a string of 1-{limit} characters (found {value!r})")
    return value


def flag(value, what):
    if not isinstance(value, bool):
        fail(f"{what} must be true or false (found {value!r})")
    return value


def namespaced(value, what):
    raw = text(value, what)
    namespace, _, path = raw.partition(":")
    if not namespace or not path or not NAMESPACE.match(namespace) or not PATH.match(path):
        fail(f"{what} must be a namespaced id such as addon:spell (found {raw!r})")
    return raw


def icon_sprite(icon):
    """The block-atlas sprite of an icon texture, or None when the icon is not a texture file."""
    if not icon:
        return None
    namespace, _, path = icon.partition(":")
    if not path.startswith("textures/") or not path.endswith(".png"):
        return None
    return f"{namespace}:{path[len('textures/'):-len('.png')]}"


def model(loader, spell_id, level, icon):
    """One glyph item model. Never names a texture that may be absent without a fallback for it."""
    namespace, path = spell_id.split(":", 1)
    if loader == "forge":
        # The imported_glyph geometry draws ANS's rune when the icon sprite is not in the atlas, and has a
        # level marker only for levels 1-10.
        sprite = icon_sprite(icon) or f"{namespace}:gui/spell_icons/{path}"
        return {"parent": "forge:item/default", "loader": "ars_n_spells:imported_glyph", "icon": sprite, "level": level}
    # The iron_glyph_icons atlas source supplies this sprite for every catalogued spell, from its icon or ANS's fallback.
    textures = {"layer0": f"ars_n_spells:item/iron_glyph_icon/{namespace}/{path}", "layer1": "ars_n_spells:item/iron_glyph/frame"}
    built = {"parent": "minecraft:item/generated", "textures": textures}
    if level <= MARKED_LEVELS:
        textures["layer2"] = f"ars_n_spells:item/iron_glyph/level_{level}"
    # The marker sprites ANS ships stop at X; a higher level shows the plain frame (and, dormant, the unmarked
    # dormant model) rather than a missing texture. The same rule as ImportedGlyphFallbackContent at run time.
    dormant = f"level_{level}" if level <= MARKED_LEVELS else "unmarked"
    built["overrides"] = [{"predicate": {"ars_n_spells:dormant": 1}, "model": f"ars_n_spells:item/glyph_iron_dormant/{dormant}"}]
    return built


def numeral(level):
    return ROMAN[level] if level < len(ROMAN) else str(level)


# ---- Validation: the target loader's own catalog rules (ImportedGlyphCatalog), applied before output ----

def validate_catalog(catalog, loader):
    """Raise PackError unless {@code catalog} is a manifest the {@code loader} build accepts whole."""
    if not isinstance(catalog, dict):
        fail("catalog: the document is not a JSON object")
    for field in catalog:
        if field not in ROOT_FIELDS:
            fail(f"catalog: unknown field {field!r}")
    if whole(catalog.get("schema_version"), "catalog 'schema_version'", 0, 2 ** 31 - 1) != 2:
        fail("catalog: schema_version must be 2")
    if catalog.get("id_encoding", ID_ENCODING) != ID_ENCODING:
        fail(f"catalog: unsupported id_encoding {catalog.get('id_encoding')!r}")
    spells = catalog.get("spells")
    if not isinstance(spells, list):
        fail("catalog: 'spells' must be a list")
    max_spells, max_variants = LIMITS[loader]
    if len(spells) > max_spells:
        fail(f"catalog: {len(spells)} spells, more than the {loader} build accepts ({max_spells})")
    seen, variants = set(), 0
    for index, entry in enumerate(spells):
        where = f"spells[{index}]"
        if not isinstance(entry, dict):
            fail(f"{where}: must be an object")
        spell = namespaced(entry.get("spell"), f"{where} 'spell'")
        where = spell
        if spell in seen:
            fail(f"{where}: duplicate spell")
        seen.add(spell)
        if spell.startswith("ars_n_spells:"):
            fail(f"{where}: ANS's own proxy spells are never imported")
        for field in entry:
            if field not in SPELL_FIELDS:
                fail(f"{where}: unknown field {field!r}")
        levels = entry.get("levels")
        if not isinstance(levels, dict) or set(levels) - {"min", "default_max", "registered_max"}:
            fail(f"{where}: 'levels' must be an object with min, default_max and registered_max")
        low = whole(levels.get("min", 1), f"{where} levels 'min'", 1, MAX_LEVEL)
        high = whole(levels.get("default_max"), f"{where} levels 'default_max'", low, MAX_LEVEL)
        registered = whole(levels.get("registered_max", high), f"{where} levels 'registered_max'", high, MAX_LEVEL)
        count = high - low + 1
        rarity, tier = entry.get("rarity"), entry.get("tier")
        if not isinstance(rarity, list) or len(rarity) != count or any(r not in RARITIES for r in rarity):
            fail(f"{where}: 'rarity' must list one of {sorted(RARITIES)} for each of the {count} default levels")
        if not isinstance(tier, list) or len(tier) != count:
            fail(f"{where}: 'tier' must list a tier for each of the {count} default levels")
        for value in tier:
            whole(value, f"{where} 'tier' entry", 1, 3)
        status = entry.get("status", "supported")
        if status not in STATUSES:
            fail(f"{where}: 'status' must be one of {sorted(STATUSES)}")
        version = whole(entry.get("adapter_version", 1), f"{where} 'adapter_version'", 0, MAX_ADAPTER_VERSION)
        if version == PLACEHOLDER_VERSION and status == "supported":
            fail(f"{where}: adapter_version 0 marks a placeholder without an adapter contract, so its status must be unsupported or retired")
        adapter = namespaced(entry.get("adapter"), f"{where} 'adapter'")
        if not adapter.startswith("ars_n_spells:") or adapter.split(":", 1)[1] not in FAMILIES[loader]:
            fail(f"{where}: adapter {adapter} is not one the {loader} build implements ({sorted(FAMILIES[loader])})")
        if entry.get("cast_type") not in CAST_TYPES:
            fail(f"{where}: 'cast_type' must be one of {sorted(CAST_TYPES)}")
        text(entry.get("name"), f"{where} 'name'")
        namespaced(entry.get("school"), f"{where} 'school'")
        for field in ("requires_learning", "enabled_by_default", "final_effect_only"):
            if field in entry:
                flag(entry[field], f"{where} '{field}'")
        if "target" in entry or loader == "neoforge":
            if entry.get("target") not in TARGETS:
                fail(f"{where}: 'target' must be one of {sorted(TARGETS)}")
        if "icon" in entry and icon_sprite(namespaced(entry["icon"], f"{where} 'icon'")) is None:
            fail(f"{where}: 'icon' must name a texture file such as addon:textures/gui/spell_icons/x.png")
        variants += registered - low + 1
    if variants > max_variants:
        fail(f"catalog: {variants} glyph identities, more than the {loader} build registers ({max_variants})")
    if len(json.dumps(catalog, indent=1, ensure_ascii=False).encode("utf-8")) > MAX_SOURCE_BYTES:
        fail(f"catalog: larger than the {MAX_SOURCE_BYTES} bytes a build reads")
    return variants


def identities(entry):
    namespace, path = entry["spell"].split(":", 1)
    levels = entry["levels"]
    return {f"ars_n_spells:iron/{namespace}/{path}/level_{level}" for level in range(levels["min"], levels["registered_max"] + 1)}


# ---- Inputs --------------------------------------------------------------------------------------

def upgrade_entry(entry):
    """A schema-1 catalog entry of either loader in the schema-2 shape (schema 1 had one contract, version 1)."""
    if "levels" in entry:
        return entry
    out = {k: v for k, v in entry.items() if k not in ("max_level", "min_level", "rarity_by_level", "tier_by_level", "default_tier")}
    low = entry.get("min_level", 1)
    out["levels"] = {"min": low, "default_max": entry["max_level"], "registered_max": entry["max_level"]}
    out["rarity"] = entry.get("rarity_by_level", entry.get("rarity", []))
    out["tier"] = entry.get("tier_by_level", entry.get("default_tier", []))
    out.setdefault("status", "supported")
    out.setdefault("adapter_version", 1)
    return out


def read_inventory(path):
    """Spell id to its validated inventory entry: levels min..max (max capped at 99) with a rarity per level."""
    inventory = read_json(path, "inventory")
    if not isinstance(inventory, dict) or not isinstance(inventory.get("spells"), list):
        fail(f"inventory {path}: must be an object with a 'spells' list")
    spells = {}
    for index, spell in enumerate(inventory["spells"]):
        where = f"inventory spells[{index}]"
        if not isinstance(spell, dict):
            fail(f"{where}: must be an object")
        if spell.get("none") is True or spell.get("ans_proxy") is True:
            continue
        spell_id = namespaced(spell.get("spell"), f"{where} 'spell'")
        where = f"inventory {spell_id}"
        if spell_id in spells:
            fail(f"{where}: listed twice")
        low = whole(spell.get("min_level", 1), f"{where} 'min_level'", 1, MAX_LEVEL)
        native_max = whole(spell.get("max_level"), f"{where} 'max_level'", low, 2 ** 31 - 1)
        high = min(native_max, MAX_LEVEL)
        rarities = spell.get("rarity_by_level", spell.get("rarity"))
        if not isinstance(rarities, list) or any(r not in RARITIES for r in rarities):
            fail(f"{where}: 'rarity_by_level' must list Iron's rarities ({sorted(RARITIES)})")
        if len(rarities) < high - low + 1:
            fail(f"{where}: lists {len(rarities)} rarities for levels {low}-{high}; the export lists one per level from the minimum")
        icon = spell.get("icon")
        if icon is not None and icon_sprite(namespaced(icon, f"{where} 'icon'")) is None:
            icon = None
        spells[spell_id] = {"spell": spell_id, "name": spell.get("name"), "school": namespaced(spell.get("school"), f"{where} 'school'"),
                            "cast_type": spell.get("cast_type"), "min": low, "max": high, "native_max": native_max,
                            "rarity": rarities[: high - low + 1], "requires_learning": flag(spell.get("requires_learning"), f"{where} 'requires_learning'"),
                            "enabled": flag(spell.get("enabled"), f"{where} 'enabled'"), "icon": icon}
        if spells[spell_id]["cast_type"] not in CAST_TYPES:
            fail(f"{where}: 'cast_type' must be one of {sorted(CAST_TYPES)}")
    return inventory, spells


def read_adapters(path, loader):
    root = read_json(path, "adapters")
    if not isinstance(root, dict) or not isinstance(root.get("spells"), dict) or set(root) - {"spells", "_comment"}:
        fail(f'adapters {path}: must be {{"spells": {{"<spell id>": {{"adapter": "..."}}}}}} (and an optional "_comment")')
    out = {}
    for spell_id, assignment in root["spells"].items():
        where = f"adapters {spell_id}"
        namespaced(spell_id, where)
        if not isinstance(assignment, dict):
            fail(f"{where}: must be an object")
        for field in assignment:
            if field not in ASSIGNMENT_FIELDS:
                fail(f"{where}: unknown field {field!r} (one of {sorted(ASSIGNMENT_FIELDS)})")
        status = assignment.get("status", "supported")
        if status not in STATUSES:
            fail(f"{where}: 'status' must be one of {sorted(STATUSES)}")
        if "adapter" not in assignment:
            # No adapter: a placeholder, with the reason it is not castable (an unsupported verdict worth recording).
            if status == "supported" or "reason" not in assignment or set(assignment) - {"status", "reason"}:
                fail(f"{where}: without an adapter, give only status unsupported (or retired) and the reason it is not castable")
            out[spell_id] = {"family": None, "version": PLACEHOLDER_VERSION, "status": status, "target": "self",
                             "final_effect_only": False, "reason": text(assignment["reason"], f"{where} 'reason'", 512)}
            continue
        family = text(assignment.get("adapter"), f"{where} 'adapter'").split(":", 1)[-1]
        if family not in FAMILIES[loader]:
            fail(f"{where}: adapter {family} is not one the {loader} build implements ({sorted(FAMILIES[loader])})")
        # A contract this tool does not know cannot be packaged: ADAPTER_VERSION is the one both builds implement.
        version = whole(assignment.get("adapter_version", ADAPTER_VERSION), f"{where} 'adapter_version'", 1, ADAPTER_VERSION)
        target = assignment.get("target", "self")
        if loader == "neoforge" and target not in TARGETS:
            fail(f"{where}: target {target} is not one of {sorted(TARGETS)}")
        out[spell_id] = {"family": family, "version": version, "status": status, "target": target,
                         "final_effect_only": flag(assignment.get("final_effect_only", False), f"{where} 'final_effect_only'"),
                         "reason": text(assignment["reason"], f"{where} 'reason'", 512) if "reason" in assignment else ""}
    return out


# ---- Building -----------------------------------------------------------------------------------

def rarity_at(entry, level):
    """A previous entry's rarity at {@code level}: its default levels' list, Legendary for reserved levels."""
    levels = entry["levels"]
    index = level - levels["min"]
    return entry["rarity"][index] if 0 <= index < len(entry["rarity"]) else "LEGENDARY"


def build(args):
    if not 1 <= args.envelope <= MAX_LEVEL:
        fail(f"--envelope must be 1-{MAX_LEVEL} (glyph ids encode at most level {MAX_LEVEL})")
    loader = args.loader
    inventory, candidates = read_inventory(args.inventory)
    adapters = read_adapters(args.adapters, loader)
    names = read_json(args.names, "names") if args.names else {}
    if not isinstance(names, dict):
        fail(f"names {args.names}: must be a language file object")
    previous = {}
    if args.previous:
        old_catalog = read_json(args.previous, "previous catalog")
        if not isinstance(old_catalog, dict) or not isinstance(old_catalog.get("spells"), list):
            fail(f"previous catalog {args.previous}: must be a catalog with a 'spells' list")
        for entry in old_catalog["spells"]:
            upgraded = upgrade_entry(entry)
            previous[upgraded["spell"]] = upgraded
        validate_catalog(dict(old_catalog, schema_version=2, spells=list(previous.values())), loader)
    if args.namespace:
        candidates = {k: v for k, v in candidates.items() if k.split(":", 1)[0] in args.namespace}
    spells, report = [], []
    for spell_id in sorted(set(candidates) | set(previous)):
        old = previous.get(spell_id)
        if spell_id not in candidates:
            spells.append(dict(old, status="retired"))
            report.append(f"{spell_id}: retired (no longer in the inventory; its ids stay registered)")
            continue
        spell = candidates[spell_id]
        assignment = adapters.get(spell_id)
        reason = ""
        old_placeholder = old is not None and old.get("adapter_version", 1) == PLACEHOLDER_VERSION
        if assignment is not None and assignment["family"] is None:
            # An explicit unsupported verdict: a placeholder like an unassigned spell, with its own reason.
            if old is not None and not old_placeholder:
                fail(f"{spell_id}: was published with {old['adapter']} v{old.get('adapter_version', 1)}; a published contract "
                     "cannot become a placeholder (assign its adapter with status unsupported instead)")
            family = (old or {}).get("adapter", "ars_n_spells:" + PLACEHOLDER_FAMILY[loader]).split(":", 1)[1]
            version, status, reason = PLACEHOLDER_VERSION, assignment["status"], assignment["reason"]
            target, final_only = (old or {}).get("target", "self"), bool((old or {}).get("final_effect_only", False))
        elif assignment is None:
            if old is not None and not old_placeholder:
                # A published contract keeps its adapter; without an assignment it is only marked unsupported.
                family, version, status = old["adapter"].split(":", 1)[1], old["adapter_version"], "unsupported"
                reason = "no adapter assigned in this version; its published adapter contract is kept"
            else:
                family = (old or {}).get("adapter", "ars_n_spells:" + PLACEHOLDER_FAMILY[loader]).split(":", 1)[1]
                version, status, reason = PLACEHOLDER_VERSION, "unsupported", "no adapter assigned"
            target, final_only = (old or {}).get("target", "self"), bool((old or {}).get("final_effect_only", False))
        else:
            family, version, status = assignment["family"], assignment["version"], assignment["status"]
            target, final_only, reason = assignment["target"], assignment["final_effect_only"], assignment["reason"]
        entry_adapter = "ars_n_spells:" + family
        transition = ""
        if old is not None and not old_placeholder:
            old_family = old["adapter"]
            same = old_family == entry_adapter and (loader != "neoforge" or old.get("target") == target) \
                and bool(old.get("final_effect_only", False)) == final_only
            if not same:
                fail(f"{spell_id}: was published with {old_family} v{old.get('adapter_version', 1)}"
                     + (f" (target {old.get('target')})" if loader == "neoforge" else "")
                     + "; an adapter contract cannot be replaced once published (only moved to a newer version of the same adapter)")
            if version < old.get("adapter_version", 1):
                fail(f"{spell_id}: was published with adapter contract v{old['adapter_version']}; it cannot go back to v{version}")
            if version > old.get("adapter_version", 1):
                transition = f"; adapter contract v{old['adapter_version']} -> v{version}"
        elif old_placeholder and version != PLACEHOLDER_VERSION:
            transition = "; promoted from a placeholder (every id kept)"
        namespace, path = spell_id.split(":", 1)
        display = names.get(f"spell.{namespace}.{path}") or spell.get("name") or path.replace("_", " ").title()
        text(display, f"{spell_id} name")
        # Identities only grow: a raised native minimum keeps the lower levels, a lowered one adds levels below.
        old_levels = (old or {}).get("levels", {})
        low = min(spell["min"], old_levels.get("min", spell["min"]))
        default_max = max(low, spell["max"])
        registered = max(default_max, args.envelope, old_levels.get("registered_max", 0))
        rarities = [rarity_at(old, level) if level < spell["min"] else spell["rarity"][level - spell["min"]]
                    for level in range(low, default_max + 1)]
        entry = {
            "spell": spell_id, "status": status, "name": display, "school": spell["school"], "cast_type": spell["cast_type"],
            "levels": {"min": low, "default_max": default_max, "registered_max": registered},
            "rarity": rarities, "tier": [TIER_BY_RARITY[r] for r in rarities],
            "requires_learning": spell["requires_learning"], "enabled_by_default": spell["enabled"],
            "adapter": entry_adapter, "adapter_version": version,
        }
        if loader == "neoforge":
            entry["target"] = target
            entry["final_effect_only"] = final_only
        if spell["icon"]:
            entry["icon"] = spell["icon"]
        if reason:
            entry["reason"] = reason
        spells.append(entry)
        notes = []
        if spell["native_max"] > MAX_LEVEL:
            notes.append(f"native maximum {spell['native_max']} is above the id encoding; levels above {MAX_LEVEL} have no glyph")
        if old_levels and spell["min"] > old_levels.get("min", 1):
            notes.append(f"native minimum rose to {spell['min']}; levels {low}-{spell['min'] - 1} stay registered (dormant)")
        report.append(f"{spell_id}: {status}, levels {low}-{default_max}, registered {low}-{registered}, adapter {family} v{version}"
                      + (f" ({reason})" if reason else "") + transition + "".join("; " + n for n in notes))
    catalog = {"schema_version": 2, "catalog": args.name, "catalog_version": args.mod_version, "id_encoding": ID_ENCODING,
               "provenance": {"generator": "tools/generate_iron_glyph_catalog.py pack", "inventory": Path(args.inventory).name,
                              "native": inventory.get("irons_spellbooks") or inventory.get("mod_version", "")},
               "spells": spells}
    validate_catalog(catalog, loader)
    built = {entry["spell"]: entry for entry in spells}
    for spell_id, old in previous.items():
        lost = identities(old) - identities(built[spell_id])
        if lost:
            fail(f"{spell_id}: would lose {len(lost)} published identities (first {sorted(lost)[0]})")
    return catalog, report, candidates


def write_jar(path, args, catalog, manifest_text, candidates):
    forge = args.loader == "forge"
    toml = "\n".join([
        'modLoader="lowcodefml"', 'loaderVersion="[47,)"' if forge else 'loaderVersion="[1,)"', 'license="All rights reserved"', "",
        "[[mods]]", f'modId="{args.mod_id}"', f'version="{args.mod_version}"', f'displayName="{args.name} glyph identities"',
        "description=\"An Ars 'n' Spells imported-glyph catalog. Keep it installed for as long as any world has used these glyphs.\"",
        "", f"[[dependencies.{args.mod_id}]]", 'modId="ars_n_spells"', "mandatory=true" if forge else 'type="required"',
        'versionRange="[3.4.1,)"', 'ordering="NONE"', 'side="BOTH"', ""])
    prefix = "iron" if forge else "glyph_iron"
    lang = {}
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("META-INF/mods.toml" if forge else "META-INF/neoforge.mods.toml", toml)
        archive.writestr(f"ans_glyph_catalog/{args.name}.json", manifest_text)
        archive.writestr("pack.mcmeta", json.dumps({"pack": {"description": f"{args.name} glyph identities", "pack_format": 15 if forge else 34}}))
        for entry in catalog["spells"]:
            namespace, spell_path = entry["spell"].split(":", 1)
            icon = entry.get("icon") or candidates.get(entry["spell"], {}).get("icon") or ""
            for level in range(entry["levels"]["min"], entry["levels"]["registered_max"] + 1):
                archive.writestr(f"assets/ars_n_spells/models/item/{prefix}/{namespace}/{spell_path}/level_{level}.json",
                                 json.dumps(model(args.loader, entry["spell"], level, icon), indent=1) + "\n")
                lang[f"ars_n_spells.glyph_name.iron/{namespace}/{spell_path}/level_{level}"] = f"{entry['name']} {numeral(level)}"
        archive.writestr("assets/ars_n_spells/lang/en_us.json", json.dumps(lang, indent=1, ensure_ascii=False) + "\n")
    # Read the jar back: the manifest it carries must be the validated one, and every identity must have a model.
    with zipfile.ZipFile(path) as archive:
        if archive.testzip() is not None:
            fail(f"{path}: the written jar is corrupt")
        carried = loads(archive.read(f"ans_glyph_catalog/{args.name}.json").decode("utf-8"), "jar manifest")
        validate_catalog(carried, args.loader)
        names = set(archive.namelist())
        for entry in carried["spells"]:
            namespace, spell_path = entry["spell"].split(":", 1)
            for level in range(entry["levels"]["min"], entry["levels"]["registered_max"] + 1):
                if f"assets/ars_n_spells/models/item/{prefix}/{namespace}/{spell_path}/level_{level}.json" not in names:
                    fail(f"{path}: no model for {entry['spell']} level {level}")


def main(argv):
    parser = argparse.ArgumentParser(prog="generate_iron_glyph_catalog.py pack")
    parser.add_argument("--inventory", required=True, help="inventory exported by /ans glyph inventory export")
    parser.add_argument("--adapters", required=True,
                        help='{"spells": {"<id>": {"adapter": "...", "adapter_version": 1, "target": "...", "reason": "..."}}}')
    parser.add_argument("--names", help="an en_us.json with the addon's spell names (else the inventory's names)")
    parser.add_argument("--name", required=True, help="catalog name; the file is ans_glyph_catalog/<name>.json")
    parser.add_argument("--out", required=True, help="output directory")
    parser.add_argument("--loader", choices=sorted(FAMILIES), default="forge")
    parser.add_argument("--previous", help="the previous version of this catalog; none of its ids may be lost")
    parser.add_argument("--envelope", type=int, default=ENVELOPE_FLOOR, help=f"reserve base levels up to this (1-{MAX_LEVEL})")
    parser.add_argument("--namespace", action="append", help="only catalogue spells of these namespaces (repeatable)")
    parser.add_argument("--jar", action="store_true", help="also write a catalog-only mod jar (lowcodefml)")
    parser.add_argument("--mod-id", help="mod id of the catalog-only jar")
    parser.add_argument("--mod-version", default="1.0.0")
    args = parser.parse_args(argv)
    if not re.fullmatch(r"[a-z0-9_.-]{1,64}", args.name):
        sys.exit("--name must be 1-64 characters of a-z, 0-9, _, . or -")
    if args.jar and not (args.mod_id and re.fullmatch(r"[a-z][a-z0-9_]{1,63}", args.mod_id)):
        sys.exit("--jar needs --mod-id (a mod id: a-z, 0-9 and _, starting with a letter)")
    try:
        catalog, report, candidates = build(args)
    except PackError as refused:
        sys.exit(f"refused, nothing written: {refused}")
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    manifest_text = json.dumps(catalog, indent=1, ensure_ascii=False) + "\n"
    final = {f"{args.name}.json": manifest_text, f"{args.name}-report.txt": "\n".join(report) + "\n"}
    # Build the whole package in a private directory next to the output, check it, then publish each file
    # with an atomic rename: a reader never sees a partial file and a failure leaves --out as it was.
    staging = Path(tempfile.mkdtemp(prefix=f".{args.name}-", dir=out))
    try:
        for name, content in final.items():
            (staging / name).write_text(content, encoding="utf-8")
        validate_catalog(loads((staging / f"{args.name}.json").read_text(encoding="utf-8"), "written manifest"), args.loader)
        jar_name = f"{args.mod_id}-{args.mod_version}.jar" if args.jar else None
        if jar_name:
            write_jar(staging / jar_name, args, catalog, manifest_text, candidates)
        for name in list(final) + ([jar_name] if jar_name else []):
            os.replace(staging / name, out / name)
    except PackError as refused:
        sys.exit(f"refused, nothing written: {refused}")
    finally:
        shutil.rmtree(staging, ignore_errors=True)
    print(f"{len(catalog['spells'])} spells, {sum(len(identities(e)) for e in catalog['spells'])} ids -> {out / (args.name + '.json')}")
    if args.jar:
        print(f"catalog-only jar -> {out / jar_name}")


if __name__ == "__main__":
    main(sys.argv[1:])
