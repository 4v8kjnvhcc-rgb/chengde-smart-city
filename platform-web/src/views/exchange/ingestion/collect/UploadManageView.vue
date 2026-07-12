<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import PageCard from '@/components/common/PageCard.vue'
import { ingestionApi, useIngestionLoading, type Upload, type UploadTemplate } from '../useIngestionHub'

const props = defineProps<{ module: string }>()
const { loading, loadError, withLoad } = useIngestionLoading()
const uploads = ref<Upload[]>([])
const templates = ref<UploadTemplate[]>([])
const uploadForm = reactive({ templateCode: 'TPL_STRUCT_01', fileName: 'demo_upload.xlsx' })
const fileInput = ref<HTMLInputElement>()

const title = computed(() => (props.module === 'm053' ? '数据上传记录' : '数据上传管理'))

async function reload() {
  await withLoad(async () => {
    uploads.value = (await ingestionApi.uploads()).data
    templates.value = (await ingestionApi.templates()).data
    if (templates.value.length) uploadForm.templateCode = templates.value[0].templateCode
  })
}

async function doUpload() {
  await ingestionApi.upload({ ...uploadForm })
  uploadForm.fileName = 'demo_upload.xlsx'
  await reload()
}

async function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const fd = new FormData()
  fd.append('file', file)
  fd.append('templateCode', uploadForm.templateCode)
  await ingestionApi.uploadFile(fd)
  input.value = ''
  await reload()
}

onMounted(reload)
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard v-if="module === 'm052'" title="数据上传管理">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="模板" class="portal-field-default">
          <el-select v-model="uploadForm.templateCode">
            <el-option v-for="t in templates" :key="t.id" :label="t.templateName" :value="t.templateCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件名" class="portal-field-lg"><el-input v-model="uploadForm.fileName" /></el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="doUpload">模拟上传解析</el-button>
          <el-button @click="fileInput?.click()">真实文件上传</el-button>
        </el-form-item>
        <input ref="fileInput" type="file" accept=".xlsx,.xls,.csv" style="display:none" @change="onFileChange" />
      </el-form>
    </PageCard>
    <PageCard :title="title">
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
