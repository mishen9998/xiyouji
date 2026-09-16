<template>
  <div class="mp-battle">
    <ResponsiveImage v-if="battle" class="battle-backdrop" :src="sceneImageUrl(roomStore.room?.floor === 1 ? 'blackwind' : roomStore.room?.floor === 2 ? 'firemountain' : 'lionridge')" alt="" emoji="" sizes="100vw" object-fit="cover" critical aria-hidden="true" />
    <header class="floor-bar">
      <div><span class="eyebrow">同行 · 共战</span><h1>第 {{ roomStore.room?.floor || 1 }} 层</h1></div>
      <span class="connection-state" role="status" :class="{ offline: !roomStore.connected }">{{ roomStore.connected ? '实时已连接' : '实时连接恢复中 · 定时同步' }}</span>
      <span class="turn-num">第 {{ battle?.turnNumber || 1 }} 回合</span>
    </header>
    <main v-if="battle" class="battle-stage">
      <p class="stage-scroll-hint">↕ 上下滑动战场，查看完整预告与队伍</p>
      <section class="enemy-section" aria-label="敌人与攻击预告">
        <div class="enemy-card">
          <div class="enemy-avatar" :class="{ boss: battle.enemy.isBoss }">
            <ResponsiveImage v-if="enemyImgUrl(battle.enemy.name)" :src="enemyImgUrl(battle.enemy.name)" :alt="battle.enemy.name" :emoji="battle.enemy.emoji || '👹'" sizes="(max-width: 600px) 90px, 136px" :object-fit="battle.enemy.isBoss ? 'contain' : 'cover'" critical />
            <span v-else>{{ battle.enemy.emoji || '👹' }}</span>
            <span v-if="battle.enemy.isBoss" class="boss-seal">关主</span>
          </div>
          <div class="enemy-info">
            <h2 class="enemy-name">{{ battle.enemy.name }}</h2>
            <HpBar :hp="battle.enemy.hp" :max-hp="battle.enemy.maxHp" is-enemy width="100%" />
            <div class="enemy-stats"><span v-if="battle.enemy.block">格挡 {{ battle.enemy.block }}</span><span v-if="battle.enemy.strength">力量 {{ battle.enemy.strength }}</span></div>
            <div class="player-buffs"><span v-for="(val, buff) in battle.enemy.buffs" :key="buff">{{ statusNames[buff] || buff }} {{ val }}</span></div>
          </div>
        </div>
        <EnemyIntent v-if="!battle.battleOver" :enemy="battle.enemy" :players="battle.players" :focus-user-id="currentUsername" />
      </section>
      <section class="players-section" aria-label="队伍状态">
        <article v-for="player in battle.players" :key="player.userId" class="player-panel"
          :class="{ dead: !player.alive, 'is-me': player.userId === currentUsername, targeted: isTargeted(player.userId) && battle.playerTurn }">
          <div class="player-header">
            <ResponsiveImage v-if="characterAvatarUrl(player.characterClass || '')" class="player-avatar" :src="characterAvatarUrl(player.characterClass || '')" :alt="charName(player.characterClass)" :emoji="charEmoji(player.characterClass)" sizes="38px" object-fit="cover" />
            <span v-else class="player-emoji">{{ charEmoji(player.characterClass) }}</span>
            <div class="player-identity"><strong class="player-name">{{ player.username }}</strong><span class="player-char-name">{{ charName(player.characterClass) }}</span></div>
            <span v-if="player.userId === currentUsername" class="me-tag">我</span>
          </div>
          <HpBar :hp="player.hp" :max-hp="player.maxHp" width="100%" />
          <div class="player-stats"><span>法力 {{ player.energy }}/{{ player.maxEnergy }}</span><span>格挡 {{ player.block }}</span></div>
          <div class="player-buffs"><span v-for="(val, buff) in player.buffs" :key="buff">{{ statusNames[buff] || buff }} {{ val }}</span></div>
          <span class="player-state">{{ !player.alive ? '已倒下' : player.endedTurn ? '已结束回合' : isTargeted(player.userId) ? '敌人目标 · 可用格挡应对' : '行动中' }}</span>
        </article>
      </section>
      <details v-if="battle.combatLog?.length" class="combat-log"><summary>战斗记录</summary><p v-for="(log, i) in battle.combatLog.slice(-8)" :key="i">{{ log }}</p></details>
    </main>
    <main v-else class="battle-loading" role="status">正在读取队伍战斗…</main>
    <footer v-if="battle" class="battle-dock" :aria-busy="commandPending">
      <div class="selection-preview" aria-live="polite">
        <template v-if="selectedCard"><strong>{{ selectedCard.name }} · {{ selectedCard.cost }} 法力</strong><span>{{ selectedCard.description }}</span></template>
        <span v-else>师徒齐心，各尽其力。</span><small>{{ actionHint }}</small>
      </div>
      <div class="hand-cards" aria-label="我的手牌，左右滑动浏览">
        <GameCard v-for="card in myPlayer?.hand || []" :key="card.index" class="mp-card"
          :card="toCard(card)" :index="card.index" :can-play="canPlay(card)" :selected="selectedHandIndex === card.index"
          preview @select="selectHand(card.index)" />
        <p v-if="!myPlayer?.hand.length" class="no-cards">{{ myPlayer?.alive ? '手牌已用完，可以结束回合。' : '你已倒下，为队友加油。' }}</p>
      </div>
      <div class="bottom-bar">
        <div class="turn-info"><strong>法力 {{ myPlayer?.energy || 0 }}/{{ myPlayer?.maxEnergy || 0 }}</strong><span class="players-ended">队伍已结束 {{ battle.playersEndedTurn }}/{{ battle.alivePlayerCount }}</span></div>
        <button class="confirm-card-btn" :disabled="!selectedCard || !canPlay(selectedCard)" @click="selectedHandIndex !== null && handlePlayCard(selectedHandIndex)">{{ commandPending ? '同步中…' : '打出此牌' }}</button>
        <button class="btn-end-turn" :disabled="!canEndTurn" @click="handleEndTurn">{{ myPlayer?.endedTurn ? '等待队友' : '结束回合' }}</button>
      </div>
    </footer>
    <div v-if="battle?.battleOver && !battle.rewardsPhase" class="battle-result-overlay">
      <section class="result-modal" role="dialog" aria-modal="true" aria-labelledby="battle-result-title">
        <h2 id="battle-result-title" class="result-title">{{ battle.victory ? '降妖功成' : '西行暂歇' }}</h2>
        <p>{{ battle.victory ? '师徒齐心，降妖除魔！' : '这一程的历练，将成为下一次出发的力量。' }}</p>
        <button class="btn-primary" @click="handleReturn">返回营地</button>
      </section>
    </div>
    <div v-if="battle?.rewardsPhase && battle.victory" class="rewards-overlay">
      <section class="rewards-modal" role="dialog" aria-modal="true" aria-labelledby="rewards-title">
        <span class="eyebrow">战后收获</span><h2 id="rewards-title" class="rewards-title">挑选一张，继续西行</h2>
        <p class="reward-hint">点选卡牌后确认；也可以跳过奖励。</p>
        <div v-if="hasClaimed" class="claimed-status"><p>{{ myClaimedCardName === '__SKIPPED__' ? '已跳过奖励' : '已领取：' + myClaimedCardName }}</p></div>
        <div v-else-if="myRewards.length" class="rewards-cards" aria-label="可选卡牌，左右滑动浏览">
          <button v-for="(card, index) in myRewards" :key="index" class="reward-card" :class="{ selected: selectedReward === card.name }"
            :aria-pressed="selectedReward === card.name" :disabled="rewardSubmitting" @click="selectReward(card.name)">
            <span class="card-cost">{{ card.cost }} 法力</span>
            <ResponsiveImage v-if="cardImgUrl(card.name)" class="reward-art" :src="cardImgUrl(card.name)" alt="" :emoji="card.emoji || '📜'" sizes="145px" object-fit="cover" />
            <span v-else class="card-emoji">{{ card.emoji || '📜' }}</span>
            <strong class="card-name">{{ card.name }}</strong>
            <span class="card-effects"><span v-if="card.damage">伤害 {{ card.damage }}</span><span v-if="card.block">格挡 {{ card.block }}</span></span>
            <span class="card-desc">{{ card.description }}</span>
          </button>
        </div>
        <div v-if="!hasClaimed && currentUsername && battle.rewards?.[currentUsername]" class="reward-actions">
          <button class="btn-primary" :disabled="rewardSubmitting || !selectedReward" @click="confirmReward(false)">{{ rewardSubmitting ? '同步中…' : '确认领取' }}</button>
          <button class="btn-small" :disabled="rewardSubmitting" @click="confirmReward(true)">跳过奖励</button>
        </div>
        <div class="other-players-status">
          <div v-for="player in battle.players" :key="player.userId" class="player-status">
            <span>{{ player.username }}</span><span v-if="battle.claimedRewards?.[player.userId]">{{ battle.claimedRewards[player.userId] === '__SKIPPED__' ? '已跳过' : '已领取' }}</span><span v-else-if="battle.rewards?.[player.userId]">等待选择</span>
          </div>
        </div>
        <div v-if="battle.rewardsHandled" class="next-floor-section">
          <button v-if="roomStore.isHost" class="btn-next-floor" :disabled="rewardSubmitting" @click="handleNextFloor">返回地图</button>
          <p v-else class="waiting-host">等待房主返回地图…</p>
        </div>
      </section>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useRoomStore } from '@/stores/room'
