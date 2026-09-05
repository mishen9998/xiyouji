import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { roomApi, multiplayerBattleApi, getCurrentUsername } from '@/api/room'
import { useRoomStore } from './room'
import type { RoomDTO } from '@/types'
const { connect, disconnect } = vi.hoisted(() => ({ connect: vi.fn(), disconnect: vi.fn() }))
vi.mock('@/composables/useStomp', () => ({ useStomp: () => ({ connect, disconnect }) }))
function dto(version = 1): RoomDTO {
  return { code: 'TEST1234', hostUserId: '玩家甲', stateVersion: version, status: 'WAITING',
    players: [{ userId: '玩家甲', username: '玩家甲', characterClass: 'SUN_WUKONG', ready: false, host: true }],
    playerCount: 1, createdAt: '', floor: 1 }
}
const conflict = { status: 409, code: 'STATE_VERSION_CONFLICT' }
beforeEach(() => {
  vi.useFakeTimers()
  setActivePinia(createPinia())
  sessionStorage.clear()
  const bytes = new TextEncoder().encode(JSON.stringify({ sub: '玩家甲' }))
  localStorage.setItem('xiyouji_jwt_token', 'x.' + btoa(String.fromCharCode(...bytes)) + '.x')
  connect.mockResolvedValue(undefined)
})
afterEach(() => { useRoomStore().reset(); vi.useRealTimers() })
it('decodes Chinese identity correctly and recognizes the host', () => {
  const store = useRoomStore(); store.room = dto()
  expect(getCurrentUsername()).toBe('玩家甲')
  expect(store.isHost).toBe(true)
})
it('refreshes a stale version and retries ready once', async () => {
  const store = useRoomStore(); store.room = dto()
  const updated = dto(3); updated.players[0].ready = true
  const ready = vi.spyOn(roomApi, 'toggleReady').mockRejectedValueOnce(conflict).mockResolvedValue(updated)
  vi.spyOn(roomApi, 'getRoom').mockResolvedValue(dto(2))
  await store.toggleReady()
  expect(ready.mock.calls.map(call => call[1])).toEqual([1, 2])
  expect(store.room!.players[0].ready).toBe(true)
})
it('does not toggle again when refreshed state already matches the requested state', async () => {
  const store = useRoomStore(); store.room = dto()
  const updated = dto(2); updated.players[0].ready = true
  const ready = vi.spyOn(roomApi, 'toggleReady').mockRejectedValue(conflict)
  vi.spyOn(roomApi, 'getRoom').mockResolvedValue(updated)
  await store.toggleReady()
  expect(ready).toHaveBeenCalledTimes(1)
  expect(store.room!.players[0].ready).toBe(true)
})
it('also recovers character and start commands from version conflicts', async () => {
  const store = useRoomStore(); store.room = dto()
  const selected = dto(3); selected.players[0].characterClass = 'SHA_SENG'
  vi.spyOn(roomApi, 'getRoom').mockResolvedValue(dto(2))
  const select = vi.spyOn(roomApi, 'selectCharacter').mockRejectedValueOnce(conflict).mockResolvedValue(selected)
  await store.selectCharacter('SHA_SENG')
  expect(select.mock.calls.map(call => call[2])).toEqual([1, 2])
  const started = dto(5); started.status = 'IN_MAP'
  vi.mocked(roomApi.getRoom).mockResolvedValue(dto(4))
  const start = vi.spyOn(roomApi, 'startGame').mockRejectedValueOnce(conflict).mockResolvedValue(started)
  await store.startGame()
  expect(start.mock.calls.map(call => call[1])).toEqual([3, 4])
  expect(store.room!.status).toBe('IN_MAP')
})
it('polls authoritative state after missed messages and updates connection status', async () => {
  const store = useRoomStore(); store.room = dto()
  const get = vi.spyOn(roomApi, 'getRoom').mockResolvedValue(dto())
  const battle = vi.spyOn(multiplayerBattleApi, 'getBattleState')
  await store.connectWs('TEST1234')
  await vi.advanceTimersByTimeAsync(0)
  const changed = dto(2)
  changed.players.push({ ...changed.players[0], userId: 'B', username: 'B', host: false })
  get.mockResolvedValue(changed)
  const status = connect.mock.calls[0][5]
  status(true); expect(store.connected).toBe(true)
  status(false); expect(store.connected).toBe(false)
  await vi.advanceTimersByTimeAsync(5000)
  expect(store.room!.players).toHaveLength(2)
  expect(battle).not.toHaveBeenCalled()
  store.reset()
  const count = get.mock.calls.length
  await vi.advanceTimersByTimeAsync(10000)
  expect(get).toHaveBeenCalledTimes(count)
})
it('restores room membership after page refresh without calling join again', async () => {
  sessionStorage.setItem('xiyouji_room:玩家甲', 'TEST1234')
  vi.spyOn(roomApi, 'getRoom').mockResolvedValue(dto())
  const join = vi.spyOn(roomApi, 'joinRoom')
  await useRoomStore().restoreRoom()
  expect(useRoomStore().room!.code).toBe('TEST1234')
  expect(useRoomStore().isHost).toBe(true)
  expect(join).not.toHaveBeenCalled()
})
it('does not let a late REST response resurrect a room after leaving', async () => {
  const store = useRoomStore(); store.room = dto()
  let finish!: (value: RoomDTO) => void
  vi.spyOn(roomApi, 'getRoom').mockImplementation(() => new Promise(resolve => { finish = resolve }))
  const refresh = store.refreshRoomState()
  store.reset()
  finish(dto(10)); await refresh
  expect(store.room).toBeNull()
})
