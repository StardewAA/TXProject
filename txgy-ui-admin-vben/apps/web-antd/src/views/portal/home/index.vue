<script lang="ts" setup>
import { portalAssistantConfig } from '#/components/txgy-assistant/config';

import AssistantChat from '#/components/txgy-assistant/assistant-chat.vue';

defineOptions({ name: 'PortalHome' });
</script>

<template>
  <!-- 首页助手与后台「概览 → AI首页」共用同一组件，差异只在 config（跳转目标） -->
  <AssistantChat :config="portalAssistantConfig" />
</template>