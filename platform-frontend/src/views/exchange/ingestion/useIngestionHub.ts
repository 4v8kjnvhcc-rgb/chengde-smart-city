import { ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'

export interface GuideStep { stepNo: number; stepName: string; stepDesc: string; requiredFlag: number; jumpModule?: string }
export interface Project {
  id: number
  projectCode: string
  projectName: string
  boundOrgId: number
  boundOrgName?: string
  systemName?: string
  status: string
  registerStatus?: string
  rejectReason?: string
  createdBy?: string
}
export interface BizSystem {
  id: number
  projectId: number
  systemCode: string
  systemName: string
  status: string
  registerStatus?: string
  rejectReason?: string
  createdBy?: string
  dataSourceCount?: number
}
export interface DataSource {
  id: number
  projectId: number
  systemId?: number
  sourceCode: string
  sourceName: string
  systemName?: string
  sourceType: string
  connStatus: string
  registerStatus?: string
  rejectReason?: string
  tableCount: number
  connConfigJson?: string
  sourceSchema?: string
  probeAt?: string
  probeMessage?: string
  syncStatus?: string
}
export interface ProbeColumn { columnName: string; dataType: string; columnSize: number; nullable: boolean; remarks?: string; sortOrder: number }
export interface ProbeTable { sourceTable: string; columns: ProbeColumn[]; primaryKeys: string[]; rowCount: number }
export interface DataTable {
  id: number
  sourceId: number
  tableCode: string
  tableName: string
  modelingMode: string
  columnCount: number
  sourceTable?: string
  physicalTableName?: string
}
export interface DataColumn { id: number; tableId: number; columnCode: string; columnName: string; dataType: string; nullableFlag: number; semanticDesc?: string; lengthVal?: number; componentType?: string; requiredTip?: string; builtInFlag?: number }
export interface Dict {
  id: number
  dictCode: string
  dictName: string
  dictType: string
  standardNo?: string
  publisher?: string
  versionNo?: string
  remark?: string
  itemCount: number
  status: string
  registerStatus?: string
  rejectReason?: string
}
export interface DictItem {
  id: number
  dictId: number
  itemKey: string
  itemValue: string
  bizUsage?: string
  sortOrder: number
  status: string
}
export interface LineageEdge {
  id?: number
  fromNode: string
  toNode: string
  fromLabel?: string
  toLabel?: string
  edgeType?: string
  fieldMapping?: string
  crossDb?: boolean
  fromSourceName?: string
  toSourceName?: string
}
export interface LineageGraphNode {
  id: string
  label: string
  type?: string
  isolated?: boolean
  matched?: boolean
  dimmed?: boolean
  sourceName?: string
  tableCode?: string
  usageDesc?: string
  categories?: string[]
  edgeType?: string
  crossDb?: boolean
  fieldMapping?: string
  fromSourceName?: string
  toSourceName?: string
}
export interface ColumnLineage { id: number; tableNode: string; columnCode: string; columnName: string; upstreamTable?: string; upstreamColumn?: string; downstreamTable?: string; downstreamColumn?: string }
export interface UploadTemplate { id: number; templateCode: string; templateName: string; columnMappingJson: string; status: string }
export interface Upload {
  id: number
  templateCode: string
  fileName: string
  sheetName?: string
  targetTable?: string
  rowCount: number
  status: string
  previewJson?: string
}
export interface Channel {
  id: number
  channelCode: string
  channelName: string
  channelType: string
  status: string
  lastMessage?: string
  lastRunAt?: string
  configJson?: string
}
export interface IngestTask {
  id: number
  taskCode: string
  taskName: string
  channelId: number
  accessMode?: string
  sourceId?: number
  tableId?: number
  targetTable?: string
  configJson?: string
  writeMode?: string
  watermarkValue?: string
  enabled?: number
  collectedRows?: number
  scheduleCron: string
  status: string
  lastRunAt?: string
  lastRunMessage?: string
  errorDetail?: string
}
export interface PipelineJob { id: number; jobCode: string; jobName: string; jobType: string; status: string; billAmount?: number; resultJson?: string }
export interface ProbeReport { id: number; reportCode: string; sourceName: string; nullRate: number; domainCheck: string; entityType: string; status: string }
export interface DataDefinition { id: number; defCode: string; defName: string; businessDesc: string; techDesc: string; status: string }
export interface ReconcileLog { id: number; batchNo: string; matchedPct: number; diffRows: number; alertLevel: string; status: string }
export interface Registry {
  id: number
  registryCode: string
  resourceCode?: string
  title: string
  providerOrg?: string
  resourceFormat?: string
  shareType?: string
  updateCycle?: string
  description?: string
  categoryPath?: string
  categoryId?: number
  secretLevel: string
  publishStatus: string
  approvalStatus: string
  refSourceId?: number
  refTableId?: number
  assetSummary?: string
  orgId?: number
}
export interface CategoryNode {
  id: number
  nodeCode: string
  nodeName: string
  parentId: number
  secretLevel: string
  secretFlag?: number
  description?: string
  sortOrder: number
}
export interface CatalogApproval {
  id: number
  registryId?: number
  categoryId?: number
  actionType: string
  status: string
  submitComment?: string
  reviewComment?: string
  submittedBy?: string
  submittedAt?: string
  reviewedBy?: string
  reviewedAt?: string
  resourceCode?: string
  resourceName?: string
  publishStatus?: string
  shareType?: string
}
export interface Policy { id: number; policyCode: string; policyName: string; policyType: string; ruleExpr?: string; lifecycleStage?: string; status?: string }
export interface AssetTag {
  id: number
  tagCode: string
  tagName: string
  ruleExpr?: string
  tagDesc?: string
  hitCount: number
  status: string
  parentId?: number | null
  level?: number | null
  tagSource?: string
  stdCode?: string
  children?: AssetTag[]
}
export interface AssetTagTreeResult {
  standardTree: AssetTag[]
  customTags: AssetTag[]
}
export interface AssetTagBinding {
  id: number
  tagId: number
  assetType: 'TABLE' | 'COLUMN' | string
  assetId: number
  tagName?: string
  stdCode?: string
  assetLabel?: string
  createdAt?: string
}
export interface HealthMetric { metricLabel: string; metricValue: string; alertLevel: string }

export interface AssetCatalogReg {
  id: number
  assetName: string
  assetDesc?: string
  ownerName?: string
  contactInfo?: string
  dataTags?: string
  orgId?: number
  orgName?: string
  projectId?: number
  projectName?: string
  sourceId?: number
  systemName?: string
  tableId?: number
  tableName?: string
  accessMode?: string
  formatType?: string
  transferMode?: string
  formatLocked?: number
  bizPurpose?: string
  bizScenario?: string
  accessScope?: string
  controlReq?: string
  qualityFilePath?: string
  qualityFileName?: string
  riskFilePath?: string
  riskFileName?: string
  otherInfo?: string
  status: string
  rejectReason?: string
  reportedAt?: string
  archivedAt?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export function useIngestionLoading() {
  const loading = ref(false)
  const loadError = ref('')

  async function withLoad<T>(fn: () => Promise<T>): Promise<T | undefined> {
    loading.value = true
    loadError.value = ''
    try {
      return await fn()
    } catch (e: unknown) {
      loadError.value = e instanceof Error ? e.message : '加载失败，请确认后端已启动（9090）且已登录'
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
  assetReportProjectTables: (projectId: number) =>
    api.get<Record<string, unknown>[]>(`/exchange/ingestion/register/asset-report/projects/${projectId}/tables`),
  assetReportTableDetail: (id: number) =>
    api.get<Record<string, unknown>>(`/exchange/ingestion/register/asset-report/tables/${id}/detail`),
  assetReportScriptDetail: (id: number) =>
    api.get<Record<string, unknown>>(`/exchange/ingestion/register/asset-report/scripts/${id}/detail`),
  assetReportWorkflowDetail: (id: number) =>
    api.get<Record<string, unknown>>(`/exchange/ingestion/register/asset-report/workflows/${id}/detail`),
  assetReportWorkflowRunMonitor: (runId: number) =>
    api.get<Record<string, unknown>>(`/exchange/ingestion/register/asset-report/workflows/runs/${runId}/monitor`),
  lineage: (params?: { projectId?: number; keyword?: string; categoryTagId?: number }) =>
    api.get<{
      projectId: number
      projectName: string
      nodes: LineageGraphNode[]
      edges: LineageEdge[]
      categories: Array<{ tagId: number; tagName: string }>
      tableCount: number
      isolatedCount: number
      linkedTableCount: number
      crossDbEdgeCount: number
    }>('/exchange/ingestion/register/lineage', { params }),
  lineageDrill: (nodeId: string) =>
    api.get<{
      focusNode: string
      focusMeta: Record<string, unknown>
      focus?: LineageGraphNode
      nodes: LineageGraphNode[]
      edges: LineageEdge[]
      upstream: LineageEdge[]
      downstream: LineageEdge[]
      upstreamNodes: LineageGraphNode[]
      downstreamNodes: LineageGraphNode[]
      hasMore: boolean
    }>('/exchange/ingestion/register/lineage/drill', { params: { nodeId } }),
  fieldLineage: (tableNode: string) =>
    api.get<{
      tableNode: string
      fields: ColumnLineage[]
      fieldEdges: Array<{ from: string; to: string; direction: string }>
      focusMeta: Record<string, unknown>
    }>('/exchange/ingestion/register/lineage/fields', { params: { tableNode } }),
  lineageTableMeta: (tableNode: string) =>
    api.get<Record<string, unknown>>('/exchange/ingestion/register/lineage/table-meta', { params: { tableNode } }),
  projects: () => api.get<Project[]>('/exchange/ingestion/projects'),
  createProject: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/projects', body),
  updateProject: (id: number, body: Record<string, unknown>) => api.put<void>(`/exchange/ingestion/projects/${id}`, body),
  deleteProject: (id: number) => api.delete<void>(`/exchange/ingestion/projects/${id}`),
  systems: (projectId: number) => api.get<BizSystem[]>('/exchange/ingestion/systems', { params: { projectId } }),
  createSystem: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/systems', body),
  updateSystem: (id: number, body: Record<string, unknown>) => api.put<void>(`/exchange/ingestion/systems/${id}`, body),
  deleteSystem: (id: number) => api.delete<void>(`/exchange/ingestion/systems/${id}`),
  dataSources: (projectId?: number, systemId?: number) =>
    api.get<DataSource[]>('/exchange/ingestion/data-sources', { params: { projectId, systemId } }),
  createDataSource: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/data-sources', body),
  updateDataSource: (id: number, body: Record<string, unknown>) => api.put<void>(`/exchange/ingestion/data-sources/${id}`, body),
  deleteDataSource: (id: number) => api.delete<void>(`/exchange/ingestion/data-sources/${id}`),
  testDataSource: (id: number) => api.post<Record<string, unknown>>(`/exchange/ingestion/data-sources/${id}/test`),
  probeDataSource: (id: number) => api.post<{ sourceId: number; schema: string; tableCount: number; tables: ProbeTable[] }>(`/exchange/ingestion/data-sources/${id}/probe`),
  registerTables: (id: number, body: Record<string, unknown>) => api.post<Record<string, unknown>>(`/exchange/ingestion/data-sources/${id}/register-tables`, body),
  tables: (sourceId?: number) => api.get<DataTable[]>('/exchange/ingestion/register/tables', { params: { sourceId } }),
  createTable: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/register/tables', body),
  finalizeForwardTable: (tableId: number) =>
    api.post<Record<string, unknown>>(`/exchange/ingestion/register/tables/${tableId}/finalize-forward`),
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
  dictColumnLinks: (dictId: number) => api.get<Record<string, unknown>[]>(`/exchange/ingestion/dicts/${dictId}/column-links`),
  bindDictColumn: (dictId: number, body: Record<string, unknown>) =>
    api.post<number>(`/exchange/ingestion/dicts/${dictId}/column-links`, body),
  unbindDictColumn: (linkId: number) => api.delete<void>(`/exchange/ingestion/dicts/column-links/${linkId}`),
  tags: () => api.get<AssetTag[]>('/exchange/ingestion/register/tags'),
  tagTree: () => api.get<AssetTagTreeResult>('/exchange/ingestion/register/tags', { params: { tree: true } }),
  createTag: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/register/tags', body),
  updateTag: (id: number, body: Record<string, unknown>) => api.put<void>(`/exchange/ingestion/register/tags/${id}`, body),
  matchTags: () => api.post<Record<string, unknown>>('/exchange/ingestion/register/tags/match'),
  suggestTagRule: (id: number) => api.get<Record<string, unknown>>(`/exchange/ingestion/register/tags/${id}/suggest-rule`),
  applySuggestedTagRule: (id: number, body?: { ruleExpr?: string }) =>
    api.post<Record<string, unknown>>(`/exchange/ingestion/register/tags/${id}/apply-suggested-rule`, body || {}),
  tagBindings: (assetType: string, assetId: number) =>
    api.get<AssetTagBinding[]>('/exchange/ingestion/register/tag-bindings', { params: { assetType, assetId } }),
  tagBindingsByTag: (tagId: number) =>
    api.get<AssetTagBinding[]>('/exchange/ingestion/register/tag-bindings/by-tag', { params: { tagId } }),
  tagMatchContext: (tableId: number) =>
    api.get<{
      tableId: number
      columns: DataColumn[]
      tableTagIds: number[]
      columnTagMap: Record<string, number[]>
    }>(`/exchange/ingestion/register/tables/${tableId}/tag-match-context`),
  bindTag: (body: { tagId: number; assetType: string; assetId: number }) =>
    api.post<number>('/exchange/ingestion/register/tag-bindings', body),
  unbindTag: (body: { tagId: number; assetType: string; assetId: number }) =>
    api.delete<void>('/exchange/ingestion/register/tag-bindings', { data: body }),
  templates: () => api.get<UploadTemplate[]>('/exchange/ingestion/collect/templates'),
  createTemplate: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/collect/templates', body),
  deleteTemplate: (id: number) => api.delete<void>(`/exchange/ingestion/collect/templates/${id}`),
  updateTemplateStatus: (id: number, status: 'ACTIVE' | 'INACTIVE') =>
    api.put<void>(`/exchange/ingestion/collect/templates/${id}/status`, { status }),
  templateBindings: (templateCode: string) =>
    api.get<Array<{
      sheetName: string
      headerRow: number
      columns: string[]
      targetTable: string
      tableId?: number
      tableName?: string
      tableCode?: string
    }>>(`/exchange/ingestion/collect/templates/${encodeURIComponent(templateCode)}/bindings`),
  uploads: () => api.get<Upload[]>('/exchange/ingestion/uploads'),
  upload: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/uploads', body),
  uploadFile: (form: FormData) => api.post<number>('/exchange/ingestion/collect/uploads/file', form),
  inspectUpload: (form: FormData) =>
    api.post<{
      uploadToken: string
      fileName: string
      sheets: string[]
      suggestedSheet: string
      suggestedTable: string
    }>('/exchange/ingestion/collect/uploads/inspect', form),
  previewHeader: (body: Record<string, unknown>) =>
    api.post<{
      sheetName: string
      headerRow: number
      columns: string[]
      sampleRows: Record<string, string>[]
      suggestedTable: string
    }>('/exchange/ingestion/collect/uploads/preview-header', body),
  previewUpload: (body: Record<string, unknown>) =>
    api.post<{
      sheetName: string
      headerRow: number
      columns: string[]
      rows: Record<string, string>[]
      previewRows: number
      truncated: boolean
      targetTable: string
      writeMode: string
    }>('/exchange/ingestion/collect/uploads/preview', body),
  commitUpload: (body: Record<string, unknown>) =>
    api.post<{
      uploadId: number
      targetTable: string
      odsDatabase: string
      rowCount: number
      sheetName: string
      writeMode?: string
      tableId?: number
      committedSheets?: string[]
      remainingSheets?: string[]
      uploadToken?: string
      message: string
    }>('/exchange/ingestion/collect/uploads/commit', body),
  finishUpload: (body: Record<string, unknown>) =>
    api.post<{ message: string; ok: boolean }>('/exchange/ingestion/collect/uploads/finish', body),
  channels: (channelType?: string) => api.get<Channel[]>('/exchange/ingestion/channels', { params: { channelType } }),
  createChannel: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/channels', body),
  updateChannel: (id: number, body: Record<string, unknown>) => api.put<void>(`/exchange/ingestion/channels/${id}`, body),
  runChannel: (id: number) => api.post<Record<string, unknown>>(`/exchange/ingestion/channels/${id}/run`),
  tasks: (channelId?: number) => api.get<IngestTask[]>('/exchange/ingestion/collect/tasks', { params: { channelId } }),
  createTask: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/collect/tasks', body),
  jobs: (accessMode?: string) => api.get<IngestTask[]>('/exchange/ingestion/collect/jobs', { params: { accessMode } }),
  getJob: (id: number) => api.get<IngestTask>(`/exchange/ingestion/collect/jobs/${id}`),
  createJob: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/collect/jobs', body),
  updateJob: (id: number, body: Record<string, unknown>) => api.put<void>(`/exchange/ingestion/collect/jobs/${id}`, body),
  deleteJob: (id: number) => api.delete<void>(`/exchange/ingestion/collect/jobs/${id}`),
  runJob: (id: number) => api.post<Record<string, unknown>>(`/exchange/ingestion/collect/jobs/${id}/run`),
  resetJob: (id: number) => api.post<void>(`/exchange/ingestion/collect/jobs/${id}/reset`),
  previewJob: (body: Record<string, unknown>) => api.post<Record<string, unknown>>('/exchange/ingestion/collect/jobs/preview', body),
  mappingSuggest: (tableId: number, mode?: string) =>
    api.get<Array<{ source: string; target: string; dataType?: string; length?: number; columnName?: string }>>(
      '/exchange/ingestion/collect/jobs/mapping-suggest',
      { params: { tableId, mode } },
    ),
  pipelineJobs: (jobType?: string) => api.get<PipelineJob[]>('/exchange/ingestion/pipeline-jobs', { params: { jobType } }),
  runPipeline: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/pipeline-jobs/run', body),
  probeReports: () => api.get<ProbeReport[]>('/exchange/ingestion/collect/probe-reports'),
  definitions: () => api.get<DataDefinition[]>('/exchange/ingestion/collect/definitions'),
  saveDefinition: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/collect/definitions', body),
  reconcileLogs: () => api.get<ReconcileLog[]>('/exchange/ingestion/collect/reconcile-logs'),
  reconcile: (action: string) => api.get<Record<string, unknown>>(`/exchange/ingestion/reconcile/${action}`),
  registries: (params?: Record<string, unknown>) =>
    api.get<Registry[]>('/exchange/ingestion/registries', { params }),
  registryDetail: (id: number) => api.get<Registry>(`/exchange/ingestion/registries/${id}`),
  createRegistry: (body: Record<string, unknown>) => api.post<number>('/exchange/ingestion/registries', body),
  updateRegistry: (id: number, body: Record<string, unknown>) =>
    api.put<void>(`/exchange/ingestion/registries/${id}`, body),
  deleteRegistry: (id: number) => api.delete<void>(`/exchange/ingestion/registries/${id}`),
  batchCreateRegistry: (body: Record<string, unknown>) =>
    api.post<number[]>('/exchange/ingestion/registries/batch', body),
  importRegistries: (body: Record<string, unknown>) =>
    api.post<{ success: number; failed: number; errors: string[] }>('/exchange/ingestion/registries/import', body),
  submitPublish: (body: { ids: number[]; comment?: string }) =>
    api.post<number>('/exchange/ingestion/registries/submit-publish', body),
  submitOffline: (body: { ids: number[]; comment?: string }) =>
    api.post<number>('/exchange/ingestion/registries/submit-offline', body),
  approveRegistry: (id: number, body: Record<string, unknown>) =>
    api.post<void>(`/exchange/ingestion/registries/${id}/approve`, body),
  categories: (params?: { keyword?: string }) =>
    api.get<CategoryNode[]>('/exchange/ingestion/collect/categories', { params }),
  createCategory: (body: Record<string, unknown>) =>
    api.post<number>('/exchange/ingestion/collect/categories', body),
  updateCategory: (id: number, body: Record<string, unknown>) =>
    api.put<void>(`/exchange/ingestion/collect/categories/${id}`, body),
  deleteCategory: (id: number) => api.delete<void>(`/exchange/ingestion/collect/categories/${id}`),
  boundResources: (categoryId: number) =>
    api.get<Registry[]>(`/exchange/ingestion/collect/categories/${categoryId}/bound`),
  bindResources: (categoryId: number, ids: number[]) =>
    api.post<void>(`/exchange/ingestion/collect/categories/${categoryId}/bind`, { ids }),
  unbindResources: (ids: number[]) =>
    api.post<void>('/exchange/ingestion/collect/categories/unbind', { ids }),
  catalogApprovals: (params?: { status?: string }) =>
    api.get<CatalogApproval[]>('/exchange/ingestion/collect/approvals', { params }),
  approveCatalog: (id: number, body?: { comment?: string }) =>
    api.post<void>(`/exchange/ingestion/collect/approvals/${id}/approve`, body || {}),
  rejectCatalog: (id: number, body: { comment: string }) =>
    api.post<void>(`/exchange/ingestion/collect/approvals/${id}/reject`, body),
  batchApproveCatalog: (body: { ids: number[]; comment?: string }) =>
    api.post<{ approved: number; errors: string[] }>('/exchange/ingestion/collect/approvals/batch-approve', body),
  batchRejectCatalog: (body: { ids: number[]; comment: string }) =>
    api.post<{ rejected: number; errors: string[] }>('/exchange/ingestion/collect/approvals/batch-reject', body),
  policies: (policyType?: string) => api.get<Policy[]>('/exchange/ingestion/policies', { params: { policyType } }),
  globalView: () => api.get<Record<string, unknown>>('/exchange/ingestion/global-view'),
  health: () => api.get<HealthMetric[]>('/exchange/ingestion/health'),
  search: (q: string) => api.get<Record<string, unknown>>('/exchange/ingestion/search', { params: { q } }),
  lifecycle: (id: number) => api.post<Record<string, unknown>>(`/exchange/ingestion/policies/${id}/lifecycle`),
  backupJobs: () => api.get<Record<string, unknown>[]>('/exchange/ingestion/collect/backup-jobs'),
  archiveJobs: () => api.get<Record<string, unknown>[]>('/exchange/ingestion/collect/archive-jobs'),
  assetCatalogDefaults: () =>
    api.get<{ ownerName?: string; contactInfo?: string; orgId?: number; orgName?: string }>(
      '/exchange/ingestion/asset-catalog-regs/defaults',
    ),
  assetCatalogOrgOptions: () =>
    api.get<Array<{ id: number; orgCode?: string; orgName: string; parentId?: number; label: string }>>(
      '/exchange/ingestion/asset-catalog-regs/org-options',
    ),
  assetCatalogContacts: (orgId: number) =>
    api.get<Array<{ phone: string; displayName: string; label: string }>>(
      '/exchange/ingestion/asset-catalog-regs/contacts',
      { params: { orgId } },
    ),
  assetCatalogList: (params?: {
    assetName?: string
    orgName?: string
    projectName?: string
    status?: string
  }) => api.get<AssetCatalogReg[]>('/exchange/ingestion/asset-catalog-regs', { params }),
  assetCatalogDetail: (id: number) => api.get<AssetCatalogReg>(`/exchange/ingestion/asset-catalog-regs/${id}`),
  assetCatalogCreate: (body: Record<string, unknown>) =>
    api.post<number>('/exchange/ingestion/asset-catalog-regs', body),
  assetCatalogUpdate: (id: number, body: Record<string, unknown>) =>
    api.put<void>(`/exchange/ingestion/asset-catalog-regs/${id}`, body),
  assetCatalogDelete: (id: number) => api.delete<void>(`/exchange/ingestion/asset-catalog-regs/${id}`),
  assetCatalogReport: (id: number) => api.post<void>(`/exchange/ingestion/asset-catalog-regs/${id}/report`),
  assetCatalogReject: (id: number, body?: { reason?: string }) =>
    api.post<void>(`/exchange/ingestion/asset-catalog-regs/${id}/reject`, body || {}),
  assetCatalogArchive: (id: number) => api.post<void>(`/exchange/ingestion/asset-catalog-regs/${id}/archive`),
  assetCatalogUpload: (form: FormData) =>
    api.post<{ fileName: string; filePath: string; kind: string }>(
      '/exchange/ingestion/asset-catalog-regs/upload',
      form,
    ),
}
