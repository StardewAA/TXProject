<script lang="ts" setup>
import type { CrmGlobalSearchApi } from '#/api/crm/global-search';

import { ref } from 'vue';

import { useRouter } from 'vue-router';

import { Card, Empty, Input, Spin, Tag } from 'ant-design-vue';

import { getGlobalSearch } from '#/api/crm/global-search';

defineOptions({ name: 'CrmGlobalSearch' });

const router = useRouter();
const keyword = ref('');
const loading = ref(false);
const searched = ref(false);
const result = ref<CrmGlobalSearchApi.Result>();

/** 结果分组配置 */
const GROUPS = [
  { key: 'customers', title: '客户（含上游供应商）', route: 'CrmCustomerDetail', color: 'blue' },
  { key: 'clues', title: '线索', route: 'CrmClueDetail', color: 'green' },
  { key: 'contacts', title: '联系人', route: 'CrmContactDetail', color: 'purple' },
  { key: 'businesses', title: '商机', route: 'CrmBusinessDetail', color: 'orange' },
  { key: 'contracts', title: '合同', route: 'CrmContractDetail', color: 'cyan' },
] as const;

/** 每个模块的条数 */
function getGroupList(key: (typeof GROUPS)[number]['key']) {
  return (result.value?.[key] ?? []) as CrmGlobalSearchApi.Item[];
}

/** 执行检索 */
async function handleSearch(value?: string) {
  const kw = (value ?? keyword.value ?? '').trim();
  if (!kw) {
    searched.value = false;
    result.value = undefined;
    return;
  }
  loading.value = true;
  searched.value = true;
  try {
    result.value = await getGlobalSearch(kw, 5);
  } catch {
    result.value = undefined;
  } finally {
    loading.value = false;
  }
}

/** 跳转到对应业务详情页 */
function handleView(item: CrmGlobalSearchApi.Item, route: string) {
  if (!item.id) {
    return;
  }
  router.push({ name: route, params: { id: item.id } });
}
</script>

<template>
  <div class="p-4">
    <Card>
      <div class="mb-2 text-base font-medium">全局检索</div>
      <div class="mb-3 text-xs text-gray-500">
        输入公司名 / 手机号 / 电话 / 合同编号，一次性检索线索、客户、联系人、商机、合同
      </div>
      <Input.Search
        v-model:value="keyword"
        placeholder="请输入公司名 / 手机号 / 电话 / 编号，回车检索"
        size="large"
        allow-clear
        :loading="loading"
        enter-button="检索"
        @search="handleSearch"
      />
    </Card>

    <Spin :spinning="loading">
      <div v-if="searched && result" class="mt-4">
        <div class="mb-2 text-xs text-gray-500">
          共匹配到 {{ result.total }} 条相关数据
        </div>
        <template v-for="group in GROUPS" :key="group.key">
          <Card
            v-if="getGroupList(group.key).length > 0"
            class="mb-3"
            size="small"
          >
            <template #title>
              <span class="text-sm">
                {{ group.title }}
                <Tag class="ml-2" :color="group.color">
                  {{ getGroupList(group.key).length }}
                </Tag>
              </span>
            </template>
            <div class="grid grid-cols-1 gap-2 md:grid-cols-2 lg:grid-cols-3">
              <div
                v-for="item in getGroupList(group.key)"
                :key="`${group.key}-${item.id}`"
                class="cursor-pointer rounded border border-gray-200 px-3 py-2 transition hover:border-blue-400 hover:bg-blue-50/50"
                @click="handleView(item, group.route)"
              >
                <div class="mb-1 truncate text-sm font-medium text-gray-800">
                  {{ item.name }}
                </div>
                <div class="flex flex-wrap items-center gap-2 text-xs text-gray-500">
                  <span v-if="item.mobile">📱 {{ item.mobile }}</span>
                  <span v-else-if="item.telephone">☎️ {{ item.telephone }}</span>
                  <span v-if="item.extra">{{ item.extra }}</span>
                  <span v-if="item.ownerUserName">👤 {{ item.ownerUserName }}</span>
                </div>
              </div>
            </div>
          </Card>
        </template>
        <Card v-if="result.total === 0">
          <Empty
            :image="Empty.PRESENTED_IMAGE_SIMPLE"
            description="没有匹配的数据，换个关键字试试"
          />
        </Card>
      </div>
    </Spin>
  </div>
</template>
