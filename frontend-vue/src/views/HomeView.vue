<!-- ====== 游戏首页 ====== -->
<template>
  <div class="home">
    <ResponsiveImage class="home-backdrop" :src="sceneImageUrl('journey')" alt="西行山水旅程" sizes="100vw" object-fit="cover" critical />
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
import { EMOJI_MAP, sceneImageUrl } from '@/constants/images'
import ResponsiveImage from '@/components/ResponsiveImage.vue'
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
.home { min-height:100dvh; position:relative; display:flex; flex-direction:column; align-items:center; padding:24px 16px 40px; isolation:isolate; }
.home-overlay {position:absolute;inset:0;z-index:-1;background:linear-gradient(#f7f1e566,#f7f1e5dd);}
.home-backdrop {position:absolute;inset:0;z-index:-2;width:100%;height:100%;}
.identity-bar {align-self:flex-end;display:grid;grid-template-columns:minmax(0,1fr) auto;gap:4px 12px;align-items:center;padding:8px 14px;border:1px solid var(--line);border-radius:12px;background:#fffaf0e8;max-width:100%;}
.identity-bar span {font-weight:700;overflow-wrap:anywhere;}.identity-bar small {grid-column:1;color:var(--text-secondary);font-size:.75rem;}.identity-bar button {grid-column:2;grid-row:1/3;border:0;border-left:1px solid var(--line);padding:8px;background:transparent;color:var(--green);}
.home-content {width:min(680px,100%);text-align:center;margin:auto;padding:48px 0 24px;}
.title {font:700 clamp(2.5rem,7vw,4rem) var(--font-display);color:var(--green);letter-spacing:8px;margin-bottom:8px;}
.subtitle {color:var(--text-secondary);letter-spacing:2px;margin-bottom:36px;font-size:.85rem;}
.menu-grid {display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;}
.menu-btn {min-width:0;display:flex;flex-direction:column;align-items:center;gap:8px;padding:24px 16px;border:1px solid var(--line);border-radius:16px;background:#fffaf0ed;font:700 1.125rem var(--font-display);box-shadow:var(--card-shadow);transition:transform .15s;}
.menu-btn:hover:not(:disabled){border-color:var(--green);transform:translateY(-2px)}.menu-btn:disabled{opacity:.55}.menu-icon{font-size:2rem}.menu-label{letter-spacing:3px}
.save-picker{display:grid;gap:12px}.save-slot{display:grid;gap:6px;padding:12px;text-align:left;border:1px solid var(--line);border-radius:8px;background:var(--bg-card)}.save-slot small{color:var(--text-secondary)}
@media(max-width:400px){.home-content{padding-top:32px}.menu-btn{padding:20px 10px;font-size:1rem}.title{letter-spacing:5px}.subtitle{letter-spacing:0}}
</style>
