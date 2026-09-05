<!-- ====== 多人战斗视图 ====== -->
<template>
  <div class="mp-battle">
    <!-- 楼层显示 + 小地图玩家头像栏 -->
    <div v-if="roomStore.room" class="floor-bar">
      <span class="floor-label">🏰 第 {{ roomStore.room.floor }} 层</span>
      <!-- 小地图：所有玩家头像 -->
      <div class="mini-map">
        <span class="mini-map-title">🗺️ 队伍</span>
        <div class="mini-map-avatars">
          <template v-if="battle">
            <div
              v-for="player in battle.players"
              :key="player.userId"
              class="mini-avatar"
              :class="{
                dead: !player.alive,
                'is-me': player.userId === currentUsername,
                targeted: battle.enemy.targetPlayerIndex === player.index,
                ended: player.endedTurn && player.alive,
                ready: player.alive && !player.endedTurn && battle.playerTurn
              }"
              :title="player.username + (player.characterClass ? ' (' + charName(player.characterClass) + ')' : '')"
            >
              <span class="mini-avatar-emoji">{{ charEmoji(player.characterClass) }}</span>
              <span class="mini-avatar-name">{{ player.username }}</span>
              <span v-if="player.alive" class="mini-avatar-hp">{{ player.hp }}</span>
              <span v-else class="mini-avatar-dead">💀</span>
            </div>
          </template>
          <template v-else>
            <!-- 战斗未开始：显示房间内玩家 -->
            <div
              v-for="player in roomStore.room.players"
              :key="player.userId"
              class="mini-avatar"
              :class="{ 'is-me': player.userId === currentUsername }"
              :title="player.username"
            >
              <span class="mini-avatar-emoji">{{ charEmoji(player.characterClass) }}</span>
              <span class="mini-avatar-name">{{ player.username }}</span>
              <span v-if="player.ready" class="mini-avatar-hp">✓</span>
            </div>
          </template>
        </div>
      </div>
    </div>

    <!-- 敌人面板 -->
    <div v-if="battle" class="enemy-section">
      <div class="enemy-card">
        <div class="enemy-avatar">{{ battle.enemy.emoji || '👹' }}</div>
        <div class="enemy-info">
          <div class="enemy-name">{{ battle.enemy.name }}</div>
          <div class="hp-bar">
            <div class="hp-fill" :style="{ width: enemyHpPct + '%' }"></div>
            <span class="hp-text">{{ battle.enemy.hp }} / {{ battle.enemy.maxHp }}</span>
          </div>
          <div class="enemy-stats">
            <span v-if="battle.enemy.block" class="stat-block">🛡️ {{ battle.enemy.block }}</span>
            <span v-if="battle.enemy.strength" class="stat-str">💪 {{ battle.enemy.strength }}</span>
            <span v-for="(val, buff) in battle.enemy.buffs" :key="buff" class="enemy-buff">
              {{ buffEmoji(buff as string) }} {{ val }}
            </span>
          </div>
        </div>
        <!-- 敌人意图 -->
        <div class="enemy-intent">
          <span class="intent-icon">{{ intentIcon(battle.enemy.intent) }}</span>
          <span class="intent-label">{{ intentLabel(battle.enemy.intent) }}</span>
          <span v-if="battle.enemy.intentValue > 0" class="intent-value">{{ battle.enemy.intentValue }}</span>
          <span v-if="battle.enemy.intent === 'ATTACK' && battle.players[battle.enemy.targetPlayerIndex]" class="intent-target">
            → {{ battle.players[battle.enemy.targetPlayerIndex].username }}
          </span>
        </div>
      </div>
    </div>

    <!-- 玩家面板区 -->
    <div v-if="battle" class="players-section">
      <div
        v-for="player in battle.players"
        :key="player.userId"
        class="player-panel"
        :class="{ dead: !player.alive, 'is-me': player.userId === currentUsername, 'targeted': battle.enemy.targetPlayerIndex === player.index && battle.playerTurn }"
      >
        <div class="player-header">
          <span class="player-emoji">{{ charEmoji(player.characterClass) }}</span>
          <span class="player-name">{{ player.username }}</span>
          <span v-if="player.characterClass" class="player-char-name">{{ charName(player.characterClass) }}</span>
          <span v-if="player.userId === currentUsername" class="me-tag">我</span>
        </div>
        <div class="player-hp-bar">
          <div class="hp-fill" :style="{ width: playerHpPct(player) + '%', background: player.alive ? 'var(--green)' : 'var(--red-dark)' }"></div>
          <span class="hp-text">{{ player.hp }}/{{ player.maxHp }}</span>
        </div>
        <div class="player-stats">
          <span class="stat-energy">⚡ {{ player.energy }}/{{ player.maxEnergy }}</span>
          <span v-if="player.block" class="stat-block">🛡️ {{ player.block }}</span>
          <span v-if="player.strength" class="stat-str">💪 {{ player.strength }}</span>
          <span v-if="player.endedTurn" class="ended-tag">已结束</span>
        </div>
        <div class="player-buffs">
          <span v-for="(val, buff) in player.buffs" :key="buff" class="player-buff">
            {{ buffEmoji(buff as string) }}{{ val }}
          </span>
        </div>
      </div>
    </div>

    <!-- 我的手牌区 -->
    <div v-if="battle && myPlayer && myPlayer.alive" class="hand-section">
      <div class="hand-cards">
        <div
          v-for="card in myPlayer.hand"
          :key="card.index"
          class="mp-card"
          :class="{
            playable: canPlay(card),
            exhausted: card.exhaust
          }"
          @click="handlePlayCard(card.index)"
        >
          <div class="card-cost">⚡{{ card.cost }}</div>
          <div class="card-emoji">{{ card.emoji || '📜' }}</div>
          <div class="card-name">{{ card.name }}</div>
          <div class="card-effects">
            <span v-if="card.damage" class="card-dmg">⚔️{{ card.damage }}</span>
            <span v-if="card.block" class="card-blk">🛡️{{ card.block }}</span>
          </div>
        </div>
        <div v-if="myPlayer.hand.length === 0" class="no-cards">无手牌</div>
      </div>
    </div>

    <!-- 底部操作栏 -->
    <div v-if="battle" class="bottom-bar">
      <div class="turn-info">
        <span class="turn-num">第 {{ battle.turnNumber }} 回合</span>
        <span class="turn-phase">{{ battle.playerTurn ? '玩家行动' : '敌人行动' }}</span>
        <span class="players-ended">已结束 {{ battle.playersEndedTurn }}/{{ battle.alivePlayerCount }}</span>
      </div>
      <button
        v-if="myPlayer && myPlayer.alive && !myPlayer.endedTurn && battle.playerTurn && !battle.battleOver"
        class="btn-end-turn"
        @click="handleEndTurn"
      >
        结束回合
      </button>
    </div>

    <!-- 战斗日志 -->
    <div v-if="battle" class="combat-log">
      <div v-for="(log, i) in battle.combatLog.slice(-5)" :key="i" class="log-line">{{ log }}</div>
    </div>

    <!-- 战斗结果 -->
    <div v-if="battle && battle.battleOver" class="battle-result-overlay">
      <div class="result-modal">
        <h2 class="result-title" :class="{ victory: battle.victory, defeat: !battle.victory }">
          {{ battle.victory ? '🎉 胜利！' : '💀 失败...' }}
        </h2>
        <p class="result-desc">{{ battle.victory ? '师徒齐心，降妖除魔！' : '西行受阻，再接再厉。' }}</p>
        <button class="btn-primary" @click="handleReturn">返回大厅</button>
      </div>
    </div>

    <!-- 宝物选择界面（战斗胜利后） -->
    <div v-if="battle && battle.rewardsPhase && battle.victory" class="rewards-overlay">
      <div class="rewards-modal">
        <h2 class="rewards-title">战斗胜利！五张卡牌选一张</h2>

        <!-- 已领取状态 -->
        <div v-if="hasClaimed" class="claimed-status">
          <p>{{ myClaimedCardName === '__SKIPPED__' ? '已跳过奖励' : '✅ 已领取：' + myClaimedCardName }}</p>
        </div>

        <!-- 五张可选卡牌 -->
        <div v-else-if="myRewards.length > 0" class="rewards-cards">
          <div
            v-for="(card, index) in myRewards"
            :key="index"
            class="reward-card"
            :class="{ selected: selectedReward === card.name }"
            @click="selectReward(card.name)"
          >
            <div class="card-cost">⚡{{ card.cost }}</div>
            <div class="card-emoji">{{ card.emoji || '📜' }}</div>
            <div class="card-name">{{ card.name }}</div>
            <div class="card-effects">
              <span v-if="card.damage" class="card-dmg">⚔️{{ card.damage }}</span>
              <span v-if="card.block" class="card-blk">🛡️{{ card.block }}</span>
            </div>
            <div class="card-desc">{{ card.description }}</div>
          </div>
        </div>

        <div v-if="!hasClaimed && currentUsername && battle.rewards?.[currentUsername]" class="reward-actions">
          <button class="btn-primary" :disabled="rewardSubmitting || !selectedReward" @click="confirmReward(false)">继续前进</button>
          <button class="btn-small" :disabled="rewardSubmitting" @click="confirmReward(true)">跳过奖励</button>
        </div>
        <!-- 其他玩家领取状态 -->
        <div class="other-players-status">
          <div v-for="player in battle.players" :key="player.userId" class="player-status">
            <span class="player-status-emoji">{{ charEmoji(player.characterClass) }}</span>
            <span class="player-status-name">{{ player.username }}</span>
            <span v-if="battle.claimedRewards && battle.claimedRewards[player.userId]" class="status-claimed">{{ battle.claimedRewards[player.userId] === '__SKIPPED__' ? '已跳过' : '✅ 已领取' }}</span>
            <span v-else-if="battle.rewards?.[player.userId]" class="status-waiting">⏳ 等待中</span>
          </div>
        </div>

        <!-- 返回地图按钮 -->
        <div v-if="battle.rewardsHandled" class="next-floor-section">
          <button v-if="roomStore.isHost" class="btn-next-floor" :disabled="rewardSubmitting" @click="handleNextFloor">
            返回地图
          </button>
          <p v-else class="waiting-host">等待房主返回地图...</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useRoomStore } from '@/stores/room'
