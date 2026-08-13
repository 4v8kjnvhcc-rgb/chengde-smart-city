import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { SUPPLY_NAV_PERMISSIONS } from '@/utils/hub-nav-permission'

/**
 * 数据供需对接系统 · 侧栏可见性
 * 以角色「配置菜单」勾选的 hub:application:supply:* 为准；
 * 兼容旧码 portal:supply:create / approve、system:exchange:supply-config。
 */
export function useSupplyRole() {
  const auth = useAuthStore()

  const isSuperAdmin = computed(() => !!auth.isSystemAdmin)

  function hasHub(sectionKey: string): boolean {
    if (isSuperAdmin.value) return true
    const mapped = SUPPLY_NAV_PERMISSIONS[sectionKey]
    if (!mapped) return false
    const codes = Array.isArray(mapped) ? mapped : [mapped]
    return codes.some((c) => auth.hasPermission(c))
  }

  /** 旧三角色码（按钮菜单已停用后，可由父级菜单继承或 V202 映射补齐） */
  const hasCreate = computed(
    () => isSuperAdmin.value || auth.hasPermission('portal:supply:create') || hasHub('demand') || hasHub('confirm'),
  )
  const hasApprove = computed(
    () =>
      isSuperAdmin.value
      || auth.hasPermission('portal:supply:approve')
      || auth.hasPermission('system:exchange:supply-config')
      || hasHub('analysis')
      || hasHub('supervise')
      || hasHub('home')
      || hasHub('supply-config'),
  )

  const isPlatformAdmin = computed(() => hasApprove.value)
  const isPlatformOnly = computed(() => hasApprove.value && !hasCreate.value)
  const isDeptAdmin = computed(() => hasCreate.value)

  const isDemandDept = isDeptAdmin
  const isProviderDept = isDeptAdmin

  /** 侧栏可见：优先 hub 菜单码；无 hub 码时回退旧三角色口径 */
  function canAccessSection(key: string): boolean {
    if (isSuperAdmin.value) return true
    if (hasHub(key)) return true
    // 父级「系统管理」：任一子项 hub 或旧平台码
    if (key === 'system') {
      return hasHub('supply-config') || hasHub('matter-manage') || hasApprove.value
    }
    // 无任何 hub:application:supply:* 时，兼容旧角色
    const anyHubSupply = Object.keys(SUPPLY_NAV_PERMISSIONS).some((k) => {
      if (k === 'system') return false
      const m = SUPPLY_NAV_PERMISSIONS[k]
      const codes = Array.isArray(m) ? m : [m]
      return codes.some((c) => auth.hasPermission(c))
    })
    if (anyHubSupply) return false

    const DEPT_SECTIONS = new Set(['demand', 'confirm'])
    const PLATFORM_SECTIONS = new Set([
      'home',
      'analysis',
      'supervise',
      'system',
      'supply-config',
      'matter-manage',
    ])
    const SHARED_SECTIONS = new Set(['supply', 'manifest-center'])
    if (SHARED_SECTIONS.has(key)) return hasCreate.value || hasApprove.value
    if (DEPT_SECTIONS.has(key)) return hasCreate.value && !isPlatformOnly.value
    if (PLATFORM_SECTIONS.has(key)) return hasApprove.value
    return false
  }

  function defaultSection(): string {
    const order = [
      'home',
      'demand',
      'analysis',
      'confirm',
      'supply',
      'supervise',
      'manifest-center',
      'supply-config',
      'matter-manage',
    ]
    return order.find((k) => canAccessSection(k)) || 'home'
  }

  return {
    isSuperAdmin,
    isPlatformAdmin,
    isPlatformOnly,
    isDeptAdmin,
    isDemandDept,
    isProviderDept,
    canAccessSection,
    defaultSection,
  }
}
