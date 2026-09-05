<!-- ====== 战斗主视图 ====== -->
<template>
  <div class="battle-view">
    <!-- 加载中 -->
    <div class="battle-loading" v-if="!bi">
      <div class="loading-spinner">⚔️</div>
      <div class="loading-text">战斗加载中...</div>
    </div>

    <template v-else>
      <!-- A. 左上角遗物栏 -->
      <div class="battle-relics">
        <div
          v-for="(relic, index) in relics"
          :key="index"
          class="relic-slot"
          :title="`${relic.name}: ${relic.description}`"
        >
          <img
            v-if="relicImgUrl(relic.name)"
            :src="relicImgUrl(relic.name) || ''"
            class="relic-icon"
            :alt="relic.name"
          />
          <span v-else class="relic-emoji">{{ relic.emoji || '🔮' }}</span>
        </div>
      </div>

      <!-- B. 战斗主区域 -->
      <div class="battle-arena">
        <!-- 玩家区 -->
        <div class="arena-side arena-player">
          <BattleCharacter3D
            v-if="battlePlayer"
            :character-class="battlePlayer.characterClass"
            :image-url="fullImgUrl(battlePlayer.characterClass || '')"
            :emoji="EMOJI_MAP[battlePlayer.characterClass || ''] || battlePlayer.emoji || '🦸'"
            :label="CHARACTER_DIR[battlePlayer.characterClass] || battlePlayer.displayName"
            :action="playerAction"
            :action-token="playerActionToken"
            size="lg"
            class="battle-player-character"
          />
          <div class="battle-action-status" :class="`is-${playerAction}`" aria-live="polite">
            <span class="battle-action-status__dot" aria-hidden="true"></span>
            {{ playerAction === 'idle' ? '待机' : playerAction === 'defense' ? '防御' : playerAction === 'ability' ? '能力' : playerAction === 'hit' ? '受击' : '攻击' }}
          </div>
          <div class="arena-info">
            <HpBar
              :hp="battlePlayer?.hp ?? 0"
              :max-hp="battlePlayer?.maxHp ?? 1"
              width="240px"
            />
            <div class="block-display">🛡️ {{ battlePlayer?.block ?? 0 }}</div>
            <div class="buff-container">
              <BuffBar :buffs="battlePlayer?.buffs" />
            </div>
          </div>
        </div>

        <!-- 敌人区 -->
        <div class="arena-side arena-enemy" v-if="battleEnemy">
          <div class="enemy-card" :class="{ 'boss-card': battleEnemy.isBoss }">
            <img
              v-if="enemyImgUrl(battleEnemy.name)"
              :src="enemyImgUrl(battleEnemy.name) || ''"
              class="enemy-avatar"
              :alt="battleEnemy.name"
            />
            <div v-else class="enemy-avatar enemy-emoji-fallback">
              {{ battleEnemy.emoji || '👹' }}
            </div>
            <div class="enemy-name">{{ battleEnemy.name }}</div>
            <HpBar
              :hp="battleEnemy.hp"
              :max-hp="battleEnemy.maxHp"
              :is-enemy="true"
              width="240px"
            />
            <div class="enemy-intent">
              <span class="intent-icon">{{ INTENT_ICONS[battleEnemy.intent] }}</span>
              <span class="intent-label">{{
                INTENT_LABELS[battleEnemy.intent] || '特殊'
              }}</span>
              <span class="intent-value" v-if="battleEnemy.intentValue">{{
                battleEnemy.intentValue
              }}</span>
            </div>
            <div class="buff-container">
              <BuffBar :buffs="battleEnemy.buffs" />
            </div>
          </div>
        </div>
      </div>

      <!-- C. 手牌区域 -->
      <div class="hand-zone" v-if="battlePlayer?.hand?.length">
        <GameCard
          v-for="(card, index) in battlePlayer?.hand"
          :key="card.id"
          :card="card"
          :index="index"
          :can-play="canPlayCard(card)"
          @play="onPlayCard(index)"
        />
      </div>

      <!-- D. 底部控制栏 -->
      <div class="battle-bottom">
        <div class="bottom-left">
          <div class="energy-display">
            ⚡ {{ battlePlayer?.energy ?? 0 }}/{{ battlePlayer?.maxEnergy ?? 0 }}
          </div>
          <button class="pile-btn" @click="pilesModalVisible = true">
            📥 {{ battlePlayer?.drawPileSize ?? 0 }}
          </button>
        </div>
        <div class="bottom-right">
          <button class="pile-btn" @click="pilesModalVisible = true">
            {{ battlePlayer?.discardPileSize ?? 0 }} 📤
          </button>
          <button
            class="end-turn-btn"
            :disabled="!canEndTurn || commandPending"
            @click="onEndTurn"
          >
            ⏭️ 结束回合
          </button>
        </div>
      </div>

      <!-- 牌堆弹窗 -->
      <DeckModal v-model:visible="pilesModalVisible" mode="piles" />

      <!-- 战斗结果弹窗 -->
      <BattleResultModal
        v-model:visible="resultModalVisible"
        @return-to-map="onReturnToMap"
        @next-layer="onNextLayer"
        @game-complete="onGameComplete"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useGameStore } from '@/stores/game'
