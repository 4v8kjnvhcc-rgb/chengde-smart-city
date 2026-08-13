import api from '@/api/http'

export interface ErrorReportPayload {
  source?: string
  moduleCode?: string
  moduleName?: string
  level?: string
  errorCode?: string
  errorType?: string
  message: string
  stackTrace?: string
  requestUri?: string
  httpMethod?: string
  httpStatus?: number
  pageUrl?: string
  traceId?: string
  appVersion?: string
  env?: string
  extraJson?: string
  occurredAt?: string
}

let reporting = false
let installed = false
const recentKeys = new Set<string>()

function dedupeKey(payload: ErrorReportPayload): string {
  return `${payload.source || ''}|${payload.errorType || ''}|${payload.message}|${payload.requestUri || ''}|${payload.pageUrl || ''}`
}

function allowOnce(key: string): boolean {
  if (recentKeys.has(key)) return false
  recentKeys.add(key)
  if (recentKeys.size > 80) {
    const first = recentKeys.values().next().value
    if (first) recentKeys.delete(first)
  }
  setTimeout(() => recentKeys.delete(key), 15_000)
  return true
}

function guessModule(): { moduleCode?: string; moduleName?: string } {
  const path = window.location.pathname + window.location.search
  if (path.includes('/system/uum') || path.includes('/analytics/support')) {
    return { moduleCode: 'hub:system:uum', moduleName: '统一用户管理' }
  }
  if (path.includes('/exchange/ingestion')) {
    return { moduleCode: 'hub:ingestion', moduleName: '数据采集汇聚' }
  }
  if (path.includes('/governance')) {
    return { moduleCode: 'hub:gov', moduleName: '数据治理' }
  }
  if (path.includes('/resource-center')) {
    return { moduleCode: 'hub:resource', moduleName: '资源中心' }
  }
  if (path.includes('/analytics')) {
    return { moduleCode: 'hub:analytics', moduleName: '分析支撑' }
  }
  return { moduleCode: 'frontend', moduleName: '前端应用' }
}

export async function reportRuntimeError(payload: ErrorReportPayload): Promise<void> {
  if (!localStorage.getItem('accessToken')) return
  if (reporting) return
  const key = dedupeKey(payload)
  if (!allowOnce(key)) return

  const mod = guessModule()
  const body: ErrorReportPayload = {
    source: payload.source || 'FRONTEND',
    moduleCode: payload.moduleCode || mod.moduleCode,
    moduleName: payload.moduleName || mod.moduleName,
    level: payload.level || 'ERROR',
    errorCode: payload.errorCode,
    errorType: payload.errorType,
    message: (payload.message || 'unknown').slice(0, 1000),
    stackTrace: payload.stackTrace?.slice(0, 16000),
    requestUri: payload.requestUri || window.location.pathname,
    httpMethod: payload.httpMethod,
    httpStatus: payload.httpStatus,
    pageUrl: payload.pageUrl || window.location.href,
    traceId: payload.traceId,
    appVersion: payload.appVersion || String(import.meta.env.VITE_APP_VERSION || ''),
    env: payload.env || String(import.meta.env.MODE || ''),
    extraJson: payload.extraJson,
    occurredAt: payload.occurredAt || formatNow(),
  }

  reporting = true
  try {
    await api.post('/system/error-logs/report', body)
  } catch {
    // 上报失败静默，避免递归
  } finally {
    reporting = false
  }
}

function formatNow(): string {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

export function installFrontendErrorReporter(): void {
  if (installed || typeof window === 'undefined') return
  installed = true

  window.addEventListener('error', (ev) => {
    const err = ev.error
    void reportRuntimeError({
      source: 'FRONTEND',
      level: 'ERROR',
      errorType: err?.name || 'Error',
      message: err?.message || ev.message || 'window.onerror',
      stackTrace: err?.stack,
      pageUrl: window.location.href,
      requestUri: window.location.pathname + window.location.search,
    })
  })

  window.addEventListener('unhandledrejection', (ev) => {
    const reason = ev.reason
    const message =
      reason instanceof Error
        ? reason.message
        : typeof reason === 'string'
          ? reason
          : 'unhandledrejection'
    const stack = reason instanceof Error ? reason.stack : undefined
    void reportRuntimeError({
      source: 'FRONTEND',
      level: 'ERROR',
      errorType: reason instanceof Error ? reason.name : 'UnhandledRejection',
      message,
      stackTrace: stack,
      pageUrl: window.location.href,
      requestUri: window.location.pathname + window.location.search,
    })
  })
}
