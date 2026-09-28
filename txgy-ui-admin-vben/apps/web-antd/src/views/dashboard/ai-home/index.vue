<script lang="ts" setup>
import { Page } from '@vben/common-ui';

import { adminAssistantConfig } from '#/components/txgy-assistant/config';

import AssistantChat from '#/components/txgy-assistant/assistant-chat.vue';

defineOptions({ name: 'DashboardAiHome' });
</script>

<template>
  <!-- 登录后的默认落地页：内容与交互和前台首页 /portal/home 一致；
       区别是跳转目标指向各业务模块的后台菜单，跳转不再离开后台布局。 -->
  <Page auto-content-height content-class="!p-0">
    <AssistantChat :config="adminAssistantConfig" embedded />
  </Page>
</template>