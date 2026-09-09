export type LoginType = 'enterprise' | 'personal';

interface LoginAccessInput {
  loginType: LoginType;
  /** 后端权限快照由后台路由生成阶段消费，入口不按数量设门槛。 */
  menus?: unknown[] | null;
  permissions?: unknown[] | null;
}

/**
 * 判断登录成功后的入口。
 * 企业员工统一进入管理后台，菜单和按钮是否可见由后端授权数据继续控制；
 * 无企业的个人用户统一进入员工门户。
 */
function hasBackendAccess({ loginType }: LoginAccessInput) {
  return loginType === 'enterprise';
}

export { hasBackendAccess };
