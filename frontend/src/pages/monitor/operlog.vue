<script setup lang="ts">
import dayjs from 'dayjs'

const fmtTime = (t?: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '')
import { onMounted, ref } from 'vue'
import request from '@/utils/request'


defineOptions({ name: 'MonitorOperLog' })
const loading = ref(false)
const list = ref<any[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const username = ref('')

async function fetchData() {
  loading.value = true
  try {
    const res = await request({
      url: '/api/v1/log/oper/page',
      method: 'GET',
      params: { page: page.value, size: size.value, username: username.value || undefined },
    })
    list.value = res.data.data.records
    total.value = res.data.data.total
  } finally { loading.value = false }
}

onMounted(fetchData)
</script>

<template>
  <el-card>
    <div class="toolbar">
      <el-input v-model="username" placeholder="搜索用户" clearable style="width: 240px;" @keyup.enter="fetchData" />
      <el-button type="primary" @click="fetchData">搜索</el-button>
    </div>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="title" label="模块" width="120" />
      <el-table-column prop="method" label="方法" width="240" />
      <el-table-column prop="requestMethod" label="HTTP" width="80" />
      <el-table-column prop="requestUrl" label="URL" width="240" />
      <el-table-column prop="username" label="操作人" width="120" />
      <el-table-column prop="ip" label="IP" width="120" />
      <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
      <el-table-column prop="operTime" label="时间" width="150">
        <template #default="{ row }">{{ fmtTime(row.operTime) }}</template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
      :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next"
      style="margin-top: 16px; justify-content: flex-end;" @current-change="fetchData" @size-change="fetchData" />
  </el-card>
</template>
<style scoped>.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }</style>
