<script setup lang="ts">
/**
 * CDC 实时数据接入：读取 / 写入配置 + 链路示意 + 同页任务管理。
 */
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import IngestChannelTaskDialog from './IngestChannelTaskDialog.vue'
import { ingestionApi, type Channel } from '../useIngestionHub'

type CdcMode = 'read' | 'write'

const mode = ref<CdcMode>('read')
const saveBusy = ref(false)
const taskDlg = ref(false)
const editingId = ref<number | undefined>()

const form = reactive({
  channelName: '',
  // read
  sourceType: 'MYSQL',
  canalHost: '',
  sourceDb: '',
  sourceTables: '',
  tableMatch: 'exact',
  startupMode: 'all',
  targetMq: '',
  // write
  changeSource: '',
  groupId: 'dap-cdc-sink-01',
  targetDs: '',
  targetDb: 'ods',
  targetTable: '',
  writeStrategy: 'UPSERT',
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
  mode.value = (s('cdcMode', 'read') as CdcMode) || 'read'
  form.sourceType = s('sourceType', 'MYSQL')
  form.canalHost = s('canalHost')
  form.sourceDb = s('sourceDb')
  form.sourceTables = s('sourceTables')
  form.tableMatch = s('tableMatch', 'exact')
  form.startupMode = s('startupMode', 'all')
  form.targetMq = s('targetMq')
  form.changeSource = s('changeSource')
  form.groupId = s('groupId', 'dap-cdc-sink-01')
  form.targetDs = s('targetDs')
  form.targetDb = s('targetDb', 'ods')
  form.targetTable = s('targetTable')
  form.writeStrategy = s('writeStrategy', 'UPSERT')
}

function validate(): boolean {
  if (!form.channelName.trim()) {
    ElMessage.warning('请填写任务名称')
    return false
  }
  if (mode.value === 'read') {
    if (!form.canalHost.trim() || !form.sourceDb.trim() || !form.sourceTables.trim()) {
      ElMessage.warning('请填写 Canal/连接地址、源库与数据表')
      return false
    }
    if (!form.targetMq.trim()) {
      ElMessage.warning('请填写目标消息中间件 Topic')
      return false
    }
  } else {
    if (!form.changeSource.trim() || !form.targetDs.trim() || !form.targetTable.trim()) {
      ElMessage.warning('请填写变更数据来源、目标数据源与目标表')
      return false
    }
  }
  return true
}

function buildConfig(): Record<string, unknown> {
  if (mode.value === 'read') {
    return {
      cdcMode: 'read',
      sourceType: form.sourceType,
      canalHost: form.canalHost.trim(),
      sourceDb: form.sourceDb.trim(),
      sourceTables: form.sourceTables.trim(),
      tableMatch: form.tableMatch,
      startupMode: form.startupMode,
      targetMq: form.targetMq.trim(),
      // 兼容旧执行字段：目标先落到中间件 Topic
      targetTable: form.targetMq.trim(),
    }
  }
  return {
    cdcMode: 'write',
    changeSource: form.changeSource.trim(),
    groupId: form.groupId.trim(),
    targetDs: form.targetDs.trim(),
    targetDb: form.targetDb,
    targetTable: form.targetTable.trim(),
    writeStrategy: form.writeStrategy,
    canalHost: form.changeSource.trim(),
    sourceDb: form.targetDb,
  }
}

async function saveChannel(andRun = false) {
  if (!validate()) return
  saveBusy.value = true
  try {
    const body = {
      channelName: form.channelName.trim(),
      channelType: 'CDC',
      config: buildConfig(),
    }
    let id = editingId.value
    if (id) {
      await ingestionApi.updateChannel(id, body)
      ElMessage.success('已保存 CDC 配置，可在任务管理中启动')
    } else {
      id = Number((await ingestionApi.createChannel(body)).data)
      editingId.value = id
      ElMessage.success('CDC 接入任务已创建')
    }
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

function onModeChange(v: string | number | boolean) {
  mode.value = String(v) as CdcMode
}
</script>

<template>
  <div class="cdc-ingest">
    <div class="page-head">
      <div>
        <h2 class="page-title">实时数据接入（CDC）</h2>
        <p class="page-desc">
          基于 CDC（Change Data Capture）技术捕获源表 INSERT / UPDATE / DELETE 变更，写入消息中间件后实时同步到目标系统，实现业务数据实时采集与更新。
        </p>
      </div>
      <el-button type="primary" plain @click="taskDlg = true">任务管理</el-button>
    </div>

    <PageCard>
      <div class="tab-row">
        <el-radio-group :model-value="mode" @update:model-value="onModeChange">
          <el-radio-button value="read">读取 MySQL 数据</el-radio-button>
          <el-radio-button value="write">写入 MySQL 数据</el-radio-button>
        </el-radio-group>
      </div>

      <div class="flow">
        <div class="flow-node hot">
          <div class="fn-title">业务源 MySQL</div>
          <div class="fn-sub">orders / users / items</div>
          <span class="flow-tag">CDC 监听</span>
        </div>
        <div class="flow-arrow">→</div>
        <div class="flow-node">
          <div class="fn-title">连接器捕获变更</div>
          <div class="fn-sub">INSERT / UPDATE / DELETE</div>
        </div>
        <div class="flow-arrow">→</div>
        <div class="flow-node">
          <div class="fn-title">Kafka 消息中间件</div>
          <div class="fn-sub">binlog 变更日志</div>
        </div>
        <div class="flow-arrow">→</div>
        <div class="flow-node hot">
          <div class="fn-title">数据解析 & 映射</div>
          <div class="fn-sub">识别变更类型 · 前后镜像</div>
          <span class="flow-tag">实时</span>
        </div>
        <div class="flow-arrow">→</div>
        <div class="flow-node">
          <div class="fn-title">目标系统表</div>
          <div class="fn-sub">数仓 / MySQL / ES</div>
        </div>
      </div>

      <el-form label-width="150px" class="cfg-form">
        <el-form-item label="任务名称" required>
          <el-input v-model="form.channelName" placeholder="如：订单库 CDC 实时同步" maxlength="80" style="max-width:480px" />
        </el-form-item>

        <template v-if="mode === 'read'">
          <el-form-item label="数据源类型" required>
            <el-select v-model="form.sourceType" style="width:320px">
              <el-option label="MySQL 业务库" value="MYSQL" />
              <el-option label="PostgreSQL" value="POSTGRES" />
              <el-option label="Oracle" value="ORACLE" />
              <el-option label="SQL Server" value="SQLSERVER" />
            </el-select>
          </el-form-item>
          <el-form-item label="Canal / 连接地址" required>
            <el-input v-model="form.canalHost" placeholder="如 10.10.10.51:19090 或 JDBC 入口" style="max-width:480px" />
          </el-form-item>
          <el-form-item label="数据库" required>
            <el-input v-model="form.sourceDb" placeholder="源库名" style="max-width:320px" />
          </el-form-item>
          <el-form-item label="数据表" required>
            <el-input v-model="form.sourceTables" placeholder="表名，逗号分隔；支持正则" style="max-width:480px" />
          </el-form-item>
          <el-form-item label="表匹配方式">
            <el-select v-model="form.tableMatch" style="width:320px">
              <el-option label="精确匹配（多表）" value="exact" />
              <el-option label="正则表达式匹配" value="regex" />
            </el-select>
          </el-form-item>
          <el-form-item label="读取范围" required>
            <el-radio-group v-model="form.startupMode">
              <el-radio value="all">读取全部数据（含历史数据及之后产生的数据）</el-radio>
              <el-radio value="latest">仅读取任务启动后产生的变更数据</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="目标消息中间件" required>
            <el-input v-model="form.targetMq" placeholder="如 Kafka · cdc-order-binlog" style="max-width:480px" />
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="变更数据来源" required>
            <el-input v-model="form.changeSource" placeholder="如 Kafka - cdc-user-binlog" style="max-width:480px" />
          </el-form-item>
          <el-form-item label="消费组 Group ID">
            <el-input v-model="form.groupId" style="max-width:320px" />
          </el-form-item>
          <el-form-item label="目标数据源" required>
            <el-input v-model="form.targetDs" placeholder="如 mysql-dw-prod (10.20.5.80:3306)" style="max-width:480px" />
          </el-form-item>
          <el-form-item label="目标数据库" required>
            <el-select v-model="form.targetDb" style="width:200px">
              <el-option label="ods" value="ods" />
              <el-option label="dwd" value="dwd" />
              <el-option label="dws" value="dws" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标表" required>
            <el-input v-model="form.targetTable" placeholder="如 ods_t_order" style="max-width:320px" />
          </el-form-item>
          <el-form-item label="写入策略">
            <el-select v-model="form.writeStrategy" style="width:320px">
              <el-option label="按主键 UPSERT（变更合并）" value="UPSERT" />
              <el-option label="追加写入 INSERT" value="APPEND" />
              <el-option label="覆盖写入 REPLACE" value="REPLACE" />
            </el-select>
          </el-form-item>
        </template>
      </el-form>

      <el-alert
        type="warning"
        show-icon
        :closable="false"
        class="link-tip"
        title="链路说明"
        description="数据从业务 MySQL 经连接器写入 Kafka，再解析映射后写入目标库表。DELETE 按写入策略处理（UPSERT 可标记删除或物理删除，以执行引擎配置为准）。"
      />

      <div class="actions">
        <el-button @click="resetForm()">取消</el-button>
        <el-button type="primary" :loading="saveBusy" @click="saveChannel(false)">保存配置</el-button>
        <el-button type="success" :loading="saveBusy" @click="saveChannel(true)">保存并启动</el-button>
      </div>
    </PageCard>

    <IngestChannelTaskDialog
      v-model="taskDlg"
      channel-type="CDC"
      title="CDC 实时数据接入 · 任务管理"
      @edit="onEditFromTasks"
    />
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.page-title { margin: 0; font-size: 18px; font-weight: 700; color: #303133; }
.page-desc { margin: 6px 0 0; font-size: 13px; color: #909399; line-height: 1.6; max-width: 760px; }
.tab-row { margin-bottom: 18px; }
.flow {
  display: flex;
  align-items: stretch;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 22px;
  padding: 14px;
  background: #f8fafc;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}
.flow-node {
  position: relative;
  flex: 1;
  min-width: 120px;
  background: #fff;
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  padding: 12px 10px;
  text-align: center;
}
.flow-node.hot { border-color: var(--el-color-primary-light-5); background: #f0f5ff; }
.fn-title { font-size: 13px; font-weight: 600; color: #303133; }
.fn-sub { margin-top: 4px; font-size: 11px; color: #909399; }
.flow-tag {
  position: absolute;
  top: -8px;
  right: 8px;
  font-size: 10px;
  padding: 1px 7px;
  border-radius: 8px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}
.flow-arrow {
  display: flex;
  align-items: center;
  color: #94a3b8;
  font-size: 16px;
  flex-shrink: 0;
}
.cfg-form { max-width: 880px; }
.link-tip { margin: 8px 0 18px; max-width: 880px; }
.actions { display: flex; justify-content: flex-end; gap: 8px; }
</style>
