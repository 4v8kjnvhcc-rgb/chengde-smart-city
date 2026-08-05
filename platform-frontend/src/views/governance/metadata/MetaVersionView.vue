<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { statusLabel } from '@/utils/status-label'

interface VersionTarget {
  targetType: 'ENTRY' | 'MODEL'
  targetId: number
  code: string
  name: string
  status?: string
  changeFlag?: string
  publishStatus: string
  versionCount?: number
  latestVersionNo?: number
  latestVersionId?: number
  publishedVersionNo?: number
  publishedVersionId?: number
  canSubscribe?: boolean
  needRepublish?: boolean
  entryType?: string
  ownerName?: string
  updatedAt?: string
}

interface Version {
  id: number
  targetType: string
  targetId: number
  versionNo: number
  changeSummary?: string
  createdBy?: string
  createdAt?: string
}

interface FieldAttr {
  nameZh?: string
  nameEn?: string
  dataType?: string
  length?: string
  primaryKey?: string
  partition?: string
  unit?: string
  description?: string
}

interface AttrDiffRow {
  fieldName: string
  changeType: string
  attr: string
  left: string
  right: string
}

const activeTab = ref<'publish' | 'history' | 'compare'>('publish')
const loading = ref(false)
const items = ref<VersionTarget[]>([])
const kpi = reactive({ total: 0, published: 0, draft: 0, offline: 0 })

const filter = reactive({
  keyword: '',
  targetType: 'ENTRY' as 'ENTRY' | 'MODEL',
  publishStatus: '',
})
const page = ref(1)
const pageSize = ref(10)

const selected = ref<VersionTarget | null>(null)
const versions = ref<Version[]>([])
const versionsLoading = ref(false)

const publishVisible = ref(false)
const publishDesc = ref('')
const publishTarget = ref<VersionTarget | null>(null)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<{
  version?: Version
  basicInfo?: Record<string, unknown>
  fields?: FieldAttr[]
  relations?: Array<{ fromCode?: string; toCode?: string; label?: string; relationType?: string }>
  dataPreview?: Array<Record<string, unknown>>
  previewHint?: string
  isPublishVersion?: boolean
} | null>(null)

const compareForm = reactive({
  leftId: undefined as number | undefined,
  rightId: undefined as number | undefined,
})
const compareLoading = ref(false)
const compare = ref<{
  sameSnapshot?: boolean
  basicDiff?: Array<{ field: string; left: string; right: string }>
  attrDiff?: AttrDiffRow[]
  fieldDiff?: { added?: string[]; removed?: string[]; changed?: string[] }
  left?: Version
  right?: Version
} | null>(null)

const pagedItems = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return items.value.slice(start, start + pageSize.value)
})

const previewColumns = computed(() => {
  const rows = detail.value?.dataPreview || []
  if (!rows.length) return [] as string[]
  return Object.keys(rows[0])
})

const basicInfoRows = computed(() => {
  const info = detail.value?.basicInfo || {}
  const prefer = [
    'entryName', 'entryCode', 'entryType', 'modelNameZh', 'modelCode', 'modelType',
    'description', 'businessDomain', 'ownerName', 'securityLevel', 'tags', 'keywords',
    'dataLayer', 'databaseName', 'physicalTableName', 'status', 'changeFlag',
  ]
  const keys = [...prefer.filter(k => k in info), ...Object.keys(info).filter(k => !prefer.includes(k))]
  return keys
    .filter(k => !['fields', 'relations', 'contentJson', 'snapshotJson'].includes(k))
    .map(k => ({ field: k, value: formatVal(info[k]) }))
})

function formatTime(v?: string) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}

function formatVal(v: unknown) {
  if (v == null || v === '') return '—'
  if (typeof v === 'object') return JSON.stringify(v)
  return String(v)
}

