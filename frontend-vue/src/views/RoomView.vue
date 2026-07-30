<!-- ====== 多人房间大厅 + 等待室 ====== -->
<template>
  <div class="room-view">
    <button class="btn-back" @click="handleBack">← 返回首页</button>

    <!-- ====== 大厅：创建/加入房间 ====== -->
    <div v-if="!roomStore.room" class="lobby">
      <h1 class="page-title">多人协作 · 西行之路</h1>
      <p class="subtitle">唐僧师徒五人，共闯九九八十一难</p>

      <div class="lobby-actions">
        <button class="lobby-btn create" :disabled="loading" @click="handleCreate">
          <span class="lobby-icon">🏰</span>
          <span class="lobby-label">{{ loading ? '创建中...' : '创建房间' }}</span>
          <span class="lobby-desc">生成房间码，邀请好友</span>
        </button>

        <div class="lobby-divider"></div>

        <div class="join-section">
          <input
            v-model="joinCode"
            class="join-input"
            placeholder="输入8位房间码"
            maxlength="8"
            @keyup.enter="handleJoin"
          />
          <button
            class="lobby-btn join"
            :disabled="loading || joinCode.length !== 8"
            @click="handleJoin"
          >
            <span class="lobby-icon">🚪</span>
            <span class="lobby-label">{{ loading ? '加入中...' : '加入房间' }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- ====== 等待室 ====== -->
    <div v-else-if="roomStore.room.status === 'WAITING'" class="waiting-room">
      <div class="room-header">
        <h1 class="page-title">房间等待室</h1>
        <div class="room-code-display">
          <span class="code-label">房间码</span>
          <span class="code-value">{{ roomStore.room.code }}</span>
          <button class="btn-copy" @click="copyCode">📋 复制</button>
        </div>
      </div>

      <!-- 玩家列表 -->
      <div class="player-slots">
        <div
          v-for="i in 5"
          :key="i"
          class="player-slot"
          :class="{ occupied: roomStore.room.players[i - 1], 'is-me': isMe(roomStore.room.players[i - 1]) }"
        >
          <template v-if="roomStore.room.players[i - 1]">
            <span class="slot-emoji">{{ getCharEmoji(roomStore.room.players[i - 1].characterClass) }}</span>
            <span class="slot-name">{{ roomStore.room.players[i - 1].username }}</span>
            <span v-if="roomStore.room.players[i - 1].host" class="slot-host">房主</span>
            <span class="slot-char">{{ getCharName(roomStore.room.players[i - 1].characterClass) }}</span>
            <span class="slot-ready" :class="{ ready: roomStore.room.players[i - 1].ready }">
              {{ roomStore.room.players[i - 1].ready ? '✓ 已准备' : '未准备' }}
            </span>
          </template>
          <template v-else>
            <span class="slot-empty">空位</span>
            <span class="slot-waiting">等待加入...</span>
          </template>
        </div>
      </div>

      <!-- 角色选择 -->
      <div v-if="myPlayer && !myPlayer.ready" class="char-select-section">
        <h2 class="section-title">选择你的角色</h2>
        <div class="char-grid">
          <div
            v-for="char in characters"
            :key="char.class"
            class="char-card-mini"
            :class="{
              selected: myPlayer?.characterClass === char.class,
              taken: isCharTaken(char.class)
            }"
            @click="handleSelectChar(char.class)"
          >
            <span class="char-emoji-mini">{{ char.emoji }}</span>
            <span class="char-name-mini">{{ char.name }}</span>
            <span class="char-hp-mini">HP {{ char.hp }}</span>
          </div>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="action-bar">
        <button class="btn-secondary" @click="handleLeave">退出房间</button>
        <button
          v-if="myPlayer && !myPlayer.ready"
          class="btn-primary"
          @click="handleReady"
        >
          准备
        </button>
        <button
          v-else-if="myPlayer && myPlayer.ready"
          class="btn-secondary"
          @click="handleReady"
        >
          取消准备
        </button>
        <button
          v-if="roomStore.isHost && roomStore.canStart"
          class="btn-primary btn-start-battle"
          @click="handleStartGame"
        >
          🗺️ 开始游戏
        </button>
      </div>

      <!-- 系统消息 -->
      <div v-if="roomStore.systemMessages.length" class="system-messages">
        <div v-for="(msg, i) in roomStore.systemMessages.slice(-3)" :key="i" class="sys-msg">
          {{ msg }}
        </div>
      </div>
    </div>

    <!-- ====== 地图探索中：跳转到地图页 ====== -->
    <div v-else-if="roomStore.room.status === 'IN_MAP'" class="map-redirect">
      <p>正在进入地图...</p>
    </div>
    <!-- ====== 战斗中：跳转到战斗页 ====== -->
    <div v-else-if="roomStore.room.status === 'IN_BATTLE'" class="battle-redirect">
      <p>正在进入战斗...</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useRoomStore } from '@/stores/room'
import { useUiStore } from '@/stores/ui'
import { getCurrentUsername } from '@/api/room'
import type { CharacterClass, RoomPlayer } from '@/types'

