#!/usr/bin/env python3
"""Regenerate the shipped imported-glyph catalog from a runtime inventory of Iron's spells.

Inputs
  An inventory JSON written by the GameTest server:
    ./gradlew runGameTestServer -PwithIronsRuntimeGameTests -PglyphCatalogDump=/tmp/iron_inventory.json
  (IronGlyphCatalogGameTests writes it; GameTestRuntimeSetup builds Iron's spell config first, so
  maximum levels, schools and rarities are Iron's defaults, as on a real server.)

Outputs (all committed)
  src/main/resources/ans_glyph_catalog/irons_spellbooks.json   startup identity catalog (read in the mod constructor)
  src/main/resources/assets/ars_n_spells/lang/en_us.json        glyph name keys merged in (one per level variant)
  docs/3.4.0/iron-glyph-coverage.md                             human-readable coverage table
  docs/3.4.0/iron-glyph-coverage.json                           machine-readable manifest

The classification table below is the reviewed decision for every registered base spell. A spell
present in the inventory but missing here fails the run: a new base spell can never be silently
dropped. Classification is by what the native implementation reads for its target (verified in
Iron's 1.21.1-3.16.3 source): cast data from pre-cast targeting, the caster's RaycastBuilder pick,
Utils.getTargetBlock, or the caster's own position.

Usage
  python3 tools/generate_iron_glyph_catalog.py /tmp/iron_inventory.json
"""
import collections
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CATALOG = ROOT / "src/main/resources/ans_glyph_catalog/irons_spellbooks.json"
LANG = ROOT / "src/main/resources/assets/ars_n_spells/lang/en_us.json"
DOC_MD = ROOT / "docs/3.4.0/iron-glyph-coverage.md"
DOC_JSON = ROOT / "docs/3.4.0/iron-glyph-coverage.json"

SELF = "ars_n_spells:self"
PROJ = "ars_n_spells:projectile"
ENT = "ars_n_spells:entity"
POS = "ars_n_spells:position"
BLOCK = "ars_n_spells:block"
TP = "ars_n_spells:teleport"
SUMMON = "ars_n_spells:summon"
CHAN = "ars_n_spells:channel"

