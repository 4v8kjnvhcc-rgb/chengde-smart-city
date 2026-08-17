<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'
import { encryptTransportPayload } from '@/utils/transport-crypto'
import { isContactPhone } from '@/utils/validators'

interface UserRow {
  id: number
  username: string
  displayName: string
  orgId: number
  status: number
  roleIds?: number[]
  roleNames?: string[]
}

interface Org {
  id: number
  orgName: string
}

interface Role {
  id: number
  roleName: string
}

const auth = useAuthStore()
const loading = ref(false)
const users = ref<UserRow[]>([])
const total = ref(0)
const page = ref(1)
const dialogVisible = ref(false)
const editVisible = ref(false)
const roleDialogVisible = ref(false)
const orgs = ref<Org[]>([])
const roles = ref<Role[]>([])
const submitting = ref(false)

const orgNameById = computed(() => {
  const m = new Map<number, string>()
  for (const o of orgs.value) m.set(o.id, o.orgName)
  return m
})

const roleForm = reactive({
  id: 0,
  username: '',
  displayName: '',
  roleIds: [] as number[],
})

const form = reactive({
  username: '',
  password: 'Test@12345',
  displayName: '',
  phone: '',
  orgId: undefined as number | undefined,
  roleIds: [] as number[],
})

const editForm = reactive({
  id: 0,
  username: '',
  displayName: '',
  status: 1,
  orgId: undefined as number | undefined,
  roleIds: [] as number[],
})

async function load() {
  loading.value = true
  try {
    const res = await api.get('/system/users', { params: { page: page.value, size: 20 } })
    users.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function loadMeta() {
  const [orgRes, roleRes] = await Promise.all([
    api.get('/system/orgs'),
    api.get('/system/roles'),
  ])
  orgs.value = orgRes.data
  roles.value = roleRes.data
}

async function openCreate() {
  dialogVisible.value = true
  await loadMeta()
  form.username = ''
  form.displayName = ''
  form.phone = ''
  form.password = 'Test@12345'
  form.orgId = orgs.value[0]?.id
  form.roleIds = []
}

async function openEdit(row: UserRow) {
  await loadMeta()
  editForm.id = row.id
  editForm.username = row.username
  editForm.displayName = row.displayName
  editForm.status = row.status
  editForm.orgId = row.orgId
  try {
    const roleRes = await api.get(`/system/users/${row.id}/roles`)
    editForm.roleIds = roleRes.data
  } catch {
    editForm.roleIds = []
  }
  editVisible.value = true
}

async function submitCreate() {
  if (!form.username || !form.password || !form.displayName || !form.phone?.trim() || !form.orgId) {
    ElMessage.warning('请填写完整信息（含联系方式）')
    return
  }
  if (!isContactPhone(form.phone)) {
    ElMessage.warning('联系电话格式不对')
    return
  }
  submitting.value = true
  try {
    const passwordTransport = await encryptTransportPayload({ password: form.password })
    await api.post('/system/users', {
      username: form.username,
      passwordTransport,
      displayName: form.displayName,
      phone: form.phone.trim(),
      orgId: form.orgId,
      roleIds: form.roleIds,
    })
    ElMessage.success('用户已创建')
    dialogVisible.value = false
    load()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '创建失败')
  } finally {
    submitting.value = false
  }
}

async function submitEdit() {
  submitting.value = true
  try {
    await api.put(`/system/users/${editForm.id}`, {
      displayName: editForm.displayName,
      status: editForm.status,
      orgId: editForm.orgId,
      roleIds: editForm.roleIds,
    })
    ElMessage.success('用户已更新')
    editVisible.value = false
    load()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '更新失败')
  } finally {
    submitting.value = false
  }
}

async function disableUser(row: UserRow) {
  try {
    await ElMessageBox.confirm(`确认禁用用户「${row.username}」？禁用后无法登录。`, '禁用用户', {
      type: 'warning',
    })
    await api.delete(`/system/users/${row.id}`)
    ElMessage.success('用户已禁用')
    load()
  } catch (e: unknown) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e instanceof Error ? e.message : '禁用失败')
    }
  }
}

async function deleteUser(row: UserRow) {
  try {
    await ElMessageBox.confirm(
      `确认删除用户「${row.username}」？删除后不可恢复。`,
      '删除用户',
      { type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' },
    )
    await api.delete(`/system/users/${row.id}/hard`)
    ElMessage.success('用户已删除')
    load()
  } catch (e: unknown) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e instanceof Error ? e.message : '删除失败')
    }
  }
}

