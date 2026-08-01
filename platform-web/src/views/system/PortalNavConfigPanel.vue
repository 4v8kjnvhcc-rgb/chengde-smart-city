<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

export interface PortalNavNode {
  id: number
  parentId: number
  name: string
  nodeType: string
  sortOrder: number
  url?: string
  menuPath?: string
  openMode?: string
  themeKey?: string
  remark?: string
  status: number
}

const TYPE_LABEL: Record<string, string> = {
  platform: '平台',
  sub_platform: '子平台',
  system: '系统',
}

const THEME_OPTIONS = [
  { value: '/exchange', label: '/exchange · 数据共享交换' },
  { value: '/master-data', label: '/master-data · 主数据' },
  { value: '/analytics', label: '/analytics · 挖掘分析' },
  { value: '/business', label: '/business · 业务功能' },
]

const auth = useAuthStore()
const loading = ref(false)
const rows = ref<PortalNavNode[]>([])
const selectedId = ref<number | null>(null)
const dialogVisible = ref(false)
const dialogMode = ref<'view' | 'edit' | 'create'>('create')
const submitting = ref(false)

const form = reactive({
  parentId: 0 as number,
  name: '',
  nodeType: 'system',
  sortOrder: 0,
  url: '',
  menuPath: '',
  openMode: 'route',
  themeKey: '',
  remark: '',
  status: 1,
})

const canAdd = computed(() => auth.hasPermission('system:portal-nav:add') || auth.isSystemAdmin)
const canEdit = computed(() => auth.hasPermission('system:portal-nav:edit') || auth.isSystemAdmin)
const canDelete = computed(() => auth.hasPermission('system:portal-nav:delete') || auth.isSystemAdmin)

const depthMap = computed(() => {
  const map = new Map<number, number>()
  const byId = new Map(rows.value.map((r) => [r.id, r]))
  function depth(id: number, guard = 0): number {
    if (map.has(id)) return map.get(id)!
    if (guard > 16) return 0
    const n = byId.get(id)
    if (!n || !n.parentId) {
      map.set(id, 0)
      return 0
    }
    const d = depth(n.parentId, guard + 1) + 1
    map.set(id, d)
    return d
  }
  rows.value.forEach((r) => depth(r.id))
  return map
})

const parentOptions = computed(() => {
  return rows.value
    .filter((r) => r.nodeType === 'platform' || r.nodeType === 'sub_platform')
    .map((r) => ({
      value: r.id,
      label: `${'　'.repeat(depthMap.value.get(r.id) || 0)}${TYPE_LABEL[r.nodeType] || r.nodeType} · ${r.name}`,
    }))
})

const selectedRow = computed(() => rows.value.find((r) => r.id === selectedId.value) || null)

