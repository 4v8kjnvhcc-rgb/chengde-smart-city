import type { HubNavGroup, HubNavItem } from '@/components/common/HubSideLayout.vue'

export type IngestionSystem = 'register' | 'collect'

/** 采集汇聚一级模块（质量管控、数据资产管理含子页） */
export type CollectModuleKey = 'ingest' | 'pipeline' | 'catalog' | 'quality' | 'asset'

/** 汇聚数据质量管控侧栏叶子 key（与治理质量页同组件，双入口） */
export const QUALITY_SUB_KEYS = [
  'quality.standards.file',
  'quality.standards.element',
  'quality.standards.code',
  'quality.standards.naming',
  'quality.rule-config',
  'quality.monitor',
  'quality.assess',
  'quality.reports',
] as const
export type QualitySubKey = (typeof QUALITY_SUB_KEYS)[number]

export const QUALITY_SUB_LABELS: Record<QualitySubKey, string> = {
  'quality.standards.file': '标准文件管理',
  'quality.standards.element': '信息数据元规范',
  'quality.standards.code': '数据编码规范',
  'quality.standards.naming': '命名规范',
  'quality.rule-config': '质量规则配置',
  'quality.monitor': '数据质量监控',
  'quality.assess': '数据质量评估',
  'quality.reports': '数据质量分析报告',
}

/** 数据汇聚接入侧栏叶子（原「其他数据接入」已拆成四个独立菜单） */
export const INGEST_SUB_KEYS = [
  'ingest.structured',
  'ingest.unstruct',
  'ingest.semi',
  'ingest.api',
  'ingest.cdc',
] as const
export type IngestSubKey = (typeof INGEST_SUB_KEYS)[number]

export const INGEST_SUB_LABELS: Record<IngestSubKey, string> = {
  'ingest.structured': '结构化数据接入',
  'ingest.unstruct': '非结构化数据接入',
  'ingest.semi': '半结构化数据接入',
  'ingest.api': 'API 接口数据接入',
  'ingest.cdc': 'CDC 实时数据接入',
}

/** 非结构化等通道类型映射 */
export const INGEST_OTHER_CHANNEL_TYPE: Record<
  Exclude<IngestSubKey, 'ingest.structured'>,
  string
> = {
  'ingest.unstruct': 'UNSTRUCT',
  'ingest.semi': 'SEMI',
  'ingest.api': 'API',
  'ingest.cdc': 'CDC',
}

/** 指标与目录体系构建侧栏叶子 */
export const CATALOG_SUB_KEYS = [
  'catalog.resources',
  'catalog.classify',
  'catalog.publish',
  'catalog.approvals',
] as const
export type CatalogSubKey = (typeof CATALOG_SUB_KEYS)[number]

export const CATALOG_SUB_LABELS: Record<CatalogSubKey, string> = {
  'catalog.resources': '数据资源编目管理',
  'catalog.classify': '数据资源分类',
  'catalog.publish': '资源目录注册发布',
  'catalog.approvals': '数据资源目录审批',
}

export const CATALOG_SUB_PERMISSIONS: Record<CatalogSubKey, string> = {
  'catalog.resources': 'hub:ingestion:collect:catalog:resources',
  'catalog.classify': 'hub:ingestion:collect:catalog:classify',
  'catalog.publish': 'hub:ingestion:collect:catalog:publish',
  'catalog.approvals': 'hub:ingestion:collect:catalog:approvals',
}

/** 数据资产管理侧栏叶子（顺序对齐 V3.0 / 投标功能清单 1.1.1.1.2.5.1～.8） */
export const ASSET_SUB_KEYS = [
  'asset.classify',
  'asset.mask',
  'asset.tag',
  'asset.search',
  'asset.backup',
  'asset.archive',
  'asset.destroy',
  'asset.global',
] as const
export type AssetSubKey = (typeof ASSET_SUB_KEYS)[number]

