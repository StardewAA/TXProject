<script lang="ts" setup>
import type { CrmCustomerApi } from '#/api/crm/customer';

import type { QuickActionDef, TodoItemDef } from './data';

import { computed, nextTick, onActivated, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page, useVbenModal } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { Button, Card, Empty, Table, Tag, Tooltip } from 'ant-design-vue';

import { getAfterSaleTicketOverdueCount } from '#/api/crm/after-sale/ticket';
import { getBusinessPage } from '#/api/crm/business';
import { getFollowClueCount } from '#/api/crm/clue';
import {
  getAuditContractCount,
  getRemindContractCount,
} from '#/api/crm/contract';
import {
  getCustomerPage,
  getPutPoolRemindCustomerCount,
  getTodayContactCustomerCount,
} from '#/api/crm/customer';
import { BizTypeEnum } from '#/api/crm/permission';
import { getAuditReceivableCount } from '#/api/crm/receivable';
import { getReceivablePlanRemindCount } from '#/api/crm/receivable/plan';
import AiAssistantIcon from '#/views/crm/ai-chat/components/ai-assistant-icon.vue';
import CrmAiChat from '#/views/crm/ai-chat/index.vue';
import FollowUpRecordForm from '#/views/crm/followup/modules/form.vue';

import AiAssistantModal from './modules/ai-assistant-modal.vue';
import {
  AI_EXAMPLES,
  QUICK_ACTIONS,
  TODAY_CONTACT_PAGE_SIZE,
  TODO_GROUPS,
  TODO_LEVEL_META,
} from './data';

defineOptions({ name: 'CrmHome' });

const { push } = useRouter();

// ========== 一、全局待办汇总 ==========

/**
 * 各待办数量的取数器
 *
 * 口径与「待办事项」页面完全相同（同一批接口）；
 * 商机没有独立的待办接口，取其「我负责的商机总数」作为关注项。
 */
const COUNT_LOADERS: Record<string, () => Promise<number | undefined>> = {
  clueFollow: () => getFollowClueCount(),
  customerPutPoolRemind: () => getPutPoolRemindCustomerCount(),
  businessFollow: async () =>
    (await getBusinessPage({ pageNo: 1, pageSize: 1, sceneType: 1 })).total,
  contractAudit: () => getAuditContractCount(),
  contractRemind: () => getRemindContractCount(),
  ticketOverdue: () => getAfterSaleTicketOverdueCount(),
  receivableAudit: () => getAuditReceivableCount(),
  receivablePlanRemind: () => getReceivablePlanRemindCount(),
};

const counts = ref<Record<string, number>>(
  Object.fromEntries(Object.keys(COUNT_LOADERS).map((key) => [key, 0])),
);

/** 待办总量（标题上的红点提示用） */
const totalTodoCount = computed(() =>
  Object.values(counts.value).reduce((sum, n) => sum + (n || 0), 0),
);

/**
 * 取数（逐项并行、失败隔离）
 *
 * 沿用「待办事项」页的经验：某个接口抖动不能连累其它待办，
 * 失败时保留上一次的值并打印日志，便于定位是哪个接口的问题。
 */
async function getCount() {
  await Promise.all(
    Object.entries(COUNT_LOADERS).map(async ([key, loader]) => {
      try {
        counts.value[key] = (await loader()) ?? 0;
      } catch (error) {
        console.error(
          `[crm-home] 待办「${key}」数量获取失败，保留上一次的值`,
          error,
        );
      }
    }),
  );
}

/** 点击待办项 → 跳转对应业务页面处理 */
function goTodo(item: TodoItemDef) {
  push(item.path);
}

/** 读取某项待办数量（缺省 0，避免模板里出现 undefined） */
function countOf(key: string): number {
  return counts.value[key] ?? 0;
}

// ========== 二、今日需联系客户 ==========

const customers = ref<CrmCustomerApi.Customer[]>([]);
const todayLoading = ref(false);

/**
 * 加载今日需联系客户
 *
 * 归属口径固定为「我负责的」(sceneType=1)：
 * 待办事项页用的是「管理员看下属负责的(3)、普通成员看我负责的(1)」，
 * 若首页照搬，管理员会把自己的今日跟进漏掉（下属范围不含本人），
 * 出现「AI 能查到、首页却空白」的不一致。首页作为个人工作台，统一取本人负责的，
 * 团队视角的汇总去「待办事项」页查看。
 */
