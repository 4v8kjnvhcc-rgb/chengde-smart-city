<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import PageHeader from '@/components/common/PageHeader.vue'
import HubSideLayout from '@/components/common/HubSideLayout.vue'
import {
  DocumentChecked,
  CircleCheck,
  Warning,
  FolderOpened,
  Sort,
  DocumentCopy,
} from '@element-plus/icons-vue'
import { SUPPLY_MAIN_SECTIONS, SUPPLY_SIDE_NAV, SUPPLY_SYSTEM_SECTIONS } from './application-nav'
import type { HubNavItem } from '@/components/common/HubSideLayout.vue'
import { useSupplyRole } from './supply-role'

const router = useRouter()
const route = useRoute()
const SupplyDemandView = defineAsyncComponent(() => import('./SupplyDemandView.vue'))
const SupplyConfigView = defineAsyncComponent(() => import('@/views/system/SupplyConfigView.vue'))
const MatterManageView = defineAsyncComponent(() => import('./MatterManageView.vue'))
const { canAccessSection, isPlatformAdmin, isDemandDept, isProviderDept } = useSupplyRole()

const section = ref('home')
const kpi = ref({ preAudit: 0, confirm: 0, objection: 0, catalog: 0, supplyManifest: 0, objectionTotal: 0 })

const navItems = computed(() => {
  const filterNav = (items: typeof SUPPLY_SIDE_NAV): HubNavItem[] =>
    items
      .map((item) => {
        if ('children' in item && item.children) {
          const children = item.children.filter((c) => canAccessSection(c.key))
          if (!children.length || !canAccessSection(item.key)) return null
          return { ...item, children } as HubNavItem
        }
        if (!canAccessSection(item.key)) return null
        return item as HubNavItem
      })
      .filter(Boolean) as HubNavItem[]
  return filterNav(SUPPLY_SIDE_NAV as unknown as typeof SUPPLY_SIDE_NAV)
})

const SECTION_LABEL: Record<string, string> = {
  home: '首页',
  demand: '数据需求管理',
  analysis: '数据需求分析',
  confirm: '数据需求确认',
  supply: '数据供给查看',
  supervise: '业务督办',
  'manifest-center': '清单中心',
  'supply-config': '供需配置',
  'matter-manage': '事项管理',
}

const leafKeys = new Set<string>([
  ...SUPPLY_MAIN_SECTIONS.map((s) => s.key),
  ...SUPPLY_SYSTEM_SECTIONS,
])

const pageTitle = computed(
  () => `大数据归集平台 · 数据供需对接系统 · ${SECTION_LABEL[section.value] || '首页'}`,
)

const FLOW_STEPS = computed(() => {
  const all = [
    { title: '需求填报', tone: 'blue', go: 'demand', need: () => isDemandDept.value },
    { title: '需求预审', tone: 'blue', go: 'analysis', need: () => isPlatformAdmin.value },
    { title: '需求确认', tone: 'blue', go: 'confirm', need: () => isProviderDept.value },
    { title: '生成共享任务', tone: 'green', go: 'confirm', need: () => isProviderDept.value },
    { title: '供给查看', tone: 'green', go: 'supply', need: () => isProviderDept.value },
    { title: '清单监控', tone: 'amber', go: 'manifest-center', need: () => true },
  ]
  return all.filter((s) => s.need())
})

function syncFromRoute() {
  const s = String(route.query.sdSection || route.query.section || 'home')
  const next = leafKeys.has(s) ? s : 'home'
  section.value = canAccessSection(next) ? next : 'home'
}

function goSection(key: string, extra: Record<string, string> = {}) {
  // 父级「系统管理」不可选中，仅展开子项
  if (key === 'system' || !leafKeys.has(key)) return
  if (!canAccessSection(key)) return
  section.value = key
  router.replace({
    path: '/exchange/application/supply',
    query: { ...route.query, section: key, sdSection: key, ...extra },
  })
}

async function loadHomeKpi() {
  try {
    const [dm, obj, cat, man] = await Promise.all([
      api.get('/exchange/supply/demands'),
      api.get('/exchange/supply/objections'),
      api.get('/exchange/supply/catalog-manifest', { params: { scope: 'published' } }),
      api.get('/exchange/supply/manifests'),
    ])
    const demands = (dm.data || []) as { status?: string; stage?: string }[]
    const objections = (obj.data || []) as { status?: string }[]
    kpi.value = {
      preAudit: demands.filter((d) =>
        ['SUBMITTED', 'PRE_AUDITING', 'ANALYZING'].includes(String(d.status || ''))
        || d.stage === 'PRE_AUDIT',
      ).length,
      confirm: demands.filter((d) =>
        ['DISPATCHED', 'SUPERVISING', 'CORRECTION'].includes(String(d.status || ''))
        || d.stage === 'AUDIT',
      ).length,
      objection: objections.filter((o) => o.status !== 'CLOSED').length,
      catalog: (cat.data || []).length,
      supplyManifest: (man.data || []).length,
      objectionTotal: objections.length,
    }
  } catch {
    // ignore
  }
}

