<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { invalidateExecCycleMap } from '@/utils/exec-cycle-label'

interface ExecCycle {
  id: number
  cycleCode: string
  cycleName: string
  cronExpr: string
  description?: string
  status: string
  sortOrder?: number
}

const loading = ref(false)
const rows = ref<ExecCycle[]>([])
const keyword = ref('')
const selectedIds = ref<number[]>([])
const { page, pageSize, paged, total, resetPage } = useClientPager(rows)
const unitTab = ref<'sec' | 'min' | 'hour' | 'day' | 'month' | 'week' | 'year'>('sec')

const dialog = reactive({
  visible: false,
  editingId: 0 as number,
  cycleCode: '',
  cycleName: '',
  description: '',
  status: 'ACTIVE',
  sortOrder: 100,
})

type FieldMode = 'every' | 'range' | 'step' | 'list'
const fields = reactive({
  sec: { mode: 'every' as FieldMode, from: 0, to: 59, start: 0, interval: 1, list: [] as number[] },
  min: { mode: 'every' as FieldMode, from: 0, to: 59, start: 0, interval: 1, list: [] as number[] },
  hour: { mode: 'every' as FieldMode, from: 0, to: 23, start: 0, interval: 1, list: [] as number[] },
  day: { mode: 'every' as FieldMode, from: 1, to: 31, start: 1, interval: 1, list: [] as number[] },
  month: { mode: 'every' as FieldMode, from: 1, to: 12, start: 1, interval: 1, list: [] as number[] },
  week: { mode: 'unset' as FieldMode | 'unset', from: 1, to: 7, start: 1, interval: 1, list: [] as number[] },
  year: { mode: 'empty' as FieldMode | 'empty', from: 2024, to: 2035, start: 2026, interval: 1, list: [] as number[] },
})

const expressionParts = reactive({
  sec: '*',
  min: '*',
  hour: '*',
  day: '*',
  month: '*',
  week: '?',
  year: '',
})

const cronExpr = computed(() => {
  const base = [
    expressionParts.sec,
    expressionParts.min,
    expressionParts.hour,
    expressionParts.day,
    expressionParts.month,
    expressionParts.week,
  ]
  if (expressionParts.year) base.push(expressionParts.year)
  return base.join(' ')
})

const nextRuns = ref<string[]>([])
const parseInput = ref('')

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

function rangeNums(from: number, to: number) {
  const out: number[] = []
  for (let i = from; i <= to; i++) out.push(i)
  return out
}

function buildPart(cfg: {
  mode: string
  from: number
  to: number
  start: number
  interval: number
  list: number[]
}, everyToken = '*', unsetToken?: string) {
  if (cfg.mode === 'empty') return ''
  if (cfg.mode === 'unset' && unsetToken) return unsetToken
  if (cfg.mode === 'every') return everyToken
  if (cfg.mode === 'range') return `${cfg.from}-${cfg.to}`
  if (cfg.mode === 'step') return `${cfg.start}/${cfg.interval}`
  if (cfg.mode === 'list') {
    if (!cfg.list.length) return everyToken
    return [...cfg.list].sort((a, b) => a - b).join(',')
  }
  return everyToken
}

function syncExprFromUi() {
  expressionParts.sec = buildPart(fields.sec)
  expressionParts.min = buildPart(fields.min)
  expressionParts.hour = buildPart(fields.hour)
  // day / week mutual exclusion like Quartz
  if (fields.week.mode !== 'unset' && fields.week.mode !== 'every') {
    expressionParts.week = buildPart(fields.week, '*')
    expressionParts.day = '?'
  } else if (fields.day.mode !== 'every') {
    expressionParts.day = buildPart(fields.day)
    expressionParts.week = '?'
    fields.week.mode = 'unset'
  } else {
    expressionParts.day = buildPart(fields.day)
    expressionParts.week = fields.week.mode === 'unset' ? '?' : buildPart(fields.week, '*')
  }
  expressionParts.month = buildPart(fields.month)
  expressionParts.year = buildPart(fields.year, '*')
}