const router = useRouter()
const roomStore = useRoomStore()
const uiStore = useUiStore()

const loading = ref(false)
const joinCode = ref('')

// 角色信息（与 CharacterSelectView 一致）
const charMap: Record<string, { emoji: string; name: string; hp: number }> = {
  SUN_WUKONG: { emoji: '🐵', name: '孙悟空', hp: 75 },
  ZHU_BAJIE: { emoji: '🐷', name: '猪八戒', hp: 85 },
  SHA_SENG: { emoji: '🟤', name: '沙僧', hp: 90 },
  BAI_LONGMA: { emoji: '🐴', name: '白龙马', hp: 70 },
  TANG_SANZANG: { emoji: '🧘', name: '唐三藏', hp: 80 },
}

const characters = Object.entries(charMap).map(([cls, info]) => ({
  class: cls as CharacterClass,
  emoji: info.emoji,
  name: info.name,
  hp: info.hp,
}))

const currentUsername = ref<string | null>(null)

onMounted(async () => {
  currentUsername.value = getCurrentUsername()
  // 如果已经在房间中，检查是否进入战斗
  if (roomStore.room?.status === 'IN_BATTLE') {
    router.push(`/room/${roomStore.roomCode}/battle`)
  }
})

// 监听房间状态变化，进入战斗
watch(() => roomStore.room?.status, (status) => {
  if (status === 'IN_MAP') {
    router.push(`/room/${roomStore.roomCode}/map`)
  } else if (status === 'IN_BATTLE') {
    router.push(`/room/${roomStore.roomCode}/battle`)
  }
})

onUnmounted(() => {
  // 不在这里断开 WebSocket，只在主动退出时断开
})

// ====== 当前用户相关 ======
const myPlayer = computed(() => {
  if (!roomStore.room || !currentUsername.value) return null
  return roomStore.room.players.find(p => p.userId === currentUsername.value) ?? null
})

function isMe(player?: RoomPlayer): boolean {
  return player?.userId === currentUsername.value
}

function isCharTaken(charClass: CharacterClass): boolean {
  if (!roomStore.room) return false
  return roomStore.room.players.some(p =>
    p.characterClass === charClass && p.userId !== currentUsername.value
  )
}

function getCharEmoji(cc: CharacterClass | null): string {
  return cc ? (charMap[cc]?.emoji ?? '?') : '❓'
}

function getCharName(cc: CharacterClass | null): string {
  return cc ? (charMap[cc]?.name ?? '未选择') : '未选择'
}

// ====== 大厅操作 ======
async function handleCreate() {
  loading.value = true
  try {
    await roomStore.createRoom()
  } finally {
    loading.value = false
  }
}

async function handleJoin() {
  if (joinCode.value.length !== 8) {
    uiStore.showToast('请输入8位房间码')
    return
  }
  loading.value = true
  try {
    await roomStore.joinRoom(joinCode.value.toUpperCase())
    joinCode.value = ''
  } catch {
    // 错误已在 store 中处理
  } finally {
    loading.value = false
  }
}

// ====== 等待室操作 ======
async function handleSelectChar(charClass: CharacterClass) {
  if (isCharTaken(charClass)) {
    uiStore.showToast('该角色已被其他玩家选择')
    return
  }
  await roomStore.selectCharacter(charClass)
}

async function handleReady() {
  if (!myPlayer.value?.characterClass) {
    uiStore.showToast('请先选择角色再准备')
    return
  }
  await roomStore.toggleReady()
}

async function handleLeave() {
  await roomStore.leaveRoom()
}

async function handleStartGame() {
  try {
    await roomStore.startGame()
    router.push(`/room/${roomStore.roomCode}/map`)
  } catch {
    // 错误已在 store 中处理
  }
}

function copyCode() {
  if (!roomStore.room) return
  navigator.clipboard.writeText(roomStore.room.code).then(() => {
    uiStore.showToast('房间码已复制')
  }).catch(() => {
    uiStore.showToast('复制失败，请手动复制: ' + roomStore.room?.code)
  })
}

function handleBack() {
  if (roomStore.room) {
    uiStore.showConfirm({
      title: '离开房间',
      message: '确定要返回首页吗？将退出当前房间。',
      okText: '退出房间',
      cancelText: '取消',
      onOk: async () => {
        await roomStore.leaveRoom()
        router.push('/')
      },
    })
  } else {
    router.push('/')
  }
}
</script>

<style scoped>
.room-view {
  width: 100%;
  height: 100vh;
  background: var(--bg-dark);
  overflow-y: auto;
  position: relative;
}

.btn-back {
  position: absolute;
  top: 20px;
  left: 20px;
  background: rgba(45, 42, 58, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: var(--text-secondary);
  padding: 8px 16px;
  border-radius: 6px;
  cursor: pointer;
  z-index: 10;
}
.btn-back:hover { color: var(--gold); border-color: var(--gold); }

/* ====== 大厅 ====== */
.lobby {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 20px;
}

.page-title {
  font-size: 36px;
  font-family: var(--font-display);
  background: linear-gradient(135deg, var(--gold), var(--red));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin-bottom: 8px;
}

.subtitle {
  color: var(--text-secondary);
  font-size: 14px;
  margin-bottom: 40px;
}

.lobby-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
  width: 100%;
  max-width: 400px;
}

