<!-- ====== 宝物查看弹窗 ====== -->
<template>
  <div v-if="visible" class="modal-overlay" @click.self="close">
    <div class="modal-box large">
      <h3>💎 你的宝物</h3>
      <div class="relic-list" v-if="relics.length">
        <div v-for="(relic, i) in relics" :key="i" class="relic-item">
          <ResponsiveImage class="relic-img" :src="relicImgUrl(relic.name)" :alt="relic.name" :emoji="relic.emoji || '💎'" sizes="(max-width:600px) 100vw, 260px" object-fit="contain" />
          <div class="relic-name">{{ relic.name }}</div>
          <ArtifactDescription kind="relic" :name="relic.name" :effect="relic.description" />
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
import ResponsiveImage from './ResponsiveImage.vue'
import ArtifactDescription from './ArtifactDescription.vue'

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
  width: 260px;
  max-width: 100%;
  border: 1px solid var(--gold);
  text-align: center;
  display: flex;
  flex-direction: column;
}

.relic-img {
  width: 100%;
  height: auto;
  aspect-ratio: 3 / 2 !important;
  margin-bottom: 6px;
  border-radius: 8px;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.relic-emoji {
  font-size: 1.75rem;
  display: block;
  margin-bottom: 4px;
}

.relic-name {
  font-weight: bold;
  color: var(--gold);
  font-size: 1rem;
}

.relic-desc {
  font-size: 0.6875rem;
  color: var(--text-muted);
  margin-top: 4px;
  line-height: 1.4;
}
</style>
