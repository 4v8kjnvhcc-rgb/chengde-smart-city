<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel } from '@/utils/status-label'
import { ingestionRegisterCache } from '../ingestion-register-cache'
import {
  ingestionApi,
  type DataSource,
  type Project,
  type Upload,
  type UploadTemplate,
} from '../useIngestionHub'

type TplBinding = {
  sheetName: string
  headerRow: number
  columns: string[]
  targetTable: string
  tableId?: number
  tableName?: string
  tableCode?: string
}

const router = useRouter()
const listTab = ref<'templates' | 'uploads'>('templates')
const templates = ref<UploadTemplate[]>([])
const uploads = ref<Upload[]>([])
const projects = ref<Project[]>([])
const fileSources = ref<DataSource[]>([])
const tplProjectId = ref<number | undefined>()
const tplSourceId = ref<number | undefined>()

const tplDialog = ref(false)
const uploadDialog = ref(false)
const tplDetailVisible = ref(false)
const tplDetailTitle = ref('')
const tplDetailRows = ref<TplBinding[]>([])

const tplForm = reactive({ templateCode: '', templateName: '' })
const tplFileInput = ref<HTMLInputElement>()
const tplBusy = ref(false)
const tplToken = ref('')
const tplFileName = ref('')
const tplSheets = ref<string[]>([])
const tplSheet = ref('')
const tplHeaderRow = ref(1)
const tplColumns = ref<string[]>([])
const tplSelectedCols = ref<string[]>([])
const tplTargetTable = ref('')
const addSysDialog = ref(false)
const addSysBusy = ref(false)
const addSysForm = reactive({ systemName: '', sourceName: '' })

const uploadForm = reactive({ templateCode: '' })
const fileInput = ref<HTMLInputElement>()
const uploadStep = ref(0)
const uploadBusy = ref(false)
const uploadToken = ref('')
const uploadFileName = ref('')
const sheetOptions = ref<string[]>([])
const selectedSheet = ref('')
const targetTable = ref('')
const boundTableLabel = ref('')
const previewColumns = ref<string[]>([])
const previewRows = ref<Record<string, string>[]>([])
const commitResult = ref('')
const committedSheets = ref<string[]>([])
const remainingSheets = ref<string[]>([])
const activeBindings = ref<TplBinding[]>([])
const activeHeaderRow = ref(1)
const writeMode = ref<'APPEND' | 'REPLACE'>('APPEND')

function isUsableTemplate(t: { columnMappingJson?: string }) {
  const j = t.columnMappingJson || ''
  return j.includes('"bindings"') && j.includes('sheetName')
}

const listedTemplates = computed(() => templates.value.filter(isUsableTemplate))
const selectableTemplates = computed(() =>
  listedTemplates.value.filter((t) => !t.status || t.status === 'ACTIVE'),
)

function statusTagType(status: unknown): 'success' | 'warning' | 'info' | 'danger' | 'primary' {
  const key = String(status ?? '').trim().toUpperCase()
  if (key === 'ACTIVE' || key === 'COMMITTED' || key === 'SUCCESS') return 'success'
  if (key === 'INACTIVE' || key === 'FAILED' || key === 'ERROR') return 'danger'
  if (key === 'RUNNING' || key === 'PENDING') return 'warning'
  return 'info'
}

function suggestOdsFromFile(suggested?: string): string {
  const raw = String(suggested || '').trim()
  if (!raw) return ''
  return raw.toLowerCase().startsWith('ods_') ? raw : `ods_${raw.replace(/[^A-Za-z0-9_]/g, '')}`
}

function parseTplBindingsLocal(json?: string): TplBinding[] {
  if (!json) return []
  try {
    const root = JSON.parse(json) as { bindings?: TplBinding[] }
    return Array.isArray(root.bindings) ? root.bindings : []
  } catch {
    return []
  }
}

