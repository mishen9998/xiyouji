<!-- ====== 地图主视图 ====== -->
<template>
  <div class="map-screen">
    <!-- 顶部信息栏 -->
    <div class="map-top-bar">
      <div class="player-info-bar">
        <img
          v-if="playerAvatarUrl"
          class="player-avatar-full"
          :src="playerAvatarUrl"
          alt="角色"
        />
        <span v-else class="player-emoji">{{ playerEmoji }}</span>
        <HpBar :hp="player?.hp ?? 0" :max-hp="player?.maxHp ?? 1" />
        <span class="resource">🪙 <span>{{ player?.gold ?? 0 }}</span></span>
        <span class="resource">📦 <span>{{ player?.deckSize ?? 0 }}</span></span>
        <span class="resource">🏯 <span>第{{ currentLayer }}层</span></span>
      </div>
      <div class="map-relics-bar">
        <template v-for="(relic, i) in playerRelics" :key="i">
          <img
            v-if="relicImgUrl(relic.name)"
            class="map-relic-icon"
            :src="relicImgUrl(relic.name)!"
            :alt="relic.name"
            :title="relic.name + ' — ' + relic.description"
          />
          <span
            v-else
            class="map-relic-emoji"
            :title="relic.name"
          >{{ relic.emoji || '💎' }}</span>
        </template>
      </div>
      <div class="top-actions">
        <button class="btn-small" @click="deckModalVisible = true">📋 牌组</button>
        <button class="btn-small" @click="relicsModalVisible = true">💎 宝物</button>
        <button class="btn-small" @click="goHome">🏠 主菜单</button>
      </div>
    </div>

    <!-- 可滑动的地图容器 -->
    <div class="map-scroll-wrapper" ref="scrollWrapper">
      <div class="map-container" :style="wrapperStyle" ref="mapContainer">
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

        <!-- 玩家头像标记 -->
        <div
          v-if="currentNode && nodePositions[currentNode.id]"
          class="map-player-marker"
          :style="{
            left: (nodePositions[currentNode.id].x - 20) + 'px',
            top: (nodePositions[currentNode.id].y - 48) + 'px',
          }"
        >
          <img
            v-if="playerAvatarUrl"
            class="map-avatar-img"
            :src="playerAvatarUrl"
            alt="玩家"
          />
          <span v-else class="map-avatar-emoji">{{ playerEmoji }}</span>
        </div>

        <!-- 底部起点标签 -->
        <div class="map-label map-label-start">▼ 第{{ currentLayer }}层出发</div>
        <!-- 顶部Boss标签 -->
        <div v-if="maxRow > 0" class="map-label map-label-boss">👑 Boss</div>
      </div>
    </div>

    <!-- 事件弹窗 -->
    <EventModal
      v-model:visible="eventModalVisible"
      :event-type="currentEventType"
      @close="onEventClose"
    />

    <!-- 牌组弹窗 -->
    <DeckModal v-model:visible="deckModalVisible" mode="deck" />

    <!-- 宝物弹窗 -->
    <RelicsModal v-model:visible="relicsModalVisible" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useGameStore } from '@/stores/game'
import { useUiStore } from '@/stores/ui'
import { fullImgUrl, relicImgUrl, EMOJI_MAP } from '@/constants/images'
import type { MapNode } from '@/types'
import HpBar from '@/components/HpBar.vue'
import MapNodeComponent from '@/components/MapNodeComponent.vue'
import EventModal from '@/components/EventModal.vue'
import DeckModal from '@/components/DeckModal.vue'
import RelicsModal from '@/components/RelicsModal.vue'

const router = useRouter()
const store = useGameStore()
const ui = useUiStore()

// 布局常量 - 27行地图使用更紧凑的行距
const ROW_HEIGHT = 70
const COL_WIDTH = 140
const MAP_WIDTH = 600

// 本地状态
const eventModalVisible = ref(false)
const currentEventType = ref('')
const deckModalVisible = ref(false)
const relicsModalVisible = ref(false)
const scrollWrapper = ref<HTMLElement | null>(null)

// 计算属性
const player = computed(() => store.player)
const mapNodes = computed(() => store.mapNodes)
const currentNode = computed(() => store.currentNode)
const currentLayer = computed(() => store.currentLayer)

const playerAvatarUrl = computed(() => {
  const charClass = store.selectedCharacter || store.player?.characterClass
  return charClass ? fullImgUrl(charClass) : null
})

const playerEmoji = computed(() => {
  const charClass = store.selectedCharacter || store.player?.characterClass
  return charClass ? (EMOJI_MAP[charClass] || '🐵') : '🐵'
})

