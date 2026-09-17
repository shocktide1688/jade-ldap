<script setup lang="ts">
import dayjs from 'dayjs'
import { useMediaQuery } from '@vueuse/core'

const fmtTime = (t?: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '')

// 窄屏取消操作列固定，避免固定列盖住数据
const wide = useMediaQuery('(min-width: 640px)')
import { onMounted, reactive, ref } from 'vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'


defineOptions({ name: 'SystemUser' })
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')

const showEdit = ref(false)
const showResetPwd = ref(false)
const editing = reactive<any>({ id: null, username: '', password: '', nickname: '', email: '', phone: '', status: 1, tenantId: null })
const resetTarget = ref<any>(null)
const newPassword = ref('')

async function fetchData() {
  loading.value = true
  try {
    const res = await request({ url: '/api/v1/users/page', method: 'GET',
      params: { page: page.value, size: size.value, keyword: keyword.value || undefined } })
    list.value = res.data.data.records
    total.value = res.data.data.total
  } finally { loading.value = false }
}

function openCreate() {
  Object.assign(editing, { id: null, username: '', password: '', nickname: '', email: '', phone: '', status: 1, tenantId: null })
  showEdit.value = true
}

function openEdit(row: any) {
  Object.assign(editing, row, { password: '' })
  showEdit.value = true
}

async function save() {
  if (!editing.username) return ElMessage.warning('用户名必填')
  try {
    if (editing.id) {
      await request({ url: `/api/v1/users/${editing.id}`, method: 'PUT', data: editing })
      ElMessage.success('更新成功')
    } else {
      if (!editing.password) return ElMessage.warning('新建用户需填密码')
      await request({ url: '/api/v1/users', method: 'POST', data: editing })
      ElMessage.success('创建成功')
    }
    showEdit.value = false
    fetchData()
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '保存失败') }
}

async function remove(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除用户 ${row.username}？`, '提示', { type: 'warning' })
    await request({ url: `/api/v1/users/${row.id}`, method: 'DELETE' })
    ElMessage.success('删除成功')
    fetchData()
  } catch (e: any) { if (e !== 'cancel') ElMessage.error('删除失败') }
}

async function toggleStatus(row: any) {
  const newStatus = row.status === 1 ? 0 : 1
  await request({ url: `/api/v1/users/${row.id}/status?status=${newStatus}`, method: 'PUT' })
  ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
  fetchData()
}

function openResetPwd(row: any) {
  resetTarget.value = row
  newPassword.value = ''
  showResetPwd.value = true
}

async function doResetPwd() {
  if (!newPassword.value) return ElMessage.warning('请输入新密码')
  await request({ url: `/api/v1/users/${resetTarget.value.id}/reset-password?newPassword=${newPassword.value}`, method: 'PUT' })
  ElMessage.success('密码已重置')
  showResetPwd.value = false
}

onMounted(fetchData)
</script>

<template>
  <el-card>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索用户名/昵称" clearable style="width: 240px;" @keyup.enter="fetchData" />
      <el-button type="primary" @click="fetchData">搜索</el-button>
      <el-button type="success" @click="openCreate" plain>+ 新建</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column prop="email" label="邮箱" />
      <el-table-column prop="phone" label="电话" width="120" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-switch :model-value="row.status === 1" @change="toggleStatus(row)" :disabled="row.username === 'admin'" />
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="150">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" :fixed="wide ? 'right' : false">
        <template #default="{ row }">
          <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button text type="warning" @click="openResetPwd(row)">重置密码</el-button>
          <el-button text type="danger" @click="remove(row)" :disabled="row.username === 'admin'">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page" v-model:page-size="size" :total="total"
      :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
      style="margin-top: 16px; justify-content: flex-end;"
      @current-change="fetchData" @size-change="fetchData"
    />
  </el-card>

  <!-- 新建/编辑用户 -->
  <el-dialog v-model="showEdit" :title="editing.id ? '编辑用户' : '新建用户'" width="540">
    <el-form :model="editing" label-width="100" label-position="right">
      <el-form-item label="用户名">
        <el-input v-model="editing.username" :disabled="!!editing.id" />
      </el-form-item>
      <el-form-item v-if="!editing.id" label="密码">
        <el-input v-model="editing.password" type="password" show-password placeholder="新建时必填" />
      </el-form-item>
      <el-form-item label="昵称">
        <el-input v-model="editing.nickname" />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="editing.email" />
      </el-form-item>
      <el-form-item label="电话">
        <el-input v-model="editing.phone" />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="editing.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">禁用</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showEdit = false">取消</el-button>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </el-dialog>

  <!-- 重置密码 -->
  <el-dialog v-model="showResetPwd" title="重置密码" width="420">
    <p>用户：<b>{{ resetTarget?.username }}</b></p>
    <el-input v-model="newPassword" type="password" show-password placeholder="新密码" />
    <template #footer>
      <el-button @click="showResetPwd = false">取消</el-button>
      <el-button type="primary" @click="doResetPwd">确定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }</style>
