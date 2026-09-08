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

  it('sends enterprise users to the backend even when no menu has been granted yet', () => {
    expect(
      hasBackendAccess({
        loginType: 'enterprise',
        menus: [],
        permissions: [],
      }),
    ).toBe(true);
  });

  it('keeps the backend entry independent from the amount of granted permissions', () => {
    expect(
      hasBackendAccess({
        loginType: 'enterprise',
        permissions: ['hrm:recruit:post:query'],
      }),
    ).toBe(true);
  });
});
