<script setup lang="ts">
/* 安许风险联动 · 企业侧面板（2026-09-30 按主体拆分）
 * 内容 = 原 safety-risk/index.vue 的企业侧区块，逐行搬迁：
 *   ① 安许 A/B/C 风险卡片（L997-1003 = 复用证照域 certificate/modules/safety-risk.vue）
 *   风险大盘（L1005-1032 + L536-549）、风险规则配置区（L1034-1092 + L758-776/809-904）、
 *   风险清单（L1094-1200 + L551-641）、风险详情抽屉 / 指派弹窗 / 规则表单弹窗（L1526-1672 + L643-711）、
 *   ⑤ 规则配置（L1518-1524 = 复用同目录 modules/RuleConfigPanel.vue）。
 * 归属口径：见 docs/模块设计与规范/电子证照域-三台账信息架构与界面重设计-2026-09-28.md（企业侧 → 企业资质台账）。
 *
 * 拆分前的「区块开关 + 嵌入模式」已删除（宿主只 import 自己要的面板）。
 * 页头（原 L933-995）不搬迁：它原本就是 v-if="!props.embedded"，三个宿主全部传 embedded，在宿主里从未渲染过。
 * 随之失去入口的页头动作（重新扫描 / 刷新 / 更多·一键预警 / 更多·导出报告 / 更多·风险规则配置开关）
 * 在本次拆分前对宿主同样不可达，行为不变；若要在台账页补工具条，属另一次改动。 */
import { computed, onMounted, reactive, ref } from 'vue';

import { message, Modal } from 'ant-design-vue';

import {
  assignRiskItem,
  createRiskRule,
  deleteRiskRule,
  getRiskItem,
  getRiskItemPage,
  getRiskRulePage,
  getRiskStats,
  handleRiskItem,
  type SafetyRiskApi,
  updateRiskRule,
  updateRiskRuleStatus,
} from '#/api/bidding/safetyRisk';

import { fmtDateTime } from '../../certificate/format';
import SafetyRisk from '../../certificate/modules/safety-risk.vue';
import { useCertMeta } from '../../certificate/useCertMeta';
import AttrFilter from '../../components/AttrFilter.vue';
import RuleConfigPanel from '../modules/RuleConfigPanel.vue';
import { useAssignPersons, useCurrentSubject, useSafetyCan } from '../shared';

import '../../certificate/cert.css';

const { can } = useSafetyCan();
const { dictOptions, dictTag } = useCertMeta();
/* 企业主体 → entId：风险卡片 / 风险大盘 / 风险工单都按 entId 查询（原为页面级单例 ref） */
const { entId, loadCurrentSubject } = useCurrentSubject();
/* 责任人下拉（风险清单筛选 + 指派弹窗）：原 loadAssignPersons 原样搬到 shared.ts */
const { assignPersonLoading, assignPersons, loadAssignPersons } = useAssignPersons();

/* ================= 2026-09-18 原型改造新增：风险大盘 / 风险条目 / 风险规则 =================
 * 契约出处：docs/电子证照前前端契约/后端接口契约-资质证书五页改造-2026-09-18.md
 *
 * 边界（**别混用**）：既有 `safety-permit/*` 给的是"结论"（A/B/C 配备结论、人员风险标签、
 * 岗位变更超限统计）；本区要的是**风险工单**——有等级、有责任人、有状态、可指派、可留痕。
 * 命名一律带 risk 前缀，避免与上面既有的 assignForm/assignOpen（关键岗位任职登记）撞名。 */

/* ===== 风险大盘 ===== */
const riskStats = ref<null | SafetyRiskApi.RiskStats>(null);
const riskStatsLoading = ref(false);
const activeRiskStat = ref<string | undefined>(undefined);
async function loadRiskStats(): Promise<void> {
  riskStatsLoading.value = true;
  try {
    riskStats.value = await getRiskStats({ entId: entId.value ? String(entId.value) : undefined });
  } catch {
    riskStats.value = null;
  } finally {
    riskStatsLoading.value = false;
  }
}