watch(() => [route.query.section, route.query.sdSection], () => {
  syncFromRoute()
  if (section.value === 'home') void loadHomeKpi()
})

onMounted(() => {
  syncFromRoute()
  if (section.value === 'home') void loadHomeKpi()
})
</script>

<template>
  <div class="supply-hub">
    <PageHeader :title="pageTitle">
      <button type="button" class="hub-back" @click="router.push('/dashboard')">
        返回总览
      </button>
    </PageHeader>

    <HubSideLayout :model-value="section" :items="navItems" @update:model-value="goSection">
      <template v-if="section === 'home'">
        <div class="sd-home">
          <div class="flow-row">
            <button
              v-for="(step, idx) in FLOW_STEPS"
              :key="step.title"
              type="button"
              class="flow-step-wrap"
              @click="goSection(step.go)"
            >
              <div class="flow-step" :class="'tone-' + step.tone">
                <div class="flow-step__ico"><em>{{ idx + 1 }}</em></div>
                <div class="flow-step__title">{{ step.title }}</div>
              </div>
              <span v-if="idx < FLOW_STEPS.length - 1" class="flow-chev" @click.stop>›</span>
            </button>
          </div>

          <div class="kpi-row">
            <button v-if="isPlatformAdmin" type="button" class="kpi-card tone-blue" @click="goSection('analysis')">
              <div class="kpi-card__icon"><el-icon :size="22"><DocumentChecked /></el-icon></div>
              <div class="kpi-card__body">
                <div class="kpi-card__lab">待预审</div>
                <div class="kpi-card__num">{{ kpi.preAudit }}</div>
              </div>
            </button>
            <button v-if="isProviderDept" type="button" class="kpi-card tone-green" @click="goSection('confirm')">
              <div class="kpi-card__icon"><el-icon :size="22"><CircleCheck /></el-icon></div>
              <div class="kpi-card__body">
                <div class="kpi-card__lab">待确认</div>
                <div class="kpi-card__num">{{ kpi.confirm }}</div>
              </div>
            </button>
            <button type="button" class="kpi-card tone-amber" @click="goSection('manifest-center', { listGroup: '异议清单' })">
              <div class="kpi-card__icon"><el-icon :size="22"><Warning /></el-icon></div>
              <div class="kpi-card__body">
                <div class="kpi-card__lab">异议</div>
                <div class="kpi-card__num">{{ kpi.objection }}</div>
              </div>
            </button>
          </div>

          <div class="list-entry-row">
            <div class="list-entry tone-blue">
              <div class="list-entry__head">
                <span class="list-entry__ico"><el-icon :size="18"><FolderOpened /></el-icon></span>
                <h3>目录清单</h3>
              </div>
              <div class="list-entry__metric"><b>{{ kpi.catalog }}</b><span>条目录</span></div>
              <button type="button" class="list-entry__btn" @click="goSection('manifest-center', { listGroup: '目录清单' })">查看详情</button>
            </div>
            <div class="list-entry tone-green">
              <div class="list-entry__head">
                <span class="list-entry__ico"><el-icon :size="18"><Sort /></el-icon></span>
                <h3>供需清单</h3>
              </div>
              <div class="list-entry__metric"><b>{{ kpi.supplyManifest }}</b><span>条清单</span></div>
              <button type="button" class="list-entry__btn" @click="goSection('manifest-center', { listGroup: '供需清单' })">查看详情</button>
            </div>
            <div class="list-entry tone-amber">
              <div class="list-entry__head">
                <span class="list-entry__ico"><el-icon :size="18"><DocumentCopy /></el-icon></span>
                <h3>异议清单</h3>
              </div>
              <div class="list-entry__metric"><b>{{ kpi.objectionTotal }}</b><span>条清单</span></div>
              <button type="button" class="list-entry__btn" @click="goSection('manifest-center', { listGroup: '异议清单' })">查看详情</button>
            </div>
          </div>
        </div>
      </template>

      <div v-else-if="section === 'supply-config'" class="supply-config-pane">
        <SupplyConfigView embedded />
      </div>

      <div v-else-if="section === 'matter-manage'" class="supply-config-pane">
        <MatterManageView />
      </div>

      <SupplyDemandView v-else mode="front" />
    </HubSideLayout>
  </div>
</template>

