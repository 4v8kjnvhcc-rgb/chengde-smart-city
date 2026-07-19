<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import PageCard from '@/components/common/PageCard.vue'
import {
  setActiveProjectId,
  syncActiveProject,
} from '../ingestion-project-scope'
import { ingestionApi, useIngestionLoading, type DataSource, type Project } from '../useIngestionHub'
import { ingestionRegisterCache } from '../ingestion-register-cache'
import ProjectSystemDetailView from './ProjectSystemDetailView.vue'

const auth = useAuthStore()
const { loading, loadError, withLoad } = useIngestionLoading()
const projects = ref<Project[]>([])
const allDataSources = ref<DataSource[]>([])
const overview = ref<Record<string, unknown> | null>(null)
const selectedIds = ref<number[]>([])

const detailProjectId = ref<number | null>(null)

const projectDialog = ref(false)
const projectDialogMode = ref<'create' | 'edit'>('create')
const projectSaving = ref(false)
const editingDataSourceId = ref<number | null>(null)
const editingProjectId = ref<number | null>(null)
const projectForm = reactive({
  projectName: '',
  systemName: '',
  sourceName: '',
  sourceType: 'MYSQL',
  host: '127.0.0.1',
  port: 3306,
  database: '',
  username: '',
  password: '',
})

const currentDeptName = computed(() => auth.user?.orgName || '（未绑定部门）')

function isOtherProject(code?: string) {
  return !!code && (code === 'PRJ_OTHER' || code.startsWith('PRJ_OTHER_'))
}
const currentAccountLabel = computed(() => {
  const u = auth.user
  if (!u) return '—'
  return u.displayName ? `${u.displayName}（${u.username}）` : u.username
})

const projectDialogTitle = computed(() =>
  projectDialogMode.value === 'edit' ? '编辑项目 / 系统' : '登记项目 / 系统',
)

const detailProject = computed(() =>
  projects.value.find((p) => p.id === detailProjectId.value) || null,
)

const dsByProject = computed(() => {
  const map = new Map<number, DataSource[]>()
  for (const ds of allDataSources.value) {
    const list = map.get(ds.projectId) || []
    list.push(ds)
    map.set(ds.projectId, list)
  }
  return map
})

const canDeleteProject = computed(() => auth.hasPermission('exchange:project:delete'))
const canCreateProject = computed(() => auth.hasPermission('exchange:project:create') || auth.permissions.length === 0)

const selectedProject = computed(() => {
  if (selectedIds.value.length !== 1) return null
  return projects.value.find((p) => p.id === selectedIds.value[0]) || null
})

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

function primaryDs(projectId: number): DataSource | undefined {
  return dsByProject.value.get(projectId)?.[0]
}

function dsSummary(projectId: number) {
  const list = dsByProject.value.get(projectId) || []
  if (!list.length) return { name: '—', type: '—', conn: '—', tables: '—' }
  const first = list[0]
  const name = list.length > 1
    ? `${first.sourceName || first.sourceCode} 等${list.length}个`
    : (first.sourceName || first.sourceCode || '—')
  return {
    name,
    type: first.sourceType || '—',
    conn: isDbType(first.sourceType) ? connLabel(first.connStatus) : '—',
    tables: isDbType(first.sourceType) ? String(first.tableCount ?? 0) : '—',
  }
}

function resetProjectForm() {
  projectForm.projectName = ''
  projectForm.systemName = ''
  projectForm.sourceName = ''
  projectForm.sourceType = 'MYSQL'
  projectForm.host = '127.0.0.1'
  projectForm.port = 3306
  projectForm.database = ''
  projectForm.username = ''
  projectForm.password = ''
  editingDataSourceId.value = null
  editingProjectId.value = null
}

async function reload() {
  await withLoad(async () => {
    const [p, o, ds] = await Promise.all([
      ingestionApi.projects(),
      ingestionApi.registerOverview(),
      ingestionApi.dataSources(),
    ])
    projects.value = p.data || []
    overview.value = o.data
    allDataSources.value = ds.data || []
    syncActiveProject(projects.value)
    ingestionRegisterCache.invalidate('dataSources', 'tables')
    if (detailProjectId.value && !projects.value.some((x) => x.id === detailProjectId.value)) {
      detailProjectId.value = null
    }
  })
}

function openCreateProject() {
  projectDialogMode.value = 'create'
  resetProjectForm()
  projectDialog.value = true
}

