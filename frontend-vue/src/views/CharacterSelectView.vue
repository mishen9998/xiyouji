<!-- ====== 角色选择页 ====== -->
<template>
  <div class="char-select" :class="{ 'has-selection': selectedCharacter }">
    <Transition name="character-bg">
      <div
        v-if="selectedCharacter"
        :key="selectedCharacter.class"
        class="character-backdrop"
        :style="{
          backgroundImage: `url(${selectedCharacter.avatar})`,
          backgroundPosition: selectedCharacter.heroPosition ?? 'center',
        }"
      ></div>
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
    desc: '攻击型战士，擅长强力打击与变化之术',
  },
  {
    class: 'ZHU_BAJIE',
    name: '猪八戒',
    title: '天蓬元帅',
    hp: 85,
    energy: 3,
    avatar: characterAvatarUrl('ZHU_BAJIE') ?? '',
    desc: '防御型坦克，拥有高血量与厚皮护甲',
  },
  {
    class: 'SHA_SENG',
    name: '沙僧',
    title: '卷帘大将',
    hp: 90,
    energy: 3,
    avatar: characterAvatarUrl('SHA_SENG') ?? '',
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
    uiStore.showToast('开始游戏失败，请重试')
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
    uiStore.showToast('覆盖存档失败，旧存档仍然保留')
  } finally {
    starting.value = false
  }
}
</script>

<style scoped>
.char-select { position: relative; isolation: isolate; min-height: 100dvh; padding: 24px max(16px, calc((100vw - 1200px)/2)) calc(96px + env(safe-area-inset-bottom)); overflow: auto; }
.character-backdrop { position: fixed; inset: 0; z-index: -2; background-size: cover; opacity: .14; }
.character-shade { position: fixed; inset: 0; z-index: -1; background: linear-gradient(#f7f1e5bb,#f7f1e5ee); pointer-events:none; }
.btn-back { border: 1px solid var(--line); border-radius: 8px; padding: 8px 16px; background: var(--bg-panel); }
.page-title { margin: 20px 0 24px; font: 700 clamp(1.5rem,4vw,2.25rem) var(--font-display); text-align: center; letter-spacing: 3px; }
.char-grid { display: grid; grid-template-columns: repeat(5,minmax(0,1fr)); gap: 16px; }
.char-card { display: flex; flex-direction: column; align-items:center; min-width:0; padding:14px; border:2px solid var(--line); border-radius:14px; background:var(--bg-panel); text-align:center; box-shadow:var(--card-shadow); }
.char-card.selected { border-color:var(--green); background:#eaf0df; box-shadow:0 0 0 3px #21665b20; }
.char-avatar { width:100%; aspect-ratio:1; background-size:cover; border-radius:10px; margin-bottom:12px; }
.char-name { font:700 1.25rem var(--font-display); }.char-title { color:var(--gold); margin:4px 0 8px; font-size:.85rem; }
.char-stats { display:flex; flex-wrap:wrap; justify-content:center; gap:8px; font-size:.85rem; }.stat-hp { color:var(--red); }.stat-energy { color:var(--blue); }
.char-desc { color:var(--text-secondary); font-size:.8rem; line-height:1.6; margin-top:8px; }
.btn-start { position:fixed; bottom:calc(16px + env(safe-area-inset-bottom)); left:50%; transform:translateX(-50%); z-index:10; min-width:200px; max-width:calc(100% - 32px); box-shadow:0 4px 24px #283c3544; }
.overwrite-overlay {z-index:30}.overwrite-box { display:grid;gap:12px; }.overwrite-slot { display:grid;gap:6px; width:100%;padding:12px;text-align:left;background:var(--bg-card);border:1px solid var(--line);border-radius:8px; }
@media(max-width:900px){.char-grid {grid-template-columns:repeat(3,minmax(0,1fr));}.char-select {padding-top:16px;}}
@media(max-width:500px){.char-grid {grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;}.char-card {padding:10px;}.char-name {font-size:1rem;}.char-desc {font-size:.8rem;}}
</style>
