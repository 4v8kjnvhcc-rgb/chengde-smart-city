<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { SECURITY_OPTIONS, TAG_OPTIONS, ENTRY_TYPE_OPTIONS } from './meta-labels'
import { statusLabel } from '@/utils/status-label'

interface Entry {
  id: number
  entryCode: string
  entryName: string
  entryType: string
  description?: string
  tags?: string
  keywords?: string
  securityLevel?: string
  businessDomain?: string
  ownerName?: string
  dataLayer?: string
  databaseName?: string
  changeFlag?: string
  status: string
}

interface Notice {
  id: number
  entryCode?: string
  title: string
  detail?: string
  status: string
  createdAt?: string
}

interface Suggestion {
  itemName: string
  count: number
  itemType: string
}

const entries = ref<Entry[]>([])
const notices = ref<Notice[]>([])
const suggestions = ref<Suggestion[]>([])
const selectedId = ref<number | undefined>()
const tagList = ref<string[]>([])
const searching = ref(false)

const form = reactive({
  id: undefined as number | undefined,
  entryCode: '',
  entryName: '',
  entryType: 'TABLE',
  description: '',
  businessDomain: '',
  ownerName: '',
  keywords: '',
  securityLevel: '',
})

const createForm = reactive({
  entryName: '',
  entryType: 'TABLE',
  description: '',
  businessDomain: '',
  ownerName: '',
  keywords: '',
  securityLevel: '',
  tags: [] as string[],
})

const maintainableEntries = computed(() =>
  entries.value.filter(e => e.entryType === 'TABLE' || e.entryType === 'SOURCE'),
)

const entryOptions = computed(() =>
  maintainableEntries.value.map(e => ({
    value: e.id,
    label: `${e.entryName}（${statusLabel(e.entryType)}）`,
    entry: e,
  })),
)

function tagsToArray(tags?: string): string[] {
  if (!tags) return []
  return tags.split(/[,，]/).map(t => t.trim()).filter(Boolean)
}

function tagsToString(list: string[]): string {
  return list.filter(Boolean).join(',')
}

function fillForm(entry: Entry) {
  form.id = entry.id
  form.entryCode = entry.entryCode
  form.entryName = entry.entryName
  form.entryType = entry.entryType
  form.description = entry.description || ''
  form.businessDomain = entry.businessDomain || ''
  form.ownerName = entry.ownerName || ''
  form.keywords = entry.keywords || ''
  form.securityLevel = entry.securityLevel || ''
  tagList.value = tagsToArray(entry.tags)
}

function onSelectEntry(id: number | undefined) {
  if (!id) {
    form.id = undefined
    form.entryCode = ''
    form.entryName = ''
    tagList.value = []
    return
  }
  const entry = maintainableEntries.value.find(e => e.id === id)
  if (entry) fillForm(entry)
}

async function remoteSearch(keyword: string) {
  if (!keyword?.trim()) return
  searching.value = true
  try {
    const res = await api.get('/governance/platform/metadata/catalog/search', {
      params: { keyword: keyword.trim() },
    })
    const found = (res.data || []) as Entry[]
    for (const e of found) {
      if ((e.entryType === 'TABLE' || e.entryType === 'SOURCE') && !entries.value.some(x => x.id === e.id)) {
        entries.value.push(e)
      }
    }
  } finally {
    searching.value = false
  }
}

async function loadEntries() {
  entries.value = (await api.get('/governance/platform/metadata/entries')).data || []
}

async function loadNotices() {
  notices.value = (await api.get('/governance/platform/metadata/notices')).data || []
}

async function loadSuggestions() {
  suggestions.value = (await api.get('/governance/platform/metadata/maintain/suggest-standards')).data || []
}

async function saveUpdate() {
  if (!form.id || !form.entryName.trim()) {
    ElMessage.warning('请选择条目并填写名称')
    return
  }
  await api.post('/governance/platform/metadata/maintain', {
    id: form.id,
    entryName: form.entryName,
    description: form.description,
    businessDomain: form.businessDomain,
    ownerName: form.ownerName,
    tags: tagsToString(tagList.value),
    keywords: form.keywords,
    securityLevel: form.securityLevel || undefined,
    mode: 'MANUAL',
  })
  ElMessage.success('已更新元数据条目')
  await loadEntries()
  await loadNotices()
}

async function saveCreate() {
  if (!createForm.entryName.trim()) {
    ElMessage.warning('请填写名称')
    return
  }
  await api.post('/governance/platform/metadata/maintain', {
    entryName: createForm.entryName,
    entryType: createForm.entryType,
    description: createForm.description,
    businessDomain: createForm.businessDomain,
    ownerName: createForm.ownerName,
    tags: tagsToString(createForm.tags),
    keywords: createForm.keywords,
    securityLevel: createForm.securityLevel || undefined,
    mode: 'MANUAL',
  })
  ElMessage.success('已手工补录')
  createForm.entryName = ''
  createForm.description = ''
  createForm.businessDomain = ''
  createForm.ownerName = ''
  createForm.keywords = ''
  createForm.securityLevel = ''
  createForm.tags = []
  await loadEntries()
  await loadNotices()
}

async function promote(item: Suggestion) {
  await api.post('/governance/platform/metadata/maintain/promote-standard', {
    itemName: item.itemName,
    itemType: item.itemType,
  })
  ElMessage.success(`已沉淀标准：${item.itemName}`)
  await loadSuggestions()
}

