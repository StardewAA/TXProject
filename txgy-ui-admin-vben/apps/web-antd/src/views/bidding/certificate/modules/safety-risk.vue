<script setup lang="ts">
/* 安许 A/B/C 风险联动（契约 v1.1 §12.1 读取 / §12.2 重算 / §12.3 快照 / §12.4 宽限登记）
 * 口径（v1.1 §12.0）：
 *   · riskFlag=null → "未评估"，**不阻断**；blocked=true（命中 safety.abc.block.flags）才是硬阻断；
 *   · 配色/终态一律取字典 safety_risk_flag / risk_level；配备要求取配置 safety.abc.required；
 *   · 宽限原因取配置 safety.abc.grace.reason.whitelist，天数上限取 safety.abc.grace.legal.person.change.days；
 *   · 读取失败（含后端尚未登记 bidding:qualification:permit:query 导致的 403）→ 降级为"风险未知"，不阻断页面渲染。 */
import { computed, h, ref, watch } from 'vue';

import { message } from 'ant-design-vue';

import { CERT_ERR, certErrorCode, certErrorMsg } from '#/api/bidding/certificate';
import {
  getSafetyPermitRisk,
  getSafetyPermitRiskHistory,
  recheckSafetyPermitRisk,
  registerSafetyGrace,
  type SafetyPermitApi,
} from '#/api/bidding/safetyPermit';

import CopyText from '../../components/CopyText.vue';
import { useCertMeta } from '../useCertMeta';

const props = defineProps<{ entId?: null | number }>();

const { configNumber, configValue, dictLabel, dictTag } = useCertMeta();

const entIdInput = ref<number | undefined>(undefined);
const asOf = ref<string | undefined>(undefined);
const loading = ref(false);
const rechecking = ref(false);
const risk = ref<null | SafetyPermitApi.Risk>(null);
const degradeMsg = ref('');
const loadedOnce = ref(false);

/* 快照历史 */
const historyOpen = ref(false);
const historyLoading = ref(false);
const history = ref<SafetyPermitApi.RiskSnapshot[]>([]);

/* 宽限登记 */
const graceOpen = ref(false);
const graceSaving = ref(false);
const graceForm = ref<{ days?: number; reason?: string; remark?: string }>({ days: undefined, reason: undefined, remark: undefined });

const effectiveEntId = computed(() => props.entId ?? entIdInput.value);

const requiredMap = computed<Record<string, number>>(() => {
  const v = configValue<unknown>('safety.abc.required', null);
  if (v && typeof v === 'object' && !Array.isArray(v)) {
    const out: Record<string, number> = {};
    for (const [k, val] of Object.entries(v as Record<string, unknown>)) {
      const n = Number(val);
      if (Number.isFinite(n)) out[k] = n;
    }
    return out;
  }
  return {};
});
const graceReasons = computed(() => {
  const v = configValue<unknown>('safety.abc.grace.reason.whitelist', []);
  return Array.isArray(v) ? v.map((x) => ({ label: String(x), value: String(x) })) : [];
});
const graceMaxDays = computed(() => configNumber('safety.abc.grace.legal.person.change.days', 180));

const flagTag = computed(() => dictTag('safety_risk_flag', risk.value?.riskFlag));
const levelTag = computed(() => dictTag('risk_level', risk.value?.riskLevel));

async function load() {
  if (!effectiveEntId.value) return;
  loading.value = true;
  degradeMsg.value = '';
  try {
    risk.value = await getSafetyPermitRisk(effectiveEntId.value, asOf.value);
    loadedOnce.value = true;
  } catch (error: unknown) {
    risk.value = null;
    const code = certErrorCode(error);
    if (code === CERT_ERR.SAFETY_PERMIT_NOT_EXISTS) {
      degradeMsg.value = '该企业没有安全生产许可证记录（1060106024）';
    } else if (code === 403 || /权限/.test(certErrorMsg(error))) {
      degradeMsg.value = '风险接口不可用：需后端登记权限 bidding:qualification:permit:query（当前 403）';
    } else {
      degradeMsg.value = `风险读取失败：${certErrorMsg(error)}｜按契约降级为"风险未知"，不阻断业务`;
    }
  } finally {
    loading.value = false;
  }
}

watch(() => props.entId, (v) => {
  if (v) void load();
});

/** §12.2 重算：dryRun=true 只算不落库（试算），false 才写库 */
async function recheck(dryRun: boolean) {
  if (!risk.value?.permitId) {
    message.warning('缺少 permitId，无法重算（请先用 entId 读取到安许记录）');
    return;
  }
  rechecking.value = true;
  try {
    const data = await recheckSafetyPermitRisk(risk.value.permitId, asOf.value, dryRun);
    risk.value = data;
    message.success(dryRun ? '试算完成（未保存，仅用于核对）' : '已重算并保存');
  } catch (error: unknown) {
    message.warning(certErrorMsg(error));
  } finally {
    rechecking.value = false;
  }
}

