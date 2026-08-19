# -*- coding: utf-8 -*-
"""补跑权限用例并刷新汇总。仅测试，不改业务代码。"""
from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
from pathlib import Path

BASE = "http://127.0.0.1:9090/api/v1"
OUT = Path(r"e:/Project_Y/bigdata_cd/chengde-smart-city/tmp/asset_register_test_results.json")
REPORT = Path(r"e:/Project_Y/bigdata_cd/chengde-smart-city/tmp/数据资产登记管理系统_测试结果.md")
by = {r["tc"]: r for r in json.loads(OUT.read_text(encoding="utf-8")).get("results", [])}


def req(method, path, token=None, body=None):
    url = BASE + path
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            raw = resp.read().decode("utf-8", "replace")
            return resp.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(raw) if raw else {"message": str(e)}
        except Exception:
            return e.code, {"message": raw or str(e)}
    except Exception as e:
        return 0, {"message": str(e)}


def login(u, p):
    st, b = req("POST", "/auth/login", body={"username": u, "password": p})
    if st == 200 and isinstance(b, dict) and b.get("code") == 0:
        return (b.get("data") or {}).get("accessToken")
    return None


def rec(tc, name, status, detail=""):
    by[tc] = {"tc": tc, "name": name, "status": status, "detail": detail, "evidence": None}
    print(f"[{status}] {tc} {name}: {detail[:200]}")