function openEditProject(row?: Project | null) {
  const target = row || selectedProject.value
  if (!target) {
    ElMessage.warning('请先选中要编辑的项目')
    return
  }
  // 「其他」为系统初始化项目：名称不可改，进入详情新增/维护系统
  if (isOtherProject(target.projectCode)) {
    ElMessage.info('「其他」为系统初始化项目，项目名称不可修改；请在详情中新增系统，或新建项目挂接不同的手动上传资产')
    openDetail(target)
    return
  }
  projectDialogMode.value = 'edit'
  resetProjectForm()
  editingProjectId.value = target.id
  projectForm.projectName = target.projectName || ''
  projectForm.systemName = target.systemName || ''
  const ds = primaryDs(target.id)
  if (ds) {
    editingDataSourceId.value = ds.id
    projectForm.sourceName = ds.sourceName || ''
    projectForm.sourceType = ds.sourceType || 'MYSQL'
    try {
      const cfg = JSON.parse(ds.connConfigJson || '{}') as Record<string, unknown>
      projectForm.host = String(cfg.host || '127.0.0.1')
      projectForm.port = Number(cfg.port || 3306)
      projectForm.database = String(cfg.database || '')
      projectForm.username = String(cfg.username || '')
    } catch { /* ignore */ }
  }
  projectForm.password = ''
  projectDialog.value = true
}

function validateProjectForm() {
  if (!projectForm.projectName.trim()) {
    ElMessage.warning('请填写项目名称')
    return false
  }
  if (!projectForm.systemName.trim()) {
    ElMessage.warning('请填写首个业务系统名称（同一项目后续还可继续添加）')
    return false
  }
  if (!projectForm.sourceName.trim()) {
    ElMessage.warning('请填写数据源名称')
    return false
  }
  if (isDbType(projectForm.sourceType) && !projectForm.database.trim()) {
    ElMessage.warning('请填写库名')
    return false
  }
  if (projectDialogMode.value === 'create' && !auth.user?.orgId) {
    ElMessage.warning('当前账号未绑定部门，请先在系统管理中维护用户所属组织')
    return false
  }
  return true
}

async function submitProjectDialog() {
  if (!validateProjectForm()) return
  projectSaving.value = true
  try {
    if (projectDialogMode.value === 'create') {
      const projectId = (await ingestionApi.createProject({
        projectName: projectForm.projectName.trim(),
        systemName: projectForm.systemName.trim(),
      })).data
      await ingestionApi.createDataSource({
        projectId,
        sourceName: projectForm.sourceName.trim(),
        systemName: projectForm.systemName.trim(),
        sourceType: projectForm.sourceType,
        host: projectForm.host,
        port: projectForm.port,
        database: projectForm.database,
        username: projectForm.username,
        password: projectForm.password,
      })
      setActiveProjectId(Number(projectId))
      selectedIds.value = [Number(projectId)]
      ElMessage.success('项目已登记（可在详情中继续添加系统/数据源）')
    } else if (editingProjectId.value) {
      await ingestionApi.updateProject(editingProjectId.value, {
        projectName: projectForm.projectName.trim(),
        systemName: projectForm.systemName.trim(),
      })
      const dsBody: Record<string, unknown> = {
        sourceName: projectForm.sourceName.trim(),
        systemName: projectForm.systemName.trim(),
        sourceType: projectForm.sourceType,
        host: projectForm.host,
        port: projectForm.port,
        database: projectForm.database,
        username: projectForm.username,
      }
      if (projectForm.password) dsBody.password = projectForm.password
      if (editingDataSourceId.value) {
        await ingestionApi.updateDataSource(editingDataSourceId.value, dsBody)
      } else {
        await ingestionApi.createDataSource({
          projectId: editingProjectId.value,
          ...dsBody,
          password: projectForm.password,
        })
      }
      ElMessage.success('项目已更新')
    }
    projectDialog.value = false
    await reload()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    projectSaving.value = false
  }
}

async function deleteProject(row?: Project | null) {
  const target = row || selectedProject.value
  if (!target) {
    ElMessage.warning('请先选中要删除的项目')
    return
  }
  if (isOtherProject(target.projectCode)) {
    ElMessage.warning('「其他」为系统初始化项目，不可删除')
    return
  }
  await ElMessageBox.confirm(`确定删除项目「${target.projectName}」？关联数据源将一并删除。`, '删除确认', { type: 'warning' })
  await ingestionApi.deleteProject(target.id)
  ElMessage.success('项目已删除')
  if (detailProjectId.value === target.id) detailProjectId.value = null
  selectedIds.value = selectedIds.value.filter((id) => id !== target.id)
  await reload()
}

function openDetail(row: Project) {
  setActiveProjectId(row.id)
  selectedIds.value = [row.id]
  detailProjectId.value = row.id
}

function backToList() {
  detailProjectId.value = null
  void reload()
}

function onSelectionChange(rows: Project[]) {
  selectedIds.value = rows.map((r) => r.id)
  if (rows.length === 1) setActiveProjectId(rows[0].id)
}

