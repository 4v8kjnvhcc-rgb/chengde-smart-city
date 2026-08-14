<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { ingestionApi, useIngestionLoading, type Project } from '../useIngestionHub'
import {
  approveRegister,
  canAuditRegister,
  registerStatusZh,
  rejectRegister,
} from './register-workflow'
import ProjectSystemDetailView from './ProjectSystemDetailView.vue'

const auth = useAuthStore()
const { loading, loadError, withLoad } = useIngestionLoading()

const projects = ref<Project[]>([])
const statusFilter = ref('')
const keyword = ref('')

const detailProjectId = ref<number | null>(null)

const auditVisible = ref(false)
const auditTarget = ref<Project | null>(null)
const auditDecision = ref<'APPROVE' | 'REJECT'>('APPROVE')
const rejectReason = ref('')
const acting = ref(false)

const currentDeptName = computed(() => auth.user?.orgName || '—')

function displayProjectName(row: { projectName?: string; projectCode?: string; boundOrgName?: string }) {
  const name = (row.projectName || '').trim()
  const code = String(row.projectCode || '').toUpperCase()
  const isOther = name === '其他' || code === 'OTHER' || code === 'PRJ_OTHER' || code.startsWith('PRJ_OTHER_')
  if (!isOther) return name || '—'
  const dept = (row.boundOrgName || currentDeptName.value || '').trim()
  if (!dept || dept === '—') return name || '其他'
  return `${name || '其他'}（${dept}）`
}
const detailProject = computed(
  () => projects.value.find((p) => p.id === detailProjectId.value) || null,
)

const filtered = computed(() => {
  let list = projects.value
  const st = statusFilter.value.trim().toUpperCase()
  if (st) {
    list = list.filter((p) => {
      const s = String(p.registerStatus || 'DRAFT').toUpperCase()
      if (st === 'PENDING_REVIEW') return s === 'PENDING_REVIEW' || s === 'PENDING' || s === 'PENDING_ARCHIVE'
      return s === st
    })
  }
  const kw = keyword.value.trim()
  if (kw) {
    list = list.filter(
      (p) =>
        (p.projectName || '').includes(kw)
        || (p.boundOrgName || '').includes(kw)
        || (p.projectCode || '').includes(kw),
    )
  }
  return list
})

const { page, pageSize, paged, total, resetPage } = useClientPager(filtered)

async function reload() {
  await withLoad(async () => {
    projects.value = (await ingestionApi.projects()).data || []
    if (detailProjectId.value && !projects.value.some((p) => p.id === detailProjectId.value)) {
      detailProjectId.value = null
    }
  })
}

function onQuery() {
  resetPage()
  void reload()
}

function onReset() {
  statusFilter.value = ''
  keyword.value = ''
  resetPage()
  void reload()
}

function openView(row: Project) {
  detailProjectId.value = row.id
}

function backToList() {
  detailProjectId.value = null
  void reload()
}

function openAudit(row: Project) {
  if (!canAuditRegister(row.registerStatus)) {
    ElMessage.warning('仅待审核状态可审核')
    return
  }
  auditTarget.value = row
  auditDecision.value = 'APPROVE'
  rejectReason.value = ''
  auditVisible.value = true
}

async function doDelete(row: Project) {
  if (!auth.isPlatformOrSystemAdmin) {
    ElMessage.warning('仅平台管理员或超级管理员可删除项目')
    return
  }
  if (String(row.projectCode || '').toUpperCase() === 'OTHER' || row.projectName === '其他') {
    ElMessage.warning('「其他」为系统初始化项目，不可删除')
    return
  }
  try {
    const systems = (await ingestionApi.systems(row.id)).data || []
    if (systems.length) {
      await ElMessageBox.alert(
        `该项目下已关联 ${systems.length} 个系统，无法删除，请先删除系统后再删项目。`,
        '删除校验',
        { type: 'warning' },
      )
      return
    }
    await ElMessageBox.confirm(`确认删除项目「${row.projectName}」？`, '删除确认', { type: 'warning' })
    await ingestionApi.deleteProject(row.id)
    ElMessage.success('项目已删除')
    if (detailProjectId.value === row.id) detailProjectId.value = null
    await reload()
  } catch (e: unknown) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
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
      await approveRegister('PROJECT', auditTarget.value.id)
      ElMessage.success('审核通过')
    } else {
      await rejectRegister('PROJECT', auditTarget.value.id, rejectReason.value.trim())
      ElMessage.success('已驳回，状态为驳回待提交')
    }
    auditVisible.value = false
    await reload()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '审核失败')
  } finally {
    acting.value = false
  }
}

onMounted(reload)
</script>

<template>
  <div v-loading="loading">
    <el-alert
      v-if="loadError"
      type="error"
      :title="loadError"
      :closable="false"
      style="margin-bottom:12px"
    />

    <ProjectSystemDetailView
      v-if="detailProject"
      :project="detailProject"
      readonly
      @back="backToList"
    />

    <template v-else>
      <PageCard title="项目/系统信息管理">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="状态" class="portal-field-md">
            <el-select v-model="statusFilter" clearable placeholder="全部">
              <el-option label="待审核" value="PENDING_REVIEW" />
              <el-option label="审核通过" value="APPROVED" />
              <el-option label="驳回待提交" value="REJECTED" />
              <el-option label="草稿" value="DRAFT" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键词" class="portal-field-xl">
            <el-input v-model="keyword" clearable placeholder="项目名称/部门" @keyup.enter="onQuery" />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="onQuery">查询</el-button>
            <el-button @click="onReset">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table v-if="filtered.length" class="portal-table" :data="paged" stripe border size="small">
          <el-table-column label="项目名称" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ displayProjectName(row) }}</template>
          </el-table-column>
          <el-table-column label="部门" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.boundOrgName || currentDeptName }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag size="small" :type="canAuditRegister(row.registerStatus) ? 'warning' : 'info'">
                {{ registerStatusZh(row.registerStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openView(row)">查看</el-button>
              <el-button
                v-if="canAuditRegister(row.registerStatus)"
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
        <PortalPagination
          v-if="filtered.length"
          v-model:page="page"
          v-model:page-size="pageSize"
          :total="total"
        />
        <el-empty v-else description="暂无待办或匹配记录" :image-size="48" />
      </PageCard>
    </template>

    <el-dialog v-model="auditVisible" title="项目审核" width="480px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="项目">
          <el-input :model-value="auditTarget?.projectName || ''" disabled />
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
