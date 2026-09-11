/**
 * CRM 合同「文件识别」工具
 *
 * 把上传的合同文件转换为可被大模型识别的内容：
 * - PDF（矢量）：优先提取文本层，速度快、识别准，无需渲染；
 * - PDF（扫描件 / 图片版）：文本层为空时，把页面渲染成图片，交给视觉大模型 OCR；
 * - 图片（jpg/png/webp/bmp/gif）：由调用方压缩后交给视觉大模型 OCR。
 *
 * 所有异常都会转换成面向用户的中文提示（通过 throw Error 抛出），
 * 由调用方 catch 后用 message 展示，保证「文件异常有明确提示」。
 */

/** 单个文件大小上限：20MB（超过后 PDF 解析极易失败、请求体也过大） */
export const MAX_FILE_SIZE = 20 * 1024 * 1024;
/** 一次最多识别的文件数 */
export const MAX_FILE_COUNT = 5;
/** PDF 最多识别页数（超出只取前 N 页，避免请求体过大） */
export const MAX_PDF_PAGES = 5;
/** 文本层去空白后字符数低于该值，视为扫描件（需要走 OCR 渲染） */
const TEXT_LAYER_MIN_LENGTH = 40;
/** 渲染页面图片的最长边（像素），控制 base64 体积 */
const RENDER_MAX_DIM = 1600;
/** 渲染页面图片的 jpeg 质量 */
const RENDER_QUALITY = 0.85;

export type ContractFileKind = 'image' | 'pdf';

/** 待识别的合同文件（仅用于识别，不作为附件保存） */
export interface ContractFileItem {
  uid: string;
  name: string;
  size: number;
  kind: ContractFileKind;
  file: File;
  /** 图片/PDF 首页预览地址（objectURL） */
  url?: string;
}

export interface FileCheckResult {
  ok: boolean;
  kind?: ContractFileKind;
  /** 校验失败时的用户提示 */
  message?: string;
}

const IMAGE_EXT_RE = /\.(?:bmp|gif|jpe?g|png|webp)$/i;

