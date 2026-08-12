<script setup lang="ts">
/**
 * 任务执行 · 执行调度（标书 1498–1507 演示台账）
 * 前端模拟：优先级队列 / 过载保护 / 重要任务旁路 / 负载均衡 / 容量指标。
 */
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { statusLabel, statusTagType } from '@/utils/status-label'

type Priority = 'P0' | 'P1' | 'P2' | 'P3'
type QueueStatus = 'WAITING' | 'RUNNING' | 'SUCCESS' | 'REJECTED' | 'QUEUED'

interface WorkerNode {
  id: string
  name: string
  host: string
  status: 'ONLINE' | 'BUSY' | 'DRAIN'
  cpuPct: number
  memPct: number
  runningTasks: number
  maxSlots: number
  weight: number
}

interface QueueJob {
  id: string
  taskName: string
  priority: Priority
  important: boolean
  status: QueueStatus
  workerId?: string
  workerName?: string
  submittedAt: string
  startedAt?: string
  message: string
}

const STORAGE_KEY = 'chengde.fusion.execute.dispatch.v1'

const workers = ref<WorkerNode[]>([])
const queue = ref<QueueJob[]>([])
const overloadEnabled = ref(true)
const maxClientConcurrent = ref(8)
const clusterSoftLimit = ref(24)
const importantBypass = ref(true)
const loadBalanceEnabled = ref(true)
const submitting = ref(false)
const tickMs = 2800
let timer: ReturnType<typeof setInterval> | null = null

const submitForm = reactive({
  taskName: '',
  priority: 'P2' as Priority,
  important: false,
})

const filter = reactive({ keyword: '', status: '' as '' | QueueStatus })

const filteredQueue = computed(() => {
  const kw = filter.keyword.trim().toLowerCase()
  return queue.value.filter((j) => {
    if (kw && !`${j.taskName} ${j.id} ${j.workerName || ''}`.toLowerCase().includes(kw)) return false
    if (filter.status && j.status !== filter.status) return false
    return true
  })
})

const {
  page,
  pageSize,
  paged,
  total,
  resetPage,
} = useClientPager(filteredQueue)

const clusterRunning = computed(() =>
  workers.value.reduce((s, w) => s + w.runningTasks, 0),
)
const clusterSlots = computed(() =>
  workers.value.reduce((s, w) => s + w.maxSlots, 0),
)
const waitingCount = computed(() =>
  queue.value.filter((j) => j.status === 'WAITING' || j.status === 'QUEUED').length,
)
const overloadActive = computed(() =>
  overloadEnabled.value && clusterRunning.value >= clusterSoftLimit.value,
)
const capacityLabel = computed(() => {
  const used = clusterRunning.value
  const soft = clusterSoftLimit.value
  return `${used} / ${soft}（软上限）· 物理槽位 ${clusterSlots.value}`
})

/** 标书「百万级」容量口径：展示平台设计容量与当前利用率（演示指标） */
const designCapacity = 1_000_000
const utilizationPct = computed(() =>
  Math.min(99.9, Number(((clusterRunning.value / designCapacity) * 100).toFixed(4))),
)

function nowIso() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function jobId() {
  return `FX${Date.now().toString(36).toUpperCase()}${Math.floor(Math.random() * 90 + 10)}`
}

function priorityWeight(p: Priority) {
  if (p === 'P0') return 0
  if (p === 'P1') return 1
  if (p === 'P2') return 2
  return 3
}

function priorityTag(p: Priority) {
  if (p === 'P0') return 'danger'
  if (p === 'P1') return 'warning'
  if (p === 'P2') return ''
  return 'info'
}

function seedDefaults() {
  workers.value = [
    { id: 'w1', name: 'fusion-exec-01', host: '10.10.10.71', status: 'ONLINE', cpuPct: 42, memPct: 58, runningTasks: 3, maxSlots: 8, weight: 10 },
    { id: 'w2', name: 'fusion-exec-02', host: '10.10.10.72', status: 'ONLINE', cpuPct: 61, memPct: 64, runningTasks: 5, maxSlots: 8, weight: 10 },
    { id: 'w3', name: 'fusion-exec-03', host: '10.10.10.73', status: 'BUSY', cpuPct: 78, memPct: 71, runningTasks: 7, maxSlots: 8, weight: 8 },
    { id: 'w4', name: 'fusion-exec-04', host: '10.10.10.74', status: 'ONLINE', cpuPct: 28, memPct: 49, runningTasks: 2, maxSlots: 8, weight: 12 },
  ]
  const t = nowIso()
  queue.value = [
    { id: 'FXDEMO001', taskName: '企业主题宽表落库', priority: 'P1', important: false, status: 'RUNNING', workerId: 'w2', workerName: 'fusion-exec-02', submittedAt: t, startedAt: t, message: '已分配执行节点' },
    { id: 'FXDEMO002', taskName: '项目专题日增量', priority: 'P2', important: false, status: 'WAITING', submittedAt: t, message: '排队等待空闲槽位' },
    { id: 'FXDEMO003', taskName: '资产报告紧急回补', priority: 'P0', important: true, status: 'RUNNING', workerId: 'w4', workerName: 'fusion-exec-04', submittedAt: t, startedAt: t, message: '重要任务旁路并发限制' },
  ]
}

