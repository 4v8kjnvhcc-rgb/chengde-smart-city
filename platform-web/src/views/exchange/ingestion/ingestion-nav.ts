import type { HubNavGroup, HubNavItem } from '@/components/common/HubSideLayout.vue'

export type IngestionSystem = 'register' | 'collect'

/** 采集汇聚 3 个一级模块（数据上传并入汇聚接入；数据资产管理移出采集验收范围） */
export type CollectModuleKey = 'ingest' | 'pipeline' | 'catalog'

export interface IngestionModuleMeta {
  key: string
  mCode: string
  label: string
  subLabel?: string
  system: IngestionSystem
}

export const REGISTER_MODULES: IngestionModuleMeta[] = [
  { key: 'm039', mCode: 'M039', label: '填报指引', subLabel: '登记引导', system: 'register' },
  { key: 'm040', mCode: 'M040', label: '项目/系统信息登记', subLabel: '项目与系统', system: 'register' },
  { key: 'm041', mCode: 'M041', label: '数据库/表/项登记', subLabel: '数据源与模型', system: 'register' },
  { key: 'm042', mCode: 'M042', label: '数据字典登记', subLabel: '字典登记', system: 'register' },
  { key: 'm043', mCode: 'M043', label: '数据资产标签登记', subLabel: '标签登记', system: 'register' },
  { key: 'm044', mCode: 'M044', label: '数据项管理', subLabel: '数据项', system: 'register' },
  { key: 'm045', mCode: 'M045', label: '数据资产标签管理', subLabel: '标签体系', system: 'register' },
  { key: 'm046', mCode: 'M046', label: '数据资产报告', subLabel: '资产大屏', system: 'register' },
  { key: 'm047', mCode: 'M047', label: '数据资产图谱分析', subLabel: '血缘图谱', system: 'register' },
  { key: 'm048', mCode: 'M048', label: '访问控制管理', subLabel: '已迁至系统管理', system: 'register' },
  { key: 'm049', mCode: 'M049', label: '系统维护管理', subLabel: '跳转系统管理', system: 'register' },
  { key: 'm050', mCode: 'M050', label: '数据字典管理', subLabel: '字典管理', system: 'register' },
]

export const COLLECT_MODULES: IngestionModuleMeta[] = [
  { key: 'ingest', mCode: 'M054', label: '数据汇聚接入', subLabel: '结构化·文件·其他接入', system: 'collect' },
  { key: 'pipeline', mCode: 'M061', label: '规范设计', subLabel: '探查·定义·任务·对账', system: 'collect' },
  { key: 'catalog', mCode: 'M065', label: '指标与目录体系构建', subLabel: '关联登记·编目·审批', system: 'collect' },
]

const REGISTER_BY_KEY = Object.fromEntries(REGISTER_MODULES.map((m) => [m.key, m])) as Record<string, IngestionModuleMeta>
const COLLECT_BY_KEY = Object.fromEntries(COLLECT_MODULES.map((m) => [m.key, m])) as Record<string, IngestionModuleMeta>

export const MCODE_TO_COLLECT: Record<number, CollectModuleKey> = {}
for (let i = 51; i <= 60; i++) MCODE_TO_COLLECT[i] = 'ingest'
for (let i = 61; i <= 64; i++) MCODE_TO_COLLECT[i] = 'pipeline'
for (let i = 65; i <= 77; i++) MCODE_TO_COLLECT[i] = 'catalog'

export const DEFAULT_MODULE: Record<IngestionSystem, string> = {
  register: 'm039',
  collect: 'ingest',
}

export const LEGACY_TAB_MAP: Record<string, { system: IngestionSystem; module: string }> = {
  m037: { system: 'register', module: 'm039' },
  m038: { system: 'register', module: 'm039' },
  register: { system: 'register', module: 'm039' },
  upload: { system: 'collect', module: 'ingest' },
  channel: { system: 'collect', module: 'ingest' },
  pipeline: { system: 'collect', module: 'pipeline' },
  resource: { system: 'collect', module: 'catalog' },
  govern: { system: 'collect', module: 'catalog' },
  asset: { system: 'collect', module: 'catalog' },
}
for (let i = 39; i <= 50; i++) LEGACY_TAB_MAP[`m0${i}`] = { system: 'register', module: `m0${i}` }
for (let i = 51; i <= 77; i++) {
  const ck = MCODE_TO_COLLECT[i]
  if (ck) LEGACY_TAB_MAP[`m${i}`] = { system: 'collect', module: ck }
}

