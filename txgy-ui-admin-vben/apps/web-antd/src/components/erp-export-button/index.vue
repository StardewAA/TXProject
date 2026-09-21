<!--
  ERP 列表页统一导出按钮（Excel / PDF 下拉）。

  背景：远程 master 的 21 个 ERP 列表页引用了本组件，但仓库里（含
  origin/ERP-approval-management-2026.9.4、origin/feat/erp-mes-integration）
  都不存在该文件，导致前端构建报
  `Rolldown failed to resolve import "#/components/erp-export-button/index.vue"`。
  2026-09-20 合并远程 master 时按各页面的实际用法补齐：
  各页面传入 `auth`（权限码）与 `exportHandler(format)`（导出动作，页面内自行
  调用自己的 exportXxx / exportXxxPdf 接口并下载），组件只负责权限判断、
  下拉选择格式与 loading 处理。
-->
<script lang="ts" setup>
import type { PropType } from 'vue';

import { computed, ref } from 'vue';

import { useAccess } from '@vben/access';

import { Button, Dropdown, message } from 'ant-design-vue';

export type ErpExportFormat = 'excel' | 'pdf';

defineOptions({ name: 'ErpExportButton' });

const props = defineProps({
  /** 需要的权限码列表，为空表示不校验权限 */
  auth: {
    type: Array as PropType<string[]>,
    default: () => [],
  },
  /** 导出处理函数：由各页面实现真正的接口调用与文件下载 */
  exportHandler: {
    type: Function as PropType<
      (format: ErpExportFormat) => Promise<unknown> | unknown
    >,
    required: true,
  },
});

const { hasAccessByCodes } = useAccess();
const loading = ref(false);

const allowed = computed(
  () => props.auth.length === 0 || hasAccessByCodes(props.auth),
);

const menuConfig = computed(() => ({
  items: [
    { key: 'excel', label: '导出 Excel' },
    { key: 'pdf', label: '导出 PDF' },
  ],
  onClick: ({ key }: { key: string }) => {
    void handleExport(key as ErpExportFormat);
  },
}));

async function handleExport(format: ErpExportFormat) {
  loading.value = true;
  try {
    await props.exportHandler(format);
  } catch (error) {
    console.error('[ErpExportButton] 导出失败', error);
    message.error('导出失败，请稍后重试');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <Dropdown v-if="allowed" :menu="menuConfig" :trigger="['click']">
    <Button :loading="loading">导出</Button>
  </Dropdown>
</template>
