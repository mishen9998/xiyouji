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
        <div class="detail-art">
          <ResponsiveImage :src="cardImgUrl(selectedCard.card.name, selectedCard.card.upgraded)" :alt="selectedCard.card.name" sizes="(max-width:720px) 68vw, 360px" object-fit="cover" />
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
            :disabled="busy || isBought(selectedCard.index) || gold < price"
            @click="purchaseSelected"
          >
            {{ busy ? '正在确认购买…' : purchaseLabel(selectedCard.index) }}
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
          @keydown.space.prevent="selectedCard = { card, index }"
        >
          <div class="shop-card-art">
            <ResponsiveImage :src="cardImgUrl(card.name, card.upgraded)" :alt="card.name" sizes="(max-width:720px) 44vw, 210px" object-fit="cover" />
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
        <button class="forward-button" type="button" :disabled="busy" data-testid="temple-forward" @click="$emit('forward')">
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
import ResponsiveImage from './ResponsiveImage.vue'

const props = withDefaults(defineProps<{
  cards: Card[]
  gold: number
  boughtIndices: Set<number>
  price?: number
  busy?: boolean
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

function isBought(index: number) {
  return props.boughtIndices.has(index)
}

function purchaseLabel(index: number) {
  if (isBought(index)) return '已收入牌组'
  if (props.gold < props.price) return '香火钱不足'
  return `供奉 ${props.price} 金币购买`
}

function purchaseSelected() {
  if (props.busy || !selectedCard.value || isBought(selectedCard.value.index) || props.gold < props.price) return
  emit('buy', selectedCard.value.card, selectedCard.value.index)
}
</script>

<style scoped>
.temple-shop{width:min(1180px,100%);max-height:92dvh;overflow:auto;padding:24px;border:1px solid var(--line);border-radius:18px;color:var(--text-primary);background:var(--bg-panel);box-shadow:0 24px 80px #283c3533;overscroll-behavior:contain}
.temple-header{display:flex;justify-content:space-between;align-items:flex-start;gap:16px;margin-bottom:24px}.temple-eyebrow,.detail-kicker{color:var(--gold);font-size:.8rem;margin-bottom:6px}.temple-header h2{font:700 2rem var(--font-display);color:var(--green)}.temple-subtitle{color:var(--text-secondary);font-size:.85rem;margin-top:8px}
.gold-pouch{flex-shrink:0;display:grid;gap:4px;padding:12px;border:1px solid var(--line);border-radius:12px;background:#f4e9cb}.gold-pouch span{font-size:.75rem}.gold-pouch strong{color:var(--gold)}
.shop-grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:16px}.shop-card{border:1px solid var(--line);border-radius:12px;background:var(--bg-card);overflow:hidden}.shop-card:hover{border-color:var(--green)}.shop-card.bought{opacity:.6}
.shop-card-art{position:relative;aspect-ratio:4/3}.shop-card-art .responsive-image,.detail-art .responsive-image{width:100%;height:100%;aspect-ratio:auto!important}
.shop-card-cost,.detail-cost{position:absolute;top:8px;left:8px;display:grid;place-items:center;width:32px;height:32px;border-radius:50%;background:var(--green);color:white;font-weight:700}.bought-seal{position:absolute;top:8px;right:8px;padding:4px 8px;color:white;background:var(--red);border-radius:5px}
.shop-card-body{padding:12px}.shop-card-title{display:flex;flex-wrap:wrap;justify-content:space-between;gap:8px;align-items:center}.shop-card-title h3{font-size:1rem}.shop-card-title>span{font-size:.75rem;color:var(--text-secondary)}.shop-card-stats,.stat-row{display:flex;flex-wrap:wrap;gap:8px;margin:10px 0}.shop-card-stats span,.stat-row span{font-size:.75rem;padding:3px 6px;background:var(--bg-panel);border-radius:5px}.shop-card-body p{font-size:.8rem;color:var(--text-secondary);line-height:1.65;margin-bottom:12px}.shop-price{color:var(--gold)}
.temple-footer{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:12px;margin-top:24px;padding-top:16px;border-top:1px solid var(--line)}.temple-footer p{font-size:.85rem;color:var(--text-secondary)}
.forward-button,.purchase-button{min-height:48px;padding:12px 20px;border:0;border-radius:9px;background:var(--green);color:white;font-weight:700}.purchase-button:disabled{opacity:.55}.back-link{padding:8px 16px;border:1px solid var(--line);border-radius:8px;background:var(--bg-card)}
.detail-layout{display:grid;grid-template-columns:minmax(0,300px) minmax(0,1fr);gap:32px;align-items:center;max-width:800px;margin:24px auto}.detail-art{position:relative;aspect-ratio:3/4;border:2px solid var(--gold);border-radius:16px;overflow:hidden}.detail-name{position:absolute;left:8px;right:8px;bottom:8px;background:#fffaf0ed;border-radius:8px;padding:8px;text-align:center;font-weight:700}.detail-copy h3{font:700 1.75rem var(--font-display);margin:8px 0}.detail-description{line-height:1.8;color:var(--text-secondary);margin:20px 0}.empty-shop{text-align:center;padding:64px 0;color:var(--text-secondary)}
@media(max-width:1000px){.shop-grid{grid-template-columns:repeat(3,minmax(0,1fr))}}
@media(max-width:720px){.temple-shop{width:100%;max-height:94dvh;padding:16px;padding-bottom:calc(16px + env(safe-area-inset-bottom))}.temple-header{flex-wrap:wrap}.shop-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.detail-layout{grid-template-columns:minmax(0,1fr);gap:20px}.detail-art{width:min(68vw,280px);margin:auto}.temple-footer{position:sticky;bottom:-16px;background:var(--bg-panel);padding:12px 0}.forward-button{width:100%}.shop-card-body{padding:10px}}
</style>
