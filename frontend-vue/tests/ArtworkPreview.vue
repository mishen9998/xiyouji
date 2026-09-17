<template>
  <main>
    <header><span>本地视觉验收 · 不连接存档</span><h1>西行绘卷</h1><p>宣纸承山海，鎏金记西行。</p></header>
    <nav aria-label="验收模式"><button v-for="tab in tabs" :key="tab" :aria-pressed="mode === tab" @click="mode = tab">{{ tab }}</button><label>名称筛选 <input v-model="query" placeholder="输入卡牌或宝物名"></label></nav>
    <template v-if="mode !== '实际组件'">
      <p role="status">{{ items.length }} 项 · 西行逸闻为改编文案，非技能规则</p>
      <section class="gallery"><article v-for="item in items" :key="item.name">
        <ResponsiveImage :src="mode === '卡牌图鉴' ? cardImgUrl(item.name) : relicImgUrl(item.name)" :alt="item.name" sizes="(max-width:600px) 90vw, 320px" object-fit="contain" />
        <div class="copy"><h2>{{ item.name }}</h2><p>{{ item.lore }}</p></div>
      </article></section>
    </template>
    <section v-else class="battle-theme components">
      <h2>固定样例 · 实际组件</h2><p role="status">点击出牌：{{ plays }} 次</p>
      <div class="hand"><GameCard v-for="(card, i) in cards" :key="card.id" :card="card" :index="i" can-play @play="plays++" /></div>
      <h2>牌组与奖励卡片</h2><div class="card-grid"><MiniCard v-for="card in cards" :key="card.id" :card="card" clickable :selected="selection === card.id" @click="selection = card.id" /></div>
      <TempleShop :cards="cards" :gold="100" :bought-indices="new Set()" />
    </section>
  </main>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import copy from '@/constants/artwork-copy.json'
import { cardImgUrl, relicImgUrl } from '@/constants/images'
import ResponsiveImage from '@/components/ResponsiveImage.vue'
import GameCard from '@/components/GameCard.vue'
import MiniCard from '@/components/MiniCard.vue'
import TempleShop from '@/components/TempleShop.vue'
import type { Card } from '@/types'
const tabs = ['卡牌图鉴', '宝物图鉴', '实际组件']
const mode = ref('卡牌图鉴'), query = ref(''), plays = ref(0), selection = ref(0)
const items = computed(() => Object.entries(mode.value === '卡牌图鉴' ? copy.cards : copy.relics).filter(([name]) => name.includes(query.value.trim())).map(([name, item]) => ({ name, lore: item.lore })))
const cards: Card[] = [
  { id: 1, name: '挥棒', type: 'ATTACK', cost: 1, damage: 6, block: 0, drawCards: 0, description: '造成6点伤害。', upgraded: false },
  { id: 2, name: '格挡', type: 'DEFENSE', cost: 1, damage: 0, block: 5, drawCards: 0, description: '获得5点格挡。', upgraded: false },
  { id: 3, name: '定海神针', type: 'ATTACK', cost: 2, damage: 12, block: 0, drawCards: 0, description: '造成12点伤害。获得2点力量。', upgraded: false },
  { id: 4, name: '龙吟', type: 'ATTACK', cost: 1, damage: 3, block: 0, drawCards: 2, description: '造成3点伤害。抽2张牌。', upgraded: false },
]
</script>
<style scoped>
main { width: min(1400px, 100%); margin: auto; padding: 24px; }
header { border-bottom: 1px solid #c1a879; padding: 20px 0; margin-bottom: 20px; }
header span { letter-spacing: .16em; font-size: .75rem; color: #795b31; }
h1 { font: 700 2.6rem var(--font-display); margin: 10px 0; }
nav { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin: 16px 0; }
nav button, input { border: 1px solid #ac925f; background: #fff8e9; border-radius: 6px; padding: 8px 12px; }
nav button[aria-pressed=true] { background: #345647; color: white; }
nav label { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.gallery { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr)); gap: 20px; margin-top: 20px; }
article { border: 1px solid #baa06e; border-radius: 12px; overflow: hidden; background: #fff9ea; box-shadow: 0 4px 18px #58452616; }
.copy { padding: 16px; } .copy h2 { font: 700 1.2rem var(--font-display); margin-bottom: 8px; } .copy p { color: #6f5b41; font-size: .8125rem; }
.components { padding: 20px; border-radius: 12px; background: var(--bg-dark); color: var(--text-primary); }
.components h2 { margin: 16px 0; }.hand { display: flex; gap: 10px; overflow-x: auto; padding: 8px 0 16px; }
@media(max-width:600px) { main { padding: 16px; } .components { padding: 12px; } }
</style>
