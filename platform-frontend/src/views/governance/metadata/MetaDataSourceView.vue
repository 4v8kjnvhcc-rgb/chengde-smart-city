<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import iconMysql from '@/assets/db-adapters/mysql.svg'
import iconOracle from '@/assets/db-adapters/oracle.svg'
import iconDm from '@/assets/db-adapters/dm.svg'
import iconPostgresql from '@/assets/db-adapters/postgresql.svg'
import iconKingbase from '@/assets/db-adapters/kingbase.svg'
import iconGbase from '@/assets/db-adapters/gbase.svg'
import iconHbase from '@/assets/db-adapters/hbase.svg'
import iconHive from '@/assets/db-adapters/hive.svg'
import { withPasswordTransport } from '@/utils/transport-crypto'

interface CategoryNode {
  id: number
  label: string
  categoryCode?: string
  layerCode?: string
  children?: CategoryNode[]
}

interface DataSourceRow {
  id: number
  sourceName: string
  sortOrder?: number
  deptName?: string
  orgId?: number
  orgName?: string
  categoryId: number
  categoryName?: string
  tagCategoryId?: number
  tagCategoryName?: string
  belongSystem?: string
  remarks?: string
  adapterType?: string
  realtimeFlag?: number
  readOnlyFlag?: number
  dbHost?: string
  dbPort?: number
  dbName?: string
  dbSchema?: string
  username?: string
  status: string
}

interface OrgRow {
  id: number
  orgName: string
  parentId?: number
}

interface AdapterItem {
  code: string
  label: string
  icon: string
  desc?: string
}

const adapters: AdapterItem[] = [
  { code: 'MYSQL', label: 'MySQL', icon: iconMysql, desc: '关系型数据库' },
  { code: 'ORACLE', label: 'Oracle', icon: iconOracle, desc: '企业级数据库' },
  { code: 'DM', label: '达梦', icon: iconDm, desc: '国产数据库' },
  { code: 'POSTGRESQL', label: 'PostgreSQL', icon: iconPostgresql, desc: '开源数据库' },
  { code: 'KINGBASE', label: '人大金仓', icon: iconKingbase, desc: '国产数据库' },
  { code: 'GBASE', label: 'GBase', icon: iconGbase, desc: '分析型数据库' },
  { code: 'HBASE', label: 'HBase', icon: iconHbase, desc: '分布式列存' },
  { code: 'HIVE', label: 'Hive', icon: iconHive, desc: '数据仓库' },
]

const wizardSteps = ['选择适配器', '基本信息', '连接配置']

const ROOT_CATEGORY: CategoryNode = { id: 0, label: '数据源分类' }

const treeKeyword = ref('')
const tableKeyword = ref('')
const treeLoading = ref(false)
const tableLoading = ref(false)
const treeData = ref<CategoryNode[]>([])
const rows = ref<DataSourceRow[]>([])
const selectedNode = ref<CategoryNode | null>(null)
const treeRef = ref<{ setCurrentKey: (key: number) => void } | null>(null)

const wizardVisible = ref(false)
const wizardStep = ref(0)
const saving = ref(false)
const testing = ref(false)
const editingId = ref<number | null>(null)

const orgTree = ref<Array<{ id: number; label: string; children?: any[] }>>([])
const orgPickerVisible = ref(false)
const orgKeyword = ref('')
const moveVisible = ref(false)
const moveTargetId = ref<number | null>(null)
const movingRow = ref<DataSourceRow | null>(null)

const collectVisible = ref(false)
const collectLoading = ref(false)
const collectSaving = ref(false)
const collectProjects = ref<CollectProject[]>([])
const collectSelectedIds = ref<number[]>([])
const collectKeyword = ref('')

interface CollectFlatRow {
  id: number
  deptName: string
  sourceName: string
  dbHost?: string
  dbPort?: number | string
  dbName?: string
  username?: string
  password?: string
  collected?: boolean
}

interface CollectDataSource {
  id: number
  sourceName: string
  dbHost?: string
  dbPort?: number | string
  dbName?: string
  username?: string
  password?: string
  collected?: boolean
}

interface CollectSystem {
  id: number
  systemName: string
  dataSourceCount?: number
  dataSources: CollectDataSource[]
}

interface CollectProject {
  id: number
  projectName: string
  deptName?: string
  boundOrgId?: number
  registerStatus?: string
  systems?: CollectSystem[]
}