async function loadTodayContact() {
  todayLoading.value = true;
  try {
    const params: Record<string, any> = {
      pageNo: 1,
      pageSize: TODAY_CONTACT_PAGE_SIZE,
      contactStatus: 1, // 今日需联系
      sceneType: 1, // 我负责的
      pool: null, // 排除公海客户
    };
    const data = await getCustomerPage(params);
    customers.value = data.list ?? [];
  } catch (error) {
    console.error('[crm-home] 今日需联系客户加载失败', error);
    customers.value = [];
  } finally {
    todayLoading.value = false;
  }
}

/** 时间格式化（yyyy-MM-dd HH:mm，空值显示 -） */
function formatDateTime(value?: Date | number | string) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '-';
  }
  const pad = (n: number) => `${n}`.padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(
    date.getDate(),
  )} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

const todayColumns = [
  { title: '客户名称', dataIndex: 'name', key: 'name', width: 220 },
  { title: '联系人', dataIndex: 'contactName', key: 'contactName', width: 120 },
  {
    title: '上次跟进时间',
    dataIndex: 'contactLastTime',
    key: 'contactLastTime',
    width: 180,
  },
  {
    title: '跟进备注',
    dataIndex: 'contactLastContent',
    key: 'contactLastContent',
    ellipsis: true,
  },
  { title: '操作', key: 'action', width: 180 },
];

/** 打开客户详情 */
function handleCustomerDetail(row: CrmCustomerApi.Customer) {
  push({ name: 'CrmCustomerDetail', params: { id: row.id } });
}

/** 去「待办事项」页处理（查看全部今日需联系客户 / 逐个处理各类待办） */
function goBacklog() {
  push('/crm/backlog');
}

// ========== 三、快捷新建（跳转到对应业务模块）==========

/**
 * 点击快捷「新建」→ 跳到对应模块并在该页打开新建表单
 *
 * 之前是在首页直接弹出各模块的表单弹窗，四个入口点开观感雷同（表单顶部都带「一键识别」面板），
 * 现在改为进入各自模块：用户能看到该模块的列表、筛选与完整功能，上下文更清晰。
 * 目标页通过 ?action=create 自动打开新建弹窗。
 */
function handleQuick(action: QuickActionDef) {
  push({ path: action.path, query: { action: 'create' } });
}

// ========== 四、AI 智能助手（内嵌对话 + 弹窗放大）==========

/** 对话是否收起（收起后仅保留标题栏与提示，减少对其它板块的挤压） */
const aiCollapsed = ref(false);

/** 内嵌助手的自动提问内容 */
const autoQuestion = ref('');

/**
 * 点击示例问题 → 内嵌助手直接提问
 *
 * 先复位再赋值，保证连续点击同一个示例也能再次触发（子组件是按值变化监听的）。
 */
async function askAi(question: string) {
  autoQuestion.value = '';
  await nextTick();
  autoQuestion.value = question;
}

const aiModalRef = ref<InstanceType<typeof AiAssistantModal> | null>(null);

/** 弹窗放大（需要更大空间时使用，能力与内嵌完全一致） */
function openAiAssistant(question?: string) {
  aiModalRef.value?.open(question);
}

// ========== 五、新增跟进记录（复用客户跟进表单）==========

const [FollowUpFormModal, followUpFormApi] = useVbenModal({
  connectedComponent: FollowUpRecordForm,
  destroyOnClose: true,
});

/** 新增跟进记录 */
function handleAddFollowUp(row: CrmCustomerApi.Customer) {
  followUpFormApi
    .setData({ bizId: row.id, bizType: BizTypeEnum.CRM_CUSTOMER })
    .open();
}

// ========== 六、刷新 ==========

/** 统一刷新（表单提交成功后调用） */
function refreshAll() {
  getCount();
  loadTodayContact();
}

/** 自动刷新间隔：与待办事项页一致，保证「今天刚到期」的数据能自动出现 */
const AUTO_REFRESH_INTERVAL_MS = 60_000;
let autoRefreshTimer: ReturnType<typeof setInterval> | undefined;

