<!-- ====== 根组件 ====== -->
<template>
  <router-view />
  <Toast />
  <ConfirmModal />
  <StoryPanel v-if="storyContext" :key="storyContext.key" :stories="storyContext.stories" />
  <aside v-if="unknownResult" class="command-recovery" role="alert">
    <p>{{ recoveryMessage }}</p>
    <button :disabled="recovering" @click="recover">同步状态并查询原命令回执</button>
    <button v-if="canStartNewIntent" :disabled="recovering" @click="newIntent">核对状态后发起新意图</button>
  </aside>
</template>

<script setup lang="ts">
import Toast from '@/components/Toast.vue'
import ConfirmModal from '@/components/ConfirmModal.vue'
import StoryPanel from '@/components/StoryPanel.vue'
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { reconcileUnknownCommands, acknowledgeUnknownCommands, hasUnknownCommands, unknownCommandsPastTtl } from '@/api/game'
import { useRoomStore } from '@/stores/room'
import { useGameStore } from '@/stores/game'
import { useRouter } from 'vue-router'
import { useUiStore } from '@/stores/ui'
const router = useRouter()
const game = useGameStore()
const room = useRoomStore()
const onAuthExpired = () => {
  useUiStore().showToast('登录已失效，请重新登录或选择游客模式；已有存档记录不会被删除')
  void router.replace({ name: 'auth', query: { expired: '1' } })
}
onMounted(() => window.addEventListener('xiyouji-auth-expired', onAuthExpired))
onUnmounted(() => window.removeEventListener('xiyouji-auth-expired', onAuthExpired))
// Only the active journey owns the narrative queue. Completion has its own
// fresh, authoritative story; changing journeys discards the old local queue.
const storyContext = computed(() => {
  const route = router.currentRoute.value
  if (route.name === 'map' || route.name === 'battle') {
    return game.sessionId ? { key: `solo:${game.sessionId}`, stories: [game.storyEvent, game.battleInfo?.storyEvent] } : null
  }
  if (route.name === 'mp-map' || route.name === 'mp-battle') {
    const code = String(route.params.code)
    return { key: `room:${code}`, stories: room.room?.code === code
      ? [room.room.storyEvent, room.battleInfo?.roomCode === code ? room.battleInfo.storyEvent : undefined] : [] }
  }
  return null
})
const unknownResult = ref(hasUnknownCommands())
const canStartNewIntent = ref(false)
const recovering = ref(false)
const recoveryMessage = ref('操作结果未确认。请查询原命令回执；状态变化不能证明本次成功。幂等保护为有限时段。')
const notifyUnknown = () => { unknownResult.value = true }
onMounted(() => window.addEventListener('xiyouji-command-unknown', notifyUnknown))
onUnmounted(() => window.removeEventListener('xiyouji-command-unknown', notifyUnknown))
async function recover() {
  if (recovering.value) return
  recovering.value = true
  try {
    const room = useRoomStore()
    await room.refreshRoomState()
    if (room.room?.status === 'IN_BATTLE') await room.refreshBattleState()
    await useGameStore().refreshState()
    await reconcileUnknownCommands(async (command, response) => {
      if (command.url === '/api/room/create' || command.url === '/api/room/join') await room.openRoom(response.code)
      if (command.url === '/api/game/new') {
        const loaded = await useGameStore().loadSavedSession(response.sessionId)
        if (!loaded) throw new Error('新游戏已确认，但权威状态暂不可用')
        await router.push('/map')
      }
    })
    unknownResult.value = false
  } catch {
    canStartNewIntent.value = unknownCommandsPastTtl()
    recoveryMessage.value = '已请求权威状态；原命令仍无完成回执，结果保持未确认。请勿重复提交；超过保护期后须核对状态并明确发起新意图。'
  } finally { recovering.value = false }
}
function newIntent() {
  if (!window.confirm('保护时段已结束。原操作可能已经生效，当前状态变化也不能证明原操作成功。确认已核对最新状态，并自行决定下一次操作？')) return
  acknowledgeUnknownCommands()
  unknownResult.value = false
}
</script>

<style scoped>
.command-recovery { position: fixed; bottom: max(16px,env(safe-area-inset-bottom)); left: 16px; right: 16px; z-index: 10000; padding: 16px; background: #fff4d6; color: #352b20; border: 2px solid #9c4b30; max-height:60dvh;overflow:auto;border-radius:12px;display:flex;flex-wrap:wrap;gap:12px; }
.command-recovery p {width:100%;line-height:1.6}.command-recovery button { min-height: 48px; padding: 8px 12px;border:1px solid #9c4b30;border-radius:8px;background:#fffaf0; }
</style>
