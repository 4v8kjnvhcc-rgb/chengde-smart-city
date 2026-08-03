<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'

interface CategoryNode {
  id: number
  categoryCode: string
  categoryName: string
  label?: string
  parentId?: number
  categoryPath?: string
  children?: CategoryNode[]
}

interface CatalogRes {
  id: number
  resourceCode: string
  resourceName: string
  resourceType: string
  categoryId?: number
  categoryPath?: string
  providerOrg?: string
  resourceFormat?: string
  shareType?: string
  updateCycle?: string
  description?: string
  publishStatus: string
  approvalStatus: string
  secretFlag?: number
  versionNo?: number
  sourcePathType?: string
  qualityScore?: number
  metadataEntryCode?: string
}

interface VersionRow {
  id: number
  resourceId: number
  versionNo: number
  changeSummary?: string
  publishedBy?: string
  publishedAt?: string
}

interface VersionDiff {
  leftNo?: number
  rightNo?: number
  sameSnapshot?: boolean
  basicDiff?: Array<{ field: string; left: string; right: string }>
}

const SHARE_ZH: Record<string, string> = {
  OPEN: '无条件共享',
  CONDITIONAL: '有条件共享',
  NOT_SHARE: '不予共享',
}
const FORMAT_ZH: Record<string, string> = {
  DATABASE: '数据库',
  FILE: '文件',
  API: '接口',
  OTHER: '其他',
}
const CYCLE_ZH: Record<string, string> = {
  REALTIME: '实时',
  DAILY: '每日',
  WEEKLY: '每周',
  MONTHLY: '每月',
  YEARLY: '每年',
}
const TYPE_ZH: Record<string, string> = { DATA: '数据', SERVICE: '服务' }

const treeData = ref<CategoryNode[]>([])
const resources = ref<CatalogRes[]>([])
const loading = ref(false)
const selectedCategoryId = ref<number | null>(null)
const selectedRows = ref<CatalogRes[]>([])
const keyword = ref('')
const dialogVisible = ref(false)
const editMode = ref(false)
const editingId = ref<number | null>(null)
const versionDrawerVisible = ref(false)
const versionResource = ref<CatalogRes | null>(null)
const versions = ref<VersionRow[]>([])
const versionLeftNo = ref<number | undefined>()
const versionRightNo = ref<number | undefined>()
const versionDiff = ref<VersionDiff | null>(null)
const importVisible = ref(false)
const importFormat = ref<'json' | 'csv'>('json')
const importContent = ref('')
const importLoading = ref(false)

const importPlaceholder = computed(() => {
  if (importFormat.value === 'json') {
    return '[{"resourceName":"示例","resourceType":"DATA"}]'
  }
  return 'resourceCode,resourceName,resourceType\nRES001,示例,DATA'
})

const form = reactive({
  resourceCode: '',
  resourceName: '',
  resourceType: 'DATA',
  categoryId: undefined as number | undefined,
  providerOrg: '',
  resourceFormat: 'DATABASE',
  shareType: 'OPEN',
  updateCycle: 'DAILY',
  description: '',
  secretFlag: 0,
  metadataEntryCode: '',
  sourcePathType: 'DIRECT',
  physicalTableName: '',
  qualityScore: undefined as number | undefined,
})

interface MetaOpt {
  entryCode: string
  entryName: string
  dataLayer?: string
  physicalTableName?: string
  dataSourceId?: number
  ownerName?: string
  sourcePathType?: string
}
const metaOptions = ref<MetaOpt[]>([])
const metaLoading = ref(false)

const flatCategories = computed(() => {
  const out: { id: number; label: string }[] = []
  const walk = (nodes: CategoryNode[], prefix = '') => {
    for (const n of nodes) {
      const label = prefix ? `${prefix} / ${n.categoryName}` : n.categoryName
      out.push({ id: n.id, label })
      if (n.children?.length) walk(n.children, label)
    }
  }
  walk(treeData.value)
  return out
})

