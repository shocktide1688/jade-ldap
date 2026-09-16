<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'


defineOptions({ name: 'SystemMenu' })
const TYPE_LABELS: Record<string, string> = { M: '目录', C: '菜单', F: '按钮' }
const TYPE_TAGS: Record<string, 'info' | 'success' | 'warning' | 'primary' | 'danger'> = {
  M: 'info', C: 'success', F: 'warning',
}
function typeLabel(t: string) { return TYPE_LABELS[t] || t }
function typeTag(t: string) { return TYPE_TAGS[t] || 'info' }

const loading = ref(false)
const list = ref<any[]>([])
const showEdit = ref(false)
const editing = reactive<any>({ id: null, parentId: 0, menuName: '', menuType: 'C', path: '', component: '', icon: '', perms: '', sortOrder: 0, visible: 1, status: 1, isCache: 0 })
const allMenus = ref<any[]>([])  // 用于 parent 选

async function fetchData() {
  loading.value = true
  try {
    const res = await request({ url: '/api/v1/menus/tree', method: 'GET' })
    list.value = res.data.data
    // 拉所有平铺菜单做 parent 下拉
    const tree = res.data.data
    allMenus.value = flattenTree(tree)
  } finally { loading.value = false }
}

function flattenTree(tree: any[], result: any[] = []): any[] {
  for (const node of tree) {
    result.push({ id: node.id, menuName: node.menuName, parentId: node.parentId })
    if (node.children) flattenTree(node.children, result)
  }
  return result
}

function openCreate() {
  Object.assign(editing, { id: null, parentId: 0, menuName: '', menuType: 'C', path: '', component: '', icon: '', perms: '', sortOrder: 0, visible: 1, status: 1, isCache: 0 })
  showEdit.value = true
}

function openEdit(row: any) {
  Object.assign(editing, row)
  showEdit.value = true
}

async function save() {
  if (!editing.menuName) return ElMessage.warning('菜单名必填')
  try {
    if (editing.id) {
      await request({ url: `/api/v1/menus/${editing.id}`, method: 'PUT', data: editing })
    } else {
      await request({ url: '/api/v1/menus', method: 'POST', data: editing })
    }
    ElMessage.success('保存成功')
    showEdit.value = false
    fetchData()
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '保存失败') }
}

async function remove(row: any) {
  try {
    await ElMessageBox.confirm(`删除菜单「${row.menuName}」？`, '提示', { type: 'warning' })
    await request({ url: `/api/v1/menus/${row.id}`, method: 'DELETE' })
    ElMessage.success('已删除')
    fetchData()
  } catch (e: any) { if (e !== 'cancel') ElMessage.error('删除失败') }
}

onMounted(fetchData)
</script>

<template>
  <el-card>
    <div class="toolbar">
      <el-button type="success" @click="openCreate" plain>+ 新建菜单</el-button>
    </div>
    <el-table v-loading="loading" :data="list" row-key="id" :tree-props="{ children: 'children' }" default-expand-all stripe>
      <el-table-column prop="menuName" label="菜单名" width="200" />
      <el-table-column prop="menuType" label="类型" width="80">
        <template #default="{ row }">
          <el-tag :type="typeTag(row.menuType)" size="small">{{ typeLabel(row.menuType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="path" label="路由" width="160" />
      <el-table-column prop="component" label="组件" width="200" />
      <el-table-column prop="perms" label="权限标识" width="200" />
      <el-table-column prop="sortOrder" label="排序" width="60" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '显示' : '隐藏' }}
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

  <el-dialog v-model="showEdit" :title="editing.id ? '编辑菜单' : '新建菜单'" width="600">
    <el-form :model="editing" label-width="100">
      <el-form-item label="类型">
        <el-radio-group v-model="editing.menuType">
          <el-radio value="M">目录</el-radio>
          <el-radio value="C">菜单</el-radio>
          <el-radio value="F">按钮</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="父菜单">
        <el-select v-model="editing.parentId" placeholder="不选则为顶级" clearable>
          <el-option v-for="m in allMenus.filter(x => x.id !== editing.id)" :key="m.id" :label="m.menuName" :value="m.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="菜单名">
        <el-input v-model="editing.menuName" />
      </el-form-item>
      <el-form-item v-if="editing.menuType !== 'F'" label="路由">
        <el-input v-model="editing.path" placeholder="如 system/user" />
      </el-form-item>
      <el-form-item v-if="editing.menuType === 'C'" label="组件">
        <el-input v-model="editing.component" placeholder="如 system/user/index" />
      </el-form-item>
      <el-form-item label="权限标识">
        <el-input v-model="editing.perms" placeholder="如 system:user:list" />
      </el-form-item>
      <el-form-item v-if="editing.menuType !== 'F'" label="图标">
        <el-input v-model="editing.icon" placeholder="Element Plus 图标名" />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="editing.sortOrder" :min="0" />
      </el-form-item>
      <el-form-item v-if="editing.menuType !== 'F'" label="显示">
        <el-radio-group v-model="editing.visible">
          <el-radio :value="1">显示</el-radio>
          <el-radio :value="0">隐藏</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="editing.menuType !== 'F'" label="状态">
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
