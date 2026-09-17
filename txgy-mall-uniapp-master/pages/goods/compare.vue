<template>
  <s-layout title="跨店商品对比">
    <view class="compare-page">
      <view v-if="ui.expired" class="card notice">登录账号已变化，请返回商品列表重新进入。
        <button size="mini" @tap="browse">返回商品列表</button>
      </view>
      <template v-else>
        <view class="card">
          <view class="heading">选规格，再比较</view>
          <view class="muted">仅比较当前商城 2—4 个 SKU。相同名称不代表同款；资料不足时不做最低价推荐。</view>
          <view class="actions"><button size="mini" @tap="browse">继续选品</button>
            <button size="mini" :disabled="busy" @tap="load">刷新清单</button></view>
          <view v-if="!state.authenticated" class="muted">当前为本商城的访客临时清单。登录后可确认合并，并在 PC/H5 同步。
            <button size="mini" @tap="login">登录同步</button></view>
          <view v-else class="muted">清单、数量和偏好由同账号双端共用；版本 {{ state.list.version }}。</view>
          <view v-if="state.authenticated && state.guest.skuIds.length" class="notice">
            发现当前商城 {{ state.guest.skuIds.length }} 个访客规格，是否合并？不会自动覆盖已保存清单。
            <view class="actions"><button size="mini" :disabled="busy || !state.ready" @tap="merge">确认合并</button>
              <button size="mini" :disabled="busy" @tap="discard">放弃访客清单</button></view>
          </view>
          <view v-if="state.error || ui.error" class="error" role="alert">{{ state.error || ui.error }}</view>
          <view v-if="busy" class="muted">正在处理…</view>
        </view>
        <view v-if="state.ready && !state.list.skuIds.length" class="card">清单为空，请在商品详情的具体规格旁点击“加入对比”。</view>
        <view v-if="state.ready && state.list.skuIds.length" class="card">
          <view class="field"><text>每项采购份数</text><input v-model="ui.quantity" type="number" maxlength="3" placeholder="1—999" :disabled="busy" /></view>
          <view class="muted">份数是每个 SKU 的销售数量，不代表相同 kg、L 或平方米；未确认包装时不换算。</view>
          <view class="field"><text>比较偏好</text><picker :range="preferenceLabels" :value="preferenceIndex" :disabled="busy" @change="changePreference"><view class="picker">{{ preferenceLabels[preferenceIndex] }} ›</view></picker></view>
          <button size="mini" :disabled="busy" @tap="apply">保存设置并比较</button>
          <view v-if="dirty" class="notice">数量或偏好已修改，请先保存并重新比较。</view>
          <view class="actions"><text>仅看原始属性差异</text><switch :checked="ui.differencesOnly" @change="ui.differencesOnly = $event.detail.value" color="#187768" /></view>
        </view>
        <view v-if="ui.result && !dirty" class="card">
          <view class="heading">比较说明</view>
          <view class="notice">{{ ui.result.recommendationReason }}</view>
          <view v-for="(warning, i) in ui.result.warnings || []" :key="i" class="muted">{{ warning }}</view>
          <view class="muted">策略 {{ ui.result.strategyVersion }} · {{ formatTime(ui.result.calculatedAt) }}</view>
        </view>
        <scroll-view v-if="ui.result && !dirty" scroll-x class="comparison-scroll">
          <view class="comparison-table">
            <view v-for="(item, index) in ui.result.items" :key="item.skuId" class="product-column">
              <view class="heading">{{ item.available ? item.spuName : '商品暂不可用' }}</view>
              <view class="muted">SKU {{ item.skuId }}</view>
              <template v-if="item.available">
                <view>店铺：{{ item.shopName || '待补充' }}</view>
                <view class="price">SKU 标价 {{ money(item.price) }}</view>
                <view class="muted">该标价不是含运费到手总价。</view>
                <view class="notice">{{ item.standardMessage }}</view>
                <view v-for="row in rows" :key="row.name" class="spec" :class="{ different: row.different }"><text>{{ row.name }}</text><view>{{ row.values[index] }}</view></view>
                <view v-if="!rows.length" class="muted">{{ ui.differencesOnly ? '未发现原始属性文本差异；不代表同款。' : '规格资料待补充' }}</view>
                <view v-for="(unknown, j) in item.unknowns || []" :key="j" class="muted">待确认：{{ unknown }}</view>
                <view class="muted">{{ item.stockMessage }}</view>
                <button size="mini" :disabled="busy" @tap="detail(item)">查看商品</button>
                <button size="mini" :disabled="busy" @tap="addCart(item)">重新核验并加购</button>
              </template>
              <view v-else class="notice">{{ item.unavailableReason || '商品已下架或当前商城不可见' }}</view>
              <button size="mini" :disabled="busy" @tap="remove(item.skuId)">移出对比</button>
            </view>
          </view>
        </scroll-view>
        <view v-if="state.ready && state.list.skuIds.length && !ui.result" class="card">
          <view v-for="id in state.list.skuIds" :key="id" class="actions">SKU {{ id }}<button size="mini" :disabled="busy" @tap="remove(id)">移出对比</button></view>
        </view>
        <view v-if="ui.result && !dirty" class="card">
          <view class="heading">按本人收货地址估价</view>
          <view class="muted">每个 SKU 独立调用现有结算计价，不合单、不下单、不锁库存。无地址或费用不确定时不会按零费用处理。</view>
          <button v-if="!state.authenticated" size="mini" @tap="login">登录后估价</button>
          <template v-else>
            <view v-if="ui.addressError" class="error">{{ ui.addressError }}<button size="mini" :disabled="busy" @tap="loadAddresses">重试地址</button></view>
            <picker :range="addressLabels" :value="addressIndex" :disabled="busy" @change="chooseAddress"><view class="picker">{{ addressLabels[addressIndex] }} ›</view></picker>
            <view class="actions"><button size="mini" @tap="addresses">管理地址</button><button size="mini" :disabled="busy" @tap="quote">获取结算预估</button></view>
            <view v-if="ui.quoteError" class="error">{{ ui.quoteError }}</view>
            <template v-if="ui.quote">
              <view v-for="item in ui.quote.items" :key="item.skuId" class="quote-item">
                <view>SKU {{ item.skuId }} · {{ item.message }}</view>
                <template v-if="item.status === 'AVAILABLE' && item.amountsComplete">
                  <view>商品金额 {{ money(item.goodsAmount) }} / 优惠 {{ money(item.discountAmount) }}</view>
                  <view>运费 {{ money(item.shippingFee) }} / 结算预估 {{ money(item.totalAmount) }}</view>
                </template>
                <view v-else class="muted">费用未完整确认，不展示到手总额。</view>
              </view>
              <view v-for="(warning, i) in ui.quote.warnings || []" :key="i" class="muted">{{ warning }}</view>
              <view class="muted">预估时间：{{ formatTime(ui.quote.calculatedAt) }}，以实际结算再次核价为准。</view>
            </template>
          </template>
        </view>
      </template>
    </view>
  </s-layout>
