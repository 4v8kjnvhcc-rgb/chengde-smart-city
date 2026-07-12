<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

interface Theme {
  id: number
  themeCode: string
  themeName: string
  partitionKey: string
  status: string
}

interface Backup {
  id: number
  jobName: string
  themeId: number
  status: string
  lastMessage: string
}

const themes = ref<Theme[]>([])
const backups = ref<Backup[]>([])
const themeForm = reactive({ themeName: '', partitionKey: 'org_id' })
const backupForm = reactive({ jobName: '', themeId: undefined as number | undefined })

async function load() {
  const [t, b] = await Promise.all([
    api.get('/resource-center/themes'),
    api.get('/resource-center/backups'),
  ])
  themes.value = t.data
  backups.value = b.data
}

async function createTheme() {
  if (!themeForm.themeName) {
    ElMessage.warning('请填写主题库名称')
    return
  }
  await api.post('/resource-center/themes', themeForm)
  ElMessage.success('主题库已创建')
  themeForm.themeName = ''
  load()
}

async function createBackup() {
  if (!backupForm.jobName) {
    ElMessage.warning('请填写备份任务名')
    return
  }
  await api.post('/resource-center/backups', backupForm)
  ElMessage.success('备份任务已创建')
  backupForm.jobName = ''
  load()
}

async function runBackup(id: number) {
  const res = await api.post(`/resource-center/backups/${id}/run`)
  ElMessage.success(res.data.message || '备份完成')
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      title="资源中心"
      description="MS5 POC：主题库逻辑管理 + 备份任务台账（M130～M138 能力等价入口）"
    />
    <PageCard title="主题库">
      <el-form inline>
        <el-form-item label="名称">
          <el-input v-model="themeForm.themeName" />
        </el-form-item>
        <el-form-item label="分区键">
          <el-input v-model="themeForm.partitionKey" />
        </el-form-item>
        <el-button type="primary" @click="createTheme">新增</el-button>
      </el-form>
      <el-table class="portal-table" :data="themes" stripe>
        <el-table-column prop="themeCode" label="编码" min-width="140" />
        <el-table-column prop="themeName" label="名称" min-width="160" />
        <el-table-column prop="partitionKey" label="分区" width="120" />
        <el-table-column prop="status" label="状态" width="100" />
      </el-table>
    </PageCard>
    <PageCard title="备份恢复">
      <el-form inline>
        <el-form-item label="任务名">
          <el-input v-model="backupForm.jobName" />
        </el-form-item>
        <el-form-item label="主题库">
          <el-select v-model="backupForm.themeId" clearable style="width: 200px">
            <el-option v-for="t in themes" :key="t.id" :label="t.themeName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-button type="primary" @click="createBackup">创建</el-button>
      </el-form>
      <el-table class="portal-table" :data="backups" stripe>
        <el-table-column prop="jobName" label="任务" min-width="160" />
        <el-table-column prop="themeId" label="主题ID" width="100" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="lastMessage" label="结果" min-width="220" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="runBackup(row.id)">执行</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>
  </div>
</template>
