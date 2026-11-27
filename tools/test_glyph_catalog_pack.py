"""The pack-author catalog builder: identity envelopes, placeholders and promotion, minimum levels, strict
input, the target loader's limits, atomic output and the catalog-only jar."""
import contextlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
import zipfile

import glyph_catalog_pack as pack
import validate_glyph_mappings as vgm

FIXTURES = Path(__file__).resolve().parent.parent / "src/test/resources/ans_shared_fixtures/catalogs"


def spell(spell_id, max_level=3, min_level=1, rarities=None, **extra):
    # Bounded: the overflow case passes a 4-billion maximum, which must never become a 4-billion-entry list here.
    count = max(1, min(max_level - min_level + 1, 99)) if type(max_level) is int and type(min_level) is int else 3
    entry = {"spell": spell_id, "name": spell_id.split(":")[1].title(), "school": "irons_spellbooks:fire", "cast_type": "INSTANT",
             "min_level": min_level, "max_level": max_level,
             "rarity_by_level": rarities or (["COMMON"] * (count - 1) + ["RARE"]),
             "requires_learning": False, "enabled": True, "icon": f"{spell_id.split(':')[0]}:textures/gui/spell_icons/x.png",
             "none": False, "ans_proxy": False}
    entry.update(extra)
    return entry


class PackTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)
        self.counter = 0

    def tearDown(self):
        self.tmp.cleanup()

    def write(self, name, data, raw=None):
        path = self.dir / name
        path.write_text(raw if raw is not None else json.dumps(data), encoding="utf-8")
        return str(path)

    def run_pack(self, *argv):
        with contextlib.redirect_stdout(io.StringIO()):
            pack.main(list(argv))

    def inventory(self, *spells, raw=None):
        self.counter += 1
        return self.write(f"inventory-{self.counter}.json", {"mod_version": "1.0", "spells": list(spells)}, raw=raw)

    def build(self, inventory, adapters, *extra, loader="forge", name="addon", out=None):
        out = Path(out) if out else self.dir / ("out-" + loader)
        self.counter += 1
        self.run_pack("--inventory", inventory, "--adapters", self.write(f"adapters-{self.counter}.json", {"spells": adapters}),
                      "--name", name, "--out", str(out), "--loader", loader, *extra)
        return json.loads((out / f"{name}.json").read_text(encoding="utf-8")), out

    def refused(self, *args, **kwargs):
        with self.assertRaises(SystemExit) as refused:
            self.build(*args, **kwargs)
        return str(refused.exception)

    def test_every_spell_reserves_the_envelope_and_an_unassigned_one_is_a_placeholder(self):
        inventory = self.inventory(spell("addon:bolt"), spell("addon:odd", 1), spell("addon:none", none=True),
                                   spell("ars_n_spells:proxy", ans_proxy=True))
        catalog, out = self.build(inventory, {"addon:bolt": {"adapter": "ars_n_spells:projectile"}})
        by_id = {e["spell"]: e for e in catalog["spells"]}
        self.assertEqual({"addon:bolt", "addon:odd"}, set(by_id))
        self.assertEqual(2, catalog["schema_version"])
        self.assertEqual({"min": 1, "default_max": 3, "registered_max": 10}, by_id["addon:bolt"]["levels"])
        self.assertEqual(("supported", 1), (by_id["addon:bolt"]["status"], by_id["addon:bolt"]["adapter_version"]))
        # A placeholder: registered and dormant, with no adapter contract (version 0) to obstruct a later assignment.
        self.assertEqual(("unsupported", 0), (by_id["addon:odd"]["status"], by_id["addon:odd"]["adapter_version"]))
        self.assertEqual({"min": 1, "default_max": 1, "registered_max": 10}, by_id["addon:odd"]["levels"],
                         "a spell limited to one level still reserves the envelope")
        self.assertEqual([1, 1, 2], by_id["addon:bolt"]["tier"])
        self.assertIn("addon:odd: unsupported", (out / "addon-report.txt").read_text(encoding="utf-8"))
        # The manifest is a catalog the mapping validator (and the game) can read.
        self.assertEqual(10, vgm.read_catalogs([out / "addon.json"])["addon:bolt"].registered_max)

    def test_a_placeholder_is_promoted_without_losing_an_identity(self):
        for loader, adapter in (("forge", {"adapter": "ars_n_spells:projectile"}),
                                ("neoforge", {"adapter": "ars_n_spells:projectile", "target": "any"})):
            with self.subTest(loader):
                first, out = self.build(self.inventory(spell("addon:bolt")), {}, "--envelope", "12", loader=loader)
                self.assertEqual(0, first["spells"][0]["adapter_version"])
                previous = self.write(f"previous-{loader}.json", first)
                second, _ = self.build(self.inventory(spell("addon:bolt")), {"addon:bolt": adapter}, "--previous", previous, loader=loader)
                promoted = second["spells"][0]
                self.assertEqual(("ars_n_spells:projectile", 1, "supported"),
                                 (promoted["adapter"], promoted["adapter_version"], promoted["status"]))
                self.assertEqual({"min": 1, "default_max": 3, "registered_max": 12}, promoted["levels"], "every id of the placeholder stays")
                self.assertIn("promoted from a placeholder", (out / "addon-report.txt").read_text(encoding="utf-8"))

    def test_a_vanished_spell_is_retired_and_a_published_contract_never_changes(self):
        inventory = self.inventory(spell("addon:bolt"), spell("addon:gone"))
        first, out = self.build(inventory, {"addon:bolt": {"adapter": "ars_n_spells:projectile"},
                                            "addon:gone": {"adapter": "ars_n_spells:summon"}}, "--envelope", "12")
        previous = self.write("previous.json", first)
        second, _ = self.build(self.inventory(spell("addon:bolt")), {"addon:bolt": {"adapter": "ars_n_spells:projectile"}},
                               "--previous", previous)
        by_id = {e["spell"]: e for e in second["spells"]}
        self.assertEqual("retired", by_id["addon:gone"]["status"])
        self.assertEqual(12, by_id["addon:bolt"]["levels"]["registered_max"], "a smaller --envelope never shrinks a published spell")
        published = (out / "addon.json").read_bytes()
        message = self.refused(self.inventory(spell("addon:bolt")), {"addon:bolt": {"adapter": "ars_n_spells:summon"}}, "--previous", previous)
        self.assertIn("cannot be replaced once published", message)
        self.assertEqual(published, (out / "addon.json").read_bytes(), "a refused build changes nothing in --out")
        # Without an assignment a published contract is kept and only marked unsupported.
        third, _ = self.build(self.inventory(spell("addon:bolt")), {}, "--previous", previous)
        kept = {e["spell"]: e for e in third["spells"]}["addon:bolt"]
        self.assertEqual(("ars_n_spells:projectile", 1, "unsupported"), (kept["adapter"], kept["adapter_version"], kept["status"]))

    def test_a_contract_never_goes_back_and_unknown_versions_are_refused(self):
        first, _ = self.build(self.inventory(spell("addon:bolt")), {"addon:bolt": {"adapter": "ars_n_spells:projectile"}})
        first["spells"][0]["adapter_version"] = 2
        previous = self.write("future.json", first)
        self.assertIn("cannot go back", self.refused(self.inventory(spell("addon:bolt")),
                                                     {"addon:bolt": {"adapter": "ars_n_spells:projectile"}}, "--previous", previous))
        for version in (0, 2, 1001, 1.5, "1"):
            with self.subTest(version=version):
                self.assertIn("adapter_version", self.refused(self.inventory(spell("addon:bolt")),
                                                              {"addon:bolt": {"adapter": "ars_n_spells:projectile", "adapter_version": version}}))

    def test_minimum_levels_are_aligned_kept_and_extended(self):
        two = spell("addon:bolt", 4, min_level=2, rarities=["UNCOMMON", "RARE", "EPIC"])
        first, out = self.build(self.inventory(two), {"addon:bolt": {"adapter": "ars_n_spells:projectile"}})
        entry = first["spells"][0]
        self.assertEqual({"min": 2, "default_max": 4, "registered_max": 10}, entry["levels"])
        self.assertEqual(["UNCOMMON", "RARE", "EPIC"], entry["rarity"], "rarity[i] is the rarity of level min + i")
        previous = self.write("previous.json", first)
        raised, _ = self.build(self.inventory(spell("addon:bolt", 4, min_level=3, rarities=["RARE", "LEGENDARY"])),
                               {"addon:bolt": {"adapter": "ars_n_spells:projectile"}}, "--previous", previous)
        kept = raised["spells"][0]
        self.assertEqual(2, kept["levels"]["min"], "a raised native minimum keeps the lower identities")
        self.assertEqual(["UNCOMMON", "RARE", "LEGENDARY"], kept["rarity"], "level 2 keeps its published rarity")
        self.assertIn("native minimum rose to 3", (out / "addon-report.txt").read_text(encoding="utf-8"))
        lowered, _ = self.build(self.inventory(spell("addon:bolt", 4)), {"addon:bolt": {"adapter": "ars_n_spells:projectile"}},
                                "--previous", self.write("previous2.json", raised))
        self.assertEqual(1, lowered["spells"][0]["levels"]["min"], "a lowered minimum adds the levels below")

    def test_extreme_maxima_are_bounded_by_the_id_encoding_and_the_loader_caps(self):
        catalog, out = self.build(self.inventory(spell("addon:huge", 150, rarities=["COMMON"] * 99)),
                                  {"addon:huge": {"adapter": "ars_n_spells:projectile"}})
        self.assertEqual({"min": 1, "default_max": 99, "registered_max": 99}, catalog["spells"][0]["levels"])
        self.assertIn("native maximum 150 is above the id encoding", (out / "addon-report.txt").read_text(encoding="utf-8"))
        for loader, count in (("forge", 42), ("neoforge", 83)):
            with self.subTest(loader):
                out = self.dir / f"caps-{loader}"
                many = [spell(f"addon:s{i:03d}", 1, rarities=["COMMON"]) for i in range(count)]
                message = self.refused(self.inventory(*many), {}, "--envelope", "99", loader=loader, out=out)
                self.assertIn("glyph identities, more than the", message)
                self.assertTrue(not out.exists() or not any(out.iterdir()), "nothing is published, not even a staging directory")

    def test_inputs_are_validated_strictly_before_anything_is_written(self):
        cases = {
            "fractional": (spell("addon:bolt", 2.5), "'max_level' must be a whole number"),
            "string": (spell("addon:bolt", "3"), "'max_level' must be a whole number"),
            "overflow": (spell("addon:bolt", 4294967298), "'max_level' must be a whole number"),
            "boolean": (spell("addon:bolt", True), "'max_level' must be a whole number"),
            "flag": (spell("addon:bolt", requires_learning="false"), "'requires_learning' must be true or false"),
            "rarity": (spell("addon:bolt", rarities=["COMMON", "MYTHIC", "RARE"]), "rarity_by_level"),
            "id": (spell("addon:bolt", school="Fire School"), "'school' must be a namespaced id"),
        }
        for label, (entry, fragment) in cases.items():
            with self.subTest(label):
                self.assertIn(fragment, self.refused(self.inventory(entry), {}))
        self.assertIn("listed twice", self.refused(self.inventory(spell("addon:bolt"), spell("addon:bolt")), {}))
        self.assertIn("NaN is not a JSON number", self.refused(self.inventory(raw='{"spells": [], "x": NaN}'), {}))
        adapters = self.write("dup.json", None, raw='{"spells": {"addon:bolt": {"adapter": "ars_n_spells:projectile"}, '
                                                  '"addon:bolt": {"adapter": "ars_n_spells:summon"}}}')
        with self.assertRaises(SystemExit) as duplicate:
            self.run_pack("--inventory", self.inventory(spell("addon:bolt")), "--adapters", adapters, "--name", "addon",
                          "--out", str(self.dir / "dup"))
        self.assertIn("duplicate key", str(duplicate.exception))
        self.assertIn("unknown field", self.refused(self.inventory(spell("addon:bolt")),
                                                    {"addon:bolt": {"adapter": "ars_n_spells:projectile", "colour": "red"}}))
        self.assertFalse((self.dir / "out-forge" / "addon.json").exists())

    def test_an_explicit_unsupported_verdict_is_a_placeholder_with_its_reason(self):
        catalog, out = self.build(self.inventory(spell("addon:dash")),
                                  {"addon:dash": {"status": "unsupported", "reason": "computes its own dash path"}})
        entry = catalog["spells"][0]
        self.assertEqual(("unsupported", 0, "computes its own dash path"), (entry["status"], entry["adapter_version"], entry["reason"]))
        self.assertIn("without an adapter", self.refused(self.inventory(spell("addon:dash")),
                                                         {"addon:dash": {"status": "supported", "reason": "x"}}),
                      "a supported spell needs an adapter")
        self.assertIn("without an adapter", self.refused(self.inventory(spell("addon:dash")), {"addon:dash": {"status": "unsupported"}}))

    def test_adapters_and_targets_follow_the_loader(self):
        inventory = self.inventory(spell("addon:bolt"))
        self.refused(inventory, {"addon:bolt": {"adapter": "ars_n_spells:entity"}}, loader="forge")
        catalog, _ = self.build(inventory, {"addon:bolt": {"adapter": "ars_n_spells:entity", "target": "entity"}}, loader="neoforge")
        self.assertEqual("entity", catalog["spells"][0]["target"])
        self.refused(inventory, {"addon:bolt": {"adapter": "ars_n_spells:entity", "target": "sideways"}}, loader="neoforge")

    def test_the_catalog_only_jar_carries_manifest_models_and_names(self):
        inventory = self.inventory(spell("addon:bolt"))
        adapters = {"addon:bolt": {"adapter": "ars_n_spells:projectile", "target": "any"}}
        for loader, toml, prefix in (("forge", "META-INF/mods.toml", "iron"), ("neoforge", "META-INF/neoforge.mods.toml", "glyph_iron")):
            with self.subTest(loader):
                _, out = self.build(inventory, adapters, "--envelope", "15", "--jar", "--mod-id", "addon_glyphs", "--mod-version", "2.0.0",
                                    loader=loader)
                with zipfile.ZipFile(out / "addon_glyphs-2.0.0.jar") as jar:
                    names = set(jar.namelist())
                    self.assertIn(toml, names)
                    self.assertIn('modLoader="lowcodefml"', jar.read(toml).decode())
                    self.assertIn("ans_glyph_catalog/addon.json", names)
                    models = [n for n in names if n.startswith(f"assets/ars_n_spells/models/item/{prefix}/addon/bolt/")]
                    self.assertEqual(15, len(models))
                    lang = json.loads(jar.read("assets/ars_n_spells/lang/en_us.json"))
                    self.assertEqual("Bolt X", lang["ars_n_spells.glyph_name.iron/addon/bolt/level_10"])
                    self.assertEqual("Bolt 15", lang["ars_n_spells.glyph_name.iron/addon/bolt/level_15"])
                    marked = json.loads(jar.read(f"assets/ars_n_spells/models/item/{prefix}/addon/bolt/level_10.json"))
                    high = json.loads(jar.read(f"assets/ars_n_spells/models/item/{prefix}/addon/bolt/level_15.json"))
                    if loader == "forge":
                        self.assertEqual({"parent": "forge:item/default", "loader": "ars_n_spells:imported_glyph",
                                          "icon": "addon:gui/spell_icons/x", "level": 15}, high)
                    else:
                        self.assertIn("layer2", marked["textures"])
                        self.assertNotIn("layer2", high["textures"], "no level marker exists above X; none is referenced")
                        self.assertEqual("ars_n_spells:item/glyph_iron_dormant/unmarked", high["overrides"][0]["model"])
                self.assertEqual([], [p.name for p in out.iterdir() if p.name.startswith(".")], "the staging directory is removed")

    def test_the_envelope_is_bounded_by_the_id_encoding(self):
        self.refused(self.inventory(spell("addon:bolt")), {}, "--envelope", "100")

    def test_the_tool_output_is_the_shared_fixture_each_loader_loads(self):
        """The tool's real output for a placeholder and its promotion, as the Java catalog fixtures assemble it."""
        for loader in ("forge", "neoforge"):
            with self.subTest(loader):
                expected = self.promotion_fixture(loader)
                path = FIXTURES / f"tool_package_promotion_loads_on_{loader}.json"
                text = json.dumps(expected, indent=1, sort_keys=True) + "\n"
                if os.environ.get("ANS_REGENERATE_FIXTURES") == "1":
                    path.write_text(text, encoding="utf-8")
                self.assertEqual(text, path.read_text(encoding="utf-8"),
                                 f"{path.name} is stale; regenerate it with ANS_REGENERATE_FIXTURES=1 in both trees")
                index = json.loads((FIXTURES / "index.json").read_text(encoding="utf-8"))
                self.assertIn(path.stem, index["cases"])

    def promotion_fixture(self, loader):
        adapter = {"adapter": "ars_n_spells:projectile", "target": "any"} if loader == "neoforge" else {"adapter": "ars_n_spells:projectile"}
        inventory = self.inventory(spell("addon:bolt", 4, min_level=2, rarities=["COMMON", "RARE", "EPIC"]), spell("addon:ward", 1))
        first, _ = self.build(inventory, {}, "--envelope", "12", "--mod-version", "1.0.0", loader=loader, out=self.dir / f"v1-{loader}")
        second, _ = self.build(inventory, {"addon:bolt": adapter}, "--previous", self.write(f"v1-{loader}.json", first),
                               "--mod-version", "2.0.0", loader=loader, out=self.dir / f"v2-{loader}")
        base = {"catalog_version": "fixture", "schema_version": 2, "spells": [{
            "adapter": "ars_n_spells:projectile", "adapter_version": 1, "cast_type": "INSTANT", "enabled_by_default": True,
            "levels": {"default_max": 2, "min": 1, "registered_max": 3}, "name": "Firebolt", "rarity": ["COMMON", "RARE"],
            "requires_learning": False, "school": "irons_spellbooks:fire", "spell": "irons_spellbooks:firebolt", "target": "any",
            "tier": [1, 2]}]}
        return {"_comment": "CATALOG-02: tools/glyph_catalog_pack.py output (regenerated and compared by tools/test_glyph_catalog_pack.py): "
                            "a placeholder catalog and its promotion load with no rejected source and keep every identity.",
                "loader": loader,
                "sources": [{"json": base, "name": "base", "required": True},
                            {"json": first, "name": "a_v1", "required": False},
                            {"json": second, "name": "b_v2", "required": False}],
                "expect": {"fails_startup": False, "accepted": ["a_v1", "b_v2"], "identities": 3 + 11 + 12,
                           "first_level": {"addon:bolt": 2}, "registered_max": {"addon:bolt": 12, "addon:ward": 12},
                           "executable": {"addon:bolt": True, "addon:ward": False}, "status": {"addon:bolt": "supported", "addon:ward": "unsupported"},
                           "adapter": {"addon:bolt": "ars_n_spells:projectile"}}}


if __name__ == "__main__":
    unittest.main()
