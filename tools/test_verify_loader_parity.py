import json
from pathlib import Path
import tempfile
import unittest
from verify_loader_parity import canonical, glyph_identity_exceptions, identities, ledger, resource_path

class LoaderParityNormalizationTest(unittest.TestCase):
    def test_registry_rename_preserves_nested_advancement_recipe_path(self):
        self.assertEqual('data/ars_n_spells/advancement/recipes/misc/spell_loom.json',
                         resource_path('data/ars_n_spells/advancements/recipes/misc/spell_loom.json'))

    def test_conditional_apparatus_forms_have_equal_semantics(self):
        forge = {'type': 'forge:conditional', 'recipes': [{
            'conditions': [{'type': 'forge:mod_loaded', 'modid': 'irons_spellbooks'}],
            'recipe': {'type': 'ars_nouveau:enchanting_apparatus', 'sourceCost': 1500,
                       'output': {'item': 'ars_n_spells:mana_infusion'},
                       'reagent': [{'item': 'ars_nouveau:blank_parchment'}]}}]}
        neo = {'type': 'ars_nouveau:enchanting_apparatus', 'sourceCost': 1500,
               'neoforge:conditions': [{'type': 'neoforge:mod_loaded', 'modid': 'irons_spellbooks'}],
               'result': {'id': 'ars_n_spells:mana_infusion', 'count': 1},
               'reagent': {'item': 'ars_nouveau:blank_parchment'}}
        self.assertEqual(canonical(forge), canonical(neo))
        neo['sourceCost'] = 1000
        self.assertNotEqual(canonical(forge), canonical(neo))
        neo['sourceCost'] = 1500
        neo['result']['count'] = 2
        self.assertNotEqual(canonical(forge), canonical(neo))

    def test_unknown_ids_and_existing_neoforge_ids_are_preserved(self):
        for value in ['neoforge:mod_loaded', 'neoforge:loot_table_id', 'other:forge:mod_loaded']:
            self.assertEqual(value, canonical(value))

    def test_multiple_recipe_alternatives_are_not_silently_dropped(self):
        with self.assertRaises(ValueError):
            canonical({'type': 'forge:conditional', 'recipes': [{}, {}]})

class GlyphIdentityParityTest(unittest.TestCase):
    def write(self, path: Path, data) -> Path:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(data if isinstance(data, str) else json.dumps(data), encoding='utf-8')
        return path

    def test_a_catalog_publishes_every_level_up_to_its_registered_maximum(self):
        with tempfile.TemporaryDirectory() as tmp:
            catalog = self.write(Path(tmp) / 'c.json', {'schema_version': 2, 'spells': [
                {'spell': 'a:bolt', 'levels': {'min': 1, 'default_max': 2, 'registered_max': 3}, 'school': 'a:fire',
                 'cast_type': 'INSTANT', 'adapter': 'ars_n_spells:projectile', 'status': 'retired'}]})
            ids, facts, adapters = identities(catalog)
            self.assertEqual({f'ars_n_spells:iron/a/bolt/level_{n}' for n in (1, 2, 3)}, ids)
            self.assertEqual('retired', facts['a:bolt']['status'])
            self.assertEqual('ars_n_spells:projectile', adapters['a:bolt'])
            self.assertEqual(['x:y'], ledger(self.write(Path(tmp) / 'l.txt', '# comment\n\nx:y\n')))

    def test_each_item_id_scheme_must_pair_one_model_and_one_name_per_identity(self):
        with tempfile.TemporaryDirectory() as tmp:
            forge, neo = Path(tmp) / 'forge', Path(tmp) / 'neo'
            ids = {'ars_n_spells:iron/a/bolt/level_1', 'ars_n_spells:iron/a/bolt/level_2'}
            for level in (1, 2):
                self.write(forge / f'src/main/resources/assets/ars_n_spells/models/item/iron/a/bolt/level_{level}.json', {})
                self.write(neo / f'src/main/resources/assets/ars_n_spells/models/item/glyph_iron/a/bolt/level_{level}.json', {})
            lang = neo / 'src/main/resources/assets/ars_n_spells/lang/en_us.json'
            self.write(lang, {f'ars_n_spells.glyph_name.iron/a/bolt/level_{n}': 'Bolt' for n in (1, 2)})
            exceptions, errors = glyph_identity_exceptions(forge, neo, ids)
            self.assertEqual([], errors)
            self.assertEqual(3, len(exceptions))
            (forge / 'src/main/resources/assets/ars_n_spells/models/item/iron/a/bolt/level_2.json').unlink()
            self.write(lang, {'ars_n_spells.glyph_name.iron/a/bolt/level_1': 'Bolt'})
            exceptions, errors = glyph_identity_exceptions(forge, neo, ids)
            self.assertEqual(2, len(errors), errors)
            self.assertIn('1 missing', errors[0])

if __name__ == '__main__': unittest.main()
