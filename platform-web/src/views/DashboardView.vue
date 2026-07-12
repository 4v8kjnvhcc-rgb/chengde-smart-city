<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  Connection,
  Coin,
  DataAnalysis,
  Setting,
} from '@element-plus/icons-vue'
import {
  firstNavPath,
  getAuthorizedPlatforms,
  visibleMenuChildren,
} from '@/utils/menu'
import type { MenuNode } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const platformIcons: Record<string, object> = {
  '/exchange': Connection,
  '/master-data': Coin,
  '/analytics': DataAnalysis,
  '/system': Setting,
}

const platformThemes: Record<
  string,
  {
    border: string
    headerBg: string
    iconBg: string
    iconColor: string
    titleColor: string
    accent: string
    dotColor: string
  }
> = {
  '/exchange': {
    border: '#B5D4F4',
    headerBg: '#E6F1FB',
    iconBg: '#B5D4F4',
    iconColor: '#185FA5',
    titleColor: '#0C447C',
    accent: '#378ADD',
    dotColor: '#85B7EB',
  },
  '/master-data': {
    border: '#9FE1CB',
    headerBg: '#E1F5EE',
    iconBg: '#9FE1CB',
    iconColor: '#0F6E56',
    titleColor: '#085041',
    accent: '#1D9E75',
    dotColor: '#5DCAA5',
  },
  '/analytics': {
    border: '#FAC775',
    headerBg: '#FAEEDA',
    iconBg: '#FAC775',
    iconColor: '#854F0B',
    titleColor: '#633806',
    accent: '#BA7517',
    dotColor: '#EF9F27',
  },
  '/system': {
    border: '#CECBF6',
    headerBg: '#EEEDFE',
    iconBg: '#CECBF6',
    iconColor: '#534AB7',
    titleColor: '#3C3489',
    accent: '#7F77DD',
    dotColor: '#AFA9EC',
  },
}

const platforms = computed(() => getAuthorizedPlatforms(auth.menus))

const activeIndex = ref<number | null>(null)

function getTheme(path: string) {
  return platformThemes[path] || platformThemes['/exchange']
}

function toggleDrawer(index: number) {
  if (activeIndex.value === index) {
    activeIndex.value = null
  } else {
    activeIndex.value = index
  }
}

function enterSubsystem(node: MenuNode) {
  const target = firstNavPath(node)
  if (target) router.push(target)
}

function getSubSystems(node: MenuNode): MenuNode[] {
  return visibleMenuChildren(node)
}
</script>

<template>
  <div class="portal-drawer">
    <div class="portal-drawer__header">
      <h1 class="portal-drawer__title">承德高新区智慧城市数据中台</h1>
      <el-button type="primary" plain class="d05-search-btn" @click="router.push('/catalog')">
        D05 全量功能检索（M001～M215）
      </el-button>
    </div>

    <div v-if="platforms.length" class="cards-row">
      <div
        v-for="(node, index) in platforms"
        :key="node.id"
        class="drawer-card"
        :class="{
          'is-open': activeIndex === index,
          'is-dim': activeIndex !== null && activeIndex !== index,
        }"
        :style="{
          '--card-border': getTheme(node.path).border,
          '--card-header-bg': getTheme(node.path).headerBg,
          '--card-icon-bg': getTheme(node.path).iconBg,
          '--card-icon-color': getTheme(node.path).iconColor,
          '--card-title-color': getTheme(node.path).titleColor,
          '--card-accent': getTheme(node.path).accent,
          '--card-dot-color': getTheme(node.path).dotColor,
        }"
      >
        <button
          type="button"
          class="card-header"
          :aria-expanded="activeIndex === index"
          @click="toggleDrawer(index)"
        >
          <div class="card-icon-wrap">
            <el-icon :size="54">
              <component :is="platformIcons[node.path] || Connection" />
            </el-icon>
          </div>
          <div class="card-title">{{ node.menuName }}</div>
          <div class="card-arrow" />
        </button>

        <div class="drawer-body">
          <div class="drawer-body-inner">
            <button
              v-for="child in getSubSystems(node)"
              :key="child.id"
              type="button"
              class="sub-item"
              @click="enterSubsystem(child)"
            >
              <span class="sub-dot" />
              <span class="sub-name">{{ child.menuName }}</span>
              <span class="sub-arrow">→</span>
            </button>
            <div class="drawer-footer">{{ getSubSystems(node).length }} 个子系统</div>
          </div>
        </div>
      </div>
    </div>

    <el-empty
      v-else
      class="portal-drawer__empty"
      description="当前账号暂无可访问的业务平台，请联系管理员授权"
    />
  </div>
</template>

