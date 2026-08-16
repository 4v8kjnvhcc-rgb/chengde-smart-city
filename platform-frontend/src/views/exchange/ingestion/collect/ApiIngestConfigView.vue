<script setup lang="ts">
/**
 * API 接口数据接入：接口配置 + 在线调试预览 + 同页任务管理。
 */
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import IngestChannelTaskDialog from './IngestChannelTaskDialog.vue'
import { ingestionApi, type Channel } from '../useIngestionHub'

type ApiParam = { name: string; type: string; required: boolean; value: string }

const saveBusy = ref(false)
const debugBusy = ref(false)
const taskDlg = ref(false)
const editingId = ref<number | undefined>()
const debugStatus = ref<'idle' | 'ok' | 'fail'>('idle')
const debugText = ref('// 点击「在线调试」发起请求，此处将展示解析后的出参结构\n// 支持单条数据与数组数据，可解析 JSON / XML')
const debugMeta = ref('')
const parsedFields = ref<string[]>([])

const form = reactive({
  channelName: '',
  method: 'GET',
  url: '',
  protocol: 'HTTPS',
  dataFormat: 'JSON',
  retryCount: '3',
  retryInterval: '30',
  pageNoParam: 'pageNo',
  pageSizeParam: 'pageSize',
  pageSize: '100',
  faultTolerance: true,
  targetDs: 'dap-hive-warehouse',
  targetDb: 'ods',
  targetTable: '',
  writeMode: 'APPEND',
  authHeader: '',
})

const params = ref<ApiParam[]>([
  { name: 'orderId', type: 'String', required: true, value: '' },
])

function resetForm(ch?: Channel) {
  editingId.value = ch?.id
  let cfg: Record<string, unknown> = {}
  if (ch?.configJson) {
    try {
      cfg = JSON.parse(ch.configJson) as Record<string, unknown>
    } catch {
      cfg = {}
    }
  }
  const s = (k: string, d = '') => String(cfg[k] ?? d)
  form.channelName = ch?.channelName || ''
  form.method = s('method', 'GET').toUpperCase()
  form.url = s('url')
  form.protocol = s('protocol', 'HTTPS')
  form.dataFormat = s('dataFormat', 'JSON')
  form.retryCount = s('retryCount', '3')
  form.retryInterval = s('retryInterval', '30')
  form.pageNoParam = s('pageNoParam', 'pageNo')
  form.pageSizeParam = s('pageSizeParam', 'pageSize')
  form.pageSize = s('pageSize', '100')
  form.faultTolerance = cfg.faultTolerance !== false && cfg.faultTolerance !== 'false'
  form.targetDs = s('targetDs', 'dap-hive-warehouse')
  form.targetDb = s('targetDb', 'ods')
  form.targetTable = s('targetTable')
  form.writeMode = s('writeMode', 'APPEND')
  form.authHeader = s('authHeader')
  if (Array.isArray(cfg.params)) {
    params.value = (cfg.params as ApiParam[]).map((p) => ({
      name: String(p.name || ''),
      type: String(p.type || 'String'),
      required: !!p.required,
      value: String(p.value ?? ''),
    }))
  } else {
    params.value = [{ name: '', type: 'String', required: false, value: '' }]
  }
  debugStatus.value = 'idle'
  debugText.value = '// 点击「在线调试」发起请求，此处将展示解析后的出参结构'
  debugMeta.value = ''
  parsedFields.value = []
}

function addParam() {
  params.value.push({ name: '', type: 'String', required: false, value: '' })
}

function removeParam(i: number) {
  params.value.splice(i, 1)
}

function validate(): boolean {
  if (!form.channelName.trim()) {
    ElMessage.warning('请填写接口名称')
    return false
  }
  if (!form.url.trim()) {
    ElMessage.warning('请填写接口地址')
    return false
  }
  if (!form.targetTable.trim()) {
    ElMessage.warning('请填写目标表名')
    return false
  }
  return true
}

