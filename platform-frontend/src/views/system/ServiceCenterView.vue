<script setup lang="ts">
/**
 * 服务中心（标书演示）：服务资源 + API 版本/文档/测试 + 调用监控 + 敏感审批。
 * 服务列表/统计/审批走真实接口；版本与文档/测试记录本地持久化，便于演示操作。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api/http'
import { formatDateTime } from '@/utils/datetime'
import { statusLabel } from '@/utils/status-label'

type Svc = {
  id: number
  serviceCode?: string
  serviceName?: string
  servicePath?: string
  protocol?: string
  status?: string
  createdAt?: string
}

type ApiVersion = {
  id: string
  serviceId: number
  version: string
  changelog: string
  status: 'DRAFT' | 'PUBLISHED' | 'DEPRECATED'
  createdAt: string
  publishedAt?: string
}

type TestRecord = {
  id: string
  serviceId: number
  version: string
  method: string
  path: string
  statusCode: number
  success: boolean
  durationMs: number
  testedAt: string
  responsePreview: string
}

const STORE_KEY = 'uum.serviceCenter.apiMeta.v1'

const activeTab = ref('resources')
const loading = ref(false)
const services = ref<Svc[]>([])
const serviceStats = ref<Record<string, unknown>[]>([])
const approvals = ref<Record<string, unknown>[]>([])

const svcForm = reactive({
  serviceName: '',
  serviceCode: '',
  servicePath: '/api/v1/',
  protocol: 'REST',
  sensitive: false,
})

const applyForm = reactive({
  serviceId: undefined as number | undefined,
  reason: '',
  urgency: 'NORMAL',
})
const approvalFilter = ref('')

const selectedServiceId = ref<number | undefined>()
const apiSubTab = ref('versions')
const versions = ref<ApiVersion[]>([])
const testRecords = ref<TestRecord[]>([])
const docsByService = ref<Record<string, string>>({})

const versionDialog = ref(false)
const versionForm = reactive({
  id: '' as string,
  version: '',
  changelog: '',
  status: 'DRAFT' as ApiVersion['status'],
})

const testForm = reactive({
  method: 'GET',
  path: '',
  headers: '{\n  "Content-Type": "application/json",\n  "Authorization": "Bearer <token>"\n}',
  body: '{\n  \n}',
})
const testRunning = ref(false)
const lastTestResult = ref<{
  statusCode: number
  durationMs: number
  body: string
  success: boolean
} | null>(null)

const selectedService = computed(() => services.value.find((s) => s.id === selectedServiceId.value))

const serviceVersions = computed(() =>
  versions.value
    .filter((v) => v.serviceId === selectedServiceId.value)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
)

const serviceTests = computed(() =>
  testRecords.value
    .filter((t) => t.serviceId === selectedServiceId.value)
    .sort((a, b) => b.testedAt.localeCompare(a.testedAt))
    .slice(0, 50),
)

const filteredApprovals = computed(() => {
  if (!approvalFilter.value) return approvals.value
  return approvals.value.filter((a) => String(a.status || '') === approvalFilter.value)
})

const currentDoc = computed(() => {
  if (selectedServiceId.value == null) return ''
  return docsByService.value[String(selectedServiceId.value)] || ''
})

function uid() {
  return `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

function loadStore() {
  try {
    const raw = localStorage.getItem(STORE_KEY)
    if (!raw) return
    const data = JSON.parse(raw) as {
      versions?: ApiVersion[]
      tests?: TestRecord[]
      docs?: Record<string, string>
    }
    versions.value = data.versions || []
    testRecords.value = data.tests || []
    docsByService.value = data.docs || {}
  } catch {
    versions.value = []
    testRecords.value = []
    docsByService.value = {}
  }
}

function saveStore() {
  localStorage.setItem(
    STORE_KEY,
    JSON.stringify({
      versions: versions.value,
      tests: testRecords.value,
      docs: docsByService.value,
    }),
  )
}

function seedDemoIfEmpty() {
  if (!services.value.length || versions.value.length) return
  const first = services.value[0]
  const now = formatDateTime(new Date()) || new Date().toISOString().replace('T', ' ').slice(0, 19)
  versions.value.push(
    {
      id: uid(),
      serviceId: first.id,
      version: 'v1.0.0',
      changelog: '初始发布：基础查询接口',
      status: 'PUBLISHED',
      createdAt: now,
      publishedAt: now,
    },
    {
      id: uid(),
      serviceId: first.id,
      version: 'v1.1.0',
      changelog: '新增分页与字段筛选参数',
      status: 'DRAFT',
      createdAt: now,
    },
  )
  saveStore()
}

async function loadResources() {
  loading.value = true
  try {
    services.value = ((await api.get('/system/uum/services')).data || []) as Svc[]
    if (selectedServiceId.value == null && services.value.length) {
      selectedServiceId.value = services.value[0].id
      syncTestPath()
    }
    seedDemoIfEmpty()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载服务失败')
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    serviceStats.value = (await api.get('/system/uum/service-stats')).data || []
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载统计失败')
  }
}

async function loadApprovals() {
  try {
    approvals.value = (await api.get('/system/uum/service-approvals', {
      params: { status: approvalFilter.value || undefined },
    })).data || []
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载审批失败')
  }
}

function syncTestPath() {
  const svc = selectedService.value
  if (!svc) return
  testForm.path = String(svc.servicePath || '/api/v1/')
}

watch(selectedServiceId, () => {
  syncTestPath()
  lastTestResult.value = null
})

watch(activeTab, (t) => {
  if (t === 'resources' || t === 'api') void loadResources()
  else if (t === 'monitor') void loadStats()
  else if (t === 'approval') {
    if (!services.value.length) void loadResources()
    void loadApprovals()
  }
})

onMounted(() => {
  loadStore()
  void loadResources()
})

async function createService() {
  if (!svcForm.serviceName.trim()) {
    ElMessage.warning('请填写服务名称')
    return
  }
  try {
    await api.post('/system/uum/services', {
      serviceName: svcForm.serviceName.trim(),
      serviceCode: svcForm.serviceCode.trim() || undefined,
      servicePath: svcForm.servicePath.trim() || '/api/v1/',
      protocol: svcForm.protocol,
      status: 'ACTIVE',
    })
    ElMessage.success('服务已发布')
    svcForm.serviceName = ''
    svcForm.serviceCode = ''
    svcForm.servicePath = '/api/v1/'
    svcForm.sensitive = false
    await loadResources()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '发布失败')
  }
}

function openVersionDialog(row?: ApiVersion) {
  if (!selectedServiceId.value) {
    ElMessage.warning('请先选择服务')
    return
  }
  if (row) {
    versionForm.id = row.id
    versionForm.version = row.version
    versionForm.changelog = row.changelog
    versionForm.status = row.status
  } else {
    versionForm.id = ''
    versionForm.version = ''
    versionForm.changelog = ''
    versionForm.status = 'DRAFT'
  }
  versionDialog.value = true
}

function saveVersion() {
  if (!selectedServiceId.value) return
  if (!versionForm.version.trim()) {
    ElMessage.warning('请填写版本号，如 v1.2.0')
    return
  }
  const now = formatDateTime(new Date()) || new Date().toISOString().replace('T', ' ').slice(0, 19)
  if (versionForm.id) {
    const hit = versions.value.find((v) => v.id === versionForm.id)
    if (hit) {
      hit.version = versionForm.version.trim()
      hit.changelog = versionForm.changelog.trim()
      hit.status = versionForm.status
      if (versionForm.status === 'PUBLISHED' && !hit.publishedAt) hit.publishedAt = now
    }
  } else {
    const dup = versions.value.some(
      (v) => v.serviceId === selectedServiceId.value && v.version === versionForm.version.trim(),
    )
    if (dup) {
      ElMessage.warning('该版本号已存在')
      return
    }
    versions.value.push({
      id: uid(),
      serviceId: selectedServiceId.value,
      version: versionForm.version.trim(),
      changelog: versionForm.changelog.trim(),
      status: versionForm.status,
      createdAt: now,
      publishedAt: versionForm.status === 'PUBLISHED' ? now : undefined,
    })
  }
  saveStore()
  versionDialog.value = false
  ElMessage.success('版本已保存')
}

async function removeVersion(row: ApiVersion) {
  await ElMessageBox.confirm(`确认删除版本 ${row.version}？`, '删除确认', { type: 'warning' })
  versions.value = versions.value.filter((v) => v.id !== row.id)
  saveStore()
  ElMessage.success('已删除')
}

function publishVersion(row: ApiVersion) {
  const now = formatDateTime(new Date()) || new Date().toISOString().replace('T', ' ').slice(0, 19)
  row.status = 'PUBLISHED'
  row.publishedAt = now
  saveStore()
  ElMessage.success(`版本 ${row.version} 已发布`)
}

function deprecateVersion(row: ApiVersion) {
  row.status = 'DEPRECATED'
  saveStore()
  ElMessage.success(`版本 ${row.version} 已标记废弃`)
}

function buildOpenApiDoc(): string {
  const svc = selectedService.value
  if (!svc) return ''
  const vers = serviceVersions.value
  const latest = vers.find((v) => v.status === 'PUBLISHED') || vers[0]
  const path = String(svc.servicePath || '/api/v1/')
  const lines = [
    `# ${svc.serviceName || svc.serviceCode} API 文档`,
    '',
    `> 自动生成时间：${formatDateTime(new Date()) || ''}`,
    `> 服务编码：\`${svc.serviceCode || '-'}\`　协议：\`${svc.protocol || 'REST'}\``,
    `> 当前版本：\`${latest?.version || '未发布'}\``,
    '',
    '## 概述',
    '',
    `本接口由统一用户管理系统 · 服务中心对外提供，路径前缀：\`${path}\`。`,
    '',
    '## 版本历史',
    '',
    '| 版本 | 状态 | 变更说明 | 发布时间 |',
    '| --- | --- | --- | --- |',
    ...vers.map(
      (v) =>
        `| ${v.version} | ${statusLabel(v.status)} | ${v.changelog || '-'} | ${v.publishedAt || v.createdAt} |`,
    ),
    '',
    '## 接口说明',
    '',
    '### GET 查询',
    '',
    '```http',
    `GET ${path}`,
    'Authorization: Bearer <access_token>',
    '```',
    '',
    '**响应示例**',
    '',
    '```json',
    '{',
    '  "code": 0,',
    '  "message": "ok",',
    '  "data": []',
    '}',
    '```',
    '',
    '### POST 提交',
    '',
    '```http',
    `POST ${path}`,
    'Content-Type: application/json',
    'Authorization: Bearer <access_token>',
    '',
    '{ "payload": {} }',
    '```',
    '',
    '## 错误码',
    '',
    '| 码 | 说明 |',
    '| --- | --- |',
    '| 0 | 成功 |',
    '| 400 | 参数错误 |',
    '| 401 | 未认证 |',
    '| 403 | 无权限（敏感接口需审批） |',
    '| 500 | 服务异常 |',
    '',
  ]
  return lines.join('\n')
}

function generateDoc() {
  if (!selectedServiceId.value) {
    ElMessage.warning('请先选择服务')
    return
  }
  const doc = buildOpenApiDoc()
  docsByService.value[String(selectedServiceId.value)] = doc
  saveStore()
  apiSubTab.value = 'docs'
  ElMessage.success('接口文档已生成')
}

async function copyDoc() {
  const text = currentDoc.value
  if (!text) {
    ElMessage.warning('请先生成文档')
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('文档已复制到剪贴板')
  } catch {
    ElMessage.warning('复制失败，请手动选择文本复制')
  }
}

function downloadDoc() {
  const text = currentDoc.value
  if (!text) {
    ElMessage.warning('请先生成文档')
    return
  }
  const name = `${selectedService.value?.serviceCode || 'api'}-doc.md`
  const blob = new Blob([text], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  URL.revokeObjectURL(url)
}

function runApiTest() {
  if (!selectedServiceId.value) {
    ElMessage.warning('请先选择服务')
    return
  }
  if (!testForm.path.trim()) {
    ElMessage.warning('请填写请求路径')
    return
  }
  try {
    JSON.parse(testForm.headers || '{}')
  } catch {
    ElMessage.warning('请求头须为合法 JSON')
    return
  }
  if (testForm.method !== 'GET' && testForm.method !== 'DELETE') {
    try {
      JSON.parse(testForm.body || '{}')
    } catch {
      ElMessage.warning('请求体须为合法 JSON')
      return
    }
  }

  testRunning.value = true
  const start = Date.now()
  // 演示：模拟网关调用结果（非真实外呼），便于验收操作闭环
  window.setTimeout(() => {
    const durationMs = 80 + Math.floor(Math.random() * 220)
    const ok = Math.random() > 0.12
    const statusCode = ok ? 200 : (Math.random() > 0.5 ? 403 : 500)
    const body = ok
      ? JSON.stringify(
          {
            code: 0,
            message: 'ok',
            data: {
              serviceId: selectedServiceId.value,
              path: testForm.path,
              method: testForm.method,
              demo: true,
              note: '演示响应：未实际外呼第三方，仅供服务中心联调验收',
            },
          },
          null,
          2,
        )
      : JSON.stringify(
          {
            code: statusCode === 403 ? 403 : 500,
            message: statusCode === 403 ? '敏感接口未审批通过' : '演示失败：下游超时',
          },
          null,
          2,
        )
    const result = { statusCode, durationMs, body, success: ok }
    lastTestResult.value = result
    const now = formatDateTime(new Date()) || new Date().toISOString().replace('T', ' ').slice(0, 19)
    const latest = serviceVersions.value.find((v) => v.status === 'PUBLISHED')
    testRecords.value.unshift({
      id: uid(),
      serviceId: selectedServiceId.value!,
      version: latest?.version || '-',
      method: testForm.method,
      path: testForm.path.trim(),
      statusCode,
      success: ok,
      durationMs,
      testedAt: now,
      responsePreview: body.slice(0, 240),
    })
    saveStore()
    testRunning.value = false
    if (ok) ElMessage.success(`测试完成（${durationMs}ms）`)
    else ElMessage.warning(`测试返回 ${statusCode}（演示）`)
    void start
  }, 320)
}

async function applyService() {
  if (applyForm.serviceId == null) {
    ElMessage.warning('请选择服务')
    return
  }
  if (!applyForm.reason.trim()) {
    ElMessage.warning('请填写申请原因')
    return
  }
  try {
    await api.post('/system/uum/service-approvals', {
      serviceId: applyForm.serviceId,
      reason: `[${applyForm.urgency === 'URGENT' ? '加急' : '普通'}] ${applyForm.reason.trim()}`,
    })
    ElMessage.success('调用申请已提交，等待审批')
    applyForm.reason = ''
    applyForm.urgency = 'NORMAL'
    await loadApprovals()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '提交失败')
  }
}

async function decide(id: number, pass: boolean) {
  try {
    await api.post(`/system/uum/service-approvals/${id}/${pass ? 'approve' : 'reject'}`, {
      comment: pass ? '同意调用' : '驳回申请',
    })
    ElMessage.success(pass ? '已批准' : '已拒绝')
    await loadApprovals()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}

function protocolLabel(p?: string) {
  if (!p) return '—'
  const map: Record<string, string> = { REST: 'REST', SOAP: 'SOAP', GRPC: 'gRPC', HTTP: 'HTTP' }
  return map[p] || p
}

function successRate(row: Record<string, unknown>) {
  const total = Number(row.callCount || 0)
  const ok = Number(row.successCount || 0)
  if (!total) return '—'
  return `${Math.round((ok * 1000) / total) / 10}%`
}
</script>

<template>
  <div v-loading="loading" class="svc-center">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="服务资源" name="resources">
        <p class="hint">统一登记对外服务资源，管理接口路径与协议，供开发人员发现与调用。</p>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="服务名" class="portal-field-lg">
            <el-input v-model="svcForm.serviceName" placeholder="如：人口核验服务" />
          </el-form-item>
          <el-form-item label="编码" class="portal-field-md">
            <el-input v-model="svcForm.serviceCode" placeholder="可空自动生成" />
          </el-form-item>
          <el-form-item label="路径" class="portal-field-xl">
            <el-input v-model="svcForm.servicePath" placeholder="/api/v1/..." />
          </el-form-item>
          <el-form-item label="协议" class="portal-field-sm">
            <el-select v-model="svcForm.protocol">
              <el-option label="REST" value="REST" />
              <el-option label="HTTP" value="HTTP" />
              <el-option label="SOAP" value="SOAP" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="createService">发布服务</el-button>
            <el-button @click="loadResources">刷新</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="services" stripe border class="portal-table">
          <el-table-column prop="serviceCode" label="编码" width="140" show-overflow-tooltip />
          <el-table-column prop="serviceName" label="名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="servicePath" label="路径" min-width="200" show-overflow-tooltip />
          <el-table-column label="协议" width="90">
            <template #default="{ row }">{{ protocolLabel(row.protocol) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                @click="selectedServiceId = row.id; activeTab = 'api'; apiSubTab = 'versions'"
              >API 管理</el-button>
              <el-button
                link
                type="primary"
                @click="applyForm.serviceId = row.id; activeTab = 'approval'"
              >申请调用</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="API 管理" name="api">
        <p class="hint">对提供应用程序编程接口的服务资源，支持版本管理、文档生成与接口测试，方便开发人员调用。</p>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="服务" class="portal-field-xl">
            <el-select v-model="selectedServiceId" filterable placeholder="请选择服务">
              <el-option
                v-for="s in services"
                :key="s.id"
                :label="`${s.serviceName}（${s.serviceCode || s.id}）`"
                :value="s.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" :disabled="!selectedServiceId" @click="generateDoc">生成文档</el-button>
          </el-form-item>
        </el-form>

        <el-tabs v-if="selectedServiceId" v-model="apiSubTab" type="card" class="api-sub">
          <el-tab-pane label="版本管理" name="versions">
            <div class="toolbar">
              <el-button type="primary" size="small" @click="openVersionDialog()">新增版本</el-button>
            </div>
            <el-table :data="serviceVersions" stripe border class="portal-table">
              <el-table-column prop="version" label="版本号" width="120" />
              <el-table-column prop="changelog" label="变更说明" min-width="220" show-overflow-tooltip />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">{{ statusLabel(row.status) }}</template>
              </el-table-column>
              <el-table-column label="创建时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="发布时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.publishedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="220" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openVersionDialog(row)">编辑</el-button>
                  <el-button
                    v-if="row.status !== 'PUBLISHED'"
                    link
                    type="primary"
                    @click="publishVersion(row)"
                  >发布</el-button>
                  <el-button
                    v-if="row.status === 'PUBLISHED'"
                    link
                    type="warning"
                    @click="deprecateVersion(row)"
                  >废弃</el-button>
                  <el-button link type="danger" @click="removeVersion(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="接口文档" name="docs">
            <div class="toolbar">
              <el-button type="primary" size="small" @click="generateDoc">重新生成</el-button>
              <el-button size="small" @click="copyDoc">复制</el-button>
              <el-button size="small" @click="downloadDoc">下载 Markdown</el-button>
            </div>
            <el-input
              v-if="currentDoc"
              type="textarea"
              :model-value="currentDoc"
              :rows="22"
              readonly
              class="doc-area"
            />
            <el-empty v-else description="尚未生成文档，请点击「生成文档」" />
          </el-tab-pane>

          <el-tab-pane label="接口测试" name="test">
            <el-form label-width="90px" style="max-width:860px">
              <el-form-item label="方法">
                <el-select v-model="testForm.method" style="width:140px">
                  <el-option label="GET" value="GET" />
                  <el-option label="POST" value="POST" />
                  <el-option label="PUT" value="PUT" />
                  <el-option label="DELETE" value="DELETE" />
                </el-select>
              </el-form-item>
              <el-form-item label="路径">
                <el-input v-model="testForm.path" placeholder="/api/v1/..." />
              </el-form-item>
              <el-form-item label="请求头">
                <el-input v-model="testForm.headers" type="textarea" :rows="4" />
              </el-form-item>
              <el-form-item v-if="testForm.method !== 'GET' && testForm.method !== 'DELETE'" label="请求体">
                <el-input v-model="testForm.body" type="textarea" :rows="6" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="testRunning" @click="runApiTest">发送测试</el-button>
                <span class="form-hint">演示环境返回模拟响应，不发起真实外呼</span>
              </el-form-item>
            </el-form>
            <div v-if="lastTestResult" class="test-result">
              <h4>
                最近一次结果：
                <el-tag :type="lastTestResult.success ? 'success' : 'danger'" size="small">
                  HTTP {{ lastTestResult.statusCode }}
                </el-tag>
                <span class="muted"> · {{ lastTestResult.durationMs }} ms</span>
              </h4>
              <el-input :model-value="lastTestResult.body" type="textarea" :rows="10" readonly />
            </div>
            <h4>测试记录</h4>
            <el-table :data="serviceTests" stripe border class="portal-table">
              <el-table-column prop="method" label="方法" width="80" />
              <el-table-column prop="path" label="路径" min-width="180" show-overflow-tooltip />
              <el-table-column prop="version" label="版本" width="100" />
              <el-table-column label="结果" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.statusCode }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
              <el-table-column label="测试时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.testedAt) }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
        <el-empty v-else description="请先选择或发布一个服务" />
      </el-tab-pane>

      <el-tab-pane label="调用监控" name="monitor">
        <p class="hint">记录服务调用次数、成功率等，监控运行状态与稳定性。</p>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="loadStats">刷新统计</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="serviceStats" stripe border class="portal-table">
          <el-table-column prop="serviceName" label="服务" min-width="160" show-overflow-tooltip />
          <el-table-column prop="callDate" label="日期" width="120" />
          <el-table-column prop="callCount" label="调用次数" width="110" />
          <el-table-column prop="successCount" label="成功" width="90" />
          <el-table-column prop="failCount" label="失败" width="90" />
          <el-table-column label="成功率" width="100">
            <template #default="{ row }">{{ successRate(row) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!serviceStats.length" description="暂无调用统计，服务被调用后将在此展示" />
      </el-tab-pane>

      <el-tab-pane label="调用审批" name="approval">
        <p class="hint">敏感或需审批的服务调用可提交申请，审批人员审核通过后方可调用。</p>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="服务" class="portal-field-xl">
            <el-select v-model="applyForm.serviceId" filterable clearable placeholder="全部 / 请选择">
              <el-option
                v-for="s in services"
                :key="s.id"
                :label="String(s.serviceName)"
                :value="s.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="紧急度" class="portal-field-md">
            <el-select v-model="applyForm.urgency">
              <el-option label="普通" value="NORMAL" />
              <el-option label="加急" value="URGENT" />
            </el-select>
          </el-form-item>
          <el-form-item label="原因" class="portal-field-xl">
            <el-input v-model="applyForm.reason" placeholder="说明调用用途与数据范围" />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="applyService">提交申请</el-button>
          </el-form-item>
        </el-form>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="状态" class="portal-field-md">
            <el-select v-model="approvalFilter" clearable placeholder="全部" @change="loadApprovals">
              <el-option label="待审核" value="PENDING" />
              <el-option label="已审核" value="APPROVED" />
              <el-option label="已拒绝" value="REJECTED" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button @click="approvalFilter = ''; loadApprovals()">重置</el-button>
            <el-button @click="loadApprovals">刷新</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="filteredApprovals" stripe border class="portal-table">
          <el-table-column prop="serviceName" label="服务" min-width="140" show-overflow-tooltip />
          <el-table-column prop="applicantName" label="申请人" width="120" />
          <el-table-column prop="reason" label="原因" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column label="申请时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="审批时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.approvedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 'PENDING'">
                <el-button link type="primary" @click="decide(row.id as number, true)">批准</el-button>
                <el-button link type="danger" @click="decide(row.id as number, false)">拒绝</el-button>
              </template>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="versionDialog" :title="versionForm.id ? '编辑版本' : '新增版本'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="版本号" required>
          <el-input v-model="versionForm.version" placeholder="如 v1.2.0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="versionForm.status" style="width:100%">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已废弃" value="DEPRECATED" />
          </el-select>
        </el-form-item>
        <el-form-item label="变更说明">
          <el-input v-model="versionForm.changelog" type="textarea" :rows="4" placeholder="本次版本变更内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="versionDialog = false">取消</el-button>
        <el-button type="primary" @click="saveVersion">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.hint {
  color: var(--el-text-color-secondary);
  margin: 0 0 12px;
  line-height: 1.5;
}
.form-hint {
  margin-left: 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.toolbar {
  margin-bottom: 10px;
  display: flex;
  gap: 8px;
}
.api-sub {
  margin-top: 4px;
}
.doc-area :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  line-height: 1.45;
}
.test-result {
  margin: 8px 0 16px;
}
.test-result h4 {
  margin: 0 0 8px;
  font-weight: 600;
}
</style>
