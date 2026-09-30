<template>
  <s-layout title="确认订单">
    <view v-if="state.expired" class="ss-p-30">登录账号已变化，旧订单、地址和优惠券已清除，请从当前账号购物车重新结算。</view>
    <view v-else-if="state.needsVerification || state.submitted" class="ss-p-30"><view>{{ state.submitted ? '订单已创建。' : '订单提交结果需要核实。' }}{{ state.recoveryError }} 请先到订单列表核实结果，不要重复提交；有意新增采购请从商品或购物车重新发起。</view><button size="mini" @tap="viewOrders">查看我的订单</button></view>
    <template v-else>
    <view v-if="state.error" class="ss-p-20" role="alert">{{ state.error }}</view>
    <s-service-entry label="平台结算咨询（不代表任一店铺）" :disabled="state.submitting || state.loading" />
    <!-- 头部地址选择【配送地址】【自提地址】 -->
    <AddressSelection ref="addressSelection" :model-value="addressState" :disabled="state.submitting" @update:modelValue="onAddressChange" />

    <!-- 商品信息 -->
    <view class="order-card-box ss-m-b-14">
      <view v-for="item in state.orderInfo.items" :key="item.skuId">
      <s-goods-item
        :img="item.picUrl"
        :title="item.spuName"
        :skuText="(Array.isArray(item.properties) ? item.properties : []).map((property) => property.valueName).join(' ')"
        :price="item.price"
        :num="item.count"
        marginBottom="10"
      />
      <s-service-entry resource-type="PRODUCT" :resource-id="item.spuId" :disabled="state.submitting || state.loading" />
      </view>
      <view class="order-item ss-flex ss-col-center ss-row-between ss-p-x-20 bg-white ss-r-10">
        <view class="item-title">订单备注</view>
        <view class="ss-flex ss-col-center">
          <uni-easyinput
            maxlength="20"
            placeholder="建议留言前先与商家沟通"
            v-model="state.orderPayload.remark"
            :disabled="state.submitting"
            :inputBorder="false"
            :clearable="false"
          />
        </view>
      </view>
    </view>

    <!-- 价格信息 -->
    <view class="bg-white total-card-box ss-p-20 ss-m-b-14 ss-r-10">
      <view class="total-box-content border-bottom">
        <view class="order-item ss-flex ss-col-center ss-row-between">
          <view class="item-title">商品金额</view>
          <view class="ss-flex ss-col-center">
            <text class="item-value ss-m-r-24">
              ￥{{ fen2yuan(state.orderInfo.price.totalPrice) }}
            </text>
          </view>
        </view>
        <view
          v-if="state.orderPayload.pointActivityId"
          class="order-item ss-flex ss-col-center ss-row-between"
        >
          <view class="item-title">兑换积分</view>
          <view class="ss-flex ss-col-center">
            <image
              :src="sheep.$url.static('/static/img/shop/goods/score1.svg')"
              class="score-img"
            />
            <text class="item-value ss-m-r-24">
              {{ state.orderInfo.usePoint }}
            </text>
          </view>
        </view>
        <view
          class="order-item ss-flex ss-col-center ss-row-between"
          v-if="state.orderInfo.type === 0 || state.orderPayload.pointActivityId"
        >
          <view class="item-title">积分抵扣</view>
          <view class="ss-flex ss-col-center">
            {{ state.pointStatus || state.orderPayload.pointActivityId ? '剩余积分' : '当前积分' }}
            <image
              :src="sheep.$url.static('/static/img/shop/goods/score1.svg')"
              class="score-img"
            />
            <text class="item-value ss-m-r-24">
              {{
                state.pointStatus || state.orderPayload.pointActivityId
                  ? state.orderInfo.totalPoint - state.orderInfo.usePoint
                  : state.orderInfo.totalPoint || 0
              }}
            </text>
            <checkbox-group @change="changeIntegral" v-if="!state.orderPayload.pointActivityId">
              <checkbox
                :checked="state.pointStatus"
                :disabled="state.submitting || state.loading || !state.orderInfo.totalPoint || state.orderInfo.totalPoint <= 0"
              />
            </checkbox-group>
          </view>
        </view>
        <!-- 快递配置时，信息的展示 -->
        <view
          class="order-item ss-flex ss-col-center ss-row-between"
          v-if="addressState.deliveryType === 1"
        >
          <view class="item-title">运费</view>
          <view class="ss-flex ss-col-center">
            <text class="item-value ss-m-r-24" v-if="state.orderInfo.price.deliveryPrice > 0">
              +￥{{ fen2yuan(state.orderInfo.price.deliveryPrice) }}
            </text>
            <view class="item-value ss-m-r-24" v-else>免运费</view>
          </view>
        </view>
        <!-- 门店自提时，需要填写姓名和手机号 -->
        <view
          class="order-item ss-flex ss-col-center ss-row-between"
          v-if="addressState.deliveryType === 2"
        >
          <view class="item-title">联系人</view>
          <view class="ss-flex ss-col-center">
            <uni-easyinput
              maxlength="20"
              placeholder="请填写您的联系姓名"
              v-model="addressState.receiverName"
              :disabled="state.submitting"
              :inputBorder="false"
              :clearable="false"
            />
          </view>
        </view>
        <view
          class="order-item ss-flex ss-col-center ss-row-between"
          v-if="addressState.deliveryType === 2"
        >
          <view class="item-title">联系电话</view>
          <view class="ss-flex ss-col-center">
            <uni-easyinput
              maxlength="20"
              placeholder="请填写您的联系电话"
              v-model="addressState.receiverMobile"
              :disabled="state.submitting"
              :inputBorder="false"
              :clearable="false"
            />
          </view>
        </view>
        <!-- 优惠劵：只有 type = 0 普通订单（非拼团、秒杀、砍价），才可以使用优惠劵 -->
        <view
          class="order-item ss-flex ss-col-center ss-row-between"
          v-if="state.orderInfo.type === 0"
        >
          <view class="item-title">优惠券</view>
          <view class="ss-flex ss-col-center" @tap="openCoupon">
            <text class="item-value text-red" v-if="state.orderPayload.couponId > 0">
              -￥{{ fen2yuan(state.orderInfo.price.couponPrice) }}
            </text>
            <text
              class="item-value"
              :class="
                state.couponInfo.filter((coupon) => coupon?.match).length > 0
                  ? 'text-red'
                  : 'text-disabled'
              "
              v-else
            >
              {{
                state.couponInfo.filter((coupon) => coupon?.match).length > 0
                  ? state.couponInfo.filter((coupon) => coupon?.match).length + ' 张可用'
                  : '暂无可用优惠券'
              }}
            </text>
            <text class="_icon-forward item-icon" />
          </view>
        </view>
        <view
          class="order-item ss-flex ss-col-center ss-row-between"
          v-if="state.orderInfo.price.discountPrice > 0"
        >
          <view class="item-title">活动优惠</view>
          <view class="ss-flex ss-col-center" @tap="state.showDiscount = true">
            <text class="item-value text-red">
              -￥{{ fen2yuan(state.orderInfo.price.discountPrice) }}
            </text>
            <text class="_icon-forward item-icon" />
          </view>
        </view>
        <view
          class="order-item ss-flex ss-col-center ss-row-between"
          v-if="state.orderInfo.price.vipPrice > 0"
        >
          <view class="item-title">会员优惠</view>
          <view class="ss-flex ss-col-center">
            <text class="item-value text-red">
              -￥{{ fen2yuan(state.orderInfo.price.vipPrice) }}
            </text>
          </view>
        </view>
      </view>
      <view class="total-box-footer ss-font-28 ss-flex ss-row-right ss-col-center ss-m-r-28">
        <view class="total-num ss-m-r-20">
          共{{ state.orderInfo.items.reduce((acc, item) => acc + (Number(item?.count) || 0), 0) }}件
        </view>
        <view>合计：</view>
        <view class="total-num text-red"> ￥{{ fen2yuan(state.orderInfo.price.payPrice) }}</view>
      </view>
    </view>

    <!-- 选择优惠券弹框 -->
    <s-coupon-select
      v-model="state.couponInfo"
      :show="state.showCoupon"
      @confirm="onSelectCoupon"
      @close="state.showCoupon = false"
    />

    <!-- 满额折扣弹框 -->
    <s-discount-list
      v-model="state.orderInfo"
      :show="state.showDiscount"
      @close="state.showDiscount = false"
    />

    <!-- 底部 -->
    <su-fixed bottom :opacity="false" bg="bg-white" placeholder :noFixed="false" :index="200">
      <view class="footer-box border-top ss-flex ss-row-between ss-p-x-20 ss-col-center">
        <view class="total-box-footer ss-flex ss-col-center">
          <view class="total-num ss-font-30 text-red">
            ￥{{ fen2yuan(state.orderInfo.price.payPrice) }}
          </view>
        </view>
        <button
          class="ss-reset-button ui-BG-Main-Gradient ss-r-40 submit-btn ui-Shadow-Main"
          :disabled="state.submitting || state.loading || !state.orderInfo.items.length || !Object.prototype.hasOwnProperty.call(state.orderInfo.price, 'payPrice')"
          @tap="onConfirm"
        >
          提交订单
        </button>
      </view>
    </su-fixed>
    </template>
  </s-layout>
