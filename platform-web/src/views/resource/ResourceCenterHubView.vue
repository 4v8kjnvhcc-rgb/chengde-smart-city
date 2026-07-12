<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

interface Library { id: number; libCode: string; libName: string; libType: string; recordCount: number }
interface Partition { partitionCode: string; partitionName: string; partitionType: string }
interface Policy { id: number; policyCode: string; policyName: string; actionType: string; retentionDays: number }
interface Backup { id: number; jobName: string; status: string; lastMessage?: string }
interface CatalogEntry { id: number; entryCode: string; entryName: string; driveTask?: string }
interface Monitor { metricLabel: string; metricValue: string; alertLevel: string }

const route = useRoute()
const router = useRouter()
const tabMap: Record<string, string> = {
  library: 'library', m130: 'library', m132: 'library',
  partition: 'partition', m133: 'partition', m134: 'partition',
  catalog: 'catalog', m135: 'catalog',
  analytics: 'analytics', m136: 'analytics', m137: 'analytics',
  monitor: 'monitor', m138: 'monitor',
}
const tab = ref('library')
const libOverview = ref<Record<string, unknown> | null>(null)
const partOverview = ref<Record<string, unknown> | null>(null)
const stats = ref<Record<string, unknown> | null>(null)
const monitor = ref<Monitor[]>([])
const catalogEntries = ref<CatalogEntry[]>([])
const searchQ = ref('')
const libForm = reactive({ libName: '', libType: 'BASE' })
const catalogForm = reactive({ entryName: '', libId: 1 })

function resolveTab() {
  tab.value = tabMap[String(route.query.tab || 'library').toLowerCase()] || 'library'
}

async function loadLibrary() {
  libOverview.value = (await api.get('/resource-center/platform/libraries/overview')).data
}
async function loadPartition() {
  partOverview.value = (await api.get('/resource-center/platform/partition/overview')).data
}
async function loadCatalog() {
  catalogEntries.value = (await api.get('/resource-center/platform/catalog/entries')).data
}
async function loadAnalytics() {
  stats.value = (await api.get('/resource-center/platform/statistics')).data
}
async function loadMonitor() {
  monitor.value = (await api.get('/resource-center/platform/monitor')).data
}

async function loadTabData() {
  try {
    if (tab.value === 'library') await loadLibrary()
    if (tab.value === 'partition') await loadPartition()
    if (tab.value === 'catalog') await loadCatalog()
    if (tab.value === 'analytics') await loadAnalytics()
    if (tab.value === 'monitor') await loadMonitor()
  } catch { ElMessage.error('加载失败') }
}

async function createLib() {
  if (!libForm.libName) return
  await api.post('/resource-center/platform/libraries', libForm)
  ElMessage.success('基础库已创建')
  libForm.libName = ''
  await loadLibrary()
}
async function runPolicy(id: number) {
  await api.post(`/resource-center/platform/policies/${id}/execute`)
  ElMessage.success('策略已执行')
}
async function runBackup(id: number) {
  const res = await api.post(`/resource-center/backups/${id}/run`)
  ElMessage.success(res.data.message)
  await loadPartition()
}
async function createCatalog() {
  if (!catalogForm.entryName) return
  await api.post('/resource-center/platform/catalog/entries', catalogForm)
  ElMessage.success('资产目录已创建')
  catalogForm.entryName = ''
  await loadCatalog()
}
async function doSearch() {
  await api.get('/resource-center/platform/search', { params: { q: searchQ.value } })
  ElMessage.success('检索完成')
}

const tabTitle = computed(() => ({
  library: 'M130-M132 基础/半结构/非结构库', partition: 'M133-M134 分区与存储', catalog: 'M135 资产目录',
  analytics: 'M136-M137 检索统计', monitor: 'M138 资源监控',
}[tab.value] || '资源中心'))

