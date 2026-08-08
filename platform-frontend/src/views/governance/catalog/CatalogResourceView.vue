<script setup lang="ts">
import { computed, nextTick, onActivated, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox, type InputInstance } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { useAuthStore } from '@/stores/auth'
import {
  catalogResourceStatusCode,
  catalogResourceStatusLabel,
  statusLabel,
  statusTagType,
} from '@/utils/status-label'
import { ingestionApi } from '@/views/exchange/ingestion/useIngestionHub'

const router = useRouter()
const auth = useAuthStore()
/** 超级管理员可选任意提供方；部门管理员锁定为本机构 */
const isSuperAdmin = computed(() => !!auth.isSystemAdmin)
const currentDeptName = computed(() => auth.user?.orgName || '（未绑定部门）')

interface CategoryNode {
  id: number
  categoryCode: string
  categoryName: string
  label?: string
  parentId?: number
  categoryPath?: string
  children?: CategoryNode[]
}

interface OrgRow {
  id: number
  orgName: string
  orgCode?: string
  parentId?: number
}

interface CatalogRes {
  id: number
  resourceCode: string
  resourceName: string
  resourceType: string
  categoryId?: number
  categoryPath?: string
  providerOrg?: string
  resourceFormat?: string
  shareType?: string
  shareCondition?: string
  notShareReason?: string
  openType?: string
  openCondition?: string
  notOpenReason?: string
  contactName?: string
  contactPhone?: string
  contactEmail?: string
  themeName?: string
  baseCatalogName?: string
  tags?: string
  extJson?: string
  updateCycle?: string
  description?: string
  publishStatus: string
  approvalStatus: string
  secretFlag?: number
  versionNo?: number
  sourcePathType?: string
  qualityScore?: number
  metadataEntryCode?: string
  physicalTableName?: string
}

interface VersionRow {
  id: number
  resourceId: number
  versionNo: number
  changeSummary?: string
  publishedBy?: string
  publishedAt?: string
}

interface VersionDiff {
  leftNo?: number
  rightNo?: number
  sameSnapshot?: boolean
  basicDiff?: Array<{ field: string; left: string; right: string }>
}

interface MetaOpt {
  entryCode: string
  entryName: string
  dataLayer?: string
  physicalTableName?: string
  dataSourceId?: number
  ownerName?: string
  sourcePathType?: string
}

interface ColumnRow {
  columnName: string
  columnNameZh: string
  dataType: string
  dataTypeZh: string
  sensLevel: string
  shareLevel: string
  displayFlag: boolean
  searchFlag: boolean
  statFlag: boolean
  sortFlag: boolean
  remark: string
}

interface BindSource {
  id: number
  sourceName: string
  sourceCode?: string
  sourceType?: string
  categoryKey?: string
  categoryName?: string
  providerOrg?: string
  versionLabel?: string
  catalogable?: boolean
  systemName?: string
  connStatus?: string
  databaseName?: string
  platformLayer?: boolean
}

interface BindTable {
  tableName: string
  sourceTable?: string
  metadataEntryCode?: string
  catalogable?: boolean
  entryName?: string
  tableComment?: string
  chineseName?: string
}

interface ApiParamRow {
  name: string
  type: string
  required: boolean
  description: string
}

interface FileColumnRow {
  columnName: string
  columnNameZh: string
  dataType: string
  required: boolean
  description: string
}

const SHARE_ZH: Record<string, string> = {
  OPEN: '无条件共享',
  CONDITIONAL: '有条件共享',
  NOT_SHARE: '不予共享',
}
const FORMAT_ZH: Record<string, string> = {
  DATABASE: '数据库',
  API: '接口',
  FILE: '文件',
  OTHER: '其他',
}
const CYCLE_ZH: Record<string, string> = {
  REALTIME: '实时',
  DAILY: '每日',
  WEEKLY: '每周',
  MONTHLY: '每月',
  YEARLY: '每年',
}
const TYPE_ZH: Record<string, string> = { DATA: '数据', SERVICE: '服务' }
const OPEN_ZH: Record<string, string> = {
  SOCIAL_OPEN: '开放',
  NOT_OPEN: '不开放',
}
const SHARE_LEVEL_ZH: Record<string, string> = {
  OPEN: '无条件共享',
  CONDITIONAL: '有条件共享',
  NOT_SHARE: '不予共享',
}
const SENS_LEVEL_OPTS = ['1级', '2级', '3级', '4级']
const BIND_CATEGORY_OPTS = [
  { key: 'SOURCE', label: '来源' },
  { key: 'ODS', label: '原始库' },
  { key: 'GOVERNANCE', label: '治理库' },
  { key: 'THEME', label: '主题专题' },
  { key: 'OTHER', label: '其他' },
  { key: 'DICT', label: '字典' },
]
const DS_ADAPTERS = [
  { type: 'MYSQL', label: 'Mysql', port: 3306 },
  { type: 'ORACLE', label: 'Oracle', port: 1521 },
  { type: 'DAMENG', label: '达梦', port: 5236 },
  { type: 'POSTGRESQL', label: 'PostgreSQL', port: 5432 },
  { type: 'GBASE', label: 'GBase', port: 5258 },
  { type: 'HBASE', label: 'Hbase', port: 2181 },
  { type: 'HIVE', label: 'Hive', port: 10000 },
  { type: 'ARGODB', label: 'ArgoDB', port: 5432 },
] as const
const PARAM_TYPE_OPTS = ['字符串', '整数', '浮点数', '布尔', '对象', '数组', '日期', '日期时间']
const FILE_MAX_BYTES = 100 * 1024 * 1024

const props = withDefaults(defineProps<{ catalogOrigin?: 'INGEST' | 'GOVERNANCE' }>(), {
  catalogOrigin: 'GOVERNANCE',
})

/**
 * 治理侧无「数据资源分类」菜单：编目/主题/基础下拉统一取归集「指标与目录体系构建」分类。
 * 归集侧仍用自身 INGEST 分类，行为不变。
 */
const categoryDataOrigin = computed(() =>
  props.catalogOrigin === 'GOVERNANCE' ? 'INGEST' : props.catalogOrigin,
)
const categoryHint = computed(() =>
  props.catalogOrigin === 'GOVERNANCE'
    ? '取自归集「指标与目录体系构建 · 数据资源分类」'
    : '请先在数据资源分类中维护',
)

const treeData = ref<CategoryNode[]>([])
const categoryOptions = ref<{ id: number; label: string }[]>([])
/** 扁平分类（含 parentId），用于取基础/主题目录下一级 */
const categoryRows = ref<
  Array<{
    id: number
    categoryName: string
    categoryCode?: string
    categoryPath?: string
    parentId?: number | null
  }>
>([])
const resources = ref<CatalogRes[]>([])
const { page, pageSize, paged: pagedResources, total: resourceTotal, resetPage } = useClientPager(resources)
const loading = ref(false)
const selectedRows = ref<CatalogRes[]>([])
const query = reactive({
  resourceName: '',
  approvalStatus: '',
  shareType: '',
  resourceFormat: '',
  categoryId: undefined as number | undefined,
})
const dialogVisible = ref(false)
const editMode = ref(false)
const viewMode = ref(false)
const editingId = ref<number | null>(null)
const batchColumnVisible = ref(false)
const batchColumnForm = reactive({
  sensLevel: '1级',
  shareLevel: 'CONDITIONAL',
  displayFlag: true,
})
const wizardStep = ref(0)
const saving = ref(false)
const versionDrawerVisible = ref(false)
const versionResource = ref<CatalogRes | null>(null)
const versions = ref<VersionRow[]>([])
const versionLeftNo = ref<number | undefined>()
const versionRightNo = ref<number | undefined>()
const versionDiff = ref<VersionDiff | null>(null)
const importVisible = ref(false)
const importFormat = ref<'json' | 'csv'>('json')
const importContent = ref('')
const importLoading = ref(false)
const batchVisible = ref(false)
const batchLoading = ref(false)
const batchEntryCodes = ref<string[]>([])
const batchShareType = ref('OPEN')
const batchMetaLoading = ref(false)

const orgTree = ref<Array<{ value: string; label: string; children?: any[] }>>([])
const metaOptions = ref<MetaOpt[]>([])
const metaLoading = ref(false)
const batchMetaOptions = ref<MetaOpt[]>([])
const columnRows = ref<ColumnRow[]>([])
const apiTab = ref('basic')
const apiForm = reactive({
  apiName: '',
  apiUrl: '',
  apiPath: '',
  apiMethod: 'GET',
  apiTimeout: 0,
  apiVersion: '',
  registerAt: '',
  expireAt: '',
  apiDescription: '',
  apiResultJson: '{}',
})
const requestParams = ref<ApiParamRow[]>([])
const responseParams = ref<ApiParamRow[]>([])
const fileForm = reactive({
  fileName: '',
  fileRemark: '',
  fileSize: undefined as number | undefined,
  sheetName: '',
})
const fileColumns = ref<FileColumnRow[]>([])
const fileSheetOptions = ref<string[]>([])

/** 库表关联：选数据源 / 选表 / 字段配置 */
const bindSourceId = ref<number | undefined>()
const bindSourceName = ref('')
const bindTableName = ref('')
const dsPickerVisible = ref(false)
const dsPickerCat = ref('SOURCE')
const dsPickerKeyword = ref('')
const dsPickerName = ref('')
const dsPickerLoading = ref(false)
const dsPickerRows = ref<BindSource[]>([])
const dsPickerSelected = ref<BindSource | null>(null)
const tablePickerVisible = ref(false)
const tablePickerLoading = ref(false)
const tablePickerRows = ref<BindTable[]>([])
const tablePickerSelected = ref<string>('')
const columnsLoading = ref(false)

const addDsVisible = ref(false)
const addDsStep = ref(0)
const addDsSaving = ref(false)
const addDsTesting = ref(false)
const addDsAdapter = ref('MYSQL')
/** 编辑已有数据源时非空；新增时为 null */
const editingDsId = ref<number | null>(null)
const addDsProjects = ref<Array<{ id: number; projectName: string }>>([])
const addDsSystems = ref<Array<{ id: number; systemName: string; projectId: number }>>([])
const addDsForm = reactive({
  sortOrder: 0,
  sourceName: '',
  deptName: '',
  categoryName: '来源',
  systemId: undefined as number | undefined,
  remark: '',
  sourceTag: '',
  readonlyFlag: true,
  host: '',
  port: 3306,
  database: '',
  username: '',
  password: '',
})
const addDsDialogTitle = computed(() => (editingDsId.value != null ? '编辑数据源' : '新增数据源'))

function dataTypeToZh(dt: string): string {
  const u = (dt || '').toUpperCase()
  if (/(INT|LONG|DECIMAL|NUMERIC|FLOAT|DOUBLE|NUMBER|BIGINT|SMALLINT)/.test(u)) {
    if (/FLOAT|DOUBLE|DECIMAL|NUMERIC/.test(u)) return '浮点数'
    return '整数'
  }
  if (/(DATE|TIME)/.test(u)) return u.includes('TIME') && !u.startsWith('DATE') ? '日期时间' : '日期'
  if (/(BOOL|BIT)/.test(u)) return '布尔'
  return '字符串'
}

const importPlaceholder = computed(() => {
  if (importFormat.value === 'json') {
    return '[{"resourceName":"示例","resourceType":"DATA"}]'
  }
  return 'resourceCode,resourceName,resourceType\nRES001,示例,DATA'
})

