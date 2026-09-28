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
  const pdfjsModule = await import('pdfjs-dist');
  const pdfjs = moduleValue(pdfjsModule as any) as any;
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
