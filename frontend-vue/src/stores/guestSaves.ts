import type { CharacterClass } from '@/types'
import { authApi } from '@/api/game'

export const GUEST_SAVE_LIMIT = 3

export interface GuestSaveSlot {
  sessionId: string
  characterClass: CharacterClass
  createdAt: string
}

function storageKey(): string | null {
  const profile = authApi.getProfile()
  if (!profile || profile.role !== 'GUEST') return null
  return `xiyouji_guest_saves:${profile.username}`
}

export function getGuestSaveSlots(): GuestSaveSlot[] {
  const key = storageKey()
  if (!key) return []
  try {
    const parsed = JSON.parse(localStorage.getItem(key) || '[]')
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter((slot): slot is GuestSaveSlot => Boolean(
        slot && typeof slot.sessionId === 'string' && typeof slot.characterClass === 'string',
      ))
      .slice(0, GUEST_SAVE_LIMIT)
  } catch { return [] }
}

function writeGuestSaveSlots(slots: GuestSaveSlot[]) {
  const key = storageKey()
  if (!key) return
  try { localStorage.setItem(key, JSON.stringify(slots.slice(0, GUEST_SAVE_LIMIT))) } catch {}
}

export function addGuestSaveSlot(slot: GuestSaveSlot, replacedSessionId?: string) {
  let slots = getGuestSaveSlots()
  if (replacedSessionId) {
    slots = slots.filter(item => item.sessionId !== replacedSessionId)
  }
  slots = [slot, ...slots.filter(item => item.sessionId !== slot.sessionId)]
  writeGuestSaveSlots(slots)
}

export function removeGuestSaveSlot(sessionId: string) {
  writeGuestSaveSlots(getGuestSaveSlots().filter(slot => slot.sessionId !== sessionId))
}

export function guestSlotsAreFull(): boolean {
  return getGuestSaveSlots().length >= GUEST_SAVE_LIMIT
}
