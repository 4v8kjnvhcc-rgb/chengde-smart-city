<script setup lang="ts">
import { onMounted, reactive, ref, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { statusLabel, statusTagType } from '@/utils/status-label'

const router = useRouter()
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
const {
  page: entityPage,
  pageSize: entityPageSize,
  paged: pagedEntities,
  total: entityTotal,
  resetPage: resetEntityPage,
} = useClientPager(entities)
const fields = ref<FieldRow[]>([])
const relations = ref<RelationRow[]>([])
const physicals = ref<PhysicalRow[]>([])
const {
  page: physicalPage,
  pageSize: physicalPageSize,
  paged: pagedPhysicals,
  total: physicalTotal,
  resetPage: resetPhysicalPage,
} = useClientPager(physicals)
const selectedEntityId = ref<number | null>(null)
const loading = ref(false)
const sources = ref<QualitySourceOption[]>([])
const sourceGroups = computed(() => groupSourcesByRole(sources.value))
const tables = ref<QualityTableMeta[]>([])
const tablesLoading = ref(false)
const previewVisible = ref(false)
const previewRows = ref<Record<string, unknown>[]>([])
const previewTitle = ref('')

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
  physicalCode: '',
  tableName: '',
  datasourceId: -3 as number | undefined,
  ddlSql: '',
})

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
    return
  }
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
  } catch {
    ElMessage.error('加载模型树失败')
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
  if (!selectedEntityId.value) return
  if (!physicalForm.physicalCode.trim() || !physicalForm.tableName.trim()) {
    ElMessage.warning('请填写物理编码并选择表')
    return
  }
  if (physicalForm.datasourceId == null) {
    ElMessage.warning('请选择来源库')
    return
  }
  await api.post('/governance/fusion/models/physical', {
    physicalCode: physicalForm.physicalCode,
    tableName: physicalForm.tableName,
    datasourceId: physicalForm.datasourceId,
    ddlSql: physicalForm.ddlSql || null,
    entityId: selectedEntityId.value,
  })
  physicalDlg.value = false
  ElMessage.success('物理映射已创建')
  await loadEntityDetail()
}

async function importFieldsFromPhysicalForm() {
  if (!selectedEntityId.value) return
  if (!physicalForm.tableName || physicalForm.datasourceId == null) {
    ElMessage.warning('请先选择来源库与表')
    return
  }
  const res = await api.post(`/governance/fusion/models/entities/${selectedEntityId.value}/import-fields`, {
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

/** 真实落主题库：委托加工共享黄金路径（非本页第三引擎） */
async function goProcessedShare(row?: PhysicalRow) {
  const q: Record<string, string> = {
    tab: 'model',
    mSub: 'execute',
    execTab: 'processed-share',
  }
  if (row?.tableName) q.hintTable = row.tableName
  await router.push({ path: '/governance', query: q })
}

function entityName(id: number) {
  return entities.value.find((e) => e.id === id)?.entityName || String(id)
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
    <el-alert
      type="success"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="V3.0「数据仓库建设」：主题域 → 逻辑实体 → 物理表。目标是多表融合成基础库/主题库/专题库（DWS/ADS）。绑定后请用「加工落库」进入加工共享；治理过程表(DWD)不作为目录资源。"
    />
    <el-row :gutter="12">
      <el-col :span="8">
        <div class="panel-head">
          <span>业务域</span>
          <el-button type="primary" size="small" @click="openDomainCreate">新增</el-button>
        </div>
        <el-table
          v-loading="loading"
          :data="pagedDomains"
          stripe
          size="small"
          highlight-current-row
          @current-change="(row: DomainRow | undefined) => { selectedDomainId = row?.id ?? null }"
        >
          <el-table-column prop="domainCode" label="编码" width="100" />
          <el-table-column prop="domainName" label="名称" />
          <el-table-column prop="entityCount" label="实体" width="50" />
          <el-table-column label="操作" width="60">
            <template #default="{ row }">
              <el-button link type="danger" @click.stop="removeDomain(row)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination
          v-model:page="domainPage"
          v-model:page-size="domainPageSize"
          :total="domainTotal"
        />
      </el-col>

      <el-col :span="8">
        <div class="panel-head">
          <span>逻辑实体</span>
          <el-button type="primary" size="small" :disabled="!selectedDomainId" @click="openEntityCreate">新增</el-button>
        </div>
        <el-table
          :data="pagedEntities"
          stripe
          size="small"
          highlight-current-row
          @current-change="(row: EntityRow | undefined) => { selectedEntityId = row?.id ?? null }"
        >
          <el-table-column prop="entityCode" label="编码" width="90" />
          <el-table-column prop="entityName" label="名称" />
          <el-table-column label="状态" width="70">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="60">
            <template #default="{ row }">
              <el-button link type="danger" @click.stop="removeEntity(row)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination
          v-model:page="entityPage"
          v-model:page-size="entityPageSize"
          :total="entityTotal"
        />

        <div class="panel-head" style="margin-top:12px">
          <span>实体关系</span>
          <el-button size="small" :disabled="!selectedDomainId" @click="openRelationCreate">新增</el-button>
        </div>
        <el-table :data="relations" stripe size="small" max-height="160">
          <el-table-column prop="relationName" label="关系" />
          <el-table-column label="从→到" min-width="120">
            <template #default="{ row }">{{ entityName(row.fromEntityId) }} → {{ entityName(row.toEntityId) }}</template>
          </el-table-column>
          <el-table-column label="" width="40">
            <template #default="{ row }">
              <el-button link type="danger" @click="removeRelation(row)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-col>

      <el-col :span="8">
        <div class="panel-head">
          <span>字段</span>
          <el-button type="primary" size="small" :disabled="!selectedEntityId" @click="openFieldCreate">新增</el-button>
        </div>
        <el-table :data="fields" stripe size="small" max-height="220">
          <el-table-column prop="fieldCode" label="编码" width="90" />
          <el-table-column prop="fieldName" label="名称" />
          <el-table-column prop="dataType" label="类型" width="80" />
          <el-table-column label="" width="40">
            <template #default="{ row }">
              <el-button link type="danger" @click="removeField(row)">删</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="panel-head" style="margin-top:12px">
          <span>物理映射</span>
          <el-button size="small" :disabled="!selectedEntityId" @click="openPhysicalCreate">新增</el-button>
        </div>
        <el-table :data="pagedPhysicals" stripe size="small">
          <el-table-column prop="physicalCode" label="编码" width="80" />
          <el-table-column prop="tableName" label="表名" min-width="100" />
          <el-table-column label="状态" width="70">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button link type="primary" @click="previewPhysical(row)">数据</el-button>
              <el-button link @click="importFieldsFromRow(row)">导字段</el-button>
              <el-button link type="success" @click="goProcessedShare(row)">加工落库</el-button>
              <el-button link type="danger" @click="removePhysical(row)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination
          v-model:page="physicalPage"
          v-model:page-size="physicalPageSize"
          :total="physicalTotal"
        />
        <el-empty v-if="selectedEntityId && !physicals.length" description="尚未绑定物理表" :image-size="48" />
      </el-col>
    </el-row>

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
            :loading="tablesLoading"
            placeholder="从分层库/登记源选择"
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
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-weight: 600;
  font-size: 13px;
}
</style>
