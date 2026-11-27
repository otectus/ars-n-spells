"""The icon generator's reproducibility contract (audit ANS-013): pixels decide, published bytes are kept."""
import io
import tempfile
import unittest
import zipfile
from pathlib import Path

from PIL import Image

import generate_icons as gi


def image(color=(200, 40, 40, 255)):
    im = Image.new('RGBA', (16, 16))
    im.putpixel((5, 5), color)
    return im


def encoded(im, level):
    out = io.BytesIO()
    im.save(out, format='PNG', optimize=False, compress_level=level)
    return out.getvalue()


def archive(members):
    data = io.BytesIO()
    with zipfile.ZipFile(data, 'w', compression=zipfile.ZIP_DEFLATED) as z:
        for name, content in members:
            z.writestr(name, content)
    return data.getvalue()


class KeepEncodedTest(unittest.TestCase):
    def test_compression_only_difference_keeps_the_published_bytes(self):
        published = encoded(image(), 0)
        self.assertNotEqual(published, gi.png(image()), 'fixture must differ in bytes only')
        self.assertEqual(published, gi.keep_encoded(published, image()))

    def test_one_pixel_change_is_encoded_fresh(self):
        published = encoded(image(), 9)
        changed = image((200, 40, 41, 255))
        kept = gi.keep_encoded(published, changed)
        self.assertNotEqual(published, kept)
        self.assertEqual(gi.image_pixels(changed), gi.pixels(kept))

    def test_mode_change_is_not_pixel_equal(self):
        rgb = Image.new('RGB', (16, 16))
        rgba = Image.new('RGBA', (16, 16), (0, 0, 0, 255))
        self.assertNotEqual(gi.pixels(gi.png(rgb)), gi.image_pixels(rgba))

    def test_missing_or_unreadable_published_file_is_encoded_fresh(self):
        self.assertEqual(gi.png(image()), gi.keep_encoded(None, image()))
        self.assertEqual(gi.png(image()), gi.keep_encoded(b'not a png', image()))


class ArchiveComparisonTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.path = Path(self.folder.name) / 'pack.zip'

    def tearDown(self):
        self.folder.cleanup()

    def test_container_metadata_does_not_matter(self):
        self.path.write_bytes(archive([('a.png', b'1'), ('b.json', b'2')]))
        data = io.BytesIO()
        with zipfile.ZipFile(data, 'w', compression=zipfile.ZIP_STORED) as z:
            z.writestr(zipfile.ZipInfo('a.png', (2001, 1, 1, 0, 0, 0)), b'1')
            z.writestr(zipfile.ZipInfo('b.json', (2001, 1, 1, 0, 0, 0)), b'2')
        self.assertTrue(gi.generated_asset_matches(self.path, data.getvalue()))

    def test_changed_extra_or_reordered_member_is_detected(self):
        self.path.write_bytes(archive([('a.png', b'1'), ('b.json', b'2')]))
        self.assertFalse(gi.generated_asset_matches(self.path, archive([('a.png', b'X'), ('b.json', b'2')])))
        self.assertFalse(gi.generated_asset_matches(self.path, archive([('a.png', b'1'), ('b.json', b'2'), ('c', b'3')])))
        self.assertFalse(gi.generated_asset_matches(self.path, archive([('b.json', b'2'), ('a.png', b'1')])))

    def test_published_members_are_read_for_reuse(self):
        published = encoded(image(), 1)
        self.path.write_bytes(archive([('x/a.png', published)]))
        view = gi.Published(Path(self.folder.name))
        self.assertEqual(published, view.member('pack.zip', 'x/a.png'))
        self.assertIsNone(view.member('pack.zip', 'x/missing.png'))
        self.assertIsNone(view.member('absent.zip', 'x/a.png'))


if __name__ == '__main__':
    unittest.main()
