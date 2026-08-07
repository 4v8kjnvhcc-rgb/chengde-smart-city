<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

const loading = ref(false)
const tab = ref('template')
const templates = ref<Record<string, unknown>[]>([])
const catalogs = ref<Record<string, unknown>[]>([])
const objections = ref<Record<string, unknown>[]>([])
const manifests = ref<Record<string, unknown>[]>([])
const duties = ref<Record<string, unknown>[]>([])

defineProps<{
  /** 嵌入供需对接侧栏时隐藏独立页头，避免与外层标题重复 */
  embedded?: boolean
}>()

const templateForm = reactive({
  templateCode: '',
  templateName: '',
  demandType: 'STRUCTURED',
  fieldSchema: '[{"key":"dataDomain","label":"数据域"},{"key":"updateFreq","label":"更新频率"},{"key":"shareScope","label":"共享范围"}]',
})
const schemaRows = ref<{ key: string; label: string }[]>([
  { key: 'dataDomain', label: '数据域' },
  { key: 'updateFreq', label: '更新频率' },
  { key: 'shareScope', label: '共享范围' },
])
const catalogForm = reactive({ title: '', description: '' })
void catalogForm

const superviseForm = reactive({
  responseDeadlineDays: 10,
  mountDeadlineDays: 10,
})

const templateDialog = reactive({
  visible: false,
  mode: 'view' as 'view' | 'edit',
  id: 0,
  templateCode: '',
  templateName: '',
  demandType: 'STRUCTURED',
  status: 'ACTIVE',
  schemaRows: [] as { key: string; label: string }[],
})

function parseSchemaRows(raw: unknown): { key: string; label: string }[] {
  try {
    const parsed = typeof raw === 'string' ? JSON.parse(raw || '[]') : raw
    if (!Array.isArray(parsed)) return []
    return parsed.map((f: Record<string, string>) => ({
      key: f.key || f.name || '',
      label: f.label || f.name || f.key || '',
    }))
  } catch {
    return []
  }
}

function syncSchemaFromRows() {
  templateForm.fieldSchema = JSON.stringify(
    schemaRows.value
      .filter((r) => r.key.trim() && r.label.trim())
      .map((r) => ({ key: r.key.trim(), label: r.label.trim() })),
  )
}

function addSchemaRow() {
  schemaRows.value.push({ key: '', label: '' })
}

function removeSchemaRow(idx: number) {
  schemaRows.value.splice(idx, 1)
  syncSchemaFromRows()
}

function addDialogSchemaRow() {
  templateDialog.schemaRows.push({ key: '', label: '' })
}

function removeDialogSchemaRow(idx: number) {
  templateDialog.schemaRows.splice(idx, 1)
}