/* ===== 风险条目清单 ===== */
const riskQuery = reactive<SafetyRiskApi.RiskItemPageReq>({
  pageNo: 1,
  pageSize: 20,
  riskLevel: undefined,
  status: undefined,
  assigneeId: undefined,
  keyword: undefined,
});
const riskRows = ref<SafetyRiskApi.RiskItem[]>([]);
const riskTotal = ref(0);
const riskLoading = ref(false);
/** ⚠️ 选中态按表格 rowKey 原始类型保存（字符串化会让回填的选中态对不上）；提交时再转字符串 */
const selectedRiskKeys = ref<Array<number | string>>([]);
const riskRowSelection = computed(() => ({
  onChange: (keys: (number | string)[]) => {
    selectedRiskKeys.value = keys;
  },
  selectedRowKeys: selectedRiskKeys.value,
}));

async function loadRiskItems(): Promise<void> {
  riskLoading.value = true;
  try {
    const page = await getRiskItemPage({ ...riskQuery, entId: entId.value ? String(entId.value) : undefined });
    riskRows.value = page.list ?? [];
    riskTotal.value = page.total ?? 0;
  } catch {
    riskRows.value = [];
    riskTotal.value = 0;
  } finally {
    riskLoading.value = false;
  }
}
function resetRiskQuery() {
  Object.assign(riskQuery, {
    riskLevel: undefined,
    status: undefined,
    assigneeId: undefined,
    keyword: undefined,
    pageNo: 1,
  });
  activeRiskStat.value = undefined;
  void loadRiskItems();
}

/** 点大盘卡片 → 写入对应筛选（保证"卡片数字 = 点击后列表条数"） */
function applyRiskStat(kind: string): void {
  activeRiskStat.value = kind;
  riskQuery.pageNo = 1;
  if (kind === 'HANDLED') {
    riskQuery.status = 'HANDLED';
    riskQuery.riskLevel = undefined;
  } else if (kind === 'ALL') {
    riskQuery.status = undefined;
    riskQuery.riskLevel = undefined;
  } else {
    riskQuery.status = undefined;
    riskQuery.riskLevel = kind;
  }
  void loadRiskItems();
}

const riskColumns = [
  { title: '风险等级', dataIndex: 'riskLevel', key: 'riskLevel', width: 110 },
  { title: '风险描述', dataIndex: 'riskDesc', key: 'riskDesc', ellipsis: true },
  { title: '关联证照', dataIndex: 'certRef', key: 'certRef', width: 200, ellipsis: true },
  { title: '影响主体', dataIndex: 'entName', key: 'entName', width: 190, ellipsis: true },
  { title: '责任人', dataIndex: 'assigneeName', key: 'assigneeName', width: 120 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '发现时间', dataIndex: 'foundAt', key: 'foundAt', width: 165 },
  { title: '操作', key: 'action', width: 180, fixed: 'right' as const },
];

/** 风险等级标签：字典 risk_level（已注册）优先 */
function riskLevelTag(level?: null | string) {
  return level ? dictTag('risk_level', level) : { color: 'default', label: '—' };
}
/** 风险条目状态：字典 risk_item_status 优先；未注册时用最小兜底集（不发明码值） */
const RISK_STATUS_FALLBACK: Record<string, { color: string; label: string }> = {
  ASSIGNED: { color: 'blue', label: '已指派' },
  HANDLED: { color: 'green', label: '已处理' },
  HANDLING: { color: 'processing', label: '处理中' },
  IGNORED: { color: 'default', label: '已忽略' },
  PENDING: { color: 'orange', label: '待处理' },
};
function riskStatusTag(status?: null | string) {
  if (!status) return { color: 'default', label: '—' };
  if (dictOptions('risk_item_status').length > 0) return dictTag('risk_item_status', status);
  return RISK_STATUS_FALLBACK[status] ?? { color: 'default', label: status };
}

