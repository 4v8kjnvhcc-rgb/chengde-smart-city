<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

const loading = ref(false)
const tab = ref('template')
const templates = ref<Record<string, unknown>[]>([])
const catalogs = ref<Record<string, unknown>[]>([])
const objections = ref<Record<string, unknown>[]>([])
const manifests = ref<Record<string, unknown>[]>([])
const duties = ref<Record<string, unknown>[]>([])

const templateForm = reactive({
  templateCode: '',
  templateName: '',
  demandType: 'STRUCTURED',
  fieldSchema: '[{"key":"dataDomain","label":"数据域"},{"key":"updateFreq","label":"更新频率"},{"key":"shareScope","label":"共享范围"}]',
})
const catalogForm = reactive({ title: '', description: '' })

async function load() {
  loading.value = true
  try {
    if (tab.value === 'template') {
      templates.value = (await api.get('/exchange/supply/templates', { params: { scope: 'all' } })).data
    } else if (tab.value === 'catalog') {
      catalogs.value = (await api.get('/exchange/supply/catalog-manifest')).data
    } else if (tab.value === 'objection') {
      objections.value = (await api.get('/exchange/supply/objections')).data
    } else if (tab.value === 'manifest') {
      manifests.value = (await api.get('/exchange/supply/manifests')).data
    } else if (tab.value === 'duty') {
      duties.value = (await api.get('/exchange/supply/duties')).data
    }
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

async function createTemplate() {
  if (!templateForm.templateName) return ElMessage.warning('请填写模板名称')
  if (templateForm.demandType === 'STRUCTURED' && templateForm.fieldSchema) {
    try {
      JSON.parse(templateForm.fieldSchema)
    } catch {
      return ElMessage.warning('字段模型须为合法 JSON')
    }
  }
  await api.post('/exchange/supply/templates', {
    ...templateForm,
    fieldSchema: templateForm.demandType === 'STRUCTURED' ? templateForm.fieldSchema : '{}',
  })
  templateForm.templateName = ''
  templateForm.templateCode = ''
  templateForm.fieldSchema = '[{"key":"dataDomain","label":"数据域"},{"key":"updateFreq","label":"更新频率"},{"key":"shareScope","label":"共享范围"}]'
  ElMessage.success('模板已创建')
  await load()
}

async function setTemplateStatus(id: number, status: string) {
  await api.post(`/exchange/supply/templates/${id}`, { status })
  await load()
}

async function createCatalog() {
  if (!catalogForm.title) return
  await api.post('/exchange/supply/catalog', catalogForm)
  catalogForm.title = ''
  await load()
}

async function publishCatalog(id: number) {
  await api.post(`/exchange/supply/catalog/${id}/publish`)
  await load()
}

async function offlineCatalog(id: number) {
  await api.post(`/exchange/supply/catalog/${id}/offline`, { reason: '目录维护' })
  await load()
}

async function closeObjection(id: number) {
  await api.post(`/exchange/supply/objections/${id}/process`, { action: 'CLOSE', handlerNote: '配置侧已处理' })
  await load()
}

async function exportManifest(id: number) {
  await api.post(`/exchange/supply/manifests/${id}/export`)
  ElMessage.success('清单已导出')
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <PageHeader
      title="供需配置"
      description="系统管理 · 数据共享交换平台 · 应用平台 — 模板、目录发布、异议治理与数据责任台账"
    />
    <el-radio-group v-model="tab" class="mb" @change="load">
      <el-radio-button value="template">需求模板</el-radio-button>
      <el-radio-button value="catalog">目录治理</el-radio-button>
      <el-radio-button value="objection">异议治理</el-radio-button>
      <el-radio-button value="manifest">全局清单</el-radio-button>
      <el-radio-button value="duty">数据责任</el-radio-button>
    </el-radio-group>

    <PageCard v-if="tab === 'template'" title="需求模板管理">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        title="结构化模板请配置 fieldSchema（JSON 数组，如 [{key,label}]），前台按模板字段模型化填报；非结构化模板供正文录入。"
      />
      <el-form label-width="96px" style="max-width:720px;margin-bottom:12px">
        <el-form-item label="编码"><el-input v-model="templateForm.templateCode" placeholder="可选，空则自动生成" style="width:240px" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="templateForm.templateName" style="width:320px" /></el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="templateForm.demandType">
            <el-radio-button value="STRUCTURED">结构化</el-radio-button>
            <el-radio-button value="UNSTRUCTURED">非结构化</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="templateForm.demandType === 'STRUCTURED'" label="字段模型">
          <el-input
            v-model="templateForm.fieldSchema"
            type="textarea"
            :rows="4"
            placeholder='[{"key":"dataDomain","label":"数据域"},{"key":"updateFreq","label":"更新频率"}]'
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="createTemplate">新建模板</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="templates" stripe size="small">
        <el-table-column prop="templateCode" label="编码" width="140" />
        <el-table-column prop="templateName" label="名称" min-width="140" />
        <el-table-column prop="demandType" label="类型" width="110" />
        <el-table-column prop="fieldSchema" label="字段模型" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'ACTIVE'" link type="primary" @click="setTemplateStatus(Number(row.id), 'ACTIVE')">启用</el-button>
            <el-button v-else link type="warning" @click="setTemplateStatus(Number(row.id), 'INACTIVE')">停用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <PageCard v-else-if="tab === 'catalog'" title="目录发布与下线">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="标题" class="portal-field-md"><el-input v-model="catalogForm.title" /></el-form-item>
        <el-form-item label="说明" class="portal-field-lg"><el-input v-model="catalogForm.description" /></el-form-item>
        <el-form-item class="portal-form-actions"><el-button type="primary" @click="createCatalog">新建</el-button></el-form-item>
      </el-form>
      <el-table :data="catalogs" stripe size="small">
        <el-table-column prop="catalogCode" label="编码" width="140" />
        <el-table-column prop="title" label="标题" min-width="160" />
        <el-table-column prop="publishStatus" label="状态" width="100" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button v-if="row.publishStatus !== 'PUBLISHED'" link type="primary" @click="publishCatalog(Number(row.id))">发布</el-button>
            <el-button v-if="row.publishStatus === 'PUBLISHED'" link type="warning" @click="offlineCatalog(Number(row.id))">下线</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <PageCard v-else-if="tab === 'objection'" title="异议治理">
      <el-table :data="objections" stripe size="small">
        <el-table-column prop="catalogId" label="目录ID" width="90" />
        <el-table-column prop="objectionType" label="类型" width="100" />
        <el-table-column prop="content" label="内容" min-width="200" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'CLOSED'" link @click="closeObjection(Number(row.id))">关闭</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <PageCard v-else-if="tab === 'manifest'" title="全局供需清单">
      <el-table :data="manifests" stripe size="small">
        <el-table-column prop="manifestType" label="类型" width="120" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="authLevel" label="授权级别" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="exportManifest(Number(row.id))">导出</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <PageCard v-else-if="tab === 'duty'" title="数据责任台账">
      <el-table :data="duties" stripe size="small">
        <el-table-column prop="demandId" label="需求ID" width="90" />
        <el-table-column prop="dutyOrg" label="责任单位" min-width="140" />
        <el-table-column prop="dutyType" label="责任类型" width="110" />
        <el-table-column prop="fulfillPath" label="履约路径" width="160" />
        <el-table-column prop="catalogId" label="目录ID" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="180" />
      </el-table>
    </PageCard>
  </div>
</template>

<style scoped>
.mb { margin-bottom: 12px; }
</style>
