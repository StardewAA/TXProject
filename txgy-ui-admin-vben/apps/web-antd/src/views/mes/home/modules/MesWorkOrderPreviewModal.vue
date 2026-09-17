<script lang="ts" setup>
/**
 * 【生产工单预览】弹窗页面
 *
 * 【新增模块：生产工单预览】
 * 复用通用空白单据预览弹窗（MesDocPreviewModal），固定传入「生产工单」模板：
 * 大标题「生产工单」+ 工单编号/制单日期 副标题 + 6 项基础信息（全空）
 * + 物料清单（6 列表头，3 空白行）+ 工序明细（5 列表头，3 空白行）
 * 底部「关闭」「下载 Word 文件」由通用弹窗提供，导出模板与预览结构完全一致。
 */
import { ref } from 'vue';

import MesDocPreviewModal from './MesDocPreviewModal.vue';
import { WORK_ORDER_SCHEMA } from './blankDocSchema';

const previewRef = ref<InstanceType<typeof MesDocPreviewModal>>();

/** 打开生产工单预览 */
function open() {
  previewRef.value?.open(WORK_ORDER_SCHEMA);
}

defineExpose({ open });
</script>

<template>
  <MesDocPreviewModal ref="previewRef" />
</template>
