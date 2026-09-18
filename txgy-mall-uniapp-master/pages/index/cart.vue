<template>
  <s-layout :bgStyle="{ color: '#fff' }" tabbar="/pages/index/cart" title="购物车">
    <s-service-entry label="平台购物咨询（不代表任一店铺）" :disabled="state.checkoutBusy || state.deleteBusy || cart.busy || cart.loading || state.expired" />
    <view v-if="state.expired || cart.loadError || cart.operationError" class="cart-notice" role="alert">
      <text>{{ state.expired ? '登录状态已变化，请重新加载购物车' : cart.loadError || cart.operationError }}</text>
      <button size="mini" :disabled="cart.busy || cart.loading" @tap="getCartList">重试</button>
    </view>
    <view v-else-if="cart.loading" class="cart-notice">正在加载购物车…</view>
    <s-empty
      v-if="cart.loaded && !cart.loading && !cart.loadError && !state.expired && state.list.length === 0"
      icon="/static/cart-empty.png"
      text="购物车空空如也,快去逛逛吧~"
    />

    <!-- 头部 -->
    <view v-if="state.list.length" class="cart-box ss-flex ss-flex-col ss-row-between">
      <view class="cart-header ss-flex ss-col-center ss-row-between ss-p-x-30">
        <view class="header-left ss-flex ss-col-center ss-font-26">
          共
          <text class="goods-number ui-TC-Main ss-flex">{{ state.list.length }}</text>
          件商品
        </view>
        <view class="header-right">
          <button v-if="state.editMode" class="ss-reset-button" @tap="onChangeEditMode(false)">
            取消
          </button>
          <button v-else class="ss-reset-button ui-TC-Main" @tap="onChangeEditMode(true)">
            编辑
          </button>
        </view>
      </view>
      <!-- 内容 -->
      <view class="cart-content ss-flex-1 ss-p-x-30 ss-m-b-40">
        <view v-for="item in state.list" :key="item?.id || item?.sku?.id" class="goods-box ss-r-10 ss-m-b-14">
          <view class="ss-flex ss-col-center">
            <label class="check-box ss-flex ss-col-center ss-p-l-10" @tap="onSelectSingle(item?.id)">
              <radio
                :checked="state.selectedIds.includes(item?.id)"
                :disabled="blocked || (!state.editMode && !isAvailable(item))"
                color="var(--ui-BG-Main)"
                style="transform: scale(0.8)"
                @tap.stop="onSelectSingle(item?.id)"
              />
            </label>
            <view v-if="!isAvailable(item) && !state.editMode" class="down-box">
              商品暂不可购买，可在编辑中删除
            </view>
            <s-goods-item
              :img="item.spu?.picUrl || item.sku?.picUrl || ''"
              :price="item.sku?.price || 0"
              :skuText="getSkuText(item)"
              :title="item.spu?.name || item.sku?.name || '商品'"
              :titleWidth="400"
              priceColor="#FF3000"
            >
              <template v-if="!state.editMode && isAvailable(item)" v-slot:tool>
                <su-number-box
                  :key="`${item.id}-${cart.revision}`"
                  :modelValue="item.count"
                  :disabled="blocked"
                  :max="item.sku?.stock || item.spu?.stock || 0"
                  :min="1"
                  :step="1"
                  @change="onNumberChange($event, item)"
                />
              </template>
            </s-goods-item>
          </view>
          <s-service-entry resource-type="PRODUCT" :resource-id="item.spu?.id || item.sku?.spuId" :disabled="state.checkoutBusy || state.deleteBusy || cart.busy || cart.loading || state.expired" />
        </view>
      </view>
      <!-- 底部 -->
      <su-fixed v-if="state.list.length > 0" :isInset="false" :val="48" bottom placeholder>
        <view class="cart-footer ss-flex ss-col-center ss-row-between ss-p-x-30 border-bottom">
          <view class="footer-left ss-flex ss-col-center">
            <label class="check-box ss-flex ss-col-center ss-p-r-30" @tap="onSelectAll">
              <radio
                :checked="state.isAllSelected"
                :disabled="blocked"
                color="var(--ui-BG-Main)"
                style="transform: scale(0.8)"
                @tap.stop="onSelectAll"
              />
              <view class="ss-m-l-8"> 全选</view>
            </label>
            <text>合计：</text>
            <view class="text-price price-text">
              {{ fen2yuan(state.totalPriceSelected) }}
            </view>
          </view>
          <view class="footer-right">
            <button
              v-if="state.editMode"
              :disabled="blocked"
              class="ss-reset-button ui-BG-Main-Gradient pay-btn ui-Shadow-Main"
              @tap="onDelete"
            >
              删除
            </button>
            <button
              v-else
              :disabled="blocked"
              class="ss-reset-button ui-BG-Main-Gradient pay-btn ui-Shadow-Main"
              @tap="onConfirm"
            >
              去结算
              {{ state.selectedIds?.length ? `(${state.selectedIds.length})` : '' }}
            </button>
          </view>
        </view>
      </su-fixed>
    </view>
  </s-layout>
