<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'

interface Entry {
  id: number
  entryCode: string
  entryName: string
  entryType: string
  description?: string
  tags?: string
  keywords?: string
  securityLevel?: string
  changeFlag?: string
  status: string
  omRef?: string
}

interface Notice {
  id: number
  entryCode?: string
  title: string
  detail?: string
  status: string
  createdAt?: string
}

interface Suggestion {
  itemName: string
  count: number
  itemType: string
}

const entries = ref<Entry[]>([])
const notices = ref<Notice[]>([])
const suggestions = ref<Suggestion[]>([])
const form = reactive({
  entryName: '',
  entryType: 'SOURCE',
  description: '',
  tags: '',
  keywords: '',
  securityLevel: '',
  mode: 'MANUAL',
  promoteStandard: false,
})

async function loadEntries() {
  entries.value = (await api.get('/governance/platform/metadata/entries')).data || []
}

async function loadNotices() {
  notices.value = (await api.get('/governance/platform/metadata/notices')).data || []
}

async function loadSuggestions() {
  suggestions.value = (await api.get('/governance/platform/metadata/maintain/suggest-standards')).data || []
}

async function load() {
  await loadEntries()
}

async function save() {
  if (!form.entryName) return
  await api.post('/governance/platform/metadata/maintain', { ...form })
  ElMessage.success(form.promoteStandard ? '已维护并登记沉淀标准提醒' : '已维护')
  form.entryName = ''
  form.description = ''
  form.tags = ''
  form.keywords = ''
  form.securityLevel = ''
  await Promise.all([loadEntries(), loadNotices()])
}

async function promote(item: Suggestion) {
  await api.post('/governance/platform/metadata/maintain/promote-standard', {
    itemName: item.itemName,
    itemType: item.itemType,
  })
  ElMessage.success(`已沉淀标准：${item.itemName}`)
  await loadSuggestions()
}

onMounted(async () => {
  await load()
  await Promise.all([loadNotices(), loadSuggestions()])
})
</script>

<template>
  <PageCard title="M092 元数据维护">
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="名称" class="portal-field-lg"><el-input v-model="form.entryName" /></el-form-item>
      <el-form-item label="类型" class="portal-field-sm">
        <el-select v-model="form.entryType">
          <el-option label="数据源" value="SOURCE" />
          <el-option label="表" value="TABLE" />
          <el-option label="目录" value="CATALOG" />
        </el-select>
      </el-form-item>
      <el-form-item label="方式" class="portal-field-sm">
        <el-select v-model="form.mode"><el-option label="手工" value="MANUAL" /><el-option label="自动" value="AUTO" /></el-select>
      </el-form-item>
      <el-form-item label="标签" class="portal-field-md"><el-input v-model="form.tags" placeholder="逗号分隔" /></el-form-item>
      <el-form-item label="关键字" class="portal-field-md"><el-input v-model="form.keywords" /></el-form-item>
      <el-form-item label="分级" class="portal-field-sm">
        <el-select v-model="form.securityLevel" clearable>
          <el-option label="公开" value="PUBLIC" /><el-option label="内部" value="INTERNAL" />
          <el-option label="敏感" value="SENSITIVE" /><el-option label="机密" value="SECRET" />
        </el-select>
      </el-form-item>
      <el-form-item label="沉淀标准">
        <el-switch v-model="form.promoteStandard" />
      </el-form-item>
      <el-form-item class="portal-form-actions"><el-button type="primary" @click="save">维护提交</el-button></el-form-item>
    </el-form>
    <el-input v-model="form.description" type="textarea" :rows="2" placeholder="变更说明 / 提醒内容" style="margin-bottom:12px" />
    <el-row :gutter="12">
      <el-col :span="14">
        <h4>维护台账</h4>
        <el-table :data="entries" stripe size="small" max-height="280">
          <el-table-column prop="entryCode" label="编码" width="140" />
          <el-table-column prop="entryName" label="名称" />
          <el-table-column prop="entryType" label="类型" width="80" />
          <el-table-column prop="tags" label="标签" width="100" show-overflow-tooltip />
          <el-table-column prop="securityLevel" label="分级" width="80" />
          <el-table-column prop="changeFlag" label="变更" width="80" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-col>
      <el-col :span="10">
        <h4>变更通知</h4>
        <el-table :data="notices" stripe size="small" max-height="280">
          <el-table-column prop="title" label="标题" />
          <el-table-column prop="entryCode" label="编码" width="120" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
          </el-table-column>
        </el-table>
        <h4 style="margin-top:12px">标准沉淀建议</h4>
        <el-table :data="suggestions" stripe size="small" max-height="200">
          <el-table-column prop="itemName" label="字段名" />
          <el-table-column prop="count" label="频次" width="70" />
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="primary" @click="promote(row)">沉淀</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-col>
    </el-row>
  </PageCard>
</template>
