<template>
  <image :src="source" mode="aspectFit" :aria-label="label" @error="failed = true" />
</template>

<script setup>
import { computed, ref, watch } from 'vue';
const props = defineProps({ src: { type: String, default: '' }, label: { type: String, default: '' } });
const failed = ref(false);
watch(() => props.src, () => { failed.value = false; });
const icons = [
  [/待付款|钱包|充值|余额/, 'wallet'], [/待收货|发货/, 'truck'],
  [/评价|反馈|客服/, 'chat'], [/售后|退款/, 'return'], [/订单/, 'order'],
  [/券|已领取|已使用|已失效/, 'ticket'], [/签到/, 'check'], [/设置/, 'settings'],
  [/收藏/, 'star'], [/记录|足迹/, 'clock'], [/问题|帮助/, 'help'],
  [/关于/, 'info'], [/隐私|协议/, 'shield'], [/首页/, 'home'],
  [/搜索/, 'search'], [/分类/, 'grid'], [/购物车|采购清单/, 'cart'], [/地址/, 'pin'], [/我的|个人中心/, 'user'],
];
// 固定功能入口直接使用随包资源，避免每次组件重新挂载都等待旧 CDN 图片失败。
// 未识别的自定义装修入口仍保留配置图片和失败兜底。
const source = computed(() => {
  const localIcon = icons.find(([pattern]) => pattern.test(props.label))?.[1];
  if (localIcon) return '/static/icons/mall/' + localIcon + '.svg';
  return props.src && !failed.value ? props.src : '/static/icons/mall/grid.svg';
});
</script>
