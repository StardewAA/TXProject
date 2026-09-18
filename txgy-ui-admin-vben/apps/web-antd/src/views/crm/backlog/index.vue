<script lang="ts" setup>
import { computed, onActivated, onMounted, ref } from 'vue';

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

/** 获取数量 */
async function getCount() {
  customerTodayContactCount.value = await getTodayContactCustomerCount();
  customerPutPoolRemindCount.value = await getPutPoolRemindCustomerCount();
  customerFollowCount.value = await getFollowCustomerCount();
  clueFollowCount.value = await getFollowClueCount();
  contractAuditCount.value = await getAuditContractCount();
  contractRemindCount.value = await getRemindContractCount();
  receivableAuditCount.value = await getAuditReceivableCount();
  receivablePlanRemindCount.value = await getReceivablePlanRemindCount();
  warrantyExpiryCount.value = await getAfterSaleWarrantyRemindCount();
  visitTaskRemindCount.value = await getAfterSaleVisitTaskRemindCount();
  ticketOverdueCount.value = await getAfterSaleTicketOverdueCount();
}

/** 激活时 */
onActivated(() => {
  getCount();
});

/** 初始化 */
onMounted(() => {
  getCount();
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