</template>

<script setup>
  import sheep from '@/sheep';
  import { onShow, onHide } from '@dcloudio/uni-app';
  import SpuApi from '@/sheep/api/product/spu';
  import { computed, reactive, onUnmounted } from 'vue';
  import { fen2yuan } from '@/sheep/hooks/useGoods';
  import { isEmpty } from '@/sheep/helper/utils';
  import { captureTenantOperation, isTenantOperationCurrent } from '@/sheep/helper/tenant-runtime';

  // 隐藏原生tabBar
  uni.hideTabBar({
    fail: () => {},
  });

  const sys_navBar = sheep.$platform.navbar;
  const cart = sheep.$store('cart');

  const state = reactive({
    editMode: computed(() => cart.editMode),
    list: computed(() => cart.list),
    checkoutBusy: false,
    deleteBusy: false,
    expired: false,
    selectedIds: computed(() => cart.selectedIds),
    isAllSelected: computed(() => cart.isAllSelected),
    totalPriceSelected: computed(() => cart.totalPriceSelected),
  });
  let pageOperation;
  let pageVersion = 0;
  const pageCurrent = () => pageOperation !== undefined && isTenantOperationCurrent(pageOperation);
  const blocked = computed(() => state.expired || state.checkoutBusy || state.deleteBusy || !cart.canOperate());
  const isAvailable = item => cart.newList.some(value => value.id === item?.id);
  function expirePage() {
    pageVersion++; pageOperation = undefined;
    state.checkoutBusy = false; state.deleteBusy = false; state.expired = true;
  }
  function sessionChanged() { expirePage(); cart.emptyList(); }
  uni.$on('mall:session-changed', sessionChanged);
  onHide(expirePage);
  onUnmounted(() => { expirePage(); uni.$off('mall:session-changed', sessionChanged); });

  // 单选选中
  function onSelectSingle(id) {
    if (!pageCurrent() || blocked.value) return;
    if (id === undefined || id === null || id === '') return;
    cart.selectSingle(id);
  }

  // 编辑、取消
  function onChangeEditMode(flag) {
    if (!pageCurrent() || blocked.value) return;
    cart.onChangeEditMode(flag);
  }

  // 全选
  function onSelectAll() {
    if (!pageCurrent() || blocked.value) return;
    cart.selectAll(!state.isAllSelected);
  }

  // 结算
  async function onConfirm() {
    if (!pageCurrent() || blocked.value || state.editMode) return;
    const operation = pageOperation, version = pageVersion;
    const current = () => version === pageVersion && isTenantOperationCurrent(operation);
    const items = [];
    const selectedList = cart.newList.filter((item) => item && state.selectedIds.includes(item.id));
    selectedList.forEach((item) => {
      const skuId = item?.sku?.id;
      const count = Number(item?.count);
      const skuIdText = skuId === undefined || skuId === null ? '' : String(skuId);
      if (!/^\d+$/.test(skuIdText) || skuIdText === '0' || !Number.isSafeInteger(count) || count <= 0 || count > 2147483647) {
        return;
      }
      items.push({
        skuId: skuIdText,
        count,
        cartId: item.id,
        categoryId: item.spu?.categoryId,
      });
    });
    if (isEmpty(items)) {
      sheep.$helper.toast('请先选择商品');
      return;
    }
    if (items.length !== selectedList.length || selectedList.some(item => !item.spu?.id)) {
      sheep.$helper.toast('商品数量或规格异常，请刷新购物车');
      return;
    }
    const spuIds = [...new Set(selectedList.map((item) => item?.spu?.id).filter((id) => id > 0))];
    state.checkoutBusy = true;
    try {
      if (!(await validateDeliveryType(spuIds, current)) || !current()) return;
      sheep.$router.go('/pages/order/confirm', { data: JSON.stringify({ items }) });
    } finally { if (current()) state.checkoutBusy = false; }
  }

  /**
   * 校验配送方式冲突
   *
   * @param {string[]} spuIds - 商品ID数组
   * @returns {Promise<void>}
   * @throws {Error} 当配送方式冲突或获取商品信息失败时抛出错误
   */
  async function validateDeliveryType(spuIds, current) {
    if (!Array.isArray(spuIds) || spuIds.length === 0) {
      sheep.$helper.toast('未找到商品信息');
      return false;
    }
    try {
      // 获取商品信息。request 在网络异常时可能返回 false，需先校验响应。
      const response = await SpuApi.getSpuListByIds(spuIds.join(','));
      if (!current()) return false;
      const spuList = response?.code === 0 && Array.isArray(response.data) ? response.data : [];
      if (spuList.length !== spuIds.length || !spuIds.every(id => spuList.some(item => String(item?.id) === String(id)))) {
        sheep.$helper.toast('未找到商品信息');
        return false;
      }
      // 配送方式缺失时无法安全判断，交由用户稍后重试，避免运行时异常。
      if (spuList.some((item) => !Array.isArray(item?.deliveryTypes) || item.deliveryTypes.length === 0)) {
        sheep.$helper.toast('商品配送信息暂不可用，请稍后重试');
        return false;
      }
      // 获取所有商品的配送方式列表
      const deliveryTypesList = spuList.map((item) => item.deliveryTypes);
      // 检查配送方式冲突
      const hasConflict = checkDeliveryConflicts(deliveryTypesList);
      if (hasConflict) {
        sheep.$helper.toast('选中商品支持的配送方式冲突，不允许提交');
        return false;
      }
      return true;
    } catch (error) {
      if (current()) sheep.$helper.toast('商品配送信息暂不可用，请稍后重试');
      return false;
    }
  }

  /**
   * 检查配送方式列表中是否存在冲突
   * @description
   * 示例场景:
   * A 商品支持：[快递, 自提]
   * B 商品支持：[快递]
   * C 商品支持：[自提]
   *
   * 对比结果:
   * A 和 B：不冲突 (有交集：快递)
   * A 和 C：不冲突 (有交集：自提)
   * B 和 C：冲突 (无交集)
   * @param {Array<Array<number>>} deliveryTypesList - 配送方式列表的数组
   * @returns {boolean} 是否存在冲突
   */
  function checkDeliveryConflicts(deliveryTypesList) {
    return !deliveryTypesList[0]?.some(type => deliveryTypesList.every(types => types.includes(type)));
  }

  function onNumberChange(e, cartItem) {
    if (!pageCurrent() || blocked.value || !isAvailable(cartItem) || !cartItem?.id) return;
    const count = Number(e);
    if (!Number.isSafeInteger(count) || count <= 0 || count > 2147483647) {
      sheep.$helper.toast('数量须为正整数，删除商品请使用编辑功能');
      cart.revision++;
      return;
    }
    if (Number(cartItem.count) === count) return;
    cart.update({
      goods_id: cartItem.id,
      goods_num: count,
    });
  }

  async function onDelete() {
    if (!pageCurrent() || blocked.value || !state.editMode) return;
    if (!state.selectedIds.length) {
      sheep.$helper.toast('请先选择商品');
      return;
    }
    const operation = pageOperation, version = pageVersion, ids = [...state.selectedIds];
    const current = () => version === pageVersion && isTenantOperationCurrent(operation);
    state.deleteBusy = true;
    try {
      const confirmed = await new Promise(resolve => uni.showModal({ title: '删除商品',
        content: `确定删除选中的 ${ids.length} 件商品吗？`,
        success: result => resolve(!!result.confirm), fail: () => resolve(false) }));
      if (!confirmed || !current()) return;
      await cart.delete(ids, operation);
    } finally { if (current()) state.deleteBusy = false; }
  }

  function getCartList() {
    if (cart.busy || state.checkoutBusy || state.deleteBusy) return;
    pageOperation = captureTenantOperation(); pageVersion++; state.expired = false;
    return cart.getList();
  }

  function getSkuText(item) {
    const properties = Array.isArray(item?.sku?.properties)
      ? item.sku.properties.filter((property) => property && typeof property === 'object')
      : [];
    const text = properties
      .map((property) => property.valueName || property.name)
      .filter(Boolean)
      .join(' ');
    return text || '默认规格';
  }

  onShow(() => {
    getCartList();
  });