function startAutoRefresh() {
  stopAutoRefresh();
  autoRefreshTimer = setInterval(getCount, AUTO_REFRESH_INTERVAL_MS);
}

function stopAutoRefresh() {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer);
    autoRefreshTimer = undefined;
  }
}

onMounted(() => {
  getCount();
  loadTodayContact();
  startAutoRefresh();
});

onActivated(() => {
  getCount();
  loadTodayContact();
});

onUnmounted(stopAutoRefresh);
</script>

<template>
  <Page auto-content-height>
    <div class="flex h-full flex-col gap-4 overflow-y-auto pb-2 pr-1">
      <!-- 1. 顶部快捷区（跳转到对应业务模块新建） -->
      <Card size="small" class="shrink-0">
        <div class="flex flex-wrap items-center gap-2">
          <span
            class="mr-1 flex items-center gap-1.5 text-sm font-semibold text-foreground"
          >
            <IconifyIcon icon="lucide:zap" :width="16" />
            快捷新建
          </span>
          <button
            v-for="action in QUICK_ACTIONS"
            :key="action.key"
            class="flex items-center gap-1.5 rounded-lg px-3.5 py-2 text-sm font-medium transition"
            :class="action.theme"
            @click="handleQuick(action)"
          >
            <IconifyIcon :icon="action.icon" :width="16" />
            {{ action.label }}
          </button>
          <span class="ml-auto hidden text-xs text-muted-foreground lg:block">
            点击后进入对应业务模块，并自动打开该模块的新建表单
          </span>
        </div>
      </Card>

      <!-- 2. 主体：左侧（AI 对话 + 今日需联系客户）/ 右侧（全局待办） -->
      <div class="grid min-h-0 grow grid-cols-1 gap-4 xl:grid-cols-3">
        <div class="flex min-w-0 flex-col gap-4 xl:col-span-2">
          <!-- 2.1 AI 智能助手（内嵌对话，可直接提问） -->
          <Card size="small" class="flex flex-col">
            <template #title>
              <div class="flex items-center gap-2">
                <AiAssistantIcon :size="26" />
                <span>AI 智能助手</span>
                <Tag color="processing" class="m-0">可直接提问</Tag>
              </div>
            </template>
            <template #extra>
              <div class="flex items-center">
                <Button
                  type="link"
                  size="small"
                  @click="aiCollapsed = !aiCollapsed"
                >
                  <IconifyIcon
                    :icon="
                      aiCollapsed ? 'lucide:chevron-down' : 'lucide:chevron-up'
                    "
                    :width="14"
                    class="mr-1"
                  />
                  {{ aiCollapsed ? '展开对话' : '收起对话' }}
                </Button>
                <Button type="link" size="small" @click="openAiAssistant()">
                  <IconifyIcon
                    icon="lucide:maximize-2"
                    :width="14"
                    class="mr-1"
                  />
                  弹窗放大
                </Button>
              </div>
            </template>

            <!-- 收起态：仅保留一行说明，把空间让给下方板块 -->
            <div
              v-if="aiCollapsed"
              class="flex items-center gap-2 text-xs text-muted-foreground"
            >
              <IconifyIcon icon="lucide:info" :width="14" />
              对话已收起。点「展开对话」继续，或点下方示例问题直接提问。
              <button
                v-for="example in AI_EXAMPLES.slice(0, 2)"
                :key="example"
                class="rounded-full border border-border bg-background px-3 py-0.5 text-xs text-muted-foreground transition hover:border-primary hover:text-primary"
                @click="askAi(example)"
              >
                {{ example }}
              </button>
            </div>

            <!-- 展开态：内嵌完整对话（多会话 / 上下文 / 数据查询 / 建单确认 / OCR 均保留） -->
            <div v-else class="flex min-h-0 flex-col gap-3">
              <div class="flex flex-wrap gap-2">
                <button
                  v-for="example in AI_EXAMPLES"
                  :key="example"
                  class="rounded-full border border-border bg-background px-3 py-1 text-xs text-muted-foreground transition hover:border-primary hover:text-primary"
                  @click="askAi(example)"
                >
                  {{ example }}
                </button>
              </div>
              <div class="h-[460px] min-h-[360px]">
                <CrmAiChat embedded :auto-question="autoQuestion" />
              </div>
            </div>
          </Card>

          <!-- 2.2 今日需联系客户 -->
          <Card size="small">
            <template #title>
              <div class="flex items-center gap-2">
                <IconifyIcon icon="lucide:phone-call" :width="16" />
                <span>今日需联系客户</span>
                <Tag v-if="customers.length > 0" color="orange" class="m-0">
                  {{ customers.length }} 位
                </Tag>
              </div>
            </template>
            <template #extra>
              <Button type="link" size="small" @click="goBacklog">
                查看全部
              </Button>
            </template>
            <Table
              :columns="todayColumns"
              :data-source="customers"
              :loading="todayLoading"
              :pagination="false"
              row-key="id"
              size="small"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'name'">
                  <Button
                    type="link"
                    class="!p-0"
                    @click="handleCustomerDetail(record)"
                  >
                    {{ record.name }}
                  </Button>
                </template>
                <template v-else-if="column.key === 'contactName'">
                  {{ record.contactName || '-' }}
                </template>
                <template v-else-if="column.key === 'contactLastTime'">
                  {{ formatDateTime(record.contactLastTime) }}
                </template>
                <template v-else-if="column.key === 'contactLastContent'">
                  <Tooltip :title="record.contactLastContent">
                    <span>{{ record.contactLastContent || '-' }}</span>
                  </Tooltip>
                </template>
                <template v-else-if="column.key === 'action'">
                  <Button
                    type="link"
                    class="!p-0 mr-3"
                    @click="handleCustomerDetail(record)"
                  >
                    查看详情
                  </Button>
                  <Button
                    type="link"
                    class="!p-0"
                    @click="handleAddFollowUp(record)"
                  >
                    新增跟进
                  </Button>
                </template>
              </template>
              <template #emptyText>
                <Empty
                  :image="Empty.PRESENTED_IMAGE_SIMPLE"
                  description="今天没有需要联系的客户"
                />
              </template>
            </Table>
          </Card>
        </div>

        <!-- 2.3 全局待办汇总（右侧竖排，避免与左侧板块相互挤压） -->
        <Card size="small" class="min-w-0">
          <template #title>
            <div class="flex items-center gap-2">
              <IconifyIcon icon="lucide:list-checks" :width="16" />
              <span>全局待办</span>
              <Tag v-if="totalTodoCount > 0" color="red" class="m-0">
                {{ totalTodoCount }} 项
              </Tag>
              <Tag v-else color="green" class="m-0">暂无待办</Tag>
            </div>
          </template>
          <template #extra>
            <Button type="link" size="small" @click="goBacklog">
              待办事项
            </Button>
          </template>
          <div class="flex flex-col gap-3">
            <div v-for="group in TODO_GROUPS" :key="group.key">
              <div
                class="mb-1.5 flex items-center gap-1.5 text-xs font-semibold text-muted-foreground"
              >
                <IconifyIcon :icon="group.icon" :width="14" />
                {{ group.title }}
              </div>
              <Tooltip
                v-for="item in group.items"
                :key="item.key"
                :title="item.desc"
              >
                <div
                  class="mb-1 flex cursor-pointer items-center justify-between rounded-md border border-transparent px-2.5 py-2 transition last:mb-0 hover:border-border hover:bg-accent"
                  @click="goTodo(item)"
                >
                  <span class="truncate text-xs text-foreground">
                    {{ item.name }}
                  </span>
                  <span class="ml-2 flex shrink-0 items-center gap-1.5">
                    <span
                      v-if="countOf(item.key) > 0"
                      class="size-1.5 rounded-full"
                      :class="TODO_LEVEL_META[item.level].dot"
                    ></span>
                    <span
                      class="text-sm font-semibold"
                      :class="
                        countOf(item.key) > 0 && item.level === 'urgent'
                          ? 'text-red-600'
                          : 'text-foreground'
                      "
                    >
                      {{ countOf(item.key) }}
                    </span>
                  </span>
                </div>
              </Tooltip>
            </div>
          </div>
        </Card>
      </div>
    </div>

    <!-- 弹窗区 -->
    <FollowUpFormModal @success="refreshAll" />
    <AiAssistantModal ref="aiModalRef" />
  </Page>
</template>
