import api from '@/api/http'
import { ingestionApi, type DataSource } from '@/views/exchange/ingestion/useIngestionHub'

/** 与元数据采集侧平台分层虚拟源 ID 对齐；并标注过程/资源语义（D07 §9.3） */
export const PLATFORM_LAYER_OPTIONS = [
  {
    id: -1,
    label: '平台 ODS（贴源/汇聚）',
    db: 'smart_city_ods',
    role: 'SOURCE' as const,
    roleLabel: '源层',
    catalogHint: '达标后可走「直通共享」编目进门户',
  },
  {
    id: -2,
    label: '平台 DWD（治理过程）',
    db: 'smart_city_dwd',
    role: 'PROCESS' as const,
    roleLabel: '过程层',
    catalogHint: '过程数据：服务融合流水线，默认不进数据目录门户',
  },
  {
    id: -3,
    label: '平台 DWS（主题/基础库）',
    db: 'smart_city_dws',
    role: 'RESOURCE' as const,
    roleLabel: '资源层',
    catalogHint: '主题库：产出质量达标后可走「加工共享」编目',
  },
  {
    id: -4,
    label: '平台 ADS（专题/应用）',
    db: 'smart_city_ads',
    role: 'RESOURCE' as const,
    roleLabel: '资源层',
    catalogHint: '专题库：产出质量达标后可走「加工共享」编目',
  },
] as const

export type DataLayerRole = 'SOURCE' | 'PROCESS' | 'RESOURCE'

export interface QualitySourceOption {
  id: number
  label: string
  kind: 'platform' | 'external'
  role: DataLayerRole
  roleLabel: string
  catalogHint: string
}

export interface QualityTableMeta {
  sourceTable: string
  columns: string[]
  entryCode?: string
}

export function layerRoleOfSourceId(sourceId: number | undefined | null): DataLayerRole | null {
  if (sourceId == null) return null
  const p = PLATFORM_LAYER_OPTIONS.find((x) => x.id === sourceId)
  if (p) return p.role
  if (sourceId > 0) return 'SOURCE'
  return null
}

export function catalogHintOfSourceId(sourceId: number | undefined | null): string {
  if (sourceId == null) return ''
  const p = PLATFORM_LAYER_OPTIONS.find((x) => x.id === sourceId)
  if (p) return p.catalogHint
  if (sourceId > 0) return '登记源表：达标后可走「直通共享」编目进门户'
  return ''
}

export function extractColumnNames(raw: unknown): string[] {
  if (!Array.isArray(raw)) return []
  const names: string[] = []
  for (const c of raw) {
    if (typeof c === 'string' && c.trim()) {
      names.push(c.trim())
      continue
    }
    if (c && typeof c === 'object') {
      const o = c as Record<string, unknown>
      const n = String(o.columnName || o.name || o.field || '').trim()
      if (n) names.push(n)
    }
  }
  return names
}

export async function loadQualitySourceOptions(): Promise<QualitySourceOption[]> {
  const platforms: QualitySourceOption[] = PLATFORM_LAYER_OPTIONS.map((p) => ({
    id: p.id,
    label: `${p.roleLabel} · ${p.label}`,
    kind: 'platform',
    role: p.role,
    roleLabel: p.roleLabel,
    catalogHint: p.catalogHint,
  }))
  let external: DataSource[] = []
  try {
    external = (await ingestionApi.dataSources()).data || []
  } catch {
    external = []
  }
  return [
    ...platforms,
    ...external.map((s) => ({
      id: s.id,
      label: `源层 · ${s.sourceName || `数据源#${s.id}`}`,
      kind: 'external' as const,
      role: 'SOURCE' as const,
      roleLabel: '源层',
      catalogHint: '登记源表：达标后可走「直通共享」编目进门户',
    })),
  ]
}

export function groupSourcesByRole(sources: QualitySourceOption[]): {
  label: string
  role: DataLayerRole
  options: QualitySourceOption[]
}[] {
  const order: { role: DataLayerRole; label: string }[] = [
    { role: 'SOURCE', label: '源层（直通可共享）' },
    { role: 'PROCESS', label: '过程层（治理中间结果，默认不进目录）' },
    { role: 'RESOURCE', label: '资源层（主题/专题，加工后可共享）' },
  ]
  return order
    .map((g) => ({
      ...g,
      options: sources.filter((s) => s.role === g.role),
    }))
    .filter((g) => g.options.length > 0)
}

export async function loadQualityTables(sourceId: number): Promise<QualityTableMeta[]> {
  const rows = (await api.get(`/governance/platform/metadata/collect/data-sources/${sourceId}/tables`)).data || []
  return (rows as Array<Record<string, unknown>>)
    .map((r) => ({
      sourceTable: String(r.sourceTable || r.tableName || r.name || '').trim(),
      columns: extractColumnNames(r.columns),
      entryCode: r.entryCode ? String(r.entryCode) : undefined,
    }))
    .filter((t) => !!t.sourceTable)
}
