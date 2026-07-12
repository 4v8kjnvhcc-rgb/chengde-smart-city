<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api/http'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'
import type { D05Module } from '@/components/catalog/ModuleMetaPanel.vue'

const router = useRouter()
const summary = ref<{
  moduleCount: number
  version: string
  statusSummary: Record<string, number>
  platforms: Record<string, { name: string; catalogPath: string }>
} | null>(null)
const modules = ref<D05Module[]>([])
const keyword = ref('')
const platform = ref('')
const status = ref('')

const STATUS_LABEL: Record<string, string> = {
  implemented: '已实装',
  poc: 'PoC',
  external: '外部',
  stub: '占位',
  missing: '待开发',
}

async function search() {
  const res = await api.get('/catalog/modules', {
    params: {
      platform: platform.value || undefined,
      keyword: keyword.value || undefined,
      status: status.value || undefined,
    },
  })
  modules.value = res.data
}

async function load() {
  const res = await api.get('/catalog/summary')
  summary.value = res.data
  await search()
}

function openModule(row: D05Module) {
  router.push(`/modules/${row.mCode}`)
}

function goPlatform(p: string) {
  router.push(`/catalog/${p}`)
}

function statusTagType(s: string) {
  if (s === 'implemented') return 'success'
  if (s === 'poc') return 'warning'
  if (s === 'missing') return 'danger'
  return 'info'
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      title="D05 全量功能检索"
      description="检索 M001～M215 全部模块；与侧栏「D05功能清单」菜单树一致。"
    />

    <PageCard v-if="summary" style="margin-bottom: 16px">
      <div style="margin-bottom: 12px">
        <el-tag type="info">{{ summary.version }}</el-tag>
        <el-tag style="margin-left: 8px">共 {{ summary.moduleCount }} 模块</el-tag>
      </div>
      <el-space wrap>
        <el-button
          v-for="(meta, key) in summary.platforms"
          :key="key"
          @click="goPlatform(key)"
        >
          {{ meta.name }}
        </el-button>
      </el-space>
    </PageCard>

    <PageCard title="检索条件">
      <el-form inline @submit.prevent="search">
        <el-form-item label="平台">
          <el-select v-model="platform" clearable placeholder="全部" style="width: 200px" @change="search">
            <el-option
              v-for="(meta, key) in summary?.platforms || {}"
              :key="key"
              :label="meta.name"
              :value="key"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="status" clearable placeholder="全部" style="width: 140px" @change="search">
            <el-option label="已实装" value="implemented" />
            <el-option label="PoC演示" value="poc" />
            <el-option label="外部组件" value="external" />
            <el-option label="占位" value="stub" />
            <el-option label="待开发" value="missing" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="keyword"
            clearable
            placeholder="M编号 / 模块名"
            style="width: 240px"
            @keyup.enter="search"
            @clear="search"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">检索</el-button>
        </el-form-item>
      </el-form>
    </PageCard>

    <PageCard :title="`检索结果（${modules.length}）`" style="margin-top: 16px">
      <el-table class="portal-table" :data="modules" stripe max-height="520" @row-click="openModule">
        <el-table-column prop="mCode" label="编号" width="88" fixed />
        <el-table-column prop="moduleName" label="功能模块" min-width="160" />
        <el-table-column prop="chapter" label="D05章节" min-width="160" show-overflow-tooltip />
        <el-table-column prop="sectionName" label="子章节" min-width="180" show-overflow-tooltip />
        <el-table-column prop="deliveryLevel" label="级别" width="72" />
        <el-table-column prop="implStatus" label="状态" width="96">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.implStatus)">
              {{ STATUS_LABEL[row.implStatus] || row.implStatus }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>
  </div>
</template>
