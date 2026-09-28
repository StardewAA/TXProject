<template>
  <s-layout :title="target?.scope === 'SHOP' ? '店铺客服' : '平台客服'" navbar-background-color="#ffffff">
    <view class="service-page">
      <view v-if="expired" class="expired-card">
        <view class="heading">当前会话已失效</view>
        <view class="muted">账号或商城已变化，请返回首页后重新进入。</view>
        <button class="primary-button" size="mini" @tap="home">返回首页</button>
      </view>
      <template v-else>
        <view v-if="!state.thread" class="unavailable-card">
          <view class="service-avatar service-avatar--large">客</view>
          <view class="heading">{{ target?.scope === 'SHOP' ? '店铺客服' : '平台客服' }}</view>
          <view class="muted">欢迎咨询，我们会为您保留本次留言。</view>
          <view v-if="state.error || routeError" class="error" role="alert">{{ state.error || routeError }}</view>
          <view v-if="state.busy" class="muted">正在连接客服…</view>
          <view v-if="state.capability && !chatEnabled(state.capability)" class="state-note">
            {{ state.capability.message || '客服暂未开放，请稍后再试。' }}
          </view>
          <button v-if="!state.thread && !routeError" class="primary-button" size="mini" :disabled="state.busy" @tap="reload">重新连接</button>
        </view>

        <view v-if="state.thread" class="chat-shell">
          <view class="chat-header">
            <view class="service-avatar">客</view>
            <view class="chat-header__main">
              <view class="heading">{{ target?.scope === 'SHOP' ? '店铺客服' : '平台客服' }}</view>
              <view class="chat-status">
                <text class="status-dot" />{{ modeText }}
                <text v-if="state.thread.onlineAgents > 0"> · {{ state.thread.onlineAgents }} 位在线</text>
              </view>
            </view>
            <button class="text-button" size="mini" :disabled="state.busy" @tap="controller.refresh">刷新</button>
          </view>

          <view v-if="state.error || routeError" class="error error--bar" role="alert">{{ state.error || routeError }}</view>
          <view class="service-state">
            <view>{{ state.thread.availabilityMessage }}</view>
            <view v-if="state.thread.mode === 'WAITING_HUMAN'" class="muted">已为您通知客服，您可以继续留言。</view>
            <view class="service-state__actions">
              <button
                v-if="!['HUMAN', 'CLOSED'].includes(state.thread.mode)"
                class="secondary-button"
                size="mini"
                :disabled="state.busy || !!state.pending || !state.ready"
                @tap="requestHuman"
              >{{ state.thread.humanRequested ? '查看接待状态' : '转人工 / 留言' }}</button>
              <button v-if="state.thread.mode === 'CLOSED'" class="secondary-button" size="mini" :disabled="state.busy" @tap="reload">重新咨询</button>
              <button class="history-button" size="mini" @tap="controller.refresh">刷新消息</button>
            </view>
          </view>

          <view v-if="state.thread.mode === 'SELF_SERVICE' && state.thread.faq?.length" class="quick-questions">
            <view class="quick-questions__label">猜你想问</view>
            <scroll-view scroll-x class="quick-questions__scroll">
              <view class="quick-questions__row">
                <button
                  v-for="faq in state.thread.faq"
                  :key="faq.id"
                  size="mini"
                  :disabled="state.busy || !!state.pending"
                  @tap="state.draft = faq.question"
                >{{ faq.question }}</button>
              </view>
            </scroll-view>
          </view>

          <view v-if="target.resourceType && !state.sentResource" class="resource-share">
            <view class="resource-share__icon">{{ target.resourceType === 'PRODUCT' ? '品' : '单' }}</view>
            <view class="resource-share__info">
              <view class="heading">发送{{ target.resourceType === 'PRODUCT' ? '商品' : '订单' }}给客服</view>
              <view class="muted">客服可据此快速了解您咨询的内容</view>
            </view>
            <button
              class="send-card-button"
              size="mini"
              :disabled="state.busy || !!state.pending || !state.ready || state.thread.mode === 'CLOSED'"
              @tap="controller.send(true)"
            >发送这张咨询卡片</button>
          </view>

          <view class="messages">
            <view v-if="!state.messages.length" class="chat-welcome">
              <view class="service-avatar service-avatar--large">客</view>
              <view class="heading">您好，很高兴为您服务</view>
              <view class="muted">请告诉我们您想咨询的商品、订单或售后问题</view>
            </view>
            <view
              v-for="message in state.messages"
              :key="message.id"
              class="message"
              :class="{ mine: message.senderRole === 'MEMBER' }"
            >
              <view class="message-avatar">{{ message.senderRole === 'MEMBER' ? '我' : '客' }}</view>
              <view class="message-body">
                <view class="meta">{{ roles[message.senderRole] }} · {{ time(message.createdAt) }}</view>
                <view class="text">{{ message.text }}</view>
                <view v-if="message.card" class="resource-card">
                  <view class="card-type">{{ message.card.type === 'PRODUCT' ? '商品咨询' : '订单咨询' }}</view>
                  <view class="heading">{{ message.card.title }}</view>
                  <view class="card-description">{{ message.card.text }}</view>
                  <view v-if="Number.isSafeInteger(message.card.amount) && message.card.amount >= 0" class="card-price">￥{{ (message.card.amount / 100).toFixed(2) }}</view>
                  <button size="mini" @tap="openResource(message.card)">查看详情</button>
                </view>
              </view>
            </view>
            <view class="message-actions">
              <button v-if="state.hasMore" size="mini" :disabled="state.busy" @tap="controller.refresh">继续加载后续消息</button>
              <button v-if="state.cursor" size="mini" :disabled="state.busy" @tap="controller.read">将已显示消息标为已读</button>
            </view>
          </view>

          <view v-if="state.thread.mode !== 'CLOSED'" class="composer">
            <textarea
              v-model="state.draft"
              maxlength="2000"
              :disabled="state.busy || !!state.pending || !state.ready"
              placeholder="请输入您想咨询的内容…"
              auto-height
            />
            <view class="composer__footer">
              <view class="muted">请勿发送密码、验证码等敏感信息</view>
              <button v-if="state.pending" class="retry-button" :disabled="state.busy" @tap="controller.retry">重试未确认消息（不会重复发送）</button>
              <button v-else class="send-button" :disabled="state.busy || !state.ready || !state.draft.trim()" @tap="controller.send(false)">发送</button>
            </view>
          </view>
        </view>
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
  const roles = { MEMBER: '我', AGENT: '人工客服', FAQ: '客服助手', AI: 'AI 客服', SYSTEM: '服务通知' };
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
  async function reload() {
    if (!task.current() || !target.value || state.busy) return;
    await controller.open(target.value);
  }
  async function requestHuman() {
    if (!task.current() || state.busy || !state.thread) return;
    const alreadyRequested = state.thread.humanRequested === true;
    const succeeded = await controller.human();
    if (!task.current() || !succeeded || !state.thread) return;
    sheep.$helper.toast(
      alreadyRequested
        ? state.thread.availabilityMessage || '接待状态已刷新'
        : state.thread.onlineAgents > 0
          ? '已通知店铺客服，请等待接待'
          : '已转为人工留言，客服上线后即可查看并回复',
    );
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
.service-page{min-height:calc(100vh - 90rpx);padding:18rpx 22rpx 32rpx;background:linear-gradient(180deg,#f1f6f4 0%,#f6f7f8 42%,#f3f5f7 100%);box-sizing:border-box;color:#1f2f2b}
.chat-shell,.unavailable-card,.expired-card{overflow:hidden;border:1rpx solid rgba(25,105,88,.1);border-radius:24rpx;background:#fff;box-shadow:0 14rpx 38rpx rgba(28,64,56,.08)}
.unavailable-card,.expired-card{padding:56rpx 32rpx;text-align:center}.unavailable-card .muted,.expired-card .muted{margin:10rpx 0 24rpx}
.service-avatar,.message-avatar{display:flex;align-items:center;justify-content:center;width:64rpx;height:64rpx;flex:0 0 auto;border-radius:19rpx;background:linear-gradient(145deg,#15927b,#0e6f60);box-shadow:0 8rpx 18rpx rgba(20,125,107,.19);color:#fff;font-size:25rpx;font-weight:700}
.service-avatar--large{width:82rpx;height:82rpx;margin:0 auto 18rpx;border-radius:50%;font-size:30rpx}.heading{font-weight:700;line-height:1.45}
.chat-header{display:flex;align-items:center;gap:17rpx;padding:22rpx 24rpx;border-bottom:1rpx solid #e8efed;background:rgba(255,255,255,.96)}
.chat-header__main{min-width:0;flex:1}.chat-header .heading{font-size:29rpx}.chat-status{display:flex;align-items:center;margin-top:4rpx;color:#71827d;font-size:21rpx}
.status-dot{width:11rpx;height:11rpx;margin-right:8rpx;border-radius:50%;background:#25ae77;box-shadow:0 0 0 5rpx rgba(37,174,119,.11)}
.text-button,.history-button{height:auto!important;margin:0!important;padding:8rpx 10rpx!important;border:0!important;background:transparent!important;color:#66807a!important;font-size:22rpx!important;line-height:1.4!important}
.service-state{padding:15rpx 24rpx;border-bottom:1rpx solid #e8efed;background:#f1f8f5;color:#3e6359;font-size:22rpx;line-height:1.55}
.service-state>.muted{margin-top:4rpx}.service-state__actions{display:flex;align-items:center;justify-content:flex-end;margin-top:8rpx;white-space:nowrap}
.secondary-button,.primary-button,.send-card-button{height:56rpx!important;margin:8rpx!important;padding:0 20rpx!important;border-radius:28rpx!important;font-size:22rpx!important;line-height:56rpx!important}
.primary-button,.send-card-button{border:0!important;background:#147d6b!important;color:#fff!important}.secondary-button{border:1rpx solid #b9d3cc!important;background:#fff!important;color:#176b5b!important}
.quick-questions{display:flex;align-items:center;gap:12rpx;padding:16rpx 20rpx;border-bottom:1rpx solid #edf1f0;background:#fff}
.quick-questions__label{flex:0 0 auto;color:#7b8d88;font-size:21rpx}.quick-questions__scroll{min-width:0;flex:1;white-space:nowrap}.quick-questions__row{display:inline-flex;gap:12rpx}
.quick-questions button{height:52rpx;margin:0;padding:0 19rpx;border:1rpx solid #dce7e4;border-radius:26rpx;background:#f7faf9;color:#3d5b54;font-size:22rpx;line-height:52rpx}
.resource-share{display:flex;align-items:center;gap:15rpx;margin:18rpx 20rpx 0;padding:18rpx;border:1rpx dashed #bfd8d1;border-radius:17rpx;background:#f7fbf9}
.resource-share__icon{display:flex;align-items:center;justify-content:center;width:62rpx;height:62rpx;flex:0 0 auto;border-radius:16rpx;background:#e2f2ed;color:#147d6b;font-weight:700}.resource-share__info{min-width:0;flex:1}.resource-share__info .heading{font-size:24rpx}.resource-share__info .muted{font-size:19rpx}
.send-card-button{flex:0 0 auto;margin:0!important;padding:0 17rpx!important;font-size:20rpx!important}
.messages{min-height:570rpx;padding:28rpx 20rpx 20rpx;background:#f3f6f8}
.chat-welcome{padding:60rpx 20rpx;text-align:center}.chat-welcome .heading{margin-bottom:7rpx;font-size:27rpx}
.message{display:flex;align-items:flex-start;gap:12rpx;margin-bottom:24rpx}.message.mine{flex-direction:row-reverse}.message-avatar{width:54rpx;height:54rpx;border-radius:17rpx;font-size:21rpx}.mine .message-avatar{background:linear-gradient(145deg,#ff8649,#ec542d);box-shadow:0 7rpx 16rpx rgba(236,84,45,.18)}
.message-body{max-width:78%}.meta{margin:0 7rpx 7rpx;color:#8b9a96;font-size:19rpx}.mine .meta{text-align:right}
.text{padding:17rpx 20rpx;border:1rpx solid #e1e9e6;border-radius:6rpx 20rpx 20rpx 20rpx;background:#fff;box-shadow:0 5rpx 14rpx rgba(31,58,51,.05);font-size:24rpx;line-height:1.65;white-space:pre-wrap;word-break:break-word}.mine .text{border-color:#cfe8df;border-radius:20rpx 6rpx 20rpx 20rpx;background:#e7f5ef}
.resource-card{position:relative;min-width:420rpx;margin-top:9rpx;padding:19rpx;border:1rpx solid #dce5e3;border-radius:17rpx;background:#fff;box-shadow:0 6rpx 16rpx rgba(31,58,51,.05)}.resource-card .heading{padding-right:94rpx;font-size:24rpx}.card-type{margin-bottom:8rpx;color:#147d6b;font-size:19rpx}.card-description{margin:7rpx 0;color:#74827f;font-size:20rpx;line-height:1.5}.card-price{color:#e24d27;font-size:28rpx;font-weight:700}.resource-card button{position:absolute;right:16rpx;bottom:16rpx;height:48rpx;margin:0;padding:0 15rpx;border:0;border-radius:24rpx;background:#edf6f3;color:#147d6b;font-size:20rpx;line-height:48rpx}
.message-actions{text-align:center}.message-actions button{margin:8rpx;padding:7rpx 14rpx;border:0;background:transparent;color:#78908a;font-size:20rpx}
.composer{padding:18rpx 20rpx calc(18rpx + env(safe-area-inset-bottom));border-top:1rpx solid #e5ecea;background:#fff}.composer textarea{width:100%;min-height:100rpx;padding:16rpx 18rpx;border:1rpx solid #dce5e3;border-radius:17rpx;background:#f8faf9;box-sizing:border-box;font-size:24rpx;line-height:1.6}.composer__footer{display:flex;align-items:center;gap:12rpx;margin-top:12rpx}.composer__footer .muted{flex:1;font-size:18rpx}.send-button,.retry-button{height:58rpx;margin:0;padding:0 28rpx;border:0;border-radius:29rpx;background:linear-gradient(135deg,#ed633d,#df4524);box-shadow:0 8rpx 16rpx rgba(226,77,39,.18);color:#fff;font-size:23rpx;line-height:58rpx}.send-button[disabled]{background:#d6dfdc;box-shadow:none;color:#93a09c}.retry-button{padding:0 18rpx;font-size:19rpx}
.muted,.meta{color:#768782;line-height:1.65}.state-note{margin:18rpx 0;padding:18rpx;border-radius:14rpx;background:#f0f7f4;color:#48675f;font-size:22rpx;line-height:1.6}.error{margin:16rpx 0;color:#b73724;font-size:22rpx}.error--bar{margin:0;padding:14rpx 24rpx;background:#fff0ed}.expired-card .heading,.unavailable-card .heading{font-size:30rpx}
</style>
