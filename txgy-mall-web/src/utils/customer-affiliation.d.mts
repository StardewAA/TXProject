export interface CustomerAffiliation { state: 'DISABLED' | 'NONE' | 'ACTIVE' | 'OWNER_UNAVAILABLE' | 'TEMPORARILY_UNAVAILABLE'; serviceEnterpriseName: string | null }
export interface CustomerAffiliationState { result: CustomerAffiliation | null; loading: boolean; error: boolean; scope: string }
export function normalizeCustomerAffiliation(value: unknown): CustomerAffiliation
export function createCustomerAffiliationLoader(state: CustomerAffiliationState, getScope: () => string, fetchProfile: () => Promise<unknown>): { reset(): void; refresh(): Promise<boolean> }
export function customerAffiliationMessage(state: CustomerAffiliationState): string
