<script lang="ts" setup>
/**
 * 规则配置面板（RuleConfigController：get / save / rollback / history / audit/page）
 *
 * 为什么做成独立面板：阈值直接改业务判定（安许 A/B/C 配备、变更窗口、检查提前量……），
 * 必须"看得懂、改得可控、改完可追"——故同屏给出 生效值 + 来源层 + 允许边界 + 版本历史 + 读取审计。
 * 口径：所有阈值文案与边界都来自接口（description / legalFloor / regulationRef），组件内不写死任何阈值。
 */
import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';

import { message, Modal } from 'ant-design-vue';

import {
  getRuleConfig,
  getRuleConfigAuditPage,
  getRuleConfigHistory,
  rollbackRuleConfig,
  RULE_HIT_SOURCE_OPTIONS,
  RULE_KEY_SUGGESTIONS,
  RULE_STATUS_OPTIONS,
  RULE_VALUE_TYPE_OPTIONS,
  type RuleConfigApi,
  saveRuleConfig,
} from '#/api/bidding/ruleConfig';

import { fmtDateTime } from '../../certificate/format';

const { hasAccessByCodes } = useAccess();
const canQuery = computed(() => hasAccessByCodes(['bidding:rule-config:query']));
const canUpdate = computed(() => hasAccessByCodes(['bidding:rule-config:update']));

const ruleKey = ref('safety.abc.required');
const keyOptions = RULE_KEY_SUGGESTIONS.map((s) => ({ label: `${s.key} — ${s.note}`, value: s.key }));
const loading = ref(false);
const item = ref<null | RuleConfigApi.Item>(null);
const history = ref<RuleConfigApi.HistoryRow[]>([]);

const form = reactive<RuleConfigApi.SaveReq>({
  changeReason: '',
  configValue: '',
  ruleKey: '',
  status: 'DRAFT',
  valueType: 'INT',
});
const saving = ref(false);
/* 生效区间：选择器用 'YYYY-MM-DD HH:mm:ss' 便于阅读，提交前转 epoch 毫秒
 * （框架 TimestampLocalDateTimeDeserializer 只接受数字，直接传字符串会得到 1970 或反序列化失败）。 */
const effectiveFromText = ref('');
const effectiveToText = ref('');
function toMillis(text: string): number | undefined {
  const ms = new Date(text.replace(/-/g, '/')).getTime();
  return Number.isNaN(ms) ? undefined : ms;
}

/** 值 → 表单文本：JSON/对象按美化 JSON，标量原样 */
function toText(value: unknown): string {
  if (value === null || value === undefined) {
    return '';
  }
  return typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value);
}

function hintOf(value: unknown): string {
  if (value === null || value === undefined) {
    return '—';
  }
  return typeof value === 'object' ? JSON.stringify(value) : String(value);
}

async function load() {
  const key = ruleKey.value.trim();
  if (!key) {
    message.warning('请填写规则键 ruleKey');
    return;
  }
  loading.value = true;
  try {
    const it = await getRuleConfig(key);
    item.value = it;
    history.value = await getRuleConfigHistory(key);
    effectiveFromText.value = '';
    effectiveToText.value = '';
    Object.assign(form, {
      changeReason: '',
      configValue: toText(it?.value),
      description: it?.description,
      legalFloor: it?.legalFloor,
      regulationRef: it?.regulationRef,
      ruleGroup: it?.ruleGroup,
      ruleKey: key,
      // 生效值响应不含状态字段，故写入目标默认「草稿」（改配置先存草稿、确认后再发布/GRAY）
      status: 'DRAFT',
      valueType: it?.valueType ?? 'INT',
      version: it?.versionNo,
    });
  } catch {
    item.value = null;
    history.value = [];
  } finally {
    loading.value = false;
  }
}

async function submit() {
  if (!form.ruleKey.trim()) {
    message.warning('规则键不能为空');
    return;
  }
  if (!form.configValue.trim()) {
    message.warning('配置值不能为空');
    return;
  }
  if (!form.changeReason.trim()) {
    message.warning('变更原因必填（配置变更需留痕追责）');
    return;
  }
  Modal.confirm({
    content: `即将写入 ${form.ruleKey} = ${form.configValue}（类型 ${form.valueType}，目标状态 ${form.status}），确认提交？`,
    okText: '提交',
    onOk: async () => {
      saving.value = true;
      try {
        await saveRuleConfig({
          ...form,
          effectiveFrom: effectiveFromText.value ? toMillis(effectiveFromText.value) : undefined,
          effectiveTo: effectiveToText.value ? toMillis(effectiveToText.value) : undefined,
        });
        message.success('配置已保存');
        await load();
      } finally {
        saving.value = false;
      }
    },
    title: '确认保存规则配置',
  });
}