const form = reactive({
  adapterType: 'MYSQL',
  sourceName: '',
  sortOrder: 1,
  deptName: '',
  orgId: null as number | null,
  orgName: '',
  categoryId: null as number | null,
  categoryName: '',
  belongSystem: '',
  remarks: '',
  realtimeFlag: false,
  readOnlyFlag: false,
  dbHost: '',
  dbPort: 3306,
  dbName: '',
  dbSchema: '',
  username: '',
  password: '',
})

const {
  page,
  pageSize,
  paged: pagedRows,
  total,
  resetPage,
} = useClientPager(rows)

const selectedLabel = computed(() => {
  if (!selectedNode.value || selectedNode.value.id === 0) return '全部'
  return selectedNode.value.label
})

const isRootCategory = computed(() => selectedNode.value?.id === 0)

const isSourceCategory = computed(() => {
  const n = selectedNode.value
  if (!n) return false
  return n.label === '来源'
    || n.categoryCode === 'CAT_SOURCE'
    || n.layerCode === 'SOURCE'
})

const categorySelectOptions = computed(() => flattenCategories(treeData.value))

const selectedAdapter = computed(() => adapters.find((a) => a.code === form.adapterType))

function pickStr(obj: Record<string, unknown>, ...keys: string[]) {
  for (const k of keys) {
    const v = obj[k]
    if (v != null && String(v).trim() !== '') return String(v)
  }
  return undefined
}

function mapCollectDataSource(ds: CollectDataSource & Record<string, unknown>, deptName: string): CollectFlatRow {
  return {
    id: ds.id,
    deptName,
    sourceName: ds.sourceName,
    dbHost: pickStr(ds, 'dbHost', 'db_host', 'host'),
    dbPort: ds.dbPort ?? ds.db_port ?? ds.port,
    dbName: pickStr(ds, 'dbName', 'db_name', 'database'),
    username: pickStr(ds, 'username', 'user'),
    password: pickStr(ds, 'password'),
    collected: ds.collected,
  }
}

const collectFlatRows = computed<CollectFlatRow[]>(() => {
  const out: CollectFlatRow[] = []
  for (const project of collectProjects.value) {
    const deptName = project.deptName?.trim() || '—'
    for (const system of project.systems || []) {
      for (const ds of system.dataSources || []) {
        out.push(mapCollectDataSource(ds as CollectDataSource & Record<string, unknown>, deptName))
      }
    }
  }
  return out.sort((a, b) => a.deptName.localeCompare(b.deptName, 'zh-CN') || a.sourceName.localeCompare(b.sourceName, 'zh-CN'))
})

const collectFilteredRows = computed(() => {
  const kw = collectKeyword.value.trim().toLowerCase()
  const base = collectFlatRows.value.filter((r) => !r.collected)
  if (!kw) return base
  return base.filter((r) => [
    r.deptName,
    r.sourceName,
    r.dbHost,
    r.dbName,
  ].some((v) => String(v || '').toLowerCase().includes(kw)))
})

const collectSelectableRows = computed(() => collectFilteredRows.value)

const collectAllChecked = computed({
  get() {
    const selectable = collectSelectableRows.value
    return selectable.length > 0 && selectable.every((r) => collectSelectedIds.value.includes(r.id))
  },
  set(checked: boolean) {
    if (checked) {
      const ids = new Set(collectSelectedIds.value)
      for (const r of collectSelectableRows.value) ids.add(r.id)
      collectSelectedIds.value = Array.from(ids)
    } else {
      const remove = new Set(collectSelectableRows.value.map((r) => r.id))
      collectSelectedIds.value = collectSelectedIds.value.filter((id) => !remove.has(id))
    }
  },
})

const collectStats = computed(() => {
  const available = collectFlatRows.value.filter((r) => !r.collected).length
  const selected = collectSelectedIds.value.length
  return { total: available, available, selected }
})

const treeWithRoot = computed(() => [{
  id: 0,
  label: '数据源分类',
  children: treeData.value,
}])

const filteredOrgTree = computed(() => {
  const kw = orgKeyword.value.trim()
  if (!kw) return orgTree.value
  const filterNodes = (nodes: typeof orgTree.value): typeof orgTree.value => {
    const out: typeof orgTree.value = []
    for (const n of nodes) {
      const children = n.children?.length ? filterNodes(n.children) : []
      if (n.label.includes(kw) || children.length) {
        out.push({ ...n, children: children.length ? children : undefined })
      }
    }
    return out
  }
  return filterNodes(orgTree.value)
})

function defaultPort(type: string) {
  if (type === 'ORACLE') return 1521
  if (type === 'POSTGRESQL' || type === 'POSTGRES') return 5432
  return 3306
}

