<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel } from '@/utils/status-label'

interface CatalogRes {
  id: number
  resourceCode: string
  resourceName: string
  resourceType: string
  categoryPath?: string
  providerOrg?: string
  resourceFormat?: string
  shareType?: string
  updateCycle?: string
  description?: string
  publishStatus: string
  sourcePathType?: 'DIRECT' | 'PROCESSED'
}

const SHARE_TYPE_ZH: Record<string, string> = {
  OPEN: '无条件共享',
  CONDITIONAL: '有条件共享',
  NOT_SHARE: '不予共享',
}
const TYPE_ZH: Record<string, string> = { DATA: '数据', SERVICE: '服务' }
const SHARE_MODE_OPTS = [
  { label: '库表同步', value: 'DB_SYNC' },
  { label: '文件同步', value: 'FILE_SYNC' },
  { label: '接口服务', value: 'API' },
]

const keyword = ref('')
const cards = ref<CatalogRes[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const current = ref<CatalogRes | null>(null)
const form = reactive({
  shareMode: 'DB_SYNC',
  applicantOrg: '',
  purpose: '',
})

async function load() {
  loading.value = true
  try {
    const res = await api.get('/governance/catalog/resources-mgmt', {
      params: {
        publishStatus: 'PUBLISHED',
        keyword: keyword.value || undefined,
      },
    })
    cards.value = res.data || []
  } catch {
    ElMessage.error('加载目录门户失败')
  } finally {
    loading.value = false
  }
}

function openSubscribe(row: CatalogRes) {
  current.value = row
  form.shareMode = row.resourceType === 'SERVICE' ? 'API' : 'DB_SYNC'
  form.applicantOrg = ''
  form.purpose = ''
  dialogVisible.value = true
}

async function submitSubscribe() {
  if (!current.value) return
  if (!form.purpose.trim()) {
    ElMessage.warning('请填写申请用途')
    return
  }
  await api.post('/governance/catalog/subscriptions', {
    resourceId: current.value.id,
    shareMode: form.shareMode,
    applicantOrg: form.applicantOrg || undefined,
    purpose: form.purpose,
  })
  ElMessage.success('订阅申请已提交')
  dialogVisible.value = false
}

onMounted(load)
</script>

<template>
  <PageCard title="资源目录门户">
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="关键词" class="portal-field-xl">
        <el-input v-model="keyword" clearable placeholder="编码 / 名称 / 提供方" @keyup.enter="load" />
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="load">搜索</el-button>
      </el-form-item>
    </el-form>

    <div v-loading="loading" class="portal-grid">
      <el-empty v-if="!loading && !cards.length" description="暂无已发布资源" />
      <div v-for="item in cards" :key="item.id" class="portal-card">
        <div class="portal-card__head">
          <span class="portal-card__title">{{ item.resourceName }}</span>
          <span>
            <el-tag v-if="item.sourcePathType" size="small" type="primary">
              {{ item.sourcePathType === 'DIRECT' ? '直通' : '加工' }}
            </el-tag>
            <el-tag size="small" type="success">{{ statusLabel(item.publishStatus) }}</el-tag>
          </span>
        </div>
        <div class="portal-card__code">{{ item.resourceCode }}</div>
        <div class="portal-card__meta">
          <span>{{ TYPE_ZH[item.resourceType] || item.resourceType }}</span>
          <span v-if="item.providerOrg">· {{ item.providerOrg }}</span>
          <span v-if="item.shareType">· {{ SHARE_TYPE_ZH[item.shareType] || item.shareType }}</span>
        </div>
        <p class="portal-card__desc">{{ item.description || item.categoryPath || '暂无描述' }}</p>
        <div class="portal-card__actions">
          <el-button type="primary" size="small" @click="openSubscribe(item)">订阅申请</el-button>
        </div>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" title="订阅申请" width="480px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="资源">
          <span>{{ current?.resourceName }}（{{ current?.resourceCode }}）</span>
        </el-form-item>
        <el-form-item label="共享方式" required>
          <el-select v-model="form.shareMode" style="width: 100%">
            <el-option v-for="o in SHARE_MODE_OPTS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="申请机构">
          <el-input v-model="form.applicantOrg" placeholder="可选，默认当前机构" />
        </el-form-item>
        <el-form-item label="申请用途" required>
          <el-input v-model="form.purpose" type="textarea" :rows="3" placeholder="请说明订阅用途" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitSubscribe">提交</el-button>
      </template>
    </el-dialog>
  </PageCard>
</template>

<style scoped>
.portal-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
  min-height: 120px;
}
.portal-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 14px 16px;
  background: var(--el-bg-color);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.portal-card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.portal-card__title {
  font-weight: 600;
  font-size: 15px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.portal-card__code {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.portal-card__meta {
  font-size: 12px;
  color: var(--el-text-color-regular);
}
.portal-card__desc {
  margin: 4px 0 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.45;
  min-height: 38px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.portal-card__actions {
  margin-top: auto;
}
</style>
