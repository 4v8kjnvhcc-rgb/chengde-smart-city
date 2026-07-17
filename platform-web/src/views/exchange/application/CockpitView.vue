<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'

interface Situation {
  id: number
  situationCode: string
  situationName: string
  domainRoute: string
  modelMCode: string
  summaryMetric: string
  boardUrl?: string
}

const router = useRouter()
const loading = ref(false)
const situations = ref<Situation[]>([])
const boardPreview = reactive<{ visible: boolean; situation: Situation | null }>({
  visible: false,
  situation: null,
})

async function load() {
  loading.value = true
  try {
    situations.value = (await api.get('/exchange/portal/situations')).data
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function goSituation(s: Situation) {
  if (s.domainRoute) router.push(s.domainRoute)
}

function openBoard(s: Situation) {
  boardPreview.situation = s
  boardPreview.visible = true
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <PageCard title="决策驾驶舱 · 八态势">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:16px"
        title="领导决策门户：进入领域分析，或打开 DataEase 看板占位（未配置 boardUrl 时显示空态）。"
      />
      <el-row :gutter="16">
        <el-col v-for="s in situations" :key="s.situationCode" :xs="24" :sm="12" :md="6">
          <el-card shadow="hover" class="situation-card">
            <div class="situation-name">{{ s.situationName }}</div>
            <div class="situation-metric">{{ s.summaryMetric }}</div>
            <div class="situation-model">{{ s.modelMCode }}</div>
            <div class="actions">
              <el-button size="small" type="primary" @click="goSituation(s)">进入分析</el-button>
              <el-button size="small" @click="openBoard(s)">看板</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </PageCard>

    <el-drawer v-model="boardPreview.visible" :title="boardPreview.situation?.situationName || '决策看板'" size="60%">
      <template v-if="boardPreview.situation">
        <div class="situation-metric" style="margin-bottom:12px">{{ boardPreview.situation.summaryMetric }}</div>
        <iframe
          v-if="boardPreview.situation.boardUrl"
          class="board-frame"
          :src="boardPreview.situation.boardUrl"
          title="DataEase"
        />
        <el-empty v-else description="待配置外部看板（DataEase）。可先进入分析查看领域统计。">
          <el-button type="primary" @click="goSituation(boardPreview.situation)">进入分析</el-button>
        </el-empty>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.situation-card { margin-bottom: 16px; min-height: 150px; }
.situation-name { font-size: 16px; font-weight: 600; }
.situation-metric { margin: 8px 0; color: #409eff; }
.situation-model { font-size: 12px; color: #909399; margin-bottom: 8px; }
.actions { display: flex; gap: 8px; }
.board-frame { width: 100%; height: 70vh; border: 1px solid #ebeef5; border-radius: 4px; }
</style>
