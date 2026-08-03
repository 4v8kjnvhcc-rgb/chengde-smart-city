# -*- coding: utf-8 -*-
"""从系统功能清单 V2.6 生成 Mxxx 实现映射表 Markdown。"""
from __future__ import annotations

import argparse
import re
import sys
from collections import Counter
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CHECKLIST_PATH = ROOT / "docs" / "D05-系统功能清单.md"
OUT_PATH = ROOT / "docs" / "D06-Mxxx实现映射表.md"

# 需求基线附录 B：V3 板块（块级）
V3_BLOCKS: list[tuple[str, str, int, int]] = [
    ("1.1", "大数据归集平台", 39, 77),
    ("1.2", "服务总线", 1, 19),
    ("1.3", "应用平台", 20, 38),  # M020-M030 + M037-M038（脚本按区间并集处理）
    ("1.4", "应用分析门户", 31, 36),
    ("2.1", "大数据融合治理平台", 78, 122),
    ("2.2", "非结构数据融合治理平台", 123, 129),
    ("2.3", "大数据平台资源中心", 130, 138),
    ("3.1", "通用支撑+智能BI", 139, 151),
    ("3.2", "业务支撑平台", 152, 209),
    ("跨平台", "跨平台公共能力", 210, 215),
]

# 1.3 特殊：M020-M030 与 M037-M038
V3_EXTRA_RANGES: dict[str, list[tuple[int, int]]] = {
    "1.3": [(20, 30), (37, 38)],
}

IMPLEMENTATION_RULES: dict[str, dict[str, str]] = {
    "外购·AEAI ESB": {
        "framework": "AEAI ESB",
        "deploy": "VM1/ESB",
        "portal": "M214代理/深链",
        "code_pkg": "integration/esb",
        "dev_notes": "见 ESB集成说明；凭证 esb.env.local",
    },
    "集成+自研·ESB适配(M214)": {
        "framework": "ESB + 自研",
        "deploy": "VM1",
        "portal": "门户代理",
        "code_pkg": "backend/integration/esb",
        "dev_notes": "Mock/真实 ESB 切换；终验须真实环境",
    },
    "开源集成·OpenMetadata": {
        "framework": "OpenMetadata 1.12.x",
        "deploy": "VM1",
        "portal": "门户代理+扩展",
        "code_pkg": "integration/openmetadata",
        "dev_notes": "门户代理 OM；菜单名映射 V3.0",
    },
    "集成+自研·OpenMetadata": {
        "framework": "OpenMetadata 1.12.x",
        "deploy": "VM1",
        "portal": "门户代理+扩展",
        "code_pkg": "integration/openmetadata",
        "dev_notes": "审批/机构/质量定制",
    },
    "集成+自研·DS+OpenMetadata": {
        "framework": "DolphinScheduler + OpenMetadata",
        "deploy": "VM1",
        "portal": "门户代理+扩展",
        "code_pkg": "integration/openmetadata + integration/ds",
        "dev_notes": "DS 调度联动 OM 治理任务",
    },
    "开源集成·DolphinScheduler": {
        "framework": "DolphinScheduler 3.x",
        "deploy": "VM1",
        "portal": "门户代理",
        "code_pkg": "integration/ds",
        "dev_notes": "调度联动",
    },
    "开源集成·DataEase(iframe)": {
        "framework": "DataEase",
        "deploy": "VM2",
        "portal": "iframe+SSO",
        "code_pkg": "integration/dataease",
        "dev_notes": "不改源码 iframe；GPL 法务待确认",
    },
    "集成+自研·DataEase(iframe)": {
        "framework": "DataEase",
        "deploy": "VM2",
        "portal": "iframe+SSO",
        "code_pkg": "integration/dataease",
        "dev_notes": "指标/SQL 样例自研",
    },
    "开源集成·Kettle": {
        "framework": "Kettle 9.4.x",
        "deploy": "VM1",
        "portal": "自研调度页",
        "code_pkg": "integration/kettle",
        "dev_notes": "治理 ETL；不覆盖 M011～M013",
    },
    "开源集成·Elasticsearch": {
        "framework": "Elasticsearch 8.x",
        "deploy": "VM2",
        "portal": "检索页",
        "code_pkg": "search/es",
        "dev_notes": "与 OpenMetadata 共用 ES",
    },
    "开源集成·SeaweedFS": {
        "framework": "SeaweedFS 3.x",
        "deploy": "VM2",
        "portal": "文件页",
        "code_pkg": "storage/seaweedfs",
        "dev_notes": "S3 协议",
    },
    "开源集成·Canal": {
        "framework": "Canal 1.1.x",
        "deploy": "VM2",
        "portal": "CDC配置页",
        "code_pkg": "ingestion/cdc",
        "dev_notes": "MySQL binlog",
    },
    "开源集成·MongoDB": {
        "framework": "MongoDB 7.x",
        "deploy": "VM2",
        "portal": "半结构化页",
        "code_pkg": "ingestion/mongo",
        "dev_notes": "半结构化占位",
    },
    "纯自研": {
        "framework": "自研 Spring Boot + Vue3",
        "deploy": "VM1",
        "portal": "原生页面",
        "code_pkg": "",  # 按逻辑域填充
        "dev_notes": "政务/等保",
    },
}

