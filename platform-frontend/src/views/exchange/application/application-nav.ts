import type { HubNavItem } from '@/components/common/HubSideLayout.vue'
import api from '@/api/http'

/** 应用平台应用入口（不再承载门户 Tab） */
export type ApplicationApp = 'home' | 'supply' | 'assessment' | 'stats-base' | 'stats-domain'

/** @deprecated 旧三壳 / 四分系统，resolve 时映射到新 app 或分析门户路由 */
export type LegacyApplicationSystem =
  | 'portal'
  | 'stats'
  | 'cockpit'
  | 'supply'
  | 'assessment'
  | 'base-stats'
  | 'domain-stats'

export interface ApplicationModuleMeta {
  key: string
  mCode: string
  label: string
  subLabel?: string
  system: ApplicationApp | LegacyApplicationSystem
  permission?: string
}

/** 应用平台顶栏/落地页四个入口 */
export const APPLICATION_APPS: {
  key: Exclude<ApplicationApp, 'home'>
  label: string
  external?: boolean
}[] = [
  { key: 'supply', label: '数据供需对接系统' },
  { key: 'assessment', label: '考核评估系统', external: true },
  { key: 'stats-base', label: '基础库统计分析应用' },
  { key: 'stats-domain', label: '重点领域统计分析应用' },
]

/** 部门数据共享门户 Tab（不含考核/供需） */
export const DEPT_PORTAL_TABS = [
  { key: 'home', label: '首页' },
  { key: 'catalog', label: '共享资源' },
  { key: 'subscribe', label: '资源订阅申请' },
  { key: 'myspace', label: '我的空间' },
] as const

export const DEPT_PORTAL_BRAND = '部门数据共享门户'
export const LEADER_PORTAL_BRAND = '领导决策门户'

/** @deprecated 兼容旧引用；部门门户已去掉 assessment */
export const PORTAL_TABS = DEPT_PORTAL_TABS

/** @deprecated 兼容旧三壳文案 */
export const APPLICATION_SYSTEMS: { key: string; label: string; permission?: string }[] = [
  { key: 'portal', label: '部门数据共享门户' },
  { key: 'stats', label: '统计分析', permission: 'analytics:stats:view' },
  { key: 'cockpit', label: '决策驾驶舱', permission: 'analytics:cockpit:view' },
]

export const SUPPLY_MODULES: ApplicationModuleMeta[] = [
  { key: 'supply-flow', mCode: 'M020', label: '供需对接', subLabel: '需求·分析·确认·供给·清单', system: 'supply' },
]

export const SUPPLY_MAIN_SECTIONS = [
  { key: 'demand', label: '数据需求管理', mCode: 'M020' },
  { key: 'analysis', label: '数据需求分析', mCode: 'M021' },
  { key: 'confirm', label: '数据需求确认', mCode: 'M022' },
  { key: 'supply', label: '数据供给查看', mCode: 'M023' },
  { key: 'manifest-center', label: '数据清单中心', mCode: 'M024' },
] as const

export const MANIFEST_CENTER_SECTIONS = [
  { key: 'dept-catalog', label: '部门目录清单', mCode: 'M024' },
  { key: 'service-list', label: '服务清单', mCode: 'M024' },
  { key: 'open-list', label: '开放清单', mCode: 'M024' },
  { key: 'objection', label: '异议清单', mCode: 'M025' },
] as const

export const SUPPLY_FLOW_SECTIONS = SUPPLY_MAIN_SECTIONS.filter((s) => s.key !== 'manifest-center')

export const MANIFEST_SECTIONS = [
  { key: 'catalog', label: '目录清单', mCode: 'M024' },
  { key: 'objection', label: '异议清单', mCode: 'M025' },
  { key: 'manifest', label: '供需清单', mCode: 'M026' },
] as const

export const ASSESSMENT_MODULES: ApplicationModuleMeta[] = [
  { key: 'execution', mCode: 'M030', label: '考核结果', subLabel: '分数·应报数据', system: 'assessment' },
]

export const ASSESSMENT_CONFIG_MODULES: ApplicationModuleMeta[] = [
  { key: 'data-source', mCode: 'M027', label: '评价数据来源', system: 'assessment' },
  { key: 'period', mCode: 'M028', label: '评价周期管理', system: 'assessment' },
  { key: 'indicator', mCode: 'M029', label: '评价指标管理', system: 'assessment' },
  { key: 'execution', mCode: 'M030', label: '评价执行与发布', system: 'assessment' },
]

export const BASE_STAT_TOPICS = [
  { key: 'pop-structure', label: '人口结构分析', rowIndex: 52 },
  { key: 'pop-growth', label: '人口增长趋势分析', rowIndex: 53 },
  { key: 'pop-mobility', label: '人口流动分析', rowIndex: 54 },
  { key: 'hukou-resident', label: '户籍人口与常住人口比较分析', rowIndex: 55 },
  { key: 'macro-economy', label: '经济运行相关统计分析', rowIndex: 56, metricCodes: ['GDP_BASE'] },
  { key: 'industry-structure', label: '产业结构分析', rowIndex: 57 },
  { key: 'employment', label: '就业率和失业率分析', rowIndex: 58 },
] as const

