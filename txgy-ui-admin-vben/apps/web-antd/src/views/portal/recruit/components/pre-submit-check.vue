<script lang="ts" setup>
/**
 * 投递前检查（A06）。
 *
 * 只做提醒，不做闸门：检查结果不会禁用「确认投递」，也不给出任何分数或通过判定。
 * 模型未配置、额度不足或调用失败时如实说明原因，不显示任何编造结论。
 */
import { ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { message } from 'ant-design-vue';

import {
  buildAnalyzeIdempotencyKey,
  getRecruitAgentTask,
  submitPreSubmitCheck,
  type HrmPortalRecruitAgentApi,
} from '#/api/hrm/portal/recruit-agent';

const props = defineProps<{
  postId: number;
  /** 未选择在线简历版本时保持禁用，允许外层渐进式传入。 */
  resumeVersionId?: number;
}>();

type Phase = 'failed' | 'idle' | 'running' | 'succeeded';

const phase = ref<Phase>('idle');
const result = ref<HrmPortalRecruitAgentApi.PreCheckResult>();
const errorMessage = ref('');

/** 检查项总数，为 0 时说明没有发现问题 */
const issueCount = ref(0);

async function run() {
  if (phase.value === 'running' || !props.postId || !props.resumeVersionId) return;
  phase.value = 'running';
  errorMessage.value = '';
  result.value = undefined;
  try {
    const taskId = await submitPreSubmitCheck({
      resumeVersionId: props.resumeVersionId,
      postId: props.postId,
      idempotencyKey: buildAnalyzeIdempotencyKey(),
    });
    const detail = await getRecruitAgentTask(taskId);
    if (detail?.status === 'SUCCEEDED' && detail.result) {
      result.value = detail.result as HrmPortalRecruitAgentApi.PreCheckResult;
      issueCount.value =
        (result.value.requiredMissing?.length || 0) +
        (result.value.conflicts?.length || 0) +
        (result.value.gaps?.length || 0);
      phase.value = 'succeeded';
      return;
    }
    errorMessage.value = detail?.errorMessage || '本次检查未完成，请稍后重试';
    phase.value = 'failed';
  } catch (error: any) {
    errorMessage.value = error?.message || '提交失败，请稍后重试';
    phase.value = 'failed';
    message.warning(errorMessage.value);
  }
}

function reset() {
  phase.value = 'idle';
  result.value = undefined;
  errorMessage.value = '';
}
</script>

<template>
  <section class="precheck">
    <div class="precheck-head">
      <span class="precheck-title">
        <IconifyIcon icon="lucide:clipboard-check" />
        投递前检查
      </span>
      <a-button
        size="small"
        :loading="phase === 'running'"
        :disabled="!props.postId || !props.resumeVersionId"
        @click="run"
      >
        {{ phase === 'succeeded' ? '重新检查' : '检查一下' }}
      </a-button>
    </div>
    <p class="precheck-tip">
      可选步骤，只提醒可能遗漏的地方，<strong>不影响投递</strong>。
    </p>

    <div v-if="phase === 'failed'" class="precheck-failed">
      <span>{{ errorMessage }}</span>
      <a-button size="small" type="link" @click="reset">收起</a-button>
    </div>

    <div v-else-if="phase === 'succeeded' && result" class="precheck-result">
      <p class="precheck-summary">{{ result.summary }}</p>

      <p v-if="issueCount === 0" class="precheck-clean">
        <IconifyIcon icon="lucide:circle-check" />
        本次没有发现明显问题。
      </p>

      <template v-else>
        <div v-if="result.requiredMissing?.length" class="precheck-group is-warn">
          <strong>建议补充</strong>
          <ul>
            <li v-for="(item, index) in result.requiredMissing" :key="index">
              {{ item.item }}
              <small v-if="item.note">{{ item.note }}</small>
            </li>
          </ul>
        </div>
        <div v-if="result.conflicts?.length" class="precheck-group is-risk">
          <strong>可能矛盾</strong>
          <ul>
            <li v-for="(item, index) in result.conflicts" :key="index">
              {{ item.point }}
              <small v-if="item.evidence">{{ item.evidence }}</small>
            </li>
          </ul>
        </div>
        <div v-if="result.gaps?.length" class="precheck-group is-confirm">
          <strong>与岗位的差距</strong>
          <ul>
            <li v-for="(item, index) in result.gaps" :key="index">
              {{ item.point }}
              <small v-if="item.evidence">{{ item.evidence }}</small>
            </li>
          </ul>
        </div>
      </template>

      <p class="precheck-disclaimer">AI 辅助分析，仅供参考</p>
    </div>
  </section>
</template>

<style scoped>
.precheck {
  display: grid;
  gap: 8px;
  margin-top: 6px;
  padding: 12px 14px;
  border: 1px solid #d7e9ed;
  border-radius: 8px;
  background: #f8fcfd;
}

.precheck-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.precheck-title {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #2f5d73;
  font-size: 13px;
  font-weight: 600;
}

.precheck-tip {
  margin: 0;
  color: #7d9299;
  font-size: 12px;
  line-height: 1.6;
}

.precheck-tip strong {
  color: #0f777d;
}

.precheck-failed {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: #b45309;
  font-size: 12px;
  line-height: 1.6;
}

.precheck-result {
  display: grid;
  gap: 8px;
}

.precheck-summary {
  margin: 0;
  color: #334155;
  font-size: 13px;
  line-height: 1.7;
}

.precheck-clean {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: #047857;
  font-size: 13px;
}

.precheck-group {
  padding: 10px 12px;
  border-radius: 8px;
  background: #fff;
}

.precheck-group strong {
  display: block;
  margin-bottom: 5px;
  font-size: 12px;
}

.precheck-group ul {
  display: grid;
  gap: 5px;
  margin: 0;
  padding-left: 16px;
  color: #475569;
  font-size: 12px;
  line-height: 1.6;
}

.precheck-group small {
  display: block;
  color: #8497a3;
  font-size: 11px;
  line-height: 1.5;
}

.precheck-group.is-warn {
  border: 1px solid #f2e2c4;
}

.precheck-group.is-warn strong {
  color: #b45309;
}

.precheck-group.is-risk {
  border: 1px solid #f3d9d9;
}

.precheck-group.is-risk strong {
  color: #b91c1c;
}

.precheck-group.is-confirm {
  border: 1px solid #d7e3f4;
}

.precheck-group.is-confirm strong {
  color: #1d4ed8;
}

.precheck-disclaimer {
  margin: 0;
  color: #9bb0b6;
  font-size: 11px;
  text-align: right;
}
</style>
