<script setup lang="ts">
import { onMounted, ref } from 'vue'
import api from '@/api/http'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

interface Menu {
  id: number
  menuName: string
  path: string
  menuType: number
  mCode: string
}

const menus = ref<Menu[]>([])

onMounted(async () => {
  const res = await api.get('/system/menus')
  menus.value = res.data
})
</script>

<template>
  <div>
    <PageHeader title="菜单管理" description="门户菜单树（对齐 D07 §5.6）" />
    <PageCard>
      <el-table class="portal-table" :data="menus" height="520" stripe>
        <el-table-column prop="menuName" label="名称" min-width="160" />
        <el-table-column prop="path" label="路径" min-width="200" />
        <el-table-column prop="mCode" label="M编号" width="100" />
        <el-table-column prop="menuType" label="类型" width="80" />
      </el-table>
    </PageCard>
  </div>
</template>
