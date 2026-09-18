<template>
  <s-layout :title="target?.scope === 'SHOP' ? '店铺客服' : '平台客服'">
    <view class="service-page">
      <view v-if="expired" class="box notice"
        >账号或商城已变化，请返回原页面重新进入。<button size="mini" @tap="home"
          >返回首页</button
        ></view
      >
      <template v-else>
        <view class="box">
          <view class="heading">{{
            target?.scope === 'SHOP' ? `店铺 #${target.shopId} 专属会话` : '平台服务会话'
          }}</view>
          <view class="muted">自助客服使用已发布的固定问答，不是真实 AI。不同店铺会话独立。</view>
          <view v-if="state.error || routeError" class="error" role="alert">{{
            state.error || routeError
          }}</view>
          <view v-if="state.busy" class="muted">正在处理…</view>
          <view v-if="state.capability && !chatEnabled(state.capability)" class="notice">{{
            state.capability.message || '新客服尚未开放，请等待店铺坐席配置完成。'
          }}</view>
          <button
            v-if="!state.thread && !routeError"
            size="mini"
            :disabled="state.busy"
            @tap="reload"
            >重新检查</button
          >
          <button size="mini" @tap="history">打开旧平台客服 / 历史记录</button>
          <view class="muted">旧平台客服不是该店铺坐席；打开旧入口不会自动发送商品或订单。</view>
        </view>
        <template v-if="state.thread">
          <view class="box">
            <view class="heading">{{ modeText }}</view>
            <view class="notice">{{ state.thread.availabilityMessage }}</view>
            <view v-if="state.thread.onlineAgents > 0"
              >在线坐席：{{ state.thread.onlineAgents }}</view
            >
            <view v-if="state.thread.mode === 'WAITING_HUMAN'" class="muted"
              >已申请人工。可以继续留言，未接入前不会显示“人工已接待”。</view
            >
            <button
              v-if="!['HUMAN', 'CLOSED'].includes(state.thread.mode)"
              size="mini"
              :disabled="state.busy || !!state.pending || !state.ready"
              @tap="controller.human"
              >{{ state.thread.humanRequested ? '刷新人工接待状态' : '转人工 / 留言' }}</button
            >
            <button
              v-if="state.thread.mode === 'CLOSED'"
              size="mini"
              :disabled="state.busy"
              @tap="reload"
              >重新咨询</button
            >
            <button size="mini" :disabled="state.busy" @tap="controller.refresh">刷新会话</button>
          </view>
          <view v-if="state.thread.mode === 'SELF_SERVICE' && state.thread.faq?.length" class="box">
            <view class="heading">常见问题（点击填入，确认后发送）</view>
            <button
              v-for="faq in state.thread.faq"
              :key="faq.id"
              size="mini"
              :disabled="state.busy || !!state.pending"
              @tap="state.draft = faq.question"
              >{{ faq.question }}</button
            >
          </view>
          <view v-if="target.resourceType" class="box">
            <view
              >{{ target.resourceType === 'PRODUCT' ? '商品' : '本人订单' }} #{{
                target.resourceId
              }}</view
            >
            <view class="muted"
              >只有点击发送才会分享，内容由服务器校验归属并生成；不分享地址、电话或支付凭据。</view
            >
            <button
              size="mini"
              :disabled="
                state.busy ||
                !!state.pending ||
                !state.ready ||
                state.sentResource ||
                state.thread.mode === 'CLOSED'
              "
              @tap="controller.send(true)"
              >{{ state.sentResource ? '本次已发送咨询卡片' : '发送这张咨询卡片' }}</button
            >
          </view>
          <view class="box messages">
            <view class="heading">会话记录 · 未读 {{ state.thread.unreadCount || 0 }}</view>
            <view v-if="!state.messages.length" class="muted">暂无消息，请说明您需要的帮助。</view>
            <view
              v-for="message in state.messages"
              :key="message.id"
              class="message"
              :class="{ mine: message.senderRole === 'MEMBER' }"
            >
              <view class="meta"
                >{{ roles[message.senderRole] }} · {{ time(message.createdAt) }}</view
              >
              <view class="text">{{ message.text }}</view>
              <view v-if="message.card" class="resource-card">
                <view
                  >{{ message.card.type === 'PRODUCT' ? '商品咨询' : '订单咨询' }} #{{
                    message.card.resourceId
                  }}</view
                >
                <view class="heading">{{ message.card.title }}</view
                ><view>{{ message.card.text }}</view>
                <view v-if="Number.isSafeInteger(message.card.amount) && message.card.amount >= 0"
                  >快照金额：￥{{ (message.card.amount / 100).toFixed(2) }}</view
                >
                <button size="mini" @tap="openResource(message.card)">查看实时详情</button>
              </view>
            </view>
            <button
              v-if="state.hasMore"
              size="mini"
              :disabled="state.busy"
              @tap="controller.refresh"
              >继续加载后续消息</button
            >
            <button v-if="state.cursor" size="mini" :disabled="state.busy" @tap="controller.read"
              >将已显示消息标为已读</button
            >
          </view>
          <view v-if="state.thread.mode !== 'CLOSED'" class="box">
            <textarea
              v-model="state.draft"
              maxlength="2000"
              :disabled="state.busy || !!state.pending || !state.ready"
              placeholder="请输入问题（不超过2000字）"
              auto-height
            />
            <view class="muted"
              >{{ state.draft.length }}/2000 ·
              暂不开放附件，请勿发送密码、支付验证码等敏感信息。</view
            >
            <button v-if="state.pending" :disabled="state.busy" @tap="controller.retry"
              >重试未确认消息（不会重复发送）</button
            >
            <button
              v-else
              :disabled="state.busy || !state.ready || !state.draft.trim()"
              @tap="controller.send(false)"
              >发送</button
            >
          </view>
        </template>
      </template>
    </view>
  </s-layout>
