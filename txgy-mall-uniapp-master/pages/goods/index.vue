<template>
  <view>
    <s-layout :onShareAppMessage="shareInfo" navbar="goods">
      <!-- 标题栏 -->
      <detailNavbar />

      <!-- 骨架屏 -->
      <detailSkeleton v-if="state.skeletonLoading" />
      <!-- 下架/售罄提醒 -->
      <s-empty
        v-else-if="state.goodsInfo === null"
        text="商品不存在或已下架"
        icon="/static/soldout-empty.png"
        showAction
        actionText="再逛逛"
        actionUrl="/pages/goods/list"
      />
      <block v-else-if="state.goodsInfo && state.goodsInfo.id">
        <view class="detail-swiper-selector">
          <view v-if="state.portal" class="portal-owner-card detail-card">
            <view class="portal-owner-kicker">平台公开商品</view>
            <view class="portal-owner-name">{{ state.goodsInfo.tenantName }}</view>
            <view class="portal-owner-meta">
              {{ state.goodsInfo.enterpriseName }} · {{ state.goodsInfo.shopName }}
            </view>
          </view>
          <!-- 商品轮播图  -->
          <su-swiper
            class="ss-m-b-14"
            isPreview
            :list="formatGoodsSwiper(state.goodsInfo.sliderPicUrls)"
            otStyle="tag"
            imageMode="widthFix"
            dotCur="bg-mask-40"
            :seizeHeight="750"
          />
          <!-- 限时折扣/会员价的优惠信息 -->
          <view
            class="discount"
            v-if="
              !state.portal &&
              state.settlementSku && state.settlementSku.id && state.settlementSku.promotionPrice
            "
          >
            <view class="discountCont">
              <view class="disContT">
                <view class="disContT1">
                  <view class="disContT1P">
                    ￥{{ fen2yuan(state.settlementSku.promotionPrice) }}
                  </view>
                  <view class="disContT1End">
                    直降￥
                    {{ fen2yuan(state.settlementSku.price - state.settlementSku.promotionPrice) }}
                  </view>
                </view>
                <view class="disContT2" v-if="state.settlementSku.promotionType === 4">
                  限时折扣
                </view>
                <view class="disContT2" v-else-if="state.settlementSku.promotionType === 6">
                  会员折扣
                </view>
              </view>
              <view class="disContB">
                <view class="disContB1">
                  价格：￥{{ fen2yuan(state.settlementSku.price) }} 丨 剩余：
                  {{ state.settlementSku.stock }}
                </view>
                <view class="disContB2" v-if="state.settlementSku.promotionEndTime > 0">
                  距结束仅剩
                  <s-count-down
                    :tipText="' '"
                    :bgColor="bgColor"
                    :dayText="':'"
                    :hourText="':'"
                    :minuteText="':'"
                    :secondText="' '"
                    :datatime="state.settlementSku.promotionEndTime / 1000"
                    :isDay="false"
                  />
                </view>
              </view>
            </view>
          </view>
          <!-- 价格+标题 -->
          <view class="title-card detail-card ss-p-y-30 ss-p-x-20">
            <!-- 没有限时折扣/会员价的优惠信息时，展示的价格信息 -->
            <view
              class="ss-flex ss-row-between ss-col-center ss-m-b-26"
              v-if="!state.settlementSku.promotionPrice"
            >
              <view class="price-box ss-flex ss-col-bottom">
                <view class="price-text ss-m-r-16">
                  {{ fen2yuan(state.selectedSku.price || state.goodsInfo.price) }}
                </view>
                <view
                  class="origin-price-text"
                  v-if="state.goodsInfo.marketPrice > state.goodsInfo.price"
                >
                  {{ fen2yuan(state.selectedSku.marketPrice || state.goodsInfo.marketPrice) }}
                </view>
              </view>
              <view class="sales-text">
                {{ formatSales('exact', state.goodsInfo.salesCount) }}
              </view>
            </view>
            <view v-if="!state.portal" class="discounts-box ss-flex ss-row-between ss-m-b-28">
              <!-- 查看优惠劵的描述 -->
              <view
                class="tag ss-m-r-10"
                v-for="coupon in state.couponInfo.slice(0, 1)"
                :key="coupon.id"
                @tap="onOpenActivity"
              >
                [劵]满{{ fen2yuanSimple(coupon.usePrice) }}元{{
                  coupon.discountType === 1
                    ? '减' + fen2yuanSimple(coupon.discountPrice) + '元'
                    : '打' + formatDiscountPercent(coupon.discountPercent) + '折'
                }}
              </view>
              <!-- 查看满减送的描述 -->
              <div class="tag-content">
                <view class="tag-box ss-flex">
                  <!-- 最多打印 3 条，所以需要扣除优惠劵已打印的 -->
                  <view
                    v-for="item in getRewardActivityRuleItemDescriptions(
                      state.rewardActivity,
                    ).slice(0, 3 - state.couponInfo.slice(0, 1).length)"
                    :key="item"
                    class="tag ss-m-r-10"
                    @tap="onOpenActivity"
                  >
                    <text>{{ item }}</text>
                  </view>
                </view>
              </div>
              <!-- 领取优惠劵的按钮 -->
              <view
                class="get-coupon-box ss-flex ss-col-center ss-m-l-20"
                @tap="onOpenActivity"
                v-if="state.couponInfo.length"
              >
                <view class="discounts-title ss-m-r-8">领券</view>
                <text class="cicon-forward"></text>
              </view>
            </view>
            <view class="title-text ss-line-2 ss-m-b-6">{{ state.goodsInfo.name }}</view>
            <view class="subtitle-text ss-line-1">{{ state.goodsInfo.introduction }}</view>
          </view>

          <!-- 功能卡片 -->
          <view class="detail-cell-card detail-card ss-flex-col">
            <detail-cell
              :clickable="state.portal ? !!state.tenantSlug : !!state.goodsInfo.shopId"
              :show-arrow="state.portal ? !!state.tenantSlug : !!state.goodsInfo.shopId"
              label="店铺"
              :value="state.goodsInfo.shopName || '店铺信息暂未提供'"
              @click="openProductShop"
            />
            <detail-cell-sku
              v-if="!state.portal"
              v-model="state.selectedSku.goods_sku_text"
              :sku="state.selectedSku"
              @tap="state.showSelectSku = true"
            />
          </view>

          <!-- 规格库存 -->
          <s-comparison-bar v-if="!state.portal" />
          <view class="detail-card sku-stock-card ss-p-y-20 ss-p-x-20" v-if="!state.portal && skuStockList.length">
            <view class="stock-title ss-m-b-16">规格库存</view>
            <view class="sku-stock-list">
              <view
                class="sku-stock-item ss-flex ss-row-between ss-col-start"
                v-for="sku in skuStockList"
                :key="sku.id"
              >
                <view class="sku-stock-name ss-line-2">{{ formatSkuText(sku) }}</view>
                <view class="sku-stock-meta">
                  <view class="sku-stock-price">￥{{ fen2yuan(sku.promotionPrice || sku.price || 0) }}</view>
                  <view class="sku-stock-count">库存 {{ sku.stock ?? 0 }}</view>
                  <button size="mini" :disabled="compareBusy" @tap="onCompareSku(sku)">加入对比</button>
                </view>
              </view>
            </view>
          </view>

          <view class="detail-card public-sku-card ss-p-y-20 ss-p-x-20" v-if="state.portal && skuStockList.length">
            <view class="stock-title ss-m-b-16">公开可售规格</view>
            <view class="public-sku-tip ss-m-b-16">规格与价格来自当前租户商城公开目录，最终以结算校验为准</view>
            <view class="sku-stock-list">
              <view
                class="sku-stock-item ss-flex ss-row-between ss-col-start"
                :class="{ 'public-sku-selected': state.selectedSku?.id === sku.id }"
                v-for="sku in skuStockList"
                :key="sku.id"
                @tap="selectPortalSku(sku)"
              >
                <view class="sku-stock-name ss-line-2">
                  <radio :checked="state.selectedSku?.id === sku.id" color="#137962" @tap.stop="selectPortalSku(sku)" />
                  {{ formatPublicSkuText(sku) }}
                </view>
                <view class="sku-stock-meta">
                  <view class="sku-stock-price">￥{{ fen2yuan(sku.price || 0) }}</view>
                  <view class="public-saleable">公开可售</view>
                </view>
              </view>
              <view class="portal-quantity ss-flex ss-row-between ss-col-center">
                <view>
                  <view class="portal-quantity-title">采购数量</view>
                  <view class="public-sku-tip">加入平台购物车后仍会重新校验商品归属与可售状态</view>
                </view>
                <su-number-box v-model="state.portalQuantity" :min="1" :max="99999" :disabled="state.portalCartBusy" />
              </view>
            </view>
          </view>

          <!-- 规格与数量弹框 -->
          <s-select-sku
            v-if="!state.portal && Array.isArray(state.goodsInfo?.skus) && state.goodsInfo.skus.length"
            :goodsInfo="state.goodsInfo"
            :show="state.showSelectSku"
            @addCart="onAddCart"
            @buy="onBuy"
            @change="onSkuChange"
            @close="state.showSelectSku = false"
          />
        </view>

        <!-- 评价 -->
        <detail-comment-card v-if="!state.portal" class="detail-comment-selector" :goodsId="state.goodsId" />
        <view v-else class="detail-comment-selector portal-note detail-card">
          <view class="portal-note-title">公开目录说明</view>
          <view>当前页面不读取其他租户会话中的收藏、优惠券和营销数据，避免跨租户串数据。</view>
        </view>
        <!-- 详情 -->
        <detail-content-card
          class="detail-content-selector"
          :content="state.goodsInfo.description"
        />

        <!-- 活动跳转：拼团/秒杀/砍价活动（企业采购简化模式下不展示） -->
        <detail-activity-tip
          v-if="!state.portal && !isSimpleMarketingEnabled() && state.activityList.length > 0"
          :activity-list="state.activityList"
        />

        <!-- 详情 tabbar -->
        <detail-tabbar v-if="!state.portal" v-model="state.goodsInfo">
          <view class="buy-box ss-flex ss-col-center ss-p-r-20" v-if="state.goodsInfo.stock > 0">
            <button
              class="ss-reset-button add-btn ui-Shadow-Main"
              @tap="state.showSelectSku = true"
            >
              加入购物车
            </button>
            <button
              class="ss-reset-button buy-btn ui-Shadow-Main"
              @tap="state.showSelectSku = true"
            >
              立即购买
            </button>
          </view>
          <view class="buy-box ss-flex ss-col-center ss-p-r-20" v-else>
            <button class="ss-reset-button disabled-btn" disabled> 已售罄 </button>
          </view>
        </detail-tabbar>
        <su-fixed v-else bottom placeholder bg="bg-white">
          <view class="portal-action-bar ss-flex ss-col-center">
            <button class="ss-reset-button portal-back-button" @tap="openPlatformProducts">平台商品</button>
            <button class="ss-reset-button portal-cart-button" @tap="openPlatformCart">购物车</button>
            <button class="ss-reset-button portal-shop-button" :disabled="state.portalCartBusy" @tap="addPortalCart">
              {{ state.portalCartBusy ? '正在加入' : '加入平台购物车' }}
            </button>
          </view>
        </su-fixed>

        <!-- 满减送/限时折扣活动弹窗 -->
        <s-activity-pop
          v-model="state"
          :show="state.showActivityModel"
          @close="state.showActivityModel = false"
          @get="onTakeCoupon"
        />
      </block>
      <s-empty
        v-else
        text="商品信息加载失败，请稍后重试"
        showAction
        actionText="返回商品列表"
        actionUrl="/pages/goods/list"
      />
    </s-layout>
  </view>
