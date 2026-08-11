export interface MetaBindSource {
  id: number
  sourceCode?: string
  sourceName: string
  sourceType?: string
  categoryId?: number
  categoryName?: string
  databaseName?: string
  providerOrg?: string
  versionLabel?: string
  sourceKind?: string
  dataLayer?: string
  platformLayer?: boolean
  /** 可选关联的归集登记源 ID（兼容旧链路；三模块选源/选表不再依赖） */
  ingSourceId?: number | null
}

/** 将选中源转为任务连接键：分层库名 / meta:{id} */
export function connectionKeyOf(row: MetaBindSource): string {
  const db = (row.databaseName || '').trim().toLowerCase()
  if (
    db === 'smart_city_ods' ||
    db === 'smart_city_dwd' ||
    db === 'smart_city_dws' ||
    db === 'smart_city_ads'
  ) {
    return db
  }
  const layer = (row.dataLayer || '').trim().toUpperCase()
  if (layer === 'ODS') return 'smart_city_ods'
  if (layer === 'DWD') return 'smart_city_dwd'
  if (layer === 'DWS') return 'smart_city_dws'
  if (layer === 'ADS') return 'smart_city_ads'
  return `meta:${row.id}`
}

export function displayNameOfConnection(
  key: string,
  labelMap?: Record<string, string>,
): string {
  if (!key) return ''
  if (labelMap?.[key]) return labelMap[key]
  if (key.startsWith('meta:')) return `元数据源 #${key.slice(5)}`
  if (key.startsWith('ds:')) return `登记源 #${key.slice(3)}`
  return key
}
