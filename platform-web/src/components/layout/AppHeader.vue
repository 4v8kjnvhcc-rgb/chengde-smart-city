<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import api from '@/api/http'
import { ArrowDown, DataBoard, HomeFilled } from '@element-plus/icons-vue'
import { isSystemRoute, findSubsystemRoot, findPlatformNode } from '@/utils/menu'
import type { AppearancePublic } from '@/utils/appearance'

const props = defineProps<{
  showBackHub?: boolean
  hubTheme?: boolean
}>()

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const appearance = ref<AppearancePublic | null>(null)
const pwdVisible = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })

onMounted(async () => {
  try {
    appearance.value = (await api.get('/system/appearance/public')).data
  } catch {
    appearance.value = null
  }
})

async function onUserCommand(cmd: string) {
  if (cmd === 'logout') {
    await logout()
  } else if (cmd === 'password') {
    pwdVisible.value = true
  }
}

async function submitPwd() {
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
    pwdVisible.value = false
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirm = ''
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '修改失败')
  }
}

const breadcrumbs = computed(() => {
  const items: { title: string }[] = []
  if (isSystemRoute(route.path)) {
    const node = findPlatformNode(auth.menus, '/system')
    if (node) items.push({ title: node.menuName })
  } else {
    const sub = findSubsystemRoot(auth.menus, route.path)
    if (sub) items.push({ title: sub.menuName })
  }
  const pageTitle = route.meta?.title as string | undefined
  if (pageTitle && pageTitle !== '功能页') {
    const last = items[items.length - 1]?.title
    if (pageTitle !== last) items.push({ title: pageTitle })
  }
  return items
})

function goHub() {
  router.push('/dashboard')
}

async function logout() {
  await auth.logout()
  router.push('/login')
}
</script>

<template>
  <header class="app-header" :class="{ 'app-header--hub': props.hubTheme }">
    <div class="app-header__left">
      <div class="app-header__logo" @click="goHub">
        <span v-if="appearance?.logoMode === 'BLANK'" class="app-header__logo-mark app-header__logo-mark--blank" />
        <img
          v-else-if="appearance?.logoUrl"
          :src="appearance.logoUrl"
          class="app-header__logo-img"
          alt="logo"
        />
        <span v-else class="app-header__logo-mark">
          <el-icon :size="20"><DataBoard /></el-icon>
        </span>
        <span class="app-header__logo-text">
          {{ props.hubTheme ? '统一门户' : '承德智慧城市' }}
        </span>
      </div>
      <el-button
        v-if="props.showBackHub"
        class="app-header__back"
        text
        type="primary"
        @click="goHub"
      >
        <el-icon><HomeFilled /></el-icon>
        返回总览
      </el-button>
      <el-breadcrumb v-if="!props.hubTheme && breadcrumbs.length" separator="/">
        <el-breadcrumb-item v-for="(b, i) in breadcrumbs" :key="i">{{ b.title }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>
    <div class="app-header__right">
      <el-dropdown trigger="click" @command="onUserCommand">
        <span class="app-header__user">
          {{ auth.user?.displayName || auth.user?.username }}
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="password">修改密码</el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
    <el-dialog v-model="pwdVisible" title="修改密码" width="420px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="旧密码">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="pwdForm.confirm" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPwd">确定</el-button>
      </template>
    </el-dialog>
  </header>
</template>

<style scoped>
.app-header {
  height: var(--portal-header-height);
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--portal-header-bg);
  border-bottom: 1px solid var(--portal-border);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}
.app-header--hub {
  background: transparent;
  border-bottom-color: rgba(142, 207, 255, 0.22);
  box-shadow: none;
}
.app-header__left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.app-header__logo {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}
.app-header__logo-mark {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #fff;
  background: linear-gradient(135deg, var(--portal-primary), var(--portal-primary-dark));
}
.app-header__logo-mark--blank {
  background: transparent;
  border: 1px dashed var(--portal-border);
}
.app-header__logo-img {
  width: 32px;
  height: 32px;
  object-fit: contain;
  border-radius: 6px;
  flex-shrink: 0;
}
.app-header--hub .app-header__logo-mark {
  color: #e8f6ff;
  background: linear-gradient(
    135deg,
    rgba(70, 150, 220, 0.42) 0%,
    rgba(120, 190, 255, 0.28) 50%,
    rgba(210, 175, 90, 0.32) 100%
  );
  border: 1px solid rgba(160, 215, 255, 0.5);
  box-shadow: 0 0 14px rgba(100, 180, 255, 0.28);
  backdrop-filter: blur(6px);
}
.app-header--hub .app-header__logo:hover .app-header__logo-mark {
  color: #fff8e8;
  border-color: rgba(242, 214, 138, 0.65);
  box-shadow: 0 0 18px rgba(242, 214, 138, 0.35);
}
.app-header__logo-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--portal-text);
  white-space: nowrap;
}
.app-header--hub .app-header__logo-text {
  font-size: 18px;
  letter-spacing: 0.04em;
  background: linear-gradient(90deg, #8ecfff 0%, #e8f6ff 45%, #f2d68a 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  filter: drop-shadow(0 1px 6px rgba(0, 20, 50, 0.45));
}
.app-header__back {
  padding: 4px 8px;
}
.app-header__user {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  font-size: 14px;
  color: var(--portal-text);
}
.app-header--hub .app-header__user {
  color: #d6ecff;
  text-shadow: 0 1px 6px rgba(0, 20, 50, 0.45);
}
.app-header--hub .app-header__user:hover {
  color: #f2d68a;
}
</style>
