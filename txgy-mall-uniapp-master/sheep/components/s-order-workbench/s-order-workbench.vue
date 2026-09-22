<template>
  <view class="workbench" aria-label="订单待办">
    <view class="heading">
      <view><text class="eyebrow">ORDER ASSISTANT</text><text class="heading-title">订单待办</text></view>
      <button class="refresh-button" @tap="workbench.refresh" :disabled="model.loading">{{ model.loading ? '更新中' : '刷新' }}</button>
    </view>
    <view class="totals" v-if="model.summary">
      <view><text>{{ model.summary.pendingCount }}</text><small>待处理</small></view>
      <view><text>{{ model.summary.unreadCount }}</text><small>新提醒</small></view>
    </view>
    <view v-else class="empty-summary">{{ model.loading ? '正在同步订单待办…' : '下拉刷新后查看订单待办' }}</view>
    <view v-if="model.error" class="error" role="alert">{{ model.error }}</view>
    <view v-if="model.lastSuccess" class="help">最近更新 {{ new Date(model.lastSuccess).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}</view>
    <view v-if="model.summary?.newCount > 0" class="new-banner" role="status">新增 {{ model.summary.newCount }} 条待处理事项</view>
    <view class="categories" v-if="model.summary">
      <text v-for="(label, action) in buyerTodoNames" :key="action">{{ label }} {{ model.summary.todoCounts[action] ?? '—' }}</text>
    </view>
    <button class="tasks-button" @tap="workbench.showTasks">{{ model.expanded ? '收起事项' : '查看全部待办' }} <text>{{ model.expanded ? '⌃' : '›' }}</text></button>
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
.workbench { margin-top: 18rpx; padding: 26rpx; border: 1rpx solid rgba(15, 118, 110, .13); border-radius: 28rpx; background: linear-gradient(145deg, #fff 0%, #f8fbfa 100%); box-shadow: 0 12rpx 34rpx rgba(15,54,62,.06); color: #243d45; font-size: 25rpx; }
.heading, .totals, .paging { display: flex; justify-content: space-between; align-items: center; gap: 16rpx; flex-wrap: wrap; }
.heading { margin-bottom: 20rpx; }
.heading view { display: flex; flex-direction: column; }
.eyebrow { color: #178473; font-size: 17rpx; font-weight: 700; letter-spacing: 3rpx; }
.heading-title { margin-top: 4rpx; color: #173d37; font-size: 30rpx; font-weight: 800; }
.refresh-button { width: auto; min-width: 96rpx; margin: 0; padding: 0 18rpx; border: 1rpx solid #cce0db; border-radius: 999rpx; background: #fff; color: #176a5c; font-size: 21rpx; line-height: 2.2; }
.refresh-button::after { border: 0; }
.totals { flex-wrap: nowrap; }
.totals view { flex: 1; display: flex; align-items: baseline; gap: 12rpx; padding: 18rpx 22rpx; border-radius: 18rpx; background: #eef7f5; }
.totals view:last-child { background: #f1f5fa; }
.totals text { color: #0f766e; font-size: 34rpx; font-weight: 800; }
.totals small { color: #60747a; font-size: 21rpx; }
.empty-summary { padding: 20rpx; border-radius: 18rpx; background: #f3f7f6; color: #718187; text-align: center; font-size: 22rpx; }
.categories { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10rpx; margin: 16rpx 0; font-size: 22rpx; }
.categories text { padding: 12rpx 14rpx; border-radius: 12rpx; background: #f6f8f8; color: #52676b; }
.help { color: #728488; font-size: 20rpx; line-height: 1.6; margin: 10rpx 0; }
.error { color: #a92b23; font-size: 24rpx; }
.new-banner { background: #fff6e8; border-left: 6rpx solid #c27a1a; border-radius: 10rpx; padding: 14rpx 16rpx; margin: 12rpx 0; color: #794607; font-weight: 600; }
.new-label { display: inline-block; background: #fff0dc; color: #7b460a; border: 1rpx solid #d5a35e; padding: 1rpx 8rpx; margin-right: 8rpx; border-radius: 4rpx; }
.small { display: inline-flex; align-items: center; justify-content: center; margin: 4rpx 0; padding: 4rpx 14rpx; line-height: 1.8; font-size: 24rpx; background: #eff7f5; color: #176a5c; }
.tasks-button { display: flex; align-items: center; justify-content: space-between; margin-top: 12rpx; padding: 0 20rpx; border: 1rpx solid #d5e5e1; border-radius: 16rpx; color: #176a5c; background: #fff; font-size: 23rpx; line-height: 2.5; }
.tasks-button::after { border: 0; }
.tasks-button text { font-size: 32rpx; }
.task-entry { padding: 20rpx 0; border-bottom: 1rpx solid #e4eeee; }
.paging { margin-top: 16rpx; font-size: 23rpx; }
button[disabled] { opacity: .5; }
</style>
