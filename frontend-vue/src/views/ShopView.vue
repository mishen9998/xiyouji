<!-- ====== 商店页面 ====== -->
<template>
  <div class="shop-screen">
    <!-- 顶部栏：角色头像 + 已有宝物 -->
    <div class="shop-top-bar">
      <div class="player-section">
        <img
          v-if="playerAvatarUrl"
          class="player-avatar"
          :src="playerAvatarUrl"
          alt="角色"
        />
        <span v-else class="player-emoji">{{ playerEmoji }}</span>
        <div class="player-meta">
          <span class="player-name">{{ player?.displayName || '取经人' }}</span>
          <span class="player-gold">🪙 {{ player?.gold ?? 0 }}</span>
        </div>
      </div>

      <div class="owned-relics">
        <span class="relics-label">已有宝物</span>
        <div class="relics-list">
          <template v-for="(relic, i) in playerRelics" :key="i">
            <img
              v-if="relicImgUrl(relic.name)"
              class="owned-relic-icon"
              :src="relicImgUrl(relic.name)!"
              :alt="relic.name"
              :title="relic.name + ' — ' + relic.description"
            />
            <span
              v-else
              class="owned-relic-emoji"
              :title="relic.name"
            >{{ relic.emoji || '💎' }}</span>
          </template>
          <span v-if="playerRelics.length === 0" class="no-relics">暂无宝物</span>
        </div>
      </div>
    </div>

    <div class="shop-divider"></div>

    <!-- 商店主体区域 -->
    <div class="shop-body">
      <!-- 卡牌区 -->
      <div class="shop-section">
        <h3 class="section-title">🃏 卡牌（{{ SHOP_CARD_PRICE }}金币/张）</h3>
        <div class="card-grid">
          <div
            v-for="(card, i) in shopCards"
            :key="i"
            class="shop-card"
            :class="{
              bought: boughtCardIds.has(card.id),
              'cannot-afford': !boughtCardIds.has(card.id) && (player?.gold ?? 0) < SHOP_CARD_PRICE
            }"
            @click="onBuyCard(card)"
          >
            <img
              v-if="cardImgUrl(card.name)"
              class="card-art"
              :src="cardImgUrl(card.name)!"
              :alt="card.name"
            />
            <span v-else class="card-emoji">{{ card.emoji || '🃏' }}</span>
            <div class="card-info">
              <span class="card-name">{{ card.name }}<span v-if="card.upgraded">+</span></span>
              <span class="card-type">{{ typeLabel(card.type) }}</span>
              <span v-if="card.damage" class="card-stat">⚔️ {{ card.damage }}</span>
              <span v-if="card.block" class="card-stat">🛡️ {{ card.block }}</span>
            </div>
            <div class="card-price">
              <span v-if="boughtCardIds.has(card.id)" class="bought-mark">✅ 已购</span>
              <span v-else>🪙 {{ SHOP_CARD_PRICE }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 宝物区 -->
      <div class="shop-section">
        <h3 class="section-title">💎 宝物</h3>
        <div class="relic-grid">
          <div
            v-for="relic in shopRelics"
            :key="relic.id"
            class="shop-relic"
            :class="{
              bought: boughtRelicIds.has(relic.id),
              'cannot-afford': !boughtRelicIds.has(relic.id) && (player?.gold ?? 0) < relic.price
            }"
            @click="onBuyRelic(relic)"
          >
            <img
              v-if="relicImgUrl(relic.name)"
              class="relic-art"
              :src="relicImgUrl(relic.name)!"
              :alt="relic.name"
            />
            <span v-else class="relic-emoji">{{ relic.emoji || '💎' }}</span>
            <div class="relic-info">
              <span class="relic-name">{{ relic.name }}</span>
              <span class="relic-tier" :class="'tier-' + relic.tier.toLowerCase()">{{ tierLabel(relic.tier) }}</span>
              <span class="relic-desc">{{ relic.description }}</span>
            </div>
            <div class="relic-price">
              <span v-if="boughtRelicIds.has(relic.id)" class="bought-mark">✅ 已购</span>
              <span v-else>🪙 {{ relic.price }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 离开商店箭头 -->
    <button class="leave-btn" @click="onLeave" title="离开商店">
      <span class="leave-arrow">→</span>
      <span class="leave-text">离开</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useGameStore } from '@/stores/game'
import { useUiStore } from '@/stores/ui'
import { fullImgUrl, relicImgUrl, cardImgUrl, EMOJI_MAP } from '@/constants/images'
import type { Card, ShopRelic } from '@/types'

const router = useRouter()
const store = useGameStore()
const ui = useUiStore()

