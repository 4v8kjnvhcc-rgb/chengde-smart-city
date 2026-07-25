<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import HubSideLayout, { type HubNavItem } from '@/components/common/HubSideLayout.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'

const navItems: HubNavItem[] = [
  { key: 'files', label: '文件资源管理' },
  { key: 'classify', label: '数据分类管理' },
  { key: 'search', label: '文件资源检索' },
  { key: 'metadata', label: '非结构化元数据管理' },
  {
    key: 'process',
    label: '非结构化数据处理',
    children: [
      { key: 'process.clean', label: '非结构化数据清洗转换处理' },
      { key: 'process.tag', label: '非结构化数据标识处理' },
      { key: 'process.link', label: '非结构化数据关联处理' },
    ],
  },
]

interface Category {
  id: number
  categoryCode: string
  categoryName: string
  mediaType: string
  status?: string
}
interface Doc {
  id: number
  docCode: string
  title: string
  storageKey: string
  categoryCode?: string
  contentType?: string
  indexStatus: string
  publishStatus: string
  processStatus?: string
  tagJson?: string
}
interface Pipeline {
  id: number
  docId: number
  pipelineType: string
  status: string
  resultMessage?: string
}
interface SearchHit {
  id: number
  title: string
  categoryCode?: string
  indexStatus?: string
  publishStatus?: string
  tagJson?: string
  storageKey?: string
  source?: string
}

const MEDIA_OPTIONS = [
  { label: '文档', value: 'DOCUMENT', contentType: 'application/pdf' },
  { label: '图片', value: 'IMAGE', contentType: 'image/jpeg' },
  { label: '视频', value: 'VIDEO', contentType: 'video/mp4' },
  { label: '音频', value: 'AUDIO', contentType: 'audio/mpeg' },
] as const

const PROCESS_TYPES = ['CLEAN', 'TAG', 'LINK'] as const
type ProcessType = (typeof PROCESS_TYPES)[number]

const route = useRoute()
const router = useRouter()

/** 兼容旧 query.tab（含 m123～m129）→ 侧栏叶子 key */
const tabMap: Record<string, string> = {
  classify: 'classify', m123: 'classify',
  files: 'files', m124: 'files',
  search: 'search', m125: 'search',
  metadata: 'metadata', m126: 'metadata',
  process: 'process.clean',
  m127: 'process.clean',
  m128: 'process.tag',
  m129: 'process.link',
  'process.clean': 'process.clean',
  'process.tag': 'process.tag',
  'process.link': 'process.link',
}

const DEFAULT_NAV = 'files'
const activeNav = ref(DEFAULT_NAV)
let applyingRoute = false
let overviewLoaded = false

const overview = ref<Record<string, unknown> | null>(null)
const categories = ref<Category[]>([])
const docs = ref<Doc[]>([])
const pipelines = ref<Pipeline[]>([])
const searchQ = ref('')
const searchCategory = ref<string>('')
const searchHits = ref<SearchHit[]>([])
const searchEsHealthy = ref<boolean | null>(null)
const searchDone = ref(false)

const catForm = reactive({ categoryName: '', mediaType: 'DOCUMENT' })
const docForm = reactive({
  title: '',
  categoryCode: '' as string,
  mediaHint: 'DOCUMENT',
  contentType: 'application/pdf',
})
const metaDialogVisible = ref(false)
const metaEdit = reactive({ id: 0, title: '', tagJson: '' })

const processType = computed<ProcessType>(() => {
  if (activeNav.value === 'process.tag') return 'TAG'
  if (activeNav.value === 'process.link') return 'LINK'
  return 'CLEAN'
})

const pageTitle = computed(() => {
  const leaf = activeNav.value
  if (leaf.startsWith('process.')) {
    const child = navItems.find((n) => n.key === 'process')?.children?.find((c) => c.key === leaf)
    return child?.label || '非结构化数据处理'
  }
  return navItems.find((n) => n.key === leaf)?.label || '非结构数据融合治理平台'
})

function resolveFromRoute() {
  applyingRoute = true
  const q = String(route.query.tab || 'classify').toLowerCase()
  activeNav.value = tabMap[q] || DEFAULT_NAV
  if (activeNav.value === 'process') activeNav.value = 'process.clean'
  nextTick(() => { applyingRoute = false })
}