PURE_SELF_CODE: dict[str, str] = {
    "L1.1": "backend/ingestion",
    "L1.3": "backend/application",
    "L1.4": "backend/application",
    "L2.1": "backend/governance",
    "L2.2": "backend/unstructured",
    "L2.3": "backend/resource-center",
    "L3.1": "backend/analysis",
    "L3.2": "backend/analysis",
    "跨平台": "backend/platform",
}

OVERRIDES: dict[str, dict[str, str]] = {
    "M036": {
        "dev_notes": "8 子态势大屏+跨域汇总；非独立 M161～M209",
    },
    "M048": {
        "dev_notes": "双重授权：系统管理员不直接授数据访问权；跨部门须审批",
    },
    "M099": {
        "dev_notes": "Kettle 治理 ETL；MS2 前确认路径；不覆盖 M011～M013",
    },
    "M158": {
        "dev_notes": "双重授权：与 M048/M211 一致；三角色场景可验证",
    },
    "M181": {
        "dev_notes": "法人域双重授权：同 M158 机制",
    },
    "M210": {"code_pkg": "backend/system"},
    "M211": {"code_pkg": "backend/system", "dev_notes": "统一系统管理；双重授权/等保"},
    "M212": {"code_pkg": "backend/hub"},
    "M213": {"code_pkg": "backend/hub"},
    "M214": {
        "dev_notes": "Mock/真实 ESB 切换；终验须真实环境；代理 M001～M019",
    },
    "M215": {
        "dev_notes": "仅 M099 治理 ETL；不覆盖 M011～M013",
    },
}

# 框架索引（与开源评估一致的分组键）
FRAMEWORK_INDEX_ORDER = [
    ("AEAI ESB（外购）", lambda r: r["implementation"] == "外购·AEAI ESB"),
    ("ESB 适配层（M214）", lambda r: r["m"] == "M214"),
    (
        "OpenMetadata",
        lambda r: "OpenMetadata" in r["framework"] and r["m"] != "M098",
    ),
    (
        "DataEase",
        lambda r: "DataEase" in r["implementation"],
    ),
    ("DolphinScheduler", lambda r: r["implementation"] == "开源集成·DolphinScheduler"),
    ("DS+OM（M098）", lambda r: r["m"] == "M098"),
    ("Kettle", lambda r: r["implementation"] == "开源集成·Kettle"),
    ("Canal", lambda r: r["implementation"] == "开源集成·Canal"),
    ("MongoDB", lambda r: r["implementation"] == "开源集成·MongoDB"),
    ("SeaweedFS", lambda r: r["implementation"] == "开源集成·SeaweedFS"),
    ("Elasticsearch", lambda r: r["implementation"] == "开源集成·Elasticsearch"),
]


def m_num(m_id: str) -> int:
    return int(m_id[1:])


def v3_block_for(m_id: str) -> str:
    n = m_num(m_id)
    for block_id, _name, start, end in V3_BLOCKS:
        if block_id == "1.3":
            for s, e in V3_EXTRA_RANGES["1.3"]:
                if s <= n <= e:
                    return f"{block_id}"
        elif start <= n <= end:
            return block_id
    return "—"


def v3_block_label(block_id: str) -> str:
    for bid, name, _, _ in V3_BLOCKS:
        if bid == block_id:
            return f"{bid} {name}"
    return block_id


def parse_checklist(path: Path) -> list[dict]:
    lines = path.read_text(encoding="utf-8").splitlines()
    rows: list[dict] = []
    for line in lines:
        if not re.match(r"^\| M\d{3} \|", line):
            continue
        if re.match(r"^\|\s*[-:]+", line):
            continue
        parts = [p.strip() for p in line.strip().strip("|").split("|")]
        if len(parts) < 7:
            continue
        rows.append(
            {
                "m": parts[0],
                "logic_domain": parts[1],
                "name": parts[2],
                "level": parts[5],
                "implementation": parts[6],
            }
        )
    rows.sort(key=lambda r: m_num(r["m"]))
    return rows


