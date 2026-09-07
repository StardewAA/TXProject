<script lang="ts" setup>
import type { QualityReport } from '#/api/wms/md/item/quality-report';

import { computed, ref, watch } from 'vue';

import { Button, Empty, Form, FormItem, Image, Input, message, Modal, Radio, RadioGroup, Spin, Tag } from 'ant-design-vue';

import { ImageUpload } from '#/components/upload';
import {
  createQualityReport,
  getQualityReportList,
  QualityStatus,
} from '#/api/wms/md/item/quality-report';

const props = defineProps<{
  current?: QualityReport;
  initial?: QualityReport;
  itemId?: number;
}>();
const emit = defineEmits<{
  change: [value: QualityReport | undefined];
}>();

const editVisible = ref(false);
const historyVisible = ref(false);
const submitting = ref(false);
const historyLoading = ref(false);
const history = ref<QualityReport[]>([]);
const localCurrent = ref<QualityReport>();
const draft = ref<QualityReport>(newDraft());

function newDraft(): QualityReport {
  return { status: QualityStatus.PENDING, imageUrls: [], remark: '' };
}

function statusMeta(status?: number) {
  if (status === QualityStatus.QUALIFIED) return { color: 'success', label: '合格' };
  if (status === QualityStatus.ABNORMAL) return { color: 'error', label: '异常' };
  return { color: 'default', label: '待质检' };
}

const currentStatus = computed(() => statusMeta(localCurrent.value?.status));

async function loadHistory() {
  if (!props.itemId) return;
  historyLoading.value = true;
  try {
    history.value = await getQualityReportList(props.itemId);
    localCurrent.value = history.value.find((report) => report.current) || history.value[0];
  } finally {
    historyLoading.value = false;
  }
}

watch(
  () => [props.current, props.initial, props.itemId] as const,
  async () => {
    localCurrent.value = props.itemId ? props.current : props.initial;
    if (props.itemId) await loadHistory();
  },
  { deep: true, immediate: true },
);

function openCreate() {
  draft.value = newDraft();
  editVisible.value = true;
}

function validateDraft() {
  const images = draft.value.imageUrls || [];
  if (draft.value.status !== QualityStatus.PENDING && images.length === 0) {
    message.warning('合格或异常报告至少上传 1 张图片');
    return false;
  }
  if (images.length > 9) {
    message.warning('质检图片最多上传 9 张');
    return false;
  }
  if (draft.value.status === QualityStatus.ABNORMAL && !draft.value.remark?.trim()) {
    message.warning('异常质检报告必须填写异常说明');
    return false;
  }
  return true;
}

async function submitReport() {
  if (!validateDraft()) return;
  if (!props.itemId) {
    localCurrent.value = { ...draft.value, imageUrls: [...draft.value.imageUrls] };
    emit('change', localCurrent.value);
    editVisible.value = false;
    return;
  }
  submitting.value = true;
  try {
    await createQualityReport({ ...draft.value, itemId: props.itemId });
    await loadHistory();
    emit('change', localCurrent.value);
    editVisible.value = false;
    message.success('质检报告已提交并生效');
  } finally {
    submitting.value = false;
  }
}

async function openHistory() {
  historyVisible.value = true;
  if (!props.itemId) {
    history.value = localCurrent.value ? [{ ...localCurrent.value, current: true }] : [];
    return;
  }
  await loadHistory();
}
</script>

<template>
  <section class="mx-4 mt-4 rounded-md border border-gray-200 p-4">
    <div class="mb-3 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <span class="text-sm font-semibold">质检报告</span>
        <Tag :color="currentStatus.color">{{ currentStatus.label }}</Tag>
        <span v-if="itemId" class="text-xs text-gray-400">共 {{ history.length }} 份报告</span>
      </div>
      <div class="flex gap-2">
        <Button v-if="localCurrent" @click="openHistory">查看全部报告</Button>
        <Button type="primary" @click="openCreate">新增质检报告</Button>
      </div>
    </div>
    <div v-if="localCurrent" class="grid grid-cols-[1fr_2fr] gap-4 text-sm">
      <div>
        <div class="text-gray-500">报告图片</div>
        <div class="mt-2 flex flex-wrap gap-2">
          <Image v-for="url in localCurrent.imageUrls" :key="url" :src="url" :width="64" :height="64" />
          <span v-if="!localCurrent.imageUrls?.length" class="text-gray-400">暂无图片</span>
        </div>
      </div>
      <div>
        <div class="text-gray-500">异常说明/备注</div>
        <div class="mt-2 min-h-16 rounded bg-gray-50 p-3">{{ localCurrent.remark || '无' }}</div>
        <div v-if="localCurrent.createTime" class="mt-2 text-xs text-gray-400">
          {{ localCurrent.creatorName || localCurrent.creator || '-' }} · {{ localCurrent.createTime }}
        </div>
      </div>
    </div>
    <Empty v-else description="暂无质检报告，当前按待质检处理" :image-style="{ height: '54px' }" />
  </section>

  <Modal v-model:open="editVisible" title="新增质检报告" width="680px" :confirm-loading="submitting" @ok="submitReport">
    <Form :label-col="{ style: { width: '110px' } }">
      <FormItem label="质检结果" required>
        <RadioGroup v-model:value="draft.status">
          <Radio :value="QualityStatus.PENDING">待质检</Radio>
          <Radio :value="QualityStatus.QUALIFIED">合格</Radio>
          <Radio :value="QualityStatus.ABNORMAL">异常</Radio>
        </RadioGroup>
      </FormItem>
      <FormItem label="报告图片" :required="draft.status !== QualityStatus.PENDING">
        <ImageUpload v-model:value="draft.imageUrls" :accept="['jpg', 'jpeg', 'png']" :max-number="9" :max-size="10" multiple />
      </FormItem>
      <FormItem label="异常说明/备注" :required="draft.status === QualityStatus.ABNORMAL">
        <Input.TextArea v-model:value="draft.remark" :rows="4" :maxlength="500" show-count />
      </FormItem>
    </Form>
  </Modal>

  <Modal v-model:open="historyVisible" title="质检报告历史" width="760px" :footer="null">
    <Spin :spinning="historyLoading">
      <div v-for="report in history" :key="report.id || report.createTime" class="mb-3 rounded border p-3">
        <div class="mb-2 flex items-center gap-2">
          <Tag :color="statusMeta(report.status).color">{{ statusMeta(report.status).label }}</Tag>
          <Tag v-if="report.current" color="blue">当前有效</Tag>
          <span class="text-xs text-gray-400">{{ report.creatorName || report.creator || '-' }} · {{ report.createTime || '-' }}</span>
        </div>
        <div class="flex flex-wrap gap-2">
          <Image v-for="url in report.imageUrls" :key="url" :src="url" :width="56" :height="56" />
        </div>
        <div class="mt-2 text-sm">{{ report.remark || '无备注' }}</div>
      </div>
      <Empty v-if="!historyLoading && history.length === 0" description="暂无质检报告" />
    </Spin>
  </Modal>
</template>