export const DOMAIN_STAT_TOPICS = [
  { key: 'insurance', label: '人口参保统计分析', rowIndex: 59, metricCodes: ['INSUR_DOMAIN'] },
  { key: 'subsidy', label: '高龄津贴统计分析', rowIndex: 60, metricCodes: ['SUBSIDY_DOMAIN'] },
  { key: 'dibao', label: '特困人员与低保补助统计分析', rowIndex: 61 },
  { key: 'permit', label: '行政许可与处罚统计分析', rowIndex: 62, metricCodes: ['PERMIT_DOMAIN'] },
] as const

export type FulfillPath = 'AUTHORIZE_EXISTING' | 'NEED_COLLECT'

export const FULFILL_PATH_OPTIONS: { value: FulfillPath; label: string }[] = [
  { value: 'AUTHORIZE_EXISTING', label: '已在中台 · 授权共享' },
  { value: 'NEED_COLLECT', label: '未在中台 · 需归集补数' },
]

/** 考核评估外系统 URL（未配置时前端提示） */
export function assessmentExternalUrl(): string {
  const raw = String(import.meta.env.VITE_ASSESSMENT_EXTERNAL_URL || '').trim()
  return raw
}

/**
 * 根据落地地址拼考核验票入口。
 * landing 例：http://127.0.0.1:18081/assessment/index#/dashboard
 * → http://127.0.0.1:18081/assessment/sso/portal?ticket=...&redirect=...
 */
export function buildAssessmentPortalSsoUrl(landingUrl: string, ticket: string): string | null {
  const raw = String(landingUrl || '').trim()
  if (!raw.startsWith('http://') && !raw.startsWith('https://')) return null
  const hashIdx = raw.indexOf('#')
  const withoutHash = hashIdx >= 0 ? raw.slice(0, hashIdx) : raw
  const marker = '/assessment'
  const i = withoutHash.toLowerCase().indexOf(marker)
  if (i < 0) return null
  const base = withoutHash.slice(0, i + marker.length)
  const redirectTarget =
    hashIdx >= 0 ? raw : `${base}/index#/dashboard`
  return `${base}/sso/portal?ticket=${encodeURIComponent(ticket)}&redirect=${encodeURIComponent(redirectTarget)}`
}

export function openAssessmentExternal(): { ok: boolean; url: string } {
  const url = assessmentExternalUrl()
  if (!url) return { ok: false, url: '' }
  window.open(url, '_blank', 'noopener,noreferrer')
  return { ok: true, url }
}

export async function openAssessmentWithPortalSso(landingUrl: string): Promise<{ ok: boolean; message?: string }> {
  const landing = String(landingUrl || '').trim() || assessmentExternalUrl()
  if (!landing.startsWith('http://') && !landing.startsWith('https://')) {
    return { ok: false, message: '请先在门户配置中填写考核系统 http(s) 地址' }
  }
  try {
    const res = await api.post<{ ticket: string }>('/auth/sso-ticket', {
      targetApp: 'assessment',
      redirectUrl: landing,
    })
    const ticket = res.data?.ticket
    if (!ticket) {
      return { ok: false, message: '签发门户票据失败' }
    }
    const openUrl = buildAssessmentPortalSsoUrl(landing, ticket)
    if (!openUrl) {
      return { ok: false, message: '考核地址须包含 /assessment 路径' }
    }
    window.open(openUrl, '_blank', 'noopener,noreferrer')
    return { ok: true }
  } catch (e: unknown) {
    return { ok: false, message: e instanceof Error ? e.message : '单点登录失败' }
  }
}

const LEGACY_TAB_MAP: Record<string, { app?: ApplicationApp; portal?: 'dept' | 'leader'; section?: string }> = {
  demand: { app: 'supply', section: 'demand' },
  analysis: { app: 'supply', section: 'analysis' },
  confirm: { app: 'supply', section: 'confirm' },
  supply: { app: 'supply', section: 'supply' },
  catalog: { portal: 'dept', section: 'catalog' },
  search: { portal: 'dept', section: 'catalog' },
  home: { portal: 'dept', section: 'home' },
  subscribe: { portal: 'dept', section: 'subscribe' },
  myspace: { portal: 'dept', section: 'myspace' },
  assessment: { app: 'assessment' },
  objection: { portal: 'dept', section: 'myspace' },
  manifest: { portal: 'dept', section: 'myspace' },
  'manifest-center': { portal: 'dept', section: 'myspace' },
  situation: { portal: 'leader' },
  stats: { app: 'stats-base', section: 'base' },
  'stats-base': { app: 'stats-base', section: 'base' },
  'stats-domain': { app: 'stats-domain', section: 'domain' },
}

