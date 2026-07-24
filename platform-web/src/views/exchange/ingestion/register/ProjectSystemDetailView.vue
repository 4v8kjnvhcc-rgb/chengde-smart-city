<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageCard from '@/components/common/PageCard.vue'
import { projectOptionLabel } from '../ingestion-project-scope'
import { ingestionApi, useIngestionLoading, type DataSource, type Project, type ProbeTable } from '../useIngestionHub'
import { ingestionRegisterCache } from '../ingestion-register-cache'

const props = defineProps<{
  project: Project
  deptName: string
}>()

const emit = defineEmits<{
  back: []
  changed: []
}>()

const auth = useAuthStore()
const { loading, loadError, withLoad } = useIngestionLoading()
const dataSources = ref<DataSource[]>([])
const orgs = ref<Array<{ id: number; orgName: string }>>([])
const editOrgId = ref<number | undefined>()
const orgSaving = ref(false)

const connDialog = ref(false)
const editingDs = ref<DataSource | null>(null)
const connForm = reactive({ host: '127.0.0.1', port: 3306, database: '', username: '', password: '' })

const addDialog = ref(false)
const addSaving = ref(false)
const addForm = reactive({
  systemName: '',
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
const editMetaForm = reactive({ systemName: '', sourceName: '' })
const editingMetaDs = ref<DataSource | null>(null)

const probeDialog = ref(false)
const probeSource = ref<DataSource | null>(null)
const probeTables = ref<ProbeTable[]>([])
const probeSchema = ref('')
const selectedTables = ref<string[]>([])
const probing = ref(false)
const registering = ref(false)

function isOtherProject(code?: string) {
  return !!code && (code === 'PRJ_OTHER' || code.startsWith('PRJ_OTHER_'))
}

function isManualUploadSource(code?: string) {
  return !!code && (code === 'DS_MANUAL_UPLOAD' || code.startsWith('DS_MANUAL_UPLOAD_'))
}

const canEditOrg = computed(() => auth.isSystemAdmin && !isOtherProject(props.project.projectCode))

const title = computed(() => `项目详情 · ${projectOptionLabel(props.project)}`)

async function ensureOrgsLoaded() {
  if (!auth.isSystemAdmin || orgs.value.length) return
  try {
    orgs.value = (await api.get('/system/orgs')).data || []
  } catch {
    orgs.value = []
  }
}

async function saveBoundOrg() {
  if (!canEditOrg.value) return
  if (!editOrgId.value) {
    ElMessage.warning('请选择部门')
    return
  }
  if (editOrgId.value === props.project.boundOrgId) {
    ElMessage.info('部门归属未变更')
    return
  }
  orgSaving.value = true
  try {
    await ingestionApi.updateProject(props.project.id, {
      projectName: props.project.projectName,
      systemName: props.project.systemName || props.project.projectName,
      boundOrgId: editOrgId.value,
    })
    ElMessage.success('部门归属已更新')
    emit('changed')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '更新失败')
  } finally {
    orgSaving.value = false
  }
}

watch(
  () => props.project.boundOrgId,
  (v) => {
    editOrgId.value = v
  },
  { immediate: true },
)
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

async function reloadDataSources() {
  await withLoad(async () => {
    const res = await ingestionApi.dataSources(props.project.id)
    dataSources.value = res.data || []
    ingestionRegisterCache.invalidate('dataSources', 'tables')
  })
}

async function deleteDs(row: DataSource) {
  if (isManualUploadSource(row.sourceCode)) {
    ElMessage.warning('平台默认「手动上传」数据源不可删除，可修改系统名称或新增系统')
    return
  }
  await ElMessageBox.confirm(
    `确定删除数据源「${row.sourceName || row.sourceCode}」？已登记的表结构将一并删除。`,
    '删除确认',
    { type: 'warning' },
  )
  await ingestionApi.deleteDataSource(row.id)
  ElMessage.success('数据源已删除')
  await reloadDataSources()
  emit('changed')
}

function openAddSystem() {
  addForm.systemName = ''
  addForm.sourceName = ''
  addForm.sourceType = 'MYSQL'
  addForm.host = '127.0.0.1'
  addForm.port = 3306
  addForm.database = ''
  addForm.username = ''
  addForm.password = ''
  addDialog.value = true
}

async function submitAddSystem() {
  if (!addForm.systemName.trim()) {
    ElMessage.warning('请填写系统名称')
    return
  }
  if (!addForm.sourceName.trim()) {
    ElMessage.warning('请填写数据源名称')
    return
  }
  if (isDbType(addForm.sourceType) && !addForm.database.trim()) {
    ElMessage.warning('请填写库名')
    return
  }
  addSaving.value = true
  try {
    await ingestionApi.createDataSource({
      projectId: props.project.id,
      systemName: addForm.systemName.trim(),
      sourceName: addForm.sourceName.trim(),
      sourceType: addForm.sourceType,
      host: addForm.host,
      port: addForm.port,
      database: addForm.database,
      username: addForm.username,
      password: addForm.password,
    })
    ElMessage.success('已添加系统/数据源')
    addDialog.value = false
    await reloadDataSources()
    emit('changed')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '添加失败')
  } finally {
    addSaving.value = false
  }
}