import { useUiStore } from '@/stores/ui'
import { getCurrentUsername } from '@/api/room'
import type { MultiplayerBattleInfo, MultiplayerPlayerInfo, MultiplayerCardInfo, CharacterClass } from '@/types'

const route = useRoute()
const router = useRouter()
const roomStore = useRoomStore()
const uiStore = useUiStore()

const currentUsername = ref<string | null>(null)
const battle = computed<MultiplayerBattleInfo | null>(() => roomStore.battleInfo)

const charEmojiMap: Record<string, string> = {
  SUN_WUKONG: '🐵', ZHU_BAJIE: '🐷', SHA_SENG: '🟤',
  BAI_LONGMA: '🐴', TANG_SANZANG: '🧘',
}

// 角色中文名映射
const charNameMap: Record<string, string> = {
  SUN_WUKONG: '孙悟空', ZHU_BAJIE: '猪八戒', SHA_SENG: '沙僧',
  BAI_LONGMA: '白龙马', TANG_SANZANG: '唐三藏',
}

const buffEmojiMap: Record<string, string> = {
  WEAK: '😵', VULNERABLE: '🔻', POISON: '☠️', STRENGTH: '💪',
  DEXTERITY: '✨', REGENERATION: '💚', RAGE: '🔥', FORTIFY: '🏰',
  GLUTTONY: '🍽️', SWIFT: '💨', BURN: '🔥', FROZEN: '🧊',
}

