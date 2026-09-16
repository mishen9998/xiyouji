import { mount } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import { expect, it, vi } from 'vitest'
import { useBattleKeyboard } from './useKeyboard'
import type { BattleInfo } from '@/types'

it('keyboard selection/end-turn ignores auto-repeat and editable fields and removes listeners', () => {
  const select = vi.fn(), end = vi.fn()
  const wrapper = mount(defineComponent({ template: '<input />', setup() {
    useBattleKeyboard(ref({ playerTurn: true, battleOver: false, player: { hand: [{}] } } as BattleInfo), select, end)
  } }), { attachTo: document.body })
  document.dispatchEvent(new KeyboardEvent('keydown', { key: '1' }))
  document.dispatchEvent(new KeyboardEvent('keydown', { key: 'e' }))
  document.dispatchEvent(new KeyboardEvent('keydown', { key: 'e', repeat: true }))
  wrapper.get('input').element.dispatchEvent(new KeyboardEvent('keydown', { key: 'e', bubbles: true }))
  expect(select).toHaveBeenCalledWith(0); expect(end).toHaveBeenCalledOnce()
  wrapper.unmount()
  document.dispatchEvent(new KeyboardEvent('keydown', { key: 'e' }))
  expect(end).toHaveBeenCalledOnce()
})
