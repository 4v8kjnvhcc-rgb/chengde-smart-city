<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import HubSideLayout, { type HubNavItem } from '@/components/common/HubSideLayout.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'

/** 侧栏对齐 V3：数据资产区为单入口；其余为资源中心管理六模块 */
const navItems: HubNavItem[] = [
  { key: 'asset', label: '数据资产区' },
  { key: 'partition', label: '分区设计管理' },
  { key: 'storage', label: '数据库存储管理' },
  { key: 'catalog', label: '资产目录管理' },
  { key: 'search', label: '数据库检索查询' },
  { key: 'stats', label: '数据库统计分析' },
  { key: 'monitor', label: '资源监控管理' },
]

interface Library {
  id: number
  libCode: string
  libName: string
  libType: string
  recordCount: number
  status?: string
}
interface Theme {
  id: number
  themeCode: string
  themeName: string
  libraryKind: string
  zoneCode?: string
  managedCount?: number
  ownerOrg?: string
  status: string
}
interface ManagedTable {
  id: number
  themeId: number
  physicalTable: string
  metaEntryCode?: string
  themeName?: string
  libraryKind?: string
  recordCount?: number
  status: string
}
interface Candidate {
  entryCode: string
  entryName: string
  physicalTable: string
  managed: boolean
}
interface Partition {
  id: number
  partitionName: string
  partitionType: string
  tableName?: string
  partitionColumn?: string
  pretestStatus?: string
  pretestMessage?: string
  previewDdl?: string
}
interface Policy {
  id: number
  policyName: string
  policyCode?: string
  actionType: string
  retentionDays: number
  managedTableId?: number
  status?: string
}
interface Artifact {
  id: number
  physicalTable: string
  fileName: string
  rowCount?: number
  byteSize?: number
  sha256?: string
  status: string
  createdAt?: string
}
interface CatalogEntry {
  id: number
  entryCode: string
  entryName: string
  driveTask?: string
  status?: string
}
interface Monitor {
  metricLabel: string
  metricValue: string
  alertLevel: string
}
interface SearchHit {
  libCode: string
  libName: string
  libType: string
  recordCount: number
  managedTableId?: number
  metaEntryCode?: string
  physicalTable?: string
}

const route = useRoute()
const router = useRouter()

/** 旧 query.tab（含 asset.* / mgmt.* / m130～m138）→ 侧栏叶子 */
const tabMap: Record<string, string> = {
  asset: 'asset',
  library: 'asset', m130: 'asset', m131: 'asset', m132: 'asset',
  'asset.libraries': 'asset', 'asset.classify': 'asset', 'asset.modules': 'asset', 'asset.files': 'asset',
  classify: 'asset', modules: 'asset', files: 'asset',
  partition: 'partition', m133: 'partition', 'mgmt.partition': 'partition',
  storage: 'storage', m134: 'storage', 'mgmt.storage': 'storage',
  catalog: 'catalog', m135: 'catalog', 'mgmt.catalog': 'catalog',
  analytics: 'search',
  search: 'search', m136: 'search', 'mgmt.search': 'search',
  stats: 'stats', m137: 'stats', 'mgmt.stats': 'stats',
  monitor: 'monitor', m138: 'monitor', 'mgmt.monitor': 'monitor',
}

const assetTabMap: Record<string, string> = {
  libraries: 'libraries', 'asset.libraries': 'libraries', library: 'libraries',
  classify: 'classify', 'asset.classify': 'classify',
  modules: 'modules', 'asset.modules': 'modules',
  files: 'files', 'asset.files': 'files',
}

const storageTabMap: Record<string, string> = {
  backup: 'backup', archive: 'archive', destroy: 'destroy', policy: 'policy', monitor: 'monitor',
}

const searchTabMap: Record<string, string> = {
  meta: 'meta', query: 'query',
}

const DEFAULT_NAV = 'asset'
const activeNav = ref(DEFAULT_NAV)
const assetTab = ref('libraries')
const storageTab = ref('backup')
const searchTab = ref('meta')
let applyingRoute = false

const libOverview = ref<Record<string, unknown> | null>(null)
const themes = ref<Theme[]>([])
const managedTables = ref<ManagedTable[]>([])
const candidates = ref<Candidate[]>([])
const partOverview = ref<Record<string, unknown> | null>(null)
const policies = ref<Policy[]>([])
const stats = ref<Record<string, unknown> | null>(null)
const monitor = ref<Monitor[]>([])
const catalogEntries = ref<CatalogEntry[]>([])
const searchQ = ref('')
const searchHits = ref<SearchHit[]>([])
const searchDone = ref(false)
const pretestResult = ref<Record<string, unknown> | null>(null)
const verifyResult = ref<Record<string, unknown> | null>(null)
const unsOverview = ref<Record<string, unknown> | null>(null)
const unsDocs = ref<{ id: number; title: string; storageKey?: string; indexStatus?: string; publishStatus?: string }[]>([])
const queryTableId = ref<number | undefined>()
const queryResult = ref<Record<string, unknown> | null>(null)
const lastPolicyRun = ref<Record<string, unknown> | null>(null)

