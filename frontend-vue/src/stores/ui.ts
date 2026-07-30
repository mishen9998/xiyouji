// ====== UI 状态 Store (Toast + Confirm Modal) ======
import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface ConfirmOptions {
  title: string
  message: string
  okText?: string
  cancelText?: string
  showDelete?: boolean
  deleteText?: string
  onOk?: () => void
  onCancel?: () => void
  onDelete?: () => void
}

export const useUiStore = defineStore('ui', () => {
  const toastMessage = ref('')
  const toastVisible = ref(false)
  let toastTimer: ReturnType<typeof setTimeout> | null = null

  const confirmVisible = ref(false)
  const confirmOptions = ref<ConfirmOptions>({
    title: '', message: ''
  })

  function showToast(message: string, duration = 2200) {
    toastMessage.value = message
    toastVisible.value = true
    if (toastTimer) clearTimeout(toastTimer)
    toastTimer = setTimeout(() => {
      toastVisible.value = false
    }, duration)
  }

  function showConfirm(options: ConfirmOptions) {
    confirmOptions.value = options
    confirmVisible.value = true
  }

  function hideConfirm() {
    confirmVisible.value = false
  }

  function confirmOk() {
    const cb = confirmOptions.value.onOk
    hideConfirm()
    cb?.()
  }

  function confirmCancel() {
    const cb = confirmOptions.value.onCancel
    hideConfirm()
    cb?.()
  }

  function confirmDelete() {
    const cb = confirmOptions.value.onDelete
    hideConfirm()
    cb?.()
  }

  return {
    toastMessage, toastVisible,
    confirmVisible, confirmOptions,
    showToast, showConfirm, hideConfirm,
    confirmOk, confirmCancel, confirmDelete,
  }
})
