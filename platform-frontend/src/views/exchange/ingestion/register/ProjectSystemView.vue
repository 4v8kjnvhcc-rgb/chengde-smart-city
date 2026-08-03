<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageCard from '@/components/common/PageCard.vue'
import {
  setActiveProjectId,
  syncActiveProject,
} from '../ingestion-project-scope'
import { ingestionApi, useIngestionLoading, type Project } from '../useIngestionHub'
import { ingestionRegisterCache } from '../ingestion-register-cache'
import ProjectSystemDetailView from './ProjectSystemDetailView.vue'
import {
  approveRegister,
  canAuditRegister,
  canEditRegister,
  canSubmitRegister,
  loadRegisterLogs,
  registerStatusZh,
  rejectRegister,
  submitRegister,
  useRegisterWorkflowRole,
} from './register-workflow'

const router = useRouter()
const auth = useAuthStore()
const { loading, loadError, withLoad } = useIngestionLoading()
const projects = ref<Project[]>([])
const selectedIds = ref<number[]>([])
const orgs = ref<Array<{ id: number; orgName: string }>>([])

const detailProjectId = ref<number | null>(null)

const projectDialog = ref(false)
const projectDialogMode = ref<'create' | 'edit'>('create')
const projectSaving = ref(false)
const editingProjectId = ref<number | null>(null)
const projectForm = reactive({
  boundOrgId: undefined as number | undefined,
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

function isDbType(type: string) {
  return type === 'MYSQL' || type === 'ORACLE'
}
const currentAccountLabel = computed(() => {
  const u = auth.user
  if (!u) return '—'
  return u.displayName ? `${u.displayName}（${u.username}）` : u.username
})

const projectDialogTitle = computed(() =>
  projectDialogMode.value === 'edit' ? '编辑项目' : '新建项目',
)

const detailProject = computed(() =>
  projects.value.find((p) => p.id === detailProjectId.value) || null,
)

const canDeleteProject = computed(() => auth.hasPermission('exchange:project:delete') || auth.isSystemAdmin)
const canCreateProject = computed(() => auth.hasPermission('exchange:project:create') || auth.permissions.length === 0 || auth.isSystemAdmin)
const { canSubmit, canAudit } = useRegisterWorkflowRole()

const viewDialog = ref(false)
const viewLogs = ref<Record<string, unknown>[]>([])
const viewRow = ref<Project | null>(null)
const auditRejectVisible = ref(false)
const auditRejectReason = ref('')
const auditTarget = ref<Project | null>(null)

const selectedProject = computed(() => {
  if (selectedIds.value.length !== 1) return null
  return projects.value.find((p) => p.id === selectedIds.value[0]) || null
})

function resetProjectForm() {
  projectForm.boundOrgId = auth.user?.orgId
  projectForm.projectName = ''
  projectForm.systemName = ''
  projectForm.sourceName = ''
  projectForm.sourceType = 'MYSQL'
  projectForm.host = '127.0.0.1'
  projectForm.port = 3306
  projectForm.database = ''
  projectForm.username = ''
  projectForm.password = ''
  editingProjectId.value = null
}

async function ensureOrgsLoaded() {
  if (!auth.isSystemAdmin || orgs.value.length) return
  try {
    orgs.value = (await api.get('/system/orgs')).data || []
  } catch {
    orgs.value = []
  }
}

async function reload() {
  await withLoad(async () => {
    const p = await ingestionApi.projects()
    projects.value = p.data || []
    syncActiveProject(projects.value)
    ingestionRegisterCache.invalidate('dataSources', 'tables')
    if (detailProjectId.value && !projects.value.some((x) => x.id === detailProjectId.value)) {
      detailProjectId.value = null
    }
    if (auth.isSystemAdmin) await ensureOrgsLoaded()
  })
}

async function openCreateProject() {
  projectDialogMode.value = 'create'
  resetProjectForm()
  await ensureOrgsLoaded()
  projectDialog.value = true
}

async function openEditProject(row?: Project | null) {
  const target = row || selectedProject.value
  if (!target) {
    ElMessage.warning('请先选中要编辑的项目')
    return
  }
  if (isOtherProject(target.projectCode)) {
    ElMessage.info('「其他」为系统初始化项目，项目名称不可修改；请在详情中维护系统与数据源')
    openDetail(target)
    return
  }
  projectDialogMode.value = 'edit'
  resetProjectForm()
  editingProjectId.value = target.id
  projectForm.boundOrgId = target.boundOrgId
  projectForm.projectName = target.projectName || ''
  await ensureOrgsLoaded()
  projectDialog.value = true
}

function validateProjectForm() {
  if (!projectForm.projectName.trim()) {
    ElMessage.warning('请填写项目名称')
    return false
  }
  if (projectDialogMode.value === 'create') {
    if (auth.isSystemAdmin) {
      if (!projectForm.boundOrgId) {
        ElMessage.warning('请选择项目部门归属')
        return false
      }
    } else if (!auth.user?.orgId) {
      ElMessage.warning('当前账号未绑定部门，请先在系统管理中维护用户所属组织')
      return false
    }
    if (!projectForm.systemName.trim()) {
      ElMessage.warning('请填写首个业务系统名称')
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
  }
  return true
}

async function submitProjectDialog() {
  if (!validateProjectForm()) return
  projectSaving.value = true
  try {
    const boundOrgPayload = auth.isSystemAdmin && projectForm.boundOrgId
      ? { boundOrgId: projectForm.boundOrgId }
      : {}
    if (projectDialogMode.value === 'create') {
      const projectId = Number((await ingestionApi.createProject({
        projectName: projectForm.projectName.trim(),
        systemName: projectForm.systemName.trim(),
        ...boundOrgPayload,
      })).data)
      const systemId = Number((await ingestionApi.createSystem({
        projectId,
        systemName: projectForm.systemName.trim(),
      })).data)
      await ingestionApi.createDataSource({
        projectId,
        systemId,
        sourceName: projectForm.sourceName.trim(),
        sourceType: projectForm.sourceType,
        host: projectForm.host,
        port: projectForm.port,
        database: projectForm.database,
        username: projectForm.username,
        password: projectForm.password,
      })
      setActiveProjectId(projectId)
      selectedIds.value = [projectId]
      ElMessage.success('项目、系统与首个数据源已创建')
      projectDialog.value = false
      await reload()
      detailProjectId.value = projectId
    } else if (editingProjectId.value) {
      await ingestionApi.updateProject(editingProjectId.value, {
        projectName: projectForm.projectName.trim(),
        ...boundOrgPayload,
      })
      ElMessage.success('项目已更新')
      projectDialog.value = false
      await reload()
    }
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
  await ElMessageBox.confirm(`确定删除项目「${target.projectName}」？若该项目下仍有系统将无法删除。`, '删除确认', { type: 'warning' })
  await ingestionApi.deleteProject(target.id)
  ElMessage.success('项目已删除')
  if (detailProjectId.value === target.id) detailProjectId.value = null
  selectedIds.value = selectedIds.value.filter((id) => id !== target.id)
  await reload()
}

async function openView(row: Project) {
  viewRow.value = row
  viewLogs.value = await loadRegisterLogs('PROJECT', row.id)
  viewDialog.value = true
}

async function doSubmit(row: Project) {
  await submitRegister('PROJECT', row.id)
  ElMessage.success('已提交审核')
  await reload()
}

async function doApprove(row: Project) {
  await approveRegister('PROJECT', row.id)
  ElMessage.success('审核通过')
  await reload()
}

function openReject(row: Project) {
  auditTarget.value = row
  auditRejectReason.value = ''
  auditRejectVisible.value = true
}

async function doReject() {
  if (!auditTarget.value || !auditRejectReason.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  await rejectRegister('PROJECT', auditTarget.value.id, auditRejectReason.value.trim())
  ElMessage.success('已驳回')
  auditRejectVisible.value = false
  await reload()
}

function openDetail(row: Project) {
  setActiveProjectId(row.id)
  selectedIds.value = [row.id]
  detailProjectId.value = row.id
}

/** 跳转访问控制 · 项目授权，并带上当前项目 */
function openUserGrant(row: Project) {
  setActiveProjectId(row.id)
  selectedIds.value = [row.id]
  void router.push({
    path: '/exchange/ingestion',
    query: {
      system: 'register',
      module: 'm048',
      accessTab: 'resource',
      projectId: String(row.id),
    },
  })
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
      @back="backToList"
      @changed="reload"
    />

    <template v-else>
      <el-alert
        type="info"
        :closable="false"
        style="margin-bottom:12px"
        title="一个项目可挂多个业务系统，一个系统可挂多个数据源。新建时可一并创建首个系统与数据源，之后在详情中继续添加。"
      />

      <PageCard title="项目">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item class="portal-form-actions">
            <div class="project-actions">
              <el-button v-if="canCreateProject" type="primary" @click="openCreateProject">新建项目</el-button>
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
          <el-table-column prop="projectName" label="项目名称" min-width="160" show-overflow-tooltip />
          <el-table-column label="部门" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.boundOrgName || currentDeptName }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small">{{ registerStatusZh(row.registerStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openView(row)">查看</el-button>
              <el-button
                v-if="canEditRegister(row.registerStatus) && canCreateProject"
                link
                type="primary"
                @click="openEditProject(row)"
              >
                编辑
              </el-button>
              <el-button
                v-if="canSubmit && canSubmitRegister(row.registerStatus)"
                link
                type="primary"
                @click="doSubmit(row)"
              >
                提交
              </el-button>
              <template v-if="canAudit && canAuditRegister(row.registerStatus)">
                <el-button link type="success" @click="doApprove(row)">审核</el-button>
                <el-button link type="warning" @click="openReject(row)">驳回</el-button>
              </template>
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button
                v-if="canDeleteProject && !isOtherProject(row.projectCode)"
                link
                type="danger"
                @click="deleteProject(row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无项目，请点击「新建项目」" :image-size="1" />
      </PageCard>
    </template>

    <el-dialog
      v-model="projectDialog"
      :title="projectDialogTitle"
      :width="projectDialogMode === 'create' ? '560px' : '480px'"
      destroy-on-close
    >
      <el-form label-width="100px">
        <el-form-item label="登记账号"><el-input :model-value="currentAccountLabel" disabled /></el-form-item>
        <el-form-item label="部门" required>
          <el-select
            v-if="auth.isSystemAdmin"
            v-model="projectForm.boundOrgId"
            filterable
            placeholder="选择部门归属（仅超级管理员可改）"
            style="width:100%"
          >
            <el-option v-for="o in orgs" :key="o.id" :label="o.orgName" :value="o.id" />
          </el-select>
          <el-input v-else :model-value="currentDeptName" disabled />
        </el-form-item>
        <el-form-item label="项目名称" required>
          <el-input v-model="projectForm.projectName" placeholder="如：公安人口库归集" />
        </el-form-item>
        <template v-if="projectDialogMode === 'create'">
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
              <el-input v-model="projectForm.password" type="password" show-password />
            </el-form-item>
          </template>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="projectDialog = false">取消</el-button>
        <el-button type="primary" :loading="projectSaving" @click="submitProjectDialog">
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="viewDialog" title="查看项目（基本信息与审核记录）" width="640px" destroy-on-close>
      <el-descriptions v-if="viewRow" :column="1" border size="small">
        <el-descriptions-item label="项目名称">{{ viewRow.projectName }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ registerStatusZh(viewRow.registerStatus) }}</el-descriptions-item>
        <el-descriptions-item v-if="viewRow.rejectReason" label="驳回原因">{{ viewRow.rejectReason }}</el-descriptions-item>
        <el-descriptions-item label="部门">{{ viewRow.boundOrgName || currentDeptName }}</el-descriptions-item>
      </el-descriptions>
      <h4 style="margin:16px 0 8px">提交 / 审核记录</h4>
      <el-table :data="viewLogs" size="small" stripe max-height="280">
        <el-table-column prop="action" label="动作" width="100" />
        <el-table-column label="状态变更" min-width="160">
          <template #default="{ row }">
            {{ registerStatusZh(row.fromStatus as string) }} → {{ registerStatusZh(row.toStatus as string) }}
          </template>
        </el-table-column>
        <el-table-column prop="commentText" label="说明" min-width="140" show-overflow-tooltip />
        <el-table-column prop="operatorName" label="操作人" width="100" />
        <el-table-column prop="createdAt" label="时间" width="170" />
      </el-table>
    </el-dialog>

    <el-dialog v-model="auditRejectVisible" title="驳回" width="420px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="驳回原因" required>
          <el-input v-model="auditRejectReason" type="textarea" :rows="3" placeholder="必填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditRejectVisible = false">取消</el-button>
        <el-button type="danger" @click="doReject">确认驳回</el-button>
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
