<script setup lang="ts">
import { onMounted, reactive, ref, watch, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TableInstance } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { statusLabel, statusTagType } from '@/utils/status-label'
import {
  groupSourcesByRole,
  loadQualitySourceOptions,
  loadQualityTables,
  type QualitySourceOption,
  type QualityTableMeta,
} from '../quality/useQualityTargetPicker'

interface DomainRow {
  id: number
  domainCode: string
  domainName: string
  description?: string
  status: string
  entityCount?: number
}

interface EntityRow {
  id: number
  domainId: number
  entityCode: string
  entityName: string
  description?: string
  status: string
}

interface FieldRow {
  id: number
  entityId: number
  fieldCode: string
  fieldName: string
  dataType: string
  nullableFlag?: number
  pkFlag?: number
  sortOrder?: number
}

interface RelationRow {
  id: number
  relationCode: string
  relationName: string
  fromEntityId: number
  toEntityId: number
  relationType: string
}

interface PhysicalRow {
  id: number
  entityId: number
  physicalCode: string
  tableName: string
  datasourceId?: number
  status: string
}

type DetailTab = 'entities' | 'relations' | 'fields' | 'physical'

const router = useRouter()

const domains = ref<DomainRow[]>([])
const {
  page: domainPage,
  pageSize: domainPageSize,
  paged: pagedDomains,
  total: domainTotal,
  resetPage: resetDomainPage,
} = useClientPager(domains)
const selectedDomainId = ref<number | null>(null)
const entities = ref<EntityRow[]>([])
const entityQuery = reactive({ keyword: '', status: '' })
const filteredEntities = computed(() => {
  const kw = entityQuery.keyword.trim().toLowerCase()
  return entities.value.filter((e) => {
    if (kw && !`${e.entityCode || ''} ${e.entityName || ''}`.toLowerCase().includes(kw)) return false
    if (entityQuery.status && e.status !== entityQuery.status) return false
    return true
  })
})
const {
  page: entityPage,
  pageSize: entityPageSize,
  paged: pagedEntities,
  total: entityTotal,
  resetPage: resetEntityPage,
} = useClientPager(filteredEntities)
const fields = ref<FieldRow[]>([])
const fieldQuery = reactive({ keyword: '', dataType: '' })
const filteredFields = computed(() => {
  const kw = fieldQuery.keyword.trim().toLowerCase()
  return fields.value.filter((f) => {
    if (kw && !`${f.fieldCode || ''} ${f.fieldName || ''}`.toLowerCase().includes(kw)) return false
    if (fieldQuery.dataType && String(f.dataType || '').toUpperCase() !== fieldQuery.dataType.toUpperCase()) return false
    return true
  })
})
const relations = ref<RelationRow[]>([])
const relationQuery = reactive({ keyword: '', relationType: '' })
const filteredRelations = computed(() => {
  const kw = relationQuery.keyword.trim().toLowerCase()
  return relations.value.filter((r) => {
    if (kw) {
      const from = entities.value.find((e) => e.id === r.fromEntityId)?.entityName || ''
      const to = entities.value.find((e) => e.id === r.toEntityId)?.entityName || ''
      const text = `${r.relationCode || ''} ${r.relationName || ''} ${from} ${to}`.toLowerCase()
      if (!text.includes(kw)) return false
    }
    if (relationQuery.relationType && r.relationType !== relationQuery.relationType) return false
    return true
  })
})
const physicals = ref<PhysicalRow[]>([])
const physicalQuery = reactive({ keyword: '', status: '' })
const filteredPhysicals = computed(() => {
  const kw = physicalQuery.keyword.trim().toLowerCase()
  return physicals.value.filter((p) => {
    if (kw && !`${p.physicalCode || ''} ${p.tableName || ''}`.toLowerCase().includes(kw)) return false
    if (physicalQuery.status && p.status !== physicalQuery.status) return false
    return true
  })
})
const {
  page: physicalPage,
  pageSize: physicalPageSize,
  paged: pagedPhysicals,
  total: physicalTotal,
  resetPage: resetPhysicalPage,
} = useClientPager(filteredPhysicals)
const selectedEntityId = ref<number | null>(null)
const loading = ref(false)
const detailLoading = ref(false)
const detailTab = ref<DetailTab>('entities')
const sources = ref<QualitySourceOption[]>([])
const sourceGroups = computed(() => groupSourcesByRole(sources.value))
const tables = ref<QualityTableMeta[]>([])
const tablesLoading = ref(false)
const previewVisible = ref(false)
const previewRows = ref<Record<string, unknown>[]>([])
const previewTitle = ref('')

