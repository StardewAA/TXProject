import { baseUrl, apiPath } from '@/sheep/config';
import { createPublicMarketClient } from '@/sheep/helper/public-market.mjs';
// H5 isolated browser: no authenticated request middleware or stored identity.
export const publicMarketClient = createPublicMarketClient((baseUrl || '') + (apiPath || '/app-api'));
