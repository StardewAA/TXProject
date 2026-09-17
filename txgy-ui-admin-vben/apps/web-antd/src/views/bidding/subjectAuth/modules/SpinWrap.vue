<!-- 抽屉内异步加载态包裹：slots 透传，不额外包一层 DOM。
     从 index.vue 的第二个 script 块抽出 —— 仓库规则 vue/one-component-per-file 禁止单文件多组件
     （原文件因此长期挂 3 处 error，模块 lint 一直不绿）。 -->
<script lang="ts" setup>
import { Spin } from 'ant-design-vue';

defineProps<{ loading?: boolean }>();
</script>

<template>
  <Spin :spinning="loading">
    <slot></slot>
  </Spin>
</template>
