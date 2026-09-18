<!-- Text logistics is authoritative; no simulated vehicle map or fabricated delivery events. -->
<template>
  <s-layout title="物流追踪">
    <view v-if="state.expired" class="log-card">登录账号已变化，请返回订单列表重新进入。</view>
    <view v-else class="log-wrap">
      <view class="log-card">
        <view class="title">运单信息</view>
        <view v-if="state.info">快递公司：{{ state.info.carrierName || '暂未登记' }}</view>
        <view v-if="state.info">快递单号：{{ state.info.trackingNo || '暂未登记' }}</view>
        <view v-if="state.info && state.info.transportStatus !== 'UNKNOWN'">运输状态：{{ state.info.transportStatusLabel }}</view>
        <view v-if="state.loading">正在查询物流信息…</view>
        <view v-if="state.error" class="notice" role="alert">{{ state.error }}</view>
        <view v-if="state.info" class="notice">{{ state.info.message }}</view>
        <view v-if="state.info?.updatedTime" class="muted">数据更新时间：{{ formatTime(state.info.updatedTime) }}</view>
        <view v-if="state.info?.cached" class="muted">当前为缓存结果，刷新不会保证产生新的承运商轨迹。</view>
        <view v-if="state.info?.stale" class="notice">轨迹可能过期，请稍后重试或联系店铺核实。</view>
        <button size="mini" :disabled="state.loading || remaining > 0" @tap="load">
          {{ remaining > 0 ? `${remaining} 秒后可刷新` : state.error ? '重试查询' : '刷新物流' }}
        </button>
      </view>
      <view v-if="state.info?.events.length" class="log-card">
        <view class="title">文字轨迹</view>
        <view v-for="(event, index) in state.info.events" :key="`${event.time}-${index}`" class="event">
          <view>{{ event.statusLabel }}</view>
          <view class="content">{{ event.content }}</view>
          <view class="muted">{{ formatTime(event.time) }}</view>
        </view>
      </view>
      <view class="log-card muted">
        <view>地图轨迹暂未开通，当前展示已核实的运单和文字信息；暂无点位时不显示模拟路线。</view>
        <view class="boundary">物流签收不等于买家确认收货；退货送达也不等于入库验收或退款完成。</view>
        <s-service-entry resource-type="ORDER" :resource-id="state.id" label="联系订单客服核实物流" />
      </view>
    </view>
  </s-layout>
</template>

<script setup>
  import { onLoad, onShow, onHide } from '@dcloudio/uni-app';
  import { computed, reactive, onScopeDispose } from 'vue';
  import OrderApi from '@/sheep/api/trade/order';
  import { useTenantTask } from '@/sheep/hooks/useTenantTask';
  import { normalizeLogistics, createLatestTask, fulfillmentTime } from '@/sheep/helper/fulfillment-view.mjs';

  const state = reactive({ id: '', info: null, error: '', loading: false, expired: false, nextRefresh: 0, now: Date.now() });
  const task = useTenantTask(() => { state.expired = true; state.info = null; state.error = ''; state.loading = false; });
  const latest = createLatestTask(task.current);
  const remaining = computed(() => Math.max(0, Math.ceil((state.nextRefresh - state.now) / 1000)));
  let timer;
  function stop() { clearInterval(timer); timer = null; }
  function tick() { state.now = Date.now(); }
  const formatTime = fulfillmentTime;
  async function load() {
    tick();
    if (!task.current() || !state.id || state.loading || remaining.value > 0) return;
    const current = latest.begin();
    state.loading = true; state.error = '';
    try {
      const response = await OrderApi.getLogistics(state.id);
      if (!current()) return;
      if (response?.code !== 0) throw new Error('unavailable');
      state.info = normalizeLogistics(response.data, state.id);
      state.nextRefresh = Date.now() + state.info.refreshAfterSeconds * 1000;
    } catch {
      if (!current()) return;
      state.info = null; state.error = '物流信息加载失败，请重试；若仍失败，请确认登录账号及订单权限。';
    } finally { if (current()) state.loading = false; }
  }
  onLoad(options => {
    const id = String(options?.id || '');
    if (!/^[1-9]\d*$/.test(id)) { state.error = '订单编号不正确，请返回订单列表重新进入。'; return; }
    state.id = id; load();
  });
  onShow(() => { tick(); stop(); timer = setInterval(tick, 1000); if (state.id && !state.info && !state.loading) load(); });
  onHide(() => { latest.invalidate(); task.invalidate(); state.loading = false; stop(); });
  onScopeDispose(stop);
</script>

<style scoped lang="scss">
  .log-wrap { padding: 20rpx; }
  .log-card { margin: 0 0 20rpx; padding: 28rpx; background: white; border-radius: 12rpx; line-height: 1.8; font-size: 28rpx; overflow-wrap: anywhere; }
  .title { font-size: 32rpx; font-weight: bold; margin-bottom: 16rpx; }
  .notice { margin: 20rpx 0; padding: 16rpx; background: #f1f7f5; color: #31574f; }
  .muted { color: #666; font-size: 24rpx; }
  .event { padding: 20rpx 0 20rpx 24rpx; border-left: 4rpx solid #208879; border-bottom: 1rpx solid #eee; }
  .content { margin: 8rpx 0; }
  .boundary { margin-top: 14rpx; }
</style>