import { useUiStore } from '@/stores/ui'
import { getCurrentUsername } from '@/api/room'
import { enemyImgUrl, characterAvatarUrl, cardImgUrl, CHARACTER_DIR, EMOJI_MAP, sceneImageUrl } from '@/constants/images'
import { intentTargets, statusNames } from '@/components/enemyIntent'
import EnemyIntent from '@/components/EnemyIntent.vue'
import ResponsiveImage from '@/components/ResponsiveImage.vue'
import GameCard from '@/components/GameCard.vue'
import HpBar from '@/components/HpBar.vue'
import type { MultiplayerBattleInfo, MultiplayerPlayerInfo, MultiplayerCardInfo, CharacterClass, Card } from '@/types'

const route = useRoute()
const router = useRouter()
const roomStore = useRoomStore()
const uiStore = useUiStore()
const currentUsername = ref<string | null>(null)
const battle = computed<MultiplayerBattleInfo | null>(() => roomStore.battleInfo)
const commandPending = ref(false)
const selectedHandIndex = ref<number | null>(null)
const myPlayer = computed<MultiplayerPlayerInfo | null>(() => battle.value?.players.find(p => p.userId === currentUsername.value) ?? null)
const selectedCard = computed(() => myPlayer.value?.hand.find(card => card.index === selectedHandIndex.value) ?? null)
const canEndTurn = computed(() => !commandPending.value && !!(battle.value?.playerTurn && !battle.value.battleOver && myPlayer.value?.alive && !myPlayer.value.endedTurn))
const actionHint = computed(() => commandPending.value ? '正在同步本次操作，请稍候…'
  : battle.value?.battleOver ? '战斗已结束，请处理战后奖励。'
  : !myPlayer.value?.alive ? '你已倒下，等待队伍完成本场战斗。'
  : myPlayer.value.endedTurn ? '本回合已结束，等待队友。'
  : !battle.value?.playerTurn ? '敌人正在行动…'
  : selectedCard.value && myPlayer.value.energy < selectedCard.value.cost ? '法力不足，换一张牌或结束回合。'
  : '点选手牌查看效果，再点击「打出此牌」。')
