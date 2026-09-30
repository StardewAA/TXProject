<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import type { Dayjs } from 'dayjs';
import { useUserStore } from '@vben/stores';
import {
  Alert, Button, Card, DatePicker, Empty, Form, FormItem, Input, Modal, Rate, Space, TabPane, Tabs, Tag, message,
} from 'ant-design-vue';
import type { CmsExpert, ConsultOrder } from '#/api/cms/expert-consult';
import {
  actConsultOrder, createConsultOrder, evaluateConsultOrder, getConsultationSession, getMyExpert, listAvailableExperts, listExpertConsultRequests, listMyConsultOrders,
} from '#/api/cms/expert-consult';

defineOptions({ name: 'PortalExpertConsult' });
const userStore = useUserStore();
const router = useRouter();
const experts = ref<CmsExpert[]>([]);
const orders = ref<ConsultOrder[]>([]);
const requests = ref<ConsultOrder[]>([]);
const myExpert = ref<CmsExpert | null>(null);
const busy = ref(false);
const orderOpen = ref(false);
const selectedExpert = ref<CmsExpert>();
const orderForm = reactive<{ appointmentTime?: Dayjs; question: string; subject: string }>({
  appointmentTime: undefined,
  subject: '',
  question: '',
});
const evaluateOpen = ref(false);
const evaluatingOrder = ref<ConsultOrder>();
const evaluation = reactive({ rating: 5, content: '' });
const statusNames = ['待支付', '待接单', '已接单', '咨询中', '待确认完成', '已完成', '已取消', '已过期', '已拒单', '退款中', '已退款', '纠纷中'];
const currentUserId = computed(() => Number((userStore.userInfo as any)?.id || 0));
const canReceiveConsultation = computed(
  () =>
    myExpert.value?.auditStatus === 1 &&
    myExpert.value.enabled,
);

async function load() {
  busy.value = true;
  try {
    [experts.value, orders.value, myExpert.value] = await Promise.all([
      listAvailableExperts(),
      listMyConsultOrders(),
      getMyExpert(),
    ]);
    requests.value = canReceiveConsultation.value
      ? await listExpertConsultRequests()
      : [];
  }
  catch { message.error('专家咨询服务读取失败，请检查 CMS 服务和数据库迁移。'); }
  finally { busy.value = false; }
}
function openOrder(expert: CmsExpert) { selectedExpert.value = expert; orderForm.subject = ''; orderForm.question = ''; orderForm.appointmentTime = undefined; orderOpen.value = true; }
async function submitOrder() {
  if (!selectedExpert.value || !orderForm.subject.trim() || !orderForm.question.trim() || !orderForm.appointmentTime) { message.warning('请填写咨询主题、问题描述和预约时间'); return; }
  if (!orderForm.appointmentTime.isAfter(new Date())) { message.warning('预约时间必须晚于当前时间'); return; }
  const order = await createConsultOrder({ appointmentTime: orderForm.appointmentTime.format('YYYY-MM-DDTHH:mm:ss'), expertId: selectedExpert.value.id, subject: orderForm.subject.trim(), question: orderForm.question.trim() });
  orderOpen.value = false;
  if (order.payOrderId) {
    message.success('咨询订单已创建，正在进入收银台。');
    await openCashier(order);
    return;
  }
  message.warning('咨询订单已创建，但支付单尚未生成，请稍后在订单中重试。');
  await load();
}
async function openCashier(order: ConsultOrder) {
  if (!order.payOrderId) { message.warning('支付单尚未生成'); return; }
  await router.push({
    path: '/pay/cashier',
    query: {
      id: String(order.payOrderId),
      returnUrl: encodeURIComponent('/portal/expert?tab=orders'),
    },
  });
}
async function action(order: ConsultOrder, value: 'ACCEPT'|'CANCEL'|'COMPLETE'|'REJECT'|'REQUEST_COMPLETE'|'START') {
  await actConsultOrder(order.id, value);
  message.success('订单状态已更新');
  await load();
}
async function contactRequester(order: ConsultOrder) {
  const contact = await getConsultationSession(order.id);
  if (!contact.imReady) {
    message.warning('即时通讯会话创建失败，请稍后重试。');
    return;
  }
  await router.push({ path: '/im/home/conversation', query: { groupId: String(contact.imGroupId) } });
}
function openEvaluation(order: ConsultOrder) { evaluatingOrder.value = order; evaluation.rating = 5; evaluation.content = ''; evaluateOpen.value = true; }
async function submitEvaluation() {
  if (!evaluatingOrder.value) return;
  await evaluateConsultOrder(evaluatingOrder.value.id, evaluation.rating, evaluation.content.trim());
  evaluateOpen.value = false; message.success('评价已提交'); await load();
}
onMounted(load);
</script>

