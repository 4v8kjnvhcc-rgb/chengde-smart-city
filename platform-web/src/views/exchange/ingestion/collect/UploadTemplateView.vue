<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import PageCard from '@/components/common/PageCard.vue'
import { ingestionApi, useIngestionLoading, type UploadTemplate } from '../useIngestionHub'

const { loading, loadError, withLoad } = useIngestionLoading()
const templates = ref<UploadTemplate[]>([])
const form = reactive({ templateCode: '', templateName: '', columnMappingJson: '[{"col":"name","target":"entity_name"}]' })

async function reload() {
  await withLoad(async () => { templates.value = (await ingestionApi.templates()).data })
}

async function create() {
  if (!form.templateName) return
  await ingestionApi.createTemplate({ ...form })
  form.templateName = ''
  await reload()
}

onMounted(reload)
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard title="上传模板管理">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="模板编码" class="portal-field-md"><el-input v-model="form.templateCode" placeholder="可选" /></el-form-item>
        <el-form-item label="模板名称" class="portal-field-md"><el-input v-model="form.templateName" /></el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="create">新建模板</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="templates" stripe>
        <el-table-column prop="templateCode" label="编码" width="160" />
        <el-table-column prop="templateName" label="名称" min-width="160" />
        <el-table-column prop="columnMappingJson" label="列映射" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="90" />
      </el-table>
    </PageCard>
  </div>
</template>