const rollbackOpen = ref(false);
const rollbackSaving = ref(false);
const rollbackForm = reactive({ changeReason: '', targetVersion: 0 });

function openRollback(row: RuleConfigApi.HistoryRow) {
  Object.assign(rollbackForm, { changeReason: '', targetVersion: row.toVersion ?? row.fromVersion ?? 0 });
  rollbackOpen.value = true;
}

async function submitRollback() {
  if (!rollbackForm.targetVersion) {
    message.warning('请填写目标版本号');
    return;
  }
  if (!rollbackForm.changeReason.trim()) {
    message.warning('回滚原因不能为空');
    return;
  }
  rollbackSaving.value = true;
  try {
    await rollbackRuleConfig(form.ruleKey, rollbackForm.targetVersion, rollbackForm.changeReason.trim());
    message.success(`已回滚到版本 ${rollbackForm.targetVersion}`);
    rollbackOpen.value = false;
    await load();
  } finally {
    rollbackSaving.value = false;
  }
}

/* ================= 读取审计（谁在什么场景读了哪一版、命中哪一层） ================= */
const auditQuery = reactive<RuleConfigApi.AuditPageReq>({
  bizScene: '',
  hitSource: undefined,
  pageNo: 1,
  pageSize: 10,
  readAtFrom: undefined,
  readAtTo: undefined,
  readBy: '',
  ruleKey: '',
});
const auditLoading = ref(false);
const auditRows = ref<RuleConfigApi.AuditRow[]>([]);
const auditTotal = ref(0);

async function loadAudit() {
  auditLoading.value = true;
  try {
    const page = await getRuleConfigAuditPage({
      ...auditQuery,
      bizScene: auditQuery.bizScene?.trim() || undefined,
      readBy: auditQuery.readBy?.trim() || undefined,
      ruleKey: auditQuery.ruleKey?.trim() || undefined,
    });
    auditRows.value = page?.list ?? [];
    auditTotal.value = page?.total ?? 0;
  } catch {
    auditRows.value = [];
    auditTotal.value = 0;
  } finally {
    auditLoading.value = false;
  }
}

function onAuditPageChange(pagination: { current?: number; pageSize?: number }) {
  auditQuery.pageNo = pagination.current ?? 1;
  auditQuery.pageSize = pagination.pageSize ?? 10;
  loadAudit();
}

/** 记录哈希只展示前 12 位，完整值在 tooltip/接口里（哈希链用于防篡改核对） */
function shortHash(v?: string) {
  return v ? `${v.slice(0, 12)}…` : '—';
}

onMounted(async () => {
  if (canQuery.value) {
    auditQuery.ruleKey = ruleKey.value;
    await Promise.all([load(), loadAudit()]);
  }
});
</script>

