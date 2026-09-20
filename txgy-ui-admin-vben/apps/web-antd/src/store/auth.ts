import type { AuthPermissionInfo, Recordable, UserInfo } from '@vben/types';

import type { AuthApi } from '#/api';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';
import { resetAllStores, useAccessStore, useUserStore } from '@vben/stores';

import { notification } from 'ant-design-vue';
import { defineStore } from 'pinia';

import {
  getAuthPermissionInfoApi,
  loginApi,
  logoutApi,
  register,
  smsLogin,
  socialLogin,
} from '#/api';
import { $t } from '#/locales';

export const useAuthStore = defineStore('auth', () => {
  const accessStore = useAccessStore();
  const userStore = useUserStore();
  const router = useRouter();

  const loginLoading = ref(false);

  /**
   * 异步处理登录操作
   * Asynchronously handle the login process
   * @param type 登录类型
   * @param params 登录表单数据
   * @param onSuccess 登录成功后的回调函数
   */
  async function authLogin(
    type: 'mobile' | 'register' | 'social' | 'username',
    params: Recordable<any>,
    onSuccess?: () => Promise<void> | void,
  ) {
    // 异步处理用户登录操作并获取 accessToken
    let userInfo: null | UserInfo = null;
    // 个人登录实际认证成功的租户：null 表示走了个人注册保留租户 0
    let personalEffectiveTenantId: null | number = null;
    try {
      let loginResult: AuthApi.LoginResult;
      loginLoading.value = true;
      accessStore.setAccessToken(null);
      accessStore.setRefreshToken(null);
      accessStore.clearVisitTenantId();
      accessStore.setIsAccessChecked(false);
      userStore.$reset();
      if (type === 'register') {
        accessStore.setTenantId(0);
      } else if (type === 'username' && params.tenantId != null) {
        accessStore.setTenantId(Number(params.tenantId));
      }
      switch (type) {
        case 'mobile': {
          loginResult = await smsLogin(params as AuthApi.SmsLoginParams);
          break;
        }
        case 'register': {
          loginResult = await register(params as AuthApi.RegisterParams);
          break;
        }
        case 'social': {
          loginResult = await socialLogin(params as AuthApi.SocialLoginParams);
          break;
        }
        default: {
          const isPersonalLogin =
            type === 'username' && params.loginType === 'personal';
          if (isPersonalLogin && params.tenantId == null) {
            // 个人登录兼容两代账号体系：旧个人账号在默认企业租户下（历史行为），
            // 新个人注册账号落在保留租户 0。先按默认租户认证，失败静默回退租户 0 重试。
            const defaultTenantId = Number(
              import.meta.env.VITE_APP_DEFAULT_TENANT_ID || 1,
            );
            accessStore.setTenantId(defaultTenantId);
            personalEffectiveTenantId = defaultTenantId;
          }
          try {
            loginResult = await loginApi(params);
          } catch (error) {
            if (isPersonalLogin) {
              accessStore.setTenantId(0);
              personalEffectiveTenantId = null;
              loginResult = await loginApi({ ...params, silentError: true });
            } else {
              throw error;
            }
          }
        }
      }
      const { accessToken, refreshToken } = loginResult;

      // 如果成功获取到 accessToken
      if (accessToken) {
        // 个人登录和个人注册归属个人租户体系：新个人注册账号固定在后端保留的 tenant_id=0；
        // 个人登录则按实际认证成功的租户回填（旧个人账号在默认租户下，新账号在租户 0），
        // 避免请求权限信息时 tenant-id 与账号实际租户错配被后端判为越权。
        const isPersonalAuthentication =
          (type === 'register' && params.registerType === 'personal') ||
          (type === 'username' && params.loginType === 'personal');
        if (isPersonalAuthentication) {
          accessStore.setTenantId(personalEffectiveTenantId ?? 0);
          accessStore.clearVisitTenantId();
        } else {
          // 企业登录：把「访问租户」初始化为本次登录租户，
          // 否则顶部租户下拉永远显示占位符「请选择租户」且无法回显当前租户。
          const loginTenantId =
            params.tenantId != null ? Number(params.tenantId) : null;
          if (
            loginTenantId != null &&
            Number.isInteger(loginTenantId) &&
            loginTenantId > 0
          ) {
            accessStore.setVisitTenantId(loginTenantId);
          }
        }
        // 企业登录：以后端返回的「账号实际所属租户」为准，自动纠正登录时选错的租户，
        // 避免出现"选了租户 A、账号却属于租户 B"导致的权限 403
        if (
          !isPersonalAuthentication &&
          loginResult.tenantId !== null &&
          loginResult.tenantId !== undefined
        ) {
          accessStore.setTenantId(Number(loginResult.tenantId));
        }
        accessStore.setAccessToken(accessToken);
        accessStore.setRefreshToken(refreshToken);

        // 获取用户信息并存储到 userStore、accessStore 中
        // TODO @芋艿：清理掉 accessCodes 相关的逻辑
        // const [fetchUserInfoResult, accessCodes] = await Promise.all([
        //   fetchUserInfo(),
        //   // getAccessCodesApi(),
        // ]);
        const fetchUserInfoResult = await fetchUserInfo();

        userInfo = fetchUserInfoResult.user;

        if (accessStore.loginExpired) {
          accessStore.setLoginExpired(false);
          // 会话过期后重新登录（弹窗模式）：统一回到默认页面，
          // 而不是停留在令牌失效前的当前页面。
          const isPersonal =
            params.loginType === 'personal' ||
            (type === 'register' && params.registerType === 'personal');
          const expiredHomePath = isPersonal
            ? '/portal/home'
            : userInfo.homePath && !userInfo.homePath.startsWith('/portal/')
              ? userInfo.homePath
              : preferences.app.defaultHomePath;
          await router.replace(expiredHomePath);
        } else {
          // oxlint-disable-next-line no-unused-expressions
          onSuccess
            ? await onSuccess?.()
            : await router.push(
                params.loginType === 'enterprise' &&
                  userInfo.homePath?.startsWith('/portal/')
                  ? preferences.app.defaultHomePath
                  : userInfo.homePath || preferences.app.defaultHomePath,
              );
        }

        if (!isPersonalAuthentication && userInfo?.nickname) {
          notification.success({
            description: `${$t('authentication.loginSuccessDesc')}:${userInfo?.nickname}`,
            duration: 3,
            message: $t('authentication.loginSuccess'),
          });
        }
      }
    } catch (error) {
      accessStore.setAccessToken(null);
      accessStore.setRefreshToken(null);
      accessStore.clearVisitTenantId();
      accessStore.setIsAccessChecked(false);
      accessStore.setAccessCodes([]);
      accessStore.setAccessMenus([]);
      accessStore.setAccessRoutes([]);
      userStore.$reset();
      throw error;
    } finally {
      loginLoading.value = false;
    }

    return {
      userInfo,
    };
  }

  async function logout(redirect: boolean = true) {
    try {
      const accessToken = accessStore.accessToken as string;
      if (accessToken) {
        await logoutApi(accessToken);
      }
    } catch {
      // 不做任何处理
    }
    resetAllStores();
    accessStore.setLoginExpired(false);

    // 回登录页带上当前路由地址
    await router.replace({
      path: LOGIN_PATH,
      query: redirect
        ? {
            redirect: encodeURIComponent(router.currentRoute.value.fullPath),
          }
        : {},
    });
  }

  async function fetchUserInfo() {
    // 加载
    const authPermissionInfo: AuthPermissionInfo | null =
      await getAuthPermissionInfoApi();
    // userStore
    userStore.setUserInfo(authPermissionInfo.user);
    userStore.setUserRoles(authPermissionInfo.roles);
    // accessStore
    accessStore.setAccessMenus(authPermissionInfo.menus);
    accessStore.setAccessCodes(authPermissionInfo.permissions);
    return authPermissionInfo;
  }

  function $reset() {
    loginLoading.value = false;
  }

  return {
    $reset,
    authLogin,
    fetchUserInfo,
    loginLoading,
    logout,
  };
});