async function openHistory() {
  if (!risk.value?.permitId) {
    message.warning('缺少 permitId，无法查看快照历史');
    return;
  }
  historyOpen.value = true;
  historyLoading.value = true;
  try {
    const page = await getSafetyPermitRiskHistory(risk.value.permitId, 1, 20);
    history.value = page.list ?? [];
  } catch (error: unknown) {
    history.value = [];
    message.warning(certErrorMsg(error));
  } finally {
    historyLoading.value = false;
  }
}

function openGrace() {
  graceForm.value = { days: undefined, reason: undefined, remark: undefined };
  graceOpen.value = true;
}

async function submitGrace() {
  if (!graceForm.value.reason) {
    message.warning('请选择宽限原因（受配置白名单约束）');
    return;
  }
  if (graceForm.value.days !== undefined && graceForm.value.days > graceMaxDays.value) {
    message.warning(`宽限天数最多 ${graceMaxDays.value} 天（配置 safety.abc.grace.legal.person.change.days）`);
    return;
  }
  graceSaving.value = true;
  try {
    await registerSafetyGrace({
      permitId: risk.value?.permitId,
      entId: risk.value?.permitId ? undefined : (effectiveEntId.value as number),
      reason: graceForm.value.reason,
      days: graceForm.value.days,
      remark: graceForm.value.remark,
    });
    message.success('宽限已登记');
    graceOpen.value = false;
    // 登记成功后强制重取风险（§12.4）
    await load();
  } catch (error: unknown) {
    const code = certErrorCode(error);
    if (code === CERT_ERR.SAFETY_GRACE_REASON_NOT_ALLOWED || code === CERT_ERR.SAFETY_GRACE_DAYS_EXCEEDED) {
      message.warning(certErrorMsg(error));
    } else {
      message.warning(certErrorMsg(error));
    }
  } finally {
    graceSaving.value = false;
  }
}

/** 哈希链校验：按时间倒序时，相邻两条应满足 prevHash === 下一条 recordHash */
function chainOk(idx: number): boolean {
  const cur = history.value[idx];
  const next = history.value[idx + 1];
  if (!cur || !next) return true;
  return (cur.prevHash ?? '') === (next.recordHash ?? '');
}
</script>

