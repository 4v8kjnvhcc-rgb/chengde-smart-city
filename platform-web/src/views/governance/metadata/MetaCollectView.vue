<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'

interface Connector { id: number; connectorName: string; sourceType: string }
interface MetaModel { id: number; modelNameZh: string; status: string }
interface Task {
  id: number
  taskCode: string
  taskName: string
  connectorId: number
  modelId?: number
  cronExpr?: string
  scopeType: string
  tableList?: string
  status: string
  lastMessage?: string
}

const connectors = ref<Connector[]>([])
const models = ref<MetaModel[]>([])
const tasks = ref<Task[]>([])
const filter = reactive({ sourceType: '', keyword: '' })
const editVisible = ref(false)
const editing = ref<Task | null>(null)
const editForm = reactive({
  taskName: '',
  modelId: undefined as number | undefined,
  cronExpr: '',
  scopeType: 'FULL',
  tableList: '',
})

const form = reactive({
  taskName: '',
  connectorId: undefined as number | undefined,
  modelId: undefined as number | undefined,
  cronExpr: '0 0 2 * * ?',
  scopeType: 'FULL',
  tableList: '',
})

const connectorForm = reactive({
  connectorName: '',
  sourceType: 'MySQL',
  jdbcUrl: '',
  jdbcUser: '',
  jdbcPassword: '',
  jdbcDatabase: '',
})

const cronMode = ref<'hour' | 'day' | 'week' | 'month' | 'custom'>('day')
const cronHour = ref(2)
const cronMinute = ref(0)
const cronWeekDay = ref(1)
const cronMonthDay = ref(1)

const cronPreview = computed(() => {
  if (cronMode.value === 'custom') return form.cronExpr
  const m = cronMinute.value
  const h = cronHour.value
  if (cronMode.value === 'hour') return `0 ${m} * * * ?`
  if (cronMode.value === 'day') return `0 ${m} ${h} * * ?`
  if (cronMode.value === 'week') return `0 ${m} ${h} ? * ${cronWeekDay.value}`
  return `0 ${m} ${h} ${cronMonthDay.value} * ?`
})

function applyCronPreview() {
  if (cronMode.value !== 'custom') form.cronExpr = cronPreview.value
}

async function loadConnectors() {
  const ov = await api.get('/governance/platform/metadata/overview')
  connectors.value = ov.data.connectors || []
}

async function loadTasks() {
  tasks.value = (await api.get('/governance/platform/metadata/collect/tasks', {
    params: {
      sourceType: filter.sourceType || undefined,
      keyword: filter.keyword || undefined,
    },
  })).data || []
}

async function loadModels() {
  models.value = (await api.get('/governance/platform/metadata/models', { params: { status: 'PUBLISHED' } })).data || []
}

async function load() {
  await loadConnectors()
  await loadTasks()
}

async function createConnector() {
  if (!connectorForm.connectorName) return
  await api.post('/governance/connectors', { ...connectorForm })
  ElMessage.success('适配器已创建')
  connectorForm.connectorName = ''
  connectorForm.jdbcUrl = ''
  connectorForm.jdbcUser = ''
  connectorForm.jdbcPassword = ''
  connectorForm.jdbcDatabase = ''
  await loadConnectors()
}

async function createTask() {
  if (!form.taskName || !form.connectorId) return
  applyCronPreview()
  await api.post('/governance/platform/metadata/collect/tasks', { ...form })
  ElMessage.success('采集任务已创建')
  form.taskName = ''
  await loadTasks()
}

async function runTask(id: number) {
  const res = await api.post(`/governance/platform/metadata/collect/tasks/${id}/run`)
  ElMessage.success(res.data.message || '采集完成')
  await loadTasks()
}

function openEdit(row: Task) {
  editing.value = row
  editForm.taskName = row.taskName
  editForm.modelId = row.modelId
  editForm.cronExpr = row.cronExpr || ''
  editForm.scopeType = row.scopeType
  editForm.tableList = row.tableList || ''
  editVisible.value = true
}

async function saveEdit() {
  if (!editing.value) return
  await api.put(`/governance/platform/metadata/collect/tasks/${editing.value.id}`, { ...editForm })
  ElMessage.success('任务已更新')
  editVisible.value = false
  await loadTasks()
}

async function removeTask(row: Task) {
  await ElMessageBox.confirm(`确认删除任务「${row.taskName}」？`, '删除确认')
  await api.delete(`/governance/platform/metadata/collect/tasks/${row.id}`)
  ElMessage.success('已删除')
  await loadTasks()
}

onMounted(async () => {
  await loadModels()
  await load()
})
</script>

