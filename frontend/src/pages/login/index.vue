<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const form = reactive({ username: 'admin', password: '' })
const loading = ref(false)

// 打字机副标题
const typed = ref('')
let typeTimer: ReturnType<typeof setTimeout> | undefined
onMounted(() => {
  const full = '温润如玉 · 稳定如石 · 智驭身份目录'
  let i = 0
  const step = () => {
    typed.value = full.slice(0, i)
    if (i++ <= full.length) typeTimer = setTimeout(step, 110)
  }
  step()
})
onUnmounted(() => clearTimeout(typeTimer))

async function handleLogin() {
  loading.value = true
  try {
    await userStore.login(form)
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.push(redirect)
  } catch (e) {
    // 已在 request.ts 统一提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page grid-bg">
    <!-- 动态背景层：极光光斑 + 扫描线 + 星点 -->
    <div class="aurora" aria-hidden="true"><i /><i /><i /></div>
    <div class="scanline" aria-hidden="true" />
    <div class="stars" aria-hidden="true" />

    <!-- 品牌区 -->
    <div class="brand">
      <div class="brand-badge">
        <span class="pulse-dot" />
        <span class="mono badge-text">SYSTEM ONLINE</span>
      </div>
      <h1 class="title neon-text">JADE PLATFORM</h1>
      <p class="subtitle">
        <span class="mono typing">{{ typed }}</span><span class="cursor" aria-hidden="true">_</span>
      </p>
      <div class="brand-metrics mono" aria-hidden="true">
        <div class="metric"><b>0.9s</b><span>冷启动</span></div>
        <div class="metric-sep" />
        <div class="metric"><b>283MB</b><span>镜像体积</span></div>
        <div class="metric-sep" />
        <div class="metric"><b>LTS</b><span>Quarkus 3.33</span></div>
      </div>
    </div>

    <!-- 登录卡片 -->
    <div class="login-shell">
      <div class="login-box stagger-in" style="--d: 120ms">
        <div class="box-head">
          <div class="box-dots" aria-hidden="true"><i /><i /><i /></div>
          <span class="mono box-title">AUTH / 身份认证</span>
        </div>
        <h2 class="form-title">欢迎登录</h2>
        <el-form :model="form" label-position="top" @submit.prevent="handleLogin">
          <el-form-item label="用户名">
            <el-input v-model="form.username" placeholder="请输入用户名" size="large" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              size="large"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
            {{ loading ? '认证中…' : '接入系统' }}
          </el-button>
        </el-form>
        <p class="tip mono">SECURE CHANNEL · 管理员账号密码由部署环境安全配置提供</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8vw;
  overflow: hidden;
  background:
    radial-gradient(ellipse 80% 60% at 70% 20%, rgba(0, 224, 138, 0.06), transparent 60%),
    radial-gradient(ellipse 60% 50% at 20% 85%, rgba(34, 211, 238, 0.05), transparent 60%),
    var(--jade-bg);
}

/* 扫描线：一条缓慢下移的微光 */
.scanline {
  position: absolute;
  left: 0; right: 0;
  height: 180px;
  background: linear-gradient(180deg, transparent, rgba(0, 224, 138, 0.045), transparent);
  animation: scan 9s linear infinite;
  pointer-events: none;
}

@keyframes scan {
  from { top: -200px; }
  to { top: 110%; }
}

/* 星点层 */
.stars {
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(1px 1px at 12% 22%, rgba(230, 237, 247, 0.5) 50%, transparent 51%),
    radial-gradient(1px 1px at 38% 68%, rgba(230, 237, 247, 0.32) 50%, transparent 51%),
    radial-gradient(1.5px 1.5px at 64% 14%, rgba(0, 224, 138, 0.5) 50%, transparent 51%),
    radial-gradient(1px 1px at 82% 44%, rgba(230, 237, 247, 0.4) 50%, transparent 51%),
    radial-gradient(1px 1px at 26% 86%, rgba(34, 211, 238, 0.45) 50%, transparent 51%),
    radial-gradient(1.5px 1.5px at 90% 78%, rgba(230, 237, 247, 0.3) 50%, transparent 51%);
  animation: twinkle 5s ease-in-out infinite alternate;
  pointer-events: none;
}

@keyframes twinkle {
  from { opacity: 0.5; }
  to { opacity: 1; }
}

/* ---------------- 品牌区 ---------------- */
.brand {
  position: relative;
  z-index: 1;
  max-width: 640px;
}

.brand-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  border-radius: 999px;
  border: 1px solid rgba(0, 224, 138, 0.3);
  background: rgba(0, 224, 138, 0.07);
  margin-bottom: 28px;
}

