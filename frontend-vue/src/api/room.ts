// ====== 多人游戏 API 封装 ======
import { postJson, getJson, authHeaders } from './game'
import type { RoomDTO, CharacterClass, MultiplayerBattleInfo } from '@/types'

const ROOM_API = '/api/room'
const BATTLE_API = '/api/multiplayer/battle'

// ====== 房间 API ======
export const roomApi = {
  createRoom(): Promise<RoomDTO> {
    return postJson(`${ROOM_API}/create`)
  },

  joinRoom(code: string): Promise<RoomDTO> {
    return postJson(`${ROOM_API}/join`, { code })
  },

  leaveRoom(code: string): Promise<{ dissolved: boolean; room?: RoomDTO }> {
    return postJson(`${ROOM_API}/${code}/leave`)
  },

  toggleReady(code: string): Promise<RoomDTO> {
    return postJson(`${ROOM_API}/${code}/ready`)
  },

  selectCharacter(code: string, characterClass: CharacterClass): Promise<RoomDTO> {
    return postJson(`${ROOM_API}/${code}/character`, { characterClass })
  },

  getRoom(code: string): Promise<RoomDTO> {
    return getJson(`${ROOM_API}/${code}`)
  },

  listCharacters(): Promise<CharacterClass[]> {
    return getJson(`${ROOM_API}/characters`)
  },

  canStart(code: string): Promise<boolean> {
    return getJson(`${ROOM_API}/${code}/canStart`)
  },

  // ===== 地图探索 API =====

  startGame(code: string): Promise<RoomDTO> {
    return postJson(`${ROOM_API}/${code}/start-game`)
  },

  moveToNode(code: string, nodeId: string): Promise<{ node: any; eventType: string; room: RoomDTO }> {
    return postJson(`${ROOM_API}/${code}/move`, { nodeId })
  },

  handleEvent(code: string, action: string, params?: { cardId?: number; cardIndex?: number }): Promise<any> {
    return postJson(`${ROOM_API}/${code}/event`, { action, ...params })
  },

  nextLayer(code: string): Promise<any> {
    return postJson(`${ROOM_API}/${code}/next-layer`)
  },
}

// ====== 多人战斗 API ======
export const multiplayerBattleApi = {
  startBattle(roomCode: string): Promise<MultiplayerBattleInfo> {
    return postJson(`${BATTLE_API}/${roomCode}/start`)
  },

  playCard(roomCode: string, handIndex: number): Promise<MultiplayerBattleInfo> {
    return postJson(`${BATTLE_API}/${roomCode}/play`, { handIndex })
  },

  endTurn(roomCode: string): Promise<MultiplayerBattleInfo> {
    return postJson(`${BATTLE_API}/${roomCode}/endturn`)
  },

  getBattleState(roomCode: string): Promise<MultiplayerBattleInfo> {
    return getJson(`${BATTLE_API}/${roomCode}/state`)
  },

  battleExists(roomCode: string): Promise<boolean> {
    return getJson(`${BATTLE_API}/${roomCode}/exists`)
  },

  claimReward(roomCode: string, cardName: string): Promise<MultiplayerBattleInfo> {
    return postJson(`${BATTLE_API}/${roomCode}/claim-reward`, { cardName })
  },

  nextFloor(roomCode: string): Promise<MultiplayerBattleInfo> {
    return postJson(`${BATTLE_API}/${roomCode}/next-floor`)
  },
}

/** 获取当前 JWT token（供 WebSocket 握手使用） */
export async function getTokenForWs(): Promise<string> {
  const headers = await authHeaders() as Record<string, string>
  return headers['Authorization'].replace('Bearer ', '')
}

/** 从 JWT 中解析当前用户名（无需后端请求） */
export function getCurrentUsername(): string | null {
  try {
    const token = localStorage.getItem('xiyouji_jwt_token')
    if (!token) return null
    const payload = token.split('.')[1]
    const decoded = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')))
    return decoded.sub || decoded.username || null
  } catch {
    return null
  }
}