import { useUiStore } from '@/stores/ui'
import { useBattleKeyboard } from '@/composables/useKeyboard'
import { useBattleAnimation } from '@/composables/useBattleAnimation'
import {
  fullImgUrl,
  enemyImgUrl,
  relicImgUrl,
  INTENT_ICONS,
  INTENT_LABELS,
  EMOJI_MAP,
  CHARACTER_DIR,
} from '@/constants/images'
import type { Card } from '@/types'
import HpBar from '@/components/HpBar.vue'
import BattleCharacter3D from '@/components/BattleCharacter3D.vue'
import GameCard from '@/components/GameCard.vue'
import BuffBar from '@/components/BuffBar.vue'
import DeckModal from '@/components/DeckModal.vue'
import BattleResultModal from '@/components/BattleResultModal.vue'

const router = useRouter()
const gameStore = useGameStore()
const { showToast } = useUiStore()

const { battleInfo, player, sessionId } = storeToRefs(gameStore)
const {
  startBattle,
  playCard,
  endTurn,
  resetBattle,
  refreshState,
  clearAll,
} = gameStore

const resultModalVisible = ref(false)
const pilesModalVisible = ref(false)
const playerAnimation = useBattleAnimation()
const playerAction = playerAnimation.action
const playerActionToken = playerAnimation.actionToken
const commandPending = ref(false)

// 响应式派生
const bi = computed(() => battleInfo.value)
const battlePlayer = computed(() => bi.value?.player)
const battleEnemy = computed(() => bi.value?.enemy)
const relics = computed(
  () => battlePlayer.value?.relics ?? player.value?.relics ?? []
)

const canEndTurn = computed(() => {
  return !!(bi.value?.playerTurn && !bi.value?.battleOver)
})

function canPlayCard(card: Card): boolean {
  if (commandPending.value) return false
  if (!bi.value?.playerTurn || bi.value?.battleOver) return false
  return (battlePlayer.value?.energy ?? 0) >= card.cost
}

async function onPlayCard(index: number) {
  if (commandPending.value) return
  if (!bi.value?.playerTurn || bi.value?.battleOver) return
  const card = battlePlayer.value?.hand?.[index]
  if (!card || !canPlayCard(card)) return
  // 先播动作，让出牌反馈不被网络延迟吞掉；actionToken 保证连续同类牌也会重播。
  playerAnimation.playCard(card.type)
  commandPending.value = true
  try {
    await playCard(index)
  } catch (e: any) {
    playerAnimation.idle()
    console.error('Play card failed:', e)
    // 会话丢失等严重错误时提示用户返回首页
    const msg = String(e?.message || '')
    if (msg.includes('会话不存在') || msg.includes('SESSION_NOT_FOUND') || msg.includes('404')) {
      showToast('⚠️ 游戏会话已失效，请重新开始')
      setTimeout(() => {
        clearAll()
        router.push('/menu')
      }, 1500)
      return
    }
    showToast('出牌失败: ' + msg)
  } finally {
    commandPending.value = false
  }
}