const intentIcons: Record<string, string> = {
  ATTACK: '⚔️', DEFEND: '🛡️', BUFF: '💪', DEBUFF: '🔻', SPECIAL: '✨',
}

const intentLabels: Record<string, string> = {
  ATTACK: '攻击', DEFEND: '防御', BUFF: '强化', DEBUFF: '削弱', SPECIAL: '特殊',
}

onMounted(async () => {
  currentUsername.value = getCurrentUsername()
  const code = route.params.code as string

  // 如果还没有连接 WebSocket，先连接
  if (!roomStore.connected && roomStore.roomCode !== code) {
    // 尝试获取房间信息
    try {
      const { roomApi } = await import('@/api/room')
      const room = await roomApi.getRoom(code)
      roomStore.room = room
      await roomStore.connectWs(code)
    } catch {
      uiStore.showToast('房间不存在或已结束')
      router.push('/room')
      return
    }
  }

  // 获取战斗状态
  await roomStore.refreshBattleState()

  if (!battle.value) {
    uiStore.showToast('战斗尚未开始')
    router.push('/room')
  }
})

onUnmounted(() => {
  // 不在这里断开，由用户主动退出
})

// 监听房间状态变化：当房主返回地图时，非房主玩家也自动跳转到地图
watch(() => roomStore.room?.status, (status) => {
  if (status === 'IN_MAP') {
    router.push(`/room/${roomStore.roomCode}/map`)
  } else if (status === 'FINISHED') {
    uiStore.showToast('🎉 恭喜通关！')
    router.push('/menu')
  } else if (status === 'WAITING') {
    router.push('/room')
  }
})

