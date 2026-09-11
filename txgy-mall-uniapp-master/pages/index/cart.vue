<template>
  <s-layout :bgStyle="{ color: '#fff' }" tabbar="/pages/index/cart" title="购物车">
    <s-empty
      v-if="state.list.length === 0"
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
                color="var(--ui-BG-Main)"
                style="transform: scale(0.8)"
                @tap.stop="onSelectSingle(item?.id)"
              />
            </label>
            <view v-if="item.spu?.status !== 1 && !state.editMode" class="down-box">
              该商品已下架
            </view>
            <view v-else-if="item.spu?.stock <= 0 && !state.editMode" class="down-box">
              该商品无库存
            </view>
            <s-goods-item
              :img="item.spu?.picUrl || item.sku?.picUrl || ''"
              :price="item.sku?.price || 0"
              :skuText="getSkuText(item)"
              :title="item.spu?.name || item.sku?.name || '商品'"
              :titleWidth="400"
              priceColor="#FF3000"
            >
              <template v-if="!state.editMode" v-slot:tool>
                <su-number-box
                  v-model="item.count"
                  :max="item.sku?.stock || item.spu?.stock || 0"
                  :min="0"
                  :step="1"
                  @change="onNumberChange($event, item)"
                />
              </template>
            </s-goods-item>
          </view>
        </view>
      </view>
      <!-- 底部 -->
      <su-fixed v-if="state.list.length > 0" :isInset="false" :val="48" bottom placeholder>
        <view class="cart-footer ss-flex ss-col-center ss-row-between ss-p-x-30 border-bottom">
          <view class="footer-left ss-flex ss-col-center">
            <label class="check-box ss-flex ss-col-center ss-p-r-30" @tap="onSelectAll">
              <radio
                :checked="state.isAllSelected"
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
              class="ss-reset-button ui-BG-Main-Gradient pay-btn ui-Shadow-Main"
              @tap="onDelete"
            >
              删除
            </button>
            <button
              v-else
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
  import { onShow } from '@dcloudio/uni-app';
  import SpuApi from '@/sheep/api/product/spu';
  import { computed, reactive } from 'vue';
  import { fen2yuan } from '@/sheep/hooks/useGoods';
  import { isEmpty } from '@/sheep/helper/utils';

  // 隐藏原生tabBar
  uni.hideTabBar({
    fail: () => {},
  });

  const sys_navBar = sheep.$platform.navbar;
  const cart = sheep.$store('cart');

  const state = reactive({
    editMode: computed(() => cart.editMode),
    list: computed(() => cart.list),
    selectedList: [],
    selectedIds: computed(() => cart.selectedIds),
    isAllSelected: computed(() => cart.isAllSelected),
    totalPriceSelected: computed(() => cart.totalPriceSelected),
  });

  // 单选选中
  function onSelectSingle(id) {
    if (id === undefined || id === null || id === '') return;
    cart.selectSingle(id);
  }

  // 编辑、取消
  function onChangeEditMode(flag) {
    cart.onChangeEditMode(flag);
  }

  // 全选
  function onSelectAll() {
    cart.selectAll(!state.isAllSelected);
  }

  // 结算
  async function onConfirm() {
    const items = [];
    state.selectedList = state.list.filter((item) => item && state.selectedIds.includes(item.id));
    state.selectedList.forEach((item) => {
      const skuId = item?.sku?.id;
      const count = Number(item?.count);
      const skuIdText = skuId === undefined || skuId === null ? '' : String(skuId);
      if (!/^\d+$/.test(skuIdText) || skuIdText === '0' || !Number.isFinite(count) || count <= 0) {
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
    const spuIds = state.selectedList.map((item) => item?.spu?.id).filter((id) => id > 0);
    if (!(await validateDeliveryType(spuIds))) {
      return;
    }
    sheep.$router.go('/pages/order/confirm', {
      data: JSON.stringify({
        items,
      }),
    });
  }

  /**
   * 校验配送方式冲突
   *
   * @param {string[]} spuIds - 商品ID数组
   * @returns {Promise<void>}
   * @throws {Error} 当配送方式冲突或获取商品信息失败时抛出错误
   */
  async function validateDeliveryType(spuIds) {
    if (!Array.isArray(spuIds) || spuIds.length === 0) {
      sheep.$helper.toast('未找到商品信息');
      return false;
    }
    try {
      // 获取商品信息。request 在网络异常时可能返回 false，需先校验响应。
      const response = await SpuApi.getSpuListByIds(spuIds.join(','));
      const spuList = response?.code === 0 && Array.isArray(response.data) ? response.data : [];
      if (isEmpty(spuList)) {
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
      sheep.$helper.toast('商品配送信息暂不可用，请稍后重试');
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
    for (let i = 0; i < deliveryTypesList.length - 1; i++) {
      const currentTypes = deliveryTypesList[i];
      for (let j = i + 1; j < deliveryTypesList.length; j++) {
        const nextTypes = deliveryTypesList[j];
        // 检查是否没有交集（即冲突）
        const hasNoIntersection = !currentTypes.some((type) => nextTypes.includes(type));
        if (hasNoIntersection) {
          return true;
        }
      }
    }
    return false;
  }

  function onNumberChange(e, cartItem) {
    if (!cartItem?.id) return;
    const count = Number(e);
    if (!Number.isFinite(count) || count <= 0) {
      cart.delete(cartItem.id);
      return;
    }
    if (Number(cartItem.count) === count) return;
    cartItem.count = count;
    cart.update({
      goods_id: cartItem.id,
      goods_num: count,
    });
  }

  async function onDelete() {
    if (!state.selectedIds.length) {
      sheep.$helper.toast('请先选择商品');
      return;
    }
    cart.delete(state.selectedIds);
  }

  function getCartList() {
    cart.getList();
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
