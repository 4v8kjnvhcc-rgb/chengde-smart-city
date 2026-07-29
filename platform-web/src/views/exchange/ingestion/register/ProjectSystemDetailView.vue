<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { projectOptionLabel } from '../ingestion-project-scope'
import {
  ingestionApi,
  useIngestionLoading,
  type BizSystem,
  type DataSource,
  type Project,
} from '../useIngestionHub'
import { ingestionRegisterCache } from '../ingestion-register-cache'

const props = defineProps<{
  project: Project
}>()

const emit = defineEmits<{
  back: []
  changed: []
}>()

const { loading, loadError, withLoad } = useIngestionLoading()
const systems = ref<BizSystem[]>([])
/** 按系统懒加载缓存 */
const sourcesBySystem = ref<Record<number, DataSource[]>>({})
const loadedSystemIds = ref<Set<number>>(new Set())
const expandLoading = ref<Record<number, boolean>>({})

const addSystemDialog = ref(false)
const addSystemSaving = ref(false)
const addSystemName = ref('')

const renameSystemDialog = ref(false)
const renameSystemSaving = ref(false)
const renameSystemName = ref('')
const renamingSystem = ref<BizSystem | null>(null)

const addDsDialog = ref(false)
const addDsSaving = ref(false)
const addDsSystemId = ref<number | null>(null)
const addDsForm = reactive({
  sourceName: '',
  sourceType: 'MYSQL',
  host: '127.0.0.1',
  port: 3306,
  database: '',
  username: '',
  password: '',
})

const editMetaDialog = ref(false)
const editMetaSaving = ref(false)
const editMetaName = ref('')
const editingMetaDs = ref<DataSource | null>(null)

const connDialog = ref(false)
const editingDs = ref<DataSource | null>(null)
const connForm = reactive({ host: '127.0.0.1', port: 3306, database: '', username: '', password: '' })

function isOtherProject(code?: string) {
  return !!code && (code === 'PRJ_OTHER' || code.startsWith('PRJ_OTHER_'))
}

function isOtherSystem(code?: string) {
  return !!code && (code === 'SYS_OTHER' || code.startsWith('SYS_OTHER_'))
}

function isManualUploadSource(code?: string) {
  return !!code && (code === 'DS_MANUAL_UPLOAD' || code.startsWith('DS_MANUAL_UPLOAD_'))
}

function isDbType(type: string) {
  return type === 'MYSQL' || type === 'ORACLE'
}

function connLabel(status?: string) {
  if (!status) return '—'
  if (status === 'OK') return '正常'
  if (status === 'FAILED') return '失败'
  if (status === 'UNTESTED') return '未测试'
  return status
}

const title = computed(() => `系统列表 · ${projectOptionLabel(props.project)}`)

const projectPathLabel = computed(() => props.project.projectName || '—')

function sourcesOf(systemId: number): DataSource[] {
  return sourcesBySystem.value[systemId] || []
}

function invalidateSources(systemId?: number) {
  if (systemId != null) {
    loadedSystemIds.value.delete(systemId)
    const next = { ...sourcesBySystem.value }
    delete next[systemId]
    sourcesBySystem.value = next
  } else {
    loadedSystemIds.value = new Set()
    sourcesBySystem.value = {}
  }
}

async function ensureSourcesLoaded(systemId: number, force = false) {
  if (!force && loadedSystemIds.value.has(systemId)) return
  expandLoading.value = { ...expandLoading.value, [systemId]: true }
  try {
    const res = await ingestionApi.dataSources(undefined, systemId)
    sourcesBySystem.value = { ...sourcesBySystem.value, [systemId]: res.data || [] }
    loadedSystemIds.value.add(systemId)
    ingestionRegisterCache.invalidate('dataSources', 'tables')
  } finally {
    expandLoading.value = { ...expandLoading.value, [systemId]: false }
  }
}

async function onExpandChange(row: BizSystem, expandedRows: BizSystem[]) {
  const expanded = expandedRows.some((r) => r.id === row.id)
  if (expanded) await ensureSourcesLoaded(row.id)
}

async function reloadSystems() {
  await withLoad(async () => {
    const res = await ingestionApi.systems(props.project.id)
    systems.value = res.data || []
  })
}

function openAddSystem() {
  addSystemName.value = ''
  addSystemDialog.value = true
}

