<template>
  <main class="login">
    <section class="login-shell" aria-labelledby="login-title">
      <header class="product-context">
        <div class="brand-line">
          <span class="mark" aria-hidden="true"><el-icon><ReadingLamp /></el-icon></span>
          <div>
            <div class="brand-name">智慧路灯</div>
            <div class="brand-code">SMART STREET LIGHT</div>
          </div>
        </div>
        <div class="context-copy">
          <p class="context-kicker">CITY LIGHTING FIELD SYSTEM / 2026</p>
          <h1 id="login-title">让城市的<br><em>夜晚可被读懂</em></h1>
          <p>以空间为索引，将设备、光照、告警与控制策略汇入同一张城市夜航图谱。</p>
        </div>
      </header>

      <div class="login-card">
        <div class="form-head">
          <span class="form-index num">ACCESS / 01</span>
          <h2>进入运维席位</h2>
          <p>使用授权账号接入城市照明网络</p>
        </div>

        <el-form class="form" label-position="top" @submit.prevent="doLogin">
          <el-form-item label="用户名" required>
            <el-input
              id="login-username"
              v-model="username"
              name="username"
              size="large"
              autocomplete="username"
              placeholder="请输入用户名"
              :prefix-icon="User"
              :aria-invalid="Boolean(formError)"
              aria-describedby="login-error"
              @input="formError = ''"
            />
          </el-form-item>
          <el-form-item label="密码" required>
            <el-input
              id="login-password"
              v-model="password"
              name="password"
              type="password"
              size="large"
              autocomplete="current-password"
              placeholder="请输入密码"
              show-password
              :prefix-icon="Lock"
              :aria-invalid="Boolean(formError)"
              aria-describedby="login-error"
              @input="formError = ''"
            />
          </el-form-item>

          <p id="login-error" class="form-error" role="alert" aria-live="assertive">
            {{ formError }}
          </p>

          <el-button
            native-type="submit"
            type="primary"
            size="large"
            class="btn"
            :loading="loading"
            :disabled="!username.trim() || !password"
            @click="doLogin"
          >
            进入夜航图谱
          </el-button>
        </el-form>

        <div class="demo-note">
          <span>演示账号</span>
          <code>admin</code>
          <span aria-hidden="true">/</span>
          <code>123456</code>
        </div>
      </div>
    </section>
    <p class="foot">SMART LIGHT ATLAS · 城市照明物联网平台</p>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Lock, ReadingLamp, User } from '@element-plus/icons-vue'
import { login } from '../api/auth'

const username = ref('admin')
const password = ref('123456')
const loading = ref(false)
const formError = ref('')
const router = useRouter()

async function doLogin() {
  if (loading.value) return
  if (!username.value.trim() || !password.value) {
    formError.value = '请输入用户名和密码。'
    return
  }
  loading.value = true
  formError.value = ''
  try {
    const response = await login(username.value.trim(), password.value)
    localStorage.setItem('token', response.token)
    localStorage.setItem('username', response.username)
    localStorage.setItem('role', response.role)
    await router.replace('/dashboard')
  } catch {
    formError.value = '登录失败，请核对账号密码或检查服务连接。'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  height: 100dvh;
  padding: 32px;
  display: grid;
  place-content: center;
  overflow-y: auto;
  background: #f5f7fa;
  background-image: radial-gradient(circle at 15% 20%, rgba(76, 175, 79, 0.12), transparent 32%), radial-gradient(circle at 88% 78%, rgba(200, 230, 201, 0.55), transparent 30%);
}

.login-shell {
  width: min(790px, calc(100vw - 48px));
  display: grid;
  grid-template-columns: minmax(260px, 0.8fr) minmax(380px, 1fr);
  overflow: hidden;
  border: 1px solid rgba(171, 190, 209, 0.28);
  border-radius: 16px;
  background: var(--bg-surface);
  box-shadow: var(--shadow-lift);
}

.product-context {
  min-height: 470px;
  padding: 32px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border-subtle);
  background: #263238;
}

.brand-line {
  display: flex;
  align-items: center;
  gap: 11px;
}

.mark {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  flex: none;
  border: 0;
  border-radius: 12px 4px 12px 4px;
  color: #ffffff;
  background: var(--accent);
}

.mark :deep(.el-icon) {
  font-size: 21px;
}

.brand-name {
  color: #ffffff;
  font-size: 15px;
  font-weight: 700;
}

.brand-code {
  margin-top: 2px;
  color: #abbed1;
  font-size: 9px;
  letter-spacing: 0.09em;
}

.context-copy {
  margin: auto 0;
}

.context-kicker {
  margin: 0 0 7px;
  color: #c8e6c9;
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 0.08em;
}

.context-copy h1 {
  margin: 0;
  color: #ffffff;
  font-size: 27px;
  font-weight: 690;
  letter-spacing: -0.03em;
}

.context-copy > p:last-child {
  max-width: 230px;
  margin: 9px 0 0;
  color: #abbed1;
  font-size: 12.5px;
  line-height: 1.7;
}

.login-card {
  padding: 46px 42px 36px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.form-head h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 22px;
  font-weight: 680;
  letter-spacing: -0.02em;
}

