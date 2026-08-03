<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'
import type { D05Module } from '@/components/catalog/ModuleMetaPanel.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'

interface Section {
  key: string
  platform: string
  chapter: string
  name: string
  moduleCount: number
}

interface Summary {
  version: string
  moduleCount: number
  statusSummary: Record<string, number>
  sections: Section[]
}

const PLATFORM_NAMES: Record<string, string> = {
  exchange: '数据共享交换平台',
  'master-data': '主数据平台',
  analytics: '大数据挖掘分析平台',
  system: '系统管理 / 跨平台',
}

const route = useRoute()
const router = useRouter()
const summary = ref<Summary | null>(null)
const modules = ref<D05Module[]>([])
const keyword = ref('')
const status = ref('')
const sectionKey = ref('')

const platform = computed(() => String(route.params.platform || ''))
const title = computed(() => PLATFORM_NAMES[platform.value] || platform.value)

const sections = computed(() =>
  (summary.value?.sections || []).filter((s) => s.platform === platform.value),
)

async function load() {
  const [s, m] = await Promise.all([
    api.get('/catalog/summary'),
    api.get('/catalog/modules', {
      params: {
        platform: platform.value,
        sectionKey: sectionKey.value || undefined,
        keyword: keyword.value || undefined,
        status: status.value || undefined,
      },
    }),
  ])
  summary.value = s.data
  modules.value = m.data
}

function openModule(row: D05Module) {
  router.push(`/modules/${row.mCode}`)
}

watch([platform, sectionKey, status], load)
watch(keyword, () => {
  window.clearTimeout((window as unknown as { _d05kw?: number })._d05kw)
  ;(window as unknown as { _d05kw?: number })._d05kw = window.setTimeout(load, 300)
})

onMounted(load)
</script>

<template>
  <div>
    <PageHeader :title="`D05 功能清单 · ${title}`" description="按 D05 V2.6 章节列出全部模块，点击编号进入验收对照页。">
      <el-button @click="router.push('/catalog')">全量检索</el-button>
    </PageHeader>

    <PageCard v-if="summary" style="margin-bottom: 16px">
      <el-space wrap>
        <el-tag>共 {{ summary.moduleCount }} 模块（本板块 {{ modules.length }}）</el-tag>
        <el-tag v-for="(cnt, key) in summary.statusSummary" :key="key" :type="statusTagType(key)">
          {{ statusLabel(key) }}: {{ cnt }}
        </el-tag>
      </el-space>
    </PageCard>

    <PageCard title="筛选">
      <el-form inline>
        <el-form-item label="章节">
          <el-select v-model="sectionKey" clearable placeholder="全部章节" style="width: 280px">
            <el-option v-for="s in sections" :key="s.key" :label="s.name" :value="s.key" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="status" clearable placeholder="全部" style="width: 140px">
            <el-option label="已实装" value="implemented" />
            <el-option label="PoC演示" value="poc" />
            <el-option label="外部组件" value="external" />
            <el-option label="占位" value="stub" />
            <el-option label="待开发" value="missing" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="keyword" clearable placeholder="编号/名称/描述" style="width: 220px" />
        </el-form-item>
      </el-form>
    </PageCard>

    <PageCard title="模块列表" style="margin-top: 16px">
      <el-table class="portal-table" :data="modules" stripe @row-click="openModule">
        <el-table-column prop="mCode" label="编号" width="88" />
        <el-table-column prop="moduleName" label="功能模块" min-width="180" />
        <el-table-column prop="sectionName" label="章节" min-width="200" show-overflow-tooltip />
        <el-table-column prop="deliveryLevel" label="级别" width="72" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.implStatus)">
              {{ statusLabel(row.implStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openModule(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>
  </div>
</template>
