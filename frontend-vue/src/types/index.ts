// ====== 西游记 Roguelike - TypeScript 类型定义 ======

export type CharacterClass = 'SUN_WUKONG' | 'ZHU_BAJIE' | 'SHA_SENG' | 'BAI_LONGMA' | 'TANG_SANZANG'

export type NodeType = 'BATTLE' | 'BOSS' | 'REST' | 'TREASURE' | 'SHOP' | 'RANDOM' | 'BONFIRE' | 'EMPEROR'

export type CardType = 'ATTACK' | 'SKILL' | 'DEFENSE' | 'POWER' | 'STATUS'

export type IntentType = 'ATTACK' | 'DEFEND' | 'BUFF' | 'DEBUFF' | 'SPECIAL'

export interface Card {
  id: number
  name: string
  type: CardType
  cost: number
  damage: number
  block: number
  drawCards: number
  description: string
  emoji?: string
  upgraded: boolean
  exhaust?: boolean
}

export interface Relic {
  name: string
  description: string
  emoji?: string
}

/** 商店宝物（含ID和价格） */
export interface ShopRelic {
  id: number
  name: string
  description: string
  tier: string
  emoji?: string
  price: number
}

export interface MapNode {
  id: string
  row: number
  col: number
  type: NodeType
  name?: string
  visited: boolean
  accessible: boolean
  connections: string[]
}

export interface Player {
  characterClass: CharacterClass
  displayName: string
  emoji?: string
  hp: number
  maxHp: number
  block: number
  energy: number
  maxEnergy: number
  gold: number
  deckSize: number
  floor: number
  deck: Card[]
  relics: Relic[]
  drawPile?: Card[]
  discardPile?: Card[]
  exhaustPile?: Card[]
  drawPileSize?: number
  discardPileSize?: number
  exhaustPileSize?: number
  buffs?: BuffEntry[]
  hand?: Card[]
}

export interface BuffEntry {
  name: string
  value: number
  permanent: boolean
}

export interface Enemy {
  name: string
  emoji?: string
  hp: number
  maxHp: number
  block: number
  intent: IntentType
  intentValue?: number | string
  isBoss: boolean
  buffs?: BuffEntry[]
}

export interface BattleInfo {
  inBattle: boolean
  playerTurn: boolean
  battleOver: boolean
  victory: boolean
  player: Player
  enemy: Enemy
  rewards?: Rewards | null
}

export interface Rewards {
  goldReward?: number
  cardRewards?: Card[]
  relicReward?: Relic | null
  message?: string
  onKillMsg?: string
}

export interface GameState {
  sessionId: string
  player: Player
  map: MapNode[]
  currentNode: MapNode | null
  inBattle: boolean
  currentLayer: number
  maxLayer: number
}

export interface NewGameResponse {
  sessionId: string
  player: Player
  map: MapNode[]
  currentNode: MapNode
}

export interface MoveResponse {
  node: MapNode
  eventType: string
}

export interface EventResponse {
  relic?: Relic
  choices?: Relic[]
  shopCards?: Card[]
  shopRelics?: ShopRelic[]
  bought?: boolean
  player?: Player
  bonfireUpgradesLeft?: number
  error?: string
  message?: string
  success?: boolean
  currentLayer?: number
  chosenCard?: string
}

export interface CardRewardChooseResponse {
  player?: Player
  chosenCard?: string
}

// ====== 多人游戏类型 ======

export type RoomStatus = 'WAITING' | 'IN_MAP' | 'IN_BATTLE' | 'FINISHED'

export interface RoomPlayer {
  userId: string
  username: string
  characterClass: CharacterClass | null
  ready: boolean
  host: boolean
  // 地图探索期间持久化的玩家状态
  hp?: number
  maxHp?: number
  gold?: number
  deck?: Card[]
  relics?: Relic[]
}

export interface RoomDTO {
  code: string
  hostUserId: string
  players: RoomPlayer[]
  playerCount: number
  status: RoomStatus
  createdAt: string
  floor: number
  // 地图探索相关字段
  maxLayer?: number
  map?: MapNode[]
  currentNode?: MapNode | null
  bonfireUpgradesLeft?: number
}

/** 多人战斗中的玩家信息 */
export interface MultiplayerPlayerInfo {
  index: number
  userId: string
  username: string
  characterClass: CharacterClass | null
  hp: number
  maxHp: number
  block: number
  energy: number
  maxEnergy: number
  strength: number
  dexterity: number
  endedTurn: boolean
  alive: boolean
  deckSize: number
  drawPileSize: number
  discardPileSize: number
  buffs: Record<string, number>
  hand: MultiplayerCardInfo[]
}

export interface MultiplayerCardInfo {
  index: number
  name: string
  type: CardType | null
  cost: number
  damage: number
  block: number
  emoji?: string
  exhaust: boolean
  description: string
}

export interface MultiplayerEnemyInfo {
  name: string
  hp: number
  maxHp: number
  block: number
  strength: number
  emoji?: string
  intent: string
  intentValue: number
  isBoss: boolean
  buffs: Record<string, number>
  targetPlayerIndex: number
}

export interface MultiplayerBattleInfo {
  roomCode: string
  turnNumber: number
  playerTurn: boolean
  battleOver: boolean
  victory: boolean
  enemy: MultiplayerEnemyInfo
  players: MultiplayerPlayerInfo[]
  alivePlayerCount: number
  playersEndedTurn: number
  combatLog: string[]
  rewardsPhase?: boolean
  rewardsHandled?: boolean
  rewards?: Record<string, MultiplayerCardInfo[]>
  claimedRewards?: Record<string, string>
}
