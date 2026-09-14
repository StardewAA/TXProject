<script lang="ts" setup>
import type { CrmCustomerApi } from '#/api/crm/customer';

import { ref, watch } from 'vue';

import { useRouter } from 'vue-router';

import { Empty, Spin, Tag } from 'ant-design-vue';

import { getSimilarCustomerList } from '#/api/crm/customer';

defineOptions({ name: 'CrmSimilarCustomerCard' });

const props = defineProps<{
  /** 当前客户编号 */
  customerId?: number;
}>();

const router = useRouter();
const list = ref<CrmCustomerApi.Customer[]>([]);
const loading = ref(false);

watch(
  () => props.customerId,
  async (id) => {
    if (!id) {
      list.value = [];
      return;
    }
    loading.value = true;
    try {
      list.value = (await getSimilarCustomerList(id, 8)) ?? [];
    } catch {
      list.value = [];
    } finally {
      loading.value = false;
    }
  },
  { immediate: true },
);

/** 跳转到相似客户详情 */
function handleView(customer: CrmCustomerApi.Customer) {
  if (!customer.id) {
    return;
  }
  router.push({ name: 'CrmCustomerDetail', params: { id: customer.id } });
}
</script>

<template>
  <div>
    <div class="mb-2 flex items-center gap-2">
      <span class="text-sm font-medium">相似客户推荐</span>
      <span class="text-xs text-gray-400">
        （同客户类别 / 同行业，便于对比与业务拓展）
      </span>
    </div>
    <Spin :spinning="loading">
      <Empty
        v-if="!loading && list.length === 0"
        :image="Empty.PRESENTED_IMAGE_SIMPLE"
        description="暂无相似客户"
      />
      <div v-else class="grid grid-cols-2 gap-2 md:grid-cols-4">
        <div
          v-for="item in list"
          :key="item.id"
          class="cursor-pointer rounded border border-gray-200 px-3 py-2 transition hover:border-blue-400 hover:bg-blue-50/50"
          @click="handleView(item)"
        >
          <div class="mb-1 truncate text-xs font-medium text-gray-800">
            {{ item.name }}
          </div>
          <div class="flex flex-wrap items-center gap-1 text-xs text-gray-500">
            <Tag :color="item.customerType === 2 ? 'orange' : 'blue'">
              {{ item.customerType === 2 ? '上游供应商' : '下游客户' }}
            </Tag>
            <span v-if="item.levelName">{{ item.levelName }}</span>
            <span v-if="item.ownerUserName">{{ item.ownerUserName }}</span>
          </div>
        </div>
      </div>
    </Spin>
  </div>
</template>
