<!-- ====== 事件弹窗组件 ====== -->
<template>
  <div v-if="visible" class="modal-overlay battle-theme" :class="{ 'temple-overlay': eventType === 'shop' }" @click.self="onBackdropClick">
    <TempleShop
      v-if="eventType === 'shop'"
      :cards="shopCards"
      :gold="currentGold"
      :bought-indices="boughtIndices"
      :price="shopPrice"
      :busy="submitting"
      @buy="buyCard"
      @forward="onContinue"
    />
    <div v-else class="modal-box" :class="{ 'modal-large': isLargeModal }" role="dialog" aria-modal="true" aria-labelledby="event-title">
      <h3 id="event-title">{{ title }}</h3>
      <p v-if="submitting" role="status">正在确认本次操作…</p>
      <p v-html="message"></p>
      <BranchEventChoices v-if="eventType === 'random' && branchEvent" :event="branchEvent" :busy="submitting" @choose="chooseBranch" />
      <button v-if="eventType === 'random' && !branchEvent" class="btn-primary" :disabled="submitting" @click="chooseBranch('leave')">离开</button>

      <!-- 篝火升级卡牌列表 -->
      <div v-if="eventType === 'bonfire'" class="bonfire-content">
        <p v-if="bonfireUpgradesLeft > 0">🔥 剩余升级次数: {{ bonfireUpgradesLeft }} 张</p>
        <p v-else>🔥 升级次数已用完</p>
        <div class="card-grid">
          <MiniCard
            v-for="(card, i) in deckCards"
            :key="i"
            :card="card"
            :clickable="bonfireUpgradesLeft > 0 && !submitting"
            :selected="selectedUpgrade === i"
            :disabled="bonfireUpgradesLeft <= 0 || submitting"
            @click="doUpgrade(i)"
          />
        </div>
      </div>

      <!-- 唐朝皇帝三选一宝物 -->
      <div v-if="eventType === 'emperor' && emperorChoices.length" class="emperor-content">
        <div class="emperor-choices">
          <button
            type="button"
            v-for="relic in emperorChoices"
            :key="relic.name"
            class="emperor-relic-card"
            :disabled="submitting || emperorConfirmed"
            :aria-pressed="chosenRelicName === relic.name"
            :class="{ chosen: chosenRelicName === relic.name }"
            @click="chooseEmperorRelic(relic)"
          >
            <ResponsiveImage
              class="emperor-relic-img"
              :src="emperorRelicImgUrl(relic.name)"
              :alt="relic.name"
              :emoji="relic.emoji || '💎'"
              sizes="190px"
              object-fit="contain"
            />
            <div class="emperor-relic-name">{{ relic.name }}</div>
            <ArtifactDescription kind="relic" :name="relic.name" :effect="relic.description" compact />
          </button>
        </div>
      </div>

      <!-- 宝箱打开后获得的宝物展示 -->
      <div v-if="eventType === 'treasure' && treasureRelic" class="treasure-relic-show">
        <ResponsiveImage
          class="treasure-relic-img"
          :src="relicImgUrl(treasureRelic.name)"
          :alt="treasureRelic.name"
          :emoji="treasureRelic.emoji || '🎁'"
          sizes="(max-width:600px) 60vw, 320px"
          object-fit="contain"
        />
        <div class="treasure-relic-name">{{ treasureRelic.name }}</div>
        <ArtifactDescription kind="relic" :name="treasureRelic.name" :effect="treasureRelic.description" />
      </div>

      <!-- 主按钮 -->
      <button v-if="eventType !== 'random'" class="btn-primary" :disabled="submitting || (eventType === 'emperor' && !chosenRelicName)" @click="onContinue">
        {{ submitting ? '处理中...' : selectedUpgrade >= 0 ? '继续前进（确认升级）' : continueText }}
      </button>
      <button v-if="eventType === 'bonfire' && selectedUpgrade >= 0" class="btn-small" :disabled="submitting" @click="selectedUpgrade = -1">取消选择</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useGameStore } from '@/stores/game'
