<script setup lang="ts">
import { onMounted, ref } from 'vue'
import request from '@/utils/request'


defineOptions({ name: 'SystemDict' })
const loading = ref(false)
const list = ref<any[]>([])
const items = ref<any[]>([])
const activeType = ref<string>('')

async function fetchTypes() {
  loading.value = true
  try {
    const res = await request({ url: '/api/v1/dict/types', method: 'GET' })
    list.value = res.data.data
    if (list.value.length > 0) {
      activeType.value = list.value[0].dictType
      await fetchItems()
    }
  } finally { loading.value = false }
}

async function fetchItems() {
  const res = await request({ url: `/api/v1/dict/data?type=${activeType.value}`, method: 'GET' })
  items.value = res.data.data
}

onMounted(fetchTypes)
</script>

<template>
  <el-card>
    <el-row :gutter="16">
      <el-col :span="8">
        <h4>字典类型</h4>
        <el-table v-loading="loading" :data="list" highlight-current-row @row-click="(r) => { activeType = r.dictType; fetchItems() }">
          <el-table-column prop="dictName" label="名称" />
          <el-table-column prop="dictType" label="类型" />
        </el-table>
      </el-col>
      <el-col :span="16">
        <h4>字典项（{{ activeType }}）</h4>
        <el-table :data="items" stripe>
          <el-table-column prop="dictLabel" label="标签" />
          <el-table-column prop="dictValue" label="值" />
          <el-table-column prop="sortOrder" label="排序" width="80" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                {{ row.status === 1 ? '正常' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-col>
    </el-row>
  </el-card>
</template>
