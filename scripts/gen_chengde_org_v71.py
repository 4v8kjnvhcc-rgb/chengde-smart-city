# -*- coding: utf-8 -*-
"""Generate V71 Flyway SQL from chengde_org_tree.json."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JSON_PATH = ROOT / "scripts" / "chengde_org_tree.json"
OUT_PATH = ROOT / "platform-backend" / "src" / "main" / "resources" / "db" / "migration" / "V71__chengde_gov_org_tree.sql"

ORG_AS_INST = {"ROOT", "LEADER", "DISPATCH", "VERTICAL", "TOWN", "SOE"}
PWD_HASH = "$2a$10$CWY2FHCAutORDZAK6auKauFUi8R.VSu4PMwnwhjuzcj5XaCqSHyZm"


def org_type(t: str) -> int:
    return 1 if t in ORG_AS_INST else 2


def flatten(nodes, out):
    for n in nodes:
        out.append(n)
        flatten(n.get("children") or [], out)


def main() -> None:
    data = json.loads(JSON_PATH.read_text(encoding="utf-8"))
    nodes: list = []
    flatten(data, nodes)

    id_map: dict[str, int] = {}
    next_id = 101
    for n in nodes:
        if n["id"] == "CD":
            id_map[n["id"]] = 1
        else:
            id_map[n["id"]] = next_id
            next_id += 1

    lines: list[str] = [
        "-- 承德高新区政府组织架构入库 + 公安分局测试账号",
        "",
        "-- 根节点沿用 id=1，兼容已有用户/授权外键",
        "UPDATE sys_org SET org_code = '100000', org_name = '承德高新技术产业开发区', org_type = 1, sort_order = 0, status = 1 WHERE id = 1;",
        "",
        "-- 旧演示机构保留，改名避免与正式树混淆",
        "UPDATE sys_org SET org_name = '演示机构A（旧）', sort_order = 900 WHERE id = 2 AND org_code = 'ORG_A';",
        "UPDATE sys_org SET org_name = '演示机构B（旧）', sort_order = 901 WHERE id = 3 AND org_code = 'ORG_B';",
        "",
    ]

    for n in nodes:
        if n["id"] == "CD":
            continue
        nid = id_map[n["id"]]
        pid = id_map[n["parent_id"]] if n.get("parent_id") else 0
        code = n["code"]
        name = n["full_name"].replace("'", "''")
        ot = org_type(n.get("type") or "INNER_DEPT")
        so = int(n.get("sort_order") or 0)
        lines.append(
            "INSERT INTO sys_org (id, parent_id, org_code, org_name, org_type, sort_order, status) "
            f"SELECT {nid}, {pid}, '{code}', '{name}', {ot}, {so}, 1 FROM DUAL "
            f"WHERE NOT EXISTS (SELECT 1 FROM sys_org WHERE id = {nid} OR org_code = '{code}');"
        )

    lines.append("")
    lines.append("-- 同步名称/父子（幂等）")
    for n in nodes:
        if n["id"] == "CD":
            continue
        pid = id_map[n["parent_id"]] if n.get("parent_id") else 0
        code = n["code"]
        name = n["full_name"].replace("'", "''")
        ot = org_type(n.get("type") or "INNER_DEPT")
        so = int(n.get("sort_order") or 0)
        lines.append(
            f"UPDATE sys_org SET parent_id = {pid}, org_name = '{name}', org_type = {ot}, "
            f"sort_order = {so}, status = 1 WHERE org_code = '{code}';"
        )

    lines.extend(
        [
            "",
            "-- 公安分局测试账号（机构管理员 + 普通用户角色）密码 Test@12345",
            "INSERT INTO sys_user (username, password_hash, display_name, org_id, status) "
            f"SELECT 'gongan', '{PWD_HASH}', '高新区公安分局测试账号', o.id, 1 "
            "FROM sys_org o WHERE o.org_code = '100200' "
            "AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'gongan');",
            "UPDATE sys_user u JOIN sys_org o ON o.org_code = '100200' "
            f"SET u.password_hash = '{PWD_HASH}', u.display_name = '高新区公安分局测试账号', "
            "u.org_id = o.id, u.status = 1 WHERE u.username = 'gongan';",
            "INSERT INTO sys_user_role (user_id, role_id) "
            "SELECT u.id, 2 FROM sys_user u WHERE u.username = 'gongan' "
            "AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = 2);",
            "INSERT INTO sys_user_role (user_id, role_id) "
            "SELECT u.id, 3 FROM sys_user u WHERE u.username = 'gongan' "
            "AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = 3);",
            "",
        ]
    )

    OUT_PATH.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {OUT_PATH} nodes={len(nodes)} police_id={id_map.get('CD-Police')}")


if __name__ == "__main__":
    main()