async function loadTree() {
  treeLoading.value = true
  try {
    const res = await api.get('/governance/platform/metadata/source-categories/tree', {
      params: { keyword: treeKeyword.value.trim() || undefined },
    })
    treeData.value = res.data || []
    await nextTick()
    if (!selectedNode.value) {
      selectRootNode()
    } else if (selectedNode.value.id === 0) {
      selectRootNode()
    } else {
      const still = findNode(treeData.value, selectedNode.value.id)
      if (still) selectNode(still)
      else selectRootNode()
    }
  } catch {
    ElMessage.error('加载分类树失败')
  } finally {
    treeLoading.value = false
  }
}

function findNode(nodes: CategoryNode[], id: number): CategoryNode | null {
  for (const n of nodes) {
    if (n.id === id) return n
    if (n.children?.length) {
      const hit = findNode(n.children, id)
      if (hit) return hit
    }
  }
  return null
}

async function loadRows() {
  tableLoading.value = true
  try {
    const categoryId = selectedNode.value?.id
    const res = await api.get('/governance/platform/metadata/data-sources', {
      params: {
        categoryId: categoryId && categoryId > 0 ? categoryId : undefined,
        keyword: tableKeyword.value.trim() || undefined,
      },
    })
    rows.value = res.data || []
    resetPage()
  } catch {
    ElMessage.error('加载数据源失败')
  } finally {
    tableLoading.value = false
  }
}

