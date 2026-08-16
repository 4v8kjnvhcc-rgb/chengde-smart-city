<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { statusLabel, statusTagType } from '@/utils/status-label'

interface AuthConfigRow {
  id: number
  configKey: string
  configValue: string
  description?: string
  status?: string
}

const loading = ref(false)
const rows = ref<AuthConfigRow[]>([])
const query = reactive({
  configKey: '',
  description: '',
})
const applied = reactive({
  configKey: '',
  description: '',
})

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit' | 'view'>('create')
const editingId = ref<number | null>(null)
const submitting = ref(false)
const actingId = ref<number | null>(null)

const form = reactive({
  configKey: '',
  description: '',
  configValue: '',
  status: 'ACTIVE',
})

const dialogTitle = computed(() => {
  if (dialogMode.value === 'create') return '新增认证配置'
  if (dialogMode.value === 'edit') return '编辑认证配置'
  return '查看认证配置'
})

const readonly = computed(() => dialogMode.value === 'view')

const filteredRows = computed(() => {
  const key = applied.configKey.trim().toLowerCase()
  const desc = applied.description.trim().toLowerCase()
  return rows.value.filter((r) => {
    if (key && !String(r.configKey || '').toLowerCase().includes(key)) return false
    if (desc && !String(r.description || '').toLowerCase().includes(desc)) return false
    return true
  })
})

async function reload() {
  loading.value = true
  try {
    rows.value = ((await api.get('/system/uum/auth-configs')).data || []) as AuthConfigRow[]
  } finally {
    loading.value = false
  }
}

function doSearch() {
  applied.configKey = query.configKey
  applied.description = query.description
}

function resetQuery() {
  query.configKey = ''
  query.description = ''
  applied.configKey = ''
  applied.description = ''
}

function resetForm() {
  Object.assign(form, {
    configKey: '',
    description: '',
    configValue: '',
    status: 'ACTIVE',
  })
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: AuthConfigRow) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  Object.assign(form, {
    configKey: row.configKey || '',
    description: row.description || '',
    configValue: row.configValue ?? '',
    status: row.status === 'DISABLED' ? 'DISABLED' : 'ACTIVE',
  })
  dialogVisible.value = true
}

function openView(row: AuthConfigRow) {
  dialogMode.value = 'view'
  editingId.value = row.id
  Object.assign(form, {
    configKey: row.configKey || '',
    description: row.description || '',
    configValue: row.configValue ?? '',
    status: row.status === 'DISABLED' ? 'DISABLED' : 'ACTIVE',
  })
  dialogVisible.value = true
}

async function submitForm() {
  if (readonly.value) {
    dialogVisible.value = false
    return
  }
  if (!form.configKey.trim()) {
    ElMessage.warning('请填写配置项')
    return
  }
  submitting.value = true
  try {
    const body = {
      configKey: form.configKey.trim(),
      description: form.description.trim(),
      configValue: form.configValue ?? '',
      status: form.status,
    }
    if (dialogMode.value === 'create') {
      await api.post('/system/uum/auth-configs', body)
      ElMessage.success('已新增')
    } else if (editingId.value != null) {
      await api.put(`/system/uum/auth-configs/${editingId.value}`, body)
      ElMessage.success('已保存')
    }
    dialogVisible.value = false
    await reload()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    submitting.value = false
  }
}

async function doDelete(row: AuthConfigRow) {
  await ElMessageBox.confirm(`确认删除配置「${row.configKey}」？`, '删除确认', { type: 'warning' })
  actingId.value = row.id
  try {
    await api.delete(`/system/uum/auth-configs/${row.id}`)
    ElMessage.success('已删除')
    await reload()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  } finally {
    actingId.value = null
  }
}

onMounted(() => {
  void reload()
})
</script>

<template>
  <div>
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="配置项" class="portal-field-lg">
        <el-input v-model="query.configKey" clearable placeholder="配置项" @keyup.enter="doSearch" />
      </el-form-item>
      <el-form-item label="说明" class="portal-field-lg">
        <el-input v-model="query.description" clearable placeholder="说明" @keyup.enter="doSearch" />
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="doSearch">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新增</el-button>
    </div>

    <el-table v-loading="loading" :data="filteredRows" stripe size="small">
      <el-table-column prop="configKey" label="配置项" min-width="180" show-overflow-tooltip />
      <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
      <el-table-column prop="configValue" label="值" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openView(row)">查看</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button
            link
            type="danger"
            :loading="actingId === row.id"
            @click="doDelete(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="520px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form label-width="88px">
        <el-form-item label="配置项" required>
          <el-input v-model="form.configKey" :disabled="readonly" placeholder="如 sso.enabled" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" :disabled="readonly" placeholder="配置说明" />
        </el-form-item>
        <el-form-item label="值">
          <el-input v-model="form.configValue" :disabled="readonly" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" :disabled="readonly" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ readonly ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!readonly" type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
</style>