const form = reactive({
  resourceCode: '',
  resourceName: '',
  resourceType: 'DATA',
  categoryId: undefined as number | undefined,
  providerOrg: '',
  resourceFormat: 'DATABASE',
  shareType: 'OPEN',
  shareCondition: '',
  notShareReason: '',
  openType: 'SOCIAL_OPEN',
  openCondition: '',
  notOpenReason: '',
  contactName: '',
  contactPhone: '',
  contactEmail: '',
  themeName: '',
  baseCatalogName: '',
  tagList: [] as string[],
  updateCycle: 'DAILY',
  description: '',
  secretFlag: 0,
  metadataEntryCode: '',
  sourcePathType: 'DIRECT',
  physicalTableName: '',
  qualityScore: undefined as number | undefined,
})

const pageTitle = computed(() =>
  props.catalogOrigin === 'INGEST' ? '数据资源编目管理' : '资源目录编制',
)
const publishEntryName = computed(() =>
  props.catalogOrigin === 'INGEST' ? '资源目录注册发布' : '目录注册发布',
)

const shareConditionEnabled = computed(() => form.shareType === 'CONDITIONAL')
const notShareReasonEnabled = computed(() => form.shareType === 'NOT_SHARE')
const openConditionEnabled = computed(() => form.openType === 'SOCIAL_OPEN')
const notOpenReasonEnabled = computed(() => form.openType === 'NOT_OPEN')

const tagInputVisible = ref(false)
const tagInputValue = ref('')
const tagInputRef = ref<InputInstance>()

function showTagInput() {
  tagInputVisible.value = true
  tagInputValue.value = ''
  void nextTick(() => tagInputRef.value?.focus())
}

function confirmAddTag() {
  const t = tagInputValue.value.trim()
  if (t && !form.tagList.includes(t)) {
    form.tagList.push(t)
  }
  tagInputVisible.value = false
  tagInputValue.value = ''
}

function removeTag(tag: string) {
  form.tagList = form.tagList.filter((x) => x !== tag)
}

const THEME_ROOT_NAME = '主题资源目录'
const BASE_ROOT_NAME = '基础资源目录'

/** 兼容分类名中的康熙部首字形（⽬/⻔ 等），避免与常用汉字匹配失败 */
function normalizeCatalogName(s: string): string {
  return String(s || '')
    .replace(/\u2F6C/g, '目') // ⽬（康熙部首）-> 目
    .replace(/\u2ED4/g, '门') // ⻔（康熙部首）-> 门
    .replace(/\s+/g, '')
    .trim()
}

function preferredRootCode(rootName: string): string | undefined {
  const n = normalizeCatalogName(rootName)
  if (n === normalizeCatalogName(BASE_ROOT_NAME)) return '_BASE'
  if (n === normalizeCatalogName(THEME_ROOT_NAME)) return '_THEME'
  if (n === '部门资源目录') return '_DEPT'
  return undefined
}

function findRootCategoryId(rootName: string): number | undefined {
  const target = normalizeCatalogName(rootName)
  const codeSuffix = preferredRootCode(rootName)
  const roots = categoryRows.value.filter((r) => !r.parentId || Number(r.parentId) === 0)
  const nameMatched = roots.filter((r) => normalizeCatalogName(r.categoryName) === target)
  // 优先种子码（CAT_INGEST_BASE / CAT_GOV_THEME 等），避免重复根名时挂错树
  if (codeSuffix && nameMatched.length) {
    const byCode = nameMatched.find((r) => String(r.categoryCode || '').toUpperCase().endsWith(codeSuffix))
    if (byCode) return byCode.id
  }
  // 其次选已有下级的根，避免空壳重复根导致「无数据」
  if (nameMatched.length > 1) {
    const withKids = nameMatched.find((r) =>
      categoryRows.value.some((c) => Number(c.parentId) === Number(r.id)),
    )
    if (withKids) return withKids.id
  }
  if (nameMatched[0]) return nameMatched[0].id
  if (codeSuffix) {
    const byCodeOnly = roots.find((r) => String(r.categoryCode || '').toUpperCase().endsWith(codeSuffix))
    if (byCodeOnly) return byCodeOnly.id
  }
  return categoryRows.value.find((r) => normalizeCatalogName(r.categoryName) === target)?.id
}

function directChildCategories(rootName: string) {
  const rootId = findRootCategoryId(rootName)
  if (rootId != null) {
    const byParent = categoryRows.value.filter((r) => Number(r.parentId) === Number(rootId))
    if (byParent.length) return byParent
  }
  // 兜底：按路径「根名/子名」识别下一级（不依赖 parentId）
  const prefix = normalizeCatalogName(rootName) + '/'
  return categoryRows.value.filter((r) => {
    const path = normalizeCatalogName(r.categoryPath || '')
    if (!path.startsWith(prefix)) return false
    const rest = path.slice(prefix.length)
    return !!rest && !rest.includes('/')
  })
}

const flatCategories = computed(() => {
  const out: { id: number; label: string }[] = []
  const walk = (nodes: CategoryNode[], prefix = '') => {
    for (const n of nodes) {
      const label = prefix ? `${prefix} / ${n.categoryName}` : n.categoryName
      out.push({ id: n.id, label })
      if (n.children?.length) walk(n.children, label)
    }
  }
  walk(treeData.value)
  return out
})

/** 查询栏：仍可按全部分类筛选 */
const categorySelectOptions = computed(() =>
  categoryOptions.value.length ? categoryOptions.value : flatCategories.value,
)

/** 表单「信息资源分类」：仅基础资源目录的下一级 */
const baseCategoryOptions = computed(() =>
  directChildCategories(BASE_ROOT_NAME).map((c) => ({
    id: c.id,
    label: c.categoryName || c.categoryCode || String(c.id),
  })),
)

/** 表单「主题资源目录」：仅主题资源目录的下一级 */
const themeCatalogOptions = computed(() =>
  directChildCategories(THEME_ROOT_NAME).map((c) => ({
    id: c.id,
    label: c.categoryName || c.categoryCode || String(c.id),
    value: c.categoryName || '',
  })),
)

watch(
  () => form.shareType,
  (v) => {
    if (v !== 'CONDITIONAL') form.shareCondition = ''
    if (v !== 'NOT_SHARE') form.notShareReason = ''
  },
)
watch(
  () => form.openType,
  (v) => {
    if (v !== 'SOCIAL_OPEN') form.openCondition = ''
    if (v !== 'NOT_OPEN') form.notOpenReason = ''
  },
)
watch(
  () => form.resourceFormat,
  () => {
    columnRows.value = []
    requestParams.value = []
    responseParams.value = []
    resetApiFileForms()
    bindSourceId.value = undefined
    bindSourceName.value = ''
    bindTableName.value = ''
    form.metadataEntryCode = ''
    form.physicalTableName = ''
    fileColumns.value = []
  },
)

function resetApiFileForms() {
  apiForm.apiName = ''
  apiForm.apiUrl = ''
  apiForm.apiPath = ''
  apiForm.apiMethod = 'GET'
  apiForm.apiTimeout = 0
  apiForm.apiVersion = ''
  apiForm.registerAt = ''
  apiForm.expireAt = ''
  apiForm.apiDescription = ''
  apiForm.apiResultJson = '{}'
  fileForm.fileName = ''
  fileForm.fileRemark = ''
  fileForm.fileSize = undefined
  fileForm.sheetName = ''
  fileColumns.value = []
  fileSheetOptions.value = []
}

async function loadTree() {
  const res = await api.get('/governance/catalog/categories/tree', {
    params: { catalogOrigin: categoryDataOrigin.value },
  })
  treeData.value = res.data || []
}

async function loadCategoryOptions() {
  try {
    const res = await api.get('/governance/catalog/categories', {
      params: { catalogOrigin: categoryDataOrigin.value },
    })
    const rows = (res.data || []) as Array<{
      id: number
      categoryName?: string
      categoryPath?: string
      categoryCode?: string
      parentId?: number | null
    }>
    categoryRows.value = rows.map((r) => ({
      id: r.id,
      categoryName: r.categoryName || '',
      categoryCode: r.categoryCode,
      categoryPath: r.categoryPath,
      parentId: r.parentId ?? 0,
    }))
    categoryOptions.value = rows.map((r) => ({
      id: r.id,
      label: r.categoryPath || r.categoryName || r.categoryCode || String(r.id),
    }))
  } catch {
    categoryRows.value = []
    categoryOptions.value = []
  }
}

function onCategorySelectVisible(visible: boolean) {
  if (visible) void loadCategoryOptions()
}

async function ensureCategoryLoaded() {
  await Promise.all([loadTree(), loadCategoryOptions()])
}

async function loadOrgs() {
  try {
    const rows = ((await api.get('/system/orgs')).data || []) as OrgRow[]
    const map = new Map<number, { value: string; label: string; children?: any[] }>()
    for (const r of rows) {
      map.set(r.id, { value: r.orgName, label: r.orgName, children: [] })
    }
    const roots: typeof orgTree.value = []
    for (const r of rows) {
      const node = map.get(r.id)!
      if (!r.parentId || r.parentId === 0 || !map.has(r.parentId)) roots.push(node)
      else map.get(r.parentId)!.children!.push(node)
    }
    const prune = (nodes: typeof orgTree.value) => {
      for (const n of nodes) {
        if (!n.children?.length) delete n.children
        else prune(n.children)
      }
    }
    prune(roots)
    orgTree.value = roots
  } catch {
    orgTree.value = []
  }
}

