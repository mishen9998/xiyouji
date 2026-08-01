<!-- ====== 地图节点组件 ====== -->
<template>
  <div
    class="map-node"
    :class="{
      visited: node.visited,
      current: isCurrent,
      accessible: node.accessible && !node.visited,
      'shop-reopen': isCurrent && node.type === 'SHOP',
      boss: node.type === 'BOSS',
    }"
    :style="{ left: x - 36 + 'px', top: y - 30 + 'px' }"
    @click="onClick"
  >
    <img v-if="imgUrl" class="node-icon-img" :src="imgUrl" :alt="iconFallback" />
    <span v-else class="node-icon">{{ iconFallback }}</span>
    <span class="node-name">{{ node.name || node.type }}</span>
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

function onClick() {
  if ((props.node.accessible && !props.node.visited) || (props.isCurrent && props.node.type === 'SHOP')) {
    emit('move', props.node)
  }
}
</script>

<style scoped>
.map-node {
  width: 80px;
  height: 80px;
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
  transform: scale(1.08);
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

.map-node.shop-reopen {
  cursor: pointer;
  animation: pulse 2s infinite;
}

.map-node.shop-reopen:hover {
  transform: scale(1.08);
  box-shadow: 0 0 16px rgba(242, 169, 0, 0.3);
  background: #3a3450;
}

.map-node.boss {
  border-color: rgba(232, 93, 117, 0.7);
}

.node-icon-img {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  object-fit: cover;
  object-position: center;
  display: block;
}

.map-node.visited .node-icon-img {
  filter: grayscale(0.7) brightness(0.5);
}

.node-icon {
  font-size: 28px;
  margin-bottom: 2px;
}

.node-name {
  font-size: 10px;
  white-space: nowrap;
}
</style>
