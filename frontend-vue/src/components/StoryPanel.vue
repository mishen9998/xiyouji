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
import type { StoryEvent } from '@/types'
const props = defineProps<{ stories: Array<StoryEvent | undefined> }>()
const queue = ref<StoryEvent['scenes']>([])
const seen = new Set<string>()
try { for (const id of JSON.parse(sessionStorage.getItem('xiyouji-story-seen') || '[]')) seen.add(id) } catch { /* local presentation only */ }
const current = computed(() => queue.value[0])
watch(() => props.stories, values => {
  for (const story of values) for (const scene of story?.scenes || []) {
    // A completed snapshot is not a fresh confirmation of the active route.
    if (scene.trigger !== 'COMPLETE' && !seen.has(scene.id) && !queue.value.some(queued => queued.id === scene.id)) queue.value.push(scene)
  }
}, { immediate: true, deep: true })
function remember() {
  try { sessionStorage.setItem('xiyouji-story-seen', JSON.stringify([...seen].slice(-300))) } catch { /* storage optional */ }
}
function dismiss() { const scene = queue.value.shift(); if (scene) seen.add(scene.id); remember() }
function skip() { queue.value.forEach(scene => seen.add(scene.id)); queue.value = []; remember() }
</script>
<style scoped>
.story-panel { position:fixed;z-index:80;top:max(12px,env(safe-area-inset-top));right:max(12px,env(safe-area-inset-right));width:min(380px,calc(100vw - 24px));max-height:60dvh;overflow:auto;padding:16px;border:1px solid var(--line);border-left:5px solid var(--red);border-radius:12px;background:var(--bg-panel);box-shadow:0 8px 32px #283c3530; }
.story-panel p {line-height:1.7;margin:8px 0;color:var(--text-secondary)}
.story-panel button {min-height:44px;min-width:72px;margin:4px 8px 0 0;padding:8px;border:1px solid var(--line);border-radius:8px;background:var(--bg-card)}
</style>
