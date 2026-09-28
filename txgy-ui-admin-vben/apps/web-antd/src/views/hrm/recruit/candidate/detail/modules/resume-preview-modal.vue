<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Alert, Button, message, Progress, Spin } from 'ant-design-vue';

import { saveRecruitResumeText } from '#/api/hrm/recruit/resume';

/**
 * 简历预览弹窗（问题 6：点「预览」应先在页面里看到内容，而不是直接下载）
 *
 * <p>此前「预览」只是把授权地址丢给浏览器打开；文件服务返回附件（attachment）时浏览器会直接下载。
 * 这里改为**站内渲染**：</p>
 * <ul>
 *   <li>PDF：pdf.js 逐页渲染到画布（离线可用，worker 走本地打包资源）</li>
 *   <li>DOCX：mammoth 转 HTML 预览</li>
 *   <li>图片：直接显示</li>
 *   <li>其它/失败：给出提示 + 「在新窗口打开 / 下载」兜底</li>
 * </ul>
 */
defineOptions({ name: 'HrmResumePreviewModal' });

const emit = defineEmits<{ saved: [] }>();

const [Modal, modalApi] = useVbenModal({
  footer: false,
  title: '简历预览',
});

const loading = ref(false);
const errorMessage = ref('');
const fileName = ref('');
const fileUrl = ref('');
const kind = ref<'docx' | 'image' | 'other' | 'pdf'>('other');
/** PDF 渲染出的页面 dataURL 列表 */
const pdfPages = ref<string[]>([]);
const docxHtml = ref('');
const resumeId = ref<number>();
const ocrRunning = ref(false);
const ocrProgress = ref(0);
const ocrSaved = ref(false);

let pdfjsPromise: null | Promise<any> = null;

function loadPdfjs(): Promise<any> {
  pdfjsPromise ??= (async () => {
    const lib: any = await import('pdfjs-dist');
    // vue 用 vite 的 ?url 让 worker 走本地打包资源（离线可用，无需 CDN）
    const worker: any = await import('pdfjs-dist/build/pdf.worker.min.mjs?url');
    lib.GlobalWorkerOptions.workerSrc = worker?.default ?? worker;
    return lib;
  })();
  return pdfjsPromise;
}

function resolveKind(name: string) {
  const lower = name.toLowerCase();
  if (lower.endsWith('.pdf')) return 'pdf' as const;
  if (lower.endsWith('.docx')) return 'docx' as const;
  if (/\.(jpe?g|png|webp|gif|bmp)$/.test(lower)) return 'image' as const;
  return 'other' as const;
}

async function load(meta: { fileName?: string; fileUrl?: string }) {
  resumeId.value = meta?.resumeId;
  ocrSaved.value = false;
  ocrProgress.value = 0;
  fileUrl.value = meta?.fileUrl ?? '';
  fileName.value = meta?.fileName ?? '简历';
  kind.value = resolveKind(fileName.value);
  errorMessage.value = '';
  pdfPages.value = [];
  docxHtml.value = '';
  if (!fileUrl.value) {
    errorMessage.value = '简历文件地址无效，请重新上传后再试。';
    return;
  }
  loading.value = true;
  try {
    if (kind.value === 'pdf') {
      await renderPdf();
    } else if (kind.value === 'docx') {
      await renderDocx();
    }
  } catch (error: any) {
    errorMessage.value = error?.message || '预览失败，可尝试在新窗口打开或下载后查看。';
  } finally {
    loading.value = false;
  }
}

async function fetchArrayBuffer() {
  const response = await fetch(fileUrl.value, {
    credentials: 'include',
  });
  if (!response.ok) {
    throw new Error(`文件读取失败（HTTP ${response.status}）`);
  }
  return await response.arrayBuffer();
}

