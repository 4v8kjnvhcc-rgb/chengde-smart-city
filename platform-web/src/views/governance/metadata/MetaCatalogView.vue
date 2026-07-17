<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import AssetProfileDrawer from '../AssetProfileDrawer.vue'

interface Entry {
  id: number
  entryCode: string
  entryName: string
  entryType: string
  parentCode?: string
  omRef?: string
  tags?: string
  keywords?: string
  securityLevel?: string
  description?: string
  changeFlag?: string
  status: string
}

const keyword = ref('')
const tag = ref('')
const catalogType = ref<'all' | 'source' | 'asset'>('all')
const viewMode = ref<'table' | 'card'>('table')
const treeType = ref<'source' | 'asset'>('asset')
const entries = ref<Entry[]>([])
const omTables = ref<unknown[]>([])
const omHealthy = ref(false)
const preview = ref<Entry | null>(null)
const previewVisible = ref(false)
const asset360Visible = ref(false)
const asset360Code = ref('')

function openAsset360(row: Entry) {
  asset360Code.value = row.entryCode
  asset360Visible.value = true
}

const treeData = computed(() => {
  const roots = entries.value.filter(e => {
    if (treeType.value === 'source') return e.entryType === 'SOURCE' || e.entryType === 'CONNECTOR'
    return e.entryType === 'TABLE' || e.entryType === 'CATALOG' || e.entryType === 'MODEL'
  })
  return roots.map(r => ({
    label: r.entryName,
    code: r.entryCode,
    children: entries.value.filter(c => c.parentCode === r.entryCode).map(c => ({
      label: c.entryName,
      code: c.entryCode,
      entry: c,
    })),
    entry: r,
  }))
})

const selectedCode = ref('')
const tableRows = computed(() => {
  if (!selectedCode.value) return entries.value
  return entries.value.filter(e => e.entryCode === selectedCode.value || e.parentCode === selectedCode.value)
})

async function load() {
  const res = await api.get('/governance/platform/metadata/catalog/search', {
    params: {
      keyword: keyword.value || undefined,
      tag: tag.value || undefined,
      type: catalogType.value === 'all' ? undefined : catalogType.value,
    },
  })
  entries.value = res.data || []
  const cat = await api.get('/governance/platform/metadata/catalog')
  omTables.value = cat.data.omTables || []
  omHealthy.value = !!cat.data.omHealthy
}

function onTreeClick(node: { code?: string; entry?: Entry }) {
  selectedCode.value = node.code || ''
  if (node.entry) openPreview(node.entry)
}

function openPreview(row: Entry) {
  preview.value = row
  previewVisible.value = true
}

async function offlineEntry(row: Entry) {
  await api.post(`/governance/platform/metadata/catalog/${row.id}/offline`)
  ElMessage.success('已下线')
  await load()
}

onMounted(load)
</script>

<template>
  <PageCard title="M095 元数据目录">
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="关键字" class="portal-field-lg"><el-input v-model="keyword" clearable @keyup.enter="load" /></el-form-item>
      <el-form-item label="标签" class="portal-field-md"><el-input v-model="tag" clearable /></el-form-item>
      <el-form-item label="目录" class="portal-field-sm">
        <el-select v-model="catalogType" @change="load">
          <el-option label="全部" value="all" /><el-option label="数据源" value="source" /><el-option label="资产" value="asset" />
        </el-select>
      </el-form-item>
      <el-form-item label="展示" class="portal-field-sm">
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="table">表格</el-radio-button>
          <el-radio-button value="card">卡片</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item class="portal-form-actions"><el-button type="primary" @click="load">搜索</el-button></el-form-item>
    </el-form>
    <el-tag :type="omHealthy ? 'success' : 'info'" size="small" style="margin-bottom:8px">
      OpenMetadata {{ omHealthy ? 'UP' : 'DOWN' }} · OM表 {{ omTables.length }}
    </el-tag>
    <el-row :gutter="12">
      <el-col :span="6">
        <el-radio-group v-model="treeType" size="small" style="margin-bottom:8px">
          <el-radio-button value="source">数据源树</el-radio-button>
          <el-radio-button value="asset">资产树</el-radio-button>
        </el-radio-group>
        <el-tree
          :data="treeData"
          node-key="code"
          highlight-current
          default-expand-all
          :props="{ label: 'label', children: 'children' }"
          @node-click="onTreeClick"
        />
      </el-col>
      <el-col :span="18">
        <el-table v-if="viewMode === 'table'" :data="tableRows" stripe size="small" max-height="420">
          <el-table-column prop="entryName" label="名称" />
          <el-table-column prop="entryType" label="类型" width="90" />
          <el-table-column prop="tags" label="标签" width="120" show-overflow-tooltip />
          <el-table-column prop="securityLevel" label="分级" width="80" />
          <el-table-column prop="changeFlag" label="变更" width="80" />
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button link type="primary" @click="openPreview(row)">预览</el-button>
              <el-button link type="primary" @click="openAsset360(row)">资产360</el-button>
              <el-button link type="danger" @click="offlineEntry(row)">下线</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-row v-else :gutter="10">
          <el-col v-for="row in tableRows" :key="row.id" :span="8" style="margin-bottom:10px">
            <el-card shadow="hover" class="meta-catalog-card">
              <div class="meta-catalog-card__title">{{ row.entryName }}</div>
              <div class="meta-catalog-card__meta">{{ row.entryType }} · {{ row.entryCode }}</div>
              <div v-if="row.tags" class="meta-catalog-card__tags">{{ row.tags }}</div>
              <div style="margin-top:8px">
                <el-button link type="primary" size="small" @click="openPreview(row)">预览</el-button>
                <el-button link type="primary" size="small" @click="openAsset360(row)">资产360</el-button>
                <el-button link type="danger" size="small" @click="offlineEntry(row)">下线</el-button>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-col>
    </el-row>
    <el-drawer v-model="previewVisible" title="目录预览" size="40%">
      <template v-if="preview">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="编码">{{ preview.entryCode }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ preview.entryName }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ preview.entryType }}</el-descriptions-item>
          <el-descriptions-item label="标签">{{ preview.tags || '—' }}</el-descriptions-item>
          <el-descriptions-item label="关键字">{{ preview.keywords || '—' }}</el-descriptions-item>
          <el-descriptions-item label="分级">{{ preview.securityLevel || '—' }}</el-descriptions-item>
          <el-descriptions-item label="说明">{{ preview.description || '—' }}</el-descriptions-item>
          <el-descriptions-item label="OM引用">{{ preview.omRef || '—' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
    <AssetProfileDrawer v-model="asset360Visible" :entry-code="asset360Code" />
  </PageCard>
</template>

<style scoped>
.meta-catalog-card__title { font-weight: 600; }
.meta-catalog-card__meta { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 4px; }
.meta-catalog-card__tags { font-size: 12px; margin-top: 6px; color: var(--el-color-primary); }
</style>
