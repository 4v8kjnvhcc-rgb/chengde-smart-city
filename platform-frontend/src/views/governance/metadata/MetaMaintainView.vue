<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { SECURITY_OPTIONS, TAG_OPTIONS, ENTRY_TYPE_OPTIONS } from './meta-labels'
import { statusLabel } from '@/utils/status-label'

interface Entry {
  id: number
  entryCode: string
  entryName: string
  entryType: string
  description?: string
  tags?: string
  keywords?: string
  securityLevel?: string
  businessDomain?: string
  ownerName?: string
  dataLayer?: string
  databaseName?: string
  changeFlag?: string
  status: string
  omRef?: string
}

interface MaintainRow {
  entry: Entry
  latestVersionNo?: number
  latestVersionId?: number
  publishedVersionNo?: number
  publishedVersionId?: number
  needRepublish?: boolean
  pendingFirstPublish?: boolean
  standardCode?: string
}

interface Notice {
  id: number
  entryId?: number
  entryCode?: string
  title: string
  detail?: string
  status: string
  createdAt?: string
}

interface Suggestion {
  itemName: string
  count: number
  itemType: string
}

interface AutoPreview {
  entryId: number
  entryName: string
  entryType: string
  standardCode: string
  standardName: string
  suggestedDescription?: string
  suggestedSecurity?: string
  suggestedDomain?: string
  alreadyLinked?: boolean
}

const activeTab = ref<'auto' | 'manual' | 'notice'>('manual')
const loading = ref(false)
const items = ref<MaintainRow[]>([])
const notices = ref<Notice[]>([])
const suggestions = ref<Suggestion[]>([])
const autoPreview = ref<AutoPreview[]>([])
const autoRunning = ref(false)
const kpi = reactive({ total: 0, needRepublish: 0, unreadNotice: 0, standardLinked: 0 })

const filter = reactive({
  keyword: '',
  entryType: '',
  needRepublishOnly: false,
})
const page = ref(1)
const pageSize = ref(10)

const form = reactive({
  id: undefined as number | undefined,
  entryCode: '',
  entryName: '',
  entryType: 'TABLE',
  description: '',
  businessDomain: '',
  ownerName: '',
  keywords: '',
  securityLevel: '',
})
const tagList = ref<string[]>([])
const editVisible = ref(false)

const createVisible = ref(false)
const createForm = reactive({
  entryName: '',
  entryType: 'TABLE',
  description: '',
  businessDomain: '',
  ownerName: '',
  keywords: '',
  securityLevel: '',
  tags: [] as string[],
})

const compareVisible = ref(false)
const compareLoading = ref(false)
const compareData = ref<{
  entry?: Entry
  published?: { versionNo?: number; changeSummary?: string; createdAt?: string }
  latest?: { versionNo?: number; changeSummary?: string }
  sameSnapshot?: boolean
  basicDiff?: Array<{ field: string; left: string; right: string }>
} | null>(null)
const noticeDetail = ref<Notice | null>(null)
const noticeVisible = ref(false)

const selectedIds = ref<number[]>([])

const pagedItems = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return items.value.slice(start, start + pageSize.value)
})

const unreadNotices = computed(() => notices.value.filter((n) => n.status === 'UNREAD'))

function tagsToArray(tags?: string): string[] {
  if (!tags) return []
  return tags.split(/[,，]/).map((t) => t.trim()).filter(Boolean)
}

function tagsToString(list: string[]): string {
  return list.filter(Boolean).join(',')
}

function formatTime(v?: string) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}

function fieldLabel(field: string) {
  const map: Record<string, string> = {
    entryName: '名称',
    description: '说明',
    tags: '标签',
    keywords: '关键字',
    securityLevel: '安全分级',
    businessDomain: '业务域',
    ownerName: '责任人',
    changeFlag: '变更标记',
    status: '状态',
    omRef: '标准编码',
    publishStatus: '发布状态',
  }
  return map[field] || field
}

async function loadOverview() {
  loading.value = true
  try {
    const res = await api.get('/governance/platform/metadata/maintain/overview', {
      params: {
        keyword: filter.keyword || undefined,
        entryType: filter.entryType || undefined,
        needRepublishOnly: filter.needRepublishOnly || undefined,
      },
    })
    items.value = res.data.items || []
    const k = res.data.kpi || {}
    kpi.total = Number(k.total || 0)
    kpi.needRepublish = Number(k.needRepublish || 0)
    kpi.unreadNotice = Number(k.unreadNotice || 0)
    kpi.standardLinked = Number(k.standardLinked || 0)
  } finally {
    loading.value = false
  }
}