<template>
  <div class="consult-page">
    <section class="consult-hero"><div><p>政策专家服务</p><h1>一对一文字咨询</h1><span>预约下单、在线支付、专家接单后进入 IM 即时通讯，全程留痕。</span></div><Button :loading="busy" @click="load">刷新</Button></section>
    <Alert message="平台只提供政策信息咨询，不承诺贷款审批、补贴申报成功或投资收益。" show-icon type="warning" />
    <Tabs default-active-key="experts">
      <TabPane key="experts" tab="选择专家">
        <div v-if="experts.length" class="expert-grid"><Card v-for="expert in experts" :key="expert.id" class="expert-card"><div class="expert-title"><div><h3>{{expert.expertName}}</h3><p>{{expert.professionalTitle||'政策咨询专家'}} · {{expert.company||'独立专家'}}</p></div><Tag color="gold">{{Number(expert.rating||0).toFixed(1)}} 分</Tag></div><p class="fields">{{expert.serviceFields}}</p><p class="intro">{{expert.introduction||'暂无个人简介'}}</p><div class="expert-footer"><strong>¥ {{(expert.fixedPrice/100).toFixed(2)}} / 次</strong><Button type="primary" @click="openOrder(expert)">发起咨询</Button></div></Card></div><Empty v-else description="暂无已审核并启用的专家" />
      </TabPane>
      <TabPane key="orders" tab="我的咨询订单">
        <div v-if="orders.length" class="order-list"><Card v-for="order in orders" :key="order.id"><div class="order-title"><div><strong>{{order.serviceSubject}}</strong><span>{{order.orderNo}} · {{order.expertName}} · 预约 {{order.appointmentTime}}</span></div><Tag>{{statusNames[order.status]}}</Tag></div><p>{{order.question}}</p><div class="order-footer"><span>¥ {{(order.price/100).toFixed(2)}} <template v-if="order.imGroupId">· IM 群 {{order.imGroupId}}</template></span><Space>
          <Button v-if="order.status===0 && order.userId===currentUserId" @click="action(order,'CANCEL')">取消订单</Button>
          <Button v-if="order.status===0 && order.userId===currentUserId && order.payOrderId" type="primary" @click="openCashier(order)">立即支付</Button>
          <template v-if="order.expertUserId===currentUserId"><Button v-if="order.status===1" danger @click="action(order,'REJECT')">拒单</Button><Button v-if="order.status===1" type="primary" @click="action(order,'ACCEPT')">接单</Button><Button v-if="order.status===2" type="primary" @click="action(order,'START')">开始咨询</Button><Button v-if="order.status===3" type="primary" @click="action(order,'REQUEST_COMPLETE')">申请完成</Button></template>
          <Button v-if="order.status===4 && order.userId===currentUserId" type="primary" @click="action(order,'COMPLETE')">确认完成</Button>
          <Button v-if="order.status>=2 && order.status<=5" type="primary" @click="contactRequester(order)">进入咨询</Button>
          <Button v-if="order.status===5 && order.userId===currentUserId" @click="openEvaluation(order)">评价专家</Button>
        </Space></div></Card></div><Empty v-else description="暂无咨询订单" />
      </TabPane>
      <TabPane v-if="canReceiveConsultation" key="requests" tab="咨询请求">
        <div v-if="requests.length" class="order-list">
          <Card v-for="request in requests" :key="request.id">
            <div class="order-title">
              <div>
                <strong>{{ request.serviceSubject }}</strong>
                <span>{{ request.orderNo }} · 咨询人：{{ request.requesterName || `用户 ${request.userId}` }} · 预约 {{ request.appointmentTime }}</span>
              </div>
              <Tag>{{ statusNames[request.status] }}</Tag>
            </div>
            <p>{{ request.question }}</p>
            <div class="order-footer">
              <span>咨询价格 ¥ {{ (request.price / 100).toFixed(2) }}</span>
              <Space>
                <Button v-if="request.status === 1" danger @click="action(request, 'REJECT')">拒单</Button>
                <Button v-if="request.status === 1" type="primary" @click="action(request, 'ACCEPT')">接单</Button>
                <Button v-if="request.status === 2" type="primary" @click="action(request, 'START')">开始咨询</Button>
                <Button v-if="request.status === 3" type="primary" @click="action(request, 'REQUEST_COMPLETE')">申请完成</Button>
                <Button v-if="request.status >= 2 && request.status <= 5" @click="contactRequester(request)">进入咨询</Button>
              </Space>
            </div>
          </Card>
        </div>
        <Empty v-else description="当前账号暂无专家咨询请求" />
      </TabPane>
    </Tabs>
    <Modal v-model:open="orderOpen" :title="`咨询 ${selectedExpert?.expertName||''}`" @ok="submitOrder"><Form layout="vertical"><FormItem label="预约时间" required><DatePicker v-model:value="orderForm.appointmentTime" show-time format="YYYY-MM-DD HH:mm" style="width:100%" /></FormItem><FormItem label="咨询主题" required><Input v-model:value="orderForm.subject" :maxlength="128" /></FormItem><FormItem label="问题描述" required><Input.TextArea v-model:value="orderForm.question" :rows="6" :maxlength="4000" show-count /></FormItem></Form></Modal>
    <Modal v-model:open="evaluateOpen" title="评价咨询服务" @ok="submitEvaluation"><Form layout="vertical"><FormItem label="评分"><Rate v-model:value="evaluation.rating" /></FormItem><FormItem label="评价内容"><Input.TextArea v-model:value="evaluation.content" :rows="4" :maxlength="1000" /></FormItem></Form></Modal>
  </div>
