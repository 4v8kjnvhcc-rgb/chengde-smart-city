<script setup lang="ts">
import { computed, onActivated, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { statusLabel } from '@/utils/status-label'
import ResourceApplyDialog from '@/views/exchange/application/ResourceApplyDialog.vue'
import type { ApplyResource } from '@/views/exchange/application/ResourceApplyDialog.vue'
import { applyColumnsFromDetail } from '@/views/exchange/application/ResourceApplyDialog.vue'
import { addFavorite, fetchFavorites, isFavorited, removeFavorite } from '@/views/exchange/application/portal-favorites'

const route = useRoute()
const router = useRouter()

interface CatalogRes {
  id: number
  resourceCode: string
  resourceName: string
  resourceType: string
  categoryPath?: string
  providerOrg?: string
  resourceFormat?: string
  shareType?: string
  openType?: string
  updateCycle?: string
  description?: string
  publishStatus: string
  sourcePathType?: 'DIRECT' | 'PROCESSED'
  portalCatalogId?: number | null
  updatedAt?: string
  extJson?: string | Record<string, unknown>
  physicalTableName?: string
}

type DetailMap = Record<string, unknown>

const SHARE_TYPE_ZH: Record<string, string> = {
  OPEN: '无条件共享',
  UNCONDITIONAL: '无条件共享',
  CONDITIONAL: '有条件共享',
  NOT_SHARE: '不予共享',
}
const OPEN_TYPE_ZH: Record<string, string> = {
  SOCIAL_OPEN: '开放',
  OPEN: '开放',
  PARTIAL_OPEN: '不开放',
  PARTIAL: '不开放',
  NOT_OPEN: '不开放',
}
const TYPE_ZH: Record<string, string> = { DATA: '数据', SERVICE: '服务' }
const FORMAT_ZH: Record<string, string> = {
  DATABASE: '库表',
  API: '接口',
  FILE: '文件',
  OTHER: '其他',
}

const keyword = ref('')
const providerOrg = ref('')
const shareType = ref('')
const resourceFormat = ref('')
const openType = ref('')
const viewMode = ref<'card' | 'table'>('table')
const cards = ref<CatalogRes[]>([])
const {
  page: cardPage,
  pageSize: cardPageSize,
  paged: pagedCards,
  total: cardTotal,
  resetPage: resetCardPage,
} = useClientPager(cards)
const loading = ref(false)

/** 详情（与政务共享资源一致）→ 再点资源申请弹窗 */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<DetailMap | null>(null)
const applySource = ref<CatalogRes | null>(null)
const activeChild = ref(0)
const subscribed = ref(false)

const applyVisible = ref(false)
const applyTarget = ref<ApplyResource | null>(null)
/** 本部门/本人已申请（待审/已通过/已分发）的资源 id，不可再次申请 */
const appliedResourceIds = ref<Set<number>>(new Set())

const providerOptions = computed(() => {
  const set = new Set<string>()
  for (const c of cards.value) {
    if (c.providerOrg) set.add(c.providerOrg)
  }
  return [...set]
})

const detailTables = computed(() => (detail.value?.tables as DetailMap[]) || [])
const detailApis = computed(() => (detail.value?.apis as DetailMap[]) || [])
const detailFiles = computed(() => (detail.value?.files as DetailMap[]) || [])
const currentTable = computed(() => detailTables.value[activeChild.value] || detailTables.value[0])
const currentApi = computed(() => detailApis.value[activeChild.value] || detailApis.value[0])
const currentFile = computed(() => detailFiles.value[activeChild.value] || detailFiles.value[0])
const tableColumns = computed(() => (currentTable.value?.columns as DetailMap[]) || [])
const apiReqParams = computed(() => (currentApi.value?.requestParams as DetailMap[]) || [])
const apiRespParams = computed(() => (currentApi.value?.responseParams as DetailMap[]) || [])

function shareAttrLabel(code?: string) {
  if (!code) return '—'
  return SHARE_TYPE_ZH[code] || statusLabel(code)
}

function openAttrLabel(code?: string) {
  if (!code) return '—'
  return OPEN_TYPE_ZH[code] || statusLabel(code)
}

function openAttrOk(code?: string) {
  return code === 'SOCIAL_OPEN' || code === 'OPEN'
}

function isApplied(row: CatalogRes) {
  return appliedResourceIds.value.has(row.id)
}

function toApplyResourceType(row: CatalogRes | DetailMap): string {
  if (typeof row.resourceType === 'string' && ['TABLE', 'FILE', 'API'].includes(row.resourceType)) {
    return row.resourceType
  }
  const fmt = String((row as CatalogRes).resourceFormat || row.resourceTypeLabel || '').toUpperCase()
  if (fmt.includes('API') || row.resourceType === 'SERVICE') return 'API'
  if (fmt.includes('FILE')) return 'FILE'
  return 'TABLE'
}

function toGovShareMode(rt?: string) {
  const t = String(rt || 'TABLE').toUpperCase()
  if (t === 'FILE') return 'FILE_SYNC'
  if (t === 'API') return 'API'
  return 'DB_SYNC'
}

function formatUpdatedAt(v?: unknown) {
  if (!v) return undefined
  return String(v).replace('T', ' ').slice(0, 19)
}

function parseExt(raw: unknown): Record<string, unknown> | null {
  if (!raw) return null
  if (typeof raw === 'object') return raw as Record<string, unknown>
  try {
    return JSON.parse(String(raw)) as Record<string, unknown>
  } catch {
    return null
  }
}

/** 无门户目录时，用编目 extJson 拼出与门户详情同结构的数据 */
function buildDetailFromGov(row: CatalogRes, full?: CatalogRes): DetailMap {
  const src = full || row
  const ext = parseExt(src.extJson)
  const rt = toApplyResourceType(src)
  const out: DetailMap = {
    id: src.portalCatalogId || src.id,
    title: src.resourceName,
    catalogCode: src.resourceCode ? `GOV_${src.resourceCode}` : String(src.id),
    providerOrg: src.providerOrg,
    shareAttr: src.shareType,
    openAttr: src.openType,
    updatedAt: formatUpdatedAt(src.updatedAt),
    resourceType: rt,
    resourceTypeLabel: rt === 'API' ? '接口' : rt === 'FILE' ? '文件' : '库表',
    updateCycle: src.updateCycle,
    description: src.description,
  }
  if (rt === 'TABLE') {
    const cols = Array.isArray(ext?.columnList)
      ? (ext!.columnList as Record<string, unknown>[]).map((c) => ({
          name: c.columnName || c.name || '',
          comment: c.columnNameZh || c.remark || c.comment || c.columnName || '',
          type: c.dataTypeZh || c.dataType || c.type || '',
          length: c.length || c.columnLength || c.columnSize || '',
          pk: !!(c.pk || c.primaryKey || c.isPk),
          nullable: c.nullable !== undefined ? !!c.nullable : true,
          sensitivity: c.sensLevel || c.sensitivity || '',
          displayFlag: c.displayFlag !== false,
          searchFlag: !!c.searchFlag,
        }))
      : []
    const tableName = String(ext?.bindTableName || src.physicalTableName || src.resourceName || '—')
    out.tables = [
      {
        tableName,
        catalogCode: out.catalogCode,
        summary: cols.length ? `共 ${cols.length} 个字段` : src.description || '—',
        columns: cols,
      },
    ]
  } else if (rt === 'API' && ext?.api && typeof ext.api === 'object') {
    const api = ext.api as Record<string, unknown>
    out.apis = [
      {
        apiName: api.apiName || src.resourceName,
        apiCode: out.catalogCode,
        catalogCode: out.catalogCode,
        version: api.apiVersion || '',
        targetAddressHint: '资源申请通过后前往个人中心查看',
        requestPath: api.apiPath || api.apiUrl || '',
        httpMethod: api.apiMethod || 'GET',
        registeredAt: api.registerAt || '',
        description: api.apiDescription || src.description || '',
        expireAt: api.expireAt || '',
        requestParams: api.requestParams || [],
        responseParams: api.responseParams || [],
        successExample: {},
      },
    ]
  } else if (rt === 'FILE' && ext?.file && typeof ext.file === 'object') {
    const file = ext.file as Record<string, unknown>
    out.files = [
      {
        fileName: file.fileName || src.resourceName,
        fileCode: out.catalogCode,
        catalogCode: out.catalogCode,
        format: '',
        size: file.fileSize || '',
        updateCycle: src.updateCycle || '',
        storage: 'FTP',
        addressHint: '资源申请通过后前往个人中心查看 FTP 地址',
        registeredAt: formatUpdatedAt(src.updatedAt) || '',
        description: file.fileRemark || src.description || '',
      },
    ]
  }
  return out
}

function matchOpenType(code?: string, filter?: string) {
  if (!filter) return true
  if (!code) return false
  if (code === filter) return true
  if (filter === 'SOCIAL_OPEN' && (code === 'OPEN' || code === 'SOCIAL_OPEN')) return true
  if (filter === 'NOT_OPEN' && (code === 'NOT_OPEN' || code === 'PARTIAL_OPEN' || code === 'PARTIAL')) return true
  return false
}

async function loadApplied() {
  try {
    const res = await api.get('/governance/catalog/subscriptions')
    const ids = new Set<number>()
    for (const row of (res.data || []) as Array<{ resourceId?: number; status?: string }>) {
      const st = String(row.status || '').toUpperCase()
      if (['PENDING', 'APPROVED', 'DISTRIBUTED'].includes(st) && row.resourceId != null) {
        ids.add(Number(row.resourceId))
      }
    }
    appliedResourceIds.value = ids
  } catch {
    appliedResourceIds.value = new Set()
  }
}

async function load() {
  loading.value = true
  try {
    const res = await api.get('/governance/catalog/resources-mgmt', {
      params: {
        publishStatus: 'PUBLISHED',
        forPortal: true,
        keyword: keyword.value.trim() || undefined,
        providerOrg: providerOrg.value || undefined,
        shareType: shareType.value || undefined,
        resourceFormat: resourceFormat.value || undefined,
      },
    })
    const list = (res.data || []) as CatalogRes[]
    cards.value = openType.value
      ? list.filter((c) => matchOpenType(c.openType, openType.value))
      : list
    resetCardPage()
    await loadApplied()
  } catch {
    ElMessage.error('加载目录门户失败')
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  keyword.value = ''
  providerOrg.value = ''
  shareType.value = ''
  resourceFormat.value = ''
  openType.value = ''
  void load()
}

/** 从「我的订阅」等入口带 resourceId 自动打开详情 */
async function tryOpenFromRoute() {
  const rid = route.query.resourceId
  if (rid == null || rid === '') return
  const id = Number(rid)
  if (!Number.isFinite(id)) return
  let row = cards.value.find((c) => c.id === id)
  if (!row) {
    try {
      const res = await api.get(`/governance/catalog/resources-mgmt/${id}`)
      if (res.data) row = res.data as CatalogRes
    } catch {
      return
    }
  }
  if (row) await openDetail(row)
}

/** 第一步：打开资源详情（同政务共享资源详情页） */
async function openDetail(row: CatalogRes) {
  applySource.value = row
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  activeChild.value = 0
  try {
    await fetchFavorites('GOV')
    subscribed.value = isFavorited(row.id, 'GOV') || (row.portalCatalogId != null && isFavorited(row.portalCatalogId, 'PORTAL'))
    if (row.portalCatalogId) {
      const res = await api.get(`/exchange/portal/catalog/${row.portalCatalogId}`)
      detail.value = res.data as DetailMap
    } else {
      const res = await api.get(`/governance/catalog/resources-mgmt/${row.id}`)
      detail.value = buildDetailFromGov(row, res.data as CatalogRes)
    }
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '加载详情失败')
    detailVisible.value = false
    applySource.value = null
  } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  detailVisible.value = false
  detail.value = null
  if (!applyVisible.value) {
    applySource.value = null
  }
}

async function toggleSubscribe() {
  const row = applySource.value
  if (!row) return
  if (!subscribed.value) {
    const d = detail.value
    try {
      await addFavorite({
        catalogId: String(row.portalCatalogId || row.id),
        title: String(d?.title || row.resourceName || ''),
        catalogCode: String(d?.catalogCode || row.resourceCode || ''),
        providerOrg: String(d?.providerOrg || row.providerOrg || ''),
        resourceType: toApplyResourceType(d?.resourceType ? d : row),
        resourceTypeLabel: String(d?.resourceTypeLabel || ''),
        shareAttr: String(d?.shareAttr || row.shareType || ''),
        openAttr: String(d?.openAttr || row.openType || ''),
        updatedAt: formatUpdatedAt(d?.updatedAt || row.updatedAt),
        source: 'GOV',
        govResourceId: row.id,
      })
      subscribed.value = true
      void ElMessageBox.confirm('已订阅该资源，是否前往「我的订阅」查看？', '订阅成功', {
        confirmButtonText: '去我的订阅',
        cancelButtonText: '继续浏览',
        type: 'success',
      })
        .then(() => {
          router.push({
            query: { ...route.query, tab: 'catalog', cSub: 'subscriptions', subTab: 'favorites' },
          })
        })
        .catch(() => undefined)
    } catch (e: unknown) {
      ElMessage.error((e as Error)?.message || '订阅失败')
    }
    return
  }
  void ElMessageBox.confirm('确认取消订阅该资源？取消后「我的订阅」中将不再显示。', '取消订阅', {
    type: 'warning',
    confirmButtonText: '取消订阅',
    cancelButtonText: '再想想',
  })
    .then(async () => {
      try {
        await removeFavorite(row.id, 'GOV')
        subscribed.value = false
        ElMessage.success('已取消订阅')
      } catch (e: unknown) {
        ElMessage.error((e as Error)?.message || '取消失败')
      }
    })
    .catch(() => undefined)
}

/** 第二步：详情页再点「资源申请」→ 弹申请单 */
function openApplyForm() {
  const d = detail.value
  const row = applySource.value
  if (!d || !row) return
  if (isApplied(row)) {
    ElMessage.warning('该目录已申请，不能再次申请')
    return
  }
  const rt = toApplyResourceType(d.resourceType ? d : row)
  applyTarget.value = {
    id: row.portalCatalogId || row.id,
    title: String(d.title || row.resourceName || ''),
    catalogCode: String(d.catalogCode || row.resourceCode || ''),
    providerOrg: String(d.providerOrg || row.providerOrg || ''),
    shareAttr: String(d.shareAttr || row.shareType || ''),
    openAttr: String(d.openAttr || row.openType || ''),
    updatedAt: formatUpdatedAt(d.updatedAt || row.updatedAt),
    resourceType: rt,
    resourceTypeLabel: String(d.resourceTypeLabel || (rt === 'API' ? '接口' : rt === 'FILE' ? '文件' : '库表')),
    columns: applyColumnsFromDetail(d as Record<string, unknown>),
  }
  applyVisible.value = true
}

async function submitApplyPayload(payload: Record<string, unknown>) {
  const row = applySource.value
  if (!row) return
  try {
    if (row.portalCatalogId) {
      await api.post('/exchange/portal/subscriptions', {
        ...payload,
        catalogId: row.portalCatalogId,
      })
      ElMessage.success('资源申请已提交，可在个人空间「我的申请」与目录「资源申请订阅」查看')
    } else {
      await api.post('/governance/catalog/subscriptions', {
        resourceId: row.id,
        shareMode: toGovShareMode(String(payload.resourceType || '')),
        purpose: payload.purpose || payload.scene,
        applyPayload: JSON.stringify(payload),
      })
      ElMessage.success('资源申请已提交，可在「资源申请订阅」查看')
    }
    appliedResourceIds.value = new Set([...appliedResourceIds.value, row.id])
    applyVisible.value = false
    applyTarget.value = null
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '提交失败')
  }
}