const libForm = reactive({ libName: '', libType: 'BASE' })
const themeForm = reactive({
  themeName: '',
  libraryKind: 'THEME',
  zoneCode: 'ZONE_THEME',
  ownerOrg: '示范单位',
})
const manageForm = reactive({
  themeId: undefined as number | undefined,
  physicalTable: '',
  metaEntryCode: '',
  entryName: '',
})
const partitionForm = reactive({
  partitionName: '',
  partitionType: 'RANGE',
  themeId: undefined as number | undefined,
  tableName: '',
  partitionColumn: '',
  expressionText: '',
})
const catalogForm = reactive({
  managedTableId: undefined as number | undefined,
  entryName: '',
})
const policyForm = reactive({
  policyName: '',
  actionType: 'BACKUP',
  retentionDays: 30,
  managedTableId: undefined as number | undefined,
})

const themesByZone = computed(() => {
  const map = new Map<string, Theme[]>()
  for (const t of themes.value) {
    const zone = t.zoneCode || '未分区'
    if (!map.has(zone)) map.set(zone, [])
    map.get(zone)!.push(t)
  }
  return [...map.entries()].map(([zone, items]) => ({ zone, items }))
})

const filteredPolicies = computed(() => {
  const all = policies.value.length
    ? policies.value
    : ((partOverview.value?.policies as Policy[]) || [])
  if (storageTab.value === 'backup') return all.filter((p) => p.actionType === 'BACKUP')
  if (storageTab.value === 'archive') return all.filter((p) => p.actionType === 'ARCHIVE')
  if (storageTab.value === 'destroy') return all.filter((p) => p.actionType === 'DESTROY')
  return all
})

const artifacts = computed(() => ((partOverview.value?.artifacts as Artifact[]) || []))

const kindLabel = (kind?: string) => {
  if (kind === 'TOPIC') return '专题库'
  if (kind === 'THEME') return '主题库'
  return kind ? statusLabel(kind) : '-'
}

const hitTypeLabel = (t: string) => {
  if (t === 'MANAGED') return '纳管表'
  if (t === 'METADATA') return '元数据'
  return statusLabel(t)
}

function resolveFromRoute() {
  applyingRoute = true
  const q = String(route.query.tab || 'asset').toLowerCase()
  activeNav.value = tabMap[q] || DEFAULT_NAV
  const at = String(route.query.assetTab || route.query.sub || '').toLowerCase()
  if (at && assetTabMap[at]) assetTab.value = assetTabMap[at]
  else if (q.startsWith('asset.') && assetTabMap[q]) assetTab.value = assetTabMap[q]
  const st = String(route.query.storageTab || '').toLowerCase()
  if (st && storageTabMap[st]) storageTab.value = storageTabMap[st]
  const qt = String(route.query.searchTab || '').toLowerCase()
  if (qt && searchTabMap[qt]) searchTab.value = searchTabMap[qt]
  nextTick(() => { applyingRoute = false })
}

function syncQuery() {
  const q: Record<string, string> = {}
  for (const [k, v] of Object.entries(route.query)) {
    if (v == null || k === 'tab' || k === 'assetTab' || k === 'storageTab' || k === 'searchTab' || k === 'sub') continue
    q[k] = Array.isArray(v) ? String(v[0]) : String(v)
  }
  q.tab = activeNav.value
  if (activeNav.value === 'asset') q.assetTab = assetTab.value
  if (activeNav.value === 'storage') q.storageTab = storageTab.value
  if (activeNav.value === 'search') q.searchTab = searchTab.value
  router.replace({ query: q })
}

async function loadLibraryOverview() {
  libOverview.value = (await api.get('/resource-center/platform/libraries/overview')).data
  themes.value = (libOverview.value?.themes as Theme[]) || []
  managedTables.value = (libOverview.value?.managedTables as ManagedTable[]) || []
}

async function loadCandidates() {
  candidates.value = (await api.get('/resource-center/platform/managed-tables/candidates')).data || []
}

async function loadPartitionOverview() {
  partOverview.value = (await api.get('/resource-center/platform/partition/overview')).data
}

async function loadPolicies(actionType?: string) {
  policies.value = (await api.get('/resource-center/platform/policies', {
    params: actionType ? { actionType } : {},
  })).data || []
}

async function loadCatalog() {
  catalogEntries.value = (await api.get('/resource-center/platform/catalog/entries')).data || []
}

async function loadStats() {
  stats.value = (await api.get('/resource-center/platform/statistics')).data
}

async function loadMonitor() {
  monitor.value = (await api.get('/resource-center/platform/monitor')).data || []
}

async function loadManagedTablesOnly() {
  managedTables.value = (await api.get('/resource-center/platform/managed-tables')).data || []
}

async function loadUnsFiles() {
  const [ov, docs] = await Promise.all([
    api.get('/unstructured/platform/overview'),
    api.get('/unstructured/platform/documents'),
  ])
  unsOverview.value = ov.data
  unsDocs.value = docs.data || []
}

