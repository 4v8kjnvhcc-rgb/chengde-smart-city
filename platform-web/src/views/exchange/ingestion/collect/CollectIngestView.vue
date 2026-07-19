<script setup lang="ts">

import { computed, onMounted, reactive, ref, watch } from 'vue'

import { useRoute, useRouter } from 'vue-router'

import PageCard from '@/components/common/PageCard.vue'
import { ElMessage } from 'element-plus'
import { collectIngestMainTab, type IngestMainTab } from '../ingestion-nav'
import StructuredTableWizard from './StructuredTableWizard.vue'
import ManualUploadView from './ManualUploadView.vue'

import { ingestionRegisterCache } from '../ingestion-register-cache'

import {

  ingestionApi,

  useIngestionLoading,

  type Channel,

  type DataTable,

  type IngestTask,

  type Upload,

  type UploadTemplate,

} from '../useIngestionHub'



const route = useRoute()

const router = useRouter()

const { loading, loadError, withLoad } = useIngestionLoading()



const MAIN_TABS: { key: IngestMainTab; label: string }[] = [

  { key: 'structured', label: '结构化数据接入' },

  { key: 'file', label: '文件上传' },

  { key: 'other', label: '其他数据接入' },

]



const FILE_MODES = [

  { key: 'file-remote', type: 'FTP', label: '远程文件接入' },

  { key: 'file-local', type: 'LOCAL', label: '本地文件上传' },

]



const OTHER_MODES = [

  { key: 'other-unstruct', type: 'UNSTRUCT', label: '非结构化数据接入' },

  { key: 'other-semi', type: 'SEMI', label: '半结构化数据接入' },

  { key: 'other-api', type: 'API', label: 'API 接口数据接入' },

  { key: 'other-cdc', type: 'CDC', label: 'CDC 实时数据接入' },

]



type ViewScope = 'structured-table' | 'structured-upload' | string



const structuredSub = ref('structured-table')

const fileSub = ref('file-remote')

const otherSub = ref('other-api')



const mainTab = ref<IngestMainTab>(collectIngestMainTab(route.query as Record<string, unknown>))

const channels = ref<Channel[]>([])

const tasks = ref<IngestTask[]>([])

const tables = ref<DataTable[]>([])

const templates = ref<UploadTemplate[]>([])

const uploads = ref<Upload[]>([])



const channelsByType = ref<Record<string, Channel[]>>({})

const loadedScopes = new Set<ViewScope>()

let activeScope: ViewScope | null = null



const selectedChannelId = ref<number | undefined>()

const channelForm = reactive<Record<string, string>>({})

const taskForm = reactive({ channelId: undefined as number | undefined, taskName: '', scheduleCron: '0 2 * * *' })

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

const uploadForm = reactive({ templateCode: '' })
const fileInput = ref<HTMLInputElement>()
const uploadStep = ref(0)
const uploadBusy = ref(false)
const uploadToken = ref('')
const uploadFileName = ref('')
const sheetOptions = ref<string[]>([])
const selectedSheet = ref('')
const targetTable = ref('')
const previewColumns = ref<string[]>([])
const previewRows = ref<Record<string, string>[]>([])
const commitResult = ref('')
const committedSheets = ref<string[]>([])
const remainingSheets = ref<string[]>([])
const activeBindings = ref<Array<{ sheetName: string; headerRow: number; columns: string[]; targetTable: string }>>([])
const activeHeaderRow = ref(1)
/** APPEND=字段一致时增量写入；REPLACE=全量覆盖 */
const writeMode = ref<'APPEND' | 'REPLACE'>('APPEND')



const activeChannelType = computed(() => {

  if (mainTab.value === 'structured') return 'TABLE'

  if (mainTab.value === 'file') return FILE_MODES.find((m) => m.key === fileSub.value)?.type

  return OTHER_MODES.find((m) => m.key === otherSub.value)?.type

})



const filteredChannels = computed(() => channels.value.filter((c) => c.channelType === activeChannelType.value))

