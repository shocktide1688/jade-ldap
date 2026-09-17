import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  getToken, setToken, removeToken,
  getAutoLogin, setAutoLogin, clearAutoLogin,
} from '@/utils/auth'
import request from '@/utils/request'

export interface UserInfo {
  id: number
  username: string
  nickname?: string
  email?: string
}

export interface LoginPayload {
  username: string
  password: string
}

export interface LoginResult {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: UserInfo
}

export const useUserStore = defineStore(
  'user',
  () => {
    const accessToken = ref<string>(getToken() || '')
    const userInfo = ref<UserInfo | null>(null)

    const isLoggedIn = computed(() => !!accessToken.value)
    const username = computed(() => userInfo.value?.username || '')

    async function login(payload: LoginPayload, options?: { auto?: boolean }) {
      const res = await request({
        url: '/api/v1/auth/login',
        method: 'POST',
        data: payload,
      })
      const data: LoginResult = res.data.data
      accessToken.value = data.accessToken
      setToken(data.accessToken)
      userInfo.value = data.user
      if (options?.auto) {
        setAutoLogin(payload)
      } else {
        clearAutoLogin()
      }
      return data
    }

    /** 会话过期时用保存的凭证静默重登；失败则清掉凭证返回 false */
    async function silentRelogin(): Promise<boolean> {
      const cred = getAutoLogin()
      if (!cred) return false
      try {
        await login(cred, { auto: true })
        return true
      } catch {
        clearAutoLogin()
        return false
      }
    }

    function logout() {
      accessToken.value = ''
      userInfo.value = null
      removeToken()
      // 主动退出 = 不再自动登录
      clearAutoLogin()
    }

    function setUser(u: any) {
      userInfo.value = u
    }

    return {
      accessToken,
      userInfo,
      isLoggedIn,
      username,
      login,
      silentRelogin,
      logout,
      setUser,
    }
  },
  {
    persist: {
      pick: ['accessToken', 'userInfo'],
    },
  }
)
