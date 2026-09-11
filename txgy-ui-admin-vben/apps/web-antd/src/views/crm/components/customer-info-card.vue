<script lang="ts" setup>
import type { CrmContactApi } from '#/api/crm/contact';
import type { CrmCustomerApi } from '#/api/crm/customer';

import { computed, ref, watch } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { getDictLabel } from '@vben/hooks';

import { message, Tag } from 'ant-design-vue';

import { getContactListByCustomer } from '#/api/crm/contact';
import { getCustomer } from '#/api/crm/customer';

defineOptions({ name: 'CrmCustomerInfoCard' });

const props = defineProps<{
  /** 客户编号，为空时不展示 */
  customerId?: number;
}>();

const customer = ref<CrmCustomerApi.Customer>();
const contacts = ref<CrmContactApi.Contact[]>([]);
const loading = ref(false);

/** 客户类别文案：1=下游客户，2=上游供应商 */
const categoryLabel = computed(() =>
  props.customerId && customer.value?.customerType
    ? getDictLabel(DICT_TYPE.CRM_CUSTOMER_CATEGORY, customer.value.customerType)
    : '',
);

/** 主联系人：优先 master=true，其次第一个 */
const mainContact = computed(
  () =>
    contacts.value.find((item) => item.master) ?? contacts.value[0] ?? undefined,
);

watch(
  () => props.customerId,
  async (id) => {
    if (!id) {
      customer.value = undefined;
      contacts.value = [];
      return;
    }
    loading.value = true;
    try {
      const [detail, contactList] = await Promise.all([
        getCustomer(id),
        getContactListByCustomer(id).catch(() => []),
      ]);
      customer.value = detail;
      contacts.value = Array.isArray(contactList) ? contactList : [];
    } catch {
      customer.value = undefined;
      contacts.value = [];
    } finally {
      loading.value = false;
    }
  },
  { immediate: true },
);

/** 复制文本 */
async function handleCopy(text?: string, label?: string) {
  if (!text) {
    return;
  }
  try {
    await navigator.clipboard.writeText(text);
    message.success(`${label ?? '信息'}已复制`);
  } catch {
    message.warning('复制失败，请手动选择复制');
  }
}
</script>

<template>
  <div
    v-if="customer"
    class="mx-4 mb-3 rounded border border-blue-200/70 bg-blue-50/60 px-3 py-2"
  >
    <div class="mb-1 flex flex-wrap items-center gap-2">
      <span class="text-xs font-medium text-gray-700">客户信息速览</span>
      <Tag
        v-if="categoryLabel"
        :color="customer.customerType === 2 ? 'orange' : 'blue'"
      >
        {{ categoryLabel }}
      </Tag>
      <span class="text-xs text-gray-500">{{ customer.name }}</span>
    </div>
    <div class="grid grid-cols-2 gap-x-6 gap-y-1 text-xs text-gray-600">
      <div>
        手机：
        <span class="text-gray-800">{{ customer.mobile || '-' }}</span>
        <a
          v-if="customer.mobile"
          class="ml-1 text-blue-500"
          @click="handleCopy(customer.mobile, '手机')"
        >
          复制
        </a>
      </div>
      <div>
        电话：
        <span class="text-gray-800">{{ customer.telephone || '-' }}</span>
        <a
          v-if="customer.telephone"
          class="ml-1 text-blue-500"
          @click="handleCopy(customer.telephone, '电话')"
        >
          复制
        </a>
      </div>
      <div class="col-span-2">
        地址：
        <span class="text-gray-800">{{ customer.detailAddress || '-' }}</span>
        <a
          v-if="customer.detailAddress"
          class="ml-1 text-blue-500"
          @click="handleCopy(customer.detailAddress, '地址')"
        >
          复制
        </a>
      </div>
      <div class="col-span-2">
        主要联系人：
        <template v-if="mainContact">
          <span class="text-gray-800">
            {{ mainContact.name }}
            <span v-if="mainContact.post">（{{ mainContact.post }}）</span>
            <span v-if="mainContact.mobile"> / {{ mainContact.mobile }}</span>
          </span>
          <a
            v-if="mainContact.mobile"
            class="ml-1 text-blue-500"
            @click="handleCopy(mainContact.mobile, '联系人手机')"
          >
            复制
          </a>
        </template>
        <span v-else class="text-gray-400">暂无联系人</span>
      </div>
    </div>
  </div>
</template>
