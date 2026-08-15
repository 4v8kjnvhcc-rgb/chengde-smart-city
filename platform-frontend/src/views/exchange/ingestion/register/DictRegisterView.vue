<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { downloadText, ingestionApi, useIngestionLoading, type Dict, type DictItem } from '../useIngestionHub'
import {
  approveRegister,
  canAuditRegister,
  canEditRegister,
  canSubmitRegister,
  canWithdrawRegister,
  loadRegisterLogs,
  registerStatusZh,
  rejectRegister,
  submitRegister,
  withdrawRegister,
} from './register-workflow'

const props = defineProps<{ module: string }>()
const { loading, loadError, withLoad } = useIngestionLoading()
const dicts = ref<Dict[]>([])
const selectedIds = ref<number[]>([])
const keyword = ref('')
const dictDialog = ref(false)
const dictSaving = ref(false)
const editingDictId = ref<number | null>(null)
const dictForm = reactive({
  dictName: '',
  standardNo: '',
  remark: '',
})
const itemDialog = ref(false)
const editDict = ref<Dict | null>(null)
const dictItems = ref<DictItem[]>([])
const itemForm = reactive({ itemKey: '', itemValue: '', bizUsage: '', sortOrder: 0 })
const editingItemId = ref<number | null>(null)
const importText = ref('')
const exportDialog = ref(false)
const exportIds = ref<number[]>([])

const linkDialog = ref(false)
const linkDict = ref<Dict | null>(null)
const links = ref<Record<string, unknown>[]>([])
const linkForm = reactive({
  projectId: undefined as number | undefined,
  systemId: undefined as number | undefined,
  sourceId: undefined as number | undefined,
  tableId: undefined as number | undefined,
  columnId: undefined as number | undefined,
})
const linkProjects = ref<Array<{ id: number; projectName: string }>>([])
const linkSystems = ref<Array<{ id: number; systemName: string }>>([])
const linkSources = ref<Array<{ id: number; sourceName: string }>>([])
const linkTables = ref<Array<{ id: number; tableName: string }>>([])
const linkColumns = ref<Array<{ id: number; columnCode: string; columnName: string }>>([])

const viewDialog = ref(false)
const viewLogs = ref<Record<string, unknown>[]>([])
const viewRow = ref<Dict | null>(null)
const rejectVisible = ref(false)
const rejectReason = ref('')
const rejectTarget = ref<Dict | null>(null)
const auditVisible = ref(false)
const auditTarget = ref<Dict | null>(null)
const auditDecision = ref<'APPROVE' | 'REJECT'>('APPROVE')
const auditSubmitting = ref(false)

const isManage = computed(() => props.module === 'm050')
const title = computed(() => (isManage.value ? '数据字典管理' : '数据字典登记'))
/** 部门登记：可提交；平台管理：可审核/删除，不可提交 */
const canSubmit = computed(() => !isManage.value)
const canAudit = computed(() => isManage.value)
const canDeptEdit = (status?: string | null) => !isManage.value && canEditRegister(status)
const canDeptDelete = (status?: string | null) => !isManage.value && canEditRegister(status)

async function reload() {
  await withLoad(async () => {
    dicts.value = (await ingestionApi.dicts(keyword.value || undefined)).data
  })
}

function onReset() {
  keyword.value = ''
  void reload()
}

function openCreateDict() {
  editingDictId.value = null
  dictForm.dictName = ''
  dictForm.standardNo = ''
  dictForm.remark = ''
  dictDialog.value = true
}

function openEditDict(row: Dict) {
  editingDictId.value = row.id
  dictForm.dictName = row.dictName || ''
  dictForm.standardNo = row.standardNo || ''
  dictForm.remark = row.remark || ''
  dictDialog.value = true
}

