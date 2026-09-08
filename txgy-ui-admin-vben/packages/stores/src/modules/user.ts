import type { BasicUserInfo } from '@vben-core/typings';

import { acceptHMRUpdate, defineStore } from 'pinia';

interface AccessState {
  /**
   * 用户信息
   */
  userInfo: BasicUserInfo | null;
  /**
   * 用户角色
   */
  userRoles: string[];
}

/**
 * @zh_CN 用户信息相关
 */
export const useUserStore = defineStore('core-user', {
  actions: {
    setUserInfo(userInfo: BasicUserInfo | null) {
      this.userInfo = userInfo;
      // 用户信息是权限状态的来源之一；切换用户或退出登录时同步清理角色。
      if (!userInfo) {
        this.userRoles = [];
        return;
      }
      const roles = Array.isArray(userInfo.roles) ? userInfo.roles : [];
      this.userRoles = roles
        .map((role: unknown) => {
          if (typeof role === 'string') return role;
          if (role && typeof role === 'object') {
            const item = role as { code?: unknown; roleName?: unknown; value?: unknown };
            return item.value ?? item.code ?? item.roleName;
          }
          return undefined;
        })
        .filter((role): role is string => typeof role === 'string' && role.length > 0);
    },
    setUserRoles(roles: string[]) {
      this.userRoles = roles;
    },
  },
  state: (): AccessState => ({
    userInfo: null,
    userRoles: [],
  }),
});

// 解决热更新问题
const hot = import.meta.hot;
if (hot) {
  hot.accept(acceptHMRUpdate(useUserStore, hot));
}
