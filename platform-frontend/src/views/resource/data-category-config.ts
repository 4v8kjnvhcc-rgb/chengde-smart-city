/** 数据分类：类型 × 级别 → 配置字段矩阵 */

export type CategoryType = 'STATIC' | 'FILE' | 'DYNAMIC' | 'VIDEO'
export type ConfigLevel = 'BASIC' | 'OPTIONAL' | 'ADVANCED'
export type FieldControl = 'select' | 'checkbox' | 'switch' | 'number' | 'input'

export interface FieldOption {
  label: string
  value: string | number | boolean
}

export interface ConfigFieldDef {
  key: string
  label: string
  control: FieldControl
  options?: FieldOption[]
  defaultValue: unknown
  min?: number
  max?: number
  step?: number
  placeholder?: string
}

export const CATEGORY_TYPE_OPTIONS: { value: CategoryType; label: string }[] = [
  { value: 'STATIC', label: '静态基础数据' },
  { value: 'FILE', label: '文件和影像' },
  { value: 'DYNAMIC', label: '动态数据' },
  { value: 'VIDEO', label: '视频数据' },
]

export const CONFIG_LEVEL_OPTIONS: { value: ConfigLevel; label: string; desc: string }[] = [
  { value: 'BASIC', label: '基本配置', desc: '达到数字化管道基本使用要求。' },
  { value: 'OPTIONAL', label: '可选配置', desc: '在满足基本配置下，可适当提升显示效果。' },
  { value: 'ADVANCED', label: '高级配置', desc: '在有特殊需求的情况下，针对性选择以下类型的地理背景数据精度及范围。' },
]

