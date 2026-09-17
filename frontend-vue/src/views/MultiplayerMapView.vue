<!-- ====== 多人模式地图视图 ====== -->
<template>
  <div class="map-screen">
    <!-- 顶部信息栏 -->
    <div class="map-top-bar">
      <!-- 玩家状态栏 -->
      <div class="players-bar">
        <div
          v-for="player in room?.players"
          :key="player.userId"
          class="player-chip"
          :class="{ 'is-me': player.userId === currentUserId, dead: (player.hp ?? 0) <= 0 }"
        >
          <span class="player-emoji">{{ charEmoji(player.characterClass) }}</span>
          <span class="player-name">{{ player.username }}</span>
          <span class="player-hp">❤{{ player.hp ?? '-' }}/{{ player.maxHp ?? '-' }}</span>
          <span class="player-gold">🪙{{ player.gold ?? 0 }}</span>
        </div>
      </div>
      <div class="floor-bar">
        <span class="floor-label">🏰 第 {{ room?.floor || 1 }} 层 / {{ room?.maxLayer || 3 }} 层</span>
        <div class="top-actions">
          <button class="btn-small" @click="goRoom">🏠 房间</button>
        </div>
      </div>
    </div>

    <p class="operation-status" role="status">{{ moving ? '正在前往下一站…' : eventSubmitting ? '正在确认本次操作…' : roomStore.connected ? (isHost ? '请选择下一站' : '由房主选择路线') : '实时连接恢复中，正在同步权威状态' }}</p>
    <!-- 可滑动的地图容器 -->
    <div class="map-scroll-wrapper" ref="scrollWrapper">
      <div class="map-container" :style="{ ...wrapperStyle, backgroundImage: `linear-gradient(#fff6da66,#e6f3dd77),url(${sceneImageUrl('journey', MAP_WIDTH < 600 ? 640 : 960)})` }" ref="mapContainer">
        <div class="map-graph-layer" :style="{ width: MAP_WIDTH + 'px', minHeight: mapHeight + 'px' }">
          <!-- SVG 连线层 -->
          <svg
            class="map-svg"
            :viewBox="`0 0 ${MAP_WIDTH} ${mapHeight}`"
            :style="{ width: MAP_WIDTH + 'px', height: mapHeight + 'px' }"
          >
            <line
              v-for="(line, i) in connectionLines"
              :key="i"
              :x1="line.x1"
              :y1="line.y1"
              :x2="line.x2"
              :y2="line.y2"
              stroke="rgba(242,169,0,0.3)"
              stroke-width="2"
              stroke-dasharray="6,4"
            />
          </svg>

          <!-- 地图节点 -->
          <MapNodeComponent
            v-for="node in mapNodes"
            :key="node.id"
            :node="node"
            :busy="moving"
            :is-current="currentNode?.id === node.id"
            :x="nodePositions[node.id]?.x ?? 0"
            :y="nodePositions[node.id]?.y ?? 0"
            @move="onMoveNode"
          />

          <!-- 队伍标记 -->
          <div
            v-if="currentNode && nodePositions[currentNode.id]"
            class="map-player-marker"
            :style="{
              left: (nodePositions[currentNode.id].x - 20) + 'px',
              top: (nodePositions[currentNode.id].y - 48) + 'px',
            }"
          >
            <span class="map-avatar-emoji">👥</span>
          </div>

          <!-- 底部起点标签 -->
          <div class="map-label map-label-start">▼ 第{{ room?.floor || 1 }}层出发</div>
          <!-- 顶部Boss标签 -->
          <div v-if="maxRow > 0" class="map-label map-label-boss">👑 Boss</div>
        </div>
      </div>
    </div>

    <!-- 事件弹窗 -->
    <div
      v-if="eventModalVisible"
      class="modal-overlay battle-theme"
      :class="{ 'temple-overlay': currentEventType === 'shop' }"
      @click.self="onEventBackdropClick"
    >
      <TempleShop
        v-if="currentEventType === 'shop'"
        :cards="shopCards"
        :gold="myGold"
        :bought-indices="boughtIndices"
        :price="shopPrice"
        :busy="eventSubmitting"
        @buy="buyCard"
        @forward="onEventClose"
      />
      <div v-else class="modal-box">
        <h3>{{ eventTitle }}</h3>
        <p v-if="eventMessage" v-html="eventMessage"></p>
        <div v-if="currentEventType === 'treasure' && treasureRelic" class="treasure-artifact">
          <ResponsiveImage :src="relicImgUrl(treasureRelic.name)" :alt="treasureRelic.name" sizes="280px" object-fit="contain" />
          <h4>{{ treasureRelic.name }}</h4>
          <ArtifactDescription kind="relic" :name="treasureRelic.name" :effect="treasureRelic.description" />
        </div>
        <BranchEventChoices v-if="currentEventType === 'random' && branchEvent" :event="branchEvent" :busy="eventSubmitting" :can-choose="isHost" @choose="chooseBranch" />
        <button v-if="currentEventType === 'random' && !branchEvent && isHost" class="btn-primary" :disabled="eventSubmitting" @click="chooseBranch('leave')">离开</button>

        <!-- 篝火 -->
        <div v-if="currentEventType === 'bonfire'" class="bonfire-content">
          <p v-if="bonfireUpgradesLeft > 0">🔥 剩余升级次数: {{ bonfireUpgradesLeft }} 张</p>
          <p v-else>🔥 升级次数已用完</p>
          <div class="card-grid" v-if="myDeck.length">
            <MiniCard
              v-for="(card, i) in myDeck"
              :key="i"
              :card="card"
              clickable
              :disabled="bonfireUpgradesLeft <= 0 || eventSubmitting"
              :selected="selectedUpgrade === i"
              @click="doUpgrade(i)"
            />
          </div>
        </div>

        <!-- 休息 -->
        <button v-if="currentEventType === 'rest'" class="btn-primary" :disabled="eventSubmitting" @click="doRest">休息回血</button>

        <button v-if="currentEventType !== 'random' || !isHost" class="btn-primary" :disabled="eventSubmitting" @click="onEventClose">{{ selectedUpgrade >= 0 ? '继续前进（确认升级）' : continueText }}</button>
        <button v-if="selectedUpgrade >= 0" class="btn-small" :disabled="eventSubmitting" @click="selectedUpgrade = -1">取消选择</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useMapLayout } from '@/composables/useMapLayout'
