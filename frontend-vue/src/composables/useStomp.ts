// WebSocket transport. Room state is also reconciled by REST in the room store.
import { Client, TickerStrategy } from '@stomp/stompjs'
import type { RoomDTO, MultiplayerBattleInfo } from '@/types'
import { getTokenForWs } from '@/api/room'

export function useStomp() {
  let client: Client | null = null
  let cancelPending: (() => void) | null = null
  let notifyConnection: ((connected: boolean) => void) | undefined

  async function connect(
    roomCode: string,
    onRoomUpdate: (room: RoomDTO) => void,
    onBattleUpdate: (battle: MultiplayerBattleInfo) => void,
    onSystemMsg?: (message: string) => void,
    onConnected?: () => void | Promise<void>,
    onConnectionChange?: (connected: boolean) => void,
  ): Promise<void> {
    disconnect()
    notifyConnection = onConnectionChange
    onConnectionChange?.(false)
    let resolveFirst!: () => void
    let rejectFirst!: (error: Error) => void
    const firstConnection = new Promise<void>((resolve, reject) => {
      resolveFirst = resolve
      rejectFirst = reject
    })
    // Return control to the lobby even if the server never completes the handshake.
    const timeout = setTimeout(() => rejectFirst(new Error('实时连接超时，正在自动重连')), 8000)
    cancelPending = () => { clearTimeout(timeout); rejectFirst(new Error('连接已取消')) }
    const socket = new Client({
      reconnectDelay: 3000,
      connectionTimeout: 7000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      heartbeatStrategy: typeof Worker === 'undefined' ? TickerStrategy.Interval : TickerStrategy.Worker,
      heartbeatToleranceMultiplier: 3,
      discardWebsocketOnCommFailure: true,
      beforeConnect: async () => {
        const token = await getTokenForWs()
        if (client !== socket) return
        const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
        socket.brokerURL = `${protocol}://${location.host}/ws?token=${encodeURIComponent(token)}`
      },
      onConnect: () => {
        if (client !== socket) return
        // STOMP subscriptions belong to a connection; always recreate them after reconnect.
        socket.subscribe(`/topic/room/${roomCode}`, msg => {
          if (client !== socket) return
          try {
            const data = JSON.parse(msg.body)
            if (data.type === 'SYSTEM_MESSAGE') onSystemMsg?.(data.message)
            else onRoomUpdate(data as RoomDTO)
          } catch (error) { console.warn('Invalid room update', error) }
        })
        socket.subscribe(`/topic/room/${roomCode}/battle`, msg => {
          if (client !== socket) return
          try { onBattleUpdate(JSON.parse(msg.body) as MultiplayerBattleInfo) }
          catch (error) { console.warn('Invalid battle update', error) }
        })
        clearTimeout(timeout)
        cancelPending = null
        onConnectionChange?.(true)
        resolveFirst()
        Promise.resolve(onConnected?.()).catch(error => console.warn('Room reconciliation failed', error))
      },
      onWebSocketClose: () => { if (client === socket) onConnectionChange?.(false) },
      onWebSocketError: () => { if (client === socket) onConnectionChange?.(false) },
      onStompError: () => {
        if (client !== socket) return
        onConnectionChange?.(false)
        clearTimeout(timeout)
        rejectFirst(new Error('实时连接失败，正在重试'))
        socket.forceDisconnect()
      },
    })
    client = socket
    socket.activate()
    await firstConnection
  }

  function disconnect() {
    cancelPending?.()
    cancelPending = null
    const previous = client
    client = null
    notifyConnection?.(false)
    notifyConnection = undefined
    if (previous) void previous.deactivate().catch(error => console.warn('Disconnect failed', error))
  }

  return { connect, disconnect, isConnected: () => client?.connected ?? false }
}