function syncQuery() {
  const q: Record<string, string> = {}
  for (const [k, v] of Object.entries(route.query)) {
    if (v == null || k === 'tab') continue
    q[k] = Array.isArray(v) ? String(v[0]) : String(v)
  }
  q.tab = activeNav.value
  router.replace({ query: q })
}

async function loadOverview() {
  overview.value = (await api.get('/unstructured/platform/overview')).data
  overviewLoaded = true
}

async function loadCategories() {
  categories.value = (await api.get('/unstructured/platform/categories')).data || []
}

async function loadDocuments() {
  docs.value = (await api.get('/unstructured/platform/documents')).data || []
}

async function loadPipelines(type: ProcessType) {
  pipelines.value = (await api.get('/unstructured/platform/pipelines', {
    params: { pipelineType: type },
  })).data || []
}

async function loadTabData() {
  try {
    if (!overviewLoaded) await loadOverview()
    const nav = activeNav.value
    if (nav === 'classify') {
      await loadCategories()
    } else if (nav === 'files') {
      await Promise.all([loadDocuments(), loadCategories()])
    } else if (nav === 'search') {
      await loadCategories()
      // 不预加载文档列表；检索由用户触发
    } else if (nav === 'metadata') {
      await loadDocuments()
    } else if (nav.startsWith('process.')) {
      await Promise.all([loadPipelines(processType.value), loadDocuments()])
    }
  } catch {
    ElMessage.error('加载失败')
  }
}

async function addCategory() {
  if (!catForm.categoryName.trim()) {
    ElMessage.warning('请填写分类名称')
    return
  }
  await api.post('/unstructured/platform/categories', {
    categoryName: catForm.categoryName.trim(),
    mediaType: catForm.mediaType,
  })
  ElMessage.success('分类已创建')
  catForm.categoryName = ''
  await loadCategories()
  overviewLoaded = false
  await loadOverview()
}

async function renameCategory(row: Category) {
  try {
    const { value } = await ElMessageBox.prompt('请输入新的分类名称', '重命名分类', {
      inputValue: row.categoryName,
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValidator: (v) => (!!v && !!String(v).trim()) || '名称不能为空',
    })
    await api.put(`/unstructured/platform/categories/${row.id}`, {
      categoryName: String(value).trim(),
      mediaType: row.mediaType,
    })
    ElMessage.success('已重命名')
    await loadCategories()
  } catch { /* cancel */ }
}

async function deleteCategory(row: Category) {
  try {
    await ElMessageBox.confirm(`确认删除分类「${row.categoryName}」？`, '删除分类', { type: 'warning' })
    await api.delete(`/unstructured/platform/categories/${row.id}`)
    ElMessage.success('已删除')
    await loadCategories()
    overviewLoaded = false
    await loadOverview()
  } catch { /* cancel */ }
}

function onMediaHintChange(hint: string) {
  const opt = MEDIA_OPTIONS.find((m) => m.value === hint)
  if (opt) docForm.contentType = opt.contentType
}

async function registerDoc() {
  if (!docForm.title.trim()) {
    ElMessage.warning('请填写文档标题')
    return
  }
  if (!docForm.categoryCode) {
    ElMessage.warning('请选择文件分类')
    return
  }
  await api.post('/unstructured/platform/documents', {
    title: docForm.title.trim(),
    categoryCode: docForm.categoryCode,
    contentType: docForm.contentType,
  })
  ElMessage.success('文档已登记')
  docForm.title = ''
  await loadDocuments()
  overviewLoaded = false
  await loadOverview()
}

async function publishDoc(id: number) {
  await api.post(`/unstructured/platform/documents/${id}/publish`)
  ElMessage.success('已发布')
  await loadDocuments()
}

async function offlineDoc(id: number) {
  await api.post(`/unstructured/platform/documents/${id}/offline`)
  ElMessage.success('已下线')
  await loadDocuments()
}

async function indexDoc(id: number) {
  await api.post(`/unstructured/platform/documents/${id}/index`)
  ElMessage.success('已建索引')
  await loadDocuments()
  overviewLoaded = false
  await loadOverview()
}

