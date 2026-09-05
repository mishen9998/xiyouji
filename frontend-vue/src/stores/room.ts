import { getCurrentUsername } from '@/api/room'
import { createCommandRetry } from '@/api/retryCommand'
// ====== 多人房间 Pinia Store ======
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { roomApi, multiplayerBattleApi } from '@/api/room'
import { useStomp } from '@/composables/useStomp'
import { useUiStore } from './ui'
import type { RoomDTO, MultiplayerBattleInfo, CharacterClass } from '@/types'

export const useRoomStore = defineStore('room', () => {
  const retryCommand = createCommandRetry()
  const uiStore = useUiStore()
  const stomp = useStomp()

  // ====== State ======
  const room = ref<RoomDTO | null>(null)
  const battleInfo = ref<MultiplayerBattleInfo | null>(null)
  const systemMessages = ref<string[]>([])
  const connected = ref(false)
  const seenEventIds = new Set<string>()
  let syncTimer: ReturnType<typeof setInterval> | null = null
  let syncGeneration = 0
  let stopFocusSync: (() => void) | null = null

  function rememberRoom() {
    const user = getCurrentUsername()
    if (user && room.value) sessionStorage.setItem(`xiyouji_room:${user}`, room.value.code)
  }

  function startRoomSync(code: string) {
    stopRoomSync()
    const generation = syncGeneration
    let syncing = false
    const reconcile = async () => {
      if (syncing || generation !== syncGeneration || room.value?.code !== code) return
      syncing = true
      try {
        await refreshRoomState()
        if (generation === syncGeneration && room.value?.status === 'IN_BATTLE') await refreshBattleState()
      } finally { syncing = false }
    }
    const onVisible = () => { if (document.visibilityState === 'visible') void reconcile() }
    window.addEventListener('focus', onVisible)
    document.addEventListener('visibilitychange', onVisible)
    stopFocusSync = () => {
      window.removeEventListener('focus', onVisible)
      document.removeEventListener('visibilitychange', onVisible)
    }
    syncTimer = setInterval(() => { void reconcile() }, 5000)
    void reconcile()
  }

  function stopRoomSync() {
    syncGeneration++
    if (syncTimer) clearInterval(syncTimer)
    syncTimer = null
    stopFocusSync?.()
    stopFocusSync = null
  }

  // ====== Getters ======
  const roomCode = computed(() => room.value?.code ?? '')
  const isHost = computed(() => !!room.value && room.value.hostUserId === getCurrentUsername())
  const canStart = computed(() => {
    if (!room.value || room.value.players.length < 1) return false
    return room.value.players.every(p => p.ready && p.characterClass)
  })
  const allReady = computed(() => {
    if (!room.value) return false
    return room.value.players.length > 0 &&
           room.value.players.every(p => p.ready)
  })

  function applyRoom(next: RoomDTO) {
    if (room.value && room.value.code !== next.code) return
    if (next.eventId && seenEventIds.has(next.eventId)) return
    if (next.eventId) {
      seenEventIds.add(next.eventId)
      if (seenEventIds.size > 200) seenEventIds.delete(seenEventIds.values().next().value as string)
    }
    if (!room.value || next.stateVersion >= room.value.stateVersion) room.value = next
  }

  function applyBattle(next: MultiplayerBattleInfo) {
    if (!room.value || room.value.code !== next.roomCode) return
    if (next.eventId && seenEventIds.has(next.eventId)) return
    if (next.eventId) {
      seenEventIds.add(next.eventId)
      if (seenEventIds.size > 200) seenEventIds.delete(seenEventIds.values().next().value as string)
    }
    if (!battleInfo.value || next.stateVersion >= battleInfo.value.stateVersion) battleInfo.value = next
  }

  async function recoverFromConflict(error: any): Promise<never> {
    if (error?.status === 409 || error?.code === 'STATE_VERSION_CONFLICT') {
      await refreshRoomState()
      if (room.value?.status === 'IN_BATTLE') await refreshBattleState()
      uiStore.showToast('房间状态已更新，请根据最新状态重新操作')
    }
    throw error
  }

  // ====== Actions ======

  /** 开始游戏（房主生成地图） */
  async function startGame() {
    if (!room.value) throw new Error('房间不存在')
    const code = room.value.code
    try {
      return await lobbyCommand(`start:${code}`, key => roomApi.startGame(code, room.value!.stateVersion, key),
        () => room.value?.status === 'IN_MAP')
    } catch (error: any) {
      uiStore.showToast(error?.message || '开始游戏失败')
      throw error
    }
  }

  // Retry only a rejected version check. A successful ready toggle is never repeated.
  async function lobbyCommand(identity: string, send: (key: string) => Promise<RoomDTO>, alreadyApplied: () => boolean) {
    if (!room.value) throw new Error('房间不存在')
    const code = room.value.code
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        const dto = await retryCommand(identity, send)
        if (room.value?.code === code) applyRoom(dto)
        return dto
      } catch (error: any) {
        if (error?.code !== 'STATE_VERSION_CONFLICT') throw error
        await refreshRoomState(true)
        if (room.value?.code !== code) throw error
        if (alreadyApplied()) return room.value
        if (attempt === 1) throw new Error('房间状态正在变化，请再试一次')
      }
    }
    throw new Error('房间操作失败')
  }

  /** 移动到地图节点 */
  async function moveToNode(nodeId: string): Promise<string> {
    if (!room.value) return ''
    try {
      const result = await roomApi.moveToNode(room.value.code, nodeId, room.value.stateVersion)
      applyRoom(result.room)
      return result.eventType
    } catch (e: any) {
      uiStore.showToast(e?.message || '移动失败')
      return recoverFromConflict(e)
    }
  }

  /** 处理节点事件 */
  async function handleEvent(action: string, params?: { cardId?: number; cardIndex?: number }) {
    if (!room.value) return
    try {
      const result = await retryCommand(`event:${room.value.code}:${room.value.currentNode?.id}:${action}:${JSON.stringify(params)}`, key => roomApi.handleEvent(room.value!.code, action, params, room.value!.stateVersion, key))
      // 更新房间玩家状态
      if (result.room) applyRoom(result.room)
      if (result.stateVersion !== undefined && room.value) {
        room.value.stateVersion = Math.max(room.value.stateVersion, result.stateVersion)
      }
      if (result.players) {
        if (room.value) {
          room.value.players = result.players
        }
      }
      return result
    } catch (e: any) {
      uiStore.showToast(e?.message || '事件处理失败')
      return recoverFromConflict(e)
    }
  }

  /** 进入下一层（房主） */
  async function nextLayer() {
    if (!room.value) return
    try {
      const result = await roomApi.nextLayer(room.value.code, room.value.stateVersion)
      // 刷新房间状态
      const dto = await roomApi.getRoom(room.value.code)
      applyRoom(dto)
      return result
    } catch (e: any) {
      uiStore.showToast(e?.message || '进入下一层失败')
      return recoverFromConflict(e)
    }
  }

  /** 刷新房间状态 */
  async function refreshRoomState(throwOnError = false) {
    const code = room.value?.code
    if (!code) return
    try {
      const dto = await roomApi.getRoom(code)
      if (room.value?.code === code) applyRoom(dto)
    } catch (error: any) {
      if (error?.code === 'ROOM_NOT_FOUND' && room.value?.code === code) {
        reset()
        uiStore.showToast('房间已结束，请重新创建或加入')
      }
      if (throwOnError) throw error
    }
  }

  async function restoreRoom() {
    const user = getCurrentUsername()
    const code = user ? sessionStorage.getItem(`xiyouji_room:${user}`) : null
    if (!code) return
    try {
      const dto = await roomApi.getRoom(code)
      if (!dto.players.some(player => player.userId === user)) {
        sessionStorage.removeItem(`xiyouji_room:${user}`)
        return
      }
      room.value = dto
      try { await connectWs(code) } catch { /* REST reconciliation remains active. */ }
      return dto
    } catch (error: any) {
      if (error?.code === 'ROOM_NOT_FOUND') sessionStorage.removeItem(`xiyouji_room:${user}`)
    }
  }

  /** 创建房间 */
  async function createRoom() {
    let dto
    try {
      dto = await roomApi.createRoom()
    } catch (e: any) {
      uiStore.showToast('创建房间失败: ' + (e?.message || ''))
      throw e
    }
    room.value = dto
    try {
      await connectWs(dto.code)
    } catch (wsErr) {
      console.warn('WebSocket 连接失败（不影响创建房间）:', wsErr)
      uiStore.showToast('房间已创建，实时连接正在恢复，仍可继续操作')
    }
    return dto
  }

  /** 加入房间 */
  async function joinRoom(code: string) {
    let dto
    try {
    dto = await roomApi.joinRoom(code)
    } catch (e: any) {
      uiStore.showToast('加入房间失败: ' + (e?.message || ''))
      throw e
    }
    // 加入成功后才设置 room + 连 WS；WS 失败不影响加入结果
    room.value = dto
    try {
      await connectWs(dto.code)
    } catch (wsErr) {
      console.warn('WebSocket 连接失败（不影响加入房间）:', wsErr)
      uiStore.showToast('已加入房间，实时连接正在恢复，仍可继续操作')
    }
    return dto
  }

  /** 退出房间 */
  async function leaveRoom() {
    if (!room.value) return
    const code = room.value.code
    try {
      await refreshRoomState(true)
      if (!room.value) return
      await retryCommand(`leave:${code}`, key => roomApi.leaveRoom(code, room.value!.stateVersion, key))
      reset()
    } catch (error: any) {
      uiStore.showToast(error?.message || '退出失败，请重试')
      throw error
    }
  }

  /** 切换准备状态 */
  async function toggleReady() {
    if (!room.value) return
    const code = room.value.code
    const user = getCurrentUsername()
    const player = room.value.players.find(p => p.userId === user)
    if (!player) throw new Error('当前玩家不在房间中')
    const desired = !player.ready
    try {
      await lobbyCommand(`ready:${code}:${desired}`, key => roomApi.toggleReady(code, room.value!.stateVersion, key),
        () => room.value?.players.find(p => p.userId === user)?.ready === desired)
    } catch (error: any) {
      uiStore.showToast(error?.message || '准备失败')
      throw error
    }
  }

  async function selectCharacter(charClass: CharacterClass) {
    if (!room.value) return
    const code = room.value.code
    const user = getCurrentUsername()
    try {
      await lobbyCommand(`character:${code}:${charClass}`, key => roomApi.selectCharacter(code, charClass, room.value!.stateVersion, key),
        () => room.value?.players.find(p => p.userId === user)?.characterClass === charClass)
    } catch (error: any) {
      uiStore.showToast(error?.message || '选择角色失败')
      throw error
    }
  }

  /** 开始战斗（仅房主） */
  async function startBattle() {
    if (!room.value) return
    try {
      const info = await multiplayerBattleApi.startBattle(room.value.code, room.value.stateVersion)
      applyBattle(info)
      return info
    } catch (e: any) {
      uiStore.showToast(e?.message || '开始战斗失败')
      return recoverFromConflict(e)
    }
  }

  /** 出牌 */
  async function playCard(handIndex: number) {
    if (!room.value) return
    try {
      const info = await multiplayerBattleApi.playCard(room.value.code, handIndex,
        battleInfo.value?.stateVersion ?? room.value.stateVersion)
      applyBattle(info)
      return info
    } catch (e: any) {
      uiStore.showToast(e?.message || '出牌失败')
      return recoverFromConflict(e)
    }
  }

  /** 结束回合 */
  async function endTurn() {
    if (!room.value) return
    try {
      const info = await multiplayerBattleApi.endTurn(room.value.code,
        battleInfo.value?.stateVersion ?? room.value.stateVersion)
      applyBattle(info)
      return info
    } catch (e: any) {
      uiStore.showToast(e?.message || '操作失败')
      return recoverFromConflict(e)
    }
  }

  /** 刷新战斗状态 */
  async function refreshBattleState() {
    const code = room.value?.code
    if (!code) return
    try {
      const info = await multiplayerBattleApi.getBattleState(code)
      if (room.value?.code === code) applyBattle(info)
    } catch { /* Reconcile again on the next connection or polling cycle. */ }
  }

  /** 领取宝物奖励（选1张卡） */
  async function claimReward(cardName: string) {
    if (!room.value) return
    try {
      const info = await retryCommand(`reward:${room.value.code}:${room.value.currentNode?.id}:${cardName}`, key => multiplayerBattleApi.claimReward(room.value!.code, cardName,
        battleInfo.value?.stateVersion ?? room.value!.stateVersion, key))
      applyBattle(info)
    } catch (e: any) {
      uiStore.showToast(e?.message || '领取奖励失败')
      return recoverFromConflict(e)
    }
  }

  async function skipReward() {
    if (!room.value) throw new Error('房间不存在')
    try {
      const info = await retryCommand(`skip:${room.value.code}:${room.value.currentNode?.id}`, key => multiplayerBattleApi.skipReward(room.value!.code,
        battleInfo.value?.stateVersion ?? room.value!.stateVersion, key))
      applyBattle(info)
    } catch (e: any) {
      uiStore.showToast(e?.message || '跳过奖励失败')
      return recoverFromConflict(e)
    }
  }

  /** 进入下一层（仅房主） */
  async function nextFloor() {
    if (!room.value) return
    try {
      const info = await retryCommand(`next:${room.value.code}`, key => multiplayerBattleApi.nextFloor(room.value!.code,
        battleInfo.value?.stateVersion ?? room.value!.stateVersion, key))
      return info
    } catch (e: any) {
      uiStore.showToast(e?.message || '进入下一层失败')
      return recoverFromConflict(e)
    }
  }

  /** 连接 WebSocket */
  async function connectWs(code: string) {
    rememberRoom()
    startRoomSync(code)
    await stomp.connect(
      code,
      (dto) => { applyRoom(dto) },           // 房间更新
      (info) => { applyBattle(info) },     // 战斗更新
      (msg) => {                                  // 系统消息
        systemMessages.value.push(msg)
        if (systemMessages.value.length > 20) {
          systemMessages.value.shift()
        }
      },
      async () => {
        // Pub/Sub is transient; reconcile authoritative state after every
        // initial connection and reconnect.
        await refreshRoomState()
        if (room.value?.status === 'IN_BATTLE') await refreshBattleState()
      },
      (value) => { if (room.value?.code === code) connected.value = value },
    )
  }

  /** 断开 WebSocket */
  function disconnect() {
    stopRoomSync()
    stomp.disconnect()
    connected.value = false
  }

  /** 重置状态 */
  function reset() {
    const user = getCurrentUsername()
    if (user) sessionStorage.removeItem(`xiyouji_room:${user}`)
    seenEventIds.clear()
    disconnect()
    room.value = null
    battleInfo.value = null
    systemMessages.value = []
  }

  return {
    // state
    room, battleInfo, systemMessages, connected,
    // getters
    roomCode, isHost, canStart, allReady,
    // actions
    createRoom, joinRoom, restoreRoom, leaveRoom, toggleReady, selectCharacter,
    startGame, moveToNode, handleEvent, nextLayer, refreshRoomState,
    startBattle, playCard, endTurn, refreshBattleState,
    claimReward, skipReward, nextFloor,
    connectWs, disconnect, reset,
  }
})
