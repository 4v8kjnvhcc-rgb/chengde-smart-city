# -*- coding: utf-8 -*-
"""从 D07 §5.6 生成门户菜单 seed（D11 V0.1 / Flyway V3）。"""
from __future__ import annotations

import argparse
import json
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_JSON = ROOT / "platform-backend" / "src" / "main" / "resources" / "menu" / "menu-seed.json"
OUT_SQL = ROOT / "platform-backend" / "src" / "main" / "resources" / "db" / "migration" / "V3__seed_ms1_menu_and_users.sql"

# D07 §5.6 菜单架构树（叶子 integration_type 见 D06 口径）
MENU_TREE: list[dict] = [
    {
        "name": "数据共享交换平台",
        "path": "/exchange",
        "icon": "Connection",
        "integration_type": "self",
        "children": [
            {"name": "大数据归集", "path": "/exchange/ingestion", "integration_type": "placeholder", "m_code": "M039"},
            {"name": "服务总线", "path": "/exchange/esb", "integration_type": "esb", "m_code": "M214"},
            {"name": "应用平台", "path": "/exchange/application", "integration_type": "placeholder", "m_code": "M020"},
            {"name": "应用分析门户", "path": "/exchange/analysis-portal", "integration_type": "iframe", "m_code": "M031"},
        ],
    },
    {
        "name": "主数据平台",
        "path": "/master-data",
        "icon": "Coin",
        "integration_type": "self",
        "children": [
            {"name": "数据融合治理", "path": "/governance", "integration_type": "om", "m_code": "M078"},
            {"name": "非结构化治理", "path": "/unstructured", "integration_type": "placeholder", "m_code": "M123"},
            {"name": "资源中心", "path": "/resource-center", "integration_type": "placeholder", "m_code": "M130"},
        ],
    },
    {
        "name": "大数据挖掘分析平台",
        "path": "/analytics",
        "icon": "DataAnalysis",
        "integration_type": "self",
        "children": [
            {"name": "通用支撑", "path": "/analytics/support", "integration_type": "placeholder", "m_code": "M139"},
            {"name": "智能BI", "path": "/analytics/bi", "integration_type": "iframe", "m_code": "M146"},
            {"name": "人口大数据", "path": "/analytics/population", "integration_type": "iframe", "m_code": "M152"},
            {"name": "法人大数据", "path": "/analytics/legal-entity", "integration_type": "iframe", "m_code": "M175"},
            {"name": "宏观经济", "path": "/analytics/macro", "integration_type": "iframe", "m_code": "M193"},
            {"name": "重点领域", "path": "/analytics/key-domains", "integration_type": "iframe", "m_code": "M204"},
        ],
    },
    {
        "name": "系统管理",
        "path": "/system",
        "icon": "Setting",
        "integration_type": "self",
        "children": [
            {"name": "用户管理", "path": "/system/users", "component": "system/UserManage", "integration_type": "self", "m_code": "M211",
             "permission": "system:user:list", "buttons": [
                 {"name": "用户查询", "permission": "system:user:query"},
                 {"name": "用户新增", "permission": "system:user:add"},
                 {"name": "用户编辑", "permission": "system:user:edit"},
                 {"name": "用户删除", "permission": "system:user:delete"},
             ]},
            {"name": "角色管理", "path": "/system/roles", "component": "system/RoleManage", "integration_type": "self", "m_code": "M211",
             "permission": "system:role:list"},
            {"name": "机构管理", "path": "/system/orgs", "component": "system/OrgManage", "integration_type": "self", "m_code": "M048",
             "permission": "system:org:list"},
            {"name": "菜单管理", "path": "/system/menus", "component": "system/MenuManage", "integration_type": "self", "m_code": "M050",
             "permission": "system:menu:list"},
            {"name": "审计日志", "path": "/system/audit", "component": "system/AuditLog", "integration_type": "self", "m_code": "M144",
             "permission": "system:audit:list"},
            {"name": "等保开关", "path": "/system/security", "component": "system/SecurityConfig", "integration_type": "self", "m_code": "M049",
             "permission": "system:security:config"},
            {"name": "调度管理", "path": "/integration/ds", "integration_type": "placeholder", "m_code": "M098"},
            {"name": "ETL治理", "path": "/integration/kettle", "integration_type": "placeholder", "m_code": "M215"},
        ],
    },
]

