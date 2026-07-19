<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import PageCard from '@/components/common/PageCard.vue'
import { useAuthStore } from '@/stores/auth'
import { ingestionApi, type AssetTag } from '@/views/exchange/ingestion/useIngestionHub'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const keyword = ref('')
const standardTree = ref<AssetTag[]>([])
const customTags = ref<AssetTag[]>([])
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const tagForm = reactive({ tagCode: '', tagName: '', ruleExpr: '', tagDesc: '' })
const denied = ref(false)

const canAccess = computed(() =>
  auth.hasPermission('system:tag:list')
  || auth.hasPermission('system:tag:edit')
  || auth.hasPermission('system:tag:query'),
)
const canEdit = computed(() => auth.hasPermission('system:tag:edit'))

const filteredTree = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return standardTree.value
  return standardTree.value
    .map((n) => {
      const selfHit = matchTag(n, kw)
      const children = (n.children || []).filter((c) => matchTag(c, kw))
      if (selfHit || children.length) {
        return { ...n, children: selfHit && !children.length ? (n.children || []) : children }
      }
      return null
    })
    .filter(Boolean) as AssetTag[]
})

const filteredCustom = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return customTags.value
  return customTags.value.filter((t) => matchTag(t, kw))
})

function matchTag(t: AssetTag, kw: string) {
  return [t.stdCode, t.tagCode, t.tagName, t.tagDesc, t.ruleExpr]
    .filter(Boolean)
    .some((s) => String(s).toLowerCase().includes(kw))
}

async function reload() {
  loading.value = true
  try {
    const res = await ingestionApi.tagTree()
    standardTree.value = res.data.standardTree || []
    customTags.value = res.data.customTags || []
  } catch {
    ElMessage.error('加载标签库失败')
  } finally {
    loading.value = false
  }
}

function openDialog(row?: AssetTag) {
  if (!canEdit.value) {
    ElMessage.warning('无权维护标签库')
    return
  }
  if (row?.tagSource === 'STANDARD') {
    editingId.value = row.id
    tagForm.tagCode = row.tagCode
    tagForm.tagName = row.tagName
    tagForm.ruleExpr = row.ruleExpr || ''
    tagForm.tagDesc = row.tagDesc || ''
    dialogVisible.value = true
    return
  }
  editingId.value = row?.id ?? null
  tagForm.tagCode = row?.tagCode ?? ''
  tagForm.tagName = row?.tagName ?? ''
  tagForm.ruleExpr = row?.ruleExpr ?? ''
  tagForm.tagDesc = row?.tagDesc ?? ''
  dialogVisible.value = true
}

async function saveTag() {
  if (!canEdit.value) {
    ElMessage.warning('无权维护标签库')
    return
  }
  if (!tagForm.tagName.trim() && !editingId.value) {
    ElMessage.warning('请填写标签名称')
    return
  }
  try {
    if (editingId.value) {
      const body: Record<string, unknown> = {
        ruleExpr: tagForm.ruleExpr,
        tagDesc: tagForm.tagDesc,
      }
      if (!editingStandard.value) {
        body.tagName = tagForm.tagName.trim()
      }
      await ingestionApi.updateTag(editingId.value, body)
      ElMessage.success('已保存')
    } else {
      await ingestionApi.createTag({
        tagCode: tagForm.tagCode.trim() || undefined,
        tagName: tagForm.tagName.trim(),
        ruleExpr: tagForm.ruleExpr,
        tagDesc: tagForm.tagDesc,
      })
      ElMessage.success('扩展标签已创建')
    }
    dialogVisible.value = false
    await reload()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  }
}

function flatten(nodes: AssetTag[]): AssetTag[] {
  const out: AssetTag[] = []
  for (const n of nodes) {
    out.push(n)
    if (n.children?.length) out.push(...flatten(n.children))
  }
  return out
}

const editingStandard = computed(() => {
  if (!editingId.value) return false
  return flatten(standardTree.value).some((t) => t.id === editingId.value && t.tagSource === 'STANDARD')
})

onMounted(async () => {
  if (!auth.permissions.length && auth.isLoggedIn) {
    try {
      await auth.fetchProfile()
    } catch { /* ignore */ }
  }
  if (!canAccess.value) {
    denied.value = true
    ElMessage.warning('无权访问标签库，已返回工作台')
    router.replace('/dashboard')
    return
  }
  await reload()
})
</script>

<template>
  <div v-loading="loading">
    <el-empty v-if="denied" description="无权访问标签库" />
    <PageCard v-else title="标签管理">
      <p class="hint">维护 GB/T 21063.4 标准主题类目（只读）与扩展标签。挂标及规则生成请在「数据资产标签登记」完成登记时确认。</p>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="检索" class="portal-field-xl">
          <el-input v-model="keyword" clearable placeholder="编码/名称/描述模糊搜索" />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button v-if="canEdit" type="primary" @click="openDialog()">新建扩展标签</el-button>
          <el-button @click="reload">刷新</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="16">
        <el-col :xs="24" :md="14">
          <div class="block-title">标准主题类目</div>
          <el-tree
            :data="filteredTree"
            node-key="id"
            :props="{ label: 'tagName', children: 'children' }"
            default-expand-all
          >
            <template #default="{ data }">
              <span class="tree-node">
                <span class="tree-code">{{ data.stdCode }}</span>
                <span>{{ data.tagName }}</span>
                <el-button
                  v-if="canEdit && data.level === 2"
                  link
                  type="primary"
                  style="margin-left:8px"
                  @click.stop="openDialog(data)"
                >规则</el-button>
              </span>
            </template>
          </el-tree>
        </el-col>
        <el-col :xs="24" :md="10">
          <div class="block-title">扩展标签</div>
          <el-table :data="filteredCustom" stripe size="small">
            <el-table-column prop="tagCode" label="编码" width="120" show-overflow-tooltip />
            <el-table-column prop="tagName" label="名称" min-width="100" />
            <el-table-column prop="ruleExpr" label="识别规则" min-width="120" show-overflow-tooltip />
            <el-table-column v-if="canEdit" label="操作" width="70" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-col>
      </el-row>
    </PageCard>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? (editingStandard ? '维护标准类目规则' : '编辑扩展标签') : '新建扩展标签'"
      width="520px"
    >
      <el-form label-width="96px">
        <el-form-item label="标签名称">
          <el-input v-model="tagForm.tagName" :disabled="editingStandard" />
        </el-form-item>
        <el-form-item label="识别规则">
          <el-input v-model="tagForm.ruleExpr" type="textarea" :rows="2" placeholder="可选；也可在资产标签登记完成时自动生成" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="tagForm.tagDesc" type="textarea" :rows="2" :disabled="editingStandard" />
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

<style scoped>
.hint { font-size: 13px; color: #606266; margin: 0 0 12px; }
.block-title { font-size: 13px; font-weight: 600; margin-bottom: 8px; }
.tree-node { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; }
.tree-code { color: #909399; font-family: ui-monospace, monospace; font-size: 12px; }
</style>
