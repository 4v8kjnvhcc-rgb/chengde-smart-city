<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'
import { ingestionApi, type DataSource } from '@/views/exchange/ingestion/useIngestionHub'

interface TaskRow {
  id: number
  taskName: string
  description?: string
  status: string
  lastScore?: number
  lastMessage?: string
  lastRunAt?: string
  scheduleType?: string
  cronExpr?: string
  datasourceId?: number
  detailCount?: number
}

interface DetailRow {
  id: number
  taskId: number
  ruleId: number
  targetTable?: string
  targetColumn?: string
  checkType?: string
  status?: string
}

interface RuleOpt {
  id: number
  ruleName: string
  ruleCode: string
  config?: { checkType?: string; targetTable?: string; targetColumn?: string } | null
}

const tasks = ref<TaskRow[]>([])
const loading = ref(false)
const drawer = ref(false)
const dataSources = ref<DataSource[]>([])
const rules = ref<RuleOpt[]>([])
const details = ref<DetailRow[]>([])

const form = reactive({
  id: null as number | null,
  taskName: '',
  description: '',
  scheduleType: 'MANUAL',
  cronExpr: '',
  datasourceId: undefined as number | undefined,
})

const detailForm = reactive({
  ruleId: undefined as number | undefined,
  targetTable: '',
  targetColumn: '',
  checkType: '',
})

async function loadTasks() {
  loading.value = true
  try {
    tasks.value = (await api.get('/governance/quality/task-mgmt')).data || []
  } catch {
    ElMessage.error('加载任务失败')
  } finally {
    loading.value = false
  }
}

async function loadRules() {
  rules.value = (await api.get('/governance/quality/rule-mgmt')).data || []
}

async function loadDataSources() {
  try {
    dataSources.value = (await ingestionApi.dataSources()).data || []
  } catch {
    dataSources.value = []
  }
}

function openCreate() {
  form.id = null
  form.taskName = ''
  form.description = ''
  form.scheduleType = 'MANUAL'
  form.cronExpr = ''
  form.datasourceId = undefined
  details.value = []
  drawer.value = true
  void loadRules()
  void loadDataSources()
}

async function openEdit(row: TaskRow) {
  form.id = row.id
  form.taskName = row.taskName
  form.description = row.description || ''
  form.scheduleType = row.scheduleType || 'MANUAL'
  form.cronExpr = row.cronExpr || ''
  form.datasourceId = row.datasourceId
  drawer.value = true
  await Promise.all([loadRules(), loadDataSources(), loadDetails(row.id)])
}

async function loadDetails(taskId: number) {
  details.value = (await api.get(`/governance/quality/task-mgmt/${taskId}/details`)).data || []
}

async function saveTask() {
  if (!form.taskName.trim()) {
    ElMessage.warning('请填写任务名称')
    return
  }
  const body = {
    taskName: form.taskName,
    description: form.description,
    scheduleType: form.scheduleType,
    cronExpr: form.cronExpr || null,
    datasourceId: form.datasourceId ?? null,
  }
  if (form.id) {
    await api.put(`/governance/quality/task-mgmt/${form.id}`, body)
    ElMessage.success('任务已更新')
  } else {
    const id = (await api.post('/governance/quality/task-mgmt', body)).data
    form.id = id
    ElMessage.success('任务已创建')
  }
  await loadTasks()
}

async function addDetail() {
  if (!form.id) {
    ElMessage.warning('请先保存任务')
    return
  }
  if (!detailForm.ruleId) {
    ElMessage.warning('请选择规则')
    return
  }
  await api.post(`/governance/quality/task-mgmt/${form.id}/details`, {
    ruleId: detailForm.ruleId,
    targetTable: detailForm.targetTable || null,
    targetColumn: detailForm.targetColumn || null,
    checkType: detailForm.checkType || null,
  })
  ElMessage.success('明细已添加')
  detailForm.ruleId = undefined
  detailForm.targetTable = ''
  detailForm.targetColumn = ''
  detailForm.checkType = ''
  await loadDetails(form.id)
  await loadTasks()
}

function onRulePick(ruleId: number) {
  const r = rules.value.find((x) => x.id === ruleId)
  if (r?.config) {
    detailForm.checkType = r.config.checkType || ''
    detailForm.targetTable = r.config.targetTable || ''
    detailForm.targetColumn = r.config.targetColumn || ''
  }
}

