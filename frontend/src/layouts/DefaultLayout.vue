<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { REGISTERED_PATHS } from '@/router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { useTabsStore, type TabItem } from '@/stores/tabs'
import request from '@/utils/request'
import {
  House, UserFilled, User, Menu, OfficeBuilding, Reading,
  Document, Key, SwitchButton, Bell, Setting,
  Connection,
} from '@element-plus/icons-vue'
import { MorphIcon } from 'morphicons/vue'
import { PanelLeftClose, PanelLeftOpen, RefreshCw, Check, X, House as HouseLucide, RotateCw, Sun, Moon, Monitor } from 'lucide'

const userStore = useUserStore()
const appStore = useAppStore()
const tabsStore = useTabsStore()
const router = useRouter()
const route = useRoute()
const menus = ref<any[]>([])

// 路径 -> 图标 的映射
const ICON_MAP: Record<string, any> = {
  首页: House, 用户管理: User, 角色管理: UserFilled, 菜单管理: Menu,
  部门管理: OfficeBuilding, 字典管理: Reading, 操作日志: Document, 登录日志: Key,
  通知公告: Bell, 参数配置: Setting,
  'LDAP 目录': Connection,
}

async function loadMenus() {
  try {
    const res = await request({ url: '/api/v1/menus/router', method: 'GET' })
    // 1. 补全父路径 (path 是相对的, Element Plus router 模式需要完整路径)
    // 2. 过滤掉前端没实现路由的菜单 (不然点过去 404)
    const raw = (res.data.data || []) as any[]
    const withPaths = buildFullPaths(raw, '')
    menus.value = filterUnregistered(withPaths)
  } catch (e) {
    console.warn('菜单加载失败，用 fallback', e)
    menus.value = []
  }
}

function buildFullPaths(items: any[], parentPath: string): any[] {
  return items.map((it) => {
    const fullPath = parentPath ? `${parentPath}/${it.path}` : `/${it.path}`
    const children = it.children ? buildFullPaths(it.children, fullPath) : undefined
    return { ...it, path: fullPath, children }
  })
}

/** 递归剔除没注册路由的菜单 (含子菜单都过滤掉) */
function filterUnregistered(items: any[]): any[] {
  return items
    .map((it) => ({
      ...it,
      children: it.children ? filterUnregistered(it.children) : undefined,
    }))
    .filter((it) => {
      // 有子菜单的父菜单: 至少有一个子菜单留下来了
      if (it.children && it.children.length > 0) return true
      // 叶子菜单: 路径必须在路由表里
      return REGISTERED_PATHS.has(it.path)
    })
}

// 主题模式图标：白天 Sun / 黑夜 Moon / 跟随系统 Monitor
const THEME_ICONS = { light: Sun, dark: Moon, system: Monitor } as const
const themeIcon = computed(() => THEME_ICONS[appStore.theme])

// 窄屏自动收起侧栏（图标栏模式），保证内容区可用
function syncSidebarToViewport() {
  appStore.sidebarCollapsed = window.innerWidth < 992
}

// 刷新按钮：点击后 RefreshCw → Check 弹簧变形反馈
const refreshed = ref(false)
async function refreshMenus() {
  await loadMenus()
  refreshed.value = true
  setTimeout(() => { refreshed.value = false }, 1400)
}

