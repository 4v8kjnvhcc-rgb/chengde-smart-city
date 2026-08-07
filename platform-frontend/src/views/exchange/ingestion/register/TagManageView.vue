<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { useAuthStore } from '@/stores/auth'
import { ingestionApi, useIngestionLoading, type AssetTag } from '../useIngestionHub'

const auth = useAuthStore()
const canDelete = computed(() => !!auth.isPlatformOrSystemAdmin)
const { loading, loadError, withLoad } = useIngestionLoading()
const tags = ref<AssetTag[]>([])
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const matching = ref(false)
const tagForm = reactive({ tagCode: '', tagName: '', ruleExpr: '', tagDesc: '' })

async function reload() {
  await withLoad(async () => {
    tags.value = (await ingestionApi.tags()).data
  })
}

function openDialog(row?: AssetTag) {
  editingId.value = row?.id ?? null
  tagForm.tagCode = row?.tagCode ?? ''
  tagForm.tagName = row?.tagName ?? ''
  tagForm.ruleExpr = row?.ruleExpr ?? ''
  tagForm.tagDesc = row?.tagDesc ?? ''
  dialogVisible.value = true
}

async function saveTag() {
  if (!tagForm.tagName.trim()) {
    ElMessage.warning('请填写标签名称')
    return
  }
  if (editingId.value) {
    await ingestionApi.updateTag(editingId.value, {
      tagName: tagForm.tagName,
      ruleExpr: tagForm.ruleExpr,
      tagDesc: tagForm.tagDesc,
    })
  } else {
    await ingestionApi.createTag({ ...tagForm })
  }
  dialogVisible.value = false
  ElMessage.success(editingId.value ? '标签已更新' : '标签已登记')
  await reload()
}

async function deleteTag(row: AssetTag) {
  if (!canDelete.value) {
    ElMessage.warning('仅平台管理员或超级管理员可删除数据标签')
    return
  }
  if (row.tagSource === 'STANDARD') {
    ElMessage.warning('标准主题类目标签不可删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除标签「${row.tagName}」？`, '删除确认', { type: 'warning' })
    await ingestionApi.deleteTag(row.id)
    ElMessage.success('已删除')
    await reload()
  } catch (e: unknown) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

/** 智能识别：仅触发识别统计，不写挂标绑定 */
async function runSmartMatch() {
  matching.value = true
  try {
    const res = await ingestionApi.matchTags()
    const matched = Number(res.data.matchedTags || 0)
    const hits = Number(res.data.totalHits || 0)
    ElMessage.success(`智能识别完成：${matched} 个标签，累计命中 ${hits}`)
    await reload()
  } catch {
    ElMessage.error('智能识别失败')
  } finally {
    matching.value = false
  }
}

onMounted(reload)
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard title="数据资产标签管理">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="openDialog()">新建标签</el-button>
          <el-button :loading="matching" @click="runSmartMatch">智能识别</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="tags" stripe>
        <el-table-column prop="tagCode" label="编码" width="140" show-overflow-tooltip />
        <el-table-column prop="tagName" label="名称" width="140" show-overflow-tooltip />
        <el-table-column prop="ruleExpr" label="识别规则" min-width="180" show-overflow-tooltip />
        <el-table-column prop="tagDesc" label="描述" min-width="160" show-overflow-tooltip />
        <el-table-column prop="hitCount" label="命中数" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button
              v-if="canDelete && row.tagSource !== 'STANDARD'"
              link
              type="danger"
              @click="deleteTag(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑标签' : '新建标签'" width="520px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="标签名称" required>
          <el-input v-model="tagForm.tagName" />
        </el-form-item>
        <el-form-item label="识别规则">
          <el-input
            v-model="tagForm.ruleExpr"
            type="textarea"
            :rows="2"
            placeholder="如：table_name LIKE %enterprise% 或 column_name IN (ID_NO,PERSON_NAME)"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="tagForm.tagDesc" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item v-if="!editingId" label="标签编码">
          <el-input v-model="tagForm.tagCode" placeholder="可选，自动生成" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTag">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
