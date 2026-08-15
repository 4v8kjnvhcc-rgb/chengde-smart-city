import api from '@/api/http'

/** 平台分层库虚拟源 ID（与元数据采集探表接口对齐） */
export const PLATFORM_LAYER_IDS: Record<string, number> = {
  smart_city_ods: -1,
  smart_city_dwd: -2,
  smart_city_dws: -3,
  smart_city_ads: -4,
}

function mapTableNames(rows: unknown): string[] {
  return (rows as Array<{ sourceTable?: string; tableName?: string; name?: string }>)
    .map((r) => String(r.sourceTable || r.tableName || r.name || '').trim())
    .filter(Boolean)
    .sort((a, b) => a.localeCompare(b))
}

/** 按连接键探表：平台分层 / meta:id / ds:id */
export async function fetchConnectionTableNames(connection: string): Promise<string[]> {
  const conn = String(connection || '').trim()
  if (!conn) return []
  if (conn.startsWith('meta:')) {
    const metaId = Number(conn.slice(5))
    if (!Number.isFinite(metaId) || metaId <= 0) return []
    const rows = (await api.get(`/governance/platform/metadata/collect/meta-data-sources/${metaId}/tables`)).data || []
    return mapTableNames(rows)
  }
  let sourceId: number | null = PLATFORM_LAYER_IDS[conn] ?? null
  if (sourceId == null && conn.startsWith('ds:')) {
    const n = Number(conn.slice(3))
    sourceId = Number.isFinite(n) ? n : null
  }
  if (sourceId == null) return []
  return fetchDataSourceTableNames(sourceId)
}

/** 按数据源 ID 探表（登记源 / 分层虚拟源） */
export async function fetchDataSourceTableNames(sourceId: number): Promise<string[]> {
  if (!Number.isFinite(sourceId)) return []
  const rows = (await api.get(`/governance/platform/metadata/collect/data-sources/${sourceId}/tables`)).data || []
  return mapTableNames(rows)
}