watch(tab, () => { router.replace({ query: { ...route.query, tab: tab.value } }); loadTabData() })
watch(() => route.query.tab, resolveTab)
onMounted(() => { resolveTab(); loadTabData() })
</script>

<template>
  <div>
    <PageHeader :title="`大数据资源中心 · ${tabTitle}`" description="M130～M138：主题库、分区备份、资产目录、检索统计与监控" />
    <el-tabs v-model="tab" type="border-card">
      <el-tab-pane label="基础库" name="library">
        <PageCard>
          <el-form inline>
            <el-form-item label="库名"><el-input v-model="libForm.libName" /></el-form-item>
            <el-select v-model="libForm.libType" style="width:120px"><el-option label="基础库" value="BASE" /><el-option label="半结构" value="SEMI" /><el-option label="非结构" value="UNSTRUCT" /></el-select>
            <el-button type="primary" @click="createLib">新增</el-button>
          </el-form>
          <el-table v-if="libOverview" :data="(libOverview.baseLibraries as Library[])" stripe size="small">
            <el-table-column prop="libCode" label="编码" width="130" /><el-table-column prop="libName" label="名称" /><el-table-column prop="recordCount" label="记录数" width="100" />
          </el-table>
        </PageCard>
      </el-tab-pane>
      <el-tab-pane label="分区存储" name="partition">
        <PageCard v-if="partOverview">
          <el-table :data="(partOverview.partitions as Partition[])" stripe size="small">
            <el-table-column prop="partitionName" label="分区" /><el-table-column prop="partitionType" label="类型" width="90" />
          </el-table>
          <el-table :data="(partOverview.policies as Policy[])" stripe size="small" style="margin-top:12px">
            <el-table-column prop="policyName" label="策略" /><el-table-column prop="actionType" label="动作" width="90" />
            <el-table-column label="操作" width="80"><template #default="{ row }"><el-button link @click="runPolicy(row.id)">执行</el-button></template></el-table-column>
          </el-table>
          <el-table :data="(partOverview.backups as Backup[])" stripe size="small" style="margin-top:12px">
            <el-table-column prop="jobName" label="备份任务" /><el-table-column prop="status" label="状态" width="90" />
            <el-table-column label="操作" width="80"><template #default="{ row }"><el-button link @click="runBackup(row.id)">执行</el-button></template></el-table-column>
          </el-table>
        </PageCard>
      </el-tab-pane>
      <el-tab-pane label="资产目录" name="catalog">
        <PageCard>
          <el-form inline><el-form-item label="名称"><el-input v-model="catalogForm.entryName" /></el-form-item><el-button type="primary" @click="createCatalog">编目</el-button></el-form>
          <el-table :data="catalogEntries" stripe><el-table-column prop="entryCode" label="编码" width="140" /><el-table-column prop="entryName" label="名称" /><el-table-column prop="driveTask" label="驱动任务" /></el-table>
        </PageCard>
      </el-tab-pane>
      <el-tab-pane label="检索统计" name="analytics">
        <PageCard v-if="stats">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="总记录">{{ stats.totalRecords }}</el-descriptions-item>
            <el-descriptions-item label="库数量">{{ stats.libraryCount }}</el-descriptions-item>
            <el-descriptions-item label="主题库">{{ stats.themeCount }}</el-descriptions-item>
          </el-descriptions>
          <el-input v-model="searchQ" placeholder="库检索" style="max-width:280px;margin-top:12px" @keyup.enter="doSearch" />
          <el-button style="margin-left:8px" @click="doSearch">M136 检索</el-button>
        </PageCard>
      </el-tab-pane>
      <el-tab-pane label="监控" name="monitor">
        <PageCard>
          <el-table :data="monitor" stripe size="small">
            <el-table-column prop="metricLabel" label="指标" /><el-table-column prop="metricValue" label="值" /><el-table-column prop="alertLevel" label="级别" width="80" />
          </el-table>
        </PageCard>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
