import type { RouteRecordRaw } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';

import { $t } from '#/locales';

const BasicLayout = () => import('#/layouts/basic.vue');
const AuthPageLayout = () => import('#/layouts/auth.vue');
const PortalLayout = () => import('#/layouts/portal.vue');
const AiResumeLayout = () => import('#/layouts/ai-resume.vue');

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
    children: [
      /**
       * 通用实体详情页（静态路由，不挂菜单，与 /portal 同机制）：
       * 项目台账抽屉/列表行「详情 ↗」打开；:type ∈ PROJECT|TENDER_PROJECT|BID_SECTION|NOTICE|NOTICE_EXTERNAL。
       * 承载登记/编辑/发布（原工作台能力），不限实体类型；见 views/bidding/entityDetail/index.vue。
       */
      {
        name: 'BiddingEntityDetail',
        path: 'bidding/entity/:type/:code',
        component: () => import('#/views/bidding/entityDetail/index.vue'),
        meta: { title: '实体详情', hideInMenu: true, hideInTab: true },
      },
    ],
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
        meta: { title: '招聘应聘', hideInMenu: true, requiresAuth: true },
      },
      {
        name: 'PortalRecruitDetail',
        path: 'recruit/detail',
        component: () => import('#/views/portal/recruit/detail.vue'),
        meta: { title: '职位详情', hideInMenu: true, requiresAuth: true },
      },
      {
        name: 'PortalRecruitCompany',
        path: 'recruit/company',
        component: () => import('#/views/portal/recruit/company.vue'),
        meta: { title: '企业主页', hideInMenu: true, requiresAuth: true },
      },
      {
        name: 'PortalRecruitMessages',
        path: 'recruit/messages',
        redirect: { name: 'PortalRecruit', query: { section: 'messages' } },
        meta: { title: '招聘沟通', hideInMenu: true, requiresAuth: true },
      },
      {
        name: 'PortalRecruitProfileSetup',
        path: 'recruit/profile-setup',
        redirect: { name: 'PortalRecruit' },
        meta: { title: '在线简历', hideInMenu: true, requiresAuth: true },
      },
      {
        name: 'PortalRecruitResumeAi',
        path: 'recruit/resume/ai',
        redirect: '/ai-resume',
        meta: { title: 'AI简历', hideInMenu: true, requiresAuth: true },
      },
      {
        /*
         * 旧「附件简历制作」入口收敛到 AI 简历工作区，且直接落在生成模式。
         *
         * 停在 /ai-resume 的列表页等于让用户再找一次入口：他从「附件简历制作」
         * 点进来，想要的就是一份新简历，不是先在列表里挑一份旧的。
         * generate 模式在工作区里不挑来源、直接开空白草稿（见 diagnose.vue）。
         * 这个路由同时兜住门户顶部菜单、旧链接和其它页面里指向它的跳转。
         */
        name: 'PortalRecruitAttachmentBuilder',
        path: 'recruit/resume/attachment-builder',
        redirect: (to) => ({
          name: 'AiResumeDiagnose',
          query: { mode: 'generate', ...to.query },
        }),
        meta: { title: '附件简历制作', hideInMenu: true, requiresAuth: true },
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
            name: 'PortalTrainingCourseDetail',
            path: 'courses/detail',
            component: () =>
              import('#/views/portal/training/courses/detail.vue'),
            meta: { title: '课程详情', hideInMenu: true },
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
            meta: { title: '云培训学院考试系统', hideInMenu: true },
          },
          {
            name: 'PortalTrainingExamWorkspace',
            path: 'exam/workspace',
            component: () =>
              import('#/views/portal/training/exam/workspace.vue'),
            meta: { title: '进入考试系统', hideInMenu: true },
          },
          {
            name: 'PortalTrainingExamRoadmap',
            path: 'exam/roadmap',
            component: () =>
              import('#/views/portal/training/exam/roadmap.vue'),
            meta: { title: '职业认证进阶路径', hideInMenu: true },
          },
          {
            name: 'PortalTrainingExamCertDetail',
            path: 'exam/cert-detail',
            component: () =>
              import('#/views/portal/training/exam/cert-detail.vue'),
            meta: { title: '认证详情', hideInMenu: true },
          },
          {
            name: 'PortalTrainingExamBooking',
            path: 'exam/booking',
            component: () =>
              import('#/views/portal/training/exam/booking.vue'),
            meta: { title: '预约考试', hideInMenu: true },
          },
          {
            name: 'PortalTrainingExamRecert',
            path: 'exam/recert',
            component: () =>
              import('#/views/portal/training/exam/recert.vue'),
            meta: { title: '重认证规则', hideInMenu: true },
          },
          {
            name: 'PortalTrainingExamTraining',
            path: 'exam/training',
            component: () =>
              import('#/views/portal/training/exam/training.vue'),
            meta: { title: '查找培训', hideInMenu: true },
          },
          {
            name: 'PortalTrainingExamBooks',
            path: 'exam/books',
            component: () =>
              import('#/views/portal/training/exam/books.vue'),
            meta: { title: '参考书籍', hideInMenu: true },
          },
          {
            name: 'PortalTrainingExamDoc',
            path: 'exam/doc',
            component: () => import('#/views/portal/training/exam/doc.vue'),
            meta: { title: '公告文档', hideInMenu: true },
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
        /** 免登录（meta.ignoreAccess）：公告大厅为公开页，任何人可查 */
        meta: { title: '招标投标', hideInMenu: true, ignoreAccess: true },
      },
      {
        name: 'PortalBiddingDetail',
        path: 'bidding/detail/:id',
        component: () => import('#/views/portal/bidding/detail.vue'),
        /** 免登录（meta.ignoreAccess）：公告详情随大厅公开 */
        meta: { title: '公告详情', hideInMenu: true, ignoreAccess: true },
      },
      {
        name: 'PortalBiddingBidDocument',
        path: 'bidding/bid-document',
        component: () => import('#/views/portal/bidding/bidDocument.vue'),
        meta: { title: '标书制作', hideInMenu: true },
      },
      {
        name: 'PortalBiddingCompliance',
        path: 'bidding/compliance',
        component: () => import('#/views/portal/bidding/compliance.vue'),
        meta: { title: '合规审查', hideInMenu: true },
      },
      {
        name: 'PortalBiddingProcess',
        path: 'bidding/process',
        component: () => import('#/views/portal/bidding/process.vue'),
        meta: { title: '投标流程管理', hideInMenu: true },
      },
      {
        name: 'PortalBiddingInternalHall',
        path: 'bidding/internal-hall',
        component: () => import('#/views/portal/bidding/internalHall.vue'),
        /** 免登录（meta.ignoreAccess）：本平台已发布公告同属公开信息 */
        meta: { title: '本平台发布', hideInMenu: true, ignoreAccess: true },
      },
      {
        name: 'PortalBiddingInternalDetail',
        path: 'bidding/internal-detail/:id',
        component: () => import('#/views/portal/bidding/internalDetail.vue'),
        /** 免登录（meta.ignoreAccess）：本平台公告详情随大厅公开 */
        meta: { title: '本平台公告详情', hideInMenu: true, ignoreAccess: true },
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
        meta: { title: '个人中心', hideInMenu: true },
      },
      {
        name: 'PortalProfileRecruit',
        path: 'profile/recruit',
        component: () => import('#/views/portal/profile/recruit.vue'),
        meta: { title: '应聘记录', hideInMenu: true, requiresAuth: true },
      },
    ],
  },
  {
    component: AiResumeLayout,
    meta: {
      hideInBreadcrumb: true,
      hideInMenu: true,
      hideInTab: true,
      requiresAuth: true,
      title: 'AI 简历',
    },
    name: 'AiResume',
    path: '/ai-resume',
    redirect: { name: 'AiResumeEditor' },
    children: [
      {
        name: 'AiResumeEditor',
        path: '',
        component: () => import('#/views/ai-resume/index.vue'),
        meta: { title: '简历 AI', hideInMenu: true, requiresAuth: true },
      },
      {
        name: 'AiResumeHome',
        path: 'home',
        component: () => import('#/views/ai-resume/index.vue'),
        meta: { title: '求职助理', hideInMenu: true, requiresAuth: true },
      },
      {
        // 简历工作区：左编辑器 / 中预览 / 右求职助手，诊断报告也在右栏切换。
        // 与「我的简历」网格分开成页，是因为它自带顶栏工具条，和列表页的
        // 留白版式不是一回事（参考站同样是独立路由 resume-builder）。
        name: 'AiResumeDiagnose',
        path: 'diagnose',
        component: () => import('#/views/ai-resume/diagnose.vue'),
        meta: { title: '简历诊断', hideInMenu: true, requiresAuth: true },
      },
      {
        name: 'AiResumeRecommend',
        path: 'recommend',
        component: () => import('#/views/ai-resume/index.vue'),
        meta: { title: '职位推荐', hideInMenu: true, requiresAuth: true },
      },
      {
        name: 'AiResumeProfile',
        path: 'profile',
        component: () => import('#/views/ai-resume/index.vue'),
        meta: { title: '个人档案', hideInMenu: true, requiresAuth: true },
      },
      {
        name: 'AiResumeSettings',
        path: 'settings',
        component: () => import('#/views/ai-resume/index.vue'),
        meta: { title: 'Agent 设置', hideInMenu: true, requiresAuth: true },
      },
      {
        name: 'AiResumeApplications',
        path: 'applications',
        component: () => import('#/views/ai-resume/index.vue'),
        meta: { title: '投递记录', hideInMenu: true, requiresAuth: true },
      },
      {
        name: 'AiResumeJob',
        path: 'job/:id',
        component: () => import('#/views/ai-resume/index.vue'),
        meta: { title: '职位详情', hideInMenu: true, requiresAuth: true },
      },
    ],
  },
  {
    // 兼容历史菜单/旧链接直接跳转 /training/exam/index。
    // 该路径不在系统菜单表中，独立注册无菜单冲突（勿在此处注册与
    // system_menu 重复的培训路由，否则侧边栏菜单会匹配失败而空白）。
    name: 'TrainingExamLegacyIndex',
    path: '/training/exam/index',
    redirect: '/training/exam/overview',
    meta: { title: '考试与认证', hideInMenu: true },
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