export const FIELD_MATRIX: Record<CategoryType, Record<ConfigLevel, ConfigFieldDef[]>> = {
  STATIC: {
    BASIC: [
      {
        key: 'dataFormat',
        label: '数据格式',
        control: 'select',
        options: [
          { label: '结构化表', value: 'table' },
          { label: '文本文件', value: 'text' },
        ],
        defaultValue: 'table',
      },
      {
        key: 'refreshCycle',
        label: '更新频率',
        control: 'select',
        options: [
          { label: '每月', value: 'monthly' },
          { label: '每季度', value: 'quarterly' },
          { label: '每年', value: 'yearly' },
        ],
        defaultValue: 'quarterly',
      },
      { key: 'metadataRequired', label: '元数据完整性校验', control: 'switch', defaultValue: true },
    ],
    OPTIONAL: [
      { key: 'indexEnabled', label: '建立检索索引', control: 'switch', defaultValue: true },
      {
        key: 'compressionType',
        label: '压缩方式',
        control: 'select',
        options: [
          { label: '无', value: 'none' },
          { label: 'GZIP', value: 'gzip' },
          { label: 'Snappy', value: 'snappy' },
        ],
        defaultValue: 'gzip',
      },
      {
        key: 'displayFormat',
        label: '展示格式优化',
        control: 'select',
        options: [
          { label: '标准', value: 'standard' },
          { label: '增强', value: 'enhanced' },
        ],
        defaultValue: 'standard',
      },
    ],
    ADVANCED: [
      {
        key: 'archivePolicy',
        label: '归档策略',
        control: 'select',
        options: [
          { label: '按年归档', value: 'yearly' },
          { label: '按容量归档', value: 'capacity' },
        ],
        defaultValue: 'yearly',
      },
      { key: 'replicaCount', label: '副本数量', control: 'number', min: 1, max: 5, defaultValue: 2 },
      { key: 'retentionYears', label: '保留年限', control: 'number', min: 1, max: 30, defaultValue: 5 },
    ],
  },
  FILE: {
    BASIC: [
      {
        key: 'mapScale',
        label: '底图比例尺',
        control: 'select',
        options: [
          { label: '1:10000', value: '1:10000' },
          { label: '1:5000', value: '1:5000' },
        ],
        defaultValue: '1:10000',
      },
      {
        key: 'coordinateSystem',
        label: '坐标系',
        control: 'select',
        options: [
          { label: 'CGCS2000', value: 'CGCS2000' },
          { label: 'WGS84', value: 'WGS84' },
        ],
        defaultValue: 'CGCS2000',
      },
      {
        key: 'imageResolution',
        label: '影像分辨率',
        control: 'select',
        options: [
          { label: '≥2m', value: '2m' },
          { label: '≥5m', value: '5m' },
        ],
        defaultValue: '2m',
      },
      {
        key: 'updateCycle',
        label: '数据更新周期',
        control: 'select',
        options: [
          { label: '每年', value: 'yearly' },
          { label: '每半年', value: 'half_yearly' },
        ],
        defaultValue: 'yearly',
      },
      {
        key: 'pipelineLayers',
        label: '管道基础图层',
        control: 'checkbox',
        options: [
          { label: '管段', value: 'segment' },
          { label: '阀室', value: 'valve' },
          { label: '站场', value: 'station' },
          { label: '高后果区', value: 'hca' },
        ],
        defaultValue: ['segment', 'valve', 'station'],
      },
      { key: 'enableBasePipeline', label: '启用数字化管道基础展示', control: 'switch', defaultValue: true },
    ],
    OPTIONAL: [
      { key: 'terrainShading', label: '地形晕渲', control: 'switch', defaultValue: false },
      {
        key: 'labelDisplay',
        label: '标注显示',
        control: 'checkbox',
        options: [
          { label: '道路名称', value: 'road' },
          { label: '地名', value: 'place' },
          { label: '桩号', value: 'stake' },
        ],
        defaultValue: ['road'],
      },
      {
        key: 'symbolStyle',
        label: '符号化样式',
        control: 'select',
        options: [
          { label: '标准样式', value: 'standard' },
          { label: '增强样式', value: 'enhanced' },
        ],
        defaultValue: 'standard',
      },
      {
        key: 'enhancedResolution',
        label: '提升影像分辨率',
        control: 'select',
        options: [
          { label: '1m', value: '1m' },
          { label: '0.5m', value: '0.5m' },
        ],
        defaultValue: '1m',
      },
      { key: 'transparency', label: '图层透明度(%)', control: 'number', min: 0, max: 100, defaultValue: 80 },
      { key: 'nightMode', label: '夜间显示模式', control: 'switch', defaultValue: false },
    ],
    ADVANCED: [
      {
        key: 'geoBackgroundTypes',
        label: '地理背景数据类型',
        control: 'checkbox',
        options: [
          { label: '卫星影像', value: 'satellite' },
          { label: 'DEM高程', value: 'dem' },
          { label: '矢量底图', value: 'vector' },
          { label: '正射影像', value: 'ortho' },
        ],
        defaultValue: ['satellite'],
      },
      {
        key: 'dataPrecision',
        label: '数据精度（比例尺）',
        control: 'select',
        options: [
          { label: '1:500', value: '1:500' },
          { label: '1:1000', value: '1:1000' },
          { label: '1:2000', value: '1:2000' },
          { label: '1:5000', value: '1:5000' },
          { label: '1:10000', value: '1:10000' },
        ],
        defaultValue: '1:2000',
      },
      {
        key: 'coverageScope',
        label: '覆盖范围',
        control: 'select',
        options: [
          { label: '省级', value: 'province' },
          { label: '市级', value: 'city' },
          { label: '区县级', value: 'district' },
          { label: '自定义范围', value: 'custom' },
        ],
        defaultValue: 'city',
      },
      {
        key: 'customRegion',
        label: '自定义范围描述',
        control: 'input',
        placeholder: '如：XX省XX市管线走廊',
        defaultValue: '',
      },
      {
        key: 'demPrecision',
        label: 'DEM精度',
        control: 'select',
        options: [
          { label: '5m', value: '5m' },
          { label: '12.5m', value: '12.5m' },
          { label: '30m', value: '30m' },
        ],
        defaultValue: '12.5m',
      },
      {
        key: 'satelliteResolution',
        label: '卫星影像分辨率',
        control: 'select',
        options: [
          { label: '亚米级', value: 'sub_meter' },
          { label: '1m', value: '1m' },
          { label: '2m', value: '2m' },
        ],
        defaultValue: '1m',
      },
      {
        key: 'pipelineBufferKm',
        label: '管道缓冲区(km)',
        control: 'number',
        min: 0,
        max: 50,
        step: 0.5,
        defaultValue: 5,
      },
    ],
  },
  DYNAMIC: {
    BASIC: [
      {
        key: 'latencyRequirement',
        label: '时效要求',
        control: 'select',
        options: [
          { label: '分钟级', value: 'minute' },
          { label: '小时级', value: 'hour' },
        ],
        defaultValue: 'hour',
      },
      {
        key: 'syncMode',
        label: '同步方式',
        control: 'select',
        options: [
          { label: '增量', value: 'incremental' },
          { label: 'CDC', value: 'cdc' },
        ],
        defaultValue: 'incremental',
      },
      { key: 'qualityCheck', label: '接入质量校验', control: 'switch', defaultValue: true },
    ],
    OPTIONAL: [
      { key: 'realtimeAlert', label: '实时告警', control: 'switch', defaultValue: true },
      { key: 'cacheEnabled', label: '热点数据缓存', control: 'switch', defaultValue: false },
      {
        key: 'displayRefreshSec',
        label: '展示刷新间隔(秒)',
        control: 'number',
        min: 5,
        max: 3600,
        defaultValue: 60,
      },
    ],
    ADVANCED: [
      {
        key: 'streamPartition',
        label: '流分区策略',
        control: 'select',
        options: [
          { label: '按时间', value: 'time' },
          { label: '按业务键', value: 'biz_key' },
        ],
        defaultValue: 'time',
      },
      {
        key: 'qosLevel',
        label: '服务质量等级',
        control: 'select',
        options: [
          { label: '标准', value: 'standard' },
          { label: '高可用', value: 'ha' },
        ],
        defaultValue: 'standard',
      },
      { key: 'failoverEnabled', label: '故障自动切换', control: 'switch', defaultValue: true },
    ],
  },
  VIDEO: {
    BASIC: [
      {
        key: 'videoFormat',
        label: '视频格式',
        control: 'select',
        options: [
          { label: 'MP4', value: 'mp4' },
          { label: 'HLS', value: 'hls' },
          { label: 'FLV', value: 'flv' },
        ],
        defaultValue: 'mp4',
      },
      { key: 'frameRate', label: '帧率(fps)', control: 'number', min: 1, max: 60, defaultValue: 25 },
      {
        key: 'storagePath',
        label: '存储路径规范',
        control: 'input',
        defaultValue: '/video/{region}/{date}',
      },
    ],
    OPTIONAL: [
      { key: 'thumbnailEnabled', label: '缩略图生成', control: 'switch', defaultValue: true },
      {
        key: 'transcodingProfile',
        label: '转码规格',
        control: 'select',
        options: [
          { label: '720P', value: '720p' },
          { label: '1080P', value: '1080p' },
        ],
        defaultValue: '720p',
      },
      { key: 'watermarkEnabled', label: '水印叠加', control: 'switch', defaultValue: false },
    ],
    ADVANCED: [
      {
        key: 'aiAnalysis',
        label: 'AI分析类型',
        control: 'checkbox',
        options: [
          { label: '目标检测', value: 'detect' },
          { label: '行为识别', value: 'behavior' },
          { label: '异常告警', value: 'alert' },
        ],
        defaultValue: [],
      },
      { key: 'retentionDays', label: '录像保留天数', control: 'number', min: 1, max: 365, defaultValue: 90 },
      { key: 'clipBufferSec', label: '事件前后缓冲(秒)', control: 'number', min: 0, max: 300, defaultValue: 30 },
    ],
  },
}

