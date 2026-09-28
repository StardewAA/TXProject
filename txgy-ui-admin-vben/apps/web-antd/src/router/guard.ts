import type { Router } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { $t } from '@vben/locales';
import { preferences } from '@vben/preferences';
import {
  resetAllStores,
  useAccessStore,
  useDictStore,
  useUserStore,
} from '@vben/stores';
import { startProgress, stopProgress } from '@vben/utils';

import { message } from 'ant-design-vue';

import { getSimpleDictDataList } from '#/api/system/dict/data';
import { accessRoutes, coreRouteNames } from '#/router/routes';
import { useAuthStore } from '#/store';

import { generateAccess } from './access';

/**
 * 通用守卫配置
 * @param router
 */
function setupCommonGuard(router: Router) {
  // 记录已经加载的页面
  const loadedPaths = new Set<string>();

  router.beforeEach((to) => {
    to.meta.loaded = loadedPaths.has(to.path);

    // 页面加载进度条
    if (!to.meta.loaded && preferences.transition.progress) {
      startProgress();
    }
    return true;
  });

  router.afterEach((to) => {
    // 记录页面是否加载,如果已经加载，后续的页面切换动画等效果不在重复执行

    loadedPaths.add(to.path);

    // 关闭页面加载进度条
    if (preferences.transition.progress) {
      stopProgress();
    }
  });
}

/**
 * 权限访问守卫配置
 * @param router
 */
