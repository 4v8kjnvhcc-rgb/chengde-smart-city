# -*- coding: utf-8 -*-
"""从 D05/D06 生成 D10 OpenMetadata 菜单名映射表。"""
from __future__ import annotations

import argparse
import re
import sys
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CHECKLIST_PATH = ROOT / "D05-系统功能清单.md"
D06_PATH = ROOT / "D06-Mxxx实现映射表.md"
OUT_PATH = ROOT / "D10-OM菜单名映射表.md"

# 28 个 OpenMetadata 模块 + M098（DS+OM）
OM_M_IDS = [
    "M078", "M079", "M080", "M082", "M083",
    "M086", "M087", "M088", "M089", "M090", "M091", "M092",
    "M093", "M094", "M095", "M096", "M097",
    "M112", "M113", "M114", "M115", "M116", "M117", "M118",
    "M119", "M120", "M121", "M122",
    "M098",
]

NON_OM_NEIGHBOR = [
    ("M081", "数据标准监控", "纯自研", "2.1.2 数据质量中心（自研扩展）"),
    ("M084", "数据标准体系管理", "纯自研", "2.1.2 标准体系"),
    ("M085", "标准映射与稽核联动", "纯自研", "2.1.2 标准体系"),
    ("M102", "数据元标准管理", "纯自研", "2.1.2 标准体系"),
    ("M103", "数据编码规范管理", "纯自研", "2.1.2 标准体系"),
    ("M104", "命名规范管理", "纯自研", "2.1.2 标准体系"),
    ("M105", "标准文件管理", "纯自研", "2.1.2 标准体系"),
    ("M099", "可视化 ETL 治理开发", "Kettle", "2.1.4 治理 ETL"),
    ("M100", "ETL 治理监控", "DolphinScheduler", "2.1.4 调度监控"),
    ("M101", "数据治理组件库", "纯自研", "2.1.4 自研组件库"),
    ("M106", "逻辑模型管理", "纯自研", "2.1.5 数据融合"),
    ("M107", "物理模型管理", "纯自研", "2.1.5 数据融合"),
    ("M108", "模型报告", "纯自研", "2.1.5 数据融合"),
    ("M109", "数据开发（脚本）", "纯自研", "2.1.5 数据融合"),
    ("M110", "工作流调度", "DolphinScheduler", "2.1.5 调度"),
    ("M111", "数据融合组件库", "纯自研", "2.1.5 数据融合"),
]

# 门户分组 → 菜单树编号
GROUP_META = {
    "quality": ("2.1.2 数据质量中心", "数据质量中心"),
    "metadata": ("2.1.1 元数据管理", "元数据管理"),
    "catalog": ("2.1.3 数据目录管理", "数据目录管理"),
    "governance": ("2.1.4 治理任务", "治理任务管理"),
}

