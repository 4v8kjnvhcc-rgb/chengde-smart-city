<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageCard from '@/components/common/PageCard.vue'
import api from '@/api/http'
import type { RegisterMenuMeta } from '../ingestion-nav'

const props = defineProps<{ module?: string }>()
const router = useRouter()
const meta = ref<RegisterMenuMeta | null>(null)

const menuId = computed(() => {
  const m = props.module || ''
  const n = /^custom-(\d+)$/.exec(m)
  return n ? Number(n[1]) : null
})

onMounted(async () => {
  if (!menuId.value) return
  try {
    const list = (await api.get('/system/menus/register-scope')).data as RegisterMenuMeta[]
    meta.value = list.find((r) => r.id === menuId.value) || null
  } catch {
    meta.value = null
  }
})

function openPath() {
  const p = meta.value?.path
  if (!p) return
  if (p.startsWith('http://') || p.startsWith('https://')) {
    window.open(p, '_blank')
    return
  }
  router.push(p.startsWith('/') ? p : `/${p}`)
}
</script>

<template>
  <PageCard :title="meta?.menuName || '自定义菜单'">
    <el-result
      icon="info"
      :title="meta?.menuName || '自定义菜单'"
      :sub-title="meta?.path ? `访问地址：${meta.path}` : '尚未配置访问地址或未接入页面组件'"
    >
      <template #extra>
        <el-button v-if="meta?.path" type="primary" @click="openPath">打开访问地址</el-button>
      </template>
    </el-result>
  </PageCard>
</template>
