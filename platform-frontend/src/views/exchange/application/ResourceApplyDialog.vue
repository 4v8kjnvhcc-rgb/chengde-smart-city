<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

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
}

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

const submitting = ref(false)
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

/** 申请方名称：优先登录用户所属部门，其次外部传入 */
function resolveApplicantOrg() {
  const fromUser = String(auth.user?.orgName || '').trim()
  if (fromUser) return fromUser
  const fromProp = String(props.defaultApplicantOrg || '').trim()
  if (fromProp) return fromProp
  return ''
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
  if (!form.systemName.trim()) return ElMessage.warning('请填写应用系统名称')
  if (!hasText(form.timeRange)) return ElMessage.warning('请选择或填写使用时间范围')
  if (isApi.value) {
    if (form.callFreq == null) return ElMessage.warning('请填写服务接口调用频次')
    if (form.peakFreq == null) return ElMessage.warning('请填写服务接口峰值频率')
    if (form.useDays == null) return ElMessage.warning('请填写服务接口使用期限')
    if (form.useDays > 3656) return ElMessage.warning('期限不得超过3656天')
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
    width="680px"
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

          <p class="sub-cap">资源申请</p>
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
            <el-input v-model="form.systemName" placeholder="请输入应用系统名称" />
          </el-form-item>
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
          <div v-if="isApi" class="api-row">
            <el-form-item label="调用频次" required>
              <div class="with-unit">
                <el-input-number v-model="form.callFreq" :min="1" :controls="false" placeholder="次数" />
                <span class="unit">次/天</span>
              </div>
            </el-form-item>
            <el-form-item label="峰值频率" required>
              <div class="with-unit">
                <el-input-number v-model="form.peakFreq" :min="1" :controls="false" placeholder="次数" />
                <span class="unit">次/天</span>
              </div>
            </el-form-item>
            <el-form-item label="使用期限" required>
              <div class="with-unit">
                <el-input-number
                  v-model="form.useDays"
                  :min="1"
                  :max="3656"
                  :controls="false"
                  placeholder="天数"
                />
                <span class="unit">天</span>
              </div>
              <p class="field-hint">期限不得超过3656天</p>
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
            <el-input v-model="form.dataDesc" placeholder="请简要描述所需数据内容" />
          </el-form-item>
          <el-form-item label="申请依据" required>
            <el-input
              v-model="form.applyBasis"
              type="textarea"
              :rows="3"
              placeholder="请填写完成本办事场景所依据的法律、法规或政策文件"
            />
          </el-form-item>
          <el-form-item label="其他技术需求" required>
            <el-input v-model="form.techReq" placeholder="请填写接口对接、数据格式等技术要求" />
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
  .api-row,
  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>
