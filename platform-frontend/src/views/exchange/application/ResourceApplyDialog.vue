<script lang="ts">
export interface ApplyColumn {
  name: string
  comment?: string
  type?: string
  length?: string | number
  displayFlag?: boolean
  searchFlag?: boolean
}

export interface ApplyResource {
  id: number | string
  title: string
  catalogCode?: string
  providerOrg?: string
  shareAttr?: string
  openAttr?: string
  updatedAt?: string
  resourceType?: string
  resourceTypeLabel?: string
  columns?: ApplyColumn[]
  /** 库表物理表名（如 cd_population），用于拼 ESB SQL */
  physicalTableName?: string
  databaseName?: string
  /** 接口类资源：目标地址 + 请求路径 + 入参 */
  apiUrl?: string
  apiPath?: string
  apiMethod?: string
  apiCode?: string
  requestParams?: Record<string, unknown>[]
  responseParams?: Record<string, unknown>[]
  apiResultJson?: string
  successExample?: unknown
}

export function applyColumnsFromDetail(src: Record<string, unknown> | null | undefined): ApplyColumn[] {
  if (!src) return []
  if (Array.isArray(src.columns)) return src.columns as ApplyColumn[]
  const tables = src.tables
  if (!Array.isArray(tables) || !tables.length) return []
  const first = tables[0] as { columns?: ApplyColumn[] }
  return Array.isArray(first?.columns) ? first.columns : []
}

export function applyTableMetaFromDetail(src: Record<string, unknown> | null | undefined): {
  physicalTableName?: string
  databaseName?: string
} {
  if (!src) return {}
  const tables = src.tables
  if (Array.isArray(tables) && tables.length) {
    const first = tables[0] as { tableName?: string; databaseName?: string }
    const name = String(first?.tableName || '').trim()
    return {
      physicalTableName: name && name !== '—' ? name : undefined,
      databaseName: first?.databaseName ? String(first.databaseName) : undefined,
    }
  }
  const name = String(src.physicalTableName || src.bindTableName || '').trim()
  return {
    physicalTableName: name && name !== '—' ? name : undefined,
    databaseName: src.databaseName ? String(src.databaseName) : undefined,
  }
}

export function applyApiMetaFromDetail(src: Record<string, unknown> | null | undefined): {
  apiUrl?: string
  apiPath?: string
  apiMethod?: string
  apiCode?: string
  requestParams?: Record<string, unknown>[]
  responseParams?: Record<string, unknown>[]
  apiResultJson?: string
  successExample?: unknown
} {
  if (!src) return {}
  const apis = src.apis
  if (!Array.isArray(apis) || !apis.length) return {}
  const api = apis[0] as Record<string, unknown>
  const requestPath = String(api.requestPath || api.apiPath || '').trim()
  const apiUrl = String(api.apiUrl || api.targetAddress || '').trim()
  const pathOnly = requestPath.startsWith('http://') || requestPath.startsWith('https://') ? '' : requestPath
  const urlOnly = requestPath.startsWith('http://') || requestPath.startsWith('https://') ? requestPath : apiUrl
  return {
    apiUrl: urlOnly || undefined,
    apiPath: pathOnly || undefined,
    apiMethod: api.httpMethod ? String(api.httpMethod) : api.apiMethod ? String(api.apiMethod) : undefined,
    apiCode: api.apiCode ? String(api.apiCode) : undefined,
    requestParams: Array.isArray(api.requestParams) ? (api.requestParams as Record<string, unknown>[]) : undefined,
    responseParams: Array.isArray(api.responseParams) ? (api.responseParams as Record<string, unknown>[]) : undefined,
    apiResultJson: api.apiResultJson ? String(api.apiResultJson) : undefined,
    successExample: api.successExample,
  }
}
</script>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{
  visible: boolean
  resource: ApplyResource | null
  shareAttrLabel: (code?: string) => string
  openAttrLabel: (code?: string) => string
  /** 可选覆盖；默认取登录用户所属部门 */
  defaultApplicantOrg?: string
}>()

const emit = defineEmits<{
  'update:visible': [v: boolean]
  submit: [payload: Record<string, unknown>]
}>()

const auth = useAuthStore()

/** 预设选项；均支持下拉选择或直接输入自定义内容 */
const SCENE_OPTIONS = ['百项堵点', '政务服务', '行业监管', '辅助决策', '其他']
const TIME_RANGE_OPTIONS = ['全天（含非工作日）', '工作日（8:00-18:00）']
const USE_SCOPE_OPTIONS = ['行政依据', '用于数据校验', '工作参考', '其他']