watch(fields, () => syncExprFromUi(), { deep: true, immediate: true })

watch(cronExpr, async (v) => {
  parseInput.value = v
  await preview(v)
})

async function preview(expr?: string) {
  try {
    const res = await api.post('/system/exec-cycles/preview', {
      cronExpr: expr || cronExpr.value,
      count: 10,
    })
    nextRuns.value = (res.data?.nextRuns as string[]) || []
  } catch {
    nextRuns.value = []
  }
}

async function load() {
  loading.value = true
  try {
    rows.value = (await api.get('/system/exec-cycles', {
      params: { keyword: keyword.value || undefined },
    })).data || []
  } catch {
    ElMessage.error('加载执行周期失败')
  } finally {
    loading.value = false
  }
}

function onReset() {
  keyword.value = ''
  resetPage()
  void load()
}

function openCreate() {
  dialog.visible = true
  dialog.editingId = 0
  dialog.cycleCode = ''
  dialog.cycleName = ''
  dialog.description = ''
  dialog.status = 'ACTIVE'
  dialog.sortOrder = 100
  resetFields()
}

function resetFields() {
  fields.sec.mode = 'every'
  fields.min.mode = 'every'
  fields.hour.mode = 'every'
  fields.day.mode = 'every'
  fields.month.mode = 'every'
  fields.week.mode = 'unset'
  fields.year.mode = 'empty'
  for (const k of ['sec', 'min', 'hour', 'day', 'month', 'week', 'year'] as const) {
    fields[k].list = []
  }
  syncExprFromUi()
}

function applyParsedToUi(expr: string) {
  const parts = expr.trim().split(/\s+/)
  if (parts.length < 6) {
    ElMessage.warning('请输入合法 6/7 段 Cron')
    return
  }
  const [sec, min, hour, day, month, week, year = ''] = parts
  expressionParts.sec = sec
  expressionParts.min = min
  expressionParts.hour = hour
  expressionParts.day = day
  expressionParts.month = month
  expressionParts.week = week
  expressionParts.year = year
  assignField('sec', sec, 0, 59)
  assignField('min', min, 0, 59)
  assignField('hour', hour, 0, 23)
  assignField('day', day === '?' ? '*' : day, 1, 31)
  assignField('month', month, 1, 12)
  if (week === '?' || !week) {
    fields.week.mode = 'unset'
    fields.week.list = []
  } else {
    assignField('week', week, 1, 7)
  }
  if (!year) {
    fields.year.mode = 'empty'
  } else {
    assignField('year', year, 2020, 2099)
  }
  ElMessage.success('已反解析到 UI')
}

function assignField(
  key: 'sec' | 'min' | 'hour' | 'day' | 'month' | 'week' | 'year',
  token: string,
  min: number,
  max: number,
) {
  const f = fields[key]
  if (token === '*') {
    f.mode = 'every'
    return
  }
  if (token.includes('/')) {
    const [s, i] = token.split('/')
    f.mode = 'step'
    f.start = Number(s === '*' ? min : s)
    f.interval = Number(i || 1)
    return
  }
  if (token.includes('-') && !token.includes(',')) {
    const [a, b] = token.split('-')
    f.mode = 'range'
    f.from = Number(a)
    f.to = Number(b)
    return
  }
  f.mode = 'list'
  f.list = token.split(',').map((x) => Number(x)).filter((n) => !Number.isNaN(n) && n >= min && n <= max)
}

function openEdit(row: ExecCycle) {
  dialog.visible = true
  dialog.editingId = row.id
  dialog.cycleCode = row.cycleCode
  dialog.cycleName = row.cycleName
  dialog.description = row.description || ''
  dialog.status = row.status
  dialog.sortOrder = row.sortOrder || 100
  parseInput.value = row.cronExpr
  applyParsedToUi(row.cronExpr)
}