# spell path -> (adapter, target, note)
# target: self | entity | entity_or_self | block | position | any
CLASSIFICATION = {
    "abyssal_shroud": (SELF, "self", "Buff on the caster."),
    "acid_orb": (PROJ, "any", "Acid orb projectile; rend on impact."),
    "acupuncture": (ENT, "entity", "Needles converge on the living target."),
    "angel_wing": (SELF, "self", "Flight buff on the caster."),
    "arcane_shackle": (PROJ, "any", "Shackle projectile."),
    "arrow_volley": (POS, "position", "Volley falls on the target point."),
    "ascension": (SELF, "self", "Strikes around and lifts the caster."),
    "ball_lightning": (PROJ, "any", "Ball lightning projectile."),
    "black_hole": (POS, "position", "Black hole at the target point."),
    "blaze_storm": (CHAN, "self", "Channel: fireballs along the caster's aim."),
    "blessing_of_life": (ENT, "entity", "Heals the living target."),
    "blight": (ENT, "entity", "Blight on the living target."),
    "blizzard": (POS, "position", "Blizzard at the target point."),
    "blood_needles": (POS, "position", "Needles from around the caster converge on the target point."),
    "blood_slash": (PROJ, "any", "Slash projectile."),
    "blood_step": (TP, "position", "Moves the caster to a safe point at the target; never moves the target."),
    "burning_dash": (SELF, "self", "Dash along the caster's aim."),
    "chain_creeper": (POS, "position", "Creeper ring at the target point."),
    "chain_lightning": (ENT, "entity", "Chain lightning from the living target."),
    "charge": (SELF, "self", "Buff on the caster."),
    "cleanse": (SELF, "self", "Cleanses around the caster."),
    "cloud_of_regeneration": (CHAN, "self", "Channel: heals around the caster each pulse. Disabled in Iron's default config."),
    "cone_of_cold": (CHAN, "self", "Channel: cone follows the caster's aim."),
    "counterspell": (POS, "position", "Anti-magic ray to the target; only anti-magic-susceptible targets are affected."),
    "devour": (ENT, "entity", "Devour jaws on the living target."),
    "divine_smite": (SELF, "self", "Smite in front of the caster."),
    "dragon_breath": (CHAN, "self", "Channel: cone follows the caster's aim."),
    "earthquake": (POS, "position", "Earthquake at the target point."),
    "echoing_strikes": (SELF, "self", "Buff on the caster."),
    "eldritch_blast": (POS, "position", "Beam to the target point; native recasts are shared with Iron's."),
    "electrocute": (CHAN, "self", "Channel: cone follows the caster's aim."),
    "evasion": (SELF, "self", "Buff on the caster."),
    "fang_strike": (SELF, "self", "Fang line along the caster's facing."),
    "fang_swirl": (POS, "position", "Fang swirl at the target point."),
    "fang_ward": (SELF, "self", "Fang rings around the caster."),
    "fire_arrow": (PROJ, "any", "Exploding arrow projectile."),
    "fire_breath": (CHAN, "self", "Channel: cone follows the caster's aim."),
    "fireball": (PROJ, "any", "Exploding fireball projectile."),
    "firebolt": (PROJ, "any", "Firebolt projectile."),
    "firecracker": (POS, "position", "Firework bursts at the target point."),
    "firefly_swarm": (POS, "position", "Swarm at the target point; homes on a living target."),
    "flaming_barrage": (SELF, "self", "Homing fireballs from the caster; native recasts are shared with Iron's."),
    "flaming_strike": (SELF, "self", "Strike in front of the caster."),
    "fortify": (SELF, "self", "Absorption to allies around the caster."),
    "frost_step": (TP, "position", "Moves the caster to a safe point at the target; leaves a frozen decoy."),
    "frostbite": (SELF, "self", "Buff on the caster."),
    "frostwave": (SELF, "self", "Wave around the caster."),
    "gluttony": (SELF, "self", "Buff on the caster."),
    "gravity_fissure": (SELF, "self", "Fissure travels from the caster along the aim."),
    "greater_heal": (SELF, "self", "Heals the caster fully."),
    "guiding_bolt": (PROJ, "any", "Guiding bolt projectile."),
    "gust": (SELF, "self", "Gust in front of the caster."),
    "haste": (ENT, "entity_or_self", "Haste on the living target, or on the caster with Self (as Iron's falls back)."),
    "heal": (SELF, "self", "Heals the caster."),
    "healing_circle": (POS, "position", "Healing area at the target point."),
    "heartstop": (SELF, "self", "Buff on the caster."),
    "heat_surge": (SELF, "self", "Surge around the caster."),
    "ice_block": (POS, "position", "Ice block falls on the target point or living target."),
    "ice_spikes": (SELF, "self", "Spike line along the caster's facing."),
    "ice_tomb": (SELF, "self", "Encases the caster."),
    "icicle": (PROJ, "any", "Icicle projectile."),
    "invisibility": (SELF, "self", "Buff on the caster."),
    "lightning_bolt": (POS, "position", "Lightning strikes the target point."),
    "lightning_lance": (PROJ, "any", "Lance projectile."),
    "lob_creeper": (PROJ, "any", "Creeper-head projectile."),
    "magic_arrow": (PROJ, "any", "Piercing arrow projectile."),
    "magic_missile": (PROJ, "any", "Missile projectile."),
    "magma_bomb": (PROJ, "any", "Magma bomb projectile."),
    "oakskin": (SELF, "self", "Buff on the caster."),
    "planar_sight": (SELF, "self", "Buff on the caster."),
    "pocket_dimension": (SELF, "self", "Moves the caster into the pocket dimension; must be the last effect of a spell."),
    "poison_arrow": (PROJ, "any", "Poison arrow projectile."),
    "poison_breath": (CHAN, "self", "Channel: cone follows the caster's aim."),
    "poison_splash": (POS, "position", "Splash at the target point."),
    "portal": (POS, "position", "Portal at the target point; the next use opens the paired exit (native recasts)."),
    "raise_dead": (SUMMON, "position", "Undead appear at the target point; native recast dismisses them."),
    "raise_hell": (SELF, "self", "Eruptions in front of the caster; native recasts are shared with Iron's."),
    "ray_of_frost": (POS, "position", "Ray from the caster to the target point."),
    "ray_of_siphoning": (CHAN, "position", "Channel: ray from the caster to the target each pulse."),
    "recall": (SELF, "self", "Returns the caster to their respawn point; must be the last effect of a spell."),
    "root": (ENT, "entity", "Roots the living target."),
    "sacrifice": (ENT, "entity", "Detonates the caster's own summon."),
    "scapegoat": (SELF, "self", "Scapegoat walks out along the caster's aim."),
    "scorch": (POS, "position", "Scorched area at the target point."),
    "sculk_tentacles": (POS, "position", "Tentacles at the target point."),
    "shadow_slash": (SELF, "self", "Slash and dash along the caster's aim."),
    "shield": (POS, "position", "Shield wall at the target point."),
    "shockwave": (SELF, "self", "Shockwave around the caster."),
    "slow": (ENT, "entity", "Slows the living target."),
    "snowball": (PROJ, "any", "Snowball projectile."),
    "sonic_boom": (SELF, "self", "Boom along the caster's aim."),
    "spectral_hammer": (BLOCK, "block", "Mines the target block area; requires a block in Iron's mineable tag."),
    "spider_aspect": (SELF, "self", "Buff on the caster."),
    "starfall": (CHAN, "position", "Channel: comets fall around the target point."),
    "stomp": (SELF, "self", "Stomp in front of the caster."),
    "summon_ender_chest": (SELF, "self", "Opens the caster's ender chest."),
    "summon_horse": (SUMMON, "position", "Horse appears at the target point; native recast dismisses it."),
    "summon_polar_bear": (SUMMON, "position", "Bear appears at the target point; native recast dismisses it."),
    "summon_swords": (SUMMON, "position", "Swords appear at the target point; native recast dismisses them."),
    "summon_vex": (SUMMON, "position", "Vexes appear at the target point; native recast dismisses them."),
    "sunbeam": (POS, "position", "Sunbeam at the target point."),
    "telekinesis": (CHAN, "entity", "Channel: holds the living target along the caster's aim."),
    "teleport": (TP, "position", "Moves the caster to a safe point at the target; never moves the target."),
    "throw": (SELF, "self", "Throws a copy of the caster's main-hand item along the aim."),
    "thunderstorm": (SELF, "self", "Storm around the caster."),
    "touch_dig": (BLOCK, "block", "Breaks the target block if its hardness allows."),
    "volt_strike": (SELF, "self", "Dash along the caster's aim."),
    "wall_of_fire": (POS, "position", "Each use adds a wall anchor at the target point; the wall forms when native recasts end."),
    "wisp": (ENT, "entity", "Wisp homes on the living target."),
    "wither_skull": (PROJ, "any", "Skull projectile."),
    "wololo": (ENT, "entity", "Recolours a targeted sheep."),
}

