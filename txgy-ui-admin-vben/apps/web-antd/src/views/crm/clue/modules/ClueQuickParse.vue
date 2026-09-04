<script lang="ts" setup>
import { computed, ref } from 'vue';

import { message } from 'ant-design-vue';

import { parseClue, type ParsedClue } from '#/utils/crm/clue-parse';
import {
  getAiClueConfig,
  saveAiClueConfig,
  type AiClueConfig,
} from '#/utils/crm/ai-config';

const emit = defineEmits<{
  apply: [payload: { fields: ParsedClue }];
}>();

type ActiveTab = 'text' | 'image';

const collapsed = ref(false);
const activeTab = ref<ActiveTab>('text');
const content = ref('');
const loading = ref(false);
const images = ref<{ file: File; url: string }[]>([]);

// AI 识别配置（界面内可填，存浏览器本地）
const aiCfg = ref<AiClueConfig>(getAiClueConfig());
const showAiCfg = ref(false);
const aiReady = computed(
  () => aiCfg.value.enabled && !!aiCfg.value.baseUrl && !!aiCfg.value.model,
);
function saveAiCfg() {
  aiCfg.value = saveAiClueConfig({
    baseUrl: aiCfg.value.baseUrl.trim(),
    model: aiCfg.value.model.trim(),
    apiKey: aiCfg.value.apiKey.trim(),
  });
  message.success(
    aiReady.value
      ? 'AI 识别配置已保存，现在可以识别图片文字了'
      : 'AI 识别配置已保存，但 baseUrl 与 model 仍需填写才能启用识别',
  );
}

/** 将文件转为 data URL（base64），用于送给视觉大模型做 OCR 识别 */
function fileToDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

/**
 * 上传图片可能很大，直接转 base64 会导致请求体过大、浏览器超时失败。
 * 这里用 canvas 把图片缩放到最大边 maxDim 并压成 jpeg，显著降低体积，
 * 既保证视觉模型能看清文字，又避免“上传图片点识别没反应”的问题。
 */
async function compressImage(
  file: File,
  maxDim = 1280,
  quality = 0.82,
): Promise<string> {
  const original = await fileToDataUrl(file);
  const canCompress =
    file.type === 'image/jpeg' ||
    file.type === 'image/png' ||
    file.type === 'image/webp';
  if (!canCompress) {
    return original;
  }
  try {
    const img = await new Promise<HTMLImageElement>((resolve, reject) => {
      const im = new Image();
      im.onload = () => resolve(im);
      im.onerror = reject;
      im.src = original;
    });
    let { width, height } = img;
    if (width > maxDim || height > maxDim) {
      const scale = maxDim / Math.max(width, height);
      width = Math.round(width * scale);
      height = Math.round(height * scale);
    }
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return original;
    }
    ctx.drawImage(img, 0, 0, width, height);
    return canvas.toDataURL('image/jpeg', quality);
  } catch {
    return original;
  }
}

function addImageFile(file: File) {
  if (!file.type.startsWith('image/')) {
    return;
  }
  images.value.push({ file, url: URL.createObjectURL(file) });
}

function onPaste(e: ClipboardEvent) {
  const dt = e.clipboardData;
  if (!dt) {
    return;
  }
  let handled = false;
  let hasImage = false;
  let hasText = false;
  for (const it of dt.items) {
    if (it.type.startsWith('image/')) {
      const f = it.getAsFile();
      if (f) {
        addImageFile(f);
        hasImage = true;
        handled = true;
      }
    } else if (it.kind === 'string' && it.type === 'text/plain') {
      const text = dt.getData('text/plain');
      if (text) {
        content.value += (content.value ? '\n' : '') + text;
        hasText = true;
        handled = true;
      }
    }
  }
  if (handled) {
    e.preventDefault();
    // 根据粘贴内容自动切换对应方案：纯图片→图片 OCR，纯文本→粘贴文本
    if (hasImage && !hasText) {
      activeTab.value = 'image';
    } else if (hasText && !hasImage) {
      activeTab.value = 'text';
    }
  }
}