.lobby-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  background: rgba(45, 42, 58, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 20px 40px;
  color: var(--text-primary);
  cursor: pointer;
  transition: all 0.2s;
  width: 100%;
}
.lobby-btn:hover:not(:disabled) {
  border-color: var(--gold);
  background: rgba(242, 169, 0, 0.1);
  transform: translateY(-2px);
}
.lobby-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.lobby-icon { font-size: 32px; }
.lobby-label { font-size: 18px; font-family: var(--font-display); }
.lobby-desc { font-size: 12px; color: var(--text-muted); }

.lobby-divider {
  width: 100%;
  height: 1px;
  background: rgba(255, 255, 255, 0.1);
}

.join-section {
  display: flex;
  gap: 12px;
  width: 100%;
}

.join-input {
  flex: 1;
  background: var(--bg-panel);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  padding: 12px 16px;
  color: var(--text-primary);
  font-size: 18px;
  letter-spacing: 4px;
  text-align: center;
  text-transform: uppercase;
}
.join-input:focus { border-color: var(--gold); outline: none; }

.join-section .lobby-btn { width: auto; padding: 12px 24px; }

/* ====== 等待室 ====== */
.waiting-room {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 20px 20px;
  max-width: 900px;
  margin: 0 auto;
}

.room-header {
  text-align: center;
  margin-bottom: 24px;
}

.room-code-display {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}

.code-label { color: var(--text-secondary); font-size: 14px; }

.code-value {
  font-size: 28px;
  font-weight: bold;
  font-family: monospace;
  color: var(--gold);
  letter-spacing: 6px;
  background: var(--bg-panel);
  padding: 8px 20px;
  border-radius: 8px;
  border: 1px solid rgba(242, 169, 0, 0.3);
}

.btn-copy {
  background: var(--bg-card);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: var(--text-secondary);
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
}
.btn-copy:hover { border-color: var(--gold); color: var(--gold); }

/* 玩家槽位 */
.player-slots {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
  width: 100%;
  margin-bottom: 24px;
}

.player-slot {
  background: var(--bg-panel);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 10px;
  padding: 16px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-height: 120px;
  justify-content: center;
}
.player-slot.occupied { border-color: rgba(102, 187, 106, 0.3); }
.player-slot.is-me { border-color: var(--gold); box-shadow: 0 0 12px rgba(242, 169, 0, 0.2); }

.slot-emoji { font-size: 28px; }
.slot-name { font-size: 14px; color: var(--text-primary); font-weight: bold; }
.slot-host {
  font-size: 10px;
  color: var(--gold);
  background: rgba(242, 169, 0, 0.15);
  padding: 2px 8px;
  border-radius: 4px;
}
.slot-char { font-size: 12px; color: var(--text-secondary); }
.slot-ready { font-size: 12px; color: var(--text-muted); }
.slot-ready.ready { color: var(--green); font-weight: bold; }

.slot-empty { font-size: 14px; color: var(--text-muted); }
.slot-waiting { font-size: 12px; color: var(--text-muted); }

/* 角色选择 */
.char-select-section { width: 100%; margin-bottom: 24px; }

.section-title {
  font-size: 18px;
  color: var(--text-primary);
  margin-bottom: 12px;
  text-align: center;
}

.char-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
}

.char-card-mini {
  background: var(--bg-panel);
  border: 2px solid transparent;
  border-radius: 8px;
  padding: 12px 8px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.char-card-mini:hover { border-color: var(--gold); }
.char-card-mini.selected { border-color: var(--gold); background: rgba(242, 169, 0, 0.1); }
.char-card-mini.taken { opacity: 0.3; cursor: not-allowed; }

.char-emoji-mini { font-size: 24px; }
.char-name-mini { font-size: 13px; color: var(--text-primary); }
.char-hp-mini { font-size: 11px; color: var(--text-muted); }

/* 操作栏 */
.action-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.btn-secondary {
  background: var(--bg-card);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: var(--text-secondary);
  padding: 12px 32px;
  font-size: 16px;
  border-radius: 8px;
  cursor: pointer;
  font-family: var(--font-display);
  letter-spacing: 2px;
}
.btn-secondary:hover { border-color: var(--red); color: var(--red); }

.btn-start-battle {
  background: linear-gradient(135deg, var(--red), var(--red-dark));
  color: white;
  font-size: 18px;
  padding: 14px 40px;
}

/* 系统消息 */
.system-messages { width: 100%; max-width: 600px; }

.sys-msg {
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
  padding: 4px;
  animation: fadeIn 0.3s;
}

@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }

.battle-redirect {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  color: var(--text-secondary);
}

/* 移动端 */
@media (max-width: 600px) {
  .char-grid { grid-template-columns: repeat(3, 1fr); }
  .page-title { font-size: 24px; }
  .code-value { font-size: 20px; letter-spacing: 4px; }
}
</style>
