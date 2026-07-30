<!-- ====== Buff图标栏组件 ====== -->
<template>
  <div v-if="buffs && buffs.length > 0" class="buff-bar">
    <div
      v-for="(buff, idx) in buffs"
      :key="idx"
      class="buff-icon"
      :class="buffClass(buff)"
      :title="buffTitle(buff)"
    >
      <img
        v-if="getIcon(buff.name).startsWith('IMG:')"
        :src="`/images/debuffs/${getIcon(buff.name).substring(4)}.jpg`"
        class="buff-img"
        :alt="buff.name"
      />
      <span v-else class="buff-emoji">{{ getIcon(buff.name) }}</span>
      <span v-if="buff.value !== 1" class="buff-value">{{ buff.value }}</span>
      <span v-if="!buff.permanent && buff.value > 0" class="buff-turns">{{ buff.value }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { BUFF_ICONS, DEBUFF_NAMES } from '@/constants/images'
import type { BuffEntry } from '@/types'

defineProps<{
  buffs?: BuffEntry[]
}>()

function getIcon(name: string): string {
  return BUFF_ICONS[name] || '?'
}

function buffClass(buff: BuffEntry): string {
  // 永久buff (力量/敏捷等能力卡效果)
  if (buff.permanent) return 'buff-permanent'
  // 负面buff
  if (DEBUFF_NAMES[buff.name]) return 'buff-debuff'
  // 其他正面buff
  return 'buff-positive'
}

function buffTitle(buff: BuffEntry): string {
  if (buff.permanent) {
    return `${buff.name}: ${buff.value} (永久·本局)`
  }
  return `${buff.name}: ${buff.value} (剩余${buff.value}回合)`
}
</script>

<style scoped>
.buff-bar {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
}

.buff-icon {
  position: relative;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  overflow: visible;
}

/* 永久buff — 金色边框, 表示能力卡效果 */
.buff-permanent {
  background: linear-gradient(135deg, rgba(242, 169, 0, 0.3), rgba(242, 169, 0, 0.15));
  border: 1.5px solid rgba(242, 169, 0, 0.6);
  box-shadow: 0 0 4px rgba(242, 169, 0, 0.3);
}

/* 负面buff — 红色 */
.buff-debuff {
  background: rgba(232, 93, 117, 0.25);
  border-color: rgba(232, 93, 117, 0.4);
}

/* 正面临时buff — 绿色 */
.buff-positive {
  background: rgba(102, 187, 106, 0.25);
  border-color: rgba(102, 187, 106, 0.4);
}

.buff-emoji {
  line-height: 1;
}

.buff-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 5px;
}

/* 数值标记 — 右下角 */
.buff-value {
  position: absolute;
  bottom: -3px;
  right: -3px;
  background: rgba(0, 0, 0, 0.85);
  color: #fff;
  font-size: 9px;
  padding: 0 3px;
  border-radius: 4px;
  font-weight: bold;
  line-height: 14px;
  min-width: 12px;
  text-align: center;
}

/* 回合数标记 — 左上角, 仅临时buff显示 */
.buff-turns {
  position: absolute;
  top: -3px;
  left: -3px;
  background: rgba(232, 93, 117, 0.9);
  color: #fff;
  font-size: 8px;
  padding: 0 2px;
  border-radius: 3px;
  font-weight: bold;
  line-height: 12px;
  min-width: 10px;
  text-align: center;
}
</style>
