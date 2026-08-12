<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Grid, Plus, RefreshRight, Search, VideoPlay } from '@element-plus/icons-vue'
import api from '@/api/http'
import { statusLabel } from '@/utils/status-label'

const props = defineProps<{
  domain: string
  /** 父级 Tab 是否当前展示；切回时重新拉取指标域，与「指标域管理」保持一致 */
  active?: boolean
}>()

interface DomainRow {
  id: number
  domainName: string
  domainDbName: string
  remark?: string
}

interface GroupRow {
  id: number
  indicatorDomainId: number
  groupName: string
  targetTable: string
  groupCategory: string
  modelMethod: string
  description?: string
  status: string
}

interface IndicatorRow {
  id: number
  queryNo?: string
  resultField?: string
  fieldType?: string
  indicatorName: string
  fieldName?: string
  indicatorFlag?: string
}

interface DsRow {
  key: string
  name: string
  category: string
  version?: string
  deptName?: string
}

interface ParsedField {
  resultField: string
  fieldType: string
  fieldLength?: number
  fieldPrecision?: number
  indicatorName?: string
  fieldName?: string
}

const loading = ref(false)
const domains = ref<DomainRow[]>([])
const domainKeyword = ref('')
const selectedDomainId = ref<number | null>(null)

const groups = ref<GroupRow[]>([])
const query = reactive({
  groupName: '',
  targetTable: '',
  groupCategory: '',
})

const detailSaving = ref(false)
const detailFormRef = ref<FormInstance>()
const detail = reactive({
  id: null as number | null,
  indicatorDomainId: null as number | null,
  groupName: '',
  targetTable: '',
  groupCategory: 'UNIT',
  description: '',
})
const detailIndicators = ref<IndicatorRow[]>([])
const detailLoading = ref(false)

const domainEditVisible = ref(false)
const domainEditSaving = ref(false)
const domainEditRef = ref<FormInstance>()
const domainEdit = reactive({
  id: null as number | null,
  domainName: '',
  domainDbName: '',
  remark: '',
})

const tableNamePattern = /^ind_[a-z0-9]+(_[a-z0-9]+)*$/

const domainEditRules: FormRules = {
  domainName: [{ required: true, message: '请填写指标域名称', trigger: 'blur' }],
  domainDbName: [
    { required: true, message: '请填写指标域库名', trigger: 'blur' },
    {
      validator: (_r, v, cb) => {
        const s = String(v || '').trim().toLowerCase()
        if (!tableNamePattern.test(s)) {
          cb(new Error('以 ind_ 开头，支持小写字母、数字、下划线，不能以下划线结尾'))
        } else cb()
      },
      trigger: 'blur',
    },
  ],
}

/** 指标组信息弹窗（新增/编辑共用，对齐原型：表单 + 新增指标 + 指标表） */
const groupDialogVisible = ref(false)

const detailRules: FormRules = {
  groupName: [{ required: true, message: '请输入指标组名称', trigger: 'blur' }],
  targetTable: [
    { required: true, message: '请输入指标组结果表名', trigger: 'blur' },
    {
      validator: (_r, v, cb) => {
        const s = String(v || '').trim().toLowerCase()
        if (!tableNamePattern.test(s)) {
          cb(new Error('以 ind_ 开头，支持小写字母、数字、下划线，不能以下划线结尾'))
        } else if (/\d$/.test(s)) {
          cb(new Error('不能以数字结尾'))
        } else cb()
      },
      trigger: 'blur',
    },
  ],
  groupCategory: [{ required: true, message: '请选择指标组分类', trigger: 'change' }],
}

const dsDialog = ref(false)
const dsLoading = ref(false)
const dsCategories = ['全部', '基础库', '治理库', '主题库', '原始库', '来源', '其他', '字典']
const dsCategory = ref('全部')
const dsCategoryKeyword = ref('')
const dsNameKeyword = ref('')
const dsRows = ref<DsRow[]>([])
const dsPage = ref(1)
const dsPageSize = ref(20)
const selectedDsKey = ref('')
const selectedDsName = ref('')

