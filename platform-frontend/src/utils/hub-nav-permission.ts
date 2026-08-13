import type { HubNavItem } from '@/components/common/HubSideLayout.vue'

/**
 * 按角色 permission 过滤 Hub 侧栏，使展示与「配置菜单」勾选一致。
 * - 叶子：须命中 permissionMap[key]，或任意前缀匹配（如父级 permission）
 * - 分组：任一子项可见则保留
 * - 无映射的叶子：有任一同前缀 permission 时可见（宽松兜底）；完全无 map 则对超管外隐藏
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
      // 未配置映射：若存在同 key 尾缀的 permission 则放行（兼容扩展）
      for (const p of perms) {
        if (p.endsWith(`:${key}`) || p.includes(`:${key}:`) || p.includes(`.${key}`)) return true
      }
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
  'quality.rule-config': 'hub:gov:quality:rule-config',
  'quality.monitor': 'hub:gov:quality:monitor',
  'quality.assess': 'hub:gov:quality:assess',
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
  'audit.access': 'hub:analytics:support:audit:access',
  'audit.security': 'hub:analytics:support:audit:security',
  'other.roleMenus': 'hub:analytics:support:other:roleMenus',
  'other.probe': 'hub:analytics:support:other:probe',
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