export const ASSET_SUB_LABELS: Record<AssetSubKey, string> = {
  'asset.classify': '数据分级分类',
  'asset.mask': '数据脱敏策略',
  'asset.tag': '数据标签管理',
  'asset.search': '数据搜索',
  'asset.backup': '数据备份',
  'asset.archive': '数据归档',
  'asset.destroy': '数据销毁',
  'asset.global': '全局数据资产视图',
}

export interface IngestionModuleMeta {
  key: string
  mCode: string
  label: string
  subLabel?: string
  system: IngestionSystem
  /** 与 sys_menu.permission（V80 hub 节点）对齐，用于侧栏按角色授权过滤 */
  permission: string
}

export const REGISTER_MODULES: IngestionModuleMeta[] = [
  { key: 'm039', mCode: 'M039', label: '填报指引', subLabel: '登记引导', system: 'register', permission: 'hub:ingestion:register:m039' },
  { key: 'm040', mCode: 'M040', label: '项目/系统信息登记', subLabel: '项目与系统', system: 'register', permission: 'hub:ingestion:register:m040' },
  { key: 'm041', mCode: 'M041', label: '数据库/表/项登记', subLabel: '数据源与模型', system: 'register', permission: 'hub:ingestion:register:m041' },
  { key: 'm042', mCode: 'M042', label: '数据字典登记', subLabel: '字典登记', system: 'register', permission: 'hub:ingestion:register:m042' },
  { key: 'm043', mCode: 'M043', label: '数据资产标签登记', subLabel: '标签登记', system: 'register', permission: 'hub:ingestion:register:m043' },
  { key: 'asset-catalog-reg', mCode: 'ACR', label: '资产目录登记', subLabel: '登记提交', system: 'register', permission: 'hub:ingestion:register:asset-catalog-reg' },
  { key: 'project-system-mgmt', mCode: 'PSM', label: '项目/系统信息管理', subLabel: '项目审核', system: 'register', permission: 'hub:ingestion:register:project-system-mgmt' },
  { key: 'm044', mCode: 'M044', label: '数据项管理', subLabel: '数据项', system: 'register', permission: 'hub:ingestion:register:m044' },
  { key: 'm045', mCode: 'M045', label: '数据资产标签管理', subLabel: '标签体系', system: 'register', permission: 'hub:ingestion:register:m045' },
  { key: 'asset-catalog-mgmt', mCode: 'ACM', label: '资产目录管理', subLabel: '查看审核', system: 'register', permission: 'hub:ingestion:register:asset-catalog-mgmt' },
  { key: 'm046', mCode: 'M046', label: '数据资产报告', subLabel: '资产大屏', system: 'register', permission: 'hub:ingestion:register:m046' },
  { key: 'm047', mCode: 'M047', label: '数据资产图谱分析', subLabel: '表血缘 / 鱼骨图谱', system: 'register', permission: 'hub:ingestion:register:m047' },
  { key: 'm048', mCode: 'M048', label: '访问控制管理', subLabel: '功能/资源/数据权限', system: 'register', permission: 'hub:ingestion:register:m048' },
  { key: 'm049', mCode: 'M049', label: '系统维护管理', subLabel: '外观/邮箱/账号安全', system: 'register', permission: 'hub:ingestion:register:m049' },
  { key: 'm050', mCode: 'M050', label: '数据字典管理', subLabel: '字典管理', system: 'register', permission: 'hub:ingestion:register:m050' },
]

