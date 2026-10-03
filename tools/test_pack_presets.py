import tempfile
from pathlib import Path
import unittest

from pack_presets import PRESETS, apply, leaves, preview
import tomllib

CONFIG = '''# Saved pack tuning
["Schema Migration"]
config_schema_version = 2
conversion_policy = "flat_legacy"
payment_open_failure_policy = "refuse"
["Master Toggles"]
enable_mana_unification = true
mana_unification_mode = "iss_primary" # retain this explanation
["Mana Unification"]
dual_cost_ars_percentage = 0.5
dual_cost_iss_percentage = 0.5
conversion_rate_ars_to_iron = 3.0
conversion_rate_iron_to_ars = 7.0
["Cross-Cast Inscription"]
cross_cast_cost_multiplier = 1.25
["Source Jar Synergy"]
source_jar_synergy_multiplier = 8.0
["Unknown Extension"]
third_party_value = "preserve me"
'''


class PresetsTest(unittest.TestCase):
    def test_each_preset_parses_and_is_idempotent(self):
        for loader in ("forge", "neoforge"):
            for preset in PRESETS:
                with self.subTest(loader=loader, preset=preset):
                    updated, changes = preview(CONFIG, preset, loader)
                    self.assertEqual([], preview(updated, preset, loader)[1])
                    self.assertIn('# retain this explanation', updated)
                    result = leaves(tomllib.loads(updated))
                    self.assertEqual("preserve me", result["third_party_value"][1])
                    self.assertEqual(3, result["conversion_rate_ars_to_iron"][1])
                    self.assertEqual(7, result["conversion_rate_iron_to_ars"][1])

    def test_neoforge_never_changes_alternate_payment_policy(self):
        updated, _ = preview(CONFIG, "legacy", "neoforge")
        self.assertEqual("refuse", leaves(tomllib.loads(updated))["payment_open_failure_policy"][1])

    def test_legacy_preserves_custom_source_income(self):
        updated, _ = preview(CONFIG, "legacy", "forge")
        self.assertEqual(8, leaves(tomllib.loads(updated))["source_jar_synergy_multiplier"][1])

    def test_missing_or_future_schema_refuses(self):
        for schema in (0, 1, 4):
            with self.assertRaises(ValueError):
                preview(CONFIG.replace("config_schema_version = 2", f"config_schema_version = {schema}"), "expert", "forge")

    def test_schema_3_config_is_accepted_and_keeps_its_cooldown(self):
        config = CONFIG.replace("config_schema_version = 2", "config_schema_version = 3").replace(
            "cross_cast_cost_multiplier = 1.25", "cross_cast_cost_multiplier = 1.25\ninscribed_ars_default_cooldown_ticks = 40")
        updated, changes = preview(config, "expert", "forge")
        self.assertTrue(changes)
        result = leaves(tomllib.loads(updated))
        self.assertEqual(3, result["config_schema_version"][1])
        self.assertEqual(40, result["inscribed_ars_default_cooldown_ticks"][1])

    def test_missing_assignment_and_duplicate_leaf_refuse(self):
        with self.assertRaises(ValueError):
            preview(CONFIG.replace('conversion_policy = "flat_legacy"', ''), "expert", "forge")
        with self.assertRaises(ValueError):
            preview(CONFIG + '\n[duplicate]\nmana_unification_mode = "disabled"\n', "expert", "forge")

    def test_apply_preserves_backup_and_preview_does_not_write(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "ans.toml"
            original = CONFIG.encode()
            path.write_bytes(original)
            updated, _ = preview(CONFIG, "expert", "forge")
            self.assertEqual(original, path.read_bytes())
            backup = apply(path, original, updated)
            self.assertEqual(original, backup.read_bytes())
            self.assertEqual(updated, path.read_text())

    def test_changed_config_is_not_overwritten(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "ans.toml"
            path.write_text(CONFIG + "\n# changed")
            with self.assertRaises(ValueError):
                apply(path, CONFIG.encode(), CONFIG)
            self.assertTrue(path.read_text().endswith("# changed"))


if __name__ == "__main__":
    unittest.main()