async function saveDict() {
  if (!dictForm.dictName.trim()) {
    ElMessage.warning('请填写字典名称')
    return
  }
  dictSaving.value = true
  const body = {
    dictName: dictForm.dictName.trim(),
    standardNo: dictForm.standardNo.trim(),
    remark: dictForm.remark.trim(),
  }
  try {
    if (editingDictId.value) {
      await ingestionApi.updateDict(editingDictId.value, body)
      ElMessage.success('字典已更新')
    } else {
      await ingestionApi.createDict(body)
      ElMessage.success('字典已登记')
    }
    dictDialog.value = false
    editingDictId.value = null
    await reload()
  } catch {
    ElMessage.error(editingDictId.value ? '更新失败' : '登记失败')
  } finally {
    dictSaving.value = false
  }
}

async function downloadTemplate() {
  const res = await ingestionApi.dictTemplate()
  downloadText('dict_import_template.csv', res.data)
}

async function doImport() {
  if (!importText.value.trim()) {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = '.csv,.xlsx,.xls,text/csv'
    input.onchange = async () => {
      const file = input.files?.[0]
      if (!file) return
      const text = await file.text()
      const res = await ingestionApi.importDict(text)
      ElMessage.success(`已导入 ${res.data.importedRows} 行`)
      await reload()
    }
    input.click()
    return
  }
  const res = await ingestionApi.importDict(importText.value)
  ElMessage.success(`已导入 ${res.data.importedRows} 行`)
  importText.value = ''
  await reload()
}

function openExport() {
  exportIds.value = selectedIds.value.length ? [...selectedIds.value] : dicts.value.map((d) => d.id)
  exportDialog.value = true
}

async function doExport() {
  const res = await ingestionApi.exportDict(exportIds.value)
  downloadText('dict_export.csv', res.data)
  exportDialog.value = false
  ElMessage.success('导出完成')
}