function fieldLabel(field: string) {
  const map: Record<string, string> = {
    entryName: '名称',
    entryCode: '编码',
    entryType: '类型',
    modelNameZh: '模型名称',
    modelCode: '模型编码',
    modelType: '模型类型',
    description: '说明',
    businessDomain: '业务域',
    ownerName: '责任人',
    securityLevel: '安全分级',
    tags: '标签',
    keywords: '关键字',
    dataLayer: '数据分层',
    databaseName: '数据库',
    physicalTableName: '物理表',
    status: '状态',
    changeFlag: '变更标记',
    omRef: '标准引用',
  }
  return map[field] || field
}

function attrLabel(attr: string) {
  const map: Record<string, string> = {
    nameZh: '字段中文名',
    nameEn: '字段英文名',
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

function publishStatusType(s?: string) {
  if (s === 'PUBLISHED') return 'success'
  if (s === 'OFFLINE') return 'info'
  return 'warning'
}

function publishStatusLabel(s?: string) {
  if (s === 'PUBLISHED') return '已定版'
  if (s === 'OFFLINE') return '已下线'
  if (s === 'DRAFT') return '待发布'
  return statusLabel(s)
}

function onPickTarget(id: number | undefined) {
  if (id == null) {
    selected.value = null
    versions.value = []
    return
  }
  const row = items.value.find(i => i.targetId === id)
  if (row) selectTarget(row)
}

function onPickCompareTarget(id: number | undefined) {
  if (id == null) {
    selected.value = null
    versions.value = []
    compare.value = null
    return
  }
  const row = items.value.find(i => i.targetId === id)
  if (row) goCompare(row)
}

async function loadOverview() {
  loading.value = true
  try {
    const res = await api.get('/governance/platform/metadata/versions/overview', {
      params: {
        keyword: filter.keyword || undefined,
        targetType: filter.targetType,
        publishStatus: filter.publishStatus || undefined,
      },
    })
    items.value = res.data.items || []
    const k = res.data.kpi || {}
    kpi.total = Number(k.total || 0)
    kpi.published = Number(k.published || 0)
    kpi.draft = Number(k.draft || 0)
    kpi.offline = Number(k.offline || 0)
  } finally {
    loading.value = false
  }
}

async function onSearch() {
  page.value = 1
  await loadOverview()
}

function resetFilter() {
  filter.keyword = ''
  filter.publishStatus = ''
  onSearch()
}

function setKpiFilter(status: string) {
  filter.publishStatus = filter.publishStatus === status ? '' : status
  onSearch()
}

async function selectTarget(row: VersionTarget) {
  selected.value = row
  compare.value = null
  compareForm.leftId = undefined
  compareForm.rightId = undefined
  await loadVersions()
}

async function loadVersions() {
  if (!selected.value) {
    versions.value = []
    return
  }
  versionsLoading.value = true
  try {
    versions.value = (await api.get('/governance/platform/metadata/versions', {
      params: {
        targetType: selected.value.targetType,
        targetId: selected.value.targetId,
      },
    })).data || []
  } finally {
    versionsLoading.value = false
  }
}

function openPublish(row: VersionTarget) {
  publishTarget.value = row
  publishDesc.value = row.publishStatus === 'PUBLISHED' ? '重新发布定版' : '首次发布定版'
  publishVisible.value = true
}

async function confirmPublish() {
  if (!publishTarget.value) return
  await api.post('/governance/platform/metadata/versions/publish', {
    targetType: publishTarget.value.targetType,
    targetId: publishTarget.value.targetId,
    description: publishDesc.value || '发布元数据',
  })
  ElMessage.success('已发布定版')
  publishVisible.value = false
  await loadOverview()
  if (selected.value?.targetId === publishTarget.value.targetId) {
    await loadVersions()
  }
}

async function doOffline(row: VersionTarget) {
  try {
    await ElMessageBox.prompt('请填写下线说明（可选）', '下线元数据', {
      confirmButtonText: '确认下线',
      cancelButtonText: '取消',
      inputPlaceholder: '下线原因',
      type: 'warning',
    }).then(async ({ value }) => {
      await api.post('/governance/platform/metadata/versions/offline', {
        targetType: row.targetType,
        targetId: row.targetId,
        description: value || '下线元数据',
      })
      ElMessage.success('已下线')
      await loadOverview()
      if (selected.value?.targetId === row.targetId) {
        await loadVersions()
      }
    })
  } catch {
    /* cancel */
  }
}

async function subscribe(row: VersionTarget) {
  if (!row.canSubscribe) {
    ElMessage.warning('仅定版发布的元数据支持订阅')
    return
  }
  await api.post('/governance/platform/metadata/subscriptions', {
    targetType: row.targetType,
    targetId: row.targetId,
    channel: 'NOTICE',
  })
  ElMessage.success('已订阅变更通知')
}

async function openDetail(version: Version) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = (await api.get(`/governance/platform/metadata/versions/${version.id}/detail`)).data
  } finally {
    detailLoading.value = false
  }
}

