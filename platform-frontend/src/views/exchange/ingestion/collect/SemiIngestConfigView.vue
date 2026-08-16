<script setup lang="ts">
/**
 * 半结构化数据接入：Kafka / MongoDB / ElasticSearch 分步配置 + 同页任务管理。
 */
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import IngestChannelTaskDialog from './IngestChannelTaskDialog.vue'
import { ingestionApi, type Channel } from '../useIngestionHub'

type SourceKind = 'kafka' | 'mongo' | 'es'

const sourceKind = ref<SourceKind>('kafka')
const step = ref(0)
const saveBusy = ref(false)
const taskDlg = ref(false)
const editingId = ref<number | undefined>()

const form = reactive({
  channelName: '',
  // kafka
  broker: '',
  topic: '',
  groupId: 'dap-ingest-group-01',
  readMode: 'earliest',
  dataFormat: 'json',
  // mongo
  mongoUri: '',
  database: '',
  collection: '',
  queryFilter: '',
  // es
  esHosts: '',
  index: '',
  indexType: '_doc',
  esQuery: '',
  scrollEnabled: true,
  // target
  targetDsType: 'HIVE',
  targetDs: 'dap-hive-warehouse',
  targetDb: 'ods',
  targetTable: '',
  writeMode: 'APPEND',
  batchSize: '1000',
  writeInterval: '',
  retryCount: '3',
})

const mapPairs = ref<Array<{ source: string; target: string }>>([
  { source: 'id', target: 'id' },
  { source: 'event_time', target: 'event_time' },
])

const stepTitles = ['数据来源', '数据去向', '字段映射', '配置完成']

const sourceKindLabel = computed(() => {
  if (sourceKind.value === 'mongo') return 'MongoDB'
  if (sourceKind.value === 'es') return 'ElasticSearch'
  return 'Kafka'
})

function resetForm(ch?: Channel) {
  editingId.value = ch?.id
  let cfg: Record<string, unknown> = {}
  if (ch?.configJson) {
    try {
      cfg = JSON.parse(ch.configJson) as Record<string, unknown>
    } catch {
      cfg = {}
    }
  }
  const s = (k: string, d = '') => String(cfg[k] ?? d)
  form.channelName = ch?.channelName || ''
  sourceKind.value = (s('sourceKind', 'kafka') as SourceKind) || 'kafka'
  form.broker = s('broker')
  form.topic = s('topic')
  form.groupId = s('groupId', 'dap-ingest-group-01')
  form.readMode = s('readMode', 'earliest')
  form.dataFormat = s('dataFormat', 'json')
  form.mongoUri = s('mongoUri')
  form.database = s('database')
  form.collection = s('collection')
  form.queryFilter = s('queryFilter')
  form.esHosts = s('esHosts') || s('broker')
  form.index = s('index')
  form.indexType = s('indexType', '_doc')
  form.esQuery = s('esQuery')
  form.scrollEnabled = cfg.scrollEnabled !== false && cfg.scrollEnabled !== 'false'
  form.targetDsType = s('targetDsType', 'HIVE')
  form.targetDs = s('targetDs', 'dap-hive-warehouse')
  form.targetDb = s('targetDb', 'ods')
  form.targetTable = s('targetTable')
  form.writeMode = s('writeMode', 'APPEND')
  form.batchSize = s('batchSize', '1000')
  form.writeInterval = s('writeInterval')
  form.retryCount = s('retryCount', '3')
  if (Array.isArray(cfg.fieldMapping)) {
    mapPairs.value = (cfg.fieldMapping as Array<{ source?: string; target?: string }>).map((p) => ({
      source: String(p.source || ''),
      target: String(p.target || ''),
    }))
  }
  step.value = 0
}

function onSourceTab(k: SourceKind) {
  sourceKind.value = k
  step.value = 0
}

function validateStep0(): boolean {
  if (!form.channelName.trim()) {
    ElMessage.warning('请填写数据源名称')
    return false
  }
  if (sourceKind.value === 'kafka') {
    if (!form.broker.trim() || !form.topic.trim()) {
      ElMessage.warning('请填写 Kafka 集群地址与 Topic')
      return false
    }
  } else if (sourceKind.value === 'mongo') {
    if (!form.mongoUri.trim() || !form.database.trim() || !form.collection.trim()) {
      ElMessage.warning('请填写 MongoDB 连接地址、库名与集合')
      return false
    }
  } else if (!form.esHosts.trim() || !form.index.trim()) {
    ElMessage.warning('请填写 ES 集群地址与索引')
    return false
  }
  return true
}

