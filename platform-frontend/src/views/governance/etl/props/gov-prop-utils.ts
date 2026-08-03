/** 治理画布属性/连线共用工具 */

export type EdgeRole = 'COPY' | 'TRUE' | 'FALSE' | 'CASE' | 'DEFAULT' | 'LEFT' | 'RIGHT'

export interface MappingRow {
  from: string
  to: string
  expr: string
}

export interface AggRow {
  field: string
  op: string
  alias: string
}

export interface FilterCondRow {
  field: string
  op: string
  value: string
  logic: 'AND' | 'OR'
}

export interface SortKeyRow {
  field: string
  order: 'ASC' | 'DESC'
}

export interface JoinKeyRow {
  leftKey: string
  rightKey: string
}

export interface ValueMapRow {
  fromValue: string
  toValue: string
}

export interface SwitchCaseRow {
  value: string
  label: string
}

export interface ConstantRow {
  field: string
  value: string
  valueType: string
}

export interface ValidatorRow {
  field: string
  ruleType: string
  param: string
  onFail: string
}

export interface LookupReturnRow {
  sourceColumn: string
  targetField: string
}

export interface TypeConvertRow {
  from: string
  to: string
  targetType: string
  dateFormat: string
}

export interface SelectFieldRow {
  from: string
  to: string
  action: 'KEEP' | 'DROP'
}

export const FILTER_OPS = [
  { value: 'EQ', label: '等于' },
  { value: 'NE', label: '不等于' },
  { value: 'GT', label: '大于' },
  { value: 'GTE', label: '大于等于' },
  { value: 'LT', label: '小于' },
  { value: 'LTE', label: '小于等于' },
  { value: 'CONTAINS', label: '包含' },
  { value: 'IS_NULL', label: '为空' },
  { value: 'NOT_NULL', label: '非空' },
]

export const FIELD_PROCESS_EXPRS = [
  { value: 'COPY', label: '原样/重命名' },
  { value: 'UPPER', label: '转大写' },
  { value: 'LOWER', label: '转小写' },
  { value: 'TRIM', label: '去空格' },
]

export const AGG_OPS = [
  { value: 'SUM', label: '求和' },
  { value: 'AVG', label: '平均' },
  { value: 'MAX', label: '最大' },
  { value: 'MIN', label: '最小' },
  { value: 'COUNT', label: '计数' },
]

export const TYPE_OPTIONS = [
  { value: 'String', label: '字符串' },
  { value: 'Integer', label: '整数' },
  { value: 'Number', label: '数值' },
  { value: 'Date', label: '日期' },
  { value: 'Boolean', label: '布尔' },
]

export const DELIMITER_OPTIONS = [
  { value: ',', label: '逗号 ,' },
  { value: '\\t', label: '制表符 Tab' },
  { value: '|', label: '竖线 |' },
  { value: ';', label: '分号 ;' },
  { value: '__CUSTOM__', label: '自定义' },
]

export function parseMappings(raw: unknown): MappingRow[] {
  if (Array.isArray(raw)) {
    return raw.map((m: Record<string, unknown>) => ({
      from: String(m?.from || ''),
      to: String(m?.to || m?.from || ''),
      expr: String(m?.expr || 'COPY'),
    })).filter((m) => m.from)
  }
  if (typeof raw === 'string' && raw.trim()) {
    return raw.split(/\n/).map((line) => {
      const [from, to, expr] = line.split(':').map((s) => s.trim())
      return { from: from || '', to: to || from || '', expr: expr || 'COPY' }
    }).filter((m) => m.from)
  }
  return []
}

export function parseAggs(raw: unknown): AggRow[] {
  if (Array.isArray(raw)) {
    return raw.map((a: Record<string, unknown>) => ({
      field: String(a?.field || ''),
      op: String(a?.op || 'COUNT'),
      alias: String(a?.alias || ''),
    })).filter((a) => a.field)
  }
  if (typeof raw === 'string' && raw.trim()) {
    return raw.split(/\n/).map((line) => {
      const [field, op, alias] = line.split(':').map((s) => s.trim())
      return { field: field || '', op: op || 'COUNT', alias: alias || `${field}_${op || 'COUNT'}` }
    }).filter((a) => a.field)
  }
  return []
}

export function parseStringList(raw: unknown): string[] {
  if (Array.isArray(raw)) return raw.map((x) => String(x)).filter(Boolean)
  if (typeof raw === 'string' && raw.trim()) {
    return raw.split(/[,;\s]+/).map((s) => s.trim()).filter(Boolean)
  }
  return []
}