export const COLLECT_MODULES: IngestionModuleMeta[] = [
  { key: 'ingest', mCode: 'M054', label: '数据汇聚接入', subLabel: '结构化·非结构·半结构·API·CDC', system: 'collect', permission: 'hub:ingestion:collect:ingest' },
  { key: 'pipeline', mCode: 'M061', label: '规范设计', subLabel: '分类·探查·定义·对账', system: 'collect', permission: 'hub:ingestion:collect:pipeline' },
  { key: 'catalog', mCode: 'M065', label: '指标与目录体系构建', subLabel: '编目·分类·注册发布·审批', system: 'collect', permission: 'hub:ingestion:collect:catalog' },
  {
    key: 'quality',
    mCode: 'QCTL',
    label: '汇聚数据质量管控',
    subLabel: '标准体系·规则·监控·评估·分析报告（复用质量管理系统）',
    system: 'collect',
    permission: 'hub:ingestion:collect:quality',
  },
  {
    key: 'asset',
    mCode: 'M069',
    label: '数据资产管理',
    subLabel: '分级分类·脱敏·标签·搜索·备份归档销毁·全局视图',
    system: 'collect',
    permission: 'hub:ingestion:collect:asset',
  },
]

const REGISTER_BY_KEY = Object.fromEntries(REGISTER_MODULES.map((m) => [m.key, m])) as Record<string, IngestionModuleMeta>
const COLLECT_BY_KEY = Object.fromEntries(COLLECT_MODULES.map((m) => [m.key, m])) as Record<string, IngestionModuleMeta>

export const MCODE_TO_COLLECT: Record<number, CollectModuleKey> = {}
for (let i = 51; i <= 60; i++) MCODE_TO_COLLECT[i] = 'ingest'
for (let i = 61; i <= 64; i++) MCODE_TO_COLLECT[i] = 'pipeline'
for (let i = 65; i <= 68; i++) MCODE_TO_COLLECT[i] = 'catalog'
for (let i = 69; i <= 76; i++) MCODE_TO_COLLECT[i] = 'asset'

export const DEFAULT_MODULE: Record<IngestionSystem, string> = {
  register: 'm039',
  collect: 'ingest',
}

