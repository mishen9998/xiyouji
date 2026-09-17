<!-- ====== 角色选择页 ====== -->
<template>
  <div class="char-select" :class="{ 'has-selection': selectedCharacter }">
    <Transition name="character-bg">
      <ResponsiveImage
        v-if="selectedCharacter"
        :key="selectedCharacter.class"
        class="character-backdrop"
        :src="characterAvatarUrl(selectedCharacter.class, 960)"
        :alt="selectedCharacter.name"
        sizes="100vw"
        object-fit="cover"
        critical
        :style="{
          '--hero-position': selectedCharacter.heroPosition ?? 'center',
        }"
      />
    </Transition>
    <div class="character-shade" :class="{ visible: selectedCharacter }"></div>

    <button type="button" class="btn-back" @click="router.push('/menu')">← 返回首页</button>

    <h1 class="page-title">选择你的角色</h1>

    <div class="char-grid" aria-label="角色选择">
      <button
        v-for="char in characters"
        :key="char.class"
        class="char-card"
        :class="{ selected: selected === char.class }"
        type="button"
        :aria-pressed="selected === char.class"
        :aria-label="`${char.name}，${char.title}，HP ${char.hp}，能量 ${char.energy}，${char.desc}`"
        @click="selected = char.class"
      >
        <ResponsiveImage
          class="char-avatar"
          :src="char.avatar"
          :alt="char.name"
          object-fit="cover"
          sizes="(max-width:500px) 42vw, (max-width:900px) 28vw, 200px"
          critical
        />
        <div class="char-name">{{ char.name }}</div>
        <div class="char-title">{{ char.title }}</div>
        <div class="char-stats">
          <span class="stat-hp">HP {{ char.hp }}</span>
          <span class="stat-energy">能量 {{ char.energy }}</span>
        </div>
        <div class="char-desc">{{ char.desc }}</div>
      </button>
    </div>

    <button
      type="button"
      class="btn-primary btn-start"
      :disabled="starting"
      @click="handleStart"
    >
      {{ starting ? '正在进入...' : '开始西行' }}
    </button>

    <div v-if="showOverwritePicker" class="modal-overlay overwrite-overlay" @click.self="showOverwritePicker = false">
      <section class="modal-box overwrite-box" aria-labelledby="overwrite-title">
        <h3 id="overwrite-title">三个游客存档已满</h3>
        <p>请选择一个旧存档覆盖。新游戏创建成功后，旧存档才会被删除。</p>
        <button
          v-for="(slot, index) in fullGuestSlots"
          :key="slot.sessionId"
          class="overwrite-slot"
          type="button"
          :disabled="starting"
          @click="replaceSlot(slot)"
        >
          <b>覆盖存档 {{ index + 1 }}</b>
          <span>{{ characterLabel(slot.characterClass) }}</span>
          <small>{{ formatTime(slot.createdAt) }}</small>
        </button>
        <button class="btn-small" type="button" :disabled="starting" @click="showOverwritePicker = false">取消</button>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ApiError } from '@/api/game'
import { useRouter } from 'vue-router'
import { GuestSaveLimitError, useGameStore } from '@/stores/game'
import { useUiStore } from '@/stores/ui'
import type { GuestSaveSlot } from '@/stores/guestSaves'
import type { CharacterClass } from '@/types'
import { characterAvatarUrl } from '@/constants/images'
import ResponsiveImage from '@/components/ResponsiveImage.vue'

const router = useRouter()
const gameStore = useGameStore()
const uiStore = useUiStore()

interface CharacterInfo {
  class: CharacterClass
  name: string
  title: string
  hp: number
  energy: number
  avatar: string
  avatarPosition?: string
  heroPosition?: string
  desc: string
}

const characters: CharacterInfo[] = [
  {
    class: 'SUN_WUKONG',
    name: '孙悟空',
    title: '齐天大圣',
    hp: 75,
    energy: 3,
    avatar: characterAvatarUrl('SUN_WUKONG') ?? '',
    avatarPosition: '-24px center',
    heroPosition: '65% center',
    desc: '攻击型战士，擅长强力打击与变化之术',
  },
  {
    class: 'ZHU_BAJIE',
    name: '猪八戒',
    title: '天蓬元帅',
    hp: 85,
    energy: 3,
    avatar: characterAvatarUrl('ZHU_BAJIE') ?? '',
    heroPosition: '65% center',
    desc: '防御型坦克，拥有高血量与厚皮护甲',
  },
  {
    class: 'SHA_SENG',
    name: '沙僧',
    title: '卷帘大将',
    hp: 90,
    energy: 3,
    avatar: characterAvatarUrl('SHA_SENG') ?? '',
    heroPosition: '65% center',
    desc: '均衡型战士，攻守兼备的稳定输出',
  },
  {
    class: 'BAI_LONGMA',
    name: '白龙马',
    title: '西海龙太子',
    hp: 70,
    energy: 3,
    avatar: characterAvatarUrl('BAI_LONGMA') ?? '',
    heroPosition: 'center 30%',
    desc: '敏捷型刺客，快速移动与灵活攻击',
  },
  {
    class: 'TANG_SANZANG',
    name: '唐三藏',
    title: '金蝉子转世',
    hp: 80,
    energy: 3,
    avatar: characterAvatarUrl('TANG_SANZANG') ?? '',
    heroPosition: '65% center',
    avatarPosition: '-24px center',
    desc: '辅助型法师，精通佛法治愈与防御',
  },
]