function templateAssetLabel(row: UploadTemplate): string {
  const bindings = parseTplBindingsLocal(row.columnMappingJson)
  const b = bindings[0]
  if (!b) return '—'
  if (b.tableName) return b.tableName
  if (b.tableId) return `资产#${b.tableId}`
  return '保存后自动登记'
}

function templateTarget(row: UploadTemplate): string {
  const bindings = parseTplBindingsLocal(row.columnMappingJson)
  return bindings[0]?.targetTable || '—'
}

const selectableFileSources = computed(() => {
  if (!tplProjectId.value) return fileSources.value
  return fileSources.value.filter((s) => s.projectId === tplProjectId.value)
})

function sourceOptionLabel(s: DataSource) {
  const sys = s.systemName || s.sourceName || '未命名系统'
  return `${sys}（${s.sourceName}）`
}

async function loadAssetTargets() {
  const [p, ds] = await Promise.all([
    ingestionApi.projects(),
    ingestionApi.dataSources(),
  ])
  projects.value = p.data || []
  fileSources.value = (ds.data || []).filter((s) => String(s.sourceType || '').toUpperCase() === 'FILE')
  const other = projects.value.find((x) => isOtherProject(x.projectCode))
  if (!tplProjectId.value) {
    tplProjectId.value = other?.id || projects.value[0]?.id
  }
  syncDefaultSource()
}

function isOtherProject(code?: string) {
  return !!code && (code === 'PRJ_OTHER' || code.startsWith('PRJ_OTHER_'))
}

function isManualUploadSource(code?: string) {
  return !!code && (code === 'DS_MANUAL_UPLOAD' || code.startsWith('DS_MANUAL_UPLOAD_'))
}

function syncDefaultSource() {
  const list = selectableFileSources.value
  if (!list.length) {
    tplSourceId.value = undefined
    return
  }
  if (tplSourceId.value && list.some((s) => s.id === tplSourceId.value)) return
  const manual = list.find((s) => isManualUploadSource(s.sourceCode))
  tplSourceId.value = manual?.id || list[0].id
}

function onTplProjectChange() {
  syncDefaultSource()
}

function openAddSystemDialog() {
  if (!tplProjectId.value) {
    ElMessage.warning('请先选择归属项目')
    return
  }
  addSysForm.systemName = ''
  addSysForm.sourceName = ''
  addSysDialog.value = true
}

async function submitAddSystem() {
  const systemName = addSysForm.systemName.trim()
  if (!systemName) {
    ElMessage.warning('请填写系统名称')
    return
  }
  if (!tplProjectId.value) {
    ElMessage.warning('请先选择归属项目')
    return
  }
  const sourceName = addSysForm.sourceName.trim() || '手动上传'
  addSysBusy.value = true
  try {
    const id = (await ingestionApi.createDataSource({
      projectId: tplProjectId.value,
      systemName,
      sourceName,
      sourceType: 'FILE',
    })).data
    ElMessage.success(`已创建系统「${systemName}」，并同步到资产登记`)
    ingestionRegisterCache.invalidate('dataSources', 'tables')
    await loadAssetTargets()
    tplSourceId.value = Number(id)
    addSysDialog.value = false
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '创建系统失败')
  } finally {
    addSysBusy.value = false
  }
}

async function loadLists() {
  const [tpl, up] = await Promise.all([ingestionApi.templates(), ingestionApi.uploads()])
  templates.value = tpl.data || []
  uploads.value = up.data || []
}

async function openCreateTemplate() {
  resetTplWizard()
  tplDialog.value = true
  await loadAssetTargets()
}

async function openUploadDialog() {
  resetUploadWizard(false)
  uploadDialog.value = true
  await loadLists()
}

function resetTplWizard() {
  tplForm.templateCode = ''
  tplForm.templateName = ''
  tplToken.value = ''
  tplFileName.value = ''
  tplSheets.value = []
  tplSheet.value = ''
  tplHeaderRow.value = 1
  tplColumns.value = []
  tplSelectedCols.value = []
  tplTargetTable.value = ''
}