async function onEndTurn() {
  if (!canEndTurn.value || commandPending.value) return
  commandPending.value = true
  // endTurn 会在服务端同步执行敌人回合，保存一个轻量快照供受击检测。
  const before = bi.value
    ? {
        player: {
          hp: bi.value.player?.hp,
          block: bi.value.player?.block,
        },
        enemy: { intent: bi.value.enemy?.intent },
        playerTurn: bi.value.playerTurn,
        battleOver: bi.value.battleOver,
        turnNumber: bi.value.turnNumber,
        combatLog: bi.value.combatLog ? [...bi.value.combatLog] : [],
      }
    : null
  try {
    await endTurn()
    // 单人结束回合接口会完整执行一次敌人行动；以上一刻的攻击意图为准，
    // 即使伤害被格挡全部吸收，也应给玩家明确的受击反馈。
    if (String(before?.enemy?.intent || '').toUpperCase() === 'ATTACK') {
      playerAnimation.playHit()
    } else {
      playerAnimation.sync(before, bi.value)
    }
  } catch (e: any) {
    playerAnimation.idle()
    console.error('End turn failed:', e)
    const msg = String(e?.message || '')
    if (msg.includes('会话不存在') || msg.includes('SESSION_NOT_FOUND') || msg.includes('404')) {
      showToast('⚠️ 游戏会话已失效，请重新开始')
      setTimeout(() => {
        clearAll()
        router.push('/menu')
      }, 1500)
      return
    }
    showToast('结束回合失败: ' + msg)
  } finally {
    commandPending.value = false
  }
}

// 键盘快捷键: 1-9 打牌, E 结束回合
useBattleKeyboard(battleInfo, onPlayCard, onEndTurn)

// 战斗结束时弹出结果弹窗
watch(
  () => bi.value?.battleOver,
  (over) => {
    if (over) {
      resultModalVisible.value = true
    }
  },
  { immediate: true }
)

// 结果弹窗事件处理
function onReturnToMap() {
  resetBattle()
  refreshState()
  router.push('/map')
}

function onNextLayer() {
  resetBattle()
  refreshState()
  router.push('/map')
}

function onGameComplete() {
  resetBattle()
  clearAll()
  router.push('/menu')
}

onMounted(async () => {
  // 刷新页面后 sessionId 可能丢失，先尝试从 localStorage 恢复
  if (!sessionId.value || !player.value) {
    try {
      const restored = await gameStore.loadSavedSession()
      if (restored && restored.inBattle) {
        await gameStore.restoreBattleState()
        // 恢复成功后 battleInfo 应已被填充，直接渲染
        return
      } else if (restored) {
        // 有存档但不在战斗中，回地图
        router.push('/map')
        return
      }
    } catch (e) {
      console.error('Restore session failed:', e)
    }
    // 无存档可用，回首页
    router.push('/menu')
    return
  }

  // 已有 sessionId：无战斗信息（从地图进入）或上一场已结束则启动新战斗
  if (!battleInfo.value) {
    try {
      await startBattle()
    } catch (e) {
      console.error('Start battle failed:', e)
      showToast('战斗启动失败')
      router.push('/map')
    }
  }
})
</script>

<style scoped>
.battle-view {
  width: 100%;
  height: 100vh;
  position: relative;
  background: var(--bg-dark);
  overflow: hidden;
}

/* ====== 加载中 ====== */
.battle-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  gap: 16px;
}
.loading-spinner {
  font-size: 64px;
  animation: pulse 1.5s infinite;
}
.loading-text {
  color: var(--text-secondary);
  font-size: 18px;
}

