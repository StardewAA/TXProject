<template>
  <su-fixed v-if="enabledActivityList.length" bottom placeholder :val="44">
    <view>
      <view v-for="activity in enabledActivityList" :key="activity.id">
        <view
          class="activity-box ss-p-x-38 ss-flex ss-row-between ss-col-center"
          :class="activity.type === 1 ? 'seckill-box' : 'groupon-box'"
        >
          <view class="activity-title ss-flex">
            <view class="ss-m-r-16">
              <image
                v-if="activity.type === 1"
                :src="sheep.$url.static('/static/img/shop/goods/seckill-icon.png')"
                class="activity-icon"
              />
              <image
                v-else-if="activity.type === 3"
                :src="sheep.$url.static('/static/img/shop/goods/groupon-icon.png')"
                class="activity-icon"
              />
            </view>
            <view>该商品正在参与{{ activity.name }}活动</view>
          </view>
          <button class="ss-reset-button activity-go" @tap="onActivity(activity)"> GO </button>
        </view>
      </view>
    </view>
  </su-fixed>
</template>

<script setup>
  import { computed } from 'vue';
  import sheep from '@/sheep';
  import { isSimpleMarketingEnabled } from '@/sheep/helper/simplified-marketing';

  const seckillBg = sheep.$url.css('/static/img/shop/goods/seckill-tip-bg.png');
  const grouponBg = sheep.$url.css('/static/img/shop/goods/groupon-tip-bg.png');

  const props = defineProps({
    activityList: {
      type: Array,
      default() {
        return [];
      },
    },
  });

  // 工业品商城暂不开放秒杀、砍价、拼团；即使旧接口返回活动，
  // 商品详情也不再展示消费型活动入口。
  const enabledActivityList = computed(() => {
    if (isSimpleMarketingEnabled()) {
      return [];
    }
    // 详情提示组件目前只支持秒杀(type=1)和拼团(type=3)跳转；其它活动
    // 由各自页面处理，避免渲染出无法点击的“GO”按钮。
    return (Array.isArray(props.activityList) ? props.activityList : []).filter((activity) =>
      [1, 3].includes(Number(activity?.type)),
    );
  });

  function onActivity(activity) {
    const type = activity.type;
    const typePath = type === 1 ? 'seckill' : type === 3 ? 'groupon' : undefined;
    if (!typePath) {
      return;
    }
    sheep.$router.go(`/pages/goods/${typePath}`, {
      id: activity.id,
    });
  }
</script>

<style lang="scss" scoped>
  .activity-box {
    width: 100%;
    height: 80rpx;
    box-sizing: border-box;
    margin-bottom: 10rpx;

    .activity-title {
      font-size: 26rpx;
      font-weight: 500;
      color: #ffffff;
      line-height: 42rpx;

      .activity-icon {
        width: 38rpx;
        height: 38rpx;
      }
    }

    .activity-go {
      width: 70rpx;
      height: 32rpx;
      background: #ffffff;
      border-radius: 16rpx;
      font-weight: 500;
      color: #ff6000;
      font-size: 24rpx;
      line-height: normal;
    }
  }

  //秒杀卡片
  .seckill-box {
    background: v-bind(seckillBg) no-repeat;
    background-size: 100% 100%;
  }

  .groupon-box {
    background: v-bind(grouponBg) no-repeat;
    background-size: 100% 100%;
  }
</style>