const selectedChannel = computed(() => filteredChannels.value.find((c) => c.id === selectedChannelId.value))



function currentScope(): ViewScope {

  if (mainTab.value === 'structured') return structuredSub.value

  if (mainTab.value === 'file') return fileSub.value

  return otherSub.value

}



function parseConfig(json?: string): Record<string, string> {

  if (!json) return {}

  try {

    const o = JSON.parse(json) as Record<string, unknown>

    const out: Record<string, string> = {}

    for (const [k, v] of Object.entries(o)) out[k] = String(v ?? '')

    return out

  } catch {

    return {}

  }

}



function loadChannelForm(ch?: Channel) {

  const cfg = parseConfig(ch?.configJson)

  Object.keys(channelForm).forEach((k) => delete channelForm[k])

  const fields = configFields(ch?.channelType || activeChannelType.value || 'TABLE')

  for (const f of fields) channelForm[f.key] = cfg[f.key] ?? f.defaultValue ?? ''

  // 未保存过目标表时，按源表自动带出 ods_表名

  if ((ch?.channelType || activeChannelType.value) === 'TABLE') {

    ensureTargetTableFromSource(false)

  }

}



/** 由源登记表推导 ODS 目标表名：ods_源物理表名 */
function suggestOdsTableName(tb?: DataTable): string {

  const raw = String(tb?.sourceTable || tb?.tableName || tb?.tableCode || '').trim()

  const sanitized = raw.replace(/[^A-Za-z0-9_]/g, '')

  if (!sanitized) return ''

  return sanitized.toLowerCase().startsWith('ods_') ? sanitized : `ods_${sanitized}`

}



/**
 * @param force 切换源表时强制覆盖；加载已有配置时仅在目标表为空时填充
 */
function ensureTargetTableFromSource(force: boolean) {

  const sid = String(channelForm.sourceTableId || '').trim()

  if (!sid) return

  const tb = tables.value.find((t) => String(t.id) === sid)

  const suggested = suggestOdsTableName(tb)

  if (!suggested) return

  if (force || !String(channelForm.targetTable || '').trim()) {

    channelForm.targetTable = suggested

  }

}



function syncChannelSelection() {

  if (filteredChannels.value.length) {

    if (!filteredChannels.value.some((c) => c.id === selectedChannelId.value)) {

      selectedChannelId.value = filteredChannels.value[0].id

    }

    loadChannelForm(selectedChannel.value)

    taskForm.channelId = selectedChannelId.value

  } else {

    selectedChannelId.value = undefined

  }

}



function configFields(type: string) {

  switch (type) {

    case 'TABLE':

      return [

        { key: 'syncMode', label: '同步方式', defaultValue: 'T+1', hint: '立即执行始终即时抽数；T+1 供定时任务约定' },

        { key: 'sourceTableId', label: '源表（登记）', defaultValue: '', hint: '选用登记侧已登记的物理表' },

        { key: 'targetTable', label: '目标表', defaultValue: '', hint: '默认 ods_源表名，可改' },

        { key: 'mappingMode', label: '字段映射', defaultValue: 'auto', hint: 'auto 自动（按登记字段）' },

      ]

    case 'FTP':

      return [

        { key: 'host', label: 'FTP 主机', defaultValue: '' },

        { key: 'port', label: '端口', defaultValue: '21' },

        { key: 'username', label: '用户名', defaultValue: '' },

        { key: 'password', label: '密码', defaultValue: '' },

        { key: 'remotePath', label: '远程目录', defaultValue: '/' },

        { key: 'filePattern', label: '文件匹配', defaultValue: '*.csv' },

      ]

    case 'LOCAL':

      return [

        { key: 'localPath', label: '本地目录', defaultValue: '' },

        { key: 'writeMode', label: '写入模式', defaultValue: 'append', hint: 'append 追加 / overwrite 覆盖' },

      ]

    case 'UNSTRUCT':

      return [

        { key: 'ftpHost', label: 'FTP 主机', defaultValue: '' },

        { key: 'storagePath', label: '对象存储路径', defaultValue: '/data/unstruct' },

      ]

    case 'SEMI':

      return [

        { key: 'broker', label: '消息中间件', defaultValue: '' },

        { key: 'topic', label: 'Topic', defaultValue: '' },

        { key: 'dataFormat', label: '数据格式', defaultValue: 'json', hint: 'json / xml' },

      ]

    case 'API':

      return [

        { key: 'url', label: '接口地址', defaultValue: '' },

        { key: 'method', label: '请求方式', defaultValue: 'GET' },

        { key: 'retryCount', label: '失败重试次数', defaultValue: '3' },

      ]

    case 'CDC':

      return [

        { key: 'canalHost', label: 'Canal 地址', defaultValue: 'localhost:19090' },

        { key: 'sourceDb', label: '源库', defaultValue: '' },

        { key: 'targetTable', label: '目标表', defaultValue: '' },

      ]

    default:

      return []

  }

}



