import type { MenuNode } from '@/stores/auth'

/** 一级平台路径与路由前缀映射 */
export const PLATFORM_ROUTE_MAP: Record<string, string[]> = {
  '/exchange': ['/exchange'],
  '/master-data': ['/master-data', '/governance', '/unstructured', '/resource-center'],
  '/analytics': ['/analytics'],
  '/system': ['/system'],
}

export const PLATFORM_PATHS = Object.keys(PLATFORM_ROUTE_MAP)

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

/** 用户有权限且含子菜单的一级平台（无权限不展示卡片） */
export function getAuthorizedPlatforms(menus: MenuNode[]): MenuNode[] {
  const set = new Set(PLATFORM_PATHS)
  return getMenuRoots(menus)
    .filter((n) => set.has(n.path) && visibleMenuChildren(n).length > 0)
}

export function findPlatformNode(menus: MenuNode[], platformPath: string): MenuNode | undefined {
  return getMenuRoots(menus).find((n) => n.path === platformPath)
}

/** 系统管理域：保持平台级侧栏（用户/角色/机构等并列） */
export function isSystemRoute(path: string): boolean {
  return path === '/system' || path.startsWith('/system/')
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

/** 侧栏上下文：系统管理用平台菜单；其余业务用子系统子菜单（无子菜单则不显示侧栏） */
export function resolveSidebarContext(menus: MenuNode[], path: string): SidebarContext | null {
  if (path === '/dashboard' || path === '/') return null

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
