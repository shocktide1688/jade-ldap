<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

defineOptions({ name: 'SystemConfig' })

const loading = ref(false)
const list = ref<any[]>([])
const keyword = ref('')

const showEdit = ref(false)
const editing = reactive<any>({
  id: null, configName: '', configKey: '', configValue: '', configType: 'system', remark: '',
})

async function fetchData() {
  loading.value = true
  try {
    const res = await request({ url: '/api/v1/configs/all', method: 'GET' })
    const rows: any[] = res.data.data || []
    const kw = keyword.value.trim().toLowerCase()
    list.value = kw
      ? rows.filter((r) =>
          [r.configName, r.configKey, r.configValue].some((v: string) =>
            (v || '').toLowerCase().includes(kw)))
      : rows
  } finally { loading.value = false }
}

function openCreate() {
  Object.assign(editing, { id: null, configName: '', configKey: '', configValue: '', configType: 'system', remark: '' })
  showEdit.value = true
}

function openEdit(row: any) {
  Object.assign(editing, row)
  showEdit.value = true
}

async function save() {
  if (!editing.configName) return ElMessage.warning('参数名必填')
  if (!editing.configKey) return ElMessage.warning('参数键必填')
  try {
    if (editing.id) {
      await request({ url: `/api/v1/configs/${editing.id}`, method: 'PUT', data: editing })
      ElMessage.success('更新成功')
    } else {
      await request({ url: '/api/v1/configs', method: 'POST', data: editing })
      ElMessage.success('创建成功')
    }
    showEdit.value = false
    fetchData()
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '保存失败') }
}

async function remove(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除配置 ${row.configName}？`, '提示', { type: 'warning' })
    await request({ url: `/api/v1/configs/${row.id}`, method: 'DELETE' })
    ElMessage.success('删除成功')
    fetchData()
  } catch (e: any) { if (e !== 'cancel') ElMessage.error(e?.response?.data?.message || '删除失败') }
}

onMounted(fetchData)
</script>

<template>
  <div class="config-page">
    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索参数名/键/值"
          clearable
          style="width: 240px"
          @input="fetchData"
        />
        <el-button type="primary" @click="openCreate">+ 新建</el-button>
      </div>

      <el-table v-loading="loading" :data="list" size="small">
        <el-table-column prop="configName" label="参数名" width="180" show-overflow-tooltip />
        <el-table-column prop="configKey" label="参数键" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono key-text">{{ row.configKey }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="configValue" label="参数值" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono value-text">{{ row.configValue }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="configType" label="类型" width="90" />
        <el-table-column prop="isBuiltin" label="内置" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isBuiltin === 1 ? 'warning' : 'info'" size="small">
              {{ row.isBuiltin === 1 ? '内置' : '自定义' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :disabled="row.isBuiltin === 1" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button link type="danger" size="small" :disabled="row.isBuiltin === 1" @click="remove(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showEdit" :title="editing.id ? '编辑参数' : '新建参数'" width="520px">
      <el-form :model="editing" label-width="90px">
        <el-form-item label="参数名">
          <el-input v-model="editing.configName" placeholder="如：验证码开关" />
        </el-form-item>
        <el-form-item label="参数键">
          <el-input v-model="editing.configKey" :disabled="!!editing.id" placeholder="如：sys.captcha.enabled" />
        </el-form-item>
        <el-form-item label="参数值">
          <el-input v-model="editing.configValue" placeholder="如：true / 3" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editing.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEdit = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.config-page { display: flex; flex-direction: column; gap: 14px; }

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.mono { font-family: var(--jade-mono); font-size: 12px; }
.key-text { color: var(--jade-primary-light); }
.value-text { color: var(--jade-text); }
</style>
