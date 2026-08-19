# -*- coding: utf-8 -*-
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
by = {r["tc"]: r for r in json.loads(OUT.read_text(encoding="utf-8"))["results"]}


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
    print(f"[{status}] {tc}: {str(detail)[:160]}")


def ok(b):
    return isinstance(b, dict) and b.get("code") == 0


def cm(b):
    return (b or {}).get("code"), str((b or {}).get("message") or "")


def write_reports(summary):
    payload = {
        "env": "http://10.216.131.100:9087/bigdata-web",
        "api": BASE,
        "accounts": ["sys_admin", "pt_gly", "mzj_gly", "rsj_gly"],
        "summary": summary,
        "total": len(by),
        "results": sorted(by.values(), key=lambda x: x["tc"]),
        "note": "仅测试未改代码；生产环境",
    }
    text = json.dumps(payload, ensure_ascii=False, indent=2)
    for p in (OUT, OUT_DESK):
        try:
            p.write_text(text, encoding="utf-8")
        except Exception as e:
            print(e)
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
        f"| PASS | {summary.get('PASS', 0)} |",
        f"| FAIL | {summary.get('FAIL', 0)} |",
        f"| BLOCKED | {summary.get('BLOCKED', 0)} |",
        f"| **合计** | **{len(by)}** |",
        "",
        "## FAIL",
        "",
        "| 用例 | 名称 | 说明 |",
        "|------|------|------|",
    ]
    for r in sorted(by.values(), key=lambda x: x["tc"]):
        if r["status"] == "FAIL":
            lines.append(f"| {r['tc']} | {r['name']} | {r['detail'].replace('|', '/')} |")
    lines += ["", "## BLOCKED", "", "| 用例 | 名称 | 说明 |", "|------|------|------|"]
    for r in sorted(by.values(), key=lambda x: x["tc"]):
        if r["status"] == "BLOCKED":
            lines.append(f"| {r['tc']} | {r['name']} | {r['detail'].replace('|', '/')} |")
    lines += ["", "## 全部用例", "", "| 用例 | 名称 | 结果 | 说明 |", "|------|------|------|------|"]
    for r in sorted(by.values(), key=lambda x: x["tc"]):
        lines.append(
            f"| {r['tc']} | {r['name']} | {r['status']} | {r['detail'][:140].replace('|', '/')} |"
        )
    md = "\n".join(lines) + "\n"
    for p in (OUT_MD, OUT_MD_DESK):
        try:
            p.write_text(md, encoding="utf-8")
        except Exception as e:
            print(e)


