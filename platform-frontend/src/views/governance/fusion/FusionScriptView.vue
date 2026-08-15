<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { statusLabel, statusTagType } from '@/utils/status-label'
import MetaDataSourcePickerDialog from '@/components/common/MetaDataSourcePickerDialog.vue'
import { connectionKeyOf, type MetaBindSource } from '@/utils/meta-datasource-conn'

const props = withDefaults(defineProps<{
  /** 嵌在「数据融合处理」页内时不套外层 PageCard */
  embedded?: boolean
}>(), { embedded: false })
import {
  extractColumnNames,
  loadQualitySourceOptions,
  loadQualityTables,
  type QualitySourceOption,
  type QualityTableMeta,
} from '../quality/useQualityTargetPicker'

/** 与 FusionScriptService 约定：元数据数据源 id 编码，避免与登记源 id 冲突 */
const META_DATASOURCE_ID_BASE = 1_000_000_000

interface ScriptSourceOption extends QualitySourceOption {
  kind: 'platform' | 'external' | 'meta'
  metaId?: number
  group: string
}

interface ScriptRow {
  id: number
  scriptCode: string
  scriptName: string
  scriptType: string
  scriptContent?: string
  datasourceId?: number
  publishStatus: string
  versionNo: number
  status: string
  lastRunAt?: string
  lastMessage?: string
}

interface VersionRow {
  id: number
  scriptId: number
  versionNo: number
  changeSummary?: string
  publishedBy?: string
  publishedAt?: string
}

interface RunRow {
  id: number
  scriptId: number
  scriptName?: string
  startedAt?: string
  endedAt?: string
  durationMs?: number
  status?: string
  message?: string
}

const scripts = ref<ScriptRow[]>([])
const runs = ref<RunRow[]>([])

const scriptQuery = reactive({ keyword: '', publishStatus: '' })
const runQuery = reactive({ keyword: '', status: '' })

const filteredScripts = computed(() => {
  const kw = scriptQuery.keyword.trim().toLowerCase()
  return scripts.value.filter((s) => {
    if (kw) {
      const hit = `${s.scriptCode || ''} ${s.scriptName || ''}`.toLowerCase().includes(kw)
      if (!hit) return false
    }
    if (scriptQuery.publishStatus && s.publishStatus !== scriptQuery.publishStatus) return false
    return true
  })
})

const filteredRuns = computed(() => {
  const kw = runQuery.keyword.trim().toLowerCase()
  return runs.value.filter((r) => {
    if (kw) {
      const name = `${r.scriptName || ''} ${r.scriptId || ''}`.toLowerCase()
      if (!name.includes(kw)) return false
    }
    if (runQuery.status && String(r.status || '').toUpperCase() !== runQuery.status) return false
    return true
  })
})

const {
  page: scriptPage,
  pageSize: scriptPageSize,
  paged: pagedScripts,
  total: scriptTotal,
  resetPage: resetScriptPage,
} = useClientPager(filteredScripts)
const {
  page: runPage,
  pageSize: runPageSize,
  paged: pagedRuns,
  total: runTotal,
  resetPage: resetRunPage,
} = useClientPager(filteredRuns)
const loading = ref(false)
const drawer = ref(false)
const versionDrawer = ref(false)
const activeTab = ref('list')
const sources = ref<ScriptSourceOption[]>([])
const tables = ref<QualityTableMeta[]>([])
const tablesLoading = ref(false)
const versions = ref<VersionRow[]>([])
const execResult = ref<{
  mode?: string
  rowCount?: number
  affectedRows?: number
  rows?: Record<string, unknown>[]
  message?: string
  runId?: number
} | null>(null)
const publishSummary = ref('')
const assistTable = ref('')
const dsPickerVisible = ref(false)
const formHint = ref('')
const saving = ref(false)
const executing = ref(false)

const form = reactive({
  id: null as number | null,
  scriptCode: '',
  scriptName: '',
  scriptType: 'SELECT',
  scriptContent: '',
  datasourceId: undefined as number | undefined,
  sourceName: '',
})

const columnOptions = computed(() => {
  const t = tables.value.find((x) => x.sourceTable === assistTable.value)
  return t?.columns || []
})

async function loadScripts() {
  loading.value = true
  try {
    scripts.value = (await api.get('/governance/fusion/scripts')).data || []
    resetScriptPage()
  } catch {
    ElMessage.error('加载脚本失败')
  } finally {
    loading.value = false
  }
}