async function loadResources() {
  loading.value = true
  try {
    const st = (query.approvalStatus || '').toUpperCase()
    const res = await api.get('/governance/catalog/resources-mgmt', {
      params: {
        keyword: query.resourceName || undefined,
        approvalStatus: st && st !== 'OFFLINE' ? st : undefined,
        publishStatus: st === 'OFFLINE' ? 'OFFLINE' : undefined,
        shareType: query.shareType || undefined,
        resourceFormat: query.resourceFormat || undefined,
        categoryId: query.categoryId || undefined,
        catalogOrigin: props.catalogOrigin,
      },
    })
    resources.value = res.data || []
    resetPage()
  } catch {
    ElMessage.error('加载资源失败')
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.resourceName = ''
  query.approvalStatus = ''
  query.shareType = ''
  query.resourceFormat = ''
  query.categoryId = undefined
  void loadResources()
}

function applyLockedProviderOrg() {
  if (!isSuperAdmin.value) {
    form.providerOrg = auth.user?.orgName || ''
  }
}

function resetForm() {
  editMode.value = false
  viewMode.value = false
  editingId.value = null
  wizardStep.value = 0
  apiTab.value = 'basic'
  form.resourceCode = ''
  form.resourceName = ''
  form.resourceType = 'DATA'
  form.categoryId = undefined
  form.providerOrg = ''
  applyLockedProviderOrg()
  form.resourceFormat = 'DATABASE'
  form.shareType = 'OPEN'
  form.shareCondition = ''
  form.notShareReason = ''
  form.openType = 'SOCIAL_OPEN'
  form.openCondition = ''
  form.notOpenReason = ''
  form.contactName = ''
  form.contactPhone = ''
  form.contactEmail = ''
  form.themeName = ''
  form.baseCatalogName = ''
  form.tagList = []
  form.updateCycle = 'DAILY'
  form.description = ''
  form.secretFlag = 0
  form.metadataEntryCode = ''
  form.sourcePathType = 'DIRECT'
  form.physicalTableName = ''
  form.qualityScore = undefined
  columnRows.value = []
  requestParams.value = []
  responseParams.value = []
  resetApiFileForms()
  bindSourceId.value = undefined
  bindSourceName.value = ''
  bindTableName.value = ''
}

async function loadEligibleMeta(keyword?: string) {
  metaLoading.value = true
  try {
    const res = await api.get('/governance/catalog/resources-mgmt/eligible-metadata', {
      params: { keyword: keyword || undefined },
    })
    metaOptions.value = res.data || []
  } finally {
    metaLoading.value = false
  }
}

function onMetaPick(code: string) {
  const m = metaOptions.value.find((x) => x.entryCode === code)
  if (!m) return
  form.metadataEntryCode = m.entryCode
  if (!form.resourceName) form.resourceName = m.entryName
  form.physicalTableName = m.physicalTableName || ''
  form.sourcePathType = m.sourcePathType || 'DIRECT'
  if (isSuperAdmin.value && m.ownerName && !form.providerOrg) form.providerOrg = m.ownerName
}

function openCreate() {
  resetForm()
  void loadEligibleMeta()
  void loadOrgs()
  void ensureCategoryLoaded()
  dialogVisible.value = true
}

async function openBatchCreate() {
  batchEntryCodes.value = []
  batchShareType.value = 'OPEN'
  batchVisible.value = true
  batchMetaLoading.value = true
  try {
    const res = await api.get('/governance/catalog/resources-mgmt/eligible-metadata')
    batchMetaOptions.value = res.data || []
  } catch {
    ElMessage.error('加载可编目库表失败')
    batchMetaOptions.value = []
  } finally {
    batchMetaLoading.value = false
  }
}

async function submitBatchCreate() {
  if (!batchEntryCodes.value.length) {
    ElMessage.warning('请选择要编目的库表/元数据')
    return
  }
  batchLoading.value = true
  try {
    const res = await api.post('/governance/catalog/resources-mgmt/batch-from-metadata', {
      entryCodes: batchEntryCodes.value,
      catalogOrigin: props.catalogOrigin,
      shareType: batchShareType.value,
      resourceFormat: 'DATABASE',
      updateCycle: 'DAILY',
    })
    const d = res.data || {}
    ElMessage.success(`批量编目完成：新增 ${d.created || 0}，跳过 ${d.skipped || 0}（请到「${publishEntryName.value}」后发布）`)
    if (d.errors?.length) {
      ElMessage.warning(d.errors.slice(0, 3).join('；'))
    }
    batchVisible.value = false
    await loadResources()
  } catch {
    ElMessage.error('批量新增失败')
  } finally {
    batchLoading.value = false
  }
}

function parseExt(row: CatalogRes) {
  if (!row.extJson) return null
  try {
    return JSON.parse(row.extJson)
  } catch {
    return null
  }
}

function fillFormFromRow(row: CatalogRes) {
  form.resourceCode = row.resourceCode
  form.resourceName = row.resourceName
  form.resourceType = row.resourceType || 'DATA'
  form.categoryId = row.categoryId
  form.providerOrg = row.providerOrg || ''
  if (!isSuperAdmin.value) {
    form.providerOrg = auth.user?.orgName || form.providerOrg
  }
  form.resourceFormat = row.resourceFormat || 'DATABASE'
  form.shareType = row.shareType || 'OPEN'
  form.shareCondition = row.shareCondition || ''
  form.notShareReason = row.notShareReason || ''
  form.openType = row.openType || 'SOCIAL_OPEN'
  form.openCondition = row.openCondition || ''
  form.notOpenReason = row.notOpenReason || ''
  form.contactName = row.contactName || ''
  form.contactPhone = row.contactPhone || ''
  form.contactEmail = row.contactEmail || ''
  form.themeName = row.themeName || ''
  form.baseCatalogName = row.baseCatalogName || ''
  form.tagList = row.tags ? row.tags.split(',').map((s) => s.trim()).filter(Boolean) : []
  form.updateCycle = row.updateCycle || 'DAILY'
  form.description = row.description || ''
  form.secretFlag = row.secretFlag || 0
  form.metadataEntryCode = row.metadataEntryCode || ''
  form.sourcePathType = row.sourcePathType || 'DIRECT'
  form.physicalTableName = row.physicalTableName || ''
  form.qualityScore = row.qualityScore
  const ext = parseExt(row)
  if (ext) {
    if (ext.bindSourceId) bindSourceId.value = Number(ext.bindSourceId)
    if (ext.bindSourceName) bindSourceName.value = String(ext.bindSourceName)
    if (ext.bindTableName) bindTableName.value = String(ext.bindTableName)
    if (Array.isArray(ext.columnList)) {
      columnRows.value = ext.columnList.map((c: any) => ({
        columnName: c.columnName || '',
        columnNameZh: c.columnNameZh || c.remark || c.columnName || '',
        dataType: c.dataType || 'VARCHAR',
        dataTypeZh: c.dataTypeZh || dataTypeToZh(c.dataType || ''),
        sensLevel: c.sensLevel || '1级',
        shareLevel: c.shareLevel || c.fieldType || 'CONDITIONAL',
        displayFlag: c.displayFlag !== false,
        searchFlag: !!c.searchFlag,
        statFlag: !!c.statFlag,
        sortFlag: !!c.sortFlag,
        remark: c.remark || '',
      }))
    }
    if (ext.api) {
      Object.assign(apiForm, {
        apiName: ext.api.apiName || '',
        apiUrl: ext.api.apiUrl || '',
        apiPath: ext.api.apiPath || '',
        apiMethod: ext.api.apiMethod || 'GET',
        apiTimeout: ext.api.apiTimeout ?? 3000,
        apiVersion: ext.api.apiVersion || '',
        registerAt: ext.api.registerAt || '',
        expireAt: ext.api.expireAt || '',
        apiDescription: ext.api.apiDescription || '',
        apiResultJson: ext.api.apiResultJson || '{}',
      })
      requestParams.value = Array.isArray(ext.api.requestParams)
        ? ext.api.requestParams.map((p: any) => ({
            name: p.name || '',
            type: p.type || '字符串',
            required: !!p.required,
            description: p.description || '',
          }))
        : []
      responseParams.value = Array.isArray(ext.api.responseParams)
        ? ext.api.responseParams.map((p: any) => ({
            name: p.name || '',
            type: p.type || '字符串',
            required: !!p.required,
            description: p.description || '',
          }))
        : []
    }
    if (ext.file) {
      fileForm.fileName = ext.file.fileName || ''
      fileForm.fileRemark = ext.file.fileRemark || ''
      fileForm.fileSize = ext.file.fileSize
      fileForm.sheetName = ext.file.sheetName || ''
      fileColumns.value = Array.isArray(ext.file.columnList)
        ? ext.file.columnList.map((c: any) => ({
            columnName: c.columnName || c.name || '',
            columnNameZh: c.columnNameZh || '',
            dataType: c.dataType || c.type || '字符串',
            required: !!c.required,
            description: c.description || '',
          }))
        : []
    }
  }
}

function openEdit(row: CatalogRes) {
  const st = (row.approvalStatus || '').toUpperCase()
  if (st === 'PENDING') {
    ElMessage.warning('审批中不可编辑')
    return
  }
  if (st === 'TO_REGISTER') {
    ElMessage.warning('待发布不可编辑，请先撤回')
    return
  }
  if (row.publishStatus === 'PUBLISHED') {
    ElMessage.warning('已发布不可编辑，请先下线')
    return
  }
  resetForm()
  editMode.value = true
  viewMode.value = false
  editingId.value = row.id
  wizardStep.value = 0
  void ensureCategoryLoaded()
  void loadOrgs()
  fillFormFromRow(row)
  void loadEligibleMeta(row.metadataEntryCode)
  dialogVisible.value = true
}

interface ApprovalFlowRow {
  id: number
  actionType?: string
  status?: string
  submitComment?: string
  reviewComment?: string
  submittedBy?: string
  submittedAt?: string
  reviewedBy?: string
  reviewedAt?: string
}

const approvalFlowRows = ref<ApprovalFlowRow[]>([])
const approvalFlowLoading = ref(false)

const ACTION_FLOW_ZH: Record<string, string> = {
  PUBLISH: '发布',
  OFFLINE: '下线',
  UPDATE: '变更',
  DELETE: '删除',
  CREATE: '编目新增',
}

async function loadApprovalFlow(resourceId: number) {
  approvalFlowLoading.value = true
  approvalFlowRows.value = []
  try {
    const res = await api.get(`/governance/catalog/resources-mgmt/${resourceId}/approvals`)
    approvalFlowRows.value = (res.data || []) as ApprovalFlowRow[]
  } catch {
    approvalFlowRows.value = []
  } finally {
    approvalFlowLoading.value = false
  }
}

function openView(row: CatalogRes) {
  resetForm()
  editMode.value = false
  viewMode.value = true
  editingId.value = row.id
  wizardStep.value = 0
  void ensureCategoryLoaded()
  void loadOrgs()
  fillFormFromRow(row)
  void loadEligibleMeta(row.metadataEntryCode)
  void loadApprovalFlow(row.id)
  dialogVisible.value = true
}

function validateStep1(): boolean {
  if (!form.resourceName?.trim()) {
    ElMessage.warning('请填写信息资源名称')
    return false
  }
  if (!form.providerOrg?.trim()) {
    ElMessage.warning('请选择信息资源提供方')
    return false
  }
  if (!form.resourceFormat) {
    ElMessage.warning('请选择信息资源格式')
    return false
  }
  if (!form.shareType) {
    ElMessage.warning('请选择共享类型')
    return false
  }
  if (form.shareType === 'CONDITIONAL' && !form.shareCondition?.trim()) {
    ElMessage.warning('有条件共享须填写共享条件')
    return false
  }
  if (form.shareType === 'NOT_SHARE' && !form.notShareReason?.trim()) {
    ElMessage.warning('不予共享须填写不共享理由')
    return false
  }
  if (!form.openType) {
    ElMessage.warning('请选择是否向社会开放')
    return false
  }
  if (form.openType === 'SOCIAL_OPEN' && !form.openCondition?.trim()) {
    ElMessage.warning('向社会开放须填写开放条件')
    return false
  }
  if (form.openType === 'NOT_OPEN' && !form.notOpenReason?.trim()) {
    ElMessage.warning('不开放须填写不开放理由')
    return false
  }
  return true
}

function validateStep2(): boolean {
  const fmt = form.resourceFormat
  if (fmt === 'DATABASE') {
    if (!bindSourceId.value) {
      ElMessage.warning('请选择关联数据源')
      return false
    }
    if (!bindTableName.value) {
      ElMessage.warning('请选择数据表')
      return false
    }
    if (!columnRows.value.length) {
      ElMessage.warning('字段列表不能为空，请重新选择数据表加载字段')
      return false
    }
    for (const c of columnRows.value) {
      if (!c.sensLevel) {
        ElMessage.warning(`字段「${c.columnName}」未选择数据敏感级别`)
        return false
      }
      if (!c.shareLevel) {
        ElMessage.warning(`字段「${c.columnName}」未选择共享类型`)
        return false
      }
    }
    return true
  }
  if (fmt === 'API') {
    if (!apiForm.apiName?.trim()) {
      ElMessage.warning('请填写接口名称')
      return false
    }
    if (!apiForm.apiUrl?.trim()) {
      ElMessage.warning('请填写目标地址')
      return false
    }
    if (!apiForm.apiPath?.trim()) {
      ElMessage.warning('请填写请求路径')
      return false
    }
    if (!apiForm.apiMethod) {
      ElMessage.warning('请选择请求方式')
      return false
    }
    if (apiForm.apiTimeout == null || Number(apiForm.apiTimeout) < 0) {
      ElMessage.warning('请填写有效超时时间(ms)')
      return false
    }
    try {
      JSON.parse(apiForm.apiResultJson || '{}')
    } catch {
      ElMessage.warning('返回结果示例须为合法 JSON')
      return false
    }
    return true
  }
  if (fmt === 'FILE') {
    if (!fileForm.fileName?.trim()) {
      ElMessage.warning('请选择或填写文件名称')
      return false
    }
    if (!fileColumns.value.length) {
      ElMessage.warning('文件列信息不能为空，请添加列或重新选择文件')
      return false
    }
    for (const c of fileColumns.value) {
      if (!c.columnName?.trim()) {
        ElMessage.warning('文件列存在未填写的参数名/列名')
        return false
      }
      if (!c.dataType) {
        ElMessage.warning(`列「${c.columnName}」未选择类型`)
        return false
      }
    }
    return true
  }
  return true
}

function goNext() {
  if (!viewMode.value && !validateStep1()) return
  wizardStep.value = 1
  apiTab.value = 'basic'
  if (form.resourceFormat === 'API') {
    if (!requestParams.value.length) addRequestParam()
    if (!responseParams.value.length) addResponseParam()
  }
}

function goPrev() {
  wizardStep.value = 0
}

function buildExtJson(): Record<string, unknown> | null {
  const fmt = form.resourceFormat
  if (fmt === 'DATABASE') {
    return {
      bindSourceId: bindSourceId.value,
      bindSourceName: bindSourceName.value,
      bindTableName: bindTableName.value,
      columnList: columnRows.value,
    }
  }
  if (fmt === 'API') {
    return {
      api: {
        ...apiForm,
        requestParams: requestParams.value,
        responseParams: responseParams.value,
      },
    }
  }
  if (fmt === 'FILE') {
    return {
      file: {
        fileName: fileForm.fileName,
        fileRemark: fileForm.fileRemark,
        fileSize: fileForm.fileSize,
        sheetName: fileForm.sheetName || null,
        columnList: fileColumns.value,
      },
    }
  }
  return null
}

function buildPayload() {
  const ext = buildExtJson()
  return {
    resourceCode: form.resourceCode || undefined,
    resourceName: form.resourceName.trim(),
    resourceType: form.resourceType,
    categoryId: form.categoryId,
    providerOrg: form.providerOrg,
    resourceFormat: form.resourceFormat,
    shareType: form.shareType,
    shareCondition: form.shareCondition || null,
    notShareReason: form.notShareReason || null,
    openType: form.openType,
    openCondition: form.openCondition || null,
    notOpenReason: form.notOpenReason || null,
    contactName: form.contactName || null,
    contactPhone: form.contactPhone || null,
    contactEmail: form.contactEmail || null,
    themeName: form.themeName || null,
    baseCatalogName: form.baseCatalogName || null,
    tags: form.tagList,
    updateCycle: form.updateCycle,
    description: form.description || null,
    secretFlag: form.secretFlag,
    metadataEntryCode: form.metadataEntryCode || null,
    dataSourceId: bindSourceId.value || null,
    sourcePathType: form.sourcePathType,
    physicalTableName: form.physicalTableName || bindTableName.value || null,
    qualityScore: form.qualityScore,
    extJson: ext,
    catalogOrigin: props.catalogOrigin,
  }
}

async function save() {
  if (wizardStep.value === 0 && !validateStep1()) return
  if (!validateStep2()) return
  saving.value = true
  try {
    const payload = buildPayload()
    if (editMode.value && editingId.value != null) {
      await api.put(`/governance/catalog/resources-mgmt/${editingId.value}`, payload)
      ElMessage.success('已更新')
    } else {
      await api.post('/governance/catalog/resources-mgmt', payload)
      ElMessage.success(`已创建，请到「${publishEntryName.value}」关联分类后发布`)
    }
    dialogVisible.value = false
    await loadResources()
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || '保存失败'
    ElMessage.error(String(msg))
  } finally {
    saving.value = false
  }
}

function addColumnRow() {
  columnRows.value.push({
    columnName: '',
    columnNameZh: '',
    dataType: 'VARCHAR',
    dataTypeZh: '字符串',
    sensLevel: '1级',
    shareLevel: form.shareType || 'CONDITIONAL',
    displayFlag: true,
    searchFlag: false,
    statFlag: false,
    sortFlag: false,
    remark: '',
  })
}

function removeColumnRow(idx: number) {
  columnRows.value.splice(idx, 1)
}

async function openDsPicker() {
  if (viewMode.value) return
  dsPickerVisible.value = true
  dsPickerSelected.value = null
  dsPickerCat.value = 'SOURCE'
  dsPickerKeyword.value = ''
  dsPickerName.value = ''
  await loadBindSources()
}

async function loadBindSources() {
  dsPickerLoading.value = true
  try {
    const res = await api.get('/governance/catalog/resources-mgmt/bind-sources', {
      params: {
        categoryKey: dsPickerCat.value || undefined,
        keyword: dsPickerName.value || dsPickerKeyword.value || undefined,
      },
    })
    dsPickerRows.value = res.data || []
  } catch {
    dsPickerRows.value = []
    ElMessage.error('加载数据源失败')
  } finally {
    dsPickerLoading.value = false
  }
}

function onDsPickerRowClick(row: BindSource) {
  dsPickerSelected.value = row
}

async function confirmDsPicker() {
  const row = dsPickerSelected.value
  if (!row) {
    ElMessage.warning('请先选择一条数据源')
    return
  }
  if (row.catalogable === false) {
    ElMessage.warning('治理库/过程层不可编目，请选择来源或原始库/主题专题')
    return
  }
  bindSourceId.value = row.id
  bindSourceName.value = row.sourceName
  bindTableName.value = ''
  form.metadataEntryCode = ''
  form.physicalTableName = ''
  columnRows.value = []
  if (isSuperAdmin.value && row.systemName && !form.providerOrg) {
    form.providerOrg = String(row.systemName)
  }
  dsPickerVisible.value = false
  await openTablePicker()
}

async function openTablePicker() {
  if (viewMode.value) return
  if (!bindSourceId.value) {
    ElMessage.warning('请先选择数据源')
    return
  }
  tablePickerVisible.value = true
  tablePickerSelected.value = bindTableName.value || ''
  tablePickerLoading.value = true
  try {
    const res = await api.get(`/governance/catalog/resources-mgmt/bind-sources/${bindSourceId.value}/tables`)
    tablePickerRows.value = res.data || []
  } catch (e: any) {
    tablePickerRows.value = []
    ElMessage.error(e?.response?.data?.message || '加载数据表失败')
  } finally {
    tablePickerLoading.value = false
  }
}

async function confirmTablePicker() {
  if (!tablePickerSelected.value) {
    ElMessage.warning('请选择数据表')
    return
  }
  const row = tablePickerRows.value.find((t) => t.tableName === tablePickerSelected.value)
  if (row && row.catalogable === false) {
    ElMessage.warning('该表不可编目（过程层）')
    return
  }
  tablePickerVisible.value = false
  await loadBindColumns(tablePickerSelected.value)
}

async function loadBindColumns(tableName: string) {
  if (!bindSourceId.value || !tableName) return
  columnsLoading.value = true
  try {
    const res = await api.get(
      `/governance/catalog/resources-mgmt/bind-sources/${bindSourceId.value}/table-columns`,
      { params: { tableName } },
    )
    const d = res.data || {}
    bindTableName.value = tableName
    form.physicalTableName = d.physicalTableName || tableName
    form.metadataEntryCode = d.metadataEntryCode || ''
    form.sourcePathType = d.sourcePathType || 'DIRECT'
    if (isSuperAdmin.value && d.ownerName && !form.providerOrg) form.providerOrg = d.ownerName
    if (!form.resourceName && (d.entryName || tableName)) {
      form.resourceName = d.entryName || tableName
    }
    const cols = (d.columns || []) as Array<{
      columnName?: string
      dataType?: string
      remarks?: string
      comment?: string
    }>
    const defaultShare = form.shareType === 'OPEN' || form.shareType === 'CONDITIONAL' || form.shareType === 'NOT_SHARE'
      ? form.shareType
      : 'CONDITIONAL'
    columnRows.value = cols.map((c) => {
      const dataType = c.dataType || 'VARCHAR'
      return {
        columnName: c.columnName || '',
        columnNameZh: c.remarks || c.comment || c.columnName || '',
        dataType,
        dataTypeZh: dataTypeToZh(dataType),
        sensLevel: '1级',
        shareLevel: defaultShare,
        displayFlag: true,
        searchFlag: false,
        statFlag: false,
        sortFlag: false,
        remark: '',
      }
    })
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载字段失败')
  } finally {
    columnsLoading.value = false
  }
}

function resetAddDsForm() {
  addDsStep.value = 0
  addDsAdapter.value = 'MYSQL'
  editingDsId.value = null
  addDsForm.sortOrder = 0
  addDsForm.sourceName = ''
  addDsForm.deptName = form.providerOrg || ''
  addDsForm.categoryName = '来源'
  addDsForm.systemId = undefined
  addDsForm.remark = ''
  addDsForm.sourceTag = ''
  addDsForm.readonlyFlag = true
  addDsForm.host = ''
  addDsForm.port = 3306
  addDsForm.database = ''
  addDsForm.username = ''
  addDsForm.password = ''
}

function openAddDsWizard() {
  resetAddDsForm()
  addDsVisible.value = true
  void loadAddDsProjects()
}

/** 编辑已有登记数据源（平台分层库不可编辑） */
async function openEditDs(row: BindSource) {
  if (row.platformLayer) {
    ElMessage.warning('平台分层库不可在此编辑，请到数据源登记维护')
    return
  }
  resetAddDsForm()
  editingDsId.value = row.id
  addDsAdapter.value = (row.sourceType || 'MYSQL').toUpperCase()
  addDsForm.sourceName = row.sourceName || ''
  addDsForm.deptName = form.providerOrg || row.providerOrg || row.systemName || ''
  addDsForm.categoryName = row.categoryName || '来源'
  addDsForm.database = row.databaseName || ''
  addDsVisible.value = true
  await loadAddDsProjects()
  try {
    const list = (await ingestionApi.dataSources()).data || []
    const ds = list.find((d) => d.id === row.id)
    if (!ds) {
      ElMessage.error('未找到该数据源详情')
      addDsVisible.value = false
      return
    }
    addDsForm.systemId = ds.systemId
    addDsForm.sourceName = ds.sourceName || row.sourceName
    try {
      const cfg = JSON.parse(ds.connConfigJson || '{}') as Record<string, unknown>
      addDsForm.host = String(cfg.host || '')
      addDsForm.port = Number(cfg.port || 3306)
      addDsForm.database = String(cfg.database || row.databaseName || '')
      addDsForm.username = String(cfg.username || '')
    } catch {
      /* keep defaults */
    }
    addDsForm.password = ''
    addDsStep.value = 1
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '加载数据源失败')
    addDsVisible.value = false
  }
}