async function bootstrap() {
  await load()
  await tryOpenFromRoute()
}

onMounted(() => {
  void bootstrap()
})
onActivated(() => {
  void bootstrap()
})
</script>

<template>
  <PageCard title="资源目录门户">
    <!-- 详情：与政务共享资源一致，支持订阅 + 资源申请 -->
    <div v-if="detailVisible" v-loading="detailLoading" class="share-detail">
      <button type="button" class="share-detail__back" @click="closeDetail">← 返回资源目录门户</button>
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
                <div><em>文件格式</em>{{ currentFile.format || '—' }}</div>
                <div><em>文件大小</em>{{ currentFile.size || '—' }}</div>
                <div><em>更新周期</em>{{ currentFile.updateCycle || detail.updateCycle || '—' }}</div>
                <div><em>存放方式</em>{{ currentFile.storage || 'FTP' }}</div>
                <div><em>资源描述</em>{{ currentFile.description || detail.description || '—' }}</div>
              </div>
            </div>
          </div>
        </section>

        <div class="detail-actions">
          <el-button :type="subscribed ? 'default' : 'primary'" plain @click="toggleSubscribe">
            {{ subscribed ? '已订阅' : '订阅' }}
          </el-button>
          <el-button
            type="primary"
            :disabled="!!applySource && isApplied(applySource)"
            @click="openApplyForm"
          >
            {{ applySource && isApplied(applySource) ? '已申请' : '资源申请' }}
          </el-button>
        </div>
      </template>
    </div>

    <template v-else>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="目录名称" class="portal-field-md">
          <el-input
            v-model="keyword"
            clearable
            placeholder="请输入目录名称"
            @keyup.enter="load"
          />
        </el-form-item>
        <el-form-item label="提供方" class="portal-field-md">
          <el-select v-model="providerOrg" clearable filterable allow-create placeholder="全部">
            <el-option v-for="p in providerOptions" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="共享属性" class="portal-field-md">
          <el-select v-model="shareType" clearable placeholder="全部">
            <el-option label="无条件共享" value="OPEN" />
            <el-option label="有条件共享" value="CONDITIONAL" />
            <el-option label="不予共享" value="NOT_SHARE" />
          </el-select>
        </el-form-item>
        <el-form-item label="资源类型" class="portal-field-sm">
          <el-select v-model="resourceFormat" clearable placeholder="全部">
            <el-option label="库表" value="DATABASE" />
            <el-option label="接口" value="API" />
            <el-option label="文件" value="FILE" />
          </el-select>
        </el-form-item>
        <el-form-item label="开放属性" class="portal-field-md">
          <el-select v-model="openType" clearable placeholder="全部">
            <el-option label="开放" value="SOCIAL_OPEN" />
            <el-option label="不开放" value="NOT_OPEN" />
          </el-select>
        </el-form-item>
        <el-form-item label="视图" class="portal-field-sm">
          <el-radio-group v-model="viewMode" size="default">
            <el-radio-button value="card">卡片</el-radio-button>
            <el-radio-button value="table">表格</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="load">搜索</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <div v-if="viewMode === 'card'" v-loading="loading" class="portal-grid">
        <el-empty v-if="!loading && !cards.length" description="暂无已发布资源" />
        <div v-for="item in pagedCards" :key="item.id" class="portal-card">
          <div class="portal-card__head">
            <span class="portal-card__title">{{ item.resourceName }}</span>
          </div>
          <div class="portal-card__code">{{ item.resourceCode }}</div>
          <div class="portal-card__meta">
            <span>{{ TYPE_ZH[item.resourceType] || $statusLabel(item.resourceType) }}</span>
            <span v-if="item.providerOrg">· {{ item.providerOrg }}</span>
            <span v-if="item.shareType">· {{ SHARE_TYPE_ZH[item.shareType] || $statusLabel(item.shareType) }}</span>
          </div>
          <p class="portal-card__desc">{{ item.description || item.categoryPath || '暂无描述' }}</p>
          <div class="portal-card__actions">
            <el-button type="primary" size="small" @click="openDetail(item)">
              {{ isApplied(item) ? '已申请' : '资源申请' }}
            </el-button>
          </div>
        </div>
      </div>

      <el-table v-else v-loading="loading" :data="pagedCards" stripe size="small">
        <el-table-column prop="resourceName" label="名称" min-width="140" />
        <el-table-column prop="providerOrg" label="提供方" width="120" show-overflow-tooltip />
        <el-table-column prop="categoryPath" label="分类" min-width="120" show-overflow-tooltip />
        <el-table-column label="资源类型" width="90">
          <template #default="{ row }">{{ FORMAT_ZH[row.resourceFormat || ''] || '—' }}</template>
        </el-table-column>
        <el-table-column label="共享" width="110">
          <template #default="{ row }">{{ SHARE_TYPE_ZH[row.shareType || ''] || '—' }}</template>
        </el-table-column>
        <el-table-column label="开放" width="90">
          <template #default="{ row }">{{ openAttrLabel(row.openType) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">
              {{ isApplied(row) ? '已申请' : '资源申请' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <PortalPagination
        v-model:page="cardPage"
        v-model:page-size="cardPageSize"
        :total="cardTotal"
      />
    </template>

    <ResourceApplyDialog
      v-model:visible="applyVisible"
      :resource="applyTarget"
      :share-attr-label="shareAttrLabel"
      :open-attr-label="openAttrLabel"
      @submit="submitApplyPayload"
    />
  </PageCard>
</template>

<style scoped>
.portal-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
  min-height: 120px;
}
.portal-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 14px 16px;
  background: var(--el-bg-color);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.portal-card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.portal-card__title {
  font-weight: 600;
  font-size: 15px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.portal-card__code {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.portal-card__meta {
  font-size: 12px;
  color: var(--el-text-color-regular);
}
.portal-card__desc {
  margin: 4px 0 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.45;
  min-height: 38px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.portal-card__actions {
  margin-top: auto;
}

.share-detail { padding: 4px 4px 16px; }
.share-detail__back {
  border: 0; background: transparent; color: #1677ff; cursor: pointer;
  padding: 0; margin-bottom: 12px; font-size: 13px;
}
.share-detail__head { margin-bottom: 16px; }
.share-detail__title { margin: 0 0 10px; font-size: 20px; font-weight: 700; color: #1f2329; }
.share-detail__tags { display: flex; flex-wrap: wrap; gap: 8px; }
.type-tag {
  display: inline-block; font-size: 12px; padding: 2px 8px; border-radius: 3px;
  background: #e8f3ff; color: #1677ff; font-weight: 500;
}
.attr-pill {
  display: inline-block; font-size: 12px; padding: 2px 8px; border-radius: 3px;
  background: #f2f3f5; color: #4e5969;
}
.attr-pill--ok { background: #e8ffea; color: #00a870; }
.attr-pill--bad { background: #ffece8; color: #d03050; }
.detail-sec { margin-bottom: 18px; }
.detail-sec h4 {
  margin: 0 0 10px; font-size: 15px; font-weight: 700;
  padding-left: 8px; border-left: 3px solid #1677ff;
}
.info-strip {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px;
  background: #fafbfd; border: 1px solid #eef1f6; border-radius: 6px; padding: 12px 14px;
  font-size: 13px;
}
.info-strip em {
  display: block; font-style: normal; color: #909399; font-size: 12px; margin-bottom: 2px;
}
.c-ok { color: #18a058; }
.c-bad { color: #d03050; }
.split { display: flex; gap: 0; border: 1px solid #eef1f6; border-radius: 6px; overflow: hidden; min-height: 220px; }
.subside {
  width: 180px; flex-shrink: 0; background: #fafbfd; border-right: 1px solid #eef1f6; padding: 10px 8px;
}
.subside__label { font-size: 12px; color: #909399; padding: 4px 8px 8px; }
.subside__item {
  width: 100%; border: 0; background: transparent; text-align: left;
  padding: 8px 10px; border-radius: 4px; cursor: pointer; font-size: 13px; color: #303133;
}
.subside__item.is-on { background: #e8f3ff; color: #1677ff; }
.submain { flex: 1; min-width: 0; padding: 12px 14px; }
.info-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 10px 16px;
  font-size: 13px; margin-bottom: 12px;
}
.info-grid em {
  display: block; font-style: normal; color: #909399; font-size: 12px; margin-bottom: 2px;
}
.sub-title { margin: 12px 0 8px; font-size: 13px; font-weight: 600; color: #303133; }
.muted { color: #909399; font-size: 12px; padding: 8px; }
.detail-actions {
  display: flex; justify-content: flex-end; gap: 8px;
  margin-top: 16px; padding-top: 12px; border-top: 1px solid #eef1f6;
}

@media (max-width: 900px) {
  .info-strip { grid-template-columns: 1fr 1fr; }
  .split { flex-direction: column; }
  .subside { width: 100%; border-right: 0; border-bottom: 1px solid #eef1f6; }
}
</style>