export const LEGACY_TAB_MAP: Record<string, { system: IngestionSystem; module: string }> = {
  m037: { system: 'register', module: 'm039' },
  m038: { system: 'register', module: 'm039' },
  register: { system: 'register', module: 'm039' },
  upload: { system: 'collect', module: 'ingest.structured' },
  channel: { system: 'collect', module: 'ingest.structured' },
  ingest: { system: 'collect', module: 'ingest.structured' },
  'ingest.structured': { system: 'collect', module: 'ingest.structured' },
  /** 旧「文件上传」→ 结构化；旧「其他数据接入」→ API（默认叶子） */
  'ingest.file': { system: 'collect', module: 'ingest.structured' },
  'ingest.other': { system: 'collect', module: 'ingest.api' },
  'ingest.unstruct': { system: 'collect', module: 'ingest.unstruct' },
  'ingest.semi': { system: 'collect', module: 'ingest.semi' },
  'ingest.api': { system: 'collect', module: 'ingest.api' },
  'ingest.cdc': { system: 'collect', module: 'ingest.cdc' },
  pipeline: { system: 'collect', module: 'pipeline' },
  resource: { system: 'collect', module: 'catalog.resources' },
  govern: { system: 'collect', module: 'catalog.approvals' },
  catalog: { system: 'collect', module: 'catalog.resources' },
  'catalog.resources': { system: 'collect', module: 'catalog.resources' },
  'catalog.classify': { system: 'collect', module: 'catalog.classify' },
  'catalog.publish': { system: 'collect', module: 'catalog.publish' },
  'catalog.approvals': { system: 'collect', module: 'catalog.approvals' },
  m065: { system: 'collect', module: 'catalog.resources' },
  m066: { system: 'collect', module: 'catalog.classify' },
  m067: { system: 'collect', module: 'catalog.publish' },
  m068: { system: 'collect', module: 'catalog.approvals' },
  asset: { system: 'collect', module: 'asset.classify' },
  'asset.classify': { system: 'collect', module: 'asset.classify' },
  'asset.mask': { system: 'collect', module: 'asset.mask' },
  'asset.tag': { system: 'collect', module: 'asset.tag' },
  'asset.search': { system: 'collect', module: 'asset.search' },
  'asset.global': { system: 'collect', module: 'asset.global' },
  'asset.backup': { system: 'collect', module: 'asset.backup' },
  'asset.archive': { system: 'collect', module: 'asset.archive' },
  'asset.destroy': { system: 'collect', module: 'asset.destroy' },
  m069: { system: 'collect', module: 'asset.classify' },
  m070: { system: 'collect', module: 'asset.mask' },
  m071: { system: 'collect', module: 'asset.tag' },
  m072: { system: 'collect', module: 'asset.search' },
  m073: { system: 'collect', module: 'asset.backup' },
  m074: { system: 'collect', module: 'asset.archive' },
  m075: { system: 'collect', module: 'asset.destroy' },
  m076: { system: 'collect', module: 'asset.global' },
  quality: { system: 'collect', module: 'quality.rule-config' },
  'quality.standards': { system: 'collect', module: 'quality.standards.element' },
  'quality.standards.file': { system: 'collect', module: 'quality.standards.file' },
  'quality.standards.element': { system: 'collect', module: 'quality.standards.element' },
  'quality.standards.code': { system: 'collect', module: 'quality.standards.code' },
  'quality.standards.naming': { system: 'collect', module: 'quality.standards.naming' },
  'quality.rule-config': { system: 'collect', module: 'quality.rule-config' },
  'quality.monitor': { system: 'collect', module: 'quality.monitor' },
  'quality.assess': { system: 'collect', module: 'quality.assess' },
  'quality.reports': { system: 'collect', module: 'quality.reports' },
}
for (let i = 39; i <= 50; i++) LEGACY_TAB_MAP[`m0${i}`] = { system: 'register', module: `m0${i}` }
for (let i = 51; i <= 77; i++) {
  if (i >= 65 && i <= 68) continue
  const ck = MCODE_TO_COLLECT[i]
  if (!ck) continue
  if (ck === 'ingest') {
    LEGACY_TAB_MAP[`m${i}`] = { system: 'collect', module: ingestSubKeyFromMCode(i) }
  } else {
    LEGACY_TAB_MAP[`m${i}`] = { system: 'collect', module: ck }
  }
}
LEGACY_TAB_MAP.m065 = { system: 'collect', module: 'catalog.resources' }
LEGACY_TAB_MAP.m066 = { system: 'collect', module: 'catalog.classify' }
LEGACY_TAB_MAP.m067 = { system: 'collect', module: 'catalog.publish' }
LEGACY_TAB_MAP.m068 = { system: 'collect', module: 'catalog.approvals' }

function ingestSubKeyFromMCode(num: number): IngestSubKey {
  // M055 远程 FTP、M056 本地 → 结构化；M057~M060 拆为独立菜单
  if (num === 57) return 'ingest.unstruct'
  if (num === 58) return 'ingest.semi'
  if (num === 59) return 'ingest.api'
  if (num === 60) return 'ingest.cdc'
  return 'ingest.structured'
}

export function registerNavItems(): HubNavItem[] {
  return REGISTER_MODULES.map(toNavItem)
}

export function registerNavGroups(): HubNavGroup[] {
  return [{ title: '数据资产登记管理', items: REGISTER_MODULES.map(toNavItem) }]
}

/** 采集侧栏：平铺模块；质量管控带三级子项 */
export function collectNavItems(): HubNavItem[] {
  return COLLECT_MODULES.map((m) => toCollectNavItem(m))
}

/** @deprecated 使用 collectNavItems */
export function collectNavGroups(): HubNavGroup[] {
  return [{ title: '', items: COLLECT_MODULES.map((m) => toCollectNavItem(m)) }]
}