async function submitAddSystem() {
  const name = addSystemName.value.trim()
  if (!name) {
    ElMessage.warning('请填写系统名称')
    return
  }
  addSystemSaving.value = true
  try {
    await ingestionApi.createSystem({
      projectId: props.project.id,
      systemName: name,
    })
    ElMessage.success('系统已创建')
    addSystemDialog.value = false
    await reloadSystems()
    emit('changed')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '创建失败')
  } finally {
    addSystemSaving.value = false
  }
}

function openRenameSystem(row: BizSystem) {
  if (isOtherSystem(row.systemCode)) {
    ElMessage.warning('平台默认「其他」系统名称不可修改')
    return
  }
  renamingSystem.value = row
  renameSystemName.value = row.systemName
  renameSystemDialog.value = true
}

async function submitRenameSystem() {
  if (!renamingSystem.value) return
  const name = renameSystemName.value.trim()
  if (!name) {
    ElMessage.warning('请填写系统名称')
    return
  }
  renameSystemSaving.value = true
  try {
    await ingestionApi.updateSystem(renamingSystem.value.id, { systemName: name })
    ElMessage.success('系统名称已更新')
    renameSystemDialog.value = false
    invalidateSources(renamingSystem.value.id)
    await reloadSystems()
    emit('changed')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '更新失败')
  } finally {
    renameSystemSaving.value = false
  }
}

async function deleteSystem(row: BizSystem) {
  if (isOtherSystem(row.systemCode)) {
    ElMessage.warning('平台默认「其他」系统不可删除')
    return
  }
  if ((row.dataSourceCount || 0) > 0) {
    ElMessage.warning('请先删除该系统下的数据源后再删除系统')
    return
  }
  await ElMessageBox.confirm(`确定删除系统「${row.systemName}」？`, '删除确认', { type: 'warning' })
  await ingestionApi.deleteSystem(row.id)
  ElMessage.success('系统已删除')
  invalidateSources(row.id)
  await reloadSystems()
  emit('changed')
}

function openAddDs(systemId: number) {
  addDsSystemId.value = systemId
  addDsForm.sourceName = ''
  addDsForm.sourceType = 'MYSQL'
  addDsForm.host = '127.0.0.1'
  addDsForm.port = 3306
  addDsForm.database = ''
  addDsForm.username = ''
  addDsForm.password = ''
  addDsDialog.value = true
}

async function submitAddDs() {
  if (!addDsSystemId.value) return
  if (!addDsForm.sourceName.trim()) {
    ElMessage.warning('请填写数据源名称')
    return
  }
  if (isDbType(addDsForm.sourceType) && !addDsForm.database.trim()) {
    ElMessage.warning('请填写库名')
    return
  }
  addDsSaving.value = true
  try {
    await ingestionApi.createDataSource({
      systemId: addDsSystemId.value,
      projectId: props.project.id,
      sourceName: addDsForm.sourceName.trim(),
      sourceType: addDsForm.sourceType,
      host: addDsForm.host,
      port: addDsForm.port,
      database: addDsForm.database,
      username: addDsForm.username,
      password: addDsForm.password,
    })
    ElMessage.success('数据源已创建')
    addDsDialog.value = false
    await ensureSourcesLoaded(addDsSystemId.value, true)
    await reloadSystems()
    emit('changed')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '创建失败')
  } finally {
    addDsSaving.value = false
  }
}

function openEditMeta(ds: DataSource) {
  editingMetaDs.value = ds
  editMetaName.value = ds.sourceName || ''
  editMetaDialog.value = true
}

async function submitEditMeta() {
  if (!editingMetaDs.value) return
  if (!editMetaName.value.trim()) {
    ElMessage.warning('请填写数据源名称')
    return
  }
  editMetaSaving.value = true
  try {
    const systemId = editingMetaDs.value.systemId
    await ingestionApi.updateDataSource(editingMetaDs.value.id, {
      sourceName: editMetaName.value.trim(),
    })
    ElMessage.success('已保存')
    editMetaDialog.value = false
    if (systemId) await ensureSourcesLoaded(systemId, true)
    emit('changed')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    editMetaSaving.value = false
  }
}

