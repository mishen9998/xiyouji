<!-- ====== 战斗结果弹窗 ====== -->
<template>
  <div class="modal-overlay" v-if="visible">
    <div
      class="modal-box result-modal"
      :class="{ 'modal-victory': isVictory, 'modal-defeat': !isVictory }"
    >
      <h3 class="result-title">{{ titleText }}</h3>
      <p class="result-message" v-if="isVictory">{{ messageText }}</p>
      <p class="result-message" v-else>{{ defeatMessage }}</p>

      <!-- 胜利: 卡牌奖励 -->
      <div class="reward-cards-section" v-if="isVictory && cardRewards.length">
        <p class="reward-hint">
          选择 <strong>一张</strong> 卡牌加入牌组（仅限一张）:
        </p>
        <div class="card-grid reward-cards">
          <div
            v-for="(card, index) in cardRewards"
            :key="index"
            class="reward-card-wrapper"
            :class="{ dimmed: rewardChosen && selectedRewardIndex !== index }"
          >
            <MiniCard
              :card="card"
              :clickable="!rewardChosen && !continuing"
              :selected="selectedRewardIndex === index"
              :disabled="continuing || rewardChosen"
              @click="onSelectReward(index)"
            />
          </div>
        </div>
      </div>

      <!-- 胜利: 遗物奖励 -->
      <div class="reward-relic" v-if="isVictory && relicReward">
        <img
          v-if="relicImgUrl(relicReward.name)"
          :src="relicImgUrl(relicReward.name) || ''"
          class="relic-img"
          :alt="relicReward.name"
        />
        <span class="relic-emoji" v-else>🎁</span>
        <span class="relic-text">🎁 获得宝物: {{ relicReward.name }}</span>
      </div>

      <button
        class="btn-primary continue-btn"
        :disabled="continuing || (isVictory && !rewardChosen && selectedRewardIndex < 0)"
        @click="onContinue(false)"
      >
        {{ continuing ? '处理中...' : '继续前进' }}
      </button>
      <button v-if="isVictory && !rewardChosen" class="btn-small" :disabled="continuing" @click="onContinue(true)">跳过奖励</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useGameStore } from '@/stores/game'
import { useUiStore } from '@/stores/ui'
import { relicImgUrl } from '@/constants/images'
import type { Card } from '@/types'
import MiniCard from '@/components/MiniCard.vue'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'continue'): void
  (e: 'return-to-map'): void
  (e: 'next-layer'): void
  (e: 'game-complete'): void
}>()

const gameStore = useGameStore()
const { showToast } = useUiStore()
const { battleInfo, rewards, currentNode } = storeToRefs(gameStore)
const { nextLayer, chooseCardReward, skipReward } = gameStore

const rewardChosen = computed(() => rewards.value?.resolved ?? false)
const selectedRewardIndex = ref(-1)
const continuing = ref(false)

const isVictory = computed(() => battleInfo.value?.victory ?? false)
const isBossNode = computed(() => currentNode.value?.type === 'BOSS')

const cardRewards = computed<Card[]>(() => rewards.value?.cardRewards ?? [])
const relicReward = computed(() => rewards.value?.relicReward ?? null)

const titleText = computed(() => {
  if (isVictory.value) {
    return isBossNode.value ? '👑 击败Boss！' : '🏆 战斗胜利！'
  }
  return '💀 战斗失败'
})

const messageText = computed(() => {
  const gold = rewards.value?.goldReward ?? 0
  return `获得 ${gold} 金币！`
})

const defeatMessage = computed(() => {
  return rewards.value?.message || '你被击败了。西行之路到此为止。'
})

// 弹窗打开时重置选择状态
watch(
  () => props.visible,
  (val) => {
    if (val) {
      selectedRewardIndex.value = -1
      continuing.value = false
    }
  }
)

function onSelectReward(index: number) {
  if (rewardChosen.value || continuing.value || !cardRewards.value[index]) return
  selectedRewardIndex.value = index
}

