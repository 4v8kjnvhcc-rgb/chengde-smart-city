<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

export interface DemandFormModel {
  id?: number
  providerOrg: string
  providerOrgId?: number
  targetCatalogId?: number
  catalogTitle: string
  dataName: string
  systemNames: string[]
  dataItems: string[]
  serviceDemandType: 'GOV' | 'NON_GOV'
  matterIds: number[]
  matterNames: string[]
  matterMaterials: string
  usageScenario: string
  demandBasis: string
  shareProvideMode: string
  updateFrequency: string
  requesterOrg: string
  contactName: string
  contactPhone: string
  contactEmail: string
  demandType: 'STRUCTURED' | 'UNSTRUCTURED'
  templateCode: string
  demandContent: string
}

interface OrgNode {
  id: number
  orgName: string
  parentId?: number
  orgCode?: string
  disabled?: boolean
  children?: OrgNode[]
}

const props = defineProps<{
  modelValue?: Partial<DemandFormModel> | null
  readonly?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [v: DemandFormModel]
  draft: [v: DemandFormModel]
  submit: [v: DemandFormModel]
  cancel: []
}>()

const auth = useAuthStore()

const SHARE_MODES = [
  { value: 'QUERY_API', label: '信息查询接口' },
  { value: 'VERIFY_API', label: '数据核验接口' },
  { value: 'TABLE_EXCHANGE', label: '批量库表交换' },
  { value: 'FILE_EXCHANGE', label: '文件下载交换' },
]
const FREQ_OPTS = ['实时', '每日', '每周', '每月', '每季度', '每半年', '每年', '其他']

const form = reactive<DemandFormModel>({
  providerOrg: '',
  providerOrgId: undefined,
  targetCatalogId: undefined,
  catalogTitle: '',
  dataName: '',
  systemNames: [''],
  dataItems: [],
  serviceDemandType: 'GOV',
  matterIds: [],
  matterNames: [],
  matterMaterials: '',
  usageScenario: '',
  demandBasis: '',
  shareProvideMode: '',
  updateFrequency: '实时',
  requesterOrg: '',
  contactName: '',
  contactPhone: '',
  contactEmail: '',
  demandType: 'STRUCTURED',
  templateCode: '',
  demandContent: '',
})

const catalogDialog = ref(false)
const matterDialog = ref(false)
const dataItemInput = ref('')
const orgFlat = ref<OrgNode[]>([])
const catalogs = ref<Record<string, unknown>[]>([])
const catalogLoading = ref(false)
const matters = ref<Record<string, unknown>[]>([])
const catalogQ = ref('')
const matterQ = ref('')
const matterTypeQ = ref('')
const selectedCatalogId = ref<number>()
const selectedMatterIds = ref<number[]>([])

const selfOrgId = computed(() => Number(auth.user?.orgId || 0) || 0)
const selfOrgName = computed(() => String(auth.user?.orgName || form.requesterOrg || '').trim())

/** 与目录编目一致的 TreeSelect 数据：value=单位名称 */
const orgTreeSelectData = computed(() => {
  type TNode = { value: string; label: string; disabled?: boolean; children?: TNode[] }
  const map = new Map<number, TNode & { id: number; parentId?: number }>()
  for (const o of orgFlat.value) {
    const isSelf =
      (selfOrgId.value > 0 && o.id === selfOrgId.value)
      || (!!selfOrgName.value && o.orgName === selfOrgName.value)
    map.set(o.id, {
      id: o.id,
      parentId: o.parentId,
      value: o.orgName,
      label: o.orgName,
      disabled: isSelf,
      children: [],
    })
  }
  const roots: TNode[] = []
  for (const n of map.values()) {
    const p = n.parentId
    if (p && map.has(p)) map.get(p)!.children!.push(n)
    else roots.push(n)
  }
  const prune = (nodes: TNode[]) => {
    for (const n of nodes) {
      if (n.children?.length) prune(n.children)
      else delete n.children
    }
  }
  prune(roots)
  return roots
})

const providerOrgModel = computed({
  get: () => form.providerOrg || undefined,
  set: (name: string | undefined) => {
    onProviderOrgChange(name)
  },
})

