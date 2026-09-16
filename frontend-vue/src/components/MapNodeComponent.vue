<!-- ====== 地图节点组件 ====== -->
<template>
  <button
    type="button"
    class="map-node"
    :class="{
      visited: node.visited,
      current: isCurrent,
      accessible: node.accessible && !node.visited,
      boss: node.type === 'BOSS',
    }"
    :style="{ left: x + 'px', top: y + 'px' }"
    :aria-label="`${displayName}${isCurrent ? '，当前位置' : node.visited ? '，已走过' : node.accessible ? '，可前往' : '，尚未开放'}`"
    :aria-current="isCurrent ? 'step' : undefined"
    :disabled="!node.accessible || node.visited || busy"
    @click="onClick"
  >
    <img v-if="imgUrl && !imageFailed" class="node-icon-img" :src="imgUrl" alt="" loading="lazy" decoding="async" @error="imageFailed = true" />
    <span v-else class="node-icon">{{ iconFallback }}</span>
    <span class="node-name">{{ displayName }}</span>
  </button>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { MapNode } from '@/types'
import { NODE_ICON, nodeImgUrl } from '@/constants/images'

const props = defineProps<{
  node: MapNode
  isCurrent: boolean
  x: number
  y: number
  busy?: boolean
}>()

const emit = defineEmits<{ move: [node: MapNode] }>()
const imageFailed = ref(false)

const imgUrl = computed(() => nodeImgUrl(props.node.type))
const iconFallback = computed(() => NODE_ICON[props.node.type] || '❓')
const displayName = computed(() => props.node.type === 'SHOP' ? '土地庙' : (props.node.name || props.node.type))

function onClick() {
  if (!props.busy && props.node.accessible && !props.node.visited) {
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
  font-size: 0.75rem;
  color: var(--text-primary);
}

.map-node.accessible {
  border-color: rgba(242, 169, 0, 0.4);
  cursor: pointer;
}

.map-node.accessible:hover {
  border-color: var(--gold);
  transform: translate(-50%, -50%) scale(1.08);
  box-shadow: 0 0 16px rgba(242, 169, 0, 0.3);
  background: #e3ecd7;
}

.map-node.visited {
  opacity: 0.7;
  border-color: rgba(255, 255, 255, 0.04);
  cursor: default;
}

.map-node.current {
  border-color: var(--gold) !important;
  box-shadow: 0 0 20px rgba(242, 169, 0, 0.4) !important;
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
  font-size: 3rem;
}

.map-node.boss .node-name {
  color: var(--red);
  font-size: 0.875rem;
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
  font-size: 2.125rem;
  margin-bottom: 2px;
}

.node-name {
  font-size: 0.6875rem;
  white-space: normal;
  line-height: 1.25;
}

@media (max-width: 600px) {
  .map-node {
    width: 64px;
    min-height: 80px;
    height: auto;
    padding: 4px;
  }

  .node-icon-img {
    width: 44px;
    height: 44px;
  }

  .node-icon {
    font-size: 1.75rem;
  }

  .node-name {
    font-size: .7rem;
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
    font-size: 2.375rem;
  }

  .map-node.boss .node-name {
    font-size: 0.75rem;
  }
}
</style>
