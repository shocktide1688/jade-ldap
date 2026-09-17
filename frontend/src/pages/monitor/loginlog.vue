<script setup lang="ts">
import dayjs from 'dayjs'

const fmtTime = (t?: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '')
import { onMounted, ref } from 'vue'
import request from '@/utils/request'


defineOptions({ name: 'MonitorLoginLog' })
const loading = ref(false)
const list = ref<any[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)

async function fetchData() {
  loading.value = true
  try {
    const res = await request({ url: '/api/v1/log/login/page', method: 'GET', params: { page: page.value, size: size.value } })
    list.value = res.data.data.records
    total.value = res.data.data.total
  } finally { loading.value = false }
}

onMounted(fetchData)
</script>

<template>
  <el-card>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="username" label="用户" width="120" />
      <el-table-column prop="ip" label="IP" width="140" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="msg" label="消息" />
      <el-table-column prop="loginTime" label="登录时间" width="150">
        <template #default="{ row }">{{ fmtTime(row.loginTime) }}</template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
      :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next"
      style="margin-top: 16px; justify-content: flex-end;" @current-change="fetchData" @size-change="fetchData" />
  </el-card>
</template>