async function load() {
  loading.value = true
  try {
    const res = await api.get<PortalNavNode[]>('/system/portal-nav', { params: { format: 'flat' } })
    rows.value = res.data || []
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载门户配置失败')
    rows.value = []
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.parentId = 0
  form.name = ''
  form.nodeType = 'system'
  form.sortOrder = 0
  form.url = ''
  form.menuPath = ''
  form.openMode = 'route'
  form.themeKey = ''
  form.remark = ''
  form.status = 1
}

function fillForm(row: PortalNavNode) {
  form.parentId = row.parentId ?? 0
  form.name = row.name
  form.nodeType = row.nodeType
  form.sortOrder = row.sortOrder ?? 0
  form.url = row.url || ''
  form.menuPath = row.menuPath || ''
  form.openMode = row.openMode || 'route'
  form.themeKey = row.themeKey || ''
  form.remark = row.remark || ''
  form.status = row.status ?? 1
}

function openCreate() {
  dialogMode.value = 'create'
  resetForm()
  if (selectedRow.value) {
    if (selectedRow.value.nodeType === 'platform') {
      form.nodeType = 'sub_platform'
      form.parentId = selectedRow.value.id
    } else if (selectedRow.value.nodeType === 'sub_platform') {
      form.nodeType = 'system'
      form.parentId = selectedRow.value.id
    }
  }
  dialogVisible.value = true
}

function openView(row: PortalNavNode) {
  selectedId.value = row.id
  dialogMode.value = 'view'
  fillForm(row)
  dialogVisible.value = true
}

function openEdit(row?: PortalNavNode | null) {
  const target = row || selectedRow.value
  if (!target) {
    ElMessage.warning('请先选择一行')
    return
  }
  selectedId.value = target.id
  dialogMode.value = 'edit'
  fillForm(target)
  dialogVisible.value = true
}

async function submit() {
  if (dialogMode.value === 'view') {
    dialogVisible.value = false
    return
  }
  if (!form.name.trim()) {
    ElMessage.warning('请填写名称')
    return
  }
  if (form.nodeType !== 'platform' && !form.parentId) {
    ElMessage.warning('请选择上级节点')
    return
  }
  submitting.value = true
  try {
    const payload = {
      parentId: form.nodeType === 'platform' ? 0 : form.parentId,
      name: form.name.trim(),
      nodeType: form.nodeType,
      sortOrder: form.sortOrder,
      url: form.url || null,
      menuPath: form.menuPath || null,
      openMode: form.openMode || 'route',
      themeKey: form.nodeType === 'platform' ? form.themeKey || null : null,
      remark: form.remark || null,
      status: form.status,
    }
    if (dialogMode.value === 'create') {
      await api.post('/system/portal-nav', payload)
      ElMessage.success('已新增')
    } else if (selectedId.value != null) {
      await api.put(`/system/portal-nav/${selectedId.value}`, payload)
      ElMessage.success('已保存')
    }
    dialogVisible.value = false
    await load()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    submitting.value = false
  }
}

async function removeRow(row?: PortalNavNode | null) {
  const target = row || selectedRow.value
  if (!target) {
    ElMessage.warning('请先选择一行')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除「${target.name}」？有子节点时将失败。`, '删除确认', { type: 'warning' })
    await api.delete(`/system/portal-nav/${target.id}`)
    ElMessage.success('已删除')
    if (selectedId.value === target.id) selectedId.value = null
    await load()
  } catch (e: unknown) {
    if (e === 'cancel') return
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

function displayAddress(row: PortalNavNode) {
  return row.url || row.menuPath || '—'
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="portal-nav-panel">
    <div class="toolbar">
      <el-button type="primary" :disabled="!canAdd" @click="openCreate">新增</el-button>
      <el-button :disabled="!canEdit || !selectedRow" @click="openEdit()">修改</el-button>
      <el-button type="danger" plain :disabled="!canDelete || !selectedRow" @click="removeRow()">删除</el-button>
      <el-button @click="load">刷新</el-button>
      <span class="hint">首页四平台卡片按本表渲染；侧栏权限仍走菜单授权。</span>
    </div>

    <el-table
      class="portal-table"
      :data="rows"
      stripe
      size="small"
      highlight-current-row
      empty-text="暂无门户导航配置"
      @current-change="(row: PortalNavNode | null) => (selectedId = row?.id ?? null)"
      @row-click="(row: PortalNavNode) => (selectedId = row.id)"
    >
      <el-table-column label="名称" min-width="220">
        <template #default="{ row }">
          <span :style="{ paddingLeft: `${(depthMap.get(row.id) || 0) * 16}px` }">{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ TYPE_LABEL[row.nodeType] || row.nodeType }}</template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="70" align="center" />
      <el-table-column label="地址" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ displayAddress(row) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="openView(row)">查看</el-button>
          <el-button link type="primary" :disabled="!canEdit" @click.stop="openEdit(row)">编辑</el-button>
          <el-button link type="danger" :disabled="!canDelete" @click.stop="removeRow(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增门户节点' : dialogMode === 'edit' ? '编辑门户节点' : '查看门户节点'"
      width="560px"
      destroy-on-close
    >
      <el-form label-width="96px" :disabled="dialogMode === 'view'">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.nodeType" style="width: 100%">
            <el-option label="平台" value="platform" />
            <el-option label="子平台" value="sub_platform" />
            <el-option label="系统" value="system" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.nodeType !== 'platform'" label="上级节点" required>
          <el-select v-model="form.parentId" filterable style="width: 100%" placeholder="选择上级">
            <el-option
              v-for="opt in parentOptions.filter((o) =>
                form.nodeType === 'sub_platform'
                  ? rows.find((r) => r.id === o.value)?.nodeType === 'platform'
                  : rows.find((r) => r.id === o.value)?.nodeType === 'sub_platform',
              )"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="排序" required>
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.url" placeholder="站内 /path 或 https:// 外链" />
        </el-form-item>
        <el-form-item label="菜单路径">
          <el-input v-model="form.menuPath" placeholder="用于权限过滤，如 /exchange/ingestion" />
        </el-form-item>
        <el-form-item label="打开方式">
          <el-select v-model="form.openMode" style="width: 100%">
            <el-option label="站内路由" value="route" />
            <el-option label="新窗口" value="new_tab" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.nodeType === 'platform'" label="主题键">
          <el-select v-model="form.themeKey" clearable style="width: 100%" placeholder="卡片配色">
            <el-option v-for="t in THEME_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="512" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ dialogMode === 'view' ? '关闭' : '取消' }}</el-button>
        <el-button v-if="dialogMode !== 'view'" type="primary" :loading="submitting" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.hint {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.portal-table {
  width: 100%;
}
</style>
