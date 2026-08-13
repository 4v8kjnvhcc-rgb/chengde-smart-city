<script setup lang="ts">
defineOptions({ name: 'DashboardView' })

import { computed, onActivated, onMounted, ref } from 'vue'
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
import { ElMessage } from 'element-plus'
import { openAssessmentWithPortalSso, assessmentExternalUrl } from '@/views/exchange/application/application-nav'
import {
  loadPortalNav,
  peekPortalNavCache,
  type PortalNavNode,
} from '@/utils/portal-nav-cache'

type DisplayCard =
  | { source: 'nav'; key: string; title: string; themePath: string; direct: boolean; nav: PortalNavNode }
  | { source: 'menu'; key: string; title: string; themePath: string; direct: boolean; menu: MenuNode }

type CardItem =
  | { kind: 'nav'; key: string; title: string; node: PortalNavNode; children?: CardItem[] }
  | { kind: 'menu'; key: string; title: string; node: MenuNode; children?: CardItem[] }

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

const bootCache = peekPortalNavCache()
const navPlatforms = ref<PortalNavNode[]>(bootCache ? bootCache.slice() : [])
const navLoadError = ref(false)
/** 路由守卫已预取时首屏直接出卡；保活返回时组件不销毁 */
const navReady = ref(bootCache !== null)

/** 平台管理：一级目录快捷入口，点击直达统一用户管理系统 */
function isPlatformMgmtMenu(n: MenuNode): boolean {
  return n.path === '/system' || n.menuName === '平台管理'
}

function isPlatformMgmtNav(n: PortalNavNode): boolean {
  const theme = (n.themeKey || '').trim()
  const menuPath = (n.menuPath || '').trim()
  return n.name === '平台管理' || theme === '/system' || menuPath === '/system'
}

function hasPlatformMgmtAccess(): boolean {
  if (auth.isSystemAdmin) return true
  if ((auth.permissions || []).includes('hub:system:platform')) return true
  return getAuthorizedPlatforms(auth.menus).some((n) => isPlatformMgmtMenu(n))
}

/** 合成「平台管理」菜单节点（门户卡兜底，不依赖树结构） */
function syntheticPlatformMgmtMenu(): MenuNode {
  return {
    id: 19,
    parentId: 1,
    menuName: '平台管理',
    menuType: 1,
    path: '/system',
    component: '',
    permission: 'hub:system:platform',
    icon: '',
    mCode: '',
    integrationType: 'self',
    visible: 1,
    children: [],
  }
}

/** 一级「平台管理」；集成运维已迁入通用支撑，不再作为门户卡片 */
const menuExtraPlatforms = computed(() =>
  getAuthorizedPlatforms(auth.menus).filter((n) => isPlatformMgmtMenu(n)),
)

const displayCards = computed<DisplayCard[]>(() => {
  if (!navReady.value) return []
  const fromNav: DisplayCard[] = navPlatforms.value.map((n) => ({
    source: 'nav' as const,
    key: `nav-${n.id}`,
    title: n.name,
    themePath: isPlatformMgmtNav(n) ? '/system' : (n.themeKey || '/exchange'),
    // 门户配置若挂了平台管理，同样直达，不展开空下拉
    direct: isPlatformMgmtNav(n),
    nav: n,
  }))
  const navHasPlatformMgmt = fromNav.some((c) => c.title === '平台管理' || c.themePath === '/system')
  const fromMenu: DisplayCard[] = menuExtraPlatforms.value
    .filter(() => !navHasPlatformMgmt)
    .map((n) => ({
      source: 'menu' as const,
      key: `menu-${n.id}`,
      title: '平台管理',
      themePath: '/system',
      direct: true,
      menu: n,
    }))
  const cards = [...fromNav, ...fromMenu]
  // 有权限但导航/菜单树都没带出时，仍出第五张卡（对齐门户五卡）
  if (!cards.some((c) => c.title === '平台管理' || c.themePath === '/system') && hasPlatformMgmtAccess()) {
    cards.push({
      source: 'menu',
      key: 'menu-platform-mgmt',
      title: '平台管理',
      themePath: '/system',
      direct: true,
      menu: syntheticPlatformMgmtMenu(),
    })
  }
  return cards
})

const activeIndex = ref<number | null>(null)
const hoverGroupKey = ref<string | null>(null)

function getTheme(path: string) {
  return platformThemes[path] || platformThemes['/exchange']
}

function navigableTarget(node: PortalNavNode): string | null {
  const t = (node.url || node.menuPath || '').trim()
  return t || null
}

function getNavCardItems(platform: PortalNavNode): CardItem[] {
  const items: CardItem[] = []
  for (const sub of platform.children || []) {
    const systems = (sub.children || []).filter((c) => !!navigableTarget(c))
    if (systems.length > 0) {
      items.push({
        kind: 'nav',
        key: `n-${sub.id}`,
        title: sub.name,
        node: sub,
        children: systems.map((leaf) => ({
          kind: 'nav' as const,
          key: `n-${leaf.id}`,
          title: leaf.name,
          node: leaf,
        })),
      })
      continue
    }
    if (navigableTarget(sub)) {
      items.push({
        kind: 'nav',
        key: `n-${sub.id}`,
        title: sub.name,
        node: sub,
      })
    }
  }
  return items
}

