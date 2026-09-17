<template>
  <div class="battle-view">
    <ResponsiveImage v-if="bi" class="battle-backdrop" :src="sceneImageUrl(gameStore.currentLayer === 1 ? 'blackwind' : gameStore.currentLayer === 2 ? 'firemountain' : 'lionridge')" alt="" emoji="" sizes="100vw" object-fit="cover" critical aria-hidden="true" />
    <div v-if="!bi" class="battle-loading" role="status"><span>西行途中</span><p>正在读取战斗…</p></div>
    <template v-else>
      <header class="battle-header">
        <div><span class="eyebrow">西行 · 降妖</span><h1>第 {{ bi.turnNumber || 1 }} 回合</h1></div>
        <div class="battle-relics" aria-label="携带遗物">
          <span v-for="(relic, index) in relics" :key="index" class="relic-slot" :title="`${relic.name}: ${relic.description}`">
            <ResponsiveImage v-if="relicImgUrl(relic.name)" :src="relicImgUrl(relic.name)" :alt="relic.name" :emoji="relic.emoji || '🔮'" sizes="36px" object-fit="cover" />
            <span v-else>{{ relic.emoji || '🔮' }}</span>
          </span>
        </div>
        <button class="view-toggle" :aria-pressed="threeD" @click="threeD = !threeD">{{ threeD ? '轻量插画' : '体验 3D' }}</button>
      </header>
      <main class="battle-stage" aria-label="战场">
        <p class="stage-scroll-hint">↕ 上下滑动战场，查看完整预告与状态</p>
        <div class="battle-arena">
          <section class="arena-player">
            <component :is="threeD ? BattleCharacter3D : BattleCharacter" v-if="battlePlayer"
              :character-class="battlePlayer.characterClass" :image-url="fullImgUrl(battlePlayer.characterClass || '')"
              :emoji="EMOJI_MAP[battlePlayer.characterClass || ''] || '🦸'"
              :label="CHARACTER_DIR[battlePlayer.characterClass] || battlePlayer.displayName"
              :action="playerAction" :action-token="playerActionToken" size="md" class="battle-player-character" />
            <div class="arena-info">
              <HpBar :hp="battlePlayer?.hp ?? 0" :max-hp="battlePlayer?.maxHp ?? 1" width="100%" />
              <span class="block-display">格挡 {{ battlePlayer?.block ?? 0 }}</span>
              <BuffBar :buffs="battlePlayer?.buffs" />
            </div>
          </section>
          <section v-if="battleEnemy" class="arena-enemy">
            <div class="enemy-portrait" :class="{ boss: battleEnemy.isBoss }">
              <ResponsiveImage v-if="enemyImgUrl(battleEnemy.name)" :src="enemyImgUrl(battleEnemy.name)" :alt="battleEnemy.name" :emoji="battleEnemy.emoji || '👹'" sizes="(max-width: 600px) 112px, 180px" :object-fit="battleEnemy.isBoss ? 'contain' : 'cover'" critical />
              <span v-else>{{ battleEnemy.emoji || '👹' }}</span>
              <span v-if="battleEnemy.isBoss" class="boss-seal">关主</span>
            </div>
            <h2>{{ battleEnemy.name }}</h2>
            <HpBar :hp="battleEnemy.hp" :max-hp="battleEnemy.maxHp" is-enemy width="100%" />
            <span v-if="battleEnemy.block" class="block-display">格挡 {{ battleEnemy.block }}</span>
            <BuffBar :buffs="battleEnemy.buffs" />
          </section>
          <EnemyIntent v-if="battleEnemy && !bi.battleOver" :enemy="battleEnemy" class="arena-forecast" />
        </div>
        <details v-if="bi.combatLog?.length" class="battle-log"><summary>本场战报</summary><p v-for="(log, i) in bi.combatLog.slice(-8)" :key="i">{{ log }}</p></details>
      </main>
      <footer class="battle-dock" :aria-busy="commandPending">
        <div class="selection-preview" aria-live="polite">
          <template v-if="selectedCard"><strong>{{ selectedCard.name }} · {{ selectedCard.cost }} 法力</strong><span>{{ selectedCard.description }}</span></template>
          <span v-else>运筹帷幄，见招拆招。</span>
          <small>{{ actionHint }}</small>
        </div>
        <div class="hand-zone" aria-label="手牌，左右滑动浏览">
          <GameCard v-for="(card, index) in battlePlayer?.hand" :key="`${card.id}:${index}`"
            :card="card" :index="index" :can-play="canPlayCard(card)" :selected="selectedIndex === index" preview @select="selectCard(index)" />
          <p v-if="!battlePlayer?.hand?.length" class="no-cards">手牌已用完，可以结束回合。</p>
        </div>
        <div class="battle-bottom">
          <span class="energy-display">法力 <strong>{{ battlePlayer?.energy ?? 0 }}/{{ battlePlayer?.maxEnergy ?? 0 }}</strong></span>
          <button class="pile-btn" @click="pilesModalVisible = true" :aria-label="`查看牌堆：抽牌堆 ${battlePlayer?.drawPileSize ?? 0}，弃牌堆 ${battlePlayer?.discardPileSize ?? 0}`">牌堆 {{ battlePlayer?.drawPileSize ?? 0 }} / {{ battlePlayer?.discardPileSize ?? 0 }}</button>
          <button class="confirm-card-btn" :disabled="!selectedCard || !canPlayCard(selectedCard)" @click="selectedIndex !== null && onPlayCard(selectedIndex)">{{ commandPending ? '同步中…' : '打出此牌' }}</button>
          <button class="end-turn-btn" :disabled="!canEndTurn || commandPending" @click="onEndTurn">结束回合</button>
        </div>
      </footer>
      <DeckModal v-model:visible="pilesModalVisible" mode="piles" />
      <BattleResultModal v-model:visible="resultModalVisible" @return-to-map="onReturnToMap" @next-layer="onNextLayer" @game-complete="onGameComplete" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, defineAsyncComponent } from 'vue'
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
  EMOJI_MAP,
  CHARACTER_DIR,
  sceneImageUrl,
} from '@/constants/images'
import type { Card } from '@/types'
import HpBar from '@/components/HpBar.vue'
import BattleCharacter from '@/components/BattleCharacter.vue'
import EnemyIntent from '@/components/EnemyIntent.vue'
import ResponsiveImage from '@/components/ResponsiveImage.vue'
const BattleCharacter3D = defineAsyncComponent(() => import('@/components/BattleCharacter3D.vue'))
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
const threeD = ref(false)
const selectedIndex = ref<number | null>(null)
const selectedCard = computed(() => selectedIndex.value === null ? null : battlePlayer.value?.hand?.[selectedIndex.value] ?? null)
const actionHint = computed(() => commandPending.value ? '正在同步本次操作，请稍候…'
  : bi.value?.battleOver ? '战斗已结束，请领取奖励。'
  : !bi.value?.playerTurn ? '敌人正在行动…'
  : selectedCard.value && !canPlayCard(selectedCard.value) ? '法力不足，换一张牌或结束回合。'
  : selectedCard.value ? '确认效果后，点击「打出此牌」。' : '点选手牌查看效果，再确认出牌。')
