import { fail, ok, type Tool } from './types.js';

/**
 * 工作区文件工具。相对易标的 read/write/edit/bash/find/ls 做了服务端收敛：
 * 目录限制在 workspace/{taskId} 内，且不提供 bash —— 服务端多租户场景下不做进程逃逸面。
 */
const CONTENT_LIMIT = 4000;

export const readFileTool: Tool<{ file: string }> = {
  name: 'read-file',
  description: '读取当前任务工作区内的文件（相对路径），常驻文件包括 项目事实卡.md / 招标文件解析.md / outline.json。',
  parameters: {
    type: 'object',
    properties: { file: { type: 'string', description: '相对工作区的文件路径' } },
    required: ['file'],
    additionalProperties: false,
  },
  async execute(args, ctx) {
    if (!(await ctx.workspace.exists(args.file))) {
      return fail({ ok: false, error: `文件不存在：${args.file}` }, '文件不存在');
    }
    const content = await ctx.workspace.readText(args.file);
    return ok(
      {
        file: args.file,
        content: content.length > CONTENT_LIMIT ? `${content.slice(0, CONTENT_LIMIT)}…` : content,
        truncated: content.length > CONTENT_LIMIT,
      },
      '读取成功',
    );
  },
};

export const writeFileTool: Tool<{ file: string; content: string }> = {
  name: 'write-file',
  description: '把内容写入当前任务工作区（相对路径）。章节正文写 章节草稿/{nodeId}.md，临时结构写 .json。',
  parameters: {
    type: 'object',
    properties: {
      file: { type: 'string', description: '相对工作区的文件路径' },
      content: { type: 'string', description: '文件内容' },
    },
    required: ['file', 'content'],
    additionalProperties: false,
  },
  async execute(args, ctx) {
    await ctx.workspace.writeText(args.file, args.content);
    return ok({ ok: true, file: args.file, bytes: Buffer.byteLength(args.content, 'utf8') }, '写入成功');
  },
};

export const listFilesTool: Tool<Record<string, never>> = {
  name: 'list-files',
  description: '列出当前任务工作区内已产生的文件，用于确认既有产出，避免重复检索与重复生成。',
  parameters: { type: 'object', properties: {}, additionalProperties: false },
  async execute(_args, ctx) {
    return ok({ files: await ctx.workspace.list() }, '已列出工作区文件');
  },
};