<template>
  <div class="ct-panel">
    <div class="ct-panel-h">
      <span class="ct-panel-t">安许 A/B/C 风险联动</span>
      <span class="ct-panel-meta">招投标/施工许可/现场监督统一口径；未评估不阻断，命中阻断规则才硬拦</span>
      <div style="margin-left: auto; display: flex; gap: 8px; align-items: center">
        <a-input-number
          v-if="!entId"
          v-model:value="entIdInput"
          :min="1"
          size="small"
          placeholder="企业 ID"
          style="width: 120px"
        />
        <a-button size="small" :loading="loading" @click="load">读取风险</a-button>
      </div>
    </div>
    <div class="ct-panel-b">
      <a-alert v-if="degradeMsg" class="mb-3" type="warning" show-icon :message="degradeMsg" />
      <a-spin :spinning="loading">
        <template v-if="risk">
          <a-alert
            v-if="risk.blocked"
            class="mb-3"
            type="error"
            show-icon
            message="命中阻断规则：该企业在整改到位前将被硬性拦截"
            :description="risk.hitRules || '（后端未返回命中说明）'"
          />
          <div class="flex flex-wrap items-center gap-3 mb-3">
            <span class="ct-lb" style="margin: 0">风险</span>
            <a-tag :color="flagTag.color">{{ risk.riskFlag ? flagTag.label : '未评估（不阻断）' }}</a-tag>
            <a-tag v-if="risk.riskLevel" :color="levelTag.color">等级 {{ levelTag.label }}</a-tag>
            <a-tag v-if="risk.graceApplied" color="orange">
              已宽限至 {{ risk.graceExpireAt || '—' }}（{{ risk.graceReason || '—' }}）
            </a-tag>
            <span class="ct-panel-meta">
              规则版本 {{ risk.ruleVersion || '—' }}；评估时间 {{ risk.riskEvaluatedAt || '未评估' }}
            </span>
          </div>

          <a-descriptions :column="3" size="small" bordered class="mb-3">
            <a-descriptions-item label="A 类（主要负责人）">
              {{ risk.aCount ?? 0 }}<span v-if="requiredMap.A !== undefined"> / 要求 {{ requiredMap.A }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="B 类（项目负责人）">
              {{ risk.bCount ?? 0 }}<span v-if="requiredMap.B !== undefined"> / 要求 {{ requiredMap.B }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="C 类（专职安全员）">
              {{ risk.cCount ?? 0 }}<span v-if="requiredMap.C !== undefined"> / 要求 {{ requiredMap.C }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="安许证 ID">{{ risk.permitId || '—' }}</a-descriptions-item>
            <a-descriptions-item label="企业 ID">{{ risk.entId ?? effectiveEntId ?? '—' }}</a-descriptions-item>
            <a-descriptions-item label="基准日">{{ asOf || '今天' }}</a-descriptions-item>
          </a-descriptions>

          <div v-if="risk.hitRules && !risk.blocked" class="ct-tip mb-2">命中规则：{{ risk.hitRules }}</div>

          <div class="flex flex-wrap items-center gap-2">
            <a-date-picker v-model:value="asOf" value-format="YYYY-MM-DD" placeholder="基准日（可回填预演）" size="small" />
            <a-button size="small" :loading="rechecking" @click="recheck(true)">试算（试算不保存）</a-button>
            <a-button size="small" type="primary" :loading="rechecking" @click="recheck(false)">重算并保存</a-button>
            <a-button size="small" @click="openGrace">登记整改宽限</a-button>
            <a-button size="small" @click="openHistory">快照历史</a-button>
          </div>
          <div class="ct-tip">
            "试算"只算不落库（dryRun），用于核对与排查；"重算并保存"会写入快照（含哈希链）。
          </div>
        </template>
        <div v-else-if="!degradeMsg" class="ct-empty">
          {{ loadedOnce ? '未取到风险数据' : '填写企业 ID 后点「读取风险」（riskFlag=null 表示未评估，不阻断）' }}
        </div>
      </a-spin>
    </div>

    <!-- §12.4 宽限登记 -->
    <a-modal
      v-model:open="graceOpen"
      title="登记整改宽限"
      :width="560"
      :confirm-loading="graceSaving"
      ok-text="提交"
      cancel-text="取消"
      destroy-on-close
      @ok="submitGrace"
    >
      <a-alert
        class="mb-3"
        type="info"
        show-icon
        message="宽限仅限非主观过错情形，原因受配置白名单约束；登记成功后会自动重取风险状态。"
      />
      <div class="ct-lb"><span class="ct-ast">*</span>宽限原因</div>
      <a-select
        v-model:value="graceForm.reason"
        class="w-full mb-3"
        :options="graceReasons"
        :placeholder="graceReasons.length ? '取自配置 safety.abc.grace.reason.whitelist' : '配置白名单为空，请先配置'"
        :disabled="graceReasons.length === 0"
        allow-clear
      />
      <div class="ct-lb">宽限天数（最多 {{ graceMaxDays }} 天）</div>
      <a-input-number v-model:value="graceForm.days" class="w-full mb-3" :min="1" :max="graceMaxDays" />
      <div class="ct-lb">备注</div>
      <a-textarea v-model:value="graceForm.remark" :rows="2" />
    </a-modal>

    <!-- §12.3 快照历史（含哈希链） -->
    <a-drawer v-model:open="historyOpen" class="bid-ind" :width="900" title="安许风险快照（哈希链）" destroy-on-close>
      <a-spin :spinning="historyLoading">
        <a-table
          class="ct-table"
          :columns="[
            { title: '评估时间', dataIndex: 'evaluatedAt', key: 'evaluatedAt', width: 165 },
            { title: '风险', dataIndex: 'riskFlag', key: 'riskFlag', width: 110 },
            { title: '上批', dataIndex: 'prevRiskFlag', key: 'prevRiskFlag', width: 100 },
            { title: '等级', dataIndex: 'riskLevel', key: 'riskLevel', width: 100 },
            { title: 'A/B/C', key: 'abc', width: 110 },
            { title: '宽限', dataIndex: 'graceApplied', key: 'graceApplied', width: 80 },
            { title: '批次号', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '批次号' }), dataIndex: 'batchNo', key: 'batchNo', width: 170 },
            { title: '链', key: 'chain', width: 90 },
          ]"
          :data-source="history"
          :pagination="false"
          row-key="id"
          size="small"
          :scroll="{ x: 1000 }"
        >
          <template #bodyCell="{ column, record, index }">
            <template v-if="column.key === 'riskFlag'">
              <a-tag :color="dictTag('safety_risk_flag', record.riskFlag).color">
                {{ record.riskFlag ? dictTag('safety_risk_flag', record.riskFlag).label : '未评估' }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'prevRiskFlag'">
              {{ record.prevRiskFlag ? dictLabel('safety_risk_flag', record.prevRiskFlag) : '—' }}
            </template>
            <template v-else-if="column.key === 'riskLevel'">
              {{ record.riskLevel ? dictLabel('risk_level', record.riskLevel) : '—' }}
            </template>
            <template v-else-if="column.key === 'abc'">
              {{ record.aCount ?? 0 }} / {{ record.bCount ?? 0 }} / {{ record.cCount ?? 0 }}
            </template>
            <template v-else-if="column.key === 'graceApplied'">
              <a-tag :color="record.graceApplied ? 'orange' : 'default'">{{ record.graceApplied ? '是' : '否' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'chain'">
              <a-tag :color="chainOk(index) ? 'green' : 'red'">{{ chainOk(index) ? '连续' : '断裂' }}</a-tag>
            </template>
          </template>
          <template #emptyText><div class="ct-empty">暂无快照</div></template>
        </a-table>
      </a-spin>
    </a-drawer>
  </div>
</template>
