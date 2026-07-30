<!-- ====== 牌组/牌堆查看弹窗 ====== -->
<template>
  <div v-if="visible" class="modal-overlay" @click.self="close">
    <div class="modal-box large">
      <h3 v-if="mode === 'deck'">📋 你的牌组 ({{ deck.length }}张)</h3>
      <h3 v-else>🎴 牌堆信息</h3>

      <!-- 牌组模式 -->
      <template v-if="mode === 'deck'">
        <div class="card-grid">
          <MiniCard v-for="(card, i) in deck" :key="i" :card="card" />
        </div>
      </template>

      <!-- 牌堆模式 -->
      <template v-else>
        <div class="piles-summary">
          抽牌堆:{{ drawPile.length }} | 弃牌堆:{{ discardPile.length }} | 消耗:{{ exhaustPile.length }}
        </div>

        <div v-if="drawPile.length > 0">
          <h4 class="pile-title draw">📥 抽牌堆</h4>
          <div class="card-grid">
            <MiniCard v-for="(card, i) in drawPile" :key="'d'+i" :card="card" />
          </div>
        </div>

        <div v-if="discardPile.length > 0">
          <h4 class="pile-title discard">📤 弃牌堆</h4>
          <div class="card-grid">
            <MiniCard v-for="(card, i) in discardPile" :key="'c'+i" :card="card" />
          </div>
        </div>

        <div v-if="exhaustPile.length > 0">
          <h4 class="pile-title exhaust">🗑️ 消耗堆</h4>
          <div class="card-grid">
            <MiniCard v-for="(card, i) in exhaustPile" :key="'e'+i" :card="card" />
          </div>
        </div>

        <p v-if="drawPile.length === 0 && discardPile.length === 0 && exhaustPile.length === 0">暂无卡牌</p>
      </template>

      <button class="btn-primary" @click="close">关闭</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useGameStore } from '@/stores/game'
import MiniCard from './MiniCard.vue'

const props = defineProps<{
  visible: boolean
  mode?: 'deck' | 'piles'
}>()

const emit = defineEmits<{ 'update:visible': [val: boolean] }>()

const store = useGameStore()

const deck = computed(() => store.player?.deck ?? [])
const drawPile = computed(() => store.battleInfo?.player?.drawPile ?? [])
const discardPile = computed(() => store.battleInfo?.player?.discardPile ?? [])
const exhaustPile = computed(() => store.battleInfo?.player?.exhaustPile ?? [])

function close() {
  emit('update:visible', false)
}
</script>

<style scoped>
.piles-summary {
  font-size: 14px;
  color: var(--gold);
  margin-bottom: 16px;
  font-weight: bold;
}

.pile-title {
  margin: 8px 0;
  font-size: 15px;
  text-align: left;
}

.pile-title.draw { color: var(--gold); }
.pile-title.discard { color: var(--text-secondary); }
.pile-title.exhaust { color: var(--red); }
</style>
