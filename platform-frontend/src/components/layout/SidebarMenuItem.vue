<script setup lang="ts">
import type { MenuNode } from '@/stores/auth'
import {
  Connection,
  Coin,
  DataAnalysis,
  HomeFilled,
  Odometer,
  Setting,
} from '@element-plus/icons-vue'

const props = defineProps<{
  node: MenuNode
}>()

const iconMap: Record<string, object> = {
  '/exchange': Connection,
  '/master-data': Coin,
  '/analytics': DataAnalysis,
  '/system': Setting,
  '/integration': Connection,
  '/dashboard': Odometer,
  '/': HomeFilled,
}

function pickIcon(node: MenuNode) {
  if (node.icon && iconMap[node.path]) return iconMap[node.path]
  if (node.path && iconMap[node.path]) return iconMap[node.path]
  return null
}

/** Hub 内页（tab 叶子 / 内部分组）不进门户侧栏；visible=0 为显式隐藏 */
function isPortalHidden(c: MenuNode): boolean {
  if (c.menuType === 3) return true
  if (c.visible === 0) return true
  if (c.integrationType === 'hub') {
    const p = c.path || ''
    if (p.includes('?tab=') || p.includes('&tab=')) return true
    if (c.menuType === 1) return true
  }
  return false
}

function visibleChildren(node: MenuNode): MenuNode[] {
  return (node.children || []).filter((c) => !isPortalHidden(c))
}

function isLeafMenu(node: MenuNode) {
  const children = visibleChildren(node)
  return children.length === 0 && node.path && node.path !== '/'
}
</script>

<template>
  <el-sub-menu v-if="visibleChildren(node).length" :index="node.path || String(node.id)">
    <template #title>
      <el-icon v-if="pickIcon(node)"><component :is="pickIcon(node)" /></el-icon>
      <span>{{ node.menuName }}</span>
    </template>
    <SidebarMenuItem v-for="child in visibleChildren(node)" :key="child.id" :node="child" />
  </el-sub-menu>
  <el-menu-item v-else-if="isLeafMenu(node)" :index="node.path!">
    <el-icon v-if="pickIcon(node)"><component :is="pickIcon(node)" /></el-icon>
    <span>{{ node.menuName }}</span>
  </el-menu-item>
</template>
