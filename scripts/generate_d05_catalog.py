#!/usr/bin/env python3
"""Generate D05 module catalog JSON and Flyway menu migration from D05 structure."""
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_JSON = os.path.join(ROOT, "platform-backend", "src", "main", "resources", "catalog", "d05-modules.json")
OUT_SQL = os.path.join(ROOT, "platform-backend", "src", "main", "resources", "db", "migration", "V8__d05_full_menu_catalog.sql")

# platform: exchange | master-data | analytics | system
# implStatus: implemented | poc | external | stub | missing
SECTIONS = [
    # --- 一、数据共享交换平台 ---
    {"key": "ex-1-1-1", "platform": "exchange", "chapter": "一、数据共享交换平台", "name": "1.1.1 API网关（ESB）", "menuParent": 3,
     "modules": [
         ("M001", "API 设计与建模", "L1.2", "L1", "外购·AEAI ESB", "接口规范设计与全生命周期管理"),
         ("M002", "第三方服务配置", "L1.2", "L1", "外购·AEAI ESB", "外部 API 访问优化与负载配置"),
         ("M003", "第三方服务注册", "L1.2", "L1", "外购·AEAI ESB", "外部服务注册与发布管理"),
         ("M004", "服务监控", "L1.2", "L2", "外购·AEAI ESB", "自定义 API 监控与告警"),
         ("M005", "服务流量控制", "L1.2", "L1", "外购·AEAI ESB", "多粒度限流"),
         ("M006", "参数处理", "L1.2", "L1", "外购·AEAI ESB", "参数安全与转换"),
         ("M007", "黑白名单控制", "L1.2", "L1", "外购·AEAI ESB", "IP 访问控制"),
     ]},
    {"key": "ex-1-1-2", "platform": "exchange", "chapter": "一、数据共享交换平台", "name": "1.1.2 集成流程与编排（ESB）", "menuParent": 3,
     "modules": [
         ("M008", "ESB 工作流对接", "L1.2", "L2", "外购·AEAI ESB", "对接 ESB 工作流"),
         ("M009", "ESB 工作流任务管理", "L1.2", "L2", "外购·AEAI ESB", "工作流实例管理"),
         ("M010", "ESB 工作流监控", "L1.2", "L2", "外购·AEAI ESB", "运行监控告警"),
         ("M011", "ETL 作业管理", "L1.2", "L2", "外购·AEAI ESB", "MessageFlow Profile CRUD"),
         ("M012", "ETL 调度执行", "L1.2", "L2", "外购·AEAI ESB", "调度与参数"),
         ("M013", "ETL 运行监控", "L1.2", "L2", "外购·AEAI ESB", "执行监控"),
     ]},
    {"key": "ex-1-1-3", "platform": "exchange", "chapter": "一、数据共享交换平台", "name": "1.1.3 数据脱敏（ESB）", "menuParent": 3,
     "modules": [("M014", "数据脱敏与匿名化", "L1.2", "L1", "外购·AEAI ESB", "ETL/交换脱敏")]},
    {"key": "ex-1-1-4", "platform": "exchange", "chapter": "一、数据共享交换平台", "name": "1.1.4 监控分析与交换任务", "menuParent": 3,
     "modules": [
         ("M015", "API 监控分析与服务质量", "L1.2", "L1", "外购·AEAI ESB", "服务血缘与 QoS"),
         ("M016", "共享交换分析", "L1.2", "L1", "外购·AEAI ESB", "交换全局统计"),
         ("M017", "交换健康监控", "L1.2", "L1", "外购·AEAI ESB", "服务健康度"),
         ("M018", "交换任务管理", "L1.2", "L1", "外购·AEAI ESB", "交换任务"),
         ("M019", "交换任务监控", "L1.2", "L1", "外购·AEAI ESB", "过程监控"),
     ]},
    {"key": "ex-1-2-1", "platform": "exchange", "chapter": "一、数据共享交换平台", "name": "1.2.1 数据供需对接", "menuParent": 3,
     "modules": [
         ("M020", "数据需求管理", "L1.3", "L1", "纯自研", "需求管理"),
         ("M021", "数据需求分析", "L1.3", "L1", "纯自研", "需求分析"),
         ("M022", "数据需求确认", "L1.3", "L1", "纯自研", "需求确认"),
         ("M023", "数据供给查看", "L1.3", "L1", "纯自研", "供给查看"),
         ("M024", "目录清单", "L1.3", "L1", "纯自研", "目录清单"),
         ("M025", "异议清单", "L1.3", "L1", "纯自研", "异议清单"),
         ("M026", "供需清单", "L1.3", "L1", "纯自研", "供需清单"),
     ]},
    {"key": "ex-1-2-2", "platform": "exchange", "chapter": "一、数据共享交换平台", "name": "1.2.2 考核评估系统", "menuParent": 3,
     "modules": [
         ("M027", "评价数据来源", "L1.3", "L2", "纯自研", "数据来源"),
         ("M028", "评价周期管理", "L1.3", "L1", "纯自研", "评价周期"),
         ("M029", "评价指标管理", "L1.3", "L1", "纯自研", "指标体系"),
         ("M030", "评价执行与结果", "L1.3", "L1", "纯自研", "评价执行"),
     ]},
    {"key": "ex-1-2-3", "platform": "exchange", "chapter": "一、数据共享交换平台", "name": "1.2.3 应用分析门户", "menuParent": 3,
     "modules": [
         ("M031", "部门数据共享门户", "L1.4", "L1", "纯自研", "共享门户"),
         ("M032", "门户首页", "L1.4", "L1", "纯自研", "首页"),
         ("M033", "全文资源检索", "L1.4", "L1", "开源集成·Elasticsearch", "全文检索"),
         ("M034", "资源目录检索", "L1.4", "L1", "纯自研", "目录检索"),
         ("M035", "资源订阅申请", "L1.4", "L1", "纯自研", "订阅申请"),
         ("M036", "领导决策门户", "L1.4", "L2", "集成+自研·DataEase(iframe)", "决策大屏八态势"),
         ("M037", "基础库统计分析", "L1.3", "L2", "纯自研", "基础库分析"),
         ("M038", "重点领域统计分析", "L1.3", "L2", "纯自研", "重点领域"),
     ]},
    # --- 二、大数据归集平台 ---
    {"key": "ing-2-1", "platform": "exchange", "chapter": "二、大数据归集平台", "name": "2.1 数据资产登记管理", "menuParent": 3,
     "modules": [
         ("M039", "填报指引", "L1.1", "L1", "纯自研", "登记引导"),
         ("M040", "项目/系统信息登记", "L1.1", "L1", "纯自研", "项目与系统"),
         ("M041", "数据库/表/项登记", "L1.1", "L1", "纯自研", "数据源与模型"),
         ("M042", "数据字典登记", "L1.1", "L1", "纯自研", "字典登记"),
         ("M043", "数据资产标签登记", "L1.1", "L1", "纯自研", "标签登记"),
         ("M044", "数据项管理", "L1.1", "L1", "纯自研", "数据项"),
         ("M045", "数据资产标签管理", "L1.1", "L1", "纯自研", "标签体系"),
         ("M046", "数据资产报告", "L1.1", "L1", "纯自研", "资产大屏"),
         ("M047", "数据资产图谱分析", "L1.1", "L1", "纯自研", "血缘图谱"),
         ("M048", "访问控制管理", "L1.1", "L1", "纯自研", "权限管理"),
         ("M049", "系统维护管理", "L1.1", "L1", "纯自研", "系统维护"),
         ("M050", "数据字典管理", "L1.1", "L1", "纯自研", "字典管理"),
     ]},
    {"key": "ing-2-2-1", "platform": "exchange", "chapter": "二、大数据归集平台", "name": "2.2.1 数据上传与汇聚", "menuParent": 3,
     "modules": [
         ("M051", "上传模板管理", "L1.1", "L1", "纯自研", "上传模板"),
         ("M052", "数据上传管理", "L1.1", "L1", "纯自研", "上传执行"),
         ("M053", "数据上传记录", "L1.1", "L1", "纯自研", "上传记录"),
         ("M054", "结构化数据接入", "L1.1", "L1", "纯自研", "库表接入"),
         ("M055", "远程文件接入（FTP）", "L1.1", "L2", "纯自研", "FTP"),
         ("M056", "本地文件接入", "L1.1", "L2", "纯自研", "本地文件"),
         ("M057", "非结构化数据接入", "L1.1", "L2", "开源集成·SeaweedFS", "FTP 至文件存储"),
         ("M058", "半结构化数据接入", "L1.1", "L3", "开源集成·MongoDB", "Kafka/MongoDB/ES"),
         ("M059", "API 接口数据接入", "L1.1", "L1", "纯自研", "REST 采集"),
         ("M060", "CDC 实时数据接入", "L1.1", "L1", "开源集成·Canal", "MySQL 变更捕获"),
     ]},
    {"key": "ing-2-2-2", "platform": "exchange", "chapter": "二、大数据归集平台", "name": "2.2.2 规范设计", "menuParent": 3,
     "modules": [
         ("M061", "数据探查", "L1.1", "L2", "纯自研", "数据探查"),
         ("M062", "数据定义", "L1.1", "L2", "纯自研", "数据定义"),
         ("M063", "数据读取", "L1.1", "L2", "纯自研", "数据读取"),
         ("M064", "数据对账", "L1.1", "L2", "纯自研", "数据对账"),
     ]},
    {"key": "ing-2-2-3", "platform": "exchange", "chapter": "二、大数据归集平台", "name": "2.2.3 指标与目录体系", "menuParent": 3,
     "modules": [
         ("M065", "数据资源编目管理", "L1.1", "L1", "纯自研", "编目"),
         ("M066", "数据资源分类", "L1.1", "L1", "纯自研", "分类"),
         ("M067", "资源目录注册发布", "L1.1", "L1", "纯自研", "注册发布"),
         ("M068", "数据资源目录审批", "L1.1", "L1", "纯自研", "审批"),
     ]},
    {"key": "ing-2-2-4", "platform": "exchange", "chapter": "二、大数据归集平台", "name": "2.2.4 数据资产管理", "menuParent": 3,
     "modules": [
         ("M069", "数据分级分类", "L1.1", "L2", "纯自研", "分级分类"),
         ("M070", "数据脱敏策略", "L1.1", "L2", "纯自研", "脱敏"),
         ("M071", "数据标签管理", "L1.1", "L2", "纯自研", "标签"),
         ("M072", "数据搜索引擎", "L1.1", "L2", "开源集成·Elasticsearch", "搜索"),
         ("M073", "数据备份", "L1.1", "L2", "纯自研", "备份"),
         ("M074", "数据归档", "L1.1", "L2", "纯自研", "归档"),
         ("M075", "数据销毁", "L1.1", "L2", "纯自研", "销毁与回收"),
         ("M076", "全局数据资产视图", "L1.1", "L2", "纯自研", "全局视图"),
         ("M077", "健康监控", "L1.1", "L2", "纯自研", "健康监控"),
     ]},
    # --- 三、主数据平台 ---
    {"key": "md-3-1-3", "platform": "master-data", "chapter": "三、主数据平台", "name": "3.1.3 数据质量中心", "menuParent": 8,
     "modules": [
         ("M078", "质量规则配置", "L2.1", "L1", "集成+自研·OpenMetadata", "8 类稽核规则"),
         ("M079", "数据质量任务配置", "L2.1", "L1", "集成+自研·OpenMetadata", "启停告警"),
         ("M080", "数据质量监控", "L2.1", "L1", "集成+自研·OpenMetadata", "工单血统"),
         ("M081", "数据标准监控", "L2.1", "L2", "纯自研", "命名对标"),
         ("M082", "数据质量评估", "L2.1", "L1", "集成+自研·OpenMetadata", "六性指标"),
         ("M083", "数据质量分析报告", "L2.1", "L1", "集成+自研·OpenMetadata", "多维度下钻"),
         ("M084", "数据标准体系管理", "L2.1", "L2", "纯自研", "标准体系"),
         ("M085", "标准映射与稽核联动", "L2.1", "L2", "纯自研", "映射联动"),
         ("M102", "数据元标准管理", "L2.1", "L1", "纯自研", "数据元"),
         ("M103", "数据编码规范管理", "L2.1", "L1", "纯自研", "编码"),
         ("M104", "命名规范管理", "L2.1", "L1", "纯自研", "命名"),
         ("M105", "标准文件管理", "L2.1", "L1", "纯自研", "标准文件"),
     ]},
    {"key": "md-3-1-1", "platform": "master-data", "chapter": "三、主数据平台", "name": "3.1.1 元数据管理", "menuParent": 8,
     "modules": [
         ("M086", "适配器管理", "L2.1", "L1", "开源集成·OpenMetadata", "适配器"),
         ("M087", "数据源分类管理", "L2.1", "L1", "开源集成·OpenMetadata", "分类"),
         ("M088", "数据源管理", "L2.1", "L1", "开源集成·OpenMetadata", "数据源"),
         ("M089", "元模型管理", "L2.1", "L1", "开源集成·OpenMetadata", "元模型"),
         ("M090", "元数据采集", "L2.1", "L1", "开源集成·OpenMetadata", "采集"),
         ("M091", "元数据采集监控", "L2.1", "L1", "开源集成·OpenMetadata", "采集监控"),
         ("M092", "元数据维护", "L2.1", "L1", "开源集成·OpenMetadata", "维护"),
         ("M093", "元数据版本管理", "L2.1", "L1", "开源集成·OpenMetadata", "版本"),
         ("M094", "元数据复制", "L2.1", "L1", "开源集成·OpenMetadata", "复制"),
         ("M095", "元数据目录", "L2.1", "L1", "开源集成·OpenMetadata", "目录"),
         ("M096", "元数据分析", "L2.1", "L1", "开源集成·OpenMetadata", "分析"),
         ("M097", "字典管理", "L2.1", "L1", "开源集成·OpenMetadata", "字典"),
     ]},
    {"key": "md-3-1-2", "platform": "master-data", "chapter": "三、主数据平台", "name": "3.1.2 数据治理", "menuParent": 8,
     "modules": [
         ("M098", "治理任务管理", "L2.1", "L1", "集成+自研·DS+OpenMetadata", "任务调度"),
         ("M099", "可视化 ETL 治理开发", "L2.1", "L2", "开源集成·Kettle", "治理 ETL"),
         ("M100", "ETL 治理监控", "L2.1", "L1", "开源集成·DolphinScheduler", "监控"),
         ("M101", "数据治理组件库", "L2.1", "L1", "纯自研", "组件库"),
     ]},
    {"key": "md-3-1-4", "platform": "master-data", "chapter": "三、主数据平台", "name": "3.1.4 数据融合", "menuParent": 8,
     "modules": [
         ("M106", "逻辑模型管理", "L2.1", "L1", "纯自研", "逻辑模型"),
         ("M107", "物理模型管理", "L2.1", "L1", "纯自研", "物理模型"),
         ("M108", "模型报告", "L2.1", "L1", "纯自研", "模型报告"),
         ("M109", "数据开发（脚本）", "L2.1", "L1", "纯自研", "脚本"),
         ("M110", "工作流调度", "L2.1", "L1", "开源集成·DolphinScheduler", "调度"),
         ("M111", "数据融合组件库", "L2.1", "L1", "纯自研", "融合算子"),
     ]},
    {"key": "md-3-1-5", "platform": "master-data", "chapter": "三、主数据平台", "name": "3.1.5 数据目录管理", "menuParent": 8,
     "modules": [
         ("M112", "目录分类", "L2.1", "L1", "集成+自研·OpenMetadata", "分类"),
         ("M113", "数据资源编目", "L2.1", "L1", "集成+自研·OpenMetadata", "编目"),
         ("M114", "服务资源编目", "L2.1", "L1", "集成+自研·OpenMetadata", "服务编目"),
         ("M115", "目录注册发布", "L2.1", "L1", "集成+自研·OpenMetadata", "注册发布"),
         ("M116", "目录审批", "L2.1", "L1", "集成+自研·OpenMetadata", "审批"),
         ("M117", "目录查询与维护", "L2.1", "L1", "集成+自研·OpenMetadata", "运维"),
         ("M118", "目录版本管理", "L2.1", "L1", "集成+自研·OpenMetadata", "版本"),
         ("M119", "资源目录门户", "L2.1", "L1", "集成+自研·OpenMetadata", "门户"),
         ("M120", "资源申请订阅", "L2.1", "L1", "集成+自研·OpenMetadata", "订阅"),
         ("M121", "资源订阅审批", "L2.1", "L1", "集成+自研·OpenMetadata", "审批"),
         ("M122", "资源订阅分发", "L2.1", "L1", "集成+自研·OpenMetadata", "分发"),
     ]},
    {"key": "md-3-2", "platform": "master-data", "chapter": "三、主数据平台", "name": "3.2 非结构化治理", "menuParent": 8,
     "modules": [
         ("M123", "数据分类管理", "L2.2", "L2", "纯自研", "分类"),
         ("M124", "文件资源管理", "L2.2", "L2", "开源集成·SeaweedFS", "文件目录"),
         ("M125", "文件资源检索", "L2.2", "L2", "开源集成·Elasticsearch", "检索"),
         ("M126", "非结构化元数据管理", "L2.2", "L2", "开源集成·Elasticsearch", "元数据"),
         ("M127", "非结构化数据清洗转换", "L2.2", "L2", "纯自研", "清洗"),
         ("M128", "非结构化数据标识处理", "L2.2", "L2", "纯自研", "标识"),
         ("M129", "非结构化数据关联处理", "L2.2", "L2", "纯自研", "关联"),
     ]},
    {"key": "md-3-3-1", "platform": "master-data", "chapter": "三、主数据平台", "name": "3.3.1 数据资产区", "menuParent": 8,
     "modules": [
         ("M130", "基础库管理", "L2.3", "L1", "纯自研", "基础库"),
         ("M131", "半结构化库管理", "L2.3", "L1", "开源集成·MongoDB", "半结构化库"),
         ("M132", "非结构化库管理", "L2.3", "L1", "开源集成·SeaweedFS", "非结构化库"),
     ]},
    {"key": "md-3-3-2", "platform": "master-data", "chapter": "三、主数据平台", "name": "3.3.2 资源中心管理", "menuParent": 8,
     "modules": [
         ("M133", "分区设计管理", "L2.3", "L2", "纯自研", "分区"),
         ("M134", "数据库存储管理", "L2.3", "L2", "纯自研", "存储"),
         ("M135", "资产目录管理", "L2.3", "L2", "纯自研", "资产目录"),
         ("M136", "数据库检索查询", "L2.3", "L2", "开源集成·Elasticsearch", "检索"),
         ("M137", "数据库统计分析", "L2.3", "L2", "纯自研", "统计"),
         ("M138", "资源监控管理", "L2.3", "L2", "纯自研", "监控"),
     ]},
    # --- 四、挖掘分析 ---
    {"key": "an-4-1", "platform": "analytics", "chapter": "四、大数据挖掘分析平台", "name": "4.1 通用支撑平台", "menuParent": 12,
     "modules": [
         ("M139", "用户中心", "L3.1", "L1", "纯自研", "用户"),
         ("M140", "应用中心", "L3.1", "L1", "纯自研", "应用"),
         ("M141", "认证中心", "L3.1", "L2", "纯自研", "SSO 对接扩展"),
         ("M142", "服务中心", "L3.1", "L2", "纯自研", "API 管理"),
         ("M143", "系统管理", "L3.1", "L1", "纯自研", "系统配置"),
         ("M144", "日志审计", "L3.1", "L1", "纯自研", "审计"),
         ("M145", "系统对接", "L3.1", "L1", "纯自研", "第三方配置"),
     ]},
    {"key": "an-4-2", "platform": "analytics", "chapter": "四、大数据挖掘分析平台", "name": "4.2 智能 BI 平台", "menuParent": 12,
     "modules": [
         ("M146", "显示引擎", "L3.1", "L3", "开源集成·DataEase(iframe)", "大屏显示"),
         ("M147", "组件引擎", "L3.1", "L3", "开源集成·DataEase(iframe)", "可视化组件"),
         ("M148", "地图管理", "L3.1", "L3", "开源集成·DataEase(iframe)", "GIS"),
         ("M149", "数据源管理", "L3.1", "L2", "开源集成·DataEase(iframe)", "BI 数据源"),
         ("M150", "可视化设计", "L3.1", "L3", "开源集成·DataEase(iframe)", "专题大屏"),
         ("M151", "自助分析", "L3.1", "L3", "开源集成·DataEase(iframe)", "查询报表"),
     ]},
    {"key": "an-4-3-1", "platform": "analytics", "chapter": "四、大数据挖掘分析平台", "name": "4.3.1 人口大数据", "menuParent": 12,
     "modules": [
         ("M152", "人口数据采集管理", "L3.2", "L1", "纯自研", "采集区"),
         ("M153", "人口数据分区管理", "L3.2", "L1", "纯自研", "五区架构"),
         ("M154", "人口源目录管理", "L3.2", "L1", "纯自研", "资源目录"),
         ("M155", "人口信息更新维护", "L3.2", "L1", "纯自研", "更新维护"),
         ("M156", "人口信息校核", "L3.2", "L1", "纯自研", "信息校核"),
         ("M157", "人口信息存储管理", "L3.2", "L1", "纯自研", "存储管理"),
         ("M158", "人口信息双重授权管理", "L3.2", "L1", "纯自研", "双重授权"),
         ("M159", "人口数据服务-接口方式", "L3.2", "L1", "纯自研", "接口服务"),
         ("M160", "人口数据服务-批量应用方式", "L3.2", "L1", "纯自研", "批量服务"),
         ("M161", "户籍人口统计分析模型", "L3.2", "L2", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M162", "城镇人口统计分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M163", "人口年龄结构统计分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M164", "人口学历结构统计分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M165", "出生人口数据统计分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M166", "人口离异统计分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M167", "贫困人口统计分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M168", "重点人口统计分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M169", "残疾人口统计分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M170", "人口党员统计分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M171", "常住人口同比统计分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M172", "死亡人口同比统计分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M173", "人口数据空间分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M174", "义务教育阶段人口空间分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
     ]},
    {"key": "an-4-3-2", "platform": "analytics", "chapter": "四、大数据挖掘分析平台", "name": "4.3.2 法人大数据", "menuParent": 12,
     "modules": [
         ("M175", "法人数据采集管理", "L3.2", "L1", "集成+自研·DataEase(iframe)", "采集区"),
         ("M176", "法人数据分区管理", "L3.2", "L1", "集成+自研·DataEase(iframe)", "五区架构"),
         ("M177", "法人源目录管理", "L3.2", "L1", "集成+自研·DataEase(iframe)", "资源目录"),
         ("M178", "法人信息更新维护", "L3.2", "L1", "集成+自研·DataEase(iframe)", "更新维护"),
         ("M179", "法人信息校核", "L3.2", "L1", "集成+自研·DataEase(iframe)", "信息校核"),
         ("M180", "法人信息存储管理", "L3.2", "L1", "集成+自研·DataEase(iframe)", "存储管理"),
         ("M181", "法人信息双重授权管理", "L3.2", "L1", "集成+自研·DataEase(iframe)", "双重授权"),
         ("M182", "法人数据服务-接口方式", "L3.2", "L1", "集成+自研·DataEase(iframe)", "接口服务"),
         ("M183", "法人数据服务-批量应用方式", "L3.2", "L1", "集成+自研·DataEase(iframe)", "批量服务"),
         ("M184", "法人年龄结构信息分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M185", "法人学历结构信息分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M186", "企业所得税统计分析模型", "L3.2", "L2", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M187", "企业纳税总额统计分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M188", "企业社保统计分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M189", "企业规模统计分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M190", "企业性质统计分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M191", "法人产业结构分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M192", "法人行业结构分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
     ]},
    {"key": "an-4-3-3", "platform": "analytics", "chapter": "四、大数据挖掘分析平台", "name": "4.3.3 宏观经济", "menuParent": 12,
     "modules": [
         ("M193", "地方生产总值分析模型", "L3.2", "L2", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M194", "一般公共预算收入分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M195", "工业国税开票销售分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M196", "行业营业收入分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M197", "行业税收分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M198", "外贸进出口分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M199", "工业用电量分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M200", "规上工业分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M201", "产业增加值分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M202", "实际利用外资同比环比分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M203", "投资项目同比环比分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
     ]},
    {"key": "an-4-3-4", "platform": "analytics", "chapter": "四、大数据挖掘分析平台", "name": "4.3.4 重点领域", "menuParent": 12,
     "modules": [
         ("M204", "应急资源空间分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M205", "应急突发事件统计分析模型", "L3.2", "L2", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M206", "应急突发事件空间分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M207", "安全生产事故统计分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M208", "安全生产事故空间分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
         ("M209", "低保特困残疾学生统计分析模型", "L3.2", "L3", "集成+自研·DataEase(iframe)", "分析模型"),
     ]},
    # --- 五、跨平台 ---
    {"key": "cross-5", "platform": "system", "chapter": "五、跨平台公共能力", "name": "跨平台公共能力", "menuParent": 19,
     "modules": [
         ("M210", "统一门户登录", "跨平台", "L1", "纯自研", "统一登录"),
         ("M211", "统一系统管理", "跨平台", "L1", "纯自研", "系统管理"),
         ("M212", "平台事件总线", "跨平台", "L1", "纯自研", "事件联动"),
         ("M213", "联通基础管理", "跨平台", "L1", "纯自研", "联通"),
         ("M214", "ESB 集成层", "跨平台", "L1", "集成+自研·ESB适配", "ESB 集成"),
         ("M215", "Kettle ETL 集成", "跨平台", "L1", "开源集成·Kettle", "Kettle ETL"),
     ]},
]

PLATFORM_META = {
    "exchange": {"path": "/exchange", "name": "数据共享交换平台", "catalogPath": "/catalog/exchange"},
    "master-data": {"path": "/master-data", "name": "主数据平台", "catalogPath": "/catalog/master-data"},
    "analytics": {"path": "/analytics", "name": "大数据挖掘分析平台", "catalogPath": "/catalog/analytics"},
    "system": {"path": "/system", "name": "系统管理", "catalogPath": "/catalog/system"},
}

D05_ROOT_IDS = {"exchange": 1000, "master-data": 2000, "analytics": 3000, "system": 4000}
CATALOG_OVERVIEW_IDS = {"exchange": 1001, "master-data": 2001, "analytics": 3001, "system": 4001}

def resolve_status(m_code: str, impl_type: str) -> tuple[str, str | None, str | None]:
    """Return (implStatus, implRoute, externalUrl)."""
    n = int(m_code[1:])
    if m_code == "M214" or (1 <= n <= 19):
        return "external", "/exchange/esb", "AEAI ESB（现场环境）"
    if m_code in ("M048",):
        return "implemented", "/system/orgs", None
    if m_code in ("M049",):
        return "implemented", "/system/security", None
    if m_code in ("M144",):
        return "implemented", "/system/audit", None
    if 139 <= n <= 145:
        tab = {139: "users", 140: "apps", 141: "auth", 142: "services", 143: "config", 144: "audit", 145: "integration"}[n]
        return "poc", f"/analytics/support?tab={tab}", None
    if m_code in ("M210",):
        return "implemented", "/dashboard", None
    if m_code in ("M211",):
        return "implemented", "/system/users", None
    if m_code in ("M098", "M110", "M100"):
        return "poc", "/integration/ds", None
    if m_code in ("M099", "M215"):
        return "poc", "/integration/kettle", None
    if 20 <= n <= 26:
        tab = {20: "demand", 21: "analysis", 22: "confirm", 23: "supply", 24: "catalog", 25: "objection", 26: "manifest"}[n]
        return "poc", f"/exchange/application?tab={tab}", None
    if 27 <= n <= 30:
        return "poc", "/exchange/assessment", None
    if 31 <= n <= 36:
        tab = {31: "home", 32: "home", 33: "search", 34: "catalog", 35: "subscribe", 36: "situation"}[n]
        return "poc", f"/exchange/portal?tab={tab}", None
    if 37 <= n <= 77:
        if n in (37, 38):
            return "poc", "/exchange/portal?tab=situation", None
        if n <= 50:
            return "implemented", f"/exchange/ingestion?system=register&module=m{n:03d}", None
        if n <= 53:
            mod = "upload"
        elif n <= 60:
            mod = "ingest"
        elif n <= 64:
            mod = "pipeline"
        elif n <= 68:
            mod = "catalog"
        else:
            mod = "asset"
        return "implemented", f"/exchange/ingestion?system=collect&module={mod}&section=m{n:03d}", None
    if 78 <= n <= 122:
        if 78 <= n <= 85 or 102 <= n <= 105:
            tab = "quality"
        elif 86 <= n <= 97:
            tab = "metadata"
        elif 98 <= n <= 101:
            tab = "etl"
        elif 106 <= n <= 111:
            tab = "model"
        else:
            tab = "catalog"
        return "poc", f"/governance?tab={tab}", None
    if 123 <= n <= 129:
        tab = {123: "classify", 124: "files", 125: "files", 126: "metadata", 127: "process", 128: "process", 129: "process"}[n]
        return "poc", f"/unstructured?tab={tab}", None
    if 130 <= n <= 138:
        if n <= 132:
            tab = "library"
        elif n <= 134:
            tab = "partition"
        elif n == 135:
            tab = "catalog"
        elif n <= 137:
            tab = "analytics"
        else:
            tab = "monitor"
        return "poc", f"/resource-center?tab={tab}", None
    if m_code in ("M146", "M147", "M148", "M149", "M150", "M151"):
        tab = {146: "display", 147: "component", 148: "map", 149: "datasource", 150: "design", 151: "self"}[n]
        return "poc", f"/analytics/bi?tab={tab}", "http://localhost:8100"
    if 152 <= n <= 174:
        return "poc", f"/analytics/population?tab=m{n}", None
    if 175 <= n <= 192:
        return "poc", f"/analytics/legal-entity?tab=m{n}", None
    if 193 <= n <= 203:
        return "poc", f"/analytics/macro?tab=m{n}", None
    if 204 <= n <= 209:
        return "poc", f"/analytics/key-domains?tab=m{n}", None
    if "OpenMetadata" in impl_type:
        return "external", "/governance", "http://localhost:8585"
    if "DataEase" in impl_type and 161 <= n <= 209:
        return "poc", None, "http://localhost:8100"
    if "Elasticsearch" in impl_type or "SeaweedFS" in impl_type or "Canal" in impl_type or "MongoDB" in impl_type:
        return "stub", None, None
    return "missing", None, None


def build_catalog():
    modules = []
    sections = []
    for sec in SECTIONS:
        sec_entry = {
            "key": sec["key"],
            "platform": sec["platform"],
            "chapter": sec["chapter"],
            "name": sec["name"],
            "moduleCount": len(sec["modules"]),
        }
        sections.append(sec_entry)
        for row in sec["modules"]:
            m_code, name, domain, level, impl_type, desc = row
            status, route, ext = resolve_status(m_code, impl_type)
            modules.append({
                "mCode": m_code,
                "moduleName": name,
                "sectionKey": sec["key"],
                "sectionName": sec["name"],
                "chapter": sec["chapter"],
                "platform": sec["platform"],
                "logicalDomain": domain,
                "deliveryLevel": level,
                "implType": impl_type,
                "description": desc,
                "implStatus": status,
                "implRoute": route,
                "externalUrl": ext,
            })
    return {
        "version": "D05-V2.6",
        "moduleCount": len(modules),
        "platforms": PLATFORM_META,
        "sections": sections,
        "modules": modules,
    }


def sql_escape(s: str) -> str:
    return s.replace("'", "''")


def menu_label(m_code: str, name: str) -> str:
    short = name if len(name) <= 14 else name[:13] + "…"
    return f"{m_code} {short}"


def generate_sql(catalog: dict) -> str:
    lines = [
        "-- D05 V2.6 full module menu catalog (M001~M215)",
        "-- Generated by scripts/generate_d05_catalog.py",
        "",
    ]
    next_id = 5000
    role_menus = []

    def alloc_id():
        nonlocal next_id
        next_id += 1
        return next_id

    # D05 root + catalog overview per platform
    platform_roots = {}
    for platform, root_id in D05_ROOT_IDS.items():
        meta = PLATFORM_META[platform]
        parent = {"exchange": 3, "master-data": 8, "analytics": 12, "system": 19}[platform]
        lines.append(
            f"INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) "
            f"SELECT {root_id}, {parent}, 'D05功能清单', 1, NULL, NULL, NULL, NULL, 200, NULL, 'catalog' "
            f"WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = {root_id});"
        )
        ov_id = CATALOG_OVERVIEW_IDS[platform]
        lines.append(
            f"INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) "
            f"SELECT {ov_id}, {root_id}, '功能清单总览', 2, '{meta['catalogPath']}', 'catalog/PlatformCatalogView', NULL, NULL, 1, NULL, 'catalog' "
            f"WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = {ov_id});"
        )
        role_menus.extend([root_id, ov_id])
        platform_roots[platform] = root_id

    # sections and modules
    sec_ids = {}
    sort_sec = 10
    for sec in SECTIONS:
        platform = sec["platform"]
        sec_id = alloc_id()
        sec_ids[sec["key"]] = sec_id
        parent = platform_roots[platform]
        sort_sec += 1
        lines.append(
            f"INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) "
            f"SELECT {sec_id}, {parent}, '{sql_escape(sec['name'])}', 1, NULL, NULL, NULL, NULL, {sort_sec}, NULL, 'catalog' "
            f"WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = {sec_id});"
        )
        role_menus.append(sec_id)

        sort_mod = 0
        for row in sec["modules"]:
            m_code = row[0]
            name = row[1]
            sort_mod += 1
            mod_id = alloc_id()
            path = f"/modules/{m_code}"
            label = sql_escape(menu_label(m_code, name))
            lines.append(
                f"INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) "
                f"SELECT {mod_id}, {sec_id}, '{label}', 2, '{path}', 'catalog/ModuleDetailView', NULL, NULL, {sort_mod}, '{m_code}', 'catalog' "
                f"WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE m_code = '{m_code}' AND menu_type = 2 AND path LIKE '/modules/%');"
            )
            role_menus.append(mod_id)

    lines.append("")
    lines.append("-- Grant all standard roles access to D05 catalog menus")
    for role_id in (1, 2, 3, 4):
        lines.append(
            f"INSERT INTO sys_role_menu (role_id, menu_id) "
            f"SELECT {role_id}, m.id FROM sys_menu m "
            f"WHERE m.integration_type = 'catalog' OR m.id = 5000 "
            f"AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = {role_id} AND rm.menu_id = m.id);"
        )

    # Hub menu: global D05 search
    lines.extend([
        "",
        "INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)",
        "SELECT 5000, 1, 'D05全量检索', 2, '/catalog', 'catalog/GlobalCatalogView', 'catalog:view', NULL, 3, NULL, 'catalog'",
        "WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 5000);",
        "INSERT INTO sys_role_menu (role_id, menu_id) SELECT 1, 5000 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 5000);",
    ])
    return "\n".join(lines) + "\n"


def main():
    catalog = build_catalog()
    assert catalog["moduleCount"] == 215, f"expected 215 modules, got {catalog['moduleCount']}"
    os.makedirs(os.path.dirname(OUT_JSON), exist_ok=True)
    with open(OUT_JSON, "w", encoding="utf-8") as f:
        json.dump(catalog, f, ensure_ascii=False, indent=2)
    sql = generate_sql(catalog)
    with open(OUT_SQL, "w", encoding="utf-8") as f:
        f.write(sql)
    print(f"Wrote {OUT_JSON} ({catalog['moduleCount']} modules)")
    print(f"Wrote {OUT_SQL}")


if __name__ == "__main__":
    main()
