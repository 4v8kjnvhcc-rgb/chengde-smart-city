<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'
import { ingestionApi, type DataSource } from '@/views/exchange/ingestion/useIngestionHub'

interface ScriptRow {
  id: number
  scriptCode: string
  scriptName: string
  scriptType: string
  scriptContent?: string
  datasourceId?: number
  publishStatus: string
  versionNo: number
  status: string
  lastRunAt?: string
  lastMessage?: string
}

interface VersionRow {
  id: number
  scriptId: number
  versionNo: number
  changeSummary?: string
  publishedBy?: string
  publishedAt?: string
}

const scripts = ref<ScriptRow[]>([])
const loading = ref(false)
const drawer = ref(false)
const versionDrawer = ref(false)
const dataSources = ref<DataSource[]>([])
const versions = ref<VersionRow[]>([])
const execResult = ref<{ mode?: string; rowCount?: number; affectedRows?: number; rows?: Record<string, unknown>[]; message?: string } | null>(null)
const publishSummary = ref('')

const form = reactive({
  id: null as number | null,
  scriptCode: '',
  scriptName: '',
  scriptType: 'SELECT',
  scriptContent: 'SELECT 1 AS demo_col',
  datasourceId: undefined as number | undefined,
})

async function loadScripts() {
  loading.value = true
  try {
    scripts.value = (await api.get('/governance/fusion/scripts')).data || []
  } catch {
    ElMessage.error('加载脚本失败')
  } finally {
    loading.value = false
  }
}

async function loadDataSources() {
  try {
    dataSources.value = (await ingestionApi.dataSources()).data || []
  } catch {
    dataSources.value = []
  }
}

function openCreate() {
  form.id = null
  form.scriptCode = ''
  form.scriptName = ''
  form.scriptType = 'SELECT'
  form.scriptContent = 'SELECT 1 AS demo_col'
  form.datasourceId = undefined
  execResult.value = null
  publishSummary.value = ''
  drawer.value = true
  void loadDataSources()
}

async function openEdit(row: ScriptRow) {
  const detail = (await api.get(`/governance/fusion/scripts/${row.id}`)).data
  form.id = detail.id
  form.scriptCode = detail.scriptCode
  form.scriptName = detail.scriptName
  form.scriptType = detail.scriptType || 'SELECT'
  form.scriptContent = detail.scriptContent || ''
  form.datasourceId = detail.datasourceId
  execResult.value = null
  publishSummary.value = ''
  drawer.value = true
  void loadDataSources()
}

async function saveScript() {
  if (!form.scriptCode.trim() || !form.scriptName.trim() || !form.scriptContent.trim()) {
    ElMessage.warning('请填写编码、名称与脚本内容')
    return
  }
  const body = {
    scriptCode: form.scriptCode,
    scriptName: form.scriptName,
    scriptType: form.scriptType,
    scriptContent: form.scriptContent,
    datasourceId: form.datasourceId ?? null,
  }
  if (form.id) {
    await api.put(`/governance/fusion/scripts/${form.id}`, body)
    ElMessage.success('脚本已更新')
  } else {
    const id = (await api.post('/governance/fusion/scripts', body)).data
    form.id = id
    ElMessage.success('脚本已创建')
  }
  await loadScripts()
}

async function removeScript(row: ScriptRow) {
  await ElMessageBox.confirm(`删除脚本「${row.scriptName}」？`, '确认')
  await api.delete(`/governance/fusion/scripts/${row.id}`)
  ElMessage.success('已删除')
  await loadScripts()
}

async function executeScript() {
  if (!form.id) {
    ElMessage.warning('请先保存脚本')
    return
  }
  await saveScript()
  try {
    execResult.value = (await api.post(`/governance/fusion/scripts/${form.id}/execute`)).data
    ElMessage.success(execResult.value?.message || '执行完成')
    await loadScripts()
  } catch {
    ElMessage.error('执行失败')
  }
}

async function openVersions(row: ScriptRow) {
  form.id = row.id
  form.scriptName = row.scriptName
  versions.value = (await api.get(`/governance/fusion/scripts/${row.id}/versions`)).data || []
  publishSummary.value = ''
  versionDrawer.value = true
}