function getMenuCardItems(node: MenuNode): CardItem[] {
  return visibleMenuChildren(node).map((child) => {
    const grandchildren = visibleMenuChildren(child)
    if (grandchildren.length > 0) {
      return {
        kind: 'menu' as const,
        key: `m-${child.id}`,
        title: child.menuName,
        node: child,
        children: grandchildren.map((leaf) => ({
          kind: 'menu' as const,
          key: `m-${leaf.id}`,
          title: leaf.menuName,
          node: leaf,
        })),
      }
    }
    return {
      kind: 'menu' as const,
      key: `m-${child.id}`,
      title: child.menuName,
      node: child,
    }
  })
}

function getCardItems(card: DisplayCard): CardItem[] {
  return card.source === 'nav' ? getNavCardItems(card.nav) : getMenuCardItems(card.menu)
}

function itemHasChildren(item: CardItem): item is CardItem & { children: CardItem[] } {
  return !!item.children?.length
}

function toggleDrawer(index: number) {
  if (activeIndex.value === index) {
    activeIndex.value = null
  } else {
    activeIndex.value = index
  }
  hoverGroupKey.value = null
}

function enterPlatformMgmt() {
  activeIndex.value = null
  hoverGroupKey.value = null
  // 平台管理目录 → 统一用户管理系统
  router.push({ path: '/analytics/support', query: { tab: 'users.org' } })
}

function onCardHeaderClick(card: DisplayCard, index: number) {
  if (card.direct) {
    enterPlatformMgmt()
    return
  }
  if (card.source === 'menu' && isPlatformMgmtMenu(card.menu)) {
    enterPlatformMgmt()
    return
  }
  if (card.source === 'nav' && isPlatformMgmtNav(card.nav)) {
    enterPlatformMgmt()
    return
  }
  toggleDrawer(index)
}

function isAssessmentMenu(node: MenuNode): boolean {
  const p = node.path || ''
  return p.includes('/application/assessment') || node.menuName === '考核评估系统'
}

function isAssessmentNav(node: PortalNavNode): boolean {
  const t = navigableTarget(node) || ''
  return t.includes('/application/assessment')
    || t.includes('/assessment/')
    || node.name === '考核评估系统'
}

function wantsPortalTicketSso(node: PortalNavNode): boolean {
  return (node.ssoMode || 'none') === 'portal_ticket'
}

/** 站内地址拆成 path + query，保证 system=collect/register 生效 */
function pushInternalTarget(target: string) {
  const t = target.trim()
  if (t.startsWith('http://') || t.startsWith('https://')) {
    window.open(t, '_blank', 'noopener,noreferrer')
    return
  }
  const qIdx = t.indexOf('?')
  if (qIdx < 0) {
    router.push(t)
    return
  }
  const path = t.slice(0, qIdx) || '/'
  const query: Record<string, string> = {}
  new URLSearchParams(t.slice(qIdx + 1)).forEach((v, k) => {
    if (k) query[k] = v
  })
  router.push({ path, query })
}

async function enterMenuNode(node: MenuNode) {
  if (isAssessmentMenu(node)) {
    const landing = assessmentExternalUrl() || 'http://127.0.0.1:18081/sxev/index'
    const r = await openAssessmentWithPortalSso(landing)
    if (r.ok) {
      ElMessage.success('已进入考核评估系统')
      return
    }
    ElMessage.warning(r.message || '单点登录失败')
    return
  }
  if (node.path && node.menuType !== 1) {
    pushInternalTarget(node.path)
    return
  }
  const target = firstNavPath(node)
  if (target) pushInternalTarget(target)
}

async function enterNavNode(node: PortalNavNode) {
  const target = navigableTarget(node)
  if (wantsPortalTicketSso(node) || isAssessmentNav(node)) {
    const landing = (target && (target.startsWith('http://') || target.startsWith('https://')))
      ? target
      : ''
    if (landing || wantsPortalTicketSso(node)) {
      const r = await openAssessmentWithPortalSso(landing)
      if (r.ok) {
        ElMessage.success('已进入考核评估系统')
        return
      }
      if (wantsPortalTicketSso(node)) {
        ElMessage.error(r.message || '单点登录失败')
        return
      }
      // 兼容：未配外链时继续站内
    }
  }
  if (!target) return
  const mode = node.openMode || 'route'
  if (mode === 'new_tab' || target.startsWith('http://') || target.startsWith('https://')) {
    window.open(target, '_blank', 'noopener,noreferrer')
    return
  }
  pushInternalTarget(target)
}

/** 取消中间选卡页：总览点击只展开飞出，由子入口直达（勿再进落地页） */
const SKIP_LANDING_TITLES = new Set([
  '大数据归集平台',
  '应用平台',
  '应用分析门户',
  '通用支撑平台',
  '业务支撑平台',
])

