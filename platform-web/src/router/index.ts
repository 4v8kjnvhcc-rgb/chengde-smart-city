import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

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
          path: 'system/users',
          name: 'system-users',
          component: () => import('@/views/system/UserManage.vue'),
          meta: { title: '用户管理' },
        },
        {
          path: 'system/roles',
          name: 'system-roles',
          component: () => import('@/views/system/RoleManage.vue'),
          meta: { title: '角色管理' },
        },
        {
          path: 'system/orgs',
          name: 'system-orgs',
          component: () => import('@/views/system/OrgManage.vue'),
          meta: { title: '机构管理' },
        },
        {
          path: 'system/menus',
          name: 'system-menus',
          component: () => import('@/views/system/MenuManage.vue'),
          meta: { title: '菜单管理' },
        },
        {
          path: 'system/portal-links',
          name: 'system-portal-links',
          component: () => import('@/views/system/PortalLinkManage.vue'),
          meta: { title: '门户外链管理' },
        },
        {
          path: 'system/audit',
          name: 'system-audit',
          component: () => import('@/views/system/AuditLog.vue'),
          meta: { title: '审计日志' },
        },
        {
          path: 'system/security',
          name: 'system-security',
          component: () => import('@/views/system/SecurityConfig.vue'),
          meta: { title: '等保开关' },
        },
        {
          path: 'exchange/ingestion',
          name: 'exchange-ingestion',
          component: () => import('@/views/exchange/ingestion/IngestionHubView.vue'),
          meta: { title: '大数据归集平台', hubLayout: true },
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
          component: () => import('@/views/exchange/SupplyDemandHubView.vue'),
          meta: { title: '供需对接平台', hubLayout: true },
        },
        {
          path: 'exchange/assessment',
          name: 'exchange-assessment',
          component: () => import('@/views/exchange/AssessmentView.vue'),
          meta: { title: '考核评估', hubLayout: true },
        },
        {
          path: 'exchange/portal',
          name: 'exchange-portal',
          component: () => import('@/views/exchange/PortalHubView.vue'),
          meta: { title: '应用分析门户', hubLayout: true },
        },
        {
          path: 'exchange/analysis-portal',
          redirect: { name: 'exchange-portal', query: { tab: 'home' } },
        },
        {
          path: 'integration/kettle',
          name: 'integration-kettle',
          component: () => import('@/views/integration/KettleView.vue'),
          meta: { title: 'ETL治理' },
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
          meta: { title: '通用支撑', hubLayout: true },
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
          component: () => import('@/views/integration/SchedulerView.vue'),
          meta: { title: '调度管理' },
        },
        {
          path: 'catalog',
          name: 'catalog-global',
          component: () => import('@/views/catalog/GlobalCatalogView.vue'),
          meta: { title: 'D05全量检索' },
        },
        {
          path: 'catalog/:platform',
          name: 'catalog-platform',
          component: () => import('@/views/catalog/PlatformCatalogView.vue'),
          meta: { title: 'D05功能清单' },
        },
        {
          path: 'modules/:mCode',
          name: 'module-detail',
          component: () => import('@/views/catalog/ModuleDetailView.vue'),
          meta: { title: '模块详情' },
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
  if (!auth.menus.length && !auth.permissions.length) {
    try {
      await auth.fetchProfile()
    } catch (e: unknown) {
      const err = e as Error & { code?: number }
      // 仅认证失败时登出；网络/接口异常不应清空会话
      if (err.code === 401) {
        await auth.logout()
        return '/login'
      }
    }
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · 承德智慧城市` : '承德智慧城市'
})

export default router