</template>

<script setup>
  import { computed, reactive, ref, onScopeDispose } from 'vue';
  import { onLoad, onShow, onHide } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import ChatApi from '@/sheep/api/promotion/kefu-chat';
import { chatTarget, chatEnabled, createChatController } from '@/sheep/helper/kefu-chat.mjs';
  import { useTenantTask } from '@/sheep/hooks/useTenantTask';
  import { tenantContext } from '@/sheep/helper/tenant-runtime';
  import { fulfillmentTime as time } from '@/sheep/helper/fulfillment-view.mjs';
  const target = ref(null),
    routeError = ref(''),
    expired = ref(false),
    state = reactive({});
  let timer = null,
    visible = false;
  const task = useTenantTask(() => {
    expired.value = true;
    stop();
    controller.expire();
  });
  const controller = createChatController(ChatApi, task.current, state, undefined, () => ({
    tenantId: tenantContext.state().accountTenantId,
    memberId: tenantContext.state().userId,
  }));
  const roles = { MEMBER: '我', AGENT: '人工客服', FAQ: '自助客服 · 固定问答', SYSTEM: '系统提示' };
  const modeText = computed(
    () =>
      ({
        SELF_SERVICE: '自助客服',
        WAITING_HUMAN: '等待人工 / 留言',
        HUMAN: '人工接待中',
        CLOSED: '本次接待已结束',
      }[state.thread?.mode]),
  );
  const home = () => sheep.$router.go('/pages/index/index');
  const history = () => {
    if (task.current()) sheep.$router.go('/pages/chat/index');
  };
  async function reload() {
    if (!task.current() || !target.value || state.busy) return;
    await controller.open(target.value);
  }
  function stop() {
    clearInterval(timer);
    timer = null;
    controller.suspend();
  }
  function openResource(card) {
    if (!task.current() || !Number.isSafeInteger(card.resourceId) || card.resourceId <= 0) return;
    if (card.type === 'PRODUCT') sheep.$router.go('/pages/goods/index', { id: card.resourceId });
    if (card.type === 'ORDER') sheep.$router.go('/pages/order/detail', { id: card.resourceId });
  }
  onLoad((options) => {
    try {
      target.value = chatTarget(options);
    } catch (error) {
      routeError.value = error.message;
    }
  });
  onShow(() => {
    visible = true;
    if (!task.current() || !target.value) return;
    controller.resume();
    if (!state.capability) reload();
    else if (state.thread) controller.refresh();
    clearInterval(timer);
    timer = setInterval(() => {
      if (visible && controller.canPoll()) controller.refresh();
    }, 5000);
  });
  onHide(() => {
    visible = false;
    stop();
  });
  onScopeDispose(stop);
</script>

<style scoped>
  .service-page {
    padding: 24rpx;
    background: #f4f6f8;
    min-height: 80vh;
  }
  .box {
    background: #fff;
    border-radius: 16rpx;
    padding: 24rpx;
    margin-bottom: 20rpx;
    word-break: break-word;
  }
  .heading {
    font-weight: 600;
    margin-bottom: 14rpx;
  }
  .muted,
  .meta {
    color: #64748b;
    font-size: 24rpx;
    line-height: 1.7;
  }
  .notice {
    background: #edf6f5;
    padding: 16rpx;
    margin: 12rpx 0;
  }
  .error {
    color: #a32626;
    padding: 14rpx 0;
  }
  .box button {
    margin: 14rpx 12rpx 0 0;
  }
  .message {
    margin-top: 22rpx;
    border-top: 1px solid #e7edf1;
    padding: 18rpx 0;
  }
  .mine {
    background: #f1f8f5;
    padding: 18rpx;
  }
  .text {
    white-space: pre-wrap;
    line-height: 1.6;
    margin-top: 8rpx;
  }
  .resource-card {
    border: 1px solid #cbd5e1;
    border-radius: 12rpx;
    padding: 20rpx;
    margin-top: 14rpx;
  }
  textarea {
    min-height: 160rpx;
    width: 100%;
    box-sizing: border-box;
    background: #f8fafc;
    padding: 16rpx;
    line-height: 1.7;
  }
</style>