async function loadNotices() {
  notices.value = (await api.get('/governance/platform/metadata/notices')).data || []
}

async function loadSuggestions() {
  suggestions.value = (await api.get('/governance/platform/metadata/maintain/suggest-standards')).data || []
}

async function loadAutoPreview() {
  autoPreview.value = (await api.get('/governance/platform/metadata/maintain/auto-preview', {
    params: { limit: 50 },
  })).data || []
}

async function onSearch() {
  page.value = 1
  await loadOverview()
}

function resetFilter() {
  filter.keyword = ''
  filter.entryType = ''
  filter.needRepublishOnly = false
  void onSearch()
}

function openEdit(row: MaintainRow) {
  const e = row.entry
  form.id = e.id
  form.entryCode = e.entryCode
  form.entryName = e.entryName
  form.entryType = e.entryType
  form.description = e.description || ''
  form.businessDomain = e.businessDomain || ''
  form.ownerName = e.ownerName || ''
  form.keywords = e.keywords || ''
  form.securityLevel = e.securityLevel || ''
  tagList.value = tagsToArray(e.tags)
  editVisible.value = true
}

async function saveUpdate() {
  if (!form.id || !form.entryName.trim()) {
    ElMessage.warning('请填写名称')
    return
  }
  await api.post('/governance/platform/metadata/maintain', {
    id: form.id,
    entryName: form.entryName,
    description: form.description,
    businessDomain: form.businessDomain,
    ownerName: form.ownerName,
    tags: tagsToString(tagList.value),
    keywords: form.keywords,
    securityLevel: form.securityLevel || undefined,
    mode: 'MANUAL',
  })
  ElMessage.success('已保存，版本有更新，请重新发布')
  editVisible.value = false
  await Promise.all([loadOverview(), loadNotices()])
}

async function saveCreate() {
  if (!createForm.entryName.trim()) {
    ElMessage.warning('请填写名称')
    return
  }
  await api.post('/governance/platform/metadata/maintain', {
    entryName: createForm.entryName,
    entryType: createForm.entryType,
    description: createForm.description,
    businessDomain: createForm.businessDomain,
    ownerName: createForm.ownerName,
    tags: tagsToString(createForm.tags),
    keywords: createForm.keywords,
    securityLevel: createForm.securityLevel || undefined,
    mode: 'MANUAL',
  })
  ElMessage.success('已手工补录')
  createVisible.value = false
  Object.assign(createForm, {
    entryName: '', description: '', businessDomain: '', ownerName: '', keywords: '', securityLevel: '', tags: [],
  })
  await Promise.all([loadOverview(), loadNotices()])
}

