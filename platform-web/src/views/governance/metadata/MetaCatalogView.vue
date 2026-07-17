<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { LAYER_OPTIONS } from './meta-labels'
import { statusLabel } from '@/utils/status-label'

interface Entry {
  id: number
  entryCode: string
  entryName: string
  entryType: string
  parentCode?: string
  databaseName?: string
  dataLayer?: string
  securityLevel?: string
  ownerName?: string
  description?: string
  conformStatus?: string
  updatedAt?: string
  status: string
}

interface Inventory {
  tableCount?: number
  idleCount?: number
  byLayer?: Record<string, number>
  idleEntryCodes?: string[]
}

const router = useRouter()

const keyword = ref('')
const tag = ref('')
const catalogType = ref<'all' | 'source' | 'asset'>('all')
const treeType = ref<'source' | 'asset'>('asset')
const entries = ref<Entry[]>([])
const inventory = ref<Inventory>({})
const selectedCode = ref('')
const loading = ref(false)

function layerLabel(layer?: string) {
  if (!layer) return '—'
  const opt = LAYER_OPTIONS.find(o => o.value === layer)
  return opt?.label || statusLabel(layer)
}

function isIdleRow(row: Entry) {
  if (row.entryType !== 'TABLE') return false
  const codes = inventory.value.idleEntryCodes || []
  return codes.includes(row.entryCode)
}

const inventoryBar = computed(() => {
  const parts = [`共 ${inventory.value.tableCount ?? 0} 张表`]
  if (inventory.value.idleCount != null) {
    parts.push(`闲置 ${inventory.value.idleCount} 张`)
  }
  const byLayer = inventory.value.byLayer || {}
  const layerParts = Object.entries(byLayer).map(([k, v]) => `${layerLabel(k)} ${v}`)
  if (layerParts.length) parts.push(layerParts.join(' · '))
  return parts.join(' ｜ ')
})

const treeData = computed(() => {
  const roots = entries.value.filter(e => {
    if (treeType.value === 'source') return e.entryType === 'SOURCE' || e.entryType === 'CONNECTOR'
    return e.entryType === 'TABLE' || e.entryType === 'CATALOG' || e.entryType === 'MODEL'
  })
  return roots.map(r => ({
    label: r.entryName,
    code: r.entryCode,
    children: entries.value
      .filter(c => c.parentCode === r.entryCode && c.entryType !== 'COLUMN')
      .map(c => ({ label: c.entryName, code: c.entryCode, entry: c })),
    entry: r,
  }))
})

const tableRows = computed(() => {
  let rows = entries.value.filter(e => e.entryType !== 'COLUMN')
  if (selectedCode.value) {
    rows = rows.filter(e => e.entryCode === selectedCode.value || e.parentCode === selectedCode.value)
  }
  return rows
})

function columnChildren(parentCode: string) {
  return entries.value.filter(e => e.entryType === 'COLUMN' && e.parentCode === parentCode)
}

async function loadSearch() {
  loading.value = true
  try {
    const res = await api.get('/governance/platform/metadata/catalog/search', {
      params: {
        keyword: keyword.value || undefined,
        tag: tag.value || undefined,
        type: catalogType.value === 'all' ? undefined : catalogType.value,
      },
    })
    entries.value = res.data || []
    selectedCode.value = ''
  } finally {
    loading.value = false
  }
}

async function loadInventory() {
  inventory.value = (await api.get('/governance/platform/metadata/catalog/inventory')).data || {}
}

function onTreeClick(node: { code?: string }) {
  selectedCode.value = node.code || ''
}

async function offlineEntry(row: Entry) {
  try {
    await ElMessageBox.confirm(`确定下线「${row.entryName}」？`, '下线确认', { type: 'warning' })
  } catch {
    return
  }
  await api.post(`/governance/platform/metadata/catalog/${row.id}/offline`)
  ElMessage.success('已下线')
  await loadSearch()
  await loadInventory()
}

function goQuality(row: Entry) {
  router.push({
    name: 'governance',
    query: { tab: 'quality', qSub: 'task-mgmt', entryCode: row.entryCode },
  }).catch(() => {
    ElMessage.info(`请前往质量任务，关联编码：${row.entryCode}`)
  })
}

function goCatalog(row: Entry) {
  router.push({
    name: 'governance',
    query: { tab: 'catalog', cSub: 'resources', entryCode: row.entryCode },
  }).catch(() => {
    ElMessage.info(`请前往资源编目，关联编码：${row.entryCode}`)
  })
}

onMounted(async () => {
  await Promise.all([loadSearch(), loadInventory()])
})
</script>

<template>
  <PageCard title="元数据目录">
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="关键字" class="portal-field-lg">
        <el-input v-model="keyword" clearable placeholder="名称/编码" @keyup.enter="loadSearch" />
      </el-form-item>
      <el-form-item label="标签" class="portal-field-md">
        <el-input v-model="tag" clearable />
      </el-form-item>
      <el-form-item label="类型" class="portal-field-sm">
        <el-select v-model="catalogType">
          <el-option label="全部" value="all" />
          <el-option label="数据源" value="source" />
          <el-option label="资产" value="asset" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" :loading="loading" @click="loadSearch">搜索</el-button>
      </el-form-item>
    </el-form>

    <el-alert type="info" :closable="false" :title="inventoryBar" style="margin-bottom: 12px" />

    <el-row :gutter="12">
      <el-col :span="6">
        <el-radio-group v-model="treeType" size="small" style="margin-bottom: 8px">
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
        <el-table :data="tableRows" stripe size="small" max-height="480" row-key="id">
          <el-table-column type="expand">
            <template #default="{ row }">
              <el-table
                v-if="columnChildren(row.entryCode).length"
                :data="columnChildren(row.entryCode)"
                size="small"
                stripe
                style="margin: 8px 0 8px 48px"
              >
                <el-table-column prop="entryName" label="字段名" />
                <el-table-column prop="entryCode" label="编码" width="180" show-overflow-tooltip />
                <el-table-column label="类型" width="90">
                  <template #default="{ row: col }">{{ $statusLabel(col.entryType) }}</template>
                </el-table-column>
                <el-table-column prop="description" label="说明" show-overflow-tooltip />
              </el-table>
              <el-empty v-else description="无字段子项" :image-size="48" />
            </template>
          </el-table-column>
          <el-table-column prop="entryName" label="名称" min-width="140">
            <template #default="{ row }">
              {{ row.entryName }}
              <el-tag v-if="isIdleRow(row)" type="info" size="small" style="margin-left: 6px">闲置</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ $statusLabel(row.entryType) }}</template>
          </el-table-column>
          <el-table-column prop="databaseName" label="库名" width="120" show-overflow-tooltip />
          <el-table-column label="分层" width="110">
            <template #default="{ row }">{{ layerLabel(row.dataLayer) }}</template>
          </el-table-column>
          <el-table-column label="分级" width="80">
            <template #default="{ row }">{{ $statusLabel(row.securityLevel) }}</template>
          </el-table-column>
          <el-table-column prop="ownerName" label="责任人" width="90" show-overflow-tooltip />
          <el-table-column label="符合度" width="90">
            <template #default="{ row }">
              <span v-if="row.conformStatus">{{ $statusLabel(row.conformStatus) }}</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" width="160" />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="goQuality(row)">发起质量</el-button>
              <el-button link type="primary" @click="goCatalog(row)">发起编目</el-button>
              <el-button link type="danger" @click="offlineEntry(row)">下线</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-col>
    </el-row>
  </PageCard>
</template>
