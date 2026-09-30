<script lang="ts" setup>
import type { CrawlExternalApi } from '#/api/bidding/crawlExternal';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  createCrawlSource,
  getCrawlSource,
  updateCrawlSource,
} from '#/api/bidding/crawlExternal';

import { useFormSchema } from '../data';

import EntryUrlsEditor from './entry-urls-editor.vue';

const emit = defineEmits(['success']);
const rowData = ref<CrawlExternalApi.SourceItem>();
const getTitle = computed(() => (rowData.value?.id ? '编辑信息源' : '新增信息源'));

/** 入口/栏目配置原文（结构化编辑器双向绑定；提交时覆盖表单 entryUrlsJson 隐藏字段） */
const entryUrlsJson = ref('');

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
  },
  layout: 'vertical',
  schema: useFormSchema(false),
  showDefaultActions: false,
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    try {
      const data = (await formApi.getValues()) as CrawlExternalApi.SourcePayload;
      // 入口/栏目配置以结构化编辑器为准（覆盖隐藏字段的原始值）
      data.entryUrlsJson = entryUrlsJson.value;
      if (rowData.value?.id) {
        await updateCrawlSource({ ...data, id: rowData.value.id! }); // truthy 分支已保证 id 存在
      } else {
        await createCrawlSource(data);
      }
      await modalApi.close();
      message.success('保存成功');
      emit('success');
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      rowData.value = undefined;
      return;
    }
    const data = modalApi.getData<CrawlExternalApi.SourceItem>();
    if (!data || !data.id) {
      rowData.value = undefined;
      entryUrlsJson.value = '';
      formApi.updateSchema(useFormSchema(false));
      await formApi.resetForm();
      await formApi.setValues({
        channelCode: 'GENERIC',
        fetchType: 'CRAWLER',
        fetchIntervalSeconds: 3600,
        robotsOk: false,
        enabled: false,
      });
      return;
    }
    modalApi.lock();
    try {
      rowData.value = await getCrawlSource(data.id);
      entryUrlsJson.value = rowData.value.entryUrlsJson || '';
      formApi.updateSchema(useFormSchema(true));
      await formApi.setValues(rowData.value);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <!--
    宽度用 class（Tailwind）而不是 :width 属性：vben 的 ModalProps 里没有 width
    （packages/@core/ui-kit/popup-ui/src/modal/modal.ts 只声明了 class / centered / zIndex 等），
    原先的 :width="860" 落进 attrs 后无人消费 → 弹窗一直是默认 w-130（520px），这是"窗口太窄"的根因。
    实测口径与 noticeExternal/modules/detail.vue 一致（用 w-[1180px] 指定宽度）；末尾 ! 是
    Tailwind v4 的 important 写法，用于压过 DialogContent 自带的 w-130。
    为什么是 1180：入口/栏目配置的六个字段要同排（见 source/modules/entry-urls-editor.vue），
    1180 减去内容区/卡片内边距与 5 个列间距后每列约 179px，输入框仍可正常读写。
  -->
  <Modal
    :title="getTitle"
    class="w-[1180px]! bid-ind"
    :centered="true"
    :mask-closable="false"
  >
    <Form class="mx-2" />
    <!-- 入口/栏目配置：结构化编辑器（立起来，替代平铺 JSON 文本域） -->
    <div class="eu-section">
      <div class="eu-section-t">
        入口 / 栏目配置
        <span class="eu-section-d">栏目对象数组，结构化编辑；保存时与补充参数一并写回 entry_urls_json。留空 = 从首页链接自动发现。</span>
      </div>
      <EntryUrlsEditor v-model="entryUrlsJson" />
    </div>
  </Modal>
</template>

<style scoped>
.eu-section { margin: 14px 8px 4px; border-top: 1px dashed hsl(var(--border)); padding-top: 12px; }
.eu-section-t { font-size: 13.5px; font-weight: 700; color: var(--ind-ink-text, #1f2937); display: flex; align-items: baseline; gap: 8px; margin-bottom: 10px; }
.eu-section-d { font-weight: 500; font-size: 12px; color: var(--ind-weak-text, #6b7280); }
</style>
