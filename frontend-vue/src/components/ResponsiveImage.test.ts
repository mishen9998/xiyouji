import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ResponsiveImage from './ResponsiveImage.vue'
import { fullImgUrl, imageSources, imageUrl } from '@/constants/images'

describe('responsive illustration', () => {
  it('uses AVIF/WebP sources with intrinsic ratio and explicit lazy decoding', () => {
    const wrapper = mount(ResponsiveImage, { props: { src: fullImgUrl('SUN_WUKONG'), alt: '孙悟空', sizes: '180px' } })
    expect(wrapper.findAll('source').map(s => s.attributes('type'))).toEqual(['image/avif', 'image/webp'])
    expect(wrapper.find('source').attributes('sizes')).toBe('180px')
    expect(wrapper.find('img').attributes()).toMatchObject({ alt: '孙悟空', loading: 'lazy', decoding: 'async' })
    expect(wrapper.find('img').attributes('width')).toBeTruthy()
    expect(wrapper.attributes('style')).toContain('aspect-ratio:')
  })
  it('loads critical art eagerly, keeps space on failure, retries a new source', async () => {
    const wrapper = mount(ResponsiveImage, { props: { src: fullImgUrl('SUN_WUKONG'), alt: '孙悟空', emoji: '🐵', critical: true } })
    const ratio = wrapper.attributes('style')
    expect(wrapper.find('img').attributes()).toMatchObject({ loading: 'eager', fetchpriority: 'high' })
    await wrapper.find('img').trigger('error')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('[role=img]').attributes('aria-label')).toBe('孙悟空')
    expect(wrapper.text()).toBe('🐵')
    expect(wrapper.attributes('style')).toBe(ratio)
    await wrapper.setProps({ src: fullImgUrl('ZHU_BAJIE') })
    expect(wrapper.find('img').exists()).toBe(true)
  })
  it('handles unknown/missing content without broken-image layout', () => {
    expect(imageUrl(null)).toBeNull()
    expect(imageSources('/future-art.webp').src).toBe('/future-art.webp')
    const wrapper = mount(ResponsiveImage, { props: { alt: '未知角色', emoji: '✦' } })
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('[role=img]').exists()).toBe(true)
  })
})