const dsPagedRows = computed(() => {
  const start = (dsPage.value - 1) * dsPageSize.value
  return dsRows.value.slice(start, start + dsPageSize.value)
})

const sqlDialog = ref(false)
const previewDialog = ref(false)
const sqlSaving = ref(false)
const parsing = ref(false)
const sqlForm = reactive({
  datasourceKey: '',
  datasourceName: '',
  timeoutSec: 60,
  sqlText: '',
})
const parsedFields = ref<ParsedField[]>([])
const previewColumns = ref<string[]>([])
const previewRows = ref<Record<string, unknown>[]>([])
const previewMessage = ref('')

const filteredDomains = computed(() => {
  const kw = domainKeyword.value.trim()
  if (!kw) return domains.value
  return domains.value.filter((d) => d.domainName.includes(kw) || d.domainDbName.includes(kw))
})

const filteredDsCategories = computed(() => {
  const kw = dsCategoryKeyword.value.trim()
  if (!kw) return dsCategories
  return dsCategories.filter((c) => c.includes(kw))
})

function categoryLabel(code: string) {
  if (code === 'UNIT') return '单元指标组'
  if (code === 'COMPOSITE') return '复合指标组'
  return code || '—'
}

function modelLabel(code: string) {
  if (code === 'SQL') return 'SQL建模'
  return code || '—'
}

async function loadDomains() {
  const res = await api.get(`/analytics/domain/${props.domain}/indicator-domains`)
  domains.value = res.data || []
  const stillSelected = domains.value.some((d) => d.id === selectedDomainId.value)
  if (!stillSelected) {
    selectedDomainId.value = domains.value.length ? domains.value[0].id : null
  }
}

async function loadGroups() {
  if (selectedDomainId.value == null) {
    groups.value = []
    return
  }
  loading.value = true
  try {
    const res = await api.get(`/analytics/domain/${props.domain}/indicator-groups`, {
      params: {
        indicatorDomainId: selectedDomainId.value,
        groupName: query.groupName || undefined,
        targetTable: query.targetTable || undefined,
        groupCategory: query.groupCategory || undefined,
      },
    })
    groups.value = res.data || []
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.groupName = ''
  query.targetTable = ''
  query.groupCategory = ''
  loadGroups()
}

function selectDomain(id: number) {
  selectedDomainId.value = id
  loadGroups()
}

/** 数据管理：选中该指标域并展示其下指标组 */
function domainDataManage(d: DomainRow) {
  selectDomain(d.id)
}

function openDomainEdit(d: DomainRow) {
  domainEdit.id = d.id
  domainEdit.domainName = d.domainName
  domainEdit.domainDbName = d.domainDbName
  domainEdit.remark = d.remark || ''
  domainEditVisible.value = true
}

async function saveDomainEdit() {
  if (!domainEditRef.value || domainEdit.id == null) return
  await domainEditRef.value.validate()
  domainEditSaving.value = true
  try {
    await api.put(`/analytics/domain/indicator-domains/${domainEdit.id}`, {
      domainName: domainEdit.domainName.trim(),
      domainDbName: domainEdit.domainDbName.trim().toLowerCase(),
      remark: domainEdit.remark?.trim() || null,
    })
    ElMessage.success('已修改指标域')
    domainEditVisible.value = false
    await loadDomains()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    domainEditSaving.value = false
  }
}

async function publishDomain(d: DomainRow) {
  await ElMessageBox.confirm(
    `确认发布指标域「${d.domainName}」下全部可发布的指标组？将同步生成指标任务。`,
    '发布确认',
    { type: 'warning' },
  )
  try {
    const res = await api.post(`/analytics/domain/indicator-domains/${d.id}/publish`)
    const published = res.data?.published ?? 0
    const skipped = res.data?.skipped ?? 0
    ElMessage.success(`已发布 ${published} 个指标组` + (skipped ? `，跳过 ${skipped} 个` : ''))
    selectDomain(d.id)
    await loadGroups()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '发布失败')
  }
}