function loadState() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      seedDefaults()
      persist()
      return
    }
    const data = JSON.parse(raw) as {
      workers?: WorkerNode[]
      queue?: QueueJob[]
      overloadEnabled?: boolean
      maxClientConcurrent?: number
      clusterSoftLimit?: number
      importantBypass?: boolean
      loadBalanceEnabled?: boolean
    }
    workers.value = data.workers?.length ? data.workers : []
    queue.value = data.queue || []
    if (!workers.value.length) seedDefaults()
    if (data.overloadEnabled != null) overloadEnabled.value = data.overloadEnabled
    if (data.maxClientConcurrent != null) maxClientConcurrent.value = data.maxClientConcurrent
    if (data.clusterSoftLimit != null) clusterSoftLimit.value = data.clusterSoftLimit
    if (data.importantBypass != null) importantBypass.value = data.importantBypass
    if (data.loadBalanceEnabled != null) loadBalanceEnabled.value = data.loadBalanceEnabled
  } catch {
    seedDefaults()
  }
}

function persist() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({
    workers: workers.value,
    queue: queue.value.slice(0, 80),
    overloadEnabled: overloadEnabled.value,
    maxClientConcurrent: maxClientConcurrent.value,
    clusterSoftLimit: clusterSoftLimit.value,
    importantBypass: importantBypass.value,
    loadBalanceEnabled: loadBalanceEnabled.value,
  }))
}

function pickWorker(): WorkerNode | null {
  const online = workers.value.filter((w) => w.status !== 'DRAIN' && w.runningTasks < w.maxSlots)
  if (!online.length) return null
  if (!loadBalanceEnabled.value) {
    return online[0]
  }
  // 加权：空闲槽位多、CPU 低者优先
  return [...online].sort((a, b) => {
    const scoreA = (a.maxSlots - a.runningTasks) * a.weight - a.cpuPct
    const scoreB = (b.maxSlots - b.runningTasks) * b.weight - b.cpuPct
    return scoreB - scoreA
  })[0]
}

function clientRunningCount() {
  return queue.value.filter((j) => j.status === 'RUNNING').length
}

function tryAdmit(job: QueueJob): { ok: boolean; message: string } {
  if (job.important && importantBypass.value) {
    return { ok: true, message: '重要任务旁路并发限制，直接提交' }
  }
  if (clientRunningCount() >= maxClientConcurrent.value) {
    return { ok: false, message: `客户端并发已满（${maxClientConcurrent.value}），进入等待队列` }
  }
  if (overloadEnabled.value && clusterRunning.value >= clusterSoftLimit.value) {
    return { ok: false, message: `集群过载保护生效（运行 ${clusterRunning.value}≥软上限 ${clusterSoftLimit.value}），暂缓提交` }
  }
  return { ok: true, message: '通过过载与并发检查' }
}

function assignJob(job: QueueJob) {
  const admit = tryAdmit(job)
  if (!admit.ok) {
    job.status = 'WAITING'
    job.message = admit.message
    return
  }
  const worker = pickWorker()
  if (!worker) {
    job.status = 'WAITING'
    job.message = '无可用执行节点槽位，排队中'
    return
  }
  job.status = 'RUNNING'
  job.workerId = worker.id
  job.workerName = worker.name
  job.startedAt = nowIso()
  job.message = loadBalanceEnabled.value
    ? `负载均衡分配至 ${worker.name}（CPU ${worker.cpuPct}%）`
    : `固定策略分配至 ${worker.name}`
  worker.runningTasks = Math.min(worker.maxSlots, worker.runningTasks + 1)
  worker.cpuPct = Math.min(96, worker.cpuPct + 3 + Math.floor(Math.random() * 4))
  if (worker.runningTasks >= worker.maxSlots - 1) worker.status = 'BUSY'
  else worker.status = 'ONLINE'
}

function sortWaiting() {
  const waiting = queue.value.filter((j) => j.status === 'WAITING' || j.status === 'QUEUED')
  waiting.sort((a, b) => {
    if (a.important !== b.important) return a.important ? -1 : 1
    return priorityWeight(a.priority) - priorityWeight(b.priority)
  })
  return waiting
}

