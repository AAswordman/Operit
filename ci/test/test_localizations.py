from __future__ import annotations

import sys
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))

from check_localizations import (  # noqa: E402
    ResourceEntry,
    Snapshot,
    locale_from_path,
    parse_locale_config,
    placeholder_mismatch,
    select_blocking_issues,
)


def entry(name: str, text: str) -> ResourceEntry:
    return ResourceEntry(name=name, tag="string", text=text, attributes=(), items=())


def strings_path(locale: str) -> str:
    if locale == "zh":
        return "app/src/main/res/values/strings.xml"
    parts = locale.split("-")
    if len(parts) == 1:
        qualifier = locale
    elif len(parts) == 2 and (
        (len(parts[1]) == 2 and parts[1].isupper())
        or (len(parts[1]) == 3 and parts[1].isdigit())
    ):
        qualifier = f"{parts[0]}-r{parts[1]}"
    else:
        qualifier = f"b+{locale.replace('-', '+')}"
    return f"app/src/main/res/values-{qualifier}/strings.xml"


def snapshot(entries: dict[str, dict[str, ResourceEntry]]) -> Snapshot:
    files = {locale: strings_path(locale) for locale in entries}
    tags = {"zh" if locale == "zh" else locale for locale in entries}
    blobs = {path: repr(entries[locale]).encode() for locale, path in files.items()}
    blobs["app/src/main/res/xml/locales_config.xml"] = repr(tags).encode()
    return Snapshot(
        files=files,
        entries=entries,
        localized_files={
            path: (locale, entries[locale]) for locale, path in files.items()
        },
        localized_duplicates={path: set() for path in files.values()},
        parse_errors={},
        locale_tags=tags,
        config_error=None,
        blobs=blobs,
    )


class LocalizationAttributionTest(unittest.TestCase):
    def test_existing_other_locale_mismatch_does_not_block_new_locale(self) -> None:
        base = snapshot(
            {
                "zh": {"welcome": entry("welcome", "Hello %1$s")},
                "en": {"welcome": entry("welcome", "Hello")},
            }
        )
        candidate = snapshot(
            {
                **base.entries,
                "ro": {"welcome": entry("welcome", "Salut")},
            }
        )

        blocking, existing_count = select_blocking_issues(base, candidate)

        self.assertEqual([issue.locale for issue in blocking], ["ro"])
        self.assertEqual(existing_count, 1)

    def test_source_change_rechecks_existing_translations(self) -> None:
        base = snapshot(
            {
                "zh": {"welcome": entry("welcome", "Hello")},
                "en": {"welcome": entry("welcome", "Hello")},
            }
        )
        candidate = snapshot(
            {
                "zh": {"welcome": entry("welcome", "Hello %1$s")},
                "en": {"welcome": entry("welcome", "Hello")},
            }
        )

        blocking, _ = select_blocking_issues(base, candidate)

        self.assertEqual([issue.locale for issue in blocking], ["en"])

    def test_array_placeholders_are_compared_by_index(self) -> None:
        source = ResourceEntry(
            name="steps",
            tag="string-array",
            text="",
            attributes=(),
            items=(("0", "%1$s"), ("1", "%2$s")),
        )
        target = ResourceEntry(
            name="steps",
            tag="string-array",
            text="",
            attributes=(),
            items=(("0", "%1$s"), ("1", "%1$s")),
        )

        self.assertTrue(placeholder_mismatch(source, target))

    def test_one_key_does_not_promote_other_errors_in_same_locale(self) -> None:
        base = snapshot(
            {
                "zh": {
                    "first": entry("first", "First %1$s"),
                    "second": entry("second", "Second %1$s"),
                },
                "en": {
                    "first": entry("first", "First"),
                    "second": entry("second", "Second"),
                },
            }
        )
        candidate = snapshot(
            {
                "zh": base.entries["zh"],
                "en": {
                    "first": entry("first", "First %1$s"),
                    "second": entry("second", "Second"),
                },
            }
        )

        blocking, existing_count = select_blocking_issues(base, candidate)

        self.assertEqual(blocking, [])
        self.assertEqual(existing_count, 1)

    def test_changed_locale_config_blocks_the_remaining_parse_error(self) -> None:
        base = snapshot({"zh": {"welcome": entry("welcome", "Hello")}})
        candidate = snapshot(base.entries)
        base.config_error = "invalid locale config: duplicate locale entries"
        candidate.config_error = base.config_error
        base.blobs["app/src/main/res/xml/locales_config.xml"] = b"duplicate en"
        candidate.blobs["app/src/main/res/xml/locales_config.xml"] = b"duplicate fr"

        blocking, _ = select_blocking_issues(base, candidate)

        self.assertEqual([issue.code for issue in blocking], ["locale-config"])

    def test_combined_locale_qualifier_is_checked_independently(self) -> None:
        base = snapshot(
            {
                "zh": {"welcome": entry("welcome", "Hello %1$s")},
                "en": {"welcome": entry("welcome", "Hello %1$s")},
            }
        )
        candidate = snapshot(base.entries)
        variant_path = "app/src/main/res/values-en-night/strings.xml"
        candidate.localized_files[variant_path] = (
            "en",
            {"welcome": entry("welcome", "Hello")},
        )
        candidate.localized_duplicates[variant_path] = set()
        candidate.blobs[variant_path] = b"night variant"

        blocking, _ = select_blocking_issues(base, candidate)

        self.assertEqual([issue.path for issue in blocking], [variant_path])
        self.assertEqual(locale_from_path(variant_path), "en")

    def test_android_locale_qualifier_forms_are_normalized(self) -> None:
        self.assertEqual(
            locale_from_path("app/src/main/res/values-pt-rBR/strings.xml"),
            "pt-BR",
        )
        self.assertEqual(
            locale_from_path("app/src/main/res/values-pt-rBR-night/strings.xml"),
            "pt-BR",
        )
        self.assertEqual(
            locale_from_path("app/src/main/res/values-b+sr+Latn/strings.xml"),
            "sr-Latn",
        )
        self.assertEqual(
            locale_from_path("app/src/main/res/values-b+sr+Latn-night/strings.xml"),
            "sr-Latn",
        )


class LocaleConfigParserTest(unittest.TestCase):
    def test_wrong_root_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "root element"):
            parse_locale_config(b"<resources />")

    def test_unknown_child_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "unsupported"):
            parse_locale_config(b"<locale-config><item /></locale-config>")

    def test_locale_without_name_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "android:name"):
            parse_locale_config(b"<locale-config><locale /></locale-config>")

    def test_unlisted_default_locale_is_rejected(self) -> None:
        content = b"""\
<locale-config xmlns:android="http://schemas.android.com/apk/res/android"
               android:defaultLocale="fr">
  <locale android:name="en" />
</locale-config>
"""
        with self.assertRaisesRegex(ValueError, "default locale is not listed"):
            parse_locale_config(content)


if __name__ == "__main__":
    unittest.main()
