export interface ImageBinding { tenantId: string; loginName: string; captchaSession: string }
export interface ImageChallenge { tenantId: string; challengeId: string; imageDataUrl: string; expiresIn: number; retryAfter: number }
export interface ImageTicket extends ImageBinding { purpose: 'REGISTER'; previousChallengeId?: string; binding: string; generation: number; serial: number; startedAt: number }
export interface ImageRegistrationPayload extends ImageBinding { imageChallengeId: string; imageCode: string }
export function normalizeLoginName(value: unknown): string
export function createCaptchaSession(random?: Pick<Crypto, 'getRandomValues'>): string
export function imageAuthPayload(operation: string, input: Record<string, unknown>): Readonly<Record<string, unknown>>
export function validateImageChallenge(value: unknown, expectedTenant: string): Readonly<ImageChallenge>
export function createImageRegistrationState(now?: () => number): {
  invalidate(): void
  begin(value: ImageBinding): Readonly<ImageTicket>
  current(ticket: ImageTicket): boolean
  accept(ticket: ImageTicket, value: unknown): boolean
  finish(ticket: ImageTicket): void
  remaining(): number
  expiresIn(): number
  payload(value: ImageBinding, code: string): ImageRegistrationPayload
}
