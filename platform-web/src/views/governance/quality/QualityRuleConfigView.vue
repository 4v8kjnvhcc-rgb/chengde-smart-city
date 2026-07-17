<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'

interface RuleConfig {
  id?: number
  ruleId?: number
  checkType?: string
  targetTable?: string
  targetColumn?: string
  configJson?: string
  threshold?: number
  status?: string
}

interface RuleRow {
  id: number
  ruleCode: string
  ruleName: string
  ruleType: string
  status: string
  config?: RuleConfig | null
}

const rules = ref<RuleRow[]>([])
const loading = ref(false)
const selectedId = ref<number | null>(null)

const form = reactive({
  checkType: 'NULL_CHECK',
  targetTable: '',
  targetColumn: '',
  threshold: undefined as number | undefined,
  configJson: '',
  status: 'ENABLED',
})

const selected = computed(() => rules.value.find((r) => r.id === selectedId.value) || null)

const checkHints: Record<string, string> = {
  NULL_CHECK: '空值稽核：指定表/字段，阈值率超过阈值则告警',
  UNIQUENESS: '唯一性稽核：指定表/字段，重复率超过阈值则告警',
  ACCURACY: '准确性稽核：按 configJson 校验格式/正则/值域',
  RECORD_COUNT: '记录数稽核：表行数相对阈值（可作上下限）',
}

async function load() {
  loading.value = true
  try {
    rules.value = (await api.get('/governance/quality/rule-mgmt')).data || []
    if (selectedId.value && !rules.value.some((r) => r.id === selectedId.value)) {
      selectedId.value = null
    }
    if (!selectedId.value && rules.value.length) {
      selectRule(rules.value[0])
    } else if (selectedId.value) {
      const row = rules.value.find((r) => r.id === selectedId.value)
      if (row) fillForm(row)
    }
  } catch {
    ElMessage.error('加载规则失败')
  } finally {
    loading.value = false
  }
}

function fillForm(row: RuleRow) {
  const c = row.config
  form.checkType = c?.checkType || mapRuleType(row.ruleType)
  form.targetTable = c?.targetTable || ''
  form.targetColumn = c?.targetColumn || ''
  form.threshold = c?.threshold != null ? Number(c.threshold) : undefined
  form.configJson = c?.configJson || ''
  form.status = c?.status || 'ENABLED'
}

function mapRuleType(ruleType: string): string {
  const t = (ruleType || '').toUpperCase()
  if (t === 'UNIQUENESS') return 'UNIQUENESS'
  if (t === 'ACCURACY') return 'ACCURACY'
  if (t === 'COMPLETENESS' || t === 'INTEGRITY') return 'NULL_CHECK'
  return 'NULL_CHECK'
}

function selectRule(row: RuleRow) {
  selectedId.value = row.id
  fillForm(row)
}

async function saveConfig() {
  if (!selectedId.value) return
  if (!form.checkType) {
    ElMessage.warning('请选择检查类型')
    return
  }
  await api.post(`/governance/quality/rule-mgmt/${selectedId.value}/config`, {
    checkType: form.checkType,
    targetTable: form.targetTable,
    targetColumn: form.targetColumn,
    threshold: form.threshold,
    configJson: form.configJson,
    status: form.status,
  })
  ElMessage.success('配置已保存')
  await load()
}

async function removeRule(id: number) {
  await ElMessageBox.confirm('确认删除该质量规则及其配置？', '删除确认', { type: 'warning' })
  await api.delete(`/governance/quality/rule-mgmt/${id}`)
  ElMessage.success('已删除')
  if (selectedId.value === id) selectedId.value = null
  await load()
}

function checkTypeLabel(v: string) {
  return ({
    NULL_CHECK: '空值检查',
    UNIQUENESS: '唯一性',
    ACCURACY: '准确性',
    RECORD_COUNT: '记录数',
  } as Record<string, string>)[v] || v
}

onMounted(load)
</script>

<template>
  <PageCard title="质量规则配置">
    <div class="rule-layout">
      <div class="rule-list" v-loading="loading">
        <el-table
          :data="rules"
          stripe
          size="small"
          highlight-current-row
          :current-row-key="selectedId ?? undefined"
          row-key="id"
          @current-change="(row: RuleRow | null) => row && selectRule(row)"
        >
          <el-table-column prop="ruleName" label="规则" min-width="120" />
          <el-table-column prop="ruleType" label="类型" width="100" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70">
            <template #default="{ row }">
              <el-button link type="danger" @click.stop="removeRule(row.id)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="rule-form">
        <template v-if="selected">
          <el-alert
            :title="`${selected.ruleCode} · ${selected.ruleName}`"
            type="info"
            :closable="false"
            show-icon
            style="margin-bottom: 12px"
          />
          <el-form label-width="100px">
            <el-form-item label="检查类型">
              <el-select v-model="form.checkType" style="width: 220px">
                <el-option label="空值检查" value="NULL_CHECK" />
                <el-option label="唯一性" value="UNIQUENESS" />
                <el-option label="准确性" value="ACCURACY" />
                <el-option label="记录数" value="RECORD_COUNT" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <span class="hint">{{ checkHints[form.checkType] }}</span>
            </el-form-item>
            <el-form-item v-if="form.checkType !== 'RECORD_COUNT'" label="目标表">
              <el-input v-model="form.targetTable" placeholder="如 gov_catalog_resource" />
            </el-form-item>
            <el-form-item v-if="form.checkType === 'RECORD_COUNT'" label="目标表">
              <el-input v-model="form.targetTable" placeholder="统计行数的表名" />
            </el-form-item>
            <el-form-item v-if="['NULL_CHECK', 'UNIQUENESS', 'ACCURACY'].includes(form.checkType)" label="目标字段">
              <el-input v-model="form.targetColumn" placeholder="如 resource_code" />
            </el-form-item>
            <el-form-item label="阈值">
              <el-input-number v-model="form.threshold" :min="0" :max="100" :step="0.1" :precision="2" />
            </el-form-item>
            <el-form-item v-if="form.checkType === 'ACCURACY'" label="参数 JSON">
              <el-input v-model="form.configJson" type="textarea" :rows="4" placeholder='{"pattern":"^\\\\d{17}[\\\\dXx]$"}' />
            </el-form-item>
            <el-form-item label="配置状态">
              <el-select v-model="form.status" style="width: 160px">
                <el-option label="启用" value="ENABLED" />
                <el-option label="停用" value="DISABLED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveConfig">保存配置</el-button>
            </el-form-item>
          </el-form>
          <el-descriptions v-if="selected.config" :column="1" size="small" border title="当前配置">
            <el-descriptions-item label="检查类型">{{ checkTypeLabel(selected.config.checkType || '') }}</el-descriptions-item>
            <el-descriptions-item label="目标表">{{ selected.config.targetTable || '—' }}</el-descriptions-item>
            <el-descriptions-item label="目标字段">{{ selected.config.targetColumn || '—' }}</el-descriptions-item>
            <el-descriptions-item label="阈值">{{ selected.config.threshold ?? '—' }}</el-descriptions-item>
          </el-descriptions>
        </template>
        <el-empty v-else description="请选择左侧规则进行配置" />
      </div>
    </div>
  </PageCard>
</template>

<style scoped>
.rule-layout {
  display: grid;
  grid-template-columns: minmax(280px, 42%) 1fr;
  gap: 16px;
  align-items: start;
}
.rule-list {
  min-height: 320px;
}
.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}
@media (max-width: 900px) {
  .rule-layout {
    grid-template-columns: 1fr;
  }
}
</style>
