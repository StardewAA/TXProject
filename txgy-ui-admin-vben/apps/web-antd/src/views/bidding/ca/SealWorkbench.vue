<script setup lang="ts">
/**
 * CA 互认 · H5 网页签章工作台（电子签章组件装载）
 * 契约来源：《接口互认》官方示例 frontend-h5-demo（gitee.com/cebpubservice）：
 *   - 静态核心 /public/generic-legacy（pdf.js 二次开发，浏览器内完成 PDF 签章）
 *   - 桥接脚本 /generic-legacy/webBridge/api.js（已适配：去掉 localhost:8858 分支，统一当前 origin 的 /generic-legacy/ 路径）
 * 【接入状态 2026-09-20】本组件当前**没有任何引用方**（原 CaSignEntry 的「PDF 签章」通道已按职责收敛删除）。
 *   保留原因：它是**真实 SDK 模式**（bidding.ca.sdk.enabled=true）装载官方 H5 签章组件的唯一封装，待 CA 互认共享组件
 *   部署 + 交易平台注册、后端能签发 document（fileKey/token）后才启用；届时由 CaSealAuth 装载（而非 CaSignEntry）。
 *   测试模式（=false）下不需要它：盖章走后端 POST /bidding/ca/seal/document 本地真实 SM2 + 红章。
 * 用法：
 *   <SealWorkbench v-model:open="open" :document="doc" :tools-excludes="['sealValidate']" @sign-complete="onSigned" />
 *   document = { appId, userId, bizNo, fileKey, fileType:'pdf', fileName, token }（由后端 CA 平台签发）
 * 完成签章：组件点击「完成签章」→ 本组件 emit('sign-complete')（兼容 window.signComplete 与 postMessage 两种官方通道）。
 */
import { nextTick, onBeforeUnmount, onMounted, watch } from 'vue';

import { message } from 'ant-design-vue';

/** 签章文档参数（与官方 generic-legacy 的 document 对象对齐） */
export interface SealDocumentConfig {
  /** CA 平台应用编号 */
  appId?: string;
  /** 平台用户编号 */
  userId?: string;
  /** 业务流水号 */
  bizNo?: string;
  /** 文件 key（上传后平台返回） */
  fileKey?: string;
  /** 文件类型：pdf / ofd */
  fileType?: string;
  /** 文件显示名 */
  fileName?: string;
  /** 平台签发 token（JWT） */
  token?: string;
  /** 待签文件类型（ofd 时走 web2 渲染；当前未装载 web2，仅 pdf 可用） */
  awaitingSignatureFileType?: string;
}

interface DocEditorInstance {
  exec: (msg: { method: string; data?: unknown }) => void;
}

declare global {
  interface Window {
    PDFAPI?: {
      DocEditor: new (placeholderId: string, config: object) => DocEditorInstance;
    };
    signComplete?: () => void;
  }
}

const props = withDefaults(
  defineProps<{
    /** 弹窗显隐（v-model:open） */
    open?: boolean;
    /** 签章文档参数（打开前必须就绪） */
    document?: SealDocumentConfig;
    /** 隐藏功能项（sealAddNormal/sealAddContinued/sealAddBatch/sealAddGap/sealAddByWord/sealRemove/signatureDetail/sealValidate/completeSeal/viewAttachments/rotateRight/find/download/print/bookMark/thumbnail） */
    toolsExcludes?: string[];
    width?: string;
    height?: string;
  }>(),
  { open: false, width: '100%', height: '78vh', toolsExcludes: undefined, document: undefined },
);

const emit = defineEmits<{
  'update:open': [v: boolean];
  /** 工作台初始化完成（iframe 就绪） */
  ready: [];
  /** 完成签章（官方 signComplete 通道） */
  'sign-complete': [];
}>();

// 容器 id：每次实例独立（防多实例抢占同一 id）
const containerId = 'pdfWorkBench-' + Date.now() + '-' + Math.random().toString(36).slice(2, 7);

let apiPromise: Promise<void> | null = null;

/** 动态加载官方桥接脚本（防重复注入，幂等） */
function loadBridge(): Promise<void> {
  if (window.PDFAPI?.DocEditor) return Promise.resolve();
  if (apiPromise) return apiPromise;
  apiPromise = new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = '/generic-legacy/webBridge/api.js';
    s.onload = () => {
      resolve();
    };
    s.onerror = () => {
      apiPromise = null;
      reject(new Error('签章组件加载失败：/generic-legacy/webBridge/api.js 不可用'));
    };
    document.head.appendChild(s);
  });
  return apiPromise;
}

/** 初始化签章工作台（DocEditor 会把占位 div 替换为签章 iframe） */
async function initWorkbench(): Promise<void> {
  try {
    await loadBridge();
    if (!props.document || !props.document.fileKey) {
      message.warning('签章文档参数缺失（document.fileKey 必填，由后端 CA 平台签发）');
      return;
    }
    const config = {
      document: props.document,
      toolsExcludes: props.toolsExcludes ?? [],
    };
    new window.PDFAPI!.DocEditor(containerId, config);
    emit('ready');
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : String(e));
  }
}

/** 销毁工作台（iframe 随 DOM 卸载，无需手动移除） */
function destroyWorkbench(): void {
  // iframe 由 DocEditor 挂在占位 div 上，随 v-if 卸载自动移除
}

function onMessage(evt: MessageEvent): void {
  if (evt.data === 'signComplete') {
    emit('sign-complete');
  }
}

watch(
  () => props.open,
  (v) => {
    if (v) {
      void nextTick(() => initWorkbench());
    } else {
      destroyWorkbench();
    }
  },
);

onMounted(() => {
  window.addEventListener('message', onMessage);
  // 官方通道一：组件完成签章会调用父窗口的 signComplete（本组件统一转 emit；业务方勿重复覆盖）
  const prevSignComplete = window.signComplete;
  window.signComplete = () => {
    emit('sign-complete');
    prevSignComplete?.();
  };
});

onBeforeUnmount(() => {
  window.removeEventListener('message', onMessage);
  destroyWorkbench();
});
</script>

<template>
  <a-modal
    :open="open"
    :width="'94vw'"
    :title="'CA 电子签章'"
    :footer="null"
    :mask-closable="false"
    destroy-on-close
    @update:open="(v: boolean) => emit('update:open', v)"
  >
    <div v-if="open" :id="containerId" :style="{ width, height }"></div>
  </a-modal>
</template>
