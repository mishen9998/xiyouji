import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import CharacterSelectView from './CharacterSelectView.vue'
import HomeView from './HomeView.vue'
import AuthView from './AuthView.vue'
import ResponsiveImage from '@/components/ResponsiveImage.vue'
import { characterAvatarUrl, coverImageUrl } from '@/constants/images'
import { useGameStore } from '@/stores/game'
import { useUiStore } from '@/stores/ui'
import { ApiError, authApi } from '@/api/game'

const { push, route } = vi.hoisted(() => ({ push: vi.fn().mockResolvedValue(undefined), route: { query: {} } }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push }), useRoute: () => route }))
beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); push.mockClear() })
afterEach(() => vi.restoreAllMocks())

describe('restored cinematic entrance', () => {
  it('keeps five original portraits and changes the full-screen hero without creating a game', async () => {
    const start = vi.spyOn(useGameStore(), 'startNewGame').mockResolvedValue()
    const wrapper = mount(CharacterSelectView)
    expect(wrapper.findAll('.char-card')).toHaveLength(5)
    expect(wrapper.find('.character-backdrop').exists()).toBe(false)
    await wrapper.findAll('.char-card')[1].trigger('click')
    expect(wrapper.classes()).toContain('has-selection')
    expect(wrapper.findAll('.char-card')[1].attributes('aria-pressed')).toBe('true')
    expect(wrapper.findAllComponents(ResponsiveImage).find(image => image.classes().includes('character-backdrop'))?.props('src'))
      .toBe(characterAvatarUrl('ZHU_BAJIE', 960))
    expect(start).not.toHaveBeenCalled()
    await wrapper.get('.btn-start').trigger('click')
    await flushPromises()
    expect(start).toHaveBeenCalledExactlyOnceWith('ZHU_BAJIE')
    expect(push).toHaveBeenCalledWith('/map')
    wrapper.unmount()
  })

  it('does not hide the actionable authentication-expired message behind a generic failure', async () => {
    vi.spyOn(useGameStore(), 'startNewGame').mockRejectedValue(new ApiError(401, { message: '登录已失效' }))
    const toast = vi.spyOn(useUiStore(), 'showToast')
    const wrapper = mount(CharacterSelectView)
    await wrapper.get('.btn-start').trigger('click')
    await flushPromises()
    expect(toast).not.toHaveBeenCalled()
    expect(wrapper.get('.btn-start').attributes('disabled')).toBeUndefined()
    expect(push).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('uses the original cover on both login and menu screens', () => {
    for (const View of [HomeView, AuthView]) {
      const wrapper = mount(View)
      expect(wrapper.findComponent(ResponsiveImage).props('src')).toBe(coverImageUrl())
      wrapper.unmount()
    }
  })

  it('validates a continued identity before entering the menu', async () => {
    localStorage.setItem('xiyouji_jwt_token', 'old-token')
    localStorage.setItem('xiyouji_auth_profile', JSON.stringify({ username: '旅人', role: 'GUEST' }))
    const validate = vi.spyOn(authApi, 'validateSession').mockRejectedValue(new ApiError(401, { message: '登录已失效' }))
    const wrapper = mount(AuthView)
    await wrapper.get('.continue-action').trigger('click')
    await flushPromises()
    expect(validate).toHaveBeenCalledTimes(1)
    expect(push).not.toHaveBeenCalled()
    expect(wrapper.get('.form-error').text()).toContain('登录已失效')
    wrapper.unmount()
  })
})
