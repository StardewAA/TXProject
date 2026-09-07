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

const emit = defineEmits(['success']);
const rowData = ref<CrawlExternalApi.SourceItem>();
const getTitle = computed(() => (rowData.value?.id ? '编辑采集源' : '新增采集源'));

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
      if (rowData.value?.id) {
        await updateCrawlSource({ ...data, id: rowData.value.id });
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
      formApi.updateSchema(useFormSchema(false));
      await formApi.resetForm();
      await formApi.setValues({
        channelCode: 'GENERIC',
        fetchIntervalSeconds: 3600,
        robotsOk: false,
        enabled: false,
      });
      return;
    }
    modalApi.lock();
    try {
      rowData.value = await getCrawlSource(data.id);
      formApi.updateSchema(useFormSchema(true));
      await formApi.setValues(rowData.value);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-1/2">
    <Form class="mx-2" />
  </Modal>
</template>
