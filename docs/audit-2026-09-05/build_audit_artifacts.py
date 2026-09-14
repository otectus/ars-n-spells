"""Rebuild audit inventories from the two pinned, extracted source snapshots.

Usage: python build_audit_artifacts.py --snapshots PATH
No network access, game files, or mod source files are modified.
"""
from pathlib import Path
import argparse
import csv
import hashlib
import json
import re
import struct
import zipfile

OUT = Path(__file__).resolve().parent
SHAS = {"forge": "2578bad5d820fc2de9f38c26c92833a0d977c441",
        "neo": "eb6d3af883b5386785e7e4d2793871f11f3830ce"}

def table(name, rows):
    with (OUT / name).open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)

def inventories(root):
    sources, assets, deps = [], [], []
    for branch, sha in SHAS.items():
        base = root / branch
        for p in sorted((base / "src").rglob("*")):
            if not p.is_file():
                continue
            rel = p.relative_to(base).as_posix()
            raw = p.read_bytes()
            sources.append(dict(loader=branch, path=rel, bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest(),
                                source_url=f"https://github.com/otectus/ars-n-spells/blob/{sha}/{rel}"))
            if p.suffix == ".png":
                w, h = struct.unpack(">II", raw[16:24])
                assets.append(dict(loader=branch, path=rel, width=w, height=h, sha256=hashlib.sha256(raw).hexdigest(),
                                   license_status="Repository GPLv3; individual art provenance requires confirmation",
                                   disposition="Preserve existing resource path as legacy alias"))
    table("source-inventory.csv", sources)
    table("current-assets.csv", assets)
    jars_file = root / "jars.json"
    if jars_file.exists():
        for label, filename in json.loads(jars_file.read_text()).items():
            p = Path(filename)
            with zipfile.ZipFile(p) as z:
                metadata = next((n for n in z.namelist() if n.endswith(("/mods.toml", "/neoforge.mods.toml"))), None)
                raw = z.read(metadata).decode() if metadata else ""
                version = re.findall(r'^\s*version\s*=\s*[\'"]([^\'\"]+)', raw, re.M)
                license_value = re.findall(r'^\s*license\s*=\s*[\'"]([^\'\"]+)', raw, re.M)
                resolved_version = version[0] if version else "unknown"
                if resolved_version == "${file.jarVersion}" and "META-INF/MANIFEST.MF" in z.namelist():
                    manifest = z.read("META-INF/MANIFEST.MF").decode()
                    values = re.findall(r'^Implementation-Version:\s*(.+)', manifest, re.M)
                    resolved_version = values[0].strip() if values else resolved_version
            deps.append(dict(label=label, filename=p.name, version=resolved_version,
                             sha256=hashlib.sha256(p.read_bytes()).hexdigest(),
                             declared_license=license_value[0] if license_value else "unspecified"))
        table("dependency-evidence.csv", deps)
    # Contact sheet is an inspection rendering of existing assets, not new game art.
    from PIL import Image, ImageDraw
    rows = [r for r in assets if r["loader"] == "forge"]
    sheet = Image.new("RGB", (880, ((len(rows) + 5) // 6) * 104 + 40), "#20232b")
    d = ImageDraw.Draw(sheet)
    d.text((12, 10), "Existing Forge assets (nearest-neighbor 3x); Neo paths and hashes in current-assets.csv", fill="white")
    for i, row in enumerate(rows):
        x, y = 10 + (i % 6) * 145, 40 + (i // 6) * 104
        p = root / "forge" / row["path"]
        img = Image.open(p).convert("RGBA").resize((48, 48), Image.Resampling.NEAREST)
        sheet.paste(img, (x + 40, y), img)
        label = p.stem
        for j in range(0, len(label), 21):
            d.text((x, y + 54 + (j // 21) * 12), label[j:j+21], fill="#e8e9ee")
    sheet.save(OUT / "current-icon-contact-sheet.png")

def proposed_icons():
    groups = {
        "spell": ("original_art", "Loom picker; carrier tooltip; Iron's proxy wheel; guide", "P2",
            "projectile beam ray burst cone nova wall ring orbit chain trap rune missile volley rain meteor shard lance wave pulse "
            "fireball flame_jet ignite combustion frost_bolt freeze blizzard hail lightning_bolt thunderstorm shock_chain static_field "
            "poison_cloud toxic_spore vine_grasp thorn_burst root_snare earthquake stone_spike sand_blast "
            "heal cleanse regenerate sanctuary ward barrier reflect absorb "
            "blood_lance drain_life sacrifice blood_pact wither curse hex terror "
            "blink teleport portal recall phase gravity_pull gravity_push levitate flight dash leap slow haste "
            "summon_familiar summon_guardian summon_undead summon_weapon command_minion banish "
            "light reveal detect invisibility silence dispel counterspell "
            "break_block place_block harvest grow smelt cut extract exchange transmute collect interact carry craft "
            "mana_transfer mana_drain mana_restore source_transfer lifelink phantom_grasp charm time_delay contingency split amplify dampen extend area_expand"),
        "school": ("original_art", "School badges; damage explanation; affinity and progression panels", "P1",
            "fire ice lightning nature holy blood ender evocation eldritch generic unknown"),
        "element": ("original_art", "Ars analysis; mapping inspector; optional add-on guide", "P2",
            "air earth water fire manipulation conjuration abjuration necromancy arcane light shadow void time force poison frost"),
        "status": ("original_art", "ANS HUD and tooltips; status explanation; no implied effect registration", "P2",
            "resonance_ready resonance_lingering resonance_depleted affinity_gain affinity_decay progression_gain mastery "
            "mana_boost mana_regen source_proximity source_empty cooldown_shared cooldown_personal silenced interrupted "
            "cast_pending cast_failed spell_bound spell_unbound spell_missing spell_blacklisted insufficient_mana insufficient_lp insufficient_aura overdrawn"),
        "compat": ("procedural", "Compatibility panel; tooltips; admin diagnostics; guide", "P1",
            "available absent degraded disabled unsupported untested mismatch synchronized desynchronized optional required server_only client_only "
            "api_verified runtime_verified resource_missing data_migrated migration_needed conflict fallback addon_unknown"),
        "ui": ("procedural", "Loom; settings; guide; diagnostics; accessible controls", "P1",
            "inscribe bind unbind transcribe erase copy paste preview confirm cancel close back forward up down expand collapse "
            "search filter sort grid list favorite unfavorite lock unlock info help warning error success settings reset refresh reload "
            "import export inspect compare link unlink swap next_spell previous_spell hand_main hand_off mouse_right keyboard gamepad inventory output"),
        "resource": ("original_art", "Cost quote; HUD; ritual preview; balance inspector", "P1",
            "mana_ars mana_irons mana_shared mana_split lp aura source mana_cost mana_refund mana_reserve mana_capacity mana_regeneration "
            "conversion efficiency power resistance health cooldown duration range area tier_one tier_two tier_three"),
        "ritual": ("owned_composition_review", "Ritual tablet item/guide/JEI recipe category; reuse own art only after provenance review", "P2",
            "mana_infusion mana_well spell_transcription spellbook_binding spell_uninscription"),
        "carrier": ("original_art", "Loom slot hints; book inspector; onboarding", "P2",
            "scroll_blank scroll_inscribed book_ars book_irons book_mixed focus parchment slot_empty slot_native slot_cross slot_conflict slot_locked"),
    }
    rows = []
    for group, (production, placement, priority, names) in groups.items():
        for name in names.split():
            logical = f"{group}/{name}"
            rows.append(dict(logical_id=f"ars_n_spells:{logical}",
                             resource_path=f"assets/ars_n_spells/textures/gui/icons/v2/{logical}.png",
                             status="PROPOSED; not implemented", art_method=production,
                             required_sizes="16;32 (32 uses separate /hd32 resource pack)",
                             variants="normal;selected;disabled;high_contrast;monochrome",
                             palette_role=group, uses=placement, priority=priority,
                             fallback=("builtin:missing_icon" if logical == "ui/help" else
                                       "ars_n_spells:ui/help" if logical == "school/unknown" else
                                       f"ars_n_spells:{'school/unknown' if group in ('spell','school','element') else 'ui/help'}"),
                             accessible_label=f"icon.ars_n_spells.{group}.{name}",
                             license_gate="Original or verified ANS-owned artwork; record author/source/license/hash; no upstream texture redistribution"))
    table("planned-icons.csv", rows)
    return len(rows)

def static_checks(root):
    checks, mappings = {}, []
    for branch, sha in SHAS.items():
        base = root / branch
        resources = base / "src/main/resources"
        parsed, invalid = 0, []
        for p in resources.rglob("*.json"):
            try:
                json.loads(p.read_text(encoding="utf-8"))
                parsed += 1
            except Exception as e:
                invalid.append({"path": p.relative_to(base).as_posix(), "error": str(e)})
        language = json.loads((resources / "assets/ars_n_spells/lang/en_us.json").read_text(encoding="utf-8"))
        missing = []
        for p in (base / "src/main/java").rglob("*.java"):
            body = p.read_text(encoding="utf-8")
            for match in re.finditer(r'Component\.translatable\("([^"\n]+)"\s*[,)]', body):
                key = match.group(1)
                if "ars_n_spells" in key and key not in language:
                    missing.append({"key": key, "path": p.relative_to(base).as_posix(), "line": body[:match.start()].count("\n") + 1})
        for filename in ["SchoolMappings.java", "SchoolResolver.java"]:
            p = next((base / "src/main/java").rglob(filename))
            body = p.read_text(encoding="utf-8")
            for match in re.finditer(r'm\.put\("([^"\n]+)",\s*([^;]+);', body):
                schools = re.findall(r'SpellSchoolId\.(\w+)', match.group(2))
                if not schools:
                    continue
                line = body[:match.start()].count("\n") + 1
                path = p.relative_to(base).as_posix()
                mappings.append(dict(loader=branch, layer="glyph_override" if filename == "SchoolMappings.java" else "ars_school_translation",
                                     key=match.group(1), schools=";".join(schools),
                                     source_url=f"https://github.com/otectus/ars-n-spells/blob/{sha}/{path}#L{line}",
                                     caveat="Static explicit map only; runtime metadata/heuristics/datapacks may also resolve glyphs"))
        checks[branch] = dict(valid_json_files=parsed, invalid_json=invalid, english_keys=len(language),
                              missing_direct_literal_translation_keys=missing,
                              limitations="Syntax and direct literal keys only; not proof of valid registry references, dynamic keys, recipe behavior, or full localization")
    table("builtin-school-mappings.csv", mappings)
    (OUT / "static-resource-checks.json").write_text(json.dumps(checks, indent=2), encoding="utf-8")

def resolve_references(root):
    source = OUT / "IMPLEMENTATION_SPEC.template.md"
    if not source.exists():
        return
    def ref(match):
        branch, filename, line = match.groups()
        branch = {"F": "forge", "N": "neo"}[branch]
        base = root / branch
        direct = base / filename
        if direct.is_file():
            p = direct
        else:
            candidates = list((base / "src/main").rglob(filename))
            if len(candidates) != 1:
                raise ValueError((branch, filename, candidates))
            p = candidates[0]
        rel = p.relative_to(base).as_posix()
        if line:
            assert 0 < int(line) <= len(p.read_text(encoding="utf-8").splitlines()), (rel, line)
        label = f"{'Forge' if branch == 'forge' else 'NeoForge'} {filename}" + (f":{line}" if line else "")
        url = f"https://github.com/otectus/ars-n-spells/blob/{SHAS[branch]}/{rel}" + (f"#L{line}" if line else "")
        return f"[{label}]({url})"
    body = re.sub(r"\{\{([FN])\|([^|}]+)\|(\d*)\}\}", ref, source.read_text(encoding="utf-8"))
    assert "{{" not in body, "Unresolved reference"
    (OUT / "IMPLEMENTATION_SPEC.md").write_text(body, encoding="utf-8")

def validate_artifacts():
    with (OUT / "planned-icons.csv").open(encoding="utf-8", newline="") as f:
        icons = list(csv.DictReader(f))
    by_id = {r["logical_id"]: r for r in icons}
    assert len(icons) == len(by_id) == 274
    assert len({r["resource_path"] for r in icons}) == len(icons)
    for icon_id in by_id:
        seen, current = set(), icon_id
        while current != "builtin:missing_icon":
            assert current in by_id, (icon_id, "Missing fallback", current)
            assert current not in seen, (icon_id, "Fallback cycle")
            seen.add(current)
            current = by_id[current]["fallback"]
    for name in ["IMPLEMENTATION_SPEC.md", "validation.md"]:
        p = OUT / name
        if not p.exists():
            continue
        body = p.read_text(encoding="utf-8")
        for link in re.findall(r'\]\(([^)]+)\)', body):
            if link.startswith(("https://", "http://", "#")):
                continue
            assert (OUT / link.split("#")[0]).exists(), (name, "Broken local link", link)
        assert body.count("```") % 2 == 0, (name, "Unclosed fenced block")
    print("Artifact checks passed: 274 unique icons, terminating fallbacks, local links and code fences.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--snapshots", type=Path, required=True)
    args = parser.parse_args()
    inventories(args.snapshots)
    static_checks(args.snapshots)
    print("Proposed base icons:", proposed_icons())
    resolve_references(args.snapshots)
    validate_artifacts()