</template>

<script setup>
import { computed, reactive, watch } from 'vue';
import { onShow, onHide } from '@dcloudio/uni-app';
import sheep from '@/sheep';
import ComparisonApi from '@/sheep/api/product/comparison';
import AddressApi from '@/sheep/api/member/address';
import CartApi from '@/sheep/api/trade/cart';
import { comparison, comparisonState as state, comparisonData } from '@/sheep/helper/comparison-runtime';
import { comparisonList, comparisonResult, comparisonQuote, comparisonRows, comparisonMoney as money, comparisonPreferences } from '@/sheep/helper/comparison.mjs';
import { useTenantTask } from '@/sheep/hooks/useTenantTask';
import { createLatestTask, fulfillmentTime as formatTime } from '@/sheep/helper/fulfillment-view.mjs';

const ui = reactive({ quantity: '1', preference: 'PRICE', differencesOnly: false, result: null, quote: null,
  addresses: [], addressId: null, addressError: '', error: '', quoteError: '', loading: false, quoting: false, adding: false, expired: false });
const task = useTenantTask(() => { latest.invalidate(); ui.expired = true; ui.result = null; ui.quote = null; ui.addresses = []; ui.error = ''; ui.addressError = ''; ui.quoteError = ''; });
const latest = createLatestTask(task.current);
let addressGeneration = 0;
const busy = computed(() => ui.loading || ui.quoting || ui.adding || state.loading || state.saving);
const dirty = computed(() => ui.quantity !== String(state.list.quantity) || ui.preference !== state.list.preference);
const rows = computed(() => comparisonRows(ui.result?.items || [], ui.differencesOnly));
const preferenceLabels = ['满足规格后价格优先', '交付优先', '综合参考'];
const preferenceIndex = computed(() => Math.max(0, comparisonPreferences.indexOf(ui.preference)));
const addressLabels = computed(() => ['请选择本人收货地址（不默认选择）', ...ui.addresses.map(address => `${address.areaName || ''} ${address.detailAddress || ''}`)]);
const addressIndex = computed(() => ui.addresses.findIndex(address => address.id === ui.addressId) + 1);
const browse = () => sheep.$router.go('/pages/goods/list');
const login = () => sheep.$router.go('/pages/index/login');
const addresses = () => sheep.$router.go('/pages/user/address/list');
const detail = item => { if (task.current() && item.available) sheep.$router.go('/pages/goods/index', { id: item.spuId }); };
function changePreference(event) { ui.preference = comparisonPreferences[Number(event.detail.value)] || 'PRICE'; }
function chooseAddress(event) { ui.addressId = ui.addresses[Number(event.detail.value) - 1]?.id || null; ui.quote = null; ui.quoteError = ''; }
watch(() => [ui.quantity, ui.preference], () => { latest.invalidate(); ui.quote = null; ui.quoteError = ''; ui.quoting = false; });
watch(() => state.ready, ready => { if (!ready) { latest.invalidate(); ui.result = null; ui.quote = null; } });
function payload() { const { skuIds, quantity, preference } = comparisonList(state.list); return { skuIds, quantity, preference }; }
async function calculate() {
  const current = latest.begin(), value = payload();
  ui.result = null; ui.quote = null; ui.error = '';
  if (!value.skuIds.length) return;
  try {
    const response = await ComparisonApi.calculate(value);
    if (!current()) return;
    ui.result = comparisonResult(comparisonData(response), value);
  } catch (error) { if (current()) ui.error = error?.message || '对比加载失败，请重试'; }
}
async function loadAddresses() {
  const current = task.capture(), revision = ++addressGeneration;
  if (!state.authenticated || !task.current()) return;
  ui.addressError = '';
  try {
    const data = comparisonData(await AddressApi.getAddressList());
    if (!current() || revision !== addressGeneration) return;
    ui.addresses = Array.isArray(data) ? data : [];
    if (!ui.addresses.some(address => address.id === ui.addressId)) { ui.addressId = null; ui.quote = null; }
  } catch { if (current() && revision === addressGeneration) { ui.addresses = []; ui.addressId = null; ui.quote = null; ui.addressError = '地址加载失败，请重试'; } }
}
async function load() {
  if (!task.current() || busy.value) return;
  const current = task.capture();
  ui.loading = true; ui.error = ''; ui.result = null; ui.quote = null;
  try {
    if (!await comparison.load() || !current()) return;
    ui.quantity = String(state.list.quantity); ui.preference = state.list.preference;
    await Promise.resolve(); // let input watchers invalidate old results before starting the new request
    if (!current()) return;
    await Promise.all([calculate(), loadAddresses()]);
  } catch (error) { if (current()) ui.error = error?.message || '加载失败'; }
  finally { if (current()) ui.loading = false; }
}
async function edit(run) {
  if (!task.current() || busy.value) return;
  const current = task.capture(); ui.loading = true; ui.result = null; ui.quote = null; ui.error = '';
  try {
    if (!await run() || !current()) return;
    ui.quantity = String(state.list.quantity); ui.preference = state.list.preference;
    await Promise.resolve();
    if (current()) await calculate();
  } catch (error) { if (current()) ui.error = error?.message || '保存失败'; }
  finally { if (current()) ui.loading = false; }
}
const remove = id => edit(() => comparison.save({ ...state.list, skuIds: state.list.skuIds.filter(skuId => skuId !== id) }));
const apply = () => edit(() => comparison.save({ ...state.list, quantity: Number(ui.quantity), preference: ui.preference }));
const merge = () => edit(() => comparison.merge());
function discard() { if (task.current()) { try { comparison.discardGuest(); } catch (error) { ui.error = error.message; } } }
async function quote() {
  if (!task.current() || busy.value || dirty.value || !ui.result || !state.authenticated) return;
  const current = latest.begin(), value = payload(), addressId = ui.addressId;
  ui.quoting = true; ui.quote = null; ui.quoteError = '';
  try {
    const data = comparisonData(await ComparisonApi.quote({ skuIds: value.skuIds, quantity: value.quantity, ...(addressId ? { addressId } : {}) }));
    if (!current()) return;
    ui.quote = comparisonQuote(data, value, addressId);
  } catch (error) { if (current()) ui.quoteError = error?.message || '估价失败，请重试'; }
  finally { if (current()) ui.quoting = false; }
}
async function addCart(item) {
  if (!task.current() || busy.value || dirty.value || !item.available) return;
  if (!state.authenticated) { login(); return; }
  const current = task.capture(); ui.adding = true; ui.error = '';
  try {
    comparisonData(await CartApi.addCart({ skuId: item.skuId, count: state.list.quantity }));
    if (current()) await sheep.$store('cart').getList();
  } catch (error) { if (current()) ui.error = error?.message || '加购失败，请查看商品状态'; }
  finally { if (current()) ui.adding = false; }
}
onShow(load);
onHide(() => { latest.invalidate(); task.invalidate(); addressGeneration++; ui.loading = false; ui.quoting = false; ui.adding = false; ui.result = null; ui.quote = null; });
</script>