async function onTplFileChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  tplBusy.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const res = await ingestionApi.inspectUpload(fd)
    tplToken.value = res.data.uploadToken
    tplFileName.value = res.data.fileName
    tplSheets.value = res.data.sheets || []
    tplSheet.value = res.data.suggestedSheet || res.data.sheets?.[0] || ''
    tplHeaderRow.value = 1
    tplColumns.value = []
    tplSelectedCols.value = []
    if (!tplTargetTable.value) {
      tplTargetTable.value = suggestOdsFromFile(res.data.suggestedTable)
    }
    ElMessage.success(`已识别 ${tplSheets.value.length} 个工作表`)
  } catch {
    ElMessage.error('解析样例文件失败')
  } finally {
    tplBusy.value = false
    if (tplFileInput.value) tplFileInput.value.value = ''
  }
}

function defaultTplName(sheet: string) {
  const base = (tplFileName.value || '数据').replace(/\.[^.]+$/, '')
  return sheet ? `${base}_${sheet}` : base
}

async function loadTplHeader() {
  if (!tplToken.value || !tplSheet.value) {
    ElMessage.warning('请先上传样例文件并选择工作表')
    return
  }
  tplBusy.value = true
  try {
    const res = await ingestionApi.previewHeader({
      uploadToken: tplToken.value,
      sheetName: tplSheet.value,
      headerRow: tplHeaderRow.value,
    })
    tplColumns.value = res.data.columns || []
    tplSelectedCols.value = [...tplColumns.value]
    if (!tplTargetTable.value && res.data.suggestedTable) {
      tplTargetTable.value = suggestOdsFromFile(res.data.suggestedTable)
    }
    tplForm.templateName = tplForm.templateName.trim() || defaultTplName(tplSheet.value)
    tplForm.templateCode = ''
    ElMessage.success(`已读取第 ${res.data.headerRow} 行表头，共 ${tplColumns.value.length} 列`)
  } catch {
    ElMessage.error('读取表头失败')
  } finally {
    tplBusy.value = false
  }
}

async function saveSheetAsTemplate() {
  if (!tplSourceId.value) {
    ElMessage.warning('请选择归属项目下的 FILE 系统（数据源）；可先在「项目/系统信息登记」新建项目或新增系统')
    return
  }
  if (!tplSheet.value) {
    ElMessage.warning('请选择工作表')
    return
  }
  if (!tplSelectedCols.value.length) {
    ElMessage.warning('请先读取表头并勾选字段')
    return
  }
  if (!tplTargetTable.value.trim()) {
    ElMessage.warning('请填写目标 ODS 表名')
    return
  }
  const name = (tplForm.templateName || '').trim() || defaultTplName(tplSheet.value)
  const code = (tplForm.templateCode || '').trim() || `TPL_${Date.now()}`
  tplBusy.value = true
  try {
    await ingestionApi.createTemplate({
      templateCode: code,
      templateName: name,
      bindings: [{
        sheetName: tplSheet.value,
        headerRow: tplHeaderRow.value,
        columns: [...tplSelectedCols.value],
        targetTable: tplTargetTable.value.trim(),
        assetName: name,
        sourceId: tplSourceId.value,
      }],
    })
    ElMessage.success(`已保存模板「${name}」，并自动登记为数据资产`)
    ingestionRegisterCache.invalidate('tables', 'dataSources')
    tplDialog.value = false
    await loadLists()
  } catch {
    ElMessage.error('保存模板失败')
  } finally {
    tplBusy.value = false
  }
}

async function showTemplateDetail(row: UploadTemplate) {
  tplDetailTitle.value = row.templateName || row.templateCode
  try {
    const res = await ingestionApi.templateBindings(row.templateCode)
    tplDetailRows.value = res.data?.length ? res.data : parseTplBindingsLocal(row.columnMappingJson)
  } catch {
    tplDetailRows.value = parseTplBindingsLocal(row.columnMappingJson)
  }
  tplDetailVisible.value = true
}

