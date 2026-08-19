<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api/http'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { formatDateTime } from '@/utils/datetime'
import { statusLabel } from '@/utils/status-label'

const searchTab = defineModel<string>('searchTab', { default: 'fulltext' })

interface ManagedTable {
  id: number
  physicalTable: string
  metaEntryCode?: string
}

interface FulltextHit {
  managedTableId?: number
  physicalTable?: string
  metaEntryCode?: string
  matchedColumns?: string[]
  summary?: string
  row?: Record<string, unknown>
}

interface MetadataHit {
  managedTableId?: number
  entryCode?: string
  entryName?: string
  physicalTable?: string
  businessDomain?: string
  dataLayer?: string
  tags?: string
  matchedDataItems?: string[]
  managed?: boolean
}

interface SchemeRow {
  id: string
  name: string
  keyword: string
  scope: string
  extra: string
  remark: string
  status: string
  updatedAt: string
}

const STORAGE_PREFIX = 'rc-db-search-schemes-v1'

const managedTables = ref<ManagedTable[]>([])

const fulltextQ = ref('')
const fulltextScope = ref('')
const fulltextField = ref('')
const fulltextHits = ref<FulltextHit[]>([])
const fulltextDone = ref(false)
const fulltextHint = ref('')
const fulltextDetail = ref<FulltextHit | null>(null)
const fulltextDetailVisible = ref(false)

const metaQ = ref('')
const metaTag = ref('')
const metaDomain = ref('')
const metaDataItem = ref('')
const metaHits = ref<MetadataHit[]>([])
const metaDone = ref(false)
const metaHint = ref('')

const queryTableId = ref<number | undefined>()
const queryKeyword = ref('')
const queryColumn = ref('')
const queryResult = ref<Record<string, unknown> | null>(null)

const schemeKeyword = reactive({ fulltext: '', meta: '', query: '' })
const schemeDialogVisible = ref(false)
const schemeTab = ref<'fulltext' | 'meta' | 'query'>('fulltext')
const editingSchemeId = ref<string | null>(null)
const schemeForm = reactive({
  name: '',
  keyword: '',
  scope: '',
  extra: '',
  remark: '',
  status: 'ACTIVE',
})

const schemes = reactive({
  fulltext: [] as SchemeRow[],
  meta: [] as SchemeRow[],
  query: [] as SchemeRow[],
})

function nowStr() {
  return formatDateTime(new Date())
}

function seedSchemes(kind: 'fulltext' | 'meta' | 'query'): SchemeRow[] {
  const t = nowStr()
  if (kind === 'fulltext') {
    return [
      { id: 'ft-1', name: '自然人身份核验', keyword: '身份证号', scope: '人口主题库', extra: '证件号/手机号', remark: '业务核验常用方案', status: 'ACTIVE', updatedAt: t },
      { id: 'ft-2', name: '企业法人检索', keyword: '统一社会信用代码', scope: '法人主题库', extra: '企业名称', remark: '市场主体核验', status: 'ACTIVE', updatedAt: t },
      { id: 'ft-3', name: '联系方式检索', keyword: '手机号', scope: '全库', extra: '联系电话', remark: '应急联络', status: 'ACTIVE', updatedAt: t },
    ]
  }
  if (kind === 'meta') {
    return [
      { id: 'md-1', name: '人口登记元数据', keyword: '人口', scope: '人口', extra: '户籍/常住', remark: '目录编目前核对', status: 'ACTIVE', updatedAt: t },
      { id: 'md-2', name: '法人登记元数据', keyword: '企业', scope: '法人', extra: '统一代码', remark: '法人库对标', status: 'ACTIVE', updatedAt: t },
      { id: 'md-3', name: 'ODS 贴源检索', keyword: 'ods_', scope: 'ODS', extra: '贴源表', remark: '治理前筛查', status: 'ACTIVE', updatedAt: t },
    ]
  }
  return [
    { id: 'qy-1', name: '企业基础表预览', keyword: '', scope: '', extra: 'ent_name', remark: '抽样核对', status: 'ACTIVE', updatedAt: t },
    { id: 'qy-2', name: '人口基础表下载', keyword: '承德', scope: '', extra: 'id_card', remark: '业务导出', status: 'ACTIVE', updatedAt: t },
    { id: 'qy-3', name: '专题指标底表查询', keyword: '', scope: '', extra: '', remark: '分析准备', status: 'ACTIVE', updatedAt: t },
  ]
}