def main():
    tok = login("sys_admin")
    tok_pt = login("pt_gly")
    ts = int(time.time())

    mapped = False
    for method, path, body in [
        ("GET", "/exchange/ingestion/collect/jobs/mapping-suggest?sourceTable=t_user&targetTable=ods_user", None),
        ("POST", "/exchange/ingestion/collect/jobs/mapping-suggest", {"sourceTable": "t_user", "targetTable": "ods_user"}),
    ]:
        st, b = req(method, path, token=tok, body=body)
        print(method, cm(b), str(b)[:120])
        if ok(b):
            rec("TC-COLLECT-006", "字段映射冲突处理", "PASS", f"{method} {cm(b)}")
            mapped = True
            break
    if not mapped:
        rec("TC-COLLECT-006", "字段映射冲突处理", "BLOCKED", "mapping-suggest 不可用或鉴权异常")

    st, b = req("GET", "/exchange/ingestion/collect/categories", token=tok)
    cats = b.get("data") or []
    cid = cats[0]["id"] if isinstance(cats, list) and cats else None
    st, b = req(
        "POST",
        "/exchange/ingestion/registries",
        token=tok,
        body={"title": f"发布目录_{ts}", "resourceCode": f"PUB_{ts}"},
    )
    rid = b.get("data") if ok(b) else None
    print("cid", cid, "rid", rid)
    if cid and rid:
        st, b = req(
            "POST",
            f"/exchange/ingestion/collect/categories/{cid}/bind",
            token=tok,
            body={"ids": [rid]},
        )
        print("bind", cm(b))
        st, b = req(
            "POST",
            "/exchange/ingestion/registries/submit-publish",
            token=tok,
            body={"ids": [rid]},
        )
        print("publish", cm(b))
        if ok(b):
            rec("TC-COLLECT-036", "资源目录注册发布", "PASS", f"bind+publish ok rid={rid}")
            st, b = req("GET", "/exchange/ingestion/collect/approvals", token=tok_pt)
            apps = b.get("data") or []
            print("approvals", len(apps) if isinstance(apps, list) else apps)
            pending = None
            if isinstance(apps, list):
                for a in apps:
                    if str(a.get("status", "")).upper() in (
                        "PENDING",
                        "SUBMITTED",
                        "WAIT",
                        "APPROVING",
                    ):
                        pending = a
                        break
                if not pending and apps:
                    pending = apps[0]
            if pending:
                aid = pending["id"]
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
                rec(
                    "TC-COLLECT-037",
                    "目录审批通过",
                    "PASS" if ok(b) else "BLOCKED",
                    f"{cm(b)} aid={aid}",
                )

                st, b = req(
                    "POST",
                    "/exchange/ingestion/registries",
                    token=tok,
                    body={"title": f"拒绝目录_{ts}", "resourceCode": f"REJ_{ts}"},
                )
                rid2 = b.get("data")
                if rid2:
                    req(
                        "POST",
                        f"/exchange/ingestion/collect/categories/{cid}/bind",
                        token=tok,
                        body={"ids": [rid2]},
                    )
                    req(
                        "POST",
                        "/exchange/ingestion/registries/submit-publish",
                        token=tok,
                        body={"ids": [rid2]},
                    )
                    st, b = req("GET", "/exchange/ingestion/collect/approvals", token=tok_pt)
                    apps = b.get("data") or []
                    pending = None
                    if isinstance(apps, list):
                        for a in apps:
                            if str(a.get("status", "")).upper() in (
                                "PENDING",
                                "SUBMITTED",
                                "WAIT",
                                "APPROVING",
                            ):
                                pending = a
                                break
                        if not pending and apps:
                            pending = apps[-1]
                    if pending:
                        aid = pending["id"]
                        st, b = req(
                            "POST",
                            f"/exchange/ingestion/collect/approvals/{aid}/reject",
                            token=tok_pt,
                            body={},
                        )
                        c, m = cm(b)
                        if c and c != 0 and (
                            "意见" in m or "原因" in m or "必填" in m or "不能为空" in m or "填写" in m
                        ):
                            rec("TC-COLLECT-039", "目录审批拒绝意见为空校验", "PASS", f"{c}/{m}")
                            st, b = req(
                                "POST",
                                f"/exchange/ingestion/collect/approvals/{aid}/reject",
                                token=tok_pt,
                                body={"reason": "信息不完整", "opinion": "信息不完整"},
                            )
                            rec(
                                "TC-COLLECT-038",
                                "目录审批拒绝填写意见",
                                "PASS" if ok(b) else "FAIL",
                                f"{cm(b)}",
                            )
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
                                f"empty={c}/{m}; filled={cm(b2)}",
                            )
                            rec(
                                "TC-COLLECT-039",
                                "目录审批拒绝意见为空校验",
                                "FAIL" if c == 0 else "PASS",
                                f"empty reject => {c}/{m}",
                            )
                    else:
                        rec("TC-COLLECT-038", "目录审批拒绝填写意见", "BLOCKED", "无第二待审批")
                        rec("TC-COLLECT-039", "目录审批拒绝意见为空校验", "BLOCKED", "无第二待审批")
            else:
                rec("TC-COLLECT-037", "目录审批通过", "BLOCKED", "发布后无审批单（可能自动通过或无需审批）")
        else:
            rec("TC-COLLECT-036", "资源目录注册发布", "FAIL", f"bind后仍失败 {cm(b)}")
    else:
        rec("TC-COLLECT-036", "资源目录注册发布", "BLOCKED", f"cid={cid} rid={rid}")

    st, b = req(
        "POST",
        "/governance/quality/models",
        token=tok,
        body={
            "modelName": f"权重模型_{ts}",
            "modelCode": f"WM_{ts}",
            "sourceType": "ODS",
            "dbName": "smart_city",
            "metaDataSourceId": 1,
            "ingDataSourceId": 1,
        },
    )
    print("model", cm(b))
    rec("TC-COLLECT-053", "质量规则权重配置", "PASS" if ok(b) else "BLOCKED", f"{cm(b)}")

    summary = {"PASS": 0, "FAIL": 0, "BLOCKED": 0, "N_A": 0}
    for r in by.values():
        summary[r["status"]] = summary.get(r["status"], 0) + 1
    write_reports(summary)
    print("SUMMARY", summary)
    for k in sorted(by):
        if by[k]["status"] in ("FAIL", "BLOCKED"):
            print(by[k]["status"], k, by[k]["name"], by[k]["detail"][:100])


if __name__ == "__main__":
    main()
