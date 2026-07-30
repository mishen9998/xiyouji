<!-- ====== 宝物查看弹窗 ====== -->
<template>
  <div v-if="visible" class="modal-overlay" @click.self="close">
    <div class="modal-box large">
      <h3>💎 你的宝物</h3>
      <div class="relic-list" v-if="relics.length">
        <div v-for="(relic, i) in relics" :key="i" class="relic-item">
          <div
            v-if="relicImgUrl(relic.name)"
            class="relic-img"
            :style="{ backgroundImage: `url('${relicImgUrl(relic.name)}')` }"
          ></div>
          <span v-else class="relic-emoji">{{ relic.emoji || '💎' }}</span>
          <div class="relic-name">{{ relic.name }}</div>
          <div class="relic-desc">{{ relic.description }}</div>
        </div>
      </div>
      <p v-else>暂无宝物</p>
      <button class="btn-primary" @click="close">关闭</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useGameStore } from '@/stores/game'
import { relicImgUrl } from '@/constants/images'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ 'update:visible': [val: boolean] }>()

const store = useGameStore()
const relics = computed(() => store.player?.relics ?? [])

function close() {
  emit('update:visible', false)
}
</script>

<style scoped>
.relic-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
  margin-bottom: 20px;
}

.relic-item {
  background: var(--bg-card);
  border-radius: 10px;
  padding: 14px;
  width: 160px;
  text-align: center;
  display: flex;
  flex-direction: column;
}

.relic-img {
  width: 100%;
  height: 80px;
  margin-bottom: 6px;
  border-radius: 8px;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.relic-emoji {
  font-size: 28px;
  display: block;
  margin-bottom: 4px;
}

.relic-name {
  font-weight: bold;
  color: var(--gold);
  font-size: 13px;
}

.relic-desc {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
  line-height: 1.4;
}
</style>
