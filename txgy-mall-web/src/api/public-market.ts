import { createPublicMarketClient } from '@/utils/public-market.mjs'
// Deliberately separate from the authenticated client and its refresh interceptors.
export const publicMarketClient = createPublicMarketClient(`${import.meta.env.VITE_API_BASE_URL || ''}${import.meta.env.VITE_API_PREFIX || '/app-api'}`)
