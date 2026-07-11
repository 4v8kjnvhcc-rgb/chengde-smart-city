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
    } catch {
      await auth.logout()
      return '/login'
    }
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · 承德智慧城市` : '承德智慧城市'
})

export default router
