export interface RegistrationOptions { enabled: boolean; publicTenantId: string | null; tenants: Array<{tenantId: string; name: string}>; crossTenantReady: boolean; selectionRequired: boolean }
export interface RegistrationChoice { tenantId: string; name: string; publicDefault: boolean }
export function parseRegistrationOptions(value: unknown): RegistrationOptions
export function resolveRegistrationChoice(options: RegistrationOptions | null, selected: unknown, currentTenant: string): RegistrationChoice
export function assertRegistrationContext(choice: RegistrationChoice, currentTenant: string): void
export function assertExplicitRegistrationTarget(selected: unknown, currentTenant: string): string
export function needsRegistrationTenantChoice(options: RegistrationOptions | null, hasExplicitTarget: boolean,
  currentTenant: string, entry: { available?: boolean; registrationAvailable?: boolean } | null): boolean
export function registrationChoiceNotice(choice: RegistrationChoice): string