async function publishScript() {
  if (!form.id) return
  const res = (await api.post(`/governance/fusion/scripts/${form.id}/publish`, {
    changeSummary: publishSummary.value || '发布',
  })).data
  ElMessage.success(`已发布 v${res.versionNo}`)
  versions.value = (await api.get(`/governance/fusion/scripts/${form.id}/versions`)).data || []
  await loadScripts()
}

async function rollbackVersion(ver: VersionRow) {
  if (!form.id) return
  await ElMessageBox.confirm(`回滚到 v${ver.versionNo}？`, '确认')
  await api.post(`/governance/fusion/scripts/${form.id}/rollback/${ver.versionNo}`)
  ElMessage.success('已回滚')
  versions.value = (await api.get(`/governance/fusion/scripts/${form.id}/versions`)).data || []
  if (drawer.value) {
    const detail = (await api.get(`/governance/fusion/scripts/${form.id}`)).data
    form.scriptContent = detail.scriptContent || ''
  }
  await loadScripts()
}

onMounted(() => { void loadScripts() })
</script>

<template>
  <PageCard title="融合脚本">
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="openCreate">新建脚本</el-button>
        <el-button @click="loadScripts">刷新</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="scripts" stripe size="small">
      <el-table-column prop="scriptCode" label="编码" width="120" />
      <el-table-column prop="scriptName" label="名称" min-width="140" />
      <el-table-column prop="scriptType" label="类型" width="80" />
      <el-table-column label="发布" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTagType(row.publishStatus)">{{ statusLabel(row.publishStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="versionNo" label="版本" width="60" />
      <el-table-column prop="lastMessage" label="最近执行" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link @click="openEdit(row)">编辑</el-button>
          <el-button link @click="openVersions(row)">版本</el-button>
          <el-button link type="danger" @click="removeScript(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="drawer" :title="form.id ? `编辑 · ${form.scriptName}` : '新建脚本'" size="520px">
      <el-form label-position="top" size="small">
        <el-form-item label="编码"><el-input v-model="form.scriptCode" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.scriptName" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.scriptType" style="width:100%">
            <el-option label="查询 SELECT" value="SELECT" />
            <el-option label="更新 UPDATE" value="UPDATE" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据源">
          <el-select v-model="form.datasourceId" clearable placeholder="默认平台库" style="width:100%">
            <el-option v-for="ds in dataSources" :key="ds.id" :label="ds.sourceName" :value="ds.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="脚本 SQL">
          <el-input v-model="form.scriptContent" type="textarea" :rows="12" placeholder="SELECT ... 或 UPDATE ..." />
        </el-form-item>
        <el-space>
          <el-button type="primary" @click="saveScript">保存</el-button>
          <el-button type="success" :disabled="!form.id" @click="executeScript">执行</el-button>
        </el-space>
      </el-form>

      <template v-if="execResult">
        <el-divider />
        <div class="result-title">执行结果</div>
        <el-alert :title="execResult.message" type="success" :closable="false" show-icon />
        <el-table
          v-if="execResult.mode === 'SELECT' && execResult.rows?.length"
          :data="execResult.rows"
          size="small"
          stripe
          max-height="240"
          style="margin-top:8px"
        />
        <div v-else-if="execResult.mode === 'UPDATE'" class="muted">影响行数：{{ execResult.affectedRows }}</div>
      </template>
    </el-drawer>

    <el-drawer v-model="versionDrawer" :title="`版本 · ${form.scriptName}`" size="420px">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="变更说明" class="portal-field-lg">
          <el-input v-model="publishSummary" placeholder="发布说明" />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" :disabled="!form.id" @click="publishScript">发布当前版本</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="versions" stripe size="small">
        <el-table-column prop="versionNo" label="版本" width="60" />
        <el-table-column prop="changeSummary" label="说明" />
        <el-table-column prop="publishedBy" label="发布人" width="80" />
        <el-table-column prop="publishedAt" label="时间" width="150" />
        <el-table-column label="操作" width="70">
          <template #default="{ row }">
            <el-button link @click="rollbackVersion(row)">回滚</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </PageCard>
</template>

<style scoped>
.result-title {
  font-weight: 600;
  margin-bottom: 8px;
  font-size: 13px;
}
.muted {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