<style scoped>
.supply-hub {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  box-sizing: border-box;
  padding: 20px;
  background: var(--portal-bg, #f5f7fa);
}
.supply-hub :deep(.page-header__title) {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  letter-spacing: 0.02em;
}
.supply-hub :deep(.page-header__desc) {
  font-size: 13px;
  color: #6b7280;
}
.supply-hub :deep(.hub-side-layout) {
  /* flushMain 无全局顶栏：扣本页 padding + PageHeader 高度 */
  height: calc(100vh - 40px - 72px);
  max-height: calc(100vh - 40px - 72px);
  flex: 1;
  min-height: 320px;
}
.supply-config-pane {
  height: 100%;
  overflow: auto;
  padding: 4px 2px 12px;
}
.hub-back {
  appearance: none;
  border: 1px solid #c5d4eb;
  background: #f5f8fd;
  color: #1d4f91;
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.02em;
  padding: 0 14px;
  height: 32px;
  border-radius: 4px;
  cursor: pointer;
  transition: color 150ms ease, background 150ms ease, border-color 150ms ease;
}
.hub-back:hover {
  color: #0d47a1;
  background: #e8f0fb;
  border-color: #9bb8e0;
}

.sd-home { display: flex; flex-direction: column; gap: 14px; }
.flow-row { display: flex; flex-wrap: wrap; gap: 4px; align-items: stretch; }
.flow-step-wrap {
  appearance: none; border: 0; background: transparent; display: flex; align-items: center;
  gap: 4px; padding: 0; cursor: pointer; text-align: left;
}
.flow-step {
  background: #fff; border: 1px solid #e8edf5; border-radius: 10px; padding: 12px 12px 14px;
  min-width: 112px; box-shadow: 0 1px 4px rgba(15, 40, 80, .05);
  transition: border-color 120ms ease, box-shadow 120ms ease;
}
.flow-step-wrap:hover .flow-step { border-color: #91caff; box-shadow: 0 2px 8px rgba(22, 119, 255, .12); }
.flow-step__ico {
  width: 32px; height: 32px; border-radius: 50%; margin-bottom: 8px;
  display: grid; place-items: center;
}
.flow-step__ico em { font-style: normal; font-size: 13px; font-weight: 700; color: #fff; }
.flow-step.tone-blue .flow-step__ico { background: #1677ff; }
.flow-step.tone-green .flow-step__ico { background: #2e7d32; }
.flow-step.tone-amber .flow-step__ico { background: #ef6c00; }
.flow-step__title { font-size: 13px; font-weight: 700; color: #1f2d3d; }
.flow-chev { color: #c0c4cc; font-size: 18px; font-weight: 300; padding: 0 2px; }

.kpi-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.kpi-card {
  appearance: none; border: 1px solid #e8edf5; background: #fff; border-radius: 10px;
  padding: 16px 18px; text-align: left; cursor: pointer;
  box-shadow: 0 1px 4px rgba(15, 40, 80, .05);
  display: flex; gap: 14px; align-items: center;
}
.kpi-card__icon {
  width: 48px; height: 48px; border-radius: 50%; flex-shrink: 0;
  display: grid; place-items: center;
}
.kpi-card.tone-blue .kpi-card__icon { background-color: #e8f3ff; color: #1677ff; }
.kpi-card.tone-green .kpi-card__icon { background-color: #e8f8ef; color: #2e7d32; }
.kpi-card.tone-amber .kpi-card__icon { background-color: #fff4e5; color: #ef6c00; }
.kpi-card__num { font-size: 30px; font-weight: 700; line-height: 1.1; }
.kpi-card.tone-blue .kpi-card__num { color: #1677ff; }
.kpi-card.tone-green .kpi-card__num { color: #2e7d32; }
.kpi-card.tone-amber .kpi-card__num { color: #ef6c00; }
.kpi-card__lab { font-size: 14px; font-weight: 600; color: #303133; }

.list-entry-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.list-entry {
  background: #fff; border: 1px solid #e8edf5; border-radius: 10px; padding: 16px 18px;
  box-shadow: 0 1px 4px rgba(15, 40, 80, .05);
}
.list-entry__head { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.list-entry__ico {
  width: 36px; height: 36px; border-radius: 8px; flex-shrink: 0;
  display: grid; place-items: center;
}
.list-entry.tone-blue .list-entry__ico { background: #e8f3ff; color: #1677ff; }
.list-entry.tone-green .list-entry__ico { background: #e8f8ef; color: #2e7d32; }
.list-entry.tone-amber .list-entry__ico { background: #fff4e5; color: #ef6c00; }
.list-entry h3 { margin: 0; font-size: 16px; }
.list-entry__metric { margin: 12px 0; }
.list-entry__metric b { font-size: 28px; font-weight: 700; margin-right: 6px; }
.list-entry.tone-blue .list-entry__metric b,
.list-entry.tone-blue h3 { color: #1677ff; }
.list-entry.tone-green .list-entry__metric b,
.list-entry.tone-green h3 { color: #2e7d32; }
.list-entry.tone-amber .list-entry__metric b,
.list-entry.tone-amber h3 { color: #ef6c00; }
.list-entry__metric span { font-size: 12px; color: #909399; }
.list-entry__btn {
  appearance: none; background: #fff; border-radius: 4px; padding: 4px 12px;
  font-size: 12px; cursor: pointer;
}
.list-entry.tone-blue .list-entry__btn { border: 1px solid #1677ff; color: #1677ff; }
.list-entry.tone-green .list-entry__btn { border: 1px solid #2e7d32; color: #2e7d32; }
.list-entry.tone-amber .list-entry__btn { border: 1px solid #ef6c00; color: #ef6c00; }

@media (max-width: 1100px) {
  .kpi-row, .list-entry-row { grid-template-columns: 1fr; }
}
</style>