</script>

<style lang="scss" scoped>
  .cart-notice { position: relative; z-index: 1001; padding: 24rpx 30rpx; background: #fff4e6; color: #8a4c0a; }
  :deep(.ui-fixed) {
    height: 72rpx;
  }

  .cart-box {
    width: 100%;

    .cart-header {
      height: 70rpx;
      background-color: #f6f6f6;
      width: 100%;
      position: fixed;
      left: 0;
      top: v-bind('sys_navBar') rpx;
      z-index: 1000;
      box-sizing: border-box;
    }

    .cart-footer {
      height: 100rpx;
      background-color: #fff;

      .pay-btn {
        width: 180rpx;
        height: 70rpx;
        font-size: 28rpx;
        line-height: 28rpx;
        font-weight: 500;
        border-radius: 40rpx;
      }
    }

    .cart-content {
      margin-top: 70rpx;

      .goods-box {
        background-color: #fff;
        position: relative;
      }
      // 下架商品
      .down-box {
        position: absolute;
        left: 0;
        top: 0;
        width: 100%;
        height: 100%;
        background: rgba(#fff, 0.8);
        z-index: 2;
        display: flex;
        justify-content: center;
        align-items: center;
        color: #999;
        font-size: 32rpx;
      }
    }
  }
</style>
