export function isPlatformPath(path: unknown): boolean
export function scopePlatformPath(path: unknown): string
export function scopeLocalPath(path: unknown, tenant: string): string
export function safeReturnPath(path: unknown, tenant: string, fallback?: string): string
export function tenantShareLink(address: string, spm: string, tenant: string, fallbackOrigin: string): string
