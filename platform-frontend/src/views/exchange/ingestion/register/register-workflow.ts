import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { statusLabel } from '@/utils/status-label'
import api from '@/api/http'

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

export function useRegisterWorkflowRole() {
  const auth = useAuthStore()
  const canSubmit = computed(
    () => auth.hasPermission('hub:ingestion:register:submit') || auth.isSystemAdmin,
  )
  const canAudit = computed(
    () => auth.hasPermission('hub:ingestion:register:audit') || auth.isSystemAdmin,
  )
  return { canSubmit, canAudit }
}

export async function submitRegister(objectType: string, objectId: number) {
  await api.post('/exchange/ingestion/register/workflow/submit', { objectType, objectId })
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
