#!/usr/bin/env python3
"""Validate D05 / mapping / ingestion-nav / d05-modules consistency."""
from __future__ import annotations

import argparse
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG = os.path.join(ROOT, "catalog")
NAV_TS = os.path.join(ROOT, "platform-web", "src", "views", "exchange", "ingestion", "ingestion-nav.ts")
D05_MODULES = os.path.join(
    ROOT, "platform-backend", "src", "main", "resources", "catalog", "d05-modules.json"
)

# D05 §2.2 implMode by mCode (source of truth for collect scope)
D05_COLLECT_IMPL = {
    f"M{n:03d}": mode
    for n, mode in [
        (51, "纯自研"), (52, "纯自研"), (53, "纯自研"), (54, "纯自研"), (55, "纯自研"),
        (56, "纯自研"), (57, "开源集成·SeaweedFS"), (58, "开源集成·MongoDB"), (59, "纯自研"),
        (60, "开源集成·Canal"), (61, "纯自研"), (62, "纯自研"), (63, "纯自研"), (64, "纯自研"),
        (65, "纯自研"), (66, "纯自研"), (67, "纯自研"), (68, "纯自研"), (69, "纯自研"),
        (70, "纯自研"), (71, "纯自研"), (72, "开源集成·Elasticsearch"), (73, "纯自研"),
        (74, "纯自研"), (75, "纯自研"), (76, "纯自研"), (77, "纯自研"),
    ]
}

COLLECT_NAV_LABELS = {
    "ingest": "数据汇聚接入",
    "pipeline": "规范设计",
    "catalog": "指标与目录体系构建",
}


def load_json(path: str) -> dict:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def validate_collect(errors: list[str]) -> None:
    mapping_path = os.path.join(CATALOG, "v3-to-m215.collect.json")
    catalog_path = os.path.join(CATALOG, "v3-catalog.collect.json")
    if not os.path.isfile(mapping_path):
        errors.append(f"missing {mapping_path}")
        return

    mapping = load_json(mapping_path)
    modules = mapping.get("modules", [])
    m_codes = {m["mCode"] for m in modules}
    expected = {f"M{n:03d}" for n in range(51, 78)}
    if m_codes != expected:
        missing = expected - m_codes
        extra = m_codes - expected
        if missing:
            errors.append(f"collect mapping missing: {sorted(missing)}")
        if extra:
            errors.append(f"collect mapping extra: {sorted(extra)}")

    for m in modules:
        code = m["mCode"]
        if m.get("status") == "excluded":
            continue
        impl = m.get("implMode", "")
        if impl != D05_COLLECT_IMPL.get(code):
            errors.append(f"{code} implMode {impl!r} != D05 {D05_COLLECT_IMPL.get(code)!r}")
        if impl.startswith("集成+自研"):
            errors.append(f"{code} must not be 集成+自研 in collect scope")
        if impl.startswith("开源集成") and not m.get("ossProbe"):
            errors.append(f"{code} 开源集成 missing ossProbe")

    boundary = mapping.get("boundaryItems", [])
    if not any(b.get("moduleName") == "数据质量管控" for b in boundary):
        errors.append("boundaryItems missing xlsx row 27 数据质量管控")

    if os.path.isfile(catalog_path):
        catalog = load_json(catalog_path)
        xlsx_modules = {r["moduleName"] for r in catalog.get("rows", []) if r.get("moduleName")}
        # xlsx uses 数据汇聚 for aggregate; mapping uses D05 split names — skip strict 1:1

    if os.path.isfile(NAV_TS):
        nav_text = open(NAV_TS, encoding="utf-8").read()
        for key, label in COLLECT_NAV_LABELS.items():
            pat = rf"key:\s*'{key}'[^}}]*label:\s*'([^']+)'"
            m = re.search(pat, nav_text)
            if not m:
                errors.append(f"ingestion-nav missing COLLECT_MODULES key={key}")
            elif m.group(1) != label:
                errors.append(f"ingestion-nav {key} label {m.group(1)!r} != {label!r}")

    if os.path.isfile(D05_MODULES):
        d05 = load_json(D05_MODULES)
        flat = d05.get("modules", [])
        by_code = {m["mCode"]: m for m in flat if "mCode" in m}
        for mod in modules:
            code = mod["mCode"]
            if mod.get("status") == "excluded":
                continue
            if code not in by_code:
                errors.append(f"d05-modules.json missing {code}")
                continue
            if by_code[code].get("moduleName") != mod.get("moduleName"):
                errors.append(
                    f"{code} moduleName mismatch mapping vs d05-modules"
                )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--scope", default="collect", choices=["collect"])
    args = parser.parse_args()
    errors: list[str] = []
    if args.scope == "collect":
        validate_collect(errors)
    if errors:
        for e in errors:
            print(f"[FAIL] {e}", file=sys.stderr)
        return 1
    print(f"[PASS] validate_d05_consistency --scope {args.scope}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
