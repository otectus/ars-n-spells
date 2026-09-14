import tempfile
import unittest
import zipfile
from pathlib import Path

from covenant_profile import REQUIRED, inspect


class CovenantProfileTest(unittest.TestCase):
    def jar(self, directory, mod, version="1.0", dependency=None):
        path = directory / (mod + '.jar')
        text = 'modLoader="javafml"\n[[mods]]\nmodId="' + mod + '"\nversion="' + version + '"\n'
        if dependency:
            text += '[[dependencies.' + mod + ']]\nmodId="' + dependency + '"\nmandatory=true\nversionRange="[1,)"\n'
        with zipfile.ZipFile(path, 'w') as z:
            z.writestr('META-INF/mods.toml', text)
            z.writestr('META-INF/MANIFEST.MF', 'Implementation-Version: 1.20.1-81-FORGE\n')
        return path

    def test_empty_directory_explains_required_graph(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(ValueError, 'Covenant.*Blood Magic'):
                inspect(Path(tmp))

    def test_complete_graph_records_versions_hashes_and_stays_unverified(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            for mod in REQUIRED:
                self.jar(directory, mod, '${file.jarVersion}' if mod == 'patchouli' else '1.0')
            result = inspect(directory)
            self.assertEqual('prepared_runtime_unverified', result['status'])
            self.assertEqual('1.20.1-81-FORGE', result['versions']['patchouli'])
            self.assertEqual(6, len(result['jars']))
            self.assertTrue(all(len(row['sha256']) == 64 for row in result['jars']))
            self.assertFalse((directory / 'covenant-profile.json').exists())

    def test_incomplete_and_unresolved_transitive_graph_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            self.jar(directory, 'covenant_of_the_seven', dependency='missing_library')
            with self.assertRaisesRegex(ValueError, 'missing_library'):
                inspect(directory)

    def test_base_dependency_duplicates_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            self.jar(directory, 'curios')
            with self.assertRaisesRegex(ValueError, 'Duplicate/base'):
                inspect(directory)

    def test_duplicate_mod_identity_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            self.jar(directory, 'bloodmagic')
            (directory / 'duplicate.jar').write_bytes((directory / 'bloodmagic.jar').read_bytes())
            with self.assertRaisesRegex(ValueError, 'Duplicate/base'):
                inspect(directory)

    def test_unresolved_jar_version_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            self.jar(directory, 'bloodmagic', '${unresolved}')
            with self.assertRaisesRegex(ValueError, 'Unresolved mod identity'):
                inspect(directory)


if __name__ == '__main__':
    unittest.main()
