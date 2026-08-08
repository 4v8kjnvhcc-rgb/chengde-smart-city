/** 元数据模块展示辅助（API 仍传英文码） */

export const ENTRY_TYPE_OPTIONS = [
  { label: '数据源', value: 'SOURCE' },
  { label: '数据表', value: 'TABLE' },
  { label: '字段', value: 'COLUMN' },
  { label: '编目', value: 'CATALOG' },
  { label: '元模型', value: 'MODEL' },
]

export const SECURITY_OPTIONS = [
  { label: '公开', value: 'PUBLIC' },
  { label: '内部', value: 'INTERNAL' },
  { label: '敏感', value: 'SENSITIVE' },
  { label: '核心', value: 'SECRET' },
]

export const TAG_OPTIONS = ['主数据', '共享数据', '高频使用', '个人信息', '主题库', '指标']

export const LAYER_OPTIONS = [
  { label: 'ODS 原始层', value: 'ODS' },
  { label: 'DWD 明细层', value: 'DWD' },
  { label: 'DWS 汇总层', value: 'DWS' },
  { label: 'ADS 应用层', value: 'ADS' },
]

export const FIELD_TYPE_OPTIONS = [
  'VARCHAR', 'CHAR', 'TEXT', 'INT', 'BIGINT', 'DECIMAL', 'DOUBLE', 'DATE', 'DATETIME', 'BOOLEAN', 'JSON',
]

export interface MetaFieldDef {
  code: string
  name: string
  type: string
  length?: number
  required?: boolean
  primaryKey?: boolean
  /** 必填项提示说明 */
  hint?: string
  /** 字段模型：ADD 新增 / MODIFY 修改 */
  action?: 'ADD' | 'MODIFY'
}

export function parseFieldDefs(contentJson?: string | null): MetaFieldDef[] {
  if (!contentJson) return []
  try {
    const raw = JSON.parse(contentJson)
    if (!Array.isArray(raw)) return []
    return raw.map((item: Record<string, unknown>) => ({
      code: String(item.code || item.columnCode || item.name || ''),
      name: String(item.name || item.columnName || item.code || ''),
      type: String(item.type || item.dataType || 'VARCHAR'),
      length: item.length == null && item.dataLength == null ? undefined : Number(item.length ?? item.dataLength),
      required: Boolean(item.required ?? item.requiredFlag),
      primaryKey: Boolean(item.primaryKey ?? item.pk),
      hint: item.hint != null ? String(item.hint) : (item.description != null ? String(item.description) : undefined),
      action: item.action === 'ADD' || item.action === 'MODIFY' ? item.action : undefined,
    })).filter((f: MetaFieldDef) => f.code)
  } catch {
    return []
  }
}

export function stringifyFieldDefs(fields: MetaFieldDef[]): string {
  return JSON.stringify(fields.map(f => ({
    code: f.code,
    name: f.name,
    type: f.type,
    length: f.length ?? null,
    required: !!f.required,
    primaryKey: !!f.primaryKey,
    hint: f.hint?.trim() || null,
    action: f.action || null,
  })))
}

export function fieldCountOf(contentJson?: string | null): number {
  return parseFieldDefs(contentJson).length
}