// ====== 计算属性 ======
const myPlayer = computed<MultiplayerPlayerInfo | null>(() => {
  if (!battle.value || !currentUsername.value) return null
  return battle.value.players.find(p => p.userId === currentUsername.value) ?? null
})

const enemyHpPct = computed(() => {
  if (!battle.value || battle.value.enemy.maxHp === 0) return 0
  return Math.max(0, (battle.value.enemy.hp / battle.value.enemy.maxHp) * 100)
})

function playerHpPct(player: MultiplayerPlayerInfo): number {
  return Math.max(0, (player.hp / player.maxHp) * 100)
}

// ====== 游戏操作 ======
function canPlay(card: MultiplayerCardInfo): boolean {
  if (!battle.value || !myPlayer.value) return false
  return battle.value.playerTurn
    && !battle.value.battleOver
    && myPlayer.value.alive
    && !myPlayer.value.endedTurn
    && myPlayer.value.energy >= card.cost
}

async function handlePlayCard(handIndex: number) {
  try {
    await roomStore.playCard(handIndex)
  } catch {
    // 错误已在 store 中处理
  }
}

async function handleEndTurn() {
  try {
    await roomStore.endTurn()
  } catch {
    // 错误已在 store 中处理
  }
}

function handleReturn() {
  roomStore.reset()
  router.push('/menu')
}

// ====== 宝物奖励相关 ======
// 当前用户可选择的奖励卡牌
const myRewards = computed<MultiplayerCardInfo[]>(() => {
  if (!battle.value || !currentUsername.value || !battle.value.rewards) return []
  return battle.value.rewards[currentUsername.value] ?? []
})

// 当前用户是否已领取奖励
const hasClaimed = computed(() => {
  if (!battle.value || !currentUsername.value || !battle.value.claimedRewards) return false
  return !!battle.value.claimedRewards[currentUsername.value]
})

// 当前用户已领取的卡牌名称
const myClaimedCardName = computed(() => {
  if (!battle.value || !currentUsername.value || !battle.value.claimedRewards) return ''
  return battle.value.claimedRewards[currentUsername.value] ?? ''
})

const selectedReward = ref('')
const rewardSubmitting = ref(false)
function selectReward(name: string) {
  if (!rewardSubmitting.value && !hasClaimed.value) selectedReward.value = name
}
async function confirmReward(skip: boolean) {
  if (rewardSubmitting.value || hasClaimed.value || (!skip && !selectedReward.value)) return
  rewardSubmitting.value = true
  try {
    if (skip) await roomStore.skipReward()
    else await roomStore.claimReward(selectedReward.value)
  } catch { /* Store displays the error; keep selection for retry. */ }
  finally { rewardSubmitting.value = false }
}

async function handleNextFloor() {
  if (rewardSubmitting.value) return
  rewardSubmitting.value = true
  try {
    const result = await roomStore.nextFloor()
    if (!result) throw new Error('房间不存在')
    await router.push(result.completed || roomStore.room?.status === 'FINISHED' ? '/menu' : `/room/${roomStore.roomCode}/map`)
  } catch { /* Store displays the error. */ }
  finally { rewardSubmitting.value = false }
}

