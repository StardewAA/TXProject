/**
 * 对话附件：把用户上传的简历/图片变成能发给模型的东西。
 *
 * <p>两条路不一样，因为模型的输入方式不一样：</p>
 * <ul>
 *   <li><b>简历（PDF/DOCX）</b>：先在前端提取成纯文本，拼进消息正文一起发 ——
 *       这几个格式的文字层本来就是文本，提取比让模型读图更准也更便宜。</li>
 *   <li><b>图片</b>：直接压一下尺寸当 data URL 发过去，让模型自己看。
 *       模型是多模态的（当前配的 deepseek-flash 能读图，实测能辨认颜色和版式），
 *       而 OCR 会把版式、手写、印章这些信息全丢掉 —— 一开始我按「模型读不了图」
 *       写了 OCR，那是错的。</li>
 * </ul>
 */

/** 图片带 data URL，简历带提取出的正文 —— 两条路给模型的东西根本不同 */
export type ChatAttachment =
  | { dataUrl: string; kind: 'image'; name: string }
  | { file?: File; kind: 'resume'; name: string; text: string };

/**
 * 把「这一轮要说的话 + 附件」拼成真正发给模型的那段文本。
 *
 * 附件是随消息一起发的：这个接口一问一答、不带会话上下文，
 * 上一轮发过什么都不作数，每轮都得把要用的材料重新带上。
 */
export function composeOutgoingMessage(
  prompt: string,
  attachment?: ChatAttachment,
) {
  if (attachment?.kind !== 'resume') {
    return prompt || '请帮我看看这张图';
  }
  return `【附件：${attachment.name}（已提取正文）】
${attachment.text}

【我的问题】
${prompt || '请帮我看看这份简历'}`;
}

/** 简历正文超过这个长度就截断 —— 再长也不会改变模型的判断，只是白烧 token */
export const MAX_ATTACH_TEXT = 6000;

/** 附件大小上限，和上传附件简历那条保持一致 */
export const MAX_ATTACH_SIZE = 10 * 1024 * 1024;

/**
 * 图片长边上限。
 *
 * <p>原图动辄几 MB，base64 之后还要再涨三分之一；长边 1600px 对简历照片、
 * 手机截图这类内容已经够模型看清了。</p>
 */
const MAX_IMAGE_SIDE = 1600;

const DOCUMENT_EXTENSIONS = new Set(['doc', 'docx', 'pdf']);

export function extensionOf(file: File) {
  return file.name.split('.').pop()?.toLowerCase() || '';
}

export function isImageFile(file: File) {
  return file.type.startsWith('image/');
}

export function isDocumentFile(file: File) {
  return DOCUMENT_EXTENSIONS.has(extensionOf(file));
}

/**
 * 图片 → data URL（长边压到 1600px 以内）。
 *
 * <p>压不动就退回原图：宁可发大一点，也不要因为一次压缩失败让上传整个失败。
 * 但那种情况下要提醒调用方 —— 原图可能超出后端的体积上限。</p>
 */
export async function imageToDataUrl(file: File): Promise<string> {
  try {
    const bitmap = await createImageBitmap(file);
    const longSide = Math.max(bitmap.width, bitmap.height);
    const scale = Math.min(1, MAX_IMAGE_SIDE / longSide);
    if (scale >= 1 && file.size <= 1024 * 1024) {
      // 本来就不大，不折腾
      return await fileToDataUrl(file);
    }
    const width = Math.round(bitmap.width * scale);
    const height = Math.round(bitmap.height * scale);
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    if (!ctx) return await fileToDataUrl(file);
    ctx.drawImage(bitmap, 0, 0, width, height);
    return canvas.toDataURL('image/jpeg', 0.82);
  } catch {
    return await fileToDataUrl(file);
  }
}

function fileToDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || ''));
    reader.onerror = () => reject(new Error('文件读取失败'));
    reader.readAsDataURL(file);
  });
}

let pdfWorkerReady: null | Promise<void> = null;

/**
 * 给 pdf.js 指定 worker 路径。
 *
 * <p>pdfjs 要求调用方自己设，否则 getDocument 直接抛
 * 「No "GlobalWorkerOptions.workerSrc" specified」。门户那份提取器没设过 ——
 * 它一直能跑，是因为 CRM 合同解析（utils/crm/contract-file.ts）先加载过、
 * 顺手把全局设上了；求职助理这页不经过 CRM，所以一上传 PDF 就报错。</p>
 *
 * <p>不能指望「别人先跑过」，这里自己设一次。两个模块 import 的是同一个
 * pdfjs-dist 实例，所以设在这里对提取器同样生效。用 Vite 的 ?url 让 worker
 * 走本地打包资源，不依赖 CDN。</p>
 */
function ensurePdfWorker(): Promise<void> {
  pdfWorkerReady ??= (async () => {
    const lib: any = await import('pdfjs-dist');
    if (lib.GlobalWorkerOptions?.workerSrc) return;
    const worker: any = await import('pdfjs-dist/build/pdf.worker.min.mjs?url');
    lib.GlobalWorkerOptions.workerSrc = worker?.default ?? worker;
  })();
  return pdfWorkerReady;
}

/**
 * 简历文件（PDF / DOC / DOCX）→ 文字。
 *
 * <p>复用门户那份提取器：它已经处理了 PDF 分页、DOCX 走 mammoth，
 * 以及扫描件 PDF（没有文字层时把页面渲染成图再交给 tesseract）。</p>
 */
export async function documentToText(file: File): Promise<string> {
  // 只有 PDF 用得上 pdf.js；DOC/DOCX 走 mammoth，不必为它多下 worker
  if (extensionOf(file) === 'pdf') {
    await ensurePdfWorker();
  }
  const { extractResumeContent } = await import(
    '#/views/portal/recruit/components/extract-text'
  );
  let failure: unknown;
  const result = await extractResumeContent(file, (error) => {
    failure = error;
  });
  const text = (result.text || '').trim();
  if (!text && failure) {
    // 提取器把异常吞进控制台了，这里把它抛出去 ——
    // 界面只显示「没提取到文字」时，分不清是「扫描件没有文字层」还是「解析器炸了」
    throw new Error(failure instanceof Error ? failure.message : String(failure));
  }
  return text.slice(0, MAX_ATTACH_TEXT);
}
