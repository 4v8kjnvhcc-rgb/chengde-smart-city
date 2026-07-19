/** 数据治理画布组件注册表（全量目录 + Kettle Step 映射） */

export type CompGroup = 'io' | 'govern' | 'transform' | 'extend'

export interface GovComponentDef {
  type: string
  name: string
  group: CompGroup
  color: string
  kettleStep: string
  summary: string
  /** 是否已具备可执行 KTR 映射 */
  mapped: boolean
  /** 首次进入默认启用 */
  defaultEnabled: boolean
  /** 画布必备，不可关闭 */
  required?: boolean
  points?: string[]
}

export const GROUP_LABELS: Record<CompGroup, string> = {
  io: '数据接入',
  govern: '常用治理',
  transform: '转换清洗',
  extend: '输入输出扩展',
}

export const GOVERNANCE_COMPONENTS: GovComponentDef[] = [
  // —— 数据接入（强制）——
  {
    type: 'INPUT', name: '输入', group: 'io', color: '#409eff', kettleStep: 'TableInput',
    summary: '从表或 SQL 读取数据。', mapped: true, defaultEnabled: true, required: true,
    points: ['支持样例/SQL/指定表', '数据源复用平台登记源与分层库'],
  },
  {
    type: 'OUTPUT', name: '输出', group: 'io', color: '#626aef', kettleStep: 'TableOutput',
    summary: '写入目标表。', mapped: true, defaultEnabled: true, required: true,
    points: ['支持插入/清空后插入', '目标一般为 DWD/DWS/ADS'],
  },
  // —— 常用治理（默认启用）——
  {
    type: 'FILTER', name: '过滤', group: 'govern', color: '#67c23a', kettleStep: 'FilterRows',
    summary: '按条件过滤记录。', mapped: true, defaultEnabled: true,
  },
  {
    type: 'FIELD_PROCESS', name: '字段处理', group: 'govern', color: '#e6a23c', kettleStep: 'SelectValues',
    summary: '字段复制、重命名、清洗。', mapped: true, defaultEnabled: true,
  },
  {
    type: 'DEDUPLICATE', name: '去重', group: 'govern', color: '#909399', kettleStep: 'Unique',
    summary: '按字段去重。', mapped: true, defaultEnabled: true,
  },
  {
    type: 'MASK', name: '脱敏', group: 'govern', color: '#f56c6c', kettleStep: 'Calculator',
    summary: '模糊或 MD5 脱敏。', mapped: true, defaultEnabled: true,
  },
  {
    type: 'SPLIT', name: '表拆分', group: 'govern', color: '#c45656', kettleStep: 'SplitField',
    summary: '按分隔符将一列拆成多列。', mapped: true, defaultEnabled: true,
  },
  {
    type: 'SORT', name: '排序', group: 'govern', color: '#b88230', kettleStep: 'SortRows',
    summary: '按字段排序。', mapped: true, defaultEnabled: true,
  },
  {
    type: 'JOIN', name: '关联', group: 'govern', color: '#337ecc', kettleStep: 'MergeJoin',
    summary: '多路上游按键关联。', mapped: true, defaultEnabled: true,
  },
  {
    type: 'UNION', name: '合并', group: 'govern', color: '#529b2e', kettleStep: 'Union',
    summary: '多路数据按行合并。', mapped: true, defaultEnabled: true,
  },
  {
    type: 'AGGREGATE', name: '聚合', group: 'govern', color: '#73767a', kettleStep: 'GroupBy',
    summary: '分组聚合统计。', mapped: true, defaultEnabled: true,
  },
  {
    type: 'PIVOT', name: '行转列', group: 'govern', color: '#c45656', kettleStep: 'Denormaliser',
    summary: '透视：行转列。', mapped: true, defaultEnabled: true,
  },
  {
    type: 'UNPIVOT', name: '列转行', group: 'govern', color: '#5156c6', kettleStep: 'Normaliser',
    summary: '逆透视：列转行。', mapped: true, defaultEnabled: true,
  },
  {
    type: 'SET_VARIABLE', name: '参数设置', group: 'govern', color: '#9b59b6', kettleStep: 'SetVariable',
    summary: '设置运行参数。', mapped: true, defaultEnabled: true,
  },
  // —— 转换清洗（默认关闭）——
  {
    type: 'VALUE_MAPPER', name: '值映射', group: 'transform', color: '#67c23a', kettleStep: 'ValueMapper',
    summary: '码表/枚举标准化。', mapped: true, defaultEnabled: false,
  },
  {
    type: 'CONSTANT', name: '增加常量', group: 'transform', color: '#909399', kettleStep: 'Constant',
    summary: '增加常量列。', mapped: true, defaultEnabled: false,
  },
  {
    type: 'FORMULA', name: '计算公式', group: 'transform', color: '#e6a23c', kettleStep: 'Formula',
    summary: '公式派生字段。', mapped: true, defaultEnabled: false,
  },
  {
    type: 'STRING_CUT', name: '字符串裁剪', group: 'transform', color: '#409eff', kettleStep: 'StringCut',
    summary: '截取/裁剪字符串。', mapped: true, defaultEnabled: false,
  },
  {
    type: 'REPLACE_STRING', name: '字符串替换', group: 'transform', color: '#409eff', kettleStep: 'ReplaceString',
    summary: '查找替换字符串。', mapped: true, defaultEnabled: false,
  },
  {
    type: 'NULL_IF', name: '空值处理', group: 'transform', color: '#f56c6c', kettleStep: 'NullIf',
    summary: '指定值转为空。', mapped: true, defaultEnabled: false,
  },
  {
    type: 'IF_NULL', name: '空值填充', group: 'transform', color: '#f56c6c', kettleStep: 'IfNull',
    summary: '空值填充默认值。', mapped: true, defaultEnabled: false,
  },
  {
    type: 'TYPE_CONVERT', name: '类型转换', group: 'transform', color: '#626aef', kettleStep: 'SelectValues',
    summary: '字段类型元数据转换。', mapped: true, defaultEnabled: false,
  },
  {
    type: 'SELECT_FIELDS', name: '字段选择', group: 'transform', color: '#e6a23c', kettleStep: 'SelectValues',
    summary: '选择/删除/重命名字段。', mapped: true, defaultEnabled: false,
  },
  {
    type: 'SWITCH_CASE', name: '流拆分', group: 'transform', color: '#337ecc', kettleStep: 'SwitchCase',
    summary: '按字段值分流。', mapped: true, defaultEnabled: false,
  },
  {
    type: 'VALIDATOR', name: '数据校验', group: 'transform', color: '#c45656', kettleStep: 'Validator',
    summary: '规则校验。', mapped: true, defaultEnabled: false,
  },
  {
    type: 'SCRIPT', name: '脚本', group: 'transform', color: '#9b59b6', kettleStep: 'ScriptValueMod',
    summary: 'JavaScript 脚本处理（慎用）。', mapped: true, defaultEnabled: false,
  },
  // —— 扩展 ——
  {
    type: 'TEXT_INPUT', name: '文本输入', group: 'extend', color: '#409eff', kettleStep: 'TextFileInput',
    summary: 'CSV/文本文件输入。', mapped: true, defaultEnabled: false,
  },
  {
    type: 'TEXT_OUTPUT', name: '文本输出', group: 'extend', color: '#626aef', kettleStep: 'TextFileOutput',
    summary: 'CSV/文本文件输出。', mapped: true, defaultEnabled: false,
  },
  {
    type: 'EXCEL_INPUT', name: 'Excel输入', group: 'extend', color: '#67c23a', kettleStep: 'ExcelInput',
    summary: 'Excel 文件输入。', mapped: true, defaultEnabled: false,
  },
  {
    type: 'INSERT_UPDATE', name: '插入更新', group: 'extend', color: '#e6a23c', kettleStep: 'InsertUpdate',
    summary: '目标表 upsert。', mapped: true, defaultEnabled: false,
  },
  {
    type: 'DB_LOOKUP', name: '库表查找', group: 'extend', color: '#337ecc', kettleStep: 'DBLookup',
    summary: '维表补全。', mapped: true, defaultEnabled: false,
  },
  {
    type: 'HTTP', name: 'HTTP接口', group: 'extend', color: '#909399', kettleStep: 'Rest',
    summary: 'HTTP/REST 取数。', mapped: true, defaultEnabled: false,
  },
]