function buildConfig(): Record<string, unknown> {
  return {
    method: form.method,
    url: form.url.trim(),
    protocol: form.protocol,
    dataFormat: form.dataFormat,
    retryCount: form.retryCount,
    retryInterval: form.retryInterval,
    pageNoParam: form.pageNoParam,
    pageSizeParam: form.pageSizeParam,
    pageSize: form.pageSize,
    faultTolerance: form.faultTolerance,
    authHeader: form.authHeader,
    params: params.value.filter((p) => p.name.trim()),
    targetDs: form.targetDs,
    targetDb: form.targetDb,
    targetTable: form.targetTable.trim(),
    writeMode: form.writeMode,
  }
}

async function startDebug() {
  if (!form.url.trim()) {
    ElMessage.warning('请先填写接口地址')
    return
  }
  debugBusy.value = true
  debugStatus.value = 'idle'
  debugMeta.value = ''
  try {
    // 真实拉取由后端通道执行；此处做配置级校验预览（不伪造业务成功入库）
    const q = params.value
      .filter((p) => p.name.trim())
      .map((p) => `${encodeURIComponent(p.name)}=${encodeURIComponent(p.value)}`)
      .join('&')
    const previewUrl = q ? `${form.url.trim()}${form.url.includes('?') ? '&' : '?'}${q}` : form.url.trim()
    const started = Date.now()
    let text = ''
    let ok = false
    try {
      const ctrl = new AbortController()
      const timer = setTimeout(() => ctrl.abort(), 8000)
      const res = await fetch(previewUrl, {
        method: form.method === 'POST' ? 'POST' : 'GET',
        headers: form.authHeader
          ? { Authorization: form.authHeader, Accept: 'application/json' }
          : { Accept: 'application/json' },
        signal: ctrl.signal,
        mode: 'cors',
      })
      clearTimeout(timer)
      text = await res.text()
      ok = res.ok
      debugMeta.value = `HTTP ${res.status} · ${Date.now() - started} ms · ${form.dataFormat}`
    } catch (e: unknown) {
      ok = false
      text = e instanceof Error ? e.message : String(e)
      debugMeta.value = `浏览器直连失败（常见于跨域限制）。配置仍可保存，正式采集由服务端执行。 · ${Date.now() - started} ms`
    }
    try {
      const j = JSON.parse(text) as unknown
      debugText.value = JSON.stringify(j, null, 2)
      parsedFields.value = extractFields(j)
      debugStatus.value = ok ? 'ok' : 'fail'
    } catch {
      debugText.value = text.slice(0, 4000) || '(空响应)'
      parsedFields.value = []
      debugStatus.value = ok ? 'ok' : 'fail'
    }
    if (ok) ElMessage.success('调试请求已完成')
    else ElMessage.warning('调试未成功，请检查地址/跨域或保存后由服务端执行')
  } finally {
    debugBusy.value = false
  }
}

function extractFields(data: unknown, prefix = '', out: string[] = []): string[] {
  if (data == null) return out
  if (Array.isArray(data)) {
    if (data.length) extractFields(data[0], prefix, out)
    return out
  }
  if (typeof data === 'object') {
    for (const [k, v] of Object.entries(data as Record<string, unknown>)) {
      const path = prefix ? `${prefix}.${k}` : k
      if (v != null && typeof v === 'object' && !Array.isArray(v)) extractFields(v, path, out)
      else out.push(path)
    }
  }
  return out
}

async function saveChannel(andRun = false) {
  if (!validate()) return
  saveBusy.value = true
  try {
    const body = {
      channelName: form.channelName.trim(),
      channelType: 'API',
      config: buildConfig(),
    }
    let id = editingId.value
    if (id) {
      await ingestionApi.updateChannel(id, body)
      ElMessage.success(andRun ? '已保存' : '草稿已保存')
    } else {
      id = Number((await ingestionApi.createChannel(body)).data)
      editingId.value = id
      ElMessage.success('接入任务已创建')
    }
    if (andRun && id) {
      const res = await ingestionApi.runChannel(id)
      ElMessage.success(String(res.data?.message || '已启动执行'))
    }
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saveBusy.value = false
  }
}

function onEditFromTasks(ch: Channel) {
  resetForm(ch)
}
</script>

