<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import api from '@/api/http'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel } from '@/utils/status-label'

interface Menu {
  id: number
  parentId: number
  menuName: string
  path: string
  menuType: number
  mCode: string
  permission?: string
  sortOrder?: number
  status?: number
  children?: Menu[]
}

const menus = ref<Menu[]>([])

const menuTree = computed(() => {
  const map = new Map<number, Menu>()
  const roots: Menu[] = []
  for (const m of menus.value) {
    map.set(m.id, { ...m, children: [] })
  }
  for (const m of map.values()) {
    const p = m.parentId
    if (p && map.has(p)) {
      map.get(p)!.children!.push(m)
    } else {
      roots.push(m)
    }
  }
  const sortRec = (list: Menu[]) => {
    list.sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.id - b.id)
    list.forEach((n) => n.children && sortRec(n.children))
  }
  sortRec(roots)
  return roots
})

function typeLabel(t: number) {
  if (t === 1) return '目录'
  if (t === 3) return '按钮'
  return '菜单'
}

onMounted(async () => {
  const res = await api.get('/system/menus')
  menus.value = res.data || []
})
</script>

<template>
  <div>
    <PageHeader
      title="菜单目录"
      description="门户菜单树。角色菜单授权请在「统一用户管理 · 用户中心」调整。"
    />
    <PageCard>
      <el-table
        class="portal-table"
        :data="menuTree"
        row-key="id"
        stripe
        default-expand-all
        :tree-props="{ children: 'children' }"
        height="560"
      >
        <el-table-column prop="menuName" label="名称" min-width="220" />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">{{ typeLabel(row.menuType) }}</template>
        </el-table-column>
        <el-table-column prop="path" label="路径" min-width="180" show-overflow-tooltip />
        <el-table-column prop="permission" label="权限码" min-width="160" show-overflow-tooltip />
        <el-table-column prop="mCode" label="M编号" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            {{ statusLabel(row.status === 0 ? 'DISABLED' : 'ACTIVE') }}
          </template>
        </el-table-column>
      </el-table>
    </PageCard>
  </div>
</template>
