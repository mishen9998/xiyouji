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

    <!-- 可滑动的地图容器 -->
    <div class="map-scroll-wrapper" ref="scrollWrapper">
      <div class="map-container" :style="wrapperStyle" ref="mapContainer">
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
    <div v-if="eventModalVisible" class="modal-overlay" @click.self="onEventClose">
      <div class="modal-box">
        <h3>{{ eventTitle }}</h3>
        <p v-if="eventMessage" v-html="eventMessage"></p>

        <!-- 商店 -->
        <div v-if="currentEventType === 'shop' && shopCards.length" class="event-actions">
          <button
            v-for="(card, i) in shopCards"
            :key="i"
            class="btn-small shop-card-btn"
            :disabled="boughtIndices.has(i) || myGold < 50"
            :class="{ bought: boughtIndices.has(i) }"
            @click="buyCard(card, i)"
          >
            {{ card.emoji || '' }} {{ card.name }} 🪙50
            <span v-if="boughtIndices.has(i)"> ✓</span>
          </button>
        </div>

        <!-- 篝火 -->
        <div v-if="currentEventType === 'bonfire'" class="bonfire-content">
          <p v-if="bonfireUpgradesLeft > 0">🔥 剩余升级次数: {{ bonfireUpgradesLeft }} 张</p>
          <p v-else>🔥 升级次数已用完</p>
          <div class="card-grid" v-if="myDeck.length">
            <div
              v-for="(card, i) in myDeck"
              :key="i"
              class="mini-card"
              :class="{ disabled: bonfireUpgradesLeft <= 0 }"
              @click="doUpgrade(i)"
            >
              <span>{{ card.emoji || '' }} {{ card.name }}</span>
            </div>
          </div>
        </div>

        <!-- 休息 -->
        <button v-if="currentEventType === 'rest'" class="btn-primary" @click="doRest">休息回血</button>

        <button class="btn-primary" @click="onEventClose">{{ continueText }}</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useRoomStore } from '@/stores/room'
import { useUiStore } from '@/stores/ui'
import { getCurrentUsername } from '@/api/room'
import { EMOJI_MAP } from '@/constants/images'
import type { MapNode, Card } from '@/types'
import MapNodeComponent from '@/components/MapNodeComponent.vue'

const router = useRouter()
const route = useRoute()
const roomStore = useRoomStore()
const ui = useUiStore()

const ROW_HEIGHT = 220
const MAP_WIDTH = Math.max(360, Math.round((typeof window !== 'undefined' ? window.innerWidth : 1200) * 0.9))
const COL_WIDTH = MAP_WIDTH / 4

// 本地状态
const eventModalVisible = ref(false)
const currentEventType = ref('')
const eventTitle = ref('')
const eventMessage = ref('')
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
const isHost = computed(() => {
  if (!room.value || !currentUserId.value) return false
  return room.value.hostUserId === currentUserId.value
})

function charEmoji(charClass: string | null): string {
  if (!charClass) return '🧙'
  return EMOJI_MAP[charClass as keyof typeof EMOJI_MAP] || '🧙'
}

const maxRow = computed(() => {
  let m = 0
  for (const n of mapNodes.value) {
    if (n.row > m) m = n.row
  }
  return m
})

const mapHeight = computed(() => (maxRow.value + 1) * ROW_HEIGHT + 60)
const HORIZONTAL_OFFSET = computed(() => (MAP_WIDTH - 4 * COL_WIDTH) / 2)

const nodePositions = computed(() => {
  const positions: Record<string, { x: number; y: number }> = {}
  for (const n of mapNodes.value) {
    const x = n.type === 'BOSS'
      ? MAP_WIDTH / 2
      : HORIZONTAL_OFFSET.value + n.col * COL_WIDTH + COL_WIDTH / 2
    const y = mapHeight.value - 30 - n.row * ROW_HEIGHT
    positions[n.id] = { x, y }
  }
  return positions
})

const connectionLines = computed(() => {
  const lines: { x1: number; y1: number; x2: number; y2: number }[] = []
  for (const n of mapNodes.value) {
    const from = nodePositions.value[n.id]
    if (!from || !n.connections) continue
    for (const toId of n.connections) {
      const to = nodePositions.value[toId]
      if (!to) continue
      lines.push({ x1: from.x, y1: from.y, x2: to.x, y2: to.y })
    }
  }
  return lines
})

const wrapperStyle = computed(() => ({
  position: 'relative' as const,
  width: '100%',
  maxWidth: 'none',
  height: mapHeight.value + 'px',
  minHeight: mapHeight.value + 'px',
  margin: '0',
}))