function setupAccessGuard(router: Router) {
  router.beforeEach(async (to, from) => {
    const accessStore = useAccessStore();
    const userStore = useUserStore();
    const authStore = useAuthStore();
    const dictStore = useDictStore();

    if (to.path === LOGIN_PATH || to.path === '/auth/register') {
      // 登录提交时会清理旧会话；已有有效 token 时不能在守卫中 reset，
      // 否则刷新恢复或短暂经过认证入口会清掉持久化会话。
      if (!accessStore.accessToken) {
        resetAllStores();
      }
      return true;
    }

    // HRM 应聘个人端的职位浏览、详情、沟通、资料和投递记录均属于登录后平台。
    // 该判断放在门户公开页例外之前，避免匿名用户通过直达 URL 绕过登录。
    const isHrmPersonalPortalRoute =
      to.path.startsWith('/portal/recruit') ||
      to.path.startsWith('/portal/profile/recruit');
    if (isHrmPersonalPortalRoute && !accessStore.accessToken) {
      message.warning({
        content: 'HRM 应聘平台需要登录后访问，正在跳转登录页…',
        key: 'hrm-portal-need-login',
      });
      return {
        path: LOGIN_PATH,
        query: { redirect: encodeURIComponent(to.fullPath) },
        replace: true,
      };
    }

    // 员工端门户：标注 meta.ignoreAccess 的公开页（招标投标 · 公告大厅/公告详情/本平台发布公告）免登录可查；
    // 其余门户页（首页/我要找工作/云培训学院/个人中心/我的关注/公告发布）仍必须登录后才能访问。
    // 依据：招标公告和公示信息发布管理办法（发改委令第10号）第12条——发布媒介须免费提供并允许公众查阅公告公示完整信息；
    //      后端 /notice-external/page|detail|go|pdf 与 /notice/public/* 均 @PermitAll + @TenantIgnore（模块 README §9.2）。
    if (to.path === '/portal' || to.path.startsWith('/portal/')) {
      // 公开页（meta.ignoreAccess）匿名可看，但仍要在已登录时补用户信息——
      // 否则登录用户从公开页进入门户，头部同样只剩兜底文案「用户」。
      const isPublicPortalPage = to.meta.ignoreAccess === true;

      if (!accessStore.accessToken) {
        if (isPublicPortalPage) {
          return true;
        }
        // 匿名访客点到「需登录」的门户页（首页/我要找工作/云培训学院/个人中心/我的关注/公告发布等）：
        // 先明确提示「需登录」再跳登录页（原先只静默跳转，用户不知道发生了什么）；登录后按 redirect 回到原页。
        // key 固定 → 连续点击只更新同一条提示，不叠加一屏。
        message.warning({
          content: '该页面需要登录后访问，正在跳转登录页…',
          key: 'portal-need-login',
        });
        return {
          path: LOGIN_PATH,
          query: { redirect: encodeURIComponent(to.fullPath) },
          replace: true,
        };
      }

      // 门户分支此前直接放行，跳过了下方的权限初始化，而 userInfo 不在持久化列表里
      // （见 packages/stores/src/modules/access.ts 的 persist.pick）。于是刷新或新开标签页后
      // 虽然 accessToken 还在，userStore.userInfo 却是空的：门户头部拿不到昵称，只剩
      // layouts/portal.vue 的兜底文案「用户」，看起来像被退出了登录。
      // 这里在登录态存在但用户信息缺失时补一次加载。不阻断导航：真正的 401 由请求拦截器的
      // 统一登出逻辑处理，此处失败只降级为显示兜底文案，不影响门户渲染。
      if (!userStore.userInfo) {
        try {
          await authStore.fetchUserInfo();
        } catch (error) {
          console.warn('门户加载用户信息失败', error);
        }
        // 加载期间若会话已被判定失效（拦截器已清空并跳登录页），按未登录处理，避免又放行回门户。
        if (!accessStore.accessToken) {
          return {
            path: LOGIN_PATH,
            query: { redirect: encodeURIComponent(to.fullPath) },
            replace: true,
          };
        }
      }
      return true;
    }

    // 个人账号（登录时落在后端保留租户 0；system_tenant 里没有 id=0 的租户）没有后台菜单，
    // userInfo.homePath 也是 null。而根路由 / 的 redirect、守卫各处的兜底都用
    // preferences.app.defaultHomePath（/dashboard/ai-home），于是个人用户会被带到企业后台——
    // 除了登录页显式回 /portal/home 那一条路径，其它入口（直接访问 /、/dashboard、/dashboard/*）
    // 都会落到那里。这里把个人账号从企业后台入口收敛回员工门户。
    if (
      accessStore.accessToken &&
      accessStore.tenantId === 0 &&
      (to.path === '/dashboard' ||
        to.path.startsWith('/dashboard/') ||
        to.path === preferences.app.defaultHomePath)
    ) {
      return { path: '/portal/home', replace: true };
    }

    // 基本路由，这些路由不需要进入权限拦截
    // AI 简历使用独立全屏路由，但仍必须复用现有登录态；普通核心路由保持原有免权限行为。
    if (
      coreRouteNames.includes(to.name as string) &&
      to.meta.requiresAuth !== true
    ) {
      return true;
    }

    // accessToken 检查
    if (!accessStore.accessToken) {
      // 明确声明忽略权限访问权限，则可以访问
      if (to.meta.ignoreAccess) {
        return true;
      }

      // 没有访问权限，跳转登录页面
      if (to.fullPath !== LOGIN_PATH) {
        return {
          path: LOGIN_PATH,
          // 如不需要，直接删除 query
          query:
            to.fullPath === preferences.app.defaultHomePath
              ? {}
              : { redirect: encodeURIComponent(to.fullPath) },
          // 携带当前跳转的页面，登录后重新跳转该页面
          replace: true,
        };
      }
      return to;
    }

    // 是否已经生成过动态路由
    if (accessStore.isAccessChecked) {
      return true;
    }

    // 加载字典数据（不阻塞加载）
    void dictStore.setDictCacheByApi(getSimpleDictDataList).catch((error) => {
      console.warn('加载字典失败', error);
    });

    // 生成路由表
    // 当前登录用户拥有的角色标识列表
    let userInfo = userStore.userInfo;
    if (!userInfo) {
      // add by 芋艿：由于 txgy 是 fetchUserInfo 统一加载用户 + 权限信息，所以将 fetchMenuListAsync
      const loading = message.loading({
        content: `${$t('common.loadingMenu')}...`,
      });
      try {
        const authPermissionInfo = await authStore.fetchUserInfo();
        if (authPermissionInfo) {
          userInfo = authPermissionInfo.user;
        }
      } finally {
        loading();
      }
    }
    const userRoles = userStore.userRoles ?? [];

    // 生成菜单和路由
    const { accessibleMenus, accessibleRoutes } = await generateAccess({
      roles: userRoles,
      router,
      // 则会在菜单中显示，但是访问会被重定向到403
      routes: accessRoutes,
    });

    // 保存菜单信息和路由信息
    accessStore.setAccessMenus(accessibleMenus);
    accessStore.setAccessRoutes(accessibleRoutes);
    accessStore.setIsAccessChecked(true);
    userStore.setUserRoles(userRoles);
    const isAuthenticationTransition = from.path === LOGIN_PATH;
    const redirectPath = (
      isAuthenticationTransition
        ? to.fullPath
        : (from.query.redirect ??
          (to.path === preferences.app.defaultHomePath
            ? userInfo?.homePath || preferences.app.defaultHomePath
            : to.fullPath))
    ) as string;

    return {
      ...router.resolve(decodeURIComponent(redirectPath)),
      replace: true,
    };
  });
}

/**
 * 项目守卫配置
 * @param router
 */
function createRouterGuard(router: Router) {
  /** 通用 */
  setupCommonGuard(router);
  /** 权限访问 */
  setupAccessGuard(router);
}

export { createRouterGuard };