const playerRelics = computed(() => store.player?.relics ?? [])

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
    const x = HORIZONTAL_OFFSET.value + n.col * COL_WIDTH + COL_WIDTH / 2
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
  maxWidth: MAP_WIDTH + 'px',
  minHeight: mapHeight.value + 'px',
  margin: '0 auto',
}))

// 交互逻辑
async function onMoveNode(node: MapNode) {
  try {
    const eventType = await store.moveToNode(node.id)
    if (eventType === 'battle' || eventType === 'boss_battle') {
      router.push('/battle')
    } else {
      currentEventType.value = eventType
      eventModalVisible.value = true
    }
  } catch (e: any) {
    console.error('Move failed:', e)
    ui.showToast('移动失败: ' + (e?.message || '未知错误'))
  }
}

async function onEventClose() {
  eventModalVisible.value = false
  await store.refreshState()
}

function goHome() {
  router.push('/')
}

// 滚动到底部（起点在底部）
function scrollToBottom() {
  nextTick(() => {
    if (scrollWrapper.value) {
      scrollWrapper.value.scrollTop = scrollWrapper.value.scrollHeight
    }
  })
}

onMounted(async () => {
  // 刷新页面后 sessionId 可能丢失，先尝试从 localStorage 恢复
  if (!store.sessionId) {
    try {
      const restored = await store.loadSavedSession()
      if (restored && restored.player) {
        // 恢复成功，继续渲染地图
      } else {
        router.replace('/')
        return
      }
    } catch (e) {
      console.error('Restore session failed:', e)
      router.replace('/')
      return
    }
  }
  scrollToBottom()
})

watch(mapNodes, () => {
  scrollToBottom()
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

.player-info-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.player-avatar-full {
  width: 36px;
  height: 44px;
  object-fit: cover;
  object-position: top center;
  border-radius: 6px;
  border: 2px solid var(--gold);
  box-shadow: 0 0 8px rgba(242, 169, 0, 0.3);
}

.player-emoji {
  font-size: 24px;
}

.resource {
  font-size: 13px;
  white-space: nowrap;
}

.map-relics-bar {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  align-items: center;
  min-height: 32px;
}

.map-relic-icon {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  object-fit: cover;
  border: 1px solid rgba(242, 169, 0, 0.3);
  cursor: default;
}

.map-relic-emoji {
  font-size: 20px;
}

.top-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.map-scroll-wrapper {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 10px;
  scrollbar-width: thin;
  scrollbar-color: rgba(242, 169, 0, 0.3) transparent;
}

.map-scroll-wrapper::-webkit-scrollbar {
  width: 6px;
}

.map-scroll-wrapper::-webkit-scrollbar-track {
  background: transparent;
}

.map-scroll-wrapper::-webkit-scrollbar-thumb {
  background: rgba(242, 169, 0, 0.3);
  border-radius: 3px;
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

.map-avatar-img {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 3px solid var(--gold);
  box-shadow: 0 0 12px rgba(242, 169, 0, 0.6);
  object-fit: cover;
  object-position: center top;
  animation: glow 1.5s infinite;
}

.map-avatar-emoji {
  font-size: 32px;
  display: block;
  text-align: center;
  line-height: 40px;
  filter: drop-shadow(0 0 6px rgba(242, 169, 0, 0.6));
}

.map-label {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  font-size: 12px;
  font-weight: bold;
}

.map-label-start {
  bottom: 0;
  color: var(--gold);
}

.map-label-boss {
  top: 0;
  color: var(--red);
}

/* ====== 移动端适配 ====== */
@media (max-width: 600px) {
  .map-top-bar {
    padding: 6px 10px;
    gap: 4px;
  }
  .player-info-bar {
    gap: 8px;
  }
  .player-avatar-full {
    width: 28px;
    height: 34px;
  }
  .resource {
    font-size: 11px;
  }
  .map-relic-icon {
    width: 24px;
    height: 24px;
  }
  .map-relic-emoji {
    font-size: 16px;
  }
  .top-actions {
    gap: 6px;
  }
  .btn-small {
    padding: 4px 10px;
    font-size: 12px;
  }
  .map-scroll-wrapper {
    padding: 6px;
  }
  .map-player-marker {
    width: 32px;
    height: 32px;
  }
  .map-avatar-img {
    width: 32px;
    height: 32px;
    border-width: 2px;
  }
  .map-avatar-emoji {
    font-size: 24px;
    line-height: 32px;
  }
}
</style>
