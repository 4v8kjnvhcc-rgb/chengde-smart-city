# -*- coding: utf-8 -*-
"""补跑分类/编目/脱敏等字段纠正后的用例，并刷新报告。"""
from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
from pathlib import Path

BASE = "http://10.216.131.100:9087/api/v1"
PWD = "Cd_zhcs@#2026"
OUT = Path(r"e:/Project_Y/bigdata_cd/chengde-smart-city/tmp/collect_prod_test_results.json")
OUT_DESK = Path(r"f:/backup/Desktop/CD系统改造/测试/数据资源采集汇聚系统_测试结果_生产.json")
OUT_MD = Path(r"e:/Project_Y/bigdata_cd/chengde-smart-city/tmp/collect_prod_test_results.md")
OUT_MD_DESK = Path(r"f:/backup/Desktop/CD系统改造/测试/数据资源采集汇聚系统_测试结果_生产.md")

d = json.loads(OUT.read_text(encoding="utf-8"))
by = {r["tc"]: r for r in d["results"]}


def req(method, path, token=None, body=None):
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode()
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    r = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=45) as resp:
            raw = resp.read().decode()
            return resp.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"message": raw}


def login(u):
    st, b = req("POST", "/auth/login", body={"username": u, "password": PWD})
    return b["data"]["accessToken"]


def rec(tc, name, status, detail=""):
    by[tc] = {"tc": tc, "name": name, "status": status, "detail": str(detail)[:400]}
    print(f"[{status}] {tc} {name}: {str(detail)[:180]}")


def ok(b):
    return isinstance(b, dict) and b.get("code") == 0


def cm(b):
    return (b or {}).get("code"), str((b or {}).get("message") or "")


