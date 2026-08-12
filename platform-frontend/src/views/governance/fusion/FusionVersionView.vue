<script setup lang="ts">
/**
 * 数据融合处理 · 版本管理
 * 标书：脚本/工作流版本、锁定、DEV/PROD 隔离、回滚、一键发布到生产调度。
 * 不替代「脚本开发」页既有编辑/执行能力。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { statusLabel, statusTagType } from '@/utils/status-label'

interface ScriptRow {
  id: number
  scriptCode: string
  scriptName: string
  scriptType: string
  publishStatus: string
  versionNo: number
  envScope?: string
  lockedBy?: string
  prodDeployedVersion?: number
  lastMessage?: string
}

interface WorkflowRow {
  objectType: 'TASK' | 'PIPELINE'
  objectTypeLabel?: string
  id: number
  code?: string
  name: string
  status?: string
  versionNo: number
  envScope?: string
  lockedBy?: string
  lastMessage?: string
}

interface VersionRow {
  id?: number
  versionNo: number
  changeSummary?: string
  envScope?: string
  publishedBy?: string
  publishedAt?: string
}

const activeTab = ref<'scripts' | 'workflows'>('scripts')
const loading = ref(false)
const scripts = ref<ScriptRow[]>([])
const workflows = ref<WorkflowRow[]>([])

const scriptQuery = reactive({ keyword: '', publishStatus: '', envScope: '' })
const workflowQuery = reactive({ keyword: '', envScope: '', objectType: '' })

const filteredScripts = computed(() => {
  const kw = scriptQuery.keyword.trim().toLowerCase()
  return scripts.value.filter((s) => {
    if (kw) {
      const hit = `${s.scriptCode || ''} ${s.scriptName || ''}`.toLowerCase().includes(kw)
      if (!hit) return false
    }
    if (scriptQuery.publishStatus && s.publishStatus !== scriptQuery.publishStatus) return false
    if (scriptQuery.envScope && (s.envScope || 'DEV') !== scriptQuery.envScope) return false
    return true
  })
})

const filteredWorkflows = computed(() => {
  const kw = workflowQuery.keyword.trim().toLowerCase()
  return workflows.value.filter((w) => {
    if (kw) {
      const hit = `${w.code || ''} ${w.name || ''}`.toLowerCase().includes(kw)
      if (!hit) return false
    }
    if (workflowQuery.envScope && (w.envScope || 'DEV') !== workflowQuery.envScope) return false
    if (workflowQuery.objectType && w.objectType !== workflowQuery.objectType) return false
    return true
  })
})

const {
  page: scriptPage,
  pageSize: scriptPageSize,
  paged: pagedScripts,
  total: scriptTotal,
  resetPage: resetScriptPage,
} = useClientPager(filteredScripts)

const {
  page: wfPage,
  pageSize: wfPageSize,
  paged: pagedWorkflows,
  total: wfTotal,
  resetPage: resetWfPage,
} = useClientPager(filteredWorkflows)

const versionDrawer = ref(false)
const versionTarget = reactive({
  kind: 'SCRIPT' as 'SCRIPT' | 'TASK' | 'PIPELINE',
  id: null as number | null,
  name: '',
})
const versions = ref<VersionRow[]>([])
const publishSummary = ref('')

async function loadScripts() {
  loading.value = true
  try {
    scripts.value = (await api.get('/governance/fusion/scripts')).data || []
    resetScriptPage()
  } catch {
    ElMessage.error('加载脚本失败')
  } finally {
    loading.value = false
  }
}

async function loadWorkflows() {
  loading.value = true
  try {
    workflows.value = (await api.get('/governance/fusion/versions/workflows')).data || []
    resetWfPage()
  } catch {
    ElMessage.error('加载工作流失败')
    workflows.value = []
  } finally {
    loading.value = false
  }
}

async function onTabChange(name: string | number) {
  if (name === 'workflows') await loadWorkflows()
  else await loadScripts()
}

async function lockScript(row: ScriptRow) {
  await api.post(`/governance/fusion/scripts/${row.id}/lock`)
  ElMessage.success('已锁定')
  await loadScripts()
}

async function unlockScript(row: ScriptRow) {
  await api.post(`/governance/fusion/scripts/${row.id}/unlock`)
  ElMessage.success('已解锁')
  await loadScripts()
}

async function setScriptEnv(row: ScriptRow, envScope: string) {
  await api.put(`/governance/fusion/scripts/${row.id}/env`, { envScope })
  ElMessage.success(`已切换为${statusLabel(envScope)}环境`)
  await loadScripts()
}

async function deployScript(row: ScriptRow) {
  await ElMessageBox.confirm(
    `将脚本「${row.scriptName}」发布新版本并部署到生产调度系统？`,
    '一键发布到生产',
  )
  const res = (await api.post(`/governance/fusion/scripts/${row.id}/deploy-prod`, {
    changeSummary: '一键发布到生产调度',
  })).data
  ElMessage.success(res?.message || `已部署 v${res?.versionNo}`)
  await loadScripts()
}

async function openScriptVersions(row: ScriptRow) {
  versionTarget.kind = 'SCRIPT'
  versionTarget.id = row.id
  versionTarget.name = row.scriptName
  publishSummary.value = ''
  versions.value = (await api.get(`/governance/fusion/scripts/${row.id}/versions`)).data || []
  versionDrawer.value = true
}

async function publishScriptVersion() {
  if (!versionTarget.id) return
  const res = (await api.post(`/governance/fusion/scripts/${versionTarget.id}/publish`, {
    changeSummary: publishSummary.value || '发布',
  })).data
  ElMessage.success(`已发布 v${res.versionNo}`)
  versions.value = (await api.get(`/governance/fusion/scripts/${versionTarget.id}/versions`)).data || []
  await loadScripts()
}

async function rollbackScript(ver: VersionRow) {
  if (!versionTarget.id) return
  await ElMessageBox.confirm(`回滚到 v${ver.versionNo}？内容将回到开发环境草稿。`, '确认回滚')
  await api.post(`/governance/fusion/scripts/${versionTarget.id}/rollback/${ver.versionNo}`)
  ElMessage.success('已回滚')
  versions.value = (await api.get(`/governance/fusion/scripts/${versionTarget.id}/versions`)).data || []
  await loadScripts()
}

function wfPath(row: WorkflowRow, action: string) {
  return `/governance/fusion/versions/workflows/${row.objectType}/${row.id}/${action}`
}

async function lockWorkflow(row: WorkflowRow) {
  await api.post(wfPath(row, 'lock'))
  ElMessage.success('已锁定')
  await loadWorkflows()
}

async function unlockWorkflow(row: WorkflowRow) {
  await api.post(wfPath(row, 'unlock'))
  ElMessage.success('已解锁')
  await loadWorkflows()
}

async function setWorkflowEnv(row: WorkflowRow, envScope: string) {
  await api.put(`/governance/fusion/versions/workflows/${row.objectType}/${row.id}/env`, { envScope })
  ElMessage.success(`已切换为${statusLabel(envScope)}环境`)
  await loadWorkflows()
}

async function deployWorkflow(row: WorkflowRow) {
  await ElMessageBox.confirm(
    `将「${row.name}」版本化并部署到生产调度系统？`,
    '一键发布到生产',
  )
  const res = (await api.post(wfPath(row, 'deploy-prod'), {
    changeSummary: '一键发布到生产调度',
  })).data
  ElMessage.success(res?.message || '已部署到生产调度')
  await loadWorkflows()
}

async function openWorkflowVersions(row: WorkflowRow) {
  versionTarget.kind = row.objectType
  versionTarget.id = row.id
  versionTarget.name = row.name
  publishSummary.value = ''
  versions.value =
    (await api.get(`/governance/fusion/versions/workflows/${row.objectType}/${row.id}`)).data || []
  versionDrawer.value = true
}

async function publishWorkflowVersion() {
  if (!versionTarget.id || versionTarget.kind === 'SCRIPT') return
  const res = (await api.post(
    `/governance/fusion/versions/workflows/${versionTarget.kind}/${versionTarget.id}/publish`,
    { changeSummary: publishSummary.value || '发布' },
  )).data
  ElMessage.success(`已发布 v${res.versionNo}`)
  versions.value =
    (await api.get(`/governance/fusion/versions/workflows/${versionTarget.kind}/${versionTarget.id}`)).data || []
  await loadWorkflows()
}

async function rollbackWorkflow(ver: VersionRow) {
  if (!versionTarget.id || versionTarget.kind === 'SCRIPT') return
  await ElMessageBox.confirm(`回滚到 v${ver.versionNo}？将恢复快照并切回开发环境。`, '确认回滚')
  await api.post(
    `/governance/fusion/versions/workflows/${versionTarget.kind}/${versionTarget.id}/rollback/${ver.versionNo}`,
  )
  ElMessage.success('已回滚')
  versions.value =
    (await api.get(`/governance/fusion/versions/workflows/${versionTarget.kind}/${versionTarget.id}`)).data || []
  await loadWorkflows()
}

async function publishCurrentVersion() {
  if (versionTarget.kind === 'SCRIPT') await publishScriptVersion()
  else await publishWorkflowVersion()
}

async function rollbackVersion(ver: VersionRow) {
  if (versionTarget.kind === 'SCRIPT') await rollbackScript(ver)
  else await rollbackWorkflow(ver)
}

onMounted(() => {
  void loadScripts()
})
</script>

<template>
  <div class="fusion-version">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="hint"
      title="版本管理：脚本与工作流均可锁定防冲突；开发/生产环境隔离；支持回滚；一键发布部署到生产调度（DolphinScheduler）。"
    />

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="脚本版本" name="scripts" />
      <el-tab-pane label="工作流版本" name="workflows" />
    </el-tabs>

    <template v-if="activeTab === 'scripts'">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="名称/编码" class="portal-field-lg">
          <el-input
            v-model="scriptQuery.keyword"
            clearable
            placeholder="脚本名称或编码"
            @keyup.enter="loadScripts"
          />
        </el-form-item>
        <el-form-item label="发布状态" class="portal-field-md">
          <el-select v-model="scriptQuery.publishStatus" clearable placeholder="全部">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
          </el-select>
        </el-form-item>
        <el-form-item label="环境" class="portal-field-sm">
          <el-select v-model="scriptQuery.envScope" clearable placeholder="全部">
            <el-option label="开发" value="DEV" />
            <el-option label="生产" value="PROD" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="loadScripts">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="pagedScripts" stripe size="small">
        <el-table-column prop="scriptCode" label="编码" width="120" />
        <el-table-column prop="scriptName" label="名称" min-width="140" />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">{{ statusLabel(row.scriptType) }}</template>
        </el-table-column>
        <el-table-column label="发布" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.publishStatus)">
              {{ statusLabel(row.publishStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="versionNo" label="版本" width="60" />
        <el-table-column label="环境" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="(row.envScope || 'DEV') === 'PROD' ? 'danger' : 'info'">
              {{ statusLabel(row.envScope || 'DEV') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="锁定" width="100">
          <template #default="{ row }">
            <span v-if="row.lockedBy">{{ row.lockedBy }}</span>
            <span v-else class="muted">未锁定</span>
          </template>
        </el-table-column>
        <el-table-column label="生产版本" width="90">
          <template #default="{ row }">
            {{ row.prodDeployedVersion != null ? `v${row.prodDeployedVersion}` : '—' }}
          </template>
        </el-table-column>
        <el-table-column prop="lastMessage" label="最近执行" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <div class="table-ops">
              <el-button link type="primary" size="small" @click="openScriptVersions(row)">版本</el-button>
              <el-button
                v-if="!row.lockedBy"
                link
                type="primary"
                size="small"
                @click="lockScript(row)"
              >锁定</el-button>
              <el-button
                v-else
                link
                type="warning"
                size="small"
                @click="unlockScript(row)"
              >解锁</el-button>
              <el-dropdown trigger="click" @command="(c: string) => setScriptEnv(row, c)">
                <el-button link type="primary" size="small">
                  环境<span class="ops-caret">▾</span>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="DEV">开发</el-dropdown-item>
                    <el-dropdown-item command="PROD">生产</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button link type="primary" size="small" @click="deployScript(row)">一键发布</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <PortalPagination
        v-model:page="scriptPage"
        v-model:page-size="scriptPageSize"
        :total="scriptTotal"
      />
      <el-empty
        v-if="!loading && !filteredScripts.length"
        :description="scripts.length ? '无匹配脚本' : '暂无融合脚本，请先在「脚本开发」创建'"
      />
    </template>

    <template v-else>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="名称" class="portal-field-lg">
          <el-input
            v-model="workflowQuery.keyword"
            clearable
            placeholder="任务/流水线名称"
            @keyup.enter="loadWorkflows"
          />
        </el-form-item>
        <el-form-item label="类型" class="portal-field-md">
          <el-select v-model="workflowQuery.objectType" clearable placeholder="全部">
            <el-option label="融合任务" value="TASK" />
            <el-option label="跨模块流水线" value="PIPELINE" />
          </el-select>
        </el-form-item>
        <el-form-item label="环境" class="portal-field-sm">
          <el-select v-model="workflowQuery.envScope" clearable placeholder="全部">
            <el-option label="开发" value="DEV" />
            <el-option label="生产" value="PROD" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="loadWorkflows">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="pagedWorkflows" stripe size="small">
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ row.objectTypeLabel || row.objectType }}</template>
        </el-table-column>
        <el-table-column prop="code" label="编码" width="120" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="versionNo" label="版本" width="60" />
        <el-table-column label="环境" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="(row.envScope || 'DEV') === 'PROD' ? 'danger' : 'info'">
              {{ statusLabel(row.envScope || 'DEV') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="锁定" width="100">
          <template #default="{ row }">
            <span v-if="row.lockedBy">{{ row.lockedBy }}</span>
            <span v-else class="muted">未锁定</span>
          </template>
        </el-table-column>
        <el-table-column prop="lastMessage" label="说明" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <div class="table-ops">
              <el-button link type="primary" size="small" @click="openWorkflowVersions(row)">版本</el-button>
              <el-button
                v-if="!row.lockedBy"
                link
                type="primary"
                size="small"
                @click="lockWorkflow(row)"
              >锁定</el-button>
              <el-button
                v-else
                link
                type="warning"
                size="small"
                @click="unlockWorkflow(row)"
              >解锁</el-button>
              <el-dropdown trigger="click" @command="(c: string) => setWorkflowEnv(row, c)">
                <el-button link type="primary" size="small">
                  环境<span class="ops-caret">▾</span>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="DEV">开发</el-dropdown-item>
                    <el-dropdown-item command="PROD">生产</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button link type="primary" size="small" @click="deployWorkflow(row)">一键发布</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <PortalPagination v-model:page="wfPage" v-model:page-size="wfPageSize" :total="wfTotal" />
      <el-empty
        v-if="!loading && !filteredWorkflows.length"
        :description="workflows.length ? '无匹配工作流' : '暂无融合任务或跨模块流水线'"
      />
    </template>

    <el-drawer v-model="versionDrawer" :title="`版本 · ${versionTarget.name}`" size="460px">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="变更说明" class="portal-field-lg">
          <el-input v-model="publishSummary" placeholder="发布说明" />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" :disabled="!versionTarget.id" @click="publishCurrentVersion">
            发布当前版本
          </el-button>
        </el-form-item>
      </el-form>
      <el-table :data="versions" stripe size="small">
        <el-table-column prop="versionNo" label="版本" width="60" />
        <el-table-column prop="changeSummary" label="说明" min-width="100" />
        <el-table-column label="环境" width="70">
          <template #default="{ row }">{{ statusLabel(row.envScope || 'DEV') }}</template>
        </el-table-column>
        <el-table-column prop="publishedBy" label="发布人" width="80" />
        <el-table-column prop="publishedAt" label="时间" width="150" />
        <el-table-column label="操作" width="70">
          <template #default="{ row }">
            <el-button link @click="rollbackVersion(row)">回滚</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<style scoped>
.hint {
  margin-bottom: 12px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.table-ops {
  display: inline-flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 2px;
  white-space: nowrap;
}
.table-ops :deep(.el-button) {
  margin: 0;
  padding: 0 6px;
  height: 24px;
  min-height: 24px;
}
.table-ops :deep(.el-dropdown) {
  display: inline-flex;
  align-items: center;
  vertical-align: middle;
}
.ops-caret {
  margin-left: 2px;
  font-size: 10px;
  opacity: 0.7;
}
</style>
