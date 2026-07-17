import type { HubNavItem } from '@/components/common/HubSideLayout.vue'

/** 应用平台顶栏：数据共享门户 / 统计分析 / 决策驾驶舱（规格书 V2.0） */
export type ApplicationSystem = 'portal' | 'stats' | 'cockpit'

/** @deprecated 旧四分系统，resolve 时映射到新 system */
export type LegacyApplicationSystem = 'supply' | 'assessment' | 'base-stats' | 'domain-stats' | ApplicationSystem

export interface ApplicationModuleMeta {
  key: string
  mCode: string
  label: string
  subLabel?: string
  system: ApplicationSystem | LegacyApplicationSystem
  permission?: string
}

export const APPLICATION_SYSTEMS: { key: ApplicationSystem; label: string; permission?: string }[] = [
  { key: 'portal', label: '数据共享门户' },
  { key: 'stats', label: '统计分析', permission: 'analytics:stats:view' },
  { key: 'cockpit', label: '决策驾驶舱', permission: 'analytics:cockpit:view' },
]

export const PORTAL_TABS = [
  { key: 'home', label: '首页' },
  { key: 'catalog', label: '共享资源' },
  { key: 'subscribe', label: '资源订阅申请' },
  { key: 'assessment', label: '考核评估', permission: 'portal:assessment:view' },
  { key: 'myspace', label: '我的空间' },
] as const

export const SUPPLY_MODULES: ApplicationModuleMeta[] = [
  { key: 'supply-flow', mCode: 'M020', label: '供需对接', subLabel: '需求·分析·确认·供给·清单', system: 'portal' },
]

/** 供需对接页内并列 Tab（门户订阅 Tab 兜底可深链） */
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
  { key: 'execution', mCode: 'M030', label: '考核结果', subLabel: '分数·应报数据', system: 'portal' },
]

