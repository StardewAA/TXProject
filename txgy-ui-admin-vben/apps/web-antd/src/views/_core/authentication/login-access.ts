export type LoginType = 'enterprise' | 'personal';

interface LoginAccessInput {
  loginType: LoginType;
  user?: unknown;
  roles?: unknown[] | null;
  menus?: unknown[] | null;
  permissions?: unknown[] | null;
}

const BACKEND_HRM_PERMISSION_PATTERN =
  /^hrm:(employee|recruit|attendance|insurance|salary|performance|home)(:|$)/i;
const MANAGEMENT_PERMISSION_PATTERN =
  /(^|:)(admin|manage|permission|tenant)(:|$)/i;

function getStringValue(value: unknown): string {
  if (typeof value === 'string') {
    return value;
  }
  if (!value || typeof value !== 'object') {
    return '';
  }
  const role = value as Record<string, unknown>;
  return String(role.value ?? role.code ?? role.roleName ?? '');
}

function hasManagementFlag(user: unknown): boolean {
  if (!user || typeof user !== 'object') {
    return false;
  }
  const userInfo = user as Record<string, unknown>;
  return [
    userInfo.isAdmin,
    userInfo.isManagement,
    userInfo.isManager,
    userInfo.admin,
    userInfo.management,
  ].some((value) => value === true || value === 1);
}

function hasBackendMenu(menus: unknown[] | null | undefined): boolean {
  return (menus ?? []).some((menu) => {
    if (!menu || typeof menu !== 'object') {
      return false;
    }
    const menuRecord = menu as Record<string, unknown>;
    const children = Array.isArray(menuRecord.children)
      ? menuRecord.children
      : [];
    if (children.length > 0) {
      return hasBackendMenu(children);
    }
    const values = [
      menuRecord.path,
      menuRecord.component,
      menuRecord.componentName,
      menuRecord.name,
    ].map((value) => String(value ?? '').toLowerCase());
    return values.some(
      (value) => value.length > 0 && !/(^|\/)portal(\/|$)/.test(value),
    );
  });
}

function hasBackendPermission(permissions: unknown[] | null | undefined) {
  return (permissions ?? []).some((permission) => {
    const code = getStringValue(permission);
    return (
      code.startsWith('crm:') ||
      MANAGEMENT_PERMISSION_PATTERN.test(code) ||
      BACKEND_HRM_PERMISSION_PATTERN.test(code)
    );
  });
}

/**
 * 判断登录成功后的入口。
 * 个人账号统一进入员工门户；企业账号只有存在管理身份或后台授权时才进入后台。
 */
function hasBackendAccess({
  loginType,
  menus,
  permissions,
  roles,
  user,
}: LoginAccessInput) {
  if (loginType !== 'enterprise') {
    return false;
  }
  if (hasManagementFlag(user)) {
    return true;
  }

  const userInfoRoles: unknown[] =
    user && typeof user === 'object'
      ? Array.isArray((user as Record<string, unknown>).roles)
        ? ((user as Record<string, unknown>).roles as unknown[])
        : []
      : [];
  const roleValues = [...(roles ?? []), ...userInfoRoles].map(getStringValue);
  const hasManagementRole = roleValues.some((role) =>
    /(admin|administrator|manager|manage|super|tenant)/i.test(role),
  );

  return (
    hasManagementRole ||
    hasBackendPermission(permissions) ||
    hasBackendMenu(menus)
  );
}

export { hasBackendAccess };
