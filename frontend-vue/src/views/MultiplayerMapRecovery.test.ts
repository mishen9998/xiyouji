import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import MultiplayerMapView from './MultiplayerMapView.vue'
import { useRoomStore } from '@/stores/room'
import type { MultiplayerBattleInfo, RoomDTO } from '@/types'

const { replace, route } = vi.hoisted(() => ({ replace: vi.fn(), route: { params: { code: 'FOLLOW01' } } }))
vi.mock('vue-router', () => ({ useRouter: () => ({ replace, push: vi.fn() }), useRoute: () => route }))
beforeEach(() => {
  setActivePinia(createPinia()); route.params.code = 'FOLLOW01'
  vi.stubGlobal('ResizeObserver', class { observe() {} disconnect() {} })
})
afterEach(() => { useRoomStore().reset(); vi.restoreAllMocks(); vi.unstubAllGlobals() })
function setup(status: RoomDTO['status'] = 'IN_MAP') {
  const store = useRoomStore()
  store.room = { code: 'FOLLOW01', hostUserId: 'host', status, players: [], playerCount: 0,
    createdAt: '', floor: 1, stateVersion: 10, battleId: 'second', battleGeneration: 10 } as RoomDTO
  const read = vi.spyOn(store, 'refreshBattleState').mockImplementation(async () => {
    store.battleInfo = { roomCode: 'FOLLOW01', battleId: 'second', battleGeneration: 10, stateVersion: 1 } as MultiplayerBattleInfo
  })
  const start = vi.spyOn(store, 'startBattle')
  const wrapper = mount(MultiplayerMapView, { global: { stubs: { MapNodeComponent: true, TempleShop: true, BranchEventChoices: true } } })
  return { store, read, start, wrapper }
}
it('follows the first guest battle only after a read, without issuing start', async () => {
  const { store, read, start, wrapper } = setup()
  store.room!.status = 'IN_BATTLE'
  await flushPromises()
  expect(read).toHaveBeenCalledOnce(); expect(start).not.toHaveBeenCalled()
  expect(replace).toHaveBeenCalledWith('/room/FOLLOW01/battle')
  wrapper.unmount()
})
it('follows an IN_BATTLE room restored after a missed transition', async () => {
  const { read, start, wrapper } = setup('IN_BATTLE')
  await flushPromises()
  expect(read).toHaveBeenCalledOnce(); expect(start).not.toHaveBeenCalled()
  expect(replace).toHaveBeenCalledWith('/room/FOLLOW01/battle')
  wrapper.unmount()
})
it('does not let a late battle read redirect a changed room URL', async () => {
  const { store, read, wrapper } = setup()
  let finish!: () => void
  read.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
  store.room!.status = 'IN_BATTLE'; await flushPromises()
  route.params.code = 'OTHER002'
  store.battleInfo = { roomCode: 'FOLLOW01', battleId: 'second' } as MultiplayerBattleInfo
  finish(); await flushPromises()
  expect(replace).not.toHaveBeenCalled()
  wrapper.unmount()
})
it('opens the completion illustration only for the current finished room', async () => {
  const { store, read, wrapper } = setup()
  store.room!.status = 'FINISHED'; await flushPromises()
  expect(replace).toHaveBeenCalledOnce()
  expect(replace).toHaveBeenCalledWith('/room/FOLLOW01/complete')
  expect(read).not.toHaveBeenCalled()
  wrapper.unmount()
})
it('does not let a different room completion redirect the current URL', async () => {
  const { store, wrapper } = setup()
  route.params.code = 'OTHER002'
  store.room!.status = 'FINISHED'; await flushPromises()
  expect(replace).not.toHaveBeenCalled()
  wrapper.unmount()
})
