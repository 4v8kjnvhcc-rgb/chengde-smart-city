<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import UserAccountMenu from '@/components/layout/UserAccountMenu.vue'
import { SUPPLY_MAIN_SECTIONS } from './application-nav'

const router = useRouter()
const route = useRoute()
const SupplyDemandView = defineAsyncComponent(() => import('./SupplyDemandView.vue'))

const section = ref('home')
const collapsed = ref(false)
const kpi = ref({ preAudit: 0, confirm: 0, objection: 0, catalog: 0, supplyManifest: 0, objectionTotal: 0 })

const navItems = SUPPLY_MAIN_SECTIONS

const SECTION_BANNER: Record<string, string> = {
  home: '数据供需对接系统',
  demand: '数据需求管理',
  analysis: '数据需求分析',
  confirm: '数据需求确认',
  supply: '数据供给查看',
  supervise: '业务督办',
  'manifest-center': '清单中心',
}

const bannerTitle = computed(() => SECTION_BANNER[section.value] || SECTION_BANNER.home)

const FLOW_STEPS = [
  { title: '需求填报', tone: 'blue', go: 'demand' },
  { title: '需求预审', tone: 'blue', go: 'analysis' },
  { title: '需求审核', tone: 'blue', go: 'confirm' },
  { title: '生成数据责任', tone: 'green', go: 'confirm' },
  { title: '供给查看', tone: 'green', go: 'supply' },
  { title: '任务执行', tone: 'amber', go: 'supply' },
  { title: '清单监控', tone: 'amber', go: 'manifest-center' },
]

function syncFromRoute() {
  const s = String(route.query.sdSection || route.query.section || 'home')
  section.value = navItems.some((n) => n.key === s) ? s : 'home'
}

