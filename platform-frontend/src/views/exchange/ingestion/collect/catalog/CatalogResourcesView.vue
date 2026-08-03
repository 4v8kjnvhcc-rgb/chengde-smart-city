<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { ingestionRegisterCache } from '../../ingestion-register-cache'
import {
  ingestionApi,
  useIngestionLoading,
  type DataSource,
  type DataTable,
  type Registry,
} from '../../useIngestionHub'

const { loading, loadError, withLoad } = useIngestionLoading()
const rows = ref<Registry[]>([])
const selected = ref<Registry[]>([])
const dataSources = ref<DataSource[]>([])
const tables = ref<DataTable[]>([])

const query = reactive({
  keyword: '',
  approvalStatus: '',
  shareType: '',
})

const dialogVisible = ref(false)
const detailVisible = ref(false)
const importVisible = ref(false)
const batchVisible = ref(false)
const editingId = ref<number | null>(null)
const detail = ref<Registry | null>(null)
const importText = ref('')
const batchTableIds = ref<number[]>([])

const form = reactive({
  title: '',
  resourceCode: '',
  providerOrg: '',
  resourceFormat: 'DATABASE',
  shareType: 'CONDITIONAL',
  updateCycle: 'MONTHLY',
  secretLevel: 'INTERNAL',
  description: '',
  categoryPath: '',
  refSourceId: undefined as number | undefined,
  refTableId: undefined as number | undefined,
  assetSummary: '',
})

const filteredTables = computed(() => {
  if (!form.refSourceId) return tables.value
  return tables.value.filter((t) => t.sourceId === form.refSourceId)
})

async function load() {
  await withLoad(async () => {
    const res = await ingestionApi.registries({
      keyword: query.keyword || undefined,
      approvalStatus: query.approvalStatus || undefined,
      shareType: query.shareType || undefined,
    })
    rows.value = res.data || []
  })
}

async function loadRefs() {
  const [ds, tb] = await Promise.all([
    ingestionRegisterCache.dataSources(),
    ingestionRegisterCache.tables(),
  ])
  dataSources.value = ds
  tables.value = tb
}

function resetForm() {
  Object.assign(form, {
    title: '',
    resourceCode: '',
    providerOrg: '',
    resourceFormat: 'DATABASE',
    shareType: 'CONDITIONAL',
    updateCycle: 'MONTHLY',
    secretLevel: 'INTERNAL',
    description: '',
    categoryPath: '',
    refSourceId: undefined,
    refTableId: undefined,
    assetSummary: '',
  })
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: Registry) {
  editingId.value = row.id
  Object.assign(form, {
    title: row.title || '',
    resourceCode: row.resourceCode || '',
    providerOrg: row.providerOrg || '',
    resourceFormat: row.resourceFormat || 'DATABASE',
    shareType: row.shareType || 'CONDITIONAL',
    updateCycle: row.updateCycle || 'MONTHLY',
    secretLevel: row.secretLevel || 'INTERNAL',
    description: row.description || '',
    categoryPath: row.categoryPath || '',
    refSourceId: row.refSourceId,
    refTableId: row.refTableId,
    assetSummary: row.assetSummary || '',
  })
  dialogVisible.value = true
}

async function openDetail(row: Registry) {
  const res = await ingestionApi.registryDetail(row.id)
  detail.value = res.data
  detailVisible.value = true
}

async function save() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写信息资源名称')
    return
  }
  const body = { ...form, title: form.title.trim() }
  if (editingId.value == null) {
    await ingestionApi.createRegistry(body)
    ElMessage.success('新增成功')
  } else {
    await ingestionApi.updateRegistry(editingId.value, body)
    ElMessage.success('保存成功')
  }
  dialogVisible.value = false
  await load()
}

async function remove(row: Registry) {
  await ElMessageBox.confirm(`确认删除「${row.title}」？`, '删除', { type: 'warning' })
  await ingestionApi.deleteRegistry(row.id)
  ElMessage.success('已删除')
  await load()
}

