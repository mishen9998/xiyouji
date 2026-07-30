<!-- ====== 血条组件 ====== -->
<template>
  <div class="hp-bar-container" :style="{ width: width }">
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
}

.hp-bar-bg {
  background: rgba(232, 93, 117, 0.2);
  height: 12px;
  border-radius: 6px;
  overflow: hidden;
  width: 100%;
}

.hp-bar-fill {
  height: 100%;
  border-radius: 6px;
  transition: width 0.3s ease;
  background: linear-gradient(90deg, var(--red-dark), var(--red));
}

.hp-bar-fill.enemy {
  background: linear-gradient(90deg, #c62828, #e53935);
}

.hp-bar-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 10px;
  color: var(--text-primary);
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.8);
  white-space: nowrap;
  pointer-events: none;
  font-weight: bold;
}
</style>
