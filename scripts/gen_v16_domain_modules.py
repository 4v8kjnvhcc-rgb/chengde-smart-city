#!/usr/bin/env python3
"""Generate V16 migration for M152-M209 domain modules."""
from pathlib import Path

SECTIONS = [
    ("population", [
        ("M152", "人口数据采集管理", "DATA_OPS", "采集区"),
        ("M153", "人口数据分区管理", "DATA_OPS", "五区架构"),
        ("M154", "人口源目录管理", "DATA_OPS", "资源目录"),
        ("M155", "人口信息更新维护", "DATA_OPS", "更新维护"),
        ("M156", "人口信息校核", "DATA_OPS", "信息校核"),
        ("M157", "人口信息存储管理", "DATA_OPS", "存储管理"),
        ("M158", "人口信息双重授权管理", "DATA_OPS", "双重授权"),
        ("M159", "人口数据服务-接口方式", "DATA_OPS", "接口服务"),
        ("M160", "人口数据服务-批量应用方式", "DATA_OPS", "批量服务"),
        ("M161", "户籍人口统计分析模型", "ANALYSIS", "分析模型"),
        ("M162", "城镇人口统计分析模型", "ANALYSIS", "分析模型"),
        ("M163", "人口年龄结构统计分析模型", "ANALYSIS", "分析模型"),
        ("M164", "人口学历结构统计分析模型", "ANALYSIS", "分析模型"),
        ("M165", "出生人口数据统计分析模型", "ANALYSIS", "分析模型"),
        ("M166", "人口离异统计分析模型", "ANALYSIS", "分析模型"),
        ("M167", "贫困人口统计分析模型", "ANALYSIS", "分析模型"),
        ("M168", "重点人口统计分析模型", "ANALYSIS", "分析模型"),
        ("M169", "残疾人口统计分析模型", "ANALYSIS", "分析模型"),
        ("M170", "人口党员统计分析模型", "ANALYSIS", "分析模型"),
        ("M171", "常住人口同比统计分析模型", "ANALYSIS", "分析模型"),
        ("M172", "死亡人口同比统计分析模型", "ANALYSIS", "分析模型"),
        ("M173", "人口数据空间分析模型", "ANALYSIS", "分析模型"),
        ("M174", "义务教育阶段人口空间分析模型", "ANALYSIS", "分析模型"),
    ]),
    ("legal", [
        ("M175", "法人数据采集管理", "DATA_OPS", "采集区"),
        ("M176", "法人数据分区管理", "DATA_OPS", "五区架构"),
        ("M177", "法人源目录管理", "DATA_OPS", "资源目录"),
        ("M178", "法人信息更新维护", "DATA_OPS", "更新维护"),
        ("M179", "法人信息校核", "DATA_OPS", "信息校核"),
        ("M180", "法人信息存储管理", "DATA_OPS", "存储管理"),
        ("M181", "法人信息双重授权管理", "DATA_OPS", "双重授权"),
        ("M182", "法人数据服务-接口方式", "DATA_OPS", "接口服务"),
        ("M183", "法人数据服务-批量应用方式", "DATA_OPS", "批量服务"),
        ("M184", "法人年龄结构信息分析模型", "ANALYSIS", "分析模型"),
        ("M185", "法人学历结构信息分析模型", "ANALYSIS", "分析模型"),
        ("M186", "企业所得税统计分析模型", "ANALYSIS", "分析模型"),
        ("M187", "企业纳税总额统计分析模型", "ANALYSIS", "分析模型"),
        ("M188", "企业社保统计分析模型", "ANALYSIS", "分析模型"),
        ("M189", "企业规模统计分析模型", "ANALYSIS", "分析模型"),
        ("M190", "企业性质统计分析模型", "ANALYSIS", "分析模型"),
        ("M191", "法人产业结构分析模型", "ANALYSIS", "分析模型"),
        ("M192", "法人行业结构分析模型", "ANALYSIS", "分析模型"),
    ]),
    ("macro", [
        ("M193", "地方生产总值分析模型", "ANALYSIS", "分析模型"),
        ("M194", "一般公共预算收入分析模型", "ANALYSIS", "分析模型"),
        ("M195", "工业国税开票销售分析模型", "ANALYSIS", "分析模型"),
        ("M196", "行业营业收入分析模型", "ANALYSIS", "分析模型"),
        ("M197", "行业税收分析模型", "ANALYSIS", "分析模型"),
        ("M198", "外贸进出口分析模型", "ANALYSIS", "分析模型"),
        ("M199", "工业用电量分析模型", "ANALYSIS", "分析模型"),
        ("M200", "规上工业分析模型", "ANALYSIS", "分析模型"),
        ("M201", "产业增加值分析模型", "ANALYSIS", "分析模型"),
        ("M202", "实际利用外资同比环比分析模型", "ANALYSIS", "分析模型"),
        ("M203", "投资项目同比环比分析模型", "ANALYSIS", "分析模型"),
    ]),
    ("key", [
        ("M204", "应急资源空间分析模型", "ANALYSIS", "分析模型"),
        ("M205", "应急突发事件统计分析模型", "ANALYSIS", "分析模型"),
        ("M206", "应急突发事件空间分析模型", "ANALYSIS", "分析模型"),
        ("M207", "安全生产事故统计分析模型", "ANALYSIS", "分析模型"),
        ("M208", "安全生产事故空间分析模型", "ANALYSIS", "分析模型"),
        ("M209", "低保特困残疾学生统计分析模型", "ANALYSIS", "分析模型"),
    ]),
]

