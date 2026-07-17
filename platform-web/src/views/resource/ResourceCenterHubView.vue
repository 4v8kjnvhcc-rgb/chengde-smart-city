<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'
import HubSideLayout from '@/components/common/HubSideLayout.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'

const navItems = [
  { key: 'library', label: '基础库' },
  { key: 'partition', label: '分区存储' },
  { key: 'catalog', label: '资产目录' },
  { key: 'analytics', label: '检索统计' },
  { key: 'monitor', label: '监控' },
]

interface Library { id: number; libCode: string; libName: string; libType: string; recordCount: number }
interface Theme { id: number; themeCode: string; themeName: string; libraryKind: string; zoneCode?: string; managedCount?: number; status: string }
interface ManagedTable { id: number; themeId: number; physicalTable: string; themeName?: string; libraryKind?: string; recordCount?: number; status: string }
interface Candidate { entryCode: string; entryName: string; physicalTable: string; managed: boolean }
interface Partition { id: number; partitionName: string; partitionType: string; tableName?: string; partitionColumn?: string; pretestStatus?: string; pretestMessage?: string; previewDdl?: string }
interface Policy { id: number; policyName: string; actionType: string; retentionDays: number; managedTableId?: number }
interface Artifact { id: number; physicalTable: string; fileName: string; rowCount?: number; byteSize?: number; sha256?: string; status: string; createdAt?: string }
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
const themes = ref<Theme[]>([])
const managedTables = ref<ManagedTable[]>([])
const candidates = ref<Candidate[]>([])
const partOverview = ref<Record<string, unknown> | null>(null)
const stats = ref<Record<string, unknown> | null>(null)
const monitor = ref<Monitor[]>([])
const catalogEntries = ref<CatalogEntry[]>([])
const searchQ = ref('')
const pretestResult = ref<Record<string, unknown> | null>(null)
const verifyResult = ref<Record<string, unknown> | null>(null)

const libForm = reactive({ libName: '', libType: 'BASE' })
const themeForm = reactive({ themeName: '', libraryKind: 'THEME', zoneCode: 'ZONE_THEME', ownerOrg: '示范单位' })
const manageForm = reactive({ themeId: undefined as number | undefined, physicalTable: '', metaEntryCode: '' })
const partitionForm = reactive({ partitionName: '', partitionType: 'RANGE', themeId: undefined as number | undefined, tableName: '', partitionColumn: '', expressionText: '' })
const catalogForm = reactive({ entryName: '', libId: 1 })

function resolveTab() {
  tab.value = tabMap[String(route.query.tab || 'library').toLowerCase()] || 'library'
}

async function loadLibrary() {
  libOverview.value = (await api.get('/resource-center/platform/libraries/overview')).data
  themes.value = (libOverview.value?.themes as Theme[]) || []
  managedTables.value = (libOverview.value?.managedTables as ManagedTable[]) || []
}

async function loadCandidates() {
  candidates.value = (await api.get('/resource-center/platform/managed-tables/candidates')).data
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
    if (tab.value === 'library') {
      await loadLibrary()
    } else if (tab.value === 'partition') {
      await loadPartition()
    } else if (tab.value === 'catalog') {
      await loadCatalog()
    } else if (tab.value === 'analytics') {
      await loadAnalytics()
    } else if (tab.value === 'monitor') {
      await loadMonitor()
    }
  } catch {
    ElMessage.error('加载失败')
  }
}

async function createLib() {
  if (!libForm.libName) return
  await api.post('/resource-center/platform/libraries', libForm)
  ElMessage.success('基础库已创建')
  libForm.libName = ''
  await loadLibrary()
}

async function createTheme() {
  if (!themeForm.themeName) return
  await api.post('/resource-center/platform/themes', themeForm)
  ElMessage.success('主题/专题库已创建')
  themeForm.themeName = ''
  await loadLibrary()
}

async function manageTable() {
  if (!manageForm.themeId || !manageForm.physicalTable) {
    ElMessage.warning('请选择主题库并填写物理表')
    return
  }
  await api.post('/resource-center/platform/managed-tables', manageForm)
  ElMessage.success('物理表已纳管')
  manageForm.physicalTable = ''
  manageForm.metaEntryCode = ''
  await loadLibrary()
}

async function unmanageTable(id: number) {
  await api.delete(`/resource-center/platform/managed-tables/${id}`)
  ElMessage.success('已解绑纳管')
  await loadLibrary()
}

async function runBackup(id: number) {
  const res = await api.post(`/resource-center/platform/managed-tables/${id}/backup`, { retentionDays: 30 })
  ElMessage.success(`备份完成：${res.data.rowCount} 行`)
  if (tab.value === 'partition') await loadPartition()
}

async function createPartition() {
  if (!partitionForm.partitionName || !partitionForm.tableName) {
    ElMessage.warning('请填写策略名称与目标表')
    return
  }
  await api.post('/resource-center/platform/partitions', partitionForm)
  ElMessage.success('分区策略已创建')
  partitionForm.partitionName = ''
  await loadPartition()
}

