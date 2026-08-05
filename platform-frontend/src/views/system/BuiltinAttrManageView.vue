<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/api/http'
import PageCard from '@/components/common/PageCard.vue'

type EditCtrlKey =
  | 'columnCode'
  | 'columnName'
  | 'dataType'
  | 'lengthVal'
  | 'componentType'
  | 'nullableFlag'

const ATTRS: Array<{ key: EditCtrlKey; label: string; desc: string }> = [
  { key: 'columnCode', label: '属性代码', desc: '字段技术编码，如 ENT_CODE' },
  { key: 'columnName', label: '属性名称', desc: '面向业务的显示名称' },
  { key: 'dataType', label: '数据类型', desc: '如 VARCHAR、BIGINT' },
  { key: 'lengthVal', label: '长度', desc: '字段长度/精度' },
  { key: 'componentType', label: '组件类型', desc: '表单控件类型' },
  { key: 'nullableFlag', label: '必填', desc: '是否必填项' },
]

function defaults(): Record<EditCtrlKey, boolean> {
  return {
    columnCode: true,
    columnName: true,
    dataType: true,
    lengthVal: true,
    componentType: true,
    nullableFlag: true,
  }
}

const loading = ref(false)
const saving = ref(false)
const form = reactive(defaults())

async function reload() {
  loading.value = true
  try {
    const data = (await api.get<Record<string, boolean>>('/system/builtin-attr-config')).data || {}
    const d = defaults()
    for (const k of Object.keys(d) as EditCtrlKey[]) {
      form[k] = typeof data[k] === 'boolean' ? data[k] : true
    }
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await api.put('/system/builtin-attr-config', { ...form })
    ElMessage.success('内置属性设置已保存')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(reload)
</script>

<template>
  <div v-loading="loading">
    <PageCard title="内置属性管理">
      <p class="hint">
        全局控制「数据项管理」中各属性维度是否可编辑。关闭后，编辑数据项时对应表单项只读（新建不受限）。
      </p>
      <div class="attr-list">
        <div v-for="a in ATTRS" :key="a.key" class="attr-row">
          <div class="attr-row__text">
            <div class="attr-row__label">{{ a.label }}</div>
            <div class="attr-row__desc">{{ a.desc }}</div>
          </div>
          <el-switch
            v-model="form[a.key]"
            inline-prompt
            active-text="可编"
            inactive-text="锁定"
          />
        </div>
      </div>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </div>
    </PageCard>
  </div>
</template>

<style scoped>
.hint {
  margin: 0 0 16px;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}
.attr-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-width: 640px;
}
.attr-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fafbfc;
}
.attr-row__label {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}
.attr-row__desc {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}
.actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
}
</style>
