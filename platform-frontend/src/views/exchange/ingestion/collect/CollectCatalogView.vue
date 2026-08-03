<script setup lang="ts">

import { onMounted, reactive, ref } from 'vue'

import PageCard from '@/components/common/PageCard.vue'

import { ingestionRegisterCache } from '../ingestion-register-cache'

import { ingestionApi, useIngestionLoading, type CategoryNode, type DataSource, type DataTable, type Dict, type Registry } from '../useIngestionHub'



const { loading, loadError, withLoad } = useIngestionLoading()

const registries = ref<Registry[]>([])

const categories = ref<CategoryNode[]>([])

const dataSources = ref<DataSource[]>([])

const tables = ref<DataTable[]>([])

const dicts = ref<Dict[]>([])

const dictsLoaded = ref(false)



const registryForm = reactive({

  title: '',

  categoryPath: '政务数据/主题库',

  secretLevel: 'INTERNAL',

  refTableId: undefined as number | undefined,

  refSourceId: undefined as number | undefined,

  publishNote: '',

})

const catForm = reactive({ nodeName: '', parentId: 0, secretLevel: 'INTERNAL', description: '' })



async function loadCore() {

  const [reg, cat] = await Promise.all([

    ingestionApi.registries(),

    ingestionApi.categories(),

  ])

  registries.value = reg.data

  categories.value = cat.data

}



async function loadRegisterRefs(force = false) {

  const [ds, tb] = await Promise.all([

    ingestionRegisterCache.dataSources(force),

    ingestionRegisterCache.tables(force),

  ])

  dataSources.value = ds

  tables.value = tb

}



async function loadDicts(force = false) {

  if (!force && dictsLoaded.value) return

  dicts.value = await ingestionRegisterCache.dicts(force)

  dictsLoaded.value = true

}



async function reload(opts?: { force?: boolean; includeRefs?: boolean }) {

  const force = opts?.force ?? false

  await withLoad(async () => {

    await loadCore()

    if (opts?.includeRefs !== false) await loadRegisterRefs(force)

  })

}



async function onDictCollapseChange(names: string | string[]) {

  const active = Array.isArray(names) ? names : [names]

  if (!active.includes('dict') || dictsLoaded.value) return

  await withLoad(() => loadDicts())

}



async function createRegistry() {

  if (!registryForm.title) return

  const tb = tables.value.find((t) => t.id === registryForm.refTableId)

  const ds = dataSources.value.find((s) => s.id === registryForm.refSourceId)

  await ingestionApi.createRegistry({

    title: registryForm.title,

    categoryPath: registryForm.categoryPath,

    secretLevel: registryForm.secretLevel,

    refTableId: registryForm.refTableId,

    refSourceId: registryForm.refSourceId,

    assetSummary: tb ? `关联表 ${tb.tableName}` : ds ? `关联源 ${ds.sourceName}` : registryForm.publishNote,

  })

  registryForm.title = ''

  await reload({ force: true, includeRefs: false })

}



async function approve(id: number) {

  await ingestionApi.approveRegistry(id, { action: 'APPROVE' })

  await reload({ force: true, includeRefs: false })

}



async function createCategory() {

  if (!catForm.nodeName) return

  await ingestionApi.createCategory({ ...catForm })

  catForm.nodeName = ''

  await reload({ force: true, includeRefs: false })

}



onMounted(() => reload())

</script>



