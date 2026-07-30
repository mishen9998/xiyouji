// ====== 键盘快捷键 composable ======
import { onMounted, onUnmounted, type Ref } from 'vue'
import type { BattleInfo } from '@/types'

export function useBattleKeyboard(
  battleInfo: Ref<BattleInfo | null>,
  onPlayCard: (index: number) => void,
  onEndTurn: () => void
) {
  function handler(e: KeyboardEvent) {
    const bi = battleInfo.value
    if (!bi || !bi.playerTurn || bi.battleOver) return
    const hand = bi.player?.hand
    if (!hand) return

    const key = parseInt(e.key)
    if (key >= 1 && key <= hand.length) {
      onPlayCard(key - 1)
    }
    if (e.key === 'e' || e.key === 'E') {
      onEndTurn()
    }
  }

  onMounted(() => document.addEventListener('keydown', handler))
  onUnmounted(() => document.removeEventListener('keydown', handler))
}
