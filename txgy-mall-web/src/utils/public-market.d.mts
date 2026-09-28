import type { Product, Identifier } from '@/types/mall'
export interface PublicMarket { tenantId: string; name: string }
export interface PublicDirectory { enabled: boolean; tenants: PublicMarket[]; publicTenantId: string | null }
export interface PublicMarketState {
  directory: PublicMarket[]; tenantId: string; name: string; shops: Array<{ id: Identifier; name: string }>;
  categories: Array<{ id: Identifier; name: string }>; products: Product[]; total: number;
  pageNo: number; keyword: string; shopId: string; categoryId: string; loading: boolean; error: string;
  detail: Product | null; detailLoading: boolean; detailError: string; enabled: boolean;
}
export interface PublicMarketClient { directory(): Promise<PublicDirectory>; read(tenant: string, kind: string, params?: Record<string, unknown>): Promise<unknown> }
export function parsePublicMarkets(value: unknown): PublicDirectory
export function publicMarketRequest(tenant: unknown, kind: string, input?: Record<string, unknown>): {path: string; params: Record<string, unknown>; headers: Record<string, string>}
export function createPublicMarketClient(base: string, fetcher?: typeof fetch): PublicMarketClient
export function publicMarketState(): PublicMarketState
export function createPublicMarketBrowser(state: PublicMarketState, client: PublicMarketClient): {open(tenant?: string): Promise<void>; search(pageNo?: number): Promise<void>; detail(id: Identifier): Promise<void>; closeDetail(): void; dispose(): void}
export function publicPrice(value: unknown): string
