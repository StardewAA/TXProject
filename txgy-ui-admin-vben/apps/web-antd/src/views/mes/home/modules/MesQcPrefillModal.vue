<script lang="ts" setup>
/**
 * 【任务包 D2】质检检验预填（过程检验 IPQC）
 *
 * 1. AI 助手按任务编号定位任务，预填：工单/任务/产品/工位/工序 + 检验数量（默认=已生产数量）
 * 2. 用户选择检验类型（字典 MES_IPQC_TYPE）、修改合格/不良数量、检验日期
 * 3. 提交走既有 MES 接口 POST /mes/qc/ipqc/create（用户本人 token，权限点不变）；AI 后端零写库
 * 4. 工废/料废/其他废品数量默认 0（与 MES 检验单页面默认一致）
 */
import { computed, reactive, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { useUserStore } from '@vben/stores';

import { DatePicker, InputNumber, message, Select } from 'ant-design-vue';

import { createIpqc, type MesQcIpqcApi } from '#/api/mes/qc/ipqc';
import { getTaskPage, type MesProTaskApi } from '#/api/mes/pro/task';

const emit = defineEmits<{ submitted: [summary: string] }>();

const userStore = useUserStore();

const loading = ref(false);
const submitting = ref(false);
const task = ref<MesProTaskApi.Task>();

/** 检验类型选项（字典 MES_IPQC_TYPE，与 MES 检验单页面同源） */
const typeOptions = ref<{ label: string; value: number }[]>(
  getDictOptions(DICT_TYPE.MES_IPQC_TYPE, 'number') as any,
);

const form = reactive({
  type: undefined as number | undefined,
  checkQuantity: 0,
  qualifiedQuantity: 0,
  unqualifiedQuantity: 0,
  inspectDate: undefined as number | undefined,
});

/** 默认检验数量 = 已生产数量（对过程检验而言即待检的产出） */
const defaultCheckQuantity = computed(() =>
  Number(task.value?.producedQuantity ?? task.value?.quantity ?? 0),
);

function fillDefaultQuantities() {
  form.checkQuantity = defaultCheckQuantity.value;
  form.qualifiedQuantity = defaultCheckQuantity.value;
  form.unqualifiedQuantity = 0;
}

const [Modal, modalApi] = useVbenModal({
  title: '过程检验预填',
  centered: true,
  draggable: false,
  footer: true,
  confirmText: '确认创建检验单',
  cancelText: '取消',
  async onConfirm() {
    await handleSubmit();
  },
});

/** 按任务编号打开：查询任务 → 预填 */
async function open(taskCode: string) {
  modalApi.setState({ title: '过程检验预填', loading: true });
  modalApi.open();
  loading.value = true;
  try {
    const page = await getTaskPage({ code: taskCode, pageNo: 1, pageSize: 1 });
    const hit = page?.list?.[0];
    if (!hit) {
      message.error(`未找到任务编号为「${taskCode}」的生产任务，请核对编号`);
      await modalApi.close();
      return;
    }
    task.value = hit;
    // 检验类型默认取字典第一项（用户可改）
    form.type = typeOptions.value?.[0]?.value;
    fillDefaultQuantities();
    form.inspectDate = Date.now();
  } catch (e: any) {
    message.error(e?.message ?? '任务信息读取失败，请稍后重试');
    await modalApi.close();
  } finally {
    loading.value = false;
    modalApi.setState({ loading: false });
  }
}

/** 组装 IPQC 创建数据（工废/料废/其他废默认 0，与检验单页面一致） */
function buildPayload(): MesQcIpqcApi.Ipqc {
  const t = task.value;
  if (!t) throw new Error('任务数据为空');
  if (!form.type) throw new Error('请选择检验类型');
  if (!form.checkQuantity || form.checkQuantity <= 0) {
    throw new Error('检验数量必须大于 0');
  }
  if (form.qualifiedQuantity + form.unqualifiedQuantity !== form.checkQuantity) {
    throw new Error('合格数量 + 不良数量 必须等于 检验数量');
  }
  const userId = userStore.userInfo?.id as number;
  if (!userId) throw new Error('无法识别当前登录用户，请重新登录后再试');
  return {
    type: form.type,
    workOrderId: t.workOrderId,
    taskId: t.id,
    workstationId: t.workstationId,
    processId: t.processId,
    itemId: t.itemId,
    itemCode: t.itemCode,
    itemName: t.itemName,
    itemSpecification: t.itemSpecification,
    checkQuantity: form.checkQuantity,
    qualifiedQuantity: form.qualifiedQuantity,
    unqualifiedQuantity: form.unqualifiedQuantity,
    laborScrapQuantity: 0,
    materialScrapQuantity: 0,
    otherScrapQuantity: 0,
    inspectDate: form.inspectDate,
    inspectorUserId: userId,
  };
}

/** 确认创建检验单（既有接口，用户本人操作） */
async function handleSubmit() {
  let payload: MesQcIpqcApi.Ipqc;
  try {
    payload = buildPayload();
  } catch (e: any) {
    message.warning(e?.message ?? '请完善检验信息');
    return;
  }
  submitting.value = true;
  modalApi.lock();
  try {
    await createIpqc(payload);
    const summary = `任务 ${task.value?.code ?? ''} · 检验数量 ${form.checkQuantity}`;
    message.success(`过程检验单已创建：${summary}`);
    emit('submitted', summary);
    await modalApi.close();
  } catch (e: any) {
    message.error(e?.message ?? '检验单创建失败，请稍后重试');
  } finally {
    submitting.value = false;
    modalApi.unlock();
  }
}

defineExpose({ open });
</script>

<template>
  <Modal class="w-1/2">
    <div v-if="loading" class="py-6 text-center text-sm text-gray-400">
      正在读取任务信息…
    </div>
    <div v-else class="doc-form">
      <div class="doc-form__section">
        <div class="doc-form__row">
          <span class="doc-form__label">任务编号：</span>
          <span>{{ task?.code }}</span>
        </div>
        <div class="doc-form__row">
          <span class="doc-form__label">所属工单：</span>
          <span>{{ task?.workOrderCode || '-' }}</span>
        </div>
        <div class="doc-form__row">
          <span class="doc-form__label">产品物料：</span>
          <span>{{ task?.itemName || '-' }}（{{ task?.itemCode || '-' }}）</span>
        </div>
        <div class="doc-form__row">
          <span class="doc-form__label">工位 / 工序：</span>
          <span>{{ task?.workstationName || '-' }} / {{ task?.processName || '-' }}</span>
        </div>
      </div>

      <div class="doc-form__section">
        <div class="doc-form__row">
          <span class="doc-form__label">检验类型：</span>
          <Select
            v-model:value="form.type"
            class="w-60"
            :options="typeOptions"
            placeholder="请选择检验类型"
          />
        </div>
        <div class="doc-form__row">
          <span class="doc-form__label">检验数量：</span>
          <InputNumber
            v-model:value="form.checkQuantity"
            class="w-40"
            :min="0"
            @change="fillDefaultQuantities"
          />
        </div>
        <div class="doc-form__row">
          <span class="doc-form__label">合格数量：</span>
          <InputNumber
            v-model:value="form.qualifiedQuantity"
            class="w-40"
            :min="0"
          />
        </div>
        <div class="doc-form__row">
          <span class="doc-form__label">不良数量：</span>
          <InputNumber
            v-model:value="form.unqualifiedQuantity"
            class="w-40"
            :min="0"
          />
        </div>
        <div class="doc-form__row">
          <span class="doc-form__label">检验日期：</span>
          <DatePicker
            v-model:value="form.inspectDate"
            show-time
            value-format="x"
            class="w-60"
          />
        </div>
      </div>
      <div class="doc-form__tip">
        提示：合格数量 + 不良数量 须等于检验数量；工废/料废/其他废品默认 0，可在检验单页面补充。
      </div>
    </div>
  </Modal>
</template>

<style scoped>
.doc-form__section {
  padding: 10px 12px;
  margin-bottom: 12px;
  background: #f9fafb;
  border-radius: 8px;
}

.doc-form__row {
  display: flex;
  align-items: center;
  padding: 4px 0;
  font-size: 14px;
}

.doc-form__label {
  flex-shrink: 0;
  width: 110px;
  color: #374151;
}

.doc-form__tip {
  margin-top: 4px;
  font-size: 12px;
  color: #9ca3af;
}
</style>