async function removeDomain(d: DomainRow) {
  await ElMessageBox.confirm(`确认删除指标域「${d.domainName}」？`, '删除确认', { type: 'warning' })
  try {
    await api.delete(`/analytics/domain/indicator-domains/${d.id}`)
    ElMessage.success('已删除')
    if (selectedDomainId.value === d.id) {
      selectedDomainId.value = null
    }
    await loadDomains()
    if (selectedDomainId.value == null && domains.value.length) {
      selectedDomainId.value = domains.value[0].id
    }
    await loadGroups()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '删除失败')
  }
}

async function openCreate() {
  if (selectedDomainId.value == null) {
    ElMessage.warning('请先选择左侧指标域')
    return
  }
  detail.id = null
  detail.indicatorDomainId = selectedDomainId.value
  detail.groupName = ''
  detail.targetTable = ''
  detail.groupCategory = 'UNIT'
  detail.description = ''
  detailIndicators.value = []
  groupDialogVisible.value = true
}

async function openDetail(row: GroupRow) {
  detailLoading.value = true
  groupDialogVisible.value = true
  try {
    const [gRes, iRes] = await Promise.all([
      api.get(`/analytics/domain/indicator-groups/${row.id}`),
      api.get(`/analytics/domain/indicator-groups/${row.id}/indicators`),
    ])
    const g = gRes.data as GroupRow
    detail.id = g.id
    detail.indicatorDomainId = g.indicatorDomainId
    detail.groupName = g.groupName
    detail.targetTable = g.targetTable
    detail.groupCategory = g.groupCategory || 'UNIT'
    detail.description = g.description || ''
    detailIndicators.value = iRes.data || []
  } finally {
    detailLoading.value = false
  }
}

async function closeGroupDialog() {
  groupDialogVisible.value = false
  await loadGroups()
}

async function ensureGroupSaved(): Promise<number | null> {
  if (!detailFormRef.value) return detail.id
  await detailFormRef.value.validate()
  detailSaving.value = true
  try {
    const body = {
      indicatorDomainId: detail.indicatorDomainId,
      groupName: detail.groupName.trim(),
      targetTable: detail.targetTable.trim().toLowerCase(),
      groupCategory: detail.groupCategory,
      description: detail.description?.trim() || null,
      modelMethod: 'SQL',
    }
    if (detail.id == null) {
      const res = await api.post(`/analytics/domain/${props.domain}/indicator-groups`, body)
      detail.id = res.data as number
      ElMessage.success('指标组已保存')
    } else {
      await api.put(`/analytics/domain/indicator-groups/${detail.id}`, body)
      ElMessage.success('指标组已更新')
    }
    return detail.id
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '保存失败')
    return null
  } finally {
    detailSaving.value = false
  }
}

async function saveDetail() {
  await ensureGroupSaved()
}

async function openAddIndicator() {
  const id = await ensureGroupSaved()
  if (id == null) return
  sqlForm.datasourceKey = 'ods_collect'
  sqlForm.datasourceName = '原始归集库'
  sqlForm.timeoutSec = 60
  sqlForm.sqlText = props.domain === 'population'
    ? 'select year(now()) as name, count(1) as num from cd_population'
    : ''
  parsedFields.value = []
  previewMessage.value = ''
  selectedDsKey.value = sqlForm.datasourceKey
  selectedDsName.value = sqlForm.datasourceName
  sqlDialog.value = true
  // 打开后自动解析字段，对齐「指标语句」原型展示
  if (sqlForm.sqlText.trim()) {
    await runParse(true)
  }
}

async function openDsPicker() {
  selectedDsKey.value = sqlForm.datasourceKey || ''
  selectedDsName.value = sqlForm.datasourceName || ''
  dsCategory.value = '全部'
  dsNameKeyword.value = ''
  dsCategoryKeyword.value = ''
  dsPage.value = 1
  dsDialog.value = true
  await loadDsCatalog()
}

