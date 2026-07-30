<!-- ====== 迷你卡牌组件 ====== -->
<template>
  <div
    class="card-mini"
    :class="{
      clickable: clickable,
      selected: selected,
      disabled: disabled,
    }"
    @click="handleClick"
  >
    <!-- 卡牌图片 -->
    <div class="card-mini-art" :style="artStyle"></div>

    <!-- 卡牌名 -->
    <div class="card-name">
      {{ card.name }}<span v-if="card.upgraded" class="upgrade-mark">+</span>
    </div>

    <!-- 费用和类型 -->
    <div class="card-info">
      <span class="card-cost">{{ card.cost }}费</span>
      <span class="card-type-label" :style="{ color: typeColor }">{{ typeLabel }}</span>
    </div>

    <!-- 动态属性 -->
    <div class="card-attrs" v-if="card.damage > 0 || card.block > 0 || card.drawCards > 0">
      <span v-if="card.damage > 0" class="attr attr-damage">伤害 {{ card.damage }}</span>
      <span v-if="card.block > 0" class="attr attr-block">格挡 {{ card.block }}</span>
      <span v-if="card.drawCards > 0" class="attr attr-draw">抽牌 {{ card.drawCards }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Card } from '@/types'
import { cardImgUrl, TYPE_LABELS } from '@/constants/images'

const props = withDefaults(defineProps<{
  card: Card
  clickable?: boolean
  selected?: boolean
  disabled?: boolean
}>(), {
  clickable: false,
  selected: false,
  disabled: false,
})

const emit = defineEmits<{
  click: []
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
  if (props.clickable && !props.disabled) {
    emit('click')
  }
}
</script>

<style scoped>
.card-name {
  font-weight: bold;
  color: var(--text-primary);
  margin: 4px 0;
  font-size: 12px;
}

.upgrade-mark {
  color: var(--gold);
  margin-left: 2px;
}

.card-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
  font-size: 11px;
}

.card-cost {
  color: var(--gold);
}

.card-type-label {
  font-weight: bold;
}

.card-attrs {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
  justify-content: center;
}

.attr {
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 3px;
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

/* 可点击状态 */
.clickable {
  cursor: pointer;
}

.clickable:hover {
  border-color: var(--gold);
  transform: translateY(-4px);
}

/* 选中状态 */
.selected {
  border-color: var(--gold);
  box-shadow: 0 0 10px rgba(242, 169, 0, 0.3);
}

/* 禁用状态 */
.disabled {
  opacity: 0.3;
  pointer-events: none;
  filter: grayscale(1);
}
</style>
