import { message } from 'ant-design-vue';

import {
  buildStructureIdempotencyKey,
  getRecruitAgentTask,
  submitResumeStructure,
} from '#/api/hrm/portal/recruit-agent';

import {
  extractResumeContentWithScanFallback,
  type ScanAwareExtraction,
} from './scan-transcribe';

/**
 * 附件简历「入库前处理」：提取正文 → 整理成结构化字段。
 *
 * <p>为什么要有这一步：附件里提取出来的是一**整段纯文本**，而预览、AI 的逐字段建议、
 * 投递时的字段回填都只认结构化字段。纯文本塞进库的后果是 —— 预览只能把全文挂在
 * 「个人优势」标题下，AI 建议也定位不到具体是教育经历还是项目经历。</p>
 *
 * <p>所以这里再走一步模型：把正文**搬运**到字段里（只整理不重写，服务端有
 * 「事实必须能在素材里找到」的严格校验兜底）。失败就退回纯文本 ——
 * 那份记录仍然有用，不能因为整理不了把整次上传判死。</p>
 */

/** 结构化任务的进度提示 key。与 scan-transcribe 用同一个：两步是接力关系，不该叠两层提示 */
const INTAKE_MESSAGE_KEY = 'scan-transcribe';

/** 轮询间隔与上限：与工作台其它任务保持一致（最多等 60 秒） */
const POLL_INTERVAL_MS = 1000;
const POLL_MAX_ATTEMPTS = 60;

export interface ResumeIntakeResult extends ScanAwareExtraction {
  /** 入库用：结构化成功是草稿 JSON，否则退回纯文本（可能为空串） */
  content: string;
  /** 正文是否被整理成了结构化字段 */
  structured: boolean;
}

async function structureText(text: string): Promise<string> {
  const taskId = await submitResumeStructure({
    idempotencyKey: buildStructureIdempotencyKey(),
    text,
  });
  for (let attempt = 0; attempt < POLL_MAX_ATTEMPTS; attempt += 1) {
    const detail = await getRecruitAgentTask(taskId);
    if (detail.status === 'SUCCEEDED') {
      return detail.result ? JSON.stringify(detail.result) : '';
    }
    if (detail.status === 'FAILED') {
      console.warn('[ResumeIntake] 结构化任务失败', detail.errorMessage);
      return '';
    }
    await new Promise((resolve) => window.setTimeout(resolve, POLL_INTERVAL_MS));
  }
  console.warn('[ResumeIntake] 结构化任务超时');
  return '';
}

/**
 * @param onError 提取阶段的异常回调（透传给提取器）
 */
export async function intakeResumeFile(
  file: File,
  onError?: (error: unknown) => void,
): Promise<ResumeIntakeResult> {
  const extracted = await extractResumeContentWithScanFallback(file, onError);
  const text = (extracted.text || '').trim();
  if (!text) {
    return { ...extracted, content: '', structured: false };
  }

  /*
   * 已经是 JSON 的正文（系统自己「下载时另存」的附件走的是另一条路，
   * 这里多一道防御）不用再过模型：结构化对象再结构化一次只会丢信息。
   */
  if (text.startsWith('{')) {
    return { ...extracted, content: text, structured: true };
  }

  message.loading({
    content: '正在用 AI 整理简历结构…',
    duration: 0,
    key: INTAKE_MESSAGE_KEY,
  });
  try {
    const structured = await structureText(text);
    return {
      ...extracted,
      // 整理不出来就退回纯文本：那份记录仍然可预览、可诊断，只是不逐字段
      content: structured || text,
      structured: Boolean(structured),
    };
  } catch (error) {
    console.warn('[ResumeIntake] 结构化失败，退回纯文本入库', error);
    return { ...extracted, content: text, structured: false };
  } finally {
    message.destroy(INTAKE_MESSAGE_KEY);
  }
}
