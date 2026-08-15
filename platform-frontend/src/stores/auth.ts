import { defineStore } from 'pinia'
import api from '@/api/http'
import { decryptTransportCipher, encryptTransportSession } from '@/utils/transport-crypto'

export interface MenuNode {
  id: number
  parentId: number
  menuName: string
  menuType: number
  path: string
  component: string
  permission: string
  icon: string
  mCode: string
  integrationType: string
  visible?: number
  children: MenuNode[]
}

export interface UserInfo {
  id: number
  username: string
  displayName: string
  orgId: number
  orgName?: string
}

function stripPhone<T extends Record<string, unknown>>(user: T | null | undefined): UserInfo | null {
  if (!user) {
    return null
  }
  const next = { ...user } as T & { phone?: string }
  delete next.phone
  return next as unknown as UserInfo
}

function readStoredUser(): UserInfo | null {
  try {
    const raw = localStorage.getItem('user')
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw) as UserInfo & { phone?: string }
    if (parsed.phone != null) {
      delete parsed.phone
      localStorage.setItem('user', JSON.stringify(parsed))
    }
    return parsed
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: localStorage.getItem('accessToken') || '',
    refreshToken: localStorage.getItem('refreshToken') || '',
    user: readStoredUser() as UserInfo | null,
    menus: [] as MenuNode[],
    permissions: [] as string[],
  }),
  getters: {
    isLoggedIn: (s) => !!s.accessToken,
    /** 超级管理员：按约定账号放行（与后端 SYSTEM_ADMIN 对齐） */
    isSystemAdmin: (s) => s.user?.username === 'sys_admin',
    /**
     * 平台管理员：含数据资产「管理」菜单权限，或供需主管部门权限；
     * 超级管理员一并视为具备平台管理能力。
     */
    isPlatformAdmin: (s) =>
      s.user?.username === 'sys_admin'
      || (Array.isArray(s.permissions) && (
        s.permissions.includes('portal:supply:approve')
        || s.permissions.includes('system:exchange:supply-config')
        || s.permissions.includes('hub:ingestion:register:project-system-mgmt')
        || s.permissions.includes('hub:ingestion:register:asset-catalog-mgmt')
        || s.permissions.includes('hub:ingestion:register:m050')
        || s.permissions.includes('hub:ingestion:register:m045')
      )),
    /** 平台管理员或超级管理员（强制删除等） */
    isPlatformOrSystemAdmin: (s) =>
      s.user?.username === 'sys_admin'
      || (Array.isArray(s.permissions) && (
        s.permissions.includes('portal:supply:approve')
        || s.permissions.includes('system:exchange:supply-config')
        || s.permissions.includes('hub:ingestion:register:project-system-mgmt')
        || s.permissions.includes('hub:ingestion:register:asset-catalog-mgmt')
        || s.permissions.includes('hub:ingestion:register:m050')
        || s.permissions.includes('hub:ingestion:register:m045')
      )),
  },
  actions: {
    async login(
      username: string,
      password: string,
      totpCode?: string,
      captchaId?: string,
      captchaCode?: string,
    ) {
      const session = await encryptTransportSession({ username, password })
      const res = await api.post('/auth/login', {
        ...session.envelope,
        totpCode,
        captchaId,
        captchaCode,
      })
      const packed = res.data as { iv?: string; cipherText?: string }
      const payload = await decryptTransportCipher(session.aesKey, packed?.iv || '', packed?.cipherText || '') as {
        accessToken: string
        refreshToken: string
        user?: UserInfo & { phone?: string }
        passwordWarn?: boolean
        passwordWarnMessage?: string
      }
      this.accessToken = payload.accessToken
      this.refreshToken = payload.refreshToken
      this.user = stripPhone(payload.user)
      localStorage.setItem('accessToken', this.accessToken)
      localStorage.setItem('refreshToken', this.refreshToken)
      if (this.user) {
        localStorage.setItem('user', JSON.stringify(this.user))
        if (this.user.username) {
          localStorage.setItem('username', this.user.username)
        }
      }
      if (payload.passwordWarn && payload.passwordWarnMessage) {
        const { ElMessage } = await import('element-plus')
        ElMessage.warning(payload.passwordWarnMessage)
      }
      await this.fetchProfile()
    },
    async fetchProfile() {
      const [menusRes, permRes] = await Promise.all([
        api.get('/system/menus/me'),
        api.get('/system/permissions/me'),
      ])
      this.menus = Array.isArray(menusRes.data) ? menusRes.data : []
      this.permissions = Array.isArray(permRes.data) ? permRes.data : []
    },
    async logout() {
      try {
        // Token 已失效时后端会 401，属正常清场，勿向上抛出以免登录页误报
        await api.post('/auth/logout')
      } catch {
        // ignore
      }
      this.accessToken = ''
      this.refreshToken = ''
      this.user = null
      this.menus = []
      this.permissions = []
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('username')
      localStorage.removeItem('user')
      try {
        const { clearPortalNavCache } = await import('@/utils/portal-nav-cache')
        clearPortalNavCache()
      } catch {
        /* ignore */
      }
    },
    hasPermission(code: string) {
      return Array.isArray(this.permissions) && this.permissions.includes(code)
    },
  },
})