DASHBOARD = {
    "name": "工作台",
    "path": "/dashboard",
    "component": "dashboard/Index",
    "integration_type": "self",
    "permission": "dashboard:view",
}


def flatten_tree() -> list[dict]:
    rows: list[dict] = []
    sort = 0

    # 根目录
    sort += 1
    root_id = 1
    rows.append({
        "id": root_id,
        "parent_id": 0,
        "menu_name": "统一门户",
        "menu_type": 1,
        "path": "/",
        "component": None,
        "permission": None,
        "icon": "HomeFilled",
        "sort_order": sort,
        "m_code": None,
        "integration_type": "self",
    })

    sort += 1
    dash_id = 2
    rows.append({
        "id": dash_id,
        "parent_id": root_id,
        "menu_name": DASHBOARD["name"],
        "menu_type": 2,
        "path": DASHBOARD["path"],
        "component": DASHBOARD["component"],
        "permission": DASHBOARD["permission"],
        "icon": "Odometer",
        "sort_order": sort,
        "m_code": None,
        "integration_type": "self",
    })

    next_id = 3
    for platform in MENU_TREE:
        sort += 1
        plat_id = next_id
        next_id += 1
        rows.append({
            "id": plat_id,
            "parent_id": root_id,
            "menu_name": platform["name"],
            "menu_type": 1,
            "path": platform["path"],
            "component": None,
            "permission": None,
            "icon": platform.get("icon"),
            "sort_order": sort,
            "m_code": None,
            "integration_type": platform.get("integration_type", "self"),
        })
        for child in platform.get("children", []):
            sort += 1
            child_id = next_id
            next_id += 1
            is_menu = child.get("component") or child.get("integration_type") in ("placeholder", "esb", "om", "iframe")
            rows.append({
                "id": child_id,
                "parent_id": plat_id,
                "menu_name": child["name"],
                "menu_type": 2 if is_menu else 1,
                "path": child.get("path"),
                "component": child.get("component"),
                "permission": child.get("permission"),
                "icon": None,
                "sort_order": sort,
                "m_code": child.get("m_code"),
                "integration_type": child.get("integration_type", "self"),
            })
            for btn in child.get("buttons", []):
                sort += 1
                rows.append({
                    "id": next_id,
                    "parent_id": child_id,
                    "menu_name": btn["name"],
                    "menu_type": 3,
                    "path": None,
                    "component": None,
                    "permission": btn["permission"],
                    "icon": None,
                    "sort_order": sort,
                    "m_code": child.get("m_code"),
                    "integration_type": "self",
                })
                next_id += 1

    return rows


def sql_escape(s: str | None) -> str:
    if s is None:
        return "NULL"
    return "'" + s.replace("'", "''") + "'"