import { useUiStore } from '@/stores/ui'
import { emperorRelicImgUrl, relicImgUrl } from '@/constants/images'
import MiniCard from './MiniCard.vue'
import TempleShop from './TempleShop.vue'
import BranchEventChoices from './BranchEventChoices.vue'
import ResponsiveImage from './ResponsiveImage.vue'
import ArtifactDescription from './ArtifactDescription.vue'
import type { Card, Relic, EventPreview } from '@/types'

const props = defineProps<{ visible: boolean; eventType: string }>()
const emit = defineEmits<{ close: [] }>()

const store = useGameStore()
const ui = useUiStore()

const submitting = ref(false)
const selectedUpgrade = ref(-1)
const emperorConfirmed = ref(false)
const title = ref('')
const message = ref('')
const continueText = ref('继续')
const shopCards = ref<Card[]>([])
const boughtIndices = ref<Set<number>>(new Set())
const currentGold = ref(0)
const deckCards = ref<Card[]>([])
const emperorChoices = ref<Relic[]>([])
const chosenRelicName = ref<string>('')
const treasureRelic = ref<Relic | null>(null)
const branchEvent = ref<EventPreview | null>(null)

const bonfireUpgradesLeft = computed(() => store.bonfireUpgradesLeft)
const shopPrice = computed(() =>
  store.player?.relics?.some(relic => relic.name === '通关文牒') ? 40 : 50
)

// 在 emperor / treasure 场景使用更大尺寸的 modal-box
const isLargeModal = computed(() =>
  props.eventType === 'emperor' || (props.eventType === 'treasure' && !!treasureRelic.value)
)

watch(
  () => [props.visible, props.eventType] as const,
  async ([vis, et]) => {
    if (!vis || !et) return
    if (submitting.value) return
    submitting.value = true
    try { await handleEvent(et) }
    catch (error: any) { message.value = error?.message || '事件读取失败，请稍后重试'; ui.showToast(message.value) }
    finally { submitting.value = false }
  }
)

async function handleEvent(et: string) {
  selectedUpgrade.value = -1
  emperorConfirmed.value = false
  boughtIndices.value = new Set()
  shopCards.value = []
  emperorChoices.value = []
  chosenRelicName.value = ''
  treasureRelic.value = null
  branchEvent.value = null

  switch (et) {
    case 'rest':
      title.value = '🏕️ 休息点'
      message.value = '一处安全的地方，可以休息恢复。'
      continueText.value = '休息 (恢复生命值)'
      break
    case 'treasure':
      title.value = '📦 宝箱'
      message.value = '你发现了一个宝箱！'
      continueText.value = '打开宝箱'
      break
    case 'shop':
      title.value = '土地庙'
      message.value = '香火照山门，选一张卡牌补充行囊。'
      continueText.value = '继续前进'
      currentGold.value = store.player?.gold ?? 0
      const shopData = await store.handleEvent('browse')
      if (shopData?.shopCards) {
        shopCards.value = shopData.shopCards
        message.value = '每张卡牌需要供奉50金币。'
      }
      break
    case 'bonfire':
      title.value = '🔥 篝火'
      message.value = '温暖的篝火，可以升级卡牌。'
      continueText.value = '不升级，继续前进'
      await store.refreshState()
      deckCards.value = store.player?.deck ?? []
      break
    case 'random':
      title.value = '❓ 随机事件'
      continueText.value = '继续'
      try {
        const randomData = await store.handleEvent('trigger')
        branchEvent.value = randomData?.storyEvent?.event || null
        title.value = branchEvent.value?.title || title.value
        message.value = randomData?.message || '正在读取事件…'
      } catch (error: any) { message.value = error?.message || '事件读取失败，可离开后继续' }
      break
    case 'emperor': {
      title.value = '👑 唐太宗赐宝'
      continueText.value = '继续前进'
      message.value = '唐太宗李世民设宴相送，请稍候…'
      try {
        const emperorData = await store.handleEvent('view')
        console.log('[EventModal] emperor event response:', emperorData)
        if (emperorData?.error) {
          message.value = '⚠️ ' + emperorData.error
          emperorChoices.value = []
        } else if (emperorData?.choices && emperorData.choices.length > 0) {
          emperorChoices.value = emperorData.choices
          message.value = emperorData.message || '唐太宗李世民设宴相送，请从三件御赐宝物中选择一件：'
        } else {
          emperorChoices.value = []
          message.value = '⚠️ 候选宝物列表为空（可能已拥有所有御赐宝物，或数据库未初始化皇帝宝物）。请联系管理员或重新开始游戏。'
          console.warn('[EventModal] emperor choices is empty:', emperorData)
        }
      } catch (e: any) {
        console.error('[EventModal] emperor event failed:', e)
        message.value = '⚠️ 获取宝物列表失败: ' + (e?.message || '未知错误')
        emperorChoices.value = []
      }
      break
    }
  }
}