async function loadRuns() {
  try {
    runs.value = (await api.get('/governance/fusion/scripts/runs')).data || []
    resetRunPage()
  } catch {
    runs.value = []
  }
}

async function searchScripts() {
  await loadScripts()
}

async function searchRuns() {
  await loadRuns()
}

const selectedSource = computed(() => sources.value.find((s) => s.id === form.datasourceId))

const sourceDisplayText = computed(() => {
  if (form.sourceName.trim()) return form.sourceName
  return selectedSource.value?.label || ''
})

const tableEmptyHint = computed(() => {
  if (tablesLoading.value || tables.value.length > 0 || form.datasourceId == null) return ''
  if (form.datasourceId === -3 || form.datasourceId === -4) {
    return '该分层库当前没有表。DWS/ADS 需融合任务产出后才有表；要查业务库请点「选择」选元数据数据源。'
  }
  if (decodeMetaDatasourceId(form.datasourceId) != null) {
    return '未探到表。请到「元数据管理 → 数据源管理」为该源填写数据库名称（db_name）并保存后再选。'
  }
  return '未探到表，请检查该数据源的连接配置。'
})

function encodeMetaDatasourceId(metaId: number) {
  return META_DATASOURCE_ID_BASE + metaId
}

function decodeMetaDatasourceId(id: number | undefined | null): number | null {
  if (id == null || id < META_DATASOURCE_ID_BASE) return null
  return id - META_DATASOURCE_ID_BASE
}

function onMetaDsPicked(row: MetaBindSource) {
  const key = connectionKeyOf(row)
  const layerId: Record<string, number> = {
    smart_city_ods: -1,
    smart_city_dwd: -2,
    smart_city_dws: -3,
    smart_city_ads: -4,
  }
  if (layerId[key] != null) {
    form.datasourceId = layerId[key]
  } else {
    form.datasourceId = encodeMetaDatasourceId(row.id)
  }
  const cat = (row.categoryName || '').trim()
  const db = (row.databaseName || '').trim()
  form.sourceName = `${cat ? `${cat} · ` : ''}${row.sourceName}${db ? `（${db}）` : ''}`
}

async function loadSources() {
  const base = await loadQualitySourceOptions()
  const platforms: ScriptSourceOption[] = base
    .filter((s) => s.kind === 'platform')
    .map((s) => ({ ...s, kind: 'platform' as const, group: '平台分层库' }))
  const external: ScriptSourceOption[] = base
    .filter((s) => s.kind === 'external')
    .map((s) => ({ ...s, kind: 'external' as const, group: '归集登记源' }))
  let meta: ScriptSourceOption[] = []
  try {
    const rows = (await api.get('/governance/platform/metadata/data-sources')).data || []
    meta = (rows as Array<Record<string, unknown>>).map((r) => {
      const metaId = Number(r.id)
      const cat = String(r.categoryName || '').trim()
      const name = String(r.sourceName || `数据源#${metaId}`).trim()
      const db = String(r.dbName || '').trim()
      const suffix = db ? `（${db}）` : ''
      return {
        id: encodeMetaDatasourceId(metaId),
        label: cat ? `${cat} · ${name}${suffix}` : `${name}${suffix}`,
        kind: 'meta' as const,
        role: 'SOURCE' as const,
        roleLabel: '源层',
        catalogHint: '元数据数据源管理中配置的 JDBC 库',
        metaId,
        group: '元数据数据源',
      }
    })
  } catch {
    meta = []
  }
  sources.value = [...platforms, ...meta, ...external]
}

async function reloadTables() {
  if (form.datasourceId == null) {
    tables.value = []
    return
  }
  tablesLoading.value = true
  try {
    const metaId = decodeMetaDatasourceId(form.datasourceId)
    if (metaId != null) {
      const rows = (await api.get(
        `/governance/platform/metadata/collect/meta-data-sources/${metaId}/tables`,
      )).data || []
      tables.value = (rows as Array<Record<string, unknown>>)
        .map((r) => ({
          sourceTable: String(r.sourceTable || r.tableName || r.name || '').trim(),
          tableComment: String(r.tableComment || r.comment || r.remarks || '').trim() || undefined,
          columns: extractColumnNames(r.columns),
        }))
        .filter((t) => !!t.sourceTable)
    } else {
      tables.value = await loadQualityTables(form.datasourceId)
    }
  } catch (e: unknown) {
    tables.value = []
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '加载表列表失败，请检查数据源连接配置')
  } finally {
    tablesLoading.value = false
  }
}

watch(() => form.datasourceId, () => {
  if (drawer.value) {
    assistTable.value = ''
    void reloadTables()
  }
})