function onProviderOrgChange(name: string | undefined) {
  const next = String(name || '').trim()
  if (!next) {
    form.providerOrg = ''
    form.providerOrgId = undefined
    form.targetCatalogId = undefined
    form.catalogTitle = ''
    catalogs.value = []
    return
  }
  if (selfOrgName.value && next === selfOrgName.value) {
    ElMessage.warning('不能选择本单位作为数据提供单位')
    form.providerOrg = ''
    form.providerOrgId = undefined
    return
  }
  const hit = orgFlat.value.find((o) => o.orgName === next)
  if (hit && selfOrgId.value > 0 && hit.id === selfOrgId.value) {
    ElMessage.warning('不能选择本单位作为数据提供单位')
    form.providerOrg = ''
    form.providerOrgId = undefined
    return
  }
  const changed = form.providerOrg !== next
  form.providerOrg = next
  form.providerOrgId = hit?.id
  if (changed) {
    form.targetCatalogId = undefined
    form.catalogTitle = ''
    void loadProviderCatalogs()
  }
}

async function loadProviderCatalogs() {
  if (!form.providerOrg) {
    catalogs.value = []
    return
  }
  catalogLoading.value = true
  try {
    const res = await api.get('/exchange/portal/catalog', {
      params: { providerOrg: form.providerOrg },
    })
    catalogs.value = res.data || []
  } catch {
    catalogs.value = []
  } finally {
    catalogLoading.value = false
  }
}

const catalogSelectModel = computed({
  get: () => form.targetCatalogId,
  set: (id: number | undefined) => {
    if (id == null) {
      form.targetCatalogId = undefined
      form.catalogTitle = ''
      return
    }
    const hit = catalogs.value.find((c) => Number(c.id) === Number(id))
    form.targetCatalogId = Number(id)
    form.catalogTitle = String(hit?.title || '')
    if (hit && !form.dataName) form.dataName = String(hit.title || '')
  },
})

const catalogFiltered = computed(() => {
  const q = catalogQ.value.trim()
  if (!q) return catalogs.value
  return catalogs.value.filter((c) => String(c.title || '').includes(q))
})

const matterFiltered = computed(() => {
  let list = matters.value.filter((m) => String(m.status || 'ACTIVE') === 'ACTIVE')
  if (matterQ.value.trim()) {
    const q = matterQ.value.trim()
    list = list.filter(
      (m) => String(m.matterName || '').includes(q) || String(m.matterCode || '').includes(q),
    )
  }
  if (matterTypeQ.value.trim()) {
    list = list.filter((m) => String(m.matterType || '') === matterTypeQ.value.trim())
  }
  return list
})

function syncFromProps() {
  const src = props.modelValue
  if (!src) return
  Object.assign(form, {
    ...form,
    ...src,
    systemNames: src.systemNames?.length ? [...src.systemNames] : [''],
    dataItems: src.dataItems ? [...src.dataItems] : [],
    matterIds: src.matterIds ? [...src.matterIds] : [],
    matterNames: src.matterNames ? [...src.matterNames] : [],
  })
  if (!form.requesterOrg) {
    form.requesterOrg = selfOrgName.value || '承德高新技术产业开发区管理委员会'
  }
  if (form.providerOrg) {
    void loadProviderCatalogs()
  }
}

watch(() => props.modelValue, syncFromProps, { deep: true, immediate: true })

async function loadRefs() {
  try {
    const [orgRes, matRes] = await Promise.all([
      api.get('/system/orgs'),
      api.get('/exchange/supply/matters', { params: { status: 'ACTIVE' } }),
    ])
    orgFlat.value = (orgRes.data || []).map((o: OrgNode) => ({
      id: Number(o.id),
      orgName: String(o.orgName || ''),
      parentId: Number(o.parentId || 0),
      orgCode: o.orgCode ? String(o.orgCode) : undefined,
    }))
    matters.value = matRes.data || []
    if (!form.requesterOrg) {
      form.requesterOrg = selfOrgName.value || '承德高新技术产业开发区管理委员会'
    }
  } catch {
    // ignore
  }
}