import { useRouter, useRoute } from 'vue-router'
import { useRoomStore } from '@/stores/room'
import { useUiStore } from '@/stores/ui'
import { getCurrentUsername } from '@/api/room'
import { EMOJI_MAP, sceneImageUrl, preloadScene, relicImgUrl } from '@/constants/images'
import type { MapNode, Card, Relic, EventPreview } from '@/types'
import MapNodeComponent from '@/components/MapNodeComponent.vue'
import TempleShop from '@/components/TempleShop.vue'
import BranchEventChoices from '@/components/BranchEventChoices.vue'
import ArtifactDescription from '@/components/ArtifactDescription.vue'
import ResponsiveImage from '@/components/ResponsiveImage.vue'
import MiniCard from '@/components/MiniCard.vue'

const router = useRouter()
const route = useRoute()
const roomStore = useRoomStore()
const ui = useUiStore()

const moving = ref(false)

// 本地状态
const eventSubmitting = ref(false)
const branchEvent = ref<EventPreview | null>(null)
const selectedUpgrade = ref(-1)
const eventModalVisible = ref(false)
const currentEventType = ref('')
const eventTitle = ref('')
const eventMessage = ref('')
const treasureRelic = ref<Relic | null>(null)
const continueText = ref('继续')
const shopCards = ref<Card[]>([])
const boughtIndices = ref<Set<number>>(new Set())
const deckCards = ref<Card[]>([])
const bonfireUpgradesLeft = ref(0)
const scrollWrapper = ref<HTMLElement | null>(null)

const room = computed(() => roomStore.room)
const mapNodes = computed(() => room.value?.map || [])
const currentNode = computed(() => room.value?.currentNode || null)
const currentUserId = computed(() => getCurrentUsername())