async function toggleTemplateStatus(row: UploadTemplate) {
  const next = row.status === 'INACTIVE' ? 'ACTIVE' : 'INACTIVE'
  try {
    await ingestionApi.updateTemplateStatus(row.id, next)
    ElMessage.success(next === 'INACTIVE' ? '已停用' : '已启用')
    await loadLists()
  } catch {
    ElMessage.error('更新状态失败')
  }
}

async function removeTemplate(row: UploadTemplate) {
  try {
    await ElMessageBox.confirm(`确定删除模板「${row.templateName}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await ingestionApi.deleteTemplate(row.id)
    ElMessage.success('已删除模板')
    await loadLists()
  } catch {
    ElMessage.error('删除失败')
  }
}

function resetUploadWizard(clearTemplate: boolean) {
  if (clearTemplate) uploadForm.templateCode = ''
  uploadStep.value = 0
  uploadToken.value = ''
  uploadFileName.value = ''
  sheetOptions.value = []
  selectedSheet.value = ''
  targetTable.value = ''
  boundTableLabel.value = ''
  previewColumns.value = []
  previewRows.value = []
  commitResult.value = ''
  committedSheets.value = []
  remainingSheets.value = []
  activeBindings.value = []
  activeHeaderRow.value = 1
  writeMode.value = 'APPEND'
}

async function onTemplateSelect(code: string) {
  activeBindings.value = []
  sheetOptions.value = []
  selectedSheet.value = ''
  boundTableLabel.value = ''
  if (!code) return
  const meta = templates.value.find((t) => t.templateCode === code)
  if (meta && !isUsableTemplate(meta)) {
    ElMessage.warning('该模板无法用于上传，请重新录入')
    uploadForm.templateCode = ''
    return
  }
  if (meta && meta.status === 'INACTIVE') {
    ElMessage.warning('该模板已停用')
    uploadForm.templateCode = ''
    return
  }
  try {
    const res = await ingestionApi.templateBindings(code)
    activeBindings.value = res.data || []
    if (!activeBindings.value.length) {
      ElMessage.warning('该模板无有效绑定')
      uploadForm.templateCode = ''
      return
    }
    const first = activeBindings.value[0]
    sheetOptions.value = activeBindings.value.map((b) => b.sheetName)
    selectedSheet.value = sheetOptions.value[0]
    applyBindingSheet(selectedSheet.value)
    if (!first.tableId) {
      ElMessage.info('该旧模板尚未登记资产，建议删除后重建以自动同步资产')
    }
  } catch {
    ElMessage.error('加载模板绑定失败')
    uploadForm.templateCode = ''
  }
}

function applyBindingSheet(sheet: string) {
  const b = activeBindings.value.find((x) => x.sheetName === sheet)
    || activeBindings.value.find((x) => x.sheetName.toLowerCase() === String(sheet || '').toLowerCase())
    || (activeBindings.value.length === 1 ? activeBindings.value[0] : undefined)
  if (b) {
    activeHeaderRow.value = b.headerRow
    targetTable.value = b.targetTable
    boundTableLabel.value = b.tableName || (b.tableId ? `#${b.tableId}` : '—')
  }
}

function matchFileSheets(fileSheets: string[], bindings: TplBinding[]) {
  const matched: string[] = []
  for (const b of bindings) {
    const hit = fileSheets.find((s) => s === b.sheetName)
      || fileSheets.find((s) => s.trim().toLowerCase() === b.sheetName.trim().toLowerCase())
    if (hit && !matched.includes(hit)) matched.push(hit)
  }
  if (!matched.length && bindings.length === 1 && fileSheets.length === 1) {
    matched.push(fileSheets[0])
  }
  return matched
}

async function onFileChange(e: Event) {
  if (!uploadForm.templateCode) {
    ElMessage.warning('请先选择模板')
    if (fileInput.value) fileInput.value.value = ''
    return
  }
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  uploadBusy.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const res = await ingestionApi.inspectUpload(fd)
    uploadToken.value = res.data.uploadToken
    uploadFileName.value = res.data.fileName
    const matched = matchFileSheets(res.data.sheets || [], activeBindings.value)
    if (!matched.length) {
      ElMessage.error('文件工作表与模板绑定不一致')
      uploadStep.value = 0
      return
    }
    sheetOptions.value = matched
    selectedSheet.value = matched[0]
    applyBindingSheet(selectedSheet.value)
    committedSheets.value = []
    remainingSheets.value = [...matched]
    uploadStep.value = 1
    ElMessage.success('文件已识别，请校验预览后写入')
  } catch {
    ElMessage.error('解析文件失败')
  } finally {
    uploadBusy.value = false
    if (fileInput.value) fileInput.value.value = ''
  }
}

