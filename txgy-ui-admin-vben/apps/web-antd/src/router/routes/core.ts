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
      /**
       * 电子证照域独立详情页（静态路由，不挂菜单、不进页签）：
       * 列表行「查看 / 详情」跳转，页内按分区展示 基本信息 / 文件版本（上传·预览·下载）/ 验真 / 变更历史。
       * 取代原先"详情渲染在表格下方 / 抽屉里"的占位做法。
       */
      {
        name: 'BiddingPersonnelDetail',
        path: 'bidding/personnel/detail/:id',
        component: () => import('#/views/bidding/personnel/detail/index.vue'),
        meta: { title: '员工详情', hideInMenu: true, hideInTab: true },
      },
      {
        name: 'BiddingReviewTaskDetail',
        path: 'bidding/review-center/detail/:id',
        component: () => import('#/views/bidding/review-center/detail/index.vue'),
        meta: { title: '审查任务详情', hideInMenu: true, hideInTab: true },
      },
      {
        name: 'BiddingLifecycleDetail',
        path: 'bidding/certificate/lifecycle/detail/:id',
        component: () => import('#/views/bidding/certificate/lifecycle/detail/index.vue'),
        meta: { title: '生命周期记录详情', hideInMenu: true, hideInTab: true },
      },
      /**
       * 证照台账 · 独立详情/建档/编辑页（设计约束：详情与编辑用独立跳转页面，非抽屉/非弹窗）：
       *   · bidding/certificate/create        建档页（可编辑）
       *   · bidding/certificate/:id           详情页（只读）
       *   · bidding/certificate/:id/edit      编辑页（可编辑，乐观锁）
       * 返回经列表 URL Query 保留筛选/分页/排序状态。见 views/bidding/certificate/{form,detail}.vue。
       */
      {
        name: 'BiddingCertCreate',
        path: 'bidding/certificate/create',
        component: () => import('#/views/bidding/certificate/form.vue'),
        meta: { title: '证照建档', hideInMenu: true, hideInTab: true },
      },
      {
        name: 'BiddingCertDetail',
        path: 'bidding/certificate/:id',
        component: () => import('#/views/bidding/certificate/detail.vue'),
        meta: { title: '证照详情', hideInMenu: true, hideInTab: true },
      },
      {
        name: 'BiddingCertEdit',
        path: 'bidding/certificate/:id/edit',
        component: () => import('#/views/bidding/certificate/form.vue'),
        meta: { title: '证照编辑', hideInMenu: true, hideInTab: true },
      },
      /**
       * 企业资质 · 独立详情/建档页（设计约束：详情用独立跳转页面，非抽屉；编辑内联到详情页）
       *   · bidding/qualification/create          建档页（初始即编辑态、字段为空）
       *   · bidding/qualification/detail/:id      详情页（只读态；?mode=edit 切编辑态，同页内联编辑）
       * 同一组件 detail.vue 承载两种场景，靠路由名区分；编辑态用 query ?mode=edit 标识，刷新不丢。
       * 列表页返回无需 URL Query 同步：菜单 60005200 keep_alive=1，组件实例被缓存，
       * 筛选/分页原样保留，数据新鲜度由列表页 onActivated 刷新保证。
       */
      {
        name: 'BiddingQualificationCreate',
        path: 'bidding/qualification/create',
        component: () => import('#/views/bidding/qualification/detail.vue'),
        meta: { title: '资质建档', hideInMenu: true, hideInTab: true },
      },
      {
        name: 'BiddingQualificationDetail',
        path: 'bidding/qualification/detail/:id',
        component: () => import('#/views/bidding/qualification/detail.vue'),
        meta: { title: '资质详情', hideInMenu: true, hideInTab: true },
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
        meta: { title: '招标投标', hideInMenu: true },
      },
      {
        name: 'PortalBiddingDetail',
        path: 'bidding/detail/:id',
        component: () => import('#/views/portal/bidding/detail.vue'),
        meta: { title: '公告详情', hideInMenu: true },
      },
      {
        name: 'PortalBiddingBidDocument',
        path: 'bidding/bid-document',
        component: () => import('#/views/portal/bidding/bidDocument.vue'),
        meta: { title: '标书制作', hideInMenu: true },
      },
      /* 原 'bidding/compliance'（本地流程状态机版合规审查页）2026-09-20 用户裁定删除：
       * 门户只保留一套「合规审查」= 标书审查工作台 bidReview/*（真实接口）；
       * 两套数据不互通，且原页在没走过投标流程时恒为空态。
       * 页面文件 views/portal/bidding/compliance.vue 已无任何 import，可直接删除。 */
      {
        name: 'PortalBiddingProcess',
        path: 'bidding/process',
        component: () => import('#/views/portal/bidding/process.vue'),
        meta: { title: '投标流程管理', hideInMenu: true },
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
        component: () => import('#/views/portal/bidding/publish.vue'),
        meta: { title: '公告发布（企业前台）', hideInMenu: true },
      },
      {
        name: 'PortalBiddingBidReview',
        path: 'bidding/bidReview',
        // 用户 2026-09-17：标书审查与审查历史拆成两个页面，左侧栏切换（workbench 承载侧栏 + 子路由）
        component: () => import('#/views/portal/bidding/bidReview/workbench.vue'),
        redirect: '/portal/bidding/bidReview/review',
        meta: { title: '标书审查（个人端）', hideInMenu: true },
        children: [
          {
            name: 'PortalBiddingBidReviewReview',
            path: 'review',
            component: () => import('#/views/portal/bidding/bidReview/index.vue'),
            meta: { title: '标书审查', hideInMenu: true },
          },
          {
            name: 'PortalBiddingBidReviewHistory',
            path: 'history',
            component: () => import('#/views/portal/bidding/bidReview/history.vue'),
            meta: { title: '审查历史', hideInMenu: true },
          },
          {
            name: 'PortalBiddingBidReviewResult',
            path: 'result',
            component: () => import('#/views/portal/bidding/bidReview/result.vue'),
            meta: { title: '标书审查结论（废标判定）', hideInMenu: true },
          },
        ],
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