OM_MENU_MAP: dict[str, dict[str, str]] = {
    "M078": {
        "group": "quality",
        "portal_route": "/governance/quality/rules",
        "om_native": "Quality > Test Definitions / Table DQ Tab",
        "om_path": "/data-quality /tables/{fqn}/profiler",
        "integration": "代理+自研扩展",
        "ms": "MS3",
        "notes": "8 类稽核规则中文化；机构维度扩展",
    },
    "M079": {
        "group": "quality",
        "portal_route": "/governance/quality/tasks",
        "om_native": "Quality > Test Suites > Schedule",
        "om_path": "/data-quality/test-suites",
        "integration": "代理+自研扩展",
        "ms": "MS3",
        "notes": "启停/告警联动自研工单",
    },
    "M080": {
        "group": "quality",
        "portal_route": "/governance/quality/monitor",
        "om_native": "Quality > By Test Suites",
        "om_path": "/data-quality",
        "integration": "代理+自研扩展",
        "ms": "MS3",
        "notes": "工单跟踪；血统分析扩展",
    },
    "M082": {
        "group": "quality",
        "portal_route": "/governance/quality/assessment",
        "om_native": "Quality > Test Results / Insights",
        "om_path": "/data-quality/test-case-results",
        "integration": "代理+自研扩展",
        "ms": "MS3",
        "notes": "六性指标绩效扩展",
    },
    "M083": {
        "group": "quality",
        "portal_route": "/governance/quality/reports",
        "om_native": "Quality + 自研报表",
        "om_path": "/api/v1/governance/quality/reports",
        "integration": "代理+自研扩展",
        "ms": "MS3",
        "notes": "多维度下钻导出；OM 结果 + 自研报表壳",
    },
    "M086": {
        "group": "metadata",
        "portal_route": "/governance/metadata/connectors",
        "om_native": "Settings > Connectors / Ingestion",
        "om_path": "/settings/integrations",
        "integration": "纯代理",
        "ms": "MS3",
        "notes": "内置与扩展连接器",
    },
    "M087": {
        "group": "metadata",
        "portal_route": "/governance/metadata/domains",
        "om_native": "Settings > Domains / Classifications",
        "om_path": "/settings/domains",
        "integration": "纯代理",
        "ms": "MS3",
        "notes": "类型与业务域映射 OM Domains",
    },
    "M088": {
        "group": "metadata",
        "portal_route": "/governance/metadata/services",
        "om_native": "Settings > Services > Databases",
        "om_path": "/settings/services/database",
        "integration": "纯代理",
        "ms": "MS3",
        "notes": "连接与生命周期",
    },
    "M089": {
        "group": "metadata",
        "portal_route": "/governance/metadata/types",
        "om_native": "Settings > Custom Properties / Types",
        "om_path": "/settings/customProperties",
        "integration": "纯代理",
        "ms": "MS3",
        "notes": "元模型发布导入导出",
    },
    "M090": {
        "group": "metadata",
        "portal_route": "/governance/metadata/ingestion",
        "om_native": "Settings > Services > Ingestion",
        "om_path": "/settings/services/ingestion",
        "integration": "纯代理",
        "ms": "MS3",
        "notes": "整库/选表；定时/增量采集",
    },
    "M091": {
        "group": "metadata",
        "portal_route": "/governance/metadata/ingestion/logs",
        "om_native": "Settings > Ingestion > Logs",
        "om_path": "/settings/services/ingestion/logs",
        "integration": "纯代理",
        "ms": "MS3",
        "notes": "状态、日志、停止",
    },
    "M092": {
        "group": "metadata",
        "portal_route": "/governance/metadata/explore",
        "om_native": "Explore > Tables > Edit Metadata",
        "om_path": "/explore/tables",
        "integration": "纯代理",
        "ms": "MS3",
        "notes": "自动/手工维护；沉淀标准",
    },
    "M093": {
        "group": "metadata",
        "portal_route": "/governance/metadata/versions",
        "om_native": "Explore > Entity Version History",
        "om_path": "/api/v1/metadata/version",
        "integration": "纯代理",
        "ms": "MS3",
        "notes": "发布/对比/订阅；版本以 OM + 自研补充",
    },
    "M094": {
        "group": "metadata",
        "portal_route": "/governance/metadata/export-import",
        "om_native": "Settings > Export / Import Metadata",
        "om_path": "/api/v1/metadata/export",
        "integration": "纯代理",
        "ms": "MS3",
        "notes": "跨环境迁移",
    },
    "M095": {
        "group": "metadata",
        "portal_route": "/governance/metadata/catalog",
        "om_native": "Explore > Search / Data Assets",
        "om_path": "/explore",
        "integration": "纯代理",
        "ms": "MS3",
        "notes": "数据源目录、资产目录",
    },
    "M096": {
        "group": "metadata",
        "portal_route": "/governance/metadata/lineage",
        "om_native": "Explore > Lineage Tab",
        "om_path": "/explore/tables/{fqn}/lineage",
        "integration": "纯代理",
        "ms": "MS3",
        "notes": "关联、血缘、影响分析",
    },
    "M097": {
        "group": "metadata",
        "portal_route": "/governance/metadata/glossary",
        "om_native": "Govern > Glossary",
        "om_path": "/glossary",
        "integration": "纯代理",
        "ms": "MS3",
        "notes": "平台字典映射 OM Glossary",
    },
    "M112": {
        "group": "catalog",
        "portal_route": "/governance/catalog/categories",
        "om_native": "Govern > Domains / Tags",
        "om_path": "/settings/domains",
        "integration": "代理+自研扩展",
        "ms": "MS3",
        "notes": "涉密分类；机构扩展",
    },
    "M113": {
        "group": "catalog",
        "portal_route": "/governance/catalog/data-assets",
        "om_native": "Explore + 自研编目",
        "om_path": "/api/v1/governance/catalog/assets",
        "integration": "代理+自研扩展",
        "ms": "MS3",
        "notes": "批量/手动编目",
    },
    "M114": {
        "group": "catalog",
        "portal_route": "/governance/catalog/service-assets",
        "om_native": "Explore > APIs / Services",
        "om_path": "/explore/apis",
        "integration": "代理+自研扩展",
        "ms": "MS3",
        "notes": "关联 API 服务编目",
    },
    "M115": {
        "group": "catalog",
        "portal_route": "/governance/catalog/publish",
        "om_native": "Govern + 自研发布",
        "om_path": "/api/v1/governance/catalog/publish",
        "integration": "代理+自研扩展",
        "ms": "MS3",
        "notes": "数据/服务目录发布",
    },
    "M116": {
        "group": "catalog",
        "portal_route": "/governance/catalog/approval",
        "om_native": "自研审批流",
        "om_path": "/api/v1/governance/catalog/approval",
        "integration": "代理+自研扩展",
        "ms": "MS3",
        "notes": "目录审批闭环；OM 无原生等价",
    },
    "M117": {
        "group": "catalog",
        "portal_route": "/governance/catalog/maintenance",
        "om_native": "Explore + 自研维护",
        "om_path": "/api/v1/governance/catalog/maintenance",
        "integration": "代理+自研扩展",
        "ms": "MS3",
        "notes": "查询与维护",
    },
    "M118": {
        "group": "catalog",
        "portal_route": "/governance/catalog/versions",
        "om_native": "Entity Version + 自研",
        "om_path": "/api/v1/governance/catalog/versions",
        "integration": "代理+自研扩展",
        "ms": "MS3",
        "notes": "变更历史对比",
    },
    "M119": {
        "group": "catalog",
        "portal_route": "/governance/catalog/portal",
        "om_native": "自研目录门户",
        "om_path": "/governance/catalog/portal",
        "integration": "代理+自研扩展",
        "ms": "MS3",
        "notes": "变更通知",
    },
    "M120": {
        "group": "catalog",
        "portal_route": "/governance/catalog/subscribe",
        "om_native": "自研订阅申请",
        "om_path": "/api/v1/governance/catalog/subscribe",
        "integration": "代理+自研扩展",
        "ms": "MS3",
        "notes": "库表/文件/API 申请",
    },
    "M121": {
        "group": "catalog",
        "portal_route": "/governance/catalog/subscribe-approval",
        "om_native": "自研订阅审批",
        "om_path": "/api/v1/governance/catalog/subscribe/approval",
        "integration": "代理+自研扩展",
        "ms": "MS3",
        "notes": "提供方审批",
    },
    "M122": {
        "group": "catalog",
        "portal_route": "/governance/catalog/distribute",
        "om_native": "自研订阅分发",
        "om_path": "/api/v1/governance/catalog/distribute",
        "integration": "代理+自研扩展",
        "ms": "MS3",
        "notes": "调用与测试",
    },
    "M098": {
        "group": "governance",
        "portal_route": "/governance/tasks",
        "om_native": "OM Governance + DolphinScheduler",
        "om_path": "/om/api/v1/governance/tasks + /ds/",
        "integration": "代理+自研扩展",
        "ms": "MS2",
        "notes": "调度执行→DS(/ds/)；任务元数据→OM API",
    },
}


