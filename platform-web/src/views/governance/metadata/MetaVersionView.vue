<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'

interface MetaModel {
  id: number
  modelNameZh: string
  status?: string
}

interface CatalogEntry {
  id: number
  entryCode: string
  entryName: string
  entryType: string
}

interface Version {
  id: number
  targetType: string
  targetId: number
  versionNo: number
  changeSummary?: string
  createdBy?: string
  createdAt?: string
  snapshotJson?: string
}

interface FieldDiff {
  added?: string[]
  removed?: string[]
  changed?: string[]
}

const models = ref<MetaModel[]>([])
const tableEntries = ref<CatalogEntry[]>([])
const versions = ref<Version[]>([])
const compare = ref<{
  sameSnapshot?: boolean
  basicDiff?: Array<{ field: string; left: string; right: string }>
  fieldDiff?: FieldDiff
} | null>(null)

const form = reactive({
  targetType: 'MODEL' as 'MODEL' | 'ENTRY',
  targetId: undefined as number | undefined,
  leftId: undefined as number | undefined,
  rightId: undefined as number | undefined,
})

const targetOptions = computed(() => {
  if (form.targetType === 'MODEL') {
    return models.value.map(m => ({ id: m.id, label: m.modelNameZh }))
  }
  return tableEntries.value.map(e => ({ id: e.id, label: `${e.entryName}（${e.entryCode}）` }))
})

const targetLabel = computed(() => (form.targetType === 'MODEL' ? '元模型' : '元数据条目'))

async function loadModels() {
  models.value = (await api.get('/governance/platform/metadata/models')).data || []
}

async function loadTableEntries() {
  const res = await api.get('/governance/platform/metadata/catalog/search', {
    params: { type: 'asset' },
  })
  tableEntries.value = ((res.data || []) as CatalogEntry[]).filter(e => e.entryType === 'TABLE')
}

async function loadVersions() {
  compare.value = null
  form.leftId = undefined
  form.rightId = undefined
  if (!form.targetId) {
    versions.value = []
    return
  }
  versions.value = (await api.get('/governance/platform/metadata/versions', {
    params: { targetType: form.targetType, targetId: form.targetId },
  })).data || []
}

async function doCompare() {
  if (!form.leftId || !form.rightId) {
    ElMessage.warning('请选择两个版本')
    return
  }
  compare.value = (await api.get('/governance/platform/metadata/versions/compare', {
    params: { leftId: form.leftId, rightId: form.rightId },
  })).data
}

async function subscribe() {
  if (!form.targetId) {
    ElMessage.warning(`请先选择${targetLabel.value}`)
    return
  }
  await api.post('/governance/platform/metadata/subscriptions', {
    targetType: form.targetType,
    targetId: form.targetId,
    channel: 'NOTICE',
  })
  ElMessage.success('已订阅变更通知')
}

async function rollback(version: Version) {
  try {
    await ElMessageBox.confirm(
      `确定回滚到版本 v${version.versionNo}？此操作将恢复该版本快照。`,
      '版本回滚确认',
      { type: 'warning', confirmButtonText: '确认回滚', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await api.post(`/governance/platform/metadata/versions/${version.id}/rollback`)
  ElMessage.success(`已回滚至 v${version.versionNo}`)
  await loadVersions()
}

function onTargetTypeChange() {
  form.targetId = undefined
  versions.value = []
  compare.value = null
  if (form.targetType === 'ENTRY' && !tableEntries.value.length) {
    loadTableEntries()
  }
}

watch(() => form.targetId, () => {
  if (form.targetId) loadVersions()
})

onMounted(loadModels)
</script>

<template>
  <PageCard title="元数据版本管理">
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="目标类型" class="portal-field-sm">
        <el-select v-model="form.targetType" @change="onTargetTypeChange">
          <el-option label="元模型" value="MODEL" />
          <el-option label="元数据条目" value="ENTRY" />
        </el-select>
      </el-form-item>
      <el-form-item :label="targetLabel" class="portal-field-xl">
        <el-select v-model="form.targetId" clearable filterable placeholder="请选择">
          <el-option v-for="o in targetOptions" :key="o.id" :label="o.label" :value="o.id" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button @click="loadVersions">刷新历史</el-button>
        <el-button type="primary" :disabled="!form.targetId" @click="subscribe">订阅变更</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="versions" stripe size="small">
      <el-table-column prop="versionNo" label="版本" width="80">
        <template #default="{ row }">v{{ row.versionNo }}</template>
      </el-table-column>
      <el-table-column prop="changeSummary" label="变更摘要" show-overflow-tooltip />
      <el-table-column prop="createdBy" label="操作人" width="100" />
      <el-table-column prop="createdAt" label="时间" width="170" />
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button link type="warning" @click="rollback(row)">回滚</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-divider />
    <el-form inline class="portal-inline-form">
      <el-form-item label="左版本" class="portal-field-md">
        <el-select v-model="form.leftId" clearable placeholder="基准版本">
          <el-option v-for="v in versions" :key="v.id" :label="`v${v.versionNo}`" :value="v.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="右版本" class="portal-field-md">
        <el-select v-model="form.rightId" clearable placeholder="对比版本">
          <el-option v-for="v in versions" :key="'r' + v.id" :label="`v${v.versionNo}`" :value="v.id" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="doCompare">版本对比</el-button>
      </el-form-item>
    </el-form>

    <template v-if="compare">
      <el-alert
        type="info"
        :closable="false"
        :title="compare.sameSnapshot ? '两版本快照内容相同' : '两版本存在差异'"
        style="margin-bottom: 12px"
      />
      <h4>基本字段差异</h4>
      <el-table :data="compare.basicDiff || []" stripe size="small" style="margin-bottom: 16px">
        <el-table-column prop="field" label="字段" width="140" />
        <el-table-column prop="left" label="左版本" show-overflow-tooltip />
        <el-table-column prop="right" label="右版本" show-overflow-tooltip />
      </el-table>
      <h4>字段级差异</h4>
      <div class="version-diff-bars">
        <div v-for="f in compare.fieldDiff?.added || []" :key="'a' + f" class="version-diff-bar version-diff-bar--add">+ {{ f }}</div>
        <div v-for="f in compare.fieldDiff?.removed || []" :key="'r' + f" class="version-diff-bar version-diff-bar--remove">− {{ f }}</div>
        <div v-for="f in compare.fieldDiff?.changed || []" :key="'c' + f" class="version-diff-bar version-diff-bar--change">~ {{ f }}</div>
        <el-empty
          v-if="!(compare.fieldDiff?.added?.length || compare.fieldDiff?.removed?.length || compare.fieldDiff?.changed?.length)"
          description="无字段差异"
        />
      </div>
    </template>
  </PageCard>
</template>

<style scoped>
.version-diff-bars { display: flex; flex-direction: column; gap: 6px; }
.version-diff-bar { padding: 6px 10px; border-radius: 4px; font-family: monospace; font-size: 13px; }
.version-diff-bar--add { background: #e8f5e9; color: #2e7d32; border-left: 4px solid #4caf50; }
.version-diff-bar--remove { background: #ffebee; color: #c62828; border-left: 4px solid #f44336; }
.version-diff-bar--change { background: #fff8e1; color: #f57f17; border-left: 4px solid #ffc107; }
</style>
