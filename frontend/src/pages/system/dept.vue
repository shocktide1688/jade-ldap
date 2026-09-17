<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'


defineOptions({ name: 'SystemDept' })
const loading = ref(false)
const list = ref<any[]>([])
const showEdit = ref(false)
const editing = reactive<any>({ id: null, parentId: 0, deptName: '', deptCode: '', sortOrder: 0, leaderUserId: null, phone: '', email: '', status: 1 })
const allDepts = ref<any[]>([])

async function fetchData() {
  loading.value = true
  try {
    const res = await request({ url: '/api/v1/depts/all', method: 'GET' })
    list.value = res.data.data
    allDepts.value = res.data.data
  } finally { loading.value = false }
}

function openCreate() {
  Object.assign(editing, { id: null, parentId: 0, deptName: '', deptCode: '', sortOrder: 0, leaderUserId: null, phone: '', email: '', status: 1 })
  showEdit.value = true
}

function openEdit(row: any) {
  Object.assign(editing, row)
  showEdit.value = true
}

async function save() {
  if (!editing.deptName) return ElMessage.warning('部门名必填')
  try {
    if (editing.id) {
      await request({ url: `/api/v1/depts/${editing.id}`, method: 'PUT', data: editing })
    } else {
      await request({ url: '/api/v1/depts', method: 'POST', data: editing })
    }
    ElMessage.success('保存成功')
    showEdit.value = false
    fetchData()
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '保存失败') }
}

async function remove(row: any) {
  try {
    await ElMessageBox.confirm(`删除部门「${row.deptName}」？`, '提示', { type: 'warning' })
    await request({ url: `/api/v1/depts/${row.id}`, method: 'DELETE' })
    ElMessage.success('已删除')
    fetchData()
  } catch (e: any) { if (e !== 'cancel') ElMessage.error(e?.response?.data?.message || '删除失败') }
}

onMounted(fetchData)
</script>

<template>
  <el-card>
    <div class="toolbar">
      <el-button type="success" @click="openCreate" plain>+ 新建部门</el-button>
    </div>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="deptName" label="部门名" />
      <el-table-column prop="deptCode" label="部门代码" width="120" />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column prop="phone" label="电话" width="140" />
      <el-table-column prop="email" label="邮箱" width="200" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button text type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="showEdit" :title="editing.id ? '编辑部门' : '新建部门'" width="540">
    <el-form :model="editing" label-width="100">
      <el-form-item label="部门名"><el-input v-model="editing.deptName" /></el-form-item>
      <el-form-item label="部门代码"><el-input v-model="editing.deptCode" /></el-form-item>
      <el-form-item label="父部门">
        <el-select v-model="editing.parentId" placeholder="不选则为顶级" clearable>
          <el-option v-for="d in allDepts.filter(x => x.id !== editing.id)" :key="d.id" :label="d.deptName" :value="d.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="排序"><el-input-number v-model="editing.sortOrder" :min="0" /></el-form-item>
      <el-form-item label="电话"><el-input v-model="editing.phone" /></el-form-item>
      <el-form-item label="邮箱"><el-input v-model="editing.email" /></el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="editing.status">
          <el-radio :value="1">正常</el-radio>
          <el-radio :value="0">停用</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showEdit = false">取消</el-button>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>.toolbar { margin-bottom: 16px; }</style>