function pickDsCategory(c: string) {
  if (dsCategory.value === c) return
  dsCategory.value = c
  loadDsCatalog()
}

async function loadDsCatalog() {
  if (!dsDialog.value) return
  dsLoading.value = true
  try {
    const cat = dsCategory.value === '全部' ? undefined : (dsCategory.value || undefined)
    const res = await api.get(`/analytics/domain/${props.domain}/indicator-datasource-catalog`, {
      params: {
        category: cat,
        keyword: dsNameKeyword.value || undefined,
      },
    })
    dsRows.value = res.data || []
    dsPage.value = 1
  } finally {
    dsLoading.value = false
  }
}

function selectDsRow(row: DsRow) {
  selectedDsKey.value = row.key
  selectedDsName.value = row.name
}

function confirmDs() {
  if (!selectedDsKey.value) {
    ElMessage.warning('请选择数据源')
    return
  }
  const row = dsRows.value.find((r) => r.key === selectedDsKey.value)
  sqlForm.datasourceKey = selectedDsKey.value
  sqlForm.datasourceName = selectedDsName.value || row?.name || selectedDsKey.value
  dsDialog.value = false
}

async function runParse(silent = false) {
  if (!sqlForm.sqlText.trim()) {
    if (!silent) ElMessage.warning('请填写查询语句')
    return
  }
  parsing.value = true
  try {
    const res = await api.post(`/analytics/domain/${props.domain}/indicators/sql/parse`, {
      sqlText: sqlForm.sqlText,
    })
    parsedFields.value = res.data || []
    if (!silent) ElMessage.success(`已解析 ${parsedFields.value.length} 个结果字段`)
  } catch (e: unknown) {
    if (!silent) ElMessage.error((e as Error).message || '解析失败')
  } finally {
    parsing.value = false
  }
}

async function openPreview() {
  if (!sqlForm.sqlText.trim()) {
    ElMessage.warning('请填写查询语句')
    return
  }
  try {
    const res = await api.post(`/analytics/domain/${props.domain}/indicators/sql/preview`, {
      sqlText: sqlForm.sqlText,
      timeoutSec: sqlForm.timeoutSec,
      datasourceKey: sqlForm.datasourceKey,
    })
    previewColumns.value = res.data?.columns || []
    previewRows.value = res.data?.rows || []
    previewMessage.value = res.data?.message || ''
    if (res.data?.fields?.length && !parsedFields.value.length) {
      parsedFields.value = res.data.fields
    }
    previewDialog.value = true
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '预览失败')
  }
}

async function saveSql() {
  if (!detail.id) {
    ElMessage.warning('请先保存指标组')
    return
  }
  if (!sqlForm.datasourceKey) {
    ElMessage.warning('请选择数据源')
    return
  }
  if (!sqlForm.sqlText.trim()) {
    ElMessage.warning('请填写查询语句')
    return
  }
  if (!parsedFields.value.length) {
    await runParse()
    if (!parsedFields.value.length) return
  }
  sqlSaving.value = true
  try {
    const fields = parsedFields.value.map((f, idx) => ({
      resultField: f.resultField,
      fieldType: f.fieldType,
      fieldLength: f.fieldLength,
      fieldPrecision: f.fieldPrecision,
      indicatorName: f.indicatorName || String(idx + 1),
      fieldName: f.fieldName || `ind_${f.resultField}`,
    }))
    await api.post(`/analytics/domain/${props.domain}/indicators/sql`, {
      groupId: detail.id,
      datasourceKey: sqlForm.datasourceKey,
      datasourceName: sqlForm.datasourceName,
      timeoutSec: sqlForm.timeoutSec,
      sqlText: sqlForm.sqlText,
      querySlug: detail.targetTable || 'query',
      fields,
    })
    ElMessage.success('指标语句已保存')
    sqlDialog.value = false
    const iRes = await api.get(`/analytics/domain/indicator-groups/${detail.id}/indicators`)
    detailIndicators.value = iRes.data || []
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    sqlSaving.value = false
  }
}

