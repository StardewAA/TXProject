<script lang="ts" setup>
/**
 * CRM AI 助手图标（蓝绿渐变圆形机器人头像）
 *
 * 说明：图形取自 assets/icons/crm-ai-avatar.ts（圆形裁切 + 透明底，base64 内联），
 * 与侧边栏菜单图标 `crmai:avatar` 使用同一份数据，保证两处视觉完全一致。
 */
import { CRM_AI_AVATAR_BODY, CRM_AI_AVATAR_VIEWBOX } from '#/assets/icons/crm-ai-avatar';

withDefaults(defineProps<{ size?: number | string }>(), {
  size: 64,
});
</script>

<template>
  <svg
    :viewBox="CRM_AI_AVATAR_VIEWBOX"
    :width="size"
    :height="size"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    v-html="CRM_AI_AVATAR_BODY"
  />
</template>
