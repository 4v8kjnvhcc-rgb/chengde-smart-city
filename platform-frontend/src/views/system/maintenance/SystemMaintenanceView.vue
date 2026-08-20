<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { applyBrowserChrome, applyThemeFromAppearance, type AppearancePublic } from '@/utils/appearance'
import { encryptTransportPayload } from '@/utils/transport-crypto'
import { formatDateTime } from '@/utils/datetime'
import { statusLabel } from '@/utils/status-label'

const props = defineProps<{ embed?: boolean }>()

const activeTab = ref('theme')
const loading = ref(false)
const saving = ref(false)
const cfg = ref<AppearancePublic & Record<string, unknown>>({})
const security = reactive<Record<string, string>>({})

const themeUpload = reactive({ name: '', primaryColor: '#1677ff', sidebarBg: '#001529', file: null as File | null })
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })

const authMethods = reactive({
  password: true,
  sms: false,
  totp: false,
  fingerprint: false,
})
const authMethodsLoaded = ref(false)

type GovTarget = {
  id: number
  targetCode: string
  targetName: string
  endpoint?: string | null
  syncDirection?: string
  status?: string
  remark?: string | null
}
type GovJob = Record<string, unknown>
type Opt = { id: number; label: string }

const govTargets = ref<GovTarget[]>([])
const govJobs = ref<GovJob[]>([])
const orgOptions = ref<Opt[]>([])
const userOptions = ref<Opt[]>([])
const roleOptions = ref<Opt[]>([])
const syncRunning = ref(false)
const syncForm = reactive({
  targetId: undefined as number | undefined,
  syncOrg: true,
  syncUser: true,
  syncRole: true,
  syncPassword: false,
  syncSms: false,
  orgIds: [] as number[],
  userIds: [] as number[],
  roleIds: [] as number[],
})
const targetDialog = ref(false)
const targetSaving = ref(false)
const targetForm = reactive({
  id: null as number | null,
  targetCode: '',
  targetName: '',
  endpoint: '',
  remark: '',
})

async function loadAppearance() {
  loading.value = true
  try {
    cfg.value = (await api.get('/system/appearance')).data || {}
    applyThemeFromAppearance(cfg.value)
    applyBrowserChrome(cfg.value)
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载外观失败')
  } finally {
    loading.value = false
  }
}

async function loadSecurity() {
  try {
    const data = (await api.get('/system/security-config')).data || {}
    Object.assign(security, data)
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载安全配置失败')
  }
}

async function loadAuthMethods() {
  try {
    const data = (await api.get('/system/gov-sync/auth-methods')).data || {}
    authMethods.password = data.password !== false
    authMethods.sms = !!data.sms
    authMethods.totp = !!data.totp
    authMethods.fingerprint = !!data.fingerprint
    authMethodsLoaded.value = true
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载认证方式失败')
  }
}

async function loadGovTargets() {
  govTargets.value = ((await api.get('/system/gov-sync/targets')).data || []) as GovTarget[]
  if (syncForm.targetId == null && govTargets.value.length) {
    syncForm.targetId = govTargets.value[0].id
  }
}

async function loadGovJobs() {
  govJobs.value = ((await api.get('/system/gov-sync/jobs', {
    params: { targetId: syncForm.targetId || undefined },
  })).data || []) as GovJob[]
}

async function loadSyncOptions() {
  const [orgRes, userRes, roleRes] = await Promise.all([
    api.get('/system/orgs'),
    api.get('/system/users', { params: { page: 1, size: 200 } }),
    api.get('/system/roles'),
  ])
  orgOptions.value = ((orgRes.data || []) as Array<Record<string, unknown>>).map((o) => ({
    id: Number(o.id),
    label: String(o.orgName || o.orgCode || o.id),
  }))
  const userPage = userRes.data as { records?: Array<Record<string, unknown>> } | Array<Record<string, unknown>>
  const users = Array.isArray(userPage) ? userPage : (userPage?.records || [])
  userOptions.value = users.map((u) => ({
    id: Number(u.id),
    label: `${u.displayName || u.username}（${u.username}）`,
  }))
  roleOptions.value = ((roleRes.data || []) as Array<Record<string, unknown>>).map((r) => ({
    id: Number(r.id),
    label: String(r.roleName || r.roleCode || r.id),
  }))
}

