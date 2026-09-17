import axios, { type AxiosInstance, type InternalAxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

/**
 * 统一响应格式
 */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: number
}

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 15000,
})

// 会话过期统一入口：提示 + 跳登录页；并发失败只提示一次
let sessionExpiredNotified = false

function handleSessionExpired(config?: InternalAxiosRequestConfig): Promise<never> {
  const userStore = useUserStore()

  // 勾选了自动登录：静默重登并重放原请求（每个请求只重试一次）
  if (config && !(config as any).__authRetry && userStore.silentRelogin) {
    ;(config as any).__authRetry = true
    return userStore.silentRelogin().then((ok) => {
      if (ok) return service.request(config)
      return handleSessionExpired(undefined)
    })
  }

  if (!sessionExpiredNotified) {
    sessionExpiredNotified = true
    ElMessage.warning('登录已过期，请重新登录')
    userStore.logout()
    const current = router.currentRoute.value
    if (current.path !== '/login') {
      router.push({ path: '/login', query: { redirect: current.fullPath } })
    }
    setTimeout(() => { sessionExpiredNotified = false }, 2000)
  }
  return Promise.reject(new Error('登录已过期，请重新登录'))
}

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const userStore = useUserStore()
    if (userStore.accessToken) {
      config.headers.Authorization = `Bearer ${userStore.accessToken}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data

    // 兼容非包装响应（如文件下载）
    if (typeof res !== 'object' || res === null || !('code' in res)) {
      return response
    }

    if (res.code === 0) {
      return response
    }

    const isLoginRequest = response.config.url?.includes('/api/v1/auth/login') === true

    // 登录接口的业务失败（1001 凭证错误 / 1102 验证码错误等）由登录页按 code 处理
    if (isLoginRequest) {
      const err: any = new Error(res.message || '登录失败')
      err.code = res.code
      return Promise.reject(err)
    }

    // 其他请求的 1001/1002 才表示已有会话失效。
    if (res.code === 1001 || res.code === 1002) {
      return handleSessionExpired(response.config)
    }

    // 1003 无权限
    if (res.code === 1003) {
      ElMessage.error('您没有权限执行此操作')
      return Promise.reject(new Error(res.message))
    }

    // 业务错误
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message))
  },
  (error) => {
    // HTTP 层 401：token 过期/非法（登录接口本身的 401 是凭证错误，走普通报错）
    const isLoginRequest = error.config?.url?.includes('/api/v1/auth/login') === true
    if (error.response?.status === 401 && !isLoginRequest) {
      return handleSessionExpired(error.config)
    }
    const msg = error.response?.data?.message || error.message || '网络异常'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default service