async function loadAddDsProjects() {
  try {
    const res = await ingestionApi.projects()
    addDsProjects.value = (res.data || []).map((p) => ({ id: p.id, projectName: p.projectName }))
    addDsSystems.value = []
    for (const p of addDsProjects.value.slice(0, 8)) {
      try {
        const sys = await ingestionApi.systems(p.id)
        for (const s of sys.data || []) {
          addDsSystems.value.push({ id: s.id, systemName: `${p.projectName} / ${s.systemName}`, projectId: p.id })
        }
      } catch {
        /* skip */
      }
    }
  } catch {
    addDsProjects.value = []
    addDsSystems.value = []
  }
}

function onAdapterPick(type: string) {
  addDsAdapter.value = type
  const ad = DS_ADAPTERS.find((a) => a.type === type)
  if (ad) addDsForm.port = ad.port
}

function addDsGoNext() {
  if (!addDsAdapter.value) {
    ElMessage.warning('请选择适配器')
    return
  }
  addDsStep.value = 1
}

function validateAddDsForm(requirePassword: boolean): boolean {
  if (!addDsForm.sourceName.trim()) {
    ElMessage.warning('请填写名称')
    return false
  }
  if (!addDsForm.deptName.trim()) {
    ElMessage.warning('请填写部门名称')
    return false
  }
  if (!addDsForm.systemId) {
    ElMessage.warning('请选择所属系统（须挂到已登记项目系统下）')
    return false
  }
  if (!addDsForm.host.trim()) {
    ElMessage.warning('请填写数据库连接地址')
    return false
  }
  if (!addDsForm.port) {
    ElMessage.warning('请填写数据库端口')
    return false
  }
  if (!addDsForm.username.trim()) {
    ElMessage.warning('请填写用户名')
    return false
  }
  if (requirePassword && !addDsForm.password) {
    ElMessage.warning(editingDsId.value != null ? '请填写密码后再测试连接' : '请填写密码')
    return false
  }
  return true
}

