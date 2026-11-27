"""The offline mapping validator against the fixtures both loaders' unit tests run, plus its CLI."""
from decimal import Decimal
import io
import json
from pathlib import Path
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
import zipfile

import validate_glyph_mappings as vgm

FIXTURES = Path(__file__).resolve().parents[1] / 'src/test/resources/ans_shared_fixtures'


def fixture(path: str):
    return vgm.loads((FIXTURES / path).read_text(encoding='utf-8'))


def sources(case: dict) -> list:
    return [vgm.Source(f['id'], f['pack'], int(f['rank']), f['json']) for f in case.get('files', [])]


def split(key: str) -> tuple:
    spell, level = key.split('@')
    return spell, int(level)


class SharedFixtureTest(unittest.TestCase):
    """The same expectations the Forge and NeoForge SharedMappingFixturesTest assert."""

    def test_every_shared_mapping_fixture(self):
        catalog = vgm.read_catalogs([FIXTURES / 'catalog.json'])
        cases = fixture('mappings/index.json')['cases']
        self.assertGreaterEqual(len(cases), 16, 'the shared fixture set shrank')
        for name in cases:
            with self.subTest(name):
                case = fixture(f'mappings/{name}.json')
                snapshot = vgm.build(sources(case), catalog)
                expect = case['expect']
                self.assertEqual(expect['valid'], snapshot.valid, snapshot.errors)
                for fragment in expect.get('errors_contain', []):
                    self.assertIn(fragment, '\n'.join(snapshot.errors))
                for spell in expect.get('failed_closed', []):
                    self.assertIn(spell, snapshot.failed_closed)
                for key, allowed in expect.get('allows', {}).items():
                    self.assertEqual(allowed, snapshot.allows(*split(key)), key)
                for key, tier in expect.get('tier', {}).items():
                    self.assertEqual(int(tier), snapshot.tier_override(*split(key)), key)
                for key, want in expect.get('terms', {}).items():
                    terms = snapshot.press_terms(*split(key), int(want['tier']))
                    self.assertEqual(int(want['source_cost']), terms.source_cost, key)
                    self.assertEqual(int(want['duration_ticks']), terms.duration_ticks, key)
                    self.assertEqual(int(want['catalyst_count']), terms.catalyst_count, key)
                    if 'catalyst_tag' in want:
                        self.assertEqual(want['catalyst_tag'], terms.catalyst_tag, key)
                for spell in expect.get('self_only', []):
                    self.assertTrue(snapshot.self_only(spell), spell)
                if 'catalyst_tags' in expect:
                    self.assertEqual(set(expect['catalyst_tags']), snapshot.catalyst_tags())

    def test_the_live_school_decides_school_policy(self):
        catalog = vgm.read_catalogs([FIXTURES / 'catalog.json'])
        case = fixture('levels/policy_follows_the_live_school.json')
        snapshot = vgm.build(sources(case), catalog)
        self.assertTrue(snapshot.valid, snapshot.errors)
        self.assertFalse(snapshot.allows('irons_spellbooks:heal', 1))
        self.assertTrue(snapshot.allows('irons_spellbooks:heal', 1, 'irons_spellbooks:ender'))


class StrictnessTest(unittest.TestCase):
    def setUp(self):
        self.catalog = vgm.read_catalogs([FIXTURES / 'catalog.json'])

    def errors(self, document: dict) -> list:
        return vgm.build([vgm.Source('test:x', 'p', 1, vgm.loads(json.dumps(document)))], self.catalog).errors

    def test_numbers_are_checked_before_narrowing(self):
        spell = {'schema_version': 2, 'spell': 'irons_spellbooks:firebolt'}
        self.assertIn('outside 0..100000', self.errors({**spell, 'levels': {'1': {'source_cost': -1}}})[0])
        self.assertIn('outside 1..72000', self.errors({**spell, 'levels': {'1': {'duration_ticks': 0}}})[0])
        self.assertIn('expected a whole number in 0..64', self.errors({**spell, 'levels': {'1': {'catalyst': {'count': True}}}})[0])
        self.assertEqual([], self.errors({**spell, 'levels': {'1': {'source_cost': 2.0}}}))
        self.assertIn('not a level number', self.errors({**spell, 'levels': {'01': {}}})[0])
        self.assertEqual(Decimal('1E+400'), vgm.loads('1e400'))
        huge = vgm.build([vgm.Source('test:x', 'p', 1, vgm.loads(
            '{"schema_version": 2, "spell": "irons_spellbooks:firebolt", "levels": {"1": {"source_cost": 1e400}}}'))], self.catalog)
        self.assertIn('is outside 0..100000', huge.errors[0])

    def test_non_finite_constants_are_not_numbers(self):
        with self.assertRaises(ValueError):
            vgm.loads('{"source_cost": NaN}')

    def test_an_adapter_may_be_confirmed_or_restricted_never_changed(self):
        spell = {'schema_version': 2, 'spell': 'irons_spellbooks:firebolt'}
        self.assertEqual([], self.errors({**spell, 'adapter': 'ars_n_spells:projectile'}))
        self.assertEqual([], self.errors({**spell, 'adapter': 'ars_n_spells:self'}))
        self.assertIn('never change it', self.errors({**spell, 'adapter': 'ars_n_spells:summon'})[0])

    def test_unknown_spells_and_types_are_reported(self):
        self.assertIn('is not in the imported-glyph catalog', self.errors({'schema_version': 2, 'spell': 'test:nothing'})[0])
        self.assertIn('unknown document type', self.errors({'schema_version': 2, 'type': 'ars_n_spells:other'})[0])
        self.assertIn('policy denies unknown spell test:nothing',
                      self.errors({'schema_version': 2, 'type': 'ars_n_spells:policy', 'deny_spells': ['test:nothing']})[0])

    def test_identical_tiers_at_one_priority_agree_and_different_ones_conflict(self):
        tier = {'schema_version': 2, 'type': 'ars_n_spells:press_tiers',
                'tiers': {'1': {'source_cost': 10, 'duration_ticks': 20, 'catalyst': {'count': 0}}}}
        other = json.loads(json.dumps(tier))
        other['tiers']['1']['source_cost'] = 11
        same = [vgm.Source(f'test:{n}', 'p', 1, vgm.loads(json.dumps(tier))) for n in 'ab']
        self.assertEqual([], vgm.build(same, self.catalog).errors)
        clash = [vgm.Source('test:a', 'p', 1, vgm.loads(json.dumps(tier))), vgm.Source('test:b', 'p', 1, vgm.loads(json.dumps(other)))]
        snapshot = vgm.build(clash, self.catalog)
        self.assertIn('declared differently', snapshot.errors[0])
        self.assertEqual(vgm.DEFAULT_TIERS[1], snapshot.tiers[1])