function onCardItemClick(item: CardItem) {
  if (itemHasChildren(item)) {
    // 有子入口的平台：不再跳中间选卡页，只展开飞出
    if (SKIP_LANDING_TITLES.has(item.title)) {
      hoverGroupKey.value = hoverGroupKey.value === item.key ? null : item.key
      return
    }
    if (item.kind === 'nav' && navigableTarget(item.node)) {
      hoverGroupKey.value = null
      void enterNavNode(item.node)
      return
    }
    hoverGroupKey.value = hoverGroupKey.value === item.key ? null : item.key
    return
  }
  if (item.kind === 'menu') {
    enterMenuNode(item.node)
  } else {
    enterNavNode(item.node)
  }
}

function onFlyoutChildClick(item: CardItem) {
  hoverGroupKey.value = null
  onCardItemClick(item)
}

async function refreshNav(force = false) {
  try {
    const list = await loadPortalNav(force)
    navPlatforms.value = list
    navLoadError.value = false
  } catch {
    if (!peekPortalNavCache()) {
      navPlatforms.value = []
      navLoadError.value = true
    }
  } finally {
    navReady.value = true
  }
}

onMounted(() => {
  void refreshNav(false)
})

/** keep-alive 再次进入：界面已是上次完整状态，仅后台对齐最新门户树 */
onActivated(() => {
  const cached = peekPortalNavCache()
  if (cached) {
    navPlatforms.value = cached.slice()
    navReady.value = true
  }
  void loadPortalNav(true)
    .then((list) => {
      navPlatforms.value = list
      navLoadError.value = false
    })
    .catch(() => {
      /* 保留现画面 */
    })
})
</script>

<template>
  <div class="portal-drawer">
    <div class="portal-drawer__header">
      <h1 class="portal-drawer__title">承德高新区智慧城市数据中台</h1>
    </div>

    <!-- 冷启动无缓存：骨架占位，避免地球背景上空无一物像白屏 -->
    <div v-if="!navReady" class="cards-row" aria-busy="true">
      <div v-for="i in 4" :key="i" class="drawer-card drawer-card--skeleton">
        <div class="card-header card-header--skeleton">
          <div class="skel-icon" />
          <div class="skel-title" />
        </div>
      </div>
    </div>

    <div v-else-if="displayCards.length" class="cards-row">
      <div
        v-for="(card, index) in displayCards"
        :key="card.key"
        class="drawer-card"
        :class="{
          'is-open': activeIndex === index,
          // 直达卡（平台管理）不灰化，避免看起来像「没了」
          'is-dim': !card.direct && activeIndex !== null && activeIndex !== index,
          'is-direct': card.direct,
        }"
        :style="{
          '--card-border': getTheme(card.themePath).border,
          '--card-header-bg': getTheme(card.themePath).headerBg,
          '--card-icon-bg': getTheme(card.themePath).iconBg,
          '--card-icon-color': getTheme(card.themePath).iconColor,
          '--card-title-color': getTheme(card.themePath).titleColor,
          '--card-accent': getTheme(card.themePath).accent,
          '--card-dot-color': getTheme(card.themePath).dotColor,
        }"
      >
        <button
          type="button"
          class="card-header"
          :aria-expanded="card.direct ? undefined : activeIndex === index"
          @click="onCardHeaderClick(card, index)"
        >
          <div class="card-icon-wrap">
            <el-icon :size="44">
              <component :is="platformIcons[card.themePath] || Connection" />
            </el-icon>
          </div>
          <div class="card-title">{{ card.title }}</div>
          <!-- 直达卡也保留色条，避免平台管理看起来像失效 -->
          <div class="card-arrow" :class="{ 'is-direct-arrow': card.direct }" />
        </button>

        <div v-if="!card.direct" class="drawer-body">
          <div class="drawer-body-inner">
            <div
              v-for="item in getCardItems(card)"
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
            <div v-if="!getCardItems(card).length" class="drawer-empty">
              {{
                card.source === 'nav'
                  ? navLoadError
                    ? '门户配置加载失败，请稍后重试或联系管理员'
                    : '暂无入口，请在「通用支撑平台 → 门户配置」中维护'
                  : '暂无入口'
              }}
            </div>
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
.drawer-card--skeleton {
  border-color: rgba(181, 212, 244, 0.55);
  background: rgba(255, 255, 255, 0.72);
  height: 202px;
  pointer-events: none;
}
.card-header--skeleton {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  height: 100%;
  padding: 24px;
}
.skel-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  background: linear-gradient(90deg, #e8eef8 25%, #f5f8fd 50%, #e8eef8 75%);
  background-size: 200% 100%;
  animation: skel-shine 1.2s ease-in-out infinite;
}
.skel-title {
  width: 120px;
  height: 16px;
  border-radius: 4px;
  background: linear-gradient(90deg, #e8eef8 25%, #f5f8fd 50%, #e8eef8 75%);
  background-size: 200% 100%;
  animation: skel-shine 1.2s ease-in-out infinite;
}
@keyframes skel-shine {
  0% { background-position: 100% 0; }
  100% { background-position: -100% 0; }
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
.card-arrow.is-direct-arrow {
  width: 54px;
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
