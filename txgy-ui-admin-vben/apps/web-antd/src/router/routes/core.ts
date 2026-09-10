import type { RouteRecordRaw } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';

import { $t } from '#/locales';

const BasicLayout = () => import('#/layouts/basic.vue');
const AuthPageLayout = () => import('#/layouts/auth.vue');
const PortalLayout = () => import('#/layouts/portal.vue');
/** 全局404页面 */
const fallbackNotFoundRoute: RouteRecordRaw = {
  component: () => import('#/views/_core/fallback/not-found.vue'),
  meta: {
    hideInBreadcrumb: true,
    hideInMenu: true,
    hideInTab: true,
    title: '404',
  },
  name: 'FallbackNotFound',
  path: '/:path(.*)*',
};

/** 基本路由，这些路由是必须存在的 */
const coreRoutes: RouteRecordRaw[] = [
  /**
   * 根路由
   * 使用基础布局，作为所有页面的父级容器，子级就不必配置BasicLayout。
   * 此路由必须存在，且不应修改
   */
  {
    component: BasicLayout,
    meta: {
      hideInBreadcrumb: true,
      title: 'Root',
    },
    name: 'Root',
    path: '/',
    redirect: preferences.app.defaultHomePath,
    children: [],
  },
  {
    component: AuthPageLayout,
    meta: {
      hideInTab: true,
      title: 'Authentication',
    },
    name: 'Authentication',
    path: '/auth',
    redirect: LOGIN_PATH,
    children: [
      {
        name: 'Login',
        path: 'login',
        component: () => import('#/views/_core/authentication/login.vue'),
        meta: {
          title: $t('page.auth.login'),
        },
      },
      {
        name: 'CodeLogin',
        path: 'code-login',
        component: () => import('#/views/_core/authentication/code-login.vue'),
        meta: {
          title: $t('page.auth.codeLogin'),
        },
      },
      {
        name: 'QrCodeLogin',
        path: 'qrcode-login',
        component: () =>
          import('#/views/_core/authentication/qrcode-login.vue'),
        meta: {
          title: $t('page.auth.qrcodeLogin'),
        },
      },
      {
        name: 'ForgetPassword',
        path: 'forget-password',
        component: () =>
          import('#/views/_core/authentication/forget-password.vue'),
        meta: {
          title: $t('page.auth.forgetPassword'),
        },
      },
      {
        name: 'Register',
        path: 'register',
        component: () => import('#/views/_core/authentication/register.vue'),
        meta: {
          title: $t('page.auth.register'),
        },
      },
      {
        name: 'SocialLogin',
        path: 'social-login',
        component: () =>
          import('#/views/_core/authentication/social-login.vue'),
        meta: {
          title: $t('page.auth.login'),
        },
      },
      {
        name: 'SSOLogin',
        path: 'sso-login',
        component: () => import('#/views/_core/authentication/sso-login.vue'),
        meta: {
          title: $t('page.auth.login'),
        },
      },
    ],
  },
  {
    component: PortalLayout,
    meta: {
      hideInBreadcrumb: true,
      hideInMenu: true,
      hideInTab: true,
      title: '员工端',
    },
    name: 'Portal',
    path: '/portal',
    redirect: '/portal/home',
    children: [
      {
        name: 'PortalHome',
        path: 'home',
        component: () => import('#/views/portal/home/index.vue'),
        meta: { title: '首页', hideInMenu: true },
      },
      {
        name: 'PortalRecruit',
        path: 'recruit',
        component: () => import('#/views/portal/recruit/index.vue'),
        meta: { title: '招聘应聘', hideInMenu: true },
      },
      {
        name: 'PortalTraining',
        path: 'training',
        component: () => import('#/views/portal/training/index.vue'),
        meta: { title: '云培训学院', hideInMenu: true },
      },
      {
        name: 'PortalTrainingLearning',
        path: 'training/learning',
        component: () => import('#/views/portal/training/learning/index.vue'),
        meta: { title: '我的学习', hideInMenu: true },
      },
      {
        name: 'PortalTrainingStudy',
        path: 'training/study',
        component: () => import('#/views/portal/training/learning/study.vue'),
        meta: { title: '在线学习', hideInMenu: true },
      },
      {
        name: 'PortalTrainingLive',
        path: 'training/live',
        component: () => import('#/views/portal/training/live/index.vue'),
        meta: { title: '直播大厅', hideInMenu: true },
      },
      {
        name: 'PortalTrainingProfile',
        path: 'training/profile',
        component: () => import('#/views/portal/training/profile/index.vue'),
        meta: { title: '学习档案', hideInMenu: true },
      },
      {
        name: 'PortalBidding',
        path: 'bidding',
        component: () => import('#/views/portal/bidding/index.vue'),
        meta: { title: '招标投标', hideInMenu: true },
      },
      {
        name: 'PortalBiddingDetail',
        path: 'bidding/detail/:id',
        component: () => import('#/views/portal/bidding/detail.vue'),
        meta: { title: '公告详情', hideInMenu: true },
      },
      {
        name: 'PortalBiddingSubscription',
        path: 'bidding/subscription',
        component: () => import('#/views/portal/bidding/subscription.vue'),
        meta: { title: '我的关注', hideInMenu: true },
      },
      {
        name: 'PortalBiddingProfile',
        path: 'bidding/profile',
        component: () => import('#/views/portal/bidding/profile.vue'),
        meta: { title: '个人中心（投标人）', hideInMenu: true },
      },
      {
        name: 'PortalBiddingInternalHall',
        path: 'bidding/internal-hall',
        component: () => import('#/views/portal/bidding/internalHall.vue'),
        meta: { title: '本平台发布', hideInMenu: true },
      },
      {
        name: 'PortalBiddingInternalDetail',
        path: 'bidding/internal-detail/:id',
        component: () => import('#/views/portal/bidding/internalDetail.vue'),
        meta: { title: '本平台公告详情', hideInMenu: true },
      },
      {
        name: 'PortalBiddingPublish',
        path: 'bidding/publish',
        component: () => import('#/views/bidding/notice/index.vue'),
        meta: { title: '公告发布（企业前台）', hideInMenu: true },
      },
      {
        name: 'PortalProfile',
        path: 'profile',
        component: () => import('#/views/portal/profile/index.vue'),
        meta: { title: '个人中心', hideInMenu: true },
      },
    ],
  },
  /** 招投标业务链工作台（VO 设计稿）：后端业务接口/菜单未就绪期间的可用稿，字段严格照录 VO 规范。
   *  核心路由不走权限校验、直接显示在菜单中，便于评审；后端接口与菜单就绪后可移除本入口。 */
  {
    component: BasicLayout,
    meta: {
      hideInBreadcrumb: true,
      icon: 'lucide:workflow',
      order: 3,
      title: '招投标业务链（VO 设计稿）',
    },
    name: 'BiddingChain',
    path: '/bidding-chain',
    redirect: '/bidding-chain/workbench',
    children: [
      {
        name: 'BiddingChainWorkbench',
        path: 'workbench',
        component: () => import('#/views/bidding/chain/index.vue'),
        meta: { icon: 'lucide:list-tree', title: '业务链工作台' },
      },
    ],
  },
];

export { coreRoutes, fallbackNotFoundRoute };
