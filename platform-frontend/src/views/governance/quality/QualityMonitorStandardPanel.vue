<script setup lang="ts">
/**
 * 数据标准监控（演示）
 * 1）命名标准监控：表/脚本/工作流命名校验任务与结果
 * 2）数据标准监控：标准映射表任务与问题下钻
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { statusLabel, statusTagType } from '@/utils/status-label'

type NameType = 'TABLE' | 'SCRIPT' | 'WORKFLOW'
type Schedule = 'ONCE' | 'HOUR' | 'DAY' | 'WEEK' | 'MONTH' | 'CRON'
type RunResult = 'PASS' | 'FAIL' | 'RUNNING' | 'PENDING'

interface NameTask {
  id: number
  name: string
  nameType: NameType
  standard: string
  targets: string
  schedule: Schedule
  cron?: string
  status: 'ENABLED' | 'DISABLED'
  updatedAt: string
}

interface NameRun {
  id: number
  taskId: number
  taskName: string
  auditType: string
  result: RunResult
  objectCount: number
  issueCount: number
  startedAt: string
  endedAt: string
  durationSec: number
}

interface NameIssue {
  objectName: string
  project: string
  creator: string
  rule: string
  checkContent: string
  detail: string
}

interface StdTask {
  id: number
  name: string
  tableName: string
  standardCode: string
  source: 'AUTO_MAP' | 'MANUAL'
  schedule: Schedule
  status: 'ENABLED' | 'DISABLED'
  updatedAt: string
}

interface StdRun {
  id: number
  taskId: number
  taskName: string
  auditType: string
  result: RunResult
  objectCount: number
  issueCount: number
  startedAt: string
  endedAt: string
  durationSec: number
}

interface StdIssue {
  dbName: string
  tableName: string
  columnName: string
  issueValue: string
  rule: string
  checkContent: string
  fixHint: string
}

const NAME_KEY = 'quality_monitor_naming_demo_v1'
const STD_KEY = 'quality_monitor_std_demo_v1'

const subTab = ref('naming')
const nameTasks = ref<NameTask[]>([])
const nameRuns = ref<NameRun[]>([])
const stdTasks = ref<StdTask[]>([])
const stdRuns = ref<StdRun[]>([])

const nameKw = ref('')
const stdKw = ref('')
const nameDialog = ref(false)
const stdDialog = ref(false)
const detailVisible = ref(false)
const detailTitle = ref('')
const detailIssues = ref<(NameIssue | StdIssue)[]>([])
const detailMode = ref<'name' | 'std'>('name')

const nameForm = reactive({
  name: '',
  nameType: 'TABLE' as NameType,
  standard: '',
  targets: '',
  schedule: 'DAY' as Schedule,
  cron: '0 0 2 * * ?',
})

const stdForm = reactive({
  name: '',
  tableName: '',
  standardCode: '',
  schedule: 'DAY' as Schedule,
})

const nameTypeLabel: Record<NameType, string> = {
  TABLE: '表命名',
  SCRIPT: '脚本命名',
  WORKFLOW: '工作流命名',
}

const scheduleLabel: Record<Schedule, string> = {
  ONCE: '一次性',
  HOUR: '按小时',
  DAY: '按天',
  WEEK: '按周',
  MONTH: '按月',
  CRON: '自定义',
}

const resultLabel: Record<RunResult, string> = {
  PASS: '通过',
  FAIL: '未通过',
  RUNNING: '执行中',
  PENDING: '待执行',
}

function nowStr() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function minutesAgo(min: number) {
  const d = new Date(Date.now() - min * 60000)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

const filteredNameTasks = computed(() => {
  const kw = nameKw.value.trim().toLowerCase()
  if (!kw) return nameTasks.value
  return nameTasks.value.filter(
    (t) => t.name.toLowerCase().includes(kw) || t.targets.toLowerCase().includes(kw) || t.standard.toLowerCase().includes(kw),
  )
})

const filteredStdTasks = computed(() => {
  const kw = stdKw.value.trim().toLowerCase()
  if (!kw) return stdTasks.value
  return stdTasks.value.filter(
    (t) => t.name.toLowerCase().includes(kw) || t.tableName.toLowerCase().includes(kw) || t.standardCode.toLowerCase().includes(kw),
  )
})

const {
  page: namePage,
  pageSize: namePageSize,
  paged: pagedNameTasks,
  total: nameTotal,
  resetPage: resetNamePage,
} = useClientPager(filteredNameTasks)

const {
  page: stdPage,
  pageSize: stdPageSize,
  paged: pagedStdTasks,
  total: stdTotal,
  resetPage: resetStdPage,
} = useClientPager(filteredStdTasks)

function seedName() {
  if (nameTasks.value.length) return
  nameTasks.value = [
    {
      id: 1,
      name: 'ODS 表命名日检',
      nameType: 'TABLE',
      standard: 'ods_{业务}_{实体}',
      targets: 'ods_pop_base；ods_legal_ent；tmp_bad_name',
      schedule: 'DAY',
      status: 'ENABLED',
      updatedAt: nowStr(),
    },
    {
      id: 2,
      name: '融合脚本命名校验',
      nameType: 'SCRIPT',
      standard: 'fs_{域}_{动作}_v{n}',
      targets: 'fs_pop_merge_v3；old_script_x',
      schedule: 'WEEK',
      status: 'ENABLED',
      updatedAt: nowStr(),
    },
    {
      id: 3,
      name: '工作流命名规范',
      nameType: 'WORKFLOW',
      standard: 'wf_{系统}_{作业}',
      targets: 'wf_gov_clean；MyFlow1',
      schedule: 'CRON',
      cron: '0 30 1 * * ?',
      status: 'ENABLED',
      updatedAt: nowStr(),
    },
  ]
  nameRuns.value = [
    {
      id: 101,
      taskId: 1,
      taskName: 'ODS 表命名日检',
      auditType: '表命名',
      result: 'FAIL',
      objectCount: 3,
      issueCount: 1,
      startedAt: minutesAgo(40),
      endedAt: minutesAgo(38),
      durationSec: 95,
    },
    {
      id: 102,
      taskId: 2,
      taskName: '融合脚本命名校验',
      auditType: '脚本命名',
      result: 'PASS',
      objectCount: 2,
      issueCount: 0,
      startedAt: minutesAgo(120),
      endedAt: minutesAgo(119),
      durationSec: 42,
    },
  ]
}

function seedStd() {
  if (stdTasks.value.length) return
  stdTasks.value = [
    {
      id: 1,
      name: '性别码表映射稽核',
      tableName: 'dwd_pop_person',
      standardCode: 'GB/T 2261.1 性别',
      source: 'AUTO_MAP',
      schedule: 'DAY',
      status: 'ENABLED',
      updatedAt: nowStr(),
    },
    {
      id: 2,
      name: '行政区划标准对标',
      tableName: 'ods_org_region',
      standardCode: 'GB/T 2260 区划',
      source: 'MANUAL',
      schedule: 'HOUR',
      status: 'ENABLED',
      updatedAt: nowStr(),
    },
  ]
  stdRuns.value = [
    {
      id: 201,
      taskId: 1,
      taskName: '性别码表映射稽核',
      auditType: '数据标准',
      result: 'FAIL',
      objectCount: 1,
      issueCount: 2,
      startedAt: minutesAgo(25),
      endedAt: minutesAgo(23),
      durationSec: 110,
    },
    {
      id: 202,
      taskId: 2,
      taskName: '行政区划标准对标',
      auditType: '数据标准',
      result: 'PASS',
      objectCount: 1,
      issueCount: 0,
      startedAt: minutesAgo(80),
      endedAt: minutesAgo(78),
      durationSec: 88,
    },
  ]
}

function persistName() {
  try {
    localStorage.setItem(NAME_KEY, JSON.stringify({ tasks: nameTasks.value, runs: nameRuns.value }))
  } catch {
    /* ignore */
  }
}