const currentConfigFields = computed(() => configFields(activeChannelType.value || 'TABLE'))



function syncSectionQuery() {

  router.replace({ query: { ...route.query, section: currentScope() } })

}



function applySectionFromRoute() {

  const sec = String(route.query.section || '')

  if (sec.startsWith('file-')) fileSub.value = sec

  else if (sec.startsWith('other-')) otherSub.value = sec

  else if (sec.startsWith('structured-')) structuredSub.value = sec

  mainTab.value = collectIngestMainTab(route.query as Record<string, unknown>)

}



async function loadChannels(type: string, force = false) {

  if (!force && channelsByType.value[type]) {

    channels.value = channelsByType.value[type]

    syncChannelSelection()

    return

  }

  const res = await ingestionApi.channels(type)

  channelsByType.value[type] = res.data

  channels.value = res.data

  syncChannelSelection()

}



async function loadTasks(force = false) {

  if (!force && tasks.value.length) return

  tasks.value = (await ingestionApi.tasks()).data

}



async function loadTables(force = false) {

  tables.value = await ingestionRegisterCache.tables(force)

}



async function loadTemplates(force = false) {

  if (!force && templates.value.length) return

  templates.value = (await ingestionApi.templates()).data

  if (templates.value.length && !uploadForm.templateCode) {

    uploadForm.templateCode = templates.value[0].templateCode

  }

}



async function loadUploads(force = false) {

  if (!force && uploads.value.length) return

  uploads.value = (await ingestionApi.uploads()).data

}



async function loadScopeData(scope: ViewScope, opts?: { force?: boolean; silent?: boolean }) {

  const force = opts?.force ?? false

  const run = async () => {

    if (scope === 'structured-table') {

      await Promise.all([

        loadChannels('TABLE', force),

        loadTasks(force),

        loadTables(force),

      ])

    } else if (scope === 'structured-upload') {
      // ManualUploadView 自行加载模板/记录
    } else {

      const type = activeChannelType.value

      if (type) await loadChannels(type, force)

    }

    loadedScopes.add(scope)

    activeScope = scope

  }

  if (opts?.silent) {

    try { await run() } catch { /* 局部刷新失败不打断操作 */ }

    return

  }

  await withLoad(run)

}



async function ensureScopeLoaded(scope = currentScope(), force = false) {

  if (!force && loadedScopes.has(scope) && activeScope === scope) return

  await loadScopeData(scope)

}



function onMainTabChange(key: string | number) {

  mainTab.value = key as IngestMainTab

  syncSectionQuery()

  ensureScopeLoaded()

}



function onStructuredSubChange() {

  syncSectionQuery()

  ensureScopeLoaded()

}



function onFileSubChange() {

  syncSectionQuery()

  ensureScopeLoaded()

}



function onOtherSubChange() {

  syncSectionQuery()

  ensureScopeLoaded()

}