function tick() {
  // 轻微节点负载轻微抖动
  for (const w of workers.value) {
    const delta = Math.floor(Math.random() * 5) - 2
    w.cpuPct = Math.max(12, Math.min(96, w.cpuPct + delta))
    w.memPct = Math.max(30, Math.min(92, w.memPct + (Math.floor(Math.random() * 3) - 1)))
  }

  // 完成部分运行中任务，释放槽位
  const running = queue.value.filter((j) => j.status === 'RUNNING')
  for (const job of running) {
    if (Math.random() > 0.35) continue
    job.status = 'SUCCESS'
    job.message = '执行完成'
    const w = workers.value.find((x) => x.id === job.workerId)
    if (w) {
      w.runningTasks = Math.max(0, w.runningTasks - 1)
      w.cpuPct = Math.max(15, w.cpuPct - 6)
      w.status = w.runningTasks >= w.maxSlots - 1 ? 'BUSY' : 'ONLINE'
    }
  }

  // 按优先级从等待队列调度
  for (const job of sortWaiting()) {
    if (job.status !== 'WAITING' && job.status !== 'QUEUED') continue
    assignJob(job)
    if (job.status === 'WAITING') break
  }
  persist()
}

async function submitTask() {
  const name = submitForm.taskName.trim() || `融合执行任务_${new Date().toLocaleTimeString()}`
  submitting.value = true
  try {
    await new Promise((r) => setTimeout(r, 280))
    const job: QueueJob = {
      id: jobId(),
      taskName: name,
      priority: submitForm.priority,
      important: submitForm.important,
      status: 'QUEUED',
      submittedAt: nowIso(),
      message: '已接收，正在准入检查',
    }
    queue.value.unshift(job)

    // 高优先级插队语义：WAITING 列表按优先级排序调度
    const admit = tryAdmit(job)
    if (!admit.ok && !(job.important && importantBypass.value)) {
      job.status = 'WAITING'
      job.message = admit.message
      ElMessage.warning(admit.message)
    } else {
      assignJob(job)
      if (job.status === 'RUNNING') {
        ElMessage.success(job.message)
      } else {
        ElMessage.info(job.message)
      }
    }
    submitForm.taskName = ''
    submitForm.important = false
    resetPage()
    persist()
  } finally {
    submitting.value = false
  }
}

function searchQueue() {
  resetPage()
}

function resetDemo() {
  seedDefaults()
  persist()
  resetPage()
  ElMessage.success('已重置演示数据')
}

