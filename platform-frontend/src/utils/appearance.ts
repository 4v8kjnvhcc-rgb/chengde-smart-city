/** 系统外观：主题 / 页签 / favicon 应用 */

export interface AppearancePublic {
  themeId?: string
  logoMode?: string
  logoUrl?: string | null
  loginCaptchaEnabled?: boolean
  loginTitle?: string
  loginTitleFontSize?: number
  loginTitleColor?: string
  loginBgMode?: string
  loginMediaUrl?: string | null
  loginMediaType?: string | null
  browserTitle?: string | null
  faviconUrl?: string | null
  watermarkEnabled?: boolean
  watermarkText?: string | null
  watermarkShowUsername?: boolean
  authMethods?: {
    password?: boolean
    sms?: boolean
    totp?: boolean
    fingerprint?: boolean
    twoFactorRequired?: boolean
  }
  themes?: Array<{
    id: string
    name: string
    primaryColor?: string
    sidebarBg?: string
    builtin?: boolean
  }>
}

const DEFAULT_TITLE = '承德高新区智慧城市基础平台'

export function applyThemeFromAppearance(cfg: AppearancePublic | null | undefined) {
  if (!cfg) return
  const themes = cfg.themes || []
  const theme = themes.find((t) => t.id === cfg.themeId) || themes[0]
  const root = document.documentElement
  if (theme?.primaryColor) {
    root.style.setProperty('--portal-primary', theme.primaryColor)
    root.style.setProperty('--el-color-primary', theme.primaryColor)
  }
  if (theme?.sidebarBg) {
    root.style.setProperty('--portal-sidebar-bg', theme.sidebarBg)
  }
}

export function applyBrowserChrome(cfg: AppearancePublic | null | undefined) {
  if (!cfg) return
  const title = (cfg.browserTitle && cfg.browserTitle.trim()) || DEFAULT_TITLE
  document.title = title
  if (cfg.faviconUrl) {
    let link = document.querySelector("link[rel='icon']") as HTMLLinkElement | null
    if (!link) {
      link = document.createElement('link')
      link.rel = 'icon'
      document.head.appendChild(link)
    }
    link.href = cfg.faviconUrl
  }
}

export async function loadAndApplyPublicAppearance(fetcher: () => Promise<{ data: AppearancePublic }>) {
  try {
    const res = await fetcher()
    const cfg = res.data
    applyThemeFromAppearance(cfg)
    applyBrowserChrome(cfg)
    return cfg
  } catch {
    return null
  }
}
