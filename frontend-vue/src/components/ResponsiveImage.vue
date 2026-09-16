<template>
  <picture class="responsive-image" :class="{ 'responsive-image--failed': failed || !sources.src }" :style="frameStyle">
    <source v-if="!failed && sources.avifSrcset" type="image/avif" :srcset="sources.avifSrcset" :sizes="sizes" />
    <source v-if="!failed && sources.webpSrcset" type="image/webp" :srcset="sources.webpSrcset" :sizes="sizes" />
    <img v-if="!failed && sources.src" :src="sources.src" :alt="alt" :width="sources.width" :height="sources.height"
      :loading="critical ? 'eager' : 'lazy'" :fetchpriority="critical ? 'high' : 'auto'" decoding="async"
      :style="{ objectFit }" draggable="false" @error="failed = true" @load="$emit('load')" />
    <span v-else class="responsive-image__fallback" role="img" :aria-label="alt || '插画暂不可用'">{{ emoji }}</span>
  </picture>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { imageSources } from '@/constants/images'
const props = withDefaults(defineProps<{
  src?: string | null
  alt?: string
  emoji?: string
  sizes?: string
  critical?: boolean
  objectFit?: 'contain' | 'cover'
}>(), { src: null, alt: '', emoji: '✦', sizes: '100vw', critical: false, objectFit: 'contain' })
defineEmits<{ load: [] }>()
const failed = ref(false)
const sources = computed(() => imageSources(props.src))
const frameStyle = computed(() => ({ aspectRatio: sources.value.width && sources.value.height ? `${sources.value.width} / ${sources.value.height}` : '1' }))
watch(() => props.src, () => { failed.value = false })
</script>
<style scoped>
.responsive-image { display: block; width: 100%; max-width: 100%; overflow: hidden; background: var(--paper, #f5efdf); }
.responsive-image img { display: block; width: 100%; height: 100%; object-position: center; }
.responsive-image__fallback { display: grid; width: 100%; height: 100%; min-height: 44px; place-items: center; font-size: clamp(28px, 6vw, 64px); color: var(--ink, #243f35); }
</style>
