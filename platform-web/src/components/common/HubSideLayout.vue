<script setup lang="ts">
export interface HubNavItem {
  key: string
  label: string
  subLabel?: string
}

export interface HubNavGroup {
  title: string
  items: HubNavItem[]
}

const props = defineProps<{
  modelValue: string
  items?: HubNavItem[]
  groups?: HubNavGroup[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

function onSelect(key: string) {
  emit('update:modelValue', key)
}
</script>

<template>
  <div class="hub-side-layout">
    <aside class="hub-side-layout__aside">
      <el-menu
        class="portal-sidebar-menu hub-side-menu"
        :default-active="modelValue"
        :key="`hub-menu-${modelValue}`"
        @select="onSelect"
      >
        <template v-if="groups?.length">
          <template v-for="group in groups" :key="group.title">
            <div v-if="group.title" class="hub-side-group-title">{{ group.title }}</div>
            <el-menu-item v-for="item in group.items" :key="item.key" :index="item.key">
              <el-tooltip v-if="item.subLabel" :content="item.subLabel" placement="right" :show-after="300">
                <span class="hub-side-label hub-side-label--single">{{ item.label }}</span>
              </el-tooltip>
              <span v-else class="hub-side-label hub-side-label--single">{{ item.label }}</span>
            </el-menu-item>
          </template>
        </template>
        <template v-else>
          <el-menu-item v-for="item in items" :key="item.key" :index="item.key">
            <el-tooltip v-if="item.subLabel" :content="item.subLabel" placement="right" :show-after="300">
              <span class="hub-side-label hub-side-label--single">{{ item.label }}</span>
            </el-tooltip>
            <span v-else class="hub-side-label hub-side-label--single">{{ item.label }}</span>
          </el-menu-item>
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
}
.hub-side-group-title {
  padding: 12px 20px 6px;
  font-size: 12px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.45);
  letter-spacing: 0.02em;
}
.hub-side-menu :deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
  padding: 0 20px !important;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.hub-side-label {
  font-size: 14px;
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
</style>