async function removeDetail(detailId: number) {
  if (!form.id) return
  await api.delete(`/governance/quality/task-mgmt/${form.id}/details/${detailId}`)
  ElMessage.success('已删除明细')
  await loadDetails(form.id)
  await loadTasks()
}

async function removeTask(id: number) {
  await ElMessageBox.confirm('确认删除该质量任务及明细？', '删除确认', { type: 'warning' })
  await api.delete(`/governance/quality/task-mgmt/${id}`)
  ElMessage.success('已删除')
  if (form.id === id) drawer.value = false
  await loadTasks()
}

async function runTask(id: number) {
  const res = await api.post(`/governance/quality/task-mgmt/${id}/run`)
  ElMessage.success(`执行完成 · 评分 ${res.data.score}`)
  await loadTasks()
}

async function stopTask(id: number) {
  await api.post(`/governance/quality/task-mgmt/${id}/stop`)
  ElMessage.success('已停止')
  await loadTasks()
}

function checkTypeLabel(v?: string) {
  return ({
    NULL_CHECK: '空值检查',
    UNIQUENESS: '唯一性',
    ACCURACY: '准确性',
    RECORD_COUNT: '记录数',
  } as Record<string, string>)[v || ''] || v || '—'
}

onMounted(loadTasks)
</script>

<template>
  <PageCard title="质量任务配置">
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="openCreate">新建任务</el-button>
        <el-button @click="loadTasks">刷新</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tasks" stripe size="small">
      <el-table-column prop="taskName" label="任务" min-width="140" />
      <el-table-column label="调度" width="100">
        <template #default="{ row }">{{ row.scheduleType === 'CRON' ? '定时' : '手动' }}</template>
      </el-table-column>
      <el-table-column prop="detailCount" label="规则数" width="70" />
      <el-table-column prop="lastScore" label="最近评分" width="90" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastMessage" label="最近消息" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link @click="openEdit(row)">配置</el-button>
          <el-button link type="primary" @click="runTask(row.id)">执行</el-button>
          <el-button link @click="stopTask(row.id)">停止</el-button>
          <el-button link type="danger" @click="removeTask(row.id)">删</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="drawer" :title="form.id ? '配置质量任务' : '新建质量任务'" size="520px" destroy-on-close>
      <el-form label-width="96px">
        <el-form-item label="任务名称" required>
          <el-input v-model="form.taskName" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="数据源">
          <el-select v-model="form.datasourceId" clearable filterable placeholder="空=平台库" style="width: 100%">
            <el-option v-for="s in dataSources" :key="s.id" :label="s.sourceName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="调度方式">
          <el-select v-model="form.scheduleType" style="width: 160px">
            <el-option label="手动" value="MANUAL" />
            <el-option label="定时" value="CRON" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.scheduleType === 'CRON'" label="Cron">
          <el-input v-model="form.cronExpr" placeholder="0 0 2 * * ?" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveTask">保存任务</el-button>
        </el-form-item>
      </el-form>

      <el-divider content-position="left">稽核明细（数据源/表/字段/规则）</el-divider>
      <el-form v-if="form.id" label-width="96px">
        <el-form-item label="规则">
          <el-select v-model="detailForm.ruleId" filterable style="width: 100%" @change="onRulePick">
            <el-option v-for="r in rules" :key="r.id" :label="`${r.ruleName}（${r.ruleCode}）`" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="检查类型">
          <el-select v-model="detailForm.checkType" clearable style="width: 100%">
            <el-option label="空值检查" value="NULL_CHECK" />
            <el-option label="唯一性" value="UNIQUENESS" />
            <el-option label="准确性" value="ACCURACY" />
            <el-option label="记录数" value="RECORD_COUNT" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标表">
          <el-input v-model="detailForm.targetTable" placeholder="可覆盖规则配置" />
        </el-form-item>
        <el-form-item label="目标字段">
          <el-input v-model="detailForm.targetColumn" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="addDetail">添加明细</el-button>
        </el-form-item>
      </el-form>
      <el-alert v-else type="info" :closable="false" title="请先保存任务后再添加明细" style="margin-bottom: 12px" />

      <el-table :data="details" stripe size="small">
        <el-table-column prop="ruleId" label="规则ID" width="80" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ checkTypeLabel(row.checkType) }}</template>
        </el-table-column>
        <el-table-column prop="targetTable" label="表" min-width="120" />
        <el-table-column prop="targetColumn" label="字段" width="100" />
        <el-table-column label="操作" width="70">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeDetail(row.id)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </PageCard>
</template>
