import unittest
from verify_loader_parity import canonical, resource_path

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

if __name__ == '__main__': unittest.main()
