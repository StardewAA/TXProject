<!-- 装修基础组件：列表导航 -->
<template>
  <view v-if="menuItems.length" class="menu-list-wrap">
    <uni-list :border="true">
      <uni-list-item
        v-for="(item, index) in menuItems"
        :key="index"
        showArrow
        clickable
        @tap="sheep.$router.go(item.url)"
      >
        <template v-slot:header>
          <view class="ss-flex ss-col-center">
            <image
              v-if="item.iconUrl"
              class="list-icon"
              :src="sheep.$url.cdn(item.iconUrl)"
              mode="aspectFit"
            ></image>
            <view
              class="title-text ss-flex ss-row-center ss-col-center ss-m-l-20"
              :style="[{ color: item.titleColor }]"
            >
              {{ item.title }}
            </view>
          </view>
        </template>
        <template v-slot:footer>
          <view
            class="notice-text ss-flex ss-row-center ss-col-center"
            :style="[{ color: item.subtitleColor }]"
          >
            {{ item.subtitle }}
          </view>
        </template>
      </uni-list-item>
    </uni-list>
  </view>
</template>

<script setup>
  /**
   * cell
   */
  import sheep from '@/sheep';
  import { computed } from 'vue';
  import { filterMarketingMenuItems } from '@/sheep/helper/simplified-marketing';

  const props = defineProps({
    data: {
      type: Object,
      default: () => ({}),
    },
  });

  const menuItems = computed(() => filterMarketingMenuItems(props.data?.list));
</script>

<style lang="scss">
  .menu-list-wrap {
    padding: 4rpx 0;

    .uni-list {
      background-color: transparent;
    }

    .uni-list-item {
      margin: 12rpx 0;
      border-radius: 24rpx;
      overflow: hidden;
      background: linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(248, 250, 252, 0.95));
      border: 1rpx solid rgba(148, 163, 184, 0.12);
      box-shadow: var(--ui-Surface-shadow-soft);
    }

    .uni-list-item__container {
      padding: 22rpx 24rpx;
    }
  }

  .list-icon {
    width: 44rpx;
    height: 44rpx;
    border-radius: 14rpx;
    background: rgba(59, 130, 246, 0.08);
  }

  .title-text {
    font-size: 28rpx;
    font-weight: 500;
    color: #1f2937;
  }

  .notice-text {
    font-size: 24rpx;
    color: #94a3b8;
  }
</style>
