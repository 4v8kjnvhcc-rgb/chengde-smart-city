<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/api/http'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { formatDateTime } from '@/utils/datetime'
import { statusLabel, statusTagType } from '@/utils/status-label'
import { isEmailList, isMobilePhoneList } from '@/utils/validators'

interface Overview {
  date?: string
  totalExpected?: number
  completed?: number
  running?: number
  waiting?: number
  failed?: number
  estimatedFinishAt?: string
  dsAvailable?: boolean
  message?: string
}

interface InstanceRow {
  id: number
  name?: string
  state?: string
  startTime?: string
  endTime?: string
  pipelineId?: number
  pipelineName?: string
  priority?: string
  projectCode?: number
  stateBucket?: string
  virtual?: boolean
  lastMessage?: string
}

interface PipelinePri {
  id: number
  pipelineName: string
  priority: string
  publishStatus?: string
  scheduleStatus?: string
}

const overviewLoading = ref(false)
const listLoading = ref(false)
const overview = ref<Overview>({})
const rows = ref<InstanceRow[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const query = reactive({
  keyword: '',
  stateType: '',
  priority: '',
})

const priorityDialog = ref(false)
const priorityLoading = ref(false)
const priorityRows = ref<PipelinePri[]>([])

const logVisible = ref(false)
const logLoading = ref(false)
const logTitle = ref('')
const logContent = ref('')
const logType = ref<'PROCESS' | 'ERROR' | 'CLUSTER'>('PROCESS')
const currentRow = ref<InstanceRow | null>(null)

const alertVisible = ref(false)
const alertLoading = ref(false)
const alertSaving = ref(false)
const alertLogs = ref<Array<Record<string, unknown>>>([])
const alertForm = reactive({
  ownerName: '',
  mailEnabled: false,
  mailReceivers: '',
  smsEnabled: false,
  smsPhones: '',
  smsGatewayUrl: '',
  smsSignName: '承德大数据',
  smsTemplateCode: 'SMS_WORKFLOW_ALERT',
})

let timer: ReturnType<typeof setInterval> | null = null

const etaText = computed(() => formatDateTime(overview.value.estimatedFinishAt))

async function loadOverview() {
  overviewLoading.value = true
  try {
    overview.value = (await api.get('/governance/cross-pipelines/monitor/overview')).data || {}
  } catch {
    overview.value = {}
  } finally {
    overviewLoading.value = false
  }
}

async function loadInstances() {
  listLoading.value = true
  try {
    const res = (await api.get('/governance/cross-pipelines/monitor/instances', {
      params: {
        keyword: query.keyword || undefined,
        stateType: query.stateType || undefined,
        priority: query.priority || undefined,
        pageNo: page.value,
        pageSize: pageSize.value,
      },
    })).data || {}
    rows.value = res.records || []
    total.value = res.total || 0
    if (res.message && !(res.records || []).length) {
      overview.value = { ...overview.value, message: String(res.message) }
    }
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    listLoading.value = false
  }
}

async function onQuery() {
  page.value = 1
  await Promise.all([loadOverview(), loadInstances()])
}

function resetQuery() {
  query.keyword = ''
  query.stateType = ''
  query.priority = ''
  page.value = 1
  void onQuery()
}

async function openPriority() {
  priorityDialog.value = true
  priorityLoading.value = true
  try {
    priorityRows.value = (await api.get('/governance/cross-pipelines/monitor/pipelines')).data || []
  } catch {
    ElMessage.error('加载流水线优先级失败')
  } finally {
    priorityLoading.value = false
  }
}

async function savePriority(row: PipelinePri) {
  try {
    await api.put(`/governance/cross-pipelines/monitor/pipelines/${row.id}/priority`, {
      priority: row.priority,
    })
    ElMessage.success('优先级已保存')
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '保存失败')
  }
}

/** 行操作「查看日志」：默认打开过程日志；抽屉内可切换错误/集群日志 */
function openViewLog(row: InstanceRow) {
  void openLog(row, 'PROCESS')
}

