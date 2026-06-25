#!/usr/bin/env python3
"""Write a small JSON manifest that represents the latest deployed release."""

from __future__ import annotations

import argparse
from datetime import UTC, datetime
import json
from pathlib import Path
import sys


def utcnow_iso() -> str:
    return datetime.now(tz=UTC).isoformat().replace("+00:00", "Z")


def parse_metadata(items: list[str]) -> dict[str, str]:
    parsed: dict[str, str] = {}
    for item in items:
        if "=" not in item:
            raise ValueError(f"metadata must be key=value, got: {item}")
        key, value = item.split("=", 1)
        key = key.strip()
        if not key:
            raise ValueError(f"metadata key is blank: {item}")
        parsed[key] = value.strip()
    return parsed


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Write deployed release manifest JSON.")
    parser.add_argument("--output", required=True, help="Path to output JSON manifest")
    parser.add_argument("--environment", required=True, choices=["dev", "prod"])
    parser.add_argument("--branch", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--deployed-at", default=utcnow_iso())
    parser.add_argument("--build-number", default="-")
    parser.add_argument("--build-url", default="-")
    parser.add_argument("--services", nargs="*", default=[])
    parser.add_argument("--metadata", nargs="*", default=[])
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "environment": args.environment,
        "branch": args.branch,
        "commit": args.commit,
        "deployed_at": args.deployed_at,
        "build_number": args.build_number,
        "build_url": args.build_url,
        "services": args.services,
        "metadata": parse_metadata(args.metadata),
    }
    output_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(output_path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
