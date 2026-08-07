/** 统一时间展示：2026-08-05 11:53:10 */

const DATETIME_RE = /^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}(:\d{2})?/

export function formatDateTime(value: unknown, empty = '—'): string {
  if (value == null || value === '') return empty
  if (Array.isArray(value) && value.length >= 3) {
    // Jackson 偶尔序列化为 [y,m,d,h,mi,s]
    const [y, m, d, h = 0, mi = 0, s = 0] = value.map(Number)
    if (![y, m, d].every((n) => Number.isFinite(n))) return empty
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${y}-${pad(m)}-${pad(d)} ${pad(h)}:${pad(mi)}:${pad(s)}`
  }
  if (value instanceof Date) {
    if (Number.isNaN(value.getTime())) return empty
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())} ${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`
  }
  let raw = String(value).trim()
  if (!raw) return empty
  // 去掉毫秒与时区后缀，统一替换 T
  raw = raw.replace(/\.\d+/, '').replace(/Z$/i, '').replace(/[+-]\d{2}:\d{2}$/, '')
  raw = raw.replace('T', ' ')
  if (DATETIME_RE.test(raw)) {
    const body = raw.slice(0, 19)
    return body.length === 16 ? `${body}:00` : body
  }
  const t = Date.parse(String(value))
  if (!Number.isNaN(t)) return formatDateTime(new Date(t), empty)
  return raw
}

/** 解析为可比时间戳；无效时回退 id */
export function toTimeMs(value: unknown): number {
  if (value == null || value === '') return 0
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (value instanceof Date) return value.getTime() || 0
  if (Array.isArray(value) && value.length >= 3) {
    const [y, m, d, h = 0, mi = 0, s = 0] = value.map(Number)
    const t = new Date(y, m - 1, d, h, mi, s).getTime()
    return Number.isNaN(t) ? 0 : t
  }
  const raw = String(value).trim()
  if (!raw) return 0
  const t = Date.parse(raw.includes('T') ? raw : raw.replace(' ', 'T'))
  return Number.isNaN(t) ? 0 : t
}

export function rowTimeMs(
  row: Record<string, unknown>,
  keys: string[] = ['updatedAt', 'createdAt', 'publishedAt', 'catalogMountedAt'],
): number {
  for (const k of keys) {
    const ms = toTimeMs(row[k])
    if (ms > 0) return ms
  }
  const id = Number(row.id)
  return Number.isFinite(id) ? id : 0
}

/** 列表按时间倒序（新→旧） */
export function sortByTimeDesc<T extends Record<string, unknown>>(
  rows: T[] | null | undefined,
  keys?: string[],
): T[] {
  const list = Array.isArray(rows) ? [...rows] : []
  list.sort((a, b) => rowTimeMs(b, keys) - rowTimeMs(a, keys))
  return list
}

/** 字段名像时间则格式化展示 */
export function formatMaybeDateTime(key: string, value: unknown): string {
  if (value == null || value === '') return '—'
  if (Array.isArray(value) && !(value.length >= 3 && typeof value[0] === 'number')) {
    return value.map(String).join(',')
  }
  const k = String(key || '')
  if (/(At|Time|Deadline|Date|date|time)$/.test(k) || /(_at|_time|_deadline)$/i.test(k)) {
    return formatDateTime(value)
  }
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