// 交互逻辑
async function onMoveNode(node: MapNode) {
  if (!isHost.value) {
    ui.showToast('只有房主才能选择路线')
    return
  }
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
  }
}

async function handleEvent(et: string) {
  boughtIndices.value = new Set()
  shopCards.value = []
  eventMessage.value = ''

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
          eventMessage.value = `获得遗物: ${result.relic.name}<br><small>${result.relic.description}</small>`
        }
      } catch { /* ignore */ }
      continueText.value = '继续'
      break
    case 'shop':
      eventTitle.value = '🏪 商店'
      try {
        const result = await roomStore.handleEvent('browse')
        if (result?.shopCards) shopCards.value = result.shopCards
      } catch { /* ignore */ }
      continueText.value = '离开'
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
        if (result?.message) eventMessage.value = result.message
      } catch { /* ignore */ }
      continueText.value = '继续'
      break
    default:
      eventTitle.value = '事件'
      continueText.value = '继续'
  }
}

async function doRest() {
  try {
    const result = await roomStore.handleEvent('rest')
    if (result?.message) eventMessage.value = result.message
  } catch { /* ignore */ }
}

async function buyCard(card: Card, index: number) {
  try {
    const result = await roomStore.handleEvent('buy', { cardId: card.id })
    if (result?.bought) {
      boughtIndices.value.add(index)
      ui.showToast('购买成功: ' + card.name)
    } else if (result?.error) {
      ui.showToast(result.error)
    }
  } catch { /* ignore */ }
}

async function doUpgrade(cardIndex: number) {
  if (bonfireUpgradesLeft.value <= 0) return
  try {
    const result = await roomStore.handleEvent('upgrade', { cardIndex })
    if (result?.bonfireUpgradesLeft !== undefined) {
      bonfireUpgradesLeft.value = result.bonfireUpgradesLeft
    }
    if (result?.message) {
      ui.showToast(result.message)
    }
  } catch { /* ignore */ }
}

async function onEventClose() {
  eventModalVisible.value = false
  await roomStore.refreshRoomState()
}

function goRoom() {
  router.push('/room')
}

function scrollToBottom() {
  nextTick(() => {
    if (scrollWrapper.value) {
      scrollWrapper.value.scrollTop = scrollWrapper.value.scrollHeight
    }
  })
}

function scrollToCurrentNode() {
  nextTick(() => {
    const wrapper = scrollWrapper.value
    if (!wrapper) return

    const position = currentNode.value
      ? nodePositions.value[currentNode.value.id]
      : undefined
    if (!position) {
      wrapper.scrollTop = wrapper.scrollHeight
      return
    }

    const maxScrollTop = Math.max(0, wrapper.scrollHeight - wrapper.clientHeight)
    const centeredTop = position.y - wrapper.clientHeight / 2
    const targetTop = Math.max(0, Math.min(centeredTop, maxScrollTop))
    wrapper.scrollTo({ top: targetTop, behavior: 'smooth' })
  })
}

onMounted(async () => {
  const code = route.params.code as string
  if (code && roomStore.room?.code !== code) {
    // 从URL恢复房间状态
    try {
      const dto = await roomStore.refreshRoomState()
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
})

watch([mapNodes, currentNode], () => {
  scrollToCurrentNode()
})
</script>

<style scoped>
.map-screen {
  display: flex;
  flex-direction: column;
  height: 100vh;
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
  font-size: 12px;
}

.player-chip.is-me {
  border-color: var(--gold);
  background: rgba(242, 169, 0, 0.15);
}

.player-chip.dead {
  opacity: 0.5;
}

.player-emoji {
  font-size: 18px;
}

.floor-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.floor-label {
  font-size: 14px;
  font-weight: bold;
  color: var(--gold);
}

.top-actions {
  display: flex;
  gap: 8px;
}

.map-scroll-wrapper {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 10px;
  /* The scrollable map itself owns the artwork so the scene moves with the route. */
  background-color: #171527;
}

.map-container {
  background-color: #252139;
  background-image:
    linear-gradient(180deg, rgba(15, 14, 23, 0.28), rgba(15, 14, 23, 0.12) 40%, rgba(15, 14, 23, 0.38)),
    url('/images/宝物/场景/map_background.png');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
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
  font-size: 32px;
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
  font-size: 12px;
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
  gap: 6px;
  justify-content: center;
}

.mini-card {
  padding: 4px 8px;
  background: var(--bg-card);
  border: 1px solid var(--gold);
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
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
  .map-top-bar { padding: 6px 10px; gap: 4px; }
  .player-chip { font-size: 11px; padding: 3px 6px; }
  .player-emoji { font-size: 14px; }
  .floor-label { font-size: 12px; }
  .map-scroll-wrapper { padding: 6px; }
}
</style>