function snapshot(): DemandFormModel {
  return {
    ...form,
    systemNames: form.systemNames.map((s) => s.trim()).filter(Boolean),
    dataItems: [...form.dataItems],
    matterIds: [...form.matterIds],
    matterNames: [...form.matterNames],
  }
}

function validate(strict: boolean): boolean {
  if (!form.providerOrg) {
    ElMessage.warning('请选择数据提供单位')
    return false
  }
  if (!form.dataName.trim()) {
    ElMessage.warning('请填写数据名称')
    return false
  }
  if (strict && !form.dataItems.length) {
    ElMessage.warning('请填写数据项')
    return false
  }
  if (strict && form.serviceDemandType === 'GOV' && !form.matterIds.length) {
    ElMessage.warning('请选择事项名称')
    return false
  }
  if (strict && !form.usageScenario.trim() && !form.demandBasis.trim()) {
    ElMessage.warning('请填写使用场景或需求依据')
    return false
  }
  if (strict && !form.shareProvideMode) {
    ElMessage.warning('请选择期望共享数据提供方式')
    return false
  }
  if (strict && !form.contactName.trim()) {
    ElMessage.warning('请填写联系人')
    return false
  }
  if (strict && !form.contactPhone.trim()) {
    ElMessage.warning('请填写联系电话')
    return false
  }
  return true
}

function onDraft() {
  if (!validate(false)) return
  const v = snapshot()
  emit('update:modelValue', v)
  emit('draft', v)
}

function onSubmit() {
  if (!validate(true)) return
  const v = snapshot()
  emit('update:modelValue', v)
  emit('submit', v)
}

function addSystemName() {
  form.systemNames.push('')
}

function addDataItem() {
  const name = dataItemInput.value.trim()
  if (!name) return
  if (!form.dataItems.includes(name)) form.dataItems.push(name)
  dataItemInput.value = ''
}

function removeDataItem(name: string) {
  form.dataItems = form.dataItems.filter((x) => x !== name)
}

async function openCatalogPick() {
  if (!form.providerOrg) {
    return ElMessage.warning('请先选择数据提供单位')
  }
  selectedCatalogId.value = form.targetCatalogId
  catalogQ.value = ''
  catalogDialog.value = true
  await loadProviderCatalogs()
}

function confirmCatalog() {
  const hit = catalogs.value.find((c) => Number(c.id) === selectedCatalogId.value)
  if (!hit) {
    form.targetCatalogId = undefined
    form.catalogTitle = ''
    ElMessage.warning('请选择目录')
    return
  }
  form.targetCatalogId = Number(hit.id)
  form.catalogTitle = String(hit.title || '')
  if (!form.dataName) form.dataName = String(hit.title || '')
  catalogDialog.value = false
}

function pickCatalogRow(row: Record<string, unknown>) {
  selectedCatalogId.value = Number(row.id)
}

/** 新开部门数据共享门户目录详情页 */
function openCatalogDetailPage(row?: Record<string, unknown> | null) {
  const id = row ? Number(row.id) : Number(form.targetCatalogId || 0)
  if (!id) {
    ElMessage.warning('请先选择目录')
    return
  }
  const href = `${window.location.origin}/exchange/analysis-portal/dept?section=catalog&catalogId=${id}`
  window.open(href, '_blank', 'noopener,noreferrer')
}

function openMatterPick() {
  selectedMatterIds.value = [...form.matterIds]
  matterDialog.value = true
}

function confirmMatters() {
  const picked = matters.value.filter((m) => selectedMatterIds.value.includes(Number(m.id)))
  form.matterIds = picked.map((m) => Number(m.id))
  form.matterNames = picked.map((m) => String(m.matterName || ''))
  matterDialog.value = false
}

onMounted(loadRefs)

defineExpose({ snapshot, validate, form })
</script>