async function save() {
  if (!dialog.cycleName.trim()) return ElMessage.warning('请填写周期名称')
  if (!cronExpr.value.trim()) return ElMessage.warning('请生成 Cron 表达式')
  const body = {
    cycleCode: dialog.cycleCode || undefined,
    cycleName: dialog.cycleName.trim(),
    cronExpr: cronExpr.value,
    description: dialog.description || undefined,
    status: dialog.status,
    sortOrder: dialog.sortOrder,
  }
  try {
    if (dialog.editingId) {
      await api.put(`/system/exec-cycles/${dialog.editingId}`, body)
      ElMessage.success('已更新')
    } else {
      await api.post('/system/exec-cycles', body)
      ElMessage.success('已创建')
    }
    dialog.visible = false
    invalidateExecCycleMap()
    await load()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '保存失败')
  }
}

async function batchDelete() {
  if (!selectedIds.value.length) {
    ElMessage.warning('请先勾选要删除的执行周期')
    return
  }
  try {
    await ElMessageBox.confirm(`确认批量删除选中的 ${selectedIds.value.length} 条执行周期？`, '批量删除确认', {
      type: 'warning',
    })
  } catch {
    return
  }
  for (const id of selectedIds.value) {
    await api.delete(`/system/exec-cycles/${id}`)
  }
  selectedIds.value = []
  ElMessage.success('批量删除已完成')
  invalidateExecCycleMap()
  await load()
}