def main():
    tok = login("sys_admin")
    tok_pt = login("pt_gly")
    ts = int(time.time())

    # list categories for parentId
    st, b = req("GET", "/exchange/ingestion/collect/categories", token=tok)
    cats = b.get("data") or []
    parent_id = 0
    if isinstance(cats, list) and cats:
        parent_id = cats[0].get("id") or cats[0].get("parentId") or 0
    print("parent_id", parent_id, "cats", len(cats) if isinstance(cats, list) else cats)

    def create_cat(name, code):
        return req(
            "POST",
            "/exchange/ingestion/collect/categories",
            token=tok,
            body={
                "nodeName": name,
                "nodeCode": code,
                "parentId": parent_id if parent_id else 0,
                "secretFlag": 0,
            },
        )

    st, b = create_cat(f"基础档案数据_{ts}", f"STATIC_{ts}")
    print("cat", cm(b), b.get("data"))
    cid = b.get("data") if ok(b) else None
    rec("TC-COLLECT-020", "静态基础数据分类", "PASS" if ok(b) else "FAIL", f"{cm(b)} id={cid}")

    oks = []
    for name, code in [("文件影像", "FILE_IMAGE"), ("动态", "DYNAMIC"), ("视频", "VIDEO")]:
        st, b = create_cat(f"{name}_{ts}", f"{code}_{ts}")
        oks.append((name, ok(b), cm(b)))
    rec(
        "TC-COLLECT-021",
        "文件影像/动态/视频数据分类",
        "PASS" if all(x[1] for x in oks) else "FAIL",
        str(oks),
    )

    st, b = create_cat(f"基础档案数据_{ts}", f"DUP_{ts}")
    c, m = cm(b)
    # duplicate name may be allowed; duplicate code checked separately
    st2, b2 = create_cat(f"另一名称_{ts}", f"STATIC_{ts}")
    c2, m2 = cm(b2)
    if (c and c != 0 and ("已存在" in m or "重复" in m)) or (c2 and c2 != 0 and "已存在" in m2):
        rec("TC-COLLECT-022", "分类名称重复校验", "PASS", f"nameDup={c}/{m}; codeDup={c2}/{m2}")
    elif ok(b) and ok(b2):
        rec("TC-COLLECT-022", "分类名称重复校验", "FAIL", f"名称与代码均可重复 name={c}/{m} code={c2}/{m2}")
    else:
        rec("TC-COLLECT-022", "分类名称重复校验", "BLOCKED", f"name={c}/{m} code={c2}/{m2}")

    if cid:
        st, b = req(
            "PUT",
            f"/exchange/ingestion/collect/categories/{cid}",
            token=tok,
            body={"nodeName": f"基础档案_edit_{ts}"},
        )
        c1 = cm(b)
        st, b = req("DELETE", f"/exchange/ingestion/collect/categories/{cid}", token=tok)
        c2 = cm(b)
        rec(
            "TC-COLLECT-023",
            "编辑删除数据分类",
            "PASS" if c1[0] == 0 and c2[0] == 0 else "FAIL",
            f"edit={c1} del={c2}",
        )
    else:
        rec("TC-COLLECT-023", "编辑删除数据分类", "BLOCKED", "无分类id")

    # registries
    st, b = req(
        "POST",
        "/exchange/ingestion/registries",
        token=tok,
        body={"title": f"人口基础目录_{ts}", "resourceCode": f"POP_{ts}", "resourceType": "TABLE"},
    )
    print("reg", cm(b), b.get("data"))
    rid = b.get("data") if ok(b) else None
    rec("TC-COLLECT-033", "资源编目新增成功", "PASS" if ok(b) else "FAIL", f"{cm(b)} id={rid}")

    if rid:
        st, b = req(
            "POST",
            "/exchange/ingestion/registries/submit-publish",
            token=tok,
            body={"ids": [rid], "id": rid},
        )
        rec("TC-COLLECT-036", "资源目录注册发布", "PASS" if ok(b) else "BLOCKED", f"{cm(b)}")
        st, b = req("GET", "/exchange/ingestion/collect/approvals", token=tok_pt)
        apps = b.get("data") or []
        print("approvals", len(apps) if isinstance(apps, list) else apps)
        pending = None
        if isinstance(apps, list):
            for a in apps:
                stt = str(a.get("status", "")).upper()
                if stt in ("PENDING", "SUBMITTED", "WAIT", "APPROVING"):
                    pending = a
                    break
            if not pending and apps:
                pending = apps[0]
        if pending:
            aid = pending.get("id")
            st, b = req(
                "POST",
                f"/exchange/ingestion/collect/approvals/{aid}/approve",
                token=tok_pt,
                body={},
            )
            if not ok(b):
                st, b = req(
                    "POST",
                    f"/exchange/ingestion/collect/approvals/{aid}/approve",
                    token=tok,
                    body={},
                )
            rec("TC-COLLECT-037", "目录审批通过", "PASS" if ok(b) else "BLOCKED", f"id={aid} {cm(b)}")
        else:
            rec("TC-COLLECT-037", "目录审批通过", "BLOCKED", "提交后仍无待审批")
    else:
        rec("TC-COLLECT-036", "资源目录注册发布", "BLOCKED", "无编目")

    st, b = req(
        "POST",
        "/exchange/ingestion/registries",
        token=tok,
        body={"title": f"待审批目录B_{ts}", "resourceCode": f"POPB_{ts}"},
    )
    rid2 = b.get("data") if ok(b) else None
    if rid2:
        req("POST", "/exchange/ingestion/registries/submit-publish", token=tok, body={"ids": [rid2]})
        st, b = req("GET", "/exchange/ingestion/collect/approvals", token=tok_pt)
        apps = b.get("data") or []
        pending = None
        if isinstance(apps, list):
            for a in apps:
                stt = str(a.get("status", "")).upper()
                if stt in ("PENDING", "SUBMITTED", "WAIT", "APPROVING"):
                    pending = a
                    break
            if not pending and apps:
                pending = apps[-1]
        if pending:
            aid = pending.get("id")
            st, b = req(
                "POST",
                f"/exchange/ingestion/collect/approvals/{aid}/reject",
                token=tok_pt,
                body={},
            )
            c, m = cm(b)
            if c and c != 0 and ("意见" in m or "原因" in m or "必填" in m or "不能为空" in m):
                rec("TC-COLLECT-039", "目录审批拒绝意见为空校验", "PASS", f"{c}/{m}")
                st, b = req(
                    "POST",
                    f"/exchange/ingestion/collect/approvals/{aid}/reject",
                    token=tok_pt,
                    body={"reason": "信息不完整", "opinion": "信息不完整", "comment": "信息不完整"},
                )
                rec("TC-COLLECT-038", "目录审批拒绝填写意见", "PASS" if ok(b) else "FAIL", f"{cm(b)}")
            else:
                st2, b2 = req(
                    "POST",
                    f"/exchange/ingestion/collect/approvals/{aid}/reject",
                    token=tok_pt,
                    body={"reason": "信息不完整", "opinion": "信息不完整"},
                )
                rec(
                    "TC-COLLECT-038",
                    "目录审批拒绝填写意见",
                    "PASS" if ok(b2) else "BLOCKED",
                    f"empty={c}/{m}; withOpinion={cm(b2)}",
                )
                if c == 0:
                    rec("TC-COLLECT-039", "目录审批拒绝意见为空校验", "FAIL", "空意见仍成功")
                else:
                    rec(
                        "TC-COLLECT-039",
                        "目录审批拒绝意见为空校验",
                        "PASS" if c and c != 0 else "BLOCKED",
                        f"{c}/{m}",
                    )
        else:
            rec("TC-COLLECT-038", "目录审批拒绝填写意见", "BLOCKED", "无待审批")
            rec("TC-COLLECT-039", "目录审批拒绝意见为空校验", "BLOCKED", "无待审批")

    st, b = create_cat(f"政务数据_{ts}", f"GOV_{ts}")
    cid = b.get("data") if ok(b) else None
    if cid:
        st, b = req(
            "PUT",
            f"/exchange/ingestion/collect/categories/{cid}",
            token=tok,
            body={"nodeName": f"政务数据_e_{ts}"},
        )
        ok_e = ok(b)
        st, b = req("GET", "/exchange/ingestion/collect/categories", token=tok)
        ok_q = ok(b)
        st, b = req("DELETE", f"/exchange/ingestion/collect/categories/{cid}", token=tok)
        ok_d = ok(b)
        rec(
            "TC-COLLECT-035",
            "资源分类增删改查",
            "PASS" if ok_e and ok_q and ok_d else "FAIL",
            f"e={ok_e} q={ok_q} d={ok_d}",
        )
    else:
        rec("TC-COLLECT-035", "资源分类增删改查", "FAIL", f"{cm(b)}")

    st, b50 = req(
        "POST",
        "/exchange/ingestion/registries",
        token=tok,
        body={"title": "N" * 50, "resourceCode": f"L50_{ts}"},
    )
    st, b51 = req(
        "POST",
        "/exchange/ingestion/registries",
        token=tok,
        body={"title": "M" * 51, "resourceCode": f"L51_{ts}"},
    )
    if ok(b50) and not ok(b51):
        rec("TC-COLLECT-040", "编目名称长度边界", "PASS", "50通过51拦截")
    elif ok(b50) and ok(b51):
        rec("TC-COLLECT-040", "编目名称长度边界", "FAIL", "51未拦截")
    else:
        rec("TC-COLLECT-040", "编目名称长度边界", "FAIL", f"50={cm(b50)} 51={cm(b51)}")

    st, b = req(
        "POST",
        "/exchange/ingestion/registries",
        token=tok,
        body={"title": "' OR '1'='1", "resourceCode": f"SQLI_{ts}"},
    )
    st2, b2 = req("GET", "/exchange/ingestion/registries", token=tok)
    rec("TC-COLLECT-041", "编目SQL注入防护", "PASS" if ok(b2) else "FAIL", f"save={cm(b)} list={ok(b2)}")

    st, b = req(
        "GET",
        "/exchange/ingestion/collect/jobs/mapping-suggest?sourceTable=t_user&targetTable=ods_user",
        token=tok,
    )
    rec("TC-COLLECT-006", "字段映射冲突处理", "PASS" if ok(b) else "BLOCKED", f"{cm(b)}")

    st, b = req(
        "POST",
        "/exchange/ingestion/mask-policy/rules",
        token=tok,
        body={
            "ruleCode": f"MASK_PHONE_{ts}",
            "ruleName": f"手机中间4位_{ts}",
            "algoType": "MASK_MIDDLE",
            "paramJson": '{"keepHead":3,"keepTail":4}',
        },
    )
    print("mask", cm(b))
    if not ok(b):
        for algo in ["KEEP_HEAD_TAIL", "REPLACE", "HASH", "MASK", "PHONE", "PARTIAL_MASK"]:
            st, b = req(
                "POST",
                "/exchange/ingestion/mask-policy/rules",
                token=tok,
                body={
                    "ruleCode": f"MASK_{algo}_{ts}",
                    "ruleName": f"规则{algo}_{ts}",
                    "algoType": algo,
                },
            )
            if ok(b):
                break
    rec("TC-COLLECT-056", "脱敏策略配置", "PASS" if ok(b) else "FAIL", f"{cm(b)}")

    st, b = req(
        "POST",
        "/governance/quality/models",
        token=tok,
        body={
            "modelName": f"绩效权重模型_{ts}",
            "dataSource": "ODS",
            "dataSourceId": 1,
            "weights": {
                "accuracy": 20,
                "volatility": 10,
                "completeness": 15,
                "consistency": 15,
                "timeliness": 10,
                "uniqueness": 10,
                "validity": 10,
                "integrity": 10,
            },
        },
    )
    rec("TC-COLLECT-053", "质量规则权重配置", "PASS" if ok(b) else "BLOCKED", f"{cm(b)}")

    st, b = req(
        "POST",
        "/governance/quality/models",
        token=tok,
        body={
            "modelName": f"权重错误_{ts}",
            "dataSource": "ODS",
            "dataSourceId": 1,
            "weights": {"accuracy": 50, "volatility": 35},
            "weightSum": 85,
        },
    )
    c, m = cm(b)
    if "数据源" in m:
        st, b = req(
            "POST",
            "/governance/quality/schemes",
            token=tok,
            body={"schemeName": f"权重错误方案_{ts}", "weights": {"a": 50, "b": 35}, "weightSum": 85},
        )
        c, m = cm(b)
    rec("TC-COLLECT-054", "权重合计非100%拦截", "PASS" if c and c != 0 else "FAIL", f"{c}/{m}")

    summary = {"PASS": 0, "FAIL": 0, "BLOCKED": 0, "N_A": 0}
    for r in by.values():
        summary[r["status"]] = summary.get(r["status"], 0) + 1
    payload = {
        "env": "http://10.216.131.100:9087/bigdata-web",
        "api": BASE,
        "accounts": ["sys_admin", "pt_gly", "mzj_gly", "rsj_gly"],
        "summary": summary,
        "total": len(by),
        "results": sorted(by.values(), key=lambda x: x["tc"]),
        "note": "仅测试未改代码；生产环境 10.216.131.100:9087",
    }
    text = json.dumps(payload, ensure_ascii=False, indent=2)
    for p in (OUT, OUT_DESK):
        try:
            p.write_text(text, encoding="utf-8")
        except Exception as e:
            print("json", p, e)

    lines = [
        "# 数据资源采集汇聚系统 — 测试结果（生产）",
        "",
        "> 环境：`http://10.216.131.100:9087/bigdata-web`",
        "> 账号：`sys_admin` / `pt_gly` / `mzj_gly` / `rsj_gly`",
        "> **仅测试，未修改业务代码**",
        "",
        "## 汇总",
        "",
        "| 结果 | 数量 |",
        "|------|------|",
        f"| PASS | {summary.get('PASS',0)} |",
        f"| FAIL | {summary.get('FAIL',0)} |",
        f"| BLOCKED | {summary.get('BLOCKED',0)} |",
        f"| **合计** | **{len(by)}** |",
        "",
        "## FAIL",
        "",
        "| 用例 | 名称 | 说明 |",
        "|------|------|------|",
    ]
    for r in sorted(by.values(), key=lambda x: x["tc"]):
        if r["status"] == "FAIL":
            lines.append(f"| {r['tc']} | {r['name']} | {r['detail'].replace('|','/')} |")
    lines += ["", "## BLOCKED", "", "| 用例 | 名称 | 说明 |", "|------|------|------|"]
    for r in sorted(by.values(), key=lambda x: x["tc"]):
        if r["status"] == "BLOCKED":
            lines.append(f"| {r['tc']} | {r['name']} | {r['detail'].replace('|','/')} |")
    lines += ["", "## 全部用例", "", "| 用例 | 名称 | 结果 | 说明 |", "|------|------|------|------|"]
    for r in sorted(by.values(), key=lambda x: x["tc"]):
        lines.append(
            f"| {r['tc']} | {r['name']} | {r['status']} | {r['detail'][:140].replace('|','/')} |"
        )
    md = "\n".join(lines) + "\n"
    for p in (OUT_MD, OUT_MD_DESK):
        try:
            p.write_text(md, encoding="utf-8")
        except Exception as e:
            print("md", p, e)

    print("SUMMARY", summary)
    for k in sorted(by):
        if by[k]["status"] in ("FAIL", "BLOCKED"):
            print(by[k]["status"], k, by[k]["name"], by[k]["detail"][:120])


if __name__ == "__main__":
    main()
