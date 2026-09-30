import type { RouteLocationNormalized, RouteRecordRaw } from 'vue-router';

import { message } from 'ant-design-vue';

/**
 * HRM 路由 ↔ 菜单组件目录对照表（Q-019 / I-019）
 *
 * <p>HRM 页面绝大多数由数据库菜单（system_menu.component）动态注册，路径与目录一致；
 * 下面这些页面的 URL 与组件目录故意不一致（历史菜单路径已对外发布，改动会影响收藏与内部链接），
 * 排查时按本表定位即可，不必再去搜索：</p>
 *
 * <ul>
 *   <li>/hrm/salary/attendance-rule → hrm/salary/config/attendance-rule/index.vue（考勤计薪规则）</li>
 *   <li>/hrm/salary/rate-limit      → hrm/salary/config/rate-limit/index.vue（比例上限）</li>
 *   <li>/hrm/salary/group          → hrm/salary/config/group/index.vue（薪资组设置）</li>
 *   <li>/hrm/salary/option         → hrm/salary/config/option/index.vue（工资表设置）</li>
 *   <li>/hrm/salary/tax-rule       → hrm/salary/config/tax-rule/index.vue（计税规则）</li>
 *   <li>/hrm/salary/config         → hrm/salary/config/config/index.vue（计薪设置）</li>
 *   <li>/hrm/salary/change-template→ hrm/salary/config/change-template/index.vue（调薪模板）</li>
 *   <li>/hrm/insurance/scheme      → hrm/insurance/scheme/index.vue（社保方案，菜单挂在社保配置下）</li>
 *   <li>/hrm/attendance/group      → hrm/attendance/config/group/index.vue（考勤组设置）</li>
 *   <li>/hrm/attendance/holiday    → hrm/attendance/config/holiday/index.vue（节假日设置）</li>
 * </ul>
 */

/**
 * 详情页缺参守卫（Q-016 / I-016）
 *
 * <p>详情路由的编号参数改为可选（`:id?`），并在这里做统一守卫：直接打开
 * `/hrm/employee/detail` 这类缺编号的地址时，给出提示并回退到对应列表页，
 * 而不是落到 404 / 组件内取参数报错。</p>
 *
 * @param paramName 需要校验的路径参数名（如 id / employeeId）
 * @param fallbackPath 缺参时回退的列表页路径
 */
function requireNumberParam(paramName: string, fallbackPath: string) {
  return (to: RouteLocationNormalized) => {
    const raw = to.params[paramName];
    const value = Array.isArray(raw) ? raw[0] : raw;
    const isValid =
      value !== undefined &&
      value !== null &&
      String(value).trim() !== '' &&
      !Number.isNaN(Number(value));
    if (isValid) {
      return true;
    }
    message.warning('缺少必要参数，已返回列表页');
    return { path: fallbackPath, replace: true };
  };
}