/* ===== 查看 / 指派 / 处理 ===== */
const riskDetailOpen = ref(false);
const riskDetailLoading = ref(false);
const riskDetail = ref<null | SafetyRiskApi.RiskItem>(null);
async function openRiskDetail(row: SafetyRiskApi.RiskItem) {
  riskDetailOpen.value = true;
  riskDetailLoading.value = true;
  riskDetail.value = row;
  if (row.id) {
    try {
      riskDetail.value = await getRiskItem(row.id);
    } catch {
      /* 用列表行兜底展示 */
    }
  }
  riskDetailLoading.value = false;
}

const riskAssignOpen = ref(false);
const riskAssignSaving = ref(false);
const riskAssignRow = ref<null | SafetyRiskApi.RiskItem>(null);
const riskAssignForm = reactive<{ assigneeId?: string; remark?: string }>({});
function openRiskAssign(row: SafetyRiskApi.RiskItem) {
  riskAssignRow.value = row;
  riskAssignForm.assigneeId = row.assigneeId ?? undefined;
  riskAssignForm.remark = undefined;
  riskAssignOpen.value = true;
  void loadAssignPersons();
}
async function submitRiskAssign() {
  if (!riskAssignForm.assigneeId) {
    message.warning('请选择责任人');
    return;
  }
  const row = riskAssignRow.value;
  if (!row?.id) return;
  riskAssignSaving.value = true;
  try {
    await assignRiskItem({
      id: row.id,
      assigneeId: riskAssignForm.assigneeId,
      version: row.version,
      remark: riskAssignForm.remark,
    });
    message.success('已指派');
    riskAssignOpen.value = false;
    void loadRiskItems();
    void loadRiskStats();
  } catch {
    // 乐观锁冲突等由请求层提示
  } finally {
    riskAssignSaving.value = false;
  }
}

function confirmHandleRisk(row: SafetyRiskApi.RiskItem) {
  Modal.confirm({
    title: '标记风险已处理',
    content: `确认把「${row.riskDesc || row.id}」标记为已处理？`,
    okText: '标记已处理',
    cancelText: '取消',
    async onOk() {
      await handleRiskItem({ id: row.id as string, targetStatus: 'HANDLED', version: row.version });
      message.success('已处理');
      void loadRiskItems();
      void loadRiskStats();
    },
  });
}

/* ===== 风险规则（可折叠；按权限可见） =====
 * 注：拆分前该区的展开开关是页头「更多 → 风险规则配置」（toggleRules），随页头一并移除；
 * rulesOpen 与拆分前在宿主里的取值一致（宿主页头不渲染 → 恒为 false），区块可见性不变。 */

const rulesOpen = ref(false);
const rulesLoading = ref(false);
const ruleRows = ref<SafetyRiskApi.Rule[]>([]);
const ruleTotal = ref(0);
const ruleQuery = reactive<SafetyRiskApi.RulePageReq>({ pageNo: 1, pageSize: 10 });
async function loadRules(): Promise<void> {
  rulesLoading.value = true;
  try {
    const page = await getRiskRulePage({ ...ruleQuery });
    ruleRows.value = page.list ?? [];
    ruleTotal.value = page.total ?? 0;
  } catch {
    ruleRows.value = [];
    ruleTotal.value = 0;
  } finally {
    rulesLoading.value = false;
  }
}