const STORAGE_PREFIX = 'gov.canvas.enabledTypes.'

export function storageKey(username?: string | null): string {
  return STORAGE_PREFIX + (username || 'system')
}

export function defaultEnabledTypes(): string[] {
  return GOVERNANCE_COMPONENTS.filter(c => c.defaultEnabled || c.required).map(c => c.type)
}

export function loadEnabledTypes(username?: string | null): string[] {
  const user = String(username || '').trim() || 'system'
  try {
    let raw = localStorage.getItem(storageKey(user))
    // 兼容：曾写入 system 键，当前登录名不同
    if (!raw && user !== 'system') {
      raw = localStorage.getItem(storageKey('system'))
    }
    if (!raw) return defaultEnabledTypes()
    const parsed = JSON.parse(raw) as string[]
    if (!Array.isArray(parsed) || !parsed.length) return defaultEnabledTypes()
    const required = GOVERNANCE_COMPONENTS.filter(c => c.required).map(c => c.type)
    const set = new Set([...parsed, ...required])
    return Array.from(set)
  } catch {
    return defaultEnabledTypes()
  }
}

export function saveEnabledTypes(types: string[], username?: string | null) {
  const user = String(username || '').trim() || 'system'
  const required = GOVERNANCE_COMPONENTS.filter(c => c.required).map(c => c.type)
  const set = new Set([...types, ...required])
  const payload = JSON.stringify(Array.from(set))
  localStorage.setItem(storageKey(user), payload)
  // 同步写一份 system，避免用户名瞬时为空时读写分叉
  if (user !== 'system') {
    localStorage.setItem(storageKey('system'), payload)
  }
}

export function findComponent(type: string): GovComponentDef | undefined {
  return GOVERNANCE_COMPONENTS.find(c => c.type === type)
}

export function componentsByGroup(group: CompGroup): GovComponentDef[] {
  return GOVERNANCE_COMPONENTS.filter(c => c.group === group)
}