function loadSchemes(kind: 'fulltext' | 'meta' | 'query') {
  const key = `${STORAGE_PREFIX}:${kind}`
  try {
    const raw = localStorage.getItem(key)
    if (raw) {
      const parsed = JSON.parse(raw) as SchemeRow[]
      if (Array.isArray(parsed) && parsed.length) {
        schemes[kind] = parsed
        return
      }
    }
  } catch {
    /* ignore */
  }
  schemes[kind] = seedSchemes(kind)
  localStorage.setItem(key, JSON.stringify(schemes[kind]))
}

function persistSchemes(kind: 'fulltext' | 'meta' | 'query') {
  localStorage.setItem(`${STORAGE_PREFIX}:${kind}`, JSON.stringify(schemes[kind]))
}

const filteredFulltextSchemes = computed(() => {
  const kw = schemeKeyword.fulltext.trim().toLowerCase()
  if (!kw) return schemes.fulltext
  return schemes.fulltext.filter((r) =>
    [r.name, r.keyword, r.scope, r.extra, r.remark].some((x) => String(x || '').toLowerCase().includes(kw)),
  )
})
const filteredMetaSchemes = computed(() => {
  const kw = schemeKeyword.meta.trim().toLowerCase()
  if (!kw) return schemes.meta
  return schemes.meta.filter((r) =>
    [r.name, r.keyword, r.scope, r.extra, r.remark].some((x) => String(x || '').toLowerCase().includes(kw)),
  )
})
const filteredQuerySchemes = computed(() => {
  const kw = schemeKeyword.query.trim().toLowerCase()
  if (!kw) return schemes.query
  return schemes.query.filter((r) =>
    [r.name, r.keyword, r.scope, r.extra, r.remark].some((x) => String(x || '').toLowerCase().includes(kw)),
  )
})

const {
  page: ftPage,
  pageSize: ftPageSize,
  paged: ftPaged,
  total: ftTotal,
  resetPage: resetFtPage,
} = useClientPager(filteredFulltextSchemes)
const {
  page: metaPage,
  pageSize: metaPageSize,
  paged: metaPaged,
  total: metaTotal,
  resetPage: resetMetaPage,
} = useClientPager(filteredMetaSchemes)
const {
  page: queryPage,
  pageSize: queryPageSize,
  paged: queryPaged,
  total: queryTotal,
  resetPage: resetQueryPage,
} = useClientPager(filteredQuerySchemes)

async function loadManagedTablesOnly() {
  const res = await api.get('/resource-center/platform/managed-tables')
  managedTables.value = (res.data as ManagedTable[]) || []
}

async function doFulltextSearch() {
  if (!fulltextQ.value.trim()) {
    ElMessage.warning('请输入关键词（如姓名、身份证号码、手机号）')
    return
  }
  const res = await api.get('/resource-center/platform/search/fulltext', {
    params: {
      q: fulltextQ.value.trim(),
      scope: fulltextScope.value.trim() || undefined,
      field: fulltextField.value.trim() || undefined,
    },
  })
  fulltextHits.value = (res.data?.hits as FulltextHit[]) || []
  fulltextDone.value = true
  fulltextHint.value = String(res.data?.hint || '')
  ElMessage.success(`全文检索完成，命中 ${fulltextHits.value.length} 条业务数据`)
}

async function doMetadataSearch() {
  if (!metaQ.value.trim() && !metaTag.value.trim() && !metaDomain.value.trim() && !metaDataItem.value.trim()) {
    ElMessage.warning('请至少填写关键词、标签、业务分类或数据项之一')
    return
  }
  const res = await api.get('/resource-center/platform/search/metadata', {
    params: {
      q: metaQ.value.trim() || undefined,
      tag: metaTag.value.trim() || undefined,
      domain: metaDomain.value.trim() || undefined,
      dataItem: metaDataItem.value.trim() || undefined,
    },
  })
  metaHits.value = (res.data?.hits as MetadataHit[]) || []
  metaDone.value = true
  metaHint.value = String(res.data?.hint || '')
  ElMessage.success(`元数据检索完成，命中 ${metaHits.value.length} 条`)
}