const entityTableRef = ref<TableInstance>()

const domainDlg = ref(false)
const entityDlg = ref(false)
const fieldDlg = ref(false)
const relationDlg = ref(false)
const physicalDlg = ref(false)

const domainForm = reactive({ id: null as number | null, domainCode: '', domainName: '', description: '' })
const entityForm = reactive({ id: null as number | null, entityCode: '', entityName: '', description: '' })
const fieldForm = reactive({
  id: null as number | null, fieldCode: '', fieldName: '', dataType: 'VARCHAR', pkFlag: 0, sortOrder: 0,
})
const relationForm = reactive({
  relationCode: '', relationName: '', fromEntityId: undefined as number | undefined,
  toEntityId: undefined as number | undefined, relationType: 'ONE_TO_MANY',
})
const physicalForm = reactive({
  entityId: undefined as number | undefined,
  physicalCode: '',
  tableName: '',
  datasourceId: -3 as number | undefined,
  ddlSql: '',
})

const selectedDomain = computed(() => domains.value.find((d) => d.id === selectedDomainId.value) || null)
const selectedEntity = computed(() => entities.value.find((e) => e.id === selectedEntityId.value) || null)
const exportingReport = ref(false)

async function loadDomains() {
  loading.value = true
  try {
    domains.value = (await api.get('/governance/fusion/models/domains')).data || []
    resetDomainPage()
    if (!selectedDomainId.value && domains.value.length) {
      selectedDomainId.value = domains.value[0].id
    }
  } catch {
    ElMessage.error('加载业务域失败')
  } finally {
    loading.value = false
  }
}

async function loadDomainDetail() {
  if (!selectedDomainId.value) {
    entities.value = []
    relations.value = []
    fields.value = []
    physicals.value = []
    selectedEntityId.value = null
    return
  }
  detailLoading.value = true
  try {
    const tree = (await api.get(`/governance/fusion/models/domains/${selectedDomainId.value}/tree`)).data
    entities.value = (tree.entities || []).map((n: { entity: EntityRow }) => n.entity)
    resetEntityPage()
    relations.value = tree.relations || []
    if (!selectedEntityId.value && entities.value.length) {
      selectedEntityId.value = entities.value[0].id
    } else if (selectedEntityId.value && !entities.value.find((e) => e.id === selectedEntityId.value)) {
      selectedEntityId.value = entities.value[0]?.id ?? null
    }
    await nextTick()
    syncEntityCurrentRow()
  } catch {
    ElMessage.error('加载模型树失败')
  } finally {
    detailLoading.value = false
  }
}

async function loadEntityDetail() {
  if (!selectedEntityId.value) {
    fields.value = []
    physicals.value = []
    return
  }
  try {
    fields.value = (await api.get('/governance/fusion/models/fields', { params: { entityId: selectedEntityId.value } })).data || []
    physicals.value = (await api.get('/governance/fusion/models/physical', { params: { entityId: selectedEntityId.value } })).data || []
    resetPhysicalPage()
  } catch {
    ElMessage.error('加载字段/物理映射失败')
  }
}

function selectDomain(row: DomainRow) {
  if (selectedDomainId.value === row.id) return
  selectedEntityId.value = null
  selectedDomainId.value = row.id
  detailTab.value = 'entities'
}

function selectEntity(row: EntityRow | undefined) {
  selectedEntityId.value = row?.id ?? null
}

function onPickLogicalEntity(id: number | null) {
  selectedEntityId.value = id
  void nextTick(() => syncEntityCurrentRow())
}

function syncEntityCurrentRow() {
  const row = entities.value.find((e) => e.id === selectedEntityId.value)
  entityTableRef.value?.setCurrentRow(row || undefined)
}

function onDetailTab(name: string | number) {
  detailTab.value = String(name) as DetailTab
}

function openDomainCreate() {
  domainForm.id = null
  domainForm.domainCode = ''
  domainForm.domainName = ''
  domainForm.description = ''
  domainDlg.value = true
}

async function saveDomain() {
  if (!domainForm.domainCode.trim() || !domainForm.domainName.trim()) {
    ElMessage.warning('请填写编码与名称')
    return
  }
  if (domainForm.id) {
    await api.put(`/governance/fusion/models/domains/${domainForm.id}`, domainForm)
    ElMessage.success('业务域已更新')
  } else {
    await api.post('/governance/fusion/models/domains', domainForm)
    ElMessage.success('业务域已创建')
  }
  domainDlg.value = false
  await loadDomains()
}