for (let i = 20; i <= 26; i++) {
  const key = { 20: 'demand', 21: 'analysis', 22: 'confirm', 23: 'supply', 24: 'catalog', 25: 'objection', 26: 'manifest' }[i] as string
  const legacy = LEGACY_TAB_MAP[key]
  if (legacy) LEGACY_TAB_MAP[`m0${i}`] = legacy
}
for (let i = 27; i <= 30; i++) {
  LEGACY_TAB_MAP[`m0${i}`] = { app: 'assessment' }
}
LEGACY_TAB_MAP.m031 = { portal: 'dept', section: 'catalog' }
LEGACY_TAB_MAP.m032 = { portal: 'dept', section: 'home' }
LEGACY_TAB_MAP.m033 = { portal: 'dept', section: 'catalog' }
LEGACY_TAB_MAP.m034 = { portal: 'dept', section: 'catalog' }
LEGACY_TAB_MAP.m035 = { portal: 'dept', section: 'subscribe' }
LEGACY_TAB_MAP.m036 = { portal: 'leader' }
LEGACY_TAB_MAP.m037 = { app: 'stats-base', section: 'base' }
LEGACY_TAB_MAP.m038 = { app: 'stats-domain', section: 'domain' }

export function isStatsSystem(system: string): boolean {
  return system === 'stats' || system === 'base-stats' || system === 'domain-stats' || system === 'stats-base' || system === 'stats-domain'
}

export function systemTitle(system: string): string {
  if (system === 'stats-base' || system === 'base-stats') return '基础库统计分析应用'
  if (system === 'stats-domain' || system === 'domain-stats') return '重点领域统计分析应用'
  if (system === 'supply') return '数据供需对接系统'
  if (system === 'assessment') return '考核评估系统'
  return APPLICATION_APPS.find((s) => s.key === system)?.label || system
}

export function moduleTitle(_module: string, system: string): string {
  return systemTitle(system)
}

export function navItems(_system: string): HubNavItem[] {
  return []
}

export function resolveApplicationApp(query: Record<string, unknown>): {
  app: ApplicationApp
  section: string
  /** 若应跳转到分析门户而非应用平台 */
  redirectPortal?: 'dept' | 'leader'
} {
  let appRaw = String(query.app || query.system || '').toLowerCase()
  let section = String(query.section || '')

  const legacyTab = String(query.tab || '').toLowerCase()
  if (legacyTab && LEGACY_TAB_MAP[legacyTab]) {
    const leg = LEGACY_TAB_MAP[legacyTab]
    if (leg.portal) return { app: 'home', section: leg.section || 'home', redirectPortal: leg.portal }
    if (leg.app) {
      appRaw = leg.app
      if (leg.section) section = leg.section
    }
  }

  if (appRaw === 'portal' || appRaw === 'cockpit') {
    return {
      app: 'home',
      section: section || 'home',
      redirectPortal: appRaw === 'cockpit' ? 'leader' : 'dept',
    }
  }
  if (appRaw === 'stats' || appRaw === 'base-stats') appRaw = 'stats-base'
  if (appRaw === 'domain-stats') appRaw = 'stats-domain'
  if (appRaw === 'assessment' || section === 'assessment') {
    return { app: 'assessment', section: '' }
  }

  const valid: ApplicationApp[] = ['home', 'supply', 'assessment', 'stats-base', 'stats-domain']
  let app = (valid.includes(appRaw as ApplicationApp) ? appRaw : 'home') as ApplicationApp

  if (app === 'stats-base') {
    if (!section || section === 'domain' || DOMAIN_STAT_TOPICS.some((t) => t.key === section)) {
      section = BASE_STAT_TOPICS.some((t) => t.key === section) ? section : 'base'
    }
  }
  if (app === 'stats-domain') {
    if (!section || section === 'base' || BASE_STAT_TOPICS.some((t) => t.key === section)) {
      section = DOMAIN_STAT_TOPICS.some((t) => t.key === section) ? section : 'domain'
    }
  }
  if (app === 'supply' && !section) section = 'demand'

  return { app, section }
}

/** @deprecated 兼容 StatsAnalysisView 等旧调用 */
export function resolveApplicationNav(query: Record<string, unknown>): {
  system: string
  module: string
  section: string
} {
  const r = resolveApplicationApp(query)
  if (r.redirectPortal === 'leader') {
    return { system: 'cockpit', module: 'cockpit', section: 'situation' }
  }
  if (r.redirectPortal === 'dept') {
    return { system: 'portal', module: 'portal-home', section: r.section || 'home' }
  }
  if (r.app === 'stats-base') return { system: 'stats', module: 'stats', section: r.section || 'base' }
  if (r.app === 'stats-domain') return { system: 'stats', module: 'stats', section: r.section || 'domain' }
  if (r.app === 'supply') return { system: 'supply', module: 'supply-flow', section: r.section || 'demand' }
  if (r.app === 'assessment') return { system: 'assessment', module: 'execution', section: '' }
  return { system: 'portal', module: 'portal-home', section: 'home' }
}