FORMS = {
    "self": ["self"],
    "entity": ["touch", "projectile"],
    "entity_or_self": ["self", "touch", "projectile"],
    "block": ["touch", "projectile", "underfoot"],
    "position": ["self", "touch", "projectile", "underfoot"],
    "any": ["self", "touch", "projectile", "underfoot"],
}

RESTRICTION = {
    "self": "ars_n_spells.glyph.restriction.self_only",
    "entity": "ars_n_spells.glyph.restriction.living_target",
    "entity_or_self": "ars_n_spells.glyph.restriction.living_target_or_self",
    "block": "ars_n_spells.glyph.restriction.block_target",
    "position": "ars_n_spells.glyph.restriction.position",
    "any": "ars_n_spells.glyph.restriction.any",
}

FINAL_EFFECT_ONLY = {"pocket_dimension", "recall"}

ROMAN = ["", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
         "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"]

TIER_BY_RARITY = {"COMMON": 1, "UNCOMMON": 2, "RARE": 2, "EPIC": 3, "LEGENDARY": 3}

# Family representatives and special-case evidence, by GameTest name. Every supported spell also
# has a generated smoke scenario (ImportedGlyphSmokeGameTests: glyph_smoke_<spell>).
EVIDENCE = {
    SELF: "ImportedGlyphGameTests#ironsLoaded_selfHeal_healsTheCaster",
    PROJ: "ImportedGlyphGameTests#ironsLoaded_touchFirebolt_hitsTheTouchedTarget",
    ENT: "ImportedGlyphGameTests#ironsLoaded_touchSlow_slowsTheTouchedEntity",
    POS: "ImportedGlyphGameTests#ironsLoaded_touchLightningBolt_strikesTheTouchedPoint",
    BLOCK: "ImportedGlyphGameTests#ironsLoaded_touchTouchDig_breaksTheTouchedBlock",
    TP: "ImportedGlyphGameTests#ironsLoaded_touchTeleport_movesOnlyTheCaster",
    SUMMON: "ImportedGlyphGameTests#ironsLoaded_touchSummonVex_summonsAtTheTarget",
    CHAN: "ImportedGlyphGameTests#ironsLoaded_selfConeOfCold_channelsAndPaysPerPulse",
}