const myPlayer = computed(() => {
  if (!room.value || !currentUserId.value) return null
  return room.value.players.find(p => p.userId === currentUserId.value)
})
const myGold = computed(() => myPlayer.value?.gold || 0)
const myDeck = computed(() => myPlayer.value?.deck || [])
const shopPrice = computed(() =>
  myPlayer.value?.relics?.some(relic => relic.name === '通关文牒') ? 40 : 50
)

function onEventBackdropClick() {
  if (!eventSubmitting.value && !['shop', 'bonfire', 'random'].includes(currentEventType.value)) onEventClose()
}
const isHost = computed(() => {
  if (!room.value || !currentUserId.value) return false
  return room.value.hostUserId === currentUserId.value
})

function charEmoji(charClass: string | null): string {
  if (!charClass) return '🧙'
  return EMOJI_MAP[charClass as keyof typeof EMOJI_MAP] || '🧙'
}

const { MAP_WIDTH, maxRow, mapHeight, nodePositions, connectionLines, wrapperStyle, scrollToCurrentNode } = useMapLayout(mapNodes, currentNode, scrollWrapper)

// 交互逻辑
async function onMoveNode(node: MapNode) {
  if (moving.value) return
  if (!isHost.value) {
    ui.showToast('只有房主才能选择路线')
    return
  }
  moving.value = true
  if (node.type === 'BATTLE' || node.type === 'BOSS') preloadScene(room.value?.floor === 1 ? 'blackwind' : room.value?.floor === 2 ? 'firemountain' : 'lionridge', MAP_WIDTH.value < 600 ? 640 : 960)
  try {
    const eventType = await roomStore.moveToNode(node.id)
    if (eventType === 'battle' || eventType === 'boss_battle') {
      // 开始战斗并跳转到战斗页面
      await roomStore.startBattle()
      router.push(`/room/${room.value?.code}/battle`)
    } else {
      currentEventType.value = eventType
      eventModalVisible.value = true
      await handleEvent(eventType)
    }
  } catch (e: any) {
    console.error('Move failed:', e)
    ui.showToast('移动失败: ' + (e?.message || '未知错误'))
  } finally { moving.value = false }
}

async function handleEvent(et: string) {
  if (eventSubmitting.value) return
  eventSubmitting.value = true
  try {
  selectedUpgrade.value = -1
  boughtIndices.value = new Set()
  shopCards.value = []
  eventMessage.value = ''
  treasureRelic.value = null

  switch (et) {
    case 'rest':
      eventTitle.value = '🏕️ 休息点'
      continueText.value = '离开'
      break
    case 'treasure':
      eventTitle.value = '💎 宝箱'
      try {
        const result = await roomStore.handleEvent('open')
        if (result?.message) eventMessage.value = result.message
        if (result?.relic) {
          treasureRelic.value = result.relic
          eventMessage.value = '获得宝物'
        }
      } catch (error: any) { eventMessage.value = error?.message || '宝箱读取失败'; ui.showToast(eventMessage.value) }
      continueText.value = '继续'
      break
    case 'shop':
      eventTitle.value = '土地庙'
      eventMessage.value = '香火照山门，选一张卡牌补充行囊。'
      try {
        const result = await roomStore.handleEvent('browse')
        if (result?.shopCards) shopCards.value = result.shopCards
      } catch (error: any) { eventMessage.value = error?.message || '商店读取失败'; ui.showToast(eventMessage.value) }
      continueText.value = '继续前进'
      break
    case 'bonfire':
      eventTitle.value = '🔥 篝火'
      bonfireUpgradesLeft.value = room.value?.bonfireUpgradesLeft || 0
      deckCards.value = myDeck.value
      continueText.value = '离开'
      break
    case 'random':
      eventTitle.value = '❓ 神秘事件'
      try {
        const result = await roomStore.handleEvent('trigger')
        branchEvent.value = result?.storyEvent?.event || null
        eventTitle.value = branchEvent.value?.title || eventTitle.value
        if (result?.message) eventMessage.value = result.message
      } catch (error: any) { eventMessage.value = error?.message || '事件读取失败'; ui.showToast(eventMessage.value) }
      continueText.value = '继续'
      break
    default:
      eventTitle.value = '事件'
      continueText.value = '继续'
  }
  } finally { eventSubmitting.value = false }
}

