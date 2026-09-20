<script setup lang="ts">
import { computed } from 'vue'
import type { Identifier } from '@/types/mall'
import { useOrderLogistics } from '@/composables/useOrderLogistics'
import { logisticsPresentation } from '@/utils/fulfillment'
import { formatDate } from '@/utils/format'
import ServiceEntry from '@/components/ServiceEntry.vue'
import OrderLogisticsMap from '@/components/OrderLogisticsMap.vue'
import type { AddressLocation } from '@/types/fulfillment'

const props = defineProps<{ orderId: Identifier; receiverLocation?: AddressLocation; receiverAddress?: string; dispatchOriginAddress?: string }>()
const { data, loading, failed, remaining, canRefresh, load } = useOrderLogistics(() => props.orderId)
const presentation = computed(() => logisticsPresentation(data.value))
const providerName = computed(() => ({ kd100: '快递100', kdniao: '快递鸟' }[data.value?.source || ''] || '物流服务'))
</script>

<template>
  <section class="logistics-panel" data-testid="order-logistics">
    <header><h2>快递物流</h2><el-button :disabled="!canRefresh" :loading="loading" @click="load">{{ remaining ? `${remaining}秒后可刷新` : (failed || presentation.status === 'PROVIDER_ERROR' ? '重试查询' : '刷新物流') }}</el-button></header>
    <el-skeleton v-if="loading && !data" :rows="2" animated />
    <template v-else>
      <dl v-if="data?.carrierName || data?.trackingNo"><div><dt>快递公司</dt><dd>{{ data.carrierName || '暂未提供' }}</dd></div><div><dt>运单号</dt><dd class="tracking-number">{{ data.trackingNo || '暂未提供' }}</dd></div></dl>
      <p v-if="failed" class="query-message warning" role="status">物流信息暂时加载失败，请稍后重试。{{ data ? '下方保留上次查询结果，不代表最新运输情况。' : '' }}</p>
      <p v-else class="query-message" :class="{ warning: presentation.warning }" role="status">{{ presentation.message }}</p>
      <p v-if="data?.stale && !failed" class="warning">当前为历史查询结果，可能已过期，请稍后刷新确认。</p>
      <p v-if="presentation.showTransport && data?.transportStatus && data.transportStatus !== 'UNKNOWN'">运输状态：{{ data.transportStatusLabel || '以物流服务提供的信息为准' }}</p>
      <div v-if="presentation.events.length" class="tracking-events">
        <p class="source-note">数据来源：{{ providerName }}<template v-if="data?.updatedTime"> · 查询更新时间：{{ formatDate(data.updatedTime) }}</template><template v-if="data?.cached"> · 缓存结果</template></p>
        <ol><li v-for="(event, index) in presentation.events" :key="`${event.time}-${index}`"><time>{{ formatDate(event.time ?? undefined) }}</time><p><span v-if="event.statusLabel">{{ event.statusLabel }} · </span>{{ event.content }}</p></li></ol>
      </div>
      <OrderLogisticsMap v-if="presentation.events.length || dispatchOriginAddress" :events="presentation.events" :receiver-location="receiverLocation" :receiver-address="receiverAddress" :dispatch-origin-address="dispatchOriginAddress" />
      <p class="logistics-footnote">物流签收不等于您已确认收货，也不会自动触发退款或结算。当前仅展示文字轨迹，不提供车辆实时定位。</p>
      <ServiceEntry resource-type="ORDER" :resource-id="orderId" label="联系订单客服核实物流" />
    </template>
  </section>
</template>

<style scoped lang="scss">
.logistics-panel { margin-top: 20px; padding: 22px; border: 1px solid var(--line); } header { display: flex; align-items: center; justify-content: space-between; gap: 20px; } h2 { margin: 0; font-size: 17px; } dl { display: flex; flex-wrap: wrap; gap: 18px 46px; } dl div { display: flex; gap: 14px; } dt { color: var(--muted); } dd { margin: 0; overflow-wrap: anywhere; } .tracking-number { user-select: all; } .query-message { padding: 12px 14px; background: #f1f6f6; border-radius: 5px; } .warning { color: #a55a09; } .logistics-footnote, .source-note { color: var(--muted); font-size: 12px; line-height: 1.7; } ol { list-style: none; padding: 0 0 0 10px; } li { position: relative; padding: 0 0 20px 22px; border-left: 2px solid var(--line); } li::before { content: ''; position: absolute; top: 4px; left: -5px; width: 8px; height: 8px; border-radius: 50%; background: var(--brand); } li time { color: var(--muted); font-size: 12px; } li p { margin: 8px 0 0; white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