const DEFAULT_CHARACTER: CharacterClass = 'SUN_WUKONG'
const selected = ref<CharacterClass | null>(null)
const starting = ref(false)
const showOverwritePicker = ref(false)
const fullGuestSlots = ref<GuestSaveSlot[]>([])
const selectedCharacter = computed(() =>
  characters.find((character) => character.class === selected.value) ?? null,
)

async function handleStart() {
  if (starting.value) return

  // 新玩家可能直接点击“开始西行”而没有先点角色卡。
  // 使用默认角色保证按钮始终有明确行为，同时仍允许用户在此之前换角。
  const characterClass = selected.value ?? DEFAULT_CHARACTER
  if (!selected.value) selected.value = characterClass

  starting.value = true
  try {
    await gameStore.startNewGame(characterClass)
    await router.push('/map')
  } catch (error) {
    if (error instanceof GuestSaveLimitError) {
      fullGuestSlots.value = error.slots
      showOverwritePicker.value = true
      starting.value = false
      return
    }
    console.error('Start game failed:', error)
    if (!(error instanceof ApiError && error.status === 401)) {
      uiStore.showToast(error instanceof Error ? error.message : '开始游戏失败，请重试')
    }
    starting.value = false
  }
}

function characterLabel(characterClass: CharacterClass) {
  return characters.find(character => character.class === characterClass)?.name || characterClass
}

function formatTime(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '创建时间未知' : date.toLocaleString('zh-CN', { hour12: false })
}

async function replaceSlot(slot: GuestSaveSlot) {
  const characterClass = selected.value ?? DEFAULT_CHARACTER
  starting.value = true
  try {
    await gameStore.startNewGame(characterClass, slot.sessionId)
    showOverwritePicker.value = false
    await router.push('/map')
  } catch (error) {
    console.error('Replace guest save failed:', error)
    if (!(error instanceof ApiError && error.status === 401)) {
      uiStore.showToast('覆盖存档失败，旧存档仍然保留')
    }
  } finally {
    starting.value = false
  }
}
</script>

<style scoped>
.char-select {
  width: 100%;
  height: 100vh;
  background: var(--bg-dark);
  position: relative;
  overflow: hidden;
  isolation: isolate;
}

.character-backdrop {
  position: absolute;
  inset: 0;
  z-index: 0;
  background-size: cover;
  background-repeat: no-repeat;
  transform: scale(1.015);
  filter: saturate(1.04) contrast(1.02);
}

.character-shade {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  opacity: 0;
  background: linear-gradient(
    180deg,
    rgba(8, 7, 14, 0.72) 0%,
    rgba(8, 7, 14, 0.12) 34%,
    rgba(8, 7, 14, 0.18) 60%,
    rgba(8, 7, 14, 0.9) 100%
  );
  transition: opacity 0.45s ease;
}

.character-shade.visible {
  opacity: 1;
}

.character-bg-enter-active,
.character-bg-leave-active {
  transition: opacity 0.38s ease, transform 0.55s ease;
}

.character-bg-enter-from,
.character-bg-leave-to {
  opacity: 0;
  transform: scale(1.055);
}

