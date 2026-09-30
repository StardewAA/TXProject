export type LoginType = 'enterprise' | 'personal';

interface LoginAccessInput {
  loginType: LoginType;
  user?: unknown;
  roles?: unknown[] | null;
  menus?: unknown[] | null;
  permissions?: unknown[] | null;
}

/**
 * 判断登录成功后的入口。
 *
 * 规则（2026-09-17 调整）：
 * - 企业登录（属于租户的员工/用户）：一律进入后台，侧边栏只显示各自有权限的菜单；
 *   不再因为"没有后台菜单/权限"就把企业账号送到员工门户。
 * - 个人登录（求职者等保留租户的账号）：进入员工门户。
 */
function hasBackendAccess({ loginType }: LoginAccessInput) {
  return loginType === 'enterprise';
}

export { hasBackendAccess };