async function loadSheetPreview() {
  if (!uploadToken.value || !selectedSheet.value || !uploadForm.templateCode) {
    ElMessage.warning('请先选模板和文件')
    return
  }
  uploadBusy.value = true
  try {
    applyBindingSheet(selectedSheet.value)
    const res = await ingestionApi.previewUpload({
      uploadToken: uploadToken.value,
      sheetName: selectedSheet.value,
      templateCode: uploadForm.templateCode,
    })
    previewColumns.value = res.data.columns || []
    previewRows.value = res.data.rows || []
    if (res.data.targetTable) targetTable.value = res.data.targetTable
    uploadStep.value = 2
    ElMessage.success(`校验通过，预览 ${previewRows.value.length} 行`)
  } catch {
    ElMessage.error('校验失败：字段须与模板一致')
    uploadStep.value = 1
  } finally {
    uploadBusy.value = false
  }
}

async function commitToOds() {
  if (uploadStep.value < 2) {
    ElMessage.warning('请先校验预览')
    return
  }
  uploadBusy.value = true
  try {
    const mode = writeMode.value
    const res = await ingestionApi.commitUpload({
      uploadToken: uploadToken.value,
      sheetName: selectedSheet.value,
      templateCode: uploadForm.templateCode,
      writeMode: mode,
    })
    commitResult.value = String(res.data.message || '写入成功')
    committedSheets.value = res.data.committedSheets || []
    remainingSheets.value = res.data.remainingSheets || []
    const tableId = res.data.tableId
    const rows = res.data.rowCount ?? 0
    const odsTable = res.data.targetTable || targetTable.value
    await loadLists()
    uploadStep.value = remainingSheets.value.length ? 1 : 3
    if (remainingSheets.value.length) {
      selectedSheet.value = remainingSheets.value[0]
      applyBindingSheet(selectedSheet.value)
      previewColumns.value = []
      previewRows.value = []
    }

    const goDataItems = async () => {
      uploadDialog.value = false
      await router.push({
        path: '/exchange/ingestion',
        query: {
          system: 'register',
          module: 'm044',
          ...(tableId != null ? { tableId: String(tableId) } : {}),
        },
      })
    }

    if (remainingSheets.value.length) {
      await ElMessageBox.alert(
        `本表已写入（资产#${tableId ?? '—'}），还有 ${remainingSheets.value.length} 个 Sheet 待处理，请继续校验并写入`,
        '部分写入成功',
        { confirmButtonText: '继续', type: 'success' },
      )
      return
    }

    if (mode === 'REPLACE') {
      await ElMessageBox.alert(
        `已绑定资产#${tableId ?? '—'}，请在数据项管理中核对并补充字段名称/注释`,
        '全量覆盖写入成功',
        { confirmButtonText: '前往数据项管理', type: 'success' },
      )
      await goDataItems()
      return
    }

    await ElMessageBox.alert(
      `增量写入成功：smart_city_ods.${odsTable}（${rows} 行）`,
      '写入成功',
      { confirmButtonText: '前往数据项管理', type: 'success' },
    )
    await goDataItems()
  } catch (err: unknown) {
    const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '写入 ODS 失败')
  } finally {
    uploadBusy.value = false
  }
}

