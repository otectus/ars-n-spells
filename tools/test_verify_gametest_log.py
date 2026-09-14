import unittest

from verify_gametest_log import verify


class GameTestEvidenceTests(unittest.TestCase):
    LOG = """[INFO] All 88 required tests passed :)
[INFO] ANS-GAMETEST executed=53 skipped=10 byMod={ars_zero=7, toomanyglyphs=3}
[INFO] ANS-PROFILE mod=irons_spellbooks expected=present version=1.20.1-3.15.0
[INFO] ANS-PROFILE mod=ars_zero expected=absent version=<absent>
"""

    def check(self, log):
        return verify(log, required=88, executed=53, skipped=10,
                      present=["irons_spellbooks"], absent=["ars_zero"])

    def test_completed_expected_profile(self):
        self.assertEqual([], self.check(self.LOG))

    def test_zero_exit_boot_failure_cannot_pass(self):
        self.assertTrue(self.check("Failed to decode world\nBUILD SUCCESSFUL"))

    def test_missing_completion_report_cannot_pass(self):
        self.assertTrue(self.check(self.LOG.replace("All 88 required tests passed :)", "")))

    def test_mass_optional_skip_cannot_pass(self):
        self.assertTrue(self.check(self.LOG.replace("executed=53 skipped=10", "executed=1 skipped=62")))

    def test_missing_requested_mod_cannot_pass(self):
        self.assertTrue(self.check(self.LOG.replace("version=1.20.1-3.15.0", "version=<absent>")))

    def test_stale_or_concatenated_evidence_cannot_pass(self):
        self.assertTrue(self.check(self.LOG + self.LOG))

    def test_hidden_required_failure_cannot_pass(self):
        self.assertTrue(self.check(self.LOG + "1 required tests failed"))

    def test_changed_required_inventory_needs_review(self):
        self.assertTrue(self.check(self.LOG.replace("All 88", "All 87")))

    def test_upstream_missing_recipe_is_not_ans_failure(self):
        self.assertEqual([], self.check(self.LOG + "Unknown item 'minecraft:wind_charge'"))


if __name__ == "__main__":
    unittest.main()
