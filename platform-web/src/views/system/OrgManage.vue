<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

interface Org {
  id: number
  orgCode: string
  orgName: string
  parentId: number
  status: number
}

const auth = useAuthStore()
const orgs = ref<Org[]>([])
const dialogVisible = ref(false)
const editVisible = ref(false)
const submitting = ref(false)

const form = reactive({
  orgCode: '',
  orgName: '',
  parentId: 0 as number,
})

const editForm = reactive({
  id: 0,
  orgName: '',
  parentId: 0 as number,
  status: 1,
})

async function load() {
  const res = await api.get('/system/orgs')
  orgs.value = res.data
}

async function openCreate() {
  form.orgCode = ''
  form.orgName = ''
  form.parentId = orgs.value[0]?.id || 0
  dialogVisible.value = true
}

function openEdit(row: Org) {
  editForm.id = row.id
  editForm.orgName = row.orgName
  editForm.parentId = row.parentId
  editForm.status = row.status ?? 1
  editVisible.value = true
}

async function submitCreate() {
  if (!form.orgCode || !form.orgName) {
    ElMessage.warning('请填写编码与名称')
    return
  }
  submitting.value = true
  try {
    await api.post('/system/orgs', form)
    ElMessage.success('机构已创建')
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
    await api.put(`/system/orgs/${editForm.id}`, {
      orgName: editForm.orgName,
      parentId: editForm.parentId,
      status: editForm.status,
    })
    ElMessage.success('机构已更新')
    editVisible.value = false
    load()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '更新失败')
  } finally {
    submitting.value = false
  }
}

async function removeOrg(row: Org) {
  try {
    await ElMessageBox.confirm(`确认删除机构「${row.orgName}」？`, '删除机构', { type: 'warning' })
    await api.delete(`/system/orgs/${row.id}`)
    ElMessage.success('机构已删除')
    load()
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
    <PageHeader title="机构管理" description="组织机构与部门信息">
      <el-button
        v-if="auth.hasPermission('system:org:add')"
        type="primary"
        @click="openCreate"
      >
        新增机构
      </el-button>
    </PageHeader>
    <PageCard>
      <el-table class="portal-table" :data="orgs" stripe>
        <el-table-column prop="orgCode" label="编码" min-width="120" />
        <el-table-column prop="orgName" label="名称" min-width="160" />
        <el-table-column prop="parentId" label="上级ID" width="100" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="auth.hasPermission('system:org:edit')"
              link
              type="primary"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="auth.hasPermission('system:org:delete')"
              link
              type="danger"
              @click="removeOrg(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <el-dialog v-model="dialogVisible" title="新增机构" width="440px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="编码" required>
          <el-input v-model="form.orgCode" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.orgName" />
        </el-form-item>
        <el-form-item label="上级机构">
          <el-select v-model="form.parentId" style="width: 100%">
            <el-option :value="0" label="无（顶级）" />
            <el-option v-for="o in orgs" :key="o.id" :label="o.orgName" :value="o.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑机构" width="440px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="名称" required>
          <el-input v-model="editForm.orgName" />
        </el-form-item>
        <el-form-item label="上级机构">
          <el-select v-model="editForm.parentId" style="width: 100%">
            <el-option :value="0" label="无（顶级）" />
            <el-option
              v-for="o in orgs.filter((x) => x.id !== editForm.id)"
              :key="o.id"
              :label="o.orgName"
              :value="o.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