async function load() {
  loading.value = true
  try {
    if (tab.value === 'template') {
      templates.value = (await api.get('/exchange/supply/templates', { params: { scope: 'all' } })).data
    } else if (tab.value === 'catalog') {
      catalogs.value = (await api.get('/exchange/supply/catalog-manifest')).data
    } else if (tab.value === 'objection') {
      objections.value = (await api.get('/exchange/supply/objections')).data
    } else if (tab.value === 'manifest') {
      manifests.value = (await api.get('/exchange/supply/manifests')).data
    } else if (tab.value === 'duty') {
      duties.value = (await api.get('/exchange/supply/duties')).data
    } else if (tab.value === 'supervise') {
      const res = await api.get('/exchange/supply/supervise-settings')
      superviseForm.responseDeadlineDays = Number(res.data.responseDeadlineDays ?? 10)
      superviseForm.mountDeadlineDays = Number(res.data.mountDeadlineDays ?? 10)
    }
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

async function saveSuperviseSettings() {
  if (!Number.isInteger(superviseForm.responseDeadlineDays) || superviseForm.responseDeadlineDays < 1) {
    return ElMessage.warning('确认/反馈时限须为正整数（天）')
  }
  if (!Number.isInteger(superviseForm.mountDeadlineDays) || superviseForm.mountDeadlineDays < 1) {
    return ElMessage.warning('挂载门户时限须为正整数（天）')
  }
  const res = await api.put('/exchange/supply/supervise-settings', {
    responseDeadlineDays: superviseForm.responseDeadlineDays,
    mountDeadlineDays: superviseForm.mountDeadlineDays,
  })
  superviseForm.responseDeadlineDays = Number(res.data.responseDeadlineDays ?? 10)
  superviseForm.mountDeadlineDays = Number(res.data.mountDeadlineDays ?? 10)
  ElMessage.success('督查督办设置已保存')
}

async function createTemplate() {
  if (!templateForm.templateName) return ElMessage.warning('请填写模板名称')
  if (templateForm.demandType === 'STRUCTURED') {
    syncSchemaFromRows()
    try {
      JSON.parse(templateForm.fieldSchema)
    } catch {
      return ElMessage.warning('字段模型须为合法 JSON')
    }
  }
  await api.post('/exchange/supply/templates', {
    ...templateForm,
    fieldSchema: templateForm.demandType === 'STRUCTURED' ? templateForm.fieldSchema : '{}',
  })
  templateForm.templateName = ''
  templateForm.templateCode = ''
  schemaRows.value = [
    { key: 'dataDomain', label: '数据域' },
    { key: 'updateFreq', label: '更新频率' },
    { key: 'shareScope', label: '共享范围' },
  ]
  syncSchemaFromRows()
  ElMessage.success('模板已创建')
  await load()
}

function openViewTemplate(row: Record<string, unknown>) {
  templateDialog.visible = true
  templateDialog.mode = 'view'
  templateDialog.id = Number(row.id)
  templateDialog.templateCode = String(row.templateCode || '')
  templateDialog.templateName = String(row.templateName || '')
  templateDialog.demandType = String(row.demandType || 'STRUCTURED')
  templateDialog.status = String(row.status || '')
  templateDialog.schemaRows = parseSchemaRows(row.fieldSchema)
}

function openEditTemplate(row: Record<string, unknown>) {
  templateDialog.visible = true
  templateDialog.mode = 'edit'
  templateDialog.id = Number(row.id)
  templateDialog.templateCode = String(row.templateCode || '')
  templateDialog.templateName = String(row.templateName || '')
  templateDialog.demandType = String(row.demandType || 'STRUCTURED')
  templateDialog.status = String(row.status || 'ACTIVE')
  templateDialog.schemaRows = parseSchemaRows(row.fieldSchema)
  if (!templateDialog.schemaRows.length && templateDialog.demandType === 'STRUCTURED') {
    templateDialog.schemaRows = [{ key: '', label: '' }]
  }
}

async function saveEditTemplate() {
  if (!templateDialog.templateName.trim()) return ElMessage.warning('请填写模板名称')
  const fieldSchema =
    templateDialog.demandType === 'STRUCTURED'
      ? JSON.stringify(
          templateDialog.schemaRows
            .filter((r) => r.key.trim() && r.label.trim())
            .map((r) => ({ key: r.key.trim(), label: r.label.trim() })),
        )
      : '{}'
  await api.post(`/exchange/supply/templates/${templateDialog.id}`, {
    templateName: templateDialog.templateName.trim(),
    demandType: templateDialog.demandType,
    fieldSchema,
    status: templateDialog.status,
  })
  ElMessage.success('模板已更新')
  templateDialog.visible = false
  await load()
}

async function deleteTemplate(row: Record<string, unknown>) {
  try {
    await ElMessageBox.confirm(`确认删除模板「${row.templateName}」？删除后不可恢复。`, '删除模板', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await api.post(`/exchange/supply/templates/${row.id}/delete`)
  ElMessage.success('模板已删除')
  await load()
}

async function closeObjection(id: number) {
  await api.post(`/exchange/supply/objections/${id}/process`, { action: 'CLOSE', handlerNote: '配置侧已处理' })
  await load()
}

async function reopenObjection(id: number) {
  await api.post(`/exchange/supply/objections/${id}/process`, {
    action: 'REOPEN_AUDIT',
    handlerNote: '配置侧回流需求审核',
  })
  ElMessage.success('已回流需求审核')
  await load()
}

async function exportManifest(id: number) {
  await api.post(`/exchange/supply/manifests/${id}/export`)
  ElMessage.success('清单已导出')
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <PageHeader
      v-if="!embedded"
      title="供需配置"
      description="系统管理 · 数据共享交换平台 · 应用平台 — 模板、统一目录只读、异议治理与数据责任台账"
    />
    <el-radio-group v-model="tab" class="mb" @change="load">
      <el-radio-button value="template">需求模板</el-radio-button>
      <el-radio-button value="catalog">目录治理</el-radio-button>
      <el-radio-button value="objection">异议治理</el-radio-button>
      <el-radio-button value="manifest">全局清单</el-radio-button>
      <el-radio-button value="duty">数据责任</el-radio-button>
      <el-radio-button value="supervise">督查督办设置</el-radio-button>
    </el-radio-group>

    <PageCard v-if="tab === 'template'" title="需求模板管理">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        title="结构化模板请配置 fieldSchema（JSON 数组，如 [{key,label}]），前台按模板字段模型化填报；非结构化模板供正文录入。"
      />
      <el-form label-width="96px" style="max-width:720px;margin-bottom:12px">
        <el-form-item label="编码"><el-input v-model="templateForm.templateCode" placeholder="可选，空则自动生成" style="width:240px" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="templateForm.templateName" style="width:320px" /></el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="templateForm.demandType">
            <el-radio-button value="STRUCTURED">结构化</el-radio-button>
            <el-radio-button value="UNSTRUCTURED">非结构化</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="templateForm.demandType === 'STRUCTURED'" label="数据项">
          <div class="schema-editor">
            <div v-for="(row, idx) in schemaRows" :key="idx" class="schema-row">
              <el-input v-model="row.key" placeholder="字段编码" style="width:140px" @change="syncSchemaFromRows" />
              <el-input v-model="row.label" placeholder="显示名称" style="width:180px" @change="syncSchemaFromRows" />
              <el-button link type="danger" @click="removeSchemaRow(idx)">删除</el-button>
            </div>
            <el-button type="primary" plain @click="addSchemaRow">新增数据项</el-button>
          </div>
        </el-form-item>
        <el-form-item v-else label="说明">
          <el-text type="info">非结构化模板用于正文描述式需求填报。</el-text>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="createTemplate">新建模板</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="templates" stripe size="small">
        <el-table-column prop="templateName" label="名称" min-width="160" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">{{ $statusLabel(row.demandType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openViewTemplate(row)">查看</el-button>
            <el-button link type="primary" @click="openEditTemplate(row)">编辑</el-button>
            <el-button link type="danger" @click="deleteTemplate(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <PageCard v-else-if="tab === 'catalog'" title="统一目录只读视图">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        title="供需对接系统不再新建/发布目录。目录请在「指标与目录体系构建」或「数据目录管理系统」编目审批，审批通过后同步至部门数据共享门户；此处仅查看已同步目录。"
      />
      <el-table :data="catalogs" stripe size="small">
        <el-table-column prop="catalogCode" label="编码" width="140" />
        <el-table-column prop="title" label="标题" min-width="160" />
        <el-table-column prop="catalogOrigin" label="来源" width="120">
          <template #default="{ row }">{{ row.catalogOrigin === 'INGEST' ? '指标与目录' : (row.catalogOrigin === 'GOVERNANCE' ? '数据目录管理' : (row.catalogOrigin || '-')) }}</template>
        </el-table-column>
        <el-table-column prop="govResourceId" label="资源ID" width="90" />
        <el-table-column prop="providerOrg" label="提供方" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ $statusLabel(row.publishStatus) }}</template>
        </el-table-column>
      </el-table>
    </PageCard>

    <PageCard v-else-if="tab === 'objection'" title="异议治理">
      <el-table :data="objections" stripe size="small">
        <el-table-column prop="title" label="标题" min-width="140" />
        <el-table-column prop="catalogId" label="目录ID" width="90" />
        <el-table-column prop="demandId" label="需求ID" width="90" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ $statusLabel(row.objectionType) }}</template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="180" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'CLOSED'" link type="warning" @click="reopenObjection(Number(row.id))">回流审核</el-button>
            <el-button v-if="row.status !== 'CLOSED'" link @click="closeObjection(Number(row.id))">关闭</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <PageCard v-else-if="tab === 'manifest'" title="全局供需清单">
      <el-table :data="manifests" stripe size="small">
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ $statusLabel(row.manifestType) }}</template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column label="授权级别" width="100">
          <template #default="{ row }">{{ $statusLabel(row.authLevel) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="exportManifest(Number(row.id))">导出</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <PageCard v-else-if="tab === 'duty'" title="数据责任台账">
      <el-table :data="duties" stripe size="small">
        <el-table-column prop="demandId" label="需求ID" width="90" />
        <el-table-column prop="dutyOrg" label="责任单位" min-width="140" />
        <el-table-column label="责任类型" width="110">
          <template #default="{ row }">{{ $statusLabel(row.dutyType) }}</template>
        </el-table-column>
        <el-table-column label="履约路径" width="160">
          <template #default="{ row }">{{ $statusLabel(row.fulfillPath) }}</template>
        </el-table-column>
        <el-table-column prop="catalogId" label="目录ID" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="180" />
      </el-table>
    </PageCard>

    <PageCard v-else-if="tab === 'supervise'" title="督查督办设置">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        title="时限按自然日计算（含周末），默认均为 10 天。修改后对新发起的分发、督办、退回与确认生效；已写入需求的截止日不回溯调整。"
      />
      <el-form label-width="200px" style="max-width:640px">
        <el-form-item label="确认/反馈时限" required>
          <el-input-number
            v-model="superviseForm.responseDeadlineDays"
            :min="1"
            :max="365"
            :step="1"
            controls-position="right"
          />
          <span class="unit-hint">天（自然日）</span>
          <div class="field-tip">分发或督办后，数据提供部门、数据需求部门须在该时限内确认或反馈。</div>
        </el-form-item>
        <el-form-item label="挂载门户时限" required>
          <el-input-number
            v-model="superviseForm.mountDeadlineDays"
            :min="1"
            :max="365"
            :step="1"
            controls-position="right"
          />
          <span class="unit-hint">天（自然日）</span>
          <div class="field-tip">数源部门同意提供后，须在该时限内将目录挂载到部门数据共享门户。</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveSuperviseSettings">保存设置</el-button>
        </el-form-item>
      </el-form>
    </PageCard>

    <el-dialog
      v-model="templateDialog.visible"
      :title="templateDialog.mode === 'view' ? '查看需求模板' : '编辑需求模板'"
      width="560px"
      destroy-on-close
    >
      <el-form label-width="96px">
        <el-form-item label="编码">
          <el-input :model-value="templateDialog.templateCode" disabled />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input
            v-model="templateDialog.templateName"
            :disabled="templateDialog.mode === 'view'"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="templateDialog.demandType" :disabled="templateDialog.mode === 'view'">
            <el-radio-button value="STRUCTURED">结构化</el-radio-button>
            <el-radio-button value="UNSTRUCTURED">非结构化</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="templateDialog.mode === 'edit'" label="状态">
          <el-radio-group v-model="templateDialog.status">
            <el-radio-button value="ACTIVE">启用</el-radio-button>
            <el-radio-button value="INACTIVE">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-else label="状态">
          <span>{{ $statusLabel(templateDialog.status) }}</span>
        </el-form-item>
        <el-form-item v-if="templateDialog.demandType === 'STRUCTURED'" label="数据项">
          <div class="schema-editor">
            <div v-for="(row, idx) in templateDialog.schemaRows" :key="idx" class="schema-row">
              <el-input
                v-model="row.key"
                placeholder="字段编码"
                style="width:140px"
                :disabled="templateDialog.mode === 'view'"
              />
              <el-input
                v-model="row.label"
                placeholder="显示名称"
                style="width:180px"
                :disabled="templateDialog.mode === 'view'"
              />
              <el-button
                v-if="templateDialog.mode === 'edit'"
                link
                type="danger"
                @click="removeDialogSchemaRow(idx)"
              >删除</el-button>
            </div>
            <el-button
              v-if="templateDialog.mode === 'edit'"
              type="primary"
              plain
              @click="addDialogSchemaRow"
            >新增数据项</el-button>
            <el-text v-if="templateDialog.mode === 'view' && !templateDialog.schemaRows.length" type="info">无数据项</el-text>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateDialog.visible = false">关闭</el-button>
        <el-button v-if="templateDialog.mode === 'edit'" type="primary" @click="saveEditTemplate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mb { margin-bottom: 12px; }
.schema-editor { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.schema-row { display: flex; gap: 8px; align-items: center; }
.unit-hint { margin-left: 8px; color: var(--el-text-color-secondary); }
.field-tip { width: 100%; margin-top: 6px; font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.5; }
</style>
