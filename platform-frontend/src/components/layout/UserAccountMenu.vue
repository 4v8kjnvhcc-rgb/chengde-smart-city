<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import api from '@/api/http'

const props = withDefaults(
  defineProps<{
    /** default=浅色页；onDark=深蓝顶栏 */
    tone?: 'default' | 'onDark'
  }>(),
  { tone: 'default' },
)

const auth = useAuthStore()
const router = useRouter()
const profileVisible = ref(false)
const pwdVisible = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })

const displayLabel = computed(
  () => auth.user?.displayName || auth.user?.username || '用户',
)

async function onCommand(cmd: string) {
  if (cmd === 'profile') {
    profileVisible.value = true
  } else if (cmd === 'password') {
    pwdVisible.value = true
  } else if (cmd === 'logout') {
    await auth.logout()
    router.push('/login')
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
</script>

<template>
  <div class="user-account" :class="{ 'user-account--on-dark': props.tone === 'onDark' }">
    <el-dropdown trigger="click" @command="onCommand">
      <span class="user-account__trigger" tabindex="0">
        <el-icon class="user-account__avatar"><User /></el-icon>
        <span class="user-account__name">{{ displayLabel }}</span>
        <el-icon class="user-account__caret"><ArrowDown /></el-icon>
      </span>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="profile">基本信息</el-dropdown-item>
          <el-dropdown-item command="password">修改密码</el-dropdown-item>
          <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <el-dialog v-model="profileVisible" title="用户基本信息" width="420px" destroy-on-close append-to-body>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="用户名">{{ auth.user?.username || '—' }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ auth.user?.displayName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="所属组织">{{ auth.user?.orgName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="组织 ID">{{ auth.user?.orgId ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="用户 ID">{{ auth.user?.id ?? '—' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="profileVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pwdVisible" title="修改密码" width="420px" destroy-on-close append-to-body>
      <el-form label-width="100px">
        <el-form-item label="旧密码">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="pwdForm.confirm" type="password" show-password autocomplete="new-password" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPwd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.user-account__trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 14px;
  color: var(--portal-text, #303133);
  outline: none;
  user-select: none;
}
.user-account__avatar {
  font-size: 16px;
}
.user-account__name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-account__caret {
  font-size: 12px;
}
.user-account--on-dark .user-account__trigger {
  color: #e8f4ff;
  text-shadow: 0 1px 6px rgba(0, 20, 50, 0.35);
}
.user-account--on-dark .user-account__trigger:hover {
  color: #f2d68a;
}
</style>
