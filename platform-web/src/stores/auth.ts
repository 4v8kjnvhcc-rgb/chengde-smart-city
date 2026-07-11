import { defineStore } from 'pinia'
import api from '@/api/http'

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
  children: MenuNode[]
}

export interface UserInfo {
  id: number
  username: string
  displayName: string
  orgId: number
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: localStorage.getItem('accessToken') || '',
    refreshToken: localStorage.getItem('refreshToken') || '',
    user: null as UserInfo | null,
    menus: [] as MenuNode[],
    permissions: [] as string[],
  }),
  getters: {
    isLoggedIn: (s) => !!s.accessToken,
  },
  actions: {
    async login(username: string, password: string, totpCode?: string) {
      const res = await api.post('/auth/login', { username, password, totpCode })
      this.accessToken = res.data.accessToken
      this.refreshToken = res.data.refreshToken
      this.user = res.data.user
      localStorage.setItem('accessToken', this.accessToken)
      localStorage.setItem('refreshToken', this.refreshToken)
      await this.fetchProfile()
    },
    async fetchProfile() {
      const [menusRes, permRes] = await Promise.all([
        api.get('/system/menus/me'),
        api.get('/system/permissions/me'),
      ])
      this.menus = menusRes.data
      this.permissions = permRes.data
    },
    async logout() {
      try {
        await api.post('/auth/logout')
      } finally {
        this.accessToken = ''
        this.refreshToken = ''
        this.user = null
        this.menus = []
        this.permissions = []
        localStorage.removeItem('accessToken')
        localStorage.removeItem('refreshToken')
      }
    },
    hasPermission(code: string) {
      return this.permissions.includes(code)
    },
  },
})