def m_num(m_id: str) -> int:
    return int(m_id[1:])


def parse_checklist(path: Path) -> dict[str, str]:
    """M -> 功能模块名"""
    names: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not re.match(r"^\| M\d{3} \|", line):
            continue
        if re.match(r"^\|\s*[-:]+", line):
            continue
        parts = [p.strip() for p in line.strip().strip("|").split("|")]
        if len(parts) >= 3:
            names[parts[0]] = parts[2]
    return names


def parse_d06_impl(path: Path) -> dict[str, str]:
    impl: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not re.match(r"^\| M\d{3} \|", line):
            continue
        if re.match(r"^\|\s*[-:]+", line):
            continue
        parts = [p.strip() for p in line.strip().strip("|").split("|")]
        if len(parts) >= 6:
            impl[parts[0]] = parts[5]
    return impl


def portal_menu_name(group_key: str, module_name: str) -> str:
    _, block_name = GROUP_META[group_key]
    return module_name


def build_rows(names: dict[str, str], impl: dict[str, str]) -> list[dict]:
    rows: list[dict] = []
    for m_id in OM_M_IDS:
        if m_id not in OM_MENU_MAP:
            raise KeyError(f"OM_MENU_MAP 缺少 {m_id}")
        cfg = OM_MENU_MAP[m_id]
        group_key = cfg["group"]
        tree_id, parent_menu = GROUP_META[group_key]
        module_name = names.get(m_id, "—")
        rows.append(
            {
                "m": m_id,
                "module_name": module_name,
                "v3": "2.1 大数据融合治理平台",
                "portal_menu": portal_menu_name(group_key, module_name),
                "parent_menu": parent_menu,
                "tree_id": tree_id,
                "portal_route": cfg["portal_route"],
                "om_native": cfg["om_native"],
                "om_path": cfg["om_path"],
                "nginx": "location /om/ → VM1:8585",
                "integration": cfg["integration"],
                "code_pkg": (
                    "integration/openmetadata + integration/ds"
                    if m_id == "M098"
                    else "integration/openmetadata"
                ),
                "ms": cfg["ms"],
                "notes": cfg["notes"],
                "d06_impl": impl.get(m_id, "—"),
            }
        )
    return rows