// ====== 辅助方法 ======
function charEmoji(cc: CharacterClass | null): string {
  return cc ? (charEmojiMap[cc] ?? '❓') : '❓'
}

function charName(cc: CharacterClass | null): string {
  return cc ? (charNameMap[cc] ?? '') : ''
}

function buffEmoji(buff: string): string {
  return buffEmojiMap[buff] ?? buff
}

function intentIcon(intent: string): string {
  return intentIcons[intent] ?? '❓'
}

function intentLabel(intent: string): string {
  return intentLabels[intent] ?? intent
}
</script>

<style scoped>
.reward-card.selected { outline: 3px solid #f2a900; }
.reward-actions { display: flex; gap: 12px; justify-content: center; margin: 16px 0; }

.mp-battle {
  width: 100%;
  height: 100vh;
  background: var(--bg-dark);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ====== 敌人面板 ====== */
.enemy-section {
  padding: 12px 20px;
  display: flex;
  justify-content: center;
}

.enemy-card {
  display: flex;
  align-items: center;
  gap: 16px;
  background: var(--bg-panel);
  border: 1px solid rgba(232, 93, 117, 0.3);
  border-radius: 12px;
  padding: 12px 24px;
  min-width: 400px;
}

.enemy-avatar { font-size: 48px; }

.enemy-info { flex: 1; }

.enemy-name {
  font-size: 18px;
  font-weight: bold;
  color: var(--red);
  margin-bottom: 4px;
}

.hp-bar {
  position: relative;
  height: 20px;
  background: var(--bg-card);
  border-radius: 10px;
  overflow: hidden;
}

.hp-fill {
  height: 100%;
  background: var(--red);
  transition: width 0.3s;
  border-radius: 10px;
}

.hp-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 12px;
  font-weight: bold;
  color: white;
  text-shadow: 0 1px 2px rgba(0,0,0,0.8);
}

.enemy-stats { display: flex; gap: 8px; margin-top: 4px; flex-wrap: wrap; }
.stat-block { color: var(--blue); font-size: 13px; }
.stat-str { color: var(--gold); font-size: 13px; }
.enemy-buff { font-size: 12px; color: var(--text-secondary); }

.enemy-intent {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  background: var(--bg-card);
  border-radius: 8px;
  padding: 8px 12px;
  min-width: 80px;
}

.intent-icon { font-size: 24px; }
.intent-label { font-size: 12px; color: var(--text-secondary); }
.intent-value { font-size: 16px; font-weight: bold; color: var(--red); }
.intent-target { font-size: 11px; color: var(--gold); }

/* ====== 玩家面板 ====== */
.players-section {
  display: flex;
  gap: 8px;
  padding: 8px 16px;
  justify-content: center;
  flex-wrap: wrap;
}

.player-panel {
  background: var(--bg-panel);
  border: 2px solid transparent;
  border-radius: 10px;
  padding: 10px 14px;
  min-width: 140px;
  max-width: 180px;
  flex: 1;
}

.player-panel.is-me { border-color: var(--gold); box-shadow: 0 0 12px rgba(242, 169, 0, 0.2); }
.player-panel.dead { opacity: 0.4; }
.player-panel.targeted { border-color: var(--red); animation: pulse 1.5s infinite; }

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 8px rgba(232, 93, 117, 0.3); }
  50% { box-shadow: 0 0 16px rgba(232, 93, 117, 0.6); }
}

.player-header { display: flex; align-items: center; gap: 4px; margin-bottom: 6px; }
.player-emoji { font-size: 18px; }
.player-name { font-size: 13px; font-weight: bold; color: var(--text-primary); }
.me-tag { font-size: 10px; color: var(--gold); background: rgba(242, 169, 0, 0.2); padding: 1px 6px; border-radius: 4px; }

.player-hp-bar {
  position: relative;
  height: 16px;
  background: var(--bg-card);
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 4px;
}

.player-stats { display: flex; gap: 6px; flex-wrap: wrap; font-size: 12px; }
.stat-energy { color: var(--gold); }
.ended-tag { color: var(--text-muted); font-style: italic; }

.player-buffs { display: flex; gap: 4px; flex-wrap: wrap; margin-top: 2px; }
.player-buff { font-size: 11px; }

