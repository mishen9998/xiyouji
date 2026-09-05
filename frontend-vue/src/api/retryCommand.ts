/** Keep a logical command key when a response is lost, so retry can replay it safely. */
export function createCommandRetry() {
  const pending = new Map<string, string>()
  return async function retry<T>(identity: string, send: (key: string) => Promise<T>): Promise<T> {
    let key = pending.get(identity)
    if (!key) {
      key = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`
      pending.set(identity, key)
    }
    try {
      const result = await send(key)
      pending.delete(identity)
      return result
    } catch (error: any) {
      // Validation/version failures did not commit. Network/server errors may have committed.
      if (error?.status >= 400 && error.status < 500 && error?.code !== 'IDEMPOTENCY_IN_PROGRESS') pending.delete(identity)
      throw error
    }
  }
}