watch(() => [battle.value?.turnNumber, myPlayer.value?.hand.map(c => c.index + ':' + c.name).join(',')].join(':'), () => { selectedHandIndex.value = null })
async function loadBattle(code: string) {
  try {
    if (roomStore.roomCode !== code) await roomStore.openRoom(code)
    await roomStore.refreshBattleState()
    if (!battle.value) { uiStore.showToast('战斗尚未开始'); await router.push('/room') }
  } catch { uiStore.showToast('房间不存在或已结束'); await router.push('/room') }
}
onMounted(() => { currentUsername.value = getCurrentUsername(); void loadBattle(route.params.code as string) })
watch(() => route.params.code, (code, old) => { if (code && code !== old) { selectedHandIndex.value = null; void loadBattle(code as string) } })
watch(() => roomStore.room?.status, (status) => {
  if (status === 'IN_MAP') router.push(`/room/${roomStore.roomCode}/map`)
  else if (status === 'FINISHED') router.push(`/room/${roomStore.roomCode}/complete`)
  else if (status === 'WAITING') router.push('/room')
})
function canPlay(card: MultiplayerCardInfo): boolean {
  return !commandPending.value && !!(battle.value?.playerTurn && !battle.value.battleOver && myPlayer.value?.alive && !myPlayer.value.endedTurn && myPlayer.value.energy >= card.cost)
}
function selectHand(index: number) { if (!commandPending.value) selectedHandIndex.value = index }
async function handlePlayCard(index: number) {
  const card = myPlayer.value?.hand.find(c => c.index === index)
  if (!card || !canPlay(card)) return
  commandPending.value = true
  try { await roomStore.playCard(index); selectedHandIndex.value = null }
  catch { /* Store retains RESULT_UNKNOWN and shows the error; never replay here. */ }
  finally { commandPending.value = false }
}
async function handleEndTurn() {
  if (!canEndTurn.value) return
  commandPending.value = true
  try { await roomStore.endTurn() }
  catch { /* A 409 requires another deliberate user action. */ }
  finally { commandPending.value = false }
}
function handleReturn() { roomStore.reset(); router.push('/menu') }
const myRewards = computed<MultiplayerCardInfo[]>(() => currentUsername.value ? battle.value?.rewards?.[currentUsername.value] ?? [] : [])
const hasClaimed = computed(() => !!(currentUsername.value && battle.value?.claimedRewards?.[currentUsername.value]))
const myClaimedCardName = computed(() => currentUsername.value ? battle.value?.claimedRewards?.[currentUsername.value] ?? '' : '')
const selectedReward = ref('')
const rewardSubmitting = ref(false)
function selectReward(name: string) { if (!rewardSubmitting.value && !hasClaimed.value) selectedReward.value = name }
async function confirmReward(skip: boolean) {
  if (rewardSubmitting.value || hasClaimed.value || (!skip && !selectedReward.value)) return
  rewardSubmitting.value = true
  try { if (skip) await roomStore.skipReward(); else await roomStore.claimReward(selectedReward.value) }
  catch { /* Selection stays local until receipt proves success. */ }
  finally { rewardSubmitting.value = false }
}
async function handleNextFloor() {
  if (rewardSubmitting.value) return
  rewardSubmitting.value = true
  try {
    const result = await roomStore.nextFloor()
    if (!result) throw new Error('房间不存在')
    await router.push(`/room/${roomStore.roomCode}/${result.completed || roomStore.room?.status === 'FINISHED' ? 'complete' : 'map'}`)
  } catch { /* Store displays the error. */ }
  finally { rewardSubmitting.value = false }
}
function charEmoji(cc: CharacterClass | null) { return cc ? EMOJI_MAP[cc] ?? '❓' : '❓' }
function charName(cc: CharacterClass | null) { return cc ? CHARACTER_DIR[cc] ?? '' : '' }
function isTargeted(userId: string) { return !!battle.value && intentTargets(battle.value.enemy, battle.value.players).includes(userId) }
function toCard(card: MultiplayerCardInfo): Card {
  return { ...card, id: card.index, type: card.type ?? 'SKILL', drawCards: 0, upgraded: false }
}
</script>
<style scoped>
.mp-battle { position: relative; isolation: isolate; height: 100vh; height: 100dvh; width: 100%; display: grid; grid-template-rows: auto minmax(0, 1fr) auto; overflow: hidden; color: #283c35; background: radial-gradient(ellipse at 50% 10%, #dbe9d4 0, #f7f1e5 60%); }
.battle-backdrop { position: absolute; inset: 0; width: 100%; height: 100%; opacity: .22; pointer-events: none; z-index: -1; }
.floor-bar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; padding: max(8px, env(safe-area-inset-top)) max(16px, env(safe-area-inset-right)) 8px max(16px, env(safe-area-inset-left)); background: #fffbf1ed; border-bottom: 1px solid #d8cbb0; }
.eyebrow { color: #776549; font-size: 0.6875rem; letter-spacing: .16em; }
h1 { font: 1.25rem var(--font-display); margin: 2px 0 0; }
.connection-state { font-size: 0.75rem; color: #21665b; margin-left: auto; }
.connection-state.offline { color: #984e26; }
.turn-num { color: #776549; font-size: 0.8125rem; }
.battle-stage { min-height: 0; overflow-y: auto; padding: 20px max(16px, env(safe-area-inset-left)) 14px; }
.stage-scroll-hint { display: none; color: #5b6455; font-size: 0.6875rem; margin: 0 0 6px; text-align: center; }
.battle-loading { display: grid; place-content: center; }
.enemy-section { display: grid; grid-template-columns: minmax(240px, .85fr) minmax(280px, 1.15fr); gap: 20px; align-items: center; max-width: 920px; margin: auto; }
.enemy-card { display: flex; align-items: center; gap: 18px; min-width: 0; }
.enemy-avatar { flex-shrink: 0; width: 136px; height: 136px; position: relative; border: 1px solid #b6bda0; border-radius: 50%; padding: 5px; background: #fffaf0; }
.enemy-avatar :deep(.responsive-image), .enemy-avatar :deep(img) { width: 100%; height: 100%; object-fit: cover; border-radius: inherit; }
.enemy-avatar.boss { border-color: #b18a47; border-radius: 16px; background: radial-gradient(ellipse at bottom, #e9e1be, #fffaf066); }
.enemy-avatar.boss :deep(.responsive-image) { background: transparent; border-radius: 0; }
.enemy-avatar.boss :deep(img) { object-fit: contain; border-radius: 0; }
.enemy-avatar > span:not(.boss-seal) { display: grid; place-items: center; font-size: 3.75rem; height: 100%; }
.boss-seal { position: absolute; right: 0; bottom: 0; padding: 4px 8px; border-radius: 4px; background: #b44736; color: #fffaf0; font-size: 0.75rem; }
.enemy-info { flex: 1; min-width: 0; }
.enemy-name { font: 1.5rem var(--font-display); margin: 0 0 12px; }
.enemy-stats, .player-stats { display: flex; flex-wrap: wrap; gap: 6px 10px; font-size: 0.75rem; margin-top: 6px; color: #335f51; }
.players-section { display: grid; grid-template-columns: repeat(auto-fit, minmax(145px, 1fr)); gap: 10px; max-width: 1040px; margin: 18px auto 0; }
.player-panel { min-width: 0; padding: 10px; border: 1px solid #d8cbb0; border-radius: 12px; background: #fffbf1; }
.player-panel.is-me { border-color: #21665b; }
.player-panel.targeted { border: 2px solid #b44736; padding: 9px; }
.player-panel.dead { filter: grayscale(1); background: #e4e2d8; }
.player-header { display: flex; align-items: center; gap: 7px; margin-bottom: 8px; }
.player-avatar { width: 38px; height: 42px; object-fit: cover; object-position: top; border-radius: 7px; }
.player-avatar :deep(img) { object-position: top; }
.player-avatar :deep(.responsive-image__fallback) { min-height: 0; font-size: 1.25rem; }
.player-emoji { font-size: 1.625rem; }
.player-identity { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.player-name { font-size: 0.8125rem; overflow-wrap: anywhere; }
.player-char-name { font-size: 0.6875rem; color: #6b6555; }
.me-tag { margin-left: auto; font-size: 0.6875rem; padding: 2px 5px; border-radius: 4px; color: #21665b; background: #e0ece3; }
.player-buffs { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 5px; font-size: 0.6875rem; }
.player-buffs span { padding: 2px 5px; border-radius: 4px; background: #eee6d5; color: #644d32; }
.player-state { display: block; margin-top: 6px; font-size: 0.6875rem; color: #635c4c; }
.targeted .player-state { color: #963b2d; }
.combat-log { max-width: 1040px; margin: 10px auto 0; color: #5d6555; font-size: 0.75rem; }
.combat-log summary { min-height: 44px; padding: 12px 0; cursor: pointer; }
.combat-log p { margin: 5px 0; }
.battle-dock { max-height: 58dvh; overflow-y: auto; background: #fffbf1f5; border-top: 1px solid #c9c7ad; padding-bottom: env(safe-area-inset-bottom); }
.selection-preview { max-width: 1100px; margin: auto; display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px 12px; padding: 8px 20px 0; font-size: 0.8125rem; }
.selection-preview strong { color: #21665b; }
.selection-preview small { font-size: 0.6875rem; color: #6b6555; }
.hand-cards { max-width: 1100px; margin: auto; display: flex; gap: 10px; padding: 10px 20px 8px; overflow-x: auto; overscroll-behavior-x: contain; }
.hand-cards > :first-child { margin-left: auto; }
.hand-cards > :last-child { margin-right: auto; }
.no-cards { padding: 12px; font-size: 0.8125rem; color: #6b6555; }
.bottom-bar { max-width: 1100px; margin: auto; display: flex; align-items: center; gap: 8px; padding: 8px 20px; }
.turn-info { flex: 1; display: flex; gap: 12px; flex-wrap: wrap; font-size: 0.875rem; color: #21665b; }
.players-ended { color: #6b6555; font-size: 0.75rem; }
button { min-width: 44px; min-height: 44px; border: 1px solid #b8b09a; border-radius: 9px; padding: 8px 14px; color: #314c41; background: #fffaf0; font: inherit; cursor: pointer; touch-action: manipulation; }
button:disabled { opacity: .55; cursor: not-allowed; }
button:focus-visible { outline: 3px solid #b44736; outline-offset: 3px; }
.confirm-card-btn, .btn-end-turn, .btn-primary, .btn-next-floor { min-height: 48px; padding: 10px 22px; background: #21665b; border-color: #21665b; color: #fffdf6; font-weight: 700; }
.btn-end-turn { background: #b44736; border-color: #b44736; }
.battle-result-overlay, .rewards-overlay { position: fixed; inset: 0; padding: max(16px, env(safe-area-inset-top)) max(12px, env(safe-area-inset-right)) max(16px, env(safe-area-inset-bottom)) max(12px, env(safe-area-inset-left)); display: grid; place-items: center; background: #1d332bb3; z-index: 100; }
.rewards-overlay { z-index: 200; }
.result-modal, .rewards-modal { width: min(100%, 850px); min-width: 0; max-height: 100%; overflow-y: auto; border: 1px solid #c4af7e; border-radius: 18px; background: #fffbf1; padding: 28px; text-align: center; }
.result-title, .rewards-title { font: 1.75rem var(--font-display); color: #21665b; margin: 8px 0; }
.reward-hint, .result-modal p { color: #645e4e; font-size: 0.875rem; margin: 10px 0 18px; }
.claimed-status { padding: 12px; background: #e2ece1; border-radius: 8px; color: #21665b; }
.rewards-cards { display: flex; gap: 12px; overflow-x: auto; overscroll-behavior-x: contain; padding: 7px 6px 14px; }
.reward-card { flex: 0 0 145px; width: 145px; display: flex; flex-direction: column; gap: 7px; align-items: center; padding: 10px; border-color: #d1c3a7; font-size: 0.8125rem; }
.reward-card.selected { outline: 3px solid #21665b; outline-offset: 2px; background: #eff3e9; }
.reward-art { width: 100%; height: 86px; border-radius: 6px; object-fit: cover; }
.card-emoji { font-size: 2.25rem; }
.card-cost { color: #21665b; font-size: 0.75rem; }
.card-name { font-family: var(--font-display); font-size: 0.9375rem; }
.card-effects { display: flex; gap: 7px; flex-wrap: wrap; color: #9c4334; font-size: 0.75rem; }
.card-desc { line-height: 1.5; font-size: 0.75rem; }
.reward-actions { display: flex; gap: 12px; justify-content: center; margin: 14px 0; }
.other-players-status { display: flex; flex-wrap: wrap; justify-content: center; gap: 10px; padding: 12px 0; }
.player-status { display: flex; gap: 6px; font-size: 0.75rem; color: #5b6455; }
.waiting-host { color: #645e4e; font-size: 0.875rem; }
@media (max-width: 600px) {
  .floor-bar { padding-left: 12px; padding-right: 12px; gap: 8px; }
  h1 { font-size: 1.0625rem; } .connection-state { font-size: 0.6875rem; }
  .battle-stage { padding: 12px; }
  .enemy-section { grid-template-columns: minmax(0, 1fr); gap: 10px; }
  .enemy-avatar { width: 90px; height: 90px; }
  .enemy-name { font-size: 1.375rem; margin-bottom: 8px; }
  .players-section { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 6px; margin-top: 12px; }
  .player-panel { flex: 0 0 145px; padding: 7px; }
  .player-panel.targeted { padding: 6px; }
  .player-header { margin-bottom: 5px; }
  .player-avatar { width: 24px; height: 24px; }
  .player-char-name { display: none; }
  .player-panel :deep(.hp-bar-bg) { height: 1.125rem; }
  .player-stats { margin-top: 4px; font-size: 0.6875rem; line-height: 0.875rem; gap: 4px 8px; }
  .player-state { margin-top: 4px; font-size: 0.625rem; line-height: 0.75rem; }
  .player-buffs { font-size: 0.625rem; }
  .selection-preview { padding: 7px 12px 0; font-size: 0.75rem; }
  .selection-preview small { flex-basis: 100%; }
  .hand-cards { padding-left: 12px; padding-right: 12px; }
  .bottom-bar { flex-wrap: wrap; padding: 6px 12px; }
  .turn-info { flex-basis: 100%; justify-content: space-between; }
  .confirm-card-btn, .btn-end-turn { flex: 1; padding: 8px; }
  .rewards-modal, .result-modal { padding: 18px 12px; }
  .rewards-title { font-size: 1.4375rem; } .reward-card { flex-basis: 142px; }
}
@media (max-height: 500px) and (orientation: landscape) {
  .floor-bar { min-height: 44px; padding: 4px 12px; }
  .floor-bar .eyebrow { display: none; }
  h1 { font-size: 1.0625rem; }
  .battle-stage { display: grid; grid-template-columns: minmax(0, 1fr) 176px; align-content: start; gap: 4px 10px; padding: 6px 10px; }
  .stage-scroll-hint { display: block; grid-column: 1 / -1; margin: 0; font-size: 0.625rem; line-height: 0.75rem; }
  .enemy-section { grid-column: 1; grid-row: 2; width: 100%; max-width: none; grid-template-columns: 164px minmax(0, 1fr); align-items: start; gap: 8px; margin: 0; }
  .enemy-card { gap: 6px; align-items: flex-start; }
  .enemy-avatar { width: 64px; height: 64px; padding: 2px; }
  .enemy-name { font-size: 1.0625rem; margin: 0 0 5px; }
  .enemy-info :deep(.hp-bar-bg) { height: 1.125rem; }
  .enemy-stats { font-size: 0.625rem; gap: 3px; margin-top: 3px; }
  .enemy-section :deep(.enemy-intent) { max-height: 146px; overflow-y: auto; padding: 6px 8px; }
  .enemy-section :deep(.intent-heading) { font-size: 0.8125rem; }
  .enemy-section :deep(.intent-effect) { margin: 2px 0; font-size: 0.75rem; }
  .enemy-section :deep(.intent-target) { margin: 2px 0; font-size: 0.6875rem; }
  .enemy-section :deep(small) { font-size: 0.625rem; }
  .players-section { grid-column: 2; grid-row: 2; display: flex; width: 100%; gap: 8px; overflow-x: auto; margin: 0; padding-bottom: 6px; }
  .player-panel { flex: 0 0 166px; padding: 7px; }
  .player-panel.targeted { padding: 6px; }
  .player-header { margin-bottom: 5px; }
  .player-avatar { width: 24px; height: 24px; }
  .player-char-name { display: none; }
  .player-panel :deep(.hp-bar-bg) { height: 1.125rem; }
  .player-stats { margin-top: 4px; font-size: 0.6875rem; line-height: 0.875rem; gap: 4px 8px; }
  .player-state { margin-top: 4px; font-size: 0.625rem; line-height: 0.75rem; }
  .combat-log { grid-column: 1 / -1; width: 100%; margin: 0; }
  .battle-dock { display: grid; grid-template-columns: minmax(0, 1fr) 238px; grid-template-rows: auto minmax(0, 1fr); height: 154px; max-height: 154px; overflow: hidden; padding: 0 max(12px, env(safe-area-inset-right)) env(safe-area-inset-bottom) max(12px, env(safe-area-inset-left)); }
  .selection-preview { grid-column: 1 / -1; width: 100%; margin: 0; padding: 4px 0; font-size: 0.6875rem; max-height: 48px; overflow-y: auto; }
  .selection-preview small { flex-basis: auto; font-size: 0.625rem; }
  .hand-cards { grid-column: 1; grid-row: 2; width: 100%; margin: 0; padding: 6px 8px 8px 0; align-items: center; }
  .hand-cards :deep(.card-art), .hand-cards :deep(.card-desc) { display: none; }
  .hand-cards :deep(.game-card) { min-height: 82px; height: 82px; flex-basis: 112px; width: 112px; gap: 4px; }
  .hand-cards :deep(.card-name) { padding-left: 20px; font-size: 0.75rem; }
  .bottom-bar { grid-column: 2; grid-row: 2; flex-wrap: wrap; width: 100%; margin: 0; padding: 0 0 8px 8px; align-content: center; }
  .turn-info { flex-basis: 100%; font-size: 0.75rem; gap: 3px; justify-content: space-between; }
  .players-ended { font-size: 0.625rem; }
  .confirm-card-btn, .btn-end-turn { flex: 1; padding: 6px 4px; font-size: 0.8125rem; min-height: 48px; }
}
@media (prefers-reduced-motion: reduce) { *, *::before, *::after { scroll-behavior: auto; transition: none !important; } }
</style>
