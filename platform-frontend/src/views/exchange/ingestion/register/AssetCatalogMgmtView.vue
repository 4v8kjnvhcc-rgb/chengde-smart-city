<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { statusTagType } from '@/utils/status-label'
import { useAuthStore } from '@/stores/auth'
import {
  ingestionApi,
  useIngestionLoading,
  type AssetCatalogReg,
  type Project,
} from '../useIngestionHub'
import {
  approveRegister,
  canAuditRegister,
  registerStatusZh,
  rejectRegister,
} from './register-workflow'
import AssetCatalogFormDialog from './AssetCatalogFormDialog.vue'
import { formatDateTime } from '@/utils/datetime'

const auth = useAuthStore()

defineProps<{ module: string }>()

const { loading, loadError, withLoad } = useIngestionLoading()
const rows = ref<AssetCatalogReg[]>([])
const query = reactive({
  assetName: '',
  orgName: '',
  projectName: '',
  status: '',
})

const dialogVisible = ref(false)
const viewingId = ref<number | null>(null)

const auditVisible = ref(false)
const auditTarget = ref<AssetCatalogReg | null>(null)
const auditDecision = ref<'APPROVE' | 'REJECT'>('APPROVE')
const rejectReason = ref('')
const acting = ref(false)
const orgOptions = ref<Array<{ id: number; orgName: string; label: string }>>([])
const projectOptions = ref<Project[]>([])

async function reload() {
  await withLoad(async () => {
    rows.value = (await ingestionApi.assetCatalogList({
      assetName: query.assetName || undefined,
      orgName: query.orgName || undefined,
      projectName: query.projectName || undefined,
      status: query.status || undefined,
    })).data || []
  })
}

function resetQuery() {
  query.assetName = ''
  query.orgName = ''
  query.projectName = ''
  query.status = ''
  void reload()
}

function openView(row: AssetCatalogReg) {
  viewingId.value = row.id
  dialogVisible.value = true
}

function openAudit(row: AssetCatalogReg) {
  if (!canAuditRegister(row.status)) {
    ElMessage.warning('仅待审核状态可审核')
    return
  }
  auditTarget.value = row
  auditDecision.value = 'APPROVE'
  rejectReason.value = ''
  auditVisible.value = true
}

async function submitAudit() {
  if (!auditTarget.value) return
  if (auditDecision.value === 'REJECT' && !rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  acting.value = true
  try {
    if (auditDecision.value === 'APPROVE') {
      await approveRegister('CATALOG_REG', auditTarget.value.id)
      ElMessage.success('审核通过')
    } else {
      await rejectRegister('CATALOG_REG', auditTarget.value.id, rejectReason.value.trim())
      ElMessage.success('已驳回，状态为驳回待提交')
    }
    auditVisible.value = false
    await reload()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '审核失败')
  } finally {
    acting.value = false
  }
}

async function doDelete(row: AssetCatalogReg) {
  if (!auth.isPlatformOrSystemAdmin) {
    ElMessage.warning('仅平台管理员或超级管理员可删除资产目录')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除资产「${row.assetName}」？`, '删除确认', { type: 'warning' })
    await ingestionApi.assetCatalogDelete(row.id)
    ElMessage.success('已删除')
    await reload()
  } catch (e: unknown) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

async function loadFilterOptions() {
  try {
    orgOptions.value = (await ingestionApi.assetCatalogOrgOptions()).data || []
  } catch {
    orgOptions.value = []
  }
  try {
    projectOptions.value = (await ingestionApi.projects()).data || []
  } catch {
    projectOptions.value = []
  }
}

onMounted(() => {
  void loadFilterOptions()
  void reload()
})
</script>

<template>
  <div>
    <PageCard title="资产目录管理">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="资产名称" class="portal-field-md">
          <el-input v-model="query.assetName" clearable placeholder="资产名称" @keyup.enter="reload" />
        </el-form-item>
        <el-form-item label="所属机构" class="portal-field-md">
          <el-select v-model="query.orgName" clearable filterable placeholder="全部机构" style="width:100%">
            <el-option
              v-for="o in orgOptions"
              :key="o.id"
              :label="o.orgName || o.label"
              :value="o.orgName || o.label"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="来源项目" class="portal-field-md">
          <el-select v-model="query.projectName" clearable filterable placeholder="全部项目" style="width:100%">
            <el-option
              v-for="p in projectOptions"
              :key="p.id"
              :label="p.projectName"
              :value="p.projectName"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" class="portal-field-sm">
          <el-select v-model="query.status" clearable placeholder="全部">
            <el-option label="待审核" value="PENDING_REVIEW" />
            <el-option label="审核通过" value="APPROVED" />
            <el-option label="驳回待提交" value="REJECTED" />
            <el-option label="草稿" value="DRAFT" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-alert v-if="loadError" type="error" :closable="false" :title="loadError" style="margin-bottom: 12px" />

      <el-table v-loading="loading" :data="rows" stripe border size="small">
        <el-table-column prop="assetName" label="资产名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="orgName" label="所属机构" min-width="140" show-overflow-tooltip />
        <el-table-column prop="projectName" label="来源项目" min-width="140" show-overflow-tooltip />
        <el-table-column prop="systemName" label="来源系统" min-width="140" show-overflow-tooltip />
        <el-table-column prop="tableName" label="来源表" min-width="140" show-overflow-tooltip />
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="canAuditRegister(row.status) ? 'warning' : statusTagType(row.status)"
            >
              {{ registerStatusZh(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openView(row)">查看</el-button>
            <el-button
              v-if="canAuditRegister(row.status)"
              link
              type="success"
              @click="openAudit(row)"
            >
              审核
            </el-button>
            <el-button
              v-if="auth.isPlatformOrSystemAdmin"
              link
              type="danger"
              @click="doDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !rows.length" description="暂无待办或匹配记录" :image-size="48" />
    </PageCard>

    <AssetCatalogFormDialog
      v-model="dialogVisible"
      mode="view"
      :record-id="viewingId"
    />

    <el-dialog v-model="auditVisible" title="资产目录审核" width="480px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="资产名称">
          <el-input :model-value="auditTarget?.assetName || ''" disabled />
        </el-form-item>
        <el-form-item label="审核结果" required>
          <el-radio-group v-model="auditDecision">
            <el-radio value="APPROVE">审核通过</el-radio>
            <el-radio value="REJECT">审核驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="auditDecision === 'REJECT'" label="驳回原因" required>
          <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="必填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitAudit">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>