function persistStd() {
  try {
    localStorage.setItem(STD_KEY, JSON.stringify({ tasks: stdTasks.value, runs: stdRuns.value }))
  } catch {
    /* ignore */
  }
}

function load() {
  try {
    const n = localStorage.getItem(NAME_KEY)
    if (n) {
      const d = JSON.parse(n)
      nameTasks.value = d.tasks || []
      nameRuns.value = d.runs || []
    }
    const s = localStorage.getItem(STD_KEY)
    if (s) {
      const d = JSON.parse(s)
      stdTasks.value = d.tasks || []
      stdRuns.value = d.runs || []
    }
  } catch {
    /* ignore */
  }
  seedName()
  seedStd()
}

function openNameCreate() {
  nameForm.name = ''
  nameForm.nameType = 'TABLE'
  nameForm.standard = ''
  nameForm.targets = ''
  nameForm.schedule = 'DAY'
  nameForm.cron = '0 0 2 * * ?'
  nameDialog.value = true
}

function submitName() {
  if (!nameForm.name.trim() || !nameForm.standard.trim() || !nameForm.targets.trim()) {
    ElMessage.warning('请填写任务名称、命名标准与对标对象')
    return
  }
  const id = Math.max(0, ...nameTasks.value.map((t) => t.id)) + 1
  nameTasks.value.unshift({
    id,
    name: nameForm.name.trim(),
    nameType: nameForm.nameType,
    standard: nameForm.standard.trim(),
    targets: nameForm.targets.trim(),
    schedule: nameForm.schedule,
    cron: nameForm.schedule === 'CRON' ? nameForm.cron : undefined,
    status: 'ENABLED',
    updatedAt: nowStr(),
  })
  persistName()
  nameDialog.value = false
  ElMessage.success('命名标准任务已新增（演示）')
  resetNamePage()
}