const routes: RouteRecordRaw[] = [
  {
    path: '/hrm',
    name: 'HrmCenter',
    meta: {
      title: '人力资源',
      icon: 'lucide:users',
      keepAlive: true,
      hideInMenu: true,
    },
    children: [
      {
        path: 'dept/detail/:id?',
        beforeEnter: requireNumberParam('id', "/hrm/dept"),
        name: 'HrmDeptDetail',
        meta: {
          title: '组织详情',
          activePath: '/hrm/dept',
        },
        component: () => import('#/views/hrm/dept/detail/index.vue'),
      },
      {
        path: 'recruit/chat', name: 'HrmRecruitChat', meta: { title: '招聘沟通', activePath: '/hrm/recruit/chat' }, component: () => import('#/views/hrm/recruit/chat/index.vue'),
      },
      {
        path: 'recruit/post-type',
        name: 'HrmRecruitPostType',
        meta: { title: '职位类型', activePath: '/hrm/recruit/post-type' },
        component: () => import('#/views/hrm/recruit/post-type/index.vue'),
      },
      {
        path: 'recruit/candidate/detail/:id?',
        beforeEnter: requireNumberParam('id', "/hrm/recruit/candidate"),
        name: 'HrmRecruitCandidateDetail',
        meta: {
          title: '候选人详情',
          activePath: '/hrm/recruit/candidate',
        },
        component: () =>
          import('#/views/hrm/recruit/candidate/detail/index.vue'),
      },
      {
        path: 'recruit/post/detail/:id?',
        beforeEnter: requireNumberParam('id', "/hrm/recruit/post"),
        name: 'HrmRecruitPostDetail',
        meta: {
          title: '招聘职位详情',
          activePath: '/hrm/recruit/post',
        },
        component: () => import('#/views/hrm/recruit/post/detail/index.vue'),
      },
      {
        path: 'employee/detail/:id?',
        beforeEnter: requireNumberParam('id', "/hrm/employee/list"),
        name: 'HrmEmployeeDetail',
        meta: {
          title: '员工详情',
          activePath: '/hrm/employee/list',
        },
        component: () => import('#/views/hrm/employee/detail/index.vue'),
      },
      {
        path: 'attendance/month/detail/:employeeId?',
        beforeEnter: requireNumberParam('employeeId', "/hrm/attendance/month"),
        name: 'HrmAttendanceMonthDetail',
        meta: {
          title: '月度考勤详情',
          activePath: '/hrm/attendance/month',
        },
        component: () =>
          import('#/views/hrm/attendance/month/detail/index.vue'),
      },
      {
        path: 'insurance/month-record/detail/:id?',
        beforeEnter: requireNumberParam('id', "/hrm/insurance/month-record"),
        name: 'HrmInsuranceMonthRecordDetail',
        meta: {
          title: '月度社保详情',
          activePath: '/hrm/insurance/month-record',
        },
        component: () =>
          import('#/views/hrm/insurance/month-record/detail/index.vue'),
      },
      {
        path: 'salary/employee-info/detail/:id?',
        beforeEnter: requireNumberParam('id', "/hrm/salary/employee-info"),
        name: 'HrmSalaryEmployeeInfoDetail',
        meta: {
          title: '薪资档案详情',
          activePath: '/hrm/salary/employee-info',
        },
        component: () =>
          import('#/views/hrm/salary/employee-info/detail/index.vue'),
      },
      {
        path: 'salary/slip/send-record/detail/:id?',
        beforeEnter: requireNumberParam('id', "/hrm/salary/slip"),
        name: 'HrmSalarySlipSendRecordDetail',
        meta: {
          title: '工资条发放详情',
          activePath: '/hrm/salary/slip',
        },
        component: () =>
          import('#/views/hrm/salary/slip/send-record/detail/index.vue'),
      },
      {
        path: 'salary/history/detail/:id?',
        beforeEnter: requireNumberParam('id', "/hrm/salary/history"),
        name: 'HrmSalaryHistoryDetail',
        meta: {
          title: '历史工资详情',
          activePath: '/hrm/salary/history',
        },
        component: () =>
          import('#/views/hrm/salary/month-record/detail/index.vue'),
      },
      {
        path: 'performance/plan/detail/:id?',
        beforeEnter: requireNumberParam('id', "/hrm/performance/plan"),
        name: 'HrmPerformancePlanDetail',
        meta: {
          title: 'KPI 考核详情',
          activePath: '/hrm/performance/plan',
        },
        component: () =>
          import('#/views/hrm/performance/plan/detail/index.vue'),
      },
      {
        path: 'performance/plan/form',
        name: 'HrmPerformancePlanForm',
        meta: {
          title: 'KPI 考核设置',
          activePath: '/hrm/performance/plan',
        },
        component: () => import('#/views/hrm/performance/plan/form/index.vue'),
      },
      {
        path: 'performance/assessment/employee/:employeeId?',
        beforeEnter: requireNumberParam('employeeId', "/hrm/performance/assessment"),
        name: 'HrmPerformanceAssessmentEmployee',
        meta: {
          title: '员工绩效档案',
          activePath: '/hrm/performance/assessment',
        },
        component: () =>
          import('#/views/hrm/performance/assessment/employee/index.vue'),
      },
      {
        path: 'performance/assessment/detail/:id?',
        beforeEnter: requireNumberParam('id', "/hrm/performance/assessment"),
        name: 'HrmPerformanceAssessmentDetail',
        meta: {
          title: '员工考核详情',
          activePath: '/hrm/performance/assessment',
        },
        component: () =>
          import('#/views/hrm/performance/assessment/detail/index.vue'),
      },
      {
        path: 'salary/config/group',
        name: 'HrmSalaryConfigGroup',
        meta: { title: '薪资组', hideInMenu: true },
        component: () => import('#/views/hrm/salary/config/group/index.vue'),
      },
      {
        path: 'salary/config/tax-rule',
        name: 'HrmSalaryConfigTaxRule',
        meta: { title: '计税规则', hideInMenu: true },
        component: () => import('#/views/hrm/salary/config/tax-rule/index.vue'),
      },
      {
        path: 'salary/config/config',
        name: 'HrmSalaryConfigConfig',
        meta: { title: '计薪设置', hideInMenu: true },
        component: () => import('#/views/hrm/salary/config/config/index.vue'),
      },
      {
        path: 'salary/config/option',
        name: 'HrmSalaryConfigOption',
        meta: { title: '工资表设置', hideInMenu: true },
        component: () => import('#/views/hrm/salary/config/option/index.vue'),
      },
      {
        path: 'salary/config/change-template',
        name: 'HrmSalaryConfigChangeTemplate',
        meta: { title: '调薪模板', hideInMenu: true },
        component: () =>
          import('#/views/hrm/salary/config/change-template/index.vue'),
      },
      {
        path: 'insurance/config/scheme',
        name: 'HrmInsuranceConfigScheme',
        meta: { title: '社保方案', hideInMenu: true },
        component: () => import('#/views/hrm/insurance/scheme/index.vue'),
      },
      {
        path: 'attendance/config/group',
        name: 'HrmAttendanceConfigGroup',
        meta: { title: '考勤组', hideInMenu: true },
        component: () =>
          import('#/views/hrm/attendance/config/group/index.vue'),
      },
      {
        path: 'attendance/config/holiday',
        name: 'HrmAttendanceConfigHoliday',
        meta: { title: '节假日', hideInMenu: true },
        component: () =>
          import('#/views/hrm/attendance/config/holiday/index.vue'),
      },
      {
        path: 'portal/home',
        name: 'HrmPortalHome',
        meta: { title: '员工工作台', hideInMenu: true },
        component: () => import('#/views/hrm/portal/home/index.vue'),
      },
      {
        path: 'portal/employee',
        name: 'HrmPortalEmployee',
        meta: { title: '我的档案', hideInMenu: true },
        component: () => import('#/views/hrm/portal/employee/index.vue'),
      },
      {
        path: 'portal/insurance',
        name: 'HrmPortalInsurance',
        meta: { title: '我的社保', hideInMenu: true },
        component: () =>
          import('#/views/hrm/portal/insurance/record/index.vue'),
      },
      {
        path: 'portal/salary/slip',
        name: 'HrmPortalSalarySlip',
        meta: { title: '我的工资条', hideInMenu: true },
        component: () => import('#/views/hrm/portal/salary/slip/index.vue'),
      },
      {
        path: 'portal/attendance/report',
        name: 'HrmPortalAttendanceReport',
        meta: { title: '我的考勤', hideInMenu: true },
        component: () =>
          import('#/views/hrm/portal/attendance/report/index.vue'),
      },
      {
        path: 'portal/performance/assessment',
        name: 'HrmPortalPerformanceAssessment',
        meta: { title: '绩效任务', hideInMenu: true },
        component: () =>
          import('#/views/hrm/portal/performance/assessment/index.vue'),
      },
      {
        path: 'portal/performance/history',
        name: 'HrmPortalPerformanceHistory',
        meta: { title: '绩效档案', hideInMenu: true },
        component: () =>
          import('#/views/hrm/portal/performance/assessment/history/index.vue'),
      },
      {
        path: 'portal/opening-guide',
        name: 'HrmPortalOpeningGuide',
        meta: { title: '员工端开通引导', hideInMenu: true },
        component: () => import('#/views/hrm/portal/opening-guide/index.vue'),
      },
    ],
  },
];

export default routes;
