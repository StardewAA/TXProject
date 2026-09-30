import type { PDFDocumentProxy, PDFPageProxy } from 'pdfjs-dist';

export interface ExtractedResumeContent {
  text: string;
  avatar?: Blob;
}

const RESUME_EXTENSIONS = new Set(['pdf', 'doc', 'docx']);

function extensionOf(file: File) {
  return file.name.split('.').pop()?.toLowerCase() || '';
}

function moduleValue<T>(module: T & { default?: T }) {
  return module.default ?? module;
}

async function extractDocxText(file: File) {
  const mammothModule = await import('mammoth');
  const mammoth = moduleValue(mammothModule as any) as typeof mammothModule;
  const result = await mammoth.extractRawText({ arrayBuffer: await file.arrayBuffer() });
  return result.value.trim();
}

async function imageBlobFromDataUrl(dataUrl: string) {
  const response = await fetch(dataUrl);
  return response.blob();
}

async function extractDocxAvatar(file: File) {
  const mammothModule = await import('mammoth');
  const mammoth = moduleValue(mammothModule as any) as typeof mammothModule;
  const images: Blob[] = [];
  await mammoth.convertToHtml(
    { arrayBuffer: await file.arrayBuffer() },
    {
      convertImage: mammoth.images.imgElement((image: any) =>
        image.read('base64').then(async (base64: string) => {
          if (images.length >= 3) return { src: '' };
          const contentType = image.contentType || 'image/jpeg';
          const blob = await imageBlobFromDataUrl(`data:${contentType};base64,${base64}`);
          const bitmap = await createImageBitmap(blob);
          const squareRatio = bitmap.width / Math.max(1, bitmap.height);
          if (bitmap.width >= 80 && bitmap.height >= 80 && squareRatio >= 0.7 && squareRatio <= 1.45) {
            images.push(blob);
          }
          bitmap.close();
          return { src: '' };
        }),
      ),
    },
  );
  return images[0];
}

let pdfWorkerReady: null | Promise<void> = null;

/**
 * 给 pdf.js 指定 worker 路径。**所有走 pdf.js 的入口都必须先经过这里。**
 *
 * <p>pdfjs 要求调用方自己设，否则 getDocument 直接抛
 * 「No "GlobalWorkerOptions.workerSrc" specified」。</p>
 *
 * <p>原先这段逻辑只长在 ai-resume 的 chat-attach.ts 里，本模块是裸调 getDocument 的 ——
 * 之所以「看着能跑」，是因为 CRM 合同解析先加载过、把全局设置顺手带上了。
 * 结果就是同一个提取器在不同页面表现不一：详情页（CRM 先跑）正常，
 * 门户的上传入口（不经过 CRM）只要刷新后直接传 PDF 就必然失败，
 * 而失败被下面的 catch 吞成一句 console.warn，界面上只显示「未提取到正文」。
 * 现在由本模块自己保证 worker 就绪，不再依赖「别人先跑过」。</p>
 */
export function ensurePdfWorker(): Promise<void> {
  pdfWorkerReady ??= (async () => {
    const lib: any = await import('pdfjs-dist');
    if (lib.GlobalWorkerOptions?.workerSrc) return;
    // 用 Vite 的 ?url 让 worker 走本地打包资源，不依赖 CDN
    const worker: any = await import('pdfjs-dist/build/pdf.worker.min.mjs?url');
    lib.GlobalWorkerOptions.workerSrc = worker?.default ?? worker;
  })();
  return pdfWorkerReady;
}

/** 载入 pdf.js 并确保 worker 已就绪。直接用 import('pdfjs-dist') 会踩上面的坑。 */
export async function loadPdfjs(): Promise<any> {
  await ensurePdfWorker();
  const pdfjsModule = await import('pdfjs-dist');
  return moduleValue(pdfjsModule as any) as any;
}

