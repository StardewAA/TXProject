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
      <view v-if="state.info?.events.length || state.order?.dispatchOriginAddress" class="log-card">
        <view class="title">文字轨迹</view>
        <view v-for="(event, index) in state.info.events" :key="`${event.time}-${index}`" class="event">
          <view>{{ event.statusLabel }}</view>
          <view class="content">{{ event.content }}</view>
          <view class="muted">{{ formatTime(event.time) }}</view>
        </view>
      </view>
      <view v-if="state.info?.events.length || state.order?.dispatchOriginAddress" class="log-card">
        <view class="title">物流节点地图</view>
        <view class="muted">{{ state.mapMessage }}</view>
        <!-- #ifdef H5 -->
        <view v-show="state.mapReady" id="h5-amap-logistics-route" class="route-map"></view>
        <!-- #endif -->
        <view class="map-boundary">无承运商轨迹时展示店铺登记发货地至收货地址的预计路线；虚线不代表车辆实时位置或实际行驶轨迹。</view>
      </view>
      <view class="log-card muted">
        <view>无法识别点位或地图不可用时，以已核实的运单和文字轨迹为准，不生成虚假车辆位置。</view>
        <view class="boundary">物流签收不等于买家确认收货；退货送达也不等于入库验收或退款完成。</view>
        <s-service-entry resource-type="ORDER" :resource-id="state.id" label="联系订单客服核实物流" />
      </view>
    </view>
  </s-layout>
</template>

