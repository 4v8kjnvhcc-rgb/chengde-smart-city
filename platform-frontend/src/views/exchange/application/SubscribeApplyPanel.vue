<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { statusLabel } from '@/utils/status-label'
import PortalPagination from '@/components/common/PortalPagination.vue'
import ResourceApplyDialog from './ResourceApplyDialog.vue'
import type { ApplyResource } from './ResourceApplyDialog.vue'
import { applyColumnsFromDetail } from './ResourceApplyDialog.vue'
import { addFavorite, fetchFavorites, isFavorited, removeFavorite } from './portal-favorites'

export interface CatalogFacet {
  code?: string
  name: string
  count?: number
  apiCount?: number
  dataCount?: number
}

export interface CatalogRow {
  id: number | string
  catalogCode: string
  title: string
  description?: string
  catalogKind?: string
  catalogOrigin?: string
  themeCode?: string
  themeName?: string
  providerOrg?: string
  shareModes?: string
  resourceType?: string
  resourceTypeLabel?: string
  shareAttr?: string
  openAttr?: string
  applyCount?: number
  visitCount?: number
  hotScore?: number
  updatedAt?: string
  publishedAt?: string
  resourceCount?: number
}

const props = defineProps<{
  themes: CatalogFacet[]
  baseLibraries?: CatalogFacet[]
  applicantOrg?: string
}>()

const emit = defineEmits<{
  submitted: []
}>()

const SHARE_ATTR_OPTS = [
  { value: '', label: '全部' },
  { value: 'CONDITIONAL', label: '有条件共享' },
  { value: 'UNCONDITIONAL', label: '无条件共享' },
  { value: 'NOT_SHARE', label: '不予共享' },
]
const OPEN_ATTR_OPTS = [
  { value: '', label: '全部' },
  { value: 'SOCIAL_OPEN', label: '开放' },
  { value: 'NOT_OPEN', label: '不开放' },
]
const RESOURCE_TYPE_OPTS = [
  { value: '', label: '全部' },
  { value: 'TABLE', label: '库表' },
  { value: 'API', label: '接口' },
  { value: 'FILE', label: '文件' },
]

/** 基础库图标条：仅用门户 home.baseLibraries（来自归集「数据资源分类」），禁止写死兜底 */
const libraries = computed(() => {
  const omitRoot = (list: CatalogFacet[]) =>
    list.filter((t) => t.name && t.name !== '基础资源目录')
  return omitRoot(props.baseLibraries || [])
})

const loading = ref(false)
const keyword = ref('')
const filterTheme = ref('')
const shareAttr = ref('')
const openAttr = ref('')
const resourceType = ref('')
const sortBy = ref<'applyCount' | 'visitCount' | 'updatedAt'>('applyCount')
const sortDir = ref<'asc' | 'desc'>('desc')
const catalogView = ref<'card' | 'table'>('card')
const rows = ref<CatalogRow[]>([])
const selectedIds = ref<(number | string)[]>([])
const page = ref(1)
const pageSize = ref(10)

const pagedRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return rows.value.slice(start, start + pageSize.value)
})

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<Record<string, unknown> | null>(null)
const activeChild = ref(0)

const applyVisible = ref(false)
const applySubmitting = ref(false)
const applyTarget = ref<ApplyResource | null>(null)
const subscribed = ref(false)
const followSubId = ref<number | null>(null)

const activeLib = computed(() => {
  if (!filterTheme.value) return null
  return libraries.value.find(
    (t) => t.code === filterTheme.value || t.name === filterTheme.value,
  ) || null
})

const bannerStats = computed(() => {
  const lib = activeLib.value
  const catalogCount = lib
    ? (lib.dataCount ?? 0) + (lib.apiCount ?? 0) || lib.count || rows.value.length
    : rows.value.length
  const applySum = rows.value.reduce((s, r) => s + (r.applyCount ?? 0), 0)
  const visitSum = rows.value.reduce((s, r) => s + (r.visitCount ?? r.hotScore ?? 0), 0)
  return { catalogCount, applySum, visitSum }
})

function libIcon(name: string) {
  if (name.includes('人口')) return 'person'
  if (name.includes('法人')) return 'org'
  if (name.includes('证照')) return 'id'
  if (name.includes('空间') || name.includes('地理')) return 'pin'
  if (name.includes('宏观') || name.includes('经济')) return 'chart'
  if (name.includes('信用')) return 'shield'
  return 'grid'
}

function shareAttrLabel(code?: string) {
  if (!code) return '—'
  const hit = SHARE_ATTR_OPTS.find((o) => o.value === code)
  if (hit) return hit.label
  if (code === 'OPEN') return '无条件共享'
  return statusLabel(code)
}

function openAttrLabel(code?: string) {
  if (!code) return '—'
  if (code === 'SOCIAL_OPEN' || code === 'OPEN') return '开放'
  if (code === 'NOT_OPEN' || code === 'PARTIAL_OPEN' || code === 'PARTIAL') return '不开放'
  const hit = OPEN_ATTR_OPTS.find((o) => o.value === code)
  return hit?.label || statusLabel(code)
}

function openAttrOk(code?: string) {
  return code === 'SOCIAL_OPEN' || code === 'OPEN'
}

const appliedCatalogIds = ref<Set<string>>(new Set())

