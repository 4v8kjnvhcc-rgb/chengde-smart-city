<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { LAYER_OPTIONS, TAG_OPTIONS } from './meta-labels'
import { statusLabel } from '@/utils/status-label'

interface Entry {
  id: number
  entryCode: string
  entryName: string
  entryType: string
  parentCode?: string
  databaseName?: string
  dataLayer?: string
  physicalTableName?: string
  businessDomain?: string
  securityLevel?: string
  ownerName?: string
  description?: string
  tags?: string
  keywords?: string
  conformStatus?: string
  updatedAt?: string
  status: string
}

interface TreeNode {
  label: string
  code: string
  children?: TreeNode[]
  entryId?: number
  entryCode?: string
  leaf?: boolean
}

interface Version {
  id: number
  versionNo: number
  changeSummary?: string
  createdBy?: string
  createdAt?: string
}

interface Inventory {
  tableCount?: number
  idleCount?: number
  byLayer?: Record<string, number>
  idleEntryCodes?: string[]
}

const router = useRouter()

const catalogKind = ref<'source' | 'asset'>('asset')
const keyword = ref('')
const tag = ref('')
const loading = ref(false)

const entries = ref<Entry[]>([])
const treeData = ref<TreeNode[]>([])
const domainOptions = ref<string[]>([])
const tagOptions = ref<string[]>([])
const selectedCode = ref('')
const inventory = ref<Inventory>({})
const kpi = reactive({ entryCount: 0, tableCount: 0, sourceCount: 0 })

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailEntry = ref<Entry | null>(null)
const detailColumns = ref<Entry[]>([])
const detailVersions = ref<Version[]>([])

const versionVisible = ref(false)
const versionLoading = ref(false)
const versionEntry = ref<Entry | null>(null)
const versions = ref<Version[]>([])
const versionDetailVisible = ref(false)
const versionDetailLoading = ref(false)
const versionDetail = ref<{
  version?: Version
  basicInfo?: Record<string, unknown>
  fields?: Array<Record<string, unknown>>
} | null>(null)

const compareVisible = ref(false)
const compareLoading = ref(false)
const compareForm = reactive({ leftId: undefined as number | undefined, rightId: undefined as number | undefined })
const compareData = ref<{
  sameSnapshot?: boolean
  basicDiff?: Array<{ field: string; left: string; right: string }>
  attrDiff?: Array<{ fieldName: string; attr: string; left: string; right: string; changeType: string }>
  left?: Version
  right?: Version
} | null>(null)

const mergedTagOptions = computed(() => {
  const set = new Set<string>([...TAG_OPTIONS, ...tagOptions.value])
  return [...set]
})

function layerLabel(layer?: string) {
  if (!layer) return '—'
  const opt = LAYER_OPTIONS.find(o => o.value === layer)
  return opt?.label || statusLabel(layer)
}

function formatTime(v?: string) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}

function fieldLabel(field: string) {
  const map: Record<string, string> = {
    entryName: '名称',
    entryCode: '编码',
    description: '说明',
    tags: '标签',
    businessDomain: '主题域',
    securityLevel: '分级',
    physicalTableName: '表名',
    databaseName: '数据库',
    dataLayer: '分层',
    status: '状态',
    changeFlag: '变更标记',
  }
  return map[field] || field
}

function attrLabel(attr: string) {
  const map: Record<string, string> = {
    nameZh: '中文名',
    nameEn: '英文名',
    dataType: '数据类型',
    length: '长度',
    primaryKey: '主键',
    partition: '分区',
    unit: '计量单位',
    description: '描述',
    整行: '整行',
  }
  return map[attr] || attr
}

function isIdleRow(row: Entry) {
  if (row.entryType !== 'TABLE') return false
  return (inventory.value.idleEntryCodes || []).includes(row.entryCode)
}

