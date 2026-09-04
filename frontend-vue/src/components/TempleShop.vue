<template>
  <section class="temple-shop" data-testid="temple-shop">
    <header class="temple-header">
      <div>
        <p class="temple-eyebrow">西行补给事件</p>
        <h2>土地庙</h2>
        <p class="temple-subtitle">香火照山门，选一张卡牌补充行囊。</p>
      </div>
      <div class="gold-pouch" aria-label="当前金币">
        <span>当前香火钱</span>
        <strong>🪙 {{ gold }}</strong>
      </div>
    </header>

    <div v-if="selectedCard" class="card-detail" data-testid="temple-card-detail">
      <button class="back-link" type="button" @click="selectedCard = null">← 返回商店</button>
      <div class="detail-layout">
        <div class="detail-art" :style="cardArtStyle(selectedCard.card)">
          <span class="detail-cost">{{ selectedCard.card.cost }}</span>
          <span class="detail-name">{{ selectedCard.card.name }}</span>
        </div>
        <div class="detail-copy">
          <p class="detail-kicker">{{ typeLabel(selectedCard.card.type) }}卡 · {{ price }} 金币</p>
          <h3>{{ selectedCard.card.name }}</h3>
          <div class="stat-row">
            <span>费用 <b>{{ selectedCard.card.cost }}</b></span>
            <span v-if="selectedCard.card.damage > 0">伤害 <b>{{ selectedCard.card.damage }}</b></span>
            <span v-if="selectedCard.card.block > 0">格挡 <b>{{ selectedCard.card.block }}</b></span>
            <span v-if="selectedCard.card.drawCards > 0">抽牌 <b>{{ selectedCard.card.drawCards }}</b></span>
          </div>
          <p class="detail-description">{{ selectedCard.card.description }}</p>
          <button
            class="purchase-button"
            type="button"
            :disabled="isBought(selectedCard.index) || gold < price"
            @click="purchaseSelected"
          >
            {{ purchaseLabel(selectedCard.index) }}
          </button>
        </div>
      </div>
    </div>

    <template v-else>
      <div v-if="cards.length" class="shop-grid" data-testid="temple-shop-grid">
        <article
          v-for="(card, index) in cards"
          :key="card.id ?? index"
          class="shop-card"
          :class="{ bought: isBought(index), unaffordable: gold < price }"
          tabindex="0"
          role="button"
          :aria-label="`查看卡牌 ${card.name}`"
          @click="selectedCard = { card, index }"
          @keydown.enter="selectedCard = { card, index }"
        >
          <div class="shop-card-art" :style="cardArtStyle(card)">
            <span class="shop-card-cost">{{ card.cost }}</span>
            <span v-if="isBought(index)" class="bought-seal">已购</span>
          </div>
          <div class="shop-card-body">
            <div class="shop-card-title">
              <h3>{{ card.name }}</h3>
              <span>{{ typeLabel(card.type) }}</span>
            </div>
            <div class="shop-card-stats">
              <span>费用 {{ card.cost }}</span>
              <span v-if="card.damage > 0">伤害 {{ card.damage }}</span>
              <span v-if="card.block > 0">格挡 {{ card.block }}</span>
              <span v-if="card.drawCards > 0">抽牌 {{ card.drawCards }}</span>
            </div>
            <p>{{ card.description }}</p>
            <strong class="shop-price">🪙 {{ price }}</strong>
          </div>
        </article>
      </div>
      <p v-else class="empty-shop">今日香火已尽，土地庙暂时没有可供奉的卡牌。</p>

      <footer class="temple-footer">
        <p>点击卡牌查看完整效果并确认购买。</p>
        <button class="forward-button" type="button" data-testid="temple-forward" @click="$emit('forward')">
          继续前进 →
        </button>
      </footer>
    </template>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { cardImgUrl, TYPE_LABELS } from '@/constants/images'
import type { Card } from '@/types'

const props = withDefaults(defineProps<{
  cards: Card[]
  gold: number
  boughtIndices: Set<number>
  price?: number
}>(), {
  price: 50,
})