.form-head p {
  margin: 5px 0 0;
  color: var(--text-muted);
  font-size: 12.5px;
}

.form {
  margin-top: 24px;
}

.form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.form :deep(.el-form-item__label) {
  padding-bottom: 6px;
  line-height: 1.3;
}

.form :deep(.el-input__wrapper) {
  min-height: 42px;
  padding-left: 12px;
}

.form-error {
  min-height: 20px;
  margin: -4px 0 8px;
  color: var(--danger);
  font-size: 12px;
  line-height: 1.5;
}

.btn {
  width: 100%;
  height: 42px;
  margin-top: 2px;
  border-radius: 8px;
  font-weight: 650;
}

.demo-note {
  margin-top: 22px;
  padding-top: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border-top: 1px solid var(--border-subtle);
  color: var(--text-muted);
  font-size: 11.5px;
}

.demo-note code {
  color: var(--text-secondary);
  font-family: var(--font-data);
  font-size: 11.5px;
}

.foot {
  margin: 20px 0 0;
  color: #717171;
  font-size: 10.5px;
  letter-spacing: 0.05em;
  text-align: center;
}

@media (max-width: 700px) {
  .login {
    padding: 20px;
    place-content: start center;
  }

  .login-shell {
    width: min(430px, calc(100vw - 24px));
    grid-template-columns: 1fr;
  }

  .product-context {
    min-height: 0;
    padding: 22px 24px;
    border-right: 0;
    border-bottom: 1px solid var(--border-subtle);
  }

  .context-copy {
    margin: 24px 0 20px;
  }

  .context-copy h1 {
    font-size: 23px;
  }

  .context-copy > p:last-child {
    max-width: none;
  }

  .login-card {
    padding: 28px 24px 26px;
  }

  .form {
    margin-top: 20px;
  }
}

@media (max-height: 700px) and (min-width: 701px) {
  .product-context {
    min-height: 420px;
  }

  .login-card {
    padding-block: 30px;
  }
}
/* Night Atlas gateway */
.login { overflow: hidden; background: var(--ink); }
.login::before { content: ''; position: fixed; inset: 0; pointer-events: none; background-image: linear-gradient(rgba(208,255,111,.055) 1px, transparent 1px), linear-gradient(90deg, rgba(208,255,111,.055) 1px, transparent 1px); background-size: 52px 52px; mask-image: linear-gradient(90deg, #000 0 60%, transparent 85%); }
.login-shell { width: min(1480px, calc(100vw - 64px)); min-height: min(760px, calc(100vh - 80px)); grid-template-columns: minmax(0, 1.35fr) minmax(380px, .65fr); border: 1px solid rgba(208,255,111,.22); border-radius: 0; box-shadow: 0 50px 100px rgba(0,0,0,.24); }
.product-context { position: relative; padding: 48px; color: #effaf6; background: var(--ink); }
.product-context::after { content: '29.5934° N  /  106.2980° E'; position: absolute; right: 42px; bottom: 36px; color: #66837a; font: 10px var(--font-data); letter-spacing: .12em; }
.mark { border-radius: 0; color: var(--ink); background: var(--signal); transform: rotate(45deg); }
.mark :deep(.el-icon) { transform: rotate(-45deg); }
.brand-name { color: #fff; letter-spacing: .12em; }
.context-copy { max-width: 690px; }
.context-copy h1 { color: #f6fff9; font-size: clamp(48px, 6.2vw, 88px); line-height: .98; letter-spacing: -.055em; }
.context-copy h1 em { color: var(--signal); font-style: normal; font-weight: inherit; }
.context-copy > p:last-child { max-width: 560px; color: #a2b9b1; font-size: 15px; }
.context-kicker { color: var(--signal) !important; font: 10px var(--font-data); letter-spacing: .16em; }
.login-card { padding: 48px; border-left: 1px solid rgba(208,255,111,.18); border-radius: 0; background: #f7faf7; }
.form-index { display: block; margin-bottom: 18px; color: var(--accent-bright); font-size: 10px; letter-spacing: .15em; }
.form-head h2 { color: var(--ink); font-size: 30px; }
.login-card :deep(.el-input__wrapper) { border-radius: 0; box-shadow: 0 0 0 1px var(--border-strong) inset; }
.login-card :deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 2px var(--accent-bright) inset; }
.btn { border-radius: 0; color: var(--ink); background: var(--signal); border-color: var(--signal); font-weight: 750; letter-spacing: .08em; }
.btn:hover { color: #fff; background: var(--ink); border-color: var(--ink); }
.demo-note { border-radius: 0; background: var(--bg-surface-2); }
.foot { color: #6f8a81; }
@media (max-width: 760px) { .login { overflow: auto; background: var(--ink); } .login-shell { width: 100%; min-height: 100vh; margin: 0; grid-template-columns: 1fr; border: 0; } .product-context { min-height: 320px; padding: 28px 24px; } .context-copy h1 { font-size: 46px; } .context-copy > p:last-child { font-size: 13px; } .product-context::after { display: none; } .login-card { padding: 34px 24px 100px; border-top: 1px solid rgba(208,255,111,.18); border-left: 0; } .foot { display: none; } }
</style>
