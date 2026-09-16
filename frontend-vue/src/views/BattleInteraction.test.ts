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
const stubs = { BattleCharacter: true, BattleResultModal: true, DeckModal: true }
describe('deliberate battle input', () => {
  it('solo click only previews; confirmation posts once and disables the end-turn action', async () => {
    const store = setupSolo()
    let finish!: () => void
    const play = vi.spyOn(store, 'playCard').mockImplementation(() => new Promise<void>(resolve => { finish = resolve }) as never)
    const wrapper = mount(BattleView, { global: { stubs } })
    await wrapper.get('.game-card').trigger('click')
    expect(play).not.toHaveBeenCalled()
    expect(wrapper.get('.selection-preview').text()).toContain('造成6点伤害')
    await wrapper.get('.confirm-card-btn').trigger('click')
    await wrapper.get('.confirm-card-btn').trigger('click')
    expect(play).toHaveBeenCalledExactlyOnceWith(0)
    expect(wrapper.get('.end-turn-btn').attributes('disabled')).toBeDefined()
    finish(); await flushPromises()
    expect(wrapper.get('.confirm-card-btn').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })
  it('insufficient energy still permits effect inspection but never submission', async () => {
    const store = setupSolo()
    const play = vi.spyOn(store, 'playCard')
    const wrapper = mount(BattleView, { global: { stubs } })
    await wrapper.findAll('.game-card')[1].trigger('click')
    expect(wrapper.get('.selection-preview').text()).toContain('法力不足')
    expect(wrapper.get('.confirm-card-btn').attributes('disabled')).toBeDefined()
    expect(play).not.toHaveBeenCalled()
    expect(wrapper.get('.game-card').element.tagName).toBe('BUTTON')
    wrapper.unmount()
  })
  it('coop uses real art, previews without mutation and retains selection after unknown result', async () => {
    const store = setupCoop()
    let reject!: (error: Error) => void
    const play = vi.spyOn(store, 'playCard').mockImplementation(() => new Promise((_, fail) => { reject = fail }))
    const wrapper = mount(MultiplayerBattleView); await flushPromises()
    expect(wrapper.get('.enemy-avatar img').attributes('src')).toBeTruthy()
    expect(wrapper.get('.player-avatar img').attributes('src')).toBeTruthy()
    await wrapper.get('.player-avatar img').trigger('error')
    expect(wrapper.find('.player-avatar img').exists()).toBe(false)
    expect(wrapper.get('.player-avatar .responsive-image__fallback').text()).toBe('🐵')
    await wrapper.get('.mp-card').trigger('click')
    expect(play).not.toHaveBeenCalled()
    await wrapper.get('.confirm-card-btn').trigger('click')
    await wrapper.get('.confirm-card-btn').trigger('click')
    expect(play).toHaveBeenCalledExactlyOnceWith(0)
    expect(wrapper.get('.btn-end-turn').attributes('disabled')).toBeDefined()
    reject(new Error('RESULT_UNKNOWN')); await flushPromises()
    expect(play).toHaveBeenCalledTimes(1)
    expect(wrapper.get('.mp-card').attributes('aria-pressed')).toBe('true')
    wrapper.unmount()
  })
  it('coop ended player cannot play or end again', async () => {
    const store = setupCoop()
    store.battleInfo!.players[0].endedTurn = true
    const play = vi.spyOn(store, 'playCard')
    const wrapper = mount(MultiplayerBattleView); await flushPromises()
    await wrapper.get('.mp-card').trigger('click')
    expect(wrapper.get('.confirm-card-btn').attributes('disabled')).toBeDefined()
    expect(wrapper.get('.btn-end-turn').attributes('disabled')).toBeDefined()
    expect(wrapper.get('.selection-preview').text()).toContain('等待队友')
    expect(play).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
