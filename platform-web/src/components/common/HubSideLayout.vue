<script setup lang="ts">
import { computed } from 'vue'

export interface HubNavItem {
  key: string
  label: string
  subLabel?: string
  /** 子菜单项（展开在一级菜单下方） */
  children?: HubNavItem[]
}

export interface HubNavGroup {
  title: string
  items: HubNavItem[]
}

const props = defineProps<{
  modelValue: string
  items?: HubNavItem[]
  groups?: HubNavGroup[]
  /** @deprecated 二级项请用 children 挂在一级下，不再嵌套侧栏 */
  nested?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

function onSelect(key: string) {
  emit('update:modelValue', key)
}

function parentKeyOf(leaf: string, list: HubNavItem[]): string | null {
  for (const item of list) {
    if (item.children?.some((c) => c.key === leaf)) return item.key
    if (item.key === leaf) return item.key
  }
  return null
}

const flatItems = computed(() => {
  if (props.groups?.length) {
    return props.groups.flatMap((g) => g.items)
  }
  return props.items || []
})

const defaultOpeneds = computed(() => {
  const p = parentKeyOf(props.modelValue, flatItems.value)
  return p ? [p] : []
})

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
        :unique-opened="true"
        @select="onSelect"
      >
        <template v-if="groups?.length">
          <template v-for="group in groups" :key="group.title">
            <div v-if="group.title" class="hub-side-group-title">{{ group.title }}</div>
            <template v-for="item in group.items" :key="item.key">
              <el-sub-menu v-if="item.children?.length" :index="item.key">
                <template #title>
                  <span class="hub-side-label hub-side-label--single">{{ item.label }}</span>
                </template>
                <el-menu-item v-for="child in item.children" :key="child.key" :index="child.key">
                  <span class="hub-side-label hub-side-label--single">{{ child.label }}</span>
                </el-menu-item>
              </el-sub-menu>
              <el-menu-item v-else :index="item.key">
                <el-tooltip v-if="item.subLabel" :content="item.subLabel" placement="right" :show-after="300">
                  <span class="hub-side-label hub-side-label--single">{{ item.label }}</span>
                </el-tooltip>
                <span v-else class="hub-side-label hub-side-label--single">{{ item.label }}</span>
              </el-menu-item>
            </template>
          </template>
        </template>
        <template v-else>
          <template v-for="item in items" :key="item.key">
            <el-sub-menu v-if="item.children?.length" :index="item.key">
              <template #title>
                <span class="hub-side-label hub-side-label--single">{{ item.label }}</span>
              </template>
              <el-menu-item v-for="child in item.children" :key="child.key" :index="child.key">
                <span class="hub-side-label hub-side-label--single">{{ child.label }}</span>
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="item.key">
              <el-tooltip v-if="item.subLabel" :content="item.subLabel" placement="right" :show-after="300">
                <span class="hub-side-label hub-side-label--single">{{ item.label }}</span>
              </el-tooltip>
              <span v-else class="hub-side-label hub-side-label--single">{{ item.label }}</span>
            </el-menu-item>
          </template>
        </template>
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
  min-height: calc(100vh - var(--portal-header-height) - 120px);
  border: 1px solid var(--portal-border);
  border-radius: var(--portal-radius);
  overflow: hidden;
  background: #fff;
}
.hub-side-layout__aside {
  width: var(--portal-sidebar-width);
  flex-shrink: 0;
  background: var(--portal-sidebar-bg);
  overflow-y: auto;
  max-height: calc(100vh - var(--portal-header-height) - 120px);
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
/* 展开后的内嵌子菜单：与主侧栏同色底，避免 Element 默认白底黑字 */
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
.hub-side-menu :deep(.el-sub-menu__icon-arrow) {
  color: rgba(255, 255, 255, 0.65);
}
.hub-side-label {
  font-size: 14px;
  color: inherit;
}
.hub-side-label--single {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
}
.hub-side-layout__main {
  flex: 1;
  min-width: 0;
  min-height: 320px;
  padding: 16px;
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
  min-height: calc(100vh - var(--portal-header-height) - 160px);
  border: none;
  border-radius: 0;
}
.hub-side-layout--nested .hub-side-layout__aside {
  width: 160px;
  max-height: calc(100vh - var(--portal-header-height) - 160px);
}
.hub-side-layout--nested .hub-side-layout__main {
  padding: 0 0 0 12px;
  background: transparent;
}
</style>
