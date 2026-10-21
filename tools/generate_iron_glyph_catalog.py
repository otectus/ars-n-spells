#!/usr/bin/env python3
"""Generate the imported-glyph startup catalog and coverage manifest for Ars 'n' Spells 3.4.0.

Inputs (all committed):
  docs/3.4.0/inventory/irons-*.json   loaded-registry inventories written by the GameTest
                                       GlyphCatalogInventoryGameTests in each pinned Iron's runtime
  tools/iron_glyph_adapters.json       the curated adapter assignment, one entry per spell
  an Iron's en_us.json                 English spell names for the Iron's-absent fallback

Outputs:
  src/main/resources/ars_n_spells/iron_glyph_catalog.json   the startup catalog (registry identity)
  docs/3.4.0/iron-glyph-coverage.json                       machine-readable coverage manifest
  docs/3.4.0/iron-glyph-coverage.md                         the same coverage as a readable table
  src/main/resources/assets/ars_n_spells/models/item/iron/  one item model per glyph variant, using
                                                            the shared ars_n_spells:imported_glyph
                                                            geometry and the spell's live icon path

Everything is sorted and the catalog digest is computed over a canonical serialization, so the
same inputs always produce byte-identical output. A spell present in the registry but missing
from the adapter table, or the other way round, is an error rather than a silent omission.

Usage: generate_iron_glyph_catalog.py <iron's en_us.json>
"""
import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
INVENTORIES = sorted((ROOT / "docs/3.4.0/inventory").glob("irons-*.json"))
ADAPTERS = ROOT / "tools/iron_glyph_adapters.json"
CATALOG_OUT = ROOT / "src/main/resources/ars_n_spells/iron_glyph_catalog.json"
COVERAGE_OUT = ROOT / "docs/3.4.0/iron-glyph-coverage.json"
MODELS_OUT = ROOT / "src/main/resources/assets/ars_n_spells/models/item/iron"
COVERAGE_MD = ROOT / "docs/3.4.0/iron-glyph-coverage.md"
# GameTests that cast, press or refuse a particular spell (ImportedGlyphGameTests unless noted).
EVIDENCE = {
    "irons_spellbooks:heal": "selfHeal, casterOnlyGlyph_withTouch, survival_importedLeg, training, augmentMatrix, nested, learning; payment suite",
    "irons_spellbooks:slow": "touchSlow, nativeCastInProgress",
    "irons_spellbooks:chain_lightning": "touchChainLightning, projectileChainLightning",
    "irons_spellbooks:firebolt": "touchFirebolt, selfFirebolt, serialization, familyMinMax, knowledgeBonus; GlyphPressGameTests",
    "irons_spellbooks:fireball": "longFireball; payment cancelledCharge",
    "irons_spellbooks:ray_of_siphoning": "touchRayOfSiphoning",
    "irons_spellbooks:teleport": "projectileTeleport, familyMinMax",
    "irons_spellbooks:summon_vex": "touchSummonVex, familyMinMax",
    "irons_spellbooks:lightning_bolt": "touchLightningBolt",
    "irons_spellbooks:abyssal_shroud": "learning_respectsIronsOwnLearningRequirement, sharedBook; Press enforcesIronsLearning",
    "irons_spellbooks:cloud_of_regeneration": "Press enforcesIronsLearningAndUpstreamDisables, behaviourReload",
}
DEFAULT_EVIDENCE = "registered at every level (catalogCoversEveryLoadedSpell)"
FAMILY_TEXT = {
    "caster": "Casts on the caster exactly as Iron's does; Self only.",
    "aimed": "Iron's own targeting receives what the Ars form hit (entity or block); Self aims at the caster.",
    "projectile": "The native projectile is launched at what the form hit; with Self, along the caster's aim.",
    "movement": "Moves the caster (never the target) to what the form hit; with Self, to the caster's aim.",
    "summon": "Summons, owned by the caster, appear where the form hit; with Self, where Iron's places them.",
}

TIER_BY_RARITY = {"COMMON": 1, "UNCOMMON": 2, "RARE": 2, "EPIC": 3, "LEGENDARY": 3}
FAMILIES = {"caster", "aimed", "projectile", "movement", "summon"}
# The four Ars Nouveau 4.12.7 forms. Addon forms are refused at composition (see GlyphFamily).
FORMS = {
    "caster": ["self"],
    "aimed": ["self", "touch", "projectile", "underfoot"],
    "projectile": ["self", "touch", "projectile", "underfoot"],
    "movement": ["self", "touch", "projectile", "underfoot"],
    "summon": ["self", "touch", "projectile", "underfoot"],
}