async function loadTabData() {
  try {
    const nav = activeNav.value
    if (nav === 'asset') {
      if (assetTab.value === 'libraries' || assetTab.value === 'modules') {
        await loadLibraryOverview()
      } else if (assetTab.value === 'classify') {
        await Promise.all([loadLibraryOverview(), loadCandidates()])
      } else if (assetTab.value === 'files') {
        await loadUnsFiles()
      }
    } else if (nav === 'partition') {
      await Promise.all([loadPartitionOverview(), loadManagedTablesOnly()])
    } else if (nav === 'storage') {
      await Promise.all([loadPartitionOverview(), loadManagedTablesOnly(), loadPolicies()])
    } else if (nav === 'catalog') {
      await Promise.all([loadCatalog(), loadManagedTablesOnly()])
    } else if (nav === 'search') {
      searchDone.value = false
      if (searchTab.value === 'query') await loadManagedTablesOnly()
    } else if (nav === 'stats') {
      await loadStats()
    } else if (nav === 'monitor') {
      await loadMonitor()
    }
  } catch {
    ElMessage.error('加载失败')
  }
}

async function createLib() {
  if (!libForm.libName.trim()) {
    ElMessage.warning('请填写库名称')
    return
  }
  await api.post('/resource-center/platform/libraries', libForm)
  ElMessage.success('库已创建')
  libForm.libName = ''
  await loadLibraryOverview()
}

async function createTheme() {
  if (!themeForm.themeName.trim()) {
    ElMessage.warning('请填写主题/专题库名称')
    return
  }
  await api.post('/resource-center/platform/themes', themeForm)
  ElMessage.success('主题/专题库已创建')
  themeForm.themeName = ''
  await loadLibraryOverview()
}

function pickCandidate(row: Candidate) {
  if (row.managed) {
    ElMessage.warning('该候选表已纳管')
    return
  }
  manageForm.physicalTable = row.physicalTable
  manageForm.metaEntryCode = row.entryCode
  manageForm.entryName = row.entryName
  if (!manageForm.themeId) {
    const topic = themes.value.find((t) => t.libraryKind === 'TOPIC')
    const theme = themes.value.find((t) => t.libraryKind === 'THEME')
    manageForm.themeId = topic?.id || theme?.id
  }
}

async function manageTable() {
  if (!manageForm.themeId) {
    ElMessage.warning('请选择主题/专题库')
    return
  }
  if (!manageForm.physicalTable || !manageForm.metaEntryCode) {
    ElMessage.warning('请从候选列表选用已登记表（含元数据码），禁止空手填写')
    return
  }
  await api.post('/resource-center/platform/managed-tables', {
    themeId: manageForm.themeId,
    physicalTable: manageForm.physicalTable,
    metaEntryCode: manageForm.metaEntryCode,
  })
  ElMessage.success('物理表已纳管')
  manageForm.physicalTable = ''
  manageForm.metaEntryCode = ''
  manageForm.entryName = ''
  await Promise.all([loadLibraryOverview(), loadCandidates()])
}

async function unmanageTable(id: number) {
  try {
    await ElMessageBox.confirm('确认解绑该纳管表？', '解绑纳管', { type: 'warning' })
    await api.delete(`/resource-center/platform/managed-tables/${id}`)
    ElMessage.success('已解绑纳管')
    await loadLibraryOverview()
  } catch { /* cancel */ }
}

async function runBackup(id: number) {
  const res = await api.post(`/resource-center/platform/managed-tables/${id}/backup`, { retentionDays: 30 })
  ElMessage.success(`备份完成：${res.data?.rowCount ?? '-'} 行`)
  if (activeNav.value === 'storage' || activeNav.value === 'partition') {
    await loadPartitionOverview()
  }
}

async function createPartition() {
  if (!partitionForm.partitionName.trim()) {
    ElMessage.warning('请填写策略名称')
    return
  }
  if (!partitionForm.tableName) {
    ElMessage.warning('请从已纳管表中选择目标表')
    return
  }
  const mt = managedTables.value.find((t) => t.physicalTable === partitionForm.tableName)
  await api.post('/resource-center/platform/partitions', {
    ...partitionForm,
    themeId: mt?.themeId || partitionForm.themeId,
  })
  ElMessage.success('分区策略已创建')
  partitionForm.partitionName = ''
  partitionForm.partitionColumn = ''
  await loadPartitionOverview()
}

async function pretestPartition(id: number) {
  const res = await api.post(`/resource-center/platform/partitions/${id}/pretest`)
  pretestResult.value = res.data
  ElMessage.success('分区预检完成（未执行物理 DDL）')
  await loadPartitionOverview()
}

async function createPolicy() {
  if (!policyForm.policyName.trim()) {
    ElMessage.warning('请填写策略名称')
    return
  }
  if ((policyForm.actionType === 'BACKUP' || policyForm.actionType === 'ARCHIVE') && !policyForm.managedTableId) {
    ElMessage.warning('请选择关联纳管表')
    return
  }
  await api.post('/resource-center/platform/policies', { ...policyForm })
  ElMessage.success('策略已创建')
  policyForm.policyName = ''
  await Promise.all([loadPolicies(), loadPartitionOverview()])
}