watch(assistTable, async (tableName) => {
  const metaId = decodeMetaDatasourceId(form.datasourceId)
  if (!tableName || metaId == null) return
  const row = tables.value.find((t) => t.sourceTable === tableName)
  if (!row || row.columns.length) return
  try {
    const res = await api.get(
      `/governance/platform/metadata/models/meta-data-sources/${metaId}/table-columns`,
      { params: { tableName } },
    )
    const fields = (res.data?.fields || []) as Array<Record<string, unknown>>
    row.columns = extractColumnNames(fields.length ? fields : res.data?.columns)
  } catch {
    /* 选表仍可用，生成 SELECT * */
  }
})

function defaultScriptLabel() {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `脚本${pad(d.getMonth() + 1)}${pad(d.getDate())}${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`
}

function openCreate() {
  const label = defaultScriptLabel()
  form.id = null
  form.scriptCode = label
  form.scriptName = label
  form.scriptType = 'SELECT'
  form.scriptContent = ''
  form.datasourceId = undefined
  form.sourceName = ''
  assistTable.value = ''
  execResult.value = null
  publishSummary.value = ''
  formHint.value = ''
  drawer.value = true
  void loadSources().then(() => reloadTables())
}

async function openEdit(row: ScriptRow) {
  const detail = (await api.get(`/governance/fusion/scripts/${row.id}`)).data
  form.id = detail.id
  form.scriptCode = detail.scriptCode
  form.scriptName = detail.scriptName
  form.scriptType = detail.scriptType || 'SELECT'
  form.scriptContent = detail.scriptContent || ''
  form.datasourceId = detail.datasourceId ?? undefined
  form.sourceName = ''
  assistTable.value = ''
  execResult.value = null
  publishSummary.value = ''
  formHint.value = ''
  drawer.value = true
  await loadSources()
  form.sourceName = sources.value.find((s) => s.id === form.datasourceId)?.label || ''
  await reloadTables()
}

function fillSelectFromTable() {
  if (!assistTable.value) {
    ElMessage.warning('请先选择表')
    return
  }
  const cols = columnOptions.value
  const list = cols.length ? cols.map((c) => `\`${c}\``).join(', ') : '*'
  form.scriptType = 'SELECT'
  form.scriptContent = `SELECT ${list}\nFROM \`${assistTable.value}\``
}

async function persistScript() {
  formHint.value = ''
  if (!form.scriptCode.trim() || !form.scriptName.trim()) {
    const label = defaultScriptLabel()
    if (!form.scriptCode.trim()) form.scriptCode = label
    if (!form.scriptName.trim()) form.scriptName = label
  }
  if (!form.scriptContent.trim()) {
    formHint.value = '请先生成或填写脚本 SQL'
    ElMessage.warning(formHint.value)
    return false
  }
  if (form.datasourceId == null) {
    formHint.value = '请选择来源库'
    ElMessage.warning(formHint.value)
    return false
  }
  const body = {
    scriptCode: form.scriptCode.trim(),
    scriptName: form.scriptName.trim(),
    scriptType: form.scriptType,
    scriptContent: form.scriptContent,
    datasourceId: form.datasourceId,
  }
  try {
    if (form.id) {
      await api.put(`/governance/fusion/scripts/${form.id}`, body)
    } else {
      const id = (await api.post('/governance/fusion/scripts', body)).data
      form.id = id
    }
    await loadScripts()
    return true
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '保存失败'
    formHint.value = msg
    ElMessage.error(msg)
    return false
  }
}

async function saveScript() {
  saving.value = true
  try {
    if (await persistScript()) {
      ElMessage.success(form.id ? '脚本已保存' : '脚本已创建')
      drawer.value = false
    }
  } finally {
    saving.value = false
  }
}

async function removeScript(row: ScriptRow) {
  await ElMessageBox.confirm(`删除脚本「${row.scriptName}」？`, '确认')
  await api.delete(`/governance/fusion/scripts/${row.id}`)
  ElMessage.success('已删除')
  await loadScripts()
}

async function executeScript() {
  executing.value = true
  try {
    if (!(await persistScript())) return
    execResult.value = (await api.post(`/governance/fusion/scripts/${form.id}/execute`)).data
    ElMessage.success(execResult.value?.message || '执行完成')
    await Promise.all([loadScripts(), loadRuns()])
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '执行失败'
    formHint.value = msg
    ElMessage.error(msg)
    await Promise.all([loadScripts(), loadRuns()])
  } finally {
    executing.value = false
  }
}

