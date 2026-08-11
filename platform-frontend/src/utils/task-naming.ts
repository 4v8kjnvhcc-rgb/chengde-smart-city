import api from '@/api/http'

/** 任务命名类别（与数据标准体系命名规范一致） */
export type TaskNamingCategory = 'GJ' | 'ZL' | 'RH' | 'META' | 'Q'

export interface GenerateTaskNameParams {
  taskCategory: TaskNamingCategory
  targetTable?: string
  dataSourceName?: string
  sequenceNo?: number
}

const BUILTIN_TEMPLATES: Record<TaskNamingCategory, string> = {
  GJ: 't_gj_{targetTable}',
  ZL: 't_zl_{targetTable}',
  RH: 't_rh_{targetTable}',
  META: 't_meta_{dataSourceName}{seq}',
  Q: 't_q_{dataSourceName}{seq}',
}

function sanitizeIdent(raw: string): string {
  return String(raw || '')
    .trim()
    .replace(/[^A-Za-z0-9_]/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_|_$/g, '')
    .toLowerCase()
}

/** 本地按内置规则生成（无网络时的兜底） */
export function buildTaskNameLocal(params: GenerateTaskNameParams): string {
  const category = params.taskCategory || 'GJ'
  const template = BUILTIN_TEMPLATES[category]
  const targetTable = sanitizeIdent(params.targetTable || '')
  const dataSourceName = sanitizeIdent(params.dataSourceName || '')
  const seq = String(Math.max(1, params.sequenceNo ?? 1)).padStart(3, '0')

  const name = template
    .replace(/\{targetTable\}/g, targetTable)
    .replace(/\{dataSourceName\}/g, dataSourceName)
    .replace(/\{seq\}/g, seq)
    .replace(/_+/g, '_')
    .replace(/^_|_$/g, '')

  return name.slice(0, 80)
}

/** 优先调用后端命名规范服务，失败时回退内置规则 */
export async function generateTaskName(params: GenerateTaskNameParams): Promise<string> {
  try {
    const res = await api.post<{ taskName?: string }>('/governance/standards/naming/generate-task-name', params)
    const name = res.data?.taskName?.trim()
    if (name) return name
  } catch {
    /* fallback */
  }
  return buildTaskNameLocal(params)
}
