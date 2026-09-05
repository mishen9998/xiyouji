<template>
  <main class="auth-page">
    <div class="auth-backdrop"></div>
    <div class="auth-vignette"></div>

    <section class="auth-shell" aria-labelledby="auth-title">
      <header class="brand-block">
        <span class="brand-seal">西</span>
        <div>
          <p class="eyebrow">JOURNEY TO THE WEST</p>
          <h1 id="auth-title">西行之路</h1>
          <p class="brand-copy">择一身份，闯八十一难！</p>
        </div>
      </header>

      <div class="auth-card">
        <div class="mode-tabs" role="tablist" aria-label="账户方式">
          <button
            type="button"
            :class="{ active: mode === 'login' }"
            role="tab"
            :aria-selected="mode === 'login'"
            @click="mode = 'login'"
          >登录</button>
          <button
            type="button"
            :class="{ active: mode === 'register' }"
            role="tab"
            :aria-selected="mode === 'register'"
            @click="mode = 'register'"
          >注册</button>
        </div>

        <form class="account-form" @submit.prevent="submitAccount">
          <label>
            <span>登录账号</span>
            <input
              v-model.trim="account"
              name="account"
              autocomplete="username"
              minlength="3"
              maxlength="50"
              placeholder="3—50 个字符"
              required
            />
          </label>

          <label :class="{ muted: mode === 'login' }">
            <span>显示用户名</span>
            <input
              v-model.trim="username"
              name="username"
              autocomplete="nickname"
              minlength="3"
              maxlength="20"
              :disabled="mode === 'login'"
              :required="mode === 'register'"
              :placeholder="mode === 'register' ? '其他玩家将看到这个名字' : '仅注册时填写'"
            />
          </label>

          <label>
            <span>密码</span>
            <input
              v-model="password"
              name="password"
              type="password"
              :autocomplete="mode === 'register' ? 'new-password' : 'current-password'"
              minlength="6"
              maxlength="50"
              placeholder="6—50 个字符"
              required
            />
          </label>

          <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

          <button class="primary-action" type="submit" :disabled="loading">
            {{ loading ? '请稍候…' : mode === 'login' ? '进入西行' : '创建账号并进入' }}
          </button>
        </form>

        <div class="divider"><span>或者</span></div>

        <button class="guest-action" type="button" :disabled="loading" @click="enterGuest">
          <span class="guest-icon">游</span>
          <span>
            <strong>游客模式</strong>
            <small>无需注册 · 当前浏览器最多保留 3 个存档</small>
          </span>
          <b>→</b>
        </button>

        <button
          v-if="currentProfile"
          class="continue-action"
          type="button"
          :disabled="loading"
          @click="continueCurrent"
        >继续使用 {{ currentProfile.username }}（{{ roleLabel(currentProfile.role) }}）</button>
      </div>

      <p class="privacy-note">账号与用户名存入 MySQL，密码仅保存 BCrypt 哈希；游客身份不写入用户表。</p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi, type AuthProfile } from '@/api/game'

const router = useRouter()
const mode = ref<'login' | 'register'>('login')
const account = ref('')
const username = ref('')
const password = ref('')
const loading = ref(false)
const errorMessage = ref('')
const currentProfile = ref<AuthProfile | null>(
  authApi.getToken() ? authApi.getProfile() : null,
)

function roleLabel(role: string) {
  return role === 'GUEST' ? '游客' : '注册用户'
}

async function submitAccount() {
  if (loading.value) return
  errorMessage.value = ''
  if (mode.value === 'register' && !username.value) {
    errorMessage.value = '注册时请填写显示用户名'
    return
  }
  loading.value = true
  try {
    if (mode.value === 'login') {
      await authApi.login(account.value, password.value)
    } else {
      await authApi.register(account.value, username.value, password.value)
    }
    await router.push('/menu')
  } catch (error: any) {
    errorMessage.value = error?.message || (mode.value === 'login' ? '登录失败' : '注册失败')
  } finally {
    loading.value = false
  }
}

async function enterGuest() {
  if (loading.value) return
  errorMessage.value = ''
  loading.value = true
  try {
    await authApi.guestLogin()
    await router.push('/menu')
  } catch (error: any) {
    errorMessage.value = error?.message || '游客模式启动失败'
  } finally {
    loading.value = false
  }
}

async function continueCurrent() {
  await router.push('/menu')
}
</script>

<style scoped>
.auth-page {
  width: 100%;
  height: 100vh;
  position: relative;
  overflow: auto;
  display: grid;
  place-items: center;
  padding: 36px 20px;
  background: #100d0a;
}

.auth-backdrop,
.auth-vignette {
  position: fixed;
  inset: 0;
  pointer-events: none;
}

.auth-backdrop {
  background: url('/images/宝物/场景/login_screen.jpg') center / cover no-repeat;
  filter: saturate(.78) contrast(1.06);
  transform: scale(1.02);
}

