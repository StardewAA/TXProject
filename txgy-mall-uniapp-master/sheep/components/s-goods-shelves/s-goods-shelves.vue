<!-- 装修商品组件：商品栏 -->
<template>
  <view>
    <!-- 布局1. 两列商品，图片左文案右 -->
    <view
      v-if="layoutType === 'twoCol'"
      class="goods-xs-box ss-flex ss-flex-wrap"
      :style="[{ margin: '-' + safeData.space + 'rpx' }]"
    >
      <view
        class="goods-xs-list"
        v-for="item in goodsList"
        :key="item.id"
        :style="[
          {
            padding: safeData.space + 'rpx',
          },
        ]"
      >
        <s-goods-column
          class="goods-card"
          size="xs"
          :goodsFields="safeData.fields"
          :tagStyle="safeData.badge"
          :data="item"
          :titleColor="safeData.fields?.name?.color"
          :topRadius="safeData.borderRadiusTop"
          :bottomRadius="safeData.borderRadiusBottom"
          :titleWidth="(454 - marginRight * 2 - safeData.space * 2 - marginLeft * 2) / 2"
          @click="sheep.$router.go('/pages/goods/index', { id: item.id })"
        />
      </view>
    </view>
    <!-- 布局. 三列商品：图片上文案下 -->
    <view
      v-if="layoutType === 'threeCol'"
      class="goods-sm-box ss-flex ss-flex-wrap"
      :style="[{ margin: '-' + safeData.space + 'rpx' }]"
    >
      <view
        v-for="item in goodsList"
        :key="item.id"
        class="goods-card-box"
        :style="[
          {
            padding: safeData.space + 'rpx',
          },
        ]"
      >
        <s-goods-column
          class="goods-card"
          size="sm"
          :goodsFields="safeData.fields"
          :tagStyle="safeData.badge"
          :data="item"
          :titleColor="safeData.fields?.name?.color"
          :topRadius="safeData.borderRadiusTop"
          :bottomRadius="safeData.borderRadiusBottom"
          @click="sheep.$router.go('/pages/goods/index', { id: item.id })"
        />
      </view>
    </view>

    <!-- 布局3. 一行商品，水平滑动 -->
    <view v-if="layoutType === 'horizSwiper'" class="">
      <scroll-view class="scroll-box goods-scroll-box" scroll-x scroll-anchoring>
        <view class="goods-box ss-flex">
          <view
            class="goods-card-box"
            v-for="item in goodsList"
            :key="item.id"
            :style="[{ marginRight: safeData.space * 2 + 'rpx' }]"
          >
            <s-goods-column
              class="goods-card"
              size="sm"
              :goodsFields="safeData.fields"
              :tagStyle="safeData.badge"
              :data="item"
              :titleColor="safeData.fields?.name?.color"
              :titleWidth="(750 - marginRight * 2 - safeData.space * 4 - marginLeft * 2) / 3"
              @click="sheep.$router.go('/pages/goods/index', { id: item.id })"
            />
          </view>
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<script setup>
  /**
   * 商品栏
   */
  import { computed, onMounted, ref } from 'vue';
  import sheep from '@/sheep';
  import SpuApi from '@/sheep/api/product/spu';

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
        title: isRecord(sourceFields.title) ? sourceFields.title : {},
        price: isRecord(sourceFields.price) ? sourceFields.price : {},
        marketPrice: isRecord(sourceFields.marketPrice) ? sourceFields.marketPrice : {},
        original_price: isRecord(sourceFields.original_price) ? sourceFields.original_price : {},
        introduction: isRecord(sourceFields.introduction) ? sourceFields.introduction : {},
        subtitle: isRecord(sourceFields.subtitle) ? sourceFields.subtitle : {},
        salesCount: isRecord(sourceFields.salesCount) ? sourceFields.salesCount : {},
        stock: isRecord(sourceFields.stock) ? sourceFields.stock : {},
      },
      badge: isRecord(source.badge) ? source.badge : {},
      spuIds: Array.isArray(source.spuIds)
        ? source.spuIds.filter((id) => id !== undefined && id !== null && String(id) !== '')
        : [],
    };
  });
  const safeStyles = computed(() => (isRecord(props.styles) ? props.styles : {}));
  const layoutType = computed(() => safeData.value.layoutType);
  const marginLeft = computed(() => Math.max(0, toNumber(safeStyles.value.marginLeft)));
  const marginRight = computed(() => Math.max(0, toNumber(safeStyles.value.marginRight)));
  const goodsList = ref([]);
  onMounted(async () => {
    if (safeData.value.spuIds.length === 0) {
      return;
    }
    try {
      const response = await SpuApi.getSpuListByIds(safeData.value.spuIds.join(','));
      goodsList.value =
        response?.code === 0 && Array.isArray(response.data)
          ? response.data.filter(
              (item) => isRecord(item) && item.id !== undefined && item.id !== null,
            )
          : [];
    } catch (error) {
      goodsList.value = [];
    }
  });
</script>

<style lang="scss" scoped>
  .goods-xs-box {
    // margin: 0 auto;
    width: 100%;
    .goods-xs-list {
      box-sizing: border-box;
      flex-shrink: 0;
      overflow: hidden;
      width: 50%;
    }
  }

  .goods-sm-box {
    margin: 0 auto;
    box-sizing: border-box;
    .goods-card-box {
      flex-shrink: 0;
      overflow: hidden;
      width: 33.3%;
      box-sizing: border-box;
    }
  }
  .goods-scroll-box {
    margin: 0 auto;
    width: 100%;
    box-sizing: border-box;
  }
</style>
