export const ENTRY_KEY: string
export interface EntryDirectory { enabled: boolean; tenants: ReadonlyArray<{ tenantId: string; name: string }> }
export function entryId(value: unknown): string
export function entryHint(storage: Pick<Storage, 'getItem' | 'setItem'> | undefined, legacy: string, href?: string): { tenantId: string; invalid: boolean }
export function parseEntryDirectory(value: unknown, legacy: string): EntryDirectory
export function requireEntry(value: unknown, target: string, legacy: string): string
export function scopedTokenKey(key: string, target: string, legacy: string): string
export function entryDestination(kind: 'pc' | 'h5', purpose: 'home' | 'register', tenant: string): string
export function selectEntry(storage: Pick<Storage, 'setItem'>, directory: unknown, target: string, legacy: string, kind: 'pc' | 'h5', purpose?: 'home' | 'register'): string
