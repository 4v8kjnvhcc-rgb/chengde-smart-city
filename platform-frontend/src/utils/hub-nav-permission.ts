import type { HubNavItem } from '@/components/common/HubSideLayout.vue'
import type { MenuNode } from '@/stores/auth'

/**
 * 按角色 permission 过滤 Hub 侧栏，使展示与「配置菜单」勾选一致。
 * - 叶子：必须命中 permissionMap[key]（未映射则隐藏）
 * - 分组：任一子项可见则保留
 * - 超级管理员（sys_admin）不裁剪
 */
export function filterHubNavByPermissions(
  items: HubNavItem[],
  permissions: string[] | undefined | null,
  permissionByNavKey: Record<string, string | string[]>,
  options?: { isSystemAdmin?: boolean },
): HubNavItem[] {
  if (options?.isSystemAdmin) return items
  const perms = new Set((permissions || []).filter(Boolean))
  if (!perms.size) return []

  const allowed = (key: string): boolean => {
    const mapped = permissionByNavKey[key]
    if (mapped == null) {
      // 未配置映射的叶子不放行，避免「勾选其它项却多出侧栏」
      return false
    }
    const codes = Array.isArray(mapped) ? mapped : [mapped]
    return codes.some((c) => perms.has(c))
  }

  const walk = (nodes: HubNavItem[]): HubNavItem[] => {
    const out: HubNavItem[] = []
    for (const n of nodes) {
      if (n.children?.length) {
        const children = walk(n.children)
        if (children.length) {
          out.push({ ...n, children })
        } else if (allowed(n.key)) {
          out.push({ ...n, children: [] })
        }
        continue
      }
      if (allowed(n.key)) out.push(n)
    }
    return out
  }

  return walk(items)
}

/**
 * 按用户已授权菜单树过滤 Hub 侧栏（对超管同样生效）。
 * - 叶子必须出现在 menus/me（角色勾选入库的菜单）中，否则隐藏
 * - 叶子命中 path?tab=/module= 或 permission 映射
 * - 自身 visible=0 一律隐藏（菜单管理「是否隐藏」对 Hub 侧栏生效）
 * - 祖先为无 query 的 Hub 页壳 visible=0 不级联；业务分组目录 visible=0 级联隐藏子项
 * - 门户顶栏另由 SidebarMenuItem 排除 hub 的 ?tab=/?module=，与 visible 解耦
 */
export function filterHubNavByMenuVisible(
  items: HubNavItem[],
  menus: MenuNode[] | undefined | null,
  permissionByNavKey: Record<string, string | string[]>,
): HubNavItem[] {
  if (!menus?.length) return items

  const flat: MenuNode[] = []
  const byId = new Map<number, MenuNode>()
  const walkMenu = (nodes: MenuNode[]) => {
    for (const n of nodes) {
      flat.push(n)
      byId.set(n.id, n)
      if (n.children?.length) walkMenu(n.children)
    }
  }
  walkMenu(menus)

  const isHubShellAncestor = (node: MenuNode | undefined): boolean => {
    if (!node || node.integrationType !== 'hub') return false
    if (node.id === 13 || node.id === 14) return true
    const p = node.path || ''
    if (p.includes('?')) return false
    const base = p.split('?')[0]?.split('#')[0] || ''
    return (
      /^\/(governance|resource|unstructured|ingestion|analytics\/(support|bi|population|legal-entity|macro|key-domains))$/.test(
        base,
      )
    )
  }

  const lineageHidden = (node: MenuNode | undefined): boolean => {
    let cur = node
    let self = true
    while (cur) {
      if (cur.visible === 0) {
        if (self) return true
        if (!isHubShellAncestor(cur)) return true
      }
      self = false
      cur = cur.parentId ? byId.get(cur.parentId) : undefined
    }
    return false
  }

  const queryVal = (path: string, name: string): string | null => {
    const i = path.indexOf('?')
    if (i < 0) return null
    return new URLSearchParams(path.slice(i + 1)).get(name)
  }

  const resolveMenu = (key: string): MenuNode | undefined => {
    const tabHit = flat.find((m) => {
      const p = m.path || ''
      return queryVal(p, 'tab') === key || queryVal(p, 'module') === key
    })
    if (tabHit) return tabHit
    const mapped = permissionByNavKey[key]
    if (mapped == null) return undefined
    const codes = Array.isArray(mapped) ? mapped : [mapped]
    return flat.find((m) => !!m.permission && codes.includes(m.permission))
  }

  const isHidden = (key: string): boolean => {
    const node = resolveMenu(key)
    if (!node) return true
    return lineageHidden(node)
  }

  const walk = (nodes: HubNavItem[]): HubNavItem[] => {
    const out: HubNavItem[] = []
    for (const n of nodes) {
      if (n.children?.length) {
        const children = walk(n.children)
        if (children.length) out.push({ ...n, children })
        continue
      }
      if (!isHidden(n.key)) out.push(n)
    }
    return out
  }
  return walk(items)
}

