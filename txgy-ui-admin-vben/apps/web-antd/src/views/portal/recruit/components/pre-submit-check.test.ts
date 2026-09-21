import { createApp, nextTick } from 'vue';

import Antd from 'ant-design-vue';
import { afterEach, describe, expect, it, vi } from 'vitest';

// 图标与 Antd 都做轻量处理：本测试只关心提醒逻辑，不验证样式
vi.mock('@vben/icons', () => ({
  IconifyIcon: {
    name: 'IconifyIcon',
    props: { icon: { default: '', type: String } },
    setup: () => () => null,
  },
}));

const submitPreSubmitCheck = vi.fn();
const getRecruitAgentTask = vi.fn();
const buildAnalyzeIdempotencyKey = vi.fn(() => 'precheck-key');

vi.mock('#/api/hrm/portal/recruit-agent', () => ({
  buildAnalyzeIdempotencyKey: () => buildAnalyzeIdempotencyKey(),
  getRecruitAgentTask: (taskId: number) => getRecruitAgentTask(taskId),
  submitPreSubmitCheck: (data: unknown) => submitPreSubmitCheck(data),
}));

import PreSubmitCheck from './pre-submit-check.vue';

const mounted: Array<{ app: ReturnType<typeof createApp>; host: HTMLElement }> =
  [];

function mountCheck(props: Record<string, unknown> = {}) {
  const host = document.createElement('div');
  document.body.append(host);
  const app = createApp(PreSubmitCheck, {
    postId: 1001,
    resumeVersionId: 2001,
    ...props,
  });
  app.use(Antd);
  app.mount(host);
  mounted.push({ app, host });
  return host;
}

/** 点击「检查一下」，并把两次异步请求都跑完 */
async function clickCheck(host: HTMLElement) {
  const button = host.querySelector('button');
  button?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  await flush();
}

async function flush() {
  for (let index = 0; index < 8; index++) {
    await nextTick();
    await Promise.resolve();
  }
}

afterEach(() => {
  for (const { app, host } of mounted.splice(0)) {
    app.unmount();
    host.remove();
  }
  vi.clearAllMocks();
});

describe('投递前检查（A06）', () => {
  it('初始渲染说明这是可选步骤，且不影响投递', () => {
    const host = mountCheck();
    expect(host.textContent).toContain('投递前检查');
    expect(host.textContent).toContain('不影响投递');
    // 未点击前不应出现任何结论
    expect(host.querySelector('.precheck-result')).toBeNull();
  });

  it('检查成功时按三类展示提醒，并带免责标识', async () => {
    submitPreSubmitCheck.mockResolvedValue(3001);
    getRecruitAgentTask.mockResolvedValue({
      status: 'SUCCEEDED',
      result: {
        summary: '整体信息完整，建议补充项目量化成果',
        requiredMissing: [{ item: '缺少期望到岗时间', note: '便于安排面试' }],
        conflicts: [{ point: '年限与经历对不上', evidence: '经历从 2020 年开始' }],
        gaps: [{ point: '未体现压力管道资质', evidence: '职位要求该资质' }],
      },
    });

    const host = mountCheck();
    await clickCheck(host);

    expect(host.textContent).toContain('整体信息完整，建议补充项目量化成果');
    expect(host.textContent).toContain('建议补充');
    expect(host.textContent).toContain('可能矛盾');
    expect(host.textContent).toContain('与岗位的差距');
    expect(host.textContent).toContain('AI 辅助分析，仅供参考');
    // 传参必须使用当前简历版本与职位
    expect(submitPreSubmitCheck).toHaveBeenCalledWith({
      resumeVersionId: 2001,
      postId: 1001,
      idempotencyKey: 'precheck-key',
    });
  });

  it('没有问题项时如实说明，不编造内容', async () => {
    submitPreSubmitCheck.mockResolvedValue(3002);
    getRecruitAgentTask.mockResolvedValue({
      status: 'SUCCEEDED',
      result: {
        summary: '本次没有发现明显问题',
        requiredMissing: [],
        conflicts: [],
        gaps: [],
      },
    });

    const host = mountCheck();
    await clickCheck(host);

    expect(host.textContent).toContain('本次没有发现明显问题');
    expect(host.querySelector('.precheck-group')).toBeNull();
  });

  it('任务失败时展示服务端原因，不展示任何结果', async () => {
    submitPreSubmitCheck.mockResolvedValue(3003);
    getRecruitAgentTask.mockResolvedValue({
      status: 'FAILED',
      errorMessage: 'AI 模型尚未配置，请联系管理员',
    });

    const host = mountCheck();
    await clickCheck(host);

    expect(host.textContent).toContain('AI 模型尚未配置，请联系管理员');
    expect(host.querySelector('.precheck-result')).toBeNull();
  });

  it('缺少简历版本或职位时按钮不可用，不发起请求', async () => {
    const host = mountCheck({ resumeVersionId: undefined });
    const button = host.querySelector('button') as HTMLButtonElement | null;
    expect(button?.disabled).toBe(true);

    await clickCheck(host);
    expect(submitPreSubmitCheck).not.toHaveBeenCalled();
  });
});
