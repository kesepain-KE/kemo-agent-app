#!/usr/bin/env python3
"""Validate that an Android release, its source metadata and its docs agree.

The script intentionally uses only the Python standard library so the same
contract can run locally and on a clean GitHub Actions runner.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


SEMVER_PATTERN = re.compile(
    r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)"
    r"(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?"
    r"(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$"
)
STRING_PATTERN = re.compile(r'<string\s+name="([^"]+)"[^>]*>')


class ContractError(RuntimeError):
    """Raised when one or more release invariants do not hold."""


@dataclass(frozen=True)
class AppVersion:
    version_name: str
    version_code: int


def _read(path: Path) -> str:
    if not path.is_file():
        raise ContractError(f"required file is missing: {path}")
    return path.read_text(encoding="utf-8")


def read_app_version(root: Path) -> AppVersion:
    gradle = _read(root / "app" / "build.gradle.kts")
    name_matches = re.findall(r'\bversionName\s*=\s*"([^"]+)"', gradle)
    code_matches = re.findall(r"\bversionCode\s*=\s*(\d+)", gradle)
    if len(name_matches) != 1 or len(code_matches) != 1:
        raise ContractError("app/build.gradle.kts must declare one versionName and one versionCode")
    version = AppVersion(name_matches[0], int(code_matches[0]))
    if not SEMVER_PATTERN.fullmatch(version.version_name):
        raise ContractError(f"versionName is not SemVer: {version.version_name}")
    if version.version_code <= 0:
        raise ContractError("versionCode must be a positive integer")
    return version


def normalize_release_tag(tag: str) -> str:
    value = tag.strip()
    if value.startswith("refs/tags/"):
        value = value.removeprefix("refs/tags/")
    if value.startswith("kemo-v"):
        value = value.removeprefix("kemo-v")
    elif value.startswith("v"):
        value = value[1:]
    return value


def _string_names(xml: str) -> set[str]:
    return set(STRING_PATTERN.findall(xml))


def _expect_contains(errors: list[str], text: str, expected: str, location: str) -> None:
    if expected not in text:
        errors.append(f"{location} does not contain: {expected}")


def validate_source(root: Path, expected_tag: str = "") -> AppVersion:
    root = root.resolve()
    version = read_app_version(root)
    errors: list[str] = []

    if expected_tag:
        release_version = normalize_release_tag(expected_tag)
        if release_version != version.version_name:
            errors.append(
                f"release tag {expected_tag!r} resolves to {release_version!r}, "
                f"but versionName is {version.version_name!r}"
            )

    zh_path = root / "app" / "src" / "main" / "res" / "values" / "strings.xml"
    en_path = root / "app" / "src" / "main" / "res" / "values-en" / "strings.xml"
    zh = _read(zh_path)
    en = _read(en_path)
    _expect_contains(errors, zh, f'<string name="version">版本 {version.version_name}</string>', str(zh_path))
    _expect_contains(errors, en, f'<string name="version">Version {version.version_name}</string>', str(en_path))

    missing_en = sorted(_string_names(zh) - _string_names(en))
    missing_zh = sorted(_string_names(en) - _string_names(zh))
    if missing_en:
        errors.append(f"values-en/strings.xml is missing keys: {', '.join(missing_en)}")
    if missing_zh:
        errors.append(f"values/strings.xml is missing keys: {', '.join(missing_zh)}")

    readme = _read(root / "README.md")
    readme_en = _read(root / "README_EN.md")
    _expect_contains(errors, readme, f"version-{version.version_name}-blue", "README.md version badge")
    _expect_contains(
        errors,
        readme,
        f"当前版本：`{version.version_name}`（versionCode {version.version_code}）",
        "README.md current version",
    )
    _expect_contains(errors, readme_en, f"version-{version.version_name}-blue", "README_EN.md version badge")
    _expect_contains(
        errors,
        readme_en,
        f"Current version: `{version.version_name}` (versionCode {version.version_code})",
        "README_EN.md current version",
    )

    update_source = _read(
        root
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "kesepain"
        / "kemoapp"
        / "update"
        / "AppUpdateRepository.kt"
    )
    project_match = re.search(r'GITHUB_PROJECT_URL\s*=\s*"(https://github\.com/[^"/]+/[^"/]+)"', update_source)
    api_match = re.search(r'LATEST_RELEASE_API\s*=\s*"https://api\.github\.com/repos/([^"/]+/[^"/]+)/releases/latest"', update_source)
    if not project_match or not api_match:
        errors.append("AppUpdateRepository must declare the GitHub project URL and latest-release API")
    else:
        project_url = project_match.group(1)
        project_slug = project_url.removeprefix("https://github.com/")
        if api_match.group(1) != project_slug:
            errors.append("AppUpdateRepository project URL and latest-release API point to different repositories")
        for name, document in (("README.md", readme), ("README_EN.md", readme_en)):
            _expect_contains(errors, document, project_url, f"{name} project link")

    for required in ("LICENSE", "gradlew", "gradlew.bat", "docs/RELEASING.md"):
        if not (root / required).is_file():
            errors.append(f"required release file is missing: {required}")

    if errors:
        raise ContractError("\n".join(f"- {error}" for error in errors))
    return version


def validate_output_metadata(root: Path, metadata_path: Path, version: AppVersion) -> None:
    path = metadata_path if metadata_path.is_absolute() else root / metadata_path
    try:
        payload = json.loads(_read(path))
    except json.JSONDecodeError as exc:
        raise ContractError(f"invalid APK output metadata: {path}: {exc}") from exc
    elements = payload.get("elements")
    if not isinstance(elements, list) or not elements:
        raise ContractError(f"APK output metadata has no elements: {path}")
    errors: list[str] = []
    if payload.get("applicationId") != "com.kesepain.kemoapp":
        errors.append(f"unexpected applicationId: {payload.get('applicationId')!r}")
    for element in elements:
        if element.get("versionName") != version.version_name:
            errors.append(
                f"{element.get('outputFile', 'APK')} versionName is {element.get('versionName')!r}; "
                f"expected {version.version_name!r}"
            )
        if element.get("versionCode") != version.version_code:
            errors.append(
                f"{element.get('outputFile', 'APK')} versionCode is {element.get('versionCode')!r}; "
                f"expected {version.version_code}"
            )
    if errors:
        raise ContractError("\n".join(f"- {error}" for error in errors))


def validate_apk_values(
    version: AppVersion,
    apk_version_name: str,
    apk_version_code: str,
    apk_application_id: str,
) -> None:
    errors: list[str] = []
    if apk_version_name and apk_version_name.strip() != version.version_name:
        errors.append(f"released APK versionName is {apk_version_name!r}; expected {version.version_name!r}")
    if apk_version_code:
        try:
            code = int(apk_version_code.strip())
        except ValueError:
            errors.append(f"released APK versionCode is not an integer: {apk_version_code!r}")
        else:
            if code != version.version_code:
                errors.append(f"released APK versionCode is {code}; expected {version.version_code}")
    if apk_application_id and apk_application_id.strip() != "com.kesepain.kemoapp":
        errors.append(f"released APK applicationId is {apk_application_id!r}; expected 'com.kesepain.kemoapp'")
    if errors:
        raise ContractError("\n".join(f"- {error}" for error in errors))


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd(), help="repository root")
    parser.add_argument("--expected-tag", default="", help="release tag, for example v1.1.2")
    parser.add_argument("--metadata", type=Path, help="Gradle output-metadata.json to verify")
    parser.add_argument("--apk-version-name", default="", help="versionName read from a released APK")
    parser.add_argument("--apk-version-code", default="", help="versionCode read from a released APK")
    parser.add_argument("--apk-application-id", default="", help="applicationId read from a released APK")
    return parser


def main(argv: Iterable[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        version = validate_source(args.root, args.expected_tag)
        if args.metadata:
            validate_output_metadata(args.root.resolve(), args.metadata, version)
        if args.apk_version_name or args.apk_version_code or args.apk_application_id:
            validate_apk_values(
                version,
                args.apk_version_name,
                args.apk_version_code,
                args.apk_application_id,
            )
    except ContractError as exc:
        print(f"RELEASE CONTRACT FAILED\n{exc}", file=sys.stderr)
        return 1
    print(
        "RELEASE CONTRACT PASSED: "
        f"versionName={version.version_name}, versionCode={version.version_code}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