</template>

<script setup>
  import { reactive, ref, watch } from 'vue';
  import { onLoad, onShow, onHide } from '@dcloudio/uni-app';
  import AddressSelection from '@/pages/order/addressSelection.vue';
  import sheep from '@/sheep';
  import OrderApi from '@/sheep/api/trade/order';
  import TradeConfigApi from '@/sheep/api/trade/config';
  import { fen2yuan } from '@/sheep/hooks/useGoods';
  import { DeliveryTypeEnum } from '@/sheep/helper/const';
  import { useTenantTask } from '@/sheep/hooks/useTenantTask';
  import { ensureAccountSession, tenantContext } from '@/sheep/helper/tenant-runtime';
  import { openCheckoutIntent, validCheckoutIntent, legacyCheckoutIntent } from '@/sheep/helper/checkout-intent.mjs';

  const state = reactive({
    orderPayload: {},
    orderInfo: {
      items: [], // 商品项列表
      price: {}, // 价格信息
    },
    showCoupon: false, // 是否展示优惠劵
    couponInfo: [], // 优惠劵列表
    showDiscount: false, // 是否展示营销活动
    // ========== 积分 ==========
    pointStatus: false, //是否使用积分
    submitting: false,
    loading: false,
    expired: false,
    needsVerification: false,
    submitted: false,
    recoveryError: '',
    error: '',
  });

  let orderInfoRequestId = 0;

  const addressState = ref({
    addressInfo: {}, // 选择的收货地址
    deliveryType: undefined, // 收货方式：1-快递配送，2-门店自提
    isPickUp: true, // 门店自提是否开启
    pickUpInfo: {}, // 选择的自提门店信息
    receiverName: '', // 收件人名称
    receiverMobile: '', // 收件人手机
  });
  const addressSelection = ref();
  let visible = true, initialized = false;
  let checkoutIntent;
  const task = useTenantTask(() => {
    state.expired = true; orderInfoRequestId++;
    state.orderPayload = {}; state.orderInfo = { items: [], price: {} }; state.couponInfo = [];
    state.showCoupon = false; state.showDiscount = false; state.pointStatus = false;
    state.submitting = false; state.loading = false; state.error = '';
    checkoutIntent = undefined; state.recoveryError = '';
    addressState.value = { addressInfo: {}, pickUpInfo: {}, deliveryType: undefined, isPickUp: false, receiverName: '', receiverMobile: '' };
  });
  const canEdit = () => task.current() && !state.expired && !state.submitting && !state.needsVerification && !state.submitted;
  function viewOrders() { if (task.current() && visible) sheep.$router.redirect('/pages/order/list'); }
  function definiteRejection(value) { const code = Number(value?.code); return Number.isInteger(code) && (code >= 1000000000 || [400,401,403,404,409,422,429].includes(code)); }
  function onAddressChange(value) { if (canEdit()) addressState.value = value; }
  function openCoupon() { if (visible && canEdit() && !state.loading) state.showCoupon = true; }
  onHide(() => { visible = false; task.invalidate(); orderInfoRequestId++; state.loading = false; state.showCoupon = false; state.showDiscount = false; if (state.submitting) state.needsVerification = true; });
  onShow(() => { visible = true; addressSelection.value?.cancelSelection(); if (initialized && canEdit()) getOrderInfo(); });

  // ========== 积分 ==========
  /**
   * 使用积分抵扣
   */
  const changeIntegral = async () => {
    if (!canEdit() || state.loading || !visible) return;
    state.pointStatus = !state.pointStatus;
    await getOrderInfo();
  };

  // 选择优惠券
  async function onSelectCoupon(couponId) {
    if (!canEdit() || state.loading || !visible) return;
    const current = task.capture();
    state.orderPayload.couponId = couponId;
    await getOrderInfo();
    if (current()) state.showCoupon = false;
  }

  // 提交订单
  function onConfirm() {
    if (!canEdit() || state.loading || !visible) return;
    if (!Array.isArray(state.orderPayload?.items) || state.orderPayload.items.length === 0) {
      sheep.$helper.toast('订单商品信息不完整，请返回重试');
      return;
    }
    if (addressState.value.deliveryType === 1 && !addressState.value.addressInfo?.id) {
      sheep.$helper.toast('请选择收货地址');
      return;
    }
    if (![1, 2].includes(addressState.value.deliveryType)) {
      sheep.$helper.toast('请选择配送方式');
      return;
    }
    if (
      !state.orderInfo?.items?.length ||
      !state.orderInfo?.price ||
      !Object.prototype.hasOwnProperty.call(state.orderInfo.price, 'payPrice')
    ) {
      sheep.$helper.toast('订单金额尚未计算完成，请稍后重试');
      return;
    }
    if (addressState.value.deliveryType === 2) {
      if (!addressState.value.pickUpInfo?.id) {
        sheep.$helper.toast('请选择自提门店地址');
        return;
      }
      if (addressState.value.receiverName === '' || addressState.value.receiverMobile === '') {
        sheep.$helper.toast('请填写联系人或联系人电话');
        return;
      }
      if (!/^[\u4e00-\u9fa5\w]{2,16}$/.test(addressState.value.receiverName)) {
        sheep.$helper.toast('请填写您的真实姓名');
        return;
      }
      if (!/^1(3|4|5|7|8|9|6)\d{9}$/.test(addressState.value.receiverMobile)) {
        sheep.$helper.toast('请填写正确的手机号');
        return;
      }
    }
    return submitOrder();
  }

  // 创建订单&跳转
  async function submitOrder() {
    if (!canEdit() || state.loading || !visible) return;
    const current = task.capture();
    state.submitting = true;
    state.showCoupon = false; state.showDiscount = false;
    const items = state.orderPayload.items.map(item => ({ ...item }));
    let attempt;
    try {
      if (!checkoutIntent) throw new Error('结算状态尚未核验，请从购物车重新进入');
      attempt = checkoutIntent.begin();
      const response = await OrderApi.createOrder({
        items,
        couponId: state.orderPayload.couponId,
        remark: state.orderPayload.remark,
        deliveryType: addressState.value.deliveryType,
        addressId: addressState.value.addressInfo.id, // 收件地址编号
        pickUpStoreId: addressState.value.pickUpInfo.id, //自提门店编号
        receiverName: addressState.value.receiverName, // 选择门店自提时，该字段为联系人名
        receiverMobile: addressState.value.receiverMobile, // 选择门店自提时，该字段为联系人手机
        pointStatus: state.pointStatus,
        combinationActivityId: state.orderPayload.combinationActivityId,
        combinationHeadId: state.orderPayload.combinationHeadId,
        seckillActivityId: state.orderPayload.seckillActivityId,
        pointActivityId: state.orderPayload.pointActivityId,
      });
      const orderId = response?.data?.id;
      if (response?.code === 0 && /^[1-9]\d*$/.test(String(orderId ?? ''))) { try { attempt.created(); } catch {} }
      if (definiteRejection(response)) { try { attempt.reject(); } catch {} }
      if (!current() || !visible) return;
      if (response?.code !== 0 || !/^[1-9]\d*$/.test(String(orderId ?? ''))) {
        if (!definiteRejection(response) || checkoutIntent.state() !== 'DRAFT') state.needsVerification = true;
        else sheep.$helper.toast(response?.msg || '订单未创建，请检查后重试');
        return;
      }
      state.submitted = true;
      const data = response.data;
      // 更新购物车列表，如果来自购物车
      if (items[0]?.cartId > 0) {
        // Secondary refresh cannot turn successful creation into a retryable failure.
        try { Promise.resolve(sheep.$store('cart').getList()).catch(() => {}); } catch {}
      }

      // 跳转到支付页面
      const payOrderId = data.payOrderId;
      if (payOrderId !== undefined && payOrderId !== null && String(payOrderId) !== '' && String(payOrderId) !== '0') {
        sheep.$router.redirect('/pages/pay/index', {
          id: String(payOrderId),
        });
      } else {
        sheep.$router.redirect('/pages/order/detail', {
          id: String(data.id),
        });
      }
    } catch (error) {
      let released = false;
      if (definiteRejection(error) && attempt) { try { released = attempt.reject(); } catch {} }
      if (current() && !state.submitted && !released) { state.needsVerification = true; state.recoveryError = error?.message || ''; }
      return;
    } finally {
      if (current()) state.submitting = false;
    }
  }

  // 检查库存 & 计算订单价格
  async function getOrderInfo() {
    if (!canEdit()) return 1;
    if (!Array.isArray(state.orderPayload?.items) || state.orderPayload.items.length === 0) {
      return 1;
    }
    const requestId = ++orderInfoRequestId;
    const current = task.capture();
    state.loading = true;
    state.error = '';
    try {
      // 计算价格
      const response = await OrderApi.settlementOrder({
        items: state.orderPayload.items,
        couponId: state.orderPayload.couponId,
        deliveryType: addressState.value.deliveryType,
        addressId: addressState.value.addressInfo.id, // 收件地址编号
        pickUpStoreId: addressState.value.pickUpInfo.id, //自提门店编号
        receiverName: addressState.value.receiverName, // 选择门店自提时，该字段为联系人名
        receiverMobile: addressState.value.receiverMobile, // 选择门店自提时，该字段为联系人手机
        pointStatus: state.pointStatus,
        combinationActivityId: state.orderPayload.combinationActivityId,
        combinationHeadId: state.orderPayload.combinationHeadId,
        seckillActivityId: state.orderPayload.seckillActivityId,
        pointActivityId: state.orderPayload.pointActivityId,
      });
      if (!current() || requestId !== orderInfoRequestId) return 1;
      if (response?.code !== 0 || !response.data) {
        if (requestId !== orderInfoRequestId) return 1;
        state.orderInfo = { items: [], price: {} };
        state.couponInfo = [];
        state.error = response?.msg || '订单金额计算失败，请检查配送地址后重试';
        return response?.code || 1;
      }
      if (requestId !== orderInfoRequestId) return 1;
      state.orderInfo = {
        ...response.data,
        items: Array.isArray(response.data.items)
          ? response.data.items
              .filter((item) => item && typeof item === 'object')
              .map((item) => ({
              ...item,
              properties: Array.isArray(item.properties) ? item.properties : [],
              count: Number(item.count) || 0,
              price: Number(item.price) || 0,
              }))
          : [],
        price: response.data.price || {},
      };
      state.couponInfo = Array.isArray(response.data.coupons)
        ? response.data.coupons.filter((coupon) => coupon && typeof coupon === 'object')
        : [];
      // 设置收货地址
      if (state.orderInfo.address) {
        addressState.value.addressInfo = state.orderInfo.address;
      }
      return 0;
    } catch (error) {
      if (current() && requestId === orderInfoRequestId) {
        state.orderInfo = { items: [], price: {} }; state.couponInfo = [];
        state.error = '订单金额计算失败，请检查配送地址后重试';
      }
      return 1;
    } finally {
      if (current() && requestId === orderInfoRequestId) state.loading = false;
    }
  }

  onLoad(async (options = {}) => {
    if (!task.current()) return;
    const current = task.capture();
    try { await ensureAccountSession(); } catch { if (current()) { state.needsVerification = true; state.recoveryError = '当前账号暂无法核验，请重新登录后从购物车进入'; } return; }
    if (!current()) return;
    // 解析参数
    const encodedData = options?.data;
    if (!encodedData) {
      sheep.$helper.toast('参数不正确，请检查！');
      return;
    }
    try {
      // uni.navigateTo 在 H5 下会对已经编码过的 query 再编码一次，
      // 因而这里可能收到 `%7B...%7D` 或 `%257B...%257D`。最多解码两次，
      // 同时避免对普通 JSON/非法字符串抛出异常。
      let dataText = String(encodedData);
      for (let index = 0; index < 2; index++) {
        try {
          const decoded = decodeURIComponent(dataText);
          if (decoded === dataText) break;
          dataText = decoded;
        } catch (error) {
          break;
        }
      }
      const parsedPayload = JSON.parse(dataText);
      if (!parsedPayload || typeof parsedPayload !== 'object' || Array.isArray(parsedPayload)) {
        throw new Error('invalid order payload');
      }
      const owner = tenantContext.state();
      if (!owner.accountTenantId || owner.accountTenantId !== owner.browseTenantId) throw new Error('账号不属于当前商城');
      const intentId = options.checkoutIntent === undefined ? legacyCheckoutIntent(parsedPayload) : options.checkoutIntent;
      if (!validCheckoutIntent(intentId)) throw new Error('结算标识不正确，请从购物车重新采购');
      try {
        checkoutIntent = openCheckoutIntent({ getItem: key => uni.getStorageSync(key) || null, setItem: (key,value) => uni.setStorageSync(key,value) }, intentId, { tenantId: owner.accountTenantId, memberId: owner.userId });
        const status = checkoutIntent.state(); state.submitted = status === 'CREATED'; state.needsVerification = status === 'PENDING';
        if (state.submitted || state.needsVerification) return;
      } catch (error) { state.needsVerification = true; state.recoveryError = error?.message || '结算状态无法核验'; return; }
      state.orderPayload = parsedPayload;
      // Keep backend Long identifiers as strings across H5 and mini-program query parsing.
      if (Array.isArray(state.orderPayload.items)) {
        state.orderPayload.items = state.orderPayload.items.map((item) => {
          if (!item || typeof item !== 'object') return item;
          const normalized = { ...item };
          ['id', 'skuId', 'spuId', 'cartId'].forEach((key) => {
            if (normalized[key] !== undefined && normalized[key] !== null) {
              normalized[key] = String(normalized[key]);
            }
          });
          return normalized;
        });
      }
    } catch (error) {
      sheep.$helper.toast('订单参数不正确，请返回重试');
      return;
    }
    if (!Array.isArray(state.orderPayload?.items) || state.orderPayload.items.length === 0) {
      sheep.$helper.toast('订单商品信息不完整，请返回重试');
      return;
    }

    // 获取交易配置
    try {
      const response = await TradeConfigApi.getTradeConfig();
      if (!current()) return;
      if (response?.code === 0 && response.data) {
        addressState.value.isPickUp = response.data.deliveryPickUpEnabled === true;
      }
    } catch (error) {
      // 默认保留快递配送；自提是否开启由后端结算结果再决定。
    }
    if (!current()) return;
    initialized = true;

    // 价格计算
    // 情况一：先自动选择“快递物流”
    addressState.value.deliveryType = DeliveryTypeEnum.EXPRESS.type;
    const initialQuote = getOrderInfo(), initialQuoteId = orderInfoRequestId;
    let orderCode = await initialQuote;
    if (!current() || orderInfoRequestId !== initialQuoteId) return;
    if (orderCode === 0) {
      return;
    }
    // 情况二：失败，再自动选择“门店自提”
    if (addressState.value.isPickUp) {
      addressState.value.deliveryType = DeliveryTypeEnum.PICK_UP.type;
      let orderCode = await getOrderInfo();
      if (!current()) return;
      if (orderCode === 0) {
        return;
      }
    }
    // 情况三：都失败，则不选择
    addressState.value.deliveryType = undefined;
    await getOrderInfo();
  });

  // 使用 watch 监听地址和配送方式的变化
  watch(addressState, async (newAddress, oldAddress) => {
    if (!canEdit()) return;
    if (!oldAddress) {
      return;
    }
    // 如果收货地址或配送方式有变化，则重新计算价格
    const newAddressId = newAddress.addressInfo?.id || 0;
    const oldAddressId = oldAddress.addressInfo?.id || 0;
    const newPickUpId = newAddress.pickUpInfo?.id || 0;
    const oldPickUpId = oldAddress.pickUpInfo?.id || 0;
    if (
      newAddressId !== oldAddressId ||
      newPickUpId !== oldPickUpId ||
      newAddress.deliveryType !== oldAddress.deliveryType ||
      newAddress.receiverName !== oldAddress.receiverName ||
      newAddress.receiverMobile !== oldAddress.receiverMobile
    ) {
      await getOrderInfo();
    }
  });
