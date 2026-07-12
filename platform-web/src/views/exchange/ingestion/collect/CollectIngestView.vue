<script setup lang="ts">

import { computed, onMounted, reactive, ref, watch } from 'vue'

import { useRoute, useRouter } from 'vue-router'

import PageCard from '@/components/common/PageCard.vue'

import { collectIngestMainTab, type IngestMainTab } from '../ingestion-nav'

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

const tplForm = reactive({ templateCode: '', templateName: '', columnMappingJson: 'name→entity_name' })

const uploadForm = reactive({ templateCode: '', fileName: '' })

const fileInput = ref<HTMLInputElement>()



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

        { key: 'syncMode', label: '同步方式', defaultValue: 'T+1', hint: 'T+1 日批 / REALTIME 近实时' },

        { key: 'sourceTableId', label: '源表（登记）', defaultValue: '', hint: '选用登记侧已登记的物理表 ID' },

        { key: 'targetTable', label: '目标表', defaultValue: '', hint: '平台侧入库表名' },

        { key: 'mappingMode', label: '字段映射', defaultValue: 'auto', hint: 'auto 自动 / manual 手工映射' },

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

      await Promise.all([loadTemplates(force), loadUploads(force)])

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



async function runChannel() {

  if (!selectedChannelId.value) return

  await ingestionApi.runChannel(selectedChannelId.value)

  const type = activeChannelType.value

  if (type) await loadScopeData(currentScope(), { force: true, silent: true })

}



async function createTask() {

  if (!taskForm.taskName || !taskForm.channelId) return

  await ingestionApi.createTask({ ...taskForm })

  taskForm.taskName = ''

  await loadTasks(true)

}



async function createTemplate() {

  if (!tplForm.templateName) return

  const mapping = tplForm.columnMappingJson.split(',').map((pair) => {

    const [col, target] = pair.split('→').map((s) => s.trim())

    return { col, target: target || col }

  })

  await ingestionApi.createTemplate({

    templateCode: tplForm.templateCode,

    templateName: tplForm.templateName,

    columnMappingJson: JSON.stringify(mapping),

  })

  tplForm.templateName = ''

  await loadTemplates(true)

}



async function doUpload() {

  await ingestionApi.upload({ templateCode: uploadForm.templateCode, fileName: uploadForm.fileName || 'manual_upload.xlsx' })

  await loadUploads(true)

}



async function onFileChange(e: Event) {

  const file = (e.target as HTMLInputElement).files?.[0]

  if (!file) return

  const fd = new FormData()

  fd.append('file', file)

  fd.append('templateCode', uploadForm.templateCode)

  await ingestionApi.uploadFile(fd)

  await loadUploads(true)

}



