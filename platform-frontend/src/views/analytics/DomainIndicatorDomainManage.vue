<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import api from '@/api/http'

const props = defineProps<{ domain: string }>()

const isUnifiedEntry = computed(() => props.domain === 'all' || props.domain === 'gov')

export interface IndicatorDomainRow {
  id: number
  domainName: string
  domainDbName: string
  remark?: string
  ownerDomainCode?: string
}

const loading = ref(false)
const rows = ref<IndicatorDomainRow[]>([])
const query = reactive({
  domainName: '',
  domainDbName: '',
})

const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  ownerDomainCode: 'population',
  domainName: '',
  domainDbName: '',
  remark: '',
})

const dbNamePattern = /^ind_[a-z0-9]+(_[a-z0-9]+)*$/

const rules: FormRules = {
  ownerDomainCode: [{ required: true, message: '请选择所属业务支撑系统', trigger: 'change' }],
  domainName: [{ required: true, message: '请填写指标域名称', trigger: 'blur' }],
  domainDbName: [
    { required: true, message: '请填写指标域库名', trigger: 'blur' },
    {
      validator: (_r, v, cb) => {
        const s = String(v || '').trim().toLowerCase()
        if (!dbNamePattern.test(s)) {
          cb(new Error('以 ind_ 开头，支持小写字母、数字、下划线，不能以下划线结尾'))
        } else {
          cb()
        }
      },
      trigger: 'blur',
    },
  ],
}

const emit = defineEmits<{ changed: [] }>()

function ownerLabel(code?: string) {
  switch (String(code || '').toLowerCase()) {
    case 'population': return '人口'
    case 'legal': return '法人'
    case 'macro': return '宏观'
    case 'key': return '重点领域'
    default: return code || '—'
  }
}

async function load() {
  loading.value = true
  try {
    const res = await api.get(`/analytics/domain/${props.domain}/indicator-domains`, {
      params: {
        domainName: query.domainName || undefined,
        domainDbName: query.domainDbName || undefined,
      },
    })
    rows.value = res.data || []
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.domainName = ''
  query.domainDbName = ''
  load()
}

function openCreate() {
  editingId.value = null
  form.ownerDomainCode = 'population'
  form.domainName = ''
  form.domainDbName = ''
  form.remark = ''
  dialogVisible.value = true
}

function openEdit(row: IndicatorDomainRow) {
  editingId.value = row.id
  form.ownerDomainCode = row.ownerDomainCode || 'population'
  form.domainName = row.domainName
  form.domainDbName = row.domainDbName
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function submit() {
  if (!formRef.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    const body: Record<string, unknown> = {
      domainName: form.domainName.trim(),
      domainDbName: form.domainDbName.trim().toLowerCase(),
      remark: form.remark?.trim() || null,
    }
    if (isUnifiedEntry.value && editingId.value == null) {
      body.ownerDomainCode = form.ownerDomainCode
    }
    if (editingId.value == null) {
      await api.post(`/analytics/domain/${props.domain}/indicator-domains`, body)
      ElMessage.success('已新增指标域')
    } else {
      await api.put(`/analytics/domain/indicator-domains/${editingId.value}`, body)
      ElMessage.success('已修改指标域')
    }
    dialogVisible.value = false
    await load()
    emit('changed')
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeRow(row: IndicatorDomainRow) {
  await ElMessageBox.confirm(`确认删除指标域「${row.domainName}」？`, '删除确认', { type: 'warning' })
  await api.delete(`/analytics/domain/indicator-domains/${row.id}`)
  ElMessage.success('已删除')
  await load()
  emit('changed')
}

watch(() => props.domain, () => {
  query.domainName = ''
  query.domainDbName = ''
  load()
})
onMounted(load)
</script>

<template>
  <div v-loading="loading" class="ind-domain-panel">
    <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent="load">
      <el-form-item label="指标域名称" class="portal-field-lg">
        <el-input v-model="query.domainName" clearable placeholder="请输入指标域名称" />
      </el-form-item>
      <el-form-item label="指标域名库" class="portal-field-lg">
        <el-input v-model="query.domainDbName" clearable placeholder="请输入指标域库名" />
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" :icon="Search" @click="load">查询</el-button>
        <el-button :icon="RefreshRight" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-form inline class="portal-inline-form">
      <el-form-item class="portal-form-actions">
        <el-button type="primary" :icon="Plus" @click="openCreate">新增</el-button>
      </el-form-item>
    </el-form>

    <el-table class="portal-table" :data="rows" stripe size="small" empty-text="暂无数据">
      <el-table-column v-if="isUnifiedEntry" label="所属系统" width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ ownerLabel(row.ownerDomainCode) }}</template>
      </el-table-column>
      <el-table-column prop="domainName" label="指标域名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="domainDbName" label="指标域库名" min-width="260" show-overflow-tooltip />
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">修改</el-button>
          <el-button link type="primary" @click="removeRow(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="ind-domain-footer">共 {{ rows.length }} 条</div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId == null ? '新增指标域' : '修改指标域'"
      width="520px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="140px">
        <el-form-item v-if="isUnifiedEntry && editingId == null" label="所属系统" prop="ownerDomainCode">
          <el-select v-model="form.ownerDomainCode" placeholder="请选择业务支撑系统" style="width: 100%">
            <el-option label="人口大数据支撑系统" value="population" />
            <el-option label="法人大数据支撑系统" value="legal" />
            <el-option label="宏观经济及工业运行大数据支撑系统" value="macro" />
            <el-option label="重点领域示范应用支撑系统" value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="指标域名称" prop="domainName">
          <el-input v-model="form.domainName" placeholder="请填写指标域名称" />
        </el-form-item>
        <el-form-item label="指标域库名" prop="domainDbName">
          <el-input
            v-model="form.domainDbName"
            placeholder="以 ind_ 开头，支持小写字母、数字、下划线，不能以下划线结尾"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="请填写备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ind-domain-footer {
  margin-top: 10px;
  text-align: right;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
