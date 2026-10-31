import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from prepare_release import prepare


class PrepareReleaseTest(unittest.TestCase):
    def run_prepare(self, root, code=25, version="0.5.17", tag="v0.5.17", badging=None):
        apk = root / "input.apk"
        apk.write_bytes(b"APK fixture")
        return prepare(apk, dict(versionCode=code, versionName=version), badging or
                       "package: name='com.v4ex.android' versionCode='25' versionName='0.5.17'", tag,
                       "ginuim/v4ex", "release notes", root / "dist")

    def test_manifest_verified_asset_and_metadata_refer_to_same_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            meta = self.run_prepare(root)
            filename = "V4EX-0.5.17-25.apk"
            self.assertEqual(filename, meta["apkFileName"])
            self.assertTrue(meta["apkUrl"].endswith("/v0.5.17/" + filename))
            self.assertEqual(meta, json.loads((root / "dist/update.json").read_text()))
            self.assertEqual(hashlib.sha256((root / "dist" / filename).read_bytes()).hexdigest(), meta["sha256"])
            self.assertEqual(meta["sha256"] + "  " + filename + "\n", (root / "dist" / (filename + ".sha256")).read_text())

    def test_rejects_tag_or_manifest_version_mismatches(self):
        for changes in [dict(tag="v0.5.16"), dict(code=24), dict(version="0.5.16", tag="v0.5.16"),
                        dict(badging="package: name='other.app' versionCode='25' versionName='0.5.17'")]:
            with self.subTest(changes=changes), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                with self.assertRaises(ValueError):
                    self.run_prepare(root, **changes)
                self.assertFalse((root / "dist/update.json").exists())


if __name__ == "__main__":
    unittest.main()