onMounted(reload)
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

    <ProjectSystemDetailView
      v-if="detailProject"
      :project="detailProject"
      :dept-name="currentDeptName"
      @back="backToList"
      @changed="reload"
    />

    <template v-else>
      <el-alert
        type="info"
        :closable="false"
        style="margin-bottom:12px"
        title="「其他」为系统初始化项目（名称不可改），可在详情中新增系统；也可新建项目，用 FILE 数据源挂接不同的手动上传资产。同一项目可登记多个业务系统（每个系统对应一个数据源）。"
      />

      <PageCard title="项目 / 系统">
        <el-descriptions v-if="overview" :column="4" border size="small" style="margin-bottom:12px">
          <el-descriptions-item label="项目数">{{ overview.projects }}</el-descriptions-item>
          <el-descriptions-item label="数据源">{{ overview.dataSources }}</el-descriptions-item>
          <el-descriptions-item label="字典(全平台)">{{ overview.dicts }}</el-descriptions-item>
          <el-descriptions-item label="登记表">{{ overview.assets }}</el-descriptions-item>
        </el-descriptions>

        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item class="portal-form-actions">
            <div class="project-actions">
              <el-button v-if="canCreateProject" type="primary" @click="openCreateProject">登记项目</el-button>
              <el-button
                v-if="canCreateProject"
                :disabled="!selectedProject"
                @click="openEditProject()"
              >
                {{ isOtherProject(selectedProject?.projectCode) ? '维护系统' : '编辑项目' }}
              </el-button>
              <el-button
                v-if="canDeleteProject"
                type="danger"
                plain
                :disabled="!selectedProject || isOtherProject(selectedProject?.projectCode)"
                @click="deleteProject()"
              >
                删除项目
              </el-button>
            </div>
          </el-form-item>
        </el-form>

        <el-table
          v-if="projects.length"
          :data="projects"
          stripe
          size="small"
          row-key="id"
          @selection-change="onSelectionChange"
        >
          <el-table-column type="selection" width="46" />
          <el-table-column prop="projectName" label="项目名称" min-width="130" show-overflow-tooltip />
          <el-table-column prop="systemName" label="系统名称" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.systemName || '—' }}</template>
          </el-table-column>
          <el-table-column label="部门" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.boundOrgName || currentDeptName }}</template>
          </el-table-column>
          <el-table-column label="数据源" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ dsSummary(row.id).name }}</template>
          </el-table-column>
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ dsSummary(row.id).type }}</template>
          </el-table-column>
          <el-table-column label="连接" width="90">
            <template #default="{ row }">{{ dsSummary(row.id).conn }}</template>
          </el-table-column>
          <el-table-column label="表数" width="70">
            <template #default="{ row }">{{ dsSummary(row.id).tables }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无项目，请点击「登记项目」" :image-size="64" />
      </PageCard>
    </template>

    <el-dialog v-model="projectDialog" :title="projectDialogTitle" width="560px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="登记账号"><el-input :model-value="currentAccountLabel" disabled /></el-form-item>
        <el-form-item label="部门"><el-input :model-value="currentDeptName" disabled /></el-form-item>
        <el-form-item label="项目名称" required>
          <el-input v-model="projectForm.projectName" placeholder="如：公安人口库归集" />
        </el-form-item>
        <el-form-item label="系统名称" required>
          <el-input v-model="projectForm.systemName" placeholder="首个业务系统名，同一项目可再添加多个" />
        </el-form-item>
        <el-divider content-position="left">首个数据源</el-divider>
        <el-form-item label="数据源名" required>
          <el-input v-model="projectForm.sourceName" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="projectForm.sourceType" style="width:100%">
            <el-option label="MySQL" value="MYSQL" />
            <el-option label="Oracle" value="ORACLE" />
            <el-option label="文件(手动上传资产)" value="FILE" />
            <el-option label="API" value="API" />
          </el-select>
        </el-form-item>
        <p v-if="projectForm.sourceType === 'FILE'" class="form-hint">
          选择「文件」类型后，可在手动上传模板中选择本项目/系统，用于归集不同的上传数据资产。
        </p>
        <template v-if="isDbType(projectForm.sourceType)">
          <el-form-item label="主机"><el-input v-model="projectForm.host" /></el-form-item>
          <el-form-item label="端口"><el-input-number v-model="projectForm.port" :min="1" :max="65535" style="width:100%" /></el-form-item>
          <el-form-item label="库名" required><el-input v-model="projectForm.database" /></el-form-item>
          <el-form-item label="用户名"><el-input v-model="projectForm.username" /></el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="projectForm.password"
              type="password"
              show-password
              :placeholder="projectDialogMode === 'edit' ? '留空则保留原密码' : ''"
            />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="projectDialog = false">取消</el-button>
        <el-button type="primary" :loading="projectSaving" @click="submitProjectDialog">
          {{ projectDialogMode === 'edit' ? '保存' : '确定登记' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.project-actions {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.project-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.form-hint {
  margin: -4px 0 12px 100px;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}
</style>
