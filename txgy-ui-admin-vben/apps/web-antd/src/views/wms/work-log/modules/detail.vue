<script lang="ts" setup>
import type { InfraWorkLogApi } from '#/api/infra/work-log';

import { computed, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import { Alert, Card, Collapse, CollapsePanel, Descriptions, DescriptionsItem, Empty, Spin, Tag } from 'ant-design-vue';

import { getWorkLog } from '#/api/infra/work-log';

import {
  ACTION_LABELS,
  CHANGE_OPERATION_LABELS,
  CHANGE_OUTCOME_LABELS,
} from '../data';
import {
  buildReviewEntries,
  buildReadableConditions,
  getWarehouseArea,
  humanizeBusinessKey,
  humanizeWorkSummary,
  parseWorkLogChanges,
} from '../helpers';

const detail = ref<InfraWorkLogApi.WorkLog>();
const loadError = ref('');
const loading = ref(false);

const changes = computed(() => parseWorkLogChanges(detail.value?.changes));
const reviewEntries = computed(() => buildReviewEntries(changes.value.entries));
const conditions = computed(() => buildReadableConditions(detail.value?.requestParams));

function getOperationColor(operation: string) {
  return {
    DELETE: 'red',
    INSERT: 'green',
    UPDATE: 'blue',
  }[operation];
}

function getOutcomeColor(outcome: string) {
  return {
    COMMITTED: 'success',
    EXECUTED: 'blue',
    PENDING: 'warning',
    ROLLED_BACK: 'error',
  }[outcome];
}

const [Drawer, drawerApi] = useVbenDrawer({
  showConfirmButton: false,
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      detail.value = undefined;
      loadError.value = '';
      return;
    }
    const data = drawerApi.getData<{
      id: number;
      scope: InfraWorkLogApi.Scope;
    }>();
    if (!data?.id) {
      return;
    }
    loading.value = true;
    loadError.value = '';
    try {
      detail.value = await getWorkLog(data.id, data.scope);
    } catch {
      loadError.value = '留痕详情加载失败，请稍后重试。';
    } finally {
      loading.value = false;
    }
  },
});
</script>

