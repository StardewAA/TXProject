<template>
  <s-layout title="跨店商品对比">
    <view class="compare-page">
      <view v-if="ui.expired" class="card notice">登录账号已变化，请返回商品列表重新进入。
        <button class="primary-button" size="mini" @tap="browse">返回商品列表</button>
      </view>
      <template v-else>
        <view class="compare-hero">
          <view class="hero-topline"><text>MATERIAL COMPARE</text><text>{{ state.list.skuIds.length }}/4</text></view>
          <view class="hero-title">材料规格对比</view>
          <view class="hero-copy">将不同店铺的具体规格放在一起，快速查看价格、参数和采购条件。</view>
          <view class="hero-steps">
            <view class="hero-step active"><text>1</text><view>选规格</view></view>
            <view class="step-line" :class="{ active: state.list.skuIds.length >= 2 }"></view>
            <view class="hero-step" :class="{ active: state.list.skuIds.length >= 2 }"><text>2</text><view>看差异</view></view>
            <view class="step-line" :class="{ active: !!ui.result && !dirty }"></view>
            <view class="hero-step" :class="{ active: !!ui.result && !dirty }"><text>3</text><view>核费用</view></view>
          </view>
          <view class="hero-actions"><button class="light-button" size="mini" @tap="browse">继续选品</button>
            <button class="ghost-button" size="mini" :disabled="busy" @tap="load">刷新清单</button></view>
        </view>
        <view class="sync-strip">
          <view><text class="sync-dot"></text>{{ state.authenticated ? '账号清单已开启双端同步' : '当前使用访客临时清单' }}</view>
          <text v-if="state.authenticated">版本 {{ state.list.version }}</text>
          <button v-else class="text-button" size="mini" @tap="login">登录同步</button>
        </view>
        <view class="card intro-card">
          <view class="heading">选规格，再比较</view>
          <view class="muted">仅比较当前商城 2—4 个具体 SKU。相同名称不代表相同规格，系统不会直接按最低价推荐。</view>
          <view v-if="state.authenticated && state.guest.skuIds.length" class="notice">
            发现当前商城 {{ state.guest.skuIds.length }} 个访客规格，是否合并？不会自动覆盖已保存清单。
            <view class="actions"><button class="primary-button" size="mini" :disabled="busy || !state.ready" @tap="merge">确认合并</button>
              <button class="subtle-button" size="mini" :disabled="busy" @tap="discard">放弃访客清单</button></view>
          </view>
          <view v-if="state.error || ui.error" class="error" role="alert">{{ state.error || ui.error }}</view>
          <view v-if="busy" class="loading-line"><text></text>正在处理…</view>
        </view>
        <view v-if="state.ready && !state.list.skuIds.length" class="card empty-card"><view class="empty-symbol">＋</view><view class="heading">对比清单还是空的</view><view class="muted">请在商品详情的具体规格旁点击“加入对比”。</view><button class="primary-button" size="mini" @tap="browse">去挑选材料</button></view>
        <view v-if="state.ready && state.list.skuIds.length" class="card setting-card">
          <view class="section-label">COMPARE SETTINGS</view>
          <view class="heading">设置比较条件</view>
          <view class="field"><text>每项采购份数</text><input v-model="ui.quantity" type="number" maxlength="3" placeholder="1—999" :disabled="busy" /></view>
          <view class="muted">份数是每个 SKU 的销售数量，不代表相同 kg、L 或平方米；未确认包装时不换算。</view>
          <view class="field preference-field"><text>比较偏好</text><picker :range="preferenceLabels" :value="preferenceIndex" :disabled="busy" @change="changePreference"><view class="picker">{{ preferenceLabels[preferenceIndex] }} <text>›</text></view></picker></view>
          <button class="primary-button wide-button" size="mini" :disabled="busy" @tap="apply">保存设置并比较</button>
          <view v-if="dirty" class="notice">数量或偏好已修改，请先保存并重新比较。</view>
          <view class="difference-filter"><view><text>仅看原始属性差异</text><text>隐藏相同项，减少阅读干扰</text></view><switch :checked="ui.differencesOnly" @change="ui.differencesOnly = $event.detail.value" color="#0e8875" /></view>
        </view>
        <view v-if="ui.result && !dirty" class="card result-card">
          <view class="recommend-tag">选型提示</view>
          <view class="heading">比较说明</view>
          <view class="result-reason">{{ ui.result.recommendationReason }}</view>
          <view v-for="(warning, i) in ui.result.warnings || []" :key="i" class="muted">{{ warning }}</view>
          <view class="muted">策略 {{ ui.result.strategyVersion }} · {{ formatTime(ui.result.calculatedAt) }}</view>
        </view>
        <view v-if="ui.result && !dirty" class="comparison-caption"><view><text>参数对照</text><text>左右滑动查看全部商品</text></view><text>{{ ui.result.items.length }} 项</text></view>
        <scroll-view v-if="ui.result && !dirty" scroll-x class="comparison-scroll">
          <view class="comparison-table">
            <view v-for="(item, index) in ui.result.items" :key="item.skuId" class="product-column">
              <view class="product-number">0{{ index + 1 }}</view>
              <view class="product-visual">材</view>
              <view class="product-heading">{{ item.available ? item.spuName : '商品暂不可用' }}</view>
              <view class="sku-tag">SKU {{ item.skuId }}</view>
              <template v-if="item.available">
                <view class="shop-name">{{ item.shopName || '店铺信息待补充' }}</view>
                <view class="price"><text>{{ money(item.price) }}</text><text>SKU 标价</text></view>
                <view class="standard-message">{{ item.standardMessage }}</view>
                <view v-for="row in rows" :key="row.name" class="spec" :class="{ different: row.different }"><text>{{ row.name }}</text><view>{{ row.values[index] }}</view></view>
                <view v-if="!rows.length" class="muted">{{ ui.differencesOnly ? '未发现原始属性文本差异；不代表同款。' : '规格资料待补充' }}</view>
                <view v-for="(unknown, j) in item.unknowns || []" :key="j" class="muted">待确认：{{ unknown }}</view>
                <view class="stock-message">库存状态将在结算时再次核验</view>
                <button class="subtle-button wide-button" size="mini" :disabled="busy" @tap="detail(item)">查看商品</button>
                <button class="primary-button wide-button" size="mini" :disabled="busy" @tap="addCart(item)">重新核验并加购</button>
              </template>
              <view v-else class="notice">{{ item.unavailableReason || '商品已下架或当前商城不可见' }}</view>
              <button class="remove-button wide-button" size="mini" :disabled="busy" @tap="remove(item.skuId)">移出对比</button>
            </view>
          </view>
        </scroll-view>
        <view v-if="state.ready && state.list.skuIds.length && !ui.result" class="card">
          <view class="heading">已选规格</view><view v-for="id in state.list.skuIds" :key="id" class="selected-row"><text>SKU {{ id }}</text><button class="remove-button" size="mini" :disabled="busy" @tap="remove(id)">移出对比</button></view>
        </view>
        <view v-if="ui.result && !dirty" class="card quote-card">
          <view class="section-label">COST ESTIMATE</view>
          <view class="heading">按本人收货地址估价</view>
          <view class="muted">每个 SKU 独立调用现有结算计价，不合单、不下单、不锁库存。无地址或费用不确定时不会按零费用处理。</view>
          <button v-if="!state.authenticated" class="primary-button wide-button" size="mini" @tap="login">登录后估价</button>
          <template v-else>
            <view v-if="ui.addressError" class="error">{{ ui.addressError }}<button class="subtle-button" size="mini" :disabled="busy" @tap="loadAddresses">重试地址</button></view>
            <picker :range="addressLabels" :value="addressIndex" :disabled="busy" @change="chooseAddress"><view class="picker address-picker">{{ addressLabels[addressIndex] }} ›</view></picker>
            <view class="actions quote-actions"><button class="subtle-button" size="mini" @tap="addresses">管理地址</button><button class="primary-button" size="mini" :disabled="busy" @tap="quote">获取结算预估</button></view>
            <view v-if="ui.quoteError" class="error">{{ ui.quoteError }}</view>
            <template v-if="ui.quote">
              <view v-for="item in ui.quote.items" :key="item.skuId" class="quote-item">
                <view class="quote-title">SKU {{ item.skuId }}<text>{{ item.message }}</text></view>
                <template v-if="item.status === 'AVAILABLE' && item.amountsComplete">
                  <view class="quote-line"><text>商品金额</text><text>{{ money(item.goodsAmount) }}</text></view>
                  <view class="quote-line"><text>优惠金额</text><text>{{ money(item.discountAmount) }}</text></view>
                  <view class="quote-line"><text>运费</text><text>{{ money(item.shippingFee) }}</text></view>
                  <view class="quote-total">结算预估 {{ money(item.totalAmount) }}</view>
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
.compare-page { padding:20rpx 20rpx 44rpx; color:#193b38; background:linear-gradient(180deg,#eef5f4 0,#f6f8f8 440rpx); min-height:100vh; }
.compare-hero { position:relative; overflow:hidden; margin-bottom:18rpx; padding:34rpx 30rpx 28rpx; border-radius:28rpx; color:#fff; background:linear-gradient(135deg,#112e3a 0%,#155562 58%,#0b8a74 100%); box-shadow:0 20rpx 42rpx rgba(19,66,60,.2); }
.compare-hero::after { content:''; position:absolute; right:-96rpx; top:-108rpx; width:330rpx; height:330rpx; border:1rpx solid rgba(255,255,255,.12); border-radius:50%; box-shadow:0 0 0 54rpx rgba(255,255,255,.04),0 0 0 108rpx rgba(255,255,255,.025); }
.hero-topline,.hero-actions,.sync-strip,.comparison-caption,.selected-row,.quote-line,.quote-total { display:flex; align-items:center; justify-content:space-between; }
.hero-topline { position:relative; z-index:1; color:#9edfd3; font-size:19rpx; letter-spacing:3rpx; }
.hero-topline text:last-child { color:#fff; font-size:24rpx; font-weight:700; letter-spacing:0; }
.hero-title { position:relative; z-index:1; margin-top:20rpx; font-size:43rpx; font-weight:800; letter-spacing:2rpx; }
.hero-copy { position:relative; z-index:1; width:82%; margin-top:12rpx; color:#d5e8e5; font-size:24rpx; line-height:1.65; }
.hero-steps { position:relative; z-index:1; display:flex; align-items:flex-start; margin:32rpx 0 24rpx; }
.hero-step { display:flex; flex-direction:column; align-items:center; gap:7rpx; color:#90adad; font-size:20rpx; }
.hero-step text { display:flex; align-items:center; justify-content:center; width:42rpx; height:42rpx; border:1rpx solid rgba(255,255,255,.25); border-radius:50%; }
.hero-step.active { color:#fff; }
.hero-step.active text { border-color:#79d6c5; background:#0b8f79; box-shadow:0 0 0 7rpx rgba(51,190,166,.12); }
.step-line { flex:1; height:2rpx; margin:21rpx 13rpx 0; background:rgba(255,255,255,.16); }
.step-line.active { background:#58c8b4; }
.hero-actions { position:relative; z-index:1; justify-content:flex-start; gap:16rpx; }
.sync-strip { min-height:66rpx; margin-bottom:18rpx; padding:0 24rpx; border:1rpx solid #d3e2de; border-radius:18rpx; background:rgba(255,255,255,.88); color:#49645f; font-size:22rpx; }
.sync-strip>view { display:flex; align-items:center; }
.sync-dot { width:13rpx; height:13rpx; margin-right:11rpx; border-radius:50%; background:#0e937c; box-shadow:0 0 0 7rpx #e3f4f0; }
.card { box-sizing:border-box; background:#fff; margin-bottom:20rpx; border:1rpx solid #dfe9e6; border-radius:24rpx; padding:28rpx; font-size:27rpx; line-height:1.75; overflow-wrap:anywhere; box-shadow:0 10rpx 28rpx rgba(29,70,62,.055); }
.intro-card { padding-bottom:21rpx; }
.heading { color:#153c39; font-size:32rpx; font-weight:800; margin-bottom:12rpx; }
.section-label { color:#0e8875; font-size:18rpx; font-weight:700; letter-spacing:2rpx; }
.section-label+.heading { margin-top:3rpx; }
.muted { color:#6c7f7b; font-size:23rpx; margin:9rpx 0; }
.notice { background:#edf6f4; color:#315b53; padding:17rpx 19rpx; margin:14rpx 0; border:1rpx solid #d4e7e2; border-radius:14rpx; }
.error { color:#ad352c; margin:16rpx 0; padding:14rpx 17rpx; border-radius:12rpx; background:#fff3f1; }
.loading-line { display:flex; align-items:center; gap:12rpx; margin-top:14rpx; color:#56726d; font-size:23rpx; }
.loading-line text { width:14rpx; height:14rpx; border-radius:50%; background:#0d8b76; box-shadow:0 0 0 7rpx #e5f4f1; }
.actions,.field { display:flex; align-items:center; justify-content:space-between; gap:16rpx; margin:16rpx 0; flex-wrap:wrap; }
.field { flex-wrap:nowrap; padding:16rpx 18rpx; border-radius:15rpx; background:#f4f8f7; }
.field>text { flex:none; color:#35524d; font-size:24rpx; font-weight:700; }
.field input { width:180rpx; box-sizing:border-box; text-align:right; background:#fff; padding:11rpx 15rpx; border:1rpx solid #ccdcd8; border-radius:10rpx; }
.preference-field picker { flex:1; min-width:0; }
.picker { display:flex; align-items:center; justify-content:space-between; padding:15rpx 18rpx; background:#f1f6f4; margin:12rpx 0; border:1rpx solid #d4e2de; border-radius:12rpx; color:#304e49; font-size:23rpx; }
.preference-field .picker { margin:0; border:0; background:transparent; text-align:right; }
.difference-filter { display:flex; align-items:center; justify-content:space-between; margin-top:18rpx; padding-top:19rpx; border-top:1rpx solid #e3ebe9; }
.difference-filter>view { display:flex; flex-direction:column; }
.difference-filter>view text:first-child { font-size:25rpx; font-weight:700; }
.difference-filter>view text:last-child { margin-top:3rpx; color:#82918e; font-size:20rpx; }
button { box-sizing:border-box; margin:10rpx 0; border-radius:11rpx; font-size:24rpx; font-weight:700; }
button::after { border:0; }
.primary-button { border:1rpx solid #0b816e; background:#0d8875; color:#fff; }
.subtle-button { border:1rpx solid #c8dad6; background:#f2f7f6; color:#176d60; }
.light-button { min-width:170rpx; background:#fff; color:#0b695d; }
.ghost-button { min-width:170rpx; border:1rpx solid rgba(255,255,255,.42); background:rgba(255,255,255,.08); color:#fff; }
.text-button { margin:0; padding:0 8rpx; border:0; background:transparent; color:#0d806e; font-size:22rpx; }
.wide-button { width:100%; }
.remove-button { border:1rpx solid #ead8d4; background:#fff8f6; color:#a24b3f; }
.empty-card { padding:56rpx 28rpx; text-align:center; }
.empty-symbol { display:flex; align-items:center; justify-content:center; width:88rpx; height:88rpx; margin:0 auto 21rpx; border-radius:50%; background:#e7f3f0; color:#0c8c76; font-size:48rpx; }
.result-card { position:relative; overflow:hidden; padding-left:116rpx; background:linear-gradient(110deg,#eaf7f3,#fff); }
.recommend-tag { position:absolute; left:0; top:0; bottom:0; display:flex; align-items:center; width:82rpx; padding:0 13rpx; background:#0f7466; color:#fff; font-size:21rpx; font-weight:700; text-align:center; }
.result-reason { color:#294f49; font-size:26rpx; line-height:1.65; }
.comparison-caption { margin:28rpx 5rpx 15rpx; }
.comparison-caption>view { display:flex; flex-direction:column; }
.comparison-caption>view text:first-child { color:#153c39; font-size:31rpx; font-weight:800; }
.comparison-caption>view text:last-child { color:#7a8c88; font-size:21rpx; }
.comparison-caption>text { padding:6rpx 14rpx; border-radius:20rpx; background:#dceee9; color:#0d7968; font-size:20rpx; }
.comparison-scroll { margin:0 -20rpx 22rpx; width:calc(100% + 40rpx); }
.comparison-table { display:flex; align-items:stretch; gap:18rpx; padding:0 20rpx 18rpx; }
.product-column { position:relative; width:520rpx; min-width:520rpx; box-sizing:border-box; background:#fff; border:1rpx solid #dae7e3; border-radius:24rpx; padding:27rpx; font-size:25rpx; line-height:1.65; overflow:hidden; overflow-wrap:anywhere; box-shadow:0 12rpx 30rpx rgba(22,66,59,.07); }
.product-number { position:absolute; right:24rpx; top:21rpx; color:#9badaa; font-size:19rpx; letter-spacing:2rpx; }
.product-visual { display:flex; align-items:center; justify-content:center; width:86rpx; height:86rpx; margin-bottom:18rpx; border-radius:20rpx; background:linear-gradient(145deg,#dcedea,#edf6f4); color:#44857b; font-size:34rpx; font-weight:800; }
.product-heading { min-height:80rpx; color:#163c39; font-size:31rpx; font-weight:800; line-height:1.35; }
.sku-tag { display:inline-flex; margin:10rpx 0; padding:5rpx 12rpx; border-radius:8rpx; background:#eef3f2; color:#657975; font-size:20rpx; }
.shop-name { margin:8rpx 0; color:#536b66; }
.price { display:flex; align-items:baseline; justify-content:space-between; margin:17rpx 0; padding:16rpx 0; border-top:1rpx solid #e5ecea; border-bottom:1rpx solid #e5ecea; }
.price text:first-child { color:#b34c28; font-size:34rpx; font-weight:800; }
.price text:last-child { color:#83918f; font-size:20rpx; }
.standard-message,.stock-message { margin:12rpx 0; padding:13rpx 15rpx; border-radius:11rpx; background:#edf6f4; color:#37635c; font-size:22rpx; }
.stock-message { background:#f5f7f6; color:#62736f; }
.spec,.quote-item { padding:15rpx 3rpx; border-bottom:1rpx solid #e6ece9; }
.spec { display:grid; grid-template-columns:180rpx 1fr; gap:15rpx; }
.spec text { color:#768783; font-size:22rpx; }
.spec view { color:#263f3b; font-weight:600; }
.different { margin:0 -12rpx; padding-left:15rpx; padding-right:15rpx; border-radius:8rpx; background:#fff6df; }
.selected-row { padding:12rpx 0; border-bottom:1rpx solid #e5ecea; }
.selected-row button { margin:0; }
.address-picker { min-height:56rpx; }
.quote-actions { flex-wrap:nowrap; }
.quote-actions button { flex:1; }
.quote-item { margin-top:18rpx; padding:20rpx; border:1rpx solid #dce8e4; border-radius:15rpx; background:#f9fbfa; }
.quote-title { display:flex; justify-content:space-between; gap:12rpx; margin-bottom:13rpx; color:#173d39; font-weight:800; }
.quote-title text { color:#6c7f7b; font-size:21rpx; font-weight:400; }
.quote-line { color:#60736e; font-size:23rpx; }
.quote-line text:last-child { color:#273e3a; font-weight:600; }
.quote-total { justify-content:flex-end; margin-top:12rpx; padding-top:12rpx; border-top:1rpx solid #dfe8e6; color:#b64b26; font-size:30rpx; font-weight:800; }
</style>