const inventoryBar = computed(() => {
  const parts = [
    catalogKind.value === 'source' ? `数据源 ${kpi.sourceCount}` : `资产表 ${kpi.tableCount}`,
    `条目 ${kpi.entryCount}`,
  ]
  if (inventory.value.idleCount != null) parts.push(`闲置 ${inventory.value.idleCount}`)
  const byLayer = inventory.value.byLayer || {}
  const layerParts = ['ODS', 'DWD', 'DWS', 'ADS']
    .filter(k => byLayer[k] != null)
    .map(k => `${layerLabel(k)} ${byLayer[k]}`)
  if (layerParts.length) parts.push(layerParts.join(' · '))
  return parts.join(' ｜ ')
})

const tableRows = computed(() => {
  let rows = entries.value.filter(e => e.entryType === 'TABLE')
  const code = selectedCode.value
  if (!code) return rows
  if (code.startsWith('biz:')) {
    const biz = code.slice(4)
    const sourceCodes = new Set(
      entries.value
        .filter(e => (e.entryType === 'SOURCE' || e.entryType === 'CONNECTOR')
          && (e.businessDomain || '未归属业务系统') === biz)
        .map(e => e.entryCode),
    )
    return rows.filter(e =>
      (e.businessDomain || '未归属业务系统') === biz
      || (e.parentCode && sourceCodes.has(e.parentCode)),
    )
  }
  if (code.startsWith('src:')) {
    const src = code.slice(4)
    if (src.startsWith('__none__')) {
      const biz = src.slice('__none__:'.length)
      return rows.filter(e => !e.parentCode && (e.businessDomain || '未归属业务系统') === biz)
    }
    return rows.filter(e => e.parentCode === src || e.entryCode === src)
  }
  if (code.startsWith('db:')) {
    // db:sourceCode:dbName 或 db:biz:dbName
    const rest = code.slice(3)
    const idx = rest.indexOf(':')
    const left = idx >= 0 ? rest.slice(0, idx) : rest
    const dbName = idx >= 0 ? rest.slice(idx + 1) : ''
    return rows.filter(e => {
      const db = e.databaseName || '未命名库'
      if (db !== dbName) return false
      if (left.startsWith('未归属') || !entries.value.find(s => s.entryCode === left)) {
        return true
      }
      return e.parentCode === left
    })
  }
  if (code.startsWith('layer:')) {
    const layer = code.slice(6)
    return rows.filter(e => resolveLayer(e) === layer)
  }
  if (code.startsWith('domain:')) {
    // domain:LAYER:主题域
    const rest = code.slice(7)
    const idx = rest.indexOf(':')
    const layer = idx >= 0 ? rest.slice(0, idx) : ''
    const domain = idx >= 0 ? rest.slice(idx + 1) : rest
    return rows.filter(e =>
      resolveLayer(e) === layer
      && (e.businessDomain || '未划分主题域') === domain,
    )
  }
  return rows.filter(e => e.entryCode === code || e.parentCode === code)
})
const {
  page: tablePage,
  pageSize: tablePageSize,
  paged: pagedTableRows,
  total: tableTotal,
  resetPage: resetTablePage,
} = useClientPager(tableRows)

function resolveLayer(e: Entry) {
  if (e.dataLayer && ['ODS', 'DWD', 'DWS', 'ADS'].includes(e.dataLayer)) return e.dataLayer
  if (e.databaseName === 'smart_city_ods') return 'ODS'
  if (e.databaseName === 'smart_city_dwd') return 'DWD'
  if (e.databaseName === 'smart_city_dws') return 'DWS'
  if (e.databaseName === 'smart_city_ads') return 'ADS'
  const name = (e.physicalTableName || e.entryName || '').toLowerCase()
  if (name.startsWith('dwd_')) return 'DWD'
  if (name.startsWith('dws_')) return 'DWS'
  if (name.startsWith('ads_')) return 'ADS'
  return 'ODS'
}

