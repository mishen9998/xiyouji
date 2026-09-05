import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { useStomp } from './useStomp'
const { sockets } = vi.hoisted(() => ({ sockets: [] as any[] }))
vi.mock('@stomp/stompjs', () => ({
  TickerStrategy: { Interval: 'interval', Worker: 'worker' },
  Client: class {
    connected = false
    subscribe = vi.fn(() => ({ unsubscribe: vi.fn() }))
    deactivate = vi.fn(async () => {})
    activate = vi.fn()
    forceDisconnect = vi.fn()
    constructor(public config: any) { sockets.push(this) }
  },
}))
vi.mock('@/api/room', () => ({ getTokenForWs: async () => 'test-token' }))
beforeEach(() => { vi.useFakeTimers(); sockets.length = 0 })
afterEach(() => { vi.useRealTimers() })
it('reports disconnect and re-subscribes once to each destination on reconnect', async () => {
  const transport = useStomp()
  const status = vi.fn(); const reconcile = vi.fn()
  const connection = transport.connect('TEST', vi.fn(), vi.fn(), vi.fn(), reconcile, status)
  const socket = sockets[0]
  socket.connected = true; socket.config.onConnect(); await connection
  expect(status).toHaveBeenLastCalledWith(true)
  expect(socket.subscribe).toHaveBeenCalledTimes(2)
  socket.connected = false; socket.config.onWebSocketClose()
  expect(status).toHaveBeenLastCalledWith(false)
  expect(transport.isConnected()).toBe(false)
  socket.connected = true; socket.config.onConnect()
  expect(socket.subscribe).toHaveBeenCalledTimes(4)
  expect(reconcile).toHaveBeenCalledTimes(2)
  transport.disconnect()
})
it('a missing handshake times out so the lobby is not blocked forever', async () => {
  const transport = useStomp()
  const outcome = transport.connect('TEST', vi.fn(), vi.fn()).catch(error => error.message)
  await vi.advanceTimersByTimeAsync(8000)
  expect(await outcome).toContain('超时')
  expect(sockets[0].config.connectionTimeout).toBe(7000)
  transport.disconnect()
})
it('replacing a connection stops the old client and ignores its late events', async () => {
  const transport = useStomp(); const firstRoom = vi.fn(); const secondRoom = vi.fn()
  const first = transport.connect('FIRST', firstRoom, vi.fn())
  sockets[0].config.onConnect(); await first
  const oldCallback = sockets[0].subscribe.mock.calls[0][1]
  const second = transport.connect('SECOND', secondRoom, vi.fn())
  sockets[1].config.onConnect(); await second
  oldCallback({ body: JSON.stringify({ code: 'FIRST' }) })
  expect(firstRoom).not.toHaveBeenCalled()
  expect(sockets[0].deactivate).toHaveBeenCalledTimes(1)
  transport.disconnect()
})
