import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

/**
 * 数据供需对接系统 · 三角色菜单口径：
 * - 部门管理员：数据需求管理、数据需求确认、数据供给查看、清单中心
 * - 平台管理员：首页、数据需求分析、数据供给查看、业务督办、清单中心、系统管理
 * - 超级管理员：全部模块
 *
 * 权限码约定：
 * - portal:supply:create → 部门管理员
 * - portal:supply:approve / system:exchange:supply-config → 平台管理员
 */
export function useSupplyRole() {
  const auth = useAuthStore()

  const isSuperAdmin = computed(() => !!auth.isSystemAdmin)

  /** 平台管理员（含超管，用于首页 KPI / 预审入口等） */
  const isPlatformAdmin = computed(
    () =>
      isSuperAdmin.value
      || auth.hasPermission('portal:supply:approve')
      || auth.hasPermission('system:exchange:supply-config'),
  )

  /** 仅平台岗（非超管）：不开放需求填报/确认 */
  const isPlatformOnly = computed(
    () =>
      !isSuperAdmin.value
      && (auth.hasPermission('portal:supply:approve')
        || auth.hasPermission('system:exchange:supply-config')),
  )

  /** 部门管理员（需求填报 + 供数确认；超管亦视为具备） */
  const isDeptAdmin = computed(
    () => isSuperAdmin.value || auth.hasPermission('portal:supply:create'),
  )

  /** @deprecated 兼容旧名：部门侧填报 */
  const isDemandDept = isDeptAdmin
  /** @deprecated 兼容旧名：部门侧确认/供给 */
  const isProviderDept = isDeptAdmin

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

  /** 侧栏可见叶子 */
  function canAccessSection(key: string): boolean {
    if (isSuperAdmin.value) return true
    if (SHARED_SECTIONS.has(key)) {
      return isDeptAdmin.value || isPlatformOnly.value
    }
    if (DEPT_SECTIONS.has(key)) {
      // 同时持有平台审批权时按平台口径，不开放填报/确认
      return auth.hasPermission('portal:supply:create') && !isPlatformOnly.value
    }
    if (PLATFORM_SECTIONS.has(key)) {
      return isPlatformOnly.value
    }
    return false
  }

  /** 无权限访问目标时的默认落地页 */
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
    return order.find((k) => canAccessSection(k)) || 'demand'
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