export function registerNavItems(): HubNavItem[] {
  return REGISTER_MODULES.map(toNavItem)
}

export function registerNavGroups(): HubNavGroup[] {
  return [{ title: '数据资产登记管理', items: REGISTER_MODULES.map(toNavItem) }]
}

/** 采集侧栏：无分组标题，平铺 3 项 */
export function collectNavItems(): HubNavItem[] {
  return COLLECT_MODULES.map(toNavItem)
}

/** @deprecated 使用 collectNavItems */
export function collectNavGroups(): HubNavGroup[] {
  return [{ title: '', items: COLLECT_MODULES.map(toNavItem) }]
}

function toNavItem(m: IngestionModuleMeta): HubNavItem {
  return { key: m.key, label: m.label, subLabel: m.subLabel }
}

function resolveCollectModule(mod: string): string | undefined {
  if (COLLECT_BY_KEY[mod]) return mod
  const m = /^m0?(\d+)$/i.exec(mod)
  if (m) {
    const ck = MCODE_TO_COLLECT[Number(m[1])]
    if (ck) return ck
  }
  return undefined
}

export function resolveIngestionNav(query: Record<string, unknown>): { system: IngestionSystem; module: string } {
  const legacyTab = String(query.tab || '').toLowerCase()
  if (legacyTab) {
    const mapped = LEGACY_TAB_MAP[legacyTab]
    if (mapped) return mapped
  }
  const system = (String(query.system || 'register').toLowerCase() === 'collect' ? 'collect' : 'register') as IngestionSystem
  const mod = String(query.module || '').toLowerCase()
  if (system === 'register' && mod && REGISTER_BY_KEY[mod]) return { system, module: mod }
  if (system === 'collect') {
    const ck = resolveCollectModule(mod)
    if (ck) return { system, module: ck }
  }
  return { system, module: DEFAULT_MODULE[system] }
}

export function moduleTitle(moduleKey: string): string {
  const m = REGISTER_BY_KEY[moduleKey] || COLLECT_BY_KEY[moduleKey]
  return m?.label || moduleKey
}

export function systemTitle(system: IngestionSystem): string {
  return system === 'register' ? '数据资产登记管理' : '数据资源采集汇聚'
}

export type IngestMainTab = 'structured' | 'file' | 'other'

const LEGACY_SECTION_TO_MAIN: Record<string, IngestMainTab> = {
  m051: 'structured', m052: 'structured', m053: 'structured', m054: 'structured',
  structured: 'structured', 'structured-table': 'structured', 'structured-upload': 'structured',
  m055: 'file', m056: 'file', file: 'file', 'file-remote': 'file', 'file-local': 'file',
  m057: 'other', m058: 'other', m059: 'other', m060: 'other', other: 'other',
  'other-unstruct': 'other', 'other-semi': 'other', 'other-api': 'other', 'other-cdc': 'other',
}

export function collectIngestMainTab(query: Record<string, unknown>): IngestMainTab {
  const section = String(query.section || '').toLowerCase()
  if (LEGACY_SECTION_TO_MAIN[section]) return LEGACY_SECTION_TO_MAIN[section]
  const mod = String(query.module || '').toLowerCase()
  const m = /^m0?(\d+)$/i.exec(mod)
  if (m && LEGACY_SECTION_TO_MAIN[`m${Number(m[1])}`]) return LEGACY_SECTION_TO_MAIN[`m${Number(m[1])}`]
  return 'structured'
}

export function collectSectionFromQuery(query: Record<string, unknown>, module: CollectModuleKey): string {
  const section = String(query.section || '').toLowerCase()
  if (section) return section
  const mod = String(query.module || '').toLowerCase()
  const m = /^m0?(\d+)$/i.exec(mod)
  if (m && MCODE_TO_COLLECT[Number(m[1])] === module) return `m${Number(m[1])}`
  const defaults: Record<CollectModuleKey, string> = {
    ingest: 'structured-table',
    pipeline: 'step-probe',
    catalog: 'm065',
  }
  return defaults[module]
}
