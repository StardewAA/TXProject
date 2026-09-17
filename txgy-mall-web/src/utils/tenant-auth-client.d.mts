import type { AuthEntry } from './auth-challenge.mjs'
export interface TenantAuthRequest {
  path: string; method: 'GET' | 'POST'; headers: Record<string, string>
  data?: Readonly<Record<string, unknown>>; params?: Record<string, string>
}
export interface VerifiedIdentity { tenantId: string; userId: string; accessToken: string; refreshToken: string; [key: string]: unknown }
export class SessionRestoreError extends Error { kind: 'invalid' | 'unavailable' | 'stale' }
export function validateTenantIdentity(value: unknown, tenantId: unknown, userId?: unknown): Readonly<VerifiedIdentity>
export function tenantAuthPayload(operation: string, input: Record<string, unknown>): Readonly<Record<string, unknown>>
export function createTenantAuthClient(transport: (request: TenantAuthRequest) => Promise<unknown>, legacyTenantId?: string): {
  discover(target?: string, current?: () => boolean): Promise<Readonly<AuthEntry>>
  execute(operation: string, input: Record<string, unknown>, current?: () => boolean): Promise<unknown>
  verifySession(identity: unknown, tenant: unknown, current?: () => boolean, userId?: unknown): Promise<Readonly<VerifiedIdentity>>
  refresh(identity: unknown, current?: () => boolean): Promise<Readonly<VerifiedIdentity>>
  restoreSession(stored: { tenantId: string; accessToken: string; refreshToken: string }, current?: () => boolean): Promise<Readonly<VerifiedIdentity>>
}