<script setup>
  import { onLoad, onShow, onHide } from '@dcloudio/uni-app';
  import { computed, reactive, onScopeDispose, nextTick } from 'vue';
  import OrderApi from '@/sheep/api/trade/order';
  import { useTenantTask } from '@/sheep/hooks/useTenantTask';
  import { normalizeLogistics, createLatestTask, fulfillmentTime, logisticsMapQueries, logisticsReceiverPoint } from '@/sheep/helper/fulfillment-view.mjs';
  import { isAmapConfigured, loadAmap } from '@/sheep/helper/amap-loader.mjs';

  const state = reactive({ id: '', info: null, order: null, error: '', loading: false, expired: false, nextRefresh: 0, now: Date.now(), mapReady: false, mapMessage: '正在整理物流节点…' });
  const task = useTenantTask(() => { state.expired = true; state.info = null; state.error = ''; state.loading = false; });
  const latest = createLatestTask(task.current);
  const remaining = computed(() => Math.max(0, Math.ceil((state.nextRefresh - state.now) / 1000)));
  let timer;
  let amap, routeMap, mapRevision = 0, renderedMapSignature = '';
  function stop() { clearInterval(timer); timer = null; }
  function tick() { state.now = Date.now(); }
  function destroyMap() { mapRevision++; if (routeMap) routeMap.destroy(); routeMap = undefined; amap = undefined; state.mapReady = false; }
  function geocode(geocoder, query) {
    return new Promise(resolve => {
      let settled = false, timer;
      const finish = value => { if (!settled) { settled = true; clearTimeout(timer); resolve(value); } };
      timer = setTimeout(() => finish(null), 8000);
      geocoder.getLocation(query, (status, result) => {
        const location = result.geocodes?.[0]?.location;
        finish(status === 'complete' && location ? [location.getLng(), location.getLat()] : null);
      });
    });
  }
  async function renderRouteMap(current = task.capture()) {
    const signature = [
      ...(state.info?.events || []).map(item => `${item.time || ''}|${item.content || ''}`),
      state.order?.receiverLongitude,
      state.order?.receiverLatitude,
      state.order?.receiverAreaName,
      state.order?.receiverDetailAddress,
      state.order?.dispatchOriginAddress,
    ].join('|');
    if (signature === renderedMapSignature) return;
    destroyMap();
    const revision = mapRevision, queries = logisticsMapQueries(state.info?.events || []);
    const receiver = logisticsReceiverPoint(state.order);
    const receiverAddress = [state.order?.receiverAreaName, state.order?.receiverDetailAddress].filter(Boolean).join(' ');
    const originAddress = String(state.order?.dispatchOriginAddress || '').trim();
    if (!queries.length && !originAddress) { state.mapMessage = '店铺未登记发货地址，暂不能绘制预计物流路线。'; return; }
    // #ifdef H5
    if (!isAmapConfigured()) { state.mapMessage = '地图服务未配置，文字物流轨迹不受影响。'; return; }
    try {
      amap = await loadAmap();
      if (!current() || revision !== mapRevision) return;
      const geocoder = new amap.Geocoder({ radius: 1000 }), points = [];
      if (originAddress) {
        const position = await geocode(geocoder, originAddress);
        if (position && current() && revision === mapRevision) points.push({ position, label: '店铺发货地' });
      }
      const located = await Promise.all(queries.map(async item => ({ item, position: await geocode(geocoder, item.query) })));
      if (!current() || revision !== mapRevision) return;
      for (const { item, position } of located) {
        if (position && !points.some(point => point.position[0] === position[0] && point.position[1] === position[1])) points.push({ position, label: item.label });
      }
      if (receiver && !points.some(point => point.position[0] === receiver[0] && point.position[1] === receiver[1])) points.push({ position: receiver, label: '收货地址' });
      else if (!receiver && receiverAddress) {
        const position = await geocode(geocoder, receiverAddress);
        if (position && current() && revision === mapRevision && !points.some(point => point.position[0] === position[0] && point.position[1] === position[1])) points.push({ position, label: '收货地址（文字定位）' });
      }
      if (!current() || revision !== mapRevision) return;
      if (points.length < 2) {
        state.mapMessage = '暂未获得完整的发货地和收货地坐标，已停止地图加载；文字地址和物流轨迹不受影响。';
        renderedMapSignature = signature;
        return;
      }
      state.mapReady = true; await nextTick();
      if (!current() || revision !== mapRevision) return;
      const instance = new amap.Map('h5-amap-logistics-route', { zoom: 5, center: [108.93977, 34.341574], viewMode: '2D' });
      routeMap = instance;
      const overlays = points.map((point, index) => new amap.Marker({ position: point.position, title: `${index + 1}. ${point.label}` }));
      if (points.length > 1) overlays.push(new amap.Polyline({ path: points.map(point => point.position), strokeColor: '#208879', strokeWeight: 5, strokeOpacity: 0.8, lineJoin: 'round', strokeStyle: 'dashed' }));
      instance.add(overlays); instance.setFitView(overlays, false, [45, 45, 45, 45], 12);
      renderedMapSignature = signature;
      state.mapMessage = points.length > 1
        ? (queries.length ? `已展示 ${points.length} 个可定位节点。` : '已展示店铺发货地至收货地址的预计路线，不代表承运商实际行驶轨迹。')
        : '仅识别到一个点位，暂不能形成物流线路。';
    } catch { if (current() && revision === mapRevision) state.mapMessage = '物流地图加载失败，文字轨迹仍可正常查看。'; }
    // #endif
  }
  const formatTime = fulfillmentTime;
  async function load() {
    tick();
    if (!task.current() || !state.id || state.loading || remaining.value > 0) return;
    const current = latest.begin();
    state.loading = true; state.error = '';
    try {
      const [logisticsResult, orderResult] = await Promise.allSettled([
        OrderApi.getLogistics(state.id), state.order ? Promise.resolve(null) : OrderApi.getOrderDetail(state.id),
      ]);
      if (!current()) return;
      if (orderResult.status === 'fulfilled' && orderResult.value?.code === 0 && String(orderResult.value.data?.id) === state.id) state.order = orderResult.value.data;
      if (logisticsResult.status !== 'fulfilled') throw new Error('unavailable');
      const response = logisticsResult.value;
      if (response?.code !== 0) throw new Error('unavailable');
      state.info = normalizeLogistics(response.data, state.id);
      state.nextRefresh = Date.now() + state.info.refreshAfterSeconds * 1000;
      await renderRouteMap(current);
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
  onShow(() => { tick(); stop(); timer = setInterval(tick, 1000); if (state.id && !state.info && !state.loading) load(); else if (state.info) void renderRouteMap(); });
  onHide(() => { latest.invalidate(); task.invalidate(); state.loading = false; stop(); renderedMapSignature = ''; destroyMap(); });
  onScopeDispose(() => { stop(); destroyMap(); });
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
  .route-map { width: 100%; height: 520rpx; margin-top: 18rpx; border: 1rpx solid #dce7e4; border-radius: 10rpx; background: #f2f5f4; }
  .map-boundary { margin-top: 14rpx; color: #7a5a18; font-size: 24rpx; line-height: 1.7; }
</style>
