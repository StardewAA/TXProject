import { createApp, nextTick } from 'vue';

import Antd from 'ant-design-vue';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('@vben/icons', () => ({
  IconifyIcon: {
    name: 'IconifyIcon',
    props: { icon: { default: '', type: String } },
    setup: () => () => null,
  },
}));

const getRecruitApplicationPage = vi.fn();
vi.mock('#/api/hrm/recruit/application', () => ({
  getRecruitApplicationPage: (params: unknown) =>
    getRecruitApplicationPage(params),
}));

const submitCandidateSummary = vi.fn();
const submitApplicationMatch = vi.fn();
const submitInterviewQuestion = vi.fn();
const getRecruitAgentTask = vi.fn();
const buildAgentIdempotencyKey = vi.fn(() => 'agent-key');

vi.mock('#/api/hrm/recruit/agent', () => ({
  buildAgentIdempotencyKey: () => buildAgentIdempotencyKey(),
  getRecruitAgentTask: (taskId: number) => getRecruitAgentTask(taskId),
  submitApplicationMatch: (data: unknown) => submitApplicationMatch(data),
  submitCandidateSummary: (data: unknown) => submitCandidateSummary(data),
  submitInterviewQuestion: (data: unknown) => submitInterviewQuestion(data),
}));

import AiAssistant from './ai-assistant.vue';

const mounted: Array<{ app: ReturnType<typeof createApp>; host: HTMLElement }> =
  [];

function mountPanel() {
  const host = document.createElement('div');
  document.body.append(host);
  const app = createApp(AiAssistant, { candidateId: 9001 });
  app.use(Antd);
  app.mount(host);
  mounted.push({ app, host });
  return host;
}

async function flush() {
  for (let index = 0; index < 8; index++) {
    await nextTick();
    await Promise.resolve();
  }
}

/** 点击页签或「开始分析」按钮 */
async function click(host: HTMLElement, selector: string, index = 0) {
  const target = host.querySelectorAll(selector)[index];
  target?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  await flush();
}

afterEach(() => {
  for (const { app, host } of mounted.splice(0)) {
    app.unmount();
    host.remove();
  }
  vi.clearAllMocks();
});