<template>
  <PageCard title="M090 元数据采集">
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="适配器" class="portal-field-lg"><el-input v-model="connectorForm.connectorName" /></el-form-item>
      <el-form-item label="类型" class="portal-field-sm">
        <el-select v-model="connectorForm.sourceType"><el-option label="MySQL" value="MySQL" /><el-option label="PostgreSQL" value="PostgreSQL" /></el-select>
      </el-form-item>
      <el-form-item label="JDBC URL" class="portal-field-xl"><el-input v-model="connectorForm.jdbcUrl" placeholder="可选" /></el-form-item>
      <el-form-item label="库名" class="portal-field-sm"><el-input v-model="connectorForm.jdbcDatabase" /></el-form-item>
      <el-form-item label="用户" class="portal-field-sm"><el-input v-model="connectorForm.jdbcUser" /></el-form-item>
      <el-form-item label="密码" class="portal-field-sm"><el-input v-model="connectorForm.jdbcPassword" type="password" show-password /></el-form-item>
      <el-form-item class="portal-form-actions"><el-button @click="createConnector">新增适配器</el-button></el-form-item>
    </el-form>
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="数据源类型" class="portal-field-sm">
        <el-select v-model="filter.sourceType" clearable @change="loadTasks">
          <el-option label="MySQL" value="MySQL" /><el-option label="PostgreSQL" value="PostgreSQL" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键字" class="portal-field-md"><el-input v-model="filter.keyword" clearable @keyup.enter="loadTasks" /></el-form-item>
      <el-form-item class="portal-form-actions"><el-button @click="loadTasks">筛选</el-button></el-form-item>
    </el-form>
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="任务名" class="portal-field-lg"><el-input v-model="form.taskName" /></el-form-item>
      <el-form-item label="适配器" class="portal-field-lg">
        <el-select v-model="form.connectorId" clearable>
          <el-option v-for="c in connectors" :key="c.id" :label="c.connectorName" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="已发布模型" class="portal-field-lg">
        <el-select v-model="form.modelId" clearable>
          <el-option v-for="m in models" :key="m.id" :label="m.modelNameZh" :value="m.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="范围" class="portal-field-sm">
        <el-select v-model="form.scopeType"><el-option label="整库" value="FULL" /><el-option label="选表" value="TABLE" /></el-select>
      </el-form-item>
      <el-form-item v-if="form.scopeType === 'TABLE'" label="表清单" class="portal-field-xl">
        <el-input v-model="form.tableList" placeholder="逗号分隔" />
      </el-form-item>
      <el-form-item class="portal-form-actions"><el-button type="primary" @click="createTask">创建任务</el-button></el-form-item>
    </el-form>
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="调度" class="portal-field-sm">
        <el-select v-model="cronMode" @change="applyCronPreview">
          <el-option label="每小时" value="hour" /><el-option label="每天" value="day" />
          <el-option label="每周" value="week" /><el-option label="每月" value="month" /><el-option label="自定义" value="custom" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="cronMode !== 'custom'" label="分" class="portal-field-xs">
        <el-input-number v-model="cronMinute" :min="0" :max="59" size="small" @change="applyCronPreview" />
      </el-form-item>
      <el-form-item v-if="['day','week','month'].includes(cronMode)" label="时" class="portal-field-xs">
        <el-input-number v-model="cronHour" :min="0" :max="23" size="small" @change="applyCronPreview" />
      </el-form-item>
      <el-form-item v-if="cronMode === 'week'" label="周几" class="portal-field-xs">
        <el-input-number v-model="cronWeekDay" :min="1" :max="7" size="small" @change="applyCronPreview" />
      </el-form-item>
      <el-form-item v-if="cronMode === 'month'" label="日" class="portal-field-xs">
        <el-input-number v-model="cronMonthDay" :min="1" :max="28" size="small" @change="applyCronPreview" />
      </el-form-item>
      <el-form-item label="Cron" class="portal-field-cron">
        <el-input v-model="form.cronExpr" :readonly="cronMode !== 'custom'" />
      </el-form-item>
      <el-form-item class="portal-form-actions"><el-button @click="applyCronPreview">生成</el-button></el-form-item>
    </el-form>
    <el-table :data="tasks" stripe size="small">
      <el-table-column prop="taskName" label="任务" />
      <el-table-column prop="scopeType" label="范围" width="80" />
      <el-table-column prop="cronExpr" label="调度" width="130" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastMessage" label="最近结果" min-width="180" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link type="primary" @click="runTask(row.id)">执行</el-button>
          <el-button link @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="removeTask(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="editVisible" title="编辑采集任务" width="520px">
      <el-form label-width="90px">
        <el-form-item label="任务名"><el-input v-model="editForm.taskName" /></el-form-item>
        <el-form-item label="模型">
          <el-select v-model="editForm.modelId" clearable>
            <el-option v-for="m in models" :key="m.id" :label="m.modelNameZh" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Cron"><el-input v-model="editForm.cronExpr" /></el-form-item>
        <el-form-item label="范围">
          <el-select v-model="editForm.scopeType"><el-option label="整库" value="FULL" /><el-option label="选表" value="TABLE" /></el-select>
        </el-form-item>
        <el-form-item v-if="editForm.scopeType === 'TABLE'" label="表清单"><el-input v-model="editForm.tableList" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </PageCard>
</template>
