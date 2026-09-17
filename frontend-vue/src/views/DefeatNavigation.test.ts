import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import BattleView from './BattleView.vue'
import MultiplayerBattleView from './MultiplayerBattleView.vue'
import HomeView from './HomeView.vue'
import BattleResultModal from '@/components/BattleResultModal.vue'
import { useGameStore } from '@/stores/game'
import { useRoomStore } from '@/stores/room'
import { useUiStore } from '@/stores/ui'
import type { BattleInfo, GameState, MultiplayerBattleInfo, RoomDTO } from '@/types'

const routing = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn(), route: { params: { code: 'TEST' } } }))
vi.mock('vue-router', () => ({ useRoute: () => routing.route, useRouter: () => routing }))
const wrappers: VueWrapper[] = []
const stubs = { BattleCharacter3D: true, DeckModal: true, ResponsiveImage: true }
function render(component: any, props = {}) {
  const wrapper = mount(component, { props, global: { stubs } }); wrappers.push(wrapper); return wrapper
}
beforeEach(() => {
  setActivePinia(createPinia()); localStorage.clear(); sessionStorage.clear(); vi.clearAllMocks()
  localStorage.setItem('xiyouji_jwt_token', 'x.' + btoa(JSON.stringify({ sub: 'me' })) + '.x')
})
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); vi.restoreAllMocks() })
function solo(over = false, victory = false) {
  const store = useGameStore()
  store.sessionId = 'solo'; store.saveSessionLocal()
  store.battleInfo = { battleOver: over, victory, playerTurn: !over, turnNumber: 1,
    player: { hp: over && !victory ? 0 : 50, maxHp: 75, hand: [], characterClass: 'SUN_WUKONG' },
    enemy: { name: '寅将军', hp: 30, maxHp: 30, intent: 'ATTACK', intentValue: 6 },
  } as unknown as BattleInfo
  store.player = store.battleInfo.player
  return store
}
function coop(over = false, victory = false) {
  const store = useRoomStore()
  store.room = { code: 'TEST', status: 'IN_BATTLE', players: [] } as unknown as RoomDTO
  store.battleInfo = { roomCode: 'TEST', battleOver: over, victory, playerTurn: !over, turnNumber: 1,
    enemy: { name: '寅将军', hp: 30, maxHp: 30 }, players: [{ userId: 'me', alive: !over, hp: over ? 0 : 50, maxHp: 75, hand: [] }],
  } as unknown as MultiplayerBattleInfo
  vi.spyOn(store, 'refreshBattleState').mockResolvedValue(undefined)
  return store
}

