import { computed } from 'vue'
import { statusLabel } from '@/utils/status-label'
import api from '@/api/http'
import { useAuthStore } from '@/stores/auth'

/** 登记审核状态码 */
export const REG_STATUS = {
  DRAFT: 'DRAFT',
  PENDING_REVIEW: 'PENDING_REVIEW',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
} as const

export function registerStatusZh(code?: string | null) {
  if (!code) return '—'
  const map: Record<string, string> = {
    DRAFT: '草稿',
    PENDING_REVIEW: '待审核',
    PENDING: '待审核',
    PENDING_ARCHIVE: '待审核',
    APPROVED: '审核通过',
    ARCHIVED: '审核通过',
    REJECTED: '驳回待提交',
  }
  return map[code] || statusLabel(code)
}

export function canEditRegister(status?: string | null) {
  const s = (status || 'DRAFT').toUpperCase()
  return s === 'DRAFT' || s === 'REJECTED'
}

export function canSubmitRegister(status?: string | null) {
  return canEditRegister(status)
}

export function canAuditRegister(status?: string | null) {
  const s = (status || '').toUpperCase()
  return s === 'PENDING_REVIEW' || s === 'PENDING' || s === 'PENDING_ARCHIVE'
}

/** 待审核可撤销，回到草稿 */
export function canWithdrawRegister(status?: string | null) {
  return canAuditRegister(status)
}

/**
 * 角色只控菜单显隐：能进登记页则提交/审核按钮都可见，
 * 是否可点仍由业务状态（草稿/待审核）决定。
 */
export function useRegisterWorkflowRole() {
  const canSubmit = computed(() => true)
  const canAudit = computed(() => true)
  return { canSubmit, canAudit }
}

/** 平台管理员 / 超级管理员可删除任意状态登记对象 */
export function useCanForceDeleteRegister() {
  const auth = useAuthStore()
  return computed(() => !!(auth.isPlatformOrSystemAdmin || auth.isSystemAdmin || auth.isPlatformAdmin))
}

export async function submitRegister(objectType: string, objectId: number) {
  await api.post('/exchange/ingestion/register/workflow/submit', { objectType, objectId })
}

export async function withdrawRegister(objectType: string, objectId: number) {
  await api.post('/exchange/ingestion/register/workflow/withdraw', { objectType, objectId })
}

export async function approveRegister(objectType: string, objectId: number, comment?: string) {
  await api.post('/exchange/ingestion/register/workflow/approve', { objectType, objectId, comment })
}

export async function rejectRegister(objectType: string, objectId: number, reason: string) {
  await api.post('/exchange/ingestion/register/workflow/reject', { objectType, objectId, reason })
}

export async function loadRegisterLogs(objectType: string, objectId: number) {
  return (await api.get('/exchange/ingestion/register/workflow/logs', { params: { objectType, objectId } })).data || []
}
