import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useRoomStore } from './room'
import { roomApi } from '@/api/room'
import { useUiStore } from './ui'
import type { RoomDTO } from '@/types'

const { connect, disconnect } = vi.hoisted(() => ({ connect: vi.fn(), disconnect: vi.fn() }))
vi.mock('@/composables/useStomp', () => ({ useStomp: () => ({ connect, disconnect }) }))
const code = 'NAVTEST1'
const snapshot = (battleId: string, version: number): RoomDTO => ({ code, hostUserId: 'host', status: 'WAITING',
  stateVersion: version, battleId, battleGeneration: version, playerCount: 1, floor: 1, createdAt: '',
  players: [{ userId: 'host', username: 'host', host: true, ready: false, characterClass: 'SUN_WUKONG' }] })
function deferred<T>() {
  let resolve!: (value: T) => void; let reject!: (reason: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
beforeEach(() => {
  vi.useFakeTimers(); setActivePinia(createPinia()); connect.mockResolvedValue(undefined)
  sessionStorage.clear(); localStorage.setItem('xiyouji_jwt_token', 'x.' + btoa(JSON.stringify({ sub: 'host' })) + '.x')
})
afterEach(() => { useRoomStore().reset(); vi.restoreAllMocks(); vi.useRealTimers() })

// Leave is a real store action: its preflight read finishes, then a poll races its pending POST.
async function leavingWithOldRead() {
  const store = useRoomStore(); store.room = snapshot('A', 10); await store.connectWs(code)
  const old = deferred<RoomDTO>(); const left = deferred<Awaited<ReturnType<typeof roomApi.leaveRoom>>>()
  const get = vi.spyOn(roomApi, 'getRoom').mockResolvedValueOnce(snapshot('A', 10)).mockReturnValueOnce(old.promise)
  const leave = vi.spyOn(roomApi, 'leaveRoom').mockReturnValue(left.promise)
  const leaving = store.leaveRoom()
  await vi.waitFor(() => expect(leave).toHaveBeenCalledOnce())
  const refreshing = store.refreshRoomState()
  left.resolve({ dissolved: false }); await leaving
  expect(store.room).toBeNull()
  return { store, get, old, refreshing }
}
it('reopening after an actual leave uses a new navigation read and ignores old success', async () => {
  const { store, get, old, refreshing } = await leavingWithOldRead()
  get.mockResolvedValueOnce(snapshot('B', 20))
  const reopening = store.openRoom(code)
  old.resolve(snapshot('A', 10)); await refreshing; await reopening
  expect(store.room?.battleId).toBe('B'); expect(get).toHaveBeenCalledTimes(3)
})
it.each([403, 404])('old %s after leave/rejoin does not clear the new authorized room or session', async status => {
  const { store, old, refreshing } = await leavingWithOldRead()
  vi.spyOn(roomApi, 'joinRoom').mockResolvedValue(snapshot('B', 20))
  await store.joinRoom(code)
  const disconnects = disconnect.mock.calls.length
  old.reject({ status, code: status === 403 ? 'ACCESS_DENIED' : 'ROOM_NOT_FOUND' }); await refreshing
  expect(store.room?.battleId).toBe('B'); expect(store.room?.players[0].userId).toBe('host')
  expect(sessionStorage.getItem('xiyouji_room:host')).toBe(code)
  expect(disconnect).toHaveBeenCalledTimes(disconnects)
})
it.each([403, 404])('current navigation %s still clears the ghost room and disconnects', async status => {
  const store = useRoomStore(); store.room = snapshot('B', 20); await store.connectWs(code)
  vi.spyOn(roomApi, 'getRoom').mockRejectedValue({ status })
  const toast = vi.spyOn(useUiStore(), 'showToast')
  await store.refreshRoomState()
  expect(store.room).toBeNull(); expect(sessionStorage.getItem('xiyouji_room:host')).toBeNull()
  expect(toast).toHaveBeenCalledWith('房间已结束，请重新创建或加入')
})
it('concurrent reads in the same navigation share the request and settle to its actual state', async () => {
  const store = useRoomStore(); store.room = snapshot('A', 10)
  const read = deferred<RoomDTO>(); const get = vi.spyOn(roomApi, 'getRoom').mockReturnValueOnce(read.promise)
  const one = store.refreshRoomState(); const two = store.refreshRoomState()
  expect(get).toHaveBeenCalledOnce(); read.resolve(snapshot('B', 20)); await Promise.all([one, two])
  expect(store.room?.battleId).toBe('B')
})
it('old finally cannot evict the new navigation pending read from its deduplication cache', async () => {
  const { store, get, old, refreshing } = await leavingWithOldRead()
  vi.spyOn(roomApi, 'joinRoom').mockResolvedValue(snapshot('B', 20)); await store.joinRoom(code)
  const next = deferred<RoomDTO>(); get.mockReturnValueOnce(next.promise)
  const one = store.refreshRoomState()
  expect(get).toHaveBeenCalledTimes(3)
  old.resolve(snapshot('A', 10)); await refreshing
  const two = store.refreshRoomState()
  expect(get).toHaveBeenCalledTimes(3)
  next.resolve(snapshot('C', 30)); await Promise.all([one, two])
  expect(store.room?.battleId).toBe('C')
})
it('an old openRoom rejection cannot escape into the new successful navigation', async () => {
  const store = useRoomStore(); const old = deferred<RoomDTO>()
  vi.spyOn(roomApi, 'getRoom').mockReturnValueOnce(old.promise).mockResolvedValueOnce({ ...snapshot('B', 20), code: 'OTHER002' })
  const opening = store.openRoom(code)
  await store.openRoom('OTHER002')
  old.reject({ status: 403 }); await expect(opening).resolves.toBeUndefined()
  expect(store.room?.code).toBe('OTHER002'); expect(store.room?.battleId).toBe('B')
})