function isApplied(id?: number | string | null) {
  if (id == null) return false
  return appliedCatalogIds.value.has(String(id))
}

async function loadApplied() {
  try {
    const res = await api.get('/exchange/portal/subscriptions', { params: { scope: 'mine' } })
    const ids = new Set<string>()
    for (const row of (res.data || []) as Array<{ catalogId?: number | string; status?: string }>) {
      const st = String(row.status || '').toUpperCase()
      if (['PENDING', 'APPROVED'].includes(st) && row.catalogId != null) {
        ids.add(String(row.catalogId))
      }
    }
    appliedCatalogIds.value = ids
  } catch {
    appliedCatalogIds.value = new Set()
  }
}

function originLabel(origin?: string) {
  if (!origin) return '共享交换平台资源'
  const o = origin.toUpperCase()
  if (o === 'INGEST' || o === 'INGESTION') return '共享交换平台资源'
  if (o === 'GOVERNANCE') return '数据目录管理系统'
  return '共享交换平台资源'
}

async function loadCatalog() {
  loading.value = true
  try {
    const res = await api.get('/exchange/portal/catalog', {
      params: {
        keyword: keyword.value || undefined,
        baseCode: filterTheme.value || undefined,
        shareAttr: shareAttr.value || undefined,
        openAttr: openAttr.value || undefined,
        resourceType: resourceType.value || undefined,
        sortBy: sortBy.value,
        sortDir: sortDir.value,
      },
    })
    rows.value = res.data || []
    selectedIds.value = []
    page.value = 1
    await loadApplied()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '加载共享资源失败')
  } finally {
    loading.value = false
  }
}

function selectAll() {
  filterTheme.value = ''
  void loadCatalog()
}

function selectLib(lib: CatalogFacet) {
  // 按基础资源目录（信息资源分类 / 六库）过滤
  filterTheme.value = lib.name || lib.code || ''
  void loadCatalog()
}

function toggleSort(key: 'applyCount' | 'visitCount' | 'updatedAt') {
  if (sortBy.value === key) {
    sortDir.value = sortDir.value === 'desc' ? 'asc' : 'desc'
  } else {
    sortBy.value = key
    sortDir.value = 'desc'
  }
  void loadCatalog()
}

function sortIcon(key: string) {
  if (sortBy.value !== key) return '⇅'
  return sortDir.value === 'asc' ? '↑' : '↓'
}

async function openDetail(row: CatalogRow) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  activeChild.value = 0
  subscribed.value = false
  followSubId.value = null
  try {
    const [res, subRes] = await Promise.all([
      api.get(`/exchange/portal/catalog/${row.id}`),
      api.get('/exchange/portal/subscriptions', { params: { scope: 'mine' } }),
      fetchFavorites('PORTAL'),
    ])
    detail.value = res.data
    const mine = (subRes.data || []) as { id?: number; catalogId?: number; status?: string }[]
    const hit = mine.find(
      (s) => String(s.catalogId) === String(row.id) && ['APPROVED', 'READY', 'PENDING'].includes(String(s.status || '')),
    )
    followSubId.value = hit?.id ?? null
    subscribed.value = !!hit || isFavorited(row.id)
    const idx = rows.value.findIndex((r) => String(r.id) === String(row.id))
    if (idx >= 0 && res.data?.visitCount != null) {
      rows.value[idx] = { ...rows.value[idx], visitCount: Number(res.data.visitCount) }
    }
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '加载详情失败')
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  detailVisible.value = false
  detail.value = null
}

/** 列表「申请」→ 先看详情（库表/接口信息） */
function applyRow(row: CatalogRow) {
  void openDetail(row)
}

/** 详情「资源申请」→ 弹出完整申请单 */
function openApplyForm(row?: CatalogRow | Record<string, unknown> | null) {
  const src = row || detail.value
  if (!src) return
  if (isApplied(src.id as number | string)) {
    ElMessage.warning('该目录已申请，不能再次申请')
    return
  }
  applyTarget.value = {
    id: src.id as number | string,
    title: String(src.title || ''),
    catalogCode: src.catalogCode as string | undefined,
    providerOrg: src.providerOrg as string | undefined,
    shareAttr: src.shareAttr as string | undefined,
    openAttr: src.openAttr as string | undefined,
    updatedAt: src.updatedAt as string | undefined,
    resourceType: (src.resourceType as string) || 'TABLE',
    resourceTypeLabel: src.resourceTypeLabel as string | undefined,
    columns: applyColumnsFromDetail(src as Record<string, unknown>),
  }
  applyVisible.value = true
}

async function submitApplyPayload(payload: Record<string, unknown>) {
  applySubmitting.value = true
  try {
    await api.post('/exchange/portal/subscriptions', payload)
    ElMessage.success('资源申请已提交，可在「个人空间」与治理平台「资源申请订阅」查看')
    if (payload.catalogId != null) {
      appliedCatalogIds.value = new Set([...appliedCatalogIds.value, String(payload.catalogId)])
    } else if (applyTarget.value?.id != null) {
      appliedCatalogIds.value = new Set([...appliedCatalogIds.value, String(applyTarget.value.id)])
    }
    applyVisible.value = false
    emit('submitted')
    await loadCatalog()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '提交失败')
  } finally {
    applySubmitting.value = false
  }
}

