<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  Connection,
  Coin,
  DataAnalysis,
  Grid,
  Setting,
} from '@element-plus/icons-vue'
import {
  firstNavPath,
  getAuthorizedPlatforms,
  visibleMenuChildren,
} from '@/utils/menu'
import type { MenuNode } from '@/stores/auth'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import { openAssessmentExternal } from '@/views/exchange/application/application-nav'

interface PortalLink {
  id: number
  platformPath: string
  title: string
  url: string
  openMode: string
  ssoMode: string
  ssoParam: string
}

type CardItem =
  | { kind: 'menu'; key: string; title: string; node: MenuNode; children?: CardItem[] }
  | { kind: 'link'; key: string; title: string; link: PortalLink }

const auth = useAuthStore()
const router = useRouter()

const platformIcons: Record<string, object> = {
  '/exchange': Connection,
  '/master-data': Coin,
  '/analytics': DataAnalysis,
  '/business': Grid,
  '/integration': Connection,
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
  '/business': {
    border: '#9AD4C8',
    headerBg: '#E6F7F3',
    iconBg: '#9AD4C8',
    iconColor: '#0B6E63',
    titleColor: '#085048',
    accent: '#1D9A88',
    dotColor: '#5CBEAE',
  },
  '/integration': {
    border: '#B5C7D9',
    headerBg: '#EEF3F8',
    iconBg: '#B5C7D9',
    iconColor: '#3D5A73',
    titleColor: '#2A4055',
    accent: '#5B7C99',
    dotColor: '#8FA8BF',
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

const portalLinks = ref<PortalLink[]>([])
const platforms = computed(() => getAuthorizedPlatforms(auth.menus))

const activeIndex = ref<number | null>(null)
/** 抽屉内悬浮展开的分组 key（应用分析门户 / 应用平台） */
const hoverGroupKey = ref<string | null>(null)

function getTheme(path: string) {
  return platformThemes[path] || platformThemes['/exchange']
}

function linksOf(platformPath: string): PortalLink[] {
  return portalLinks.value.filter((l) => l.platformPath === platformPath)
}

function isExchangeGroup(node: MenuNode, child: MenuNode): boolean {
  if (node.path !== '/exchange') return false
  const path = child.path || ''
  return (
    path === '/exchange/analysis-portal' ||
    path === '/exchange/application' ||
    child.menuName === '应用分析门户' ||
    child.menuName === '应用平台'
  )
}

/**
 * 目标 IA：一级显示 归集 / ESB / 应用分析门户 / 应用平台；
 * 后两者挂 children，悬浮展开，点叶子进各自独立系统。
 */
function getCardItems(node: MenuNode): CardItem[] {
  const menus: CardItem[] = []

  for (const child of visibleMenuChildren(node)) {
    const grandchildren = visibleMenuChildren(child)
    if (isExchangeGroup(node, child) && grandchildren.length > 0) {
      menus.push({
        kind: 'menu',
        key: `m-${child.id}`,
        title: child.menuName,
        node: child,
        children: grandchildren.map((leaf) => ({
          kind: 'menu' as const,
          key: `m-${leaf.id}`,
          title: leaf.menuName,
          node: leaf,
        })),
      })
      continue
    }
    menus.push({
      kind: 'menu',
      key: `m-${child.id}`,
      title: child.menuName,
      node: child,
    })
  }

  const links: CardItem[] = linksOf(node.path).map((link) => ({
    kind: 'link' as const,
    key: `l-${link.id}`,
    title: link.title,
    link,
  }))
  return [...menus, ...links]
}

function itemHasChildren(item: CardItem): item is CardItem & { kind: 'menu'; children: CardItem[] } {
  return item.kind === 'menu' && !!item.children?.length
}

function toggleDrawer(index: number) {
  if (activeIndex.value === index) {
    activeIndex.value = null
  } else {
    activeIndex.value = index
  }
  hoverGroupKey.value = null
}

function isSystemPlatform(node: MenuNode) {
  return node.path === '/system'
}

function onCardHeaderClick(node: MenuNode, index: number) {
  if (isSystemPlatform(node)) {
    activeIndex.value = null
    enterSubsystem(node)
    return
  }
  toggleDrawer(index)
}

function isAssessmentMenu(node: MenuNode): boolean {
  const p = node.path || ''
  return p.includes('/application/assessment') || node.menuName === '考核评估系统'
}

function enterSubsystem(node: MenuNode) {
  if (isAssessmentMenu(node)) {
    const r = openAssessmentExternal()
    if (r.ok) {
      ElMessage.success('已在新窗口打开考核评估系统')
      return
    }
    router.push('/exchange/application/assessment')
    return
  }
  if (node.path && node.menuType !== 1) {
    router.push(node.path)
    return
  }
  const target = firstNavPath(node)
  if (target) router.push(target)
}

function buildSsoUrl(link: PortalLink): string {
  const token = auth.accessToken || localStorage.getItem('accessToken') || ''
  if (link.ssoMode !== 'token_query' || !token) {
    return link.url
  }
  try {
    if (link.url.startsWith('/')) {
      const u = new URL(link.url, window.location.origin)
      u.searchParams.set(link.ssoParam || 'access_token', token)
      return u.pathname + u.search + u.hash
    }
    const u = new URL(link.url)
    u.searchParams.set(link.ssoParam || 'access_token', token)
    return u.toString()
  } catch {
    const sep = link.url.includes('?') ? '&' : '?'
    return `${link.url}${sep}${encodeURIComponent(link.ssoParam || 'access_token')}=${encodeURIComponent(token)}`
  }
}

function openExternalLink(link: PortalLink) {
  const target = buildSsoUrl(link)
  if (link.openMode === 'same_tab') {
    if (target.startsWith('/')) {
      router.push(target)
    } else {
      window.location.href = target
    }
    return
  }
  window.open(target, '_blank', 'noopener,noreferrer')
}

function onCardItemClick(item: CardItem) {
  if (itemHasChildren(item)) {
    // 分组：悬浮展开；点击也可切换（触控友好）
    hoverGroupKey.value = hoverGroupKey.value === item.key ? null : item.key
    return
  }
  if (item.kind === 'menu') {
    enterSubsystem(item.node)
  } else {
    openExternalLink(item.link)
  }
}

function onFlyoutChildClick(item: CardItem) {
  hoverGroupKey.value = null
  onCardItemClick(item)
}

onMounted(async () => {
  try {
    const res = await api.get('/system/portal-links/enabled')
    portalLinks.value = res.data || []
  } catch {
    portalLinks.value = []
  }
})
</script>

<template>
  <div class="portal-drawer">
    <div class="portal-drawer__header">
      <h1 class="portal-drawer__title">承德高新区智慧城市数据中台</h1>
    </div>

    <div v-if="platforms.length" class="cards-row">
      <div
        v-for="(node, index) in platforms"
        :key="node.id"
        class="drawer-card"
        :class="{
          'is-open': activeIndex === index,
          'is-dim': activeIndex !== null && activeIndex !== index,
          'is-direct': isSystemPlatform(node),
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
          :aria-expanded="isSystemPlatform(node) ? undefined : activeIndex === index"
          @click="onCardHeaderClick(node, index)"
        >
          <div class="card-icon-wrap">
            <el-icon :size="44">
              <component :is="platformIcons[node.path] || Connection" />
            </el-icon>
          </div>
          <div class="card-title">{{ node.menuName }}</div>
          <div v-if="!isSystemPlatform(node)" class="card-arrow" />
        </button>

        <div v-if="!isSystemPlatform(node)" class="drawer-body">
          <div class="drawer-body-inner">
            <div
              v-for="item in getCardItems(node)"
              :key="item.key"
              class="sub-item-wrap"
              :class="{ 'has-flyout': itemHasChildren(item), 'is-flyout-open': hoverGroupKey === item.key }"
              @mouseenter="itemHasChildren(item) && (hoverGroupKey = item.key)"
              @mouseleave="hoverGroupKey = null"
            >
              <button
                type="button"
                class="sub-item"
                :class="{ 'is-group': itemHasChildren(item) }"
                @click="onCardItemClick(item)"
              >
                <span class="sub-dot" />
                <span class="sub-name">{{ item.title }}</span>
                <span v-if="item.kind === 'link'" class="sub-badge">外链</span>
                <span v-if="itemHasChildren(item)" class="sub-chevron">›</span>
                <span v-else class="sub-arrow">→</span>
              </button>
              <div v-if="itemHasChildren(item) && hoverGroupKey === item.key" class="sub-flyout">
                <button
                  v-for="child in item.children"
                  :key="child.key"
                  type="button"
                  class="sub-flyout__item"
                  @click.stop="onFlyoutChildClick(child)"
                >
                  <span class="sub-dot" />
                  <span class="sub-name">{{ child.title }}</span>
                  <span class="sub-arrow">→</span>
                </button>
              </div>
            </div>
            <div v-if="!getCardItems(node).length" class="drawer-empty">
              暂无入口，请在「系统管理 → 门户外链管理」中配置
            </div>
            <div class="drawer-footer">{{ getCardItems(node).length }} 个入口</div>
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
  overflow: visible;
}

/* 原宽 289px，缩小 10% → 260px */
.drawer-card {
  flex: 0 0 260px;
  width: 260px;
  align-self: flex-start;
  max-height: 100%;
  display: flex;
  flex-direction: column;
  border-radius: 11px;
  overflow: hidden;
  border: 1px solid var(--card-border);
  box-sizing: border-box;
  transition: opacity 200ms ease;
  position: relative;
  z-index: 1;
}
.drawer-card:not(.is-open) {
  height: 202px;
}
.drawer-card.is-open {
  align-self: flex-start;
  width: 260px;
  flex: 0 0 auto;
  height: auto;
  overflow: visible;
  z-index: 5;
}
.drawer-card.is-dim {
  opacity: 0.45;
}

.card-header {
  width: 100%;
  flex: 0 0 100%;
  min-height: 0;
  box-sizing: border-box;
  padding: 16px 14px;
  cursor: pointer;
  user-select: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: var(--card-header-bg);
  border: none;
  outline: none;
}
.drawer-card.is-open .card-header {
  flex: 0 0 202px;
  height: 202px;
}
.card-header:focus-visible {
  outline: 2px solid var(--card-accent);
  outline-offset: -2px;
}

.card-icon-wrap {
  width: 68px;
  height: 68px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--card-icon-bg);
  color: var(--card-icon-color);
}

