<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  ingestionApi,
  type AssetCatalogReg,
  type AssetTag,
  type BizSystem,
  type DataColumn,
  type DataSource,
  type DataTable,
  type Project,
} from '../useIngestionHub'
import { loadRegisterLogs, registerStatusZh } from './register-workflow'

const auth = useAuthStore()

const props = defineProps<{
  modelValue: boolean
  mode: 'create' | 'edit' | 'view'
  recordId?: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [boolean]
  saved: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

const readonly = computed(() => props.mode === 'view')
const saving = ref(false)
const loadingDetail = ref(false)
const projects = ref<Project[]>([])
const systems = ref<BizSystem[]>([])
const sources = ref<DataSource[]>([])
const tables = ref<DataTable[]>([])
const columns = ref<DataColumn[]>([])
const tagOptions = ref<{ id: number; tagName: string }[]>([])
const orgOptions = ref<Array<{ id: number; orgCode?: string; orgName: string; parentId?: number; label: string }>>([])
const auditLogs = ref<Record<string, unknown>[]>([])
const auditLogsLoading = ref(false)
/** 平台/超级管理员可选其它机构；部门管理员锁定本组织 */
const canPickOtherOrg = ref(false)

/** 机构下拉：选中值用 orgId */
const orgSelectValue = ref<number | null>(null)

const form = reactive({
  assetName: '',
  assetDesc: '',
  contactInfo: '',
  dataTags: [] as string[],
  orgId: null as number | null,
  orgName: '',
  projectId: null as number | null,
  systemId: null as number | null,
  sourceId: null as number | null,
  tableId: null as number | null,
  accessMode: '',
  formatType: '',
  transferMode: '',
  formatLocked: 0,
  bizPurpose: '',
  bizScenario: '',
  accessScope: '',
  controlReq: '',
  qualityFilePath: '',
  qualityFileName: '',
  riskFilePath: '',
  riskFileName: '',
  otherInfo: '',
  projectName: '',
  systemName: '',
  tableName: '',
})

const FORMAT_OPTIONS = [
  { value: 'DATABASE', label: '数据库' },
  { value: 'FILE', label: '文件' },
  { value: 'API', label: '应用程序编程接口' },
]

const ACCESS_OPTIONS = ['数据库直连', '文件导入', '接口拉取', 'CDC 实时', '手工上传']
const TRANSFER_OPTIONS = ['全量', '增量', '实时', '批量']

const dialogTitle = computed(() => {
  if (props.mode === 'create') return '新增资产目录登记'
  if (props.mode === 'edit') return '编辑资产目录登记'
  return '查看资产目录'
})

const sourceChain = computed(() => {
  const parts: string[] = []
  if (form.projectName) parts.push(form.projectName)
  if (form.systemName) parts.push(form.systemName)
  if (form.tableName) parts.push(form.tableName)
  return parts
})

function resetForm() {
  form.assetName = ''
  form.assetDesc = ''
  form.contactInfo = ''
  form.dataTags = []
  form.orgId = null
  form.orgName = ''
  form.projectId = null
  form.systemId = null
  form.sourceId = null
  form.tableId = null
  form.accessMode = ''
  form.formatType = ''
  form.transferMode = ''
  form.formatLocked = 0
  form.bizPurpose = ''
  form.bizScenario = ''
  form.accessScope = ''
  form.controlReq = ''
  form.qualityFilePath = ''
  form.qualityFileName = ''
  form.riskFilePath = ''
  form.riskFileName = ''
  form.otherInfo = ''
  form.projectName = ''
  form.systemName = ''
  form.tableName = ''
  orgSelectValue.value = null
  systems.value = []
  sources.value = []
  tables.value = []
  columns.value = []
  auditLogs.value = []
}

function formatLogTime(v?: unknown) {
  if (v == null || v === '') return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}

function actionZh(action?: unknown) {
  const a = String(action || '').toUpperCase()
  if (a === 'CREATE') return '创建'
  if (a === 'SUBMIT') return '提交'
  if (a === 'APPROVE') return '审核通过'
  if (a === 'REJECT') return '审核驳回'
  return String(action || '—')
}

async function loadAuditLogs(id: number) {
  auditLogsLoading.value = true
  try {
    auditLogs.value = await loadRegisterLogs('CATALOG_REG', id)
  } catch {
    auditLogs.value = []
  } finally {
    auditLogsLoading.value = false
  }
}

function fillFromRecord(row: AssetCatalogReg) {
  form.assetName = row.assetName || ''
  form.assetDesc = row.assetDesc || ''
  form.contactInfo = row.contactInfo || ''
  form.dataTags = (row.dataTags || '').split(/[,，]/).map((s) => s.trim()).filter(Boolean)
  form.orgId = row.orgId ?? null
  form.orgName = row.orgName || ''
  form.projectId = row.projectId ?? null
  form.systemId = null
  form.sourceId = row.sourceId ?? null
  form.tableId = row.tableId ?? null
  form.accessMode = row.accessMode || ''
  form.formatType = row.formatType || ''
  form.transferMode = row.transferMode || ''
  form.formatLocked = 0
  form.bizPurpose = row.bizPurpose || ''
  form.bizScenario = row.bizScenario || ''
  form.accessScope = row.accessScope || ''
  form.controlReq = row.controlReq || ''
  form.qualityFilePath = row.qualityFilePath || ''
  form.qualityFileName = row.qualityFileName || ''
  form.riskFilePath = row.riskFilePath || ''
  form.riskFileName = row.riskFileName || ''
  form.otherInfo = row.otherInfo || ''
  form.projectName = row.projectName || ''
  form.systemName = row.systemName || ''
  form.tableName = row.tableName || ''
  orgSelectValue.value = form.orgId
}

async function loadMeta() {
  try {
    const orgRes = await ingestionApi.assetCatalogOrgOptions()
    orgOptions.value = orgRes.data || []
  } catch (e: unknown) {
    orgOptions.value = []
    ElMessage.warning(e instanceof Error ? e.message : '组织机构选项加载失败，请确认后端已重启')
  }
  try {
    const projRes = await ingestionApi.projects()
    projects.value = projRes.data || []
  } catch {
    projects.value = []
  }
  try {
    const tagRes = await ingestionApi.tagTree()
    const list: { id: number; tagName: string }[] = []
    const walk = (nodes: AssetTag[]) => {
      for (const n of nodes) {
        if (n.children?.length) walk(n.children)
        else if (n.tagName) list.push({ id: n.id, tagName: n.stdCode ? `${n.stdCode} ${n.tagName}` : n.tagName })
      }
    }
    walk(tagRes.data.standardTree || [])
    for (const t of tagRes.data.customTags || []) {
      if (t.tagName) list.push({ id: t.id, tagName: t.tagName })
    }
    tagOptions.value = list
  } catch {
    tagOptions.value = []
  }
}

async function loadSystems(projectId: number) {
  systems.value = (await ingestionApi.systems(projectId)).data || []
}

async function loadSources(projectId: number, systemId?: number | null) {
  if (systemId) {
    sources.value = (await ingestionApi.dataSources(undefined, systemId)).data || []
  } else {
    sources.value = (await ingestionApi.dataSources(projectId)).data || []
  }
}

async function loadTables(sourceId: number) {
  tables.value = (await ingestionApi.tables(sourceId)).data || []
}

async function loadColumns(tableId: number) {
  columns.value = (await ingestionApi.columns(tableId)).data || []
}

async function applyDefaults() {
  canPickOtherOrg.value = !!(
    auth.isSystemAdmin
    || auth.hasPermission('hub:ingestion:register:asset-catalog-mgmt')
  )
  try {
    const d = (await ingestionApi.assetCatalogDefaults()).data || {}
    if (typeof d.canPickOtherOrg === 'boolean') {
      canPickOtherOrg.value = d.canPickOtherOrg || auth.isSystemAdmin
    }
    form.orgId = (d.orgId as number | null | undefined) ?? auth.user?.orgId ?? null
    form.orgName = String(d.orgName || auth.user?.orgName || '')
    form.contactInfo = String(d.contactInfo || auth.user?.phone || '')
    orgSelectValue.value = form.orgId
  } catch {
    form.orgId = auth.user?.orgId ?? null
    form.orgName = auth.user?.orgName || ''
    form.contactInfo = auth.user?.phone || ''
    orgSelectValue.value = form.orgId
  }
}

async function openInit() {
  resetForm()
  await loadMeta()
  canPickOtherOrg.value = !!(
    auth.isSystemAdmin
    || auth.hasPermission('hub:ingestion:register:asset-catalog-mgmt')
  )
  if (props.mode === 'create') {
    await applyDefaults()
    return
  }
  if (!props.recordId) return
  loadingDetail.value = true
  try {
    const row = (await ingestionApi.assetCatalogDetail(props.recordId)).data
    fillFromRecord(row)
    if (form.projectId) {
      await loadSystems(form.projectId)
      if (form.sourceId) {
        const allSources = (await ingestionApi.dataSources(form.projectId)).data || []
        const hit = allSources.find((s) => s.id === form.sourceId)
        form.systemId = hit?.systemId ?? null
        if (form.systemId) {
          const sys = systems.value.find((s) => s.id === form.systemId)
          form.systemName = sys?.systemName || form.systemName
          await loadSources(form.projectId, form.systemId)
        } else {
          sources.value = allSources
        }
      }
    }
    if (form.sourceId) await loadTables(form.sourceId)
    if (form.tableId) await loadColumns(form.tableId)
    if (props.mode === 'view') {
      await loadAuditLogs(props.recordId)
    }
  } finally {
    loadingDetail.value = false
  }
}

watch(
  () => props.modelValue,
  async (v) => {
    if (v) await openInit()
  },
)

async function onOrgChange(val: number | null) {
  if (val == null) {
    form.orgId = null
    form.orgName = ''
    return
  }
  const o = orgOptions.value.find((x) => x.id === val)
  form.orgId = val
  form.orgName = o?.orgName || ''
}

async function onProjectChange(projectId: number | null) {
  form.systemId = null
  form.sourceId = null
  form.tableId = null
  form.systemName = ''
  form.tableName = ''
  systems.value = []
  sources.value = []
  tables.value = []
  columns.value = []
  const p = projects.value.find((x) => x.id === projectId)
  form.projectName = p?.projectName || ''
  if (projectId) await loadSystems(projectId)
}

function mapFormatFromSource(sourceType?: string): string | null {
  if (!sourceType) return null
  const t = sourceType.toUpperCase()
  if (t.includes('FILE') || t === 'CSV' || t === 'EXCEL' || t === 'FTP' || t === 'SFTP') return 'FILE'
  if (t.includes('API') || t === 'HTTP' || t === 'REST' || t === 'WS') return 'API'
  if (t.includes('DB') || t.includes('JDBC') || t.includes('MYSQL') || t.includes('ORACLE')
    || t.includes('POSTGRES') || t === 'DATABASE' || t === 'TABLE') return 'DATABASE'
  return null
}

async function onSystemChange(systemId: number | null) {
  form.sourceId = null
  form.tableId = null
  form.tableName = ''
  sources.value = []
  tables.value = []
  columns.value = []
  const s = systems.value.find((x) => x.id === systemId)
  form.systemName = s?.systemName || ''
  if (form.projectId && systemId) await loadSources(form.projectId, systemId)
}

async function onSourceChange(sourceId: number | null) {
  form.tableId = null
  form.tableName = ''
  tables.value = []
  columns.value = []
  const s = sources.value.find((x) => x.id === sourceId)
  if (!form.systemName) form.systemName = s?.systemName || ''
  // 仅预填建议值，不锁定，用户可改
  const mapped = mapFormatFromSource(s?.sourceType)
  if (mapped && !form.formatType) form.formatType = mapped
  form.formatLocked = 0
  if (sourceId) await loadTables(sourceId)
}

async function onTableChange(tableId: number | null) {
  columns.value = []
  const t = tables.value.find((x) => x.id === tableId)
  form.tableName = t?.tableName || t?.tableCode || ''
  if (tableId) await loadColumns(tableId)
}

function formatColumnType(row: DataColumn) {
  const type = (row.dataType || '—').trim()
  if (row.lengthVal != null && row.lengthVal > 0 && !/\(\d/.test(type)) {
    return `${type}(${row.lengthVal})`
  }
  return type
}

async function uploadFile(kind: 'quality' | 'risk', file: File) {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('kind', kind)
  const res = await ingestionApi.assetCatalogUpload(fd)
  if (kind === 'quality') {
    form.qualityFilePath = res.data.filePath
    form.qualityFileName = res.data.fileName
  } else {
    form.riskFilePath = res.data.filePath
    form.riskFileName = res.data.fileName
  }
  ElMessage.success('附件已上传')
}

function onQualityChange(uploadFileObj: { raw?: File } | undefined) {
  if (readonly.value) return
  const raw = uploadFileObj?.raw
  if (raw) void uploadFile('quality', raw)
}

function onRiskChange(uploadFileObj: { raw?: File } | undefined) {
  if (readonly.value) return
  const raw = uploadFileObj?.raw
  if (raw) void uploadFile('risk', raw)
}

function buildBody(): Record<string, unknown> {
  return {
    assetName: form.assetName.trim(),
    assetDesc: form.assetDesc.trim(),
    contactInfo: form.contactInfo.trim(),
    dataTags: form.dataTags.join(','),
    orgId: form.orgId,
    orgName: form.orgName.trim(),
    projectId: form.projectId,
    sourceId: form.sourceId,
    tableId: form.tableId,
    accessMode: form.accessMode,
    formatType: form.formatType,
    transferMode: form.transferMode,
    bizPurpose: form.bizPurpose.trim(),
    bizScenario: form.bizScenario.trim(),
    accessScope: form.accessScope.trim(),
    controlReq: form.controlReq.trim(),
    qualityFilePath: form.qualityFilePath || null,
    qualityFileName: form.qualityFileName || null,
    riskFilePath: form.riskFilePath || null,
    riskFileName: form.riskFileName || null,
    otherInfo: form.otherInfo.trim(),
  }
}

async function save() {
  if (readonly.value) return
  if (!form.assetName.trim()) {
    ElMessage.warning('请填写资产名称')
    return
  }
  if (!form.orgName.trim() && !form.orgId) {
    ElMessage.warning('请选择所属机构')
    return
  }
  if (!form.projectId || !form.systemId || !form.sourceId || !form.tableId) {
    ElMessage.warning('请选择来源项目、来源系统、数据库与数据表')
    return
  }
  saving.value = true
  try {
    const body = buildBody()
    if (props.mode === 'edit' && props.recordId) {
      await ingestionApi.assetCatalogUpdate(props.recordId, body)
      ElMessage.success('已保存')
    } else {
      await ingestionApi.assetCatalogCreate(body)
      ElMessage.success('已登记')
    }
    visible.value = false
    emit('saved')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="960px"
    top="3vh"
    destroy-on-close
    class="asset-catalog-dialog"
  >
    <div v-loading="loadingDetail" class="form-body">
      <!-- 基本信息 -->
      <section class="form-section">
        <header class="section-head">
          <div>
            <h4>基本信息</h4>
            <p>资产标识与责任单位</p>
          </div>
        </header>
        <el-form label-width="100px" class="section-form">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="资产名称" required>
                <el-input v-model="form.assetName" :disabled="readonly" placeholder="请输入资产名称" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="所属机构" required>
                <el-select
                  v-model="orgSelectValue"
                  :disabled="readonly || !canPickOtherOrg"
                  filterable
                  clearable
                  placeholder="选择组织机构"
                  style="width: 100%"
                  @change="onOrgChange"
                >
                  <el-option
                    v-for="o in orgOptions"
                    :key="o.id"
                    :label="o.orgName || o.label"
                    :value="o.id"
                  >
                    <span>{{ o.orgName || o.label }}</span>
                    <span v-if="o.orgCode" style="float: right; color: #94a3b8; font-size: 12px">{{ o.orgCode }}</span>
                  </el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="联系方式">
                <el-input
                  v-model="form.contactInfo"
                  :disabled="readonly"
                  clearable
                  placeholder="默认当前账号联系方式，可修改"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="数据标签">
                <el-select
                  v-model="form.dataTags"
                  :disabled="readonly"
                  multiple
                  filterable
                  collapse-tags
                  collapse-tags-tooltip
                  placeholder="从标签库选择"
                  style="width: 100%"
                >
                  <el-option v-for="t in tagOptions" :key="t.id" :label="t.tagName" :value="t.tagName" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="资产描述">
                <el-input v-model="form.assetDesc" :disabled="readonly" type="textarea" :rows="2" placeholder="请输入资产描述" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </section>

      <!-- 来源信息 -->
      <section class="form-section">
        <header class="section-head">
          <div>
            <h4>来源信息</h4>
            <p>项目 → 来源系统 → 数据库 → 数据表（逐级选择）</p>
          </div>
        </header>

        <div v-if="sourceChain.length" class="source-breadcrumb">
          <template v-for="(part, idx) in sourceChain" :key="idx">
            <span v-if="idx > 0" class="crumb-sep">›</span>
            <span class="crumb-item" :class="{ active: idx === sourceChain.length - 1 }">{{ part }}</span>
          </template>
        </div>

        <el-form label-width="100px" class="section-form">
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="来源项目" required>
                <el-select
                  v-model="form.projectId"
                  :disabled="readonly"
                  filterable
                  placeholder="选择项目"
                  style="width: 100%"
                  @change="onProjectChange"
                >
                  <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="来源系统" required>
                <el-select
                  v-model="form.systemId"
                  :disabled="readonly || !form.projectId"
                  filterable
                  placeholder="选择系统"
                  style="width: 100%"
                  @change="onSystemChange"
                >
                  <el-option v-for="s in systems" :key="s.id" :label="s.systemName" :value="s.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="数据库" required>
                <el-select
                  v-model="form.sourceId"
                  :disabled="readonly || !form.systemId"
                  filterable
                  placeholder="选择数据库"
                  style="width: 100%"
                  @change="onSourceChange"
                >
                  <el-option
                    v-for="s in sources"
                    :key="s.id"
                    :label="s.sourceName"
                    :value="s.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="数据表" required>
                <el-select
                  v-model="form.tableId"
                  :disabled="readonly || !form.sourceId"
                  filterable
                  placeholder="选择已登记表"
                  style="width: 100%"
                  @change="onTableChange"
                >
                  <el-option
                    v-for="t in tables"
                    :key="t.id"
                    :label="t.tableName || t.tableCode"
                    :value="t.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="接入方式">
                <el-select v-model="form.accessMode" :disabled="readonly" allow-create filterable placeholder="请选择或填写" style="width: 100%">
                  <el-option v-for="o in ACCESS_OPTIONS" :key="o" :label="o" :value="o" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="格式类型">
                <el-select
                  v-model="form.formatType"
                  :disabled="readonly"
                  placeholder="请选择"
                  style="width: 100%"
                >
                  <el-option v-for="o in FORMAT_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="传输方式">
                <el-select v-model="form.transferMode" :disabled="readonly" allow-create filterable placeholder="请选择或填写" style="width: 100%">
                  <el-option v-for="o in TRANSFER_OPTIONS" :key="o" :label="o" :value="o" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <div v-if="form.tableId" class="field-panel">
          <div class="field-panel__head">
            <div>
              <strong>字段详情</strong>
              <p class="field-meta">共 {{ columns.length }} 个字段</p>
            </div>
            <span v-if="form.tableName" class="field-table-name">{{ form.tableName }}</span>
          </div>
          <el-table
            v-if="columns.length"
            :data="columns"
            stripe
            size="small"
            class="field-table"
            max-height="280"
            empty-text="暂无字段"
          >
            <el-table-column prop="columnCode" label="字段编码" min-width="140" show-overflow-tooltip />
            <el-table-column prop="columnName" label="字段名称" min-width="140" show-overflow-tooltip />
            <el-table-column label="类型" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="type-text">{{ formatColumnType(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="可空" width="90" align="center">
              <template #default="{ row }">
                <el-tag
                  size="small"
                  effect="plain"
                  :type="row.nullableFlag === 1 ? 'info' : 'warning'"
                >
                  {{ row.nullableFlag === 1 ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无字段，请先在「数据库/表/项登记」完善表结构" :image-size="64" />
        </div>
        <div v-else class="field-placeholder">
          请先完成上方项目、来源系统、数据库、数据表选择，字段结构将在此展示
        </div>
      </section>

      <!-- 业务信息 -->
      <section class="form-section">
        <header class="section-head">
          <div>
            <h4>业务信息</h4>
            <p>用途与应用场景</p>
          </div>
        </header>
        <el-form label-width="100px" class="section-form">
          <el-form-item label="主要用途">
            <el-input v-model="form.bizPurpose" :disabled="readonly" type="textarea" :rows="2" placeholder="请输入主要用途" />
          </el-form-item>
          <el-form-item label="业务场景">
            <el-input v-model="form.bizScenario" :disabled="readonly" type="textarea" :rows="2" placeholder="请输入业务场景" />
          </el-form-item>
        </el-form>
      </section>

      <!-- 安全控制 -->
      <section class="form-section">
        <header class="section-head">
          <div>
            <h4>安全控制</h4>
            <p>访问范围与控制要求</p>
          </div>
        </header>
        <el-form label-width="100px" class="section-form">
          <el-form-item label="访问权限范围">
            <el-input v-model="form.accessScope" :disabled="readonly" placeholder="如：本部门 / 本系统 / 全市共享" />
          </el-form-item>
          <el-form-item label="控制要求">
            <el-input v-model="form.controlReq" :disabled="readonly" type="textarea" :rows="2" placeholder="脱敏、加密、审计等要求" />
          </el-form-item>
        </el-form>
      </section>

      <!-- 其他信息 -->
      <section class="form-section">
        <header class="section-head">
          <div>
            <h4>其他信息</h4>
            <p>质量 / 风险评估附件及其他说明</p>
          </div>
        </header>
        <el-form label-width="100px" class="section-form">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="质量评估">
                <div class="attach-row">
                  <el-upload
                    v-if="!readonly"
                    :auto-upload="false"
                    :show-file-list="false"
                    :on-change="(f: any) => onQualityChange(f)"
                  >
                    <el-button size="small">上传附件</el-button>
                  </el-upload>
                  <span v-if="form.qualityFileName" class="attach-name">{{ form.qualityFileName }}</span>
                  <span v-else class="attach-empty">未上传</span>
                </div>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="风险评估">
                <div class="attach-row">
                  <el-upload
                    v-if="!readonly"
                    :auto-upload="false"
                    :show-file-list="false"
                    :on-change="(f: any) => onRiskChange(f)"
                  >
                    <el-button size="small">上传附件</el-button>
                  </el-upload>
                  <span v-if="form.riskFileName" class="attach-name">{{ form.riskFileName }}</span>
                  <span v-else class="attach-empty">未上传</span>
                </div>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="其他">
                <el-input v-model="form.otherInfo" :disabled="readonly" type="textarea" :rows="2" placeholder="其他补充说明" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </section>

      <section v-if="readonly" class="form-section">
        <div class="section-head">
          <h4>提交 / 审核记录</h4>
          <p>含提交、通过、驳回等操作流水</p>
        </div>
        <el-table
          v-loading="auditLogsLoading"
          :data="auditLogs"
          stripe
          size="small"
          empty-text="暂无提交/审核记录"
        >
          <el-table-column label="操作" width="100">
            <template #default="{ row }">{{ actionZh(row.action) }}</template>
          </el-table-column>
          <el-table-column label="原状态" width="110">
            <template #default="{ row }">{{ registerStatusZh(row.fromStatus as string) }}</template>
          </el-table-column>
          <el-table-column label="新状态" width="110">
            <template #default="{ row }">{{ registerStatusZh(row.toStatus as string) }}</template>
          </el-table-column>
          <el-table-column label="操作人" width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.operatorName || '—' }}</template>
          </el-table-column>
          <el-table-column label="说明" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.commentText || '—' }}</template>
          </el-table-column>
          <el-table-column label="时间" width="170">
            <template #default="{ row }">{{ formatLogTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </section>
    </div>
    <template #footer>
      <el-button @click="visible = false">{{ readonly ? '关闭' : '取消' }}</el-button>
      <el-button v-if="!readonly" type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.form-body {
  max-height: 74vh;
  overflow: auto;
  padding: 0 4px 8px 0;
}
.form-section {
  border: 1px solid #e8eef6;
  border-radius: 8px;
  background: #fbfcfe;
  padding: 14px 16px 8px;
  margin-bottom: 14px;
}
.section-head {
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px dashed #d7e2f0;
}
.section-head h4 {
  margin: 0;
  font-size: 15px;
  color: #1f2937;
  font-weight: 600;
}
.section-head p {
  margin: 2px 0 0;
  font-size: 12px;
  color: #6b7280;
}
.section-form {
  margin-bottom: 0;
}
.source-breadcrumb {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin: 0 0 12px;
  padding: 8px 12px;
  background: #eef4fb;
  border-radius: 6px;
  font-size: 13px;
}
.crumb-sep {
  color: #94a3b8;
  font-weight: 600;
}
.crumb-item {
  color: #64748b;
}
.crumb-item.active {
  color: #1d4f91;
  font-weight: 600;
}
.field-panel {
  margin: 4px 0 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}
.field-panel__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 12px 16px 10px;
  background: #fff;
  border-bottom: 1px solid #f0f2f5;
}
.field-panel__head strong {
  display: block;
  color: #111827;
  font-size: 14px;
  font-weight: 600;
}
.field-meta {
  margin: 4px 0 0;
  color: #9ca3af;
  font-size: 12px;
}
.field-table-name {
  color: #6b7280;
  font-size: 12px;
  padding-top: 2px;
}
.field-table :deep(.el-table__header th) {
  background: #f5f7fa;
  color: #4b5563;
  font-weight: 500;
}
.field-table :deep(.el-table__row td) {
  color: #374151;
}
.type-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: #1f2937;
}
.field-placeholder {
  margin: 4px 0 10px;
  padding: 16px;
  text-align: center;
  color: #94a3b8;
  font-size: 13px;
  border: 1px dashed #d1d9e6;
  border-radius: 8px;
  background: #fff;
}
.attach-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.attach-name {
  color: #1d4f91;
  font-size: 13px;
}
.attach-empty {
  color: #909399;
  font-size: 13px;
}
</style>