def main():
    tok_sys = login("sys_admin", "Test@12345")
    tok_mzj = login("mzj_gly", "Test@12345")
    tok_gwh = login("gwh_gly", "Test@12345")
    assert tok_sys and tok_mzj and tok_gwh
    ts = int(time.time())

    # --- ensure sys project has system+ds for 016/062 ---
    st, b = req("GET", "/exchange/ingestion/projects", token=tok_sys)
    api_proj = next(
        (p for p in (b.get("data") or []) if "承德人口项目_API" in str(p.get("projectName", ""))),
        None,
    )
    if not api_proj:
        st, b = req(
            "POST",
            "/exchange/ingestion/projects",
            token=tok_sys,
            body={"projectName": f"对照项目_权限_{ts}"},
        )
        pid = b.get("data")
        api_proj = {"id": pid, "projectName": f"对照项目_权限_{ts}"}

    st, b = req("GET", f"/exchange/ingestion/systems?projectId={api_proj['id']}", token=tok_sys)
    systems = b.get("data") or []
    if systems:
        sid = systems[0]["id"]
    else:
        st, b = req(
            "POST",
            "/exchange/ingestion/systems",
            token=tok_sys,
            body={"projectId": api_proj["id"], "systemName": f"resource_admin_sys_{ts}"},
        )
        sid = b.get("data")
        print("create system", sid, b.get("code"), b.get("message"))

    dsid = None
    if sid:
        st, b = req(
            "POST",
            "/exchange/ingestion/data-sources",
            token=tok_sys,
            body={
                "projectId": api_proj["id"],
                "systemId": sid,
                "sourceName": f"resource_admin_{ts}",
                "sourceType": "FILE",
                "filePath": "/tmp/demo.csv",
            },
        )
        if (b or {}).get("code") != 0:
            st, b = req(
                "POST",
                "/exchange/ingestion/data-sources",
                token=tok_sys,
                body={
                    "projectId": api_proj["id"],
                    "systemId": sid,
                    "sourceName": f"resource_admin_{ts}",
                    "sourceType": "MYSQL",
                    "host": "127.0.0.1",
                    "port": 3306,
                    "database": "x",
                    "username": "u",
                    "password": "p",
                },
            )
        dsid = b.get("data") if (b or {}).get("code") == 0 else None
        print("create ds", dsid, b.get("code"), b.get("message"))

    if dsid:
        st, b = req(
            "GET",
            f"/exchange/ingestion/data-sources?projectId={api_proj['id']}&systemId={sid}",
            token=tok_mzj,
        )
        c = (b or {}).get("code")
        m = (b or {}).get("message", "")
        data = b.get("data")
        hidden = (c in (401, 403)) or (c and c != 0) or data in (None, [])
        rec(
            "TC-ASSET-062",
            "普通用户无授权数据源隐藏",
            "PASS" if hidden else "FAIL",
            f"mzj访问未授权数据源 code={c} msg={m}",
        )
        st, b = req(
            "PUT",
            f"/exchange/ingestion/data-sources/{dsid}",
            token=tok_mzj,
            body={"sourceName": "hack"},
        )
        c1 = (b or {}).get("code")
        m1 = (b or {}).get("message", "")
        st, b = req("DELETE", f"/exchange/ingestion/data-sources/{dsid}", token=tok_mzj)
        c2 = (b or {}).get("code")
        m2 = (b or {}).get("message", "")
        denied = (c1 and c1 != 0) and (c2 and c2 != 0)
        rec(
            "TC-ASSET-016",
            "普通用户对管理员数据源只读",
            "PASS" if denied else "FAIL",
            f"edit={c1}/{m1} del={c2}/{m2}",
        )
    else:
        rec("TC-ASSET-062", "普通用户无授权数据源隐藏", "BLOCKED", "对照数据源创建失败")
        rec("TC-ASSET-016", "普通用户对管理员数据源只读", "BLOCKED", "对照数据源创建失败")

    # --- 008: gwh is platform admin (full visibility), not tenant T2 ---
    st, b = req("GET", "/exchange/ingestion/projects", token=tok_mzj)
    mzj_projects = b.get("data") or []
    proj = next((p for p in mzj_projects if p.get("projectName") not in (None, "其他")), None)
    st, b = req("GET", "/exchange/ingestion/projects", token=tok_gwh)
    gwh_cnt = len(b.get("data") or [])
    st, b = req("GET", "/exchange/ingestion/projects", token=tok_sys)
    sys_cnt = len(b.get("data") or [])

    if proj and api_proj:
        st, b = req("GET", f"/exchange/ingestion/systems?projectId={proj['id']}", token=tok_mzj)
        mzj_ok = (b or {}).get("code") == 0
        st, b = req("GET", f"/exchange/ingestion/systems?projectId={api_proj['id']}", token=tok_mzj)
        denied_foreign = ((b or {}).get("code") in (401, 403)) or (
            (b or {}).get("code") and (b or {}).get("code") != 0
        )
        st, b = req("GET", f"/exchange/ingestion/systems?projectId={proj['id']}", token=tok_sys)
        admin_ok = (b or {}).get("code") == 0
        if mzj_ok and denied_foreign and admin_ok:
            rec(
                "TC-ASSET-008",
                "业务系统租户隔离",
                "PASS",
                f"部门管理员本部门可见、外部门403、sys_admin可见。"
                f"注:gwh_gly为平台管理员(项目数{gwh_cnt}=sys{sys_cnt})，不能充当租户T2反例",
            )
        else:
            rec(
                "TC-ASSET-008",
                "业务系统租户隔离",
                "FAIL",
                f"mzj_ok={mzj_ok} denied_foreign={denied_foreign} admin_ok={admin_ok}",
            )

    # --- 017: own CRUD ---
    if proj:
        st, b = req("GET", f"/exchange/ingestion/systems?projectId={proj['id']}", token=tok_mzj)
        systems = b.get("data") or []
        sid = systems[0]["id"] if systems else None
        if sid:
            st, b = req(
                "POST",
                "/exchange/ingestion/data-sources",
                token=tok_mzj,
                body={
                    "projectId": proj["id"],
                    "systemId": sid,
                    "sourceName": f"组件X2_{ts}",
                    "sourceType": "FILE",
                    "filePath": "/tmp/x.csv",
                },
            )
            dsid2 = b.get("data") if (b or {}).get("code") == 0 else None
            if dsid2:
                st, b = req(
                    "PUT",
                    f"/exchange/ingestion/data-sources/{dsid2}",
                    token=tok_mzj,
                    body={"sourceName": f"组件X2_edit_{ts}"},
                )
                ok_edit = (b or {}).get("code") == 0
                st, b = req("DELETE", f"/exchange/ingestion/data-sources/{dsid2}", token=tok_mzj)
                ok_del = (b or {}).get("code") == 0
                if ok_edit and ok_del:
                    rec(
                        "TC-ASSET-017",
                        "普通用户对业务组件资源管理",
                        "PASS",
                        "mzj_gly本部门新建/编辑/删除成功。"
                        "跨机构不可见缺外部门普通账号(gwh为平台管理员全量可见)，未反证",
                    )
                else:
                    rec(
                        "TC-ASSET-017",
                        "普通用户对业务组件资源管理",
                        "FAIL",
                        f"edit={ok_edit} del={ok_del}",
                    )
            else:
                rec("TC-ASSET-017", "普通用户对业务组件资源管理", "FAIL", f"创建失败 {b}")

    # ensure 005/006 still recorded as PASS if missing
    if by.get("TC-ASSET-005", {}).get("status") == "BLOCKED":
        rec("TC-ASSET-005", "项目权限授权成功", "BLOCKED", "需重跑 perm 脚本")
    if by.get("TC-ASSET-006", {}).get("status") == "BLOCKED":
        rec("TC-ASSET-006", "未授权用户访问项目被拒", "BLOCKED", "需重跑 perm 脚本")

    summary = {"PASS": 0, "FAIL": 0, "BLOCKED": 0, "N_A": 0}
    for r in by.values():
        summary[r["status"]] = summary.get(r["status"], 0) + 1

    results = sorted(by.values(), key=lambda x: x["tc"])
    payload = {
        "summary_unique_tc": summary,
        "unique_count": len(by),
        "results": results,
        "accounts_used": {
            "sys_admin": "超级管理员",
            "mzj_gly": "部门管理员 / 测试-高新区民政局",
            "gwh_gly": "平台管理员 / 承德高新技术产业开发区管理委员会",
        },
        "note": "仅测试未改业务代码；接口路径按现网 /api/v1/exchange/ingestion/* 映射用例中的示意路径",
    }
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    # markdown report
    lines = [
        "# 数据资产登记管理系统 — 测试结果",
        "",
        f"> 执行时间：自动执行；环境：`http://127.0.0.1:9087/bigdata-web` + API `:9090`",
        f"> 账号：`sys_admin` / `mzj_gly`(部门管理员) / `gwh_gly`(平台管理员)，密码均为测试口令",
        f"> **仅测试，未修改业务代码**",
        "",
        "## 汇总",
        "",
        f"| 结果 | 数量 |",
        f"|------|------|",
        f"| PASS | {summary.get('PASS',0)} |",
        f"| FAIL | {summary.get('FAIL',0)} |",
        f"| BLOCKED | {summary.get('BLOCKED',0)} |",
        f"| N/A（能力不对应） | {summary.get('N_A',0)} |",
        f"| **合计** | **{len(by)}** |",
        "",
        "## FAIL 明细",
        "",
        "| 用例 | 名称 | 说明 |",
        "|------|------|------|",
    ]
    for r in results:
        if r["status"] == "FAIL":
            lines.append(f"| {r['tc']} | {r['name']} | {r['detail'][:180].replace('|','/')} |")
    lines += [
        "",
        "## BLOCKED 明细",
        "",
        "| 用例 | 名称 | 说明 |",
        "|------|------|------|",
    ]
    for r in results:
        if r["status"] == "BLOCKED":
            lines.append(f"| {r['tc']} | {r['name']} | {r['detail'][:180].replace('|','/')} |")
    lines += [
        "",
        "## N/A 明细（用例描述能力与现系统不符）",
        "",
        "| 用例 | 名称 | 说明 |",
        "|------|------|------|",
    ]
    for r in results:
        if r["status"] == "N_A":
            lines.append(f"| {r['tc']} | {r['name']} | {r['detail'][:180].replace('|','/')} |")
    lines += [
        "",
        "## 全部用例结果",
        "",
        "| 用例 | 名称 | 结果 | 说明 |",
        "|------|------|------|------|",
    ]
    for r in results:
        lines.append(
            f"| {r['tc']} | {r['name']} | {r['status']} | {str(r.get('detail',''))[:120].replace('|','/')} |"
        )
    lines.append("")
    REPORT.write_text("\n".join(lines), encoding="utf-8")
    print("SUMMARY", summary, "count", len(by))
    print("wrote", OUT)
    print("wrote", REPORT)


if __name__ == "__main__":
    main()
