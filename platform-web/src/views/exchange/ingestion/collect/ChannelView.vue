<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import PageCard from '@/components/common/PageCard.vue'
import { ingestionApi, useIngestionLoading, type Channel, type IngestTask } from '../useIngestionHub'

const props = defineProps<{ module: string }>()
const { loading, loadError, withLoad } = useIngestionLoading()
const channels = ref<Channel[]>([])
const tasks = ref<IngestTask[]>([])
const taskForm = reactive({ channelId: undefined as number | undefined, taskName: '', scheduleCron: '0 2 * * *' })

const CHANNEL_TYPE_MAP: Record<string, string> = {
  m054: 'TABLE', m055: 'FTP', m056: 'LOCAL', m057: 'UNSTRUCT', m058: 'SEMI', m059: 'API', m060: 'CDC',
}

const title = computed(() => ({
  m054: '结构化数据接入', m055: '远程文件接入（FTP）', m056: '本地文件接入',
  m057: '非结构化数据接入', m058: '半结构化数据接入', m059: 'API 接口数据接入', m060: 'CDC 实时数据接入',
}[props.module] || '多通道接入'))

const channelType = computed(() => CHANNEL_TYPE_MAP[props.module])

async function reload() {
  await withLoad(async () => {
    channels.value = (await ingestionApi.channels(channelType.value)).data
    tasks.value = (await ingestionApi.tasks()).data
    if (channels.value.length) taskForm.channelId = channels.value[0].id
  })
}

async function runChannel(id: number) {
  await ingestionApi.runChannel(id)
  await reload()
}

async function createTask() {
  if (!taskForm.taskName || !taskForm.channelId) return
  await ingestionApi.createTask({ ...taskForm })
  taskForm.taskName = ''
  await reload()
}

onMounted(reload)
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard :title="title">
      <el-table :data="channels" stripe>
        <el-table-column prop="channelName" label="通道" min-width="160" />
        <el-table-column prop="channelType" label="类型" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="configJson" label="配置" min-width="180" show-overflow-tooltip />
        <el-table-column prop="lastMessage" label="最近执行" min-width="200" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }"><el-button link type="primary" @click="runChannel(row.id)">执行</el-button></template>
        </el-table-column>
      </el-table>
    </PageCard>
    <PageCard title="接入任务配置">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="通道" class="portal-field-default">
          <el-select v-model="taskForm.channelId">
            <el-option v-for="c in channels" :key="c.id" :label="c.channelName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务名" class="portal-field-md"><el-input v-model="taskForm.taskName" /></el-form-item>
        <el-form-item label="调度" class="portal-field-cron"><el-input v-model="taskForm.scheduleCron" /></el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="createTask">登记任务</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="tasks.filter(t => !channelType || channels.some(c => c.id === t.channelId))" stripe size="small">
        <el-table-column prop="taskCode" label="编码" width="160" />
        <el-table-column prop="taskName" label="任务" min-width="140" />
        <el-table-column prop="scheduleCron" label="Cron" width="120" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="lastRunMessage" label="日志" min-width="200" show-overflow-tooltip />
      </el-table>
    </PageCard>
  </div>
</template>
