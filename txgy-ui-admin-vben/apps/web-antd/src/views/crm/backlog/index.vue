<script lang="ts" setup>
import type { Ref } from 'vue';

import {
  computed,
  onActivated,
  onDeactivated,
  onMounted,
  onUnmounted,
  ref,
} from 'vue';

import { Page } from '@vben/common-ui';

import { Badge, Card, List } from 'ant-design-vue';

import { getFollowClueCount } from '#/api/crm/clue';
import {
  getAuditContractCount,
  getRemindContractCount,
} from '#/api/crm/contract';
import {
  getFollowCustomerCount,
  getPutPoolRemindCustomerCount,
  getTodayContactCustomerCount,
} from '#/api/crm/customer';
import { getAuditReceivableCount } from '#/api/crm/receivable';
import { getReceivablePlanRemindCount } from '#/api/crm/receivable/plan';
import { getAfterSaleVisitTaskRemindCount } from '#/api/crm/after-sale/task';
import { getAfterSaleTicketOverdueCount } from '#/api/crm/after-sale/ticket';
import { getAfterSaleWarrantyRemindCount } from '#/api/crm/after-sale/warranty';

import { BACKLOG_SOURCE, useLeftSides } from './data';
import ClueFollowList from './modules/clue-follow-list.vue';
import ContractAuditList from './modules/contract-audit-list.vue';
import ContractRemindList from './modules/contract-remind-list.vue';
import CustomerFollowList from './modules/customer-follow-list.vue';
import CustomerPutPoolRemindList from './modules/customer-put-pool-remind-list.vue';
import CustomerTodayContactList from './modules/customer-today-contact-list.vue';
import ReceivableAuditList from './modules/receivable-audit-list.vue';
import ReceivablePlanRemindList from './modules/receivable-plan-remind-list.vue';
import TicketOverdueList from './modules/ticket-overdue-list.vue';
import VisitTaskRemindList from './modules/visit-task-remind-list.vue';
import WarrantyExpiryList from './modules/warranty-expiry-list.vue';

const leftMenu = ref('customerTodayContact');

const clueFollowCount = ref(0);
const customerFollowCount = ref(0);
const customerPutPoolRemindCount = ref(0);
const customerTodayContactCount = ref(0);
const contractAuditCount = ref(0);
const contractRemindCount = ref(0);
const receivableAuditCount = ref(0);
const receivablePlanRemindCount = ref(0);
const warrantyExpiryCount = ref(0);
const visitTaskRemindCount = ref(0);
const ticketOverdueCount = ref(0);

const leftSides = useLeftSides(
  customerTodayContactCount,
  clueFollowCount,
  customerFollowCount,
  customerPutPoolRemindCount,
  contractAuditCount,
  contractRemindCount,
  receivableAuditCount,
  receivablePlanRemindCount,
  warrantyExpiryCount,
  visitTaskRemindCount,
  ticketOverdueCount,
);

const currentComponent = computed(() => {
  const components = {
    customerTodayContact: CustomerTodayContactList,
    clueFollow: ClueFollowList,
    contractAudit: ContractAuditList,
    receivableAudit: ReceivableAuditList,
    contractRemind: ContractRemindList,
    customerFollow: CustomerFollowList,
    customerPutPoolRemind: CustomerPutPoolRemindList,
    receivablePlanRemind: ReceivablePlanRemindList,
    [BACKLOG_SOURCE.WARRANTY]: WarrantyExpiryList,
    [BACKLOG_SOURCE.VISIT]: VisitTaskRemindList,
    [BACKLOG_SOURCE.WORK_ORDER]: TicketOverdueList,
  } as const;
  return components[leftMenu.value as keyof typeof components];
});

/** 侧边点击 */
function sideClick(item: { menu: string }) {
  leftMenu.value = item.menu;
}