const ruleFormOpen = ref(false);
const ruleSaving = ref(false);
const ruleEditingId = ref<string | undefined>(undefined);
const ruleForm = reactive<SafetyRiskApi.RuleSaveReq>({ ruleName: '', enabled: true });
function openRuleCreate() {
  ruleEditingId.value = undefined;
  Object.assign(ruleForm, {
    id: undefined,
    version: undefined,
    ruleName: '',
    condition: undefined,
    conditionType: undefined,
    scope: undefined,
    ruleKey: undefined,
    riskLevel: undefined,
    remark: undefined,
    enabled: true,
  });
  ruleFormOpen.value = true;
}
function openRuleEdit(row: SafetyRiskApi.Rule) {
  ruleEditingId.value = row.id;
  Object.assign(ruleForm, {
    id: row.id,
    version: row.version,
    ruleName: row.ruleName ?? '',
    condition: row.condition,
    conditionType: row.conditionType,
    scope: row.scope,
    ruleKey: row.ruleKey ?? undefined,
    riskLevel: row.riskLevel,
    remark: row.remark,
    enabled: row.enabled ?? true,
  });
  ruleFormOpen.value = true;
}
async function submitRule() {
  if (!ruleForm.ruleName?.trim()) {
    message.warning('规则名称不能为空');
    return;
  }
  ruleSaving.value = true;
  try {
    if (ruleEditingId.value) {
      await updateRiskRule({ ...ruleForm, id: ruleEditingId.value });
    } else {
      await createRiskRule({ ...ruleForm });
    }
    message.success('已保存');
    ruleFormOpen.value = false;
    await loadRules();
  } catch {
    // 错误由请求层提示
  } finally {
    ruleSaving.value = false;
  }
}
function toggleRule(row: SafetyRiskApi.Rule) {
  const next = !(row.enabled ?? true);
  Modal.confirm({
    title: next ? '启用规则' : '禁用规则',
    content: `确认${next ? '启用' : '禁用'}「${row.ruleName}」？变更在下次风险扫描时生效。`,
    okText: next ? '启用' : '禁用',
    cancelText: '取消',
    async onOk() {
      await updateRiskRuleStatus(row.id as string, next);
      message.success(next ? '已启用' : '已禁用');
      await loadRules();
    },
  });
}
function confirmDeleteRule(row: SafetyRiskApi.Rule) {
  Modal.confirm({
    title: '删除风险规则',
    content: `确认删除「${row.ruleName}」？已产出的风险条目不回滚（历史留痕）。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deleteRiskRule(row.id as string);
      message.success('已删除');
      await loadRules();
    },
  });
}

/** 规则条件下的触发条件类型（字典 risk_rule_cond_type 优先；未注册时最小兜底集） */
const COND_TYPE_FALLBACK = [
  { label: '阈值比较', value: 'THRESHOLD' },
  { label: '表达式', value: 'EXPRESSION' },
  { label: '自然语言', value: 'NATURAL' },
];
const condTypeOptions = computed(() => {
  const dict = dictOptions('risk_rule_cond_type');
  return dict.length > 0 ? dict : COND_TYPE_FALLBACK;
});

onMounted(() => {
  /* 企业侧区块以 entId 为查询上下文，必须先取主体；取不到主体时不发起请求，避免无谓 403。
     这段是拆分前 onMounted 里 showRiskCard || showWorkbench 分支的原样搬迁（本面板恒含这两块）。 */
  void loadCurrentSubject().then(() => {
    // 主体带出后再拉风险工单（risk 接口按 entId 过滤）
    void loadRiskItems();
    void loadRiskStats();
  });
});
</script>

<template>
  <div class="ct-page ct-embedded-root">
    <!-- ① 风险卡片（复用证照域组件：读取/试算/重算/宽限/快照哈希链） -->
    <div class="ct-panel mb-3">
      <div class="ct-panel-t">① 安许 A/B/C 风险</div>
      <div class="ct-panel-b">
        <SafetyRisk :ent-id="entId ?? null" />
      </div>
    </div>

    <!-- ===== 风险大盘：全量风险工单计数，点卡片联动下方风险清单 ===== -->
    <div class="ct-kpis" :class="{ 'is-loading': riskStatsLoading }">
      <div class="ct-kpi k-red" :class="{ 'is-active': activeRiskStat === 'HIGH' }" @click="applyRiskStat('HIGH')">
        <div class="k-label">高风险</div>
        <div class="k-num">{{ riskStats?.high ?? '—' }}</div>
        <div class="k-note">risk_level = HIGH</div>
      </div>
      <div class="ct-kpi k-orange" :class="{ 'is-active': activeRiskStat === 'MEDIUM' }" @click="applyRiskStat('MEDIUM')">
        <div class="k-label">中风险</div>
        <div class="k-num">{{ riskStats?.medium ?? '—' }}</div>
        <div class="k-note">risk_level = MEDIUM</div>
      </div>
      <div class="ct-kpi k-green" :class="{ 'is-active': activeRiskStat === 'LOW' }" @click="applyRiskStat('LOW')">
        <div class="k-label">低风险</div>
        <div class="k-num">{{ riskStats?.low ?? '—' }}</div>
        <div class="k-note">risk_level = LOW</div>
      </div>
      <div class="ct-kpi" :class="{ 'is-active': activeRiskStat === 'HANDLED' }" @click="applyRiskStat('HANDLED')">
        <div class="k-label">已处理</div>
        <div class="k-num">{{ riskStats?.handled ?? '—' }}</div>
        <div class="k-note">status = HANDLED</div>
      </div>
      <div class="ct-kpi k-blue" :class="{ 'is-active': activeRiskStat === 'ALL' }" @click="applyRiskStat('ALL')">
        <div class="k-label">风险总数</div>
        <div class="k-num">{{ riskStats?.total ?? '—' }}</div>
        <div class="k-note">全部风险条目</div>
      </div>
    </div>

    <!-- ===== 风险规则配置区（可折叠；仅授权者可见） ===== -->
    <div v-if="rulesOpen && can('bidding:safety-risk:rule')" class="ct-panel mb-3">
      <div class="ct-panel-h">
        <span class="ct-panel-t">风险规则配置</span>
        <span class="ct-panel-meta">
          这里维护的是"规则"（名称 / 触发条件 / 影响范围 / 启停）；阈值本身仍在「规则配置中心」按 ruleKey 管理，两者可用 ruleKey 关联
        </span>
        <a-button size="small" type="primary" @click="openRuleCreate">+ 新增规则</a-button>
      </div>
      <div class="ct-panel-b">
        <a-table
          class="ct-table"
          :columns="[
            { title: '规则名称', dataIndex: 'ruleName', key: 'ruleName', width: 220 },
            { title: '触发条件', dataIndex: 'condition', key: 'condition', ellipsis: true },
            { title: '条件类型', dataIndex: 'conditionType', key: 'conditionType', width: 120 },
            { title: '影响范围', dataIndex: 'scope', key: 'scope', width: 190, ellipsis: true },
            { title: '产出等级', dataIndex: 'riskLevel', key: 'riskLevel', width: 110 },
            { title: '关联阈值键', dataIndex: 'ruleKey', key: 'ruleKey', width: 200, ellipsis: true },
            { title: '状态', dataIndex: 'enabled', key: 'enabled', width: 90 },
            { title: '操作', key: 'ruleAction', width: 190, fixed: 'right' },
          ]"
          :data-source="ruleRows"
          :loading="rulesLoading"
          :pagination="{
            current: ruleQuery.pageNo,
            pageSize: ruleQuery.pageSize,
            total: ruleTotal,
            showSizeChanger: false,
            onChange: (p: number) => { ruleQuery.pageNo = p; loadRules(); },
          }"
          row-key="id"
          size="small"
          :scroll="{ x: 1500 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'conditionType'">
              {{ condTypeOptions.find((o) => o.value === record.conditionType)?.label ?? (record.conditionType || '—') }}
            </template>
            <template v-else-if="column.key === 'riskLevel'">
              <a-tag :color="riskLevelTag(record.riskLevel).color">{{ riskLevelTag(record.riskLevel).label }}</a-tag>
            </template>
            <template v-else-if="column.key === 'enabled'">
              <a-tag :color="record.enabled ? 'green' : 'default'">{{ record.enabled ? '启用' : '禁用' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'ruleAction'">
              <a-space size="small">
                <a @click="toggleRule(record)">{{ record.enabled ? '禁用' : '启用' }}</a>
                <a @click="openRuleEdit(record)">编辑</a>
                <a class="ct-danger" @click="confirmDeleteRule(record)">删除</a>
              </a-space>
            </template>
          </template>
          <template #emptyText>
            <div class="ct-empty">暂无风险规则：点右上「+ 新增规则」（该接口后端待实现，未就绪时列表恒为空）</div>
          </template>
        </a-table>
      </div>
    </div>

    <!-- ===== 风险清单（原型"主数据区"）：等级 / 描述 / 关联证照 / 影响主体 / 责任人 / 状态 ===== -->
    <div class="ct-panel">
      <div class="ct-panel-h">
        <span class="ct-panel-t">风险清单</span>
        <span class="ct-panel-meta">与上方 A/B/C 卡片的区别：这里是可指派、可流转、可留痕的**风险工单**</span>
      </div>
      <div class="mb-3 rounded-md border border-border bg-card px-3.5 pt-1.5 pb-2.5">
        <AttrFilter label="搜索">
          <a-input
            v-model:value="riskQuery.keyword"
            placeholder="风险描述 / 关联证照 / 影响主体"
            style="width: 240px"
            allow-clear
            @press-enter="riskQuery.pageNo = 1; loadRiskItems()"
          />
        </AttrFilter>
        <AttrFilter v-model="riskQuery.riskLevel" label="风险等级" :options="dictOptions('risk_level')" @change="riskQuery.pageNo = 1; loadRiskItems()" />
        <AttrFilter label="状态">
          <a-select
            v-model:value="riskQuery.status"
            :options="
              dictOptions('risk_item_status').length > 0
                ? dictOptions('risk_item_status')
                : [
                    { label: '待处理', value: 'PENDING' },
                    { label: '已指派', value: 'ASSIGNED' },
                    { label: '处理中', value: 'HANDLING' },
                    { label: '已处理', value: 'HANDLED' },
                    { label: '已忽略', value: 'IGNORED' },
                  ]
            "
            placeholder="处理状态"
            allow-clear
            style="width: 150px"
            @change="riskQuery.pageNo = 1; loadRiskItems()"
          />
        </AttrFilter>
        <AttrFilter label="责任人">
          <a-select
            v-model:value="riskQuery.assigneeId"
            :options="assignPersons.map((p) => ({ label: p.label, value: String(p.value) }))"
            :loading="assignPersonLoading"
            show-search
            option-filter-prop="label"
            allow-clear
            placeholder="按姓名搜索员工档案"
            style="width: 200px"
            @search="loadAssignPersons"
            @change="riskQuery.pageNo = 1; loadRiskItems()"
          />
        </AttrFilter>
        <div class="mt-2 flex flex-wrap items-center gap-2">
          <a-button type="primary" @click="riskQuery.pageNo = 1; loadRiskItems()">查询</a-button>
          <a-button @click="resetRiskQuery">重置</a-button>
          <span class="ml-auto text-xs text-muted-foreground">共 {{ riskTotal }} 条</span>
        </div>
      </div>
      <div class="ct-panel-b">
        <a-table
          class="ct-table"
          :columns="riskColumns"
          :data-source="riskRows"
          :loading="riskLoading"
          :pagination="{
            current: riskQuery.pageNo,
            pageSize: riskQuery.pageSize,
            total: riskTotal,
            showSizeChanger: true,
            showTotal: (t: number) => `共 ${t} 条`,
          }"
          :row-selection="riskRowSelection"
          row-key="id"
          size="small"
          :scroll="{ x: 1500 }"
          @change="(p: any) => { riskQuery.pageNo = p.current; riskQuery.pageSize = p.pageSize; void loadRiskItems(); }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'riskLevel'">
              <a-tag :color="riskLevelTag(record.riskLevel).color">{{ riskLevelTag(record.riskLevel).label }}</a-tag>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="riskStatusTag(record.status).color">{{ riskStatusTag(record.status).label }}</a-tag>
            </template>
            <template v-else-if="column.key === 'assigneeName'">
              {{ record.assigneeName || '未指派' }}
            </template>
            <template v-else-if="column.key === 'foundAt'">
              {{ record.foundAt ? fmtDateTime(record.foundAt) : '—' }}
            </template>
            <template v-else-if="column.key === 'action'">
              <a-space size="small">
                <a @click="openRiskDetail(record)">查看</a>
                <a v-if="can('bidding:safety-risk:assign')" @click="openRiskAssign(record)">指派</a>
                <a v-if="can('bidding:safety-risk:handle') && record.status !== 'HANDLED'" @click="confirmHandleRisk(record)">
                  标记已处理
                </a>
              </a-space>
            </template>
          </template>
          <template #emptyText>
            <div class="ct-empty">
              暂无风险条目：点右上「重新扫描风险」按启用规则重算（该接口后端待实现，未就绪时列表恒为空）
            </div>
          </template>
        </a-table>
      </div>
    </div>

    <!-- ⑤ 规则配置：阈值生效值 / 发布与灰度 / 版本历史与回滚 / 读取审计（bidding:rule-config:*） -->
    <div class="ct-panel">
      <div class="ct-panel-t">⑤ 规则配置（阈值 · 版本 · 读取审计）</div>
      <div class="ct-panel-b">
        <RuleConfigPanel />
      </div>
    </div>

    <!-- ===== 风险详情 ===== -->
    <a-drawer v-model:open="riskDetailOpen" class="bid-ind" :width="640" title="风险详情" destroy-on-close>
      <a-spin :spinning="riskDetailLoading">
        <a-descriptions v-if="riskDetail" :column="1" size="small" bordered>
          <a-descriptions-item label="风险等级">
            <a-tag :color="riskLevelTag(riskDetail.riskLevel).color">{{ riskLevelTag(riskDetail.riskLevel).label }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="风险描述">{{ riskDetail.riskDesc || '—' }}</a-descriptions-item>
          <a-descriptions-item label="风险类型">{{ riskDetail.riskType || '—' }}</a-descriptions-item>
          <a-descriptions-item label="关联证照">{{ riskDetail.certRef || '—' }}</a-descriptions-item>
          <a-descriptions-item label="影响主体">{{ riskDetail.entName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="责任人">{{ riskDetail.assigneeName || '未指派' }}</a-descriptions-item>
          <a-descriptions-item label="处理状态">
            <a-tag :color="riskStatusTag(riskDetail.status).color">{{ riskStatusTag(riskDetail.status).label }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="发现时间">
            {{ riskDetail.foundAt ? fmtDateTime(riskDetail.foundAt) : '—' }}
          </a-descriptions-item>
          <a-descriptions-item label="处理人 / 时间">
            {{ riskDetail.handledBy || '—' }} / {{ riskDetail.handledAt ? fmtDateTime(riskDetail.handledAt) : '—' }}
          </a-descriptions-item>
          <a-descriptions-item label="备注">{{ riskDetail.remark || '—' }}</a-descriptions-item>
        </a-descriptions>
        <div v-else class="ct-empty">未取到风险详情</div>
      </a-spin>
    </a-drawer>

    <!-- ===== 指派责任人 ===== -->
    <a-modal
      v-model:open="riskAssignOpen"
      :title="`指派风险责任人${riskAssignRow?.riskDesc ? ` · ${riskAssignRow.riskDesc}` : ''}`"
      :width="560"
      :confirm-loading="riskAssignSaving"
      ok-text="确认指派"
      cancel-text="取消"
      destroy-on-close
      @ok="submitRiskAssign"
    >
      <a-form layout="vertical">
        <a-form-item label="责任人（必填）">
          <a-select
            v-model:value="riskAssignForm.assigneeId"
            :options="assignPersons.map((p) => ({ label: p.label, value: String(p.value) }))"
            :loading="assignPersonLoading"
            show-search
            :filter-option="false"
            placeholder="按姓名搜索员工档案"
            style="width: 100%"
            @search="loadAssignPersons"
          />
        </a-form-item>
        <a-form-item label="指派说明">
          <a-textarea v-model:value="riskAssignForm.remark" :rows="3" placeholder="如：由安全总监牵头，本周内完成整改" />
        </a-form-item>
      </a-form>
      <div class="ct-tip">提交时回传乐观锁版本；指派结果与时间会留痕，可在风险详情回看。</div>
    </a-modal>

    <!-- ===== 新增 / 编辑风险规则 ===== -->
    <a-modal
      v-model:open="ruleFormOpen"
      :title="ruleEditingId ? '编辑风险规则' : '新增风险规则'"
      :width="720"
      :confirm-loading="ruleSaving"
      ok-text="保存"
      cancel-text="取消"
      destroy-on-close
      @ok="submitRule"
    >
      <a-form layout="horizontal">
        <div class="ct-sheet">
          <div class="ct-sheet-group">规则定义<span class="ct-sg-note">名称 / 条件 / 影响范围</span></div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell ct-sheet-wide">
              <a-form-item label="规则名称（必填）">
                <a-input v-model:value="ruleForm.ruleName" placeholder="如：安许证到期 60 天内未续期" />
              </a-form-item>
            </div>
          </div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item label="条件类型">
                <a-select
                  v-model:value="ruleForm.conditionType"
                  :options="condTypeOptions"
                  allow-clear
                  placeholder="选择条件类型"
                  style="width: 100%"
                />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="产出等级">
                <a-select
                  v-model:value="ruleForm.riskLevel"
                  :options="dictOptions('risk_level')"
                  allow-clear
                  placeholder="命中后产出的风险等级"
                  style="width: 100%"
                />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="是否启用">
                <a-switch v-model:checked="ruleForm.enabled" />
              </a-form-item>
            </div>
          </div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell ct-sheet-wide">
              <a-form-item label="触发条件">
                <a-textarea
                  v-model:value="ruleForm.condition"
                  :rows="2"
                  placeholder="如：daysToExpire <= 60 且 hasPendingApply == false"
                />
              </a-form-item>
            </div>
          </div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item label="影响范围">
                <a-input v-model:value="ruleForm.scope" placeholder="如：全部企业资质 / 安许 A·B·C 人员" />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="关联阈值键">
                <a-input v-model:value="ruleForm.ruleKey" placeholder="如 safety.abc.required（可与规则配置中心挂钩）" />
              </a-form-item>
            </div>
          </div>
        </div>

        <div class="ct-sheet" style="margin-top: 14px">
          <div class="ct-sheet-group">备注</div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell ct-sheet-wide">
              <a-form-item label="备注"><a-textarea v-model:value="ruleForm.remark" :rows="2" /></a-form-item>
            </div>
          </div>
        </div>
      </a-form>
      <div class="ct-tip">
        规则变更在**下一次风险扫描**时生效；阈值本身仍由「规则配置中心」按 ruleKey 管理（版本 / 灰度 / 回滚 / 读取审计），
        两者通过"关联阈值键"挂钩，避免同一套数字维护两遍。
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
/* 风险大盘卡片可点击联动（与其它页同款交互） */
.ct-kpis.is-loading {
  opacity: 0.6;
}

.ct-kpi {
  cursor: pointer;
  transition:
    box-shadow 0.15s ease,
    transform 0.15s ease;
}

.ct-kpi:hover {
  box-shadow: var(--ind-shadow-lg, 0 4px 12px hsl(var(--foreground) / 16%));
  transform: translateY(-1px);
}

.ct-kpi.is-active {
  outline: 2px solid var(--ind-line, #2e333b);
  outline-offset: -2px;
}
</style>