/** 仅探测连通性，不落库创建/更新 */
async function testAddDsConnection() {
  const editKeepPwd = editingDsId.value != null && !addDsForm.password
  if (!validateAddDsForm(!editKeepPwd)) return
  addDsTesting.value = true
  try {
    if (editKeepPwd && editingDsId.value != null) {
      const tr = await ingestionApi.testDataSource(editingDsId.value)
      const ok = tr.data?.ok !== false && tr.data?.connStatus !== 'FAILED'
      ElMessage[ok ? 'success' : 'warning'](
        ok
          ? `连接成功${tr.data?.tableCount != null ? `，表数 ${tr.data.tableCount}` : ''}`
          : `连接失败：${tr.data?.message || ''}`,
      )
      return
    }
    const tr = await ingestionApi.testDataSourceConnection({
      sourceType: addDsAdapter.value,
      host: addDsForm.host.trim(),
      port: addDsForm.port,
      database: addDsForm.database,
      username: addDsForm.username.trim(),
      password: addDsForm.password,
    })
    const ok = tr.data?.ok !== false
    ElMessage[ok ? 'success' : 'warning'](
      ok
        ? `连接成功${tr.data?.tableCount != null ? `，表数 ${tr.data.tableCount}` : ''}（未保存，请点确定）`
        : `连接失败：${tr.data?.message || ''}`,
    )
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '连接测试失败')
  } finally {
    addDsTesting.value = false
  }
}

/** 确定后才创建/更新数据源 */
async function submitAddDs() {
  const isEdit = editingDsId.value != null
  if (!validateAddDsForm(!isEdit)) return
  const sys = addDsSystems.value.find((s) => s.id === addDsForm.systemId)
  addDsSaving.value = true
  try {
    const body: Record<string, unknown> = {
      systemId: addDsForm.systemId,
      projectId: sys?.projectId,
      sourceName: addDsForm.sourceName.trim(),
      sourceType: addDsAdapter.value,
      host: addDsForm.host.trim(),
      port: addDsForm.port,
      database: addDsForm.database,
      username: addDsForm.username.trim(),
      remark: addDsForm.remark,
    }
    if (addDsForm.password) body.password = addDsForm.password
    let id = editingDsId.value
    if (isEdit && id != null) {
      await ingestionApi.updateDataSource(id, body)
      ElMessage.success('数据源已更新')
    } else {
      id = (await ingestionApi.createDataSource(body)).data
      ElMessage.success('数据源已创建')
    }
    addDsVisible.value = false
    await loadBindSources()
    const selected = dsPickerRows.value.find((r) => r.id === id)
    if (selected) {
      dsPickerSelected.value = selected
      dsPickerCat.value = 'SOURCE'
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || (isEdit ? '更新数据源失败' : '创建数据源失败'))
  } finally {
    addDsSaving.value = false
  }
}

function addRequestParam() {
  requestParams.value.push({
    name: '',
    type: '字符串',
    required: false,
    description: '',
  })
}

function addResponseParam() {
  responseParams.value.push({
    name: '',
    type: '字符串',
    required: false,
    description: '',
  })
}

function addFileColumn() {
  fileColumns.value.push({
    columnName: '',
    columnNameZh: '',
    dataType: '字符串',
    required: false,
    description: '',
  })
}

function onFileSelected(uploadFile: { name?: string; size?: number; raw?: File }) {
  const raw = uploadFile?.raw
  const size = raw?.size ?? uploadFile?.size ?? 0
  if (size > FILE_MAX_BYTES) {
    ElMessage.warning('单文件不能超过 100M')
    return false
  }
  fileForm.fileName = uploadFile?.name || raw?.name || ''
  fileForm.fileSize = size
  fileForm.sheetName = ''
  fileSheetOptions.value = []
  // Excel 表头解析需额外依赖；此处预置一空列并开放 sheet 手填
  const lower = fileForm.fileName.toLowerCase()
  if (lower.endsWith('.xlsx') || lower.endsWith('.xls') || lower.endsWith('.csv')) {
    fileSheetOptions.value = lower.endsWith('.csv') ? ['默认'] : ['Sheet1']
    fileForm.sheetName = fileSheetOptions.value[0]
  }
  if (!fileColumns.value.length) addFileColumn()
  return false
}

async function removeOne(row: CatalogRes) {
  if (!canDeleteRow(row)) {
    ElMessage.warning(row.approvalStatus === 'PENDING' ? '审批中不可删除' : '已发布请先下线后再删除')
    return
  }
  const draft = isDraftRow(row)
  await ElMessageBox.confirm(
    draft
      ? `确认删除草稿「${row.resourceName}」？删除后不可恢复。`
      : `确认提交删除「${row.resourceName}」的审批？通过后才会删除。`,
    draft ? '删除草稿' : '删除审批',
    { type: 'warning' },
  )
  await api.delete(`/governance/catalog/resources-mgmt/${row.id}`)
  ElMessage.success(draft ? '已删除' : '已提交删除审批')
  await loadResources()
}

async function batchDelete() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先勾选资源')
    return
  }
  const deletable = selectedRows.value.filter(canDeleteRow)
  if (!deletable.length) {
    ElMessage.warning('所选记录均不可删除（审批中或已发布）')
    return
  }
  const draftCount = deletable.filter(isDraftRow).length
  const approvalCount = deletable.length - draftCount
  const tip =
    approvalCount > 0 && draftCount > 0
      ? `将直接删除 ${draftCount} 条草稿，并提交 ${approvalCount} 条删除审批，确认继续？`
      : draftCount > 0
        ? `确认直接删除 ${draftCount} 条草稿？删除后不可恢复。`
        : `确认提交 ${approvalCount} 条删除审批？通过后才会删除。`
  await ElMessageBox.confirm(tip, '批量删除', { type: 'warning' })
  await api.post('/governance/catalog/resources-mgmt/batch-delete', {
    ids: deletable.map((r) => r.id),
  })
  ElMessage.success('批量删除已处理')
  selectedRows.value = []
  await loadResources()
}

async function submitOffline(row: CatalogRes) {
  await ElMessageBox.confirm(`确认提交「${row.resourceName}」下线审批？`, '下线审批', { type: 'warning' })
  await api.post(`/governance/catalog/resources-mgmt/${row.id}/submit`, {
    actionType: 'OFFLINE',
    comment: '提交下线审批',
  })
  ElMessage.success('已提交下线审批')
  await loadResources()
}

/** 提交：草稿进入注册发布可见范围，并跳转目录注册发布页 */
async function tipGoPublish(row: CatalogRes) {
  if (!canSubmitToRegister(row)) {
    ElMessage.warning('当前状态不可提交')
    return
  }
  try {
    await api.post(`/governance/catalog/resources-mgmt/${row.id}/submit-register`)
    ElMessage.success(`已提交，可在「${publishEntryName.value}」关联分类并发布`)
    await loadResources()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '提交失败')
    return
  }
  if (props.catalogOrigin === 'INGEST') {
    await router.push({
      path: '/exchange/ingestion',
      query: { system: 'collect', module: 'catalog.publish' },
    })
  } else {
    await router.push({
      path: '/governance',
      query: { tab: 'catalog', cSub: 'publish' },
    })
  }
}

function approvalOf(row: CatalogRes) {
  return (row.approvalStatus || 'DRAFT').toUpperCase()
}

/** 草稿 / 驳回待提交 / 已撤回：可编辑提交删除 */
function isEditableCatalogRow(row: CatalogRes) {
  const st = approvalOf(row)
  return st === 'DRAFT' || st === 'REJECTED' || st === 'WITHDRAWN'
}

function canSubmitToRegister(row: CatalogRes) {
  if (row.publishStatus === 'PUBLISHED') return false
  return isEditableCatalogRow(row)
}

function isPendingRow(row: CatalogRes) {
  return approvalOf(row) === 'PENDING'
}

function isToRegisterRow(row: CatalogRes) {
  return approvalOf(row) === 'TO_REGISTER'
}

function openBatchColumnSetting() {
  if (!columnRows.value.length) {
    ElMessage.warning('请先选择数据表加载字段')
    return
  }
  batchColumnForm.sensLevel = '1级'
  batchColumnForm.shareLevel = 'CONDITIONAL'
  batchColumnForm.displayFlag = true
  batchColumnVisible.value = true
}

function applyBatchColumnSetting() {
  for (const row of columnRows.value) {
    row.sensLevel = batchColumnForm.sensLevel
    row.shareLevel = batchColumnForm.shareLevel
    row.displayFlag = batchColumnForm.displayFlag
  }
  batchColumnVisible.value = false
  ElMessage.success(`已批量设置 ${columnRows.value.length} 个字段`)
}

/** 待发布撤回为草稿 */
async function withdrawToDraft(row: CatalogRes) {
  await ElMessageBox.confirm(`确认撤回「${row.resourceName}」？撤回后回到草稿。`, '撤回', { type: 'warning' })
  await api.post(`/governance/catalog/resources-mgmt/${row.id}/withdraw-register`)
  ElMessage.success('已撤回为草稿')
  await loadResources()
}

/** 草稿：可直接删除（驳回待提交等同草稿直接删） */
function isDraftRow(row: CatalogRes) {
  const st = approvalOf(row)
  return st === 'DRAFT' || st === 'REJECTED' || st === 'WITHDRAWN'
}

/** 草稿/驳回可删；待发布/待审核/已发布不可删 */
function canDeleteRow(row: CatalogRes) {
  if (row.publishStatus === 'PUBLISHED') return false
  return isEditableCatalogRow(row)
}