interface MyAppOption {
  id: number
  appName: string
  contactName?: string
  contactPhone?: string
}

interface ParamRow {
  checked: boolean
  name: string
  comment: string
  type: string
  length: string
}

const submitting = ref(false)
const myApps = ref<MyAppOption[]>([])
const myAppsLoading = ref(false)
const inputRows = ref<ParamRow[]>([])
const outputRows = ref<ParamRow[]>([])
const inputExpanded = ref(false)
const outputExpanded = ref(false)
const PARAM_PREVIEW = 5
const form = reactive({
  applicantOrg: '',
  contactName: '',
  contactPhone: '',
  contactEmail: '',
  scene: '百项堵点',
  systemName: '',
  timeRange: '全天（含非工作日）',
  callFreq: undefined as number | undefined,
  peakFreq: undefined as number | undefined,
  useDays: undefined as number | undefined,
  useScope: '行政依据',
  dataDesc: '',
  applyBasis: '',
  techReq: '',
  purpose: '',
  resourceType: 'TABLE',
})

const dialogTitle = computed(() =>
  props.resource ? `《${props.resource.title}》资源申请` : '资源申请',
)

const isApi = computed(() =>
  (props.resource?.resourceType || form.resourceType) === 'API',
)
const isTable = computed(() => {
  const t = String(props.resource?.resourceType || form.resourceType || '').toUpperCase()
  return t === 'TABLE' || t === 'DATABASE'
})
const showQuotaFields = computed(() => isApi.value || isTable.value)
const useDaysMax = computed(() => (isTable.value ? 3096 : 3656))

function toParamRow(c: ApplyColumn): ParamRow {
  return {
    checked: true,
    name: String(c.name || ''),
    comment: String(c.comment || ''),
    type: String(c.type || ''),
    length: c.length == null || String(c.length).trim() === '' ? '—' : String(c.length),
  }
}

function hydrateTableParams() {
  const cols = props.resource?.columns || []
  inputRows.value = cols.filter((c) => !!c.searchFlag).map(toParamRow)
  outputRows.value = cols.filter((c) => c.displayFlag !== false).map(toParamRow)
  inputExpanded.value = false
  outputExpanded.value = false
}

const visibleInputRows = computed(() =>
  inputExpanded.value || inputRows.value.length <= PARAM_PREVIEW
    ? inputRows.value
    : inputRows.value.slice(0, PARAM_PREVIEW),
)
const visibleOutputRows = computed(() =>
  outputExpanded.value || outputRows.value.length <= PARAM_PREVIEW
    ? outputRows.value
    : outputRows.value.slice(0, PARAM_PREVIEW),
)

const inputAllChecked = computed({
  get: () => inputRows.value.length > 0 && inputRows.value.every((r) => r.checked),
  set: (v: boolean) => {
    inputRows.value.forEach((r) => {
      r.checked = v
    })
  },
})
const inputIndeterminate = computed(
  () => inputRows.value.some((r) => r.checked) && !inputRows.value.every((r) => r.checked),
)
const outputAllChecked = computed({
  get: () => outputRows.value.length > 0 && outputRows.value.every((r) => r.checked),
  set: (v: boolean) => {
    outputRows.value.forEach((r) => {
      r.checked = v
    })
  },
})
const outputIndeterminate = computed(
  () => outputRows.value.some((r) => r.checked) && !outputRows.value.every((r) => r.checked),
)

function selectedParams(rows: ParamRow[]) {
  return rows
    .filter((r) => r.checked && r.name)
    .map((r) => ({ name: r.name, comment: r.comment, type: r.type, length: r.length }))
}

/** 申请方名称：优先登录用户所属部门，其次外部传入 */
function resolveApplicantOrg() {
  const fromUser = String(auth.user?.orgName || '').trim()
  if (fromUser) return fromUser
  const fromProp = String(props.defaultApplicantOrg || '').trim()
  if (fromProp) return fromProp
  return ''
}

async function loadMyApps() {
  myAppsLoading.value = true
  try {
    const res = await api.get('/exchange/portal/my-apps')
    myApps.value = Array.isArray(res.data) ? res.data : []
  } catch (e: unknown) {
    myApps.value = []
    ElMessage.error(e instanceof Error ? e.message : '加载我的应用失败')
  } finally {
    myAppsLoading.value = false
  }
}

function onSystemNameChange(name: string) {
  const hit = myApps.value.find((a) => a.appName === name)
  if (!hit) return
  if (hit.contactName) form.contactName = hit.contactName
  if (hit.contactPhone) form.contactPhone = hit.contactPhone
}