/** 治理 Hub 侧栏 key → sys_menu.permission */
export const GOVERNANCE_NAV_PERMISSIONS: Record<string, string | string[]> = {
  'metadata.category': 'hub:gov:metadata:category',
  'metadata.source': 'hub:gov:metadata:source',
  'metadata.model': 'hub:gov:metadata:model',
  'metadata.collect': 'hub:gov:metadata:collect',
  'metadata.monitor': 'hub:gov:metadata:monitor',
  'metadata.maintain': 'hub:gov:metadata:maintain',
  'metadata.version': 'hub:gov:metadata:version',
  'metadata.catalog': 'hub:gov:metadata:catalog',
  'metadata.analyze': 'hub:gov:metadata:analyze',
  'etl.task-mgmt': 'hub:gov:etl:task-mgmt',
  'etl.task-run': 'hub:gov:etl:task-run',
  'etl.task-schedule': 'hub:gov:etl:task-schedule',
  'etl.etl-monitor': 'hub:gov:etl:etl-monitor',
  'etl.components': 'hub:gov:etl:components',
  'quality.standards.file': 'hub:gov:quality:standards:file',
  'quality.standards.element': 'hub:gov:quality:standards:element',
  'quality.standards.code': 'hub:gov:quality:standards:code',
  'quality.standards.naming': 'hub:gov:quality:standards:naming',
  'quality.rule-config': 'hub:gov:quality:rule-config',
  'quality.monitor': 'hub:gov:quality:monitor',
  'quality.assess': 'hub:gov:quality:assess',
  'quality.reports': 'hub:gov:quality:reports',
  'model.warehouse': 'hub:gov:fusion:warehouse',
  'model.script': 'hub:gov:fusion:script',
  'model.clean': 'hub:gov:fusion:clean',
  'model.schedule': 'hub:gov:fusion:schedule',
  'model.workflow': 'hub:gov:fusion:workflow',
  'model.execute': 'hub:gov:fusion:execute',
  'model.version': 'hub:gov:fusion:version',
  'model.components': 'hub:gov:fusion:components',
  'catalog.resources': 'hub:gov:catalog:resources',
  'catalog.publish': 'hub:gov:catalog:publish',
  'catalog.approvals': 'hub:gov:catalog:approvals',
  'catalog.subscriptions': 'hub:gov:catalog:subscriptions',
  'catalog.portal': 'hub:gov:catalog:portal',
  'indicator.domains': 'hub:gov:indicator:domains',
  'indicator.groups': 'hub:gov:indicator:groups',
  'indicator.tasks': 'hub:gov:indicator:tasks',
}

/** 通用支撑 Hub */
export const SUPPORT_NAV_PERMISSIONS: Record<string, string | string[]> = {
  'users.org': 'hub:analytics:support:users:org',
  'users.user': 'hub:analytics:support:users:user',
  'users.role': 'hub:analytics:support:users:role',
  'users.cluster': 'hub:analytics:support:users:cluster',
  'apps.manage': 'hub:analytics:support:apps:manage',
  'apps.integration': 'hub:analytics:support:apps:integration',
  'apps.portal': 'hub:analytics:support:apps:portal',
  auth: 'hub:analytics:support:auth',
  services: 'hub:analytics:support:services',
  tasks: 'hub:analytics:support:tasks',
  'ops.kettle': 'hub:analytics:support:ops',
  'sys.menus': 'hub:analytics:support:sys:menus',
  'sys.dict': 'hub:analytics:support:sys:dict',
  'sys.cfg.general': 'hub:analytics:support:sys:general',
  'sys.cfg.appearance': 'hub:analytics:support:sys:appearance',
  'sys.cfg.mail': 'hub:analytics:support:sys:mail',
  'sys.cfg.cron': 'hub:analytics:support:sys:cron',
  'sys.tags': 'hub:analytics:support:sys:tags',
  'sys.builtin': 'hub:analytics:support:sys:builtin',
  'audit.log': 'hub:analytics:support:audit:log',
  'audit.runtime': 'hub:analytics:support:audit:runtime',
  'audit.access': 'hub:analytics:support:audit:access',
  'audit.security': 'hub:analytics:support:audit:security',
}

/** 智能 BI */
export const BI_NAV_PERMISSIONS: Record<string, string | string[]> = {
  display: 'hub:analytics:bi:display',
  component: 'hub:analytics:bi:component',
  map: 'hub:analytics:bi:map',
  datasource: 'hub:analytics:bi:datasource',
  design: 'hub:analytics:bi:design',
  self: 'hub:analytics:bi:self',
}