async function removeName(row: NameTask) {
  await ElMessageBox.confirm(`删除任务「${row.name}」？`, '确认', { type: 'warning' })
  nameTasks.value = nameTasks.value.filter((t) => t.id !== row.id)
  persistName()
  resetNamePage()
}

function runName(row: NameTask) {
  const fail = row.targets.split(/[；;]/).some((t) => /tmp_|old_|MyFlow/i.test(t.trim()))
  const objects = row.targets.split(/[；;]/).map((s) => s.trim()).filter(Boolean)
  const run: NameRun = {
    id: Date.now(),
    taskId: row.id,
    taskName: row.name,
    auditType: nameTypeLabel[row.nameType],
    result: fail ? 'FAIL' : 'PASS',
    objectCount: objects.length,
    issueCount: fail ? 1 : 0,
    startedAt: nowStr(),
    endedAt: nowStr(),
    durationSec: 30 + Math.floor(Math.random() * 40),
  }
  nameRuns.value.unshift(run)
  persistName()
  ElMessage.success(fail ? '执行完成：存在命名不合规对象（演示）' : '执行完成：全部通过（演示）')
  if (fail) openNameDetail(run)
}

function openNameDetail(run: NameRun) {
  detailMode.value = 'name'
  detailTitle.value = `命名标准结果详情 · ${run.taskName}`
  const task = nameTasks.value.find((t) => t.id === run.taskId)
  const bad = (task?.targets || '')
    .split(/[；;]/)
    .map((s) => s.trim())
    .filter((t) => /tmp_|old_|MyFlow/i.test(t))
  detailIssues.value =
    run.issueCount > 0
      ? bad.map((objectName) => ({
          objectName,
          project: '承德智慧城市平台',
          creator: 'demo_user',
          rule: task?.standard || '命名规范',
          checkContent: `对象名称须匹配：${task?.standard || ''}`,
          detail: `「${objectName}」不符合命名标准，请追溯责任人整改`,
        }))
      : []
  detailVisible.value = true
}