function chooseEmperorRelic(relic: Relic) {
  if (!submitting.value && !emperorConfirmed.value) chosenRelicName.value = relic.name
}

async function onContinue() {
  if (submitting.value) return
  const et = props.eventType
  if (et === 'emperor' && !chosenRelicName.value) return
  submitting.value = true
  try {
    if (et === 'emperor' && !emperorConfirmed.value) {
      const data = await store.handleEvent('choose', { relicName: chosenRelicName.value })
      if (!data?.relic || data.error) throw new Error(data?.error || '领取宝物失败')
      emperorConfirmed.value = true
      ui.showToast('✅ 获得: ' + chosenRelicName.value)
    } else if (et === 'bonfire' && selectedUpgrade.value >= 0) {
      const data = await store.upgradeCard(selectedUpgrade.value)
      if (!data || data.error) throw new Error(data?.error || '升级失败')
      selectedUpgrade.value = -1
      deckCards.value = store.player?.deck ?? []
      ui.showToast('✅ 升级成功')
      if (bonfireUpgradesLeft.value > 0) return
    } else if (et === 'rest') {
      const data = await store.handleEvent('rest')
      if (data?.error) throw new Error(data.error)
    } else if (et === 'treasure' && continueText.value === '打开宝箱') {
      const data = await store.handleEvent('open')
      if (!data || data.error) throw new Error(data?.error || '打开宝箱失败')
      if (data.relic) {
        treasureRelic.value = data.relic
        message.value = '🎉 获得遗物！'
      }
      continueText.value = '继续前进'
      return
    }
    emit('close')
  } catch (e: any) {
    ui.showToast(e?.message || '操作失败，请重试')
  } finally { submitting.value = false }
}

async function buyCard(card: Card, index: number) {
  if (submitting.value) return
  if (currentGold.value < shopPrice.value) {
    ui.showToast('🪙 金币不足，无法购买')
    return
  }
  submitting.value = true
  try {
    const data = await store.handleEvent('buy', { cardId: card.id, price: 50 })
    if (data?.bought) {
      boughtIndices.value = new Set([...boughtIndices.value, index])
      if (data.player) currentGold.value = data.player.gold
      ui.showToast('✅ 购买成功：' + card.name)
    } else ui.showToast(data?.error || '购买失败')
  } catch (error: any) { ui.showToast(error?.message || '购买失败') }
  finally { submitting.value = false }
}

function doUpgrade(index: number) {
  if (!submitting.value && bonfireUpgradesLeft.value > 0) selectedUpgrade.value = index
}

function onBackdropClick() {
  // Choices are only committed or dismissed through their explicit buttons.
  if (!submitting.value && !['shop', 'emperor', 'bonfire', 'random'].includes(props.eventType)) emit('close')
}

async function chooseBranch(optionId: string) {
  if (submitting.value) return
  submitting.value = true
  try {
    const data = await store.handleEvent(optionId)
    branchEvent.value = data?.storyEvent?.event || branchEvent.value
    if (branchEvent.value?.resolved || optionId === 'leave') emit('close')
  } catch (error: any) { ui.showToast(error?.message || '事件选择失败') }
  finally { submitting.value = false }
}

</script>

<style scoped>
/* ====== 大尺寸弹窗（emperor / treasure 场景） ====== */
.modal-large {
  min-width: 0;
  max-width: 880px;
  width: min(90vw, 880px);
  padding: 32px;
}

.modal-large h3 {
  font-size: 1.75rem;
}

.modal-large p {
  font-size: 1.0625rem;
}

.event-actions {
  margin-bottom: 16px;
}

.temple-overlay {
  padding: 2vh 2vw;
  background: rgba(4, 4, 12, 0.92);
}

