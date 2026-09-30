/**
 * 安许三面板挂载冒烟（2026-09-23，拆分自 1698 行单体页 safety-risk/index.vue）。
 *
 * 为什么要有：拆分只做了静态验证（eslint / vue-tsc / 逐行审计），三个宿主页在无浏览器环境里没人跑过。
 * 这里用挂载实测把"能渲染、区块标题在、onMounted 的取数按原时序发出"钉住 —— 静态检查抓不到
 * "ref 忘了声明""use*() 拿错实例""onMounted 分支走空"这类运行时问题。
 */
import type { SetupContext } from 'vue';

import { createApp, h, nextTick } from 'vue';

import Antd from 'ant-design-vue';
import { beforeEach, describe, expect, it, vi } from 'vitest';

/* vi.mock 会被提升到文件顶部：工厂里引用的变量必须用 vi.hoisted 一起提升，否则 Cannot access before initialization */
const fns = vi.hoisted(() => ({
  assignKeyPost: vi.fn(async () => true),
  assignRiskItem: vi.fn(async () => true),
  batchAlertRiskItem: vi.fn(async () => 0),
  checkMultiPost: vi.fn(async () => ({ conflict: false })),
  createRiskRule: vi.fn(async () => 1),
  deleteRiskRule: vi.fn(async () => true),
  exportRiskReport: vi.fn(async () => new Blob()),
  fetchSubjectProfile: vi.fn(async () => ({ id: 6001, subjectName: '某防腐保温公司', uscc: '91610000TEST0001X' })),
  getPersonnelCertPage: vi.fn(async () => ({ list: [], total: 0 })),
  getPersonnelProfile: vi.fn(async () => ({ list: [], total: 0 })),
  getPersonnelProfilePage: vi.fn(async () => ({ list: [], total: 0 })),
  getPersonnelRisk: vi.fn(async () => ({ items: [], total: 0 })),
  getProjectKeyPostChanges: vi.fn(async () => ({ list: [], total: 0 })),
  getProjectPage: vi.fn(async () => ({ list: [], total: 0 })),
  getRiskItem: vi.fn(async () => ({})),
  getRiskItemPage: vi.fn(async (_params?: Record<string, unknown>) => ({ list: [], total: 0 })),
  getRiskRulePage: vi.fn(async () => ({ list: [], total: 0 })),
  getRiskStats: vi.fn(async () => ({ handled: 0, high: 0, low: 0, medium: 0, total: 0 })),
  handleRiskItem: vi.fn(async () => true),
  scanRiskItem: vi.fn(async () => 0),
  updateRiskRule: vi.fn(async () => true),
  updateRiskRuleStatus: vi.fn(async () => true),
}));

vi.mock('#/api/bidding/safetyRisk', () => ({
  assignRiskItem: fns.assignRiskItem,
  createRiskRule: fns.createRiskRule,
  deleteRiskRule: fns.deleteRiskRule,
  exportRiskReport: fns.exportRiskReport,
  getRiskItem: fns.getRiskItem,
  getRiskItemPage: fns.getRiskItemPage,
  getRiskRulePage: fns.getRiskRulePage,
  getRiskStats: fns.getRiskStats,
  handleRiskItem: fns.handleRiskItem,
  scanRiskItem: fns.scanRiskItem,
  updateRiskRule: fns.updateRiskRule,
  updateRiskRuleStatus: fns.updateRiskRuleStatus,
}));
vi.mock('#/api/bidding/safetyPermit', () => ({
  assignKeyPost: fns.assignKeyPost,
  checkMultiPost: fns.checkMultiPost,
  getPersonnelRisk: fns.getPersonnelRisk,
  getProjectKeyPostChanges: fns.getProjectKeyPostChanges,
}));
vi.mock('#/api/bidding/personnel', () => ({
  getPersonnelCertPage: fns.getPersonnelCertPage,
  getPersonnelProfile: fns.getPersonnelProfile,
  getPersonnelProfilePage: fns.getPersonnelProfilePage,
}));
vi.mock('#/api/bidding/project', () => ({ getProjectPage: fns.getProjectPage }));
vi.mock('#/api/bidding/subject', () => ({ fetchSubjectProfile: fns.fetchSubjectProfile }));
vi.mock('@vben/access', () => ({ useAccess: () => ({ hasAccessByCodes: () => true }) }));
vi.mock('@vben/stores', () => ({ useAccessStore: () => ({ accessCodes: [] }) }));
vi.mock('@vben/icons', () => ({
  IconifyIcon: { name: 'IconifyIcon', props: ['icon'], render: () => null },
}));
vi.mock('../../certificate/useCertMeta', () => ({
  useCertMeta: () => ({
    dictLabel: (v: unknown) => String(v ?? ''),
    dictOptions: () => [],
    dictTag: () => undefined,
  }),
}));
vi.mock('../../certificate/modules/safety-risk.vue', () => ({
  default: { name: 'SafetyRiskStub', props: ['entId'], render: () => null },
}));
vi.mock('../../components/AttrFilter.vue', () => ({
  default: {
    name: 'AttrFilterStub',
    setup(_props: unknown, { slots }: SetupContext) {
      return () => h('div', slots.default?.());
    },
  },
}));
vi.mock('../modules/RuleConfigPanel.vue', () => ({
  default: { name: 'RuleConfigPanelStub', render: () => null },
}));

