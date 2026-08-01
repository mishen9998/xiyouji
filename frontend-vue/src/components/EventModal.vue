<!-- ====== 事件弹窗组件 ====== -->
<template>
  <div v-if="visible" class="modal-overlay" @click.self="onClose">
    <div class="modal-box" :class="{ 'modal-large': isLargeModal }">
      <h3>{{ title }}</h3>
      <p v-html="message"></p>

      <!-- 篝火升级卡牌列表 -->
      <div v-if="eventType === 'bonfire'" class="bonfire-content">
        <p v-if="bonfireUpgradesLeft > 0">🔥 剩余升级次数: {{ bonfireUpgradesLeft }} 张</p>
        <p v-else>🔥 升级次数已用完</p>
        <div class="card-grid">
          <MiniCard
            v-for="(card, i) in deckCards"
            :key="i"
            :card="card"
            :clickable="bonfireUpgradesLeft > 0"
            :disabled="bonfireUpgradesLeft <= 0"
            @click="doUpgrade(i)"
          />
        </div>
      </div>

      <!-- 唐朝皇帝三选一宝物 -->
      <div v-if="eventType === 'emperor' && emperorChoices.length" class="emperor-content">
        <div class="emperor-choices">
          <div
            v-for="relic in emperorChoices"
            :key="relic.name"
            class="emperor-relic-card"
            :class="{ chosen: chosenRelicName === relic.name, selected: selectedRelicName === relic.name && !chosenRelicName }"
            @click="onSelectEmperorRelic(relic)"
          >
            <img
              v-if="emperorRelicImgUrl(relic.name)"
              class="emperor-relic-img"
              :src="emperorRelicImgUrl(relic.name)!"
              :alt="relic.name"
            />
            <span v-else class="emperor-relic-emoji">{{ relic.emoji || '💎' }}</span>
            <div class="emperor-relic-name">{{ relic.name }}</div>
            <div class="emperor-relic-desc">{{ relic.description }}</div>
          </div>
        </div>
      </div>

      <!-- 宝箱打开后获得的宝物展示 -->
      <div v-if="eventType === 'treasure' && treasureRelic" class="treasure-relic-show">
        <img
          v-if="relicImgUrl(treasureRelic.name)"
          class="treasure-relic-img"
          :src="relicImgUrl(treasureRelic.name)!"
          :alt="treasureRelic.name"
        />
        <span v-else class="treasure-relic-emoji">{{ treasureRelic.emoji || '🎁' }}</span>
        <div class="treasure-relic-name">{{ treasureRelic.name }}</div>
        <div class="treasure-relic-desc">{{ treasureRelic.description }}</div>
      </div>

      <!-- 主按钮：皇帝场景需先选再确认 -->
      <button
        v-if="eventType === 'emperor' && emperorChoices.length && !chosenRelicName"
        class="btn-primary"
        :disabled="!selectedRelicName"
        @click="confirmEmperorRelic"
      >
        {{ selectedRelicName ? '确认选择' : '请先选择宝物' }}
      </button>
      <button v-else class="btn-primary" @click="onContinue">{{ continueText }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useGameStore } from '@/stores/game'
import { useUiStore } from '@/stores/ui'
import { emperorRelicImgUrl, relicImgUrl } from '@/constants/images'
import MiniCard from './MiniCard.vue'
import type { Card, Relic } from '@/types'

const props = defineProps<{ visible: boolean; eventType: string }>()
const emit = defineEmits<{ close: [] }>()

const store = useGameStore()
const ui = useUiStore()

const title = ref('')
const message = ref('')
const continueText = ref('继续')
const deckCards = ref<Card[]>([])
const emperorChoices = ref<Relic[]>([])
const chosenRelicName = ref<string>('')
const selectedRelicName = ref<string>('')
const treasureRelic = ref<Relic | null>(null)

const bonfireUpgradesLeft = computed(() => store.bonfireUpgradesLeft)

// 在 emperor / treasure 场景使用更大尺寸的 modal-box
const isLargeModal = computed(() =>
  props.eventType === 'emperor' || (props.eventType === 'treasure' && !!treasureRelic.value)
)

watch(
  () => [props.visible, props.eventType] as const,
  async ([vis, et]) => {
    if (!vis || !et) return
    await handleEvent(et)
  }
)

