import { ok, type Tool } from './types.js';

export interface AskUserArgs {
  question: string;
  options?: string[];
  context?: string;
  required?: boolean;
}

/**
 * ask-user：HIL 的核心实现（复用易标 piUserQuestionTool 语义）。
 * 调用后任务转入 awaiting_human，等待业务侧 POST /agent/tasks/{taskId}/confirm。
 */
export const askUserTool: Tool<AskUserArgs> = {
  name: 'ask-user',
  description:
    '向人工提问并等待答复。用于：证据不足无法落笔、需要确认报价策略/工期等关键约束、人工签字盖章类字段。回答缺失必要信息时优先使用本工具，而不是自行编造。',
  parameters: {
    type: 'object',
    properties: {
      question: { type: 'string', description: '要问人的问题，需具体可执行' },
      options: { type: 'array', items: { type: 'string' }, description: '可选答案列表' },
      context: { type: 'string', description: '补充背景，说明为什么需要人工介入' },
      required: { type: 'boolean', description: '是否必须回答，默认 true' },
    },
    required: ['question'],
    additionalProperties: false,
  },
  async execute(args, ctx) {
    const { answer, question } = await ctx.askUser({
      question: args.question,
      options: args.options,
      required: args.required,
      context: args.context,
    });
    return ok({ answer, questionId: question.questionId }, '已收到人工答复');
  },
};