async function openLog(row: InstanceRow, type: 'PROCESS' | 'ERROR' | 'CLUSTER') {
  if (row.virtual || !row.id || row.id <= 0) {
    ElMessage.warning('暂无运行日志：该流水线尚未产生实例，请先到「跨模块流水线」点击「执行」')
    return
  }
  currentRow.value = row
  logType.value = type
  logTitle.value = `${row.pipelineName || row.name || '实例'} · 运行日志`
  logVisible.value = true
  await fetchLog()
}

async function fetchLog() {
  logLoading.value = true
  try {
    const instanceId = logType.value === 'CLUSTER' ? (currentRow.value?.id || 0) : currentRow.value?.id
    const res = (await api.get(`/governance/cross-pipelines/monitor/instances/${instanceId}/logs`, {
      params: {
        projectCode: currentRow.value?.projectCode,
        logType: logType.value,
      },
    })).data || {}
    logContent.value = res.content || '—'
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    logContent.value = msg || '加载日志失败'
  } finally {
    logLoading.value = false
  }
}

async function control(row: InstanceRow, action: string) {
  if (row.virtual || !row.id || row.id <= 0) {
    ElMessage.info('尚未产生运行实例，请先在「跨模块流水线」点击「执行」')
    return
  }
  try {
    await api.post(`/governance/cross-pipelines/monitor/instances/${row.id}/control`, {
      projectCode: row.projectCode,
      action,
    })
    ElMessage.success('操作已提交')
    await loadInstances()
    await loadOverview()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '操作失败')
  }
}

async function openAlert() {
  alertVisible.value = true
  alertLoading.value = true
  try {
    const d = (await api.get('/governance/cross-pipelines/monitor/alert/channel')).data || {}
    alertForm.ownerName = d.ownerName || ''
    alertForm.mailEnabled = !!d.mailEnabled
    alertForm.mailReceivers = d.mailReceivers || ''
    alertForm.smsEnabled = !!d.smsEnabled
    alertForm.smsPhones = d.smsPhones || ''
    alertForm.smsGatewayUrl = d.smsGatewayUrl || ''
    alertForm.smsSignName = d.smsSignName || '承德大数据'
    alertForm.smsTemplateCode = d.smsTemplateCode || 'SMS_WORKFLOW_ALERT'
    alertLogs.value = (await api.get('/governance/cross-pipelines/monitor/alert/logs')).data || []
  } catch {
    ElMessage.error('加载告警配置失败')
  } finally {
    alertLoading.value = false
  }
}

async function saveAlert() {
  if (alertForm.mailReceivers.trim() && !isEmailList(alertForm.mailReceivers)) {
    ElMessage.warning('邮箱格式不对')
    return
  }
  if (alertForm.smsPhones.trim() && !isMobilePhoneList(alertForm.smsPhones)) {
    ElMessage.warning('手机号格式不对')
    return
  }
  alertSaving.value = true
  try {
    await api.put('/governance/cross-pipelines/monitor/alert/channel', { ...alertForm })
    ElMessage.success('告警通道已保存')
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '保存失败')
  } finally {
    alertSaving.value = false
  }
}

async function notifyRow(row: InstanceRow) {
  try {
    await api.post('/governance/cross-pipelines/monitor/alert/notify', {
      pipelineId: row.pipelineId,
      instanceId: row.id,
      state: row.state,
    })
    ElMessage.success('已触发告警通知')
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '告警失败')
  }
}

watch([page, pageSize], () => {
  void loadInstances()
})