async function renderPdf() {
  const pdfjs = await loadPdfjs();
  const data = await fetchArrayBuffer();
  const document = await pdfjs.getDocument({ data, isEvalSupported: false }).promise;
  const pages: string[] = [];
  // 预览最多渲染 20 页，避免超大简历卡顿
  const pageCount = Math.min(document.numPages, 20);
  for (let pageNo = 1; pageNo <= pageCount; pageNo++) {
    const page = await document.getPage(pageNo);
    const baseViewport = page.getViewport({ scale: 1 });
    const scale = Math.min(2, Math.max(1, 1400 / (baseViewport.width || 1)));
    const viewport = page.getViewport({ scale });
    const canvas = globalThis.document.createElement('canvas');
    canvas.width = Math.max(1, Math.floor(viewport.width));
    canvas.height = Math.max(1, Math.floor(viewport.height));
    const context = canvas.getContext('2d');
    if (!context) {
      throw new Error('当前浏览器不支持 Canvas');
    }
    context.fillStyle = '#fff';
    context.fillRect(0, 0, canvas.width, canvas.height);
    await page.render({ canvasContext: context, viewport }).promise;
    pages.push(canvas.toDataURL('image/jpeg', 0.9));
  }
  pdfPages.value = pages;
}

async function renderDocx() {
  const mammoth: any = await import('mammoth/mammoth.browser');
  const data = await fetchArrayBuffer();
  const result = await (mammoth.default ?? mammoth).convertToHtml({ arrayBuffer: data });
  docxHtml.value = result.value;
}

/**
 * 扫描件/图片简历 → 本地 OCR（tesseract.js）识别文字并保存。
 *
 * <p>扫描件 PDF 没有文字层，服务端解析不出内容；HR 在这里点一次「识别文字」，
 * 识别结果保存到简历后，入职建档即可按规则自动补全档案字段（不需要 AI）。</p>
 */
async function handleOcrAndSave() {
  if (!resumeId.value) {
    message.warning('该简历没有可保存的编号（历史附件请重新上传后再识别）');
    return;
  }
  ocrRunning.value = true;
  ocrProgress.value = 0;
  try {
    const text = kind.value === 'pdf' ? await ocrPdf() : await ocrImage();
    if (!text.trim()) {
      message.warning('未识别到文字，请确认简历是否清晰可读');
      return;
    }
    await saveRecruitResumeText(resumeId.value, text, 'ocr');
    ocrSaved.value = true;
    emit('saved');
    message.success('识别完成并已保存，入职建档时会自动补全档案字段');
  } catch (error: any) {
    message.error(error?.message || '识别失败，请稍后重试');
  } finally {
    ocrRunning.value = false;
  }
}

/** 把页面画布交给 tesseract 识别（中英文） */
async function ocrCanvas(canvas: HTMLCanvasElement) {
  const blob: Blob = await new Promise((resolve, reject) => {
    canvas.toBlob((value) => (value ? resolve(value) : reject(new Error('画布导出失败'))), 'image/png');
  });
  const tesseract: any = await import('tesseract.js');
  const result = await tesseract.recognize(blob, 'chi_sim+eng', {
    logger: (m: any) => {
      if (m.status === 'recognizing text') {
        ocrProgress.value = Math.round((m.progress || 0) * 100);
      }
    },
  });
  return result?.data?.text ?? '';
}

/** PDF：逐页渲染后 OCR（最多 5 页，控制耗时） */
async function ocrPdf() {
  const pdfjs = await loadPdfjs();
  const data = await fetchArrayBuffer();
  const document = await pdfjs.getDocument({ data, isEvalSupported: false }).promise;
  const pageCount = Math.min(document.numPages, 5);
  const texts: string[] = [];
  for (let pageNo = 1; pageNo <= pageCount; pageNo++) {
    const page = await document.getPage(pageNo);
    const baseViewport = page.getViewport({ scale: 1 });
    // OCR 对分辨率敏感：长边放大到约 1600px
    const scale = Math.min(3, Math.max(1, 1600 / (baseViewport.width || 1)));
    const viewport = page.getViewport({ scale });
    const canvas = globalThis.document.createElement('canvas');
    canvas.width = Math.max(1, Math.floor(viewport.width));
    canvas.height = Math.max(1, Math.floor(viewport.height));
    const context = canvas.getContext('2d');
    if (!context) {
      throw new Error('当前浏览器不支持 Canvas');
    }
    context.fillStyle = '#fff';
    context.fillRect(0, 0, canvas.width, canvas.height);
    await page.render({ canvasContext: context, viewport }).promise;
    texts.push(await ocrCanvas(canvas));
  }
  return texts.join('\n');
}