// 顶栏实时时钟
const clock = ref('')
let clockTimer: ReturnType<typeof setInterval> | undefined
function tickClock() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  clock.value = `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

const activeMenu = computed(() => router.currentRoute.value.path)

// 面包屑：从当前路由的 matched 数组构造
interface Crumb { name: string; path: string }
const breadcrumbs = computed<Crumb[]>(() => {
  const crumbs: Crumb[] = []
  for (const m of route.matched) {
    const title = (m.meta?.title as string)
    if (title && !m.meta?.hidden) {
      crumbs.push({ name: title, path: m.path })
    }
  }
  return crumbs
})

async function handleCommand(cmd: string) {
  if (cmd === 'logout') {
    await userStore.logout()
    router.push('/login')
  } else if (cmd === 'profile') {
    router.push('/profile')
  }
}

/* ================= 多标签页 ================= */

// 路由变化 → 登记进 tab 栏
watch(
  () => route.path,
  () => tabsStore.openTab(route),
)

// keep-alive 缓存名单（刷新当前页时临时剔除，触发缓存逐出）
const refreshSkip = ref(new Set<string>())
const includeNames = computed(() =>
  tabsStore.cachedNames.filter((n) => !refreshSkip.value.has(n)),
)
const showView = ref(true)
async function refreshCurrent() {
  if (!showView.value) return
  const name = route.name as string | undefined
  if (name) {
    refreshSkip.value.add(name)
    showView.value = false
    await nextTick()
    refreshSkip.value.delete(name)
  }
  showView.value = true
}

function go(path: string) {
  if (path !== route.path) router.push(path)
}

function close(path: string) {
  const next = tabsStore.closeTab(path)
  if (next) router.push(next)
}

// tab 栏横向滚动（滚轮转横向）
const tabScrollRef = ref<HTMLElement | null>(null)
function onTabWheel(e: WheelEvent) {
  if (tabScrollRef.value) tabScrollRef.value.scrollLeft += e.deltaY
}

// 激活 tab 自动滚进可视区
watch(
  () => tabsStore.activePath,
  async () => {
    await nextTick()
    tabScrollRef.value
      ?.querySelector('.tab-chip.active')
      ?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
  },
)

// 右键菜单
const ctx = ref({ show: false, x: 0, y: 0, tab: null as TabItem | null })
function openCtx(e: MouseEvent, tab: TabItem) {
  ctx.value = {
    show: true,
    x: Math.min(e.clientX, window.innerWidth - 150),
    y: Math.min(e.clientY, window.innerHeight - 170),
    tab,
  }
}
function ctxRefresh() { closeCtx(); refreshCurrent() }
function ctxClose() { if (ctx.value.tab) close(ctx.value.tab.path); closeCtx() }
function ctxCloseOthers() { if (ctx.value.tab) go(tabsStore.closeOthers(ctx.value.tab.path)); closeCtx() }
function ctxCloseAll() { go(tabsStore.closeAll()); closeCtx() }
function onGlobalClick() { closeCtx() }
function closeCtx() { ctx.value.show = false }

// 每个 tab 独立记忆滚动位置
const scrollRef = ref<HTMLElement | null>(null)
const scrollMap = new Map<string, number>()
watch(
  () => route.path,
  async (_n, old) => {
    if (old && scrollRef.value) scrollMap.set(old, scrollRef.value.scrollTop)
    await nextTick()
    if (scrollRef.value) scrollRef.value.scrollTop = scrollMap.get(route.path) ?? 0
  },
)

onMounted(() => {
  syncSidebarToViewport()
  loadMenus()
  tabsStore.openTab(route)
  tickClock()
  clockTimer = setInterval(tickClock, 1000)
  window.addEventListener('click', onGlobalClick)
  window.addEventListener('resize', syncSidebarToViewport)
})
onUnmounted(() => {
  clearInterval(clockTimer)
  window.removeEventListener('click', onGlobalClick)
  window.removeEventListener('resize', syncSidebarToViewport)
})
</script>

<template>
  <el-container class="layout">
    <el-aside :width="appStore.sidebarCollapsed ? '64px' : '232px'" class="aside">
      <div class="aside-glow" aria-hidden="true" />
      <div class="logo" @click="router.push('/dashboard')">
        <span class="logo-mark" aria-hidden="true">
          <i class="pulse-dot" />
        </span>
        <span v-if="!appStore.sidebarCollapsed" class="logo-text">
          Jade<span class="logo-accent">LDAP</span>
        </span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="appStore.sidebarCollapsed"
        :collapse-transition="false"
        router
      >
        <template v-for="m in menus" :key="m.path || m.name">
          <el-sub-menu v-if="m.children && m.children.length" :index="m.path || m.name">
            <template #title>
              <el-icon><component :is="ICON_MAP[m.name] || Menu" /></el-icon>
              <span>{{ m.name }}</span>
            </template>
            <el-menu-item v-for="c in m.children" :key="c.path" :index="c.path">
              <el-icon><component :is="ICON_MAP[c.name] || Menu" /></el-icon>
              <template #title>{{ c.name }}</template>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else-if="m.path" :index="m.path">
            <el-icon><component :is="ICON_MAP[m.name] || Menu" /></el-icon>
            <template #title>{{ m.name }}</template>
          </el-menu-item>
        </template>
      </el-menu>
      <div v-if="!appStore.sidebarCollapsed" class="aside-footer">
        <span class="pulse-dot" />
        <span class="aside-footer-text mono">SYS · ONLINE</span>
      </div>
    </el-aside>

    <el-container class="body-container">
      <el-header class="header">
        <div class="header-left">
          <el-button text class="icon-btn" @click="appStore.toggleSidebar">
            <MorphIcon
              :icon="appStore.sidebarCollapsed ? PanelLeftOpen : PanelLeftClose"
              :size="18"
              spring="snappy"
              reduced-motion="user"
              label="折叠侧边栏"
            />
          </el-button>
          <el-breadcrumb v-if="breadcrumbs.length > 1" separator="/" class="breadcrumb">
            <el-breadcrumb-item v-for="(c, i) in breadcrumbs" :key="c.path">
              <router-link v-if="i < breadcrumbs.length - 1" :to="c.path">{{ c.name }}</router-link>
              <span v-else class="crumb-current">{{ c.name }}</span>
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-tooltip :content="`主题：${appStore.themeName()}（点击切换）`" placement="bottom">
            <el-button text class="icon-btn" @click="appStore.cycleTheme">
              <MorphIcon
                :icon="themeIcon"
                :size="17"
                spring="snappy"
                reduced-motion="user"
                label="切换主题"
              />
            </el-button>
          </el-tooltip>
          <span class="header-clock mono">{{ clock }}</span>
          <el-tooltip content="刷新菜单" placement="bottom">
            <el-button text class="icon-btn" @click="refreshMenus">
              <MorphIcon
                :icon="refreshed ? Check : RefreshCw"
                :size="17"
                spring="bouncy"
                reduced-motion="user"
                label="刷新菜单"
              />
            </el-button>
          </el-tooltip>
          <el-dropdown @command="handleCommand" trigger="click">
            <span class="user-name">
              <span class="avatar" aria-hidden="true">{{ (userStore.username || '?').slice(0, 1).toUpperCase() }}</span>
              {{ userStore.username || '未登录' }}
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon> 个人中心
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 多标签栏 -->
      <div class="tab-bar">
        <div ref="tabScrollRef" class="tab-scroll" @wheel.prevent="onTabWheel">
          <div
            v-for="t in tabsStore.tabs"
            :key="t.path"
            class="tab-chip"
            role="tab"
            :class="{ active: tabsStore.activePath === t.path }"
            :aria-selected="tabsStore.activePath === t.path"
            :tabindex="0"
            @click="go(t.path)"
            @keydown.enter.prevent="go(t.path)"
            @keydown.space.prevent="go(t.path)"
            @click.middle="close(t.path)"
            @contextmenu.prevent="openCtx($event, t)"
          >
            <MorphIcon v-if="t.affix" :icon="HouseLucide" :size="12" :stroke-width="2.2" />
            <span class="tab-title">{{ t.title }}</span>
            <button
              v-if="!t.affix"
              class="tab-close"
              :aria-label="`关闭 ${t.title}`"
              @click.stop="close(t.path)"
            >
              <MorphIcon :icon="X" :size="10" :stroke-width="2.5" />
            </button>
          </div>
        </div>
        <el-tooltip content="刷新当前页" placement="bottom">
          <el-button text class="icon-btn tab-refresh" @click="refreshCurrent">
            <MorphIcon :icon="RotateCw" :size="15" label="刷新当前页" />
          </el-button>
        </el-tooltip>

        <!-- 右键菜单 -->
        <teleport to="body">
          <div
            v-if="ctx.show"
            class="ctx-menu"
            :style="{ left: ctx.x + 'px', top: ctx.y + 'px' }"
          >
            <button class="ctx-item" :disabled="ctx.tab?.path !== tabsStore.activePath" @click="ctxRefresh">刷新页面</button>
            <button class="ctx-item" :disabled="!!ctx.tab?.affix" @click="ctxClose">关闭标签</button>
            <button class="ctx-item" @click="ctxCloseOthers">关闭其他</button>
            <button class="ctx-item" @click="ctxCloseAll">关闭全部</button>
          </div>
        </teleport>
      </div>

      <el-main class="main">
        <div class="main-bg grid-bg" aria-hidden="true">
          <div class="aurora"><i /><i /><i /></div>
        </div>
        <div ref="scrollRef" class="main-scroll">
          <!-- 不加 <transition>：Transition + KeepAlive + RouterView 组合在 vue-router 5 下
               会触发 "parentComponent.ctx.deactivate is not a function"（Vue core 已知问题），
               导致切页白屏。多标签场景下瞬时切换反而更顺手。 -->
          <router-view v-slot="{ Component, route: r }">
            <keep-alive :include="includeNames">
              <component :is="Component" :key="r.path" v-if="showView" />
            </keep-alive>
          </router-view>
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout { height: 100vh; }

/* ---------------- 侧边栏 ---------------- */
.aside {
  position: relative;
  background: linear-gradient(180deg, var(--jade-aside-grad-a) 0%, var(--jade-aside-grad-b) 100%);
  border-right: 1px solid var(--jade-aside-border);
  transition: width 0.25s cubic-bezier(0.22, 1, 0.36, 1);
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
}

/* 侧边栏顶部一抹霓虹 */
.aside-glow {
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 220px;
  background: radial-gradient(ellipse 70% 100% at 50% 0%, var(--jade-aside-glow), transparent 70%);
  pointer-events: none;
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  cursor: pointer;
  position: relative;
}

.logo-mark {
  width: 34px; height: 34px;
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(0, 224, 138, 0.2), rgba(34, 211, 238, 0.14));
  border: 1px solid rgba(0, 224, 138, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 0 18px var(--jade-glow-soft), inset 0 0 12px rgba(0, 224, 138, 0.1);
}

.logo-text {
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 1.5px;
  color: var(--jade-aside-text-strong, var(--jade-text));
  white-space: nowrap;
}

.logo-accent {
  background: linear-gradient(120deg, var(--jade-primary), var(--jade-cyan));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.aside :deep(.el-menu) {
  border-right: none;
  padding: 6px 10px;
  flex: 1;
  --el-menu-text-color: var(--jade-aside-text);
  --el-menu-hover-bg-color: var(--jade-aside-hover-bg);
  --el-menu-active-color: var(--jade-aside-active);
  --el-menu-bg-color: transparent;
}

/* 收起态：去掉水平 padding，图标与 logo 圆标共用同一条中线 */
.aside :deep(.el-menu--collapse) {
  padding: 6px 0;
}

.aside :deep(.el-menu-item),
.aside :deep(.el-sub-menu__title) {
  border-radius: 10px;
  margin: 3px 0;
  height: 44px;
  line-height: 44px;
  transition: background 0.2s ease, color 0.2s ease;
}

.aside :deep(.el-menu-item:hover),
.aside :deep(.el-sub-menu__title:hover) {
  background: var(--jade-aside-hover-bg) !important;
  color: var(--jade-text) !important;
}

/* 激活态：霓虹指示条 + 微光背景 */
.aside :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(0, 224, 138, 0.16), rgba(34, 211, 238, 0.06) 70%, transparent) !important;
  position: relative;
  color: var(--jade-primary) !important;
}

.aside :deep(.el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 0; top: 22%; bottom: 22%;
  width: 3px;
  border-radius: 2px;
  background: linear-gradient(180deg, var(--jade-primary), var(--jade-cyan));
  box-shadow: 0 0 10px var(--jade-glow);
}

.aside :deep(.el-sub-menu .el-menu) { background: transparent; }

.aside-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px 0 16px;
  border-top: 1px solid var(--jade-aside-border);
}

.aside-footer-text {
  font-size: 11px;
  letter-spacing: 2px;
  color: var(--jade-aside-text-dim);
}

/* ---------------- 顶栏 ---------------- */
.header {
  position: relative;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  flex-shrink: 0;
  padding: 0 16px;
  background: var(--jade-surface);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid var(--jade-border);
}

.header-left { display: flex; align-items: center; gap: 12px; min-width: 0; }

.icon-btn { color: var(--jade-text-secondary); }
.icon-btn:hover { color: var(--jade-primary) !important; background: rgba(0, 224, 138, 0.08) !important; }

.header-clock {
  font-size: 13px;
  color: var(--jade-primary);
  letter-spacing: 1px;
  padding: 4px 10px;
  border: 1px solid rgba(0, 224, 138, 0.25);
  border-radius: 8px;
  background: rgba(0, 224, 138, 0.06);
}

.header-right { display: flex; align-items: center; gap: 10px; }

.user-name {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--jade-text);
  padding: 4px 10px 4px 4px;
  border-radius: 10px;
  border: 1px solid transparent;
  transition: border-color 0.2s ease, background 0.2s ease;
  outline: none;
}

.user-name:hover,
.user-name:focus-visible {
  background: rgba(148, 163, 184, 0.08);
  border-color: var(--jade-border);
}

.avatar {
  width: 30px; height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  color: #04120b;
  background: linear-gradient(135deg, #00e08a, #22d3ee);
  box-shadow: 0 0 12px var(--jade-glow-soft);
}

/* ---------------- 多标签栏 ---------------- */
.tab-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  flex-shrink: 0; /* 内容高的页面（如菜单管理）不得挤压 tab 栏 */
  padding: 0 12px 0 10px;
  background: var(--jade-surface);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--jade-border);
  position: relative;
  z-index: 4;
}

.tab-scroll {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  height: 100%;
  overflow-x: auto;
  scrollbar-width: none;
}

.tab-scroll::-webkit-scrollbar { display: none; }

.tab-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 26px;
  padding: 0 9px;
  border-radius: 8px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--jade-text-dim);
  font-size: 12.5px;
  white-space: nowrap;
  flex-shrink: 0;
  cursor: pointer;
  user-select: none;
  transition: color 0.2s ease, background 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.tab-chip:hover {
  color: var(--jade-text);
  background: rgba(148, 163, 184, 0.08);
}

.tab-chip:focus-visible {
  outline: 2px solid var(--jade-primary);
  outline-offset: 1px;
}

.tab-chip.active {
  color: var(--jade-primary);
  font-weight: 600;
  border-color: rgba(0, 224, 138, 0.3);
  background: linear-gradient(90deg, rgba(0, 224, 138, 0.14), rgba(34, 211, 238, 0.06));
  box-shadow: 0 0 12px rgba(0, 224, 138, 0.12), inset 0 0 8px rgba(0, 224, 138, 0.05);
}

.tab-title { line-height: 1; }

.tab-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  padding: 0;
  border: none;
  border-radius: 5px;
  background: transparent;
  color: inherit;
  opacity: 0.55;
  cursor: pointer;
  transition: opacity 0.2s ease, background 0.2s ease, color 0.2s ease;
}

.tab-close:hover {
  opacity: 1;
  background: rgba(248, 113, 113, 0.18);
  color: #f87171;
}

.tab-close:focus-visible {
  outline: 2px solid var(--jade-primary);
  outline-offset: 1px;
  opacity: 1;
}

.tab-refresh { height: 26px; }

/* 右键菜单（teleport 到 body，scope 属性仍随模板带上） */
.ctx-menu {
  position: fixed;
  z-index: 3000;
  min-width: 128px;
  padding: 5px;
  background: var(--jade-card-solid);
  border: 1px solid var(--jade-border);
  border-radius: 10px;
  box-shadow: var(--jade-shadow-panel);
}

.ctx-item {
  display: block;
  width: 100%;
  padding: 7px 10px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--jade-text);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.ctx-item:hover:not(:disabled) {
  background: rgba(0, 224, 138, 0.1);
  color: var(--jade-primary-light);
}

.ctx-item:disabled {
  color: var(--jade-text-dim);
  opacity: 0.5;
  cursor: not-allowed;
}

/* ---------------- 主区域 ---------------- */
.body-container { position: relative; min-width: 0; }

.main {
  position: relative;
  padding: 0;
  overflow: hidden;
}

.main-bg {
  position: absolute;
  inset: 0;
  overflow: hidden; /* 裁剪极光光斑，避免把页面撑出横向滚动 */
  pointer-events: none;
  /* 网格向下淡出 */
  -webkit-mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.9), transparent 60%);
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.9), transparent 60%);
}

.main-bg .aurora { opacity: 0.55; }

.main-scroll {
  position: relative;
  z-index: 1;
  height: 100%;
  overflow-y: auto;
  padding: 20px;
}

.breadcrumb { font-size: 13px; white-space: nowrap; }
.crumb-current { color: var(--jade-text); font-weight: 500; }

/* 小屏：隐藏时钟与面包屑（标签栏已承担定位），收紧留白 */
@media (max-width: 639px) {
  .header-clock { display: none; }
  .breadcrumb { display: none; }
  .main-scroll { padding: 12px; }
  .tab-bar { padding: 0 8px; }
}
</style>
