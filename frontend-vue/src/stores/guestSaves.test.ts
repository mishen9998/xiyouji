import { beforeEach, describe, expect, it } from 'vitest'
import { authApi } from '@/api/game'
import {
  addGuestSaveSlot,
  getGuestSaveSlots,
  guestSlotsAreFull,
  removeGuestSaveSlot,
} from './guestSaves'

function activateGuest() {
  localStorage.setItem('xiyouji_jwt_token', 'guest-token')
  localStorage.setItem('xiyouji_auth_profile', JSON.stringify({
    account: 'guest_1234', username: 'guest_1234', role: 'GUEST',
  }))
}

describe('guest save slots', () => {
  beforeEach(() => {
    localStorage.clear()
    activateGuest()
  })

  it('keeps at most three slots for the active guest identity', () => {
    addGuestSaveSlot({ sessionId: 's1', characterClass: 'SUN_WUKONG', createdAt: '2026-09-01' })
    addGuestSaveSlot({ sessionId: 's2', characterClass: 'ZHU_BAJIE', createdAt: '2026-09-02' })
    addGuestSaveSlot({ sessionId: 's3', characterClass: 'SHA_SENG', createdAt: '2026-09-03' })

    expect(guestSlotsAreFull()).toBe(true)
    expect(getGuestSaveSlots().map(slot => slot.sessionId)).toEqual(['s3', 's2', 's1'])
  })

  it('replaces the selected slot and leaves the other two intact', () => {
    addGuestSaveSlot({ sessionId: 's1', characterClass: 'SUN_WUKONG', createdAt: '2026-09-01' })
    addGuestSaveSlot({ sessionId: 's2', characterClass: 'ZHU_BAJIE', createdAt: '2026-09-02' })
    addGuestSaveSlot({ sessionId: 's3', characterClass: 'SHA_SENG', createdAt: '2026-09-03' })
    addGuestSaveSlot(
      { sessionId: 's4', characterClass: 'TANG_SANZANG', createdAt: '2026-09-04' },
      's2',
    )

    expect(getGuestSaveSlots().map(slot => slot.sessionId)).toEqual(['s4', 's3', 's1'])
  })

  it('removes a deleted slot', () => {
    addGuestSaveSlot({ sessionId: 's1', characterClass: 'SUN_WUKONG', createdAt: '2026-09-01' })
    removeGuestSaveSlot('s1')
    expect(getGuestSaveSlots()).toEqual([])
    expect(authApi.getProfile()?.role).toBe('GUEST')
  })
})
