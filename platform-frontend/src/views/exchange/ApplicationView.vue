<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

interface Catalog {
  id: number
  catalogCode: string
  title: string
  description: string
  publishStatus: string
}

interface Demand {
  id: number
  demandTitle: string
  requesterOrg: string
  targetCatalogId: number
  status: string
  confirmNote: string
}

const catalogs = ref<Catalog[]>([])
const demands = ref<Demand[]>([])
const catalogForm = reactive({ title: '', description: '' })
const demandForm = reactive({
  demandTitle: '',
  requesterOrg: '机构A',
  targetCatalogId: undefined as number | undefined,
})

async function load() {
  const [c, d] = await Promise.all([
    api.get('/exchange/catalog'),
    api.get('/exchange/demands'),
  ])
  catalogs.value = c.data
  demands.value = d.data
}

async function createCatalog() {
  if (!catalogForm.title) {
    ElMessage.warning('请填写目录标题')
    return
  }
  await api.post('/exchange/catalog', catalogForm)
  ElMessage.success('目录已创建')
  catalogForm.title = ''
  catalogForm.description = ''
  load()
}

async function publish(id: number) {
  await api.post(`/exchange/catalog/${id}/publish`)
  ElMessage.success('目录已发布到共享门户')
  load()
}

async function submitDemand() {
  if (!demandForm.demandTitle) {
    ElMessage.warning('请填写需求标题')
    return
  }
  await api.post('/exchange/demands', demandForm)
  ElMessage.success('需求已提交')
  demandForm.demandTitle = ''
  load()
}

async function confirmDemand(id: number) {
  await api.post(`/exchange/demands/${id}/confirm`, { confirmNote: '供需对接确认，进入交换台账' })
  ElMessage.success('需求已确认')
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      title="应用平台 · 供需对接"
      description="演示场景：目录编目发布 + 数据需求填报确认"
    />
    <PageCard title="目录编目与发布">
      <el-form inline>
        <el-form-item label="标题">
          <el-input v-model="catalogForm.title" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="catalogForm.description" />
        </el-form-item>
        <el-button type="primary" @click="createCatalog">新建目录</el-button>
      </el-form>
      <el-table class="portal-table" :data="catalogs" stripe>
        <el-table-column prop="catalogCode" label="编码" min-width="140" />
        <el-table-column prop="title" label="标题" min-width="160" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">{{ $statusLabel(row.publishStatus) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button
              v-if="row.publishStatus !== 'PUBLISHED'"
              link
              type="primary"
              @click="publish(row.id)"
            >
              发布
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>
    <PageCard title="数据需求对接">
      <el-form inline>
        <el-form-item label="需求标题">
          <el-input v-model="demandForm.demandTitle" />
        </el-form-item>
        <el-form-item label="申请机构">
          <el-input v-model="demandForm.requesterOrg" />
        </el-form-item>
        <el-form-item label="目标目录">
          <el-select v-model="demandForm.targetCatalogId" clearable style="width: 200px">
            <el-option
              v-for="c in catalogs.filter((x) => x.publishStatus === 'PUBLISHED')"
              :key="c.id"
              :label="c.title"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-button type="primary" @click="submitDemand">提交需求</el-button>
      </el-form>
      <el-table class="portal-table" :data="demands" stripe>
        <el-table-column prop="demandTitle" label="需求" min-width="160" />
        <el-table-column prop="requesterOrg" label="申请方" min-width="120" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="confirmNote" label="确认说明" min-width="200" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'SUBMITTED'"
              link
              type="primary"
              @click="confirmDemand(row.id)"
            >
              确认
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>
  </div>
</template>
