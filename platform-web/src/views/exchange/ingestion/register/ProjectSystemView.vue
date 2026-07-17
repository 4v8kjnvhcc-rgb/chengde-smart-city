<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import PageCard from '@/components/common/PageCard.vue'
import { ingestionApi, useIngestionLoading, type DataSource, type Project, type ProbeTable } from '../useIngestionHub'

const auth = useAuthStore()
const { loading, loadError, withLoad } = useIngestionLoading()
const projects = ref<Project[]>([])
const dataSources = ref<DataSource[]>([])
const overview = ref<Record<string, unknown> | null>(null)
const projectForm = reactive({ projectName: '', systemName: '业务系统', boundOrgId: undefined as number | undefined })
const dsForm = reactive({
  projectId: undefined as number | undefined,
  sourceName: '',
  sourceType: 'MYSQL',
  host: '127.0.0.1',
  port: 3306,
  database: '',
  username: '',
  password: '',
})
const connDialog = ref(false)
const editingDs = ref<DataSource | null>(null)

const probeDialog = ref(false)
const probeSource = ref<DataSource | null>(null)
const probeTables = ref<ProbeTable[]>([])
const probeSchema = ref('')
const selectedTables = ref<string[]>([])
const probing = ref(false)
const registering = ref(false)

const canDeleteProject = computed(() => auth.hasPermission('exchange:project:delete'))

function isDbType(type: string) {
  return type === 'MYSQL' || type === 'ORACLE'
}

async function reload() {
  await withLoad(async () => {
    const [p, ds, o] = await Promise.all([ingestionApi.projects(), ingestionApi.dataSources(), ingestionApi.registerOverview()])
    projects.value = p.data
    dataSources.value = ds.data
    overview.value = o.data
    if (projects.value.length && !dsForm.projectId) dsForm.projectId = projects.value[0].id
  })
}

async function createProject() {
  if (!projectForm.projectName) return
  await ingestionApi.createProject({ ...projectForm })
  projectForm.projectName = ''
  await reload()
}

async function deleteProject(row: Project) {
  await ElMessageBox.confirm(`确定删除项目「${row.projectName}」？关联数据源将一并删除。`, '删除确认', { type: 'warning' })
  await ingestionApi.deleteProject(row.id)
  ElMessage.success('项目已删除')
  await reload()
}

async function createDs() {
  if (!dsForm.sourceName || !dsForm.projectId) return
  await ingestionApi.createDataSource({ ...dsForm })
  dsForm.sourceName = ''
  await reload()
}

function openConn(ds: DataSource) {
  editingDs.value = ds
  dsForm.host = '127.0.0.1'
  dsForm.port = 3306
  dsForm.database = ''
  dsForm.username = ''
  dsForm.password = ''
  if (ds.connConfigJson) {
    try {
      const cfg = JSON.parse(ds.connConfigJson)
      dsForm.host = cfg.host || dsForm.host
      dsForm.port = cfg.port || dsForm.port
      dsForm.database = cfg.database || ''
      dsForm.username = cfg.username || ''
      dsForm.password = cfg.password || ''
    } catch { /* ignore */ }
  }
  connDialog.value = true
}

async function saveConn() {
  if (!editingDs.value) return
  await ingestionApi.updateDataSource(editingDs.value.id, { ...dsForm })
  connDialog.value = false
  ElMessage.success('连接配置已保存，请点击「测试」验证')
  await reload()
}

async function testDs(id: number) {
  const res = await ingestionApi.testDataSource(id)
  ElMessage.success(String(res.data.message || '连接测试成功'))
  await reload()
}

async function openProbe(ds: DataSource) {
  probeSource.value = ds
  probeTables.value = []
  selectedTables.value = []
  probeSchema.value = ''
  probeDialog.value = true
  probing.value = true
  try {
    const res = await ingestionApi.probeDataSource(ds.id)
    probeSchema.value = res.data.schema
    probeTables.value = res.data.tables
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '探库失败')
  } finally {
    probing.value = false
  }
}

async function registerSelected() {
  if (!probeSource.value || !selectedTables.value.length) {
    ElMessage.warning('请至少勾选一张源表')
    return
  }
  registering.value = true
  try {
    const tables = selectedTables.value.map((name) => ({ sourceTable: name }))
    const res = await ingestionApi.registerTables(probeSource.value.id, { tables })
    const registered = (res.data.registered as unknown[]) || []
    ElMessage.success(`已登记 ${registered.length} 张源表（待汇聚）`)
    probeDialog.value = false
    await reload()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '登记失败')
  } finally {
    registering.value = false
  }
}

