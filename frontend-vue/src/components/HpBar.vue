<!-- ====== 血条组件 ====== -->
<template>
  <div class="hp-bar-container" :style="{ width: width }" role="meter" aria-label="生命值" :aria-valuenow="hp" :aria-valuemin="0" :aria-valuemax="maxHp" :aria-valuetext="`${hp} / ${maxHp} 生命`">
    <div class="hp-bar-bg">
      <div
        class="hp-bar-fill"
        :class="{ enemy: isEnemy }"
        :style="{ width: fillPercent + '%' }"
      ></div>
    </div>
    <span class="hp-bar-text">{{ hp }}/{{ maxHp }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  hp: number
  maxHp: number
  isEnemy?: boolean
  width?: string
}>(), {
  isEnemy: false,
  width: '140px',
})

const fillPercent = computed(() => {
  if (props.maxHp <= 0) return 0
  const pct = (props.hp / props.maxHp) * 100
  return Math.max(0, Math.min(100, pct))
})
</script>

<style scoped>
.hp-bar-container {
  position: relative;
  display: inline-block;
  max-width: 100%;
  flex-shrink: 0;
}

.hp-bar-bg {
  background: #655d4e;
  height: 1.375rem;
  border-radius: 6px;
  overflow: hidden;
  width: 100%;
}

.hp-bar-fill {
  height: 100%;
  border-radius: 6px;
  transition: width 0.3s ease;
  background: linear-gradient(90deg, #21665b, #39745c);
}

.hp-bar-fill.enemy {
  background: linear-gradient(90deg, #993c2f, #b44736);
}

.hp-bar-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 0.75rem;
  color: #fffdf6;
  text-shadow: 0 1px 2px #25352e;
  white-space: nowrap;
  pointer-events: none;
  font-weight: bold;
}
@media (prefers-reduced-motion: reduce) { .hp-bar-fill { transition: none; } }
</style>
