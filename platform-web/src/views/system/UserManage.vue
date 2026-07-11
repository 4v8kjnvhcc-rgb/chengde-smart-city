<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

interface UserRow {
  id: number
  username: string
  displayName: string
  orgId: number
  status: number
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
const orgs = ref<Org[]>([])
const roles = ref<Role[]>([])
const submitting = ref(false)

const form = reactive({
  username: '',
  password: 'Test@12345',
  displayName: '',
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

async function openCreate() {
  dialogVisible.value = true
  const [orgRes, roleRes] = await Promise.all([
    api.get('/system/orgs'),
    api.get('/system/roles'),
  ])
  orgs.value = orgRes.data
  roles.value = roleRes.data
  form.username = ''
  form.displayName = ''
  form.password = 'Test@12345'
  form.orgId = orgs.value[0]?.id
  form.roleIds = []
}

async function submitCreate() {
  if (!form.username || !form.password || !form.displayName || !form.orgId) {
    ElMessage.warning('请填写完整信息')
    return
  }
  submitting.value = true
  try {
    await api.post('/system/users', {
      username: form.username,
      password: form.password,
      displayName: form.displayName,
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

function statusLabel(s: number) {
  return s === 1 ? '启用' : '禁用'
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="用户管理" description="管理系统用户账号、机构与角色绑定">
      <el-button
        v-if="auth.hasPermission('system:user:add')"
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
        <el-table-column prop="orgId" label="机构ID" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
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
  </div>
</template>

<style scoped>
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
