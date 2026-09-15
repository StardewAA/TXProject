<script lang="ts" setup>
/**
 * 【任务包 B·P0】报工智能填单 —— 预填确认弹窗
 *
 * 1. AI 助手识别报工意图后，按任务编号在这里预填：任务/工单/产品/工序/计划数量/已生产数量
 * 2. 用户只需填写：本次报工数量、合格/不良数量、审核人、备注
 * 3. 提交走「既有 MES 接口」：create（草稿）→ submit（提交审批），
 *    编号用平台自动编码规则（PRO_FEEDBACK_CODE），与 MES 报工页面完全一致
 * 4. 权限与红线：读取用用户本人 token 的既有只读接口；提交是用户本人的操作，
 *    AI 后端不经手任何写库请求（符合“AI 线对 MES 只读”约定）
 */
import { computed, reactive, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { MesAutoCodeRuleCode } from '@vben/constants';
import { useUserStore } from '@vben/stores';

import { Input, InputNumber, message, Select, Textarea } from 'ant-design-vue';

import { generateAutoCode } from '#/api/mes/md/autocode/record';
import {
  createFeedback,
  submitFeedback,
  type MesProFeedbackApi,
} from '#/api/mes/pro/feedback';
import { getTaskPage, type MesProTaskApi } from '#/api/mes/pro/task';
import { getRouteProcessByRouteAndProcess } from '#/api/mes/pro/route/process';
import { getSimpleUserList } from '#/api/system/user';

const emit = defineEmits<{ submitted: [feedbackCode: string] }>();

const userStore = useUserStore();

const loading = ref(false);
const submitting = ref(false);
const task = ref<MesProTaskApi.Task>();
/** 审核人下拉（平台用户列表） */
const userOptions = ref<{ label: string; value: number }[]>([]);

async function loadUserOptions() {
  if (userOptions.value.length > 0) return;
  try {
    const users = await getSimpleUserList();
    userOptions.value = (users ?? []).map((u: any) => ({
      label: u.nickname ?? String(u.id),
      value: u.id,
    }));
  } catch {
    userOptions.value = [];
  }
}
/** 工序是否需要检验：true = 报工数量计入“待检测”，false = 需要拆合格/不良 */
const checkFlag = ref(true);
/** 表单可填写项 */
const form = reactive({
  feedbackQuantity: 0,
  qualifiedQuantity: 0,
  unqualifiedQuantity: 0,
  feedbackTime: '',
  approveUserId: undefined as number | undefined,
  remark: '',
});

/** 剩余可报数量 = 计划数量 - 已生产数量 */
const remainingQuantity = computed(() => {
  const plan = Number(task.value?.quantity ?? 0);
  const produced = Number(task.value?.producedQuantity ?? 0);
  return Math.max(plan - produced, 0);
});

/** 报工数量默认 = 剩余可报数量 */
function fillDefaultQuantity() {
  form.feedbackQuantity = remainingQuantity.value;
  alignQuantities();
}

/** 与 MES 报工页面对齐的数量规则：需检验时全部计入待检，否则拆合格/不良 */
function alignQuantities() {
  if (checkFlag.value) {
    form.unqualifiedQuantity = 0;
    form.qualifiedQuantity = 0;
  } else {
    form.qualifiedQuantity = form.feedbackQuantity;
    form.unqualifiedQuantity = 0;
  }
}

const [Modal, modalApi] = useVbenModal({
  title: '报工智能填单',
  centered: true,
  draggable: false,
  footer: true,
  confirmText: '确认提交报工',
  cancelText: '取消',
  async onConfirm() {
    await handleSubmit();
  },
});

/** 按任务编号打开：查询任务 → 预填 */
async function open(taskCode: string) {
  modalApi.setState({ title: '报工智能填单', loading: true });
  modalApi.open();
  loading.value = true;
  loadUserOptions();
  try {
    const page = await getTaskPage({ code: taskCode, pageNo: 1, pageSize: 1 });
    const hit = page?.list?.[0];
    if (!hit) {
      message.error(`未找到任务编号为「${taskCode}」的生产任务，请核对编号`);
      await modalApi.close();
      return;
    }
    task.value = hit;
    // 工序是否需要检验（与 MES 报工页面同一规则）
    checkFlag.value = hit.routeId
      ? await resolveCheckFlag(hit.routeId, hit.processId)
      : true;
    fillDefaultQuantity();
    form.feedbackTime = formatNow();
    form.remark = '';
  } catch (e: any) {
    message.error(e?.message ?? '任务信息读取失败，请稍后重试');
    await modalApi.close();
  } finally {
    loading.value = false;
    modalApi.setState({ loading: false });
  }
}

/** 工序检验标记（与 MES 报工页面同一接口） */
async function resolveCheckFlag(routeId?: number, processId?: number) {
  if (!routeId || !processId) return true;
  try {
    const routeProcess = await getRouteProcessByRouteAndProcess(
      routeId,
      processId,
    );
    return routeProcess?.checkFlag ?? false;
  } catch {
    return true;
  }
}

/** 当前时间 yyyy-MM-dd HH:mm:ss（后端 LocalDateTime 格式） */
function formatNow(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

/** 校验并组装提交数据 */
function buildPayload(): MesProFeedbackApi.Feedback {
  if (!task.value) {
    throw new Error('任务数据为空');
  }
  if (!form.feedbackQuantity || form.feedbackQuantity <= 0) {
    throw new Error('本次报工数量必须大于 0');
  }
  const userId = userStore.userInfo?.id as number;
  if (!userId) {
    throw new Error('无法识别当前登录用户，请重新登录后再试');
  }
  if (!form.approveUserId) {
    throw new Error('请选择审核人');
  }
  if (!checkFlag.value && form.qualifiedQuantity < 0) {
    throw new Error('合格数量不能为负数');
  }
  return {
    taskId: task.value.id,
    workOrderId: task.value.workOrderId,
    itemId: task.value.itemId,
    itemCode: task.value.itemCode,
    itemName: task.value.itemName,
    itemSpecification: task.value.itemSpecification,
    unitMeasureName: task.value.unitMeasureName,
    workstationId: task.value.workstationId,
    routeId: task.value.routeId,
    processId: task.value.processId,
    processName: task.value.processName,
    scheduledQuantity: task.value.quantity,
    feedbackQuantity: form.feedbackQuantity,
    qualifiedQuantity: checkFlag.value ? 0 : form.qualifiedQuantity,
    unqualifiedQuantity: checkFlag.value ? 0 : form.unqualifiedQuantity,
    uncheckQuantity: checkFlag.value ? form.feedbackQuantity : 0,
    feedbackUserId: userId,
    feedbackTime: form.feedbackTime,
    approveUserId: form.approveUserId,
    remark: form.remark,
  };
}

/** 确认提交：生成报工单号 → 创建草稿 → 提交审批（与 MES 报工页面同一闭环） */
async function handleSubmit() {
  let payload: MesProFeedbackApi.Feedback;
  let code: string;
  try {
    payload = buildPayload();
    code = await generateAutoCode(MesAutoCodeRuleCode.PRO_FEEDBACK_CODE);
  } catch (e: any) {
    message.warning(e?.message ?? '请完善报工信息');
    return;
  }
  submitting.value = true;
  modalApi.lock();
  try {
    const id = await createFeedback({ ...payload, code });
    await submitFeedback(id);
    message.success(`报工单 ${code} 已提交，进入审批流`);
    emit('submitted', code);
    await modalApi.close();
  } catch (e: any) {
    message.error(e?.message ?? '报工提交失败，请稍后重试');
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
      <!-- 任务信息（只读，来自解析/查询结果） -->
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
          <span class="doc-form__label">计划数量：</span>
          <span>{{ task?.quantity ?? 0 }}</span>
        </div>
        <div class="doc-form__row">
          <span class="doc-form__label">已生产：</span>
          <span>{{ task?.producedQuantity ?? 0 }}</span>
        </div>
        <div class="doc-form__row">
          <span class="doc-form__label">剩余可报：</span>
          <span class="text-blue-600 font-semibold">{{ remainingQuantity }}</span>
        </div>
      </div>

      <!-- 可填写项 -->
      <div class="doc-form__section">
        <div class="doc-form__row">
          <span class="doc-form__label">本次报工数量：</span>
          <InputNumber
            v-model:value="form.feedbackQuantity"
            class="w-40"
            :min="0"
            @change="alignQuantities"
          />
        </div>
        <template v-if="!checkFlag">
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
        </template>
        <div v-else class="doc-form__row">
          <span class="doc-form__label">质检方式：</span>
          <span>该工序需要检验，本次报工将全部计入「待检测数量」</span>
        </div>
        <div class="doc-form__row">
          <span class="doc-form__label">审核人：</span>
          <Select
            v-model:value="form.approveUserId"
            class="w-60"
            :options="userOptions"
            :field-names="{ label: 'label', value: 'value' }"
            placeholder="请选择审核人"
            show-search
            option-filter-prop="label"
          />
        </div>
        <div class="doc-form__row">
          <span class="doc-form__label">备注：</span>
          <Textarea
            v-model:value="form.remark"
            class="flex-1"
            :rows="2"
            placeholder="选填"
          />
        </div>
      </div>
      <div class="doc-form__tip">
        提示：提交后将进入审批流；数据仅用于本次报工单创建，不会修改工单/任务原始数据。
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
  width: 120px;
  color: #374151;
}

.doc-form__tip {
  margin-top: 4px;
  font-size: 12px;
  color: #9ca3af;
}
</style>