function openStdCreate() {
  stdForm.name = ''
  stdForm.tableName = ''
  stdForm.standardCode = ''
  stdForm.schedule = 'DAY'
  stdDialog.value = true
}

function submitStd() {
  if (!stdForm.name.trim() || !stdForm.tableName.trim() || !stdForm.standardCode.trim()) {
    ElMessage.warning('请填写任务名称、对标表与数据标准')
    return
  }
  const id = Math.max(0, ...stdTasks.value.map((t) => t.id)) + 1
  stdTasks.value.unshift({
    id,
    name: stdForm.name.trim(),
    tableName: stdForm.tableName.trim(),
    standardCode: stdForm.standardCode.trim(),
    source: 'MANUAL',
    schedule: stdForm.schedule,
    status: 'ENABLED',
    updatedAt: nowStr(),
  })
  persistStd()
  stdDialog.value = false
  ElMessage.success('数据标准任务已新增（演示）')
  resetStdPage()
}

async function removeStd(row: StdTask) {
  await ElMessageBox.confirm(`删除任务「${row.name}」？`, '确认', { type: 'warning' })
  stdTasks.value = stdTasks.value.filter((t) => t.id !== row.id)
  persistStd()
  resetStdPage()
}

function runStd(row: StdTask) {
  const fail = row.tableName.includes('pop') || row.standardCode.includes('性别')
  const run: StdRun = {
    id: Date.now(),
    taskId: row.id,
    taskName: row.name,
    auditType: '数据标准',
    result: fail ? 'FAIL' : 'PASS',
    objectCount: 1,
    issueCount: fail ? 2 : 0,
    startedAt: nowStr(),
    endedAt: nowStr(),
    durationSec: 50 + Math.floor(Math.random() * 60),
  }
  stdRuns.value.unshift(run)
  persistStd()
  ElMessage.success(fail ? '执行完成：存在标准映射问题（演示）' : '执行完成：全部通过（演示）')
  if (fail) openStdDetail(run)
}

function openStdDetail(run: StdRun) {
  detailMode.value = 'std'
  detailTitle.value = `数据标准结果详情 · ${run.taskName}`
  const task = stdTasks.value.find((t) => t.id === run.taskId)
  detailIssues.value =
    run.issueCount > 0
      ? [
          {
            dbName: 'smart_city_dwd',
            tableName: task?.tableName || '—',
            columnName: 'gender',
            issueValue: '男',
            rule: task?.standardCode || '标准码表',
            checkContent: '性别字段须映射为标准码 1/2/9',
            fixHint: '将「男/女」映射为国标码，并回写清洗规则',
          },
          {
            dbName: 'smart_city_dwd',
            tableName: task?.tableName || '—',
            columnName: 'gender',
            issueValue: '未知',
            rule: task?.standardCode || '标准码表',
            checkContent: '非法码值',
            fixHint: '统一为 9（未说明），并补充字典约束',
          },
        ]
      : []
  detailVisible.value = true
}

function notifyDemo() {
  ElMessage.success('演示：已通过邮件/短信推送异常结果给业务人员')
}

onMounted(load)
</script>

