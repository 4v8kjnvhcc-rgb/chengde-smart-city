<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage } from 'element-plus'

interface Situation {
  id: number
  situationCode: string
  situationName: string
  jumpUrl?: string
  openMode?: string
}

const router = useRouter()
const loading = ref(false)
const situations = ref<Situation[]>([])

async function load() {
  loading.value = true
  try {
    situations.value = (await api.get('/exchange/portal/situations')).data || []
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function onCardClick(s: Situation) {
  const url = String(s.jumpUrl || '').trim()
  if (!url) {
    ElMessage.warning('请先在「统一用户管理 → 门户配置 → 领导决策八态势」中设置该模块的跳转地址')
    return
  }
  const openMode = String(s.openMode || 'new_tab')
  if (/^https?:\/\//i.test(url)) {
    window.open(url, '_blank', 'noopener,noreferrer')
    return
  }
  if (openMode === 'new_tab') {
    const href = url.startsWith('/') ? `${window.location.origin}${url}` : url
    window.open(href, '_blank', 'noopener,noreferrer')
    return
  }
  void router.push(url)
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="cockpit">
    <div class="cockpit-panel">
      <div class="cockpit-grid">
        <button
          v-for="s in situations"
          :key="s.situationCode"
          type="button"
          class="situation-card"
          @click="onCardClick(s)"
        >
          <span class="corner-deco" aria-hidden="true">
            <i class="corner-line corner-h" />
            <i class="corner-line corner-v" />
            <i class="corner-line corner-h-sm" />
            <i class="corner-line corner-v-sm" />
          </span>
          <span class="situation-name">{{ s.situationName }}</span>
        </button>
      </div>
      <el-empty
        v-if="!loading && !situations.length"
        description="暂无态势模块"
      />
    </div>
  </div>
</template>

<style scoped>
.cockpit {
  min-height: calc(100vh - 56px - 48px);
  display: flex;
  flex-direction: column;
}
.cockpit-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  min-height: 0;
  padding: 28px 4px 24px;
  box-sizing: border-box;
}
.cockpit-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  grid-template-rows: repeat(2, minmax(200px, 1fr));
  gap: 22px;
  flex: 1;
  min-height: 440px;
  max-height: 560px;
}
.situation-card {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  min-height: 200px;
  padding: 28px 24px;
  overflow: hidden;
  border: 1px solid rgba(18, 81, 168, 0.28);
  border-radius: 10px;
  background: linear-gradient(165deg, #ffffff 0%, #f7faff 55%, #eef5fc 100%);
  box-shadow: 0 2px 10px rgba(11, 58, 122, 0.05);
  cursor: pointer;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease,
    background 0.18s ease;
}
.corner-deco {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 28px;
  height: 28px;
  pointer-events: none;
}
.corner-line {
  position: absolute;
  display: block;
  background: #3a7fd4;
  opacity: 0.55;
}
.corner-h {
  top: 0;
  right: 0;
  width: 22px;
  height: 1.5px;
}
.corner-v {
  top: 0;
  right: 0;
  width: 1.5px;
  height: 22px;
}
.corner-h-sm {
  top: 6px;
  right: 0;
  width: 12px;
  height: 1px;
  opacity: 0.35;
}
.corner-v-sm {
  top: 0;
  right: 6px;
  width: 1px;
  height: 12px;
  opacity: 0.35;
}
.situation-card:hover {
  transform: translateY(-2px);
  border-color: rgba(18, 81, 168, 0.55);
  background: linear-gradient(165deg, #ffffff 0%, #eef5ff 50%, #e2eefb 100%);
  box-shadow: 0 8px 20px rgba(11, 58, 122, 0.1);
}
.situation-card:hover .corner-line {
  opacity: 0.85;
  background: #1251a8;
}
.situation-card:hover .situation-name {
  color: #0b3a7a;
}
.situation-card:focus-visible {
  outline: 2px solid #1251a8;
  outline-offset: 3px;
}
.situation-name {
  position: relative;
  z-index: 1;
  font-size: clamp(24px, 2vw, 30px);
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #123a6b;
  text-align: center;
  line-height: 1.35;
}
@media (max-width: 1100px) {
  .cockpit-grid {
    grid-template-columns: repeat(2, 1fr);
    grid-template-rows: repeat(4, minmax(160px, 1fr));
    max-height: none;
  }
}
@media (max-width: 640px) {
  .cockpit-grid {
    grid-template-columns: 1fr;
    grid-template-rows: none;
    min-height: 0;
    max-height: none;
  }
  .situation-card {
    min-height: 140px;
    height: auto;
  }
  .situation-name {
    font-size: 22px;
    letter-spacing: 0.04em;
  }
}
</style>
