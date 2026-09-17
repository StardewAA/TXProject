<script lang="ts" setup>
/**
 * 【任务包 D1】安灯异常上报（草稿 → 确认 → 提交）
 *
 * 1. AI 助手把用户口述的异常描述作为"草稿"带进弹窗（备注栏），用户补选：安灯配置（原因+等级）、工位
 * 2. 提交走既有 MES 接口 POST /mes/pro/andon-record/create（用户本人 token，权限点不变）
 * 3. 原因/等级来自安灯配置（MesProAndonConfig），AI 不编造；AI 后端零写库（符合只读红线）
 */
import { computed, reactive, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { message, Select, Textarea } from 'ant-design-vue';

import {
  createAndonRecord,
} from '#/api/mes/pro/andon/record';
import { getAndonConfigList } from '#/api/mes/pro/andon/config';
import { getWorkstationPage } from '#/api/mes/md/workstation';

const emit = defineEmits<{ submitted: [summary: string] }>();

const userStore = useUserStore();

const loading = ref(false);
const submitting = ref(false);

/** 安灯配置（原因 + 等级 + 处置人）下拉 */
const configOptions = ref<{ label: string; value: number; level: number }[]>([]);
/** 工位下拉 */
const workstationOptions = ref<{ label: string; value: number }[]>([]);

const form = reactive({
  configId: undefined as number | undefined,
  workstationId: undefined as number | undefined,
  remark: '',
});

/** 等级码转中文（与 MesProAndonLevelEnum 一致） */
function levelName(level?: number): string {
  return level === 1 ? '一级' : level === 2 ? '二级' : level === 3 ? '三级' : `等级${level ?? '?'}`;
}

/** 选中配置后展示对应等级 */
const selectedLevel = computed(() => {
  const hit = configOptions.value.find((o) => o.value === form.configId);
  return hit ? levelName(hit.level) : '';
});

/** 打开弹窗：加载配置与工位，带上 AI 草稿描述 */
async function open(draftText?: string) {
  modalApi.setState({ title: '安灯异常上报', loading: true });
  modalApi.open();
  loading.value = true;
  form.remark = draftText ?? '';
  form.configId = undefined;
  form.workstationId = undefined;
  try {
    // 并行加载：安灯配置（原因+等级）与工位列表
    const [configs, workstations] = await Promise.all([
      getAndonConfigList(),
      getWorkstationPage({ pageNo: 1, pageSize: 100 }),
    ]);
    configOptions.value = (configs ?? []).map((c: any) => ({
      label: `${c.reason}（${levelName(c.level)}）`,
      value: c.id,
      level: c.level,
    }));
    workstationOptions.value = (workstations?.list ?? []).map((w: any) => ({
      label: w.name ?? String(w.id),
      value: w.id,
    }));
    if (configOptions.value.length === 0) {
      message.warning('尚未维护安灯配置（原因/等级），请先在「安灯配置」页面新增后再上报');
    }
  } catch (e: any) {
    message.error(e?.message ?? '安灯配置/工位信息读取失败');
    await modalApi.close();
  } finally {
    loading.value = false;
    modalApi.setState({ loading: false });
  }
}

const [Modal, modalApi] = useVbenModal({
  title: '安灯异常上报',
  centered: true,
  draggable: false,
  footer: true,
  confirmText: '确认呼叫安灯',
  cancelText: '取消',
  async onConfirm() {
    await handleSubmit();
  },
});

/** 确认呼叫：走既有创建接口（用户本人操作） */
async function handleSubmit() {
  const userId = userStore.userInfo?.id as number;
  if (!userId) {
    message.error('无法识别当前登录用户，请重新登录后再试');
    return;
  }
  if (!form.configId) {
    message.warning('请选择安灯配置（异常原因）');
    return;
  }
  if (!form.workstationId) {
    message.warning('请选择工位');
    return;
  }
  submitting.value = true;
  modalApi.lock();
  try {
    await createAndonRecord({
      configId: form.configId,
      workstationId: form.workstationId,
      userId,
      remark: form.remark,
    });
    const cfg = configOptions.value.find((o) => o.value === form.configId);
    const summary = `${cfg?.label ?? '安灯'} @ ${
      workstationOptions.value.find((o) => o.value === form.workstationId)?.label ?? ''
    }`;
    message.success(`安灯呼叫成功：${summary}`);
    emit('submitted', summary);
    await modalApi.close();
  } catch (e: any) {
    message.error(e?.message ?? '安灯呼叫失败，请稍后重试');
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
      正在读取安灯配置…
    </div>
    <template v-else>
      <div class="doc-form__row">
        <span class="doc-form__label">异常原因：</span>
        <Select
          v-model:value="form.configId"
          class="flex-1"
          :options="configOptions"
          placeholder="请选择安灯配置（决定原因与等级）"
          show-search
          option-filter-prop="label"
        />
      </div>
      <div class="doc-form__row" v-if="selectedLevel">
        <span class="doc-form__label">呼叫等级：</span>
        <span class="andon-level">{{ selectedLevel }}</span>
      </div>
      <div class="doc-form__row">
        <span class="doc-form__label">工位：</span>
        <Select
          v-model:value="form.workstationId"
          class="flex-1"
          :options="workstationOptions"
          placeholder="请选择发生异常的工位"
          show-search
          option-filter-prop="label"
        />
      </div>
      <div class="doc-form__row doc-form__row--top">
        <span class="doc-form__label">情况说明：</span>
        <Textarea
          v-model:value="form.remark"
          class="flex-1"
          :rows="3"
          placeholder="已根据你的描述预填，可修改补充"
        />
      </div>
      <div class="doc-form__tip">
        安灯规范：一级=操作工自行处理；二级=班长 5 分钟内响应；三级=立即停线，主管到场。提交后将进入待处置列表。
      </div>
    </template>
  </Modal>
</template>

<style scoped>
.doc-form__row {
  display: flex;
  align-items: center;
  padding: 5px 0;
  font-size: 14px;
}

.doc-form__row--top {
  align-items: flex-start;
}

.doc-form__label {
  flex-shrink: 0;
  width: 100px;
  color: #374151;
}

.andon-level {
  font-weight: 600;
  color: #d46b08;
}

.doc-form__tip {
  margin-top: 8px;
  font-size: 12px;
  color: #9ca3af;
}
</style>
