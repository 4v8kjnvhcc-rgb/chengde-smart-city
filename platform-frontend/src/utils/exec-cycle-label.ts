/**
 * 执行周期展示：列表页用「名称」而非 Cron 原文（对照系统配置·执行周期管理）。
 */
import { onMounted, ref } from 'vue'
import api from '@/api/http'

type CycleRow = { cronExpr?: string; cycleName?: string; status?: string }

let cache: Record<string, string> | null = null
let pending: Promise<Record<string, string>> | null = null

export function normalizeCronExpr(cron: string): string {
  return String(cron || '')
    .trim()
    .replace(/\s+/g, ' ')
}

async function fetchMap(): Promise<Record<string, string>> {
  const res = await api.get('/system/exec-cycles', { params: { status: 'ACTIVE' } })
  const m: Record<string, string> = {}
  for (const o of (res.data || []) as CycleRow[]) {
    const cron = o.cronExpr ? normalizeCronExpr(o.cronExpr) : ''
    if (!cron) continue
    m[cron] = (o.cycleName || '').trim() || cron
  }
  return m
}

/** 预加载 / 刷新映射表 */
export async function ensureExecCycleMap(force = false): Promise<Record<string, string>> {
  if (!force && cache) return cache
  if (!force && pending) return pending
  pending = fetchMap()
    .then((m) => {
      cache = m
      return m
    })
    .finally(() => {
      pending = null
    })
  return pending
}

export function invalidateExecCycleMap() {
  cache = null
  pending = null
}

/** 同步取名称；映射未加载或未匹配时显示「自定义周期」，不回显 Cron 原文 */
export function formatExecCycleLabel(cron?: string | null, empty = '—'): string {
  if (cron == null || !String(cron).trim()) return empty
  const key = normalizeCronExpr(String(cron))
  return cache?.[key] || '自定义周期'
}

/**
 * 列表页推荐：挂载时加载映射，label() 用于表格列。
 * ready 变化会触发依赖重渲染。
 */
export function useExecCycleLabel() {
  const ready = ref(false)
  onMounted(() => {
    void ensureExecCycleMap().finally(() => {
      ready.value = true
    })
  })
  function label(cron?: string | null, empty = '—'): string {
    void ready.value
    return formatExecCycleLabel(cron, empty)
  }
  return { label, ready, reload: () => ensureExecCycleMap(true).then(() => { ready.value = true }) }
}
