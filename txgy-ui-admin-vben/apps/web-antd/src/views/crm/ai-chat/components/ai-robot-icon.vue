<script lang="ts" setup>
/**
 * CRM 智能助手图标（机器人）
 *
 * 说明：按设计稿复刻的线稿机器人，使用 currentColor 描边，
 * 因此可通过外层 CSS 的 color / text-* 类自由控制颜色，并自动适配深色模式。
 * 相比原来的 emoji（🤖），这里是矢量图，任意尺寸都清晰、不依赖系统字体。
 */
withDefaults(defineProps<{ size?: number | string }>(), {
  size: 64,
});
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 120 120"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <g
      stroke="currentColor"
      stroke-width="4.5"
      stroke-linecap="round"
      stroke-linejoin="round"
    >
      <!-- 头顶天线 -->
      <path d="M44 23 L40 16" />
      <path d="M76 23 L80 16" />
      <circle cx="36" cy="11" r="4.5" />
      <circle cx="84" cy="11" r="4.5" />

      <!-- 头部 -->
      <rect x="34" y="22" width="52" height="32" rx="13" />

      <!-- 眼睛 -->
      <rect
        x="44.5"
        y="33"
        width="9"
        height="11.5"
        rx="4.5"
        fill="currentColor"
        stroke="none"
      />
      <rect
        x="66.5"
        y="33"
        width="9"
        height="11.5"
        rx="4.5"
        fill="currentColor"
        stroke="none"
      />

      <!-- 身体 -->
      <rect x="30" y="54" width="60" height="34" rx="15" />

      <!-- 胸前屏幕 -->
      <rect x="48" y="64" width="24" height="13" rx="6.5" />

      <!-- 手臂 -->
      <rect x="12" y="56" width="12" height="24" rx="6" />
      <rect x="96" y="56" width="12" height="24" rx="6" />

      <!-- 腿 -->
      <rect x="46" y="88" width="12" height="24" rx="5" />
      <rect x="62" y="88" width="12" height="24" rx="5" />
    </g>
  </svg>
</template>