class CommandLineTest(unittest.TestCase):
    def run_main(self, *args) -> tuple:
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            code = vgm.main([str(a) for a in args])
        return code, out.getvalue(), err.getvalue()

    def write(self, root: Path, name: str, document: dict):
        path = root / 'data' / 'test' / vgm.DIRECTORY / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(document), encoding='utf-8')

    def test_a_valid_pack_passes_and_a_broken_one_names_its_pack_file_and_field(self):
        with tempfile.TemporaryDirectory() as tmp:
            good, bad = Path(tmp) / 'good', Path(tmp) / 'bad'
            self.write(good, 'bolt.json', {'schema_version': 2, 'spell': 'irons_spellbooks:firebolt', 'levels': {'2': {'ars_tier': 3}}})
            self.write(bad, 'heal.json', {'schema_version': 2, 'spell': 'irons_spellbooks:heal', 'levels': {'1': {'source_cost': 10.5}}})
            catalog = FIXTURES / 'catalog.json'
            code, out, _ = self.run_main('--catalog', catalog, good)
            self.assertEqual(0, code, out)
            self.assertIn('valid', out)
            code, out, _ = self.run_main('--catalog', catalog, good, bad)
            self.assertEqual(1, code, out)
            self.assertIn('test:heal [bad]: levels.1.source_cost: expected a whole number, got 10.5', out)
            code, out, _ = self.run_main('--catalog', catalog, '--json', good, bad)
            report = json.loads(out)
            self.assertFalse(report['valid'])
            self.assertEqual(['irons_spellbooks:heal'], report['failed_closed'])

    def test_a_later_pack_overrides_an_earlier_one_and_zips_are_read(self):
        with tempfile.TemporaryDirectory() as tmp:
            low = Path(tmp) / 'low'
            self.write(low, 'bolt.json', {'schema_version': 2, 'spell': 'irons_spellbooks:firebolt', 'enabled': False})
            high = Path(tmp) / 'high.zip'
            with zipfile.ZipFile(high, 'w') as archive:
                archive.writestr(f'data/test/{vgm.DIRECTORY}/bolt.json',
                                 json.dumps({'schema_version': 2, 'spell': 'irons_spellbooks:firebolt', 'enabled': True}))
            code, out, _ = self.run_main('--catalog', FIXTURES / 'catalog.json', '--json', low, high)
            self.assertEqual(0, code, out)
            self.assertIn('overrides 1 lower-priority file', json.loads(out)['winners'][0])

    def test_malformed_json_is_a_problem_and_a_missing_pack_is_unusable_input(self):
        with tempfile.TemporaryDirectory() as tmp:
            pack = Path(tmp) / 'pack'
            path = pack / 'data' / 'test' / vgm.DIRECTORY / 'broken.json'
            path.parent.mkdir(parents=True)
            path.write_text('{"schema_version": 2,', encoding='utf-8')
            code, out, _ = self.run_main('--catalog', FIXTURES / 'catalog.json', pack)
            self.assertEqual(1, code)
            self.assertIn('test:broken [pack]: not valid JSON', out)
            code, _, err = self.run_main('--catalog', FIXTURES / 'catalog.json', Path(tmp) / 'missing')
            self.assertEqual(2, code)
            self.assertIn('not a data pack', err)

    def test_the_bundled_catalog_is_the_default(self):
        with tempfile.TemporaryDirectory() as tmp:
            pack = Path(tmp) / 'pack'
            self.write(pack, 'bolt.json', {'schema_version': 2, 'spell': 'irons_spellbooks:firebolt', 'enabled': False})
            code, out, _ = self.run_main(pack)
            self.assertEqual(0, code, out)


if __name__ == '__main__':
    unittest.main()
