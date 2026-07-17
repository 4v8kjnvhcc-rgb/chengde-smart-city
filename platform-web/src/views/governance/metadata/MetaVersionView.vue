<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import PageCard from '@/components/common/PageCard.vue'

interface MetaModel { id: number; modelNameZh: string }
interface Version {
  id: number
  targetType: string
  targetId: number
  versionNo: number
  changeSummary?: string
  createdBy?: string
  createdAt?: string
  snapshotJson?: string
}

interface FieldDiff {
  added?: string[]
  removed?: string[]
  changed?: string[]
}

const models = ref<MetaModel[]>([])
const versions = ref<Version[]>([])
const compare = ref<{
  sameSnapshot?: boolean
  basicDiff?: Array<{ field: string; left: string; right: string }>
  fieldDiff?: FieldDiff
} | null>(null)
const form = reactive({
  targetType: 'MODEL',
  targetId: undefined as number | undefined,
  leftId: undefined as number | undefined,
  rightId: undefined as number | undefined,
})

async function loadModels() {
  models.value = (await api.get('/governance/platform/metadata/models')).data || []
}

async function loadVersions() {
  if (!form.targetId) {
    versions.value = []
    return
  }
  versions.value = (await api.get('/governance/platform/metadata/versions', {
    params: { targetType: form.targetType, targetId: form.targetId },
  })).data || []
}

async function doCompare() {
  if (!form.leftId || !form.rightId) return
  compare.value = (await api.get('/governance/platform/metadata/versions/compare', {
    params: { leftId: form.leftId, rightId: form.rightId },
  })).data
}

onMounted(loadModels)
</script>

<template>
  <PageCard title="M093/M094 版本管理">
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="目标" class="portal-field-xl">
        <el-select v-model="form.targetId" clearable @change="loadVersions">
          <el-option v-for="m in models" :key="m.id" :label="m.modelNameZh" :value="m.id" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions"><el-button type="primary" @click="loadVersions">刷新历史</el-button></el-form-item>
    </el-form>
    <el-table :data="versions" stripe size="small">
      <el-table-column prop="versionNo" label="版本" width="80" />
      <el-table-column prop="changeSummary" label="变更摘要" />
      <el-table-column prop="createdBy" label="操作人" width="100" />
      <el-table-column prop="createdAt" label="时间" width="170" />
    </el-table>
    <el-divider />
    <el-form inline class="portal-inline-form">
      <el-form-item label="左版本" class="portal-field-md">
        <el-select v-model="form.leftId" clearable>
          <el-option v-for="v in versions" :key="v.id" :label="`v${v.versionNo}`" :value="v.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="右版本" class="portal-field-md">
        <el-select v-model="form.rightId" clearable>
          <el-option v-for="v in versions" :key="'r'+v.id" :label="`v${v.versionNo}`" :value="v.id" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions"><el-button @click="doCompare">版本对比</el-button></el-form-item>
    </el-form>
    <template v-if="compare">
      <el-alert type="info" :closable="false" :title="`快照相同=${compare.sameSnapshot}`" style="margin-bottom:12px" />
      <h4>基本字段差异</h4>
      <el-table :data="compare.basicDiff || []" stripe size="small" style="margin-bottom:16px">
        <el-table-column prop="field" label="字段" width="140" />
        <el-table-column prop="left" label="左版本" />
        <el-table-column prop="right" label="右版本" />
      </el-table>
      <h4>content_json 字段级差异</h4>
      <div class="version-diff-bars">
        <div v-for="f in compare.fieldDiff?.added || []" :key="'a'+f" class="version-diff-bar version-diff-bar--add">+ {{ f }}</div>
        <div v-for="f in compare.fieldDiff?.removed || []" :key="'r'+f" class="version-diff-bar version-diff-bar--remove">− {{ f }}</div>
        <div v-for="f in compare.fieldDiff?.changed || []" :key="'c'+f" class="version-diff-bar version-diff-bar--change">~ {{ f }}</div>
        <el-empty v-if="!(compare.fieldDiff?.added?.length || compare.fieldDiff?.removed?.length || compare.fieldDiff?.changed?.length)" description="无字段差异" />
      </div>
    </template>
  </PageCard>
</template>

<style scoped>
.version-diff-bars { display: flex; flex-direction: column; gap: 6px; }
.version-diff-bar { padding: 6px 10px; border-radius: 4px; font-family: monospace; font-size: 13px; }
.version-diff-bar--add { background: #e8f5e9; color: #2e7d32; border-left: 4px solid #4caf50; }
.version-diff-bar--remove { background: #ffebee; color: #c62828; border-left: 4px solid #f44336; }
.version-diff-bar--change { background: #fff8e1; color: #f57f17; border-left: 4px solid #ffc107; }
</style>