function applyDetail() {
  openApplyForm(detail.value)
}

async function toggleSubscribe() {
  if (!detail.value) return
  const id = detail.value.id as number | string
  if (!subscribed.value) {
    try {
      await addFavorite({
        catalogId: String(id),
        title: String(detail.value.title || ''),
        catalogCode: detail.value.catalogCode as string | undefined,
        providerOrg: detail.value.providerOrg as string | undefined,
        resourceType: detail.value.resourceType as string | undefined,
        resourceTypeLabel: detail.value.resourceTypeLabel as string | undefined,
        shareAttr: detail.value.shareAttr as string | undefined,
        openAttr: detail.value.openAttr as string | undefined,
        updatedAt: detail.value.updatedAt as string | undefined,
        source: 'PORTAL',
      })
      subscribed.value = true
      ElMessage.success('已订阅，可在「个人空间 · 我的订阅」查看')
    } catch (e: unknown) {
      ElMessage.error((e as Error)?.message || '订阅失败')
    }
    return
  }
  void ElMessageBox.confirm('确认取消订阅该资源？取消后「我的订阅」中将不再显示。', '取消订阅', {
    type: 'warning',
    confirmButtonText: '取消订阅',
    cancelButtonText: '再想想',
  }).then(async () => {
    if (followSubId.value != null) {
      try {
        await api.post(`/exchange/portal/subscriptions/${followSubId.value}/cancel`)
      } catch (e: unknown) {
        ElMessage.error((e as Error)?.message || '取消失败')
        return
      }
      followSubId.value = null
    }
    try {
      await removeFavorite(id, 'PORTAL')
      subscribed.value = false
      ElMessage.success('已取消订阅')
    } catch (e: unknown) {
      ElMessage.error((e as Error)?.message || '取消失败')
    }
  }).catch(() => undefined)
}

function batchApply() {
  if (!selectedIds.value.length) {
    ElMessage.warning('请先勾选资源')
    return
  }
  const first = rows.value.find((r) => String(r.id) === String(selectedIds.value[0]))
  if (first) void openDetail(first)
}

const detailTables = computed(() => (detail.value?.tables as Record<string, unknown>[]) || [])
const detailApis = computed(() => (detail.value?.apis as Record<string, unknown>[]) || [])
const detailFiles = computed(() => (detail.value?.files as Record<string, unknown>[]) || [])
const currentTable = computed(() => detailTables.value[activeChild.value] || detailTables.value[0])
const currentApi = computed(() => detailApis.value[activeChild.value] || detailApis.value[0])
const currentFile = computed(() => detailFiles.value[activeChild.value] || detailFiles.value[0])

const tableColumns = computed(() => (currentTable.value?.columns as Record<string, unknown>[]) || [])
const apiReqParams = computed(() => (currentApi.value?.requestParams as Record<string, unknown>[]) || [])
const apiRespParams = computed(() => (currentApi.value?.responseParams as Record<string, unknown>[]) || [])

onMounted(() => {
  void loadCatalog()
})

defineExpose({ loadCatalog, openDetail })
</script>