async function copyExpr() {
  try {
    await navigator.clipboard.writeText(cronExpr.value)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择')
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="exec-cycle">
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新建执行周期</el-button>
      <el-button type="danger" plain :disabled="!selectedIds.length" @click="batchDelete">批量删除</el-button>
      <el-input v-model="keyword" clearable placeholder="名称/Cron" style="width:240px" @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <el-button @click="onReset">重置</el-button>
    </div>

    <el-table
      :data="paged"
      stripe
      size="small"
      @selection-change="(sel: ExecCycle[]) => { selectedIds = sel.map((r) => r.id) }"
    >
      <el-table-column type="selection" width="44" />
      <el-table-column prop="cycleName" label="名称" min-width="140" />
      <el-table-column prop="cronExpr" label="Cron" min-width="180" show-overflow-tooltip />
      <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
    <PortalPagination
      v-if="rows.length"
      v-model:page="page"
      v-model:page-size="pageSize"
      :total="total"
    />

    <el-dialog
      v-model="dialog.visible"
      :title="dialog.editingId ? '编辑执行周期' : '新建执行周期'"
      width="920px"
      top="4vh"
      destroy-on-close
    >
      <el-form label-width="88px" style="margin-bottom:12px">
        <el-form-item label="名称" required>
          <el-input v-model="dialog.cycleName" placeholder="如：每天凌晨2点" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="dialog.cycleCode" placeholder="可选，空则自动生成" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="dialog.description" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="dialog.status">
            <el-radio-button value="ACTIVE">启用</el-radio-button>
            <el-radio-button value="INACTIVE">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <el-tabs v-model="unitTab" type="border-card" class="cron-tabs">
        <el-tab-pane
          v-for="u in [
            { key: 'sec', label: '秒', min: 0, max: 59 },
            { key: 'min', label: '分钟', min: 0, max: 59 },
            { key: 'hour', label: '小时', min: 0, max: 23 },
            { key: 'day', label: '日', min: 1, max: 31 },
            { key: 'month', label: '月', min: 1, max: 12 },
            { key: 'week', label: '周', min: 1, max: 7 },
            { key: 'year', label: '年', min: 2024, max: 2035 },
          ]"
          :key="u.key"
          :name="u.key"
          :label="u.label"
        >
          <el-radio-group v-model="(fields as any)[u.key].mode" class="mode-group">
            <el-radio v-if="u.key === 'week'" value="unset">不指定（?）</el-radio>
            <el-radio v-if="u.key === 'year'" value="empty">不指定（空）</el-radio>
            <el-radio value="every">每{{ u.label }} 允许的通配符[, - * /]</el-radio>
            <el-radio value="range">
              周期 从
              <el-input-number v-model="(fields as any)[u.key].from" :min="u.min" :max="u.max" size="small" controls-position="right" />
              -
              <el-input-number v-model="(fields as any)[u.key].to" :min="u.min" :max="u.max" size="small" controls-position="right" />
              {{ u.label }}
            </el-radio>
            <el-radio value="step">
              间隔 从
              <el-input-number v-model="(fields as any)[u.key].start" :min="u.min" :max="u.max" size="small" controls-position="right" />
              {{ u.label }}开始，每
              <el-input-number v-model="(fields as any)[u.key].interval" :min="1" :max="u.max" size="small" controls-position="right" />
              {{ u.label }}执行一次
            </el-radio>
            <el-radio value="list">指定</el-radio>
          </el-radio-group>
          <div v-if="(fields as any)[u.key].mode === 'list'" class="check-grid">
            <el-checkbox-group v-model="(fields as any)[u.key].list">
              <el-checkbox
                v-for="n in rangeNums(u.min, u.max)"
                :key="n"
                :label="n"
                border
              >{{ u.key === 'year' ? n : pad2(n) }}</el-checkbox>
            </el-checkbox-group>
          </div>
        </el-tab-pane>
      </el-tabs>

      <div class="cron-summary">
        <div class="parts">
          <div v-for="p in [
            { k: 'sec', l: '秒' }, { k: 'min', l: '分钟' }, { k: 'hour', l: '小时' },
            { k: 'day', l: '日' }, { k: 'month', l: '月' }, { k: 'week', l: '周' }, { k: 'year', l: '年' },
          ]" :key="p.k" class="part">
            <span>{{ p.l }}</span>
            <el-input :model-value="(expressionParts as any)[p.k]" readonly size="small" />
          </div>
        </div>
        <div class="expr-row">
          <span>表达式：</span>
          <el-input v-model="parseInput" />
          <el-button type="primary" @click="applyParsedToUi(parseInput)">反解析到 UI</el-button>
          <el-button type="primary" @click="copyExpr">复制</el-button>
        </div>
        <div class="next-runs">
          <div class="next-title">近 10 次执行时间：</div>
          <ol>
            <li v-for="(t, i) in nextRuns" :key="i">{{ t }}</li>
          </ol>
          <el-empty v-if="!nextRuns.length" description="无法解析或无下次执行时间" :image-size="48" />
        </div>
      </div>

      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.cron-tabs { margin-bottom: 12px; }
.mode-group { display: flex; flex-direction: column; align-items: flex-start; gap: 10px; }
.mode-group :deep(.el-radio) { margin-right: 0; height: auto; white-space: normal; align-items: center; }
.check-grid { margin-top: 10px; max-height: 180px; overflow: auto; }
.check-grid :deep(.el-checkbox) { margin: 4px; }
.cron-summary {
  border: 1px solid #e8edf5;
  border-radius: 10px;
  padding: 12px 14px;
  background: #fafbfd;
}
.parts { display: grid; grid-template-columns: repeat(7, 1fr); gap: 8px; margin-bottom: 10px; }
.part span { display: block; font-size: 12px; color: #909399; margin-bottom: 4px; text-align: center; }
.expr-row { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.expr-row > span { white-space: nowrap; }
.next-title { font-weight: 600; margin-bottom: 6px; }
.next-runs ol { margin: 0; padding-left: 20px; color: #606266; font-size: 13px; }
@media (max-width: 900px) {
  .parts { grid-template-columns: repeat(4, 1fr); }
}
</style>