function openFulltextDetail(row: FulltextHit) {
  fulltextDetail.value = row
  fulltextDetailVisible.value = true
}

async function lockManagedTableAndQuery(managedTableId?: number | null, presetKeyword?: string) {
  if (!managedTableId) {
    ElMessage.warning('该元数据尚未纳管，请先在「数据资产区」纳管后再查询')
    return
  }
  searchTab.value = 'query'
  await loadManagedTablesOnly()
  queryTableId.value = managedTableId
  queryKeyword.value = presetKeyword?.trim() || ''
  queryColumn.value = ''
  queryResult.value = null
  ElMessage.success('已锁定物理表，可设置查询条件后浏览或下载')
}

async function doQueryTable() {
  if (!queryTableId.value) {
    ElMessage.warning('请选择纳管表')
    return
  }
  const res = await api.get(`/resource-center/platform/managed-tables/${queryTableId.value}/query`, {
    params: {
      limit: 100,
      keyword: queryKeyword.value.trim() || undefined,
      column: queryColumn.value.trim() || undefined,
    },
  })
  queryResult.value = res.data || null
  ElMessage.success(`查询完成，返回 ${Number(queryResult.value?.rowCount || 0)} 行`)
}

function downloadQueryCsv() {
  if (!queryResult.value) return
  const cols = (queryResult.value.columns as string[]) || []
  const rows = (queryResult.value.rows as Record<string, string>[]) || []
  const lines = [cols.join(',')]
  for (const row of rows) {
    lines.push(cols.map((c) => JSON.stringify(row[c] ?? '')).join(','))
  }
  const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${queryResult.value.physicalTable || 'query'}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('已下载 CSV')
}

function openCreateScheme(kind: 'fulltext' | 'meta' | 'query') {
  schemeTab.value = kind
  editingSchemeId.value = null
  schemeForm.name = ''
  schemeForm.keyword = kind === 'fulltext' ? fulltextQ.value : kind === 'meta' ? metaQ.value : queryKeyword.value
  schemeForm.scope = kind === 'fulltext' ? fulltextScope.value : kind === 'meta' ? metaDomain.value : String(queryTableId.value || '')
  schemeForm.extra = kind === 'fulltext' ? fulltextField.value : kind === 'meta' ? metaDataItem.value : queryColumn.value
  schemeForm.remark = ''
  schemeForm.status = 'ACTIVE'
  schemeDialogVisible.value = true
}

function openEditScheme(kind: 'fulltext' | 'meta' | 'query', row: SchemeRow) {
  schemeTab.value = kind
  editingSchemeId.value = row.id
  schemeForm.name = row.name
  schemeForm.keyword = row.keyword
  schemeForm.scope = row.scope
  schemeForm.extra = row.extra
  schemeForm.remark = row.remark
  schemeForm.status = row.status || 'ACTIVE'
  schemeDialogVisible.value = true
}

function saveScheme() {
  if (!schemeForm.name.trim()) {
    ElMessage.warning('请填写方案名称')
    return
  }
  const kind = schemeTab.value
  const list = schemes[kind]
  if (editingSchemeId.value) {
    const hit = list.find((x) => x.id === editingSchemeId.value)
    if (hit) {
      hit.name = schemeForm.name.trim()
      hit.keyword = schemeForm.keyword.trim()
      hit.scope = schemeForm.scope.trim()
      hit.extra = schemeForm.extra.trim()
      hit.remark = schemeForm.remark.trim()
      hit.status = schemeForm.status
      hit.updatedAt = nowStr()
    }
    ElMessage.success('方案已更新')
  } else {
    list.unshift({
      id: `${kind}-${Date.now()}`,
      name: schemeForm.name.trim(),
      keyword: schemeForm.keyword.trim(),
      scope: schemeForm.scope.trim(),
      extra: schemeForm.extra.trim(),
      remark: schemeForm.remark.trim(),
      status: schemeForm.status,
      updatedAt: nowStr(),
    })
    ElMessage.success('方案已创建')
  }
  persistSchemes(kind)
  schemeDialogVisible.value = false
}