async function openVersions(row: ScriptRow) {
  form.id = row.id
  form.scriptName = row.scriptName
  versions.value = (await api.get(`/governance/fusion/scripts/${row.id}/versions`)).data || []
  publishSummary.value = ''
  versionDrawer.value = true
}

async function publishScript() {
  if (!form.id) return
  const res = (await api.post(`/governance/fusion/scripts/${form.id}/publish`, {
    changeSummary: publishSummary.value || '发布',
  })).data
  ElMessage.success(`已发布 v${res.versionNo}`)
  versions.value = (await api.get(`/governance/fusion/scripts/${form.id}/versions`)).data || []
  await loadScripts()
}

async function rollbackVersion(ver: VersionRow) {
  if (!form.id) return
  await ElMessageBox.confirm(`回滚到 v${ver.versionNo}？`, '确认')
  await api.post(`/governance/fusion/scripts/${form.id}/rollback/${ver.versionNo}`)
  ElMessage.success('已回滚')
  versions.value = (await api.get(`/governance/fusion/scripts/${form.id}/versions`)).data || []
  if (drawer.value) {
    const detail = (await api.get(`/governance/fusion/scripts/${form.id}`)).data
    form.scriptContent = detail.scriptContent || ''
  }
  await loadScripts()
}

onMounted(() => {
  void loadScripts()
})
</script>