describe('defeat returns to the main menu', () => {
  it('solo enemy turn defeat replaces the route and clears active state, not authentication', async () => {
    const store = solo()
    const toast = vi.spyOn(useUiStore(), 'showToast')
    vi.spyOn(store, 'endTurn').mockImplementation(async () => {
      store.battleInfo = { ...store.battleInfo!, battleOver: true, victory: false, playerTurn: false }
    })
    const wrapper = render(BattleView)
    await wrapper.get('.end-turn-btn').trigger('click'); await flushPromises()
    expect(routing.replace).toHaveBeenCalledExactlyOnceWith('/menu')
    expect(routing.push).not.toHaveBeenCalled()
    expect(store.sessionId).toBeNull(); expect(store.battleInfo).toBeNull()
    expect(store.getSavedSessionId()).toBeNull()
    expect(localStorage.getItem('xiyouji_jwt_token')).toBeTruthy()
    expect(toast).toHaveBeenCalledWith(expect.stringContaining('被击败'))
  })
  it('an already defeated solo snapshot returns immediately without restarting battle', async () => {
    const store = solo(true)
    const start = vi.spyOn(store, 'startBattle')
    const load = vi.spyOn(store, 'loadSavedSession')
    render(BattleView); await flushPromises()
    expect(routing.replace).toHaveBeenCalledExactlyOnceWith('/menu')
    expect(start).not.toHaveBeenCalled(); expect(load).not.toHaveBeenCalled()
  })
  it('cold reload of a dead save never routes through the map', async () => {
    const store = useGameStore()
    vi.spyOn(store, 'loadSavedSession').mockResolvedValue({ player: { hp: 0 }, inBattle: false } as GameState)
    render(BattleView); await flushPromises()
    expect(routing.replace).toHaveBeenCalledExactlyOnceWith('/menu')
    expect(routing.push).not.toHaveBeenCalled()
  })
  it('solo victory still displays rewards without returning to menu', async () => {
    solo(true, true)
    const wrapper = render(BattleView); await flushPromises()
    expect(wrapper.get('#solo-result-title').text()).toContain('胜利')
    expect(routing.replace).not.toHaveBeenCalled()
  })
  it('defeat result fallback never emits a map or reward action', async () => {
    const store = solo(true)
    const claim = vi.spyOn(store, 'chooseCardReward')
    const wrapper = render(BattleResultModal, { visible: true })
    expect(wrapper.get('.continue-btn').text()).toBe('返回主菜单')
    await wrapper.get('.continue-btn').trigger('click')
    expect(wrapper.emitted('return-to-menu')).toHaveLength(1)
    expect(wrapper.emitted('return-to-map')).toBeUndefined()
    expect(claim).not.toHaveBeenCalled()
  })
  it('coop full-team defeat resets subscriptions and replaces the route only once', async () => {
    const store = coop()
    const reset = vi.spyOn(store, 'reset')
    render(MultiplayerBattleView); await flushPromises()
    store.battleInfo!.battleOver = true
    store.battleInfo!.victory = false
    await flushPromises()
    expect(reset).toHaveBeenCalledOnce(); expect(store.room).toBeNull()
    expect(routing.replace).toHaveBeenCalledExactlyOnceWith('/menu')
    expect(routing.push).not.toHaveBeenCalled()
  })
  it('coop restored defeat cannot be overwritten by the loadBattle fallback redirect', async () => {
    const store = coop()
    vi.mocked(store.refreshBattleState).mockImplementation(async () => {
      store.battleInfo = { ...store.battleInfo!, battleOver: true, victory: false }
    })
    render(MultiplayerBattleView); await flushPromises()
    expect(routing.replace).toHaveBeenCalledExactlyOnceWith('/menu')
    expect(routing.push).not.toHaveBeenCalled()
  })
  it('one fallen teammate does not end an ongoing coop battle', async () => {
    const store = coop()
    store.battleInfo!.players[0].alive = false
    store.battleInfo!.players[0].hp = 0
    const wrapper = render(MultiplayerBattleView); await flushPromises()
    expect(wrapper.get('.action-hint').text()).toContain('等待队伍')
    expect(routing.replace).not.toHaveBeenCalled(); expect(store.room).not.toBeNull()
  })
  it('coop victory never triggers the defeat return', async () => {
    const store = coop(true, true)
    render(MultiplayerBattleView); await flushPromises()
    expect(routing.replace).not.toHaveBeenCalled(); expect(store.room).not.toBeNull()
  })
  it('a stale defeated snapshot from another room cannot end this room', async () => {
    const store = coop(true)
    store.battleInfo!.roomCode = 'OTHER'
    render(MultiplayerBattleView); await flushPromises()
    expect(routing.replace).not.toHaveBeenCalled(); expect(store.room?.code).toBe('TEST')
  })
  it('loading a defeated save offers return to menu, not continue game', async () => {
    const store = solo(true)
    vi.spyOn(store, 'loadSavedSession').mockResolvedValue({ player: { hp: 0, maxHp: 75, characterClass: 'SUN_WUKONG' }, inBattle: false } as GameState)
    const confirm = vi.spyOn(useUiStore(), 'showConfirm')
    const wrapper = render(HomeView)
    await wrapper.findAll('.menu-btn')[2].trigger('click'); await flushPromises()
    const options = confirm.mock.calls[0][0]
    expect(options.okText).toBe('返回主菜单')
    expect(store.sessionId).toBeNull() // Cancelling the dialog must not reactivate the dead save either.
    await options.onOk?.()
    expect(store.sessionId).toBeNull(); expect(routing.push).not.toHaveBeenCalled()
  })
})