</template>

<script setup>
  import { reactive, computed, ref } from 'vue';
  import { onLoad, onPageScroll } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import CouponApi from '@/sheep/api/promotion/coupon';
  import ActivityApi from '@/sheep/api/promotion/activity';
  import FavoriteApi from '@/sheep/api/product/favorite';
  import RewardActivityApi from '@/sheep/api/promotion/rewardActivity';
  import {
    formatSales,
    formatGoodsSwiper,
    fen2yuan,
    fen2yuanSimple,
    formatDiscountPercent,
    getRewardActivityRuleItemDescriptions,
  } from '@/sheep/hooks/useGoods';
  import detailNavbar from './components/detail/detail-navbar.vue';
  import detailCell from './components/detail/detail-cell.vue';
  import detailCellSku from './components/detail/detail-cell-sku.vue';
  import detailTabbar from './components/detail/detail-tabbar.vue';
  import detailSkeleton from './components/detail/detail-skeleton.vue';
  import detailCommentCard from './components/detail/detail-comment-card.vue';
  import detailContentCard from './components/detail/detail-content-card.vue';
  import detailActivityTip from './components/detail/detail-activity-tip.vue';
  import { isEmpty } from 'lodash-es';
  import SpuApi from '@/sheep/api/product/spu';
  import PortalApi from '@/sheep/api/product/portal';
  import PlatformCartApi from '@/sheep/api/trade/platformCart';
  import { showAuthModal } from '@/sheep/hooks/useModal';
  import {
    normalizePortalProduct,
    normalizePortalSpuId,
    normalizePortalTenantSlug,
    portalProductListPath,
  } from '@/sheep/helper/platform-portal.mjs';
  import { platformCartPath } from '@/sheep/helper/platform-commerce.mjs';

  onPageScroll(() => {});
  import OrderApi from '@/sheep/api/trade/order';
  import { SharePageEnum } from '@/sheep/helper/const';
  import { isSimpleMarketingEnabled } from '@/sheep/helper/simplified-marketing';
  import { comparison } from '@/sheep/helper/comparison-runtime';
  import { useTenantTask } from '@/sheep/hooks/useTenantTask';
  const compareBusy = ref(false);
  const compareTask = useTenantTask(() => { compareBusy.value = false; });
  async function onCompareSku(sku) {
    if (!compareTask.current() || compareBusy.value || !skuStockList.value.some(item => item.id === sku?.id)) return;
    const current = compareTask.capture(); compareBusy.value = true;
    try {
      const added = await comparison.add(sku.id);
      if (current()) sheep.$helper.toast(added ? '已加入对比，请点击查看对比' : '清单未保存，请进入对比页刷新重试');
    } catch (error) { if (current()) sheep.$helper.toast(error?.message || '加入对比失败'); }
    finally { if (current()) compareBusy.value = false; }
  }

  const bgColor = {
    bgColor: '#E93323',
    Color: '#fff',
    width: '44rpx',
    timeTxtwidth: '16rpx',
    isDay: true,
  };
  const isLogin = computed(() => sheep.$store('user').isLogin);
  let state = reactive({
    goodsId: 0,
    portal: false,
    tenantSlug: '',
    portalQuantity: 1,
    portalCartBusy: false,
    skeletonLoading: true, // SPU 加载中
    goodsInfo: {}, // SPU 信息
    showSelectSku: false, // 是否展示 SKU 选择弹窗
    selectedSku: {}, // 选中的 SKU
    settlementSku: {}, // 结算的 SKU：由于 selectedSku 不进行默认选中，所以初始使用结算价格最低的 SKU 作为基础展示
    showModel: false, // 是否展示 Coupon 优惠劵的弹窗
    couponInfo: [], // 可领取的 Coupon 优惠劵的列表
    showActivityModel: false, // 【满减送/限时折扣】是否展示 Activity 营销活动的弹窗
    rewardActivity: {}, // 【满减送】活动
    activityList: [], // 【秒杀/拼团/砍价】可参与的 Activity 营销活动的列表
  });
  const skuStockList = computed(() => (Array.isArray(state.goodsInfo?.skus) ? state.goodsInfo.skus : []));

  // 规格变更
  function onSkuChange(e) {
    const sku = e && typeof e === 'object' ? e : {};
    state.selectedSku = sku;
    state.settlementSku = sku;
  }

  function formatSkuText(sku) {
    if (!sku?.properties?.length) {
      return '默认规格';
    }
    return sku.properties
      .filter((item) => item && typeof item === 'object')
      .map((item) => `${item.propertyName || ''}:${item.valueName || ''}`)
      .filter((item) => item !== ':')
      .join(' / ') || '默认规格';
  }

  function formatPublicSkuText(sku) {
    const propertyText = formatSkuText(sku);
    return propertyText === '默认规格' ? (sku?.skuName || propertyText) : propertyText;
  }

  function selectPortalSku(sku) {
    if (!state.portal || state.portalCartBusy || !skuStockList.value.some((item) => item.id === sku?.id)) return;
    state.selectedSku = sku;
    state.settlementSku = sku;
  }

  function openPlatformCart() {
    sheep.$router.go(platformCartPath());
  }

  async function addPortalCart() {
    if (state.portalCartBusy) return;
    if (!isLogin.value) {
      showAuthModal('accountLogin');
      return;
    }
    const skuId = Number(state.selectedSku?.id);
    const quantity = Number(state.portalQuantity);
    if (!Number.isSafeInteger(skuId) || skuId <= 0) {
      sheep.$helper.toast('请先选择商品规格');
      return;
    }
    if (!Number.isSafeInteger(quantity) || quantity < 1 || quantity > 99999) {
      sheep.$helper.toast('采购数量须为 1 至 99999 的整数');
      return;
    }
    state.portalCartBusy = true;
    try {
      const response = await PlatformCartApi.add(skuId, quantity);
      if (response?.code !== 0 || !response.data) throw new Error(response?.msg || '加入平台购物车失败');
    } catch (error) {
      sheep.$helper.toast(error?.message || '加入平台购物车失败，请稍后重试');
    } finally {
      state.portalCartBusy = false;
    }
  }

  function openPlatformProducts() {
    sheep.$router.go(portalProductListPath());
  }

  function openProductShop() {
    if (state.portal) {
      if (state.tenantSlug) {
        sheep.$router.go(portalProductListPath({ tenantSlug: state.tenantSlug }));
      }
      return;
    }
    if (state.goodsInfo?.shopId) {
      sheep.$router.go('/pages/goods/list', { shopId: state.goodsInfo.shopId });
    }
  }

  // 添加购物车
  async function onAddCart(e) {
    if (!e || typeof e !== 'object' || !e.id) {
      sheep.$helper.toast('请选择商品规格');
      return;
    }
    const added = await sheep.$store('cart').add(e);
    // 只有后端确认添加成功后才关闭规格弹窗，库存不足/请求失败时保留现场便于重试。
    if (added) state.showSelectSku = false;
  }

  // 立即购买
  function onBuy(e) {
    if (!e || typeof e !== 'object' || !e.id) {
      sheep.$helper.toast('请选择商品规格');
      return;
    }
    sheep.$router.go('/pages/order/confirm', {
      data: JSON.stringify({
        items: [
          {
            skuId: e.id,
            count: e.goods_num,
            categoryId: state.goodsInfo.categoryId,
          },
        ],
      }),
    });
  }

  // 打开营销弹窗
  function onOpenActivity() {
    state.showActivityModel = true;
  }

  // 立即领取优惠劵
  async function onTakeCoupon(id) {
    if (!id || !isLogin.value) {
      sheep.$helper.toast('请先登录后领取优惠券');
      return;
    }
    try {
      const response = await CouponApi.takeCoupon(id);
      if (response?.code !== 0) {
        return;
      }
      uni.showToast({
        title: '领取成功',
      });
      setTimeout(() => {
        getCoupon();
      }, 1000);
    } catch (error) {
      // 请求层负责展示接口错误，这里避免弹窗回调异常导致详情页崩溃。
    }
  }

  const shareInfo = computed(() => {
    if (isEmpty(state.goodsInfo)) return {};
    // 旧分享协议只能携带 tenantId，无法表达稳定的 tenantSlug。平台详情不生成
    // 旧式分享链接，避免分享后错误进入账号所属租户的同号商品。
    if (state.portal) return {};
    return sheep.$platform.share.getShareInfo(
      {
        title: state.goodsInfo.name,
        image: sheep.$url.cdn(state.goodsInfo.picUrl),
        desc: state.goodsInfo.introduction,
        params: {
          page: SharePageEnum.GOODS.value,
          query: state.goodsInfo.id,
        },
      },
      {
        type: 'goods', // 商品海报
        title: state.goodsInfo.name, // 商品名称
        image: sheep.$url.cdn(state.goodsInfo.picUrl), // 商品主图
        price: fen2yuan(state.goodsInfo.price), // 商品价格
        original_price: fen2yuan(state.goodsInfo.marketPrice), // 商品原价
      },
    );
  });

  async function getCoupon() {
    if (!state.goodsId) {
      return;
    }
    try {
      const response = await CouponApi.getCouponTemplateList(state.goodsId, 2, 10);
      if (response?.code === 0 && Array.isArray(response.data)) {
        state.couponInfo = response.data.filter(
          (coupon) => coupon && typeof coupon === 'object',
        );
      }
    } catch (error) {
      state.couponInfo = [];
    }
  }

  async function getSettlementByIds(ids) {
    try {
      const response = await OrderApi.getSettlementProduct(ids);
      let data = response?.data;
      if (response?.code !== 0 || !Array.isArray(data) || data.length !== 1) {
        return;
      }
      data = data[0];

      // 补充 SKU 的价格信息
      const promotionSkus = Array.isArray(data.skus)
        ? data.skus.filter((sku) => sku && typeof sku === 'object')
        : [];
      const goodsSkus = Array.isArray(state.goodsInfo?.skus)
        ? state.goodsInfo.skus.filter((sku) => sku && typeof sku === 'object')
        : [];
      goodsSkus.forEach((sku) => {
        promotionSkus.forEach((item) => {
          if (sku.id === item.id) {
            sku.promotionType = item.promotionType;
            sku.promotionPrice = item.promotionPrice;
            sku.promotionId = item.promotionId;
            sku.promotionEndTime = item.promotionEndTime;
          }
        });
      });

      // 选择有 promotionPrice 且最小的
      const promotionSku = goodsSkus
        .filter((sku) => sku.stock > 0 && sku.promotionPrice > 0)
        .reduce(
          (prev, curr) => (!prev || curr.promotionPrice < prev.promotionPrice ? curr : prev),
          null,
        );
      state.settlementSku = promotionSku || {};

      // 设置满减送活动
      if (data.rewardActivity) {
        state.rewardActivity = data.rewardActivity;
        //获取活动时间
        getActivityTime(state.rewardActivity.id);
      }
    } catch (error) {
      state.settlementSku = {};
    }
  }

  //获取活动时间
  async function getActivityTime(id) {
    if (!id) {
      return;
    }
    try {
      const response = await RewardActivityApi.getRewardActivity(id);
      if (response?.code === 0 && response.data) {
        // console.log('获取到的活动 数据', data)
        state.rewardActivity.startTime = response.data.startTime;
        state.rewardActivity.endTime = response.data.endTime;
      }
    } catch (error) {
      // 活动时间仅用于展示，不应影响商品详情和下单。
    }
  }

  async function loadPortalProduct() {
    try {
      const response = state.tenantSlug
        ? await PortalApi.getTenantProduct(state.tenantSlug, state.goodsId)
        : await PortalApi.getProduct(state.goodsId);
      const product = response?.code === 0 ? normalizePortalProduct(response.data) : null;
      if (!product || product.spuId !== state.goodsId
          || (state.tenantSlug && product.tenantSlug !== state.tenantSlug)) {
        throw new Error('公开商品不可用');
      }
      state.goodsInfo = product;
      state.tenantSlug = product.tenantSlug;
      state.selectedSku = product.skus[0] || {};
      state.settlementSku = state.selectedSku;
      state.portalQuantity = 1;
    } catch (error) {
      state.goodsInfo = null;
    } finally {
      state.skeletonLoading = false;
    }
  }

  onLoad((options = {}) => {
    // 非法参数
    if (!options?.id) {
      state.goodsInfo = null;
      state.skeletonLoading = false;
      return;
    }
    try {
      state.goodsId = Number(normalizePortalSpuId(options.id));
      state.portal = options.portal === '1' || Boolean(options.tenantSlug);
      state.tenantSlug = options.tenantSlug ? normalizePortalTenantSlug(options.tenantSlug) : '';
    } catch {
      state.goodsInfo = null;
      state.skeletonLoading = false;
      return;
    }
    if (state.portal) {
      loadPortalProduct();
      return;
    }
    // 1. 加载商品信息
    SpuApi.getSpuDetail(state.goodsId).then((res) => {
      if (res?.code !== 0 || !res?.data) {
        state.goodsInfo = null;
        state.skeletonLoading = false;
        return;
      }
      // 加载到商品
      state.skeletonLoading = false;
      state.goodsInfo = res.data;
      // 获取结算信息。该接口需要会员上下文，未登录时跳过，避免匿名
      // 请求触发 trade 服务的空 userId Feign 调用。
      if (isLogin.value) {
        getSettlementByIds(state.goodsId);
      }
      // 加载是否收藏
      if (isLogin.value) {
        FavoriteApi.isFavoriteExists(state.goodsId, 'goods').then((res) => {
          if (res?.code !== 0) {
            return;
          }
          state.goodsInfo.favorite = res?.data === true;
        }).catch(() => {
          state.goodsInfo.favorite = false;
        });
      }
    }).catch(() => {
      state.goodsInfo = null;
      state.skeletonLoading = false;
    });

    // 2. 加载优惠劵信息
    if (isLogin.value) {
      getCoupon();
    }

    // 3. 加载营销活动信息。企业采购简化模式不展示也不请求
    // 拼团/秒杀/砍价活动，避免旧活动数据再次出现在用户端。
    if (!isSimpleMarketingEnabled()) {
      ActivityApi.getActivityListBySpuId(state.goodsId).then((res) => {
        if (res?.code !== 0) {
          return;
        }
        state.activityList = Array.isArray(res?.data) ? res.data : [];
      }).catch(() => {
        state.activityList = [];
      });
    }
  });