/** 按角色权限严格过滤侧栏；无对应 hub 权限则不展示（禁止「无权限时回退全量」） */
export function filterIngestionModules(
  modules: IngestionModuleMeta[],
  opts: { isSystemAdmin: boolean; permissions: string[] },
): IngestionModuleMeta[] {
  if (opts.isSystemAdmin) return modules
  const perms = opts.permissions || []
  return modules.filter((m) => {
    if (perms.includes(m.permission)) return true
    // 部门报告/图谱权限可打开同一模块
    if (m.key === 'm046' && perms.includes('hub:ingestion:register:m046:dept')) return true
    if (m.key === 'm047' && perms.includes('hub:ingestion:register:m047:dept')) return true
    if (m.key === 'catalog') {
      return CATALOG_SUB_KEYS.some((k) => perms.includes(CATALOG_SUB_PERMISSIONS[k]))
    }
    return false
  })
}

export function filterRegisterNavItems(opts: { isSystemAdmin: boolean; permissions: string[] }): HubNavItem[] {
  return filterIngestionModules(REGISTER_MODULES, opts).map(toNavItem)
}

/** 登记侧栏元数据（来自 register-scope） */
export interface RegisterMenuMeta {
  id: number
  parentId: number
  menuName: string
  routeName?: string
  menuType?: number
  path?: string
  permission?: string
  sortOrder?: number
  visible?: number
}

/** 登记侧栏：库表覆盖标题/排序；visible=0 的内置与自定义项均不展示 */
export function buildRegisterNavItems(
  opts: { isSystemAdmin: boolean; permissions: string[] },
  dbMenus?: RegisterMenuMeta[] | null,
): HubNavItem[] {
  const base = filterIngestionModules(REGISTER_MODULES, opts)
  if (!dbMenus?.length) {
    return base.map(toNavItem)
  }
  if (dbMenus.some((m) => m.id === 7000 && Number(m.visible) === 0)) {
    return []
  }

  const byPerm = new Map(
    dbMenus.filter((m) => m.permission).map((m) => [m.permission as string, m]),
  )
  const staticPerms = new Set(REGISTER_MODULES.map((m) => m.permission))
  // 部门报告/图谱权限节点不作为侧栏自定义项重复展示
  staticPerms.add('hub:ingestion:register:m046:dept')
  staticPerms.add('hub:ingestion:register:m047:dept')
  const staticIndex = new Map(REGISTER_MODULES.map((m, i) => [m.key, i]))

  const merged = base
    .map((m) => {
      const db =
        byPerm.get(m.permission)
        || (m.key === 'm046' ? byPerm.get('hub:ingestion:register:m046:dept') : undefined)
        || (m.key === 'm047' ? byPerm.get('hub:ingestion:register:m047:dept') : undefined)
      if (db && Number(db.visible) === 0) return null
      const sort = db?.sortOrder ?? staticIndex.get(m.key) ?? 0
      const label = normalizeRegisterMenuLabel(db?.menuName || m.label)
      return { ...m, label, _sort: sort }
    })
    .filter((m): m is NonNullable<typeof m> => m != null)
    .sort((a, b) => a._sort - b._sort || a.key.localeCompare(b.key))

  const items: HubNavItem[] = merged.map((m) => toNavItem(m))

  const customs = dbMenus
    .filter(
      (m) =>
        m.parentId === 7000
        && m.menuType !== 1
        && m.menuType !== 3
        && Number(m.visible) !== 0
        && m.permission
        && m.permission !== 'hub:ingestion:register:menu-mgmt'
        && !staticPerms.has(m.permission),
    )
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.id - b.id)

  for (const c of customs) {
    if (!opts.isSystemAdmin && !opts.permissions.includes(c.permission!)) continue
    items.push({
      key: `custom-${c.id}`,
      label: normalizeRegisterMenuLabel(c.menuName),
      subLabel: c.routeName || '自定义',
    })
  }
  return items
}