function selectCard(index: number) { if (!commandPending.value && battlePlayer.value?.hand?.[index]) selectedIndex.value = index }

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
watch(() => [bi.value?.turnNumber, battlePlayer.value?.hand?.map(card => card.id).join(',')].join(':'), () => { selectedIndex.value = null })

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
    selectedIndex.value = null
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

// 键盘快捷键: 1-9 仅预览选牌，明确确认后出牌；E 结束回合。
useBattleKeyboard(battleInfo, (index) => { if (!pilesModalVisible.value && !resultModalVisible.value) selectCard(index) }, () => { if (!pilesModalVisible.value && !resultModalVisible.value) void onEndTurn() })

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
  router.push('/complete')
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
.battle-view { position: relative; isolation: isolate; height: 100vh; height: 100dvh; width: 100%; display: grid; grid-template-rows: auto minmax(0, 1fr) auto; color: #283c35; background: radial-gradient(ellipse at 50% 22%, #dbe9d4 0, #f7f1e5 60%); overflow: hidden; }
.battle-backdrop { position: absolute; inset: 0; width: 100%; height: 100%; opacity: .22; pointer-events: none; z-index: -1; }
.battle-loading { height: 100dvh; display: grid; place-content: center; text-align: center; color: #21665b; }
.battle-loading > span { font: 1.875rem var(--font-display); }
.battle-header { display: flex; align-items: center; gap: 12px; padding: max(8px, env(safe-area-inset-top)) max(16px, env(safe-area-inset-right)) 8px max(16px, env(safe-area-inset-left)); background: #fffbf1ed; border-bottom: 1px solid #d8cbb0; }
.eyebrow { color: #776549; font-size: 0.6875rem; letter-spacing: .2em; }
h1 { font-family: var(--font-display); font-size: 1.25rem; margin: 2px 0 0; }
.battle-relics { display: flex; gap: 8px; margin-left: auto; max-width: 40%; overflow-x: auto; }
.relic-slot { flex: 0 0 36px; width: 36px; height: 36px; display: grid; place-items: center; border: 1px solid #d3c4a6; border-radius: 8px; background: #ede4d0; }
.relic-slot :deep(.responsive-image), .relic-slot :deep(img) { width: 100%; height: 100%; border-radius: inherit; object-fit: cover; }
.relic-slot :deep(.responsive-image__fallback) { min-height: 0; font-size: 1.375rem; }
button { min-width: 44px; min-height: 44px; font: inherit; cursor: pointer; touch-action: manipulation; border: 1px solid #b8b09a; border-radius: 9px; color: #314c41; background: #fffbf1; padding: 8px 12px; }
button:focus-visible { outline: 3px solid #b44736; outline-offset: 3px; }
button:disabled { opacity: .55; cursor: not-allowed; }
.view-toggle { font-size: 0.75rem; }
.battle-stage { overflow-y: auto; min-height: 0; padding: 20px max(16px, env(safe-area-inset-left)) 16px; }
.stage-scroll-hint { display: none; color: #5b6455; font-size: 0.6875rem; margin: 0 0 6px; text-align: center; }
.battle-arena { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) minmax(250px, 1.5fr); align-items: center; gap: 16px 28px; max-width: 1060px; margin: auto; }
.arena-player, .arena-enemy { min-width: 0; display: flex; flex-direction: column; align-items: center; gap: 8px; }
.battle-player-character { --character-width: 140px; --character-height: 152px; --character-accent: #9e7a34; --character-glow: #a5c7a533; }
.battle-player-character :deep(.battle-character__stage) { height: 176px; }
.arena-info { display: flex; flex-direction: column; align-items: center; gap: 6px; width: 100%; max-width: 240px; }
.enemy-portrait { position: relative; width: 150px; height: 150px; padding: 5px; border: 1px solid #a8bda2; border-radius: 50%; background: #f7f1e5; box-shadow: 0 8px 22px #48654112; }
.enemy-portrait :deep(.responsive-image), .enemy-portrait :deep(img) { width: 100%; height: 100%; object-fit: cover; border-radius: inherit; }
.enemy-portrait > span:not(.boss-seal) { display: grid; place-items: center; height: 100%; font-size: 4.5rem; }
.enemy-portrait.boss { border-color: #b18a47; border-radius: 16px; background: radial-gradient(ellipse at bottom, #e9e1be, #fffaf066); }
.enemy-portrait.boss :deep(.responsive-image) { background: transparent; border-radius: 0; }
.enemy-portrait.boss :deep(img) { object-fit: contain; border-radius: 0; }
.boss-seal { position: absolute; bottom: 0; right: -4px; padding: 5px 8px; color: #fffaf0; border-radius: 4px; background: #b44736; font-size: 0.75rem; transform: rotate(-6deg); }
h2 { font: 1.3125rem var(--font-display); margin: 0; }
.arena-enemy > .hp-bar-container { max-width: 240px; }
.block-display { font-size: 0.75rem; padding: 3px 9px; border-radius: 5px; background: #e1ece5; color: #245e50; }
.arena-forecast { grid-column: 3; grid-row: 1; align-self: center; }
.battle-log { max-width: 720px; margin: 12px auto 0; color: #5d6555; font-size: 0.75rem; }
.battle-log summary { min-height: 44px; cursor: pointer; padding: 12px 0; }
.battle-log p { margin: 5px 0; }
.battle-dock { max-height: 58dvh; overflow-y: auto; background: #fffbf1f5; border-top: 1px solid #c9c7ad; box-shadow: 0 -5px 20px #4865410a; padding-bottom: env(safe-area-inset-bottom); }
.selection-preview { display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px 12px; padding: 8px 20px 0; max-width: 1100px; margin: auto; font-size: 0.8125rem; }
.selection-preview strong { color: #21665b; }
.selection-preview small { font-size: 0.6875rem; color: #6b6555; }
.hand-zone { display: flex; gap: 10px; max-width: 1100px; margin: auto; overflow-x: auto; overscroll-behavior-x: contain; padding: 10px 20px 8px; scroll-padding: 20px; }
.hand-zone > :first-child { margin-left: auto; }
.hand-zone > :last-child { margin-right: auto; }
.no-cards { font-size: 0.8125rem; padding: 12px; color: #6b6555; }
.battle-bottom { display: flex; align-items: center; gap: 8px; padding: 8px 20px; max-width: 1100px; margin: auto; }
.energy-display { margin-right: auto; color: #21665b; white-space: nowrap; font-size: 0.875rem; }
.energy-display strong { font-size: 1.25rem; }
.pile-btn { font-size: 0.75rem; }
.confirm-card-btn, .end-turn-btn { min-height: 48px; padding: 10px 22px; font-weight: 700; }
.confirm-card-btn { background: #21665b; color: #fffdf6; border-color: #21665b; }
.end-turn-btn { background: #b44736; color: #fffdf6; border-color: #b44736; }
@media (max-width: 900px) {
  .battle-arena { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); max-width: 720px; }
  .arena-forecast { grid-column: 1 / -1; grid-row: auto; }
}
@media (max-width: 600px) {
  .battle-header { gap: 8px; padding-left: 12px; padding-right: 12px; }
  h1 { font-size: 1.0625rem; } .battle-relics { max-width: 28%; }
  .battle-stage { padding: 10px 12px; }
  .battle-arena { gap: 10px 20px; }
  .battle-player-character { --character-width: 82px; --character-height: 68px; }
  .battle-player-character :deep(.battle-character__stage) { height: 86px; }
  .battle-player-character :deep(.battle-character__name) { margin-top: 0; font-size: 0.75rem; line-height: 1rem; }
  .battle-player-character :deep(.battle-character__action-label) { display: none; }
  .arena-player, .arena-enemy { gap: 5px; }
  .arena-info { gap: 4px; }
  .enemy-portrait { width: 84px; height: 84px; }
  h2 { font-size: 1.0625rem; }
  .block-display { padding: 2px 7px; font-size: 0.6875rem; }
  .selection-preview { padding: 7px 12px 0; font-size: 0.75rem; }
  .selection-preview small { flex-basis: 100%; }
  .hand-zone { padding-left: 12px; padding-right: 12px; }
  .battle-bottom { padding: 6px 12px; flex-wrap: wrap; }
  .energy-display { flex: 1; }
  .confirm-card-btn, .end-turn-btn { flex: 1; padding: 8px; }
  .pile-btn { padding: 8px; }
  .energy-display strong { font-size: 1.0625rem; }
}
@media (max-width: 390px) { .energy-display { flex-basis: calc(60% - 8px); } .pile-btn { flex-basis: 35%; } .confirm-card-btn, .end-turn-btn { flex-basis: 40%; } }
@media (max-height: 500px) and (orientation: landscape) {
  .battle-header { min-height: 52px; padding: 4px 12px; }
  .battle-header .eyebrow { display: none; }
  h1 { font-size: 1.0625rem; }
  .battle-stage { padding: 6px 10px; }
  .stage-scroll-hint { display: block; font-size: 0.625rem; line-height: 0.75rem; margin: 0 0 4px; }
  .battle-arena { grid-template-columns: minmax(105px, .65fr) minmax(105px, .65fr) minmax(230px, 1.7fr); align-items: start; gap: 8px; max-width: none; }
  .arena-player, .arena-enemy { gap: 3px; }
  .battle-player-character { --character-width: 64px; --character-height: 40px; }
  .battle-player-character :deep(.battle-character__stage) { height: 58px; }
  .battle-player-character :deep(.battle-character__name) { margin: 0; line-height: 0.875rem; font-size: 0.6875rem; }
  .battle-player-character :deep(.battle-character__action-label) { display: none; }
  .arena-info { gap: 3px; }
  .arena-info :deep(.hp-bar-bg), .arena-enemy :deep(.hp-bar-bg) { height: 1.125rem; }
  .enemy-portrait { width: 64px; height: 64px; padding: 2px; }
  h2 { font-size: 1rem; line-height: 1.1875rem; }
  .block-display { font-size: 0.625rem; line-height: 0.875rem; padding: 1px 6px; }
  .arena-forecast { grid-column: 3; grid-row: 1; align-self: start; max-height: 140px; overflow-y: auto; padding: 6px 8px; }
  .arena-forecast :deep(.intent-heading) { font-size: 0.8125rem; }
  .arena-forecast :deep(.intent-effect) { margin: 2px 0; font-size: 0.75rem; }
  .arena-forecast :deep(.intent-target) { margin: 2px 0; font-size: 0.6875rem; }
  .arena-forecast :deep(small) { font-size: 0.625rem; }
  .battle-dock { display: grid; grid-template-columns: minmax(0, 1fr) 238px; grid-template-rows: auto minmax(0, 1fr); height: 154px; max-height: 154px; overflow: hidden; padding: 0 max(12px, env(safe-area-inset-right)) env(safe-area-inset-bottom) max(12px, env(safe-area-inset-left)); }
  .selection-preview { grid-column: 1 / -1; width: 100%; margin: 0; padding: 4px 0; font-size: 0.6875rem; max-height: 48px; overflow-y: auto; }
  .selection-preview small { flex-basis: auto; font-size: 0.625rem; }
  .hand-zone { grid-column: 1; grid-row: 2; width: 100%; margin: 0; padding: 6px 8px 8px 0; align-items: center; }
  .hand-zone :deep(.card-art), .hand-zone :deep(.card-desc) { display: none; }
  .hand-zone :deep(.game-card) { min-height: 82px; height: 82px; flex-basis: 112px; width: 112px; gap: 4px; }
  .hand-zone :deep(.card-name) { padding-left: 20px; font-size: 0.75rem; }
  .battle-bottom { grid-column: 2; grid-row: 2; display: grid; grid-template-columns: 1fr 1fr; gap: 8px; width: 100%; margin: 0; padding: 0 0 8px 8px; align-content: center; }
  .energy-display { font-size: 0.75rem; margin: 0; }
  .energy-display strong { font-size: 0.9375rem; }
  .pile-btn { padding: 6px; font-size: 0.6875rem; }
  .confirm-card-btn, .end-turn-btn { padding: 6px 4px; font-size: 0.8125rem; min-height: 48px; }
}
@media (prefers-reduced-motion: reduce) { *, *::before, *::after { scroll-behavior: auto; transition: none !important; } }
</style>
