<!--
  CRM 首页 · AI 智能助手弹窗（方案 A：弹窗唤起）

  设计说明：
  - 直接复用整页版 AI 助手（views/crm/ai-chat），只是改用嵌入模式渲染，
    「多会话 / 上下文记忆 / 意图识别 / 写操作二次确认 / OCR 建单」等能力完全不变；
  - draggable: 复用框架内建的弹窗拖拽（拖动标题栏），可自由移动；
  - destroyOnClose: false —— 关闭弹窗不销毁组件，再次打开仍保留上一次的对话上下文；
  - 首页点击「示例问题」时通过 setData({ question }) 传入，弹窗打开即自动提问。
-->
<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import CrmAiChat from '#/views/crm/ai-chat/index.vue';

/** 待自动提问的问题（由首页示例问题传入） */
const autoQuestion = ref('');

const [Modal, modalApi] = useVbenModal({
  title: 'AI 智能助手',
  // 可拖动（复用框架内建拖拽能力）
  draggable: true,
  // 对话自带输入区，不需要底部「确定/取消」
  footer: false,
  // 保留会话上下文：关闭不销毁
  destroyOnClose: false,
  class: 'w-[1080px] max-w-[94vw]',
  async onOpenChange(isOpen) {
    if (isOpen) {
      autoQuestion.value = modalApi.getData<{ question?: string }>()?.question ?? '';
    } else {
      // 复位：保证下次点击同一个示例问题仍能触发自动提问
      autoQuestion.value = '';
    }
  },
});

defineExpose({
  /** 打开弹窗（可传 question 让助手自动提问） */
  open: (question?: string) => {
    modalApi.setData({ question }).open();
  },
});
</script>

<template>
  <Modal>
    <!-- 固定高度：嵌入模式下 AI 助手不再依赖整页布局（Page），由这里提供尺寸 -->
    <div class="h-[70vh] min-h-[420px]">
      <CrmAiChat embedded :auto-question="autoQuestion" />
    </div>
  </Modal>
</template>
