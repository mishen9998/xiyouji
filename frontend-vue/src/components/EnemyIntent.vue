<template>
  <section class="enemy-intent" aria-label="敌人下次行动预告" aria-live="polite">
    <div class="intent-heading"><span class="intent-icon" aria-hidden="true">{{ forecast.icon }}</span><strong>{{ forecast.label }}</strong><span class="intent-kicker">下次行动</span></div>
    <p class="intent-effect">{{ forecast.details.join(' · ') }}</p>
    <p class="intent-target">目标：{{ forecast.targetLabel }}</p>
    <ul v-if="forecast.forecast.length" class="intent-damage" :class="{ 'intent-damage--multiple': forecast.forecast.length > 1 }" :tabindex="forecast.forecast.length > 1 ? 0 : undefined" :aria-label="forecast.forecast.length > 1 ? '逐人伤害明细，可上下滑动，我的结果优先显示' : '预计伤害'">
      <li v-for="target in damageRows" :key="target.id">
        <span>{{ target.name }}{{ target.id === focusUserId ? '（你）' : '' }}</span><span>{{ target.damagePerHit }} × {{ target.hits }}<template v-if="target.blockAbsorbed"> · 挡 {{ target.blockAbsorbed }}</template> · <strong>预计失去 {{ target.hpLoss }} 生命</strong></span>
      </li>
    </ul>
    <small v-if="forecast.attacking"><template v-if="forecast.forecast.length > 1">可上下滑动明细；</template>出牌可改变伤害与格挡，招式和目标不会重抽。</small>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { describeIntent, type EnemyIntentState, type IntentPlayer } from './enemyIntent'
const props = withDefaults(defineProps<{ enemy: EnemyIntentState; players?: IntentPlayer[]; focusUserId?: string | null }>(), { players: () => [], focusUserId: null })
const forecast = computed(() => describeIntent(props.enemy, props.players))
// Presentation order only: targets and all numeric forecasts remain server-owned.
const damageRows = computed(() => [...forecast.value.forecast].sort((a, b) => Number(b.id === props.focusUserId) - Number(a.id === props.focusUserId)))
</script>
<style scoped>
.enemy-intent { width: 100%; min-width: 0; padding: 10px 12px; color: var(--text-primary); background: var(--bg-panel); border: 1px solid var(--line); border-left: 4px solid var(--red); border-radius: 10px; text-align: left; overflow-wrap: anywhere; }
.intent-heading { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; color: var(--red); font-size: 0.9375rem; }
.intent-icon { font-size: 1.1875rem; }
.intent-kicker { margin-left: auto; color: var(--text-muted); font-size: 0.6875rem; }
.intent-effect { font-size: 0.875rem; font-weight: 650; margin: 4px 0; }
.intent-target { font-size: 0.75rem; margin: 4px 0; }
.intent-damage { list-style: none; padding: 6px 0 0; margin: 0; border-top: 1px dashed var(--line); }
.intent-damage--multiple { max-height: 3.25rem; overflow-y: auto; overscroll-behavior-y: contain; scrollbar-width: thin; }
.intent-damage--multiple:focus-visible { outline: 2px solid var(--gold); outline-offset: 2px; }
.intent-damage li { display: flex; justify-content: space-between; gap: 8px; flex-wrap: wrap; font-size: 0.75rem; line-height: 1.6; }
.intent-damage strong { color: var(--red); font-weight: 600; }
small { display: block; margin-top: 5px; color: var(--text-muted); font-size: 0.6875rem; line-height: 1.5; }
</style>
