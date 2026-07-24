<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'
import HubSideLayout from '@/components/common/HubSideLayout.vue'

const navItems = [
  { key: 'classify', label: 'M123 分类' },
  { key: 'files', label: 'M124-M125 文件' },
  { key: 'metadata', label: 'M126 元数据' },
  { key: 'process', label: 'M127-M129 处理' },
]

interface Category { id: number; categoryCode: string; categoryName: string; mediaType: string }
interface Doc { id: number; docCode: string; title: string; storageKey: string; indexStatus: string; publishStatus: string; processStatus?: string; tagJson?: string }
interface Pipeline { id: number; docId: number; pipelineType: string; status: string; resultMessage?: string }

const route = useRoute()
const router = useRouter()
const tabMap: Record<string, string> = {
  classify: 'classify', m123: 'classify',
  files: 'files', m124: 'files', m125: 'files',
  metadata: 'metadata', m126: 'metadata',
  process: 'process', m127: 'process', m129: 'process',
}
const tab = ref('classify')
const overview = ref<Record<string, unknown> | null>(null)
const categories = ref<Category[]>([])
const docs = ref<Doc[]>([])
const pipelines = ref<Pipeline[]>([])
const searchQ = ref('')
const searchResult = ref<Record<string, unknown> | null>(null)
const docForm = reactive({ title: '', contentType: 'application/pdf', categoryCode: 'CAT_GOV_DOC' })
const catForm = reactive({ categoryName: '', mediaType: 'DOCUMENT' })

function resolveTab() {
  tab.value = tabMap[String(route.query.tab || 'classify').toLowerCase()] || 'classify'
}

async function loadClassify() {
  categories.value = (await api.get('/unstructured/platform/categories')).data
}
async function loadFiles() {
  docs.value = (await api.get('/unstructured/platform/documents')).data
}
async function loadProcess() {
  pipelines.value = (await api.get('/unstructured/platform/pipelines')).data
  docs.value = (await api.get('/unstructured/platform/documents')).data
}
async function loadOverview() {
  overview.value = (await api.get('/unstructured/platform/overview')).data
}

async function loadTabData() {
  try {
    await loadOverview()
    if (tab.value === 'classify') await loadClassify()
    if (tab.value === 'files' || tab.value === 'metadata') await loadFiles()
    if (tab.value === 'process') await loadProcess()
  } catch { ElMessage.error('加载失败') }
}

async function registerDoc() {
  if (!docForm.title) return
  await api.post('/unstructured/platform/documents', docForm)
  ElMessage.success('文档已登记')
  docForm.title = ''
  await loadFiles()
}
async function publishDoc(id: number) {
  await api.post(`/unstructured/platform/documents/${id}/publish`)
  ElMessage.success('已发布')
  await loadFiles()
}
async function indexDoc(id: number) {
  await api.post(`/unstructured/platform/documents/${id}/index`)
  ElMessage.success('已建索引')
  await loadFiles()
}
async function runPipe(id: number, type: string) {
  const res = await api.post(`/unstructured/platform/documents/${id}/pipeline/${type}`)
  ElMessage.success(res.data.message)
  await loadProcess()
}
async function doSearch() {
  searchResult.value = (await api.get('/unstructured/platform/search', { params: { q: searchQ.value } })).data
}
async function addCategory() {
  if (!catForm.categoryName) return
  await api.post('/unstructured/platform/categories', catForm)
  ElMessage.success('分类已创建')
  catForm.categoryName = ''
  await loadClassify()
}

const tabTitle = computed(() => ({
  classify: 'M123 数据分类', files: 'M124-M125 文件管理/检索', metadata: 'M126 元数据', process: 'M127-M129 清洗/标识/关联',
}[tab.value] || '非结构化治理'))

watch(tab, () => { router.replace({ query: { ...route.query, tab: tab.value } }); loadTabData() })
watch(() => route.query.tab, resolveTab)
onMounted(() => { resolveTab(); loadTabData() })
</script>

