<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

interface Connector {
  id: number
  connectorCode: string
  connectorName: string
  sourceType: string
  status: string
  lastMessage: string
}

interface Rule {
  id: number
  ruleCode: string
  ruleName: string
  ruleType: string
  status: string
}

interface Task {
  id: number
  taskName: string
  ruleId: number
  status: string
  lastScore: number
  lastMessage: string
}

const connectors = ref<Connector[]>([])
const rules = ref<Rule[]>([])
const tasks = ref<Task[]>([])
const integration = ref<Record<string, boolean>>({})
const connectorForm = reactive({ connectorName: '', sourceType: 'MySQL' })
const ruleForm = reactive({ ruleName: '', ruleType: 'COMPLETENESS' })
const taskForm = reactive({ taskName: '', ruleId: undefined as number | undefined })

async function load() {
  const [c, r, t, h] = await Promise.all([
    api.get('/governance/connectors'),
    api.get('/governance/quality/rules'),
    api.get('/governance/quality/tasks'),
    api.get('/integration/health').catch(() => ({ data: {} })),
  ])
  connectors.value = c.data
  rules.value = r.data
  tasks.value = t.data
  integration.value = h.data || {}
}

async function createConnector() {
  if (!connectorForm.connectorName) {
    ElMessage.warning('请填写适配器名称')
    return
  }
  await api.post('/governance/connectors', connectorForm)
  ElMessage.success('适配器已创建')
  connectorForm.connectorName = ''
  load()
}

async function syncConnector(id: number) {
  const res = await api.post(`/governance/connectors/${id}/sync`)
  ElMessage.success(res.data.message || '同步成功')
  load()
}

async function createRule() {
  if (!ruleForm.ruleName) {
    ElMessage.warning('请填写规则名称')
    return
  }
  await api.post('/governance/quality/rules', ruleForm)
  ElMessage.success('质量规则已创建')
  ruleForm.ruleName = ''
  load()
}

async function createTask() {
  if (!taskForm.taskName) {
    ElMessage.warning('请填写任务名称')
    return
  }
  await api.post('/governance/quality/tasks', taskForm)
  ElMessage.success('质量任务已创建')
  taskForm.taskName = ''
  load()
}

async function runTask(id: number) {
  const res = await api.post(`/governance/quality/tasks/${id}/run`)
  ElMessage.success(`质量评分 ${res.data.score}`)
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      title="数据融合治理"
      description="OpenMetadata 真实联调：元数据适配器 + 质量规则/任务（D10 菜单映射）"
    />
    <el-alert
      v-if="integration.enabled"
      type="success"
      :closable="false"
      show-icon
      :title="`开源集成已启用 · OpenMetadata=${integration.openmetadata ? 'UP' : 'DOWN'}`"
      style="margin-bottom: 12px"
    />
    <PageCard title="元数据 · 适配器管理（M086）">
      <el-form inline>
        <el-form-item label="名称">
          <el-input v-model="connectorForm.connectorName" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="connectorForm.sourceType" style="width: 140px">
            <el-option label="MySQL" value="MySQL" />
            <el-option label="Oracle" value="Oracle" />
            <el-option label="API" value="API" />
          </el-select>
        </el-form-item>
        <el-button type="primary" @click="createConnector">新增</el-button>
      </el-form>
      <el-table class="portal-table" :data="connectors" stripe>
        <el-table-column prop="connectorCode" label="编码" min-width="140" />
        <el-table-column prop="connectorName" label="名称" min-width="160" />
        <el-table-column prop="sourceType" label="类型" width="100" />
        <el-table-column prop="lastMessage" label="最近同步" min-width="200" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="syncConnector(row.id)">采集</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>
    <PageCard title="数据质量中心（M078/M079）">
      <el-form inline>
        <el-form-item label="规则名">
          <el-input v-model="ruleForm.ruleName" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="ruleForm.ruleType" style="width: 160px">
            <el-option label="完整性" value="COMPLETENESS" />
            <el-option label="准确性" value="ACCURACY" />
            <el-option label="一致性" value="CONSISTENCY" />
          </el-select>
        </el-form-item>
        <el-button type="primary" @click="createRule">新增规则</el-button>
      </el-form>
      <el-table class="portal-table" :data="rules" stripe>
        <el-table-column prop="ruleCode" label="编码" min-width="120" />
        <el-table-column prop="ruleName" label="名称" min-width="160" />
        <el-table-column prop="ruleType" label="类型" width="140" />
        <el-table-column prop="status" label="状态" width="100" />
      </el-table>
      <el-divider />
      <el-form inline>
        <el-form-item label="任务名">
          <el-input v-model="taskForm.taskName" />
        </el-form-item>
        <el-form-item label="关联规则">
          <el-select v-model="taskForm.ruleId" clearable style="width: 200px">
            <el-option v-for="r in rules" :key="r.id" :label="r.ruleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-button type="primary" @click="createTask">创建任务</el-button>
      </el-form>
      <el-table class="portal-table" :data="tasks" stripe>
        <el-table-column prop="taskName" label="任务" min-width="160" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="lastScore" label="评分" width="100" />
        <el-table-column prop="lastMessage" label="结果" min-width="180" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="runTask(row.id)">执行</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>
  </div>
</template>
