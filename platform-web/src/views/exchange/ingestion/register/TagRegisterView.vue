<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { ingestionApi, useIngestionLoading, type AssetTag } from '../useIngestionHub'

const props = defineProps<{ module: string }>()
const { loading, loadError, withLoad } = useIngestionLoading()
const tags = ref<AssetTag[]>([])
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const tagForm = reactive({ tagCode: '', tagName: '', ruleExpr: '', tagDesc: '' })

const isManage = computed(() => props.module === 'm045')
const title = computed(() => (isManage.value ? '数据资产标签管理' : '数据资产标签登记'))
const subtitle = computed(() => (isManage.value
  ? '根据业务含义构建丰富的数据标签，提供智能识别规则定义，快速标记数据，助力了解、定位数据的业务。'
  : '登记标签名称、识别规则与描述，供标签管理模块联动识别。'))

function openDialog(row?: AssetTag) {
  editingId.value = row?.id ?? null
  tagForm.tagCode = row?.tagCode ?? ''
  tagForm.tagName = row?.tagName ?? ''
  tagForm.ruleExpr = row?.ruleExpr ?? ''
  tagForm.tagDesc = row?.tagDesc ?? ''
  dialogVisible.value = true
}

async function reload() {
  await withLoad(async () => { tags.value = (await ingestionApi.tags()).data })
}

async function saveTag() {
  if (!tagForm.tagName) return
  if (editingId.value) {
    await ingestionApi.updateTag(editingId.value, { tagName: tagForm.tagName, ruleExpr: tagForm.ruleExpr, tagDesc: tagForm.tagDesc })
  } else {
    await ingestionApi.createTag({ ...tagForm })
  }
  dialogVisible.value = false
  ElMessage.success(editingId.value ? '标签已更新' : '标签已登记')
  await reload()
}

async function runMatch() {
  const res = await ingestionApi.matchTags()
  ElMessage.success(`智能识别完成：${res.data.matchedTags} 个标签，累计命中 ${res.data.totalHits}`)
  await reload()
}

onMounted(reload)
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard :title="title">
      <p class="tag-desc">{{ subtitle }}</p>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="openDialog()">{{ isManage ? '新建标签' : '登记标签' }}</el-button>
          <el-button v-if="isManage" @click="runMatch">执行智能识别</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="tags" stripe>
        <el-table-column prop="tagCode" label="编码" width="140" />
        <el-table-column prop="tagName" label="名称" width="140" />
        <el-table-column prop="ruleExpr" label="识别规则" min-width="180" />
        <el-table-column prop="tagDesc" label="描述" min-width="160" />
        <el-table-column prop="hitCount" label="命中数" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column v-if="isManage" label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑标签' : '登记标签'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="标签名称"><el-input v-model="tagForm.tagName" /></el-form-item>
        <el-form-item label="识别规则">
          <el-input v-model="tagForm.ruleExpr" type="textarea" :rows="2" placeholder="如 table_name LIKE %enterprise% 或 column_name IN (ID_NO,PERSON_NAME)" />
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="tagForm.tagDesc" type="textarea" :rows="2" /></el-form-item>
        <el-form-item v-if="!editingId" label="标签编码"><el-input v-model="tagForm.tagCode" placeholder="可选，自动生成" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTag">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tag-desc { font-size: 13px; color: #606266; margin: 0 0 12px; }
</style>