<template>

  <div v-loading="loading">

    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />

    <el-alert type="info" :closable="false" show-icon style="margin-bottom:16px"

      title="目录编目关联「数据资产登记」中的数据源、物理表与字典；编目信息在界面填写，无需修改后台 JSON。" />



    <PageCard title="数据资源编目管理">

      <el-form label-width="120px">

        <el-form-item label="目录标题"><el-input v-model="registryForm.title" style="max-width:360px" /></el-form-item>

        <el-form-item label="分类路径"><el-input v-model="registryForm.categoryPath" style="max-width:360px" /></el-form-item>

        <el-form-item label="关联数据源">

          <el-select v-model="registryForm.refSourceId" clearable filterable style="min-width:280px">

            <el-option v-for="s in dataSources" :key="s.id" :label="`${s.sourceName}（${$statusLabel(s.sourceType)}）`" :value="s.id" />

          </el-select>

        </el-form-item>

        <el-form-item label="关联登记表">

          <el-select v-model="registryForm.refTableId" clearable filterable style="min-width:280px">

            <el-option v-for="t in tables" :key="t.id" :label="`${t.tableName}（${t.tableCode}）`" :value="t.id" />

          </el-select>

        </el-form-item>

        <el-form-item label="涉密等级">

          <el-select v-model="registryForm.secretLevel" style="width:160px">

            <el-option label="内部" value="INTERNAL" />

            <el-option label="秘密" value="SECRET" />

            <el-option label="机密" value="CONFIDENTIAL" />

          </el-select>

        </el-form-item>

        <el-form-item label="发布说明"><el-input v-model="registryForm.publishNote" type="textarea" :rows="2" style="max-width:480px" /></el-form-item>

        <el-form-item>

          <el-button type="primary" @click="createRegistry">新建编目</el-button>

        </el-form-item>

      </el-form>

      <el-table :data="registries" stripe size="small" style="margin-top:12px">

        <el-table-column prop="title" label="标题" min-width="160" />

        <el-table-column prop="categoryPath" label="分类" min-width="140" />

        <el-table-column label="涉密" width="90">
          <template #default="{ row }">{{ $statusLabel(row.secretLevel) }}</template>
        </el-table-column>

        <el-table-column label="发布" width="100">
          <template #default="{ row }">{{ $statusLabel(row.publishStatus) }}</template>
        </el-table-column>

        <el-table-column label="审批" width="100">
          <template #default="{ row }">{{ $statusLabel(row.approvalStatus) }}</template>
        </el-table-column>

      </el-table>

    </PageCard>



    <PageCard title="数据资源分类">

      <el-form inline class="portal-inline-form portal-inline-form--block">

        <el-form-item label="分类名称" class="portal-field-md"><el-input v-model="catForm.nodeName" /></el-form-item>

        <el-form-item label="父节点" class="portal-field-md">

          <el-select v-model="catForm.parentId">

            <el-option label="根节点" :value="0" />

            <el-option v-for="c in categories" :key="c.id" :label="c.nodeName" :value="c.id" />

          </el-select>

        </el-form-item>

        <el-form-item label="涉密等级" class="portal-field-sm">

          <el-select v-model="catForm.secretLevel">

            <el-option label="内部" value="INTERNAL" />

            <el-option label="秘密" value="SECRET" />

          </el-select>

        </el-form-item>

        <el-form-item class="portal-form-actions"><el-button type="primary" @click="createCategory">新增分类</el-button></el-form-item>

      </el-form>

      <el-table :data="categories" stripe size="small">

        <el-table-column prop="nodeName" label="名称" />

        <el-table-column label="涉密" width="90">
          <template #default="{ row }">{{ $statusLabel(row.secretLevel) }}</template>
        </el-table-column>

      </el-table>

      <el-collapse style="margin-top:12px" @change="onDictCollapseChange">

        <el-collapse-item title="可引用登记侧字典（只读）" name="dict">

          <el-table :data="dicts" size="small" stripe>

            <el-table-column prop="dictName" label="字典" />

            <el-table-column prop="dictCode" label="编码" width="140" />

            <el-table-column prop="itemCount" label="项数" width="80" />

          </el-table>

        </el-collapse-item>

      </el-collapse>

    </PageCard>



    <PageCard title="资源目录注册发布">

      <el-table :data="registries.filter(r => r.publishStatus === 'PUBLISHED' || r.approvalStatus === 'APPROVED')" stripe size="small">

        <el-table-column prop="title" label="目录" />

        <el-table-column prop="categoryPath" label="分类" />

        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ $statusLabel(row.publishStatus) }}</template>
        </el-table-column>

      </el-table>

    </PageCard>



    <PageCard title="数据资源目录审批">

      <el-table :data="registries" stripe size="small">

        <el-table-column prop="title" label="标题" />

        <el-table-column label="审批状态" width="120">
          <template #default="{ row }">{{ $statusLabel(row.approvalStatus) }}</template>
        </el-table-column>

        <el-table-column label="操作" width="100">

          <template #default="{ row }">

            <el-button v-if="row.approvalStatus === 'PENDING'" link type="primary" @click="approve(row.id)">四性审批通过</el-button>

          </template>

        </el-table-column>

      </el-table>

    </PageCard>

  </div>

</template>