async function removeDomain(row: DomainRow) {
  await ElMessageBox.confirm(`删除业务域「${row.domainName}」？`, '确认')
  await api.delete(`/governance/fusion/models/domains/${row.id}`)
  if (selectedDomainId.value === row.id) selectedDomainId.value = null
  ElMessage.success('已删除')
  await loadDomains()
}

function openEntityCreate() {
  entityForm.id = null
  entityForm.entityCode = ''
  entityForm.entityName = ''
  entityForm.description = ''
  entityDlg.value = true
}

async function saveEntity() {
  if (!selectedDomainId.value) return
  if (!entityForm.entityCode.trim() || !entityForm.entityName.trim()) {
    ElMessage.warning('请填写实体编码与名称')
    return
  }
  if (entityForm.id) {
    await api.put(`/governance/fusion/models/entities/${entityForm.id}`, entityForm)
  } else {
    await api.post('/governance/fusion/models/entities', { ...entityForm, domainId: selectedDomainId.value })
  }
  entityDlg.value = false
  ElMessage.success('逻辑实体已保存')
  await loadDomainDetail()
}

async function removeEntity(row: EntityRow) {
  await ElMessageBox.confirm(`删除实体「${row.entityName}」？`, '确认')
  await api.delete(`/governance/fusion/models/entities/${row.id}`)
  ElMessage.success('已删除')
  await loadDomainDetail()
}

function openFieldCreate() {
  fieldForm.id = null
  fieldForm.fieldCode = ''
  fieldForm.fieldName = ''
  fieldForm.dataType = 'VARCHAR'
  fieldForm.pkFlag = 0
  fieldForm.sortOrder = fields.value.length
  fieldDlg.value = true
}

async function saveField() {
  if (!selectedEntityId.value) return
  if (!fieldForm.fieldCode.trim() || !fieldForm.fieldName.trim()) {
    ElMessage.warning('请填写字段编码与名称')
    return
  }
  if (fieldForm.id) {
    await api.put(`/governance/fusion/models/fields/${fieldForm.id}`, fieldForm)
  } else {
    await api.post('/governance/fusion/models/fields', { ...fieldForm, entityId: selectedEntityId.value })
  }
  fieldDlg.value = false
  ElMessage.success('字段已保存')
  await loadEntityDetail()
}

async function removeField(row: FieldRow) {
  await api.delete(`/governance/fusion/models/fields/${row.id}`)
  ElMessage.success('已删除')
  await loadEntityDetail()
}

function openRelationCreate() {
  relationForm.relationCode = ''
  relationForm.relationName = ''
  relationForm.fromEntityId = undefined
  relationForm.toEntityId = undefined
  relationForm.relationType = 'ONE_TO_MANY'
  relationDlg.value = true
}

async function saveRelation() {
  if (!selectedDomainId.value) return
  if (!relationForm.relationCode.trim() || !relationForm.fromEntityId || !relationForm.toEntityId) {
    ElMessage.warning('请完整填写关系信息')
    return
  }
  await api.post('/governance/fusion/models/relations', { ...relationForm, domainId: selectedDomainId.value })
  relationDlg.value = false
  ElMessage.success('关系已创建')
  await loadDomainDetail()
}

async function removeRelation(row: RelationRow) {
  await api.delete(`/governance/fusion/models/relations/${row.id}`)
  ElMessage.success('已删除')
  await loadDomainDetail()
}

async function ensureSources() {
  if (!sources.value.length) {
    sources.value = await loadQualitySourceOptions()
  }
}

async function reloadTables() {
  if (physicalForm.datasourceId == null) {
    tables.value = []
    return
  }
  tablesLoading.value = true
  try {
    tables.value = await loadQualityTables(physicalForm.datasourceId)
  } catch {
    tables.value = []
    ElMessage.warning('加载表清单失败')
  } finally {
    tablesLoading.value = false
  }
}

async function openPhysicalCreate() {
  physicalForm.entityId = selectedEntityId.value ?? undefined
  physicalForm.physicalCode = ''
  physicalForm.tableName = ''
  physicalForm.datasourceId = -3
  physicalForm.ddlSql = ''
  physicalDlg.value = true
  await ensureSources()
  await reloadTables()
}

watch(() => physicalForm.datasourceId, () => {
  if (physicalDlg.value) {
    physicalForm.tableName = ''
    void reloadTables()
  }
})

