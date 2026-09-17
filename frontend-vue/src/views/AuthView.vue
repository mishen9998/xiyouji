<template>
  <main class="auth-page">
    <ResponsiveImage class="auth-backdrop" :src="coverImageUrl()" alt="西行之路原版封面" sizes="100vw" object-fit="cover" critical />
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

      <p class="privacy-note">注册账号可跨浏览器登录；游客身份与存档入口仅保留在当前浏览器。</p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { authApi, type AuthProfile } from '@/api/game'
import ResponsiveImage from '@/components/ResponsiveImage.vue'
import { coverImageUrl } from '@/constants/images'

const router = useRouter()
const mode = ref<'login' | 'register'>('login')
const account = ref('')
const username = ref('')
const password = ref('')
const loading = ref(false)
const errorMessage = ref(useRoute().query.expired ? '登录已失效，请重新登录或选择游客模式。已有存档记录不会被删除。' : '')
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
  if (loading.value) return
  loading.value = true
  try {
    await authApi.validateSession()
    await router.push('/menu')
  } catch (error: any) {
    currentProfile.value = authApi.getProfile()
    errorMessage.value = error?.message || '暂时无法确认登录状态，请稍后重试'
  } finally {
    loading.value = false
  }
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

/* Restored entrance palette is scoped: in-game pages retain their own theme. */
:where(.home, .char-select, .auth-page) {
  --bg-dark: #100e17; --bg-card: #211d2e; --bg-panel: #211d2e;
  --text-primary: #f6eedc; --text-secondary: #cfc5b8; --text-muted: #b7ad9f;
  --gold: #f2bd58; --red: #ff958a; --blue: #8fc8ed; --purple: #b78ae1;
  --line: #ffffff26; color: var(--text-primary);
}
:where(.home, .char-select, .auth-page) button:focus-visible {
  outline: 3px solid #ffe0a0; outline-offset: 4px;
}

.auth-page { min-height: 100dvh; height: auto; isolation: isolate; padding-bottom: calc(36px + env(safe-area-inset-bottom)); }
.auth-backdrop { width: 100%; height: 100%; z-index: -2; background: #100e17; }
.auth-vignette { z-index: -1; }
.auth-shell { width: min(980px, 100%); grid-template-columns: minmax(0, 1fr) minmax(0, 420px); }
.brand-block h1 { font-size: clamp(30px, 4vw, 60px); letter-spacing: 4px; white-space: nowrap; }
.primary-action, .mode-tabs button, .continue-action { min-height: 48px; }
.guest-action { grid-template-columns: auto minmax(0, 1fr) auto; }
.guest-action small { line-height: 1.5; font-size: 12px; }
.privacy-note { color: #cec3b0; }
@media(max-width:800px) {
  .auth-shell { grid-template-columns: minmax(0, 1fr); width: min(430px, 100%); }
  .brand-seal { flex: 0 0 56px; }
  .brand-block { gap: 14px; }
  .brand-block h1 { font-size: clamp(30px, 8vw, 42px); letter-spacing: 4px; }
  .brand-copy { letter-spacing: 2px; }
  .eyebrow { letter-spacing: 2px; }
  .auth-card { padding: 20px; }
}

</style>
