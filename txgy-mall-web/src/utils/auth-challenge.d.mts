export type AuthProtocol = 'legacy' | 'challenge-v1'
export type AuthPurpose = 'REGISTER' | 'SMS_LOGIN' | 'RESET_PASSWORD' | 'PASSWORD_LOGIN'
export interface AuthEntry { protocol: AuthProtocol; tenantId: string; available: boolean; registrationAvailable: boolean; tenantSelection: boolean; registrationMethod?: 'image-v1' | 'sms'; loginNameEnabled?: boolean }
export interface ChallengeBinding { tenantId: string; mobile: string; purpose: AuthPurpose; protocol: AuthProtocol }
export interface ChallengeTicket extends ChallengeBinding { binding: string; generation: number; serial: number; startedAt: number }
export function parseAuthEntry(value: unknown, expectedTenant: string): Readonly<AuthEntry>
export function assertAuthAvailable(entry: AuthEntry | null, purpose: AuthPurpose): void
export function createChallengeState(now?: () => number): {
  change(value: ChallengeBinding): void
  invalidate(): void
  begin(value: ChallengeBinding): ChallengeTicket
  current(ticket: ChallengeTicket): boolean
  accept(ticket: ChallengeTicket, result: unknown): boolean
  finish(ticket: ChallengeTicket): void
  remaining(mobile: string): number
  payload(value: ChallengeBinding, code: string): { tenantId?: string; challengeId?: string }
}
