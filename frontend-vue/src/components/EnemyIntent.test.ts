import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import EnemyIntent from './EnemyIntent.vue'
import { describeIntent, intentTargets, type EnemyIntentState } from './enemyIntent'

const players = [{ userId: 'a', username: '悟空', index: 0 }, { userId: 'b', username: '八戒', index: 1 }]
describe('server-owned enemy telegraph', () => {
  it.each([
    ['ATTACK', '单体攻击'], ['ATTACK_ALL', '全体攻击'], ['MULTI_HIT', '连击'],
    ['DEFEND', '防御'], ['ATTACK_DEFEND', '攻防兼备'], ['GAIN_STRENGTH', '力量强化'], ['APPLY_STATUS', '施加状态'],
  ])('labels %s without collapsing it into legacy intent', (actionType, label) => {
    expect(describeIntent({ intent: 'SPECIAL', intentEffects: { actionType } }).label).toBe(label)
  })
  it('shows fixed names, hit count, status, guard and authoritative per-target HP loss', () => {
    const enemy: EnemyIntentState = {
      intent: 'ATTACK', intentValue: 9, intentHits: 3, intentTargetUserIds: ['b'], targetPlayerIndex: 0,
      intentEffects: { actionType: 'MULTI_HIT', block: 7, statuses: { WEAK: 2 }, targetDamage: { b: { damagePerHit: 13, hits: 3, blockAbsorbed: 10, hpLoss: 29 } } },
    }
    const text = mount(EnemyIntent, { props: { enemy, players } }).text()
    expect(text).toContain('目标：八戒')
    expect(text).toContain('9 伤害 × 3 次')
    expect(text).toContain('获得 7 格挡')
    expect(text).toContain('虚弱 2 层')
    expect(text).toContain('13 × 3')
    expect(text).toContain('挡 10')
    expect(text).toContain('预计失去 29 生命')
    expect(intentTargets(enemy, players)).toEqual(['b'])
  })
  it('refreshes forecast after weak without changing the server target', async () => {
    const wrapper = mount(EnemyIntent, { props: { enemy: { intentValue: 9, intentHits: 3, intentTargetUserIds: ['a'], intentEffects: { actionType: 'MULTI_HIT' } }, players } })
    await wrapper.setProps({ enemy: { intentValue: 6, intentHits: 3, intentTargetUserIds: ['a'], intentEffects: { actionType: 'MULTI_HIT' } } })
    expect(wrapper.text()).toContain('6 伤害 × 3 次')
    expect(wrapper.text()).toContain('目标：悟空')
  })
  it('supports legacy forecasts and does not invent targets for self actions', () => {
    expect(intentTargets({ intent: 'ATTACK', targetPlayerIndex: 1 }, players)).toEqual(['b'])
    expect(describeIntent({ intent: 'DEFEND', intentValue: 8 }, players).details).toEqual(['获得 8 格挡'])
    expect(describeIntent({ intentEffects: { actionType: 'GAIN_STRENGTH', strength: 2 } }, players).targetLabel).toBe('敌人自身')
    expect(intentTargets({ intent: 'ATTACK', intentTargetUserIds: [], targetPlayerIndex: 0 }, players)).toEqual([])
  })
  it('keeps all multi-target numbers while presenting my result first in a keyboard-scrollable list', () => {
    const wrapper = mount(EnemyIntent, { props: { players, focusUserId: 'b', enemy: { intentValue: 8, intentTargetUserIds: ['a', 'b'], intentEffects: { actionType: 'ATTACK_ALL', targetDamage: {
      a: { damagePerHit: 8, hits: 1, blockAbsorbed: 3, hpLoss: 5 }, b: { damagePerHit: 12, hits: 1, blockAbsorbed: 0, hpLoss: 12 },
    } } } } })
    expect(wrapper.findAll('.intent-damage li')).toHaveLength(2)
    expect(wrapper.findAll('.intent-damage li')[0].text()).toContain('八戒（你）')
    expect(wrapper.findAll('.intent-damage li')[0].text()).toContain('预计失去 12 生命')
    expect(wrapper.findAll('.intent-damage li')[1].text()).toContain('挡 3')
    expect(wrapper.get('.intent-damage').attributes('tabindex')).toBe('0')
    expect(wrapper.get('.intent-target').text()).toContain('悟空、八戒')
  })
})
