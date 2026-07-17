<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'

interface TaskRow {
  id: number
  taskCode: string
  taskName: string
  description?: string
  status: string
  engineType?: string
  lockedBy?: string
  lastRunAt?: string
  lastMessage?: string
  updatedAt?: string
  scheduleEnabled?: boolean
  scheduleCron?: string
  nextRunAt?: string
}

const emit = defineEmits<{
  design: [id: number]
  monitor: [id: number]
}>()

const tasks = ref<TaskRow[]>([])
const loading = ref(false)
const createVisible = ref(false)
const renameVisible = ref(false)
const renameId = ref<number | null>(null)
const scheduleVisible = ref(false)
const scheduleId = ref<number | null>(null)
const selectedIds = ref<number[]>([])
const varDialogVisible = ref(false)
const runTargetId = ref<number | null>(null)
const varDefs = ref<Array<{ name: string; label?: string; defaultValue?: string; required?: boolean }>>([])
const varForm = ref<Record<string, string>>({})

const form = reactive({
  taskName: '',
  description: '',
})

const renameForm = reactive({
  taskName: '',
})

const scheduleForm = reactive({
  scheduleEnabled: false,
  scheduleMode: 'CRON' as 'CRON' | 'SIMPLE',
  scheduleCron: '0 0 2 * * ?',
  startTime: '',
  timeUnit: 'DAY',
  intervalValue: 1,
  nextRunAt: '',
})