async function resetPassword(row: UserRow) {
  try {
    const { value } = await ElMessageBox.prompt(`为用户「${row.username}」设置新密码（至少 8 位，含字母和数字）`, '重置密码', {
      inputType: 'password',
      inputValue: 'Test@12345',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })
    await api.put(`/system/users/${row.id}/password`, await encryptTransportPayload({ password: value }))
    ElMessage.success('密码已重置')
  } catch (e: unknown) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e instanceof Error ? e.message : '重置失败')
    }
  }
}

async function openRoleConfig(row: UserRow) {
  await loadMeta()
  roleForm.id = row.id
  roleForm.username = row.username
  roleForm.displayName = row.displayName
  try {
    const roleRes = await api.get(`/system/users/${row.id}/roles`)
    roleForm.roleIds = Array.isArray(roleRes.data) ? [...roleRes.data] : []
  } catch {
    roleForm.roleIds = []
  }
  roleDialogVisible.value = true
}

async function submitRoleConfig() {
  submitting.value = true
  try {
    await api.put(`/system/users/${roleForm.id}`, { roleIds: roleForm.roleIds })
    ElMessage.success('角色已更新')
    roleDialogVisible.value = false
    await load()
    load()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '配置角色失败')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await loadMeta()
  await load()
})
</script>

<template>
  <div>
    <PageHeader title="用户管理（全局列表）" description="建议优先在「组织与账号」按单位管理用户；本页为全局检索入口。">
      <el-button @click="$router.push({ path: '/analytics/support', query: { tab: 'users.org' } })">
        打开组织管理
      </el-button>
      <el-button
        type="primary"
        @click="openCreate"
      >
        新增用户
      </el-button>
    </PageHeader>
    <PageCard>
      <el-table class="portal-table" :data="users" v-loading="loading" stripe>
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="displayName" label="姓名" min-width="120" />
        <el-table-column label="机构" min-width="140">
          <template #default="{ row }">
            {{ orgNameById.get(row.orgId) || row.orgId || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <template v-if="row.roleNames?.length">
              <el-tag
                v-for="name in row.roleNames"
                :key="name"
                size="small"
                class="role-tag"
              >
                {{ name }}
              </el-tag>
            </template>
            <span v-else class="muted">未分配</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ $statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="360" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              link
              type="primary"
              @click="openRoleConfig(row)"
            >
              配置角色
            </el-button>
            <el-button
              link
              type="primary"
              @click="resetPassword(row)"
            >
              重置密码
            </el-button>
            <el-button
              v-if="row.status === 1"
              link
              type="danger"
              @click="disableUser(row)"
            >
              禁用
            </el-button>
            <el-button
              link
              type="danger"
              @click="deleteUser(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        :current-page="page"
        @current-change="(p: number) => { page = p; load() }"
      />
    </PageCard>

    <el-dialog v-model="dialogVisible" title="新增用户" width="480px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item label="联系方式" required>
          <el-input v-model="form.phone" placeholder="手机号或座机，必填" maxlength="32" />
        </el-form-item>
        <el-form-item label="密码" required>
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="机构" required>
          <el-select v-model="form.orgId" placeholder="选择机构" style="width: 100%">
            <el-option v-for="o in orgs" :key="o.id" :label="o.orgName" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple placeholder="选择角色" style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑用户" width="480px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="editForm.username" disabled />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="editForm.displayName" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="机构">
          <el-select v-model="editForm.orgId" placeholder="选择机构" style="width: 100%">
            <el-option v-for="o in orgs" :key="o.id" :label="o.orgName" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="editForm.roleIds" multiple placeholder="选择角色" style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="roleDialogVisible"
      :title="`配置角色 — ${roleForm.displayName || roleForm.username}`"
      width="480px"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item label="用户">
          <el-input :model-value="`${roleForm.displayName || '-'}（${roleForm.username}）`" disabled />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select
            v-model="roleForm.roleIds"
            multiple
            filterable
            clearable
            placeholder="选择一个或多个角色"
            style="width: 100%"
          >
            <el-option
              v-for="r in roles"
              :key="r.id"
              :label="r.roleName"
              :value="r.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitRoleConfig">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.role-tag {
  margin: 0 6px 4px 0;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
