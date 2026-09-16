<template>
  <aside v-if="current" class="story-panel" aria-label="西游故事" aria-live="polite">
    <strong>{{ current.title }}</strong>
    <p>{{ current.text }}</p>
    <button type="button" @click="dismiss">{{ queue.length > 1 ? '下一段' : '收起' }}</button>
    <button type="button" @click="skip">跳过剧情</button>
  </aside>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useGameStore } from '@/stores/game'
import { useRoomStore } from '@/stores/room'
import type { StoryEvent } from '@/types'
const game = useGameStore()
const room = useRoomStore()
const queue = ref<StoryEvent['scenes']>([])
const seen = new Set<string>()
try { for (const id of JSON.parse(sessionStorage.getItem('xiyouji-story-seen') || '[]')) seen.add(id) } catch { /* local presentation only */ }
const current = computed(() => queue.value[0])
watch(() => [game.storyEvent, game.battleInfo?.storyEvent, room.room?.storyEvent, room.battleInfo?.storyEvent], values => {
  for (const story of values) for (const scene of story?.scenes || []) {
    if (!seen.has(scene.id) && !queue.value.some(queued => queued.id === scene.id)) queue.value.push(scene)
  }
}, { immediate: true, deep: true })
function remember() {
  try { sessionStorage.setItem('xiyouji-story-seen', JSON.stringify([...seen].slice(-300))) } catch { /* storage optional */ }
}
function dismiss() { const scene = queue.value.shift(); if (scene) seen.add(scene.id); remember() }
function skip() { queue.value.forEach(scene => seen.add(scene.id)); queue.value = []; remember() }
</script>
<style scoped>
.story-panel { position: fixed; z-index: 80; top: 72px; right: 12px; width: min(360px, calc(100vw - 24px)); max-height: 45vh; overflow: auto; padding: 16px; border: 1px solid var(--gold); border-radius: 12px; background: var(--bg-panel, #25222a); box-shadow: 0 4px 16px #0006; }
.story-panel p { line-height: 1.6; margin: 8px 0; }
.story-panel button { min-height: 44px; min-width: 72px; margin-right: 8px; }
</style>