watch(
  () => props.visible,
  (v) => {
    if (!v || !props.resource) return
    form.applicantOrg = resolveApplicantOrg()
    form.resourceType = props.resource.resourceType || 'TABLE'
    form.contactName = String(auth.user?.displayName || '').trim()
    form.contactPhone = ''
    form.contactEmail = ''
    form.scene = '百项堵点'
    form.systemName = ''
    form.timeRange = '全天（含非工作日）'
    form.callFreq = undefined
    form.peakFreq = undefined
    form.useDays = undefined
    form.useScope = '行政依据'
    form.dataDesc = ''
    form.applyBasis = ''
    form.techReq = ''
    form.purpose = ''
    hydrateTableParams()
    void loadMyApps().then(() => {
      if (myApps.value.length === 1) {
        form.systemName = myApps.value[0].appName
        onSystemNameChange(form.systemName)
      }
    })
  },
)

function close() {
  emit('update:visible', false)
}

function hasText(v: unknown) {
  return String(v ?? '').trim().length > 0
}

async function onSubmit() {
  if (!props.resource) return
  if (!form.applicantOrg.trim()) return ElMessage.warning('请填写申请方名称')
  if (!form.contactName.trim()) return ElMessage.warning('请填写联系人')
  if (!form.contactPhone.trim()) return ElMessage.warning('请填写联系电话')
  if (!hasText(form.scene)) return ElMessage.warning('请选择或填写使用办事场景')
  if (!form.systemName.trim()) {
    return ElMessage.warning(myApps.value.length ? '请选择应用系统名称' : '请先在个人空间「我的应用」中登记应用系统')
  }
  if (!hasText(form.timeRange)) return ElMessage.warning('请选择或填写使用时间范围')
  if (isTable.value) {
    if (!outputRows.value.length) return ElMessage.warning('编目未勾选展示项，无法申请出参')
    if (!selectedParams(outputRows.value).length) return ElMessage.warning('请至少勾选出参字段')
  }
  if (showQuotaFields.value) {
    if (form.callFreq == null) return ElMessage.warning('请填写服务接口调用频次')
    if (form.peakFreq == null) return ElMessage.warning('请填写服务接口峰值频率')
    if (form.useDays == null) return ElMessage.warning('请填写服务接口使用期限')
    if (form.useDays > useDaysMax.value) return ElMessage.warning(`期限不得超过${useDaysMax.value}天`)
  }
  if (!hasText(form.useScope)) return ElMessage.warning('请选择或填写使用范围说明')
  if (!form.dataDesc.trim()) return ElMessage.warning('请填写数据描述')
  if (!form.applyBasis.trim()) return ElMessage.warning('请填写申请依据')
  if (!form.techReq.trim()) return ElMessage.warning('请填写其他技术需求')

  submitting.value = true
  try {
    emit('submit', {
      catalogId: Number(props.resource.id),
      resourceType: form.resourceType,
      applicantOrg: form.applicantOrg,
      purpose: form.scene + (form.purpose ? `：${form.purpose}` : ''),
      contactName: form.contactName,
      contactPhone: form.contactPhone,
      contactEmail: form.contactEmail,
      scene: String(form.scene).trim(),
      systemName: form.systemName,
      timeRange: String(form.timeRange).trim(),
      callFreq: form.callFreq,
      peakFreq: form.peakFreq,
      useDays: form.useDays,
      useScope: String(form.useScope).trim(),
      dataDesc: form.dataDesc,
      applyBasis: form.applyBasis,
      techReq: form.techReq,
      inputParams: isTable.value ? selectedParams(inputRows.value) : undefined,
      outputParams: isTable.value ? selectedParams(outputRows.value) : undefined,
      physicalTableName: isTable.value ? props.resource.physicalTableName : undefined,
      tableName: isTable.value ? props.resource.physicalTableName : undefined,
      databaseName: isTable.value ? props.resource.databaseName : undefined,
      apiUrl: isApi.value ? props.resource.apiUrl : undefined,
      apiPath: isApi.value ? props.resource.apiPath : undefined,
      requestPath: isApi.value ? props.resource.apiPath : undefined,
      apiMethod: isApi.value ? props.resource.apiMethod : undefined,
      apiCode: isApi.value ? props.resource.apiCode : undefined,
      requestParams: isApi.value ? props.resource.requestParams : undefined,
      responseParams: isApi.value ? props.resource.responseParams : undefined,
      apiResultJson: isApi.value ? props.resource.apiResultJson : undefined,
      successExample: isApi.value ? props.resource.successExample : undefined,
    })
  } finally {
    submitting.value = false
  }
}

