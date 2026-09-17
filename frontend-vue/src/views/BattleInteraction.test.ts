import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { mount, flushPromises } from '@vue/test-utils'
import BattleView from './BattleView.vue'
import MultiplayerBattleView from './MultiplayerBattleView.vue'
import { useGameStore } from '@/stores/game'
import { useRoomStore } from '@/stores/room'
import type { BattleInfo, RoomDTO, MultiplayerBattleInfo } from '@/types'

vi.mock('vue-router', () => ({ useRoute: () => ({ params: { code: 'TEST' } }), useRouter: () => ({ push: vi.fn() }) }))
const hand = [{ id: 1, name: '挥棒', type: 'ATTACK', cost: 1, damage: 6, block: 0, drawCards: 0, description: '造成6点伤害', upgraded: false }, { id: 2, name: '格挡', type: 'DEFENSE', cost: 2, damage: 0, block: 5, drawCards: 0, description: '获得5点格挡', upgraded: false }]
beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); sessionStorage.clear(); localStorage.setItem('xiyouji_jwt_token', 'x.' + btoa(JSON.stringify({ sub: 'me' })) + '.x') })
function setupSolo() {
  const store = useGameStore()
  store.sessionId = 'solo'
  store.battleInfo = { stateVersion: 1, turnNumber: 1, inBattle: true, playerTurn: true, battleOver: false, victory: false,
    player: { characterClass: 'SUN_WUKONG', displayName: '悟空', hp: 50, maxHp: 70, energy: 1, maxEnergy: 3, block: 0, hand, relics: [] },
    enemy: { name: '寅将军', hp: 30, maxHp: 30, intent: 'ATTACK', intentValue: 5, isBoss: false }, combatLog: [],
  } as unknown as BattleInfo
  store.player = store.battleInfo.player
  return store
}
function setupCoop() {
  const store = useRoomStore()
  store.room = { code: 'TEST', hostUserId: 'me', stateVersion: 1, players: [], status: 'IN_BATTLE' } as unknown as RoomDTO
  store.battleInfo = { roomCode: 'TEST', stateVersion: 1, turnNumber: 1, playerTurn: true, battleOver: false, enemy: { name: '寅将军', hp: 30, maxHp: 30, intent: 'ATTACK', intentValue: 5, buffs: {} },
    players: [{ userId: 'me', username: '悟空', characterClass: 'SUN_WUKONG', index: 0, hp: 50, maxHp: 70, energy: 1, maxEnergy: 3, alive: true, endedTurn: false, hand: hand.map((card, index) => ({ ...card, index })), buffs: {}, block: 0 }], combatLog: [],
  } as unknown as MultiplayerBattleInfo
  vi.spyOn(store, 'refreshBattleState').mockResolvedValue(undefined)
  return store
}
const stubs = { BattleCharacter3D: true, BattleResultModal: true, DeckModal: true }
describe('single-click battle input', () => {
  it('solo click posts immediately and prevents duplicate or overlapping commands', async () => {
    const store = setupSolo()
    let finish!: () => void
    const play = vi.spyOn(store, 'playCard').mockImplementation(() => new Promise<void>(resolve => { finish = resolve }) as never)
    const wrapper = mount(BattleView, { global: { stubs } })
    await wrapper.get('.game-card').trigger('click')
    await wrapper.get('.game-card').trigger('click')
    expect(wrapper.find('.confirm-card-btn').exists()).toBe(false)
    expect(wrapper.find('.view-toggle').exists()).toBe(false)
    expect(wrapper.findComponent({ name: 'BattleCharacter3D' }).exists()).toBe(true)
    expect(play).toHaveBeenCalledExactlyOnceWith(0)
    expect(wrapper.get('.end-turn-btn').attributes('disabled')).toBeDefined()
    finish(); await flushPromises()
    expect(wrapper.get('.game-card').attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })
  it('insufficient energy disables submission while keeping card effects readable', async () => {
    const store = setupSolo()
    const play = vi.spyOn(store, 'playCard')
    const wrapper = mount(BattleView, { global: { stubs } })
    await wrapper.findAll('.game-card')[1].trigger('click')
    expect(wrapper.findAll('.game-card')[1].attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('.game-card')[1].text()).toContain('获得5点格挡')
    expect(play).not.toHaveBeenCalled()
    expect(wrapper.get('.game-card').element.tagName).toBe('BUTTON')
    wrapper.unmount()
  })
  it('coop plays immediately and never automatically replays an unknown result', async () => {
    const store = setupCoop()
    let reject!: (error: Error) => void
    const play = vi.spyOn(store, 'playCard').mockImplementation(() => new Promise((_, fail) => { reject = fail }))
    const wrapper = mount(MultiplayerBattleView, { global: { stubs } }); await flushPromises()
    expect(wrapper.get('.enemy-avatar img').attributes('src')).toBeTruthy()
    expect(wrapper.get('.player-avatar img').attributes('src')).toBeTruthy()
    await wrapper.get('.player-avatar img').trigger('error')
    expect(wrapper.find('.player-avatar img').exists()).toBe(false)
    expect(wrapper.get('.player-avatar .responsive-image__fallback').text()).toBe('🐵')
    await wrapper.get('.mp-card').trigger('click')
    await wrapper.get('.mp-card').trigger('click')
    expect(wrapper.find('.confirm-card-btn').exists()).toBe(false)
    expect(play).toHaveBeenCalledExactlyOnceWith(0)
    expect(wrapper.get('.btn-end-turn').attributes('disabled')).toBeDefined()
    reject(new Error('RESULT_UNKNOWN')); await flushPromises()
    expect(play).toHaveBeenCalledTimes(1)
    expect(wrapper.findComponent({ name: 'BattleCharacter3D' }).exists()).toBe(true)
    wrapper.unmount()
  })
  it('coop ended player cannot play or end again', async () => {
    const store = setupCoop()
    store.battleInfo!.players[0].endedTurn = true
    const play = vi.spyOn(store, 'playCard')
    const wrapper = mount(MultiplayerBattleView, { global: { stubs } }); await flushPromises()
    await wrapper.get('.mp-card').trigger('click')
    expect(wrapper.get('.mp-card').attributes('disabled')).toBeDefined()
    expect(wrapper.get('.btn-end-turn').attributes('disabled')).toBeDefined()
    expect(wrapper.get('.action-hint').text()).toContain('等待队友')
    expect(play).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  it('number keys play directly but held keys do not repeat', async () => {
    const store = setupSolo()
    const play = vi.spyOn(store, 'playCard').mockResolvedValue(undefined as never)
    const wrapper = mount(BattleView, { global: { stubs } })
    document.dispatchEvent(new KeyboardEvent('keydown', { key: '1' }))
    await flushPromises()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: '1', repeat: true }))
    await flushPromises()
    expect(play).toHaveBeenCalledExactlyOnceWith(0)
    wrapper.unmount()
  })
})
