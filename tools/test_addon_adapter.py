from copy import deepcopy
import json
from pathlib import Path
import tempfile
import unittest

from addon_adapter import build, compare, validate

MANIFEST = json.loads((Path(__file__).resolve().parents[1] / "examples/addon-adapter/ars-elemental.json").read_text())


class AdapterTest(unittest.TestCase):
    def test_both_pins_generate_actual_loader_pack_format(self):
        for loader, expected in (("forge", 15), ("neoforge", 48)):
            with tempfile.TemporaryDirectory() as temp:
                output = Path(temp) / "pack"
                files = build(MANIFEST, loader, output)
                self.assertEqual(2, len(files))
                self.assertEqual(expected, json.loads(files[0].read_text())["pack"]["pack_format"])
                self.assertEqual(["ars_elemental", "irons_spellbooks"], json.loads(files[1].read_text())["requires_mods"])

    def test_filter_cannot_become_a_damage_school_fixture(self):
        manifest = deepcopy(MANIFEST)
        manifest["glyphs"]["ars_elemental:glyph_aquatic_filter"]["schools"] = ["irons_spellbooks:ice"]
        with self.assertRaises(ValueError): validate(manifest, "forge")

    def test_exact_runtime_pins_and_each_glyph_are_required(self):
        observed = {"versions": deepcopy(MANIFEST["profiles"]["forge"]), "glyphs": deepcopy(MANIFEST["glyphs"])}
        compare(MANIFEST, "forge", observed)
        observed["versions"]["ars_elemental"] = "future"
        with self.assertRaises(ValueError): compare(MANIFEST, "forge", observed)
        observed["versions"] = MANIFEST["profiles"]["forge"]
        observed["glyphs"].pop("ars_elemental:glyph_life_link")
        with self.assertRaises(ValueError): compare(MANIFEST, "forge", observed)

    def test_generated_pack_never_overwrites_existing_content(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp)
            (output / "owned.txt").write_text("keep")
            with self.assertRaises(ValueError): build(MANIFEST, "forge", output)
            self.assertEqual("keep", (output / "owned.txt").read_text())

    def test_identity_cannot_escape_namespace_or_output_directory(self):
        manifest = deepcopy(MANIFEST)
        manifest["mod_id"] = "../../escape"
        with self.assertRaises(ValueError): validate(manifest, "forge")

    def test_future_schema_and_loader_mismatch_refuse(self):
        manifest = deepcopy(MANIFEST)
        manifest["schema_version"] = 2
        with self.assertRaises(ValueError): validate(manifest, "forge")
        manifest = deepcopy(MANIFEST)
        manifest["profiles"]["forge"]["minecraft"] = "1.21.1"
        with self.assertRaises(ValueError): validate(manifest, "forge")


if __name__ == "__main__":
    unittest.main()
