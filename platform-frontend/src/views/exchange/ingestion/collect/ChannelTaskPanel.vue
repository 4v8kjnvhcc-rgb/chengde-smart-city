<script setup lang="ts">
/**
 * FTP / 本地目录等通道任务面板（非演示假成功）。
 * LOCAL：引导走真实「手动上传」落 ODS；FTP：保存真实连接配置，执行前校验必填项。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api/http'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'
import { formatDateTime } from '@/utils/datetime'
import { ingestionApi, type Channel } from '../useIngestionHub'
import { fetchDataSourceTableNames } from '@/utils/layer-tables'

export interface ConfigField {
  key: string
  label: string
  defaultValue?: string
  hint?: string
  secret?: boolean
  required?: boolean
}

interface RelatedDoc {
  id: number
  title?: string
  originalFileName?: string
  contentType?: string
  storageKey?: string
  sourceType?: string
  landed?: boolean
}

const props = defineProps<{
  title: string
  channelType: string
  configFields: ConfigField[]
}>()

const emit = defineEmits<{
  /** 本地上传引导到真实 Excel/CSV 落库页 */
  goManualUpload: []
}>()

const channels = ref<Channel[]>([])
const loading = ref(false)
const runBusy = ref(false)
const saveBusy = ref(false)
const fileBusy = ref(false)
const dialogVisible = ref(false)
const filePickVisible = ref(false)
const filePickMode = ref<'preview' | 'download'>('preview')
const relatedDocs = ref<RelatedDoc[]>([])
const editingId = ref<number | undefined>()
const channelName = ref('')
const channelForm = reactive<Record<string, string>>({})
const queryKeyword = ref('')
const tableOptions = ref<string[]>([])
const tablesLoading = ref(false)

const isFtp = computed(() => props.channelType === 'FTP')
const isLocal = computed(() => props.channelType === 'LOCAL')
const isUnstruct = computed(() => props.channelType === 'UNSTRUCT')

const dialogTitle = computed(() => {
  if (editingId.value) return isFtp.value ? '编辑远程接入' : '编辑本地目录接入'
  return isFtp.value ? '新建远程接入' : '新建本地目录接入'
})

const opColWidth = computed(() => (isUnstruct.value ? 320 : 220))

const filteredChannels = computed(() => {
  const kw = queryKeyword.value.trim().toLowerCase()
  if (!kw) return channels.value
  return channels.value.filter((c) => {
    const cfg = parseConfig(c.configJson)
    const blob = [c.channelName, c.channelCode, cfg.host, cfg.remotePath, cfg.localPath]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
    return blob.includes(kw)
  })
})

function parseConfig(json?: string): Record<string, string> {
  if (!json) return {}
  try {
    const o = JSON.parse(json) as Record<string, unknown>
    const out: Record<string, string> = {}
    for (const [k, v] of Object.entries(o)) out[k] = String(v ?? '')
    return out
  } catch {
    return {}
  }
}

function cfgSummary(row: Channel): string {
  const cfg = parseConfig(row.configJson)
  if (isFtp.value) {
    const host = cfg.host || '—'
    const port = cfg.port || '21'
    const path = cfg.remotePath || '/'
    const pat = cfg.filePattern || '*.*'
    return `${host}:${port}  ${path}  (${pat})`
  }
  if (isLocal.value) {
    return cfg.localPath || '未配置目录'
  }
  if (props.channelType === 'API') {
    return `${(cfg.method || 'GET').toUpperCase()} ${cfg.url || '未配置地址'}`
  }
  if (props.channelType === 'CDC') {
    return `${cfg.canalHost || '—'} / ${cfg.sourceDb || '未配置源库'}`
  }
  if (props.channelType === 'SEMI') {
    return `${cfg.broker || '—'} · ${cfg.topic || '未配置 Topic'}`
  }
  if (props.channelType === 'UNSTRUCT') {
    return cfg.storagePath || cfg.ftpHost || '未配置路径'
  }
  return '—'
}

function resetForm(ch?: Channel) {
  const cfg = parseConfig(ch?.configJson)
  Object.keys(channelForm).forEach((k) => delete channelForm[k])
  for (const f of props.configFields) {
    channelForm[f.key] = cfg[f.key] ?? f.defaultValue ?? ''
  }
  channelName.value = ch?.channelName || ''
  editingId.value = ch?.id
}