async function ensureAuthSyncTab() {
  if (!authMethodsLoaded.value) await loadAuthMethods()
  if (!govTargets.value.length) await loadGovTargets()
  if (!orgOptions.value.length) await loadSyncOptions()
  await loadGovJobs()
}

onMounted(() => {
  void loadAppearance()
})

watch(activeTab, (t) => {
  if (t === 'security' && !security.login_max_failures) {
    void loadSecurity()
  }
  if (t === 'auth-sync') {
    void ensureAuthSyncTab()
  }
})

async function saveAppearance(patch: Record<string, unknown>, tip = '已保存') {
  saving.value = true
  try {
    await api.put('/system/appearance', patch)
    ElMessage.success(tip)
    await loadAppearance()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function applyTheme(themeId: string) {
  await saveAppearance({ themeId }, '主题已应用并保存')
}

async function uploadAsset(kind: string, file?: File | null) {
  if (!file) {
    ElMessage.warning('请选择文件')
    return
  }
  const fd = new FormData()
  fd.append('file', file)
  try {
    await api.post(`/system/appearance/upload?kind=${encodeURIComponent(kind)}`, fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    ElMessage.success('上传成功')
    await loadAppearance()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '上传失败')
  }
}

async function uploadTheme() {
  if (!themeUpload.name.trim()) {
    ElMessage.warning('请填写主题名称')
    return
  }
  const fd = new FormData()
  fd.append('name', themeUpload.name.trim())
  fd.append('primaryColor', themeUpload.primaryColor)
  fd.append('sidebarBg', themeUpload.sidebarBg)
  if (themeUpload.file) fd.append('file', themeUpload.file)
  try {
    await api.post('/system/appearance/theme/upload', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    ElMessage.success('自定义主题已上传')
    themeUpload.name = ''
    themeUpload.file = null
    await loadAppearance()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '上传失败')
  }
}

async function deleteTheme(id: string) {
  try {
    await api.delete(`/system/appearance/theme/${encodeURIComponent(id)}`)
    ElMessage.success('已删除')
    await loadAppearance()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

function downloadBuiltin(id: string) {
  window.open(`/api/v1/system/appearance/theme/builtin/${encodeURIComponent(id)}/download`, '_blank')
}

async function saveSecurity() {
  saving.value = true
  try {
    await api.put('/system/security-config', { ...security })
    ElMessage.success('账号安全配置已保存')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function saveAuthMethods() {
  saving.value = true
  try {
    await api.put('/system/gov-sync/auth-methods', { ...authMethods })
    ElMessage.success('身份验证方式已保存')
    await loadAuthMethods()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

function openTargetDialog(row?: GovTarget) {
  if (row) {
    targetForm.id = row.id
    targetForm.targetCode = row.targetCode || ''
    targetForm.targetName = row.targetName || ''
    targetForm.endpoint = row.endpoint || ''
    targetForm.remark = row.remark || ''
  } else {
    targetForm.id = null
    targetForm.targetCode = ''
    targetForm.targetName = ''
    targetForm.endpoint = ''
    targetForm.remark = ''
  }
  targetDialog.value = true
}

async function submitTarget() {
  if (!targetForm.targetName.trim()) {
    ElMessage.warning('请填写对接系统名称')
    return
  }
  targetSaving.value = true
  try {
    const body = {
      targetCode: targetForm.targetCode.trim() || undefined,
      targetName: targetForm.targetName.trim(),
      endpoint: targetForm.endpoint.trim() || null,
      remark: targetForm.remark.trim() || null,
      status: 'ACTIVE',
      syncDirection: 'PUSH',
    }
    if (targetForm.id == null) {
      await api.post('/system/gov-sync/targets', body)
    } else {
      await api.put(`/system/gov-sync/targets/${targetForm.id}`, body)
    }
    ElMessage.success('对接目标已保存')
    targetDialog.value = false
    await loadGovTargets()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    targetSaving.value = false
  }
}

async function removeTarget(row: GovTarget) {
  try {
    await api.delete(`/system/gov-sync/targets/${row.id}`)
    ElMessage.success('已删除')
    if (syncForm.targetId === row.id) {
      syncForm.targetId = undefined
    }
    await loadGovTargets()
    await loadGovJobs()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

async function runGovSync() {
  if (syncForm.targetId == null) {
    ElMessage.warning('请选择对接系统')
    return
  }
  if (!syncForm.syncOrg && !syncForm.syncUser && !syncForm.syncRole && !syncForm.syncPassword && !syncForm.syncSms) {
    ElMessage.warning('请至少勾选一项同步内容')
    return
  }
  syncRunning.value = true
  try {
    const res = await api.post('/system/gov-sync/run', {
      targetId: syncForm.targetId,
      syncOrg: syncForm.syncOrg,
      syncUser: syncForm.syncUser,
      syncRole: syncForm.syncRole,
      syncPassword: syncForm.syncPassword,
      syncSms: syncForm.syncSms,
      orgIds: syncForm.orgIds,
      userIds: syncForm.userIds,
      roleIds: syncForm.roleIds,
    })
    const data = res.data || {}
    const st = String(data.status || '')
    if (st === 'SUCCESS') {
      ElMessage.success(String(data.message || '同步成功'))
    } else if (st === 'LEDGER') {
      ElMessage.warning(String(data.message || '已记入台账'))
    } else {
      ElMessage.error(String(data.message || '同步失败'))
    }
    await loadGovJobs()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '同步失败')
  } finally {
    syncRunning.value = false
  }
}

function syncScopeText(row: GovJob) {
  const parts: string[] = []
  if (row.syncOrg) parts.push('组织')
  if (row.syncUser) parts.push('用户')
  if (row.syncRole) parts.push('角色')
  if (row.syncPassword) parts.push('账号密码')
  if (row.syncSms) parts.push('短信验证')
  return parts.join('、') || '—'
}

function currentTarget(): GovTarget | undefined {
  return govTargets.value.find((t) => t.id === syncForm.targetId)
}

async function changePassword() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning('请填写旧密码与新密码')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirm) {
    ElMessage.warning('两次新密码不一致')
    return
  }
  try {
    const envelope = await encryptTransportPayload({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    })
    await api.put('/auth/password', envelope)
    ElMessage.success('密码已修改，请使用新密码重新登录')
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirm = ''
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '修改失败')
  }
}

function onFilePick(kind: string, ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  void uploadAsset(kind, file)
  input.value = ''
}
</script>

<template>
  <div v-loading="loading" class="maint-appearance">
    <PageCard v-if="!props.embed" title="系统维护管理 · 外观">
      <p class="hint">主题、标识、登录页、页签、水印、账号安全与身份认证同步；保存后刷新生效。</p>
    </PageCard>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="主题" name="theme">
        <el-table :data="(cfg.themes as any[]) || []" stripe size="small" style="margin-bottom:16px">
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="id" label="标识" width="180" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ row.builtin ? '内置' : '自定义' }}</template>
          </el-table-column>
          <el-table-column label="主色" width="120">
            <template #default="{ row }">
              <span class="swatch" :style="{ background: row.primaryColor }" />
              {{ row.primaryColor }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="applyTheme(row.id)">应用并保存</el-button>
              <el-button v-if="row.builtin" link @click="downloadBuiltin(row.id)">下载</el-button>
              <el-button v-else link type="danger" @click="deleteTheme(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-divider content-position="left">上传自定义主题</el-divider>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="名称" class="portal-field-md"><el-input v-model="themeUpload.name" /></el-form-item>
          <el-form-item label="主色" class="portal-field-sm"><el-color-picker v-model="themeUpload.primaryColor" /></el-form-item>
          <el-form-item label="侧栏" class="portal-field-sm"><el-color-picker v-model="themeUpload.sidebarBg" /></el-form-item>
          <el-form-item label="文件" class="portal-field-lg">
            <input type="file" @change="(e) => (themeUpload.file = (e.target as HTMLInputElement).files?.[0] || null)" />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="uploadTheme">上传</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="系统标识" name="logo">
        <el-form label-width="140px" style="max-width:560px">
          <el-form-item label="Logo 模式">
            <el-radio-group v-model="cfg.logoMode">
              <el-radio value="CUSTOM">自定义图片</el-radio>
              <el-radio value="BLANK">空白（不显示）</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="cfg.logoMode === 'CUSTOM'" label="上传 Logo">
            <input type="file" accept="image/*" @change="(e) => onFilePick('logo', e)" />
            <img v-if="cfg.logoUrl" :src="String(cfg.logoUrl)" alt="logo" class="preview-img" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="saveAppearance({ logoMode: cfg.logoMode })">保存</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="登录页" name="login">
        <el-form label-width="140px" style="max-width:640px">
          <el-form-item label="登录验证码">
            <el-switch v-model="cfg.loginCaptchaEnabled" />
          </el-form-item>
          <el-form-item label="标题文字">
            <el-input v-model="cfg.loginTitle" />
          </el-form-item>
          <el-form-item label="标题字号">
            <el-input-number v-model="cfg.loginTitleFontSize" :min="16" :max="48" />
          </el-form-item>
          <el-form-item label="标题颜色">
            <el-color-picker v-model="cfg.loginTitleColor as string" />
          </el-form-item>
          <el-form-item label="背景模式">
            <el-radio-group v-model="cfg.loginBgMode">
              <el-radio value="DEFAULT">系统默认</el-radio>
              <el-radio value="CUSTOM">自定义媒体</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="cfg.loginBgMode === 'CUSTOM'" label="背景图/视频">
            <input type="file" accept="image/*,video/*" @change="(e) => onFilePick('login-media', e)" />
            <div v-if="cfg.loginMediaUrl" class="preview-media">
              <video v-if="cfg.loginMediaType === 'VIDEO'" :src="String(cfg.loginMediaUrl)" controls class="preview-video" />
              <img v-else :src="String(cfg.loginMediaUrl)" class="preview-img" alt="bg" />
            </div>
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              :loading="saving"
              @click="saveAppearance({
                loginCaptchaEnabled: cfg.loginCaptchaEnabled,
                loginTitle: cfg.loginTitle,
                loginTitleFontSize: cfg.loginTitleFontSize,
                loginTitleColor: cfg.loginTitleColor,
                loginBgMode: cfg.loginBgMode,
              })"
            >保存</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="浏览器页签" name="browser">
        <el-form label-width="140px" style="max-width:560px">
          <el-form-item label="页签标题">
            <el-input v-model="cfg.browserTitle" placeholder="空则使用默认产品名" />
          </el-form-item>
          <el-form-item label="Favicon">
            <input type="file" accept="image/*" @change="(e) => onFilePick('favicon', e)" />
            <img v-if="cfg.faviconUrl" :src="String(cfg.faviconUrl)" class="preview-favicon" alt="favicon" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="saveAppearance({ browserTitle: cfg.browserTitle })">保存</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="水印" name="watermark">
        <el-form label-width="160px" style="max-width:560px">
          <el-form-item label="启用水印">
            <el-switch v-model="cfg.watermarkEnabled" />
          </el-form-item>
          <el-form-item label="水印文字">
            <el-input v-model="cfg.watermarkText" />
          </el-form-item>
          <el-form-item label="叠加当前用户名">
            <el-switch v-model="cfg.watermarkShowUsername" />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              :loading="saving"
              @click="saveAppearance({
                watermarkEnabled: cfg.watermarkEnabled,
                watermarkText: cfg.watermarkText,
                watermarkShowUsername: cfg.watermarkShowUsername,
              })"
            >保存</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="账号安全" name="security">
        <el-form label-width="200px" style="max-width:640px">
          <el-divider content-position="left">登录锁定</el-divider>
          <el-form-item label="登录失败锁定次数">
            <el-input v-model="security.login_max_failures" />
          </el-form-item>
          <el-form-item label="锁定时长(分钟)">
            <el-input v-model="security.login_lock_minutes" />
          </el-form-item>
          <el-divider content-position="left">修改密码锁定</el-divider>
          <el-form-item label="改密失败上限">
            <el-input v-model="security.pwd_change_max_failures" />
          </el-form-item>
          <el-form-item label="改密锁定(分钟)">
            <el-input v-model="security.pwd_change_lock_minutes" />
          </el-form-item>
          <el-divider content-position="left">密码使用天数</el-divider>
          <el-form-item label="警告天数">
            <el-input v-model="security.pwd_expire_warn_days" />
          </el-form-item>
          <el-form-item label="锁定天数">
            <el-input v-model="security.pwd_expire_lock_days" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="saveSecurity">保存安全配置</el-button>
          </el-form-item>
          <el-divider content-position="left">本人修改密码</el-divider>
          <p class="hint">须点下方「修改密码」才会改库；「保存安全配置」只保存锁定天数等，不会改密码。新密码至少 8 位且含字母和数字。</p>
          <el-form-item label="旧密码"><el-input v-model="pwdForm.oldPassword" type="password" show-password autocomplete="current-password" /></el-form-item>
          <el-form-item label="新密码"><el-input v-model="pwdForm.newPassword" type="password" show-password autocomplete="new-password" /></el-form-item>
          <el-form-item label="确认新密码"><el-input v-model="pwdForm.confirm" type="password" show-password autocomplete="new-password" /></el-form-item>
          <el-form-item>
            <el-button type="primary" @click="changePassword">修改密码</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="身份认证与同步" name="auth-sync">
        <el-divider content-position="left">身份验证方式</el-divider>
        <p class="hint">支持用户名密码、短信验证码、动态令牌与指纹识别配置；短信/令牌开启后登录需填写第二因子。</p>
        <el-form label-width="160px" style="max-width:720px">
          <el-form-item label="用户名密码">
            <el-switch v-model="authMethods.password" disabled />
            <span class="form-hint">门户主认证，不可关闭</span>
          </el-form-item>
          <el-form-item label="短信验证码">
            <el-switch v-model="authMethods.sms" />
            <span class="form-hint">开启后登录需短信验证码（无网关可用演示码 000000）</span>
          </el-form-item>
          <el-form-item label="动态令牌(TOTP)">
            <el-switch v-model="authMethods.totp" />
          </el-form-item>
          <el-form-item label="指纹识别">
            <el-switch v-model="authMethods.fingerprint" />
            <span class="form-hint">配置预留；终端硬件未接入时不作为登录通过条件</span>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="saveAuthMethods">保存认证方式</el-button>
          </el-form-item>
        </el-form>

        <el-divider content-position="left">政务系统数据同步</el-divider>
        <p class="hint">选择组织、用户、角色及账号密码/短信验证相关字段，向政务对接系统推送，保证用户信息一致。</p>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="对接系统" class="portal-field-xl">
            <el-select v-model="syncForm.targetId" filterable placeholder="请选择" @change="loadGovJobs">
              <el-option
                v-for="t in govTargets"
                :key="t.id"
                :label="t.targetName"
                :value="t.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button @click="openTargetDialog()">新增对接</el-button>
            <el-button v-if="currentTarget()" @click="openTargetDialog(currentTarget())">编辑对接</el-button>
            <el-button v-if="currentTarget()" type="danger" plain @click="removeTarget(currentTarget()!)">删除对接</el-button>
          </el-form-item>
        </el-form>

        <el-form label-width="120px" style="max-width:920px;margin-bottom:12px">
          <el-form-item label="同步内容">
            <el-checkbox v-model="syncForm.syncOrg">组织</el-checkbox>
            <el-checkbox v-model="syncForm.syncUser">用户</el-checkbox>
            <el-checkbox v-model="syncForm.syncRole">角色</el-checkbox>
            <el-checkbox v-model="syncForm.syncPassword">账号密码（摘要）</el-checkbox>
            <el-checkbox v-model="syncForm.syncSms">短信验证信息</el-checkbox>
          </el-form-item>
          <el-form-item v-if="syncForm.syncOrg" label="组织范围">
            <el-select v-model="syncForm.orgIds" multiple filterable clearable placeholder="全部组织" style="width:100%">
              <el-option v-for="o in orgOptions" :key="o.id" :label="o.label" :value="o.id" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="syncForm.syncUser || syncForm.syncPassword || syncForm.syncSms" label="用户范围">
            <el-select v-model="syncForm.userIds" multiple filterable clearable placeholder="全部用户（或按所选组织）" style="width:100%">
              <el-option v-for="u in userOptions" :key="u.id" :label="u.label" :value="u.id" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="syncForm.syncRole" label="角色范围">
            <el-select v-model="syncForm.roleIds" multiple filterable clearable placeholder="全部角色" style="width:100%">
              <el-option v-for="r in roleOptions" :key="r.id" :label="r.label" :value="r.id" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="syncRunning" @click="runGovSync">执行同步</el-button>
            <el-button @click="loadGovJobs">刷新记录</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="govJobs" stripe border class="portal-table">
          <el-table-column prop="id" label="编号" width="80" />
          <el-table-column prop="targetName" label="对接系统" min-width="140" show-overflow-tooltip />
          <el-table-column label="同步范围" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ syncScopeText(row) }}</template>
          </el-table-column>
          <el-table-column prop="payloadSummary" label="摘要" min-width="200" show-overflow-tooltip />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="message" label="结果说明" min-width="220" show-overflow-tooltip />
          <el-table-column label="执行时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.startedAt || row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="createdByName" label="操作人" width="100" />
        </el-table>

        <el-dialog v-model="targetDialog" :title="targetForm.id == null ? '新增对接系统' : '编辑对接系统'" width="520px">
          <el-form label-width="100px">
            <el-form-item label="编码">
              <el-input v-model="targetForm.targetCode" placeholder="可空，自动生成" />
            </el-form-item>
            <el-form-item label="名称" required>
              <el-input v-model="targetForm.targetName" placeholder="如：市政务用户中心" />
            </el-form-item>
            <el-form-item label="Endpoint">
              <el-input v-model="targetForm.endpoint" placeholder="空则仅记台账，不伪造成功" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="targetForm.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="targetDialog = false">取消</el-button>
            <el-button type="primary" :loading="targetSaving" @click="submitTarget">确定</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.hint { color: var(--el-text-color-secondary); margin: 0 0 12px; }
.form-hint {
  margin-left: 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.swatch {
  display: inline-block;
  width: 14px;
  height: 14px;
  border-radius: 3px;
  vertical-align: middle;
  margin-right: 6px;
  border: 1px solid #ddd;
}
.preview-img { display: block; margin-top: 8px; max-height: 64px; }
.preview-favicon { display: block; margin-top: 8px; width: 32px; height: 32px; }
.preview-video { margin-top: 8px; max-width: 320px; max-height: 180px; }
</style>