function columnChildren(parentCode: string) {
  return entries.value.filter(e => e.entryType === 'COLUMN' && e.parentCode === parentCode)
}

async function loadBrowse() {
  loading.value = true
  try {
    const res = await api.get('/governance/platform/metadata/catalog/browse', {
      params: {
        keyword: keyword.value || undefined,
        tag: tag.value || undefined,
        catalogKind: catalogKind.value,
      },
    })
    entries.value = res.data?.entries || []
    treeData.value = res.data?.tree || []
    tagOptions.value = res.data?.tags || []
    domainOptions.value = res.data?.domains || []
    const k = res.data?.kpi || {}
    kpi.entryCount = Number(k.entryCount || 0)
    kpi.tableCount = Number(k.tableCount || 0)
    kpi.sourceCount = Number(k.sourceCount || 0)
    selectedCode.value = ''
    resetTablePage()
  } finally {
    loading.value = false
  }
}

async function loadInventory() {
  inventory.value = (await api.get('/governance/platform/metadata/catalog/inventory')).data || {}
}

function onTreeClick(node: TreeNode) {
  selectedCode.value = node.code || ''
}

async function offlineEntry(row: Entry) {
  try {
    await ElMessageBox.confirm(
      `确定下线「${row.entryName}」？下线后将不在目录中显示，也无法被查询到。`,
      '元数据下线',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await api.post(`/governance/platform/metadata/catalog/${row.id}/offline`)
  ElMessage.success('已下线')
  await Promise.all([loadBrowse(), loadInventory()])
}

async function openDetail(row: Entry) {
  detailVisible.value = true
  detailLoading.value = true
  detailEntry.value = null
  detailColumns.value = []
  detailVersions.value = []
  try {
    const data = (await api.get(`/governance/platform/metadata/catalog/entries/${row.id}`)).data
    detailEntry.value = data?.entry || row
    detailColumns.value = data?.columns || columnChildren(row.entryCode)
    detailVersions.value = data?.versions || []
  } finally {
    detailLoading.value = false
  }
}

async function openVersions(row: Entry) {
  versionEntry.value = row
  versionVisible.value = true
  versionLoading.value = true
  versions.value = []
  try {
    versions.value = (await api.get('/governance/platform/metadata/versions', {
      params: { targetType: 'ENTRY', targetId: row.id },
    })).data || []
  } finally {
    versionLoading.value = false
  }
}

async function openVersionDetail(v: Version) {
  versionDetailVisible.value = true
  versionDetailLoading.value = true
  versionDetail.value = null
  try {
    versionDetail.value = (await api.get(`/governance/platform/metadata/versions/${v.id}/detail`)).data
  } finally {
    versionDetailLoading.value = false
  }
}

function openCompare() {
  compareData.value = null
  compareForm.leftId = versions.value[1]?.id
  compareForm.rightId = versions.value[0]?.id
  compareVisible.value = true
}

async function doCompare() {
  if (!compareForm.leftId || !compareForm.rightId) {
    ElMessage.warning('请选择两个版本')
    return
  }
  compareLoading.value = true
  try {
    compareData.value = (await api.get('/governance/platform/metadata/versions/compare', {
      params: { leftId: compareForm.leftId, rightId: compareForm.rightId },
    })).data
  } finally {
    compareLoading.value = false
  }
}

function goQuality(row: Entry) {
  router.push({
    name: 'governance',
    query: { tab: 'quality', qSub: 'task-mgmt', entryCode: row.entryCode },
  }).catch(() => ElMessage.info(`请前往质量任务，关联编码：${row.entryCode}`))
}

function goCatalog(row: Entry) {
  router.push({
    name: 'governance',
    query: { tab: 'catalog', cSub: 'resources', entryCode: row.entryCode },
  }).catch(() => ElMessage.info(`请前往资源编目，关联编码：${row.entryCode}`))
}

function resetFilter() {
  keyword.value = ''
  tag.value = ''
  loadBrowse()
}

watch(catalogKind, () => loadBrowse())
watch(selectedCode, resetTablePage)

onMounted(async () => {
  await Promise.all([loadBrowse(), loadInventory()])
})
</script>

<template>
  <div>
    <div class="mcatalog-kpi">
      <div class="mcatalog-kpi__card">
        <span>{{ catalogKind === 'source' ? '数据源' : '资产表' }}</span>
        <b>{{ catalogKind === 'source' ? kpi.sourceCount : kpi.tableCount }}</b>
      </div>
      <div class="mcatalog-kpi__card tone-info">
        <span>目录条目</span>
        <b>{{ kpi.entryCount }}</b>
      </div>
      <div class="mcatalog-kpi__card tone-warn">
        <span>闲置表</span>
        <b>{{ inventory.idleCount ?? 0 }}</b>
      </div>
      <div class="mcatalog-kpi__card tone-ok">
        <span>主题域</span>
        <b>{{ domainOptions.length }}</b>
      </div>
    </div>

    <PageCard title="元数据目录">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="目录" class="portal-field-md">
          <el-radio-group v-model="catalogKind" size="default">
            <el-radio-button value="source">数据源目录</el-radio-button>
            <el-radio-button value="asset">数据资产目录</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="关键字" class="portal-field-lg">
          <el-input
            v-model="keyword"
            clearable
            placeholder="名称/编码/表名/字段名"
            @keyup.enter="loadBrowse"
          />
        </el-form-item>
        <el-form-item label="标签" class="portal-field-md">
          <el-select v-model="tag" clearable filterable allow-create placeholder="标签过滤">
            <el-option v-for="t in mergedTagOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" :loading="loading" @click="loadBrowse">搜索</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>

      <el-alert type="success" :closable="false" :title="inventoryBar" style="margin-bottom: 12px" />

      <el-row :gutter="12">
        <el-col :span="7">
          <div class="mcatalog-section">目录检索</div>
          <el-tree
            v-loading="loading"
            :data="treeData"
            node-key="code"
            highlight-current
            default-expand-all
            :props="{ label: 'label', children: 'children' }"
            @node-click="onTreeClick"
          />
        </el-col>
        <el-col :span="17">
          <div class="mcatalog-section">元数据列表</div>
          <el-table v-loading="loading" :data="pagedTableRows" stripe size="small" max-height="520" row-key="id">
            <el-table-column type="expand">
              <template #default="{ row }">
                <el-table
                  v-if="columnChildren(row.entryCode).length"
                  :data="columnChildren(row.entryCode)"
                  size="small"
                  stripe
                  style="margin: 8px 0 8px 48px"
                >
                  <el-table-column prop="entryName" label="字段英文名" min-width="120" />
                  <el-table-column prop="keywords" label="字段中文名" min-width="120" show-overflow-tooltip>
                    <template #default="{ row: col }">{{ col.keywords || col.entryName || '—' }}</template>
                  </el-table-column>
                  <el-table-column prop="entryCode" label="编码" width="160" show-overflow-tooltip />
                  <el-table-column prop="description" label="说明" show-overflow-tooltip />
                </el-table>
                <el-empty v-else description="无字段子项" :image-size="48" />
              </template>
            </el-table-column>
            <el-table-column label="名称" min-width="150">
              <template #default="{ row }">
                <div class="mcatalog-name">{{ row.entryName }}</div>
                <div class="mcatalog-code">{{ row.entryCode }}</div>
                <el-tag v-if="isIdleRow(row)" type="info" size="small">闲置</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="physicalTableName" label="表名" width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.physicalTableName || row.entryName }}</template>
            </el-table-column>
            <el-table-column prop="databaseName" label="库名" width="110" show-overflow-tooltip />
            <el-table-column label="分层/主题" width="130">
              <template #default="{ row }">
                <div>{{ layerLabel(row.dataLayer || resolveLayer(row)) }}</div>
                <div class="mcatalog-code">{{ row.businessDomain || '—' }}</div>
              </template>
            </el-table-column>
            <el-table-column label="标签" width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.tags || '—' }}</template>
            </el-table-column>
            <el-table-column label="分级" width="80">
              <template #default="{ row }">{{ statusLabel(row.securityLevel) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDetail(row)">详情</el-button>
                <el-button link type="primary" @click="openVersions(row)">历史版本</el-button>
                <el-button link type="primary" @click="goQuality(row)">质量</el-button>
                <el-button link type="danger" @click="offlineEntry(row)">下线</el-button>
              </template>
            </el-table-column>
          </el-table>
          <PortalPagination
            v-model:page="tablePage"
            v-model:page-size="tablePageSize"
            :total="tableTotal"
          />
        </el-col>
      </el-row>
    </PageCard>

    <!-- 详情 -->
    <el-drawer v-model="detailVisible" title="元数据详情" size="640px">
      <div v-loading="detailLoading">
        <template v-if="detailEntry">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="名称">{{ detailEntry.entryName }}</el-descriptions-item>
            <el-descriptions-item label="编码">{{ detailEntry.entryCode }}</el-descriptions-item>
            <el-descriptions-item label="表名">{{ detailEntry.physicalTableName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="数据库">{{ detailEntry.databaseName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="分层">{{ layerLabel(detailEntry.dataLayer) }}</el-descriptions-item>
            <el-descriptions-item label="主题域">{{ detailEntry.businessDomain || '—' }}</el-descriptions-item>
            <el-descriptions-item label="标签">{{ detailEntry.tags || '—' }}</el-descriptions-item>
            <el-descriptions-item label="分级">{{ statusLabel(detailEntry.securityLevel) }}</el-descriptions-item>
            <el-descriptions-item label="责任人">{{ detailEntry.ownerName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="版本数">{{ detailVersions.length }}</el-descriptions-item>
            <el-descriptions-item label="说明" :span="2">{{ detailEntry.description || '—' }}</el-descriptions-item>
          </el-descriptions>
          <div class="mcatalog-section">字段信息</div>
          <el-table :data="detailColumns" stripe size="small" max-height="280" empty-text="无字段">
            <el-table-column prop="entryName" label="英文名" min-width="100" />
            <el-table-column label="中文名" min-width="100">
              <template #default="{ row }">{{ row.keywords || row.entryName }}</template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="140" show-overflow-tooltip />
          </el-table>
          <div style="margin-top:12px">
            <el-button type="primary" @click="openVersions(detailEntry)">查看历史版本</el-button>
            <el-button @click="goCatalog(detailEntry)">发起编目</el-button>
          </div>
        </template>
      </div>
    </el-drawer>

    <!-- 历史版本 -->
    <el-drawer v-model="versionVisible" :title="`历史版本 · ${versionEntry?.entryName || ''}`" size="640px">
      <div v-loading="versionLoading">
        <el-form inline class="portal-inline-form" style="margin-bottom:8px">
          <el-form-item class="portal-form-actions">
            <el-button type="primary" :disabled="versions.length < 2" @click="openCompare">版本对比</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="versions" stripe size="small" empty-text="暂无版本历史">
          <el-table-column label="版本号" width="90">
            <template #default="{ row }">v{{ row.versionNo }}</template>
          </el-table-column>
          <el-table-column prop="createdBy" label="提交人" width="100" />
          <el-table-column label="提交时间" width="160">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="changeSummary" label="发布描述" min-width="160" show-overflow-tooltip />
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button link type="primary" @click="openVersionDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>

    <el-drawer v-model="versionDetailVisible" title="版本详情" size="560px">
      <div v-loading="versionDetailLoading">
        <template v-if="versionDetail?.version">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="版本">v{{ versionDetail.version.versionNo }}</el-descriptions-item>
            <el-descriptions-item label="提交人">{{ versionDetail.version.createdBy || '—' }}</el-descriptions-item>
            <el-descriptions-item label="时间">{{ formatTime(versionDetail.version.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="说明">{{ versionDetail.version.changeSummary || '—' }}</el-descriptions-item>
          </el-descriptions>
          <div class="mcatalog-section">字段快照</div>
          <el-table :data="versionDetail.fields || []" stripe size="small" max-height="320" empty-text="无字段快照">
            <el-table-column prop="nameZh" label="中文名" min-width="100" />
            <el-table-column prop="nameEn" label="英文名" min-width="100" />
            <el-table-column prop="dataType" label="类型" width="90" />
            <el-table-column prop="description" label="描述" min-width="120" show-overflow-tooltip />
          </el-table>
        </template>
      </div>
    </el-drawer>

    <el-drawer v-model="compareVisible" title="版本对比" size="640px">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="基准" class="portal-field-md">
          <el-select v-model="compareForm.leftId" clearable>
            <el-option v-for="v in versions" :key="'l' + v.id" :label="`v${v.versionNo}`" :value="v.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="对比" class="portal-field-md">
          <el-select v-model="compareForm.rightId" clearable>
            <el-option v-for="v in versions" :key="'r' + v.id" :label="`v${v.versionNo}`" :value="v.id" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" :loading="compareLoading" @click="doCompare">对比</el-button>
        </el-form-item>
      </el-form>
      <template v-if="compareData">
        <el-alert
          :type="compareData.sameSnapshot ? 'success' : 'warning'"
          :closable="false"
          style="margin-bottom:12px"
          :title="compareData.sameSnapshot ? '两版本一致' : '两版本存在差异'"
        />
        <div class="mcatalog-section">基本信息差异</div>
        <el-table :data="compareData.basicDiff || []" stripe size="small" empty-text="无差异">
          <el-table-column label="信息项" width="120">
            <template #default="{ row }">{{ fieldLabel(row.field) }}</template>
          </el-table-column>
          <el-table-column prop="left" label="基准" min-width="120" show-overflow-tooltip />
          <el-table-column prop="right" label="对比" min-width="120" show-overflow-tooltip />
        </el-table>
        <div class="mcatalog-section">属性信息差异</div>
        <el-table :data="compareData.attrDiff || []" stripe size="small" empty-text="无差异" max-height="280">
          <el-table-column prop="fieldName" label="字段" width="120" />
          <el-table-column label="属性" width="100">
            <template #default="{ row }">{{ attrLabel(row.attr) }}</template>
          </el-table-column>
          <el-table-column prop="left" label="基准" min-width="100" show-overflow-tooltip />
          <el-table-column prop="right" label="对比" min-width="100" show-overflow-tooltip />
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.mcatalog-kpi {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 12px;
}
.mcatalog-kpi__card {
  border: 1px solid #e8edf5; background: #fff; border-radius: 10px;
  padding: 12px 14px; box-shadow: 0 1px 4px rgba(15, 40, 80, .04);
}
.mcatalog-kpi__card span { display: block; font-size: 12px; color: #606266; }
.mcatalog-kpi__card b { font-size: 24px; font-weight: 700; color: #303133; }
.mcatalog-kpi__card.tone-warn b { color: #ef6c00; }
.mcatalog-kpi__card.tone-info b { color: #1677ff; }
.mcatalog-kpi__card.tone-ok b { color: #2e7d32; }
.mcatalog-section {
  margin: 0 0 8px; font-size: 13px; font-weight: 600;
  padding-left: 8px; border-left: 3px solid #1677ff;
}
.mcatalog-name { font-weight: 600; }
.mcatalog-code { font-size: 12px; color: #909399; }
@media (max-width: 1100px) {
  .mcatalog-kpi { grid-template-columns: repeat(2, 1fr); }
}
</style>
