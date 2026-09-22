import { describe, expect, it } from 'vitest';

import {
  canOpenPortalTenant,
  normalizePortalTenantSlug,
  PORTAL_TENANT_SLUG_PATTERN,
} from './model';

describe('portal tenant model', () => {
  it('normalizes a slug before it is submitted', () => {
    expect(normalizePortalTenantSlug('  KeDun-Mall  ')).toBe('kedun-mall');
  });

  it.each([
    ['kedun', true],
    ['kedun-mall-2009', true],
    ['A-mall', false],
    ['-kedun', false],
    ['kedun-', false],
    ['kedun_mall', false],
  ])('validates slug %s', (slug, expected) => {
    expect(PORTAL_TENANT_SLUG_PATTERN.test(slug)).toBe(expected);
  });

  it('never allows the legacy tenant 3000 to be opened', () => {
    expect(canOpenPortalTenant(3000)).toBe(false);
    expect(canOpenPortalTenant(1)).toBe(true);
  });
});