</template>

<style scoped>
.consult-page{display:grid;gap:18px}.consult-hero{display:flex;align-items:center;justify-content:space-between;padding:34px 38px;color:#fff;background:radial-gradient(circle at 85% 0,rgb(201 169 97 / 22%),transparent 35%),linear-gradient(125deg,#14243f,#2c4470);border-radius:25px}.consult-hero p{margin:0 0 7px;color:#dfc48a}.consult-hero h1{margin:0 0 9px;font-size:34px}.consult-hero span{color:rgb(229 239 252 / 75%)}.expert-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:15px}.expert-card{border-radius:18px}.expert-title,.expert-footer,.order-title,.order-footer{display:flex;align-items:center;justify-content:space-between;gap:14px}.expert-title h3{margin:0;color:#1a3153;font-size:19px}.expert-title p,.order-title span{display:block;margin:4px 0 0;color:#8793a5}.fields{color:#a88840;font-weight:600}.intro{min-height:48px;color:#66758a;line-height:1.7}.expert-footer strong{color:#1a3153;font-size:18px}.order-list{display:grid;gap:12px}.order-title strong{display:block;color:#1a3153;font-size:16px}.order-footer{margin-top:14px}@media(max-width:1000px){.expert-grid{grid-template-columns:1fr 1fr}}@media(max-width:700px){.expert-grid{grid-template-columns:1fr}.consult-hero,.order-footer{align-items:stretch;flex-direction:column}}
</style>