def apply_rules(row: dict) -> dict:
    impl = row["implementation"]
    rules = IMPLEMENTATION_RULES.get(impl)
    if not rules:
        raise ValueError(f"未定义实现方式规则: {impl} ({row['m']})")
    out = {
        **row,
        "v3_block": v3_block_for(row["m"]),
        "framework": rules["framework"],
        "deploy": rules["deploy"],
        "portal": rules["portal"],
        "code_pkg": rules["code_pkg"],
        "dev_notes": rules["dev_notes"],
    }
    if impl == "纯自研":
        out["code_pkg"] = PURE_SELF_CODE.get(
            row["logic_domain"], "backend/platform"
        )
    overrides = OVERRIDES.get(row["m"], {})
    for k, v in overrides.items():
        out[k] = v
    return out


def format_m_ranges(m_ids: list[str]) -> str:
    if not m_ids:
        return "—"
    nums = sorted(m_num(m) for m in m_ids)
    ranges: list[str] = []
    start = prev = nums[0]
    for n in nums[1:]:
        if n == prev + 1:
            prev = n
            continue
        ranges.append(
            f"M{start:03d}" if start == prev else f"M{start:03d}～M{prev:03d}"
        )
        start = prev = n
    ranges.append(
        f"M{start:03d}" if start == prev else f"M{start:03d}～M{prev:03d}"
    )
    return "、".join(ranges)


def build_framework_index(rows: list[dict]) -> list[tuple[str, int, str]]:
    index: list[tuple[str, int, str]] = []
    covered: set[str] = set()
    for label, pred in FRAMEWORK_INDEX_ORDER:
        ids = [r["m"] for r in rows if pred(r)]
        if ids:
            covered.update(ids)
            index.append((label, len(ids), format_m_ranges(ids)))
    pure_ids = [r["m"] for r in rows if r["m"] not in covered]
    index.append(("自研平台", len(pure_ids), "其余模块"))
    return index


def impl_stats(rows: list[dict]) -> list[tuple[str, int]]:
    c = Counter(r["implementation"] for r in rows)
    # 合并「其他开源集成」用于简表
    order = [
        "纯自研",
        "集成+自研·DataEase(iframe)",
        "外购·AEAI ESB",
        "集成+自研·OpenMetadata",
        "开源集成·OpenMetadata",
        "开源集成·DataEase(iframe)",
        "开源集成·Elasticsearch",
        "开源集成·SeaweedFS",
        "开源集成·MongoDB",
        "开源集成·Kettle",
        "开源集成·DolphinScheduler",
        "开源集成·Canal",
        "集成+自研·DS+OpenMetadata",
        "集成+自研·ESB适配(M214)",
    ]
    stats: list[tuple[str, int]] = []
    other = 0
    for key in order:
        if c[key]:
            stats.append((key, c[key]))
    for k, v in c.items():
        if k not in order:
            other += v
    if other:
        stats.append(("其他", other))
    return stats


def category_totals(rows: list[dict]) -> dict[str, int]:
    """与开源框架选型评估 §三 对齐的四类汇总。"""
    pure = sum(1 for r in rows if r["implementation"] == "纯自研")
    outsource = sum(1 for r in rows if r["implementation"] == "外购·AEAI ESB")
    oss_prefix = "开源集成·"
    oss = sum(1 for r in rows if r["implementation"].startswith(oss_prefix))
    integrated = 215 - pure - outsource - oss
    return {
        "外购": outsource,
        "开源集成": oss,
        "集成+自研": integrated,
        "纯自研": pure,
    }


def validate(rows: list[dict], checklist_rows: list[dict]) -> list[str]:
    errors: list[str] = []
    if len(rows) != 215:
        errors.append(f"行数应为 215，实际 {len(rows)}")
    expected = {r["m"] for r in checklist_rows}
    actual = {r["m"] for r in rows}
    if expected != actual:
        errors.append(f"M 编号缺失/多余: 缺 {expected - actual} 多 {actual - expected}")
    checklist_map = {r["m"]: r["implementation"] for r in checklist_rows}
    for r in rows:
        if checklist_map[r["m"]] != r["implementation"]:
            errors.append(
                f"{r['m']} 实现方式不一致: 清单={checklist_map[r['m']]} 映射={r['implementation']}"
            )
    totals = category_totals(rows)
    # 与清单 V2.6 明细一致（开源评估 §三 写 35 为四舍五入/历史口径，明细加总为 33）
    expected_totals = {"外购": 19, "开源集成": 33, "集成+自研": 68, "纯自研": 95}
    for k, v in expected_totals.items():
        if totals[k] != v:
            errors.append(f"类型汇总 {k}: 期望 {v} 实际 {totals[k]}")
    if sum(expected_totals.values()) != 215:
        errors.append("四类汇总之和不等于 215")
    return errors


