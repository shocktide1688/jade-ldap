<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'
import { MorphIcon } from 'morphicons/vue'
import {
  Users, UserCheck, KeyRound, ShieldCheck, Building2, Network,
  Boxes, Layers, FolderKanban, GitBranch, ScrollText, FileClock,
  LogIn, ScanFace, Megaphone, BellRing,
  Sparkles, Bot, BrainCircuit,
} from 'lucide'


defineOptions({ name: 'Dashboard' })
const userStore = useUserStore()
const stats = ref({
  users: 0, roles: 0, depts: 0, tenants: 0, patients: 0, projects: 0,
  operLogs: 0, loginLogs: 0, notices: 0,
})
const recentNotices = ref<any[]>([])
const recentOperLogs = ref<any[]>([])

async function load() {
  try {
    const [users, roles, depts, tenants, projects, operLogs, loginLogs, notices] = await Promise.all([
      request({ url: '/api/v1/users/page', method: 'GET', params: { page: 1, size: 1 } }),
      request({ url: '/api/v1/roles/all', method: 'GET' }),
      request({ url: '/api/v1/depts/all', method: 'GET' }),
      request({ url: '/api/v1/tenants', method: 'GET' }),
      request({ url: '/api/v1/projects', method: 'GET' }),
      request({ url: '/api/v1/log/oper/page', method: 'GET', params: { page: 1, size: 1 } }),
      request({ url: '/api/v1/log/login/page', method: 'GET', params: { page: 1, size: 1 } }),
      request({ url: '/api/v1/notices/latest', method: 'GET' }),
    ])
    stats.value = {
      users: users.data.data.total,
      roles: (roles.data.data || []).length,
      depts: (depts.data.data || []).length,
      tenants: (tenants.data.data || []).length,
      patients: 0, // 没 list 接口, 暂用 0
      projects: (projects.data.data || []).length,
      operLogs: operLogs.data.data.total,
      loginLogs: loginLogs.data.data.total,
      notices: (notices.data.data || []).length,
    }
    recentNotices.value = notices.data.data || []
    // 操作日志最新 5 条
    const oper = await request({ url: '/api/v1/log/oper/page', method: 'GET', params: { page: 1, size: 5 } })
    recentOperLogs.value = oper.data.data.records || []
  } catch (e) { console.error('Dashboard load failed', e) }
}

// ---------- 指标卡配置：icon ↔ hoverIcon 由 morphicons 弹簧变形 ----------
const cards = computed(() => [
  { key: 'users', label: '用户', value: stats.value.users, icon: Users, hoverIcon: UserCheck, accent: '#00e08a' },
  { key: 'roles', label: '角色', value: stats.value.roles, icon: KeyRound, hoverIcon: ShieldCheck, accent: '#22d3ee' },
  { key: 'depts', label: '部门', value: stats.value.depts, icon: Building2, hoverIcon: Network, accent: '#8b5cf6' },
  { key: 'tenants', label: '租户', value: stats.value.tenants, icon: Boxes, hoverIcon: Layers, accent: '#f59e0b' },
  { key: 'projects', label: '项目', value: stats.value.projects, icon: FolderKanban, hoverIcon: GitBranch, accent: '#ec4899' },
  { key: 'operLogs', label: '操作日志', value: stats.value.operLogs, icon: ScrollText, hoverIcon: FileClock, accent: '#38bdf8' },
  { key: 'loginLogs', label: '登录日志', value: stats.value.loginLogs, icon: LogIn, hoverIcon: ScanFace, accent: '#fb7185' },
  { key: 'notices', label: '最新公告', value: stats.value.notices, icon: Megaphone, hoverIcon: BellRing, accent: '#a3e635' },
])