/* ====== 手牌区 ====== */
.hand-section {
  flex: 1;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 0 16px 8px;
  overflow: hidden;
}

.hand-cards {
  display: flex;
  gap: 8px;
  justify-content: center;
  flex-wrap: nowrap;
  overflow-x: auto;
}

.mp-card {
  background: var(--bg-card);
  border: 2px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  padding: 10px 8px;
  min-width: 90px;
  max-width: 110px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.mp-card.playable {
  border-color: var(--gold);
}
.mp-card.playable:hover {
  transform: translateY(-8px);
  box-shadow: 0 8px 16px rgba(242, 169, 0, 0.3);
}
.mp-card:not(.playable) { opacity: 0.6; cursor: default; }
.mp-card.exhausted { border-color: var(--red); }

.card-cost {
  position: absolute;
  top: -6px;
  left: -6px;
  background: var(--gold);
  color: var(--bg-dark);
  font-size: 12px;
  font-weight: bold;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-emoji { font-size: 28px; margin: 4px 0; }
.card-name { font-size: 12px; color: var(--text-primary); margin-bottom: 4px; }
.card-effects { display: flex; gap: 4px; justify-content: center; }
.card-dmg { color: var(--red); font-size: 13px; }
.card-blk { color: var(--blue); font-size: 13px; }

.no-cards { color: var(--text-muted); font-size: 14px; padding: 20px; }

/* ====== 底部操作栏 ====== */
.bottom-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 20px;
  background: var(--bg-panel);
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}

.turn-info { display: flex; gap: 16px; align-items: center; }
.turn-num { color: var(--gold); font-weight: bold; font-size: 14px; }
.turn-phase { color: var(--text-secondary); font-size: 13px; }
.players-ended { color: var(--text-muted); font-size: 12px; }

.btn-end-turn {
  background: linear-gradient(135deg, var(--red), var(--red-dark));
  color: white;
  border: none;
  padding: 10px 28px;
  font-size: 16px;
  border-radius: 8px;
  cursor: pointer;
  font-family: var(--font-display);
  letter-spacing: 2px;
}
.btn-end-turn:hover { opacity: 0.9; transform: translateY(-1px); }

/* ====== 战斗日志 ====== */
.combat-log {
  position: absolute;
  bottom: 60px;
  right: 16px;
  width: 280px;
  max-height: 100px;
  overflow-y: auto;
  background: rgba(15, 14, 23, 0.8);
  border-radius: 8px;
  padding: 6px 10px;
  pointer-events: none;
}

.log-line {
  font-size: 11px;
  color: var(--text-secondary);
  line-height: 1.4;
}

/* ====== 战斗结果 ====== */
.battle-result-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.result-modal {
  background: var(--bg-panel);
  border-radius: 16px;
  padding: 40px 60px;
  text-align: center;
}

.result-title { font-size: 36px; margin-bottom: 8px; }
.result-title.victory { color: var(--gold); }
.result-title.defeat { color: var(--red); }
.result-desc { color: var(--text-secondary); margin-bottom: 24px; }

/* ====== 楼层显示 + 小地图 ====== */
.floor-bar {
  padding: 6px 16px;
  background: var(--bg-panel);
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.floor-label {
  color: var(--gold);
  font-weight: bold;
  font-size: 14px;
  font-family: var(--font-display);
  letter-spacing: 2px;
  white-space: nowrap;
}

/* 小地图：玩家头像栏 */
.mini-map {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  justify-content: flex-end;
  min-width: 0;
}

.mini-map-title {
  font-size: 11px;
  color: var(--text-muted);
  white-space: nowrap;
}

.mini-map-avatars {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.mini-avatar {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  padding: 3px 6px;
  background: var(--bg-card);
  border: 2px solid transparent;
  border-radius: 8px;
  min-width: 50px;
  position: relative;
  transition: all 0.2s;
}

.mini-avatar.is-me {
  border-color: var(--gold);
  background: rgba(242, 169, 0, 0.15);
  box-shadow: 0 0 8px rgba(242, 169, 0, 0.4);
}

.mini-avatar.ready {
  border-color: var(--green, #6bce6b);
}

.mini-avatar.targeted {
  border-color: var(--red);
  animation: pulse 1.5s infinite;
}

.mini-avatar.ended {
  opacity: 0.55;
}

.mini-avatar.dead {
  filter: grayscale(1);
  opacity: 0.35;
}

.mini-avatar-emoji {
  font-size: 20px;
  line-height: 1;
}

.mini-avatar-name {
  font-size: 10px;
  color: var(--text-primary);
  max-width: 60px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mini-avatar-hp {
  font-size: 10px;
  color: var(--green, #6bce6b);
  font-weight: bold;
}

.mini-avatar-dead {
  font-size: 14px;
}

/* 角色名称标签 */
.player-char-name {
  font-size: 11px;
  color: var(--text-secondary);
  background: rgba(255, 255, 255, 0.05);
  padding: 1px 6px;
  border-radius: 4px;
}

/* ====== 宝物选择界面 ====== */
.rewards-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}

.rewards-modal {
  background: var(--bg-panel);
  border: 2px solid var(--gold);
  border-radius: 16px;
  padding: 32px 40px;
  text-align: center;
  max-width: 720px;
  width: 90%;
  max-height: 90vh;
  overflow-y: auto;
}

.rewards-title {
  font-size: 28px;
  color: var(--gold);
  margin-bottom: 20px;
  font-family: var(--font-display);
  letter-spacing: 2px;
}

.claimed-status {
  background: rgba(242, 169, 0, 0.1);
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 16px;
}

.claimed-status p {
  color: var(--gold);
  font-size: 16px;
  margin: 0;
}

.rewards-cards {
  flex-wrap: wrap;
  display: flex;
  gap: 16px;
  justify-content: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.reward-card {
  background: var(--bg-card);
  border: 2px solid rgba(242, 169, 0, 0.4);
  border-radius: 10px;
  padding: 16px 12px;
  width: 160px;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.reward-card:hover {
  transform: translateY(-8px);
  border-color: var(--gold);
  box-shadow: 0 8px 20px rgba(242, 169, 0, 0.4);
}

.reward-card .card-cost {
  position: absolute;
  top: -8px;
  left: -8px;
  background: var(--gold);
  color: var(--bg-dark);
  font-size: 13px;
  font-weight: bold;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.reward-card .card-emoji { font-size: 40px; margin: 8px 0; }
.reward-card .card-name { font-size: 14px; color: var(--text-primary); margin-bottom: 6px; font-weight: bold; }
.reward-card .card-effects { display: flex; gap: 6px; justify-content: center; margin-bottom: 6px; }
.reward-card .card-dmg { color: var(--red); font-size: 14px; }
.reward-card .card-blk { color: var(--blue); font-size: 14px; }
.reward-card .card-desc { font-size: 11px; color: var(--text-secondary); line-height: 1.4; }

.other-players-status {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
  padding: 12px;
  background: var(--bg-card);
  border-radius: 8px;
  margin-bottom: 16px;
}

.player-status {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
}

.player-status-emoji { font-size: 16px; }
.player-status-name { color: var(--text-primary); margin-right: 4px; }
.status-claimed { color: var(--gold); }
.status-waiting { color: var(--text-muted); }

.next-floor-section {
  margin-top: 8px;
}

.btn-next-floor {
  background: linear-gradient(135deg, var(--gold), #d4a017);
  color: var(--bg-dark);
  border: none;
  padding: 12px 32px;
  font-size: 16px;
  border-radius: 8px;
  cursor: pointer;
  font-family: var(--font-display);
  letter-spacing: 2px;
  font-weight: bold;
}

.btn-next-floor:hover { opacity: 0.9; transform: translateY(-1px); }

.waiting-host {
  color: var(--text-secondary);
  font-size: 14px;
  font-style: italic;
}

/* 移动端 */
@media (max-width: 768px) {
  .enemy-card { min-width: auto; width: 100%; }
  .players-section { gap: 4px; padding: 4px 8px; }
  .player-panel { min-width: 100px; padding: 6px 8px; }
  .player-name { font-size: 11px; }
  .mp-card { min-width: 70px; padding: 6px 4px; }
  .card-emoji { font-size: 20px; }
  .combat-log { display: none; }
}
</style>