<template>
  <div class="std-mon">
    <el-tabs v-model="subTab" class="std-sub">
      <el-tab-pane label="命名标准监控" name="naming">
        <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent="resetNamePage">
          <el-form-item label="关键字" class="portal-field-lg">
            <el-input v-model="nameKw" clearable placeholder="任务 / 标准 / 对标对象" @keyup.enter="resetNamePage" />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="resetNamePage">查询</el-button>
            <el-button type="primary" @click="openNameCreate">+ 新增命名任务</el-button>
          </el-form-item>
        </el-form>

        <div class="sec-title">任务管理</div>
        <el-table :data="pagedNameTasks" stripe border size="small">
          <el-table-column prop="name" label="任务名称" min-width="150" show-overflow-tooltip />
          <el-table-column label="命名标准类型" width="120">
            <template #default="{ row }">{{ nameTypeLabel[row.nameType as NameType] }}</template>
          </el-table-column>
          <el-table-column prop="standard" label="命名标准" min-width="140" show-overflow-tooltip />
          <el-table-column prop="targets" label="对标对象" min-width="180" show-overflow-tooltip />
          <el-table-column label="调度" width="100">
            <template #default="{ row }">
              {{ scheduleLabel[row.schedule as Schedule] }}
              <span v-if="row.schedule === 'CRON'" class="muted"> {{ row.cron }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="runName(row)">启动</el-button>
              <el-button link type="danger" @click="removeName(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination v-model:page="namePage" v-model:page-size="namePageSize" :total="nameTotal" />

        <div class="sec-title">任务监控</div>
        <el-table :data="nameRuns.slice(0, 20)" stripe border size="small">
          <el-table-column prop="taskName" label="任务名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="auditType" label="稽核类型" width="110" />
          <el-table-column label="稽核结果" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.result === 'PASS' ? 'success' : row.result === 'FAIL' ? 'danger' : 'info'">
                {{ resultLabel[row.result as RunResult] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="objectCount" label="稽核对象个数" width="120" />
          <el-table-column prop="startedAt" label="执行开始时间" width="170" />
          <el-table-column prop="endedAt" label="执行结束时间" width="170" />
          <el-table-column label="执行时长" width="100">
            <template #default="{ row }">{{ row.durationSec }} 秒</template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openNameDetail(row)">结果详情</el-button>
              <el-button v-if="row.result === 'FAIL'" link @click="notifyDemo">推送</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="数据标准监控" name="data-std">
        <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent="resetStdPage">
          <el-form-item label="关键字" class="portal-field-lg">
            <el-input v-model="stdKw" clearable placeholder="任务 / 表 / 标准" @keyup.enter="resetStdPage" />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="resetStdPage">查询</el-button>
            <el-button type="primary" @click="openStdCreate">+ 按表配置标准任务</el-button>
          </el-form-item>
        </el-form>

        <div class="sec-title">任务管理（自动映射 + 按表维护）</div>
        <el-table :data="pagedStdTasks" stripe border size="small">
          <el-table-column prop="name" label="任务名称" min-width="150" show-overflow-tooltip />
          <el-table-column prop="tableName" label="对标表" min-width="140" show-overflow-tooltip />
          <el-table-column prop="standardCode" label="数据标准" min-width="150" show-overflow-tooltip />
          <el-table-column label="来源" width="110">
            <template #default="{ row }">{{ row.source === 'AUTO_MAP' ? '标准映射自动生成' : '按表手工配置' }}</template>
          </el-table-column>
          <el-table-column label="调度" width="90">
            <template #default="{ row }">{{ scheduleLabel[row.schedule as Schedule] }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="runStd(row)">启动</el-button>
              <el-button link type="danger" @click="removeStd(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination v-model:page="stdPage" v-model:page-size="stdPageSize" :total="stdTotal" />

        <div class="sec-title">任务监控</div>
        <el-table :data="stdRuns.slice(0, 20)" stripe border size="small">
          <el-table-column prop="taskName" label="任务名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="auditType" label="稽核类型" width="110" />
          <el-table-column label="稽核结果" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.result === 'PASS' ? 'success' : row.result === 'FAIL' ? 'danger' : 'info'">
                {{ resultLabel[row.result as RunResult] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="objectCount" label="稽核对象个数" width="120" />
          <el-table-column prop="startedAt" label="执行开始时间" width="170" />
          <el-table-column prop="endedAt" label="执行结束时间" width="170" />
          <el-table-column label="执行时长" width="100">
            <template #default="{ row }">{{ row.durationSec }} 秒</template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openStdDetail(row)">结果详情</el-button>
              <el-button v-if="row.result === 'FAIL'" link @click="notifyDemo">推送</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="detailVisible" :title="detailTitle" size="640px">
      <el-empty v-if="!detailIssues.length" description="无问题数据" />
      <el-table v-else :data="detailIssues" stripe border size="small">
        <template v-if="detailMode === 'name'">
          <el-table-column prop="objectName" label="问题对象" min-width="120" />
          <el-table-column prop="project" label="项目" width="140" />
          <el-table-column prop="creator" label="创建人" width="100" />
          <el-table-column prop="rule" label="引用规则" min-width="120" show-overflow-tooltip />
          <el-table-column prop="checkContent" label="校验内容" min-width="140" show-overflow-tooltip />
          <el-table-column prop="detail" label="问题详情" min-width="160" show-overflow-tooltip />
        </template>
        <template v-else>
          <el-table-column prop="dbName" label="数据库" width="130" />
          <el-table-column prop="tableName" label="表" width="130" />
          <el-table-column prop="columnName" label="字段" width="90" />
          <el-table-column prop="issueValue" label="数据值" width="90" />
          <el-table-column prop="rule" label="引用规则" min-width="120" show-overflow-tooltip />
          <el-table-column prop="checkContent" label="校验内容" min-width="140" show-overflow-tooltip />
          <el-table-column prop="fixHint" label="整改要求" min-width="160" show-overflow-tooltip />
        </template>
      </el-table>
    </el-drawer>

    <el-dialog v-model="nameDialog" title="新增命名标准任务" width="560px" destroy-on-close>
      <el-form label-width="120px">
        <el-form-item label="任务名称" required>
          <el-input v-model="nameForm.name" />
        </el-form-item>
        <el-form-item label="命名标准类型" required>
          <el-radio-group v-model="nameForm.nameType">
            <el-radio-button value="TABLE">表</el-radio-button>
            <el-radio-button value="SCRIPT">脚本</el-radio-button>
            <el-radio-button value="WORKFLOW">工作流</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="命名标准" required>
          <el-input v-model="nameForm.standard" placeholder="如：ods_{业务}_{实体}" />
        </el-form-item>
        <el-form-item :label="nameForm.nameType === 'TABLE' ? '选择对标表' : nameForm.nameType === 'SCRIPT' ? '选择对标脚本' : '选择对标工作流'" required>
          <el-input v-model="nameForm.targets" type="textarea" :rows="2" placeholder="多个用分号分隔" />
        </el-form-item>
        <el-form-item label="执行方式" required>
          <el-select v-model="nameForm.schedule" style="width: 100%">
            <el-option v-for="(lab, key) in scheduleLabel" :key="key" :label="lab" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="nameForm.schedule === 'CRON'" label="自定义 Cron">
          <el-input v-model="nameForm.cron" class="portal-field-cron" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nameDialog = false">取消</el-button>
        <el-button type="primary" @click="submitName">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="stdDialog" title="按表配置数据标准任务" width="520px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="任务名称" required>
          <el-input v-model="stdForm.name" />
        </el-form-item>
        <el-form-item label="对标表" required>
          <el-input v-model="stdForm.tableName" placeholder="如：dwd_pop_person" />
        </el-form-item>
        <el-form-item label="数据标准" required>
          <el-input v-model="stdForm.standardCode" placeholder="如：GB/T 2261.1 性别" />
        </el-form-item>
        <el-form-item label="执行方式" required>
          <el-select v-model="stdForm.schedule" style="width: 100%">
            <el-option v-for="(lab, key) in scheduleLabel" :key="key" :label="lab" :value="key" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stdDialog = false">取消</el-button>
        <el-button type="primary" @click="submitStd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.std-mon {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.sec-title {
  margin: 14px 0 8px;
  font-weight: 650;
  font-size: 14px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
