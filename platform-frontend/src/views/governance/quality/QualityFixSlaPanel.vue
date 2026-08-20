<script setup lang="ts">
/**
 * 质量规则配置 · 整改时间要求
 */
import { onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDateTime } from '@/utils/datetime'
import { statusLabel, statusTagType } from '@/utils/status-label'

interface FixSlaRow {
  id: number
  ruleCode?: string
  ruleName?: string
  severity: string
  fixDays: number
  overdueAction: string
  notifyRoles?: string
  remark?: string
  sortNo?: number
  status: string
  createdAt?: string
  updatedAt?: string
}

const rows = ref<FixSlaRow[]>([])
const loading = ref(false)
const severityFilter = ref('')
const statusFilter = ref('')
const dialog = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  ruleCode: '',
  ruleName: '',
  severity: 'IMPORTANT',
  fixDays: 7,
  overdueAction: 'ALERT',
  notifyRoles: '',
  remark: '',
  sortNo: 0,
  status: 'ACTIVE',
})

async function load() {
  loading.value = true
  try {
    const res = await api.get('/governance/quality/fix-sla', {
      params: {
        severity: severityFilter.value || undefined,
        status: statusFilter.value || undefined,
      },
    })
    rows.value = res.data || []
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  severityFilter.value = ''
  statusFilter.value = ''
  void load()
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    ruleCode: '',
    ruleName: '',
    severity: 'IMPORTANT',
    fixDays: 7,
    overdueAction: 'ALERT',
    notifyRoles: 'DEPT_ADMIN',
    remark: '',
    sortNo: 0,
    status: 'ACTIVE',
  })
  dialog.value = true
}

function openEdit(row: FixSlaRow) {
  editingId.value = row.id
  Object.assign(form, {
    ruleCode: row.ruleCode || '',
    ruleName: row.ruleName || '',
    severity: row.severity,
    fixDays: row.fixDays,
    overdueAction: row.overdueAction,
    notifyRoles: row.notifyRoles || '',
    remark: row.remark || '',
    sortNo: row.sortNo || 0,
    status: row.status,
  })
  dialog.value = true
}

async function save() {
  if (!form.ruleName.trim()) {
    ElMessage.warning('请填写要求名称')
    return
  }
  if (form.fixDays < 1) {
    ElMessage.warning('整改时限至少 1 天')
    return
  }
  const body = { ...form }
  if (editingId.value == null) {
    await api.post('/governance/quality/fix-sla', body)
    ElMessage.success('已新增')
  } else {
    await api.put(`/governance/quality/fix-sla/${editingId.value}`, body)
    ElMessage.success('已更新')
  }
  dialog.value = false
  await load()
}

async function removeRow(row: FixSlaRow) {
  await ElMessageBox.confirm(`确认删除「${row.ruleName}」？`, '删除确认', { type: 'warning' })
  await api.delete(`/governance/quality/fix-sla/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<template>
  <div>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom:12px"
      title="按问题等级配置整改时限；发现问题后须在时限内完成整改。逾期动作：告警或升级通知。"
    />
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="问题等级" class="portal-field-md">
        <el-select v-model="severityFilter" clearable placeholder="全部" @change="load">
          <el-option label="一般" value="GENERAL" />
          <el-option label="重要" value="IMPORTANT" />
          <el-option label="严重" value="CRITICAL" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" class="portal-field-sm">
        <el-select v-model="statusFilter" clearable placeholder="全部" @change="load">
          <el-option label="启用" value="ACTIVE" />
          <el-option label="停用" value="INACTIVE" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="openCreate">新增</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="rows" stripe border class="portal-table" empty-text="暂无整改时间要求">
      <el-table-column prop="ruleName" label="要求名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="ruleCode" label="关联规则编码" width="140" show-overflow-tooltip />
      <el-table-column label="问题等级" width="100">
        <template #default="{ row }">{{ statusLabel(row.severity) }}</template>
      </el-table-column>
      <el-table-column label="整改时限" width="100">
        <template #default="{ row }">{{ row.fixDays }} 天</template>
      </el-table-column>
      <el-table-column label="逾期动作" width="110">
        <template #default="{ row }">{{ statusLabel(row.overdueAction) }}</template>
      </el-table-column>
      <el-table-column prop="notifyRoles" label="通知角色" min-width="140" show-overflow-tooltip />
      <el-table-column prop="remark" label="说明" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt || row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="removeRow(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="editingId == null ? '新增整改时间要求' : '编辑整改时间要求'" width="560px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="要求名称" required>
          <el-input v-model="form.ruleName" placeholder="如 通用-重要问题" />
        </el-form-item>
        <el-form-item label="关联规则编码">
          <el-input v-model="form.ruleCode" placeholder="可空，表示通用等级要求" />
        </el-form-item>
        <el-form-item label="问题等级" required>
          <el-select v-model="form.severity" style="width:100%">
            <el-option label="一般" value="GENERAL" />
            <el-option label="重要" value="IMPORTANT" />
            <el-option label="严重" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="整改时限(天)" required>
          <el-input-number v-model="form.fixDays" :min="1" :max="3650" />
        </el-form-item>
        <el-form-item label="逾期动作">
          <el-select v-model="form.overdueAction" style="width:100%">
            <el-option label="告警通知" value="ALERT" />
            <el-option label="升级通知" value="ESCALATE" />
          </el-select>
        </el-form-item>
        <el-form-item label="通知角色">
          <el-input v-model="form.notifyRoles" placeholder="逗号分隔，如 DEPT_ADMIN" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortNo" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
