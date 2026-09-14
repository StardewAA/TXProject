<script lang="ts" setup>
import type { MesWmBarcodeApi } from '#/api/mes/wm/barcode';

import { computed, ref } from 'vue';

import { BarcodeBizTypeEnum } from '@vben/constants';

import { Button, message, Modal } from 'ant-design-vue';

import { getBarcodeByBusiness } from '#/api/mes/wm/barcode';

import MesWmBarcode from './barcode.vue';

const props = withDefaults(
  defineProps<{
    bizCode?: string;
    bizId?: number;
    bizType?: number | string;
    labelText?: string;
  }>(),
  {
    bizCode: undefined,
    bizId: undefined,
    bizType: undefined,
    labelText: '标签打印',
  },
);

/**
 * 归一化业务类型：兼容调用方直接传枚举名字符串（如 "PROCARD"、"BATCH"）的旧写法
 * ITEM_BATCH（采购入库批次标签）是 BATCH 的别名
 */
function resolveBizType(t?: number | string): number | undefined {
  if (t === undefined || t === null || t === '') {
    return undefined;
  }
  if (typeof t === 'number') {
    return t;
  }
  const alias: Record<string, number> = {
    ITEM_BATCH: BarcodeBizTypeEnum.BATCH,
  };
  return (BarcodeBizTypeEnum as unknown as Record<string, number>)[t] ?? alias[t];
}

const open = ref(false); // 打印预览弹窗
const loading = ref(false); // 条码加载中
const record = ref<MesWmBarcodeApi.Barcode>(); // 条码记录
const barcodeRef = ref<InstanceType<typeof MesWmBarcode>>();

/** 无条码记录时的兜底：直接以业务编码作为条码内容（二维码） */
const fallbackRecord = computed<MesWmBarcodeApi.Barcode>(() => ({
  bizCode: props.bizCode,
  bizId: props.bizId,
  bizType: props.bizType,
  content: props.bizCode,
  format: 1, // QR_CODE
}));

/** 打开预览弹窗 */
async function handlePreview() {
  const bizType = resolveBizType(props.bizType);
  if (props.bizId === undefined || bizType === undefined) {
    message.warning(`未知的标签业务类型：${props.bizType ?? '空'}`);
    return;
  }
  loading.value = true;
  try {
    record.value =
      (await getBarcodeByBusiness(bizType, props.bizId)) || undefined;
  } catch {
    record.value = undefined;
  } finally {
    loading.value = false;
  }
  open.value = true;
}

/** 当前用于渲染的条码 */
const current = computed(() => record.value || fallbackRecord.value);

/** 调起浏览器打印：以新窗口输出标签页 */
function handlePrint() {
  const base64 = barcodeRef.value?.getImageBase64();
  if (!base64) {
    message.error('条码渲染失败，请重试');
    return;
  }
  const now = new Date().toLocaleString('zh-CN');
  const html = `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8" />
<title>标签打印</title>
<style>
  @page { margin: 0; }
  body { margin: 0; font-family: "Microsoft YaHei", "PingFang SC", sans-serif; }
  .label {
    width: 320px; margin: 12px auto; padding: 12px 16px;
    border: 1px dashed #999; box-sizing: border-box;
    display: flex; flex-direction: column; align-items: center; gap: 6px;
  }
  .title { font-size: 14px; font-weight: 600; letter-spacing: 2px; }
  .barcode { line-height: 0; }
  .row { width: 100%; display: flex; justify-content: space-between; font-size: 12px; }
  .row .k { color: #666; }
  @media print { body > *:not(.label) { display: none; } .label { border-style: solid; } }
</style>
</head>
<body>
  <div class="label">
    <div class="title">天信管业 MES · 流转标签</div>
    <div class="barcode"><img src="${base64}" alt="barcode" /></div>
    <div class="row"><span class="k">编码</span><span>${current.value.bizCode ?? '-'}</span></div>
    <div class="row"><span class="k">条码内容</span><span>${current.value.content ?? '-'}</span></div>
    <div class="row"><span class="k">打印时间</span><span>${now}</span></div>
  </div>
</body>
</html>`;
  const win = window.open('', '_blank', 'width=420,height=600');
  if (!win) {
    message.error('打印窗口被浏览器拦截，请允许弹出窗口后重试');
    return;
  }
  win.document.write(html);
  win.document.close();
  win.focus();
  win.print();
}
</script>

<template>
  <Button type="link" @click="handlePreview">
    {{ labelText }}
  </Button>

  <Modal
    v-model:open="open"
    title="标签打印预览"
    :width="420"
    :footer="null"
  >
    <div
      class="mx-auto flex flex-col items-center gap-2 border border-dashed border-gray-400 p-4"
    >
      <div class="text-sm font-semibold tracking-widest">
        天信管业 MES · 流转标签
      </div>
      <div v-if="loading" class="py-8 text-gray-400">条码加载中...</div>
      <template v-else>
        <MesWmBarcode
          ref="barcodeRef"
          :content="current.content"
          :format="current.format"
          :height="120"
          :width="220"
        />
        <div class="flex w-full justify-between text-xs">
          <span class="text-gray-500">编码</span>
          <span>{{ current.bizCode ?? '-' }}</span>
        </div>
        <div class="flex w-full justify-between text-xs">
          <span class="text-gray-500">条码内容</span>
          <span>{{ current.content ?? '-' }}</span>
        </div>
      </template>
    </div>
    <div class="mt-4 text-right">
      <Button class="mr-2" @click="open = false">关闭</Button>
      <Button type="primary" :disabled="loading" @click="handlePrint">
        打印
      </Button>
    </div>
  </Modal>
</template>