def table_row(cells: list[str]) -> str:
    return "| " + " | ".join(cells) + " |"


def generate_md(rows: list[dict], today: str) -> str:
    lines = [
        "# OpenMetadata 菜单名映射表（Mxxx → 门户 / OM 1.12）",
        "",
        "| 属性 | 说明 |",
        "|------|------|",
        "| **文档编号** | **D10** |",
        "| **文档版本** | V1.0 |",
        f"| **编制日期** | {today} |",
        "| **文档性质** | MS2/MS3 OpenMetadata 门户集成**菜单名与路由契约** |",
        "| **模块范围** | **29 条**：OM 28 模块 + M098（DS+OM） |",
        "| **上位基线** | [`D02-需求基线说明.md`](D02-需求基线说明.md) V2.2 |",
        "| **配套文档** | [`D05-系统功能清单.md`](D05-系统功能清单.md) V2.6、[`D06-Mxxx实现映射表.md`](D06-Mxxx实现映射表.md) V1.0、[`D07-总体技术架构设计.md`](D07-总体技术架构设计.md) V1.1、[`D04-开源框架选型评估.md`](D04-开源框架选型评估.md)、[`D00-文档索引与编号规范.md`](D00-文档索引与编号规范.md) |",
        "",
        "---",
        "",
        "## 一、编制说明",
        "",
        "| 项 | 说明 |",
        "|----|------|",
        "| **核心要求** | 甲方在统一门户看到的菜单名与 V3.0 投标文件功能模块名 **1:1 对齐**；不得暴露 OM 原生英文 UI |",
        "| **代理入口** | Nginx `location /om/` → OpenMetadata `http://vm1:8585`（D07 附录 E） |",
        "| **集成模式** | `纯代理`：OM UI/API 经门户壳加载；`代理+自研扩展`：OM 能力 + 自研审批/机构/订阅等 |",
        "| **不在本表** | M081/M084～M085/M102～M105（自研标准）、M099～M101/M106～M111（Kettle/DS/自研）；见 §六 |",
        "| **实测路径** | OM 1.12 UI path 以 MS2 POC 部署后截图为准；本表为设计基准 |",
        "",
        "---",
        "",
        "## 二、门户侧菜单树（2.1 数据融合治理）",
        "",
        "挂载路径：`2 主数据平台 > 2.1 大数据融合治理平台`（D07 §5.6）",
        "",
        "```",
        "2.1 大数据融合治理平台",
        "├── 2.1.1 元数据管理",
        "│   ├── 适配器管理 … 字典管理（M086～M097）",
        "├── 2.1.2 数据质量中心",
        "│   ├── 质量规则配置 … 数据质量分析报告（M078～M080、M082～M083）",
        "├── 2.1.3 数据目录管理",
        "│   ├── 目录分类 … 资源订阅分发（M112～M122）",
        "└── 2.1.4 治理任务",
        "    └── 治理任务管理（M098）",
        "```",
        "",
        "---",
        "",
        "## 三、映射表列说明",
        "",
        "| 列名 | 说明 |",
        "|------|------|",
        "| M | 验收模块编号 |",
        "| 功能模块 | D05 模块名 |",
        "| 门户父菜单 | 二级菜单块（元数据/质量/目录/治理任务） |",
        "| 门户菜单名 | 甲方可见叶子菜单名（= 功能模块名） |",
        "| 门户路由 | `platform-web` 路由 |",
        "| OM 原生入口 | OpenMetadata 1.12 侧栏/页面 |",
        "| OM 路由/API | UI path 或 REST 前缀（部署后核对） |",
        "| 集成方式 | 纯代理 / 代理+自研扩展 |",
        "",
        "---",
        "",
        "## 四、逐条映射表",
        "",
        "### 4.1 数据质量中心（M078～M080、M082～M083）",
        "",
        table_row(
            [
                "M",
                "功能模块",
                "门户父菜单",
                "门户菜单名",
                "门户路由",
                "OM 原生入口",
                "OM 路由/API",
                "集成方式",
                "MS",
                "备注",
            ]
        ),
        table_row(["---"] * 10),
    ]

    for r in rows:
        if r["m"] not in ("M078", "M079", "M080", "M082", "M083"):
            continue
        lines.append(
            table_row(
                [
                    r["m"],
                    r["module_name"],
                    r["parent_menu"],
                    r["portal_menu"],
                    r["portal_route"],
                    r["om_native"],
                    r["om_path"],
                    r["integration"],
                    r["ms"],
                    r["notes"],
                ]
            )
        )

    lines.extend(
        [
            "",
            "### 4.2 元数据管理（M086～M097）",
            "",
            table_row(
                [
                    "M",
                    "功能模块",
                    "门户父菜单",
                    "门户菜单名",
                    "门户路由",
                    "OM 原生入口",
                    "OM 路由/API",
                    "集成方式",
                    "MS",
                    "备注",
                ]
            ),
            table_row(["---"] * 10),
        ]
    )

    for r in rows:
        n = m_num(r["m"])
        if n < 86 or n > 97:
            continue
        lines.append(
            table_row(
                [
                    r["m"],
                    r["module_name"],
                    r["parent_menu"],
                    r["portal_menu"],
                    r["portal_route"],
                    r["om_native"],
                    r["om_path"],
                    r["integration"],
                    r["ms"],
                    r["notes"],
                ]
            )
        )

    lines.extend(
        [
            "",
            "### 4.3 数据目录管理（M112～M122）",
            "",
            table_row(
                [
                    "M",
                    "功能模块",
                    "门户父菜单",
                    "门户菜单名",
                    "门户路由",
                    "OM 原生入口",
                    "OM 路由/API",
                    "集成方式",
                    "MS",
                    "备注",
                ]
            ),
            table_row(["---"] * 10),
        ]
    )

    for r in rows:
        n = m_num(r["m"])
        if n < 112 or n > 122:
            continue
        lines.append(
            table_row(
                [
                    r["m"],
                    r["module_name"],
                    r["parent_menu"],
                    r["portal_menu"],
                    r["portal_route"],
                    r["om_native"],
                    r["om_path"],
                    r["integration"],
                    r["ms"],
                    r["notes"],
                ]
            )
        )

    lines.extend(
        [
            "",
            "### 4.4 治理任务（M098）",
            "",
            table_row(
                [
                    "M",
                    "功能模块",
                    "门户父菜单",
                    "门户菜单名",
                    "门户路由",
                    "OM 原生入口",
                    "OM/DS 路由",
                    "集成方式",
                    "MS",
                    "备注",
                ]
            ),
            table_row(["---"] * 10),
        ]
    )

    r = next(x for x in rows if x["m"] == "M098")
    lines.append(
        table_row(
            [
                r["m"],
                r["module_name"],
                r["parent_menu"],
                r["portal_menu"],
                r["portal_route"],
                r["om_native"],
                r["om_path"],
                r["integration"],
                r["ms"],
                r["notes"],
            ]
        )
    )

    # §五 summary
    qual = sum(1 for r in rows if r["m"] in ("M078", "M079", "M080", "M082", "M083"))
    meta = sum(1 for r in rows if 86 <= m_num(r["m"]) <= 97)
    cat = sum(1 for r in rows if 112 <= m_num(r["m"]) <= 122)
    lines.extend(
        [
            "",
            "---",
            "",
            "## 五、功能域汇总",
            "",
            "| 门户分组 | 模块数 | M 范围 |",
            "|----------|--------|--------|",
            f"| 数据质量中心 | {qual} | M078～M080、M082～M083 |",
            f"| 元数据管理 | {meta} | M086～M097 |",
            f"| 数据目录管理 | {cat} | M112～M122 |",
            "| 治理任务 | 1 | M098 |",
            f"| **合计** | **{len(rows)}** | — |",
            "",
            "---",
            "",
            "## 六、非 OM 邻域模块索引（2.1 内不在本表）",
            "",
            "| M | 功能模块 | 实现方式 | 门户归属 |",
            "|---|----------|----------|----------|",
        ]
    )

    for m_id, name, impl, portal in NON_OM_NEIGHBOR:
        lines.append(table_row([m_id, name, impl, portal]))

    lines.extend(
        [
            "",
            "---",
            "",
            "## 七、配置与验收",
            "",
            "### 7.1 application.yml（摘录）",
            "",
            "```yaml",
            "integration:",
            "  openmetadata:",
            "    base-url: http://vm1:8585",
            "    menu-mapping-file: classpath:om-menu-mapping.json  # 可由本表导出",
            "  dolphinscheduler:",
            "    base-url: http://vm1:12345  # M098 调度侧",
            "```",
            "",
            "### 7.2 验收对齐",
            "",
            "| 项 | 说明 |",
            "|----|------|",
            "| D08 | **TC-P1-OM-001**（MS3）：数据源接入 + Schema 发现 |",
            "| D04 MS2 | 门户登录 → 按本表菜单进入 → 对照 D05 验收要点 |",
            "| 脚本校验 | `python scripts/gen_om_menu_mapping.py --check` 行数 = 29 |",
            "",
            "---",
            "",
            "## 八、修订记录",
            "",
            "| 版本 | 日期 | 说明 |",
            "|------|------|------|",
            f"| V1.0 | {today} | 初版：29 条 OM 菜单映射；脚本可再生 |",
            "",
            "---",
            "",
            f"*文档结束 — D10 与 D06 OpenMetadata 索引（28+1）配套；全表 Excel 见 `D10-OM菜单名映射表_V1.0.xlsx`。*",
            "",
        ]
    )

    return "\n".join(lines)


def validate_d06_om(impl: dict[str, str]) -> None:
    om_impl_keys = {
        k for k, v in impl.items()
        if "OpenMetadata" in v and k != "M098"
    }
    expected = {m for m in OM_M_IDS if m != "M098"}
    if om_impl_keys != expected:
        missing = expected - om_impl_keys
        extra = om_impl_keys - expected
        if missing or extra:
            print(f"警告: D06 OM 模块与 OM_M_IDS 不一致 missing={missing} extra={extra}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="仅校验不写入")
    parser.add_argument("--out", type=Path, default=OUT_PATH)
    args = parser.parse_args()

    names = parse_checklist(CHECKLIST_PATH)
    impl = parse_d06_impl(D06_PATH)
    validate_d06_om(impl)

    rows = build_rows(names, impl)
    if len(rows) != 29:
        print(f"错误: 行数 {len(rows)} != 29")
        return 1

    if args.check:
        print(f"OK: {len(rows)} 条 OM 映射")
        return 0

    today = date.today().isoformat()
    md = generate_md(rows, today)
    args.out.write_text(md, encoding="utf-8")
    print(f"已写入 {args.out}（{len(rows)} 条）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
