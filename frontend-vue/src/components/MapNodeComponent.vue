<!-- ====== 地图节点组件 ====== -->
<template>
  <div
    class="map-node"
    :class="{
      visited: node.visited,
      current: isCurrent,
      accessible: node.accessible && !node.visited,
      boss: node.type === 'BOSS',
    }"
    :style="{ left: x + 'px', top: y + 'px' }"
    @click="onClick"
  >
    <img v-if="imgUrl" class="node-icon-img" :src="imgUrl" :alt="iconFallback" />
    <span v-else class="node-icon">{{ iconFallback }}</span>
    <span class="node-name">{{ displayName }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MapNode } from '@/types'
import { NODE_ICON, nodeImgUrl } from '@/constants/images'

const props = defineProps<{
  node: MapNode
  isCurrent: boolean
  x: number
  y: number
}>()

const emit = defineEmits<{ move: [node: MapNode] }>()

const imgUrl = computed(() => nodeImgUrl(props.node.type))
const iconFallback = computed(() => NODE_ICON[props.node.type] || '❓')
const displayName = computed(() => props.node.type === 'SHOP' ? '土地庙' : (props.node.name || props.node.type))

function onClick() {
  if (props.node.accessible && !props.node.visited) {
    emit('move', props.node)
  }
}
</script>

<style scoped>
.map-node {
  width: 96px;
  height: 96px;
  border-radius: 12px;
  background: var(--bg-card);
  border: 2px solid rgba(255, 255, 255, 0.08);
  cursor: default;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: absolute;
  transform: translate(-50%, -50%);
  z-index: 2;
  font-size: 12px;
  color: var(--text-muted);
}

.map-node.accessible {
  border-color: rgba(242, 169, 0, 0.4);
  cursor: pointer;
  animation: pulse 2s infinite;
}

.map-node.accessible:hover {
  border-color: var(--gold);
  transform: translate(-50%, -50%) scale(1.08);
  box-shadow: 0 0 16px rgba(242, 169, 0, 0.3);
  background: #3a3450;
}

.map-node.visited {
  opacity: 0.5;
  border-color: rgba(255, 255, 255, 0.04);
  cursor: default;
}

.map-node.current {
  border-color: var(--gold) !important;
  box-shadow: 0 0 20px rgba(242, 169, 0, 0.4) !important;
  animation: glow 1.5s infinite;
}

.map-node.boss {
  width: 128px;
  height: 128px;
  border-color: rgba(232, 93, 117, 0.7);
  border-width: 3px;
  background: radial-gradient(circle at center, rgba(232, 93, 117, 0.3), var(--bg-card) 72%);
  box-shadow: 0 0 22px rgba(232, 93, 117, 0.58), 0 0 42px rgba(242, 169, 0, 0.24);
}

.map-node.boss .node-icon-img {
  width: 76px;
  height: 76px;
}

.map-node.boss .node-icon {
  font-size: 48px;
}

.map-node.boss .node-name {
  color: #ffe4e8;
  font-size: 14px;
  font-weight: 700;
}

.node-icon-img {
  width: 56px;
  height: 56px;
  border-radius: 10px;
  object-fit: cover;
  object-position: center;
  display: block;
}

.map-node.visited .node-icon-img {
  filter: grayscale(0.7) brightness(0.5);
}

.node-icon {
  font-size: 34px;
  margin-bottom: 2px;
}

.node-name {
  font-size: 11px;
  white-space: nowrap;
}

@media (max-width: 600px) {
  .map-node {
    width: 78px;
    height: 78px;
  }

  .node-icon-img {
    width: 44px;
    height: 44px;
  }

  .node-icon {
    font-size: 28px;
  }

  .node-name {
    font-size: 9px;
  }

  .map-node.boss {
    width: 104px;
    height: 104px;
  }

  .map-node.boss .node-icon-img {
    width: 62px;
    height: 62px;
  }

  .map-node.boss .node-icon {
    font-size: 38px;
  }

  .map-node.boss .node-name {
    font-size: 12px;
  }
}
</style>