function openEditMeta(ds: DataSource) {
  editingMetaDs.value = ds
  editMetaForm.systemName = ds.systemName || ''
  editMetaForm.sourceName = ds.sourceName || ''
  editMetaDialog.value = true
}

async function submitEditMeta() {
  if (!editingMetaDs.value) return
  if (!editMetaForm.systemName.trim()) {
    ElMessage.warning('请填写系统名称')
    return
  }
  if (!editMetaForm.sourceName.trim()) {
    ElMessage.warning('请填写数据源名称')
    return
  }
  editMetaSaving.value = true
  try {
    await ingestionApi.updateDataSource(editingMetaDs.value.id, {
      systemName: editMetaForm.systemName.trim(),
      sourceName: editMetaForm.sourceName.trim(),
    })
    ElMessage.success('已更新')
    editMetaDialog.value = false
    await reloadDataSources()
    emit('changed')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '更新失败')
  } finally {
    editMetaSaving.value = false
  }
}

function openConn(ds: DataSource) {
  editingDs.value = ds
  let host = '127.0.0.1'
  let port = 3306
  let database = ''
  let username = ''
  try {
    const cfg = JSON.parse(ds.connConfigJson || '{}') as Record<string, unknown>
    host = String(cfg.host || host)
    port = Number(cfg.port || port)
    database = String(cfg.database || '')
    username = String(cfg.username || '')
  } catch { /* ignore */ }
  connForm.host = host
  connForm.port = port
  connForm.database = database
  connForm.username = username
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
  await ingestionApi.updateDataSource(editingDs.value.id, body)
  connDialog.value = false
  ElMessage.success('连接已保存')
  await reloadDataSources()
  emit('changed')
}

async function testDs(id: number) {
  try {
    const res = await ingestionApi.testDataSource(id)
    ElMessage.success(String(res.data?.message || '连接成功'))
    await reloadDataSources()
    emit('changed')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '连接测试失败')
    await reloadDataSources()
    emit('changed')
  }
}

async function openProbe(ds: DataSource) {
  probeSource.value = ds
  probeDialog.value = true
  probing.value = true
  selectedTables.value = []
  try {
    const res = await ingestionApi.probeDataSource(ds.id)
    probeSchema.value = String(res.data.schema || '')
    probeTables.value = res.data.tables || []
  } catch {
    probeTables.value = []
    ElMessage.error('探库失败')
  } finally {
    probing.value = false
  }
}

async function registerSelected() {
  if (!probeSource.value || !selectedTables.value.length) {
    ElMessage.warning('请勾选要登记的表')
    return
  }
  registering.value = true
  try {
    await ingestionApi.registerTables(probeSource.value.id, {
      tables: selectedTables.value.map((sourceTable) => ({ sourceTable })),
    })
    ElMessage.success('表已登记')
    probeDialog.value = false
    ingestionRegisterCache.invalidate('tables')
    await reloadDataSources()
    emit('changed')
  } finally {
    registering.value = false
  }
}

watch(() => props.project.id, () => {
  void reloadDataSources()
})

