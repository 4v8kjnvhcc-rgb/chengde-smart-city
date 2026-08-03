<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

interface Doc {
  id: number
  docCode: string
  title: string
  contentType: string
  storageKey: string
  indexStatus: string
}

const docs = ref<Doc[]>([])
const keyword = ref('')
const form = reactive({ title: '', contentType: 'application/pdf' })

async function load() {
  const res = await api.get('/unstructured/documents', { params: { keyword: keyword.value || undefined } })
  docs.value = res.data
}

async function register() {
  if (!form.title) {
    ElMessage.warning('请填写标题')
    return
  }
  await api.post('/unstructured/documents', form)
  ElMessage.success('文档已登记（SeaweedFS 路径占位）')
  form.title = ''
  load()
}

async function indexDoc(id: number) {
  await api.post(`/unstructured/documents/${id}/index`)
  ElMessage.success('已写入检索索引（ES 能力等价 POC）')
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      title="非结构化治理"
      description="MS4 POC：文档登记 + 对象存储键 + 检索索引状态（SeaweedFS/ES 能力等价）"
    />
    <PageCard>
      <el-form inline>
        <el-form-item label="标题">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.contentType" style="width: 160px">
            <el-option label="PDF" value="application/pdf" />
            <el-option label="图片" value="image/jpeg" />
            <el-option label="Office" value="application/msword" />
          </el-select>
        </el-form-item>
        <el-button type="primary" @click="register">登记</el-button>
        <el-form-item label="检索">
          <el-input v-model="keyword" clearable @clear="load" @keyup.enter="load" />
        </el-form-item>
        <el-button @click="load">查询</el-button>
      </el-form>
      <el-table class="portal-table" :data="docs" stripe>
        <el-table-column prop="docCode" label="编码" min-width="140" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="storageKey" label="存储键" min-width="220" />
        <el-table-column label="索引" width="120">
          <template #default="{ row }">{{ $statusLabel(row.indexStatus) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button
              v-if="row.indexStatus !== 'INDEXED'"
              link
              type="primary"
              @click="indexDoc(row.id)"
            >
              建索
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>
  </div>
</template>
