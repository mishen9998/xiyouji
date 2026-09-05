<!-- ====== 游戏首页 ====== -->
<template>
  <div class="home">
    <div class="home-overlay"></div>
    <div class="identity-bar" v-if="profile">
      <span>{{ profile.username }}</span>
      <small>{{ profile.role === 'GUEST' ? `游客 · ${guestSlotCount}/3 存档` : '注册用户' }}</small>
      <button type="button" @click="switchIdentity">切换身份</button>
    </div>
    <div class="home-content">
      <h1 class="title">西行之路</h1>
      <p class="subtitle">Journey to the West · Roguelike</p>
      <div class="menu-grid">
        <button class="menu-btn" @click="handleSinglePlayer">
          <span class="menu-icon">⚔️</span>
          <span class="menu-label">单人游戏</span>
        </button>
        <button class="menu-btn" @click="handleMultiplayer">
          <span class="menu-icon">👥</span>
          <span class="menu-label">多人游戏</span>
        </button>
        <button class="menu-btn" :disabled="loading" @click="handleLoadGame">
          <span class="menu-icon">📂</span>
          <span class="menu-label">{{ loading ? '加载中...' : '加载游戏' }}</span>
        </button>
        <button class="menu-btn" @click="handleExit">
          <span class="menu-icon">🚪</span>
          <span class="menu-label">离开游戏</span>
        </button>
      </div>
    </div>

    <div v-if="showSlotPicker" class="modal-overlay" @click.self="showSlotPicker = false">
      <section class="modal-box save-picker" aria-labelledby="save-picker-title">
        <h3 id="save-picker-title">选择游客存档</h3>
        <p>当前浏览器保存了 {{ guestSlots.length }} 个游客进度。</p>
        <button
          v-for="(slot, index) in guestSlots"
          :key="slot.sessionId"
          class="save-slot"
          type="button"
          @click="loadSlot(slot.sessionId)"
        >
          <b>存档 {{ index + 1 }}</b>
          <span>{{ characterName(slot.characterClass) }}</span>
          <small>{{ formatTime(slot.createdAt) }}</small>
        </button>
        <button class="btn-small" type="button" @click="showSlotPicker = false">取消</button>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '@/api/game'
import { useGameStore } from '@/stores/game'
import { useUiStore } from '@/stores/ui'
import type { GuestSaveSlot } from '@/stores/guestSaves'
import { EMOJI_MAP } from '@/constants/images'
import type { GameState } from '@/types'

const router = useRouter()
const gameStore = useGameStore()
const uiStore = useUiStore()

const loading = ref(false)
const showSlotPicker = ref(false)
const guestSlots = ref<GuestSaveSlot[]>(gameStore.getGuestSaveSlots())
const profile = computed(() => authApi.getProfile())
const guestSlotCount = computed(() => guestSlots.value.length)

const characterNames: Record<string, string> = {
  SUN_WUKONG: '孙悟空', ZHU_BAJIE: '猪八戒', SHA_SENG: '沙僧',
  BAI_LONGMA: '白龙马', TANG_SANZANG: '唐三藏',
}

function characterName(characterClass: string) {
  return characterNames[characterClass] || characterClass
}

function formatTime(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '创建时间未知' : date.toLocaleString('zh-CN', { hour12: false })
}

function handleSinglePlayer() {
  router.push('/char-select')
}

function handleMultiplayer() {
  router.push('/room')
}

async function handleLoadGame() {
  if (profile.value?.role === 'GUEST') {
    guestSlots.value = gameStore.getGuestSaveSlots()
    if (guestSlots.value.length > 1) {
      showSlotPicker.value = true
      return
    }
  }
  const savedId = guestSlots.value[0]?.sessionId || gameStore.getSavedSessionId()
  if (!savedId) {
    uiStore.showToast('没有找到存档')
    return
  }

  await loadSlot(savedId)
}

