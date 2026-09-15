import type { RouteRecordRaw } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';

import { $t } from '#/locales';

const BasicLayout = () => import('#/layouts/basic.vue');
const AuthPageLayout = () => import('#/layouts/auth.vue');
const PortalLayout = () => import('#/layouts/portal.vue');

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

const coreRoutes: RouteRecordRaw[] = [
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
        name: 'PortalContent',
        path: 'content',
        component: () => import('#/views/portal/content/index.vue'),
        meta: { title: 'CMS系统', hideInMenu: true },
      },
      {
        name: 'PortalRecruit',
        path: 'recruit',
        component: () => import('#/views/portal/recruit/index.vue'),
        meta: { title: '招聘应聘', hideInMenu: true },
      },
      {
        name: 'PortalRecruitDetail',
        path: 'recruit/detail',
        component: () => import('#/views/portal/recruit/detail.vue'),
        meta: { title: '职位详情', hideInMenu: true },
      },
      {
        name: 'PortalRecruitMessages',
        path: 'recruit/messages',
        redirect: { name: 'PortalRecruit', query: { section: 'messages' } },
        meta: { title: '招聘沟通', hideInMenu: true },
      },
      {
        name: 'PortalRecruitProfileSetup',
        path: 'recruit/profile-setup',
        redirect: { name: 'PortalRecruit' },
        meta: { title: '在线简历', hideInMenu: true },
      },
      {
        name: 'PortalTraining',
        path: 'training',
        component: () => import('#/views/portal/training/index.vue'),
        redirect: '/portal/training/workbench',
        meta: { title: '云培训学院', hideInMenu: true },
        children: [
          {
            name: 'PortalTrainingWorkbench',
            path: 'workbench',
            component: () =>
              import('#/views/portal/training/workbench/index.vue'),
            meta: { title: '学习工作台', hideInMenu: true },
          },
          {
            name: 'PortalTrainingCourses',
            path: 'courses',
            component: () =>
              import('#/views/portal/training/courses/index.vue'),
            meta: { title: '课程中心', hideInMenu: true },
          },
          {
            name: 'PortalTrainingLearning',
            path: 'learning',
            component: () =>
              import('#/views/portal/training/learning/index.vue'),
            meta: { title: '我的学习', hideInMenu: true },
          },
          {
            name: 'PortalTrainingVr',
            path: 'vr',
            component: () => import('#/views/portal/training/vr/index.vue'),
            meta: { title: 'VR 实操训练', hideInMenu: true },
          },
          {
            name: 'PortalTrainingExam',
            path: 'exam',
            component: () =>
              import('#/views/portal/training/exam/index.vue'),
            meta: { title: '考试与证书', hideInMenu: true },
          },
          {
            name: 'PortalTrainingLive',
            path: 'live',
            component: () =>
              import('#/views/portal/training/live/index.vue'),
            meta: { title: '直播大厅', hideInMenu: true },
          },
          {
            name: 'PortalTrainingTasks',
            path: 'tasks',
            component: () =>
              import('#/views/portal/training/tasks/index.vue'),
            meta: { title: '培训任务与上岗', hideInMenu: true },
          },
          {
            name: 'PortalTrainingProfile',
            path: 'profile',
            component: () =>
              import('#/views/portal/training/profile/index.vue'),
            meta: { title: '个人档案与成长', hideInMenu: true },
          },
          {
            name: 'PortalTrainingCooperation',
            path: 'cooperation',
            component: () =>
              import('#/views/portal/training/cooperation/index.vue'),
            meta: { title: '校企合作专区', hideInMenu: true },
          },
          {
            name: 'PortalTrainingContest',
            path: 'contest',
            component: () =>
              import('#/views/portal/training/contest/index.vue'),
            meta: { title: '创新大赛专区', hideInMenu: true },
          },
        ],
      },
      {
        name: 'PortalTrainingStudy',
        path: 'training/study',
        component: () =>
          import('#/views/portal/training/learning/study.vue'),
        meta: { title: '在线学习', hideInMenu: true },
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
        component: () =>
          import('#/views/portal/bidding/internalDetail.vue'),
        meta: { title: '本平台公告详情', hideInMenu: true },
      },
      {
        name: 'PortalBiddingPublish',
        path: 'bidding/publish',
        component: () => import('#/views/portal/bidding/publish.vue'),
        meta: { title: '公告发布（企业前台）', hideInMenu: true },
      },
      {
        name: 'PortalBiddingProjects',
        path: 'bidding/projects',
        component: () =>
          import('#/views/portal/bidding/enterpriseMoved.vue'),
        meta: { title: '项目公告管理', hideInMenu: true },
      },
      {
        name: 'PortalBiddingProjectTenderProjects',
        path: 'bidding/projects/:projectCode/tender-projects',
        component: () =>
          import('#/views/portal/bidding/enterpriseMoved.vue'),
        meta: { title: '招标项目', hideInMenu: true },
      },
      {
        name: 'PortalBiddingProjectBidSections',
        path: 'bidding/projects/:projectCode/tender-projects/:tenderProjectCode/sections',
        component: () =>
          import('#/views/portal/bidding/enterpriseMoved.vue'),
        meta: { title: '标段（包）', hideInMenu: true },
      },
      {
        name: 'PortalBiddingSectionNotices',
        path: 'bidding/sections/:bidSectionCode/notices',
        component: () =>
          import('#/views/portal/bidding/enterpriseMoved.vue'),
        meta: { title: '公告 / 公示', hideInMenu: true },
      },
      {
        name: 'PortalProfile',
        path: 'profile',
        component: () => import('#/views/portal/profile/index.vue'),
        meta: { title: '个人中心', hideInMenu: true },
      },
      {
        name: 'PortalProfileRecruit',
        path: 'profile/recruit',
        component: () => import('#/views/portal/profile/recruit.vue'),
        meta: { title: '应聘记录', hideInMenu: true },
      },
    ],
  },
  {
    component: BasicLayout,
    meta: {
      title: '培训管理',
    },
    name: 'Training',
    path: '/training',
    redirect: '/training/exam/overview',
    children: [
      {
        name: 'TrainingExam',
        path: 'exam',
        component: () => import('#/views/training/exam/index.vue'),
        redirect: '/training/exam/overview',
        meta: { title: '考试与认证', hideInMenu: true },
        children: [
          {
            path: 'overview',
            name: 'TrainingExamOverview',
            component: () =>
              import('#/views/training/exam/overview/index.vue'),
            meta: { title: '总览', icon: 'lucide:layout-dashboard' },
          },
          {
            path: 'questions',
            name: 'TrainingExamQuestions',
            component: () =>
              import('#/views/training/exam/questions/index.vue'),
            meta: { title: '题库与审核', icon: 'lucide:list-checks' },
          },
          {
            path: 'papers',
            name: 'TrainingExamPapers',
            component: () =>
              import('#/views/training/exam/papers/index.vue'),
            meta: { title: '试卷管理', icon: 'lucide:file-text' },
          },
          {
            path: 'schedules',
            name: 'TrainingExamSchedules',
            component: () =>
              import('#/views/training/exam/schedules/index.vue'),
            meta: { title: '考试安排', icon: 'lucide:calendar' },
          },
          {
            path: 'grading',
            name: 'TrainingExamGrading',
            component: () =>
              import('#/views/training/exam/grading/index.vue'),
            meta: { title: '阅卷与成绩', icon: 'lucide:check-square' },
          },
          {
            path: 'certificates',
            name: 'TrainingExamCertificates',
            component: () =>
              import('#/views/training/exam/certificates/index.vue'),
            meta: { title: '证书管理', icon: 'lucide:award' },
          },
          {
            path: 'myexams',
            name: 'TrainingExamMyExams',
            component: () =>
              import('#/views/training/exam/myexams/index.vue'),
            meta: { title: '我的考试', icon: 'lucide:clipboard-list' },
          },
          {
            path: 'mycerts',
            name: 'TrainingExamMyCerts',
            component: () =>
              import('#/views/training/exam/mycerts/index.vue'),
            meta: { title: '我的证书', icon: 'lucide:badge-check' },
          },
          {
            path: 'wrong',
            name: 'TrainingExamWrong',
            component: () =>
              import('#/views/training/exam/wrong/index.vue'),
            meta: { title: '错题复盘', icon: 'lucide:rotate-ccw' },
          },
          {
            path: 'issuers',
            name: 'TrainingExamIssuers',
            component: () =>
              import('#/views/training/exam/issuers/index.vue'),
            meta: { title: '联合认证', icon: 'lucide:handshake' },
          },
        ],
      },
      {
        path: 'resource/doc-list',
        name: 'TrainingResourceDocList',
        component: () =>
          import('#/views/training/resource/doc-list/index.vue'),
        meta: { title: '文档资源库', hideInMenu: true },
      },
      {
        path: 'live/hall',
        name: 'TrainingLiveHall',
        component: () => import('#/views/training/live/live-hall.vue'),
        meta: { title: '直播大厅', hideInMenu: true },
      },
    ],
  },
];

export { coreRoutes, fallbackNotFoundRoute };