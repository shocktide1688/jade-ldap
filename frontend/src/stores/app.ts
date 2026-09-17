import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'system'

/** 读取持久化里的初始主题（index.html 内联脚本用同一逻辑，防首帧闪烁） */
export function resolveStoredTheme(): ThemeMode {
  try {
    const raw = localStorage.getItem('app')
    if (raw) {
      const t = JSON.parse(raw)?.theme
      if (t === 'light' || t === 'dark' || t === 'system') return t
    }
  } catch { /* ignore */ }
  return 'system'
}

export function systemPrefersDark(): boolean {
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

export function applyThemeClass(mode: ThemeMode) {
  const dark = mode === 'dark' || (mode === 'system' && systemPrefersDark())
  document.documentElement.classList.toggle('dark', dark)
}

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const device = ref<'desktop' | 'mobile'>('desktop')
  const theme = ref<ThemeMode>(resolveStoredTheme())

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  /** 循环切换：light → dark → system → light */
  function cycleTheme(): ThemeMode {
    const next: ThemeMode = theme.value === 'light' ? 'dark' : theme.value === 'dark' ? 'system' : 'light'
    theme.value = next
    return next
  }

  /** 当前主题的中文名（与按钮图标一致） */
  function themeName(): string {
    return theme.value === 'light' ? '白天' : theme.value === 'dark' ? '黑夜' : '跟随系统'
  }

  function applyTheme() {
    applyThemeClass(theme.value)
  }

  // 本地持久化 + 跟随系统实时切换
  watch(theme, () => {
    localStorage.setItem('app', JSON.stringify({ theme: theme.value }))
    applyTheme()
  })

  const media = window.matchMedia('(prefers-color-scheme: dark)')
  media.addEventListener('change', () => {
    if (theme.value === 'system') applyTheme()
  })

  // 初始化（index.html 内联脚本已先行设类，这里兜底）
  applyTheme()

  return {
    sidebarCollapsed,
    device,
    theme,
    toggleSidebar,
    cycleTheme,
    themeName,
  }
})
