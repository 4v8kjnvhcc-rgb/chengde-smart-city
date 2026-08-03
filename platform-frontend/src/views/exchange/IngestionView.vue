<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

interface Asset {
  id: number
  assetCode: string
  assetName: string
  sourceSystem: string
  status: string
}

interface Task {
  id: number
  taskName: string
  assetId: number
  status: string
  lastRunAt: string
  lastMessage: string
}

const assets = ref<Asset[]>([])
const tasks = ref<Task[]>([])
const assetForm = reactive({ assetName: '', sourceSystem: '业务系统A' })
const taskForm = reactive({ taskName: '', assetId: undefined as number | undefined })

async function load() {
  const [a, t] = await Promise.all([
    api.get('/exchange/assets'),
    api.get('/exchange/collect-tasks'),
  ])
  assets.value = a.data
  tasks.value = t.data
}

async function registerAsset() {
  if (!assetForm.assetName) {
    ElMessage.warning('请填写资产名称')
    return
  }
  await api.post('/exchange/assets', assetForm)
  ElMessage.success('资产已登记')
  assetForm.assetName = ''
  load()
}

async function createTask() {
  if (!taskForm.taskName) {
    ElMessage.warning('请填写任务名称')
    return
  }
  await api.post('/exchange/collect-tasks', taskForm)
  ElMessage.success('采集任务已创建')
  taskForm.taskName = ''
  load()
}

async function runTask(id: number) {
  await api.post(`/exchange/collect-tasks/${id}/run`)
  ElMessage.success('采集任务已执行')
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="大数据归集" description="演示场景：数据资产登记 → 采集任务 → 执行记录" />
    <PageCard title="1. 登记数据资产">
      <el-form inline>
        <el-form-item label="资产名称">
          <el-input v-model="assetForm.assetName" placeholder="如：企业基础台账" />
        </el-form-item>
        <el-form-item label="数据源">
          <el-input v-model="assetForm.sourceSystem" />
        </el-form-item>
        <el-button type="primary" @click="registerAsset">登记</el-button>
      </el-form>
      <el-table class="portal-table" :data="assets" stripe>
        <el-table-column prop="assetCode" label="编码" min-width="140" />
        <el-table-column prop="assetName" label="名称" min-width="160" />
        <el-table-column prop="sourceSystem" label="数据源" min-width="120" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
      </el-table>
    </PageCard>
    <PageCard title="2. 采集任务">
      <el-form inline>
        <el-form-item label="任务名称">
          <el-input v-model="taskForm.taskName" />
        </el-form-item>
        <el-form-item label="关联资产">
          <el-select v-model="taskForm.assetId" clearable placeholder="可选" style="width: 200px">
            <el-option v-for="a in assets" :key="a.id" :label="a.assetName" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-button type="primary" @click="createTask">创建任务</el-button>
      </el-form>
      <el-table class="portal-table" :data="tasks" stripe>
        <el-table-column prop="taskName" label="任务" min-width="160" />
        <el-table-column prop="assetId" label="资产ID" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="lastMessage" label="最近执行" min-width="220" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="runTask(row.id)">执行</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>
  </div>
</template>