.shop-card-btn {
  margin: 5px;
  display: inline-block;
}

.shop-card-btn:disabled {
  opacity: 0.5;
}

.shop-card-btn.bought {
  opacity: 0.6;
}

.bonfire-content {
  margin-bottom: 16px;
  text-align: center;
}

.bonfire-content p {
  margin-bottom: 8px;
}

/* ====== 唐朝皇帝赐宝 UI（放大版） ====== */
.emperor-content {
  margin-bottom: 22px;
}

.emperor-choices {
  display: flex;
  gap: 18px;
  justify-content: center;
  flex-wrap: wrap;
}

.emperor-relic-card {
  width: 220px;
  padding: 18px 14px;
  border: 2px solid rgba(242, 169, 0, 0.4);
  border-radius: 14px;
  background: var(--bg-card);
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.emperor-relic-card:hover {
  border-color: var(--gold);
  transform: translateY(-6px);
  box-shadow: 0 8px 24px rgba(242, 169, 0, 0.3);
}

.emperor-relic-card.chosen {
  border-color: #4ade80;
  background: rgba(74, 222, 128, 0.1);
  box-shadow: 0 0 24px rgba(74, 222, 128, 0.4);
  pointer-events: none;
}

.emperor-relic-img {
  width: 100%;
  height: auto;
  aspect-ratio: 3 / 2 !important;
  border-radius: 10px;
  object-fit: cover;
  margin-bottom: 12px;
  border: 1px solid rgba(242, 169, 0, 0.3);
}

.emperor-relic-emoji {
  font-size: 4rem;
  margin-bottom: 12px;
  line-height: 1;
}

.emperor-relic-name {
  font-size: 1.0625rem;
  font-weight: bold;
  color: var(--gold);
  margin-bottom: 8px;
}

.emperor-relic-desc {
  font-size: 0.8125rem;
  color: var(--text-muted);
  line-height: 1.5;
}

/* ====== 宝箱获得宝物展示（放大版） ====== */
.treasure-relic-show {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px;
  margin: 0 auto 22px;
  width: min(100%, 400px);
  background: rgba(242, 169, 0, 0.08);
  border: 1px solid rgba(242, 169, 0, 0.25);
  border-radius: 14px;
  animation: treasure-pop 0.4s ease;
}

@keyframes treasure-pop {
  0% { transform: scale(0.6); opacity: 0; }
  60% { transform: scale(1.08); opacity: 1; }
  100% { transform: scale(1); opacity: 1; }
}

.treasure-relic-img {
  width: 100%;
  height: auto;
  aspect-ratio: 3 / 2 !important;
  border-radius: 12px;
  object-fit: cover;
  margin-bottom: 14px;
  border: 2px solid rgba(242, 169, 0, 0.4);
  box-shadow: 0 0 24px rgba(242, 169, 0, 0.3);
}

.treasure-relic-emoji {
  font-size: 5rem;
  margin-bottom: 14px;
  line-height: 1;
}

.treasure-relic-name {
  font-size: 1.25rem;
  font-weight: bold;
  color: var(--gold);
  margin-bottom: 8px;
}

.treasure-relic-desc {
  font-size: 0.875rem;
  color: var(--text-muted);
  line-height: 1.6;
  max-width: 320px;
  text-align: center;
}

/* ====== 响应式 ====== */
@media (max-width: 768px) {
  .modal-large {
    min-width: 0;
    width: 92vw;
    padding: 22px;
  }
  .modal-large h3 {
    font-size: 1.375rem;
  }
  .emperor-relic-card {
    width: min(150px, calc(50% - 9px));
    padding: 12px 8px;
  }
  .emperor-relic-img {
    width: 100%;
    height: auto;
  }
  .emperor-relic-emoji {
    font-size: 3rem;
  }
  .emperor-relic-name {
    font-size: 0.875rem;
  }
  .emperor-relic-desc {
    font-size: 0.6875rem;
  }
  .treasure-relic-img {
    width: 100%;
    height: auto;
  }
  .treasure-relic-emoji {
    font-size: 3.75rem;
  }
  .treasure-relic-name {
    font-size: 1.0625rem;
  }
  .treasure-relic-desc {
    font-size: 0.75rem;
  }
}
</style>