def augments(entry, target, adapter):
    out = []
    if entry["max_level"] > 1:
        out.append("amplify/dampen (effective level, within native levels)")
    if adapter == CHAN:
        out.append("extend_time/duration_down (channel length)")
    if entry["cast_type"] == "LONG":
        out.append("accelerate/decelerate (charge time)")
    elif adapter == PROJ:
        out.append("accelerate/decelerate (projectile speed)")
    return out


def main(inventory_path):
    inventory = json.loads(pathlib.Path(inventory_path).read_text(encoding="utf-8"))
    spells = [s for s in inventory["spells"] if s["spell"].startswith("irons_spellbooks:") and not s["none"]]
    missing = sorted(s["spell"] for s in spells if s["spell"].split(":", 1)[1] not in CLASSIFICATION)
    if missing:
        sys.exit("unclassified base spells (classify them before regenerating): " + ", ".join(missing))
    stale = sorted(set(CLASSIFICATION) - {s["spell"].split(":", 1)[1] for s in spells})
    if stale:
        sys.exit("classified spells absent from the inventory: " + ", ".join(stale))

    catalog_spells = []
    lang = collections.OrderedDict()
    manifest = []
    for s in sorted(spells, key=lambda e: e["spell"]):
        path = s["spell"].split(":", 1)[1]
        adapter, target, note = CLASSIFICATION[path]
        tiers = [TIER_BY_RARITY[r] for r in s["rarity"]]
        record = collections.OrderedDict([
            ("spell", s["spell"]),
            ("name", s["name"]),
            ("school", s["school"]),
            ("cast_type", s["cast_type"]),
            ("min_level", s["min_level"]),
            ("max_level", s["max_level"]),
            ("rarity", s["rarity"]),
            ("default_tier", tiers),
            ("requires_learning", s["requires_learning"]),
            ("enabled_by_default", s["enabled"]),
            ("adapter", adapter),
            ("target", target),
            ("final_effect_only", path in FINAL_EFFECT_ONLY),
            ("status", "supported"),
        ])
        catalog_spells.append(record)
        for level in range(s["min_level"], s["max_level"] + 1):
            # Ars names a spell part by "<namespace>.glyph_name.<part path>"; the part id is iron/...
            key = "ars_n_spells.glyph_name.iron/irons_spellbooks/%s/level_%d" % (path, level)
            lang[key] = "%s %s" % (s["name"], ROMAN[level] if level < len(ROMAN) else str(level))
        prereq = []
        if s["requires_learning"]:
            prereq.append("learned in Iron's (Eldritch)")
        if not s["enabled"]:
            prereq.append("disabled in Iron's default config")
        manifest.append(collections.OrderedDict([
            ("spell", s["spell"]),
            ("levels", "%d-%d" % (s["min_level"], s["max_level"])),
            ("school", s["school"]),
            ("cast_type", s["cast_type"]),
            ("adapter", adapter),
            ("target", target),
            ("supported_forms", FORMS[target]),
            ("augments", augments(s, target, adapter)),
            ("prerequisites", prereq or ["none beyond Iron's own cast conditions"]),
            ("status", "supported"),
            ("notes", note + (" Must be the last effect of a spell." if path in FINAL_EFFECT_ONLY and "last effect" not in note else "")),
            ("test_evidence", [EVIDENCE[adapter], "ImportedGlyphSmokeGameTests#glyph_smoke_" + path]),
            ("glyph_ids", ["ars_n_spells:iron/irons_spellbooks/%s/level_%d" % (path, l)
                           for l in range(s["min_level"], s["max_level"] + 1)]),
            ("item_ids", ["ars_n_spells:glyph_iron/irons_spellbooks/%s/level_%d" % (path, l)
                          for l in range(s["min_level"], s["max_level"] + 1)]),
        ]))

    catalog = collections.OrderedDict([
        ("schema_version", 1),
        ("catalog", "ars_n_spells:irons_spellbooks_base"),
        ("source", collections.OrderedDict([
            ("mod_id", inventory["mod_id"]),
            ("mod_version", inventory["mod_version"]),
            ("generator", "tools/generate_iron_glyph_catalog.py"),
            ("inventory", "runtime inventory (IronGlyphCatalogGameTests) with Iron's default spell config"),
        ])),
        ("excluded", [
            {"spell": "irons_spellbooks:none", "reason": "Iron's empty-slot placeholder, not a spell."},
            {"spell": "ars_n_spells:ars_cross_*", "reason": "ANS native-wheel proxies for exported Ars spells; importing one would recurse."},
            {"spell": "irons_spellbooks:soulfire_ray", "reason": "Class present in the jar but not registered by Iron's 1.21.1-3.16.3; no scroll can exist."},
            {"spell": "irons_spellbooks:thunder_step", "reason": "Class present in the jar but not registered by Iron's 1.21.1-3.16.3; no scroll can exist."},
        ]),
        ("spells", catalog_spells),
    ])
    CATALOG.parent.mkdir(parents=True, exist_ok=True)
    CATALOG.write_text(json.dumps(catalog, indent=2) + "\n", encoding="utf-8")

    data = json.loads(LANG.read_text(encoding="utf-8"), object_pairs_hook=collections.OrderedDict)
    for key in [k for k in data if k.startswith(("ars_n_spells.glyph_name.glyph_iron/", "ars_n_spells.glyph_name.iron/"))]:
        del data[key]
    data.update(lang)
    LANG.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    DOC_JSON.parent.mkdir(parents=True, exist_ok=True)
    DOC_JSON.write_text(json.dumps(collections.OrderedDict([
        ("source", catalog["source"]),
        ("variants", sum(s["max_level"] - s["min_level"] + 1 for s in catalog_spells)),
        ("spells", manifest),
        ("excluded", catalog["excluded"]),
    ]), indent=2) + "\n", encoding="utf-8")

    rows = ["| spell ID | levels | school | cast type | adapter | supported forms | augments | prerequisites | status/reason | test evidence |",
            "|---|---|---|---|---|---|---|---|---|---|"]
    for m in manifest:
        rows.append("| `%s` | %s | %s | %s | %s | %s | %s | %s | %s — %s | %s |" % (
            m["spell"], m["levels"], m["school"].split(":")[1], m["cast_type"], m["adapter"].split(":")[1],
            ", ".join(m["supported_forms"]), "; ".join(m["augments"]) or "none",
            "; ".join(m["prerequisites"]), m["status"], m["notes"],
            "<br>".join("`%s`" % e for e in m["test_evidence"])))
    header = DOC_MD.read_text(encoding="utf-8").split("<!-- GENERATED TABLE -->")[0] if DOC_MD.exists() else ""
    if not header:
        header = "# Iron's glyph coverage\n\n<!-- Hand-written text above the marker is kept by the generator. -->\n\n"
    DOC_MD.write_text(header + "<!-- GENERATED TABLE -->\n\n" + "\n".join(rows) + "\n\n"
                      + "Excluded: " + "; ".join("`%s` (%s)" % (e["spell"], e["reason"]) for e in catalog["excluded"]) + "\n",
                      encoding="utf-8")
    print("catalog: %d spells, %d variants, %d lang keys" % (len(catalog_spells),
          sum(s["max_level"] - s["min_level"] + 1 for s in catalog_spells), len(lang)))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
