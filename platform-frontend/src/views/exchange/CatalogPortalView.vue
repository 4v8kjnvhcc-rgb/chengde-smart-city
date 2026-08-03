<script setup lang="ts">
import { onMounted, ref } from 'vue'
import api from '@/api/http'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

interface Catalog {
  id: number
  catalogCode: string
  title: string
  description: string
  publishStatus: string
}

const items = ref<Catalog[]>([])

onMounted(async () => {
  const res = await api.get('/exchange/shared-portal')
  items.value = res.data
})
</script>

<template>
  <div>
    <PageHeader title="应用分析门户 · 共享目录" description="演示场景：已发布目录在共享门户可见并可订阅对接" />
    <PageCard>
      <el-empty v-if="!items.length" description="暂无已发布目录，请先在「应用平台」发布目录" />
      <el-table v-else class="portal-table" :data="items" stripe>
        <el-table-column prop="catalogCode" label="目录编码" min-width="140" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="description" label="说明" min-width="220" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">{{ $statusLabel(row.publishStatus) }}</template>
        </el-table-column>
      </el-table>
    </PageCard>
  </div>
</template>
