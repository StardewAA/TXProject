import { message } from 'ant-design-vue';

import { chatWithRecruitAgent } from '#/api/hrm/portal/recruit-agent';

import {
  extractResumeContent,
  renderPdfPagesToImages,
  type ExtractedResumeContent,
} from './extract-text';

/**
 * 扫描件简历 → 正文：把 PDF 每页渲染成图片，交给多模态模型转写。
 *
 * <p>为什么需要这一层：`extractResumeContent` 只读 PDF 的文字层，
 * 而扫描件（手机拍的、打印后另存为 PDF 被栅格化的、传真件）整页就是一张图，
 * 一个字都读不出来 —— 而且它不报错，安安静静返回空串。用户看到的于是是
 * 「简历还没有解析出正文，暂时不能诊断」，却完全不知道问题出在文件本身。</p>
 *
 * <p>为什么不本地 OCR：项目原先用 tesseract.js，但它的 worker 脚本与
 * `chi_sim` 语言包默认从 cdn.jsdelivr.net 拉取，本内网环境该域名不可达
 * （unpkg 同样不通），HR 侧那个「识别文字」按钮在这里也是坏的。而多模态模型
 * 这条路是本环境现成可用的：`/hrm/portal/recruit-agent/chat` 本来就接受
 * 图片 data URL，模型对中文简历版式的识别质量也明显好过通用 OCR。</p>
 */

/**
 * 走模型识别时的页数上限。
 *
 * <p>按页调模型，页数直接等于用户等待时间（实测每页约 4 秒）。简历极少超过 3 页，
 * 封在 5 页是为了挡住「把一整本 PDF 当简历传上来」这种极端情况。</p>
 */
const MAX_SCAN_PAGES = 5;

/** 识别结果长度上限，与入库正文的口径保持一致 */
const MAX_SCAN_TEXT = 50_000;

/** 低于这个长度的回复视为没识别出东西，不当正文存 */
const MIN_MEANINGFUL_TEXT = 20;

const TRANSCRIBE_PROMPT = [
  '请把这张简历图片里的所有文字原样转写成纯文本。',
  '要求：保留原有的分节顺序与换行；不要总结、不要翻译、不要评论、不要补充说明；',
  '只输出转写出来的文本本身，不要加任何前缀和结尾语。',
].join('');

export interface ScanAwareExtraction extends ExtractedResumeContent {
  /** 正文是多模态模型识别出来的，而不是从文字层直接读到的 */
  transcribed: boolean;
}

/**
 * 上传简历时的统一提取入口：先读文字层，读不到再上模型识别。
 *
 * <p>调用方拿到的 `text` 要么是文字层里的原文，要么是模型转写的结果，
 * 不用自己判断走的哪条路；`transcribed` 只用于决定提示语怎么说。</p>
 *
 * <p>识别失败一律降级为空正文，不往上传流程外面抛 ——
 * 附件这时候已经存进存储了，不该因为识别不出文字把整次上传判死。</p>
 *
 * @param onError 提取阶段的异常回调（原样透传给 extractResumeContent）
 */
export async function extractResumeContentWithScanFallback(
  file: File,
  onError?: (error: unknown) => void,
): Promise<ScanAwareExtraction> {
  const extracted = await extractResumeContent(file, onError);
  if (extracted.text.trim()) {
    return { ...extracted, transcribed: false };
  }

  // 只有 PDF 能渲染成图。DOC/DOCX 走 mammoth，读不出来说明文件本身有问题，
  // 这里没有第二条路，交给调用方按原有文案提示。
  if (!file.name.toLowerCase().endsWith('.pdf')) {
    return { ...extracted, transcribed: false };
  }

  const text = await transcribeScannedPdf(file);
  return { ...extracted, text, transcribed: Boolean(text) };
}

/**
 * 逐页渲染并转写。任何一页失败都不影响其它页，最后把拿到的拼起来。
 *
 * <p>进度用带 key 的 loading 提示接管：扫描件识别要好几秒，
 * 没有反馈的话用户会以为上传卡死了。</p>
 */
async function transcribeScannedPdf(file: File): Promise<string> {
  const key = 'scan-transcribe';
  let images: string[] = [];
  try {
    images = await renderPdfPagesToImages(file, MAX_SCAN_PAGES);
  } catch (error) {
    console.warn('[ScanTranscribe] 渲染扫描件失败，放弃识别', error);
    return '';
  }
  if (images.length === 0) return '';

  message.loading({
    content: `未读到文字层，正在用 AI 识别扫描件（共 ${images.length} 页）…`,
    duration: 0,
    key,
  });

  const pages: string[] = [];
  try {
    for (let index = 0; index < images.length; index += 1) {
      if (index > 0) {
        message.loading({
          content: `正在识别扫描件…（${index + 1}/${images.length} 页）`,
          duration: 0,
          key,
        });
      }
      const pageText = await transcribePage(images[index]!);
      if (pageText) pages.push(pageText);
    }
  } catch (error) {
    // 中途失败：已经识别出来的页仍然有价值，照常返回
    console.warn('[ScanTranscribe] 调用模型识别扫描件失败', error);
  } finally {
    message.destroy(key);
  }

  return pages.join('\n').slice(0, MAX_SCAN_TEXT).trim();
}

async function transcribePage(imageDataUrl: string): Promise<string> {
  const resp = await chatWithRecruitAgent(TRANSCRIBE_PROMPT, imageDataUrl);
  const text = (resp?.reply || '').trim();
  return text.length >= MIN_MEANINGFUL_TEXT ? text : '';
}
