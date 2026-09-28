<script setup lang="ts">
/* 证照状态流转（契约 §2.5）
 * · targetStatus 取字典 cert_status；**终态（terminal）提前禁用**，且当前状态不作为目标项
 * · 必须回传 version；1060106003 状态机拒绝 → 提示并刷新详情
 * · 事由：目标状态为终态时必填（吊销/注销留痕），非终态选填 */
import { computed, reactive, ref, watch } from 'vue';

import { message } from 'ant-design-vue';

import {
  CERT_ERR,
  certErrorCode,
  type CertificateApi,
  changeCertificateStatus,
  getCertificate,
} from '#/api/bidding/certificate';

import { useCertMeta } from '../useCertMeta';

const props = defineProps<{
  cert?: CertificateApi.Certificate | null;
  open: boolean;
}>();
const emit = defineEmits<{
  (e: 'done'): void;
  (e: 'update:open', v: boolean): void;
}>();

const { dictItems, dictTag, isTerminal } = useCertMeta();
const saving = ref(false);
/** 乐观锁版本：打开时先取列表行、再被详情覆盖；两者都没有则为 undefined（不伪造 0） */
const version = ref<number | undefined>(undefined);
const form = reactive<{ reason?: string; targetStatus?: string }>({ reason: undefined, targetStatus: undefined });

/** 可选目标状态：排除当前状态；仅提示终态（是否允许由后端裁判，前端只做禁用提示） */
const targets = computed(() =>
  dictItems('cert_status')
    .filter((i) => i.value !== props.cert?.certStatus)
    .map((i) => ({
      label: i.terminal ? `${i.label}（终态，不可再流转）` : i.label,
      terminal: i.terminal,
      value: i.value,
    })),
);
const currentTerminal = computed(() => isTerminal('cert_status', props.cert?.certStatus));
const targetTerminal = computed(() => !!targets.value.find((t) => t.value === form.targetStatus)?.terminal);
const reasonRequired = computed(() => targetTerminal.value);

watch(
  () => props.open,
  (v) => {
    if (!v) return;
    form.targetStatus = undefined;
    form.reason = undefined;
    // 乐观锁版本：未知时保持 undefined（提交前由详情回填），**不得伪造 0**（0 会被后端判为版本冲突）
    version.value = props.cert?.version;
    // 打开时拉一次最新详情，拿到最新乐观锁版本（避免用户拿着旧列表行提交）
    if (props.cert?.id) {
      getCertificate(props.cert.id)
        .then((d) => {
          version.value = d.version;
        })
        .catch(() => {
          /* 拉不到就用列表行版本，冲突时后端会给出 1060106002 */
        });
    }
  },
);

async function submit() {
  if (!props.cert?.id) return;
  if (!form.targetStatus) {
    message.warning('请选择目标状态');
    return;
  }
  if (reasonRequired.value && !form.reason?.trim()) {
    message.warning('目标状态为终态，请填写事由（留痕）');
    return;
  }
  if (version.value === undefined) {
    // 乐观锁版本缺失（详情未取到）：不发请求，避免被后端判为版本冲突
    message.warning('未取到该证照的最新版本号，请关闭窗口后重试');
    return;
  }
  saving.value = true;
  try {
    await changeCertificateStatus({
      certificateId: props.cert.id,
      targetStatus: form.targetStatus,
      version: version.value,
      reason: form.reason,
    });
    message.success('状态已流转');
    emit('done');
    emit('update:open', false);
  } catch (error: unknown) {
    const code = certErrorCode(error);
    if (code === CERT_ERR.STATUS_FLOW_DENIED) {
      message.warning('当前状态不允许该流转，已刷新详情');
      emit('done');
      emit('update:open', false);
    } else if (code === CERT_ERR.OPTIMISTIC_LOCK) {
      message.warning('数据已被他人修改，已刷新详情，请重新选择');
      emit('done');
    } else if (code === CERT_ERR.CERT_NOT_FOUND) {
      message.error('证照不存在，已刷新列表');
      emit('done');
      emit('update:open', false);
    }
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <a-modal
    :open="open"
    title="证照状态流转"
    :width="560"
    :confirm-loading="saving"
    ok-text="确认流转"
    cancel-text="取消"
    destroy-on-close
    @ok="submit"
    @cancel="emit('update:open', false)"
  >
    <a-alert
      v-if="currentTerminal"
      class="mb-3"
      type="warning"
      show-icon
      message="该证照当前为终态，按契约不再允许状态流转"
    />
    <div class="ct-lb">当前状态</div>
    <div class="mb-3">
      <a-tag :color="dictTag('cert_status', cert?.certStatus).color">
        {{ dictTag('cert_status', cert?.certStatus).label }}
      </a-tag>
    </div>
    <div class="ct-lb"><span class="ct-ast">*</span>目标状态</div>
    <a-select
      v-model:value="form.targetStatus"
      class="w-full mb-3"
      :options="targets"
      :disabled="currentTerminal || targets.length === 0"
      :placeholder="targets.length ? '选择目标状态' : '状态字典未配置，请联系管理员补种后再流转'"
    />
    <div class="ct-lb">
      事由<span v-if="reasonRequired" class="ct-ast">*</span>
    </div>
    <a-textarea
      v-model:value="form.reason"
      :rows="3"
      :placeholder="reasonRequired ? '终态流转必须填写事由，将写入留痕' : '选填（吊销/注销建议填写事由）'"
    />
    <div class="ct-tip">提交时回传乐观锁版本 v{{ version ?? '—' }}；冲突会自动提示并刷新。</div>
  </a-modal>
</template>