<template>
  <div>
    <PageHeader :title="`非结构化治理 · ${tabTitle}`" description="M123～M129：SeaweedFS 存储 + ES 检索 + 清洗打标关联" />
    <el-descriptions v-if="overview" :column="4" border size="small" style="margin-bottom:12px">
      <el-descriptions-item label="分类">{{ overview.categories }}</el-descriptions-item>
      <el-descriptions-item label="文档">{{ overview.documents }}</el-descriptions-item>
      <el-descriptions-item label="已索引">{{ overview.indexed }}</el-descriptions-item>
      <el-descriptions-item label="Seaweed/ES">{{ overview.seaweedHealthy }}/{{ overview.esHealthy }}</el-descriptions-item>
    </el-descriptions>
    <HubSideLayout v-model="tab" :items="navItems">
      <PageCard v-if="tab === 'classify'">
          <el-form inline>
            <el-form-item label="分类名"><el-input v-model="catForm.categoryName" /></el-form-item>
            <el-button type="primary" @click="addCategory">新增</el-button>
          </el-form>
          <el-table :data="categories" stripe size="small">
            <el-table-column prop="categoryCode" label="编码" width="140" />
            <el-table-column prop="categoryName" label="名称" />
            <el-table-column label="媒介" width="100">
              <template #default="{ row }">{{ $statusLabel(row.mediaType) }}</template>
            </el-table-column>
          </el-table>
      </PageCard>
      <PageCard v-if="tab === 'files'">
          <el-form inline>
            <el-form-item label="标题"><el-input v-model="docForm.title" /></el-form-item>
            <el-button type="primary" @click="registerDoc">登记</el-button>
            <el-input v-model="searchQ" placeholder="检索" style="width:200px;margin-left:8px" @keyup.enter="doSearch" />
            <el-button @click="doSearch">搜索</el-button>
          </el-form>
          <el-table :data="docs" stripe>
            <el-table-column prop="title" label="标题" />
            <el-table-column prop="storageKey" label="存储键" min-width="180" />
            <el-table-column label="发布" width="90">
              <template #default="{ row }">{{ $statusLabel(row.publishStatus) }}</template>
            </el-table-column>
            <el-table-column label="索引" width="90">
              <template #default="{ row }">{{ $statusLabel(row.indexStatus) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button v-if="row.publishStatus!=='PUBLISHED'" link @click="publishDoc(row.id)">发布</el-button>
                <el-button v-if="row.indexStatus!=='INDEXED'" link @click="indexDoc(row.id)">建索</el-button>
              </template>
            </el-table-column>
          </el-table>
      </PageCard>
      <PageCard v-if="tab === 'metadata'">
          <el-table :data="docs" stripe>
            <el-table-column prop="title" label="文档" />
            <el-table-column prop="tagJson" label="标签" />
            <el-table-column label="处理状态" width="110">
              <template #default="{ row }">{{ $statusLabel(row.processStatus) }}</template>
            </el-table-column>
          </el-table>
      </PageCard>
      <PageCard v-if="tab === 'process'">
          <el-table :data="docs" stripe size="small">
            <el-table-column prop="title" label="文档" />
            <el-table-column label="操作" width="260">
              <template #default="{ row }">
                <el-button link @click="runPipe(row.id,'CLEAN')">清洗</el-button>
                <el-button link @click="runPipe(row.id,'TAG')">打标</el-button>
                <el-button link @click="runPipe(row.id,'LINK')">关联</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-table :data="pipelines" stripe size="small" style="margin-top:12px">
            <el-table-column label="类型" width="90">
              <template #default="{ row }">{{ $statusLabel(row.pipelineType) }}</template>
            </el-table-column>
            <el-table-column prop="resultMessage" label="结果" />
          </el-table>
      </PageCard>
    </HubSideLayout>
  </div>
</template>
