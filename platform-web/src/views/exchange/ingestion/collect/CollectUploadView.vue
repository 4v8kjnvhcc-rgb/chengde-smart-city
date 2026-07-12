<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import PageCard from '@/components/common/PageCard.vue'
import { ingestionApi, useIngestionLoading, type Upload, type UploadTemplate } from '../useIngestionHub'

const { loading, loadError, withLoad } = useIngestionLoading()
const templates = ref<UploadTemplate[]>([])
const uploads = ref<Upload[]>([])
const tplForm = reactive({ templateCode: '', templateName: '', columnMappingJson: '[{"col":"name","target":"entity_name"}]' })
const uploadForm = reactive({ templateCode: 'TPL_STRUCT_01', fileName: 'demo_upload.xlsx' })
const fileInput = ref<HTMLInputElement>()

async function reload() {
  await withLoad(async () => {
    templates.value = (await ingestionApi.templates()).data
    uploads.value = (await ingestionApi.uploads()).data
    if (templates.value.length) uploadForm.templateCode = templates.value[0].templateCode
  })
}

async function createTemplate() {
  if (!tplForm.templateName) return
  await ingestionApi.createTemplate({ ...tplForm })
  tplForm.templateName = ''
  await reload()
}

async function doUpload() {
  await ingestionApi.upload({ ...uploadForm })
  await reload()
}

async function onFileChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  const fd = new FormData()
  fd.append('file', file)
  fd.append('templateCode', uploadForm.templateCode)
  await ingestionApi.uploadFile(fd)
  await reload()
}

onMounted(reload)
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard title="上传模板管理">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="模板编码" class="portal-field-md"><el-input v-model="tplForm.templateCode" placeholder="可选" /></el-form-item>
        <el-form-item label="模板名称" class="portal-field-md"><el-input v-model="tplForm.templateName" /></el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="createTemplate">新建模板</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="templates" stripe size="small">
        <el-table-column prop="templateCode" label="编码" width="160" />
        <el-table-column prop="templateName" label="名称" min-width="160" />
        <el-table-column prop="columnMappingJson" label="列映射" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="90" />
      </el-table>
    </PageCard>
    <PageCard title="数据上传管理">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="模板" class="portal-field-default">
          <el-select v-model="uploadForm.templateCode">
            <el-option v-for="t in templates" :key="t.id" :label="t.templateName" :value="t.templateCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件名" class="portal-field-lg"><el-input v-model="uploadForm.fileName" /></el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="doUpload">解析入库</el-button>
          <el-button @click="fileInput?.click()">选择文件上传</el-button>
        </el-form-item>
        <input ref="fileInput" type="file" accept=".xlsx,.xls,.csv" style="display:none" @change="onFileChange" />
      </el-form>
    </PageCard>
    <PageCard title="数据上传记录">
      <el-table :data="uploads" stripe>
        <el-table-column prop="fileName" label="文件" min-width="180" />
        <el-table-column prop="templateCode" label="模板" width="140" />
        <el-table-column prop="rowCount" label="行数" width="80" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="previewJson" label="预览" min-width="160" show-overflow-tooltip />
      </el-table>
    </PageCard>
  </div>
</template>
