/** 门户 / 目录门户「订阅/收藏」——个人空间「我的订阅」与目录「我的订阅」数据源 */

export interface PortalFavorite {
  catalogId: string
  title: string
  catalogCode?: string
  providerOrg?: string
  resourceType?: string
  resourceTypeLabel?: string
  shareAttr?: string
  openAttr?: string
  updatedAt?: string
  followedAt: string
  /** PORTAL=部门共享门户；GOV=资源目录门户 */
  source?: 'PORTAL' | 'GOV'
  /** 编目资源 ID（source=GOV 时） */
  govResourceId?: number
}

const LEGACY_KEY = 'portal-catalog-follow'
const STORE_KEY = 'portal-catalog-favorites-v1'

function readRaw(): PortalFavorite[] {
  try {
    const raw = localStorage.getItem(STORE_KEY)
    if (raw) {
      const arr = JSON.parse(raw)
      return Array.isArray(arr) ? (arr as PortalFavorite[]) : []
    }
  } catch {
    /* ignore */
  }
  // 兼容旧版仅存 id 的列表
  try {
    const legacy = localStorage.getItem(LEGACY_KEY)
    if (!legacy) return []
    const ids = JSON.parse(legacy)
    if (!Array.isArray(ids)) return []
    const migrated: PortalFavorite[] = ids.map((id: unknown) => ({
      catalogId: String(id),
      title: `资源 #${id}`,
      source: 'PORTAL' as const,
      followedAt: new Date().toISOString(),
    }))
    writeRaw(migrated)
    return migrated
  } catch {
    return []
  }
}

function writeRaw(list: PortalFavorite[]) {
  localStorage.setItem(STORE_KEY, JSON.stringify(list))
  // 同步旧 key，供其它未改造页面 isFollowed 兼容（仅门户 id）
  localStorage.setItem(
    LEGACY_KEY,
    JSON.stringify(list.filter((x) => x.source !== 'GOV').map((x) => x.catalogId)),
  )
}

export function listFavorites(source?: 'PORTAL' | 'GOV'): PortalFavorite[] {
  const all = readRaw().slice().sort((a, b) => String(b.followedAt).localeCompare(String(a.followedAt)))
  if (!source) return all
  return all.filter((x) => (x.source || 'PORTAL') === source)
}

export function isFavorited(catalogId: number | string, source: 'PORTAL' | 'GOV' = 'PORTAL'): boolean {
  const id = String(catalogId)
  return readRaw().some((x) => x.catalogId === id && (x.source || 'PORTAL') === source)
}

export function addFavorite(item: Omit<PortalFavorite, 'followedAt'> & { followedAt?: string }): void {
  const id = String(item.catalogId)
  const src = item.source || 'PORTAL'
  const list = readRaw().filter((x) => !(x.catalogId === id && (x.source || 'PORTAL') === src))
  list.unshift({
    catalogId: id,
    title: item.title || `资源 #${id}`,
    catalogCode: item.catalogCode,
    providerOrg: item.providerOrg,
    resourceType: item.resourceType,
    resourceTypeLabel: item.resourceTypeLabel,
    shareAttr: item.shareAttr,
    openAttr: item.openAttr,
    updatedAt: item.updatedAt,
    source: src,
    govResourceId: item.govResourceId,
    followedAt: item.followedAt || new Date().toISOString(),
  })
  writeRaw(list)
}

export function removeFavorite(catalogId: number | string, source: 'PORTAL' | 'GOV' = 'PORTAL'): void {
  const id = String(catalogId)
  writeRaw(readRaw().filter((x) => !(x.catalogId === id && (x.source || 'PORTAL') === source)))
}

export function favoriteCount(source?: 'PORTAL' | 'GOV'): number {
  return listFavorites(source).length
}