/** 非结构 */
export const UNSTRUCT_NAV_PERMISSIONS: Record<string, string | string[]> = {
  files: 'hub:unstruct:files',
  classify: 'hub:unstruct:classify',
  search: 'hub:unstruct:search',
  metadata: 'hub:unstruct:metadata',
  'process.clean': 'hub:unstruct:process:clean',
  'process.tag': 'hub:unstruct:process:tag',
  'process.link': 'hub:unstruct:process:link',
}

/** 资源中心 */
export const RESOURCE_NAV_PERMISSIONS: Record<string, string | string[]> = {
  asset: 'hub:resource:asset',
  partition: 'hub:resource:partition',
  storage: 'hub:resource:storage',
  catalog: 'hub:resource:catalog',
  search: 'hub:resource:search',
  stats: 'hub:resource:stats',
  monitor: 'hub:resource:monitor',
}

/** 数据供需对接系统侧栏（与 SUPPLY_SIDE_NAV / sys_menu 785x 对齐） */
export const SUPPLY_NAV_PERMISSIONS: Record<string, string | string[]> = {
  home: 'hub:application:supply:home',
  demand: 'hub:application:supply:demand',
  analysis: 'hub:application:supply:analysis',
  confirm: 'hub:application:supply:confirm',
  supply: 'hub:application:supply:supply',
  supervise: 'hub:application:supply:supervise',
  'manifest-center': 'hub:application:supply:manifest',
  system: 'hub:application:supply:system',
  'supply-config': 'hub:application:supply:config',
  'matter-manage': 'hub:application:supply:matter',
}

/** 归集 Hub：登记叶子 + 采集一级（子页走 path 的 module= 匹配） */
export const INGESTION_NAV_PERMISSIONS: Record<string, string | string[]> = {
  m039: 'hub:ingestion:register:m039',
  m040: 'hub:ingestion:register:m040',
  m041: 'hub:ingestion:register:m041',
  m042: 'hub:ingestion:register:m042',
  m043: 'hub:ingestion:register:m043',
  'asset-catalog-reg': 'hub:ingestion:register:asset-catalog-reg',
  'project-system-mgmt': 'hub:ingestion:register:project-system-mgmt',
  m044: 'hub:ingestion:register:m044',
  m045: 'hub:ingestion:register:m045',
  'asset-catalog-mgmt': 'hub:ingestion:register:asset-catalog-mgmt',
  m046: ['hub:ingestion:register:m046', 'hub:ingestion:register:m046:dept'],
  m047: ['hub:ingestion:register:m047', 'hub:ingestion:register:m047:dept'],
  m048: 'hub:ingestion:register:m048',
  m049: 'hub:ingestion:register:m049',
  m050: 'hub:ingestion:register:m050',
  ingest: 'hub:ingestion:collect:ingest',
  'ingest.structured': 'hub:ingestion:collect:ingest:structured',
  'ingest.unstruct': 'hub:ingestion:collect:ingest:unstruct',
  'ingest.semi': 'hub:ingestion:collect:ingest:semi',
  'ingest.api': 'hub:ingestion:collect:ingest:api',
  'ingest.cdc': 'hub:ingestion:collect:ingest:cdc',
  pipeline: 'hub:ingestion:collect:pipeline',
  catalog: 'hub:ingestion:collect:catalog',
  'catalog.resources': 'hub:ingestion:collect:catalog:resources',
  'catalog.classify': 'hub:ingestion:collect:catalog:classify',
  'catalog.publish': 'hub:ingestion:collect:catalog:publish',
  'catalog.approvals': 'hub:ingestion:collect:catalog:approvals',
  quality: 'hub:ingestion:collect:quality',
  'quality.standards': 'hub:ingestion:collect:quality:standards',
  'quality.standards.file': 'hub:ingestion:collect:quality:standards:file',
  'quality.standards.element': 'hub:ingestion:collect:quality:standards:element',
  'quality.standards.code': 'hub:ingestion:collect:quality:standards:code',
  'quality.standards.naming': 'hub:ingestion:collect:quality:standards:naming',
  'quality.rule-config': 'hub:ingestion:collect:quality:rule-config',
  'quality.monitor': 'hub:ingestion:collect:quality:monitor',
  'quality.assess': 'hub:ingestion:collect:quality:assess',
  'quality.reports': 'hub:ingestion:collect:quality:reports',
  asset: 'hub:ingestion:collect:asset',
  'asset.classify': 'hub:ingestion:collect:asset:classify',
  'asset.mask': 'hub:ingestion:collect:asset:mask',
  'asset.tag': 'hub:ingestion:collect:asset:tag',
  'asset.search': 'hub:ingestion:collect:asset:search',
  'asset.backup': 'hub:ingestion:collect:asset:backup',
  'asset.archive': 'hub:ingestion:collect:asset:archive',
  'asset.destroy': 'hub:ingestion:collect:asset:destroy',
  'asset.global': 'hub:ingestion:collect:asset:global',
}