<template>
  <Drawer title="仓储操作核查" class="w-[860px]">
    <Spin :spinning="loading">
      <Alert v-if="loadError" :message="loadError" type="error" show-icon />

      <template v-else-if="detail">
        <div class="mb-4 rounded-lg border border-border bg-card px-4 py-3">
          <div class="mb-1 flex flex-wrap items-center gap-2">
            <Tag color="blue">
              {{ getWarehouseArea(detail.requestUrl) }}
            </Tag>
            <Tag>{{ ACTION_LABELS[detail.action] || detail.action || '其他操作' }}</Tag>
            <Tag :color="detail.resultCode === 0 ? 'success' : 'error'">
              {{ detail.resultCode === 0 ? '操作成功' : '操作失败' }}
            </Tag>
          </div>
          <div class="text-lg font-medium leading-7">
            {{ detail.userName || `成员 ${detail.userId}` }}于 {{ formatDateTime(detail.beginTime) }}{{ humanizeWorkSummary(detail.action, detail.summary, detail.requestUrl) }}
          </div>
          <div v-if="humanizeBusinessKey(detail.businessKey)" class="mt-1 text-xs text-muted-foreground">
            相关资料：{{ humanizeBusinessKey(detail.businessKey) }}
          </div>
        </div>

        <Descriptions bordered size="small" :column="2">
          <DescriptionsItem label="操作人">
            {{ detail.userName || `用户 ${detail.userId}` }}
          </DescriptionsItem>
          <DescriptionsItem label="操作时间">
            {{ formatDateTime(detail.beginTime) }}
          </DescriptionsItem>
          <DescriptionsItem label="执行结果" :span="2">
            <span v-if="detail.resultCode === 0">成功</span>
            <span v-else class="text-red-500">
              {{ detail.resultMsg || `失败（结果码 ${detail.resultCode}）` }}
            </span>
          </DescriptionsItem>
        </Descriptions>

        <Card size="small" title="核查内容" class="mt-4">
          <Alert
            v-if="changes.malformed"
            message="变更详情暂时无法显示"
            description="本次操作记录已保留，但变更详情的格式无法识别。"
            type="warning"
            show-icon
          />
          <Alert
            v-if="changes.warnings.length"
            class="mb-3"
            message="部分业务内容未被采集，请结合原单据核对"
            type="warning"
            show-icon
          />

          <div v-if="reviewEntries.length" class="flex flex-col gap-3">
            <div
              v-for="(entry, index) in reviewEntries"
              :key="`${entry.table}-${entry.operation}-${index}`"
              class="rounded-md border border-border p-3"
            >
              <div class="mb-3 flex flex-wrap items-center gap-2">
                <Tag :color="getOperationColor(entry.operation)">
                  {{ CHANGE_OPERATION_LABELS[entry.operation] || entry.operation }}
                </Tag>
                <Tag v-if="entry.outcome === 'ROLLED_BACK'" :color="getOutcomeColor(entry.outcome)">
                  {{ CHANGE_OUTCOME_LABELS[entry.outcome] || entry.outcome }}
                </Tag>
                <span class="font-medium">{{ entry.label }}</span>
              </div>
              <div
                v-for="(record, recordIndex) in entry.records"
                :key="record.key"
                class="mb-3 overflow-hidden rounded-md border border-border last:mb-0"
              >
                <div v-if="entry.records.length > 1" class="bg-muted px-3 py-2 text-sm font-medium">
                  第 {{ recordIndex + 1 }} 条{{ entry.label }}
                </div>
                <div v-if="entry.operation === 'UPDATE'" class="grid grid-cols-[minmax(120px,0.8fr)_minmax(180px,1fr)_minmax(180px,1fr)] bg-muted/60 px-3 py-2 text-xs text-muted-foreground">
                  <span>核查项目</span><span>修改前</span><span>修改后</span>
                </div>
                <div v-else class="grid grid-cols-[minmax(120px,0.8fr)_minmax(180px,1fr)] bg-muted/60 px-3 py-2 text-xs text-muted-foreground">
                  <span>核查项目</span><span>{{ entry.operation === 'DELETE' ? '删除前内容' : '新增内容' }}</span>
                </div>
                <div
                  v-for="field in record.fields"
                  :key="field.field"
                  class="grid border-t border-border px-3 py-2 text-sm"
                  :class="[entry.operation === 'UPDATE' ? 'grid-cols-[minmax(120px,0.8fr)_minmax(180px,1fr)_minmax(180px,1fr)]' : 'grid-cols-[minmax(120px,0.8fr)_minmax(180px,1fr)]', entry.operation === 'UPDATE' && field.changed ? 'bg-blue-50 dark:bg-blue-950/30' : '']"
                >
                  <span class="font-medium">
                    {{ field.field }}
                    <Tag v-if="entry.operation === 'UPDATE' && field.changed" class="ml-1" color="blue">已修改</Tag>
                  </span>
                  <span v-if="entry.operation === 'UPDATE'" class="break-all text-muted-foreground">{{ field.before }}</span>
                  <span class="break-all" :class="entry.operation === 'UPDATE' && field.changed ? 'font-medium text-blue-700 dark:text-blue-300' : ''">
                    {{ entry.operation === 'DELETE' ? field.before : field.after }}
                  </span>
                </div>
              </div>
            </div>
          </div>
          <Empty
            v-else-if="!changes.malformed && !changes.warnings.length"
            :description="detail.action === 'QUERY' ? '这次查询没有修改业务资料' : '这次操作没有可展示的业务资料，请到对应单据核查'"
          />
        </Card>

        <Card v-if="detail.action === 'QUERY' && conditions.length" size="small" title="查询条件" class="mt-4">
          <Descriptions v-if="conditions.length" bordered size="small" :column="1">
            <DescriptionsItem v-for="condition in conditions" :key="condition.label" :label="condition.label">
              {{ condition.value }}
            </DescriptionsItem>
          </Descriptions>
        </Card>

        <Collapse class="mt-4">
          <CollapsePanel key="technical" header="技术记录（仅供系统排查）">
            <Descriptions bordered size="small" :column="1">
              <DescriptionsItem label="请求地址">
                <span class="break-all">{{ detail.requestMethod || '-' }} {{ detail.requestUrl || '-' }}</span>
              </DescriptionsItem>
              <DescriptionsItem label="链路编号">{{ detail.traceId || '-' }}</DescriptionsItem>
              <DescriptionsItem label="访问地址">{{ detail.userIp || '-' }}</DescriptionsItem>
              <DescriptionsItem label="执行耗时">{{ detail.duration == null ? '-' : `${detail.duration} ms` }}</DescriptionsItem>
              <DescriptionsItem label="采集提示">{{ changes.warnings.join('；') || '-' }}</DescriptionsItem>
              <DescriptionsItem label="客户端信息">
                <span class="break-all">{{ detail.userAgent || '-' }}</span>
              </DescriptionsItem>
            </Descriptions>
          </CollapsePanel>
        </Collapse>
      </template>
    </Spin>
  </Drawer>
</template>
