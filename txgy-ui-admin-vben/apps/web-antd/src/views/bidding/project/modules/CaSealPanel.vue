<script setup lang="ts">
/* ============================================================================
  CA 签章台账（抽屉）—— 项目台账 6771 的子功能入口
  背景：caSeal/index.vue 是独立路由页，但后端 SQL（2026-09-28-ca-seal-menu.sql）只挂了
    两个按钮权限点（bidding:ca:query / bidding:ca:verify）在项目台账 6771 下，**没有页面菜单行**
    → 页面没有入口、用户根本进不来。故按招投标域惯例（子域=项目台账下的按钮权限）把它挂成抽屉，
    复用既有页面内容，零重复实现。
============================================================================ */
import CaSealLedger from '../../caSeal/index.vue';

const props = withDefaults(defineProps<{ open: boolean }>(), { open: false });
const emit = defineEmits<{ (e: 'update:open', v: boolean): void }>();
</script>

<template>
  <a-drawer
    :open="props.open"
    title="CA 签章台账"
    :width="1200"
    destroy-on-close
    @close="emit('update:open', false)"
  >
    <!-- destroy-on-close：每次打开重新挂载，统计与列表自动拉最新 -->
    <CaSealLedger />
  </a-drawer>
</template>
