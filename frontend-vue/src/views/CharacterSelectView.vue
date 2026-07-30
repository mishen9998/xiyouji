<!-- ====== 角色选择页 ====== -->
<template>
  <div class="char-select">
    <button class="btn-back" @click="router.push('/')">← 返回首页</button>

    <h1 class="page-title">选择你的角色</h1>

    <div class="char-grid">
      <div
        v-for="char in characters"
        :key="char.class"
        class="char-card"
        :class="{ selected: selected === char.class }"
        @click="selected = char.class"
      >
        <div class="char-avatar" :style="{ backgroundImage: `url(${char.avatar})` }">
          <span class="char-emoji">{{ char.emoji }}</span>
        </div>
        <div class="char-name">{{ char.name }}</div>
        <div class="char-title">{{ char.title }}</div>
        <div class="char-stats">
          <span class="stat-hp">HP {{ char.hp }}</span>
          <span class="stat-energy">能量 {{ char.energy }}</span>
        </div>
        <div class="char-desc">{{ char.desc }}</div>
      </div>
    </div>

    <button
      class="btn-primary btn-start"
      :disabled="!selected || starting"
      @click="handleStart"
    >
      {{ starting ? '正在进入...' : '开始西行' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useGameStore } from '@/stores/game'
import { useUiStore } from '@/stores/ui'
import type { CharacterClass } from '@/types'

const router = useRouter()
const gameStore = useGameStore()
const uiStore = useUiStore()

interface CharacterInfo {
  class: CharacterClass
  emoji: string
  name: string
  title: string
  hp: number
  energy: number
  avatar: string
  desc: string
}

const characters: CharacterInfo[] = [
  {
    class: 'SUN_WUKONG',
    emoji: '🐵',
    name: '孙悟空',
    title: '齐天大圣',
    hp: 75,
    energy: 3,
    avatar: '/images/avatar_sunwukong.jpg',
    desc: '攻击型战士，擅长强力打击与变化之术',
  },
  {
    class: 'ZHU_BAJIE',
    emoji: '🐷',
    name: '猪八戒',
    title: '天蓬元帅',
    hp: 85,
    energy: 3,
    avatar: '/images/avatar_zhubajie.jpg',
    desc: '防御型坦克，拥有高血量与厚皮护甲',
  },
  {
    class: 'SHA_SENG',
    emoji: '🟤',
    name: '沙僧',
    title: '卷帘大将',
    hp: 90,
    energy: 3,
    avatar: '/images/avatar_shawujing.jpg',
    desc: '均衡型战士，攻守兼备的稳定输出',
  },
  {
    class: 'BAI_LONGMA',
    emoji: '🐴',
    name: '白龙马',
    title: '西海龙太子',
    hp: 70,
    energy: 3,
    avatar: '/images/avatar_bailongma.jpg',
    desc: '敏捷型刺客，快速移动与灵活攻击',
  },
  {
    class: 'TANG_SANZANG',
    emoji: '🧘',
    name: '唐三藏',
    title: '金蝉子转世',
    hp: 80,
    energy: 3,
    avatar: '/images/avatar_tangsanzang.jpg',
    desc: '辅助型法师，精通佛法治愈与防御',
  },
]

const selected = ref<CharacterClass | null>(null)
const starting = ref(false)

async function handleStart() {
  if (!selected.value || starting.value) return

  starting.value = true
  try {
    await gameStore.startNewGame(selected.value)
    router.push('/map')
  } catch {
    uiStore.showToast('开始游戏失败，请重试')
    starting.value = false
  }
}
</script>

<style scoped>
.char-select {
  width: 100%;
  height: 100vh;
  background: var(--bg-dark);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow-y: auto;
  padding: 40px 20px;
}

.btn-back {
  position: absolute;
  top: 24px;
  left: 24px;
  background: var(--bg-card);
  color: var(--text-secondary);
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 8px 18px;
  font-size: 14px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  font-family: var(--font-body);
}

.btn-back:hover {
  background: #3a3650;
  color: var(--text-primary);
}

.page-title {
  font-size: 36px;
  font-family: var(--font-display);
  color: var(--text-primary);
  margin-bottom: 40px;
  letter-spacing: 8px;
  background: linear-gradient(135deg, var(--gold), var(--red));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.char-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 20px;
  margin-bottom: 40px;
  max-width: 1100px;
  width: 100%;
}

.char-card {
  background: var(--bg-panel);
  border: 2px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 16px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.char-card:hover {
  border-color: rgba(242, 169, 0, 0.4);
  transform: translateY(-4px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.3);
}

.char-card.selected {
  border-color: var(--gold);
  box-shadow: 0 0 16px rgba(242, 169, 0, 0.3);
}

.char-avatar {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 10px;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  background-color: rgba(0, 0, 0, 0.3);
  margin-bottom: 12px;
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
  position: relative;
  overflow: hidden;
}

.char-emoji {
  font-size: 28px;
  margin: 4px;
  text-shadow: 0 2px 6px rgba(0, 0, 0, 0.8);
}

.char-name {
  font-size: 20px;
  font-weight: bold;
  color: var(--text-primary);
  font-family: var(--font-display);
  margin-bottom: 2px;
}

.char-title {
  font-size: 13px;
  color: var(--gold);
  margin-bottom: 8px;
}

.char-stats {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
  font-size: 13px;
}

.stat-hp {
  color: var(--red);
}

.stat-energy {
  color: var(--blue);
}

.char-desc {
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.5;
}

.btn-start {
  padding: 16px 64px;
  font-size: 20px;
  letter-spacing: 6px;
}

@media (max-width: 768px) {
  .char-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