async function runPolicy(id: number) {
  try {
    const res = await api.post(`/resource-center/platform/policies/${id}/execute`)
    lastPolicyRun.value = res.data
    const st = String(res.data?.status || '')
    if (st === 'LEDGER') {
      ElMessage.warning(String(res.data?.message || '已记台账，未改物理数据'))
    } else {
      ElMessage.success(String(res.data?.message || '策略已执行'))
    }
    await Promise.all([loadPolicies(), loadPartitionOverview()])
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '策略执行失败')
  }
}

async function verifyArtifact(id: number) {
  const res = await api.get(`/resource-center/platform/backups/artifacts/${id}/verify`)
  verifyResult.value = res.data
  ElMessage.success(res.data?.match ? '校验通过' : '校验失败')
}

async function refreshMonitor() {
  const res = await api.post('/resource-center/platform/monitor/refresh')
  monitor.value = res.data?.metrics || []
  ElMessage.success('容量指标已刷新')
}

async function createCatalog() {
  if (!catalogForm.managedTableId) {
    ElMessage.warning('请选择已纳管表创建资产目录，禁止仅填名称')
    return
  }
  const mt = managedTables.value.find((t) => t.id === catalogForm.managedTableId)
  await api.post('/resource-center/platform/catalog/entries', {
    managedTableId: catalogForm.managedTableId,
    entryName: catalogForm.entryName.trim() || mt?.physicalTable,
  })
  ElMessage.success('资产目录已创建')
  catalogForm.entryName = ''
  catalogForm.managedTableId = undefined
  await loadCatalog()
}

async function doSearch() {
  const res = await api.get('/resource-center/platform/search', { params: { q: searchQ.value } })
  searchHits.value = (res.data?.hits as SearchHit[]) || []
  searchDone.value = true
  ElMessage.success(`检索完成，命中 ${searchHits.value.length} 条`)
}

async function doQueryTable() {
  if (!queryTableId.value) {
    ElMessage.warning('请选择纳管表')
    return
  }
  const res = await api.get(`/resource-center/platform/managed-tables/${queryTableId.value}/query`, {
    params: { limit: 100 },
  })
  queryResult.value = res.data
  ElMessage.success(`已查询 ${res.data?.rowCount ?? 0} 行（最多 100）`)
}