/* ====== A. 遗物栏 ====== */
.battle-relics {
  position: fixed;
  top: 8px;
  left: 8px;
  display: flex;
  gap: 6px;
  z-index: 18;
}
.relic-slot {
  width: 42px;
  height: 42px;
  border-radius: 8px;
  overflow: hidden;
  background: var(--bg-card);
  border: 1px solid rgba(255, 255, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform 0.2s;
}
.relic-slot:hover {
  transform: scale(1.2);
  z-index: 2;
}
.relic-icon {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.relic-emoji {
  font-size: 24px;
}

/* ====== B. 战斗主区域 ====== */
.battle-arena {
  display: flex;
  height: calc(100vh - 120px);
  justify-content: center;
  align-items: center;
  padding: 60px 20px 0;
  gap: 40px;
}
.arena-side {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  max-width: 50%;
}

/* 玩家 */
.player-full-img {
  width: 200px;
  height: 280px;
  object-fit: cover;
  object-position: top center;
  border: 3px solid var(--gold);
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5), 0 0 16px rgba(242, 169, 0, 0.2);
}
.battle-player-character {
  --character-accent: var(--gold);
  --character-glow: rgba(242, 169, 0, 0.42);
  z-index: 1;
}
.battle-action-status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 24px;
  padding: 3px 11px;
  border: 1px solid rgba(242, 169, 0, 0.28);
  border-radius: 999px;
  background: rgba(15, 14, 23, 0.72);
  color: var(--gold);
  font-size: 12px;
  letter-spacing: 1px;
  transition: color 180ms ease, border-color 180ms ease, background 180ms ease;
}
.battle-action-status__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 8px currentColor;
}
.battle-action-status.is-attack {
  color: var(--red);
  border-color: rgba(232, 93, 117, 0.52);
  background: rgba(232, 93, 117, 0.12);
}
.battle-action-status.is-defense {
  color: var(--blue);
  border-color: rgba(79, 195, 247, 0.5);
  background: rgba(79, 195, 247, 0.12);
}
.battle-action-status.is-ability {
  color: var(--purple);
  border-color: rgba(187, 134, 252, 0.52);
  background: rgba(187, 134, 252, 0.12);
}
.battle-action-status.is-hit {
  color: #ff8f8f;
  border-color: rgba(255, 112, 112, 0.58);
  background: rgba(232, 93, 117, 0.2);
  animation: hit-status 560ms ease-out;
}

@keyframes hit-status {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-4px); }
  40% { transform: translateX(4px); }
  60% { transform: translateX(-2px); }
}
.player-emoji-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 96px;
  background: var(--bg-card);
}
.arena-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.player-name {
  font-family: var(--font-display);
  font-size: 18px;
  color: var(--text-primary);
}
.block-display {
  background: rgba(79, 195, 247, 0.15);
  color: var(--blue);
  border: 1px solid rgba(79, 195, 247, 0.4);
  border-radius: 6px;
  padding: 2px 12px;
  font-size: 15px;
  font-weight: bold;
}

/* 敌人 */
.enemy-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 24px;
  border-radius: 16px;
  background: linear-gradient(160deg, #2a1518, #1a1825);
  border: 2px solid rgba(232, 93, 117, 0.4);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
}
.enemy-card.boss-card {
  border-color: var(--gold);
  box-shadow: 0 0 24px rgba(242, 169, 0, 0.25), 0 8px 24px rgba(0, 0, 0, 0.5);
}
.enemy-avatar {
  width: 160px;
  height: 160px;
  border-radius: 50%;
  object-fit: cover;
  border: 4px solid var(--red);
  box-shadow: 0 4px 16px rgba(232, 93, 117, 0.3);
}
.enemy-emoji-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 80px;
  background: var(--bg-card);
}
.enemy-name {
  font-family: var(--font-display);
  font-size: 22px;
  color: var(--text-primary);
}
.enemy-intent {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--bg-card);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  padding: 4px 14px;
  font-size: 14px;
}
.intent-icon {
  font-size: 18px;
}
.intent-label {
  color: var(--text-secondary);
}
.intent-value {
  color: var(--red);
  font-weight: bold;
}

