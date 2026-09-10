<!-- 积分商城：商品列表  -->
<template>
  <s-layout title="积分商城" navbar="normal" :leftWidth="0" :rightWidth="0">
    <scroll-view
      class="scroll-box"
      :style="{ height: pageHeight + 'rpx' }"
      scroll-y="true"
      :scroll-with-animation="false"
      :enable-back-to-top="true"
    >
      <s-point-card ref="sPointCardRef" class="ss-p-x-20 ss-m-t-20" />
      <s-empty
        v-if="activityCount === 0 && loadStatus !== 'loading'"
        icon="/static/goods-empty.png"
        text="暂无积分商品"
      ></s-empty>
      <uni-load-more
        v-if="loading || (activityCount > 0 && loadStatus !== 'noMore')"
        :status="loadStatus"
        :content-text="{
          contentdown: '上拉加载更多',
        }"
        @tap="loadMore"
      />
    </scroll-view>
  </s-layout>
</template>
<script setup>
  import sheep from '@/sheep';
  import { onReachBottom } from '@dcloudio/uni-app';
  import { onMounted, reactive, ref } from 'vue';
  import PointApi from '@/sheep/api/promotion/point';
  import SLayout from '@/sheep/components/s-layout/s-layout.vue';

  // 计算页面高度
  const device = sheep.$platform?.device || {};
  const safeAreaInsets = device.safeAreaInsets || { bottom: 0 };
  const safeArea = device.safeArea || { height: 667 };
  const statusBarHeight = (Number(device.statusBarHeight) || 0) * 2;
  const navbarHeight = Number(sheep.$platform?.navbar) || 0;
  const pageHeight =
    ((Number(safeArea.height) || 667) + (Number(safeAreaInsets.bottom) || 0)) * 2 +
    statusBarHeight - navbarHeight - 350;

  const sPointCardRef = ref();
  // 查询活动列表
  const activityPageParams = reactive({
    pageNo: 1, // 页码
    pageSize: 5, // 每页数量
  });

  const activityTotal = ref(0); // 活动总数
  const activityCount = ref(0); // 已加载活动数量
  const loadStatus = ref(''); // 页面加载状态
  const loading = ref(false);
  async function getActivityList() {
    if (loading.value || (activityPageParams.pageNo > 1 && loadStatus.value === 'noMore')) {
      return;
    }
    loading.value = true;
    loadStatus.value = 'loading';
    try {
      const response = await PointApi.getPointActivityPage(activityPageParams);
      const data =
        response?.code === 0 && response.data && typeof response.data === 'object'
          ? response.data
          : {};
      const list = Array.isArray(data.list) ? data.list : [];
      const total = Number(data.total) || 0;
      // onMounted 后 ref 才可用；接口返回的失效 SPU 会由组件过滤掉。
      if (sPointCardRef.value && list.length) {
        await sPointCardRef.value.concatActivity(list);
        activityCount.value = sPointCardRef.value.getActivityCount();
      }
      activityTotal.value = total;
      loadStatus.value =
        list.length === 0 || activityCount.value >= activityTotal.value ? 'noMore' : 'more';
    } catch (error) {
      // 活动数据异常时展示空状态，不让积分商城白屏。
      if (activityPageParams.pageNo > 1) activityPageParams.pageNo -= 1;
      loadStatus.value = activityCount.value > 0 ? 'more' : 'noMore';
    } finally {
      loading.value = false;
    }
  }

  // 加载更多
  function loadMore() {
    if (!loading.value && loadStatus.value !== 'noMore') {
      activityPageParams.pageNo += 1;
      getActivityList();
    }
  }

  // 上拉加载更多
  onReachBottom(() => {
    loadMore();
  });
  onMounted(() => {
    getActivityList();
  });
</script>