const emit = defineEmits<{
  buy: [card: Card, index: number]
  forward: []
}>()

const selectedCard = ref<{ card: Card; index: number } | null>(null)

function typeLabel(type: string) {
  return TYPE_LABELS[type] || type
}

function cardArtStyle(card: Card) {
  const url = cardImgUrl(card.name, card.upgraded)
  return url ? { backgroundImage: `url('${url}')` } : {}
}

function isBought(index: number) {
  return props.boughtIndices.has(index)
}

function purchaseLabel(index: number) {
  if (isBought(index)) return '已收入牌组'
  if (props.gold < props.price) return '香火钱不足'
  return `供奉 ${props.price} 金币购买`
}

function purchaseSelected() {
  if (!selectedCard.value || isBought(selectedCard.value.index) || props.gold < props.price) return
  emit('buy', selectedCard.value.card, selectedCard.value.index)
}
</script>

<style scoped>
.temple-shop {
  width: min(1180px, 96vw);
  min-height: min(760px, 94vh);
  max-height: 96vh;
  overflow-y: auto;
  padding: 30px clamp(18px, 3vw, 44px) 26px;
  border: 1px solid rgba(244, 190, 90, 0.72);
  border-radius: 22px;
  color: #fff7e4;
  background:
    linear-gradient(180deg, rgba(12, 12, 24, 0.2), rgba(12, 12, 24, 0.86) 55%, rgba(8, 8, 18, 0.96)),
    url('/images/宝物/场景/temple_shop_background.jpg') center / cover no-repeat;
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.72), inset 0 0 80px rgba(242, 169, 0, 0.08);
}

.temple-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  margin-bottom: 28px;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.9);
}

.temple-eyebrow,
.detail-kicker {
  margin: 0 0 4px;
  color: #e9bd68;
  font-size: 12px;
  letter-spacing: 0.24em;
}

.temple-header h2 {
  margin: 0;
  color: #fff3c4;
  font-family: var(--font-display);
  font-size: clamp(34px, 5vw, 58px);
  letter-spacing: 0.16em;
}

.temple-subtitle {
  margin: 8px 0 0;
  color: rgba(255, 247, 228, 0.8);
}

.gold-pouch {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 146px;
  padding: 12px 18px;
  border: 1px solid rgba(242, 169, 0, 0.48);
  border-radius: 14px;
  background: rgba(21, 17, 29, 0.78);
  text-align: right;
}