<template>
  <div v-loading="loading" class="share-cat">
    <!-- 详情 -->
    <div v-if="detailVisible" v-loading="detailLoading" class="share-detail">
      <button type="button" class="share-detail__back" @click="closeDetail">← 返回基础资源目录</button>
      <template v-if="detail">
        <div class="share-detail__head">
          <h2 class="share-detail__title">{{ detail.title }}</h2>
          <div class="share-detail__tags">
            <span class="type-tag">{{ detail.resourceTypeLabel || '资源' }}</span>
            <span class="attr-pill attr-pill--ok">✓ {{ shareAttrLabel(String(detail.shareAttr || '')) }}</span>
            <span
              class="attr-pill"
              :class="openAttrOk(String(detail.openAttr || '')) ? 'attr-pill--ok' : 'attr-pill--bad'"
            >
              {{ openAttrOk(String(detail.openAttr || '')) ? '✓' : '✕' }}
              {{ openAttrLabel(String(detail.openAttr || '')) }}
            </span>
          </div>
        </div>

        <section class="detail-sec">
          <h4>基本信息</h4>
          <div class="info-strip">
            <div><em>数据来源</em><span>{{ detail.providerOrg || '—' }}</span></div>
            <div><em>共享属性</em><span class="c-ok">✓ {{ shareAttrLabel(String(detail.shareAttr || '')) }}</span></div>
            <div>
              <em>开放属性</em>
              <span :class="openAttrOk(String(detail.openAttr || '')) ? 'c-ok' : 'c-bad'">
                {{ openAttrOk(String(detail.openAttr || '')) ? '✓' : '✕' }}
                {{ openAttrLabel(String(detail.openAttr || '')) }}
              </span>
            </div>
            <div><em>更新时间</em><span>{{ detail.updatedAt || '—' }}</span></div>
            <div><em>目录来源</em><span>{{ originLabel(String(detail.catalogOrigin || '')) }}</span></div>
          </div>
        </section>

        <section v-if="detail.resourceType === 'TABLE'" class="detail-sec">
          <h4>库表信息</h4>
          <div class="split">
            <aside class="subside">
              <div class="subside__label">库表</div>
              <button
                v-for="(t, i) in detailTables"
                :key="i"
                type="button"
                class="subside__item"
                :class="{ 'is-on': activeChild === i }"
                @click="activeChild = i"
              >{{ t.tableName }}</button>
              <div v-if="!detailTables.length" class="muted">暂无挂接表</div>
            </aside>
            <div v-if="currentTable" class="submain">
              <div class="info-grid">
                <div><em>数据表名称</em>{{ currentTable.tableName }}</div>
                <div><em>目录 Code</em>{{ currentTable.catalogCode || detail.catalogCode }}</div>
                <div><em>数据项摘要</em>{{ currentTable.summary || '—' }}</div>
              </div>
              <p class="sub-title">数据项</p>
              <el-table :data="tableColumns" size="small" stripe border>
                <el-table-column prop="name" label="字段名称" min-width="120" />
                <el-table-column prop="comment" label="字段描述" min-width="120" />
                <el-table-column prop="type" label="字段类型" width="90" />
                <el-table-column prop="length" label="字段长度" width="90" />
                <el-table-column label="是否主键" width="90">
                  <template #default="{ row }">{{ row.pk ? '是' : '否' }}</template>
                </el-table-column>
                <el-table-column label="是否非空" width="90">
                  <template #default="{ row }">{{ row.nullable === false ? '是' : '否' }}</template>
                </el-table-column>
                <el-table-column prop="sensitivity" label="数据敏感级别" width="110" />
              </el-table>
            </div>
          </div>
        </section>

        <section v-else-if="detail.resourceType === 'API'" class="detail-sec">
          <h4>接口</h4>
          <div class="split">
            <aside class="subside">
              <div class="subside__label">接口</div>
              <button
                v-for="(a, i) in detailApis"
                :key="i"
                type="button"
                class="subside__item"
                :class="{ 'is-on': activeChild === i }"
                @click="activeChild = i"
              >{{ a.apiName }}</button>
            </aside>
            <div v-if="currentApi" class="submain">
              <div class="info-grid">
                <div><em>接口名称</em>{{ currentApi.apiName }}</div>
                <div><em>接口编码</em>{{ currentApi.apiCode }}</div>
                <div><em>目录编码</em>{{ currentApi.catalogCode || detail.catalogCode }}</div>
                <div><em>版本</em>{{ currentApi.version || '—' }}</div>
                <div><em>接口目标地址</em>{{ currentApi.targetAddressHint || '资源申请通过后前往个人中心查看' }}</div>
                <div><em>接口请求路径</em>{{ currentApi.requestPath }}</div>
                <div><em>服务请求方法</em>{{ currentApi.httpMethod }}</div>
                <div><em>注册时间</em>{{ currentApi.registeredAt || '—' }}</div>
                <div><em>资源描述</em>{{ currentApi.description || '—' }}</div>
                <div><em>失效时间</em>{{ currentApi.expireAt || '—' }}</div>
              </div>
              <p class="sub-title">请求方式</p>
              <div class="path-box">{{ currentApi.requestPath }}</div>
              <p class="sub-title">请求参数</p>
              <el-table :data="apiReqParams" size="small" stripe border>
                <el-table-column prop="name" label="参数名称" />
                <el-table-column label="是否必填" width="90">
                  <template #default="{ row }">{{ row.required ? '是' : '否' }}</template>
                </el-table-column>
                <el-table-column prop="dataType" label="参数数据类型" width="120" />
                <el-table-column prop="comment" label="参数描述" />
              </el-table>
              <p class="sub-title">响应参数</p>
              <el-table :data="apiRespParams" size="small" stripe border>
                <el-table-column prop="name" label="参数名称" />
                <el-table-column label="是否必填" width="90">
                  <template #default="{ row }">{{ row.required ? '是' : '否' }}</template>
                </el-table-column>
                <el-table-column prop="dataType" label="参数数据类型" width="120" />
                <el-table-column prop="comment" label="参数描述" />
              </el-table>
              <p class="sub-title">成功响应示例</p>
              <pre class="example">{{ JSON.stringify(currentApi.successExample || {}, null, 2) }}</pre>
            </div>
          </div>
        </section>

        <section v-else class="detail-sec">
          <h4>文件信息</h4>
          <div class="split">
            <aside class="subside">
              <div class="subside__label">文件</div>
              <button
                v-for="(f, i) in detailFiles"
                :key="i"
                type="button"
                class="subside__item"
                :class="{ 'is-on': activeChild === i }"
                @click="activeChild = i"
              >{{ f.fileName }}</button>
            </aside>
            <div v-if="currentFile" class="submain">
              <div class="info-grid">
                <div><em>文件名称</em>{{ currentFile.fileName }}</div>
                <div><em>文件编码</em>{{ currentFile.fileCode }}</div>
                <div><em>目录编码</em>{{ currentFile.catalogCode || detail.catalogCode }}</div>
                <div><em>文件格式</em>{{ currentFile.format }}</div>
                <div><em>文件大小</em>{{ currentFile.size }}</div>
                <div><em>更新周期</em>{{ currentFile.updateCycle || detail.updateCycle || '—' }}</div>
                <div><em>存放方式</em>{{ currentFile.storage || 'FTP' }}</div>
                <div><em>文件地址</em>{{ currentFile.addressHint || '资源申请通过后前往个人中心查看 FTP 地址' }}</div>
                <div><em>注册时间</em>{{ currentFile.registeredAt || '—' }}</div>
                <div><em>资源描述</em>{{ currentFile.description || detail.description || '—' }}</div>
              </div>
              <p class="sub-title">文件清单</p>
              <el-table :data="detailFiles" size="small" stripe border>
                <el-table-column prop="fileName" label="文件名" min-width="140" />
                <el-table-column prop="format" label="格式" width="80" />
                <el-table-column prop="size" label="大小" width="90" />
                <el-table-column prop="registeredAt" label="更新日期" width="160" />
                <el-table-column prop="description" label="说明" min-width="140" />
              </el-table>
            </div>
          </div>
        </section>

        <div class="detail-actions">
          <el-button
            :type="subscribed ? 'default' : 'primary'"
            plain
            @click="toggleSubscribe"
          >{{ subscribed ? '已订阅' : '订阅' }}</el-button>
          <el-button
            type="primary"
            :disabled="isApplied(detail.id as number | string)"
            @click="applyDetail"
          >
            {{ isApplied(detail.id as number | string) ? '已申请' : '资源申请' }}
          </el-button>
        </div>
      </template>
    </div>

    <!-- 列表：六库图标 + 浅色横幅 + 卡片/表格 + 申请记录 -->
    <div v-else class="share-browse">
      <nav class="lib-bar" aria-label="基础资源分类">
        <button
          type="button"
          class="lib-item"
          :class="{ 'is-on': !filterTheme }"
          @click="selectAll"
        >
          <span class="lib-ico lib-ico--grid" />
          <em>全部</em>
        </button>
        <button
          v-for="lib in libraries"
          :key="lib.code || lib.name"
          type="button"
          class="lib-item"
          :class="{ 'is-on': filterTheme === lib.code || filterTheme === lib.name }"
          @click="selectLib(lib)"
        >
          <span class="lib-ico" :class="'lib-ico--' + libIcon(lib.name)" />
          <em>{{ lib.name }}</em>
        </button>
      </nav>

      <div class="lib-banner lib-banner--metrics-only">
        <div class="lib-banner__metrics">
          <div class="metric">
            <b>{{ bannerStats.catalogCount }}</b>
            <span>目录数</span>
          </div>
          <div class="metric">
            <b>{{ bannerStats.applySum }}</b>
            <span>申请量</span>
          </div>
          <div class="metric">
            <b>{{ bannerStats.visitSum }}</b>
            <span>访问量</span>
          </div>
        </div>
      </div>

      <section class="list-main">
        <div class="filter-box">
          <div class="filter-row">
            <span class="filter-label">共享属性</span>
            <button
              v-for="o in SHARE_ATTR_OPTS"
              :key="'s-' + o.value"
              type="button"
              class="chip"
              :class="{ 'is-on': shareAttr === o.value }"
              @click="shareAttr = o.value; loadCatalog()"
            >{{ o.label }}</button>
          </div>
          <div class="filter-row">
            <span class="filter-label">开放属性</span>
            <button
              v-for="o in OPEN_ATTR_OPTS"
              :key="'o-' + o.value"
              type="button"
              class="chip"
              :class="{ 'is-on': openAttr === o.value }"
              @click="openAttr = o.value; loadCatalog()"
            >{{ o.label }}</button>
          </div>
          <div class="filter-row">
            <span class="filter-label">资源类型</span>
            <button
              v-for="o in RESOURCE_TYPE_OPTS"
              :key="'rt-' + o.value"
              type="button"
              class="chip"
              :class="{ 'is-on': resourceType === o.value }"
              @click="resourceType = o.value; loadCatalog()"
            >{{ o.label }}</button>
          </div>
        </div>

        <div class="toolbar">
          <div class="sort-bar">
            <span class="sort-label">排序方式</span>
            <button type="button" class="sort-btn" :class="{ 'is-on': sortBy === 'applyCount' }" @click="toggleSort('applyCount')">
              申请量 <i>{{ sortIcon('applyCount') }}</i>
            </button>
            <button type="button" class="sort-btn" :class="{ 'is-on': sortBy === 'visitCount' }" @click="toggleSort('visitCount')">
              访问量 <i>{{ sortIcon('visitCount') }}</i>
            </button>
            <button type="button" class="sort-btn" :class="{ 'is-on': sortBy === 'updatedAt' }" @click="toggleSort('updatedAt')">
              更新时间 <i>{{ sortIcon('updatedAt') }}</i>
            </button>
            <span class="total">共 <b>{{ rows.length }}</b> 条</span>
          </div>
          <div class="toolbar-right">
            <el-input
              v-model="keyword"
              clearable
              placeholder="请输入关键字"
              class="kw-input"
              @keyup.enter="loadCatalog"
            >
              <template #suffix>
                <el-icon class="kw-search" @click="loadCatalog"><Search /></el-icon>
              </template>
            </el-input>
            <div class="view-toggle">
              <button type="button" :class="{ 'is-on': catalogView === 'table' }" @click="catalogView = 'table'">表格</button>
              <button type="button" :class="{ 'is-on': catalogView === 'card' }" @click="catalogView = 'card'">卡片</button>
            </div>
          </div>
        </div>

        <el-empty v-if="!rows.length" description="没有找到您需要的资源" />

        <div v-else-if="catalogView === 'card'" class="card-list">
          <article v-for="row in pagedRows" :key="String(row.id)" class="res-card" @click="openDetail(row)">
            <div class="res-card__top">
              <div class="res-card__title-wrap">
                <button type="button" class="res-card__title" @click.stop="openDetail(row)">{{ row.title }}</button>
                <span class="type-tag">{{ row.resourceTypeLabel || '资源' }}</span>
              </div>
              <div class="res-card__stats">
                <span class="stat"><i class="stat-ico stat-ico--doc" />申请量 {{ row.applyCount ?? 0 }}</span>
                <span class="stat"><i class="stat-ico stat-ico--eye" />访问量 {{ row.visitCount ?? row.hotScore ?? 0 }}</span>
                <el-button
                  type="primary"
                  size="small"
                  :disabled="isApplied(row.id)"
                  @click.stop="isApplied(row.id) ? undefined : applyRow(row)"
                >
                  {{ isApplied(row.id) ? '已申请' : '申请' }}
                </el-button>
              </div>
            </div>
            <div class="res-card__meta">
              <div>数据来源：{{ row.providerOrg || '—' }}</div>
              <div>目录来源：{{ originLabel(row.catalogOrigin) }}</div>
              <div>共享属性：<span class="c-ok">✓ {{ shareAttrLabel(row.shareAttr) }}</span></div>
              <div>
                开放属性：
                <span :class="openAttrOk(row.openAttr) ? 'c-ok' : 'c-bad'">
                  {{ openAttrOk(row.openAttr) ? '✓' : '✕' }}
                  {{ openAttrLabel(row.openAttr) }}
                </span>
              </div>
              <div>更新时间：{{ row.updatedAt || '—' }}</div>
            </div>
          </article>
        </div>

        <template v-else>
          <div class="batch-bar">
            <el-checkbox
              :model-value="selectedIds.length > 0 && selectedIds.length === pagedRows.length"
              :indeterminate="selectedIds.length > 0 && selectedIds.length < pagedRows.length"
              @change="(v: boolean) => { selectedIds = v ? pagedRows.map(r => r.id) : [] }"
            >全选</el-checkbox>
            <el-button size="small" type="primary" plain @click="batchApply">批量申请</el-button>
          </div>
          <el-table
            :data="pagedRows"
            stripe
            size="small"
            @selection-change="(sel: CatalogRow[]) => { selectedIds = sel.map(s => s.id) }"
          >
            <el-table-column type="selection" width="44" />
            <el-table-column label="资源名称" min-width="180">
              <template #default="{ row }">
                <button type="button" class="link-title" @click="openDetail(row)">{{ row.title }}</button>
                <span class="type-tag type-tag--sm">{{ row.resourceTypeLabel }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="providerOrg" label="数据来源" min-width="140" />
            <el-table-column label="共享属性" width="120">
              <template #default="{ row }">
                <span class="c-ok">{{ shareAttrLabel(row.shareAttr) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="开放属性" width="140">
              <template #default="{ row }">
                <span :class="openAttrOk(row.openAttr) ? 'c-ok' : 'c-bad'">
                  {{ openAttrOk(row.openAttr) ? '✓' : '✕' }} {{ openAttrLabel(row.openAttr) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="申请量" width="90">
              <template #default="{ row }">{{ row.applyCount ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="访问量" width="90">
              <template #default="{ row }">{{ row.visitCount ?? 0 }}</template>
            </el-table-column>
            <el-table-column prop="updatedAt" label="更新时间" width="160" />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDetail(row)">预览</el-button>
                <el-button
                  link
                  type="success"
                  :disabled="isApplied(row.id)"
                  @click="applyRow(row)"
                >
                  {{ isApplied(row.id) ? '已申请' : '申请' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <PortalPagination
          v-if="rows.length"
          v-model:page="page"
          v-model:page-size="pageSize"
          :total="rows.length"
        />
      </section>
    </div>

    <ResourceApplyDialog
      v-model:visible="applyVisible"
      :resource="applyTarget"
      :share-attr-label="shareAttrLabel"
      :open-attr-label="openAttrLabel"
      :default-applicant-org="applicantOrg"
      @submit="submitApplyPayload"
    />
  </div>
</template>

<style scoped>
.share-cat {
  background: transparent;
  min-height: 520px;
}
.share-browse {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.page-head {
  margin-bottom: 12px;
}
.page-head__title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #1f2d3d;
}

/* —— 六库图标条 —— */
.lib-bar {
  display: flex;
  align-items: stretch;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
  background: #fff;
  border-radius: 8px 8px 0 0;
  padding: 16px 12px 12px;
  border: 1px solid #e8edf5;
  border-bottom: 0;
}
.lib-item {
  appearance: none;
  border: 0;
  background: transparent;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  min-width: 88px;
  padding: 8px 10px 10px;
  cursor: pointer;
  border-radius: 8px;
  position: relative;
  color: #606266;
  transition: background 120ms ease, color 120ms ease;
}
.lib-item:hover { background: #f5f9ff; color: #1677ff; }
.lib-item.is-on { color: #1677ff; }
.lib-item.is-on::after {
  content: '';
  position: absolute;
  left: 22%;
  right: 22%;
  bottom: 0;
  height: 3px;
  border-radius: 3px 3px 0 0;
  background: #1677ff;
}
.lib-item em {
  font-style: normal;
  font-size: 13px;
  font-weight: 500;
}
.lib-ico {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 1.5px solid #d6e4f5;
  background: #f7fbff;
  display: block;
  position: relative;
  box-sizing: border-box;
}
.lib-item.is-on .lib-ico {
  border-color: #1677ff;
  background: #e8f3ff;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.12);
}
.lib-ico::before {
  content: '';
  position: absolute;
  inset: 12px;
  border-radius: 2px;
  background: #90caf9;
}
.lib-ico--person::before {
  inset: auto;
  width: 14px; height: 14px; left: 50%; top: 10px;
  transform: translateX(-50%);
  border-radius: 50%;
  background: #42a5f5;
  box-shadow: 0 12px 0 -2px #1565c0, 0 12px 0 4px transparent;
}
.lib-ico--org::before {
  inset: 11px 10px 12px;
  border-radius: 3px;
  background: linear-gradient(180deg, #64b5f6, #1565c0);
}
.lib-ico--id::before {
  inset: 12px 9px;
  border-radius: 3px;
  background: linear-gradient(135deg, #90caf9, #1976d2);
}
.lib-ico--pin::before {
  inset: auto;
  width: 12px; height: 16px; left: 50%; top: 11px;
  transform: translateX(-50%);
  border-radius: 50% 50% 50% 0;
  background: #1976d2;
  rotate: -45deg;
}
.lib-ico--chart::before {
  inset: 12px 10px;
  border-radius: 2px;
  background:
    linear-gradient(#1976d2, #1976d2) 20% 70%/18% 30% no-repeat,
    linear-gradient(#42a5f5, #42a5f5) 50% 50%/18% 50% no-repeat,
    linear-gradient(#1565c0, #1565c0) 80% 40%/18% 60% no-repeat,
    #e3f2fd;
}
.lib-ico--shield::before {
  inset: 10px 12px;
  border-radius: 4px 4px 10px 10px;
  background: linear-gradient(180deg, #64b5f6, #0d47a1);
}
.lib-ico--grid::before {
  inset: 12px;
  border-radius: 3px;
  background:
    linear-gradient(#fff 0 0) center/100% 2px no-repeat,
    linear-gradient(#fff 0 0) center/2px 100% no-repeat,
    #1976d2;
}

/* —— 浅色分类横幅 —— */
.lib-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
  padding: 16px 20px;
  background: #fff;
  border: 1px solid #e8edf5;
  border-top: 0;
}
.lib-banner--metrics-only {
  justify-content: flex-end;
}
.lib-banner__left h2 {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 700;
  color: #1f2d3d;
}
.lib-banner__left p {
  margin: 0;
  font-size: 13px;
  color: #909399;
}
.lib-banner__metrics {
  display: flex;
  gap: 24px;
  padding: 8px 14px;
  background: #f5f9ff;
  border: 1px solid #e8edf5;
  border-radius: 8px;
}
.metric {
  text-align: center;
  min-width: 64px;
}
.metric b {
  display: block;
  font-size: 26px;
  font-weight: 700;
  color: #1677ff;
  line-height: 1.15;
}
.metric span {
  font-size: 12px;
  color: #909399;
}

/* —— 主内容白卡 —— */
.list-main {
  background: #fff;
  border: 1px solid #e8edf5;
  border-top: 0;
  border-radius: 0 0 8px 8px;
  padding: 18px 20px 22px;
  box-shadow: 0 1px 4px rgba(15, 40, 80, 0.04);
}
.list-title { margin: 0 0 12px; font-size: 18px; font-weight: 700; color: #1f2d3d; }
.filter-box {
  border: 1px solid #e5eaf2;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 12px;
  background: #fafbfd;
}
.filter-row { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; margin: 4px 0; }
.filter-label { width: 64px; color: #606266; font-size: 13px; flex-shrink: 0; }
.chip {
  border: 0; background: transparent; padding: 4px 12px; border-radius: 4px;
  cursor: pointer; font-size: 13px; color: #303133;
}
.chip.is-on { background: #1677ff; color: #fff; }

.toolbar {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  flex-wrap: wrap; margin-bottom: 12px;
}
.sort-bar { display: flex; align-items: center; flex-wrap: wrap; gap: 4px; font-size: 13px; }
.sort-label { color: #606266; margin-right: 6px; }
.sort-btn {
  border: 0; background: transparent; color: #606266; cursor: pointer;
  padding: 4px 8px; border-radius: 4px; font-size: 13px;
}
.sort-btn i { font-style: normal; font-size: 11px; opacity: 0.75; margin-left: 2px; }
.sort-btn.is-on { color: #1677ff; font-weight: 600; }
.total { margin-left: 12px; color: #606266; }
.total b { color: #303133; }
.toolbar-right { display: flex; align-items: center; gap: 10px; }
.kw-input { width: 200px; }
.kw-search { cursor: pointer; color: #909399; }
.view-toggle {
  display: inline-flex; border: 1px solid #dcdfe6; border-radius: 4px; overflow: hidden;
}
.view-toggle button {
  border: 0; background: #fff; height: 32px; padding: 0 14px; cursor: pointer; font-size: 13px; color: #606266;
}
.view-toggle button.is-on { background: #1677ff; color: #fff; }

.card-list { display: flex; flex-direction: column; }
.res-card {
  border: 1px solid #eef1f6;
  border-radius: 8px;
  padding: 16px 16px 16px 18px;
  margin-bottom: 10px;
  cursor: pointer;
  background: #fff;
  border-left: 3px solid #1677ff;
  transition: background 120ms ease, box-shadow 120ms ease;
}
.res-card:hover {
  background: #f7fbff;
  box-shadow: 0 2px 10px rgba(22, 119, 255, 0.08);
}
.res-card__top { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
.res-card__title-wrap { display: flex; align-items: center; gap: 8px; min-width: 0; }
.res-card__title {
  border: 0; background: transparent; padding: 0; cursor: pointer;
  color: #1677ff; font-size: 16px; font-weight: 700; text-align: left;
}
.res-card__title:hover { text-decoration: underline; }
.res-card__stats { display: flex; align-items: center; gap: 14px; flex-shrink: 0; }
.stat {
  display: inline-flex; align-items: center; gap: 4px;
  color: #909399; font-size: 12px; white-space: nowrap;
}
.stat-ico {
  width: 14px; height: 14px; border-radius: 2px; display: inline-block;
  background: #c0c4cc; flex-shrink: 0;
}
.stat-ico--doc {
  background:
    linear-gradient(#fff 0 0) center/60% 2px no-repeat,
    linear-gradient(#90caf9, #1565c0);
}
.stat-ico--eye {
  border-radius: 50%;
  background: radial-gradient(circle at 50% 50%, #fff 0 2px, transparent 3px), linear-gradient(135deg, #90caf9, #1565c0);
}
.res-card__meta {
  margin-top: 12px; display: grid; grid-template-columns: 1fr 1fr;
  gap: 6px 20px; font-size: 13px; color: #606266;
}
.type-tag {
  display: inline-block; font-size: 12px; padding: 1px 7px; border-radius: 3px;
  background: #e8f3ff; color: #1677ff; font-weight: 500;
}
.type-tag--sm { margin-left: 6px; font-size: 11px; }
.c-ok { color: #18a058; }
.c-bad { color: #d03050; }
.link-title {
  border: 0; background: transparent; color: #1677ff; cursor: pointer; padding: 0; font-size: 13px;
}
.batch-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; }
.muted { color: #909399; font-size: 12px; padding: 8px; }

.share-detail {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e8edf5;
  padding: 16px 20px 24px;
}
.share-detail__back {
  border: 0; background: transparent; color: #1677ff; cursor: pointer;
  font-size: 13px; padding: 0; margin-bottom: 10px;
}
.share-detail__title { margin: 0 0 8px; font-size: 22px; }
.share-detail__tags { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
.attr-pill {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 12px; padding: 2px 8px; border-radius: 999px; background: #f4f4f5; color: #606266;
}
.attr-pill--ok { background: #e8f8ef; color: #18a058; }
.attr-pill--partial { background: #fff7e6; color: #d48806; }
.attr-pill--bad { background: #ffece8; color: #d03050; }
.detail-sec { margin-top: 18px; }
.detail-sec h4 {
  margin: 0 0 10px; font-size: 15px; border-left: 3px solid #1677ff; padding-left: 8px;
}
.info-strip {
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px;
  background: #fafafa; border: 1px solid #eef1f6; border-radius: 6px; padding: 12px;
  font-size: 13px;
}
.info-strip em, .info-grid em {
  display: block; font-style: normal; color: #909399; font-size: 12px; margin-bottom: 2px;
}
.info-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 10px 16px;
  background: #fafafa; border: 1px solid #eef1f6; border-radius: 6px; padding: 12px; font-size: 13px;
}
.split { display: flex; gap: 12px; }
.subside {
  width: 160px; flex-shrink: 0; border: 1px solid #eef1f6; border-radius: 6px;
  padding: 8px; background: #fafbfd;
}
.subside__label { font-size: 12px; color: #909399; margin-bottom: 6px; }
.subside__item {
  width: 100%; border: 0; background: transparent; text-align: left;
  padding: 8px; border-radius: 4px; cursor: pointer; font-size: 13px; color: #303133;
  word-break: break-all;
}
.subside__item.is-on { background: #e8f3ff; color: #1677ff; }
.submain { flex: 1; min-width: 0; }
.sub-title { margin: 14px 0 6px; font-size: 13px; font-weight: 600; }
.path-box {
  background: #f5f7fa; border: 1px solid #ebeef5; border-radius: 4px;
  padding: 8px 10px; font-size: 12px; word-break: break-all;
}
.example {
  background: #f5f7fa;
  color: #303133;
  border: 1px solid #e4e7ed;
  padding: 12px;
  border-radius: 6px;
  font-size: 12px;
  overflow: auto;
  margin: 0;
  font-family: Consolas, "Courier New", monospace;
  line-height: 1.5;
}
.detail-actions {
  display: flex; justify-content: flex-end; gap: 10px;
  margin-top: 20px; padding-top: 14px; border-top: 1px solid #eef1f6;
}

@media (max-width: 960px) {
  .lib-banner { flex-direction: column; align-items: flex-start; }
  .info-strip { grid-template-columns: 1fr 1fr; }
  .res-card__meta { grid-template-columns: 1fr; }
  .res-card__top { flex-direction: column; }
}
</style>