</script>

<style lang="scss" scoped>
  .detail-card {
    background-color: #ffff;
    margin: 14rpx 20rpx;
    border-radius: 10rpx;
    overflow: hidden;
  }

  // 价格标题卡片
  .title-card {
    .price-box {
      .price-text {
        font-size: 42rpx;
        font-weight: 500;
        color: #ff3000;
        line-height: 30rpx;
        font-family: OPPOSANS;

        &::before {
          content: '￥';
          font-size: 30rpx;
        }
      }

      .origin-price-text {
        font-size: 26rpx;
        font-weight: 400;
        text-decoration: line-through;
        color: $gray-c;
        font-family: OPPOSANS;

        &::before {
          content: '￥';
        }
      }
    }

    .sales-text {
      font-size: 26rpx;
      font-weight: 500;
      color: $gray-c;
    }

    .discounts-box {
      .tag-content {
        flex: 1;
        min-width: 0;
        white-space: nowrap;
      }

      .tag-box {
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .tag {
        flex-shrink: 0;
        padding: 4rpx 10rpx;
        font-size: 24rpx;
        font-weight: 500;
        border-radius: 4rpx;
        color: var(--ui-BG-Main);
        background: var(--ui-BG-Main-tag);
      }

      .discounts-title {
        font-size: 24rpx;
        font-weight: 500;
        color: var(--ui-BG-Main);
        line-height: normal;
      }

      .cicon-forward {
        color: var(--ui-BG-Main);
        font-size: 24rpx;
        line-height: normal;
        margin-top: 4rpx;
      }
    }

    .title-text {
      font-size: 30rpx;
      font-weight: bold;
      line-height: 42rpx;
    }

    .subtitle-text {
      font-size: 26rpx;
      font-weight: 400;
      color: $dark-9;
      line-height: 42rpx;
    }
  }

  // 购买
  .buy-box {
    .add-btn {
      width: 214rpx;
      height: 82rpx;
      font-weight: 600;
      font-size: 28rpx;
      border-radius: 40rpx 0 0 40rpx;
      background-color: #e2f3eb;
      color: #156b52;
    }

    .buy-btn {
      width: 214rpx;
      height: 82rpx;
      font-weight: 600;
      font-size: 28rpx;

      border-radius: 0 40rpx 40rpx 0;
      background: linear-gradient(110deg, #137962, #105849);
      color: $white;
    }

    .disabled-btn {
      width: 428rpx;
      height: 72rpx;
      border-radius: 40rpx;
      background: #999999;
      color: $white;
    }
  }

  .model-box {
    height: 60vh;

    .model-content {
      height: 56vh;
    }

    .title {
      font-size: 36rpx;
      font-weight: bold;
      color: #333333;
    }

    .subtitle {
      font-size: 26rpx;
      font-weight: 500;
      color: #333333;
    }
  }

  // 限时折扣
  .discount {
    width: 100%;
    height: 100rpx;
    background: linear-gradient(110deg, #137962, #105849);
    overflow: hidden;
    position: relative;
  }

  .discountCont {
    width: 680rpx;
    height: 90rpx;
    margin: 10rpx auto 0 auto;
    // background-color: gold;
  }

  .disContT {
    width: 680rpx;
    height: 50rpx;
    display: flex;
    justify-content: space-between;
  }

  .disContT1 {
    width: 400rpx;
    height: 50rpx;
    // background-color: green;
    display: flex;
    justify-content: flex-start;
    align-items: center;
  }

  .disContT2 {
    width: 200rpx;
    height: 50rpx;
    line-height: 50rpx;
    // background-color: gold;
    font-size: 30rpx;
    text-align: end;
    color: white;
    font-weight: bolder;
    font-style: oblique 20deg;
    letter-spacing: 0.1rem;
  }

  .disContT1P {
    color: white;
    font-weight: bold;
    font-size: 28rpx;
  }

  .disContT1End {
    // width: 180rpx;
    padding: 0 10rpx;
    height: 30rpx;
    line-height: 28rpx;
    text-align: center;
    font-weight: bold;
    background-color: white;
    color: #ff3000;
    font-size: 23rpx;
    border-radius: 20rpx;
    margin-left: 10rpx;
  }

  .disContB {
    width: 680rpx;
    height: 40rpx;
    display: flex;
    justify-content: space-between;
    font-size: 20rpx;
    color: white;
    align-items: center;
  }

  .disContB1 {
    width: 300rpx;
    height: 40rpx;
    line-height: 40rpx;
  }

  .disContB2 {
    width: 300rpx;
    height: 40rpx;
    line-height: 40rpx;
    display: flex;
    justify-content: flex-end;
  }

  .sku-stock-card,
  .public-sku-card {
    .stock-title {
      font-size: 28rpx;
      font-weight: 600;
      color: #333333;
    }

    .sku-stock-item {
      padding: 20rpx 0;
      border-top: 1rpx solid #f2f2f2;

      &:first-child {
        border-top: 0;
        padding-top: 0;
      }
    }

    .sku-stock-name {
      flex: 1;
      min-width: 0;
      font-size: 26rpx;
      color: #333333;
      line-height: 36rpx;
    }

    .sku-stock-meta {
      flex-shrink: 0;
      text-align: right;
      font-size: 24rpx;
      color: #666666;
    }

    .sku-stock-price {
      font-weight: 600;
      color: #ff3000;
    }

    .sku-stock-count {
      margin-top: 6rpx;
    }

    .public-sku-selected {
      margin: 0 -12rpx;
      padding-right: 12rpx;
      padding-left: 12rpx;
      border-radius: 10rpx;
      background: #eef9f6;
    }
  }

  .portal-owner-card {
    padding: 24rpx 26rpx;
    color: #e7fff7;
    background: linear-gradient(120deg, #102f40, #167564);

    .portal-owner-kicker {
      font-size: 21rpx;
      letter-spacing: 4rpx;
      opacity: 0.78;
    }

    .portal-owner-name {
      margin-top: 10rpx;
      font-size: 32rpx;
      font-weight: 700;
    }

    .portal-owner-meta {
      margin-top: 8rpx;
      font-size: 23rpx;
      opacity: 0.88;
    }
  }

  .public-sku-tip,
  .public-saleable,
  .portal-note {
    color: #64748b;
    font-size: 23rpx;
    line-height: 36rpx;
  }

  .public-saleable {
    margin-top: 6rpx;
    color: #137962;
  }

  .portal-quantity {
    margin-top: 12rpx;
    padding-top: 20rpx;
    border-top: 1rpx solid #edf2f1;
  }

  .portal-quantity-title {
    color: #263d42;
    font-size: 26rpx;
    font-weight: 600;
  }

  .portal-note {
    padding: 26rpx;

    .portal-note-title {
      margin-bottom: 10rpx;
      color: #0f172a;
      font-size: 27rpx;
      font-weight: 700;
    }
  }

  .portal-action-bar {
    gap: 16rpx;
    padding: 14rpx 20rpx calc(14rpx + env(safe-area-inset-bottom));

    button {
      height: 76rpx;
      border-radius: 38rpx;
      font-size: 27rpx;
    }

    .portal-back-button {
      width: 150rpx;
      color: #156b52;
      background: #e2f3eb;
    }

    .portal-cart-button {
      width: 150rpx;
      color: #156b52;
      border: 1rpx solid #99c5b9;
      background: #fff;
    }

    .portal-shop-button {
      flex: 1;
      color: #fff;
      background: linear-gradient(110deg, #137962, #105849);
    }
  }
</style>
