<script setup lang="ts">
import { onMounted, ref } from 'vue'
import api from '@/api/http'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

interface Org {
  id: number
  orgCode: string
  orgName: string
  parentId: number
}

const orgs = ref<Org[]>([])

onMounted(async () => {
  const res = await api.get('/system/orgs')
  orgs.value = res.data
})
</script>

<template>
  <div>
    <PageHeader title="机构管理" description="组织机构与部门信息（MS1 列表视图）" />
    <PageCard>
      <el-table class="portal-table" :data="orgs" stripe>
        <el-table-column prop="orgCode" label="编码" min-width="120" />
        <el-table-column prop="orgName" label="名称" min-width="160" />
        <el-table-column prop="parentId" label="上级ID" width="100" />
      </el-table>
    </PageCard>
  </div>
</template>