const SHOP_CARD_PRICE = 50
const loading = ref(false)

const player = computed(() => store.player)
const shopCards = computed(() => store.shopCards)
const shopRelics = computed(() => store.shopRelics)
const boughtCardIds = computed(() => store.boughtCardIds)
const boughtRelicIds = computed(() => store.boughtRelicIds)

const playerAvatarUrl = computed(() => {
  const charClass = store.selectedCharacter || store.player?.characterClass
  return charClass ? fullImgUrl(charClass) : null
})

const playerEmoji = computed(() => {
  const charClass = store.selectedCharacter || store.player?.characterClass
  return charClass ? (EMOJI_MAP[charClass] || '🐵') : '🐵'
})

const playerRelics = computed(() => store.player?.relics ?? [])

function typeLabel(type: string): string {
  const labels: Record<string, string> = {
    ATTACK: '攻击', SKILL: '技能', DEFENSE: '防御', POWER: '能力', STATUS: '状态'
  }
  return labels[type] || type
}

function tierLabel(tier: string): string {
  const labels: Record<string, string> = {
    COMMON: '普通', UNCOMMON: '罕见', RARE: '稀有', BOSS: '首领', SPECIAL: '特殊'
  }
  return labels[tier] || tier
}

async function onBuyCard(card: Card) {
  if (loading.value) return
  if (boughtCardIds.value.has(card.id)) return
  if ((player.value?.gold ?? 0) < SHOP_CARD_PRICE) {
    ui.showToast('🪙 金币不足')
    return
  }
  loading.value = true
  try {
    const ok = await store.buyShopCard(card.id, SHOP_CARD_PRICE)
    if (ok) {
      ui.showToast('✅ 购买成功：' + card.name)
    } else {
      ui.showToast('🪙 金币不足，购买失败')
    }
  } catch (e: any) {
    ui.showToast('购买失败: ' + (e?.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

async function onBuyRelic(relic: ShopRelic) {
  if (loading.value) return
  if (boughtRelicIds.value.has(relic.id)) return
  if ((player.value?.gold ?? 0) < relic.price) {
    ui.showToast('🪙 金币不足')
    return
  }
  loading.value = true
  try {
    const ok = await store.buyShopRelic(relic.id)
    if (ok) {
      ui.showToast('✅ 获得宝物：' + relic.name)
    } else {
      ui.showToast('🪙 金币不足或已拥有')
    }
  } catch (e: any) {
    ui.showToast('购买失败: ' + (e?.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

async function onLeave() {
  await store.leaveShop()
  router.push('/map')
}

onMounted(async () => {
  if (!store.sessionId) {
    router.replace('/')
    return
  }
  await store.browseShop()
})
</script>

<style scoped>
.shop-screen {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg-dark);
  position: relative;
}

/* ====== 顶部栏 ====== */
.shop-top-bar {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 12px 24px;
  background: var(--bg-panel);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-wrap: wrap;
}

.player-section {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.player-avatar {
  width: 56px;
  height: 68px;
  object-fit: cover;
  object-position: top center;
  border-radius: 8px;
  border: 3px solid var(--gold);
  box-shadow: 0 0 12px rgba(242, 169, 0, 0.4);
}

.player-emoji {
  font-size: 40px;
}

.player-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.player-name {
  font-size: 16px;
  font-weight: bold;
  color: var(--text-primary);
  font-family: var(--font-display);
}

.player-gold {
  font-size: 18px;
  color: var(--gold);
  font-weight: bold;
}

.owned-relics {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.relics-label {
  font-size: 12px;
  color: var(--text-muted);
}

.relics-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

.owned-relic-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  object-fit: cover;
  border: 2px solid rgba(242, 169, 0, 0.4);
}

.owned-relic-emoji {
  font-size: 28px;
}

.no-relics {
  font-size: 13px;
  color: var(--text-muted);
  font-style: italic;
}

.shop-divider {
  height: 2px;
  background: linear-gradient(90deg, transparent, rgba(242, 169, 0, 0.4), transparent);
}

/* ====== 商店主体 ====== */
.shop-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px 80px;
  scrollbar-width: thin;
  scrollbar-color: rgba(242, 169, 0, 0.3) transparent;
}

.shop-body::-webkit-scrollbar {
  width: 6px;
}

.shop-body::-webkit-scrollbar-thumb {
  background: rgba(242, 169, 0, 0.3);
  border-radius: 3px;
}

.shop-section {
  margin-bottom: 28px;
}

.section-title {
  font-family: var(--font-display);
  font-size: 20px;
  color: var(--gold);
  margin-bottom: 16px;
  letter-spacing: 2px;
}

/* ====== 卡牌网格 ====== */
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 16px;
}

.shop-card {
  background: var(--bg-card);
  border: 2px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
}

.shop-card:hover:not(.bought):not(.cannot-afford) {
  border-color: var(--gold);
  transform: translateY(-4px);
  box-shadow: 0 6px 20px rgba(242, 169, 0, 0.25);
}

.shop-card.bought {
  opacity: 0.5;
  cursor: default;
}

.shop-card.cannot-afford {
  opacity: 0.6;
  cursor: not-allowed;
}

.card-art {
  width: 100%;
  height: 120px;
  object-fit: cover;
  border-radius: 8px;
  margin-bottom: 8px;
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.card-emoji {
  font-size: 48px;
  margin-bottom: 8px;
  line-height: 120px;
}

.card-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  text-align: center;
}

.card-name {
  font-size: 15px;
  font-weight: bold;
  color: var(--text-primary);
}

.card-type {
  font-size: 11px;
  color: var(--text-muted);
}

.card-stat {
  font-size: 12px;
  color: var(--text-secondary);
}

.card-price {
  margin-top: 8px;
  font-size: 16px;
  color: var(--gold);
  font-weight: bold;
}

.bought-mark {
  color: var(--green);
}

/* ====== 宝物网格 ====== */
.relic-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.shop-relic {
  background: var(--bg-card);
  border: 2px solid rgba(242, 169, 0, 0.2);
  border-radius: 12px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
}

.shop-relic:hover:not(.bought):not(.cannot-afford) {
  border-color: var(--gold);
  transform: translateY(-4px);
  box-shadow: 0 6px 20px rgba(242, 169, 0, 0.25);
}

.shop-relic.bought {
  opacity: 0.5;
  cursor: default;
}

.shop-relic.cannot-afford {
  opacity: 0.6;
  cursor: not-allowed;
}

.relic-art {
  width: 80px;
  height: 80px;
  border-radius: 10px;
  object-fit: cover;
  margin-bottom: 10px;
  border: 2px solid rgba(242, 169, 0, 0.3);
}

.relic-emoji {
  font-size: 56px;
  margin-bottom: 10px;
  line-height: 1;
}

.relic-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  text-align: center;
}

.relic-name {
  font-size: 15px;
  font-weight: bold;
  color: var(--gold);
}

.relic-tier {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
}

.tier-common {
  background: rgba(160, 160, 160, 0.2);
  color: #b0b0b0;
}

.tier-uncommon {
  background: rgba(79, 195, 247, 0.15);
  color: var(--blue);
}

.tier-rare {
  background: rgba(242, 169, 0, 0.15);
  color: var(--gold);
}

.relic-desc {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.4;
  max-width: 180px;
}

.relic-price {
  margin-top: 10px;
  font-size: 18px;
  color: var(--gold);
  font-weight: bold;
}

/* ====== 离开按钮 ====== */
.leave-btn {
  position: fixed;
  bottom: 24px;
  right: 24px;
  display: flex;
  align-items: center;
  gap: 8px;
  background: linear-gradient(135deg, var(--gold), var(--gold-dark));
  color: #1a1a2e;
  border: none;
  padding: 14px 28px;
  font-size: 18px;
  font-weight: bold;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
  font-family: var(--font-display);
  letter-spacing: 2px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
  z-index: 10;
}

.leave-btn:hover {
  transform: translateX(-4px);
  box-shadow: 0 6px 24px rgba(242, 169, 0, 0.4);
}

.leave-arrow {
  font-size: 24px;
  line-height: 1;
}

/* ====== 响应式 ====== */
@media (max-width: 768px) {
  .shop-top-bar {
    padding: 8px 12px;
    gap: 12px;
  }
  .player-avatar {
    width: 44px;
    height: 54px;
  }
  .player-emoji {
    font-size: 32px;
  }
  .player-gold {
    font-size: 15px;
  }
  .owned-relic-icon {
    width: 32px;
    height: 32px;
  }
  .owned-relic-emoji {
    font-size: 22px;
  }
  .shop-body {
    padding: 12px 12px 80px;
  }
  .section-title {
    font-size: 17px;
  }
  .card-grid {
    grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
    gap: 10px;
  }
  .card-art {
    height: 90px;
  }
  .relic-grid {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 10px;
  }
  .relic-art {
    width: 64px;
    height: 64px;
  }
  .leave-btn {
    bottom: 16px;
    right: 16px;
    padding: 10px 20px;
    font-size: 15px;
  }
}

@media (max-width: 480px) {
  .card-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .relic-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
