import api from '@/api/http'

/** 统一门户三级导航节点（与 Dashboard 展示结构一致） */
export interface PortalNavNode {
  id: number
  parentId: number
  name: string
  nodeType: string
  sortOrder?: number
  url?: string
  menuPath?: string
  openMode?: string
  ssoMode?: string
  themeKey?: string
  remark?: string
  status?: number
  children?: PortalNavNode[]
}

let cache: PortalNavNode[] | null = null
let inflight: Promise<PortalNavNode[]> | null = null

export function peekPortalNavCache(): PortalNavNode[] | null {
  return cache
}

/** 拉取门户树并写入会话缓存；并发请求合并为一次 */
export async function loadPortalNav(force = false): Promise<PortalNavNode[]> {
  if (!force && cache) return cache
  if (!force && inflight) return inflight
  const req = api
    .get<PortalNavNode[]>('/system/portal-nav/enabled-tree')
    .then((res) => {
      cache = Array.isArray(res.data) ? res.data : []
      return cache
    })
    .finally(() => {
      if (inflight === req) inflight = null
    })
  inflight = req
  return req
}

export function clearPortalNavCache() {
  cache = null
  inflight = null
}
