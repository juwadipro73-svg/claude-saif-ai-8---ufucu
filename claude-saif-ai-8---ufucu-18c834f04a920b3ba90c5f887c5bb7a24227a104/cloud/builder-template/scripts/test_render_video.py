import tempfile
from pathlib import Path
import unittest
from render_video import build_command


class RenderVideoCommandTest(unittest.TestCase):
    def test_text_and_private_image_have_correct_input_indices(self):
        with tempfile.TemporaryDirectory() as root:
            asset_id = '123e4567-e89b-42d3-a456-426614174001'
            Path(root, asset_id).write_bytes(b'fake image; command construction only')
            project = {'width': 720, 'height': 1280, 'fps': 30, 'durationUs': 3_000_000,
                       'items': [
                           {'type': 'text', 'startUs': 0, 'durationUs': 3_000_000,
                            'text': "It's an image: safely escaped", 'fontSize': 30, 'color': '#ffffff'},
                           {'type': 'image', 'startUs': 1_000_000, 'durationUs': 1_000_000,
                            'assetId': asset_id}]}
            cmd = build_command(project, root, Path(root, 'video.mp4'), root)
            graph = cmd[cmd.index('-filter_complex') + 1]
            self.assertIn('[1:v]trim=', graph)
            self.assertIn('drawtext=', graph)
            self.assertIn('overlay=', graph)
            self.assertNotIn("It's an image", graph)  # user text is in a textfile, not ffmpeg expression
            self.assertTrue(Path(root, 'text_000.txt').exists())

    def test_media_is_not_auto_probed_as_a_playlist(self):
        with tempfile.TemporaryDirectory() as root:
            asset_id = '123e4567-e89b-42d3-a456-426614174003'
            Path(root, asset_id).write_text('#EXTM3U\nhttp://example.invalid/secret', encoding='utf-8')
            project = {'width': 720, 'height': 1280, 'fps': 30, 'durationUs': 1_000_000,
                       'items': [{'type': 'video', 'startUs': 0, 'durationUs': 1_000_000,
                                  'assetId': asset_id, 'sourceInUs': 0}]}
            with self.assertRaisesRegex(ValueError, 'declared type'):
                build_command(project, root, 'out.mp4', root, {asset_id: 'video/mp4'})
            Path(root, asset_id).write_bytes(b'\x89PNG\r\n\x1a\n' + b'0' * 8)
            project['items'][0]['type'] = 'image'
            cmd = build_command(project, root, 'out.mp4', root, {asset_id: 'image/png'})
            self.assertIn('-protocol_whitelist', cmd)
            self.assertEqual(cmd[cmd.index('-protocol_whitelist') + 1], 'file,pipe')
            self.assertIn('png', cmd)

    def test_studio_bitrate_tier_is_used_by_ffmpeg(self):
        with tempfile.TemporaryDirectory() as root:
            project = {'width': 720, 'height': 1280, 'fps': 30, 'durationUs': 1_000_000,
                       'bitrate': 18_000_000, 'items': [
                           {'type': 'text', 'startUs': 0, 'durationUs': 1_000_000,
                            'text': 'Studio', 'fontSize': 24, 'color': '#ffffff'}]}
            cmd = build_command(project, root, Path(root, 'video.mp4'), root)
            self.assertEqual(cmd[cmd.index('-b:v') + 1], '18000000')
            self.assertNotIn('-crf', cmd)
            project['bitrate'] = -1
            with self.assertRaises(ValueError):
                build_command(project, root, 'x.mp4', root)

    def test_rejects_bad_duration(self):
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaises(ValueError):
                build_command({'width': 720, 'height': 1280, 'fps': 30,
                               'durationUs': 99_999_999_999, 'items': []}, root, 'x.mp4', root)


if __name__ == '__main__':
    unittest.main()