async function deleteSelected() {
  if (!selectedIds.value.length) return
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 个字典？`, '删除确认', { type: 'warning' })
  await ingestionApi.deleteDicts(selectedIds.value)
  selectedIds.value = []
  ElMessage.success('已删除')
  await reload()
}

async function deleteOne(row: Dict) {
  await ElMessageBox.confirm(`确定删除字典「${row.dictName}」？`, '删除确认', { type: 'warning' })
  await ingestionApi.deleteDicts([row.id])
  ElMessage.success('已删除')
  await reload()
}

async function openItems(row: Dict) {
  editDict.value = row
  dictItems.value = (await ingestionApi.dictItems(row.id)).data
  itemForm.itemKey = ''
  itemForm.itemValue = ''
  itemForm.bizUsage = ''
  editingItemId.value = null
  itemDialog.value = true
}

async function saveItem() {
  if (!editDict.value || !itemForm.itemKey.trim() || !itemForm.itemValue.trim()) {
    ElMessage.warning('请填写代码值与中文名称')
    return
  }
  const body = {
    itemKey: itemForm.itemKey.trim(),
    itemValue: itemForm.itemValue.trim(),
    bizUsage: itemForm.bizUsage.trim(),
    sortOrder: itemForm.sortOrder,
  }
  if (editingItemId.value) {
    await ingestionApi.updateDictItem(editingItemId.value, body)
  } else {
    await ingestionApi.createDictItem(editDict.value.id, body)
  }
  itemForm.itemKey = ''
  itemForm.itemValue = ''
  itemForm.bizUsage = ''
  editingItemId.value = null
  dictItems.value = (await ingestionApi.dictItems(editDict.value.id)).data
  await reload()
}

function editItem(row: DictItem) {
  editingItemId.value = row.id
  itemForm.itemKey = row.itemKey
  itemForm.itemValue = row.itemValue
  itemForm.bizUsage = row.bizUsage || ''
  itemForm.sortOrder = row.sortOrder
}

async function removeItem(row: DictItem) {
  await ingestionApi.deleteDictItem(row.id)
  if (editDict.value) dictItems.value = (await ingestionApi.dictItems(editDict.value.id)).data
  await reload()
}

async function openView(row: Dict) {
  viewRow.value = row
  viewLogs.value = await loadRegisterLogs('DICT', row.id)
  viewDialog.value = true
}

async function doSubmit(row: Dict) {
  await submitRegister('DICT', row.id)
  ElMessage.success('已提交审核')
  await reload()
}

async function doWithdraw(row: Dict) {
  await ElMessageBox.confirm(`确认撤销「${row.dictName}」的审核提交？撤销后状态将回到草稿。`, '撤销确认', {
    type: 'warning',
  })
  await withdrawRegister('DICT', row.id)
  ElMessage.success('已撤销，状态为草稿')
  await reload()
}

function openAudit(row: Dict) {
  auditTarget.value = row
  auditDecision.value = 'APPROVE'
  rejectReason.value = ''
  auditVisible.value = true
}

async function submitAudit() {
  if (!auditTarget.value) return
  if (auditDecision.value === 'REJECT' && !rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  auditSubmitting.value = true
  try {
    if (auditDecision.value === 'APPROVE') {
      await approveRegister('DICT', auditTarget.value.id)
      ElMessage.success('审核通过')
    } else {
      await rejectRegister('DICT', auditTarget.value.id, rejectReason.value.trim())
      ElMessage.success('已驳回')
    }
    auditVisible.value = false
    await reload()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '审核失败')
  } finally {
    auditSubmitting.value = false
  }
}

function openReject(row: Dict) {
  rejectTarget.value = row
  rejectReason.value = ''
  rejectVisible.value = true
}

async function doReject() {
  if (!rejectTarget.value || !rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  await rejectRegister('DICT', rejectTarget.value.id, rejectReason.value.trim())
  ElMessage.success('已驳回')
  rejectVisible.value = false
  await reload()
}

async function openLink(row: Dict) {
  linkDict.value = row
  links.value = (await ingestionApi.dictColumnLinks(row.id)).data || []
  linkProjects.value = ((await ingestionApi.projects()).data || []) as Array<{ id: number; projectName: string }>
  linkForm.projectId = undefined
  linkForm.systemId = undefined
  linkForm.sourceId = undefined
  linkForm.tableId = undefined
  linkForm.columnId = undefined
  linkSystems.value = []
  linkSources.value = []
  linkTables.value = []
  linkColumns.value = []
  linkDialog.value = true
}

async function onLinkProjectChange(pid?: number) {
  linkForm.systemId = undefined
  linkForm.sourceId = undefined
  linkForm.tableId = undefined
  linkForm.columnId = undefined
  linkSystems.value = pid ? ((await ingestionApi.systems(pid)).data || []) : []
  linkSources.value = []
  linkTables.value = []
  linkColumns.value = []
}

async function onLinkSystemChange(sid?: number) {
  linkForm.sourceId = undefined
  linkForm.tableId = undefined
  linkForm.columnId = undefined
  if (!linkForm.projectId || !sid) {
    linkSources.value = []
    return
  }
  linkSources.value = ((await ingestionApi.dataSources(linkForm.projectId, sid)).data || []) as Array<{
    id: number
    sourceName: string
  }>
  linkTables.value = []
  linkColumns.value = []
}

async function onLinkSourceChange(sourceId?: number) {
  linkForm.tableId = undefined
  linkForm.columnId = undefined
  linkTables.value = sourceId ? ((await ingestionApi.tables(sourceId)).data || []) : []
  linkColumns.value = []
}

async function onLinkTableChange(tableId?: number) {
  linkForm.columnId = undefined
  linkColumns.value = tableId ? ((await ingestionApi.columns(tableId)).data || []) : []
}

async function bindLink() {
  if (!linkDict.value || !linkForm.columnId) {
    ElMessage.warning('请选择到数据项')
    return
  }
  await ingestionApi.bindDictColumn(linkDict.value.id, { columnId: linkForm.columnId })
  ElMessage.success('已关联')
  links.value = (await ingestionApi.dictColumnLinks(linkDict.value.id)).data || []
}

async function unbindLink(linkId: number) {
  await ingestionApi.unbindDictColumn(linkId)
  ElMessage.success('已取消关联')
  if (linkDict.value) links.value = (await ingestionApi.dictColumnLinks(linkDict.value.id)).data || []
}

onMounted(reload)
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard :title="title">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item v-if="isManage" label="查询" class="portal-field-md">
          <el-input v-model="keyword" placeholder="名称/标准依据" clearable @keyup.enter="reload" />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button v-if="!isManage" type="primary" @click="openCreateDict">新增字典</el-button>
          <el-button v-if="isManage" @click="reload">查询</el-button>
          <el-button v-if="isManage" @click="onReset">重置</el-button>
          <el-button v-if="!isManage" @click="downloadTemplate">下载模板</el-button>
          <el-button v-if="!isManage" @click="doImport">导入 Excel/CSV</el-button>
          <el-button @click="openExport">导出</el-button>
          <el-button v-if="isManage" type="danger" :disabled="!selectedIds.length" @click="deleteSelected">删除</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="dicts" stripe @selection-change="(rows: Dict[]) => selectedIds = rows.map((r) => r.id)">
        <el-table-column v-if="isManage" type="selection" width="45" />
        <el-table-column prop="dictName" label="字典名称" min-width="140" />
        <el-table-column prop="standardNo" label="标准依据" min-width="160" show-overflow-tooltip />
        <el-table-column prop="itemCount" label="字典项数" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small">{{ registerStatusZh(row.registerStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="360" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openView(row)">查看</el-button>
            <el-button
              v-if="canDeptEdit(row.registerStatus)"
              link
              type="primary"
              @click="openEditDict(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="canDeptDelete(row.registerStatus)"
              link
              type="danger"
              @click="deleteOne(row)"
            >
              删除
            </el-button>
            <el-button link type="primary" @click="openItems(row)">字典项</el-button>
            <el-button v-if="!isManage" link type="primary" @click="openLink(row)">关联</el-button>
            <el-button
              v-if="canSubmit && canSubmitRegister(row.registerStatus)"
              link
              type="primary"
              @click="doSubmit(row)"
            >
              提交
            </el-button>
            <el-button
              v-if="canSubmit && canWithdrawRegister(row.registerStatus)"
              link
              type="warning"
              @click="doWithdraw(row)"
            >
              撤销
            </el-button>
            <template v-if="canAudit && canAuditRegister(row.registerStatus)">
              <el-button link type="success" @click="openAudit(row)">审核</el-button>
            </template>
            <el-button
              v-if="isManage"
              link
              type="danger"
              @click="deleteOne(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <el-dialog v-model="dictDialog" :title="editingDictId ? '编辑字典' : '新增字典'" width="520px" destroy-on-close>
      <el-form label-width="96px">
        <el-form-item label="字典名称" required>
          <el-input v-model="dictForm.dictName" placeholder="如：性别代码" />
        </el-form-item>
        <el-form-item label="标准依据">
          <el-input v-model="dictForm.standardNo" placeholder="如：GB/T 2261.1-2003" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="dictForm.remark" type="textarea" :rows="2" placeholder="用途说明（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dictDialog = false">取消</el-button>
        <el-button type="primary" :loading="dictSaving" @click="saveDict">{{ editingDictId ? '保存' : '确定登记' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="itemDialog" :title="`字典项 · ${editDict?.dictName || ''}`" width="720px">
      <el-form label-width="110px" style="margin-bottom:12px">
        <el-form-item label="代码值" required>
          <el-input v-model="itemForm.itemKey" placeholder="如 M" />
        </el-form-item>
        <el-form-item label="中文名称" required>
          <el-input v-model="itemForm.itemValue" placeholder="如 男" />
        </el-form-item>
        <el-form-item label="业务使用说明">
          <el-input v-model="itemForm.bizUsage" type="textarea" :rows="2" placeholder="业务侧如何使用该代码值（可选）" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveItem">{{ editingItemId ? '保存' : '新增' }}</el-button>
          <el-button v-if="editingItemId" @click="editingItemId = null; itemForm.itemKey = ''; itemForm.itemValue = ''; itemForm.bizUsage = ''">取消编辑</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="dictItems" size="small" stripe>
        <el-table-column prop="itemKey" label="代码值" width="100" />
        <el-table-column prop="itemValue" label="中文名称" width="120" />
        <el-table-column prop="bizUsage" label="业务使用说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link @click="editItem(row)">编辑</el-button>
            <el-button link type="danger" @click="removeItem(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="exportDialog" title="导出数据字典" width="400px">
      <p>已选 {{ exportIds.length }} 个字典，确认导出为 CSV？</p>
      <template #footer>
        <el-button @click="exportDialog = false">取消</el-button>
        <el-button type="primary" @click="doExport">导出</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="linkDialog" title="关联数据项" width="720px" destroy-on-close>
      <el-form label-width="96px">
        <el-form-item label="项目">
          <el-select v-model="linkForm.projectId" filterable clearable style="width:100%" @change="onLinkProjectChange">
            <el-option v-for="p in linkProjects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="系统">
          <el-select v-model="linkForm.systemId" filterable clearable style="width:100%" @change="onLinkSystemChange">
            <el-option v-for="s in linkSystems" :key="s.id" :label="s.systemName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据库">
          <el-select v-model="linkForm.sourceId" filterable clearable style="width:100%" @change="onLinkSourceChange">
            <el-option v-for="s in linkSources" :key="s.id" :label="s.sourceName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据表">
          <el-select v-model="linkForm.tableId" filterable clearable placeholder="输入表名筛选" style="width:100%" @change="onLinkTableChange">
            <el-option v-for="t in linkTables" :key="t.id" :label="t.tableName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据项">
          <el-select v-model="linkForm.columnId" filterable clearable style="width:100%">
            <el-option
              v-for="c in linkColumns"
              :key="c.id"
              :label="`${c.columnCode}${c.columnName ? ' / ' + c.columnName : ''}`"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="bindLink">关联</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="links" size="small" stripe>
        <el-table-column prop="projectName" label="项目" min-width="100" />
        <el-table-column prop="systemName" label="系统" min-width="100" />
        <el-table-column prop="sourceName" label="数据库" min-width="100" />
        <el-table-column prop="tableName" label="表" min-width="100" />
        <el-table-column label="数据项" min-width="140">
          <template #default="{ row }">{{ row.columnCode }} {{ row.columnName ? '/ ' + row.columnName : '' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="danger" @click="unbindLink(row.id as number)">取消关联</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="viewDialog" title="查看字典" width="640px" destroy-on-close>
      <el-descriptions v-if="viewRow" :column="1" border size="small">
        <el-descriptions-item label="名称">{{ viewRow.dictName }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ registerStatusZh(viewRow.registerStatus) }}</el-descriptions-item>
        <el-descriptions-item v-if="viewRow.rejectReason" label="驳回原因">{{ viewRow.rejectReason }}</el-descriptions-item>
        <el-descriptions-item label="标准依据">{{ viewRow.standardNo || '—' }}</el-descriptions-item>
      </el-descriptions>
      <h4 style="margin:16px 0 8px">提交 / 审核记录</h4>
      <el-table :data="viewLogs" size="small" stripe max-height="240">
        <el-table-column prop="action" label="动作" width="90" />
        <el-table-column label="状态" min-width="140">
          <template #default="{ row }">
            {{ registerStatusZh(row.fromStatus as string) }} → {{ registerStatusZh(row.toStatus as string) }}
          </template>
        </el-table-column>
        <el-table-column prop="commentText" label="说明" min-width="120" />
        <el-table-column prop="operatorName" label="操作人" width="90" />
        <el-table-column prop="createdAt" label="时间" width="160" />
      </el-table>
    </el-dialog>

    <el-dialog v-model="rejectVisible" title="驳回" width="420px" destroy-on-close>
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="驳回原因（必填）" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" @click="doReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="auditVisible" title="数据字典审核" width="480px" destroy-on-close>
      <el-form label-width="96px">
        <el-form-item label="字典名称">
          <el-input :model-value="auditTarget?.dictName || ''" disabled />
        </el-form-item>
        <el-form-item label="审核结果" required>
          <el-radio-group v-model="auditDecision">
            <el-radio value="APPROVE">审核通过</el-radio>
            <el-radio value="REJECT">审核驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="auditDecision === 'REJECT'" label="驳回原因" required>
          <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="驳回原因（必填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" :loading="auditSubmitting" @click="submitAudit">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>
