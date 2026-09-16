<template>
  <button type="button" class="game-card" :class="{ 'can-play': canPlay, 'cannot-play': !canPlay, selected }"
    :style="{ borderTopColor: typeColor }" :aria-pressed="preview ? selected : undefined"
    :aria-label="`${card.name}，${card.cost} 法力，${card.description}${!canPlay ? '，当前不可出牌' : ''}`"
    :disabled="!preview && !canPlay" @click="handleClick">
    <span class="cost-circle" :aria-label="`${card.cost} 法力`">{{ card.cost }}</span>
    <ResponsiveImage v-if="artUrl" class="card-art" :src="artUrl" alt="" :emoji="card.emoji || '📜'" sizes="(max-width: 600px) 122px, 138px" object-fit="cover" />
    <span v-else class="card-art card-emoji" aria-hidden="true">{{ card.emoji || '📜' }}</span>
    <span class="card-name">{{ card.name }}<span v-if="card.upgraded" class="upgrade-mark">+</span></span>
    <span class="card-desc">{{ card.description }}</span>
    <span class="card-attrs"><span v-if="card.damage > 0">伤害 {{ card.damage }}</span><span v-if="card.block > 0">格挡 {{ card.block }}</span><span v-if="card.drawCards > 0">抽牌 {{ card.drawCards }}</span></span>
    <span class="card-type" :style="{ color: typeColor }">{{ typeLabel }}<template v-if="selected"> · 已选中</template></span>
  </button>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { Card } from '@/types'
import { cardImgUrl, TYPE_LABELS } from '@/constants/images'
import ResponsiveImage from '@/components/ResponsiveImage.vue'
const props = withDefaults(defineProps<{ card: Card; canPlay: boolean; index?: number; selected?: boolean; preview?: boolean }>(), { selected: false, preview: false })
const emit = defineEmits<{ play: [index?: number]; select: [index?: number] }>()
const typeColor = computed(() => ({ ATTACK: '#a43e2e', SKILL: '#286978', DEFENSE: '#286447', POWER: '#794885', STATUS: '#805b26' }[props.card.type]))
const typeLabel = computed(() => TYPE_LABELS[props.card.type] || props.card.type)
const artUrl = computed(() => cardImgUrl(props.card.name, props.card.upgraded))
function handleClick() { if (props.preview) emit('select', props.index); else if (props.canPlay) emit('play', props.index) }
</script>
<style scoped>
.game-card { position: relative; flex: 0 0 138px; width: 138px; min-height: 194px; padding: 8px; display: flex; flex-direction: column; align-items: center; gap: 5px; border: 1px solid #d1c3a7; border-top: 4px solid #a43e2e; border-radius: 10px; background: #fffaf0; color: #283c35; text-align: center; font: inherit; cursor: pointer; box-shadow: 0 3px 8px #57452415; transition: transform 120ms, border-color 120ms, box-shadow 120ms; touch-action: manipulation; }
.cost-circle { position: absolute; top: 5px; left: 5px; width: 28px; height: 28px; display: grid; place-items: center; border: 2px solid #fffaf0; border-radius: 50%; background: #21665b; color: white; font-size: 0.9375rem; font-weight: 800; }
.card-art { width: 100%; height: 76px; object-fit: cover; border-radius: 5px; background: #ece2cd; }
.card-emoji { display: grid; place-items: center; font-size: 2.25rem; }
.card-name { font-size: 0.875rem; font-weight: 750; font-family: var(--font-display); }
.upgrade-mark { color: #8d6420; }
.card-desc { font-size: 0.75rem; line-height: 1.5; flex: 1; }
.card-attrs { display: flex; justify-content: center; gap: 5px; flex-wrap: wrap; font-size: 0.6875rem; }
.card-attrs > span { background: #ede8d8; border-radius: 4px; padding: 2px 4px; }
.card-type { font-size: 0.6875rem; font-weight: 650; }
.cannot-play { background: #eee9dd; }
.cannot-play .card-art { filter: saturate(.5); }
.selected { outline: 3px solid #21665b; outline-offset: 2px; box-shadow: 0 4px 12px #21665b20; }
.game-card:focus-visible { outline: 3px solid #b44736; outline-offset: 3px; }
@media (hover:hover) { .game-card:hover { transform: translateY(-3px); border-color: #21665b; } }
@media (max-width: 600px) {
  .game-card { flex-basis: 122px; width: 122px; height: 174px; min-height: 174px; padding: 6px; gap: 3px; overflow: hidden; }
  .card-art { height: 56px; flex-shrink: 0; }
  .card-name { width: 100%; line-height: 1.125rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; flex-shrink: 0; }
  .card-desc { font-size: 0.75rem; line-height: 1rem; min-height: 0; max-height: 2rem; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
  .card-attrs { max-width: 100%; height: 16px; flex-shrink: 0; flex-wrap: nowrap; overflow: hidden; }
  .card-attrs > span { white-space: nowrap; padding: 0 3px; }
  .card-type { line-height: 0.875rem; flex-shrink: 0; }
}
@media (prefers-reduced-motion: reduce) { .game-card { transition: none; } }
</style>
