<script setup lang="ts">
/**
 * 流程调度控制（演示）：ETL 调度中注入稽核门禁，关键问题自动停流，处理后继续。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { statusLabel } from '@/utils/status-label'

type GateStatus = 'RUNNING' | 'PAUSED' | 'PASSED' | 'WAITING'

interface GateRow {
  id: number
  flowName: string
  auditNode: string
  etlJob: string
  status: GateStatus
  criticalIssue: string
  pausedAt?: string
  owner: string
  notify: string
}

const STORAGE_KEY = 'quality_monitor_schedule_demo_v1'
const keyword = ref('')
const dialogVisible = ref(false)

const form = reactive({
  flowName: '',
  auditNode: '',
  etlJob: '',
  owner: '',
  notify: 'EMAIL',
})

const rows = ref<GateRow[]>([])

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return rows.value
  return rows.value.filter(
    (r) =>
      r.flowName.toLowerCase().includes(kw)
      || r.etlJob.toLowerCase().includes(kw)
      || r.auditNode.toLowerCase().includes(kw),
  )
})

const { page, pageSize, paged, total, resetPage } = useClientPager(filtered)

const pausedCount = computed(() => rows.value.filter((r) => r.status === 'PAUSED').length)
const runningCount = computed(() => rows.value.filter((r) => r.status === 'RUNNING').length)

function nowStr() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function seed() {
  if (rows.value.length) return
  rows.value = [
    {
      id: 1,
      flowName: '人口主题融合日批',
      auditNode: '融合前完整性稽核',
      etlJob: 'kettle_pop_dws_daily',
      status: 'PAUSED',
      criticalIssue: '主键空值 128 行，阻断后续 DWS 写入',
      pausedAt: nowStr(),
      owner: '张工',
      notify: 'EMAIL+SMS',
    },
    {
      id: 2,
      flowName: '法人库 ODS→DWD',
      auditNode: '一致性门禁',
      etlJob: 'ds_legal_ods_clean',
      status: 'RUNNING',
      criticalIssue: '—',
      owner: '李工',
      notify: 'EMAIL',
    },
    {
      id: 3,
      flowName: '宏观指标 ADS 刷新',
      auditNode: '准确性波动校验',
      etlJob: 'kettle_macro_ads',
      status: 'PASSED',
      criticalIssue: '—',
      owner: '王工',
      notify: 'IM',
    },
  ]
}

function persist() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(rows.value))
  } catch {
    /* ignore */
  }
}

function load() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) rows.value = JSON.parse(raw)
  } catch {
    /* ignore */
  }
  seed()
  resetPage()
}

function gateStatusLabel(s: GateStatus) {
  const map: Record<GateStatus, string> = {
    RUNNING: '加工中',
    PAUSED: '已停流',
    PASSED: '稽核通过',
    WAITING: '待稽核',
  }
  return map[s] || statusLabel(s)
}

function openCreate() {
  form.flowName = ''
  form.auditNode = ''
  form.etlJob = ''
  form.owner = ''
  form.notify = 'EMAIL'
  dialogVisible.value = true
}

function submit() {
  if (!form.flowName.trim() || !form.auditNode.trim() || !form.etlJob.trim()) {
    ElMessage.warning('请填写流程、稽核节点与 ETL 作业')
    return
  }
  const id = Math.max(0, ...rows.value.map((r) => r.id)) + 1
  rows.value.unshift({
    id,
    flowName: form.flowName.trim(),
    auditNode: form.auditNode.trim(),
    etlJob: form.etlJob.trim(),
    status: 'WAITING',
    criticalIssue: '—',
    owner: form.owner.trim() || '未指定',
    notify: form.notify,
  })
  persist()
  dialogVisible.value = false
  ElMessage.success('已注入稽核门禁（演示）')
  resetPage()
}

async function simulateCritical(row: GateRow) {
  row.status = 'PAUSED'
  row.criticalIssue = '演示：检出关键质量问题，加工流程已自动停止'
  row.pausedAt = nowStr()
  persist()
  ElMessage.warning(`流程「${row.flowName}」已停流，已模拟推送 ${row.notify}`)
}

