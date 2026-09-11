<!-- 装修商品组件：商品卡片 -->
<template>
  <!-- 商品卡片 -->
  <view>
    <!-- 布局1. 单列大图（上图，下内容）-->
    <view
      v-if="layoutType === LayoutTypeEnum.ONE_COL_BIG_IMG && state.goodsList.length"
      class="goods-sl-box"
    >
      <view
        class="goods-box"
        v-for="item in state.goodsList"
        :key="item.id"
        :style="[{ marginBottom: safeData.space * 2 + 'rpx' }]"
      >
        <s-goods-column
          class=""
          size="sl"
          :goodsFields="safeData.fields"
          :tagStyle="safeData.badge"
          :data="item"
          :titleColor="safeData.fields?.name?.color"
          :subTitleColor="safeData.fields?.introduction?.color"
          :topRadius="safeData.borderRadiusTop"
          :bottomRadius="safeData.borderRadiusBottom"
          @click="sheep.$router.go('/pages/goods/index', { id: item.id })"
        >
          <!-- 购买按钮 -->
          <template v-slot:cart>
            <button class="ss-reset-button cart-btn" :style="[buyStyle]">
              {{ btnBuy.type === 'text' ? btnBuy.text : '' }}
            </button>
          </template>
        </s-goods-column>
      </view>
    </view>

    <!-- 布局2. 双列（每一列：上图，下内容）-->
    <view
      v-if="layoutType === LayoutTypeEnum.TWO_COL && state.goodsList.length"
      class="goods-md-wrap ss-flex ss-flex-wrap ss-col-top"
    >
      <view class="goods-list-box">
        <view
          class="left-list"
          :style="[{ paddingRight: safeData.space + 'rpx', marginBottom: safeData.space + 'px' }]"
          v-for="item in state.leftGoodsList"
          :key="item.id"
        >
          <s-goods-column
            class="goods-md-box"
            size="md"
            :goodsFields="safeData.fields"
            :tagStyle="safeData.badge"
            :data="item"
            :titleColor="safeData.fields?.name?.color"
            :subTitleColor="safeData.fields?.introduction?.color"
            :topRadius="safeData.borderRadiusTop"
            :bottomRadius="safeData.borderRadiusBottom"
            :titleWidth="330 - marginLeft - marginRight"
            @click="sheep.$router.go('/pages/goods/index', { id: item.id })"
            @getHeight="calculateGoodsColumn($event, 'left')"
          >
            <!-- 购买按钮 -->
            <template v-slot:cart>
              <button class="ss-reset-button cart-btn" :style="[buyStyle]">
                {{ btnBuy.type === 'text' ? btnBuy.text : '' }}
              </button>
            </template>
          </s-goods-column>
        </view>
      </view>
      <view class="goods-list-box">
        <view
          class="right-list"
          :style="[{ paddingLeft: safeData.space + 'rpx', marginBottom: safeData.space + 'px' }]"
          v-for="item in state.rightGoodsList"
          :key="item.id"
        >
          <s-goods-column
            class="goods-md-box"
            size="md"
            :goodsFields="safeData.fields"
            :tagStyle="safeData.badge"
            :data="item"
            :titleColor="safeData.fields?.name?.color"
            :subTitleColor="safeData.fields?.introduction?.color"
            :topRadius="safeData.borderRadiusTop"
            :bottomRadius="safeData.borderRadiusBottom"
            :titleWidth="330 - marginLeft - marginRight"
            @click="sheep.$router.go('/pages/goods/index', { id: item.id })"
            @getHeight="calculateGoodsColumn($event, 'right')"
          >
            <!-- 购买按钮 -->
            <template v-slot:cart>
              <button class="ss-reset-button cart-btn" :style="[buyStyle]">
                {{ btnBuy.type === 'text' ? btnBuy.text : '' }}
              </button>
            </template>
          </s-goods-column>
        </view>
      </view>
    </view>

    <!-- 布局3. 单列小图（左图，右内容） -->
    <view
      v-if="layoutType === LayoutTypeEnum.ONE_COL_SMALL_IMG && state.goodsList.length"
      class="goods-lg-box"
    >
      <view
        class="goods-box"
        :style="[{ marginBottom: safeData.space + 'px' }]"
        v-for="item in state.goodsList"
        :key="item.id"
      >
        <s-goods-column
          class="goods-card"
          size="lg"
          :goodsFields="safeData.fields"
          :data="item"
          :tagStyle="safeData.badge"
          :titleColor="safeData.fields?.name?.color"
          :subTitleColor="safeData.fields?.introduction?.color"
          :topRadius="safeData.borderRadiusTop"
          :bottomRadius="safeData.borderRadiusBottom"
          @tap="sheep.$router.go('/pages/goods/index', { id: item.id })"
        >
          <!-- 购买按钮 -->
          <template v-slot:cart>
            <button class="ss-reset-button cart-btn" :style="[buyStyle]">
              {{ btnBuy.type === 'text' ? btnBuy.text : '' }}
            </button>
          </template>
        </s-goods-column>
      </view>
    </view>
  </view>