async function pretestPartition(id: number) {
  const res = await api.post(`/resource-center/platform/partitions/${id}/pretest`)
  pretestResult.value = res.data
  ElMessage.success('分区预检完成（未执行物理 DDL）')
  await loadPartition()
}

async function runPolicy(id: number) {
  await api.post(`/resource-center/platform/policies/${id}/execute`)
  ElMessage.success('策略已执行')
  await loadPartition()
}

async function verifyArtifact(id: number) {
  const res = await api.get(`/resource-center/platform/backups/artifacts/${id}/verify`)
  verifyResult.value = res.data
  ElMessage.success(res.data.match ? '校验通过' : '校验失败')
}

async function refreshMonitor() {
  const res = await api.post('/resource-center/platform/monitor/refresh')
  monitor.value = res.data.metrics || []
  ElMessage.success('容量指标已刷新')
}

async function createCatalog() {
  if (!catalogForm.entryName) return
  await api.post('/resource-center/platform/catalog/entries', catalogForm)
  ElMessage.success('资产目录已创建')
  catalogForm.entryName = ''
  await loadCatalog()
}

async function doSearch() {
  const res = await api.get('/resource-center/platform/search', { params: { q: searchQ.value } })
  const hits = (res.data.hits as unknown[]) || []
  ElMessage.success(`检索完成，命中 ${hits.length} 条`)
}

function fillCandidate(row: Candidate) {
  manageForm.physicalTable = row.physicalTable
  manageForm.metaEntryCode = row.entryCode
  const topic = themes.value.find(t => t.libraryKind === 'TOPIC')
  const theme = themes.value.find(t => t.libraryKind === 'THEME')
  manageForm.themeId = topic?.id || theme?.id
}

const tabTitle = computed(() => ({
  library: 'M130-M132 主题/专题库与纳管',
  partition: 'M133-M134 分区预检与备份',
  catalog: 'M135 资产目录',
  analytics: 'M136-M137 检索统计',
  monitor: 'M138 资源监控',
}[tab.value] || '资源中心'))

watch(tab, () => {
  router.replace({ query: { ...route.query, tab: tab.value } })
  loadTabData()
})
watch(() => route.query.tab, resolveTab)
onMounted(() => {
  resolveTab()
  loadTabData()
})
</script>

