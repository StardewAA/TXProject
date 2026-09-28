import { ok, type Tool } from './types.js';

export interface TaskFailureArgs {
  reason: string;
  detail?: string;
}

/** 任务失败上报（复用易标 piTaskFailureTool），编排层据此结束任务并转人工 */
export const taskFailureTool: Tool<TaskFailureArgs> = {
  name: 'task-failure',
  description:
    '上报无法继续的原因。适用场景：检索始终未命中、招标文件关键条款缺失、模板字段与资料库冲突无法自动消解。调用后本轮任务结束并转人工处理。',
  parameters: {
    type: 'object',
    properties: {
      reason: { type: 'string', description: '失败原因简述' },
      detail: { type: 'string', description: '补充细节' },
    },
    required: ['reason'],
    additionalProperties: false,
  },
  async execute(args, ctx) {
    const text = args.detail ? `${args.reason}｜${args.detail}` : args.reason;
    ctx.noteFailure(text);
    return ok({ reported: true, reason: text }, '已上报失败原因');
  },
};
