// ====== WebSocket STOMP 客户端封装 ======
import { Client, type StompSubscription } from '@stomp/stompjs'
import type { RoomDTO, MultiplayerBattleInfo } from '@/types'
import { getTokenForWs } from '@/api/room'

/**
 * STOMP WebSocket 连接管理
 *
 * 连接：ws://{host}/ws?token={jwt}
 * 订阅频道：
 *   /topic/room/{code}        - 房间状态变化
 *   /topic/room/{code}/battle - 战斗状态变化
 */
export function useStomp() {
  let client: Client | null = null
  let roomSub: StompSubscription | null = null
  let battleSub: StompSubscription | null = null

  /**
   * 连接 WebSocket 并订阅房间频道
   *
   * @param roomCode      房间码
   * @param onRoomUpdate  房间状态更新回调
   * @param onBattleUpdate 战斗状态更新回调
   * @param onSystemMsg   系统消息回调
   */
  async function connect(
    roomCode: string,
    onRoomUpdate: (room: RoomDTO) => void,
    onBattleUpdate: (battle: MultiplayerBattleInfo) => void,
    onSystemMsg?: (message: string) => void,
  ): Promise<void> {
    const token = await getTokenForWs()
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
    const wsUrl = `${protocol}://${location.host}/ws?token=${encodeURIComponent(token)}`

    client = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,

      onConnect: () => {
        // 订阅房间状态频道
        roomSub = client!.subscribe(`/topic/room/${roomCode}`, (msg) => {
          const data = JSON.parse(msg.body)
          if (data.type === 'SYSTEM_MESSAGE' && data.message) {
            onSystemMsg?.(data.message)
          } else {
            // RoomDTO 格式
            onRoomUpdate(data as RoomDTO)
          }
        })

        // 订阅战斗状态频道
        battleSub = client!.subscribe(`/topic/room/${roomCode}/battle`, (msg) => {
          const data = JSON.parse(msg.body)
          onBattleUpdate(data as MultiplayerBattleInfo)
        })
      },

      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message'], frame.body)
      },

      onWebSocketError: (event) => {
        console.error('WebSocket error:', event)
      },
    })

    client.activate()
  }

  /** 断开连接 */
  function disconnect() {
    if (roomSub) {
      roomSub.unsubscribe()
      roomSub = null
    }
    if (battleSub) {
      battleSub.unsubscribe()
      battleSub = null
    }
    if (client) {
      client.deactivate()
      client = null
    }
  }

  /** 是否已连接 */
  function isConnected(): boolean {
    return client?.active ?? false
  }

  return { connect, disconnect, isConnected }
}