function validateStep1(): boolean {
  if (!form.targetTable.trim()) {
    ElMessage.warning('请填写目标表名')
    return false
  }
  return true
}

function nextStep() {
  if (step.value === 0 && !validateStep0()) return
  if (step.value === 1 && !validateStep1()) return
  if (step.value < 3) step.value += 1
}

function prevStep() {
  if (step.value > 0) step.value -= 1
}

function addMapRow() {
  mapPairs.value.push({ source: '', target: '' })
}

function removeMapRow(i: number) {
  mapPairs.value.splice(i, 1)
}

function buildConfig(): Record<string, unknown> {
  const base: Record<string, unknown> = {
    sourceKind: sourceKind.value,
    dataFormat: form.dataFormat,
    targetDsType: form.targetDsType,
    targetDs: form.targetDs,
    targetDb: form.targetDb,
    targetTable: form.targetTable.trim(),
    writeMode: form.writeMode,
    batchSize: form.batchSize,
    writeInterval: form.writeInterval,
    retryCount: form.retryCount,
    fieldMapping: mapPairs.value.filter((p) => p.source || p.target),
  }
  if (sourceKind.value === 'kafka') {
    Object.assign(base, {
      broker: form.broker.trim(),
      topic: form.topic.trim(),
      groupId: form.groupId.trim(),
      readMode: form.readMode,
    })
  } else if (sourceKind.value === 'mongo') {
    Object.assign(base, {
      mongoUri: form.mongoUri.trim(),
      database: form.database.trim(),
      collection: form.collection.trim(),
      queryFilter: form.queryFilter.trim(),
      broker: form.mongoUri.trim(),
      topic: form.collection.trim(),
    })
  } else {
    Object.assign(base, {
      esHosts: form.esHosts.trim(),
      index: form.index.trim(),
      indexType: form.indexType.trim(),
      esQuery: form.esQuery.trim(),
      scrollEnabled: form.scrollEnabled,
      broker: form.esHosts.trim(),
      topic: form.index.trim(),
    })
  }
  return base
}

async function saveChannel(andRun = false) {
  if (!validateStep0()) {
    step.value = 0
    return
  }
  if (!validateStep1()) {
    step.value = 1
    return
  }
  saveBusy.value = true
  try {
    const body = {
      channelName: form.channelName.trim(),
      channelType: 'SEMI',
      config: buildConfig(),
    }
    let id = editingId.value
    if (id) {
      await ingestionApi.updateChannel(id, body)
      ElMessage.success('已保存')
    } else {
      id = Number((await ingestionApi.createChannel(body)).data)
      editingId.value = id
      ElMessage.success('接入任务已创建')
    }
    step.value = 3
    if (andRun && id) {
      const res = await ingestionApi.runChannel(id)
      ElMessage.success(String(res.data?.message || '已启动执行'))
    }
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saveBusy.value = false
  }
}

function onEditFromTasks(ch: Channel) {
  resetForm(ch)
}

function openTasks() {
  taskDlg.value = true
}

function newConfig() {
  resetForm()
  form.channelName = `半结构-${sourceKindLabel.value}-${new Date().toISOString().slice(0, 10)}`
}
</script>

