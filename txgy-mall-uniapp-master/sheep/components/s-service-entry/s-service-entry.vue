<template><view v-if="!expired" class="service-entry"><button size="mini" :disabled="disabled || state.busy" :loading="state.busy" @tap.stop="open">{{ text }}</button><view v-if="state.error" class="error" role="alert">{{ state.error }}</view></view><view v-else class="service-entry error">账号已变化，请刷新页面后重新咨询。</view></template>
<script setup>
  import { computed, reactive, ref } from 'vue';
  import sheep from '@/sheep';
  import SpuApi from '@/sheep/api/product/spu';
  import OrderApi from '@/sheep/api/trade/order';
  import { useTenantTask } from '@/sheep/hooks/useTenantTask';
  import { chatData } from '@/sheep/helper/kefu-chat.mjs';
  import { consultationId, createConsultationAction } from '@/sheep/helper/consultation-entry.mjs';
  const props = defineProps({ resourceType: String, resourceId: [String, Number], label: String, disabled: Boolean });
  const expired = ref(false), state = reactive({ busy: false, error: '' });
  const ownerPage = getCurrentPages().at(-1);
  const task = useTenantTask(() => { expired.value = true; state.busy = false; state.error = ''; });
  const typed = computed(() => ['PRODUCT', 'ORDER'].includes(props.resourceType) && consultationId(props.resourceId));
  const text = computed(() => props.resourceType && !typed.value ? '店铺信息不足，咨询平台' : props.label || (typed.value ? props.resourceType === 'PRODUCT' ? '联系该商品客服' : '联系订单客服' : '店铺信息不足，咨询平台'));
  const execute = createConsultationAction({
    resource: () => ({ type: typed.value ? props.resourceType : 'PLATFORM', id: typed.value || undefined }),
    readProduct: async id => chatData(await SpuApi.getSpuDetail(id)),
    readOrder: async id => chatData(await OrderApi.getOrderDetail(id)),
    current: () => task.current() && !props.disabled && getCurrentPages().at(-1) === ownerPage,
    confirmPlatform: () => new Promise(resolve => uni.showModal({ title: '选择平台咨询', content: '此商品或订单未提供可核验的店铺。是否明确改为咨询平台？不会携带商品或订单卡片，也不会发送消息。', confirmText: '咨询平台', success: result => resolve(result.confirm === true), fail: () => resolve(false) })),
    navigate: target => sheep.$router.go('/pages/chat/service', target),
    update: value => Object.assign(state, value),
  });
  function open() { if (!props.disabled) execute(); }
</script>
<style scoped>.service-entry{padding:12rpx 20rpx;line-height:1.6}.service-entry button{font-size:24rpx;color:#187768;background:#f1f7f5;white-space:normal}.error{color:#a13927;font-size:24rpx;margin-top:10rpx}</style>
