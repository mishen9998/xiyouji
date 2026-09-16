import { computed, onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

/** Stops work immediately on document visibility and intersection changes (including v-show). */
export function useMotionVisibility(target: Ref<HTMLElement | null>) {
  const inViewport = ref(true)
  const pageVisible = ref(typeof document === 'undefined' || !document.hidden)
  const reducedMotion = ref(false)
  let observer: IntersectionObserver | undefined
  let preference: MediaQueryList | undefined
  const refreshDocument = () => { pageVisible.value = !document.hidden }
  const refreshPreference = () => { reducedMotion.value = preference?.matches ?? false }
  onMounted(() => {
    refreshDocument()
    document.addEventListener('visibilitychange', refreshDocument)
    preference = window.matchMedia?.('(prefers-reduced-motion: reduce)')
    refreshPreference()
    preference?.addEventListener('change', refreshPreference)
    if (target.value && typeof IntersectionObserver !== 'undefined') {
      observer = new IntersectionObserver(entries => {
        inViewport.value = entries.some(entry => entry.isIntersecting)
      }, { threshold: 0 })
      observer.observe(target.value)
    }
  })
  onBeforeUnmount(() => {
    observer?.disconnect()
    document.removeEventListener('visibilitychange', refreshDocument)
    preference?.removeEventListener('change', refreshPreference)
  })
  const visible = computed(() => inViewport.value && pageVisible.value)
  const motionAllowed = computed(() => visible.value && !reducedMotion.value)
  return { visible, reducedMotion, motionAllowed }
}

/** Capability gate only. Users still opt into 3D in the battle UI. */
export function shouldUseLightweightIllustration(nav: Navigator = navigator): boolean {
  const device = nav as Navigator & { deviceMemory?: number; connection?: { saveData?: boolean } }
  return device.connection?.saveData === true
    || (device.deviceMemory !== undefined && device.deviceMemory <= 4)
    || (device.hardwareConcurrency > 0 && device.hardwareConcurrency <= 4)
}

/** Single-owner RAF loop: repeated resume cannot schedule duplicates; pause cancels synchronously. */
export function createVisibleFrameLoop(render: (timestamp: number) => void, fps = 30) {
  let frame: number | null = null
  let running = false
  let last = -Infinity
  function tick(now: number) {
    frame = null
    if (!running) return
    if (now - last >= 1000 / fps - 1) { last = now; render(now) }
    if (running) frame = requestAnimationFrame(tick)
  }
  return {
    start() {
      if (running) return
      running = true
      last = -Infinity
      frame = requestAnimationFrame(tick)
    },
    stop() {
      running = false
      if (frame !== null) cancelAnimationFrame(frame)
      frame = null
    },
  }
}
