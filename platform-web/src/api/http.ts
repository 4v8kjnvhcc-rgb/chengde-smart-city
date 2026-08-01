import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/stores/auth'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
})

let refreshing: Promise<string> | null = null

async function refreshAccessToken(): Promise<string> {
  const refreshToken = localStorage.getItem('refreshToken')
  if (!refreshToken) {
    throw new Error('无 Refresh Token')
  }
  const res = await axios.post('/api/v1/auth/refresh', { refreshToken })
  const body = res.data
  if (!body || body.code !== 0) {
    throw new Error(body?.message || '刷新失败')
  }
  const accessToken = body.data.accessToken as string
  const nextRefresh = body.data.refreshToken as string
  localStorage.setItem('accessToken', accessToken)
  if (nextRefresh) {
    localStorage.setItem('refreshToken', nextRefresh)
  }
  try {
    const auth = useAuthStore()
    auth.accessToken = accessToken
    if (nextRefresh) auth.refreshToken = nextRefresh
  } catch {
    // Pinia 未初始化时仅更新 localStorage
  }
  return accessToken
}

function clearSessionAndRedirect() {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  if (!window.location.pathname.includes('/login')) {
    window.location.href = '/login'
  }
}

function rejectFrom(err: AxiosError) {
  const body = err.response?.data as { message?: string; code?: number } | undefined
  const message = body?.message || err.message || '请求失败'
  const wrapped = new Error(message) as Error & { code?: number }
  wrapped.code = body?.code ?? err.response?.status
  return Promise.reject(wrapped)
}

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body.code === 'number' && body.code !== 0) {
      const err = new Error(body.message || '请求失败') as Error & { code?: number }
      err.code = body.code
      return Promise.reject(err)
    }
    return body?.data !== undefined ? { ...res, data: body.data } : res
  },
  async (err: AxiosError) => {
    const original = err.config as InternalAxiosRequestConfig & { _retry?: boolean }
    const url = original?.url || ''
    const isAuthApi = url.includes('/auth/login') || url.includes('/auth/refresh') || url.includes('/auth/logout')

    if (err.response?.status === 401 && original && !original._retry && !isAuthApi) {
      original._retry = true
      try {
        if (!refreshing) {
          refreshing = refreshAccessToken().finally(() => {
            refreshing = null
          })
        }
        const token = await refreshing
        original.headers.Authorization = `Bearer ${token}`
        return api(original)
      } catch {
        clearSessionAndRedirect()
        return rejectFrom(err)
      }
    }

    // 已刷新过仍 401：多为接口未部署/无权限，不应踢出登录
    if (err.response?.status === 401 && original?._retry) {
      return rejectFrom(err)
    }

    if (err.response?.status === 401 && (isAuthApi || !original)) {
      clearSessionAndRedirect()
    }

    return rejectFrom(err)
  },
)

export default api
