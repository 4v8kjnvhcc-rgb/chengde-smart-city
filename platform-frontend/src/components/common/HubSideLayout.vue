<script setup lang="ts">
import { computed } from 'vue'
import HubNavNodes from './HubNavNodes.vue'
import type { HubNavItem, HubNavGroup } from './hub-nav'

export type { HubNavItem, HubNavGroup } from './hub-nav'

const props = defineProps<{
  modelValue: string
  items?: HubNavItem[]
  groups?: HubNavGroup[]
  /** @deprecated 二级项请用 children 挂在一级下，不再嵌套侧栏 */
  nested?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  /** 每次点击都会触发（含重复点击当前项），便于父级强制切回列表等 */
  select: [value: string]
}>()

function onSelect(key: string) {
  emit('update:modelValue', key)
  emit('select', key)
}

/** 从根到叶子的祖先 key，用于 default-openeds */
function ancestorKeysOf(leaf: string, list: HubNavItem[], path: string[] = []): string[] | null {
  for (const item of list) {
    if (item.key === leaf) return path
    if (item.children?.length) {
      const found = ancestorKeysOf(leaf, item.children, [...path, item.key])
      if (found) return found
    }
  }
  return null
}

const flatItems = computed(() => {
  if (props.groups?.length) {
    return props.groups.flatMap((g) => g.items)
  }
  return props.items || []
})

const defaultOpeneds = computed(() => ancestorKeysOf(props.modelValue, flatItems.value) || [])

/** 仅在父级变化时重挂菜单，避免展开态丢失过多；仍保证 active 正确 */
const menuRemountKey = computed(() => defaultOpeneds.value.join(',') || 'root')
</script>

<template>
  <div class="hub-side-layout" :class="{ 'hub-side-layout--nested': nested }">
    <aside class="hub-side-layout__aside">
      <el-menu
        class="portal-sidebar-menu hub-side-menu"
        background-color="transparent"
        text-color="rgba(255,255,255,0.75)"
        active-text-color="#ffffff"
        :default-active="modelValue"
        :default-openeds="defaultOpeneds"
        :key="`hub-menu-${menuRemountKey}-${modelValue}`"
        :unique-opened="false"
        @select="onSelect"
      >
        <template v-if="groups?.length">
          <template v-for="group in groups" :key="group.title">
            <div v-if="group.title" class="hub-side-group-title">{{ group.title }}</div>
            <HubNavNodes :items="group.items" />
          </template>
        </template>
        <HubNavNodes v-else :items="items || []" />
      </el-menu>
    </aside>
    <main class="hub-side-layout__main">
      <slot>
        <div class="hub-side-empty">请选择左侧功能项</div>
      </slot>
    </main>
  </div>
</template>

<style scoped>
.hub-side-layout {
  display: flex;
  gap: 0;
  /* 仅扣顶栏与 portal-main 上下 padding（20+20），不再为已移除的 PageHeader 预留高度 */
  height: calc(100vh - var(--portal-header-height) - 40px);
  max-height: calc(100vh - var(--portal-header-height) - 40px);
  min-height: 320px;
  border: 1px solid var(--portal-border);
  border-radius: var(--portal-radius);
  overflow: hidden;
  background: #fff;
}
.hub-side-layout__aside {
  width: var(--portal-sidebar-width);
  flex-shrink: 0;
  align-self: stretch;
  background: var(--portal-sidebar-bg);
  overflow-x: hidden;
  overflow-y: auto;
}
.hub-side-menu {
  border-right: none;
  background: transparent !important;
}
.hub-side-group-title {
  padding: 12px 20px 6px;
  font-size: 12px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.45);
  letter-spacing: 0.02em;
}
.hub-side-menu :deep(.el-menu-item),
.hub-side-menu :deep(.el-sub-menu__title) {
  height: 44px;
  line-height: 44px;
  padding: 0 20px !important;
  color: rgba(255, 255, 255, 0.75);
  background: transparent;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.hub-side-menu :deep(.el-menu-item:hover),
.hub-side-menu :deep(.el-sub-menu__title:hover) {
  background: rgba(255, 255, 255, 0.08) !important;
  color: #fff;
}
.hub-side-menu :deep(.el-menu-item.is-active) {
  background: var(--portal-primary) !important;
  color: #fff;
}
.hub-side-menu :deep(.el-sub-menu .el-menu) {
  background: transparent !important;
}
.hub-side-menu :deep(.el-sub-menu .el-menu-item) {
  min-width: auto;
  padding-left: 36px !important;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.75);
  background: transparent;
}
.hub-side-menu :deep(.el-sub-menu .el-sub-menu .el-menu-item) {
  padding-left: 48px !important;
  font-size: 13px;
}
.hub-side-menu :deep(.el-sub-menu .el-sub-menu > .el-sub-menu__title) {
  padding-left: 36px !important;
  font-size: 13px;
}
.hub-side-menu :deep(.el-sub-menu__icon-arrow) {
  color: rgba(255, 255, 255, 0.65);
}
.hub-side-layout__main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  padding: 16px;
  overflow-x: hidden;
  overflow-y: auto;
  background: var(--portal-bg);
}
.hub-side-empty {
  padding: 48px 16px;
  text-align: center;
  color: var(--portal-text-secondary);
  font-size: 14px;
}
.hub-side-layout--nested {
  height: calc(100vh - var(--portal-header-height) - 80px);
  max-height: calc(100vh - var(--portal-header-height) - 80px);
  min-height: 320px;
  border: none;
  border-radius: 0;
}
.hub-side-layout--nested .hub-side-layout__aside {
  width: 160px;
}
.hub-side-layout--nested .hub-side-layout__main {
  padding: 0 0 0 12px;
  background: transparent;
}
</style>

<style>
/* HubNavNodes 在子组件内渲染，标签 class 需全局可读（侧栏内） */
.hub-side-menu .hub-side-label {
  font-size: 14px;
  color: inherit;
}
.hub-side-menu .hub-side-label--single {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
}
</style>
