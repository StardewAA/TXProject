import { describe, expect, it } from 'vitest';

import { resolveMallPcUrl } from './mall-pc-entry';

describe('resolveMallPcUrl', () => {
  it('accepts the configured PC storefront URL', () => {
    expect(resolveMallPcUrl('https://shop.example.com')).toBe(
      'https://shop.example.com/',
    );
  });

  it('keeps the current HTTP/port-based deployment address unchanged', () => {
    expect(resolveMallPcUrl('http://localhost:15175/?tenantId=1')).toBe(
      'http://localhost:15175/?tenantId=1',
    );
  });

  it('rejects unsupported schemes, credentials, and credential query parameters', () => {
    for (const value of [
      'ftp://shop.example.com',
      'https://shop.example.com/?accessToken=secret',
      'https://shop.example.com/?tenantId=1&access_token=secret',
      'https://user:password@shop.example.com',
    ]) {
      expect(resolveMallPcUrl(value)).toBeNull();
    }
  });

  it('returns null for a missing or malformed configuration', () => {
    expect(resolveMallPcUrl('')).toBeNull();
    expect(resolveMallPcUrl('not a URL')).toBeNull();
  });
});