onMounted(() => {
  loadState()
  timer = setInterval(tick, tickMs)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="fx-dispatch">
    <div class="fx-kpi">
      <div class="fx-kpi__card">
        <div class="fx-kpi__lab">集群运行 / 软上限</div>
        <div class="fx-kpi__val">{{ clusterRunning }} / {{ clusterSoftLimit }}</div>
        <div class="fx-kpi__sub">{{ capacityLabel }}</div>
      </div>
      <div class="fx-kpi__card" :class="{ warn: overloadActive }">
        <div class="fx-kpi__lab">过载保护</div>
        <div class="fx-kpi__val">{{ overloadActive ? '已触发' : '正常' }}</div>
        <div class="fx-kpi__sub">{{ overloadEnabled ? '已启用准入控制' : '已关闭' }}</div>
      </div>
      <div class="fx-kpi__card">
        <div class="fx-kpi__lab">等待队列</div>
        <div class="fx-kpi__val">{{ waitingCount }}</div>
        <div class="fx-kpi__sub">按优先级 P0→P3 调度</div>
      </div>
      <div class="fx-kpi__card">
        <div class="fx-kpi__lab">设计容量</div>
        <div class="fx-kpi__val">{{ (designCapacity / 10000).toFixed(0) }} 万</div>
        <div class="fx-kpi__sub">当前利用率 {{ utilizationPct }}%</div>
      </div>
    </div>

    <el-row :gutter="12">
      <el-col :span="10">
        <PageCard title="执行策略">
          <el-form label-width="120px" size="small" class="fx-policy">
            <el-form-item label="过载保护">
              <el-switch v-model="overloadEnabled" @change="persist" />
              <span class="fx-inline">集群运行数 ≥ 软上限时拒绝普通提交</span>
            </el-form-item>
            <el-form-item label="集群软上限">
              <el-input-number v-model="clusterSoftLimit" :min="4" :max="200" @change="persist" />
            </el-form-item>
            <el-form-item label="客户端并发">
              <el-input-number v-model="maxClientConcurrent" :min="1" :max="64" @change="persist" />
              <span class="fx-inline">单客户端同时运行上限</span>
            </el-form-item>
            <el-form-item label="重要任务旁路">
              <el-switch v-model="importantBypass" @change="persist" />
              <span class="fx-inline">重要任务不受并发/过载限制，优先直提</span>
            </el-form-item>
            <el-form-item label="负载均衡">
              <el-switch v-model="loadBalanceEnabled" @change="persist" />
              <span class="fx-inline">按空闲槽位与 CPU 分配到不同执行节点</span>
            </el-form-item>
          </el-form>
        </PageCard>
      </el-col>
      <el-col :span="14">
        <PageCard title="提交任务（优先级队列）">
          <el-form inline class="portal-inline-form portal-inline-form--block">
            <el-form-item label="任务名称" class="portal-field-xl">
              <el-input v-model="submitForm.taskName" clearable placeholder="如：主题库日批加工" />
            </el-form-item>
            <el-form-item label="优先级" class="portal-field-sm">
              <el-select v-model="submitForm.priority">
                <el-option label="P0 最高" value="P0" />
                <el-option label="P1 高" value="P1" />
                <el-option label="P2 普通" value="P2" />
                <el-option label="P3 低" value="P3" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-checkbox v-model="submitForm.important">重要任务</el-checkbox>
            </el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button type="primary" :loading="submitting" @click="submitTask">提交执行</el-button>
              <el-button @click="resetDemo">重置演示</el-button>
            </el-form-item>
          </el-form>
          <div v-if="overloadActive" class="fx-banner">
            集群过载保护已触发：普通任务将进入等待队列；勾选「重要任务」可旁路限制优先执行。
          </div>
        </PageCard>
      </el-col>
    </el-row>

    <PageCard title="执行节点（负载均衡）" style="margin-top: 12px">
      <el-table :data="workers" stripe size="small">
        <el-table-column prop="name" label="节点" min-width="130" />
        <el-table-column prop="host" label="地址" width="130" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="CPU" width="140">
          <template #default="{ row }">
            <el-progress :percentage="row.cpuPct" :stroke-width="10" :status="row.cpuPct > 80 ? 'exception' : undefined" />
          </template>
        </el-table-column>
        <el-table-column label="内存" width="140">
          <template #default="{ row }">
            <el-progress :percentage="row.memPct" :stroke-width="10" />
          </template>
        </el-table-column>
        <el-table-column label="运行/槽位" width="100">
          <template #default="{ row }">{{ row.runningTasks }} / {{ row.maxSlots }}</template>
        </el-table-column>
        <el-table-column prop="weight" label="权重" width="70" />
      </el-table>
    </PageCard>

    <PageCard title="执行队列" style="margin-top: 12px">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="任务" class="portal-field-lg">
          <el-input v-model="filter.keyword" clearable placeholder="名称 / 编号" @keyup.enter="searchQueue" />
        </el-form-item>
        <el-form-item label="状态" class="portal-field-md">
          <el-select v-model="filter.status" clearable placeholder="全部">
            <el-option label="等待" value="WAITING" />
            <el-option label="排队" value="QUEUED" />
            <el-option label="运行中" value="RUNNING" />
            <el-option label="成功" value="SUCCESS" />
            <el-option label="拒绝" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="searchQueue">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="paged" stripe size="small">
        <el-table-column prop="id" label="任务编号" width="130" show-overflow-tooltip />
        <el-table-column prop="taskName" label="任务名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="优先级" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="priorityTag(row.priority)">{{ row.priority }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="重要" width="70">
          <template #default="{ row }">{{ row.important ? '是' : '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="workerName" label="执行节点" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.workerName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="submittedAt" label="提交时间" width="170" />
        <el-table-column prop="message" label="调度说明" min-width="200" show-overflow-tooltip />
      </el-table>
      <PortalPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
    </PageCard>
  </div>
</template>

<style scoped>
.fx-dispatch {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.fx-kpi {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}
.fx-kpi__card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 12px 14px;
}
.fx-kpi__card.warn {
  border-color: var(--el-color-warning-light-5);
  background: var(--el-color-warning-light-9);
}
.fx-kpi__lab {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.fx-kpi__val {
  margin-top: 4px;
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;
}
.fx-kpi__sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}
.fx-inline {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.fx-policy :deep(.el-form-item) {
  margin-bottom: 12px;
}
.fx-banner {
  margin-top: 4px;
  padding: 8px 12px;
  border-radius: 6px;
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning-dark-2);
  font-size: 13px;
}
@media (max-width: 1100px) {
  .fx-kpi {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