def write_sql(rows: list[dict]) -> str:
    lines = [
        "-- MS1 菜单树 + 机构/角色/用户（D08 测试账号）",
        "-- 默认密码: Test@12345（BCrypt）",
        "",
        "DELETE FROM sys_role_menu;",
        "DELETE FROM sys_user_role;",
        "DELETE FROM sys_menu;",
        "DELETE FROM sys_user;",
        "DELETE FROM sys_role;",
        "DELETE FROM sys_org;",
        "DELETE FROM sys_security_config;",
        "",
    ]
    for r in rows:
        lines.append(
            f"INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES "
            f"({r['id']}, {r['parent_id']}, {sql_escape(r['menu_name'])}, {r['menu_type']}, {sql_escape(r['path'])}, "
            f"{sql_escape(r['component'])}, {sql_escape(r['permission'])}, {sql_escape(r['icon'])}, {r['sort_order']}, "
            f"{sql_escape(r['m_code'])}, {sql_escape(r['integration_type'])});"
        )

  # BCrypt Test@12345
    pwd = "$2a$10$CWY2FHCAutORDZAK6auKauFUi8R.VSu4PMwnwhjuzcj5XaCqSHyZm"
    lines.extend([
        "",
        "INSERT INTO sys_org (id, parent_id, org_code, org_name, org_type) VALUES",
        "(1, 0, 'ORG_ROOT', '承德高新区', 1),",
        "(2, 1, 'ORG_A', '机构A', 1),",
        "(3, 1, 'ORG_B', '机构B', 1);",
        "",
        "INSERT INTO sys_role (id, role_code, role_name, role_type, description) VALUES",
        "(1, 'SYSTEM_ADMIN', '系统管理员', 1, '全平台管理'),",
        "(2, 'DEPT_ADMIN', '机构管理员', 2, '本机构管理'),",
        "(3, 'USER', '普通用户', 2, '业务用户'),",
        "(4, 'USER_QUERY_ONLY', '仅查询用户', 2, 'RBAC演示：仅用户查询');",
        "",
        f"INSERT INTO sys_user (id, username, password_hash, display_name, org_id, status) VALUES",
        f"(1, 'sys_admin', '{pwd}', '系统管理员', 1, 1),",
        f"(2, 'dept_admin_a', '{pwd}', '机构A管理员', 2, 1),",
        f"(3, 'user_a', '{pwd}', '机构A用户', 2, 1),",
        f"(4, 'user_b', '{pwd}', '机构B用户', 3, 1);",
        "",
        "INSERT INTO sys_user_role (user_id, role_id) VALUES (1,1), (2,2), (3,3), (4,3);",
        "",
        "INSERT INTO sys_role_menu (role_id, menu_id) SELECT 1, id FROM sys_menu;",
        "",
        "INSERT INTO sys_role_menu (role_id, menu_id) SELECT 2, id FROM sys_menu WHERE permission IS NULL OR permission NOT LIKE 'system:user:%' OR permission = 'system:user:query';",
        "",
        "INSERT INTO sys_role_menu (role_id, menu_id) SELECT 3, id FROM sys_menu WHERE path = '/dashboard' OR menu_name = '工作台';",
        "",
        "INSERT INTO sys_role_menu (role_id, menu_id) SELECT 4, id FROM sys_menu WHERE permission IS NULL OR permission = 'system:user:query' OR path = '/dashboard';",
        "",
        "INSERT INTO sys_security_config (config_key, config_value, description) VALUES",
        "('two_factor_enabled', 'false', '双因素登录（MS1 默认关闭，验收时可开）'),",
        "('password_min_length', '8', '密码最小长度'),",
        "('password_require_complex', 'true', '密码复杂度'),",
        "('login_max_failures', '5', '登录失败锁定次数'),",
        "('login_lock_minutes', '60', '锁定时长分钟'),",
        "('session_idle_minutes', '30', '会话空闲超时'),",
        "('access_token_minutes', '30', 'Access Token 有效期'),",
        "('refresh_token_hours', '8', 'Refresh Token 有效期'),",
        "('audit_enabled', 'true', '审计开关');",
    ])
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="校验菜单行数")
    args = parser.parse_args()
    rows = flatten_tree()
    OUT_JSON.parent.mkdir(parents=True, exist_ok=True)
    OUT_JSON.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
    OUT_SQL.write_text(write_sql(rows), encoding="utf-8")
    print(f"菜单节点: {len(rows)} 条 -> {OUT_JSON}")
    print(f"Flyway seed -> {OUT_SQL}")
    if args.check:
        assert len(rows) >= 20, f"菜单过少: {len(rows)}"
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
