export interface DemandAttachment {
  fileName: string
  filePath?: string
  url?: string
  size?: number
}

export interface DemandFormModel {
  id?: number
  providerOrg: string
  providerOrgId?: number
  targetCatalogId?: number
  catalogTitle: string
  dataName: string
  systemNames: string[]
  dataItems: string[]
  serviceDemandType: 'GOV' | 'NON_GOV'
  matterIds: number[]
  matterNames: string[]
  matterCodes: string[]
  matterMaterials: string
  usageScenario: string
  demandBasis: string
  shareProvideMode: string
  updateFrequency: string
  requesterOrg: string
  contactName: string
  contactPhone: string
  contactEmail: string
  demandType: 'STRUCTURED' | 'UNSTRUCTURED'
  templateCode: string
  demandContent: string
  attachments: DemandAttachment[]
}

/** 兼容数组 / JSON 字符串 / 逗号分隔，避免查看时数据项丢失 */
export function normalizeDataItems(raw: unknown): string[] {
  if (Array.isArray(raw)) {
    return raw.map((x) => String(x ?? '').trim()).filter(Boolean)
  }
  if (typeof raw === 'string' && raw.trim()) {
    const s = raw.trim()
    try {
      const parsed = JSON.parse(s)
      if (Array.isArray(parsed)) return normalizeDataItems(parsed)
    } catch {
      // ignore
    }
    return s.split(/[,，;；、\n]+/).map((x) => x.trim()).filter(Boolean)
  }
  return []
}

export function normalizeAttachments(raw: unknown): DemandAttachment[] {
  if (!Array.isArray(raw)) return []
  return raw
    .map((x) => {
      if (!x || typeof x !== 'object') return null
      const o = x as Record<string, unknown>
      const fileName = String(o.fileName || o.name || '').trim()
      if (!fileName) return null
      return {
        fileName,
        filePath: o.filePath != null ? String(o.filePath) : undefined,
        url: o.url != null ? String(o.url) : undefined,
        size: o.size != null ? Number(o.size) : undefined,
      } as DemandAttachment
    })
    .filter(Boolean) as DemandAttachment[]
}
