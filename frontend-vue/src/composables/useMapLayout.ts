import { computed, nextTick, onMounted, onUnmounted, ref, watch, type Ref } from 'vue'
import type { MapNode } from '@/types'

/** One layout contract for both maps; observe the actual container, not the window. */
export function useMapLayout(nodes: Ref<MapNode[]>, current: Ref<MapNode | null | undefined>, wrapper: Ref<HTMLElement | null>) {
  const width = ref(336)
  const MAP_WIDTH = computed(() => Math.max(240, Math.min(1200, width.value)))
  const rowHeight = computed(() => MAP_WIDTH.value < 600 ? 150 : 190)
  const maxRow = computed(() => Math.max(0, ...nodes.value.map(node => node.row)))
  const mapHeight = computed(() => (maxRow.value + 1) * rowHeight.value + 120)
  const nodePositions = computed(() => Object.fromEntries(nodes.value.map(node => [node.id, {
    x: node.type === 'BOSS' ? MAP_WIDTH.value / 2 : (node.col + .5) * MAP_WIDTH.value / 4,
    y: mapHeight.value - 80 - node.row * rowHeight.value,
  }])))
  const connectionLines = computed(() => nodes.value.flatMap(node => (node.connections || []).flatMap(id => {
    const from = nodePositions.value[node.id], to = nodePositions.value[id]
    return from && to ? [{ x1: from.x, y1: from.y, x2: to.x, y2: to.y }] : []
  })))
  const wrapperStyle = computed(() => ({ position: 'relative' as const, width: '100%', minHeight: `${mapHeight.value}px`, height: `${mapHeight.value}px` }))
  async function scrollToCurrentNode() {
    await nextTick()
    const el = wrapper.value
    if (!el) return
    const position = current.value && nodePositions.value[current.value.id]
    el.scrollTop = position ? Math.max(0, Math.min(position.y - el.clientHeight / 2, el.scrollHeight - el.clientHeight)) : el.scrollHeight
  }
  let observer: ResizeObserver | undefined
  onMounted(() => {
    const el = wrapper.value
    if (!el) return
    const resize = () => {
      const css = getComputedStyle(el)
      width.value = el.clientWidth - parseFloat(css.paddingLeft || '0') - parseFloat(css.paddingRight || '0')
      void scrollToCurrentNode()
    }
    resize()
    observer = new ResizeObserver(resize)
    observer.observe(el)
  })
  onUnmounted(() => observer?.disconnect())
  watch([nodes, current], scrollToCurrentNode)
  return { MAP_WIDTH, maxRow, mapHeight, nodePositions, connectionLines, wrapperStyle, scrollToCurrentNode }
}