async function saveChannelConfig() {

  if (!selectedChannelId.value) return

  await ingestionApi.updateChannel(selectedChannelId.value, {

    channelName: selectedChannel.value?.channelName,

    config: { ...channelForm },

  })

  const type = activeChannelType.value

  if (type) await loadScopeData(currentScope(), { force: true, silent: true })

}



const runBusy = ref(false)



async function runChannel() {

  if (!selectedChannelId.value) return

  if (activeChannelType.value === 'TABLE') {

    const sid = String(channelForm.sourceTableId || '').trim()

    if (!sid) {

      ElMessage.warning('请先选择源表并保存接入配置')

      return

    }

  }

  runBusy.value = true

  try {

    // 先落盘配置，保证后端读到最新源表/目标表

    await ingestionApi.updateChannel(selectedChannelId.value, {

      channelName: selectedChannel.value?.channelName,

      config: { ...channelForm },

    })

    const res = await ingestionApi.runChannel(selectedChannelId.value)

    const data = (res as { data?: Record<string, unknown> }).data || {}

    if (activeChannelType.value === 'TABLE') {

      const rows = data.collectedRows

      ElMessage.success(

        typeof rows !== 'undefined'

          ? `已落入 smart_city_ods.${data.odsTable || ''}（${rows} 行）`

          : String(data.message || '汇聚完成'),

      )

    } else {

      ElMessage.success(String(data.message || '执行完成'))

    }

    const type = activeChannelType.value

    if (type) await loadScopeData(currentScope(), { force: true, silent: true })

  } catch {

    // request 拦截器已提示

  } finally {

    runBusy.value = false

  }

}



async function createTask() {

  if (!taskForm.taskName || !taskForm.channelId) return

  await ingestionApi.createTask({ ...taskForm })

  taskForm.taskName = ''

  await loadTasks(true)

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
    tplTargetTable.value = res.data.suggestedTable || ''
    ElMessage.success(`已识别 ${tplSheets.value.length} 个工作表，请指定表头行并选择字段`)
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
    if (res.data.suggestedTable) tplTargetTable.value = res.data.suggestedTable
    // 每个 sheet 一个模板：读表头后默认带出名称，可改
    tplForm.templateName = defaultTplName(tplSheet.value)
    tplForm.templateCode = ''
    ElMessage.success(`已读取第 ${res.data.headerRow} 行表头，共 ${tplColumns.value.length} 列；确认后点「保存为模板」`)
  } catch {
    ElMessage.error('读取表头失败')
  } finally {
    tplBusy.value = false
  }
}

/** 新版模板：columnMappingJson 含 bindings；旧种子仅有 col/target，不可用于上传 */
function isUsableTemplate(t: { columnMappingJson?: string }) {
  const j = t.columnMappingJson || ''
  return j.includes('"bindings"') && j.includes('sheetName')
}

/** 列表展示：有 bindings 的模板（含已停用） */
const listedTemplates = computed(() => templates.value.filter(isUsableTemplate))
/** 上传下拉：仅启用中的模板 */
const selectableTemplates = computed(() =>
  listedTemplates.value.filter((t) => !t.status || t.status === 'ACTIVE'),
)

type TplBindingDetail = { sheetName: string; headerRow: number; columns: string[]; targetTable: string }
const tplDetailVisible = ref(false)
const tplDetailTitle = ref('')
const tplDetailRows = ref<TplBindingDetail[]>([])

function parseTplBindingsLocal(json?: string): TplBindingDetail[] {
  if (!json) return []
  try {
    const root = JSON.parse(json) as { bindings?: TplBindingDetail[] }
    return Array.isArray(root.bindings) ? root.bindings : []
  } catch {
    return []
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
    if (next === 'INACTIVE' && uploadForm.templateCode === row.templateCode) {
      uploadForm.templateCode = ''
      activeBindings.value = []
    }
    ElMessage.success(next === 'INACTIVE' ? '已停用，上传时不可再选' : '已启用')
    await loadTemplates(true)
  } catch {
    ElMessage.error('更新状态失败')
  }
}

