import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

/**
 * 供需三角色能力（基于权限码，与规划对齐）：
 * - 平台管理员（数据主管部门）：portal:supply:approve / system:exchange:supply-config / 超管
 * - 数据需求部门 / 数据提供部门：portal:supply:create（部门管理员兼两端，按菜单进入对应模块）
 */
export function useSupplyRole() {
  const auth = useAuthStore()

  const isPlatformAdmin = computed(
    () =>
      !!auth.isSystemAdmin
      || auth.hasPermission('portal:supply:approve')
      || auth.hasPermission('system:exchange:supply-config'),
  )

  const isDemandDept = computed(
    () => !!auth.isSystemAdmin || auth.hasPermission('portal:supply:create') || isPlatformAdmin.value,
  )

  const isProviderDept = computed(
    () => !!auth.isSystemAdmin || auth.hasPermission('portal:supply:create') || isPlatformAdmin.value,
  )

  /** 侧栏可见叶子：home / demand / analysis / confirm / supply / supervise / manifest / config */
  function canAccessSection(key: string): boolean {
    if (key === 'home' || key === 'manifest-center') {
      return isDemandDept.value || isPlatformAdmin.value
    }
    if (key === 'demand') return isDemandDept.value
    if (key === 'analysis' || key === 'supervise') return isPlatformAdmin.value
    if (key === 'confirm' || key === 'supply') return isProviderDept.value
    if (key === 'supply-config' || key === 'matter-manage') {
      return !!auth.isSystemAdmin || auth.hasPermission('system:exchange:supply-config') || isPlatformAdmin.value
    }
    if (key === 'system') return isPlatformAdmin.value
    return false
  }

  return { isPlatformAdmin, isDemandDept, isProviderDept, canAccessSection }
}