async function runAuto(selectedOnly = false) {
  try {
    await ElMessageBox.confirm(
      selectedOnly
        ? `将按数据元标准自动匹配并补充选中的 ${selectedIds.value.length} 条元数据，是否继续？`
        : '将按数据元标准自动匹配并补充可匹配的元数据信息项，是否继续？',
      '自动维护',
      { type: 'info', confirmButtonText: '开始匹配', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  if (selectedOnly && !selectedIds.value.length) {
    ElMessage.warning('请先勾选条目')
    return
  }
  autoRunning.value = true
  try {
    const res = await api.post('/governance/platform/metadata/maintain/auto-run', {
      entryIds: selectedOnly ? selectedIds.value : undefined,
    })
    ElMessage.success(`扫描 ${res.data.scanned} 条，成功匹配补充 ${res.data.matched} 条`)
    selectedIds.value = []
    await Promise.all([loadOverview(), loadNotices(), loadAutoPreview(), loadSuggestions()])
  } finally {
    autoRunning.value = false
  }
}

async function promote(item: Suggestion) {
  await api.post('/governance/platform/metadata/maintain/promote-standard', {
    itemName: item.itemName,
    itemType: item.itemType,
  })
  ElMessage.success(`已沉淀标准：${item.itemName}`)
  await Promise.all([loadSuggestions(), loadAutoPreview()])
}

async function publishEntry(row: MaintainRow) {
  try {
    await ElMessageBox.confirm(
      `确认发布「${row.entry.entryName}」？发布后将作为最新生效版本。`,
      '发布元数据',
      { type: 'warning', confirmButtonText: '发布', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await api.post(`/governance/platform/metadata/maintain/entries/${row.entry.id}/publish`)
  ElMessage.success('已发布')
  await Promise.all([loadOverview(), loadNotices()])
}

async function openCompare(row: MaintainRow) {
  compareVisible.value = true
  compareLoading.value = true
  compareData.value = null
  try {
    compareData.value = (await api.get(
      `/governance/platform/metadata/maintain/entries/${row.entry.id}/compare-published`,
    )).data
  } catch {
    ElMessage.error('加载对比失败')
    compareVisible.value = false
  } finally {
    compareLoading.value = false
  }
}

async function openNotice(row: Notice) {
  noticeDetail.value = row
  noticeVisible.value = true
  if (row.status === 'UNREAD') {
    try {
      await api.post(`/governance/platform/metadata/notices/${row.id}/read`)
      row.status = 'READ'
      kpi.unreadNotice = Math.max(0, kpi.unreadNotice - 1)
    } catch { /* ignore */ }
  }
}

function onSelectionChange(rows: MaintainRow[]) {
  selectedIds.value = rows.map((r) => r.entry.id)
}

function applyKpiFilter(kind: 'all' | 'republish' | 'notice') {
  if (kind === 'notice') {
    activeTab.value = 'notice'
    return
  }
  activeTab.value = 'manual'
  filter.needRepublishOnly = kind === 'republish'
  void onSearch()
}

onMounted(async () => {
  await onSearch()
  await Promise.all([loadNotices(), loadSuggestions(), loadAutoPreview()])
})
</script>

<template>
  <div v-loading="loading" class="mmaint">
    <div class="mmaint-kpi">
      <button type="button" class="mmaint-kpi__card" @click="applyKpiFilter('all')">
        <span>维护条目</span><b>{{ kpi.total }}</b>
      </button>
      <button type="button" class="mmaint-kpi__card tone-warn" :class="{ 'is-on': filter.needRepublishOnly }" @click="applyKpiFilter('republish')">
        <span>待重新发布</span><b>{{ kpi.needRepublish }}</b>
      </button>
      <button type="button" class="mmaint-kpi__card tone-info" @click="applyKpiFilter('notice')">
        <span>未读变更</span><b>{{ kpi.unreadNotice }}</b>
      </button>
      <button type="button" class="mmaint-kpi__card tone-ok" @click="activeTab = 'auto'">
        <span>已关联标准</span><b>{{ kpi.standardLinked }}</b>
      </button>
    </div>

    <PageCard title="元数据维护">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="手工维护" name="manual" />
        <el-tab-pane label="自动维护" name="auto" />
        <el-tab-pane name="notice">
          <template #label>
            <span>变更提醒</span>
            <el-badge v-if="unreadNotices.length" :value="unreadNotices.length" class="mmaint-badge" />
          </template>
        </el-tab-pane>
      </el-tabs>

      <!-- 手工维护 -->
      <template v-if="activeTab === 'manual'">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="关键字" class="portal-field-lg">
            <el-input v-model="filter.keyword" clearable placeholder="名称/编码/标签" @keyup.enter="onSearch" />
          </el-form-item>
          <el-form-item label="类型" class="portal-field-md">
            <el-select v-model="filter.entryType" clearable placeholder="全部">
              <el-option v-for="o in ENTRY_TYPE_OPTIONS.filter(x => ['TABLE','SOURCE','COLUMN'].includes(x.value))" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="filter.needRepublishOnly">仅看待重新发布</el-checkbox>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="onSearch">查询</el-button>
            <el-button @click="resetFilter">重置</el-button>
            <el-button type="success" @click="createVisible = true">手工补录</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="pagedItems" stripe size="small" @selection-change="onSelectionChange">
          <el-table-column type="selection" width="42" />
          <el-table-column label="名称" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="mmaint-name">{{ row.entry.entryName }}</div>
              <div class="mmaint-code">{{ row.entry.entryCode }}</div>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ $statusLabel(row.entry.entryType) }}</template>
          </el-table-column>
          <el-table-column label="标签" width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.entry.tags || '—' }}</template>
          </el-table-column>
          <el-table-column label="分级" width="90">
            <template #default="{ row }">{{ $statusLabel(row.entry.securityLevel) }}</template>
          </el-table-column>
          <el-table-column label="版本" width="110">
            <template #default="{ row }">
              <span>v{{ row.latestVersionNo ?? '—' }}</span>
              <el-tag v-if="row.needRepublish" type="warning" size="small" style="margin-left:4px">需重新发布</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="变更" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.entry.changeFlag" size="small" :type="$statusTagType(row.entry.changeFlag)">
                {{ $statusLabel(row.entry.changeFlag) }}
              </el-tag>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="primary" @click="openCompare(row)">变更对比</el-button>
              <el-button v-if="row.needRepublish || row.pendingFirstPublish" link type="success" @click="publishEntry(row)">发布</el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination
          v-if="items.length"
          v-model:page="page"
          v-model:page-size="pageSize"
          :total="items.length"
        />
      </template>

      <!-- 自动维护 -->
      <template v-else-if="activeTab === 'auto'">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom:12px"
          title="系统将元数据信息项与数据元标准自动匹配，依据标准补充说明、分级、业务域、标签等缺失信息。"
        />
        <div class="mmaint-auto-actions">
          <el-button type="primary" :loading="autoRunning" @click="runAuto(false)">一键自动匹配补充</el-button>
          <el-button :loading="autoRunning" :disabled="!selectedIds.length" @click="runAuto(true)">仅匹配已选条目</el-button>
          <el-button @click="loadAutoPreview">刷新预览</el-button>
        </div>

        <div class="mmaint-section">匹配预览（可补全）</div>
        <el-table :data="autoPreview" stripe size="small" max-height="360" empty-text="暂无可匹配标准的条目">
          <el-table-column prop="entryName" label="元数据项" min-width="120" />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ $statusLabel(row.entryType) }}</template>
          </el-table-column>
          <el-table-column prop="standardName" label="匹配数据元" min-width="120" />
          <el-table-column prop="standardCode" label="标准编码" width="120" />
          <el-table-column prop="suggestedDescription" label="建议说明" min-width="160" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.alreadyLinked ? 'success' : 'warning'" size="small">
                {{ row.alreadyLinked ? '已关联' : '待补充' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

        <div class="mmaint-section">标准沉淀建议</div>
        <el-table :data="suggestions" stripe size="small" max-height="240" empty-text="暂无沉淀建议">
          <el-table-column prop="itemName" label="高频字段名" min-width="140" />
          <el-table-column prop="count" label="出现频次" width="100" />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ $statusLabel(row.itemType) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="promote(row)">沉淀标准</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <!-- 变更提醒 -->
      <template v-else>
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom:12px"
          title="列表展示当前最新版本；元数据更新后将提示「需重新发布」。可查看变更详情，并与最近发布版本对比。"
        />
        <el-table :data="notices" stripe size="small" empty-text="暂无变更提醒">
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 'UNREAD' ? 'danger' : 'info'" size="small">{{ $statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
          <el-table-column prop="entryCode" label="条目编码" width="140" show-overflow-tooltip />
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="openNotice(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="mmaint-section">待重新发布条目</div>
        <el-table :data="items.filter(i => i.needRepublish)" stripe size="small" empty-text="暂无待发布项">
          <el-table-column label="名称" min-width="140">
            <template #default="{ row }">{{ row.entry.entryName }}</template>
          </el-table-column>
          <el-table-column label="最新版本" width="100">
            <template #default="{ row }">v{{ row.latestVersionNo ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="已发布版本" width="110">
            <template #default="{ row }">{{ row.publishedVersionNo != null ? `v${row.publishedVersionNo}` : '未发布' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button link type="primary" @click="openCompare(row)">对比详情</el-button>
              <el-button link type="success" @click="publishEntry(row)">重新发布</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </PageCard>

    <!-- 手工编辑抽屉 -->
    <el-drawer v-model="editVisible" title="手工维护元数据" size="520px">
      <el-form label-width="96px">
        <el-form-item label="条目编码">
          <el-input :model-value="form.entryCode" disabled />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.entryName" />
        </el-form-item>
        <el-form-item label="类型">
          <el-input :model-value="statusLabel(form.entryType)" disabled />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="业务域">
          <el-input v-model="form.businessDomain" placeholder="分类/业务域" />
        </el-form-item>
        <el-form-item label="责任人">
          <el-input v-model="form.ownerName" />
        </el-form-item>
        <el-form-item label="数据标签">
          <el-select v-model="tagList" multiple filterable allow-create collapse-tags style="width:100%">
            <el-option v-for="t in TAG_OPTIONS" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="安全分级">
          <el-select v-model="form.securityLevel" clearable style="width:100%">
            <el-option v-for="o in SECURITY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="form.keywords" placeholder="逗号分隔" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveUpdate">保存</el-button>
          <el-button @click="editVisible = false">取消</el-button>
        </el-form-item>
      </el-form>
    </el-drawer>

    <!-- 手工补录 -->
    <el-dialog v-model="createVisible" title="手工补录元数据" width="560px" destroy-on-close>
      <el-form label-width="88px">
        <el-form-item label="名称" required>
          <el-input v-model="createForm.entryName" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="createForm.entryType" style="width:100%">
            <el-option
              v-for="o in ENTRY_TYPE_OPTIONS.filter(x => x.value === 'TABLE' || x.value === 'SOURCE')"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="createForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="业务域">
          <el-input v-model="createForm.businessDomain" />
        </el-form-item>
        <el-form-item label="责任人">
          <el-input v-model="createForm.ownerName" />
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="createForm.tags" multiple collapse-tags style="width:100%">
            <el-option v-for="t in TAG_OPTIONS" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="安全分级">
          <el-select v-model="createForm.securityLevel" clearable style="width:100%">
            <el-option v-for="o in SECURITY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="createForm.keywords" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCreate">提交</el-button>
      </template>
    </el-dialog>

    <!-- 与已发布版本对比 -->
    <el-drawer v-model="compareVisible" title="与最新发布版本对比" size="560px">
      <div v-loading="compareLoading">
        <template v-if="compareData">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="条目">{{ compareData.entry?.entryName }}</el-descriptions-item>
            <el-descriptions-item label="已发布版本">
              {{ compareData.published ? `v${compareData.published.versionNo}` : '尚未发布' }}
            </el-descriptions-item>
            <el-descriptions-item label="当前最新版本">
              {{ compareData.latest ? `v${compareData.latest.versionNo}` : '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="是否一致">
              <el-tag :type="compareData.sameSnapshot ? 'success' : 'warning'" size="small">
                {{ compareData.sameSnapshot ? '一致' : '有差异，需重新发布' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
          <div class="mmaint-section">字段差异（左=已发布，右=当前）</div>
          <el-table :data="compareData.basicDiff || []" stripe size="small" empty-text="无差异">
            <el-table-column label="字段" width="120">
              <template #default="{ row }">{{ fieldLabel(row.field) }}</template>
            </el-table-column>
            <el-table-column prop="left" label="已发布" min-width="120" show-overflow-tooltip />
            <el-table-column prop="right" label="当前" min-width="120" show-overflow-tooltip />
          </el-table>
        </template>
      </div>
    </el-drawer>

    <el-drawer v-model="noticeVisible" title="变更详情" size="480px">
      <template v-if="noticeDetail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="标题">{{ noticeDetail.title }}</el-descriptions-item>
          <el-descriptions-item label="编码">{{ noticeDetail.entryCode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ $statusLabel(noticeDetail.status) }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ formatTime(noticeDetail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="详情">{{ noticeDetail.detail || '—' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.mmaint-kpi {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 12px;
}
.mmaint-kpi__card {
  appearance: none; border: 1px solid #e8edf5; background: #fff; border-radius: 10px;
  padding: 12px 14px; text-align: left; cursor: pointer;
  box-shadow: 0 1px 4px rgba(15, 40, 80, .04);
}
.mmaint-kpi__card span { display: block; font-size: 12px; color: #606266; }
.mmaint-kpi__card b { font-size: 24px; font-weight: 700; color: #303133; }
.mmaint-kpi__card.tone-warn b { color: #ef6c00; }
.mmaint-kpi__card.tone-info b { color: #1677ff; }
.mmaint-kpi__card.tone-ok b { color: #2e7d32; }
.mmaint-kpi__card.is-on { outline: 2px solid #ef6c00; }
.mmaint-badge { margin-left: 6px; }
.mmaint-name { font-weight: 600; }
.mmaint-code { font-size: 12px; color: #909399; }
.mmaint-section {
  margin: 16px 0 8px; font-size: 13px; font-weight: 600;
  padding-left: 8px; border-left: 3px solid #1677ff;
}
.mmaint-auto-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
@media (max-width: 1100px) {
  .mmaint-kpi { grid-template-columns: repeat(2, 1fr); }
}
</style>