/** 去掉历史「（总体）/（部门）」后缀，侧栏统一简称 */
function normalizeRegisterMenuLabel(name: string): string {
  return String(name || '')
    .replace(/（总体）/g, '')
    .replace(/（部门）/g, '')
    .replace(/\(总体\)/g, '')
    .replace(/\(部门\)/g, '')
    .trim()
}

/** 从登记菜单 path 解析 module query */
export function moduleKeyFromRegisterPath(path?: string): string | null {
  if (!path) return null
  try {
    const q = path.includes('?') ? path.slice(path.indexOf('?') + 1) : ''
    const params = new URLSearchParams(q)
    const mod = params.get('module')
    return mod || null
  } catch {
    return null
  }
}

export function filterCollectNavItems(opts: { isSystemAdmin: boolean; permissions: string[] }): HubNavItem[] {
  // 按角色菜单权限过滤；部门管理员勾选「数据汇聚接入」等即可展示（不再硬编码仅目录三菜单）
  return filterIngestionModules(COLLECT_MODULES, opts)
    .map((m) => toCollectNavItem(m, opts))
    .filter((item) => {
      if (item.key !== 'catalog') return true
      return !item.children || item.children.length > 0
    })
}

/** 侧栏选中的 key 是否在已授权采集模块内（含汇聚接入/质量/资产/目录子页） */
export function isCollectModuleAllowed(
  moduleKey: string,
  allowed: IngestionModuleMeta[],
  opts?: { isSystemAdmin: boolean; permissions: string[] },
): boolean {
  if (allowed.some((m) => m.key === moduleKey)) return true
  if (isIngestSubKey(moduleKey) || moduleKey === 'ingest') {
    return allowed.some((m) => m.key === 'ingest')
  }
  if (isQualitySubKey(moduleKey) || moduleKey === 'quality' || moduleKey === 'quality.standards') {
    return allowed.some((m) => m.key === 'quality')
  }
  if (isAssetSubKey(moduleKey) || moduleKey === 'asset') {
    return allowed.some((m) => m.key === 'asset')
  }
  if (isCatalogSubKey(moduleKey) || moduleKey === 'catalog') {
    if (!allowed.some((m) => m.key === 'catalog')) return false
    if (moduleKey === 'catalog.approvals') return !!opts?.isSystemAdmin
    if (!opts || opts.isSystemAdmin || moduleKey === 'catalog') return true
    const p = CATALOG_SUB_PERMISSIONS[moduleKey as CatalogSubKey]
    return opts.permissions.includes(p) || opts.permissions.includes('hub:ingestion:collect:catalog')
  }
  return false
}

/** 目录模块：落到当前账号有权访问的第一个子页 */
export function firstAllowedCatalogModule(opts: { isSystemAdmin: boolean; permissions: string[] }): CatalogSubKey {
  for (const k of CATALOG_SUB_KEYS) {
    if (k === 'catalog.approvals' && !opts.isSystemAdmin) continue
    if (opts.isSystemAdmin) return k
    const p = CATALOG_SUB_PERMISSIONS[k]
    if (opts.permissions.includes(p) || opts.permissions.includes('hub:ingestion:collect:catalog')) {
      return k
    }
  }
  return 'catalog.resources'
}

export function isIngestSubKey(key: string): key is IngestSubKey {
  return (INGEST_SUB_KEYS as readonly string[]).includes(key)
}

export function isQualitySubKey(key: string): key is QualitySubKey {
  return (QUALITY_SUB_KEYS as readonly string[]).includes(key)
}

export function isAssetSubKey(key: string): key is AssetSubKey {
  return (ASSET_SUB_KEYS as readonly string[]).includes(key)
}

export function isCatalogSubKey(key: string): key is CatalogSubKey {
  return (CATALOG_SUB_KEYS as readonly string[]).includes(key)
}

