<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
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
  canEditRegister,
  canSubmitRegister,
  registerStatusZh,
  submitRegister,
  useRegisterWorkflowRole,
} from './register-workflow'

function isApprovedRegister(status?: string | null) {
  const s = String(status || '').toUpperCase()
  return s === 'APPROVED' || s === 'ARCHIVED'
}

const auth = useAuthStore()
const { loading, loadError, withLoad } = useIngestionLoading()
const projects = ref<Project[]>([])
const selectedIds = ref<number[]>([])
const orgs = ref<Array<{ id: number; orgName: string }>>([])
const clusterOptions = ref<Array<{ id: number; clusterCode: string; clusterName: string; accountName: string }>>([])

interface GrantUser {
  id: number
  username: string
  displayName: string
  orgId?: number
}

interface ProjectGrantRow {
  id: number
  projectId: number
  projectName?: string
  granteeId?: number
  granteeName?: string
  perm?: string
}

const detailProjectId = ref<number | null>(null)

const projectDialog = ref(false)
const projectDialogMode = ref<'create' | 'edit'>('create')
const projectSaving = ref(false)
const editingProjectId = ref<number | null>(null)
const projectForm = reactive({
  boundOrgId: undefined as number | undefined,
  clusterAccountId: undefined as number | undefined,
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

/** 有登记菜单即可操作页内全部按钮（角色不再拆按钮权限） */
const canDeleteProject = computed(() => true)
const canCreateProject = computed(() => true)
const { canSubmit } = useRegisterWorkflowRole()

const projectGrantVisible = ref(false)
const projectGrantSaving = ref(false)
const grantUsers = ref<GrantUser[]>([])
const projectGrants = ref<ProjectGrantRow[]>([])
const projectGrantForm = reactive({
  projectId: 0,
  projectName: '',
  userId: undefined as number | undefined,
})

const selectedProject = computed(() => {
  if (selectedIds.value.length !== 1) return null
  return projects.value.find((p) => p.id === selectedIds.value[0]) || null
})

function resetProjectForm() {
  projectForm.boundOrgId = auth.user?.orgId
  projectForm.clusterAccountId = undefined
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

async function ensureClustersLoaded() {
  try {
    clusterOptions.value = (await ingestionApi.clusterAccountOptions()).data || []
  } catch {
    clusterOptions.value = []
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
  await Promise.all([ensureOrgsLoaded(), ensureClustersLoaded()])
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
  projectForm.clusterAccountId = target.clusterAccountId ?? undefined
  projectForm.projectName = target.projectName || ''
  await Promise.all([ensureOrgsLoaded(), ensureClustersLoaded()])
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
        clusterAccountId: projectForm.clusterAccountId ?? null,
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
        clusterAccountId: projectForm.clusterAccountId ?? null,
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

async function deleteOneProject(row: Project) {
  if (isOtherProject(row.projectCode)) {
    ElMessage.warning('「其他」为系统初始化项目，不可删除')
    return
  }
  if (isApprovedRegister(row.registerStatus) && !auth.isSystemAdmin) {
    ElMessage.warning('审核通过的项目仅超级管理员可删除')
    return
  }
  try {
    const list = (await ingestionApi.systems(row.id)).data || []
    if (list.length > 0) {
      await ElMessageBox.alert(
        `该项目下已关联 ${list.length} 个系统，无法删除，请先删除系统后再删项目。`,
        '删除校验',
        { type: 'warning', confirmButtonText: '知道了' },
      )
      return
    }
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '校验系统关联失败')
    return
  }
  const tip = isApprovedRegister(row.registerStatus)
    ? `该项目已审核通过，确认以超级管理员身份删除「${row.projectName}」？`
    : `确定删除项目「${row.projectName}」？`
  await ElMessageBox.confirm(tip, '删除确认', { type: 'warning' })
  try {
    await ingestionApi.deleteProject(row.id)
    ElMessage.success('项目已删除')
    selectedIds.value = selectedIds.value.filter((id) => id !== row.id)
    await reload()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

function canShowProjectDelete(row: Project) {
  if (isOtherProject(row.projectCode)) return false
  if (isApprovedRegister(row.registerStatus)) return !!auth.isSystemAdmin
  return !!canDeleteProject.value
}

async function batchDeleteProjects() {
  const ids = selectedIds.value.slice()
  if (!ids.length) {
    ElMessage.warning('请先勾选要删除的项目')
    return
  }
  const targets = projects.value.filter((p) => ids.includes(p.id))
  const blockedOther = targets.filter((p) => isOtherProject(p.projectCode))
  let candidates = targets.filter((p) => !isOtherProject(p.projectCode))

  if (!auth.isSystemAdmin) {
    const approved = candidates.filter((p) => isApprovedRegister(p.registerStatus))
    if (approved.length) {
      await ElMessageBox.alert(
        `以下项目已审核通过，仅超级管理员可删除：${approved.map((p) => p.projectName).join('、')}`,
        '无法删除',
        { type: 'warning' },
      )
      candidates = candidates.filter((p) => !isApprovedRegister(p.registerStatus))
    }
  }

  if (!candidates.length) {
    ElMessage.warning(
      blockedOther.length ? '「其他」为系统初始化项目，不可删除' : '没有可删除的项目',
    )
    return
  }

  const withSystems: string[] = []
  const deletable: Project[] = []
  for (const p of candidates) {
    try {
      const list = (await ingestionApi.systems(p.id)).data || []
      if (list.length > 0) {
        withSystems.push(`${p.projectName}（${list.length} 个系统）`)
      } else {
        deletable.push(p)
      }
    } catch {
      deletable.push(p)
    }
  }

  if (withSystems.length) {
    await ElMessageBox.alert(
      `以下项目下已关联系统，无法删除，请先删除系统后再删项目：\n${withSystems.join('\n')}`,
      '删除校验',
      { type: 'warning', confirmButtonText: '知道了' },
    )
  }

  if (!deletable.length) {
    return
  }

  const names = deletable.map((p) => p.projectName).join('、')
  const tip =
    blockedOther.length > 0
      ? `将删除 ${deletable.length} 个项目（已排除「其他」及不可删项）：${names}`
      : `确定删除 ${deletable.length} 个项目：${names}？`
  try {
    await ElMessageBox.confirm(tip, '批量删除确认', { type: 'warning' })
  } catch {
    return
  }

  let ok = 0
  const errors: string[] = []
  for (const p of deletable) {
    try {
      await ingestionApi.deleteProject(p.id)
      ok += 1
      if (detailProjectId.value === p.id) detailProjectId.value = null
    } catch (e: unknown) {
      errors.push(`${p.projectName}：${e instanceof Error ? e.message : '删除失败'}`)
    }
  }
  selectedIds.value = []
  if (ok) ElMessage.success(`已删除 ${ok} 个项目`)
  if (errors.length) ElMessage.error(errors.slice(0, 3).join('；'))
  await reload()
}

async function doSubmit(row: Project) {
  await submitRegister('PROJECT', row.id)
  ElMessage.success('已提交审核')
  await reload()
}

function openDetail(row: Project) {
  setActiveProjectId(row.id)
  selectedIds.value = [row.id]
  detailProjectId.value = row.id
}

async function loadGrantUsers() {
  try {
    const res = await api.get('/system/users', { params: { page: 1, size: 500 } })
    const records = (res.data?.records || res.data || []) as GrantUser[]
    grantUsers.value = records.filter((u) => u && u.id != null)
  } catch {
    grantUsers.value = []
  }
}

async function loadProjectGrants(projectId: number) {
  try {
    const res = await api.get('/system/access/project-grants', { params: { projectId } })
    const rows = (res.data || []) as Array<ProjectGrantRow & { granteeType?: string }>
    projectGrants.value = rows.filter((g) => {
      const t = String(g.granteeType || 'USER').toUpperCase()
      return t === 'USER'
    })
  } catch {
    projectGrants.value = []
  }
}

async function openProjectGrant(row: Project) {
  setActiveProjectId(row.id)
  selectedIds.value = [row.id]
  projectGrantForm.projectId = row.id
  projectGrantForm.projectName = row.projectName
  projectGrantForm.userId = undefined
  await Promise.all([loadGrantUsers(), loadProjectGrants(row.id)])
  projectGrantVisible.value = true
}

async function submitProjectGrant() {
  if (!projectGrantForm.userId) {
    ElMessage.warning('请选择用户')
    return
  }
  projectGrantSaving.value = true
  try {
    await api.post('/system/access/project-grants', {
      projectId: projectGrantForm.projectId,
      granteeType: 'USER',
      granteeId: projectGrantForm.userId,
      perm: 'VIEW',
    })
    ElMessage.success('项目授权成功')
    projectGrantForm.userId = undefined
    await loadProjectGrants(projectGrantForm.projectId)
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '项目授权失败')
  } finally {
    projectGrantSaving.value = false
  }
}

async function revokeProjectGrant(row: ProjectGrantRow) {
  try {
    await ElMessageBox.confirm(
      `确认取消用户「${row.granteeName || row.granteeId}」对本项目的授权？`,
      '取消授权',
      { type: 'warning' },
    )
    await api.delete(`/system/access/project-grants/${row.id}`)
    ElMessage.success('已取消授权')
    await loadProjectGrants(projectGrantForm.projectId)
  } catch (e: unknown) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e instanceof Error ? e.message : '取消失败')
    }
  }
}

function permLabel(perm?: string) {
  const p = String(perm || '').toUpperCase()
  if (p === 'ADMIN') return '管理'
  if (p === 'EDIT') return '编辑'
  if (p === 'VIEW' || p === 'ACCESS' || p === 'READ') return '查看'
  return perm || '—'
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
      :readonly="isApprovedRegister(detailProject.registerStatus)"
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
                v-if="canDeleteProject"
                type="danger"
                plain
                :disabled="!selectedIds.length"
                @click="batchDeleteProjects"
              >
                批量删除
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
          <el-table-column label="绑定集群" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.clusterAccountName || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small">{{ registerStatusZh(row.registerStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="340" fixed="right">
            <template #default="{ row }">
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
              <el-button link type="primary" @click="openProjectGrant(row)">项目权限</el-button>
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button
                v-if="canShowProjectDelete(row)"
                link
                type="danger"
                @click="deleteOneProject(row)"
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
        <el-form-item label="绑定集群账号">
          <el-select
            v-model="projectForm.clusterAccountId"
            clearable
            filterable
            placeholder="可选，一个项目仅绑定一个集群"
            style="width:100%"
          >
            <el-option
              v-for="c in clusterOptions"
              :key="c.id"
              :label="`${c.clusterName}（${c.clusterCode} / ${c.accountName}）`"
              :value="c.id"
            />
          </el-select>
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

    <el-dialog
      v-model="projectGrantVisible"
      title="项目授权"
      width="560px"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item label="项目">
          <el-input :model-value="projectGrantForm.projectName" disabled />
        </el-form-item>
        <el-form-item label="选择用户" required>
          <el-select
            v-model="projectGrantForm.userId"
            filterable
            clearable
            placeholder="请选择要授权的用户"
            style="width: 100%"
          >
            <el-option
              v-for="u in grantUsers"
              :key="u.id"
              :label="`${u.displayName || '-'}（${u.username}）`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <h4 class="grant-subtitle">已授权用户</h4>
      <el-table :data="projectGrants" size="small" stripe max-height="240">
        <el-table-column label="用户" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.granteeName || row.granteeId }}</template>
        </el-table-column>
        <el-table-column label="权限" width="90">
          <template #default="{ row }">{{ permLabel(row.perm) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="danger" @click="revokeProjectGrant(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!projectGrants.length" description="暂无用户授权" :image-size="40" />

      <template #footer>
        <el-button @click="projectGrantVisible = false">关闭</el-button>
        <el-button type="primary" :loading="projectGrantSaving" @click="submitProjectGrant">确认</el-button>
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
.grant-subtitle {
  margin: 8px 0 10px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
</style>