/** 人类可读的文件大小 */
export function formatFileSize(size: number): string {
  if (!size || size <= 0) {
    return '0 B';
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / 1024 / 1024).toFixed(2)} MB`;
}

/**
 * 校验单个文件是否可用于识别。
 * 依次拦截：空文件、超大文件、不支持的类型（word/excel/txt 等）。
 */
export function checkContractFile(file: File): FileCheckResult {
  const name = file.name || '未命名文件';
  const size = file.size ?? 0;
  if (size === 0) {
    return { message: `「${name}」是空文件，请重新选择`, ok: false };
  }
  if (size > MAX_FILE_SIZE) {
    return {
      message: `「${name}」大小为 ${formatFileSize(size)}，超过 ${MAX_FILE_SIZE / 1024 / 1024}MB 上限，请压缩后再上传`,
      ok: false,
    };
  }
  const lower = name.toLowerCase();
  const mime = (file.type || '').toLowerCase();
  if (mime === 'application/pdf' || lower.endsWith('.pdf')) {
    return { kind: 'pdf', ok: true };
  }
  if (mime.startsWith('image/') || IMAGE_EXT_RE.test(lower)) {
    return { kind: 'image', ok: true };
  }
  return {
    message: `「${name}」格式不支持，仅支持 PDF 与 JPG/PNG/WEBP/BMP 图片合同`,
    ok: false,
  };
}

/** 把 pdf.js 抛出的底层异常翻译成用户能看懂的中文 */
function describePdfError(e: any): string {
  const name = `${e?.name ?? ''}`;
  const msg = `${e?.message ?? e ?? ''}`;
  if (name === 'PasswordException' || /password/i.test(msg)) {
    return 'PDF 已加密（需要密码），请先解除密码后再上传';
  }
  if (
    name === 'InvalidPDFException' ||
    /invalid pdf|not a pdf|invalidpdf/i.test(msg)
  ) {
    return 'PDF 文件已损坏或内容不是有效的 PDF，请确认后重新上传';
  }
  return `PDF 解析失败：${msg || '未知错误'}`;
}

let pdfjsPromise: null | Promise<any> = null;

/** 懒加载 pdf.js（及 worker），避免影响首屏体积 */
function loadPdfjs(): Promise<any> {
  pdfjsPromise ??= (async () => {
    const lib: any = await import('pdfjs-dist');
    // 用 vite 的 ?url 让 worker 走本地打包资源（离线可用，无需 CDN）
    const worker: any = await import('pdfjs-dist/build/pdf.worker.min.mjs?url');
    lib.GlobalWorkerOptions.workerSrc = worker?.default ?? worker;
    return lib;
  })();
  return pdfjsPromise;
}

/** 合并冗余空白，避免把 PDF 文本层的换行噪声交给大模型 */
function normalizeText(raw: string): string {
  return raw
    .replaceAll('\u00A0', ' ')
    .replace(/[ \t]+/g, ' ')
    .replace(/\n{2,}/g, '\n')
    .trim();
}

/** 把 PDF 单页渲染成 jpeg dataURL */
async function renderPageToDataUrl(page: any): Promise<string> {
  const baseViewport = page.getViewport({ scale: 1 });
  const maxSide = Math.max(baseViewport.width, baseViewport.height) || 1;
  // 最长边控制在 RENDER_MAX_DIM 内，同时放大到最多 2 倍保证文字清晰
  const scale = Math.min(2, Math.max(1, RENDER_MAX_DIM / maxSide));
  const viewport = page.getViewport({ scale });
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1, Math.floor(viewport.width));
  canvas.height = Math.max(1, Math.floor(viewport.height));
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    throw new Error('当前浏览器不支持 Canvas，无法解析 PDF 页面');
  }
  // 先铺白底，避免透明背景在 OCR 时对比度不足
  ctx.fillStyle = '#FFFFFF';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  await page.render({ canvasContext: ctx, viewport }).promise;
  return canvas.toDataURL('image/jpeg', RENDER_QUALITY);
}

export interface PdfExtractResult {
  /** 文本层内容（扫描件为空） */
  text: string;
  /** 渲染出的页面图片 dataURL（矢量 PDF 为空） */
  images: string[];
  /** PDF 总页数 */
  pageCount: number;
  /** 实际参与识别的页数 */
  usedPages: number;
  /** 是否因为页数过多被截断 */
  truncated: boolean;
  /** text=矢量 PDF 走文本识别；scan=扫描件走 OCR */
  mode: 'scan' | 'text';
}

/**
 * 解析 PDF：优先取文本层，文本层为空则渲染页面图片走 OCR。
 * 失败时抛出带中文提示的 Error。
 */
export async function extractPdfContent(
  file: File,
  onProgress?: (message: string) => void,
): Promise<PdfExtractResult> {
  let pdfjs: any;
  try {
    pdfjs = await loadPdfjs();
  } catch (e: any) {
    throw new Error(
      `PDF 解析组件加载失败：${e?.message ?? '未知错误'}，请刷新页面重试`,
    );
  }

  let doc: any;
  const data = await file.arrayBuffer();
  if (!data || data.byteLength === 0) {
    throw new Error(`「${file.name || 'PDF'}」内容为空，请重新选择`);
  }
  try {
    doc = await pdfjs.getDocument({ data, isEvalSupported: false }).promise;
  } catch (e: any) {
    throw new Error(describePdfError(e));
  }

  try {
    const pageCount: number = doc.numPages || 0;
    if (pageCount === 0) {
      throw new Error('PDF 中没有任何页面，请确认文件是否完整');
    }
    const usedPages = Math.min(pageCount, MAX_PDF_PAGES);
    const truncated = pageCount > usedPages;

    // 第一步：抽取文本层
    let rawText = '';
    for (let i = 1; i <= usedPages; i++) {
      onProgress?.(`正在读取 PDF 第 ${i}/${usedPages} 页…`);
      const page = await doc.getPage(i);
      try {
        const textContent = await page.getTextContent();
        rawText += `${(textContent?.items ?? [])
          .map((it: any) => `${it?.str ?? ''}${it?.hasEOL ? '\n' : ''}`)
          .join('')}\n`;
      } catch {
        // 单页文本层读取失败不影响整体：后续会按扫描件渲染
      }
      page.cleanup?.();
    }

    const normalized = normalizeText(rawText);
    if (normalized.replace(/\s/g, '').length >= TEXT_LAYER_MIN_LENGTH) {
      return {
        images: [],
        mode: 'text',
        pageCount,
        text: normalized,
        truncated,
        usedPages,
      };
    }

    // 第二步：文本层为空（扫描件 / 图片版 PDF）→ 渲染页面图片
    const images: string[] = [];
    for (let i = 1; i <= usedPages; i++) {
      onProgress?.(`正在渲染 PDF 第 ${i}/${usedPages} 页用于 OCR…`);
      const page = await doc.getPage(i);
      try {
        images.push(await renderPageToDataUrl(page));
      } catch {
        // 忽略单页渲染失败，全部失败时下面统一报错
      }
      page.cleanup?.();
    }
    if (images.length === 0) {
      throw new Error('未能从 PDF 中提取到文字或图片，请确认文件是否为有效合同');
    }
    return { images, mode: 'scan', pageCount, text: '', truncated, usedPages };
  } finally {
    // 释放 pdf.js 占用的内存与 worker 资源
    await doc.destroy?.().catch?.(() => {});
  }
}
