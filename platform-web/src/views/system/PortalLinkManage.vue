<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'
import { PLATFORM_LABELS, PLATFORM_PATHS } from '@/utils/menu'

interface PortalLink {
  id: number
  platformPath: string
  title: string
  url: string
  description?: string
  openMode: string
  ssoMode: string
  ssoParam: string
  sortOrder: number
  status: number
}

const auth = useAuthStore()
const links = ref<PortalLink[]>([])
const filterPlatform = ref('')
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)

const form = reactive({
  platformPath: '/business',
  title: '',
  url: '',
  description: '',
  openMode: 'new_tab',
  ssoMode: 'token_query',
  ssoParam: 'access_token',
  sortOrder: 0,
  status: 1,
})

const platformOptions = PLATFORM_PATHS.map((path) => ({
  value: path,
  label: PLATFORM_LABELS[path] || path,
}))

const filteredLinks = computed(() => {
  if (!filterPlatform.value) return links.value
  return links.value.filter((l) => l.platformPath === filterPlatform.value)
})

function platformLabel(path: string) {
  return PLATFORM_LABELS[path] || path
}

async function load() {
  const res = await api.get('/system/portal-links')
  links.value = res.data
}

function resetForm() {
  form.platformPath = filterPlatform.value || '/business'
  form.title = ''
  form.url = ''
  form.description = ''
  form.openMode = 'new_tab'
  form.ssoMode = 'token_query'
  form.ssoParam = 'access_token'
  form.sortOrder = 0
  form.status = 1
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: PortalLink) {
  editingId.value = row.id
  form.platformPath = row.platformPath
  form.title = row.title
  form.url = row.url
  form.description = row.description || ''
  form.openMode = row.openMode || 'new_tab'
  form.ssoMode = row.ssoMode || 'token_query'
  form.ssoParam = row.ssoParam || 'access_token'
  form.sortOrder = row.sortOrder ?? 0
  form.status = row.status ?? 1
  dialogVisible.value = true
}

async function submit() {
  if (!form.title || !form.url || !form.platformPath) {
    ElMessage.warning('请填写所属卡片、名称与链接地址')
    return
  }
  submitting.value = true
  try {
    const payload = { ...form }
    if (editingId.value == null) {
      await api.post('/system/portal-links', payload)
      ElMessage.success('外链已创建')
    } else {
      await api.put(`/system/portal-links/${editingId.value}`, payload)
      ElMessage.success('外链已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    submitting.value = false
  }
}

async function removeLink(row: PortalLink) {
  try {
    await ElMessageBox.confirm(`确认删除外链「${row.title}」？`, '删除外链', { type: 'warning' })
    await api.delete(`/system/portal-links/${row.id}`)
    ElMessage.success('外链已删除')
    await load()
  } catch (e: unknown) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e instanceof Error ? e.message : '删除失败')
    }
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      title="门户外链管理"
      description="为统一门户各平台卡片挂接外部业务系统；启用 SSO 后跳转时携带本平台 Access Token，对接统一身份认证"
    >
      <el-button
        v-if="auth.hasPermission('system:portal-link:add')"
        type="primary"
        @click="openCreate"
      >
        新增外链
      </el-button>
    </PageHeader>

    <PageCard>
      <div class="toolbar">
        <el-select
          v-model="filterPlatform"
          clearable
          placeholder="按卡片筛选"
          style="width: 240px"
        >
          <el-option
            v-for="opt in platformOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </div>

      <el-table class="portal-table" :data="filteredLinks" stripe>
        <el-table-column label="所属卡片" min-width="160">
          <template #default="{ row }">{{ platformLabel(row.platformPath) }}</template>
        </el-table-column>
        <el-table-column prop="title" label="名称" min-width="140" />
        <el-table-column prop="url" label="链接" min-width="220" show-overflow-tooltip />
        <el-table-column label="SSO" width="120">
          <template #default="{ row }">
            {{ row.ssoMode === 'token_query' ? `参数:${row.ssoParam || 'access_token'}` : '关闭' }}
          </template>
        </el-table-column>
        <el-table-column label="打开" width="90">
          <template #default="{ row }">
            {{ row.openMode === 'same_tab' ? '当前页' : '新窗口' }}
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="70" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="auth.hasPermission('system:portal-link:edit')"
              link
              type="primary"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="auth.hasPermission('system:portal-link:delete')"
              link
              type="danger"
              @click="removeLink(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId == null ? '新增外链' : '编辑外链'"
      width="520px"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item label="所属卡片" required>
          <el-select v-model="form.platformPath" style="width: 100%">
            <el-option
              v-for="opt in platformOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="显示名称" required>
          <el-input v-model="form.title" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="链接地址" required>
          <el-input v-model="form.url" placeholder="https://业务系统地址 或 /内部路径" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" maxlength="200" />
        </el-form-item>
        <el-form-item label="打开方式">
          <el-radio-group v-model="form.openMode">
            <el-radio value="new_tab">新窗口</el-radio>
            <el-radio value="same_tab">当前页</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="统一身份认证（SSO）">
          <el-radio-group v-model="form.ssoMode">
            <el-radio value="token_query">携带 Access Token</el-radio>
            <el-radio value="none">不携带</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.ssoMode === 'token_query'" label="Token 参数名">
          <el-input v-model="form.ssoParam" placeholder="access_token" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}
</style>
