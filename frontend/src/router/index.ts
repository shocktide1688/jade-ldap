import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { setupGuards } from './guards'

// 静态导入：页面组件都很小（几 KB），合并进主包；也避免懒加载 chunk 在切页时的加载时序问题
import LoginPage from '@/pages/login/index.vue'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import DashboardPage from '@/pages/dashboard/index.vue'
import LdapPage from '@/pages/ldap/index.vue'
import UserPage from '@/pages/system/user.vue'
import RolePage from '@/pages/system/role.vue'
import MenuPage from '@/pages/system/menu.vue'
import DeptPage from '@/pages/system/dept.vue'
import DictPage from '@/pages/system/dict.vue'
import NoticePage from '@/pages/system/notice.vue'
import ConfigPage from '@/pages/system/config.vue'
import OperLogPage from '@/pages/monitor/operlog.vue'
import LoginLogPage from '@/pages/monitor/loginlog.vue'
import ProfilePage from '@/pages/profile/index.vue'
import DemoPlatformPage from '@/pages/demo/platform.vue'
import NotFoundPage from '@/pages/error/404.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: LoginPage,
    meta: { layout: 'blank', title: '登录' },
  },
  {
    path: '/',
    component: DefaultLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: DashboardPage,
        meta: { title: '首页', icon: 'House' },
      },
      {
        path: 'ldap',
        name: 'LdapDirectory',
        component: LdapPage,
        meta: { title: 'LDAP 目录', icon: 'Connection' },
      },
      // ===== 系统管理 =====
      {
        path: 'system/user',
        name: 'SystemUser',
        component: UserPage,
        meta: { title: '用户管理', icon: 'User' },
      },
      {
        path: 'system/role',
        name: 'SystemRole',
        component: RolePage,
        meta: { title: '角色管理', icon: 'UserFilled' },
      },
      {
        path: 'system/menu',
        name: 'SystemMenu',
        component: MenuPage,
        meta: { title: '菜单管理', icon: 'Menu' },
      },
      {
        path: 'system/dept',
        name: 'SystemDept',
        component: DeptPage,
        meta: { title: '部门管理', icon: 'OfficeBuilding' },
      },
      {
        path: 'system/dict',
        name: 'SystemDict',
        component: DictPage,
        meta: { title: '字典管理', icon: 'Reading' },
      },
      {
        path: 'system/notice',
        name: 'SystemNotice',
        component: NoticePage,
        meta: { title: '通知公告', icon: 'Bell' },
      },
      {
        path: 'system/config',
        name: 'SystemConfig',
        component: ConfigPage,
        meta: { title: '参数配置', icon: 'Setting' },
      },
      // ===== 系统监控 =====
      {
        path: 'monitor/operlog',
        name: 'MonitorOperLog',
        component: OperLogPage,
        meta: { title: '操作日志', icon: 'Document' },
      },
      {
        path: 'monitor/loginlog',
        name: 'MonitorLoginLog',
        component: LoginLogPage,
        meta: { title: '登录日志', icon: 'Key' },
      },
      // ===== 个人中心 =====
      {
        path: 'profile',
        name: 'Profile',
        component: ProfilePage,
        meta: { title: '个人中心', icon: 'UserFilled', hidden: true },
      },
      // ===== 平台演示 =====
      {
        path: 'demo/platform',
        name: 'DemoPlatform',
        component: DemoPlatformPage,
        meta: { title: '平台能力展示', icon: 'MagicStick' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: NotFoundPage,
    meta: { layout: 'blank', title: '404' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

setupGuards(router)

/** 收集所有已注册路由的完整路径（用于过滤后端菜单里没实现的） */
export const REGISTERED_PATHS = new Set<string>(['/dashboard', '/login'])
function collectPaths(rs: RouteRecordRaw[], prefix = '') {
  for (const r of rs) {
    const p = (prefix + '/' + (r.path || '')).replace('//', '/')
    if (p && p !== '/') REGISTERED_PATHS.add(p)
    if (r.children?.length) collectPaths(r.children, p)
  }
}
collectPaths(routes)

export default router