<style scoped>
.compare-page { padding:20rpx; color:#243c38; }
.card { background:white; margin-bottom:20rpx; border-radius:14rpx; padding:26rpx; font-size:27rpx; line-height:1.75; overflow-wrap:anywhere; }
.heading { font-size:32rpx; font-weight:bold; margin-bottom:14rpx; }
.muted { color:#68766f; font-size:24rpx; margin:10rpx 0; }
.notice { background:#edf5f3; color:#315b53; padding:16rpx; margin:14rpx 0; border-radius:8rpx; }
.error { color:#ad352c; margin:16rpx 0; }
.actions,.field { display:flex; align-items:center; justify-content:space-between; gap:16rpx; margin:16rpx 0; flex-wrap:wrap; }
.field input { width:160rpx; background:#f2f5f4; padding:12rpx; border:1px solid #ccd8d4; }
.picker { padding:18rpx; background:#f1f5f4; margin:12rpx 0; }
button { margin:12rpx 0; color:#186d60; }
.comparison-scroll { margin-bottom:20rpx; }
.comparison-table { display:flex; align-items:stretch; gap:16rpx; padding-bottom:10rpx; }
.product-column { width:490rpx; min-width:490rpx; box-sizing:border-box; background:white; border-radius:14rpx; padding:24rpx; font-size:26rpx; line-height:1.7; overflow-wrap:anywhere; }
.price { font-size:30rpx; color:#b54b25; margin-top:14rpx; }
.spec,.quote-item { padding:14rpx 0; border-bottom:1px solid #e6ece9; }
.spec text { color:#69756f; font-size:24rpx; }
.different { background:#fff8e8; }
</style>