import EnterpriseSafetyPanels from './EnterpriseSafetyPanels.vue';
import PersonnelSafetyPanels from './PersonnelSafetyPanels.vue';
import ProjectKeyPostPanel from './ProjectKeyPostPanel.vue';

const flush = async () => {
  for (let i = 0; i < 10; i += 1) await nextTick();
  await new Promise((r) => setTimeout(r, 15));
  for (let i = 0; i < 10; i += 1) await nextTick();
};

async function mountPanel(comp: unknown) {
  document.body.innerHTML = '';
  const host = document.createElement('div');
  document.body.append(host);
  const app = createApp({ render: () => h(comp as never) });
  // 面板用的是全局注册的 antd 组件（a-button/a-table/...），测试 app 也要装上，否则解析失败
  app.use(Antd);
  app.mount(host);
  await flush();
  return { app, text: () => host.textContent || '' };
}

describe('安许三面板 · 挂载冒烟', () => {
  // 三个用例共用同一组 mock 函数，逐用例清调用记录，否则"未调用"断言会被前一个用例的调用污染
  beforeEach(() => {
    for (const f of Object.values(fns)) f.mockClear();
  });

  it('企业侧：① 风险卡片 + 风险大盘 + 风险清单都在；取主体后按 entId 拉工单与大盘', async () => {
    const view = await mountPanel(EnterpriseSafetyPanels);
    const text = view.text();
    expect(text).toContain('① 安许 A/B/C 风险');
    expect(text).toContain('风险清单');
    expect(fns.fetchSubjectProfile).toHaveBeenCalled();
    expect(fns.getRiskItemPage).toHaveBeenCalled();
    expect(fns.getRiskStats).toHaveBeenCalled();
    // entId 以字符串下发（与拆分前一致，RiskItemPageReq.entId 是 string）
    const firstParams = fns.getRiskItemPage.mock.calls[0]?.[0];
    expect(String(firstParams?.entId)).toBe('6001');
    view.app.unmount();
  });

  it('员工侧：② 多岗兼任校验 + ③ 人员风险都在；取主体后建员工索引', async () => {
    const view = await mountPanel(PersonnelSafetyPanels);
    const text = view.text();
    expect(text).toContain('② 多岗兼任校验');
    expect(text).toContain('③ 人员风险（12 个月岗位变更）');
    expect(fns.getPersonnelProfilePage).toHaveBeenCalled();
    // 员工侧不拉风险工单（拆分前只开 ②③ 的宿主同样不拉）
    expect(fns.getRiskItemPage).not.toHaveBeenCalled();
    view.app.unmount();
  });

  it('项目侧：④ 关键岗位变更监控在；只预加载项目下拉，不取主体、不拉工单', async () => {
    const view = await mountPanel(ProjectKeyPostPanel);
    expect(view.text()).toContain('④ 项目关键岗位变更监控');
    expect(fns.getProjectPage).toHaveBeenCalled();
    expect(fns.fetchSubjectProfile).not.toHaveBeenCalled();
    expect(fns.getRiskItemPage).not.toHaveBeenCalled();
    view.app.unmount();
  });
});