.card-title {
  width: 100%;
  height: 2.8em;
  font-size: 20px;
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

.sub-item-wrap {
  position: relative;
}
.sub-item-wrap.has-flyout.is-flyout-open {
  z-index: 20;
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
.sub-item:focus-visible,
.sub-item-wrap.is-flyout-open > .sub-item {
  background: #f0f4fa;
  border-left-color: var(--card-accent);
}
.sub-item.is-group {
  cursor: default;
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
.sub-item:hover .sub-name,
.sub-flyout__item:hover .sub-name {
  color: #1e3a5f;
}

.sub-badge {
  flex-shrink: 0;
  font-size: 11px;
  line-height: 1;
  padding: 3px 6px;
  border-radius: 4px;
  color: var(--card-accent);
  background: color-mix(in srgb, var(--card-accent) 14%, white);
}

.sub-arrow,
.sub-chevron {
  margin-left: auto;
  font-size: 18px;
  color: #bbc4ce;
  opacity: 0;
  transition: opacity 150ms ease;
  flex-shrink: 0;
}
.sub-chevron {
  opacity: 0.55;
  font-size: 22px;
  line-height: 1;
}
.sub-item:hover .sub-arrow,
.sub-item-wrap.is-flyout-open .sub-chevron,
.sub-flyout__item:hover .sub-arrow {
  opacity: 1;
}

.sub-flyout {
  position: absolute;
  left: calc(100% - 4px);
  top: 0;
  min-width: 240px;
  padding: 6px 0;
  background: #fff;
  border: 1px solid var(--card-border);
  border-radius: 10px;
  box-shadow: 0 10px 28px rgba(20, 40, 70, 0.16);
  z-index: 30;
}
.sub-flyout__item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 18px;
  cursor: pointer;
  border: none;
  border-left: 3px solid transparent;
  background: transparent;
  text-align: left;
}
.sub-flyout__item:hover {
  background: #f0f4fa;
  border-left-color: var(--card-accent);
}
.sub-flyout__item .sub-arrow {
  opacity: 0;
}
.sub-flyout__item:hover .sub-arrow {
  opacity: 1;
}

.drawer-empty {
  padding: 16px 18px;
  font-size: 13px;
  color: #8a96a5;
  text-align: center;
  line-height: 1.5;
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
    height: 202px;
  }
  .drawer-card.is-open {
    flex: 0 0 auto;
    width: 260px;
    height: auto;
  }
}
</style>