function goSection(key: string, extra: Record<string, string> = {}) {
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
  <div class="sd-shell" :class="{ 'is-collapsed': collapsed }">
    <aside class="sd-side">
      <div class="sd-brand">
        <span class="sd-brand__mark">供</span>
        <div v-if="!collapsed" class="sd-brand__text">
          <b>数据供需对接系统</b>
        </div>
      </div>
      <nav class="sd-nav" aria-label="供需导航">
        <button
          v-for="item in navItems"
          :key="item.key"
          type="button"
          class="sd-nav__item"
          :class="{ 'is-on': section === item.key }"
          :title="item.label"
          @click="goSection(item.key)"
        >
          <span class="sd-nav__ico" :data-k="item.key" />
          <span v-if="!collapsed" class="sd-nav__lab">{{ item.label }}</span>
        </button>
      </nav>
      <button type="button" class="sd-collapse" @click="collapsed = !collapsed">
        <span class="sd-collapse__chev" :class="{ 'is-flip': collapsed }" />
        <span v-if="!collapsed">收起</span>
        <span v-else>展开</span>
      </button>
    </aside>

    <div class="sd-main">
      <section class="sd-banner">
        <div class="sd-banner__copy">
          <h1>{{ bannerTitle }}</h1>
        </div>
        <div class="sd-banner__actions">
          <button type="button" class="sd-banner__btn sd-banner__btn--ghost" @click="router.push('/dashboard')">返回总览</button>
          <UserAccountMenu tone="onDark" />
        </div>
        <div class="sd-banner__art" aria-hidden="true" />
      </section>

      <main class="sd-body">
        <template v-if="section === 'home'">
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
            <button type="button" class="kpi-card tone-blue" @click="goSection('analysis')">
              <div class="kpi-card__icon" />
              <div class="kpi-card__body">
                <div class="kpi-card__lab">待预审</div>
                <div class="kpi-card__num">{{ kpi.preAudit }}</div>
              </div>
            </button>
            <button type="button" class="kpi-card tone-green" @click="goSection('confirm')">
              <div class="kpi-card__icon" />
              <div class="kpi-card__body">
                <div class="kpi-card__lab">待确认</div>
                <div class="kpi-card__num">{{ kpi.confirm }}</div>
              </div>
            </button>
            <button type="button" class="kpi-card tone-amber" @click="goSection('manifest-center', { listGroup: '异议清单' })">
              <div class="kpi-card__icon" />
              <div class="kpi-card__body">
                <div class="kpi-card__lab">异议</div>
                <div class="kpi-card__num">{{ kpi.objection }}</div>
              </div>
            </button>
          </div>

          <div class="list-entry-row">
            <div class="list-entry tone-blue">
              <div class="list-entry__head">
                <span class="list-entry__ico" />
                <h3>目录清单</h3>
              </div>
              <div class="list-entry__metric"><b>{{ kpi.catalog }}</b><span>条目录</span></div>
              <button type="button" class="list-entry__btn" @click="goSection('manifest-center', { listGroup: '目录清单' })">查看详情</button>
            </div>
            <div class="list-entry tone-green">
              <div class="list-entry__head">
                <span class="list-entry__ico" />
                <h3>供需清单</h3>
              </div>
              <div class="list-entry__metric"><b>{{ kpi.supplyManifest }}</b><span>条清单</span></div>
              <button type="button" class="list-entry__btn" @click="goSection('manifest-center', { listGroup: '供需清单' })">查看详情</button>
            </div>
            <div class="list-entry tone-amber">
              <div class="list-entry__head">
                <span class="list-entry__ico" />
                <h3>异议清单</h3>
              </div>
              <div class="list-entry__metric"><b>{{ kpi.objectionTotal }}</b><span>条清单</span></div>
              <button type="button" class="list-entry__btn" @click="goSection('manifest-center', { listGroup: '异议清单' })">查看详情</button>
            </div>
          </div>
        </template>

        <SupplyDemandView v-else mode="front" />
      </main>

    </div>
  </div>
</template>

<style scoped>
.sd-shell { display: flex; min-height: 100vh; background: #f5f7fa; }
.sd-side {
  width: 220px; background: #fff; border-right: 1px solid #e8edf5;
  display: flex; flex-direction: column; flex-shrink: 0; transition: width 160ms ease;
}
.sd-shell.is-collapsed .sd-side { width: 72px; }
.sd-brand {
  display: flex; align-items: center; gap: 10px; padding: 16px 14px;
  border-bottom: 1px solid #eef1f6;
}
.sd-brand__mark {
  width: 36px; height: 36px; border-radius: 50%; background: #1677ff; color: #fff;
  display: grid; place-items: center; font-weight: 700; flex-shrink: 0;
}
.sd-brand__text b { font-size: 14px; color: #1f2d3d; }
.sd-nav { flex: 1; padding: 10px 8px; display: flex; flex-direction: column; gap: 4px; overflow: auto; }
.sd-nav__item {
  appearance: none; border: 0; background: transparent; display: flex; align-items: center;
  gap: 10px; padding: 10px 12px; border-radius: 8px; color: #606266; cursor: pointer;
  font-size: 13px; text-align: left;
}
.sd-nav__item:hover { background: #f5f9ff; color: #1677ff; }
.sd-nav__item.is-on { background: #e8f3ff; color: #1677ff; font-weight: 600; }
.sd-nav__ico {
  width: 18px; height: 18px; border-radius: 4px; background: #d9e8ff; flex-shrink: 0;
  position: relative;
}
.sd-nav__item.is-on .sd-nav__ico { background: #1677ff; }
.sd-nav__ico::after {
  content: ''; position: absolute; inset: 4px; border-radius: 2px; background: rgba(255,255,255,.55);
}
.sd-nav__lab { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.sd-collapse {
  appearance: none; border: 0; border-top: 1px solid #eef1f6; background: #fff;
  padding: 12px; color: #909399; cursor: pointer; font-size: 12px;
  display: flex; align-items: center; justify-content: center; gap: 6px;
}
.sd-collapse__chev {
  width: 0; height: 0; border-top: 5px solid transparent; border-bottom: 5px solid transparent;
  border-right: 6px solid #909399;
}
.sd-collapse__chev.is-flip { transform: rotate(180deg); }

.sd-main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.sd-banner {
  position: relative; background: linear-gradient(115deg, #0a4ea8 0%, #1677ff 48%, #0d47a1 100%);
  color: #fff; padding: 20px 24px; overflow: hidden; min-height: 100px;
  display: flex; align-items: center; justify-content: space-between; gap: 16px;
}
.sd-banner__copy { position: relative; z-index: 1; }
.sd-banner__copy h1 { margin: 0; font-size: 24px; font-weight: 700; letter-spacing: .5px; }
.sd-banner__actions {
  position: relative; z-index: 1; display: flex; align-items: center; gap: 8px; flex-shrink: 0;
}
.sd-banner__btn {
  appearance: none; border: 1px solid rgba(255,255,255,.4); background: rgba(255,255,255,.14);
  color: #fff; height: 30px; padding: 0 12px; border-radius: 4px; cursor: pointer; font-size: 12px;
}
.sd-banner__btn--ghost { background: transparent; }
.sd-banner__art {
  position: absolute; right: 180px; bottom: 0; width: 260px; height: 90px; opacity: .28;
  background:
    linear-gradient(90deg, transparent, rgba(255,255,255,.4)),
    repeating-linear-gradient(90deg, transparent 0 16px, rgba(255,255,255,.55) 16px 18px);
  clip-path: polygon(0 100%, 6% 58%, 14% 72%, 24% 42%, 36% 64%, 48% 30%, 60% 52%, 72% 22%, 84% 48%, 94% 18%, 100% 36%, 100% 100%);
  pointer-events: none;
}
.sd-body { flex: 1; padding: 16px 18px 8px; }

.flow-row { display: flex; flex-wrap: wrap; gap: 4px; align-items: stretch; margin-bottom: 14px; }
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

.kpi-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 14px; }
.kpi-card {
  appearance: none; border: 1px solid #e8edf5; background: #fff; border-radius: 10px;
  padding: 16px 18px; text-align: left; cursor: pointer;
  box-shadow: 0 1px 4px rgba(15, 40, 80, .05);
  display: flex; gap: 14px; align-items: center;
}
.kpi-card__icon {
  width: 48px; height: 48px; border-radius: 50%; flex-shrink: 0;
  background: #e8f3ff center/22px no-repeat;
}
.kpi-card.tone-blue .kpi-card__icon { background-color: #e8f3ff; }
.kpi-card.tone-green .kpi-card__icon { background-color: #e8f8ef; }
.kpi-card.tone-amber .kpi-card__icon { background-color: #fff4e5; }
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
}
.list-entry.tone-blue .list-entry__ico { background: #e8f3ff; }
.list-entry.tone-green .list-entry__ico { background: #e8f8ef; }
.list-entry.tone-amber .list-entry__ico { background: #fff4e5; }
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
  .sd-banner__art { display: none; }
}
</style>
