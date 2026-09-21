export interface CheckoutIntent { intentId: string; state(): 'DRAFT'|'PENDING'|'CREATED'; begin(): { reject(): boolean; created(): boolean } }
export function checkoutIntentId(): string
export function validCheckoutIntent(value: unknown): value is string
export function legacyCheckoutIntent(payload: unknown): string
export function openCheckoutIntent(storage: Pick<Storage,'getItem'|'setItem'>, intentId: string, owner: { tenantId: unknown; memberId: unknown }): CheckoutIntent