<template>
  <div v-if="!canQuery" class="ct-tip">当前角色无 bidding:rule-config:query 权限，规则配置不可见。</div>
  <div v-else class="rc-wrap">
    <div class="rc-query">
      <a-auto-complete
        v-model:value="ruleKey"
        :options="keyOptions"
        :filter-option="true"
        class="rc-key"
        placeholder="规则键，如 safety.abc.required"
      />
      <a-button type="primary" :loading="loading" @click="load">查询生效值</a-button>
      <span class="rc-hint">生效值来自「租户级 → 灰度 → 平台默认 → 代码兜底」逐层解析，来源见下表 source。</span>
    </div>

    <a-spin :spinning="loading">
      <a-descriptions v-if="item" :column="3" bordered class="rc-desc" size="small">
        <a-descriptions-item label="规则键">{{ item.ruleKey }}</a-descriptions-item>
        <a-descriptions-item label="生效值">
          <b>{{ hintOf(item.value) }}</b>
        </a-descriptions-item>
        <a-descriptions-item label="值类型">{{ item.valueType }}</a-descriptions-item>
        <a-descriptions-item label="配置来源">
          <a-tag :color="item.source === 'TENANT' ? 'green' : item.source === 'GRAY' ? 'orange' : 'blue'">
            {{ item.source ?? '—' }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="分组">{{ item.ruleGroup ?? '—' }}</a-descriptions-item>
        <a-descriptions-item label="版本号（乐观锁 versionNo）">{{ item.versionNo ?? '—' }}</a-descriptions-item>
        <a-descriptions-item label="含义" :span="3">{{ item.description ?? '—' }}</a-descriptions-item>
        <a-descriptions-item label="允许边界 legalFloor" :span="2">{{ item.legalFloor ?? '不限制' }}</a-descriptions-item>
        <a-descriptions-item label="依据">{{ item.regulationRef ?? '—' }}</a-descriptions-item>
      </a-descriptions>
    </a-spin>

    <div v-if="canUpdate && item" class="rc-form">
      <div class="ct-panel-t">写入配置（DRAFT 先存 → 发布 / 灰度 → 可回滚；每次变更必须填原因）</div>
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="6">
            <a-form-item label="值类型"><a-select v-model:value="form.valueType" :options="RULE_VALUE_TYPE_OPTIONS" /></a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="目标状态"><a-select v-model:value="form.status" :options="RULE_STATUS_OPTIONS" /></a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="分组 ruleGroup"><a-input v-model:value="form.ruleGroup" placeholder="CERT / ALERT / PERSONNEL" /></a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="灰度范围 grayScope">
              <a-input v-model:value="form.grayScope" :disabled="form.status !== 'GRAY'" placeholder="仅 GRAY 时生效，如租户 id 列表" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="配置值 configValue（JSON 类型请填合法 JSON 文本）">
              <a-textarea v-model:value="form.configValue" :rows="4" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="含义 description"><a-textarea v-model:value="form.description" :rows="2" /></a-form-item>
            <a-form-item label="允许边界 legalFloor"><a-input v-model:value="form.legalFloor" placeholder="如 {&quot;min&quot;:1,&quot;max&quot;:365}" /></a-form-item>
            <a-form-item label="变更原因 changeReason（必填）"><a-input v-model:value="form.changeReason" /></a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="依据 regulationRef"><a-input v-model:value="form.regulationRef" /></a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="生效开始（空 = 立即）">
              <a-date-picker
                v-model:value="effectiveFromText"
                show-time
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="如 2026-10-01 00:00:00"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="生效结束（空 = 长期）">
              <a-date-picker
                v-model:value="effectiveToText"
                show-time
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="留空 = 长期有效"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
      <div class="rc-foot">
        <a-button type="primary" :loading="saving" @click="submit">保存配置</a-button>
        <span class="rc-hint">
          生效区间以本机时区转 epoch 毫秒提交（后端 TimestampLocalDateTimeDeserializer 只认毫秒数）；
          版本号随查询结果自动回传（并发改动会被后端识别）。
        </span>
      </div>
    </div>

    <div class="ct-panel-t">版本历史（哈希链：recordHash / prevHash 防篡改核对）</div>
    <a-table
      class="ct-table"
      :columns="[
        { title: '版本', key: 'ver', width: 110 },
        { title: '变更', key: 'action', fixed: 'right', width: 110 },
        { title: '原值 → 新值', key: 'value' },
        { title: '原因', dataIndex: 'changeReason', key: 'changeReason', ellipsis: true },
        { title: '操作人', dataIndex: 'changedBy', key: 'changedBy', width: 120 },
        { title: '变更时间', key: 'changedAt', width: 170 },
        { title: '哈希', key: 'hash', width: 180 },
        { title: '操作', fixed: 'right', key: 'op', width: 100 },
      ]"
      :data-source="history"
      :pagination="false"
      :row-key="(r: RuleConfigApi.HistoryRow) => String(r.id ?? r.toVersion)"
      :scroll="{ x: 1200 }"
      size="small"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'ver'">v{{ record.fromVersion }} → v{{ record.toVersion }}</template>
        <template v-else-if="column.key === 'action'">{{ record.changeAction ?? '—' }}</template>
        <template v-else-if="column.key === 'value'">
          <span class="rc-mono">{{ record.fromValue ?? '—' }}</span> → <span class="rc-mono">{{ record.toValue ?? '—' }}</span>
        </template>
        <template v-else-if="column.key === 'changedAt'">{{ fmtDateTime(record.changedAt) }}</template>
        <template v-else-if="column.key === 'hash'">
          <a-tooltip :title="`record=${record.recordHash ?? '—'} / prev=${record.prevHash ?? '—'}`">
            {{ shortHash(record.recordHash) }}
          </a-tooltip>
        </template>
        <template v-else-if="column.key === 'op'">
          <a v-if="canUpdate" @click="openRollback(record)">回滚到此版本</a>
          <span v-else>—</span>
        </template>
      </template>
      <template #emptyText><div class="ct-empty">该规则键暂无版本记录（首次保存后产生）</div></template>
    </a-table>

    <div class="ct-panel-t">读取审计（争议追溯：谁在什么场景读了哪一版、命中哪一层）</div>
    <a-space class="mb-2" wrap>
      <a-input v-model:value="auditQuery.ruleKey" placeholder="规则键（留空=全部）" style="width: 260px" allow-clear />
      <a-input v-model:value="auditQuery.bizScene" placeholder="业务场景 bizScene" style="width: 200px" allow-clear />
      <a-input v-model:value="auditQuery.readBy" placeholder="读取人 readBy" style="width: 180px" allow-clear />
      <a-select
        v-model:value="auditQuery.hitSource"
        :options="RULE_HIT_SOURCE_OPTIONS"
        allow-clear
        placeholder="命中层"
        style="width: 200px"
      />
      <a-date-picker
        v-model:value="auditQuery.readAtFrom"
        show-time
        value-format="YYYY-MM-DD HH:mm:ss"
        placeholder="读取时刻起（含）"
        style="width: 210px"
      />
      <a-date-picker
        v-model:value="auditQuery.readAtTo"
        show-time
        value-format="YYYY-MM-DD HH:mm:ss"
        placeholder="读取时刻止（含）"
        style="width: 210px"
      />
      <a-button :loading="auditLoading" @click="loadAudit">查询审计</a-button>
    </a-space>
    <a-table
      class="ct-table"
      :columns="[
        { title: '规则键', dataIndex: 'ruleKey', key: 'ruleKey', width: 240 },
        { title: '版本', dataIndex: 'ruleVersion', key: 'ruleVersion', width: 90 },
        { title: '命中层', key: 'hitSource', width: 120 },
        { title: '业务场景', dataIndex: 'bizScene', key: 'bizScene', width: 180 },
        { title: '读取人', dataIndex: 'readBy', key: 'readBy', width: 140 },
        { title: '读取时间', key: 'readAt', width: 170 },
        { title: '追踪编号', dataIndex: 'traceId', key: 'traceId' },
      ]"
      :data-source="auditRows"
      :loading="auditLoading"
      :pagination="{ current: auditQuery.pageNo, pageSize: auditQuery.pageSize, showSizeChanger: true, total: auditTotal }"
      :row-key="(r: RuleConfigApi.AuditRow) => String(r.id)"
      :scroll="{ x: 1000 }"
      size="small"
      @change="onAuditPageChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'hitSource'">
          <a-tag>{{ record.hitSource ?? '—' }}</a-tag>
        </template>
        <template v-else-if="column.key === 'readAt'">{{ fmtDateTime(record.readAt) }}</template>
      </template>
      <template #emptyText><div class="ct-empty">暂无读取审计记录（业务判定点调用 resolveWithAudit 后产生）</div></template>
    </a-table>

    <a-modal
      v-model:open="rollbackOpen"
      :confirm-loading="rollbackSaving"
      ok-text="回滚"
      cancel-text="取消"
      destroy-on-close
      title="回滚规则配置"
      @ok="submitRollback"
    >
      <a-form layout="vertical">
        <a-form-item label="规则键"><a-input :value="form.ruleKey" disabled /></a-form-item>
        <a-form-item label="目标版本（必填）">
          <a-input-number v-model:value="rollbackForm.targetVersion" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="回滚原因（必填）"><a-input v-model:value="rollbackForm.changeReason" /></a-form-item>
      </a-form>
      <div class="ct-tip">回滚会写入一条 ROLLED_BACK 版本并进入历史；已回滚版本不可再直接发布，需改出新版本。</div>
    </a-modal>
  </div>
</template>

<style scoped>
.rc-wrap {
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #e6ebf2;
  border-radius: 6px;
}

.rc-query {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}

.rc-key {
  width: 420px;
}

.rc-hint {
  font-size: 12px;
  color: #6b7280;
}

.rc-desc {
  margin-bottom: 14px;
}

.rc-form {
  padding: 10px 0 4px;
  margin-bottom: 10px;
  border-top: 1px dashed #e6ebf2;
}

.rc-foot {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 8px;
}

.rc-mono {
  font-family: Menlo, Consolas, monospace;
  font-size: 12px;
}
</style>