async function handleEvent(et: string) {
  emperorChoices.value = []
  chosenRelicName.value = ''
  selectedRelicName.value = ''
  treasureRelic.value = null

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
      const randomData = await store.handleEvent('trigger')
      message.value = randomData?.message || '一个奇怪的事件发生了…'
      break
    case 'emperor': {
      title.value = '👑 唐太宗赐宝'
      continueText.value = '继续前行'
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

// 点击宝物只高亮选中，不立即锁定
function onSelectEmperorRelic(relic: Relic) {
  if (chosenRelicName.value) return
  selectedRelicName.value = relic.name
}

// 确认选择：此时才调用后端锁定宝物
async function confirmEmperorRelic() {
  if (chosenRelicName.value || !selectedRelicName.value) return
  const relic = emperorChoices.value.find(r => r.name === selectedRelicName.value)
  if (!relic) return
  const data = await store.handleEvent('choose', { relicName: relic.name })
  if (data?.relic) {
    chosenRelicName.value = relic.name
    message.value = '🎉 ' + (data.message || '已选中: ' + relic.name)
    ui.showToast('✅ 获得: ' + relic.name)
    await store.refreshState()
  } else if (data?.error) {
    ui.showToast(data.error)
  }
}

async function onContinue() {
  const et = props.eventType
  if (et === 'rest') {
    await store.handleEvent('rest')
  } else if (et === 'treasure') {
    if (continueText.value === '打开宝箱') {
      const data = await store.handleEvent('open')
      if (data?.relic) {
        treasureRelic.value = data.relic
        message.value = '🎉 获得遗物！'
      }
      continueText.value = '继续前行'
      return
    }
  }
  // 关闭并刷新
  emit('close')
  await store.refreshState()
}

async function doUpgrade(index: number) {
  if (bonfireUpgradesLeft.value <= 0) return
  const data = await store.upgradeCard(index)
  if (data?.error) {
    ui.showToast(data.error)
  } else {
    ui.showToast('✅ 升级成功！剩余升级次数: ' + store.bonfireUpgradesLeft)
  }
  deckCards.value = store.player?.deck ?? []
}

function onClose() {
  emit('close')
}
</script>

<style scoped>
/* ====== 大尺寸弹窗（emperor / treasure 场景） ====== */
.modal-large {
  min-width: 520px;
  max-width: 880px;
  width: min(90vw, 880px);
  padding: 32px;
}

.modal-large h3 {
  font-size: 28px;
}

.modal-large p {
  font-size: 17px;
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

.emperor-relic-card.selected {
  border-color: var(--gold);
  background: rgba(242, 169, 0, 0.12);
  box-shadow: 0 0 20px rgba(242, 169, 0, 0.35);
  transform: translateY(-4px);
}

.emperor-relic-img {
  width: 96px;
  height: 96px;
  border-radius: 10px;
  object-fit: cover;
  margin-bottom: 12px;
  border: 1px solid rgba(242, 169, 0, 0.3);
}

.emperor-relic-emoji {
  font-size: 64px;
  margin-bottom: 12px;
  line-height: 1;
}

.emperor-relic-name {
  font-size: 17px;
  font-weight: bold;
  color: var(--gold);
  margin-bottom: 8px;
}

.emperor-relic-desc {
  font-size: 13px;
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
  width: fit-content;
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
  width: 120px;
  height: 120px;
  border-radius: 12px;
  object-fit: cover;
  margin-bottom: 14px;
  border: 2px solid rgba(242, 169, 0, 0.4);
  box-shadow: 0 0 24px rgba(242, 169, 0, 0.3);
}

.treasure-relic-emoji {
  font-size: 80px;
  margin-bottom: 14px;
  line-height: 1;
}

.treasure-relic-name {
  font-size: 20px;
  font-weight: bold;
  color: var(--gold);
  margin-bottom: 8px;
}

.treasure-relic-desc {
  font-size: 14px;
  color: var(--text-muted);
  line-height: 1.6;
  max-width: 320px;
  text-align: center;
}

/* ====== 响应式 ====== */
@media (max-width: 768px) {
  .modal-large {
    min-width: 300px;
    width: 92vw;
    padding: 22px;
  }
  .modal-large h3 {
    font-size: 22px;
  }
  .emperor-relic-card {
    width: 150px;
    padding: 12px 8px;
  }
  .emperor-relic-img {
    width: 72px;
    height: 72px;
  }
  .emperor-relic-emoji {
    font-size: 48px;
  }
  .emperor-relic-name {
    font-size: 14px;
  }
  .emperor-relic-desc {
    font-size: 11px;
  }
  .treasure-relic-img {
    width: 88px;
    height: 88px;
  }
  .treasure-relic-emoji {
    font-size: 60px;
  }
  .treasure-relic-name {
    font-size: 17px;
  }
  .treasure-relic-desc {
    font-size: 12px;
  }
}
</style>