describe('候选人 AI 辅助面板（A07 / A08 / A09）', () => {
  it('渲染免责说明与三个页签，且不改变业务状态', async () => {
    getRecruitApplicationPage.mockResolvedValue({ list: [], total: 0 });
    const host = mountPanel();
    await flush();

    expect(host.textContent).toContain('AI 辅助分析，仅供参考');
    expect(host.textContent).toContain('不会自动淘汰、录用或改变候选人');
    const tabs = [...host.querySelectorAll('.agent-tabs button')].map(
      (item) => item.textContent?.trim(),
    );
    expect(tabs).toEqual(['候选人摘要', '岗位匹配', '面试问题']);
  });

  it('没有应聘记录时明确说明入口依赖，不给出假结果', async () => {
    getRecruitApplicationPage.mockResolvedValue({ list: [], total: 0 });
    const host = mountPanel();
    await flush();

    expect(host.textContent).toContain('还没有应聘记录');
    expect(host.querySelector('.agent-result')).toBeNull();
  });

  it('摘要成功时区分事实、缺失与推断', async () => {
    getRecruitApplicationPage.mockResolvedValue({
      list: [{ id: 7001, postId: 8001, postName: '项目经理', statusName: '筛选中' }],
      total: 1,
    });
    submitCandidateSummary.mockResolvedValue(4001);
    getRecruitAgentTask.mockResolvedValue({
      taskId: 4001,
      scene: 'CANDIDATE_SUMMARY',
      status: 'SUCCEEDED',
      model: 'glm-4.5-air',
      promptVersion: 'candidate-summary-v1',
      totalTokens: 1400,
      result: {
        coreSkills: ['管道保温施工'],
        workYears: '简历未提供',
        experienceSummary: '负责过工业管道保温项目。',
        achievements: ['累计交付 6 个项目'],
        facts: [{ field: 'experiences', note: '在某公司任项目经理' }],
        missingInfo: ['未提供期望到岗时间'],
        inferences: ['长期驻外接受度需核实'],
      },
    });

    const host = mountPanel();
    await flush();
    await click(host, '.agent-actions button');

    expect(submitCandidateSummary).toHaveBeenCalledWith({
      applicationId: 7001,
      idempotencyKey: 'agent-key',
    });
    expect(host.textContent).toContain('简历未提供');
    expect(host.textContent).toContain('核心技能');
    expect(host.textContent).toContain('简历事实');
    expect(host.textContent).toContain('缺失信息');
    expect(host.textContent).toContain('AI 推断（需向候选人核实）');
    expect(host.textContent).toContain('1400 tokens');
  });

  it('匹配成功时展示风险与待核实项', async () => {
    getRecruitApplicationPage.mockResolvedValue({
      list: [{ id: 7002, postId: 8002, postName: '项目经理', resumeId: 6002 }],
      total: 1,
    });
    submitApplicationMatch.mockResolvedValue(4002);
    getRecruitAgentTask.mockResolvedValue({
      taskId: 4002,
      scene: 'APPLICATION_MATCH',
      status: 'SUCCEEDED',
      result: {
        overallScore: 76,
        dimensions: [
          {
            key: 'SKILL',
            score: 80,
            comment: '技能基本覆盖',
            evidence: [{ field: 'skills', note: '具备管道保温施工技能' }],
          },
        ],
        matched: [{ point: '具备现场施工管理经验', evidence: '负责过 6 个项目' }],
        missing: [{ point: '未体现出差意愿', evidence: '简历未提及' }],
        toConfirm: [{ point: '是否持有一级建造师证', reason: '简历未写明' }],
        risks: ['经历间隔较长，需要核实原因'],
      },
    });

    const host = mountPanel();
    await flush();
    await click(host, '.agent-tabs button', 1);
    await click(host, '.agent-actions button');

    expect(host.textContent).toContain('76');
    expect(host.textContent).toContain('技能');
    expect(host.textContent).toContain('优势（1）');
    expect(host.textContent).toContain('缺口（1）');
    expect(host.textContent).toContain('风险点（1）');
    expect(host.textContent).toContain('待核实（1）');
  });

  it('面试问题按类别展示目的与关注点', async () => {
    getRecruitApplicationPage.mockResolvedValue({
      list: [{ id: 7003, postId: 8003, postName: '项目经理', resumeId: 6003 }],
      total: 1,
    });
    submitInterviewQuestion.mockResolvedValue(4003);
    getRecruitAgentTask.mockResolvedValue({
      taskId: 4003,
      scene: 'INTERVIEW_QUESTION',
      status: 'SUCCEEDED',
      result: {
        questions: [
          {
            category: 'PROFESSIONAL',
            question: '保温层厚度如何确定？',
            purpose: '核实专业知识',
            focus: '保温计算依据',
          },
          {
            category: 'VERIFICATION',
            question: '二级建造师是否已注册？',
            purpose: '核实证书状态',
            focus: '注册单位',
          },
        ],
      },
    });

    const host = mountPanel();
    await flush();
    await click(host, '.agent-tabs button', 2);
    await click(host, '.agent-actions button');

    expect(host.textContent).toContain('专业类');
    expect(host.textContent).toContain('核实类');
    expect(host.textContent).toContain('保温层厚度如何确定？');
    expect(host.textContent).toContain('提问目的');
    expect(host.textContent).toContain('关注点');
    expect(host.textContent).toContain('不会自动写入面试评价');
  });

  it('切换页签会清空上一场景结果，避免张冠李戴', async () => {
    getRecruitApplicationPage.mockResolvedValue({
      list: [{ id: 7004, postId: 8004, postName: '项目经理', resumeId: 6004 }],
      total: 1,
    });
    submitCandidateSummary.mockResolvedValue(4004);
    getRecruitAgentTask.mockResolvedValue({
      taskId: 4004,
      scene: 'CANDIDATE_SUMMARY',
      status: 'SUCCEEDED',
      result: {
        coreSkills: [],
        workYears: '简历未提供',
        experienceSummary: '摘要内容',
        achievements: [],
        facts: [],
        missingInfo: [],
        inferences: [],
      },
    });

    const host = mountPanel();
    await flush();
    await click(host, '.agent-actions button');
    expect(host.querySelector('.agent-result')).not.toBeNull();

    await click(host, '.agent-tabs button', 1);
    expect(host.querySelector('.agent-result')).toBeNull();
  });

  it('任务失败时展示服务端原因，不展示半成品结论', async () => {
    getRecruitApplicationPage.mockResolvedValue({
      list: [{ id: 7005, postId: 8005, postName: '项目经理', resumeId: 6005 }],
      total: 1,
    });
    submitCandidateSummary.mockResolvedValue(4005);
    getRecruitAgentTask.mockResolvedValue({
      taskId: 4005,
      scene: 'CANDIDATE_SUMMARY',
      status: 'FAILED',
      errorMessage: '该应聘记录关联的简历不是在线结构化简历，暂不支持 AI 分析',
    });

    const host = mountPanel();
    await flush();
    await click(host, '.agent-actions button');

    expect(host.textContent).toContain('本次分析未完成');
    expect(host.textContent).toContain('暂不支持 AI 分析');
    expect(host.querySelector('.agent-result')).toBeNull();
  });
});