<template>
  <div class="api-ingest">
    <div class="page-head">
      <div>
        <h2 class="page-title">API 数据接入</h2>
        <p class="page-desc">
          支持 REST 协议（GET / POST），提供接口参数解析、在线测试预览、错误重试与分页配置，出参支持 JSON / XML 解析。
        </p>
      </div>
      <el-button type="primary" plain @click="taskDlg = true">任务管理</el-button>
    </div>

    <div class="grid-2">
      <PageCard>
        <div class="section-title">接口基础信息</div>
        <el-form label-width="120px" class="cfg-form">
          <el-form-item label="接口名称" required>
            <el-input v-model="form.channelName" placeholder="接口名称" maxlength="80" />
          </el-form-item>
          <el-form-item label="接口地址" required>
            <div class="url-row">
              <el-select v-model="form.method" style="width:100px">
                <el-option label="GET" value="GET" />
                <el-option label="POST" value="POST" />
                <el-option label="PUT" value="PUT" />
              </el-select>
              <el-input v-model="form.url" placeholder="https://api.example.com/path" />
            </div>
          </el-form-item>
          <el-form-item label="接口协议" required>
            <el-select v-model="form.protocol" style="width:100%">
              <el-option label="REST · HTTPS" value="HTTPS" />
              <el-option label="REST · HTTP" value="HTTP" />
            </el-select>
          </el-form-item>
          <el-form-item label="数据格式">
            <el-select v-model="form.dataFormat" style="width:100%">
              <el-option label="JSON" value="JSON" />
              <el-option label="XML" value="XML" />
            </el-select>
          </el-form-item>
          <el-form-item label="错误重试次数" required>
            <el-input v-model="form.retryCount">
              <template #append>次</template>
            </el-input>
          </el-form-item>
          <el-form-item label="错误重试间隔">
            <el-input v-model="form.retryInterval">
              <template #append>秒</template>
            </el-input>
          </el-form-item>
          <el-form-item label="鉴权头">
            <el-input v-model="form.authHeader" placeholder="可选，如 Bearer token" />
          </el-form-item>
          <el-form-item label="分页配置">
            <div class="page-grid">
              <el-input v-model="form.pageNoParam" placeholder="页码参数名" />
              <el-input v-model="form.pageSizeParam" placeholder="每页大小参数名" />
              <el-input v-model="form.pageSize" placeholder="每页大小" />
            </div>
          </el-form-item>
        </el-form>

        <div class="section-title">请求参数（Query / Body）</div>
        <el-table :data="params" border size="small" class="portal-table">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column label="参数名" min-width="120">
            <template #default="{ row }"><el-input v-model="row.name" /></template>
          </el-table-column>
          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              <el-select v-model="row.type">
                <el-option label="String" value="String" />
                <el-option label="Integer" value="Integer" />
                <el-option label="Long" value="Long" />
                <el-option label="Boolean" value="Boolean" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="是否必填" width="100">
            <template #default="{ row }">
              <el-select v-model="row.required">
                <el-option label="必填" :value="true" />
                <el-option label="选填" :value="false" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="参数值" min-width="120">
            <template #default="{ row }"><el-input v-model="row.value" /></template>
          </el-table-column>
          <el-table-column label="操作" width="70" fixed="right">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeParam($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button class="add-btn" @click="addParam">＋ 添加参数</el-button>

        <el-divider />
        <el-form-item label="请求容错机制" label-width="120px">
          <el-switch v-model="form.faultTolerance" />
          <span class="hint">开启后按重试规则自动重试，连续失败将标记任务失败</span>
        </el-form-item>
        <el-button type="primary" style="width:100%" :loading="debugBusy" @click="startDebug">
          在线调试 · 实时验证接口
        </el-button>
        <p class="muted">调试通过后，请配置数据去向并保存；正式采集由服务端执行。</p>

        <el-divider />
        <div class="section-title">数据去向配置</div>
        <el-form label-width="120px" class="cfg-form">
          <el-form-item label="目标数据源" required>
            <el-input v-model="form.targetDs" />
          </el-form-item>
          <el-form-item label="目标数据库">
            <el-select v-model="form.targetDb" style="width:100%">
              <el-option label="ods" value="ods" />
              <el-option label="dwd" value="dwd" />
              <el-option label="dws" value="dws" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标表名" required>
            <el-input v-model="form.targetTable" placeholder="目标表名" />
          </el-form-item>
          <el-form-item label="写入模式">
            <el-select v-model="form.writeMode" style="width:100%">
              <el-option label="追加写入 (INSERT)" value="APPEND" />
              <el-option label="UPSERT 更新写入" value="UPSERT" />
            </el-select>
          </el-form-item>
        </el-form>
        <div class="actions">
          <el-button :loading="saveBusy" @click="saveChannel(false)">保存草稿</el-button>
          <el-button type="success" :loading="saveBusy" @click="saveChannel(true)">保存并启动</el-button>
        </div>
      </PageCard>

      <div class="right-col">
        <PageCard>
          <div class="preview-head">
            <h3>调试响应预览</h3>
            <el-tag v-if="debugStatus === 'idle'" type="info" size="small">等待调试</el-tag>
            <el-tag v-else-if="debugStatus === 'ok'" type="success" size="small">调试成功</el-tag>
            <el-tag v-else type="danger" size="small">调试失败</el-tag>
          </div>
          <pre class="code-block">{{ debugText }}</pre>
          <p v-if="debugMeta" class="meta">{{ debugMeta }}</p>
        </PageCard>
        <PageCard>
          <h3 class="side-title">解析结果结构</h3>
          <div v-if="!parsedFields.length" class="empty-struct">调试成功后将自动解析出参字段</div>
          <ul v-else class="field-list">
            <li v-for="f in parsedFields" :key="f">{{ f }}</li>
          </ul>
        </PageCard>
        <PageCard>
          <h3 class="side-title">接入流程</h3>
          <ol class="flow-list">
            <li>配置接口信息（地址、协议、重试等）</li>
            <li>在线调试验证</li>
            <li>配置数据去向参数</li>
            <li>出参字段到目标表字段映射（可在保存后于任务管理中维护）</li>
            <li>保存并启动（由服务端采集写入大数据平台）</li>
          </ol>
        </PageCard>
      </div>
    </div>

    <IngestChannelTaskDialog
      v-model="taskDlg"
      channel-type="API"
      title="API 接口数据接入 · 任务管理"
      @edit="onEditFromTasks"
    />
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.page-title { margin: 0; font-size: 18px; font-weight: 700; color: #303133; }
.page-desc { margin: 6px 0 0; font-size: 13px; color: #909399; line-height: 1.6; max-width: 720px; }
.grid-2 {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(280px, 2fr);
  gap: 16px;
  align-items: start;
}
@media (max-width: 1100px) {
  .grid-2 { grid-template-columns: 1fr; }
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 14px;
  padding-left: 10px;
  border-left: 4px solid var(--el-color-primary);
}
.url-row { display: flex; gap: 8px; width: 100%; }
.page-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 8px; width: 100%; }
.add-btn { margin-top: 8px; }
.hint { margin-left: 10px; font-size: 12px; color: #909399; }
.muted { font-size: 12px; color: #909399; margin: 8px 0 0; }
.actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
.right-col { display: flex; flex-direction: column; gap: 16px; }
.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.preview-head h3, .side-title { margin: 0 0 10px; font-size: 15px; }
.code-block {
  margin: 0;
  padding: 12px;
  max-height: 240px;
  overflow: auto;
  background: #1e293b;
  color: #e2e8f0;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-all;
}
.meta { margin: 8px 0 0; font-size: 12px; color: #909399; }
.empty-struct {
  padding: 24px 12px;
  text-align: center;
  color: #909399;
  font-size: 13px;
  background: #f8fafc;
  border-radius: 8px;
}
.field-list { margin: 0; padding-left: 18px; font-size: 13px; color: #475569; }
.flow-list { margin: 0; padding-left: 18px; font-size: 13px; color: #475569; line-height: 1.8; }
</style>
