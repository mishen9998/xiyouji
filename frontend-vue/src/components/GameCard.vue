<template>
  <button type="button" class="game-card" :class="{ 'can-play': canPlay, 'cannot-play': !canPlay }"
    :style="{ borderTopColor: typeColor }"
    :title="artworkLore('card', card.name)"
    :aria-label="`${card.name}，${card.cost} 法力，${card.description}${!canPlay ? '，当前不可出牌' : ''}`"
    :disabled="!canPlay" @click="handleClick">
    <span class="cost-circle" :aria-label="`${card.cost} 法力`">{{ card.cost }}</span>
    <ResponsiveImage v-if="artUrl" class="card-art" :src="artUrl" alt="" :emoji="card.emoji || '📜'" sizes="(max-width: 600px) 122px, 138px" object-fit="cover" />
    <span v-else class="card-art card-emoji" aria-hidden="true">{{ card.emoji || '📜' }}</span>
    <span class="card-name">{{ card.name }}<span v-if="card.upgraded" class="upgrade-mark">+</span></span>
    <span class="card-desc">{{ card.description }}</span>
    <span class="card-attrs"><span v-if="card.damage > 0">伤害 {{ card.damage }}</span><span v-if="card.block > 0">格挡 {{ card.block }}</span><span v-if="card.drawCards > 0">抽牌 {{ card.drawCards }}</span></span>
    <span class="card-type" :style="{ color: typeColor }">{{ typeLabel }}</span>
  </button>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { Card } from '@/types'
import { cardImgUrl, TYPE_LABELS } from '@/constants/images'
import { artworkLore } from '@/constants/artwork'
import ResponsiveImage from '@/components/ResponsiveImage.vue'
const props = defineProps<{ card: Card; canPlay: boolean; index?: number }>()
const emit = defineEmits<{ play: [index?: number] }>()
const typeColor = computed(() => ({ ATTACK: 'var(--red)', SKILL: 'var(--blue)', DEFENSE: 'var(--green)', POWER: 'var(--purple)', STATUS: 'var(--orange)' }[props.card.type]))
const typeLabel = computed(() => TYPE_LABELS[props.card.type] || props.card.type)
const artUrl = computed(() => cardImgUrl(props.card.name, props.card.upgraded))
function handleClick() { if (props.canPlay) emit('play', props.index) }
</script>
<style scoped>
.game-card { position: relative; flex: 0 0 138px; width: 138px; min-height: 194px; padding: 8px; display: flex; flex-direction: column; align-items: center; gap: 5px; border: 1px solid var(--line); border-top: 4px solid #a43e2e; border-radius: 10px; background: var(--bg-panel); color: var(--text-primary); text-align: center; font: inherit; cursor: pointer; box-shadow: 0 3px 8px #57452415; transition: transform 120ms, border-color 120ms, box-shadow 120ms; touch-action: manipulation; }
.cost-circle { position: absolute; top: 5px; left: 5px; width: 28px; height: 28px; display: grid; place-items: center; border: 2px solid var(--bg-panel); border-radius: 50%; background: var(--gold); color: #201421; font-size: 0.9375rem; font-weight: 800; }
.card-art { width: 100%; height: 76px; object-fit: cover; border-radius: 5px; background: var(--bg-card); }
.card-emoji { display: grid; place-items: center; font-size: 2.25rem; }
.card-name { font-size: 0.875rem; font-weight: 750; font-family: var(--font-display); }
.upgrade-mark { color: var(--gold); }
.card-desc { font-size: 0.75rem; line-height: 1.5; flex: 1; }
.card-attrs { display: flex; justify-content: center; gap: 5px; flex-wrap: wrap; font-size: 0.6875rem; }
.card-attrs > span { background: #332b40; border-radius: 4px; padding: 2px 4px; }
.card-type { font-size: 0.6875rem; font-weight: 650; }
.cannot-play { background: #16141e; }
.cannot-play .card-art { filter: saturate(.5); }
.game-card:active:not(:disabled) { transform: translateY(2px) scale(.97); }
.game-card:disabled { cursor: not-allowed; opacity: .65; }
.game-card:focus-visible { outline: 3px solid var(--red); outline-offset: 3px; }
@media (hover:hover) { .game-card:hover:not(:disabled) { transform: translateY(-3px); border-color: var(--gold); } }
@media (max-width: 600px) {
  .game-card { flex-basis: 122px; width: 122px; min-height: 174px; padding: 6px; gap: 3px; }
  .card-art { height: 56px; flex-shrink: 0; }
  .card-name { width: 100%; line-height: 1.125rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; flex-shrink: 0; }
  .card-desc { font-size: 0.75rem; line-height: 1rem; min-height: 0; }
  .card-attrs { max-width: 100%; height: 16px; flex-shrink: 0; flex-wrap: nowrap; overflow: hidden; }
  .card-attrs > span { white-space: nowrap; padding: 0 3px; }
  .card-type { line-height: 0.875rem; flex-shrink: 0; }
}
@media (prefers-reduced-motion: reduce) { .game-card { transition: none; } }
</style>
