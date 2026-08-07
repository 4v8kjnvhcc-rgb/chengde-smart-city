/** 数据资产登记 · 数据源类型（新建项目 / 新增数据源） */

export type SourceTypeCategory = 'db' | 'memory' | 'file'

export interface SourceTypeOption {
  value: string
  label: string
  category: SourceTypeCategory
  defaultPort?: number
}

export const SOURCE_TYPE_OPTIONS: SourceTypeOption[] = [
  // 数据库型
  { value: 'MYSQL', label: 'MySQL', category: 'db', defaultPort: 3306 },
  { value: 'ORACLE', label: 'Oracle', category: 'db', defaultPort: 1521 },
  { value: 'POSTGRESQL', label: 'PostgreSQL', category: 'db', defaultPort: 5432 },
  { value: 'CLICKHOUSE', label: 'ClickHouse', category: 'db', defaultPort: 8123 },
  { value: 'HIVE', label: 'Hive', category: 'db', defaultPort: 10000 },
  { value: 'MONGODB', label: 'MongoDB', category: 'db', defaultPort: 27017 },
  // 内存型
  { value: 'REDIS', label: 'Redis', category: 'memory', defaultPort: 6379 },
  // 文件型
  { value: 'CSV', label: 'CSV', category: 'file' },
  { value: 'EXCEL', label: 'Excel', category: 'file' },
  { value: 'JSON', label: 'JSON', category: 'file' },
]

export const SOURCE_TYPE_GROUPS: { label: string; options: SourceTypeOption[] }[] = [
  { label: '数据库型', options: SOURCE_TYPE_OPTIONS.filter((o) => o.category === 'db') },
  { label: '内存型', options: SOURCE_TYPE_OPTIONS.filter((o) => o.category === 'memory') },
  { label: '文件型', options: SOURCE_TYPE_OPTIONS.filter((o) => o.category === 'file') },
]

export function sourceTypeCategory(type?: string): SourceTypeCategory | 'unknown' {
  const t = String(type || '').toUpperCase()
  if (!t) return 'unknown'
  // 兼容历史 FILE / API
  if (t === 'FILE' || t === 'CSV' || t === 'EXCEL' || t === 'JSON' || t === 'API') return 'file'
  if (t === 'REDIS') return 'memory'
  const hit = SOURCE_TYPE_OPTIONS.find((o) => o.value === t)
  return hit?.category || 'unknown'
}

export function isDbType(type?: string) {
  return sourceTypeCategory(type) === 'db'
}

export function isMemoryType(type?: string) {
  return sourceTypeCategory(type) === 'memory'
}

export function isFileType(type?: string) {
  return sourceTypeCategory(type) === 'file'
}

/** 需要主机/端口等连接配置的类型（库表 / 内存） */
export function needsConnConfig(type?: string) {
  const c = sourceTypeCategory(type)
  return c === 'db' || c === 'memory'
}

export function defaultPortFor(type?: string): number {
  const t = String(type || '').toUpperCase()
  const hit = SOURCE_TYPE_OPTIONS.find((o) => o.value === t)
  if (hit?.defaultPort) return hit.defaultPort
  if (t === 'POSTGRES' || t === 'POSTGRESQL') return 5432
  if (t === 'ORACLE') return 1521
  return 3306
}