/** 图片：直接 OCR */
async function ocrImage() {
  const data = await fetchArrayBuffer();
  const blob = new Blob([data]);
  const bitmap = await createImageBitmap(blob);
  const longSide = Math.max(bitmap.width, bitmap.height) || 1;
  const scale = Math.min(3, Math.max(1, 1600 / longSide));
  const canvas = globalThis.document.createElement('canvas');
  canvas.width = Math.max(1, Math.round(bitmap.width * scale));
  canvas.height = Math.max(1, Math.round(bitmap.height * scale));
  const context = canvas.getContext('2d');
  if (!context) {
    throw new Error('当前浏览器不支持 Canvas');
  }
  context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  return await ocrCanvas(canvas);
}

function openInNewWindow() {
  if (fileUrl.value) {
    globalThis.open(fileUrl.value, '_blank');
  }
}

const hasPreview = computed(
  () =>
    pdfPages.value.length > 0 ||
    docxHtml.value.length > 0 ||
    kind.value === 'image',
);

defineExpose({
  open(meta: { fileName?: string; fileUrl?: string; resumeId?: number }) {
    // 每次打开都重新渲染，避免看到上一个简历的内容
    modalApi.setData(meta).open();
    load(meta);
  },
});
</script>

<template>
  <Modal class="w-[1000px]" :title="`简历预览 · ${fileName || ''}`">
    <Spin :spinning="loading">
      <Alert
        v-if="errorMessage"
        class="mb-3"
        :message="errorMessage"
        show-icon
        type="warning"
      >
        <template #description>
          <div class="mt-1">
            <Button size="small" type="link" @click="openInNewWindow">
              在新窗口打开
            </Button>
          </div>
        </template>
      </Alert>

      <!-- 扫描件/图片简历：本地 OCR 识别文字并保存（不用 AI，也不需要额外服务） -->
      <div
        v-if="kind === 'pdf' || kind === 'image'"
        class="mb-3 flex flex-wrap items-center gap-3 rounded border p-3"
      >
        <Button
          :disabled="ocrRunning || !resumeId"
          :loading="ocrRunning"
          type="primary"
          @click="handleOcrAndSave"
        >
          识别文字（OCR）并保存
        </Button>
        <span class="text-muted-foreground text-xs">
          扫描件没有文字层时点这里：识别结果会存到简历，入职建档时自动补全档案字段（不调用 AI）
        </span>
        <Progress
          v-if="ocrRunning"
          class="w-[240px]"
          :percent="ocrProgress"
          size="small"
        />
        <span v-if="ocrSaved" class="text-green-600 text-xs">已保存识别文本</span>
      </div>

      <div
        v-if="kind === 'pdf' && pdfPages.length"
        class="max-h-[70vh] overflow-auto bg-gray-100 p-3 dark:bg-gray-800"
      >
        <img
          v-for="(page, index) in pdfPages"
          :key="index"
          :src="page"
          alt="简历页面"
          class="mx-auto mb-3 w-full max-w-[900px] rounded shadow"
        />
      </div>

      <div
        v-else-if="kind === 'docx' && docxHtml"
        class="docx-preview-box max-h-[70vh] overflow-auto rounded border p-4"
        v-html="docxHtml"
      ></div>

      <div v-else-if="kind === 'image'" class="text-center">
        <img :src="fileUrl" alt="简历图片" class="mx-auto max-h-[70vh]" />
      </div>

      <Alert
        v-else-if="!loading && !hasPreview && !errorMessage"
        message="该格式暂不支持在线预览（如 .doc），请下载后查看"
        show-icon
        type="info"
      >
        <template #description>
          <Button class="mt-1" size="small" type="link" @click="openInNewWindow">
            在新窗口打开
          </Button>
        </template>
      </Alert>
    </Spin>
  </Modal>
</template>

<style scoped>
.docx-preview-box :deep(img) {
  max-width: 100%;
}

.docx-preview-box :deep(table) {
  border-collapse: collapse;
  width: 100%;
}

.docx-preview-box :deep(td),
.docx-preview-box :deep(th) {
  border: 1px solid hsl(var(--border));
  padding: 4px 8px;
}
</style>
