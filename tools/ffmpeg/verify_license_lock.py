#!/usr/bin/env python3
"""Validate FFmpeg source and license evidence locks without resolving the network."""

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from pathlib import Path


REVIEW_STATUSES = {
    "combined-review",
    "configuration-dependent",
    "needs-review",
    "policy-review",
}
ALLOWED_STATUSES = REVIEW_STATUSES | {"verified"}
ALLOWED_ROLES = {"build-only", "runtime"}


@dataclass(frozen=True)
class LicenseEvidence:
    component: str
    expression: str
    role: str
    status: str
    evidence_path: str
    evidence_sha256: str


def read_components(path: Path) -> set[str]:
    components: set[str] = set()
    for line_number, raw_line in enumerate(path.read_text().splitlines(), 1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("|")
        if len(parts) != 4:
            raise ValueError(f"{path}:{line_number}: expected four fields")
        component, _repository, _upstream_ref, commit = parts
        if component in components:
            raise ValueError(f"{path}:{line_number}: duplicate component {component}")
        if not re.fullmatch(r"[0-9a-f]{40}", commit):
            raise ValueError(f"{path}:{line_number}: invalid commit for {component}")
        components.add(component)
    return components


def read_license_evidence(path: Path) -> dict[str, LicenseEvidence]:
    evidence: dict[str, LicenseEvidence] = {}
    for line_number, raw_line in enumerate(path.read_text().splitlines(), 1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("|")
        if len(parts) != 6:
            raise ValueError(f"{path}:{line_number}: expected six fields")
        component, expression, role, status, evidence_path, sha256 = parts
        if component in evidence:
            raise ValueError(f"{path}:{line_number}: duplicate component {component}")
        if not expression:
            raise ValueError(f"{path}:{line_number}: empty license expression")
        if role not in ALLOWED_ROLES:
            raise ValueError(f"{path}:{line_number}: invalid role {role}")
        if status not in ALLOWED_STATUSES:
            raise ValueError(f"{path}:{line_number}: invalid status {status}")
        evidence_file = Path(evidence_path)
        if evidence_file.is_absolute() or ".." in evidence_file.parts:
            raise ValueError(f"{path}:{line_number}: unsafe evidence path {evidence_path}")
        if not re.fullmatch(r"[0-9a-f]{64}", sha256):
            raise ValueError(f"{path}:{line_number}: invalid evidence SHA-256")
        evidence[component] = LicenseEvidence(*parts)
    return evidence


def validate(source_lock: Path, license_lock: Path) -> list[LicenseEvidence]:
    source_components = read_components(source_lock)
    evidence = read_license_evidence(license_lock)
    license_components = set(evidence)
    if source_components != license_components:
        missing = sorted(source_components - license_components)
        extra = sorted(license_components - source_components)
        raise ValueError(
            f"source/license component mismatch; missing={missing}, extra={extra}"
        )
    return sorted(
        (item for item in evidence.values() if item.status in REVIEW_STATUSES),
        key=lambda item: item.component,
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    script_dir = Path(__file__).resolve().parent
    parser.add_argument(
        "--source-lock",
        type=Path,
        default=script_dir / "source-lock.properties",
    )
    parser.add_argument(
        "--license-lock",
        type=Path,
        default=script_dir / "license-lock.properties",
    )
    args = parser.parse_args()

    review_items = validate(args.source_lock.resolve(), args.license_lock.resolve())
    print("FFmpeg source/license lock component coverage is complete.")
    if review_items:
        print("License review remains required for:")
        for item in review_items:
            print(f"- {item.component}: {item.status} ({item.expression})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())