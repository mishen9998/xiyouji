<!-- ====== 手牌卡牌组件 ====== -->
<template>
  <div
    class="game-card"
    :class="{
      'can-play': canPlay,
      'cannot-play': !canPlay,
    }"
    :style="{ borderLeftColor: typeColor }"
    @click="handleClick"
  >
    <!-- 费用圆圈 -->
    <div class="cost-circle">{{ card.cost }}</div>

    <!-- emoji -->
    <div v-if="card.emoji" class="card-emoji">{{ card.emoji }}</div>

    <!-- 卡牌图片 -->
    <div class="card-art" :style="artStyle"></div>

    <!-- 卡牌名 -->
    <div class="card-name">
      {{ card.name }}<span v-if="card.upgraded" class="upgrade-mark">+</span>
    </div>

    <!-- 描述 -->
    <div class="card-desc">{{ card.description }}</div>

    <!-- 动态属性栏 -->
    <div class="card-attrs">
      <span v-if="card.damage > 0" class="attr attr-damage">伤害 {{ card.damage }}</span>
      <span v-if="card.block > 0" class="attr attr-block">格挡 {{ card.block }}</span>
      <span v-if="card.drawCards > 0" class="attr attr-draw">抽牌 {{ card.drawCards }}</span>
    </div>

    <!-- 类型标签 -->
    <div class="card-type" :style="{ color: typeColor }">{{ typeLabel }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Card } from '@/types'
import { cardImgUrl, TYPE_LABELS } from '@/constants/images'

const props = withDefaults(defineProps<{
  card: Card
  canPlay: boolean
  index?: number
}>(), {})

const emit = defineEmits<{
  play: [index?: number]
}>()

const typeColor = computed(() => {
  switch (props.card.type) {
    case 'ATTACK': return 'var(--red)'
    case 'SKILL': return 'var(--blue)'
    case 'DEFENSE': return 'var(--green)'
    case 'POWER': return 'var(--purple)'
    default: return 'var(--orange)'
  }
})

const typeLabel = computed(() => TYPE_LABELS[props.card.type] || props.card.type)

const artStyle = computed(() => {
  const url = cardImgUrl(props.card.name, props.card.upgraded)
  return url ? { backgroundImage: `url(${url})` } : {}
})

function handleClick() {
  if (props.canPlay) {
    emit('play', props.index)
  }
}
</script>

<style scoped>
.game-card {
  width: 140px;
  min-height: 200px;
  background: var(--bg-card);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-left: 3px solid var(--red);
  border-radius: 10px;
  padding: 8px;
  padding-top: 10px;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-left: -25px;
  flex-shrink: 0;
  box-shadow: var(--card-shadow);
}

.game-card:first-child {
  margin-left: 0;
}

/* 费用圆圈 */
.cost-circle {
  position: absolute;
  top: -6px;
  left: -6px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--gold), var(--gold-dark));
  color: #1a1a2e;
  font-size: 14px;
  font-weight: bold;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.4);
  border: 2px solid var(--bg-panel);
}

/* emoji */
.card-emoji {
  font-size: 20px;
  margin-bottom: 2px;
}

/* 卡牌图片 */
.card-art {
  width: 100%;
  height: 96px;
  border-radius: 6px;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  background-color: rgba(0, 0, 0, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.08);
  margin-bottom: 6px;
}

/* 卡牌名 */
.card-name {
  font-size: 13px;
  font-weight: bold;
  color: var(--text-primary);
  text-align: center;
  margin-bottom: 4px;
  font-family: var(--font-display);
}

.upgrade-mark {
  color: var(--gold);
  margin-left: 2px;
}

/* 描述 */
.card-desc {
  font-size: 11px;
  color: var(--text-secondary);
  text-align: center;
  line-height: 1.4;
  margin-bottom: 6px;
  flex-grow: 1;
}

/* 动态属性栏 */
.card-attrs {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: center;
  margin-bottom: 4px;
}

.attr {
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 4px;
  font-weight: bold;
}

.attr-damage {
  color: var(--red);
  background: rgba(232, 93, 117, 0.15);
}

.attr-block {
  color: var(--blue);
  background: rgba(79, 195, 247, 0.15);
}

.attr-draw {
  color: var(--green);
  background: rgba(102, 187, 106, 0.15);
}

/* 类型标签 */
.card-type {
  font-size: 10px;
  font-weight: bold;
  opacity: 0.8;
}

/* 可打出 + hover */
.can-play:hover {
  transform: translateY(-12px);
  border-color: var(--gold);
  border-left-color: var(--gold);
  box-shadow: 0 8px 20px rgba(242, 169, 0, 0.3);
  z-index: 10;
}

/* 不可打出 */
.cannot-play {
  opacity: 0.5;
  cursor: not-allowed;
}

.cannot-play:hover {
  transform: none;
}

/* ====== 移动端适配 ====== */
@media (max-width: 600px) {
  .game-card {
    width: 100px;
    min-height: 150px;
    padding: 6px;
    padding-top: 8px;
    margin-left: -30px;
  }
  .cost-circle {
    width: 22px;
    height: 22px;
    font-size: 12px;
    top: -4px;
    left: -4px;
  }
  .card-emoji {
    font-size: 16px;
  }
  .card-art {
    height: 64px;
    margin-bottom: 4px;
  }
  .card-name {
    font-size: 11px;
    margin-bottom: 2px;
  }
  .card-desc {
    font-size: 9px;
    line-height: 1.3;
    margin-bottom: 4px;
  }
  .attr {
    font-size: 9px;
    padding: 1px 4px;
  }
  .card-type {
    font-size: 9px;
  }
  .can-play:active {
    transform: translateY(-8px);
  }
}
</style>