async function loadSlot(savedId: string) {
  showSlotPicker.value = false
  loading.value = true
  try {
    const data: GameState | null = await gameStore.loadSavedSession(savedId)
    if (!data || !data.player) {
      uiStore.showToast('存档已失效或不存在')
      return
    }

    const player = data.player
    const emoji = player.emoji || EMOJI_MAP[player.characterClass] || ''
    const charInfo =
      `${emoji} ${player.displayName}\n` +
      `HP: ${player.hp}/${player.maxHp}\n` +
      `楼层: ${data.currentLayer}/${data.maxLayer}`

    uiStore.showConfirm({
      title: '发现存档',
      message: charInfo,
      okText: '继续游戏',
      cancelText: '返回',
      showDelete: true,
      deleteText: '删除存档',
      onOk: async () => {
        if (data.inBattle) {
          await gameStore.restoreBattleState()
          router.push('/battle')
        } else {
          router.push('/map')
        }
      },
      onDelete: () => {
        uiStore.showConfirm({
          title: '确认删除',
          message: '确定要删除此存档吗？此操作不可恢复。',
          okText: '确认删除',
          cancelText: '取消',
          onOk: async () => {
            await gameStore.deleteSavedSession(savedId)
            guestSlots.value = gameStore.getGuestSaveSlots()
            uiStore.showToast('存档已删除')
          },
        })
      },
    })
  } catch (e: any) {
    uiStore.showToast('加载存档失败: ' + (e?.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

async function switchIdentity() {
  gameStore.clearAll(true)
  authApi.logout()
  await router.push('/')
}

function handleExit() {
  uiStore.showConfirm({
    title: '离开游戏',
    message: '确定要离开游戏吗？',
    okText: '确定离开',
    cancelText: '取消',
    onOk: () => {
      window.close()
    },
  })
}
</script>

<style scoped>
.home {
  width: 100%;
  height: 100vh;
  background: url('/images/宝物/场景/login_screen.jpg') center / cover no-repeat;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.home-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(15, 14, 23, 0.6);
}

.identity-bar {
  position: absolute;
  top: 20px;
  right: 22px;
  z-index: 3;
  display: grid;
  grid-template-columns: auto auto;
  column-gap: 10px;
  align-items: center;
  padding: 9px 11px 9px 15px;
  border: 1px solid rgba(242, 169, 0, .22);
  border-radius: 999px;
  background: rgba(20, 17, 25, .76);
  backdrop-filter: blur(8px);
}
.identity-bar span { color: #fff2cf; font-weight: 700; }
.identity-bar small { grid-column: 1; color: var(--text-muted); font-size: 10px; }
.identity-bar button {
  grid-column: 2;
  grid-row: 1 / 3;
  border: 0;
  border-left: 1px solid rgba(255,255,255,.12);
  padding-left: 10px;
  background: transparent;
  color: var(--gold);
  cursor: pointer;
}

.save-picker { display: grid; gap: 10px; }
.save-slot {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 12px;
  align-items: center;
  width: 100%;
  border: 1px solid rgba(242,169,0,.2);
  border-radius: 10px;
  padding: 13px 14px;
  background: rgba(255,255,255,.04);
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
}
.save-slot:hover { border-color: var(--gold); background: rgba(242,169,0,.08); }
.save-slot span { color: var(--gold); }
.save-slot small { color: var(--text-muted); }

.home-content {
  position: relative;
  z-index: 1;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.title {
  font-size: 64px;
  font-family: var(--font-display);
  font-weight: bold;
  background: linear-gradient(135deg, var(--gold), var(--red), var(--purple));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: 12px;
  margin-bottom: 8px;
  text-shadow: 0 0 40px rgba(242, 169, 0, 0.3);
}

.subtitle {
  font-size: 16px;
  color: var(--text-secondary);
  letter-spacing: 4px;
  margin-bottom: 56px;
  font-style: italic;
}

.menu-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.menu-btn {
  background: rgba(45, 42, 58, 0.8);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 24px 48px;
  color: var(--text-primary);
  font-size: 18px;
  font-family: var(--font-display);
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  min-width: 180px;
}

.menu-btn:hover:not(:disabled) {
  background: rgba(242, 169, 0, 0.15);
  border-color: var(--gold);
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(242, 169, 0, 0.2);
}

.menu-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.menu-icon {
  font-size: 32px;
}

.menu-label {
  letter-spacing: 4px;
}

/* ====== 移动端适配 ====== */
@media (max-width: 600px) {
  .title {
    font-size: 36px;
    letter-spacing: 6px;
  }
  .subtitle {
    font-size: 12px;
    letter-spacing: 2px;
    margin-bottom: 32px;
  }
  .menu-grid {
    grid-template-columns: 1fr;
    gap: 12px;
    width: 90vw;
    max-width: 320px;
  }
  .menu-btn {
    padding: 16px 24px;
    font-size: 16px;
    min-width: 0;
  }
  .menu-icon {
    font-size: 24px;
  }
}
</style>
