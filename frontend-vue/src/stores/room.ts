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

  // ====== Actions ======

  /** 开始游戏（房主生成地图） */
  async function startGame() {
    if (!room.value) return
    try {
      const dto = await roomApi.startGame(room.value.code)
      room.value = dto
      return dto
    } catch (e: any) {
      uiStore.showToast(e?.message || '开始游戏失败')
      throw e
    }
  }

  /** 移动到地图节点 */
  async function moveToNode(nodeId: string): Promise<string> {
    if (!room.value) return ''
    try {
      const result = await roomApi.moveToNode(room.value.code, nodeId)
      room.value = result.room
      return result.eventType
    } catch (e: any) {
      uiStore.showToast(e?.message || '移动失败')
      throw e
    }
  }

  /** 处理节点事件 */
  async function handleEvent(action: string, params?: { cardId?: number; cardIndex?: number }) {
    if (!room.value) return
    try {
      const result = await roomApi.handleEvent(room.value.code, action, params)
      // 更新房间玩家状态
      if (result.players) {
        if (room.value) {
          room.value.players = result.players
        }
      }
      return result
    } catch (e: any) {
      uiStore.showToast(e?.message || '事件处理失败')
      throw e
    }
  }

  /** 进入下一层（房主） */
  async function nextLayer() {
    if (!room.value) return
    try {
      const result = await roomApi.nextLayer(room.value.code)
      // 刷新房间状态
      const dto = await roomApi.getRoom(room.value.code)
      room.value = dto
      return result
    } catch (e: any) {
      uiStore.showToast(e?.message || '进入下一层失败')
      throw e
    }
  }

  /** 刷新房间状态 */
  async function refreshRoomState() {
    if (!room.value) return
    try {
      const dto = await roomApi.getRoom(room.value.code)
      room.value = dto
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
      await roomApi.leaveRoom(room.value.code)
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
      const dto = await roomApi.toggleReady(room.value.code)
      room.value = dto
    } catch (e: any) {
      uiStore.showToast(e?.message || '操作失败')
    }
  }

  /** 选择角色 */
  async function selectCharacter(charClass: CharacterClass) {
    if (!room.value) return
    try {
      const dto = await roomApi.selectCharacter(room.value.code, charClass)
      room.value = dto
    } catch (e: any) {
      uiStore.showToast(e?.message || '选择角色失败')
    }
  }

  /** 开始战斗（仅房主） */
  async function startBattle() {
    if (!room.value) return
    try {
      const info = await multiplayerBattleApi.startBattle(room.value.code)
      battleInfo.value = info
      return info
    } catch (e: any) {
      uiStore.showToast(e?.message || '开始战斗失败')
      throw e
    }
  }

  /** 出牌 */
  async function playCard(handIndex: number) {
    if (!room.value) return
    try {
      const info = await multiplayerBattleApi.playCard(room.value.code, handIndex)
      battleInfo.value = info
      return info
    } catch (e: any) {
      uiStore.showToast(e?.message || '出牌失败')
      throw e
    }
  }

  /** 结束回合 */
  async function endTurn() {
    if (!room.value) return
    try {
      const info = await multiplayerBattleApi.endTurn(room.value.code)
      battleInfo.value = info
      return info
    } catch (e: any) {
      uiStore.showToast(e?.message || '操作失败')
      throw e
    }
  }

  /** 刷新战斗状态 */
  async function refreshBattleState() {
    if (!room.value) return
    try {
      const info = await multiplayerBattleApi.getBattleState(room.value.code)
      battleInfo.value = info
    } catch { /* ignore */ }
  }

  /** 领取宝物奖励（选1张卡） */
  async function claimReward(cardName: string) {
    if (!room.value) return
    try {
      const info = await multiplayerBattleApi.claimReward(room.value.code, cardName)
      battleInfo.value = info
    } catch (e: any) {
      uiStore.showToast(e?.message || '领取奖励失败')
      throw e
    }
  }

  /** 进入下一层（仅房主） */
  async function nextFloor() {
    if (!room.value) return
    try {
      const info = await multiplayerBattleApi.nextFloor(room.value.code)
      battleInfo.value = info
    } catch (e: any) {
      uiStore.showToast(e?.message || '进入下一层失败')
      throw e
    }
  }

  /** 连接 WebSocket */
  async function connectWs(code: string) {
    await stomp.connect(
      code,
      (dto) => { room.value = dto },           // 房间更新
      (info) => { battleInfo.value = info },     // 战斗更新
      (msg) => {                                  // 系统消息
        systemMessages.value.push(msg)
        if (systemMessages.value.length > 20) {
          systemMessages.value.shift()
        }
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