async function finishAndClose() {
  if (uploadToken.value) {
    try {
      await ingestionApi.finishUpload({ uploadToken: uploadToken.value })
    } catch {
      /* ignore */
    }
  }
  resetUploadWizard(true)
}

async function onUploadDialogClosed() {
  await finishAndClose()
}

onMounted(() => {
  loadLists()
})
</script>

<template>
  <div class="manual-upload">
    <PageCard>
      <template #header>
        <div class="wiz-head">
          <div>
            <div class="wiz-title">手动上传数据</div>
            <div class="wiz-sub">
              新建模板时自动登记为数据资产（含字段），数据写入 smart_city_ods，可供目录/发布引用
            </div>
          </div>
          <div class="wiz-actions">
            <el-button type="primary" @click="openCreateTemplate">新建模板</el-button>
            <el-button type="success" @click="openUploadDialog">上传数据</el-button>
          </div>
        </div>
      </template>

      <el-radio-group v-model="listTab" size="small" style="margin-bottom: 12px">
        <el-radio-button value="templates">上传模板</el-radio-button>
        <el-radio-button value="uploads">上传记录</el-radio-button>
      </el-radio-group>

      <el-table
        v-if="listTab === 'templates'"
        :data="listedTemplates"
        stripe
        size="small"
        empty-text="暂无模板，请点击右上角新建"
      >
        <el-table-column prop="templateName" label="模板" min-width="140" align="center" header-align="center" />
        <el-table-column prop="templateCode" label="编码" width="150" align="center" header-align="center" show-overflow-tooltip />
        <el-table-column label="同步资产" min-width="140" align="center" header-align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ templateAssetLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="ODS表" min-width="140" align="center" header-align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ templateTarget(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center" header-align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="showTemplateDetail(row)">详情</el-button>
            <el-button link :type="row.status === 'INACTIVE' ? 'success' : 'warning'" @click="toggleTemplateStatus(row)">
              {{ row.status === 'INACTIVE' ? '启用' : '停用' }}
            </el-button>
            <el-button link type="danger" @click="removeTemplate(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-table
        v-else
        :data="uploads"
        stripe
        size="small"
        empty-text="暂无上传记录"
      >
        <el-table-column prop="fileName" label="文件" min-width="140" align="center" header-align="center" show-overflow-tooltip />
        <el-table-column prop="sheetName" label="Sheet" width="100" align="center" header-align="center" />
        <el-table-column prop="targetTable" label="ODS表" min-width="140" align="center" header-align="center" show-overflow-tooltip />
        <el-table-column prop="templateCode" label="模板" width="120" align="center" header-align="center" show-overflow-tooltip />
        <el-table-column prop="rowCount" label="行数" width="80" align="center" header-align="center" />
        <el-table-column label="状态" width="100" align="center" header-align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <!-- 新建模板弹窗 -->
    <el-dialog v-model="tplDialog" title="新建上传模板" width="820px" destroy-on-close append-to-body>
      <p class="hint">
        选择归属项目与系统后保存模板，将自动创建数据资产。可点「+」当场新建系统名称，并同步到资产登记。
      </p>
      <el-form label-width="110px" class="portal-inline-form portal-inline-form--block">
        <el-form-item label="归属项目" class="portal-field-xl" required>
          <el-select
            v-model="tplProjectId"
            filterable
            placeholder="选择项目"
            @change="onTplProjectChange"
          >
            <el-option
              v-for="p in projects"
              :key="p.id"
              :label="isOtherProject(p.projectCode) ? `${p.projectName}（系统默认）` : p.projectName"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="归属系统" class="portal-field-xl portal-field-with-addon" required>
          <div class="field-with-plus">
            <el-select
              v-model="tplSourceId"
              filterable
              placeholder="选择 FILE 数据源/系统"
              :disabled="!selectableFileSources.length && !tplProjectId"
            >
              <el-option
                v-for="s in selectableFileSources"
                :key="s.id"
                :label="sourceOptionLabel(s)"
                :value="s.id"
              />
            </el-select>
            <el-button
              type="primary"
              plain
              class="plus-btn"
              :disabled="!tplProjectId"
              title="新建系统并同步到资产登记"
              @click="openAddSystemDialog"
            >+</el-button>
          </div>
        </el-form-item>
        <p v-if="tplProjectId && !selectableFileSources.length" class="hint">
          当前项目下暂无 FILE 系统，请点击「+」填写系统名称创建。
        </p>
        <el-form-item label="样例文件" class="portal-form-actions">
          <el-button type="primary" :loading="tplBusy" @click="tplFileInput?.click()">选择 Excel/CSV</el-button>
          <input ref="tplFileInput" type="file" accept=".xlsx,.xls,.csv" style="display:none" @change="onTplFileChange" />
        </el-form-item>
        <p v-if="tplFileName" class="hint">当前文件：{{ tplFileName }}</p>
        <template v-if="tplToken">
          <el-form-item label="工作表" class="portal-field-lg">
            <el-select v-model="tplSheet" filterable>
              <el-option v-for="s in tplSheets" :key="s" :label="s" :value="s" />
            </el-select>
          </el-form-item>
          <el-form-item label="表头行" class="portal-field-xs">
            <el-input-number v-model="tplHeaderRow" :min="1" :max="200" controls-position="right" />
          </el-form-item>
          <el-form-item label="模板名称" class="portal-field-lg">
            <el-input v-model="tplForm.templateName" placeholder="同时作为资产名称" />
          </el-form-item>
          <el-form-item label="目标 ODS" class="portal-field-xl">
            <el-input v-model="tplTargetTable" placeholder="ods_xxx" />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button :loading="tplBusy" @click="loadTplHeader">读取表头</el-button>
            <el-button type="success" :loading="tplBusy" :disabled="!tplSelectedCols.length" @click="saveSheetAsTemplate">
              保存模板
            </el-button>
          </el-form-item>
        </template>
        <div v-if="tplColumns.length" style="margin-top: 8px">
          <div class="hint" style="margin-bottom: 6px">纳入模板的字段：</div>
          <el-checkbox-group v-model="tplSelectedCols">
            <el-checkbox v-for="c in tplColumns" :key="c" :label="c" :value="c">{{ c }}</el-checkbox>
          </el-checkbox-group>
        </div>
      </el-form>
    </el-dialog>

    <el-dialog
      v-model="addSysDialog"
      title="新建归属系统"
      width="420px"
      append-to-body
      destroy-on-close
    >
      <p class="hint">将在当前归属项目下创建 FILE 数据源，并同步到「项目/系统信息登记」。</p>
      <el-form label-width="100px">
        <el-form-item label="系统名称" required>
          <el-input
            v-model="addSysForm.systemName"
            placeholder="如：人社局_就业登记系统"
            maxlength="80"
            @keyup.enter="submitAddSystem"
          />
        </el-form-item>
        <el-form-item label="数据源名">
          <el-input v-model="addSysForm.sourceName" placeholder="默认：手动上传" maxlength="80" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addSysDialog = false">取消</el-button>
        <el-button type="primary" :loading="addSysBusy" @click="submitAddSystem">创建并选用</el-button>
      </template>
    </el-dialog>

    <!-- 上传数据弹窗 -->
    <el-dialog
      v-model="uploadDialog"
      title="按模板上传数据"
      width="860px"
      destroy-on-close
      append-to-body
      @closed="onUploadDialogClosed"
    >
      <el-steps :active="uploadStep" align-center finish-status="success" style="margin-bottom: 16px">
        <el-step title="选模板" />
        <el-step title="选文件" />
        <el-step title="校验预览" />
        <el-step title="写入 ODS" />
      </el-steps>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="模板" class="portal-field-default">
          <el-select
            v-model="uploadForm.templateCode"
            clearable
            placeholder="已启用模板"
            @change="onTemplateSelect"
          >
            <el-option
              v-for="t in selectableTemplates"
              :key="t.id"
              :label="t.templateName"
              :value="t.templateCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" :loading="uploadBusy" :disabled="!uploadForm.templateCode" @click="fileInput?.click()">
            选择数据文件
          </el-button>
        </el-form-item>
        <input ref="fileInput" type="file" accept=".xlsx,.xls,.csv" style="display:none" @change="onFileChange" />
      </el-form>
      <p v-if="uploadFileName" class="hint">当前文件：{{ uploadFileName }}</p>
      <el-form v-if="uploadStep >= 1" inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="工作表" class="portal-field-lg">
          <el-select v-model="selectedSheet" filterable @change="applyBindingSheet">
            <el-option
              v-for="s in sheetOptions"
              :key="s"
              :label="committedSheets.includes(s) ? `${s}（已写入）` : s"
              :value="s"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="同步资产" class="portal-field-xl">
          <el-input :model-value="boundTableLabel" disabled />
        </el-form-item>
        <el-form-item label="目标表" class="portal-field-xl">
          <el-input v-model="targetTable" disabled />
        </el-form-item>
        <el-form-item label="写入方式" class="portal-field-lg">
          <el-select v-model="writeMode">
            <el-option label="增量写入" value="APPEND" />
            <el-option label="全量覆盖" value="REPLACE" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button :loading="uploadBusy" @click="loadSheetPreview">校验并预览</el-button>
          <el-button type="primary" :loading="uploadBusy" :disabled="uploadStep < 2" @click="commitToOds">
            {{ writeMode === 'APPEND' ? '增量写入 ODS' : '全量覆盖写入 ODS' }}
          </el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="commitResult" type="success" :closable="true" show-icon :title="commitResult" style="margin-bottom: 12px" />
      <el-table
        v-if="previewColumns.length"
        :data="previewRows"
        stripe
        size="small"
        max-height="320"
        style="width: 100%"
      >
        <el-table-column
          v-for="col in previewColumns"
          :key="col"
          :prop="col"
          :label="col"
          min-width="120"
          align="center"
          header-align="center"
          show-overflow-tooltip
        />
      </el-table>
      <template #footer>
        <el-button @click="uploadDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="tplDetailVisible" :title="`模板详情 · ${tplDetailTitle}`" width="720px" destroy-on-close append-to-body>
      <el-table :data="tplDetailRows" stripe size="small" max-height="420">
        <el-table-column prop="sheetName" label="工作表" width="120" align="center" header-align="center" />
        <el-table-column prop="headerRow" label="表头行" width="80" align="center" header-align="center" />
        <el-table-column label="同步资产" min-width="120" align="center" header-align="center">
          <template #default="{ row }">{{ row.tableName || (row.tableId ? `#${row.tableId}` : '—') }}</template>
        </el-table-column>
        <el-table-column prop="targetTable" label="目标表" min-width="140" align="center" header-align="center" />
        <el-table-column label="字段" min-width="200" align="center" header-align="center">
          <template #default="{ row }">
            <el-tag v-for="c in row.columns || []" :key="c" size="small" style="margin: 2px 4px 2px 0">{{ c }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.wiz-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}
.wiz-title {
  font-size: 15px;
  font-weight: 600;
}
.wiz-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--portal-text-secondary, #909399);
}
.wiz-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: #909399;
}
.field-with-plus {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.field-with-plus :deep(.el-select) {
  flex: 1;
  min-width: 0;
}
.plus-btn {
  flex-shrink: 0;
  width: 32px;
  padding: 0;
  font-size: 18px;
  font-weight: 600;
  line-height: 1;
}
</style>