/**
 * 单个待办项取数（失败隔离）
 *
 * 修复前的问题：getCount() 是「串行 await 且没有任何 try/catch」，
 * 只要其中任意一个接口报错，排在它后面的待办就全部不会执行，
 * 而界面不会给出任何提示，最终表现为「某个待办数量一直是 0」，且极难排查
 * （例如「质保即将到期」排在第 9 位，前面任何一个接口抖动都会把它连累）。
 *
 * 现在改成：每项独立请求、独立捕获 —— 互不影响；失败时保留上一次的值，
 * 只在控制台留下日志，便于定位到底是哪个接口出了问题。
 */
async function loadCount(
  target: Ref<number>,
  loader: () => Promise<number | undefined>,
  label: string,
) {
  try {
    target.value = (await loader()) ?? 0;
  } catch (error) {
    console.error(
      `[backlog] 待办「${label}」数量获取失败，保持上一次的值`,
      error,
    );
  }
}

/** 获取数量：各项并行、互不影响 */
async function getCount() {
  await Promise.all([
    loadCount(
      customerTodayContactCount,
      getTodayContactCustomerCount,
      '今日需联系客户',
    ),
    loadCount(
      customerPutPoolRemindCount,
      getPutPoolRemindCustomerCount,
      '待进入公海的客户',
    ),
    loadCount(customerFollowCount, getFollowCustomerCount, '分配给我的客户'),
    loadCount(clueFollowCount, getFollowClueCount, '分配给我的线索'),
    loadCount(contractAuditCount, getAuditContractCount, '待审核合同'),
    loadCount(contractRemindCount, getRemindContractCount, '即将到期的合同'),
    loadCount(receivableAuditCount, getAuditReceivableCount, '待审核回款'),
    loadCount(
      receivablePlanRemindCount,
      getReceivablePlanRemindCount,
      '待回款提醒',
    ),
    loadCount(
      warrantyExpiryCount,
      getAfterSaleWarrantyRemindCount,
      '质保即将到期',
    ),
    loadCount(
      visitTaskRemindCount,
      getAfterSaleVisitTaskRemindCount,
      '任务提醒',
    ),
    loadCount(
      ticketOverdueCount,
      getAfterSaleTicketOverdueCount,
      '工单超时预警',
    ),
  ]);
}

/**
 * 自动刷新间隔（毫秒）
 *
 * 各待办来源都是「实时查询」口径（不落库、不预生成），数量会随日期与状态流转而变化。
 * 修复前只在进入页面时取一次，用户停留期间一直不变 —— 比如质保档案当天刚变成
 * 「即将到期」，页面却仍显示 0，必须手动刷新才能看到。
 */
const AUTO_REFRESH_INTERVAL_MS = 60_000;

let autoRefreshTimer: ReturnType<typeof setInterval> | undefined;

/** 启动自动刷新 */
function startAutoRefresh() {
  stopAutoRefresh();
  autoRefreshTimer = setInterval(getCount, AUTO_REFRESH_INTERVAL_MS);
}

/** 停止自动刷新（离开页面 / 组件销毁时调用，避免后台空转） */
function stopAutoRefresh() {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer);
    autoRefreshTimer = undefined;
  }
}

/** 激活时 */
onActivated(() => {
  getCount();
});

/** 初始化 */
onMounted(() => {
  getCount();
  startAutoRefresh();
});

onUnmounted(() => {
  stopAutoRefresh();
});

/** keep-alive 缓存场景：离开页面时停止轮询 */
onDeactivated(() => {
  stopAutoRefresh();
});
</script>
<template>
  <Page auto-content-height>
    <div class="flex h-full w-full">
      <Card class="w-1/5">
        <List item-layout="horizontal" :data-source="leftSides">
          <template #renderItem="{ item }">
            <List.Item
              @click="sideClick(item)"
              class="cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-700"
            >
              <List.Item.Meta>
                <template #title>
                  {{ item.name }}
                </template>
              </List.Item.Meta>
              <template #extra>
                <Badge
                  v-if="item.count.value > 0"
                  :color="item.menu === leftMenu ? 'blue' : 'red'"
                  :count="item.count.value"
                />
              </template>
            </List.Item>
          </template>
        </List>
      </Card>
      <component class="ml-4 w-4/5" :is="currentComponent" />
    </div>
  </Page>
</template>
