<template>
  <div class="login">
    <div class="glow" aria-hidden="true"></div>
    <div class="login-card">
      <div class="mark"><el-icon><ReadingLamp /></el-icon></div>
      <h1 class="title">智慧路灯管理系统</h1>
      <p class="sub">SMART STREET LIGHT MANAGEMENT</p>

      <el-form class="form" @submit.prevent>
        <el-form-item>
          <el-input v-model="username" size="large" placeholder="用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="password"
            type="password"
            size="large"
            placeholder="密码"
            show-password
            :prefix-icon="Lock"
            @keyup.enter="doLogin"
          />
        </el-form-item>
        <el-button type="primary" size="large" class="btn" :loading="loading" @click="doLogin">
          登 录
        </el-button>
      </el-form>

      <p class="hint">
        演示账号 <span class="num">admin</span> / <span class="num">123456</span>
      </p>
    </div>
    <p class="foot">智慧路灯 · 城市照明物联网平台</p>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ReadingLamp, User, Lock } from '@element-plus/icons-vue'
import { login } from '../api/auth'

const username = ref('admin')
const password = ref('123456')
const loading = ref(false)
const router = useRouter()

async function doLogin() {
  if (loading.value) return
  loading.value = true
  try {
    const r = await login(username.value, password.value)
    localStorage.setItem('token', r.token)
    localStorage.setItem('username', r.username)
    router.push('/dashboard')
  } catch {
    // 登录失败提示已由请求拦截器统一弹出
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  position: relative;
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.glow {
  position: absolute;
  width: 720px;
  height: 720px;
  left: 50%;
  top: 40%;
  transform: translate(-50%, -50%);
  background: radial-gradient(circle, rgba(223, 179, 79, 0.1), transparent 62%);
  pointer-events: none;
}

.login-card {
  position: relative;
  width: min(400px, calc(100vw - 48px));
  padding: 46px 40px 34px;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: 20px;
  box-shadow: var(--shadow-lift);
}
@media (max-width: 480px) {
  .login-card {
    padding: 38px 26px 30px;
  }
}
.mark {
  width: 56px;
  height: 56px;
  margin: 0 auto;
  display: grid;
  place-items: center;
  border-radius: 15px;
  background: var(--accent-dim);
  border: 1px solid rgba(223, 179, 79, 0.28);
  color: var(--accent-bright);
}
.mark :deep(.el-icon) {
  font-size: 27px;
}
.title {
  text-align: center;
  margin: 22px 0 4px;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.02em;
}
.sub {
  text-align: center;
  color: var(--text-muted);
  font-size: 10.5px;
  letter-spacing: 0.12em;
  margin: 0 0 30px;
}
.form {
  margin-top: 4px;
}
.form :deep(.el-form-item) {
  margin-bottom: 18px;
}
.form :deep(.el-input__wrapper) {
  border-radius: 11px;
  padding-left: 13px;
}
.btn {
  width: 100%;
  border-radius: 11px;
  font-weight: 600;
  letter-spacing: 0.24em;
  margin-top: 4px;
  height: 44px;
}
.hint {
  text-align: center;
  color: var(--text-muted);
  font-size: 12px;
  margin: 24px 0 0;
}
.hint .num {
  color: var(--text-secondary);
}
.foot {
  position: relative;
  margin-top: 28px;
  font-size: 12px;
  color: var(--text-muted);
  letter-spacing: 0.04em;
}
</style>