<template>
  <component :is="props.embedded ? 'div' : PageCard" v-bind="props.embedded ? {} : { title: '数据融合处理 · 脚本开发' }">
    <el-tabs v-model="activeTab" @tab-change="(name: string | number) => { if (name === 'runs') void loadRuns() }">
      <el-tab-pane label="脚本列表" name="list" />
      <el-tab-pane label="运行记录" name="runs" />
    </el-tabs>
    <el-form v-if="activeTab === 'list'" inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="名称/编码" class="portal-field-lg">
        <el-input
          v-model="scriptQuery.keyword"
          clearable
          placeholder="脚本名称或编码"
          @keyup.enter="searchScripts"
        />
      </el-form-item>
      <el-form-item label="发布状态" class="portal-field-md">
        <el-select v-model="scriptQuery.publishStatus" clearable placeholder="全部">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="searchScripts">查询</el-button>
        <el-button type="primary" plain @click="openCreate">新建脚本</el-button>
      </el-form-item>
    </el-form>

    <el-table v-if="activeTab === 'list'" v-loading="loading" :data="pagedScripts" stripe size="small">
      <el-table-column prop="scriptCode" label="编码" width="120" />
      <el-table-column prop="scriptName" label="名称" min-width="140" />
      <el-table-column label="类型" width="80">
        <template #default="{ row }">{{ $statusLabel(row.scriptType) }}</template>
      </el-table-column>
      <el-table-column label="发布" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTagType(row.publishStatus)">{{ statusLabel(row.publishStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="versionNo" label="版本" width="60" />
      <el-table-column prop="lastMessage" label="最近执行" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link @click="openEdit(row)">编辑</el-button>
          <el-button link @click="openVersions(row)">版本</el-button>
          <el-button link type="danger" @click="removeScript(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <PortalPagination
      v-if="activeTab === 'list'"
      v-model:page="scriptPage"
      v-model:page-size="scriptPageSize"
      :total="scriptTotal"
    />
    <el-empty
      v-if="activeTab === 'list' && !loading && !filteredScripts.length"
      :description="scripts.length ? '无匹配脚本' : '暂无融合脚本'"
    />

    <template v-if="activeTab === 'runs'">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="脚本" class="portal-field-lg">
          <el-input
            v-model="runQuery.keyword"
            clearable
            placeholder="脚本名称"
            @keyup.enter="searchRuns"
          />
        </el-form-item>
        <el-form-item label="状态" class="portal-field-md">
          <el-select v-model="runQuery.status" clearable placeholder="全部">
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
            <el-option label="运行中" value="RUNNING" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="searchRuns">查询</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="pagedRuns" stripe size="small">
        <el-table-column prop="id" label="运行ID" width="80" />
        <el-table-column label="脚本" min-width="140">
          <template #default="{ row }">{{ row.scriptName || `脚本#${row.scriptId}` }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" label="耗时(ms)" width="90" />
        <el-table-column prop="startedAt" label="开始" width="170" />
        <el-table-column prop="message" label="摘要" min-width="160" show-overflow-tooltip />
      </el-table>
      <PortalPagination
        v-model:page="runPage"
        v-model:page-size="runPageSize"
        :total="runTotal"
      />
      <el-empty
        v-if="!filteredRuns.length"
        :description="runs.length ? '无匹配运行记录' : '暂无运行记录；执行脚本后将写入此处'"
      />
    </template>

    <el-drawer v-model="drawer" :title="form.id ? `编辑 · ${form.scriptName}` : '新建脚本'" size="560px">
      <el-form label-position="top" size="small">
        <el-alert v-if="formHint" :title="formHint" type="error" show-icon :closable="false" style="margin-bottom:12px" />
        <el-form-item label="编码" required>
          <el-input v-model="form.scriptCode" :disabled="!!form.id" placeholder="自动生成，可改" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.scriptName" placeholder="自动生成，可改" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.scriptType" class="full-w">
            <el-option label="查询 SELECT" value="SELECT" />
            <el-option label="更新 UPDATE" value="UPDATE" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源库" required>
          <div class="conn-pick">
            <el-input
              :model-value="sourceDisplayText"
              readonly
              placeholder="点击选择来源库"
              @click="dsPickerVisible = true"
            />
            <el-button type="primary" @click="dsPickerVisible = true">选择</el-button>
          </div>
        </el-form-item>
        <el-form-item label="辅助选表（生成 SELECT）">
          <div class="assist-row">
            <el-select
              v-model="assistTable"
              filterable
              clearable
              :loading="tablesLoading"
              :disabled="form.datasourceId == null"
              :placeholder="form.datasourceId == null ? '请先选择来源库' : '选择表后点生成'"
              class="full-w"
            >
              <el-option
                v-for="t in tables"
                :key="t.sourceTable"
                :label="t.tableComment ? `${t.sourceTable}（${t.tableComment}）` : t.sourceTable"
                :value="t.sourceTable"
              />
            </el-select>
            <el-button :disabled="!assistTable" @click="fillSelectFromTable">生成查询</el-button>
          </div>
          <div v-if="tableEmptyHint" class="muted" style="margin-top:6px">{{ tableEmptyHint }}</div>
        </el-form-item>
        <el-form-item label="脚本 SQL" required>
          <el-input
            v-model="form.scriptContent"
            type="textarea"
            :rows="12"
            placeholder="从上方选表生成，或手写 SELECT / UPDATE（禁止 DROP/INSERT/DELETE）"
          />
        </el-form-item>
      </el-form>

      <template v-if="execResult">
        <el-divider />
        <div class="result-title">执行结果{{ execResult.runId ? ` · 运行 #${execResult.runId}` : '' }}</div>
        <el-alert :title="execResult.message" type="success" :closable="false" show-icon />
        <el-table
          v-if="execResult.mode === 'SELECT' && execResult.rows?.length"
          :data="execResult.rows"
          size="small"
          stripe
          max-height="240"
          style="margin-top:8px"
        />
        <div v-else-if="execResult.mode === 'UPDATE'" class="muted">影响行数：{{ execResult.affectedRows }}</div>
      </template>
      <template #footer>
        <el-button type="primary" :loading="saving" @click="saveScript">保存</el-button>
        <el-button type="success" :loading="executing" @click="executeScript">执行</el-button>
      </template>
    </el-drawer>

    <el-drawer v-model="versionDrawer" :title="`版本 · ${form.scriptName}`" size="420px">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="变更说明" class="portal-field-lg">
          <el-input v-model="publishSummary" placeholder="发布说明" />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" :disabled="!form.id" @click="publishScript">发布当前版本</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="versions" stripe size="small">
        <el-table-column prop="versionNo" label="版本" width="60" />
        <el-table-column prop="changeSummary" label="说明" />
        <el-table-column prop="publishedBy" label="发布人" width="80" />
        <el-table-column prop="publishedAt" label="时间" width="150" />
        <el-table-column label="操作" width="70">
          <template #default="{ row }">
            <el-button link @click="rollbackVersion(row)">回滚</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <MetaDataSourcePickerDialog v-model="dsPickerVisible" title="选择来源库" @confirm="onMetaDsPicked" />
  </component>
</template>

<style scoped>
.result-title {
  font-weight: 600;
  margin-bottom: 8px;
  font-size: 13px;
}
.muted {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.full-w {
  width: 100%;
}
.assist-row {
  display: flex;
  gap: 8px;
  width: 100%;
  align-items: center;
}
.assist-row .el-select {
  flex: 1;
}
.conn-pick {
  display: flex;
  gap: 8px;
  width: 100%;
}
.conn-pick .el-input {
  flex: 1;
}
</style>