async function loadTree() {
  const res = await api.get('/governance/catalog/categories/tree')
  treeData.value = res.data || []
}

async function loadResources() {
  loading.value = true
  try {
    const res = await api.get('/governance/catalog/resources-mgmt', {
      params: {
        categoryId: selectedCategoryId.value || undefined,
        keyword: keyword.value || undefined,
      },
    })
    resources.value = res.data || []
  } catch {
    ElMessage.error('加载资源失败')
  } finally {
    loading.value = false
  }
}

function onTreeClick(data: CategoryNode) {
  selectedCategoryId.value = data.id
  loadResources()
}

function clearCategory() {
  selectedCategoryId.value = null
  loadResources()
}

function resetForm() {
  editMode.value = false
  editingId.value = null
  form.resourceCode = ''
  form.resourceName = ''
  form.resourceType = 'DATA'
  form.categoryId = selectedCategoryId.value || undefined
  form.providerOrg = ''
  form.resourceFormat = 'DATABASE'
  form.shareType = 'OPEN'
  form.updateCycle = 'DAILY'
  form.description = ''
  form.secretFlag = 0
  form.metadataEntryCode = ''
  form.sourcePathType = 'DIRECT'
  form.physicalTableName = ''
  form.qualityScore = undefined
}

async function loadEligibleMeta(keyword?: string) {
  metaLoading.value = true
  try {
    const res = await api.get('/governance/catalog/resources-mgmt/eligible-metadata', {
      params: { keyword: keyword || undefined },
    })
    metaOptions.value = res.data || []
  } finally {
    metaLoading.value = false
  }
}

function onMetaPick(code: string) {
  const m = metaOptions.value.find((x) => x.entryCode === code)
  if (!m) return
  form.metadataEntryCode = m.entryCode
  if (!form.resourceName) form.resourceName = m.entryName
  form.physicalTableName = m.physicalTableName || ''
  form.sourcePathType = m.sourcePathType || 'DIRECT'
  if (m.ownerName) form.providerOrg = m.ownerName
}

function openCreate() {
  resetForm()
  void loadEligibleMeta()
  dialogVisible.value = true
}

function openEdit(row: CatalogRes) {
  if (row.publishStatus === 'PUBLISHED') {
    ElMessage.warning('已发布不可编辑，请先下线')
    return
  }
  editMode.value = true
  editingId.value = row.id
  form.resourceCode = row.resourceCode
  form.resourceName = row.resourceName
  form.resourceType = row.resourceType || 'DATA'
  form.categoryId = row.categoryId
  form.providerOrg = row.providerOrg || ''
  form.resourceFormat = row.resourceFormat || 'DATABASE'
  form.shareType = row.shareType || 'OPEN'
  form.updateCycle = row.updateCycle || 'DAILY'
  form.description = row.description || ''
  form.secretFlag = row.secretFlag || 0
  form.metadataEntryCode = row.metadataEntryCode || ''
  form.sourcePathType = row.sourcePathType || 'DIRECT'
  form.physicalTableName = ''
  form.qualityScore = row.qualityScore
  void loadEligibleMeta(row.metadataEntryCode)
  dialogVisible.value = true
}

async function save() {
  if (!form.metadataEntryCode) {
    ElMessage.warning('请选择已登记的元数据条目')
    return
  }
  if (!form.resourceName) {
    ElMessage.warning('请填写资源名称')
    return
  }
  const payload = { ...form }
  if (editMode.value && editingId.value != null) {
    await api.put(`/governance/catalog/resources-mgmt/${editingId.value}`, payload)
    ElMessage.success('已更新')
  } else {
    await api.post('/governance/catalog/resources-mgmt', payload)
    ElMessage.success('已创建')
  }
  dialogVisible.value = false
  await loadResources()
}

async function removeOne(row: CatalogRes) {
  await ElMessageBox.confirm(`确认删除「${row.resourceName}」？`, '删除确认', { type: 'warning' })
  await api.delete(`/governance/catalog/resources-mgmt/${row.id}`)
  ElMessage.success('已删除')
  await loadResources()
}

