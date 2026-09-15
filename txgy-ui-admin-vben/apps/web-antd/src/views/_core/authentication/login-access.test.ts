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

  it('keeps enterprise users without backend access in the portal', () => {
    expect(
      hasBackendAccess({
        loginType: 'enterprise',
        menus: [],
        permissions: [],
      }),
    ).toBe(false);
  });

  it('allows HRM management permissions to enter the backend', () => {
    expect(
      hasBackendAccess({
        loginType: 'enterprise',
        permissions: ['hrm:recruit:post:query'],
      }),
    ).toBe(true);
  });

  it('does not treat portal-only permissions as backend access', () => {
    expect(
      hasBackendAccess({
        loginType: 'enterprise',
        permissions: ['hrm:portal:query', 'hrm:portal:employee:update'],
      }),
    ).toBe(false);
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
