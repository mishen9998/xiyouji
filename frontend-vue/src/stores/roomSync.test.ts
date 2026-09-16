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
it('refreshes a stale version without automatically repeating ready', async () => {
  const store = useRoomStore(); store.room = dto()
  const updated = dto(3); updated.players[0].ready = true
  const ready = vi.spyOn(roomApi, 'toggleReady').mockRejectedValueOnce(conflict).mockResolvedValue(updated)
  vi.spyOn(roomApi, 'getRoom').mockResolvedValue(dto(2))
  await expect(store.toggleReady()).rejects.toEqual(conflict)
  expect(ready.mock.calls.map(call => call[1])).toEqual([1])
  expect(store.room!.stateVersion).toBe(2)
})
it('does not toggle again when refreshed state already matches the requested state', async () => {
  const store = useRoomStore(); store.room = dto()
  const updated = dto(2); updated.players[0].ready = true
  const ready = vi.spyOn(roomApi, 'toggleReady').mockRejectedValue(conflict)
  vi.spyOn(roomApi, 'getRoom').mockResolvedValue(updated)
  await expect(store.toggleReady()).rejects.toEqual(conflict)
  expect(ready).toHaveBeenCalledTimes(1)
  expect(store.room!.players[0].ready).toBe(true)
})
it('also recovers character and start commands from version conflicts', async () => {
  const store = useRoomStore(); store.room = dto()
  const selected = dto(3); selected.players[0].characterClass = 'SHA_SENG'
  vi.spyOn(roomApi, 'getRoom').mockResolvedValue(dto(2))
  const select = vi.spyOn(roomApi, 'selectCharacter').mockRejectedValueOnce(conflict).mockResolvedValue(selected)
  await expect(store.selectCharacter('SHA_SENG')).rejects.toEqual(conflict)
  expect(select.mock.calls.map(call => call[2])).toEqual([1])
  const started = dto(5); started.status = 'IN_MAP'
  vi.mocked(roomApi.getRoom).mockResolvedValue(dto(4))
  const start = vi.spyOn(roomApi, 'startGame').mockRejectedValueOnce(conflict).mockResolvedValue(started)
  await expect(store.startGame()).rejects.toEqual(conflict)
  expect(start.mock.calls.map(call => call[1])).toEqual([2])
  expect(store.room!.stateVersion).toBe(4)
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
it('clears ghost room and remembered code on HTTP 404', async () => {
  const store = useRoomStore(); store.room = dto()
  sessionStorage.setItem('xiyouji_room:玩家甲', 'TEST1234')
  vi.spyOn(roomApi, 'getRoom').mockRejectedValue({ status: 404, code: 'ROOM_NOT_FOUND' })
  await store.refreshRoomState()
  expect(store.room).toBeNull()
  expect(sessionStorage.getItem('xiyouji_room:玩家甲')).toBeNull()
})
it('switches to URL room and discards old room battle state before loading', async () => {
  const store = useRoomStore(); store.room = dto()
  let finish!: (value: RoomDTO) => void
  const get = vi.spyOn(roomApi, 'getRoom').mockImplementation(() => new Promise(resolve => { finish = resolve }))
  const loading = store.openRoom('NEWROOM1')
  expect(store.room).toBeNull(); expect(store.battleInfo).toBeNull()
  finish({ ...dto(1), code: 'NEWROOM1' }); await loading
  expect(get).toHaveBeenCalledWith('NEWROOM1')
  expect(store.roomCode).toBe('NEWROOM1')
  expect(connect).toHaveBeenLastCalledWith('NEWROOM1', expect.any(Function), expect.any(Function), expect.any(Function), expect.any(Function), expect.any(Function))
  await store.openRoom('NEWROOM1'); await store.connectWs('NEWROOM1')
  expect(connect).toHaveBeenCalledTimes(1)
})
it('version advancement after RESULT_UNKNOWN never proves the ready command succeeded', async () => {
  const store = useRoomStore(); store.room = dto()
  const unknown = { status: 409, code: 'RESULT_UNKNOWN' }
  const ready = vi.spyOn(roomApi, 'toggleReady').mockRejectedValue(unknown)
  const latest = dto(99); latest.players[0].ready = true
  vi.spyOn(roomApi, 'getRoom').mockResolvedValue(latest)
  await expect(store.toggleReady()).rejects.toEqual(unknown)
  expect(store.room?.stateVersion).toBe(99)
  expect(ready).toHaveBeenCalledTimes(1)
})
