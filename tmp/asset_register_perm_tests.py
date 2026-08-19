# -*- coding: utf-8 -*-
"""仅测试：用 mzj_gly / gwh_gly 补跑授权与隔离类用例。不修改业务代码。"""
from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
from pathlib import Path

BASE = "http://127.0.0.1:9090/api/v1"
OUT = Path(r"e:/Project_Y/bigdata_cd/chengde-smart-city/tmp/asset_register_test_results.json")
PREV = json.loads(OUT.read_text(encoding="utf-8")) if OUT.exists() else {"results": []}
by = {r["tc"]: r for r in PREV.get("results", [])}


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
        with urllib.request.urlopen(r, timeout=45) as resp:
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
        return (b.get("data") or {}).get("accessToken"), b.get("data") or {}
    return None, b


def rec(tc, name, status, detail=""):
    by[tc] = {"tc": tc, "name": name, "status": status, "detail": detail, "evidence": None}
    print(f"[{status}] {tc} {name}: {detail[:220]}")


def main():
    tok_sys, _ = login("sys_admin", "Test@12345")
    tok_mzj, info_mzj = login("mzj_gly", "Test@12345")
    tok_gwh, info_gwh = login("gwh_gly", "Test@12345")
    assert tok_mzj and tok_gwh and tok_sys
    print(
        "logins ok:",
        "mzj=",
        (info_mzj.get("user") or {}).get("orgName"),
        "gwh=",
        (info_gwh.get("user") or {}).get("orgName"),
    )

    st, b = req("GET", "/exchange/ingestion/projects", token=tok_mzj)
    mzj_projects = b.get("data") or []
    proj = next(
        (
            p
            for p in mzj_projects
            if p.get("projectName") not in (None, "其他")
            and not str(p.get("projectCode", "")).startswith("PRJ_OTHER")
        ),
        None,
    )
    print("mzj project", proj)

    st, b = req("GET", "/system/access/users-for-project-grant", token=tok_mzj)
    grant_users = b.get("data") or []
    target = next((u for u in grant_users if u.get("username") == "mzj_gly01"), None)
    if not target and grant_users:
        target = grant_users[0]
    print("grant target", target)

    # TC-ASSET-005
    if proj and target:
        st, b = req("GET", f"/system/access/project-grants?projectId={proj['id']}", token=tok_mzj)
        existing = b.get("data") or []
        hit = next((g for g in existing if g.get("granteeId") == target["id"]), None)
        if hit and not hit.get("creatorGrant"):
            req("DELETE", f"/system/access/project-grants/{hit['id']}", token=tok_mzj)
            hit = None
        if hit:
            rec(
                "TC-ASSET-005",
                "项目权限授权成功",
                "PASS",
                f"目标用户已有授权 grantee={target['username']} project={proj['projectName']}",
            )
        else:
            st, b = req(
                "POST",
                "/system/access/project-grants",
                token=tok_mzj,
                body={
                    "projectId": proj["id"],
                    "granteeType": "USER",
                    "granteeId": target["id"],
                    "perm": "VIEW",
                },
            )
            c = (b or {}).get("code")
            m = (b or {}).get("message", "")
            if c == 0:
                rec(
                    "TC-ASSET-005",
                    "项目权限授权成功",
                    "PASS",
                    f"mzj_gly 授权 {target['username']} VIEW 到项目 {proj['projectName']} grantId={b.get('data')}",
                )
            else:
                rec("TC-ASSET-005", "项目权限授权成功", "FAIL", f"code={c} msg={m} body={b}")
    else:
        rec("TC-ASSET-005", "项目权限授权成功", "BLOCKED", "缺少项目或可授权用户")

    # TC-ASSET-006
    st, b = req("GET", "/exchange/ingestion/projects", token=tok_sys)
    sys_projects = b.get("data") or []
    foreign = next((p for p in sys_projects if "承德人口项目_API" in str(p.get("projectName", ""))), None)
    if not foreign:
        mzj_ids = {p["id"] for p in mzj_projects}
        foreign = next((p for p in sys_projects if p.get("id") not in mzj_ids), None)
    print("foreign project", foreign)
    if foreign:
        mzj_ids = {p["id"] for p in mzj_projects}
        visible = foreign["id"] in mzj_ids
        st, b = req("GET", f"/exchange/ingestion/systems?projectId={foreign['id']}", token=tok_mzj)
        c = (b or {}).get("code")
        m = (b or {}).get("message", "")
        st2, b2 = req("GET", f"/exchange/ingestion/data-sources?projectId={foreign['id']}", token=tok_mzj)
        c2 = (b2 or {}).get("code")
        m2 = (b2 or {}).get("message", "")
        denied = (c in (401, 403)) or (c and c != 0) or ("权限" in m) or ("无权限" in m)
        denied2 = (c2 in (401, 403)) or (c2 and c2 != 0)
        if (not visible) and (denied or denied2):
            rec(
                "TC-ASSET-006",
                "未授权用户访问项目被拒",
                "PASS",
                f"mzj不可见「{foreign['projectName']}」; systems={c}/{m}; ds={c2}/{m2}",
            )
        elif not visible and c == 0:
            rec(
                "TC-ASSET-006",
                "未授权用户访问项目被拒",
                "FAIL",
                f"列表已隐藏但直访systems仍成功 code={c} len={len(b.get('data') or [])}",
            )
        elif visible:
            rec("TC-ASSET-006", "未授权用户访问项目被拒", "FAIL", f"mzj可见未授权项目 {foreign['projectName']}")
        else:
            rec("TC-ASSET-006", "未授权用户访问项目被拒", "FAIL", f"visible={visible} sys={c}/{m} ds={c2}/{m2}")
    else:
        rec("TC-ASSET-006", "未授权用户访问项目被拒", "BLOCKED", "未找到外部门对照项目")

    # TC-ASSET-008
    ts = int(time.time())
    if proj:
        st, b = req(
            "POST",
            "/exchange/ingestion/systems",
            token=tok_mzj,
            body={"projectId": proj["id"], "systemName": f"sys_T1_mzj_{ts}"},
        )
        st, b = req("GET", f"/exchange/ingestion/systems?projectId={proj['id']}", token=tok_mzj)
        mzj_sys_names = [x.get("systemName") for x in (b.get("data") or [])]
        mzj_sees = f"sys_T1_mzj_{ts}" in mzj_sys_names

        st, b = req("GET", f"/exchange/ingestion/systems?projectId={proj['id']}", token=tok_gwh)
        c_g = (b or {}).get("code")
        m_g = (b or {}).get("message", "")
        data_g = b.get("data")
        leaked = isinstance(data_g, list) and any(x.get("systemName") == f"sys_T1_mzj_{ts}" for x in data_g)
        cross_hidden = (c_g in (401, 403)) or (c_g and c_g != 0) or (not leaked)

        st, b = req("GET", f"/exchange/ingestion/systems?projectId={proj['id']}", token=tok_sys)
        c_s = (b or {}).get("code")
        sys_names = [x.get("systemName") for x in (b.get("data") or [])]
        admin_sees = c_s == 0 and f"sys_T1_mzj_{ts}" in sys_names

        if mzj_sees and cross_hidden and not leaked and admin_sees:
            rec(
                "TC-ASSET-008",
                "业务系统租户隔离",
                "PASS",
                f"T1可见新建系统; gwh不可见/被拒(code={c_g}/{m_g}); sys_admin可见",
            )
        else:
            rec(
                "TC-ASSET-008",
                "业务系统租户隔离",
                "FAIL",
                f"mzj_sees={mzj_sees} leaked={leaked} cross_hidden={cross_hidden} admin_sees={admin_sees} gwh={c_g}/{m_g} sys={c_s}",
            )
    else:
        rec("TC-ASSET-008", "业务系统租户隔离", "BLOCKED", "无mzj项目")

    # TC-ASSET-062 / 016
    st, b = req("GET", "/exchange/ingestion/projects", token=tok_sys)
    api_proj = next((p for p in (b.get("data") or []) if "承德人口项目_API" in str(p.get("projectName", ""))), None)
    if api_proj:
        st, b = req("GET", f"/exchange/ingestion/systems?projectId={api_proj['id']}", token=tok_sys)
        systems = b.get("data") or []
        sid = systems[0]["id"] if systems else None
        ds_list = []
        if sid:
            st, b = req(
                "GET",
                f"/exchange/ingestion/data-sources?projectId={api_proj['id']}&systemId={sid}",
                token=tok_sys,
            )
            ds_list = b.get("data") or []
        print("api_proj ds count", len(ds_list))
        if sid:
            st, b = req(
                "GET",
                f"/exchange/ingestion/data-sources?projectId={api_proj['id']}&systemId={sid}",
                token=tok_mzj,
            )
            c = (b or {}).get("code")
            m = (b or {}).get("message", "")
            data = b.get("data")
            hidden = (c in (401, 403)) or (c and c != 0) or data in (None, [])
            if hidden:
                rec(
                    "TC-ASSET-062",
                    "普通用户无授权数据源隐藏",
                    "PASS",
                    f"mzj_gly访问未授权项目数据源被拒/空 code={c} msg={m}",
                )
            else:
                rec(
                    "TC-ASSET-062",
                    "普通用户无授权数据源隐藏",
                    "FAIL",
                    f"mzj可见未授权数据源 count={len(data) if isinstance(data, list) else data}",
                )

            if ds_list:
                dsid = ds_list[0]["id"]
                st, b = req(
                    "PUT",
                    f"/exchange/ingestion/data-sources/{dsid}",
                    token=tok_mzj,
                    body={"sourceName": "hack"},
                )
                c = (b or {}).get("code")
                m = (b or {}).get("message", "")
                st2, b2 = req("DELETE", f"/exchange/ingestion/data-sources/{dsid}", token=tok_mzj)
                c2 = (b2 or {}).get("code")
                m2 = (b2 or {}).get("message", "")
                denied_write = (c and c != 0) and (c2 and c2 != 0)
                if denied_write:
                    rec(
                        "TC-ASSET-016",
                        "普通用户对管理员数据源只读",
                        "PASS",
                        f"编辑/删除被拒 edit={c}/{m} del={c2}/{m2}",
                    )
                else:
                    rec(
                        "TC-ASSET-016",
                        "普通用户对管理员数据源只读",
                        "FAIL",
                        f"写操作未拒 edit={c}/{m} del={c2}/{m2}",
                    )
            else:
                rec("TC-ASSET-016", "普通用户对管理员数据源只读", "BLOCKED", "目标项目无数据源")
        else:
            rec("TC-ASSET-062", "普通用户无授权数据源隐藏", "BLOCKED", "无system")
            rec("TC-ASSET-016", "普通用户对管理员数据源只读", "BLOCKED", "无system")
    else:
        rec("TC-ASSET-062", "普通用户无授权数据源隐藏", "BLOCKED", "无对照项目")
        rec("TC-ASSET-016", "普通用户对管理员数据源只读", "BLOCKED", "无对照项目")

    # TC-ASSET-017
    if proj:
        st, b = req("GET", f"/exchange/ingestion/systems?projectId={proj['id']}", token=tok_mzj)
        systems = b.get("data") or []
        sid = systems[0]["id"] if systems else None
        if not sid:
            st, b = req(
                "POST",
                "/exchange/ingestion/systems",
                token=tok_mzj,
                body={"projectId": proj["id"], "systemName": f"sys_for_ds_{ts}"},
            )
            sid = b.get("data")
        if sid:
            st, b = req(
                "POST",
                "/exchange/ingestion/data-sources",
                token=tok_mzj,
                body={
                    "projectId": proj["id"],
                    "systemId": sid,
                    "sourceName": f"组件X_{ts}",
                    "sourceType": "MYSQL",
                    "host": "127.0.0.1",
                    "port": 3306,
                    "database": "smart_city",
                    "username": "smart_city",
                    "password": "smart_city",
                },
            )
            if (b or {}).get("code") != 0:
                st, b = req(
                    "POST",
                    "/exchange/ingestion/data-sources",
                    token=tok_mzj,
                    body={
                        "projectId": proj["id"],
                        "systemId": sid,
                        "sourceName": f"组件X_{ts}",
                        "sourceType": "MYSQL",
                        "connConfig": {
                            "host": "127.0.0.1",
                            "port": 3306,
                            "database": "smart_city",
                            "username": "smart_city",
                            "password": "smart_city",
                        },
                    },
                )
            dsid = b.get("data") if (b or {}).get("code") == 0 else None
            if dsid:
                st, b = req(
                    "PUT",
                    f"/exchange/ingestion/data-sources/{dsid}",
                    token=tok_mzj,
                    body={"sourceName": f"组件X_edit_{ts}"},
                )
                ok_edit = (b or {}).get("code") == 0
                st, b = req(
                    "GET",
                    f"/exchange/ingestion/data-sources?projectId={proj['id']}&systemId={sid}",
                    token=tok_gwh,
                )
                c_g = (b or {}).get("code")
                data_g = b.get("data")
                other_hidden = (c_g and c_g != 0) or not (
                    isinstance(data_g, list) and any(x.get("id") == dsid for x in data_g)
                )
                st, b = req("DELETE", f"/exchange/ingestion/data-sources/{dsid}", token=tok_mzj)
                ok_del = (b or {}).get("code") == 0
                if ok_edit and ok_del and other_hidden:
                    rec(
                        "TC-ASSET-017",
                        "普通用户对业务组件资源管理",
                        "PASS",
                        f"mzj新建/编辑/删除成功; 其他机构(gwh)不可见 code={c_g}",
                    )
                else:
                    rec(
                        "TC-ASSET-017",
                        "普通用户对业务组件资源管理",
                        "FAIL",
                        f"edit={ok_edit} del={ok_del} other_hidden={other_hidden} gwh_code={c_g}",
                    )
            else:
                rec("TC-ASSET-017", "普通用户对业务组件资源管理", "FAIL", f"mzj创建数据源失败 {b}")
        else:
            rec("TC-ASSET-017", "普通用户对业务组件资源管理", "BLOCKED", "无业务系统")
    else:
        rec("TC-ASSET-017", "普通用户对业务组件资源管理", "BLOCKED", "无项目")

    # TC-ASSET-043
    st, b = req("GET", "/exchange/ingestion/guides", token=tok_mzj)
    c_m = (b or {}).get("code")
    st, b2 = req("GET", "/exchange/ingestion/guides", token=tok_gwh)
    c_g = (b2 or {}).get("code")
    if c_m in (401, 403) or (c_m and c_m != 0 and "权限" in str((b or {}).get("message", ""))):
        rec("TC-ASSET-043", "非业务人员访问填报指引", "PASS", f"mzj被拒 code={c_m}")
    else:
        rec(
            "TC-ASSET-043",
            "非业务人员访问填报指引",
            "BLOCKED",
            f"当前 mzj/gwh 均能访问指引(mzj={c_m}, gwh={c_g})，缺少无填报权限账号",
        )

    st, b = req("GET", "/exchange/ingestion/projects", token=tok_gwh)
    gwh_cnt = len(b.get("data") or [])
    st, b = req("GET", "/exchange/ingestion/projects", token=tok_mzj)
    mzj_cnt = len(b.get("data") or [])
    st, b = req("GET", "/exchange/ingestion/projects", token=tok_sys)
    sys_cnt = len(b.get("data") or [])
    print(f"visibility sys={sys_cnt} gwh={gwh_cnt} mzj={mzj_cnt}")

    summary = {"PASS": 0, "FAIL": 0, "BLOCKED": 0, "N_A": 0}
    for r in by.values():
        summary[r["status"]] = summary.get(r["status"], 0) + 1
    payload = {
        "summary_unique_tc": summary,
        "unique_count": len(by),
        "results": list(by.values()),
        "updated_with_accounts": ["mzj_gly", "gwh_gly", "sys_admin"],
        "visibility": {"sys": sys_cnt, "gwh": gwh_cnt, "mzj": mzj_cnt},
    }
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print("SUMMARY", summary, "count", len(by))
    for k in [
        "TC-ASSET-005",
        "TC-ASSET-006",
        "TC-ASSET-008",
        "TC-ASSET-016",
        "TC-ASSET-017",
        "TC-ASSET-062",
        "TC-ASSET-043",
    ]:
        r = by.get(k)
        if r:
            print(k, r["status"], r["detail"][:180])


if __name__ == "__main__":
    main()
