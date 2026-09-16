<template>
  <main class="auth-page">
    <ResponsiveImage class="auth-backdrop" :src="sceneImageUrl('journey')" alt="西行山水旅程" sizes="100vw" object-fit="cover" critical />
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
import { useRouter } from 'vue-router'
import { authApi, type AuthProfile } from '@/api/game'
import ResponsiveImage from '@/components/ResponsiveImage.vue'
import { sceneImageUrl } from '@/constants/images'

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
.auth-page { min-height:100dvh; position:relative; display:grid;place-items:center;padding:32px 20px;background:var(--bg-dark);isolation:isolate; }
.auth-backdrop,.auth-vignette {position:fixed;inset:0;z-index:-1;pointer-events:none}.auth-backdrop{width:100%;height:100%;opacity:.28}.auth-vignette{background:linear-gradient(90deg,#f7f1e533,#f7f1e5cc)}
.auth-shell{width:min(100%,980px);display:grid;grid-template-columns:minmax(0,1fr) minmax(0,420px);align-items:center;gap:32px}
.brand-block{display:flex;align-items:center;gap:18px}.brand-seal{flex:0 0 76px;min-height:96px;display:grid;place-items:center;border:2px solid var(--red);color:#fff8e9;font:48px var(--font-display);background:var(--red);box-shadow:inset 0 0 0 5px #fff8e944}
.eyebrow{color:var(--gold);letter-spacing:3px;font-size:.7rem;margin-bottom:10px}.brand-block h1{font:700 clamp(2rem,5vw,3.8rem) var(--font-display);letter-spacing:6px;color:var(--green)}.brand-copy{margin-top:16px;color:var(--text-secondary);letter-spacing:3px}
.auth-card{padding:26px;border:1px solid var(--line);border-radius:20px;background:#fffaf0f2;box-shadow:0 16px 60px #283c3520}
.mode-tabs{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-bottom:20px}.mode-tabs button{border:0;border-bottom:2px solid var(--line);padding:10px;color:var(--text-secondary);background:transparent;font-weight:700}.mode-tabs button.active{color:var(--green);border-color:var(--green)}
.account-form{display:grid;gap:14px}.account-form label{display:grid;gap:6px}.account-form label>span{font-size:.8rem;color:var(--text-secondary)}.account-form label.muted{opacity:.6}
.account-form input{width:100%;min-width:0;border:1px solid var(--line);border-radius:8px;background:white;color:var(--text-primary);padding:12px;font-size:1rem}
.form-error{color:var(--red);font-size:.85rem}.primary-action{min-height:48px;border:0;border-radius:9px;padding:12px;background:var(--green);color:#fffaf0;font-weight:700}.primary-action:disabled,.guest-action:disabled{opacity:.55}
.divider{display:flex;align-items:center;gap:12px;font-size:.8rem;color:var(--text-muted);margin:20px 0}.divider::before,.divider::after{content:'';flex:1;height:1px;background:var(--line)}
.guest-action{width:100%;display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:12px;padding:12px;border:1px solid #9eb9a7;border-radius:12px;background:#edf3e8;text-align:left}
.guest-icon{width:38px;height:38px;display:grid;place-items:center;background:var(--green);color:white;border-radius:50%}.guest-action strong,.guest-action small{display:block}.guest-action small{margin-top:4px;font-size:.75rem;color:var(--text-secondary)}
.continue-action{width:100%;margin-top:12px;border:0;background:transparent;color:var(--green);font-size:.8rem}.privacy-note{grid-column:2;color:var(--text-secondary);text-align:center;font-size:.75rem;line-height:1.6}
@media(max-width:800px){.auth-shell{grid-template-columns:minmax(0,1fr);width:min(100%,430px);gap:24px}.brand-block{justify-content:center}.brand-seal{flex-basis:56px;min-height:70px;font-size:36px}.brand-block h1{font-size:2rem;letter-spacing:3px}.brand-copy{font-size:.8rem}.privacy-note{grid-column:1}.auth-card{padding:20px}}
</style>