<template>
  <div class="semi-ingest">
    <div class="page-head">
      <div>
        <h2 class="page-title">半结构化数据接入</h2>
        <p class="page-desc">
          支持 Kafka、MongoDB、ElasticSearch 三种数据源的实时接入，自动解析 JSON / XML 并结构化入库。
        </p>
      </div>
    </div>

    <PageCard>
      <div class="tab-row">
        <el-radio-group :model-value="sourceKind" @update:model-value="(v) => onSourceTab(v as SourceKind)">
          <el-radio-button value="kafka">Kafka 接入</el-radio-button>
          <el-radio-button value="mongo">MongoDB 接入</el-radio-button>
          <el-radio-button value="es">ElasticSearch 接入</el-radio-button>
        </el-radio-group>
        <el-button type="primary" plain @click="openTasks">任务管理</el-button>
      </div>

      <el-steps :active="step" finish-status="success" align-center class="semi-steps">
        <el-step v-for="(t, i) in stepTitles" :key="t" :title="t" @click="step = i" />
      </el-steps>

      <!-- 步骤 0：数据来源 -->
      <div v-show="step === 0" class="step-body">
        <el-alert type="info" show-icon :closable="false" class="tip-banner"
          title="配置数据源连接信息与读取规则。读取规则支持全部数据或从当前开始两种模式。" />
        <el-form label-width="140px" class="cfg-form">
          <el-form-item label="数据源名称" required>
            <el-input v-model="form.channelName" placeholder="例如：kafka-cluster-prod" maxlength="80" />
          </el-form-item>

          <template v-if="sourceKind === 'kafka'">
            <el-form-item label="Kafka 集群地址" required>
              <el-input v-model="form.broker" placeholder="bootstrap.servers，如 10.20.1.101:9092" />
            </el-form-item>
            <el-form-item label="消息主题 Topic" required>
              <el-input v-model="form.topic" placeholder="例如：order_topic" />
            </el-form-item>
            <el-form-item label="消费者组 Group ID">
              <el-input v-model="form.groupId" placeholder="consumer group" />
            </el-form-item>
            <el-form-item label="读取规则" required>
              <el-radio-group v-model="form.readMode">
                <el-radio value="earliest">读取全部数据（含历史消息）</el-radio>
                <el-radio value="latest">从当前开始读取（仅最新消息）</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="数据格式">
              <el-radio-group v-model="form.dataFormat">
                <el-radio value="json">JSON</el-radio>
                <el-radio value="xml">XML</el-radio>
                <el-radio value="auto">自动解析</el-radio>
              </el-radio-group>
            </el-form-item>
          </template>

          <template v-else-if="sourceKind === 'mongo'">
            <el-form-item label="连接地址" required>
              <el-input v-model="form.mongoUri" placeholder="mongodb://host:port" />
            </el-form-item>
            <el-form-item label="数据库" required>
              <el-input v-model="form.database" placeholder="database" />
            </el-form-item>
            <el-form-item label="集合 Collection" required>
              <el-input v-model="form.collection" placeholder="collection" />
            </el-form-item>
            <el-form-item label="过滤条件">
              <el-input v-model="form.queryFilter" type="textarea" :rows="3" placeholder='如 {"status":"active"}' />
            </el-form-item>
            <el-form-item label="数据格式">
              <el-radio-group v-model="form.dataFormat">
                <el-radio value="json">JSON</el-radio>
                <el-radio value="bson">BSON</el-radio>
              </el-radio-group>
            </el-form-item>
          </template>

          <template v-else>
            <el-form-item label="ES 集群地址" required>
              <el-input v-model="form.esHosts" placeholder="如 10.20.3.20:9200" />
            </el-form-item>
            <el-form-item label="索引 Index" required>
              <el-input v-model="form.index" placeholder="index name" />
            </el-form-item>
            <el-form-item label="索引类型">
              <el-input v-model="form.indexType" placeholder="_doc" />
            </el-form-item>
            <el-form-item label="实时数据过滤">
              <el-input v-model="form.esQuery" type="textarea" :rows="4" placeholder="Elasticsearch Query DSL JSON" />
            </el-form-item>
            <el-form-item label="滚动读取 Scroll">
              <el-switch v-model="form.scrollEnabled" />
              <span class="hint">开启后使用 Scroll 游标批量读取大索引</span>
            </el-form-item>
          </template>
        </el-form>
      </div>

      <!-- 步骤 1：数据去向 -->
      <div v-show="step === 1" class="step-body">
        <el-form label-width="140px" class="cfg-form">
          <el-form-item label="目标数据源类型">
            <el-select v-model="form.targetDsType" style="width:100%">
              <el-option label="大数据平台·Hive 数仓" value="HIVE" />
              <el-option label="MySQL 数仓" value="MYSQL" />
              <el-option label="Iceberg 湖仓" value="ICEBERG" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标数据源">
            <el-input v-model="form.targetDs" placeholder="目标数据源标识" />
          </el-form-item>
          <el-form-item label="目标数据库 / 库名">
            <el-select v-model="form.targetDb" style="width:100%">
              <el-option label="ods" value="ods" />
              <el-option label="dwd" value="dwd" />
              <el-option label="dws" value="dws" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标表名" required>
            <el-input v-model="form.targetTable" placeholder="如 ods_user_behavior" />
          </el-form-item>
          <el-form-item label="写入模式">
            <el-select v-model="form.writeMode" style="width:100%">
              <el-option label="追加写入 (INSERT)" value="APPEND" />
              <el-option label="覆盖写入 (REPLACE)" value="REPLACE" />
              <el-option label="UPSERT 更新写入" value="UPSERT" />
            </el-select>
          </el-form-item>
          <el-form-item label="批处理大小">
            <el-input v-model="form.batchSize">
              <template #append>条/批</template>
            </el-input>
          </el-form-item>
          <el-form-item label="写入间隔">
            <el-input v-model="form.writeInterval" placeholder="可选，秒" />
          </el-form-item>
          <el-form-item label="失败重试">
            <el-input v-model="form.retryCount" placeholder="次数" />
          </el-form-item>
        </el-form>
      </div>

      <!-- 步骤 2：字段映射 -->
      <div v-show="step === 2" class="step-body">
        <el-alert type="info" show-icon :closable="false" class="tip-banner"
          title="将来源字段映射到目标表字段。可增删映射行；未映射字段默认忽略。" />
        <el-table :data="mapPairs" border size="small" class="portal-table map-table">
          <el-table-column label="来源字段" min-width="160">
            <template #default="{ row }">
              <el-input v-model="row.source" placeholder="source field" />
            </template>
          </el-table-column>
          <el-table-column label="目标字段" min-width="160">
            <template #default="{ row }">
              <el-input v-model="row.target" placeholder="target column" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeMapRow($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button class="add-map" @click="addMapRow">＋ 添加映射</el-button>
      </div>

      <!-- 步骤 3：完成 -->
      <div v-show="step === 3" class="step-body done-panel">
        <el-result icon="success" title="配置已就绪" :sub-title="`类型：${sourceKindLabel} · 目标表：${form.targetTable || '—'}`">
          <template #extra>
            <el-button type="primary" :loading="saveBusy" @click="saveChannel(true)">保存并启动</el-button>
            <el-button @click="openTasks">打开任务管理</el-button>
            <el-button @click="newConfig">再建一条</el-button>
          </template>
        </el-result>
      </div>

      <div class="step-actions">
        <el-button :disabled="step === 0" @click="prevStep">← 上一步</el-button>
        <div class="spacer" />
        <el-button v-if="step < 3" type="primary" @click="nextStep">下一步 →</el-button>
        <el-button v-if="step === 2 || step === 3" :loading="saveBusy" @click="saveChannel(false)">保存配置</el-button>
        <el-button v-if="step === 3" type="success" :loading="saveBusy" @click="saveChannel(true)">保存并启动</el-button>
      </div>
    </PageCard>

    <IngestChannelTaskDialog
      v-model="taskDlg"
      channel-type="SEMI"
      title="半结构化数据接入 · 任务管理"
      @edit="onEditFromTasks"
    />
  </div>
</template>

<style scoped>
.page-head { margin-bottom: 14px; }
.page-title { margin: 0; font-size: 18px; font-weight: 700; color: #303133; }
.page-desc { margin: 6px 0 0; font-size: 13px; color: #909399; line-height: 1.6; }
.tab-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}
.semi-steps { margin: 8px 0 22px; cursor: pointer; }
.step-body { max-width: 880px; }
.tip-banner { margin-bottom: 16px; }
.cfg-form :deep(.el-form-item) { margin-bottom: 16px; }
.hint { margin-left: 10px; font-size: 12px; color: #909399; }
.map-table { margin-bottom: 10px; }
.add-map { margin-top: 4px; }
.step-actions {
  display: flex;
  align-items: center;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.spacer { flex: 1; }
.done-panel { padding: 12px 0; }
</style>