async function load() {
  loading.value = true
  try {
    tasks.value = (await api.get('/governance/gov-tasks')).data || []
  } catch {
    ElMessage.error('加载治理任务失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.taskName = ''
  form.description = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!form.taskName.trim()) {
    ElMessage.warning('请输入任务名称')
    return
  }
  const id = (await api.post('/governance/gov-tasks', {
    taskName: form.taskName.trim(),
    description: form.description || undefined,
  })).data
  ElMessage.success('已创建')
  createVisible.value = false
  await load()
  openDesign(id as number)
}

function openDesign(id: number) {
  emit('design', id)
}

function openMonitor(id: number) {
  emit('monitor', id)
}

function openRename(row: TaskRow) {
  renameId.value = row.id
  renameForm.taskName = row.taskName
  renameVisible.value = true
}

async function submitRename() {
  if (!renameId.value || !renameForm.taskName.trim()) return
  await api.post(`/governance/gov-tasks/${renameId.value}/rename`, {
    taskName: renameForm.taskName.trim(),
  })
  ElMessage.success('已重命名')
  renameVisible.value = false
  await load()
}

async function lockTask(row: TaskRow) {
  await api.post(`/governance/gov-tasks/${row.id}/lock`)
  ElMessage.success('已锁定')
  await load()
}

async function unlockTask(row: TaskRow) {
  await api.post(`/governance/gov-tasks/${row.id}/unlock`)
  ElMessage.success('已解锁')
  await load()
}

async function runTask(row: TaskRow) {
  try {
    const vres = await api.get(`/governance/tasks/${row.id}/variables`)
    const list = (vres.data || []) as Array<{ name: string; label?: string; defaultValue?: string; required?: boolean }>
    if (list.length > 0) {
      runTargetId.value = row.id
      varDefs.value = list
      varForm.value = {}
      list.forEach((v) => { varForm.value[v.name] = v.defaultValue || '' })
      varDialogVisible.value = true
      return
    }
    const res = await api.post(`/governance/kettle/tasks/${row.id}/execute`)
    ElMessage.success(res.data?.message || '执行已启动')
    await load()
    openMonitor(row.id)
  } catch {
    ElMessage.error('启动执行失败')
  }
}

async function confirmVarRun() {
  if (!runTargetId.value) return
  for (const v of varDefs.value) {
    if (v.required && !String(varForm.value[v.name] || '').trim()) {
      ElMessage.warning(`请填写变量：${v.label || v.name}`)
      return
    }
  }
  try {
    const res = await api.post(`/governance/kettle/tasks/${runTargetId.value}/execute`, { ...varForm.value })
    ElMessage.success(res.data?.message || '执行已启动')
    varDialogVisible.value = false
    const id = runTargetId.value
    runTargetId.value = null
    await load()
    openMonitor(id)
  } catch {
    ElMessage.error('启动执行失败')
  }
}

async function stopTask(row: TaskRow) {
  await api.post(`/governance/gov-tasks/${row.id}/stop`)
  ElMessage.success('已停止')
  await load()
}

async function removeTask(row: TaskRow) {
  await ElMessageBox.confirm(`确认删除任务「${row.taskName}」？`, '删除确认', { type: 'warning' })
  await api.delete(`/governance/gov-tasks/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

async function batchDelete() {
  await ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 个任务？`, '批量删除确认', { type: 'warning' })
  try {
    await api.post('/governance/gov-tasks/batch-delete', { ids: selectedIds.value })
    ElMessage.success(`已删除 ${selectedIds.value.length} 个任务`)
    selectedIds.value = []
    await load()
  } catch {
    ElMessage.error('删除失败')
  }
}

function openSchedule(row: TaskRow) {
  scheduleId.value = row.id
  scheduleForm.scheduleEnabled = !!row.scheduleEnabled
  scheduleForm.scheduleMode = (row as TaskRow & { scheduleMode?: string }).scheduleMode === 'SIMPLE' ? 'SIMPLE' : 'CRON'
  scheduleForm.scheduleCron = row.scheduleCron || '0 0 2 * * ?'
  scheduleForm.startTime = (row as TaskRow & { startTime?: string }).startTime || ''
  scheduleForm.timeUnit = (row as TaskRow & { timeUnit?: string }).timeUnit || 'DAY'
  scheduleForm.intervalValue = Number((row as TaskRow & { intervalValue?: number }).intervalValue || 1)
  scheduleForm.nextRunAt = row.nextRunAt || ''
  scheduleVisible.value = true
}

async function submitSchedule() {
  if (!scheduleId.value) return
  if (scheduleForm.scheduleEnabled) {
    if (scheduleForm.scheduleMode === 'CRON' && !scheduleForm.scheduleCron.trim()) {
      ElMessage.warning('请填写 Cron 表达式')
      return
    }
    if (scheduleForm.scheduleMode === 'SIMPLE' && !scheduleForm.startTime) {
      ElMessage.warning('请选择起始时间')
      return
    }
  }
  const res = await api.put(`/governance/gov-tasks/${scheduleId.value}/schedule`, {
    scheduleEnabled: scheduleForm.scheduleEnabled,
    scheduleMode: scheduleForm.scheduleMode,
    scheduleCron: scheduleForm.scheduleCron.trim(),
    startTime: scheduleForm.startTime || undefined,
    timeUnit: scheduleForm.timeUnit,
    intervalValue: scheduleForm.intervalValue,
  })
  scheduleForm.nextRunAt = res.data?.nextRunAt || ''
  ElMessage.success('定时配置已保存')
  scheduleVisible.value = false
  await load()
}

onMounted(load)

defineExpose({ reload: load })
</script>

<template>
  <PageCard title="数据治理 · ETL任务">
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="openCreate">新建任务</el-button>
        <el-button @click="load">刷新</el-button>
        <el-button
          type="danger"
          :disabled="selectedIds.length === 0"
          @click="batchDelete"
        >
          批量删除 ({{ selectedIds.length }})
        </el-button>
      </el-form-item>
    </el-form>

    <el-table
      v-loading="loading"
      :data="tasks"
      stripe
      size="small"
      @selection-change="(val: TaskRow[]) => selectedIds = val.map(r => r.id)"
    >
      <el-table-column type="selection" width="55" />
      <el-table-column prop="taskCode" label="编码" width="180" />
      <el-table-column prop="taskName" label="名称" min-width="140" />
      <el-table-column label="引擎" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.engineType === 'IN_MEMORY' ? 'info' : 'success'">
            {{ row.engineType === 'IN_MEMORY' ? '内存' : 'Kettle' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lockedBy" label="锁定人" width="100" />
      <el-table-column label="定时" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.scheduleEnabled" type="success" size="small">已启用</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="nextRunAt" label="下次运行" width="150" />
      <el-table-column prop="lastMessage" label="最近结果" min-width="140" show-overflow-tooltip />
      <el-table-column prop="lastRunAt" label="最近运行" width="150" />
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDesign(row.id)">开发</el-button>
          <el-button link @click="openMonitor(row.id)">监控</el-button>
          <el-button link @click="openSchedule(row)">定时</el-button>
          <el-button link @click="openRename(row)">重命名</el-button>
          <el-button v-if="row.status !== 'LOCKED'" link @click="lockTask(row)">锁定</el-button>
          <el-button v-else link @click="unlockTask(row)">解锁</el-button>
          <el-button v-if="row.status === 'RUNNING'" link type="warning" @click="stopTask(row)">停止</el-button>
          <el-button v-else link type="success" @click="runTask(row)">运行</el-button>
          <el-button link type="danger" @click="removeTask(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createVisible" title="新建治理任务" width="420px">
      <el-form label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="form.taskName" maxlength="128" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">创建并设计</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="renameVisible" title="重命名" width="400px">
      <el-input v-model="renameForm.taskName" maxlength="128" />
      <template #footer>
        <el-button @click="renameVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRename">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="scheduleVisible" title="定时计划" width="520px">
      <el-form label-width="100px">
        <el-form-item label="启用定时">
          <el-switch v-model="scheduleForm.scheduleEnabled" />
        </el-form-item>
        <el-form-item v-if="scheduleForm.scheduleEnabled" label="调度方式">
          <el-radio-group v-model="scheduleForm.scheduleMode">
            <el-radio-button value="SIMPLE">简单间隔</el-radio-button>
            <el-radio-button value="CRON">自定义脚本</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <template v-if="scheduleForm.scheduleEnabled && scheduleForm.scheduleMode === 'SIMPLE'">
          <el-form-item label="起始时间">
            <el-date-picker
              v-model="scheduleForm.startTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择起始时间"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="偏移量">
            <el-input-number v-model="scheduleForm.intervalValue" :min="1" :max="999" />
          </el-form-item>
          <el-form-item label="时间单位">
            <el-radio-group v-model="scheduleForm.timeUnit">
              <el-radio value="HOUR">小时</el-radio>
              <el-radio value="DAY">天</el-radio>
              <el-radio value="WEEK">周</el-radio>
              <el-radio value="MONTH">月</el-radio>
            </el-radio-group>
          </el-form-item>
        </template>
        <template v-if="scheduleForm.scheduleEnabled && scheduleForm.scheduleMode === 'CRON'">
          <el-form-item label="Cron 表达式">
            <el-input
              v-model="scheduleForm.scheduleCron"
              placeholder="0 0 2 * * ?"
              class="portal-field-cron"
            />
          </el-form-item>
          <el-form-item label="常用模板">
            <el-button size="small" @click="scheduleForm.scheduleCron = '0 0 2 * * ?'">每天2点</el-button>
            <el-button size="small" @click="scheduleForm.scheduleCron = '0 0 */1 * * ?'">每小时</el-button>
            <el-button size="small" @click="scheduleForm.scheduleCron = '0 0 0 ? * MON'">每周一</el-button>
          </el-form-item>
        </template>
        <el-form-item v-if="scheduleForm.nextRunAt" label="下次运行">
          <span>{{ scheduleForm.nextRunAt }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scheduleVisible = false">取消</el-button>
        <el-button type="primary" @click="submitSchedule">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="varDialogVisible" title="添加变量" width="480px">
      <el-form label-width="100px">
        <el-form-item
          v-for="v in varDefs"
          :key="v.name"
          :label="v.label || v.name"
          :required="!!v.required"
        >
          <el-input v-model="varForm[v.name]" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="varDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmVarRun">运行</el-button>
      </template>
    </el-dialog>
  </PageCard>
</template>