/* Buff 容器 */
.buff-container {
  max-width: 260px;
  width: 100%;
  display: flex;
  justify-content: center;
}

/* ====== C. 手牌区域 ====== */
.hand-zone {
  position: fixed;
  bottom: 120px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 0;
  justify-content: center;
  z-index: 10;
}
.hand-zone > * {
  margin-left: -25px;
}
.hand-zone > *:first-child {
  margin-left: 0;
}

/* ====== D. 底部控制栏 ====== */
.battle-bottom {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 110px;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  padding: 0 20px 12px;
  z-index: 20;
  pointer-events: none;
}
.bottom-left,
.bottom-right {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  pointer-events: auto;
}

.energy-display {
  font-size: 24px;
  color: var(--gold);
  background: var(--bg-panel);
  border: 2px solid var(--gold);
  border-radius: 12px;
  padding: 10px 28px;
  font-weight: bold;
  box-shadow: 0 0 12px rgba(242, 169, 0, 0.2);
}

.pile-btn {
  background: var(--bg-card);
  color: var(--text-secondary);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  padding: 10px 16px;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.2s;
}
.pile-btn:hover {
  background: #3a3650;
  color: var(--text-primary);
}

.end-turn-btn {
  background: linear-gradient(135deg, var(--red), var(--red-dark));
  color: #fff;
  border: none;
  font-size: 18px;
  padding: 12px 32px;
  border-radius: 12px;
  cursor: pointer;
  font-family: var(--font-display);
  letter-spacing: 2px;
  font-weight: bold;
  transition: all 0.2s;
}
.end-turn-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(232, 93, 117, 0.4);
}
.end-turn-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* ====== 响应式 ====== */
@media (max-width: 768px) {
  .battle-arena {
    gap: 10px;
    padding: 50px 10px 0;
  }
  .player-full-img {
    width: 140px;
    height: 200px;
  }
  .battle-player-character {
    transform: scale(0.82);
    transform-origin: center top;
    margin-bottom: -54px;
  }
  .battle-action-status {
    font-size: 11px;
    min-height: 21px;
    padding: 2px 8px;
  }
  .enemy-avatar {
    width: 110px;
    height: 110px;
  }
  .energy-display {
    font-size: 18px;
    padding: 8px 18px;
  }
  .end-turn-btn {
    font-size: 15px;
    padding: 10px 22px;
  }
}

@media (max-width: 600px) {
  .battle-arena {
    gap: 6px;
    padding: 44px 6px 0;
  }
  .player-full-img {
    width: 100px;
    height: 150px;
  }
  .battle-player-character {
    transform: scale(0.62);
    margin-bottom: -112px;
  }
  .enemy-avatar {
    width: 80px;
    height: 80px;
  }
  .battle-relics {
    top: 4px;
    left: 4px;
    gap: 4px;
  }
  .relic-slot {
    width: 28px;
    height: 28px;
  }
  .relic-icon {
    width: 24px;
    height: 24px;
  }
  .enemy-intent {
    font-size: 12px;
    padding: 3px 10px;
    gap: 4px;
  }
  .intent-icon {
    font-size: 14px;
  }
  .buff-container {
    max-width: 160px;
  }
  .hand-zone {
    bottom: 90px;
  }
  .hand-zone > * {
    margin-left: -30px;
  }
  .battle-bottom {
    height: 80px;
    padding: 0 10px 8px;
  }
  .energy-display {
    font-size: 16px;
    padding: 6px 14px;
  }
  .pile-btn {
    padding: 6px 10px;
    font-size: 13px;
  }
  .end-turn-btn {
    font-size: 13px;
    padding: 8px 16px;
    letter-spacing: 1px;
  }
}
</style>