async function doSearch() {
  try {
    const res = await api.get('/unstructured/platform/search', {
      params: {
        q: searchQ.value || undefined,
        categoryCode: searchCategory.value || undefined,
      },
    })
    const data = res.data || {}
    searchHits.value = (data.hits as SearchHit[]) || []
    searchEsHealthy.value = data.esHealthy == null ? null : !!data.esHealthy
    searchDone.value = true
    ElMessage.success(`检索完成，命中 ${searchHits.value.length} 条`)
  } catch {
    ElMessage.error('检索失败')
  }
}

function openMetaEdit(row: Doc) {
  metaEdit.id = row.id
  metaEdit.title = row.title
  metaEdit.tagJson = row.tagJson || '[]'
  metaDialogVisible.value = true
}

async function saveMetadata() {
  if (!metaEdit.id) return
  await api.put(`/unstructured/platform/documents/${metaEdit.id}/metadata`, {
    tagJson: metaEdit.tagJson,
    title: metaEdit.title,
  })
  ElMessage.success('元数据已更新')
  metaDialogVisible.value = false
  metaEdit.id = 0
  await loadDocuments()
}

async function runPipe(docId: number) {
  const type = processType.value
  const res = await api.post(`/unstructured/platform/documents/${docId}/pipeline/${type}`)
  ElMessage.success(String(res.data?.message || '处理完成'))
  await Promise.all([loadPipelines(type), loadDocuments()])
}

const processActionLabel = computed(() => statusLabel(processType.value))

watch(activeNav, () => {
  if (!applyingRoute) syncQuery()
  loadTabData()
})
watch(() => route.query.tab, () => { resolveFromRoute() })
onMounted(() => {
  resolveFromRoute()
  // 若 query 与默认侧栏相同，activeNav 不变不会触发 watch，需主动加载
  loadTabData()
})
</script>