function openConn(ds: DataSource) {
  editingDs.value = ds
  try {
    const cfg = JSON.parse(ds.connConfigJson || '{}') as Record<string, unknown>
    connForm.host = String(cfg.host || '127.0.0.1')
    connForm.port = Number(cfg.port || 3306)
    connForm.database = String(cfg.database || '')
    connForm.username = String(cfg.username || '')
  } catch {
    connForm.host = '127.0.0.1'
    connForm.port = 3306
    connForm.database = ''
    connForm.username = ''
  }
  connForm.password = ''
  connDialog.value = true
}

async function saveConn() {
  if (!editingDs.value) return
  const body: Record<string, unknown> = {
    host: connForm.host,
    port: connForm.port,
    database: connForm.database,
    username: connForm.username,
  }
  if (connForm.password) body.password = connForm.password
  const systemId = editingDs.value.systemId
  await ingestionApi.updateDataSource(editingDs.value.id, body)
  connDialog.value = false
  ElMessage.success('连接已保存')
  if (systemId) await ensureSourcesLoaded(systemId, true)
  emit('changed')
}

async function testDs(ds: DataSource) {
  try {
    const res = await ingestionApi.testDataSource(ds.id)
    ElMessage.success(String(res.data?.message || '连接成功'))
    if (ds.systemId) await ensureSourcesLoaded(ds.systemId, true)
    emit('changed')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '连接测试失败')
    if (ds.systemId) await ensureSourcesLoaded(ds.systemId, true)
    emit('changed')
  }
}

async function deleteDs(row: DataSource) {
  if (isManualUploadSource(row.sourceCode)) {
    ElMessage.warning('平台默认「手动上传」数据源不可删除')
    return
  }
  await ElMessageBox.confirm(`确定删除数据源「${row.sourceName}」？`, '删除确认', { type: 'warning' })
  const systemId = row.systemId
  await ingestionApi.deleteDataSource(row.id)
  ElMessage.success('数据源已删除')
  if (systemId) await ensureSourcesLoaded(systemId, true)
  await reloadSystems()
  emit('changed')
}

watch(() => props.project.id, () => {
  invalidateSources()
  void reloadSystems()
})

onMounted(reloadSystems)
</script>

