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
  physicalTableName?: string
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

interface TreeNode {
  label: string
  code: string
  children?: TreeNode[]
  entry?: Entry
}

const ASSET_LAYER_ORDER = ['ODS', 'DWD', 'DWS', 'ADS'] as const

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

/** 控制面 smart_city 系统表，不作为数据资产展示 */
function isControlEntry(e: Entry) {
  if (e.dataLayer === 'CONTROL') return true
  if (e.databaseName === 'smart_city') return true
  return false
}

function resolveLayer(e: Entry) {
  if (e.dataLayer && ASSET_LAYER_ORDER.includes(e.dataLayer as typeof ASSET_LAYER_ORDER[number])) {
    return e.dataLayer
  }
  if (e.databaseName === 'smart_city_ods') return 'ODS'
  if (e.databaseName === 'smart_city_dwd') return 'DWD'
  if (e.databaseName === 'smart_city_dws') return 'DWS'
  if (e.databaseName === 'smart_city_ads') return 'ADS'
  const name = (e.physicalTableName || e.entryName || '').toLowerCase()
  if (name.startsWith('dwd_')) return 'DWD'
  if (name.startsWith('dws_')) return 'DWS'
  if (name.startsWith('ads_')) return 'ADS'
  if (name.startsWith('ods_')) return 'ODS'
  return 'ODS'
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
  const layerParts = ASSET_LAYER_ORDER
    .filter(k => byLayer[k] != null)
    .map(k => `${layerLabel(k)} ${byLayer[k]}`)
  if (layerParts.length) parts.push(layerParts.join(' · '))
  return parts.join(' ｜ ')
})

const treeData = computed((): TreeNode[] => {
  if (treeType.value === 'source') {
    const roots = entries.value.filter(e => e.entryType === 'SOURCE' || e.entryType === 'CONNECTOR')
    return roots.map(r => ({
      label: r.entryName,
      code: r.entryCode,
      children: entries.value
        .filter(c => c.parentCode === r.entryCode && c.entryType === 'TABLE' && !isControlEntry(c))
        .map(c => ({ label: c.entryName, code: c.entryCode, entry: c })),
      entry: r,
    }))
  }
  // 资产树：按 ODS/DWD/DWS/ADS；ODS 下再按登记源分组（手动上传落在此层）
  const sourceName = (code?: string) => {
    if (!code) return '未归属数据源'
    const s = entries.value.find(e => e.entryCode === code && (e.entryType === 'SOURCE' || e.entryType === 'CONNECTOR'))
    return s?.entryName || code
  }
  return ASSET_LAYER_ORDER.map(layer => {
    const tables = entries.value.filter(e =>
      e.entryType === 'TABLE' && !isControlEntry(e) && resolveLayer(e) === layer,
    )
    const bySource = new Map<string, Entry[]>()
    for (const t of tables) {
      const key = t.parentCode || '__none__'
      if (!bySource.has(key)) bySource.set(key, [])
      bySource.get(key)!.push(t)
    }
    const children: TreeNode[] = []
    for (const [srcCode, list] of bySource) {
      if (list.length === 1 && srcCode === '__none__') {
        children.push({ label: list[0].entryName, code: list[0].entryCode, entry: list[0] })
        continue
      }
      children.push({
        label: `${sourceName(srcCode === '__none__' ? undefined : srcCode)}（${list.length}）`,
        code: `__src_${layer}_${srcCode}`,
        children: list.map(t => ({ label: t.entryName, code: t.entryCode, entry: t })),
      })
    }
    return {
      label: `${layerLabel(layer)}（${tables.length}）`,
      code: `__layer_${layer}`,
      children,
    }
  })
})

const tableRows = computed(() => {
  let rows = entries.value.filter(e => e.entryType !== 'COLUMN' && !isControlEntry(e))
  if (selectedCode.value.startsWith('__layer_')) {
    const layer = selectedCode.value.slice('__layer_'.length)
    rows = rows.filter(e => e.entryType === 'TABLE' && resolveLayer(e) === layer)
  } else if (selectedCode.value.startsWith('__src_')) {
    // __src_ODS_SRC_xxx
    const rest = selectedCode.value.slice('__src_'.length)
    const idx = rest.indexOf('_')
    const layer = idx >= 0 ? rest.slice(0, idx) : ''
    const srcCode = idx >= 0 ? rest.slice(idx + 1) : ''
    rows = rows.filter(e => {
      if (e.entryType !== 'TABLE' || resolveLayer(e) !== layer) return false
      if (srcCode === '__none__') return !e.parentCode
      return e.parentCode === srcCode
    })
  } else if (selectedCode.value) {
    rows = rows.filter(e => e.entryCode === selectedCode.value || e.parentCode === selectedCode.value)
  } else if (treeType.value === 'asset') {
    rows = rows.filter(e => e.entryType === 'TABLE')
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

    <el-alert type="info" :closable="false" :title="inventoryBar" style="margin-bottom: 8px" />
    <el-alert
      type="success"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="手动上传 / 库表汇聚写入 smart_city_ods 后，在「资产树 → ODS 原始层」按数据源分组展示；也可切换「数据源树」按登记源查看。"
    />

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
            <template #default="{ row }">{{ layerLabel(row.dataLayer || resolveLayer(row)) }}</template>
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
