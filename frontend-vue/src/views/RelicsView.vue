<!-- ====== 宝物全屏页面 ====== -->
<template>
  <div class="relics-screen">
    <!-- 顶部栏 -->
    <div class="relics-top-bar">
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
          <span class="relics-count">💎 持有 {{ relics.length }} 件宝物</span>
        </div>
      </div>
    </div>

    <div class="relics-divider"></div>

    <!-- 宝物网格 -->
    <div class="relics-body">
      <div class="relic-grid" v-if="relics.length">
        <div v-for="(relic, i) in relics" :key="i" class="relic-card">
          <img
            v-if="relicImgUrl(relic.name)"
            class="relic-art"
            :src="relicImgUrl(relic.name)!"
            :alt="relic.name"
          />
          <span v-else class="relic-emoji">{{ relic.emoji || '💎' }}</span>
          <div class="relic-name">{{ relic.name }}</div>
          <div class="relic-desc">{{ relic.description }}</div>
        </div>
      </div>
      <div v-else class="empty-hint">
        <p>暂无宝物</p>
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
import { fullImgUrl, relicImgUrl, EMOJI_MAP } from '@/constants/images'

const router = useRouter()
const store = useGameStore()

const player = computed(() => store.player)
const relics = computed(() => store.player?.relics ?? [])

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
.relics-screen {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg-dark);
  position: relative;
}

/* ====== 顶部栏 ====== */
.relics-top-bar {
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

.relics-count {
  font-size: 18px;
  color: var(--gold);
  font-weight: bold;
}

.relics-divider {
  height: 2px;
  background: linear-gradient(90deg, transparent, rgba(242, 169, 0, 0.4), transparent);
}

/* ====== 宝物主体 ====== */
.relics-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px 80px;
  scrollbar-width: thin;
  scrollbar-color: rgba(242, 169, 0, 0.3) transparent;
}

.relics-body::-webkit-scrollbar {
  width: 6px;
}

.relics-body::-webkit-scrollbar-thumb {
  background: rgba(242, 169, 0, 0.3);
  border-radius: 3px;
}

.relic-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 18px;
}

.relic-card {
  background: var(--bg-card);
  border: 2px solid rgba(242, 169, 0, 0.2);
  border-radius: 14px;
  padding: 18px 14px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  transition: all 0.2s ease;
}

.relic-card:hover {
  border-color: var(--gold);
  transform: translateY(-4px);
  box-shadow: 0 6px 20px rgba(242, 169, 0, 0.25);
}

.relic-art {
  width: 96px;
  height: 96px;
  border-radius: 10px;
  object-fit: cover;
  margin-bottom: 12px;
  border: 2px solid rgba(242, 169, 0, 0.3);
}

.relic-emoji {
  font-size: 64px;
  margin-bottom: 12px;
  line-height: 1;
}

.relic-name {
  font-size: 17px;
  font-weight: bold;
  color: var(--gold);
  margin-bottom: 8px;
  font-family: var(--font-display);
}

.relic-desc {
  font-size: 13px;
  color: var(--text-muted);
  line-height: 1.5;
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
  .relics-top-bar {
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
  .relics-count {
    font-size: 15px;
  }
  .relics-body {
    padding: 12px 12px 80px;
  }
  .relic-grid {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 10px;
  }
  .relic-art {
    width: 72px;
    height: 72px;
  }
  .relic-emoji {
    font-size: 48px;
  }
  .relic-name {
    font-size: 14px;
  }
  .relic-desc {
    font-size: 11px;
  }
  .leave-btn {
    bottom: 16px;
    left: 16px;
    padding: 10px 20px;
    font-size: 15px;
  }
}

@media (max-width: 480px) {
  .relic-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