async function loadOrgs() {
  if (orgTree.value.length) return
  try {
    const list = ((await api.get('/system/orgs')).data || []) as OrgRow[]
    const map = new Map<number, { id: number; label: string; children: any[] }>()
    for (const r of list) {
      map.set(r.id, { id: r.id, label: r.orgName, children: [] })
    }
    const roots: typeof orgTree.value = []
    for (const r of list) {
      const node = map.get(r.id)!
      if (!r.parentId || r.parentId === 0 || !map.has(r.parentId)) roots.push(node)
      else map.get(r.parentId)!.children.push(node)
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

function selectRootNode() {
  selectedNode.value = { ...ROOT_CATEGORY }
  treeRef.value?.setCurrentKey(0)
  loadRows()
}

function selectNode(node: CategoryNode) {
  selectedNode.value = node
  treeRef.value?.setCurrentKey(node.id)
  loadRows()
}

function onTreeClick(node: CategoryNode) {
  if (node.id === 0) {
    selectRootNode()
    return
  }
  selectNode(node)
}

function resetForm() {
  editingId.value = null
  wizardStep.value = 0
  form.adapterType = 'MYSQL'
  form.sourceName = ''
  form.sortOrder = 1
  form.deptName = ''
  form.orgId = null
  form.orgName = ''
  form.categoryId = selectedNode.value?.id ?? null
  form.categoryName = selectedNode.value?.label ?? ''
  form.belongSystem = ''
  form.remarks = ''
  form.realtimeFlag = false
  form.readOnlyFlag = false
  form.dbHost = ''
  form.dbPort = defaultPort('MYSQL')
  form.dbName = ''
  form.dbSchema = ''
  form.username = ''
  form.password = ''
}

function openCreateWizard() {
  resetForm()
  wizardVisible.value = true
}

async function openCreate() {
  if (!selectedNode.value || isRootCategory.value) {
    ElMessage.warning('请先在左侧选择具体分类')
    return
  }
  if (isSourceCategory.value) {
    try {
      await ElMessageBox.confirm(
        '是否同步数据资产登记管理系统的数据源？',
        '新增数据源',
        {
          confirmButtonText: '是',
          cancelButtonText: '否',
          distinguishCancelAndClose: true,
          type: 'info',
        },
      )
      await openCollect()
    } catch (action) {
      if (action === 'cancel') openCreateWizard()
    }
    return
  }
  openCreateWizard()
}

async function openEdit(row: DataSourceRow) {
  try {
    const res = await api.get(`/governance/platform/metadata/data-sources/${row.id}`)
    const d = res.data as DataSourceRow & { password?: string }
    editingId.value = row.id
    wizardStep.value = 1
    form.adapterType = d.adapterType || 'MYSQL'
    form.sourceName = d.sourceName || ''
    form.sortOrder = d.sortOrder != null && d.sortOrder >= 1 ? d.sortOrder : 1
    form.deptName = d.deptName || ''
    form.orgId = d.orgId ?? null
    form.orgName = d.orgName || ''
    form.categoryId = d.categoryId
    form.categoryName = d.categoryName || ''
    form.belongSystem = d.belongSystem || ''
    form.remarks = d.remarks || ''
    form.realtimeFlag = d.realtimeFlag === 1
    form.readOnlyFlag = d.readOnlyFlag !== 0
    form.dbHost = d.dbHost || ''
    form.dbPort = d.dbPort ?? defaultPort(form.adapterType)
    form.dbName = d.dbName || ''
    form.dbSchema = d.dbSchema || ''
    form.username = d.username || ''
    form.password = ''
    wizardVisible.value = true
  } catch {
    ElMessage.error('加载详情失败')
  }
}

function selectAdapter(code: string) {
  form.adapterType = code
  form.dbPort = defaultPort(code)
}

function onCategoryChange(categoryId: number) {
  const hit = categorySelectOptions.value.find((c) => c.id === categoryId)
  form.categoryId = categoryId
  form.categoryName = hit?.label || ''
}

function nextStep() {
  if (wizardStep.value === 0) {
    if (!form.adapterType) {
      ElMessage.warning('请选择适配器')
      return
    }
    wizardStep.value = 1
    return
  }
  if (wizardStep.value === 1) {
    if (!form.sourceName.trim()) {
      ElMessage.warning('请填写名称')
      return
    }
    if (!form.orgId) {
      ElMessage.warning('请选择部门组织')
      return
    }
    if (!form.categoryId) {
      ElMessage.warning('请选择所属分类')
      return
    }
    wizardStep.value = 2
    return
  }
}

function prevStep() {
  if (wizardStep.value > 0) wizardStep.value -= 1
}

function openOrgPicker() {
  orgKeyword.value = ''
  orgPickerVisible.value = true
  void loadOrgs()
}

function onOrgPick(data: { id: number; label: string }) {
  form.orgId = data.id
  form.orgName = data.label
  form.deptName = data.label
  orgPickerVisible.value = false
}

function buildPayload(includePassword = true) {
  const body: Record<string, unknown> = {
    adapterType: form.adapterType,
    sourceName: form.sourceName.trim(),
    sortOrder: form.sortOrder,
    deptName: form.deptName.trim(),
    orgId: form.orgId,
    orgName: form.orgName || undefined,
    categoryId: form.categoryId,
    belongSystem: form.belongSystem.trim() || undefined,
    remarks: form.remarks.trim() || undefined,
    realtimeFlag: form.realtimeFlag ? 1 : 0,
    readOnlyFlag: form.readOnlyFlag ? 1 : 0,
    dbHost: form.dbHost.trim(),
    dbPort: form.dbPort,
    dbName: form.dbName.trim() || undefined,
    dbSchema: form.dbSchema.trim() || undefined,
    username: form.username.trim(),
  }
  if (editingId.value) {
    body.id = editingId.value
  }
  return body
}

async function buildEncryptedPayload(includePassword = true) {
  return withPasswordTransport(buildPayload(includePassword), includePassword ? form.password : null)
}

async function testConnection() {
  if (!form.dbHost.trim() || !form.username.trim()) {
    ElMessage.warning('请先填写连接地址与用户名')
    return
  }
  if (!form.password && !editingId.value) {
    ElMessage.warning('请填写密码后再测试连接')
    return
  }
  testing.value = true
  try {
    await api.post(
      '/governance/platform/metadata/data-sources/test-connection',
      await buildEncryptedPayload(true),
    )
    ElMessage.success('连接成功')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '连接失败')
  } finally {
    testing.value = false
  }
}

async function submitForm() {
  if (!form.dbHost.trim()) {
    ElMessage.warning('请填写数据库连接地址')
    return
  }
  if (!form.username.trim()) {
    ElMessage.warning('请填写用户名')
    return
  }
  if (!form.password && !editingId.value) {
    ElMessage.warning('请填写密码')
    return
  }
  saving.value = true
  try {
    const payload = await buildEncryptedPayload(true)
    if (editingId.value) {
      await api.put(`/governance/platform/metadata/data-sources/${editingId.value}`, payload)
      ElMessage.success('已保存')
    } else {
      await api.post('/governance/platform/metadata/data-sources', payload)
      ElMessage.success('已新增')
    }
    wizardVisible.value = false
    await loadRows()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeRow(row: DataSourceRow) {
  try {
    await ElMessageBox.confirm(`确认删除数据源「${row.sourceName}」？`, '删除确认', { type: 'warning' })
    await api.delete(`/governance/platform/metadata/data-sources/${row.id}`)
    ElMessage.success('已删除')
    await loadRows()
  } catch (e: unknown) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

function openMove(row: DataSourceRow) {
  movingRow.value = row
  // 虚拟根节点 id=0 不在可选分类中，不能作为默认目标（否则下拉会显示原始值「0」）
  const id = selectedNode.value?.id
  moveTargetId.value = id && id > 0 ? id : null
  moveVisible.value = true
}

async function confirmMove() {
  if (!movingRow.value || moveTargetId.value == null || moveTargetId.value <= 0) {
    ElMessage.warning('请选择目标分类')
    return
  }
  try {
    await api.put(`/governance/platform/metadata/data-sources/${movingRow.value.id}/move`, {
      categoryId: moveTargetId.value,
    })
    ElMessage.success('已移动')
    moveVisible.value = false
    await loadRows()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '移动失败')
  }
}

function flattenCategories(nodes: CategoryNode[], out: Array<{ id: number; label: string }> = []) {
  for (const n of nodes) {
    out.push({ id: n.id, label: n.label })
    if (n.children?.length) flattenCategories(n.children, out)
  }
  return out
}

const moveCategoryOptions = computed(() => flattenCategories(treeData.value))

async function openCollect() {
  if (!isSourceCategory.value || !selectedNode.value) {
    ElMessage.warning('请先在左侧选择「来源」分类')
    return
  }
  collectVisible.value = true
  collectSelectedIds.value = []
  collectKeyword.value = ''
  await loadCollectCandidates()
}

async function loadCollectCandidates() {
  collectLoading.value = true
  try {
    const res = await api.get('/governance/platform/metadata/data-sources/collect/candidates')
    collectProjects.value = res.data || []
  } catch {
    ElMessage.error('加载归集数据源失败')
    collectProjects.value = []
  } finally {
    collectLoading.value = false
  }
}

function onCollectCheck(id: number, checked: boolean, collected?: boolean) {
  if (collected) return
  if (checked) {
    if (!collectSelectedIds.value.includes(id)) {
      collectSelectedIds.value = [...collectSelectedIds.value, id]
    }
  } else {
    collectSelectedIds.value = collectSelectedIds.value.filter((x) => x !== id)
  }
}

function isCollectSourceChecked(id: number) {
  return collectSelectedIds.value.includes(id)
}

function onCollectRowClick(row: CollectFlatRow) {
  onCollectCheck(row.id, !isCollectSourceChecked(row.id), false)
}

function formatConnAddr(row: CollectFlatRow) {
  if (!row.dbHost) return '—'
  return row.dbPort ? `${row.dbHost}:${row.dbPort}` : row.dbHost
}

function formatCell(v?: string | number | null) {
  if (v == null || String(v).trim() === '') return '—'
  return String(v)
}

async function confirmCollect() {
  if (!selectedNode.value?.id) return
  if (!collectSelectedIds.value.length) {
    ElMessage.warning('请至少选择一个数据源')
    return
  }
  collectSaving.value = true
  try {
    const res = await api.post('/governance/platform/metadata/data-sources/collect', {
      categoryId: selectedNode.value.id,
      ingSourceIds: collectSelectedIds.value,
    })
    const created = Number(res.data?.created || 0)
    const skipped = Number(res.data?.skipped || 0)
    ElMessage.success(`采集完成：新增 ${created} 条${skipped ? `，跳过 ${skipped} 条（已存在）` : ''}`)
    collectVisible.value = false
    await loadRows()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '采集失败')
  } finally {
    collectSaving.value = false
  }
}

onMounted(async () => {
  await loadTree()
})
</script>

<template>
  <PageCard title="数据源管理">
    <div class="mds-layout">
      <aside class="mds-tree-pane" v-loading="treeLoading">
        <div class="mds-pane-title">数据源管理</div>
        <el-input
          v-model="treeKeyword"
          clearable
          placeholder="请输入名称"
          class="mds-search"
          @keyup.enter="loadTree"
        />
        <el-tree
          ref="treeRef"
          class="mds-tree"
          node-key="id"
          highlight-current
          default-expand-all
          :data="treeWithRoot"
          :props="{ label: 'label', children: 'children' }"
          @node-click="onTreeClick"
        />
      </aside>

      <section class="mds-table-pane" v-loading="tableLoading">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="数据源名称" class="portal-field-lg">
            <el-input v-model="tableKeyword" clearable placeholder="请输入数据源名称" @keyup.enter="loadRows" />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="loadRows">查询</el-button>
            <el-button @click="tableKeyword = ''; loadRows()">重置</el-button>
            <el-button type="primary" :disabled="!selectedNode || isRootCategory" @click="openCreate">+ 新增</el-button>
          </el-form-item>
        </el-form>

        <div v-if="selectedNode" class="mds-context">当前分类：{{ selectedLabel }}</div>

        <el-table :data="pagedRows" stripe size="small" empty-text="暂无数据">
          <el-table-column prop="sourceName" label="名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="deptName" label="部门名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="categoryName" label="所属分类" min-width="120" show-overflow-tooltip />
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="primary" @click="openMove(row)">移动</el-button>
              <el-button link type="danger" @click="removeRow(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination v-if="total" v-model:page="page" v-model:page-size="pageSize" :total="total" />
      </section>
    </div>

    <!-- 新增/编辑向导 -->
    <el-dialog
      v-model="wizardVisible"
      :title="editingId ? '编辑数据源' : '新增数据源'"
      width="820px"
      class="mds-wizard-dialog"
      destroy-on-close
      @closed="resetForm"
    >
      <el-steps :active="wizardStep" align-center finish-status="success" class="mds-wizard-steps">
        <el-step v-for="(title, idx) in wizardSteps" :key="idx" :title="title" />
      </el-steps>

      <!-- Step 1: 选适配器 -->
      <div v-if="wizardStep === 0" class="mds-wizard-body">
        <div class="mds-step-head">
          <div class="mds-step-head__title">选择适配器</div>
          <div class="mds-step-head__desc">请选择要连接的数据库类型，后续将展示对应连接参数</div>
        </div>
        <div class="mds-adapter-grid">
          <button
            v-for="item in adapters"
            :key="item.code"
            type="button"
            class="mds-adapter-card"
            :class="{ active: form.adapterType === item.code }"
            @click="selectAdapter(item.code)"
          >
            <img :src="item.icon" :alt="item.label" class="mds-adapter-logo" />
            <span class="mds-adapter-name">{{ item.label }}</span>
            <span v-if="item.desc" class="mds-adapter-desc">{{ item.desc }}</span>
            <span v-if="form.adapterType === item.code" class="mds-adapter-check">✓</span>
          </button>
        </div>
      </div>

      <!-- Step 2: 基本信息 -->
      <div v-else-if="wizardStep === 1" class="mds-wizard-body">
        <div class="mds-step-head">
          <div class="mds-step-head__title">填写基本信息</div>
          <div class="mds-step-head__desc">
            已选适配器：
            <img v-if="selectedAdapter" :src="selectedAdapter.icon" alt="" class="mds-step-head__icon" />
            <strong>{{ selectedAdapter?.label || form.adapterType }}</strong>
          </div>
        </div>
        <div class="mds-form-panel">
          <el-form label-width="108px" class="mds-form-grid">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="排序">
                  <el-input-number v-model="form.sortOrder" :min="1" controls-position="right" class="mds-full-width" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="名称" required>
                  <el-input v-model="form.sourceName" maxlength="128" placeholder="请输入数据源名称" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="部门名称" required>
                  <div class="mds-org-field">
                    <el-input
                      v-model="form.deptName"
                      readonly
                      maxlength="128"
                      placeholder="请选择组织"
                      class="mds-readonly-input"
                    />
                    <el-button plain type="primary" @click="openOrgPicker">选择组织</el-button>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="所属分类" required>
                  <el-select
                    v-model="form.categoryId"
                    filterable
                    placeholder="请选择或搜索分类"
                    class="mds-full-width"
                    @change="onCategoryChange"
                  >
                    <el-option
                      v-for="opt in categorySelectOptions"
                      :key="opt.id"
                      :label="opt.label"
                      :value="opt.id"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="所属系统">
                  <el-input
                    v-model="form.belongSystem"
                    maxlength="128"
                    clearable
                    placeholder="可自定义填写，如：政务共享交换平台"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="备注">
                  <el-input v-model="form.remarks" maxlength="512" placeholder="选填" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="属性">
                  <div class="mds-flag-box">
                    <el-checkbox v-model="form.realtimeFlag">检测是否支持实时</el-checkbox>
                    <el-checkbox v-model="form.readOnlyFlag">是否只读</el-checkbox>
                  </div>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </div>
      </div>

      <!-- Step 3: 连接配置（含密码） -->
      <div v-else class="mds-wizard-body">
        <div class="mds-step-head">
          <div class="mds-step-head__title">连接配置</div>
          <div class="mds-step-head__desc">填写数据库连接地址、端口、库名与账号密码</div>
        </div>
        <div class="mds-form-panel">
          <div class="mds-summary-bar">
            <span>分类：{{ form.categoryName || '—' }}</span>
            <span>{{ form.readOnlyFlag ? '只读' : '可写' }}</span>
          </div>
          <el-form label-width="168px">
            <el-form-item label="db_host(数据库连接地址)" required>
              <el-input v-model="form.dbHost" placeholder="例如 192.168.1.10 或 db.example.com" />
            </el-form-item>
            <el-form-item label="db_port(数据库端口)" required>
              <el-input-number v-model="form.dbPort" :min="1" :max="65535" controls-position="right" class="mds-full-width" />
            </el-form-item>
            <el-form-item label="db_name(数据库名称)">
              <el-input v-model="form.dbName" placeholder="库名（可选）" />
            </el-form-item>
            <el-form-item label="username(用户名)" required>
              <el-input v-model="form.username" autocomplete="off" placeholder="数据库用户名" />
            </el-form-item>
            <el-form-item label="password(密码)" :required="!editingId">
              <el-input
                v-model="form.password"
                type="password"
                show-password
                autocomplete="new-password"
                :placeholder="editingId ? '留空则不修改' : '请输入密码'"
              />
            </el-form-item>
            <el-form-item label="db_schema(SCHEMA)">
              <el-input v-model="form.dbSchema" placeholder="Schema（可选）" />
            </el-form-item>
          </el-form>
        </div>
      </div>

      <template #footer>
        <div class="mds-wizard-footer">
          <el-button @click="wizardVisible = false">取消</el-button>
          <div class="mds-wizard-footer__actions">
            <el-button v-if="wizardStep === 2" :loading="testing" type="primary" plain @click="testConnection">测试连接</el-button>
            <el-button v-if="wizardStep > 0" plain @click="prevStep">上一步</el-button>
            <el-button v-if="wizardStep < 2" type="primary" @click="nextStep">下一步</el-button>
            <el-button v-else type="primary" :loading="saving" @click="submitForm">确定</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 来源分类：从归集登记采集 -->
    <el-dialog v-model="collectVisible" title="同步数据源" width="860px" destroy-on-close class="mds-collect-dialog">
      <div class="mds-collect-hero">
        <div class="mds-collect-hero__title">选择要纳入「来源」的数据源</div>
        <div class="mds-collect-hero__desc">按部门展示数据资产登记管理系统的数据源，勾选后添加到「来源」分类</div>
      </div>

      <div class="mds-collect-stats">
        <span>可采集 <strong>{{ collectStats.available }}</strong> 条</span>
        <span v-if="collectStats.selected">已选 <strong class="is-primary">{{ collectStats.selected }}</strong> 条</span>
      </div>

      <div class="mds-collect-toolbar">
        <el-input
          v-model="collectKeyword"
          clearable
          placeholder="搜索部门、数据源或地址"
          class="mds-collect-search"
        />
        <el-checkbox
          v-model="collectAllChecked"
          :disabled="!collectSelectableRows.length"
          class="mds-collect-select-all"
        >
          全选当前列表
        </el-checkbox>
      </div>

      <div v-loading="collectLoading" class="mds-collect-table-wrap">
        <el-table
          :data="collectFilteredRows"
          stripe
          size="default"
          height="380"
          empty-text="暂无可采集数据源"
          @row-click="onCollectRowClick"
        >
          <el-table-column width="52" align="center">
            <template #default="{ row }">
              <el-checkbox
                :model-value="isCollectSourceChecked(row.id)"
                @click.stop
                @change="(v: boolean) => onCollectCheck(row.id, v, false)"
              />
            </template>
          </el-table-column>
          <el-table-column prop="deptName" label="部门" min-width="140" show-overflow-tooltip />
          <el-table-column prop="sourceName" label="数据源名称" min-width="140" show-overflow-tooltip />
          <el-table-column label="地址" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ formatConnAddr(row) }}</template>
          </el-table-column>
          <el-table-column label="库名" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ formatCell(row.dbName) }}</template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <div class="mds-collect-footer">
          <span v-if="collectStats.selected" class="mds-collect-footer__hint">将采集 {{ collectStats.selected }} 个数据源到「来源」</span>
          <span v-else class="mds-collect-footer__hint">请勾选需要采集的数据源</span>
          <div class="mds-collect-footer__actions">
            <el-button @click="collectVisible = false">取消</el-button>
            <el-button type="primary" :loading="collectSaving" :disabled="!collectSelectedIds.length" @click="confirmCollect">
              确认添加
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 组织选择 -->
    <el-dialog v-model="orgPickerVisible" title="选择组织" width="480px" append-to-body destroy-on-close>
      <el-input v-model="orgKeyword" clearable placeholder="搜索组织名称" class="mds-search" />
      <el-tree
        class="mds-org-tree"
        node-key="id"
        highlight-current
        default-expand-all
        :data="filteredOrgTree"
        :props="{ label: 'label', children: 'children' }"
        @node-click="onOrgPick"
      />
    </el-dialog>

    <!-- 移动分类 -->
    <el-dialog v-model="moveVisible" title="移动数据源" width="420px" destroy-on-close>
      <el-form label-width="88px">
        <el-form-item label="目标分类" required>
          <el-select v-model="moveTargetId" filterable placeholder="请选择" style="width: 100%">
            <el-option v-for="opt in moveCategoryOptions" :key="opt.id" :label="opt.label" :value="opt.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="moveVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmMove">确定</el-button>
      </template>
    </el-dialog>
  </PageCard>
