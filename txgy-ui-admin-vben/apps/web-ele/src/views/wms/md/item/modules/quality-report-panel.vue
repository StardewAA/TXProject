<script lang="ts" setup>
import type { QualityReport } from '#/api/wms/md/item/quality-report';

import { computed, ref, watch } from 'vue';
import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElImage,
  ElInput,
  ElMessage,
  ElRadioButton,
  ElRadioGroup,
  ElTag,
  ElTimeline,
  ElTimelineItem,
} from 'element-plus';

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

watch(
  () => [props.current, props.initial] as const,
  () => {
    localCurrent.value = props.itemId ? props.current : props.initial;
  },
  { immediate: true, deep: true },
);

watch(
  () => props.itemId,
  async (itemId) => {
    if (!itemId) return;
    history.value = await getQualityReportList(itemId);
    localCurrent.value = history.value.find((item) => item.current) || history.value[0];
  },
  { immediate: true },
);

function newDraft(): QualityReport {
  return { status: QualityStatus.PENDING, imageUrls: [], remark: '' };
}

const statusMeta = computed(() => getStatusMeta(localCurrent.value?.status));

function getStatusMeta(status?: number) {
  if (status === QualityStatus.QUALIFIED) return { label: '合格', type: 'success' as const };
  if (status === QualityStatus.ABNORMAL) return { label: '异常', type: 'danger' as const };
  return { label: '待质检', type: 'info' as const };
}

function openCreate() {
  draft.value = newDraft();
  editVisible.value = true;
}

function validateDraft() {
  const images = draft.value.imageUrls || [];
  if (draft.value.status !== QualityStatus.PENDING && images.length === 0) {
    ElMessage.warning('合格或异常报告至少上传 1 张图片');
    return false;
  }
  if (images.length > 9) {
    ElMessage.warning('质检图片最多上传 9 张');
    return false;
  }
  if (draft.value.status === QualityStatus.ABNORMAL && !draft.value.remark?.trim()) {
    ElMessage.warning('异常质检报告必须填写异常说明');
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
    const list = await getQualityReportList(props.itemId);
    history.value = list;
    localCurrent.value = list.find((item) => item.current) || list[0];
    emit('change', localCurrent.value);
    editVisible.value = false;
    ElMessage.success('质检报告已提交并生效');
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
  historyLoading.value = true;
  try {
    history.value = await getQualityReportList(props.itemId);
  } finally {
    historyLoading.value = false;
  }
}
</script>

<template>
  <section class="mx-4 mt-4 rounded-md border border-gray-200 p-4">
    <div class="mb-3 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <span class="text-sm font-semibold">质检报告</span>
        <ElTag :type="statusMeta.type">{{ statusMeta.label }}</ElTag>
        <span v-if="itemId" class="text-xs text-gray-400">
          共 {{ history.length || (current ? 1 : 0) }} 份报告
        </span>
      </div>
      <div class="flex gap-2">
        <ElButton v-if="localCurrent" @click="openHistory">查看全部报告</ElButton>
        <ElButton type="primary" @click="openCreate">新增质检报告</ElButton>
      </div>
    </div>
    <div v-if="localCurrent" class="grid grid-cols-[1fr_2fr] gap-4 text-sm">
      <div>
        <div class="text-gray-500">报告图片</div>
        <div class="mt-2 flex flex-wrap gap-2">
          <ElImage
            v-for="url in localCurrent.imageUrls"
            :key="url"
            :preview-src-list="localCurrent.imageUrls"
            :src="url"
            class="h-16 w-16 rounded border object-cover"
            fit="cover"
          />
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
    <ElEmpty v-else description="暂无质检报告，当前按待质检处理" :image-size="54" />
  </section>

  <ElDialog v-model="editVisible" title="新增质检报告" width="680px" append-to-body>
    <ElForm label-width="110px">
      <ElFormItem label="质检结果" required>
        <ElRadioGroup v-model="draft.status">
          <ElRadioButton :value="QualityStatus.PENDING">待质检</ElRadioButton>
          <ElRadioButton :value="QualityStatus.QUALIFIED">合格</ElRadioButton>
          <ElRadioButton :value="QualityStatus.ABNORMAL">异常</ElRadioButton>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="报告图片" :required="draft.status !== QualityStatus.PENDING">
        <ImageUpload
          v-model="draft.imageUrls"
          :accept="['jpg', 'jpeg', 'png']"
          :max-number="9"
          :max-size="10"
          multiple
        />
      </ElFormItem>
      <ElFormItem label="异常说明/备注" :required="draft.status === QualityStatus.ABNORMAL">
        <ElInput v-model="draft.remark" type="textarea" :rows="4" maxlength="500" show-word-limit />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="editVisible = false">取消</ElButton>
      <ElButton type="primary" :loading="submitting" @click="submitReport">确认提交</ElButton>
    </template>
  </ElDialog>

  <ElDialog v-model="historyVisible" title="质检报告历史" width="760px" append-to-body>
    <ElTimeline v-loading="historyLoading">
      <ElTimelineItem v-for="report in history" :key="report.id || report.createTime" :timestamp="report.createTime">
        <div class="rounded border p-3">
          <div class="mb-2 flex items-center gap-2">
            <ElTag :type="getStatusMeta(report.status).type">{{ getStatusMeta(report.status).label }}</ElTag>
            <ElTag v-if="report.current" type="primary" effect="plain">当前有效</ElTag>
            <span class="text-xs text-gray-400">{{ report.creatorName || report.creator || '-' }}</span>
          </div>
          <div class="flex flex-wrap gap-2">
            <ElImage v-for="url in report.imageUrls" :key="url" :src="url" :preview-src-list="report.imageUrls" class="h-14 w-14 rounded border" fit="cover" />
          </div>
          <div class="mt-2 text-sm">{{ report.remark || '无备注' }}</div>
        </div>
      </ElTimelineItem>
    </ElTimeline>
    <ElEmpty v-if="!historyLoading && history.length === 0" description="暂无质检报告" />
  </ElDialog>
</template>