async function reload() {
  loading.value = true
  try {
    channels.value = (await ingestionApi.channels(props.channelType)).data || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  resetForm()
  channelName.value = ''
  dialogVisible.value = true
  void loadTableOptionsIfNeeded()
}

function openEdit(row: Channel) {
  resetForm(row)
  dialogVisible.value = true
  void loadTableOptionsIfNeeded()
}

async function loadTableOptionsIfNeeded() {
  if (!props.configFields.some((f) => f.key === 'targetTable' || f.key === 'sourceTable')) return
  tablesLoading.value = true
  try {
    tableOptions.value = await fetchDataSourceTableNames(-1)
  } catch {
    tableOptions.value = []
  } finally {
    tablesLoading.value = false
  }
}

function validateForm(): boolean {
  if (!channelName.value.trim()) {
    ElMessage.warning('请填写任务名称')
    return false
  }
  for (const f of props.configFields) {
    if (f.required && !(channelForm[f.key] || '').trim()) {
      ElMessage.warning(`请填写${f.label}`)
      return false
    }
  }
  if (isFtp.value && !(channelForm.host || '').trim()) {
    ElMessage.warning('请填写 FTP 主机')
    return false
  }
  if (isLocal.value && !(channelForm.localPath || '').trim()) {
    ElMessage.warning('请填写服务器可访问的本地/共享目录')
    return false
  }
  return true
}

async function saveTask(andRun = false) {
  if (!validateForm()) return
  saveBusy.value = true
  try {
    const body = {
      channelName: channelName.value.trim(),
      channelType: props.channelType,
      config: { ...channelForm },
    }
    let id = editingId.value
    if (id) {
      await ingestionApi.updateChannel(id, body)
      ElMessage.success('已保存')
    } else {
      id = Number((await ingestionApi.createChannel(body)).data)
      editingId.value = id
      ElMessage.success('接入任务已创建')
    }
    await reload()
    if (andRun && id) {
      await doRun(id)
    } else {
      dialogVisible.value = false
    }
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saveBusy.value = false
  }
}

async function doRun(id: number) {
  runBusy.value = true
  try {
    const res = await ingestionApi.runChannel(id)
    ElMessage.success(String(res.data?.message || '执行完成'))
    dialogVisible.value = false
    await reload()
  } catch {
    // 拦截器已提示；失败状态以后端落库为准
    await reload()
  } finally {
    runBusy.value = false
  }
}

async function runRow(row: Channel) {
  if (isLocal.value) {
    try {
      await ElMessageBox.confirm(
        '本地目录通道不会再返回演示行数。浏览器选文件写入 ODS 请用「本地文件上传 / 上传文件」。是否仍按已保存目录配置发起服务端执行？',
        '执行确认',
        { type: 'warning', confirmButtonText: '继续执行', cancelButtonText: '去本地文件上传' },
      )
    } catch {
      emit('goManualUpload')
      return
    }
  }
  await doRun(row.id)
}

async function removeRow(row: Channel) {
  try {
    await ElMessageBox.confirm(
      `确定删除接入任务「${row.channelName}」？仅删除接入配置，不会删除已落库数据。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await ingestionApi.deleteChannel(row.id)
    ElMessage.success('已删除')
    if (editingId.value === row.id) {
      dialogVisible.value = false
      editingId.value = undefined
    }
    await reload()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

function isDocLanded(row: RelatedDoc) {
  if (typeof row.landed === 'boolean') return row.landed
  const key = row.storageKey || ''
  return !!key && !key.startsWith('external://')
}

/** 按通道存储路径到非结构文件资源（同一套 uns_document）查找可预览/下载文件 */
async function loadRelatedDocs(row: Channel): Promise<RelatedDoc[]> {
  const cfg = parseConfig(row.configJson)
  const path = (cfg.storagePath || cfg.localPath || cfg.remotePath || '').trim()
  const keyword = path || row.channelName || ''
  if (!keyword) return []
  const res = await api.get('/unstructured/platform/documents', {
    params: { keyword },
  })
  const rows = (res.data || []) as RelatedDoc[]
  if (!path) return rows
  const norm = path.replace(/\\/g, '/').toLowerCase()
  const hit = rows.filter((d) => {
    const blob = [d.title, d.originalFileName, d.storageKey]
      .filter(Boolean)
      .join(' ')
      .replace(/\\/g, '/')
      .toLowerCase()
    return blob.includes(norm) || norm.includes(blob.slice(0, 32))
  })
  return hit.length ? hit : rows
}

async function accessFile(doc: RelatedDoc, download: boolean) {
  if (!isDocLanded(doc)) {
    ElMessage.warning('还未落盘')
    return
  }
  try {
    const res = await api.get(`/unstructured/platform/documents/${doc.id}/content`, {
      params: { download },
      responseType: 'blob',
    })
    const blob = res.data instanceof Blob ? res.data : new Blob([res.data], { type: doc.contentType })
    if (blob.type && blob.type.includes('application/json')) {
      const text = await blob.text()
      try {
        const parsed = JSON.parse(text) as { message?: string; msg?: string }
        const msg = parsed.message || parsed.msg || ''
        if (msg.includes('还未落盘')) {
          ElMessage.warning('还未落盘')
          return
        }
      } catch { /* ignore */ }
      ElMessage.error(download ? '下载失败' : '当前文件格式无法预览或内容不可用')
      return
    }
    const url = URL.createObjectURL(blob)
    if (download) {
      const a = document.createElement('a')
      a.href = url
      a.download = doc.originalFileName || doc.title || '文件'
      a.click()
    } else {
      window.open(url, '_blank', 'noopener,noreferrer')
    }
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch {
    ElMessage.error(download ? '下载失败' : '当前文件格式无法预览或内容不可用')
  }
}

async function openPreviewOrDownload(row: Channel, mode: 'preview' | 'download') {
  if (!isUnstruct.value) return
  fileBusy.value = true
  try {
    const docs = await loadRelatedDocs(row)
    const landed = docs.filter((d) => isDocLanded(d))
    if (!docs.length || !landed.length) {
      ElMessage.warning('还未落盘')
      return
    }
    if (landed.length === 1) {
      await accessFile(landed[0], mode === 'download')
      return
    }
    relatedDocs.value = landed
    filePickMode.value = mode
    filePickVisible.value = true
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载文件失败')
  } finally {
    fileBusy.value = false
  }
}

watch(
  () => props.channelType,
  () => {
    queryKeyword.value = ''
    void reload()
  },
)

onMounted(reload)
</script>

<template>
  <div v-loading="loading" class="channel-task-panel">
    <PageCard>
      <template #header>
        <div class="wiz-head">
          <div>
            <div class="wiz-title">{{ title }}</div>
          </div>
          <div class="wiz-actions">
            <el-button v-if="isLocal" type="success" @click="emit('goManualUpload')">上传 Excel/CSV</el-button>
            <el-button type="primary" @click="openCreate">{{ isFtp ? '新建远程接入' : '新建目录接入' }}</el-button>
          </div>
        </div>
      </template>

      <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent>
        <el-form-item label="关键词" class="portal-field-lg">
          <el-input v-model="queryKeyword" clearable placeholder="名称 / 主机 / 路径" />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button @click="reload">刷新</el-button>
        </el-form-item>
      </el-form>

      <el-table
        :data="filteredChannels"
        stripe
        size="small"
        empty-text="暂无接入任务，请点击右上角新建"
      >
        <el-table-column prop="channelName" label="任务名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="连接信息" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <code class="cfg-code">{{ cfgSummary(row) }}</code>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastRunAt" label="最近执行" width="170">
          <template #default="{ row }">{{ formatDateTime(row.lastRunAt) }}</template>
        </el-table-column>
        <el-table-column prop="lastMessage" label="执行说明" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" :width="opColWidth" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="success" :loading="runBusy" @click="runRow(row)">执行</el-button>
            <template v-if="isUnstruct">
              <el-button link type="primary" :loading="fileBusy" @click="openPreviewOrDownload(row, 'preview')">预览</el-button>
              <el-button link type="primary" :loading="fileBusy" @click="openPreviewOrDownload(row, 'download')">下载</el-button>
            </template>
            <el-button link type="danger" @click="removeRow(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <el-dialog
      v-model="filePickVisible"
      :title="filePickMode === 'download' ? '选择要下载的文件' : '选择要预览的文件'"
      width="520px"
      destroy-on-close
      append-to-body
    >
      <el-table :data="relatedDocs" stripe size="small" max-height="360">
        <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
        <el-table-column prop="originalFileName" label="文件名" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="accessFile(row, filePickMode === 'download'); filePickVisible = false">
              {{ filePickMode === 'download' ? '下载' : '预览' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="620px"
      destroy-on-close
      append-to-body
    >
      <el-form label-width="120px">
        <el-form-item label="任务名称" required>
          <el-input
            v-model="channelName"
            maxlength="80"
          />
        </el-form-item>
        <el-form-item
          v-for="f in configFields"
          :key="f.key"
          :label="f.label"
          :required="!!f.required || (isFtp && f.key === 'host') || (isLocal && f.key === 'localPath')"
        >
          <el-select
            v-if="f.key === 'targetTable' || f.key === 'sourceTable'"
            v-model="channelForm[f.key]"
            filterable
            allow-create
            default-first-option
            clearable
            :loading="tablesLoading"
            style="width: 100%"
          >
            <el-option v-for="t in tableOptions" :key="t" :label="t" :value="t" />
          </el-select>
          <el-input
            v-else
            v-model="channelForm[f.key]"
            :type="f.secret ? 'password' : 'text'"
            :show-password="!!f.secret"
            autocomplete="off"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button :loading="saveBusy" @click="saveTask(false)">保存</el-button>
        <el-button type="primary" :loading="saveBusy || runBusy" @click="saveTask(true)">保存并执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.wiz-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.wiz-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  line-height: 1.4;
}
.wiz-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.cfg-code {
  font-size: 12px;
  color: var(--el-text-color-regular);
  background: transparent;
}
</style>
