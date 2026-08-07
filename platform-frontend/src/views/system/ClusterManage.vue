<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { statusLabel } from '@/utils/status-label'

interface ClusterAccount {
  id: number
  clusterCode: string
  clusterName: string
  accountName: string
  accountPassword?: string
  endpoint?: string
  remark?: string
  status: number
  createdAt?: string
  updatedAt?: string
}

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const rows = ref<ClusterAccount[]>([])
const { page, pageSize, paged, total, resetPage } = useClientPager(rows)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)

const form = reactive({
  clusterCode: '',
  clusterName: '',
  accountName: '',
  accountPassword: '',
  endpoint: '',
  remark: '',
  status: 1,
})

async function load() {
  loading.value = true
  try {
    const res = await api.get('/system/cluster-accounts', {
      params: { keyword: keyword.value.trim() || undefined },
    })
    rows.value = res.data || []
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function onReset() {
  keyword.value = ''
  resetPage()
  void load()
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    clusterCode: '',
    clusterName: '',
    accountName: '',
    accountPassword: '',
    endpoint: '',
    remark: '',
    status: 1,
  })
  dialogVisible.value = true
}

function openEdit(row: ClusterAccount) {
  editingId.value = row.id
  Object.assign(form, {
    clusterCode: row.clusterCode,
    clusterName: row.clusterName,
    accountName: row.accountName,
    accountPassword: '',
    endpoint: row.endpoint || '',
    remark: row.remark || '',
    status: row.status === 0 ? 0 : 1,
  })
  dialogVisible.value = true
}

async function submit() {
  if (!form.clusterCode.trim() || !form.clusterName.trim() || !form.accountName.trim()) {
    ElMessage.warning('请填写编码、名称与账号')
    return
  }
  saving.value = true
  try {
    const body = {
      clusterCode: form.clusterCode.trim(),
      clusterName: form.clusterName.trim(),
      accountName: form.accountName.trim(),
      accountPassword: form.accountPassword,
      endpoint: form.endpoint.trim(),
      remark: form.remark.trim(),
      status: form.status,
    }
    if (editingId.value == null) {
      await api.post('/system/cluster-accounts', body)
      ElMessage.success('新增成功')
    } else {
      await api.put(`/system/cluster-accounts/${editingId.value}`, body)
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    await load()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeRow(row: ClusterAccount) {
  try {
    await ElMessageBox.confirm(`确定删除集群「${row.clusterName}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await api.delete(`/system/cluster-accounts/${row.id}`)
    ElMessage.success('已删除')
    await load()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="集群管理" description="维护集群账号台账（编码、名称、账号、地址等），供运维登记与查询。">
      <el-button type="primary" @click="openCreate">新增集群</el-button>
    </PageHeader>
    <PageCard>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="关键词" class="portal-field-xl">
          <el-input v-model="keyword" clearable placeholder="编码/名称/账号/地址" @keyup.enter="load" />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table class="portal-table" :data="paged" v-loading="loading" stripe border>
        <el-table-column prop="clusterCode" label="集群编码" min-width="120" show-overflow-tooltip />
        <el-table-column prop="clusterName" label="集群名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="accountName" label="账号" min-width="120" show-overflow-tooltip />
        <el-table-column prop="endpoint" label="访问地址" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.endpoint || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ statusLabel(row.status === 1 ? 'ACTIVE' : 'DISABLED') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="removeRow(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <PortalPagination
        v-if="rows.length"
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="total"
      />
    </PageCard>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId == null ? '新增集群' : '编辑集群'"
      width="520px"
      destroy-on-close
    >
      <el-form label-width="100px">
        <el-form-item label="集群编码" required>
          <el-input v-model="form.clusterCode" :disabled="editingId != null" placeholder="唯一编码，如 K8S-DEV" />
        </el-form-item>
        <el-form-item label="集群名称" required>
          <el-input v-model="form.clusterName" placeholder="显示名称" />
        </el-form-item>
        <el-form-item label="账号" required>
          <el-input v-model="form.accountName" placeholder="登录账号" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.accountPassword"
            type="password"
            show-password
            :placeholder="editingId == null ? '可选' : '留空则不修改'"
          />
        </el-form-item>
        <el-form-item label="访问地址">
          <el-input v-model="form.endpoint" placeholder="如 https://cluster.example.com" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
