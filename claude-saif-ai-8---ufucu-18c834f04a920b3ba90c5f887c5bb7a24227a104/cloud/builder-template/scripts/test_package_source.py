import tempfile
import unittest
import zipfile
from pathlib import Path

from package_source import package_source


class PackageSourceTest(unittest.TestCase):
    def test_one_zip_preserves_sources_but_not_build_cache_or_symlinks(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "project"
            (root / "app/src/main/assets").mkdir(parents=True)
            (root / "app/src/main/assets/index.html").write_text("<html>source</html>")
            (root / "settings.gradle.kts").write_text("// fixed template")
            (root / "app/build").mkdir(parents=True)
            (root / "app/build/temp.apk").write_bytes(b"not-source")
            (root / ".gradle").mkdir()
            (root / ".gradle/secret.txt").write_text("not-source")
            (root / "app/src/main/assets/external.txt").symlink_to(root / "../../private")
            output = Path(tmp) / "source.zip"
            self.assertEqual(package_source(root, output), 2)
            with zipfile.ZipFile(output) as archive:
                self.assertEqual(set(archive.namelist()), {
                    "app/src/main/assets/index.html", "settings.gradle.kts"})
                self.assertEqual(archive.read("app/src/main/assets/index.html"), b"<html>source</html>")

    def test_rejects_output_inside_source_and_too_many_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "project"
            root.mkdir()
            with self.assertRaises(ValueError):
                package_source(root, root / "out.zip")
            for i in range(201):
                (root / f"file{i}.txt").write_text("sample")
            with self.assertRaises(ValueError):
                package_source(root, Path(tmp) / "source.zip")


if __name__ == "__main__":
    unittest.main()