async function batchDelete() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先勾选资源')
    return
  }
  await ElMessageBox.confirm(`确认批量删除 ${selectedRows.value.length} 条？`, '批量删除', { type: 'warning' })
  await api.post('/governance/catalog/resources-mgmt/batch-delete', {
    ids: selectedRows.value.map(r => r.id),
  })
  ElMessage.success('已批量删除')
  await loadResources()
}

async function submitApproval(row: CatalogRes) {
  await api.post(`/governance/catalog/resources-mgmt/${row.id}/submit`, {
    actionType: 'PUBLISH',
    comment: '提交发布审批',
  })
  ElMessage.success('已提交发布审批（须先完成目录注册挂载）')
  await loadResources()
}

async function submitOffline(row: CatalogRes) {
  await ElMessageBox.confirm(`确认提交「${row.resourceName}」下线审批？`, '下线审批', { type: 'warning' })
  await api.post(`/governance/catalog/resources-mgmt/${row.id}/submit`, {
    actionType: 'OFFLINE',
    comment: '提交下线审批',
  })
  ElMessage.success('已提交下线审批')
  await loadResources()
}

async function openVersions(row: CatalogRes) {
  versionResource.value = row
  versionLeftNo.value = undefined
  versionRightNo.value = undefined
  versionDiff.value = null
  versionDrawerVisible.value = true
  try {
    versions.value = (await api.get(`/governance/catalog/resources-mgmt/${row.id}/versions`)).data || []
  } catch {
    ElMessage.error('加载版本历史失败')
  }
}

async function compareVersions() {
  if (!versionResource.value || versionLeftNo.value == null || versionRightNo.value == null) {
    ElMessage.warning('请选择两个版本')
    return
  }
  versionDiff.value = (await api.get(
    `/governance/catalog/resources-mgmt/${versionResource.value.id}/versions/diff`,
    { params: { leftNo: versionLeftNo.value, rightNo: versionRightNo.value } },
  )).data
}

function openImport() {
  importFormat.value = 'json'
  importContent.value = ''
  importVisible.value = true
}

async function submitImport() {
  if (!importContent.value.trim()) {
    ElMessage.warning('请粘贴导入内容')
    return
  }
  importLoading.value = true
  try {
    const res = await api.post('/governance/catalog/resources-mgmt/import', {
      format: importFormat.value,
      content: importContent.value.trim(),
    })
    const d = res.data || {}
    ElMessage.success(`导入完成：新增 ${d.created || 0}，更新 ${d.updated || 0}，跳过 ${d.skipped || 0}`)
    if (d.errors?.length) {
      ElMessage.warning(d.errors.slice(0, 3).join('；'))
    }
    importVisible.value = false
    await loadResources()
  } catch {
    ElMessage.error('导入失败')
  } finally {
    importLoading.value = false
  }
}