async function doImport() {
  const lines = importText.value
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter(Boolean)
  if (!lines.length) {
    ElMessage.warning('请粘贴 CSV：title,resourceCode,providerOrg,shareType')
    return
  }
  const rowsData: Record<string, unknown>[] = []
  const start = lines[0].toLowerCase().includes('title') ? 1 : 0
  for (let i = start; i < lines.length; i++) {
    const cols = lines[i].split(',').map((c) => c.trim())
    rowsData.push({
      title: cols[0],
      resourceCode: cols[1] || undefined,
      providerOrg: cols[2] || undefined,
      shareType: cols[3] || 'CONDITIONAL',
    })
  }
  const res = await ingestionApi.importRegistries({ rows: rowsData })
  const d = res.data || { success: 0, failed: 0, errors: [] }
  ElMessage.success(`导入成功 ${d.success} 条，失败 ${d.failed} 条`)
  if (d.errors?.length) ElMessage.warning(d.errors.slice(0, 3).join('；'))
  importVisible.value = false
  await load()
}

async function doBatchFromTables() {
  if (!batchTableIds.value.length) {
    ElMessage.warning('请选择库表')
    return
  }
  const items = batchTableIds.value.map((tid) => {
    const tb = tables.value.find((t) => t.id === tid)
    const ds = dataSources.value.find((s) => s.id === tb?.sourceId)
    return {
      title: tb?.tableName || `表${tid}`,
      resourceCode: tb?.tableCode,
      providerOrg: ds?.sourceName,
      resourceFormat: 'DATABASE',
      refTableId: tid,
      refSourceId: tb?.sourceId,
      assetSummary: tb ? `关联表 ${tb.tableName}` : '',
    }
  })
  await ingestionApi.batchCreateRegistry({ items })
  ElMessage.success(`已批量编目 ${items.length} 条`)
  batchVisible.value = false
  batchTableIds.value = []
  await load()
}

