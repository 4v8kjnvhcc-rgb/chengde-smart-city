export type MetaSection =
  | 'model'
  | 'collect'
  | 'monitor'
  | 'maintain'
  | 'version'
  | 'catalog'
  | 'analyze'

export const metaSectionItems: { key: MetaSection; label: string }[] = [
  { key: 'model', label: '元模型管理' },
  { key: 'collect', label: '元数据采集' },
  { key: 'monitor', label: '元数据采集监控' },
  { key: 'maintain', label: '元数据维护' },
  { key: 'version', label: '元数据版本管理' },
  { key: 'catalog', label: '元数据目录' },
  { key: 'analyze', label: '元数据分析' },
]

const sectionMap: Record<string, MetaSection> = {
  model: 'model', m089: 'model', metamodel: 'model',
  collect: 'collect', m090: 'collect',
  monitor: 'monitor', m091: 'monitor',
  maintain: 'maintain', m092: 'maintain',
  version: 'version', m093: 'version', m094: 'version',
  catalog: 'catalog', m095: 'catalog',
  analyze: 'analyze', m096: 'analyze', lineage: 'analyze',
}

export function resolveMetaSection(raw: unknown): MetaSection {
  const key = String(raw || 'model').toLowerCase()
  return sectionMap[key] || 'model'
}
