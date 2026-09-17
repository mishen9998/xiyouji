import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { multiplayerBattleApi, roomApi } from '@/api/room'
import { useRoomStore } from './room'
import type { MultiplayerBattleInfo, RoomDTO } from '@/types'
const { connect, disconnect } = vi.hoisted(() => ({ connect: vi.fn(), disconnect: vi.fn() }))
vi.mock('@/composables/useStomp', () => ({ useStomp: () => ({ connect, disconnect }) }))
const code = 'BATTLE01'
function room(id = 'A', generation = 10, version = generation, status: RoomDTO['status'] = 'IN_BATTLE'): RoomDTO {
  return { code, hostUserId: 'host', players: [{ userId: 'host', username: 'host', ready: true, host: true, characterClass: 'SUN_WUKONG' }],
    playerCount: 1, createdAt: '', floor: 1, stateVersion: version, status, battleId: id, battleGeneration: generation,
    currentNode: { id: `node-${id}` } as RoomDTO['currentNode'] }
}
function battle(id = 'A', generation = 10, version = 6): MultiplayerBattleInfo {
  return { roomCode: code, battleId: id, battleGeneration: generation, encounterId: `node-${id}`, stateVersion: version,
    turnNumber: 1, playerTurn: true, battleOver: id === 'A', victory: id === 'A', rewardsHandled: id === 'A',
    enemy: { name: id === 'A' ? '寅将军' : '熊山君', hp: id === 'A' ? 0 : 48 }, players: [], combatLog: [],
    alivePlayerCount: 2, playersEndedTurn: 0 } as unknown as MultiplayerBattleInfo
}
async function connected(initial = room()) {
  const store = useRoomStore(); store.room = initial
  await store.connectWs(code)
  const callbacks = connect.mock.calls[connect.mock.calls.length - 1]!
  return { store, roomFrame: callbacks[1], battleFrame: callbacks[2], reconnect: callbacks[4] }
}
beforeEach(() => { vi.useFakeTimers(); setActivePinia(createPinia()); connect.mockResolvedValue(undefined) })
afterEach(() => { useRoomStore().reset(); vi.restoreAllMocks(); vi.useRealTimers() })