async function removeTemplate(row: UploadTemplate) {
  try {
    await ingestionApi.deleteTemplate(row.id)
    if (uploadForm.templateCode === row.templateCode) {
      uploadForm.templateCode = ''
      activeBindings.value = []
    }
    ElMessage.success('已删除模板')
    await loadTemplates(true)
  } catch {
    ElMessage.error('删除失败')
  }
}

/** 当前 sheet 立刻存成一条独立模板（一 sheet = 一模板） */
async function saveSheetAsTemplate() {
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
      }],
    })
    ElMessage.success(`已保存模板「${name}」（仅含工作表 ${tplSheet.value}）`)
    await loadTemplates(true)
    uploadForm.templateCode = code
    await onTemplateSelect(code)
    // 保留样例会话，方便换下一个 sheet 再建一条模板
    tplForm.templateName = ''
    tplForm.templateCode = ''
    tplColumns.value = []
    tplSelectedCols.value = []
    const next = tplSheets.value.find((s) => s !== tplSheet.value)
    if (next) {
      tplSheet.value = next
      tplHeaderRow.value = 1
      tplTargetTable.value = ''
      ElMessage.info(`可继续为工作表「${next}」读取表头并保存为另一条模板`)
    }
  } catch {
    ElMessage.error('保存模板失败')
  } finally {
    tplBusy.value = false
  }
}

async function onTemplateSelect(code: string) {
  activeBindings.value = []
  sheetOptions.value = []
  selectedSheet.value = ''
  if (!code) return
  const meta = templates.value.find((t) => t.templateCode === code)
  if (meta && !isUsableTemplate(meta)) {
    ElMessage.warning('「' + (meta.templateName || code) + '」无法用于上传，请重新录入模板')
    uploadForm.templateCode = ''
    return
  }
  if (meta && meta.status === 'INACTIVE') {
    ElMessage.warning('该模板已停用，请先启用或另选模板')
    uploadForm.templateCode = ''
    return
  }
  try {
    const res = await ingestionApi.templateBindings(code)
    activeBindings.value = res.data || []
    if (!activeBindings.value.length) {
      ElMessage.warning('该模板无有效 sheet 绑定，请重新录入')
      uploadForm.templateCode = ''
      return
    }
    sheetOptions.value = activeBindings.value.map((b) => b.sheetName)
    selectedSheet.value = sheetOptions.value[0]
    applyBindingSheet(selectedSheet.value)
  } catch {
    ElMessage.error('加载模板绑定失败（若为旧版模板请重新录入）')
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
  }
}

function matchFileSheets(fileSheets: string[], bindings: typeof activeBindings.value) {
  const matched: string[] = []
  for (const b of bindings) {
    const hit = fileSheets.find((s) => s === b.sheetName)
      || fileSheets.find((s) => s.trim().toLowerCase() === b.sheetName.trim().toLowerCase())
    if (hit && !matched.includes(hit)) matched.push(hit)
  }
  // 模板只绑了一个 sheet、文件也只有一个 sheet 时，按同表处理（名称可不同）
  if (!matched.length && bindings.length === 1 && fileSheets.length === 1) {
    matched.push(fileSheets[0])
  }
  return matched
}

