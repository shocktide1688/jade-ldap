<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'


defineOptions({ name: 'Profile' })
const userStore = useUserStore()
const profile = reactive<any>({ id: null, username: '', nickname: '', email: '', phone: '', status: 1, tenantId: null, createdAt: '' })
const pwdForm = reactive({ old: '', new: '' })
const saving = ref(false)
const savingPwd = ref(false)

async function load() {
  const res = await request({ url: '/api/v1/auth/me', method: 'GET' })
  Object.assign(profile, res.data.data)
  userStore.setUser(res.data.data)
}

async function saveProfile() {
  saving.value = true
  try {
    await request({ url: '/api/v1/auth/profile', method: 'PUT', data: profile })
    ElMessage.success('已保存')
    userStore.setUser(profile)
  } finally { saving.value = false }
}

async function changePassword() {
  if (!pwdForm.old || !pwdForm.new) return ElMessage.warning('请填写原密码和新密码')
  savingPwd.value = true
  try {
    await request({ url: `/api/v1/auth/password?old=${encodeURIComponent(pwdForm.old)}&new=${encodeURIComponent(pwdForm.new)}`, method: 'PUT' })
    ElMessage.success('密码已修改')
    pwdForm.old = ''
    pwdForm.new = ''
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '修改失败')
  } finally { savingPwd.value = false }
}

onMounted(load)
</script>

<template>
  <el-row :gutter="16">
    <el-col :span="12">
      <el-card>
        <template #header><span>个人资料</span></template>
        <el-form :model="profile" label-width="100" label-position="right">
          <el-form-item label="ID">
            <el-input v-model="profile.id" disabled />
          </el-form-item>
          <el-form-item label="用户名">
            <el-input v-model="profile.username" disabled />
          </el-form-item>
          <el-form-item label="昵称">
            <el-input v-model="profile.nickname" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="profile.email" />
          </el-form-item>
          <el-form-item label="电话">
            <el-input v-model="profile.phone" />
          </el-form-item>
          <el-form-item label="状态">
            <el-tag :type="profile.status === 1 ? 'success' : 'danger'">
              {{ profile.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </el-form-item>
          <el-form-item label="注册时间">
            <el-input v-model="profile.createdAt" disabled />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="saveProfile">保存</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </el-col>

    <el-col :span="12">
      <el-card>
        <template #header><span>修改密码</span></template>
        <el-form :model="pwdForm" label-width="100" label-position="right">
          <el-form-item label="原密码">
            <el-input v-model="pwdForm.old" type="password" show-password />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="pwdForm.new" type="password" show-password />
          </el-form-item>
          <el-form-item>
            <el-button type="warning" :loading="savingPwd" @click="changePassword">修改密码</el-button>
          </el-form-item>
        </el-form>
        <el-alert type="info" :closable="false" show-icon>
          <p>修改密码后需用新密码重新登录</p>
          <p>建议：至少 8 位，包含字母+数字</p>
        </el-alert>
      </el-card>
    </el-col>
  </el-row>
</template>