<template>
  <div class="uns-hub-root">
    <HubSideLayout v-model="activeNav" :items="navItems">
      <PageCard v-if="activeNav === 'classify'" title="数据分类管理">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="分类名" class="portal-field-md">
            <el-input v-model="catForm.categoryName" placeholder="如：政务公文" />
          </el-form-item>
          <el-form-item label="媒介类型" class="portal-field-sm">
            <el-select v-model="catForm.mediaType">
              <el-option v-for="m in MEDIA_OPTIONS" :key="m.value" :label="m.label" :value="m.value" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="addCategory">新增分类</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="categories" stripe size="small">
          <el-table-column prop="categoryCode" label="编码" width="160" />
          <el-table-column prop="categoryName" label="名称" min-width="140" />
          <el-table-column label="媒介" width="100">
            <template #default="{ row }">{{ statusLabel(row.mediaType) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="renameCategory(row)">重命名</el-button>
              <el-button link type="danger" @click="deleteCategory(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </PageCard>

      <PageCard v-else-if="activeNav === 'files'" title="文件资源管理">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="标题" class="portal-field-lg">
            <el-input v-model="docForm.title" placeholder="文档标题" />
          </el-form-item>
          <el-form-item label="分类" class="portal-field-lg">
            <el-select v-model="docForm.categoryCode" placeholder="选择已登记分类" filterable>
              <el-option
                v-for="c in categories"
                :key="c.id"
                :label="c.categoryName"
                :value="c.categoryCode"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="媒介类型" class="portal-field-sm">
            <el-select v-model="docForm.mediaHint" @change="onMediaHintChange">
              <el-option v-for="m in MEDIA_OPTIONS" :key="m.value" :label="m.label" :value="m.value" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="registerDoc">登记</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="docs" stripe size="small">
          <el-table-column prop="title" label="标题" min-width="140" />
          <el-table-column prop="categoryCode" label="分类编码" width="140" />
          <el-table-column prop="storageKey" label="存储键" min-width="180" show-overflow-tooltip />
          <el-table-column label="发布" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.publishStatus)" size="small">
                {{ statusLabel(row.publishStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="索引" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.indexStatus)" size="small">
                {{ statusLabel(row.indexStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220">
            <template #default="{ row }">
              <el-button
                v-if="row.publishStatus !== 'PUBLISHED'"
                link
                type="primary"
                @click="publishDoc(row.id)"
              >发布</el-button>
              <el-button
                v-else
                link
                @click="offlineDoc(row.id)"
              >下线</el-button>
              <el-button
                v-if="row.indexStatus !== 'INDEXED'"
                link
                type="primary"
                @click="indexDoc(row.id)"
              >建索</el-button>
            </template>
          </el-table-column>
        </el-table>
      </PageCard>

      <PageCard v-else-if="activeNav === 'search'" title="文件资源检索">
        <el-alert
          v-if="searchEsHealthy !== null || overview"
          :type="(searchEsHealthy ?? overview?.esHealthy) ? 'success' : 'warning'"
          :closable="false"
          show-icon
          style="margin-bottom:12px"
          :title="(searchEsHealthy ?? overview?.esHealthy)
            ? 'Elasticsearch 可用，优先走检索引擎'
            : 'Elasticsearch 不可用，将降级为数据库检索'"
        />
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="关键词" class="portal-field-xl">
            <el-input v-model="searchQ" placeholder="标题/内容关键词" @keyup.enter="doSearch" />
          </el-form-item>
          <el-form-item label="分类" class="portal-field-lg">
            <el-select v-model="searchCategory" clearable placeholder="全部分类">
              <el-option
                v-for="c in categories"
                :key="c.id"
                :label="c.categoryName"
                :value="c.categoryCode"
              />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="doSearch">检索</el-button>
          </el-form-item>
        </el-form>
        <el-table v-if="searchDone" :data="searchHits" stripe size="small">
          <el-table-column prop="title" label="标题" min-width="160" />
          <el-table-column prop="categoryCode" label="分类" width="140" />
          <el-table-column label="发布" width="100">
            <template #default="{ row }">{{ statusLabel(row.publishStatus) }}</template>
          </el-table-column>
          <el-table-column label="索引" width="100">
            <template #default="{ row }">{{ statusLabel(row.indexStatus) }}</template>
          </el-table-column>
          <el-table-column prop="source" label="来源" width="120">
            <template #default="{ row }">
              {{ row.source === 'elasticsearch' ? '检索引擎' : '数据库' }}
            </template>
          </el-table-column>
          <el-table-column prop="tagJson" label="标签" min-width="120" show-overflow-tooltip />
        </el-table>
        <el-empty v-else description="输入条件后点击检索" />
      </PageCard>

      <PageCard v-else-if="activeNav === 'metadata'" title="非结构化元数据管理">
        <el-table :data="docs" stripe size="small">
          <el-table-column prop="title" label="文档" min-width="140" />
          <el-table-column prop="tagJson" label="标签 JSON" min-width="200" show-overflow-tooltip />
          <el-table-column label="处理状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.processStatus)" size="small">
                {{ statusLabel(row.processStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="openMetaEdit(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-dialog v-model="metaDialogVisible" title="编辑元数据" width="520px" @closed="metaEdit.id = 0">
          <el-form label-width="80px">
            <el-form-item label="标题">
              <el-input v-model="metaEdit.title" />
            </el-form-item>
            <el-form-item label="标签 JSON">
              <el-input v-model="metaEdit.tagJson" type="textarea" :rows="4" placeholder='["政务","公开"]' />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="metaDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="saveMetadata">保存</el-button>
          </template>
        </el-dialog>
      </PageCard>

      <PageCard
        v-else-if="activeNav.startsWith('process.')"
        :title="pageTitle"
      >
        <p class="uns-process-hint">
          当前仅执行「{{ processActionLabel }}」处理；流水线列表已按类型过滤。
        </p>
        <el-table :data="docs" stripe size="small">
          <el-table-column prop="title" label="文档" min-width="160" />
          <el-table-column label="处理状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.processStatus)" size="small">
                {{ statusLabel(row.processStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" @click="runPipe(row.id)">
                执行{{ processActionLabel }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-divider content-position="left">处理流水线</el-divider>
        <el-table :data="pipelines" stripe size="small">
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ statusLabel(row.pipelineType) }}</template>
          </el-table-column>
          <el-table-column prop="docId" label="文档 ID" width="100" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="resultMessage" label="结果" min-width="200" show-overflow-tooltip />
        </el-table>
      </PageCard>
    </HubSideLayout>
  </div>
</template>

<style scoped>
.uns-hub-root {
  height: calc(100vh - var(--portal-header-height) - 40px);
  min-height: 0;
}
.uns-process-hint {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
