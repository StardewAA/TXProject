import {
  extractJsonText,
  validateAgainstSchema,
  formatIssues,
  type JsonSchema,
  type ValidationIssue,
} from '../lib/jsonSchema.js';
import { AppError } from '../errors.js';
import { fail, ok, type Tool } from './types.js';

export interface JsonValidationArgs {
  /** 待校验的原始文本（LLM 输出），与 value 二选一 */
  jsonText?: string;
  /** 已解析的对象，与 jsonText 二选一 */
  value?: unknown;
  schema: JsonSchema;
}

/** 结构化输出守门员（复用易标 piJsonValidationTool 的职责） */
export const jsonValidationTool: Tool<JsonValidationArgs> = {
  name: 'json-validation',
  description: '按 JSON Schema 校验一段 JSON 文本或对象，返回是否符合以及具体的错误位置。最终回答前必须先自检。',
  parameters: {
    type: 'object',
    properties: {
      jsonText: { type: 'string', description: '待校验的 JSON 文本' },
      value: { description: '已解析好的 JSON 对象' },
      schema: {
        type: 'object',
        description: 'JSON Schema（支持 type/required/properties/items/enum 等子集）',
      },
    },
    required: ['schema'],
    additionalProperties: false,
  },
  async execute(args, ctx) {
    let target: unknown;
    if (args.jsonText !== undefined) {
      const text = extractJsonText(args.jsonText);
      if (!text) return fail({ ok: false, error: '未能在文本中找到 JSON 内容' }, '抽取 JSON 失败');
      try {
        target = JSON.parse(text);
      } catch (error) {
        return fail({ ok: false, error: `JSON 解析失败：${(error as Error).message}` }, 'JSON 解析失败');
      }
    } else if (args.value !== undefined) {
      target = args.value;
    } else {
      throw new AppError('json-validation 需要 jsonText 或 value', {
        category: 'validation',
        status: 400,
        retryable: false,
      });
    }

    const issues: ValidationIssue[] = validateAgainstSchema(args.schema, target);
    if (issues.length > 0) {
      ctx.logger.debug('JSON 校验失败', { issues: issues.length });
      return fail({ ok: false, issues, message: formatIssues(issues) }, '校验失败');
    }
    return ok({ ok: true, value: target }, '校验通过');
  },
};