</script>

<style lang="scss" scoped>
  :deep() {
    .uni-input-wrapper {
      width: 320rpx;
    }

    .uni-easyinput__content-input {
      font-size: 28rpx;
      height: 72rpx;
      text-align: right !important;
      padding-right: 0 !important;

      .uni-input-input {
        font-weight: 500;
        color: #333333;
        font-size: 26rpx;
        height: 32rpx;
        margin-top: 4rpx;
      }
    }

    .uni-easyinput__content {
      display: flex !important;
      align-items: center !important;
      justify-content: right !important;
    }
  }

  .score-img {
    width: 36rpx;
    height: 36rpx;
    margin: 0 4rpx;
  }

  .order-item {
    height: 80rpx;

    .item-title {
      font-size: 28rpx;
      font-weight: 400;
    }

    .item-value {
      font-size: 28rpx;
      font-weight: 500;
      font-family: OPPOSANS;
    }

    .text-disabled {
      color: #bbbbbb;
    }

    .item-icon {
      color: $dark-9;
    }

    .remark-input {
      text-align: right;
    }

    .item-placeholder {
      color: $dark-9;
      font-size: 26rpx;
      text-align: right;
    }
  }

  .total-box-footer {
    height: 90rpx;

    .total-num {
      color: #333333;
      font-family: OPPOSANS;
    }
  }

  .footer-box {
    height: 100rpx;

    .submit-btn {
      width: 240rpx;
      height: 70rpx;
      font-size: 28rpx;
      font-weight: 500;

      .goto-pay-text {
        line-height: 28rpx;
      }
    }

    .cancel-btn {
      width: 240rpx;
      height: 80rpx;
      font-size: 26rpx;
      background-color: #e5e5e5;
      color: $dark-9;
    }
  }

  .title {
    font-size: 36rpx;
    font-weight: bold;
    color: #333333;
  }

  .subtitle {
    font-size: 28rpx;
    color: #999999;
  }

  .cicon-checkbox {
    font-size: 36rpx;
    color: var(--ui-BG-Main);
  }

  .cicon-box {
    font-size: 36rpx;
    color: #999999;
  }
</style>