def canonical(obj):
    return json.dumps(obj, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    names = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    adapters = json.loads(ADAPTERS.read_text(encoding="utf-8"))["spells"]
    seen = {}
    versions = []
    for path in INVENTORIES:
        inventory = json.loads(path.read_text(encoding="utf-8"))
        version = inventory["irons_spellbooks"]
        versions.append(version)
        for spell in inventory["spells"]:
            if spell["none"] or spell["ans_proxy"]:
                continue
            previous = seen.get(spell["spell"])
            if previous is not None:
                for field in ("school", "cast_type", "max_level", "rarity_by_level", "requires_learning"):
                    if previous[field] != spell[field]:
                        sys.exit(f"{spell['spell']}: {field} differs between Iron's builds; the catalog cannot freeze it")
                previous["versions"].append(version)
            else:
                seen[spell["spell"]] = dict(spell, versions=[version])
    missing = sorted(set(seen) - set(adapters))
    extra = sorted(set(adapters) - set(seen))
    if missing or extra:
        sys.exit(f"adapter table mismatch: missing={missing} extra={extra}")

    spells = []
    coverage = []
    for spell_id in sorted(seen):
        spell = seen[spell_id]
        adapter = adapters[spell_id]
        family = adapter["adapter"]
        if family not in FAMILIES:
            sys.exit(f"{spell_id}: unknown adapter family {family}")
        namespace, path = spell_id.split(":", 1)
        display = names.get(f"spell.{namespace}.{path}")
        if not display:
            sys.exit(f"{spell_id}: no English name in the supplied language file")
        if spell["min_level"] != 1 or spell["max_level"] < 1 or len(spell["rarity_by_level"]) != spell["max_level"]:
            sys.exit(f"{spell_id}: unexpected level range")
        tiers = [TIER_BY_RARITY[r] for r in spell["rarity_by_level"]]
        entry = {
            "spell": spell_id,
            "name": display,
            "school": spell["school"],
            "cast_type": spell["cast_type"],
            "max_level": spell["max_level"],
            "rarity_by_level": spell["rarity_by_level"],
            "tier_by_level": tiers,
            "requires_learning": spell["requires_learning"],
            "enabled_by_default": spell["enabled"],
            "adapter": "ars_n_spells:" + family,
            "irons_versions": sorted(spell["versions"]),
        }
        spells.append(entry)
        coverage.append({
            "spell": spell_id,
            "levels": f"1-{spell['max_level']}",
            "school": spell["school"],
            "cast_type": spell["cast_type"],
            "adapter": "ars_n_spells:" + family,
            "supported_forms": FORMS[family],
            "augments": ["amplify", "dampen"] if spell["max_level"] > 1 else [],
            "prerequisites": ["learned in Iron's"] if spell["requires_learning"] else [],
            "status": "supported" if spell["enabled"] else "supported_disabled_by_default",
            "reason": adapter["reason"],
            "irons_versions": sorted(spell["versions"]),
        })

    body = {
        "schema_version": 1,
        "id_encoding": "ars_n_spells:iron/<spell namespace>/<spell path>/level_<n>",
        "irons_builds": sorted(versions),
        "spells": spells,
    }
    digest = hashlib.sha256(canonical(body).encode("utf-8")).hexdigest()
    catalog = {"catalog_version": "3.4.0-base-1", "digest": digest, **body}
    CATALOG_OUT.parent.mkdir(parents=True, exist_ok=True)
    CATALOG_OUT.write_text(json.dumps(catalog, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    COVERAGE_OUT.write_text(json.dumps({
        "catalog_version": catalog["catalog_version"], "catalog_digest": digest,
        "irons_builds": sorted(versions), "spells": coverage}, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    write_models(seen, spells)
    write_coverage_markdown(catalog, coverage, spells)
    variants = sum(s["max_level"] for s in spells)
    print(f"{len(spells)} spells, {variants} glyph variants, digest {digest[:16]}")


def write_coverage_markdown(catalog, coverage, spells):
    """The coverage manifest as a table, so a reader never needs the JSON."""
    names = {entry["spell"]: entry["name"] for entry in spells}
    variants = sum(entry["max_level"] for entry in spells)
    families = {}
    for row in coverage:
        family = row["adapter"].split(":", 1)[1]
        counts = families.setdefault(family, [0, 0])
        counts[0] += 1
        counts[1] += int(row["levels"].split("-")[1])
    lines = [
        "# Imported Iron's glyph coverage (3.4.0)",
        "",
        "Generated by `tools/generate_iron_glyph_catalog.py` with `iron-glyph-coverage.json` from the same run.",
        f"Catalog `{catalog['catalog_version']}`, identity digest `{catalog['digest']}`, built from the loaded",
        f"registries of Iron's {', '.join(catalog['irons_builds'])} (`docs/3.4.0/inventory/`).",
        "",
        f"**{len(coverage)} spells, {variants} glyphs (one per spell level).** Every player spell in both pinned",
        "Iron's builds is covered; the only registry entries left out are Iron's `none` placeholder and",
        "Ars 'n' Spells' own `ars_cross_*` proxy spells, which are not player spells.",
        "",
        "Glyph ids are `ars_n_spells:iron/<spell namespace>/<spell path>/level_<n>`. A level's Ars tier",
        "follows Iron's live rarity for that level (Common I; Uncommon and Rare II; Epic and Legendary III)",
        "unless a datapack overrides it. Amplify and Dampen raise or lower the cast level within the",
        "spell's range; a single-level spell takes neither. Iron's learning requirements, cooldowns,",
        "recasts, cast times and channels apply as natively.",
        "",
        "## Adapter families",
        "",
        "| Adapter | Spells | Glyphs | Forms | Behaviour |",
        "| --- | --- | --- | --- | --- |",
    ]
    for family in sorted(families):
        forms = ", ".join(FORMS[family])
        lines.append(f"| `ars_n_spells:{family}` | {families[family][0]} | {families[family][1]} | {forms} | {FAMILY_TEXT[family]} |")
    lines += [
        "",
        "Forms are the four Ars Nouveau 4.12.7 forms; addon forms are refused at composition.",
        "",
        "## Casters",
        "",
        "Imported glyphs run only in casts whose Ars caster is the player. Any other cast containing one",
        "is refused when Ars admits it: nothing in it runs and no player mana is charged.",
        "",
        "| Route | Status |",
        "| --- | --- |",
        "| Ars spell books held by the player | Supported; tested |",
        "| Ars 'n' Spells carriers: exported Iron's scrolls and inscribed items | Supported; exported scroll tested (`nested`) |",
        "| Ars spells bound into Iron's books (wheel, hotkey, staff) | Supported by design: instant glyphs run inside the outer cast, charged ones start when it ends; not exercised by an imported-glyph GameTest |",
        "| Delayed effects and projectiles of a player's cast | Supported for the cast's session lifetime (1,200 ticks); tested with Delay and Projectile |",
        "| Turrets, runes, Ars fake players and other non-player casters | Refused; Ars takes a turret's or rune's own Source before it asks the spell to cast, so that Source is spent. Not exercised by a GameTest |",
        "",
        "A shared or copied spell grants nothing: each caster needs the spell learned in Iron's where Iron's",
        "requires it (`sharedBook`). Ars's own rules for casting another player's book apply unchanged.",
        "",
        "## Spells",
        "",
        "| Spell | Name | School | Levels | Cast | Adapter | Forms | Augments | Requires | Status | Notes | Test evidence |",
        "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for row in coverage:
        status = "supported" if row["status"] == "supported" else "supported; disabled by default in Iron's"
        requires = "learning in Iron's" if row["prerequisites"] else ""
        school = row["school"].split(":", 1)[1]
        adapter = row["adapter"].split(":", 1)[1]
        forms = "self" if row["supported_forms"] == ["self"] else "all four"
        augments = ", ".join(row["augments"]) if row["augments"] else "none"
        evidence = EVIDENCE.get(row["spell"], DEFAULT_EVIDENCE)
        lines.append(f"| `{row['spell']}` | {names[row['spell']]} | {school} | {row['levels']} | {row['cast_type'].lower()} "
                     f"| {adapter} | {forms} | {augments} | {requires} | {status} | {row['reason']} | {evidence} |")
    COVERAGE_MD.write_text("\n".join(lines) + "\n", encoding="utf-8")


def icon_sprite(spell_id, icon):
    """Iron's names its icon as a texture path; the item model needs the block-atlas sprite id."""
    namespace, path = icon.split(":", 1)
    if not path.startswith("textures/") or not path.endswith(".png"):
        sys.exit(f"{spell_id}: unexpected icon path {icon}")
    return f"{namespace}:{path[len('textures/'):-len('.png')]}"


def write_models(seen, spells):
    """One small model per variant; stale variants from an older catalog are removed."""
    wanted = {}
    for entry in spells:
        spell_id = entry["spell"]
        namespace, path = spell_id.split(":", 1)
        sprite = icon_sprite(spell_id, seen[spell_id]["icon"])
        for level in range(1, entry["max_level"] + 1):
            model = MODELS_OUT / namespace / path / f"level_{level}.json"
            wanted[model] = {"parent": "forge:item/default", "loader": "ars_n_spells:imported_glyph",
                             "icon": sprite, "level": level}
    if MODELS_OUT.exists():
        for stale in MODELS_OUT.rglob("*.json"):
            if stale not in wanted:
                stale.unlink()
    for model, body in wanted.items():
        model.parent.mkdir(parents=True, exist_ok=True)
        model.write_text(json.dumps(body, indent=1) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