async function doRest() {
  if (eventSubmitting.value) return
  eventSubmitting.value = true
  try {
    const result = await roomStore.handleEvent('rest')
    if (result?.message) eventMessage.value = result.message
  } catch (error: any) { ui.showToast(error?.message || '休息失败') }
  finally { eventSubmitting.value = false }
}

async function chooseBranch(optionId: string) {
  if (eventSubmitting.value || !isHost.value) return
  eventSubmitting.value = true
  try {
    const result = await roomStore.handleEvent(optionId)
    branchEvent.value = result?.storyEvent?.event || branchEvent.value
    if (branchEvent.value?.resolved || optionId === 'leave') eventModalVisible.value = false
  } catch (error: any) { ui.showToast(error?.message || '事件选择失败') }
  finally { eventSubmitting.value = false }
}

async function buyCard(card: Card, index: number) {
  if (eventSubmitting.value) return
  eventSubmitting.value = true
  try {
    const result = await roomStore.handleEvent('buy', { cardId: card.id })
    if (result?.bought) {
      boughtIndices.value.add(index)
      ui.showToast('购买成功: ' + card.name)
    } else if (result?.error) {
      ui.showToast(result.error)
    }
  } catch (error: any) { ui.showToast(error?.message || '购买失败') }
  finally { eventSubmitting.value = false }
}

function doUpgrade(cardIndex: number) {
  if (!eventSubmitting.value && bonfireUpgradesLeft.value > 0) selectedUpgrade.value = cardIndex
}

async function onEventClose() {
  if (eventSubmitting.value) return
  eventSubmitting.value = true
  try {
    if (currentEventType.value === 'bonfire' && selectedUpgrade.value >= 0) {
      const result = await roomStore.handleEvent('upgrade', { cardIndex: selectedUpgrade.value })
      if (!result || result.error) throw new Error(result?.error || '升级失败')
      selectedUpgrade.value = -1
      bonfireUpgradesLeft.value = result.bonfireUpgradesLeft ?? room.value?.bonfireUpgradesLeft ?? 0
      if (result.message) ui.showToast(result.message)
      if (bonfireUpgradesLeft.value > 0) return
    }
    await roomStore.refreshRoomState()
    eventModalVisible.value = false
  } catch (e: any) { ui.showToast(e?.message || '操作失败，请重试') }
  finally { eventSubmitting.value = false }
}

function goRoom() {
  router.push('/room')
}

onMounted(async () => {
  const code = route.params.code as string
  if (code && roomStore.room?.code !== code) {
    // 从URL恢复房间状态
    try {
      await roomStore.openRoom(code)
    } catch {
      router.replace('/room')
      return
    }
  }
  if (!roomStore.room) {
    router.replace('/room')
    return
  }
  scrollToCurrentNode()
  if (roomStore.room.currentNode?.type === 'RANDOM' && !roomStore.room.storyEvent?.event) {
    currentEventType.value = 'random'
    eventModalVisible.value = true
    await handleEvent('random')
  }
})

// Guests and reconnected clients must follow the authoritative encounter without issuing start again.
let followingBattle = false
watch(() => [room.value?.code, room.value?.status, roomStore.battleInfo?.battleId], async () => {
  const code = room.value?.code
  if (followingBattle || !code || room.value?.status !== 'IN_BATTLE' || route.params.code !== code) return
  followingBattle = true
  try {
    await roomStore.refreshBattleState()
    if (route.params.code === code && room.value?.code === code && room.value.status === 'IN_BATTLE' && roomStore.battleInfo) {
      await router.replace(`/room/${code}/battle`)
    }
  } finally { followingBattle = false }
}, { immediate: true })

watch(() => room.value?.storyEvent?.event, event => {
  if (!event) return
  branchEvent.value = event
  eventTitle.value = event.title
  eventMessage.value = event.text
  if (!event.resolved) { currentEventType.value = 'random'; eventModalVisible.value = true }
  else if (currentEventType.value === 'random') eventModalVisible.value = false
}, { immediate: true })