async function onFileChange(e: Event) {
  if (!uploadForm.templateCode) {
    ElMessage.warning('请先选择下方「可用」部门模板（旧示范模板不可用）')
    if (fileInput.value) fileInput.value.value = ''
    return
  }
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  uploadBusy.value = true
  commitResult.value = ''
  try {
    if (!activeBindings.value.length) await onTemplateSelect(uploadForm.templateCode)
    if (!activeBindings.value.length) {
      uploadStep.value = 0
      return
    }
    const fd = new FormData()
    fd.append('file', file)
    const res = await ingestionApi.inspectUpload(fd)
    const data = res.data
    uploadToken.value = data.uploadToken
    uploadFileName.value = data.fileName
    const fileSheets = data.sheets || []
    sheetOptions.value = matchFileSheets(fileSheets, activeBindings.value)
    if (!sheetOptions.value.length) {
      ElMessage.error(
        `文件工作表 [${fileSheets.join('、')}] 与模板绑定 [${activeBindings.value.map((b) => b.sheetName).join('、')}] 对不上。请用同结构样例重新录入模板，或保证 sheet 名一致`,
      )
      uploadStep.value = 0
      return
    }
    selectedSheet.value = sheetOptions.value[0]
    applyBindingSheet(selectedSheet.value)
    previewColumns.value = []
    previewRows.value = []
    committedSheets.value = []
    remainingSheets.value = [...sheetOptions.value]
    uploadStep.value = 1
    ElMessage.success('已匹配工作表；字段校验通过后可选增量或全量写入')
  } catch {
    ElMessage.error('解析文件失败')
  } finally {
    uploadBusy.value = false
    if (fileInput.value) fileInput.value.value = ''
  }
}

async function loadSheetPreview() {
  if (!uploadToken.value || !selectedSheet.value || !uploadForm.templateCode) {
    ElMessage.warning('请先选择模板、文件与工作表')
    return
  }
  applyBindingSheet(selectedSheet.value)
  uploadBusy.value = true
  try {
    const res = await ingestionApi.previewUpload({
      uploadToken: uploadToken.value,
      sheetName: selectedSheet.value,
      templateCode: uploadForm.templateCode,
      limit: 50,
    })
    previewColumns.value = res.data.columns || []
    previewRows.value = res.data.rows || []
    targetTable.value = res.data.targetTable || targetTable.value
    activeHeaderRow.value = res.data.headerRow || activeHeaderRow.value
    uploadStep.value = 2
    ElMessage.success(`字段校验通过，预览 ${res.data.previewRows} 行`)
  } catch (err: unknown) {
    const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '预览失败：字段与模板不一致时请新建模板')
  } finally {
    uploadBusy.value = false
  }
}

async function commitToOds() {
  if (!uploadToken.value || !selectedSheet.value || !uploadForm.templateCode) {
    ElMessage.warning('请先完成模板选择与预览')
    return
  }
  uploadBusy.value = true
  try {
    const res = await ingestionApi.commitUpload({
      uploadToken: uploadToken.value,
      sheetName: selectedSheet.value,
      templateCode: uploadForm.templateCode,
      writeMode: writeMode.value,
    })
    commitResult.value = res.data.message || '写入成功'
    committedSheets.value = res.data.committedSheets || [...committedSheets.value, selectedSheet.value]
    remainingSheets.value = res.data.remainingSheets || []
    ElMessage.success(commitResult.value)
    await loadUploads(true)
    previewColumns.value = []
    previewRows.value = []
    uploadStep.value = 1
    if (remainingSheets.value.length) {
      selectedSheet.value = remainingSheets.value[0]
      applyBindingSheet(selectedSheet.value)
    } else {
      uploadStep.value = 3
    }
  } catch (err: unknown) {
    const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '写入 ODS 失败')
  } finally {
    uploadBusy.value = false
  }
}

async function resetUploadWizard() {
  if (uploadToken.value) {
    try {
      await ingestionApi.finishUpload({ uploadToken: uploadToken.value })
    } catch { /* ignore */ }
  }
  uploadStep.value = 0
  uploadToken.value = ''
  uploadFileName.value = ''
  sheetOptions.value = activeBindings.value.map((b) => b.sheetName)
  selectedSheet.value = sheetOptions.value[0] || ''
  targetTable.value = ''
  previewColumns.value = []
  previewRows.value = []
  commitResult.value = ''
  committedSheets.value = []
  remainingSheets.value = []
  writeMode.value = 'APPEND'
  if (selectedSheet.value) applyBindingSheet(selectedSheet.value)
}



watch(selectedSheet, (sheet) => {
  if (!uploadToken.value || !sheet) return
  previewColumns.value = []
  previewRows.value = []
  if (uploadStep.value > 1) uploadStep.value = 1
  applyBindingSheet(sheet)
})