async function openVersions(row: CatalogRes) {
  versionResource.value = row
  versionLeftNo.value = undefined
  versionRightNo.value = undefined
  versionDiff.value = null
  versionDrawerVisible.value = true
  try {
    versions.value = (await api.get(`/governance/catalog/resources-mgmt/${row.id}/versions`)).data || []
  } catch {
    ElMessage.error('加载版本历史失败')
  }
}

async function compareVersions() {
  if (!versionResource.value || versionLeftNo.value == null || versionRightNo.value == null) {
    ElMessage.warning('请选择两个版本')
    return
  }
  versionDiff.value = (
    await api.get(`/governance/catalog/resources-mgmt/${versionResource.value.id}/versions/diff`, {
      params: { leftNo: versionLeftNo.value, rightNo: versionRightNo.value },
    })
  ).data
}

function openImport() {
  importFormat.value = 'json'
  importContent.value = ''
  importVisible.value = true
}

async function submitImport() {
  if (!importContent.value.trim()) {
    ElMessage.warning('请粘贴导入内容')
    return
  }
  importLoading.value = true
  try {
    const res = await api.post('/governance/catalog/resources-mgmt/import', {
      format: importFormat.value,
      content: importContent.value.trim(),
    })
    const d = res.data || {}
    ElMessage.success(`导入完成：新增 ${d.created || 0}，更新 ${d.updated || 0}，跳过 ${d.skipped || 0}`)
    if (d.errors?.length) {
      ElMessage.warning(d.errors.slice(0, 3).join('；'))
    }
    importVisible.value = false
    await loadResources()
  } catch {
    ElMessage.error('导入失败')
  } finally {
    importLoading.value = false
  }
}