defineExpose({ close })
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="dialogTitle"
    width="920px"
    top="5vh"
    destroy-on-close
    align-center
    class="apply-dlg"
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-if="resource" class="apply-dlg__body">
      <section class="apply-sec">
        <h4>基本信息</h4>
        <div class="info-grid">
          <div><em>数据来源</em><span>{{ resource.providerOrg || '—' }}</span></div>
          <div><em>共享属性</em><span class="ok">✓ {{ shareAttrLabel(resource.shareAttr) }}</span></div>
          <div><em>开放属性</em><span class="ok">✓ {{ openAttrLabel(resource.openAttr) }}</span></div>
          <div><em>更新时间</em><span>{{ resource.updatedAt || '—' }}</span></div>
        </div>
      </section>

      <section class="apply-sec">
        <h4>信息填写</h4>
        <el-form label-position="top" class="apply-form" require-asterisk-position="left">
          <p class="sub-cap">申请方信息</p>
          <el-form-item label="申请方名称" required>
            <el-input
              v-model="form.applicantOrg"
              readonly
              placeholder="自动带出登录用户所属部门"
            />
          </el-form-item>
          <div class="contact-row">
            <el-form-item label="联系人" required>
              <el-input v-model="form.contactName" placeholder="请输入" />
            </el-form-item>
            <el-form-item label="联系电话" required>
              <el-input v-model="form.contactPhone" placeholder="请输入" />
            </el-form-item>
            <el-form-item label="联系邮箱">
              <el-input v-model="form.contactEmail" placeholder="选填" />
            </el-form-item>
          </div>

          <template v-if="isTable">
            <p class="sub-cap">库表资源申请</p>
            <p class="param-cap">入参<span class="param-cap__hint">（编目「是否搜索项」）</span></p>
            <el-table :data="visibleInputRows" size="small" stripe border class="param-table" empty-text="编目未勾选搜索项">
              <el-table-column width="46" align="center">
                <template #header>
                  <el-checkbox
                    v-model="inputAllChecked"
                    :indeterminate="inputIndeterminate"
                    :disabled="!inputRows.length"
                  />
                </template>
                <template #default="{ row }"><el-checkbox v-model="row.checked" /></template>
              </el-table-column>
              <el-table-column prop="name" label="字段名称" min-width="120" show-overflow-tooltip />
              <el-table-column prop="comment" label="中文名称" min-width="120" show-overflow-tooltip />
              <el-table-column prop="type" label="字段类型" width="110" show-overflow-tooltip />
              <el-table-column prop="length" label="字段长度" width="90" />
            </el-table>
            <button
              v-if="inputRows.length > PARAM_PREVIEW"
              type="button"
              class="expand-link"
              @click="inputExpanded = !inputExpanded"
            >{{ inputExpanded ? '收起内容' : '展开内容' }}</button>

            <p class="param-cap">出参<span class="param-cap__hint">（编目「是否展示项」）</span></p>
            <el-table :data="visibleOutputRows" size="small" stripe border class="param-table" empty-text="编目未勾选展示项">
              <el-table-column width="46" align="center">
                <template #header>
                  <el-checkbox
                    v-model="outputAllChecked"
                    :indeterminate="outputIndeterminate"
                    :disabled="!outputRows.length"
                  />
                </template>
                <template #default="{ row }"><el-checkbox v-model="row.checked" /></template>
              </el-table-column>
              <el-table-column prop="name" label="字段名称" min-width="120" show-overflow-tooltip />
              <el-table-column prop="comment" label="中文名称" min-width="120" show-overflow-tooltip />
              <el-table-column prop="type" label="字段类型" width="110" show-overflow-tooltip />
              <el-table-column prop="length" label="字段长度" width="90" />
            </el-table>
            <button
              v-if="outputRows.length > PARAM_PREVIEW"
              type="button"
              class="expand-link"
              @click="outputExpanded = !outputExpanded"
            >{{ outputExpanded ? '收起内容' : '展开内容' }}</button>
          </template>

          <p class="sub-cap">信息填写</p>
          <div class="pair-row">
            <el-form-item label="使用办事场景" required>
              <el-select
                v-model="form.scene"
                filterable
                allow-create
                default-first-option
                clearable
                placeholder="请选择或输入办事场景"
              >
                <el-option v-for="o in SCENE_OPTIONS" :key="o" :label="o" :value="o" />
              </el-select>
            </el-form-item>
            <el-form-item label="应用系统名称" required>
              <el-select
                v-model="form.systemName"
                filterable
                clearable
                :loading="myAppsLoading"
                :placeholder="myApps.length ? '请选择应用系统' : '请先在个人空间登记应用'"
                style="width: 100%"
                @change="onSystemNameChange"
              >
                <el-option
                  v-for="a in myApps"
                  :key="a.id"
                  :label="a.appName"
                  :value="a.appName"
                />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item label="使用时间范围" required>
            <el-select
              v-model="form.timeRange"
              filterable
              allow-create
              default-first-option
              clearable
              placeholder="请选择或输入使用时间范围"
            >
              <el-option v-for="o in TIME_RANGE_OPTIONS" :key="o" :label="o" :value="o" />
            </el-select>
          </el-form-item>
          <div v-if="showQuotaFields" class="api-row">
            <el-form-item label="服务接口调用频次" required>
              <div class="with-unit">
                <el-input-number v-model="form.callFreq" :min="1" :controls="false" placeholder="次数" />
                <span class="unit">次/天</span>
              </div>
            </el-form-item>
            <el-form-item label="服务接口峰值频率" required>
              <div class="with-unit">
                <el-input-number v-model="form.peakFreq" :min="1" :controls="false" placeholder="次数" />
                <span class="unit">次/天</span>
              </div>
            </el-form-item>
            <el-form-item label="服务接口使用期限" required>
              <div class="with-unit">
                <el-input-number
                  v-model="form.useDays"
                  :min="1"
                  :max="useDaysMax"
                  :controls="false"
                  placeholder="天数"
                />
                <span class="unit">天</span>
              </div>
              <p class="field-hint">期限不能超过{{ useDaysMax }}天</p>
            </el-form-item>
          </div>
          <el-form-item label="使用范围说明" required>
            <el-select
              v-model="form.useScope"
              filterable
              allow-create
              default-first-option
              clearable
              placeholder="请选择或输入使用范围说明"
            >
              <el-option v-for="o in USE_SCOPE_OPTIONS" :key="o" :label="o" :value="o" />
            </el-select>
          </el-form-item>
          <el-form-item label="数据描述" required>
            <el-input v-model="form.dataDesc" placeholder="请输入" />
          </el-form-item>
          <el-form-item label="申请依据" required>
            <el-input
              v-model="form.applyBasis"
              type="textarea"
              :rows="3"
              placeholder="描述使用部门完成上述办事场景的法律、法规、政策的具体依据"
            />
          </el-form-item>
          <el-form-item label="其他技术需求" required>
            <el-input v-model="form.techReq" placeholder="基于特定服务接口的具体技术要求" />
          </el-form-item>
        </el-form>
      </section>
    </div>
    <template #footer>
      <div class="apply-dlg__footer">
        <el-button @click="close">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">提交</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.apply-dlg__body {
  max-height: 66vh;
  overflow: auto;
  padding: 0 2px 4px;
}
.apply-sec {
  margin-bottom: 20px;
}
.apply-sec:last-child {
  margin-bottom: 0;
}
.apply-sec h4 {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
  line-height: 1;
}
.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 24px;
  background: #f7f8fa;
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  padding: 14px 16px;
  font-size: 13px;
  color: #1f2329;
}
.info-grid em {
  display: block;
  font-style: normal;
  color: #86909c;
  font-size: 12px;
  margin-bottom: 4px;
}
.ok {
  color: #00a870;
}
.sub-cap {
  margin: 4px 0 10px;
  padding-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #4e5969;
  border-bottom: 1px solid #e5e6eb;
}
.apply-form :deep(.el-form-item) {
  margin-bottom: 14px;
}
.apply-form :deep(.el-form-item__label) {
  color: #4e5969;
  font-weight: 500;
  padding-bottom: 4px !important;
  line-height: 1.4;
}
.apply-form :deep(.el-select),
.apply-form :deep(.el-input),
.apply-form :deep(.el-textarea) {
  width: 100%;
}
.contact-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 0 12px;
}
.pair-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 12px;
}
.param-cap {
  margin: 12px 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: #1f2329;
}
.param-cap__hint {
  margin-left: 6px;
  font-weight: 400;
  color: #86909c;
  font-size: 12px;
}
.param-table {
  width: 100%;
}
.expand-link {
  display: inline-block;
  margin: 6px 0 4px;
  border: 0;
  background: transparent;
  color: #1677ff;
  cursor: pointer;
  font-size: 13px;
  padding: 0;
}
.api-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 0 12px;
}
.with-unit {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.with-unit :deep(.el-input-number) {
  flex: 1;
  width: auto;
}
.unit {
  flex-shrink: 0;
  color: #86909c;
  font-size: 13px;
}
.field-hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: #c9cdd4;
  line-height: 1.3;
}
.apply-dlg__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 640px) {
  .contact-row,
  .pair-row,
  .api-row,
  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>
