<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'


defineOptions({ name: 'SystemNotice' })
const loading = ref(false)
const list = ref<any[]>([])
const showEdit = ref(false)
const editing = reactive<any>({ id: null, noticeTitle: '', noticeType: 1, noticeContent: '', status: 1 })
const total = ref(0)
const page = ref(1)
const size = ref(10)
const title = ref('')

async function fetchData() {
  loading.value = true
  try {
    const res = await request({ url: '/api/v1/notices/page', method: 'GET', params: { page: page.value, size: size.value, title: title.value || undefined } })
    list.value = res.data.data.records
    total.value = res.data.data.total
  } finally { loading.value = false }
}

function openCreate() {
  Object.assign(editing, { id: null, noticeTitle: '', noticeType: 1, noticeContent: '', status: 1 })
  showEdit.value = true
}

function openEdit(row: any) {
  Object.assign(editing, row)
  showEdit.value = true
}

async function save() {
  if (!editing.noticeTitle) return ElMessage.warning('标题必填')
  try {
    if (editing.id) {
      await request({ url: `/api/v1/notices/${editing.id}`, method: 'PUT', data: editing })
    } else {
      await request({ url: '/api/v1/notices', method: 'POST', data: editing })
    }
    ElMessage.success('保存成功')
    showEdit.value = false
    fetchData()
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '保存失败') }
}

async function remove(row: any) {
  try {
    await ElMessageBox.confirm(`删除公告「${row.noticeTitle}」？`, '提示', { type: 'warning' })
    await request({ url: `/api/v1/notices/${row.id}`, method: 'DELETE' })
    ElMessage.success('已删除')
    fetchData()
  } catch (e: any) { if (e !== 'cancel') ElMessage.error('删除失败') }
}

onMounted(fetchData)
</script>

<template>
  <el-card>
    <div class="toolbar">
      <el-input v-model="title" placeholder="搜索标题" clearable style="width: 240px;" @keyup.enter="fetchData" />
      <el-button type="primary" @click="fetchData">搜索</el-button>
      <el-button type="success" @click="openCreate" plain>+ 发布公告</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="noticeTitle" label="标题" show-overflow-tooltip />
      <el-table-column prop="noticeType" label="类型" width="80">
        <template #default="{ row }">
          <el-tag :type="row.noticeType === 1 ? 'primary' : 'warning'" size="small">
            {{ row.noticeType === 1 ? '通知' : '公告' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdBy" label="发布人" width="120" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button text type="danger" @click="remove(row)">删除</el-button>
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

  <el-dialog v-model="showEdit" :title="editing.id ? '编辑公告' : '发布公告'" width="640">
    <el-form :model="editing" label-width="100">
      <el-form-item label="标题"><el-input v-model="editing.noticeTitle" /></el-form-item>
      <el-form-item label="类型">
        <el-radio-group v-model="editing.noticeType">
          <el-radio :value="1">通知</el-radio>
          <el-radio :value="2">公告</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="内容">
        <el-input v-model="editing.noticeContent" type="textarea" :rows="6" placeholder="支持 HTML 标签" />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="editing.status">
          <el-radio :value="1">发布</el-radio>
          <el-radio :value="0">草稿</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showEdit = false">取消</el-button>
      <el-button type="primary" @click="save">发布</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }</style>