async function exportResources(format: 'json' | 'csv') {
  try {
    const res = await api.get('/governance/catalog/resources-mgmt/export', {
      params: { format },
    })
    const blob =
      format === 'csv'
        ? new Blob([String(res.data || '')], { type: 'text/csv;charset=utf-8' })
        : new Blob([JSON.stringify(res.data || [], null, 2)], { type: 'application/json;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `catalog-resources.${format}`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  }
}

onMounted(async () => {
  try {
    await Promise.all([loadTree(), loadCategoryOptions()])
    await loadResources()
  } catch {
    ElMessage.error('加载目录失败')
  }
})

onActivated(async () => {
  try {
    await Promise.all([loadCategoryOptions(), loadTree(), loadResources()])
  } catch {
    /* keep-alive 再次进入时静默刷新 */
  }
})
</script>

<template>
  <PageCard :title="pageTitle">
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="资源名称" class="portal-field-lg">
        <el-input
          v-model="query.resourceName"
          clearable
          placeholder="名称/编码/提供方"
          @keyup.enter="loadResources"
        />
      </el-form-item>
      <el-form-item label="所属分类" class="portal-field-xl">
        <el-select
          v-model="query.categoryId"
          clearable
          filterable
          placeholder="全部"
          @visible-change="onCategorySelectVisible"
        >
          <el-option v-for="c in categorySelectOptions" :key="c.id" :label="c.label" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" class="portal-field-md">
        <el-select v-model="query.approvalStatus" clearable placeholder="全部">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="待发布" value="TO_REGISTER" />
          <el-option label="待审核" value="PENDING" />
          <el-option label="已审核" value="APPROVED" />
          <el-option label="驳回待提交" value="REJECTED" />
          <el-option label="已下线" value="OFFLINE" />
        </el-select>
      </el-form-item>
      <el-form-item label="共享方式" class="portal-field-md">
        <el-select v-model="query.shareType" clearable placeholder="全部">
          <el-option v-for="(lab, val) in SHARE_ZH" :key="val" :label="lab" :value="val" />
        </el-select>
      </el-form-item>
      <el-form-item label="数据格式" class="portal-field-md">
        <el-select v-model="query.resourceFormat" clearable placeholder="全部">
          <el-option v-for="(lab, val) in FORMAT_ZH" :key="val" :label="lab" :value="val" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="loadResources">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
        <el-button type="primary" @click="openCreate">手动新增</el-button>
        <el-button @click="openBatchCreate">批量新增</el-button>
        <el-button @click="openImport">导入</el-button>
        <el-button @click="exportResources('json')">导出 JSON</el-button>
        <el-button @click="exportResources('csv')">导出 CSV</el-button>
        <el-button type="danger" plain :disabled="!selectedRows.length" @click="batchDelete">批量删除</el-button>
      </el-form-item>
    </el-form>

    <el-table
      v-loading="loading"
      :data="pagedResources"
      stripe
      size="small"
      @selection-change="(rows: CatalogRes[]) => (selectedRows = rows)"
    >
      <el-table-column type="selection" width="42" />
      <el-table-column prop="resourceName" label="名称" min-width="140" />
      <el-table-column label="来源路径" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.sourcePathType" size="small" :type="row.sourcePathType === 'PROCESSED' ? 'warning' : 'success'">
            {{ row.sourcePathType === 'PROCESSED' ? '加工共享' : '直通共享' }}
          </el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="providerOrg" label="提供方" width="110" show-overflow-tooltip />
      <el-table-column label="信息资源格式" width="110">
        <template #default="{ row }">{{ FORMAT_ZH[row.resourceFormat!] || $statusLabel(row.resourceFormat) || '—' }}</template>
      </el-table-column>
      <el-table-column label="共享方式" width="110">
        <template #default="{ row }">{{ SHARE_ZH[row.shareType!] || $statusLabel(row.shareType) || '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="statusTagType(catalogResourceStatusCode(row.approvalStatus, row.publishStatus))"
          >
            {{ catalogResourceStatusLabel(row.approvalStatus, row.publishStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <!-- 待发布：查看、撤回、版本 -->
          <template v-if="isToRegisterRow(row)">
            <el-button link type="primary" @click="openView(row)">查看</el-button>
            <el-button link type="info" @click="withdrawToDraft(row)">撤回</el-button>
            <el-button link @click="openVersions(row)">版本</el-button>
          </template>
          <!-- 待审核：查看、版本（撤回在注册发布） -->
          <template v-else-if="isPendingRow(row)">
            <el-button link type="primary" @click="openView(row)">查看</el-button>
            <el-button link @click="openVersions(row)">版本</el-button>
          </template>
          <!-- 草稿 / 驳回待提交：查看、编辑、提交、删除、版本 -->
          <template v-else-if="isEditableCatalogRow(row)">
            <el-button link type="primary" @click="openView(row)">查看</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="canSubmitToRegister(row)"
              link
              type="primary"
              @click="tipGoPublish(row)"
            >提交</el-button>
            <el-button v-if="canDeleteRow(row)" link type="danger" @click="removeOne(row)">删除</el-button>
            <el-button link @click="openVersions(row)">版本</el-button>
          </template>
          <!-- 已审核等：查看、版本；已发布可提交下线 -->
          <template v-else>
            <el-button link type="primary" @click="openView(row)">查看</el-button>
            <el-button
              v-if="row.publishStatus === 'PUBLISHED'"
              link
              @click="submitOffline(row)"
            >提交下线</el-button>
            <el-button link @click="openVersions(row)">版本</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <PortalPagination v-model:page="page" v-model:page-size="pageSize" :total="resourceTotal" />

    <el-dialog
      v-model="dialogVisible"
      :title="viewMode ? '查看数据资源' : editMode ? '编辑资源' : '手动新增数据资源'"
      width="960px"
      destroy-on-close
      append-to-body
      :close-on-click-modal="false"
    >
      <el-steps :active="wizardStep" finish-status="success" align-center style="margin-bottom: 20px">
        <el-step title="基本信息" />
        <el-step title="关联资源" />
      </el-steps>

      <!-- 步骤1：基本信息 -->
      <el-form v-show="wizardStep === 0" label-width="130px" :disabled="viewMode">
        <el-form-item label="信息资源名称" required>
          <el-input v-model="form.resourceName" maxlength="128" show-word-limit placeholder="清晰唯一，同提供方勿重名" />
        </el-form-item>
        <el-form-item label="信息资源代码">
          <el-input v-model="form.resourceCode" placeholder="可空，自动生成" />
        </el-form-item>
        <el-form-item label="信息资源提供方" required>
          <el-tree-select
            v-if="isSuperAdmin"
            v-model="form.providerOrg"
            :data="orgTree"
            filterable
            check-strictly
            :render-after-expand="false"
            placeholder="选择具体部门/提供方"
            style="width: 100%"
          />
          <el-input v-else :model-value="form.providerOrg || currentDeptName" disabled />
        </el-form-item>
        <el-form-item label="信息资源格式" required>
          <el-select v-model="form.resourceFormat" style="width: 100%" placeholder="决定下一步关联形态">
            <el-option label="库表" value="DATABASE" />
            <el-option label="接口" value="API" />
            <el-option label="文件" value="FILE" />
          </el-select>
          <div class="hint">选择「库表」后，下一步将选择数据源→数据表→维护字段共享属性</div>
        </el-form-item>
        <el-form-item label="共享类型" required>
          <el-select v-model="form.shareType" style="width: 100%">
            <el-option v-for="(lab, val) in SHARE_ZH" :key="val" :label="lab" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item label="共享条件" :required="shareConditionEnabled">
          <el-input
            v-model="form.shareCondition"
            :disabled="!shareConditionEnabled || viewMode"
            clearable
            maxlength="256"
            show-word-limit
            placeholder="有条件共享时必填"
          />
        </el-form-item>
        <el-form-item v-if="notShareReasonEnabled" label="不共享理由" required>
          <el-input v-model="form.notShareReason" type="textarea" :rows="2" placeholder="不予共享时必填" />
        </el-form-item>
        <el-form-item label="向社会开放" required>
          <el-select v-model="form.openType" style="width: 100%">
            <el-option v-for="(lab, val) in OPEN_ZH" :key="val" :label="lab" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item label="开放条件" :required="openConditionEnabled">
          <el-input
            v-model="form.openCondition"
            :disabled="!openConditionEnabled || viewMode"
            clearable
            maxlength="256"
            show-word-limit
            placeholder="开放时必填"
          />
        </el-form-item>
        <el-form-item v-if="notOpenReasonEnabled" label="不开放理由" required>
          <el-input v-model="form.notOpenReason" type="textarea" :rows="2" placeholder="不开放时必填" />
        </el-form-item>
        <el-form-item label="主题资源目录">
          <el-select
            v-model="form.themeName"
            filterable
            clearable
            style="width: 100%"
            :placeholder="`取自「主题资源目录」下一级，${categoryHint}`"
            @visible-change="(v: boolean) => v && ensureCategoryLoaded()"
          >
            <el-option
              v-for="o in themeCatalogOptions"
              :key="o.id"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="更新周期">
          <el-select v-model="form.updateCycle" style="width: 100%">
            <el-option v-for="(lab, val) in CYCLE_ZH" :key="val" :label="lab" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item label="信息资源分类">
          <el-select
            v-model="form.categoryId"
            clearable
            filterable
            style="width: 100%"
            :placeholder="`仅显示「基础资源目录」下一级（${categoryHint}）`"
            @visible-change="(v: boolean) => v && ensureCategoryLoaded()"
          >
            <el-option v-for="c in baseCategoryOptions" :key="c.id" :label="c.label" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contactName" placeholder="姓名" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" />
        </el-form-item>
        <el-form-item label="联系邮箱">
          <el-input v-model="form.contactEmail" />
        </el-form-item>
        <el-form-item label="标签">
          <div class="tag-editor">
            <el-tag
              v-for="tag in form.tagList"
              :key="tag"
              closable
              class="tag-editor__item"
              @close="removeTag(tag)"
            >
              {{ tag }}
            </el-tag>
            <el-input
              v-if="tagInputVisible"
              ref="tagInputRef"
              v-model="tagInputValue"
              class="tag-editor__input"
              size="small"
              maxlength="32"
              @keyup.enter="confirmAddTag"
              @blur="confirmAddTag"
            />
            <el-button v-else class="tag-editor__add" @click="showTagInput">+ 新增标签</el-button>
          </div>
        </el-form-item>
        <el-form-item label="目录描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>

      <!-- 步骤2：关联资源 -->
      <div v-show="wizardStep === 1">
        <!-- 库表 -->
        <template v-if="form.resourceFormat === 'DATABASE'">
          <el-form label-width="100px">
            <el-form-item label="关联资源" required>
              <div class="bind-row">
                <el-input
                  :model-value="bindSourceName"
                  readonly
                  placeholder="点击选择数据源"
                  style="flex: 1"
                  @click="openDsPicker"
                />
                <el-input
                  :model-value="bindTableName"
                  readonly
                  placeholder="选择数据表"
                  style="flex: 1"
                  @click="openTablePicker"
                />
              </div>
              <div v-if="form.metadataEntryCode" class="hint">
                元数据条目：{{ form.metadataEntryCode }}
                · {{ form.sourcePathType === 'PROCESSED' ? '加工共享' : '直通共享' }}
              </div>
              <div v-else-if="bindTableName" class="hint">
                物理表：{{ bindTableName }}
                · {{ form.sourcePathType === 'PROCESSED' ? '加工共享' : '直通共享' }}
              </div>
            </el-form-item>
          </el-form>
          <div v-if="columnRows.length && !viewMode" class="section-head">
            <span>字段共享属性</span>
            <el-button size="small" type="primary" plain @click="openBatchColumnSetting">批量设置</el-button>
          </div>
          <el-table
            v-loading="columnsLoading"
            :data="columnRows"
            size="small"
            stripe
            border
            max-height="360"
            empty-text="请先选择数据源与数据表，系统将自动带出字段"
          >
            <el-table-column prop="columnName" label="名称" width="140" show-overflow-tooltip />
            <el-table-column label="中文名称" min-width="140">
              <template #default="{ row }"><el-input v-model="row.columnNameZh" size="small" :disabled="viewMode" /></template>
            </el-table-column>
            <el-table-column label="类型" width="90">
              <template #default="{ row }">{{ row.dataTypeZh || dataTypeToZh(row.dataType) }}</template>
            </el-table-column>
            <el-table-column label="数据敏感级别" width="120">
              <template #default="{ row }">
                <el-select v-model="row.sensLevel" size="small" style="width: 100%" :disabled="viewMode">
                  <el-option v-for="o in SENS_LEVEL_OPTS" :key="o" :label="o" :value="o" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="共享类型" width="130">
              <template #default="{ row }">
                <el-select v-model="row.shareLevel" size="small" style="width: 100%" :disabled="viewMode">
                  <el-option v-for="(lab, val) in SHARE_LEVEL_ZH" :key="val" :label="lab" :value="val" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="是否展示项" width="90" align="center">
              <template #default="{ row }"><el-checkbox v-model="row.displayFlag" :disabled="viewMode" /></template>
            </el-table-column>
          </el-table>
        </template>

        <!-- 接口：基本信息 | 请求参数 | 响应参数 -->
        <template v-else-if="form.resourceFormat === 'API'">
          <el-tabs v-model="apiTab">
            <el-tab-pane label="基本信息" name="basic">
              <el-form label-width="120px" class="api-basic-form">
                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item label="接口名称" required>
                      <el-input v-model="apiForm.apiName" placeholder="请输入接口名称" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="接口版本">
                      <el-input v-model="apiForm.apiVersion" placeholder="如 v1" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="目标地址" required>
                      <el-input v-model="apiForm.apiUrl" placeholder="网关/服务根地址" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="请求路径" required>
                      <el-input v-model="apiForm.apiPath" placeholder="/api/xxx" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="请求方式" required>
                      <el-select v-model="apiForm.apiMethod" style="width: 100%">
                        <el-option label="GET" value="GET" />
                        <el-option label="POST" value="POST" />
                        <el-option label="PUT" value="PUT" />
                        <el-option label="DELETE" value="DELETE" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="请求超时(ms)" required>
                      <el-input-number v-model="apiForm.apiTimeout" :min="0" :step="500" controls-position="right" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="注册时间">
                      <el-date-picker v-model="apiForm.registerAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="失效时间">
                      <el-date-picker v-model="apiForm.expireAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="24">
                    <el-form-item label="接口描述">
                      <el-input v-model="apiForm.apiDescription" type="textarea" :rows="2" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="24">
                    <el-form-item label="返回结果示例">
                      <el-input v-model="apiForm.apiResultJson" type="textarea" :rows="5" placeholder="{}" class="code-area" />
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-form>
            </el-tab-pane>
            <el-tab-pane label="请求参数" name="req">
              <div class="section-head">
                <span>请求参数</span>
                <el-button type="primary" size="small" @click="addRequestParam">添加参数</el-button>
              </div>
              <el-table :data="requestParams" size="small" stripe border max-height="360">
                <el-table-column label="参数名" min-width="140">
                  <template #default="{ row }"><el-input v-model="row.name" size="small" placeholder="参数名" /></template>
                </el-table-column>
                <el-table-column label="是否必填" width="110">
                  <template #default="{ row }">
                    <el-select v-model="row.required" size="small" style="width: 100%">
                      <el-option label="是" :value="true" />
                      <el-option label="否" :value="false" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="类型" width="130">
                  <template #default="{ row }">
                    <el-select v-model="row.type" size="small" style="width: 100%">
                      <el-option v-for="t in PARAM_TYPE_OPTS" :key="t" :label="t" :value="t" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="简介" min-width="160">
                  <template #default="{ row }"><el-input v-model="row.description" size="small" /></template>
                </el-table-column>
                <el-table-column label="操作" width="70" align="center">
                  <template #default="{ $index }">
                    <el-button link type="primary" @click="requestParams.splice($index, 1)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="响应参数" name="resp">
              <div class="section-head">
                <span>响应参数</span>
                <el-button type="primary" size="small" @click="addResponseParam">添加参数</el-button>
              </div>
              <el-table :data="responseParams" size="small" stripe border max-height="360">
                <el-table-column label="参数名" min-width="140">
                  <template #default="{ row }"><el-input v-model="row.name" size="small" placeholder="参数名" /></template>
                </el-table-column>
                <el-table-column label="是否必须" width="110">
                  <template #default="{ row }">
                    <el-select v-model="row.required" size="small" style="width: 100%">
                      <el-option label="是" :value="true" />
                      <el-option label="否" :value="false" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="类型" width="130">
                  <template #default="{ row }">
                    <el-select v-model="row.type" size="small" style="width: 100%">
                      <el-option v-for="t in PARAM_TYPE_OPTS" :key="t" :label="t" :value="t" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="简介" min-width="160">
                  <template #default="{ row }"><el-input v-model="row.description" size="small" /></template>
                </el-table-column>
                <el-table-column label="操作" width="70" align="center">
                  <template #default="{ $index }">
                    <el-button link type="primary" @click="responseParams.splice($index, 1)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </template>

        <!-- 文件：上传 + 列信息 -->
        <template v-else-if="form.resourceFormat === 'FILE'">
          <el-form label-width="110px" style="margin-bottom: 12px">
            <el-form-item label="关联文件" required>
              <div class="bind-row">
                <el-upload
                  :auto-upload="false"
                  :show-file-list="false"
                  :on-change="onFileSelected"
                  accept=".xlsx,.xls,.csv,.json,.txt,.xml"
                >
                  <el-button type="primary">选择文件（&lt;100M）</el-button>
                </el-upload>
                <el-input v-model="fileForm.fileName" placeholder="文件名称" style="flex: 1" />
              </div>
              <div v-if="fileForm.fileSize" class="hint">大小：{{ Math.round(fileForm.fileSize / 1024) }} KB</div>
            </el-form-item>
            <el-form-item v-if="fileSheetOptions.length" label="工作表">
              <el-select v-model="fileForm.sheetName" style="width: 240px" allow-create filterable>
                <el-option v-for="s in fileSheetOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
            <el-form-item label="文件说明">
              <el-input v-model="fileForm.fileRemark" type="textarea" :rows="2" placeholder="可选" />
            </el-form-item>
          </el-form>
          <div class="section-head">
            <span>文件列信息（须至少一行）</span>
            <el-button size="small" type="primary" link @click="addFileColumn">+ 添加列</el-button>
          </div>
          <el-table :data="fileColumns" size="small" stripe border max-height="320" empty-text="请添加列信息">
            <el-table-column label="列名" min-width="120">
              <template #default="{ row }"><el-input v-model="row.columnName" size="small" /></template>
            </el-table-column>
            <el-table-column label="中文名称" min-width="120">
              <template #default="{ row }"><el-input v-model="row.columnNameZh" size="small" /></template>
            </el-table-column>
            <el-table-column label="是否必填" width="110">
              <template #default="{ row }">
                <el-select v-model="row.required" size="small" style="width: 100%">
                  <el-option label="是" :value="true" />
                  <el-option label="否" :value="false" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="130">
              <template #default="{ row }">
                <el-select v-model="row.dataType" size="small" style="width: 100%">
                  <el-option v-for="t in PARAM_TYPE_OPTS" :key="t" :label="t" :value="t" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="简介" min-width="140">
              <template #default="{ row }"><el-input v-model="row.description" size="small" /></template>
            </el-table-column>
            <el-table-column width="70" align="center">
              <template #default="{ $index }">
                <el-button link type="primary" @click="fileColumns.splice($index, 1)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>

      <div v-if="viewMode" v-loading="approvalFlowLoading" class="approval-flow-block">
        <div class="approval-flow-title">审批流程</div>
        <el-table :data="approvalFlowRows" stripe size="small" empty-text="暂无审批记录">
          <el-table-column label="操作类型" width="100">
            <template #default="{ row }">{{ ACTION_FLOW_ZH[row.actionType || ''] || statusLabel(row.actionType) || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="submittedBy" label="提交人" width="120" show-overflow-tooltip />
          <el-table-column prop="submittedAt" label="提交时间" width="166" />
          <el-table-column prop="reviewedBy" label="审核人" width="120" show-overflow-tooltip />
          <el-table-column prop="reviewedAt" label="审核时间" width="166" />
          <el-table-column prop="reviewComment" label="审批结果/意见" min-width="140" show-overflow-tooltip />
        </el-table>
      </div>

      <template #footer>
        <template v-if="viewMode">
          <el-button @click="dialogVisible = false">关闭</el-button>
          <el-button v-if="wizardStep === 1" @click="goPrev">上一步</el-button>
          <el-button v-if="wizardStep === 0" type="primary" @click="goNext">下一步</el-button>
        </template>
        <template v-else>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button v-if="wizardStep === 1" @click="goPrev">上一步</el-button>
          <el-button v-if="wizardStep === 0" type="primary" @click="goNext">下一步</el-button>
          <el-button v-if="wizardStep === 1" type="primary" :loading="saving" @click="save">提交</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 选择数据源 -->
    <el-dialog
      v-model="dsPickerVisible"
      title="选择数据源"
      width="900px"
      destroy-on-close
      append-to-body
      :close-on-click-modal="false"
    >
      <div class="ds-picker">
        <aside class="ds-picker-side">
          <el-input v-model="dsPickerKeyword" clearable placeholder="请输入名称" size="small" style="margin-bottom: 8px" />
          <div
            v-for="c in BIND_CATEGORY_OPTS"
            :key="c.key"
            class="ds-cat"
            :class="{ active: dsPickerCat === c.key }"
            @click="dsPickerCat = c.key; loadBindSources()"
          >
            {{ c.label }}
          </div>
        </aside>
        <div class="ds-picker-main">
          <el-form inline class="portal-inline-form portal-inline-form--sm" size="small">
            <el-form-item label="数据源名称" class="portal-field-lg">
              <el-input v-model="dsPickerName" clearable placeholder="请输入数据源名称" @keyup.enter="loadBindSources" />
            </el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button type="primary" @click="loadBindSources">查询</el-button>
              <el-button @click="dsPickerName = ''; loadBindSources()">重置</el-button>
              <el-button type="primary" @click="openAddDsWizard">+ 新增</el-button>
            </el-form-item>
          </el-form>
          <el-table
            v-loading="dsPickerLoading"
            :data="dsPickerRows.filter((r) => !dsPickerKeyword || r.sourceName?.includes(dsPickerKeyword))"
            size="small"
            stripe
            highlight-current-row
            max-height="380"
            @row-click="onDsPickerRowClick"
          >
            <el-table-column prop="sourceName" label="名称" min-width="140" show-overflow-tooltip />
            <el-table-column label="版本" width="90">
              <template #default="{ row }">{{ row.versionLabel || '—' }}</template>
            </el-table-column>
            <el-table-column label="所属分类" width="100">
              <template #default="{ row }">{{ row.categoryName || '—' }}</template>
            </el-table-column>
            <el-table-column label="提供部门" width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.providerOrg || row.systemName || '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="70">
              <template #default="{ row }">
                <el-button link type="primary" @click.stop="openEditDs(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <template #footer>
        <el-button @click="dsPickerVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmDsPicker">确定</el-button>
      </template>
    </el-dialog>

    <!-- 选择数据表 -->
    <el-dialog v-model="tablePickerVisible" title="选择数据表" width="720px" destroy-on-close append-to-body>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="选择数据表后将自动带出字段清单，可维护敏感级别与共享属性。"
        style="margin-bottom: 12px"
      />
      <el-table
        v-loading="tablePickerLoading"
        :data="tablePickerRows"
        size="small"
        stripe
        highlight-current-row
        max-height="360"
        @row-click="(row: BindTable) => (tablePickerSelected = row.tableName)"
      >
        <el-table-column width="40">
          <template #default="{ row }">
            <el-radio v-model="tablePickerSelected" :value="row.tableName">&nbsp;</el-radio>
          </template>
        </el-table-column>
        <el-table-column prop="tableName" label="表名" min-width="140" />
        <el-table-column label="中文名称" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.chineseName || row.tableComment || row.entryName || '—' }}</template>
        </el-table-column>
        <el-table-column label="元数据" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.metadataEntryCode || '未登记' }}</template>
        </el-table-column>
        <el-table-column label="可编目" width="80">
          <template #default="{ row }">{{ row.catalogable === false ? '否' : '是' }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="tablePickerVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmTablePicker">确定</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑数据源：适配器 → 连接信息 -->
    <el-dialog
      v-model="addDsVisible"
      :title="addDsDialogTitle"
      width="720px"
      destroy-on-close
      append-to-body
      :close-on-click-modal="false"
    >
      <template v-if="addDsStep === 0">
        <div class="adapter-title">选择适配器</div>
        <div class="adapter-grid">
          <div
            v-for="a in DS_ADAPTERS"
            :key="a.type"
            class="adapter-card"
            :class="{ active: addDsAdapter === a.type }"
            @click="onAdapterPick(a.type)"
          >
            <div class="adapter-label">{{ a.label }}</div>
            <div class="adapter-type">{{ a.type }}</div>
          </div>
        </div>
      </template>
      <template v-else>
        <el-form label-width="140px">
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="排序"><el-input-number v-model="addDsForm.sortOrder" :min="0" style="width: 100%" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="名称" required><el-input v-model="addDsForm.sourceName" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="部门名称" required><el-input v-model="addDsForm.deptName" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="所属分类" required><el-input v-model="addDsForm.categoryName" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="所属系统" required>
                <el-select v-model="addDsForm.systemId" filterable style="width: 100%" placeholder="挂到已登记项目系统">
                  <el-option v-for="s in addDsSystems" :key="s.id" :label="s.systemName" :value="s.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="备注"><el-input v-model="addDsForm.remark" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="数据源标签" required>
                <el-select v-model="addDsForm.sourceTag" filterable allow-create style="width: 100%" placeholder="请选择">
                  <el-option label="业务库" value="业务库" />
                  <el-option label="主题库" value="主题库" />
                  <el-option label="测试库" value="测试库" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="是否只读">
                <el-checkbox v-model="addDsForm.readonlyFlag">只读</el-checkbox>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="db_host(数据库连接地址)" required>
            <el-input v-model="addDsForm.host" placeholder="主机或 JDBC 地址" />
          </el-form-item>
          <el-form-item label="db_port(数据库端口)" required>
            <el-input-number v-model="addDsForm.port" :min="1" :max="65535" style="width: 100%" />
          </el-form-item>
          <el-form-item label="db_name(数据库名称)">
            <el-input v-model="addDsForm.database" />
          </el-form-item>
          <el-form-item label="username(用户名)" required>
            <el-input v-model="addDsForm.username" />
          </el-form-item>
          <el-form-item label="password(密码)">
            <el-input
              v-model="addDsForm.password"
              type="password"
              show-password
              :placeholder="editingDsId != null ? '留空则保持原密码；测试连接须填写' : ''"
            />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="addDsVisible = false">取消</el-button>
        <el-button v-if="addDsStep === 1" @click="addDsStep = 0">上一步</el-button>
        <el-button v-if="addDsStep === 0" type="primary" @click="addDsGoNext">下一步</el-button>
        <template v-if="addDsStep === 1">
          <el-button type="primary" :loading="addDsTesting" @click="testAddDsConnection">测试连接</el-button>
          <el-button type="primary" :loading="addDsSaving" @click="submitAddDs">确定</el-button>
        </template>
      </template>
    </el-dialog>

    <el-drawer v-model="versionDrawerVisible" :title="`版本历史 · ${versionResource?.resourceName || ''}`" size="520px">
      <el-table :data="versions" stripe size="small">
        <el-table-column prop="versionNo" label="版本" width="70" />
        <el-table-column prop="changeSummary" label="摘要" min-width="120" show-overflow-tooltip />
        <el-table-column prop="publishedBy" label="发布人" width="90" />
        <el-table-column prop="publishedAt" label="时间" width="150" />
      </el-table>
      <el-divider />
      <el-form inline class="portal-inline-form">
        <el-form-item label="左版本" class="portal-field-sm">
          <el-select v-model="versionLeftNo" clearable placeholder="v">
            <el-option v-for="v in versions" :key="'l' + v.id" :label="`v${v.versionNo}`" :value="v.versionNo" />
          </el-select>
        </el-form-item>
        <el-form-item label="右版本" class="portal-field-sm">
          <el-select v-model="versionRightNo" clearable placeholder="v">
            <el-option v-for="v in versions" :key="'r' + v.id" :label="`v${v.versionNo}`" :value="v.versionNo" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="compareVersions">对比</el-button>
        </el-form-item>
      </el-form>
      <template v-if="versionDiff">
        <el-alert
          :type="versionDiff.sameSnapshot ? 'success' : 'info'"
          :closable="false"
          :title="versionDiff.sameSnapshot ? '两版本快照相同' : '存在字段差异'"
          style="margin-bottom: 12px"
        />
        <el-table :data="versionDiff.basicDiff || []" stripe size="small">
          <el-table-column prop="field" label="字段" width="120" />
          <el-table-column prop="left" label="左版本" />
          <el-table-column prop="right" label="右版本" />
        </el-table>
      </template>
    </el-drawer>

    <el-dialog v-model="importVisible" title="批量导入" width="560px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="格式">
          <el-radio-group v-model="importFormat">
            <el-radio value="json">JSON 数组</el-radio>
            <el-radio value="csv">CSV 文本</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="importContent" type="textarea" :rows="10" :placeholder="importPlaceholder" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="submitImport">导入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchColumnVisible" title="批量设置字段属性" width="480px" destroy-on-close append-to-body>
      <el-form label-width="120px">
        <el-form-item label="数据敏感级别">
          <el-select v-model="batchColumnForm.sensLevel" style="width: 100%">
            <el-option v-for="o in SENS_LEVEL_OPTS" :key="o" :label="o" :value="o" />
          </el-select>
        </el-form-item>
        <el-form-item label="共享类型">
          <el-select v-model="batchColumnForm.shareLevel" style="width: 100%">
            <el-option v-for="(lab, val) in SHARE_LEVEL_ZH" :key="val" :label="lab" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否展示项">
          <el-switch v-model="batchColumnForm.displayFlag" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchColumnVisible = false">取消</el-button>
        <el-button type="primary" @click="applyBatchColumnSetting">应用到全部字段</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchVisible" title="批量新增数据资源" width="640px" destroy-on-close>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="从已登记库表/元数据中多选，抽取核心元数据生成标准资源目录（已编目对象自动跳过）。"
        style="margin-bottom: 12px"
      />
      <el-form label-width="100px">
        <el-form-item label="选择库表" required>
          <el-select
            v-model="batchEntryCodes"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            :loading="batchMetaLoading"
            style="width: 100%"
            placeholder="多选可编目对象（排除过程层 DWD）"
          >
            <el-option
              v-for="m in batchMetaOptions"
              :key="m.entryCode"
              :label="`${m.entryName}（${m.physicalTableName || m.entryCode} · ${m.dataLayer || '?'}）`"
              :value="m.entryCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="共享方式">
          <el-select v-model="batchShareType" style="width: 100%">
            <el-option v-for="(lab, val) in SHARE_ZH" :key="val" :label="lab" :value="val" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchLoading" @click="submitBatchCreate">生成目录</el-button>
      </template>
    </el-dialog>
  </PageCard>
</template>

<style scoped>
.approval-flow-block {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.approval-flow-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
}
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
  margin-top: 4px;
}
.hint.warn {
  color: var(--el-color-warning);
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 8px 0 10px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.bind-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
.tag-editor {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.tag-editor__item {
  margin: 0;
}
.tag-editor__input {
  width: 120px;
}
.tag-editor__add {
  border-style: dashed;
}
.ds-picker {
  display: flex;
  gap: 12px;
  min-height: 420px;
}
.ds-picker-side {
  width: 160px;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-lighter);
  padding-right: 10px;
}
.ds-cat {
  padding: 8px 10px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.ds-cat:hover {
  background: var(--el-fill-color-light);
}
.ds-cat.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}
.ds-picker-main {
  flex: 1;
  min-width: 0;
}
.adapter-title {
  margin-bottom: 12px;
  font-size: 14px;
  color: var(--el-text-color-regular);
}
.adapter-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.adapter-card {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 18px 12px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.adapter-card:hover {
  border-color: var(--el-color-primary-light-5);
}
.adapter-card.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary-light-7);
  background: var(--el-color-primary-light-9);
}
.adapter-label {
  font-size: 15px;
  font-weight: 600;
}
.adapter-type {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.code-area :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}
</style>