async function exportResources(format: 'json' | 'csv') {
  try {
    const res = await api.get('/governance/catalog/resources-mgmt/export', {
      params: {
        categoryId: selectedCategoryId.value || undefined,
        format,
      },
    })
    const blob = format === 'csv'
      ? new Blob([String(res.data || '')], { type: 'text/csv;charset=utf-8' })
      : new Blob([JSON.stringify(res.data || [], null, 2)], { type: 'application/json;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `catalog-resources.${format}`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  }
}

onMounted(async () => {
  try {
    await loadTree()
    await loadResources()
  } catch {
    ElMessage.error('加载目录失败')
  }
})
</script>

<template>
  <PageCard title="资源目录编制">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="仅挂载直通源或加工主题/专题资源；过程层 DWD 不可编目。须选择已登记元数据；发布须经审批，禁止直接上架。分类挂载请到「目录注册发布」。"
      style="margin-bottom: 12px"
    />
    <div class="catalog-layout">
      <aside class="catalog-tree">
        <div class="tree-toolbar">
          <span>按分类筛选</span>
          <el-button link type="primary" @click="clearCategory">全部</el-button>
        </div>
        <el-tree
          :data="treeData"
          node-key="id"
          :props="{ label: 'categoryName', children: 'children' }"
          highlight-current
          default-expand-all
          @node-click="onTreeClick"
        />
      </aside>
      <main class="catalog-main">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="关键字" class="portal-field-lg">
            <el-input v-model="keyword" clearable placeholder="编码/名称/提供方" @keyup.enter="loadResources" />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="loadResources">查询</el-button>
            <el-button type="primary" @click="openCreate">新增</el-button>
            <el-button @click="openImport">导入</el-button>
            <el-button @click="exportResources('json')">导出 JSON</el-button>
            <el-button @click="exportResources('csv')">导出 CSV</el-button>
            <el-button type="danger" plain :disabled="!selectedRows.length" @click="batchDelete">批量删除</el-button>
          </el-form-item>
        </el-form>
        <el-table
          v-loading="loading"
          :data="resources"
          stripe
          size="small"
          @selection-change="(rows: CatalogRes[]) => (selectedRows = rows)"
        >
          <el-table-column type="selection" width="42" />
          <el-table-column prop="resourceCode" label="编码" width="130" />
          <el-table-column prop="resourceName" label="名称" min-width="140" />
          <el-table-column label="类型" width="70">
            <template #default="{ row }">{{ TYPE_ZH[row.resourceType] || $statusLabel(row.resourceType) }}</template>
          </el-table-column>
          <el-table-column label="来源路径" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.sourcePathType" size="small" :type="row.sourcePathType === 'PROCESSED' ? 'warning' : 'success'">
                {{ row.sourcePathType === 'PROCESSED' ? '加工共享' : '直通共享' }}
              </el-tag>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="质量分" width="72">
            <template #default="{ row }">{{ row.qualityScore ?? '—' }}</template>
          </el-table-column>
          <el-table-column prop="metadataEntryCode" label="元数据条目" width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.metadataEntryCode || '—' }}</template>
          </el-table-column>
          <el-table-column prop="providerOrg" label="提供方" width="110" show-overflow-tooltip />
          <el-table-column label="格式" width="70">
            <template #default="{ row }">{{ FORMAT_ZH[row.resourceFormat] || $statusLabel(row.resourceFormat) || '—' }}</template>
          </el-table-column>
          <el-table-column label="共享" width="100">
            <template #default="{ row }">{{ SHARE_ZH[row.shareType] || $statusLabel(row.shareType) || '—' }}</template>
          </el-table-column>
          <el-table-column label="发布" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.publishStatus)">{{ statusLabel(row.publishStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="审批" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.approvalStatus)">{{ statusLabel(row.approvalStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link @click="openVersions(row)">版本</el-button>
              <el-button
                v-if="row.publishStatus !== 'PUBLISHED' && row.approvalStatus !== 'PENDING'"
                link
                @click="submitApproval(row)"
              >提交发布</el-button>
              <el-button
                v-if="row.publishStatus === 'PUBLISHED' && row.approvalStatus !== 'PENDING'"
                link
                @click="submitOffline(row)"
              >提交下线</el-button>
              <el-button link type="danger" @click="removeOne(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </main>
    </div>

    <el-dialog v-model="dialogVisible" :title="editMode ? '编辑资源' : '新增资源'" width="600px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="元数据条目" required>
          <el-select
            v-model="form.metadataEntryCode"
            filterable
            remote
            :remote-method="loadEligibleMeta"
            :loading="metaLoading"
            style="width:100%"
            placeholder="搜索已登记可编目对象（排除 DWD）"
            @change="onMetaPick"
          >
            <el-option
              v-for="m in metaOptions"
              :key="m.entryCode"
              :label="`${m.entryName}（${m.entryCode} · ${m.dataLayer || '?'}）`"
              :value="m.entryCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="资源编码"><el-input v-model="form.resourceCode" placeholder="可空，自动生成" /></el-form-item>
        <el-form-item label="资源名称" required><el-input v-model="form.resourceName" /></el-form-item>
        <el-form-item label="来源路径">
          <el-tag>{{ form.sourcePathType === 'PROCESSED' ? '加工共享' : '直通共享' }}</el-tag>
          <span v-if="form.physicalTableName" style="margin-left:8px;color:var(--el-text-color-secondary)">{{ form.physicalTableName }}</span>
        </el-form-item>
        <el-form-item label="资源类型">
          <el-select v-model="form.resourceType" style="width:100%">
            <el-option label="数据" value="DATA" /><el-option label="服务" value="SERVICE" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属分类">
          <el-select v-model="form.categoryId" clearable filterable style="width:100%" placeholder="可后在「目录注册发布」挂载">
            <el-option v-for="c in flatCategories" :key="c.id" :label="c.label" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="提供方"><el-input v-model="form.providerOrg" /></el-form-item>
        <el-form-item label="资源格式">
          <el-select v-model="form.resourceFormat" style="width:100%">
            <el-option v-for="(lab, val) in FORMAT_ZH" :key="val" :label="lab" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item label="共享类型">
          <el-select v-model="form.shareType" style="width:100%">
            <el-option v-for="(lab, val) in SHARE_ZH" :key="val" :label="lab" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item label="更新周期">
          <el-select v-model="form.updateCycle" style="width:100%">
            <el-option v-for="(lab, val) in CYCLE_ZH" :key="val" :label="lab" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item label="质量分">
          <el-input-number v-model="form.qualityScore" :min="0" :max="100" :precision="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="versionDrawerVisible" :title="`版本历史 · ${versionResource?.resourceName || ''}`" size="520px">
      <el-table :data="versions" stripe size="small">
        <el-table-column prop="versionNo" label="版本" width="70" />
        <el-table-column prop="changeSummary" label="摘要" min-width="120" show-overflow-tooltip />
        <el-table-column prop="publishedBy" label="发布人" width="90" />
        <el-table-column prop="publishedAt" label="时间" width="150" />
      </el-table>
      <el-divider />
      <el-form inline class="portal-inline-form">
        <el-form-item label="左版本" class="portal-field-sm">
          <el-select v-model="versionLeftNo" clearable placeholder="v">
            <el-option v-for="v in versions" :key="'l'+v.id" :label="`v${v.versionNo}`" :value="v.versionNo" />
          </el-select>
        </el-form-item>
        <el-form-item label="右版本" class="portal-field-sm">
          <el-select v-model="versionRightNo" clearable placeholder="v">
            <el-option v-for="v in versions" :key="'r'+v.id" :label="`v${v.versionNo}`" :value="v.versionNo" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="compareVersions">对比</el-button>
        </el-form-item>
      </el-form>
      <template v-if="versionDiff">
        <el-alert
          :type="versionDiff.sameSnapshot ? 'success' : 'info'"
          :closable="false"
          :title="versionDiff.sameSnapshot ? '两版本快照相同' : '存在字段差异'"
          style="margin-bottom:12px"
        />
        <el-table :data="versionDiff.basicDiff || []" stripe size="small">
          <el-table-column prop="field" label="字段" width="120" />
          <el-table-column prop="left" label="左版本" />
          <el-table-column prop="right" label="右版本" />
        </el-table>
      </template>
    </el-drawer>

    <el-dialog v-model="importVisible" title="批量导入" width="560px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="格式">
          <el-radio-group v-model="importFormat">
            <el-radio value="json">JSON 数组</el-radio>
            <el-radio value="csv">CSV 文本</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="内容">
          <el-input
            v-model="importContent"
            type="textarea"
            :rows="10"
            :placeholder="importPlaceholder"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="submitImport">导入</el-button>
      </template>
    </el-dialog>
  </PageCard>
</template>

<style scoped>
.catalog-layout {
  display: flex;
  gap: 16px;
  min-height: 420px;
}
.catalog-tree {
  width: 220px;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-lighter);
  padding-right: 12px;
}
.tree-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: 600;
}
.catalog-main {
  flex: 1;
  min-width: 0;
}
</style>
