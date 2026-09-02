// ====== 多人房间 Pinia Store ======
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { roomApi, multiplayerBattleApi } from '@/api/room'
import { useStomp } from '@/composables/useStomp'
import { useUiStore } from './ui'
import type { RoomDTO, MultiplayerBattleInfo, CharacterClass } from '@/types'

export const useRoomStore = defineStore('room', () => {
  const uiStore = useUiStore()
  const stomp = useStomp()

  // ====== State ======
  const room = ref<RoomDTO | null>(null)
  const battleInfo = ref<MultiplayerBattleInfo | null>(null)
  const systemMessages = ref<string[]>([])
  const connected = ref(false)
  const seenEventIds = new Set<string>()

  // ====== Getters ======
  const roomCode = computed(() => room.value?.code ?? '')
  const isHost = computed(() => {
    // 当前用户通过 JWT subject 识别，但前端无法直接获取
    // 改为在 joinRoom/createRoom 时标记
    return _isHost.value
  })
  const _isHost = ref(false)
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
    if (next.eventId && seenEventIds.has(next.eventId)) return
    if (next.eventId) {
      seenEventIds.add(next.eventId)
      if (seenEventIds.size > 200) seenEventIds.delete(seenEventIds.values().next().value as string)
    }
    if (!room.value || next.stateVersion >= room.value.stateVersion) room.value = next
  }

  function applyBattle(next: MultiplayerBattleInfo) {
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
      await refreshBattleState()
      uiStore.showToast('房间状态已更新，请根据最新状态重新操作')
    }
    throw error
  }

  // ====== Actions ======

  /** 开始游戏（房主生成地图） */
  async function startGame() {
    if (!room.value) return
    try {
      const dto = await roomApi.startGame(room.value.code, room.value.stateVersion)
      applyRoom(dto)
      return dto
    } catch (e: any) {
      uiStore.showToast(e?.message || '开始游戏失败')
      return recoverFromConflict(e)
    }
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
      const result = await roomApi.handleEvent(room.value.code, action, params, room.value.stateVersion)
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
  async function refreshRoomState() {
    if (!room.value) return
    try {
      const dto = await roomApi.getRoom(room.value.code)
      applyRoom(dto)
    } catch { /* ignore */ }
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
    _isHost.value = true
    try {
      await connectWs(dto.code)
    } catch (wsErr) {
      console.warn('WebSocket 连接失败（不影响创建房间）:', wsErr)
      uiStore.showToast('已创建房间，但实时连接失败，请刷新重试')
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
    _isHost.value = false
    try {
      await connectWs(dto.code)
    } catch (wsErr) {
      console.warn('WebSocket 连接失败（不影响加入房间）:', wsErr)
      uiStore.showToast('已加入房间，但实时连接失败，请刷新重试')
    }
    return dto
  }

  /** 退出房间 */
  async function leaveRoom() {
    if (!room.value) return
    try {
      await roomApi.leaveRoom(room.value.code, room.value.stateVersion)
    } catch { /* ignore */ }
    disconnect()
    room.value = null
    battleInfo.value = null
    systemMessages.value = []
  }

  /** 切换准备状态 */
  async function toggleReady() {
    if (!room.value) return
    try {
      const dto = await roomApi.toggleReady(room.value.code, room.value.stateVersion)
      applyRoom(dto)
    } catch (e: any) {
      uiStore.showToast(e?.message || '操作失败')
    }
  }

  /** 选择角色 */
  async function selectCharacter(charClass: CharacterClass) {
    if (!room.value) return
    try {
      const dto = await roomApi.selectCharacter(room.value.code, charClass, room.value.stateVersion)
      applyRoom(dto)
    } catch (e: any) {
      uiStore.showToast(e?.message || '选择角色失败')
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
    if (!room.value) return
    try {
      const info = await multiplayerBattleApi.getBattleState(room.value.code)
      applyBattle(info)
    } catch { /* ignore */ }
  }

  /** 领取宝物奖励（选1张卡） */
  async function claimReward(cardName: string) {
    if (!room.value) return
    try {
      const info = await multiplayerBattleApi.claimReward(room.value.code, cardName,
        battleInfo.value?.stateVersion ?? room.value.stateVersion)
      applyBattle(info)
    } catch (e: any) {
      uiStore.showToast(e?.message || '领取奖励失败')
      return recoverFromConflict(e)
    }
  }

  /** 进入下一层（仅房主） */
  async function nextFloor() {
    if (!room.value) return
    try {
      const info = await multiplayerBattleApi.nextFloor(room.value.code,
        battleInfo.value?.stateVersion ?? room.value.stateVersion)
      applyBattle(info)
    } catch (e: any) {
      uiStore.showToast(e?.message || '进入下一层失败')
      return recoverFromConflict(e)
    }
  }

  /** 连接 WebSocket */
  async function connectWs(code: string) {
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
        await refreshBattleState()
      },
    )
    connected.value = true
  }

  /** 断开 WebSocket */
  function disconnect() {
    stomp.disconnect()
    connected.value = false
  }

  /** 重置状态 */
  function reset() {
    disconnect()
    room.value = null
    battleInfo.value = null
    systemMessages.value = []
    _isHost.value = false
  }

  return {
    // state
    room, battleInfo, systemMessages, connected,
    // getters
    roomCode, isHost, canStart, allReady,
    // actions
    createRoom, joinRoom, leaveRoom, toggleReady, selectCharacter,
    startGame, moveToNode, handleEvent, nextLayer, refreshRoomState,
    startBattle, playCard, endTurn, refreshBattleState,
    claimReward, nextFloor,
    connectWs, disconnect, reset,
  }
})
