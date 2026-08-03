<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { applyBrowserChrome, applyThemeFromAppearance, type AppearancePublic } from '@/utils/appearance'

const props = defineProps<{ embed?: boolean }>()

const activeTab = ref('theme')
const loading = ref(false)
const saving = ref(false)
const cfg = ref<AppearancePublic & Record<string, unknown>>({})
const security = reactive<Record<string, string>>({})

const themeUpload = reactive({ name: '', primaryColor: '#1677ff', sidebarBg: '#001529', file: null as File | null })
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })

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

onMounted(() => {
  void loadAppearance()
})

watch(activeTab, (t) => {
  if (t === 'security' && !security.login_max_failures) {
    void loadSecurity()
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
    await api.put('/auth/password', {
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    })
    ElMessage.success('密码已修改')
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
      <p class="hint">主题、标识、登录页、页签、水印与账号安全；保存后刷新生效。</p>
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
          <el-table-column label="操作" width="260">
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
          <el-form-item label="旧密码"><el-input v-model="pwdForm.oldPassword" type="password" show-password /></el-form-item>
          <el-form-item label="新密码"><el-input v-model="pwdForm.newPassword" type="password" show-password /></el-form-item>
          <el-form-item label="确认新密码"><el-input v-model="pwdForm.confirm" type="password" show-password /></el-form-item>
          <el-form-item>
            <el-button type="primary" @click="changePassword">修改密码</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.hint { color: var(--el-text-color-secondary); margin: 0 0 12px; }
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