watch(() => room.value?.status, status => {
  const code = room.value?.code
  if (status === 'FINISHED' && code && route.params.code === code) void router.replace(`/room/${code}/complete`)
}, { immediate: true })
</script>

<style scoped>
.treasure-artifact { width: min(100%, 320px); margin: 16px auto; padding: 12px; border: 1px solid var(--gold); border-radius: 12px; }
.treasure-artifact h4 { margin: 12px 0; color: var(--gold); }
.mini-card.selected { outline: 3px solid #f2a900; }
.map-screen {
  display: flex;
  flex-direction: column;
  height: 100dvh;
}

.map-top-bar {
  display: flex;
  flex-direction: column;
  padding: 8px 16px;
  background: var(--bg-panel);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  gap: 6px;
}

.players-bar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.player-chip {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: var(--bg-card);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 8px;
  font-size: 0.75rem;
}

.player-chip.is-me {
  border-color: var(--gold);
  background: rgba(242, 169, 0, 0.15);
}

.player-chip.dead {
  opacity: 0.5;
}

.player-emoji {
  font-size: 1.125rem;
}

.floor-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.floor-label {
  font-size: 0.875rem;
  font-weight: bold;
  color: var(--gold);
}

.top-actions {
  display: flex;
  gap: 8px;
}

.map-scroll-wrapper {
  flex: 1;
  min-height: 120px;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 10px;
  /* The scrollable map itself owns the artwork so the scene moves with the route. */
  background-color: var(--bg-dark);
}

.map-container {
  background-color: #e5ead8;
  background-size: 100% auto;
  background-position: center;
  background-repeat: repeat-y;
  border-radius: 18px;
  overflow: hidden;
}

.map-graph-layer {
  position: relative;
  margin: 0 auto;
}

.map-svg {
  position: absolute;
  top: 0;
  left: 0;
  pointer-events: none;
}

.map-player-marker {
  width: 40px;
  height: 40px;
  position: absolute;
  z-index: 5;
  pointer-events: none;
  transition: all 0.3s ease;
}

.map-avatar-emoji {
  font-size: 2rem;
  display: block;
  text-align: center;
  line-height: 40px;
  filter: drop-shadow(0 0 6px rgba(242, 169, 0, 0.6));
  animation: glow 1.5s infinite;
}

.map-label {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  font-size: 0.75rem;
  font-weight: bold;
}

.map-label-start { bottom: 0; color: var(--gold); }
.map-label-boss { top: 0; color: var(--red); }

/* 事件弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.temple-overlay {
  padding: 2vh 2vw;
  background: rgba(4, 4, 12, 0.92);
}

.modal-box {
  background: var(--bg-panel);
  border: 2px solid var(--gold);
  border-radius: 12px;
  padding: 24px;
  max-width: 400px;
  width: 90%;
  text-align: center;
}

.modal-box h3 {
  margin-bottom: 12px;
  color: var(--gold);
}

.event-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 12px 0;
}

.bonfire-content {
  margin: 12px 0;
}

.card-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.mini-card {
  padding: 4px 8px;
  background: var(--bg-card);
  border: 1px solid var(--gold);
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.75rem;
}

.mini-card.disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.shop-card-btn.bought {
  opacity: 0.5;
}

.btn-primary {
  margin-top: 12px;
}

/* 移动端适配 */
@media (max-width: 600px) {
  .players-bar { flex-wrap: nowrap; overflow-x: auto; padding: 4px 0; }
  .player-chip { flex: 0 0 auto; display: grid; grid-template-columns: auto auto; min-width: 108px; }
  .player-name { max-width: 80px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .map-top-bar { padding: 6px 10px; gap: 4px; }
  .player-chip { font-size: 0.6875rem; padding: 3px 6px; }
  .player-emoji { font-size: 0.875rem; }
  .floor-label { font-size: 0.75rem; }
  .map-scroll-wrapper { padding: 6px; }
}
</style>
