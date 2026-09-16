/** Additive combat-v2 forecast. The server owns every target and numeric result. */
export interface DamageForecast { damagePerHit: number; hits: number; blockAbsorbed: number; hpLoss: number }
export interface IntentEffects {
  actionType?: string; block?: number; strength?: number
  statuses?: Record<string, number>
  targetDamage?: Record<string, DamageForecast>
}
export interface EnemyIntentState {
  intent?: string; intentValue?: number | string; intentTargetUserIds?: string[]
  intentHits?: number; intentEffects?: IntentEffects; targetPlayerIndex?: number
}
export interface IntentPlayer { userId: string; username?: string; index?: number; alive?: boolean }
const actions: Record<string, { label: string; icon: string }> = {
  ATTACK: { label: '单体攻击', icon: '⚔' }, ATTACK_ALL: { label: '全体攻击', icon: '⚔' },
  MULTI_HIT: { label: '连击', icon: '⚔' }, DEFEND: { label: '防御', icon: '盾' },
  ATTACK_DEFEND: { label: '攻防兼备', icon: '⚔' }, GAIN_STRENGTH: { label: '力量强化', icon: '↑' },
  APPLY_STATUS: { label: '施加状态', icon: '✦' }, BUFF: { label: '强化', icon: '↑' },
  DEBUFF: { label: '削弱', icon: '✦' }, SPECIAL: { label: '特殊行动', icon: '✦' },
}
export const statusNames: Record<string, string> = {
  WEAK: '虚弱', VULNERABLE: '脆弱', POISON: '中毒', STRENGTH: '力量', DEXTERITY: '敏捷',
  REGENERATION: '再生', BURN: '灼烧', FROZEN: '冰冻', RAGE: '怒气', FORTIFY: '坚守', GLUTTONY: '贪食', SWIFT: '迅捷',
}
export function intentTargets(enemy: EnemyIntentState, players: IntentPlayer[] = []): string[] {
  if (enemy.intentTargetUserIds) return enemy.intentTargetUserIds
  const legacy = players.find(player => player.index === enemy.targetPlayerIndex)
  return legacy && ['ATTACK', 'DEBUFF'].includes(enemy.intent ?? '') ? [legacy.userId] : []
}
export function describeIntent(enemy: EnemyIntentState, players: IntentPlayer[] = []) {
  const effects = enemy.intentEffects
  const type = effects?.actionType ?? enemy.intent ?? 'SPECIAL'
  const action = actions[type] ?? { label: '未知行动', icon: '?' }
  const attacking = ['ATTACK', 'ATTACK_ALL', 'MULTI_HIT', 'ATTACK_DEFEND'].includes(type)
  const hits = Math.max(1, enemy.intentHits ?? 1)
  const targets = intentTargets(enemy, players)
  const playerName = (id: string) => players.find(player => player.userId === id)?.username ?? (players.length ? id : '你')
  const details: string[] = []
  if (attacking) details.push(`${Number(enemy.intentValue) || 0} 伤害${hits > 1 ? ` × ${hits} 次` : ''}`)
  const block = effects?.block ?? (type === 'DEFEND' ? Number(enemy.intentValue) || 0 : 0)
  if (block > 0) details.push(`获得 ${block} 格挡`)
  const strength = effects?.strength ?? (type === 'BUFF' ? Number(enemy.intentValue) || 0 : 0)
  if (strength > 0) details.push(`力量 +${strength}（本场累计最多 +6）`)
  for (const [status, stacks] of Object.entries(effects?.statuses ?? {})) {
    if (stacks > 0) details.push(`${statusNames[status] ?? status} ${stacks} 层`)
  }
  if (!details.length && enemy.intentValue) details.push(`效果 ${enemy.intentValue}`)
  const forecast = Object.entries(effects?.targetDamage ?? {}).map(([id, value]) => ({ id, name: playerName(id), ...value }))
  const targetLabel = type === 'DEFEND' || type === 'GAIN_STRENGTH' || type === 'BUFF'
    ? '敌人自身' : targets.length ? targets.map(playerName).join('、') : (attacking && !players.length ? '你' : '以服务端预告为准')
  return { ...action, type, details, targetLabel, forecast, attacking }
}