it('accepts second battle v1 after first reward v6 but rejects old battle v999 and same-battle v0', async () => {
  const { store, roomFrame, battleFrame } = await connected()
  battleFrame(battle()); expect(store.battleInfo?.enemy.name).toBe('寅将军')
  roomFrame(room('B', 20)); battleFrame(battle('B', 20, 1))
  expect(store.battleInfo?.enemy.name).toBe('熊山君'); expect(store.battleInfo?.enemy.hp).toBe(48)
  expect(store.battleInfo?.battleOver).toBe(false)
  battleFrame(battle('A', 10, 999)); battleFrame(battle('B', 20, 0))
  expect(store.battleInfo?.stateVersion).toBe(1)
  battleFrame(battle('B', 20, 2)); expect(store.battleInfo?.stateVersion).toBe(2)
})
it('accepts battle-before-room ordering and ignores a delayed old room transition', async () => {
  const { store, roomFrame, battleFrame } = await connected(room('A', 10, 12, 'IN_MAP'))
  battleFrame(battle('B', 20, 1)); expect(store.battleInfo?.battleId).toBe('B')
  roomFrame(room('A', 10, 13, 'IN_MAP')); expect(store.battleInfo?.battleId).toBe('B')
  roomFrame(room('B', 20)); expect(store.room?.battleId).toBe('B')
  battleFrame(battle('A', 10, 999)); expect(store.battleInfo?.battleId).toBe('B')
})
it('room-before-battle invalidates old display and late old frames before fetching the new encounter', async () => {
  const { store, roomFrame, battleFrame } = await connected()
  battleFrame(battle()); roomFrame(room('B', 20))
  expect(store.battleInfo).toBeNull()
  battleFrame(battle('A', 10, 999)); expect(store.battleInfo).toBeNull()
  vi.spyOn(multiplayerBattleApi, 'getBattleState').mockResolvedValue(battle('B', 20, 1))
  await store.refreshBattleState(); expect(store.battleInfo?.battleId).toBe('B')
})
it('a late REST response from the first battle cannot replace the second and does not block its read', async () => {
  const { store, roomFrame } = await connected()
  let finish!: (value: MultiplayerBattleInfo) => void
  const get = vi.spyOn(multiplayerBattleApi, 'getBattleState').mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
    .mockResolvedValueOnce(battle('B', 20, 1))
  const old = store.refreshBattleState(); roomFrame(room('B', 20))
  await store.refreshBattleState(); expect(get).toHaveBeenCalledTimes(2)
  finish(battle('A', 10, 999)); await old
  expect(store.battleInfo?.battleId).toBe('B')
})
it('retains the completed generation boundary while on map, then accepts a new generation', async () => {
  const { store, roomFrame, battleFrame } = await connected()
  battleFrame(battle()); roomFrame(room('A', 10, 12, 'IN_MAP'))
  expect(store.battleInfo).toBeNull()
  battleFrame(battle('A', 10, 999)); expect(store.battleInfo).toBeNull()
  battleFrame(battle('B', 20, 1)); expect(store.battleInfo?.battleId).toBe('B')
})
it('reconnect reconciles a whole missed transition via REST without another subscription connection', async () => {
  const { store, battleFrame, reconnect } = await connected()
  battleFrame(battle())
  vi.spyOn(roomApi, 'getRoom').mockResolvedValue(room('B', 20))
  vi.spyOn(multiplayerBattleApi, 'getBattleState').mockResolvedValue(battle('B', 20, 1))
  await reconnect()
  expect(store.battleInfo?.battleId).toBe('B'); expect(connect).toHaveBeenCalledTimes(1)
  battleFrame(battle('A', 10, 999)); expect(store.battleInfo?.battleId).toBe('B')
})
it('same generation with a different identity cannot replace the current battle', async () => {
  const { store, battleFrame } = await connected()
  battleFrame(battle()); battleFrame(battle('unknown', 10, 1000))
  expect(store.battleInfo?.battleId).toBe('A')
})
it('old snapshots with deterministic legacy identity keep version protection and yield to the first new generation', async () => {
  const legacyRoom = { ...room('A', 0), battleId: undefined, battleGeneration: undefined }
  const { store, battleFrame, roomFrame } = await connected(legacyRoom)
  battleFrame({ ...battle('A', 0), battleId: 'legacy:BATTLE01:node-A' })
  battleFrame({ ...battle('A', 0, 1), battleId: 'legacy:BATTLE01:node-A' })
  expect(store.battleInfo?.stateVersion).toBe(6)
  roomFrame(room('B', 20)); battleFrame(battle('B', 20, 1))
  battleFrame({ ...battle('A', 0, 999), battleId: 'legacy:BATTLE01:node-A' })
  expect(store.battleInfo?.battleId).toBe('B')
})
it('unidentified legacy wire data is only accepted via unchanged-context REST, never a late push', async () => {
  const { store, battleFrame, roomFrame } = await connected({ ...room(), battleId: undefined, battleGeneration: undefined })
  const legacy = { ...battle(), battleId: undefined, battleGeneration: undefined }
  const get = vi.spyOn(multiplayerBattleApi, 'getBattleState').mockResolvedValue(legacy)
  await store.refreshBattleState(); expect(store.battleInfo?.stateVersion).toBe(6)
  roomFrame(room('B', 20)); battleFrame(battle('B', 20, 1))
  battleFrame({ ...legacy, stateVersion: 999 }); await store.refreshBattleState()
  expect(get).toHaveBeenCalled(); expect(store.battleInfo?.battleId).toBe('B')
})
it('callbacks and REST from a previous navigation cannot contaminate a reopened room of the same code', async () => {
  const { store, battleFrame } = await connected()
  let finish!: (value: MultiplayerBattleInfo) => void
  vi.spyOn(multiplayerBattleApi, 'getBattleState').mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
  const old = store.refreshBattleState(); store.reset(); store.room = room('B', 20); await store.connectWs(code)
  connect.mock.calls[connect.mock.calls.length - 1]![2](battle('B', 20, 1)); battleFrame(battle('A', 10, 999))
  finish(battle('A', 10, 999)); await old
  expect(store.battleInfo?.battleId).toBe('B')
})