.badge-text { font-size: 11px; letter-spacing: 3px; color: var(--jade-primary); }

.title {
  font-size: clamp(40px, 5vw, 68px);
  font-weight: 800;
  margin: 0 0 18px;
  letter-spacing: 8px;
  font-family: var(--jade-mono);
}

.subtitle {
  font-size: 17px;
  color: var(--jade-text-secondary);
  letter-spacing: 3px;
  min-height: 28px;
  margin: 0 0 42px;
}

.typing { letter-spacing: 4px; }

.cursor {
  color: var(--jade-primary);
  animation: blink 1s steps(1) infinite;
}

@keyframes blink { 50% { opacity: 0; } }

.brand-metrics {
  display: flex;
  align-items: center;
  gap: 24px;
}

.metric { display: flex; flex-direction: column; gap: 4px; }

.metric b {
  font-size: 22px;
  font-weight: 700;
  color: var(--jade-text);
}

.metric span { font-size: 12px; color: var(--jade-text-dim); letter-spacing: 1px; }

.metric-sep {
  width: 1px;
  height: 34px;
  background: linear-gradient(180deg, transparent, var(--jade-border), transparent);
}

/* ---------------- 登录卡片 ---------------- */
.login-shell { position: relative; z-index: 1; }

.login-box {
  width: 400px;
  padding: 28px 32px 32px;
  border-radius: 18px;
  background: rgba(13, 20, 36, 0.78);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid var(--jade-border);
  box-shadow:
    0 24px 80px rgba(0, 0, 0, 0.55),
    0 0 0 1px rgba(0, 224, 138, 0.06),
    0 0 48px rgba(0, 224, 138, 0.07);
  position: relative;
  overflow: hidden;
}

/* 卡片顶部流动渐变描边 */
.login-box::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, #00e08a, #22d3ee, #8b5cf6, transparent);
  background-size: 220% 100%;
  animation: neon-flow 5s linear infinite;
}

.box-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.box-dots { display: flex; gap: 6px; }

.box-dots i {
  width: 9px; height: 9px;
  border-radius: 50%;
  background: rgba(148, 163, 184, 0.25);
}

.box-dots i:first-child { background: rgba(0, 224, 138, 0.7); box-shadow: 0 0 8px var(--jade-glow); }

.box-title { font-size: 11px; letter-spacing: 3px; color: var(--jade-text-dim); }

.form-title {
  margin: 0 0 26px;
  font-size: 22px;
  font-weight: 700;
  color: var(--jade-text);
  letter-spacing: 2px;
}

.login-btn {
  width: 100%;
  margin-top: 6px;
  height: 44px;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 6px;
  border-radius: 10px;
}

.tip {
  text-align: center;
  color: var(--jade-text-dim);
  font-size: 11px;
  letter-spacing: 1px;
  margin-top: 18px;
}

/* 窄屏：纵向堆叠 */
@media (max-width: 900px) {
  .login-page {
    flex-direction: column;
    justify-content: center;
    gap: 40px;
    padding: 24px;
  }
  .brand { text-align: center; }
  .brand-metrics { justify-content: center; }
  .login-shell { width: 100%; display: flex; justify-content: center; }
  .login-box { width: min(400px, 100%); }
  .title { letter-spacing: 4px; }
}
</style>
