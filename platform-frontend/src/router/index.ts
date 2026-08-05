import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { canAccessRoutePath } from '@/utils/menu'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, title: '登录' },
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '统一门户' },
        },
        {
          path: 'system/uum',
          name: 'system-uum',
          redirect: (to) => {
            const t = String(to.query.tab || 'users').toLowerCase()
            const map: Record<string, string> = {
              users: 'users.org',
              apps: 'apps.manage',
              auth: 'auth',
              services: 'services',
              integration: 'apps.integration',
              portal: 'apps.portal',
              audit: 'audit.log',
              config: 'sys.cfg.appearance',
            }
            return { path: '/analytics/support', query: { tab: map[t] || 'users.org' } }
          },
        },
        {
          path: 'system/access',
          redirect: { path: '/analytics/support', query: { tab: 'audit.access' } },
        },
        {
          path: 'system/users',
          redirect: { path: '/analytics/support', query: { tab: 'users.user' } },
        },
        {
          path: 'system/roles',
          redirect: { path: '/analytics/support', query: { tab: 'users.role' } },
        },
        {
          path: 'system/orgs',
          redirect: { path: '/analytics/support', query: { tab: 'users.org' } },
        },
        {
          path: 'system/menus',
          redirect: { path: '/analytics/support', query: { tab: 'sys.menus' } },
        },
        {
          path: 'system/portal-links',
          redirect: { path: '/analytics/support', query: { tab: 'apps.portal' } },
        },
        {
          path: 'system/tags',
          redirect: { path: '/analytics/support', query: { tab: 'sys.tags' } },
        },
        {
          path: 'system/audit',
          redirect: { path: '/analytics/support', query: { tab: 'audit.log' } },
        },
        {
          path: 'data-category',
          redirect: { path: '/exchange/ingestion', query: { system: 'collect', module: 'pipeline', section: 'step-classify' } },
        },
        {
          path: 'system/maintenance',
          redirect: (to) => {
            const p = String(to.query.pane || 'appearance').toLowerCase()
            const map: Record<string, string> = {
              appearance: 'sys.cfg.appearance',
              mail: 'sys.cfg.mail',
              security: 'audit.security',
            }
            return { path: '/analytics/support', query: { tab: map[p] || 'sys.cfg.appearance' } }
          },
        },
        {
          path: 'system/security',
          redirect: { path: '/analytics/support', query: { tab: 'audit.security' } },
        },
        {
          path: 'exchange/ingestion',
          name: 'exchange-ingestion',
          component: () => import('@/views/exchange/ingestion/IngestionHubView.vue'),
          meta: { title: '大数据归集平台', hubLayout: true, hideAppHeader: true },
        },
        {
          path: 'exchange/esb',
          name: 'exchange-esb',
          component: () => import('@/views/exchange/EsbView.vue'),
          meta: { title: '服务总线' },
        },
        {
          path: 'exchange/application',
          name: 'exchange-application',
          component: () => import('@/views/exchange/application/ApplicationHubView.vue'),
          meta: { title: '应用平台', hubLayout: true, hideAppHeader: true, flushMain: true },
        },
        {
          path: 'exchange/application/supply',
          name: 'exchange-application-supply',
          component: () => import('@/views/exchange/application/SupplyAppView.vue'),
          meta: { title: '数据供需对接系统', hubLayout: true, hideAppHeader: true, flushMain: true },
        },
        {
          path: 'exchange/application/assessment',
          name: 'exchange-application-assessment',
          component: () => import('@/views/exchange/application/AssessmentAppView.vue'),
          meta: { title: '考核评估系统', hubLayout: true, hideAppHeader: true, flushMain: true },
        },
        {
          path: 'exchange/application/stats-base',
          name: 'exchange-application-stats-base',
          component: () => import('@/views/exchange/application/StatsAppView.vue'),
          props: { domain: 'base' },
          meta: { title: '基础库统计分析应用', hubLayout: true, hideAppHeader: true, flushMain: true },
        },
        {
          path: 'exchange/application/stats-domain',
          name: 'exchange-application-stats-domain',
          component: () => import('@/views/exchange/application/StatsAppView.vue'),
          props: { domain: 'domain' },
          meta: { title: '重点领域统计分析应用', hubLayout: true, hideAppHeader: true, flushMain: true },
        },
        {
          path: 'exchange/assessment',
          redirect: { name: 'exchange-application-assessment' },
        },
        {
          path: 'exchange/application/supply-config',
          name: 'exchange-supply-config',
          component: () => import('@/views/system/SupplyConfigView.vue'),
          meta: { title: '供需配置' },
        },
        {
          path: 'exchange/application/assessment-config',
          name: 'exchange-assessment-config',
          component: () => import('@/views/system/AssessmentConfigView.vue'),
          meta: { title: '考核评估配置' },
        },
        {
          path: 'system/exchange/application/supply-config',
          redirect: { name: 'exchange-supply-config' },
        },
        {
          path: 'system/exchange/application/assessment-config',
          redirect: { name: 'exchange-assessment-config' },
        },
        {
          path: 'exchange/analysis-portal',
          name: 'exchange-analysis-portal',
          component: () => import('@/views/exchange/application/AnalysisPortalHubView.vue'),
          meta: { title: '应用分析门户', hubLayout: true, hideAppHeader: true, flushMain: true },
        },
        {
          path: 'exchange/analysis-portal/dept',
          name: 'exchange-analysis-dept',
          component: () => import('@/views/exchange/application/DeptPortalHubView.vue'),
          meta: { title: '部门数据共享门户', hubLayout: true, hideAppHeader: true, flushMain: true },
        },
        {
          path: 'exchange/analysis-portal/leader',
          name: 'exchange-analysis-leader',
          component: () => import('@/views/exchange/application/LeaderPortalHubView.vue'),
          meta: { title: '领导决策门户', hubLayout: true, hideAppHeader: true, flushMain: true },
        },
        {
          path: 'exchange/portal',
          name: 'exchange-portal',
          component: () => import('@/views/exchange/PortalHubView.vue'),
          meta: { title: '应用分析门户', hubLayout: true },
        },
        {
          path: 'integration',
          redirect: { path: '/analytics/support', query: { tab: 'ops.kettle' } },
        },
        {
          path: 'integration/kettle',
          name: 'integration-kettle',
          redirect: { path: '/analytics/support', query: { tab: 'ops.kettle' } },
        },
        {
          path: 'governance',
          name: 'governance',
          component: () => import('@/views/governance/GovernanceHubView.vue'),
          meta: { title: '数据融合治理平台', hubLayout: true },
        },
        {
          path: 'unstructured',
          name: 'unstructured',
          component: () => import('@/views/unstructured/UnstructuredHubView.vue'),
          meta: { title: '非结构化治理平台', hubLayout: true },
        },
        {
          path: 'resource-center',
          name: 'resource-center',
          component: () => import('@/views/resource/ResourceCenterHubView.vue'),
          meta: { title: '大数据资源中心', hubLayout: true },
        },
        {
          path: 'analytics/support',
          name: 'analytics-support',
          component: () => import('@/views/analytics/AnalyticsSupportHubView.vue'),
          meta: { title: '通用支撑平台', hubLayout: true },
        },
        {
          path: 'analytics/bi',
          name: 'analytics-bi',
          component: () => import('@/views/analytics/AnalyticsBiHubView.vue'),
          meta: { title: '智能BI', hubLayout: true },
        },
        {
          path: 'analytics/population',
          name: 'analytics-population',
          component: () => import('@/views/analytics/AnalyticsDomainHubView.vue'),
          meta: { title: '人口大数据', hubLayout: true },
        },
        {
          path: 'analytics/legal-entity',
          name: 'analytics-legal',
          component: () => import('@/views/analytics/AnalyticsDomainHubView.vue'),
          meta: { title: '法人大数据', hubLayout: true },
        },
        {
          path: 'analytics/macro',
          name: 'analytics-macro',
          component: () => import('@/views/analytics/AnalyticsDomainHubView.vue'),
          meta: { title: '宏观经济', hubLayout: true },
        },
        {
          path: 'analytics/key-domains',
          name: 'analytics-key',
          component: () => import('@/views/analytics/AnalyticsDomainHubView.vue'),
          meta: { title: '重点领域', hubLayout: true },
        },
        {
          path: 'analytics/embed-preview',
          name: 'analytics-embed',
          component: () => import('@/views/analytics/EmbedPreviewView.vue'),
          meta: { title: 'DataEase嵌入' },
        },
        {
          path: 'integration/ds',
          name: 'integration-ds',
          redirect: { path: '/analytics/support', query: { tab: 'tasks' } },
        },
        {
          path: 'catalog',
          name: 'catalog-global',
          redirect: '/dashboard',
        },
        {
          path: 'catalog/:platform',
          name: 'catalog-platform',
          redirect: '/dashboard',
        },
        {
          path: 'modules/:mCode',
          name: 'module-detail',
          redirect: '/dashboard',
        },
        {
          path: ':pathMatch(.*)*',
          name: 'placeholder',
          component: () => import('@/views/PlaceholderView.vue'),
          meta: { title: '功能页' },
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    if (auth.isLoggedIn && to.path === '/login') return '/dashboard'
    return true
  }
  if (!auth.isLoggedIn) return '/login'

  let profileOk = true
  if (!auth.menus.length && !auth.permissions.length) {
    try {
      await auth.fetchProfile()
    } catch (e: unknown) {
      const err = e as Error & { code?: number }
      if (err.code === 401) {
        await auth.logout()
        return '/login'
      }
      profileOk = false
    }
  }

  // 进入总览前先就绪门户树，避免 Dashboard 首帧白屏
  if (to.path === '/dashboard' || to.path === '/') {
    try {
      const { loadPortalNav } = await import('@/utils/portal-nav-cache')
      await loadPortalNav()
    } catch {
      /* Dashboard 内再处理错误态 */
    }
  }

  if (auth.isSystemAdmin) return true

  if (!profileOk && !auth.menus.length) {
    if (to.path === '/dashboard' || to.path === '/') return true
    ElMessage.warning('菜单加载失败，请刷新后重试')
    return '/dashboard'
  }

  if (!canAccessRoutePath(to.path, auth.menus, { isSystemAdmin: auth.isSystemAdmin })) {
    ElMessage.warning('无权访问该页面')
    return '/dashboard'
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · 承德智慧城市` : '承德智慧城市'
})

export default router