def esc(s: str) -> str:
    return s.replace("'", "''")

lines = [
    "-- M152~M209 domain modules + analysis model alignment",
    "",
    "CREATE TABLE IF NOT EXISTS ana_domain_module (",
    "  id BIGINT PRIMARY KEY AUTO_INCREMENT,",
    "  domain_code VARCHAR(32) NOT NULL,",
    "  m_code VARCHAR(16) NOT NULL,",
    "  module_name VARCHAR(128) NOT NULL,",
    "  module_type VARCHAR(32) NOT NULL COMMENT 'DATA_OPS|ANALYSIS',",
    "  cap_group VARCHAR(64) NULL,",
    "  de_dashboard_id VARCHAR(64) NULL,",
    "  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',",
    "  sort_order INT NOT NULL DEFAULT 0,",
    "  last_run_at DATETIME NULL,",
    "  last_message VARCHAR(512) NULL,",
    "  UNIQUE KEY uk_m_code (m_code),",
    "  KEY idx_domain (domain_code)",
    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;",
    "",
    "INSERT INTO ana_domain_module (domain_code, m_code, module_name, module_type, cap_group, de_dashboard_id, sort_order) VALUES",
]

rows = []
for domain, mods in SECTIONS:
    for m_code, name, mtype, group in mods:
        n = int(m_code[1:])
        de_id = f"de-{domain}-{n}" if mtype == "ANALYSIS" else "NULL"
        if de_id != "NULL":
            de_id = f"'{de_id}'"
        rows.append(
            f"('{domain}', '{m_code}', '{esc(name)}', '{mtype}', '{esc(group)}', {de_id}, {n})"
        )

lines.append(",\n".join(rows))
lines.append("ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), module_type = VALUES(module_type), de_dashboard_id = VALUES(de_dashboard_id);")
lines.append("")
lines.append("-- Align analysis models to D05 M161~M209 (40 models)")
lines.append("INSERT INTO ana_analysis_model (model_code, model_name, domain_code, m_code, de_dashboard_id, sample_row_count) VALUES")

amodel_rows = []
for domain, mods in SECTIONS:
    for m_code, name, mtype, _ in mods:
        if mtype != "ANALYSIS":
            continue
        n = int(m_code[1:])
        code = f"DM_{m_code}"
        de = f"de-{domain}-{n}"
        amodel_rows.append(f"('{code}', '{esc(name)}', '{domain}', '{m_code}', '{de}', 100)")

lines.append(",\n".join(amodel_rows))
lines.append("ON DUPLICATE KEY UPDATE model_name = VALUES(model_name), domain_code = VALUES(domain_code), m_code = VALUES(m_code), de_dashboard_id = VALUES(de_dashboard_id);")
lines.append("")
lines.append("UPDATE sys_menu SET component = 'analytics/AnalyticsDomainHubView' WHERE id IN (15, 16, 17, 18);")

out = Path(__file__).resolve().parents[1] / "platform-backend/src/main/resources/db/migration/V16__analytics_domain_m152_m209.sql"
out.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"Wrote {out}")