<template>
  <div class="demand-apply" :class="{ 'is-readonly': readonly }">
    <h3 class="sec-title">填写需求信息</h3>
    <el-form label-width="200px" label-position="right" class="apply-form">
      <el-form-item label="数据提供单位" required>
        <el-tree-select
          v-model="providerOrgModel"
          :data="orgTreeSelectData"
          filterable
          clearable
          check-strictly
          :render-after-expand="false"
          :disabled="readonly"
          placeholder="选择具体部门/提供方"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="选择目录" required>
        <div class="pick-row">
          <el-select
            v-model="catalogSelectModel"
            filterable
            clearable
            :disabled="readonly || !form.providerOrg"
            :loading="catalogLoading"
            :placeholder="form.providerOrg ? '请选择该单位在门户已发布的目录' : '请先选择数据提供单位'"
            no-data-text="该单位暂无门户已发布目录"
            style="flex:1; min-width:0"
          >
            <el-option
              v-for="c in catalogs"
              :key="String(c.id)"
              :label="String(c.title || c.id)"
              :value="Number(c.id)"
            />
          </el-select>
          <el-button
            v-if="!readonly"
            type="primary"
            plain
            :disabled="!form.targetCatalogId"
            @click="openCatalogDetailPage()"
          >详情</el-button>
          <el-button v-if="!readonly" type="primary" :disabled="!form.providerOrg" @click="openCatalogPick">选择</el-button>
        </div>
      </el-form-item>
      <el-form-item label="数据名称" required>
        <el-input v-model="form.dataName" :disabled="readonly" placeholder="请填写数据名称" />
      </el-form-item>
      <el-form-item label="信息系统名称">
        <div class="multi-col">
          <div v-for="(_, idx) in form.systemNames" :key="'sys-' + idx" class="pick-row">
            <el-input v-model="form.systemNames[idx]" :disabled="readonly" placeholder="请输入信息系统名称" />
          </div>
          <el-button v-if="!readonly" link type="primary" @click="addSystemName">+ 继续添加</el-button>
        </div>
      </el-form-item>
      <el-form-item label="数据项" required>
        <div class="data-items">
          <el-tag
            v-for="it in form.dataItems"
            :key="it"
            :closable="!readonly"
            class="data-tag"
            @close="removeDataItem(it)"
          >{{ it }}</el-tag>
          <template v-if="!readonly">
            <el-input
              v-model="dataItemInput"
              style="width:160px"
              placeholder="数据项名称"
              @keyup.enter="addDataItem"
            />
            <el-button type="primary" plain :icon="Plus" @click="addDataItem">新增数据项</el-button>
          </template>
        </div>
      </el-form-item>
      <el-form-item label="需求类型" required>
        <el-radio-group v-model="form.serviceDemandType" :disabled="readonly">
          <el-radio-button value="GOV">政务服务需求</el-radio-button>
          <el-radio-button value="NON_GOV">非政务服务需求</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <template v-if="form.serviceDemandType === 'GOV'">
        <el-form-item label="产生该需求的事项名称" required>
          <div class="pick-row">
            <el-input :model-value="form.matterNames.join('、') || ''" readonly placeholder="请选择事项（可多选）" />
            <el-button v-if="!readonly" type="primary" @click="openMatterPick">选择</el-button>
          </div>
        </el-form-item>
        <el-form-item label="事项材料名称">
          <el-input v-model="form.matterMaterials" :disabled="readonly" placeholder="请输入或选择事项材料名称" />
        </el-form-item>
      </template>
      <el-form-item v-else label="需求依据" required>
        <el-input v-model="form.demandBasis" type="textarea" :rows="3" :disabled="readonly" />
      </el-form-item>
      <el-form-item label="使用场景" required>
        <el-input v-model="form.usageScenario" type="textarea" :rows="3" :disabled="readonly" />
      </el-form-item>
      <el-form-item label="期望共享数据提供方式" required>
        <el-select v-model="form.shareProvideMode" :disabled="readonly" clearable placeholder="请选择" style="width:100%">
          <el-option v-for="o in SHARE_MODES" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="期望共享数据更新频率" required>
        <el-select v-model="form.updateFrequency" :disabled="readonly" style="width:100%">
          <el-option v-for="f in FREQ_OPTS" :key="f" :label="f" :value="f" />
        </el-select>
      </el-form-item>
      <el-form-item label="附件">
        <el-button type="primary" :disabled="readonly">点击上传</el-button>
      </el-form-item>
    </el-form>

    <h3 class="sec-title">其他信息</h3>
    <el-form label-width="200px" class="apply-form">
      <el-form-item label="需求单位" required>
        <el-input v-model="form.requesterOrg" disabled />
      </el-form-item>
      <el-form-item label="联系人" required>
        <el-input v-model="form.contactName" :disabled="readonly" placeholder="请输入联系人信息" />
      </el-form-item>
      <el-form-item label="联系电话" required>
        <el-input v-model="form.contactPhone" :disabled="readonly" placeholder="请输入联系人电话信息" />
      </el-form-item>
      <el-form-item label="联系邮箱">
        <el-input v-model="form.contactEmail" :disabled="readonly" placeholder="请输入联系人邮箱信息" />
      </el-form-item>
    </el-form>

    <div v-if="!readonly" class="footer-actions">
      <el-button type="primary" plain @click="onDraft">暂存草稿</el-button>
      <el-button type="primary" @click="onSubmit">提交</el-button>
      <el-button @click="emit('cancel')">取消</el-button>
    </div>

    <el-dialog v-model="catalogDialog" :title="`目录 · ${form.providerOrg || ''}`" width="900px" destroy-on-close>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:10px"
        :title="`展示「${form.providerOrg}」在部门数据共享门户已发布的数据目录。点击「详情」新开页面查看。`"
      />
      <el-form inline>
        <el-form-item label="目录名称">
          <el-input v-model="catalogQ" clearable />
        </el-form-item>
        <el-button type="primary" @click="catalogQ = catalogQ">查询</el-button>
        <el-button @click="catalogQ = ''">重置</el-button>
      </el-form>
      <el-table
        v-loading="catalogLoading"
        :data="catalogFiltered"
        size="small"
        highlight-current-row
        max-height="400"
        empty-text="该单位暂无门户已发布目录"
        @row-click="pickCatalogRow"
        @current-change="(row: Record<string, unknown> | null) => { selectedCatalogId = row ? Number(row.id) : undefined }"
      >
        <el-table-column width="48">
          <template #default="{ row }">
            <el-radio
              :model-value="selectedCatalogId"
              :value="Number(row.id)"
              @change="selectedCatalogId = Number(row.id)"
            >&nbsp;</el-radio>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="目录名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="shareModes" label="共享方式" width="120" />
        <el-table-column prop="providerOrg" label="提供方" width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openCatalogDetailPage(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="catalogDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmCatalog">确定选用</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="matterDialog" title="政务服务事项" width="720px" destroy-on-close>
      <el-form inline>
        <el-form-item label="事项名称">
          <el-input v-model="matterQ" clearable />
        </el-form-item>
        <el-form-item label="事项类型">
          <el-input v-model="matterTypeQ" clearable placeholder="请选择或输入" />
        </el-form-item>
        <el-button type="primary">查询</el-button>
        <el-button @click="matterQ = ''; matterTypeQ = ''">重置</el-button>
      </el-form>
      <el-table
        :data="matterFiltered"
        size="small"
        max-height="360"
        @selection-change="(rows: Record<string, unknown>[]) => { selectedMatterIds = rows.map((r) => Number(r.id)) }"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="matterCode" label="事项编码" width="140" />
        <el-table-column prop="matterName" label="事项名称" min-width="160" />
        <el-table-column prop="matterType" label="事项类型" width="120" />
      </el-table>
      <template #footer>
        <el-button @click="matterDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmMatters">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sec-title {
  margin: 0 0 14px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e8edf5;
  font-size: 16px;
  font-weight: 700;
  color: #1f2d3d;
}
.apply-form { max-width: 100%; }
.apply-form :deep(.el-form-item__label) {
  white-space: nowrap;
  line-height: 32px;
}
.pick-row {
  display: flex;
  gap: 8px;
  width: 100%;
  align-items: center;
}
.pick-row .el-input { flex: 1; }
.multi-col { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.data-items { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.data-tag { margin: 0; }
.footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
  padding-top: 12px;
  border-top: 1px solid #eef2f7;
}
</style>
