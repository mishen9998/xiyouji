<template>
  <main class="completion-view">
    <ResponsiveImage v-if="confirmed" class="completion-art" :src="sceneImageUrl('completion')" alt="灵山金殿，云海间的取经归途" sizes="100vw" object-fit="cover" critical />
    <section v-if="confirmed" class="completion-panel" aria-labelledby="completion-title">
      <span class="completion-seal" aria-hidden="true">功成</span>
      <p class="eyebrow">三程西行 · 一路同行</p>
      <h1 id="completion-title">取经归来</h1>
      <p class="completion-story">{{ completionStory || '黑风山的灯火、火焰山的清雨、狮驼岭获救的人们，都已成为西行的回响。山高路远，幸有同行；今日抵达灵山，把这一路的勇气与善意带回人间。' }}</p>
      <p class="completion-summary">{{ isMultiplayer ? `${roomStore.room?.players.length ?? 1} 人同行，三章圆满` : '三章圆满，西行之志已成' }}</p>
      <div class="completion-actions">
        <button class="primary" @click="router.push('/menu')">返回营地</button>
        <button @click="beginNewJourney">{{ isMultiplayer ? '寻找新的同行' : '再启西行' }}</button>
      </div>
      <small>通关记录仍保留在本局存档中。结局无需等待，可随时离开。</small>
    </section>
    <section v-else class="completion-panel" role="status">
      <h1>{{ checking ? '正在确认旅程…' : failed ? '结果未确认' : '旅程尚未圆满' }}</h1>
      <p>{{ checking ? '读取服务端的通关记录。' : failed ? '暂时无法读取本局记录，请返回营地重试；不会重复执行通关操作。' : '未找到本局通关记录，请回到旅程继续前行。' }}</p>
      <button @click="router.push('/menu')">返回营地</button>
    </section>
  </main>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useGameStore } from '@/stores/game'
import { useRoomStore } from '@/stores/room'
import { sceneImageUrl } from '@/constants/images'
import ResponsiveImage from '@/components/ResponsiveImage.vue'

const route = useRoute()
const router = useRouter()
const gameStore = useGameStore()
const roomStore = useRoomStore()
const checking = ref(true)
const confirmed = ref(false)
const failed = ref(false)
const isMultiplayer = computed(() => !!route.params.code)
const completionStory = computed(() => (isMultiplayer.value ? roomStore.room?.storyEvent : gameStore.storyEvent)?.scenes.find(scene => scene.trigger === 'COMPLETE')?.text)
function beginNewJourney() {
  if (isMultiplayer.value) roomStore.reset() // Only leave the local view; never delete teammates' completed room.
  void router.push(isMultiplayer.value ? '/room' : '/char-select')
}
onMounted(async () => {
  try {
    if (isMultiplayer.value) {
      await roomStore.openRoom(String(route.params.code))
      confirmed.value = roomStore.room?.status === 'FINISHED'
    } else {
      const state = await gameStore.loadSavedSession(gameStore.sessionId || undefined)
      confirmed.value = !!state?.storyEvent?.scenes.some(scene => scene.trigger === 'COMPLETE')
      failed.value = state === null && !!gameStore.sessionId
    }
  } catch { confirmed.value = false; failed.value = true }
  finally { checking.value = false }
})
</script>
<style scoped>
.completion-view { min-height: 100dvh; position: relative; isolation: isolate; display: grid; place-items: end center; padding: max(24px, env(safe-area-inset-top)) max(16px, env(safe-area-inset-right)) max(32px, env(safe-area-inset-bottom)) max(16px, env(safe-area-inset-left)); color: #283c35; background: #f7f1e5; }
.completion-art { position: absolute; inset: 0; width: 100%; height: 100%; z-index: -2; }
.completion-view::after { content: ''; position: absolute; inset: 0; z-index: -1; background: linear-gradient(transparent 20%, #f7f1e544 55%, #f7f1e5c9); }
.completion-panel { width: min(680px, 100%); min-width: 0; margin-top: 30vh; padding: 28px clamp(16px, 4vw, 40px); text-align: center; border: 1px solid #cdb984; border-radius: 18px; background: #fffbf1f5; box-shadow: 0 12px 36px #715e3022; }
.completion-seal { display: inline-grid; place-items: center; width: 48px; height: 48px; border: 3px double #fff8e8; outline: 1px solid #b44736; background: #b44736; color: #fff8e8; font: 1rem var(--font-display); transform: rotate(-5deg); }
.eyebrow { margin: 14px 0 6px; font-size: .75rem; letter-spacing: .15em; color: #776549; }
h1 { margin: 8px 0 16px; font: 700 clamp(2rem, 6vw, 3rem) var(--font-display); color: #21665b; }
.completion-story { line-height: 1.9; font-size: 1rem; text-align: left; }
.completion-summary { margin: 18px 0; color: #8b6837; }
.completion-actions { display: flex; flex-wrap: wrap; justify-content: center; gap: 12px; margin: 20px 0 14px; }
button { min-width: 120px; min-height: 48px; padding: 10px 20px; border: 1px solid #21665b; border-radius: 10px; background: #fffbf1; color: #21665b; font: inherit; cursor: pointer; touch-action: manipulation; }
button.primary { background: #21665b; color: #fffaf0; }
button:focus-visible { outline: 3px solid #b44736; outline-offset: 3px; }
small { display: block; color: #6b6555; font-size: .75rem; line-height: 1.6; }
@media (max-height: 500px) { .completion-panel { margin-top: 0; padding: 18px; } .completion-seal { display: none; } }
</style>