export type LevelConfigs = Record<ConfigLevel, Record<string, unknown>>

export function buildDefaultLevel(type: CategoryType, level: ConfigLevel): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const f of FIELD_MATRIX[type][level]) {
    out[f.key] = Array.isArray(f.defaultValue) ? [...(f.defaultValue as unknown[])] : f.defaultValue
  }
  return out
}

export function buildDefaultConfigs(type: CategoryType): LevelConfigs {
  return {
    BASIC: buildDefaultLevel(type, 'BASIC'),
    OPTIONAL: buildDefaultLevel(type, 'OPTIONAL'),
    ADVANCED: buildDefaultLevel(type, 'ADVANCED'),
  }
}

export function ensureLevelConfigs(type: CategoryType, raw: unknown): LevelConfigs {
  const base = buildDefaultConfigs(type)
  if (!raw || typeof raw !== 'object') return base
  const obj = raw as Record<string, unknown>
  for (const level of ['BASIC', 'OPTIONAL', 'ADVANCED'] as ConfigLevel[]) {
    const cur = obj[level]
    if (cur && typeof cur === 'object') {
      base[level] = { ...base[level], ...(cur as Record<string, unknown>) }
    }
  }
  return base
}

export function categoryTypeLabel(type: string) {
  return CATEGORY_TYPE_OPTIONS.find((o) => o.value === type)?.label || type
}

export function configLevelLabel(level: string) {
  return CONFIG_LEVEL_OPTIONS.find((o) => o.value === level)?.label || level
}

export function configLevelDesc(level: string) {
  return CONFIG_LEVEL_OPTIONS.find((o) => o.value === level)?.desc || ''
}
