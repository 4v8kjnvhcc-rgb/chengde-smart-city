<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/api/http'
import { statusLabel, statusTagType } from '@/utils/status-label'

const props = defineProps<{ modelValue: boolean; entryCode: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

interface Profile {
  entryCode: string
  entry: Record<string, unknown>
  columns: Array<Record<string, unknown>>
  lineage: {
    upstream: Array<Record<string, unknown>>
    downstream: Array<Record<string, unknown>>
  }
  quality: Record<string, unknown>
  catalog: Record<string, unknown>
  subscriptions: Array<Record<string, unknown>>
}

const loading = ref(false)
const profile = ref<Profile | null>(null)

async function load(code: string) {
  if (!code) return
  loading.value = true
  try {
    profile.value = (await api.get('/governance/asset/360', { params: { entryCode: code } })).data
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载资产360失败')
    profile.value = null
  } finally {
    loading.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}

watch(
  () => [props.modelValue, props.entryCode] as const,
  ([visible, code]) => {
    if (visible && code) load(code)
  },
  { immediate: true },
)
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="资产 360"
    size="52%"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @close="close"
  >
    <div v-loading="loading">
      <template v-if="profile">
        <el-descriptions :column="2" border size="small" title="元数据">
          <el-descriptions-item label="条目编码">{{ profile.entry.entryCode }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ profile.entry.entryName }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ profile.entry.entryType }}</el-descriptions-item>
          <el-descriptions-item label="物理表">{{ profile.entry.physicalTableName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="标签">{{ profile.entry.tags || '—' }}</el-descriptions-item>
          <el-descriptions-item label="分级">{{ profile.entry.securityLevel || '—' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">血缘</el-divider>
        <div class="asset-lineage">
          <div class="asset-lineage__col">
            <div class="asset-lineage__cap">上游</div>
            <el-tag v-for="u in profile.lineage.upstream" :key="String(u.code)" type="info" class="asset-chip">
              {{ u.name || u.code }}
            </el-tag>
            <span v-if="!profile.lineage.upstream.length" class="asset-empty">无</span>
          </div>
          <div class="asset-lineage__arrow">→</div>
          <div class="asset-lineage__col">
            <div class="asset-lineage__cap">当前</div>
            <el-tag type="primary" class="asset-chip">{{ profile.entry.entryName }}</el-tag>
          </div>
          <div class="asset-lineage__arrow">→</div>
          <div class="asset-lineage__col">
            <div class="asset-lineage__cap">下游</div>
            <el-tag v-for="d in profile.lineage.downstream" :key="String(d.code)" type="success" class="asset-chip">
              {{ d.name || d.code }}
            </el-tag>
            <span v-if="!profile.lineage.downstream.length" class="asset-empty">无</span>
          </div>
        </div>

        <el-divider content-position="left">质量</el-divider>
        <template v-if="String(profile.quality.status) !== 'NONE'">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="状态">
              <el-tag :type="statusTagType(profile.quality.status)">{{ statusLabel(profile.quality.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="任务">{{ profile.quality.taskName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="评分">{{ profile.quality.score ?? profile.quality.lastScore ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="问题行">{{ profile.quality.issueCount ?? '—' }}</el-descriptions-item>
          </el-descriptions>
          <el-table
            v-if="(profile.quality.issues as unknown[])?.length"
            :data="profile.quality.issues as unknown[]"
            size="small"
            stripe
            style="margin-top:8px"
          >
            <el-table-column prop="targetColumn" label="字段" width="160" />
            <el-table-column prop="issueType" label="问题类型" width="110" />
            <el-table-column prop="issueValue" label="描述" show-overflow-tooltip />
            <el-table-column prop="severity" label="级别" width="90" />
          </el-table>
        </template>
        <el-empty v-else description="未绑定质量任务" :image-size="60" />

        <el-divider content-position="left">目录</el-divider>
        <template v-if="String(profile.catalog.status) !== 'NONE'">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="资源">{{ profile.catalog.resourceName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="来源路径">
              <el-tag :type="profile.catalog.sourcePathType === 'PROCESSED' ? 'warning' : 'success'" size="small">
                {{ profile.catalog.sourcePathType === 'PROCESSED' ? '加工共享' : '直通共享' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="发布状态">
              <el-tag :type="statusTagType(profile.catalog.publishStatus)">
                {{ statusLabel(profile.catalog.publishStatus) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="质量评分">{{ profile.catalog.qualityScore ?? '—' }}</el-descriptions-item>
          </el-descriptions>
        </template>
        <el-empty v-else description="尚未编目" :image-size="60" />

        <el-divider content-position="left">订阅与授权</el-divider>
        <el-table v-if="profile.subscriptions.length" :data="profile.subscriptions" size="small" stripe>
          <el-table-column prop="applicantOrg" label="申请方" width="140" />
          <el-table-column label="订阅状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="授权码" min-width="180">
            <template #default="{ row }">{{ row.authorization?.authorizationCode || '—' }}</template>
          </el-table-column>
          <el-table-column label="授权状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.authorization" :type="statusTagType(row.authorization.status)" size="small">
                {{ statusLabel(row.authorization.status) }}
              </el-tag>
              <span v-else>—</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无订阅" :image-size="60" />
      </template>
    </div>
  </el-drawer>
</template>

<style scoped>
.asset-lineage {
  display: flex;
  align-items: stretch;
  gap: 12px;
}
.asset-lineage__col {
  flex: 1;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px;
  min-height: 64px;
}
.asset-lineage__cap {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}
.asset-lineage__arrow {
  display: flex;
  align-items: center;
  color: var(--el-text-color-secondary);
}
.asset-chip {
  margin: 0 6px 6px 0;
}
.asset-empty {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