def render_markdown(rows: list[dict]) -> str:
    today = date.today().isoformat()
    stats = impl_stats(rows)
    index = build_framework_index(rows)
    totals = category_totals(rows)

    lines = [
        "# Mxxx 实现映射表",
        "",
        "| 属性 | 说明 |",
        "|------|------|",
        "| **文档编号** | **D06** |",
        "| **文档版本** | V1.0（终稿） |",
        f"| **编制日期** | {today} |",
        "| **配套清单** | [`D05-系统功能清单.md`](D05-系统功能清单.md) V2.6 |",
        "| **框架选型** | [`D04-开源框架选型评估.md`](D04-开源框架选型评估.md) V1.0 |",
        "| **ESB** | [`D03-ESB集成说明.md`](D03-ESB集成说明.md)；SMC `http://10.10.10.61:7000` |",
        "| **凭证** | 复制 `esb.env.example` → `esb.env.local`（**勿提交 Git**） |",
        "| **Excel** | [`Mxxx实现映射表_V1.0.xlsx`](mapping/Mxxx实现映射表_V1.0.xlsx) |",
        "| **模块总数** | 215 |",
        "",
        "---",
        "",
        "## 一、列说明",
        "",
        "| 列名 | 说明 |",
        "|------|------|",
        "| **M** | 验收/合同引用编号 |",
        "| **V3板块** | V3.0 块级板块（基线附录 B）；非 Word 叶子章节 |",
        "| **实现方式** | 与功能清单 V2.6 一致 |",
        "| **框架/组件** | 外购/开源/自研产品 |",
        "| **部署** | VM1：平台+ESB+OM+DS+Kettle；VM2：ES+DataEase+Canal 等 |",
        "| **门户集成** | 甲方须从统一门户进入 |",
        "| **代码包** | 规划路径（`platform-backend` / `platform-frontend`） |",
        "| **二次开发要点** | 适配或自研重点 |",
        "",
        "---",
        "",
        "## 二、实现方式统计",
        "",
        "### 2.1 按清单实现方式（明细）",
        "",
        "| 实现方式 | 数量 |",
        "|----------|------|",
    ]
    for name, count in stats:
        lines.append(f"| {name} | {count} |")
    lines.extend(
        [
            "",
            "### 2.2 按开源评估四类汇总",
            "",
            "| 类型 | 数量 | 说明 |",
            "|------|------|------|",
            f"| 外购 | {totals['外购']} | M001～M019 |",
            f"| 开源集成 | {totals['开源集成']} | 以框架能力为主（清单明细加总；评估文档写 35 为口径差异） |",
            f"| 集成+自研 | {totals['集成+自研']} | 框架底座 + 定制 |",
            f"| 纯自研 | {totals['纯自研']} | 政务业务与等保 |",
            "",
            "---",
            "",
            "## 三、M001～M215 完整映射表",
            "",
            "| M | 功能模块 | 逻辑域 | L | V3板块 | 实现方式 | 框架/组件 | 部署 | 门户集成 | 代码包 | 二次开发要点 |",
            "|---|----------|--------|---|--------|----------|-----------|------|----------|--------|--------------|",
        ]
    )
    for r in rows:
        v3 = v3_block_label(r["v3_block"])
        lines.append(
            f"| {r['m']} | {r['name']} | {r['logic_domain']} | {r['level']} | {v3} | "
            f"{r['implementation']} | {r['framework']} | {r['deploy']} | {r['portal']} | "
            f"{r['code_pkg']} | {r['dev_notes']} |"
        )
    lines.extend(
        [
            "",
            "---",
            "",
            "## 四、框架 → M 编号索引",
            "",
            "| 框架/组件 | 模块数 | M 编号 |",
            "|-----------|--------|--------|",
        ]
    )
    for label, count, ranges in index:
        lines.append(f"| {label} | {count} | {ranges} |")
    lines.extend(
        [
            "",
            "---",
            "",
            "## 五、修订记录",
            "",
            "| 版本 | 日期 | 说明 |",
            "|------|------|------|",
            f"| V1.0（终稿） | {today} | 215 模块完整映射；增补 V3板块列；脚本可再生 |",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description="生成 Mxxx 实现映射表")
    parser.add_argument(
        "--check",
        action="store_true",
        help="仅校验，不写文件",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=OUT_PATH,
        help="输出 Markdown 路径",
    )
    args = parser.parse_args()

    checklist_rows = parse_checklist(CHECKLIST_PATH)
    rows = [apply_rules(r) for r in checklist_rows]
    errors = validate(rows, checklist_rows)
    if errors:
        print("校验失败:")
        for e in errors:
            print(f"  - {e}")
        return 1
    print("校验通过: 215 模块，实现方式与清单一致，四类汇总 19/33/68/95")
    if args.check:
        return 0
    content = render_markdown(rows)
    args.out.write_text(content, encoding="utf-8")
    print(f"已写入: {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