function onPhysicalTablePick(name: string) {
  if (!physicalForm.physicalCode.trim() && name) {
    physicalForm.physicalCode = `PHY_${name}`.slice(0, 64)
  }
}

async function savePhysical() {
  const entityId = physicalForm.entityId ?? selectedEntityId.value
  if (!entityId) {
    ElMessage.warning('请选择逻辑实体')
    return
  }
  if (!physicalForm.physicalCode.trim() || !physicalForm.tableName.trim()) {
    ElMessage.warning('请填写物理编码并选择表')
    return
  }
  if (physicalForm.datasourceId == null) {
    ElMessage.warning('请选择来源库')
    return
  }
  onPickLogicalEntity(entityId)
  await api.post('/governance/fusion/models/physical', {
    physicalCode: physicalForm.physicalCode,
    tableName: physicalForm.tableName,
    datasourceId: physicalForm.datasourceId,
    ddlSql: physicalForm.ddlSql || null,
    entityId,
  })
  physicalDlg.value = false
  ElMessage.success('物理映射已创建')
  await loadEntityDetail()
}

async function importFieldsFromPhysicalForm() {
  const entityId = physicalForm.entityId ?? selectedEntityId.value
  if (!entityId) {
    ElMessage.warning('请选择逻辑实体')
    return
  }
  if (!physicalForm.tableName || physicalForm.datasourceId == null) {
    ElMessage.warning('请先选择来源库与表')
    return
  }
  onPickLogicalEntity(entityId)
  const res = await api.post(`/governance/fusion/models/entities/${entityId}/import-fields`, {
    datasourceId: physicalForm.datasourceId,
    tableName: physicalForm.tableName,
  })
  ElMessage.success(`已导入 ${res.data.imported} 个字段，跳过 ${res.data.skipped} 个`)
  await loadEntityDetail()
}

async function removePhysical(row: PhysicalRow) {
  await api.delete(`/governance/fusion/models/physical/${row.id}`)
  ElMessage.success('已删除')
  await loadEntityDetail()
}

async function previewPhysical(row: PhysicalRow) {
  try {
    const res = await api.post(`/governance/fusion/models/physical/${row.id}/preview`)
    previewTitle.value = `查看数据 · ${row.tableName}`
    previewRows.value = res.data?.rows || []
    previewVisible.value = true
    if (!previewRows.value.length) {
      ElMessage.info(res.data?.message || '表暂无数据')
    }
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '预览失败')
  }
}

async function importFieldsFromRow(row: PhysicalRow) {
  if (!selectedEntityId.value || row.datasourceId == null) {
    ElMessage.warning('物理映射缺少来源库，请重新绑定')
    return
  }
  const res = await api.post(`/governance/fusion/models/entities/${selectedEntityId.value}/import-fields`, {
    datasourceId: row.datasourceId,
    tableName: row.tableName,
  })
  ElMessage.success(`已导入 ${res.data.imported} 个字段，跳过 ${res.data.skipped} 个`)
  await loadEntityDetail()
}

/** 真实落主题库：跳转融合任务运行（加工/直通共享黄金路径页已下线） */
async function goProcessedShare(row?: PhysicalRow) {
  const q: Record<string, string> = {
    tab: 'model',
    mSub: 'execute',
  }
  if (row?.tableName) q.hintTable = row.tableName
  await router.push({ path: '/governance', query: q })
}

function entityName(id: number) {
  return entities.value.find((e) => e.id === id)?.entityName || String(id)
}

function relationTypeLabel(t: string) {
  if (t === 'ONE_TO_ONE') return '一对一'
  if (t === 'MANY_TO_MANY') return '多对多'
  return '一对多'
}