function downloadQueryCsv() {
  if (!queryResult.value) {
    ElMessage.warning('请先查询')
    return
  }
  const columns = (queryResult.value.columns as string[]) || []
  const rows = (queryResult.value.rows as Record<string, string>[]) || []
  const esc = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`
  const lines = [columns.map(esc).join(',')]
  for (const row of rows) {
    lines.push(columns.map((c) => esc(row[c])).join(','))
  }
  const blob = new Blob(['\ufeff' + lines.join('\n')], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `${queryResult.value.physicalTable || 'query'}.csv`
  a.click()
  URL.revokeObjectURL(a.href)
}

function onStorageTabChange() {
  if (!applyingRoute) syncQuery()
  if (storageTab.value === 'backup' || storageTab.value === 'archive' || storageTab.value === 'destroy') {
    policyForm.actionType = storageTab.value === 'backup' ? 'BACKUP'
      : storageTab.value === 'archive' ? 'ARCHIVE' : 'DESTROY'
  }
}

watch(activeNav, () => {
  if (!applyingRoute) syncQuery()
  loadTabData()
})
watch(assetTab, () => {
  if (!applyingRoute && activeNav.value === 'asset') {
    syncQuery()
    loadTabData()
  }
})
watch(storageTab, () => {
  if (!applyingRoute && activeNav.value === 'storage') onStorageTabChange()
})
watch(searchTab, () => {
  if (!applyingRoute && activeNav.value === 'search') {
    syncQuery()
    loadTabData()
  }
})
watch(() => route.query.tab, () => { resolveFromRoute() })
onMounted(() => {
  resolveFromRoute()
  loadTabData()
})
</script>

<template>
  <div class="rc-hub-root">
    <HubSideLayout v-model="activeNav" :items="navItems">
      <!-- 数据资产区：四模块页内 Tab -->
      <PageCard v-if="activeNav === 'asset'" title="数据资产区">
        <el-tabs v-model="assetTab">
          <el-tab-pane label="基础库、半结构化和非结构化库管理" name="libraries">
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="库名" class="portal-field-md">
                <el-input v-model="libForm.libName" placeholder="库名称" />
              </el-form-item>
              <el-form-item label="类型" class="portal-field-sm">
                <el-select v-model="libForm.libType">
                  <el-option label="基础库" value="BASE" />
                  <el-option label="半结构化" value="SEMI" />
                  <el-option label="非结构化" value="UNSTRUCT" />
                </el-select>
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="createLib">新增库</el-button>
              </el-form-item>
            </el-form>
            <template v-if="libOverview">
              <el-divider content-position="left">基础库</el-divider>
              <el-table :data="(libOverview.baseLibraries as Library[]) || []" stripe size="small">
                <el-table-column prop="libCode" label="编码" width="140" />
                <el-table-column prop="libName" label="名称" />
                <el-table-column prop="recordCount" label="记录数" width="100" />
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
              </el-table>
              <el-divider content-position="left">半结构化库</el-divider>
              <el-table :data="(libOverview.semiLibraries as Library[]) || []" stripe size="small">
                <el-table-column prop="libCode" label="编码" width="140" />
                <el-table-column prop="libName" label="名称" />
                <el-table-column prop="recordCount" label="记录数" width="100" />
              </el-table>
              <el-divider content-position="left">非结构化库</el-divider>
              <el-table :data="(libOverview.unstructLibraries as Library[]) || []" stripe size="small">
                <el-table-column prop="libCode" label="编码" width="140" />
                <el-table-column prop="libName" label="名称" />
                <el-table-column prop="recordCount" label="记录数" width="100" />
              </el-table>
            </template>
          </el-tab-pane>

          <el-tab-pane label="数据资产管理与分类" name="classify">
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="名称" class="portal-field-md">
                <el-input v-model="themeForm.themeName" placeholder="主题/专题库名称" />
              </el-form-item>
              <el-form-item label="类型" class="portal-field-sm">
                <el-select v-model="themeForm.libraryKind">
                  <el-option label="主题库" value="THEME" />
                  <el-option label="专题库" value="TOPIC" />
                </el-select>
              </el-form-item>
              <el-form-item label="库区" class="portal-field-sm">
                <el-input v-model="themeForm.zoneCode" />
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="createTheme">新增主题库</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="themes" stripe size="small" style="margin-bottom:16px">
              <el-table-column prop="themeCode" label="编码" width="160" />
              <el-table-column prop="themeName" label="名称" />
              <el-table-column label="类型" width="90">
                <template #default="{ row }">{{ kindLabel(row.libraryKind) }}</template>
              </el-table-column>
              <el-table-column prop="zoneCode" label="库区" width="120" />
              <el-table-column prop="managedCount" label="纳管表" width="80" />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
            <el-divider content-position="left">物理表纳管（须选用候选）</el-divider>
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="主题库" class="portal-field-lg">
                <el-select v-model="manageForm.themeId" placeholder="必选主题/专题库">
                  <el-option
                    v-for="t in themes"
                    :key="t.id"
                    :label="`${t.themeName}（${kindLabel(t.libraryKind)}）`"
                    :value="t.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="已选表" class="portal-field-lg">
                <el-input
                  :model-value="manageForm.physicalTable
                    ? `${manageForm.physicalTable} / ${manageForm.metaEntryCode}`
                    : ''"
                  readonly
                  placeholder="请从下方候选选用"
                />
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="manageTable">纳管</el-button>
                <el-button @click="loadCandidates">刷新候选</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="candidates" stripe size="small" style="margin-bottom:12px">
              <el-table-column prop="entryName" label="条目名称" min-width="140" />
              <el-table-column prop="physicalTable" label="产出表" min-width="140" />
              <el-table-column prop="entryCode" label="元数据码" width="200" show-overflow-tooltip />
              <el-table-column label="已纳管" width="80">
                <template #default="{ row }">{{ row.managed ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column label="操作" width="80">
                <template #default="{ row }">
                  <el-button link type="primary" :disabled="row.managed" @click="pickCandidate(row)">选用</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-table :data="managedTables" stripe size="small">
              <el-table-column prop="physicalTable" label="物理表" />
              <el-table-column prop="metaEntryCode" label="元数据码" width="200" show-overflow-tooltip />
              <el-table-column prop="themeName" label="所属库" />
              <el-table-column prop="recordCount" label="行数" width="90" />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button link @click="unmanageTable(row.id)">解绑</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="数据资产中心模块化管理" name="modules">
            <el-empty v-if="!themesByZone.length" description="暂无主题/专题库，请先在「数据资产管理与分类」创建" />
            <div v-for="group in themesByZone" :key="group.zone" style="margin-bottom:16px">
              <el-divider content-position="left">库区 · {{ group.zone }}</el-divider>
              <el-table :data="group.items" stripe size="small">
                <el-table-column prop="themeCode" label="编码" width="160" />
                <el-table-column prop="themeName" label="名称" />
                <el-table-column label="类型" width="90">
                  <template #default="{ row }">{{ kindLabel(row.libraryKind) }}</template>
                </el-table-column>
                <el-table-column prop="ownerOrg" label="责任单位" width="140" />
                <el-table-column prop="managedCount" label="纳管表" width="80" />
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>

          <el-tab-pane label="文件目录库、文件索引库管理" name="files">
            <el-descriptions v-if="unsOverview" :column="4" border size="small" style="margin-bottom:12px">
              <el-descriptions-item label="文档数">{{ unsOverview.documents }}</el-descriptions-item>
              <el-descriptions-item label="已发布">{{ unsOverview.published }}</el-descriptions-item>
              <el-descriptions-item label="已索引">{{ unsOverview.indexed }}</el-descriptions-item>
              <el-descriptions-item label="存储/检索">
                {{ unsOverview.seaweedHealthy ? '存储正常' : '存储异常' }} /
                {{ unsOverview.esHealthy ? '检索正常' : '检索降级' }}
              </el-descriptions-item>
            </el-descriptions>
            <el-table :data="unsDocs" stripe size="small">
              <el-table-column prop="title" label="文件标题" min-width="160" />
              <el-table-column prop="storageKey" label="目录存储键" min-width="200" show-overflow-tooltip />
              <el-table-column label="发布" width="100">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.publishStatus)" size="small">
                    {{ statusLabel(row.publishStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="索引" width="100">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.indexStatus)" size="small">
                    {{ statusLabel(row.indexStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </PageCard>

      <!-- 分区设计 -->
      <PageCard v-else-if="activeNav === 'partition'" title="分区设计管理">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="策略名" class="portal-field-md">
            <el-input v-model="partitionForm.partitionName" />
          </el-form-item>
          <el-form-item label="类型" class="portal-field-sm">
            <el-select v-model="partitionForm.partitionType">
              <el-option :label="statusLabel('RANGE')" value="RANGE" />
              <el-option :label="statusLabel('HASH')" value="HASH" />
              <el-option :label="statusLabel('LIST')" value="LIST" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标表" class="portal-field-lg">
            <el-select v-model="partitionForm.tableName" placeholder="选择已纳管表" filterable>
              <el-option
                v-for="t in managedTables"
                :key="t.id"
                :label="t.physicalTable"
                :value="t.physicalTable"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="分区列" class="portal-field-md">
            <el-input v-model="partitionForm.partitionColumn" placeholder="如 created_at" />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="createPartition">新增策略</el-button>
          </el-form-item>
        </el-form>
        <el-table v-if="partOverview" :data="(partOverview.partitions as Partition[]) || []" stripe size="small">
          <el-table-column prop="partitionName" label="分区策略" />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ statusLabel(row.partitionType) }}</template>
          </el-table-column>
          <el-table-column prop="tableName" label="目标表" width="180" />
          <el-table-column label="预检" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.pretestStatus)" size="small">
                {{ statusLabel(row.pretestStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button link type="primary" @click="pretestPartition(row.id)">预检</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-alert
          v-if="pretestResult"
          type="info"
          :closable="false"
          style="margin-top:12px"
          :title="String(pretestResult.pretestMessage || '预检完成')"
        >
          <pre style="white-space:pre-wrap;margin:8px 0 0">{{ pretestResult.previewDdl }}</pre>
        </el-alert>
      </PageCard>

      <!-- 数据库存储管理：五子能力 Tab -->
      <PageCard v-else-if="activeNav === 'storage'" title="数据库存储管理">
        <el-tabs v-model="storageTab" @tab-change="onStorageTabChange">
          <el-tab-pane label="数据备份" name="backup">
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="策略名" class="portal-field-md">
                <el-input v-model="policyForm.policyName" placeholder="备份策略名称" />
              </el-form-item>
              <el-form-item label="纳管表" class="portal-field-lg">
                <el-select v-model="policyForm.managedTableId" placeholder="选择备份目标表" filterable>
                  <el-option
                    v-for="t in managedTables"
                    :key="t.id"
                    :label="t.physicalTable"
                    :value="t.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="保留天" class="portal-field-xs">
                <el-input-number v-model="policyForm.retentionDays" :min="1" :max="3650" controls-position="right" />
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="policyForm.actionType = 'BACKUP'; createPolicy()">创建备份策略</el-button>
              </el-form-item>
            </el-form>
            <el-divider content-position="left">备份策略</el-divider>
            <el-table :data="filteredPolicies" stripe size="small">
              <el-table-column prop="policyName" label="策略" />
              <el-table-column prop="retentionDays" label="保留天" width="80" />
              <el-table-column label="操作" width="160">
                <template #default="{ row }">
                  <el-button link type="primary" @click="runPolicy(row.id)">执行备份</el-button>
                  <el-button
                    v-if="row.managedTableId"
                    link
                    type="primary"
                    @click="runBackup(row.managedTableId)"
                  >立即备份</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-divider content-position="left">备份产物</el-divider>
            <el-table :data="artifacts" stripe size="small">
              <el-table-column prop="physicalTable" label="表" width="180" />
              <el-table-column prop="fileName" label="文件" min-width="160" show-overflow-tooltip />
              <el-table-column prop="rowCount" label="行数" width="80" />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80">
                <template #default="{ row }">
                  <el-button link type="primary" @click="verifyArtifact(row.id)">校验</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="数据归档" name="archive">
            <el-alert
              type="info"
              :closable="false"
              title="归档执行仅记台账，不移动物理数据；状态为「台账」而非假成功。"
              style="margin-bottom:12px"
            />
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="策略名" class="portal-field-md">
                <el-input v-model="policyForm.policyName" placeholder="归档策略名称" />
              </el-form-item>
              <el-form-item label="纳管表" class="portal-field-lg">
                <el-select v-model="policyForm.managedTableId" filterable>
                  <el-option v-for="t in managedTables" :key="t.id" :label="t.physicalTable" :value="t.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="保留天" class="portal-field-xs">
                <el-input-number v-model="policyForm.retentionDays" :min="1" :max="3650" controls-position="right" />
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="policyForm.actionType = 'ARCHIVE'; createPolicy()">创建归档策略</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="filteredPolicies" stripe size="small">
              <el-table-column prop="policyName" label="策略" />
              <el-table-column prop="retentionDays" label="保留天" width="80" />
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button link type="primary" @click="runPolicy(row.id)">执行归档</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="数据销毁" name="destroy">
            <el-alert
              type="warning"
              :closable="false"
              title="销毁策略可配置，但禁止自动物理删除；执行将被拒绝。"
              style="margin-bottom:12px"
            />
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="策略名" class="portal-field-md">
                <el-input v-model="policyForm.policyName" placeholder="销毁策略名称" />
              </el-form-item>
              <el-form-item label="纳管表" class="portal-field-lg">
                <el-select v-model="policyForm.managedTableId" filterable>
                  <el-option v-for="t in managedTables" :key="t.id" :label="t.physicalTable" :value="t.id" />
                </el-select>
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="danger" @click="policyForm.actionType = 'DESTROY'; createPolicy()">创建销毁策略</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="filteredPolicies" stripe size="small">
              <el-table-column prop="policyName" label="策略" />
              <el-table-column label="动作" width="100">
                <template #default="{ row }">{{ statusLabel(row.actionType) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button link type="danger" @click="runPolicy(row.id)">尝试执行</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="执行策略管理" name="policy">
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="策略名" class="portal-field-md">
                <el-input v-model="policyForm.policyName" />
              </el-form-item>
              <el-form-item label="动作" class="portal-field-sm">
                <el-select v-model="policyForm.actionType">
                  <el-option :label="statusLabel('BACKUP')" value="BACKUP" />
                  <el-option :label="statusLabel('ARCHIVE')" value="ARCHIVE" />
                  <el-option :label="statusLabel('DESTROY')" value="DESTROY" />
                </el-select>
              </el-form-item>
              <el-form-item label="纳管表" class="portal-field-lg">
                <el-select v-model="policyForm.managedTableId" filterable clearable>
                  <el-option v-for="t in managedTables" :key="t.id" :label="t.physicalTable" :value="t.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="保留天" class="portal-field-xs">
                <el-input-number v-model="policyForm.retentionDays" :min="1" :max="3650" controls-position="right" />
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="createPolicy">创建策略</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="filteredPolicies" stripe size="small">
              <el-table-column prop="policyCode" label="编码" width="160" />
              <el-table-column prop="policyName" label="策略" />
              <el-table-column label="动作" width="100">
                <template #default="{ row }">{{ statusLabel(row.actionType) }}</template>
              </el-table-column>
              <el-table-column prop="retentionDays" label="保留天" width="80" />
              <el-table-column prop="managedTableId" label="纳管表ID" width="100" />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="执行策略监控" name="monitor">
            <el-alert
              v-if="lastPolicyRun"
              :type="lastPolicyRun.status === 'LEDGER' ? 'warning' : 'success'"
              :closable="true"
              style="margin-bottom:12px"
              :title="`最近执行：${statusLabel(String(lastPolicyRun.actionType || ''))} · ${statusLabel(String(lastPolicyRun.status || ''))}`"
              @close="lastPolicyRun = null"
            >
              {{ lastPolicyRun.message || `产物行数 ${lastPolicyRun.rowCount ?? '-'}` }}
            </el-alert>
            <el-divider content-position="left">全部策略</el-divider>
            <el-table :data="policies.length ? policies : ((partOverview?.policies as Policy[]) || [])" stripe size="small">
              <el-table-column prop="policyName" label="策略" />
              <el-table-column label="动作" width="100">
                <template #default="{ row }">{{ statusLabel(row.actionType) }}</template>
              </el-table-column>
              <el-table-column prop="retentionDays" label="保留天" width="80" />
              <el-table-column label="操作" width="80">
                <template #default="{ row }">
                  <el-button link type="primary" @click="runPolicy(row.id)">执行</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-divider content-position="left">备份/归档产物与校验</el-divider>
            <el-table :data="artifacts" stripe size="small">
              <el-table-column prop="physicalTable" label="表" width="180" />
              <el-table-column prop="fileName" label="文件" min-width="160" show-overflow-tooltip />
              <el-table-column prop="rowCount" label="行数" width="80" />
              <el-table-column prop="createdAt" label="时间" width="170" />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80">
                <template #default="{ row }">
                  <el-button link type="primary" @click="verifyArtifact(row.id)">校验</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-alert
              v-if="verifyResult"
              :type="verifyResult.match ? 'success' : 'error'"
              :closable="false"
              style="margin-top:12px"
              :title="verifyResult.match ? 'SHA-256 校验通过' : 'SHA-256 校验失败'"
            />
          </el-tab-pane>
        </el-tabs>
      </PageCard>

      <!-- 资产目录 -->
      <PageCard v-else-if="activeNav === 'catalog'" title="资产目录管理">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="纳管表" class="portal-field-xl">
            <el-select v-model="catalogForm.managedTableId" placeholder="选择已纳管表" filterable>
              <el-option
                v-for="t in managedTables"
                :key="t.id"
                :label="`${t.physicalTable}${t.themeName ? ' · ' + t.themeName : ''}`"
                :value="t.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="目录名" class="portal-field-md">
            <el-input v-model="catalogForm.entryName" placeholder="可选，默认用表名" />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="createCatalog">编目</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="catalogEntries" stripe size="small">
          <el-table-column prop="entryCode" label="编码" width="160" />
          <el-table-column prop="entryName" label="名称" />
          <el-table-column prop="driveTask" label="驱动任务" min-width="160" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.status" :type="statusTagType(row.status)" size="small">
                {{ statusLabel(row.status) }}
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
      </PageCard>

      <!-- 数据库检索查询：两子模块 Tab -->
      <PageCard v-else-if="activeNav === 'search'" title="数据库检索查询">
        <el-tabs v-model="searchTab">
          <el-tab-pane label="数据搜索与元数据检索" name="meta">
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="关键词" class="portal-field-xl">
                <el-input v-model="searchQ" placeholder="库名 / 纳管表 / 元数据码或名称" @keyup.enter="doSearch" />
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="doSearch">检索</el-button>
              </el-form-item>
            </el-form>
            <el-table v-if="searchDone" :data="searchHits" stripe size="small">
              <el-table-column prop="libCode" label="编码/表名" width="200" show-overflow-tooltip />
              <el-table-column prop="libName" label="名称" />
              <el-table-column label="类型" width="120">
                <template #default="{ row }">{{ hitTypeLabel(row.libType) }}</template>
              </el-table-column>
              <el-table-column prop="physicalTable" label="物理表" width="160" show-overflow-tooltip />
              <el-table-column prop="recordCount" label="记录数" width="100" />
            </el-table>
            <el-empty v-else description="输入关键词后点击检索" />
          </el-tab-pane>
          <el-tab-pane label="数据查询与下载" name="query">
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="纳管表" class="portal-field-xl">
                <el-select v-model="queryTableId" placeholder="选择已纳管表" filterable>
                  <el-option
                    v-for="t in managedTables"
                    :key="t.id"
                    :label="`${t.physicalTable}${t.metaEntryCode ? ' · ' + t.metaEntryCode : ''}`"
                    :value="t.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="doQueryTable">查询</el-button>
                <el-button :disabled="!queryResult" @click="downloadQueryCsv">下载 CSV</el-button>
              </el-form-item>
            </el-form>
            <template v-if="queryResult">
              <el-alert
                type="info"
                :closable="false"
                style="margin-bottom:12px"
                :title="`表 ${queryResult.physicalTable} · 返回 ${queryResult.rowCount} 行（上限 ${queryResult.limit}）`"
              />
              <el-table :data="(queryResult.rows as Record<string, string>[]) || []" stripe size="small" max-height="420">
                <el-table-column
                  v-for="col in ((queryResult.columns as string[]) || [])"
                  :key="col"
                  :prop="col"
                  :label="col"
                  min-width="120"
                  show-overflow-tooltip
                />
              </el-table>
            </template>
            <el-empty v-else description="选择纳管表后查询预览，并可下载 CSV" />
          </el-tab-pane>
        </el-tabs>
      </PageCard>

      <!-- 统计 -->
      <PageCard v-else-if="activeNav === 'stats'" title="数据库统计分析">
        <el-descriptions v-if="stats" :column="2" border size="small">
          <el-descriptions-item label="总记录数">{{ stats.totalRecords }}</el-descriptions-item>
          <el-descriptions-item label="库数量">{{ stats.libraryCount }}</el-descriptions-item>
          <el-descriptions-item label="主题/专题库数">{{ stats.themeCount }}</el-descriptions-item>
          <el-descriptions-item label="纳管表数">{{ stats.managedTableCount }}</el-descriptions-item>
        </el-descriptions>
        <el-empty v-else description="暂无统计数据" />
        <template v-if="stats">
          <el-divider content-position="left">记录量靠前的库</el-divider>
          <el-table :data="(stats.topLibraries as Library[]) || []" stripe size="small">
            <el-table-column prop="libCode" label="编码" width="140" />
            <el-table-column prop="libName" label="名称" />
            <el-table-column label="类型" width="120">
              <template #default="{ row }">
                {{ row.libType === 'BASE' ? '基础库' : statusLabel(row.libType) }}
              </template>
            </el-table-column>
            <el-table-column prop="recordCount" label="记录数" width="100" />
          </el-table>
        </template>
      </PageCard>

      <!-- 监控 -->
      <PageCard v-else-if="activeNav === 'monitor'" title="资源监控管理">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="refreshMonitor">刷新真实容量</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="monitor" stripe size="small">
          <el-table-column prop="metricLabel" label="指标" />
          <el-table-column prop="metricValue" label="值" />
          <el-table-column label="级别" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.alertLevel)" size="small">
                {{ statusLabel(row.alertLevel) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </PageCard>
    </HubSideLayout>
  </div>
</template>

<style scoped>
.rc-hub-root {
  height: calc(100vh - var(--portal-header-height) - 40px);
  min-height: 0;
}
</style>