/** 父级点选时落到默认子页 */
export function normalizeCollectModuleKey(
  key: string,
  opts?: { isSystemAdmin: boolean; permissions: string[] },
): string {
  if (key === 'ingest') return 'ingest.structured'
  if (key === 'quality') return 'quality.rule-config'
  if (key === 'quality.standards') return 'quality.standards.element'
  if (key === 'asset') return 'asset.classify'
  if (key === 'catalog') return opts ? firstAllowedCatalogModule(opts) : 'catalog.resources'
  return key
}

function toNavItem(m: IngestionModuleMeta): HubNavItem {
  return { key: m.key, label: m.label, subLabel: m.subLabel }
}

function toCollectNavItem(
  m: IngestionModuleMeta,
  opts?: { isSystemAdmin: boolean; permissions: string[] },
): HubNavItem {
  if (m.key === 'ingest') {
    return {
      key: m.key,
      label: m.label,
      subLabel: m.subLabel,
      children: INGEST_SUB_KEYS.map((k) => ({ key: k, label: INGEST_SUB_LABELS[k] })),
    }
  }
  if (m.key === 'quality') {
    return {
      key: m.key,
      label: m.label,
      subLabel: m.subLabel,
      children: [
        {
          key: 'quality.standards',
          label: '数据标准体系',
          children: [
            { key: 'quality.standards.file', label: QUALITY_SUB_LABELS['quality.standards.file'] },
            { key: 'quality.standards.element', label: QUALITY_SUB_LABELS['quality.standards.element'] },
            { key: 'quality.standards.code', label: QUALITY_SUB_LABELS['quality.standards.code'] },
            { key: 'quality.standards.naming', label: QUALITY_SUB_LABELS['quality.standards.naming'] },
          ],
        },
        { key: 'quality.rule-config', label: QUALITY_SUB_LABELS['quality.rule-config'] },
        { key: 'quality.monitor', label: QUALITY_SUB_LABELS['quality.monitor'] },
        { key: 'quality.assess', label: QUALITY_SUB_LABELS['quality.assess'] },
        { key: 'quality.reports', label: QUALITY_SUB_LABELS['quality.reports'] },
      ],
    }
  }
  if (m.key === 'asset') {
    return {
      key: m.key,
      label: m.label,
      subLabel: m.subLabel,
      children: ASSET_SUB_KEYS.map((k) => ({ key: k, label: ASSET_SUB_LABELS[k] })),
    }
  }
  if (m.key === 'catalog') {
    const children = CATALOG_SUB_KEYS
      .filter((k) => {
        // 部门管理员无目录审批权限，仅超管可见
        if (k === 'catalog.approvals') return !!opts?.isSystemAdmin
        if (!opts || opts.isSystemAdmin) return true
        const p = CATALOG_SUB_PERMISSIONS[k]
        return opts.permissions.includes(p) || opts.permissions.includes('hub:ingestion:collect:catalog')
      })
      .map((k) => ({ key: k, label: CATALOG_SUB_LABELS[k] }))
    return {
      key: m.key,
      label: m.label,
      subLabel: m.subLabel,
      children,
    }
  }
  return toNavItem(m)
}