.btn-back {
  position: absolute;
  top: 24px;
  left: 24px;
  z-index: 7;
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
  position: absolute;
  top: 24px;
  left: 50%;
  z-index: 6;
  transform: translateX(-50%);
  font-size: 36px;
  font-family: var(--font-display);
  color: var(--text-primary);
  margin: 0;
  letter-spacing: 8px;
  background: linear-gradient(135deg, var(--gold), var(--red));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.char-grid {
  position: absolute;
  top: 50%;
  left: 50%;
  z-index: 5;
  transform: translate(-50%, -50%);
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 20px;
  max-width: 1100px;
  width: calc(100% - 40px);
  transition:
    top 0.58s cubic-bezier(0.22, 1, 0.36, 1),
    max-width 0.58s cubic-bezier(0.22, 1, 0.36, 1),
    gap 0.45s ease;
  will-change: top, max-width;
}

.char-card {
  min-width: 0;
  width: 100%;
  background: rgba(31, 28, 44, 0.94);
  backdrop-filter: blur(9px);
  border: 2px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 16px;
  text-align: center;
  color: inherit;
  font: inherit;
  cursor: pointer;
  appearance: none;
  transition: all 0.42s cubic-bezier(0.22, 1, 0.36, 1);
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
  box-shadow: 0 0 22px rgba(242, 169, 0, 0.44);
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

.has-selection .char-grid {
  top: calc(100% - 82px);
  max-width: 760px;
  gap: 12px;
}

.has-selection .char-card {
  padding: 7px;
  border-radius: 10px;
}

.has-selection .char-card:hover,
.has-selection .char-card.selected {
  transform: translateY(-7px);
}

.has-selection .char-avatar {
  aspect-ratio: 16 / 9;
  margin-bottom: 5px;
  border-radius: 7px;
}

.has-selection .char-name {
  font-size: 15px;
  margin: 0;
}

.has-selection .char-title,
.has-selection .char-stats,
.has-selection .char-desc {
  display: none;
}

.btn-start {
  position: absolute;
  top: 20px;
  right: 24px;
  z-index: 8;
  min-width: 172px;
  padding: 12px 26px;
  font-size: 16px;
  letter-spacing: 4px;
  box-shadow: 0 6px 22px rgba(0, 0, 0, 0.32);
}

.overwrite-overlay { z-index: 20; }
.overwrite-box { display: grid; gap: 10px; }
.overwrite-slot {
  width: 100%;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 12px;
  border: 1px solid rgba(242, 169, 0, .22);
  border-radius: 10px;
  padding: 13px 14px;
  background: rgba(255,255,255,.04);
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
}
.overwrite-slot:hover:not(:disabled) { border-color: var(--gold); background: rgba(242,169,0,.09); }
.overwrite-slot span { color: var(--gold); }
.overwrite-slot small { color: var(--text-muted); }
.overwrite-slot:disabled { opacity: .55; cursor: wait; }

@media (max-width: 768px) {
  .page-title {
    top: 72px;
    font-size: 25px;
    letter-spacing: 5px;
  }

  .btn-back {
    top: 16px;
    left: 14px;
  }

  .btn-start {
    top: 14px;
    right: 14px;
    min-width: 132px;
    padding: 10px 16px;
    font-size: 13px;
    letter-spacing: 2px;
  }

  .char-grid {
    grid-template-columns: repeat(3, 1fr);
    top: 56%;
    gap: 10px;
    width: calc(100% - 24px);
  }

  .char-card {
    padding: 9px;
  }

  .has-selection .char-grid {
    grid-template-columns: repeat(5, 1fr);
    top: calc(100% - 61px);
    gap: 5px;
    width: calc(100% - 12px);
  }

  .has-selection .char-card {
    padding: 4px;
  }

  .has-selection .char-avatar {
    aspect-ratio: 1;
    margin-bottom: 3px;
  }

  .has-selection .char-name {
    font-size: 10px;
  }
}

/* Restored entrance palette is scoped: in-game pages retain their own theme. */
:where(.home, .char-select, .auth-page) {
  --bg-dark: #100e17; --bg-card: #211d2e; --bg-panel: #211d2e;
  --text-primary: #f6eedc; --text-secondary: #cfc5b8; --text-muted: #b7ad9f;
  --gold: #f2bd58; --red: #ff958a; --blue: #8fc8ed; --purple: #b78ae1;
  --line: #ffffff26; color: var(--text-primary);
}
:where(.home, .char-select, .auth-page) button:focus-visible {
  outline: 3px solid #ffe0a0; outline-offset: 4px;
}

.char-select { height: 100dvh; min-height: 540px; }
.char-select.has-selection { min-height: 100dvh; }
.char-avatar { aspect-ratio: 1 !important; }
.character-backdrop { width: 100%; height: 100%; background: #100e17; }
.character-backdrop :deep(img) { object-position: var(--hero-position, center); }
.has-selection .char-grid { top: auto; bottom: calc(20px + env(safe-area-inset-bottom)); transform: translateX(-50%); }
.has-selection .char-avatar { aspect-ratio: 16 / 9 !important; }
.btn-back { min-height: 44px; }
.btn-start { min-height: 48px; color: #24170c; background: linear-gradient(135deg, #f1c96b, #bc7c2d); border: 1px solid #ecc779; }
.overwrite-box { background: #211d2e; color: #f6eedc; }
.overwrite-slot { grid-template-columns: 1fr; min-height: 48px; }
@media (max-width: 768px) {
  .char-select { min-height: 100dvh; height: auto; padding: 130px 12px 24px; }
  .char-grid { position: relative; top: auto; left: auto; transform: none; width: 100%; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .char-select.has-selection { height: 100dvh; min-height: 100dvh; padding: 0; }
  .has-selection .char-grid { position: absolute; left: 50%; top: auto; bottom: calc(12px + env(safe-area-inset-bottom)); transform: translateX(-50%); grid-template-columns: repeat(5, minmax(0, 1fr)); }
  .has-selection .char-card { min-height: 70px; }
  .has-selection .char-avatar { aspect-ratio: 1 !important; }
  .page-title { width: 100%; text-align: center; font-size: 24px; }
  .btn-back { left: 12px; padding: 8px 12px; }
  .btn-start { right: 12px; min-width: 124px; }
}
@media (max-height: 560px) and (min-width: 769px) {
  .char-select { min-height: 460px; }
  .char-grid { max-width: 900px; }
  .char-card { padding: 8px; }
}
@media (prefers-reduced-motion: reduce) {
  .char-grid, .char-card, .character-shade, .character-bg-enter-active, .character-bg-leave-active { transition: none; }
}

</style>
