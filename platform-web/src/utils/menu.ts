import type { MenuNode } from '@/stores/auth'

/** 一级平台路径与路由前缀映射 */
export const PLATFORM_ROUTE_MAP: Record<string, string[]> = {
  '/exchange': ['/exchange', '/catalog/exchange'],
  '/master-data': ['/master-data', '/governance', '/unstructured', '/resource-center', '/catalog/master-data'],
  '/analytics': ['/analytics', '/catalog/analytics'],
  '/business': ['/business'],
  '/system': ['/system', '/integration', '/catalog/system'],
}

export const PLATFORM_PATHS = Object.keys(PLATFORM_ROUTE_MAP)

export const PLATFORM_LABELS: Record<string, string> = {
  '/exchange': '数据共享交换平台',
  '/master-data': '主数据平台',
  '/analytics': '大数据挖掘分析平台',
  '/business': '业务功能平台',
  '/system': '系统管理',
}

export function visibleMenuChildren(node: MenuNode): MenuNode[] {
  return (node.children || []).filter((c) => c.menuType !== 3)
}

/** 菜单树根下的一级子节点（通常为「统一门户」下的工作台与各平台） */
export function getMenuRoots(menus: MenuNode[]): MenuNode[] {
  if (!menus.length) return []
  const root = menus[0]
  if (
    menus.length === 1 &&
    (root.path === '/' || root.menuName === '统一门户' || root.parentId === 0)
  ) {
    return root.children || []
  }
  return menus
}

export function matchPlatformPath(path: string): string | null {
  for (const [platform, prefixes] of Object.entries(PLATFORM_ROUTE_MAP)) {
    for (const prefix of prefixes) {
      if (path === prefix || path.startsWith(`${prefix}/`)) return platform
    }
  }
  return null
}

/** 用户有权限的一级平台（业务功能平台允许无菜单子项，靠外链填充） */
export function getAuthorizedPlatforms(menus: MenuNode[]): MenuNode[] {
  const set = new Set(PLATFORM_PATHS)
  return getMenuRoots(menus).filter((n) => {
    if (!set.has(n.path)) return false
    if (n.path === '/business') return true
    return visibleMenuChildren(n).length > 0
  })
}

export function findPlatformNode(menus: MenuNode[], platformPath: string): MenuNode | undefined {
  return getMenuRoots(menus).find((n) => n.path === platformPath)
}

/** 使用 HubSideLayout 内嵌侧栏的页面：不展示 MainLayout 外层平台侧栏 */
export function isHubLayoutRoute(path: string, meta?: { hubLayout?: boolean }): boolean {
  if (meta?.hubLayout) return true
  return HUB_LAYOUT_PATHS.has(path)
}

const HUB_LAYOUT_PATHS = new Set([
  '/exchange/ingestion',
  '/exchange/application',
  '/exchange/assessment',
  '/exchange/portal',
  '/governance',
  '/unstructured',
  '/resource-center',
  '/analytics/support',
  '/analytics/bi',
  '/analytics/population',
  '/analytics/legal-entity',
  '/analytics/macro',
  '/analytics/key-domains',
])

/** 系统管理域：保持平台级侧栏（用户/角色/机构等并列） */
export function isSystemRoute(path: string): boolean {
  return (
    path === '/system' ||
    path.startsWith('/system/') ||
    path === '/integration' ||
    path.startsWith('/integration/') ||
    path === '/catalog/system' ||
    path.startsWith('/catalog/system') ||
    (path.startsWith('/modules/M21') && /^\/modules\/M21[0-5]$/.test(path))
  )
}

function findMenuTrail(menus: MenuNode[], targetPath: string, trail: MenuNode[] = []): MenuNode[] | null {
  for (const n of menus) {
    const next = [...trail, n]
    if (n.path === targetPath) return next
    if (n.children?.length) {
      const hit = findMenuTrail(n.children, targetPath, next)
      if (hit) return hit
    }
  }
  return null
}

function findD05Root(trail: MenuNode[]): MenuNode | undefined {
  return trail.find((n) => n.menuName === 'D05功能清单')
}

function platformFromModuleCode(mCode: string): string | null {
  const n = parseInt(mCode.replace(/^M/i, ''), 10)
  if (Number.isNaN(n)) return null
  if (n >= 1 && n <= 77) return '/exchange'
  if (n >= 78 && n <= 138) return '/master-data'
  if (n >= 139 && n <= 209) return '/analytics'
  if (n >= 210 && n <= 215) return '/system'
  return null
}

/**
 * 非系统管理路由：匹配所属子系统（平台下直接子节点，如「大数据归集」「数据融合治理」）
 */
export function findSubsystemRoot(menus: MenuNode[], path: string): MenuNode | undefined {
  if (isSystemRoute(path)) return undefined

  const platformPath = matchPlatformPath(path)
  if (!platformPath) return undefined

  const platformNode = findPlatformNode(menus, platformPath)
  if (!platformNode) return undefined

  for (const child of visibleMenuChildren(platformNode)) {
    if (!child.path) continue
    if (path === child.path || path.startsWith(`${child.path}/`)) return child
  }
  return undefined
}

export interface SidebarContext {
  title: string
  menus: MenuNode[]
}

/** 侧栏上下文：系统管理用平台菜单；Hub 内嵌页不显示外层侧栏；D05 清单页保留 D05 树 */
export function resolveSidebarContext(menus: MenuNode[], path: string, meta?: { hubLayout?: boolean }): SidebarContext | null {
  if (path === '/dashboard' || path === '/') return null
  if (path === '/catalog') return null
  if (isHubLayoutRoute(path, meta)) return null

  const roots = getMenuRoots(menus)

  if (path.startsWith('/modules/') || path.startsWith('/catalog/')) {
    let platformPath = matchPlatformPath(path)
    if (!platformPath && path.startsWith('/modules/')) {
      const mCode = path.replace('/modules/', '').toUpperCase()
      platformPath = platformFromModuleCode(mCode)
    }
    if (platformPath) {
      const trail = findMenuTrail(roots, path)
      const d05 = trail ? findD05Root(trail) : undefined
      if (d05?.children?.length) {
        return { title: d05.menuName, menus: visibleMenuChildren(d05) }
      }
      const platform = findPlatformNode(menus, platformPath)
      if (platform) {
        const d05Child = visibleMenuChildren(platform).find((c) => c.menuName === 'D05功能清单')
        if (d05Child?.children?.length) {
          return { title: d05Child.menuName, menus: visibleMenuChildren(d05Child) }
        }
      }
    }
  }

  if (isSystemRoute(path)) {
    const platform = findPlatformNode(menus, '/system')
    if (!platform) return null
    const list = visibleMenuChildren(platform)
    if (!list.length) return null
    return { title: platform.menuName, menus: list }
  }

  const subsystem = findSubsystemRoot(menus, path)
  if (!subsystem) return null

  const list = visibleMenuChildren(subsystem)
  if (!list.length) return null

  return { title: subsystem.menuName, menus: list }
}

/** 进入平台时默认跳转的首个子菜单路径 */
export function firstNavPath(node: MenuNode): string {
  const children = visibleMenuChildren(node)
  for (const child of children) {
    const sub = firstNavPath(child)
    if (sub) return sub
  }
  if (node.path && node.path !== '/' && node.menuType !== 1) return node.path
  return ''
}
