import copy from './artwork-copy.json'

export type ArtworkKind = 'card' | 'relic'

/** Narrative only: effect text and upgraded numbers always come from the server. */
export function artworkLore(kind: ArtworkKind, name: string): string {
  const canonicalName = kind === 'relic' && name === '降魔宝杖' ? '降妖宝杖' : name
  const catalog: Record<string, { lore: string }> = kind === 'card' ? copy.cards : copy.relics
  return catalog[canonicalName]?.lore ?? ''
}