watch(selectedChannelId, () => loadChannelForm(selectedChannel.value))

watch(() => channelForm.sourceTableId, (id, prev) => {

  if (activeChannelType.value !== 'TABLE') return

  if (!id || id === prev) return

  ensureTargetTableFromSource(true)

})

watch(() => route.query.section, () => {

  applySectionFromRoute()

  ensureScopeLoaded()

})

onMounted(() => {

  applySectionFromRoute()

  ensureScopeLoaded()

})

</script>



<template>

  <div v-loading="loading">

    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />

    <el-tabs :model-value="mainTab" @tab-change="onMainTabChange">

      <el-tab-pane v-for="t in MAIN_TABS" :key="t.key" :label="t.label" :name="t.key" />

    </el-tabs>



    <!-- 结构化：库表接入 + 手动上传 -->

    <template v-if="mainTab === 'structured'">

      <el-radio-group v-model="structuredSub" style="margin-bottom:12px" @change="onStructuredSubChange">

        <el-radio-button value="structured-table">库表接入配置</el-radio-button>

        <el-radio-button value="structured-upload">手动上传数据</el-radio-button>

      </el-radio-group>



      <template v-if="structuredSub === 'structured-table'">
        <StructuredTableWizard />
      </template>
      <template v-else>
        <ManualUploadView />
      </template>

    </template>



    <!-- 文件上传：远程 / 本地 -->

    <template v-else-if="mainTab === 'file'">

      <el-radio-group v-model="fileSub" style="margin-bottom:12px" @change="onFileSubChange">

        <el-radio-button v-for="m in FILE_MODES" :key="m.key" :value="m.key">{{ m.label }}</el-radio-button>

      </el-radio-group>

      <PageCard :title="FILE_MODES.find(m => m.key === fileSub)?.label || '文件上传'">

        <el-form label-width="120px">

          <el-form-item label="接入通道">

            <el-select v-model="selectedChannelId" style="min-width:240px">

              <el-option v-for="c in filteredChannels" :key="c.id" :label="c.channelName" :value="c.id" />

            </el-select>

          </el-form-item>

          <template v-for="f in currentConfigFields" :key="f.key">

            <el-form-item :label="f.label">

              <el-input v-model="channelForm[f.key]" :placeholder="f.hint" style="max-width:400px" />

            </el-form-item>

          </template>

          <el-form-item>

            <el-button type="primary" @click="saveChannelConfig">保存配置</el-button>

            <el-button @click="runChannel">测试接入</el-button>

          </el-form-item>

        </el-form>

      </PageCard>

    </template>



    <!-- 其他数据接入 -->

    <template v-else>

      <el-radio-group v-model="otherSub" style="margin-bottom:12px" @change="onOtherSubChange">

        <el-radio-button v-for="m in OTHER_MODES" :key="m.key" :value="m.key">{{ m.label }}</el-radio-button>

      </el-radio-group>

      <PageCard title="其他数据接入">

        <el-form label-width="120px">

          <el-form-item label="接入通道">

            <el-select v-model="selectedChannelId" style="min-width:240px">

              <el-option v-for="c in filteredChannels" :key="c.id" :label="c.channelName" :value="c.id" />

            </el-select>

          </el-form-item>

          <template v-for="f in currentConfigFields" :key="f.key">

            <el-form-item :label="f.label">

              <el-input v-model="channelForm[f.key]" :placeholder="f.hint" style="max-width:400px" />

            </el-form-item>

          </template>

          <el-form-item>

            <el-button type="primary" @click="saveChannelConfig">保存配置</el-button>

            <el-button @click="runChannel">测试接入</el-button>

          </el-form-item>

        </el-form>

      </PageCard>

    </template>



  </div>

</template>

<style scoped>
.channel-fixed-name {
  display: inline-flex;
  align-items: center;
  height: 32px;
  font-size: 14px;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}
</style>