watch(selectedChannelId, () => loadChannelForm(selectedChannel.value))

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

        <PageCard title="结构化数据接入">

          <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px"

            title="源表与数据源来自「数据资产登记」中已登记内容，请先在登记侧完成数据源/表登记后再配置接入。" />

          <el-form label-width="120px" class="portal-inline-form portal-inline-form--block">

            <el-form-item label="接入通道">

              <el-select v-model="selectedChannelId" style="min-width:240px">

                <el-option v-for="c in filteredChannels" :key="c.id" :label="c.channelName" :value="c.id" />

              </el-select>

              <el-button type="primary" style="margin-left:8px" @click="runChannel">立即执行</el-button>

            </el-form-item>

            <template v-for="f in currentConfigFields" :key="f.key">

              <el-form-item :label="f.label">

                <el-select v-if="f.key === 'sourceTableId'" v-model="channelForm[f.key]" filterable style="min-width:320px" placeholder="选择已登记源表">

                  <el-option v-for="tb in tables" :key="tb.id" :label="`${tb.tableName}（${tb.tableCode}）`" :value="String(tb.id)" />

                </el-select>

                <el-input v-else v-model="channelForm[f.key]" :placeholder="f.hint" style="max-width:400px" />

              </el-form-item>

            </template>

            <el-form-item>

              <el-button type="primary" @click="saveChannelConfig">保存接入配置</el-button>

            </el-form-item>

          </el-form>

          <el-table v-if="selectedChannel" :data="[selectedChannel]" stripe size="small" style="margin-top:8px">

            <el-table-column prop="status" label="状态" width="90" />

            <el-table-column prop="lastMessage" label="最近执行" min-width="240" />

          </el-table>

        </PageCard>

      </template>



      <template v-else>

        <PageCard title="上传模板管理">

          <el-form inline class="portal-inline-form portal-inline-form--block">

            <el-form-item label="模板编码" class="portal-field-md"><el-input v-model="tplForm.templateCode" placeholder="可选" /></el-form-item>

            <el-form-item label="模板名称" class="portal-field-md"><el-input v-model="tplForm.templateName" /></el-form-item>

            <el-form-item label="列映射" class="portal-field-lg"><el-input v-model="tplForm.columnMappingJson" placeholder="源列→目标列，逗号分隔" /></el-form-item>

            <el-form-item class="portal-form-actions"><el-button type="primary" @click="createTemplate">新建模板</el-button></el-form-item>

          </el-form>

          <el-table :data="templates" stripe size="small">

            <el-table-column prop="templateName" label="模板名称" />

            <el-table-column prop="templateCode" label="编码" width="140" />

            <el-table-column prop="columnMappingJson" label="列映射" min-width="200" show-overflow-tooltip />

          </el-table>

        </PageCard>

        <PageCard title="手动上传数据">

          <el-form inline class="portal-inline-form portal-inline-form--block">

            <el-form-item label="模板" class="portal-field-default">

              <el-select v-model="uploadForm.templateCode">

                <el-option v-for="t in templates" :key="t.id" :label="t.templateName" :value="t.templateCode" />

              </el-select>

            </el-form-item>

            <el-form-item class="portal-form-actions">

              <el-button @click="fileInput?.click()">选择文件上传</el-button>

              <el-button type="primary" @click="doUpload">模拟解析入库</el-button>

            </el-form-item>

            <input ref="fileInput" type="file" accept=".xlsx,.xls,.csv" style="display:none" @change="onFileChange" />

          </el-form>

        </PageCard>

        <PageCard title="数据上传记录">

          <el-table :data="uploads" stripe size="small">

            <el-table-column prop="fileName" label="文件" />

            <el-table-column prop="templateCode" label="模板" width="140" />

            <el-table-column prop="rowCount" label="行数" width="80" />

            <el-table-column prop="status" label="状态" width="100" />

          </el-table>

        </PageCard>

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



    <!-- 接入任务（结构化库表页显示） -->

    <PageCard v-if="mainTab === 'structured' && structuredSub === 'structured-table'" title="接入任务">

      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px"

        title="定时调度：使用 Cron 表达式控制任务自动执行时间。示例「0 2 * * *」表示每天凌晨 2 点执行一次（T+1 日批常见配置）。" />

      <el-form inline class="portal-inline-form portal-inline-form--block">

        <el-form-item label="通道" class="portal-field-default">

          <el-select v-model="taskForm.channelId">

            <el-option v-for="c in filteredChannels" :key="c.id" :label="c.channelName" :value="c.id" />

          </el-select>

        </el-form-item>

        <el-form-item label="任务名称" class="portal-field-md"><el-input v-model="taskForm.taskName" /></el-form-item>

        <el-form-item label="定时调度" class="portal-field-cron">

          <el-input v-model="taskForm.scheduleCron" placeholder="0 2 * * *" />

        </el-form-item>

        <el-form-item class="portal-form-actions">

          <el-button type="primary" @click="createTask">保存任务</el-button>

        </el-form-item>

      </el-form>

      <el-table :data="tasks.filter(t => filteredChannels.some(c => c.id === t.channelId))" stripe size="small">

        <el-table-column prop="taskName" label="任务名称" min-width="140" />

        <el-table-column prop="scheduleCron" label="定时调度(Cron)" width="140" />

        <el-table-column prop="status" label="状态" width="90" />

        <el-table-column prop="lastRunMessage" label="最近日志" min-width="200" show-overflow-tooltip />

      </el-table>

    </PageCard>

  </div>

</template>