.auth-vignette {
  background:
    radial-gradient(circle at 50% 42%, rgba(15, 10, 7, .12), rgba(9, 7, 8, .84) 78%),
    linear-gradient(90deg, rgba(16, 10, 6, .76), rgba(16, 10, 6, .18) 48%, rgba(16, 10, 6, .68));
}

.auth-shell {
  position: relative;
  z-index: 1;
  width: min(980px, 96vw);
  display: grid;
  grid-template-columns: minmax(280px, 1fr) minmax(350px, 420px);
  align-items: center;
  gap: clamp(36px, 8vw, 100px);
}

.brand-block { display: flex; align-items: center; gap: 22px; text-shadow: 0 3px 22px #000; }
.brand-seal {
  width: 92px; height: 112px; display: grid; place-items: center;
  border: 2px solid rgba(246, 206, 119, .78); color: #f5d78c;
  font: 58px/1 var(--font-display); background: rgba(93, 21, 14, .75);
  box-shadow: inset 0 0 0 7px rgba(248, 214, 137, .08), 0 12px 40px rgba(0, 0, 0, .38);
}
.eyebrow { color: #d9bd82; letter-spacing: 5px; font-size: 11px; margin-bottom: 10px; }
.brand-block h1 { font: 700 clamp(48px, 7vw, 76px)/1 var(--font-display); letter-spacing: 10px; color: #fff1c9; }
.brand-copy { margin-top: 16px; color: #d9c7a5; letter-spacing: 5px; }

.auth-card {
  padding: 28px;
  border: 1px solid rgba(244, 205, 121, .24);
  border-radius: 20px;
  background: linear-gradient(145deg, rgba(27, 23, 24, .94), rgba(18, 16, 21, .9));
  box-shadow: 0 28px 80px rgba(0, 0, 0, .52), inset 0 1px rgba(255, 255, 255, .04);
  backdrop-filter: blur(15px);
}

.mode-tabs { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-bottom: 24px; }
.mode-tabs button {
  border: 0; border-bottom: 2px solid rgba(255,255,255,.1); padding: 11px;
  color: #8f8a83; background: transparent; cursor: pointer; font: 600 16px var(--font-display); letter-spacing: 4px;
}
.mode-tabs button.active { color: #f5d78c; border-color: #d5a94e; }

.account-form { display: grid; gap: 14px; }
.account-form label { display: grid; gap: 7px; }
.account-form label > span { color: #c9bda8; font-size: 12px; letter-spacing: 2px; }
.account-form label.muted { opacity: .5; }
.account-form input {
  width: 100%; border: 1px solid rgba(255,255,255,.11); border-radius: 9px;
  background: rgba(255,255,255,.055); color: #fff5dc; padding: 12px 13px;
  outline: none; font: 14px var(--font-body); transition: border-color .2s, box-shadow .2s;
}
.account-form input:focus { border-color: #d5a94e; box-shadow: 0 0 0 3px rgba(213,169,78,.12); }
.account-form input:disabled { cursor: not-allowed; }
.form-error { color: #ff9b98; font-size: 13px; line-height: 1.5; }

.primary-action {
  margin-top: 4px; border: 0; border-radius: 9px; padding: 13px;
  background: linear-gradient(135deg, #e5bd63, #b87925); color: #24170c;
  font: 700 15px var(--font-display); letter-spacing: 3px; cursor: pointer;
}
.primary-action:disabled, .guest-action:disabled { opacity: .55; cursor: wait; }

.divider { display: flex; align-items: center; gap: 12px; color: #716d67; font-size: 11px; margin: 20px 0; }
.divider::before, .divider::after { content: ''; height: 1px; flex: 1; background: rgba(255,255,255,.09); }

.guest-action {
  width: 100%; display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 12px;
  border: 1px solid rgba(118, 160, 151, .35); border-radius: 12px; padding: 13px;
  background: rgba(57, 100, 91, .13); color: #d9eee6; text-align: left; cursor: pointer;
}
.guest-action:hover { border-color: rgba(130, 196, 181, .72); background: rgba(57, 100, 91, .24); }
.guest-icon { width: 38px; height: 38px; display: grid; place-items: center; border-radius: 50%; background: #315f56; font-family: var(--font-display); }
.guest-action strong, .guest-action small { display: block; }
.guest-action strong { margin-bottom: 4px; letter-spacing: 2px; }
.guest-action small { color: #94ada7; }
.continue-action { width: 100%; margin-top: 12px; border: 0; background: transparent; color: #bba878; cursor: pointer; font-size: 12px; }
.privacy-note { grid-column: 2; color: rgba(226,216,195,.58); text-align: center; font-size: 11px; line-height: 1.6; }

@media (max-width: 800px) {
  .auth-page { align-items: start; }
  .auth-shell { grid-template-columns: 1fr; width: min(430px, 94vw); gap: 24px; }
  .brand-block { justify-content: center; }
  .brand-seal { width: 64px; height: 78px; font-size: 40px; }
  .brand-block h1 { font-size: 42px; letter-spacing: 7px; }
  .brand-copy { font-size: 12px; }
  .privacy-note { grid-column: 1; }
}
</style>
