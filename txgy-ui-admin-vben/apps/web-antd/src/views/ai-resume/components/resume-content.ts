/**
 * 简历 content 的解析与归一化。
 *
 * `hrm_personal_resume.content` 有两种形态，取决于这份简历怎么来的：
 * - 结构化 JSON：在线求职资料、附件简历制作生成的那几份；
 * - 附件抽取出的纯文本：「个人简历 一、基本信息 姓名：张三…」这样的整段文字。
 *
 * 工作区（diagnose.vue）和列表页的预览弹窗都要做同一件事，
 * 放在这里避免两处对「非结构化怎么办」给出不同答案。
 */

export interface ParsedResumeContent {
  /** 编辑与渲染用的内容；非结构化时把正文放进 summary */
  draft: Record<string, any>;
  /** 原始内容字符串，非结构化简历保存版本时要原样带回 */
  raw: string;
  /** 是否是结构化 JSON */
  structured: boolean;
}

function tryParseJson(value: string): Record<string, any> | undefined {
  const text = value.trim();
  // 只对花括号开头的做尝试：纯文本里偶尔也有 `{`，交给 JSON.parse 抛错不如先筛一道
  if (!text.startsWith('{')) return undefined;
  try {
    const parsed = JSON.parse(text);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed
      : undefined;
  } catch {
    return undefined;
  }
}

/**
 * @param content 简历的 content 字段，可能为空
 * @param fallbackName 非结构化时的标题，通常用文件名
 */
export function parseResumeContent(
  content: string | undefined,
  fallbackName = '',
): ParsedResumeContent {
  const raw = content || '';
  const parsed = tryParseJson(raw);

  if (parsed) {
    return { draft: parsed, raw, structured: true };
  }

  // 非结构化：只把正文塞进个人简介，不硬拆字段——猜错的字段名会渲染成一片空白，
  // 而原文整段留着，预览里至少能看到完整内容。
  return {
    draft: { name: fallbackName, summary: raw },
    raw,
    structured: false,
  };
}
