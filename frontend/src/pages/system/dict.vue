<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

defineOptions({ name: 'SystemDict' })

// ---- 字典类型 ----
const loading = ref(false)
const list = ref<any[]>([])
const activeType = ref<string>('')
const showTypeEdit = ref(false)
const typeForm = reactive<any>({ id: null, dictName: '', dictType: '', remark: '', status: 1 })

async function fetchTypes() {
  loading.value = true
  try {
    const res = await request({ url: '/api/v1/dict/types', method: 'GET' })
    list.value = res.data.data
    if (list.value.length > 0 && !list.value.some((t) => t.dictType === activeType.value)) {
      activeType.value = list.value[0].dictType
    }
    if (activeType.value) await fetchItems()
    else items.value = []
  } finally { loading.value = false }
}

function openTypeCreate() {
  Object.assign(typeForm, { id: null, dictName: '', dictType: '', remark: '', status: 1 })
  showTypeEdit.value = true
}

function openTypeEdit(row: any) {
  Object.assign(typeForm, row)
  showTypeEdit.value = true
}

async function saveType() {
  if (!typeForm.dictName) return ElMessage.warning('名称必填')
  if (!typeForm.dictType) return ElMessage.warning('类型 key 必填')
  try {
    if (typeForm.id) {
      await request({ url: `/api/v1/dict/types/${typeForm.id}`, method: 'PUT', data: typeForm })
    } else {
      await request({ url: '/api/v1/dict/types', method: 'POST', data: typeForm })
    }
    ElMessage.success('保存成功')
    showTypeEdit.value = false
    await fetchTypes()
    activeType.value = typeForm.dictType
    await fetchItems()
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '保存失败') }
}

async function removeType(row: any) {
  try {
    await ElMessageBox.confirm(`删除字典类型「${row.dictName}」及其全部字典项？`, '提示', { type: 'warning' })
    await request({ url: `/api/v1/dict/types/${row.id}`, method: 'DELETE' })
    ElMessage.success('已删除')
    activeType.value = ''
    fetchTypes()
  } catch (e: any) { if (e !== 'cancel') ElMessage.error('删除失败') }
}

// ---- 字典项 ----
const items = ref<any[]>([])
const showDataEdit = ref(false)
const dataForm = reactive<any>({ id: null, dictType: '', dictLabel: '', dictValue: '', cssClass: '', sortOrder: 0, status: 1, remark: '' })

async function fetchItems() {
  if (!activeType.value) { items.value = []; return }
  const res = await request({ url: `/api/v1/dict/data`, method: 'GET', params: { type: activeType.value, all: true } })
  items.value = res.data.data
}

function openDataCreate() {
  Object.assign(dataForm, { id: null, dictType: activeType.value, dictLabel: '', dictValue: '', cssClass: '', sortOrder: 0, status: 1, remark: '' })
  showDataEdit.value = true
}

function openDataEdit(row: any) {
  Object.assign(dataForm, row)
  showDataEdit.value = true
}

async function saveData() {
  if (!dataForm.dictLabel) return ElMessage.warning('标签必填')
  if (!dataForm.dictValue) return ElMessage.warning('值必填')
  try {
    if (dataForm.id) {
      await request({ url: `/api/v1/dict/data/${dataForm.id}`, method: 'PUT', data: dataForm })
    } else {
      await request({ url: `/api/v1/dict/data`, method: 'POST', data: dataForm })
    }
    ElMessage.success('保存成功')
    showDataEdit.value = false
    fetchItems()
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '保存失败') }
}

async function removeData(row: any) {
  try {
    await ElMessageBox.confirm(`删除字典项「${row.dictLabel}」？`, '提示', { type: 'warning' })
    await request({ url: `/api/v1/dict/data/${row.id}`, method: 'DELETE' })
    ElMessage.success('已删除')
    fetchItems()
  } catch (e: any) { if (e !== 'cancel') ElMessage.error('删除失败') }
}

function selectType(row: any) {
  activeType.value = row.dictType
  fetchItems()
}

onMounted(fetchTypes)
</script>

<template>
  <el-card>
    <el-row :gutter="16">
      <el-col :span="8">
        <div class="col-head">
          <h4>字典类型</h4>
          <el-button type="primary" size="small" @click="openTypeCreate">新建类型</el-button>
        </div>
        <el-table v-loading="loading" :data="list" highlight-current-row @row-click="selectType">
          <el-table-column prop="dictName" label="名称" />
          <el-table-column prop="dictType" label="类型" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click.stop="openTypeEdit(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click.stop="removeType(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-col>
      <el-col :span="16">
        <div class="col-head">
          <h4>字典项（{{ activeType || '未选择' }}）</h4>
          <el-button type="primary" size="small" :disabled="!activeType" @click="openDataCreate">新建字典项</el-button>
        </div>
        <el-table :data="items" stripe>
          <el-table-column prop="dictLabel" label="标签" />
          <el-table-column prop="dictValue" label="值" />
          <el-table-column prop="sortOrder" label="排序" width="70" />
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                {{ row.status === 1 ? '正常' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" show-overflow-tooltip />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openDataEdit(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="removeData(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-col>
    </el-row>

    <!-- 类型编辑 -->
    <el-dialog v-model="showTypeEdit" :title="typeForm.id ? '编辑字典类型' : '新建字典类型'" width="480">
      <el-form :model="typeForm" label-width="90">
        <el-form-item label="名称" required>
          <el-input v-model="typeForm.dictName" placeholder="如：用户性别" />
        </el-form-item>
        <el-form-item label="类型 key" required>
          <el-input v-model="typeForm.dictType" placeholder="如：sys_user_sex" :disabled="!!typeForm.id" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="typeForm.status" :active-value="1" :inactive-value="0" active-text="正常" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="typeForm.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTypeEdit = false">取消</el-button>
        <el-button type="primary" @click="saveType">保存</el-button>
      </template>
    </el-dialog>

    <!-- 字典项编辑 -->
    <el-dialog v-model="showDataEdit" :title="dataForm.id ? '编辑字典项' : '新建字典项'" width="480">
      <el-form :model="dataForm" label-width="90">
        <el-form-item label="所属类型">
          <el-input :model-value="dataForm.dictType" disabled />
        </el-form-item>
        <el-form-item label="标签" required>
          <el-input v-model="dataForm.dictLabel" placeholder="如：男" />
        </el-form-item>
        <el-form-item label="值" required>
          <el-input v-model="dataForm.dictValue" placeholder="如：0" />
        </el-form-item>
        <el-form-item label="样式 class">
          <el-input v-model="dataForm.cssClass" placeholder="如：primary（可选）" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dataForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="dataForm.status" :active-value="1" :inactive-value="0" active-text="正常" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dataForm.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDataEdit = false">取消</el-button>
        <el-button type="primary" @click="saveData">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.col-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.col-head h4 { margin: 0; }
</style>
