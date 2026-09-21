import { describe, expect, it } from 'vitest';

import { hasBackendAccess } from './login-access';

describe('hasBackendAccess', () => {
  it('keeps personal users in the portal', () => {
    expect(
      hasBackendAccess({
        loginType: 'personal',
        permissions: ['hrm:recruit:post:query'],
      }),
    ).toBe(false);
  });

  it('keeps enterprise users in the backend even without menu data', () => {
    expect(
      hasBackendAccess({
        loginType: 'enterprise',
        menus: [],
        permissions: [],
      }),
    ).toBe(true);
  });

  it('allows HRM management permissions to enter the backend', () => {
    expect(
      hasBackendAccess({
        loginType: 'enterprise',
        permissions: ['hrm:recruit:post:query'],
      }),
    ).toBe(true);
  });

  it('keeps enterprise users with portal-only permissions in the backend', () => {
    expect(
      hasBackendAccess({
        loginType: 'enterprise',
        permissions: ['hrm:portal:query', 'hrm:portal:employee:update'],
      }),
    ).toBe(true);
  });

  it('allows an explicitly managed account without menu data', () => {
    expect(
      hasBackendAccess({
        loginType: 'enterprise',
        user: { isManagement: true },
      }),
    ).toBe(true);
  });
});