export const ASSESSMENT_CONFIG_MODULES: ApplicationModuleMeta[] = [
  { key: 'data-source', mCode: 'M027', label: '评价数据来源', system: 'assessment' as LegacyApplicationSystem },
  { key: 'period', mCode: 'M028', label: '评价周期管理', system: 'assessment' as LegacyApplicationSystem },
  { key: 'indicator', mCode: 'M029', label: '评价指标管理', system: 'assessment' as LegacyApplicationSystem },
  { key: 'execution', mCode: 'M030', label: '评价执行与发布', system: 'assessment' as LegacyApplicationSystem },
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

export const DEFAULT_MODULE: Record<ApplicationSystem, string> = {
  portal: 'portal-home',
  stats: 'stats',
  cockpit: 'cockpit',
}

export type FulfillPath = 'AUTHORIZE_EXISTING' | 'NEED_COLLECT'

export const FULFILL_PATH_OPTIONS: { value: FulfillPath; label: string }[] = [
  { value: 'AUTHORIZE_EXISTING', label: '已在中台 · 授权共享' },
  { value: 'NEED_COLLECT', label: '未在中台 · 需归集补数' },
]

const LEGACY_TAB_MAP: Record<string, { system: ApplicationSystem; module: string; section?: string }> = {
  demand: { system: 'portal', module: 'portal-home', section: 'subscribe' },
  analysis: { system: 'portal', module: 'portal-home', section: 'subscribe' },
  confirm: { system: 'portal', module: 'portal-home', section: 'subscribe' },
  supply: { system: 'portal', module: 'portal-home', section: 'subscribe' },
  catalog: { system: 'portal', module: 'portal-home', section: 'catalog' },
  search: { system: 'portal', module: 'portal-home', section: 'catalog' },
  home: { system: 'portal', module: 'portal-home', section: 'home' },
  subscribe: { system: 'portal', module: 'portal-home', section: 'subscribe' },
  objection: { system: 'portal', module: 'portal-home', section: 'myspace' },
  manifest: { system: 'portal', module: 'portal-home', section: 'myspace' },
  'manifest-center': { system: 'portal', module: 'portal-home', section: 'myspace' },
  situation: { system: 'cockpit', module: 'cockpit' },
  stats: { system: 'stats', module: 'stats' },
  'stats-base': { system: 'stats', module: 'stats', section: 'base' },
  'stats-domain': { system: 'stats', module: 'stats', section: 'domain' },
}

for (let i = 20; i <= 26; i++) {
  const key = { 20: 'demand', 21: 'analysis', 22: 'confirm', 23: 'supply', 24: 'catalog', 25: 'objection', 26: 'manifest' }[i] as string
  const legacy = LEGACY_TAB_MAP[key]
  if (legacy) LEGACY_TAB_MAP[`m0${i}`] = legacy
}
for (let i = 27; i <= 30; i++) {
  LEGACY_TAB_MAP[`m0${i}`] = { system: 'portal', module: 'portal-home', section: 'assessment' }
}
LEGACY_TAB_MAP.m031 = { system: 'portal', module: 'portal-home', section: 'catalog' }
LEGACY_TAB_MAP.m032 = { system: 'portal', module: 'portal-home', section: 'catalog' }
LEGACY_TAB_MAP.m033 = { system: 'portal', module: 'portal-home', section: 'catalog' }
LEGACY_TAB_MAP.m034 = { system: 'portal', module: 'portal-home', section: 'catalog' }
LEGACY_TAB_MAP.m035 = { system: 'portal', module: 'portal-home', section: 'subscribe' }
LEGACY_TAB_MAP.m036 = { system: 'cockpit', module: 'cockpit' }
LEGACY_TAB_MAP.m037 = { system: 'stats', module: 'stats', section: 'base' }
LEGACY_TAB_MAP.m038 = { system: 'stats', module: 'stats', section: 'domain' }

export function isStatsSystem(system: string): boolean {
  return system === 'stats' || system === 'base-stats' || system === 'domain-stats'
}

export function systemTitle(system: string): string {
  if (system === 'base-stats' || system === 'domain-stats') return '统计分析'
  return APPLICATION_SYSTEMS.find((s) => s.key === system)?.label || system
}

export function moduleTitle(_module: string, system: string): string {
  if (isStatsSystem(system)) return '统计分析'
  if (system === 'cockpit') return '决策驾驶舱'
  return '数据共享门户'
}

export function navItems(_system: ApplicationSystem): HubNavItem[] {
  return []
}

export function resolveApplicationNav(query: Record<string, unknown>): {
  system: ApplicationSystem
  module: string
  section: string
} {
  let rawSystem = String(query.system || '').toLowerCase()
  let module = String(query.module || '')
  let section = String(query.section || '')

  const legacyTab = String(query.tab || '').toLowerCase()
  if (legacyTab && LEGACY_TAB_MAP[legacyTab]) {
    const leg = LEGACY_TAB_MAP[legacyTab]
    rawSystem = leg.system
    module = leg.module
    if (leg.section) section = leg.section
  }

  // 旧四分系统 → 新三壳（供需子页落到门户「订阅」兜底区，子 Tab 用 query.sdSection）
  if (rawSystem === 'supply') {
    rawSystem = 'portal'
    module = 'portal-home'
    section = 'subscribe'
  }
  if (rawSystem === 'assessment') {
    rawSystem = 'portal'
    module = 'portal-home'
    section = ['home', 'catalog', 'subscribe', 'assessment', 'myspace'].includes(section) ? section : 'assessment'
  }
  if (rawSystem === 'base-stats') {
    rawSystem = 'stats'
    module = 'stats'
    section = section || 'base'
  }
  if (rawSystem === 'domain-stats') {
    rawSystem = 'stats'
    module = 'stats'
    section = section || 'domain'
  }

  let system = rawSystem as ApplicationSystem
  const valid: ApplicationSystem[] = ['portal', 'stats', 'cockpit']
  if (!valid.includes(system)) system = 'portal'
  if (!module) module = DEFAULT_MODULE[system]

  if (system === 'portal') {
    module = 'portal-home'
    if (!section || !['home', 'catalog', 'subscribe', 'assessment', 'myspace'].includes(section)) {
      section = 'home'
    }
  }
  if (system === 'stats') {
    module = 'stats'
    if (!section || !['base', 'domain', ...BASE_STAT_TOPICS.map((t) => t.key), ...DOMAIN_STAT_TOPICS.map((t) => t.key)].includes(section)) {
      section = 'base'
    }
  }
  if (system === 'cockpit') {
    module = 'cockpit'
    section = section || 'situation'
  }

  return { system, module, section }
}
