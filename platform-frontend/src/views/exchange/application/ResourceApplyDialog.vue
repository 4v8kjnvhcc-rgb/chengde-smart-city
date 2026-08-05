<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

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
  defaultApplicantOrg?: string
}>()

const emit = defineEmits<{
  'update:visible': [v: boolean]
  submit: [payload: Record<string, unknown>]
}>()

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

watch(
  () => props.visible,
  (v) => {
    if (!v || !props.resource) return
    form.applicantOrg = props.defaultApplicantOrg || '承德高新技术产业开发区管理委员会'
    form.resourceType = props.resource.resourceType || 'TABLE'
    form.contactName = ''
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

async function onSubmit() {
  if (!props.resource) return
  if (!form.applicantOrg.trim()) return ElMessage.warning('请填写申请方名称')
  if (!form.contactName.trim()) return ElMessage.warning('请填写联系人')
  if (!form.contactPhone.trim()) return ElMessage.warning('请填写联系电话')
  if (!form.contactEmail.trim()) return ElMessage.warning('请填写联系邮箱')
  if (!form.scene) return ElMessage.warning('请选择使用办事场景')
  if (!form.systemName.trim()) return ElMessage.warning('请填写应用系统名称')
  if (!form.timeRange) return ElMessage.warning('请选择使用时间范围')
  if (isApi.value) {
    if (form.callFreq == null) return ElMessage.warning('请填写服务接口调用频次')
    if (form.peakFreq == null) return ElMessage.warning('请填写服务接口峰值频率')
    if (form.useDays == null) return ElMessage.warning('请填写服务接口使用期限')
    if (form.useDays > 3096) return ElMessage.warning('期限不能超过3096天')
  }
  if (!form.useScope) return ElMessage.warning('请选择使用范围说明')
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
      scene: form.scene,
      systemName: form.systemName,
      timeRange: form.timeRange,
      callFreq: form.callFreq,
      peakFreq: form.peakFreq,
      useDays: form.useDays,
      useScope: form.useScope,
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
    width="720px"
    top="6vh"
    destroy-on-close
    class="apply-dlg"
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-if="resource" class="apply-dlg__body">
      <section class="apply-sec">
        <h4>基本信息</h4>
        <div class="info-grid">
          <div><em>数据来源</em>{{ resource.providerOrg || '—' }}</div>
          <div><em>共享属性</em><span class="ok">✓ {{ shareAttrLabel(resource.shareAttr) }}</span></div>
          <div><em>开放属性</em><span class="ok">✓ {{ openAttrLabel(resource.openAttr) }}</span></div>
          <div><em>更新时间</em>{{ resource.updatedAt || '—' }}</div>
        </div>
      </section>

      <section class="apply-sec">
        <h4>信息填写</h4>
        <el-form label-width="140px" class="apply-form">
          <p class="sub-cap">申请方信息</p>
          <el-form-item label="申请方名称" required>
            <el-input v-model="form.applicantOrg" />
          </el-form-item>
          <el-row :gutter="12">
            <el-col :span="8">
              <el-form-item label="联系人" required label-width="70px">
                <el-input v-model="form.contactName" placeholder="请输入" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="联系电话" required label-width="80px">
                <el-input v-model="form.contactPhone" placeholder="请输入" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="联系邮箱" required label-width="80px">
                <el-input v-model="form.contactEmail" placeholder="请输入" />
              </el-form-item>
            </el-col>
          </el-row>

          <p class="sub-cap">资源申请</p>
          <el-form-item label="使用办事场景" required>
            <el-select v-model="form.scene" style="width: 100%">
              <el-option label="百项堵点" value="百项堵点" />
              <el-option label="政务服务" value="政务服务" />
              <el-option label="行业监管" value="行业监管" />
              <el-option label="辅助决策" value="辅助决策" />
            </el-select>
          </el-form-item>
          <el-form-item label="应用系统名称" required>
            <el-input v-model="form.systemName" placeholder="请输入" />
          </el-form-item>
          <el-form-item label="使用时间范围" required>
            <el-select v-model="form.timeRange" style="width: 100%">
              <el-option label="全天（含非工作日）" value="全天（含非工作日）" />
              <el-option label="工作日白天" value="工作日白天" />
              <el-option label="自定义时段" value="自定义时段" />
            </el-select>
          </el-form-item>
          <template v-if="isApi">
            <el-form-item label="服务接口调用频次" required>
              <el-input-number v-model="form.callFreq" :min="1" :controls="false" />
              <span class="unit">次/天</span>
            </el-form-item>
            <el-form-item label="服务接口峰值频率" required>
              <el-input-number v-model="form.peakFreq" :min="1" :controls="false" />
              <span class="unit">次/天</span>
            </el-form-item>
            <el-form-item label="服务接口使用期限" required>
              <el-input-number v-model="form.useDays" :min="1" :max="3096" :controls="false" placeholder="期限不超过3096天" />
              <span class="unit">天</span>
            </el-form-item>
          </template>
          <el-form-item label="使用范围说明" required>
            <el-select v-model="form.useScope" style="width: 100%">
              <el-option label="行政依据" value="行政依据" />
              <el-option label="业务协同" value="业务协同" />
              <el-option label="监督检查" value="监督检查" />
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
            <el-input v-model="form.techReq" placeholder="基于规划服务接口的具体技术要求" />
          </el-form-item>
        </el-form>
      </section>
    </div>
    <template #footer>
      <el-button type="primary" :loading="submitting" @click="onSubmit">提交</el-button>
      <el-button @click="close">取消</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.apply-dlg__body { max-height: 68vh; overflow: auto; padding-right: 4px; }
.apply-sec { margin-bottom: 18px; }
.apply-sec h4 {
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 700;
  padding-left: 8px;
  border-left: 3px solid #1677ff;
}
.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 16px;
  background: #fafbfd;
  border: 1px solid #eef1f6;
  border-radius: 6px;
  padding: 12px 14px;
  font-size: 13px;
}
.info-grid em {
  display: block;
  font-style: normal;
  color: #909399;
  font-size: 12px;
  margin-bottom: 2px;
}
.ok { color: #18a058; }
.sub-cap {
  margin: 8px 0 12px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.unit { margin-left: 8px; color: #909399; font-size: 13px; }
.apply-form :deep(.el-form-item) { margin-bottom: 14px; }
</style>