async function onContinue(skip = false) {
  if (continuing.value) return
  if (isVictory.value && !rewardChosen.value && !skip && selectedRewardIndex.value < 0) return
  continuing.value = true
  try {
    if (isVictory.value && !rewardChosen.value) {
      const data = skip ? await skipReward() : await chooseCardReward(selectedRewardIndex.value)
      if (!data?.success) throw new Error('奖励提交失败，请重试')
    }
    if (isVictory.value && isBossNode.value) {
      const result = await nextLayer()
      if (result.success) {
        showToast(`🏯 进入第${result.currentLayer}层！`)
        emit('next-layer')
      } else {
        showToast('🎉 恭喜通关！西天取经圆满！')
        emit('game-complete')
      }
    } else {
      emit('return-to-map')
    }
    emit('continue')
    emit('update:visible', false)
  } catch (e) {
    console.error('Continue failed:', e)
    showToast('操作失败，请重试')
  } finally {
    continuing.value = false
  }
}

</script>

<style scoped>
.result-modal {
  /* 放大弹窗，让三张卡牌有充足展示空间 */
  min-width: min(520px, 92vw);
  max-height: 90vh;
  overflow-y: auto;
  max-width: 880px;
  width: min(90vw, 880px);
}

.modal-victory {
  border-color: rgba(242, 169, 0, 0.3);
  box-shadow: 0 0 40px rgba(242, 169, 0, 0.15);
}

.modal-defeat {
  border-color: rgba(232, 93, 117, 0.3);
  box-shadow: 0 0 40px rgba(232, 93, 117, 0.15);
}

.result-title {
  font-family: var(--font-display);
  font-size: 30px;
  margin-bottom: 18px;
}

.modal-victory .result-title {
  color: var(--gold);
}

.modal-defeat .result-title {
  color: var(--red);
}

.result-message {
  color: var(--text-secondary);
  font-size: 17px;
  margin-bottom: 22px;
}

.reward-cards-section {
  margin-bottom: 22px;
}

.reward-hint {
  color: var(--text-primary);
  font-size: 17px;
  margin-bottom: 18px;
}

.reward-hint strong {
  color: var(--gold);
}

.reward-cards {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 18px;
}

.reward-card-wrapper {
  transition: all 0.25s ease;
}

.reward-card-wrapper.dimmed {
  opacity: 0.3;
  pointer-events: none;
  filter: grayscale(1);
}

/* ====== 放大奖励场景下的 MiniCard ====== */
.reward-cards :deep(.card-mini) {
  width: 220px;
  padding: 16px;
  font-size: 14px;
}

.reward-cards :deep(.card-mini .card-mini-art) {
  height: 140px;
  margin-bottom: 8px;
  border-radius: 10px;
}

.reward-cards :deep(.card-mini .card-name) {
  font-size: 16px;
  margin: 6px 0;
}

.reward-cards :deep(.card-mini .card-info) {
  font-size: 13px;
  margin-bottom: 6px;
}

.reward-cards :deep(.card-mini .card-cost) {
  font-size: 13px;
}

.reward-cards :deep(.card-mini .card-attrs .attr) {
  font-size: 12px;
  padding: 2px 6px;
}

.reward-cards :deep(.card-mini.clickable:hover) {
  transform: translateY(-6px);
  box-shadow: 0 8px 24px rgba(242, 169, 0, 0.25);
}

.reward-cards :deep(.card-mini.selected) {
  box-shadow: 0 0 16px rgba(242, 169, 0, 0.5);
}

/* ====== 放大宝物获得展示 ====== */
.reward-relic {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  background: rgba(242, 169, 0, 0.08);
  border: 1px solid rgba(242, 169, 0, 0.25);
  border-radius: 12px;
  padding: 18px 24px;
  margin-bottom: 22px;
}

.relic-img {
  width: 72px;
  height: 72px;
  border-radius: 10px;
  object-fit: cover;
  border: 1px solid rgba(242, 169, 0, 0.3);
}

.relic-emoji {
  font-size: 48px;
}

.relic-text {
  color: var(--gold);
  font-size: 18px;
  font-weight: bold;
}

.continue-btn {
  margin-top: 8px;
  font-size: 16px;
  padding: 10px 24px;
}

/* ====== 响应式：小屏自适应 ====== */
@media (max-width: 768px) {
  .result-modal {
    min-width: 300px;
    width: 92vw;
    padding: 20px;
  }
  .reward-cards :deep(.card-mini) {
    width: 150px;
    padding: 10px;
  }
  .reward-cards :deep(.card-mini .card-mini-art) {
    height: 95px;
  }
  .reward-cards :deep(.card-mini .card-name) {
    font-size: 13px;
  }
  .relic-img {
    width: 56px;
    height: 56px;
  }
  .relic-emoji {
    font-size: 36px;
  }
  .relic-text {
    font-size: 15px;
  }
}
</style>