onMounted(async () => {
  await onQuery()
  timer = setInterval(() => {
    void loadOverview()
    void loadInstances()
  }, 30000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <PageCard>
    <template #header>
      <div class="card-head">
        <div>
          <div class="card-head__title">实时任务监控</div>
        </div>
        <div class="card-head__actions">
          <el-button @click="openPriority">优先级设置</el-button>
          <el-button @click="openAlert">告警配置</el-button>
          <el-button type="primary" :loading="overviewLoading || listLoading" @click="onQuery">刷新</el-button>
        </div>
      </div>
    </template>

    <div v-loading="overviewLoading" class="kpi-row">
      <div class="kpi">
        <div class="kpi__label">今日应执行</div>
        <div class="kpi__value">{{ overview.totalExpected ?? '—' }}</div>
      </div>
      <div class="kpi kpi--ok">
        <div class="kpi__label">已完成</div>
        <div class="kpi__value">{{ overview.completed ?? '—' }}</div>
      </div>
      <div class="kpi kpi--run">
        <div class="kpi__label">执行中</div>
        <div class="kpi__value">{{ overview.running ?? '—' }}</div>
      </div>
      <div class="kpi kpi--wait">
        <div class="kpi__label">待执行</div>
        <div class="kpi__value">{{ overview.waiting ?? '—' }}</div>
      </div>
      <div class="kpi kpi--eta">
        <div class="kpi__label">预计完成时间</div>
        <div class="kpi__value kpi__value--sm">{{ etaText }}</div>
      </div>
    </div>

    <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent="onQuery">
      <el-form-item label="名称" class="portal-field-lg">
        <el-input v-model="query.keyword" clearable placeholder="流水线/实例名" @keyup.enter="onQuery" />
      </el-form-item>
      <el-form-item label="状态" class="portal-field-md">
        <el-select v-model="query.stateType" clearable placeholder="全部">
          <el-option label="待执行" value="WAITING" />
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILURE" />
          <el-option label="执行中" value="RUNNING_EXECUTION" />
          <el-option label="暂停" value="PAUSE" />
          <el-option label="停止" value="STOP" />
        </el-select>
      </el-form-item>
      <el-form-item label="优先级" class="portal-field-md">
        <el-select v-model="query.priority" clearable placeholder="全部">
          <el-option label="最高" value="HIGHEST" />
          <el-option label="高" value="HIGH" />
          <el-option label="中" value="MEDIUM" />
          <el-option label="低" value="LOW" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" native-type="button" :loading="listLoading" @click="onQuery">查询</el-button>
        <el-button native-type="button" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="listLoading" class="portal-table" :data="rows" stripe empty-text="暂无数据：发布后会出现待执行记录；点「执行」后才会有运行实例">
      <el-table-column label="流水线" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.pipelineName || row.name || '—' }}</template>
      </el-table-column>
      <el-table-column label="实例 ID" width="110">
        <template #default="{ row }">{{ row.virtual || !row.id || row.id <= 0 ? '未运行' : row.id }}</template>
      </el-table-column>
      <el-table-column label="优先级" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.priority)" size="small" effect="light">
            {{ statusLabel(row.priority) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.state)" size="small" effect="light">
            {{ statusLabel(row.state) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="开始时间" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
      </el-table-column>
      <el-table-column label="结束时间" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ formatDateTime(row.endTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <div class="op-row">
            <template v-if="row.virtual || !row.id || row.id <= 0">
              <el-button link type="primary" @click="openViewLog(row)">查看日志</el-button>
              <el-button link type="info" disabled>待执行（请先点执行）</el-button>
            </template>
            <template v-else>
              <el-button link type="primary" @click="openViewLog(row)">查看日志</el-button>
              <el-button link type="warning" @click="notifyRow(row)">告警</el-button>
              <el-button link @click="control(row, 'RETRY')">重跑</el-button>
              <el-button link @click="control(row, 'PAUSE')">暂停</el-button>
              <el-button link @click="control(row, 'RESUME')">恢复</el-button>
              <el-button link type="danger" @click="control(row, 'STOP')">停止</el-button>
            </template>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <PortalPagination
      v-model:page="page"
      v-model:page-size="pageSize"
      :total="total"
    />

    <el-dialog v-model="priorityDialog" title="任务执行优先级" width="640px" destroy-on-close>
      <el-table v-loading="priorityLoading" :data="priorityRows" stripe>
        <el-table-column prop="pipelineName" label="流水线" min-width="180" show-overflow-tooltip />
        <el-table-column label="优先级" width="180">
          <template #default="{ row }">
            <el-select v-model="row.priority" style="width: 140px">
              <el-option label="最高（插队）" value="HIGHEST" />
              <el-option label="高" value="HIGH" />
              <el-option label="中（常规）" value="MEDIUM" />
              <el-option label="低" value="LOW" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="savePriority(row)">保存</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="priorityDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="logVisible" :title="logTitle" size="52%" destroy-on-close>
      <div class="log-toolbar">
        <el-radio-group v-model="logType" size="small" @change="fetchLog">
          <el-radio-button value="PROCESS">过程日志</el-radio-button>
          <el-radio-button value="ERROR">错误日志</el-radio-button>
          <el-radio-button value="CLUSTER">集群日志</el-radio-button>
        </el-radio-group>
        <el-button size="small" :loading="logLoading" @click="fetchLog">刷新</el-button>
      </div>
      <pre v-loading="logLoading" class="log-pre">{{ logContent }}</pre>
    </el-drawer>

    <el-drawer v-model="alertVisible" title="运行状态监控告警" size="520px" destroy-on-close>
      <div v-loading="alertLoading" class="alert-panel">
        <el-form label-width="100px">
          <el-form-item label="责任人">
            <el-input v-model="alertForm.ownerName" />
          </el-form-item>
          <el-form-item label="邮件告警">
            <el-switch v-model="alertForm.mailEnabled" active-text="启用" inactive-text="关闭" />
          </el-form-item>
          <el-form-item label="收件人">
            <el-input
              v-model="alertForm.mailReceivers"
              type="textarea"
              :rows="2"
              :disabled="!alertForm.mailEnabled"
              placeholder="多个邮箱用分号分隔"
            />
          </el-form-item>
          <el-form-item label="短信告警">
            <el-switch v-model="alertForm.smsEnabled" active-text="启用" inactive-text="关闭" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input
              v-model="alertForm.smsPhones"
              type="textarea"
              :rows="2"
              :disabled="!alertForm.smsEnabled"
              placeholder="多个号码用分号分隔；当前记推送台账"
            />
          </el-form-item>
          <el-form-item label="短信网关">
            <el-input v-model="alertForm.smsGatewayUrl" :disabled="!alertForm.smsEnabled" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="alertSaving" @click="saveAlert">保存告警通道</el-button>
          </el-form-item>
        </el-form>
        <div class="alert-logs-title">最近告警台账</div>
        <el-table :data="alertLogs" size="small" max-height="280" stripe>
          <el-table-column prop="channel" label="通道" width="70">
            <template #default="{ row }">{{ statusLabel(row.channel) }}</template>
          </el-table-column>
          <el-table-column prop="receivers" label="接收人" min-width="100" show-overflow-tooltip />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column label="时间" width="170" show-overflow-tooltip>
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>
  </PageCard>
</template>

<style scoped>
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  width: 100%;
}
.card-head__title {
  font-size: 16px;
  font-weight: 650;
}
.card-head__actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.kpi-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.kpi {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 12px 14px;
  background: var(--el-bg-color);
}
.kpi--ok {
  background: linear-gradient(180deg, rgba(16, 185, 129, 0.08), transparent 60%);
}
.kpi--run {
  background: linear-gradient(180deg, rgba(37, 99, 235, 0.08), transparent 60%);
}
.kpi--wait {
  background: linear-gradient(180deg, rgba(245, 158, 11, 0.1), transparent 60%);
}
.kpi--eta {
  background: linear-gradient(180deg, rgba(99, 102, 241, 0.08), transparent 60%);
}
.kpi__label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.kpi__value {
  margin-top: 6px;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.2;
}
.kpi__value--sm {
  font-size: 15px;
  font-weight: 600;
}
.op-row {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 4px;
}
.log-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.log-pre {
  margin: 0;
  padding: 12px;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 8px;
  min-height: 360px;
  max-height: calc(100vh - 180px);
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.5;
}
.alert-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.alert-logs-title {
  margin-top: 8px;
  font-weight: 600;
}
@media (max-width: 1100px) {
  .kpi-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