async function deleteScheme(kind: 'fulltext' | 'meta' | 'query', row: SchemeRow) {
  try {
    await ElMessageBox.confirm(`确认删除方案「${row.name}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  schemes[kind] = schemes[kind].filter((x) => x.id !== row.id)
  persistSchemes(kind)
  ElMessage.success('已删除')
}

function applyScheme(kind: 'fulltext' | 'meta' | 'query', row: SchemeRow) {
  if (kind === 'fulltext') {
    fulltextQ.value = row.keyword
    fulltextScope.value = row.scope
    fulltextField.value = row.extra
    void doFulltextSearch()
  } else if (kind === 'meta') {
    metaQ.value = row.keyword
    metaDomain.value = row.scope
    metaDataItem.value = row.extra
    metaTag.value = ''
    void doMetadataSearch()
  } else {
    const tid = Number(row.scope)
    if (Number.isFinite(tid) && tid > 0) queryTableId.value = tid
    queryKeyword.value = row.keyword
    queryColumn.value = row.extra
    void doQueryTable()
  }
}

watch(searchTab, (v) => {
  if (v === 'query' && !managedTables.value.length) void loadManagedTablesOnly()
})

onMounted(() => {
  loadSchemes('fulltext')
  loadSchemes('meta')
  loadSchemes('query')
  if (searchTab.value === 'query') void loadManagedTablesOnly()
})
</script>

<template>
  <el-tabs v-model="searchTab">
    <el-tab-pane label="数据全文检索" name="fulltext">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="关键词" class="portal-field-lg">
          <el-input v-model="fulltextQ" placeholder="姓名 / 身份证号 / 手机号等" clearable @keyup.enter="doFulltextSearch" />
        </el-form-item>
        <el-form-item label="业务范围" class="portal-field-md">
          <el-input v-model="fulltextScope" placeholder="主题库/专题" clearable @keyup.enter="doFulltextSearch" />
        </el-form-item>
        <el-form-item label="命中字段" class="portal-field-md">
          <el-input v-model="fulltextField" placeholder="字段偏好" clearable @keyup.enter="doFulltextSearch" />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="doFulltextSearch">全文检索</el-button>
          <el-button @click="openCreateScheme('fulltext')">新建方案</el-button>
        </el-form-item>
      </el-form>

      <div class="scheme-title">检索方案</div>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="方案搜索" class="portal-field-lg">
          <el-input v-model="schemeKeyword.fulltext" clearable placeholder="名称/关键词/范围" @change="resetFtPage" />
        </el-form-item>
      </el-form>
      <el-table :data="ftPaged" stripe border size="small">
        <el-table-column prop="name" label="方案名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="keyword" label="关键词" width="120" show-overflow-tooltip />
        <el-table-column prop="scope" label="业务范围" width="120" show-overflow-tooltip />
        <el-table-column prop="extra" label="命中字段" width="120" show-overflow-tooltip />
        <el-table-column prop="remark" label="说明" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="applyScheme('fulltext', row)">执行</el-button>
            <el-button link type="primary" @click="openEditScheme('fulltext', row)">编辑</el-button>
            <el-button link type="danger" @click="deleteScheme('fulltext', row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <PortalPagination v-model:page="ftPage" v-model:page-size="ftPageSize" :total="ftTotal" />

      <el-alert
        v-if="fulltextDone && fulltextHint"
        type="info"
        :closable="false"
        style="margin:12px 0"
        :title="fulltextHint"
      />
      <el-table v-if="fulltextDone" :data="fulltextHits" stripe size="small" style="margin-top:8px">
        <el-table-column prop="physicalTable" label="物理表" width="160" show-overflow-tooltip />
        <el-table-column prop="metaEntryCode" label="元数据码" width="140" show-overflow-tooltip />
        <el-table-column label="命中字段" width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ (row.matchedColumns || []).join('、') || '-' }}</template>
        </el-table-column>
        <el-table-column prop="summary" label="命中摘要" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openFulltextDetail(row)">详情</el-button>
            <el-button link type="primary" @click="lockManagedTableAndQuery(row.managedTableId, fulltextQ)">
              锁定并查询
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="维护检索方案或输入关键词后执行全文检索" />
      <el-drawer v-model="fulltextDetailVisible" title="命中详情" size="480px">
        <template v-if="fulltextDetail">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="物理表">{{ fulltextDetail.physicalTable }}</el-descriptions-item>
            <el-descriptions-item label="元数据码">{{ fulltextDetail.metaEntryCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="命中字段">
              {{ (fulltextDetail.matchedColumns || []).join('、') || '-' }}
            </el-descriptions-item>
          </el-descriptions>
          <el-divider content-position="left">行数据</el-divider>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item
              v-for="(val, key) in (fulltextDetail.row || {})"
              :key="String(key)"
              :label="String(key)"
            >
              {{ val ?? '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </template>
      </el-drawer>
    </el-tab-pane>

    <el-tab-pane label="元数据检索" name="meta">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="关键词" class="portal-field-md">
          <el-input v-model="metaQ" placeholder="编码/名称/表名" clearable @keyup.enter="doMetadataSearch" />
        </el-form-item>
        <el-form-item label="标签" class="portal-field-sm">
          <el-input v-model="metaTag" placeholder="标签" clearable @keyup.enter="doMetadataSearch" />
        </el-form-item>
        <el-form-item label="业务分类" class="portal-field-md">
          <el-input v-model="metaDomain" placeholder="域/分层/类型" clearable @keyup.enter="doMetadataSearch" />
        </el-form-item>
        <el-form-item label="数据项" class="portal-field-md">
          <el-input v-model="metaDataItem" placeholder="字段名/数据项" clearable @keyup.enter="doMetadataSearch" />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="doMetadataSearch">元数据检索</el-button>
          <el-button @click="openCreateScheme('meta')">新建方案</el-button>
        </el-form-item>
      </el-form>

      <div class="scheme-title">检索方案</div>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="方案搜索" class="portal-field-lg">
          <el-input v-model="schemeKeyword.meta" clearable placeholder="名称/关键词/分类" @change="resetMetaPage" />
        </el-form-item>
      </el-form>
      <el-table :data="metaPaged" stripe border size="small">
        <el-table-column prop="name" label="方案名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="keyword" label="关键词" width="120" show-overflow-tooltip />
        <el-table-column prop="scope" label="业务分类" width="120" show-overflow-tooltip />
        <el-table-column prop="extra" label="数据项" width="120" show-overflow-tooltip />
        <el-table-column prop="remark" label="说明" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="applyScheme('meta', row)">执行</el-button>
            <el-button link type="primary" @click="openEditScheme('meta', row)">编辑</el-button>
            <el-button link type="danger" @click="deleteScheme('meta', row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <PortalPagination v-model:page="metaPage" v-model:page-size="metaPageSize" :total="metaTotal" />

      <el-alert
        v-if="metaDone && metaHint"
        type="info"
        :closable="false"
        style="margin:12px 0"
        :title="metaHint"
      />
      <el-table v-if="metaDone" :data="metaHits" stripe size="small" style="margin-top:8px">
        <el-table-column prop="entryCode" label="元数据码" width="150" show-overflow-tooltip />
        <el-table-column prop="entryName" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="physicalTable" label="物理表" width="150" show-overflow-tooltip />
        <el-table-column label="业务分类" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.businessDomain || row.dataLayer || '-' }}</template>
        </el-table-column>
        <el-table-column prop="tags" label="标签" width="120" show-overflow-tooltip />
        <el-table-column label="命中数据项" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ (row.matchedDataItems || []).join('、') || '-' }}</template>
        </el-table-column>
        <el-table-column label="纳管" width="80">
          <template #default="{ row }">
            <el-tag :type="row.managed ? 'success' : 'info'" size="small">
              {{ row.managed ? '已纳管' : '未纳管' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="!row.managed"
              @click="lockManagedTableAndQuery(row.managedTableId)"
            >
              锁定并查询
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="维护检索方案或按条件检索元数据" />
    </el-tab-pane>

    <el-tab-pane label="数据查询与下载" name="query">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="纳管表" class="portal-field-xl">
          <el-select v-model="queryTableId" placeholder="输入表名筛选" filterable clearable>
            <el-option
              v-for="t in managedTables"
              :key="t.id"
              :label="`${t.physicalTable}${t.metaEntryCode ? ' · ' + t.metaEntryCode : ''}`"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="查询关键词" class="portal-field-md">
          <el-input v-model="queryKeyword" placeholder="可选，过滤行" clearable @keyup.enter="doQueryTable" />
        </el-form-item>
        <el-form-item label="指定列" class="portal-field-sm">
          <el-input v-model="queryColumn" placeholder="可选列名" clearable @keyup.enter="doQueryTable" />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="doQueryTable">查询</el-button>
          <el-button :disabled="!queryResult" @click="downloadQueryCsv">下载 CSV</el-button>
          <el-button @click="openCreateScheme('query')">新建方案</el-button>
        </el-form-item>
      </el-form>

      <div class="scheme-title">查询与下载方案</div>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="方案搜索" class="portal-field-lg">
          <el-input v-model="schemeKeyword.query" clearable placeholder="名称/关键词/列" @change="resetQueryPage" />
        </el-form-item>
      </el-form>
      <el-table :data="queryPaged" stripe border size="small">
        <el-table-column prop="name" label="方案名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="scope" label="纳管表ID" width="100" show-overflow-tooltip />
        <el-table-column prop="keyword" label="关键词" width="120" show-overflow-tooltip />
        <el-table-column prop="extra" label="指定列" width="120" show-overflow-tooltip />
        <el-table-column prop="remark" label="说明" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="applyScheme('query', row)">执行</el-button>
            <el-button link type="primary" @click="openEditScheme('query', row)">编辑</el-button>
            <el-button link type="danger" @click="deleteScheme('query', row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <PortalPagination v-model:page="queryPage" v-model:page-size="queryPageSize" :total="queryTotal" />

      <template v-if="queryResult">
        <el-alert
          type="info"
          :closable="false"
          style="margin:12px 0"
          :title="`表 ${queryResult.physicalTable} · 返回 ${queryResult.rowCount} 行（上限 ${queryResult.limit}）${queryResult.filter ? ' · 条件 ' + queryResult.filter : ''}`"
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
      <el-empty v-else description="维护查询方案，或选择纳管表后查询预览并可下载 CSV" />
    </el-tab-pane>
  </el-tabs>

  <el-dialog
    v-model="schemeDialogVisible"
    :title="editingSchemeId ? '编辑方案' : '新建方案'"
    width="520px"
    destroy-on-close
  >
    <el-form label-width="100px">
      <el-form-item label="方案名称" required>
        <el-input v-model="schemeForm.name" placeholder="如：自然人身份核验" />
      </el-form-item>
      <el-form-item :label="schemeTab === 'query' ? '关键词' : '关键词'">
        <el-input v-model="schemeForm.keyword" />
      </el-form-item>
      <el-form-item :label="schemeTab === 'fulltext' ? '业务范围' : schemeTab === 'meta' ? '业务分类' : '纳管表ID'">
        <el-input v-model="schemeForm.scope" :placeholder="schemeTab === 'query' ? '纳管表数字 ID' : ''" />
      </el-form-item>
      <el-form-item :label="schemeTab === 'fulltext' ? '命中字段' : schemeTab === 'meta' ? '数据项' : '指定列'">
        <el-input v-model="schemeForm.extra" />
      </el-form-item>
      <el-form-item label="说明">
        <el-input v-model="schemeForm.remark" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="schemeForm.status" style="width:100%">
          <el-option label="启用" value="ACTIVE" />
          <el-option label="停用" value="INACTIVE" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="schemeDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="saveScheme">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.scheme-title {
  margin: 8px 0 10px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
</style>