onMounted(async () => {
  await reloadDataSources()
  await ensureOrgsLoaded()
})
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
          <el-button @click="emit('back')">返回列表</el-button>
        </el-form-item>
      </el-form>

      <el-descriptions :column="3" border size="small" style="margin-bottom:16px">
        <el-descriptions-item label="项目名称">{{ project.projectName }}</el-descriptions-item>
        <el-descriptions-item label="默认系统">{{ project.systemName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="部门">
          <template v-if="canEditOrg">
            <div class="org-edit">
              <el-select v-model="editOrgId" filterable placeholder="选择部门" style="width:200px">
                <el-option v-for="o in orgs" :key="o.id" :label="o.orgName" :value="o.id" />
              </el-select>
              <el-button type="primary" link :loading="orgSaving" @click="saveBoundOrg">保存归属</el-button>
            </div>
          </template>
          <template v-else>{{ project.boundOrgName || deptName }}</template>
        </el-descriptions-item>
        <el-descriptions-item label="编码">{{ project.projectCode }}</el-descriptions-item>
        <el-descriptions-item label="登记账号">{{ project.createdBy || '—' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ $statusLabel(project.status) }}</el-descriptions-item>
      </el-descriptions>

      <div class="section-head">
        <h4 class="section-title">系统 / 数据源</h4>
        <el-button type="primary" size="small" @click="openAddSystem">新增系统</el-button>
      </div>
      <p class="hint">
        同一项目可挂多个业务系统（每个系统一个数据源）。
        <template v-if="isOtherProject(project.projectCode)">
          「其他」为系统初始化项目，名称不可改；可在此新增系统，或另建项目挂接不同的手动上传资产。
        </template>
        <template v-else>
          手动上传资产请选择 FILE 类型系统；也可使用默认「其他」项目。
        </template>
      </p>
      <el-table :data="dataSources" stripe size="small">
        <el-table-column label="系统名称" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.systemName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="sourceName" label="数据源" min-width="140" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ $statusLabel(row.sourceType) }}</template>
        </el-table-column>
        <el-table-column label="连接状态" width="100">
          <template #default="{ row }">
            {{ isDbType(row.sourceType) ? connLabel(row.connStatus) : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="表数" width="70">
          <template #default="{ row }">{{ row.tableCount ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="320">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditMeta(row)">改名称</el-button>
            <template v-if="isDbType(row.sourceType)">
              <el-button link @click="openConn(row)">配置连接</el-button>
              <el-button link type="primary" @click="testDs(row.id)">测试</el-button>
              <el-button link type="success" :disabled="row.connStatus !== 'OK'" @click="openProbe(row)">探库登记</el-button>
            </template>
            <span v-else class="muted">文件源</span>
            <el-button
              link
              type="danger"
              :disabled="isManualUploadSource(row.sourceCode)"
              @click="deleteDs(row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!dataSources.length" description="暂无数据源，请点击「新增系统」" :image-size="48" />
    </PageCard>

    <el-dialog v-model="addDialog" title="新增系统 / 数据源" width="520px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="系统名称" required>
          <el-input v-model="addForm.systemName" placeholder="业务系统名" />
        </el-form-item>
        <el-form-item label="数据源名" required>
          <el-input v-model="addForm.sourceName" placeholder="如：业务库 / 手动上传-部门A" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="addForm.sourceType" style="width:100%">
            <el-option label="MySQL" value="MYSQL" />
            <el-option label="Oracle" value="ORACLE" />
            <el-option label="文件(手动上传)" value="FILE" />
            <el-option label="API" value="API" />
          </el-select>
        </el-form-item>
        <template v-if="isDbType(addForm.sourceType)">
          <el-form-item label="主机"><el-input v-model="addForm.host" /></el-form-item>
          <el-form-item label="端口"><el-input-number v-model="addForm.port" :min="1" :max="65535" style="width:100%" /></el-form-item>
          <el-form-item label="库名" required><el-input v-model="addForm.database" /></el-form-item>
          <el-form-item label="用户名"><el-input v-model="addForm.username" /></el-form-item>
          <el-form-item label="密码"><el-input v-model="addForm.password" type="password" show-password /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="addDialog = false">取消</el-button>
        <el-button type="primary" :loading="addSaving" @click="submitAddSystem">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editMetaDialog" title="修改系统 / 数据源名称" width="420px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="系统名称" required>
          <el-input v-model="editMetaForm.systemName" />
        </el-form-item>
        <el-form-item label="数据源名" required>
          <el-input v-model="editMetaForm.sourceName" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editMetaDialog = false">取消</el-button>
        <el-button type="primary" :loading="editMetaSaving" @click="submitEditMeta">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="probeDialog" :title="`探库登记 · ${probeSource?.sourceName || ''}`" width="720px">
      <el-alert
        type="info"
        :closable="false"
        style="margin-bottom:10px"
        :title="`源库 ${probeSchema || '-'} 探测到 ${probeTables.length} 张表；勾选后登记到当前项目。`"
      />
      <div v-loading="probing">
        <el-table
          :data="probeTables"
          size="small"
          max-height="360"
          @selection-change="(rows: ProbeTable[]) => (selectedTables = rows.map((r) => r.sourceTable))"
        >
          <el-table-column type="selection" width="46" />
          <el-table-column prop="sourceTable" label="源表" min-width="160" />
          <el-table-column label="列数" width="70">
            <template #default="{ row }">{{ row.columns.length }}</template>
          </el-table-column>
          <el-table-column label="主键" min-width="120">
            <template #default="{ row }">{{ row.primaryKeys.join(', ') || '—' }}</template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="probeDialog = false">取消</el-button>
        <el-button type="primary" :loading="registering" @click="registerSelected">登记选中表</el-button>
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
.org-edit {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