async function renderFirstPdfPage(page: PDFPageProxy) {
  const viewport = page.getViewport({ scale: 1.5 });
  const canvas = document.createElement('canvas');
  canvas.width = Math.ceil(viewport.width);
  canvas.height = Math.ceil(viewport.height);
  const context = canvas.getContext('2d');
  if (!context) return undefined;
  await page.render({ canvasContext: context, viewport }).promise;
  return { canvas, viewport };
}

async function extractPdfAvatar(page: PDFPageProxy, pdfjs: any) {
  const rendered = await renderFirstPdfPage(page);
  if (!rendered) return undefined;
  const { canvas, viewport } = rendered;
  const operatorList = await page.getOperatorList();
  const OPS = pdfjs.OPS || {};
  type Matrix = [number, number, number, number, number, number];
  let matrix: Matrix = [1, 0, 0, 1, 0, 0];
  const candidates: Array<{ x: number; y: number; w: number; h: number; score: number }> = [];
  const multiply = (left: Matrix, right: number[]): Matrix => {
    const l0 = left[0] ?? 0; const l1 = left[1] ?? 0; const l2 = left[2] ?? 0;
    const l3 = left[3] ?? 0; const l4 = left[4] ?? 0; const l5 = left[5] ?? 0;
    const r0 = right[0] ?? 0; const r1 = right[1] ?? 0; const r2 = right[2] ?? 0;
    const r3 = right[3] ?? 0; const r4 = right[4] ?? 0; const r5 = right[5] ?? 0;
    return [
      l0 * r0 + l2 * r1,
      l1 * r0 + l3 * r1,
      l0 * r2 + l2 * r3,
      l1 * r2 + l3 * r3,
      l0 * r4 + l2 * r5 + l4,
      l1 * r4 + l3 * r5 + l5,
    ];
  };
  for (let index = 0; index < operatorList.fnArray.length; index += 1) {
    const fn = operatorList.fnArray[index];
    const args = operatorList.argsArray[index] as any[] | undefined;
    if (fn === OPS.transform && args?.length === 6) {
      matrix = multiply(matrix, args);
      continue;
    }
    if (fn !== OPS.paintImageXObject && fn !== OPS.paintJpegXObject) continue;
    const w = Math.abs(matrix[0] ?? 0);
    const h = Math.abs(matrix[3] ?? 0);
    const x = matrix[4] ?? 0;
    const y = matrix[5] ?? 0;
    const widthRatio = w / viewport.width;
    const heightRatio = h / viewport.height;
    const areaRatio = widthRatio * heightRatio;
    if (widthRatio < 0.08 || widthRatio > 0.3 || heightRatio < 0.05 || heightRatio > 0.6 || areaRatio > 0.06) continue;
    if (widthRatio / Math.max(heightRatio, 0.01) < 0.7 || widthRatio / Math.max(heightRatio, 0.01) > 1.45) continue;
    if (x > viewport.width * 0.7 || y > viewport.height * 0.35) continue;
    const score = Math.abs(widthRatio - 0.16) * 10 + x / viewport.width + y / viewport.height;
    candidates.push({ x, y, w, h, score });
  }
  candidates.sort((a, b) => a.score - b.score);
  const candidate = candidates[0];
  if (!candidate) return undefined;
  const output = document.createElement('canvas');
  output.width = Math.max(1, Math.round(candidate.w));
  output.height = Math.max(1, Math.round(candidate.h));
  const outputContext = output.getContext('2d');
  if (!outputContext) return undefined;
  outputContext.drawImage(canvas, candidate.x, candidate.y, candidate.w, candidate.h, 0, 0, output.width, output.height);
  return new Promise<Blob | undefined>((resolve) => output.toBlob((blob) => resolve(blob || undefined), 'image/jpeg', 0.9));
}

