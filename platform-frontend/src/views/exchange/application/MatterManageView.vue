<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { formatDateTime, sortByTimeDesc } from '@/utils/datetime'

const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const keyword = ref('')
const matterType = ref('')
const { page, pageSize, paged, total, resetPage } = useClientPager(rows)

const dialog = reactive({
  visible: false,
  id: 0,
  matterCode: '',
  matterName: '',
  matterType: '企业服务',
  regionScope: 'CITY',
  status: 'ACTIVE',
  sortOrder: 100,
})

async function load() {
  loading.value = true
  try {
    const res = await api.get('/exchange/supply/matters', {
      params: {
        keyword: keyword.value || undefined,
        matterType: matterType.value || undefined,
      },
    })
    rows.value = sortByTimeDesc(res.data || [])
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '加载事项失败')
  } finally {
    loading.value = false
  }
}

function onReset() {
  keyword.value = ''
  matterType.value = ''
  resetPage()
  void load()
}

function openCreate() {
  Object.assign(dialog, {
    visible: true,
    id: 0,
    matterCode: '',
    matterName: '',
    matterType: '企业服务',
    regionScope: 'CITY',
    status: 'ACTIVE',
    sortOrder: 100,
  })
}

function openEdit(row: Record<string, unknown>) {
  Object.assign(dialog, {
    visible: true,
    id: Number(row.id),
    matterCode: String(row.matterCode || ''),
    matterName: String(row.matterName || ''),
    matterType: String(row.matterType || 'OTHER'),
    regionScope: String(row.regionScope || 'CITY'),
    status: String(row.status || 'ACTIVE'),
    sortOrder: Number(row.sortOrder ?? 100),
  })
}

async function save() {
  if (!dialog.matterCode.trim() || !dialog.matterName.trim()) {
    return ElMessage.warning('请填写事项编码与名称')
  }
  const body = {
    matterCode: dialog.matterCode.trim(),
    matterName: dialog.matterName.trim(),
    matterType: dialog.matterType,
    regionScope: dialog.regionScope,
    status: dialog.status,
    sortOrder: dialog.sortOrder,
  }
  if (dialog.id) {
    await api.post(`/exchange/supply/matters/${dialog.id}`, body)
    ElMessage.success('已更新')
  } else {
    await api.post('/exchange/supply/matters', body)
    ElMessage.success('已创建')
  }
  dialog.visible = false
  await load()
}

async function remove(row: Record<string, unknown>) {
  await ElMessageBox.confirm(`确认删除事项「${row.matterName}」？`, '删除确认', { type: 'warning' })
  await api.post(`/exchange/supply/matters/${row.id}/delete`)
  ElMessage.success('已删除')
  await load()
}

function scopeLabel(s?: string) {
  return ({ NATIONAL: '国家', PROVINCE: '省级', CITY: '市级' } as Record<string, string>)[String(s || '')] || s || '—'
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="matter-manage">
    <PageCard title="事项管理">
      <div class="toolbar">
        <el-input v-model="keyword" clearable placeholder="事项编码/名称" style="width:220px" @keyup.enter="load" />
        <el-input v-model="matterType" clearable placeholder="事项类型" style="width:160px" />
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="onReset">重置</el-button>
        <el-button type="primary" @click="openCreate">新建事项</el-button>
      </div>
      <el-table :data="paged" stripe size="small" empty-text="暂无事项数据（若刚部署请重启后端以执行内置事项初始化）">
        <el-table-column label="事项编码" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.matterCode || row.matter_code || '—' }}</template>
        </el-table-column>
        <el-table-column label="事项名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.matterName || row.matter_name || '—' }}</template>
        </el-table-column>
        <el-table-column label="事项类型" width="120">
          <template #default="{ row }">{{ row.matterType || row.matter_type || '—' }}</template>
        </el-table-column>
        <el-table-column label="范围" width="90">
          <template #default="{ row }">{{ scopeLabel(row.regionScope) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
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

    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑事项' : '新建事项'" width="520px" destroy-on-close>
      <el-form label-width="96px">
        <el-form-item label="事项编码" required>
          <el-input v-model="dialog.matterCode" :disabled="!!dialog.id" />
        </el-form-item>
        <el-form-item label="事项名称" required>
          <el-input v-model="dialog.matterName" />
        </el-form-item>
        <el-form-item label="事项类型">
          <el-input v-model="dialog.matterType" />
        </el-form-item>
        <el-form-item label="范围">
          <el-select v-model="dialog.regionScope" style="width:100%">
            <el-option label="国家" value="NATIONAL" />
            <el-option label="省级" value="PROVINCE" />
            <el-option label="市级" value="CITY" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="dialog.status">
            <el-radio-button value="ACTIVE">启用</el-radio-button>
            <el-radio-button value="INACTIVE">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dialog.sortOrder" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
  align-items: center;
}
</style>