async function resume(row: GateRow) {
  await ElMessageBox.confirm('确认问题已处理完毕，继续执行 ETL？', '恢复加工', { type: 'info' })
  row.status = 'RUNNING'
  row.criticalIssue = '—'
  row.pausedAt = undefined
  persist()
  ElMessage.success('ETL 流程已继续执行（演示）')
}

async function removeRow(row: GateRow) {
  await ElMessageBox.confirm(`删除门禁「${row.flowName}」？`, '确认', { type: 'warning' })
  rows.value = rows.value.filter((r) => r.id !== row.id)
  persist()
  resetPage()
}

onMounted(load)
</script>

<template>
  <div class="qm-panel">
    <div class="qm-stats">
      <div class="qm-stat"><span>门禁流程</span><b>{{ rows.length }}</b></div>
      <div class="qm-stat tone-run"><span>加工中</span><b>{{ runningCount }}</b></div>
      <div class="qm-stat tone-pause"><span>已停流</span><b>{{ pausedCount }}</b></div>
    </div>

    <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent="resetPage">
      <el-form-item label="关键字" class="portal-field-lg">
        <el-input v-model="keyword" clearable placeholder="流程 / 作业 / 稽核节点" @keyup.enter="resetPage" />
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="resetPage">查询</el-button>
        <el-button type="primary" @click="openCreate">+ 注入稽核门禁</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="paged" stripe border>
      <el-table-column prop="flowName" label="加工流程" min-width="150" show-overflow-tooltip />
      <el-table-column prop="auditNode" label="稽核节点" min-width="140" show-overflow-tooltip />
      <el-table-column prop="etlJob" label="ETL 作业" min-width="140" show-overflow-tooltip />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="row.status === 'PAUSED' ? 'danger' : row.status === 'RUNNING' ? 'success' : 'info'"
          >
            {{ gateStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="criticalIssue" label="关键问题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="owner" label="责任人" width="90" />
      <el-table-column label="通知" width="110">
        <template #default="{ row }">{{ row.notify === 'EMAIL' ? '邮箱' : row.notify === 'IM' ? '即时消息' : '邮箱+短信' }}</template>
      </el-table-column>
      <el-table-column prop="pausedAt" label="停流时间" width="170" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status !== 'PAUSED'"
            link
            type="warning"
            @click="simulateCritical(row)"
          >
            模拟关键停流
          </el-button>
          <el-button v-if="row.status === 'PAUSED'" link type="primary" @click="resume(row)">
            问题已处理·继续
          </el-button>
          <el-button link type="danger" @click="removeRow(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <PortalPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />

    <el-dialog v-model="dialogVisible" title="注入稽核门禁" width="520px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="加工流程" required>
          <el-input v-model="form.flowName" placeholder="如：人口主题融合日批" />
        </el-form-item>
        <el-form-item label="稽核节点" required>
          <el-input v-model="form.auditNode" placeholder="如：融合前完整性稽核" />
        </el-form-item>
        <el-form-item label="ETL 作业" required>
          <el-input v-model="form.etlJob" placeholder="Kettle / DolphinScheduler 作业名" />
        </el-form-item>
        <el-form-item label="责任人">
          <el-input v-model="form.owner" />
        </el-form-item>
        <el-form-item label="问题推送">
          <el-select v-model="form.notify" style="width: 100%">
            <el-option label="邮箱" value="EMAIL" />
            <el-option label="短信" value="SMS" />
            <el-option label="邮箱+短信" value="EMAIL+SMS" />
            <el-option label="即时消息" value="IM" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.qm-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.qm-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.qm-stat {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: var(--el-bg-color);
}
.qm-stat span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.qm-stat b {
  font-size: 22px;
}
.qm-stat.tone-run b {
  color: #059669;
}
.qm-stat.tone-pause b {
  color: #dc2626;
}
</style>