<template>
  <div>
    <PageHeader
      :title="`大数据资源中心 · ${tabTitle}`"
      description="M130～M138：主题/专题库纳管、分区 DDL 预检、JDBC 逻辑备份与真实容量监控"
    />
    <HubSideLayout v-model="tab" :items="navItems">
      <PageCard v-if="tab === 'library'">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="库名" class="portal-field-md">
            <el-input v-model="libForm.libName" placeholder="基础库名称" />
          </el-form-item>
          <el-form-item label="类型" class="portal-field-sm">
            <el-select v-model="libForm.libType">
              <el-option label="基础库" value="BASE" />
              <el-option label="半结构" value="SEMI" />
              <el-option label="非结构" value="UNSTRUCT" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="createLib">新增基础库</el-button>
          </el-form-item>
        </el-form>
        <el-table v-if="libOverview" :data="(libOverview.baseLibraries as Library[])" stripe size="small">
          <el-table-column prop="libCode" label="编码" width="130" />
          <el-table-column prop="libName" label="名称" />
          <el-table-column prop="recordCount" label="记录数" width="100" />
        </el-table>

        <el-divider content-position="left">主题 / 专题库</el-divider>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="名称" class="portal-field-md">
            <el-input v-model="themeForm.themeName" placeholder="库名称" />
          </el-form-item>
          <el-form-item label="类型" class="portal-field-sm">
            <el-select v-model="themeForm.libraryKind">
              <el-option label="主题库" value="THEME" />
              <el-option label="专题库" value="TOPIC" />
            </el-select>
          </el-form-item>
          <el-form-item label="库区" class="portal-field-sm">
            <el-input v-model="themeForm.zoneCode" />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="createTheme">新增</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="themes" stripe size="small">
          <el-table-column prop="themeCode" label="编码" width="160" />
          <el-table-column prop="themeName" label="名称" />
          <el-table-column prop="libraryKind" label="类型" width="90">
            <template #default="{ row }">{{ row.libraryKind === 'TOPIC' ? '专题库' : '主题库' }}</template>
          </el-table-column>
          <el-table-column prop="zoneCode" label="库区" width="120" />
          <el-table-column prop="managedCount" label="纳管表" width="80" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <el-divider content-position="left">物理表纳管</el-divider>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="主题库" class="portal-field-lg">
            <el-select v-model="manageForm.themeId" placeholder="选择主题/专题库">
              <el-option v-for="t in themes" :key="t.id" :label="`${t.themeName} (${t.libraryKind})`" :value="t.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="物理表" class="portal-field-lg">
            <el-input v-model="manageForm.physicalTable" placeholder="dws_xxx" />
          </el-form-item>
          <el-form-item label="元数据码" class="portal-field-lg">
            <el-input v-model="manageForm.metaEntryCode" placeholder="TBL_FUS_..." />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="manageTable">纳管</el-button>
            <el-button @click="loadCandidates">刷新候选</el-button>
          </el-form-item>
        </el-form>
        <el-table v-if="candidates.length" :data="candidates" stripe size="small" style="margin-bottom:12px">
          <el-table-column prop="physicalTable" label="产出表" />
          <el-table-column prop="entryCode" label="元数据码" width="220" />
          <el-table-column label="已纳管" width="80">
            <template #default="{ row }">{{ row.managed ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="primary" @click="fillCandidate(row)">选用</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-table :data="managedTables" stripe size="small">
          <el-table-column prop="physicalTable" label="物理表" />
          <el-table-column prop="themeName" label="所属库" />
          <el-table-column prop="recordCount" label="行数" width="90" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="runBackup(row.id)">备份</el-button>
              <el-button link @click="unmanageTable(row.id)">解绑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </PageCard>

      <PageCard v-if="tab === 'partition' && partOverview">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="策略名" class="portal-field-md">
            <el-input v-model="partitionForm.partitionName" />
          </el-form-item>
          <el-form-item label="类型" class="portal-field-sm">
            <el-select v-model="partitionForm.partitionType">
              <el-option label="RANGE" value="RANGE" />
              <el-option label="HASH" value="HASH" />
              <el-option label="LIST" value="LIST" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标表" class="portal-field-lg">
            <el-input v-model="partitionForm.tableName" />
          </el-form-item>
          <el-form-item label="分区列" class="portal-field-md">
            <el-input v-model="partitionForm.partitionColumn" />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="createPartition">新增策略</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="(partOverview.partitions as Partition[])" stripe size="small">
          <el-table-column prop="partitionName" label="分区策略" />
          <el-table-column prop="partitionType" label="类型" width="90" />
          <el-table-column prop="tableName" label="目标表" width="180" />
          <el-table-column label="预检" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.pretestStatus)">{{ statusLabel(row.pretestStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button link type="primary" @click="pretestPartition(row.id)">预检</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-alert
          v-if="pretestResult"
          type="info"
          :closable="false"
          style="margin-top:12px"
          :title="String(pretestResult.pretestMessage || '预检完成')"
        >
          <pre style="white-space:pre-wrap;margin:8px 0 0">{{ pretestResult.previewDdl }}</pre>
        </el-alert>

        <el-divider content-position="left">存储策略</el-divider>
        <el-table :data="(partOverview.policies as Policy[])" stripe size="small">
          <el-table-column prop="policyName" label="策略" />
          <el-table-column prop="actionType" label="动作" width="100">
            <template #default="{ row }">{{ statusLabel(row.actionType) }}</template>
          </el-table-column>
          <el-table-column prop="retentionDays" label="保留天" width="80" />
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button link @click="runPolicy(row.id)">执行</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-divider content-position="left">备份历史</el-divider>
        <el-table :data="(partOverview.artifacts as Artifact[])" stripe size="small">
          <el-table-column prop="physicalTable" label="表" width="180" />
          <el-table-column prop="fileName" label="文件" />
          <el-table-column prop="rowCount" label="行数" width="80" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="primary" @click="verifyArtifact(row.id)">校验</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-alert
          v-if="verifyResult"
          type="success"
          :closable="false"
          style="margin-top:12px"
          :title="verifyResult.match ? 'SHA-256 校验通过' : 'SHA-256 校验失败'"
        />
      </PageCard>

      <PageCard v-if="tab === 'catalog'">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="名称" class="portal-field-md">
            <el-input v-model="catalogForm.entryName" />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="createCatalog">编目</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="catalogEntries" stripe>
          <el-table-column prop="entryCode" label="编码" width="140" />
          <el-table-column prop="entryName" label="名称" />
          <el-table-column prop="driveTask" label="驱动任务" />
        </el-table>
      </PageCard>

      <PageCard v-if="tab === 'analytics' && stats">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="总记录">{{ stats.totalRecords }}</el-descriptions-item>
          <el-descriptions-item label="库数量">{{ stats.libraryCount }}</el-descriptions-item>
          <el-descriptions-item label="主题库">{{ stats.themeCount }}</el-descriptions-item>
          <el-descriptions-item label="纳管表">{{ stats.managedTableCount }}</el-descriptions-item>
        </el-descriptions>
        <el-form inline class="portal-inline-form" style="margin-top:12px">
          <el-form-item class="portal-field-xl">
            <el-input v-model="searchQ" placeholder="库/纳管表检索" @keyup.enter="doSearch" />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button @click="doSearch">M136 检索</el-button>
          </el-form-item>
        </el-form>
      </PageCard>

      <PageCard v-if="tab === 'monitor'">
        <el-button type="primary" style="margin-bottom:12px" @click="refreshMonitor">刷新真实容量</el-button>
        <el-table :data="monitor" stripe size="small">
          <el-table-column prop="metricLabel" label="指标" />
          <el-table-column prop="metricValue" label="值" />
          <el-table-column label="级别" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.alertLevel)">{{ statusLabel(row.alertLevel) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </PageCard>
    </HubSideLayout>
  </div>
</template>