async function extractPdf(file: File): Promise<ExtractedResumeContent> {
  const pdfjs = await loadPdfjs();
  const pdf: PDFDocumentProxy = await pdfjs.getDocument({ data: new Uint8Array(await file.arrayBuffer()) }).promise;
  const pages: string[] = [];
  let avatar: Blob | undefined;
  for (let pageNo = 1; pageNo <= pdf.numPages; pageNo += 1) {
    const page = await pdf.getPage(pageNo);
    const text = await page.getTextContent();
    pages.push(text.items.map((item: any) => item.str || '').join(' '));
    if (pageNo === 1) avatar = await extractPdfAvatar(page, pdfjs);
  }
  return { text: pages.join('\n').trim(), avatar };
}

/** 扫描件渲染参数：长边约 1000px、JPEG 0.82，实测单页约 170KB，模型读得准又不至于撑爆请求体 */
const SCAN_RENDER_WIDTH = 1000;
const SCAN_JPEG_QUALITY = 0.82;

/**
 * 把 PDF 逐页渲染成 JPEG data URL。
 *
 * <p>给「没有文字层」的扫描件用：这类文件 pdf.js 一个字也读不出来，
 * 但页面本身是有内容的 —— 渲染成图交给多模态模型，就能把正文捞回来。</p>
 *
 * @param maxPages 最多渲染几页。扫描件按页调模型，页数就是耗时，必须封顶。
 */
export async function renderPdfPagesToImages(
  file: File,
  maxPages = 5,
  onPage?: (pageNo: number, total: number) => void,
): Promise<string[]> {
  const pdfjs = await loadPdfjs();
  const pdf: PDFDocumentProxy = await pdfjs.getDocument({
    data: new Uint8Array(await file.arrayBuffer()),
  }).promise;
  try {
    const total = Math.min(pdf.numPages, maxPages);
    const images: string[] = [];
    for (let pageNo = 1; pageNo <= total; pageNo += 1) {
      onPage?.(pageNo, total);
      const page = await pdf.getPage(pageNo);
      const base = page.getViewport({ scale: 1 });
      const scale = Math.min(3, Math.max(1, SCAN_RENDER_WIDTH / (base.width || 1)));
      const viewport = page.getViewport({ scale });
      const canvas = document.createElement('canvas');
      canvas.width = Math.max(1, Math.floor(viewport.width));
      canvas.height = Math.max(1, Math.floor(viewport.height));
      const context = canvas.getContext('2d');
      if (!context) {
        throw new Error('当前浏览器不支持 Canvas，无法识别扫描件');
      }
      // 铺白底：扫描件本身可能是透明底，直接导出会变黑
      context.fillStyle = '#fff';
      context.fillRect(0, 0, canvas.width, canvas.height);
      await page.render({ canvasContext: context, viewport }).promise;
      images.push(canvas.toDataURL('image/jpeg', SCAN_JPEG_QUALITY));
    }
    return images;
  } finally {
    // 渲染中途出错也要释放，否则 pdf.js 的 worker 会一直挂着
    await pdf.destroy().catch(() => {});
  }
}

/**
 * @param onError 可选。异常默认只进控制台，调用方要把失败原因显示给用户时传这个回调。
 *                门户那边的调用不传，行为与之前完全一致。
 */
export async function extractResumeContent(
  file: File,
  onError?: (error: unknown) => void,
): Promise<ExtractedResumeContent> {
  const extension = extensionOf(file);
  if (!RESUME_EXTENSIONS.has(extension)) return { text: '' };
  try {
    if (extension === 'pdf') return await extractPdf(file);
    if (extension === 'docx') {
      const [text, avatar] = await Promise.all([
        extractDocxText(file),
        extractDocxAvatar(file).catch(() => undefined),
      ]);
      return { text, avatar };
    }
  } catch (error) {
    console.warn('[ResumeExtract] 提取简历内容失败', error);
    onError?.(error);
  }
  return { text: '' };
}

export async function extractResumeAvatar(file: File) {
  return (await extractResumeContent(file)).avatar;
}
