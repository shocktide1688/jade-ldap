<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div class="error-page grid-bg">
    <div class="aurora" aria-hidden="true"><i /><i /><i /></div>
    <div class="error-box">
      <h1 class="code mono"><span class="glitch" data-text="404">404</span></h1>
      <p class="desc">页面不存在 · 已偏离网络坐标</p>
      <p class="mono path">ERR_NODE_NOT_FOUND</p>
      <el-button type="primary" size="large" class="back-btn" @click="router.push('/')">
        返回首页
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.error-page {
  position: relative;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--jade-bg);
}

.error-box {
  position: relative;
  z-index: 1;
  text-align: center;
  padding: 48px 64px;
  border-radius: 18px;
  background: var(--jade-panel-bg);
  backdrop-filter: blur(16px);
  border: 1px solid var(--jade-border);
  box-shadow: var(--jade-shadow-panel);
}

.code {
  margin: 0;
  font-size: 110px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: 6px;
  background: linear-gradient(120deg, #00e08a, #22d3ee);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  filter: drop-shadow(0 0 24px var(--jade-glow));
}

.desc { color: var(--jade-text-secondary); margin: 18px 0 6px; letter-spacing: 2px; }

.path { color: var(--jade-text-dim); font-size: 12px; letter-spacing: 3px; margin: 0 0 28px; }

.back-btn {
  min-width: 160px;
  height: 42px;
  font-weight: 700;
  letter-spacing: 4px;
  border-radius: 10px;
}
</style>
