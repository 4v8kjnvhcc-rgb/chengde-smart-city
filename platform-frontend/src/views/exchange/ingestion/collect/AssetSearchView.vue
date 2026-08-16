<script setup lang="ts">
import { onActivated, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import api from '@/api/http'
import { statusLabel } from '@/utils/status-label'
import { useExecCycleLabel } from '@/utils/exec-cycle-label'

const BASE = '/exchange/ingestion/asset-search'
const tab = ref('portal')
const loading = ref(false)
const overview = ref<Record<string, unknown>>({
  docCount: 0,
  syncPolicies: 0,
  knowledgeCount: 0,
  engine: '—',
  globalFields: 0,
  globalBindings: 0,
  identities: 0,
  lastSuccessAt: '',
})

const mode = ref('FUZZY')
const q = ref('')
const { label: cycleLabel } = useExecCycleLabel()
const tagIds = ref<number[]>([])
const tableId = ref<number | undefined>()
const dataItem = ref('')
const result = ref<Record<string, unknown> | null>(null)
const browse = ref<Record<string, unknown> | null>(null)
const globalValues = reactive<Record<string, string>>({})
const tags = ref<Record<string, unknown>[]>([])
const tables = ref<Record<string, unknown>[]>([])

const syncPolicies = ref<Record<string, unknown>[]>([])
const knowledge = ref<Record<string, unknown>[]>([])
const globalFields = ref<Record<string, unknown>[]>([])
const globalBindings = ref<Record<string, unknown>[]>([])
const identities = ref<Record<string, unknown>[]>([])
const savedQueries = ref<Record<string, unknown>[]>([])
const queryLogs = ref<Record<string, unknown>[]>([])
const audits = ref<Record<string, unknown>[]>([])
const impact = ref<Record<string, unknown> | null>(null)

const knowledgeDialog = ref(false)
const knowledgeForm = reactive({
  id: undefined as number | undefined,
  knowledgeType: 'SYNONYM',
  knowledgeCode: '',
  knowledgeName: '',
  payloadJson: '{"terms":["词1","词2"]}',
  priority: 50,
  status: 'ACTIVE',
  description: '',
})

const fieldDialog = ref(false)
const fieldForm = reactive({
  id: undefined as number | undefined,
  fieldCode: '',
  fieldName: '',
  semantic: '',
  dataType: 'STRING',
  controlType: 'INPUT',
  matchNameRegex: '',
  matchCommentKeywords: '',
  requiredFlag: 0,
  status: 'ACTIVE',
  description: '',
})

const identityDialog = ref(false)
const identityForm = reactive({
  entityId: '',
  idType: 'CREDIT_CODE',
  idValue: '',
  displayName: '',
  sourceSystem: '',
  profileJson: '{}',
})

const MODE_OPTS = [
  { value: 'FUZZY', label: '智能模糊' },
  { value: 'EXACT', label: '精确标识' },
  { value: 'META', label: '元数据找表' },
  { value: 'COMBO', label: '多维组合' },
]

async function loadOverview() {
  try {
    overview.value = {
      ...overview.value,
      ...((await api.get(`${BASE}/overview`)).data || {}),
    }
  } catch {
    /* 保留默认概览，避免整页空白 */
  }
}

async function loadPortalDeps() {
  try {
    tags.value = (await api.get('/exchange/ingestion/tag-manage/tags')).data || []
  } catch {
    tags.value = []
  }
  try {
    tables.value = (await api.get('/exchange/ingestion/register/tables')).data || []
  } catch {
    tables.value = []
  }
}

async function reload() {
  loading.value = true
  try {
    await loadOverview()
    if (tab.value === 'portal') await loadPortalDeps()
    else if (tab.value === 'engine') await Promise.all([
      api.get(`${BASE}/sync-policies`).then((r) => (syncPolicies.value = r.data || [])),
      api.get(`${BASE}/knowledge`).then((r) => (knowledge.value = r.data || [])),
    ])
    else if (tab.value === 'global') await Promise.all([
      api.get(`${BASE}/global-fields`).then((r) => (globalFields.value = r.data || [])),
      api.get(`${BASE}/global-bindings`).then((r) => (globalBindings.value = r.data || [])),
    ])
    else if (tab.value === 'identity') {
      identities.value = (await api.get(`${BASE}/identities`)).data || []
    } else if (tab.value === 'ops') {
      await Promise.all([
        api.get(`${BASE}/query-logs`, { params: { limit: 50 } }).then((r) => (queryLogs.value = r.data || [])),
        api.get(`${BASE}/audit-logs`, { params: { limit: 50 } }).then((r) => (audits.value = r.data || [])),
        api.get(`${BASE}/saved-queries`).then((r) => (savedQueries.value = r.data || [])),
      ])
    }
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function onTab(name: string | number) {
  tab.value = String(name)
  await reload()
}

async function doSearch() {
  loading.value = true
  browse.value = null
  try {
    const body: Record<string, unknown> = {
      mode: mode.value,
      q: q.value,
      tagIds: tagIds.value,
      tableId: tableId.value,
      dataItem: dataItem.value || undefined,
      globalValues: { ...globalValues },
      limit: 50,
    }
    result.value = (await api.post(`${BASE}/query`, body)).data
    ElMessage.success(`命中 ${result.value?.total ?? 0} 条`)
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '检索失败')
  } finally {
    loading.value = false
  }
}

async function onHitClick(row: Record<string, unknown>) {
  await api.post(`${BASE}/click`, { docKey: row.docKey, mode: mode.value, q: q.value })
  if (row.docType === 'TABLE' && row.assetId) {
    tableId.value = Number(row.assetId)
    const gfs = (await api.get(`${BASE}/tables/${row.assetId}/global-fields`)).data || []
    for (const g of gfs as Record<string, unknown>[]) {
      const code = String(g.fieldCode)
      if (!(code in globalValues)) globalValues[code] = ''
    }
  }
}

async function doBrowse(row?: Record<string, unknown>) {
  const tid = row?.assetId ? Number(row.assetId) : tableId.value
  if (!tid) {
    ElMessage.warning('请先选择表资产')
    return
  }
  loading.value = true
  try {
    browse.value = (await api.post(`${BASE}/browse`, {
      tableId: tid,
      globalValues: { ...globalValues },
      limit: 20,
      offset: 0,
    })).data
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '浏览失败')
  } finally {
    loading.value = false
  }
}

async function doDownload(row: Record<string, unknown>) {
  const res = await api.post(`${BASE}/download`, {
    docKey: row.docKey,
    tableId: row.assetId,
    mode: mode.value,
    q: q.value,
  })
  ElMessage.success(String(res.data?.message || '已提交下载申请'))
}

async function runSync(row: Record<string, unknown>) {
  await ElMessageBox.confirm(`执行同步「${row.policyName}」？`, '同步')
  const res = await api.post(`${BASE}/sync-policies/${row.id}/run`)
  ElMessage.success(`已索引 ${res.data?.indexed} 条（引擎 ${res.data?.engine}）`)
  await reload()
}

function openKnowledge(row?: Record<string, unknown>) {
  knowledgeForm.id = row?.id as number | undefined
  knowledgeForm.knowledgeType = String(row?.knowledgeType || 'SYNONYM')
  knowledgeForm.knowledgeCode = String(row?.knowledgeCode || '')
  knowledgeForm.knowledgeName = String(row?.knowledgeName || '')
  knowledgeForm.payloadJson = String(row?.payloadJson || '{"terms":[]}')
  knowledgeForm.priority = Number(row?.priority ?? 50)
  knowledgeForm.status = String(row?.status || 'ACTIVE')
  knowledgeForm.description = String(row?.description || '')
  knowledgeDialog.value = true
}

async function saveKnowledge() {
  try {
    JSON.parse(knowledgeForm.payloadJson || '{}')
  } catch {
    ElMessage.warning('payload JSON 非法')
    return
  }
  await api.post(`${BASE}/knowledge`, { ...knowledgeForm })
  knowledgeDialog.value = false
  ElMessage.success('已保存')
  await reload()
}

async function deleteKnowledge(row: Record<string, unknown>) {
  await ElMessageBox.confirm('删除该业务知识？', '确认')
  await api.delete(`${BASE}/knowledge/${row.id}`)
  ElMessage.success('已删除')
  await reload()
}

function openField(row?: Record<string, unknown>) {
  fieldForm.id = row?.id as number | undefined
  fieldForm.fieldCode = String(row?.fieldCode || '')
  fieldForm.fieldName = String(row?.fieldName || '')
  fieldForm.semantic = String(row?.semantic || '')
  fieldForm.dataType = String(row?.dataType || 'STRING')
  fieldForm.controlType = String(row?.controlType || 'INPUT')
  fieldForm.matchNameRegex = String(row?.matchNameRegex || '')
  fieldForm.matchCommentKeywords = String(row?.matchCommentKeywords || '')
  fieldForm.requiredFlag = Number(row?.requiredFlag ?? 0)
  fieldForm.status = String(row?.status || 'ACTIVE')
  fieldForm.description = String(row?.description || '')
  fieldDialog.value = true
}

async function saveField() {
  await api.post(`${BASE}/global-fields`, { ...fieldForm })
  fieldDialog.value = false
  ElMessage.success('已保存')
  await reload()
}

async function autoMatch(row: Record<string, unknown>) {
  const res = await api.post(`${BASE}/global-fields/${row.id}/auto-match`)
  ElMessage.success(`自动匹配 ${res.data?.matched} 条`)
  globalBindings.value = (await api.get(`${BASE}/global-bindings`, { params: { fieldId: row.id } })).data || []
  impact.value = (await api.get(`${BASE}/global-fields/${row.id}/impact`)).data
}

async function confirmBind(row: Record<string, unknown>, accept: boolean) {
  await api.post(`${BASE}/global-bindings/${row.id}/confirm`, null, { params: { accept } })
  ElMessage.success(accept ? '已确认' : '已排除')
  await reload()
}

async function saveIdentity() {
  await api.post(`${BASE}/identities`, { ...identityForm })
  identityDialog.value = false
  ElMessage.success('标识已保存')
  await reload()
}

async function saveCurrentQuery() {
  if (!result.value) {
    ElMessage.warning('请先执行一次检索')
    return
  }
  const { value } = await ElMessageBox.prompt('方案名称', '保存组合方案')
  await api.post(`${BASE}/saved-queries`, {
    queryName: value,
    mode: mode.value,
    payloadJson: JSON.stringify({
      mode: mode.value,
      q: q.value,
      tagIds: tagIds.value,
      tableId: tableId.value,
      globalValues: { ...globalValues },
    }),
  })
  ElMessage.success('方案已保存')
}

function applySaved(row: Record<string, unknown>) {
  try {
    const p = JSON.parse(String(row.payloadJson || '{}')) as Record<string, unknown>
    mode.value = String(p.mode || 'COMBO')
    q.value = String(p.q || '')
    tagIds.value = (p.tagIds as number[]) || []
    tableId.value = p.tableId as number | undefined
    Object.assign(globalValues, (p.globalValues as Record<string, string>) || {})
    tab.value = 'portal'
    doSearch()
  } catch {
    ElMessage.error('方案解析失败')
  }
}

onMounted(reload)
onActivated(() => {
  void reload()
})
</script>

<template>
  <div>
    <PageCard title="数据搜索" v-loading="loading">
      <el-descriptions :column="4" border size="small" class="mb">
        <el-descriptions-item label="索引文档">{{ overview.docCount }}</el-descriptions-item>
        <el-descriptions-item label="同步策略">{{ overview.syncPolicies }}</el-descriptions-item>
        <el-descriptions-item label="业务知识">{{ overview.knowledgeCount }}</el-descriptions-item>
        <el-descriptions-item label="引擎">{{ overview.engine }}</el-descriptions-item>
        <el-descriptions-item label="全局条件">{{ overview.globalFields }}</el-descriptions-item>
        <el-descriptions-item label="条件绑定">{{ overview.globalBindings }}</el-descriptions-item>
        <el-descriptions-item label="标识实体">{{ overview.identities }}</el-descriptions-item>
        <el-descriptions-item label="最近同步成功">{{ overview.lastSuccessAt || '—' }}</el-descriptions-item>
      </el-descriptions>

      <el-tabs :model-value="tab" @tab-change="onTab">
        <el-tab-pane label="统一搜索门户" name="portal" />
        <el-tab-pane label="引擎与业务知识" name="engine" />
        <el-tab-pane label="全局查询条件" name="global" />
        <el-tab-pane label="标识号融合" name="identity" />
        <el-tab-pane label="运维与审计" name="ops" />
      </el-tabs>

      <!-- 门户 -->
      <div v-if="tab === 'portal'">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="模式" class="portal-field-md">
            <el-select v-model="mode">
              <el-option v-for="m in MODE_OPTS" :key="m.value" :label="m.label" :value="m.value" />
            </el-select>
          </el-form-item>
          <el-form-item :label="mode === 'EXACT' ? '标识号' : '关键词'" class="portal-field-xl">
            <el-input v-model="q" clearable :placeholder="mode === 'EXACT' ? '如统一社会信用代码' : '表名/标签/同义词'" @keyup.enter="doSearch" />
          </el-form-item>
          <el-form-item v-if="mode === 'META' || mode === 'COMBO'" label="标签" class="portal-field-xl">
            <el-select v-model="tagIds" multiple filterable collapse-tags clearable placeholder="分类/标签">
              <el-option v-for="t in tags.filter((x) => x.level !== 1)" :key="String(t.id)" :label="String(t.tagName)" :value="t.id as number" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="mode === 'META'" label="数据项" class="portal-field-md">
            <el-input v-model="dataItem" clearable placeholder="字段名/注释" />
          </el-form-item>
          <el-form-item v-if="mode === 'COMBO'" label="目标表" class="portal-field-xl">
            <el-select v-model="tableId" filterable clearable placeholder="输入表名筛选（可选）">
              <el-option v-for="t in tables" :key="String(t.id)" :label="`${t.tableName || t.tableCode}`" :value="t.id as number" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="doSearch">搜索</el-button>
            <el-button @click="saveCurrentQuery">保存方案</el-button>
          </el-form-item>
        </el-form>

        <div v-if="Object.keys(globalValues).length" class="mb">
          <div class="block-title">全局条件（自动关联复用）</div>
          <el-form inline class="portal-inline-form portal-inline-form--sm" size="small">
            <el-form-item v-for="(val, key) in globalValues" :key="key" :label="String(key)" class="portal-field-md">
              <el-input v-model="globalValues[key]" clearable />
            </el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button type="primary" size="small" @click="doBrowse()">条件浏览</el-button>
            </el-form-item>
          </el-form>
        </div>

        <el-alert v-if="result?.emptyHint" type="warning" :title="String(result.emptyHint)" show-icon :closable="false" class="mb" />
        <el-alert
          v-if="result?.secondary"
          type="info"
          :title="String((result.secondary as Record<string, unknown>).hint || '二次检索建议')"
          show-icon
          :closable="false"
          class="mb"
        />

        <div v-if="result?.profile && Object.keys(result.profile as object).length" class="mb profile">
          <div class="block-title">实体画像（精确搜索）</div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="实体ID">{{ (result.profile as Record<string, unknown>).entityId }}</el-descriptions-item>
            <el-descriptions-item label="名称">{{ (result.profile as Record<string, unknown>).displayName }}</el-descriptions-item>
          </el-descriptions>
          <el-table :data="((result.profile as Record<string, unknown>).identifiers as Record<string, unknown>[]) || []" size="small" class="mt">
            <el-table-column label="标识类型" width="120">
              <template #default="{ row }">{{ statusLabel(row.idType) }}</template>
            </el-table-column>
            <el-table-column prop="idValue" label="标识值" />
            <el-table-column prop="sourceSystem" label="来源" width="120" />
          </el-table>
        </div>

        <div class="block-title">检索结果 <el-tag v-if="result" size="small">共 {{ result.total }} · {{ statusLabel(String(result.mode)) }}</el-tag></div>
        <el-table :data="(result?.hits as Record<string, unknown>[]) || []" stripe @row-click="onHitClick">
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ statusLabel(row.docType) }}</template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
          <el-table-column label="高权重属性" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-for="(v, k) in (row.highAttrs || {})" :key="String(k)" class="attr">{{ k }}={{ Array.isArray(v) ? v.join(',') : v }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="highlight" label="高亮" min-width="160" show-overflow-tooltip />
          <el-table-column prop="score" label="相关度" width="80" />
          <el-table-column prop="physicalTable" label="物理表" width="140" show-overflow-tooltip />
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.docType === 'TABLE' || row.assetId" link type="primary" @click.stop="doBrowse(row)">浏览</el-button>
              <el-button link @click.stop="doDownload(row)">下载申请</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="browse" class="mt">
          <div class="block-title">
            物理表浏览 · {{ browse.physicalTable }}
            <el-tag size="small" class="ml">{{ statusLabel(String(browse.mode)) }}</el-tag>
            <el-tag v-if="browse.masked" size="small" type="warning" class="ml">已脱敏样例</el-tag>
          </div>
          <el-alert v-if="browse.message" type="info" :title="String(browse.message)" :closable="false" class="mb" />
          <el-table v-if="(browse.rows as unknown[])?.length" :data="browse.rows as Record<string, unknown>[]" size="small" max-height="320" stripe>
            <el-table-column
              v-for="col in (browse.columns as string[]) || []"
              :key="col"
              :prop="col"
              :label="col"
              min-width="120"
              show-overflow-tooltip
            />
          </el-table>
          <el-empty v-else description="无行数据或未配置数据源连接" />
        </div>
      </div>

      <!-- 引擎 -->
      <div v-if="tab === 'engine'">
        <div class="block-title">同步策略</div>
        <el-table :data="syncPolicies" stripe class="mb">
          <el-table-column prop="policyCode" label="编码" width="140" />
          <el-table-column prop="policyName" label="名称" width="160" />
          <el-table-column label="策略" width="120">
            <template #default="{ row }">{{ statusLabel(row.updateStrategy) }}</template>
          </el-table-column>
          <el-table-column label="执行周期" width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ cycleLabel(row.cronExpr as string) }}</template>
          </el-table-column>
          <el-table-column label="上次状态" width="100">
            <template #default="{ row }">{{ statusLabel(row.lastStatus) }}</template>
          </el-table-column>
          <el-table-column prop="lastSuccessAt" label="成功时间" width="170" />
          <el-table-column prop="docCount" label="文档量" width="90" />
          <el-table-column prop="lastMessage" label="消息" min-width="160" show-overflow-tooltip />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="runSync(row)">执行同步</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="block-title">
          业务知识
          <el-button type="primary" size="small" class="ml" @click="openKnowledge()">新建</el-button>
        </div>
        <el-table :data="knowledge" stripe>
          <el-table-column label="类型" width="130">
            <template #default="{ row }">{{ statusLabel(row.knowledgeType) }}</template>
          </el-table-column>
          <el-table-column prop="knowledgeCode" label="编码" width="140" />
          <el-table-column prop="knowledgeName" label="名称" width="160" />
          <el-table-column prop="payloadJson" label="内容" min-width="220" show-overflow-tooltip />
          <el-table-column prop="priority" label="优先级" width="80" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button link type="primary" @click="openKnowledge(row)">编辑</el-button>
              <el-button link type="danger" @click="deleteKnowledge(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 全局条件 -->
      <div v-if="tab === 'global'">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="openField()">登记全局条件</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="globalFields" stripe class="mb">
          <el-table-column prop="fieldCode" label="编码" width="130" />
          <el-table-column prop="fieldName" label="名称" width="120" />
          <el-table-column prop="semantic" label="语义" min-width="160" show-overflow-tooltip />
          <el-table-column label="控件" width="90">
            <template #default="{ row }">{{ statusLabel(row.controlType) }}</template>
          </el-table-column>
          <el-table-column prop="matchNameRegex" label="匹配规则" min-width="160" show-overflow-tooltip />
          <el-table-column label="必填" width="70">
            <template #default="{ row }">{{ row.requiredFlag ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button link type="primary" @click="openField(row)">编辑</el-button>
              <el-button link @click="autoMatch(row)">自动匹配</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="block-title">字段绑定（可确认/排除）</div>
        <el-table :data="globalBindings" stripe>
          <el-table-column prop="fieldName" label="全局条件" width="120" />
          <el-table-column prop="tableName" label="表" width="140" />
          <el-table-column prop="columnCode" label="字段" width="120" />
          <el-table-column prop="matchScore" label="匹配分" width="90" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">{{ statusLabel(row.confirmStatus) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button v-if="row.confirmStatus === 'SUGGESTED'" link type="success" @click="confirmBind(row, true)">确认</el-button>
              <el-button v-if="row.confirmStatus === 'SUGGESTED'" link type="danger" @click="confirmBind(row, false)">排除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-alert v-if="impact" class="mt" type="info" :closable="false"
          :title="`影响预览：已确认 ${impact.confirmedTables} 表，建议 ${impact.suggestedTables} 表`" />
      </div>

      <!-- 标识 -->
      <div v-if="tab === 'identity'">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="identityDialog = true">登记标识映射</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="identities" stripe>
          <el-table-column prop="entityId" label="实体ID" width="140" />
          <el-table-column label="标识类型" width="120">
            <template #default="{ row }">{{ statusLabel(row.idType) }}</template>
          </el-table-column>
          <el-table-column prop="idValue" label="标识值" min-width="160" />
          <el-table-column prop="displayName" label="显示名" width="160" />
          <el-table-column prop="sourceSystem" label="来源系统" width="120" />
        </el-table>
      </div>

      <!-- 运维 -->
      <div v-if="tab === 'ops'">
        <div class="block-title">已保存组合方案</div>
        <el-table :data="savedQueries" stripe size="small" class="mb">
          <el-table-column prop="queryName" label="名称" />
          <el-table-column label="模式" width="100">
            <template #default="{ row }">{{ statusLabel(row.mode) }}</template>
          </el-table-column>
          <el-table-column prop="ownerName" label="创建人" width="100" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="applySaved(row)">调用</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="block-title">检索日志</div>
        <el-table :data="queryLogs" stripe size="small" class="mb" max-height="260">
          <el-table-column prop="createdAt" label="时间" width="170" />
          <el-table-column label="动作" width="110">
            <template #default="{ row }">{{ statusLabel(row.actionType) }}</template>
          </el-table-column>
          <el-table-column label="模式" width="90">
            <template #default="{ row }">{{ statusLabel(row.mode) }}</template>
          </el-table-column>
          <el-table-column prop="queryText" label="查询" min-width="160" show-overflow-tooltip />
          <el-table-column prop="hitCount" label="命中" width="70" />
          <el-table-column prop="operatorName" label="操作人" width="100" />
        </el-table>
        <div class="block-title">审计</div>
        <el-table :data="audits" stripe size="small" max-height="260">
          <el-table-column prop="createdAt" label="时间" width="170" />
          <el-table-column label="动作" width="110">
            <template #default="{ row }">{{ statusLabel(row.actionType) }}</template>
          </el-table-column>
          <el-table-column prop="targetRef" label="对象" width="160" />
          <el-table-column prop="detailJson" label="详情" min-width="200" show-overflow-tooltip />
          <el-table-column prop="operatorName" label="操作人" width="100" />
        </el-table>
      </div>
    </PageCard>

    <el-dialog v-model="knowledgeDialog" :title="knowledgeForm.id ? '编辑业务知识' : '新建业务知识'" width="600px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="类型">
          <el-select v-model="knowledgeForm.knowledgeType" style="width:100%">
            <el-option label="同义词" value="SYNONYM" />
            <el-option label="词典" value="DICT" />
            <el-option label="标识规则" value="IDENTITY_RULE" />
            <el-option label="字段别名" value="FIELD_ALIAS" />
            <el-option label="权重" value="WEIGHT" />
            <el-option label="交互配置" value="UI_CONFIG" />
            <el-option label="分类标签映射" value="CLASS_TAG_MAP" />
          </el-select>
        </el-form-item>
        <el-form-item label="编码"><el-input v-model="knowledgeForm.knowledgeCode" :disabled="!!knowledgeForm.id" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="knowledgeForm.knowledgeName" /></el-form-item>
        <el-form-item label="JSON"><el-input v-model="knowledgeForm.payloadJson" type="textarea" :rows="6" /></el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="knowledgeForm.priority" :min="1" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="knowledgeForm.description" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="knowledgeDialog = false">取消</el-button>
        <el-button type="primary" @click="saveKnowledge">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="fieldDialog" :title="fieldForm.id ? '编辑全局条件' : '登记全局条件'" width="560px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="编码"><el-input v-model="fieldForm.fieldCode" :disabled="!!fieldForm.id" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="fieldForm.fieldName" /></el-form-item>
        <el-form-item label="语义"><el-input v-model="fieldForm.semantic" /></el-form-item>
        <el-form-item label="控件">
          <el-select v-model="fieldForm.controlType" style="width:100%">
            <el-option label="输入框" value="INPUT" />
            <el-option label="下拉" value="SELECT" />
            <el-option label="日期" value="DATE" />
            <el-option label="数值" value="NUMBER" />
            <el-option label="范围" value="RANGE" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称正则"><el-input v-model="fieldForm.matchNameRegex" /></el-form-item>
        <el-form-item label="注释关键词"><el-input v-model="fieldForm.matchCommentKeywords" placeholder="逗号分隔" /></el-form-item>
        <el-form-item label="必填"><el-switch v-model="fieldForm.requiredFlag" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="fieldForm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fieldDialog = false">取消</el-button>
        <el-button type="primary" @click="saveField">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="identityDialog" title="登记标识映射" width="520px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="实体ID"><el-input v-model="identityForm.entityId" placeholder="同一实体共用" /></el-form-item>
        <el-form-item label="标识类型">
          <el-select v-model="identityForm.idType" style="width:100%">
            <el-option label="身份证" value="ID_CARD" />
            <el-option label="统一社会信用代码" value="CREDIT_CODE" />
            <el-option label="手机号" value="PHONE" />
            <el-option label="自定义" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="标识值"><el-input v-model="identityForm.idValue" /></el-form-item>
        <el-form-item label="显示名"><el-input v-model="identityForm.displayName" /></el-form-item>
        <el-form-item label="来源系统"><el-input v-model="identityForm.sourceSystem" /></el-form-item>
        <el-form-item label="画像JSON"><el-input v-model="identityForm.profileJson" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="identityDialog = false">取消</el-button>
        <el-button type="primary" @click="saveIdentity">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.hint { color: var(--el-text-color-secondary); margin: 0 0 12px; line-height: 1.5; }
.mb { margin-bottom: 12px; }
.mt { margin-top: 12px; }
.ml { margin-left: 8px; }
.block-title { font-weight: 600; margin: 8px 0; }
.attr { margin-right: 8px; color: var(--el-text-color-secondary); font-size: 12px; }
.profile { padding: 8px; background: var(--el-fill-color-lighter); border-radius: 4px; }
</style>