async function rollback(version: Version) {
  try {
    await ElMessageBox.confirm(
      `确定回滚到版本 v${version.versionNo}？将恢复该版本快照并生成新版本记录。`,
      '版本回滚确认',
      { type: 'warning', confirmButtonText: '确认回滚', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await api.post(`/governance/platform/metadata/versions/${version.id}/rollback`)
  ElMessage.success(`已回滚至 v${version.versionNo}`)
  await loadOverview()
  await loadVersions()
}

function goHistory(row: VersionTarget) {
  selected.value = row
  activeTab.value = 'history'
  loadVersions()
}

function goCompare(row: VersionTarget) {
  selected.value = row
  activeTab.value = 'compare'
  compare.value = null
  compareForm.leftId = undefined
  compareForm.rightId = undefined
  loadVersions().then(() => {
    if (versions.value.length >= 2) {
      compareForm.rightId = versions.value[0].id
      compareForm.leftId = versions.value[1].id
    } else if (versions.value.length === 1) {
      compareForm.rightId = versions.value[0].id
    }
  })
}

async function doCompare() {
  if (!compareForm.leftId || !compareForm.rightId) {
    ElMessage.warning('请选择两个版本进行对比')
    return
  }
  if (compareForm.leftId === compareForm.rightId) {
    ElMessage.warning('请选择不同的两个版本')
    return
  }
  compareLoading.value = true
  try {
    compare.value = (await api.get('/governance/platform/metadata/versions/compare', {
      params: { leftId: compareForm.leftId, rightId: compareForm.rightId },
    })).data
  } finally {
    compareLoading.value = false
  }
}

function compareWithLatest(version: Version) {
  if (!versions.value.length) return
  const latest = versions.value[0]
  compareForm.leftId = version.id
  compareForm.rightId = latest.id
  activeTab.value = 'compare'
  doCompare()
}

watch(() => filter.targetType, () => {
  selected.value = null
  versions.value = []
  compare.value = null
  onSearch()
})

onMounted(loadOverview)
</script>

<template>
  <div>
    <div class="mver-kpi">
      <button type="button" class="mver-kpi__card" :class="{ 'is-on': !filter.publishStatus }" @click="filter.publishStatus = ''; onSearch()">
        <span>实体总数</span>
        <b>{{ kpi.total }}</b>
      </button>
      <button type="button" class="mver-kpi__card tone-ok" :class="{ 'is-on': filter.publishStatus === 'PUBLISHED' }" @click="setKpiFilter('PUBLISHED')">
        <span>已定版发布</span>
        <b>{{ kpi.published }}</b>
      </button>
      <button type="button" class="mver-kpi__card tone-warn" :class="{ 'is-on': filter.publishStatus === 'DRAFT' }" @click="setKpiFilter('DRAFT')">
        <span>待发布</span>
        <b>{{ kpi.draft }}</b>
      </button>
      <button type="button" class="mver-kpi__card tone-info" :class="{ 'is-on': filter.publishStatus === 'OFFLINE' }" @click="setKpiFilter('OFFLINE')">
        <span>已下线</span>
        <b>{{ kpi.offline }}</b>
      </button>
    </div>

    <PageCard title="元数据版本管理">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="元数据发布" name="publish" />
        <el-tab-pane label="版本历史" name="history" />
        <el-tab-pane label="版本对比" name="compare" />
      </el-tabs>

      <!-- 发布管理 -->
      <template v-if="activeTab === 'publish'">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom:12px"
          title="定版发布后的元数据才会进入元数据目录查询，并支持用户订阅。下线后将从查询中移除。"
        />
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="目标类型" class="portal-field-sm">
            <el-select v-model="filter.targetType">
              <el-option label="元数据条目" value="ENTRY" />
              <el-option label="元模型" value="MODEL" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键字" class="portal-field-lg">
            <el-input v-model="filter.keyword" clearable placeholder="名称/编码" @keyup.enter="onSearch" />
          </el-form-item>
          <el-form-item label="发布状态" class="portal-field-md">
            <el-select v-model="filter.publishStatus" clearable placeholder="全部">
              <el-option label="已定版" value="PUBLISHED" />
              <el-option label="待发布" value="DRAFT" />
              <el-option label="已下线" value="OFFLINE" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="onSearch">查询</el-button>
            <el-button @click="resetFilter">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="loading" :data="pagedItems" stripe size="small" highlight-current-row @row-click="selectTarget">
          <el-table-column label="名称" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="mver-name">{{ row.name }}</div>
              <div class="mver-code">{{ row.code }}</div>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="90">
            <template #default="{ row }">
              {{ row.entryType ? statusLabel(row.entryType) : (row.targetType === 'MODEL' ? '元模型' : '—') }}
            </template>
          </el-table-column>
          <el-table-column label="发布状态" width="110">
            <template #default="{ row }">
              <el-tag :type="publishStatusType(row.publishStatus)" size="small">
                {{ publishStatusLabel(row.publishStatus) }}
              </el-tag>
              <el-tag v-if="row.needRepublish" type="warning" size="small" style="margin-left:4px">需重新发布</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="版本" width="120">
            <template #default="{ row }">
              <span>最新 v{{ row.latestVersionNo ?? '—' }}</span>
              <div class="mver-code">定版 {{ row.publishedVersionNo != null ? `v${row.publishedVersionNo}` : '未发布' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="历史数" width="80" prop="versionCount" />
          <el-table-column label="更新时间" width="160">
            <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <el-button link type="success" @click.stop="openPublish(row)">发布</el-button>
              <el-button link type="warning" :disabled="row.publishStatus === 'OFFLINE'" @click.stop="doOffline(row)">下线</el-button>
              <el-button link type="primary" :disabled="!row.canSubscribe" @click.stop="subscribe(row)">订阅</el-button>
              <el-button link type="primary" @click.stop="goHistory(row)">历史</el-button>
              <el-button link type="primary" @click.stop="goCompare(row)">对比</el-button>
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

      <!-- 版本历史 -->
      <template v-else-if="activeTab === 'history'">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="目标类型" class="portal-field-sm">
            <el-select v-model="filter.targetType">
              <el-option label="元数据条目" value="ENTRY" />
              <el-option label="元模型" value="MODEL" />
            </el-select>
          </el-form-item>
          <el-form-item label="实体" class="portal-field-xl">
            <el-select
              :model-value="selected?.targetId"
              filterable
              clearable
              placeholder="请选择实体"
              @change="onPickTarget"
            >
              <el-option v-for="o in items" :key="o.targetId" :label="`${o.name}（${o.code}）`" :value="o.targetId" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button @click="loadOverview().then(() => loadVersions())">刷新</el-button>
          </el-form-item>
        </el-form>

        <el-alert
          v-if="selected"
          type="success"
          :closable="false"
          style="margin-bottom:12px"
          :title="`当前实体：${selected.name}（${selected.code}） · 共 ${versions.length} 个历史版本`"
        />
        <el-empty v-else description="请先选择实体查看修改历史" />

        <el-table v-loading="versionsLoading" :data="versions" stripe size="small" empty-text="暂无版本历史">
          <el-table-column label="版本号" width="90">
            <template #default="{ row }">
              <el-tag size="small">v{{ row.versionNo }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdBy" label="提交人" width="110" />
          <el-table-column label="提交时间" width="170">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="changeSummary" label="发布描述 / 变更说明" min-width="200" show-overflow-tooltip />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button link type="primary" @click="compareWithLatest(row)">与最新对比</el-button>
              <el-button link type="warning" @click="rollback(row)">回滚</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <!-- 版本对比 -->
      <template v-else>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom:12px"
          title="可对比历史版本与当前最新版本的基本信息项及属性信息（字段中文名、英文名、数据类型、长度、主键/分区、计量单位、描述）。"
        />
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="实体" class="portal-field-xl">
            <el-select
              :model-value="selected?.targetId"
              filterable
              clearable
              placeholder="请选择实体"
              @change="onPickCompareTarget"
            >
              <el-option v-for="o in items" :key="'c' + o.targetId" :label="`${o.name}（${o.code}）`" :value="o.targetId" />
            </el-select>
          </el-form-item>
          <el-form-item label="基准版本" class="portal-field-md">
            <el-select v-model="compareForm.leftId" clearable placeholder="历史版本">
              <el-option v-for="v in versions" :key="'l' + v.id" :label="`v${v.versionNo}`" :value="v.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="对比版本" class="portal-field-md">
            <el-select v-model="compareForm.rightId" clearable placeholder="最新/另一版本">
              <el-option v-for="v in versions" :key="'r' + v.id" :label="`v${v.versionNo}`" :value="v.id" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" :loading="compareLoading" @click="doCompare">开始对比</el-button>
          </el-form-item>
        </el-form>

        <template v-if="compare">
          <el-alert
            :type="compare.sameSnapshot ? 'success' : 'warning'"
            :closable="false"
            style="margin-bottom:12px"
            :title="compare.sameSnapshot
              ? `v${compare.left?.versionNo} 与 v${compare.right?.versionNo} 内容一致`
              : `v${compare.left?.versionNo} 与 v${compare.right?.versionNo} 存在差异`"
          />

          <div class="mver-section">基本信息对比</div>
          <el-table :data="compare.basicDiff || []" stripe size="small" empty-text="基本信息无差异">
            <el-table-column label="信息项" width="140">
              <template #default="{ row }">{{ fieldLabel(row.field) }}</template>
            </el-table-column>
            <el-table-column label="基准版本" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.left || '—' }}</template>
            </el-table-column>
            <el-table-column label="对比版本" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.right || '—' }}</template>
            </el-table-column>
          </el-table>

          <div class="mver-section">属性信息对比（字段）</div>
          <el-table :data="compare.attrDiff || []" stripe size="small" empty-text="属性信息无差异" max-height="420">
            <el-table-column prop="fieldName" label="字段" width="140" show-overflow-tooltip />
            <el-table-column label="变更" width="90">
              <template #default="{ row }">
                <el-tag
                  size="small"
                  :type="row.changeType === 'added' ? 'success' : row.changeType === 'removed' ? 'danger' : 'warning'"
                >
                  {{ row.changeType === 'added' ? '新增' : row.changeType === 'removed' ? '删除' : '变更' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="属性项" width="120">
              <template #default="{ row }">{{ attrLabel(row.attr) }}</template>
            </el-table-column>
            <el-table-column label="基准版本" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.left || '—' }}</template>
            </el-table-column>
            <el-table-column label="对比版本" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.right || '—' }}</template>
            </el-table-column>
          </el-table>
        </template>
      </template>
    </PageCard>

    <el-dialog v-model="publishVisible" title="发布定版" width="480px" destroy-on-close>
      <el-form label-width="88px">
        <el-form-item label="实体">
          <span>{{ publishTarget?.name }}（{{ publishTarget?.code }}）</span>
        </el-form-item>
        <el-form-item label="发布描述" required>
          <el-input v-model="publishDesc" type="textarea" :rows="3" placeholder="本版发布说明，将写入版本历史" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmPublish">确认发布</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="历史版本详情" size="720px">
      <div v-loading="detailLoading">
        <template v-if="detail?.version">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="版本号">v{{ detail.version.versionNo }}</el-descriptions-item>
            <el-descriptions-item label="提交人">{{ detail.version.createdBy || '—' }}</el-descriptions-item>
            <el-descriptions-item label="提交时间">{{ formatTime(detail.version.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="发布描述">{{ detail.version.changeSummary || '—' }}</el-descriptions-item>
          </el-descriptions>

          <div class="mver-section">基本信息</div>
          <el-table :data="basicInfoRows" stripe size="small" max-height="240">
            <el-table-column label="信息项" width="140">
              <template #default="{ row }">{{ fieldLabel(row.field) }}</template>
            </el-table-column>
            <el-table-column prop="value" label="值" min-width="200" show-overflow-tooltip />
          </el-table>

          <div class="mver-section">字段信息</div>
          <el-table :data="detail.fields || []" stripe size="small" max-height="280" empty-text="无字段信息">
            <el-table-column prop="nameZh" label="中文名" min-width="100" show-overflow-tooltip />
            <el-table-column prop="nameEn" label="英文名" min-width="100" show-overflow-tooltip />
            <el-table-column prop="dataType" label="数据类型" width="100" />
            <el-table-column prop="length" label="长度" width="70" />
            <el-table-column prop="primaryKey" label="主键" width="70" />
            <el-table-column prop="partition" label="分区" width="70" />
            <el-table-column prop="unit" label="计量单位" width="90" />
            <el-table-column prop="description" label="描述" min-width="140" show-overflow-tooltip />
          </el-table>

          <div class="mver-section">关联关系</div>
          <el-table :data="detail.relations || []" stripe size="small" max-height="200" empty-text="无关联关系">
            <el-table-column prop="fromCode" label="源" min-width="120" show-overflow-tooltip />
            <el-table-column prop="toCode" label="目标" min-width="120" show-overflow-tooltip />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">{{ statusLabel(row.relationType) }}</template>
            </el-table-column>
            <el-table-column prop="label" label="说明" min-width="120" show-overflow-tooltip />
          </el-table>

          <div class="mver-section">数据预览</div>
          <el-alert
            v-if="detail.previewHint && !(detail.dataPreview || []).length"
            type="info"
            :closable="false"
            :title="detail.previewHint"
            style="margin-bottom:8px"
          />
          <el-table
            v-if="(detail.dataPreview || []).length"
            :data="detail.dataPreview"
            stripe
            size="small"
            max-height="240"
          >
            <el-table-column
              v-for="col in previewColumns"
              :key="col"
              :prop="col"
              :label="col"
              min-width="100"
              show-overflow-tooltip
            />
          </el-table>
          <el-empty v-else-if="!detail.previewHint" description="暂无预览数据" :image-size="64" />
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.mver-kpi {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 12px;
}
.mver-kpi__card {
  appearance: none; border: 1px solid #e8edf5; background: #fff; border-radius: 10px;
  padding: 12px 14px; text-align: left; cursor: pointer;
  box-shadow: 0 1px 4px rgba(15, 40, 80, .04);
}
.mver-kpi__card span { display: block; font-size: 12px; color: #606266; }
.mver-kpi__card b { font-size: 24px; font-weight: 700; color: #303133; }
.mver-kpi__card.tone-warn b { color: #ef6c00; }
.mver-kpi__card.tone-info b { color: #1677ff; }
.mver-kpi__card.tone-ok b { color: #2e7d32; }
.mver-kpi__card.is-on { outline: 2px solid #1677ff; }
.mver-name { font-weight: 600; }
.mver-code { font-size: 12px; color: #909399; }
.mver-section {
  margin: 16px 0 8px; font-size: 13px; font-weight: 600;
  padding-left: 8px; border-left: 3px solid #1677ff;
}
@media (max-width: 1100px) {
  .mver-kpi { grid-template-columns: repeat(2, 1fr); }
}
</style>
