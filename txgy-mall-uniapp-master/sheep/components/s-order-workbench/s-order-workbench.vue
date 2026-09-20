<template>
  <view class="workbench" aria-label="本人全部待办">
    <view class="heading"><text>本人全部待办</text><button class="small" @tap="workbench.refresh" :disabled="model.loading">刷新</button></view>
    <view class="totals" v-if="model.summary">
      <text>待处理事项 {{ model.summary.pendingCount }}</text><text>未读提醒 {{ model.summary.unreadCount }}</text>
    </view>
    <view v-else>待办统计{{ model.loading ? '加载中…' : '尚未更新' }}</view>
    <view v-if="model.error" class="error" role="alert">{{ model.error }}</view>
    <view v-if="model.lastSuccess" class="help">更新于 {{ new Date(model.lastSuccess).toLocaleTimeString() }} · 已读不代表已处理</view>
    <view v-if="model.summary?.newCount > 0" class="new-banner" role="status">新增 {{ model.summary.newCount }} 条待处理事项</view>
    <view class="categories" v-if="model.summary">
      <text v-for="(label, action) in buyerTodoNames" :key="action">{{ label }} {{ model.summary.todoCounts[action] ?? '—' }}</text>
    </view>
    <button class="tasks-button" @tap="workbench.showTasks">{{ model.expanded ? '收起待办' : '查看待办' }}</button>
    <view v-if="model.expanded" class="task-list">
      <view v-if="model.pageError" class="error" role="alert">{{ model.pageError }}<button class="small" @tap="workbench.loadPage(model.pageNo)">重试</button></view>
      <view v-if="model.pageLoading">正在更新待办…</view>
      <view v-else-if="!model.list.length && !model.pageError">暂无待处理事项</view>
      <view v-for="entry in model.list" :key="entry.eventId" class="task-entry">
        <view><text v-if="entry.unread" class="new-label">新</text>{{ buyerTodoNames[entry.action] }} · {{ entry.no }}</view>
        <view class="help">{{ entry.reason }}{{ entry.shopId ? ` · 店铺 ${entry.shopId}` : '' }}</view>
        <button class="small" @tap="workbench.open(entry)" :disabled="model.reading || model.pageLoading">查看详情</button>
      </view>
      <view class="paging">
        <button class="small" :disabled="model.pageNo <= 1 || model.pageLoading" @tap="workbench.loadPage(model.pageNo - 1)">上一页</button>
        <text>{{ model.pageNo }} / {{ Math.max(1, Math.ceil(model.total / 10)) }} 页 · {{ model.total }} 项</text>
        <button class="small" :disabled="model.pageNo * 10 >= model.total || model.pageLoading" @tap="workbench.loadPage(model.pageNo + 1)">下一页</button>
      </view>
      <button class="small" :disabled="model.reading || model.pageLoading || !model.list.some(e => e.unread)" @tap="workbench.markRead(model.list)">仅标记本页提醒已读</button>
    </view>
  </view>
</template>
<script setup>
import { computed } from 'vue';
import { buyerTodoNames } from '@/sheep/helper/order-workbench.mjs';
const props = defineProps({ workbench: { type: Object, required: true } });
const model = computed(() => props.workbench.state);
</script>
<style scoped lang="scss">
.workbench { margin: 20rpx; padding: 24rpx; border: 1rpx solid #bed7d2; border-radius: 16rpx; background: #fff; color: #243d45; font-size: 26rpx; }
.heading, .totals, .paging { display: flex; justify-content: space-between; align-items: center; gap: 16rpx; flex-wrap: wrap; }
.heading { font-weight: 700; margin-bottom: 12rpx; }
.totals { justify-content: flex-start; font-weight: 600; }
.categories { display: flex; flex-wrap: wrap; gap: 14rpx; margin: 16rpx 0; font-size: 24rpx; }
.help { color: #607c83; font-size: 23rpx; line-height: 1.6; margin: 8rpx 0; }
.error { color: #a92b23; font-size: 24rpx; }
.new-banner { background: #fff2df; border-left: 6rpx solid #a05e12; padding: 16rpx; margin: 12rpx 0; color: #794607; font-weight: 600; }
.new-label { display: inline-block; background: #fff0dc; color: #7b460a; border: 1rpx solid #d5a35e; padding: 1rpx 8rpx; margin-right: 8rpx; border-radius: 4rpx; }
.small { display: inline-flex; align-items: center; justify-content: center; margin: 4rpx 0; padding: 4rpx 14rpx; line-height: 1.8; font-size: 24rpx; background: #eff7f5; color: #176a5c; }
.tasks-button { font-size: 26rpx; color: #176a5c; background: #eff7f5; line-height: 2; margin-top: 12rpx; }
.task-entry { padding: 20rpx 0; border-bottom: 1rpx solid #e4eeee; }
.paging { margin-top: 16rpx; font-size: 23rpx; }
button[disabled] { opacity: .5; }
</style>