async function copyEntryCode() {
  if (!form.entryCode) return
  try {
    await navigator.clipboard.writeText(form.entryCode)
    ElMessage.success('编码已复制')
  } catch {
    ElMessage.info(form.entryCode)
  }
}

const selectedEntry = computed(() => maintainableEntries.value.find(e => e.id === form.id))

onMounted(async () => {
  await loadEntries()
  await Promise.all([loadNotices(), loadSuggestions()])
})
</script>

<template>
  <PageCard title="元数据维护">
    <el-form label-width="88px" class="meta-maintain-form">
      <el-form-item label="选择条目">
        <el-select
          v-model="selectedId"
          filterable
          remote
          clearable
          reserve-keyword
          placeholder="搜索 TABLE / SOURCE 条目"
          class="portal-field-xl"
          style="width: 100%; max-width: 480px"
          :remote-method="remoteSearch"
          :loading="searching"
          @change="onSelectEntry"
        >
          <el-option v-for="o in entryOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>

      <template v-if="form.id">
        <el-form-item label="名称" required>
          <el-input v-model="form.entryName" class="portal-field-xl" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="业务域">
              <el-input v-model="form.businessDomain" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="责任人">
              <el-input v-model="form.ownerName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="标签">
              <el-select v-model="tagList" multiple collapse-tags placeholder="选择标签" style="width: 100%">
                <el-option v-for="t in TAG_OPTIONS" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="安全分级">
              <el-select v-model="form.securityLevel" clearable placeholder="选择分级" style="width: 100%">
                <el-option v-for="o in SECURITY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="关键字">
          <el-input v-model="form.keywords" placeholder="逗号分隔" />
        </el-form-item>
        <el-row v-if="selectedEntry?.dataLayer || selectedEntry?.databaseName" :gutter="16">
          <el-col v-if="selectedEntry?.dataLayer" :span="12">
            <el-form-item label="数据分层">
              <el-input :model-value="$statusLabel(selectedEntry.dataLayer)" disabled />
            </el-form-item>
          </el-col>
          <el-col v-if="selectedEntry?.databaseName" :span="12">
            <el-form-item label="库名">
              <el-input :model-value="selectedEntry.databaseName" disabled />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item>
          <el-button type="primary" @click="saveUpdate">保存更新</el-button>
        </el-form-item>

        <el-collapse>
          <el-collapse-item title="高级信息" name="advanced">
            <el-form-item label="条目编码">
              <el-input :model-value="form.entryCode" disabled style="max-width: 360px">
                <template #append>
                  <el-button @click="copyEntryCode">复制</el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-collapse-item>
        </el-collapse>
      </template>
    </el-form>

    <el-collapse style="margin-top: 16px">
      <el-collapse-item title="手工补录（新建条目）" name="create">
        <el-form label-width="88px">
          <el-form-item label="名称" required>
            <el-input v-model="createForm.entryName" class="portal-field-xl" />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="createForm.entryType">
              <el-option
                v-for="o in ENTRY_TYPE_OPTIONS.filter(x => x.value === 'TABLE' || x.value === 'SOURCE')"
                :key="o.value"
                :label="o.label"
                :value="o.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="说明">
            <el-input v-model="createForm.description" type="textarea" :rows="2" />
          </el-form-item>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="业务域">
                <el-input v-model="createForm.businessDomain" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="责任人">
                <el-input v-model="createForm.ownerName" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="标签">
                <el-select v-model="createForm.tags" multiple collapse-tags style="width: 100%">
                  <el-option v-for="t in TAG_OPTIONS" :key="t" :label="t" :value="t" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="安全分级">
                <el-select v-model="createForm.securityLevel" clearable style="width: 100%">
                  <el-option v-for="o in SECURITY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="关键字">
            <el-input v-model="createForm.keywords" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="saveCreate">提交补录</el-button>
          </el-form-item>
        </el-form>
      </el-collapse-item>
    </el-collapse>

    <el-row :gutter="12" style="margin-top: 20px">
      <el-col :span="14">
        <h4>元数据条目</h4>
        <el-table :data="maintainableEntries" stripe size="small" max-height="280">
          <el-table-column prop="entryName" label="名称" min-width="120" />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ $statusLabel(row.entryType) }}</template>
          </el-table-column>
          <el-table-column prop="tags" label="标签" width="120" show-overflow-tooltip />
          <el-table-column label="分级" width="80">
            <template #default="{ row }">{{ $statusLabel(row.securityLevel) }}</template>
          </el-table-column>
          <el-table-column label="变更" width="80">
            <template #default="{ row }">{{ $statusLabel(row.changeFlag) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70">
            <template #default="{ row }">
              <el-button link type="primary" @click="selectedId = row.id; onSelectEntry(row.id)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-col>
      <el-col :span="10">
        <h4>变更通知</h4>
        <el-table :data="notices" stripe size="small" max-height="280">
          <el-table-column prop="title" label="标题" show-overflow-tooltip />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
          </el-table-column>
        </el-table>
        <h4 style="margin-top: 12px">标准沉淀建议</h4>
        <el-table :data="suggestions" stripe size="small" max-height="200">
          <el-table-column prop="itemName" label="字段名" />
          <el-table-column prop="count" label="频次" width="70" />
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="primary" @click="promote(row)">沉淀</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-col>
    </el-row>
  </PageCard>
</template>

<style scoped>
.meta-maintain-form { max-width: 720px; }
</style>
