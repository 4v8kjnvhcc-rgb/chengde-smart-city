/** 业务状态码 → 中文展示（API 仍传英文码，仅 UI 转换） */

const STATUS_ZH: Record<string, string> = {
  // 通用启用
  ACTIVE: '启用',
  INACTIVE: '停用',
  ENABLED: '启用',
  DISABLED: '已停用',
  // 草稿/发布
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  PUBLISH: '发布',
  CREATE: '创建',
  ALTER: '变更',
  UPDATE: '更新',
  OFFLINE: '已下线',
  ARCHIVED: '已归档',
  // 监控/预检
  OK: '正常',
  WARN: '预警',
  CRITICAL: '严重',
  BLOCKED: '阻断',
  // 审批/订阅
  PENDING: '待处理',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CANCELLED: '已取消',
  WITHDRAWN: '已撤回',
  RETURNED: '已退回',
  SUBSCRIBED: '已订阅',
  DISTRIBUTED: '已分发',
  // 运行/任务
  IDLE: '空闲',
  READY: '已配置',
  CONFIGURED: '已配置',
  RUNNING: '运行中',
  SUCCESS: '成功',
  PARTIAL: '部分成功',
  FAILED: '失败',
  ERROR: '错误',
  COMPLETED: '已完成',
  STOPPED: '已停止',
  CLOSED: '已关闭',
  OPEN: '开放',
  WAITING: '等待中',
  // 供需
  SUBMITTED: '已提交',
  ANALYZING: '分析中',
  CONFIRMED: '已确认',
  DISPATCHED: '已分发',
  SUPERVISING: '督办中',
  FULFILLING: '履约中',
  // 采集/解析
  PARSED: '已解析',
  COMMITTED: '已入库',
  PREVIEWED: '已预览',
  UPLOADED: '已上传',
  REGISTERED: '已登记',
  // 评估
  EVALUATING: '评估中',
  SCORED: '已打分',
  // 其它常见
  NORMAL: '正常',
  ABNORMAL: '异常',
  UNKNOWN: '未知',
  NEW: '新建',
  CHANGED: '已变更',
  UNCHANGED: '未变更',
  UNREAD: '未读',
  LOCKED: '已锁定',
  EXPIRED: '已过期',
  // 访问控制授权级别
  VIEW: '查看',
  EDIT: '编辑',
  ADMIN: '管理',
  ACCESS: '访问',
  READ: '只读',
  WRITE: '读写',
  USER: '用户',
  ROLE: '角色',
  ORG: '机构',
  PROJECT: '项目',
  TABLE: '表',
  SOURCE: '数据源',
  // 治理 ETL 节点
  INPUT: '输入',
  OUTPUT: '输出',
  FILTER: '过滤',
  FIELD_PROCESS: '字段处理',
  DEDUPLICATE: '去重',
  MASK: '脱敏',
  SPLIT: '表拆分',
  SORT: '排序',
  JOIN: '关联',
  UNION: '合并',
  AGGREGATE: '聚合',
  PIVOT: '行转列',
  UNPIVOT: '列转行',
  SET_VARIABLE: '参数设置',
  VALUE_MAPPER: '值映射',
  CONSTANT: '增加常量',
  FORMULA: '计算公式',
  STRING_CUT: '字符串裁剪',
  REPLACE_STRING: '字符串替换',
  NULL_IF: '空值处理',
  IF_NULL: '空值填充',
  TYPE_CONVERT: '类型转换',
  SELECT_FIELDS: '字段选择',
  SWITCH_CASE: '流拆分',
  VALIDATOR: '数据校验',
  SCRIPT: '脚本',
  TEXT_INPUT: '文本输入',
  TEXT_OUTPUT: '文本输出',
  EXCEL_INPUT: 'Excel输入',
  INSERT_UPDATE: '插入更新',
  DB_LOOKUP: '库表查找',
  HTTP: 'HTTP接口',
  // 标准对标
  MAPPED: '已对标',
  PARTIAL: '部分对标',
  UNMAPPED: '未对标',
  // 元数据条目类型
  SOURCE: '数据源',
  CONNECTOR: '连接器',
  TABLE: '数据表',
  COLUMN: '字段',
  CATALOG: '编目',
  MODEL: '元模型',
  LINEAGE: '血缘',
  VERSION: '版本',
  METRIC: '指标',
  SERVICE: '服务',
  FILE: '文件',
  API: '接口',
  // 安全分级
  PUBLIC: '公开',
  INTERNAL: '内部',
  SENSITIVE: '敏感',
  SECRET: '核心',
  CORE: '核心',
  // 采集范围 / 符合度 / 分层
  FULL: '整库',
  PASS: '通过',
  FAIL: '不通过',
  ODS: 'ODS',
  DWD: 'DWD',
  DWS: 'DWS',
  ADS: 'ADS',
  EXTERNAL: '外部源',
  // 关系类型
  FK: '外键',
  IMPACT: '影响',
  ASSOC: '关联',
  // 健康
  UP: '可用',
  DOWN: '不可用',
  // 治理 ETL 节点类型
  INPUT: '输入',
  OUTPUT: '输出',
  FILTER: '过滤',
  FIELD_PROCESS: '字段处理',
  DEDUPLICATE: '去重',
  MASK: '脱敏',
  JOIN: '关联',
  UNION: '合并',
  SORT: '排序',
  AGGREGATE: '聚合',
  PIVOT: '透视',
  UNPIVOT: '逆透视',
}

/**
 * 将后端状态码转为中文；未知码原样返回（便于发现漏映射）。
 * 数值状态：1→启用 / 0→停用（账号等）。
 */
export function statusLabel(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'number') {
    if (value === 1) return '启用'
    if (value === 0) return '停用'
    return String(value)
  }
  const key = String(value).trim().toUpperCase()
  return STATUS_ZH[key] || String(value)
}

export function statusTagType(value: unknown): 'success' | 'warning' | 'info' | 'danger' | 'primary' {
  const key = String(value ?? '').trim().toUpperCase()
  if (['ACTIVE', 'ENABLED', 'SUCCESS', 'APPROVED', 'COMPLETED', 'CONFIRMED', 'PUBLISHED', 'READY', 'CONFIGURED', 'NORMAL', 'SUBSCRIBED', 'DISTRIBUTED', 'MAPPED', 'OK'].includes(key)) {
    return 'success'
  }
  if (['PENDING', 'RUNNING', 'ANALYZING', 'WAITING', 'DRAFT', 'SUPERVISING', 'DISPATCHED', 'FULFILLING', 'PARTIAL', 'WARN'].includes(key)) {
    return 'warning'
  }
  if (['REJECTED', 'FAILED', 'ERROR', 'CANCELLED', 'ABNORMAL', 'EXPIRED', 'RETURNED', 'STOPPED', 'UNMAPPED', 'CRITICAL', 'BLOCKED'].includes(key)) {
    return 'danger'
  }
  if (['INACTIVE', 'DISABLED', 'OFFLINE', 'CLOSED', 'WITHDRAWN', 'ARCHIVED'].includes(key)) {
    return 'info'
  }
  return 'info'
}
