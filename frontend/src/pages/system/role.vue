<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'


defineOptions({ name: 'SystemRole' })
const loading = ref(false)
const list = ref<any[]>([])
const showEdit = ref(false)
const showAssign = ref(false)
const editing = reactive<any>({ id: null, roleName: '', roleCode: '', dataScope: 'ALL', status: 1, roleSort: 0 })
const assignTarget = ref<any>(null)
const tree = ref<any[]>([])
const treeRef = ref<any>()
const checkedKeys = ref<any[]>([])

async function fetchData() {
  loading.value = true
  try {
    const res = await request({ url: '/api/v1/roles/all', method: 'GET' })
    list.value = res.data.data
  } finally { loading.value = false }
}

function openCreate() {
  Object.assign(editing, { id: null, roleName: '', roleCode: '', dataScope: 'ALL', status: 1, roleSort: 0 })
  showEdit.value = true
}

function openEdit(row: any) {
  Object.assign(editing, row)
  showEdit.value = true
}

async function save() {
  if (!editing.roleName || !editing.roleCode) return ElMessage.warning('角色名和代码必填')
  try {
    if (editing.id) {
      await request({ url: `/api/v1/roles/${editing.id}`, method: 'PUT', data: editing })
    } else {
      await request({ url: '/api/v1/roles', method: 'POST', data: editing })
    }
    ElMessage.success('保存成功')
    showEdit.value = false
    fetchData()
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '保存失败') }
}

async function remove(row: any) {
  try {
    await ElMessageBox.confirm(`删除角色「${row.roleName}」？`, '提示', { type: 'warning' })
    await request({ url: `/api/v1/roles/${row.id}`, method: 'DELETE' })
    ElMessage.success('已删除')
    fetchData()
  } catch (e: any) { if (e !== 'cancel') ElMessage.error('删除失败') }
}

async function openAssign(row: any) {
  assignTarget.value = row
  // 拉所有菜单树
  const treeRes = await request({ url: '/api/v1/menus/tree', method: 'GET' })
  tree.value = treeRes.data.data
  // 拉角色已分配的 menu_ids
  const assignedRes = await request({ url: `/api/v1/roles/${row.id}/menus`, method: 'GET' })
  checkedKeys.value = assignedRes.data.data || []
  // 展开所有节点
  await nextTick()
  treeRef.value?.store.nodesMap.forEach((n: any) => n.expand())
  showAssign.value = true
}

async function doAssign() {
  // 收集选中的 key (含半选 = 父级, 但后端只需要 leaf)
  const checked = treeRef.value?.getCheckedKeys() || []
  const halfChecked = treeRef.value?.getHalfCheckedKeys() || []
  const allKeys = [...checked, ...halfChecked]
  await request({ url: `/api/v1/roles/${assignTarget.value.id}/menus`, method: 'PUT', data: { menuIds: allKeys } })
  ElMessage.success('已分配')
  showAssign.value = false
}

import { nextTick } from 'vue'

onMounted(fetchData)
</script>

<template>
  <el-card>
    <div class="toolbar">
      <el-button type="success" @click="openCreate" plain>+ 新建角色</el-button>
    </div>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="roleName" label="角色名" />
      <el-table-column prop="roleCode" label="角色代码" />
      <el-table-column prop="dataScope" label="数据范围" width="120" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button text type="success" @click="openAssign(row)">分配菜单</el-button>
          <el-button text type="danger" @click="remove(row)" :disabled="row.roleCode === 'admin'">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <!-- 新建/编辑 -->
  <el-dialog v-model="showEdit" :title="editing.id ? '编辑角色' : '新建角色'" width="500">
    <el-form :model="editing" label-width="100">
      <el-form-item label="角色名"><el-input v-model="editing.roleName" /></el-form-item>
      <el-form-item label="角色代码"><el-input v-model="editing.roleCode" :disabled="!!editing.id" /></el-form-item>
      <el-form-item label="数据范围">
        <el-select v-model="editing.dataScope">
          <el-option label="全部" value="ALL" />
          <el-option label="本部门及下级" value="DEPT_AND_CHILD" />
          <el-option label="本部门" value="DEPT" />
          <el-option label="仅本人" value="SELF" />
        </el-select>
      </el-form-item>
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

  <!-- 分配菜单 -->
  <el-dialog v-model="showAssign" :title="`分配菜单 - ${assignTarget?.roleName}`" width="540">
    <p style="margin-top: 0; color: var(--jade-text-secondary);">勾选要给该角色分配的菜单（包含子级会自动带父级）</p>
    <el-tree
      ref="treeRef"
      :data="tree"
      show-checkbox
      node-key="id"
      :default-checked-keys="checkedKeys"
      :props="{ children: 'children', label: 'menuName' }"
      style="max-height: 480px; overflow: auto;"
    />
    <template #footer>
      <el-button @click="showAssign = false">取消</el-button>
      <el-button type="primary" @click="doAssign">保存分配</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>.toolbar { margin-bottom: 16px; }</style>
