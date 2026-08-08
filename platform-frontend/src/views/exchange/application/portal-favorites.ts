/** 门户 / 目录门户「订阅/收藏」——两端共用服务端表 biz_resource_favorite */

import api from '@/api/http'

export interface PortalFavorite {
  id?: number
  catalogId: string | number
  title: string
  catalogCode?: string
  providerOrg?: string
  resourceType?: string
  resourceTypeLabel?: string
  shareAttr?: string
  openAttr?: string
  updatedAt?: string
  followedAt: string
  /** @deprecated 已统一服务端存储，保留兼容 */
  source?: 'PORTAL' | 'GOV'
  govResourceId?: number
}

const LEGACY_KEY = 'portal-catalog-follow'
const STORE_KEY = 'portal-catalog-favorites-v1'

/** 内存缓存，避免频繁请求；写操作后刷新 */
let cache: PortalFavorite[] | null = null
let cacheAt = 0
const CACHE_TTL_MS = 5_000

function fromApi(row: Record<string, unknown>): PortalFavorite {
  return {
    id: row.id == null ? undefined : Number(row.id),
    catalogId: row.catalogId != null ? String(row.catalogId) : String(row.govResourceId || ''),
    title: String(row.title || ''),
    catalogCode: row.catalogCode != null ? String(row.catalogCode) : undefined,
    providerOrg: row.providerOrg != null ? String(row.providerOrg) : undefined,
    resourceType: row.resourceType != null ? String(row.resourceType) : undefined,
    resourceTypeLabel: row.resourceTypeLabel != null ? String(row.resourceTypeLabel) : undefined,
    followedAt: row.followedAt != null ? String(row.followedAt) : new Date().toISOString(),
    govResourceId: row.govResourceId == null ? undefined : Number(row.govResourceId),
  }
}

async function migrateLocalIfNeeded(): Promise<void> {
  try {
    const raw = localStorage.getItem(STORE_KEY)
    if (!raw) return
    const arr = JSON.parse(raw)
    if (!Array.isArray(arr) || !arr.length) {
      localStorage.removeItem(STORE_KEY)
      localStorage.removeItem(LEGACY_KEY)
      return
    }
    for (const item of arr as PortalFavorite[]) {
      try {
        await api.post('/exchange/portal/favorites', {
          catalogId: item.source === 'GOV' ? undefined : item.catalogId,
          govResourceId: item.govResourceId || (item.source === 'GOV' ? item.catalogId : undefined),
          title: item.title,
          catalogCode: item.catalogCode,
          providerOrg: item.providerOrg,
          resourceType: item.resourceType,
          resourceTypeLabel: item.resourceTypeLabel,
        })
      } catch {
        /* ignore single migrate failure */
      }
    }
    localStorage.removeItem(STORE_KEY)
    localStorage.removeItem(LEGACY_KEY)
  } catch {
    /* ignore */
  }
}

export async function fetchFavorites(_source?: 'PORTAL' | 'GOV'): Promise<PortalFavorite[]> {
  if (cache && Date.now() - cacheAt < CACHE_TTL_MS) {
    return cache
  }
  await migrateLocalIfNeeded()
  const res = await api.get('/exchange/portal/favorites')
  const list = ((res.data || []) as Record<string, unknown>[]).map(fromApi)
  cache = list
  cacheAt = Date.now()
  return list
}

export function invalidateFavoritesCache() {
  cache = null
  cacheAt = 0
}

/** 同步兼容：优先返回缓存；无缓存时返回空（调用方应 await fetchFavorites） */
export function listFavorites(_source?: 'PORTAL' | 'GOV'): PortalFavorite[] {
  return cache ? cache.slice() : []
}

export function isFavorited(catalogId: number | string, _source: 'PORTAL' | 'GOV' = 'PORTAL'): boolean {
  const id = String(catalogId)
  return (cache || []).some(
    (x) => String(x.catalogId) === id || String(x.govResourceId || '') === id,
  )
}

export async function addFavorite(
  item: Omit<PortalFavorite, 'followedAt'> & { followedAt?: string },
): Promise<PortalFavorite> {
  const res = await api.post('/exchange/portal/favorites', {
    catalogId: item.source === 'GOV' && !item.govResourceId ? undefined : item.catalogId,
    govResourceId:
      item.govResourceId ||
      (item.source === 'GOV' ? Number(item.catalogId) || undefined : undefined),
    title: item.title,
    catalogCode: item.catalogCode,
    providerOrg: item.providerOrg,
    resourceType: item.resourceType,
    resourceTypeLabel: item.resourceTypeLabel,
  })
  invalidateFavoritesCache()
  await fetchFavorites()
  return fromApi((res.data || {}) as Record<string, unknown>)
}

export async function removeFavorite(
  catalogId: number | string,
  source: 'PORTAL' | 'GOV' = 'PORTAL',
): Promise<void> {
  const id = String(catalogId)
  const hit = (cache || []).find(
    (x) =>
      (source === 'GOV'
        ? String(x.govResourceId || x.catalogId) === id
        : String(x.catalogId) === id) || String(x.govResourceId || '') === id,
  )
  if (hit?.id) {
    await api.post(`/exchange/portal/favorites/${hit.id}/remove`)
  } else {
    await api.post('/exchange/portal/favorites/remove', {
      catalogId: source === 'PORTAL' ? catalogId : undefined,
      govResourceId: source === 'GOV' ? catalogId : hit?.govResourceId,
    })
  }
  invalidateFavoritesCache()
  await fetchFavorites()
}

export function favoriteCount(_source?: 'PORTAL' | 'GOV'): number {
  return (cache || []).length
}
