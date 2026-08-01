<!-- ====== 牌组全屏页面 ====== -->
<template>
  <div class="deck-screen">
    <!-- 顶部栏 -->
    <div class="deck-top-bar">
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
          <span class="deck-count">📋 牌组共 {{ deck.length }} 张</span>
        </div>
      </div>
    </div>

    <div class="deck-divider"></div>

    <!-- 牌组网格 -->
    <div class="deck-body">
      <div class="card-grid" v-if="deck.length">
        <div v-for="(card, i) in deck" :key="i" class="deck-card-wrapper">
          <MiniCard :card="card" />
        </div>
      </div>
      <div v-else class="empty-hint">
        <p>暂无卡牌</p>
      </div>
    </div>

    <!-- 离开按钮 -->
    <button class="leave-btn" @click="onLeave" title="返回地图">
      <span class="leave-arrow">←</span>
      <span class="leave-text">返回地图</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useGameStore } from '@/stores/game'
import { fullImgUrl, EMOJI_MAP } from '@/constants/images'
import MiniCard from '@/components/MiniCard.vue'

const router = useRouter()
const store = useGameStore()

const player = computed(() => store.player)
const deck = computed(() => store.player?.deck ?? [])

const playerAvatarUrl = computed(() => {
  const charClass = store.selectedCharacter || store.player?.characterClass
  return charClass ? fullImgUrl(charClass) : null
})

const playerEmoji = computed(() => {
  const charClass = store.selectedCharacter || store.player?.characterClass
  return charClass ? (EMOJI_MAP[charClass] || '🐵') : '🐵'
})

function onLeave() {
  router.push('/map')
}

onMounted(async () => {
  if (!store.sessionId) {
    router.replace('/')
    return
  }
  await store.refreshState()
})
</script>

<style scoped>
.deck-screen {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg-dark);
  position: relative;
}

/* ====== 顶部栏 ====== */
.deck-top-bar {
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

.deck-count {
  font-size: 18px;
  color: var(--gold);
  font-weight: bold;
}

.deck-divider {
  height: 2px;
  background: linear-gradient(90deg, transparent, rgba(242, 169, 0, 0.4), transparent);
}

/* ====== 牌组主体 ====== */
.deck-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px 80px;
  scrollbar-width: thin;
  scrollbar-color: rgba(242, 169, 0, 0.3) transparent;
}

.deck-body::-webkit-scrollbar {
  width: 6px;
}

.deck-body::-webkit-scrollbar-thumb {
  background: rgba(242, 169, 0, 0.3);
  border-radius: 3px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
}

.deck-card-wrapper {
  display: flex;
  justify-content: center;
}

.empty-hint {
  text-align: center;
  padding: 60px 20px;
  color: var(--text-muted);
  font-size: 18px;
}

/* ====== 离开按钮 ====== */
.leave-btn {
  position: fixed;
  bottom: 24px;
  left: 24px;
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
  transform: translateX(4px);
  box-shadow: 0 6px 24px rgba(242, 169, 0, 0.4);
}

.leave-arrow {
  font-size: 24px;
  line-height: 1;
}

/* ====== 响应式 ====== */
@media (max-width: 768px) {
  .deck-top-bar {
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
  .deck-count {
    font-size: 15px;
  }
  .deck-body {
    padding: 12px 12px 80px;
  }
  .card-grid {
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
    gap: 10px;
  }
  .leave-btn {
    bottom: 16px;
    left: 16px;
    padding: 10px 20px;
    font-size: 15px;
  }
}

@media (max-width: 480px) {
  .card-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