</template>

<style scoped>
.mds-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 16px;
  min-height: 520px;
}

.mds-tree-pane,
.mds-table-pane {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 12px;
  background: #fff;
}

.mds-pane-title {
  font-weight: 600;
  margin-bottom: 10px;
}

.mds-search {
  margin-bottom: 10px;
}

.mds-tree {
  max-height: 460px;
  overflow: auto;
}

.mds-context {
  margin-bottom: 10px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.mds-step-title {
  font-weight: 600;
  margin-bottom: 16px;
}

:deep(.mds-wizard-dialog .el-dialog__body) {
  padding-top: 8px;
}

.mds-wizard-steps {
  margin-bottom: 20px;
}

.mds-wizard-body {
  min-height: 320px;
}

.mds-step-head {
  margin-bottom: 16px;
}

.mds-step-head__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.mds-step-head__desc {
  margin-top: 6px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
}

.mds-step-head__icon {
  width: 20px;
  height: 20px;
  border-radius: 4px;
}

.mds-form-panel {
  background: linear-gradient(180deg, #f8fafc 0%, #f3f6fb 100%);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  padding: 20px 20px 4px;
}

.mds-full-width {
  width: 100%;
}

.mds-readonly-input :deep(.el-input__wrapper) {
  background: #eef2f7;
}

.mds-flag-box {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  min-height: 32px;
  align-items: center;
  padding: 4px 0;
}

.mds-summary-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fff;
  border: 1px dashed var(--el-border-color);
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.mds-wizard-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.mds-wizard-footer__actions {
  display: flex;
  gap: 8px;
}

.mds-adapter-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  max-height: 380px;
  overflow: auto;
  padding: 4px 2px 8px;
}

.mds-adapter-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 118px;
  padding: 14px 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.mds-adapter-card:hover {
  transform: translateY(-2px);
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 8px 20px rgba(64, 158, 255, 0.12);
}

.mds-adapter-card.active {
  border-color: var(--el-color-primary);
  background: linear-gradient(180deg, #fff 0%, #f0f7ff 100%);
  box-shadow: 0 0 0 1px var(--el-color-primary-light-7), 0 10px 24px rgba(64, 158, 255, 0.16);
}

.mds-adapter-logo {
  width: 52px;
  height: 52px;
  object-fit: contain;
}

.mds-adapter-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.mds-adapter-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.mds-adapter-check {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 12px;
  line-height: 20px;
  text-align: center;
}

.mds-adapter-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}

.mds-org-field {
  display: flex;
  gap: 8px;
  width: 100%;
}

.mds-org-field .el-input {
  flex: 1;
}

.mds-org-tree {
  margin-top: 10px;
  max-height: 360px;
  overflow: auto;
}

.mds-collect-hero {
  padding: 14px 16px;
  border-radius: 10px;
  background: linear-gradient(135deg, #f0f7ff 0%, #f8fbff 100%);
  border: 1px solid #dbeafe;
  margin-bottom: 14px;
}

.mds-collect-hero__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.mds-collect-hero__desc {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.mds-collect-stats {
  display: flex;
  gap: 20px;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.mds-collect-stats strong {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.mds-collect-stats .is-primary {
  color: var(--el-color-primary);
}

.mds-collect-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
}

.mds-collect-search {
  flex: 1;
}

.mds-collect-select-all {
  flex-shrink: 0;
  white-space: nowrap;
}

.mds-collect-table-wrap {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  overflow: hidden;
}

.mds-collect-table-wrap :deep(.el-table__row) {
  cursor: pointer;
}

.mds-collect-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.mds-collect-footer__hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.mds-collect-footer__actions {
  display: flex;
  gap: 8px;
}

@media (max-width: 960px) {
  .mds-layout {
    grid-template-columns: 1fr;
  }

  .mds-adapter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