function exportCsv() {
  const header = 'title,resourceCode,providerOrg,shareType,approvalStatus,publishStatus\n'
  const body = rows.value
    .map((r) =>
      [r.title, r.resourceCode || r.registryCode, r.providerOrg || '', r.shareType || '', r.approvalStatus, r.publishStatus]
        .map((v) => `"${String(v).replace(/"/g, '""')}"`)
        .join(','),
    )
    .join('\n')
  const blob = new Blob([header + body], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `data-resources-${Date.now()}.csv`
  a.click()
  URL.revokeObjectURL(a.href)
}

onMounted(async () => {
  await loadRefs()
  await load()
})
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard title="数据资源编目管理">
      <el-form inline class="portal-inline-form">
        <el-form-item label="名称">
          <el-input v-model="query.keyword" clearable placeholder="名称/代码" style="width:160px" />
        </el-form-item>
        <el-form-item label="审核状态">
          <el-select v-model="query.approvalStatus" clearable placeholder="全部" style="width:120px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="待审批" value="PENDING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已拒绝" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="共享方式">
          <el-select v-model="query.shareType" clearable placeholder="全部" style="width:130px">
            <el-option label="无条件共享" value="OPEN" />
            <el-option label="有条件共享" value="CONDITIONAL" />
            <el-option label="不予共享" value="NOT_SHARE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button type="primary" @click="openCreate">手动新增</el-button>
          <el-button @click="batchVisible = true">批量新增</el-button>
          <el-button @click="importVisible = true">批量导入</el-button>
          <el-button @click="exportCsv">批量导出</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="rows" border stripe @selection-change="(v: Registry[]) => (selected = v)">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="title" label="信息资源名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="代码" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.resourceCode || row.registryCode }}</template>
        </el-table-column>
        <el-table-column prop="providerOrg" label="提供方" width="120" show-overflow-tooltip />
        <el-table-column prop="shareType" label="共享方式" width="110" />
        <el-table-column prop="approvalStatus" label="审核状态" width="100" />
        <el-table-column prop="publishStatus" label="发布状态" width="110" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button link type="primary" :disabled="row.publishStatus === 'PUBLISHED' || row.approvalStatus === 'PENDING'" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" :disabled="row.publishStatus === 'PUBLISHED' || row.approvalStatus === 'PENDING'" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <el-dialog v-model="dialogVisible" :title="editingId == null ? '手动新增数据资源' : '编辑数据资源'" width="640px" destroy-on-close>
      <el-form label-width="120px">
        <el-form-item label="信息资源名称" required>
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="信息资源代码">
          <el-input v-model="form.resourceCode" placeholder="可空，默认自动生成" />
        </el-form-item>
        <el-form-item label="提供方">
          <el-input v-model="form.providerOrg" />
        </el-form-item>
        <el-form-item label="资源格式">
          <el-select v-model="form.resourceFormat" style="width:100%">
            <el-option label="数据库" value="DATABASE" />
            <el-option label="文件" value="FILE" />
            <el-option label="接口" value="API" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="共享属性">
          <el-select v-model="form.shareType" style="width:100%">
            <el-option label="无条件共享" value="OPEN" />
            <el-option label="有条件共享" value="CONDITIONAL" />
            <el-option label="不予共享" value="NOT_SHARE" />
          </el-select>
        </el-form-item>
        <el-form-item label="更新周期">
          <el-select v-model="form.updateCycle" style="width:100%">
            <el-option label="实时" value="REALTIME" />
            <el-option label="每日" value="DAILY" />
            <el-option label="每周" value="WEEKLY" />
            <el-option label="每月" value="MONTHLY" />
            <el-option label="每年" value="YEARLY" />
          </el-select>
        </el-form-item>
        <el-form-item label="涉密等级">
          <el-select v-model="form.secretLevel" style="width:100%">
            <el-option label="内部" value="INTERNAL" />
            <el-option label="秘密" value="SECRET" />
            <el-option label="机密" value="CONFIDENTIAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联数据源">
          <el-select v-model="form.refSourceId" clearable filterable style="width:100%">
            <el-option v-for="s in dataSources" :key="s.id" :label="s.sourceName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联库表">
          <el-select v-model="form.refTableId" clearable filterable style="width:100%">
            <el-option v-for="t in filteredTables" :key="t.id" :label="t.tableName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="数据资源详情" width="560px">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="名称">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="代码">{{ detail.resourceCode || detail.registryCode }}</el-descriptions-item>
        <el-descriptions-item label="提供方">{{ detail.providerOrg || '-' }}</el-descriptions-item>
        <el-descriptions-item label="格式">{{ detail.resourceFormat }}</el-descriptions-item>
        <el-descriptions-item label="共享">{{ detail.shareType }}</el-descriptions-item>
        <el-descriptions-item label="周期">{{ detail.updateCycle }}</el-descriptions-item>
        <el-descriptions-item label="分类路径">{{ detail.categoryPath || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核/发布">{{ detail.approvalStatus }} / {{ detail.publishStatus }}</el-descriptions-item>
        <el-descriptions-item label="摘要">{{ detail.assetSummary || detail.description || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="importVisible" title="批量导入" width="560px">
      <el-input v-model="importText" type="textarea" :rows="10" placeholder="CSV：title,resourceCode,providerOrg,shareType" />
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" @click="doImport">导入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchVisible" title="批量新增（从库表抽取）" width="560px">
      <el-select v-model="batchTableIds" multiple filterable style="width:100%" placeholder="选择已登记库表">
        <el-option v-for="t in tables" :key="t.id" :label="`${t.tableName} (#${t.id})`" :value="t.id" />
      </el-select>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" @click="doBatchFromTables">生成编目</el-button>
      </template>
    </el-dialog>
  </div>
</template>
