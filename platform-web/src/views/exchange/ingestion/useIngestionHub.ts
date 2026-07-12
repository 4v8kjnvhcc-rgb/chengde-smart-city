import { ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'

export interface GuideStep { stepNo: number; stepName: string; stepDesc: string; requiredFlag: number; jumpModule?: string }
export interface Project { id: number; projectCode: string; projectName: string; boundOrgId: number; systemName: string; status: string }
export interface DataSource { id: number; projectId: number; sourceCode: string; sourceName: string; sourceType: string; connStatus: string; tableCount: number; connConfigJson?: string }
export interface DataTable { id: number; sourceId: number; tableCode: string; tableName: string; modelingMode: string; columnCount: number }
export interface DataColumn { id: number; tableId: number; columnCode: string; columnName: string; dataType: string; nullableFlag: number; semanticDesc?: string; lengthVal?: number; componentType?: string; requiredTip?: string; builtInFlag?: number }
export interface Dict { id: number; dictCode: string; dictName: string; dictType: string; itemCount: number; status: string }
export interface DictItem { id: number; dictId: number; itemKey: string; itemValue: string; sortOrder: number; status: string }
export interface LineageEdge { id: number; fromNode: string; toNode: string; fromLabel: string; toLabel: string; edgeType: string; fieldMapping?: string }
export interface ColumnLineage { id: number; tableNode: string; columnCode: string; columnName: string; upstreamTable?: string; upstreamColumn?: string; downstreamTable?: string; downstreamColumn?: string }
export interface UploadTemplate { id: number; templateCode: string; templateName: string; columnMappingJson: string; status: string }
export interface Upload { id: number; templateCode: string; fileName: string; rowCount: number; status: string; previewJson?: string }
export interface Channel { id: number; channelCode: string; channelName: string; channelType: string; status: string; lastMessage?: string; configJson?: string }
export interface IngestTask { id: number; taskCode: string; taskName: string; channelId: number; scheduleCron: string; status: string; lastRunMessage?: string }
export interface PipelineJob { id: number; jobCode: string; jobName: string; jobType: string; status: string; billAmount?: number; resultJson?: string }
export interface ProbeReport { id: number; reportCode: string; sourceName: string; nullRate: number; domainCheck: string; entityType: string; status: string }
export interface DataDefinition { id: number; defCode: string; defName: string; businessDesc: string; techDesc: string; status: string }
export interface ReconcileLog { id: number; batchNo: string; matchedPct: number; diffRows: number; alertLevel: string; status: string }
export interface Registry { id: number; registryCode: string; title: string; categoryPath: string; secretLevel: string; publishStatus: string; approvalStatus: string }
export interface CategoryNode { id: number; nodeCode: string; nodeName: string; parentId: number; secretLevel: string; sortOrder: number }
export interface Policy { id: number; policyCode: string; policyName: string; policyType: string; ruleExpr?: string; lifecycleStage?: string }
export interface AssetTag { id: number; tagCode: string; tagName: string; ruleExpr: string; tagDesc?: string; hitCount: number; status: string }
export interface HealthMetric { metricLabel: string; metricValue: string; alertLevel: string }

export function useIngestionLoading() {
  const loading = ref(false)
  const loadError = ref('')

  async function withLoad<T>(fn: () => Promise<T>): Promise<T | undefined> {
    loading.value = true
    loadError.value = ''
    try {
      return await fn()
    } catch (e: unknown) {
      loadError.value = e instanceof Error ? e.message : '加载失败，请确认后端已启动（8080）且已登录'
      ElMessage.error(loadError.value)
      return undefined
    } finally {
      loading.value = false
    }
  }

  return { loading, loadError, withLoad }
}

export function downloadText(filename: string, content: string) {
  const blob = new Blob(['\uFEFF' + content], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

export const ingestionApi = {
  guides: () => api.get<GuideStep[]>('/exchange/ingestion/guides'),
  registerOverview: () => api.get<Record<string, unknown>>('/exchange/ingestion/register/overview'),
  assetReport: () => api.get<Record<string, unknown>>('/exchange/ingestion/register/asset-report'),
  lineage: (projectScope?: string) => api.get<{ nodes: { id: string; label: string; type: string }[]; edges: LineageEdge[] }>('/exchange/ingestion/register/lineage', { params: { projectScope } }),
  lineageDrill: (nodeId: string) => api.get<{ focusNode: string; nodes: { id: string; label: string; type: string }[]; edges: LineageEdge[]; upstream: LineageEdge[]; downstream: LineageEdge[] }>('/exchange/ingestion/register/lineage/drill', { params: { nodeId } }),
  fieldLineage: (tableNode: string) => api.get<ColumnLineage[]>('/exchange/ingestion/register/lineage/fields', { params: { tableNode } }),
  projects: () => api.get<Project[]>('/exchange/ingestion/projects'),
  createProject: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/projects', body),
  deleteProject: (id: number) => api.delete<void>(`/exchange/ingestion/projects/${id}`),
  dataSources: (projectId?: number) => api.get<DataSource[]>('/exchange/ingestion/data-sources', { params: { projectId } }),
  createDataSource: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/data-sources', body),
  updateDataSource: (id: number, body: Record<string, unknown>) => api.put<void>(`/exchange/ingestion/data-sources/${id}`, body),
  testDataSource: (id: number) => api.post<Record<string, unknown>>(`/exchange/ingestion/data-sources/${id}/test`),
  tables: (sourceId?: number) => api.get<DataTable[]>('/exchange/ingestion/register/tables', { params: { sourceId } }),
  createTable: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/register/tables', body),
  columns: (tableId: number) => api.get<DataColumn[]>(`/exchange/ingestion/register/tables/${tableId}/columns`),
  createColumn: (tableId: number, body: Record<string, unknown>) => api.post<number>(`/exchange/ingestion/register/tables/${tableId}/columns`, body),
  updateColumn: (columnId: number, body: Record<string, unknown>) => api.put<void>(`/exchange/ingestion/register/columns/${columnId}`, body),
  metadataTemplate: () => api.get<string>('/exchange/ingestion/register/metadata/template'),
  importMetadata: (body: Record<string, unknown>) => api.post<Record<string, unknown>>('/exchange/ingestion/register/metadata/import', body),
  dicts: (keyword?: string) => api.get<Dict[]>('/exchange/ingestion/dicts', { params: { keyword } }),
  createDict: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/dicts', body),
  updateDict: (id: number, body: Record<string, unknown>) => api.put<void>(`/exchange/ingestion/dicts/${id}`, body),
  deleteDicts: (ids: number[]) => api.delete<void>('/exchange/ingestion/dicts', { data: { ids } }),
  dictItems: (dictId: number) => api.get<DictItem[]>(`/exchange/ingestion/dicts/${dictId}/items`),
  createDictItem: (dictId: number, body: Record<string, unknown>) => api.post<number>(`/exchange/ingestion/dicts/${dictId}/items`, body),
  updateDictItem: (itemId: number, body: Record<string, unknown>) => api.put<void>(`/exchange/ingestion/dicts/items/${itemId}`, body),
  deleteDictItem: (itemId: number) => api.delete<void>(`/exchange/ingestion/dicts/items/${itemId}`),
  dictTemplate: () => api.get<string>('/exchange/ingestion/dicts/template'),
  importDict: (csvText: string) => api.post<Record<string, unknown>>('/exchange/ingestion/dicts/import', { csvText }),
  exportDict: (ids: number[]) => api.post<string>('/exchange/ingestion/dicts/export', { ids }),
  tags: () => api.get<AssetTag[]>('/exchange/ingestion/register/tags'),
  createTag: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/register/tags', body),
  updateTag: (id: number, body: Record<string, unknown>) => api.put<void>(`/exchange/ingestion/register/tags/${id}`, body),
  matchTags: () => api.post<Record<string, unknown>>('/exchange/ingestion/register/tags/match'),
  templates: () => api.get<UploadTemplate[]>('/exchange/ingestion/collect/templates'),
  createTemplate: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/collect/templates', body),
  uploads: () => api.get<Upload[]>('/exchange/ingestion/uploads'),
  upload: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/uploads', body),
  uploadFile: (form: FormData) => api.post<number>('/exchange/ingestion/collect/uploads/file', form),
  channels: (channelType?: string) => api.get<Channel[]>('/exchange/ingestion/channels', { params: { channelType } }),
  updateChannel: (id: number, body: Record<string, unknown>) => api.put<void>(`/exchange/ingestion/channels/${id}`, body),
  runChannel: (id: number) => api.post<Record<string, unknown>>(`/exchange/ingestion/channels/${id}/run`),
  tasks: (channelId?: number) => api.get<IngestTask[]>('/exchange/ingestion/collect/tasks', { params: { channelId } }),
  createTask: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/collect/tasks', body),
  pipelineJobs: (jobType?: string) => api.get<PipelineJob[]>('/exchange/ingestion/pipeline-jobs', { params: { jobType } }),
  runPipeline: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/pipeline-jobs/run', body),
  probeReports: () => api.get<ProbeReport[]>('/exchange/ingestion/collect/probe-reports'),
  definitions: () => api.get<DataDefinition[]>('/exchange/ingestion/collect/definitions'),
  saveDefinition: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/collect/definitions', body),
  reconcileLogs: () => api.get<ReconcileLog[]>('/exchange/ingestion/collect/reconcile-logs'),
  reconcile: (action: string) => api.get<Record<string, unknown>>(`/exchange/ingestion/reconcile/${action}`),
  registries: () => api.get<Registry[]>('/exchange/ingestion/registries'),
  createRegistry: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/registries', body),
  approveRegistry: (id: number, body: Record<string, unknown>) => api.post<void>(`/exchange/ingestion/registries/${id}/approve`, body),
  categories: () => api.get<CategoryNode[]>('/exchange/ingestion/collect/categories'),
  createCategory: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/collect/categories', body),
  policies: (policyType?: string) => api.get<Policy[]>('/exchange/ingestion/policies', { params: { policyType } }),
  globalView: () => api.get<Record<string, unknown>>('/exchange/ingestion/global-view'),
  health: () => api.get<HealthMetric[]>('/exchange/ingestion/health'),
  search: (q: string) => api.get<Record<string, unknown>>('/exchange/ingestion/search', { params: { q } }),
  lifecycle: (id: number) => api.post<Record<string, unknown>>(`/exchange/ingestion/policies/${id}/lifecycle`),
  backupJobs: () => api.get<Record<string, unknown>[]>('/exchange/ingestion/collect/backup-jobs'),
  archiveJobs: () => api.get<Record<string, unknown>[]>('/exchange/ingestion/collect/archive-jobs'),
}