async function persistIndicator(row: IndicatorRow) {
  try {
    await api.put(`/analytics/domain/indicators/${row.id}`, {
      indicatorName: row.indicatorName,
      fieldName: row.fieldName,
      indicatorFlag: row.indicatorFlag || null,
    })
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '保存失败')
    if (detail.id) {
      const iRes = await api.get(`/analytics/domain/indicator-groups/${detail.id}/indicators`)
      detailIndicators.value = iRes.data || []
    }
  }
}

async function removeIndicator(row: IndicatorRow) {
  await ElMessageBox.confirm(`确认删除指标「${row.indicatorName}」？`, '删除确认', { type: 'warning' })
  await api.delete(`/analytics/domain/indicators/${row.id}`)
  ElMessage.success('已删除')
  if (detail.id) {
    const iRes = await api.get(`/analytics/domain/indicator-groups/${detail.id}/indicators`)
    detailIndicators.value = iRes.data || []
  }
}

async function removeCurrentGroup() {
  if (detail.id == null) return
  await ElMessageBox.confirm(`确认删除指标组「${detail.groupName}」？`, '删除确认', { type: 'warning' })
  await api.delete(`/analytics/domain/indicator-groups/${detail.id}`)
  ElMessage.success('已删除')
  await closeGroupDialog()
}

watch(() => props.domain, async () => {
  groupDialogVisible.value = false
  selectedDomainId.value = null
  await loadDomains()
  await loadGroups()
})
watch(
  () => props.active,
  async (v, prev) => {
    if (v && prev === false) {
      groupDialogVisible.value = false
      await loadDomains()
      await loadGroups()
    }
  },
)

onMounted(async () => {
  await loadDomains()
  await loadGroups()
})
</script>

