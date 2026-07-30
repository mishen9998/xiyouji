// ====== 游戏全局状态 Store ======
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { gameApi } from '@/api/game'
import type {
  CharacterClass, Player, MapNode as GameMapNode,
  BattleInfo, Rewards, GameState
} from '@/types'

const SESSION_KEY = 'xiyouji_session_id'

export const useGameStore = defineStore('game', () => {
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

  // ====== Getters ======
  const isPlayerAlive = computed(() => (player.value?.hp ?? 0) > 0)
  const hasSession = computed(() => !!sessionId.value)

  // ====== Actions ======

  function saveSessionLocal() {
    if (sessionId.value) {
      try { localStorage.setItem(SESSION_KEY, sessionId.value) } catch (e) { /* ignore */ }
    }
  }

  function clearSessionLocal() {
    try { localStorage.removeItem(SESSION_KEY) } catch (e) { /* ignore */ }
  }

  function getSavedSessionId(): string | null {
    try { return localStorage.getItem(SESSION_KEY) } catch (e) { return null }
  }

  async function startNewGame(charClass: CharacterClass) {
    const data = await gameApi.newGame(charClass)
    sessionId.value = data.sessionId
    player.value = data.player
    mapNodes.value = data.map
    currentNode.value = data.currentNode
    selectedCharacter.value = charClass
    currentLayer.value = 1
    maxLayer.value = 3
    inBattle.value = false
    battleInfo.value = null
    saveSessionLocal()
  }

  async function refreshState() {
    if (!sessionId.value) return
    try {
      const data: GameState = await gameApi.getState(sessionId.value)
      player.value = data.player
      mapNodes.value = data.map
      currentNode.value = data.currentNode
      currentLayer.value = data.currentLayer || 1
      maxLayer.value = data.maxLayer || 3
    } catch (e) {
      console.error('Refresh failed:', e)
    }
  }

  async function loadSavedSession(): Promise<GameState | null> {
    const savedId = getSavedSessionId()
    if (!savedId) return null
    try {
      const data: GameState = await gameApi.getState(savedId)
      sessionId.value = savedId
      player.value = data.player
      mapNodes.value = data.map
      currentNode.value = data.currentNode
      selectedCharacter.value = data.player?.characterClass ?? null
      currentLayer.value = data.currentLayer || 1
      maxLayer.value = data.maxLayer || 3
      inBattle.value = data.inBattle
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
      inBattle.value = true
      if (bi.rewards) rewards.value = bi.rewards
    } catch (e) {
      console.error('Restore battle failed:', e)
    }
  }

  async function deleteSavedSession() {
    const savedId = getSavedSessionId()
    if (!savedId) return
    await gameApi.deleteSession(savedId)
    clearSessionLocal()
    if (sessionId.value === savedId) {
      sessionId.value = null
      player.value = null
      inBattle.value = false
      battleInfo.value = null
    }
  }

  async function moveToNode(nodeId: string): Promise<string> {
    if (!sessionId.value) throw new Error('No session')
    const data = await gameApi.move(sessionId.value, nodeId)
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
    const bi = await gameApi.startBattle(sessionId.value)
    battleInfo.value = bi
    inBattle.value = true
    if (bi.rewards) rewards.value = bi.rewards
  }

  async function playCard(handIndex: number) {
    if (!sessionId.value) return
    const bi = await gameApi.playCard(sessionId.value, handIndex)
    battleInfo.value = bi
    if (bi.rewards) rewards.value = bi.rewards
  }

  async function endTurn() {
    if (!sessionId.value) return
    const bi = await gameApi.endTurn(sessionId.value)
    battleInfo.value = bi
    if (bi.rewards) rewards.value = bi.rewards
  }

  async function chooseCardReward(cardIndex: number) {
    if (!sessionId.value) return
    const data = await gameApi.chooseCardReward(sessionId.value, cardIndex)
    if (data.player) player.value = data.player
    return data
  }

  async function nextLayer(): Promise<{ success: boolean; currentLayer?: number }> {
    if (!sessionId.value) return { success: false }
    const data = await gameApi.nextLayer(sessionId.value)
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
    const data = await gameApi.handleEvent(sessionId.value, action, params)
    if (data.player) player.value = data.player
    if (data.bonfireUpgradesLeft !== undefined) bonfireUpgradesLeft.value = data.bonfireUpgradesLeft
    return data
  }

  async function upgradeCard(cardIndex: number) {
    if (!sessionId.value) return null
    const data = await gameApi.handleEvent(sessionId.value, 'upgrade', { cardIndex })
    if (data.bonfireUpgradesLeft !== undefined) bonfireUpgradesLeft.value = data.bonfireUpgradesLeft
    if (data.player) player.value = data.player
    await refreshState()
    return data
  }

  function resetBattle() {
    inBattle.value = false
    battleInfo.value = null
    rewards.value = null
  }

  function clearAll() {
    sessionId.value = null
    selectedCharacter.value = null
    player.value = null
    mapNodes.value = []
    currentNode.value = null
    inBattle.value = false
    battleInfo.value = null
    rewards.value = null
    clearSessionLocal()
  }

  return {
    // state
    sessionId, selectedCharacter, player, mapNodes, currentNode,
    currentLayer, maxLayer, inBattle, battleInfo, rewards, bonfireUpgradesLeft,
    // getters
    isPlayerAlive, hasSession,
    // actions
    startNewGame, refreshState, loadSavedSession, restoreBattleState,
    deleteSavedSession, moveToNode, startBattle, playCard, endTurn,
    chooseCardReward, nextLayer, handleEvent, upgradeCard,
    resetBattle, clearAll, getSavedSessionId, saveSessionLocal, clearSessionLocal,
  }
})