function onFilePick(e: Event) {
  const input = e.target as HTMLInputElement;
  if (input.files) {
    Array.from(input.files).forEach(addImageFile);
  }
  input.value = '';
}

function onDrop(e: DragEvent) {
  if (e.dataTransfer?.files?.length) {
    const files = Array.from(e.dataTransfer.files);
    if (files.some((f) => f.type.startsWith('image/'))) {
      activeTab.value = 'image';
      files.forEach(addImageFile);
    }
  }
}

function removeImage(i: number) {
  const [removed] = images.value.splice(i, 1);
  if (removed) {
    URL.revokeObjectURL(removed.url);
  }
}

function clearAll() {
  images.value.forEach((i) => URL.revokeObjectURL(i.url));
  images.value = [];
  content.value = '';
  activeTab.value = 'text';
}

async function handleRecognize() {
  const isImageMode = activeTab.value === 'image';
  if (!isImageMode && !content.value.trim()) {
    message.warning('请先粘贴文本');
    return;
  }
  if (isImageMode && images.value.length === 0) {
    message.warning('请先上传或粘贴图片');
    return;
  }

  loading.value = true;
  try {
    const text = isImageMode ? '' : content.value;
    let imageDataUrls: string[] = [];
    if (isImageMode && images.value.length) {
      try {
        imageDataUrls = await Promise.all(
          images.value.map((i) => compressImage(i.file)),
        );
      } catch {
        // 转换失败不影响文本识别
      }
    }

    const aiReadyFlag = aiReady.value;
    const { fields, aiError } = await parseClue(text, imageDataUrls);

    // 图片仅用于识别信息，不作为附件保存
    emit('apply', { fields });

    if (aiError) {
      // AI 真正调用失败（网络 / CORS / 接口报错 / 超大图片超时等），明确告知用户
      message.warning(
        `AI 识别失败：${aiError}。已按规则识别文本；如需识别图片请检查网络或点右上角“AI 配置”核对 Base URL 与模型。`,
      );
    } else if (!aiReadyFlag && isImageMode) {
      message.info(
        '当前未配置 AI，图片无法被识别。点右上角“AI 配置”填写 Base URL 与视觉模型后，即可自动识别图片文字。',
      );
    } else {
      message.success('识别完成，已自动填充线索字段，请核对后提交');
    }
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div
    class="mb-4 rounded-md border border-dashed border-primary/40 bg-primary/5 p-3"
    @paste.prevent="onPaste"
    @dragover.prevent
    @drop.prevent="onDrop"
  >
    <!-- 头部：与截图保持一致 -->
    <div class="mb-3 flex items-start justify-between gap-3">
      <div class="flex items-start gap-3">
        <span
          class="flex h-8 w-8 shrink-0 items-center justify-center rounded bg-primary text-lg text-primary-foreground"
        >
          ✨
        </span>
        <div>
          <div class="text-base font-medium leading-tight">一键识别</div>
          <p class="mt-0.5 text-xs text-muted-foreground">
            粘贴微信聊天/名片/邮件文本自动填入；粘贴或拖入名片照片可 OCR 识别图中文字
          </p>
        </div>
      </div>
      <div class="flex shrink-0 items-center gap-1">
        <a-button type="link" size="small" @click="showAiCfg = !showAiCfg">
          AI 配置
        </a-button>
        <a-button type="link" size="small" @click="collapsed = !collapsed">
          {{ collapsed ? '展开' : '收起' }}
        </a-button>
      </div>
    </div>

    <div
      v-if="showAiCfg"
      class="mb-3 rounded border border-border bg-muted/40 p-2"
    >
      <div class="grid grid-cols-1 gap-2">
        <div>
          <label class="text-xs text-muted-foreground">Base URL（以 /v1 结尾）</label>
          <a-input
            v-model:value="aiCfg.baseUrl"
            placeholder="例如 https://api.openai.com/v1 或你的代理地址"
            size="small"
          />
        </div>
        <div>
          <label class="text-xs text-muted-foreground">视觉模型名</label>
          <a-input
            v-model:value="aiCfg.model"
            placeholder="例如 gpt-4o / gpt-4o-mini"
            size="small"
          />
        </div>
        <div>
          <label class="text-xs text-muted-foreground">API Key</label>
          <a-input
            v-model:value="aiCfg.apiKey"
            placeholder="sk-..."
            size="small"
            type="password"
          />
        </div>
      </div>
      <div class="mt-2 flex items-center gap-2">
        <a-button type="primary" size="small" @click="saveAiCfg">保存配置</a-button>
        <span class="text-xs text-muted-foreground">
          保存后本浏览器生效；填好 Base URL 与模型即可识别图片文字
        </span>
      </div>
    </div>

    <template v-if="!collapsed">
      <!-- 两种方案切换 -->
      <div class="mb-3 flex items-center gap-2">
        <a-button
          :type="activeTab === 'text' ? 'primary' : 'default'"
          size="small"
          @click="activeTab = 'text'"
        >
          粘贴文本
        </a-button>
        <a-button
          :type="activeTab === 'image' ? 'primary' : 'default'"
          size="small"
          @click="activeTab = 'image'"
        >
          图片 OCR 识别
        </a-button>
        <a-tag v-if="aiReady" color="green" class="ml-auto">AI 已启用</a-tag>
        <a-tag v-else color="orange" class="ml-auto">AI 未配置</a-tag>
      </div>

      <!-- 方案一：粘贴文本 -->
      <template v-if="activeTab === 'text'">
        <a-textarea
          v-model:value="content"
          :rows="4"
          placeholder="例:王经理 13800138000 中石化四公司,北京朝阳区xx路 123号,邮箱 wang@example.com,QQ 1234567"
        />
      </template>

      <!-- 方案二：图片 OCR 识别 -->
      <template v-else>
        <div
          class="rounded border border-dashed border-border bg-muted/20 p-4"
          @dragover.prevent
          @drop.prevent="onDrop"
        >
          <div class="flex flex-wrap items-center justify-center gap-2">
            <div v-for="(img, i) in images" :key="i" class="relative">
              <img
                :src="img.url"
                class="h-16 w-16 rounded border object-cover"
                alt="预览"
              />
              <span
                class="absolute -right-1.5 -top-1.5 flex h-4 w-4 cursor-pointer items-center justify-center rounded-full bg-red-500 text-xs text-white"
                @click="removeImage(i)"
              >
                ×
              </span>
            </div>
            <label
              class="flex h-16 w-16 cursor-pointer items-center justify-center rounded border border-dashed text-xs text-muted-foreground hover:bg-muted/40"
            >
              + 图片
              <input
                type="file"
                accept="image/*"
                class="hidden"
                @change="onFilePick"
              />
            </label>
          </div>
          <p class="mt-2 text-center text-xs text-muted-foreground">
            点击“+ 图片”上传，或拖拽 / 粘贴图片到此处
          </p>
          <p class="mt-1 text-center text-xs text-muted-foreground">
            {{ aiReady ? 'AI 已配置：上传后点击“智能解析并填充”即可识别图中文字。' : '当前未配置 AI，图片无法被识别；请先点右上角“AI 配置”。' }}
          </p>
        </div>
      </template>

      <!-- 操作按钮 -->
      <div class="mt-3 flex items-center gap-2">
        <a-button type="primary" :loading="loading" @click="handleRecognize">
          智能解析并填充
        </a-button>
        <a-button @click="clearAll">清空</a-button>
      </div>
    </template>
  </div>
</template>
