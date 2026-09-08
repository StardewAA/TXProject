<script lang="ts" setup>
import type { CrawlExternalApi } from '#/api/bidding/crawlExternal';

import { computed, onMounted, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Input, message, Radio, Select, Upload } from 'ant-design-vue';

import { crawlParserTest, fetchCrawlParserList } from '#/api/bidding/crawlExternal';

const props = defineProps<{ sourceOptions?: Array<{ label: string; value: string }> }>();
const emit = defineEmits(['parsed']);

const inputType = ref<'HTML' | 'JSON' | 'FILE'>('HTML');
const content = ref('');
const url = ref('');
const sourceCode = ref<string | undefined>(undefined);
const parserCode = ref('');
const parserOptions = ref<CrawlExternalApi.ParserInfo[]>([]);
const file = ref<File>();
const running = ref(false);

const parserCodeOptions = computed(() =>
  parserOptions.value.map((p) => ({
    label: p.name ? p.code + ' · ' + p.name : (p.code ?? ''),
    value: p.code ?? '',
  })),
);

/** 按输入口径建议缺省解析器 */
function suggestParser(): string {
  if (inputType.value === 'JSON') return 'JSON_GENERIC';
  if (inputType.value === 'FILE') {
    const n = (file.value?.name ?? '').toLowerCase();
    if (n.endsWith('.pdf')) return 'PDF_TEXT_GENERIC';
    if (n.endsWith('.json')) return 'JSON_GENERIC';
    return 'HTML_GENERIC';
  }
  return 'HTML_GENERIC';
}
watch([inputType, () => file.value?.name], () => {
  parserCode.value = suggestParser();
});

onMounted(async () => {
  try {
    parserOptions.value = (await fetchCrawlParserList()) || [];
    parserCode.value = suggestParser();
  } catch {
    // 解析器清单不可用（契约未落地环境）——下拉留空，手动输入 code
  }
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (running.value) return;
    if (!parserCode.value) {
      message.warning('请选择解析器');
      return;
    }
    if (inputType.value === 'FILE') {
      if (!file.value) {
        message.warning('请先选择要上传的文件（.html/.htm/.json/.pdf）');
        return;
      }
    } else if (!content.value.trim()) {
      message.warning('请粘贴源代码/原文内容');
      return;
    }
    running.value = true;
    modalApi.lock();
    try {
      const resp = await crawlParserTest({
        parserCode: parserCode.value,
        payloadType: inputType.value === 'FILE' ? 'FILE' : inputType.value,
        content: inputType.value === 'FILE' ? undefined : content.value,
        fileName: inputType.value === 'FILE' ? file.value!.name : undefined,
        file: inputType.value === 'FILE' ? file.value : undefined,
        url: url.value.trim() || undefined,
      });
      const ok = resp && (resp.status === 'SUCCESS' || resp.status === 'PARTIAL');
      message.success(ok ? '解析完成，已生成测试任务条' : '解析未成功（生成失败任务条）');
      modalApi.close();
      emit('parsed', resp, { inputType: inputType.value, fileName: file.value?.name });
    } catch (e) {
      message.error(e instanceof Error ? e.message : String(e));
    } finally {
      running.value = false;
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      content.value = '';
      url.value = '';
      sourceCode.value = undefined;
      file.value = undefined;
      inputType.value = 'HTML';
      parserCode.value = suggestParser();
    }
  },
});
</script>

<template>
  <Modal title="手动录入（生成测试任务）" class="w-2/3">
    <div class="mb-2 text-xs text-gray-500">
      粘贴 HTML/JSON 源代码，或上传文件（.html/.htm/.json/.pdf）。提交后走真实下载→解析管线，
      结果即刻生成一条“测试任务”任务条：源（原文）· 提取内容 · 渲染内容 · 过程。
    </div>
    <div class="mb-3 flex items-center gap-2">
      <span class="text-sm text-gray-600">输入口径：</span>
      <Radio.Group v-model:value="inputType" :options="[{ label: 'HTML 源代码', value: 'HTML' }, { label: 'JSON 原文', value: 'JSON' }, { label: '上传文件', value: 'FILE' }]" option-type="button" />
    </div>

    <div v-if="inputType !== 'FILE'" class="mb-2">
      <div class="mb-1 text-xs font-medium text-gray-600">源代码 / 原文内容</div>
      <Input.TextArea v-model:value="content" :rows="8" placeholder="将浏览器“查看源代码”全选复制粘贴于此，或粘贴页面接口返回的 JSON 原文…" />
    </div>

    <div v-else class="mb-2">
      <div class="mb-1 text-xs font-medium text-gray-600">上传文件（PDF/HTML/JSON）</div>
      <Upload.Dragger :max-count="1" accept=".html,.htm,.json,.pdf" :before-upload="(f: File) => { file = f; return false; }" :file-list="[]">
        <p class="ant-upload-text">{{ file ? file.name + '（' + file.size + ' 字节）' : '点击或拖拽文件到此处' }}</p>
        <p class="ant-upload-hint">PDF 需含文字层（扫描件将解析失败并如实标记）；HTML/JSON 文件同文本口径。</p>
      </Upload.Dragger>
    </div>

    <div class="grid grid-cols-2 gap-3">
      <div>
        <div class="mb-1 text-xs font-medium text-gray-600">解析器</div>
        <Select v-model:value="parserCode" :options="parserCodeOptions" placeholder="选择解析器（按口径自动建议）" style="width: 100%" />
      </div>
      <div>
        <div class="mb-1 text-xs font-medium text-gray-600">归属采集源（可选，MANUAL 直测）</div>
        <Select v-model:value="sourceCode" :options="props.sourceOptions || []" placeholder="不选=手动直测，不入源" allow-clear style="width: 100%" />
      </div>
      <div class="col-span-2">
        <div class="mb-1 text-xs font-medium text-gray-600">来源页 URL（可选，用于附件相对链接绝对化与站点上下文提示）</div>
        <Input v-model:value="url" placeholder="https://…" allow-clear />
      </div>
    </div>
  </Modal>
</template>