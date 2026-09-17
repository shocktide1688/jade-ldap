import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { RouteLocationNormalized } from 'vue-router'

export interface TabItem {
  path: string
  title: string
  /** 路由 name，同时约定为页面组件 defineOptions 的 name（keep-alive include 匹配用） */
  name?: string
  /** 固定 tab（首页），不可关闭 */
  affix?: boolean
}

const AFFIX_TAB: TabItem = { path: '/dashboard', title: '首页', name: 'Dashboard', affix: true }

export const useTabsStore = defineStore('tabs', () => {
  const tabs = ref<TabItem[]>([{ ...AFFIX_TAB }])
  const activePath = ref('')

  /** 打开的 tab 对应的组件名集合，供 keep-alive include */
  const cachedNames = computed(() =>
    Array.from(new Set(tabs.value.map((t) => t.name).filter((n): n is string => !!n))),
  )

  /** 路由变化时调用：非 blank 布局页都登记成 tab */
  function openTab(route: RouteLocationNormalized) {
    if (route.meta?.layout === 'blank') return
    activePath.value = route.path
    const existed = tabs.value.find((t) => t.path === route.path)
    if (existed) {
      const title = route.meta?.title as string | undefined
      if (title && existed.title !== title) existed.title = title
      return
    }
    tabs.value.push({
      path: route.path,
      title: (route.meta?.title as string) || route.path,
      name: route.name as string | undefined,
    })
  }

  /** 关闭 tab；若关闭的是激活 tab，返回应跳转的路径，否则返回 null */
  function closeTab(path: string): string | null {
    const idx = tabs.value.findIndex((t) => t.path === path)
    if (idx < 0 || tabs.value[idx].affix) return null
    tabs.value.splice(idx, 1)
    if (activePath.value !== path) return null
    const next = tabs.value[idx] ?? tabs.value[idx - 1]
    activePath.value = next.path
    return next.path
  }

  /** 关闭其他，返回应激活的路径 */
  function closeOthers(path: string): string {
    tabs.value = tabs.value.filter((t) => t.affix || t.path === path)
    activePath.value = path
    return path
  }

  /** 关闭全部，返回应激活的路径（回到首页） */
  function closeAll(): string {
    tabs.value = tabs.value.filter((t) => t.affix)
    activePath.value = AFFIX_TAB.path
    return AFFIX_TAB.path
  }

  return { tabs, activePath, cachedNames, openTab, closeTab, closeOthers, closeAll }
})