// ---------- 数字滚动（rAF 缓动，从 0 滚到目标值） ----------
const display = ref<Record<string, number>>({})
function countUp(key: string, to: number, duration = 900) {
  const from = display.value[key] ?? 0
  if (from === to) { display.value[key] = to; return }
  const t0 = performance.now()
  const ease = (t: number) => 1 - Math.pow(1 - t, 3)
  const step = (now: number) => {
    const p = Math.min((now - t0) / duration, 1)
    display.value[key] = Math.round(from + (to - from) * ease(p))
    if (p < 1) requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
}

const hoveredIdx = ref(-1)

// ---------- 欢迎区 AI 图标轮播变形 ----------
const AI_ICONS = [Sparkles, Bot, BrainCircuit]
const aiIdx = ref(0)
let aiTimer: ReturnType<typeof setInterval> | undefined

onMounted(async () => {
  aiTimer = setInterval(() => { aiIdx.value = (aiIdx.value + 1) % AI_ICONS.length }, 3200)
  await load()
  for (const c of cards.value) countUp(c.key, c.value)
})
onUnmounted(() => clearInterval(aiTimer))
</script>

<template>
  <div class="dashboard">
    <!-- 欢迎横幅 -->
    <el-card class="welcome">
      <div class="welcome-inner">
        <div class="ai-orb" aria-hidden="true">
          <MorphIcon
            :icon="AI_ICONS[aiIdx]"
            :size="30"
            color="#00e08a"
            spring="bouncy"
            reduced-motion="user"
          />
        </div>
        <div class="welcome-text">
          <h2>
            欢迎回来，{{ userStore.userInfo?.nickname || userStore.username || '管理员' }}
          </h2>
          <p>身份中枢运行正常，所有服务节点在线。</p>
        </div>
        <div class="spec-chips mono" aria-hidden="true">
          <span class="chip">Quarkus 3.33 LTS</span>
          <span class="chip">启动 0.9s</span>
          <span class="chip">镜像 119MB</span>
        </div>
      </div>
    </el-card>

    <!-- 指标卡：hover 时图标弹簧变形 + 边框霓虹 -->
    <div class="stat-grid">
      <el-card
        v-for="(c, i) in cards"
        :key="c.key"
        class="stat-card stagger-in"
        :style="{ '--d': `${i * 60}ms`, '--accent': c.accent }"
        shadow="never"
        @mouseenter="hoveredIdx = i"
        @mouseleave="hoveredIdx = -1"
      >
        <div class="stat-top">
          <span class="stat-icon">
            <MorphIcon
              :icon="hoveredIdx === i ? c.hoverIcon : c.icon"
              :size="22"
              :color="c.accent"
              spring="bouncy"
              reduced-motion="user"
            />
          </span>
          <span class="stat-live" aria-hidden="true"><i class="pulse-dot" /></span>
        </div>
        <div class="stat-value mono">{{ display[c.key] ?? 0 }}</div>
        <div class="stat-label">{{ c.label }}</div>
      </el-card>
    </div>

    <!-- 列表卡 -->
    <div class="table-grid">
      <el-card class="stagger-in" :style="{ '--d': '520ms' }">
        <template #header>
          <div class="card-head"><span class="pulse-dot" />最新公告</div>
        </template>
        <el-table :data="recentNotices" size="small" empty-text="暂无公告">
          <el-table-column prop="noticeTitle" label="标题" show-overflow-tooltip />
          <el-table-column prop="noticeType" label="类型" width="80">
            <template #default="{ row }">
              <el-tag :type="row.noticeType === 1 ? 'primary' : 'warning'" size="small">
                {{ row.noticeType === 1 ? '通知' : '公告' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdBy" label="发布人" width="100" />
        </el-table>
      </el-card>
      <el-card class="stagger-in" :style="{ '--d': '600ms' }">
        <template #header>
          <div class="card-head"><span class="pulse-dot" />最近操作</div>
        </template>
        <el-table :data="recentOperLogs" size="small" empty-text="暂无操作">
          <el-table-column prop="title" label="模块" width="100" />
          <el-table-column prop="method" label="方法" show-overflow-tooltip />
          <el-table-column prop="username" label="操作人" width="100" />
          <el-table-column prop="durationMs" label="ms" width="60" />
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.dashboard { display: flex; flex-direction: column; gap: 16px; }

/* ---------- 欢迎横幅 ---------- */
.welcome :deep(.el-card__body) { padding: 20px 24px; }

.welcome-inner {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
}

.ai-orb {
  width: 56px; height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(circle at 30% 30%, rgba(0, 224, 138, 0.18), rgba(34, 211, 238, 0.08));
  border: 1px solid rgba(0, 224, 138, 0.35);
  box-shadow: 0 0 24px var(--jade-glow-soft), inset 0 0 16px rgba(0, 224, 138, 0.08);
  animation: orb-breathe 4s ease-in-out infinite;
}

@keyframes orb-breathe {
  0%, 100% { box-shadow: 0 0 18px var(--jade-glow-soft), inset 0 0 12px rgba(0, 224, 138, 0.08); }
  50% { box-shadow: 0 0 34px var(--jade-glow), inset 0 0 20px rgba(0, 224, 138, 0.14); }
}

.welcome-text { flex: 1; min-width: 200px; }
.welcome-text h2 { margin: 0 0 6px; font-size: 18px; color: var(--jade-text); }
.welcome-text p { margin: 0; color: var(--jade-text-secondary); font-size: 13px; }

.spec-chips { display: flex; gap: 8px; flex-wrap: wrap; }

.chip {
  font-size: 11px;
  letter-spacing: 0.5px;
  color: var(--jade-text-secondary);
  padding: 4px 10px;
  border: 1px solid var(--jade-border);
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.06);
}

/* ---------- 指标卡网格 ---------- */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}

.stat-card { cursor: default; }

.stat-card:hover {
  border-color: color-mix(in srgb, var(--accent) 45%, transparent);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.35), 0 0 22px color-mix(in srgb, var(--accent) 18%, transparent);
  transform: translateY(-2px);
}

.stat-card :deep(.el-card__body) { padding: 16px 18px; }

.stat-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.stat-icon {
  width: 40px; height: 40px;
  border-radius: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--accent) 28%, transparent);
}

.stat-live { opacity: 0.7; }

.stat-value {
  font-size: 30px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--jade-text);
}

.stat-label {
  margin-top: 4px;
  font-size: 12px;
  letter-spacing: 1px;
  color: var(--jade-text-dim);
}

/* ---------- 列表卡 ---------- */
.table-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}

.card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1200px) {
  .stat-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 479px) {
  .stat-grid { grid-template-columns: 1fr; }
  .spec-chips { display: none; }
}
@media (max-width: 860px) {
  .table-grid { grid-template-columns: 1fr; }
}
</style>
