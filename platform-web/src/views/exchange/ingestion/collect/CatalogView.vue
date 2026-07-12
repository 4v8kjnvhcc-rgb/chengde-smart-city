<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import PageCard from '@/components/common/PageCard.vue'
import { ingestionApi, useIngestionLoading, type CategoryNode, type Registry } from '../useIngestionHub'

const props = defineProps<{ module: string }>()
const { loading, loadError, withLoad } = useIngestionLoading()
const registries = ref<Registry[]>([])
const categories = ref<CategoryNode[]>([])
const registryForm = reactive({ title: '', categoryPath: '政务数据/主题库', secretLevel: 'INTERNAL' })
const catForm = reactive({ nodeName: '', parentId: 0, secretLevel: 'INTERNAL' })

const title = computed(() => ({
  m065: '数据资源编目管理', m066: '数据资源分类', m067: '资源目录注册发布', m068: '数据资源目录审批',
}[props.module] || '指标与目录'))

async function reload() {
  await withLoad(async () => {
    registries.value = (await ingestionApi.registries()).data
    categories.value = (await ingestionApi.categories()).data
  })
}

async function createRegistry() {
  if (!registryForm.title) return
  await ingestionApi.createRegistry({ ...registryForm })
  registryForm.title = ''
  await reload()
}

async function approve(id: number) {
  await ingestionApi.approveRegistry(id, { action: 'APPROVE' })
  await reload()
}

async function createCategory() {
  if (!catForm.nodeName) return
  await ingestionApi.createCategory({ ...catForm })
  catForm.nodeName = ''
  await reload()
}

onMounted(reload)
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard v-if="module === 'm066'" title="数据资源分类">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="分类名" class="portal-field-md"><el-input v-model="catForm.nodeName" /></el-form-item>
        <el-form-item label="父节点" class="portal-field-md">
          <el-select v-model="catForm.parentId">
            <el-option label="根节点" :value="0" />
            <el-option v-for="c in categories" :key="c.id" :label="c.nodeName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="createCategory">新增分类</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="categories" stripe size="small">
        <el-table-column prop="nodeCode" label="编码" width="140" />
        <el-table-column prop="nodeName" label="名称" />
        <el-table-column prop="parentId" label="父ID" width="80" />
        <el-table-column prop="secretLevel" label="涉密" width="90" />
      </el-table>
    </PageCard>
    <PageCard :title="title">
      <el-form v-if="module === 'm065'" inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="标题" class="portal-field-lg"><el-input v-model="registryForm.title" /></el-form-item>
        <el-form-item label="分类" class="portal-field-lg"><el-input v-model="registryForm.categoryPath" /></el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="createRegistry">新建编目</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="registries" stripe>
        <el-table-column prop="registryCode" label="编码" width="140" />
        <el-table-column prop="title" label="标题" min-width="160" />
        <el-table-column prop="categoryPath" label="分类" min-width="140" />
        <el-table-column prop="secretLevel" label="涉密" width="90" />
        <el-table-column prop="publishStatus" label="发布" width="100" />
        <el-table-column prop="approvalStatus" label="审批" width="100" />
        <el-table-column v-if="module === 'm068'" label="操作" width="90">
          <template #default="{ row }">
            <el-button v-if="row.approvalStatus === 'PENDING'" link @click="approve(row.id)">四性审批</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>
  </div>
</template>
