<script setup lang="ts">
import { computed, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { productApi, tradeApi } from '@/api/mall'
import { useAccountMessageBox, useAccountOperation } from '@/composables/useAccountOperation'
import { consultationId, createConsultationAction } from '@/utils/consultation-entry.mjs'
const props = defineProps<{ resourceType?: 'PRODUCT' | 'ORDER'; resourceId?: unknown; label?: string; disabled?: boolean }>()
const router = useRouter(), operation = useAccountOperation(), modal = useAccountMessageBox()
const state = reactive({ busy: false, error: '' })
const typed = computed(() => props.resourceType && consultationId(props.resourceId))
const label = computed(() => props.resourceType && !typed.value ? '店铺信息不足，咨询平台' : props.label || (typed.value ? props.resourceType === 'PRODUCT' ? '联系该商品客服' : '联系订单客服' : '店铺信息不足，咨询平台'))
const execute = createConsultationAction({
  resource: () => ({ type: typed.value ? props.resourceType! : 'PLATFORM', id: typed.value || undefined }),
  readProduct: productApi.getDetail, readOrder: tradeApi.getOrder,
  current: () => operation.current() && !props.disabled,
  async confirmPlatform() { try { await modal.confirm('此商品或订单未提供可核验的店铺。是否明确改为咨询平台？不会携带商品或订单卡片，也不会发送消息。', '选择平台咨询', { confirmButtonText: '明确咨询平台', cancelButtonText: '暂不咨询' }); return true } catch { return false } },
  navigate: target => router.push({ path: target.scope === 'SHOP' ? `/member/customer-service/shop/${target.shopId}` : '/member/customer-service/scoped', query: target.resourceType ? { resourceType: target.resourceType, resourceId: String(target.resourceId) } : {} }),
  update: value => Object.assign(state, value),
})
function open() { if (!props.disabled) void execute() }
</script>
<template><span class="service-entry"><el-button link type="primary" :loading="state.busy" :disabled="disabled" @click.stop="open">{{ label }}</el-button><small v-if="state.error" role="alert">{{ state.error }}</small></span></template>
<style scoped>.service-entry{display:block;margin-top:8px;white-space:normal}.service-entry small{display:block;color:#a13927;font-size:12px;line-height:1.6}.service-entry .el-button{height:auto;white-space:normal;text-align:left}</style>