function resolveCollectModule(mod: string): string | undefined {
  if (isIngestSubKey(mod)) return mod
  if (mod === 'ingest') return 'ingest.structured'
  if (isQualitySubKey(mod)) return mod
  if (mod === 'quality') return 'quality.rule-config'
  if (mod === 'quality.standards') return 'quality.standards.element'
  if (isAssetSubKey(mod)) return mod
  if (mod === 'asset') return 'asset.classify'
  if (isCatalogSubKey(mod)) return mod
  if (mod === 'catalog') return 'catalog.resources'
  if (COLLECT_BY_KEY[mod]) return mod
  const m = /^m0?(\d+)$/i.exec(mod)
  if (m) {
    const num = Number(m[1])
    if (num === 65) return 'catalog.resources'
    if (num === 66) return 'catalog.classify'
    if (num === 67) return 'catalog.publish'
    if (num === 68) return 'catalog.approvals'
    if (num === 69) return 'asset.classify'
    if (num === 70) return 'asset.mask'
    if (num === 71) return 'asset.tag'
    if (num === 72) return 'asset.search'
    if (num === 73) return 'asset.backup'
    if (num === 74) return 'asset.archive'
    if (num === 75) return 'asset.destroy'
    if (num === 76) return 'asset.global'
    const ck = MCODE_TO_COLLECT[num]
    if (ck === 'catalog') return 'catalog.resources'
    if (ck === 'ingest') return ingestSubKeyFromMCode(num)
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
  if (system === 'register' && mod.startsWith('custom-')) return { system, module: mod }
  if (system === 'collect') {
    const ck = resolveCollectModule(mod)
    if (ck) return { system, module: ck }
  }
  return { system, module: DEFAULT_MODULE[system] }
}

export function moduleTitle(moduleKey: string): string {
  if (moduleKey.startsWith('custom-')) return '自定义菜单'
  if (isIngestSubKey(moduleKey)) {
    return `${COLLECT_BY_KEY.ingest?.label || '数据汇聚接入'} · ${INGEST_SUB_LABELS[moduleKey]}`
  }
  if (isQualitySubKey(moduleKey)) {
    return `${COLLECT_BY_KEY.quality?.label || '汇聚数据质量管控'} · ${QUALITY_SUB_LABELS[moduleKey]}`
  }
  if (isAssetSubKey(moduleKey)) {
    return `${COLLECT_BY_KEY.asset?.label || '数据资产管理'} · ${ASSET_SUB_LABELS[moduleKey]}`
  }
  if (isCatalogSubKey(moduleKey)) {
    return `${COLLECT_BY_KEY.catalog?.label || '指标与目录体系构建'} · ${CATALOG_SUB_LABELS[moduleKey]}`
  }
  const m = REGISTER_BY_KEY[moduleKey] || COLLECT_BY_KEY[moduleKey]
  return m?.label || moduleKey
}

export function systemTitle(system: IngestionSystem): string {
  return system === 'register' ? '数据资产登记管理' : '数据资源采集汇聚'
}

export type IngestMainTab = 'structured' | 'unstruct' | 'semi' | 'api' | 'cdc'

const INGEST_SUB_TO_MAIN: Record<IngestSubKey, IngestMainTab> = {
  'ingest.structured': 'structured',
  'ingest.unstruct': 'unstruct',
  'ingest.semi': 'semi',
  'ingest.api': 'api',
  'ingest.cdc': 'cdc',
}

const LEGACY_SECTION_TO_MAIN: Record<string, IngestMainTab> = {
  m051: 'structured', m052: 'structured', m053: 'structured', m054: 'structured',
  structured: 'structured', 'structured-table': 'structured', 'structured-upload': 'structured',
  'structured-remote': 'structured', 'structured-local': 'structured',
  m056: 'structured', file: 'structured', 'file-local': 'structured',
  m055: 'structured', 'file-remote': 'structured',
  m057: 'unstruct', 'other-unstruct': 'unstruct',
  m058: 'semi', 'other-semi': 'semi',
  m059: 'api', 'other-api': 'api', other: 'api',
  m060: 'cdc', 'other-cdc': 'cdc',
}

export function collectIngestMainTab(query: Record<string, unknown>): IngestMainTab {
  const mod = String(query.module || '').toLowerCase()
  if (isIngestSubKey(mod)) return INGEST_SUB_TO_MAIN[mod]
  if (mod === 'ingest') return 'structured'
  const section = String(query.section || '').toLowerCase()
  if (LEGACY_SECTION_TO_MAIN[section]) return LEGACY_SECTION_TO_MAIN[section]
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
    catalog: 'catalog.resources',
    quality: 'rule-config',
    asset: 'classify',
  }
  return defaults[module]
}
