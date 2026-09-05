import { createCommandRetry } from '@/api/retryCommand'
// ====== 游戏全局状态 Store ======
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, gameApi } from '@/api/game'
import { useUiStore } from './ui'
import {
  addGuestSaveSlot,
  getGuestSaveSlots,
  guestSlotsAreFull,
  removeGuestSaveSlot,
  type GuestSaveSlot,
} from './guestSaves'
import type {
  CharacterClass, Player, MapNode as GameMapNode,
  BattleInfo, Rewards, GameState
} from '@/types'

const SESSION_KEY = 'xiyouji_session_id'

export class GuestSaveLimitError extends Error {
  readonly slots: GuestSaveSlot[]

  constructor(slots: GuestSaveSlot[]) {
    super('游客最多保留三个存档，请选择一个覆盖')
    this.name = 'GuestSaveLimitError'
    this.slots = slots
  }
}

export const useGameStore = defineStore('game', () => {
  const retryCommand = createCommandRetry()
  const uiStore = useUiStore()
  // ====== State ======
  const sessionId = ref<string | null>(null)
  const selectedCharacter = ref<CharacterClass | null>(null)
  const player = ref<Player | null>(null)
  const mapNodes = ref<GameMapNode[]>([])
  const currentNode = ref<GameMapNode | null>(null)
  const currentLayer = ref(1)
  const maxLayer = ref(3)
  const inBattle = ref(false)
  const battleInfo = ref<BattleInfo | null>(null)
  const rewards = ref<Rewards | null>(null)
  const bonfireUpgradesLeft = ref(2)
  const stateVersion = ref(0)

  // ====== Getters ======
  const isPlayerAlive = computed(() => (player.value?.hp ?? 0) > 0)
  const hasSession = computed(() => !!sessionId.value)

  // ====== Actions ======

  function sessionStorageKey() {
    const profile = authApi.getProfile()
    if (!profile) return SESSION_KEY
    const identity = profile.account || profile.username
    return `${SESSION_KEY}:${profile.role}:${identity}`
  }

  function saveSessionLocal() {
    if (sessionId.value) {
      try { localStorage.setItem(sessionStorageKey(), sessionId.value) } catch (e) { /* ignore */ }
    }
  }

  function clearSessionLocal() {
    try { localStorage.removeItem(sessionStorageKey()) } catch (e) { /* ignore */ }
  }

  function getSavedSessionId(): string | null {
    try {
      const scoped = localStorage.getItem(sessionStorageKey())
      if (scoped) return scoped
      // 兼容升级前只有一个全局存档指针的浏览器。
      return localStorage.getItem(SESSION_KEY)
    } catch (e) { return null }
  }

  async function startNewGame(charClass: CharacterClass, replaceSessionId?: string) {
    const profile = authApi.getProfile()
    const isGuest = profile?.role === 'GUEST'
    const existingSlots = isGuest ? getGuestSaveSlots() : []
    if (isGuest && guestSlotsAreFull() && !replaceSessionId) {
      throw new GuestSaveLimitError(existingSlots)
    }
    if (replaceSessionId && !existingSlots.some(slot => slot.sessionId === replaceSessionId)) {
      throw new Error('要覆盖的游客存档不存在')
    }

    let replacedVersion: number | null = null
    if (replaceSessionId) {
      try {
        const previous = await gameApi.getState(replaceSessionId)
        replacedVersion = previous.stateVersion ?? 0
      } catch {
        // 旧存档可能已过期；仍允许用新存档替换本地槽位。
      }
    }

    const data = await gameApi.newGame(charClass)
    sessionId.value = data.sessionId
    stateVersion.value = data.stateVersion ?? 0
    player.value = data.player
    mapNodes.value = data.map
    currentNode.value = data.currentNode
    selectedCharacter.value = charClass
    currentLayer.value = 1
    maxLayer.value = 3
    inBattle.value = false
    battleInfo.value = null
    saveSessionLocal()

    if (isGuest) {
      addGuestSaveSlot({
        sessionId: data.sessionId,
        characterClass: charClass,
        createdAt: new Date().toISOString(),
      }, replaceSessionId)
      if (replaceSessionId && replacedVersion !== null) {
        try { await gameApi.deleteSession(replaceSessionId, replacedVersion) } catch { /* 新存档已成功，不回滚 */ }
      }
    }
  }

  async function refreshState() {
    if (!sessionId.value) return
    try {
      const data: GameState = await gameApi.getState(sessionId.value)
      stateVersion.value = data.stateVersion ?? stateVersion.value
      player.value = data.player
      mapNodes.value = data.map
      currentNode.value = data.currentNode
      currentLayer.value = data.currentLayer || 1
      maxLayer.value = data.maxLayer || 3
    } catch (e) {
      console.error('Refresh failed:', e)
    }
  }

  async function recoverFromConflict(error: any): Promise<never> {
    if (error?.status === 409 || error?.code === 'STATE_VERSION_CONFLICT') {
      await refreshState()
      uiStore.showToast('游戏状态已更新，请根据最新状态重新操作')
    }
    throw error
  }

  async function loadSavedSession(requestedSessionId?: string): Promise<GameState | null> {
    const savedId = requestedSessionId || getSavedSessionId()
    if (!savedId) return null
    try {
      const data: GameState = await gameApi.getState(savedId)
      sessionId.value = savedId
      stateVersion.value = data.stateVersion ?? 0
      player.value = data.player
      mapNodes.value = data.map
      currentNode.value = data.currentNode
      selectedCharacter.value = data.player?.characterClass ?? null
      currentLayer.value = data.currentLayer || 1
      maxLayer.value = data.maxLayer || 3
      inBattle.value = data.inBattle
      saveSessionLocal()
      return data
    } catch (e: any) {
      console.error('loadSavedSession error:', e)
      // 不清除 localStorage — 让用户可以重试或服务器恢复后再次加载
      return null
    }
  }

  async function restoreBattleState() {
    if (!sessionId.value) return
    try {
      const bi = await gameApi.getBattleState(sessionId.value)
      battleInfo.value = bi
      stateVersion.value = bi.stateVersion ?? stateVersion.value
      inBattle.value = true
      if (bi.rewards) rewards.value = bi.rewards
    } catch (e) {
      console.error('Restore battle failed:', e)
    }
  }

  async function deleteSavedSession(requestedSessionId?: string) {
    const savedId = requestedSessionId || getSavedSessionId()
    if (!savedId) return
    let version = stateVersion.value
    if (savedId !== sessionId.value) {
      try {
        const saved = await gameApi.getState(savedId)
        version = saved.stateVersion ?? 0
      } catch {
        removeGuestSaveSlot(savedId)
        return
      }
    }
    try {
      await gameApi.deleteSession(savedId, version)
    } catch (error) {
      await recoverFromConflict(error)
    }
    removeGuestSaveSlot(savedId)
    if (getSavedSessionId() === savedId) clearSessionLocal()
    if (sessionId.value === savedId) {
      sessionId.value = null
      stateVersion.value = 0
      player.value = null
      inBattle.value = false
      battleInfo.value = null
    }
  }

  async function moveToNode(nodeId: string): Promise<string> {
    if (!sessionId.value) throw new Error('No session')
    let data
    try {
      data = await gameApi.move(sessionId.value, nodeId, stateVersion.value)
    } catch (error) {
      return recoverFromConflict(error)
    }
    stateVersion.value = data.stateVersion ?? stateVersion.value
    currentNode.value = data.node

    if (data.eventType === 'bonfire') {
      bonfireUpgradesLeft.value = 2
    }
    return data.eventType
  }

  async function startBattle() {
    if (!sessionId.value) {
      throw new Error('未找到会话，请重新加载游戏')
    }
    let bi
    try { bi = await gameApi.startBattle(sessionId.value, stateVersion.value) }
    catch (error) { return recoverFromConflict(error) }
    stateVersion.value = bi.stateVersion ?? stateVersion.value
    battleInfo.value = bi
    inBattle.value = true
    if (bi.rewards) rewards.value = bi.rewards
  }

  async function playCard(handIndex: number) {
    if (!sessionId.value) return
    let bi
    try { bi = await gameApi.playCard(sessionId.value, handIndex, stateVersion.value) }
    catch (error) { return recoverFromConflict(error) }
    stateVersion.value = bi.stateVersion ?? stateVersion.value
    battleInfo.value = bi
    if (bi.rewards) rewards.value = bi.rewards
  }

  async function endTurn() {
    if (!sessionId.value) return
    let bi
    try { bi = await gameApi.endTurn(sessionId.value, stateVersion.value) }
    catch (error) { return recoverFromConflict(error) }
    stateVersion.value = bi.stateVersion ?? stateVersion.value
    battleInfo.value = bi
    if (bi.rewards) rewards.value = bi.rewards
  }

  async function chooseCardReward(cardIndex: number) {
    if (!sessionId.value) return
    let data
    try { data = await retryCommand(`reward:${sessionId.value}:${currentNode.value?.id}:${cardIndex}`, key => gameApi.chooseCardReward(sessionId.value!, cardIndex, stateVersion.value, key)) }
    catch (error) { return recoverFromConflict(error) }
    stateVersion.value = data.stateVersion ?? stateVersion.value
    if (data.player) player.value = data.player
    if (data.success && rewards.value) rewards.value.resolved = true
    return data
  }

  async function skipReward() {
    if (!sessionId.value) throw new Error('游戏会话不存在')
    try {
      const data = await retryCommand(`skip:${sessionId.value}:${currentNode.value?.id}`, key => gameApi.skipReward(sessionId.value!, stateVersion.value, key))
      stateVersion.value = data.stateVersion ?? stateVersion.value
      if (rewards.value) rewards.value.resolved = true
      return data
    } catch (error) { return recoverFromConflict(error) }
  }

  async function nextLayer(): Promise<{ success: boolean; currentLayer?: number }> {
    if (!sessionId.value) return { success: false }
    let data
    try { data = await retryCommand(`next:${sessionId.value}`, key => gameApi.nextLayer(sessionId.value!, stateVersion.value, key)) }
    catch (error) { return recoverFromConflict(error) }
    stateVersion.value = data.stateVersion ?? stateVersion.value
    if (data.success) {
      currentLayer.value = data.currentLayer || currentLayer.value + 1
      return { success: true, currentLayer: data.currentLayer }
    }
    return { success: false }
  }

  async function handleEvent(action: string, params?: {
    cardIndex?: number; cardId?: number; price?: number; relicName?: string
  }) {
    if (!sessionId.value) return null
    let data
    try { data = await retryCommand(`event:${sessionId.value}:${currentNode.value?.id}:${action}:${JSON.stringify(params)}`, key => gameApi.handleEvent(sessionId.value!, action, params, stateVersion.value, key)) }
    catch (error) { return recoverFromConflict(error) }
    stateVersion.value = data.stateVersion ?? stateVersion.value
    if (data.player) player.value = data.player
    if (data.bonfireUpgradesLeft !== undefined) bonfireUpgradesLeft.value = data.bonfireUpgradesLeft
    return data
  }

  async function upgradeCard(cardIndex: number) {
    if (!sessionId.value) return null
    let data
    try { data = await retryCommand(`upgrade:${sessionId.value}:${currentNode.value?.id}:${cardIndex}`, key => gameApi.handleEvent(sessionId.value!, 'upgrade', { cardIndex }, stateVersion.value, key)) }
    catch (error) { return recoverFromConflict(error) }
    stateVersion.value = data.stateVersion ?? stateVersion.value
    if (data.bonfireUpgradesLeft !== undefined) bonfireUpgradesLeft.value = data.bonfireUpgradesLeft
    if (data.player) player.value = data.player
    return data
  }

  function resetBattle() {
    inBattle.value = false
    battleInfo.value = null
    rewards.value = null
  }

  function clearAll(preserveSavedSession = false) {
    sessionId.value = null
    selectedCharacter.value = null
    player.value = null
    mapNodes.value = []
    currentNode.value = null
    stateVersion.value = 0
    inBattle.value = false
    battleInfo.value = null
    rewards.value = null
    if (!preserveSavedSession) clearSessionLocal()
  }

  return {
    // state
    sessionId, selectedCharacter, player, mapNodes, currentNode, stateVersion,
    currentLayer, maxLayer, inBattle, battleInfo, rewards, bonfireUpgradesLeft,
    // getters
    isPlayerAlive, hasSession,
    // actions
    startNewGame, refreshState, loadSavedSession, restoreBattleState,
    deleteSavedSession, moveToNode, startBattle, playCard, endTurn,
    chooseCardReward, skipReward, nextLayer, handleEvent, upgradeCard,
    resetBattle, clearAll, getSavedSessionId, saveSessionLocal, clearSessionLocal,
    getGuestSaveSlots,
  }
})