async function exportModelReport() {
  if (!selectedDomainId.value || !selectedDomain.value) {
    ElMessage.warning('请先选择业务域')
    return
  }
  exportingReport.value = true
  try {
    const res = await api.get(`/governance/fusion/models/domains/${selectedDomainId.value}/report`, {
      responseType: 'blob',
    })
    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const code = (selectedDomain.value.domainCode || 'domain').replace(/[^\w\-]/g, '_')
    a.download = `模型报告_${code}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('模型报告已导出')
  } catch {
    ElMessage.error('导出模型报告失败')
  } finally {
    exportingReport.value = false
  }
}

watch(selectedDomainId, () => { void loadDomainDetail() })
watch(selectedEntityId, () => { void loadEntityDetail() })

onMounted(async () => {
  await loadDomains()
  await loadDomainDetail()
})
</script>

<template>
  <PageCard title="数据仓库建设">
    <div class="warehouse-layout">
      <!-- 左侧：业务域 -->
      <aside class="domain-pane" v-loading="loading">
        <div class="pane-toolbar">
          <span class="pane-title">业务域</span>
          <el-button type="primary" size="small" @click="openDomainCreate">新增</el-button>
        </div>
        <div v-if="!pagedDomains.length" class="pane-empty">
          <el-empty description="暂无业务域" :image-size="56" />
        </div>
        <ul v-else class="domain-list">
          <li
            v-for="row in pagedDomains"
            :key="row.id"
            class="domain-item"
            :class="{ active: row.id === selectedDomainId }"
            @click="selectDomain(row)"
          >
            <div class="domain-item-main">
              <div class="domain-name">{{ row.domainName }}</div>
              <div class="domain-code">{{ row.domainCode }}</div>
            </div>
            <div class="domain-item-meta">
              <el-tag size="small" type="info" effect="plain">{{ row.entityCount ?? 0 }} 实体</el-tag>
              <el-button link type="danger" @click.stop="removeDomain(row)">删除</el-button>
            </div>
          </li>
        </ul>
        <PortalPagination
          v-if="domainTotal > 10"
          v-model:page="domainPage"
          v-model:page-size="domainPageSize"
          :total="domainTotal"
          class="pane-pager"
        />
      </aside>

      <!-- 右侧：当前域下的模型详情 -->
      <section class="detail-pane">
        <div v-if="!selectedDomain" class="pane-empty pane-empty--center">
          <el-empty description="请选择或新建业务域" :image-size="72" />
        </div>

        <template v-else>
          <div class="detail-header">
            <div class="detail-header-row">
              <div class="crumb">
                <span class="crumb-muted">建模路径</span>
                <span class="crumb-sep">/</span>
                <span class="crumb-strong">{{ selectedDomain.domainName }}</span>
                <template v-if="selectedEntity">
                  <span class="crumb-sep">/</span>
                  <span class="crumb-strong">{{ selectedEntity.entityName }}</span>
                </template>
              </div>
              <el-button type="primary" plain :loading="exportingReport" @click="exportModelReport">
                导出模型报告
              </el-button>
            </div>
          </div>

          <div v-loading="detailLoading" class="detail-body">
            <el-tabs :model-value="detailTab" class="detail-tabs" @tab-change="onDetailTab">
              <el-tab-pane name="entities">
                <template #label>
                  <span>逻辑实体<span v-if="entities.length" class="tab-count">{{ entities.length }}</span></span>
                </template>
                <div class="tab-panel">
                  <el-form inline class="portal-inline-form portal-inline-form--block">
                    <el-form-item label="名称/编码" class="portal-field-lg">
                      <el-input v-model="entityQuery.keyword" clearable placeholder="实体名称或编码" />
                    </el-form-item>
                    <el-form-item label="状态" class="portal-field-md">
                      <el-select v-model="entityQuery.status" clearable placeholder="全部">
                        <el-option label="启用" value="ACTIVE" />
                        <el-option label="草稿" value="DRAFT" />
                      </el-select>
                    </el-form-item>
                    <el-form-item class="portal-form-actions">
                      <el-button type="primary" @click="resetEntityPage">查询</el-button>
                      <el-button type="primary" plain @click="openEntityCreate">新增逻辑实体</el-button>
                    </el-form-item>
                  </el-form>
                  <el-table
                    ref="entityTableRef"
                    :data="pagedEntities"
                    stripe
                    size="small"
                    highlight-current-row
                    row-key="id"
                    empty-text="当前业务域下暂无逻辑实体"
                    @current-change="selectEntity"
                    @row-click="selectEntity"
                  >
                    <el-table-column prop="entityCode" label="编码" min-width="140" show-overflow-tooltip />
                    <el-table-column prop="entityName" label="名称" min-width="140" show-overflow-tooltip />
                    <el-table-column label="状态" width="90">
                      <template #default="{ row }">
                        <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" width="88" fixed="right">
                      <template #default="{ row }">
                        <el-button link type="danger" @click.stop="removeEntity(row)">删除</el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                  <PortalPagination
                    v-model:page="entityPage"
                    v-model:page-size="entityPageSize"
                    :total="entityTotal"
                  />
                </div>
              </el-tab-pane>

              <el-tab-pane name="relations">
                <template #label>
                  <span>实体关系<span v-if="relations.length" class="tab-count">{{ relations.length }}</span></span>
                </template>
                <div class="tab-panel">
                  <el-form inline class="portal-inline-form portal-inline-form--block">
                    <el-form-item label="关键词" class="portal-field-lg">
                      <el-input v-model="relationQuery.keyword" clearable placeholder="关系名称 / 实体" />
                    </el-form-item>
                    <el-form-item label="类型" class="portal-field-md">
                      <el-select v-model="relationQuery.relationType" clearable placeholder="全部">
                        <el-option label="一对一" value="ONE_TO_ONE" />
                        <el-option label="一对多" value="ONE_TO_MANY" />
                        <el-option label="多对多" value="MANY_TO_MANY" />
                      </el-select>
                    </el-form-item>
                    <el-form-item class="portal-form-actions">
                      <el-button type="primary" :disabled="entities.length < 2" @click="openRelationCreate">新增关系</el-button>
                    </el-form-item>
                  </el-form>
                  <el-alert
                    v-if="entities.length < 2"
                    type="info"
                    :closable="false"
                    show-icon
                    title="至少两个逻辑实体后才能建立关系"
                    style="margin-bottom: 12px"
                  />
                  <el-table :data="filteredRelations" stripe size="small" empty-text="暂无实体关系">
                    <el-table-column prop="relationCode" label="编码" width="120" show-overflow-tooltip />
                    <el-table-column prop="relationName" label="关系名称" min-width="120" show-overflow-tooltip />
                    <el-table-column label="从 → 到" min-width="200">
                      <template #default="{ row }">
                        {{ entityName(row.fromEntityId) }} → {{ entityName(row.toEntityId) }}
                      </template>
                    </el-table-column>
                    <el-table-column label="类型" width="90">
                      <template #default="{ row }">{{ relationTypeLabel(row.relationType) }}</template>
                    </el-table-column>
                    <el-table-column label="操作" width="88" fixed="right">
                      <template #default="{ row }">
                        <el-button link type="danger" @click="removeRelation(row)">删除</el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </el-tab-pane>

              <el-tab-pane name="physical">
                <template #label>
                  <span>物理映射<span v-if="physicals.length" class="tab-count">{{ physicals.length }}</span></span>
                </template>
                <div v-if="!entities.length" class="pane-empty pane-empty--center">
                  <el-empty description="请先新增逻辑实体" :image-size="64">
                    <el-button type="primary" @click="detailTab = 'entities'">去新增实体</el-button>
                  </el-empty>
                </div>
                <div v-else class="tab-panel">
                  <el-form inline class="portal-inline-form portal-inline-form--block">
                    <el-form-item label="逻辑实体" class="portal-field-xl">
                      <el-select
                        :model-value="selectedEntityId"
                        filterable
                        placeholder="选择逻辑实体"
                        style="width: 100%"
                        @change="onPickLogicalEntity"
                      >
                        <el-option
                          v-for="e in entities"
                          :key="e.id"
                          :label="`${e.entityName}（${e.entityCode}）`"
                          :value="e.id"
                        />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="表名/编码" class="portal-field-lg">
                      <el-input v-model="physicalQuery.keyword" clearable placeholder="物理表或编码" />
                    </el-form-item>
                    <el-form-item label="状态" class="portal-field-md">
                      <el-select v-model="physicalQuery.status" clearable placeholder="全部">
                        <el-option label="启用" value="ACTIVE" />
                        <el-option label="草稿" value="DRAFT" />
                      </el-select>
                    </el-form-item>
                    <el-form-item class="portal-form-actions">
                      <el-button type="primary" @click="resetPhysicalPage">查询</el-button>
                      <el-button type="primary" plain @click="openPhysicalCreate">绑定物理表</el-button>
                      <el-button @click="goProcessedShare()">加工落库</el-button>
                    </el-form-item>
                  </el-form>
                  <el-table :data="pagedPhysicals" stripe size="small" empty-text="尚未绑定物理表">
                    <el-table-column prop="physicalCode" label="编码" min-width="120" show-overflow-tooltip />
                    <el-table-column prop="tableName" label="表名" min-width="140" show-overflow-tooltip />
                    <el-table-column label="状态" width="90">
                      <template #default="{ row }">
                        <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" width="220" fixed="right">
                      <template #default="{ row }">
                        <el-button link type="primary" @click="previewPhysical(row)">查看数据</el-button>
                        <el-button link @click="importFieldsFromRow(row)">导入字段</el-button>
                        <el-dropdown trigger="click">
                          <el-button link type="primary">更多</el-button>
                          <template #dropdown>
                            <el-dropdown-menu>
                              <el-dropdown-item @click="goProcessedShare(row)">加工落库</el-dropdown-item>
                              <el-dropdown-item divided @click="removePhysical(row)">
                                <span class="danger-text">删除映射</span>
                              </el-dropdown-item>
                            </el-dropdown-menu>
                          </template>
                        </el-dropdown>
                      </template>
                    </el-table-column>
                  </el-table>
                  <PortalPagination
                    v-model:page="physicalPage"
                    v-model:page-size="physicalPageSize"
                    :total="physicalTotal"
                  />
                </div>
              </el-tab-pane>

              <el-tab-pane name="fields">
                <template #label>
                  <span>字段<span v-if="fields.length" class="tab-count">{{ fields.length }}</span></span>
                </template>
                <div v-if="!entities.length" class="pane-empty pane-empty--center">
                  <el-empty description="请先新增逻辑实体" :image-size="64">
                    <el-button type="primary" @click="detailTab = 'entities'">去新增实体</el-button>
                  </el-empty>
                </div>
                <div v-else class="tab-panel">
                  <el-form inline class="portal-inline-form portal-inline-form--block">
                    <el-form-item label="逻辑实体" class="portal-field-xl">
                      <el-select
                        :model-value="selectedEntityId"
                        filterable
                        placeholder="选择逻辑实体"
                        style="width: 100%"
                        @change="onPickLogicalEntity"
                      >
                        <el-option
                          v-for="e in entities"
                          :key="e.id"
                          :label="`${e.entityName}（${e.entityCode}）`"
                          :value="e.id"
                        />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="名称/编码" class="portal-field-lg">
                      <el-input v-model="fieldQuery.keyword" clearable placeholder="字段名称或编码" />
                    </el-form-item>
                    <el-form-item label="类型" class="portal-field-md">
                      <el-select v-model="fieldQuery.dataType" clearable placeholder="全部" filterable allow-create>
                        <el-option label="VARCHAR" value="VARCHAR" />
                        <el-option label="BIGINT" value="BIGINT" />
                        <el-option label="DECIMAL" value="DECIMAL" />
                        <el-option label="DATETIME" value="DATETIME" />
                        <el-option label="TEXT" value="TEXT" />
                      </el-select>
                    </el-form-item>
                    <el-form-item class="portal-form-actions">
                      <el-button type="primary" plain @click="openFieldCreate">新增字段</el-button>
                    </el-form-item>
                  </el-form>
                  <el-table :data="filteredFields" stripe size="small" empty-text="暂无字段，可手工新增或从物理表导入">
                    <el-table-column prop="fieldCode" label="编码" min-width="120" show-overflow-tooltip />
                    <el-table-column prop="fieldName" label="名称" min-width="120" show-overflow-tooltip />
                    <el-table-column prop="dataType" label="类型" width="100" />
                    <el-table-column label="主键" width="70">
                      <template #default="{ row }">{{ row.pkFlag ? '是' : '—' }}</template>
                    </el-table-column>
                    <el-table-column label="操作" width="88" fixed="right">
                      <template #default="{ row }">
                        <el-button link type="danger" @click="removeField(row)">删除</el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </el-tab-pane>
            </el-tabs>
          </div>
        </template>
      </section>
    </div>

    <el-dialog v-model="domainDlg" title="业务域" width="420px">
      <el-form label-width="80px">
        <el-form-item label="编码"><el-input v-model="domainForm.domainCode" :disabled="!!domainForm.id" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="domainForm.domainName" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="domainForm.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="domainDlg = false">取消</el-button>
        <el-button type="primary" @click="saveDomain">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="entityDlg" title="逻辑实体" width="420px">
      <el-form label-width="80px">
        <el-form-item label="编码"><el-input v-model="entityForm.entityCode" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="entityForm.entityName" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="entityForm.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="entityDlg = false">取消</el-button>
        <el-button type="primary" @click="saveEntity">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="fieldDlg" title="字段" width="420px">
      <el-form label-width="80px">
        <el-form-item label="编码"><el-input v-model="fieldForm.fieldCode" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="fieldForm.fieldName" /></el-form-item>
        <el-form-item label="类型"><el-input v-model="fieldForm.dataType" /></el-form-item>
        <el-form-item label="主键"><el-switch v-model="fieldForm.pkFlag" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fieldDlg = false">取消</el-button>
        <el-button type="primary" @click="saveField">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="relationDlg" title="实体关系" width="460px">
      <el-form label-width="80px">
        <el-form-item label="编码"><el-input v-model="relationForm.relationCode" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="relationForm.relationName" /></el-form-item>
        <el-form-item label="源实体">
          <el-select v-model="relationForm.fromEntityId" style="width:100%">
            <el-option v-for="e in entities" :key="e.id" :label="e.entityName" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标实体">
          <el-select v-model="relationForm.toEntityId" style="width:100%">
            <el-option v-for="e in entities" :key="e.id" :label="e.entityName" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="relationForm.relationType" style="width:100%">
            <el-option label="一对一" value="ONE_TO_ONE" />
            <el-option label="一对多" value="ONE_TO_MANY" />
            <el-option label="多对多" value="MANY_TO_MANY" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="relationDlg = false">取消</el-button>
        <el-button type="primary" @click="saveRelation">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="physicalDlg" title="物理映射" width="520px">
      <el-form label-width="88px">
        <el-form-item label="逻辑实体" required>
          <el-select v-model="physicalForm.entityId" filterable style="width: 100%" placeholder="选择已新增的逻辑实体">
            <el-option
              v-for="e in entities"
              :key="e.id"
              :label="`${e.entityName}（${e.entityCode}）`"
              :value="e.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="来源库" required>
          <el-select v-model="physicalForm.datasourceId" filterable style="width: 100%" placeholder="主题库优先选 DWS/ADS">
            <el-option-group v-for="g in sourceGroups" :key="g.role" :label="g.label">
              <el-option v-for="s in g.options" :key="s.id" :label="s.label" :value="s.id" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="物理表" required>
          <el-select
            v-model="physicalForm.tableName"
            filterable
            allow-create
            default-first-option
            :loading="tablesLoading"
            placeholder="输入表名筛选，或选择/新建"
            style="width: 100%"
            @change="onPhysicalTablePick"
          >
            <el-option v-for="t in tables" :key="t.sourceTable" :label="t.sourceTable" :value="t.sourceTable" />
          </el-select>
        </el-form-item>
        <el-form-item label="编码"><el-input v-model="physicalForm.physicalCode" /></el-form-item>
        <el-form-item label="DDL 备注">
          <el-input v-model="physicalForm.ddlSql" type="textarea" :rows="3" placeholder="可选，仅作台账备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="physicalDlg = false">取消</el-button>
        <el-button @click="importFieldsFromPhysicalForm">导入字段</el-button>
        <el-button type="primary" @click="savePhysical">保存映射</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="previewVisible" :title="previewTitle" size="560px">
      <el-table v-if="previewRows.length" :data="previewRows" stripe size="small" max-height="480" />
      <el-empty v-else description="无数据行" />
    </el-drawer>
  </PageCard>
</template>

<style scoped>
.warehouse-layout {
  display: flex;
  gap: 16px;
  min-height: 520px;
  align-items: stretch;
}

.domain-pane {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
  overflow: hidden;
}

.pane-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.pane-title {
  font-weight: 600;
  font-size: 14px;
}

.domain-list {
  list-style: none;
  margin: 0;
  padding: 8px;
  overflow: auto;
  flex: 1;
}

.domain-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid transparent;
  margin-bottom: 6px;
  transition: background 0.15s, border-color 0.15s;
}

.domain-item:hover {
  background: var(--el-fill-color-light);
}

.domain-item.active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
}

.domain-name {
  font-weight: 600;
  font-size: 13px;
  line-height: 1.3;
}

.domain-code {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.domain-item-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pane-pager {
  padding: 4px 8px 8px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.detail-pane {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  padding: 12px 16px 16px;
}

.detail-header {
  margin-bottom: 4px;
}

.detail-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.crumb {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  font-size: 14px;
}

.crumb-muted {
  color: var(--el-text-color-secondary);
}

.crumb-sep {
  color: var(--el-text-color-placeholder);
  margin: 0 2px;
}

.crumb-strong {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.detail-tabs {
  margin-top: 4px;
}

.detail-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.tab-count {
  display: inline-block;
  margin-left: 6px;
  min-width: 18px;
  padding: 0 6px;
  border-radius: 9px;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 18px;
  text-align: center;
  font-weight: 500;
}

.detail-body {
  flex: 1;
  min-height: 280px;
}

.tab-panel {
  min-height: 240px;
}

.pane-empty {
  padding: 24px 12px;
}

.pane-empty--center {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 320px;
}

.danger-text {
  color: var(--el-color-danger);
}

@media (max-width: 960px) {
  .warehouse-layout {
    flex-direction: column;
  }

  .domain-pane {
    width: 100%;
    max-height: 240px;
  }
}
</style>