export function parseFilterConditions(cfg: Record<string, unknown>): FilterCondRow[] {
  if (Array.isArray(cfg.conditions) && cfg.conditions.length) {
    return (cfg.conditions as Record<string, unknown>[]).map((c, i) => ({
      field: String(c.field || ''),
      op: String(c.op || 'EQ'),
      value: String(c.value ?? ''),
      logic: (i === 0 ? 'AND' : String(c.logic || 'AND').toUpperCase()) as 'AND' | 'OR',
    })).filter((c) => c.field)
  }
  if (cfg.field) {
    return [{
      field: String(cfg.field),
      op: String(cfg.op || 'EQ'),
      value: String(cfg.value ?? ''),
      logic: 'AND',
    }]
  }
  return []
}

export function parseSortKeys(cfg: Record<string, unknown>): SortKeyRow[] {
  if (Array.isArray(cfg.sortKeys) && cfg.sortKeys.length) {
    return (cfg.sortKeys as Record<string, unknown>[]).map((s) => ({
      field: String(s.field || ''),
      order: (String(s.order || 'ASC').toUpperCase() === 'DESC' ? 'DESC' : 'ASC') as 'ASC' | 'DESC',
    })).filter((s) => s.field)
  }
  const field = String(cfg.field || cfg.sortField || '')
  if (field) {
    return [{ field, order: (String(cfg.order || 'ASC').toUpperCase() === 'DESC' ? 'DESC' : 'ASC') as 'ASC' | 'DESC' }]
  }
  return []
}

export function parseJoinKeys(cfg: Record<string, unknown>): JoinKeyRow[] {
  if (Array.isArray(cfg.joinKeys) && cfg.joinKeys.length) {
    return (cfg.joinKeys as Record<string, unknown>[]).map((j) => ({
      leftKey: String(j.leftKey || ''),
      rightKey: String(j.rightKey || ''),
    })).filter((j) => j.leftKey || j.rightKey)
  }
  if (cfg.leftKey || cfg.rightKey) {
    return [{ leftKey: String(cfg.leftKey || ''), rightKey: String(cfg.rightKey || cfg.leftKey || '') }]
  }
  return []
}

export function parseValueMaps(cfg: Record<string, unknown>): ValueMapRow[] {
  if (Array.isArray(cfg.mappings) && cfg.mappings.length) {
    return (cfg.mappings as Record<string, unknown>[]).map((m) => ({
      fromValue: String(m.fromValue ?? m.from ?? ''),
      toValue: String(m.toValue ?? m.to ?? ''),
    }))
  }
  if (cfg.fromValue != null || cfg.toValue != null) {
    return [{ fromValue: String(cfg.fromValue || ''), toValue: String(cfg.toValue || '') }]
  }
  return []
}

export function parseSwitchCases(cfg: Record<string, unknown>): SwitchCaseRow[] {
  if (Array.isArray(cfg.cases) && cfg.cases.length) {
    return (cfg.cases as Record<string, unknown>[]).map((c) => ({
      value: String(c.value ?? ''),
      label: String(c.label || c.value || ''),
    })).filter((c) => c.value !== '')
  }
  return []
}

export function roleFromHandles(sourceHandle?: string | null, targetHandle?: string | null): EdgeRole {
  const sh = String(sourceHandle || '')
  const th = String(targetHandle || '')
  if (sh === 'out_true' || sh === 'true') return 'TRUE'
  if (sh === 'out_false' || sh === 'false') return 'FALSE'
  if (sh === 'out_default' || sh === 'default') return 'DEFAULT'
  if (sh.startsWith('out_case_') || sh.startsWith('case_')) return 'CASE'
  if (th === 'in_left' || th === 'left') return 'LEFT'
  if (th === 'in_right' || th === 'right') return 'RIGHT'
  return 'COPY'
}

/** 按节点类型返回输出 Handle 定义 */
export function outputHandlesFor(nodeType: string, cases: SwitchCaseRow[] = []): Array<{ id: string; label: string }> {
  if (nodeType === 'FILTER') {
    return [
      { id: 'out_true', label: '是' },
      { id: 'out_false', label: '否' },
    ]
  }
  if (nodeType === 'SWITCH_CASE') {
    const outs = cases.map((c, i) => ({
      id: `out_case_${i}`,
      label: c.label || c.value || `分支${i + 1}`,
    }))
    outs.push({ id: 'out_default', label: '默认' })
    return outs
  }
  if (nodeType === 'OUTPUT' || nodeType === 'TEXT_OUTPUT' || nodeType === 'INSERT_UPDATE') {
    return []
  }
  return [{ id: 'out', label: '' }]
}

export function inputHandlesFor(nodeType: string): Array<{ id: string; label: string }> {
  if (nodeType === 'INPUT' || nodeType === 'TEXT_INPUT' || nodeType === 'EXCEL_INPUT' || nodeType === 'HTTP') {
    return []
  }
  if (nodeType === 'JOIN') {
    return [
      { id: 'in_left', label: '左' },
      { id: 'in_right', label: '右' },
    ]
  }
  return [{ id: 'in', label: '' }]
}