onMounted(async () => {
  if (!auth.permissions.length) await auth.fetchProfile()
  await reload()
})
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <el-alert type="info" :closable="false" style="margin-bottom:12px"
      title="数据库类数据源需配置连接并测试；文件/API 类无需连接测试，不展示连接状态与表数。删除项目权限由系统管理分配（仅管理员）。" />
    <PageCard title="项目/系统信息登记">
      <el-descriptions v-if="overview" :column="4" border size="small" style="margin-bottom:12px">
        <el-descriptions-item label="项目数">{{ overview.projects }}</el-descriptions-item>
        <el-descriptions-item label="数据源">{{ overview.dataSources }}</el-descriptions-item>
        <el-descriptions-item label="字典">{{ overview.dicts }}</el-descriptions-item>
        <el-descriptions-item label="资产">{{ overview.assets }}</el-descriptions-item>
      </el-descriptions>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="项目名称" class="portal-field-lg"><el-input v-model="projectForm.projectName" placeholder="新建项目" /></el-form-item>
        <el-form-item label="业务系统" class="portal-field-md"><el-input v-model="projectForm.systemName" /></el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="createProject">登记项目</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="projects" stripe size="small">
        <el-table-column prop="projectCode" label="编码" width="140" />
        <el-table-column prop="projectName" label="项目" min-width="140" />
        <el-table-column prop="systemName" label="系统" width="120" />
        <el-table-column prop="boundOrgId" label="机构ID" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column v-if="canDeleteProject" label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="danger" @click="deleteProject(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>
    <PageCard title="数据源绑定">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="项目" class="portal-field-md">
          <el-select v-model="dsForm.projectId">
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据源名" class="portal-field-md"><el-input v-model="dsForm.sourceName" /></el-form-item>
        <el-form-item label="类型" class="portal-field-sm">
          <el-select v-model="dsForm.sourceType">
            <el-option label="MySQL" value="MYSQL" />
            <el-option label="Oracle" value="ORACLE" />
            <el-option label="文件" value="FILE" />
            <el-option label="API" value="API" />
          </el-select>
        </el-form-item>
        <template v-if="isDbType(dsForm.sourceType)">
          <el-form-item label="主机" class="portal-field-sm"><el-input v-model="dsForm.host" /></el-form-item>
          <el-form-item label="端口"><el-input-number v-model="dsForm.port" :min="1" :max="65535" /></el-form-item>
          <el-form-item label="库名" class="portal-field-sm"><el-input v-model="dsForm.database" /></el-form-item>
          <el-form-item label="用户名" class="portal-field-xs"><el-input v-model="dsForm.username" /></el-form-item>
          <el-form-item label="密码" class="portal-field-sm"><el-input v-model="dsForm.password" type="password" show-password /></el-form-item>
        </template>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="createDs">登记数据源</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="dataSources" stripe size="small">
        <el-table-column prop="sourceName" label="数据源" />
        <el-table-column prop="sourceType" label="类型" width="90" />
        <el-table-column label="连接" width="90">
          <template #default="{ row }">{{ isDbType(row.sourceType) ? row.connStatus : '—' }}</template>
        </el-table-column>
        <el-table-column label="表数" width="70">
          <template #default="{ row }">{{ isDbType(row.sourceType) ? row.tableCount : '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <template v-if="isDbType(row.sourceType)">
              <el-button link @click="openConn(row)">配置连接</el-button>
              <el-button link type="primary" @click="testDs(row.id)">测试</el-button>
              <el-button link type="success" :disabled="row.connStatus !== 'OK'" @click="openProbe(row)">探库登记</el-button>
            </template>
            <span v-else class="muted">无需测试</span>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>
    <el-dialog v-model="probeDialog" :title="`探库登记 · ${probeSource?.sourceName || ''}`" width="720px">
      <el-alert type="info" :closable="false" style="margin-bottom:10px"
        :title="`源库 ${probeSchema || '-'} 真实探测到 ${probeTables.length} 张表；勾选后登记到平台（状态：待汇聚），再由汇聚流程真实抽取到 ODS。`" />
      <div v-loading="probing">
        <el-table :data="probeTables" size="small" max-height="360"
          @selection-change="(rows: ProbeTable[]) => selectedTables = rows.map(r => r.sourceTable)">
          <el-table-column type="selection" width="46" />
          <el-table-column prop="sourceTable" label="源表" min-width="160" />
          <el-table-column label="列数" width="70">
            <template #default="{ row }">{{ row.columns.length }}</template>
          </el-table-column>
          <el-table-column label="主键" min-width="120">
            <template #default="{ row }">{{ row.primaryKeys.join(', ') || '—' }}</template>
          </el-table-column>
          <el-table-column label="行数" width="90">
            <template #default="{ row }">{{ row.rowCount < 0 ? '—' : row.rowCount }}</template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="probeDialog = false">取消</el-button>
        <el-button type="primary" :loading="registering" @click="registerSelected">登记选中表</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="connDialog" title="数据源连接配置" width="480px">
      <el-form label-width="80px">
        <el-form-item label="主机"><el-input v-model="dsForm.host" /></el-form-item>
        <el-form-item label="端口"><el-input-number v-model="dsForm.port" :min="1" :max="65535" /></el-form-item>
        <el-form-item label="库名"><el-input v-model="dsForm.database" /></el-form-item>
        <el-form-item label="用户名"><el-input v-model="dsForm.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="dsForm.password" type="password" show-password /></el-form-item>
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
</style>