<style scoped>
.portal-drawer {
  width: 100%;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.portal-drawer__header {
  text-align: center;
  margin-bottom: 20px;
  flex-shrink: 0;
}
.d05-search-btn {
  margin-top: 16px;
}
.portal-drawer__title {
  font-size: 56px;
  font-weight: 600;
  margin: 0;
  background: linear-gradient(90deg, #8ecfff 0%, #e8f6ff 42%, #f2d68a 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  filter: drop-shadow(0 2px 10px rgba(0, 20, 50, 0.55));
}

.cards-row {
  flex: 1;
  display: flex;
  gap: 24px;
  align-items: flex-start;
  justify-content: center;
  min-height: 0;
  margin-top: 40px;
}

.drawer-card {
  flex: 0 0 222px;
  width: 222px;
  align-self: flex-start;
  max-height: 100%;
  display: flex;
  flex-direction: column;
  border-radius: 11px;
  overflow: hidden;
  border: 1px solid var(--card-border);
  box-sizing: border-box;
  transition:
    opacity 400ms cubic-bezier(0.16, 1, 0.3, 1),
    height 400ms cubic-bezier(0.16, 1, 0.3, 1);
}
.drawer-card:not(.is-open) {
  height: 252px;
}
.drawer-card.is-open {
  align-self: flex-start;
  width: 222px;
  flex: 0 0 auto;
  height: auto;
}
.drawer-card.is-dim {
  opacity: 0.45;
}

.card-header {
  width: 100%;
  flex: 0 0 100%;
  min-height: 0;
  box-sizing: border-box;
  padding: 24px 18px;
  cursor: pointer;
  user-select: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  background: var(--card-header-bg);
  border: none;
  outline: none;
  transition: flex 400ms cubic-bezier(0.16, 1, 0.3, 1), padding 300ms ease;
}
.drawer-card.is-open .card-header {
  flex: 0 0 252px;
  height: 252px;
}
.card-header:focus-visible {
  outline: 2px solid var(--card-accent);
  outline-offset: -2px;
}

.card-icon-wrap {
  width: 84px;
  height: 84px;
  border-radius: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--card-icon-bg);
  color: var(--card-icon-color);
  transition: transform 300ms cubic-bezier(0.16, 1, 0.3, 1);
}
.drawer-card:hover .card-icon-wrap {
  transform: translateY(-2px);
}

.card-title {
  width: 100%;
  height: 2.8em;
  font-size: 24px;
  font-weight: 500;
  text-align: center;
  line-height: 1.4;
  color: var(--card-title-color);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  word-break: break-all;
}

.card-arrow {
  width: 42px;
  height: 6px;
  border-radius: 3px;
  background: var(--card-accent);
  transition: all 300ms ease;
}
.drawer-card.is-open .card-arrow {
  width: 54px;
  opacity: 0.5;
}

.drawer-body {
  flex: 0 0 auto;
  max-height: 0;
  overflow: hidden;
  background: #fafbfd;
  border-top: 1px solid transparent;
  transition:
    max-height 400ms cubic-bezier(0.16, 1, 0.3, 1),
    flex 400ms cubic-bezier(0.16, 1, 0.3, 1),
    border-color 200ms ease;
}
.drawer-card.is-open .drawer-body {
  flex: 0 0 auto;
  max-height: none;
  overflow: visible;
  border-top-color: rgba(0, 0, 0, 0.08);
}

.drawer-body-inner {
  padding: 6px 0;
}

.sub-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 21px;
  cursor: pointer;
  transition: all 150ms ease;
  border: none;
  border-left: 3px solid transparent;
  background: transparent;
  outline: none;
  text-align: left;
}
.sub-item:hover,
.sub-item:focus-visible {
  background: #f0f4fa;
  border-left-color: var(--card-accent);
}
.sub-item:focus-visible {
  outline: 2px solid var(--card-accent);
  outline-offset: -2px;
}

.sub-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--card-dot-color);
}

.sub-name {
  font-size: 18px;
  font-weight: 400;
  color: #3a4a5c;
  line-height: 1.4;
}
.sub-item:hover .sub-name {
  color: #1e3a5f;
}

.sub-arrow {
  margin-left: auto;
  font-size: 18px;
  color: #bbc4ce;
  opacity: 0;
  transition: opacity 150ms ease;
}
.sub-item:hover .sub-arrow {
  opacity: 1;
}

.drawer-footer {
  padding: 6px 14px 8px;
  font-size: 11px;
  color: #a0aab8;
  text-align: center;
}

.portal-drawer__empty {
  padding: 48px 0;
}

@media (prefers-reduced-motion: reduce) {
  .drawer-card,
  .card-header,
  .card-icon-wrap,
  .card-arrow,
  .drawer-body,
  .sub-item,
  .sub-arrow {
    transition: none !important;
    transform: none !important;
  }
}

@media (max-width: 768px) {
  .cards-row {
    flex-direction: column;
    flex: 1;
    justify-content: flex-start;
    align-items: center;
  }
  .drawer-card:not(.is-open) {
    height: 252px;
  }
  .drawer-card.is-open {
    flex: 0 0 auto;
    width: 222px;
    height: auto;
  }
}
</style>