.gold-pouch span { color: #c9bda8; font-size: 12px; }
.gold-pouch strong { color: #ffd477; font-size: 22px; }

.shop-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: clamp(14px, 2vw, 24px);
}

.shop-card {
  overflow: hidden;
  border: 1px solid rgba(242, 169, 0, 0.36);
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(36, 30, 45, 0.96), rgba(18, 16, 28, 0.98));
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.shop-card:hover,
.shop-card:focus-visible {
  outline: none;
  transform: translateY(-7px);
  border-color: #f2a900;
  box-shadow: 0 16px 34px rgba(0, 0, 0, 0.48), 0 0 22px rgba(242, 169, 0, 0.22);
}

.shop-card.bought { opacity: 0.55; filter: saturate(0.55); }
.shop-card.unaffordable:not(.bought) .shop-price { color: #d38f8f; }

.shop-card-art {
  position: relative;
  aspect-ratio: 4 / 3;
  background: #171422 center / cover no-repeat;
}

.shop-card-cost,
.detail-cost {
  position: absolute;
  top: 10px;
  left: 10px;
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border: 2px solid #ffe39a;
  border-radius: 50%;
  color: #2c1c0c;
  background: radial-gradient(circle at 35% 30%, #fff0a8, #d88b24 72%);
  font-weight: 900;
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.6);
}

.bought-seal {
  position: absolute;
  inset: 50% auto auto 50%;
  transform: translate(-50%, -50%) rotate(-8deg);
  padding: 7px 14px;
  border: 2px solid #e8bd70;
  border-radius: 6px;
  color: #ffe7aa;
  background: rgba(82, 28, 22, 0.9);
  font-weight: 800;
}

.shop-card-body { padding: 14px; }
.shop-card-title { display: flex; justify-content: space-between; align-items: baseline; gap: 10px; }
.shop-card-title h3 { margin: 0; color: #fff3c4; font-size: 17px; }
.shop-card-title span { color: #d8b36d; font-size: 11px; }
.shop-card-stats { display: flex; flex-wrap: wrap; gap: 5px; margin: 9px 0; }
.shop-card-stats span,
.stat-row span {
  padding: 3px 7px;
  border: 1px solid rgba(242, 169, 0, 0.24);
  border-radius: 999px;
  color: #f1dfbd;
  background: rgba(242, 169, 0, 0.08);
  font-size: 11px;
}
.shop-card-body p { min-height: 44px; margin: 0 0 10px; color: #c9c1b4; font-size: 12px; line-height: 1.55; }
.shop-price { color: #ffd477; }

.temple-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18px;
  margin-top: 26px;
  padding-top: 18px;
  border-top: 1px solid rgba(242, 169, 0, 0.28);
}
.temple-footer p { margin: 0; color: #bfb5a4; font-size: 13px; }

.forward-button,
.purchase-button,
.back-link {
  border: 1px solid rgba(255, 220, 140, 0.72);
  color: #2b1a09;
  background: linear-gradient(180deg, #ffe09a, #c77d1e);
  font-weight: 800;
  cursor: pointer;
}
.forward-button { padding: 12px 26px; border-radius: 999px; font-size: 15px; }
.purchase-button { padding: 13px 22px; border-radius: 10px; font-size: 15px; }
.purchase-button:disabled { opacity: 0.5; cursor: not-allowed; }
.back-link { padding: 9px 16px; border-radius: 999px; margin-bottom: 18px; }

.detail-layout {
  display: grid;
  grid-template-columns: minmax(220px, 360px) minmax(280px, 1fr);
  gap: clamp(24px, 5vw, 68px);
  align-items: center;
  max-width: 900px;
  margin: 24px auto;
}
.detail-art {
  position: relative;
  aspect-ratio: 3 / 4;
  border: 3px solid #ddb765;
  border-radius: 22px;
  background: #171422 center / cover no-repeat;
  box-shadow: 0 20px 45px rgba(0, 0, 0, 0.64), 0 0 26px rgba(242, 169, 0, 0.25);
}
.detail-name {
  position: absolute;
  right: 14px;
  bottom: 14px;
  left: 14px;
  padding: 10px;
  border-radius: 10px;
  color: #fff1bf;
  background: rgba(12, 10, 18, 0.84);
  font-family: var(--font-display);
  font-size: 24px;
  text-align: center;
}
.detail-copy h3 { margin: 4px 0 16px; color: #fff3c4; font-size: clamp(30px, 5vw, 48px); }
.stat-row { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 20px; }
.stat-row span { padding: 7px 11px; font-size: 13px; }
.detail-description { margin: 0 0 28px; color: #e4dac8; font-size: 17px; line-height: 1.8; }
.empty-shop { margin: 18vh auto; color: #e7d7b8; text-align: center; }

@media (max-width: 1000px) {
  .shop-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 720px) {
  .temple-shop { width: 100vw; min-height: 100vh; max-height: 100vh; border: 0; border-radius: 0; padding: 18px 14px 22px; }
  .temple-header { align-items: flex-end; }
  .temple-subtitle { font-size: 12px; }
  .gold-pouch { min-width: 118px; padding: 9px 12px; }
  .gold-pouch strong { font-size: 17px; }
  .shop-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
  .shop-card-body { padding: 10px; }
  .shop-card-body p { min-height: 0; }
  .detail-layout { grid-template-columns: 1fr; }
  .detail-art { width: min(68vw, 300px); margin: 0 auto; }
  .detail-copy { text-align: center; }
  .stat-row { justify-content: center; }
  .temple-footer { align-items: flex-end; }
}
</style>