<template>
  <div class="ind-group-panel">
    <!-- 列表：左指标域 + 右指标组 -->
    <div class="ind-group-list">
      <aside class="ind-domain-side">
        <div class="side-title">指标域</div>
        <el-input
          v-model="domainKeyword"
          clearable
          size="small"
          placeholder="请输入指标域名称"
          class="side-search"
        />
        <div class="side-list">
          <div
            v-for="d in filteredDomains"
            :key="d.id"
            class="side-item"
            :class="{ active: selectedDomainId === d.id }"
          >
            <button type="button" class="side-item-name" @click="domainDataManage(d)">
              {{ d.domainName }}
            </button>
            <div class="side-item-ops">
              <el-button link type="primary" size="small" @click.stop="domainDataManage(d)">数据管理</el-button>
              <el-button link type="primary" size="small" @click.stop="publishDomain(d)">发布</el-button>
              <el-button link type="primary" size="small" @click.stop="openDomainEdit(d)">修改</el-button>
              <el-button link type="primary" size="small" @click.stop="removeDomain(d)">删除</el-button>
            </div>
          </div>
          <el-empty v-if="!filteredDomains.length" description="暂无指标域" :image-size="64" />
        </div>
      </aside>

      <section v-loading="loading" class="ind-group-main">
        <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent="loadGroups">
          <el-form-item label="组名称" class="portal-field-md">
            <el-input v-model="query.groupName" clearable placeholder="请输入组名称" />
          </el-form-item>
          <el-form-item label="目标表" class="portal-field-md">
            <el-input v-model="query.targetTable" clearable placeholder="请输入目标表" />
          </el-form-item>
          <el-form-item label="组分类" class="portal-field-md">
            <el-select v-model="query.groupCategory" clearable placeholder="请选择">
              <el-option label="单元指标组" value="UNIT" />
              <el-option label="复合指标组" value="COMPOSITE" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" :icon="Search" @click="loadGroups">查询</el-button>
            <el-button :icon="RefreshRight" @click="resetQuery">重置</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">新增</el-button>
          </el-form-item>
        </el-form>

        <el-table class="portal-table" :data="groups" stripe size="small" empty-text="暂无数据">
          <el-table-column label="组名称" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">{{ row.groupName }}</el-button>
            </template>
          </el-table-column>
          <el-table-column prop="targetTable" label="目标表" min-width="180" show-overflow-tooltip />
          <el-table-column label="组分类" width="120">
            <template #default="{ row }">{{ categoryLabel(row.groupCategory) }}</template>
          </el-table-column>
          <el-table-column label="建模方式" width="110">
            <template #default="{ row }">{{ modelLabel(row.modelMethod) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
        </el-table>
        <div class="list-footer">共 {{ groups.length }} 条</div>
      </section>
    </div>

    <el-dialog v-model="domainEditVisible" title="修改指标域" width="520px" destroy-on-close>
      <el-form ref="domainEditRef" :model="domainEdit" :rules="domainEditRules" label-width="120px">
        <el-form-item label="指标域名称" prop="domainName">
          <el-input v-model="domainEdit.domainName" placeholder="请填写指标域名称" />
        </el-form-item>
        <el-form-item label="指标域库名" prop="domainDbName">
          <el-input
            v-model="domainEdit.domainDbName"
            placeholder="以 ind_ 开头，支持小写字母、数字、下划线，不能以下划线结尾"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="domainEdit.remark" placeholder="请填写备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="domainEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="domainEditSaving" @click="saveDomainEdit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 指标组信息弹窗（新增/编辑） -->
    <el-dialog
      v-model="groupDialogVisible"
      title="指标组信息"
      width="920px"
      top="6vh"
      destroy-on-close
      class="ind-group-info-dialog"
      @closed="loadGroups"
    >
      <div v-loading="detailLoading" class="ind-group-dialog-body">
        <el-form
          ref="detailFormRef"
          :model="detail"
          :rules="detailRules"
          label-width="140px"
          class="detail-form"
        >
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="指标组名称" prop="groupName" required>
                <el-input v-model="detail.groupName" placeholder="请输入指标组名称" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="指标组结果表名" prop="targetTable" required>
                <el-input
                  v-model="detail.targetTable"
                  placeholder="以 ind_ 开头，支持小写字母、数字、下划线，不能以数字结尾"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="指标组分类" prop="groupCategory" required>
                <el-select v-model="detail.groupCategory" placeholder="请选择指标组分类" style="width: 100%">
                  <el-option label="单元指标组" value="UNIT" />
                  <el-option label="复合指标组" value="COMPOSITE" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="指标组描述">
                <el-input v-model="detail.description" placeholder="请输入描述" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <el-button type="primary" :icon="Plus" style="margin-bottom: 12px" @click="openAddIndicator">
          新增指标
        </el-button>

        <el-table
          class="portal-table detail-ind-table"
          :data="detailIndicators"
          stripe
          size="small"
          empty-text="暂无数据"
          max-height="360"
        >
          <el-table-column prop="queryNo" label="查询编号" min-width="180" show-overflow-tooltip />
          <el-table-column prop="resultField" label="查询结果字段" min-width="120" show-overflow-tooltip />
          <el-table-column prop="fieldType" label="字段类型" width="90" />
          <el-table-column label="指标名称" min-width="120">
            <template #default="{ row }">
              <el-input v-model="row.indicatorName" size="small" @change="persistIndicator(row)" />
            </template>
          </el-table-column>
          <el-table-column label="字段名" min-width="120">
            <template #default="{ row }">
              <el-input v-model="row.fieldName" size="small" @change="persistIndicator(row)" />
            </template>
          </el-table-column>
          <el-table-column label="指标标识" min-width="120">
            <template #default="{ row }">
              <el-input v-model="row.indicatorFlag" size="small" @change="persistIndicator(row)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="removeIndicator(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="closeGroupDialog">取消</el-button>
        <el-button v-if="detail.id" type="danger" plain @click="removeCurrentGroup">删除</el-button>
        <el-button type="primary" :loading="detailSaving" @click="saveDetail">保存</el-button>
      </template>
    </el-dialog>

    <!-- 指标语句（新增指标先弹此窗） -->
    <el-dialog v-model="sqlDialog" title="指标语句" width="860px" destroy-on-close top="6vh" append-to-body>
      <el-form label-width="110px" inline class="sql-top-form portal-inline-form">
        <el-form-item label="数据源" required class="portal-field-xl">
          <el-input
            :model-value="sqlForm.datasourceName"
            readonly
            placeholder="请选择数据源"
            class="ds-picker-input"
            @click="openDsPicker"
          >
            <template #suffix>
              <el-icon class="ds-picker-icon" @click.stop="openDsPicker"><Search /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="超时时间(秒)" required>
          <el-input-number v-model="sqlForm.timeoutSec" :min="5" :max="600" controls-position="right" />
        </el-form-item>
      </el-form>
      <el-form label-width="110px">
        <el-form-item label="查询语句">
          <div class="sql-toolbar">
            <el-tooltip content="预览执行">
              <el-button type="primary" size="small" class="sql-tool-btn" :icon="VideoPlay" @click="openPreview" />
            </el-tooltip>
            <el-tooltip content="解析结果字段">
              <el-button
                type="primary"
                size="small"
                class="sql-tool-btn"
                :icon="Grid"
                :loading="parsing"
                @click="runParse()"
              />
            </el-tooltip>
          </div>
          <el-input
            v-model="sqlForm.sqlText"
            type="textarea"
            :rows="10"
            class="sql-editor"
            placeholder="请输入 SELECT 语句，并为结果列指定 AS 别名"
          />
        </el-form-item>
      </el-form>
      <el-table :data="parsedFields" stripe size="small" max-height="220" empty-text="暂无数据">
        <el-table-column prop="resultField" label="结果字段名" min-width="140" />
        <el-table-column prop="fieldType" label="字段类型" width="100" />
        <el-table-column prop="fieldLength" label="字段长度" width="100" />
        <el-table-column prop="fieldPrecision" label="字段精度" width="100" />
      </el-table>
      <template #footer>
        <el-button @click="sqlDialog = false">取消</el-button>
        <el-button type="primary" :loading="sqlSaving" @click="saveSql">保存</el-button>
      </template>
    </el-dialog>

    <!-- 选择数据源（从指标语句·数据源字段点开） -->
    <el-dialog v-model="dsDialog" title="选择数据源" width="960px" destroy-on-close top="5vh" append-to-body>
      <div class="ds-picker">
        <aside class="ds-side">
          <div class="side-title">数据源分类</div>
          <el-input
            v-model="dsCategoryKeyword"
            size="small"
            clearable
            placeholder="请输入名称"
            class="ds-side-search"
          />
          <div class="ds-side-list">
            <button
              v-for="c in filteredDsCategories"
              :key="c"
              type="button"
              class="side-item"
              :class="{ active: dsCategory === c }"
              @click="pickDsCategory(c)"
            >
              {{ c }}
            </button>
          </div>
        </aside>
        <section class="ds-main">
          <el-form inline class="portal-inline-form" @submit.prevent="loadDsCatalog">
            <el-form-item label="数据源名称" class="portal-field-lg">
              <el-input v-model="dsNameKeyword" clearable placeholder="请输入名称" />
            </el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button type="primary" :icon="Search" @click="loadDsCatalog">查询</el-button>
              <el-button type="primary" :icon="RefreshRight" @click="dsNameKeyword = ''; loadDsCatalog()">重置</el-button>
            </el-form-item>
          </el-form>
          <el-table
            v-loading="dsLoading"
            :data="dsPagedRows"
            stripe
            border
            size="small"
            height="360"
            highlight-current-row
            empty-text="暂无数据"
            :row-class-name="({ row }: { row: DsRow }) => (row.key === selectedDsKey ? 'ds-row-selected' : '')"
            @row-click="selectDsRow"
          >
            <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="version" label="版本" width="80" />
            <el-table-column prop="category" label="所属分类" width="100" />
            <el-table-column prop="deptName" label="提供部门" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click.stop="selectDsRow(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="ds-pager">
            <el-pagination
              v-model:current-page="dsPage"
              v-model:page-size="dsPageSize"
              :total="dsRows.length"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              small
            />
          </div>
        </section>
      </div>
      <template #footer>
        <el-button @click="dsDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmDs">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewDialog" title="预览" width="860px" destroy-on-close append-to-body>
      <el-alert v-if="previewMessage" type="warning" :closable="false" :title="previewMessage" style="margin-bottom: 8px" />
      <el-table :data="previewRows" stripe size="small" max-height="360" empty-text="暂无数据">
        <el-table-column
          v-for="col in previewColumns"
          :key="col"
          :prop="col"
          :label="col"
          min-width="120"
          show-overflow-tooltip
        />
      </el-table>
      <template #footer>
        <el-button @click="previewDialog = false">关闭</el-button>
        <el-button type="primary" @click="previewDialog = false">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ind-group-list {
  display: flex;
  gap: 12px;
  min-height: 420px;
}
.ind-domain-side {
  width: 280px;
  flex-shrink: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: #fff;
  padding: 10px;
}
.side-title {
  font-weight: 600;
  margin-bottom: 8px;
}
.side-search {
  margin-bottom: 8px;
}
.side-list {
  max-height: 520px;
  overflow: auto;
}
.ind-domain-side .side-item {
  border: 1px solid transparent;
  border-radius: 6px;
  padding: 8px 8px 4px;
  margin-bottom: 6px;
  background: var(--el-fill-color-blank, #fff);
}
.ind-domain-side .side-item.active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
}
.side-item-name {
  display: block;
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  padding: 2px 4px 6px;
  cursor: pointer;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}
.ind-domain-side .side-item.active .side-item-name {
  color: var(--el-color-primary);
}
.side-item-ops {
  display: flex;
  flex-wrap: wrap;
  gap: 0 2px;
  padding: 0 2px 2px;
}
.side-item-ops :deep(.el-button) {
  padding: 0 4px;
  margin: 0;
}
.ind-group-main {
  flex: 1;
  min-width: 0;
}
.list-footer {
  margin-top: 10px;
  text-align: right;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.detail-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.detail-title {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
}
.detail-form {
  margin-bottom: 8px;
}
.detail-ind-table {
  margin-top: 12px;
}
.ds-picker {
  display: flex;
  gap: 12px;
  min-height: 420px;
}
.ds-side {
  width: 200px;
  flex-shrink: 0;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 10px;
  background: #fff;
}
.ds-side-search {
  margin-bottom: 8px;
}
.ds-side-list {
  max-height: 420px;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 2px;
  padding: 4px;
}
.ds-side .side-item {
  display: block;
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  padding: 8px 10px;
  border-radius: 2px;
  cursor: pointer;
  color: var(--el-text-color-regular);
  margin: 0;
}
.ds-side .side-item:hover {
  background: var(--el-fill-color-light);
}
.ds-side .side-item.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}
.ds-main {
  flex: 1;
  min-width: 0;
}
.ds-pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
:deep(.ds-row-selected) {
  --el-table-tr-bg-color: var(--el-color-primary-light-9);
}
.sql-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.sql-tool-btn {
  width: 28px;
  height: 28px;
  padding: 0;
}
.sql-top-form {
  margin-bottom: 4px;
}
.ds-picker-input {
  width: 280px;
  cursor: pointer;
}
.ds-picker-icon {
  cursor: pointer;
  color: var(--el-color-primary);
}
.sql-editor :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  line-height: 1.45;
}
</style>
