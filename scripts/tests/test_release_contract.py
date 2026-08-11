from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from scripts.release_contract import (
    AppVersion,
    ContractError,
    normalize_release_tag,
    validate_apk_values,
    validate_output_metadata,
    validate_source,
)


class ReleaseContractTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self._write("app/build.gradle.kts", 'versionCode = 2\nversionName = "1.1.0"\n')
        self._write("app/src/main/res/values/strings.xml", '<string name="version">版本 1.1.0</string>\n')
        self._write("app/src/main/res/values-en/strings.xml", '<string name="version">Version 1.1.0</string>\n')
        project_url = "https://github.com/example/kemo-agent-app"
        self._write(
            "app/src/main/java/com/kesepain/kemoapp/update/AppUpdateRepository.kt",
            f'''const val GITHUB_PROJECT_URL = "{project_url}"
private const val LATEST_RELEASE_API = "https://api.github.com/repos/example/kemo-agent-app/releases/latest"
''',
        )
        self._write(
            "README.md",
            f"{project_url}\nversion-1.1.0-blue\n当前版本：`1.1.0`（versionCode 2）\n",
        )
        self._write(
            "README_EN.md",
            f"{project_url}\nversion-1.1.0-blue\nCurrent version: `1.1.0` (versionCode 2)\n",
        )
        for name in ("LICENSE", "gradlew", "gradlew.bat", "docs/RELEASING.md"):
            self._write(name, "test\n")

    def tearDown(self) -> None:
        self.temp.cleanup()

    def _write(self, relative: str, content: str) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def test_aligned_source_and_release_tag_pass(self) -> None:
        self.assertEqual(validate_source(self.root, "refs/tags/v1.1.0"), AppVersion("1.1.0", 2))

    def test_release_tag_mismatch_fails(self) -> None:
        with self.assertRaisesRegex(ContractError, "release tag"):
            validate_source(self.root, "v1.2.0")

    def test_localized_string_key_mismatch_fails(self) -> None:
        self._write(
            "app/src/main/res/values/strings.xml",
            '<string name="version">版本 1.1.0</string><string name="only_zh">仅中文</string>\n',
        )
        with self.assertRaisesRegex(ContractError, "missing keys: only_zh"):
            validate_source(self.root)

    def test_apk_output_metadata_mismatch_fails(self) -> None:
        metadata = self.root / "output-metadata.json"
        metadata.write_text(
            json.dumps(
                {
                    "applicationId": "com.kesepain.kemoapp",
                    "elements": [{"versionName": "1.0.0", "versionCode": 1, "outputFile": "app.apk"}],
                }
            ),
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ContractError, "versionName"):
            validate_output_metadata(self.root, metadata, AppVersion("1.1.0", 2))

    def test_released_apk_identity_mismatch_fails(self) -> None:
        with self.assertRaisesRegex(ContractError, "applicationId"):
            validate_apk_values(AppVersion("1.1.0", 2), "1.1.0", "2", "invalid.package")

    def test_tag_normalization_only_removes_ref_and_v_prefix(self) -> None:
        self.assertEqual(normalize_release_tag("refs/tags/v1.1.0"), "1.1.0")
        self.assertEqual(normalize_release_tag("refs/tags/kemo-v1.1.0"), "1.1.0")
        self.assertEqual(normalize_release_tag("1.1.0-rc.1"), "1.1.0-rc.1")


if __name__ == "__main__":
    unittest.main()
