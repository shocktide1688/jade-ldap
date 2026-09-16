import type { Router } from 'vue-router'
import { useUserStore } from '@/stores/user'

const WHITE_LIST = ['/login', '/404', '/403']

export function setupGuards(router: Router) {
  // vue-router 5 推荐返回值式守卫（替代 next() 回调）
  router.beforeEach((to) => {
    const userStore = useUserStore()

    if (to.meta.title) {
      document.title = `${to.meta.title} - Jade Platform`
    }

    if (WHITE_LIST.includes(to.path)) {
      return true
    }

    if (!userStore.isLoggedIn) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }

    return true
  })
}