</template>

<script setup>
  /**
   * 商品卡片
   */
  import { computed, reactive, onMounted } from 'vue';
  import sheep from '@/sheep';
  import SpuApi from '@/sheep/api/product/spu';
  import OrderApi from '@/sheep/api/trade/order';
  import { appendSettlementProduct } from '@/sheep/hooks/useGoods';

  // 布局类型
  const LayoutTypeEnum = {
    // 单列大图
    ONE_COL_BIG_IMG: 'oneColBigImg',
    // 双列
    TWO_COL: 'twoCol',
    // 单列小图
    ONE_COL_SMALL_IMG: 'oneColSmallImg',
  };

  const state = reactive({
    goodsList: [],
    leftGoodsList: [],
    rightGoodsList: [],
  });
  const props = defineProps({
    data: {
      type: Object,
      default: () => ({}),
    },
    styles: {
      type: Object,
      default: () => ({}),
    },
  });

  const isRecord = (value) => value && typeof value === 'object' && !Array.isArray(value);
  const toNumber = (value, fallback = 0) => {
    const number = Number(value);
    return Number.isFinite(number) ? number : fallback;
  };

  const safeData = computed(() => {
    const source = isRecord(props.data) ? props.data : {};
    const sourceFields = isRecord(source.fields) ? source.fields : {};
    return {
      ...source,
      layoutType: source.layoutType || '',
      space: Math.max(0, toNumber(source.space)),
      borderRadiusTop: Math.max(0, toNumber(source.borderRadiusTop)),
      borderRadiusBottom: Math.max(0, toNumber(source.borderRadiusBottom)),
      fields: {
        ...sourceFields,
        name: isRecord(sourceFields.name) ? sourceFields.name : {},
        introduction: isRecord(sourceFields.introduction) ? sourceFields.introduction : {},
        title: isRecord(sourceFields.title) ? sourceFields.title : {},
        subtitle: isRecord(sourceFields.subtitle) ? sourceFields.subtitle : {},
        price: isRecord(sourceFields.price) ? sourceFields.price : {},
        marketPrice: isRecord(sourceFields.marketPrice) ? sourceFields.marketPrice : {},
        original_price: isRecord(sourceFields.original_price) ? sourceFields.original_price : {},
        salesCount: isRecord(sourceFields.salesCount) ? sourceFields.salesCount : {},
        stock: isRecord(sourceFields.stock) ? sourceFields.stock : {},
      },
      badge: isRecord(source.badge) ? source.badge : {},
      btnBuy: isRecord(source.btnBuy) ? source.btnBuy : {},
      spuIds: Array.isArray(source.spuIds)
        ? source.spuIds.filter((id) => id !== undefined && id !== null && String(id) !== '')
        : [],
    };
  });
  const safeStyles = computed(() => (isRecord(props.styles) ? props.styles : {}));
  const layoutType = computed(() => safeData.value.layoutType);
  const btnBuy = computed(() => safeData.value.btnBuy);
  const spuIds = computed(() => safeData.value.spuIds);
  const marginLeft = computed(() => Math.max(0, toNumber(safeStyles.value.marginLeft)));
  const marginRight = computed(() => Math.max(0, toNumber(safeStyles.value.marginRight)));

  // 购买按钮样式
  const buyStyle = computed(() => {
    if (btnBuy.value.type === 'text') {
      // 文字按钮：线性渐变背景颜色
      return {
        background: `linear-gradient(to right, ${btnBuy.value.bgBeginColor || '#0f766e'}, ${
          btnBuy.value.bgEndColor || '#0d9488'
        })`,
      };
    }
    if (btnBuy.value.type === 'img' && btnBuy.value.imgUrl) {
      // 图片按钮
      return {
        width: '54rpx',
        height: '54rpx',
        background: `url(${sheep.$url.cdn(btnBuy.value.imgUrl)}) no-repeat`,
        backgroundSize: '100% 100%',
      };
    }
    return {};
  });

  //region 商品瀑布流布局
  // 下一个要处理的商品索引
  let count = 0;
  // 左列的高度
  let leftHeight = 0;
  // 右列的高度
  let rightHeight = 0;

  /**
   * 计算商品在左列还是右列
   * @param height 商品的高度
   * @param where 添加到哪一列
   */
  function calculateGoodsColumn(height = 0, where = 'left') {
    // 处理完
    if (!state.goodsList[count]) return;
    const cardHeight = Math.max(0, toNumber(height));
    // 增加列的高度
    if (where === 'left') leftHeight += cardHeight;
    if (where === 'right') rightHeight += cardHeight;
    // 添加到矮的一列
    if (leftHeight <= rightHeight) {
      state.leftGoodsList.push(state.goodsList[count]);
    } else {
      state.rightGoodsList.push(state.goodsList[count]);
    }
    // 计数
    count++;
  }

  //endregion

  /**
   * 根据商品编号列表，获取商品列表
   * @param ids 商品编号列表
   * @return {Promise<undefined>} 商品列表
   */
  async function getGoodsListByIds(ids) {
    if (!ids) {
      return [];
    }
    try {
      const response = await SpuApi.getSpuListByIds(ids);
      return response?.code === 0 && Array.isArray(response.data)
        ? response.data.filter(
            (item) => isRecord(item) && item.id !== undefined && item.id !== null,
          )
        : [];
    } catch (error) {
      return [];
    }
  }

  // 初始化
  onMounted(async () => {
    // 加载商品列表
    state.goodsList = await getGoodsListByIds(spuIds.value.join(','));
    // 重置瀑布流状态，避免组件复用时旧商品列数据污染当前渲染。
    state.leftGoodsList = [];
    state.rightGoodsList = [];
    count = 0;
    leftHeight = 0;
    rightHeight = 0;
    if (!state.goodsList.length) {
      return;
    }
    // 拼接结算信息（营销）
    try {
      const response = await OrderApi.getSettlementProduct(
        state.goodsList.map((item) => item.id).join(','),
      );
      if (response?.code === 0 && Array.isArray(response.data)) {
        appendSettlementProduct(state.goodsList, response.data);
      }
    } catch (error) {
      // 营销接口不可用时仍展示基础商品，不能阻断 DIY 页面。
    }
    // 只有双列布局时需要
    if (layoutType.value === LayoutTypeEnum.TWO_COL) {
      // 分列
      calculateGoodsColumn();
    }
  });
</script>

<style lang="scss" scoped>
  .goods-md-wrap {
    width: 100%;
  }

  .goods-list-box {
    width: 50%;
    box-sizing: border-box;

    .left-list {
      &:nth-last-child(1) {
        margin-bottom: 0 !important;
      }
    }

    .right-list {
      &:nth-last-child(1) {
        margin-bottom: 0 !important;
      }
    }
  }

  .goods-box {
    &:nth-last-of-type(1) {
      margin-bottom: 0 !important;
    }
  }

  .goods-md-box,
  .goods-sl-box,
  .goods-lg-box {
    position: relative;

    .cart-btn {
      position: absolute;
      bottom: 18rpx;
      right: 20rpx;
      z-index: 11;
      height: 50rpx;
      line-height: 50rpx;
      padding: 0 20rpx;
      border-radius: 25rpx;
      font-size: 24rpx;
      color: #fff;
    }
  }
</style>