<template>
  <div v-loading="loading">
    <el-alert
      v-if="loadError"
      type="error"
      :title="loadError"
      :closable="false"
      style="margin-bottom:12px"
    />

    <PageCard :title="title">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item class="portal-form-actions">
          <el-button @click="emit('back')">返回项目列表</el-button>
        </el-form-item>
      </el-form>

      <div class="section-head">
        <h4 class="section-title">业务系统</h4>
        <el-button type="primary" size="small" @click="openAddSystem">新增系统</el-button>
      </div>
      <p class="hint">
        展开系统行可查看其下全部数据源；归属项目：{{ projectPathLabel }}。
        <template v-if="isOtherProject(project.projectCode)">
          「其他」为系统初始化项目，可新增业务系统或另建项目。
        </template>
      </p>

      <el-table
        v-if="systems.length"
        :data="systems"
        stripe
        size="small"
        row-key="id"
        @expand-change="onExpandChange"
      >
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-panel" v-loading="expandLoading[row.id]">
              <div class="expand-head">
                <span class="expand-path">{{ projectPathLabel }} / {{ row.systemName }}</span>
                <el-button type="primary" link size="small" @click="openAddDs(row.id)">新增数据源</el-button>
              </div>
              <el-table
                v-if="sourcesOf(row.id).length"
                :data="sourcesOf(row.id)"
                size="small"
                border
                class="expand-table"
              >
                <el-table-column prop="sourceName" label="数据源" min-width="140" show-overflow-tooltip />
                <el-table-column label="类型" width="90">
                  <template #default="{ row: ds }">{{ $statusLabel(ds.sourceType) }}</template>
                </el-table-column>
                <el-table-column label="连接状态" width="100">
                  <template #default="{ row: ds }">
                    {{ isDbType(ds.sourceType) ? connLabel(ds.connStatus) : '—' }}
                  </template>
                </el-table-column>
                <el-table-column label="表数" width="70">
                  <template #default="{ row: ds }">{{ ds.tableCount ?? '—' }}</template>
                </el-table-column>
                <el-table-column label="操作" min-width="260">
                  <template #default="{ row: ds }">
                    <el-button link type="primary" @click="openEditMeta(ds)">改名称</el-button>
                    <template v-if="isDbType(ds.sourceType)">
                      <el-button link @click="openConn(ds)">配置连接</el-button>
                      <el-button link type="primary" @click="testDs(ds)">测试</el-button>
                    </template>
                    <span v-else class="muted">文件源</span>
                    <el-button
                      link
                      type="danger"
                      :disabled="isManualUploadSource(ds.sourceCode)"
                      @click="deleteDs(ds)"
                    >删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty
                v-else-if="!expandLoading[row.id]"
                description="该系统下暂无数据源"
                :image-size="40"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="systemName" label="系统名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="sys-name">{{ row.systemName }}</span>
            <span class="sys-path-hint">（项目：{{ projectPathLabel }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="数据源数" width="100">
          <template #default="{ row }">{{ row.dataSourceCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="220">
          <template #default="{ row }">
            <el-button link type="primary" @click="openAddDs(row.id)">新增数据源</el-button>
            <el-button
              link
              :disabled="isOtherSystem(row.systemCode)"
              @click="openRenameSystem(row)"
            >改名称</el-button>
            <el-button
              link
              type="danger"
              :disabled="isOtherSystem(row.systemCode)"
              @click="deleteSystem(row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无业务系统，请点击「新增系统」" :image-size="48" />
    </PageCard>

    <el-dialog v-model="addSystemDialog" title="新增系统" width="420px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="系统名称" required>
          <el-input v-model="addSystemName" placeholder="业务系统名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addSystemDialog = false">取消</el-button>
        <el-button type="primary" :loading="addSystemSaving" @click="submitAddSystem">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="renameSystemDialog" title="修改系统名称" width="420px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="系统名称" required>
          <el-input v-model="renameSystemName" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renameSystemDialog = false">取消</el-button>
        <el-button type="primary" :loading="renameSystemSaving" @click="submitRenameSystem">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="addDsDialog" title="新增数据源" width="520px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="数据源名" required>
          <el-input v-model="addDsForm.sourceName" placeholder="如：业务库 / 手动上传-部门A" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="addDsForm.sourceType" style="width:100%">
            <el-option label="MySQL" value="MYSQL" />
            <el-option label="Oracle" value="ORACLE" />
            <el-option label="文件(手动上传)" value="FILE" />
            <el-option label="API" value="API" />
          </el-select>
        </el-form-item>
        <template v-if="isDbType(addDsForm.sourceType)">
          <el-form-item label="主机"><el-input v-model="addDsForm.host" /></el-form-item>
          <el-form-item label="端口"><el-input-number v-model="addDsForm.port" :min="1" :max="65535" style="width:100%" /></el-form-item>
          <el-form-item label="库名" required><el-input v-model="addDsForm.database" /></el-form-item>
          <el-form-item label="用户名"><el-input v-model="addDsForm.username" /></el-form-item>
          <el-form-item label="密码"><el-input v-model="addDsForm.password" type="password" show-password /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="addDsDialog = false">取消</el-button>
        <el-button type="primary" :loading="addDsSaving" @click="submitAddDs">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editMetaDialog" title="修改数据源名称" width="420px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="数据源名" required>
          <el-input v-model="editMetaName" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editMetaDialog = false">取消</el-button>
        <el-button type="primary" :loading="editMetaSaving" @click="submitEditMeta">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="connDialog" :title="`连接配置 · ${editingDs?.sourceName || ''}`" width="480px">
      <el-form label-width="80px">
        <el-form-item label="主机"><el-input v-model="connForm.host" /></el-form-item>
        <el-form-item label="端口"><el-input-number v-model="connForm.port" :min="1" :max="65535" /></el-form-item>
        <el-form-item label="库名"><el-input v-model="connForm.database" /></el-form-item>
        <el-form-item label="用户名"><el-input v-model="connForm.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="connForm.password" type="password" show-password placeholder="留空则保留原密码" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="connDialog = false">取消</el-button>
        <el-button type="primary" @click="saveConn">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.muted { font-size: 13px; color: #909399; }
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}
.section-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.hint {
  margin: 0 0 10px;
  font-size: 12px;
  color: #909399;
}
.sys-name { font-weight: 500; }
.sys-path-hint {
  margin-left: 6px;
  font-size: 12px;
  color: #909399;
  font-weight: 400;
}
.expand-panel {
  padding: 8px 12px 12px 48px;
}
.expand-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.expand-path {
  font-size: 12px;
  color: #606266;
}
.expand-table {
  width: 100%;
}
</style>
