import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import BranchEventChoices from './BranchEventChoices.vue'
import type { EventPreview } from '@/types'
const event: EventPreview = {
  eventInstanceId: 'one', definitionId: 'fork-road', title: '双叉岭迷途', text: '故事', resolved: false,
  options: [
    { id: 'option1', label: '支付生命', enabled: false, disabledReason: '生命不足', members: { player: { hpCost: 9, goldCost: 0, reward: 'UPGRADE', amount: 1, goldReward: 0, targetName: '挥棒' } } },
    { id: 'option2', label: '治疗', enabled: true, members: {} },
    { id: 'leave', label: '离开', enabled: true, members: {} },
  ],
}
describe('branch events', () => {
  it('shows locked target and reason, rejects disabled branch, allows leave', async () => {
    const wrapper = mount(BranchEventChoices, { props: { event } })
    expect(wrapper.text()).toContain('生命不足')
    expect(wrapper.text()).toContain('目标：挥棒')
    const buttons = wrapper.findAll('button')
    await buttons[0].trigger('click')
    expect(wrapper.emitted('choose')).toBeUndefined()
    await buttons[2].trigger('click')
    expect(wrapper.emitted('choose')).toEqual([['leave']])
  })
  it('allows only the host to submit a shared choice', async () => {
    const wrapper = mount(BranchEventChoices, { props: { event, canChoose: false } })
    expect(wrapper.text()).toContain('等待房主')
    for (const button of wrapper.findAll('button')) expect(button.attributes('disabled')).toBeDefined()
  })
})
